# Building and signing the beta

Toolchain: JDK 17, Gradle 9.5.0, AGP 9.3.2, compile/target API 37.0, minimum API 31,
Build Tools 36.0.0. Android's SDK manager names the platform `platforms;android-37.0`.
Keep tools in ignored `.tools/` or install them through Android Studio.

## Normal checks

```powershell
powershell -ExecutionPolicy Bypass -File .\build-local.ps1
python tools/verify_release.py
python tools/test_build_controls.py
```

The wrapper verifies the downloaded Gradle ZIP with its publisher SHA256. The local
build helper uses the wrapper, not an unverified alternate Gradle installation.
Gradle verifies dependency artifacts strictly against committed metadata and locks
dependency versions. Robolectric's Android test runtime is also pinned and verified;
Robolectric runs offline and is absent from the app's runtime dependencies.
The build/test security overrides and their advisory review are recorded in
SECURITY_REVIEW.md; the required Kotlin build-tool family is pinned to patched
2.4.20 even though application Kotlin compilation is disabled.

Without signing variables, release APK/AAB artifacts are unsigned. This is intentional;
the debug key is never selected as a production fallback. Debug APKs remain for local
testing only. Release builds disable debugging, shrink code/resources and remove logs.

## Owner signing configuration

For local DEBUG builds only, `BC_DEBUG_KEYSTORE` may select an existing Android debug
keystore (standard `androiddebugkey` alias and debug credentials). `build-local.ps1`
defaults to the original `%USERPROFILE%\.android\debug.keystore` when it exists,
so changing `ANDROID_USER_HOME` does not silently change the test signing identity.
This setting never signs release artifacts. Private keys are excluded from exports;
a build on another computer may therefore require a separate test installation.
Android updates require the installed and replacement APKs to have compatible signing
certificates. Do not uninstall an existing app automatically to work around a mismatch.

Supply these four environment variables using a secure secret store or local process
environment (not a committed script, command history, or repository file):

- `BC_KEYSTORE_PATH`: absolute path to the owner's upload keystore outside the repo.
- `BC_KEYSTORE_PASSWORD`: keystore password.
- `BC_KEY_ALIAS`: upload key alias.
- `BC_KEY_PASSWORD`: key password.

All four must be present or all absent; partial configuration fails the build. Run
`gradlew.bat bundleRelease` for the Play bundle or `gradlew.bat assembleRelease` for
an APK. Enable Play App Signing in the owner's Console. Keep recovery material and
upload credentials with the owner; never disclose them in logs or check them in.
Verify a signed APK with SDK `apksigner verify --verbose --print-certs <apk>` and a
signed bundle with JDK `jarsigner -verify <aab>`. Compare the certificate fingerprint
to the owner's intended upload key before distribution.

## Dependency changes

Only when intentionally updating dependencies, regenerate metadata and locks for
every test/release variant, then run `python tools/audit_dependencies.py` with network
access. It compares local SHA256 entries with publisher repository checksum files
(SHA256 where available; SHA1 fallback is documented per artifact) and queries OSV
for public Maven coordinates. Review every mismatch, unverified artifact and advisory
before committing metadata. Do not automatically trust newly generated checksums.

Publisher sidecars confirm repository consistency, not an independent guarantee
against repository compromise. Preserve the reviewed metadata in code review.
Repeat vulnerability review before each beta upload and monthly while maintaining v1.
Do not auto-upgrade dependencies or publish builds from audit results.

## Release gates

Run tests/lint and release inspection with strict verification. Review the applicable
MASVS controls and evidence in SECURITY_REVIEW.md. Finish physical-device and
adversarial cross-app tests in DEVICE_TESTS.md before accepting v1. Network-dependent
future features require a new threat review, disclosure and privacy-policy update.
