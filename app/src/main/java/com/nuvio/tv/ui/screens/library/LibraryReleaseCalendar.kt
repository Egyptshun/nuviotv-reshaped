package com.nuvio.tv.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class LibraryReleaseDay(
    val date: LocalDate,
    val entries: List<LibraryEntry>
)

internal fun groupLibraryEntriesByReleaseMonth(entries: List<LibraryEntry>): List<LibraryReleaseDay> {
    val today = LocalDate.now()
    val byDate = linkedMapOf<LocalDate, MutableList<LibraryEntry>>()
    entries.forEach { entry ->
        val year = entry.releaseInfo?.trim()?.take(4)?.toIntOrNull() ?: return@forEach
        val date = when {
            entry.releaseInfo.contains("-") -> runCatching {
                LocalDate.parse(entry.releaseInfo.trim().take(10))
            }.getOrNull()
            year >= today.year -> LocalDate.of(year, 1, 1)
            else -> null
        } ?: return@forEach
        if (date.isBefore(today.minusDays(1))) return@forEach
        byDate.getOrPut(date) { mutableListOf() }.add(entry)
    }
    return byDate.entries
        .sortedBy { it.key }
        .map { (date, list) -> LibraryReleaseDay(date, list.distinctBy { it.id }) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryReleaseCalendarContent(
    entries: List<LibraryEntry>,
    onNavigateToDetail: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = remember(entries) { groupLibraryEntriesByReleaseMonth(entries) }
    if (days.isEmpty()) {
        EmptyScreenState(
            title = stringResource(R.string.library_calendar_empty),
            modifier = modifier.fillMaxSize()
        )
        return
    }
    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
    ) {
        items(days, key = { it.date.toString() }) { day ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = day.date.format(monthFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioTheme.colors.TextPrimary,
                    modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
                )
                day.entries.forEach { entry ->
                    GridContentCard(
                        item = entry.toMetaPreview(),
                        onClick = {
                            onNavigateToDetail(entry.id, entry.type, entry.addonBaseUrl)
                        },
                        posterCardStyle = PosterCardDefaults.Style,
                        showLabel = true
                    )
                }
            }
        }
    }
}
