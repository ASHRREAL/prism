package com.prism.muse.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.ui.components.AriaBackground
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.theme.AccentColors
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.TrackedLabel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit
) {
    val graph = PrismApp.graph(LocalContext.current)
    val prefs = graph.prefs
    val library = graph.library
    val accent = LocalPrismAccent.current
    val scope = rememberCoroutineScope()

    val server by prefs.server.collectAsState()
    val offline by prefs.offlineMode.collectAsState()
    val gapless by prefs.gapless.collectAsState()
    val replayGain by prefs.replayGain.collectAsState()
    val crossfade by prefs.crossfadeSec.collectAsState()
    val speed by prefs.playbackSpeed.collectAsState()
    val depth by prefs.depthEffect.collectAsState()
    val syncing by library.syncing.collectAsState()
    val accentName by prefs.accentColorName.collectAsState()
    val autoFetchLyrics by prefs.autoFetchLyrics.collectAsState()
    val npBg by prefs.npBackground.collectAsState()
    val dynamicAccent by prefs.dynamicAccent.collectAsState()
    val eqEnabled by prefs.eqEnabled.collectAsState()

    var downloadsBytes by remember { mutableStateOf(library.downloadsSizeBytes()) }

    val bgLabels = mapOf(
        "blurred" to "blurred art",
        "gradient" to "radial gradient",
        "waves" to "animated waves",
        "solid" to "solid black"
    )

    AriaBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 80.dp)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    var swipe = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { if (swipe > 180f) onBack(); swipe = 0f },
                        onDragCancel = { swipe = 0f }
                    ) { _, dx -> swipe += dx }
                }
        ) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹",
                    style = SectionHeader.copy(fontSize = 34.sp),
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp)
                )
                Text("settings", style = HubTitle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = TextPrimary, modifier = Modifier.align(Alignment.CenterVertically))
            }

            GroupLabel("SERVER")
            SettingRow(
                "Navidrome accounts",
                if (server.isConfigured) "${server.username} ›" else "add ›",
                accentValue = server.isConfigured,
                accent = accent,
                onClick = onOpenAccounts
            )
            SettingRow(
                "Offline mode",
                if (offline) "on" else "off",
                accentValue = offline,
                accent = accent,
                onClick = { prefs.setOfflineMode(!offline) }
            )
            SettingRow(
                "Sync now",
                if (syncing) "syncing…" else "›",
                accentValue = syncing,
                accent = accent,
                onClick = { scope.launch { library.refresh() } }
            )

            GroupLabel("PLAYBACK")
            SettingRow(
                "Crossfade",
                if (crossfade == 0) "off ›" else "${crossfade}s ›",
                accentValue = crossfade > 0,
                accent = accent,
                onClick = { prefs.setCrossfadeSec((crossfade + 2) % 12) }
            )
            SettingRow(
                "Gapless playback",
                if (gapless) "on" else "off",
                accentValue = gapless,
                accent = accent,
                onClick = { prefs.setGapless(!gapless) }
            )
            SettingRow(
                "ReplayGain",
                if (replayGain) "on" else "off",
                accentValue = replayGain,
                accent = accent,
                onClick = { prefs.setReplayGain(!replayGain) }
            )
            SettingRow(
                "Playback speed",
                "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x ›",
                accentValue = speed != 1f,
                accent = accent,
                onClick = {
                    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                    val next = speeds[(speeds.indexOfFirst { it >= speed - 0.01f } + 1) % speeds.size]
                    graph.player.setSpeed(next)
                }
            )
            SettingRow(
                "Equalizer",
                if (eqEnabled) "on" else "off",
                accentValue = eqEnabled,
                accent = accent,
                onClick = { prefs.setEqEnabled(!eqEnabled) }
            )

            GroupLabel("APPEARANCE")
            SettingRow(
                "3D depth effect",
                if (depth) "on" else "off",
                accentValue = depth,
                accent = accent,
                onClick = { prefs.setDepthEffect(!depth) }
            )
            SettingRow("Now Playing background", "${bgLabels[npBg] ?: "blurred art"} ›",
                accentValue = false, accent = accent,
                onClick = {
                    val styles = listOf("blurred", "gradient", "waves", "solid")
                    val idx = styles.indexOf(npBg)
                    prefs.setNpBackground(styles[(idx + 1) % styles.size])
                }
            )
            SettingRow(
                "Accent color",
                accentName,
                accentValue = true,
                accent = accent,
                onClick = {
                    val names = AccentColors.keys.toList()
                    val idx = names.indexOf(accentName)
                    prefs.setAccentColorName(names[(idx + 1) % names.size])
                }
            )
            SettingRow(
                "Dynamic accent",
                if (dynamicAccent) "from artwork" else "fixed",
                accentValue = dynamicAccent,
                accent = accent,
                onClick = { prefs.setDynamicAccent(!dynamicAccent) }
            )

            GroupLabel("LYRICS")
            SettingRow(
                "Auto-fetch lyrics",
                if (autoFetchLyrics) "multi-source" else "off",
                accentValue = autoFetchLyrics,
                accent = accent,
                onClick = { prefs.setAutoFetchLyrics(!autoFetchLyrics) }
            )

            GroupLabel("STORAGE")
            SettingRow(
                "Downloads",
                "${formatBytes(downloadsBytes)} ›",
                accentValue = false,
                accent = accent,
                onClick = {}
            )
            SettingRow(
                "Clear cache",
                "›",
                accentValue = false,
                accent = accent,
                onClick = {
                    library.clearDownloads()
                    downloadsBytes = library.downloadsSizeBytes()
                }
            )

            GroupLabel("HOME TABS")
            val allTabs = listOf("recommended", "recently played", "albums", "artists", "playlists", "favorites", "downloaded", "genres", "all songs")
            val visibleTabs by prefs.visibleTabs.collectAsState()
            allTabs.forEach { tab ->
                SettingRow(
                    tab.replaceFirstChar { it.uppercase() },
                    if (tab in visibleTabs) "shown" else "hidden",
                    accentValue = tab in visibleTabs,
                    accent = accent,
                    onClick = {
                        val current = visibleTabs.toMutableSet()
                        if (tab in current) current.remove(tab) else current.add(tab)
                        prefs.setVisibleTabs(current)
                    }
                )
            }

            GroupLabel("ABOUT")
            SettingRow("Prism Muse", "0.2.0", accentValue = false, accent = accent, onClick = {})
            SettingRow("Build", "prototype", accentValue = false, accent = accent, onClick = {})
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = TrackedLabel,
        color = TextTertiary,
        modifier = Modifier.padding(top = 30.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    accentValue: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (accentValue) accent else TextTertiary
            )
        }
        HairlineDivider()
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes > 0 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "0 MB"
}
