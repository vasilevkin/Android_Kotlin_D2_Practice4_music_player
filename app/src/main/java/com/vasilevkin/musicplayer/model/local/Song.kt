package com.vasilevkin.musicplayer.model.local

import java.io.Serializable


data class Song(
    val author: String? = null,
    val trackTitle: String? = null,
    val soundUrl: String? = null
)


class Audio(
    var data: String,
    var title: String,
    var album: String,
    var artist: String
) : Serializable