package com.prism.muse.ui.screens.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.playback.RepeatMode
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.components.FloatingIcon
import com.prism.muse.ui.components.GlassSurface
import com.prism.muse.ui.components.WaveBackground
import com.prism.muse.ui.components.gyroTilt
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.seedColor

@Composable
fun NowPlayingScreen(
    viewModel: PlaybackViewModel,
    onCollapse: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val song = state.current
    val accent = seedColor(song.artUrl)
    var queueOpen by remember { mutableStateOf(false) }

    WaveBackground(accent = accent, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingIcon(Icons.Rounded.ExpandMore, "Collapse", onClick = onCollapse, size = 44.dp)
                Text("now playing", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                FloatingIcon(Icons.Rounded.QueueMusic, "Queue", onClick = { queueOpen = !queueOpen }, size = 44.dp)
            }

            Box(Modifier.padding(horizontal = 44.dp).fillMaxWidth()) {
                Artwork(
                    seed = song.artUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .gyroTilt(maxDegrees = 8f)
                )
            }

            Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.displayMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FloatingIcon(
                        icon = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        onClick = {},
                        size = 44.dp,
                        tint = if (song.isFavorite) accent else Color.White
                    )
                }

                AnimatedSeekBar(
                    position = state.positionSec,
                    duration = song.durationSec.toFloat(),
                    accent = accent,
                    onSeek = viewModel::seekTo
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingIcon(
                        Icons.Rounded.Shuffle, "Shuffle", onClick = viewModel::toggleShuffle,
                        size = 40.dp, tint = if (state.shuffle) accent else Color.White
                    )
                    FloatingIcon(Icons.Rounded.SkipPrevious, "Previous", onClick = viewModel::skipPrevious, size = 52.dp)
                    FloatingIcon(
                        icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        onClick = viewModel::togglePlay,
                        size = 76.dp,
                        accent = accent,
                        filled = true
                    )
                    FloatingIcon(Icons.Rounded.SkipNext, "Next", onClick = viewModel::skipNext, size = 52.dp)
                    FloatingIcon(
                        icon = when (state.repeat) {
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        onClick = viewModel::cycleRepeat,
                        size = 40.dp,
                        tint = if (state.repeat != RepeatMode.OFF) accent else Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = queueOpen,
                enter = expandVertically(),
                exit = shrinkVertically(),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                QueueDrawer(viewModel = viewModel, accent = accent)
            }
        }
    }
}

@Composable
private fun AnimatedSeekBar(
    position: Float,
    duration: Float,
    accent: Color,
    onSeek: (Float) -> Unit
) {
    val animatedPosition by animateFloatAsState(position, label = "seek")
    Column(Modifier.padding(top = 20.dp)) {
        Slider(
            value = animatedPosition.coerceIn(0f, duration),
            onValueChange = onSeek,
            valueRange = 0f..(if (duration > 0f) duration else 1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(formatTime(duration), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueDrawer(viewModel: PlaybackViewModel, accent: Color) {
    val state by viewModel.state.collectAsState()
    GlassSurface(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = accent)
                Text(" up next", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(state.queue, key = { it.id }) { song ->
                    val isCurrent = song == state.current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { viewModel.playSong(song) }, onLongClick = { viewModel.removeFromQueue(song) })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(seed = song.artUrl, modifier = Modifier.size(40.dp), cornerRadius = 10)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                song.title,
                                color = if (isCurrent) accent else TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
