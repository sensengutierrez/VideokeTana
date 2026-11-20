package com.videoketana.app.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import com.videoketana.app.data.Prefs
import java.util.Locale

/** Boses na nagbibilang kapag nagta-type ng numero (Text-to-Speech ng device). */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val FIL = listOf("isa", "dalawa", "tatlo", "apat", "lima", "anim", "pito", "walo", "siyam")

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { st ->
            ready = st == TextToSpeech.SUCCESS
            if (ready) setLang()
        }
    }

    private var filOk = false
    private fun setLang() {
        val t = tts ?: return
        filOk = false
        if (Prefs.lang == "fil") {
            for (l in listOf(Locale("fil", "PH"), Locale("fil"), Locale("tl", "PH"))) {
                val r = t.setLanguage(l)
                if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) { filOk = true; break }
            }
        }
        if (!filOk) t.language = Locale.US
        t.setSpeechRate(1.25f)
    }

    /** Sabihin ang numero ("0".."9"), o "clear"/"delete". */
    fun key(k: String) {
        if (!Prefs.numVoice) return
        val t = tts ?: return
        if (!ready) return
        setLang()
        val word = when (k) {
            "clr" -> if (filOk) "bura lahat" else "clear"
            "del" -> if (filOk) "bura" else "delete"
            "0" -> if (filOk) "zero" else "zero"
            else -> { val n = k.toIntOrNull() ?: return; if (filOk) FIL[n - 1] else n.toString() }
        }
        t.speak(word, TextToSpeech.QUEUE_FLUSH, null, "k")
    }

    fun say(text: String) { if (Prefs.numVoice && ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "s") }
}
