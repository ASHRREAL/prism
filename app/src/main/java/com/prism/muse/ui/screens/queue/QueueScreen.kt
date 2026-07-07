package com.prism.muse.ui.screens.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Song
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.components.PlayerBackdrop
import com.prism.muse.ui.components.SongActions
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.TrackedLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(viewModel: PlaybackViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val accent = LocalPrismAccent.current
    val currentId = state.current?.id
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = PrismApp.graph(ctx).library
    val density = LocalDensity.current

    val queueList = remember(state.queue) {
        mutableStateListOf<Song>().also { it.addAll(state.queue.toList()) }
    }
    val safeCurrentIndex = state.currentIndex.coerceIn(0, (queueList.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var dragStartIdx by remember { mutableIntStateOf(-1) }
    val rowHeightPx = with(density) { 72.dp.toPx() }

    PlayerBackdrop(artUrl = state.current?.artUrl) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .pointerInput(Unit) {
                var down = 0f
                detectVerticalDragGestures(
                    onDragEnd = { if (down > 60f) onBack(); down = 0f },
                    onDragCancel = { down = 0f }
                ) { _, dy -> down += dy }
            }
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Now playing", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Text("now playing", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("SAVE", style = TrackedLabel.copy(fontSize = 11.sp, letterSpacing = 1.sp), color = TextSecondary,
                    modifier = Modifier.clickable {
                        if (state.queue.isNotEmpty()) scope.launch {
                            library.createPlaylist("Queue " + nowStamp(), state.queue)
                            android.widget.Toast.makeText(ctx, "playlist saved", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
            }
            Text("up next", style = HubTitle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = TextPrimary,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 6.dp))

            LazyColumn(state = listState, modifier = Modifier.weight(1f).pointerInput(Unit) {
                var down = 0f
                detectVerticalDragGestures(
                    onDragEnd = { if (down > 80f) onBack(); down = 0f },
                    onDragCancel = { down = 0f }
                ) { _, dy -> down += dy }
            }, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 12.dp)) {
                itemsIndexed(queueList.drop(safeCurrentIndex), key = { i, s -> "${i + safeCurrentIndex}:${s.id}" }) { idxShown, song ->
                    val realIdx = idxShown + safeCurrentIndex
                    val isCurrent = song.id == currentId
                    val isDragging = draggedId == song.id

                    val dragOff = if (isDragging) {
                        val cur = queueList.indexOfFirst { it.id == song.id }
                        if (cur >= 0) totalDragY - (cur - dragStartIdx) * rowHeightPx else 0f
                    } else 0f

                    if (isCurrent) {
                        QueueRow(song, true, accent, onClick = { viewModel.playAt(realIdx) })
                    } else {
                        val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                            if (v == SwipeToDismissBoxValue.EndToStart) { viewModel.removeFromQueue(song); queueList.remove(song); true } else false
                        })
                        SwipeToDismissBox(state = dismiss, enableDismissFromStartToEnd = false,
                            backgroundContent = { Box(Modifier.fillMaxSize().background(Color(0xFFD32F2F))) }
                        ) {
                            Box {
                                QueueRow(song, false, accent, onClick = { viewModel.playAt(realIdx) },
                                    isDragging = isDragging, dragOffset = dragOff,
                                    onDragStart = { draggedId = song.id; dragStartIdx = queueList.indexOfFirst { it.id == song.id }; totalDragY = 0f },
                                    onDrag = { dy ->
                                        totalDragY += dy
                                        val ci = queueList.indexOfFirst { it.id == song.id }
                                        if (ci >= 0) {
                                            val off = totalDragY - (ci - dragStartIdx) * rowHeightPx
                                            if (kotlin.math.abs(off) > rowHeightPx * 0.5f) {
                                                val dir = if (off > 0) 1 else -1
                                                val target = (ci + dir).coerceIn(0, queueList.lastIndex)
                                                if (target != ci && target != state.currentIndex) {
                                                    queueList[ci] = queueList[target].also { queueList[target] = queueList[ci] }
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = { draggedId = null; totalDragY = 0f })
                            }
                        }
                    }
                }
                item {
                    Text("swipe left to remove · drag ⋮⋮ to reorder",
                        style = TrackedLabel.copy(fontSize = 11.sp, letterSpacing = 2.sp), color = TextTertiary,
                        modifier = Modifier.padding(top = 18.dp))
                }
            }

            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Text("shuffle", style = SectionHeader.copy(fontSize = 18.sp), color = TextPrimary,
                    modifier = Modifier.clickable { viewModel.shuffleQueue() })
                Text("clear", style = SectionHeader.copy(fontSize = 18.sp), color = TextSecondary,
                    modifier = Modifier.clickable { state.current?.let { viewModel.playQueue(listOf(it), 0) } })
            }
        }
    }
}

private fun nowStamp(): String {
    val c = java.util.Calendar.getInstance()
    return "%02d/%02d %02d:%02d".format(c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    song: Song, isCurrent: Boolean, accent: Color, onClick: () -> Unit,
    isDragging: Boolean = false, dragOffset: Float = 0f,
    onDragStart: (() -> Unit)? = null, onDrag: ((Float) -> Unit)? = null, onDragEnd: (() -> Unit)? = null
) {
    Column {
        Row(
            Modifier.fillMaxWidth().graphicsLayer {
                translationY = dragOffset
                scaleX = if (isDragging) 1.04f else 1f
                scaleY = if (isDragging) 1.04f else 1f
            }.combinedClickable(onClick = onClick, onLongClick = { SongActions.open(song, queueContext = true) }).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) Text("▶", color = accent, fontSize = 12.sp)
            }
            Artwork(seed = song.artUrl, modifier = Modifier.padding(start = 4.dp).size(48.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("⋮⋮", color = TextTertiary, fontSize = 16.sp,
                modifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { onDragStart?.invoke() },
                        onDragEnd = { onDragEnd?.invoke() },
                        onDragCancel = { onDragEnd?.invoke() }
                    ) { change, dy -> change.consume(); onDrag?.invoke(dy) }
                })
        }
        HairlineDivider()
    }
}
