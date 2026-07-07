package com.prism.muse.data

import com.prism.muse.data.model.Lyrics
import com.prism.muse.data.model.LyricLine
import com.prism.muse.data.model.Song
import com.prism.muse.data.navidrome.SubsonicClient
import com.prism.muse.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LyricsRepository(private val api: SubsonicClient, private val prefs: AppPrefs) {

    private val cache = HashMap<String, Lyrics?>()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun lyricsFor(song: Song): Lyrics? {
        synchronized(cache) { if (cache.containsKey(song.id)) return cache[song.id] }

        val result = withContext(Dispatchers.IO) {
            val autoFetch = prefs.autoFetchLyrics.value

            if (autoFetch) {
                // Try multiple sources in order
                runCatching { lrclibLyrics(song) }.getOrNull()
                    ?: runCatching { lrclibSearch(song) }.getOrNull()
                    ?: runCatching { lyricsOvhLyrics(song) }.getOrNull()
                    ?: runCatching { neteaseLyrics(song) }.getOrNull()
                    ?: runCatching { api.getLyrics(song) }.getOrNull()
                    ?: if (song.streamUrl.isBlank()) demoLyrics(song) else null
            } else {
                runCatching { api.getLyrics(song) }.getOrNull()
                    ?: if (song.streamUrl.isBlank()) demoLyrics(song) else null
            }
        }

        synchronized(cache) { cache[song.id] = result }
        return result
    }

    fun clearCache() {
        cache.clear()
    }

    /** Simple translation attempt — currently returns null (stub). */
    suspend fun translateLyrics(song: Song?, lyrics: Lyrics?): Lyrics? {
        if (lyrics == null || song == null) return null
        return null // Stub: user-facing "translation unavailable" message
    }

    private fun lrclibSearch(song: Song): Lyrics? {
        // Try without album name (broader search)
        try {
            val url = "https://lrclib.net/api/search?" +
                "q=${java.net.URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")}"
            val request = Request.Builder().url(url).header("User-Agent", "PrismMuse/0.2").build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val arr = org.json.JSONArray(response.body!!.string())
            if (arr.length() == 0) return null

            // Try first result's synced lyrics
            for (i in 0 until arr.length()) {
                val json = arr.getJSONObject(i)
                val syncedRaw = json.optString("syncedLyrics", "").ifBlank { null }
                val syncedLines = syncedRaw?.lines()?.filter { it.isNotBlank() }?.mapNotNull { raw ->
                    val match = Regex("""\[(\d+):(\d+(?:\.\d+)?)\](.*)""").find(raw.trim())
                    if (match == null) return@mapNotNull null
                    val min = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                    val sec = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
                    val text = match.groupValues[3].replace(Regex("""<\d+:\d+(?:\.\d+)?>"""), "").trim()
                    LyricLine(((min * 60 + sec) * 1000).toLong(), text)
                }
                if (syncedLines != null) return Lyrics(syncedLines, synced = true, source = "lrclib.net")
            }
            return null
        } catch (_: Exception) {
            return null
        }
    }

    private fun lrclibLyrics(song: Song): Lyrics? {
        val url = "https://lrclib.net/api/get?" +
            "artist_name=${java.net.URLEncoder.encode(song.artist, "UTF-8")}" +
            "&track_name=${java.net.URLEncoder.encode(song.title, "UTF-8")}" +
            "&album_name=${java.net.URLEncoder.encode(song.album, "UTF-8")}"
        val request = Request.Builder().url(url).header("User-Agent", "PrismMuse/0.2").build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val json = JSONObject(response.body!!.string())

        val syncedRaw = json.optString("syncedLyrics", "").ifBlank { null }
        val syncedLines = syncedRaw?.lines()?.filter { it.isNotBlank() }?.mapNotNull { raw ->
            val match = Regex("""\[(\d+):(\d+(?:\.\d+)?)\](.*)""").find(raw.trim())
            if (match == null) return@mapNotNull null
            val min = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val sec = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val text = match.groupValues[3].replace(Regex("""<\d+:\d+(?:\.\d+)?>"""), "").trim()
            LyricLine(((min * 60 + sec) * 1000).toLong(), text)
        }

        val plainLines = json.optString("plainLyrics", "").ifBlank { null }
            ?.lines()?.filter { it.isNotBlank() }?.mapIndexed { i, text ->
                LyricLine((i * 5000).toLong(), text)
            }

        val lines = syncedLines ?: plainLines
        if (lines.isNullOrEmpty()) return null
        return Lyrics(lines = lines, synced = syncedLines != null, source = "lrclib.net")
    }

    /** lyrics.ovh provider — simple lyrics API that sometimes has more songs. */
    private fun lyricsOvhLyrics(song: Song): Lyrics? {
        val url = "https://api.lyrics.ovh/v1/" +
            "${java.net.URLEncoder.encode(song.artist, "UTF-8")}/" +
            "${java.net.URLEncoder.encode(song.title, "UTF-8")}"
        val request = Request.Builder().url(url).header("User-Agent", "PrismMuse/0.2").build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val json = JSONObject(response.body!!.string())
        val text = json.optString("lyrics", "")
        if (text.isBlank()) return null
        val lines = text.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
            LyricLine((i * 5000).toLong(), line.trim())
        }
        if (lines.isEmpty()) return null
        return Lyrics(lines = lines, synced = false, source = "lyrics.ovh")
    }

    /** Netease Cloud Music API — better coverage for Japanese/Vocaloid songs. */
    private fun neteaseLyrics(song: Song): Lyrics? {
        try {
            // First search for the song
            val searchUrl = "https://music.163.com/api/search/get?" +
                "s=${java.net.URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")}" +
                "&type=1&limit=1"
            val req = Request.Builder().url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36")
                .header("Referer", "https://music.163.com/")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body!!.string())
            val songs = json.optJSONObject("result")?.optJSONArray("songs")
            if (songs == null || songs.length() == 0) return null
            val songId = songs.getJSONObject(0).optInt("id", -1)
            if (songId < 0) return null

            // Get lyrics for the song
            val lrcUrl = "https://music.163.com/api/song/lyric?" +
                "id=$songId&lv=1&kv=1&tv=-1"
            val lrcReq = Request.Builder().url(lrcUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36")
                .header("Referer", "https://music.163.com/")
                .build()
            val lrcResp = httpClient.newCall(lrcReq).execute()
            if (!lrcResp.isSuccessful) return null
            val lrcJson = JSONObject(lrcResp.body!!.string())

            val lrc = lrcJson.optJSONObject("lrc")?.optString("lyric", "").orEmpty()
            val tLrc = lrcJson.optJSONObject("tlyric")?.optString("lyric", "").orEmpty()

            if (lrc.isBlank()) return null

            val lines = lrc.lines().filter { it.isNotBlank() }.mapNotNull { raw ->
                val match = Regex("""\[(\d+):(\d+(?:\.\d+)?)\](.*)""").find(raw.trim())
                if (match == null) return@mapNotNull null
                val min = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val sec = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
                val text = match.groupValues[3].trim()
                LyricLine(((min * 60 + sec) * 1000).toLong(), text)
            }
            if (lines.isEmpty()) return null
            return Lyrics(lines = lines, synced = true, source = "netease")
        } catch (_: Exception) {
            return null
        }
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
