package com.prism.muse.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.prism.muse.data.mock.MockLibrary
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.screens.StubScreen
import com.prism.muse.ui.screens.album.AlbumDetailScreen
import com.prism.muse.ui.screens.artist.ArtistDetailScreen
import com.prism.muse.ui.screens.home.HomeHubScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
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
    onOpenNowPlaying: () -> Unit
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
                onPlaylistClick = { /* playlist detail: future work */ },
                onSongClick = { song ->
                    playbackViewModel.playSong(song)
                    onOpenNowPlaying()
                },
                onGenreClick = { /* genre browse: future work */ },
                contentPadding = contentPadding
            )
        }
        composable(Routes.SEARCH) { StubScreen("Search") }
        composable(Routes.SETTINGS) { StubScreen("Settings") }
        composable(Routes.ALBUM) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: return@composable
            val album = MockLibrary.albumById(albumId)
            AlbumDetailScreen(
                album = album,
                onBack = { navController.popBackStack() },
                onSongClick = { song ->
                    playbackViewModel.playSong(song)
                    onOpenNowPlaying()
                }
            )
        }
        composable(Routes.ARTIST) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
            val artist = MockLibrary.artistById(artistId)
            ArtistDetailScreen(
                artist = artist,
                onBack = { navController.popBackStack() },
                onAlbumClick = { navController.navigate(Routes.album(it.id)) }
            )
        }
    }
}
