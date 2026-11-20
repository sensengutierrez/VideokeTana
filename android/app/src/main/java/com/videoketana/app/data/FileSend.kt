package com.videoketana.app.data

import android.util.Base64
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

/**
 * Pagpasa ng kanta mula sa phone (remote) papunta sa TV gamit ang Realtime broadcast, paisa-isang piraso.
 * Para sa kantang nasa folder ng phone (hal. MIDI) pero wala sa TV. Hindi dumadaan sa storage at hindi naiimbak sa server.
 */
object FileSend {
    const val CHUNK = 48 * 1024
    const val MAX = 8L * 1024 * 1024

    suspend fun send(rt: Realtime?, qid: String, f: File) {
        val bytes = f.readBytes()
        val n = (bytes.size + CHUNK - 1) / CHUNK
        for (i in 0 until n) {
            val part = bytes.copyOfRange(i * CHUNK, minOf(bytes.size, (i + 1) * CHUNK))
            rt?.broadcast("file", JSONObject().put("qid", qid).put("i", i).put("n", n).put("ext", f.extension.lowercase())
                .put("data", Base64.encodeToString(part, Base64.NO_WRAP)))
            delay(70)
        }
    }

    /** Taga-buo sa TV. */
    class Receiver(private val dir: File) {
        private val parts = HashMap<String, Array<String?>>()
        init { dir.mkdirs() }
        fun file(qid: String): File? = dir.listFiles()?.firstOrNull { it.nameWithoutExtension == qid && it.length() > 0 }
        fun onPart(p: JSONObject) {
            val qid = p.optString("qid").filter { it.isLetterOrDigit() || it == '-' }; if (qid.isEmpty()) return
            val n = p.optInt("n"); val i = p.optInt("i"); if (n <= 0 || i !in 0 until n || n > 400) return
            val arr = parts.getOrPut(qid) { arrayOfNulls(n) }; if (arr.size != n) return
            arr[i] = p.optString("data")
            if (arr.all { it != null }) {
                parts.remove(qid)
                val ext = p.optString("ext").filter { it.isLetterOrDigit() }.take(5)
                try { File(dir, "$qid.$ext").outputStream().use { o -> arr.forEach { o.write(Base64.decode(it, Base64.NO_WRAP)) } } } catch (e: Exception) { }
            }
        }
        /** Burahin ang mga natanggap na wala na sa pila (at lumampas na sa 2 minuto). */
        fun clean(keep: Set<String>) {
            val old = System.currentTimeMillis() - 120_000
            dir.listFiles()?.forEach { if (it.nameWithoutExtension !in keep && it.lastModified() < old) it.delete() }
        }
    }
}
