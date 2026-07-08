package com.prism.muse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.data.model.Playlist
import com.prism.muse.data.model.Song
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(140.dp).clickable(onClick = onClick)) {
        Artwork(
            seed = album.artUrl,
            label = album.title,
            overrideColor = com.prism.muse.ui.theme.colorFromHex(album.dominantColorHex),
            modifier = Modifier.aspectRatio(1f)
        )
        Text(
            album.title,
            style = MaterialTheme.typography.titleMedium,
            color = com.prism.muse.ui.theme.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ArtistTile(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(108.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(seed = artist.imageUrl, label = artist.name, modifier = Modifier.size(96.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = com.prism.muse.ui.theme.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(150.dp).clickable(onClick = onClick)) {
        Box(Modifier.aspectRatio(1f)) {
            Artwork(seed = playlist.coverUrls.firstOrNull() ?: playlist.id, modifier = Modifier.aspectRatio(1f), icon = false)
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = com.prism.muse.ui.theme.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("${playlist.trackCount} tracks", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
fun SongCard(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(140.dp).clickable(onClick = onClick)) {
        Box(Modifier.aspectRatio(1f)) {
            Artwork(seed = song.artUrl, label = song.title, modifier = Modifier.aspectRatio(1f))
            if (song.isFavorite) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = "Favorite",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
        }
        Text(
            song.title,
            style = MaterialTheme.typography.titleMedium,
            color = com.prism.muse.ui.theme.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GenreChip(name: String, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        shape = RoundedCornerShape(0.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = com.prism.muse.ui.theme.TextPrimary)
            Text("$count albums", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        }
    }
}
