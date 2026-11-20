package com.videoketana.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videoketana.app.data.Prefs
import com.videoketana.app.data.tr

private val V_ACC = Color(0xFF62E3D2)
private val V_ACC2 = Color(0xFF7CC4FF)

/** Paghahanap gamit ang boses: pindutin ang 🎤, magsalita (title o artist), at ito ang ita-type. */
class VoiceSearchState {
    var open by mutableStateOf(false)
    var partial by mutableStateOf("")
    var level by mutableFloatStateOf(0f)      // 0..1, lakas ng boses
    var error by mutableStateOf("")
    internal var start: () -> Unit = {}
    internal var stop: () -> Unit = {}
    fun listen() = start()
}

@Composable
fun rememberVoiceSearch(onText: (String) -> Unit): VoiceSearchState {
    val ctx = LocalContext.current
    val st = remember { VoiceSearchState() }
    var rec by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, tr("Sabihin ang title o artist", "Say the title or artist"))
        // Tagalog app: fil-PH (kaya rin ang English na title); English app: wika ng device
        if (Prefs.lang != "en") putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fil-PH")
    }

    // kapag walang SpeechRecognizer sa device: ang sariling screen ng Google
    val sysVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onText(it.trim()) }
    }

    fun begin() {
        st.partial = ""; st.error = ""; st.level = 0f; st.open = true
        Haptic.tap()
        val r = try { SpeechRecognizer.createSpeechRecognizer(ctx) } catch (e: Exception) { null }
        if (r == null) { st.open = false; try { sysVoice.launch(intent()) } catch (e: Exception) { } ; return }
        rec?.destroy(); rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { st.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { st.level = 0f }
            override fun onError(error: Int) {
                st.level = 0f
                st.error = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tr("Hindi narinig. Subukan ulit.", "Didn't catch that. Try again.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> tr("Kailangan ng internet para sa boses.", "Voice search needs internet.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> tr("Payagan ang mic para sa VideokeTana.", "Allow the mic for VideokeTana.")
                    else -> tr("Hindi gumana ang boses dito (code $error).", "Voice search failed (code $error).")
                }
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (t.isNotEmpty()) { onText(t); Haptic.found(); st.open = false } else st.error = tr("Hindi narinig. Subukan ulit.", "Didn't catch that. Try again.")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) st.partial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try { r.startListening(intent()) } catch (e: Exception) { st.error = e.message ?: "?" }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else { st.open = true; st.error = tr("Payagan ang mic para makahanap gamit ang boses.", "Allow the mic to search by voice.") }
    }
    st.start = {
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    st.stop = { try { rec?.cancel() } catch (e: Exception) { }; st.open = false; st.level = 0f }
    DisposableEffect(Unit) { onDispose { try { rec?.destroy() } catch (e: Exception) { } } }

    if (st.open) VoiceOverlay(st)
    return st
}

/** Bilog na 🎤 na button (may maliit na gradient). */
@Composable
fun MicSearchButton(st: VoiceSearchState, size: androidx.compose.ui.unit.Dp = 38.dp) {
    val src = remember { MutableInteractionSource() }
    var f by remember { mutableStateOf(false) }
    Box(Modifier.size(size).clip(RoundedCornerShape(50))
        .background(Brush.linearGradient(listOf(V_ACC, V_ACC2)))
        .then(if (f) Modifier.border(3.dp, Color.White, RoundedCornerShape(50)) else Modifier)
        .onFocusChanged { f = it.isFocused }
        .clickable(src, null) { st.listen() }, contentAlignment = Alignment.Center) {
        VkIcon("mic", size * 0.5f, Color(0xFF062320))
    }
}

@Composable
private fun VoiceOverlay(st: VoiceSearchState) {
    Dialog(onDismissRequest = { st.stop() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val inf = rememberInfiniteTransition(label = "v")
        val wave by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "w")
        val lvl by animateFloatAsState(st.level, tween(120), label = "l")
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xF2070A0E), Color(0xF20C1418))))
            .clickable(remember { MutableInteractionSource() }, null) { st.stop() }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                Text(if (st.error.isNotEmpty()) tr("Hmm…", "Hmm…") else tr("Nakikinig…", "Listening…"), color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(26.dp))
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    // mga alon na lumalaki ayon sa boses
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height / 2)
                        val base = size.minDimension * 0.24f
                        for (k in 0 until 3) {
                            val ph = (wave + k / 3f) % 1f
                            val r = base + (size.minDimension * 0.26f) * ph * (0.45f + lvl)
                            drawCircle(V_ACC.copy(alpha = (1f - ph) * 0.45f), r, c, style = Stroke(width = 3.dp.toPx()))
                        }
                        drawCircle(Brush.radialGradient(listOf(V_ACC.copy(alpha = 0.35f + lvl * 0.4f), Color.Transparent), c, base * (1.6f + lvl)), base * (1.6f + lvl), c)
                    }
                    Box(Modifier.size(104.dp).clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(V_ACC, V_ACC2)))
                        .clickable { if (st.error.isNotEmpty()) st.listen() }, contentAlignment = Alignment.Center) {
                        VkIcon("mic", 46.dp, Color(0xFF062320))
                    }
                }
                Spacer(Modifier.height(24.dp))
                // sound bars
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(34.dp)) {
                    for (i in 0 until 9) {
                        val wob = kotlin.math.abs(kotlin.math.sin((wave * 6.28f) + i * 0.8f))
                        val h = 6f + 28f * (lvl * (0.5f + 0.5f * wob))
                        Box(Modifier.width(5.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(V_ACC.copy(alpha = 0.85f)))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(when {
                    st.error.isNotEmpty() -> st.error
                    st.partial.isNotEmpty() -> "“${st.partial}”"
                    else -> tr("Sabihin ang title o artist ng kanta", "Say the song title or artist")
                }, color = if (st.error.isNotEmpty()) Color(0xFFFF9A9A) else Color.White, fontSize = if (st.partial.isNotEmpty() && st.error.isEmpty()) 24.sp else 17.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (st.error.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(tr("Pindutin ang 🎤 para subukan ulit", "Tap 🎤 to try again"), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        }
    }
}
