package com.nuvio.tv.core.tracking

import com.nuvio.tv.data.mdblist.MdbListAuthStore
import com.nuvio.tv.data.mdblist.MdbListRatingsWriteService
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktIdsDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRatedMovieDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRatedShowDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRatingRequestDto
import com.nuvio.tv.data.repository.TraktAuthService
import com.nuvio.tv.data.simkl.SimklAuthRepository
import com.nuvio.tv.data.simkl.SimklMutationService
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class UserRatingSubmitResult(
    val trakt: Result<Unit>? = null,
    val simkl: Result<Unit>? = null,
    val mdblist: Result<Unit>? = null
) {
    val anySuccess: Boolean
        get() = listOfNotNull(trakt, simkl, mdblist).any(Result<Unit>::isSuccess)

    val allFailed: Boolean
        get() {
            val attempts = listOfNotNull(trakt, simkl, mdblist)
            return attempts.isNotEmpty() && attempts.all(Result<Unit>::isFailure)
        }
}

@Singleton
class UserRatingCoordinator @Inject constructor(
    private val traktApi: TraktApi,
    private val traktAuthService: TraktAuthService,
    private val traktAuthDataStore: TraktAuthDataStore,
    private val simklMutationService: SimklMutationService,
    private val simklAuthRepository: SimklAuthRepository,
    private val mdbListRatingsWriteService: MdbListRatingsWriteService,
    private val mdbListAuthStore: MdbListAuthStore
) {
    suspend fun submitRating(
        meta: Meta,
        rating: Int,
        imdbId: String?,
        tmdbId: Int?,
        traktId: Int?
    ): UserRatingSubmitResult {
        val media = buildTrackingMediaReference(
            contentType = meta.apiType,
            parentMetaId = meta.id,
            title = meta.name,
            releaseInfo = meta.releaseInfo
        )
        val score = rating.coerceIn(1, 10)

        val traktResult = if (traktAuthDataStore.isAuthenticated.first()) {
            runCatching { submitTraktRating(meta, score, imdbId, tmdbId, traktId).getOrThrow() }
        } else {
            null
        }

        val simklResult = if (simklAuthRepository.state.value.isAuthenticated) {
            runCatching {
                val mutation = simklMutationService.addRating(media, score)
                if (mutation.notFoundCount == 0) Unit
                else error("Simkl rating was not applied")
            }
        } else {
            null
        }

        val mdblistResult = if (mdbListAuthStore.state.value.isAuthenticated) {
            val scope = mdbListAuthStore.scope()
            runCatching {
                mdbListRatingsWriteService.submitRating(scope, media, score).getOrThrow()
            }
        } else {
            null
        }

        return UserRatingSubmitResult(
            trakt = traktResult,
            simkl = simklResult,
            mdblist = mdblistResult
        )
    }

    private suspend fun submitTraktRating(
        meta: Meta,
        rating: Int,
        imdbId: String?,
        tmdbId: Int?,
        traktId: Int?
    ): Result<Unit> {
        return traktAuthService.executeAuthorizedRequest { authHeader ->
            val ids = TraktIdsDto(
                trakt = traktId,
                imdb = imdbId,
                tmdb = tmdbId
            )
            val body = when (meta.type) {
                ContentType.MOVIE -> TraktRatingRequestDto(
                    movies = listOf(TraktRatedMovieDto(rating = rating, ids = ids))
                )
                else -> TraktRatingRequestDto(
                    shows = listOf(TraktRatedShowDto(rating = rating, ids = ids))
                )
            }
            traktApi.addOrUpdateRatings(authHeader, body)
        }?.let { response ->
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(IllegalStateException("Trakt rating failed: ${response.code()}"))
        } ?: Result.failure(IllegalStateException("Trakt not authenticated"))
    }
}
