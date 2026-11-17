package com.videoketana.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoketana.app.data.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Intro: umiikot na song number, lalapag sa 10001, lalabas ang VIDEOKETANA, may tunog na pang-sine. */
@Composable
fun Intro(onDone: () -> Unit) {
    var number by remember { mutableStateOf("88888") }
    var letters by remember { mutableIntStateOf(0) }
    var landed by remember { mutableStateOf(false) }
    val fade = remember { Animatable(1f) }
    val zoom = remember { Animatable(1f) }
    var finished by remember { mutableStateOf(false) }

    suspend fun finish() {
        if (finished) return
        finished = true
        kotlinx.coroutines.coroutineScope {
            launch { zoom.animateTo(1.05f, tween(500)) }
            fade.animateTo(0f, tween(500))
        }
        onDone()
    }

    LaunchedEffect(Unit) {
        Synth.prepare("intro")
        Synth.play("intro", 1f)
        val t0 = System.currentTimeMillis()
        while (!finished) {
            val t = System.currentTimeMillis() - t0
            if (t < 900) number = (10000 + Random.nextInt(89999)).toString()
            else if (!landed) { number = "10001"; landed = true }
            letters = ((t - 900) / 70).toInt().coerceIn(0, 11)
            if (t > 3000) break
            delay(16)
        }
        finish()
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().alpha(fade.value).scale(zoom.value).background(Color(0xFF0C0E11))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { scope.launch { finish() } },
        contentAlignment = Alignment.Center
    ) {
        // pinong tuldok sa likod, parang LCD
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            var y = step / 2
            while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(Color(0x1262E3D2), 1.2f, Offset(x, y)); x += step }; y += step }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DotText(number, 64.dp, Color(0xFF62E3D2).copy(alpha = if (landed) 0.25f else 0.5f), ghost = false, glow = false)
            Spacer(Modifier.height(18.dp))
            DotText("VIDEOKETANA".take(letters).padEnd(11, ' '), 30.dp, Color(0xFFECEEF1), ghost = false)
            Spacer(Modifier.height(18.dp))
            Text(
                tr("Pumili ng kanta, i-type ang numero, kumanta.", "Pick a song, type its number, sing."),
                color = Color(0xFF949BAA), fontSize = 15.sp, modifier = Modifier.alpha(if (landed) 1f else 0f)
            )
        }
    }
}
