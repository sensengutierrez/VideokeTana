package com.videoketana.app.tv

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Interlude habang naghihintay ng kanta: malumanay na musika na ginagawa ng code
 * (electric piano, bass, mahinang beat). Walang audio file at walang kinopyang kanta.
 * Bawat simula ay bagong key, progression at tempo.
 */
object Interlude {
    private const val SR = 32000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var track: AudioTrack? = null
    private var job: Job? = null
    @Volatile private var target = 0f

    // mga progression (mga degree ng major scale, 0 = I)
    private val PROGS = listOf(
        intArrayOf(0, 5, 3, 4), intArrayOf(0, 4, 5, 3), intArrayOf(5, 3, 0, 4),
        intArrayOf(0, 3, 4, 3), intArrayOf(3, 4, 2, 5), intArrayOf(0, 2, 3, 4)
    )
    private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    fun start(volume: Int) {
        target = (volume / 100f).coerceIn(0f, 1f)
        if (job?.isActive == true) return
        job = scope.launch {
            val pcm = render()
            if (!isActive) return@launch
            try {
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                t.write(pcm, 0, pcm.size)
                t.setLoopPoints(0, pcm.size, -1)
                t.setVolume(0f); t.play(); track = t
                // dahan-dahang paglakas (fade in) at pagsunod sa volume
                var v = 0f
                while (isActive) {
                    v += (target - v) * 0.08f
                    t.setVolume(v)
                    delay(60)
                }
            } catch (e: Exception) { }
        }
    }

    fun setVolume(volume: Int) { target = (volume / 100f).coerceIn(0f, 1f) }

    /** Dahan-dahang paghina tapos tigil. */
    fun stop() {
        val j = job ?: return
        val t = track
        job = null; track = null
        scope.launch {
            try {
                var v = target
                while (v > 0.01f) { v *= 0.8f; t?.setVolume(v); delay(50) }
            } catch (e: Exception) { }
            j.cancel()
            try { t?.stop(); t?.release() } catch (e: Exception) { }
        }
    }

    private fun midiHz(n: Int) = 440.0 * Math.pow(2.0, (n - 69) / 12.0)

    private fun render(): ShortArray {
        val rnd = Random(System.nanoTime())
        val bpm = 76 + rnd.nextInt(18)
        val beat = 60.0 / bpm
        val bar = beat * 4
        val prog = PROGS[rnd.nextInt(PROGS.size)]
        val root = 57 + rnd.nextInt(8)                     // A3 hanggang E4
        val bars = 16
        val n = (bar * bars * SR).toInt()
        val b = FloatArray(n)
        fun add(i: Int, v: Float) { if (i in 0 until n) b[i] += v }

        // electric piano: sine + kaunting harmonics, malambot na decay
        fun piano(t: Double, dur: Double, hz: Double, g: Double) {
            val s = (t * SR).toInt(); val len = (dur * SR).toInt()
            for (k in 0 until len) {
                val x = k.toDouble() / SR
                val env = (1 - exp(-x * 60)) * exp(-x * 1.6)
                val w = sin(2 * PI * hz * x) + 0.25 * sin(4 * PI * hz * x) * exp(-x * 4) + 0.08 * sin(6 * PI * hz * x) * exp(-x * 8)
                add(s + k, (w * env * g).toFloat())
            }
        }
        fun bass(t: Double, dur: Double, hz: Double, g: Double) {
            val s = (t * SR).toInt(); val len = (dur * SR).toInt()
            for (k in 0 until len) {
                val x = k.toDouble() / SR
                val env = (1 - exp(-x * 120)) * exp(-x * 2.5) * (if (k > len - 400) (len - k) / 400.0 else 1.0)
                add(s + k, (tanh(1.6 * sin(2 * PI * hz * x)) * env * g).toFloat())
            }
        }
        fun kick(t: Double, g: Double) {
            val s = (t * SR).toInt()
            var ph = 0.0
            for (k in 0 until (0.25 * SR).toInt()) {
                val x = k.toDouble() / SR
                ph += 2 * PI * (45 + 90 * exp(-x * 30)) / SR
                add(s + k, (sin(ph) * exp(-x * 14) * g).toFloat())
            }
        }
        fun hat(t: Double, g: Double) {
            val s = (t * SR).toInt(); var prev = 0.0
            for (k in 0 until (0.05 * SR).toInt()) {
                val w = rnd.nextDouble() * 2 - 1; val hp = w - prev; prev = w
                add(s + k, (hp * exp(-k.toDouble() / SR * 70) * g).toFloat())
            }
        }
        fun note(degree: Int, octave: Int = 0): Int {
            val d = ((degree % 7) + 7) % 7
            return root + MAJOR[d] + 12 * (octave + Math.floorDiv(degree, 7))
        }

        for (barI in 0 until bars) {
            val t0 = barI * bar
            val deg = prog[barI % prog.size]
            val chord = listOf(note(deg), note(deg + 2), note(deg + 4), note(deg + 6))   // may 7th para malambot
            // chords: sa beat 1 at "and" ng 2
            chord.forEach { piano(t0, beat * 1.6, midiHz(it), 0.055) }
            chord.drop(1).forEach { piano(t0 + beat * 2.5, beat * 1.4, midiHz(it), 0.04) }
            // maliit na melody paminsan-minsan
            if (barI % 2 == 1) for (k in 0 until 3) {
                val md = deg + listOf(4, 2, 7, 6, 4)[rnd.nextInt(5)]
                piano(t0 + beat * (k + 0.5 + rnd.nextInt(2) * 0.5), beat * 0.9, midiHz(note(md, 1)), 0.035)
            }
            // bass
            bass(t0, beat * 1.5, midiHz(note(deg, -2)), 0.16)
            bass(t0 + beat * 2, beat * 0.9, midiHz(note(deg, -2)), 0.12)
            bass(t0 + beat * 3, beat * 0.9, midiHz(note(deg + 4, -2)), 0.11)
            // beat: tahimik sa unang 2 bar
            if (barI >= 2) {
                kick(t0, 0.22); kick(t0 + beat * 2.5, 0.16)
                for (h in 0 until 8) hat(t0 + h * beat / 2, if (h % 2 == 1) 0.05 else 0.025)
            }
        }
        // malumanay na reverb (ilang delay)
        val taps = intArrayOf((0.031 * SR).toInt(), (0.047 * SR).toInt(), (0.071 * SR).toInt(), (0.113 * SR).toInt())
        for (i in n - 1 downTo 0) { var r = 0f; taps.forEachIndexed { k, d -> if (i - d >= 0) r += b[i - d] * (0.22f - k * 0.04f) }; b[i] += r }
        // tahi ang dulo sa simula para walang putol sa pag-ulit
        val xf = (0.4 * SR).toInt()
        for (k in 0 until xf) { val a = k.toFloat() / xf; b[k] = b[k] * a + b[n - xf + k] * (1 - a) }
        val out = ShortArray(n - xf)
        for (i in out.indices) out[i] = (tanh(b[i] * 1.4) * 30000).toInt().toShort()
        return out
    }
}
