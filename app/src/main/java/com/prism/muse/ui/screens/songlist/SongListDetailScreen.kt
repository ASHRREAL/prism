package com.prism.muse.ui.screens.songlist

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.data.model.Song
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.AriaBackground
import com.prism.muse.ui.components.FloatingIcon
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.components.SongRowWithMenu
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

    AriaBackground(tinted = true) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var swipe = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { if (swipe > 180f) onBack(); swipe = 0f },
                        onDragCancel = { swipe = 0f }
                    ) { _, dx -> swipe += dx }
                }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = HubTitle.copy(fontSize = 40.sp, lineHeight = 44.sp),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                FloatingIcon(Icons.Rounded.Close, "Close", onClick = onBack, size = 40.dp, tint = TextTertiary)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextLinkRow(
                    links = listOf("play all", "shuffle"),
                    accent = accent,
                    onClick = { link ->
                        when (link) {
                            "play all" -> {
                                viewModel.playQueue(songs, 0)
                            }
                            "shuffle" -> {
                                viewModel.playQueue(songs.shuffled(), 0)
                            }
                        }
                    }
                )
                Text(
                    "${songs.size} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    softWrap = false,
                    maxLines = 1
                )
            }

            HairlineDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            if (songs.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("no songs found", style = SectionHeader.copy(fontSize = 22.sp), color = TextTertiary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongRowWithMenu(
                            song = song,
                            viewModel = viewModel,
                            onClick = { viewModel.playSong(song) },
                            accent = accent
                        )
                    }
                }
            }
        }
    }
}
