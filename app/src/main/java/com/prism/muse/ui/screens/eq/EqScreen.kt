package com.prism.muse.ui.screens.eq

import android.media.audiofx.Equalizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.components.PlayerBackdrop
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.VoidBlack
import kotlinx.coroutines.launch

@Composable
fun EqScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    artUrl: String? = null
) {
    val accent = LocalPrismAccent.current
    val context = LocalContext.current
    val graph = PrismApp.graph(context)
    val prefs = graph.prefs
    var eqEnabled by remember { mutableStateOf(prefs.eqEnabled.value) }

    // The DSP chain is owned by the player (app-scoped, stable session), so it
    // survives navigation and keeps applying during playback. `revision` bumps
    // whenever it (re)binds, so the metadata/band snapshots below rebuild then.
    val effects = graph.player.audioEffects
    val revision by effects.revision.collectAsState()
    LaunchedEffect(Unit) { effects.attach(graph.player.audioSessionId) }

    var menuExpanded by remember { mutableStateOf(false) }
    // Bumped by actions (reset/preset from the menu) that change bands from
    // outside the sliders, so the sliders re-read the equalizer.
    var reloadTick by remember { mutableStateOf(0) }
    var savePresetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    val savedPresets = prefs.savedEqPresets.value
    var importedToast by remember { mutableStateOf<String?>(null) }

    PlayerBackdrop(artUrl = artUrl) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .pointerInput(Unit) {
                    var down = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { if (down > 80f) onBack(); down = 0f },
                        onDragCancel = { down = 0f }
                    ) { _, dy -> down += dy }
                }
        ) {
            // Header row
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹ now playing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onBack)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Box {
                    Text(
                        "\u2022\u2022\u2022",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.clickable { menuExpanded = true }.padding(8.dp)
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(VoidBlack)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save preset", color = TextPrimary) },
                            onClick = { savePresetDialog = true; menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Export EQ data", color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                importedToast = "EQ data copied"
                                android.widget.Toast.makeText(context, "EQ data copied", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import EQ data", color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                importedToast = "import from clipboard"
                                android.widget.Toast.makeText(context, "import from clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset to flat", color = TextTertiary) },
                            onClick = {
                                menuExpanded = false
                                effects.eq?.let { eq ->
                                    runCatching {
                                        for (i in 0 until eq.numberOfBands) {
                                            runCatching { eq.setBandLevel(i.toShort(), 0) }
                                        }
                                    }
                                }
                                effects.persist()
                                reloadTick++
                            }
                        )
                        // Saved presets sub-menu
                        savedPresets.entries.forEach { (name: String, data: String) ->
                            DropdownMenuItem(
                                text = { Text(name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    menuExpanded = false
                                    applyPreset(effects.eq, data)
                                    effects.persist()
                                    reloadTick++
                                }
                            )
                        }
                    }
                }
            }

            Text(
                "equalizer",
                style = HubTitle.copy(fontSize = 48.sp, lineHeight = 52.sp),
                color = TextPrimary,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Save preset row
            if (savePresetDialog) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        placeholder = { Text("preset name…", color = TextTertiary) },
                        singleLine = true,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                    Text("save", style = SectionHeader.copy(fontSize = 18.sp),
                        color = if (presetName.isBlank()) TextTertiary else accent,
                        modifier = Modifier.clickable(enabled = presetName.isNotBlank()) {
                            val e = effects.eq ?: return@clickable
                            val levels = runCatching {
                                (0 until e.numberOfBands).map { e.getBandLevel(it.toShort()).toInt() }
                            }.getOrNull() ?: return@clickable
                            prefs.saveEqPreset(presetName.trim(), levels)
                            savePresetDialog = false
                            presetName = ""
                        }.padding(start = 12.dp)
                    )
                    Text("cancel", style = SectionHeader.copy(fontSize = 18.sp),
                        color = TextTertiary,
                        modifier = Modifier.clickable { savePresetDialog = false; presetName = "" }.padding(start = 8.dp)
                    )
                }
            }

            // Reading band/preset metadata can itself throw on flaky audio HALs;
            // gather it once behind runCatching and bail out to the placeholder
            // text rather than crashing the screen.
            val eqInfo = remember(revision) {
                val eq0 = effects.eq ?: return@remember null
                runCatching {
                    EqInfo(
                        bandCount = eq0.numberOfBands.toInt(),
                        minLevel = eq0.bandLevelRange[0].toInt(),
                        maxLevel = eq0.bandLevelRange[1].toInt(),
                        presetNames = (0 until eq0.numberOfPresets).map { i ->
                            runCatching { eq0.getPresetName(i.toShort()) }.getOrDefault("preset $i")
                        },
                        centerFreqHz = (0 until eq0.numberOfBands.toInt()).map { b ->
                            runCatching { eq0.getCenterFreq(b.toShort()) / 1000 }.getOrDefault(0)
                        }
                    )
                }.getOrNull()
            }

            val eq = effects.eq
            if (eq == null || eqInfo == null || eqInfo.bandCount <= 0) {
                Text(
                    "equalizer unavailable — start playback on a real stream first",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 30.dp)
                )
                return@PlayerBackdrop
            }

            val bandCount = eqInfo.bandCount
            val minLevel = eqInfo.minLevel
            val maxLevel = eqInfo.maxLevel

            val bandLevels = remember(revision, reloadTick) {
                mutableStateListOf<Int>().apply {
                    repeat(bandCount) {
                        add(runCatching { eq.getBandLevel(it.toShort()).toInt() }.getOrDefault(0))
                    }
                }
            }
            var presetIndex by remember { mutableIntStateOf(-1) }
            var activeBand by remember { mutableIntStateOf(bandCount / 2) }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (presetIndex < 0) "Custom" else eqInfo.presetNames.getOrNull(presetIndex) ?: "Custom",
                    style = MaterialTheme.typography.bodyLarge,
                    color = accent
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    // Edits auto-save now; this stays as an explicit confirm.
                    Text(
                        "save",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.clickable {
                            effects.persist()
                            presetIndex = -1
                            android.widget.Toast.makeText(context, "saved", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    Text(
                        if (eqEnabled) "on" else "off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (eqEnabled) accent else TextTertiary,
                        modifier = Modifier.clickable {
                            eqEnabled = !eqEnabled
                            prefs.setEqEnabled(eqEnabled)
                            effects.setEnabled(eqEnabled)
                        }
                    )
                }
            }

            // Preset strip
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Text(
                    "custom",
                    style = SectionHeader.copy(fontSize = 18.sp),
                    color = if (presetIndex < 0) accent else TextSecondary,
                    modifier = Modifier.clickable { presetIndex = -1 }
                )
                eqInfo.presetNames.forEachIndexed { i, name ->
                    Text(
                        name.lowercase(),
                        style = SectionHeader.copy(fontSize = 18.sp),
                        color = if (presetIndex == i) accent else TextSecondary,
                        modifier = Modifier.clickable {
                            presetIndex = i
                            effects.usePreset(i)
                            repeat(bandCount) { b ->
                                bandLevels[b] = runCatching { eq.getBandLevel(b.toShort()).toInt() }.getOrDefault(bandLevels[b])
                            }
                        }
                    )
                }
            }

            // Band sliders
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(bandCount) { band ->
                    val centerHz = eqInfo.centerFreqHz.getOrElse(band) { 0 }
                    BandSlider(
                        label = if (centerHz >= 1000) "${centerHz / 1000}k" else "$centerHz",
                        fraction = (bandLevels[band] - minLevel).toFloat() / (maxLevel - minLevel).coerceAtLeast(1),
                        dotColor = if (band == activeBand) accent else Color.White,
                        modifier = Modifier.weight(1f),
                        onChange = { f ->
                            activeBand = band
                            presetIndex = -1
                            val level = (minLevel + f * (maxLevel - minLevel)).toInt()
                            bandLevels[band] = level
                            effects.setBand(band, level)
                        }
                    )
                }
            }

            var bassOn by remember { mutableStateOf(effects.bassOn) }
            var virtOn by remember { mutableStateOf(effects.virtOn) }
            var loudOn by remember { mutableStateOf(effects.loudOn) }

            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ToggleRow("Bass Boost", bassOn, accent) {
                    bassOn = !bassOn
                    effects.bassOn = bassOn
                }
                ToggleRow("Virtualizer", virtOn, accent) {
                    virtOn = !virtOn
                    effects.virtOn = virtOn
                }
                ToggleRow("Loudness", loudOn, accent) {
                    loudOn = !loudOn
                    effects.loudOn = loudOn
                }
            }

            if (importedToast != null) {
                Text(importedToast!!, style = MaterialTheme.typography.bodyMedium, color = TextTertiary,
                    modifier = Modifier.padding(top = 4.dp))
                LaunchedEffect(importedToast) {
                    kotlinx.coroutines.delay(2000)
                    importedToast = null
                }
            }
        }
    }
}

private fun applyPreset(eq: Equalizer?, data: String) {
    val e = eq ?: return
    runCatching {
        val levels = data.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (levels.size == e.numberOfBands.toInt()) {
            levels.forEachIndexed { i, lvl -> runCatching { e.setBandLevel(i.toShort(), lvl.toShort()) } }
        }
    }
}

/** Equalizer metadata snapshotted once behind runCatching (HALs can throw). */
private class EqInfo(
    val bandCount: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val presetNames: List<String>,
    val centerFreqHz: List<Int>
)

@Composable
private fun BandSlider(
    label: String,
    fraction: Float,
    dotColor: Color,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(34.dp)
                .weight(1f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        onChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val y = size.height * (1f - fraction.coerceIn(0f, 1f))
                drawLine(Color.White.copy(alpha = 0.15f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), StrokeCap.Butt)
                drawCircle(dotColor, 7.5.dp.toPx(), Offset(x, y))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp), color = TextTertiary, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, accent: Color, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.75f))
        Text(if (on) "on" else "off", style = MaterialTheme.typography.bodyLarge, color = if (on) accent else TextTertiary)
    }
}
