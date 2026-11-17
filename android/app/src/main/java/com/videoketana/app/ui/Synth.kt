package com.videoketana.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.videoketana.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Mga tunog ng app na ginagawa ng code (walang audio file, walang kinopya):
 * intro, palakpak, cheers, drum roll, ding, busina, boom, wow, aww, boo, tawa, galit, fanfare, beep.
 */
object Synth {
    private const val SR = 44100
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = HashMap<String, ShortArray>()
    private val playing = mutableListOf<AudioTrack>()

    val NAMES = listOf("applause", "cheers", "drumroll", "ding", "horn", "boom", "wow", "aww", "boo", "laugh", "angry", "fanfare")

    fun play(name: String, volume: Float = Prefs.sfxVolume / 100f) {
        if (!Prefs.sfx && name != "beep") return
        scope.launch {
            val pcm = synchronized(cache) { cache[name] } ?: render(name)?.also { synchronized(cache) { cache[name] = it } } ?: return@launch
            try {
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                t.write(pcm, 0, pcm.size)
                t.setVolume(volume.coerceIn(0f, 1f))
                synchronized(playing) { playing += t }
                t.play()
                kotlinx.coroutines.delay(pcm.size * 1000L / SR + 300)
                synchronized(playing) { playing -= t }
                t.release()
            } catch (e: Exception) { }
        }
    }

    /** Gawin agad ang tunog (para sabay ang tunog at ang animation). */
    suspend fun prepare(name: String) = kotlinx.coroutines.withContext(Dispatchers.Default) {
        if (synchronized(cache) { cache[name] } == null) { render(name)?.let { synchronized(cache) { cache[name] = it } } }
        Unit
    }

    fun stopAll() { synchronized(playing) { playing.forEach { try { it.stop() } catch (e: Exception) {} } } }

    /** Ihanda nang maaga para walang antala sa unang pindot. */
    fun warm(vararg names: String) { scope.launch { names.forEach { n -> prepare(n) } } }

    // ---------------- paggawa ng tunog ----------------
    private class Buf(sec: Double) {
        val d = FloatArray((sec * SR).toInt())
        fun idx(t: Double) = (t * SR).toInt()
    }

    private fun env(i: Int, n: Int, att: Int, curve: Double = 4.0): Float {
        if (i < att) return i / att.toFloat()
        val x = (i - att).toDouble() / (n - att).coerceAtLeast(1)
        return exp(-curve * x).toFloat() * (1 - x).toFloat().coerceAtLeast(0f)
    }

    /** Tono (sine/triangle/saw/square) na may pagbaba o pagtaas ng nota. */
    private fun tone(b: Buf, t: Double, dur: Double, gain: Double, wave: Char, freq: Double, bendTo: Double = freq, att: Double = 0.01, curve: Double = 3.0) {
        val s = b.idx(t); val n = (dur * SR).toInt(); var ph = 0.0
        for (i in 0 until n) {
            val j = s + i; if (j >= b.d.size) break
            val f = freq + (bendTo - freq) * i / n
            ph += f / SR
            val p = ph - kotlin.math.floor(ph)
            val v = when (wave) {
                't' -> 1 - 4 * abs(p - 0.5)
                'w' -> 2 * p - 1
                'q' -> if (p < 0.5) 1.0 else -1.0
                else -> sin(2 * PI * p)
            }
            b.d[j] += (v * gain * env(i, n, (att * SR).toInt().coerceAtLeast(1), curve)).toFloat()
        }
    }

    /** Ingay na dinaanan ng filter (bandpass/lowpass), gaya ng palakpak, whoosh, kalabog. */
    private fun noise(b: Buf, t: Double, dur: Double, gain: Double, freq: Double, q: Double = 1.0, type: Char = 'b', freqTo: Double = freq, att: Double = 0.003, curve: Double = 4.0) {
        val s = b.idx(t); val n = (dur * SR).toInt()
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in 0 until n) {
            val j = s + i; if (j >= b.d.size) break
            val f = freq * (freqTo / freq).pow(i.toDouble() / n)
            val w0 = 2 * PI * f / SR; val al = sin(w0) / (2 * q); val cs = kotlin.math.cos(w0)
            val (b0, b1, b2) = if (type == 'l') Triple((1 - cs) / 2, 1 - cs, (1 - cs) / 2) else Triple(al, 0.0, -al)
            val a0 = 1 + al; val a1 = -2 * cs; val a2 = 1 - al
            val x = Random.nextDouble(-1.0, 1.0)
            val y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x; y2 = y1; y1 = y
            b.d[j] += (y * gain * env(i, n, (att * SR).toInt().coerceAtLeast(1), curve)).toFloat()
        }
    }

    /** Simpleng reverb (mga comb delay) para lumaki ang tunog. */
    private fun reverb(b: Buf, mix: Double) {
        val delays = intArrayOf(1557, 1617, 1491, 1422).map { it * SR / 44100 }
        val src = b.d.copyOf()
        delays.forEach { dl ->
            val fb = 0.78; val line = FloatArray(dl); var k = 0
            for (i in src.indices) {
                val out = line[k]; line[k] = (src[i] + out * fb).toFloat(); k = (k + 1) % dl
                b.d[i] += (out * mix / delays.size).toFloat()
            }
        }
    }

    /** Boses ng maraming tao (sawtooth na may formant), para sa wow, aww, boo, tawa. */
    private fun crowd(b: Buf, t: Double, dur: Double, f0: Double, bend: Double, formant: Double, gain: Double, n: Int = 9) {
        repeat(n) {
            val f = f0 * (0.7 + Random.nextDouble() * 0.9)
            val at = t + Random.nextDouble() * 0.1
            tone(b, at, dur, gain / n, 'w', f, f * bend, 0.12, 1.5)
        }
        noise(b, t, dur, gain * 0.5, formant, 2.0, att = 0.1, curve = 1.5)
    }

    private fun finish(b: Buf, loud: Double = 1.0): ShortArray {
        var peak = 0f; b.d.forEach { peak = maxOf(peak, abs(it)) }
        val pre = if (peak > 0) (1.4 * loud / peak) else 1.0
        return ShortArray(b.d.size) { i -> (tanh(b.d[i] * pre) * 0.92 * 32767).toInt().toShort() }   // soft limiter: malakas pero hindi basag
    }

    private fun render(name: String): ShortArray? = when (name) {
        "intro" -> Buf(4.6).also { b ->
            for (i in 0 until 18) noise(b, 0.9 * (i / 18.0).pow(0.85), 0.035, 0.5, 2400.0 + i * 140, 4.0)
            noise(b, 0.0, 1.0, 0.6, 300.0, 2.0, freqTo = 5000.0, att = 0.85, curve = 8.0)               // riser
            tone(b, 0.9, 1.6, 1.0, 's', 110.0, 38.0, 0.01, 3.0)                                  // boom
            tone(b, 0.9, 0.35, 0.6, 't', 196.0, 100.0, 0.005, 5.0)                               // suntok na naririnig sa phone
            noise(b, 0.9, 0.5, 0.9, 1200.0, 0.7, 'l')
            listOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.5, 1568.0, 1760.0, 2093.0)
                .forEachIndexed { i, f -> tone(b, 0.9 + i * 0.07, 0.22, 0.25, 't', f, f, 0.008, 6.0) }
            listOf(130.81, 196.0, 261.63, 329.63, 392.0, 493.88, 587.33).forEachIndexed { i, f ->
                tone(b, 1.65, 2.8, 0.09 - i * 0.008, 'w', f * 1.003, f * 1.003, 0.35, 2.2)
                tone(b, 1.65, 2.8, 0.09 - i * 0.008, 'w', f * 0.997, f * 0.997, 0.35, 2.2)
            }
            listOf(2637.0, 3136.0, 3951.0).forEachIndexed { i, f -> tone(b, 1.75 + i * 0.09, 0.5, 0.08, 's', f, f, 0.01, 5.0) }
            reverb(b, 0.5)
        }.let { finish(it) }
        "applause" -> Buf(3.0).also { b ->
            repeat(260) { noise(b, Random.nextDouble() * 2.4, 0.04, 0.5, 1500.0 + Random.nextDouble() * 2500, 1.2) }
        }.let { finish(it, 0.9) }
        "cheers" -> Buf(3.0).also { b ->
            repeat(220) { noise(b, Random.nextDouble() * 2.4, 0.04, 0.45, 1500.0 + Random.nextDouble() * 2500, 1.2) }
            crowd(b, 0.0, 2.4, 260.0, 1.25, 1800.0, 0.5, 12)
            listOf(0.2, 0.9, 1.6).forEach { tone(b, it, 0.35, 0.15, 's', 1900.0, 2600.0) }
        }.let { finish(it) }
        "drumroll" -> Buf(2.4).also { b ->
            var t = 0.0; while (t < 1.8) { noise(b, t, 0.06, 0.4 + t * 0.2, 900.0, 0.8); t += 0.045 }
            tone(b, 1.85, 0.5, 0.9, 's', 90.0, 50.0); noise(b, 1.85, 0.8, 0.8, 5000.0, 0.6)
        }.let { finish(it) }
        "ding" -> Buf(1.6).also { b -> tone(b, 0.0, 1.4, 0.6, 's', 1318.5); tone(b, 0.0, 1.0, 0.25, 's', 2637.0) }.let { finish(it, 0.8) }
        "horn" -> Buf(1.3).also { b -> tone(b, 0.0, 0.45, 0.5, 'q', 466.0, 466.0, 0.02, 0.5); tone(b, 0.5, 0.7, 0.5, 'q', 466.0, 440.0, 0.02, 0.6) }.let { finish(it, 0.7) }
        "boom" -> Buf(1.8).also { b -> tone(b, 0.0, 1.5, 1.0, 's', 120.0, 30.0); tone(b, 0.0, 0.3, 0.6, 't', 200.0, 90.0); noise(b, 0.0, 1.2, 0.7, 500.0, 0.7, 'l') }.let { finish(it) }
        "wow" -> Buf(1.9).also { b -> crowd(b, 0.0, 1.6, 180.0, 1.6, 870.0, 0.7, 12) }.let { finish(it, 0.9) }
        "aww" -> Buf(2.0).also { b -> crowd(b, 0.0, 1.8, 330.0, 0.62, 1200.0, 0.7, 12) }.let { finish(it, 0.9) }
        "boo" -> Buf(2.4).also { b -> crowd(b, 0.0, 2.2, 120.0, 0.85, 500.0, 0.8, 14) }.let { finish(it, 0.9) }
        "laugh" -> Buf(1.8).also { b -> for (i in 0 until 7) crowd(b, i * 0.2, 0.16, 240.0 - i * 8, 0.9, 1200.0, 0.6, 6) }.let { finish(it, 0.9) }
        "angry" -> Buf(1.5).also { b -> tone(b, 0.0, 0.5, 0.5, 'q', 110.0, 100.0, 0.01, 1.0); tone(b, 0.55, 0.8, 0.5, 'q', 92.0, 72.0, 0.01, 1.0) }.let { finish(it, 0.7) }
        "fanfare" -> Buf(2.0).also { b ->
            listOf(523.25 to 0.0, 659.25 to 0.12, 783.99 to 0.24).forEach { (f, t) -> tone(b, t, 0.14, 0.3, 'w', f) }
            listOf(523.25, 659.25, 783.99, 1046.5).forEach { tone(b, 0.36, 1.2, 0.18, 'w', it, it, 0.02, 2.0) }
            reverb(b, 0.4)
        }.let { finish(it, 0.85) }
        "beep" -> Buf(0.09).also { b -> tone(b, 0.0, 0.08, 0.5, 's', 880.0, 880.0, 0.003, 4.0) }.let { finish(it, 0.35) }
        else -> null
    }
}
