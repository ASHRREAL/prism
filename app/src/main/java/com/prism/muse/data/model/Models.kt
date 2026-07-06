package com.prism.muse.data.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val album: String,
    val durationSec: Int,
    val trackNumber: Int,
    val artUrl: String,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val playCount: Int = 0
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int,
    val artUrl: String,
    val trackCount: Int,
    val genre: String,
    val dominantColorHex: String
)

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String,
    val albumCount: Int,
    val bio: String = ""
)

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val coverUrls: List<String>,
    val pinned: Boolean = false
)

data class Genre(
    val id: String,
    val name: String,
    val albumCount: Int
)
