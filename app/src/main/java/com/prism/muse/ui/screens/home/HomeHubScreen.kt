package com.prism.muse.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.muse.PrismApp
import com.prism.muse.PlaylistActions
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.data.model.Genre
import com.prism.muse.data.model.Playlist
import com.prism.muse.data.model.Song
import com.prism.muse.ui.components.AriaBackground
import com.prism.muse.ui.components.Artwork
import com.prism.muse.ui.components.FloatingIcon
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.components.PagerDots
import com.prism.muse.ui.components.SongRow
import com.prism.muse.ui.components.SongRowWithMenu
import com.prism.muse.ui.theme.HubTitle
import com.prism.muse.ui.theme.LocalPrismAccent
import com.prism.muse.ui.theme.MetroListEntry
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextSecondary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.theme.VoidBlack
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeHubScreen(
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onGenreClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenAllSongs: () -> Unit = {},
    viewModel: com.prism.muse.playback.PlaybackViewModel? = null,
    contentPadding: PaddingValues = PaddingValues()
) {
    val graph = PrismApp.graph(LocalContext.current)
    val library = graph.library
    val scope = rememberCoroutineScope()
    val accent = LocalPrismAccent.current

    val recent by library.recentSongs.collectAsState()
    val albums by library.albums.collectAsState()
    val artists by library.artists.collectAsState()
    val playlists by library.playlists.collectAsState()
    val favorites by library.favorites.collectAsState()
    val downloaded by library.downloaded.collectAsState()
    val genres: List<Genre> by library.genres.collectAsState()
    val recommended: List<Song> by library.recommended.collectAsState()
    val playback by graph.player.state.collectAsState()
    val visibleTabs by graph.prefs.visibleTabs.collectAsState()

    val allPanels = listOf("recommended", "recently played", "albums", "artists", "playlists", "favorites", "downloaded", "genres", "all songs")
    val panels = remember(visibleTabs) {
        allPanels.filter { it in visibleTabs }.ifEmpty { allPanels }
    }
    val pagerState = rememberPagerState(pageCount = { panels.size })
    val scroll by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    fun playSongs(songs: List<Song>, index: Int) {
        graph.player.setQueue(songs, index)
    }

    AriaBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .pointerInput(Unit) {
                    var down = 0f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (down > 200f) {
                                scope.launch { library.refresh() }
                            }
                            down = 0f
                        },
                        onDragCancel = { down = 0f }
                    ) { _, dy -> down += dy }
                }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "music",
                    style = HubTitle,
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { translationX = -scroll * 22.dp.toPx() }
                )
                FloatingIcon(Icons.Rounded.Search, "Search", onClick = onSearchClick, size = 44.dp)
                FloatingIcon(Icons.Rounded.Settings, "Settings", onClick = onSettingsClick, size = 44.dp)
            }

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(start = 0.dp, end = 100.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.weight(1f).padding(top = 6.dp)
            ) { page ->
                val bottomPad = contentPadding.calculateBottomPadding() + 32.dp
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, end = 24.dp)
                        .graphicsLayer {
                            val off = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                            val t = off.coerceIn(0f, 1f)
                            scaleX = 1f - 0.08f * t
                            scaleY = 1f - 0.08f * t
                            alpha = 1f - 0.4f * t
                        }
                ) {
                    Text(
                        text = panels[page],
                        style = SectionHeader,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    when (panels[page]) {
                        "recommended" -> SongsPanel(recommended, playback.current?.id, accent, bottomPad, viewModel) { i -> playSongs(recommended, i) }
                        "recently played" -> SongsPanel(recent, playback.current?.id, accent, bottomPad, viewModel) { i -> playSongs(recent, i) }
                        "albums" -> AlbumsPanel(albums, onAlbumClick, bottomPad)
                        "artists" -> ArtistsPanel(artists, onArtistClick, bottomPad)
                        "playlists" -> PlaylistsPanel(playlists, onPlaylistClick, bottomPad)
                        "favorites" -> SongsPanel(favorites, playback.current?.id, accent, bottomPad, viewModel) { i -> playSongs(favorites, i) }
                        "downloaded" -> SongsPanel(downloaded, playback.current?.id, accent, bottomPad, viewModel, emptyText = "no downloads yet") { i -> playSongs(downloaded, i) }
                        "genres" -> GenresPanel(genres.map { it.name to it.albumCount }, onGenreClick, bottomPad)
                        "all songs" -> AllSongsPanel(accent, bottomPad, onOpenAllSongs)
                    }
                }
            }

            Spacer(Modifier.padding(bottom = if (playback.current != null) 8.dp else 24.dp))
        }
    }
}

@Composable
private fun SongsPanel(
    songs: List<Song>, currentId: String?, accent: Color, bottomPad: Dp,
    viewModel: com.prism.muse.playback.PlaybackViewModel? = null,
    emptyText: String = "nothing here yet", onPlay: (Int) -> Unit
) {
    if (songs.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = TextTertiary)
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            if (viewModel != null) {
                SongRowWithMenu(song = song, viewModel = viewModel, onClick = { onPlay(index) },
                    active = song.id == currentId, accent = accent)
            } else {
                SongRow(song = song, onClick = { onPlay(index) },
                    active = song.id == currentId, accent = accent)
            }
        }
    }
}

@Composable
private fun AlbumsPanel(albums: List<Album>, onAlbumClick: (Album) -> Unit, bottomPad: Dp) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = bottomPad)
    ) {
        itemsIndexed(albums, key = { _, a -> a.id }) { _, album ->
            Column(Modifier.clickable { onAlbumClick(album) }) {
                Artwork(seed = album.artUrl, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Text(album.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistsPanel(artists: List<Artist>, onArtistClick: (Artist) -> Unit, bottomPad: Dp) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
        items(artists, key = { it.id }) { artist ->
            Column(Modifier.fillMaxWidth().clickable { onArtistClick(artist) }.padding(vertical = 12.dp)) {
                Text(artist.name, style = MetroListEntry, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${artist.albumCount} ${if (artist.albumCount == 1) "album" else "albums"}", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            }
            HairlineDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistsPanel(playlists: List<Playlist>, onPlaylistClick: (Playlist) -> Unit, bottomPad: Dp) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { onPlaylistClick(playlist) },
                        onLongClick = { PlaylistActions.show(playlist) }
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(seed = playlist.coverUrls.firstOrNull() ?: playlist.id, modifier = Modifier.size(64.dp), icon = false)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(playlist.name, style = MetroListEntry.copy(fontSize = 24.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${playlist.trackCount} tracks", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                }
            }
            HairlineDivider()
        }
    }
}

@Composable
private fun GenresPanel(genres: List<Pair<String, Int>>, onGenreClick: (String) -> Unit, bottomPad: Dp) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
        items(genres, key = { it.first }) { (name, count) ->
            Row(
                Modifier.fillMaxWidth().clickable { onGenreClick(name) }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(name.lowercase(), style = MetroListEntry.copy(fontSize = 32.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.size(12.dp))
                Text("$count", style = MaterialTheme.typography.bodyLarge, color = TextTertiary, modifier = Modifier.padding(bottom = 4.dp))
            }
            HairlineDivider()
        }
    }
}

@Composable
private fun AllSongsPanel(accent: Color, bottomPad: Dp, onOpenAllSongs: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Your complete library at a glance.", style = MaterialTheme.typography.bodyLarge, color = TextTertiary, modifier = Modifier.padding(bottom = 14.dp))
        Text("open all songs ›", style = MaterialTheme.typography.bodyLarge, color = accent, modifier = Modifier.padding(bottom = 10.dp).clickable(onClick = onOpenAllSongs))
        Text("browse and play any track from every album, artist, and playlist.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
