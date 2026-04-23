package de.robv.android.xposed;

import java.lang.reflect.Method;

/**
 * Compile-time stub for the Xposed helpers used by the Hapticks edge hooks.
 * Only the signatures we invoke are listed; the LSPosed runtime ships the
 * real, richer implementation.
 */
public final class XposedHelpers {

    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz,
            String methodName,
            Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            String className,
            ClassLoader classLoader,
            String methodName,
            Object... parameterTypesAndCallback) {
        return new XC_MethodHook.Unhook();
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return null;
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return null;
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
    }

    public static int getIntField(Object obj, String fieldName) {
        return 0;
    }

    public static Method findMethodExact(
            Class<?> clazz,
            String methodName,
            Object... parameterTypes) {
        return null;
    }
}
