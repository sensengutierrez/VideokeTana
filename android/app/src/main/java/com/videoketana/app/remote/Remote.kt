package com.videoketana.app.remote

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.videoketana.app.ui.StandBy
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoketana.app.Screen
import com.videoketana.app.data.Config
import com.videoketana.app.data.Prefs
import com.videoketana.app.data.QItem
import com.videoketana.app.data.Realtime
import com.videoketana.app.data.Song
import com.videoketana.app.data.Supa
import com.videoketana.app.data.parseYouTubeId
import com.videoketana.app.data.tr
import com.videoketana.app.ui.DotText
import com.videoketana.app.ui.Panel
import com.videoketana.app.ui.SearchField
import com.videoketana.app.ui.SongRow
import com.videoketana.app.ui.Synth
import com.videoketana.app.ui.VkButton
import com.videoketana.app.ui.VkIcon
import com.videoketana.app.ui.VkIconButton
import com.videoketana.app.ui.vk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Ang kinakanta ngayon, mula sa "state" na ipinapadala ng TV. */
data class NowState(
    val title: String, val artist: String, val code: String, val source: String, val singer: String, val id: String?,
    val paused: Boolean, val volume: Int, val local: List<Triple<Int, String, String>>, val fx: JSONObject?
)

private fun parseState(p: JSONObject): NowState {
    val n = p.optJSONObject("now")
    val loc = p.optJSONArray("local") ?: JSONArray()
    return NowState(
        title = n?.optString("title") ?: "", artist = n?.optString("artist") ?: "", code = n?.opt("code")?.toString() ?: "",
        source = n?.optString("source") ?: "", singer = n?.optString("singer") ?: "", id = n?.optString("id"),
        paused = p.optBoolean("paused"), volume = p.optInt("volume", 100),
        local = (0 until loc.length()).map { val o = loc.getJSONObject(it); Triple(o.optInt("code"), o.optString("title"), o.optString("artist")) },
        fx = p.optJSONObject("fx")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(room: String, go: (Screen) -> Unit) {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    var hostSeen by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf<NowState?>(null) }
    var queue by remember { mutableStateOf<List<QItem>>(emptyList()) }
    var tab by remember { mutableIntStateOf(0) }
    var askName by remember { mutableStateOf(Prefs.name.isBlank()) }
    var sheet by remember { mutableStateOf("") }          // "" | sound | board
    var rt by remember { mutableStateOf<Realtime?>(null) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    var standby by remember { mutableStateOf(false) }

    suspend fun loadQueue() { queue = try { Supa.queue(room) } catch (e: Exception) { queue } }
    // mga kanta sa folder ng phone na ito (pinapasa sa TV kapag ni-reserve)
    val sentFiles = remember { HashMap<String, java.io.File>() }
    var phoneLocal by remember { mutableStateOf<List<com.videoketana.app.data.LocalSong>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (com.videoketana.app.data.LocalLib.hasPermission(ctx)) phoneLocal = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.videoketana.app.data.LocalLib.scan() }
    }
    val pickWall = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { if (com.videoketana.app.data.Wallpaper.save(ctx, uri) && Prefs.glass == 0) Prefs.changeGlass(35) }
    }

    DisposableEffect(room) {
        val r = Realtime(room,
            onBroadcast = { ev, p ->
                when (ev) {
                    "state" -> { now = parseState(p); hostSeen = true }
                    "need" -> sentFiles[p.optString("qid")]?.let { f -> scope.launch(kotlinx.coroutines.Dispatchers.IO) { com.videoketana.app.data.FileSend.send(rt, p.optString("qid"), f) } }
                    "score" -> Toast.makeText(ctx, tr("Score", "Score") + (p.optString("singer").takeIf { it.isNotBlank() }?.let { tr(" ni $it", " for $it") } ?: "") + ": " + p.optInt("score"), Toast.LENGTH_LONG).show()
                }
            },
            onQueueChange = { scope.launch { loadQueue() } },
            onStatus = { ok -> connected = ok })
        rt = r; r.connect()
        onDispose { r.close(); com.videoketana.app.data.MicSender.stop() }
    }
    LaunchedEffect(connected) { if (connected) { rt?.broadcast("hello", JSONObject()); loadQueue() } }
    LaunchedEffect(room) { while (true) { loadQueue(); delay(15000) } }
    // StandBy kapag matagal na hindi ginagalaw ang phone
    LaunchedEffect(lastTouch, standby) {
        if (!Prefs.standby || standby) return@LaunchedEffect
        delay(Prefs.standbyAfter * 1000L)
        if (sheet.isEmpty()) standby = true
    }

    fun cmd(action: String, value: Any? = null) {
        if (!hostSeen) Toast.makeText(ctx, tr("Hindi pa makita ang TV sa room $room.", "The TV in room $room cannot be found yet."), Toast.LENGTH_SHORT).show()
        rt?.broadcast("cmd", JSONObject().put("action", action).apply { if (value != null) put("value", value) })
    }
    fun sfx(name: String) = cmd("sfx", JSONObject().put("name", name).put("from", Prefs.name))
    // mode: "reserve" (sa dulo ng pila), "top" (unahin), "now" (patugtugin ngayon)
    suspend fun reserve(songId: String? = null, ytId: String? = null, ytTitle: String? = null, localCode: Int? = null, localTitle: String? = null, mode: String = "reserve", file: java.io.File? = null): Boolean = try {
        if (file != null && file.length() > com.videoketana.app.data.FileSend.MAX) throw RuntimeException(tr("Masyadong malaki para ipasa sa TV (max 8 MB).", "Too large to send to the TV (max 8 MB)."))
        val minBefore = queue.filter { it.status == "waiting" }.minOfOrNull { it.sortOrder }
        val id = Supa.reserve(room, Prefs.name, songId, ytId, ytTitle, localCode, localTitle)
        if (file != null && id.isNotEmpty()) { sentFiles[id] = file; scope.launch(kotlinx.coroutines.Dispatchers.IO) { com.videoketana.app.data.FileSend.send(rt, id, file) } }
        if (mode != "reserve" && minBefore != null && id.isNotEmpty()) Supa.moveToTop(id, minBefore)
        Synth.play("ding", 0.5f)
        com.videoketana.app.ui.Haptic.reserved()
        val playing = !now?.title.isNullOrEmpty()
        if (mode == "now" && playing) cmd("skip")
        val pos = if (mode == "reserve") queue.count { it.status == "waiting" } + 1 else 1
        Toast.makeText(ctx, when {
            mode == "now" || !playing && pos <= 1 -> tr("Tutugtog na sa TV.", "Playing on the TV now.")
            mode == "top" -> tr("Inuna sa pila. Ikaw na ang susunod.", "Moved to the top. You are next.")
            else -> tr("Naka-reserve. Pang-$pos ka sa pila.", "Reserved. You are number $pos in the queue.")
        }, Toast.LENGTH_SHORT).show()
        loadQueue(); true
    } catch (e: Exception) { Toast.makeText(ctx, tr("Hindi na-reserve: ", "Could not reserve: ") + e.message, Toast.LENGTH_LONG).show(); false }

    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); lastTouch = System.currentTimeMillis() } }
    }) {
        Column(Modifier.fillMaxSize()) {
            // ---------- itaas ----------
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                VkIconButton("x", { go(Screen.Home) }, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("VideokeTana", color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (connected && hostSeen) c.accent else if (connected) c.muted else c.danger))
                        Spacer(Modifier.width(6.dp))
                        Text(when { connected && hostSeen -> tr("Konektado sa TV", "Connected to the TV"); connected -> tr("Hinahanap ang TV", "Looking for the TV"); else -> tr("Kumokonekta", "Connecting") },
                            color = c.muted, fontSize = 12.sp)
                    }
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    DotText(room, 11.dp, ghost = false)
                }
                Spacer(Modifier.width(8.dp))
                VkIconButton("sliders", { sheet = "sound" }, size = 38.dp, primary = true)
            }

            // ---------- kinakanta ngayon ----------
            NowCard(now, hostSeen, room,
                onCmd = { a -> cmd(a) },
                onSfx = { sfx(it) },
                onBoard = { sheet = "board" },
                onFx = { k, v -> cmd("fx", JSONObject().put("key", k).put("value", v)) })

            // ---------- laman ng tab ----------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> NumberTab(now?.local ?: emptyList(), phoneLocal) { s, local, mode ->
                        scope.launch {
                            val pf = if (s.kind == "phone") phoneLocal.firstOrNull { it.code == s.code } else null
                            when {
                                pf != null -> reserve(localCode = pf.code, localTitle = (if (pf.artist.isNotBlank()) pf.artist + " - " else "") + pf.title, mode = mode, file = pf.file)
                                local -> reserve(localCode = s.code, localTitle = (if (s.artist.isNotBlank()) s.artist + " - " else "") + s.title, mode = mode)
                                else -> reserve(songId = s.id, mode = mode)
                            }
                        }
                    }
                    1 -> SearchTab(now?.local ?: emptyList(), phoneLocal, onDeleteLocal = { code -> cmd("localdel", code) }) { s ->
                        scope.launch {
                            val pf = if (s.kind == "phone") phoneLocal.firstOrNull { it.code == s.code } else null
                            if (pf != null) reserve(localCode = pf.code, localTitle = (if (pf.artist.isNotBlank()) pf.artist + " - " else "") + pf.title, file = pf.file)
                            else if (s.kind == "local") reserve(localCode = s.code, localTitle = (if (s.artist.isNotBlank()) s.artist + " - " else "") + s.title)
                            else reserve(songId = s.id)
                        }
                    }
                    2 -> YouTubeTab { id, title -> scope.launch { reserve(ytId = id, ytTitle = title) } }
                    3 -> QueueTab(queue, now?.id,
                        onRemove = { q -> scope.launch { try { Supa.removeFromQueue(q.id); loadQueue() } catch (e: Exception) { Toast.makeText(ctx, e.message, Toast.LENGTH_SHORT).show() } } },
                        onTop = { q -> scope.launch { val min = queue.filter { it.status == "waiting" }.minOfOrNull { it.sortOrder } ?: 0.0; try { Supa.moveToTop(q.id, min); loadQueue() } catch (e: Exception) {} } })
                    else -> MicTab(room, now?.fx) { k, v -> cmd("fx", JSONObject().put("key", k).put("value", v)) }
                }
            }

            // ---------- tab sa ibaba ----------
            BottomNav(tab, queue.count { it.status == "waiting" }) { tab = it }
        }

        if (standby) {
            val next = queue.firstOrNull { it.status == "waiting" }
            StandBy(nowTitle = now?.title ?: "", nowSinger = now?.singer ?: "", nextTitle = next?.title ?: "", room = room,
                onWake = { standby = false; lastTouch = System.currentTimeMillis() })
        }
    }

    if (sheet == "sound") ModalBottomSheet(onDismissRequest = { sheet = "" }, containerColor = if (Prefs.dark) com.videoketana.app.ui.DarkVk.surface else com.videoketana.app.ui.LightVk.surface) {
        SoundSheet(now?.fx, now?.source ?: "", send = { k, v -> cmd("fx", JSONObject().put("key", k).put("value", v)) },
            onWallpaper = { pickWall.launch("image/*") }, onStandby = { sheet = ""; standby = true }, onScene = { cmd("scene") })
    }
    if (sheet == "board") ModalBottomSheet(onDismissRequest = { sheet = "" }, containerColor = if (Prefs.dark) com.videoketana.app.ui.DarkVk.surface else com.videoketana.app.ui.LightVk.surface) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
            Text("Soundboard", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(tr("Tutunog sa TV.", "Plays on the TV."), color = c.muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Soundboard { name -> if (name == "stop") cmd("sfx", JSONObject().put("name", "stop")) else sfx(name) }
        }
    }
    if (askName) NameDialog { n -> Prefs.name = n; askName = false }
}

/** Card ng kinakanta ngayon: numero, title, kumakanta, mga kontrol, mabilis na tunog. */
/** Kompaktong "mini player": kinakanta, kontrol, at mabilis na sound effects. Key/Tempo ay nasa "Tunog sa TV". */
@Composable
private fun NowCard(now: NowState?, hostSeen: Boolean, room: String, onCmd: (String) -> Unit, onSfx: (String) -> Unit, onBoard: () -> Unit, onFx: (String, Int) -> Unit) {
    val c = vk
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xF2181C24), Color(0xF20E1116))))
        .border(1.dp, Brush.linearGradient(listOf(c.accent.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), c.accent.copy(alpha = 0.15f))), shape)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            val n = now
            Row(verticalAlignment = Alignment.CenterVertically) {
                // numero o YouTube
                Box(Modifier.size(width = 74.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF07090C)).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    when {
                        n == null || n.title.isEmpty() -> DotText("-----", 13.dp, ghost = true)
                        n.source == "youtube" -> VkIcon("yt", 20.dp, c.danger)
                        else -> DotText(n.code, 13.dp, ghost = false)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (n == null || n.title.isEmpty()) {
                        Text(if (hostSeen) tr("Walang tumutugtog", "Nothing playing") else tr("Hinahanap ang TV…", "Looking for the TV…"),
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (hostSeen) tr("Pumili ng kanta sa ibaba", "Pick a song below") else tr("Room $room", "Room $room"), color = Color(0xFF8C93A1), fontSize = 12.sp, maxLines = 1)
                    } else {
                        Text(n.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(if (n.paused) tr("Naka-pause", "Paused") else null, n.artist.ifBlank { null }, n.singer.ifBlank { null }).joinToString(" · "),
                            color = if (n.paused) c.accent else Color(0xFF8C93A1), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                n?.let { Text("${it.volume}", color = Color(0xFF8C93A1), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CtlBtn("restart") { onCmd("restart") }
                CtlBtn("voldown") { onCmd("voldown") }
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(c.accent, c.accent.copy(alpha = 0.75f))))
                    .clickable { onCmd("toggle") }, contentAlignment = Alignment.Center) {
                    VkIcon(if (now?.paused == true) "play" else "pause", 22.dp, c.accentInk)
                }
                CtlBtn("volup") { onCmd("volup") }
                CtlBtn("next") { onCmd("skip") }
            }
            // Key at Tempo: laging nakikita (kapag kaya ng TV)
            val fx = now?.fx
            val isYt = now?.source == "youtube"
            if (fx != null && fx.has("musicTempo") && (!isYt || fx.optBoolean("ytTempo"))) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isYt && fx.has("musicKey")) MiniStepper("Key", fx.optInt("musicKey"), -6, 6, Modifier.weight(1f)) { onFx("musicKey", it) }
                    MiniStepper("Tempo", fx.optInt("musicTempo"), -5, 5, Modifier.weight(1f)) { onFx("musicTempo", it) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SfxChip(tr("Palakpak", "Applause"), Modifier.weight(1f)) { onSfx("applause") }
                SfxChip("Cheers", Modifier.weight(1f)) { onSfx("cheers") }
                SfxChip("Wow", Modifier.weight(1f)) { onSfx("wow") }
                SfxChip(tr("Iba pa", "More"), Modifier.weight(1f), on = true) { onBoard() }
            }
        }
    }
}

@Composable
private fun MiniStepper(label: String, value: Int, min: Int, max: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val c = vk
    Row(modifier.height(34.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable(enabled = value > min) { onChange(value - 1); com.videoketana.app.ui.Haptic.tap() }, contentAlignment = Alignment.Center) {
            Text("−", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(label + " " + (if (value > 0) "+" else "") + value, color = if (value == 0) Color(0xFF8C93A1) else c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable(enabled = value < max) { onChange(value + 1); com.videoketana.app.ui.Haptic.tap() }, contentAlignment = Alignment.Center) {
            Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CtlBtn(icon: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) { VkIcon(icon, 20.dp, Color.White) }
}

/** − halaga + (para sa Key at Tempo). */
@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val c = vk
    Row(modifier.height(42.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.3f)).border(1.dp, c.displayLine, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).clickable(enabled = value > min) { onChange(value - 1) }, contentAlignment = Alignment.Center) {
            Text("−", color = c.displayText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(label + " " + (if (value > 0) "+" else "") + value, color = if (value == 0) c.displayMuted else c.displayFg, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).clickable(enabled = value < max) { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            Text("+", color = c.displayText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Tab sa ibaba ng screen, parang totoong app. */
@Composable
private fun BottomNav(tab: Int, waiting: Int, onTab: (Int) -> Unit) {
    val c = vk
    val tabs = listOf("hash" to tr("Numero", "Number"), "search" to tr("Hanap", "Search"), "yt" to "YouTube", "list" to tr("Pila", "Queue"), "mic" to "Mic")
    Row(Modifier.fillMaxWidth().background(c.surface).border(width = 1.dp, color = c.line, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
        .padding(horizontal = 8.dp, vertical = 8.dp)) {
        tabs.forEachIndexed { i, (ic, label) ->
            val on = tab == i
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onTab(i) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) c.accent.copy(alpha = 0.18f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 5.dp)) {
                    VkIcon(ic, 22.dp, if (on) c.accent else c.muted)
                    if (i == 3 && waiting > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-4).dp).size(17.dp).clip(RoundedCornerShape(50)).background(c.accent), contentAlignment = Alignment.Center) {
                        Text("$waiting", color = c.accentInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(label, color = if (on) c.text else c.muted, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** Mga adjust ng tunog sa TV: boses (mic) at kanta (bass, treble, key). */
@Composable
private fun SoundSheet(fx: JSONObject?, source: String, send: (String, Int) -> Unit, onWallpaper: () -> Unit, onStandby: () -> Unit, onScene: () -> Unit = {}) {
    val c = vk
    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 30.dp)) {
        Text(tr("Tunog sa TV", "Sound on the TV"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(tr("Nagbabago agad habang kumakanta.", "Changes right away while singing."), color = c.muted, fontSize = 14.sp)
        if (fx == null) Text(tr("Hinihintay ang TV...", "Waiting for the TV..."), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        Text(tr("Kanta", "Song"), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        val yt = source == "youtube"
        val ytEq = fx?.optBoolean("ytEq") == true
        val ytTempo = fx?.optBoolean("ytTempo") == true
        FxSlider("Bass", "musicBass", -12, 12, fx, send, unit = " dB", enabled = !yt || ytEq)
        FxSlider("Treble", "musicTreble", -12, 12, fx, send, unit = " dB", enabled = !yt || ytEq)
        FxSlider(tr("Key (tono ng kanta)", "Key (song pitch)"), "musicKey", -6, 6, fx, send, unit = "", enabled = !yt && fx?.has("musicKey") == true)
        FxSlider(tr("Tempo (bilis)", "Tempo (speed)"), "musicTempo", -5, 5, fx, send, unit = "", enabled = fx?.has("musicTempo") == true && (!yt || ytTempo))
        Text(when {
            yt && (ytEq || ytTempo) -> tr("YouTube: gumagana ang bass, treble at tempo (kung papayagan ng TV), pero hindi ang key.", "YouTube: bass, treble and tempo work (if the TV allows it), but not key.")
            yt -> tr("Hindi mababago ang tunog ng YouTube na kanta sa TV na ito. Sa sariling kanta lang ito gumagana.", "YouTube songs cannot be changed on this TV. This only works on your own songs.")
            fx != null && !fx.has("musicKey") -> tr("Gumagana ang Key kapag ang TV ay ang VideokeTana app.", "Key works when the TV is the VideokeTana app.")
            else -> ""
        }, color = c.muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text(tr("Boses (mic)", "Voice (mic)"), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        FxSlider(tr("Lakas ng mic", "Mic volume"), "micVolume", 0, 150, fx, send, unit = "%")
        FxSlider("Echo", "micEcho", 0, 100, fx, send)
        FxSlider("Reverb", "micReverb", 0, 100, fx, send)
        FxSlider(tr("Linaw", "Clarity"), "micClarity", 0, 100, fx, send)

        // ----- TV: interlude at background (sinusunod ng kahit anong TV: web o app) -----
        if (fx != null && fx.has("interlude")) {
            Spacer(Modifier.height(16.dp))
            Text(tr("TV: interlude at background", "TV: interlude and background"), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Interlude habang naghihintay", "Interlude while waiting"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(fx.optInt("interlude") == 1, { send("interlude", if (it) 1 else 0) },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.accentInk))
            }
            if (fx.optInt("interlude") == 1) FxSlider(tr("Lakas ng interlude", "Interlude volume"), "interludeVol", 0, 100, fx, send, unit = "%")
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val vid = fx.optInt("bgVideo") == 1
                listOf(1 to "BGV", 0 to tr("Animation", "Animation")).forEach { (v, label) ->
                    val on = (v == 1) == vid
                    Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50)).background(if (on) c.accent else c.surface).border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(50))
                        .clickable { send("bgVideo", v) }, contentAlignment = Alignment.Center) {
                        Text(label, color = if (on) c.accentInk else c.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                VkButton(tr("Palitan", "Next"), icon = "refresh", onClick = onScene, height = 40.dp)
            }
            Text(tr("BGV: video sa Library o sa folder ng TV. Kung wala, animation.", "BGV: videos in the Library or the TV's folder. If none, animation."), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(18.dp))
        Text(tr("Itsura ng phone", "Phone look"), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VkButton(if (Prefs.wallpaper.isEmpty()) tr("Wallpaper", "Wallpaper") else tr("Palitan", "Change"), icon = "plus", onClick = onWallpaper, modifier = Modifier.weight(1f))
            if (Prefs.wallpaper.isNotEmpty()) { val ctx = LocalContext.current; VkIconButton("trash", { com.videoketana.app.data.Wallpaper.clear(ctx) }, size = 48.dp, tint = c.danger) }
            VkButton("StandBy", icon = "moon", onClick = onStandby, modifier = Modifier.weight(1f))
        }
        if (Prefs.wallpaper.isNotEmpty()) {
            var g by remember { mutableFloatStateOf(Prefs.glass.toFloat()) }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Transparency", "Transparency"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${g.toInt()}%", color = c.muted, fontSize = 13.sp)
            }
            Slider(value = g, onValueChange = { g = it }, onValueChangeFinished = { Prefs.changeGlass(g.toInt()) }, valueRange = 0f..85f,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line))
        }
    }
}

@Composable
private fun FxSlider(label: String, key: String, min: Int, max: Int, fx: JSONObject?, send: (String, Int) -> Unit, unit: String = "", enabled: Boolean = true) {
    val c = vk
    var v by remember(key) { mutableFloatStateOf((fx?.optInt(key, 0) ?: 0).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(fx) { if (!dragging && fx != null && fx.has(key)) v = fx.optInt(key).toFloat() }
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (enabled) c.text else c.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text((if (min < 0 && v > 0) "+" else "") + v.toInt() + unit, color = c.muted, fontSize = 13.sp)
        }
        Slider(value = v, onValueChange = { dragging = true; v = it.roundToInt().toFloat() },
            onValueChangeFinished = { dragging = false; send(key, v.toInt()) },
            valueRange = min.toFloat()..max.toFloat(), enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line))
    }
}

@Composable
private fun SfxChip(label: String, modifier: Modifier, on: Boolean = false, onClick: () -> Unit) {
    val c = vk
    Box(modifier.height(34.dp).clip(RoundedCornerShape(50)).background(if (on) c.accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
        .border(1.dp, if (on) c.accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (on) c.accent else Color(0xFFE6E8EB), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Soundboard(send: (String) -> Unit) {
    val items = listOf(
        "drumroll" to "Drum roll", "ding" to "Ding", "horn" to tr("Busina", "Horn"), "boom" to "Boom",
        "wow" to "Wow", "aww" to "Aww", "boo" to "Boo", "laugh" to tr("Tawa", "Laugh"),
        "angry" to tr("Galit", "Angry"), "omg" to "OMG", "wownaman" to "Wow naman", "stop" to "Stop"
    )
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (k, label) -> SfxChip(label, Modifier.weight(1f)) { send(k) } }
            }
        }
    }
}

// ---------------- NUMERO ----------------
@Composable
private fun NumberTab(local: List<Triple<Int, String, String>>, phoneLocal: List<com.videoketana.app.data.LocalSong>, onReserve: (Song, Boolean, String) -> Unit) {
    val c = vk
    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Song?>(null) }
    var isLocal by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(code) {
        found = null; isLocal = false
        if (code.length == 5) {
            searching = true
            val n = code.toInt()
            if (n >= 90000) {
                // una ang nasa TV; kung wala, ang nasa folder ng phone na ito
                local.firstOrNull { it.first == n }?.let { found = Song("local:$n", n, it.second, it.third, "local", null, null, null, null, null); isLocal = true }
                if (found == null) phoneLocal.firstOrNull { it.code == n }?.let { found = Song("phone:$n", n, it.title, it.artist, "phone", null, null, null, tr("Sa phone", "On phone"), null); isLocal = true }
            } else found = try { Supa.songByCode(n) } catch (e: Exception) { null }
            searching = false
            found?.let { com.videoketana.app.ui.Haptic.found(); kotlinx.coroutines.delay(500); com.videoketana.app.ui.Voice.say(it.title) }
        }
    }
    fun key(k: String) {
        if (Prefs.keyBeep && !Prefs.numVoice) Synth.play("beep", 0.4f)
        com.videoketana.app.ui.Voice.key(k)
        com.videoketana.app.ui.Haptic.tap()
        code = when (k) { "clr" -> ""; "del" -> code.dropLast(1); else -> if (code.length < 5) code + k else code }
    }
    // walang scroll: ang mga key ang lumiliit/lumalaki ayon sa natitirang espasyo
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // LCD
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF05070A), Color(0xFF0B0F14))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), c.accent.copy(alpha = 0.25f))), RoundedCornerShape(18.dp))
            .padding(vertical = 10.dp, horizontal = 14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DotText(if (code.isEmpty()) "-----" else code.padEnd(5, ' '), 34.dp)
                Spacer(Modifier.height(4.dp))
                Text(when {
                    code.length < 5 -> tr("I-type ang song number", "Type the song number")
                    searching -> tr("Hinahanap…", "Searching…")
                    found != null -> found!!.title + if (found!!.artist.isNotBlank()) " · " + found!!.artist else ""
                    else -> tr("Walang kanta sa numerong ito", "No song with this number")
                }, color = when { code.length == 5 && found == null && !searching -> c.danger; found != null -> c.accent; else -> Color(0xFF8C93A1) },
                    fontSize = 14.sp, fontWeight = if (found != null) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // keypad
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("clr", "0", "del")).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k -> KeyPad(k, Modifier.weight(1f).fillMaxHeight()) { key(k) } }
                }
            }
        }
        // aksyon: Unahin | Reserve | Patugtugin ngayon
        val ok = found != null
        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActBtn("up", tr("Unahin", "Next"), ok, Modifier.width(72.dp).fillMaxHeight()) { found?.let { onReserve(it, isLocal, "top"); code = "" } }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                .background(if (ok) Brush.horizontalGradient(listOf(c.accent, Color(0xFF3FC7B6))) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.04f))))
                .clickable(enabled = ok) { found?.let { onReserve(it, isLocal, "reserve"); code = "" } }, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VkIcon("plus", 20.dp, if (ok) c.accentInk else Color(0xFF6B7280)); Spacer(Modifier.width(8.dp))
                    Text(if (ok) "Reserve ${found!!.code}" else "Reserve", color = if (ok) c.accentInk else Color(0xFF6B7280), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            ActBtn("play", tr("Ngayon", "Now"), ok, Modifier.width(72.dp).fillMaxHeight()) { found?.let { onReserve(it, isLocal, "now"); code = "" } }
        }
    }
}

@Composable
private fun KeyPad(k: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xE6232832), Color(0xE6161A21))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))), shape)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        when (k) {
            "del" -> VkIcon("del", 24.dp, Color(0xFFFF8A8A))
            "clr" -> Text("C", color = Color(0xFF8C93A1), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            else -> Text(k, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun ActBtn(icon: String, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val col = if (enabled) Color.White else Color(0xFF5B6270)
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
        .clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        VkIcon(icon, 18.dp, col)
        Text(label, color = col, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ---------------- HANAP ----------------
@Composable
private fun SearchTab(local: List<Triple<Int, String, String>>, phoneLocal: List<com.videoketana.app.data.LocalSong>, onDeleteLocal: (Int) -> Unit, onReserve: (Song) -> Unit) {
    var askDel by remember { mutableStateOf<Song?>(null) }
    askDel?.let { d ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { askDel = null },
            title = { Text(tr("Burahin ang ${d.code}?", "Delete ${d.code}?")) },
            text = { Text(tr("\"${d.title}\" ay mabubura sa listahan at sa folder ng TV. Hindi na maibabalik.", "\"${d.title}\" will be removed from the list and from the TV's folder. This cannot be undone.")) },
            confirmButton = { androidx.compose.material3.TextButton({ onDeleteLocal(d.code); askDel = null }) { Text(tr("Burahin", "Delete"), color = vk.danger) } },
            dismissButton = { androidx.compose.material3.TextButton({ askDel = null }) { Text(tr("Huwag", "Cancel")) } })
    }
    val c = vk
    var q by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<Song>>(emptyList()) }
    var favs by remember { mutableStateOf(Prefs.favorites) }
    var onlyFavs by remember { mutableStateOf(false) }
    LaunchedEffect(q) { delay(250); list = try { Supa.songs(q) } catch (e: Exception) { emptyList() } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 12.dp, 16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(q, { q = it }, tr("Title, artist o numero", "Title, artist or number"), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            VkIconButton("star", { onlyFavs = !onlyFavs }, size = 50.dp, primary = onlyFavs)
        }
        // mga kanta sa folder ng TV (MIDI at iba pa; 90001+)
        val ql = q.trim().lowercase()
        val locals = local.filter { ql.isEmpty() || it.second.lowercase().contains(ql) || it.third.lowercase().contains(ql) || it.first.toString() == ql }
            .map { Song("local:${it.first}", it.first, it.second, it.third, "local", null, null, null, tr("Sa TV", "On TV"), null) }
        // mga kanta sa folder ng phone na ito (hindi kasama ang meron na sa TV)
        val tvTitles = local.map { it.second.lowercase() + "|" + it.third.lowercase() }.toSet()
        val phones = phoneLocal.filter { (it.title.lowercase() + "|" + it.artist.lowercase()) !in tvTitles }
            .filter { ql.isEmpty() || it.title.lowercase().contains(ql) || it.artist.lowercase().contains(ql) }
            .map { Song("phone:${it.code}", it.code, it.title, it.artist, "phone", null, null, null, tr("Sa phone", "On phone") + if (it.isMidi) " (MIDI)" else "", null) }
        val all = locals + phones + list
        val shown = if (onlyFavs) all.filter { it.code in favs } else all
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            items(shown, key = { it.id }) { s ->
                SongRow(s) {
                    if (s.kind == "local") { VkIconButton("trash", { askDel = s }, size = 38.dp, tint = c.danger); Spacer(Modifier.width(6.dp)) }
                    VkIconButton("star", { favs = if (s.code in favs) favs - s.code else favs + s.code; Prefs.favorites = favs }, size = 38.dp, tint = if (s.code in favs) c.accent else c.muted)
                    Spacer(Modifier.width(6.dp))
                    VkButton("Reserve", height = 38.dp, onClick = { onReserve(s) })
                }
            }
            if (shown.isEmpty()) item { Text(if (onlyFavs) tr("Wala pang Paborito. Pindutin ang bituin sa tabi ng kanta.", "No favorites yet. Tap the star next to a song.") else tr("Walang nahanap. Subukan sa YouTube tab.", "Not found. Try the YouTube tab."), color = c.muted, modifier = Modifier.padding(20.dp)) }
        }
    }
}

// ---------------- YOUTUBE ----------------
@Composable
private fun YouTubeTab(onReserve: (String, String) -> Unit) {
    val c = vk
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, Pair<String, String>>>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    fun run(latest: Boolean = false) {
        val link = parseYouTubeId(q)
        if (!latest && q.contains("youtu") && link != null) { onReserve(link, "YouTube video"); q = ""; return }
        if (!latest && q.isBlank()) return
        loading = true; msg = ""
        scope.launch {
            try {
                val j = Supa.ytSearch(q, latest)
                val err = j.optString("error")
                if (err.isNotEmpty()) {
                    results = emptyList()
                    msg = when (err) {
                        "no_key" -> tr("Hindi pa naka-set ang YouTube search. Pwede pa ring mag-paste ng YouTube link.", "YouTube search is not set up yet. You can still paste a YouTube link.")
                        "quota" -> tr("Naubos na ang libreng YouTube search para ngayong araw. Bukas ulit, o mag-paste ng link.", "Today's free YouTube searches are used up. Try tomorrow, or paste a link.")
                        else -> tr("Hindi gumana ang YouTube search. Subukan ulit, o mag-paste ng link.", "YouTube search failed. Try again, or paste a link.")
                    }
                } else {
                    val a = j.optJSONArray("items") ?: JSONArray()
                    results = (0 until a.length()).map { val o = a.getJSONObject(it); o.optString("id") to (o.optString("title") to o.optString("channel")) }
                    if (results.isEmpty()) msg = tr("Walang nahanap.", "Nothing found.")
                }
            } catch (e: Exception) { msg = tr("Hindi maabot ang YouTube. Subukan ulit.", "Cannot reach YouTube. Try again.") }
            loading = false
        }
    }
    LaunchedEffect(Unit) { run(latest = true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 12.dp, 16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(q, { q = it }, tr("Kanta o i-paste ang YouTube link", "Song or paste a YouTube link"), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            VkButton(tr("Hanap", "Search"), primary = true, onClick = { run() }, height = 50.dp)
        }
        if (loading) Text(tr("Hinahanap...", "Searching..."), color = c.muted, modifier = Modifier.padding(horizontal = 18.dp))
        if (msg.isNotEmpty()) Text(msg, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            items(results, key = { it.first }) { (id, tc) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2), contentAlignment = Alignment.Center) { VkIcon("yt", 22.dp, c.danger) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tc.first, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(tc.second, color = c.muted, fontSize = 13.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    VkButton("Reserve", height = 38.dp, onClick = { onReserve(id, tc.first) })
                }
            }
        }
    }
}

// ---------------- PILA ----------------
@Composable
private fun QueueTab(queue: List<QItem>, nowId: String?, onRemove: (QItem) -> Unit, onTop: (QItem) -> Unit) {
    val c = vk
    if (queue.isEmpty()) { Text(tr("Walang nakapila. Mag-reserve ng kanta sa Numero o Hanap.", "The queue is empty. Reserve a song from Number or Search."), color = c.muted, modifier = Modifier.padding(24.dp)); return }
    var pos = 0
    val labels = queue.map { if (it.status == "playing") 0 else ++pos }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(queue, key = { _, q -> q.id }) { i, q ->
            val playing = q.status == "playing"
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (playing) c.surface2 else c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                    if (playing) VkIcon("mic", 20.dp, c.accent) else Text("${labels[i]}", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(q.title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(if (q.source == "youtube") "YouTube" else q.code, q.singer?.let { tr("Kakanta: $it", "Singer: $it") }).joinToString(". "), color = c.muted, fontSize = 13.sp, maxLines = 1)
                }
                if (playing) Text(if (q.id == nowId) tr("Kinakanta", "Singing") else tr("Naipit", "Stuck"), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                else if (labels[i] > 1) { VkIconButton("up", { onTop(q) }, size = 38.dp); Spacer(Modifier.width(6.dp)) }
                VkIconButton("x", { onRemove(q) }, size = 38.dp, tint = c.danger)
            }
        }
    }
}

// ---------------- MIC ----------------
@Composable
private fun MicTab(room: String, fx: JSONObject?, send: (String, Int) -> Unit) {
    val c = vk
    val ctx = LocalContext.current
    val ip = fx?.optString("micIp").orEmpty()
    // ang TV ay ang web (walang micIp): mic ng web sa loob mismo ng app
    if (fx != null && ip.isEmpty()) { WebMic(room); return }
    var on by remember { mutableStateOf(com.videoketana.app.data.MicSender.isOn) }
    var muted by remember { mutableStateOf(com.videoketana.app.data.MicSender.muted) }
    var level by remember { mutableFloatStateOf(0f) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(on) { while (on) { level = com.videoketana.app.data.MicSender.level; delay(50) }; level = 0f }
    fun startMic() {
        err = ""
        com.videoketana.app.data.MicSender.start(ip, fx?.optInt("micPort", com.videoketana.app.data.MicLink.PORT) ?: com.videoketana.app.data.MicLink.PORT) { e -> err = e; on = false }
        on = true; com.videoketana.app.ui.Haptic.reserved()
    }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startMic() else err = tr("Kailangan ang pahintulot sa mic.", "Microphone permission is needed.")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // malaking button ng mic na may singsing ng lakas ng boses
        val ring = 1f + level * 2.2f
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size((120 * ring.coerceAtMost(1.4f)).dp).clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.18f)))
            Box(Modifier.size(120.dp).clip(RoundedCornerShape(50))
                .background(if (on) Brush.verticalGradient(listOf(c.accent, Color(0xFF3FC7B6))) else Brush.verticalGradient(listOf(Color(0xFF232832), Color(0xFF161A21))))
                .border(1.dp, if (on) c.accent else Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                .clickable {
                    if (on) { com.videoketana.app.data.MicSender.stop(); on = false; com.videoketana.app.ui.Haptic.tap() }
                    else if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startMic()
                    else ask.launch(android.Manifest.permission.RECORD_AUDIO)
                }, contentAlignment = Alignment.Center) { VkIcon("mic", 52.dp, if (on) c.accentInk else Color.White) }
        }
        Text(when { fx == null -> tr("Hinahanap ang TV…", "Looking for the TV…"); on && muted -> tr("Naka-mute", "Muted"); on -> tr("Bukas ang mic: kumanta na!", "Mic is on: sing!"); else -> tr("Pindutin para buksan ang mic", "Tap to turn on the mic") },
            color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(tr("Diretso sa TV sa iisang Wi-Fi (walang website). Gumamit ng earphone na may mic para iwas ugong.", "Straight to the TV on the same Wi-Fi (no website). Use earphones with a mic to avoid feedback."),
            color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (err.isNotEmpty()) Text(err, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        if (on) VkButton(if (muted) tr("I-unmute", "Unmute") else tr("I-mute", "Mute"), icon = "mic", onClick = { muted = !muted; com.videoketana.app.data.MicSender.muted = muted }, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(14.dp))
        if (fx != null) {
            FxSlider(tr("Lakas ng mic", "Mic volume"), "micVolume", 0, 150, fx, send, unit = "%")
            FxSlider("Echo", "micEcho", 0, 100, fx, send)
            FxSlider("Reverb", "micReverb", 0, 100, fx, send)
            FxSlider(tr("Linaw", "Clarity"), "micClarity", 0, 100, fx, send)
        }
    }
}

/** Kapag web ang TV: ang mic page ng web, pero nasa loob ng app (hindi na lilipat sa browser). */
@Composable
private fun WebMic(room: String) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(android.Manifest.permission.RECORD_AUDIO) }
    if (!granted) { Text(tr("Kailangan ang pahintulot sa mic.", "Microphone permission is needed."), color = vk.danger, modifier = Modifier.padding(20.dp)); return }
    androidx.compose.ui.viewinterop.AndroidView(factory = { c ->
        android.webkit.WebView(c).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = android.webkit.WebViewClient()
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onPermissionRequest(request: android.webkit.PermissionRequest) { request.grant(request.resources) }
            }
            loadUrl(Config.SITE + "mic/?room=$room&lang=" + Prefs.lang + "&name=" + java.net.URLEncoder.encode(Prefs.name.ifBlank { "Mic" }, "UTF-8"))
        }
    }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun NameDialog(onDone: (String) -> Unit) {
    val c = vk
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(name) },
        containerColor = c.surface,
        title = { Text(tr("Pangalan mo?", "Your name?"), color = c.text) },
        text = {
            Column {
                Text(tr("Lalabas sa TV kapag ikaw na ang kakanta.", "Shown on the TV when it is your turn."), color = c.muted, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(name, { name = it.take(40) }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name) }) { Text(tr("Sige", "OK"), color = c.accent, fontWeight = FontWeight.Bold) } }
    )
}

@Suppress("unused") private val keepAspect = Modifier.aspectRatio(1f)
