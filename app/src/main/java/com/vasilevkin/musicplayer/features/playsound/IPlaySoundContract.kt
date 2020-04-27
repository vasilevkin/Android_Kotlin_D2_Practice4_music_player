package com.vasilevkin.musicplayer.features.playsound

import com.vasilevkin.musicplayer.base.BaseContract
import com.vasilevkin.musicplayer.model.local.Song
import com.vasilevkin.musicplayer.player.IMediaPlayer


interface IPlaySoundContract {

    interface Presenter : BaseContract.Presenter {

        fun deactivate()

        fun getPlayer(): IMediaPlayer

        fun play(song: Song)

        fun releasePlayer()

        fun setMediaSessionState(isActive: Boolean)
    }

    interface View : BaseContract.View
}
