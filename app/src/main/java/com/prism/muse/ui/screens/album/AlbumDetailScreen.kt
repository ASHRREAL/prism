package com.prism.muse.ui.screens.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Song
import com.prism.muse.ui.components.AriaBackground
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.components.TextLinkRow
import com.prism.muse.ui.components.gyroTilt
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/** Album page: cover beside title block, text actions (play/shuffle/download), and a flat track list. */
@Composable
fun AlbumDetailScreen(
    album: Album,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val graph = PrismApp.graph(LocalContext.current)
    val accent = LocalPrismAccent.current
    val scope = rememberCoroutineScope()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(album.id) {
        songs = runCatching { graph.library.songsForAlbum(album.id) }.getOrDefault(emptyList())
    }

    fun play(startIndex: Int, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val queue = if (shuffled) songs.shuffled() else songs
        graph.player.setQueue(queue, if (shuffled) 0 else startIndex)
    }

    AriaBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                "‹",
                style = SectionHeader.copy(fontSize = 30.sp),
                color = TextSecondary,
                modifier = Modifier
                    .padding(start = 24.dp, top = 10.dp)
                    .clickable(onClick = onBack)
                    .padding(8.dp)
            )

            LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Artwork(
                            seed = album.artUrl,
                            label = album.title,
                            modifier = Modifier.size(150.dp).gyroTilt(maxDegrees = 5f)
                        )
                        Column(Modifier.padding(start = 18.dp).weight(1f)) {
                            Text(
                                album.title,
                                style = SectionHeader.copy(fontSize = 34.sp, lineHeight = 38.sp),
                                color = TextPrimary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "by ${album.artist}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                listOfNotNull(
                                    album.year.takeIf { it > 0 }?.toString(),
                                    album.genre.lowercase().ifBlank { null }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }

                    Row(
                        Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        TextLinkRow(
                            links = listOf("play", "shuffle", downloadStatus ?: "download"),
                            activeLink = "play",
                            accent = accent,
                            onClick = { link ->
                                when (link) {
                                    "play" -> play(0)
                                    "shuffle" -> play(0, shuffled = true)
                                    else -> {
                                        downloadStatus = "downloading…"
                                        scope.launch {
                                            var ok = true
                                            songs.forEach { song -> ok = graph.library.download(song) && ok }
                                            downloadStatus = if (ok) "downloaded" else "download failed"
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { index, song ->
                    TrackRow(index + 1, song, onClick = { play(index) })
                }
            }
        }
    }
}

@Composable
private fun TrackRow(number: Int, song: Song, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "%02d".format(number),
                style = MaterialTheme.typography.bodyLarge,
                color = TextTertiary,
                modifier = Modifier.width(40.dp)
            )
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Text(formatDuration(song.durationSec), color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
        }
        HairlineDivider(Modifier.padding(horizontal = 24.dp))
    }
}

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
