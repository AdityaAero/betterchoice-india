package com.betterchoice.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Sign-in links use PKCE: the app keeps a random secret (verifier) and sends only its SHA-256 hash.
 * A link intercepted by another app is useless without the verifier, which never leaves this phone.
 */
final class EmailLink {
    static final String HOST = "auth-callback";
    static final long MAX_AGE_MS = 60 * 60 * 1000;
    private static final String FILE = "pending_email_link";

    /** Custom scheme = application ID, so the Claude and Codex builds never receive each other's links. */
    static String redirectUrl() { return BuildConfig.APPLICATION_ID + "://" + HOST; }

    static String newVerifier() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return base64Url(bytes);
    }

    static String challenge(String verifier) {
        try {
            return base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** Remembers the verifier for the most recent email request (one pending sign-in at a time). */
    static void remember(Context context, String verifier) {
        prefs(context).edit().clear().putString("verifier", verifier).putLong("created", System.currentTimeMillis()).commit();
    }

    /** Returns the verifier once, if it is still fresh, and deletes it. */
    static String consume(Context context) {
        SharedPreferences prefs = prefs(context);
        String verifier = prefs.getString("verifier", null);
        long created = prefs.getLong("created", 0);
        prefs.edit().clear().commit();
        long age = System.currentTimeMillis() - created;
        return verifier != null && age >= 0 && age <= MAX_AGE_MS ? verifier : null;
    }

    static void clear(Context context) { prefs(context).edit().clear().commit(); }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Accepts only our own scheme/host with a single well-formed code; anything else is ignored. */
    static String authCode(Uri uri) {
        if (uri == null || !BuildConfig.APPLICATION_ID.equals(uri.getScheme()) || !HOST.equals(uri.getHost())) return null;
        if (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) return null;
        String code;
        try { code = uri.getQueryParameter("code"); } catch (RuntimeException malformed) { return null; }
        return code != null && code.matches("[A-Za-z0-9-]{16,128}") ? code : null;
    }

    /** Supabase reports expired or reused links as error parameters in the query or fragment. */
    static String errorMessage(Uri uri) {
        if (uri == null) return null;
        String raw = (uri.getEncodedQuery() == null ? "" : uri.getEncodedQuery()) + "&" + (uri.getEncodedFragment() == null ? "" : uri.getEncodedFragment());
        if (!raw.contains("error")) return null;
        if (raw.contains("otp_expired") || raw.contains("expired")) return "This sign-in link has expired or was already used. Request a new code in the app.";
        return "This sign-in link could not be used. Request a new code in the app.";
    }

    private EmailLink() {}
}
