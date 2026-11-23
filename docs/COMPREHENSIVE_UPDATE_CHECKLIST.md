# Comprehensive YW Launcher update

Target: LG Exalt LTE VN220, Android 6 / API 23. Package ID remains `com.lge.ywlauncher`.

All implementation changes for this update are kept together on `feature/comprehensive-launcher-update`.

## Implementation status

- [x] Settings opens at scroll position zero; option dialogs use an opaque full-screen theme so the previous page is not visible behind them; left soft key returns and right soft key activates the focused setting.
- [x] Recent-task handling now leaves the task-description bitmap unset and relies on the explicit manifest activity icon, allowing Android 6 Recents to follow its activity-icon fallback. Physical LG SystemUI verification remains required.
- [x] Persistent notification uses the monochrome launcher-mark glyph when enabled. When disabled, it uses a transparent status glyph while keeping the full-color launcher icon as the notification's large icon.
- [x] Inactive page dots are real outlined circular markers with contrasting fill/stroke.
- [x] Exalt gradient stops were sampled from the user's original Exalt/Classic comparison image and updated to navy → electric blue → violet → vivid magenta.
- [x] The full-screen background chooser shows color choices, System, Exalt, Classic, and Custom image. It previews the selected apps and supports left/right preview navigation, soft-key actions, and Save.
- [x] The custom image chooser uses the Android-6-compatible `ACTION_GET_CONTENT` wildcard/MIME approach already used by the working custom background picker.
- [x] Saved-wallpaper selection now uses the same working picker instead of the internal file browser.
- [x] Weather icons use the app's vivid blue rounded-square visual language; night periods pass their `isDaytime=false` state so they render moon/night imagery rather than a sun.
- [x] Forecast notifications compare the NWS forecast `updateTime` value, not JSON/download changes. The new notification has a dedicated modern monochrome weather glyph and full-color weather large icon.
- [x] Forecast load errors show only “Failed to load...”.
- [x] Forecast discussion is loaded after successful forecast refreshes, persisted locally, and restored for offline viewing.
- [x] Hiding apps fades selected cells out and animates remaining cells into their new positions.
- [x] The clear-selection X already uses the modern close drawable and accessible content description.
- [x] Added a single-screen scrollable app-grid mode alongside paged grid and list.
- [x] Weather new-forecast notification setting is a visible on/off switch.
- [x] Outline focus is now the default, including app-cell focus.
- [ ] Wi-Fi settings popup: no Wi-Fi settings intent or Wi-Fi API call exists in the current launcher source. This behavior still needs a reproducible device trace to identify whether LG SystemUI or a separate app is opening the page.
- [ ] Call-connected vibration: deliberately not implemented. Android 6's public call-state API reports OFFHOOK while an outgoing call is still dialing and does not reliably expose the remote-answer event; using it would vibrate too early.
- [x] Added LG Keyboard settings shortcut to `com.lge.ime/com.lge.ime.setting.MainSettingsActivity`, with General > Word Suggestions > Emoticons instructions.
- [x] Added a Home-link activity and HTTP/HTTPS manifest deep links for `www.ywhome.go`; updated the YW Launcher vCard URL to that address.
- [x] CI run #345 (commit `2864f807`) passed: `assembleDebug` succeeded; `lintDebug` succeeded with 0 errors and 81 warnings; `testDebugUnitTest` succeeded but was `NO-SOURCE` because the project currently defines no unit tests.
- [ ] Install and verify on a physical LG Exalt VN220, especially Recents icons, soft keys, focus navigation, file picker, and Wi-Fi behavior.

## Device/API notes

Android's public `PhoneStateListener` exposes OFFHOOK but does not reliably tell an ordinary third-party app that the remote party has answered; therefore call-connected vibration must not be implemented by treating OFFHOOK as answered. AOSP Recents task loading prefers a TaskDescription icon when supplied, then falls back to the activity icon and application icon. This branch deliberately uses the manifest activity icon fallback because the earlier custom task bitmap did not display on the Exalt.
