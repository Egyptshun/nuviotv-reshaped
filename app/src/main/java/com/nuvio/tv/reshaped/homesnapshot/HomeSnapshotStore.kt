package com.nuvio.tv.reshaped.homesnapshot

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.legacyKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal data class HomeSnapshot(
    val profileId: Int,
    val catalogOrderKeys: List<String>,
    val sections: List<CatalogRow>,
    val heroItems: List<MetaPreview>,
)

private data class HomeSnapshotMetaDto(
    val id: String,
    val apiType: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val posterShape: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: Float? = null,
    val genres: List<String> = emptyList(),
    val landscapePoster: String? = null,
    val rawPosterUrl: String? = null,
)

private data class HomeSnapshotSectionDto(
    val cacheKey: String,
    val addonId: String,
    val addonName: String,
    val addonBaseUrl: String,
    val catalogId: String,
    val catalogName: String,
    val apiType: String,
    val items: List<HomeSnapshotMetaDto>,
    val hasMore: Boolean = true,
    val currentPage: Int = 0,
    val supportsSkip: Boolean = false,
    val skipStep: Int = 100,
    val nextSkip: Int = 0,
)

private data class HomeSnapshotPayload(
    val profileId: Int,
    val savedAtMillis: Long,
    val catalogOrderKeys: List<String> = emptyList(),
    val sections: List<HomeSnapshotSectionDto> = emptyList(),
    val heroItems: List<HomeSnapshotMetaDto> = emptyList(),
)

@Singleton
class HomeSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
) {
    companion object {
        private const val TAG = "HomeSnapshot"
        private const val SAVE_DELAY_MS = 1_500L
    }

    private val gson = Gson()
    private val mutex = Mutex()

    val saveDelayMs: Long = SAVE_DELAY_MS

    private fun snapshotFile(profileId: Int): File {
        val dir = File(context.filesDir, "home_snapshot")
        dir.mkdirs()
        return File(dir, "home_$profileId.json")
    }

    suspend fun load(profileId: Int = profileManager.activeProfileId.value): HomeSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val file = snapshotFile(profileId)
                if (!file.exists()) return@withContext null
                val payload = gson.fromJson(file.readText(), HomeSnapshotPayload::class.java)
                    ?: return@withContext null
                if (payload.profileId != profileId) return@withContext null
                val sections = payload.sections.mapNotNull { it.toCatalogRow() }
                if (sections.isEmpty()) return@withContext null
                HomeSnapshot(
                    profileId = profileId,
                    catalogOrderKeys = payload.catalogOrderKeys,
                    sections = sections,
                    heroItems = payload.heroItems.map { it.toMetaPreview() },
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to read home snapshot: ${error.message}")
            }.getOrNull()
        }
    }

    suspend fun save(
        profileId: Int,
        savedAtMillis: Long,
        catalogOrderKeys: List<String>,
        sections: List<CatalogRow>,
        heroItems: List<MetaPreview>,
    ) = withContext(Dispatchers.IO) {
        if (sections.isEmpty()) return@withContext
        mutex.withLock {
            runCatching {
                val payload = HomeSnapshotPayload(
                    profileId = profileId,
                    savedAtMillis = savedAtMillis,
                    catalogOrderKeys = catalogOrderKeys,
                    sections = sections.map { it.toDto() },
                    heroItems = heroItems.map { it.toDto() },
                )
                snapshotFile(profileId).writeText(gson.toJson(payload))
            }.onFailure { error ->
                Log.w(TAG, "Failed to write home snapshot: ${error.message}")
            }
        }
    }
}

private fun MetaPreview.toDto(): HomeSnapshotMetaDto = HomeSnapshotMetaDto(
    id = id,
    apiType = apiType,
    name = name,
    poster = poster,
    background = background,
    logo = logo,
    posterShape = posterShape.name,
    description = description,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    genres = genres,
    landscapePoster = landscapePoster,
    rawPosterUrl = rawPosterUrl,
)

private fun HomeSnapshotMetaDto.toMetaPreview(): MetaPreview = MetaPreview(
    id = id,
    type = ContentType.fromString(apiType),
    rawType = apiType,
    name = name,
    poster = poster,
    posterShape = posterShape?.let { name ->
        PosterShape.entries.firstOrNull { it.name == name }
    } ?: PosterShape.POSTER,
    background = background,
    logo = logo,
    description = description,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    genres = genres,
    landscapePoster = landscapePoster,
    rawPosterUrl = rawPosterUrl,
)

private fun CatalogRow.toDto(): HomeSnapshotSectionDto = HomeSnapshotSectionDto(
    cacheKey = legacyKey(),
    addonId = addonId,
    addonName = addonName,
    addonBaseUrl = addonBaseUrl,
    catalogId = catalogId,
    catalogName = catalogName,
    apiType = apiType,
    items = items.map { it.toDto() },
    hasMore = hasMore,
    currentPage = currentPage,
    supportsSkip = supportsSkip,
    skipStep = skipStep,
    nextSkip = nextSkip,
)

private fun HomeSnapshotSectionDto.toCatalogRow(): CatalogRow? {
    if (items.isEmpty()) return null
    val contentType = ContentType.fromString(apiType)
    return CatalogRow(
        addonId = addonId,
        addonName = addonName,
        addonBaseUrl = addonBaseUrl,
        catalogId = catalogId,
        catalogName = catalogName,
        type = contentType,
        rawType = apiType,
        items = items.map { it.toMetaPreview() },
        isLoading = false,
        hasMore = hasMore,
        currentPage = currentPage,
        supportsSkip = supportsSkip,
        skipStep = skipStep,
        nextSkip = nextSkip,
    )
}
