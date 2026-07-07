package com.prism.muse.ui.screens.nowplaying

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Song
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.playback.RepeatMode
import com.prism.muse.ui.components.AriaSeekBar
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.components.FloatingIcon
import com.prism.muse.ui.components.SongActions
import com.prism.muse.ui.components.gyroTilt
import com.prism.muse.ui.components.seedColor
import com.prism.muse.ui.components.WaveBackground
import com.prism.muse.ui.theme.InkNavy
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.ProvideAccent
import com.prism.muse.ui.theme.MetroFontFamily
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.TrackedLabel
import com.prism.muse.ui.theme.VoidBlack

/**
 * Now Playing — a two-pane Metro panorama exactly like the design source: the
 * current pane (hero art + progress + transport + lyrics/equalizer links) sits
 * flat and full; the "up next" pane peeks at the right edge, receded in 3D
 * (perspective 900px, rotateY 32°, scale 0.82, opacity 0.4). Blurred cover-art
 * backdrop under a darkening gradient.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlaybackViewModel,
    onCollapse: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenVis: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val song = state.current
    val themeAccent = LocalPrismAccent.current
    val graph = PrismApp.graph(LocalContext.current)
    val depthEffect by graph.prefs.depthEffect.collectAsState()
    val npBg by graph.prefs.npBackground.collectAsState()
    val favorites by graph.library.favorites.collectAsState()
    val upNext = if (song != null) state.queue.drop(state.currentIndex + 1) else emptyList()
    var dragAccum by remember { mutableStateOf(0f) }
    val accent = LocalPrismAccent.current

    Box(Modifier.fillMaxSize().background(VoidBlack)) {
        // Background based on preference
        when (npBg) {
            "gradient" -> {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.3f),
                                InkNavy.copy(alpha = 0.7f),
                                VoidBlack
                            ),
                            center = Offset(200f, 100f),
                            radius = 1600f
                        )
                    )
                )
            }
            "waves" -> {
                WaveBackground(accent = accent, drift = 0f) {}
            }
            "solid" -> {
                // Just void black - gradient overlay handles it
            }
            else -> {
                // Blurred art (default)
                if (song != null && song.artUrl.startsWith("http")) {
                    AsyncImage(
                        model = song.artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(30.dp).graphicsLayer { alpha = 0.7f }
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.radialGradient(
                                colors = listOf(InkNavy.copy(alpha = 0.7f), Color.Transparent),
                                center = Offset(280f, 180f),
                                radius = 1400f
                            )
                        )
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.65f to Color.Black.copy(alpha = 0.85f),
                    1f to Color.Black
                )
            )
        )

        if (song == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("nothing playing", style = SectionHeader, color = TextTertiary)
            }
            return@Box
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            when {
                                dragAccum > 140f -> onCollapse()   // swipe down: close
                                dragAccum < -140f -> onOpenQueue()  // swipe up: open queue
                            }
                            dragAccum = 0f
                        }
                    ) { _, dy -> dragAccum += dy }
                }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("now playing", style = TrackedLabel, color = TextTertiary, modifier = Modifier.weight(1f))
                val isFav = song.let { s -> favorites.any { it.id == s.id } }
                FloatingIcon(
                    icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like",
                    onClick = { viewModel.toggleFavorite(song) },
                    size = 38.dp,
                    tint = if (isFav) accent else Color.White.copy(alpha = 0.85f)
                )
                FloatingIcon(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    onClick = { SongActions.open(song) },
                    size = 38.dp,
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
            Column(Modifier.padding(start = 22.dp, top = 8.dp, end = 22.dp)) {
                Text(
                    song.title,
                    style = SectionHeader.copy(fontSize = 26.sp, lineHeight = 30.sp),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "by ${song.artist}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Panorama strip: current pane full + up-next pane receded at the right.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
                // Guard against a transient narrow measure producing a negative
                // width (which crashes layout).
                val leftPaneWidth = (maxWidth - 92.dp).coerceAtLeast(160.dp)
                Row(Modifier.fillMaxSize().padding(start = 22.dp)) {

                    // CURRENT PANE — cluster vertically centered so the pane
                    // isn't top-heavy with a big empty gap at the bottom.
                    Column(Modifier.width(leftPaneWidth).fillMaxHeight().padding(end = 18.dp)) {
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(216.dp)
                                    .then(if (depthEffect) Modifier.gyroTilt(maxDegrees = 7f) else Modifier)
                                    .shadow(30.dp, RectangleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            ) {
                                Artwork(seed = song.artUrl, modifier = Modifier.fillMaxSize())
                            }
                        }

                        AriaSeekBar(
                            position = state.positionSec,
                            duration = (song.durationSec.takeIf { it > 0 } ?: 1).toFloat(),
                            onSeek = viewModel::seekTo,
                            modifier = Modifier.padding(top = 20.dp)
                        )

                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingIcon(
                                Icons.Rounded.Shuffle, "Shuffle", onClick = viewModel::toggleShuffle,
                                size = 30.dp, tint = if (state.shuffle) accent else Color.White.copy(alpha = 0.75f)
                            )
                            FloatingIcon(Icons.Rounded.SkipPrevious, "Previous", onClick = viewModel::skipPrevious, size = 34.dp)
                            FloatingIcon(
                                icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                onClick = viewModel::togglePlay,
                                size = 56.dp,
                                filled = true
                            )
                            FloatingIcon(Icons.Rounded.SkipNext, "Next", onClick = viewModel::skipNext, size = 34.dp)
                            FloatingIcon(
                                icon = when (state.repeat) {
                                    RepeatMode.ALL -> Icons.Rounded.Repeat
                                    RepeatMode.ONCE -> Icons.Rounded.RepeatOne
                                    else -> Icons.Rounded.Repeat
                                },
                                contentDescription = "Repeat",
                                onClick = viewModel::cycleRepeat,
                                size = 30.dp,
                                tint = if (state.repeat != RepeatMode.OFF) accent else Color.White.copy(alpha = 0.75f)
                            )
                        }

                        Row(
                            Modifier.padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            Text(
                                "lyrics",
                                style = SectionHeader.copy(fontSize = 18.sp),
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.clickable(onClick = onOpenLyrics)
                            )
                            Text(
                                "equalizer",
                                style = SectionHeader.copy(fontSize = 18.sp),
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.clickable(onClick = onOpenEq)
                            )
                            Text(
                                "visualizer",
                                style = SectionHeader.copy(fontSize = 18.sp),
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.clickable(onClick = onOpenVis)
                            )
                        }

                        Spacer(Modifier.weight(1f))
                    }

                    // UP NEXT PANE — receded in 3D
                    Column(
                        Modifier
                            .width(150.dp)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                cameraDistance = 16f * density
                                rotationY = if (depthEffect) 32f else 0f
                                scaleX = if (depthEffect) 0.82f else 1f
                                scaleY = if (depthEffect) 0.82f else 1f
                                alpha = if (depthEffect) 0.4f else 0.9f
                            }
                            .clickable(onClick = onOpenQueue)
                    ) {
                        // Nudge the receded pane down so "up next" sits roughly
                        // level with the centered hero art, not up at the top.
                        Spacer(Modifier.size(72.dp))
                        Text(
                            "up next",
                            style = SectionHeader.copy(fontSize = 24.sp),
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                        if (upNext.isEmpty()) {
                            Text("—", style = SectionHeader.copy(fontSize = 20.sp), color = TextTertiary)
                        }
                        upNext.take(4).forEach { s -> UpNextRow(s) }
                    }
                }
            }

            // Queue affordance
            Column(
                Modifier.fillMaxWidth().clickable(onClick = onOpenQueue).padding(top = 4.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Queue",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "QUEUE",
                    style = TrackedLabel.copy(fontSize = 10.sp, letterSpacing = 2.sp),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun UpNextRow(song: Song) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(seed = song.artUrl, modifier = Modifier.size(40.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                song.title,
                style = androidx.compose.ui.text.TextStyle(fontFamily = MetroFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = androidx.compose.ui.text.TextStyle(fontFamily = MetroFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
