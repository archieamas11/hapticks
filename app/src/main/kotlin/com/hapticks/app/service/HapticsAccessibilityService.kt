package com.hapticks.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HapticsAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var settingsJob: Job? = null

    @Volatile
    private var current: HapticsSettings = HapticsSettings.Default

    @Volatile private var tapEnabled: Boolean = current.tapEnabled

    private lateinit var engine: HapticEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as HapticksApp
        engine = app.hapticEngine

        settingsJob = app.preferences.settings
            .distinctUntilChanged()
            .onEach { snapshot ->
                current = snapshot
                tapEnabled = snapshot.tapEnabled
                applyEventMask(snapshot)
            }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return

        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (!tapEnabled) return
            val s = current
            engine.play(s.pattern, s.intensity)
            return
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
        val mask = if (settings.tapEnabled) AccessibilityEvent.TYPE_VIEW_CLICKED else 0
        if (info.eventTypes == mask) return
        info.eventTypes = mask
        serviceInfo = info
    }
}
