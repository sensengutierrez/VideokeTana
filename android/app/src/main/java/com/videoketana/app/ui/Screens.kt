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

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Settings", "Settings"), tr("Naka-save sa device na ito", "Saved on this device"), onClose = { go(Screen.Home) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Group(tr("Itsura", "Appearance")) {
                SetRow(tr("Wika", "Language"), tr("Ang wika ng buong app.", "The language of the whole app.")) {
                    Seg(listOf("fil" to "Tagalog", "en" to "English"), Prefs.lang) { Prefs.setLang(it) }
                }
                SetRow(tr("Tema", "Theme"), "") {
                    Seg(listOf("dark" to tr("Dark", "Dark"), "light" to tr("Light", "Light")), if (Prefs.dark) "dark" else "light") { Prefs.setDark(it == "dark") }
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
            Box(Modifier.background(if (on) c.text else c.surface).clickable { onPick(v) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, color = if (on) c.bg else c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
