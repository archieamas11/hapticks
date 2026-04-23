package de.robv.android.xposed;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time stub of {@code de.robv.android.xposed.XSharedPreferences}.
 * <p>
 * LSPosed ships the real implementation (which implements
 * {@code android.content.SharedPreferences} and tails the module's
 * {@code shared_prefs/*.xml} files from target processes).
 * <p>
 * This stub only mirrors the subset of methods Hapticks' edge hooks call into,
 * plus the constructors, so the module source can compile with a pure
 * {@code java-library} module — the real class replaces this one at runtime.
 */
@SuppressWarnings("unused")
public final class XSharedPreferences {

    public XSharedPreferences(File file) {
    }

    public XSharedPreferences(String packageName) {
    }

    public XSharedPreferences(String packageName, String prefFileName) {
    }

    @Deprecated
    public void makeWorldReadable() {
    }

    public boolean hasFileChanged() {
        return false;
    }

    public void reload() {
    }

    public File getFile() {
        return null;
    }

    public Map<String, ?> getAll() {
        return null;
    }

    public String getString(String key, String defValue) {
        return defValue;
    }

    public Set<String> getStringSet(String key, Set<String> defValues) {
        return defValues;
    }

    public int getInt(String key, int defValue) {
        return defValue;
    }

    public long getLong(String key, long defValue) {
        return defValue;
    }

    public float getFloat(String key, float defValue) {
        return defValue;
    }

    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }

    public boolean contains(String key) {
        return false;
    }
}
