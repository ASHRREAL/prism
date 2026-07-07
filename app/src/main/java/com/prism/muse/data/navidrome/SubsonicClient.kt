package com.prism.muse.data.navidrome

import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.data.model.Genre
import com.prism.muse.data.model.LyricLine
import com.prism.muse.data.model.Lyrics
import com.prism.muse.data.model.Playlist
import com.prism.muse.data.model.Song
import com.prism.muse.data.prefs.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SubsonicException(message: String) : Exception(message)

/**
 * Minimal Subsonic/OpenSubsonic client speaking Navidrome's JSON dialect.
 * Auth is the standard salted-token scheme (t=md5(password+salt)). Kept
 * dependency-light on purpose: OkHttp + org.json, no codegen.
 */
class SubsonicClient(private val configProvider: () -> ServerConfig) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val clientName = "aria"
    private val apiVersion = "1.16.1"

    private fun authParams(config: ServerConfig): String {
        val salt = (1..10).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5(config.password + salt)
        return "u=${config.username.urlEncode()}&t=$token&s=$salt&v=$apiVersion&c=$clientName&f=json"
    }

    private fun endpoint(config: ServerConfig, method: String, params: String = ""): String {
        val extra = if (params.isBlank()) "" else "&$params"
        return "${config.url}/rest/$method?${authParams(config)}$extra"
    }

    /** Throws [SubsonicException] on failure; returns the subsonic-response object. */
    private suspend fun call(method: String, params: String = ""): JSONObject =
        withContext(Dispatchers.IO) {
            val config = configProvider()
            if (!config.isConfigured) throw SubsonicException("No server configured")
            val url = endpoint(config, method, params).toHttpUrlOrNull()
                ?: throw SubsonicException("Bad server URL")
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw SubsonicException("HTTP ${resp.code}")
                resp.body?.string() ?: throw SubsonicException("Empty response")
            }
            val root = JSONObject(body).getJSONObject("subsonic-response")
            if (root.getString("status") != "ok") {
                val err = root.optJSONObject("error")
                throw SubsonicException(err?.optString("message") ?: "Server error")
            }
            root
        }

    suspend fun ping(): Boolean {
        call("ping")
        return true
    }

    // ---- URL builders (used directly by Coil / ExoPlayer) ----

    fun coverArtUrl(coverArt: String, size: Int = 600): String {
        val config = configProvider()
        if (!config.isConfigured || coverArt.isBlank()) return coverArt
        return endpoint(config, "getCoverArt", "id=${coverArt.urlEncode()}&size=$size")
    }

    fun streamUrl(songId: String): String {
        val config = configProvider()
        if (!config.isConfigured) return ""
        return endpoint(config, "stream", "id=${songId.urlEncode()}")
    }

    // ---- Library ----

    suspend fun getAlbumList(type: String, size: Int = 60): List<Album> {
        val root = call("getAlbumList2", "type=$type&size=$size")
        return root.optJSONObject("albumList2")?.optJSONArray("album").mapObjects(::parseAlbum)
    }

    suspend fun getArtists(): List<Artist> {
        val root = call("getArtists")
        val result = mutableListOf<Artist>()
        val indexes = root.optJSONObject("artists")?.optJSONArray("index") ?: return result
        for (i in 0 until indexes.length()) {
            indexes.getJSONObject(i).optJSONArray("artist").mapObjects(::parseArtist).forEach(result::add)
        }
        return result
    }

    suspend fun getArtistAlbums(artistId: String): List<Album> {
        val root = call("getArtist", "id=${artistId.urlEncode()}")
        return root.optJSONObject("artist")?.optJSONArray("album").mapObjects(::parseAlbum)
    }

    suspend fun getArtistInfo(artistId: String): String {
        val root = call("getArtistInfo2", "id=${artistId.urlEncode()}")
        return root.optJSONObject("artistInfo2")?.optString("biography").orEmpty()
            .replace(Regex("<[^>]*>"), "")
    }

    suspend fun getAlbumSongs(albumId: String): List<Song> {
        val root = call("getAlbum", "id=${albumId.urlEncode()}")
        return root.optJSONObject("album")?.optJSONArray("song").mapObjects(::parseSong)
    }

    suspend fun getPlaylists(): List<Playlist> {
        val root = call("getPlaylists")
        return root.optJSONObject("playlists")?.optJSONArray("playlist").mapObjects { obj ->
            Playlist(
                id = obj.getString("id"),
                name = obj.optString("name"),
                trackCount = obj.optInt("songCount"),
                coverUrls = listOf(coverArtUrl(obj.optString("coverArt")))
            )
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): List<Song> {
        val root = call("getPlaylist", "id=${playlistId.urlEncode()}")
        return root.optJSONObject("playlist")?.optJSONArray("entry").mapObjects(::parseSong)
    }

    /** Create a new playlist seeded with the given songs; returns its id. */
    suspend fun createPlaylist(name: String, songIds: List<String>): String? {
        val params = buildString {
            append("name=${name.urlEncode()}")
            songIds.forEach { append("&songId=${it.urlEncode()}") }
        }
        val root = call("createPlaylist", params)
        return root.optJSONObject("playlist")?.optString("id")?.ifBlank { null }
    }

    /** Append a song to an existing playlist. */
    suspend fun addToPlaylist(playlistId: String, songId: String) {
        call("updatePlaylist", "playlistId=${playlistId.urlEncode()}&songIdToAdd=${songId.urlEncode()}")
    }

    /** Delete a playlist. */
    suspend fun deletePlaylist(playlistId: String) {
        call("deletePlaylist", "id=${playlistId.urlEncode()}")
    }

    suspend fun getGenres(): List<Genre> {
        val root = call("getGenres")
        return root.optJSONObject("genres")?.optJSONArray("genre").mapObjects { obj ->
            Genre(
                id = obj.optString("value"),
                name = obj.optString("value"),
                albumCount = obj.optInt("albumCount")
            )
        }
    }

    suspend fun getSongsByGenre(genre: String, count: Int = 100): List<Song> {
        val root = call("getSongsByGenre", "genre=${genre.urlEncode()}&count=$count")
        return root.optJSONObject("songsByGenre")?.optJSONArray("song").mapObjects(::parseSong)
    }

    suspend fun getRandomSongs(size: Int = 30): List<Song> {
        val root = call("getRandomSongs", "size=$size")
        return root.optJSONObject("randomSongs")?.optJSONArray("song").mapObjects(::parseSong)
    }

    data class SearchResults(
        val songs: List<Song>,
        val albums: List<Album>,
        val artists: List<Artist>
    )

    suspend fun search(query: String): SearchResults {
        val root = call(
            "search3",
            "query=${query.urlEncode()}&songCount=25&albumCount=15&artistCount=10"
        )
        val result = root.optJSONObject("searchResult3")
        return SearchResults(
            songs = result?.optJSONArray("song").mapObjects(::parseSong),
            albums = result?.optJSONArray("album").mapObjects(::parseAlbum),
            artists = result?.optJSONArray("artist").mapObjects(::parseArtist)
        )
    }

    // ---- Favorites / ratings / scrobble ----

    suspend fun getStarredSongs(): List<Song> {
        val root = call("getStarred2")
        return root.optJSONObject("starred2")?.optJSONArray("song").mapObjects(::parseSong)
    }

    suspend fun star(id: String) {
        call("star", "id=${id.urlEncode()}")
    }

    suspend fun unstar(id: String) {
        call("unstar", "id=${id.urlEncode()}")
    }

    suspend fun scrobble(songId: String) {
        call("scrobble", "id=${songId.urlEncode()}&submission=true")
    }

    // ---- Lyrics ----

    /** OpenSubsonic synced lyrics with plain-text fallback. */
    suspend fun getLyrics(song: Song): Lyrics? {
        // Navidrome ≥ 0.49 supports getLyricsBySongId (OpenSubsonic extension).
        runCatching {
            val root = call("getLyricsBySongId", "id=${song.id.urlEncode()}")
            val list = root.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")
            if (list != null && list.length() > 0) {
                val structured = list.getJSONObject(0)
                val synced = structured.optBoolean("synced", false)
                val lines = structured.optJSONArray("line").mapObjects { obj ->
                    LyricLine(
                        timeMs = if (synced) obj.optLong("start", -1L) else -1L,
                        text = obj.optString("value")
                    )
                }
                if (lines.isNotEmpty()) return Lyrics(lines, synced, source = "navidrome")
            }
        }
        // Legacy getLyrics: single unsynced blob keyed by artist/title.
        runCatching {
            val root = call(
                "getLyrics",
                "artist=${song.artist.urlEncode()}&title=${song.title.urlEncode()}"
            )
            val value = root.optJSONObject("lyrics")?.optString("value").orEmpty()
            if (value.isNotBlank()) {
                val lines = value.lines().filter { it.isNotBlank() }.map { LyricLine(-1L, it.trim()) }
                return Lyrics(lines, synced = false, source = "navidrome")
            }
        }
        return null
    }

    // ---- Parsers ----

    private fun parseSong(obj: JSONObject): Song = Song(
        id = obj.getString("id"),
        title = obj.optString("title"),
        artist = obj.optString("artist"),
        albumId = obj.optString("albumId"),
        album = obj.optString("album"),
        durationSec = obj.optInt("duration"),
        trackNumber = obj.optInt("track"),
        artUrl = coverArtUrl(obj.optString("coverArt")),
        streamUrl = streamUrl(obj.getString("id")),
        isFavorite = obj.has("starred"),
        playCount = obj.optInt("playCount")
    )

    private fun parseAlbum(obj: JSONObject): Album = Album(
        id = obj.getString("id"),
        title = obj.optString("name", obj.optString("title")),
        artist = obj.optString("artist"),
        artistId = obj.optString("artistId"),
        year = obj.optInt("year"),
        artUrl = coverArtUrl(obj.optString("coverArt")),
        trackCount = obj.optInt("songCount"),
        genre = obj.optString("genre")
    )

    private fun parseArtist(obj: JSONObject): Artist = Artist(
        id = obj.getString("id"),
        name = obj.optString("name"),
        imageUrl = obj.optString("artistImageUrl").ifBlank { coverArtUrl(obj.optString("coverArt")) },
        albumCount = obj.optInt("albumCount")
    )

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
}

private inline fun <T> org.json.JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) out.add(transform(getJSONObject(i)))
    return out
}
