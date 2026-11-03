# YWLauncher

**Android application ID:** `com.lge.ywlauncher`

A native Android app designed for keypad-only LG flip phones and compatible Android devices. It targets Android 6.0 (API 23) and remains a regular app; it does **not** register itself as the device's default Home app.

## Features

- App grid and list layouts with D-pad navigation, visible focus states, configurable rows, columns, and icon sizes.
- App selection, hide/unhide, system-app opt-in, and saved app ordering.
- System wallpaper, solid colors, custom images with Fit / Fill-Crop / Stretch preview, Exalt gradient, and LG Classic menu backgrounds.
- Light, dark, and system themes; configurable accent and focus styles.
- Persistent notification shortcut with a separately configurable status-bar icon.
- Optional wallpaper restoration after reboot.
- T9 dictionary backup and merge-style restore through Android's User Dictionary provider. Availability depends on the device's provider and permissions.
- Logcat export to a user-selected file. Android may restrict which system messages an ordinary app can read.
- YW Weather with National Weather Service forecasts, saved locations, ZIP/city lookup, current device-location detection, forecast refresh, and weather widget.
- English and Hebrew date widget.
- Separate YW Launcher and YW Weather entries in Recent Apps.

## Build

GitHub Actions uses JDK 17, Android SDK platform 34, Gradle 8.2, builds the debug APK, runs Android lint, and uploads build diagnostics and the APK artifact for pull requests.

To build locally, install JDK 17 and Android SDK platform 34, then run:

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon lintDebug
```

## Compatibility and verification

- Minimum Android version: Android 6.0 (API 23).
- Weather forecasts use the US National Weather Service; weather.gov coverage is for the United States.
- Current-location detection requires location permission and an enabled provider capable of returning a fresh fix. If it cannot obtain one, choose a city or ZIP code; the app does not substitute an IP-based location estimate.
- Widgets are hosted by the phone's existing Home app, not inside YW Launcher.
- A successful CI build and lint run do not replace testing on the target LG device. Wallpaper rendering, file access, notification behavior, dictionary-provider access, and keypad navigation still require physical-device verification.
