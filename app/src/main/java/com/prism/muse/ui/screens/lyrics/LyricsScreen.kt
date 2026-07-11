package com.prism.muse.ui.screens.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Lyrics
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.PlayerBackdrop
import com.prism.muse.ui.components.SongActions
import com.prism.muse.ui.components.TextLinkRow
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.VoidBlack
import com.prism.muse.ui.theme.TrackedLabel
import kotlinx.coroutines.launch

@Composable
fun LyricsScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    artUrl: String? = null
) {
    val state by viewModel.state.collectAsState()
    val song = state.current
    val accent = LocalPrismAccent.current
    val graph = PrismApp.graph(LocalContext.current)
    val lyricsRepo = graph.lyrics
    val lyricsStyle by graph.prefs.lyricsStyle.collectAsState()
    val scope = rememberCoroutineScope()

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var translating by remember { mutableStateOf(false) }
    var cachedMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(song?.id, reloadKey) {
        if (song == null) return@LaunchedEffect
        lyrics = null
        loading = true
        lyrics = lyricsRepo.lyricsFor(song)
        loading = false
    }

    // 60fps smoothed clock — player position only ticks ~10x/sec and
    // would make the highlight step and jitter otherwise.
    var smoothPosMs by remember { mutableStateOf((state.positionSec * 1000).toLong()) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val deltaMs = if (lastNanos == 0L) 0L else (now - lastNanos) / 1_000_000
            lastNanos = now
            val target = (state.positionSec * 1000).toLong()
            smoothPosMs = when {
                !state.isPlaying -> target
                kotlin.math.abs(smoothPosMs - target) > 1200L -> target // seek/skip
                else -> {
                    val advanced = smoothPosMs + (deltaMs * state.speed).toLong()
                    advanced + ((target - advanced) * 0.08f).toLong()
                }
            }
        }
    }

    val activeIndex by remember(lyrics) {
        derivedStateOf {
            val lines = lyrics?.lines ?: return@derivedStateOf -1
            if (lyrics?.synced != true) return@derivedStateOf -1
            val posMs = smoothPosMs
            // Lines are time-sorted: last line whose timestamp is <= position.
            var best = -1
            for (i in lines.indices) {
                val t = lines[i].timeMs
                if (t < 0) continue
                if (t <= posMs) best = i else break
            }
            best
        }
    }

    val listState = rememberLazyListState()
    // Auto-scroll follows the active line ~1/3 from the top.
    // Stops following when the user scrolls by hand; "sync" re-enables it.
    var followActive by remember { mutableStateOf(true) }
    // Re-follow whenever the song changes (single stable state so the long-lived
    // interaction collector below never writes to a stale one).
    LaunchedEffect(song?.id) { followActive = true }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followActive = false
        }
    }
    LaunchedEffect(activeIndex, followActive) {
        if (followActive && activeIndex >= 0 && !listState.isScrollInProgress) {
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(activeIndex, scrollOffset = -viewport / 3)
        }
    }

    // Pull from very top to close — only fires when list is at scroll origin.
    val onBackUpdated = rememberUpdatedState(onBack)
    val pullToClose = remember(listState) {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                ) {
                    pull += available.y
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull > 180f) onBackUpdated.value()
                pull = 0f
                return Velocity.Zero
            }
        }
    }

    PlayerBackdrop(artUrl = artUrl) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var swipe = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { if (swipe > 120f) onBack(); swipe = 0f },
                        onDragCancel = { swipe = 0f }
                    ) { _, dx -> swipe += dx }
                }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var down = 0f
                        detectVerticalDragGestures(
                            onDragEnd = { if (down > 60f) onBack(); down = 0f },
                            onDragCancel = { down = 0f }
                        ) { _, dy -> down += dy }
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    "‹",
                    style = SectionHeader.copy(fontSize = 30.sp),
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 8.dp)
                )
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        song?.title ?: "lyrics",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "\u2022\u2022\u2022",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { song?.let { SongActions.open(it) } }
                        .padding(8.dp)
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val current = lyrics
                when {
                    loading -> Column(
                        Modifier.fillMaxSize().align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "looking for lyrics…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                    current == null || current.lines.isEmpty() -> Column(
                        Modifier.fillMaxSize().align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "no lyrics found",
                            style = SectionHeader.copy(fontSize = 24.sp),
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                        if (cachedMessage != null) {
                            Text(
                                cachedMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    translating -> Column(
                        Modifier.fillMaxSize().align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "translating…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.nestedScroll(pullToClose),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        itemsIndexed(current.lines, key = { i, _ -> i }) { index, line ->
                            val active = index == activeIndex
                            // Smoothed so scrolling isn't steppy.
                            val distance = if (activeIndex < 0) 0 else kotlin.math.abs(index - activeIndex)

                            // Scale via graphicsLayer instead of font size to avoid relaying-out every frame.
                            val targetScale = when {
                                !active -> 1f
                                lyricsStyle == "spotlight" -> 1.16f
                                lyricsStyle == "fade" -> 1.03f
                                lyricsStyle == "pulse" -> 1.08f
                                lyricsStyle == "karaoke" -> 1f
                                else -> 1f
                            }
                            val scale by animateFloatAsState(targetScale, tween(240), label = "lineScale")
                            val targetAlpha = when {
                                active -> 1f
                                lyricsStyle == "spotlight" -> (0.25f - distance * 0.07f).coerceIn(0.04f, 0.25f)
                                lyricsStyle == "fade" -> 0.38f
                                lyricsStyle == "pulse" -> 0.55f
                                else -> 1f
                            }
                            val alpha by animateFloatAsState(targetAlpha, tween(220), label = "lineAlpha")

                            val baseStyle = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 21.sp,
                                fontWeight = if (active) FontWeight.Medium else FontWeight.Light,
                                lineHeight = 27.sp
                            )
                            val clickMod = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                }
                                .clickable(enabled = current.synced && line.timeMs >= 0) {
                                    viewModel.seekTo(line.timeMs / 1000f)
                                }

                            // Karaoke sweep: time-driven horizontal gradient fill.
                            if (active && current.synced && lyricsStyle == "karaoke") {
                                val startT = line.timeMs
                                val end = current.lines.getOrNull(index + 1)?.timeMs ?: (startT + 3000L)
                                // Fill fraction over line duration.
                                val a = if (end > startT)
                                    ((smoothPosMs - startT).toFloat() / (end - startT)).coerceIn(0f, 1f)
                                else 1f
                                val dim = TextSecondary.copy(alpha = 0.4f)
                                // Narrow blend band rides ahead of the fill for a smooth glide.
                                val edge = (a + 0.06f).coerceAtMost(1f)
                                val brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to TextPrimary,
                                        a to TextPrimary,
                                        edge to dim,
                                        1f to dim
                                    )
                                )
                                Text(line.text, style = baseStyle.merge(TextStyle(brush = brush)), modifier = clickMod)
                            } else {
                                Text(
                                    line.text,
                                    style = baseStyle,
                                    color = if (active) TextPrimary else TextTertiary,
                                    modifier = clickMod
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val styleOrder = listOf("karaoke", "fade", "spotlight", "pulse")
                TextLinkRow(
                    links = listOf(
                        if (lyrics?.synced == true) "sync" else "unsynced",
                        lyricsStyle,
                        "translate",
                        "search"
                    ),
                    activeLink = lyricsStyle,
                    accent = accent,
                    onClick = { link ->
                        when (link) {
                            // Re-follow the song and jump back to the current line.
                            "sync" -> { followActive = true }
                            lyricsStyle -> {
                                val next = styleOrder[(styleOrder.indexOf(lyricsStyle) + 1) % styleOrder.size]
                                graph.prefs.setLyricsStyle(next)
                            }
                            "search" -> {
                                lyricsRepo.clearCache()
                                reloadKey++
                                android.widget.Toast.makeText(context, "searching for lyrics…", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            "offline cache" -> {
                                if (lyrics != null) {
                                    scope.launch {
                                        val song = song ?: return@launch
                                        val uri = PrismApp.graph(context).library.playableUri(song)
                                        if (uri.isNotBlank()) {
                                            PrismApp.graph(context).library.download(song)
                                            cachedMessage = "lyrics cached for offline"
                                        } else {
                                            cachedMessage = "no stream available"
                                        }
                                    }
                                } else {
                                    cachedMessage = "no lyrics to cache"
                                }
                                android.widget.Toast.makeText(context, cachedMessage ?: "", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            "translate" -> {
                                translating = true
                                scope.launch {
                                    val result = lyricsRepo.translateLyrics(song, lyrics)
                                    translating = false
                                    if (result != null) {
                                        lyrics = result
                                    } else {
                                        android.widget.Toast.makeText(context, "translation unavailable", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                )
            }
        }
    }
}
