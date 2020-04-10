package com.vasilevkin.musicplayer.player

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer
import com.vasilevkin.musicplayer.model.local.Song


interface IMediaPlayer {

    fun play(song: Song)

    fun getPlayer(context: Context): ExoPlayer

    fun releasePlayer()

    fun setMediaSessionState(isActive: Boolean)
}