package com.hapticks.app.edge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.hapticks.app.HapticksApp
import kotlinx.coroutines.runBlocking

/**
 * Runs inside the Hapticks process (which always holds [android.Manifest.permission.VIBRATE]).
 * Receives the fallback broadcast that [EdgeVibrator] sends from hooked processes that
 * lack VIBRATE and fires the distinct edge haptic locally.
 *
 * The receiver is kept deliberately minimal: it does not touch [com.hapticks.app.haptics.HapticEngine]
 * because that engine is tuned to the tap patterns the user picked in Feel Every Tap.
 * The edge pattern lives in [EdgeVibrator.edgeEffect] so the in-hook path and the
 * fallback path fire an identical `VibrationEffect`.
 */
class EdgeHapticReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EdgeVibrator.ACTION_EDGE_HAPTIC) return

        val (enabled, pattern, intensity) = try {
            val app = context.applicationContext as? HapticksApp
            app?.let { runBlocking { it.preferences.getEdgeSettingsOnce() } }
                ?: Triple(false, com.hapticks.app.haptics.HapticPattern.Default, 1.0f)
        } catch (t: Throwable) {
            Log.w(TAG, "edge pref check failed", t)
            Triple(false, com.hapticks.app.haptics.HapticPattern.Default, 1.0f)
        }
        if (!enabled) return

        val vibrator = resolveVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val intentPatternName = intent.getStringExtra(EdgeVibrator.EXTRA_PATTERN)
        val finalPattern = intentPatternName?.let {
            com.hapticks.app.haptics.HapticPattern.fromStorageKey(it)
        } ?: pattern

        val finalIntensity = intent.getFloatExtra(EdgeVibrator.KEY_EDGE_INTENSITY, intensity)

        val effect = EdgeVibrator.edgeEffect(finalPattern, finalIntensity)
        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        try {
            vibrator.vibrate(effect, attrs)
        } catch (t: Throwable) {
            Log.w(TAG, "vibrate failed in receiver", t)
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? = try {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } catch (t: Throwable) {
        null
    }

    private companion object {
        const val TAG = "HapticksEdgeRx"
    }
}
