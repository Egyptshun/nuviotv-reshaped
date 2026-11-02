package com.nuvio.tv.reshaped.homesnapshot

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSnapshotSerializationTest {

    @Test
    fun metaPreviewRoundTripPreservesDisplayFields() {
        val original = MetaPreview(
            id = "tt123",
            type = ContentType.MOVIE,
            rawType = "movie",
            name = "Example",
            poster = "https://example/poster.jpg",
            posterShape = PosterShape.POSTER,
            background = "https://example/backdrop.jpg",
            logo = "https://example/logo.png",
            description = "Synopsis",
            releaseInfo = "2020",
            imdbRating = 7.5f,
            genres = listOf("Drama"),
            landscapePoster = "https://example/landscape.jpg",
            rawPosterUrl = "https://example/raw.jpg",
        )
        val dto = original.toDtoForTest()
        val restored = dto.toMetaPreviewForTest()
        assertEquals(original.id, restored.id)
        assertEquals(original.apiType, restored.apiType)
        assertEquals(original.name, restored.name)
        assertEquals(original.poster, restored.poster)
        assertEquals(original.background, restored.background)
        assertEquals(original.landscapePoster, restored.landscapePoster)
        assertEquals(original.genres, restored.genres)
    }
}

private fun MetaPreview.toDtoForTest() = HomeSnapshotMetaDto(
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

private fun HomeSnapshotMetaDto.toMetaPreviewForTest(): MetaPreview = MetaPreview(
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
