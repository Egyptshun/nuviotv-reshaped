package com.nuvio.tv.reshaped.artwork

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkDominantColorTest {

    @Test
    fun blendTowardsUsesRequestedFraction() {
        val base = Color.Black
        val target = Color.White
        val blended = base.blendTowards(target, 0.5f)
        assertEquals(0.5f, blended.red, 0.001f)
        assertEquals(0.5f, blended.green, 0.001f)
        assertEquals(0.5f, blended.blue, 0.001f)
    }
}
