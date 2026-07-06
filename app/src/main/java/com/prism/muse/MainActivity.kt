package com.prism.muse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.MiniPlayer
import com.prism.muse.ui.components.glassBar
import com.prism.muse.ui.nav.PrismNavHost
import com.prism.muse.ui.nav.Routes
import com.prism.muse.ui.screens.nowplaying.NowPlayingScreen
import com.prism.muse.ui.theme.DefaultAccent
import com.prism.muse.ui.theme.PrismMuseTheme
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.ui.components.seedColor

class MainActivity : ComponentActivity() {

    private val playbackViewModel: PlaybackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrismMuseTheme {
                PrismApp(playbackViewModel)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "home", Icons.Rounded.Home),
    Tab(Routes.SEARCH, "search", Icons.Rounded.Search),
    Tab(Routes.SETTINGS, "settings", Icons.Rounded.Settings),
)

@Composable
private fun PrismApp(playbackViewModel: PlaybackViewModel) {
    val navController = rememberNavController()
    var nowPlayingOpen by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(Modifier.fillMaxSize()) {
        PrismNavHost(
            navController = navController,
            playbackViewModel = playbackViewModel,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 150.dp),
            onOpenNowPlaying = { nowPlayingOpen = true }
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val playbackState by playbackViewModel.state.collectAsState()
            MiniPlayer(
                song = playbackState.current,
                isPlaying = playbackState.isPlaying,
                accent = seedColor(playbackState.current.artUrl),
                onTogglePlay = playbackViewModel::togglePlay,
                onSkipNext = playbackViewModel::skipNext,
                onExpand = { nowPlayingOpen = true },
                modifier = Modifier.height(68.dp)
            )

            BottomNavBar(
                currentRoute = currentRoute,
                onSelect = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = nowPlayingOpen,
            enter = slideInVertically(tween(360)) { it } + fadeIn(tween(280)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(220))
        ) {
            NowPlayingScreen(viewModel = playbackViewModel, onCollapse = { nowPlayingOpen = false })
        }
    }
}

@Composable
private fun BottomNavBar(currentRoute: String?, onSelect: (Tab) -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(64.dp)
            .glassBar()
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    androidx.compose.material3.IconButton(onClick = { onSelect(tab) }) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) DefaultAccent else TextTertiary
                        )
                    }
                }
            }
        }
    }
}

