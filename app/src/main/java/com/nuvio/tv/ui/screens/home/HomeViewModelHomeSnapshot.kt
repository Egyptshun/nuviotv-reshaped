package com.nuvio.tv.ui.screens.home

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.reshaped.homesnapshot.HomeSnapshot
import com.nuvio.tv.reshaped.homesnapshot.HomeSnapshotStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal suspend fun HomeViewModel.restoreHomeSnapshotIfNeeded() {
    val profileId = profileManager.activeProfileId.value
    val generation = homeSnapshotGeneration
    val preloaded = preloadedHomeSnapshot?.takeIf { it.profileId == profileId }
    preloadedHomeSnapshot = null
    val snapshot = preloaded ?: homeSnapshotStore.load(profileId) ?: return
    if (generation != homeSnapshotGeneration) return
    if (hasAnyCatalogRows()) return
    applyHomeSnapshot(snapshot)
}

internal fun HomeViewModel.onHomeProfileChanged(newProfileId: Int) {
    homeSnapshotGeneration++
    preloadedHomeSnapshot = null
    homeSnapshotSaveJob?.cancel()
    homeSnapshotSaveJob = null
    cancelInFlightCatalogLoads()
    clearCatalogData()
    catalogLoadGeneration++
    _uiState.update {
        it.copy(
            catalogRows = emptyList(),
            homeRows = emptyList(),
            heroItems = emptyList(),
            gridItems = emptyList(),
            isLoading = true,
            error = null,
        )
    }
    viewModelScope.launch {
        val generation = homeSnapshotGeneration
        val snapshot = homeSnapshotStore.load(newProfileId)
        if (generation != homeSnapshotGeneration) return@launch
        if (snapshot != null) {
            applyHomeSnapshot(snapshot)
        }
        if (addonsCache.isNotEmpty()) {
            loadAllCatalogsPipeline(addonsCache, forceReload = true)
        }
    }
}

private fun HomeViewModel.applyHomeSnapshot(snapshot: HomeSnapshot) {
    if (snapshot.sections.isEmpty()) return
    synchronized(catalogStateLock) {
        catalogOrder.clear()
        snapshot.catalogOrderKeys.forEach { key ->
            if (key !in catalogOrder) catalogOrder.add(key)
        }
        snapshot.sections.forEach { row ->
            val key = row.legacyKey()
            if (key !in catalogOrder) catalogOrder.add(key)
            replaceCatalogRow(key, row)
        }
    }
    heroItemOrder = snapshot.heroItems.map { it.id }
    hasRenderedFirstCatalog = true
    _uiState.update {
        it.copy(
            heroItems = snapshot.heroItems,
            isLoading = true,
            error = null,
        )
    }
    scheduleUpdateCatalogRows()
}

internal fun HomeViewModel.scheduleHomeSnapshotSave() {
    if (catalogsLoadInProgress || pendingCatalogLoads > 0) return
    val (orderedKeys, catalogSnapshot) = snapshotCatalogState()
    val sections = orderedKeys.mapNotNull { key ->
        catalogSnapshot[key]?.takeIf { it.items.isNotEmpty() }
    }
    if (sections.isEmpty()) return
    val heroItems = _uiState.value.heroItems
    val profileId = profileManager.activeProfileId.value
    homeSnapshotSaveJob?.cancel()
    homeSnapshotSaveJob = viewModelScope.launch {
        delay(homeSnapshotStore.saveDelayMs)
        val (latestKeys, latestSnapshot) = snapshotCatalogState()
        val latestSections = latestKeys.mapNotNull { key ->
            latestSnapshot[key]?.takeIf { it.items.isNotEmpty() }
        }
        if (latestSections.isEmpty()) return@launch
        homeSnapshotStore.save(
            profileId = profileId,
            savedAtMillis = SystemClock.elapsedRealtime(),
            catalogOrderKeys = latestKeys,
            sections = latestSections,
            heroItems = _uiState.value.heroItems.ifEmpty { heroItems },
        )
    }
}
