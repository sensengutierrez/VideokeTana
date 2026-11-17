package com.videoketana.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.videoketana.app.Screen
import com.videoketana.app.data.Prefs
import com.videoketana.app.data.Song
import com.videoketana.app.data.Supa
import com.videoketana.app.data.Updater
import com.videoketana.app.data.cleanRoom
import com.videoketana.app.data.roomFromScan
import com.videoketana.app.data.tr
import com.videoketana.app.data.validRoom
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Launcher ng QR scanner (zxing). Ibinabalik ang room na nabasa. */
@Composable
fun rememberRoomScanner(onRoom: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()) { r ->
        val text = r.contents ?: return@rememberLauncherForActivityResult
        val room = roomFromScan(text)
        if (room != null) onRoom(room)
        else Toast.makeText(ctx, tr("Hindi ito ang QR ng VideokeTana. Itapat sa QR na nasa TV.", "This is not a VideokeTana QR code. Point at the QR on the TV."), Toast.LENGTH_LONG).show()
    }
    return {
        launcher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(tr("Itapat ang camera sa QR code na nasa TV", "Point the camera at the QR code on the TV"))
            .setBeepEnabled(false).setOrientationLocked(false))
    }
}

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val c = vk
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var shown by remember { mutableStateOf<Song?>(null) }
    var roomText by remember { mutableStateOf(Prefs.lastRoom) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var dlProgress by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(Unit) {
        try { songs = Supa.songs() } catch (e: Exception) { }
        update = Updater.check()?.takeIf { it.number != Prefs.skipUpdate }
    }
    // random na kanta sa display bawat 4 na segundo, parang demo ng videoke machine
    LaunchedEffect(songs) {
        while (songs.isNotEmpty()) { shown = songs.random(); delay(4000) }
    }
    val scan = rememberRoomScanner { r -> Prefs.lastRoom = r; go(Screen.Remote(r)) }
    fun join() {
        val r = cleanRoom(roomText)
        if (!validRoom(r)) { Toast.makeText(ctx, tr("Ilagay ang 4 na letra o numero na room code na nasa TV", "Enter the 4-character room code shown on the TV"), Toast.LENGTH_SHORT).show(); return }
        Prefs.lastRoom = r; go(Screen.Remote(r))
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // itaas
        Row(verticalAlignment = Alignment.CenterVertically) {
            VkIcon("mic", 22.dp, c.accent); Spacer(Modifier.width(8.dp))
            Text("VideokeTana", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
            Gap()
            VkIconButton("gear", { go(Screen.Settings) })
        }

        // update na available
        update?.let { u ->
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("May bagong bersyon ng app", "A new version of the app is available"), color = c.text, style = VkType.title)
                    Text(tr("Bersyon ${u.number}. Isang pindot lang para i-update; hindi mabubura ang settings mo.", "Version ${u.number}. One tap to update; your settings are kept."), color = c.muted, style = VkType.small)
                    Spacer(Modifier.height(10.dp))
                    if (dlProgress >= 0f) LinearProgressIndicator(progress = { dlProgress }, modifier = Modifier.fillMaxWidth(), color = c.accent)
                    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VkButton(tr("I-update", "Update"), primary = true, icon = "refresh", onClick = {
                            if (!Updater.canInstall(ctx)) {
                                Toast.makeText(ctx, tr("Payagan ang \"Install unknown apps\" para sa VideokeTana, tapos pindutin ulit ang I-update.", "Allow \"Install unknown apps\" for VideokeTana, then tap Update again."), Toast.LENGTH_LONG).show()
                                Updater.openInstallPermission(ctx); return@VkButton
                            }
                            scope.launch {
                                dlProgress = 0f
                                try { val f = Updater.download(ctx, u) { dlProgress = it }; Updater.install(ctx, f) }
                                catch (e: Exception) { Toast.makeText(ctx, tr("Hindi na-download: ", "Download failed: ") + e.message, Toast.LENGTH_LONG).show() }
                                dlProgress = -1f
                            }
                        })
                        VkButton(tr("Mamaya na", "Later"), onClick = { Prefs.skipUpdate = u.number; update = null })
                    }
                }
            }
        }

        // display na parang videoke machine
        Panel(Modifier.fillMaxWidth(), display = true) {
            Column(Modifier.padding(18.dp)) {
                Row { Text(tr("Song no.", "Song no."), color = c.displayMuted, fontSize = 13.sp); Gap()
                    Text(if (songs.isEmpty()) "" else tr("${songs.size} na kanta", "${songs.size} songs"), color = c.displayMuted, fontSize = 13.sp) }
                Spacer(Modifier.height(12.dp))
                DotText(shown?.code?.toString() ?: "88888", 70.dp, color = c.displayFg)
                Spacer(Modifier.height(12.dp))
                Text(shown?.let { it.title + if (it.artist.isNotBlank()) ", " + it.artist else "" } ?: tr("Pumili ng kanta, i-type ang numero, kumanta.", "Pick a song, type its number, sing."),
                    color = c.displayText, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (songs.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.displayLine))
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Bago sa library", "New in the library"), color = c.displayMuted, fontSize = 13.sp)
                    songs.sortedByDescending { it.createdAt ?: "" }.take(3).forEach { s ->
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DotText(s.code.toString(), 14.dp, ghost = false); Spacer(Modifier.width(12.dp))
                            Text(s.title, color = c.displayText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Buksan sa TV
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.accent).clickable { go(Screen.Tv) }.padding(20.dp)) {
            Column {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.accentInk.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { VkIcon("tv", 22.dp, c.accentInk) }
                Spacer(Modifier.height(12.dp))
                Text(tr("Buksan sa TV", "Open on TV"), color = c.accentInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(tr("Gawing TV screen ang device na ito: dito tutugtog ang kanta, may QR para sa mga phone.", "Make this device the TV screen: songs play here, with a QR code for phones."),
                    color = c.accentInk.copy(alpha = 0.75f), fontSize = 14.sp)
            }
        }

        // Sumali sa room
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2), contentAlignment = Alignment.Center) { VkIcon("phone", 22.dp) }
                Spacer(Modifier.height(12.dp))
                Text(tr("Sumali sa room", "Join a room"), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(tr("I-scan ang QR o i-type ang room code na nasa TV para gawing remote ang phone.", "Scan the QR or type the room code on the TV to use your phone as a remote."), color = c.muted, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        BasicTextField(
                            value = roomText,
                            onValueChange = { v -> roomText = cleanRoom(v).take(8) },
                            singleLine = true,
                            textStyle = TextStyle(color = c.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { join() }),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { if (roomText.isEmpty()) Text("ABCD", color = c.muted.copy(alpha = 0.4f), fontSize = 24.sp, letterSpacing = 6.sp); inner() } }
                        )
                    }
                    VkButton(tr("Sumali", "Join"), primary = true, onClick = { join() }, height = 52.dp)
                }
                Spacer(Modifier.height(10.dp))
                VkButton(tr("I-scan ang QR code", "Scan the QR code"), icon = "qr", onClick = scan, modifier = Modifier.fillMaxWidth())
            }
        }

        // iba pa
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("book", tr("Songbook", "Songbook"), tr("Lahat ng kanta", "All songs"), Modifier.weight(1f)) { go(Screen.Songbook) }
            Tile("gear", tr("Settings", "Settings"), tr("Wika, tema, tunog", "Language, theme, sound"), Modifier.weight(1f)) { go(Screen.Settings) }
        }
        Text("VideokeTana ${com.videoketana.app.BuildConfig.VERSION_NAME}", color = c.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Tile(icon: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val c = vk
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp)) {
        VkIcon(icon, 22.dp, c.accent)
        Spacer(Modifier.height(10.dp))
        Text(title, color = c.text, style = VkType.title)
        Text(sub, color = c.muted, style = VkType.small)
    }
}

@Suppress("unused") private val keepWidth = Modifier.widthIn(max = 640.dp)
