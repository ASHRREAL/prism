package com.prism.muse.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.prism.muse.data.model.Song
import com.prism.muse.playback.PlaybackViewModel

/**
 * A flat song row whose long-press opens the app-level [SongActions] sheet
 * (play next / add to queue / add to playlist / like / remove). The sheet is
 * rendered once at the app root, so there's no fragile per-row popup.
 */
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
