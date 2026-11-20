# Comprehensive YW Launcher update

Target: LG Exalt LTE VN220, Android 6 / API 23. Package ID remains `com.lge.ywlauncher`.

This branch is the single integration branch for the complete user-requested update. Preserve existing launcher icon, package ID, Android 6 compatibility, existing working custom-image picker flow, and prior merged fixes.

## Acceptance checklist

- [ ] Settings option pages must cover the previous page instead of leaving the old page visible; Settings always opens at scroll position zero; make left/right physical soft keys useful.
- [ ] Investigate and correct both launcher and weather task icons in LG Android 6 Recents. Do not claim device verification without an Exalt test.
- [ ] Persistent notification: use the app's proper monochrome small status icon when enabled; when disabled, suppress the status-bar glyph while retaining the full launcher icon in the notification shade.
- [ ] Make inactive page dots visibly distinct from the background.
- [ ] Match the Exalt gradient to the user's original supplied reference; retain Classic and System choices.
- [ ] Reuse the proven YW App Installer-compatible image picker for both launcher background and saved-wallpaper selection.
- [ ] Replace the background popup with a full-screen color/background grid and a live app-grid preview. Left/right changes the preview; Save applies it.
- [ ] Weather icons should match the launcher weather mark's polished palette; night conditions must not show a sun.
- [ ] Forecast notifications fire only when the forecast's actual issued/updated timestamp changes, not on a reload or JSON serialization change. Use a modern notification glyph.
- [ ] Weather load errors show a short generic message, never raw api.weather.gov diagnostics.
- [ ] Cache forecast discussion, load it automatically after refresh, and display the cached text offline.
- [ ] Animate hidden-app removal/reordering rather than abruptly rebuilding the list.
- [ ] Use an icon for the clear-selection X.
- [ ] Add an optional one-screen all-apps grid/drawer mode.
- [ ] Render weather notification preference as a real visible switch.
- [ ] Make outline the default focus treatment.
- [ ] Stop unexpectedly opening Wi-Fi settings when Wi-Fi is already enabled.
- [ ] Add call-connected vibration only if Android 6's public APIs can reliably distinguish call connection from dialing; do not vibrate on mere OFFHOOK/dialing.
- [ ] Add an LG Keyboard settings shortcut to `com.lge.ime/com.lge.ime.setting.MainSettingsActivity` and instructions: General > Word Suggestions > Emoticons.
- [ ] Add a dedicated Home-link activity and manifest deep link for `https://www.ywhome.go`; include that URL in the YW Launcher contact.
- [ ] Verify build, lint, tests, manifest/deep-link behavior, and generated APK. Physical LG Exalt validation remains a separate requirement.

## Device/API notes

Android's public `PhoneStateListener` exposes OFFHOOK but does not reliably tell an ordinary third-party app that the remote party has answered; therefore a call-connected vibration must not be implemented by treating OFFHOOK as answered. Recents task icons use `Activity.setTaskDescription` on API 21+, but OEM SystemUI behavior still requires testing on the LG Exalt.
