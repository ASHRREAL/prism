package com.prism.muse.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Song
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Thin adapter between the screens and the app-wide [PlayerHolder] engine —
 * playback state survives the ViewModel because it lives in the holder.
 */
class PlaybackViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = PrismApp.graph(app)
    private val holder = graph.player
    private val library = graph.library

    val state: StateFlow<PlaybackState> = holder.state
    val audioSessionId: Int get() = holder.audioSessionId

    fun togglePlay() = holder.togglePlay()
    fun seekTo(sec: Float) = holder.seekTo(sec)
    fun skipNext() = holder.skipNext()
    fun skipPrevious() = holder.skipPrevious()
    fun toggleShuffle() = holder.toggleShuffle()
    fun cycleRepeat() = holder.cycleRepeat()
    fun setSpeed(speed: Float) = holder.setSpeed(speed)
    fun playSong(song: Song) = holder.playSong(song)
    fun playQueue(songs: List<Song>, startIndex: Int = 0) = holder.setQueue(songs, startIndex)
    fun addNext(song: Song) = holder.addNext(song)
    fun addToQueue(song: Song) = holder.addToQueue(song)
    fun shuffleQueue() = holder.shuffleQueue()
    fun removeFromQueue(song: Song) = holder.removeFromQueue(song)

    fun toggleFavorite(song: Song) = library.toggleFavorite(song)
    fun isFavorite(song: Song): Boolean = library.isFavorite(song.id)

    /** Play a song standalone by loading its album as the queue context. */
    fun playFromAlbum(song: Song) {
        viewModelScope.launch {
            val songs = runCatching { library.songsForAlbum(song.albumId) }.getOrDefault(listOf(song))
            val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            holder.setQueue(if (songs.isEmpty()) listOf(song) else songs, index)
        }
    }
}
