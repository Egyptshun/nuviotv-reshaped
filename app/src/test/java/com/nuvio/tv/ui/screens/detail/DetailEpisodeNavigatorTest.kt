package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailEpisodeNavigatorTest {

    @Test
    fun reverseSortOrdersHighestEpisodeFirst() {
        val episodes = listOf(
            video(episode = 1),
            video(episode = 3),
            video(episode = 2)
        )
        val sorted = sortEpisodesForDisplay(episodes, EpisodeListSort.REVERSE)
        assertEquals(listOf(3, 2, 1), sorted.map { it.episode })
    }

    private fun video(episode: Int) = Video(
        id = "e$episode",
        title = "Episode $episode",
        season = 1,
        episode = episode,
        released = null,
        thumbnail = null,
        overview = null
    )
}
