package com.videoketana.app.remote

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
data class NowState(val title: String, val artist: String, val code: String, val source: String, val singer: String, val id: String?, val paused: Boolean, val volume: Int, val local: List<Triple<Int, String, String>>)

private fun parseState(p: JSONObject): NowState {
    val n = p.optJSONObject("now")
    val loc = p.optJSONArray("local") ?: JSONArray()
    return NowState(
        title = n?.optString("title") ?: "", artist = n?.optString("artist") ?: "", code = n?.opt("code")?.toString() ?: "",
        source = n?.optString("source") ?: "", singer = n?.optString("singer") ?: "", id = n?.optString("id"),
        paused = p.optBoolean("paused"), volume = p.optInt("volume", 100),
        local = (0 until loc.length()).map { val o = loc.getJSONObject(it); Triple(o.optInt("code"), o.optString("title"), o.optString("artist")) }
    )
}

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
    var showBoard by remember { mutableStateOf(false) }
    var rt by remember { mutableStateOf<Realtime?>(null) }

    suspend fun loadQueue() { queue = try { Supa.queue(room) } catch (e: Exception) { queue } }

    DisposableEffect(room) {
        val r = Realtime(room,
            onBroadcast = { ev, p ->
                when (ev) {
                    "state" -> { now = parseState(p); hostSeen = true }
                    "score" -> Toast.makeText(ctx, tr("Score", "Score") + (p.optString("singer").takeIf { it.isNotBlank() }?.let { tr(" ni $it", " for $it") } ?: "") + ": " + p.optInt("score"), Toast.LENGTH_LONG).show()
                }
            },
            onQueueChange = { scope.launch { loadQueue() } },
            onStatus = { ok -> connected = ok; if (ok) rt?.broadcast("hello", JSONObject()) })
        rt = r; r.connect()
        onDispose { r.close() }
    }
    LaunchedEffect(connected) { if (connected) { rt?.broadcast("hello", JSONObject()); loadQueue() } }
    LaunchedEffect(room) { while (true) { loadQueue(); delay(15000) } }

    fun cmd(action: String, value: Any? = null) {
        if (!hostSeen) Toast.makeText(ctx, tr("Hindi pa makita ang TV sa room $room.", "The TV in room $room cannot be found yet."), Toast.LENGTH_SHORT).show()
        rt?.broadcast("cmd", JSONObject().put("action", action).apply { if (value != null) put("value", value) })
    }
    suspend fun reserve(songId: String? = null, ytId: String? = null, ytTitle: String? = null, localCode: Int? = null, localTitle: String? = null): Boolean = try {
        Supa.reserve(room, Prefs.name, songId, ytId, ytTitle, localCode, localTitle)
        Synth.play("ding", 0.5f)
        val pos = queue.count { it.status == "waiting" } + 1
        Toast.makeText(ctx, if (now?.title.isNullOrEmpty() && pos <= 1) tr("Naka-reserve. Magsisimula na sa TV.", "Reserved. Starting on the TV.") else tr("Naka-reserve. Pang-$pos ka sa pila.", "Reserved. You are number $pos in the queue."), Toast.LENGTH_SHORT).show()
        loadQueue(); true
    } catch (e: Exception) { Toast.makeText(ctx, tr("Hindi na-reserve: ", "Could not reserve: ") + e.message, Toast.LENGTH_LONG).show(); false }

    Column(Modifier.fillMaxSize()) {
        // itaas: ✕, koneksyon, room
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            VkIconButton("x", { go(Screen.Home) })
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(if (connected && hostSeen) c.accent else if (connected) c.muted else c.danger))
            Spacer(Modifier.width(8.dp))
            Text("VideokeTana", color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                DotText(room, 16.dp, ghost = false)
            }
        }
        if (connected && !hostSeen) Text(tr("Hinahanap ang TV sa room $room. Siguraduhing bukas ang TV screen at napindot ang Simulan.", "Looking for the TV in room $room. Make sure the TV screen is open and Start was pressed."),
            color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp))

        // kinakanta ngayon + kontrol
        Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), display = true) {
            Column(Modifier.padding(16.dp)) {
                Row { Text(tr("Kinakanta ngayon", "Now singing"), color = c.displayMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    now?.let { Text(tr("Lakas ${it.volume}", "Volume ${it.volume}"), color = c.displayMuted, fontSize = 13.sp) } }
                Spacer(Modifier.height(6.dp))
                val n = now
                if (n == null || n.title.isEmpty()) {
                    Text(tr("Walang tumutugtog", "Nothing is playing"), color = c.displayText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(tr("Mag-reserve ng kanta para magsimula.", "Reserve a song to start."), color = c.displayMuted, fontSize = 14.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n.source != "youtube") { DotText(n.code, 16.dp, ghost = false); Spacer(Modifier.width(10.dp)) }
                        Text(n.title, color = c.displayText, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(listOfNotNull(n.artist.ifBlank { null }, n.singer.ifBlank { null }?.let { tr("kumakanta: $it", "singing: $it") }).joinToString(", "), color = c.displayMuted, fontSize = 14.sp, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VkIconButton("restart", { cmd("restart") }, size = 48.dp)
                    VkIconButton("voldown", { cmd("voldown") }, size = 48.dp)
                    VkButton("", icon = if (now?.paused == true) "play" else "pause", primary = true, onClick = { cmd("toggle") }, modifier = Modifier.weight(1f), height = 48.dp)
                    VkIconButton("volup", { cmd("volup") }, size = 48.dp)
                    VkIconButton("next", { cmd("skip") }, size = 48.dp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SfxChip(tr("Palakpak", "Applause"), Modifier.weight(1f)) { cmd("sfx", JSONObject().put("name", "applause").put("from", Prefs.name)) }
                    SfxChip("Cheers", Modifier.weight(1f)) { cmd("sfx", JSONObject().put("name", "cheers").put("from", Prefs.name)) }
                    SfxChip(if (showBoard) tr("Isara", "Close") else "Soundboard", Modifier.weight(1f), on = showBoard) { showBoard = !showBoard }
                }
                if (showBoard) Soundboard { name -> if (name == "stop") cmd("sfx", JSONObject().put("name", "stop")) else cmd("sfx", JSONObject().put("name", name).put("from", Prefs.name)) }
            }
        }

        // mga tab
        val tabs = listOf("hash" to tr("Numero", "Number"), "search" to tr("Hanap", "Search"), "yt" to "YouTube", "list" to tr("Pila", "Queue"), "mic" to "Mic")
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            tabs.forEachIndexed { i, (ic, label) ->
                val on = tab == i
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { tab = i }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        VkIcon(ic, 22.dp, if (on) c.accent else c.muted)
                        val w = queue.count { it.status == "waiting" }
                        if (i == 3 && w > 0) Box(Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(18.dp).clip(RoundedCornerShape(50)).background(c.accent), contentAlignment = Alignment.Center) {
                            Text("$w", color = c.accentInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(label, color = if (on) c.text else c.muted, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.padding(top = 4.dp).width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (on) c.accent else c.bg))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> NumberTab(now?.local ?: emptyList()) { s, local -> scope.launch { if (local) reserve(localCode = s.code, localTitle = (if (s.artist.isNotBlank()) s.artist + " - " else "") + s.title) else reserve(songId = s.id) } }
                1 -> SearchTab { s -> scope.launch { reserve(songId = s.id) } }
                2 -> YouTubeTab { id, title -> scope.launch { reserve(ytId = id, ytTitle = title) } }
                3 -> QueueTab(queue, now?.id,
                    onRemove = { q -> scope.launch { try { Supa.removeFromQueue(q.id); loadQueue() } catch (e: Exception) { Toast.makeText(ctx, e.message, Toast.LENGTH_SHORT).show() } } },
                    onTop = { q -> scope.launch { val min = queue.filter { it.status == "waiting" }.minOfOrNull { it.sortOrder } ?: 0.0; try { Supa.moveToTop(q.id, min); loadQueue() } catch (e: Exception) {} } })
                else -> MicTab(room)
            }
        }
    }

    if (askName) NameDialog { n -> Prefs.name = n; askName = false }
}

@Composable
private fun SfxChip(label: String, modifier: Modifier, on: Boolean = false, onClick: () -> Unit) {
    val c = vk
    Box(modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, if (on) c.displayFg else c.displayLine, RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (on) c.displayFg else c.displayText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
private fun NumberTab(local: List<Triple<Int, String, String>>, onReserve: (Song, Boolean) -> Unit) {
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
                local.firstOrNull { it.first == n }?.let { found = Song("local:$n", n, it.second, it.third, "local", null, null, null, null, null); isLocal = true }
            } else found = try { Supa.songByCode(n) } catch (e: Exception) { null }
            searching = false
        }
    }
    fun key(k: String) {
        if (Prefs.keyBeep) Synth.play("beep", 0.4f)
        code = when (k) { "clr" -> ""; "del" -> code.dropLast(1); else -> if (code.length < 5) code + k else code }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Panel(Modifier.fillMaxWidth(), display = true) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DotText(code.padEnd(5, ' '), 44.dp)
                Spacer(Modifier.height(8.dp))
                Text(when {
                    code.length < 5 -> tr("I-type ang song number", "Type the song number")
                    searching -> tr("Hinahanap...", "Searching...")
                    found != null -> found!!.title + if (found!!.artist.isNotBlank()) ", " + found!!.artist else ""
                    else -> tr("Walang kanta sa numerong ito", "No song with this number")
                }, color = if (code.length == 5 && found == null && !searching) c.danger else c.displayText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("clr", "0", "del")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp)).clickable { key(k) }, contentAlignment = Alignment.Center) {
                        when (k) {
                            "del" -> VkIcon("del", 24.dp)
                            "clr" -> Text("C", color = c.muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            else -> Text(k, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        VkButton(if (found != null) tr("Reserve ${found!!.code}", "Reserve ${found!!.code}") else tr("Reserve", "Reserve"), primary = true, enabled = found != null,
            onClick = { found?.let { onReserve(it, isLocal); code = "" } }, modifier = Modifier.fillMaxWidth(), height = 54.dp)
    }
}

// ---------------- HANAP ----------------
@Composable
private fun SearchTab(onReserve: (Song) -> Unit) {
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
        val shown = if (onlyFavs) list.filter { it.code in favs } else list
        LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            items(shown, key = { it.id }) { s ->
                SongRow(s) {
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
private fun MicTab(room: String) {
    val c = vk
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { VkIcon("mic", 48.dp, c.muted) }
        Spacer(Modifier.height(16.dp))
        Text(tr("Mic sa phone: malapit na sa app", "Phone mic: coming soon to the app"), color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(tr("Ginagawa pa ang mic sa loob ng app. Habang wala pa, magagamit ang mic sa web remote ng parehong room.", "The in-app mic is still being built. Meanwhile, use the mic in the web remote for the same room."),
            color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        VkButton(tr("Buksan ang mic sa web", "Open the mic on the web"), icon = "mic", onClick = {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.SITE + "remote/?room=$room&tab=mic")).setPackage("com.android.chrome").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).let { i ->
                if (i.resolveActivity(ctx.packageManager) != null) i else Intent(Intent.ACTION_VIEW, Uri.parse(Config.SITE + "remote/?room=$room&tab=mic")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        })
    }
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
