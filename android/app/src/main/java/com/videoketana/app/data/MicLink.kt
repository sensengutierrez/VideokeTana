package com.videoketana.app.data

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tanh

/*
 * Mic ng app (walang website): phone → TV sa iisang Wi-Fi, diretsong UDP.
 * 24 kHz mono, 10 ms bawat packet (mababang delay). Ang TV (app) ang may echo, reverb, linaw at lakas.
 * Packet: 4 byte na bilang + 240 sample (16-bit, little endian).
 */
object MicLink {
    const val SR = 24000
    const val FRAME = 240
    const val PORT = 47321

    /** IP ng device sa Wi-Fi/LAN (para malaman ng phone kung saan ipapadala). */
    fun lanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    } catch (e: Exception) { null }
}

// ===================== PHONE: nagpapadala =====================
object MicSender {
    @Volatile private var running = false
    @Volatile var muted = false
    @Volatile var level = 0f                 // 0..1 para sa meter
    private var thread: Thread? = null
    val isOn get() = running

    @SuppressLint("MissingPermission")
    fun start(ip: String, port: Int = MicLink.PORT, onError: (String) -> Unit = {}) {
        stop()
        running = true
        thread = Thread({
            var rec: AudioRecord? = null
            var sock: DatagramSocket? = null
            try {
                val min = AudioRecord.getMinBufferSize(MicLink.SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                rec = AudioRecord(MediaRecorder.AudioSource.MIC, MicLink.SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, MicLink.FRAME * 8))
                if (rec.state != AudioRecord.STATE_INITIALIZED) throw RuntimeException("Ayaw bumukas ng mic")
                sock = DatagramSocket()
                val addr = InetSocketAddress(InetAddress.getByName(ip), port)
                val buf = ShortArray(MicLink.FRAME)
                val out = ByteArray(4 + MicLink.FRAME * 2)
                var seq = 0
                rec.startRecording()
                while (running) {
                    var got = 0
                    while (got < MicLink.FRAME && running) { val n = rec.read(buf, got, MicLink.FRAME - got); if (n <= 0) break; got += n }
                    var peak = 0
                    for (i in 0 until MicLink.FRAME) { val v = if (muted) 0 else buf[i].toInt(); peak = max(peak, abs(v)); out[4 + i * 2] = (v and 0xFF).toByte(); out[5 + i * 2] = ((v shr 8) and 0xFF).toByte() }
                    level = level * 0.7f + (peak / 32768f) * 0.3f
                    out[0] = (seq shr 24).toByte(); out[1] = (seq shr 16).toByte(); out[2] = (seq shr 8).toByte(); out[3] = seq.toByte(); seq++
                    try { sock.send(DatagramPacket(out, out.size, addr)) } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                running = false
                android.os.Handler(android.os.Looper.getMainLooper()).post { onError(e.message ?: "Error") }
            } finally {
                try { rec?.stop() } catch (e: Exception) {}; try { rec?.release() } catch (e: Exception) {}; try { sock?.close() } catch (e: Exception) {}
                level = 0f
            }
        }, "vk-mic").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() { running = false; try { thread?.join(300) } catch (e: Exception) {}; thread = null; level = 0f }
}

// ===================== TV: tumatanggap at nagpapatugtog =====================
object MicReceiver {
    @Volatile var volume = 100; @Volatile var echo = 30; @Volatile var reverb = 20; @Volatile var clarity = 30
    @Volatile private var running = false
    @Volatile var activeMics = 0; private set
    private var thread: Thread? = null
    private var sock: DatagramSocket? = null

    private class Chan(val track: AudioTrack) {
        var written = 0L; var last = System.currentTimeMillis(); var prev = 0.0
        var agc = 4.0                                   // awtomatikong lakas (mahina ang mic ng phone)
        val echoBuf = DoubleArray((MicLink.SR * 0.19).toInt()); var ei = 0
        val combs = intArrayOf(1116, 1188, 1277, 1356).map { DoubleArray(it * MicLink.SR / 44100) }; val ci = IntArray(4)
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            val chans = HashMap<String, Chan>()
            try {
                val s = DatagramSocket(null).apply { reuseAddress = true; soTimeout = 500; bind(InetSocketAddress(MicLink.PORT)) }
                sock = s
                val pkt = DatagramPacket(ByteArray(2048), 2048)
                val pcm = ShortArray(MicLink.FRAME)
                while (running) {
                    try { s.receive(pkt) } catch (e: java.net.SocketTimeoutException) { cleanup(chans); continue }
                    val n = (pkt.length - 4) / 2; if (n <= 0) continue
                    val key = pkt.address.hostAddress + ":" + pkt.port
                    val ch = chans.getOrPut(key) { Chan(newTrack()) }
                    ch.last = System.currentTimeMillis()
                    val d = pkt.data
                    // AGC: sukatin ang lakas ng boses sa packet na ito; kapag may boses, unti-unting ipantay (×1 hanggang ×10)
                    var ss = 0.0
                    for (i in 0 until min(n, pcm.size)) { val v = ((d[4 + i * 2].toInt() and 0xFF) or (d[5 + i * 2].toInt() shl 8)).toShort() / 32768.0; ss += v * v }
                    val rms = kotlin.math.sqrt(ss / max(1, min(n, pcm.size)))
                    if (rms > 0.004) ch.agc = ch.agc * 0.92 + (0.14 / rms).coerceIn(1.0, 10.0) * 0.08
                    val vol = volume / 100.0 * ch.agc; val e = echo / 100.0; val rv = reverb / 100.0; val cl = clarity / 100.0
                    for (i in 0 until min(n, pcm.size)) {
                        var x = ((d[4 + i * 2].toInt() and 0xFF) or (d[5 + i * 2].toInt() shl 8)).toShort() / 32768.0
                        val y0 = x + cl * 0.7 * (x - ch.prev); ch.prev = x; x = y0
                        // echo (paulit-ulit na delay)
                        val eb = ch.echoBuf[ch.ei]
                        var y = x + eb * e * 0.6
                        ch.echoBuf[ch.ei] = x + eb * e * 0.45; if (++ch.ei >= ch.echoBuf.size) ch.ei = 0
                        // reverb (comb)
                        var r = 0.0
                        for (k in 0 until 4) { val b = ch.combs[k]; val v = b[ch.ci[k]]; b[ch.ci[k]] = x + v * 0.78; if (++ch.ci[k] >= b.size) ch.ci[k] = 0; r += v }
                        y += r * rv * 0.12
                        pcm[i] = (tanh(y * vol) * 32767).toInt().toShort()
                    }
                    // huwag hayaang humaba ang delay: kapag sobra na ang naka-pila, laktawan
                    val queued = ch.written - (ch.track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
                    if (queued > MicLink.SR * 0.12) continue
                    ch.track.write(pcm, 0, min(n, pcm.size)); ch.written += min(n, pcm.size)
                }
            } catch (e: Exception) { }
            chans.values.forEach { try { it.track.release() } catch (x: Exception) {} }
            activeMics = 0
            try { sock?.close() } catch (e: Exception) {}
        }, "vk-mic-rx").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun min(a: Int, b: Int) = if (a < b) a else b

    private fun cleanup(chans: HashMap<String, Chan>) {
        val now = System.currentTimeMillis()
        val it = chans.entries.iterator()
        while (it.hasNext()) { val c = it.next().value; if (now - c.last > 3000) { try { c.track.release() } catch (e: Exception) {}; it.remove() } }
        activeMics = chans.size
    }

    private fun newTrack(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(MicLink.SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val b = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(MicLink.SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(max(min, MicLink.FRAME * 2 * 6))
        if (Build.VERSION.SDK_INT >= 26) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        val t = b.build(); t.play()
        activeMics++
        return t
    }

    fun stop() { running = false; try { sock?.close() } catch (e: Exception) {}; try { thread?.join(300) } catch (e: Exception) {}; thread = null }
}
