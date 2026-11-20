package com.videoketana.app.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoketana.app.BuildConfig
import com.videoketana.app.Screen
import com.videoketana.app.data.Prefs
import com.videoketana.app.data.Song
import com.videoketana.app.data.Supa
import com.videoketana.app.data.Updater
import com.videoketana.app.data.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Itaas ng bawat screen: ✕ (pabalik), pamagat, at kanang bahagi. */
@Composable
fun TopBar(title: String, sub: String = "", onClose: () -> Unit, right: @Composable () -> Unit = {}) {
    val c = vk
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        VkIconButton("x", onClose)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, color = c.muted, fontSize = 13.sp)
        }
        right()
    }
}

/** Search field na may icon. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val c = vk
    Row(modifier.height(50.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        VkIcon("search", 18.dp, c.muted); Spacer(Modifier.width(10.dp))
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = c.muted, fontSize = 16.sp); inner() } })
        if (value.isNotEmpty()) VkIconButton("x", { onChange("") }, size = 32.dp)
    }
}

/** Isang kanta sa listahan: numero (LCD), title, artist, at aksyon sa kanan. */
@Composable
fun SongRow(s: Song, right: @Composable () -> Unit = {}) {
    val c = vk
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(78.dp)) { DotText(s.code.toString(), 15.dp, ghost = false) }
        Column(Modifier.weight(1f)) {
            Text(s.title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(s.artist.ifBlank { null }, s.category, if (s.kind == "youtube") "YouTube" else null).joinToString(", "),
                color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        right()
    }
}

// ================= SONGBOOK =================
@Composable
fun SongbookScreen(go: (Screen) -> Unit) {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var src by remember { mutableStateOf("online") }
    var list by remember { mutableStateOf<List<Song>?>(null) }
    var locals by remember { mutableStateOf<List<com.videoketana.app.data.LocalSong>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var askDel by remember { mutableStateOf<com.videoketana.app.data.LocalSong?>(null) }
    val L = com.videoketana.app.data.LocalLib
    LaunchedEffect(q) { delay(250); list = try { Supa.songs(q) } catch (e: Exception) { emptyList() } }
    LaunchedEffect(src, reload) {
        if (src == "device") locals = if (L.hasPermission(ctx)) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { L.ensureFolders(); L.scan() } else emptyList()
    }
    val room = Prefs.lastRoom
    val ql = q.trim().lowercase()
    val shownLocal = (locals ?: emptyList()).filter { ql.isEmpty() || it.title.lowercase().contains(ql) || it.artist.lowercase().contains(ql) || it.code.toString() == ql }
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Songbook", "Songbook"),
            if (src == "online") list?.let { tr("${it.size} na kanta", "${it.size} songs") } ?: tr("Naglo-load", "Loading") else tr("${shownLocal.size} sa device na ito", "${shownLocal.size} on this device"),
            onClose = { go(Screen.Home) })
        Row(Modifier.padding(horizontal = 16.dp)) {
            Seg(listOf("online" to "Online", "device" to tr("Sa device na ito", "On this device")), src) { src = it }
        }
        Spacer(Modifier.height(8.dp))
        SearchField(q, { q = it }, tr("Title, artist o numero", "Title, artist or number"), Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        if (src == "device") {
            if (!L.hasPermission(ctx)) Text(tr("Payagan muna ang access: Settings → Folder sa device.", "Allow access first: Settings → Folder on this device."), color = c.danger, modifier = Modifier.padding(16.dp))
            else Text(tr("Folder: ${L.shownPath}. Ang basurahan ay nagbubura ng file sa folder.", "Folder: ${L.shownPath}. The trash deletes the file from the folder."), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shownLocal, key = { it.code }) { ls ->
                    SongRow(Song("local:${ls.code}", ls.code, ls.title, ls.artist, "local", null, null, null, if (ls.isMidi) "MIDI" else ls.ext.uppercase(), null)) {
                        VkIconButton("trash", { askDel = ls }, size = 38.dp, tint = c.danger)
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
                }
                if (locals != null && shownLocal.isEmpty()) item {
                    Text(tr("Walang nakita sa folder. Ilagay ang .mid/.kar sa ${L.shownPath}/MIDI. Kung nandoon na pero wala dito, pindutin ang \"Buong access\" sa Settings → Folder sa device.",
                        "Nothing found. Put .mid/.kar in ${L.shownPath}/MIDI. If they are there but not listed, tap \"Full access\" in Settings → Folder on this device."), color = c.muted, modifier = Modifier.padding(24.dp))
                }
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list ?: emptyList(), key = { it.id }) { s ->
                SongRow(s) {
                    if (room.isNotEmpty()) VkButton(tr("Reserve", "Reserve"), height = 38.dp, onClick = {
                        scope.launch {
                            try { Supa.reserve(room, Prefs.name, songId = s.id); Toast.makeText(ctx, tr("Naka-reserve sa room $room", "Reserved in room $room"), Toast.LENGTH_SHORT).show() }
                            catch (e: Exception) { Toast.makeText(ctx, tr("Hindi na-reserve: ", "Could not reserve: ") + e.message, Toast.LENGTH_LONG).show() }
                        }
                    })
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
            }
            if (list?.isEmpty() == true) item { Text(tr("Walang nahanap. Ang pagbura ng online na kanta ay nasa Library (admin).", "Nothing found. Deleting online songs is in the Library (admin)."), color = c.muted, modifier = Modifier.padding(24.dp)) }
        }
    }
    askDel?.let { d ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { askDel = null },
            title = { Text(tr("Burahin ang ${d.code}?", "Delete ${d.code}?")) },
            text = { Text(tr("\"${d.title}\" (${d.file.name}) ay mabubura sa folder. Hindi na maibabalik.", "\"${d.title}\" (${d.file.name}) will be deleted from the folder. This cannot be undone.")) },
            confirmButton = { androidx.compose.material3.TextButton({
                val ok = L.delete(d); askDel = null; reload++
                Toast.makeText(ctx, if (ok) tr("Nabura.", "Deleted.") else tr("Hindi mabura. Kailangan ang \"Buong access\" sa Settings.", "Could not delete. \"Full access\" in Settings is needed."), Toast.LENGTH_LONG).show()
            }) { Text(tr("Burahin", "Delete"), color = vk.danger) } },
            dismissButton = { androidx.compose.material3.TextButton({ askDel = null }) { Text(tr("Huwag", "Cancel")) } })
    }
}

// ================= SETTINGS =================
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(go: (Screen) -> Unit) {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Prefs.name) }
    var sfx by remember { mutableStateOf(Prefs.sfx) }
    var beep by remember { mutableStateOf(Prefs.keyBeep) }
    var numVoice by remember { mutableStateOf(Prefs.numVoice) }
    var vibrate by remember { mutableStateOf(Prefs.vibrate) }
    var score by remember { mutableStateOf(Prefs.scoreOn) }
    var checking by remember { mutableStateOf(false) }
    var standby by remember { mutableStateOf(Prefs.standby) }
    var tvLand by remember { mutableStateOf(Prefs.tvLandscape) }
    var interlude by remember { mutableStateOf(Prefs.interlude) }
    var introMode by remember { mutableStateOf(Prefs.str("introMode").ifEmpty { "full" }) }
    var autoSkip by remember { mutableStateOf(Prefs.autoSkipIntro) }
    var scoreSnd by remember { mutableStateOf(Prefs.str("scoreSound").ifEmpty { "builtin" }) }
    var bgMode by remember { mutableStateOf(Prefs.bgMode) }
    var bgScene by remember { mutableStateOf(Prefs.bgScene) }
    var after by remember { mutableStateOf(Prefs.standbyAfter) }
    var sbColor by remember { mutableStateOf(Prefs.standbyColor) }
    var lySize by remember { mutableStateOf(Prefs.lyricSize) }
    var hlColor by remember { mutableStateOf(Prefs.hlColor) }
    var lyOutline by remember { mutableStateOf(Prefs.lyricOutline) }
    var countdown by remember { mutableStateOf(Prefs.countdownOn) }
    var clockTz by remember { mutableStateOf(Prefs.clockTz) }
    // ipadala rin sa TV na konektado (web o app) para sabay ang itsura ng lyrics
    fun pushLyr() {
        val o = org.json.JSONObject().put("lyricSize", lySize).put("hlColor", hlColor).put("outline", lyOutline).put("countdown", if (countdown) "num" else "off")
        listOf(Prefs.lastRoom, Prefs.hostRoom).filter { it.isNotEmpty() }.distinct().forEach { r -> com.videoketana.app.data.RoomPing.cmd(r, "lyr", o) }
    }
    val pickWall = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            if (!com.videoketana.app.data.Wallpaper.save(ctx, uri)) Toast.makeText(ctx, tr("Hindi magamit ang larawang ito.", "This picture cannot be used."), Toast.LENGTH_SHORT).show()
            else if (Prefs.glass == 0) Prefs.changeGlass(35)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Settings", "Settings"), tr("Naka-save sa device na ito", "Saved on this device"), onClose = { go(Screen.Home) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Group(tr("Folder sa device", "Folder on this device")) { FolderSection() }
            Group(tr("MIDI at SoundFont", "MIDI and SoundFont")) { SoundFontSection() }
            Group(tr("Songbook", "Songbook")) { SongbookExport() }
            Group(tr("Itsura", "Appearance")) {
                SetRow(tr("Wika", "Language"), tr("Ang wika ng buong app.", "The language of the whole app.")) {
                    Seg(listOf("fil" to "Tagalog", "en" to "English"), Prefs.lang) { Prefs.changeLang(it) }
                }
                SetRow(tr("Tema", "Theme"), "") {
                    Seg(listOf("dark" to tr("Dark", "Dark"), "light" to tr("Light", "Light")), if (Prefs.dark) "dark" else "light") { Prefs.changeDark(it == "dark") }
                }
            }
            Group(tr("Estilo ng numero at orasan", "Number and clock style")) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Para sa orasan, song number at room code.", "For the clock, song numbers and room code."), color = c.muted, style = VkType.small)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("dot" to "LCD", "seg" to "Digital", "bold" to tr("Makapal", "Bold"), "thin" to tr("Manipis", "Thin")).forEach { (v, label) ->
                            val on = Prefs.displayFont == v
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color.Black)
                                .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line, RoundedCornerShape(14.dp))
                                .clickable {
                                    Prefs.changeDisplayFont(v)
                                    // sabay ang TV na konektado (web o app)
                                    listOf(Prefs.lastRoom, Prefs.hostRoom).filter { it.isNotEmpty() }.distinct().forEach { r -> com.videoketana.app.data.RoomPing.cmd(r, "font", v) }
                                }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                // halimbawa ng bawat estilo
                                DotText("8:30", 22.dp, androidx.compose.ui.graphics.Color(0xFF62E3D2), ghost = false, style = v)
                                Spacer(Modifier.height(6.dp))
                                Text(label, color = if (on) c.accent else androidx.compose.ui.graphics.Color(0xFFB9C0CA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Group(tr("Lyrics sa TV", "Lyrics on the TV")) {
                Column(Modifier.padding(16.dp)) {
                    // halimbawa
                    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color(0xFF0B0C10)), contentAlignment = Alignment.Center) {
                        val hl = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hlColor)) } catch (e: Exception) { c.accent }
                        val fs = when (lySize) { "s" -> 17; "l" -> 25; "xl" -> 29; else -> 21 }
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(color = hl)) { append(tr("Ikaw ang ", "You are ")) }
                            withStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.White)) { append(tr("aking mahal", "my love")) }
                        }, fontSize = fs.sp, fontWeight = FontWeight.ExtraBold,
                            style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.geometry.Offset(0f, if (lyOutline == 2) 0f else 2f), when (lyOutline) { 0 -> 4f; 2 -> 14f; else -> 8f })))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Kulay ng kinakanta", "Highlight color"), color = c.text, style = VkType.title)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("#ffd54a", "#62e3d2", "#ff7ab6", "#7cc4ff", "#9be15d", "#ffa552", "#c49bff").forEach { col ->
                            val on = hlColor.equals(col, true)
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(col)))
                                .border(if (on) 3.dp else 1.dp, if (on) c.text else c.line, RoundedCornerShape(50))
                                .clickable { hlColor = col; Prefs.hlColor = col; pushLyr() })
                        }
                    }
                }
                SetRow(tr("Laki ng lyrics", "Lyrics size"), "") {
                    Seg(listOf("s" to "S", "m" to "M", "l" to "L", "xl" to "XL"), lySize) { lySize = it; Prefs.lyricSize = it; pushLyr() }
                }
                SetRow(tr("Anino ng letra", "Text outline"), tr("Mas makapal, mas madaling basahin sa maliwanag na BGV.", "Thicker is easier to read on bright backgrounds.")) {
                    Seg(listOf("0" to tr("Manipis", "Light"), "1" to tr("Katamtaman", "Medium"), "2" to tr("Makapal", "Heavy")), lyOutline.toString()) { lyOutline = it.toInt(); Prefs.lyricOutline = lyOutline; pushLyr() }
                }
                Toggle(tr("Bilang 3-2-1 bago kumanta", "3-2-1 countdown before singing"), "", countdown) { countdown = it; Prefs.countdownOn = it; pushLyr() }
                Text(tr("Sabay na nagbabago ang TV na konektado (web o app).", "The connected TV (web or app) changes too."), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            }
            Group(tr("TV screen sa phone o tablet", "TV screen on a phone or tablet")) {
                SetRow(tr("Oras sa TV", "TV clock"), tr("Sundan: kukunin ang oras ng bansa mula sa phone na nakakonekta (para tama kahit mali ang timezone ng Mi TV).", "Follow phone: uses the time zone of the connected phone (correct even if the TV's time zone is wrong).")) {
                    Seg(listOf("auto" to tr("Sundan phone", "Follow phone"), "device" to "Device"), clockTz) { clockTz = it; Prefs.clockTz = it }
                }
                Toggle(tr("Pahiga (landscape)", "Landscape"), tr("Kusang nakahiga ang screen pagbukas ng Buksan sa TV.", "The screen turns sideways when you open the TV screen."), tvLand) { tvLand = it; Prefs.tvLandscape = it }
                SetRow(tr("Intro pagbukas ng app", "Intro when the app opens"), tr("Ang umiikot na numero at tunog sa simula.", "The spinning number and sound at the start.")) {
                    Seg(listOf("full" to tr("Buo", "Full"), "short" to tr("Maikli", "Short"), "off" to tr("Wala", "Off")), introMode) { introMode = it; Prefs.putStr("introMode", if (it == "full") "" else it) }
                }
                SetRow(tr("Tunog ng score", "Score sound"), tr("Sarili: mga file sa Download/VideokeTana/Score na pinangalanang score_61_75.mp3, score_100.mp3 (saklaw ng score). ${com.videoketana.app.data.LocalLib.scoreFiles().size} na file ngayon.",
                    "Own: files in Download/VideokeTana/Score named score_61_75.mp3, score_100.mp3 (score range). ${com.videoketana.app.data.LocalLib.scoreFiles().size} files now.")) {
                    Seg(listOf("builtin" to tr("Kasama", "Built-in"), "own" to tr("Sarili", "Own")), scoreSnd) { scoreSnd = it; Prefs.putStr("scoreSound", if (it == "own") "own" else "") }
                }
                Toggle(tr("Laktawan ang mahabang intro ng kanta", "Skip long song intros"), tr("Diretso sa 3 segundo bago ang unang linya ng lyrics (kapag ang device na ito ang TV). May \"⏭ Intro\" din sa remote.", "Jumps to 3 seconds before the first lyric line (when this device is the TV). The remote also has \"⏭ Intro\"."), autoSkip) { autoSkip = it; Prefs.autoSkipIntro = it }
                Toggle(tr("Interlude habang naghihintay", "Interlude while waiting"), tr("Malumanay na musika sa TV kapag walang kanta. Gawa ng app, walang kinopya.", "Soft music on the TV when no song is playing. Made by the app, nothing copied."), interlude) { interlude = it; Prefs.interlude = it }
                if (interlude) SliderRow(tr("Lakas ng interlude", "Interlude volume"), "", Prefs.interludeVol, 0, 100, "%") { Prefs.interludeVol = it; com.videoketana.app.tv.Interlude.setVolume(it) }
                SetRow(tr("Background ng TV (BGV)", "TV background (BGV)"), tr("Saan kukunin ang background video.", "Where to get the background video.")) {}
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Seg(listOf("auto" to tr("Lahat", "All"), "mix" to tr("Halo", "Mix"), "device" to tr("Device", "Device"), "online" to "Library", "anim" to tr("Sarili", "Built-in")), bgMode) { bgMode = it; Prefs.bgMode = it }
                }
                Text(tr("Lahat/Device/Library: video kung meron (Device = folder na Download/VideokeTana/BGV). Sarili: animation na gawa ng app. Kapag walang video, ang napiling eksena sa ibaba.",
                    "All/Device/Library: video when available (Device = Download/VideokeTana/BGV folder). Built-in: the app's own animation. With no video, the scene below is used."),
                    color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                SetRow(tr("Eksena (sariling BGV)", "Scene (built-in BGV)"), "") {}
                Box(Modifier.padding(horizontal = 16.dp)) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("cycle") + com.videoketana.app.tv.BG_SCENES).forEach { k ->
                            val label = when (k) { "cycle" -> tr("Iikot", "Rotate"); "lights" -> tr("Ilaw", "Lights"); "stage" -> tr("Entablado", "Stage"); "stars" -> tr("Bituin", "Stars"); "sunset" -> tr("Dapithapon", "Sunset"); else -> "Retro" }
                            val on = k == bgScene
                            Column(Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).border(if (on) 3.dp else 1.dp, if (on) c.accent else c.line, RoundedCornerShape(12.dp)).clickable { bgScene = k; Prefs.bgScene = k }) {
                                if (k == "cycle") Box(Modifier.fillMaxWidth().height(54.dp).background(c.surface2), contentAlignment = Alignment.Center) { VkIcon("refresh", 22.dp, c.accent) }
                                else com.videoketana.app.tv.SceneCanvas(k, Modifier.fillMaxWidth().height(54.dp))
                                Text(label, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Group(tr("Wallpaper at transparency", "Wallpaper and transparency")) {
                SetRow(tr("Wallpaper", "Wallpaper"), if (Prefs.wallpaper.isEmpty()) tr("Sariling larawan bilang background. Nasa device lang, hindi ina-upload.", "Your own picture as the background. Stays on this device.") else tr("Naka-set ang sariling larawan.", "Your picture is set.")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (Prefs.wallpaper.isNotEmpty()) VkIconButton("trash", { com.videoketana.app.data.Wallpaper.clear(ctx) }, size = 40.dp, tint = c.danger)
                        VkButton(tr("Pumili", "Choose"), height = 40.dp, onClick = { pickWall.launch("image/*") })
                    }
                }
                SliderRow(tr("Transparency ng mga kahon", "Box transparency"), tr("Gawing see-through ang mga kahon para makita ang wallpaper.", "Make boxes see-through to show the wallpaper."), Prefs.glass, 0, 85, "%") { Prefs.changeGlass(it) }
                SliderRow(tr("Dilim ng wallpaper", "Wallpaper darkness"), tr("Mas madilim, mas madaling basahin.", "Darker is easier to read."), Prefs.wallDim, 0, 85, "%") { Prefs.changeWallDim(it) }
            }
            Group(tr("StandBy sa phone", "Phone StandBy")) {
                Toggle(tr("StandBy kapag hindi ginagamit", "StandBy when idle"), tr("Madilim na malaking orasan, ang kinakanta at ang susunod, habang nakabukas ang remote. Iharap nang pahiga para mas malaki.", "A dim big clock with the current and next song while the remote is open. Turn the phone sideways for a bigger view."), standby) { standby = it; Prefs.standby = it }
                SetRow(tr("Lalabas pagkatapos ng", "Show after"), "") {
                    Seg(listOf("15" to "15s", "30" to "30s", "60" to "1m", "120" to "2m"), after.toString()) { after = it.toInt(); Prefs.standbyAfter = after }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(tr("Kulay", "Color"), color = c.text, style = VkType.title)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        STANDBY_COLORS.forEach { (col, _) ->
                            val on = sbColor == col
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color(col))
                                .border(if (on) 3.dp else 1.dp, if (on) c.text else c.line, RoundedCornerShape(50))
                                .clickable { sbColor = col; Prefs.standbyColor = col })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                        DotText("8:30", 40.dp, androidx.compose.ui.graphics.Color(sbColor).copy(alpha = 0.9f))
                    }
                }
            }
            Group(tr("Ikaw", "You")) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Pangalan mo", "Your name"), color = c.text, style = VkType.title)
                    Text(tr("Lalabas sa TV kapag ikaw na ang kakanta.", "Shown on the TV when it is your turn."), color = c.muted, style = VkType.small)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(name, { name = it.take(40); Prefs.name = name }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Group(tr("Tunog", "Sound")) {
                Toggle(tr("Sound effects", "Sound effects"), tr("Intro, palakpak, soundboard at score.", "Intro, applause, soundboard and score."), sfx) { sfx = it; Prefs.sfx = it }
                Toggle(tr("Vibrate sa pag-type", "Vibrate when typing"), tr("Ramdam ang bawat numero, at mas mahaba kapag na-reserve.", "Feel each number, and a longer buzz when reserved."), vibrate) { vibrate = it; Prefs.vibrate = it; if (it) Haptic.reserved() }
                Toggle(tr("Boses na nagbibilang", "Counting voice"), tr("Sinasabi ang bawat numerong tina-type, at ang title ng kanta.", "Says each number you type, and the song title."), numVoice) { numVoice = it; Prefs.numVoice = it }
                Toggle(tr("Beep sa pag-type ng numero", "Beep when typing numbers"), tr("Kapag naka-off ang boses.", "When the voice is off."), beep) { beep = it; Prefs.keyBeep = it }
                Toggle(tr("Score pagkatapos ng kanta", "Score after each song"), tr("Sa TV screen ng app na ito.", "On this app's TV screen."), score) { score = it; Prefs.scoreOn = it }
            }
            Group(tr("App", "App")) {
                SetRow("VideokeTana ${BuildConfig.VERSION_NAME}", tr("Sariling update: sasabihin ng app kapag may bago.", "Self-updating: the app tells you when there is a new version.")) {
                    VkButton(if (checking) tr("Tinitingnan", "Checking") else tr("Tingnan", "Check"), height = 40.dp, onClick = {
                        if (checking) return@VkButton
                        checking = true
                        scope.launch {
                            val u = Updater.check(); checking = false
                            if (u == null) Toast.makeText(ctx, tr("Pinakabago na ang app mo.", "Your app is up to date."), Toast.LENGTH_SHORT).show()
                            else { Prefs.skipUpdate = 0; Toast.makeText(ctx, tr("May bersyon ${u.number}. Bumalik sa home para i-update.", "Version ${u.number} is available. Go back home to update."), Toast.LENGTH_LONG).show(); go(Screen.Home) }
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun SliderRow(title: String, sub: String, value: Int, min: Int, max: Int, unit: String, onDone: (Int) -> Unit) {
    val c = vk
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row { Text(title, color = c.text, style = VkType.title, modifier = Modifier.weight(1f)); Text("${v.toInt()}$unit", color = c.muted, fontSize = 14.sp) }
        if (sub.isNotEmpty()) Text(sub, color = c.muted, style = VkType.small)
        androidx.compose.material3.Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onDone(v.toInt()) }, valueRange = min.toFloat()..max.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line))
    }
}

@Composable
fun Group(title: String, content: @Composable () -> Unit) {
    val c = vk
    Column {
        Text(title, color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Panel(Modifier.fillMaxWidth()) { Column { content() } }
    }
}

@Composable
fun SetRow(title: String, sub: String, right: @Composable () -> Unit) {
    val c = vk
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = c.text, style = VkType.title)
            if (sub.isNotEmpty()) Text(sub, color = c.muted, style = VkType.small)
        }
        right()
    }
}

@Composable
fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = vk
    SetRow(title, sub) {
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.accentInk))
    }
}

@Composable
fun Seg(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    val c = vk
    Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, c.line, RoundedCornerShape(10.dp))) {
        options.forEach { (v, label) ->
            val on = v == value
            Box(Modifier.background(if (on) c.accent else c.surface).clickable { onPick(v) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, color = if (on) c.accentInk else c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}


/** Gabay: paano gawing main (TV) ang device, at paano sumali bilang remote. */
@Composable
fun ConnectGuide(go: (Screen) -> Unit) {
    val c = vk
    @Composable
    fun Card(icon: String, title: String, steps: List<String>, button: String, onClick: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VkIcon(icon, 22.dp, c.accent); Spacer(Modifier.width(10.dp))
                Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            steps.forEachIndexed { i, t ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}.", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                    Text(t, color = c.text, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            VkButton(button, onClick = onClick, icon = icon, height = 42.dp)
        }
    }
    Card("tv", tr("Main: ito ang TV (dito tutugtog)", "Main: this is the TV (songs play here)"), listOf(
        tr("Sa Mi TV o Android TV: buksan ang VideokeTana. Kusa itong magiging TV screen.", "On a Mi TV or Android TV: open VideokeTana. It becomes the TV screen by itself."),
        tr("Sa phone o tablet: Home, tapos \"Buksan sa TV\". Pwedeng pahiga.", "On a phone or tablet: Home, then \"Open on TV\". It can go landscape."),
        tr("Lalabas ang QR code at room code (hal. ABCD). Iwanang bukas ang screen.", "A QR code and room code appear (e.g. ABCD). Keep the screen open."),
        tr("Iisang Wi-Fi para sa mic. Para sa kanta at remote, kahit anong internet.", "Same Wi-Fi for the mic. For songs and the remote, any internet works.")
    ), tr("Gawing TV ang device na ito", "Use this device as the TV")) { go(Screen.Tv) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
    Card("phone", tr("Sumali: remote ang phone", "Join: the phone is the remote"), listOf(
        tr("Sa Home, pindutin ang \"I-scan ang QR code\" at itapat sa QR ng TV.", "On Home, tap \"Scan the QR code\" and point it at the TV's QR."),
        tr("O i-type ang room code (hal. ABCD) at pindutin ang Sumali.", "Or type the room code (e.g. ABCD) and tap Join."),
        tr("Walang app? I-scan lang ng camera ang QR: bubukas ang remote sa browser.", "No app? Just scan the QR with the camera: the remote opens in the browser."),
        tr("Pwedeng sabay-sabay ang maraming phone sa iisang room.", "Many phones can join the same room at once.")
    ), tr("Sumali sa room", "Join a room")) { go(Screen.Home) }
}


/** Folder na Download/VideokeTana: MIDI, Kanta, BGV. Para sa mga file na na-download o in-extract sa device. */
@Composable
fun FolderSection() {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val L = com.videoketana.app.data.LocalLib
    var ok by remember { mutableStateOf(L.hasPermission(ctx)) }
    var info by remember { mutableStateOf("") }
    fun refresh() {
        ok = L.hasPermission(ctx)
        if (!ok) { info = ""; return }
        scope.launch {
            info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val made = L.ensureFolders()
                val songs = L.scan(); val bg = L.bgvFiles()
                if (!made && !L.root.isDirectory) tr("Hindi magawa ang folder. Gumawa ng folder na VideokeTana sa Download, at sa loob nito: MIDI, Kanta, BGV.",
                    "Could not create the folder. Make a VideokeTana folder in Download, with MIDI, Kanta and BGV inside.")
                else tr("${songs.count { it.isMidi }} MIDI, ${songs.count { !it.isMidi }} kanta, ${bg.size} BGV. Numero: 90001 pataas.",
                    "${songs.count { it.isMidi }} MIDI, ${songs.count { !it.isMidi }} songs, ${bg.size} BGV. Numbers: 90001 and up.")
            }
        }
    }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    LaunchedEffect(Unit) { refresh() }
    // pagbalik mula sa setting ng Android: i-check ulit
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(life) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh() }
        life.lifecycle.addObserver(o); onDispose { life.lifecycle.removeObserver(o) }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(L.shownPath, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(tr("MIDI → .mid / .kar (pwedeng i-extract dito ang zip)\nKanta → .mp3 / .mp4 (+ .lrc na kapareho ng pangalan)\nBGV → background video (.mp4)",
            "MIDI → .mid / .kar (you can extract a zip here)\nKanta → .mp3 / .mp4 (+ .lrc with the same name)\nBGV → background video (.mp4)"), color = c.text, fontSize = 14.sp)
        Text(tr("Sa TV na device ito binabasa (ang device na nagpapatugtog). Hindi ina-upload ang mga file. Siguraduhing may karapatan ka sa mga ito.",
            "Read on the TV device (the one that plays). Files are not uploaded. Make sure you have the rights to them."), color = c.muted, fontSize = 12.sp)
        if (info.isNotEmpty()) Text(info, color = c.text, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!ok) VkButton(tr("Payagan ang access", "Allow access"), primary = true, height = 42.dp, onClick = {
                // Android 11+: "Buong access" ang pinaka-sigurado (nakikita ang .mid/.kar at nabubura); kung wala sa device, ang karaniwang permission
                if (android.os.Build.VERSION.SDK_INT < 30 || !L.openAllFilesSetting(ctx)) ask.launch(L.permissions())
            })
            else VkButton(tr("I-scan ulit", "Scan again"), icon = "refresh", height = 42.dp, onClick = { refresh() })
            if (android.os.Build.VERSION.SDK_INT >= 30) VkButton(tr("Buong access", "Full access"), height = 42.dp, onClick = {
                if (!L.openAllFilesSetting(ctx)) Toast.makeText(ctx, tr("Walang ganitong setting sa device na ito.", "This device has no such setting."), Toast.LENGTH_LONG).show()
            })
        }
        Text(tr("Kung hindi lumalabas ang .kar o ilang file, gamitin ang \"Buong access\".", "If .kar or some files do not show, use \"Full access\"."), color = c.muted, fontSize = 12.sp)
    }
}


/** I-download ang buong songbook bilang Excel (online + mga kanta sa device na ito). */
@Composable
fun SongbookExport() {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Excel file ng lahat ng kanta: numero, title, artist, kategorya, uri at kung saan galing. Pang-print o pang-share habang dumadami ang kanta.",
            "An Excel file of every song: number, title, artist, category, type and source. To print or share as the list grows."), color = c.muted, fontSize = 13.sp)
        if (msg.isNotEmpty()) Text(msg, color = c.text, fontSize = 14.sp)
        VkButton(if (busy) tr("Ginagawa...", "Making...") else tr("I-download ang Songbook (Excel)", "Download the Songbook (Excel)"), icon = "list", primary = true, enabled = !busy,
            modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; msg = ""
                scope.launch {
                    try {
                        val (f, where) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.videoketana.app.data.Xlsx.songbook(ctx) }
                        msg = if (where.isNotEmpty()) tr("Na-save sa $where", "Saved to $where") else tr("Handa na ang file.", "The file is ready.")
                        com.videoketana.app.data.Xlsx.share(ctx, f)
                    } catch (e: Exception) { msg = tr("Hindi nagawa: ", "Could not make it: ") + e.message }
                    busy = false
                }
            })
    }
}


/** SoundFont: estado, download, pagpili ng .sf2, at "Subukan ang tunog". */
@Composable
fun SoundFontSection() {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val SF = com.videoketana.app.tv.SoundFonts
    var engine by remember { mutableStateOf(Prefs.str("midiEngine").ifEmpty { "sf" }) }
    var chosen by remember { mutableStateOf(SF.chosenName()) }
    var files by remember { mutableStateOf(SF.localFiles()) }
    var def by remember { mutableStateOf(SF.defaultFile(ctx)) }
    var prog by remember { mutableStateOf(-1f) }
    var msg by remember { mutableStateOf("") }
    val player = remember { com.videoketana.app.tv.SynthPlayer() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { player.clear() } }
    fun refresh() { files = SF.localFiles(); def = SF.defaultFile(ctx) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Ang SoundFont ang nagbibigay ng tunog ng mga instrumento sa MIDI. Kasama ang GeneralUser GS (libre, mga 300 instrumento).",
            "The SoundFont gives MIDI its instrument sounds. GeneralUser GS is included (free, about 300 instruments)."), color = c.muted, fontSize = 13.sp)
        Seg(listOf("sf" to "SoundFont", "basic" to tr("Simple", "Basic")), engine) { engine = it; Prefs.putStr("midiEngine", if (it == "sf") "" else it) }
        var quality by remember { mutableStateOf(Prefs.str("synthQuality").ifEmpty { "auto" }) }
        Text(tr("Kalidad", "Quality") + ": " + if (quality == "auto") (if (com.videoketana.app.tv.lowPowerDevice(ctx)) tr("Auto (magaan sa device na ito)", "Auto (light on this device)") else tr("Auto (mataas sa device na ito)", "Auto (high on this device)")) else "",
            color = c.muted, fontSize = 13.sp)
        Seg(listOf("auto" to "Auto", "high" to tr("Mataas", "High"), "lite" to tr("Magaan", "Light")), quality) { quality = it; Prefs.putStr("synthQuality", if (it == "auto") "" else it) }
        // estado
        val d = def
        val inApp = remember { SF.bundled(ctx) }
        if (inApp) Text(tr("✓ Kasama na sa app ang GeneralUser GS: hindi na kailangang i-download.", "✓ GeneralUser GS is built into the app: no download needed."), color = c.accent, fontSize = 14.sp)
        else Text(if (d != null) tr("✓ Nakahanda: GeneralUser GS (${d.length() / 1048576} MB)", "✓ Ready: GeneralUser GS (${d.length() / 1048576} MB)") + "\n" +
                (if (d.parentFile == SF.folder) "Download/VideokeTana/SoundFont" else tr("nasa loob ng app", "inside the app"))
            else tr("Hindi pa na-download ang GeneralUser GS (32 MB).", "GeneralUser GS is not downloaded yet (32 MB)."),
            color = if (d != null) c.accent else c.text, fontSize = 14.sp)
        if (prog >= 0f) androidx.compose.material3.LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth(), color = c.accent)
        // pagpili
        if (files.any { it.name != SF.DEFAULT }) {
            Text(tr("Piliin ang SoundFont", "Choose the SoundFont"), color = c.text, fontWeight = FontWeight.Bold)
            (listOf("" to "GeneralUser GS") + files.filter { it.name != SF.DEFAULT }.map { it.name to it.nameWithoutExtension + " (" + it.length() / 1048576 + " MB)" }).forEach { (k, label) ->
                val on = chosen == k
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(12.dp)).clickable { chosen = k; Prefs.putStr("soundfont", k) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (on) VkIcon("check", 18.dp, c.accent)
                }
            }
        }
        Text(tr("Para magdagdag: ilagay ang .sf2 sa Download/VideokeTana/SoundFont, tapos pindutin ang I-scan.", "To add one: put the .sf2 in Download/VideokeTana/SoundFont, then tap Scan."), color = c.muted, fontSize = 12.sp)
        if (msg.isNotEmpty()) Text(msg, color = c.text, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d == null && !inApp) VkButton(if (prog >= 0f) "${(prog * 100).toInt()}%" else tr("I-download", "Download"), primary = true, enabled = prog < 0f, height = 42.dp, onClick = {
                prog = 0f; msg = ""
                scope.launch {
                    try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SF.download(ctx) { prog = it } }; msg = tr("Na-download!", "Downloaded!") }
                    catch (e: Exception) { msg = tr("Hindi na-download: ", "Download failed: ") + e.message }
                    prog = -1f; refresh()
                }
            })
            VkButton(tr("Subukan ang tunog", "Test the sound"), icon = "play", height = 42.dp, enabled = prog < 0f, onClick = {
                msg = tr("Inihahanda...", "Preparing...")
                scope.launch {
                    try {
                        val sf = SF.get(ctx) { st -> if (st == "download") msg = tr("Dina-download muna (32 MB)...", "Downloading first (32 MB)...") }
                        player.start(sf, SF.testSong(), 100)
                        msg = tr("Gumagana ang SoundFont: may ${sf.presets.size} na tunog. Naririnig mo ba ang piano, bass, strings at drums?", "The SoundFont works: ${sf.presets.size} sounds. Do you hear piano, bass, strings and drums?")
                    } catch (e: Throwable) { msg = tr("Hindi gumana: ", "Did not work: ") + e.message }
                    refresh()
                }
            })
            VkButton(tr("I-scan", "Scan"), icon = "refresh", height = 42.dp, onClick = { refresh(); msg = "" })
        }
    }
}
