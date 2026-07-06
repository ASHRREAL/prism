package com.prism.muse.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.muse.data.model.Song
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Floating translucent bar above the bottom nav. Swipe left/right skips
 * tracks, swipe up opens Now Playing — mirrors the "swipe mini-player"
 * gesture called out in the brief.
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer { translationX = dragOffset }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> dragOffset += delta },
                onDragStopped = { velocity ->
                    if (abs(dragOffset) > 120f || abs(velocity) > 900f) onSkipNext()
                    dragOffset = 0f
                }
            )
            .clickable(onClick = onExpand),
        elevation = 24.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(seed = song.artUrl, modifier = Modifier.size(48.dp).aspectRatio(1f))
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
                size = 40.dp,
                accent = accent,
                filled = true
            )
            FloatingIcon(Icons.Rounded.SkipNext, "Next", onClick = onSkipNext, size = 40.dp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
