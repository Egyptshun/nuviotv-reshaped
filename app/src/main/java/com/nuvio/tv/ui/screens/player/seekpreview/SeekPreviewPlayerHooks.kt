package com.nuvio.tv.ui.screens.player.seekpreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.SeekParameters
import com.nuvio.tv.ui.screens.player.PlayerEvent
import com.nuvio.tv.ui.screens.player.PlayerRuntimeController
import com.nuvio.tv.ui.screens.player.PlayerViewModel

/*
 * Seek-preview integration points for the TV player. Everything the player needs lives
 * here and in this package, so upstream player files only carry short calls in.
 */

/** Ticks closer than this are noise rather than information, so the grid is hidden instead. */
private val MinCueTickSpacing = 5.dp

/** Upper bound on tick count, so a long title cannot turn the scrubber into a solid block. */
private const val MaxCueTicks = 400L

/**
 * Seek-preview thumbnails above the controls' progress bar. Takes no height in the layout —
 * it is drawn above whatever precedes the bar — so the controls never shift.
 */
@Composable
fun SeekPreviewAboveProgressBar(viewModel: PlayerViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, 0) {
                    placeable.placeRelative(0, -placeable.height)
                }
            }
    ) {
        SeekPreviewThumbnailHost(viewModel = viewModel)
    }
}

/**
 * Cue ticks across a progress bar, at the positions grid-locked scrubbing can stop on.
 * Suppressed once they would be denser than the eye can separate — at that point the 10s grid
 * is a rounding error on the bar, and the preview frame carries the granularity.
 */
@Composable
fun SeekPreviewCueTicks(viewModel: PlayerViewModel, durationMs: Long, modifier: Modifier) {
    val cueIntervalMs by viewModel.seekPreview.cueIntervalMs.collectAsStateWithLifecycle()
    if (cueIntervalMs <= 0L || durationMs <= 0L) return
    val tickCount = durationMs / cueIntervalMs
    if (tickCount !in 2..MaxCueTicks) return
    Canvas(modifier = modifier) {
        val stepPx = size.width * (cueIntervalMs.toFloat() / durationMs.toFloat())
        if (stepPx < MinCueTickSpacing.toPx()) return@Canvas
        var x = stepPx
        while (x < size.width) {
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
            x += stepPx
        }
    }
}

/**
 * Seek parameters for committing a D-pad preview scrub: lands on the frame the preview showed
 * while previews are on (see [SeekPreviewState.commitSeekParameters]), the player's closest
 * keyframe otherwise.
 */
internal fun PlayerRuntimeController.seekPreviewCommitSeekParameters(): SeekParameters =
    SeekPreviewState.forController(this)?.commitSeekParameters() ?: SeekParameters.CLOSEST_SYNC

/** Stops on-device generation when playback errors. */
@Composable
fun BoxScope.SeekPreviewErrorHook(viewModel: PlayerViewModel, hasError: Boolean) {
    LaunchedEffect(hasError) {
        if (hasError) viewModel.seekPreview.stopGenerationForError()
    }
}
