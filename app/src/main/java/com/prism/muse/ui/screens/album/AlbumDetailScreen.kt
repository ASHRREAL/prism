package com.prism.muse.ui.screens.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.muse.data.mock.MockLibrary
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Song
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.components.FloatingIcon
import com.prism.muse.ui.components.GlassSurface
import com.prism.muse.ui.components.WaveBackground
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.colorFromHex

@Composable
fun AlbumDetailScreen(
    album: Album,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    val accent = colorFromHex(album.dominantColorHex)
    val songs = remember(album.id) { MockLibrary.songsForAlbum(album.id) }

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val artScale by animateFloatAsState(if (revealed) 1f else 0.6f, tween(520), label = "artZoom")
    val artAlpha by animateFloatAsState(if (revealed) 1f else 0f, tween(420), label = "artFade")

    WaveBackground(accent = accent, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingIcon(Icons.Rounded.ArrowBack, "Back", onClick = onBack, size = 44.dp)
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Artwork(
                            seed = album.artUrl,
                            overrideColor = accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = artScale; scaleY = artScale; alpha = artAlpha
                                    shadowElevation = 40f
                                }
                        )
                        Text(
                            album.title,
                            style = MaterialTheme.typography.displayMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 20.dp)
                        )
                        Text(
                            "${album.artist} • ${album.year} • ${album.genre}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )

                        Row(
                            Modifier.padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            FloatingIcon(Icons.Rounded.PlayArrow, "Play", onClick = {}, accent = accent, filled = true, size = 56.dp)
                            FloatingIcon(Icons.Rounded.Shuffle, "Shuffle", onClick = {}, size = 56.dp)
                        }
                    }
                }

                itemsIndexed(songs) { index, song ->
                    AnimatedVisibility(
                        visible = revealed,
                        enter = fadeIn(tween(300, delayMillis = 60 * index)) +
                            slideInVertically(tween(340, delayMillis = 60 * index)) { it / 3 }
                    ) {
                        TrackRow(index + 1, song, onClick = { onSongClick(song) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(number: Int, song: Song, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        elevation = 4.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.padding(end = 16.dp)) {
                Text("$number", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(song.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDuration(song.durationSec), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
