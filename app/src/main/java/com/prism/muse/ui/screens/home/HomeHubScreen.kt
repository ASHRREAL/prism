package com.prism.muse.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.prism.muse.data.mock.MockLibrary
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.data.model.Playlist
import com.prism.muse.data.model.Song
import com.prism.muse.ui.components.AlbumCard
import com.prism.muse.ui.components.ArtistCircle
import com.prism.muse.ui.components.GenreChip
import com.prism.muse.ui.components.PlaylistCard
import com.prism.muse.ui.components.SongCard
import com.prism.muse.ui.components.WaveBackground
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent

/**
 * Windows Phone "Hub" home: one continuous vertical scroll where each section's
 * row cascades sideways at its own rate as you scroll — the diagonal parallax
 * feel of the original Music+Videos hub, rebuilt with translucent glass rows.
 */
@Composable
fun HomeHubScreen(
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSongClick: (Song) -> Unit,
    onGenreClick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val accent = LocalPrismAccent.current
    val listState = rememberLazyListState()

    // A single continuous scroll signal used to stagger each section horizontally.
    val scrollSignal by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex * 1000f + listState.firstVisibleItemScrollOffset
        }
    }

    WaveBackground(accent = accent, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 32.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                Text(
                    text = "music",
                    style = HubTitle,
                    color = com.prism.muse.ui.theme.TextPrimary,
                    modifier = Modifier
                        .padding(start = 24.dp, bottom = 8.dp)
                        .graphicsLayer { translationX = -scrollSignal * 0.06f }
                )
            }

            item {
                HubSection(
                    title = "recently played",
                    parallax = scrollSignal,
                    direction = 1f
                ) {
                    itemsIndexed(MockLibrary.recentlyPlayed) { _, song ->
                        SongCard(song = song, onClick = { onSongClick(song) })
                    }
                }
            }

            item {
                HubSection(title = "albums", parallax = scrollSignal, direction = -1f) {
                    itemsIndexed(MockLibrary.albums) { _, album ->
                        AlbumCard(album = album, onClick = { onAlbumClick(album) })
                    }
                }
            }

            item {
                HubSection(title = "artists", parallax = scrollSignal, direction = 1f) {
                    itemsIndexed(MockLibrary.artists) { _, artist ->
                        ArtistCircle(artist = artist, onClick = { onArtistClick(artist) })
                    }
                }
            }

            item {
                HubSection(title = "playlists", parallax = scrollSignal, direction = -1f) {
                    itemsIndexed(MockLibrary.playlists) { _, playlist ->
                        PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                    }
                }
            }

            item {
                HubSection(title = "favorites", parallax = scrollSignal, direction = 1f) {
                    itemsIndexed(MockLibrary.favorites) { _, song ->
                        SongCard(song = song, onClick = { onSongClick(song) })
                    }
                }
            }

            item {
                HubSection(title = "downloaded", parallax = scrollSignal, direction = -1f) {
                    itemsIndexed(MockLibrary.downloaded) { _, song ->
                        SongCard(song = song, onClick = { onSongClick(song) })
                    }
                }
            }

            item {
                HubSection(title = "genres", parallax = scrollSignal, direction = 1f) {
                    itemsIndexed(MockLibrary.genres) { _, genre ->
                        GenreChip(name = genre.name, count = genre.albumCount, onClick = { onGenreClick(genre.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HubSection(
    title: String,
    parallax: Float,
    direction: Float,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    // Diminishing, clamped shift so distant scroll positions don't fling rows off-screen.
    val shift = (parallax * 0.025f * direction).coerceIn(-48f, 48f)

    Box(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column {
            Text(
                title,
                style = MaterialTheme.typography.displayMedium,
                color = com.prism.muse.ui.theme.TextPrimary,
                modifier = Modifier
                    .padding(start = 24.dp, bottom = 12.dp)
                    .graphicsLayer { translationX = shift }
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shift * 1.6f }
            ) {
                content()
            }
        }
    }
}
