package com.prism.muse.data.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val album: String,
    val durationSec: Int,
    val trackNumber: Int,
    /** getCoverArt URL when connected to Navidrome; opaque seed string in demo mode. */
    val artUrl: String,
    /** stream URL when connected; empty in demo mode (silent fake playback). */
    val streamUrl: String = "",
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val playCount: Int = 0
) {
    fun serialize(): String {
        return listOf(
            id, title.urlEncode(), artist.urlEncode(), albumId, album.urlEncode(),
            durationSec.toString(), trackNumber.toString(), artUrl.urlEncode(),
            streamUrl.urlEncode()
        ).joinToString("|")
    }

    companion object {
        fun fromSerialized(data: String): Song? {
            val parts = data.split("|")
            if (parts.size < 9) return null
            return try {
                Song(
                    id = parts[0],
                    title = parts[1].urlDecode(),
                    artist = parts[2].urlDecode(),
                    albumId = parts[3],
                    album = parts[4].urlDecode(),
                    durationSec = parts[5].toInt(),
                    trackNumber = parts[6].toInt(),
                    artUrl = parts[7].urlDecode(),
                    streamUrl = parts[8].urlDecode()
                )
            } catch (_: Exception) { null }
        }
    }
}

private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
private fun String.urlDecode(): String = java.net.URLDecoder.decode(this, "UTF-8")

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val year: Int,
    val artUrl: String,
    val trackCount: Int,
    val genre: String,
    val dominantColorHex: String = "#8FD1E8"
)

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String,
    val albumCount: Int,
    val bio: String = ""
)

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val coverUrls: List<String>,
    val pinned: Boolean = false
)

data class Genre(
    val id: String,
    val name: String,
    val albumCount: Int
)

/** One line of synchronized lyrics; [timeMs] < 0 means unsynced. */
data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String = ""
)
