package com.vasilevkin.musicplayer.features.playsound

import com.vasilevkin.musicplayer.player.IMediaPlayer


interface IPlaySoundContract {

    interface Presenter {

        fun deactivate()

        fun getPlayer(): IMediaPlayer

        fun play(url: String)

        fun releasePlayer()

        fun setMediaSessionState(isActive: Boolean)
    }

    interface View
}