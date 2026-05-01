package com.hapticks.app.service.accessibility.handlers

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

internal fun scrolledSurfaceKey(
    event: AccessibilityEvent,
    node: AccessibilityNodeInfo? = null
): String? {
    val pkg = event.packageName?.toString()?.ifBlank { null } ?: "unknown"
    val windowId = event.windowId
    val base = "pkg=$pkg|win=$windowId"
    val nodeId = resolveViewResourceId(event, node)
    if (nodeId != null) {
        return "$base|id=$nodeId"
    }

    val className = event.className?.toString()?.ifBlank { null }
    if (!className.isNullOrBlank()) {
        return if (event.itemCount > 0) {
            "$base|cls=$className|items=${event.itemCount}"
        } else {
            "$base|cls=$className"
        }
    }

    return "$base|event=${event.eventType}"
}

private fun resolveViewResourceId(
    event: AccessibilityEvent,
    node: AccessibilityNodeInfo?
): String? {
    val n: AccessibilityNodeInfo = node ?: event.source ?: return null
    return try {
        val id = n.viewIdResourceName
        if (id.isNullOrBlank() || id.endsWith("/no_id") || id == "null") null else id
    } finally {
        // Only recycle if we fetched it here
        if (node == null && android.os.Build.VERSION.SDK_INT < 33) {
            try {
                n.recycle()
            } catch (_: Exception) {
            }
        }
    }
}

