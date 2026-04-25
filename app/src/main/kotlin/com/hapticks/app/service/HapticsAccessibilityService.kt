package com.hapticks.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HapticsAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var settingsJob: Job? = null

    @Volatile
    private var current: HapticsSettings = HapticsSettings.Default

    private lateinit var engine: HapticEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as HapticksApp
        engine = app.hapticEngine

        applyEventMask(HapticsSettings.Default)

        settingsJob = app.preferences.settings
            .distinctUntilChanged()
            .onEach { snapshot ->
                current = snapshot
                applyEventMask(snapshot)
            }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return

        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (!current.tapEnabled) return
            engine.play(current.pattern, current.intensity)
            return
        }

        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            var consumedByEdge = false
            if (current.a11yScrollBoundEdge) {
                if (A11yScrollBoundEdgeHaptics.onViewScrolled(event) == A11yScrollBoundEdgeHaptics.Result.PlayEdgeHaptic) {
                    engine.play(current.edgePattern, current.edgeIntensity, throttleMs = EDGE_THROTTLE_MS)
                    consumedByEdge = true
                }
            }
            if (current.scrollEnabled && !consumedByEdge) {
                engine.play(current.scrollPattern, current.scrollIntensity, throttleMs = SCROLL_THROTTLE_MS)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        settingsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun applyEventMask(settings: HapticsSettings) {
        val info = serviceInfo ?: return
        var mask = 0
        if (settings.tapEnabled) mask = mask or AccessibilityEvent.TYPE_VIEW_CLICKED
        if (settings.scrollEnabled || settings.a11yScrollBoundEdge) mask = mask or AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (mask == 0) mask = AccessibilityEvent.TYPE_VIEW_CLICKED
        if (info.eventTypes == mask) return
        info.eventTypes = mask
        serviceInfo = info
    }

    private companion object {
        const val SCROLL_THROTTLE_MS = 42L
        const val EDGE_THROTTLE_MS = 200L
    }
}