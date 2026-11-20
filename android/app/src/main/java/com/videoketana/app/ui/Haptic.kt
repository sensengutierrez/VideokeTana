package com.videoketana.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.videoketana.app.data.Prefs

/** Vibrate sa phone: maikli sa bawat numero, mas mahaba kapag nahanap o na-reserve ang kanta. */
object Haptic {
    private var vib: Vibrator? = null
    fun init(ctx: Context) {
        vib = try {
            if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        } catch (e: Exception) { null }
    }
    private fun buzz(ms: Long, amp: Int) {
        if (!Prefs.vibrate) return
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, if (v.hasAmplitudeControl()) amp else VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (e: Exception) { }
    }
    fun tap() = buzz(18, 120)
    fun found() = buzz(40, 200)
    fun reserved() {
        if (!Prefs.vibrate) return
        val v = vib ?: return
        try { if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 60, 55), intArrayOf(0, 220, 0, 255), -1)) else buzz(80, 255) } catch (e: Exception) { }
    }
}
