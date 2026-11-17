package com.videoketana.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.videoketana.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Sariling update ng app: tinitingnan ang pinakabagong release sa GitHub (tag na app-vN).
 * Kapag mas bago ang N kaysa sa versionCode ng app, ida-download ang APK at bubuksan ang installer.
 */
object Updater {
    data class Release(val number: Int, val name: String, val notes: String, val apkUrl: String)

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        try {
            val r = Request.Builder().url("https://api.github.com/repos/${Config.REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json").build()
            Supa.http.newCall(r).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val j = JSONObject(res.body?.string() ?: return@withContext null)
                val n = j.optString("tag_name").removePrefix("app-v").toIntOrNull() ?: return@withContext null
                val assets = j.optJSONArray("assets")
                var url = Config.APK_URL
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url", url)
                }
                if (n > BuildConfig.VERSION_CODE) Release(n, j.optString("name"), j.optString("body"), url) else null
            }
        } catch (e: Exception) { null }
    }

    /** I-download ang APK; tinatawag ang progress(0..1). Ibinabalik ang file kapag tapos. */
    suspend fun download(c: Context, rel: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(c.getExternalFilesDir(null), "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "VideokeTana-${rel.number}.apk")
        Supa.http.newCall(Request.Builder().url(rel.apkUrl).build()).execute().use { res ->
            if (!res.isSuccessful) throw RuntimeException("HTTP ${res.code}")
            val body = res.body ?: throw RuntimeException("Walang laman")
            val total = body.contentLength().takeIf { it > 0 } ?: 1L
            body.byteStream().use { input ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        withContext(Dispatchers.Main) { progress((done.toFloat() / total).coerceAtMost(1f)) }
                    }
                }
            }
        }
        f
    }

    /** Kailangang payagan ng user ang "Install unknown apps" para sa VideokeTana (minsan lang). */
    fun canInstall(c: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || c.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(c: Context, f: File) {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
