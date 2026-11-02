# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Use GitHub's
[private vulnerability reporting](../../security/advisories/new) for this repository, with:

- what is affected (file, screen or endpoint) and the version or commit;
- steps to reproduce, and the impact you expect;
- any suggested fix.

You can expect an acknowledgement within 7 days. Please allow reasonable time for a fix before any disclosure.

## Supported versions

| Version | Supported |
|---|---|
| 3.9.x (`android/`, on-device) | ✅ |
| 4.0.x (`android-db/`, database edition) | ✅ |
| < 3.9 | ❌ |

## How the app is designed to be safe

- **No secrets in the repository.** The only backend settings an app build takes are *public* client values
  (Supabase URL and publishable key), injected at build time from environment variables. The build refuses a key
  that is not a publishable key. Server keys exist only inside Supabase and are read there from the environment.
- **Health data stays on the phone.** Profile, conditions, allergens and goals are stored on the device and
  are not uploaded.
- **Narrow screen access.** The accessibility service reacts only to an allow-list of shopping apps, shows a
  versioned consent disclosure, and stops when consent is withdrawn. Screenshots are never stored.
- **Backend (optional).** Row-level security on every table; public lookups are read-only `SECURITY DEFINER`
  functions; account deletion is an authenticated edge function.
- **Supply chain.** Gradle dependency verification and lockfiles; reviewed dependency advisories
  ([`android/security/SECURITY_REVIEW.md`](android/security/SECURITY_REVIEW.md)).

More detail: [docs/security-layers.md](docs/security-layers.md).
