package com.prism.muse.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.data.navidrome.SubsonicClient
import com.prism.muse.ui.components.AriaBackground
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.components.SongRow
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Universal search (songs / albums / artists) over Subsonic search3 with
 * debounced instant results; demo library when no server is connected.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onOpenNowPlaying: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val graph = PrismApp.graph(LocalContext.current)
    val accent = LocalPrismAccent.current

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SubsonicClient.SearchResults?>(null) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        searching = true
        delay(250) // debounce for instant-feel without hammering the server
        results = runCatching { graph.library.search(query) }.getOrNull()
        searching = false
    }

    AriaBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    "‹",
                    style = SectionHeader.copy(fontSize = 30.sp),
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp)
                )
                Text("search", style = HubTitle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = TextPrimary)
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("songs, albums, artists…", color = TextTertiary) },
                textStyle = SectionHeader.copy(fontSize = 24.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = accent
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            HairlineDivider()

            val r = results
            LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
                if (searching) {
                    item {
                        Text(
                            "searching…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                if (r != null) {
                    if (r.songs.isNotEmpty()) {
                        item { ResultHeader("songs") }
                        itemsIndexed(r.songs, key = { _, s -> "s${s.id}" }) { index, song ->
                            SongRow(
                                song = song,
                                onClick = {
                                    graph.player.setQueue(r.songs, index)
                                    onOpenNowPlaying()
                                },
                                accent = accent
                            )
                        }
                    }
                    if (r.albums.isNotEmpty()) {
                        item { ResultHeader("albums") }
                        items(r.albums, key = { "a${it.id}" }) { album ->
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().clickable { onAlbumClick(album) }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Artwork(seed = album.artUrl, label = album.title, modifier = Modifier.size(56.dp))
                                    Column(Modifier.padding(start = 16.dp)) {
                                        Text(album.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                HairlineDivider()
                            }
                        }
                    }
                    if (r.artists.isNotEmpty()) {
                        item { ResultHeader("artists") }
                        items(r.artists, key = { "r${it.id}" }) { artist ->
                            Column {
                                Text(
                                    artist.name,
                                    style = SectionHeader.copy(fontSize = 24.sp),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onArtistClick(artist) }
                                        .padding(vertical = 12.dp)
                                )
                                HairlineDivider()
                            }
                        }
                    }
                    if (r.songs.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty() && !searching) {
                        item {
                            Text(
                                "nothing found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextTertiary,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultHeader(text: String) {
    Text(
        text,
        style = SectionHeader.copy(fontSize = 22.sp),
        color = TextTertiary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}
