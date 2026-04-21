package com.hapticks.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HapticksDarkColorScheme = darkColorScheme(
    primary = HapticksSage,
    onPrimary = HapticksOnPrimary,
    primaryContainer = HapticksOlive,
    onPrimaryContainer = HapticksSage,
    secondary = HapticksSageDim,
    onSecondary = HapticksOnPrimary,
    secondaryContainer = HapticksOliveDim,
    onSecondaryContainer = HapticksSage,
    background = HapticksBlack,
    onBackground = HapticksOnSurface,
    surface = HapticksBlack,
    onSurface = HapticksOnSurface,
    surfaceVariant = HapticksSurface,
    onSurfaceVariant = HapticksOnSurfaceMuted,
    surfaceContainer = HapticksSurface,
    surfaceContainerHigh = HapticksSurfaceHigh,
    outline = HapticksOutline,
    outlineVariant = HapticksOutline,
)

@Composable
fun HapticksTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = HapticksDarkColorScheme,
        typography = HapticksTypography,
        content = content,
    )
}
