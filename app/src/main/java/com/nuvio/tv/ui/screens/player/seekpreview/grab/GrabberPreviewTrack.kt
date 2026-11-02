package com.nuvio.tv.ui.screens.player.seekpreview.grab

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import com.nuvio.tv.ui.screens.player.seekpreview.SeekPreviewThumbnail
import com.nuvio.tv.ui.screens.player.seekpreview.SeekPreviewTrack
import com.nuvio.tv.ui.screens.player.seekpreview.engine.ConnectionLedger
import com.nuvio.tv.ui.screens.player.seekpreview.engine.PreviewDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.TimeSource

private const val FrameMaxWidthPx = 320
private const val MaxCachedFramesDefault = 100
private const val MaxCachedFramesLowRam = 60
private const val MaxInitialFailures = 3
private const val WarmUpDelayMs = 1_200L
private const val IdleReleaseMs = 15_000L

internal class GrabberPreviewTrack(
    context: Context,
    private val url: String,
    private val headers: Map<String, String>,
    private val durationMs: Long,
) : SeekPreviewTrack {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appContext = context.applicationContext
    private val maxCachedFrames = if (appContext.isLowRamDevice()) MaxCachedFramesLowRam else MaxCachedFramesDefault

    private val requests = MutableStateFlow<Long?>(null)
    private val cache = LinkedHashMap<Long, Bitmap>()
    private val _revision = MutableStateFlow(0)
    override val revision: StateFlow<Int> = _revision.asStateFlow()

    override var offsetMs: Long = 0L

    private var worker: Job? = null
    private var warmJob: Job? = null
    private var releaseJob: Job? = null
    private var scrubbing = false
    private var heldScrub = false
    private var consecutiveFailures = 0
    private var hasSucceeded = false
    private var unavailable = false

    @Volatile
    private var source: ExoSeekPreviewFrameSource? = null
    private var sourceOpened = false
    private var ledgerSlot: ConnectionLedger.Slot? = null
    private var connectionGeneration = 0
    private var pendingHeldBucket: Long? = null

    private val bucketMs: Long get() = grabberBucketSpacingMs(durationMs)

    override suspend fun thumbnailFor(positionMs: Long): SeekPreviewThumbnail? {
        if (unavailable || durationMs <= 0L) return null
        val shifted = (positionMs + offsetMs).coerceIn(0L, durationMs)
        val bucket = grabberBucketFor(shifted, durationMs)
        val cached = cache[bucket]
        if (cached != null) {
            cache.remove(bucket)
            cache[bucket] = cached
        }
        if (!heldScrub) {
            scrubbing = true
            warmJob?.cancel()
            releaseJob?.cancel()
            requests.value = bucket
            if (worker == null) worker = scope.launch { decodeRequests() }
        } else {
            pendingHeldBucket = bucket
        }
        val exact = cached != null
        val bitmap = cached ?: nearestCached(bucket)?.second
        return bitmap?.let { frameThumbnail(it, bucket, exact) }
    }

    override suspend fun sideThumbnailFor(positionMs: Long): SeekPreviewThumbnail? =
        cacheOnly(positionMs)

    override suspend fun neighborThumbnailFor(frame: SeekPreviewThumbnail, forward: Boolean): SeekPreviewThumbnail? {
        val probe = if (forward) frame.cueEndMs - offsetMs else frame.cueStartMs - offsetMs - 1
        if (probe < 0L) return null
        return cacheOnly(probe)?.takeIf {
            if (forward) it.cueStartMs > frame.cueStartMs else it.cueStartMs < frame.cueStartMs
        }
    }

    override fun noteHeldScrub(held: Boolean) {
        heldScrub = held
        if (!held) {
            val bucket = pendingHeldBucket
            pendingHeldBucket = null
            if (bucket != null && !unavailable) {
                requests.value = bucket
                if (worker == null) worker = scope.launch { decodeRequests() }
            }
        }
    }

    override fun keyframeNear(positionMs: Long, toleranceMs: Long): Long? = null

    fun warmUp(positionMs: Long) {
        if (unavailable || durationMs <= 0L || scrubbing) return
        releaseJob?.cancel()
        warmJob?.cancel()
        warmJob = scope.launch {
            delay(WarmUpDelayMs)
            if (scrubbing || unavailable) return@launch
            val bucket = grabberBucketFor((positionMs + offsetMs).coerceIn(0L, durationMs), durationMs)
            cache[bucket]?.let { _revision.value++ }
            requests.value = bucket
            if (worker == null) worker = scope.launch { decodeRequests() }
            scheduleRelease()
        }
    }

    fun endScrub() {
        scrubbing = false
        requests.value = null
        scheduleRelease()
    }

    override fun close() {
        scope.cancel()
        releaseConnection()
    }

    private fun cacheOnly(positionMs: Long): SeekPreviewThumbnail? {
        if (unavailable) return null
        val bucket = grabberBucketFor((positionMs + offsetMs).coerceIn(0L, durationMs), durationMs)
        val cached = cache[bucket] ?: nearestCached(bucket)?.second ?: return null
        return frameThumbnail(cached, bucket, exact = cache.containsKey(bucket))
    }

    private fun frameThumbnail(bitmap: Bitmap, bucket: Long, exact: Boolean): SeekPreviewThumbnail {
        val half = bucketMs / 2
        return SeekPreviewThumbnail(
            bitmap = bitmap,
            cueStartMs = (bucket - half).coerceAtLeast(0L),
            cueEndMs = (bucket + half).coerceAtMost(durationMs),
            approximate = !exact,
            origin = "GRAB",
        )
    }

    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IdleReleaseMs)
            if (scrubbing) return@launch
            requests.value = null
            releaseConnection()
        }
    }

    private fun releaseConnection() {
        connectionGeneration++
        source?.close()
        source = null
        sourceOpened = false
        ledgerSlot?.close()
        ledgerSlot = null
    }

    private suspend fun decodeRequests() {
        requests.filterNotNull().collect { bucket ->
            if (heldScrub) return@collect
            if (cache.containsKey(bucket) && source != null) return@collect
            val started = TimeSource.Monotonic.markNow()
            val generation = connectionGeneration
            val bitmap = try {
                withContext(decodeDispatcher) { decodeFrame(bucket) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (generation != connectionGeneration) return@collect
                onFailure(bucket, started.elapsedNow().inWholeMilliseconds, error)
                return@collect
            }
            val elapsedMs = started.elapsedNow().inWholeMilliseconds
            if (bitmap == null) {
                onFailure(bucket, elapsedMs, null)
                return@collect
            }
            PreviewDiagnostics.note("grabber frame ${bucket}ms in ${elapsedMs}ms")
            hasSucceeded = true
            consecutiveFailures = 0
            cache[bucket] = bitmap
            while (cache.size > maxCachedFrames) {
                cache.remove(cache.keys.first())
            }
            _revision.value++
        }
    }

    private fun decodeFrame(positionMs: Long): Bitmap? {
        if (!sourceOpened) {
            try {
                ledgerSlot = ConnectionLedger.app.acquire(url, maxWaitMs = 0L)
            } catch (e: ConnectionLedger.NoSlotException) {
                throw e
            }
            sourceOpened = true
            source = ExoSeekPreviewFrameSource(appContext, url, headers)
            PreviewDiagnostics.note("grabber source opened headers=${headers.size}")
        }
        val activeSource = source ?: throw UnsupportedGrabberSource
        return activeSource.frameAt(positionMs, FrameMaxWidthPx)
    }

    private fun onFailure(positionMs: Long, elapsedMs: Long, error: Throwable?) {
        consecutiveFailures++
        val reason = error?.let { "${it::class.simpleName}: ${it.message}" } ?: "no frame"
        PreviewDiagnostics.note("grabber frame at ${positionMs}ms failed after ${elapsedMs}ms: $reason")
        if (error === UnsupportedGrabberSource) {
            unavailable = true
        } else if (error is ConnectionLedger.NoSlotException) {
            // Arc or playback owns the connection budget; try again on the next scrub.
            consecutiveFailures = (consecutiveFailures - 1).coerceAtLeast(0)
        } else if (!hasSucceeded && consecutiveFailures >= MaxInitialFailures) {
            unavailable = true
        }
        if (unavailable) {
            worker?.cancel()
            releaseConnection()
        } else if (ledgerSlot != null) {
            ledgerSlot?.succeeded()
        }
    }

    private fun nearestCached(bucket: Long): Pair<Long, Bitmap>? {
        var bestKey: Long? = null
        var bestDistance = Long.MAX_VALUE
        for (key in cache.keys) {
            val distance = abs(key - bucket)
            if (distance < bestDistance) {
                bestDistance = distance
                bestKey = key
            }
        }
        val key = bestKey ?: return null
        val bitmap = cache[key] ?: return null
        return key to bitmap
    }
}

private fun Context.isLowRamDevice(): Boolean {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return am.isLowRamDevice
}
