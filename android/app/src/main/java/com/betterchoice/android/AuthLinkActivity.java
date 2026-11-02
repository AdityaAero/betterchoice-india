package com.betterchoice.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Receives the email sign-in link (com.betterchoice.india://auth-callback?code=...). The code is
 * exchanged only together with the verifier stored when this phone requested the email, so a
 * forged or intercepted link cannot sign anyone in. No other extras or URI data are trusted.
 */
public final class AuthLinkActivity extends Activity {
    private TextView message;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setHideOverlayWindows(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER);
        int pad = UiKit.dp(this, 28); body.setPadding(pad, pad, pad, pad); body.setBackgroundColor(UiKit.paper(this));
        message = UiKit.text(this, "Signing you in…", 18, UiKit.ink(this));
        message.setGravity(Gravity.CENTER);
        body.addView(message);
        setContentView(body);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        Uri uri = intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) ? null : intent.getData();
        String error = EmailLink.errorMessage(uri);
        String code = EmailLink.authCode(uri);
        if (error != null || code == null) { fail(error != null ? error : "This link isn't a BetterChoice sign-in link."); return; }
        AccountStore account = AccountStore.get(this);
        if (account.hasSession()) { continueOnboarding(); return; }
        String verifier = EmailLink.consume(this);
        if (verifier == null) {
            fail("Open the link on the same phone where you requested the code, within an hour. You can also type the 6-digit code instead.");
            return;
        }
        AuthController.get(this).exchangeEmailLink(code, verifier, problem -> {
            if (isDestroyed() || isFinishing()) return;
            if (problem == null) continueOnboarding();
            else fail(problem);
        });
    }

    private void continueOnboarding() {
        startActivity(new Intent(this, OnboardingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void fail(String text) {
        message.setText(text);
        message.postDelayed(() -> {
            if (isDestroyed() || isFinishing()) return;
            continueOnboarding();
        }, 3500);
    }
}
