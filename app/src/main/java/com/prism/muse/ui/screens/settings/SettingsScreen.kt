package com.prism.muse.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.prism.muse.ui.theme.VoidBlack
import kotlinx.coroutines.launch

/** One multiple-choice settings row: title + all options shown in a sheet. */
private class PickerData(
    val title: String,
    val options: List<String>,
    val selected: String,
    val onPick: (String) -> Unit
)

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
    var picker by remember { mutableStateOf<PickerData?>(null) }

    val bgLabels = mapOf(
        "blurred" to "blurred art",
        "gradient" to "radial gradient",
        "waves" to "animated waves",
        "solid" to "solid black"
    )

    AriaBackground {
        Box(Modifier.fillMaxSize()) {
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
                onClick = {
                    picker = PickerData(
                        title = "crossfade",
                        options = listOf("off", "2s", "4s", "6s", "8s", "10s", "12s"),
                        selected = if (crossfade == 0) "off" else "${crossfade}s"
                    ) { sel -> prefs.setCrossfadeSec(sel.removeSuffix("s").toIntOrNull() ?: 0) }
                }
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
                    val options = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
                    picker = PickerData(
                        title = "playback speed",
                        options = options,
                        selected = options.firstOrNull {
                            it.removeSuffix("x").toFloat() == speed
                        } ?: "1x"
                    ) { sel ->
                        graph.player.setSpeed(sel.removeSuffix("x").toFloatOrNull() ?: 1f)
                    }
                }
            )
            SettingRow(
                "Equalizer",
                if (eqEnabled) "on" else "off",
                accentValue = eqEnabled,
                accent = accent,
                onClick = { prefs.setEqEnabled(!eqEnabled) }
            )
            val sleepMin by graph.player.sleepTimerMin.collectAsState()
            SettingRow(
                "Sleep timer",
                if (sleepMin == 0) "off ›" else "${sleepMin}m ›",
                accentValue = sleepMin > 0,
                accent = accent,
                onClick = {
                    picker = PickerData(
                        title = "sleep timer",
                        options = listOf("off", "15m", "30m", "45m", "60m", "90m"),
                        selected = if (sleepMin == 0) "off" else "${sleepMin}m"
                    ) { sel -> graph.player.setSleepTimer(sel.removeSuffix("m").toIntOrNull() ?: 0) }
                }
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
                    picker = PickerData(
                        title = "now playing background",
                        options = bgLabels.values.toList(),
                        selected = bgLabels[npBg] ?: "blurred art"
                    ) { sel ->
                        prefs.setNpBackground(
                            bgLabels.entries.firstOrNull { it.value == sel }?.key ?: "blurred"
                        )
                    }
                }
            )
            SettingRow(
                "Accent color",
                "$accentName ›",
                accentValue = true,
                accent = accent,
                onClick = {
                    picker = PickerData(
                        title = "accent color",
                        options = AccentColors.keys.toList(),
                        selected = accentName
                    ) { sel -> prefs.setAccentColorName(sel) }
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

            GroupLabel("HOME TABS · TAP TO SHOW/HIDE · DRAG ⋮⋮ TO REORDER")
            val visibleTabs by prefs.visibleTabs.collectAsState()
            val tabOrder by prefs.tabOrder.collectAsState()
            // Live, local copy dragged in real time; committed to prefs on release
            // so we're not writing SharedPreferences on every pointer move.
            val liveTabs = remember(tabOrder) {
                mutableStateListOf<String>().also { it.addAll(tabOrder) }
            }
            var draggedTabName by remember { mutableStateOf<String?>(null) }
            var draggedStartTabIdx by remember { mutableIntStateOf(-1) }
            var tabDragAccum by remember { mutableFloatStateOf(0f) }
            val tabRowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }

            liveTabs.forEachIndexed { idx, tab ->
                androidx.compose.runtime.key(tab) {
                val shown = tab in visibleTabs
                val dragging = draggedTabName == tab

                fun toggle() {
                    val current = visibleTabs.toMutableSet()
                    if (tab in current) current.remove(tab) else current.add(tab)
                    prefs.setVisibleTabs(current)
                }

                Column(
                    Modifier.graphicsLayer {
                        translationY = if (dragging) tabDragAccum else 0f
                        scaleX = if (dragging) 1.03f else 1f
                        scaleY = if (dragging) 1.03f else 1f
                    }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tab.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (shown) TextPrimary else TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { toggle() }
                        )
                        Text(
                            if (shown) "shown" else "hidden",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (shown) accent else TextTertiary,
                            modifier = Modifier.clickable { toggle() }.padding(horizontal = 12.dp)
                        )
                        Text(
                            "⋮⋮",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (dragging) accent else TextTertiary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .pointerInput(tab) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            draggedTabName = tab
                                            draggedStartTabIdx = liveTabs.indexOf(tab)
                                            tabDragAccum = 0f
                                        },
                                        onDragEnd = {
                                            draggedTabName = null; tabDragAccum = 0f
                                            prefs.setTabOrder(liveTabs.toList())
                                        },
                                        onDragCancel = {
                                            draggedTabName = null; tabDragAccum = 0f
                                            prefs.setTabOrder(liveTabs.toList())
                                        }
                                    ) { change, dy ->
                                        change.consume()
                                        tabDragAccum += dy
                                        // Swap if accumulated drag passes half a row height
                                        val i = liveTabs.indexOf(tab)
                                        if (i >= 0 && kotlin.math.abs(tabDragAccum) > tabRowHeightPx * 0.5f) {
                                            val dir = if (tabDragAccum > 0) 1 else -1
                                            val target = (i + dir).coerceIn(0, liveTabs.lastIndex)
                                            if (target != i) {
                                                liveTabs[i] = liveTabs[target].also { liveTabs[target] = liveTabs[i] }
                                                tabDragAccum -= dir * tabRowHeightPx
                                            }
                                        }
                                    }
                                }
                        )
                    }
                    HairlineDivider()
                }
                } // key(tab)
            }

            GroupLabel("ABOUT")
            SettingRow("Prism Muse", "0.2.0", accentValue = false, accent = accent, onClick = {})
            SettingRow("Build", "beta", accentValue = false, accent = accent, onClick = {})
        }

        // Multiple-choice picker sheet: scrim + all options at the bottom.
        picker?.let { p ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { picker = null } }
            )
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(VoidBlack)
                    .pointerInput(Unit) { detectTapGestures { /* consume */ } }
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Text(p.title.uppercase(), style = TrackedLabel, color = TextTertiary)
                HairlineDivider(Modifier.padding(top = 10.dp, bottom = 4.dp))
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    p.options.forEach { option ->
                        Text(
                            option,
                            style = SectionHeader.copy(fontSize = 22.sp),
                            color = if (option == p.selected) accent else TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { p.onPick(option); picker = null }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
        } // Box
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
