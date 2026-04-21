package com.hapticks.app.haptics

import androidx.annotation.StringRes
import com.hapticks.app.R

/**
 * User-selectable haptic patterns shown in the Pattern section of the UI.
 *
 * Order of declaration is the order rendered in the chip row.
 */
enum class HapticPattern(@StringRes val labelRes: Int) {
    CLICK(R.string.pattern_click),
    TICK(R.string.pattern_tick),
    HEAVY_CLICK(R.string.pattern_heavy_click),
    DOUBLE_CLICK(R.string.pattern_double_click);

    companion object {
        val Default: HapticPattern = TICK

        fun fromStorageKey(key: String?): HapticPattern =
            entries.firstOrNull { it.name == key } ?: Default
    }
}
