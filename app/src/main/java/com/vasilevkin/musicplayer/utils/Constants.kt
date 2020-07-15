package com.vasilevkin.musicplayer.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.vasilevkin.musicplayer.features.foregroundservice.MediaPlayerService


val Broadcast_PLAY_NEW_AUDIO = "com.vasilevkin.musicplayer.features.playsound.PlayNewAudio"
val Broadcast_PLAY_PAUSE_CHANGE = "com.vasilevkin.musicplayer.features.playsound.PlayPauseChange"
val Broadcast_NETWORK_STATE = WifiManager.NETWORK_STATE_CHANGED_ACTION



const val SONG_POS = "song_position"
const val PROGRESS = "progress"
const val CALL_SETUP_AFTER = "call_setup_after"
const val SONG_IDS = "song_ids"
const val EDITED_SONG = "edited_song"
const val ALL_SONGS_PLAYLIST_ID = 1
const val START_SLEEP_TIMER = "start_sleep_timer"
const val STOP_SLEEP_TIMER = "stop_sleep_timer"

private const val PATH = "com.simplemobiletools.musicplayer.action."

const val INIT = PATH + "INIT"
const val INIT_PATH = PATH + "INIT_PATH"
const val SETUP = PATH + "SETUP"
const val FINISH = PATH + "FINISH"
const val PREVIOUS = PATH + "PREVIOUS"
const val PAUSE = PATH + "PAUSE"
const val PLAYPAUSE = PATH + "PLAYPAUSE"
const val NEXT = PATH + "NEXT"
const val RESET = PATH + "RESET"
const val EDIT = PATH + "EDIT"
const val PLAYPOS = PATH + "PLAYPOS"
const val REFRESH_LIST = PATH + "REFRESH_LIST"
const val SET_PROGRESS = PATH + "SET_PROGRESS"
const val SET_EQUALIZER = PATH + "SET_EQUALIZER"
const val SKIP_BACKWARD = PATH + "SKIP_BACKWARD"
const val SKIP_FORWARD = PATH + "SKIP_FORWARD"
const val REMOVE_CURRENT_SONG = PATH + "REMOVE_CURRENT_SONG"
const val REMOVE_SONG_IDS = PATH + "REMOVE_SONG_IDS"
const val NEW_SONG = "NEW_SONG"
const val IS_PLAYING = "IS_PLAYING"
const val SONG_CHANGED = "SONG_CHANGED"
const val SONG_STATE_CHANGED = "SONG_STATE_CHANGED"
const val BROADCAST_STATUS = "BROADCAST_STATUS"


fun isOreoPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
fun Context.hasPermission(permId: Int) = ContextCompat.checkSelfPermission(this, getPermissionString(permId)) == PackageManager.PERMISSION_GRANTED

fun Context.getPermissionString(id: Int) = when (id) {
    PERMISSION_READ_STORAGE -> Manifest.permission.READ_EXTERNAL_STORAGE
    PERMISSION_WRITE_STORAGE -> Manifest.permission.WRITE_EXTERNAL_STORAGE
    PERMISSION_CAMERA -> Manifest.permission.CAMERA
    PERMISSION_RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO
    PERMISSION_READ_CONTACTS -> Manifest.permission.READ_CONTACTS
    PERMISSION_WRITE_CONTACTS -> Manifest.permission.WRITE_CONTACTS
    PERMISSION_READ_CALENDAR -> Manifest.permission.READ_CALENDAR
    PERMISSION_WRITE_CALENDAR -> Manifest.permission.WRITE_CALENDAR
    PERMISSION_CALL_PHONE -> Manifest.permission.CALL_PHONE
    PERMISSION_READ_CALL_LOG -> Manifest.permission.READ_CALL_LOG
    PERMISSION_WRITE_CALL_LOG -> Manifest.permission.WRITE_CALL_LOG
    PERMISSION_GET_ACCOUNTS -> Manifest.permission.GET_ACCOUNTS
    PERMISSION_READ_SMS -> Manifest.permission.READ_SMS
    PERMISSION_SEND_SMS -> Manifest.permission.SEND_SMS
    else -> ""
}

// permissions
const val PERMISSION_READ_STORAGE = 1
const val PERMISSION_WRITE_STORAGE = 2
const val PERMISSION_CAMERA = 3
const val PERMISSION_RECORD_AUDIO = 4
const val PERMISSION_READ_CONTACTS = 5
const val PERMISSION_WRITE_CONTACTS = 6
const val PERMISSION_READ_CALENDAR = 7
const val PERMISSION_WRITE_CALENDAR = 8
const val PERMISSION_CALL_PHONE = 9
const val PERMISSION_READ_CALL_LOG = 10
const val PERMISSION_WRITE_CALL_LOG = 11
const val PERMISSION_GET_ACCOUNTS = 12
const val PERMISSION_READ_SMS = 13
const val PERMISSION_SEND_SMS = 14


//val Context.config: Config get() = Config.newInstance(applicationContext)



fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()

fun ensureBackgroundThread(callback: () -> Unit) {
    if (isOnMainThread()) {
        Thread {
            callback()
        }.start()
    } else {
        callback()
    }
}

class ControlActionsListener : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            PREVIOUS, PLAYPAUSE, NEXT, FINISH -> context.sendIntent(action)
        }
    }
}

@SuppressLint("NewApi")
fun Context.sendIntent(action: String) {
    Intent(this, MediaPlayerService::class.java).apply {
        this.action = action
        try {
            if (isOreoPlus()) {
                startForegroundService(this)
            } else {
                startService(this)
            }
        } catch (ignored: Exception) {
        }
    }
}












// some helper functions were taken from https://github.com/iPaulPro/aFileChooser/blob/master/aFileChooser/src/com/ipaulpro/afilechooser/utils/FileUtils.java
//fun Context.getRealPathFromURI(uri: Uri): String? {
//    if (uri.scheme == "file") {
//        return uri.path
//    }
//
//    if (isDownloadsDocument(uri)) {
//        val id = DocumentsContract.getDocumentId(uri)
//        if (id.areDigitsOnly()) {
//            val newUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())
//            val path = getDataColumn(newUri)
//            if (path != null) {
//                return path
//            }
//        }
//    } else if (isExternalStorageDocument(uri)) {
//        val documentId = DocumentsContract.getDocumentId(uri)
//        val parts = documentId.split(":")
//        if (parts[0].equals("primary", true)) {
//            return "${Environment.getExternalStorageDirectory().absolutePath}/${parts[1]}"
//        }
//    } else if (isMediaDocument(uri)) {
//        val documentId = DocumentsContract.getDocumentId(uri)
//        val split = documentId.split(":").dropLastWhile { it.isEmpty() }.toTypedArray()
//        val type = split[0]
//
//        val contentUri = when (type) {
//            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
//            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
//            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//        }
//
//        val selection = "_id=?"
//        val selectionArgs = arrayOf(split[1])
//        val path = getDataColumn(contentUri, selection, selectionArgs)
//        if (path != null) {
//            return path
//        }
//    }
//
//    return getDataColumn(uri)
//}
//
//fun Context.getDataColumn(uri: Uri, selection: String? = null, selectionArgs: Array<String>? = null): String? {
//    var cursor: Cursor? = null
//    try {
//        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
//        cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
//        if (cursor?.moveToFirst() == true) {
//            val data = cursor.getStringValue(MediaStore.Files.FileColumns.DATA)
//            if (data != "null") {
//                return data
//            }
//        }
//    } catch (e: Exception) {
//    } finally {
//        cursor?.close()
//    }
//    return null
//}
