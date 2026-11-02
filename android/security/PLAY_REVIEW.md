# Google Play beta preparation

Status: draft materials; no submission, publication, Play approval or security
certification has occurred. The release bundle remains unsigned without owner keys.

The owner's Vivo Android 16 screenshot confirms a Play Protect sensitive-access
installation block. See [the review packet](PLAY_PROTECT_REVIEW.md) for the documented
restriction, further hardening and owner review steps. Do not claim a rebuilt APK
will remove that block.

## Accessibility declaration rationale

BetterChoice's core feature is a visible companion bubble that automatically appears
when Blinkit, Zepto, Swiggy, Zomato or Flipkart is active and disappears on exit.
It must work without the user starting a session from the app first. The service
receives window transitions and reads the root package name only. No screenshots,
screen text traversal, keystrokes, gestures, automated actions, or remote processing
are implemented. `isAccessibilityTool` is false.

Narrower API assessment: Usage Access requires separate user authorization and
polling for foreground transitions. It does not provide equivalent event-driven
window/focus detection for this interaction. Accessibility window retrieval is used
to resolve current focus instead of acting on potentially delayed event packages.
Record this engineering rationale in the declaration; Google may still require
an alternative or reject the use case. This is not a claim of preapproval.

The bubble uses TYPE_ACCESSIBILITY_OVERLAY and stays confined to a visible 56dp
control. The app does not request SYSTEM_ALERT_WINDOW. HIDE_OVERLAY_WINDOWS protects
its own consent windows; QUERY_ADVANCED_PROTECTION_MODE reads device restriction
state. Neither permission grants screen capture or user interaction automation.

Suggested listing disclosure:

> BetterChoice uses Android Accessibility to recognise supported shopping apps and
> display its bubble automatically. Version 1 processes app/window metadata only
> on your phone, does not capture screens or read product text, and sends no data.
> You can turn the companion off at any time. The feature may be unavailable where
> device security policy restricts accessibility services.

## Draft Data Safety answers

For the current release binary (recheck after any dependency or feature change):

| Question | Draft answer and basis |
| --- | --- |
| Does the app collect or share required user data types? | No: all app processing is on-device; no network permission or transmission SDK. |
| Advertising / tracking | None. |
| Accounts / account deletion | No accounts are created. Account deletion is not applicable. |
| Local-data deletion | Turn off clears consent; Clear storage or uninstall removes all app preferences. |
| Data encrypted in transit | No app data transmission; do not claim a tested network encryption feature. |
| Independent security review | Do not claim certification or a completed independent audit. |

Accessibility permission and disclosure requirements apply independently of the
Data Safety collection answers. Verify the live Console questions when submitting.

## Demonstration recording

1. Fresh install, open the app, show the complete disclosure and explicit Agree action.
2. Show Android settings enabling BetterChoice bubble.
3. Close the setup activity and launch each supported retailer; show one bubble.
4. Return Home and switch to another app; show immediate removal.
5. Show drag, keyboard interaction, lock/unlock, and the retailer remaining usable.
6. Turn off BetterChoice; show removal, disabled service, and required fresh consent.
7. Show direct Settings enable without consent does not activate the companion.

## Submission prerequisites

- Fill in publisher/legal entity, monitored contact, and effective date in the policy;
  host it publicly and configure its HTTPS URL in the app and store listing.
- Owner supplies upload signing credentials and enables Play App Signing. Keep
  upload key and recovery material outside the repository.
- Complete declaration, Data Safety, listing, content rating, and any testing/account
  requirements shown for the owner's Play account. No answers are submitted automatically.
- Attach real-device acceptance evidence for Android 12 and Android 17. Record
  Advanced Protection as an unsupported configuration where it restricts the service.
- Do not mark the beta release-ready until the security evidence checklist passes.

Sources checked for this implementation:
- [AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- [Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Android security changes in 2026](https://blog.google/security/whats-new-in-android-security-privacy-2026/)
