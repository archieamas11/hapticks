package com.hapticks.app.xposed

import android.app.Application
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.EdgeEffect
import com.hapticks.app.edge.EdgeHapticsBridge
import com.hapticks.app.haptics.HapticPattern
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import java.util.WeakHashMap

class EdgeEffectHapticsModule : XposedModule() {

    private val edgeStates = WeakHashMap<EdgeEffect, EdgeStretchState>()

    @Volatile private var vibrator: Vibrator? = null

    @Volatile private var enabled = false
    @Volatile private var cachedPattern: HapticPattern? = null
    @Volatile private var cachedIntensity = 1f

    private val touchAttrs: VibrationAttributes by lazy {
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "onModuleLoaded — process=${param.processName}")

        if ((getFrameworkProperties() and PROP_CAP_REMOTE) == 0L) {
            log(Log.WARN, TAG, "PROP_CAP_REMOTE missing — remote prefs unavailable in this process")
        }

        loadRemotePrefs()
        hookApplicationOnCreate()
        hookEdgeEffect()
    }

    private fun loadRemotePrefs() {
        val prefs = runCatching { getRemotePreferences(XposedEdgeRemotePrefs.GROUP) }
            .onFailure { log(Log.WARN, TAG, "Remote prefs unavailable: ${it.message}") }
            .getOrNull() ?: return

        applyPrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener { p, _ -> applyPrefs(p) }
    }

    private fun applyPrefs(prefs: android.content.SharedPreferences) {
        enabled         = prefs.getBoolean(XposedEdgeRemotePrefs.KEY_ENABLED, false)
        cachedPattern   = HapticPattern.fromStorageKey(prefs.getString(XposedEdgeRemotePrefs.KEY_PATTERN, null))
        cachedIntensity = prefs.getFloat(XposedEdgeRemotePrefs.KEY_INTENSITY, 1f).coerceIn(0f, 1f)
    }

    private fun hookApplicationOnCreate() {
        runCatching {
            hookAfter(Application::class.java, "onCreate") { app, _ ->
                if (vibrator != null) return@hookAfter
                vibrator = (app as Application)
                    .getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator
            }
        }.onFailure { log(Log.ERROR, TAG, "Failed to hook Application.onCreate", it) }
    }

    private fun hookEdgeEffect() {
        try {
            val cls = EdgeEffect::class.java

            hookAfter(
                cls, "onPull",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ) { effect, args ->
                val delta = args[0] as? Float ?: return@hookAfter
                handlePull(effect as EdgeEffect, delta)
            }

            hookAfter(
                cls, "onPullDistance",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ) { effect, args ->
                val delta = args[0] as? Float ?: return@hookAfter
                handlePull(effect as EdgeEffect, delta)
            }

            runCatching {
                hookAfter(cls, "onRelease") { effect, _ ->
                    handleRelease(effect as EdgeEffect)
                }
            }.onFailure {
                log(Log.INFO, TAG, "onRelease not found — release haptic unavailable: ${it.message}")
            }

            hookAfter(cls, "onAbsorb", Int::class.javaPrimitiveType) { effect, _ ->
                handleAbsorb(effect as EdgeEffect)
            }

            runCatching {
                hookAfter(cls, "finish") { effect, _ ->
                    resetState(effect as EdgeEffect)
                }
            }.onFailure {
                log(Log.DEBUG, TAG, "finish not found — cleanup via release/absorb only: ${it.message}")
            }

            runCatching {
                hookAfter(cls, "setDistance", Float::class.javaPrimitiveType) { effect, args ->
                    val distance = args[0] as? Float ?: return@hookAfter
                    handleSetDistance(effect as EdgeEffect, distance)
                }
            }.onFailure {
                log(Log.DEBUG, TAG, "setDistance not available — API < 31 or fork: ${it.message}")
            }

        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "EdgeEffect hook installation failed", t)
        }
    }

    private inline fun hookAfter(
        cls: Class<*>,
        name: String,
        vararg paramTypes: Class<*>?,
        crossinline block: (thisObj: Any, args: List<Any?>) -> Unit,
    ) {
        val method = cls.getDeclaredMethod(name, *paramTypes)
        hook(method)
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                runCatching { block(chain.thisObject, chain.args) }
                    .onFailure { log(Log.DEBUG, TAG, "hookAfter [$name] threw: ${it.message}") }
                result
            }
    }

    private fun handlePull(effect: EdgeEffect, deltaDistance: Float) {
        if (!enabled) return
        if (deltaDistance < 0f) return

        val distanceAfter = effect.distance
        val shouldMarkActive = deltaDistance > 0f || distanceAfter > EDGE_DISTANCE_EPSILON
        if (!shouldMarkActive) return
        updateStretchState(effect, distanceAfter)
    }

    private fun handleSetDistance(effect: EdgeEffect, distance: Float) {
        if (!enabled) return
        updateStretchState(effect, distance)
    }

    private fun handleRelease(effect: EdgeEffect) {
        if (!enabled) return
        val state = edgeStates.getOrPut(effect) { EdgeStretchState() }
        val wasStretched = state.stretchActive || state.wasStretchedSession || effect.distance > EDGE_DISTANCE_EPSILON
        if (wasStretched) {
            triggerHaptic(HapticEventType.RELEASE)
        }
        resetState(effect)
    }

    private fun handleAbsorb(effect: EdgeEffect) {
        if (!enabled) return
        triggerHaptic(HapticEventType.ABSORB)
        resetState(effect)
    }

    private fun updateStretchState(effect: EdgeEffect, distanceAfter: Float) {
        val state = edgeStates.getOrPut(effect) { EdgeStretchState() }
        val nowUptimeMs = SystemClock.uptimeMillis()
        val clampedDistance = distanceAfter.coerceAtLeast(0f)
        val stretchActive = clampedDistance > EDGE_DISTANCE_EPSILON

        if (!stretchActive) {
            state.stretchActive = false
            state.lastDistance = clampedDistance
            state.isRearmPending = true

            // Re-arm only after a tiny stable idle window to suppress fast oscillation double fire.
            if (state.edgeHitConsumed && (nowUptimeMs - state.lastEdgeHitUptimeMs) >= EDGE_HIT_COOLDOWN_MS) {
                state.edgeHitConsumed = false
                state.isRearmPending = false
            }
            return
        }

        if (!state.edgeHitConsumed && (nowUptimeMs - state.lastEdgeHitUptimeMs) >= EDGE_HIT_COOLDOWN_MS) {
            triggerHaptic(HapticEventType.PULL)
            state.edgeHitConsumed = true
            state.lastEdgeHitUptimeMs = nowUptimeMs
        }

        state.stretchActive = true
        state.wasStretchedSession = true
        state.isRearmPending = false
        state.lastDistance = clampedDistance
    }

    private fun resetState(effect: EdgeEffect) {
        edgeStates[effect] = EdgeStretchState()
    }

    private enum class HapticEventType { PULL, RELEASE, ABSORB }

    private fun triggerHaptic(type: HapticEventType) {
        val pattern   = cachedPattern   ?: return
        val intensity = cachedIntensity
        val v         = vibrator        ?: return
        if (!v.hasVibrator()) return

        val effect = runCatching {
            EdgeHapticsBridge.edgeVibrationEffect(
                pattern = pattern,
                intensity = intensity,
                eventType = when (type) {
                    HapticEventType.PULL -> EdgeHapticsBridge.EdgeVibrationEvent.EDGE_HIT
                    HapticEventType.RELEASE -> EdgeHapticsBridge.EdgeVibrationEvent.RELEASE
                    HapticEventType.ABSORB -> EdgeHapticsBridge.EdgeVibrationEvent.ABSORB
                },
            )
        }.onFailure {
            log(Log.DEBUG, TAG, "VibrationEffect build failed [$type]: ${it.message}")
        }.getOrNull() ?: return

        runCatching {
            v.vibrate(effect, touchAttrs)
        }.onFailure {
            log(Log.DEBUG, TAG, "vibrate() failed [$type]: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "HapticksEdgeXposed"
        private const val EDGE_DISTANCE_EPSILON = 0.001f
        private const val EDGE_HIT_COOLDOWN_MS = 40L
    }

    private data class EdgeStretchState(
        var lastDistance: Float = 0f,
        var stretchActive: Boolean = false,
        var edgeHitConsumed: Boolean = false,
        var wasStretchedSession: Boolean = false,
        var lastEdgeHitUptimeMs: Long = Long.MIN_VALUE / 4L,
        var isRearmPending: Boolean = false,
    )
}