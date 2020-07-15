package com.vasilevkin.musicplayer.features.foregroundservice

import android.R
import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.AudioManager.*
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.media.audiofx.Equalizer
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media.AudioManagerCompat.AUDIOFOCUS_GAIN
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.vasilevkin.musicplayer.features.playsound.view.MainActivity
import com.vasilevkin.musicplayer.model.local.Song
import com.vasilevkin.musicplayer.model.local.PlaybackStatus
import com.vasilevkin.musicplayer.utils.*
import java.io.IOException


class MediaPlayerService : Service(),
    OnCompletionListener,
    OnPreparedListener,
    OnErrorListener,
//    OnSeekCompleteListener,
//    OnInfoListener,
//    OnBufferingUpdateListener,
    OnAudioFocusChangeListener {

    companion object {
        private const val MIN_INITIAL_DURATION = 30
        private const val PROGRESS_UPDATE_INTERVAL = 1000L
        private const val MAX_CLICK_DURATION = 700L
        private const val FAST_FORWARD_SKIP_MS = 10000
        private const val NOTIFICATION_CHANNEL = "music_player_channel"
        private const val NOTIFICATION_ID = 78    // just a random number

        var mCurrSong: Song? = null
        var mCurrSongCover: Bitmap? = null
        var mEqualizer: Equalizer? = null
//        private var mHeadsetPlugReceiver = HeadsetPlugReceiver()
        private var mPlayer: MediaPlayer? = null
        private var mPlayedSongIndexes = ArrayList<Int>()
        private var mProgressHandler = Handler()
        private var mSleepTimer: CountDownTimer? = null
        private var mSongs = ArrayList<Song>()
        private var mAudioManager: AudioManager? = null
        private var mCoverArtHeight = 0
        private var mOreoFocusHandler: OreoAudioFocusHandler? = null

        private var mWasPlayingAtFocusLost = false
        private var mPlayOnPrepare = true
        private var mIsThirdPartyIntent = false
        private var mIntentUri: Uri? = null
        private var mMediaSession: MediaSessionCompat? = null
        private var mIsServiceInitialized = false
        private var mPrevAudioFocusState = 0

        fun getIsPlaying() = mPlayer?.isPlaying == true
    }

    private var mClicksCnt = 0
    private val mRemoteControlHandler = Handler()
    private val mRunnable = Runnable {
        if (mClicksCnt == 0) {
            return@Runnable
        }

        when (mClicksCnt) {
            1 -> handlePlayPause()
            2 -> handleNext()
            else -> handlePrevious()
        }
        mClicksCnt = 0
    }

    override fun onCreate() {
        super.onCreate()
        mCoverArtHeight = 220
//            resources.getDimension(R.dimen.top_art_height).toInt()
        mMediaSession = MediaSessionCompat(this, "MusicService")
        mMediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)
        mMediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                handleMediaButton(mediaButtonEvent)
                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        })

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (isOreoPlus()) {
            mOreoFocusHandler = OreoAudioFocusHandler(applicationContext)
        }

//        if (!hasPermission(PERMISSION_WRITE_STORAGE)) {
//            EventBus.getDefault().post(Events.NoStoragePermission())
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyPlayer()
//        SongsDatabase.destroyInstance()
        mMediaSession?.isActive = false
        mSleepTimer?.cancel()
//        config.sleepInTS = 0L
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
//        if (!hasPermission(PERMISSION_WRITE_STORAGE)) {
//            return START_NOT_STICKY
//        }

        val action = intent.action
        if (isOreoPlus() && action != NEXT && action != PREVIOUS && action != PLAYPAUSE) {
            setupFakeNotification()
        }

        when (action) {
            INIT -> handleInit()
            INIT_PATH -> handleInitPath(intent)
            SETUP -> handleSetup()
            PREVIOUS -> handlePrevious()
            PAUSE -> pauseSong()
            PLAYPAUSE -> handlePlayPause()
            NEXT -> handleNext()
            RESET -> handleReset()
            PLAYPOS -> playSong(intent)
            EDIT -> handleEdit(intent)
            FINISH -> handleFinish()
            REFRESH_LIST -> handleRefreshList(intent)
            SET_PROGRESS -> handleSetProgress(intent)
//            SET_EQUALIZER -> handleSetEqualizer(intent)
            SKIP_BACKWARD -> skip(false)
            SKIP_FORWARD -> skip(true)
            REMOVE_CURRENT_SONG -> handleRemoveCurrentSong()
            REMOVE_SONG_IDS -> handleRemoveSongIDS(intent)
//            START_SLEEP_TIMER -> startSleepTimer()
//            STOP_SLEEP_TIMER -> stopSleepTimer()
            BROADCAST_STATUS -> broadcastPlayerStatus()
        }

        MediaButtonReceiver.handleIntent(mMediaSession!!, intent)
        setupNotification()
        return START_NOT_STICKY
    }

    private fun initService() {
        mSongs.clear()
        mPlayedSongIndexes = ArrayList()
        mCurrSong = null
        if (mIsThirdPartyIntent && mIntentUri != null) {
//            val path = getRealPathFromURI(mIntentUri!!) ?: ""
//            val song = RoomHelper(this).getSongFromPath(path)
//            if (song != null) {
//                mSongs.add(song)
//            }
        } else {
            getSortedSongs()
        }

        mWasPlayingAtFocusLost = false
        initMediaPlayerIfNeeded()
        setupNotification()
        mIsServiceInitialized = true
    }

    private fun handleInit() {
        mIsThirdPartyIntent = false
        ensureBackgroundThread {
            if (!mIsServiceInitialized) {
                initService()
            }
            initSongs()
        }
    }

    private fun handleInitPath(intent: Intent) {
        mIsThirdPartyIntent = true
        if (mIntentUri != intent.data) {
            mIntentUri = intent.data
            initService()
            initSongs()
        } else {
            updateUI()
        }
    }

    private fun handleSetup() {
        mPlayOnPrepare = true
        setupNextSong()
    }

    private fun handlePrevious() {
        mPlayOnPrepare = true
        playPreviousSong()
    }

    private fun handlePlayPause() {
        mPlayOnPrepare = true
        if (getIsPlaying()) {
            pauseSong()
        } else {
            resumeSong()
        }
    }

    private fun handleNext() {
        mPlayOnPrepare = true
        setupNextSong()
    }

    private fun handleReset() {
        if (mPlayedSongIndexes.size - 1 != -1) {
            mPlayOnPrepare = true
            setSong(mPlayedSongIndexes[mPlayedSongIndexes.size - 1], false)
        }
    }

    private fun handleEdit(intent: Intent) {
        mCurrSong = intent.getSerializableExtra(EDITED_SONG) as Song
        songChanged(mCurrSong)
    }

    private fun handleFinish() {
//        EventBus.getDefault().post(Events.ProgressUpdated(0))
        destroyPlayer()
    }

    private fun handleRefreshList(intent: Intent) {
        mSongs.clear()
        ensureBackgroundThread {
            getSortedSongs()
//            EventBus.getDefault().post(Events.PlaylistUpdated(mSongs))

            if (intent.getBooleanExtra(CALL_SETUP_AFTER, false)) {
                mPlayOnPrepare = false
                setupNextSong()
            }

        }
    }

    private fun handleSetProgress(intent: Intent) {
        if (mPlayer != null) {
            val progress = intent.getIntExtra(PROGRESS, mPlayer!!.currentPosition / 1000)
            updateProgress(progress)
        }
    }

//    private fun handleSetEqualizer(intent: Intent) {
//        if (intent.extras?.containsKey(EQUALIZER) == true) {
//            val presetID = intent.extras?.getInt(EQUALIZER) ?: 0
//            if (mEqualizer != null) {
//                setPreset(presetID)
//            }
//        }
//    }

    private fun handleRemoveCurrentSong() {
        pauseSong()
        mCurrSong = null
        songChanged(null)
    }

    private fun handleRemoveSongIDS(intent: Intent) {
        val ids = intent.getIntegerArrayListExtra(SONG_IDS)
        val songsToRemove = ArrayList<Song>()
//        mSongs.sortedDescending().forEach {
//            if (ids.contains(it.path.hashCode())) {
//                songsToRemove.add(it)
//            }
//        }
        mSongs.removeAll(songsToRemove)
    }

    private fun setupSong() {
        if (mIsThirdPartyIntent) {
            initMediaPlayerIfNeeded()

            try {
                mPlayer!!.apply {
                    reset()
                    setDataSource(applicationContext, mIntentUri!!)
                    setOnPreparedListener(null)
                    prepare()
                    start()
                }
                requestAudioFocus()

                val song = mSongs.first()
                mSongs.clear()
                mSongs.add(song)
                mCurrSong = song
                updateUI()
            } catch (ignored: Exception) {
            }
        } else {
            mPlayOnPrepare = false
            setupNextSong()
        }
    }

    private fun initSongs() {
        if (mCurrSong == null) {
            setupSong()
        }
        updateUI()
    }

    private fun updateUI() {
        if (mPlayer != null) {
//            EventBus.getDefault().post(Events.PlaylistUpdated(mSongs))
//            mCurrSongCover = getAlbumImage(mCurrSong).first
            broadcastSongChange(mCurrSong)

            val secs = mPlayer!!.currentPosition / 1000
//            EventBus.getDefault().post(Events.ProgressUpdated(secs))
        }
        songStateChanged(getIsPlaying())
    }

    private fun initMediaPlayerIfNeeded() {
        if (mPlayer != null) {
            return
        }

        mPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(STREAM_MUSIC)
            setOnPreparedListener(this@MediaPlayerService)
            setOnCompletionListener(this@MediaPlayerService)
            setOnErrorListener(this@MediaPlayerService)
        }
//        setupEqualizer()
    }

    private fun getAllDeviceSongs() {
//        val ignoredPaths = config.ignoredPaths
//        val uri = Audio.Media.EXTERNAL_CONTENT_URI
//        val projection = arrayOf(
//            Audio.Media.DURATION,
//            Audio.Media.DATA
//        )
//
//        val paths = ArrayList<String>()
//
//        queryCursor(uri, projection) { cursor ->
//            val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
//            if (duration > MIN_INITIAL_DURATION) {
//                val path = cursor.getStringValue(Audio.Media.DATA)
//                if (!ignoredPaths.contains(path) && !path.doesThisOrParentHaveNoMedia()) {
//                    paths.add(path)
//                }
//            }
//        }
//
//        val storedAllSongPaths = songsDAO.getSongsFromPlaylist(ALL_SONGS_PLAYLIST_ID).map { it.path }
//        paths.removeAll(storedAllSongPaths)
//        RoomHelper(this).addSongsToPlaylist(paths)
    }

    private fun getSortedSongs() {
//        if (config.currentPlaylist == ALL_SONGS_PLAYLIST_ID) {
//            getAllDeviceSongs()
//        }
//
//        mSongs = getPlaylistSongs(config.currentPlaylist)
//        Song.sorting = config.sorting
//        try {
//            mSongs.sort()
//        } catch (ignored: Exception) {
//        }
    }

//    private fun setupEqualizer() {
//        try {
//            mEqualizer = Equalizer(1, mPlayer!!.audioSessionId)
//        } catch (e: Exception) {
//            showErrorToast(e)
//            mEqualizer?.enabled = true
//            setPreset(config.equalizer)
//        }
//    }

//    private fun setPreset(id: Int) {
//        try {
//            mEqualizer?.usePreset(id.toShort())
//        } catch (ignored: IllegalArgumentException) {
//        }
//    }

    @SuppressLint("NewApi")
    private fun setupNotification() {
        val title = mCurrSong?.trackTitle ?: ""
        val artist = mCurrSong?.author ?: ""
        val playPauseIcon = if (getIsPlaying()) R.drawable.ic_media_pause
//        R.drawable.ic_pause_vector
        else R.drawable.ic_media_play
//            R.drawable.ic_play_vector

        var notifWhen = 0L
        var showWhen = false
        var usesChronometer = false
        var ongoing = false
        if (getIsPlaying()) {
            notifWhen = System.currentTimeMillis() - (mPlayer?.currentPosition ?: 0)
            showWhen = true
            usesChronometer = true
            ongoing = true
        }

        if (isOreoPlus()) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val name = "App name"
//                resources.getString(R.string.app_name)

//            val name = resources.getString(R.string.app_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            NotificationChannel(NOTIFICATION_CHANNEL, name, importance).apply {
                enableLights(false)
                enableVibration(false)
                notificationManager.createNotificationChannel(this)
            }
        }

//        if (mCurrSongCover?.isRecycled == true) {
//            mCurrSongCover = resources.getColoredBitmap(R.drawable.ic_headset, config.textColor)
//        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.stat_sys_headset)
            .setLargeIcon(mCurrSongCover)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setWhen(notifWhen)
            .setShowWhen(showWhen)
            .setUsesChronometer(usesChronometer)
            .setContentIntent(getContentIntent())
            .setOngoing(ongoing)
            .setChannelId(NOTIFICATION_CHANNEL)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0
//                    , 1, 2
                )
                .setMediaSession(mMediaSession?.sessionToken))
//            .addAction(R.drawable.ic_previous_vector, getString(R.string.previous), getIntent(PREVIOUS))
            .addAction(playPauseIcon,
                "Play / Pause"
//                getString(R.string.playpause)
                , getIntent(PLAYPAUSE))
//            .addAction(R.drawable.ic_next_vector, getString(R.string.next), getIntent(NEXT))

        startForeground(NOTIFICATION_ID, notification.build())

        // delay foreground state updating a bit, so the notification can be swiped away properly after initial display
        Handler(Looper.getMainLooper()).postDelayed({
            if (!getIsPlaying()) {
                stopForeground(false)
            }
        }, 200L)

        val playbackState = if (getIsPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        try {
            mMediaSession!!.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(playbackState, PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build())
        } catch (ignored: IllegalStateException) {
        }
    }

    private fun getContentIntent(): PendingIntent {
        val contentIntent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, contentIntent, 0)
    }

    private fun getIntent(action: String): PendingIntent {
        val intent = Intent(this, ControlActionsListener::class.java)
        intent.action = action
        return PendingIntent.getBroadcast(applicationContext, 0, intent, 0)
    }

    // on Android 8+ the service is launched with startForegroundService(), so startForeground must be called within a few secs
    @SuppressLint("NewApi")
    private fun setupFakeNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = "Fake app name"
        val importance = NotificationManager.IMPORTANCE_LOW
        NotificationChannel(NOTIFICATION_CHANNEL, name, importance).apply {
            enableLights(false)
            enableVibration(false)
            notificationManager.createNotificationChannel(this)
        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(R.drawable.stat_sys_headset
//                ic_headset_small
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setChannelId(NOTIFICATION_CHANNEL)
            .setCategory(Notification.CATEGORY_SERVICE)

        startForeground(NOTIFICATION_ID, notification.build())
    }

    private fun getNewSongId(): Int {
//        return if (config.isShuffleEnabled) {
//            val cnt = mSongs.size
//            when (cnt) {
//                0 -> -1
//                1 -> 0
//                else -> {
//                    val random = Random()
//                    var newSongIndex = random.nextInt(cnt)
//                    while (mPlayedSongIndexes.contains(newSongIndex)) {
//                        newSongIndex = random.nextInt(cnt)
//                    }
//                    newSongIndex
//                }
//            }
//        } else {
//            if (mPlayedSongIndexes.isEmpty()) {
                return 0
//            }
//
//            val lastIndex = mPlayedSongIndexes[mPlayedSongIndexes.size - 1]
//            (lastIndex + 1) % Math.max(mSongs.size, 1)
//        }
    }

    private fun playPreviousSong() {
        if (mSongs.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        // play the previous song if we are less than 5 secs into the song, else restart
        // remove the latest song from the list
        if (mPlayedSongIndexes.size > 1 && mPlayer!!.currentPosition < 5000) {
            mPlayedSongIndexes.removeAt(mPlayedSongIndexes.size - 1)
            setSong(mPlayedSongIndexes[mPlayedSongIndexes.size - 1], false)
        } else {
            restartSong()
        }
    }

    private fun pauseSong() {
        initMediaPlayerIfNeeded()

        mPlayer!!.pause()
        songStateChanged(false)
    }

    private fun resumeSong() {
        if (mSongs.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        if (mCurrSong == null) {
            setupNextSong()
        } else {
            mPlayer!!.start()
            requestAudioFocus()
        }

        songStateChanged(true)
    }

    private fun setupNextSong() {
        if (mIsThirdPartyIntent) {
            setupSong()
        } else {
            setSong(getNewSongId(), true)
        }
    }

    private fun restartSong() {
        val newSongIndex = if (mPlayedSongIndexes.isEmpty()) 0 else mPlayedSongIndexes[mPlayedSongIndexes.size - 1]
        setSong(newSongIndex, false)
    }

    private fun playSong(intent: Intent) {
        if (mIsThirdPartyIntent) {
            setupSong()
        } else {
            mPlayOnPrepare = true
            val pos = intent.getIntExtra(SONG_POS, 0)
            setSong(pos, true)
        }
        mMediaSession?.isActive = true
    }

    private fun setSong(songIndex: Int, addNewSongToHistory: Boolean) {
//        if (mSongs.isEmpty()) {
//            handleEmptyPlaylist()
//            return
//        }
//
//        initMediaPlayerIfNeeded()
//
//        mPlayer!!.reset()
//        if (addNewSongToHistory) {
//            mPlayedSongIndexes.add(songIndex)
//            if (mPlayedSongIndexes.size >= mSongs.size) {
//                mPlayedSongIndexes.clear()
//            }
//        }
//
//        mCurrSong = mSongs.getOrNull(Math.min(songIndex, mSongs.size - 1)) ?: return
//
//        try {
//            val trackUri = if (mCurrSong!!.mediaStoreId == 0L) {
//                Uri.fromFile(File(mCurrSong!!.path))
//            } else {
//                ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, mCurrSong!!.mediaStoreId)
//            }
//            mPlayer!!.setDataSource(applicationContext, trackUri)
//            mPlayer!!.prepareAsync()
//            songChanged(mCurrSong)
//        } catch (ignored: Exception) {
//        }
    }

    private fun handleEmptyPlaylist() {
        mPlayer?.pause()
        abandonAudioFocus()
        mCurrSong = null
        songChanged(null)
        songStateChanged(false)

        if (!mIsServiceInitialized) {
            handleInit()
        }
    }

    override fun onBind(intent: Intent) = null

    override fun onCompletion(mp: MediaPlayer) {
//        if (!config.autoplay) {
//            return
//        }
//
//        if (config.repeatSong) {
//            restartSong()
//        } else if (mPlayer!!.currentPosition > 0) {
//            mPlayer!!.reset()
//            setupNextSong()
//        }
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        mPlayer!!.reset()
        return false
    }

    override fun onPrepared(mp: MediaPlayer) {
        if (mPlayOnPrepare) {
            mp.start()
            requestAudioFocus()
        }
        songStateChanged(getIsPlaying())
        setupNotification()
    }

    private fun songChanged(song: Song?) {
//        val albumImage = getAlbumImage(song)
//        mCurrSongCover = albumImage.first
        broadcastSongChange(song)

//        val lockScreenImage = if (albumImage.second) albumImage.first else null
//        val metadata = MediaMetadataCompat.Builder()
//            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, lockScreenImage)
//            .build()

//        mMediaSession?.setMetadata(metadata)
    }

    private fun broadcastSongChange(song: Song?) {
        Handler(Looper.getMainLooper()).post {
//            broadcastUpdateWidgetSong(song)
//            EventBus.getDefault().post(Events.SongChanged(song))
        }
    }

    private fun broadcastSongStateChange(isPlaying: Boolean) {
//        broadcastUpdateWidgetSongState(isPlaying)
//        EventBus.getDefault().post(Events.SongStateChanged(isPlaying))
    }

    // do not just return the album cover, but also a boolean to indicate if it a real cover, or just the placeholder
//    private fun getAlbumImage(song: Song?): Pair<Bitmap, Boolean> {
//        if (File(song?.path ?: "").exists()) {
//            try {
//                val mediaMetadataRetriever = MediaMetadataRetriever()
//                mediaMetadataRetriever.setDataSource(song!!.path)
//                val rawArt = mediaMetadataRetriever.embeddedPicture
//                if (rawArt != null) {
//                    val options = BitmapFactory.Options()
//                    val bitmap = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
//                    if (bitmap != null) {
//                        val resultBitmap = if (bitmap.height > mCoverArtHeight * 2) {
//                            val ratio = bitmap.width / bitmap.height.toFloat()
//                            Bitmap.createScaledBitmap(bitmap, (mCoverArtHeight * ratio).toInt(), mCoverArtHeight, false)
//                        } else {
//                            bitmap
//                        }
//                        return Pair(resultBitmap, true)
//                    }
//                }
//
//                val songParentDirectory = File(song.path).parent.trimEnd('/')
//                val albumArtFiles = arrayListOf("folder.jpg", "albumart.jpg", "cover.jpg")
//                albumArtFiles.forEach {
//                    val albumArtFilePath = "$songParentDirectory/$it"
//                    if (File(albumArtFilePath).exists()) {
//                        val bitmap = BitmapFactory.decodeFile(albumArtFilePath)
//                        if (bitmap != null) {
//                            val resultBitmap = if (bitmap.height > mCoverArtHeight * 2) {
//                                val ratio = bitmap.width / bitmap.height.toFloat()
//                                Bitmap.createScaledBitmap(bitmap, (mCoverArtHeight * ratio).toInt(), mCoverArtHeight, false)
//                            } else {
//                                bitmap
//                            }
//                            return Pair(resultBitmap, true)
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//            }
//        }
//
//        return Pair(resources.getColoredBitmap(R.drawable.ic_headset, config.textColor), false)
//    }

    private fun destroyPlayer() {
        mPlayer?.stop()
        mPlayer?.release()
        mPlayer = null

        songStateChanged(false)
        songChanged(null)

        mEqualizer?.release()

        stopForeground(true)
        stopSelf()
        mIsThirdPartyIntent = false
        mIsServiceInitialized = false
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        if (isOreoPlus()) {
            mOreoFocusHandler?.requestAudioFocus(this)
        } else {
            mAudioManager?.requestAudioFocus(this, STREAM_MUSIC, AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (isOreoPlus()) {
            mOreoFocusHandler?.abandonAudioFocus()
        } else {
            mAudioManager?.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AUDIOFOCUS_GAIN -> audioFocusGained()
            AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> duckAudio()
            AUDIOFOCUS_LOSS, AUDIOFOCUS_LOSS_TRANSIENT -> audioFocusLost()
        }
        mPrevAudioFocusState = focusChange
    }

    private fun audioFocusLost() {
        if (getIsPlaying()) {
            mWasPlayingAtFocusLost = true
            pauseSong()
        } else {
            mWasPlayingAtFocusLost = false
        }
    }

    private fun audioFocusGained() {
        if (mWasPlayingAtFocusLost) {
            if (mPrevAudioFocusState == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                unduckAudio()
            } else {
                resumeSong()
            }
        }

        mWasPlayingAtFocusLost = false
    }

    private fun duckAudio() {
        mPlayer?.setVolume(0.3f, 0.3f)
        mWasPlayingAtFocusLost = getIsPlaying()
    }

    private fun unduckAudio() {
        mPlayer?.setVolume(1f, 1f)
    }

    private fun updateProgress(progress: Int) {
        mPlayer!!.seekTo(progress * 1000)
        resumeSong()
    }

    private fun songStateChanged(isPlaying: Boolean) {
        handleProgressHandler(isPlaying)
        setupNotification()
        mMediaSession?.isActive = isPlaying
        broadcastSongStateChange(isPlaying)

        if (isPlaying) {
            val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            filter.addAction(ACTION_AUDIO_BECOMING_NOISY)
//            registerReceiver(mHeadsetPlugReceiver, filter)
        } else {
            try {
//                unregisterReceiver(mHeadsetPlugReceiver)
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    private fun handleProgressHandler(isPlaying: Boolean) {
        if (isPlaying) {
            mProgressHandler.post(object : Runnable {
                override fun run() {
                    val secs = mPlayer!!.currentPosition / 1000
//                    EventBus.getDefault().post(Events.ProgressUpdated(secs))
                    mProgressHandler.removeCallbacksAndMessages(null)
                    mProgressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
                }
            })
        } else {
            mProgressHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun skip(forward: Boolean) {
        val curr = mPlayer?.currentPosition ?: return
        val newProgress = if (forward) curr + FAST_FORWARD_SKIP_MS else curr - FAST_FORWARD_SKIP_MS
        mPlayer!!.seekTo(newProgress)
        resumeSong()
    }

//    private fun startSleepTimer() {
//        val millisInFuture = config.sleepInTS - System.currentTimeMillis() + 1000L
//        mSleepTimer?.cancel()
//        mSleepTimer = object : CountDownTimer(millisInFuture, 1000) {
//            override fun onTick(millisUntilFinished: Long) {
//                val seconds = (millisUntilFinished / 1000).toInt()
//                EventBus.getDefault().post(Events.SleepTimerChanged(seconds))
//            }
//
//            override fun onFinish() {
//                EventBus.getDefault().post(Events.SleepTimerChanged(0))
//                config.sleepInTS = 0
//                sendIntent(FINISH)
//            }
//        }
//        mSleepTimer?.start()
//    }
//
//    private fun stopSleepTimer() {
//        config.sleepInTS = 0
//        mSleepTimer?.cancel()
//    }

    // used at updating the widget at create or resize
    private fun broadcastPlayerStatus() {
        broadcastSongStateChange(mPlayer?.isPlaying ?: false)
        broadcastSongChange(mCurrSong)
    }

    private fun handleMediaButton(mediaButtonEvent: Intent) {
//        if (mediaButtonEvent.action == Intent.ACTION_MEDIA_BUTTON) {
//            val swapPrevNext = config.swapPrevNext
//            val event = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
//            if (event.action == KeyEvent.ACTION_UP) {
//                when (event.keyCode) {
//                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> handlePlayPause()
//                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (swapPrevNext) handleNext() else handlePrevious()
//                    KeyEvent.KEYCODE_MEDIA_NEXT -> if (swapPrevNext) handlePrevious() else handleNext()
//                    KeyEvent.KEYCODE_HEADSETHOOK -> {
//                        mClicksCnt++
//
//                        mRemoteControlHandler.removeCallbacks(mRunnable)
//                        if (mClicksCnt >= 3) {
//                            mRemoteControlHandler.post(mRunnable)
//                        } else {
//                            mRemoteControlHandler.postDelayed(mRunnable, MAX_CLICK_DURATION)
//                        }
//                    }
//                }
//            }
//        }
    }
}




//class MediaPlayerService : Service(),
//    OnCompletionListener,
//    OnPreparedListener,
//    OnErrorListener,
//    OnSeekCompleteListener,
//    OnInfoListener,
//    OnBufferingUpdateListener,
//    OnAudioFocusChangeListener {
//
//    // Binder given to clients
//    private val iBinder: IBinder = LocalBinder()
//
//    private var mediaPlayer: MediaPlayer? = null
//    private var mediaFilePath: String? = null
//    private var resumePosition = 0
//    private var audioManager: AudioManager? = null
//
//    //Handle incoming phone calls
//    private var ongoingCall = false
//    private var phoneStateListener: PhoneStateListener? = null
//    private var telephonyManager: TelephonyManager? = null
//
//    //List of available Audio files
//    private var songList: ArrayList<Song?>? = null
//    private var songIndex = -1
//    //an object of the currently playing audio
//    private var activeSong: Song? = null
//
//    val ACTION_PLAY = "com.valdioveliu.valdio.audioplayer.ACTION_PLAY"
//    val ACTION_PAUSE = "com.valdioveliu.valdio.audioplayer.ACTION_PAUSE"
//    val ACTION_PREVIOUS = "com.valdioveliu.valdio.audioplayer.ACTION_PREVIOUS"
//    val ACTION_NEXT = "com.valdioveliu.valdio.audioplayer.ACTION_NEXT"
//    val ACTION_STOP = "com.valdioveliu.valdio.audioplayer.ACTION_STOP"
//
//    var duration = mediaPlayer?.duration
//
//    //MediaSession
//    private var mediaSessionManager: MediaSessionManager? = null
//    private var mediaSession: MediaSessionCompat? = null
//    private var transportControls: MediaControllerCompat.TransportControls? = null
//
//    //AudioPlayer notification ID
//    private val NOTIFICATION_ID = 101
//
//    override fun onCreate() {
//        super.onCreate()
//        // Perform one-time setup procedures
//        // Manage incoming phone calls during playback.
//        // Pause MediaPlayer on incoming call,
//        // Resume on hangup.
//        callStateListener()
//        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
//        registerBecomingNoisyReceiver()
//        //Listen for new Audio to play -- BroadcastReceiver
//        registerPlayNewAudioBroadcastReceiver()
//
//        registerWiFiStatusBroadcastReceiver()
//        registerPlayPauseChangeBroadcastReceiver()
//    }
//    override fun onBind(intent: Intent?): IBinder {
//        return iBinder
//    }
//
//    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
//        //Invoked indicating buffering status of a media resource being streamed over the network.
//    }
//
//    override fun onCompletion(mp: MediaPlayer?) {
//        //Invoked when playback of a media source has completed.
//        stopMedia()
//        //stop the service
//        stopSelf()
//    }
//
//    //Handle errors
//    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
//        //Invoked when there has been an error during an asynchronous operation
//        when (what) {
//            MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.d(
//                "MediaPlayer Error",
//                "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $extra"
//            )
//            MEDIA_ERROR_SERVER_DIED -> Log.d(
//                "MediaPlayer Error",
//                "MEDIA ERROR SERVER DIED $extra"
//            )
//            MEDIA_ERROR_UNKNOWN -> Log.d(
//                "MediaPlayer Error",
//                "MEDIA ERROR UNKNOWN $extra"
//            )
//        }
//        return false
//    }
//
//    override fun onPrepared(mp: MediaPlayer?) {
//        //Invoked when the media source is ready for playback.
//        playMedia()
//    }
//
//    override fun onInfo(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
//        //Invoked to communicate some info.
//        return false
//    }
//
//    override fun onSeekComplete(mp: MediaPlayer?) {
//        //Invoked indicating the completion of a seek operation.
//    }
//
//    override fun onAudioFocusChange(focusState: Int) {
//        //Invoked when the audio focus of the system is updated.
//        when (focusState) {
//            AudioManager.AUDIOFOCUS_GAIN -> {
//                // resume playback
//                if (mediaPlayer == null)
//                    initMediaPlayer()
//                else if (!mediaPlayer!!.isPlaying)
//                    mediaPlayer?.start()
//                mediaPlayer!!.setVolume(1.0f, 1.0f)
//            }
//            AudioManager.AUDIOFOCUS_LOSS -> {
//                // Lost focus for an unbounded amount of time: stop playback and release media player
//                if (mediaPlayer!!.isPlaying)
//                    mediaPlayer?.stop()
//                mediaPlayer?.release()
//                mediaPlayer = null
//            }
//            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
//                // Lost focus for a short time, but we have to stop playback.
//                // We don't release the media player because playback is likely to resume
//                if (mediaPlayer!!.isPlaying)
//                    mediaPlayer?.pause()
//            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
//                // Lost focus for a short time, but it's ok to keep playing
//                // at an attenuated level
//                if (mediaPlayer!!.isPlaying)
//                    mediaPlayer?.setVolume(0.1f, 0.1f)
//        }
//    }
//
//    // Local player methods
//
//    fun getCurrentPosition() : Int? {
//        return mediaPlayer?.currentPosition
//    }
//
//    fun seekTo(position: Int) {
//        mediaPlayer?.seekTo(position)
//    }
//
//    // Private
//
//    private fun requestAudioFocus(): Boolean {
//        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
//        val result = audioManager!!.requestAudioFocus(
//            this,
//            AudioManager.STREAM_MUSIC,
//            AudioManager.AUDIOFOCUS_GAIN
//        )
//        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
//            //Focus gained
//            true
//        } else false
//        //Could not gain focus
//    }
//
//    private fun removeAudioFocus(): Boolean {
//        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager!!.abandonAudioFocus(this)
//    }
//
//
//    inner class LocalBinder : Binder() {
//        val service: MediaPlayerService
//            get() = this@MediaPlayerService
//    }
//
//
//    private fun initMediaPlayer() {
//        mediaPlayer = MediaPlayer()
//        //Set up MediaPlayer event listeners
//        mediaPlayer?.setOnCompletionListener(this)
//        mediaPlayer?.setOnErrorListener(this)
//        mediaPlayer?.setOnPreparedListener(this)
//        mediaPlayer?.setOnBufferingUpdateListener(this)
//        mediaPlayer?.setOnSeekCompleteListener(this)
//        mediaPlayer?.setOnInfoListener(this)
//        //Reset so that the MediaPlayer is not pointing to another data source
//        mediaPlayer?.reset()
//        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
//        try { // Set the data source to the mediaFile location
//            mediaPlayer?.setDataSource(activeSong?.soundUrl
//                ?: "https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")
////            mediaPlayer?.setDataSource(mediaFilePath)
//        } catch (e: IOException) {
//            e.printStackTrace()
//            stopSelf()
//        }
//        mediaPlayer?.prepareAsync()
//    }
//
//
//    private fun playMedia() {
//        if (!mediaPlayer!!.isPlaying) {
//            mediaPlayer!!.start()
//        }
//        duration = mediaPlayer?.duration
//    }
//
//    private fun stopMedia() {
//        if (mediaPlayer == null) return
//        if (mediaPlayer!!.isPlaying) {
//            mediaPlayer?.stop()
//        }
//    }
//
//    private fun pauseMedia() {
//        if (mediaPlayer!!.isPlaying) {
//            mediaPlayer?.pause()
//            resumePosition = mediaPlayer?.currentPosition ?: 0
//        }
//    }
//
//    private fun resumeMedia() {
//        if (!mediaPlayer!!.isPlaying) {
//            mediaPlayer!!.seekTo(resumePosition)
//            mediaPlayer!!.start()
//        }
//    }
//
//
//    // Service Lifecycle Methods
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        try { //Load data from SharedPreferences
//            val storage = StorageUtil(getApplicationContext())
//            songList = storage.loadSongs()
//            songIndex = storage.loadAudioIndex()
//            if (songIndex !== -1 && songIndex < songList?.size!!) {
//                //index is in a valid range
//                activeSong = songList?.get(songIndex)
//            } else {
//                stopSelf()
//            }
//        } catch (e: NullPointerException) {
//            stopSelf()
//        }
//        //Request audio focus
//        if (requestAudioFocus() == false) { //Could not gain focus
//            stopSelf()
//        }
//        if (mediaSessionManager == null) {
//            try {
//                initMediaSession()
//                initMediaPlayer()
//            } catch (e: RemoteException) {
//                e.printStackTrace()
//                stopSelf()
//            }
//            buildNotification(PlaybackStatus.PLAYING)
//        }
//        //Handle Intent action from MediaSession.TransportControls
//        handleIncomingActions(intent)
//        return super.onStartCommand(intent, flags, startId)
//    }
////    //The system calls this method when an activity, requests the service be started
////    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
////        try {
////            //An audio file is passed to the service through putExtra()
////            mediaFilePath = intent.extras!!.getString("media")
////        } catch (e: NullPointerException) {
////            stopSelf()
////        }
////        //Request audio focus
////        if (requestAudioFocus() == false) {
////            //Could not gain focus
////            stopSelf()
////        }
////        if (mediaFilePath != null && mediaFilePath !== "")
////            initMediaPlayer()
////        return super.onStartCommand(intent, flags, startId)
////    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        if (mediaPlayer != null) {
//            stopMedia()
//            mediaPlayer!!.release()
//        }
//        removeAudioFocus()
//
//        //Disable the PhoneStateListener
//        if (phoneStateListener != null) {
//            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
//        }
//
//        removeNotification();
//
//        //unregister BroadcastReceivers
//        unregisterReceiver(becomingNoisyReceiver);
//        unregisterReceiver(playNewAudio);
//        unregisterReceiver(processConnectionStateChange)
//        unregisterReceiver(playPause)
//
//        //clear cached playlist
//        StorageUtil(getApplicationContext()).clearCachedAudioPlaylist()
//    }
//
//    // Dynamic BroadcastReceiver
//
//    //Becoming noisy
//    private val becomingNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            //pause audio on ACTION_AUDIO_BECOMING_NOISY
//            pauseMedia()
////            buildNotification(PlaybackStatus.PAUSED)
//        }
//    }
//
//    private fun registerBecomingNoisyReceiver() {
//        //register after getting audio focus
//        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
//        registerReceiver(becomingNoisyReceiver, intentFilter)
//    }
//
//    //Handle incoming phone calls
//    private fun callStateListener() {
//        // Get the telephony manager
//        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//        //Starting listening for PhoneState changes
//        phoneStateListener = object : PhoneStateListener() {
//            override fun onCallStateChanged(state: Int, incomingNumber: String) {
//                when (state) {
//                    //if at least one call exists or the phone is ringing
//                    //pause the MediaPlayer
//                    TelephonyManager.CALL_STATE_OFFHOOK,
//                    TelephonyManager.CALL_STATE_RINGING ->
//                        if (mediaPlayer != null) {
//                        pauseMedia()
//                        ongoingCall = true
//                        }
//                    TelephonyManager.CALL_STATE_IDLE ->
//                        // Phone idle. Start playing.
//                        if (mediaPlayer != null) {
//                            if (ongoingCall) {
//                                ongoingCall = false
//                                resumeMedia()
//                            }
//                        }
//                }
//            }
//        }
//        // Register the listener with the telephony manager
//        // Listen for changes to the device call state.
//        telephonyManager!!.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
//    }
//
//    private val playNewAudio: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            //Get the new media index from SharedPreferences
//            songIndex = StorageUtil(applicationContext).loadAudioIndex()
//            if (songIndex != -1 && songIndex < songList!!.size) {
//                //index is in a valid range
//                activeSong = songList!![songIndex]
//            } else {
//                stopSelf()
//            }
//            //A PLAY_NEW_AUDIO action received
//            // reset mediaPlayer to play the new Audio
//            stopMedia()
//            mediaPlayer!!.reset()
//            initMediaPlayer()
//            updateMetaData()
//            buildNotification(PlaybackStatus.PLAYING)
//        }
//    }
//
//    private val processConnectionStateChange: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            val action = intent.getAction();
//            if (action.equals(Broadcast_NETWORK_STATE)) {
//                var networkStatusString : String
//                val networkInfo: NetworkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
//                if (networkInfo.state == NetworkInfo.State.CONNECTED) {
//                    networkStatusString = "CONNECTED"
//                } else {
//                    networkStatusString = "NOT-CONNECTED"
//                }
//
//                val handler = Handler(Looper.getMainLooper())
//                handler.post(Runnable {
//                    Toast.makeText(
//                        this@MediaPlayerService.getApplicationContext(),
//                        "Network state is changed" + networkStatusString,
//                        Toast.LENGTH_SHORT
//                    ).show()
//                })
//            }
//        }
//    }
//
//    private val playPause: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            if (mediaPlayer!!.isPlaying) {
//                pauseMedia()
//            } else {
//                resumeMedia()
//            }
//        }
//    }
//
//    // BroadcastReceivers
//
//    private fun registerPlayNewAudioBroadcastReceiver() {
//        //Register playNewMedia receiver
//        val filter = IntentFilter(Broadcast_PLAY_NEW_AUDIO)
//        registerReceiver(playNewAudio, filter)
//    }
//
//    private fun registerWiFiStatusBroadcastReceiver() {
//
//        val intentFilter = IntentFilter()
//        intentFilter.addAction(Broadcast_NETWORK_STATE)
////            SUPPLICANT_CONNECTION_CHANGE_ACTION)
//        registerReceiver(processConnectionStateChange, intentFilter)
//    }
//
//    private fun registerPlayPauseChangeBroadcastReceiver() {
//        val filter = IntentFilter(Broadcast_PLAY_PAUSE_CHANGE)
//        registerReceiver(playPause, filter)
//    }
//
//    // MediaSession
//
//    @Throws(RemoteException::class)
//    private fun initMediaSession() {
//        if (mediaSessionManager != null)
//            return  //mediaSessionManager exists
//
//        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
//        // Create a new MediaSession
//        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
//        //Get MediaSessions transport controls
//        transportControls = mediaSession?.getController()?.getTransportControls()
//        //set MediaSession -> ready to receive media commands
//        mediaSession?.setActive(true)
//        //indicate that the MediaSession handles transport control commands
//        // through its MediaSessionCompat.Callback.
//        mediaSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
//        //Set mediaSession's MetaData
//        updateMetaData()
//        // Attach Callback to receive MediaSession updates
//        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
//            // Implement callbacks
//            override fun onPlay() {
//                super.onPlay()
//                resumeMedia()
//                buildNotification(PlaybackStatus.PLAYING)
//            }
//
//            override fun onPause() {
//                super.onPause()
//                pauseMedia()
//                buildNotification(PlaybackStatus.PAUSED)
//            }
//
//            override fun onSkipToNext() {
//                super.onSkipToNext()
//                skipToNext()
//                updateMetaData()
//                buildNotification(PlaybackStatus.PLAYING)
//            }
//
//            override fun onSkipToPrevious() {
//                super.onSkipToPrevious()
//                skipToPrevious()
//                updateMetaData()
//                buildNotification(PlaybackStatus.PLAYING)
//            }
//
//            override fun onStop() {
//                super.onStop()
//                removeNotification()
//                //Stop the service
//                stopSelf()
//            }
//
//            override fun onSeekTo(position: Long) {
//                super.onSeekTo(position)
//            }
//        })
//    }
//
//    private fun updateMetaData() {
//        val albumArt = BitmapFactory.decodeResource(resources, R.drawable.ic_media_play)
//        //replace with medias albumArt
//        // Update the current metadata
//        mediaSession?.setMetadata(
//            MediaMetadataCompat.Builder()
//                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
//                .putString(MediaMetadata.METADATA_KEY_ARTIST, activeSong?.author ?: "Empty author")
////                .putString(MediaMetadata.METADATA_KEY_ALBUM, activeAudio?.album ?: "Empty album")
//                .putString(MediaMetadata.METADATA_KEY_TITLE, activeSong?.trackTitle ?: "Empty track title")
//                .build()
//        )
//    }
//
//    private fun skipToNext() {
//        if (songIndex == songList!!.size - 1) {
//            //if last in playlist
//            songIndex = 0
//            activeSong = songList!![songIndex]
//        } else {
//            //get next in playlist
//            activeSong = songList!![++songIndex]
//        }
//        //Update stored index
//        StorageUtil(applicationContext).storeAudioIndex(songIndex)
//        stopMedia()
//        //reset mediaPlayer
//        mediaPlayer!!.reset()
//        initMediaPlayer()
//    }
//
//    private fun skipToPrevious() {
//        if (songIndex == 0) {
//            //if first in playlist
//            //set index to the last of audioList
//            songIndex = songList!!.size - 1
//            activeSong = songList!![songIndex]
//        } else {
//            //get previous in playlist
//            activeSong = songList!![--songIndex]
//        }
//        //Update stored index
//        StorageUtil(applicationContext).storeAudioIndex(songIndex)
//        stopMedia()
//        //reset mediaPlayer
//        mediaPlayer!!.reset()
//        initMediaPlayer()
//    }
//
//    // Notifications
//
//    private fun buildNotification(playbackStatus: PlaybackStatus) {
//        var notificationAction = R.drawable.ic_media_pause //needs to be initialized
//        var play_pauseAction: PendingIntent? = null
//        //Build a new notification according to the current state of the MediaPlayer
//        if (playbackStatus === PlaybackStatus.PLAYING) {
//            notificationAction = R.drawable.ic_media_pause
//            //create the pause action
//            play_pauseAction = playbackAction(1)
//        } else if (playbackStatus === PlaybackStatus.PAUSED) {
//            notificationAction = R.drawable.ic_media_play
//            //create the play action
//            play_pauseAction = playbackAction(0)
//        }
//        val largeIcon = BitmapFactory.decodeResource(
//            resources,
//            R.drawable.ic_btn_speak_now
//        ) //replace with your own image
//        // Create a new Notification
//        val notificationBuilder = NotificationCompat.Builder(this)
//            .setShowWhen(false) // Set the Notification style
//            .setStyle(
//                androidx.media.app.NotificationCompat.MediaStyle() // Attach our MediaSession token
//                    .setMediaSession(mediaSession?.getSessionToken())
//                    // Show our playback controls in the compact notification view.
//                    .setShowActionsInCompactView(0)
////                    .setShowActionsInCompactView(0, 1, 2)
//            ) // Set the Notification color
//            .setColor(resources.getColor(R.color.holo_purple)) // Set the large and small icons
//            .setLargeIcon(largeIcon)
//            .setSmallIcon(R.drawable.stat_sys_headset) // Set Notification content information
//            .setContentText(activeSong?.author)
////            .setContentTitle(activeAudio?.album)
//            .setContentInfo(activeSong?.trackTitle) // Add playback actions
////            .addAction(R.drawable.ic_media_previous, "previous", playbackAction(3))
//            .addAction(notificationAction, "pause", play_pauseAction)
////            .addAction(R.drawable.ic_media_next, "next", playbackAction(2))
//                as NotificationCompat.Builder
//        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
//            NOTIFICATION_ID,
//            notificationBuilder.build()
//        )
//    }
//
//    private fun removeNotification() {
//        val notificationManager =
//            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.cancel(NOTIFICATION_ID)
//    }
//
//
//    private fun playbackAction(actionNumber: Int): PendingIntent? {
//        val playbackAction = Intent(this, MediaPlayerService::class.java)
//        when (actionNumber) {
//            0 -> {
//                // Play
//                playbackAction.action = ACTION_PLAY
//                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
//            }
//            1 -> {
//                // Pause
//                playbackAction.action = ACTION_PAUSE
//                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
//            }
//            2 -> {
//                // Next track
//                playbackAction.action = PlayerNotificationManager.ACTION_NEXT
//                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
//            }
//            3 -> {
//                // Previous track
//                playbackAction.action = PlayerNotificationManager.ACTION_PREVIOUS
//                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
//            }
//            else -> {
//            }
//        }
//        return null
//    }
//
//
//    private fun handleIncomingActions(playbackAction: Intent?) {
//        if (playbackAction == null || playbackAction.action == null) return
//        val actionString = playbackAction.action
//        if (actionString.equals(ACTION_PLAY, ignoreCase = true)) {
//            transportControls?.play()
//        } else if (actionString.equals(ACTION_PAUSE, ignoreCase = true)) {
//            transportControls?.pause()
//        } else if (actionString.equals(PlayerNotificationManager.ACTION_NEXT, ignoreCase = true)) {
//            transportControls?.skipToNext()
//        } else if (actionString.equals(PlayerNotificationManager.ACTION_PREVIOUS, ignoreCase = true)) {
//            transportControls?.skipToPrevious()
//        } else if (actionString.equals(ACTION_STOP, ignoreCase = true)) {
//            transportControls?.stop()
//        }
//    }
//
//
//}
//








////class MusicForegroundService : Service() {
//
////    override fun onBind(intent: Intent?): IBinder? {
////        return null
//////        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
////    }
////
////    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
////        val input = intent.getStringExtra("inputExtra")
////        createNotificationChannel()
////        val notificationIntent = Intent(this, MainActivity::class.java)
////        val pendingIntent = PendingIntent.getActivity(
////            this,
////            0, notificationIntent, 0
////        )
////        val notification: Notification =
////            Builder(this, CHANNEL_ID)
////                .setContentTitle("Foreground Service")
////                .setContentText(input)
////                .setSmallIcon(R.drawable.ic_stat_name)
////                .setContentIntent(pendingIntent)
////                .build()
////        startForeground(1, notification)
////        //do heavy work on a background thread
//////stopSelf();
////        return START_NOT_STICKY
////    }
////
////
////
////    private fun createNotificationChannel() {
////        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
////            val serviceChannel = NotificationChannel(
////                com.wave.foregroundservice.ForegroundService.CHANNEL_ID,
////                "Foreground Service Channel",
////                NotificationManager.IMPORTANCE_DEFAULT
////            )
////            val manager = getSystemService(
////                NotificationManager::class.java
////            )
////            manager.createNotificationChannel(serviceChannel)
////        }
////    }
////
////}