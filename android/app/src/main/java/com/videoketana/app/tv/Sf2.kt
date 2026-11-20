package com.videoketana.app.tv

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.videoketana.app.data.Config
import com.videoketana.app.data.LocalLib
import com.videoketana.app.data.Supa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/*
 * SoundFont (SF2) engine ng app: kapareho ng web (frontend/assets/js/sf2.js) para pareho ang tunog.
 * Tumutugtog nang real-time sa AudioTrack (walang hinihintay na render), kaya kaya rin ang Key at Tempo.
 */

// ===================== MIDI =====================
class MNote(val t: Double, var dur: Double, val midi: Int, val ch: Int, val v: Int, val cg: Double, val bank: Int, val prog: Int, val pan: Int, val bend: Double,
            val rvb: Double = 0.3, val cho: Double = 0.0)
/** Pagbabago sa paglipas ng oras bawat channel: (segundo, halaga). */
class Timeline { val t = ArrayList<Double>(); val v = ArrayList<Double>()
    fun add(at: Double, x: Double) { if (t.isNotEmpty() && t.last() == at) v[v.size - 1] = x else if (v.isEmpty() || v.last() != x) { t += at; v += x } }
    fun at(x: Double): Double { var lo = 0; var hi = t.size - 1; if (hi < 0) return 0.0; while (lo < hi) { val m = (lo + hi + 1) / 2; if (t[m] <= x) lo = m else hi = m - 1 }; return v[lo] } }
class MidiSong(val notes: List<MNote>, val lyrics: List<LyricLine>, val duration: Double,
               val bend: Array<Timeline> = Array(16) { Timeline().apply { add(0.0, 0.0) } },
               val gain: Array<Timeline> = Array(16) { Timeline().apply { add(0.0, 100 / 127.0) } },
               val mod: Array<Timeline> = Array(16) { Timeline().apply { add(0.0, 0.0) } })

fun parseMidiSong(b: ByteArray): MidiSong {
    var p = 0
    fun u8() = b[p++].toInt() and 0xFF
    fun u16() = (u8() shl 8) or u8()
    fun u32() = (u16() shl 16) or u16()
    fun vlq(): Int { var v = 0; while (true) { val c = u8(); v = (v shl 7) or (c and 0x7F); if (c and 0x80 == 0) return v } }
    require(String(b, 0, 4, Charsets.ISO_8859_1) == "MThd") { "Hindi MIDI file" }
    p = 4; val hl = u32(); val s0 = p; u16(); val ntr = u16(); val div = u16(); p = s0 + hl
    require(div and 0x8000 == 0) { "Hindi suportadong MIDI (SMPTE)" }
    class Ev(val tick: Long, val k: Int, val ch: Int, val a: Int, val b: Int)   // k: 1 on, 0 off, 2 prog, 3 cc, 4 bend
    val tempos = mutableListOf(0L to 500000)
    val ev = ArrayList<Ev>()
    repeat(ntr) {
        if (p + 8 > b.size) return@repeat
        val id = String(b, p, 4, Charsets.ISO_8859_1); p += 4
        val len = u32(); val end = min(p + len, b.size)
        if (id != "MTrk") { p = end; return@repeat }
        var tick = 0L; var status = 0
        while (p < end) {
            tick += vlq()
            var st = u8()
            if (st < 0x80) { p--; st = status } else if (st < 0xF0) status = st
            when {
                st == 0xFF -> { val type = u8(); val l = vlq()
                    if (type == 0x51 && l == 3) tempos += tick to (((b[p].toInt() and 0xFF) shl 16) or ((b[p + 1].toInt() and 0xFF) shl 8) or (b[p + 2].toInt() and 0xFF))
                    p += l; if (type == 0x2F) p = end }
                st == 0xF0 || st == 0xF7 -> { val l = vlq(); p += l }
                else -> {
                    val hi = st and 0xF0; val ch = st and 0x0F
                    val d1 = u8(); val d2 = if (hi == 0xC0 || hi == 0xD0) 0 else u8()
                    when {
                        hi == 0x90 && d2 > 0 -> ev += Ev(tick, 1, ch, d1, d2)
                        hi == 0x80 || hi == 0x90 -> ev += Ev(tick, 0, ch, d1, 0)
                        hi == 0xC0 -> ev += Ev(tick, 2, ch, d1, 0)
                        hi == 0xB0 -> ev += Ev(tick, 3, ch, d1, d2)
                        hi == 0xE0 -> ev += Ev(tick, 4, ch, ((d2 shl 7) or d1) - 8192, 0)
                    }
                }
            }
        }
        p = end
    }
    val tm = tempos.sortedBy { it.first }
    fun sec(t: Long): Double { var s = 0.0; var lt = 0L; var us = 500000; for ((tt, u) in tm) { if (tt > t) break; s += (tt - lt) * us / 1e6 / div; lt = tt; us = u }; return s + (t - lt) * us / 1e6 / div }
    ev.sortWith(compareBy<Ev> { it.tick }.thenBy { if (it.k == 0) 0 else 1 })
    val prog = IntArray(16); val vol = IntArray(16) { 100 }; val expr = IntArray(16) { 127 }; val bank = IntArray(16); val pan = IntArray(16) { 64 }
    val bend = DoubleArray(16); val sus = BooleanArray(16)
    val rvb = IntArray(16) { 40 }; val cho = IntArray(16); val bRange = IntArray(16) { 2 }; val rpn = IntArray(16) { 16383 }
    val tlBend = Array(16) { Timeline().apply { add(0.0, 0.0) } }; val tlGain = Array(16) { Timeline().apply { add(0.0, 100 / 127.0) } }; val tlMod = Array(16) { Timeline().apply { add(0.0, 0.0) } }
    val open = HashMap<Int, MNote>(); val held = Array(16) { ArrayList<MNote>() }
    val notes = ArrayList<MNote>()
    fun close(n: MNote, t: Double) { n.dur = max(0.05, t - n.t) }
    for (e in ev) {
        val t = sec(e.tick)
        when (e.k) {
            2 -> prog[e.ch] = e.a
            4 -> { bend[e.ch] = e.a / 8192.0 * bRange[e.ch]; tlBend[e.ch].add(t, bend[e.ch]) }
            3 -> {
                when (e.a) {
                    7 -> vol[e.ch] = e.b; 11 -> expr[e.ch] = e.b; 0 -> bank[e.ch] = e.b; 10 -> pan[e.ch] = e.b
                    91 -> rvb[e.ch] = e.b; 93 -> cho[e.ch] = e.b
                    1 -> tlMod[e.ch].add(t, e.b / 127.0)
                    101 -> rpn[e.ch] = (rpn[e.ch] and 0x7F) or (e.b shl 7)
                    100 -> rpn[e.ch] = (rpn[e.ch] and 0x7F.inv()) or e.b
                    6 -> if (rpn[e.ch] == 0) bRange[e.ch] = e.b.coerceIn(1, 24)
                    64 -> { val on = e.b >= 64; if (sus[e.ch] && !on) { held[e.ch].forEach { close(it, t) }; held[e.ch].clear() }; sus[e.ch] = on }
                    121 -> { vol[e.ch] = 100; expr[e.ch] = 127; bend[e.ch] = 0.0; tlBend[e.ch].add(t, 0.0); tlMod[e.ch].add(t, 0.0) }
                }
                if (e.a == 7 || e.a == 11 || e.a == 121) tlGain[e.ch].add(t, vol[e.ch] / 127.0 * expr[e.ch] / 127.0)
            }
            1 -> { val key = e.ch * 128 + e.a; open[key]?.let { close(it, t) }
                val n = MNote(t, 0.5, e.a, e.ch, e.b, vol[e.ch] / 127.0 * expr[e.ch] / 127.0, bank[e.ch], prog[e.ch], pan[e.ch], bend[e.ch], rvb[e.ch] / 127.0, cho[e.ch] / 127.0); notes += n; open[key] = n }
            0 -> { val key = e.ch * 128 + e.a; val n = open.remove(key); if (n != null) { if (sus[e.ch]) held[e.ch] += n else close(n, t) } }
        }
    }
    open.values.forEach { close(it, it.t + 1) }; held.forEach { h -> h.forEach { close(it, it.t + 1) } }
    notes.sortBy { it.t }
    val dur = notes.maxOfOrNull { it.t + it.dur } ?: 0.0
    return MidiSong(notes, parseMidiLyrics(b), dur + 2, tlBend, tlGain, tlMod)
}

// ===================== SoundFont =====================
class SfZone(val g: HashMap<Int, Int>, val lo: Int, val hi: Int, val vlo: Int, val vhi: Int, val ref: Int)
class SfSet(val global: SfZone?, val zones: List<SfZone>)
class SfSample(val start: Int, val end: Int, val ls: Int, val le: Int, val rate: Int, val pitch: Int, val corr: Int)

class SoundFont(ch: FileChannel, base: Long, size: Long) {
    constructor(file: File) : this(RandomAccessFile(file, "r").channel, 0L, file.length())
    companion object {
        /** Mula sa APK (assets, hindi naka-compress): mmap nang diretso, walang kopya. */
        fun fromAsset(ctx: Context, name: String): SoundFont {
            val fd = ctx.assets.openFd(name)
            val ch = java.io.FileInputStream(fd.fileDescriptor).channel
            return SoundFont(ch, fd.startOffset, fd.length)
        }
    }

    val presets = HashMap<Int, SfSet>()
    val instruments = ArrayList<SfSet>()
    val shdr = ArrayList<SfSample>()
    val data: ShortBuffer
    val count: Int

    init {
        val map = ch.map(FileChannel.MapMode.READ_ONLY, base, size).order(ByteOrder.LITTLE_ENDIAN)
        fun str(o: Int, n: Int): String { val sb = StringBuilder(); for (i in 0 until n) { val c = map.get(o + i).toInt() and 0xFF; if (c == 0) break; sb.append(c.toChar()) }; return sb.toString() }
        require(str(0, 4) == "RIFF" && str(8, 4) == "sfbk") { "Hindi SoundFont (.sf2) ang file" }
        val chunks = HashMap<String, Pair<Int, Int>>()
        var smpl = 0 to 0
        fun walk(o0: Int, end: Int) {
            var o = o0
            while (o + 8 <= end) {
                val id = str(o, 4); val len = map.getInt(o + 4)
                when (id) { "LIST" -> walk(o + 12, o + 8 + len); "smpl" -> smpl = (o + 8) to len; else -> chunks[id] = (o + 8) to len }
                o += 8 + len + (len and 1)
            }
        }
        walk(12, size.toInt())
        listOf("phdr", "pbag", "pgen", "inst", "ibag", "igen", "shdr").forEach { require(chunks.containsKey(it)) { "Sirang SoundFont: walang $it" } }
        fun u16(o: Int) = map.getShort(o).toInt() and 0xFFFF
        fun <T> recs(k: String, size: Int, f: (Int) -> T): List<T> { val (o, len) = chunks[k]!!; return (0 until len / size).map { f(o + it * size) } }
        class Gen(val op: Int, val amt: Int, val lo: Int, val hi: Int)
        val phdr = recs("phdr", 38) { o -> Triple(u16(o + 20), u16(o + 22), u16(o + 24)) }    // preset, bank, bag
        val pbag = recs("pbag", 4) { u16(it) }
        val pgen = recs("pgen", 4) { o -> Gen(u16(o), map.getShort(o + 2).toInt(), map.get(o + 2).toInt() and 0xFF, map.get(o + 3).toInt() and 0xFF) }
        val inst = recs("inst", 22) { o -> u16(o + 20) }
        val ibag = recs("ibag", 4) { u16(it) }
        val igen = recs("igen", 4) { o -> Gen(u16(o), map.getShort(o + 2).toInt(), map.get(o + 2).toInt() and 0xFF, map.get(o + 3).toInt() and 0xFF) }
        recs("shdr", 46) { o -> SfSample(map.getInt(o + 20), map.getInt(o + 24), map.getInt(o + 28), map.getInt(o + 32), map.getInt(o + 36), map.get(o + 40).toInt() and 0xFF, map.get(o + 41).toInt()) }.let { shdr.addAll(it) }
        fun zones(bags: List<Int>, gens: List<Gen>, i0: Int, i1: Int, refOp: Int): SfSet {
            val out = ArrayList<SfZone>(); var global: SfZone? = null
            for (bI in i0 until i1) {
                val g = HashMap<Int, Int>(); var lo = 0; var hi = 127; var vlo = 0; var vhi = 127; var ref = -1
                for (k in bags[bI] until bags.getOrElse(bI + 1) { bags[bI] }) {
                    val x = gens.getOrNull(k) ?: continue
                    when (x.op) { 43 -> { lo = x.lo; hi = x.hi }; 44 -> { vlo = x.lo; vhi = x.hi }; refOp -> ref = x.amt and 0xFFFF; else -> g[x.op] = x.amt }
                }
                val z = SfZone(g, lo, hi, vlo, vhi, ref)
                if (ref < 0) { if (bI == i0) global = z } else out += z
            }
            return SfSet(global, out)
        }
        for (i in 0 until inst.size - 1) instruments += zones(ibag, igen, inst[i], inst[i + 1], 53)
        for (i in 0 until phdr.size - 1) presets[phdr[i].second * 128 + phdr[i].first] = zones(pbag, pgen, phdr[i].third, phdr[i + 1].third, 41)
        map.position(smpl.first)
        val sb = map.slice().order(ByteOrder.LITTLE_ENDIAN)
        sb.limit(smpl.second and 1.inv())
        data = sb.asShortBuffer()
        count = data.limit()
    }

    class Region(val g: HashMap<Int, Int>, val s: SfSample)
    private val skip = setOf(0, 1, 2, 3, 4, 12, 45, 50, 41, 53, 54, 57, 58, 46, 47, 43, 44)
    fun regions(bank: Int, prog: Int, key: Int, vel: Int, drum: Boolean): List<Region> {
        val p = presets[(if (drum) 128 else bank) * 128 + prog] ?: presets[(if (drum) 128 else 0) * 128 + prog] ?: (if (drum) presets[128 * 128] else null) ?: presets[0] ?: return emptyList()
        val out = ArrayList<Region>()
        for (pz in p.zones) {
            if (key < pz.lo || key > pz.hi || vel < pz.vlo || vel > pz.vhi) continue
            val ins = instruments.getOrNull(pz.ref) ?: continue
            for (iz in ins.zones) {
                if (key < iz.lo || key > iz.hi || vel < iz.vlo || vel > iz.vhi) continue
                val g = HashMap<Int, Int>(); ins.global?.let { g.putAll(it.g) }; g.putAll(iz.g)
                val add = HashMap<Int, Int>(); p.global?.let { add.putAll(it.g) }; add.putAll(pz.g)
                for ((k, v) in add) if (k !in skip) g[k] = (g[k] ?: 0) + v
                shdr.getOrNull(iz.ref)?.let { out += Region(g, it) }
            }
        }
        return out
    }
}

/** Saan kukunin ang SoundFont: sariling .sf2 sa Download/VideokeTana/SoundFont, o GeneralUser GS mula sa site (isang beses i-download). */
object SoundFonts {
    const val DEFAULT = "GeneralUser-GS.sf2"
    @Volatile private var cached: Pair<String, SoundFont>? = null
    val folder: File get() = File(LocalLib.root, "SoundFont")
    fun localFiles(): List<File> = try { folder.listFiles()?.filter { it.isFile && it.extension.equals("sf2", true) && it.length() > 100_000 }?.sortedBy { it.name.lowercase() } ?: emptyList() } catch (e: Exception) { emptyList() }

    /** Saan nakalagay ang GeneralUser GS: sa folder na Download/VideokeTana/SoundFont (permanente, nakikita), o sa loob ng app. */
    fun defaultFile(ctx: Context): File? =
        listOf(File(folder, DEFAULT), File(ctx.filesDir, DEFAULT)).firstOrNull { it.exists() && it.length() > 1_000_000 }

    /** Ang napili sa Settings ("" = GeneralUser GS). */
    fun chosenName(): String = com.videoketana.app.data.Prefs.str("soundfont")

    /** I-download ang GeneralUser GS (isang beses). Inilalagay sa folder kung may access, para hindi na ulit kahit i-reinstall. */
    fun download(ctx: Context, progress: (Float) -> Unit = {}): File {
        val canFolder = LocalLib.hasPermission(ctx) && (folder.isDirectory || folder.mkdirs())
        val dst = if (canFolder) File(folder, DEFAULT) else File(ctx.filesDir, DEFAULT)
        val tmp = File(dst.parentFile, "sf-download.tmp")
        Supa.http.newBuilder().readTimeout(5, java.util.concurrent.TimeUnit.MINUTES).build()
            .newCall(Request.Builder().url(Config.SITE + "sf/" + DEFAULT).build()).execute().use { r ->
                if (!r.isSuccessful) throw RuntimeException("SoundFont: HTTP ${r.code}")
                val total = r.body!!.contentLength()
                tmp.outputStream().use { o ->
                    r.body!!.byteStream().use { i ->
                        val buf = ByteArray(64 * 1024); var got = 0L
                        while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); got += n; if (total > 0) progress(got.toFloat() / total) }
                    }
                }
            }
        if (dst.exists()) dst.delete()
        if (!tmp.renameTo(dst)) { tmp.copyTo(dst, true); tmp.delete() }
        return dst
    }

    private val lock = kotlinx.coroutines.sync.Mutex()
    /** Kasama na sa app ang GeneralUser GS? (mula sa APK) */
    fun bundled(ctx: Context): Boolean = try { ctx.assets.openFd(DEFAULT).use { it.length > 1_000_000 } } catch (e: Exception) { false }

    suspend fun get(ctx: Context, onStatus: (String) -> Unit = {}): SoundFont = withContext(Dispatchers.IO) {
        lock.lock()
        try {
            val chosen = chosenName()
            val own = localFiles().firstOrNull { chosen.isNotEmpty() && it.name == chosen }
            when {
                own != null -> { val key = own.absolutePath + ":" + own.length(); cached?.takeIf { it.first == key }?.second ?: SoundFont(own).also { cached = key to it } }
                bundled(ctx) -> cached?.takeIf { it.first == "asset" }?.second ?: SoundFont.fromAsset(ctx, DEFAULT).also { cached = "asset" to it }
                else -> {
                    val f = defaultFile(ctx) ?: run { onStatus("download"); download(ctx) }
                    val key = f.absolutePath + ":" + f.length()
                    cached?.takeIf { it.first == key }?.second ?: SoundFont(f).also { cached = key to it }
                }
            }
        } finally { lock.unlock() }
    }

    /** Maikling tugtog para subukan ang SoundFont: piano, bass, strings at drums (mga 6 segundo). */
    fun testSong(): MidiSong {
        val n = ArrayList<MNote>()
        fun add(t: Double, d: Double, m: Int, ch: Int, prog: Int, v: Int = 100) { n += MNote(t, d, m, ch, v, 0.8, 0, prog, 64, 0.0) }
        val chords = listOf(listOf(60, 64, 67), listOf(57, 60, 64), listOf(53, 57, 60), listOf(55, 59, 62))
        chords.forEachIndexed { i, c ->
            val t = i * 1.4
            c.forEach { add(t, 1.3, it, 0, 0) }                 // piano
            c.forEach { add(t, 1.4, it + 12, 1, 48, 60) }       // strings
            add(t, 0.6, c[0] - 24, 2, 33); add(t + 0.7, 0.6, c[0] - 24, 2, 33)   // bass
            add(t, 0.2, 36, 9, 0); add(t + 0.7, 0.2, 38, 9, 0)  // kick, snare
            for (h in 0 until 4) add(t + h * 0.35, 0.1, 42, 9, 0, 70)
        }
        add(5.6, 1.2, 72, 0, 0); add(5.6, 0.2, 49, 9, 0)
        n.sortBy { it.t }
        return MidiSong(n, emptyList(), 7.5)
    }
}

// ===================== real-time player =====================
class SynthPlayer {
    private val SR = 44100
    @Volatile private var running = false
    @Volatile var paused = false
    @Volatile var key = 0                  // semitono (Key ±)
    @Volatile var gain = 1f
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    var onEnd: () -> Unit = {}
    var sessionId: Int = 0; private set
    @Volatile private var startFrames = 0L

    private class Voice(
        var pos: Double, val end: Int, val ls: Int, val le: Int, val loop: Boolean, val mode: Int, val rate: Double, val basePitch: Double,
        val l: Double, val r: Double, val rvb: Double, val cho: Double, val filtered: Boolean, val fc: Double, val q: Double,
        val delay: Double, val attack: Double, val hold: Double, val decay: Double, val susDb: Double, var release: Double,
        val mDelay: Double, val mAttack: Double, val mHold: Double, val mDecay: Double, val mSus: Double, val mRelease: Double, val mToFc: Double, val mToPitch: Double,
        val vDelay: Double, val vFreq: Double, val vToPitch: Double, val lDelay: Double, val lFreq: Double, val lToPitch: Double, val lToFc: Double, val lToVol: Double,
        val t0: Double, val off: Double, val excl: Int, val ch: Int
    ) {
        var done = false; var relStart = -1.0; var relDb = 0.0; var curDb = 0.0; var mLevel = 0.0; var mRelLevel = 0.0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
    }

    /** Oras ng kanta (ms) ayon sa narinig na (para sa lyrics). */
    val positionMs: Long get() = offsetMs + try { ((track?.playbackHeadPosition?.toLong() ?: 0L) and 0xFFFFFFFFL) * 1000 / SR } catch (e: Exception) { 0L }
    val active get() = running

    private var last: Pair<SoundFont, MidiSong>? = null
    private var lastVol = 100
    fun restart() { last?.let { start(it.first, it.second, lastVol) } }
    @Volatile private var offsetMs = 0L
    /** Tumalon sa segundong ito (hal. laktaw ng intro). */
    fun seekTo(sec: Double) { last?.let { start(it.first, it.second, lastVol, sec) } }

    fun start(sf: SoundFont, song: MidiSong, volume: Int, from: Double = 0.0) {
        stop()
        last = sf to song; lastVol = volume; offsetMs = (from * 1000).toLong()
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(max(minBuf * 4, SR / 2 * 4)).build()
        track = t; sessionId = t.audioSessionId
        t.setVolume(volume / 100f); t.play()
        running = true; paused = false
        thread = Thread({ loop(sf, song, t, from) }, "vk-synth").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun tc(v: Int?, d: Int) = 2.0.pow((v ?: d) / 1200.0)
    private fun cents(c: Double) = 2.0.pow(c / 1200.0)

    private fun voice(sf: SoundFont, n: MNote, r: SoundFont.Region, transpose: Int): Voice? {
        val g = r.g; val s = r.s
        fun gv(k: Int, d: Int) = g[k] ?: d
        val start = s.start + gv(0, 0) + 32768 * gv(4, 0)
        val end = s.end + gv(1, 0) + 32768 * gv(12, 0)
        val ls = s.ls + gv(2, 0) + 32768 * gv(45, 0)
        val le = s.le + gv(3, 0) + 32768 * gv(50, 0)
        val lim = sf.count - 3
        val mode = gv(54, 0) and 3
        val root = if (gv(58, -1) >= 0) g[58]!! else s.pitch
        val key = if (n.ch == 9) n.midi else n.midi + transpose
        val basePitch = (key - root) * gv(56, 100) / 100.0 + gv(51, 0) + gv(52, 0) / 100.0 + s.corr / 100.0
        val rate = s.rate.toDouble() / SR
        if (!(rate > 0) || start < 1 || start >= lim) return null
        val endC = min(lim, max(start + 2, end))
        val att = gv(48, 0) * 0.4
        val vel = n.v / 127.0
        val amp = 10.0.pow(-att / 200) * vel * vel * 0.9
        val pz = (gv(17, 0) + (n.pan - 64) / 64.0 * 500).coerceIn(-500.0, 500.0)
        val pa = (pz + 500) / 1000 * PI / 2
        var fc = gv(8, 13500).toDouble()
        val filtered = fc < 13400 || gv(11, 0) != 0 || gv(10, 0) != 0
        if (filtered) fc += -2400 * (1 - vel) * 0.5
        val qDb = max(0, gv(9, 0)) / 10.0
        val kn = n.midi
        return Voice(start.toDouble(), endC, max(start, ls), min(endC, max(ls + 2, le)), (mode == 1 || mode == 3) && le > ls + 2, mode, rate, basePitch,
            amp * cos(pa), amp * sin(pa), min(1.0, n.rvb + gv(16, 0) / 1000.0), min(1.0, n.cho + gv(15, 0) / 1000.0), filtered, fc, 10.0.pow(qDb / 20) * 0.7071,
            tc(g[33], -12000), tc(g[34], -12000), tc(gv(35, -12000) + gv(39, 0) * (60 - kn), -12000), tc(gv(36, -12000) + gv(40, 0) * (60 - kn), -12000),
            gv(37, 0).coerceIn(0, 1440) / 10.0, min(3.0, tc(g[38], -12000)),
            tc(g[25], -12000), tc(g[26], -12000), tc(gv(27, -12000) + gv(31, 0) * (60 - kn), -12000), tc(gv(28, -12000) + gv(32, 0) * (60 - kn), -12000),
            1 - gv(29, 0).coerceIn(0, 1000) / 1000.0, tc(g[30], -12000), gv(11, 0).toDouble(), gv(7, 0).toDouble(),
            tc(g[23], -12000), 8.176 * cents(gv(24, 0).toDouble()), gv(6, 0).toDouble(),
            tc(g[21], -12000), 8.176 * cents(gv(22, 0).toDouble()), gv(5, 0).toDouble(), gv(10, 0).toDouble(), gv(13, 0).toDouble(),
            n.t, n.t + n.dur, gv(57, 0), n.ch)
    }

    private fun env(v: Voice, x0: Double): Double {
        var x = x0
        if (v.relStart >= 0) {
            val rx = x - v.relStart
            if (rx < 0) return 10.0.pow(-max(0.0, v.curDb) / 20)
            val db = max(0.0, v.relDb) + 100 * rx / max(0.001, v.release)
            if (db >= 96) { v.done = true; return 0.0 }
            return 10.0.pow(-db / 20)
        }
        if (x < v.delay) return 0.0
        x -= v.delay
        if (x < v.attack) { v.curDb = 0.0; return x / v.attack }
        x -= v.attack
        val db = if (x < v.hold) 0.0 else min(v.susDb, 100 * (x - v.hold) / max(0.001, v.decay))
        v.curDb = db
        return 10.0.pow(-db / 20)
    }

    private fun modEnv(v: Voice, x0: Double): Double {
        var x = x0
        if (v.relStart >= 0) { val rx = max(0.0, x - v.relStart); return v.mRelLevel * max(0.0, 1 - rx / max(0.001, v.mRelease)) }
        if (x < v.mDelay) return 0.0
        x -= v.mDelay
        val lv = if (x < v.mAttack) x / v.mAttack else { x -= v.mAttack; if (x < v.mHold) 1.0 else max(v.mSus, 1 - (1 - v.mSus) * (x - v.mHold) / max(0.001, v.mDecay)) }
        v.mLevel = lv
        return lv
    }

    private fun softClip(x: Double): Double { val a = abs(x); return if (a <= 0.7) x else sign(x) * (0.7 + 0.3 * tanh((a - 0.7) / 0.3)) }

    private fun loop(sf: SoundFont, song: MidiSong, t: AudioTrack, from: Double = 0.0) {
        val BLK = 256; val SUB = 64
        val L = DoubleArray(BLK); val R = DoubleArray(BLK); val RV = DoubleArray(BLK); val CL = DoubleArray(BLK); val CR = DoubleArray(BLK)
        val out = ShortArray(BLK * 2)
        val notes = song.notes; var ni = 0
        val active = ArrayList<Voice>()
        // Freeverb (stereo) + chorus — kapareho ng web
        val sc = SR / 44100.0
        val combLen = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val cbL = combLen.map { DoubleArray((it * sc).toInt()) }; val cbR = combLen.map { DoubleArray(((it + 23) * sc).toInt()) }
        val ciL = IntArray(8); val ciR = IntArray(8); val cfL = DoubleArray(8); val cfR = DoubleArray(8)
        val apLen = intArrayOf(556, 441, 341, 225)
        val apL = apLen.map { DoubleArray((it * sc).toInt()) }; val apR = apLen.map { DoubleArray(((it + 23) * sc).toInt()) }
        val aiL = IntArray(4); val aiR = IntArray(4)
        val room = 0.84; val damp = 0.25; val wet = 0.32
        val cLen = (0.05 * SR).toInt(); val chBL = DoubleArray(cLen); val chBR = DoubleArray(cLen); var chI = 0; var chPh = 0.0
        val total = ((song.duration + 2) * SR).toLong()
        var frame = (from * SR).toLong()
        val startFrame = frame
        while (ni < notes.size && notes[ni].t < from) ni++
        val data = sf.data
        try {
            while (running && frame < total) {
                if (paused) { Thread.sleep(20); continue }
                java.util.Arrays.fill(L, 0.0); java.util.Arrays.fill(R, 0.0); java.util.Arrays.fill(RV, 0.0); java.util.Arrays.fill(CL, 0.0); java.util.Arrays.fill(CR, 0.0)
                val tEnd = (frame + BLK).toDouble() / SR
                while (ni < notes.size && notes[ni].t < tEnd) {
                    val n = notes[ni++]
                    for (r in sf.regions(n.bank, n.prog, n.midi, n.v, n.ch == 9)) {
                        val v = voice(sf, n, r, key) ?: continue
                        if (v.excl != 0) for (a in active) if (a.excl == v.excl && a.ch == v.ch && a.relStart < 0 && a.t0 < v.t0) { a.relStart = max(0.0, v.t0 - a.t0); a.relDb = a.curDb; a.mRelLevel = a.mLevel; a.release = min(a.release, 0.04) }
                        active += v
                    }
                }
                val it = active.iterator()
                while (it.hasNext()) {
                    val v = it.next()
                    var k = max(0L, (v.t0 * SR).toLong() - frame).toInt()
                    if (k >= BLK) continue
                    var pos = v.pos
                    while (k < BLK && !v.done) {
                        val tAbs = (frame + k).toDouble() / SR; val x = tAbs - v.t0
                        if (v.relStart < 0 && tAbs >= v.off) { v.relStart = x; v.relDb = v.curDb; v.mRelLevel = v.mLevel }
                        var e = env(v, x)
                        val me = modEnv(v, x)
                        val vib = if (x > v.vDelay) sin(2 * PI * v.vFreq * (x - v.vDelay)) else 0.0
                        val lfo = if (x > v.lDelay) sin(2 * PI * v.lFreq * (x - v.lDelay)) else 0.0
                        val modW = song.mod[v.ch].at(tAbs)
                        val pitchC = v.basePitch * 100 + song.bend[v.ch].at(tAbs) * 100 + me * v.mToPitch + vib * (v.vToPitch + modW * 50) + lfo * v.lToPitch
                        val step = v.rate * cents(pitchC)
                        if (v.lToVol != 0.0) e *= 10.0.pow(-(lfo * v.lToVol) / 200)
                        val cg = song.gain[v.ch].at(tAbs)
                        val gl = v.l * e * cg; val gr = v.r * e * cg
                        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0; var filt = false
                        if (v.filtered) {
                            val fHz = min(SR * 0.45, 8.176 * cents(v.fc + me * v.mToFc + lfo * v.lToFc))
                            if (fHz < SR * 0.44) {
                                val w = 2 * PI * fHz / SR; val al = sin(w) / (2 * v.q); val cw = cos(w); val a0 = 1 + al
                                b1 = (1 - cw) / a0; b0 = b1 / 2; b2 = b0; a1 = -2 * cw / a0; a2 = (1 - al) / a0; filt = true
                            }
                        }
                        val kEnd = min(BLK, k + SUB)
                        while (k < kEnd) {
                            val ip = pos.toInt()
                            if (ip + 2 >= v.end) { v.done = true; break }
                            val fr = pos - ip
                            val xm1 = data.get(ip - 1).toDouble(); val x0 = data.get(ip).toDouble(); val xp1 = data.get(ip + 1).toDouble(); val xp2 = data.get(ip + 2).toDouble()
                            val c1 = 0.5 * (xp1 - xm1); val c2 = xm1 - 2.5 * x0 + 2 * xp1 - 0.5 * xp2; val c3 = 0.5 * (xp2 - xm1) + 1.5 * (x0 - xp1)
                            var smp = (((c3 * fr + c2) * fr + c1) * fr + x0) / 32768.0
                            if (filt) { val y = b0 * smp + b1 * v.x1 + b2 * v.x2 - a1 * v.y1 - a2 * v.y2; v.x2 = v.x1; v.x1 = smp; v.y2 = v.y1; v.y1 = y; smp = y }
                            val l = smp * gl; val r = smp * gr
                            L[k] += l; R[k] += r; RV[k] += (l + r) * v.rvb; CL[k] += l * v.cho; CR[k] += r * v.cho
                            pos += step
                            if (v.loop && !(v.mode == 3 && v.relStart >= 0) && pos >= v.le) pos -= (v.le - v.ls)
                            k++
                        }
                    }
                    v.pos = pos
                    if (v.done) it.remove()
                }
                for (k in 0 until BLK) {
                    val inp = RV[k] * 0.03
                    var ol = 0.0; var or = 0.0
                    for (j in 0 until 8) {
                        var b = cbL[j]; var y = b[ciL[j]]; cfL[j] = y * (1 - damp) + cfL[j] * damp; b[ciL[j]] = inp + cfL[j] * room; if (++ciL[j] >= b.size) ciL[j] = 0; ol += y
                        b = cbR[j]; y = b[ciR[j]]; cfR[j] = y * (1 - damp) + cfR[j] * damp; b[ciR[j]] = inp + cfR[j] * room; if (++ciR[j] >= b.size) ciR[j] = 0; or += y
                    }
                    for (j in 0 until 4) {
                        var b = apL[j]; var bo = b[aiL[j]]; var y = -ol + bo; b[aiL[j]] = ol + bo * 0.5; if (++aiL[j] >= b.size) aiL[j] = 0; ol = y
                        b = apR[j]; bo = b[aiR[j]]; y = -or + bo; b[aiR[j]] = or + bo * 0.5; if (++aiR[j] >= b.size) aiR[j] = 0; or = y
                    }
                    chBL[chI] = CL[k]; chBR[chI] = CR[k]
                    val m = sin(chPh) * 0.004 * SR; val base = 0.018 * SR
                    val rl = (chI - (base + m) + cLen) % cLen; val rr = (chI - (base - m) + cLen) % cLen
                    val il = rl.toInt(); val ir = rr.toInt(); val fl = rl - il; val frr = rr - ir
                    val cl = chBL[il] * (1 - fl) + chBL[(il + 1) % cLen] * fl; val cr = chBR[ir] * (1 - frr) + chBR[(ir + 1) % cLen] * frr
                    chPh += 2 * PI * 0.45 / SR; if (++chI >= cLen) chI = 0
                    out[k * 2] = (softClip(L[k] + ol * wet + cl * 0.5) * 32767).toInt().toShort()
                    out[k * 2 + 1] = (softClip(R[k] + or * wet + cr * 0.5) * 32767).toInt().toShort()
                }
                var off = 0
                while (off < out.size && running) { val w = t.write(out, off, out.size - off); if (w <= 0) break; off += w }
                frame += BLK
            }
            while (running && ((t.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < frame - startFrame - SR / 20)) Thread.sleep(30)
            if (running) { running = false; android.os.Handler(android.os.Looper.getMainLooper()).post { onEnd() } }
        } catch (e: Exception) { running = false }
    }

    fun pause() { paused = true; try { track?.pause() } catch (e: Exception) {} }
    fun play() { try { track?.play() } catch (e: Exception) {}; paused = false }
    fun volume(v: Int) { lastVol = v; try { track?.setVolume(v / 100f) } catch (e: Exception) {} }
    /** Tigil at kalimutan ang kanta. */
    fun clear() { stop(); last = null }
    fun stop() {
        running = false
        try { thread?.join(300) } catch (e: Exception) {}
        thread = null
        try { track?.pause(); track?.flush(); track?.release() } catch (e: Exception) {}
        track = null
    }
}
