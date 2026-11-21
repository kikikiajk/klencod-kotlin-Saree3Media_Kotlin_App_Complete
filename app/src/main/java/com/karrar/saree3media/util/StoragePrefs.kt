package com.karrar.saree3media.util

import android.content.Context
import android.net.Uri

class StoragePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("saree3_media_prefs", Context.MODE_PRIVATE)

    fun getAudioTreeUri(): Uri? {
        val uriStr = prefs.getString("audio_folder_uri", null) ?: return null
        return Uri.parse(uriStr)
    }

    fun saveAudioTreeUri(uri: Uri) {
        prefs.edit().putString("audio_folder_uri", uri.toString()).apply()
    }
}
