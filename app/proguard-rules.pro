# Keep accessibility service class + constructors so the system can bind it by name.
-keep class com.hapticks.app.service.HapticsAccessibilityService { *; }
# Keep Application class for the same reason.
-keep class com.hapticks.app.HapticksApp { *; }

# Kotlin coroutines - internal debug metadata only; strip noisy probes from release.
-assumenosideeffects class kotlinx.coroutines.internal.FastServiceLoader { *; }

# Strip Log.v/d/i in release to shave binary + CPU cycles.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
