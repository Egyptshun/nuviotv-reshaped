package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

enum class EpisodeListSort {
    DEFAULT,
    REVERSE,
    AIR_DATE
}

internal fun sortEpisodesForDisplay(episodes: List<Video>, sort: EpisodeListSort): List<Video> {
    return when (sort) {
        EpisodeListSort.DEFAULT -> episodes.sortedWith(
            compareBy({ it.season ?: 0 }, { it.episode ?: 0 })
        )
        EpisodeListSort.REVERSE -> episodes.sortedWith(
            compareBy<Video> { it.season ?: 0 }
                .thenByDescending { it.episode ?: 0 }
        )
        EpisodeListSort.AIR_DATE -> episodes.sortedWith(
            compareBy<Video> { it.released.orEmpty() }
                .thenBy { it.episode ?: 0 }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailEpisodeNavigatorBar(
    maxEpisodeNumber: Int,
    sort: EpisodeListSort,
    onSortChange: (EpisodeListSort) -> Unit,
    onJumpToEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showJumpDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        Button(onClick = { showJumpDialog = true }) {
            Text(stringResource(R.string.detail_episode_navigator_jump))
        }
        Button(onClick = { showSortDialog = true }) {
            Text(
                text = when (sort) {
                    EpisodeListSort.DEFAULT -> stringResource(R.string.detail_episode_sort_default)
                    EpisodeListSort.REVERSE -> stringResource(R.string.detail_episode_sort_reverse)
                    EpisodeListSort.AIR_DATE -> stringResource(R.string.detail_episode_sort_air_date)
                }
            )
        }
    }

    if (showJumpDialog) {
        EpisodeJumpDialog(
            maxEpisode = maxEpisodeNumber.coerceAtLeast(1),
            onDismiss = { showJumpDialog = false },
            onConfirm = { number ->
                showJumpDialog = false
                onJumpToEpisode(number)
            }
        )
    }

    if (showSortDialog) {
        NuvioDialog(
            title = stringResource(R.string.detail_episode_sort_title),
            onDismiss = { showSortDialog = false }
        ) {
            EpisodeListSort.entries.forEach { option ->
                Button(
                    onClick = {
                        onSortChange(option)
                        showSortDialog = false
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        when (option) {
                            EpisodeListSort.DEFAULT -> stringResource(R.string.detail_episode_sort_default)
                            EpisodeListSort.REVERSE -> stringResource(R.string.detail_episode_sort_reverse)
                            EpisodeListSort.AIR_DATE -> stringResource(R.string.detail_episode_sort_air_date)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeJumpDialog(
    maxEpisode: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var target by remember { mutableIntStateOf(1) }
    NuvioDialog(
        title = stringResource(R.string.detail_episode_navigator_jump_title),
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.detail_episode_navigator_jump_subtitle, maxEpisode),
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
            Button(onClick = { target = (target - 1).coerceAtLeast(1) }) {
                Text("-")
            }
            Text(
                text = target.toString(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md)
            )
            Button(onClick = { target = (target + 1).coerceAtMost(maxEpisode) }) {
                Text("+")
            }
        }
        Button(onClick = { onConfirm(target.coerceIn(1, maxEpisode)) }) {
            Text(stringResource(R.string.action_apply))
        }
    }
}
