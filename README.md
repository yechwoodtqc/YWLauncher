# YWLauncher

A native Android launcher project targeting Android 6.0 (API 23) and keypad-only LG flip phones.

## Implemented
- Selectable Android home launcher with D-pad-focusable rows, app icons above names, and visible focus states.
- App inclusion controls, with system apps excluded by default.
- Custom per-activity ordering with Move Up / Move Down controls.
- Long-press actions to launch, uninstall, or hide an app.
- Background choices (system wallpaper, blue, or black), compact/comfortable/large app tiles, system/light/dark themes, and ten accent palettes.
- Persistent launcher notification, boot receiver, and a visually hidden small icon by default (Android still requires a notification icon resource).
- Weather screen using the National Weather Service API, ZIP-code lookup, manual location input, current-location detection with provider fallback, refresh, and last-updated time.
- English/Hebrew date home-screen widget provider.
- Logcat collection with a system file picker so the user can choose where to save the text file.
- GitHub Actions workflow configured to build and upload a debug APK artifact named `ywlauncher-debug-apk`.

## Build
The workflow uses JDK 17, Android SDK platform 34, Gradle 8.2, and runs `gradle --no-daemon assembleDebug`. To build locally, install the Android SDK platform 34 and run `gradle assembleDebug` with JDK 17.

## Device and build status
This project has not yet been verified on an LG Exalt or LG Classic Flip. The GitHub connector did not return a workflow run or commit status for the latest commits, so a successful APK build is **not confirmed**. Check the repository's Actions page for the actual run and download the artifact if the build succeeds.

Android widgets are hosted by the phone's home app, not inside this app, so widget availability depends on that home app. The weather.gov service is intended for US forecasts. Location detection needs runtime permission and may fail indoors or when no provider can obtain a fix. Android restricts access to system-wide logcat on production devices, so exported logs may not contain every system message.
