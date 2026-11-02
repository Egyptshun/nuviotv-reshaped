package com.nuvio.tv.reshaped.artwork

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private const val DOMINANT_BLEND_FRACTION = 0.42f
private const val COLOR_TRANSITION_MS = 600

@Composable
fun DynamicArtworkBackground(
    enabled: Boolean,
    artworkUrl: String?,
    baseColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val context = LocalContext.current
    val key = artworkUrl?.takeIf { it.isNotBlank() }
    var tint by remember(key) { mutableStateOf(key?.let(ArtworkDominantColorCache::get)) }
    var failed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, enabled) {
        if (!enabled || key == null || tint != null) return@LaunchedEffect
        val sampled = resolveArtworkDominantColor(context, key)
        if (sampled != null) {
            tint = sampled
        } else {
            failed = true
        }
    }

    val targetColor = tint?.let { baseColor.blendTowards(it, DOMINANT_BLEND_FRACTION) } ?: baseColor
    val washColor by animateColorAsState(
        targetValue = if (failed && tint == null) baseColor else targetColor,
        animationSpec = tween(durationMillis = COLOR_TRANSITION_MS, easing = LinearOutSlowInEasing),
        label = "dynamic_artwork_wash_color",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(washColor) },
    ) {
        content()
    }
}
