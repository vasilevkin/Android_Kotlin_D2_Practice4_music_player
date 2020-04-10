package com.vasilevkin.musicplayer.player

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.vasilevkin.musicplayer.R
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.vasilevkin.musicplayer.model.local.Song


class MediaPlayer : IMediaPlayer {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var context: Context

    // interface IMediaPlayer

    override fun play(song: Song) {
        val userAgent = Util.getUserAgent(context, context.getString(R.string.app_name))

//        val mediaSource = ExtractorMediaSource.Factory(DefaultDataSourceFactory(context, userAgent))
//            .setExtractorsFactory(DefaultExtractorsFactory())
//            .createMediaSource(Uri.parse(url))

//        val mediaSource: MediaSource = HlsMediaSource(
//            Uri.parse("https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"),
//            mediaDataSourceFactory, mainHandler, null
//        )
//
//        AudioPlayer.player.prepare(mediaSource)
//        AudioPlayer.player.setPlayWhenReady(true)

        val uri = Uri.parse(song.soundUrl)
        val mediaSource: MediaSource = this!!.buildMediaSource(uri)!!



        exoPlayer.prepare(mediaSource)

        exoPlayer.playWhenReady = true
    }

    override fun getPlayer(context: Context): ExoPlayer {
        this.context = context
        initializePlayer()
//        initializeMediaSession()
        return exoPlayer
    }

    override fun releasePlayer() {
        exoPlayer.stop()
        exoPlayer.release()
    }

    override fun setMediaSessionState(isActive: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    // Private methods

    private fun initializePlayer() {
        val renderersFactory = DefaultRenderersFactory(context)
        val trackSelector = DefaultTrackSelector()
        val loadControl = DefaultLoadControl()

        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector)
    }

    private fun buildMediaSource(uri: Uri): MediaSource? {
        val dataSourceFactory: DataSource.Factory =
            DefaultDataSourceFactory(context, "exoplayer-codelab")
        return ProgressiveMediaSource
            .Factory(dataSourceFactory)
            .createMediaSource(uri)
    }

}