package com.nuvio.tv.data.mdblist

import com.nuvio.tv.core.tracking.TrackingMediaKind
import com.nuvio.tv.core.tracking.TrackingMediaReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class MdbListRatingsWriteService @Inject constructor(
    private val api: MdbListApiClient,
    private val store: MdbListAuthStore
) {
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    suspend fun submitRating(
        scope: MdbListAuthScope,
        media: TrackingMediaReference,
        rating: Int
    ): Result<Unit> {
        if (!store.state.value.isAuthenticated) {
            return Result.failure(IllegalStateException("MDBList not authenticated"))
        }
        val ids = media.ids.toMdbListIds()
            ?: return Result.failure(IllegalStateException("MDBList rating requires resolvable IDs"))
        val score = rating.coerceIn(1, 10)
        val body = when (media.kind) {
            TrackingMediaKind.MOVIE -> json.encodeToString(
                MdbListRatingsWriteRequest(
                    movies = listOf(MdbListRatedMovie(ids = ids, rating = score))
                )
            )
            else -> json.encodeToString(
                MdbListRatingsWriteRequest(
                    shows = listOf(MdbListRatedShow(ids = ids, rating = score))
                )
            )
        }
        return runCatching {
            val response = api.post("/sync/ratings", body, scope = scope)
            if (response.status in 200..299) Unit
            else throw MdbListApiException(response.status, response.errorCode())
        }
    }
}

@Serializable
private data class MdbListRatingsWriteRequest(
    val movies: List<MdbListRatedMovie> = emptyList(),
    val shows: List<MdbListRatedShow> = emptyList()
)

@Serializable
private data class MdbListRatedMovie(
    val ids: MdbListIds,
    val rating: Int
)

@Serializable
private data class MdbListRatedShow(
    val ids: MdbListIds,
    val rating: Int
)
