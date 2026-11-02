package com.nuvio.tv.ui.screens.player.seekpreview

import com.nuvio.tv.ui.screens.player.seekpreview.torbox.TorboxFirstPreviewTrack
import com.nuvio.tv.ui.screens.player.seekpreview.torbox.TorboxMovieRequest
import com.nuvio.tv.ui.screens.player.seekpreview.torbox.TorboxSpriteTrack
import com.nuvio.tv.ui.screens.player.seekpreview.torbox.torboxPreviewKeys
import com.nuvio.tv.ui.screens.player.seekpreview.torbox.torboxPreviewRequest
import com.nuvio.tv.ui.screens.player.seekpreview.torbox.calibrateTorboxPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.nuvio.tv.ui.screens.player.PlayerEvent
import com.nuvio.tv.ui.screens.player.PlayerRuntimeController
import com.nuvio.tv.ui.screens.player.currentPlaybackDurationMs
import com.nuvio.tv.ui.screens.player.currentPlaybackPositionMs
import com.nuvio.tv.ui.screens.player.hideControls
import com.nuvio.tv.ui.screens.player.setPlaybackPaused
import com.nuvio.tv.ui.screens.player.seekpreview.local.LocalPreviewSource
import com.nuvio.tv.ui.screens.player.seekpreview.local.LocalPreviewSources
import com.nuvio.tv.ui.screens.player.seekpreview.local.LocalPreviewTrack
import com.nuvio.tv.ui.screens.player.seekpreview.local.LocalSeekPreviewSettings
import com.nuvio.tv.ui.screens.player.seekpreview.local.localSeekPreviewCacheKey
import com.nuvio.tv.ui.screens.player.seekpreview.engine.SessionPreviewTrack
import com.nuvio.tv.ui.screens.player.seekpreview.engine.PreviewPreferences
import com.nuvio.tv.ui.screens.player.seekpreview.engine.PreviewOptions
import com.nuvio.tv.ui.screens.player.seekpreview.grab.GrabberPreviewTrack
import androidx.media3.exoplayer.SeekParameters
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.abs

/**
 * Seek-preview state for one player session: on-device Arc/local tracks, an ExoPlayer grabber
 * for mpv and adaptive streams, optional TorBox sprites, and the cue behind the frame on screen.
 */
class SeekPreviewState internal constructor(
    scope: CoroutineScope,
    private val controller: PlayerRuntimeController,
) {
    @Volatile private var activeGenerated: ArcPreviewTrack? = null
    @Volatile private var ownedGrab: GrabberPreviewTrack? = null
    init {
        // A newly opened player (including MPV) supersedes any detached after-playback pass.
        com.nuvio.tv.ui.screens.player.seekpreview.engine.SeekThumbnails.stopSession(handBackPlayer = false)
        com.nuvio.tv.ui.screens.player.seekpreview.engine.NextEpisodeThumbs.cancel()
        com.nuvio.tv.ui.screens.player.seekpreview.engine.NextEpisodeThumbs.resume()
        scope.coroutineContext[Job]?.invokeOnCompletion {
            ownedGrab?.close()
            ownedGrab = null
        }
    }
    private val _previewCue = MutableStateFlow<SeekPreviewCue?>(null)

    /**
     * Cue window (playback timebase) behind the preview frame currently on screen, or `null`
     * while no preview has resolved. Drives grid-locked scrubbing and the scrubber's cue ticks.
     */
    val previewCue: StateFlow<SeekPreviewCue?> = _previewCue.asStateFlow()

    private val _offsetMs = MutableStateFlow(0)

    /**
     * Manual offset applied before TorBox thumbnail lookup (calibration).
     * Session-scoped and reset when the stream changes.
     */
    val offsetMs: StateFlow<Int> = _offsetMs.asStateFlow()

    /** Shared TorBox previews; account setup and visual verification stay outside the core player. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val torboxTrack: StateFlow<TorboxSpriteTrack?> =
        combine(
            controller.playbackTimeline.map { if (it.isLive) 0L else it.duration }
                .distinctUntilChanged { a, b -> (a > 0L) == (b > 0L) && abs(a - b) <= 1_000L },
            controller.uiState.map { Triple(it.currentStreamUrl, it.currentStreamInfoHash, it.currentStreamFileIdx) }
                .distinctUntilChanged(),
            combine(torboxPreviewKeys(controller.context), LocalSeekPreviewSettings.enabled(controller.context)) {
                key, enabled -> if (enabled) key else ""
            }
        ) { duration, stream, key ->
            Triple(key, stream.first, TorboxMovieRequest(controller.contentId, controller.contentType,
                duration, stream.second, stream.third))
        }.distinctUntilChanged()
            .transformLatest<Triple<String, String?, TorboxMovieRequest>, TorboxSpriteTrack?> { (key, streamUrl, request) ->
                setOffset(0)
                _previewCue.value = null
                // The account listing is large: let a new source finish its first buffering (bounded)
                // before downloading it, so preview setup never slows the start of playback.
                if (key.isNotBlank() && request.durationMs > 0L) withTimeoutOrNull(TORBOX_START_WAIT_MS) {
                    controller.uiState.first { it.isPlaying && !it.isBuffering }
                }
                val resolved = if (key.isBlank()) request else torboxPreviewRequest(controller, request)
                val owned = TorboxSpriteTrack.open(key, resolved, controller.context.cacheDir.resolve("torbox-sprites-v1"))
                var calibration: Job? = null
                var stalls: Job? = null
                try {
                    if (owned != null) calibration = scope.launch { calibrateTorboxPreview(controller, streamUrl, owned) }
                    if (owned != null) stalls = scope.launch {
                        controller.uiState.map { it.isBuffering }.distinctUntilChanged().collect { owned.playbackStalled = it }
                    }
                    owned?.prefetch(runCatching { controller.currentPlaybackPositionMs() }.getOrNull() ?: 0L)
                    emit(owned) // Fingerprint verification never blocks or hides initial previews.
                    if (owned != null) {
                        owned.available.first { !it }
                        calibration?.cancel()
                        owned.close()
                        emit(null) // Resume existing providers if the TorBox track becomes unavailable.
                    }
                    awaitCancellation()
                } finally { calibration?.cancel(); stalls?.cancel(); owned?.close() }
            }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * On-device thumbnails for the stream this player shows with ExoPlayer, while "Generate
     * previews on device" is on. Opened when the stream registers and its duration is known,
     * closed (and saved to the disk cache) when either changes or the player goes away. The
     * duration of some streams is refined while they play: a change of a second or less keeps
     * the open track rather than tearing it down mid-playback.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val localTrack: StateFlow<SeekPreviewTrack?> =
        combine(
            // Live windows have no fixed timeline to hang thumbnails on.
            controller.playbackTimeline.map { if (it.isLive) 0L else it.duration }
                .distinctUntilChanged { old, new -> (old > 0L) == (new > 0L) && abs(old - new) <= 1_000L },
            LocalSeekPreviewSettings.enabled(controller.context),
            LocalPreviewSources.sourceFor(controller),
            PreviewPreferences.options(controller.context),
            torboxTrack
        ) { durationMs, enabled, source, options, torbox ->
            PreviewLocalRequest(durationMs, enabled && torbox == null, source, options)
        }
            .distinctUntilChanged()
            .transformLatest<PreviewLocalRequest, SeekPreviewTrack?> { (durationMs, enabled, source) ->
                com.nuvio.tv.ui.screens.player.seekpreview.engine.PreviewDiagnostics.note(
                    "local preview request: enabled=$enabled source=${source != null} duration=$durationMs")
                if (!enabled || source == null || durationMs <= 0L) {
                    emit(null)
                    return@transformLatest
                }
                val cacheKey = localSeekPreviewCacheKey(
                    contentId = controller.contentId,
                    season = controller.currentSeason,
                    episode = controller.currentEpisode,
                    durationMs = durationMs
                ) + "|" + com.nuvio.tv.ui.screens.player.seekpreview.engine.PreviewStore.digest(source.sourceKey.toByteArray())
                val adapter = ArcPreviewAdapter(controller, source)
                val serverSource = previewServerSource(source.sourceKey)
                val generated = if (previewSingleFileUrl(source.sourceKey)) ArcPreviewTrack(controller.context,
                    source, listOf(controller.contentId ?: "source:" + com.nuvio.tv.ui.screens.player.seekpreview.engine.PreviewStore.digest(source.sourceKey.toByteArray()), controller.currentSeason, controller.currentEpisode).joinToString("|"),
                    identityExact = !controller.contentId.isNullOrBlank(), durationMs = durationMs, adapter = adapter,
                    startImmediately = serverSource == null) else null
                val captured = if (generated == null) LocalPreviewSources.open(source, cacheKey, durationMs) else null
                val local = generated ?: captured ?: run { emit(null); return@transformLatest }
                val server = if (serverSource != null) controller.serverPreview(source, durationMs,
                    onUnavailable = { generated?.start() }) else null
                if (serverSource != null && server == null) generated?.start()
                val combined = server?.let { SessionPreviewTrack(local, it) }
                activeGenerated = generated
                try {
                    emit(combined ?: local)
                    controller.uiState.combine(controller.playbackTimeline) { state, timeline ->
                        PreviewPlaybackSample(state.isPlaying || state.isBuffering, state.isBuffering,
                            timeline.currentPosition, timeline.bufferedPosition, controller.previewDroppedFrames())
                    }.distinctUntilChanged().collect { (active, stalled, position, buffered, dropped) ->
                        captured?.playbackActive = active
                        adapter.sampleSignals()
                        server?.updatePlayback(active, stalled, position, buffered, dropped)
                    }
                } finally {
                    if (activeGenerated === generated) activeGenerated = null
                    combined?.close()
                    generated?.close(afterPlayback = scope.coroutineContext[Job]?.isActive == false,
                        resumeIfHeld = !source.released && scope.coroutineContext[Job]?.isActive == true)
                    captured?.let { LocalPreviewSources.close(source, it) }
                    if (generated == null) adapter.detach()
                }

            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Headless ExoPlayer thumbnails for mpv, HLS/DASH, and gaps the Arc engine or playback tap
     * do not cover yet.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val grabTrack: StateFlow<GrabberPreviewTrack?> =
        combine(
            controller.playbackTimeline.map { if (it.isLive) 0L else it.duration }
                .distinctUntilChanged { a, b -> (a > 0L) == (b > 0L) && abs(a - b) <= 1_000L },
            controller.uiState.map { it.currentStreamUrl }.distinctUntilChanged(),
            LocalSeekPreviewSettings.enabled(controller.context),
        ) { durationMs, url, enabled -> Triple(durationMs, url, enabled) }
            .distinctUntilChanged()
            .mapLatest { (durationMs, url, enabled) ->
                ownedGrab?.close()
                ownedGrab = null
                if (!enabled || durationMs <= 0L || url.isNullOrBlank()) return@mapLatest null
                if (controller.playbackTimeline.value.isLive || controller.isTorrentStream) return@mapLatest null
                GrabberPreviewTrack(
                    context = controller.context,
                    url = url,
                    headers = controller.getCurrentHeaders(),
                    durationMs = durationMs,
                ).also { ownedGrab = it }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * What the preview shows: on-device frames where available, grabber elsewhere (see
     * [FallbackPreviewTrack]); TorBox when configured.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val previewTrack: StateFlow<SeekPreviewTrack?> =
        combine(localTrack, grabTrack, torboxTrack) { local, grab, torbox -> Triple(local, grab, torbox) }
            .transformLatest<Triple<SeekPreviewTrack?, GrabberPreviewTrack?, TorboxSpriteTrack?>, SeekPreviewTrack?> { (local, grab, torbox) ->
                val layered = when {
                    local != null && grab != null -> FallbackPreviewTrack(local, grab)
                    else -> local ?: grab
                }
                val decorated = torbox?.let { TorboxFirstPreviewTrack(it, layered) }
                try {
                    emit(decorated ?: layered)
                    awaitCancellation()
                } finally {
                    decorated?.close()
                }
            }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            controller.uiState
                .map { it.showControls || it.showSeekOverlay }
                .distinctUntilChanged()
                .collect { open ->
                    if (!open) return@collect
                    val pos = runCatching { controller.currentPlaybackPositionMs() }.getOrNull() ?: 0L
                    grabTrack.value?.warmUp(pos)
                }
        }
        // Scrubbing pauses the video and playback resumes once the scrub ends (committed or
        // abandoned), unless the viewer paused or played meanwhile. A paused player leaves the
        // CPU to the preview frames and stops the picture moving under a frame that is elsewhere.
        scope.launch {
            var pausedForScrub = false
            controller.uiState
                .map { it.pendingPreviewSeekPosition != null }
                .distinctUntilChanged()
                .collect { scrubbing ->
                    val state = controller.uiState.value
                    if (scrubbing) {
                        if (state.isPlaying && state.error == null) {
                            pausedForScrub = true
                            controller.setPlaybackPaused(true)
                        }
                    } else if (pausedForScrub) {
                        pausedForScrub = false
                        grabTrack.value?.endScrub()
                        val latest = controller.uiState.value
                        if (!latest.isPlaying && !controller.userPausedManually &&
                            latest.error == null && !latest.playbackEnded
                        ) {
                            controller.setPlaybackPaused(false)
                        }
                    }
                }
        }
        // Which way the viewer scrubs, so on-device frames ahead of the scrub decode first.
        // Snapping onto a cue start moves the position a little; only whole steps count.
        scope.launch {
            var previous: Long? = null
            controller.uiState
                .map { it.pendingPreviewSeekPosition }
                .distinctUntilChanged()
                .collect { position ->
                    val track = localTrack.value
                    val last = previous
                    previous = position
                    if (position == null || last == null) {
                        if (position == null) track?.noteScrubDirection(0)
                        return@collect
                    }
                    if (abs(position - last) >= SCRUB_STEP_MIN_MS) {
                        track?.noteScrubDirection(if (position > last) 1 else -1)
                    }
                }
        }
    }

    /** Spacing between preview cues, or 0 when no preview has resolved. */
    val cueIntervalMs: StateFlow<Long> =
        previewCue
            .map { it?.durationMs ?: 0L }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, 0L)

    /**
     * Grid-locked scrubbing: rewrites a D-pad preview step so it lands on a position an actual
     * preview frame exists for, so the thumbnail and the eventual seek can never disagree.
     * Every other event, and every step without a resolved cue, passes through unchanged.
     */
    internal fun intercept(event: PlayerEvent): PlayerEvent {
        if (event !is PlayerEvent.OnPreviewSeekBy || previewTrack.value == null) return event
        if (controller.playbackTimeline.value.isLive) return event
        val maxDuration = controller.currentPlaybackDurationMs().takeIf { it >= 0 } ?: Long.MAX_VALUE
        val basePosition = controller.pendingPreviewSeekPosition
            ?: controller.currentPlaybackPositionMs()?.coerceAtLeast(0L)
            ?: 0L
        val target = activeGenerated?.gridStep(basePosition, event.deltaMs) ?: SeekPreviewCueStepper.targetMs(
            cue = _previewCue.value,
            fromMs = basePosition,
            deltaMs = event.deltaMs,
            durationMs = maxDuration
        )
        return PlayerEvent.OnPreviewSeekBy(target - basePosition)
    }

    /**
     * Reported by the thumbnail host once it knows which cue the frame on screen came from.
     * Snapping the pending scrub position onto its start keeps the number under the thumbnail
     * honest even when the cue grid is not perfectly uniform. The start moves onto the file's
     * keyframe when one is within [SEEK_PREVIEW_KEYFRAME_SNAP_MS], so the committed seek is exact
     * and needs no decoding forward (see [commitSeekParameters]).
     */
    fun onPreviewCueResolved(cue: SeekPreviewCue?) {
        _previewCue.value = cue
        if (controller.playbackTimeline.value.isLive) return
        val maxDuration = controller.currentPlaybackDurationMs().takeIf { it >= 0 } ?: Long.MAX_VALUE
        val activeTrack = previewTrack.value
        val aligned = SeekPreviewCueStepper.alignedTargetMs(
            cue = cue,
            pendingMs = controller.pendingPreviewSeekPosition,
            durationMs = maxDuration,
            snap = { startMs -> activeTrack?.keyframeNear(startMs, SEEK_PREVIEW_KEYFRAME_SNAP_MS) ?: startMs }
        ) ?: return
        controller.pendingPreviewSeekPosition = aligned
        controller.updatePlaybackTimeline(currentPosition = aligned)
    }

    internal fun stopGenerationForError() { activeGenerated?.close() }

    /**
     * Sets TorBox calibration offset. Only thumbnail composables consume it.
     */
    fun setOffset(targetMs: Int) {
        val clamped = targetMs.coerceIn(SEEK_PREVIEW_OFFSET_MIN_MS, SEEK_PREVIEW_OFFSET_MAX_MS)
        if (_offsetMs.value == clamped) return
        // The cached cue was converted to the playback timebase with the old offset.
        _offsetMs.value = clamped
        _previewCue.value = null
    }

    fun adjustOffset(deltaMs: Int) = setOffset(_offsetMs.value + deltaMs)

    init {
        synchronized(states) { states[controller] = WeakReference(this) }
    }

    /**
     * How a committed D-pad scrub should land while previews are shown. The player's own choice,
     * the closest keyframe at any distance, can land seconds away from the frame just shown (half
     * the gap between keyframes). A preview target is already a keyframe whenever the file's
     * index is known (see [onPreviewCueResolved]), and landing on it needs no decoding forward;
     * otherwise the decoder advances to the preview's timestamp rather than landing on a different keyframe.
     */
    internal fun commitSeekParameters(): SeekParameters? =
        if (previewTrack.value != null) PreviewCommitSeek else null

    internal companion object {
        private val PreviewCommitSeek = SeekParameters.EXACT
        // The value is weak too: the state references its controller, so a strong value would keep
        // the key, and with it every finished player session, alive for the whole app session.
        private val states = WeakHashMap<PlayerRuntimeController, WeakReference<SeekPreviewState>>()

        fun forController(controller: PlayerRuntimeController): SeekPreviewState? =
            synchronized(states) { states[controller]?.get() }
    }
}

/** A change of the scrub position at least this large is a step, not a snap onto a cue start. */
private const val SCRUB_STEP_MIN_MS = 6_000L

/** Longest a new source waits for its first buffering before TorBox preview setup starts. */
private const val TORBOX_START_WAIT_MS = 15_000L

/** Position/counters participate so a full read-ahead buffer does not freeze health observations. */
private data class PreviewPlaybackSample(
    val active: Boolean, val stalled: Boolean, val positionMs: Long, val bufferedMs: Long, val droppedFrames: Int,
)

private data class PreviewLocalRequest(val durationMs: Long, val enabled: Boolean, val source: LocalPreviewSource?, val options: PreviewOptions)
