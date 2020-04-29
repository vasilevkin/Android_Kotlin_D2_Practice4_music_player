package com.vasilevkin.musicplayer.features.foregroundservice

import android.R
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.media.session.MediaSessionManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.vasilevkin.musicplayer.model.local.Song
import com.vasilevkin.musicplayer.model.local.PlaybackStatus
import com.vasilevkin.musicplayer.utils.Broadcast_NETWORK_STATE
import com.vasilevkin.musicplayer.utils.Broadcast_PLAY_NEW_AUDIO
import com.vasilevkin.musicplayer.utils.StorageUtil
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

    //Handle incoming phone calls
    private var ongoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null

    //List of available Audio files
    private var songList: ArrayList<Song?>? = null
    private var songIndex = -1
    //an object of the currently playing audio
    private var activeSong: Song? = null

    val ACTION_PLAY = "com.valdioveliu.valdio.audioplayer.ACTION_PLAY"
    val ACTION_PAUSE = "com.valdioveliu.valdio.audioplayer.ACTION_PAUSE"
    val ACTION_PREVIOUS = "com.valdioveliu.valdio.audioplayer.ACTION_PREVIOUS"
    val ACTION_NEXT = "com.valdioveliu.valdio.audioplayer.ACTION_NEXT"
    val ACTION_STOP = "com.valdioveliu.valdio.audioplayer.ACTION_STOP"

    var duration = mediaPlayer?.duration

    //MediaSession
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var transportControls: MediaControllerCompat.TransportControls? = null

    //AudioPlayer notification ID
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        // Perform one-time setup procedures
        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener()
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver()
        //Listen for new Audio to play -- BroadcastReceiver
        registerPlayNewAudioBroadcastReceiver()

        registerWiFiStatusBroadcastReceiver()
    }
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
                    mediaPlayer?.start()
                mediaPlayer!!.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer!!.isPlaying)
                    mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                // Lost focus for a short time, but we have to stop playback.
                // We don't release the media player because playback is likely to resume
                if (mediaPlayer!!.isPlaying)
                    mediaPlayer?.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer!!.isPlaying)
                    mediaPlayer?.setVolume(0.1f, 0.1f)
        }
    }

    // Local player methods

    fun playPause() {
        if (mediaPlayer!!.isPlaying) {
            pauseMedia()
        } else {
            resumeMedia()
        }
    }

    fun getCurrentPosition() : Int? {
        return mediaPlayer?.currentPosition
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    // Private

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
            mediaPlayer?.setDataSource(activeSong?.soundUrl
                ?: "https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")
//            mediaPlayer?.setDataSource(mediaFilePath)
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
        duration = mediaPlayer?.duration
    }

    private fun stopMedia() {
        if (mediaPlayer == null) return
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer?.stop()
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer?.pause()
            resumePosition = mediaPlayer?.currentPosition ?: 0
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.seekTo(resumePosition)
            mediaPlayer!!.start()
        }
    }


    // Service Lifecycle Methods

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { //Load data from SharedPreferences
            val storage = StorageUtil(getApplicationContext())
            songList = storage.loadSongs()
            songIndex = storage.loadAudioIndex()
            if (songIndex !== -1 && songIndex < songList?.size!!) {
                //index is in a valid range
                activeSong = songList?.get(songIndex)
            } else {
                stopSelf()
            }
        } catch (e: NullPointerException) {
            stopSelf()
        }
        //Request audio focus
        if (requestAudioFocus() == false) { //Could not gain focus
            stopSelf()
        }
        if (mediaSessionManager == null) {
            try {
                initMediaSession()
                initMediaPlayer()
            } catch (e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(PlaybackStatus.PLAYING)
        }
        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent)
        return super.onStartCommand(intent, flags, startId)
    }
//    //The system calls this method when an activity, requests the service be started
//    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
//        try {
//            //An audio file is passed to the service through putExtra()
//            mediaFilePath = intent.extras!!.getString("media")
//        } catch (e: NullPointerException) {
//            stopSelf()
//        }
//        //Request audio focus
//        if (requestAudioFocus() == false) {
//            //Could not gain focus
//            stopSelf()
//        }
//        if (mediaFilePath != null && mediaFilePath !== "")
//            initMediaPlayer()
//        return super.onStartCommand(intent, flags, startId)
//    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer!!.release()
        }
        removeAudioFocus()

        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);
        unregisterReceiver(processConnectionStateChange)

        //clear cached playlist
        StorageUtil(getApplicationContext()).clearCachedAudioPlaylist()
    }

    // Dynamic BroadcastReceiver

    //Becoming noisy
    private val becomingNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia()
//            buildNotification(PlaybackStatus.PAUSED)
        }
    }

    private fun registerBecomingNoisyReceiver() {
        //register after getting audio focus
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, intentFilter)
    }

    //Handle incoming phone calls
    private fun callStateListener() {
        // Get the telephony manager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        //Starting listening for PhoneState changes
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String) {
                when (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    TelephonyManager.CALL_STATE_OFFHOOK,
                    TelephonyManager.CALL_STATE_RINGING ->
                        if (mediaPlayer != null) {
                        pauseMedia()
                        ongoingCall = true
                        }
                    TelephonyManager.CALL_STATE_IDLE ->
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                }
            }
        }
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager!!.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private val playNewAudio: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //Get the new media index from SharedPreferences
            songIndex = StorageUtil(applicationContext).loadAudioIndex()
            if (songIndex != -1 && songIndex < songList!!.size) {
                //index is in a valid range
                activeSong = songList!![songIndex]
            } else {
                stopSelf()
            }
            //A PLAY_NEW_AUDIO action received
            // reset mediaPlayer to play the new Audio
            stopMedia()
            mediaPlayer!!.reset()
            initMediaPlayer()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private val processConnectionStateChange: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.getAction();
            if (action.equals(Broadcast_NETWORK_STATE)) {
                var networkStatusString : String
                val networkInfo: NetworkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
                if (networkInfo.state == NetworkInfo.State.CONNECTED) {
                    networkStatusString = "CONNECTED"
                } else {
                    networkStatusString = "NOT-CONNECTED"
                }

                val handler = Handler(Looper.getMainLooper())
                handler.post(Runnable {
                    Toast.makeText(
                        this@MediaPlayerService.getApplicationContext(),
                        "Network state is changed" + networkStatusString,
                        Toast.LENGTH_SHORT
                    ).show()
                })
            }
        }
    }

    // BroadcastReceivers

    private fun registerPlayNewAudioBroadcastReceiver() {
        //Register playNewMedia receiver
        val filter = IntentFilter(Broadcast_PLAY_NEW_AUDIO)
        registerReceiver(playNewAudio, filter)
    }

    private fun registerWiFiStatusBroadcastReceiver() {

        val intentFilter = IntentFilter()
        intentFilter.addAction(Broadcast_NETWORK_STATE)
//            SUPPLICANT_CONNECTION_CHANGE_ACTION)
        registerReceiver(processConnectionStateChange, intentFilter)
    }

    // MediaSession

    @Throws(RemoteException::class)
    private fun initMediaSession() {
        if (mediaSessionManager != null)
            return  //mediaSessionManager exists

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        // Create a new MediaSession
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        //Get MediaSessions transport controls
        transportControls = mediaSession?.getController()?.getTransportControls()
        //set MediaSession -> ready to receive media commands
        mediaSession?.setActive(true)
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        //Set mediaSession's MetaData
        updateMetaData()
        // Attach Callback to receive MediaSession updates
        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            // Implement callbacks
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
                buildNotification(PlaybackStatus.PAUSED)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                skipToNext()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                skipToPrevious()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }

            override fun onStop() {
                super.onStop()
                removeNotification()
                //Stop the service
                stopSelf()
            }

            override fun onSeekTo(position: Long) {
                super.onSeekTo(position)
            }
        })
    }

    private fun updateMetaData() {
        val albumArt = BitmapFactory.decodeResource(resources, R.drawable.ic_media_play)
        //replace with medias albumArt
        // Update the current metadata
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, activeSong?.author ?: "Empty author")
//                .putString(MediaMetadata.METADATA_KEY_ALBUM, activeAudio?.album ?: "Empty album")
                .putString(MediaMetadata.METADATA_KEY_TITLE, activeSong?.trackTitle ?: "Empty track title")
                .build()
        )
    }

    private fun skipToNext() {
        if (songIndex == songList!!.size - 1) {
            //if last in playlist
            songIndex = 0
            activeSong = songList!![songIndex]
        } else {
            //get next in playlist
            activeSong = songList!![++songIndex]
        }
        //Update stored index
        StorageUtil(applicationContext).storeAudioIndex(songIndex)
        stopMedia()
        //reset mediaPlayer
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun skipToPrevious() {
        if (songIndex == 0) {
            //if first in playlist
            //set index to the last of audioList
            songIndex = songList!!.size - 1
            activeSong = songList!![songIndex]
        } else {
            //get previous in playlist
            activeSong = songList!![--songIndex]
        }
        //Update stored index
        StorageUtil(applicationContext).storeAudioIndex(songIndex)
        stopMedia()
        //reset mediaPlayer
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    // Notifications

    private fun buildNotification(playbackStatus: PlaybackStatus) {
        var notificationAction = R.drawable.ic_media_pause //needs to be initialized
        var play_pauseAction: PendingIntent? = null
        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus === PlaybackStatus.PLAYING) {
            notificationAction = R.drawable.ic_media_pause
            //create the pause action
            play_pauseAction = playbackAction(1)
        } else if (playbackStatus === PlaybackStatus.PAUSED) {
            notificationAction = R.drawable.ic_media_play
            //create the play action
            play_pauseAction = playbackAction(0)
        }
        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_btn_speak_now
        ) //replace with your own image
        // Create a new Notification
        val notificationBuilder = NotificationCompat.Builder(this)
            .setShowWhen(false) // Set the Notification style
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle() // Attach our MediaSession token
                    .setMediaSession(mediaSession?.getSessionToken())
                    // Show our playback controls in the compact notification view.
                    .setShowActionsInCompactView(0)
//                    .setShowActionsInCompactView(0, 1, 2)
            ) // Set the Notification color
            .setColor(resources.getColor(R.color.holo_purple)) // Set the large and small icons
            .setLargeIcon(largeIcon)
            .setSmallIcon(R.drawable.stat_sys_headset) // Set Notification content information
            .setContentText(activeSong?.author)
//            .setContentTitle(activeAudio?.album)
            .setContentInfo(activeSong?.trackTitle) // Add playback actions
//            .addAction(R.drawable.ic_media_previous, "previous", playbackAction(3))
            .addAction(notificationAction, "pause", play_pauseAction)
//            .addAction(R.drawable.ic_media_next, "next", playbackAction(2))
                as NotificationCompat.Builder
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            NOTIFICATION_ID,
            notificationBuilder.build()
        )
    }

    private fun removeNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }


    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackAction = Intent(this, MediaPlayerService::class.java)
        when (actionNumber) {
            0 -> {
                // Play
                playbackAction.action = ACTION_PLAY
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            1 -> {
                // Pause
                playbackAction.action = ACTION_PAUSE
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            2 -> {
                // Next track
                playbackAction.action = PlayerNotificationManager.ACTION_NEXT
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            3 -> {
                // Previous track
                playbackAction.action = PlayerNotificationManager.ACTION_PREVIOUS
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            else -> {
            }
        }
        return null
    }


    private fun handleIncomingActions(playbackAction: Intent?) {
        if (playbackAction == null || playbackAction.action == null) return
        val actionString = playbackAction.action
        if (actionString.equals(ACTION_PLAY, ignoreCase = true)) {
            transportControls?.play()
        } else if (actionString.equals(ACTION_PAUSE, ignoreCase = true)) {
            transportControls?.pause()
        } else if (actionString.equals(PlayerNotificationManager.ACTION_NEXT, ignoreCase = true)) {
            transportControls?.skipToNext()
        } else if (actionString.equals(PlayerNotificationManager.ACTION_PREVIOUS, ignoreCase = true)) {
            transportControls?.skipToPrevious()
        } else if (actionString.equals(ACTION_STOP, ignoreCase = true)) {
            transportControls?.stop()
        }
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