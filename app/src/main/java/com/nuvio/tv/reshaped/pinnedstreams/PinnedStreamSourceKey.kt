package com.nuvio.tv.reshaped.pinnedstreams

internal object PinnedStreamSourceKey {
    private const val SEPARATOR = '|'

    fun encode(addonId: String, sourceName: String): String {
        val id = addonId.trim()
        val name = sourceName.trim()
        require(id.isNotEmpty() && name.isNotEmpty())
        return "$id$SEPARATOR$name"
    }

    fun decode(key: String): Pair<String, String>? {
        val separator = key.indexOf(SEPARATOR)
        if (separator <= 0 || separator >= key.lastIndex) return null
        val addonId = key.substring(0, separator).trim()
        val sourceName = key.substring(separator + 1).trim()
        if (addonId.isEmpty() || sourceName.isEmpty()) return null
        return addonId to sourceName
    }

    fun sourceName(key: String): String? = decode(key)?.second
}
