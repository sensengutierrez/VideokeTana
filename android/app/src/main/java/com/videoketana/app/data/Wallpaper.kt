package com.videoketana.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Sariling larawan bilang wallpaper ng app. Nasa device lang ito; hindi ina-upload. */
object Wallpaper {
    suspend fun save(c: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2200) sample *= 2
            val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@withContext false
            val f = File(c.filesDir, "wallpaper.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            withContext(Dispatchers.Main) { Prefs.changeWallpaper(f.absolutePath) }
            true
        } catch (e: Exception) { false }
    }

    fun clear(c: Context) { File(c.filesDir, "wallpaper.jpg").delete(); Prefs.changeWallpaper("") }
}
