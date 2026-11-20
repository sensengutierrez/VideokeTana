package com.videoketana.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import org.json.JSONObject
import java.io.File

/** Isang kanta mula sa folder ng device (hindi ina-upload). Numero 90001 pataas, gaya ng web. */
data class LocalSong(val code: Int, val title: String, val artist: String, val file: File) {
    val ext: String get() = file.extension.lowercase()
    val isMidi: Boolean get() = ext in LocalLib.MIDI
    val isVideo: Boolean get() = ext in LocalLib.VIDEO
    /** .lrc na kapareho ng pangalan (para sa MP3). */
    val lrc: File? get() = File(file.parentFile, file.nameWithoutExtension + ".lrc").takeIf { it.exists() }
}

/**
 * Folder ng VideokeTana sa device:  Download/VideokeTana/
 *   MIDI/   → .mid .midi .kar (pwedeng may sub-folder, hal. kapag in-extract ang zip)
 *   Kanta/  → .mp3 .m4a .mp4 .webm (+ .lrc na kapareho ng pangalan)
 *   BGV/    → background video (.mp4 .webm)
 * Nasa device lang ang mga file; hindi ina-upload. Ang may-ari ng device ang may pananagutan sa karapatan.
 */
object LocalLib {
    val MIDI = setOf("mid", "midi", "kar")
    val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "wav")
    val VIDEO = setOf("mp4", "webm", "mkv", "3gp")

    val root: File get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VideokeTana")
    val midiDir: File get() = File(root, "MIDI")
    val songDir: File get() = File(root, "Kanta")
    val bgvDir: File get() = File(root, "BGV")
    val shownPath = "Download/VideokeTana"

    /** Mga permission na kailangan para mabasa ang folder. */
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun hasPermission(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) ||
            permissions().all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** "Buong access sa files" (para sa .kar at kapag ayaw ng ibang paraan). */
    fun openAllFilesSetting(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) {
            try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (x: Exception) { false }
        }
    }

    /** Gawin ang mga folder (kung pinapayagan ng device). Totoo kung meron na ang lahat. */
    fun ensureFolders(): Boolean {
        listOf(midiDir, songDir, bgvDir).forEach { try { it.mkdirs() } catch (e: Exception) { } }
        return midiDir.isDirectory && songDir.isDirectory && bgvDir.isDirectory
    }

    private fun walk(dir: File, exts: Set<String>): List<File> =
        try { dir.walkTopDown().maxDepth(4).filter { it.isFile && it.extension.lowercase() in exts && !it.name.startsWith(".") }.toList() } catch (e: Exception) { emptyList() }

    /** "Title - Artist (FIX).mid" → title, artist. */
    private fun nameParts(f: File): Pair<String, String> {
        val base = f.nameWithoutExtension.replace('_', ' ').replace(Regex("\\s*\\((FIX|fix|Fix)\\)\\s*"), " ").trim()
        val p = base.split(" - ", limit = 2)
        return if (p.size == 2) p[0].trim() to p[1].trim() else base to ""
    }

    /** Lahat ng kanta sa folder. Hindi nagbabago ang numero ng isang file kahit may madagdag (naka-save ang listahan). */
    fun scan(): List<LocalSong> {
        val files = (walk(midiDir, MIDI) + walk(songDir, AUDIO + VIDEO + MIDI)).distinctBy { it.absolutePath }
        val map = try { JSONObject(Prefs.str("localCodes").ifEmpty { "{}" }) } catch (e: Exception) { JSONObject() }
        var next = 90001
        map.keys().forEach { k -> next = maxOf(next, map.optInt(k) + 1) }
        val rel = { f: File -> f.absolutePath.removePrefix(root.absolutePath) }
        var changed = false
        files.sortedBy { it.name.lowercase() }.forEach { f -> if (!map.has(rel(f))) { map.put(rel(f), next++); changed = true } }
        if (changed) Prefs.putStr("localCodes", map.toString())
        return files.map { f -> val (t, a) = nameParts(f); LocalSong(map.optInt(rel(f)), t, a, f) }.sortedBy { it.code }
    }

    fun bgvFiles(): List<File> = walk(bgvDir, VIDEO).sortedBy { it.name.lowercase() }
}
