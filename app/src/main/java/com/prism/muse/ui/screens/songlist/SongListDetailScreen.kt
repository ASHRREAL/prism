package com.prism.muse.ui.screens.songlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.data.model.Song
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.AriaBackground
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.components.SongRowWithMenu
import com.prism.muse.ui.components.gyroTilt
import com.prism.muse.ui.components.TextLinkRow
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListDetailScreen(
    title: String,
    songs: List<Song>,
    viewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    val accent = LocalPrismAccent.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val depthEffect = com.prism.muse.PrismApp.graph(ctx).prefs.depthEffect.collectAsState().value

    AriaBackground(tinted = true) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .pointerInput(Unit) {
                    var swipe = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { if (swipe > 180f) onBack(); swipe = 0f },
                        onDragCancel = { swipe = 0f }
                    ) { _, dx -> swipe += dx }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", style = SectionHeader.copy(fontSize = 34.sp), color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp))
                Text(title, style = HubTitle.copy(fontSize = 40.sp, lineHeight = 44.sp), color = TextPrimary,
                    modifier = Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                TextLinkRow(links = listOf("play all", "shuffle"), accent = accent,
                    onClick = { link ->
                        when (link) {
                            "play all" -> viewModel.playQueue(songs, 0)
                            "shuffle" -> viewModel.playQueue(songs.shuffled(), 0)
                        }
                    }
                )
                Text("${songs.size} tracks", style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    modifier = Modifier.weight(1f).padding(end = 8.dp), softWrap = false, maxLines = 1)
            }

            HairlineDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            if (songs.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("no songs found", style = SectionHeader.copy(fontSize = 22.sp), color = TextTertiary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 80.dp),
                    modifier = Modifier.weight(1f)
                        .then(if (depthEffect) Modifier.gyroTilt(maxDegrees = 4f) else Modifier)
                ) {
                    // Position-qualified key — playlists can have duplicate songs.
                    itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { index, song ->
                        SongRowWithMenu(song = song, viewModel = viewModel,
                            onClick = { viewModel.playQueue(songs, index) }, accent = accent)
                    }
                }
            }
        }
    }
}
