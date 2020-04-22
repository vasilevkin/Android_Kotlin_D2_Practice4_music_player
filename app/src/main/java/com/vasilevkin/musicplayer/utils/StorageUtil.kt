package com.vasilevkin.musicplayer.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vasilevkin.musicplayer.model.local.Song
import java.lang.reflect.Type


class StorageUtil(private val context: Context?) {

    private val STORAGE = "com.vasilevkin.musicplayer.utils.STORAGE"
    private var preferences: SharedPreferences? = null
//    private var context: Context? = null

//    fun StorageUtil(context: Context?) {
//        this.context = context
//    }

    fun storeAudio(arrayList: ArrayList<Song?>?) {
        preferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val editor = preferences!!.edit()
        val gson = Gson()
        val json = gson.toJson(arrayList)
        editor.putString("audioArrayList", json)
        editor.apply()
    }

    fun loadSongs(): ArrayList<Song?>? {
        preferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = preferences!!.getString("audioArrayList", null)
        val type: Type = object : TypeToken<ArrayList<Song?>?>() {}.type

        val audioList: ArrayList<Song?>? = gson.fromJson(json, type)

        if (audioList == null) {
            return arrayListOf(
                Song( "test artist",
                    "new title",
                    "https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")
            )
        }
        return audioList
    }

    fun storeAudioIndex(index: Int) {
        preferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val editor = preferences!!.edit()
        editor.putInt("audioIndex", index)
        editor.apply()
    }

    fun loadAudioIndex(): Int {
        preferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        //return -1 if no data found
        return preferences!!.getInt("audioIndex", -1)
    }

    fun clearCachedAudioPlaylist() {
        preferences = context?.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
        val editor = preferences!!.edit()
        editor.clear()
        editor.commit()
    }

}