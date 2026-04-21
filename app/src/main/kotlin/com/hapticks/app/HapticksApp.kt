package com.hapticks.app

import android.app.Application
import com.hapticks.app.data.HapticsPreferences
import com.hapticks.app.haptics.HapticEngine

/**
 * Application class acts as a tiny service locator so the accessibility service, view model,
 * and any future surface can share a single [HapticsPreferences] and [HapticEngine] instance
 * without pulling in a DI framework.
 */
class HapticksApp : Application() {

    lateinit var preferences: HapticsPreferences
        private set

    lateinit var hapticEngine: HapticEngine
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = HapticsPreferences(this)
        hapticEngine = HapticEngine(this)
    }
}
