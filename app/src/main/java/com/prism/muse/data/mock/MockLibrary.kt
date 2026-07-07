package com.prism.muse.data.mock

import com.prism.muse.data.model.Album
import com.prism.muse.data.model.Artist
import com.prism.muse.data.model.Genre
import com.prism.muse.data.model.Playlist
import com.prism.muse.data.model.Song

/**
 * Static in-memory library standing in for the Navidrome/Subsonic API during the
 * UI prototype phase. Shapes mirror what the real repository will return later.
 */
object MockLibrary {

    private val palette = listOf(
        "#5CE0A8", "#3FA9F5", "#B57CE0", "#F4A259", "#E05C6D",
        "#4EC9B0", "#8AC24A", "#F27059", "#6C63FF", "#2ED1C2"
    )

    val albums: List<Album> = listOf(
        Album("al1", "Cinema", "Andrea Bocelli", "", 2015, "art_cinema", 14, "Classical Crossover", palette[0]),
        Album("al2", "Discovery", "Daft Punk", "", 2001, "art_discovery", 14, "Electronic", palette[1]),
        Album("al3", "In Rainbows", "Radiohead", "", 2007, "art_rainbows", 10, "Alt Rock", palette[2]),
        Album("al4", "Blonde", "Frank Ocean", "", 2016, "art_blonde", 17, "R&B", palette[3]),
        Album("al5", "Currents", "Tame Impala", "", 2015, "art_currents", 13, "Psych Pop", palette[4]),
        Album("al6", "To Pimp a Butterfly", "Kendrick Lamar", "", 2015, "art_tpab", 16, "Hip Hop", palette[5]),
        Album("al7", "Random Access Memories", "Daft Punk", "", 2013, "art_ram", 13, "Electronic", palette[6]),
        Album("al8", "Norman F***ing Rockwell", "Lana Del Rey", "", 2019, "art_nfr", 14, "Indie Pop", palette[7]),
        Album("al9", "Ctrl", "SZA", "", 2017, "art_ctrl", 14, "R&B", palette[8]),
        Album("al10", "Wildflower", "The Avalanches", "", 2016, "art_wildflower", 22, "Downtempo", palette[9]),
    )

    val artists: List<Artist> = listOf(
        Artist("ar1", "Andrea Bocelli", "art_cinema", 3, "Italian tenor known for crossover classical performances."),
        Artist("ar2", "Daft Punk", "art_ram", 2, "French electronic duo, pioneers of French house."),
        Artist("ar3", "Radiohead", "art_rainbows", 1, "English rock band known for experimental production."),
        Artist("ar4", "Frank Ocean", "art_blonde", 1, "American singer-songwriter."),
        Artist("ar5", "Tame Impala", "art_currents", 1, "Australian psychedelic project led by Kevin Parker."),
        Artist("ar6", "Kendrick Lamar", "art_tpab", 1, "American rapper and songwriter."),
    )

    val playlists: List<Playlist> = listOf(
        Playlist("pl1", "Late Night Drive", 32, listOf("art_currents", "art_blonde", "art_ram"), pinned = true),
        Playlist("pl2", "Focus Flow", 48, listOf("art_rainbows", "art_wildflower"), pinned = true),
        Playlist("pl3", "Sunday Morning", 21, listOf("art_cinema", "art_nfr")),
        Playlist("pl4", "Workout Energy", 40, listOf("art_tpab", "art_ram", "art_ctrl")),
    )

    val genres: List<Genre> = listOf(
        Genre("g1", "Electronic", 132),
        Genre("g2", "R&B", 88),
        Genre("g3", "Alt Rock", 61),
        Genre("g4", "Hip Hop", 97),
        Genre("g5", "Classical Crossover", 24),
        Genre("g6", "Indie Pop", 55),
    )

    val songs: List<Song> = buildList {
        albums.forEach { album ->
            repeat(minOf(album.trackCount, 6)) { i ->
                add(
                    Song(
                        id = "${album.id}_t$i",
                        title = "${album.title} Track ${i + 1}",
                        artist = album.artist,
                        albumId = album.id,
                        album = album.title,
                        durationSec = 180 + i * 23,
                        trackNumber = i + 1,
                        artUrl = album.artUrl,
                        isFavorite = i == 0,
                        isDownloaded = i % 3 == 0,
                        playCount = (10 - i) * 4
                    )
                )
            }
        }
    }

    val recentlyPlayed: List<Song> = songs.shuffled(kotlin.random.Random(42)).take(8)
    val favorites: List<Song> = songs.filter { it.isFavorite }
    val downloaded: List<Song> = songs.filter { it.isDownloaded }

    fun albumById(id: String) = albums.first { it.id == id }
    fun songsForAlbum(albumId: String) = songs.filter { it.albumId == albumId }.sortedBy { it.trackNumber }
    fun artistById(id: String) = artists.first { it.id == id }
    fun albumsForArtist(artistName: String) = albums.filter { it.artist == artistName }
}
