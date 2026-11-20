package com.videoketana.app.tv

import android.content.Context
import android.media.MediaPlayer
import com.videoketana.app.data.Supa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/** Totoo kung MIDI/KAR ang file (ayon sa extension). */
fun isMidiPath(p: String?) = p != null && Regex("\\.(mid|midi|kar)$", RegexOption.IGNORE_CASE).containsMatchIn(p)

/**
 * Basahin ang lyrics na nasa loob ng MIDI/KAR.
 * KAR: text event (FF 01); "/" = bagong linya, "\" = bagong talata, "@" = header (hindi lyrics).
 * Karaniwang MIDI: lyric event (FF 05); bagong linya kapag may \r o \n.
 */
fun parseMidiLyrics(b: ByteArray): List<LyricLine> {
    try {
        var p = 0
        fun u8() = b[p++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun vlq(): Int { var v = 0; while (true) { val c = u8(); v = (v shl 7) or (c and 0x7F); if (c and 0x80 == 0) return v } }
        if (String(b, 0, 4, Charsets.ISO_8859_1) != "MThd") return emptyList()
        p = 4; val hl = u32(); val start = p
        u16(); val ntr = u16(); val div = u16()
        p = start + hl
        if (div and 0x8000 != 0) return emptyList()       // SMPTE: bihira, hindi pa suportado
        val tempos = mutableListOf(0L to 500000)           // tick → microseconds bawat quarter
        val texts = mutableListOf<Triple<Long, Int, String>>()   // tick, uri (1 o 5), text
        repeat(ntr) {
            if (p + 8 > b.size) return@repeat
            val id = String(b, p, 4, Charsets.ISO_8859_1); p += 4
            val len = u32(); val end = (p + len).coerceAtMost(b.size)
            if (id != "MTrk") { p = end; return@repeat }
            var tick = 0L; var status = 0
            while (p < end) {
                tick += vlq()
                var st = u8()
                if (st < 0x80) { p--; st = status } else if (st < 0xF0) status = st
                when {
                    st == 0xFF -> {
                        val type = u8(); val l = vlq()
                        val data = b.copyOfRange(p, (p + l).coerceAtMost(b.size)); p += l
                        when (type) {
                            0x51 -> if (l == 3) tempos += tick to (((data[0].toInt() and 0xFF) shl 16) or ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF))
                            0x01, 0x05 -> texts += Triple(tick, type, String(data, Charsets.ISO_8859_1))
                            0x2F -> p = end
                        }
                    }
                    st == 0xF0 || st == 0xF7 -> { val l = vlq(); p += l }
                    else -> { val hi = st and 0xF0; p += if (hi == 0xC0 || hi == 0xD0) 1 else 2 }
                }
            }
            p = end
        }
        // tick → segundo gamit ang mga palit ng tempo
        val tm = tempos.sortedBy { it.first }
        fun sec(t: Long): Double {
            var s = 0.0; var lastT = 0L; var us = 500000
            for ((tt, u) in tm) { if (tt > t) break; s += (tt - lastT) * us / 1e6 / div; lastT = tt; us = u }
            return s + (t - lastT) * us / 1e6 / div
        }
        val useLyric = texts.any { it.second == 0x05 && it.third.isNotBlank() }
        val events = texts.filter { (if (useLyric) it.second == 0x05 else it.second == 0x01) && !it.third.startsWith("@") }.sortedBy { it.first }
        val out = mutableListOf<LyricLine>()
        var cur = StringBuilder(); var curT = -1.0
        fun flush() { val line = cur.toString().trim(); if (line.isNotBlank() && line.any { it.isLetterOrDigit() }) out += LyricLine(curT, line); cur = StringBuilder(); curT = -1.0 }
        for ((tick, _, raw) in events) {
            var t = raw
            if (t.startsWith("/") || t.startsWith("\\")) { flush(); t = t.drop(1) }
            val breakAfter = t.endsWith("\r") || t.endsWith("\n")
            if (t.startsWith("\r") || t.startsWith("\n")) flush()
            t = t.replace("\r", "").replace("\n", "").replace("#", "")
            if (t.isNotEmpty()) { if (curT < 0) curT = sec(tick); cur.append(t) }
            if (breakAfter) flush()
        }
        flush()
        return out
    } catch (e: Exception) { return emptyList() }
}

/**
 * MIDI player ng TV ng app: ang sariling synth ng Android (General MIDI).
 * Unang bersyon: simple ang tunog; papalitan ng SoundFont na engine sa susunod.
 */
class MidiPlayer {
    private var mp: MediaPlayer? = null
    var onEnd: () -> Unit = {}
    var onError: (String) -> Unit = {}

    /** I-download sa cache (kailangan ng synth ng Android ang file), basahin ang lyrics, tapos patugtugin. */
    suspend fun load(ctx: Context, url: String, volume: Int): List<LyricLine> {
        val bytes = withContext(Dispatchers.IO) {
            Supa.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
                r.body!!.bytes()
            }
        }
        val f = File(ctx.cacheDir, "song.mid"); withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        return start(f, bytes, volume)
    }

    /** MIDI na nasa device mismo (folder na VideokeTana/MIDI). */
    suspend fun loadFile(f: File, volume: Int): List<LyricLine> {
        val bytes = withContext(Dispatchers.IO) { f.readBytes() }
        return start(f, bytes, volume)
    }

    private fun start(f: File, bytes: ByteArray, volume: Int): List<LyricLine> {
        stop()
        mp = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            setOnCompletionListener { onEnd() }
            setOnErrorListener { _, w, x -> onError("$w/$x"); true }
            prepare()
            setVolume(volume / 100f, volume / 100f)
            start()
        }
        return parseMidiLyrics(bytes)
    }

    val active get() = mp != null
    val position: Long get() = try { mp?.currentPosition?.toLong() ?: 0L } catch (e: Exception) { 0L }
    fun pause() { try { mp?.pause() } catch (e: Exception) {} }
    fun play() { try { mp?.start() } catch (e: Exception) {} }
    fun restart() { try { mp?.seekTo(0); mp?.start() } catch (e: Exception) {} }
    fun volume(v: Int) { try { mp?.setVolume(v / 100f, v / 100f) } catch (e: Exception) {} }
    fun stop() { try { mp?.release() } catch (e: Exception) {}; mp = null }
}
