package com.prism.muse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.muse.data.model.Song
import com.prism.muse.ui.theme.Hairline
import com.prism.muse.ui.theme.InkNavy
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.VoidBlack

/**
 * aria screen ground: near-black, with an optional whisper of blue-black
 * light from the top corner (the Now Playing treatment in the mockup).
 */
@Composable
fun AriaBackground(
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (tinted) Brush.linearGradient(
                    colors = listOf(InkNavy, VoidBlack, VoidBlack),
                    start = Offset(0f, 0f),
                    end = Offset(1200f, 2000f)
                ) else Brush.verticalGradient(listOf(VoidBlack, VoidBlack))
            )
    ) {
        content()
    }
}

/** Hairline separator used between flat list rows. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}

/** Panorama page indicator dots, with a slanted parallax look. */
@Composable
fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(6.dp, 4.dp)
                    .graphicsLayer {
                        rotationZ = -15f
                    }
                    .background(
                        if (i == current) Color.White else Color.White.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/**
 * Flat aria song row: square art, condensed title over artist, hairline below.
 * The artist line turns accent-colored when [active] (the playing track).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Color = com.prism.muse.ui.theme.DefaultAccent,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (onLongClick != null) Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    else Modifier.clickable(onClick = onClick)
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(seed = song.artUrl, label = song.title, modifier = Modifier.size(56.dp))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) accent else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
        }
        HairlineDivider()
    }
}

/**
 * The lowercase text-link row from the mockup ("lyrics  eq  cast") — active
 * link in accent, the rest dim.
 */
@Composable
fun TextLinkRow(
    links: List<String>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    activeLink: String? = null,
    accent: Color = com.prism.muse.ui.theme.DefaultAccent
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        links.forEach { link ->
            Text(
                link,
                style = MaterialTheme.typography.bodyLarge,
                color = if (link == activeLink) accent else TextTertiary,
                modifier = Modifier.clickable { onClick(link) }
            )
        }
    }
}

/**
 * aria seekbar: hairline track, accent-less white progress, round thumb,
 * elapsed time left and negative remaining right like the mockup. Drag to
 * scrub, tap to jump.
 */
@Composable
fun AriaSeekBar(
    position: Float,
    duration: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    progressColor: Color = Color.White
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val safeDuration = duration.coerceAtLeast(1f)
    val fraction = dragFraction ?: (position / safeDuration).coerceIn(0f, 1f)

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatSeekTime(dragFraction?.times(safeDuration) ?: position),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
                .height(30.dp)
                .pointerInput(safeDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { onSeek(it * safeDuration) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    ) { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f) * safeDuration)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2
                val stroke = 2.dp.toPx()
                val thumbX = size.width * fraction
                drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Butt)
                drawLine(progressColor, Offset(0f, y), Offset(thumbX, y), stroke, StrokeCap.Butt)
                drawCircle(Color.White, 7.dp.toPx(), Offset(thumbX, y))
            }
        }
        Text(
            "-" + formatSeekTime(safeDuration - (dragFraction?.times(safeDuration) ?: position)),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatSeekTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
