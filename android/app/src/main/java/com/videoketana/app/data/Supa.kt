package com.videoketana.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Mga setting ng server (kapareho ng frontend/assets/js/config.js). */
object Config {
    const val SUPABASE_URL = "https://sminngwxvaceiteuuorf.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_2nyJAzO_hJnq6ItAkIJo6w_2IOc-mur"
    const val SITE = "https://videoketana.tigomenote.com/"
    const val REPO = "sensengutierrez/VideokeTana"
    const val APK_URL = "https://github.com/$REPO/releases/latest/download/VideokeTana.apk"
}

data class Song(
    val id: String,
    val code: Int,
    val title: String,
    val artist: String,
    val kind: String,          // audio | video | youtube
    val mediaPath: String?,
    val lrcPath: String?,
    val ytId: String?,
    val category: String?,
    val createdAt: String?
) {
    companion object {
        fun from(o: JSONObject) = Song(
            id = o.optString("id"),
            code = o.optInt("code"),
            title = o.optString("title"),
            artist = o.optStr("artist") ?: "",
            kind = o.optStr("kind") ?: "audio",
            mediaPath = o.optStr("media_path"),
            lrcPath = o.optStr("lrc_path"),
            ytId = o.optStr("yt_id"),
            category = o.optStr("category"),
            createdAt = o.optStr("created_at")
        )
    }
}

/** Isang kanta sa pila. */
data class QItem(
    val id: String,
    val room: String,
    val status: String,        // waiting | playing
    val sortOrder: Double,
    val singer: String?,
    val song: Song?,
    val ytId: String?,
    val ytTitle: String?,
    val localCode: Int?,
    val localTitle: String?
) {
    val code: String get() = song?.code?.toString() ?: localCode?.toString() ?: "YT"
    val title: String get() = song?.title ?: localTitle ?: ytTitle ?: "YouTube video"
    val artist: String get() = song?.artist ?: if (ytId != null) "YouTube" else ""
    val source: String get() = song?.kind ?: if (localCode != null) "local" else "youtube"

    companion object {
        fun from(o: JSONObject) = QItem(
            id = o.optString("id"),
            room = o.optString("room"),
            status = o.optString("status", "waiting"),
            sortOrder = o.optDouble("sort_order", 0.0),
            singer = o.optStr("singer"),
            song = o.optJSONObject("song")?.let { Song.from(it) },
            ytId = o.optStr("yt_id"),
            ytTitle = o.optStr("yt_title"),
            localCode = if (o.isNull("local_code") || !o.has("local_code")) null else o.optInt("local_code"),
            localTitle = o.optStr("local_title")
        )
    }
}

fun JSONObject.optStr(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifEmpty { null }

/** Maliit na client para sa Supabase REST (PostgREST). Walang login: publishable key lang, gaya ng web. */
object Supa {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()

    private fun req(path: String, params: Map<String, String> = emptyMap()): Request.Builder {
        val b = "${Config.SUPABASE_URL}/rest/v1/$path".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return Request.Builder().url(b.build()).header("apikey", Config.SUPABASE_KEY)
    }

    private suspend fun call(r: Request): String = withContext(Dispatchers.IO) {
        http.newCall(r).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw RuntimeException(errText(body, res.code))
            body
        }
    }

    private fun errText(body: String, code: Int) = try { JSONObject(body).optString("message", "HTTP $code") } catch (e: Exception) { "HTTP $code" }

    private fun arr(s: String): JSONArray = if (s.isBlank()) JSONArray() else JSONArray(s)

    /** Public URL ng file: "site:demo/x.mp3" ay nasa website; iba ay nasa storage bucket na songs. */
    fun mediaUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("site:")) return Config.SITE + path.removePrefix("site:")
        return "${Config.SUPABASE_URL}/storage/v1/object/public/songs/" + path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    }

    private const val SONG_COLS = "id,code,title,artist,kind,media_path,lrc_path,yt_id,category,created_at"

    suspend fun songs(query: String = "", limit: Int = 300): List<Song> {
        val p = mutableMapOf("select" to SONG_COLS, "order" to "code.asc", "limit" to "$limit")
        val q = query.trim().replace(Regex("[,()*%\\\\]"), " ").trim()
        if (q.isNotEmpty()) {
            p["or"] = if (q.matches(Regex("\\d{1,5}"))) "(title.ilike.*$q*,artist.ilike.*$q*,code.eq.${q.toInt()})"
            else "(title.ilike.*$q*,artist.ilike.*$q*)"
        }
        val a = arr(call(req("songs", p).get().build()))
        return (0 until a.length()).map { Song.from(a.getJSONObject(it)) }
    }

    suspend fun songCount(): Int {
        val r = req("songs", mapOf("select" to "id")).head().header("Prefer", "count=exact").header("Range", "0-0").build()
        return withContext(Dispatchers.IO) {
            Supa.http.newCall(r).execute().use { it.header("Content-Range")?.substringAfter("/")?.toIntOrNull() ?: 0 }
        }
    }

    suspend fun songByCode(code: Int): Song? {
        val a = arr(call(req("songs", mapOf("select" to SONG_COLS, "code" to "eq.$code", "limit" to "1")).get().build()))
        return if (a.length() > 0) Song.from(a.getJSONObject(0)) else null
    }

    suspend fun queue(room: String): List<QItem> {
        val a = arr(call(req("queue", mapOf(
            "select" to "*,song:songs($SONG_COLS)",
            "room" to "eq.$room",
            "order" to "sort_order.asc"
        )).get().build()))
        val list = (0 until a.length()).map { QItem.from(a.getJSONObject(it)) }
        // kinakanta muna, tapos ang naghihintay ayon sa pagkakasunod
        return list.sortedWith(compareBy<QItem> { if (it.status == "playing") 0 else 1 }.thenBy { it.sortOrder })
    }

    suspend fun reserve(room: String, singer: String?, songId: String? = null, ytId: String? = null, ytTitle: String? = null, localCode: Int? = null, localTitle: String? = null): String {
        val o = JSONObject().put("room", room).put("sort_order", System.currentTimeMillis().toDouble())
        if (!singer.isNullOrBlank()) o.put("singer", singer.take(40))
        songId?.let { o.put("song_id", it) }
        ytId?.let { o.put("yt_id", it) }
        ytTitle?.let { o.put("yt_title", it.take(200)) }
        localCode?.let { o.put("local_code", it) }
        localTitle?.let { o.put("local_title", it.take(200)) }
        val res = call(req("queue", mapOf("select" to "id")).post(o.toString().toRequestBody(JSON)).header("Prefer", "return=representation").build())
        return try { JSONArray(res).getJSONObject(0).optString("id") } catch (e: Exception) { "" }
    }

    suspend fun removeFromQueue(id: String) { call(req("queue", mapOf("id" to "eq.$id")).delete().build()) }

    suspend fun setStatus(id: String, status: String) {
        call(req("queue", mapOf("id" to "eq.$id")).patch(JSONObject().put("status", status).toString().toRequestBody(JSON)).build())
    }

    suspend fun moveToTop(id: String, minOrder: Double) {
        call(req("queue", mapOf("id" to "eq.$id")).patch(JSONObject().put("sort_order", minOrder - 1).toString().toRequestBody(JSON)).build())
    }

    /** YouTube search sa pamamagitan ng Edge Function (nakatago ang API key sa server). */
    suspend fun ytSearch(q: String, latest: Boolean = false): JSONObject {
        val url = "${Config.SUPABASE_URL}/functions/v1/yt-search".toHttpUrl().newBuilder()
            .addQueryParameter("mode", if (latest) "latest" else "search").addQueryParameter("q", q).build()
        val r = Request.Builder().url(url).header("apikey", Config.SUPABASE_KEY).header("Origin", "https://videoketana.tigomenote.com").build()
        return withContext(Dispatchers.IO) { http.newCall(r).execute().use { JSONObject(it.body?.string() ?: "{}") } }
    }

    suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() ?: "" else "" }
    }
}

/** YouTube video id mula sa link o id. */
fun parseYouTubeId(input: String): String? {
    val s = input.trim()
    if (s.matches(Regex("^[\\w-]{11}$"))) return s
    return Regex("(?:v=|youtu\\.be/|shorts/|embed/|live/)([\\w-]{11})").find(s)?.groupValues?.get(1)
}
