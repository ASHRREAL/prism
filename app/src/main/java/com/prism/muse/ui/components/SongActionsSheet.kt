package com.prism.muse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Song
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.VoidBlack
import kotlinx.coroutines.launch

/**
 * App-wide "song actions" controller. Any screen calls [SongActions.open];
 * the single [SongActionsHost] at the app root renders the sheet.
 */
object SongActions {
    var song by mutableStateOf<Song?>(null)
        private set
    var queueContext by mutableStateOf(false)
        private set

    fun open(song: Song, queueContext: Boolean = false) {
        this.song = song
        this.queueContext = queueContext
    }

    fun close() {
        song = null
    }
}

@Composable
fun SongActionsHost(viewModel: PlaybackViewModel) {
    val visible = SongActions.song != null
    // Keep the last song around during the exit animation.
    var shown by remember { mutableStateOf<Song?>(null) }
    if (SongActions.song != null) shown = SongActions.song

    val graph = PrismApp.graph(LocalContext.current)
    val scope = rememberCoroutineScope()
    val favorites by graph.library.favorites.collectAsState()
    val playlists by graph.library.playlists.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // Scrim
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapClose() }
            )
        }
        // Panel
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val song = shown
            if (song != null) {
                val isFav = favorites.any { it.id == song.id }
                var picking by remember(song.id) { mutableStateOf(false) }
                var newName by remember(song.id) { mutableStateOf("") }
                var downloaded by remember(song.id) { mutableStateOf(graph.library.isDownloaded(song.id)) }
                var downloading by remember(song.id) { mutableStateOf(false) }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(VoidBlack)
                        .navigationBarsPadding()
                        .pointerInput(Unit) { /* consume taps so the scrim doesn't dismiss */ detectTapConsume() }
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HairlineDivider(Modifier.padding(vertical = 12.dp))

                    if (!picking) {
                        ActionItem("play next") { viewModel.addNext(song); SongActions.close() }
                        ActionItem("add to queue") { viewModel.addToQueue(song); SongActions.close() }
                        ActionItem(if (isFav) "unlike" else "like") { viewModel.toggleFavorite(song); SongActions.close() }
                        ActionItem("add to playlist") { picking = true }
                        val downloadLabel = when {
                            downloading -> "downloading…"
                            downloaded -> "remove download"
                            else -> "download"
                        }
                        ActionItem(downloadLabel) {
                            if (downloading) return@ActionItem
                            if (downloaded) {
                                graph.library.deleteDownload(song.id)
                                downloaded = false
                            } else if (graph.library.playableUri(song).isNotBlank() || song.streamUrl.isNotBlank()) {
                                downloading = true
                                scope.launch {
                                    val ok = graph.library.download(song)
                                    downloading = false
                                    downloaded = ok
                                }
                            }
                        }
                        if (SongActions.queueContext) {
                            ActionItem("remove from queue") { viewModel.removeFromQueue(song); SongActions.close() }
                        }
                    } else {
                        Text("add to playlist", style = MaterialTheme.typography.bodyMedium, color = TextTertiary, modifier = Modifier.padding(bottom = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = newName,
                                onValueChange = { newName = it },
                                placeholder = { Text("new playlist…", color = TextTertiary) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .heightIn(min = 52.dp)
                                    .weight(1f),
                                keyboardActions = KeyboardActions()
                            )
                            Text(
                                "create",
                                style = SectionHeader.copy(fontSize = 18.sp),
                                color = if (newName.isBlank()) TextTertiary else TextPrimary,
                                modifier = Modifier
                                    .clickable(enabled = newName.isNotBlank()) {
                                        scope.launch { graph.library.createPlaylist(newName.trim(), listOf(song)) }
                                        SongActions.close()
                                    }
                            )
                        }
                        LazyColumn(Modifier.heightIn(max = 280.dp).padding(top = 6.dp)) {
                            items(playlists, key = { it.id }) { pl ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch { graph.library.addToPlaylist(pl.id, song) }
                                            SongActions.close()
                                        }
                                        .padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(pl.name, style = SectionHeader.copy(fontSize = 18.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${pl.trackCount}", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = SectionHeader.copy(fontSize = 20.sp),
        color = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapClose() {
    detectTapGestures { SongActions.close() }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapConsume() {
    detectTapGestures { /* swallow taps on the panel */ }
}
