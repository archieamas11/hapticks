package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Compile-time stub of the LSPosed/Xposed classic API entry point.
 * The real implementation is injected into the module's process by LSPosed at runtime;
 * this stub only exists so the module source can compile.
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
