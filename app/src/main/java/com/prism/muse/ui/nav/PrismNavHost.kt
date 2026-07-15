package com.prism.muse.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.prism.muse.PrismApp
import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.data.model.Playlist
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.screens.album.AlbumDetailScreen
import com.prism.muse.ui.screens.artist.ArtistDetailScreen
import com.prism.muse.ui.screens.home.HomeHubScreen
import com.prism.muse.ui.screens.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"

    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
}

@Composable
fun PrismNavHost(
    navController: NavHostController,
    playbackViewModel: PlaybackViewModel,
    contentPadding: PaddingValues,
    onOpenNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit = {},
    onOpenGenre: (String) -> Unit = {},
    onOpenAllSongs: () -> Unit = {},
    onOpenSongs: (String, List<com.prism.muse.data.model.Song>) -> Unit = { _, _ -> }
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(240)) { it / 6 } }
    ) {
        composable(Routes.HOME) {
            HomeHubScreen(
                onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                onPlaylistClick = onOpenPlaylist,
                onOpenNowPlaying = onOpenNowPlaying,
                onGenreClick = onOpenGenre,
                onOpenAllSongs = onOpenAllSongs,
                onOpenSongs = onOpenSongs,
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onSettingsClick = onOpenSettings,
                viewModel = playbackViewModel,
                contentPadding = contentPadding
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onAlbumClick = { navController.navigate(Routes.album(it.id)) },
                onArtistClick = { navController.navigate(Routes.artist(it.id)) },
                onOpenNowPlaying = onOpenNowPlaying
            )
        }
        composable(Routes.ALBUM) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: return@composable
            val library = PrismApp.graph(LocalContext.current).library
            val albums by library.albums.collectAsState()
            val album = albums.find { it.id == albumId }
            if (album != null) {
                AlbumDetailScreen(
                    album = album,
                    onBack = { navController.popBackStack() },
                    onOpenNowPlaying = onOpenNowPlaying
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text(
                            "album not found",
                            color = com.prism.muse.ui.theme.TextTertiary
                        )
                        androidx.compose.material3.Text(
                            "connect a server to load your library",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = com.prism.muse.ui.theme.TextTertiary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        androidx.compose.material3.Text(
                            "go back",
                            color = com.prism.muse.ui.theme.LocalPrismAccent.current,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 16.dp).clickable { navController.popBackStack() }
                        )
                    }
                }
            }
        }
        composable(Routes.ARTIST) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
            val library = PrismApp.graph(LocalContext.current).library
            val artists by library.artists.collectAsState()
            val artist = artists.find { it.id == artistId }
            if (artist != null) {
                ArtistDetailScreen(
                    artist = artist,
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { navController.navigate(Routes.album(it.id)) }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text(
                            "artist not found",
                            color = com.prism.muse.ui.theme.TextTertiary
                        )
                        androidx.compose.material3.Text(
                            "connect a server to load your library",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = com.prism.muse.ui.theme.TextTertiary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        androidx.compose.material3.Text(
                            "go back",
                            color = com.prism.muse.ui.theme.LocalPrismAccent.current,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 16.dp).clickable { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
