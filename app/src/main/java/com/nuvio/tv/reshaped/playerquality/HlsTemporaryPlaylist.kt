package com.nuvio.tv.reshaped.playerquality

import java.io.File

internal object HlsTemporaryPlaylist {
    fun write(playlistText: String): String? = runCatching {
        val file = File.createTempFile("nuvio_player_quality_", ".m3u8")
        file.writeText(playlistText)
        file.deleteOnExit()
        "file://${file.absolutePath}"
    }.getOrNull()
}
