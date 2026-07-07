package com.prism.muse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.muse.data.model.Song
import com.prism.muse.ui.theme.Hairline
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.VoidBlack
import kotlin.math.abs

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(VoidBlack)
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .pointerInput(Unit) {
                    var up = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { if (up < -60f) onExpand(); up = 0f },
                        onDragCancel = { up = 0f }
                    ) { _, dy -> up += dy }
                }
                .pointerInput(Unit) {
                    var horiz = 0f
                    dragOffset = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(horiz) > 120f) {
                                if (horiz > 0) onSkipPrevious() else onSkipNext()
                            }
                            horiz = 0f; dragOffset = 0f
                        },
                        onDragCancel = { horiz = 0f; dragOffset = 0f }
                    ) { _, dx ->
                        horiz += dx
                        dragOffset = horiz / 3f
                    }
                }
                .graphicsLayer { translationX = dragOffset }
                .clickable(onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(seed = song.artUrl, modifier = Modifier.size(44.dp).aspectRatio(1f))
            androidx.compose.foundation.layout.Column(
                Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                Text(song.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FloatingIcon(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play/Pause",
                onClick = onTogglePlay,
                size = 36.dp,
                accent = accent,
                tint = Color.White,
                filled = true
            )
            FloatingIcon(Icons.Rounded.SkipNext, "Next", onClick = onSkipNext, size = 36.dp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
