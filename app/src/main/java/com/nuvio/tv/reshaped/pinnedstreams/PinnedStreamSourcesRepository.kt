package com.nuvio.tv.reshaped.pinnedstreams

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.repository.AddonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinnedStreamSourcesRepository @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager,
    private val addonRepository: AddonRepository,
    private val pluginManager: PluginManager,
) {
    private val pinnedKeysPreference = stringSetPreferencesKey("pinned_stream_source_keys")

    val pinnedKeysForActiveProfile: Flow<Set<String>> =
        profileManager.activeProfileId.flatMapLatest(::observePinnedKeys)

    fun observePinnedKeys(profileId: Int): Flow<Set<String>> =
        factory.get(profileId, DATA_STORE_NAME).data.map { preferences ->
            preferences[pinnedKeysPreference].orEmpty()
        }

    suspend fun togglePinnedSource(profileId: Int, sourceName: String, contentType: String) {
        if (sourceName.isBlank()) return
        val key = resolveKey(sourceName, contentType) ?: return
        factory.get(profileId, DATA_STORE_NAME).edit { preferences ->
            val current = preferences[pinnedKeysPreference].orEmpty().toMutableSet()
            if (current.contains(key)) {
                current.remove(key)
            } else {
                current.add(key)
            }
            preferences[pinnedKeysPreference] = current
        }
    }

    fun isPinned(sourceName: String, pinnedKeys: Set<String>): Boolean {
        if (pinnedKeys.isEmpty() || sourceName.isBlank()) return false
        return pinnedKeys.any { PinnedStreamSourceKey.sourceName(it).equals(sourceName, ignoreCase = true) }
    }

    suspend fun sortSourceNames(names: List<String>, pinnedKeys: Set<String>, contentType: String): List<String> {
        if (pinnedKeys.isEmpty()) return names
        val keyForName = buildKeyResolver(contentType)
        return applyPinnedSourceNameOrder(names, pinnedKeys, keyForName)
    }

    suspend fun sortAddonStreamGroups(
        groups: List<AddonStreams>,
        pinnedKeys: Set<String>,
        contentType: String
    ): List<AddonStreams> {
        if (pinnedKeys.isEmpty()) return groups
        val keyForName = buildKeyResolver(contentType)
        return applyPinnedAddonStreamOrder(groups, pinnedKeys, keyForName)
    }

    suspend fun mergePreferredSourceNames(
        preferredNames: Set<String>,
        pinnedKeys: Set<String>,
        contentType: String,
        candidateNames: Collection<String>,
    ): Set<String> {
        if (pinnedKeys.isEmpty()) return preferredNames
        val keyForName = buildKeyResolver(contentType)
        val pinnedNames = candidateNames.filter { name -> keyForName(name) in pinnedKeys }.toSet()
        return preferredNames + pinnedNames
    }

    suspend fun resolveKey(sourceName: String, contentType: String): String? {
        val trimmed = sourceName.trim()
        if (trimmed.isEmpty()) return null
        return buildKeyResolver(contentType)(trimmed)
    }

    private suspend fun buildKeyResolver(contentType: String): (String) -> String {
        val addons = addonRepository.getInstalledAddons().first()
        val addonIdByName = addons.associateBy({ it.displayName }, { it.id })

        val pluginNameToId = if (pluginManager.pluginsEnabled.first()) {
            val scrapers = pluginManager.enabledScrapers.first().filter { it.supportsType(contentType) }
            val groupByRepository = pluginManager.groupStreamsByRepository.first()
            val repositoriesById = pluginManager.repositories.first().associateBy { it.id }
            scrapers.associate { scraper ->
                val displayName = if (groupByRepository) {
                    repositoriesById[scraper.repositoryId]?.name?.takeIf { it.isNotBlank() } ?: scraper.name
                } else {
                    scraper.name
                }
                displayName to scraper.id
            }
        } else {
            emptyMap()
        }

        return { sourceName ->
            val trimmed = sourceName.trim()
            addonIdByName.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.let { entry ->
                PinnedStreamSourceKey.encode(entry.value, trimmed)
            } ?: pluginNameToId.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.let { entry ->
                PinnedStreamSourceKey.encode(entry.value, trimmed)
            } ?: PinnedStreamSourceKey.encode(SPECIAL_SOURCE_ID, trimmed)
        }
    }

    private companion object {
        /** Media server, embedded, and other non-addon sources share a stable id segment. */
        const val SPECIAL_SOURCE_ID = "source"
        const val DATA_STORE_NAME = "pinned_stream_sources"
    }
}
