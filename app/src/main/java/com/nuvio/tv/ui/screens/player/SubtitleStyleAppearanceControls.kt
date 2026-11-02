@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlin.math.roundToInt

@Composable
internal fun SubtitleStyleBackgroundControls(
    subtitleStyle: SubtitleStyleSettings,
    onEvent: (PlayerEvent) -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    SubtitleStyleSection(
        title = stringResource(R.string.subtitle_style_background),
        centerContent = false,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight + 38.dp)
    ) {
        val currentAlphaPercent = (Color(subtitleStyle.backgroundColor).alpha * 100f).roundToInt().coerceIn(0, 100)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                SubtitleBackgroundColorPresets.forEach { color ->
                    SubtitleStyleColorChip(
                        color = if (color == Color.Transparent) Color.White.copy(alpha = 0.2f) else color,
                        isSelected = when {
                            color == Color.Transparent ->
                                (Color(subtitleStyle.backgroundColor).alpha * 100f).roundToInt() == 0
                            else ->
                                Color(subtitleStyle.backgroundColor).copy(alpha = 1f).toArgb() == color.toArgb()
                        },
                        onClick = {
                            val alpha = if (color == Color.Transparent) {
                                0f
                            } else {
                                Color(subtitleStyle.backgroundColor).alpha.takeIf { it > 0f } ?: 0.75f
                            }
                            onEvent(
                                PlayerEvent.OnSetSubtitleBackgroundColor(color.copy(alpha = alpha).toArgb())
                            )
                        }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubtitleStyleStepperButton(
                    icon = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.cd_decrease),
                    onClick = {
                        val newAlpha = (currentAlphaPercent - 10).coerceAtLeast(0) / 100f
                        onEvent(
                            PlayerEvent.OnSetSubtitleBackgroundColor(
                                Color(subtitleStyle.backgroundColor).copy(alpha = newAlpha).toArgb()
                            )
                        )
                    }
                )
                SubtitleStyleValueDisplay(text = "$currentAlphaPercent%")
                SubtitleStyleStepperButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_increase),
                    onClick = {
                        val newAlpha = (currentAlphaPercent + 10).coerceAtMost(100) / 100f
                        val base = Color(subtitleStyle.backgroundColor)
                        val rgb = if ((base.alpha * 100).roundToInt() == 0 && newAlpha > 0f) {
                            Color.Black.copy(alpha = newAlpha)
                        } else {
                            base.copy(alpha = newAlpha)
                        }
                        onEvent(PlayerEvent.OnSetSubtitleBackgroundColor(rgb.toArgb()))
                    }
                )
            }
        }
    }
}

@Composable
internal fun SubtitleStyleShadowControls(
    subtitleStyle: SubtitleStyleSettings,
    onEvent: (PlayerEvent) -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    SubtitleStyleSection(
        title = stringResource(R.string.subtitle_style_shadow),
        centerContent = false,
        modifier = modifier
            .width(cardWidth)
            .height(if (subtitleStyle.shadowEnabled) cardHeight + 38.dp else cardHeight)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SubtitleStyleToggleButton(
                isEnabled = subtitleStyle.shadowEnabled,
                onClick = { onEvent(PlayerEvent.OnSetSubtitleShadowEnabled(!subtitleStyle.shadowEnabled)) }
            )
            if (subtitleStyle.shadowEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_style_shadow_opacity),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    SubtitleStyleStepperButton(
                        icon = Icons.Default.Remove,
                        onClick = {
                            onEvent(
                                PlayerEvent.OnSetSubtitleShadowOpacity(subtitleStyle.shadowOpacity - 5)
                            )
                        }
                    )
                    SubtitleStyleValueDisplay(text = "${subtitleStyle.shadowOpacity.coerceIn(0, 100)}%")
                    SubtitleStyleStepperButton(
                        icon = Icons.Default.Add,
                        onClick = {
                            onEvent(
                                PlayerEvent.OnSetSubtitleShadowOpacity(subtitleStyle.shadowOpacity + 5)
                            )
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_style_shadow_size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    SubtitleStyleStepperButton(
                        icon = Icons.Default.Remove,
                        onClick = {
                            onEvent(PlayerEvent.OnSetSubtitleShadowSize(subtitleStyle.shadowSize - 1))
                        }
                    )
                    SubtitleStyleValueDisplay(text = "${subtitleStyle.shadowSize.coerceIn(1, 8)}")
                    SubtitleStyleStepperButton(
                        icon = Icons.Default.Add,
                        onClick = {
                            onEvent(PlayerEvent.OnSetSubtitleShadowSize(subtitleStyle.shadowSize + 1))
                        }
                    )
                }
            }
        }
    }
}
