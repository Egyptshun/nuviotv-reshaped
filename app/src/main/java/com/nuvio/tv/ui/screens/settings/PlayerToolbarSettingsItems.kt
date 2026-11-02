package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.ui.reshaped.player.PlayerToolbarButtonId
import kotlinx.coroutines.launch

@Composable
fun PlayerToolbarSettingsItems(
    viewModel: PlaybackSettingsViewModel = hiltViewModel()
) {
    val hidden by viewModel.playerSettingsDataStore.hiddenPlayerToolbarButtons.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    PlayerToolbarButtonId.entries.forEach { button ->
        SettingsToggleRow(
            title = stringResource(button.titleRes()),
            subtitle = stringResource(button.subtitleRes()),
            checked = button.storageKey !in hidden,
            onToggle = {
                scope.launch {
                    val currentlyVisible = button.storageKey !in hidden
                    viewModel.playerSettingsDataStore.setPlayerToolbarButtonHidden(
                        button.storageKey,
                        hidden = currentlyVisible,
                    )
                }
            }
        )
    }
}

private fun PlayerToolbarButtonId.titleRes(): Int = when (this) {
    PlayerToolbarButtonId.SUBTITLE -> R.string.player_toolbar_subtitle
    PlayerToolbarButtonId.AUDIO -> R.string.player_toolbar_audio
    PlayerToolbarButtonId.CHAPTERS -> R.string.player_toolbar_chapters
    PlayerToolbarButtonId.SOURCES -> R.string.player_toolbar_sources
    PlayerToolbarButtonId.EPISODES -> R.string.player_toolbar_episodes
    PlayerToolbarButtonId.PARTY -> R.string.player_toolbar_party
    PlayerToolbarButtonId.MORE -> R.string.player_toolbar_more
}

private fun PlayerToolbarButtonId.subtitleRes(): Int = when (this) {
    PlayerToolbarButtonId.SUBTITLE -> R.string.player_toolbar_subtitle_sub
    PlayerToolbarButtonId.AUDIO -> R.string.player_toolbar_audio_sub
    PlayerToolbarButtonId.CHAPTERS -> R.string.player_toolbar_chapters_sub
    PlayerToolbarButtonId.SOURCES -> R.string.player_toolbar_sources_sub
    PlayerToolbarButtonId.EPISODES -> R.string.player_toolbar_episodes_sub
    PlayerToolbarButtonId.PARTY -> R.string.player_toolbar_party_sub
    PlayerToolbarButtonId.MORE -> R.string.player_toolbar_more_sub
}
