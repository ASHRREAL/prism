package com.prism.muse.ui.screens.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.ui.components.AriaBackground
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary

/** aria artist page: giant thin name, server biography, album strip. */
@Composable
fun ArtistDetailScreen(
    artist: Artist,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit
) {
    val graph = PrismApp.graph(LocalContext.current)

    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var bio by remember { mutableStateOf(artist.bio) }
    LaunchedEffect(artist.id) {
        albums = runCatching { graph.library.albumsForArtist(artist) }.getOrDefault(emptyList())
        graph.library.artistBio(artist).takeIf { it.isNotBlank() }?.let { bio = it }
    }

    AriaBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                "‹",
                style = SectionHeader.copy(fontSize = 30.sp),
                color = TextSecondary,
                modifier = Modifier
                    .padding(start = 24.dp, top = 10.dp)
                    .clickable(onClick = onBack)
                    .padding(8.dp)
            )

            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            artist.name.lowercase(),
                            style = HubTitle.copy(fontSize = 46.sp, lineHeight = 50.sp),
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${artist.albumCount} ${if (artist.albumCount == 1) "album" else "albums"}",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        if (bio.isNotBlank()) {
                            Text(
                                bio,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }

                item {
                    Text(
                        "albums",
                        style = SectionHeader,
                        color = TextPrimary,
                        modifier = Modifier.padding(start = 24.dp, top = 30.dp, bottom = 12.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        items(albums, key = { it.id }) { album ->
                            Column(Modifier.width(140.dp).clickable { onAlbumClick(album) }) {
                                Artwork(seed = album.artUrl, modifier = Modifier.aspectRatio(1f))
                                Text(
                                    album.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Text(
                                    album.year.takeIf { it > 0 }?.toString() ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
