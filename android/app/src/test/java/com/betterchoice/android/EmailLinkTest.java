package com.betterchoice.android;

import android.content.Context;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class EmailLinkTest {
    private static final String OWN = BuildConfig.APPLICATION_ID + "://auth-callback";

    @Test public void challengeMatchesRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            EmailLink.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test public void verifiersAreLongRandomAndUrlSafe() {
        String a = EmailLink.newVerifier(), b = EmailLink.newVerifier();
        assertNotEquals(a, b);
        assertTrue(a.matches("[A-Za-z0-9_-]{64}"));
    }

    @Test public void redirectUsesThisBuildsOwnScheme() {
        assertEquals(OWN, EmailLink.redirectUrl());
    }

    @Test public void onlyOwnWellFormedLinksYieldACode() {
        String code = "3f2c9a1e-7b4d-4c1a-9e2f-5a6b7c8d9e0f";
        assertEquals(code, EmailLink.authCode(Uri.parse(OWN + "?code=" + code)));
        assertNull("Other app's scheme", EmailLink.authCode(Uri.parse("com.evil.app://auth-callback?code=" + code)));
        assertNull("Wrong host", EmailLink.authCode(Uri.parse(BuildConfig.APPLICATION_ID + "://evil?code=" + code)));
        assertNull("Extra path", EmailLink.authCode(Uri.parse(OWN + "/steal?code=" + code)));
        assertNull("Web URL", EmailLink.authCode(Uri.parse("https://localhost:3000/?code=" + code)));
        assertNull("Injected characters", EmailLink.authCode(Uri.parse(OWN + "?code=abc%22%3Cscript%3E1234567890")));
        assertNull("Too short", EmailLink.authCode(Uri.parse(OWN + "?code=abc")));
        assertNull(EmailLink.authCode(null));
    }

    @Test public void expiredOrErrorLinksAreExplained() {
        assertNotNull(EmailLink.errorMessage(Uri.parse(OWN + "#error=access_denied&error_code=otp_expired")));
        assertNull(EmailLink.errorMessage(Uri.parse(OWN + "?code=3f2c9a1e-7b4d-4c1a-9e2f-5a6b7c8d9e0f")));
    }

    @Test public void verifierIsSingleUseAndExpires() {
        Context context = RuntimeEnvironment.getApplication();
        EmailLink.remember(context, "verifier-one");
        assertEquals("verifier-one", EmailLink.consume(context));
        assertNull("Second use must fail", EmailLink.consume(context));

        EmailLink.remember(context, "old");
        context.getSharedPreferences("pending_email_link", Context.MODE_PRIVATE).edit()
            .putLong("created", System.currentTimeMillis() - EmailLink.MAX_AGE_MS - 1000).commit();
        assertNull("Older than an hour", EmailLink.consume(context));
    }
}
