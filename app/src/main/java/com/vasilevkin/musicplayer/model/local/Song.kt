package com.vasilevkin.musicplayer.model.local

import java.io.Serializable


data class Song(
    val author: String? = null,
    val trackTitle: String? = null,
    val soundUrl: String? = null
) : Serializable