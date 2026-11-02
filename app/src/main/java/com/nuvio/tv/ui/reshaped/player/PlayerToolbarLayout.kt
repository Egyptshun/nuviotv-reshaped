package com.nuvio.tv.ui.reshaped.player

enum class PlayerToolbarButtonId(val storageKey: String) {
    SUBTITLE("subtitle"),
    AUDIO("audio"),
    CHAPTERS("chapters"),
    SOURCES("sources"),
    EPISODES("episodes"),
    PARTY("party"),
    MORE("more");

    companion object {
        val DEFAULT_ORDER: List<PlayerToolbarButtonId> = entries.toList()
        fun fromStorageKey(key: String): PlayerToolbarButtonId? =
            entries.firstOrNull { it.storageKey == key }
    }
}
