package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UserRatingDialog(
    titleName: String,
    initialRating: Int = 8,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit
) {
    var rating by remember { mutableIntStateOf(initialRating.coerceIn(1, 10)) }
    NuvioDialog(title = stringResource(R.string.user_rating_title, titleName), onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.user_rating_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
            Button(onClick = { rating = (rating - 1).coerceAtLeast(1) }) { Text("-") }
            Text(
                text = rating.toString(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md)
            )
            Button(onClick = { rating = (rating + 1).coerceAtMost(10) }) { Text("+") }
        }
        Button(onClick = { onSubmit(rating) }) {
            Text(stringResource(R.string.action_apply))
        }
    }
}
