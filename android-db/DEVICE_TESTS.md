# V1 device acceptance

Record phone model, Android version/security patch level, app versions, APK revision, date, tester,
and battery-management settings. Test a Pixel and the intended Samsung/Xiaomi/
OnePlus devices. Android 12 (API 31) and Android 17 (API 37) are mandatory.

Host tests run Android 12 simulations. On 2026-09-17, the owner confirmed setup,
consent, access enabling, Blinkit bubble appearance and hiding on Home for 1.0.2 on
Vivo V2404 / Android 16. They reported slow disappearance and an unwanted shortcut.
This is qualitative owner evidence, not the full matrix or a timing measurement.

## Baseline procedure

Keep the service enabled but its optional Android accessibility shortcut disabled.
The shortcut is a system button that is separate from BetterChoice's shopping bubble.

1. Install the debug APK and enable accessibility through BetterChoice's disclosure.
2. Swipe BetterChoice away from Recents. Do not open it again during the run.
3. Start log capture: `adb logcat -v monotonic BetterChoice:I '*:S'`.
4. Record the screen at 60fps or higher (external high-frame-rate video preferred).
5. From Home, launch each of the five apps 30 times, returning Home each time.
6. Measure launch gesture to first visible bubble and Home gesture to bubble
   disappearance frame by frame. Also report first app frame to first bubble frame.
7. Record median, p95, maximum, missing appearances, stale bubbles and duplicates.
   For the initial strict gate, require every appearance/removal within 500ms.
   Keep cold app startup measurements separate from warm launches.

## Required cases

| Case | Expected |
| --- | --- |
| Setup denied or cancelled | No bubble; setup remains available |
| Each supported app opened from Home after setup activity dismissed | One bubble within target |
| Home, Recents, Settings, unrelated app | No bubble within target |
| Supported → supported | One bubble, no duplication |
| Rapid supported → unsupported → supported | Final window determines visibility |
| Keyboard in supported app | Bubble remains; typing and back work |
| Notification shade / permission dialog | Bubble hidden while system window is focused |
| Lock / screen off | Bubble removed immediately |
| Unlock into supported app | Bubble returns without opening BetterChoice |
| Rotate, change display size, drag to every edge | Bubble stays reachable |
| Split screen | Only focused supported app enables bubble |
| Reboot and unlock | Enabled service reconnects without opening setup |
| Idle overnight / battery saver / OEM battery controls | Record survival and latency |
| Disable accessibility while bubble visible | Bubble removed, remains absent |
| Re-enable accessibility while app already open | Bubble returns on reconciliation |
| Force-stop BetterChoice | Record platform behavior; outside persistence guarantee |
| 100 transitions | No leaks, crashes, duplicate overlays or stale bubbles |

Unit tests cannot establish event delivery, reboot survival, keyboard/window
behavior, OEM lifecycle behavior or the 500ms device target. Keep v1 unaccepted
until these observations are recorded; do not substitute log timings for video.

## Security cases (physical device / second test app required)

| Case | Expected |
| --- | --- |
| Enable in Android Settings on fresh install before any in-app consent | Service disables itself; no bubble and no window reads |
| Upgrade original v1 with previously enabled service | No detection until disclosure version 2 accepted |
| Cancel, Back or outside-tap on disclosure | No new consent |
| Obscuring test-app overlay over setup/consent | Application overlays suppressed; flagged touches rejected |
| Partial overlay appears during a gesture, disappears before finger up | Entire gesture rejected; no consent change |
| TalkBack / keyboard activation of consent | Explicit activation works; no forced touch-only workflow |
| Turn off while a follow-up reconciliation is pending | Bubble removed, callback cancelled, service disabled |
| Revoke access during drag / rapid app switch | No crash, stale bubble or subsequent window reads |
| Test app calls bindService with explicit BetterChoice service component | SecurityException / binding denial; only Android can bind |
| External ACTION_VIEW or launcher intent with consent/enabled extras and URI | Setup only; incoming data cannot enable or grant consent |
| Enable Android 17 Advanced Protection | Feature unavailable; no bypass instructions; no service activity |
| Device policy blocks settings | Clear restricted/unavailable explanation; no crash |
| Clear storage / uninstall / backup and restore | No retained consent or shopping data; setup required |
| Release binary logs while switching retailers | No BetterChoice diagnostic messages or activity data |
| Owner-signed APK / Play-delivered bundle | Verify intended certificate; debug signing not accepted |

The binding test must use another ordinary app UID; a same-process unit test or
ADB shell's privileged environment is not equivalent. Do not mark this test passed
based only on the manifest inspection. Release acceptance also requires testing the
optimized binary, not only the debug APK.
# Additional 1.0.2 security checks

- Leave setup while the disclosure is open, then return. A fresh disclosure must be
  opened and accepted; an old Agree callback must never grant consent.
- On Android 13+, confirm cache disabling succeeds and foreground detection still
  works. If cache setup fails, the service must disable before reading windows.
- Android 12 has no public cache-disable API; record this limitation when testing.
- Vivo V2404 / Android 16: owner screenshot confirms Play Protect blocks the observed
  file-based installation. Do not mark installed/passed until supported installation
  and actual device testing succeed. Preserve Play Protect and device policy.

## 1.0.3 exit and shortcut checks

1. Install/update with USB developer installation; 1.0.2 previously succeeded on Vivo.
2. In Android Accessibility > BetterChoice bubble, keep the service enabled and
   disable only its optional shortcut. Confirm the persistent system button vanishes
   and the BC shopping bubble still appears automatically.
3. Repeat shopping app -> Home, Recents, another app and lock. Record exit latency
   on video, including rapid switches and long transition animations. The 100ms
   watchdog interval is not itself a physical timing measurement.
4. Type with the keyboard and drag the BC bubble; watch for flicker or repeated
   attachments. Return to each retailer without opening BetterChoice first.
5. Turn off/revoke access while BC is visible. Confirm removal and no reappearance.
6. Check battery/CPU usage during a long shopping session and idle Home. There should
   be no recurring foreground-window queries while the bubble is hidden.
