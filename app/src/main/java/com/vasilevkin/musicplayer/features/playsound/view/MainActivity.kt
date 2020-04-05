package com.vasilevkin.musicplayer.features.playsound.view

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.exoplayer2.ui.PlayerView
import com.vasilevkin.musicplayer.R
import com.vasilevkin.musicplayer.features.playsound.IPlaySoundContract
import com.vasilevkin.musicplayer.features.playsound.presenter.PlaySoundPresenter


class MainActivity : AppCompatActivity(), IPlaySoundContract.View {

    companion object {
        const val SOUND_URL_EXTRA = "sound_url_extra"
    }

    private lateinit var videoView: PlayerView

    private lateinit var presenter: IPlaySoundContract.Presenter

    // Lifecycle methods

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            presenter.releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            presenter.releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.deactivate()
        presenter.setMediaSessionState(false)
    }

    // Private methods

    private fun init() {
        presenter = PlaySoundPresenter(this)

        val videoUrl = intent.getStringExtra(SOUND_URL_EXTRA)

        videoView = findViewById(R.id.player_seekbar)
        videoView.player = presenter.getPlayer().getPlayer(this)
        presenter.play(videoUrl)
    }
}
