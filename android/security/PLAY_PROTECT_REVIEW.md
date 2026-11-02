# Play Protect installation block - owner review packet

Date: 2026-09-16. Device reported by owner: Vivo T3 / V2404, Android 16.
The owner's screenshot shows Play Protect's sensitive-access installation block.
It is not evidence of an Android-version mismatch or APK-signature error. The exact
APK hash of the file on the phone is unknown; retain that file to identify it.

Google documents this warning for internet-sideloaded apps requesting sensitive
permissions, including Accessibility. BetterChoice requires an AccessibilityService
for its current automatic foreground-app bubble design. Removing that declaration
would disable the intended feature. The engineering fixes below do not guarantee
that Play Protect will allow installation or that Play will approve the app.

## Additional hardening in 1.0.2-beta

- Android 13+: disable the framework accessibility node cache and prefetching before
  any window query. If the platform cannot apply this setting, stop the service.
  Request the selected window root with prefetch flags zero; read its package name
  and release it. Android 12 lacks the public cache control, so that platform has a
  documented limitation; no node traversal is performed.
- Dismiss the consent dialog when setup loses its resumed lifecycle state. Ignore
  stale Agree callbacks, including after returning to the activity.
- Preserve versioned consent, obscured-touch rejection, lock/unknown-state hiding,
  no network permission, no screen capture/gestures/keystroke capabilities, disabled
  backups, and debug-only diagnostics. Broader window-content capability remains
  declared and disclosed because root-package resolution requires it.
- Restore the original local debug signing identity for compatible test updates.
  Release artifacts remain unsigned without owner credentials; debug keys are not
  substituted for production signing.

## Supported next steps

1. Keep Play Protect enabled. Record the phone OS, installation source, app version,
   APK SHA256, signing certificate and exact warning. The screenshot's blocked APK
   is not yet tied to a known hash, so do not label it as the latest build.
2. Use a dedicated Android Studio emulator or an owner-controlled development device
   for development testing. The included USB helper performs a normal ADB install
   and preserves Android's result; it does not disable verification or override
   restrictions. It is not a consumer distribution solution or proof of approval.
3. For broader testing, complete owner signing, publisher/contact/policy information
   and applicable Play Console requirements, then prepare an internal or closed test
   distribution. Google still decides eligibility and approval. Nothing is submitted
   by this project or handoff.
4. Review Google's warning guidance and applicable policies. If the classification
   is believed to be incorrect after review, the owner can use the official appeal
   route. Submit the actual blocked artifact's identity and an accurate description;
   do not claim certification or a completed physical-device audit.

## Draft factual explanation for owner review (not submitted)

BetterChoice v1 displays a clearly branded 56dp bubble only while one of five shopping
apps is foreground. Setup requires explicit local consent followed by the user's
Android Accessibility authorization. The service resolves current window focus and
uses the selected root's package name; it does not traverse screen content, perform
actions, capture screenshots or read notifications/keystrokes. Android grants broader
window access than the app uses, and the disclosure states this. No INTERNET permission,
analytics or remote processing is included. Consent can be withdrawn in the app;
the bubble is removed and the service disables itself. It declares
isAccessibilityTool=false. This is a shopping companion, not a disability assistance
tool. Host tests and release inspections are documented; real-device acceptance is
incomplete. Please review the attached, correctly identified artifact and evidence.

Owner must supply: blocked artifact SHA256 and certificate, actual distribution URL
if applicable, publisher identity/contact, privacy policy URL, and a demonstration
recording. Confirm every statement against the submitted binary. Keep private keys
and passwords out of submissions and logs.

## Sources checked 2026-09-16

- [Google Play Protect warning guidance and appeal route](https://developers.google.com/android/play-protect/warning-dev-guidance)
- [Accessibility cache control, API 33+](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#setCacheEnabled(boolean))
- [Window root prefetch control, API 33+](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo#getRoot(int))
- [Play AccessibilityService requirements](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)

See [PLAY_REVIEW.md](PLAY_REVIEW.md), [RELEASE.md](RELEASE.md) and the exported
INSTALLATION_RECHECK.md. No appeal, Play submission or publication has occurred.
