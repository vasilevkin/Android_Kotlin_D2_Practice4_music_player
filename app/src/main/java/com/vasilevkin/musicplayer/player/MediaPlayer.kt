package com.vasilevkin.musicplayer.player

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.vasilevkin.musicplayer.R


class MediaPlayer : IMediaPlayer {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var context: Context

    // interface IMediaPlayer

    override fun play(song: Song) {
        val userAgent = Util.getUserAgent(context, context.getString(R.string.app_name))

        val mediaSource = ExtractorMediaSource.Factory(DefaultDataSourceFactory(context, userAgent))
            .setExtractorsFactory(DefaultExtractorsFactory())
            .createMediaSource(Uri.parse(url))
        val uri = Uri.parse(song.soundUrl)

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
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setMediaSessionState(isActive: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    // Private methods

    private fun initializePlayer() {
        val renderersFactory = DefaultRenderersFactory(context)
        val trackSelector = DefaultTrackSelector()
        val loadControl = DefaultLoadControl()

        exoPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, loadControl)
    }

}