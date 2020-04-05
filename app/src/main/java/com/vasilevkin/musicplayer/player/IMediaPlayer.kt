package com.vasilevkin.musicplayer.player

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer


interface IMediaPlayer {

    fun play(url: String)

    fun getPlayer(context: Context): ExoPlayer

    fun releasePlayer()

    fun setMediaSessionState(isActive: Boolean)
}