package com.prism.muse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.prism.muse.playback.PlaybackViewModel
import com.prism.muse.ui.components.MiniPlayer
import com.prism.muse.ui.components.SongActionsHost
import com.prism.muse.ui.components.seedColor
import com.prism.muse.ui.nav.PrismNavHost
import com.prism.muse.ui.screens.nowplaying.NowPlayingScreen
import com.prism.muse.ui.screens.lyrics.LyricsScreen
import com.prism.muse.ui.screens.eq.EqScreen
import com.prism.muse.ui.screens.visualizer.VisualizerScreen
import com.prism.muse.ui.screens.queue.QueueScreen
import com.prism.muse.ui.screens.settings.SettingsScreen
import com.prism.muse.ui.screens.login.LoginScreen
import com.prism.muse.ui.screens.songlist.SongListDetailScreen
import com.prism.muse.ui.components.HairlineDivider
import com.prism.muse.ui.theme.PrismMuseTheme
import com.prism.muse.ui.theme.VoidBlack
import com.prism.muse.ui.theme.ProvideAccent
import com.prism.muse.ui.theme.accentColorByName
import com.prism.muse.ui.theme.SectionHeader
import com.prism.muse.ui.theme.TextPrimary
import com.prism.muse.ui.theme.TextTertiary
import com.prism.muse.data.model.Song
import com.prism.muse.data.model.Playlist
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val playbackViewModel: PlaybackViewModel by viewModels()

    /**
     * Connecting a MediaController binds and starts [PlaybackService], which is
     * what registers the MediaSession with the system — without it the session
     * only comes alive if the manual startService() call raced correctly, and
     * the phone's media controls never learned about us. The controller itself
     * is unused; the UI keeps talking to the shared ExoPlayer directly.
     */
    private var controllerFuture:
        com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

    override fun onStart() {
        super.onStart()
        runCatching {
            val token = androidx.media3.session.SessionToken(
                this,
                android.content.ComponentName(this, com.prism.muse.playback.PlaybackService::class.java)
            )
            controllerFuture = androidx.media3.session.MediaController.Builder(this, token).buildAsync()
        }
    }

    override fun onStop() {
        controllerFuture?.let { androidx.media3.session.MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Notification permission so the media-controls notification can show on
        // Android 13+. RECORD_AUDIO is only needed by the visualizer, which asks
        // for it itself when opened — requesting the mic at launch scares users.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            val prefs = PrismApp.graph(this).prefs
            val accentName by prefs.accentColorName.collectAsState()
            PrismMuseTheme(accent = accentColorByName(accentName)) {
                PrismApp(playbackViewModel)
            }
        }
    }
}

@Composable
private fun PrismApp(playbackViewModel: PlaybackViewModel) {
    val navController = rememberNavController()
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var eqOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var loginOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var visOpen by remember { mutableStateOf(false) }
    var songListOpen by remember { mutableStateOf(false) }
    var songListTitle by remember { mutableStateOf("") }
    var songListSongs by remember { mutableStateOf(emptyList<Song>()) }
    var lastBackMs by remember { mutableStateOf(0L) }

    val overlayOpen = nowPlayingOpen || lyricsOpen || eqOpen || visOpen || settingsOpen || loginOpen || queueOpen || songListOpen

    val context = LocalContext.current
    val activity = context as? android.app.Activity
        val graph = PrismApp.graph(context)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        graph.player.restoreSession()
    }

    val playbackState by playbackViewModel.state.collectAsState()
    val dynamicAccent by graph.prefs.dynamicAccent.collectAsState()
    val settingsAccentName by graph.prefs.accentColorName.collectAsState()

    // App-wide accent: dynamic from art when enabled, else settings accent
    val appAccent = if (dynamicAccent && playbackState.current != null) {
        seedColor(playbackState.current!!.artUrl)
    } else {
        accentColorByName(settingsAccentName)
    }

    ProvideAccent(appAccent) {

    BackHandler(enabled = true) {
        when {
            queueOpen -> queueOpen = false
            visOpen -> visOpen = false
            eqOpen -> eqOpen = false
            lyricsOpen -> lyricsOpen = false
            nowPlayingOpen -> nowPlayingOpen = false
            songListOpen -> songListOpen = false
            loginOpen -> loginOpen = false
            settingsOpen -> settingsOpen = false
            navController.previousBackStackEntry != null -> {
                navController.popBackStack()
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackMs < 2000) {
                    activity?.finish()
                } else {
                    lastBackMs = now
                    android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Fill the whole window (incl. behind the status/nav bars) with the app
    // background; each screen applies its own status/nav insets. This stops the
    // strip behind the gesture pill from showing the default window colour.
    Box(Modifier.fillMaxSize().background(VoidBlack)) {
        PrismNavHost(
            navController = navController,
            playbackViewModel = playbackViewModel,
            contentPadding = PaddingValues(bottom = 80.dp),
            onOpenNowPlaying = { nowPlayingOpen = true },
            onOpenSettings = { settingsOpen = true },
            onOpenPlaylist = { playlist ->
                scope.launch {
                    val songs = runCatching { graph.library.songsForPlaylist(playlist.id) }.getOrDefault(emptyList())
                    songListTitle = playlist.name
                    songListSongs = songs
                    songListOpen = true
                }
            },
            onOpenGenre = { genre ->
                scope.launch {
                    val songs = runCatching { graph.library.songsForGenre(genre) }.getOrDefault(emptyList())
                    songListTitle = genre
                    songListSongs = songs
                    songListOpen = true
                }
            },
            onOpenAllSongs = {
                scope.launch {
                    songListTitle = "all songs"
                    songListSongs = emptyList()
                    songListOpen = true
                    songListSongs = runCatching { graph.library.allSongs() }.getOrDefault(emptyList())
                }
            }
        )

        val currentArtUrl = playbackState.current?.artUrl

        // Layer 1: Settings / SongLists / Login (bottom layer)
        AnimatedVisibility(
            visible = songListOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
        ) {
            GestureLayer {
                SongListDetailScreen(
                    title = songListTitle,
                    songs = songListSongs,
                    viewModel = playbackViewModel,
                    onBack = { songListOpen = false }
                )
            }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
        ) {
            GestureLayer {
                SettingsScreen(
                    onBack = { settingsOpen = false },
                    onOpenAccounts = { loginOpen = true }
                )
            }
        }

        AnimatedVisibility(
            visible = loginOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
        ) {
            GestureLayer {
                LoginScreen(
                    onBack = { loginOpen = false }
                )
            }
        }

        // Layer 2: Now Playing (above settings/songlists, below lyrics/eq/vis/queue)
        AnimatedVisibility(
            visible = nowPlayingOpen,
            enter = slideInVertically(tween(360)) { it } + fadeIn(tween(280)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(220))
        ) {
            GestureLayer {
                NowPlayingScreen(
                    viewModel = playbackViewModel,
                    onCollapse = { nowPlayingOpen = false },
                    onOpenLyrics = { lyricsOpen = true },
                    onOpenEq = { eqOpen = true },
                    onOpenVis = { visOpen = true },
                    onOpenQueue = { queueOpen = true }
                )
            }
        }

        // Layer 3: Lyrics / EQ / Visualizer / Queue (top layer)
        AnimatedVisibility(
            visible = lyricsOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
        ) {
            GestureLayer {
                LyricsScreen(
                    viewModel = playbackViewModel,
                    onBack = { lyricsOpen = false },
                    artUrl = currentArtUrl
                )
            }
        }

        AnimatedVisibility(
            visible = eqOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
        ) {
            GestureLayer {
                EqScreen(
                    viewModel = playbackViewModel,
                    onBack = { eqOpen = false },
                    artUrl = currentArtUrl
                )
            }
        }

        AnimatedVisibility(
            visible = visOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
        ) {
            GestureLayer {
                VisualizerScreen(
                    viewModel = playbackViewModel,
                    onBack = { visOpen = false },
                    artUrl = currentArtUrl
                )
            }
        }

        AnimatedVisibility(
            visible = queueOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200))
        ) {
            GestureLayer {
                QueueScreen(
                    viewModel = playbackViewModel,
                    onBack = { queueOpen = false }
                )
            }
        }
        val fullPlayerOpen = nowPlayingOpen || lyricsOpen || eqOpen || visOpen || queueOpen
        playbackState.current?.let { current ->
            AnimatedVisibility(
                visible = !fullPlayerOpen,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MiniPlayer(
                    song = current,
                    isPlaying = playbackState.isPlaying,
                    accent = seedColor(current.artUrl),
                    onTogglePlay = playbackViewModel::togglePlay,
                    onSkipNext = playbackViewModel::skipNext,
                    onSkipPrevious = playbackViewModel::skipPrevious,
                    onExpand = { nowPlayingOpen = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // App-level song actions sheet (add to playlist / queue / like …).
        SongActionsHost(playbackViewModel)

        // Playlist actions (delete) bottom sheet
        PlaylistActionsHost()
    }
    } // Close ProvideAccent
}

object PlaylistActions {
    var current by mutableStateOf<Playlist?>(null)
        private set
    fun show(p: Playlist) { current = p }
    fun hide() { current = null }
}

@Composable
private fun PlaylistActionsHost() {
    val pl = PlaylistActions.current ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { PlaylistActions.hide() } }
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(VoidBlack)
                .navigationBarsPadding().padding(24.dp)
        ) {
            Text(pl.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TextPrimary)
            HairlineDivider(Modifier.padding(vertical = 12.dp))
            Text("delete playlist", style = SectionHeader.copy(fontSize = 22.sp), color = Color(0xFFD32F2F),
                modifier = Modifier.fillMaxWidth().clickable {
                    PlaylistActions.hide()
                    scope.launch { PrismApp.graph(ctx).library.deletePlaylist(pl.id) }
                }.padding(vertical = 12.dp))
            Text("cancel", style = SectionHeader.copy(fontSize = 22.sp), color = TextTertiary,
                modifier = Modifier.fillMaxWidth().clickable { PlaylistActions.hide() }.padding(vertical = 12.dp))
        }
    }
}

/**
 * Wraps a full-screen overlay so it fully absorbs touches: a transparent
 * catcher sits *behind* the overlay's own content and consumes any pointer
 * events the content didn't handle. Without this, taps/drags on the overlay's
 * empty areas fell through to whatever screen was underneath (e.g. swiping down
 * in Lyrics was reaching Now Playing's collapse gesture).
 */
@Composable
private fun GestureLayer(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().clickable(enabled = false) { })
        content()
    }
}
