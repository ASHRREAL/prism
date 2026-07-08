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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
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
        val q = state.queue.toList()
        mutableStateListOf<Song>().also { it.addAll(q) }
    }
    // Stable key per queue slot (independent of position) so the LazyColumn
    // composable follows the row through reorderings instead of being disposed
    // — disposing mid-drag cancels the gesture coroutine, which is why a row
    // "got stuck" after one swap.
    val queueKeys = remember(state.queue) {
        var n = 0
        mutableStateListOf<Int>().also { list -> repeat(state.queue.size) { list.add(n++) } }
    }
    val safeCurrentIndex = state.currentIndex.coerceIn(0, (queueList.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedSlotKey by remember { mutableIntStateOf(-1) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { 72.dp.toPx() }

    // The list now shows the whole queue (no `drop(currentIndex)` shortcut), so
    // jump straight to the playing row on first show.
    androidx.compose.runtime.LaunchedEffect(safeCurrentIndex, queueList.size) {
        if (!listState.isScrollInProgress && queueList.isNotEmpty()) {
            runCatching { listState.scrollToItem(safeCurrentIndex) }
        }
    }

    // Pull-down-to-close: only when the list is already scrolled to the very top,
    // a downward over-scroll that isn't consumed by the list accumulates, and a
    // release past the threshold drops back to Now Playing. Uses nested scroll so
    // it never fights the list's own scrolling mid-list.
    val onBackUpdated = rememberUpdatedState(onBack)
    val pullToClose = remember(listState) {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f &&
                    draggedId == null &&
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

    PlayerBackdrop(artUrl = state.current?.artUrl) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .pointerInput(Unit) {
                    var down = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { if (down > 60f) onBack(); down = 0f },
                        onDragCancel = { down = 0f }
                    ) { _, dy -> down += dy }
                }
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Now playing", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Text("now playing", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("SAVE", style = TrackedLabel.copy(fontSize = 11.sp, letterSpacing = 1.sp), color = TextSecondary,
                    modifier = Modifier.clickable {
                        val q = state.queue
                        if (q.isNotEmpty()) scope.launch {
                            library.createPlaylist("Queue " + nowStamp(), q)
                            android.widget.Toast.makeText(ctx, "playlist saved", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Text("up next", style = HubTitle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = TextPrimary,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).nestedScroll(pullToClose),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 12.dp)
            ) {
                // Show every queue slot — already-played rows are greyed out via [played]
// instead of being dropped. Dropping by currentIndex (the old behavior) hid
// arbitrary subsets under shuffle because ExoPlayer's media-item index jumps
// along its opaque shuffle order, not sequentially.
itemsIndexed(queueList, key = { i, _ -> queueKeys[i] }) { realIdx, song ->
                    val isCurrent = realIdx == safeCurrentIndex
                    val isDragging = queueKeys[realIdx] == draggedSlotKey && !isCurrent
                    // Positional "previous" — songs earlier in queue than the
                    // one now playing read as already-heard. No history tracking;
                    // this stays intuitive under shuffle (the queue UI keeps its
                    // slot order, pre-current = before now-playing).
                    val played = realIdx < safeCurrentIndex

                    if (isCurrent) {
                        QueueRow(
                            song = song, isCurrent = true, accent = accent,
                            played = false, onClick = { viewModel.playAt(realIdx) }
                        )
                    } else {
                        val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                            if (v == SwipeToDismissBoxValue.EndToStart) {
                                if (realIdx >= 0 && realIdx < queueKeys.size) queueKeys.removeAt(realIdx)
                                viewModel.removeFromQueue(song); queueList.removeAt(realIdx); true
                            } else false
                        })
                        SwipeToDismissBox(state = dismiss, enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val bg by animateColorAsState(
                                    if (dismiss.targetValue == SwipeToDismissBoxValue.EndToStart) Color(0xFFD32F2F) else Color.Transparent,
                                    label = "swipeBg"
                                )
                                Box(Modifier.fillMaxSize().background(
                                    Brush.horizontalGradient(listOf(Color.Transparent, bg))
                                ))
                            }
                        ) {
                            Box {
                                QueueRow(
                                    song = song, isCurrent = false, accent = accent,
                                    played = played, onClick = { viewModel.playAt(realIdx) },
                                    isDragging = isDragging,
                                    dragOffset = if (isDragging) dragAccumY else 0f,
                                    onDragStart = {
                                        draggedId = song.id
                                        draggedSlotKey = queueKeys[realIdx]
                                        dragAccumY = 0f
                                    },
                                    onDrag = { dy ->
                                        dragAccumY += dy
                                        // The pointer-input gesture scope captures this lambda
                                        // once, so realIdx / queueKeys[realIdx] would be stale
                                        // after a swap. Re-resolve the dragged slot's position
                                        // by its stable key every event — survives reorderings
                                        // and is unique even with duplicate songs.
                                        val i = queueKeys.indexOf(draggedSlotKey)
                                        if (i in queueList.indices && i != safeCurrentIndex) {
                                            if (kotlin.math.abs(dragAccumY) > rowHeightPx * 0.5f) {
                                                val dir = if (dragAccumY > 0) 1 else -1
                                                val target = (i + dir).coerceIn(0, queueList.lastIndex)
                                                if (target != i && target != safeCurrentIndex) {
                                                    queueList[i] = queueList[target].also { queueList[target] = queueList[i] }
                                                    queueKeys[i] = queueKeys[target].also { queueKeys[target] = queueKeys[i] }
                                                    dragAccumY -= dir * rowHeightPx
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = { draggedId = null; dragAccumY = 0f }
                                )
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

            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Text("shuffle", style = SectionHeader.copy(fontSize = 18.sp), color = TextPrimary,
                    modifier = Modifier.clickable { viewModel.shuffleQueue() })
                Text("clear", style = SectionHeader.copy(fontSize = 18.sp), color = TextSecondary,
                    modifier = Modifier.clickable { viewModel.clearUpcoming() })
            }
        }
    }
}

private fun nowStamp(): String {
    val c = java.util.Calendar.getInstance()
    return "%02d/%02d %02d:%02d".format(c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH),
        c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    song: Song, isCurrent: Boolean, accent: Color, onClick: () -> Unit,
    played: Boolean = false,
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    // Played-but-not-current rows fade back so the upcoming list still reads as
    // "what's next", with history dimmed behind it rather than hidden.
    val titleColor = if (isCurrent) TextPrimary else if (played) TextTertiary else TextPrimary
    val artistColor = if (isCurrent) TextSecondary else if (played) TextTertiary.copy(alpha = 0.6f) else TextSecondary
    Column {
        Row(
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    translationY = dragOffset
                    scaleX = if (isDragging) 1.04f else 1f
                    scaleY = if (isDragging) 1.04f else 1f
                    alpha = if (played && !isCurrent) 0.55f else 1f
                }
                .combinedClickable(onClick = onClick, onLongClick = { SongActions.open(song, queueContext = true) })
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) Text("▶", color = accent, fontSize = 12.sp)
            }
            Artwork(seed = song.artUrl, label = song.title, modifier = Modifier.padding(start = 4.dp).size(48.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium,
                    color = artistColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("⋮⋮", color = TextTertiary, fontSize = 16.sp,
                modifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { onDragStart?.invoke() },
                        onDragEnd = { onDragEnd?.invoke() },
                        onDragCancel = { onDragEnd?.invoke() }
                    ) { change, dy ->
                        change.consume()
                        onDrag?.invoke(dy)
                    }
                }
            )
        }
        HairlineDivider()
    }
}
