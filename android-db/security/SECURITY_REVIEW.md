# BetterChoice v1 security review

Latest follow-up: 2026-09-17, **1.0.3-beta (code 4)**. Original review and earlier
evidence below retain their dates; see the latest follow-up section at the end.

Reviewed 2026-09-13. Build: 1.0.1-beta (version code 2), Android 12+ / target API 37.
Status: **implementation and host verification complete; device acceptance pending**.
This is an engineering review, not a certification, independent penetration test,
or Google Play approval. The first v1 APK is superseded by this hardened beta.

## Boundaries and threats

Protect consent against bypass and UI deception, minimise accessibility data access,
prevent untrusted apps from binding the companion, avoid stale overlays and retained
activity data, and protect build inputs/signing credentials. The trusted boundary is
Android's process sandbox, system accessibility binding and device security policy.
A compromised OS/rooted device, malicious system component, or another already
privileged accessibility service is not fully controlled by this app.

App package matching enables only a bubble; it is not cryptographic verification of
retailer identity. No product data, links, purchase actions or account information
are handled. Before future retailer APIs or sensitive actions, revisit that boundary.
The app cannot identify payment pages without content classification; it makes no
claim to hide specifically on checkout screens within a supported app.

## Applicable MASVS controls and evidence

| Area | Implemented controls | Verification and limits |
| --- | --- | --- |
| PRIVACY | Versioned opt-in, service gate before window reads, direct-Settings bypass prevention, synchronous stop and consent withdrawal; app/package metadata only | Consent persistence, old/future/malformed versions, external intent data, cancellation and withdrawal tests pass. |
| STORAGE | Only consent version and bubble position in private preferences; no backup/device transfer; no history; release diagnostics removed | Shipped backup XML inspected. Release DEX contains no Android Log references or diagnostic strings. Restore on real devices pending. |
| PLATFORM | System signature permission guards exported service; launcher ignores all incoming extras/URI data; no other exported component; protected setup/dialog windows; whole obscured gestures rejected | Manifest and obscured-event simulations pass. Actual cross-UID binding denial and malicious overlay testing remain pending. |
| CODE | Pinned patched build/test dependencies, strict checksums, dependency locks, unsigned-by-default release; production debugging disabled and code/resources shrunk | Debug/release builds, unit tests, lint, publisher checksum checks and negative build-control tests pass. |
| NETWORK | No INTERNET permission or runtime networking SDK; cleartext denied in configuration | APK permission allowlist and packaged XML checked. No network security feature or TLS implementation is claimed. |
| AUTH / CRYPTO | No accounts, authentication tokens, app-managed sensitive secrets or cryptographic protocol | Not applicable to this offline v1; signing uses platform tooling and external owner credentials. |

Reference: [OWASP MASVS](https://mas.owasp.org/MASVS/). Controls are scoped to this
version; this table is not a claim that every MASVS/MASTG test was executed.

## Checks performed

- 24 passing tests: 9 original foreground/window tests, 6 Android service/consent
  lifecycle tests, 3 consent UI tests, 2 gesture rejection tests, 2 overlay race tests,
  and 2 manifest security tests. Android framework simulations use API 31.
- Debug/release lint: zero errors. Remaining warnings concern newer tool versions;
  they are not suppressed as security findings. Versions are intentionally pinned.
- Release APK inspection: minimum/target API, permission allowlist, protected service,
  disabled accessibility-tool designation, absence of capture/gesture capabilities,
  backup exclusions, cleartext policy, no logging/test libraries in DEX.
- Both negative build-control tests pass: modified checksum rejected; partial signing
  configuration rejected. No production keys were supplied, generated or committed.
- The normal build helper completed all test/lint/APK/AAB tasks offline with strict
  dependency verification and committed lockfiles, without regenerating trust metadata.
- SDK apksigner verifies the debug APK's v2 signature. The release APK has no signing
  block, and jarsigner confirms the AAB is unsigned, as required without owner keys.
- 702 candidate verification artifacts match repository publisher checksum sidecars,
  with zero mismatches or unverified artifacts. SHA256 is used where published; SHA1
  fallback and exact publisher URL are recorded per artifact. Gradle itself enforces
  committed SHA256 values. Module variant redirects are followed from Gradle metadata.
- OSV review of the locked Maven dependency versions returned zero advisories after
  remediation. This is a dated known-advisory check, not proof of no vulnerabilities.
  It does not scan Windows, JDK/SDK binaries, or the Gradle distribution's embedded
  libraries as Maven dependencies. Update those from their official security releases.

## Dependency remediation

Initial findings were in build/lint/test tooling, not the app runtime. Applied and
tested compatible overrides: Netty 4.1.137.Final, Bouncy Castle 1.84, Commons Lang
3.18.0, HttpClient 4.5.14, jose4j 0.9.6 and JDOM 2.0.6.1. AGP also requires Kotlin
tooling types even with no Kotlin source; its Kotlin toolchain family is patched
to stable 2.4.20 for GHSA-r937-wjx7-w2jp. Built-in Kotlin and build caching remain
disabled for this Java-only project. AGP 9.3.2, Gradle 9.5.0 and JDK 17 remain pinned.

Resolved buildscript and application configurations are locked independently.
Verification metadata may include previously resolved parent POMs and older
reviewed artifacts; vulnerability queries use currently locked versions. All metadata
artifacts, including those historical entries, are checked against publisher hashes.

Raw evidence:
- [Publisher checksums](evidence/publisher-checksums.json)
- [Final OSV queries](evidence/osv-dependencies.json)
- [Original advisory details for remediation review](evidence/advisory-details.json)
- [Packaged release inspection](evidence/release-inspection.json)
- [Negative build tests](evidence/build-control-tests.json)

## Outstanding acceptance gates

No Android phone/emulator is connected. Android 12 and Android 17 real-device
interaction, 500ms end-to-end timing, reboot/lock/battery behaviour, OEM settings,
backup/restore and adversarial second-app tests are **not executed**. Follow
[DEVICE_TESTS.md](../DEVICE_TESTS.md) before accepting v1. Host simulations do not
establish system permission enforcement or pixels appearing on an actual phone.

Advanced Protection on Android 17 is an explicitly unsupported configuration where
it prevents this non-accessibility-tool service. The app shows unavailability and
does not prompt users to disable protection or mislabel itself. A device policy or
force-stop can also stop the service. No universal persistence guarantee is claimed.

Before Play distribution, the owner must provide the upload key, publisher identity,
privacy contact and hosted policy URL, complete the declaration and pass review.
The current release artifacts are unsigned; debug signing is for testing only.
See [release instructions](RELEASE.md) and [Play preparation](PLAY_REVIEW.md).

## 2026-09-16 follow-up

Version 1.0.2-beta minimizes accessibility prefetch/cache use on API 33+ and rejects
consent from stale or backgrounded dialogs. Cache-setup failure stops the service
before any window read; API 31-32 lack this public cache control. This does not remove
Android's broader window-content grant, which remains accurately disclosed.
New host regression cases cover privacy-setup failure and stale consent callbacks.
The owner supplied a Play Protect sensitive-access block on Vivo/Android 16; that
device remains unsupported for the observed file-based installation route pending
platform review. See [PLAY_PROTECT_REVIEW.md](PLAY_PROTECT_REVIEW.md). No successful
installation, cross-app timing, or framework cache enforcement on hardware is claimed.

## 2026-09-17 exit responsiveness and shortcut follow-up

The owner confirmed the six basic setup/Blinkit/Home steps on Vivo V2404 / Android
16, but reported delayed removal and a persistent Android accessibility shortcut.
The shortcut is controlled separately in Android settings: disable only the shortcut,
keeping the service enabled. The app now explains this during setup. It does not
request the accessibility button or change protected shortcut settings.

Version 1.0.3 removes the previous bubble before requesting a different foreground
window's root. A 100ms check runs only while the bubble is visible, to catch focus
updates arriving after the last event. One window ID/package pair is kept in memory
only while that verified supported window remains selected; no node or history is
retained. Hiding/locking/interruption/withdrawal/disconnect clear this pair and stop
the visible check. The event follow-up is 40ms instead of 80ms. Consent/restriction
checks still precede window queries, including timer callbacks. No permissions,
dependencies, persisted data or consent purpose were added.

33 host tests passed, including seven new exit/keyboard/cleanup cases. Both lint
variants, APKs, release bundle, debug signature and all 18 release-inspection checks
passed. New cases verify removal before next-app root reads, a missing-event exit
at the 100ms host scheduler tick, no repeated root reads or reattachment on a stable
window/keyboard, exception handling, and cancellation on consent/permission loss.
This is not a measured 100ms device guarantee. Recheck battery usage during long
shopping sessions and real Home/Recents/keyboard/lock/switching behavior on Vivo.
