package com.vasilevkin.musicplayer.features.playsound.view

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.vasilevkin.musicplayer.R
import com.vasilevkin.musicplayer.features.playsound.IPlaySoundContract
import com.vasilevkin.musicplayer.features.playsound.presenter.PlaySoundPresenter


class MainActivity : AppCompatActivity(), IPlaySoundContract.View {

    companion object {
        const val SOUND_URL_EXTRA = "sound_url_extra"
    }

//    private lateinit var videoView: PlayerView

    private lateinit var presenter: IPlaySoundContract.Presenter
    private var playerView: PlayerView? = null
    private var player: SimpleExoPlayer? = null
    private var playWhenReady = true
    private var playbackPosition: Long = 0
    private var currentWindow = 0

    // Lifecycle methods

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        playerView = findViewById(R.id.ep_video_view)
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (Util.SDK_INT < 24 || player == null) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT < 24) {

            releasePlayer()
            //            presenter.releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= 24) {

            releasePlayer()
            //            presenter.releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.deactivate()
        presenter.setMediaSessionState(false)
    }

    // Private methods

    private fun initializePlayer() {

        presenter = PlaySoundPresenter(this)

        val soundUrl =
//            "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
            "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview128/v4/38/40/fb/3840fbc5-6273-d595-289e-2486153f074a/mzaf_5312791648997337345.plus.aac.p.m4a"
//            intent.getStringExtra(SOUND_URL_EXTRA)

//        videoView.player = presenter.getPlayer().getPlayer(this)
//        presenter.play(soundUrl)

        player = ExoPlayerFactory.newSimpleInstance(this)
        playerView!!.setPlayer(player)

//        val uri = Uri.parse("https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3")
        val uri =
            Uri.parse("https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview128/v4/38/40/fb/3840fbc5-6273-d595-289e-2486153f074a/mzaf_5312791648997337345.plus.aac.p.m4a")
//        val uri = Uri.parse(getString(R.string.media_url_mp3))
        val mediaSource = buildMediaSource(uri)
        player!!.setPlayWhenReady(playWhenReady)
        player!!.seekTo(currentWindow, playbackPosition)
        player!!.prepare(mediaSource, false, false)
    }

    private fun buildMediaSource(uri: Uri): MediaSource {
        val dataSourceFactory: DataSource.Factory =
            DefaultDataSourceFactory(this, "exoplayer-codelab")
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(uri)
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        playerView!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    private fun releasePlayer() {
        if (player != null) {
            playWhenReady = player!!.playWhenReady
            playbackPosition = player!!.currentPosition
            currentWindow = player!!.currentWindowIndex
            player!!.release()
            player = null
        }
    }

}
