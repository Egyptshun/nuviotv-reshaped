@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.format.formatUsdAmountForDisplay
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun MovieFinancialInfoRow(
    meta: Meta,
    modifier: Modifier = Modifier
) {
    if (meta.type != ContentType.MOVIE) return
    val budgetLabel = remember(meta.budget) { formatUsdAmountForDisplay(meta.budget) }
    val revenueLabel = remember(meta.revenue) { formatUsdAmountForDisplay(meta.revenue) }
    if (budgetLabel == null && revenueLabel == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = NuvioTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
    ) {
        budgetLabel?.let { value ->
            FinancialChip(label = stringResource(R.string.detail_budget), value = value)
        }
        revenueLabel?.let { value ->
            FinancialChip(label = stringResource(R.string.detail_revenue), value = value)
        }
    }
}

@Composable
private fun FinancialChip(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.labelLarge,
        color = NuvioTheme.extendedColors.textSecondary
    )
}
