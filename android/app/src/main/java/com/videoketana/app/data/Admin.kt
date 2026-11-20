package com.videoketana.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Mga kategorya (kapareho ng web). */
val CATEGORIES = listOf("OPM", "Pop", "Love song", "Rock", "Pasko", "Pambata", "Gospel", "Folk", "Classic", "Iba pa")

const val LICENSE_UPLOAD = "Na-upload ng admin. Siguraduhing may karapatan sa kantang ito."
fun licenseYouTube(channel: String) = "Naka-embed mula sa YouTube (${channel.ifBlank { "YouTube" }}). Ang karapatan ay nasa YouTube at sa nag-upload; hindi dina-download."

/** Napiling file mula sa phone. */
data class PickedFile(val uri: Uri, val name: String, val size: Long, val mime: String) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

fun pickedFile(ctx: Context, uri: Uri): PickedFile {
    var name = "file"; var size = -1L
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ni >= 0) name = c.getString(ni) ?: name
            val si = c.getColumnIndex(OpenableColumns.SIZE); if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
        }
    }
    return PickedFile(uri, name, size, ctx.contentResolver.getType(uri) ?: "application/octet-stream")
}

/**
 * Admin ng library sa app: login (Supabase Auth), upload ng kanta sa storage, YouTube, at pagbura.
 * Ang RLS sa server ang tunay na bantay: admin lang (unang user) ang pwedeng magdagdag o magbura.
 */
object Admin {
    private val JSON = "application/json".toMediaType()
    private const val MAX_BYTES = 50L * 1024 * 1024   // limit ng bucket na songs

    var email: String
        get() = Prefs.str("adm_email")
        private set(v) = Prefs.putStr("adm_email", v)
    private var access: String
        get() = Prefs.str("adm_access")
        set(v) = Prefs.putStr("adm_access", v)
    private var refresh: String
        get() = Prefs.str("adm_refresh")
        set(v) = Prefs.putStr("adm_refresh", v)
    private var expires: Long
        get() = Prefs.long("adm_exp")
        set(v) = Prefs.putLong("adm_exp", v)

    val loggedIn: Boolean get() = refresh.isNotEmpty()

    private fun saveSession(j: JSONObject) {
        access = j.optString("access_token")
        refresh = j.optString("refresh_token")
        expires = System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600) - 60
        j.optJSONObject("user")?.optString("email")?.let { if (it.isNotEmpty()) email = it }
    }

    private suspend fun post(url: String, body: JSONObject, token: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val r = Request.Builder().url(url).header("apikey", Config.SUPABASE_KEY)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .post(body.toString().toRequestBody(JSON)).build()
        Supa.http.newCall(r).execute().use { res ->
            val txt = res.body?.string() ?: ""
            val j = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
            if (!res.isSuccessful) throw RuntimeException(friendly(j.optString("error_description").ifEmpty { j.optString("msg").ifEmpty { j.optString("message", "HTTP ${res.code}") } }))
            j
        }
    }

    suspend fun login(mail: String, pass: String) {
        val j = post("${Config.SUPABASE_URL}/auth/v1/token?grant_type=password", JSONObject().put("email", mail.trim()).put("password", pass))
        saveSession(j)
    }

    fun logout() { access = ""; refresh = ""; expires = 0 }

    /** Valid na token; nire-refresh kapag paso na. */
    private suspend fun token(): String {
        if (!loggedIn) throw RuntimeException(tr("Mag-login muna.", "Log in first."))
        if (System.currentTimeMillis() / 1000 < expires && access.isNotEmpty()) return access
        try { saveSession(post("${Config.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))) }
        catch (e: Exception) { logout(); throw RuntimeException(tr("Paso na ang login. Mag-login ulit.", "Login expired. Please log in again.")) }
        return access
    }

    /** Totoo kung admin ang naka-login (tinatanong ang server). */
    suspend fun isAdmin(): Boolean = withContext(Dispatchers.IO) {
        val r = Request.Builder().url("${Config.SUPABASE_URL}/rest/v1/rpc/is_admin").header("apikey", Config.SUPABASE_KEY)
            .header("Authorization", "Bearer ${token()}").post("{}".toRequestBody(JSON)).build()
        Supa.http.newCall(r).execute().use { it.isSuccessful && (it.body?.string()?.trim() == "true") }
    }

    private suspend fun rest(method: String, path: String, params: Map<String, String>, body: String?): String = withContext(Dispatchers.IO) {
        val u = "${Config.SUPABASE_URL}/rest/v1/$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val r = Request.Builder().url(u).header("apikey", Config.SUPABASE_KEY).header("Authorization", "Bearer ${token()}")
            .header("Prefer", "return=representation")
            .method(method, body?.toRequestBody(JSON)).build()
        Supa.http.newCall(r).execute().use { res ->
            val txt = res.body?.string() ?: ""
            if (!res.isSuccessful) throw RuntimeException(friendly(try { JSONObject(txt).optString("message") } catch (e: Exception) { "HTTP ${res.code}" }))
            txt
        }
    }

    /** Ina-upload ang file sa bucket na songs, tuloy-tuloy mula sa phone (hindi buong file sa memory). */
    private suspend fun putObject(ctx: Context, path: String, f: PickedFile, mime: String, upsert: Boolean, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val body = object : RequestBody() {
            override fun contentType(): MediaType = mime.toMediaType()
            override fun contentLength(): Long = f.size
            override fun writeTo(sink: BufferedSink) {
                ctx.contentResolver.openInputStream(f.uri)!!.source().use { src ->
                    var sent = 0L
                    while (true) {
                        val n = src.read(sink.buffer, 64 * 1024)
                        if (n == -1L) break
                        sink.flush(); sent += n
                        if (f.size > 0) progress(sent.toFloat() / f.size)
                    }
                }
            }
        }
        val r = Request.Builder().url("${Config.SUPABASE_URL}/storage/v1/object/songs/$path")
            .header("apikey", Config.SUPABASE_KEY).header("Authorization", "Bearer ${token()}")
            .header("x-upsert", if (upsert) "true" else "false").post(body).build()
        Supa.http.newBuilder().writeTimeout(10, java.util.concurrent.TimeUnit.MINUTES).build().newCall(r).execute().use { res ->
            if (!res.isSuccessful) {
                val txt = res.body?.string() ?: ""
                throw RuntimeException(friendly(try { JSONObject(txt).optString("message", "HTTP ${res.code}") } catch (e: Exception) { "HTTP ${res.code}" }))
            }
        }
    }

    private suspend fun removeObjects(paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        val r = Request.Builder().url("${Config.SUPABASE_URL}/storage/v1/object/songs")
            .header("apikey", Config.SUPABASE_KEY).header("Authorization", "Bearer ${token()}")
            .delete(JSONObject().put("prefixes", JSONArray(paths)).toString().toRequestBody(JSON)).build()
        try { Supa.http.newCall(r).execute().close() } catch (e: Exception) {}
    }

    private val MIME = mapOf("mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "ogg" to "audio/ogg", "wav" to "audio/wav",
        "mp4" to "video/mp4", "webm" to "video/webm", "mkv" to "video/x-matroska",
        "mid" to "audio/midi", "midi" to "audio/midi", "kar" to "audio/midi")
    val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "wav")
    val VIDEO = setOf("mp4", "webm", "mkv")
    val MIDI = setOf("mid", "midi", "kar")

    /** Upload ng isang kanta (audio o video) + opsyonal na .lrc. Ibinabalik ang code ng kanta. */
    suspend fun uploadSong(ctx: Context, media: PickedFile, lrc: PickedFile?, title: String, artist: String, category: String?, progress: (Float) -> Unit): Int {
        // MIDI/KAR: kind na "audio" pero nasa sariling folder na midi/ (nakikilala ng TV sa extension)
        val kind = when (media.ext) { in AUDIO, in MIDI -> "audio"; in VIDEO -> "video"; else -> throw RuntimeException(tr("Hindi suportadong file. MP3, M4A, MP4, WEBM, MID o KAR lang.", "Unsupported file. Use MP3, M4A, MP4, WEBM, MID or KAR.")) }
        if (media.size > MAX_BYTES) throw RuntimeException(tr("Masyadong malaki (max 50 MB).", "Too large (max 50 MB)."))
        val id = UUID.randomUUID().toString()
        val mediaPath = (if (media.ext in MIDI) "midi/" else "media/") + "$id.${media.ext}"
        putObject(ctx, mediaPath, media, MIME[media.ext] ?: media.mime, false, progress)
        var lrcPath: String? = null
        if (lrc != null) {
            lrcPath = "lrc/$id.lrc"
            try { putObject(ctx, lrcPath, lrc, "text/plain; charset=utf-8", true) {} } catch (e: Exception) { lrcPath = null }
        }
        val o = JSONObject().put("title", title.trim().take(200)).put("artist", artist.trim().take(200).ifEmpty { JSONObject.NULL })
            .put("kind", kind).put("media_path", mediaPath).put("lrc_path", lrcPath ?: JSONObject.NULL)
            .put("size_bytes", media.size + (lrc?.size ?: 0)).put("category", category ?: JSONObject.NULL)
            .put("license_note", LICENSE_UPLOAD)
        return try {
            JSONArray(rest("POST", "songs", mapOf("select" to "code"), o.toString())).getJSONObject(0).optInt("code")
        } catch (e: Exception) {
            removeObjects(listOfNotNull(mediaPath, lrcPath)); throw e
        }
    }

    /** Dagdag na YouTube na kanta (naka-embed lang, hindi dina-download). */
    suspend fun addYouTube(ytId: String, title: String, artist: String, category: String?, channel: String): Int {
        val o = JSONObject().put("title", title.trim().take(200)).put("artist", artist.trim().take(200).ifEmpty { JSONObject.NULL })
            .put("kind", "youtube").put("yt_id", ytId).put("category", category ?: JSONObject.NULL).put("license_note", licenseYouTube(channel))
        return JSONArray(rest("POST", "songs", mapOf("select" to "code"), o.toString())).getJSONObject(0).optInt("code")
    }

    /** Background video (BGV) para sa TV: folder na bgv/ at table na backgrounds (kapareho ng web). */
    suspend fun uploadBackground(ctx: Context, f: PickedFile, title: String, progress: (Float) -> Unit) {
        if (f.ext !in VIDEO) throw RuntimeException(tr("Video lang (MP4 o WEBM).", "Video only (MP4 or WEBM)."))
        if (f.size > MAX_BYTES) throw RuntimeException(tr("Masyadong malaki (max 50 MB).", "Too large (max 50 MB)."))
        val path = "bgv/${UUID.randomUUID()}.${f.ext}"
        putObject(ctx, path, f, MIME[f.ext] ?: f.mime, false, progress)
        try { rest("POST", "backgrounds", emptyMap(), JSONObject().put("title", title.trim().ifEmpty { f.name }.take(120)).put("path", path).put("size_bytes", f.size).toString()) }
        catch (e: Exception) { removeObjects(listOf(path)); throw e }
    }

    suspend fun deleteBackground(id: String, path: String) {
        rest("DELETE", "backgrounds", mapOf("id" to "eq.$id"), null)
        removeObjects(listOf(path))
    }

    suspend fun updateSong(id: String, title: String, artist: String, category: String?) {
        rest("PATCH", "songs", mapOf("id" to "eq.$id"), JSONObject().put("title", title.trim().take(200))
            .put("artist", artist.trim().take(200).ifEmpty { JSONObject.NULL }).put("category", category ?: JSONObject.NULL).toString())
    }

    /** Palitan o idagdag ang lyrics (.lrc) ng kanta. */
    suspend fun setLrc(ctx: Context, s: Song, lrc: PickedFile) {
        val path = if (s.lrcPath != null && !s.lrcPath.startsWith("site:")) s.lrcPath else "lrc/${s.id}.lrc"
        putObject(ctx, path, lrc, "text/plain; charset=utf-8", true) {}
        rest("PATCH", "songs", mapOf("id" to "eq.${s.id}"), JSONObject().put("lrc_path", path).toString())
    }

    suspend fun deleteSong(s: Song) {
        rest("DELETE", "songs", mapOf("id" to "eq.${s.id}"), null)
        removeObjects(listOfNotNull(s.mediaPath, s.lrcPath).filter { !it.startsWith("site:") })
    }

    private fun friendly(m: String): String = when {
        m.contains("Invalid login", true) -> tr("Mali ang email o password.", "Wrong email or password.")
        m.contains("Email not confirmed", true) -> tr("Hindi pa kumpirmado ang email. Buksan ang link sa email mo.", "Email not confirmed yet. Open the link in your email.")
        m.contains("row-level security", true) || m.contains("Unauthorized", true) || m.contains("403") ->
            tr("Admin lang ang pwedeng magdagdag o magbura ng kanta.", "Only the admin can add or delete songs.")
        m.contains("already exists", true) || m.contains("Duplicate", true) -> tr("Meron nang ganitong file.", "This file already exists.")
        m.contains("exceeded", true) || m.contains("too large", true) -> tr("Masyadong malaki ang file (max 50 MB).", "File too large (max 50 MB).")
        else -> m
    }
}
