package com.hapticks.app.edge

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticPattern
import kotlinx.coroutines.flow.first

/**
 * In-app preview of the scroll-edge haptic (same waveform the accessibility service uses).
 */
object EdgeHapticsBridge {

    sealed class TestResult {
        object Fired : TestResult()
        object NoVibrator : TestResult()
    }

    fun testEdgeHaptic(context: Context): TestResult {
        val vibrator = resolveVibrator(context) ?: return TestResult.NoVibrator
        if (!vibrator.hasVibrator()) return TestResult.NoVibrator

        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        return try {
            val hapticksApp = context.applicationContext as HapticksApp
            val s = hapticksApp.preferences.settings
                .let { flow ->
                    try {
                        kotlinx.coroutines.runBlocking { flow.first() }
                    } catch (_: Throwable) {
                        HapticsSettings.Default
                    }
                }
            vibrator.vibrate(buildEdgeEffect(s.edgePattern, s.edgeIntensity), attrs)
            TestResult.Fired
        } catch (_: Throwable) {
            TestResult.NoVibrator
        }
    }

    /**
     * Builds the same [VibrationEffect] used for edge preview and for the LSPosed [EdgeEffect] hooks.
     */
    fun edgeVibrationEffect(pattern: HapticPattern, intensity: Float): VibrationEffect =
        buildEdgeEffect(pattern, intensity.coerceIn(0f, 1f))

    private fun resolveVibrator(context: Context): Vibrator? = try {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } catch (_: Throwable) {
        null
    }

    private fun buildEdgeEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        return try {
            val composition = VibrationEffect.startComposition()
            when (pattern) {
                HapticPattern.CLICK ->
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                HapticPattern.TICK ->
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)
                HapticPattern.HEAVY_CLICK -> {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                    composition.addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        (intensity * 0.6f).coerceIn(0f, 1f),
                        40,
                    )
                }
                HapticPattern.DOUBLE_CLICK -> {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity, 80)
                }
                HapticPattern.SOFT_BUMP ->
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, intensity)
                HapticPattern.DOUBLE_TICK -> {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity, 60)
                }
            }
            composition.compose()
        } catch (_: Throwable) {
            val effectId = when (pattern) {
                HapticPattern.CLICK -> VibrationEffect.EFFECT_CLICK
                HapticPattern.TICK -> VibrationEffect.EFFECT_TICK
                HapticPattern.HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
                HapticPattern.DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                HapticPattern.SOFT_BUMP -> VibrationEffect.EFFECT_TICK
                HapticPattern.DOUBLE_TICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
            }
            VibrationEffect.createPredefined(effectId)
        }
    }
}
