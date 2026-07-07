package com.prism.muse.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.prism.muse.data.LibraryRepository
import com.prism.muse.data.model.Song
import com.prism.muse.data.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackState(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val positionSec: Float = 0f,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f
) {
    val current: Song? get() = queue.getOrNull(currentIndex)
}

enum class RepeatMode { OFF, ONE, ALL }

/** Key names for last-playback memory in SharedPreferences. */
private const val PREFS_LAST_SONG = "last_song_data"
private const val PREFS_LAST_POSITION = "last_song_position"
private const val PREFS_LAST_QUEUE = "last_song_queue"
private const val PREFS_LAST_INDEX = "last_song_index"
private const val PREFS_LAST_SPEED = "last_song_speed"

/**
 * The app-wide playback engine: a single ExoPlayer shared between the UI and
 * [PlaybackService]'s MediaSession (same process). Real audio when songs have
 * stream URLs; a fake position clock in demo mode so the prototype UI still
 * animates without a server. Gapless is ExoPlayer's default behavior; audio
 * focus + ducking are handled via audio attributes.
 */
class PlayerHolder(
    private val context: Context,
    private val prefs: AppPrefs,
    private val library: LibraryRepository
) {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefsStore = context.getSharedPreferences("aria_player", Context.MODE_PRIVATE)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    /** True when the current queue has no playable URIs (no server, demo UI). */
    private var demoMode = false

    /** The original queue order (before shuffling), stored for replay reshuffle. */
    private var originalQueue: List<Song> = emptyList()
    /** Whether the original queue was created from a shuffle-press action. */
    private var wasShuffled: Boolean = false

    val audioSessionId: Int get() = player.audioSessionId

    /**
     * Tries to restore the last playback session. Call once after construction.
     * Returns true if a session was restored and playback has begun.
     */
    fun restoreSession(): Boolean {
        val data = prefsStore.getString(PREFS_LAST_SONG, null) ?: return false
        val queueData = prefsStore.getString(PREFS_LAST_QUEUE, null) ?: return false
        return try {
            val queue = queueData.split("||").mapNotNull { Song.fromSerialized(it) }
            if (queue.isEmpty()) return false
            val index = prefsStore.getInt(PREFS_LAST_INDEX, 0).coerceIn(0, queue.lastIndex)
            val position = prefsStore.getFloat(PREFS_LAST_POSITION, 0f)
            val speed = prefsStore.getFloat(PREFS_LAST_SPEED, 1f)
            player.setPlaybackSpeed(speed)
            setQueue(queue, index, play = false)
            seekTo(position)
            _state.update { it.copy(speed = speed) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Saves the current playback state to SharedPreferences. */
    private fun saveSession() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val queueData = s.queue.joinToString("||") { it.serialize() }
        prefsStore.edit()
            .putString(PREFS_LAST_QUEUE, queueData)
            .putInt(PREFS_LAST_INDEX, s.currentIndex)
            .putFloat(PREFS_LAST_POSITION, s.positionSec)
            .putFloat(PREFS_LAST_SPEED, s.speed)
            .putString(PREFS_LAST_SONG, s.current?.serialize() ?: "")
            .apply()
    }

    init {
        player.setPlaybackSpeed(prefs.playbackSpeed.value)

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (demoMode) return
                val s = _state.value
                val index = player.currentMediaItemIndex

                // If we reached the end while shuffle+repeat-all is on, reshuffle.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    s.repeat == RepeatMode.ALL && s.shuffle && index == 0) {
                    reshuffleOnReplay()
                    return
                }

                _state.update { it.copy(currentIndex = index, positionSec = 0f) }
                _state.value.current?.let(library::scrobble)
                saveSession()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!demoMode) _state.update { it.copy(isPlaying = isPlaying) }
            }
        })

        mainScope.launch {
            while (true) {
                delay(100)
                val s = _state.value
                if (!s.isPlaying) continue
                if (demoMode) {
                    val duration = (s.current?.durationSec ?: 0).toFloat()
                    val next = s.positionSec + 0.1f
                    if (duration > 0f && next >= duration) demoAdvance()
                    else _state.update { it.copy(positionSec = next) }
                } else {
                    _state.update { it.copy(positionSec = player.currentPosition / 1000f) }
                    // Periodically save the position
                    val pos = player.currentPosition / 1000f
                    if (pos > 0f && (pos.toLong() % 5 == 0L)) saveSession()
                }
            }
        }
    }

    /** Reshuffles the queue when repeat-all re-loops with shuffle on. */
    private fun reshuffleOnReplay() {
        val s = _state.value
        if (s.queue.size <= 1) return
        val current = s.current ?: return
        val rest = s.queue.filterNot { it.id == current.id }.shuffled()
        val newQueue = listOf(current) + rest
        _state.update { it.copy(queue = newQueue, currentIndex = 0, positionSec = 0f) }
        if (!demoMode) {
            player.setMediaItems(newQueue.map(::toMediaItem), 0, 0L)
            player.playWhenReady = s.isPlaying
        }
        saveSession()
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0, play: Boolean = true) {
        if (songs.isEmpty()) return
        demoMode = songs.all { library.playableUri(it).isBlank() }
        originalQueue = songs.toList()
        wasShuffled = false
        _state.update {
            it.copy(queue = songs, currentIndex = startIndex, positionSec = 0f, isPlaying = play)
        }
        if (demoMode) {
            player.stop()
            saveSession()
            return
        }
        player.setMediaItems(songs.map(::toMediaItem), startIndex, 0L)
        player.prepare()
        player.playWhenReady = play
        if (play) startService()
        saveSession()
    }

    fun playSong(song: Song) {
        val index = _state.value.queue.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            _state.update { it.copy(currentIndex = index, positionSec = 0f, isPlaying = true) }
            if (!demoMode) {
                player.seekTo(index, 0L)
                player.playWhenReady = true
                startService()
            }
            saveSession()
        } else {
            setQueue(listOf(song))
        }
    }

    fun togglePlay() {
        val playing = !_state.value.isPlaying
        _state.update { it.copy(isPlaying = playing) }
        if (!demoMode) {
            player.playWhenReady = playing
            if (playing) startService()
        }
        if (!playing) saveSession()
    }

    fun seekTo(sec: Float) {
        _state.update { it.copy(positionSec = sec) }
        if (!demoMode) player.seekTo((sec * 1000).toLong())
    }

    fun skipNext() {
        if (demoMode) demoAdvance() else player.seekToNextMediaItem()
    }

    fun skipPrevious() {
        if (demoMode) {
            _state.update {
                it.copy(currentIndex = (it.currentIndex - 1 + it.queue.size) % it.queue.size, positionSec = 0f)
            }
        } else player.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val shuffle = !_state.value.shuffle
        _state.update { it.copy(shuffle = shuffle) }
        player.shuffleModeEnabled = shuffle
        wasShuffled = true
        if (shuffle) {
            // Shuffle upcoming tracks without restarting current song
            val s = _state.value
            if (s.queue.size > 1) {
                val current = s.current ?: return
                val rest = s.queue.filterIndexed { i, _ -> i != s.currentIndex }.shuffled()
                val newQueue = listOf(current) + rest
                _state.update { it.copy(queue = newQueue, currentIndex = 0) }
                if (!demoMode) {
                    player.setMediaItems(newQueue.map(::toMediaItem), 0, (s.positionSec * 1000).toLong())
                    player.playWhenReady = s.isPlaying
                }
            }
        } else if (originalQueue.isNotEmpty()) {
            val s = _state.value
            val current = s.current ?: return
            val origIndex = originalQueue.indexOfFirst { it.id == current.id }
            _state.update { it.copy(queue = originalQueue.toList(), currentIndex = origIndex.coerceAtLeast(0)) }
            if (!demoMode) {
                player.setMediaItems(originalQueue.map(::toMediaItem), origIndex.coerceAtLeast(0), (s.positionSec * 1000).toLong())
                player.playWhenReady = s.isPlaying
            }
        }
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.update { it.copy(repeat = next) }
        player.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        player.setPlaybackSpeed(speed)
        prefs.setPlaybackSpeed(speed)
    }

    fun addNext(song: Song) {
        val s = _state.value
        if (s.queue.isEmpty()) { setQueue(listOf(song)); return }
        val list = s.queue.toMutableList().apply { add(s.currentIndex + 1, song) }
        _state.update { it.copy(queue = list) }
        originalQueue = list.toList()
        if (!demoMode) player.addMediaItem(player.currentMediaItemIndex + 1, toMediaItem(song))
    }

    fun addToQueue(song: Song) {
        val s = _state.value
        if (s.queue.isEmpty()) { setQueue(listOf(song)); return }
        val list = s.queue.toMutableList().apply { add(song) }
        _state.update { it.copy(queue = list) }
        originalQueue = list.toList()
        if (!demoMode) player.addMediaItem(toMediaItem(song))
    }

    /** Shuffle the upcoming tracks, keeping the current song playing. */
    fun shuffleQueue() {
        val s = _state.value
        if (s.queue.size <= 2) return
        val curIdx = s.currentIndex
        val current = s.current ?: return
        val rest = s.queue.filterIndexed { i, _ -> i != curIdx }.shuffled()
        val newList = rest.toMutableList().apply { add(curIdx.coerceIn(0, size), current) }
        val newCurIdx = newList.indexOfFirst { it.id == current.id }
        _state.update { it.copy(queue = newList, currentIndex = newCurIdx) }
        if (!demoMode) {
            player.setMediaItems(newList.map(::toMediaItem), newCurIdx, (s.positionSec * 1000).toLong())
            // Don't call prepare() — it interrupts playback
            player.playWhenReady = s.isPlaying
        }
    }

    fun removeFromQueue(song: Song) {
        val s = _state.value
        if (s.queue.size <= 1) return
        val index = s.queue.indexOfFirst { it.id == song.id }
        if (index < 0) return
        val list = s.queue.toMutableList().apply { removeAt(index) }
        val newCurrent = when {
            index < s.currentIndex -> s.currentIndex - 1
            else -> s.currentIndex.coerceAtMost(list.lastIndex)
        }
        _state.update { it.copy(queue = list, currentIndex = newCurrent) }
        originalQueue = list.toList()
        if (!demoMode) player.removeMediaItem(index)
        saveSession()
    }

    private fun demoAdvance() {
        _state.update {
            it.copy(currentIndex = (it.currentIndex + 1) % it.queue.size.coerceAtLeast(1), positionSec = 0f)
        }
        _state.value.current?.let(library::scrobble)
    }

    private fun toMediaItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(library.playableUri(song))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(
                        if (song.artUrl.startsWith("http")) android.net.Uri.parse(song.artUrl) else null
                    )
                    .build()
            )
            .build()

    private fun startService() {
        runCatching {
            val intent = Intent(context, PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
