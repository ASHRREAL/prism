package com.prism.muse.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.prism.muse.data.mock.MockLibrary
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.ui.components.AlbumCard
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.components.FloatingIcon
import com.prism.muse.ui.components.WaveBackground
import com.prism.muse.ui.components.seedColor
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit
) {
    val accent = seedColor(artist.imageUrl)
    val albums = remember(artist.id) { MockLibrary.albumsForArtist(artist.name) }

    WaveBackground(accent = accent, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                FloatingIcon(Icons.Rounded.ArrowBack, "Back", onClick = onBack, size = 44.dp)
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(
                            seed = artist.imageUrl,
                            modifier = Modifier.size(160.dp).clip(CircleShape),
                        )
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.displayMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 20.dp)
                        )
                        Text("${artist.albumCount} albums", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                        if (artist.bio.isNotBlank()) {
                            Text(
                                artist.bio,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }

                item {
                    Text(
                        "albums",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 12.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        items(albums) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }
    }
}
