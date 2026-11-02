# Supabase setup so BetterChoice India sends real codes

Project: `<your-project-ref>` (https://<your-project-ref>.supabase.co)
App build: `INSTALL-BetterChoice-India-v2.1.0-mvp-live-debug.apk` (connected to this project; no test mode).

Checked on 17 Sep 2026 with the publishable key (read-only): email sign-in **enabled**, phone sign-in **disabled**,
tables were missing at that time (now created, see step 3).

## 1. Email: 6-digit code AND sign-in link (both work)

**a) Authentication → URL Configuration**
- **Site URL:** replace `http://localhost:3000` with `https://<your-project-ref>.supabase.co`
  (Supabase recommends a web URL here; localhost is why the link showed "site can't be reached").
- **Redirect URLs → Add URL:** `com.betterchoice.india://auth-callback`
  (exact value; this lets the email link return to the BetterChoice India app).

**b) Authentication → Email Templates**: paste into **both** *Magic Link* and *Confirm signup*:

```html
<h2>Sign in to BetterChoice India</h2>
<p>Your 6-digit code: <strong style="font-size:22px;letter-spacing:4px">{{ .Token }}</strong></p>
<p>Or, on the phone where you requested it, tap:
   <a href="{{ .ConfirmationURL }}">Sign in to BetterChoice India</a></p>
<p>The code and link work once and expire soon. Never share them with anyone.</p>
```

How the link stays safe: the app sends only a hash of a secret it keeps (PKCE). The link returns a one-time
code to the app, which works only together with that secret on the same phone, within one hour.
Old emails sent before these changes still point to localhost, so request a new code.

Also check **Authentication → Providers → Email → Email OTP Length = 6**.

Supabase's built-in email sender is for testing only. It delivers **only to email addresses of your Supabase
organisation's team members** and only a few emails per hour. For real users, set **Authentication → SMTP Settings**
(e.g. Amazon SES, Resend, Postmark) with a company sender address.

## 2. SMS codes

Dashboard → **Authentication → Providers → Phone**: turn on, choose an SMS provider (Twilio, MessageBird, Vonage or
Textlocal), enter its credentials in the dashboard, and set the OTP message to include `{{ .Code }}`.
In India, carriers deliver OTP SMS only after **DLT registration** of your company, sender ID and template.
Until then, test with email. (The provider's secret credentials go only into the Supabase dashboard, never into the app.)

## 3. Database tables: DONE by Claude (17 Sep 2026, via the Supabase connector)

Applied migrations `betterchoice_mvp` and `security_hardening`, and deployed the `delete-account` function (JWT required).
The `profiles` table is a **merged design compatible with both** BetterChoice India (Claude) and BetterChoice (Codex),
so either app can be tested against this project. Details and remaining dashboard security steps:
`SECURITY_LAYERS_BETTERCHOICE_INDIA.md`.

## 4. Test

1. Open **BetterChoice India** (no red TEST MODE banner should appear).
2. Choose **Email**, enter a team-member email address, tap **Send code by email**.
3. Either type the 6-digit code **or** tap the link in the email on the same phone
   (Chrome may ask "Open with BetterChoice India?"; allow it).
4. Finish the age check, questions and trial.
