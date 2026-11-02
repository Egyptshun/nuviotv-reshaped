package com.nuvio.tv.ui.screens.player.seekpreview.grab

import com.nuvio.tv.ui.screens.player.PlayerScrubRates
import kotlin.test.Test
import kotlin.test.assertEquals

class GrabberBucketsTest {
    @Test
    fun spacingIsAtLeastTenSecondsAndAlignsToScrubSteps() {
        assertEquals(10_000L, grabberBucketSpacingMs(3_600_000L))
        assertEquals(20_000L, grabberBucketSpacingMs(25_000_000L))
    }

    @Test
    fun bucketSnapsToGrid() {
        val duration = 3_600_000L
        assertEquals(0L, grabberBucketFor(0L, duration))
        assertEquals(10_000L, grabberBucketFor(4_999L, duration))
        assertEquals(10_000L, grabberBucketFor(5_000L, duration))
        assertEquals(20_000L, grabberBucketFor(15_000L, duration))
        assertEquals(duration, grabberBucketFor(duration, duration))
    }

    @Test
    fun bucketMatchesDpadStep() {
        val duration = 3_600_000L
        val step = PlayerScrubRates.STEP_SHORT_MS
        var pos = 0L
        while (pos < duration) {
            val bucket = grabberBucketFor(pos, duration)
            assertEquals(0L, bucket % step, "pos=$pos bucket=$bucket")
            pos += step
        }
    }
}
