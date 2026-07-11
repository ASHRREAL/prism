package com.prism.muse.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.prism.muse.data.model.Song
import com.prism.muse.playback.PlaybackViewModel

/** Song row with long-press → SongActions sheet (rendered once at the app root). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRowWithMenu(
    song: Song,
    viewModel: PlaybackViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Color = com.prism.muse.ui.theme.DefaultAccent,
    isQueueContext: Boolean = false
) {
    SongRow(
        song = song,
        onClick = onClick,
        modifier = modifier,
        active = active,
        accent = accent,
        onLongClick = { SongActions.open(song, isQueueContext) }
    )
}
