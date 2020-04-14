package com.vasilevkin.musicplayer.features.foregroundservice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.io.IOException


class MediaPlayerService : Service(),
    OnCompletionListener,
    OnPreparedListener,
    OnErrorListener,
    OnSeekCompleteListener,
    OnInfoListener,
    OnBufferingUpdateListener,
    OnAudioFocusChangeListener {

    // Binder given to clients
    private val iBinder: IBinder = LocalBinder()

    private var mediaPlayer: MediaPlayer? = null
    private var mediaFilePath: String? = null
    private var resumePosition = 0
    private var audioManager: AudioManager? = null


    override fun onBind(intent: Intent?): IBinder {
        return iBinder
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
        //Invoked indicating buffering status of a media resource being streamed over the network.
    }

    override fun onCompletion(mp: MediaPlayer?) {
        //Invoked when playback of a media source has completed.
        stopMedia()
        //stop the service
        stopSelf()
    }

    //Handle errors
    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        //Invoked when there has been an error during an asynchronous operation
        when (what) {
            MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $extra"
            )
            MEDIA_ERROR_SERVER_DIED -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR SERVER DIED $extra"
            )
            MEDIA_ERROR_UNKNOWN -> Log.d(
                "MediaPlayer Error",
                "MEDIA ERROR UNKNOWN $extra"
            )
        }
        return false
    }

    override fun onPrepared(mp: MediaPlayer?) {
        //Invoked when the media source is ready for playback.
        playMedia()
    }

    override fun onInfo(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        //Invoked to communicate some info.
        return false
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
        //Invoked indicating the completion of a seek operation.
    }

    override fun onAudioFocusChange(focusState: Int) {
        //Invoked when the audio focus of the system is updated.
        when (focusState) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // resume playback
                if (mediaPlayer == null)
                    initMediaPlayer()
                else if (!mediaPlayer!!.isPlaying)
                    mediaPlayer!!.start()
                mediaPlayer!!.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer!!.isPlaying)
                    mediaPlayer!!.stop()
                mediaPlayer!!.release()
                mediaPlayer = null
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                // Lost focus for a short time, but we have to stop playback.
                // We don't release the media player because playback is likely to resume
                if (mediaPlayer!!.isPlaying)
                    mediaPlayer!!.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer!!.isPlaying)
                    mediaPlayer!!.setVolume(0.1f, 0.1f)
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = audioManager!!.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //Focus gained
            true
        } else false
        //Could not gain focus
    }

    private fun removeAudioFocus(): Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager!!.abandonAudioFocus(this)
    }


    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }


    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        //Set up MediaPlayer event listeners
        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.setOnPreparedListener(this)
        mediaPlayer?.setOnBufferingUpdateListener(this)
        mediaPlayer?.setOnSeekCompleteListener(this)
        mediaPlayer?.setOnInfoListener(this)
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer?.reset()
        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
        try { // Set the data source to the mediaFile location
            mediaPlayer?.setDataSource(mediaFilePath)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }
        mediaPlayer?.prepareAsync()
    }


    private fun playMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer == null) return
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.stop()
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
            resumePosition = mediaPlayer!!.currentPosition
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.seekTo(resumePosition)
            mediaPlayer!!.start()
        }
    }


    // Service Lifecycle Methods

    //The system calls this method when an activity, requests the service be started
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        try {
            //An audio file is passed to the service through putExtra()
            mediaFilePath = intent.extras!!.getString("media")
        } catch (e: NullPointerException) {
            stopSelf()
        }
        //Request audio focus
        if (requestAudioFocus() == false) {
            //Could not gain focus
            stopSelf()
        }
        if (mediaFilePath != null && mediaFilePath !== "")
            initMediaPlayer()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer!!.release()
        }
        removeAudioFocus()
    }
    
}

//class MusicForegroundService : Service() {

//    override fun onBind(intent: Intent?): IBinder? {
//        return null
////        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
//        val input = intent.getStringExtra("inputExtra")
//        createNotificationChannel()
//        val notificationIntent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(
//            this,
//            0, notificationIntent, 0
//        )
//        val notification: Notification =
//            Builder(this, CHANNEL_ID)
//                .setContentTitle("Foreground Service")
//                .setContentText(input)
//                .setSmallIcon(R.drawable.ic_stat_name)
//                .setContentIntent(pendingIntent)
//                .build()
//        startForeground(1, notification)
//        //do heavy work on a background thread
////stopSelf();
//        return START_NOT_STICKY
//    }
//
//
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val serviceChannel = NotificationChannel(
//                com.wave.foregroundservice.ForegroundService.CHANNEL_ID,
//                "Foreground Service Channel",
//                NotificationManager.IMPORTANCE_DEFAULT
//            )
//            val manager = getSystemService(
//                NotificationManager::class.java
//            )
//            manager.createNotificationChannel(serviceChannel)
//        }
//    }
//
//}