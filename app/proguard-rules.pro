-keep class com.hapticks.app.service.HapticsAccessibilityService { *; }
-keep class com.hapticks.app.HapticksApp { *; }
-keep class io.github.libxposed.** { *; }
-keep interface io.github.libxposed.** { *; }
-keep class com.hapticks.app.xposed.EdgeEffectHapticsModule { *; }
-assumenosideeffects class kotlinx.coroutines.internal.FastServiceLoader { *; }
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
