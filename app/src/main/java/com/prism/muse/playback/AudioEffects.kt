package com.prism.muse.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.prism.muse.data.prefs.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-scoped DSP chain (EQ, bass, virtualizer, loudness), bound to the
 * player's stable audio session. Lives for the whole process so the EQ
 * keeps applying during playback regardless of screen visibility.
 */
class AudioEffects(private val prefs: AppPrefs) {

    private var sessionId = 0

    var eq: Equalizer? = null
        private set
    var bass: BassBoost? = null
        private set
    var virt: Virtualizer? = null
        private set
    var loud: LoudnessEnhancer? = null
        private set

    /** Bumped on every (re)bind so Compose can key `remember` blocks off it. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    val available: Boolean get() = eq != null

    /** Persisted toggles for the non-EQ effects (survive screen + process). */
    var bassOn: Boolean
        get() = prefs.bassBoostOn.value
        set(v) { prefs.setBassBoostOn(v); runCatching { bass?.setStrength(if (v) 700 else 0) } }
    var virtOn: Boolean
        get() = prefs.virtualizerOn.value
        set(v) { prefs.setVirtualizerOn(v); runCatching { virt?.setStrength(if (v) 700 else 0) } }
    var loudOn: Boolean
        get() = prefs.loudnessOn.value
        set(v) {
            prefs.setLoudnessOn(v)
            runCatching { loud?.setTargetGain(if (v) 500 else 0); loud?.enabled = v }
        }

    /**
     * (Re)bind to an audio session. No-op when the id is unset or unchanged, so
     * it is safe to call every playback tick until it succeeds.
     */
    fun attach(newSessionId: Int) {
        if (newSessionId == 0 || (newSessionId == sessionId && eq != null)) return
        release()
        sessionId = newSessionId
        runCatching {
            eq = Equalizer(0, sessionId).apply { enabled = prefs.eqEnabled.value }
            bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
            virt = runCatching { Virtualizer(0, sessionId) }.getOrNull()
            loud = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        }.onFailure { eq = null }
        applySaved()
        _revision.value = _revision.value + 1
    }

    private fun applySaved() {
        val e = eq ?: return
        runCatching {
            val saved = prefs.customEq.value.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (saved.size == e.numberOfBands.toInt()) {
                saved.forEachIndexed { i, lvl -> runCatching { e.setBandLevel(i.toShort(), lvl.toShort()) } }
            }
        }
        runCatching { bass?.setStrength(if (prefs.bassBoostOn.value) 700 else 0) }
        runCatching { virt?.setStrength(if (prefs.virtualizerOn.value) 700 else 0) }
        runCatching {
            loud?.setTargetGain(if (prefs.loudnessOn.value) 500 else 0)
            loud?.enabled = prefs.loudnessOn.value
        }
    }

    fun setEnabled(on: Boolean) {
        runCatching { eq?.enabled = on }
    }

    /** Set one band and persist the whole curve so edits are never lost. */
    fun setBand(band: Int, level: Int) {
        runCatching { eq?.setBandLevel(band.toShort(), level.toShort()) }
        persist()
    }

    /** Apply a device preset, then persist the resulting curve. */
    fun usePreset(index: Int) {
        runCatching { eq?.usePreset(index.toShort()) }
        persist()
    }

    /** Snapshot the current band curve to prefs. */
    fun persist() {
        val e = eq ?: return
        runCatching {
            val levels = (0 until e.numberOfBands).map { e.getBandLevel(it.toShort()).toInt() }
            prefs.setCustomEq(levels)
        }
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { virt?.release() }
        runCatching { loud?.release() }
        eq = null; bass = null; virt = null; loud = null
    }
}
