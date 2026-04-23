package com.hapticks.app.edge

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ScrollView
import com.hapticks.app.haptics.HapticPattern
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed / Xposed entry point for Hapticks Edge Haptics.
 *
 * Wiring:
 *  - `app/src/main/assets/xposed_init` points LSPosed at this class.
 *  - `AndroidManifest.xml` advertises the module (xposedmodule = true,
 *    xposedminversion = 93 which every LSPosed 1.x / 2026 build reports).
 *  - `res/values/xposed_scope.xml` lists the default recommended scope.
 *
 * What it does inside each scoped process:
 *  - When the hooked process is *our own* (`com.hapticks.app`) it overrides
 *    [EdgeHapticsBridge.isModuleActive] to return `true`, which is how the
 *    app's UI detects "LSPosed actually injected us".
 *  - For every other process it hooks the relevant scroll surface methods
 *    (RecyclerView, ScrollView, AndroidX NestedScrollView, WebView, and the
 *    low-level [View.overScrollBy]) and fires [EdgeVibrator.play] when the
 *    view reports it cannot scroll any further.
 *
 * Everything is defensive — any failure to hook a single surface logs and
 * continues so a missing class or an obfuscated implementation never prevents
 * the other hooks from installing.
 */
class EdgeScrollHooks : IXposedHookLoadPackage {

    @Volatile private var enabled: Boolean = true
    @Volatile private var pattern: HapticPattern = HapticPattern.TICK
    @Volatile private var intensity: Float = 1.0f
    @Volatile private var lastPrefCheckMs: Long = 0L
    @Volatile private var sharedPrefs: XSharedPreferences? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (lpparam.packageName == OWN_PACKAGE) {
                hookOwnActivationStub(lpparam)
                // Self-process does not need scroll hooks; UI handles its own haptics.
                return
            }

            initSharedPrefs()
            // We install hooks unconditionally and re-read the enabled flag on
            // every callback — otherwise toggling "Enable Edge Haptics" in the
            // UI while an app is already running would have no effect until
            // the app was restarted.
            hookRecyclerView(lpparam)
            hookScrollView(lpparam)
            hookNestedScrollView(lpparam)
            hookWebView(lpparam)
            hookOverScrollBy(lpparam)
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: handleLoadPackage failed for ${lpparam.packageName}: $t")
        }
    }

    private fun hookOwnActivationStub(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.hapticks.app.edge.EdgeHapticsBridge",
                lpparam.classLoader,
                "isModuleActive",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                },
            )
            XposedBridge.log("$TAG: activation stub hooked in own process")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: activation stub hook failed: ${t.message}")
        }
    }

    private fun initSharedPrefs() {
        if (sharedPrefs != null) return
        try {
            sharedPrefs = XSharedPreferences(OWN_PACKAGE, EdgeHapticsBridge.XPOSED_PREFS_NAME).also { prefs ->
                @Suppress("DEPRECATION")
                try { prefs.makeWorldReadable() } catch (_: Throwable) { /* LSPosed 1.x no-ops this */ }
            }
            refreshPrefs()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: prefs init failed: ${t.message}")
        }
    }

    private fun isEnabled(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastPrefCheckMs > PREF_TTL_MS) refreshPrefs()
        return enabled
    }

    private fun refreshPrefs() {
        val prefs = sharedPrefs ?: return
        try {
            prefs.reload()
            enabled = prefs.getBoolean(EdgeHapticsBridge.KEY_EDGE_ENABLED, true)
            val patternName = prefs.getString(EdgeHapticsBridge.KEY_EDGE_PATTERN, "TICK") ?: "TICK"
            pattern = HapticPattern.fromStorageKey(patternName)
            intensity = prefs.getFloat(EdgeHapticsBridge.KEY_EDGE_INTENSITY, 1.0f)
            lastPrefCheckMs = SystemClock.uptimeMillis()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: prefs reload failed: ${t.message}")
        }
    }

    private fun hookRecyclerView(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (className in RV_CLASS_CANDIDATES) {
            val rvClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(
                    rvClass, "dispatchOnScrollStateChanged",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!isEnabled()) return
                            val state = (param.args[0] as? Int) ?: return
                            if (state != STATE_IDLE) return
                            val rv = param.thisObject as? View ?: return
                            val atTop = !rv.canScrollVertically(-1)
                            val atBottom = !rv.canScrollVertically(1)
                            dispatchEdge(rv.context, atTop, atBottom)
                        }
                    },
                )
                XposedBridge.log("$TAG: hooked $className in ${lpparam.packageName}")
                return
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: RecyclerView hook failed on $className: ${t.message}")
            }
        }
    }

    private fun hookScrollView(@Suppress("UNUSED_PARAMETER") lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                ScrollView::class.java, "onScrollChanged",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                scrollViewHook,
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ScrollView hook failed: ${t.message}")
        }
    }

    private fun hookNestedScrollView(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (className in NSV_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(
                    cls, "onScrollChanged",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    scrollViewHook,
                )
                XposedBridge.log("$TAG: hooked $className")
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: NSV hook failed on $className: ${t.message}")
            }
        }
    }

    private val scrollViewHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (!isEnabled()) return
            val view = param.thisObject as? ViewGroup ?: return
            val scrollY = (param.args[1] as? Int) ?: view.scrollY
            val child = view.getChildAt(0) ?: return
            val viewportH = view.height
            val contentH = child.height
            val atTop = scrollY <= 0
            val atBottom = contentH > 0 && scrollY + viewportH >= contentH
            dispatchEdge(view.context, atTop, atBottom)
        }
    }

    private fun hookWebView(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                WebView::class.java, "onScrollChanged",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                webViewHook,
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: WebView hook failed: ${t.message}")
        }
    }

    private val webViewHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (!isEnabled()) return
            val wv = param.thisObject as? WebView ?: return
            val scrollY = (param.args[1] as? Int) ?: wv.scrollY
            val scale = try {
                @Suppress("DEPRECATION")
                wv.scale
            } catch (_: Throwable) {
                1.0f
            }
            val contentH = (wv.contentHeight * scale).toInt()
            val viewportH = wv.height
            val atTop = scrollY <= 0
            val atBottom = contentH > 0 && scrollY + viewportH >= contentH
            dispatchEdge(wv.context, atTop, atBottom)
        }
    }

    /**
     * [View.overScrollBy] is the single chokepoint every AOSP-derived scroll
     * container calls *after* clamping the scroll delta — Compose's
     * interop view, androidx.core NestedScrollView, Jetpack Paging grids,
     * Chromium's scroller, etc. Hooking it gives us edge coverage for the
     * long tail of custom scroll surfaces that don't route through
     * RecyclerView/ScrollView/NestedScrollView/WebView.
     *
     * Signature:
     * ```
     * protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY,
     *     int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY,
     *     boolean isTouchEvent)
     * ```
     */
    private fun hookOverScrollBy(@Suppress("UNUSED_PARAMETER") lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                View::class.java, "overScrollBy",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!isEnabled()) return
                        val view = param.thisObject as? View ?: return
                        val isTouch = (param.args[8] as? Boolean) ?: return
                        if (!isTouch) return
                        val atTop = !view.canScrollVertically(-1)
                        val atBottom = !view.canScrollVertically(1)
                        if (!atTop && !atBottom) return
                        dispatchEdge(view.context, atTop, atBottom)
                    }
                },
            )
            XposedBridge.log("$TAG: hooked View.overScrollBy")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: View.overScrollBy hook failed: ${t.message}")
        }
    }

    private fun dispatchEdge(context: Context, atTop: Boolean, atBottom: Boolean) {
        if (!atTop && !atBottom) return
        val edge = if (atTop) Edge.TOP else Edge.BOTTOM
        val decision = EdgeDetector.Shared.detect(edge, SystemClock.uptimeMillis())
        if (decision == EdgeDetector.Decision.FIRE) {
            EdgeVibrator.play(context, edge, pattern, intensity)
        }
    }

    private companion object {
        const val TAG = "HapticksEdgeHooks"
        const val OWN_PACKAGE = "com.hapticks.app"

        const val PREF_TTL_MS = 2_000L

        const val STATE_IDLE = 0

        val RV_CLASS_CANDIDATES = arrayOf(
            "androidx.recyclerview.widget.RecyclerView",
            "android.support.v7.widget.RecyclerView",
        )

        val NSV_CLASS_CANDIDATES = arrayOf(
            "androidx.core.widget.NestedScrollView",
            "android.support.v4.widget.NestedScrollView",
        )
    }
}
