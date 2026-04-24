package com.hapticks.app.xposed

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.EdgeEffect
import com.hapticks.app.edge.EdgeHapticsBridge
import com.hapticks.app.haptics.HapticPattern
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.PROP_CAP_REMOTE
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import java.util.WeakHashMap

class EdgeEffectHapticsModule : XposedModule() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastHaptic = WeakHashMap<EdgeEffect, Long>()
    private val lastHapticLock = Any()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "onModuleLoaded process=${param.processName}")
        if ((getFrameworkProperties() and PROP_CAP_REMOTE) == 0L) {
            log(Log.WARN, TAG, "Framework lacks PROP_CAP_REMOTE; LSPosed remote prefs may be unavailable")
        }
        installEdgeHooks()
    }

    private fun installEdgeHooks() {
        try {
            val edge = EdgeEffect::class.java
            val onPull = edge.getDeclaredMethod(
                "onPull",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            )
            hook(onPull)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    chain.proceed()
                    val effect = chain.getThisObject() as? EdgeEffect
                    if (effect != null) afterEdgeInteraction(effect)
                    null
                }

            val onAbsorb = edge.getDeclaredMethod("onAbsorb", Int::class.javaPrimitiveType)
            hook(onAbsorb)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    chain.proceed()
                    val effect = chain.getThisObject() as? EdgeEffect
                    if (effect != null) afterEdgeInteraction(effect)
                    null
                }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "EdgeEffect hook install failed", t)
        }
    }

    private fun afterEdgeInteraction(effect: EdgeEffect) {
        val prefs = try {
            getRemotePreferences(XposedEdgeRemotePrefs.GROUP)
        } catch (t: Throwable) {
            log(Log.DEBUG, TAG, "getRemotePreferences failed: ${t.message}")
            return
        }
        if (!prefs.getBoolean(XposedEdgeRemotePrefs.KEY_ENABLED, false)) return

        val now = System.currentTimeMillis()
        synchronized(lastHapticLock) {
            val last = lastHaptic[effect] ?: 0L
            if (now - last <= COOLDOWN_MS) return
            lastHaptic[effect] = now
        }

        val pattern = HapticPattern.fromStorageKey(prefs.getString(XposedEdgeRemotePrefs.KEY_PATTERN, null))
        val intensity = prefs.getFloat(XposedEdgeRemotePrefs.KEY_INTENSITY, 1f).coerceIn(0f, 1f)
        val vibrationEffect = try {
            EdgeHapticsBridge.edgeVibrationEffect(pattern, intensity)
        } catch (t: Throwable) {
            log(Log.DEBUG, TAG, "edgeVibrationEffect: ${t.message}")
            return
        }

        val app = currentApplication() ?: return
        val vibrator = resolveVibrator(app) ?: return
        if (!vibrator.hasVibrator()) return

        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        mainHandler.post {
            try {
                vibrator.vibrate(vibrationEffect, attrs)
            } catch (t: Throwable) {
                log(Log.DEBUG, TAG, "vibrate failed: ${t.message}")
            }
        }
    }

    private fun resolveVibrator(app: Application): Vibrator? = try {
        val mgr = app.getSystemService(VibratorManager::class.java)
        mgr?.defaultVibrator
    } catch (_: Throwable) {
        null
    }

    private fun currentApplication(): Application? = try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Application
    } catch (_: Throwable) {
        null
    }

    private companion object {
        private const val TAG = "HapticksEdgeXposed"
        private const val COOLDOWN_MS = 400L
    }
}
