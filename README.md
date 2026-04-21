# Hapticks

Android app that adds customizable haptic feedback to taps and scrolls across every installed app, built on top of the Android Accessibility API.

## Requirements

- Android Studio Ladybug (or newer) with the AGP 8.5+ ready toolchain
- JDK 17
- Android SDK Platform 34
- Physical device with a vibrator (emulators generally won't produce haptics)

## Tech stack

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3 (dark-only)
- **Persistence**: AndroidX DataStore Preferences
- **System bridge**: Accessibility Service listening to `TYPE_VIEW_CLICKED` and `TYPE_VIEW_SCROLLED`
- **Vibration**: `VibratorManager` with `VibrationEffect` composition primitives + predefined fallbacks

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Then on the device:

1. Open Hapticks, keep the onboarding card visible.
2. Tap **Open Accessibility settings**.
3. Find **Installed services > Hapticks** and toggle it on.
4. Accept the system warning (this is normal for any accessibility service).
5. Return to Hapticks. Choose a pattern, set intensity, and tap **Test Haptic**. Tapping any button in any app should now vibrate.

## Project layout

```
app/src/main/kotlin/com/hapticks/app/
  HapticksApp.kt                 # Application + service-locator
  MainActivity.kt                # Hosts the single Compose screen
  data/
    HapticsSettings.kt           # Immutable settings snapshot
    HapticsPreferences.kt        # DataStore-backed repo (Flow<HapticsSettings>)
  haptics/
    HapticPattern.kt             # CLICK / TICK / HEAVY_CLICK / DOUBLE_CLICK
    HapticEngine.kt              # VibratorManager + compositions + fallbacks
  service/
    HapticsAccessibilityService.kt   # The system-wide bridge
  ui/
    components/                  # SectionCard, HapticToggleRow, PatternChipRow, EnableServiceCard
    screens/CustomHapticsScreen.kt
    theme/                       # Color, Type, Theme
  viewmodel/CustomHapticsViewModel.kt
app/src/main/res/
  xml/accessibility_service_config.xml
  values/strings.xml, colors.xml, themes.xml
```

## Privacy & Play Store policy

Hapticks uses the Accessibility API solely to detect `TYPE_VIEW_CLICKED` and `TYPE_VIEW_SCROLLED` event types and trigger the device vibrator. It declares `android:canRetrieveWindowContent="false"` so the OS does not grant it access to on-screen content, and it never inspects, stores, or transmits any user data. This intent is disclosed in-app on the onboarding card.

## Out of scope for v1

- Per-app allow / block lists
- Element-type targeting (buttons vs switches vs sliders)
- User-defined custom patterns
- Foreground-service keep-alive for aggressive OEM battery policies
