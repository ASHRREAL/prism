package com.prism.muse.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.muse.data.mock.MockLibrary
import com.prism.muse.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackState(
    val queue: List<Song> = MockLibrary.songs.take(12),
    val currentIndex: Int = 0,
    val positionSec: Float = 46f,
    val isPlaying: Boolean = true,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f
) {
    val current: Song get() = queue.getOrElse(currentIndex) { queue.first() }
}

enum class RepeatMode { OFF, ONE, ALL }

/**
 * Stands in for the real ExoPlayer/Media3-backed session during the visual
 * prototype: advances a fake position clock so the Now Playing seekbar,
 * mini player, and queue all have something believable to animate against.
 */
class PlaybackViewModel : ViewModel() {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { s ->
                    if (!s.isPlaying) return@update s
                    val duration = s.current.durationSec.toFloat()
                    val next = s.positionSec + 1f
                    if (next >= duration) s.copy(positionSec = 0f, currentIndex = (s.currentIndex + 1) % s.queue.size)
                    else s.copy(positionSec = next)
                }
            }
        }
    }

    fun togglePlay() = _state.update { it.copy(isPlaying = !it.isPlaying) }
    fun seekTo(sec: Float) = _state.update { it.copy(positionSec = sec) }
    fun skipNext() = _state.update { it.copy(positionSec = 0f, currentIndex = (it.currentIndex + 1) % it.queue.size) }
    fun skipPrevious() = _state.update { it.copy(positionSec = 0f, currentIndex = (it.currentIndex - 1 + it.queue.size) % it.queue.size) }
    fun toggleShuffle() = _state.update { it.copy(shuffle = !it.shuffle) }
    fun cycleRepeat() = _state.update {
        it.copy(repeat = when (it.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        })
    }
    fun setSpeed(speed: Float) = _state.update { it.copy(speed = speed) }
    fun playSong(song: Song) {
        val index = _state.value.queue.indexOf(song)
        if (index >= 0) _state.update { it.copy(currentIndex = index, positionSec = 0f, isPlaying = true) }
    }
    fun reorderQueue(from: Int, to: Int) = _state.update { s ->
        val list = s.queue.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        val playingSong = s.current
        s.copy(queue = list, currentIndex = list.indexOf(playingSong).coerceAtLeast(0))
    }
    fun removeFromQueue(song: Song) = _state.update { s ->
        if (s.queue.size <= 1) return@update s
        val playingSong = s.current
        val list = s.queue.toMutableList().also { it.remove(song) }
        s.copy(queue = list, currentIndex = list.indexOf(playingSong).coerceAtLeast(0))
    }
}
