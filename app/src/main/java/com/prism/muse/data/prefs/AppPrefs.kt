package com.prism.muse.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ServerConfig(
    val url: String,
    val username: String,
    val password: String
) {
    val isConfigured: Boolean get() = url.isNotBlank() && username.isNotBlank()
}

/**
 * SharedPreferences-backed settings + Navidrome account store. Kept
 * plugin-free (no Room/DataStore) so the build stays lean; swap for encrypted
 * storage before shipping real credentials.
 */
class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aria_prefs", Context.MODE_PRIVATE)

    private val _server = MutableStateFlow(readServer())
    val server: StateFlow<ServerConfig> = _server

    private val _offlineMode = MutableStateFlow(prefs.getBoolean("offline_mode", false))
    val offlineMode: StateFlow<Boolean> = _offlineMode

    private val _depthEffect = MutableStateFlow(prefs.getBoolean("depth_effect", true))
    val depthEffect: StateFlow<Boolean> = _depthEffect

    private val _gapless = MutableStateFlow(prefs.getBoolean("gapless", true))
    val gapless: StateFlow<Boolean> = _gapless

    private val _replayGain = MutableStateFlow(prefs.getBoolean("replaygain", false))
    val replayGain: StateFlow<Boolean> = _replayGain

    private val _crossfadeSec = MutableStateFlow(prefs.getInt("crossfade_sec", 0))
    val crossfadeSec: StateFlow<Int> = _crossfadeSec

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat("playback_speed", 1f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _accentColorName = MutableStateFlow(prefs.getString("accent_color", "cyan") ?: "cyan")
    val accentColorName: StateFlow<String> = _accentColorName

    private val _autoFetchLyrics = MutableStateFlow(prefs.getBoolean("auto_fetch_lyrics", true))
    val autoFetchLyrics: StateFlow<Boolean> = _autoFetchLyrics

    /** Which online lyric providers the multi-source fetch is allowed to use. */
    private val _lyricsProviders = MutableStateFlow(
        prefs.getStringSet("lyrics_providers", ALL_LYRIC_PROVIDERS.toSet())?.toSet()
            ?: ALL_LYRIC_PROVIDERS.toSet()
    )
    val lyricsProviders: StateFlow<Set<String>> = _lyricsProviders

    fun setLyricsProviders(value: Set<String>) {
        prefs.edit().putStringSet("lyrics_providers", value).apply()
        _lyricsProviders.value = value
    }

    private val _eqEnabled = MutableStateFlow(prefs.getBoolean("eq_enabled", true))
    val eqEnabled: StateFlow<Boolean> = _eqEnabled

    private val _bassBoostOn = MutableStateFlow(prefs.getBoolean("bass_boost_on", false))
    val bassBoostOn: StateFlow<Boolean> = _bassBoostOn
    fun setBassBoostOn(value: Boolean) = putBool("bass_boost_on", value, _bassBoostOn)

    private val _virtualizerOn = MutableStateFlow(prefs.getBoolean("virtualizer_on", false))
    val virtualizerOn: StateFlow<Boolean> = _virtualizerOn
    fun setVirtualizerOn(value: Boolean) = putBool("virtualizer_on", value, _virtualizerOn)

    private val _loudnessOn = MutableStateFlow(prefs.getBoolean("loudness_on", false))
    val loudnessOn: StateFlow<Boolean> = _loudnessOn
    fun setLoudnessOn(value: Boolean) = putBool("loudness_on", value, _loudnessOn)

    /** Now Playing background style: blurred, gradient, waves, solid. */
    private val _npBackground = MutableStateFlow(prefs.getString("np_background", "blurred") ?: "blurred")
    val npBackground: StateFlow<String> = _npBackground

    /** Dynamic accent: derive accent color from album art. */
    private val _dynamicAccent = MutableStateFlow(prefs.getBoolean("dynamic_accent", true))
    val dynamicAccent: StateFlow<Boolean> = _dynamicAccent

    /** Saved custom EQ band levels (millibels) as CSV; "" when none saved. */
    private val _customEq = MutableStateFlow(prefs.getString("custom_eq", "") ?: "")
    val customEq: StateFlow<String> = _customEq

    private fun readServer() = ServerConfig(
        url = prefs.getString("server_url", "") ?: "",
        username = prefs.getString("server_user", "") ?: "",
        password = prefs.getString("server_pass", "") ?: ""
    )

    fun saveServer(config: ServerConfig) {
        prefs.edit()
            .putString("server_url", config.url.trimEnd('/'))
            .putString("server_user", config.username)
            .putString("server_pass", config.password)
            .apply()
        _server.value = readServer()
    }

    fun clearServer() = saveServer(ServerConfig("", "", ""))

    fun setOfflineMode(value: Boolean) = putBool("offline_mode", value, _offlineMode)
    fun setDepthEffect(value: Boolean) = putBool("depth_effect", value, _depthEffect)
    fun setGapless(value: Boolean) = putBool("gapless", value, _gapless)
    fun setReplayGain(value: Boolean) = putBool("replaygain", value, _replayGain)

    fun setCrossfadeSec(value: Int) {
        prefs.edit().putInt("crossfade_sec", value).apply()
        _crossfadeSec.value = value
    }

    fun setPlaybackSpeed(value: Float) {
        prefs.edit().putFloat("playback_speed", value).apply()
        _playbackSpeed.value = value
    }

    fun setAccentColorName(name: String) {
        prefs.edit().putString("accent_color", name).apply()
        _accentColorName.value = name
    }

    fun setAutoFetchLyrics(value: Boolean) = putBool("auto_fetch_lyrics", value, _autoFetchLyrics)

    fun setEqEnabled(value: Boolean) = putBool("eq_enabled", value, _eqEnabled)

    fun setNpBackground(value: String) {
        prefs.edit().putString("np_background", value).apply()
        _npBackground.value = value
    }

    fun setDynamicAccent(value: Boolean) = putBool("dynamic_accent", value, _dynamicAccent)

    /** Which home tabs are visible; defaults to all. */
    private val _visibleTabs = MutableStateFlow(
        prefs.getStringSet("visible_tabs", ALL_TABS.toSet()) ?: setOf()
    )
    val visibleTabs: StateFlow<Set<String>> = _visibleTabs

    fun setVisibleTabs(tabs: Set<String>) {
        prefs.edit().putStringSet("visible_tabs", tabs).apply()
        _visibleTabs.value = tabs
    }

    /** Home tab order (all tabs, shown or not); StringSet can't hold order. */
    private val _tabOrder = MutableStateFlow(readTabOrder())
    val tabOrder: StateFlow<List<String>> = _tabOrder

    fun setTabOrder(order: List<String>) {
        prefs.edit().putString("tab_order", order.joinToString("|")).apply()
        _tabOrder.value = readTabOrder()
    }

    private fun readTabOrder(): List<String> {
        val saved = (prefs.getString("tab_order", "") ?: "")
            .split("|").filter { it in ALL_TABS }
        // Tabs added in app updates get appended so they never vanish.
        return saved + ALL_TABS.filterNot { it in saved }
    }

    /** Visualizer rendering style: bars, wave, ring. */
    private val _visualizerStyle = MutableStateFlow(prefs.getString("visualizer_style", "bars") ?: "bars")
    val visualizerStyle: StateFlow<String> = _visualizerStyle

    fun setVisualizerStyle(value: String) {
        prefs.edit().putString("visualizer_style", value).apply()
        _visualizerStyle.value = value
    }

    /** Lyrics highlight style: karaoke (sweep), fade, spotlight. */
    private val _lyricsStyle = MutableStateFlow(prefs.getString("lyrics_style", "karaoke") ?: "karaoke")
    val lyricsStyle: StateFlow<String> = _lyricsStyle

    fun setLyricsStyle(value: String) {
        prefs.edit().putString("lyrics_style", value).apply()
        _lyricsStyle.value = value
    }

    companion object {
        val ALL_TABS = listOf(
            "recommended", "recently played", "albums", "artists",
            "playlists", "favorites", "downloaded", "genres", "all songs"
        )

        /** Selectable lyric providers, in fetch-priority order (synced first). */
        val ALL_LYRIC_PROVIDERS = listOf("lrclib", "netease", "subsonic", "genius", "lyrics.ovh")
    }

    fun setCustomEq(levels: List<Int>) {
        val csv = levels.joinToString(",")
        prefs.edit().putString("custom_eq", csv).apply()
        _customEq.value = csv
    }

    /** Saved EQ presets as name -> band-levels-CSV map. */
    private val _savedEqPresets = MutableStateFlow(readPresets())
    val savedEqPresets: StateFlow<Map<String, String>> = _savedEqPresets

    fun saveEqPreset(name: String, levels: List<Int>) {
        val all = readPresets().toMutableMap()
        all[name] = levels.joinToString(",")
        prefs.edit().putString("eq_presets", all.entries.joinToString(";;") { "${it.key}=${it.value}" }).apply()
        _savedEqPresets.value = all
    }

    private fun readPresets(): Map<String, String> {
        val raw = prefs.getString("eq_presets", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split(";;").mapNotNull { part ->
            val eqIdx = part.indexOf('=')
            if (eqIdx < 0) return@mapNotNull null
            part.substring(0, eqIdx) to part.substring(eqIdx + 1)
        }.toMap()
    }

    private fun putBool(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        flow.value = value
    }
}
