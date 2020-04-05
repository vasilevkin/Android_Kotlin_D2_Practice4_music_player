package com.vasilevkin.musicplayer.features.playsound.presenter

import com.vasilevkin.musicplayer.features.playsound.IPlaySoundContract
import com.vasilevkin.musicplayer.player.MediaPlayer
import java.lang.ref.WeakReference


class PlaySoundPresenter(playSoundView: IPlaySoundContract.View) : IPlaySoundContract.Presenter {

    private val view = WeakReference(playSoundView)

    private val mediaPlayer = MediaPlayer()

    override fun deactivate() {}

    override fun getPlayer() = mediaPlayer

    override fun play(url: String) = mediaPlayer.play(url)

    override fun releasePlayer() = mediaPlayer.releasePlayer()

    override fun setMediaSessionState(isActive: Boolean) {
        mediaPlayer.setMediaSessionState(isActive)
    }
}