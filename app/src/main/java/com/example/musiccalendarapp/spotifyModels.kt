package com.example.musiccalendarapp

// this is to display each individual track
data class playedTrack(
    val trackName: String = "",
    val artistName: String = "",
    val imageUrl: String = "",
    val playedAt: String = ""
)


data class currentTrack(
    val trackName: String = "",
    val artistName: String = "",
    val imageUrl: String = ""
)

