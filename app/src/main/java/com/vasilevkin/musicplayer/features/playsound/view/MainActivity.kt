package com.vasilevkin.musicplayer.features.playsound.view

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vasilevkin.musicplayer.R
import com.vasilevkin.musicplayer.features.foregroundservice.MediaPlayerService
import com.vasilevkin.musicplayer.features.foregroundservice.MediaPlayerService.LocalBinder
import com.vasilevkin.musicplayer.features.playsound.IPlaySoundContract
import com.vasilevkin.musicplayer.model.local.Audio
import com.vasilevkin.musicplayer.utils.Broadcast_PLAY_NEW_AUDIO
import com.vasilevkin.musicplayer.utils.StorageUtil


class MainActivity : AppCompatActivity(), IPlaySoundContract.View {

    private var player: MediaPlayerService? = null
    var serviceBound = false

    var audioList: ArrayList<Audio?>? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadAudio()
        if (audioList != null) {
            //play the first audio in the ArrayList
            playAudio(audioList?.get(0)?.data!!)
            Toast.makeText(this@MainActivity, "Play first song on the device", Toast.LENGTH_LONG).show()
        } else {
            playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")
            Toast.makeText(this@MainActivity, "No songs on the device", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState.getBoolean("ServiceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            //service is active
            player!!.stopSelf()
        }
    }


    //Binding this Client to the AudioPlayer Service
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as LocalBinder
            player = binder.service
            serviceBound = true
            Toast.makeText(this@MainActivity, "Service Bound", Toast.LENGTH_LONG).show()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
        }
    }

    private fun playAudio(audioIndex: Int) {
        //Check if service is active
        if (!serviceBound) {
            //Store Serializable audioList to SharedPreferences
            val storage = StorageUtil(applicationContext)
            storage.storeAudio(audioList)
            storage.storeAudioIndex(audioIndex)
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            //Store the new audioIndex to SharedPreferences
            val storage = StorageUtil(applicationContext)
            storage.storeAudioIndex(audioIndex)
            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            val broadcastIntent = Intent(Broadcast_PLAY_NEW_AUDIO)
            sendBroadcast(broadcastIntent)
        }
    }

    private fun playAudio(media: String) { //Check if service is active
        if (!serviceBound) {
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            playerIntent.putExtra("media", media)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else { //Service is active
            //Send media with BroadcastReceiver
        }
    }


    private fun loadAudio() {
        val contentResolver = contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor: Cursor? = contentResolver.query(uri, null, selection, null, sortOrder)
        if (cursor != null && cursor.getCount() > 0) {
            audioList = ArrayList()
            while (cursor.moveToNext()) {
                val data: String =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val title: String =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album: String =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist: String =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                // Save to audioList
                audioList!!.add(Audio(data, title, album, artist))
            }
        }
        cursor!!.close()
    }


}


//    companion object {
//        const val SOUND_URL_EXTRA = "sound_url_extra"
//    }
//
////    private lateinit var videoView: PlayerView
//
//    private lateinit var presenter: IPlaySoundContract.Presenter
//    private var playerView: PlayerView? = null
//    private var player: SimpleExoPlayer? = null
//    private var playWhenReady = true
//    private var playbackPosition: Long = 0
//    private var currentWindow = 0
//
//    // Lifecycle methods
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//        playerView = findViewById(R.id.ep_video_view)
//    }
//
//    override fun onStart() {
//        super.onStart()
//        if (Util.SDK_INT >= 24) {
//            initializePlayer()
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        hideSystemUi()
//        if (Util.SDK_INT < 24 || player == null) {
//            initializePlayer()
//        }
//    }
//
//    override fun onPause() {
//        super.onPause()
//        if (Util.SDK_INT < 24) {
//
//            releasePlayer()
//            //            presenter.releasePlayer()
//        }
//    }
//
//    override fun onStop() {
//        super.onStop()
//        if (Util.SDK_INT >= 24) {
//
//            releasePlayer()
//            //            presenter.releasePlayer()
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        presenter.deactivate()
//        presenter.setMediaSessionState(false)
//    }
//
//    // Private methods
//
//    private fun initializePlayer() {
//
//        presenter = PlaySoundPresenter(this)
//
//        val soundUrl =
////            "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
//            "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview128/v4/38/40/fb/3840fbc5-6273-d595-289e-2486153f074a/mzaf_5312791648997337345.plus.aac.p.m4a"
////            intent.getStringExtra(SOUND_URL_EXTRA)
//
////        videoView.player = presenter.getPlayer().getPlayer(this)
////        presenter.play(soundUrl)
//
//        player = ExoPlayerFactory.newSimpleInstance(this)
//        playerView!!.setPlayer(player)
//
////        val uri = Uri.parse("https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3")
//        val uri =
//            Uri.parse("https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview128/v4/38/40/fb/3840fbc5-6273-d595-289e-2486153f074a/mzaf_5312791648997337345.plus.aac.p.m4a")
////        val uri = Uri.parse(getString(R.string.media_url_mp3))
//        val mediaSource = buildMediaSource(uri)
//        player!!.setPlayWhenReady(playWhenReady)
//        player!!.seekTo(currentWindow, playbackPosition)
//        player!!.prepare(mediaSource, false, false)
//    }
//
//    private fun buildMediaSource(uri: Uri): MediaSource {
//        val dataSourceFactory: DataSource.Factory =
//            DefaultDataSourceFactory(this, "exoplayer-codelab")
//        return ProgressiveMediaSource.Factory(dataSourceFactory)
//            .createMediaSource(uri)
//    }
//
//    @SuppressLint("InlinedApi")
//    private fun hideSystemUi() {
//        playerView!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
//                or View.SYSTEM_UI_FLAG_FULLSCREEN
//                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
//    }
//
//    private fun releasePlayer() {
//        if (player != null) {
//            playWhenReady = player!!.playWhenReady
//            playbackPosition = player!!.currentPosition
//            currentWindow = player!!.currentWindowIndex
//            player!!.release()
//            player = null
//        }
//    }
//
//}
