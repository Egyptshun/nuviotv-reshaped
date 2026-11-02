package com.nuvio.tv.data.remote.dto.trakt

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TraktRatingRequestDto(
    val movies: List<TraktRatedMovieDto>? = null,
    val shows: List<TraktRatedShowDto>? = null
)

@JsonClass(generateAdapter = true)
data class TraktRatedMovieDto(
    val rating: Int,
    val ids: TraktIdsDto
)

@JsonClass(generateAdapter = true)
data class TraktRatedShowDto(
    val rating: Int,
    val ids: TraktIdsDto
)

@JsonClass(generateAdapter = true)
data class TraktRatingsResponseDto(
    @Json(name = "added") val added: TraktRatingsMutationDto? = null,
    @Json(name = "updated") val updated: TraktRatingsMutationDto? = null,
    @Json(name = "deleted") val deleted: TraktRatingsMutationDto? = null,
    @Json(name = "not_found") val notFound: TraktRatingsMutationDto? = null
)

@JsonClass(generateAdapter = true)
data class TraktRatingsMutationDto(
    val movies: Int? = null,
    val shows: Int? = null
)
