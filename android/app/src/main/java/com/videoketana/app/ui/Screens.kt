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
    var list by remember { mutableStateOf<List<Song>?>(null) }
    LaunchedEffect(q) {
        delay(250)
        list = try { Supa.songs(q) } catch (e: Exception) { emptyList() }
    }
    val room = Prefs.lastRoom
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Songbook", "Songbook"), list?.let { tr("${it.size} na kanta", "${it.size} songs") } ?: tr("Naglo-load", "Loading"), onClose = { go(Screen.Home) })
        SearchField(q, { q = it }, tr("Title, artist o numero", "Title, artist or number"), Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
            if (list?.isEmpty() == true) item { Text(tr("Walang nahanap.", "Nothing found."), color = c.muted, modifier = Modifier.padding(24.dp)) }
        }
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
    var score by remember { mutableStateOf(Prefs.scoreOn) }
    var checking by remember { mutableStateOf(false) }
    var standby by remember { mutableStateOf(Prefs.standby) }
    var tvLand by remember { mutableStateOf(Prefs.tvLandscape) }
    var interlude by remember { mutableStateOf(Prefs.interlude) }
    var bgMode by remember { mutableStateOf(Prefs.bgMode) }
    var bgScene by remember { mutableStateOf(Prefs.bgScene) }
    var after by remember { mutableStateOf(Prefs.standbyAfter) }
    var sbColor by remember { mutableStateOf(Prefs.standbyColor) }
    val pickWall = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            if (!com.videoketana.app.data.Wallpaper.save(ctx, uri)) Toast.makeText(ctx, tr("Hindi magamit ang larawang ito.", "This picture cannot be used."), Toast.LENGTH_SHORT).show()
            else if (Prefs.glass == 0) Prefs.changeGlass(35)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Settings", "Settings"), tr("Naka-save sa device na ito", "Saved on this device"), onClose = { go(Screen.Home) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Group(tr("Pagkonekta", "Connecting")) { ConnectGuide(go) }
            Group(tr("Folder sa device", "Folder on this device")) { FolderSection() }
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
                                .clickable { Prefs.changeDisplayFont(v) }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                // halimbawa ng bawat estilo
                                DotText("8:30", 22.dp, androidx.compose.ui.graphics.Color(0xFF62E3D2), ghost = false, style = v)
                                Spacer(Modifier.height(6.dp))
                                Text(label, color = if (on) c.accent else androidx.compose.ui.graphics.Color(0xFFB9C0CA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Group(tr("TV screen sa phone o tablet", "TV screen on a phone or tablet")) {
                Toggle(tr("Pahiga (landscape)", "Landscape"), tr("Kusang nakahiga ang screen pagbukas ng Buksan sa TV.", "The screen turns sideways when you open the TV screen."), tvLand) { tvLand = it; Prefs.tvLandscape = it }
                Toggle(tr("Interlude habang naghihintay", "Interlude while waiting"), tr("Malumanay na musika sa TV kapag walang kanta. Gawa ng app, walang kinopya.", "Soft music on the TV when no song is playing. Made by the app, nothing copied."), interlude) { interlude = it; Prefs.interlude = it }
                if (interlude) SliderRow(tr("Lakas ng interlude", "Interlude volume"), "", Prefs.interludeVol, 0, 100, "%") { Prefs.interludeVol = it; com.videoketana.app.tv.Interlude.setVolume(it) }
                SetRow(tr("Background ng TV (BGV)", "TV background (BGV)"), tr("Saan kukunin ang background video.", "Where to get the background video.")) {}
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Seg(listOf("auto" to tr("Lahat", "All"), "device" to tr("Device", "Device"), "online" to "Library", "anim" to tr("Sarili", "Built-in")), bgMode) { bgMode = it; Prefs.bgMode = it }
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
                Toggle(tr("Beep sa pag-type ng numero", "Beep when typing numbers"), "", beep) { beep = it; Prefs.keyBeep = it }
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
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(L.shownPath, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(tr("MIDI → .mid / .kar (pwedeng i-extract dito ang zip)\nKanta → .mp3 / .mp4 (+ .lrc na kapareho ng pangalan)\nBGV → background video (.mp4)",
            "MIDI → .mid / .kar (you can extract a zip here)\nKanta → .mp3 / .mp4 (+ .lrc with the same name)\nBGV → background video (.mp4)"), color = c.text, fontSize = 14.sp)
        Text(tr("Sa TV na device ito binabasa (ang device na nagpapatugtog). Hindi ina-upload ang mga file. Siguraduhing may karapatan ka sa mga ito.",
            "Read on the TV device (the one that plays). Files are not uploaded. Make sure you have the rights to them."), color = c.muted, fontSize = 12.sp)
        if (info.isNotEmpty()) Text(info, color = c.text, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!ok) VkButton(tr("Payagan ang access", "Allow access"), primary = true, height = 42.dp, onClick = { ask.launch(L.permissions()) })
            else VkButton(tr("I-scan ulit", "Scan again"), icon = "refresh", height = 42.dp, onClick = { refresh() })
            if (android.os.Build.VERSION.SDK_INT >= 30) VkButton(tr("Buong access", "Full access"), height = 42.dp, onClick = {
                if (!L.openAllFilesSetting(ctx)) Toast.makeText(ctx, tr("Walang ganitong setting sa device na ito.", "This device has no such setting."), Toast.LENGTH_LONG).show()
            })
        }
        Text(tr("Kung hindi lumalabas ang .kar o ilang file, gamitin ang \"Buong access\".", "If .kar or some files do not show, use \"Full access\"."), color = c.muted, fontSize = 12.sp)
    }
}
