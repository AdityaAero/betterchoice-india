package com.betterchoice.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.accessibility.AccessibilityManager;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Home after onboarding: bubble setup, how ratings work, trial and account controls. */
public final class MainActivity extends Activity {
    private TextView status;
    private ConsentStore consent;
    private AlertDialog consentDialog;
    private boolean resumed;
    private AccountStore account;
    private final SafeTouches.GestureGuard touches = new SafeTouches.GestureGuard();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 500); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Never read launch extras or URI data. This is not a command endpoint.
        getWindow().setHideOverlayWindows(true);
        account = AccountStore.get(this);
        if (!requireAccount()) return;
        consent = new ConsentStore(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 24);
        body.setPadding(pad, pad, pad, pad);
        body.setBackgroundColor(UiKit.paper(this));
        body.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(pad + bars.left, bars.top + pad, pad + bars.right, bars.bottom + pad);
            return insets;
        });
        addText(body, "BC  /  BETTERCHOICE INDIA " + BuildConfig.VERSION_NAME + (account.testMode() ? "  ·  TEST MODE" : ""), 13);
        TextView heading = addText(body, "Your basket.\nYour better choices.", 32);
        heading.setTypeface(Typeface.create("serif", Typeface.BOLD));
        addText(body, "Signed in: " + account.label() + "  ·  " + trialLine(), 14);
        status = addText(body, "", 17);
        Button enable = UiKit.button(this, "", true);
        enable.setText(R.string.setup_bubble);
        enable.setOnClickListener(v -> showConsent());
        body.addView(enable);
        UiKit.gap(body, 12);
        Button settings = UiKit.button(this, "", false);
        settings.setText(R.string.manage_access);
        settings.setOnClickListener(v -> openSettings());
        body.addView(settings);

        addText(body, "How the bubble works", 22).setTypeface(Typeface.create("serif", Typeface.BOLD));
        addText(body, "Open a supported app: a see-through blue BC bubble appears, tinted to stand out from that app without covering products. " +
            "Open a food product: BetterChoice reads the nutrition shown, then the pack photo, then the BetterChoice database, and turns the bubble into a rating. Tap it for the reasons.", 16);
        legendRow(body, NutriGrade.Band.A);
        legendRow(body, NutriGrade.Band.B);
        legendRow(body, NutriGrade.Band.C);
        addText(body, "No rating found? Tap the bubble to skip that item, or add its label details to help make India eat healthier.", 15);
        addText(body, "Ratings use the published Nutri-Score (2017) nutrient points per 100 g/ml, grouped into 3 bands. They are a guide, not medical advice.", 13)
            .setTextColor(UiKit.muted(this));

        addText(body, "Know your label", 22).setTypeface(Typeface.create("serif", Typeface.BOLD));
        addText(body, "Three short label-reading cards based on your selected goal and food preferences.", 15);
        body.addView(LearnCards.view(this, account.profile()));

        addText(body, "Works with", 20).setTypeface(Typeface.create("serif", Typeface.BOLD));
        addText(body, "Blinkit · Zepto · Swiggy · Swiggy Instamart · Zomato · Flipkart (including Minutes)", 16);
        addText(body, getString(R.string.shortcut_title), 20).setTypeface(Typeface.create("serif", Typeface.BOLD));
        addText(body, getString(R.string.shortcut_help), 15);

        Button off = UiKit.button(this, "", false);
        off.setText(R.string.turn_off);
        off.setOnClickListener(v -> {
            BubbleService.turnOffConnectedService();
            boolean saved = consent.revoke();
            refreshStatus();
            if (!saved) Toast.makeText(this, R.string.consent_save_failed, Toast.LENGTH_LONG).show();
        });
        body.addView(off);
        UiKit.gap(body, 12);
        Button edit = UiKit.button(this, "Edit or clear my profile", false);
        edit.setOnClickListener(v -> startActivity(new Intent(this, OnboardingActivity.class).setAction(OnboardingActivity.EDIT_PROFILE)));
        body.addView(edit); UiKit.gap(body, 12);
        Button signOut = UiKit.button(this, "Sign out", false);
        signOut.setOnClickListener(v -> { AuthController.get(this).signOut(); requireAccount(); });
        body.addView(signOut); UiKit.gap(body, 12);
        Button delete = UiKit.button(this, "Delete my account", false);
        delete.setOnClickListener(v -> confirmDeletion()); body.addView(delete);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
    }
    private String trialLine() {
        String started = account.profile().trialStartedAt;
        try {
            Instant end = Instant.parse(started).atZone(ZoneId.systemDefault()).plusMonths(3).toInstant();
            return "Free trial until " + DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(end);
        } catch (RuntimeException unknown) { return "Free trial active"; }
    }
    private void legendRow(LinearLayout parent, NutriGrade.Band band) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = UiKit.text(this, band.name(), 16, band == NutriGrade.Band.B ? UiKit.ON_BAND_B : Color.WHITE);
        chip.setGravity(Gravity.CENTER); chip.setTypeface(null, Typeface.BOLD);
        chip.setBackground(UiKit.shape(this, BubbleStyle.argb(0xFF, BubbleStyle.bandColor(band)), 0, 20));
        row.addView(chip, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));
        TextView label = UiKit.text(this, "   " + BubbleStyle.meaning(band), 15, UiKit.ink(this));
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = UiKit.dp(this, 8);
        parent.addView(row, params);
    }
    private TextView addText(LinearLayout parent, String text, int size) {
        TextView view = UiKit.text(this, text, size, UiKit.ink(this));
        view.setPadding(0, 16, 0, 16); parent.addView(view); return view;
    }
    private void showConsent() {
        if (!resumed || !requireAccount() || (consentDialog != null && consentDialog.isShowing())) return;
        if (PlatformAccess.check(this) != PlatformAccess.State.AVAILABLE) { refreshStatus(); return; }
        AlertDialog dialog = new AlertDialog(this) {
            private final SafeTouches.GestureGuard dialogTouches = new SafeTouches.GestureGuard();
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (dialogTouches.reject(event.getActionMasked(), event.getFlags())) return true;
                return super.dispatchTouchEvent(event);
            }
        };
        dialog.setTitle(R.string.consent_title);
        consentDialog = dialog;
        dialog.setOnDismissListener(ignored -> { if (consentDialog == dialog) consentDialog = null; });
        dialog.setMessage(getString(R.string.consent_disclosure));
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel), (d, which) -> d.dismiss());
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.consent_agree), (d, which) -> {});
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setHideOverlayWindows(true);
            Button agree = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            SafeTouches.protect(agree);
            agree.setOnClickListener(v -> {
                // A stale dialog callback must never authorize access after leaving setup.
                if (!resumed || !account.canUseBubble() || consentDialog != dialog || !dialog.isShowing()) return;
                if (PlatformAccess.check(this) != PlatformAccess.State.AVAILABLE) { dialog.dismiss(); refreshStatus(); return; }
                if (!consent.accept()) {
                    consent.revoke();
                    Toast.makeText(this, R.string.consent_save_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                openSettings();
            });
        });
        dialog.show();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (touches.reject(event.getActionMasked(), event.getFlags())) return true;
        return super.dispatchTouchEvent(event);
    }
    private void openSettings() {
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (ActivityNotFoundException | SecurityException blocked) {
            status.setText(R.string.status_settings_restricted);
            Toast.makeText(this, R.string.status_settings_restricted, Toast.LENGTH_LONG).show();
        }
    }
    @Override public void onResume() {
        super.onResume();
        if (!requireAccount()) return;
        AuthController.get(this).resume();
        resumed = true;
        handler.removeCallbacks(refresh);
        refresh.run();
    }
    @Override public void onPause() {
        resumed = false;
        handler.removeCallbacks(refresh);
        if (consentDialog != null) consentDialog.dismiss();
        super.onPause();
    }
    private void refreshStatus() {
        if (!requireAccount() || status == null) return;
        PlatformAccess.State access = PlatformAccess.check(this);
        if (access != PlatformAccess.State.AVAILABLE) {
            status.setText(access == PlatformAccess.State.RESTRICTED ? R.string.status_restricted : R.string.status_unknown);
            return;
        }
        if (!consent.isCurrent()) { status.setText(R.string.status_setup); return; }
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        boolean enabled = false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(getPackageName()) &&
                info.getResolveInfo().serviceInfo.name.equals(BubbleService.class.getName())) enabled = true;
        }
        status.setText(!enabled ? R.string.status_permission_needed :
            BubbleService.isConnected() ? R.string.status_enabled : R.string.status_disconnected);
    }
    private boolean requireAccount() {
        if (account != null && account.canUseBubble()) return true;
        if (!isFinishing()) { startActivity(new Intent(this, OnboardingActivity.class)); finish(); }
        return false;
    }
    private void confirmDeletion() {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Delete your account?")
            .setMessage("This permanently removes your account and saved profile, signs you out and turns off the bubble. An internet connection is required.")
            .setNegativeButton("Keep account", null).setPositiveButton("Delete account", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setHideOverlayWindows(true);
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE); SafeTouches.protect(confirm);
            confirm.setOnClickListener(v -> {
                confirm.setEnabled(false); confirm.setText("Deleting…");
                AuthController.get(this).deleteAccount(message -> {
                    if (isDestroyed() || isFinishing()) return;
                    dialog.dismiss();
                    if (message == null) requireAccount();
                    else Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            });
        }); dialog.show();
    }
}
