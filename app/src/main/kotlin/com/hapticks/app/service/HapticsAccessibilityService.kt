package com.hapticks.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

/**
 * System-wide haptic bridge.
 *
 * Listens for accessibility events from every installed app and, when the user has opted in,
 * fires a vibration via [HapticEngine]. Content is never inspected; only the event type is read.
 *
 * The service owns a tiny coroutine scope whose sole job is to keep [current] in sync with the
 * user's settings so [onAccessibilityEvent] can stay non-suspending and allocation-free.
 */
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
        settingsJob = app.preferences.settings
            .onEach { current = it }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return
        val settings = current

        when (evt.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (!settings.tapEnabled) return
                engine.play(settings.pattern, settings.intensity)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!settings.scrollEnabled) return
                engine.playScrollTick(settings.intensity)
            }
            // Other subscribed event types (selected/focused/touch start) are intentionally
            // ignored for v1 but are still declared in the service config so we can enable them
            // later without re-prompting the user to re-grant the accessibility permission.
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        settingsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
