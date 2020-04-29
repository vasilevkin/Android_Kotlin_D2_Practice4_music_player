package com.vasilevkin.musicplayer.features.playsound.presenter

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import com.google.android.exoplayer2.ExoPlayerFactory
import com.vasilevkin.musicplayer.base.BasePresenter
import com.vasilevkin.musicplayer.features.playsound.IPlaySoundContract
import com.vasilevkin.musicplayer.model.local.Song
import com.vasilevkin.musicplayer.player.MediaPlayer
import java.lang.ref.WeakReference


class PlaySoundPresenter(playSoundView: IPlaySoundContract.View) : BasePresenter<IPlaySoundContract.View>(), IPlaySoundContract.Presenter {

    override var view: IPlaySoundContract.View? = playSoundView

//    private val view = WeakReference(playSoundView)

    private val mediaPlayer
//     = ExoPlayerFactory.newSimpleInstance(view as Context)
            = MediaPlayer()


    var serviceBound = false

    var songList: ArrayList<Song?>? = null



    override fun deactivate() {}

    override fun getPlayer() = mediaPlayer

    override fun play(song: Song) = mediaPlayer.play(song)

    override fun releasePlayer() = mediaPlayer.releasePlayer()

    override fun setMediaSessionState(isActive: Boolean) {
        mediaPlayer.setMediaSessionState(isActive)
    }

    override fun getServiceState(): Boolean {
        return serviceBound
    }

    override fun setServiceState(isBound: Boolean) {
        serviceBound = isBound
    }

    override fun getSongsList(): ArrayList<Song?>? {
        return songList
    }

    override fun onViewCreated() {
        super.onViewCreated()
        loadAudio()
    }

    // Private

    private fun loadAudio() {
        val contentResolver = (view as ContextWrapper)?.getContentResolver()
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor: Cursor? = contentResolver.query(uri, null, selection, null, sortOrder)
        if (cursor != null && cursor.getCount() > 0) {
            songList = ArrayList()
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
                songList!!.add(Song(author = artist, trackTitle = title, soundUrl = data))
            }
        }
        cursor!!.close()
    }

}