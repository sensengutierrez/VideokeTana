package com.videoketana.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoketana.app.data.LocalSong
import com.videoketana.app.data.Song
import com.videoketana.app.data.Supa
import com.videoketana.app.data.tr
import com.videoketana.app.ui.DotText
import com.videoketana.app.ui.Haptic
import com.videoketana.app.ui.SearchField
import com.videoketana.app.ui.VkIcon
import com.videoketana.app.ui.Voice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ACC = Color(0xFF62E3D2)
private val INK2 = Color(0xFF062320)
private val MUTE = Color(0xFF8C93A1)

/** Kitang-kita kung saan nakatutok ang remote ng TV (D-pad). */
fun Modifier.tvFocus(): Modifier = composed {
    var f by remember { mutableStateOf(false) }
    // puting makapal na guhit + liwanag, para kita agad kahit sa malayo
    this.onFocusChanged { f = it.isFocused }.then(if (f) Modifier.background(Color.White.copy(alpha = 0.22f)).border(4.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier)
}

/** Maliit na "#" sa gilid ng TV screen: bukas ang sariling remote kapag iisang device lang ang gamit. */
@Composable
fun SoloTab(dim: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 54.dp, height = 96.dp)
        .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
        .background(if (dim) Color(0x55101318) else Color(0xCC101318))
        .border(1.dp, ACC.copy(alpha = if (dim) 0.25f else 0.6f), RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
        .tvFocus().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("#", color = ACC.copy(alpha = if (dim) 0.6f else 1f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            VkIcon("search", 16.dp, Color.White.copy(alpha = if (dim) 0.5f else 0.9f))
        }
    }
}

/**
 * Remote sa loob mismo ng TV screen (para sa iisang phone/tablet o TV na may touch/remote).
 * mode ng reserve: "reserve" (sa dulo), "now" (patugtugin ngayon).
 */
@Composable
fun SoloPanel(local: List<LocalSong>, hasAccess: Boolean, onAccess: () -> Unit, queue: List<com.videoketana.app.data.QItem>, currentId: String?, onClose: () -> Unit,
              onRemove: (com.videoketana.app.data.QItem) -> Unit, onYt: (String, String) -> Unit, onCmd: (String) -> Unit,
              onReserve: (Song?, LocalSong?, String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); try { firstFocus.requestFocus() } catch (e: Exception) { } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0x99000000)).focusProperties { canFocus = false }.clickable(onClick = onClose)) {
        val wide = maxWidth > maxHeight
        val panelMod = if (wide) Modifier.align(Alignment.CenterEnd).width(minOf(maxWidth * 0.46f, 460.dp)).fillMaxHeight()
        else Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight * 0.82f)
        Column(panelMod.clip(RoundedCornerShape(26.dp)).background(Brush.verticalGradient(listOf(Color(0xFA181C24), Color(0xFA0D1015))))
            .border(1.dp, ACC.copy(alpha = 0.3f), RoundedCornerShape(26.dp)).clickable(enabled = false) {}.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    listOf("hash" to tr("Numero", "Number"), "search" to tr("Hanap", "Search"), "yt" to "YouTube", "list" to tr("Pila", "Queue"), "mic" to "Mic").forEachIndexed { i, (ic, label) ->
                        val on = tab == i
                        Row(Modifier.padding(end = 6.dp).then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier).clip(RoundedCornerShape(50)).background(if (on) ACC else Color.White.copy(alpha = 0.06f)).tvFocus().clickable { tab = i }
                            .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            VkIcon(ic, 16.dp, if (on) INK2 else Color.White)
                            if (on) { Spacer(Modifier.width(6.dp)); Text(label, color = INK2, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).tvFocus().clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    VkIcon("x", 18.dp, Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            when (tab) {
                0 -> SoloKeypad(local, hasAccess, onAccess, onReserve)
                1 -> SoloSearch(local, onReserve)
                2 -> SoloYouTube(onYt)
                3 -> SoloQueue(queue, currentId, onRemove, onCmd)
                else -> SoloMic()
            }
        }
    }
}

@Composable
private fun SoloKeypad(local: List<LocalSong>, hasAccess: Boolean, onAccess: () -> Unit, onReserve: (Song?, LocalSong?, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Song?>(null) }
    var foundLocal by remember { mutableStateOf<LocalSong?>(null) }
    LaunchedEffect(code) {
        found = null; foundLocal = null
        if (code.length == 5) {
            val n = code.toInt()
            if (n >= 90000) foundLocal = local.firstOrNull { it.code == n }
            else found = try { Supa.songByCode(n) } catch (e: Exception) { null }
            if (found != null || foundLocal != null) { Haptic.found(); delay(400); Voice.say(found?.title ?: foundLocal!!.title) }
        }
    }
    val ok = found != null || foundLocal != null
    val title = found?.let { it.title + if (it.artist.isNotBlank()) " · " + it.artist else "" } ?: foundLocal?.let { it.title + if (it.artist.isNotBlank()) " · " + it.artist else "" }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF05070A)).border(1.dp, ACC.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(10.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DotText(if (code.isEmpty()) "-----" else code.padEnd(5, ' '), 30.dp)
                val mine = code.length == 5 && !ok && code.toInt() >= 90000
                Text(when {
                    code.length < 5 -> tr("I-type ang numero", "Type the number")
                    ok -> title!!
                    mine && !hasAccess -> tr("Hindi pa nababasa ang folder ng TV na ito", "This TV's folder can't be read yet")
                    mine -> tr("Wala sa folder ng TV na ito (${local.size} kanta). Nasa phone? I-reserve sa phone.", "Not in this TV's folder (${local.size} songs). On a phone? Reserve from the phone.")
                    else -> tr("Walang kanta sa numerong ito", "No song with this number") },
                    color = when { ok -> ACC; code.length == 5 -> Color(0xFFFF7A7A); else -> MUTE }, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (mine && !hasAccess) Box(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(ACC).tvFocus().clickable { onAccess() }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(tr("Payagan ang folder", "Allow the folder"), color = INK2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("clr", "0", "del")).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF232832), Color(0xFF161A21))))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .tvFocus().clickable {
                                Haptic.tap(); Voice.key(k)
                                code = when (k) { "clr" -> ""; "del" -> code.dropLast(1); else -> if (code.length < 5) code + k else code }
                            }, contentAlignment = Alignment.Center) {
                            when (k) {
                                "del" -> VkIcon("del", 22.dp, Color(0xFFFF8A8A))
                                "clr" -> Text("C", color = MUTE, fontSize = 22.sp)
                                else -> Text(k, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(if (ok) ACC else Color.White.copy(alpha = 0.06f))
                .tvFocus().clickable(enabled = ok) { onReserve(found, foundLocal, "reserve"); code = "" }, contentAlignment = Alignment.Center) {
                Text("Reserve", color = if (ok) INK2 else Color(0xFF5B6270), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
            Box(Modifier.width(96.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .tvFocus().clickable(enabled = ok) { onReserve(found, foundLocal, "now"); code = "" }, contentAlignment = Alignment.Center) {
                Text(tr("Ngayon", "Now"), color = if (ok) Color.White else Color(0xFF5B6270), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SoloSearch(local: List<LocalSong>, onReserve: (Song?, LocalSong?, String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(q) { delay(250); list = try { Supa.songs(q, 200) } catch (e: Exception) { emptyList() } }
    val ql = q.trim().lowercase()
    val locs = local.filter { ql.isEmpty() || it.title.lowercase().contains(ql) || it.artist.lowercase().contains(ql) || it.code.toString() == ql }
    Column(Modifier.fillMaxSize()) {
        SearchField(q, { q = it }, tr("Title, artist o numero", "Title, artist or number"), Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(locs, key = { "l" + it.code }) { s -> SoloRow(s.code, s.title, s.artist, tr("Sa device", "On device")) { onReserve(null, s, "reserve") } }
            items(list, key = { it.id }) { s -> SoloRow(s.code, s.title, s.artist, if (s.kind == "youtube") "YouTube" else s.category ?: "") { onReserve(s, null, "reserve") } }
        }
    }
}

@Composable
private fun SoloRow(code: Int, title: String, artist: String, tag: String, onReserve: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(70.dp)) { DotText(code.toString(), 13.dp, ghost = false) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(artist, tag).filter { it.isNotBlank() }.joinToString(" · "), color = MUTE, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(RoundedCornerShape(50)).background(ACC).tvFocus().clickable { Haptic.reserved(); onReserve() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Reserve", color = INK2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}


@Composable
private fun SoloYouTube(onYt: (String, String) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    fun run() {
        val id = com.videoketana.app.data.parseYouTubeId(q)
        if (q.contains("youtu") && id != null) { onYt(id, "YouTube video"); q = ""; return }
        if (q.isBlank()) return
        msg = tr("Hinahanap…", "Searching…")
        scope.launch {
            try {
                val j = Supa.ytSearch(q)
                val a = j.optJSONArray("items") ?: org.json.JSONArray()
                res = (0 until a.length()).map { val o = a.getJSONObject(it); Triple(o.optString("id"), o.optString("title"), o.optString("channel")) }
                msg = if (j.optString("error").isNotEmpty()) tr("Hindi gumana ang search. Pwedeng mag-paste ng link.", "Search failed. You can paste a link.") else if (res.isEmpty()) tr("Walang nahanap.", "Nothing found.") else ""
            } catch (e: Exception) { msg = tr("Walang internet?", "No internet?") }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(q, { q = it }, tr("Hanapin o i-paste ang link", "Search or paste a link"), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(ACC).tvFocus().clickable { run() }.padding(horizontal = 14.dp, vertical = 14.dp)) { VkIcon("search", 18.dp, INK2) }
        }
        if (msg.isNotEmpty()) Text(msg, color = MUTE, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(res, key = { it.first }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VkIcon("yt", 20.dp, Color(0xFFFF6B6B)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.second, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(r.third, color = MUTE, fontSize = 12.sp, maxLines = 1)
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(ACC).tvFocus().clickable { Haptic.reserved(); onYt(r.first, r.second) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text("Reserve", color = INK2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SoloQueue(queue: List<com.videoketana.app.data.QItem>, currentId: String?, onRemove: (com.videoketana.app.data.QItem) -> Unit, onCmd: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // kontrol ng kanta
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("restart" to "restart", "stop" to "stop", "pause" to "toggle", "next" to "skip").forEach { (ic, cmd) ->
                Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (cmd == "stop") Color(0x33FF7A7A) else Color.White.copy(alpha = 0.07f))
                    .tvFocus().clickable { Haptic.tap(); onCmd(cmd) }, contentAlignment = Alignment.Center) { VkIcon(ic, 20.dp, if (cmd == "stop") Color(0xFFFF8A8A) else Color.White) }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (queue.isEmpty()) Text(tr("Walang laman ang pila.", "The queue is empty."), color = MUTE)
        LazyColumn(Modifier.fillMaxSize()) {
            items(queue, key = { it.id }) { q ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(70.dp)) { if (q.source == "youtube") VkIcon("yt", 18.dp, Color(0xFFFF6B6B)) else DotText(q.code, 13.dp, ghost = false) }
                    Column(Modifier.weight(1f)) {
                        Text(q.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (q.id == currentId) tr("Kinakanta ngayon", "Now singing") else (q.singer ?: ""), color = if (q.id == currentId) ACC else MUTE, fontSize = 12.sp)
                    }
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).tvFocus().clickable { onRemove(q) }, contentAlignment = Alignment.Center) {
                        VkIcon("trash", 16.dp, Color(0xFFFF8A8A))
                    }
                }
            }
        }
    }
}

/** Mic sa iisang device: ang mic ng device na ito → sariling speaker (dumadaan sa parehong echo/reverb ng TV). */
@Composable
private fun SoloMic() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(com.videoketana.app.data.MicSender.isOn) }
    var level by remember { mutableStateOf(0f) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(on) { while (on) { level = com.videoketana.app.data.MicSender.level; delay(50) }; level = 0f }
    fun go() { err = ""; com.videoketana.app.data.MicSender.start("127.0.0.1") { e -> err = e; on = false }; on = true }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { if (it) go() else err = tr("Kailangan ang pahintulot sa mic.", "Microphone permission is needed.") }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size((110 * (1f + (level * 2f).coerceAtMost(0.4f))).dp).clip(RoundedCornerShape(50)).background(if (on) ACC else Color.White.copy(alpha = 0.08f))
            .tvFocus().clickable {
                if (on) { com.videoketana.app.data.MicSender.stop(); on = false }
                else if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) go()
                else ask.launch(android.Manifest.permission.RECORD_AUDIO)
            }, contentAlignment = Alignment.Center) { VkIcon("mic", 46.dp, if (on) INK2 else Color.White) }
        Spacer(Modifier.height(12.dp))
        Text(if (on) tr("Bukas ang mic", "Mic is on") else tr("Pindutin para gamitin ang mic ng device na ito", "Tap to use this device's mic"), color = Color.White, fontWeight = FontWeight.Bold)
        Text(tr("Gumamit ng earphone na may mic para walang ugong.", "Use earphones with a mic to avoid feedback."), color = MUTE, fontSize = 13.sp)
        if (err.isNotEmpty()) Text(err, color = Color(0xFFFF7A7A), fontSize = 13.sp)
    }
}
