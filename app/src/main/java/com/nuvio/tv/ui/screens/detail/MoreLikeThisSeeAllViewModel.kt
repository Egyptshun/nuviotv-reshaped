package com.nuvio.tv.ui.screens.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.core.poster.patternForScreen
import com.nuvio.tv.core.poster.withCustomPosterUrls
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.util.isUnreleased
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.MoreLikeThisSourcePreference
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.data.repository.TraktRelatedService
import com.nuvio.tv.data.simkl.SimklAuthRepository
import com.nuvio.tv.data.simkl.SimklRelatedService
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLDecoder
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "MoreLikeThisSeeAllVM"
private const val GRID_ITEM_LIMIT = 80

data class MoreLikeThisSeeAllUiState(
    val title: String = "",
    val items: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val source: MoreLikeThisSource? = null
)

@HiltViewModel
class MoreLikeThisSeeAllViewModel @Inject constructor(
    private val metaRepository: MetaRepository,
    private val tmdbMetadataService: TmdbMetadataService,
    private val tmdbService: TmdbService,
    private val traktRelatedService: TraktRelatedService,
    private val simklRelatedService: SimklRelatedService,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val traktSettingsDataStore: TraktSettingsDataStore,
    private val simklAuthRepository: SimklAuthRepository,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val watchProgressRepository: WatchProgressRepository,
    watchedSeriesStateHolder: WatchedSeriesStateHolder,
    val posterOptions: PosterOptionsController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val contentId: String = savedStateHandle.get<String>("contentId").orEmpty()
    val itemType: String = savedStateHandle.get<String>("itemType").orEmpty()
    val addonBaseUrl: String? = savedStateHandle.get<String>("addonBaseUrl")?.takeIf { it.isNotBlank() }
    val sourceRoute: String = savedStateHandle.get<String>("source").orEmpty()
    val headerTitle: String = savedStateHandle.get<String>("title").orEmpty().let { raw ->
        runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private val _uiState = MutableStateFlow(MoreLikeThisSeeAllUiState(title = headerTitle))
    val uiState: StateFlow<MoreLikeThisSeeAllUiState> = _uiState.asStateFlow()

    private val _watchedMovieIds = MutableStateFlow<Set<String>>(emptySet())
    val watchedMovieIds: StateFlow<Set<String>> = _watchedMovieIds.asStateFlow()
    val watchedSeriesIds: StateFlow<Set<String>> = watchedSeriesStateHolder.fullyWatchedSeriesIds

    init {
        posterOptions.bind(viewModelScope)
        viewModelScope.launch {
            watchProgressRepository.observeWatchedMovieIds().collect { ids ->
                _watchedMovieIds.value = ids
            }
        }
        load()
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val meta = loadMeta()
            if (meta == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "unavailable")
                return@launch
            }
            val source = resolveSource() ?: run {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "unavailable")
                return@launch
            }
            val today = LocalDate.now()
            val raw = loadRecommendations(meta, source).filterNot { it.isUnreleased(today) }
            val pattern = layoutPreferenceDataStore.customPosterUrlPattern.first()
            val enabledScreens = layoutPreferenceDataStore.customPosterEnabledScreens.first()
            val items = raw.withCustomPosterUrls(
                patternForScreen(pattern, CustomPosterScreen.DETAILS, enabledScreens)
            )
            _uiState.value = MoreLikeThisSeeAllUiState(
                title = headerTitle.ifBlank { meta.name },
                items = items,
                isLoading = false,
                source = source.takeIf { items.isNotEmpty() }
            )
        }
    }

    private suspend fun loadMeta(): Meta? {
        val flow = if (!addonBaseUrl.isNullOrBlank()) {
            metaRepository.getMeta(addonBaseUrl = addonBaseUrl, type = itemType, id = contentId)
        } else {
            metaRepository.getMetaFromPrimaryAddon(type = itemType, id = contentId)
        }
        return when (val result = flow.first { it !is NetworkResult.Loading }) {
            is NetworkResult.Success -> result.data
            else -> metaRepository.getCachedMeta(type = itemType, id = contentId)
        }
    }

    private suspend fun resolveSource(): MoreLikeThisSource? {
        return when (sourceRoute.lowercase()) {
            "trakt" -> MoreLikeThisSource.TRAKT
            "simkl" -> MoreLikeThisSource.SIMKL
            "tmdb" -> MoreLikeThisSource.TMDB
            else -> {
                val preference = traktSettingsDataStore.moreLikeThisSource.first()
                when {
                    preference == MoreLikeThisSourcePreference.SIMKL &&
                        simklAuthRepository.state.value.isAuthenticated -> MoreLikeThisSource.SIMKL
                    preference == MoreLikeThisSourcePreference.TRAKT -> MoreLikeThisSource.TRAKT
                    else -> {
                        val settings = tmdbSettingsDataStore.settings.first()
                        if (settings.enabled && settings.useMoreLikeThis) MoreLikeThisSource.TMDB else null
                    }
                }
            }
        }
    }

    private suspend fun loadRecommendations(meta: Meta, source: MoreLikeThisSource): List<MetaPreview> {
        return when (source) {
            MoreLikeThisSource.TRAKT -> runCatching {
                traktRelatedService.getRelated(meta = meta, fallbackItemId = contentId, fallbackItemType = itemType)
            }.getOrElse {
                Log.w(TAG, "Trakt related failed: ${it.message}")
                emptyList()
            }
            MoreLikeThisSource.SIMKL -> runCatching {
                simklRelatedService.getRelated(meta = meta, fallbackItemId = contentId, fallbackItemType = itemType)
            }.getOrElse {
                Log.w(TAG, "Simkl related failed: ${it.message}")
                emptyList()
            }
            MoreLikeThisSource.TMDB -> {
                val settings = tmdbSettingsDataStore.settings.first()
                val tmdbContentType = when (meta.type) {
                    ContentType.MOVIE -> ContentType.MOVIE
                    ContentType.SERIES, ContentType.TV -> ContentType.SERIES
                    else -> if (itemType.equals("movie", ignoreCase = true)) ContentType.MOVIE else ContentType.SERIES
                }
                val tmdbLookupType = tmdbContentType.toApiString()
                val tmdbId = tmdbService.ensureTmdbId(meta.id, tmdbLookupType, fallbackImdbId = meta.imdbId)
                    ?: tmdbService.ensureTmdbId(contentId, itemType, fallbackImdbId = meta.imdbId)
                if (tmdbId.isNullOrBlank()) return emptyList()
                runCatching {
                    tmdbMetadataService.fetchMoreLikeThis(
                        tmdbId = tmdbId,
                        contentType = tmdbContentType,
                        language = settings.language,
                        maxItems = GRID_ITEM_LIMIT
                    )
                }.getOrElse {
                    Log.w(TAG, "TMDB related failed: ${it.message}")
                    emptyList()
                }
            }
        }
    }
}
