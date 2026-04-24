package com.hapticks.app.service

import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap

internal object A11yScrollBoundEdgeHaptics {

    private const val MAX_TRACKED_SURFACES = 128

    private val perSurface = ConcurrentHashMap<String, ScrollEdgeState>()

    fun onViewScrolled(event: AccessibilityEvent): Result {
        val my = event.maxScrollY
        val key = keyFor(event) ?: return Result.NoHaptic
        if (my <= 0) {
            perSurface.remove(key)
            return Result.NoHaptic
        }

        val y = event.scrollY.coerceIn(0, my)
        val snap = ScrollEdgeSnapshot(scrollY = y, maxScrollY = my)

        val old = perSurface[key] ?: ScrollEdgeState()
        val (newState, action) = advance(old, snap)
        perSurface[key] = newState
        evictIfNeeded()

        return when (action) {
            ScrollEdgeAction.REACHED_TOP, ScrollEdgeAction.REACHED_BOTTOM -> Result.PlayEdgeHaptic
            null -> Result.NoHaptic
        }
    }

    private fun keyFor(e: AccessibilityEvent): String? {
        val s = e.source
        if (s != null) {
            return try {
                val viewId = s.viewIdResourceName
                "w${e.windowId}\u001f${e.className}\u001f$viewId"
            } finally {
                s.recycle()
            }
        }
        return e.className?.toString()?.let { cn -> "w${e.windowId}\u001f$cn" }
    }

    private fun evictIfNeeded() {
        while (perSurface.size > MAX_TRACKED_SURFACES) {
            val it = perSurface.keys.iterator()
            if (it.hasNext()) perSurface.remove(it.next()) else return
        }
    }

    enum class Result { PlayEdgeHaptic, NoHaptic }
}

internal enum class ScrollEdgeAction { REACHED_TOP, REACHED_BOTTOM }
internal data class ScrollEdgeState(
    val lastScrollY: Int? = null,
)
internal data class ScrollEdgeSnapshot(
    val scrollY: Int,
    val maxScrollY: Int,
)

internal fun advance(
    state: ScrollEdgeState,
    snap: ScrollEdgeSnapshot,
): Pair<ScrollEdgeState, ScrollEdgeAction?> {
    val my = snap.maxScrollY
    if (my <= 0) {
        return ScrollEdgeState(lastScrollY = snap.scrollY.coerceAtLeast(0)) to null
    }
    val y = snap.scrollY.coerceIn(0, my)
    val last = state.lastScrollY

    val action = if (last != null) {
        when {
            y == 0 && last > 0 -> ScrollEdgeAction.REACHED_TOP
            y == my && last < my -> ScrollEdgeAction.REACHED_BOTTOM
            else -> null
        }
    } else {
        null
    }

    return ScrollEdgeState(lastScrollY = y) to action
}
