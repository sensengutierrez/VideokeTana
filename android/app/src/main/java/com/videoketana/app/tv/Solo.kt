package com.videoketana.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

private val ACC = Color(0xFF62E3D2)
private val INK2 = Color(0xFF062320)
private val MUTE = Color(0xFF8C93A1)

/** Maliit na "#" sa gilid ng TV screen: bukas ang sariling remote kapag iisang device lang ang gamit. */
@Composable
fun SoloTab(dim: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 54.dp, height = 96.dp)
        .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
        .background(if (dim) Color(0x55101318) else Color(0xCC101318))
        .border(1.dp, ACC.copy(alpha = if (dim) 0.25f else 0.6f), RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
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
fun SoloPanel(local: List<LocalSong>, onClose: () -> Unit, onReserve: (Song?, LocalSong?, String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        val wide = maxWidth > maxHeight
        val panelMod = if (wide) Modifier.align(Alignment.CenterEnd).width(minOf(maxWidth * 0.46f, 460.dp)).fillMaxHeight()
        else Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight * 0.82f)
        Column(panelMod.clip(RoundedCornerShape(26.dp)).background(Brush.verticalGradient(listOf(Color(0xFA181C24), Color(0xFA0D1015))))
            .border(1.dp, ACC.copy(alpha = 0.3f), RoundedCornerShape(26.dp)).clickable(enabled = false) {}.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("#" to tr("Numero", "Number"), "⌕" to tr("Hanap", "Search")).forEachIndexed { i, (ic, label) ->
                    val on = tab == i
                    Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(if (on) ACC else Color.White.copy(alpha = 0.06f)).clickable { tab = i }
                        .padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text("$ic  $label", color = if (on) INK2 else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    VkIcon("x", 18.dp, Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (tab == 0) SoloKeypad(local, onReserve) else SoloSearch(local, onReserve)
        }
    }
}

@Composable
private fun SoloKeypad(local: List<LocalSong>, onReserve: (Song?, LocalSong?, String) -> Unit) {
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
                Text(when { code.length < 5 -> tr("I-type ang numero", "Type the number"); ok -> title!!; else -> tr("Walang kanta sa numerong ito", "No song with this number") },
                    color = when { ok -> ACC; code.length == 5 -> Color(0xFFFF7A7A); else -> MUTE }, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("clr", "0", "del")).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF232832), Color(0xFF161A21))))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .clickable {
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
                .clickable(enabled = ok) { onReserve(found, foundLocal, "reserve"); code = "" }, contentAlignment = Alignment.Center) {
                Text("Reserve", color = if (ok) INK2 else Color(0xFF5B6270), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
            Box(Modifier.width(96.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .clickable(enabled = ok) { onReserve(found, foundLocal, "now"); code = "" }, contentAlignment = Alignment.Center) {
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
        Box(Modifier.clip(RoundedCornerShape(50)).background(ACC).clickable { Haptic.reserved(); onReserve() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Reserve", color = INK2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
