package com.prism.muse

import android.app.Application
import android.content.Context
import com.prism.muse.data.LibraryRepository
import com.prism.muse.data.LyricsRepository
import com.prism.muse.data.navidrome.SubsonicClient
import com.prism.muse.data.prefs.AppPrefs
import com.prism.muse.playback.PlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-wired object graph — deliberately no Hilt so the build needs no
 * annotation processors; everything hangs off the Application.
 */
class AppGraph(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = AppPrefs(app)
    val api = SubsonicClient { prefs.server.value }
    val library = LibraryRepository(app, api, prefs, appScope)
    val lyrics = LyricsRepository(api, prefs)
    val player = PlayerHolder(app, prefs, library)
}

class PrismApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    companion object {
        fun graph(context: Context): AppGraph =
            (context.applicationContext as PrismApp).graph
    }
}
