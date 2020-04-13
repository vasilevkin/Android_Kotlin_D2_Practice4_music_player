package com.vasilevkin.musicplayer.features.foregroundservice

import android.app.Service
import android.content.Intent
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.os.Binder
import android.os.IBinder


class MediaPlayerService : Service(),
    MediaPlayer.OnCompletionListener,
    OnPreparedListener,
    MediaPlayer.OnErrorListener,
    OnSeekCompleteListener,
    MediaPlayer.OnInfoListener,
    OnBufferingUpdateListener,
    OnAudioFocusChangeListener {

    // Binder given to clients
    private val iBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder {
        return iBinder
    }

    override fun onBufferingUpdate(
        mp: MediaPlayer?,
        percent: Int
    ) { //Invoked indicating buffering status of
//a media resource being streamed over the network.
    }

    override fun onCompletion(mp: MediaPlayer?) { //Invoked when playback of a media source has completed.
    }

    //Handle errors
    override fun onError(
        mp: MediaPlayer?,
        what: Int,
        extra: Int
    ): Boolean { //Invoked when there has been an error during an asynchronous operation.
        return false
    }

    override fun onInfo(
        mp: MediaPlayer?,
        what: Int,
        extra: Int
    ): Boolean { //Invoked to communicate some info.
        return false
    }

    override fun onPrepared(mp: MediaPlayer?) { //Invoked when the media source is ready for playback.
    }

    override fun onSeekComplete(mp: MediaPlayer?) { //Invoked indicating the completion of a seek operation.
    }

    override fun onAudioFocusChange(focusChange: Int) { //Invoked when the audio focus of the system is updated.
    }

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
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