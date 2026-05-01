package com.hapticks.app.service.accessibility.handlers

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

internal object ScrollEdgeHapticHandler {
    private const val MAX_TRACKED_SURFACES = 128

    private data class EdgeState(
        val atTop: Boolean = false,
        val atBottom: Boolean = false
    )

    private val perSurface = object : LinkedHashMap<String, EdgeState>(
        16, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EdgeState>) =
            size > MAX_TRACKED_SURFACES
    }

    fun onViewScrolled(event: AccessibilityEvent, node: AccessibilityNodeInfo? = null): Result {
        val n = node ?: event.source
        return try {
            val key = scrolledSurfaceKey(event, n) ?: return Result.NoHaptic

            if (isDefinitelyInMiddle(event)) {
                perSurface[key] = EdgeState(atTop = false, atBottom = false)
                return Result.NoHaptic
            }

            val snapshot = getEdgeSnapshot(event, n)
            val old = perSurface[key]

            perSurface[key] = snapshot

            if (old == null) {
                return Result.NoHaptic
            }

            val topHit = snapshot.atTop && !old.atTop
            val bottomHit = snapshot.atBottom && !old.atBottom

            if (topHit || bottomHit) Result.PlayEdgeHaptic else Result.NoHaptic
        } finally {
            if (node == null && android.os.Build.VERSION.SDK_INT < 33) {
                try {
                    n?.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun isDefinitelyInMiddle(event: AccessibilityEvent): Boolean {
        if (event.maxScrollY > 0 && event.scrollY > 0 && event.scrollY < event.maxScrollY) return true
        if (event.maxScrollX > 0 && event.scrollX > 0 && event.scrollX < event.maxScrollX) return true

        val count = event.itemCount
        if (count > 0) {
            val from = event.fromIndex
            val to = event.toIndex
            if (from > 0 && to >= 0 && to < count - 1) return true
        }
        return false
    }

    private fun getEdgeSnapshot(
        event: AccessibilityEvent,
        node: AccessibilityNodeInfo?
    ): EdgeState {
        var atTop = false
        var atBottom = false

        if (event.maxScrollY > 0) {
            atTop = event.scrollY <= 0
            atBottom = event.scrollY >= event.maxScrollY
        } else if (event.maxScrollX > 0) {
            atTop = event.scrollX <= 0
            atBottom = event.scrollX >= event.maxScrollX
        } else if (event.itemCount > 0) {
            atTop = event.fromIndex == 0
            atBottom = event.toIndex >= 0 && event.toIndex == event.itemCount - 1
        }

        node?.let {
            val actions = it.actionList
            val canBackward = actions.any { a ->
                a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id ||
                        a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id ||
                        a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
            }
            val canForward = actions.any { a ->
                a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id ||
                        a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id ||
                        a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
            }

            if (canBackward || canForward) {
                atTop = !canBackward
                atBottom = !canForward
            }
        }

        return EdgeState(atTop, atBottom)
    }

    enum class Result { PlayEdgeHaptic, NoHaptic }
}
