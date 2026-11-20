package com.videoketana.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoketana.app.Screen
import com.videoketana.app.data.Admin
import com.videoketana.app.data.CATEGORIES
import com.videoketana.app.data.PickedFile
import com.videoketana.app.data.Song
import com.videoketana.app.data.Supa
import com.videoketana.app.data.licenseYouTube
import com.videoketana.app.data.parseYouTubeId
import com.videoketana.app.data.pickedFile
import com.videoketana.app.data.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Library ng kanta sa app (kapareho ng Admin ng web): login, upload, YouTube, listahan na may lisensya, at pagbura.
 * Admin lang (unang account) ang pinapayagan ng server na magdagdag o magbura.
 */
@Composable
fun LibraryScreen(go: (Screen) -> Unit) {
    var logged by remember { mutableStateOf(Admin.loggedIn) }
    var admin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(logged) { admin = if (logged) try { Admin.isAdmin() } catch (e: Exception) { null } else null }
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Library", "Library"), if (logged) Admin.email else tr("Pagdagdag at pag-ayos ng kanta", "Add and manage songs"), onClose = { go(Screen.Home) }) {
            if (logged) VkButton(tr("Logout", "Log out"), height = 38.dp, onClick = { Admin.logout(); logged = false })
        }
        if (!logged) LoginPanel { logged = true }
        else {
            if (admin == false) Text(tr("Hindi admin ang account na ito. Makikita mo ang listahan pero hindi ka makakapagdagdag o makakabura.",
                "This account is not the admin. You can see the list but cannot add or delete."),
                color = vk.danger, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LibraryBody()
        }
    }
}

@Composable
private fun LoginPanel(onDone: () -> Unit) {
    val c = vk
    val scope = rememberCoroutineScope()
    var mail by remember { mutableStateOf(Admin.email) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Mag-login gamit ang admin account na ginawa mo sa web (Admin page). Ang unang account ang admin.",
            "Log in with the admin account you made on the web (Admin page). The first account is the admin."), color = c.muted, fontSize = 14.sp)
        Field(tr("Email", "Email"), mail, { mail = it }, keyboard = KeyboardType.Email)
        Field(tr("Password", "Password"), pass, { pass = it }, password = true)
        if (err.isNotEmpty()) Text(err, color = c.danger, fontSize = 14.sp)
        VkButton(if (busy) tr("Naglo-login...", "Logging in...") else tr("Mag-login", "Log in"), primary = true, enabled = !busy && mail.isNotBlank() && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; err = ""
                scope.launch {
                    try { Admin.login(mail, pass); onDone() } catch (e: Exception) { err = e.message ?: "Error" }
                    busy = false
                }
            })
    }
}

@Composable
private fun LibraryBody() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Seg(listOf("0" to tr("Mga kanta", "Songs"), "1" to tr("Upload", "Upload"), "2" to "YouTube"), tab.toString()) { tab = it.toInt() }
        }
        when (tab) {
            0 -> SongsTab()
            1 -> UploadTab { tab = 0 }
            else -> YouTubeAddTab { tab = 0 }
        }
    }
}

// ---------- listahan na may lisensya ----------
@Composable
private fun SongsTab() {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<Song>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Song?>(null) }
    var edit by remember { mutableStateOf<Song?>(null) }
    var lrcFor by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(q, reload) { delay(250); list = try { Supa.songs(q, 500) } catch (e: Exception) { emptyList() } }
    val pickLrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val s = lrcFor ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try { Admin.setLrc(ctx, s, pickedFile(ctx, uri)); toast(ctx, tr("Na-save ang lyrics ng ${s.code}.", "Saved lyrics for ${s.code}.")); reload++ }
            catch (e: Exception) { toast(ctx, e.message ?: "Error") }
        }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(q, { q = it }, tr("Title, artist o numero", "Title, artist or number"), Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Text(list?.let { tr("${it.size} na kanta", "${it.size} songs") } ?: tr("Naglo-load", "Loading"), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list ?: emptyList(), key = { it.id }) { s ->
                Column(Modifier.fillMaxWidth()) {
                    SongRow(s) {
                        if (s.kind == "audio") VkIconButton("music", { lrcFor = s; pickLrc.launch(arrayOf("*/*")) }, size = 38.dp)
                        Spacer(Modifier.width(6.dp))
                        VkIconButton("gear", { edit = s }, size = 38.dp)
                        Spacer(Modifier.width(6.dp))
                        VkIconButton("trash", { confirm = s }, size = 38.dp, tint = c.danger)
                    }
                    val tags = buildList {
                        if (s.kind == "audio") add(if (com.videoketana.app.tv.isMidiPath(s.mediaPath)) "MIDI" else if (s.lrcPath != null) tr("May lyrics", "Has lyrics") else tr("Walang lyrics", "No lyrics"))
                        add(s.licenseNote ?: tr("Walang nakasulat na lisensya", "No license note"))
                    }
                    Text(tags.joinToString(". "), color = if (s.licenseNote == null) c.danger else c.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 94.dp, end = 16.dp, bottom = 10.dp))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.line))
                }
            }
            if (list?.isEmpty() == true) item { Text(tr("Walang nahanap.", "Nothing found."), color = c.muted, modifier = Modifier.padding(24.dp)) }
        }
    }
    confirm?.let { s ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(tr("Burahin ang ${s.code}?", "Delete ${s.code}?")) },
            text = { Text(tr("\"${s.title}\" at ang file nito ay mabubura. Hindi na maibabalik.", "\"${s.title}\" and its file will be deleted. This cannot be undone.")) },
            confirmButton = { TextButton({
                confirm = null
                scope.launch {
                    try { Admin.deleteSong(s); toast(ctx, tr("Nabura ang ${s.code}.", "Deleted ${s.code}.")); reload++ } catch (e: Exception) { toast(ctx, e.message ?: "Error") }
                }
            }) { Text(tr("Burahin", "Delete"), color = vk.danger) } },
            dismissButton = { TextButton({ confirm = null }) { Text(tr("Huwag", "Cancel")) } })
    }
    edit?.let { s -> EditDialog(s, onClose = { edit = null }, onSaved = { edit = null; reload++ }) }
}

@Composable
private fun EditDialog(s: Song, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(s.title) }
    var artist by remember { mutableStateOf(s.artist) }
    var cat by remember { mutableStateOf(s.category) }
    AlertDialog(onDismissRequest = onClose, containerColor = dlgBg,
        title = { Text(tr("Ayusin ang ${s.code}", "Edit ${s.code}"), color = vk.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(tr("Title", "Title"), title, { title = it })
                Field(tr("Artist", "Artist"), artist, { artist = it })
                CategoryPicker(cat) { cat = it }
            }
        },
        confirmButton = { TextButton({
            scope.launch {
                try { Admin.updateSong(s.id, title, artist, cat); toast(ctx, tr("Na-save.", "Saved.")); onSaved() } catch (e: Exception) { toast(ctx, e.message ?: "Error") }
            }
        }, enabled = title.isNotBlank()) { Text(tr("I-save", "Save")) } },
        dismissButton = { TextButton(onClose) { Text(tr("Huwag", "Cancel")) } })
}

// ---------- upload mula sa phone ----------
@Composable
private fun UploadTab(onDone: () -> Unit) {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var media by remember { mutableStateOf<PickedFile?>(null) }
    var lrc by remember { mutableStateOf<PickedFile?>(null) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>("OPM") }
    var rights by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var prog by remember { mutableFloatStateOf(0f) }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val f = pickedFile(ctx, uri); media = f
        // "Artist - Title.mp3" → hatiin
        val base = f.name.substringBeforeLast('.').replace('_', ' ').trim()
        val parts = base.split(" - ", limit = 2)
        if (parts.size == 2) { artist = parts[0].trim(); title = parts[1].trim() } else { title = base }
    }
    val pickLrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) lrc = pickedFile(ctx, uri) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Kanta (MP3, M4A, MP4, WEBM, MIDI/KAR; hanggang 50 MB)", "Song (MP3, M4A, MP4, WEBM, MIDI/KAR; up to 50 MB)"), color = c.text, fontWeight = FontWeight.Bold)
                VkButton(media?.name ?: tr("Pumili ng file", "Choose a file"), icon = "music", modifier = Modifier.fillMaxWidth(), onClick = { pickMedia.launch(arrayOf("audio/*", "video/*", "application/x-midi", "application/octet-stream")) })
                media?.let { Text("%.1f MB".format(it.size / 1048576f), color = if (it.size > 50L * 1048576) c.danger else c.muted, fontSize = 13.sp) }
                Text(tr("Lyrics (.lrc, opsyonal; para sa audio)", "Lyrics (.lrc, optional; for audio)"), color = c.text, fontWeight = FontWeight.Bold)
                VkButton(lrc?.name ?: tr("Pumili ng .lrc", "Choose a .lrc"), icon = "list", modifier = Modifier.fillMaxWidth(), onClick = { pickLrc.launch(arrayOf("*/*")) })
            }
        }
        Field(tr("Title", "Title"), title, { title = it })
        Field(tr("Artist", "Artist"), artist, { artist = it })
        CategoryPicker(cat) { cat = it }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { rights = !rights }) {
            Checkbox(rights, { rights = it }, colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.accentInk))
            Text(tr("May karapatan akong gamitin ang kantang ito (sarili kong gawa, may lisensya, o public domain).",
                "I have the right to use this song (my own work, licensed, or public domain)."), color = c.text, fontSize = 14.sp)
        }
        Text(tr("Isusulat sa kanta: \"Na-upload ng admin. Siguraduhing may karapatan sa kantang ito.\"",
            "The song will be marked: \"Uploaded by admin. Make sure you have the rights to this song.\""), color = c.muted, fontSize = 12.sp)
        if (busy) LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth(), color = c.accent)
        VkButton(if (busy) tr("Ina-upload... ${(prog * 100).toInt()}%", "Uploading... ${(prog * 100).toInt()}%") else tr("I-upload", "Upload"), primary = true,
            enabled = !busy && media != null && title.isNotBlank() && rights, modifier = Modifier.fillMaxWidth(), onClick = {
                val m = media ?: return@VkButton
                busy = true; prog = 0f
                scope.launch {
                    try {
                        val code = Admin.uploadSong(ctx, m, lrc, title, artist, cat) { prog = it }
                        toast(ctx, tr("Na-upload! Numero: $code", "Uploaded! Number: $code"))
                        media = null; lrc = null; title = ""; artist = ""; rights = false
                        onDone()
                    } catch (e: Exception) { toast(ctx, tr("Hindi na-upload: ", "Upload failed: ") + e.message) }
                    busy = false
                }
            })
    }
}

// ---------- YouTube ----------
@Composable
private fun YouTubeAddTab(onDone: () -> Unit) {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    fun search() {
        val id = parseYouTubeId(q)
        if (id != null) { pick = Triple(id, "", "YouTube"); return }
        if (q.isBlank()) return
        msg = tr("Hinahanap...", "Searching...")
        scope.launch {
            try {
                val j = Supa.ytSearch(q)
                val a = j.optJSONArray("items") ?: JSONArray()
                results = (0 until a.length()).map { val o = a.getJSONObject(it); Triple(o.optString("id"), o.optString("title"), o.optString("channel")) }
                msg = when {
                    j.optString("error").isNotEmpty() -> tr("Hindi gumana ang YouTube search. Pwedeng mag-paste ng link.", "YouTube search failed. You can paste a link.")
                    results.isEmpty() -> tr("Walang nahanap.", "Nothing found.")
                    else -> ""
                }
            } catch (e: Exception) { msg = tr("Walang internet o pumalya ang search.", "No internet or search failed.") }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(q, { q = it }, tr("Hanapin o i-paste ang link", "Search or paste a link"), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            VkButton(tr("Hanap", "Search"), primary = true, onClick = { search() }, height = 50.dp)
        }
        Text(tr("Naka-embed lang ang YouTube; hindi dina-download. Ang karapatan ay nasa YouTube at sa nag-upload.",
            "YouTube is embedded only, never downloaded. Rights stay with YouTube and the uploader."), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp, 8.dp))
        if (msg.isNotEmpty()) Text(msg, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.first }) { r ->
                Row(Modifier.fillMaxWidth().clickable { pick = r }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    VkIcon("yt", 22.dp, c.accent); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.second, color = c.text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(r.third, color = c.muted, fontSize = 13.sp, maxLines = 1)
                    }
                    VkIcon("plus", 20.dp, c.text)
                }
            }
        }
    }
    pick?.let { r ->
        var title by remember(r) { mutableStateOf(r.second) }
        var artist by remember(r) { mutableStateOf("") }
        var cat by remember(r) { mutableStateOf<String?>("OPM") }
        AlertDialog(onDismissRequest = { pick = null }, containerColor = dlgBg,
            title = { Text(tr("Idagdag sa songbook", "Add to songbook"), color = vk.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(tr("Title", "Title"), title, { title = it })
                    Field(tr("Artist", "Artist"), artist, { artist = it })
                    CategoryPicker(cat) { cat = it }
                    Text(licenseYouTube(r.third), color = vk.muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton({
                scope.launch {
                    try { val code = Admin.addYouTube(r.first, title, artist, cat, r.third); toast(ctx, tr("Naidagdag! Numero: $code", "Added! Number: $code")); pick = null; onDone() }
                    catch (e: Exception) { toast(ctx, e.message ?: "Error") }
                }
            }, enabled = title.isNotBlank()) { Text(tr("Idagdag", "Add")) } },
            dismissButton = { TextButton({ pick = null }) { Text(tr("Huwag", "Cancel")) } })
    }
}

// ---------- maliliit na piraso ----------
@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text) {
    val c = vk
    Column {
        Text(label, color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(14.dp))
    }
}

@Composable
private fun CategoryPicker(value: String?, onPick: (String?) -> Unit) {
    val c = vk
    Column {
        Text(tr("Kategorya", "Category"), color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.forEach { k ->
                val on = k == value
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) c.accent else c.surface).border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(50))
                    .clickable { onPick(if (on) null else k) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(k, color = if (on) c.accentInk else c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun toast(ctx: android.content.Context, m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

private val dlgBg: androidx.compose.ui.graphics.Color
    get() = if (com.videoketana.app.data.Prefs.dark) DarkVk.surface2 else LightVk.surface2

@Suppress("unused") private val keep = Modifier.size(1.dp)
