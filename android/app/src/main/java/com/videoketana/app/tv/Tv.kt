package com.videoketana.app.tv

import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.videoketana.app.data.Config
import com.videoketana.app.data.Prefs
import com.videoketana.app.data.QItem
import com.videoketana.app.data.Realtime
import com.videoketana.app.data.Supa
import com.videoketana.app.data.makeRoomCode
import com.videoketana.app.data.tr
import com.videoketana.app.data.validRoom
import com.videoketana.app.ui.DotText
import com.videoketana.app.ui.Synth
import com.videoketana.app.ui.VkButton
import com.videoketana.app.ui.VkIconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val TEAL = Color(0xFF62E3D2)
private val INK = Color(0xFFEEF0F3)
private val DIM = Color(0xFFB9C0CA)
private val GOLD = Color(0xFFFFD54A)

/** QR code bilang larawan. */
fun qrBitmap(text: String, size: Int = 512): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (y in 0 until size) for (x in 0 until size) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bmp.asImageBitmap()
}

/**
 * TV screen ng native app: laging bukas na screen ng videoke.
 * Kapareho ng web host ang usapan sa Supabase, kaya pwedeng web o app ang remote.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvScreen(onClose: () -> Unit, isTv: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var room by remember { mutableStateOf(Prefs.hostRoom.takeIf { validRoom(it) } ?: makeRoomCode().also { Prefs.hostRoom = it }) }
    val remoteLink = Config.SITE + "remote/?room=" + room
    val qr = remember(room) { qrBitmap(remoteLink) }

    var queue by remember { mutableStateOf<List<QItem>>(emptyList()) }
    var current by remember { mutableStateOf<QItem?>(null) }
    var mode by remember { mutableStateOf("idle") }          // idle | audio | video | youtube
    var paused by remember { mutableStateOf(false) }
    var volume by remember { mutableIntStateOf(100) }
    var splash by remember { mutableStateOf<QItem?>(null) }
    var score by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var note by remember { mutableStateOf("") }
    var showUi by remember { mutableStateOf(false) }
    var standby by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricOffset by remember { mutableStateOf(0.0) }
    var view by remember { mutableStateOf(LyricView("", 0f, "", 0)) }
    var busy by remember { mutableStateOf(false) }
    var ytPlaying by remember { mutableStateOf(false) }   // true kapag talagang tumutugtog na ang YouTube
    var ytTry by remember { mutableIntStateOf(0) }        // dagdag bawat bagong YouTube na kanta
    val hostId = remember { Random.nextLong().toString(36) }
    var hostSince by remember { mutableStateOf(System.currentTimeMillis()) }

    val exo = remember { ExoPlayer.Builder(ctx).build() }
    val yt = remember { YtController() }
    var rt by remember { mutableStateOf<Realtime?>(null) }

    fun applyVolume() { exo.volume = volume / 100f; yt.volume(volume) }

    // ---- tunog ng kanta: bass, treble (Equalizer ng Android), key at tempo (ExoPlayer) ----
    var bass by remember { mutableIntStateOf(Prefs.int("musicBass")) }
    var treble by remember { mutableIntStateOf(Prefs.int("musicTreble")) }
    var key by remember { mutableIntStateOf(0) }       // bawat kanta: nagsisimula sa 0
    var tempo by remember { mutableIntStateOf(0) }
    var eq by remember { mutableStateOf<android.media.audiofx.Equalizer?>(null) }
    // pang-YouTube: equalizer sa buong tunog ng app (session 0). Luma na ito sa Android at hindi lahat ng device ay sumusunod.
    val geq = remember { try { android.media.audiofx.Equalizer(0, 0).apply { enabled = false } } catch (e: Throwable) { null } }
    fun setBands(e: android.media.audiofx.Equalizer) {
        val lo = e.bandLevelRange[0].toInt(); val hi = e.bandLevelRange[1].toInt()
        for (b in 0 until e.numberOfBands) {
            val f = e.getCenterFreq(b.toShort()) / 1000      // Hz
            val db = when { f < 250 -> bass; f > 3500 -> treble; else -> 0 }
            e.setBandLevel(b.toShort(), (db * 100).coerceIn(lo, hi).toShort())
        }
    }
    fun applyFx() {
        if (mode == "youtube") {
            yt.rate(1f + tempo * 0.05f)
            try { geq?.let { g -> setBands(g); g.enabled = bass != 0 || treble != 0 } } catch (e: Throwable) { }
            return
        }
        try { geq?.enabled = false } catch (e: Throwable) { }
        exo.playbackParameters = androidx.media3.common.PlaybackParameters(1f + tempo * 0.05f, Math.pow(2.0, key / 12.0).toFloat())
        try {
            if (eq == null && exo.audioSessionId != 0) eq = android.media.audiofx.Equalizer(0, exo.audioSessionId).apply { enabled = true }
            eq?.let { setBands(it) }
        } catch (ex: Exception) { }
    }
    fun fxJson() = JSONObject().put("musicBass", bass).put("musicTreble", treble).put("musicKey", key).put("musicTempo", tempo)
        .put("micVolume", Prefs.int("micVolume", 100)).put("micEcho", Prefs.int("micEcho", 30)).put("micReverb", Prefs.int("micReverb", 20)).put("micClarity", Prefs.int("micClarity", 30))
        .put("eq", true).put("ytTempo", true).put("ytEq", geq != null)

    fun broadcastState() {
        if (standby) return
        val q = current
        val now = q?.let { JSONObject().put("code", it.code).put("title", it.title).put("artist", it.artist).put("source", it.source).put("singer", it.singer ?: "").put("id", it.id) }
        rt?.broadcast("state", JSONObject().put("now", now ?: JSONObject.NULL).put("paused", paused).put("volume", volume).put("room", room).put("local", JSONArray()).put("fx", fxJson()))
    }

    fun stopMedia() {
        exo.stop(); exo.clearMediaItems()
        yt.stop()
        lyrics = emptyList(); view = LyricView("", 0f, "", 0)
    }

    suspend fun loadQueue() {
        if (standby) return
        queue = try { Supa.queue(room) } catch (e: Exception) { queue }
        val cur = current
        if (cur != null && queue.none { it.id == cur.id }) { stopMedia(); current = null; mode = "idle" }   // inalis mula sa remote
    }

    lateinit var startNext: suspend () -> Unit

    suspend fun finish(reason: String) {
        val done = current ?: return
        stopMedia(); current = null; paused = false
        queue = queue.filter { it.id != done.id }
        try { Supa.removeFromQueue(done.id) } catch (e: Exception) { Toast.makeText(ctx, tr("Hindi naalis sa pila: ", "Could not remove from the queue: ") + e.message, Toast.LENGTH_SHORT).show() }
        if (reason == "ended" && Prefs.scoreOn) {
            mode = "idle"
            Synth.play("drumroll")
            delay(1900)
            val n = (100 - Random.nextDouble().let { it * it } * 30).toInt()
            score = n to (done.singer ?: "")
            Synth.play("fanfare"); Synth.play("applause", 0.7f)
            rt?.broadcast("score", JSONObject().put("score", n).put("singer", done.singer ?: ""))
            delay(4200)
            score = null
        }
        startNext()
    }

    startNext = suspend sn@{
        if (!busy && current == null && !standby) {
            busy = true
            val next = queue.firstOrNull { it.status == "playing" } ?: queue.firstOrNull { it.status == "waiting" }
            // mga naipit sa "Kinakanta" (galing sa ibang TV): ibalik sa pila
            queue.filter { it.status == "playing" && it.id != next?.id }.forEach { s -> try { Supa.setStatus(s.id, "waiting") } catch (e: Exception) {} }
            if (next == null) { mode = "idle"; busy = false; broadcastState() }
            else {
                current = next
                if (next.status != "playing") try { Supa.setStatus(next.id, "playing") } catch (e: Exception) {}
                splash = next; delay(2600); splash = null
                if (current?.id == next.id) {
                    val s = next.song
                    when {
                        next.localCode != null && s == null -> { Toast.makeText(ctx, tr("Wala sa device na ito ang kantang ${next.localCode}", "Song ${next.localCode} is not on this device"), Toast.LENGTH_LONG).show(); busy = false; finish("error"); return@sn }
                        s != null && (s.kind == "audio" || s.kind == "video") -> {
                            mode = s.kind
                            lyrics = emptyList(); lyricOffset = 0.0
                            if (s.kind == "audio" && s.lrcPath != null) {
                                val (l, off) = parseLrc(try { Supa.text(Supa.mediaUrl(s.lrcPath)!!) } catch (e: Exception) { "" })
                                lyrics = l; lyricOffset = off
                            }
                            exo.setMediaItem(MediaItem.fromUri(Supa.mediaUrl(s.mediaPath) ?: ""))
                            key = 0; tempo = 0
                            exo.prepare(); applyVolume(); applyFx(); exo.play()
                        }
                        else -> {
                            mode = "youtube"
                            val id = next.ytId ?: s?.ytId
                            if (id == null) { busy = false; finish("error"); return@sn }
                            ytPlaying = false; ytTry++
                            key = 0; tempo = 0
                            yt.load(id, volume)
                            applyFx()
                        }
                    }
                }
                busy = false
                broadcastState()
            }
        } else Unit
    }

    fun handleCmd(action: String, value: Any?) {
        when (action) {
            "toggle", "pause", "play" -> {
                paused = when (action) { "pause" -> true; "play" -> false; else -> !paused }
                if (mode == "youtube") { if (paused) yt.pause() else yt.play() } else exo.playWhenReady = !paused
            }
            "skip" -> scope.launch { finish("skip") }
            "restart" -> { if (mode == "youtube") yt.seek(0) else exo.seekTo(0) }
            "volup" -> { volume = (volume + 10).coerceAtMost(100); applyVolume(); note = "Volume $volume" }
            "voldown" -> { volume = (volume - 10).coerceAtLeast(0); applyVolume(); note = "Volume $volume" }
            "setvol" -> { volume = (value?.toString()?.toIntOrNull() ?: volume).coerceIn(0, 100); applyVolume() }
            "refresh" -> scope.launch { loadQueue(); startNext() }
            "fx" -> {
                val o = value as? JSONObject ?: return
                val k = o.optString("key"); val v = o.optInt("value")
                when (k) {
                    "musicBass" -> { bass = v.coerceIn(-12, 12); Prefs.putInt(k, bass) }
                    "musicTreble" -> { treble = v.coerceIn(-12, 12); Prefs.putInt(k, treble) }
                    "musicKey" -> if (mode != "youtube") { key = v.coerceIn(-6, 6); note = "Key " + (if (key > 0) "+" else "") + key }
                    "musicTempo" -> { tempo = v.coerceIn(-5, 5); note = "Tempo " + (if (tempo > 0) "+" else "") + tempo + if (mode == "youtube") "  (x" + "%.2f".format(1f + tempo * 0.05f) + ")" else "" }
                    "micVolume", "micEcho", "micReverb", "micClarity" -> Prefs.putInt(k, v)
                }
                applyFx()
            }
            "sfx" -> {
                val o = value as? JSONObject
                val name = o?.optString("name") ?: value?.toString() ?: return
                if (name == "stop") Synth.stopAll()
                else {
                    val mapped = when (name) { "omg", "wownaman", "galing" -> "wow"; "faah" -> "boo"; else -> name }
                    Synth.play(mapped)
                    note = (o?.optString("from")?.takeIf { it.isNotBlank() }?.let { tr("$name mula kay $it", "$name from $it") }) ?: name
                }
            }
        }
        broadcastState()
    }

    // ---- sa phone/tablet: buong screen at pahiga ----
    DisposableEffect(Unit) {
        val act = ctx as? android.app.Activity
        val ctl = act?.let { androidx.core.view.WindowInsetsControllerCompat(it.window, it.window.decorView) }
        ctl?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        ctl?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!isTv && Prefs.tvLandscape) act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (!isTv) { act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; ctl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
        }
    }

    // ---- Realtime ----
    DisposableEffect(room) {
        val r = Realtime(room,
            onBroadcast = { ev, p ->
                when (ev) {
                    "cmd" -> if (!standby) handleCmd(p.optString("action"), p.opt("value"))
                    "hello" -> broadcastState()
                    "host" -> if (p.optString("id") != hostId && !standby) {
                        if (p.optLong("since") > hostSince) { standby = true; stopMedia(); current = null; mode = "idle" }
                        else rt?.broadcast("host", JSONObject().put("id", hostId).put("since", hostSince))
                    }
                }
            },
            onQueueChange = { scope.launch { loadQueue(); startNext() } },
            onStatus = { ok -> if (ok) scope.launch { loadQueue(); startNext(); broadcastState(); rt?.broadcast("host", JSONObject().put("id", hostId).put("since", hostSince)) } })
        rt = r; r.connect()
        onDispose { r.close() }
    }
    LaunchedEffect(room) { while (true) { delay(15000); loadQueue(); startNext(); if (!standby) rt?.broadcast("host", JSONObject().put("id", hostId).put("since", hostSince)) } }

    // ---- ExoPlayer events ----
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED && current != null) scope.launch { finish("ended") } }
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(ctx, tr("Hindi ma-play: ", "Cannot play: ") + error.errorCodeName, Toast.LENGTH_LONG).show()
                scope.launch { delay(1500); finish("error") }
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l); try { eq?.release() } catch (e: Exception) {}; exo.release(); yt.destroy(); try { geq?.release() } catch (e: Throwable) {} }
    }

    // ---- lyrics habang tumutugtog ----
    LaunchedEffect(mode, lyrics) {
        while (mode == "audio") { view = lyricAt(lyrics, exo.currentPosition / 1000.0 + lyricOffset); delay(40) }
    }
    LaunchedEffect(note) { if (note.isNotEmpty()) { delay(2200); note = "" } }
    LaunchedEffect(showUi) { if (showUi) { delay(4000); showUi = false } }

    // ================= ITSURA =================
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0C10)).clickable(remember { MutableInteractionSource() }, null) { showUi = true }) {
        Background(mode == "idle" || mode == "audio")

        // YouTube player (laging nakahanda para mabilis magsimula)
        DisposableEffect(Unit) {
            yt.onState = { st ->
                if (st == 1) ytPlaying = true
                if (st == 0 && mode == "youtube") scope.launch { finish("ended") }
            }
            yt.onError = { code ->
                if (mode == "youtube") {
                    val msg = when (code) {
                        "noapi", "page" -> tr("Hindi ma-load ang YouTube player. Tingnan ang internet. Lilipat sa susunod.", "Could not load the YouTube player. Check the internet. Moving to the next song.")
                        else -> tr("Ayaw ipa-play ng YouTube ang video na ito sa labas ng YouTube (error $code). Lilipat sa susunod.", "YouTube does not allow this video outside YouTube (error $code). Moving to the next song.")
                    }
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    if (code == "noapi" || code == "page") yt.reload()
                    scope.launch { delay(1500); finish("error") }
                } else if (code == "noapi" || code == "page") scope.launch { delay(5000); yt.reload() }
            }
            onDispose { }
        }
        // bantay: kung hindi nagsimula ang YouTube sa loob ng 25s, lumipat sa susunod
        LaunchedEffect(ytTry, mode) {
            if (mode != "youtube") return@LaunchedEffect
            delay(25_000)
            if (mode == "youtube" && !ytPlaying) {
                Toast.makeText(ctx, tr("Hindi nagsimula ang YouTube video. Lilipat sa susunod.", "The YouTube video did not start. Moving to the next song."), Toast.LENGTH_LONG).show()
                yt.reload()
                finish("error")
            }
        }
        // laging buong laki ang WebView (kung 1dp, minsan itim lang ang lumalabas); itinatago lang kapag hindi YouTube
        AndroidView(factory = { c -> yt.create(c) }, modifier = Modifier.fillMaxSize().alpha(if (mode == "youtube") 1f else 0f))
        if (mode == "youtube" && !ytPlaying) Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                current?.let { Text(it.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
                Spacer(Modifier.height(10.dp))
                Text(tr("Kinukuha ang video sa YouTube...", "Loading the video from YouTube..."), color = DIM, fontSize = 15.sp)
            }
        }

        // video ng sariling kanta
        if (mode == "video") AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; player = exo } },
            modifier = Modifier.fillMaxSize()
        )

        // lyrics ng audio na kanta
        if (mode == "audio") current?.let { q -> AudioStage(q, view, lyrics.isEmpty()) }

        // walang kanta: orasan, QR, "Pumili ng kanta"
        if (mode == "idle" && splash == null && score == null) Idle(room, qr)

        // itaas: susunod sa pila + orasan
        TopStrip(queue, current, mode)

        // pagitan bago ang kanta
        AnimatedVisibility(splash != null, enter = fadeIn(), exit = fadeOut()) { splash?.let { Splash(it) } }
        // score
        AnimatedVisibility(score != null, enter = fadeIn(), exit = fadeOut()) { score?.let { ScoreView(it.first, it.second) } }

        if (paused) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp)).background(Color(0xCC000000)).padding(horizontal = 28.dp, vertical = 16.dp)) {
            Text(tr("Naka-pause", "Paused"), color = INK, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        if (note.isNotEmpty()) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp).clip(RoundedCornerShape(50)).background(Color(0xCC000000)).padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(note, color = INK, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        // mga button (lalabas kapag pinindot ang screen o ang remote ng TV)
        AnimatedVisibility(showUi || isTv && mode == "idle", modifier = Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) VkIconButton("next", { scope.launch { finish("skip") } })
                VkIconButton("refresh", {
                    room = makeRoomCode(); Prefs.hostRoom = room; queue = emptyList()
                    Toast.makeText(ctx, tr("Bagong room: $room", "New room: $room"), Toast.LENGTH_SHORT).show()
                })
                VkIconButton("x", onClose)
            }
        }

        if (standby) Box(Modifier.fillMaxSize().background(Color(0xEE0B0C10)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("May ibang TV na gumagamit ng room na ito", "Another TV is using this room"), color = INK, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(tr("Doon na tutugtog ang mga kanta. Pindutin para gamitin ulit dito.", "Songs will play there. Tap to use it here again."), color = DIM, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                VkButton(tr("Gamitin dito", "Use it here"), primary = true, onClick = {
                    standby = false; hostSince = System.currentTimeMillis()
                    rt?.broadcast("host", JSONObject().put("id", hostId).put("since", hostSince))
                    scope.launch { loadQueue(); startNext() }
                })
            }
        }
    }
}

// ---------------- mga bahagi ng TV screen ----------------

/** Gumagalaw na background: mga bilog na liwanag na parang ilaw ng entablado. */
@Composable
private fun Background(show: Boolean) {
    if (!show) return
    val t = rememberInfiniteTransition(label = "bg")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1730), Color(0xFF0E0F18))))
        val lights = listOf(Color(0x5562E3D2), Color(0x55A06BFF), Color(0x44FF7AB6), Color(0x3362A0FF))
        lights.forEachIndexed { i, col ->
            val x = size.width * (0.15f + 0.7f * ((p + i * 0.27f) % 1f))
            val y = size.height * (0.25f + 0.45f * ((i * 0.37f + p * 0.6f) % 1f))
            drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), center = Offset(x, y), radius = size.minDimension * 0.45f), radius = size.minDimension * 0.45f, center = Offset(x, y))
        }
        val step = 22f
        var yy = step / 2
        while (yy < size.height) { var xx = step / 2; while (xx < size.width) { drawCircle(Color(0x0DFFFFFF), 1.2f, Offset(xx, yy)); xx += step }; yy += step }
    }
}

@Composable
private fun Clock(height: androidx.compose.ui.unit.Dp) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    Row(verticalAlignment = Alignment.Bottom) {
        DotText(SimpleDateFormat("h:mm", Locale.US).format(now), height, TEAL, ghost = false)
        Spacer(Modifier.width(height * 0.2f))
        Text(SimpleDateFormat("a", Locale.US).format(now), color = DIM, fontWeight = FontWeight.Bold, fontSize = (height.value * 0.3f).sp)
    }
}

@Composable
private fun Idle(room: String, qr: ImageBitmap) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp), contentAlignment = Alignment.Center) {
        val mw = maxWidth; val mh = maxHeight
        val wide = mw > mh
        val qrSize = if (wide) minOf(mh * 0.55f, mw * 0.3f) else minOf(mw * 0.6f, mh * 0.32f)
        val clockH = if (wide) minOf(mh * 0.16f, mw * 0.07f) else mw * 0.12f
        val date = SimpleDateFormat("EEEE, MMMM d", if (Prefs.lang == "en") Locale.US else Locale("fil", "PH")).format(Date())
        val card: @Composable () -> Unit = {
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(qr, null, Modifier.size(qrSize))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Room ", color = Color(0xFF171A20), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    DotText(room, 22.dp, Color(0xFF0B8576), ghost = false, glow = false)
                }
            }
        }
        val info: @Composable (Alignment.Horizontal) -> Unit = { align ->
            Column(horizontalAlignment = align) {
                Text(tr("Pumili ng kanta", "Pick a song"), color = INK, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Clock(clockH)
                Spacer(Modifier.height(10.dp))
                Text(date, color = INK, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(tr("I-scan ang QR para mag-reserve ng kanta", "Scan the QR to reserve a song"), color = DIM, fontSize = 16.sp,
                    textAlign = if (align == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start)
            }
        }
        if (wide) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            info(Alignment.Start); Spacer(Modifier.width(48.dp)); card()
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            info(Alignment.CenterHorizontally); Spacer(Modifier.height(24.dp)); card()
        }
    }
}

@Composable
private fun TopStrip(queue: List<QItem>, current: QItem?, mode: String) {
    Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.Companion.displayCutout).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val next = queue.filter { it.status == "waiting" && it.id != current?.id }
        if (next.isEmpty()) Text(tr("Walang kasunod. Mag-reserve gamit ang phone.", "Nothing next. Reserve with your phone."), color = DIM, fontSize = 15.sp)
        else {
            Text(tr("Susunod", "Next"), color = INK, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            next.take(4).forEach { q ->
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (q.source != "youtube") { DotText(q.code, 11.dp, TEAL, ghost = false, glow = false); Spacer(Modifier.width(6.dp)) }
                    Text(q.title, color = INK, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
                }
            }
            if (next.size > 4) Text("  +${next.size - 4}", color = DIM, fontSize = 15.sp)
        }
        Spacer(Modifier.weight(1f))
        if (mode != "idle") Clock(22.dp)
    }
}

@Composable
private fun AudioStage(q: QItem, v: LyricView, noLyrics: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val big = (maxWidth.value * 0.05f).coerceIn(26f, 72f)
        Column(Modifier.padding(start = 48.dp, top = 70.dp)) {
            Text(q.title, color = INK, fontSize = (big * 0.6f).sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(q.artist, color = DIM, fontSize = (big * 0.35f).sp)
            q.singer?.let { Text(tr("Kumakanta: $it", "Singing: $it"), color = TEAL, fontSize = (big * 0.32f).sp, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = maxHeight * 0.12f), horizontalAlignment = Alignment.CenterHorizontally) {
            val shadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 10f)
            when {
                noLyrics -> Text(tr("Walang lyrics para sa kantang ito", "No lyrics for this song"), color = DIM, fontSize = (big * 0.5f).sp)
                v.countdown > 0 && v.current.isBlank() -> Text("${v.countdown}", color = GOLD, fontSize = big.sp, fontWeight = FontWeight.ExtraBold)
                else -> {
                    val sungChars = (v.current.length * v.progress).toInt()
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = GOLD)) { append(v.current.take(sungChars)) }
                        withStyle(SpanStyle(color = Color.White)) { append(v.current.drop(sungChars)) }
                    }, fontSize = big.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = TextStyle(shadow = shadow), lineHeight = (big * 1.15f).sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(v.next, color = DIM, fontSize = (big * 0.62f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = TextStyle(shadow = shadow), maxLines = 2)
        }
    }
}

@Composable
private fun Splash(q: QItem) {
    Box(Modifier.fillMaxSize().background(Color(0xE60B0C10)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Susunod na kanta", "Up next"), color = DIM, fontSize = 20.sp)
            Spacer(Modifier.height(14.dp))
            if (q.source != "youtube") { DotText(q.code, 64.dp, TEAL); Spacer(Modifier.height(16.dp)) }
            Text(q.title, color = INK, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2)
            if (q.artist.isNotBlank()) Text(q.artist, color = DIM, fontSize = 22.sp)
            q.singer?.let { Spacer(Modifier.height(14.dp)); Text(tr("Kakanta: $it", "Singer: $it"), color = TEAL, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ScoreView(n: Int, singer: String) {
    Box(Modifier.fillMaxSize().background(Color(0xF00B0C10)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Score", color = DIM, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            DotText(n.toString(), 150.dp, TEAL)
            Spacer(Modifier.height(16.dp))
            Text(when {
                n == 100 -> tr("Perfect! Pang-concert ang boses!", "Perfect! A concert-ready voice!")
                n >= 95 -> tr("Ang galing! Isa pa!", "Amazing! One more!")
                n >= 88 -> tr("Ang ganda ng boses!", "What a beautiful voice!")
                n >= 80 -> tr("Magaling! Palakpakan natin!", "Great job! Let's give a hand!")
                else -> tr("Pwede na! Kanta pa ulit!", "Not bad! Sing again!")
            }, color = INK, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            if (singer.isNotBlank()) Text(singer, color = TEAL, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
