package com.vasilevkin.musicplayer.features.playsound.presenter

import com.vasilevkin.musicplayer.features.playsound.IPlaySoundContract
import com.vasilevkin.musicplayer.model.local.Song
import com.vasilevkin.musicplayer.player.MediaPlayer
import java.lang.ref.WeakReference


class PlaySoundPresenter(playSoundView: IPlaySoundContract.View) : IPlaySoundContract.Presenter {

    private val view = WeakReference(playSoundView)

    private val mediaPlayer = MediaPlayer()

    override fun deactivate() {}

    override fun getPlayer() = mediaPlayer

    override fun play(song: Song) = mediaPlayer.play(song)

    override fun releasePlayer() = mediaPlayer.releasePlayer()

    override fun setMediaSessionState(isActive: Boolean) {
        mediaPlayer.setMediaSessionState(isActive)
    }
}