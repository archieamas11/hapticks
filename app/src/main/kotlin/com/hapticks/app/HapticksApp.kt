package com.hapticks.app

import android.app.Application
import com.hapticks.app.data.HapticsPreferences
import com.hapticks.app.haptics.HapticEngine

/**
 * Tiny service-locator for app-wide singletons shared between the accessibility service, the
 * view model, and the UI. Both singletons use lazy initialization so [Application.onCreate]
 * returns fast; the first call from either surface pays the binder probes.
 */
class HapticksApp : Application() {

    val preferences: HapticsPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HapticsPreferences(this)
    }

    val hapticEngine: HapticEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HapticEngine(this)
    }
}
