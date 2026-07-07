package com.prism.muse.data

import android.content.Context
import com.prism.muse.data.mock.MockLibrary
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.data.model.Genre
import com.prism.muse.data.model.Playlist
import com.prism.muse.data.model.Song
import com.prism.muse.data.navidrome.SubsonicClient
import com.prism.muse.data.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Single source of truth for the library. When a Navidrome server is
 * configured everything comes from Subsonic calls (cached in memory); with no
 * server it serves the bundled demo library so the UI stays explorable.
 * Downloads are plain files under filesDir/music, keyed by song id.
 */
class LibraryRepository(
    private val context: Context,
    private val api: SubsonicClient,
    private val prefs: AppPrefs,
    private val scope: CoroutineScope
) {

    val connected = MutableStateFlow(false)
    val syncing = MutableStateFlow(false)
    val lastError = MutableStateFlow<String?>(null)

    private val _recentSongs = MutableStateFlow(emptyList<Song>())
    val recentSongs: StateFlow<List<Song>> = _recentSongs

    private val _albums = MutableStateFlow(emptyList<Album>())
    val albums: StateFlow<List<Album>> = _albums

    private val _artists = MutableStateFlow(emptyList<Artist>())
    val artists: StateFlow<List<Artist>> = _artists

    private val _playlists = MutableStateFlow(emptyList<Playlist>())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _favorites = MutableStateFlow(emptyList<Song>())
    val favorites: StateFlow<List<Song>> = _favorites

    private val _genres = MutableStateFlow(emptyList<Genre>())
    val genres: StateFlow<List<Genre>> = _genres

    private val _recommended = MutableStateFlow(emptyList<Song>())
    val recommended: StateFlow<List<Song>> = _recommended

    private val _downloaded = MutableStateFlow<List<Song>>(emptyList())
    val downloaded: StateFlow<List<Song>> = _downloaded

    private val albumSongs = HashMap<String, List<Song>>()
    private val playlistSongs = HashMap<String, List<Song>>()

    private val downloadDir get() = File(context.filesDir, "music").apply { mkdirs() }

    init {
        scope.launch { if (prefs.server.value.isConfigured) refresh() }
    }

    /** Full background sync of every hub section; safe to call repeatedly. */
    suspend fun refresh() {
        if (!prefs.server.value.isConfigured || prefs.offlineMode.value) return
        // Pull-to-refresh, login and "sync now" can all fire this; don't stack
        // concurrent full syncs on top of each other.
        if (syncing.value) return
        syncing.value = true
        lastError.value = null
        try {
            coroutineScope {
                val recent = async { api.getAlbumList("recent", 20) }
                val albums = async { api.getAlbumList("newest", 100) }
                val artists = async { api.getArtists() }
                val playlists = async { api.getPlaylists() }
                val starred = async { api.getStarredSongs() }
                val genres = async { api.getGenres() }
                val random = async { api.getRandomSongs(12) }

                _albums.value = albums.await()
                _artists.value = artists.await()
                _playlists.value = playlists.await()
                _favorites.value = starred.await()
                _genres.value = genres.await()

                val randSongs = random.await()

                // "recently played": songs of the most recently played albums,
                // topped up with random picks so the panel is never empty.
                val recentAlbums = recent.await().take(4)
                val recentSongsRes = mutableListOf<Song>()
                recentAlbums.forEach { album ->
                    runCatching { recentSongsRes += songsForAlbum(album.id).take(3) }
                }
                if (recentSongsRes.isEmpty()) recentSongsRes += randSongs
                _recentSongs.value = recentSongsRes.distinctBy { it.id }

                // Recommended: mix of recent and random
                _recommended.value = (recentSongsRes + randSongs).distinctBy { it.id }.shuffled().take(20)

                connected.value = true
            }
        } catch (e: Exception) {
            lastError.value = e.message
            connected.value = false
        } finally {
            refreshDownloads()
            syncing.value = false
        }
    }

    suspend fun songsForAlbum(albumId: String): List<Song> {
        albumSongs[albumId]?.let { return it }
        if (!connectedToServer()) return MockLibrary.songsForAlbum(albumId)
        return api.getAlbumSongs(albumId).also { albumSongs[albumId] = it }
    }

    suspend fun songsForPlaylist(playlistId: String): List<Song> {
        playlistSongs[playlistId]?.let { return it }
        if (!connectedToServer()) return MockLibrary.songs.take(10)
        return api.getPlaylistSongs(playlistId).also { playlistSongs[playlistId] = it }
    }

    /**
     * Every song in the library. Subsonic has no "all songs" endpoint, so we
     * walk every album (cached) and collect their tracks — this is why the old
     * ad-hoc version (recent + favorites + first 20 albums) only ever showed a
     * partial list.
     */
    suspend fun allSongs(): List<Song> {
        if (!connectedToServer()) return emptyList()
        val out = LinkedHashMap<String, Song>()
        _albums.value.forEach { album ->
            runCatching { songsForAlbum(album.id) }.getOrDefault(emptyList())
                .forEach { out[it.id] = it }
        }
        (_favorites.value + _recentSongs.value).forEach { out.putIfAbsent(it.id, it) }
        return out.values.sortedBy { it.title.lowercase() }
    }

    /** Create a playlist from songs (e.g. save the current queue). Refreshes the list. */
    suspend fun createPlaylist(name: String, songs: List<Song>): Boolean {
        if (!connectedToServer()) return false
        return runCatching {
            api.createPlaylist(name.ifBlank { "New playlist" }, songs.map { it.id })
            _playlists.value = api.getPlaylists()
        }.isSuccess
    }

    /** Add a single song to an existing playlist. */
    suspend fun addToPlaylist(playlistId: String, song: Song): Boolean {
        if (!connectedToServer()) return false
        return runCatching {
            api.addToPlaylist(playlistId, song.id)
            playlistSongs.remove(playlistId)
            _playlists.value = api.getPlaylists()
        }.isSuccess
    }

    suspend fun deletePlaylist(playlistId: String): Boolean {
        if (!connectedToServer()) return false
        return runCatching {
            api.deletePlaylist(playlistId)
            _playlists.value = api.getPlaylists()
        }.isSuccess
    }

    suspend fun albumsForArtist(artist: Artist): List<Album> =
        if (!connectedToServer()) MockLibrary.albumsForArtist(artist.name)
        else api.getArtistAlbums(artist.id)

    suspend fun artistBio(artist: Artist): String =
        if (!connectedToServer()) artist.bio
        else runCatching { api.getArtistInfo(artist.id) }.getOrDefault("")

    suspend fun songsForGenre(genre: String): List<Song> =
        if (!connectedToServer()) MockLibrary.songs.take(12)
        else api.getSongsByGenre(genre)

    suspend fun search(query: String): SubsonicClient.SearchResults {
        if (!connectedToServer()) {
            val q = query.lowercase()
            return SubsonicClient.SearchResults(
                songs = MockLibrary.songs.filter { it.title.lowercase().contains(q) }.take(20),
                albums = MockLibrary.albums.filter { it.title.lowercase().contains(q) },
                artists = MockLibrary.artists.filter { it.name.lowercase().contains(q) }
            )
        }
        return api.search(query)
    }

    fun toggleFavorite(song: Song) {
        val nowFavorite = !_favorites.value.any { it.id == song.id }
        if (nowFavorite) {
            _favorites.value = _favorites.value + song.copy(isFavorite = true)
        } else {
            _favorites.value = _favorites.value.filterNot { it.id == song.id }
        }
        if (connectedToServer()) scope.launch {
            runCatching { if (nowFavorite) api.star(song.id) else api.unstar(song.id) }
        }
    }

    fun isFavorite(songId: String): Boolean = _favorites.value.any { it.id == songId }

    fun scrobble(song: Song) {
        if (connectedToServer()) scope.launch { runCatching { api.scrobble(song.id) } }
    }

    // ---- Downloads ----

    fun localFile(songId: String): File = File(downloadDir, "$songId.audio")

    fun isDownloaded(songId: String): Boolean = localFile(songId).exists()

    /** Resolve the playable URI: local file when downloaded, stream otherwise. */
    fun playableUri(song: Song): String {
        val file = localFile(song.id)
        return if (file.exists()) file.toURI().toString() else song.streamUrl
    }

    suspend fun download(song: Song): Boolean = withContext(Dispatchers.IO) {
        if (song.streamUrl.isBlank()) return@withContext false
        runCatching {
            val tmp = File(downloadDir, "${song.id}.part")
            URL(song.streamUrl).openStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(localFile(song.id))
        }.isSuccess.also { if (it) refreshDownloads(song) }
    }

    fun deleteDownload(songId: String) {
        localFile(songId).delete()
        _downloaded.value = _downloaded.value.filterNot { it.id == songId }
    }

    fun downloadsSizeBytes(): Long =
        downloadDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clearDownloads() {
        downloadDir.listFiles()?.forEach { it.delete() }
        _downloaded.value = emptyList()
    }

    private fun refreshDownloads(newSong: Song? = null) {
        val known = (albumSongs.values.flatten() + playlistSongs.values.flatten() +
            _favorites.value + _recentSongs.value + listOfNotNull(newSong))
            .distinctBy { it.id }
        _downloaded.value = known.filter { isDownloaded(it.id) }.map { it.copy(isDownloaded = true) }
    }

    private fun connectedToServer(): Boolean =
        prefs.server.value.isConfigured && !prefs.offlineMode.value
}
