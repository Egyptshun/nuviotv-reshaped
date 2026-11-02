package com.nuvio.tv.reshaped.pinnedstreams

import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedStreamSourceOrderingTest {
    @Test
    fun `pinned source names move to the front`() {
        val names = listOf("B", "A", "C")
        val keys = setOf(PinnedStreamSourceKey.encode("addon-a", "A"))
        val sorted = applyPinnedSourceNameOrder(names, keys) { name ->
            when (name) {
                "A" -> PinnedStreamSourceKey.encode("addon-a", "A")
                else -> PinnedStreamSourceKey.encode("source", name)
            }
        }
        assertEquals(listOf("A", "B", "C"), sorted)
    }

    @Test
    fun `pinned addon groups move to the front`() {
        val groups = listOf(
            group("Beta"),
            group("Alpha"),
        )
        val keys = setOf(PinnedStreamSourceKey.encode("id-alpha", "Alpha"))
        val sorted = applyPinnedAddonStreamOrder(groups, keys) { name ->
            if (name == "Alpha") PinnedStreamSourceKey.encode("id-alpha", "Alpha")
            else PinnedStreamSourceKey.encode("source", name)
        }
        assertEquals(listOf("Alpha", "Beta"), sorted.map { it.addonName })
    }

    private fun group(name: String) = AddonStreams(
        addonName = name,
        addonLogo = null,
        streams = listOf(
            Stream(
                name = "stream",
                title = null,
                description = null,
                url = "https://example.com",
                ytId = null,
                infoHash = null,
                fileIdx = null,
                externalUrl = null,
                behaviorHints = null,
                addonName = name,
                addonLogo = null,
            )
        )
    )
}
