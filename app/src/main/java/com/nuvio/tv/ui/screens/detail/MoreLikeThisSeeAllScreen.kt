@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun MoreLikeThisSeeAllScreen(
    viewModel: MoreLikeThisSeeAllViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String, String?) -> Unit,
    onBackPress: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val watchedMovieIds by viewModel.watchedMovieIds.collectAsState()
    val watchedSeriesIds by viewModel.watchedSeriesIds.collectAsState()
    val landscapeStyle = remember {
        PosterCardStyle(
            width = 260.dp,
            height = 146.dp,
            cornerRadius = 12.dp,
            focusedBorderWidth = NuvioTheme.spacing.xxs,
            focusedScale = 1.02f
        )
    }

    BackHandler(onBack = onBackPress)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = NuvioTheme.spacing.xl)
    ) {
        Text(
            text = uiState.title.ifBlank { stringResource(R.string.more_like_this_see_all_title) },
            style = MaterialTheme.typography.headlineLarge,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxxl)
        )
        when (uiState.source) {
            MoreLikeThisSource.TMDB -> stringResource(R.string.detail_more_like_this_powered_by_tmdb)
            MoreLikeThisSource.TRAKT -> stringResource(R.string.detail_more_like_this_powered_by_trakt)
            MoreLikeThisSource.SIMKL -> stringResource(R.string.detail_more_like_this_powered_by_simkl)
            null -> null
        }?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary,
                modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.xs)
            )
        }

        when {
            uiState.isLoading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
            uiState.errorMessage != null -> EmptyScreenState(
                title = stringResource(R.string.error_generic),
                modifier = Modifier.fillMaxSize()
            )
            uiState.items.isEmpty() -> EmptyScreenState(
                title = stringResource(R.string.search_no_results_title),
                modifier = Modifier.fillMaxSize()
            )
            else -> {
                val gridState = rememberLazyGridState()
                val firstFocus = remember { FocusRequester() }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = landscapeStyle.width),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = NuvioTheme.spacing.xxxl,
                        vertical = NuvioTheme.spacing.md
                    ),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
                ) {
                    itemsIndexed(uiState.items, key = { _, item -> "${item.apiType}:${item.id}" }) { index, item ->
                        val watched = when (item.apiType.lowercase()) {
                            "movie" -> watchedMovieIds.contains(item.id)
                            else -> watchedSeriesIds.contains(item.id)
                        }
                        GridContentCard(
                            item = item,
                            onClick = { onNavigateToDetail(item.id, item.apiType, null) },
                            posterCardStyle = landscapeStyle,
                            showLabel = true,
                            imageCrossfade = true,
                            isWatched = watched,
                            focusRequester = if (index == 0) firstFocus else null
                        )
                    }
                }
            }
        }
    }
}
