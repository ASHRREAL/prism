package com.prism.muse.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.prism.muse.data.model.Song
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.VoidBlack

@Composable
fun SongContextMenu(
    song: Song,
    viewModel: PlaybackViewModel,
    expanded: Boolean,
    onDismiss: () -> Unit,
    isFavorite: Boolean,
    isQueueContext: Boolean = false
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(VoidBlack)
    ) {
        DropdownMenuItem(
            text = { Text("Play", color = TextPrimary) },
            onClick = { viewModel.playSong(song); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text("Play Next", color = TextPrimary) },
            onClick = { viewModel.addNext(song); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text("Add to Queue", color = TextPrimary) },
            onClick = { viewModel.addNext(song); onDismiss() }
        )
        if (isQueueContext) {
            DropdownMenuItem(
                text = { Text("Remove from Queue", color = TextSecondary) },
                onClick = { viewModel.removeFromQueue(song); onDismiss() }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Add to Playlist", color = TextPrimary) },
                onClick = { onDismiss() }
            )
            DropdownMenuItem(
                text = { Text("Song Info", color = TextPrimary) },
                onClick = { onDismiss() }
            )
        }
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Unlike" else "Like", color = TextPrimary) },
            onClick = { viewModel.toggleFavorite(song); onDismiss() }
        )
    }
}

@Composable
fun rememberSongMenuState(): SongMenuState {
    return remember { SongMenuState() }
}

class SongMenuState {
    var expanded by mutableStateOf(false)
    var selectedSong by mutableStateOf<Song?>(null)

    fun show(song: Song) {
        selectedSong = song
        expanded = true
    }

    fun dismiss() {
        expanded = false
        selectedSong = null
    }
}
