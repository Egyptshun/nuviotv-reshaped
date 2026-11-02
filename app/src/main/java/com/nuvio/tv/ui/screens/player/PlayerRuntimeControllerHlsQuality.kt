package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.reshaped.playerquality.HlsQualityAutoId
import com.nuvio.tv.reshaped.playerquality.HlsQualityResolver
import com.nuvio.tv.reshaped.playerquality.HlsQualitySelectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val HLS_QUALITY_TAG = "PlayerHlsQuality"

internal fun PlayerRuntimeController.resetHlsQualityForNewStream(sourceUrl: String) {
    hlsMasterSourceUrl = sourceUrl.trim()
    selectedHlsQualityId = null
    hlsQualityResolveJob?.cancel()
    _uiState.update {
        it.copy(
            hlsQualityState = HlsQualitySelectionState(isLoading = true, sourceUrl = sourceUrl),
            selectedHlsQualityId = null,
            showHlsQualityOverlay = false,
        )
    }
    hlsQualityResolveJob = scope.launch {
        val masterUrl = hlsMasterSourceUrl ?: sourceUrl
        val state = HlsQualityResolver.resolve(masterUrl, currentHeaders)
        _uiState.update {
            it.copy(
                hlsQualityState = state,
                selectedHlsQualityId = null,
            )
        }
        if (state.hasSelectableQualities) {
            Log.i(HLS_QUALITY_TAG, "HLS qualities available: ${state.variants.size}")
        }
    }
}

internal fun PlayerRuntimeController.selectHlsQuality(qualityId: String?) {
    val normalizedId = qualityId?.takeUnless { it == HlsQualityAutoId }
    selectedHlsQualityId = normalizedId
    val state = _uiState.value.hlsQualityState
    val playbackUrl = state.playbackUrlFor(normalizedId) ?: return
    val resumeMs = currentPlaybackPositionMs() ?: 0L
    val wasPlaying = _uiState.value.isPlaying

    _uiState.update {
        it.copy(
            selectedHlsQualityId = normalizedId,
            showHlsQualityOverlay = false,
        )
    }

    if (playbackUrl == currentStreamUrl) return

    releasePlayer(flushPlaybackState = false)
    currentStreamUrl = playbackUrl
    _uiState.update { it.copy(currentStreamUrl = playbackUrl) }
    if (resumeMs > 0L) {
        _uiState.update { it.copy(pendingSeekPosition = resumeMs) }
    }
    initializePlayer(
        url = playbackUrl,
        headers = currentHeaders,
        startPaused = !wasPlaying,
    )
}

internal fun PlayerRuntimeController.showHlsQualityOverlay() {
    if (!_uiState.value.hlsQualityState.hasSelectableQualities) return
    _uiState.update {
        it.copy(
            showHlsQualityOverlay = true,
            showAudioOverlay = false,
            showSubtitleOverlay = false,
        )
    }
    hideControlsJob?.cancel()
}

internal fun PlayerRuntimeController.dismissHlsQualityOverlay() {
    _uiState.update { it.copy(showHlsQualityOverlay = false) }
    scheduleHideControls()
}
