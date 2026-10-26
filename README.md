# YWLauncher

A native Android launcher project targeting Android 6.0 (API 23) and keypad-only LG flip phones.

## Current implementation
- Standalone launcher activity with focusable D-pad rows and app icons/names.
- Long-press app actions: launch, uninstall, or hide.
- App inclusion settings (defaults to excluding system apps) and launcher access notification with boot receiver.
- Weather activity using the National Weather Service API, manual location lookup, last-updated timestamp, and refresh button.
- Date home-screen widget provider.
- Logcat export to the app-specific Documents directory.
- GitHub Actions workflow builds and uploads a debug APK artifact.

## Build
Run `gradle assembleDebug` with JDK 17 and Android SDK platform 34 installed. GitHub Actions runs the same build and uploads the APK as `ywlauncher-debug-apk`.

## Device notes
This is an early build, not yet device-tested on an LG Exalt or LG Classic Flip. Android widgets are hosted by the phone's home app, not inside this app; widget availability therefore depends on the stock home app. Location detection requires runtime location permission and a recent location fix. App list order is currently alphabetical; the manual reorder UI and exact D-PAD Player theme parity still need follow-up work. Logcat access is restricted by Android on production devices, so exported logs may contain only the app-visible portion.
