package com.hapticks.app.service.accessibility.handlers

import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.core.haptics.HapticPattern
import com.hapticks.app.data.model.AppSettings

object ViewInteractedHapticHandler {

    private const val TOGGLE_COALESCE_THROTTLE_MS = 120L
    private const val TOGGLE_HAPTIC_DEBOUNCE_MS = 250L

    private val TOGGLE_CONTENT_CHANGE_MASK: Int
        get() = if (Build.VERSION.SDK_INT >= 36) {
            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION or
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_CHECKED
        } else {
            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
        }

    @Volatile
    private var lastToggleHapticMs = 0L

    @JvmStatic
    fun eventTypeMask(settings: AppSettings): Int {
        return if (settings.tapEnabled) {
            AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        } else 0
    }

    @JvmStatic
    fun hasToggleLikeContentChange(event: AccessibilityEvent): Boolean {
        val types = event.contentChangeTypes
        return types != 0 && (types and TOGGLE_CONTENT_CHANGE_MASK) != 0
    }

    @JvmStatic
    fun handle(engine: HapticEngine, settings: AppSettings, event: AccessibilityEvent): Boolean {
        if (!settings.tapEnabled) return true

        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleClickEvent(engine, settings, event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContentChangedEvent(
                engine,
                settings,
                event
            )

            else -> false
        }
    }

    private fun handleClickEvent(
        engine: HapticEngine,
        settings: AppSettings,
        event: AccessibilityEvent
    ): Boolean {
        val node = event.source
        val suppressStandardPattern = if (node != null) {
            try {
                node.isCheckable || node.containsToggleDescendant(maxDepth = 2)
            } finally {
                node.recycle()
            }
        } else {
            true
        }

        if (!suppressStandardPattern) {
            engine.play(settings.pattern, settings.intensity)
        }
        return true
    }

    private fun handleWindowContentChangedEvent(
        engine: HapticEngine,
        settings: AppSettings,
        event: AccessibilityEvent
    ): Boolean {
        val changeTypes = event.contentChangeTypes
        if (changeTypes == 0) return true
        if (changeTypes and TOGGLE_CONTENT_CHANGE_MASK == 0) return true
        if (!isSwitchLikeToggleForWindowEvent(event, changeTypes)) return true

        val now = SystemClock.uptimeMillis()
        if (now - lastToggleHapticMs < TOGGLE_HAPTIC_DEBOUNCE_MS) {
            return true
        }
        lastToggleHapticMs = now

        engine.play(
            HapticPattern.DOUBLE_CLICK,
            settings.intensity,
            throttleMs = TOGGLE_COALESCE_THROTTLE_MS,
        )
        return true
    }

    private fun isSwitchLikeToggleForWindowEvent(
        event: AccessibilityEvent,
        changeTypes: Int
    ): Boolean {
        val hasChecked = if (Build.VERSION.SDK_INT >= 36) {
            (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CHECKED) != 0
        } else false
        val hasStateDescription =
            (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) != 0
        if (!hasChecked && !hasStateDescription) return false

        val node = event.source ?: return false
        return try {
            when {
                !node.isCheckable -> false
                node.isRatingBar() || node.isChip() -> false
                node.isSwitchRole() || node.hasToggleActions() -> true
                hasChecked && !node.isCheckBoxRole() && !node.isRadioRole() -> true
                else -> false
            }
        } finally {
            node.recycle()
        }
    }

    private fun AccessibilityNodeInfo.containsToggleDescendant(maxDepth: Int): Boolean {
        if (maxDepth <= 0) return false
        return try {
            val childCount = this.childCount.coerceAtMost(12)
            for (i in 0 until childCount) {
                val child = this.getChild(i) ?: continue
                val childIsToggle = try {
                    child.isCheckable ||
                            child.isSwitchRole() ||
                            child.isCheckBoxRole() ||
                            child.isRadioRole() ||
                            child.containsToggleDescendant(maxDepth - 1)
                } finally {
                    child.recycle()
                }
                if (childIsToggle) return true
            }
            false
        } catch (e: Exception) {
            true
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isSwitchRole(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        return role != null && (
                role.equals("switch", ignoreCase = true) ||
                        role.equals("toggle", ignoreCase = true)
                )
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isCheckBoxRole(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        return role != null && role.equals("checkbox", ignoreCase = true)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isRadioRole(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        return role != null && (
                role.equals("radio button", ignoreCase = true) ||
                        role.equals("radio", ignoreCase = true)
                )
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isRatingBar(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        if (role != null && role.contains("rating", ignoreCase = true)) return true
        return this.rangeInfo != null
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isChip(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        return role != null && role.contains("chip", ignoreCase = true)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.hasToggleActions(): Boolean {
        val compat = AccessibilityNodeInfoCompat.wrap(this)
        for (action in compat.actionList) {
            val id = action.id
            if (id == AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK.id ||
                id == AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SELECT.id
            ) {
                return true
            }
        }
        return false
    }
}