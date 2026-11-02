package com.nuvio.tv.ui.screens.player.seekpreview

import android.graphics.Bitmap
import com.nuvio.tv.ui.screens.player.seekpreview.colourmatch.PreviewColourAdjustment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A preview frame with the cue window it stands for.
 *
 * Cue times are on the preview timeline: the position asked for plus the track's [SeekPreviewTrack.offsetMs].
 * [approximate] marks a stand-in from a nearby moment (an on-device frame for a slot that has
 * none yet); its [bitmap] is already a low-resolution copy, so drawing it scaled up reads as
 * blurred on every API level without a blur shader.
 */
data class SeekPreviewThumbnail(
    val bitmap: Bitmap,
    val cueStartMs: Long,
    val cueEndMs: Long,
    val approximate: Boolean = false,
    /** Provenance is kept with the image, not inferred from which fallback answered last. */
    val origin: String = "UNKNOWN",
    val actualFrameMs: Long? = null,
    val crop: android.graphics.Rect? = null,
    /** Display-only calibration; original cached bytes and bitmaps stay unchanged. */
    val colourAdjustment: PreviewColourAdjustment? = null,
)

/**
 * Seek-preview thumbnails queried by playback position: frames generated on the device from the
 * playing stream, a headless ExoPlayer grabber, TorBox sprites, or a combination (see
 * [FallbackPreviewTrack]).
 */
interface SeekPreviewTrack {
    /** Signed milliseconds added to a requested position before the lookup (TorBox calibration). */
    var offsetMs: Long

    /** Built from the playing stream itself, so it never needs an external sync offset. */
    val isLocal: Boolean get() = false

    /** Bumped whenever thumbnails are added, so a visible preview re-reads its frame. */
    val revision: StateFlow<Int> get() = StaticRevision

    /** The thumbnail covering [positionMs] (after [offsetMs]) with its cue window, or null. */
    suspend fun thumbnailFor(positionMs: Long): SeekPreviewThumbnail?

    /**
     * Like [thumbnailFor], for a frame shown beside the one being scrubbed to: it does not steer
     * background work (the on-device track decodes nearest the last [thumbnailFor] first).
     */
    suspend fun sideThumbnailFor(positionMs: Long): SeekPreviewThumbnail? = thumbnailFor(positionMs)

    /** Adjacent distinct frame; generated tracks use their index rather than guessing from cue windows. */
    suspend fun neighborThumbnailFor(frame: SeekPreviewThumbnail, forward: Boolean): SeekPreviewThumbnail? {
        val probe = if (forward) frame.cueEndMs - offsetMs else frame.cueStartMs - offsetMs - 1
        if (probe < 0L) return null
        return sideThumbnailFor(probe)?.takeIf {
            if (forward) it.cueStartMs > frame.cueStartMs else it.cueStartMs < frame.cueStartMs
        }
    }

    /**
     * The playing file's keyframe nearest [positionMs] (playback timeline) when one is within
     * [toleranceMs], read from the file's own index; null when unknown. Seeking onto a keyframe
     * is both exact and the fastest seek a player can make.
     */
    fun keyframeNear(positionMs: Long, toleranceMs: Long): Long? = null
    fun noteScrubDirection(direction: Int) = Unit

    /**
     * True while a remote key is held and the scrub moves faster than a network fetch. Tracks
     * that download frames answer from what they already hold and fetch in the background.
     */
    fun noteHeldScrub(held: Boolean) = Unit
    fun externalPreviewAvailable(positionMs: Long) = Unit

    /** Stops background work; the track is not fed again. */
    fun close() = Unit
}

private val StaticRevision: StateFlow<Int> = MutableStateFlow(0)

/**
 * Primary on-device thumbnails where available; the grabber fills any slot the primary does not
 * have yet (mpv, HLS/DASH, or unplayed regions).
 *
 * Owns neither track: [SeekPreviewState] opens and closes both.
 */
internal class FallbackPreviewTrack(
    private val primary: SeekPreviewTrack,
    private val fallback: SeekPreviewTrack,
) : SeekPreviewTrack {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val changes = MutableStateFlow(0)
    override var offsetMs: Long
        get() = primary.offsetMs
        set(value) {
            primary.offsetMs = value
            fallback.offsetMs = value
        }

    override val revision: StateFlow<Int> = changes
    init { scope.launch { combine(primary.revision, fallback.revision) { a, b -> a to b }.collect { changes.value++ } } }
    override fun close() = scope.cancel()

    override suspend fun thumbnailFor(positionMs: Long): SeekPreviewThumbnail? {
        val cached = primary.sideThumbnailFor(positionMs)
        if (cached != null && !cached.approximate) {
            return primary.thumbnailFor(positionMs) ?: cached
        }
        val external = fallback.thumbnailFor(positionMs)
        if (external != null) {
            primary.externalPreviewAvailable(positionMs)
            return external
        }
        return pick(positionMs, primary.thumbnailFor(positionMs))
    }

    override suspend fun sideThumbnailFor(positionMs: Long): SeekPreviewThumbnail? =
        pick(positionMs, primary.sideThumbnailFor(positionMs), side = true)

    override suspend fun neighborThumbnailFor(frame: SeekPreviewThumbnail, forward: Boolean): SeekPreviewThumbnail? =
        if (frame.origin == "ORIGINAL" || frame.origin == "VERIFIED_COPY") {
            primary.neighborThumbnailFor(frame, forward)
        } else super<SeekPreviewTrack>.neighborThumbnailFor(frame, forward)

    private suspend fun pick(
        positionMs: Long,
        primaryThumbnail: SeekPreviewThumbnail?,
        side: Boolean = false,
    ): SeekPreviewThumbnail? {
        if (primaryThumbnail != null && !primaryThumbnail.approximate) return primaryThumbnail
        return (if (side) fallback.sideThumbnailFor(positionMs) else fallback.thumbnailFor(positionMs))
            ?: primaryThumbnail
    }

    override fun keyframeNear(positionMs: Long, toleranceMs: Long): Long? =
        primary.keyframeNear(positionMs, toleranceMs)

    override fun noteHeldScrub(held: Boolean) {
        primary.noteHeldScrub(held)
        fallback.noteHeldScrub(held)
    }
}
