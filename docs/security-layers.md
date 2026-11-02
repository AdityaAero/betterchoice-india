# BetterChoice India: security layers

Project `<your-project-ref>` (Mumbai). Status as of 17 Sep 2026.

## A. Applied by Claude and verified

| Layer | What is in place | Evidence |
| --- | --- | --- |
| Row-level security | On and **forced** for `profiles`, `products`, `product_contributions` | Attack simulation, 19/19 checks passed |
| Least privilege | Anonymous: read *verified* products + `match_product` only. Signed-in: own profile (read/create/edit/delete), own contributions (read/create). No one can publish products or approve contributions through the API | Same test |
| Cross-user isolation | User B cannot see, edit or delete user A's profile or contributions; A cannot write as B | Same test |
| Tamper protection | Trial start date cannot be changed or set in the future; `created_at` cannot be rewritten; contributions always `pending`; retailer allowlist; value range checks | Same test |
| Abuse limits | 30 contributions per user per day; 2 s timeout on the anonymous lookup; name length limits | Migration |
| No exposed internals | Trigger/helper functions and Supabase's `rls_auto_enable` removed from the public API | Security advisor: those warnings cleared |
| Deny by default | Future tables/functions in `public` get no API access unless explicitly granted | Migration |
| Account deletion | `delete-account` edge function, JWT verification on, service key stays server-side; data cascades | Deployed (ACTIVE) |
| App side | Only the publishable key in the app; TLS only (cleartext blocked); encrypted session on the phone (Android Keystore); no cloud backup; screen capture blocked on sign-in/profile screens; obscured-tap (tapjacking) rejection; test sign-in compiled out of release builds; no test data left in the database | Build checks 20/20 |

Migrations: `Claude_execution\betterchoice-android\supabase\migrations\` (`betterchoice_mvp`, `security_hardening`).

## B. You must switch on in the Supabase dashboard (not possible through the connector)

1. **CAPTCHA:** *Authentication → Attack Protection* → enable Cloudflare Turnstile (or hCaptcha). It stops bots from triggering thousands of OTP emails/SMS. Tell Claude when it's enabled: the app needs a small hosted challenge page (`BC_CAPTCHA_URL`) before sign-in keeps working.
2. **Rate limits:** *Authentication → Rate Limits* → keep OTP sends and verifications low (e.g. OTPs per hour per IP, token verifications per 5 min).
3. **OTP settings:** *Authentication → Providers → Email* (and Phone): OTP length **6**, expiry **600 seconds** or less.
4. **Email template:** include `{{ .Token }}` (see `SUPABASE_SETUP_FOR_BETTERCHOICE_INDIA.md`).
5. **Custom SMTP** with SPF/DKIM on a company domain, so codes aren't spoofable or sent to spam.
6. **Leaked password protection:** *Authentication → Attack Protection* → on (the one remaining advisor warning; BetterChoice itself uses no passwords).
7. **Site URL / Redirect URLs:** *Authentication → URL Configuration* → remove anything you don't use (blocks open-redirect tricks with email links).
8. **Account security:** turn on **MFA** for every Supabase organisation member; keep only needed members; never share the service-role key or database password.
9. **Database access:** *Settings → Database* → enforce SSL and add **Network Restrictions** (allow only your IPs for direct Postgres connections).
10. **Backups:** Free plan has no backups; use Pro (daily backups) before real users.
11. **SMS (when enabled):** add DLT-registered sender and template; set SMS rate limits.

## C. Limits: what security cannot promise

- No system is attack-proof. These layers remove the common attacks (data leakage between users, forged records, API abuse, credential exposure), but new risks need periodic review. Re-run the Supabase Security Advisor after every schema change.
- The anonymous product lookup is intentionally public (product nutrition is not personal data).
- Anyone can decompile an Android app. That's why the app holds only the public key and all protection is enforced on the server.
- A penetration test by an independent firm is recommended before a public launch.
