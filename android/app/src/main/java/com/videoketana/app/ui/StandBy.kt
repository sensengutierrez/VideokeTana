package com.videoketana.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoketana.app.data.Prefs
import com.videoketana.app.data.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mga kulay ng StandBy (hindi lang pula gaya ng iPhone). */
val STANDBY_COLORS = listOf(
    0xFF62E3D2 to "Teal", 0xFFFFB547 to "Amber", 0xFFFF6B6B to "Pula", 0xFFFF7AB6 to "Pink",
    0xFFB18CFF to "Lila", 0xFF6AA8FF to "Asul", 0xFF7BE08A to "Berde", 0xFFF2F2F2 to "Puti"
)

/**
 * StandBy ng phone: madilim na screen na may malaking orasan (LCD na tuldok-tuldok), ang kinakanta at ang susunod.
 * Lumalabas kapag ilang segundong hindi ginagalaw ang phone. Pinapadilim din ang liwanag ng screen. Pindutin para bumalik.
 */
@Composable
fun StandBy(nowTitle: String, nowSinger: String, nextTitle: String, room: String, onWake: () -> Unit) {
    val color = Color(Prefs.standbyColor)
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }

    // dim na liwanag habang naka-StandBy
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        val before = w?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        w?.let { val a = it.attributes; a.screenBrightness = 0.08f; it.attributes = a }
        onDispose { w?.let { val a = it.attributes; a.screenBrightness = before; it.attributes = a } }
    }
    // dahan-dahang gumagalaw para hindi masunog ang screen
    val drift = rememberInfiniteTransition(label = "drift")
    val dx by drift.animateFloat(-8f, 8f, infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Reverse), label = "dx")

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onWake)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val mw = maxWidth; val mh = maxHeight
        val wide = mw > mh
        val time = SimpleDateFormat("h:mm", Locale.US).format(now)
        // laki ng orasan: kasya sa lapad (bawat letra = 6 na tuldok ang lapad, 7 ang taas)
        val fit = (if (wide) mw * 0.6f else mw * 0.95f) * (7f / (time.length * 6 - 1))
        val clockH = minOf(if (wide) mh * 0.5f else mh * 0.25f, fit)
        val date = SimpleDateFormat("EEE, MMM d", if (Prefs.lang == "en") Locale.US else Locale("fil", "PH")).format(now)
        val info: @Composable () -> Unit = {
            Column {
                Text(date, color = color.copy(alpha = 0.75f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                if (nowTitle.isNotBlank()) {
                    Text(tr("Kinakanta", "Singing"), color = color.copy(alpha = 0.5f), fontSize = 14.sp)
                    Text(nowTitle, color = color.copy(alpha = 0.85f), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (nowSinger.isNotBlank()) Text(nowSinger, color = color.copy(alpha = 0.6f), fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                }
                if (nextTitle.isNotBlank()) {
                    Text(tr("Susunod", "Next"), color = color.copy(alpha = 0.5f), fontSize = 14.sp)
                    Text(nextTitle, color = color.copy(alpha = 0.75f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Room ", color = color.copy(alpha = 0.45f), fontSize = 14.sp)
                    DotText(room, 14.dp, color.copy(alpha = 0.6f), ghost = false, glow = false)
                }
            }
        }
        if (wide) Row(Modifier.offset(x = dx.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            DotText(time, clockH, color.copy(alpha = 0.9f), ghost = true)
            Spacer(Modifier.width(36.dp))
            Box(Modifier.width(mw * 0.3f)) { info() }
        } else Column(Modifier.offset(x = dx.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DotText(time, clockH, color.copy(alpha = 0.9f), ghost = true)
            Spacer(Modifier.height(30.dp))
            info()
        }
        Text(tr("Pindutin para bumalik", "Tap to go back"), color = color.copy(alpha = 0.3f), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
