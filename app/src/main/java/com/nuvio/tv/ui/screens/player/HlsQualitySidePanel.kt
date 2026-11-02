@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.reshaped.playerquality.HlsQualityAutoId
import com.nuvio.tv.reshaped.playerquality.HlsQualitySelectionState
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun HlsQualitySidePanel(
    visible: Boolean,
    state: HlsQualitySelectionState,
    selectedQualityId: String?,
    onQualitySelected: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible, state.variants) {
        if (visible && state.variants.isNotEmpty()) {
            focusRequester.requestFocusAfterFrames()
        }
    }

    Column(
        modifier = modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(Color(0xE6101010))
            .padding(NuvioTheme.spacing.lg)
    ) {
        Text(
            text = stringResource(R.string.player_hls_quality_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = NuvioTheme.spacing.md)
        )
        if (state.isLoading) {
            Text(stringResource(R.string.player_hls_quality_loading), color = Color.White.copy(alpha = 0.7f))
            return@Column
        }
        state.errorMessage?.let { message ->
            Text(message, color = Color.White.copy(alpha = 0.7f))
            return@Column
        }
        val options = buildList {
            add(null to stringResource(R.string.player_hls_quality_auto))
            state.variants.forEach { variant ->
                add(variant.id to variant.displayLabel)
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
            contentPadding = PaddingValues(bottom = NuvioTheme.spacing.lg)
        ) {
            items(options, key = { it.first ?: HlsQualityAutoId }) { (id, label) ->
                val selected = when {
                    id == null -> selectedQualityId.isNullOrBlank()
                    else -> selectedQualityId == id
                }
                Card(
                    onClick = {
                        onQualitySelected(id)
                        onDismiss()
                    },
                    modifier = Modifier.then(
                        if (options.firstOrNull()?.first == id) Modifier else Modifier
                    ),
                    colors = CardDefaults.colors(
                        containerColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White.copy(alpha = 0.28f)
                    )
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = NuvioTheme.spacing.lg, vertical = 12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
