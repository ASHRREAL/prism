package com.prism.muse.data

import com.prism.muse.data.model.Lyrics
import com.prism.muse.data.model.LyricLine
import com.prism.muse.data.model.Song
import com.prism.muse.data.navidrome.SubsonicClient
import com.prism.muse.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class LyricsRepository(private val api: SubsonicClient, private val prefs: AppPrefs) {

    private val cache = HashMap<String, Lyrics?>()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun lyricsFor(song: Song): Lyrics? {
        synchronized(cache) { if (cache.containsKey(song.id)) return cache[song.id] }

        val result = withContext(Dispatchers.IO) {
            val found = if (prefs.autoFetchLyrics.value) {
                // All providers race concurrently instead of one-after-another
                // (serial fetching took up to ~30s of stacked timeouts). Await
                // in priority order — synced sources first — and cancel the
                // stragglers as soon as something usable arrives.
                coroutineScope {
                    val providers = listOf(
                        async { runCatching { lrclibLyrics(song) }.getOrNull() },
                        async { runCatching { lrclibSearch(song) }.getOrNull() },
                        async { runCatching { neteaseLyrics(song) }.getOrNull() },
                        async { runCatching { api.getLyrics(song) }.getOrNull() },
                        async { runCatching { lyricsOvhLyrics(song) }.getOrNull() }
                    )
                    var best: Lyrics? = null
                    for (p in providers) {
                        best = p.await()
                        if (best != null) break
                    }
                    providers.forEach { it.cancel() }
                    best
                }
            } else {
                runCatching { api.getLyrics(song) }.getOrNull()
            }
            found ?: if (song.streamUrl.isBlank()) demoLyrics(song) else null
        }

        synchronized(cache) { cache[song.id] = result }
        return result
    }

    /**
     * Suspending GET that actually aborts the socket on coroutine cancellation
     * (OkHttp's blocking execute() can't be interrupted, which made cancelled
     * lyric providers keep the fetch alive to their full timeout).
     */
    private suspend fun httpGet(url: String, referer: String? = null): String? =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(url)
                .header(
                    "User-Agent",
                    if (referer == null) "PrismMuse/0.2"
                    else "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"
                )
                .apply { referer?.let { header("Referer", it) } }
                .build()
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.use { r ->
                        if (r.isSuccessful) runCatching { r.body?.string() }.getOrNull() else null
                    }
                    if (cont.isActive) cont.resume(body)
                }
            })
        }

    /** `[mm:ss.xx]text` LRC lines; compiled once instead of per input line. */
    private val lrcLineRegex = Regex("""\[(\d+):(\d+(?:\.\d+)?)\](.*)""")
    private val lrcWordTagRegex = Regex("""<\d+:\d+(?:\.\d+)?>""")

    private fun parseLrc(raw: String): List<LyricLine> =
        raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val match = lrcLineRegex.find(line.trim()) ?: return@mapNotNull null
            val min = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val sec = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val text = match.groupValues[3].replace(lrcWordTagRegex, "").trim()
            LyricLine(((min * 60 + sec) * 1000).toLong(), text)
        }

    fun clearCache() {
        cache.clear()
    }

    /** Simple translation attempt — currently returns null (stub). */
    suspend fun translateLyrics(song: Song?, lyrics: Lyrics?): Lyrics? {
        if (lyrics == null || song == null) return null
        return null // Stub: user-facing "translation unavailable" message
    }

    private suspend fun lrclibSearch(song: Song): Lyrics? {
        // Try without album name (broader search)
        val url = "https://lrclib.net/api/search?" +
            "q=${java.net.URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")}"
        val body = httpGet(url) ?: return null
        val arr = org.json.JSONArray(body)
        for (i in 0 until arr.length()) {
            val json = arr.getJSONObject(i)
            val syncedRaw = json.optString("syncedLyrics", "").ifBlank { null } ?: continue
            val lines = parseLrc(syncedRaw)
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true, source = "lrclib.net")
        }
        return null
    }

    private suspend fun lrclibLyrics(song: Song): Lyrics? {
        val url = "https://lrclib.net/api/get?" +
            "artist_name=${java.net.URLEncoder.encode(song.artist, "UTF-8")}" +
            "&track_name=${java.net.URLEncoder.encode(song.title, "UTF-8")}" +
            "&album_name=${java.net.URLEncoder.encode(song.album, "UTF-8")}"
        val body = httpGet(url) ?: return null
        val json = JSONObject(body)

        val syncedLines = json.optString("syncedLyrics", "").ifBlank { null }
            ?.let { parseLrc(it) }?.ifEmpty { null }

        val plainLines = json.optString("plainLyrics", "").ifBlank { null }
            ?.lines()?.filter { it.isNotBlank() }?.mapIndexed { i, text ->
                LyricLine((i * 5000).toLong(), text)
            }

        val lines = syncedLines ?: plainLines
        if (lines.isNullOrEmpty()) return null
        return Lyrics(lines = lines, synced = syncedLines != null, source = "lrclib.net")
    }

    /** lyrics.ovh provider — simple lyrics API that sometimes has more songs. */
    private suspend fun lyricsOvhLyrics(song: Song): Lyrics? {
        val url = "https://api.lyrics.ovh/v1/" +
            "${java.net.URLEncoder.encode(song.artist, "UTF-8")}/" +
            "${java.net.URLEncoder.encode(song.title, "UTF-8")}"
        val body = httpGet(url) ?: return null
        val text = JSONObject(body).optString("lyrics", "")
        if (text.isBlank()) return null
        val lines = text.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
            LyricLine((i * 5000).toLong(), line.trim())
        }
        if (lines.isEmpty()) return null
        return Lyrics(lines = lines, synced = false, source = "lyrics.ovh")
    }

    /** Netease Cloud Music API — better coverage for Japanese/Vocaloid songs. */
    private suspend fun neteaseLyrics(song: Song): Lyrics? {
        // First search for the song
        val searchUrl = "https://music.163.com/api/search/get?" +
            "s=${java.net.URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")}" +
            "&type=1&limit=1"
        val searchBody = httpGet(searchUrl, referer = "https://music.163.com/") ?: return null
        val songs = JSONObject(searchBody).optJSONObject("result")?.optJSONArray("songs")
        if (songs == null || songs.length() == 0) return null
        val songId = songs.getJSONObject(0).optInt("id", -1)
        if (songId < 0) return null

        // Get lyrics for the song
        val lrcUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
        val lrcBody = httpGet(lrcUrl, referer = "https://music.163.com/") ?: return null
        val lrc = JSONObject(lrcBody).optJSONObject("lrc")?.optString("lyric", "").orEmpty()
        if (lrc.isBlank()) return null

        val lines = parseLrc(lrc)
        if (lines.isEmpty()) return null
        return Lyrics(lines = lines, synced = true, source = "netease")
    }

    private val demoLyricSets = listOf(
        listOf(
            "the streetlights blur to gold",
            "the radio hums an old refrain",
            "and every mile we drove that night",
            "still echoes down the lane",
            "the windows down, the engine low",
            "we let the night decide",
            "with nowhere that we had to be",
            "and all the time to drive"
        ),
        listOf(
            "colors bleed across the sky",
            "like watercolors running wild",
            "i trace the outlines with my eyes",
            "and lose myself for just a while",
            "the silence settles in the air",
            "a gentle whisper calls my name",
            "i follow shadows everywhere",
            "but nothing ever stays the same"
        ),
        listOf(
            "waking up to morning light",
            "the city stretches out below",
            "yesterday is out of sight",
            "there's only one way left to go",
            "the coffee's warm, the air is bright",
            "another chapter starts today",
            "i'm reaching for the fading night",
            "before it slips away"
        ),
        listOf(
            "waves crash against the shore tonight",
            "the moon is hanging low and full",
            "footprints wash away in fading light",
            "the ocean takes its pull",
            "we're standing at the water's edge",
            "where everything begins again",
            "no promises, no sacred pledge",
            "just salt and spray and wind"
        ),
        listOf(
            "in the space between two heartbeats",
            "lies a universe of sound",
            "where melodies and lonely streets",
            "are the only truth i've found",
            "turn the volume up to drown the noise",
            "let the rhythm set you free",
            "in the chaos and the poise",
            "this is where i want to be"
        ),
        listOf(
            "driving through the desert rain",
            "thunder rolling in the west",
            "every drop against the pane",
            "puts my restless mind to rest",
            "the highway stretches on for miles",
            "a ribbon winding through the dark",
            "i chase the lightning's fleeting smiles",
            "leaving nothing but a spark"
        ),
        listOf(
            "paper airplanes in the hall",
            "we wrote our dreams on every fold",
            "launched them watching as they fall",
            "stories waiting to be told",
            "some flew high and touched the ceiling",
            "some just spiraled to the floor",
            "but the act of believing",
            "keeps us coming back for more"
        ),
        listOf(
            "autumn leaves are falling down",
            "carpeting the empty street",
            "golden crowns upon the ground",
            "where the seasons come to meet",
            "i wrap my scarf a little tighter",
            "breathe the cool november air",
            "every ending makes it brighter",
            "every loss beyond compare"
        )
    )

    private fun demoLyrics(song: Song): Lyrics {
        val hash = kotlin.math.abs(song.title.hashCode() + song.artist.hashCode())
        val lines = demoLyricSets[hash % demoLyricSets.size]
        val syncedLines = lines.mapIndexed { i, text ->
            LyricLine(((i + 1) * 5000L), text)
        }
        return Lyrics(syncedLines, synced = true, source = "demo")
    }
}
