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

enum class RepeatMode { OFF, ALL, ONCE }

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

    /**
     * A fixed audio session id, generated once and pinned on the player, so the
     * DSP chain binds to a stable session for the whole process. Without this
     * ExoPlayer's session id could change between tracks and the EQ would drop
     * its effects (and its edits) mid-listen.
     */
    private val stableSessionId: Int = runCatching {
        (context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
            .generateAudioSessionId()
    }.getOrDefault(0)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        // Hold CPU + WiFi awake while streaming so playback survives screen-off.
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { p ->
            if (stableSessionId != 0) runCatching { p.audioSessionId = stableSessionId }
        }

    /** App-wide EQ / bass / virtualizer / loudness, bound to the player. */
    val audioEffects = AudioEffects(prefs)

    val audioSessionId: Int get() =
        stableSessionId.takeIf { it != 0 } ?: runCatching { player.audioSessionId }.getOrDefault(0)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    /** True when the current queue has no playable URIs (no server, demo UI). */
    private var demoMode = false

    /** The original queue order (before shuffling), stored for replay reshuffle. */
    private var originalQueue: List<Song> = emptyList()
    /** Whether the original queue was created from a shuffle-press action. */
    private var wasShuffled: Boolean = false

    /**
     * Tries to restore the last playback session. Call once after construction.
     * Returns true if a session was restored and playback has begun.
     */
    fun restoreSession(): Boolean {
        // The activity re-runs this on every recreation (rotation, theme change);
        // never clobber a session that is already loaded or playing.
        if (_state.value.queue.isNotEmpty()) return false
        val data = prefsStore.getString(PREFS_LAST_SONG, null) ?: return false
        val queueData = prefsStore.getString(PREFS_LAST_QUEUE, null) ?: return false
        return try {
            val queue = queueData.split("\n").mapNotNull { Song.fromSerialized(it) }
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
        lastSaveMs = System.currentTimeMillis()
        // Fields inside a serialized song are URL-encoded, so a newline is a
        // safe song separator ("||" collided with empty trailing fields).
        val queueData = s.queue.joinToString("\n") { it.serialize() }
        prefsStore.edit()
            .putString(PREFS_LAST_QUEUE, queueData)
            .putInt(PREFS_LAST_INDEX, s.currentIndex)
            .putFloat(PREFS_LAST_POSITION, s.positionSec)
            .putFloat(PREFS_LAST_SPEED, s.speed)
            .putString(PREFS_LAST_SONG, s.current?.serialize() ?: "")
            .apply()
    }

    /** Last time the session was persisted; throttles the position saves. */
    private var lastSaveMs = 0L

    /** Pending auto-resume after the deliberate inter-track gap (gapless off). */
    private var gapResumeJob: kotlinx.coroutines.Job? = null

    /** Sleep timer: minutes remaining until playback pauses (0 = off). */
    private var sleepJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerMin = MutableStateFlow(0)
    val sleepTimerMin: StateFlow<Int> = _sleepTimerMin

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _sleepTimerMin.value = minutes.coerceAtLeast(0)
        if (minutes <= 0) return
        sleepJob = mainScope.launch {
            delay(minutes * 60_000L)
            _sleepTimerMin.value = 0
            if (_state.value.isPlaying) togglePlay()
        }
    }

    init {
        player.setPlaybackSpeed(prefs.playbackSpeed.value)

        // Gapless off = pause at each item boundary, then resume after a short
        // silence. ExoPlayer itself is gapless by default (the "on" case).
        mainScope.launch {
            prefs.gapless.collect { gapless ->
                runCatching { player.pauseAtEndOfMediaItems = !gapless }
            }
        }

        // Bind the DSP chain up front (session id is stable) and keep the EQ
        // enable state in sync with the setting from anywhere in the app.
        audioEffects.attach(audioSessionId)
        mainScope.launch {
            prefs.eqEnabled.collect { audioEffects.setEnabled(it) }
        }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (demoMode) return
                val s = _state.value
                val index = player.currentMediaItemIndex

                _state.update { it.copy(currentIndex = index, positionSec = 0f) }
                _state.value.current?.let(library::scrobble)
                saveSession()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (demoMode) return
                // STATE_ENDED fires when REPEAT_MODE_OFF lets the last queued item end
                // naturally. We engineer exactly this for two cases:
                //  - repeat OFF / ONCE: stop playback (don't loop)
                //  - repeat-ALL + shuffle: we run ExoPlayer in REPEAT_MODE_OFF for the
                //    shuffle lap (see [cycleRepeat]) so we get a deterministic end-of-
                //    playlist signal here. Build a fresh shuffled queue starting at a
                //    random head song, restart playback from slot 0. No more counting
                //    AUTO transitions or fighting ExoPlayer's opaque shuffle order —
                //    the new lap starts visibly at queue[0].
                if (playbackState == Player.STATE_ENDED) {
                    val s = _state.value
                    if (s.repeat == RepeatMode.ALL && s.shuffle && s.queue.size > 1) {
                        reshuffleOnReplay()
                        return
                    }
                    _state.update { it.copy(isPlaying = false) }
                    saveSession()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!demoMode) _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // The gapless-off pause: hold a beat of silence, then continue.
                if (!playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
                    gapResumeJob?.cancel()
                    gapResumeJob = mainScope.launch {
                        delay(400)
                        player.play()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // A dead stream (bad URL, server down mid-queue) must not end the
                // session silently: skip to the next track when there is one,
                // otherwise stop cleanly and reflect that in the UI state.
                if (demoMode) return
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else {
                    _state.update { it.copy(isPlaying = false) }
                    saveSession()
                }
            }
        })

        mainScope.launch {
            while (true) {
                delay(100)
                val s = _state.value
                if (!s.isPlaying || s.queue.isEmpty()) continue
                if (demoMode) {
                    val duration = (s.current?.durationSec ?: 0).toFloat()
                    val next = s.positionSec + 0.1f
                    if (duration > 0f && next >= duration) demoAdvance()
                    else _state.update { it.copy(positionSec = next) }
                } else {
                    _state.update { it.copy(positionSec = player.currentPosition / 1000f) }
                    applyVolumeAutomation()
                    // Retry binding the DSP chain until the session is live.
                    if (!audioEffects.available) audioEffects.attach(audioSessionId)
                    // Persist position at most once every 5 seconds.
                    if (System.currentTimeMillis() - lastSaveMs > 5000L) saveSession()
                }
            }
        }
    }

    /**
     * Ticks at 10Hz while playing: crossfade (a volume ramp over the last and
     * first N seconds of each track — a real dual-player overlap isn't possible
     * with a single ExoPlayer) and ReplayGain track-gain attenuation.
     */
    private fun applyVolumeAutomation() {
        val song = _state.value.current ?: return
        var vol = 1f
        val cfSec = prefs.crossfadeSec.value
        if (cfSec > 0) {
            val durMs = player.duration
            if (durMs > 0) {
                val posMs = player.currentPosition
                val cfMs = cfSec * 1000f
                val fadeOut = ((durMs - posMs) / cfMs).coerceIn(0f, 1f)
                val fadeIn = ((posMs / cfMs) + 0.15f).coerceIn(0f, 1f)
                vol = minOf(fadeOut, fadeIn)
            }
        }
        if (prefs.replayGain.value && song.trackGainDb != 0f) {
            // Positive gains are clamped: volume can only attenuate.
            vol *= Math.pow(10.0, song.trackGainDb / 20.0).toFloat().coerceIn(0f, 1f)
        }
        runCatching { player.volume = vol.coerceIn(0f, 1f) }
    }

    /**
     * Re-shuffles the queue when repeat-all re-loops with shuffle on. Picks a
     * random song, places it at the head of a fresh queue, then shuffles every
     * other song into the trailing slots — and starts playback from that head.
     * ExoPlayer's own shuffle order is opaque (we can't ask it to start from a
     * specific song on the next lap) so rebuilding media items here is the only
     * way to control which song plays first next loop. This is safe because the
     * previous item just ended naturally — there's no in-progress playback to
     * interrupt, only the next lap to set up.
     */
    private fun reshuffleOnReplay() {
        val s = _state.value
        if (s.queue.size <= 1) {
            _state.update { it.copy(shuffle = true) }
            return
        }
        val first = s.queue.random()
        // Drop exactly one instance of `first` (the queue can hold duplicate songs
        // — `add to queue` twice is legal — so removing by id would nuke both).
        val rest = s.queue.toMutableList().apply { removeAt(s.queue.indexOf(first)) }.shuffled()
        val newQueue = listOf(first) + rest
        _state.update {
            it.copy(queue = newQueue, currentIndex = 0, positionSec = 0f, shuffle = true)
        }
        if (!demoMode) {
            player.setMediaItems(newQueue.map(::toMediaItem), 0, 0L)
            // STATE_ENDED must be followed by prepare() + play() to start the next
            // lap — setMediaItems alone leaves the player idle at slot 0.
            player.shuffleModeEnabled = false
            player.prepare()
            player.playWhenReady = true
            startService()
        } else {
            _state.update { it.copy(isPlaying = true) }
        }
        saveSession()
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0, play: Boolean = true) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(0, songs.lastIndex)
        demoMode = songs.all { library.playableUri(it).isBlank() }
        originalQueue = songs.toList()
        wasShuffled = false
        _state.update {
            it.copy(queue = songs, currentIndex = index, positionSec = 0f, isPlaying = play)
        }
        if (demoMode) {
            runCatching { player.stop() }
            saveSession()
            return
        }
        player.setMediaItems(songs.map(::toMediaItem), index, 0L)
        player.prepare()
        player.playWhenReady = play
        if (play) startService()
        saveSession()
    }

    fun playSong(song: Song) {
        val index = _state.value.queue.indexOfFirst { it.id == song.id }
        if (index >= 0) playAt(index) else setQueue(listOf(song))
    }

    /** Jump to a queue position (safe with duplicate songs in the queue). */
    fun playAt(index: Int) {
        val s = _state.value
        if (index !in s.queue.indices) return
        _state.update {
            it.copy(currentIndex = index, positionSec = 0f, isPlaying = true)
        }
        if (!demoMode) {
            player.seekTo(index, 0L)
            player.playWhenReady = true
            startService()
        }
        saveSession()
    }

    fun togglePlay() {
        val playing = !_state.value.isPlaying
        if (!playing) gapResumeJob?.cancel()
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
            val s = _state.value
            if (s.queue.isEmpty()) return
            _state.update {
                it.copy(currentIndex = (s.currentIndex - 1 + s.queue.size) % s.queue.size,
                    positionSec = 0f)
            }
        } else player.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val shuffle = !_state.value.shuffle
        _state.update { it.copy(shuffle = shuffle) }
        player.shuffleModeEnabled = shuffle
        wasShuffled = shuffle
        applyRepeatMode()
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONCE
            RepeatMode.ONCE -> RepeatMode.OFF
        }
        _state.update { it.copy(repeat = next) }
        applyRepeatMode()
    }

    /**
     * Maps the app's repeat mode to ExoPlayer's repeat mode. Repeat-ALL-with-shuffle
     * runs as REPEAT_MODE_OFF deliberately — we drive the lap restart ourselves from
     * the STATE_ENDED callback (see [onPlaybackStateChanged]) so we can pick a
     * random head song, place it at queue slot 0, and restart visibly from there.
     * Without this ExoPlayer's opaque internal shuffle order wraps on its own and
     * the new lap would start wherever it landed (the "starts from the 10th one"
     * bug). Everything else maps directly to ExoPlayer's repeat semantics.
     */
    private fun applyRepeatMode() {
        val s = _state.value
        player.repeatMode = when {
            s.repeat == RepeatMode.ALL && s.shuffle -> Player.REPEAT_MODE_OFF
            s.repeat == RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
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
        val ins = s.currentIndex + 1
        val list = s.queue.toMutableList().apply { add(ins, song) }
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
        val insertPos = curIdx.coerceIn(0, rest.size)
        val newList = rest.toMutableList().apply { add(insertPos, current) }
        // currentIndex points to the just-reinserted `current`, never the
        // first matching id in case the same song sits twice in the queue.
        val newCurIdx = insertPos
        _state.update {
            it.copy(queue = newList, currentIndex = newCurIdx, shuffle = true)
        }
        if (!demoMode) {
            player.setMediaItems(newList.map(::toMediaItem), newCurIdx, (s.positionSec * 1000).toLong())
            // Don't call prepare() — it interrupts playback
            player.playWhenReady = s.isPlaying
            player.shuffleModeEnabled = false
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

    /** Drop every queued song after the current one, without touching playback. */
    fun clearUpcoming() {
        val s = _state.value
        if (s.currentIndex >= s.queue.lastIndex) return
        val kept = s.queue.take(s.currentIndex + 1)
        _state.update { it.copy(queue = kept) }
        originalQueue = kept.toList()
        if (!demoMode) {
            runCatching { player.removeMediaItems(s.currentIndex + 1, player.mediaItemCount) }
        }
        saveSession()
    }

    private fun demoAdvance() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (s.currentIndex < s.queue.lastIndex) {
            _state.update {
                it.copy(currentIndex = s.currentIndex + 1, positionSec = 0f)
            }
            _state.value.current?.let(library::scrobble)
            return
        }
        // Just played the last item: handle repeat modes.
        when (s.repeat) {
            RepeatMode.ALL -> {
                if (s.shuffle && s.queue.size > 1) {
                    // Pick a random head song, shuffle the rest behind it — same
                    // rule as the real-mode reshuffle so the next lap starts at
                    // a random song sitting visibly at queue slot 0.
                    val first = s.queue.random()
                    val rest = s.queue.toMutableList().apply { removeAt(s.queue.indexOf(first)) }.shuffled()
                    val newQueue = listOf(first) + rest
                    _state.update {
                        it.copy(queue = newQueue, currentIndex = 0, positionSec = 0f)
                    }
                } else {
                    _state.update { it.copy(currentIndex = 0, positionSec = 0f) }
                }
            }
            RepeatMode.ONCE, RepeatMode.OFF -> {
                _state.update { it.copy(isPlaying = false, positionSec = 0f) }
                return
            }
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
        // Plain startService, NOT startForegroundService: the latter demands
        // startForeground() within 5s, but media3 only promotes the service once
        // playback is actually active — a slow stream start or a broken URL then
        // kills the whole app (ForegroundServiceDidNotStartInTimeException).
        // This is always called from the foreground (a user tap), where plain
        // startService is allowed; media3 handles the foreground promotion.
        runCatching {
            context.startService(Intent(context, PlaybackService::class.java))
        }
    }
}
