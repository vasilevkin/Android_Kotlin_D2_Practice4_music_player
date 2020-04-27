package com.vasilevkin.musicplayer.features.playsound.presenter

import android.content.Context
import com.google.android.exoplayer2.ExoPlayerFactory
import com.vasilevkin.musicplayer.base.BasePresenter
import com.vasilevkin.musicplayer.features.playsound.IPlaySoundContract
import com.vasilevkin.musicplayer.model.local.Song
import com.vasilevkin.musicplayer.player.MediaPlayer
import java.lang.ref.WeakReference


class PlaySoundPresenter(playSoundView: IPlaySoundContract.View) : BasePresenter<IPlaySoundContract.View>(), IPlaySoundContract.Presenter {

    override var view: IPlaySoundContract.View? = playSoundView

//    private val view = WeakReference(playSoundView)

    private val mediaPlayer
//     = ExoPlayerFactory.newSimpleInstance(view as Context)
            = MediaPlayer()

    override fun deactivate() {}

    override fun getPlayer() = mediaPlayer

    override fun play(song: Song) = mediaPlayer.play(song)

    override fun releasePlayer() = mediaPlayer.releasePlayer()

    override fun setMediaSessionState(isActive: Boolean) {
        mediaPlayer.setMediaSessionState(isActive)
    }
}