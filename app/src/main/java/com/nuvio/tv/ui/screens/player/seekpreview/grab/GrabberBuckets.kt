package com.nuvio.tv.ui.screens.player.seekpreview.grab

import com.nuvio.tv.ui.screens.player.PlayerScrubRates
import kotlin.math.max

/** Spacing between grabber preview buckets for a title of [durationMs]. */
internal fun grabberBucketSpacingMs(durationMs: Long): Long {
    if (durationMs <= 0L) return PlayerScrubRates.STEP_SHORT_MS
    val raw = max(PlayerScrubRates.STEP_SHORT_MS, durationMs / 1_000L)
    val step = PlayerScrubRates.STEP_SHORT_MS
    return ((raw + step / 2 - 1) / step) * step
}

/** Rounds [positionMs] to the nearest grabber bucket on the D-pad cue grid. */
internal fun grabberBucketFor(positionMs: Long, durationMs: Long): Long {
    val bucketMs = grabberBucketSpacingMs(durationMs)
    val clamped = positionMs.coerceIn(0L, durationMs)
    if (clamped == 0L) return 0L
    return ((clamped + bucketMs - 1) / bucketMs * bucketMs).coerceAtMost(durationMs)
}
