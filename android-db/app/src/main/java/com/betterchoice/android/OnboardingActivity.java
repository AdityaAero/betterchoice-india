package com.betterchoice.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.time.Instant;

/**
 * Onboarding: verified sign-in -> 18+ confirmation -> six optional screens (skip any or all)
 * -> free-trial terms -> bubble setup. Every profile answer is optional. Never reads launch credentials.
 */
public final class OnboardingActivity extends Activity {
    static final String EDIT_PROFILE = "com.betterchoice.android.EDIT_PROFILE";
    enum Page { SIGN_IN, CODE, AGE, UNDER_AGE, INTRO, QUESTION, TRIAL, RESTORE }
    private static final class State {
        WeakReference<OnboardingActivity> owner = new WeakReference<>(null);
        Page page = Page.SIGN_IN;
        boolean phone = true, busy;
        String country = "India", dial = "91", raw = "", identity = "", code = "", error = "", info = "", lastTriedCode = "", clipboardCodeAtSend = "";
        long resendAt;
        int step;
        Profile draft = new Profile();
        /**
         * Which screens this run will show, decided once when the form opens. Recomputing it on
         * every keystroke would make screens vanish from under the person answering them.
         */
        java.util.List<Integer> pending = new java.util.ArrayList<>();
    }
    private State state;
    private AccountStore account;
    private AuthController auth;
    private LinearLayout body;
    private TextView error;
    private Button resend, sendButton;
    private EditText identityInput, codeInput, answerInput;
    private final SafeTouches.GestureGuard guard = new SafeTouches.GestureGuard();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long seconds = Math.max(0, (state.resendAt - SystemClock.elapsedRealtime() + 999) / 1000);
            // One code per wait period: grey and disabled until it expires, then green again.
            if (resend != null) {
                resend.setText(seconds > 0 ? "Resend a new code in " + seconds + "s" : "Send a new code  →");
                resend.setEnabled(seconds == 0 && !state.busy);
            }
            if (sendButton != null) {
                sendButton.setText(seconds > 0 ? "Code sent · resend in " + seconds + "s" : (state.phone ? "Send code by SMS  →" : "Send code by email  →"));
                sendButton.setEnabled(seconds == 0 && !state.busy);
            }
            if ((resend != null || sendButton != null) && seconds > 0) handler.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setHideOverlayWindows(true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        account = AccountStore.get(this); auth = AuthController.get(this);
        Object retained = getLastNonConfigurationInstance();
        state = retained instanceof State ? (State) retained : new State();
        state.owner = new WeakReference<>(this);
        boolean fresh = retained == null;
        if (saved == null && fresh && Intent.ACTION_MAIN.equals(getIntent().getAction())) {
            // Brand moment on every cold launch from the app icon, then continue exactly as before.
            splashShowing = true;
            setContentView(Splash.view(this));
            handler.postDelayed(() -> afterSplash(true), Splash.DURATION_MS);
            return;
        }
        afterSplash(fresh);
    }
    private boolean splashShowing;
    private void afterSplash(boolean fresh) {
        if (isFinishing() || isDestroyed()) return;
        splashShowing = false;
        if (fresh && account.hasSession()) { state.page = Page.RESTORE; restore(); return; }
        render();
    }
    boolean splashShowing() { return splashShowing; }
    /** Host tests only: renders the code screen without contacting a backend. */
    void showCodePageForTests() { state.identity = "tester@betterchoice.test"; state.phone = false; state.page = Page.CODE; render(); }
    Page page() { return state.page; }
    @Override public Object onRetainNonConfigurationInstance() { return state; }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return guard.reject(event.getActionMasked(), event.getFlags()) || super.dispatchTouchEvent(event);
    }
    @Override public void onResume() { super.onResume(); handler.removeCallbacks(tick); tick.run(); if (state.page != Page.RESTORE) auth.resume(); }
    @Override public void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override public void onDestroy() { if (state != null && state.owner.get() == this) state.owner.clear(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void render() {
        if (isFinishing() || isDestroyed()) return;
        handler.removeCallbacks(tick);
        resend = null; sendButton = null; identityInput = null; codeInput = null; answerInput = null;
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setBackgroundColor(UiKit.paper(this));
        int pad = UiKit.dp(this, 24); body.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(UiKit.paper(this));
        scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        setContentView(scroll);
        // Screen capture is blocked only where a one-time code can appear, so the rest of the app
        // (including the bubble) can be screen-shared or recorded for demos and support.
        if (state.page == Page.CODE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = UiKit.text(this, "BC", 17, UiKit.onGreen(this)); logo.setGravity(Gravity.CENTER); logo.setTypeface(null, Typeface.BOLD);
        logo.setBackground(UiKit.shape(this, UiKit.green(this), 0, 14)); brand.addView(logo, new LinearLayout.LayoutParams(UiKit.dp(this, 44), UiKit.dp(this, 44)));
        TextView wordmark = UiKit.text(this, "  betterchoice india", 22, UiKit.ink(this)); wordmark.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); brand.addView(wordmark);
        body.addView(brand); UiKit.gap(body, 20);
        if (TestMode.enabled() || TestMode.bypassActive(this)) {
            TextView banner = text(TestMode.bypassActive(this)
                ? "TEST SIGN-IN · debug build · this account and its answers stay on this phone"
                : "TEST MODE · debug build · no real codes are sent · use code " + TestMode.CODE, 12, android.graphics.Color.WHITE);
            banner.setBackground(UiKit.shape(this, UiKit.warning(this), 0, 10));
            banner.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 8), UiKit.dp(this, 12), UiKit.dp(this, 8));
            UiKit.gap(body, 16);
        }
        switch (state.page) {
            case SIGN_IN: signInPage(); break;
            case CODE: codePage(); break;
            case AGE: agePage(); break;
            case UNDER_AGE: underAgePage(); break;
            case INTRO: introPage(); break;
            case QUESTION: questionPage(); break;
            case TRIAL: trialPage(); break;
            case RESTORE: restorePage(); break;
        }
        UiKit.gap(body, 20);
        if (state.busy) text("One moment…", 14, UiKit.muted(this));
        error = text(state.error, 14, android.graphics.Color.rgb(158, 49, 37));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(state.error.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private TextView text(String value, int size, int color) { TextView t = UiKit.text(this, value, size, color); body.addView(t); return t; }
    private void title(String eyebrow, String heading, String description) {
        TextView tag = text(eyebrow, 12, UiKit.green(this)); tag.setLetterSpacing(.12f); tag.setTypeface(null, Typeface.BOLD);
        UiKit.gap(body, 12); TextView h = text(heading, 32, UiKit.ink(this)); h.setTypeface(Typeface.create("serif", Typeface.BOLD));
        h.setAccessibilityHeading(true); UiKit.gap(body, 12); text(description, 16, UiKit.muted(this)); UiKit.gap(body, 22);
    }
    private Button action(String label, boolean primary, Runnable click) {
        Button b = UiKit.button(this, label, primary); b.setEnabled(!state.busy);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = UiKit.dp(this, 12); body.addView(b, params);
        b.setOnClickListener(view -> { if (!state.busy) { state.error = ""; click.run(); } }); return b;
    }
    private void signInPage() {
        title("CHOOSE RIGHT. EAT RIGHT.", "Better choices,\nright where you shop.",
            "BetterChoice rates packaged food on Blinkit, Zepto, Swiggy, Instamart, Zomato and Flipkart Minutes, in a glance. Start with a quick, secure sign-in.");
        body.addView(new UiKit.WelcomeArt(this), new LinearLayout.LayoutParams(-1, UiKit.dp(this, 150))); UiKit.gap(body, 22);
        LinearLayout tabs = new LinearLayout(this);
        for (boolean phone : new boolean[]{true, false}) {
            Button tab = UiKit.button(this, phone ? "Mobile number" : "Email", state.phone == phone);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(0, 0, UiKit.dp(this, 6), 0); tabs.addView(tab, lp);
            tab.setEnabled(!state.busy);
            tab.setOnClickListener(v -> { state.phone = phone; state.raw = ""; state.error = ""; render(); });
        }
        body.addView(tabs); UiKit.gap(body, 16);
        if (state.phone) {
            action(state.country + "  +" + state.dial + "  ▾", false, () -> CountryPicker.show(this, (country, code) -> { state.country = country; state.dial = code; render(); }));
            UiKit.gap(body, 12);
        }
        identityInput = UiKit.input(this, state.phone ? "Mobile number" : "you@example.com", state.phone ? InputType.TYPE_CLASS_PHONE : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        identityInput.setContentDescription(state.phone ? "Mobile number without country code" : "Email address");
        identityInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(state.phone ? 20 : 254)});
        identityInput.setText(state.raw); body.addView(identityInput); watch(identityInput, value -> state.raw = value);
        if (state.phone) { UiKit.gap(body, 8); text("India is selected by default. Enter your number without the country code (10 digits for India).", 12, UiKit.muted(this)); }
        sendButton = action(state.phone ? "Send code by SMS  →" : "Send code by email  →", true, this::sendCode); tick.run();
        UiKit.gap(body, 16); text("No password. We send a one-time code.", 13, UiKit.muted(this));
        action("How we use your information", false, this::privacy);
        if (BuildConfig.DEBUG) {
            // Always offered in debug builds. This used to hide itself once a test session existed,
            // which made it look removed and left the app quietly talking to the local stand-in.
            UiKit.gap(body, 18);
            text("Testing only. Not for customers:", 12, UiKit.muted(this));
            action(TestMode.BYPASS_LABEL, false, () -> {
                state.identity = TestMode.TESTER; state.phone = false;
                state.busy = true; render();
                auth.startTestSession(callback(state, Op.VERIFIED));
            });
            if (TestMode.bypassActive(this)) text("A test session is active. Sending a real code ends it.", 12, UiKit.muted(this));
        }
        if (!AuthController.available()) { UiKit.gap(body, 14); text("Sign-in is being connected for this beta. This build cannot send codes yet.", 13, UiKit.muted(this)); }
    }
    private void sendCode() {
        if (!AuthController.available()) { showError("Sign-in is not available in this build yet. The beta owner needs to connect the verification service."); return; }
        if (SystemClock.elapsedRealtime() < state.resendAt) { showError("Please wait for the resend timer."); return; }
        try { state.identity = state.phone ? AuthInput.phone(state.dial, state.raw) : AuthInput.email(state.raw); }
        catch (IllegalArgumentException invalid) { showError(invalid.getMessage()); return; }
        if (!TestMode.enabled() && !BuildConfig.CAPTCHA_URL.isBlank()) {
            CaptchaDialog.show(this, token -> { if (token == null) showError("Security check cancelled. You can try again."); else sendWithCaptcha(token); });
        } else sendWithCaptcha("");
    }
    private void sendWithCaptcha(String token) {
        if (isDestroyed() || isFinishing()) return;
        // Remember any code already on the clipboard so an old code is never auto-submitted.
        String existing = clipboardCode();
        state.clipboardCodeAtSend = existing == null ? "" : existing;
        state.busy = true; state.resendAt = SystemClock.elapsedRealtime() + 60000; render();
        auth.send(state.identity, state.phone, token, callback(state, Op.SENT));
    }
    private void codePage() {
        title("ONE SMALL CHECK", "Check your " + (state.phone ? "messages." : "inbox."), "Enter the 6-digit code sent to " + AuthInput.mask(state.identity, state.phone) + "." +
            (state.phone ? "" : " Or tap the sign-in link in that email on this phone."));
        codeInput = UiKit.input(this, "000000", InputType.TYPE_CLASS_NUMBER); codeInput.setTextSize(29); codeInput.setLetterSpacing(.22f);
        codeInput.setGravity(Gravity.CENTER); codeInput.setContentDescription("Six-digit verification code");
        // Let the keyboard / autofill service suggest the code it sees in an SMS or notification.
        codeInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        codeInput.setAutofillHints("smsOTPCode", "oneTimeCode");
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)}); codeInput.setText(state.code);
        body.addView(codeInput);
        // Verifies automatically as soon as 6 digits are present: typed, pasted, suggested or autofilled.
        watch(codeInput, value -> { state.code = value; if (AuthInput.otp(value)) handler.post(() -> verifyCode(false)); });
        if (!state.info.isEmpty()) { UiKit.gap(body, 8); text(state.info, 13, UiKit.green(this)); }
        action("Verify & continue  →", true, () -> verifyCode(true));
        if (!state.phone) action("Open email app", false, this::openEmailApp);
        action("Paste code", false, () -> fillFromClipboard(true));
        resend = action("Send a new code  →", true, this::sendCode); tick.run();
        action("Change " + (state.phone ? "number" : "email"), false, () -> { state.code = ""; state.page = Page.SIGN_IN; render(); });
        UiKit.gap(body, 20); text("Never share this code with anyone.", 13, UiKit.muted(this));
    }
    /** One verification per distinct code, so a wrong code is not resubmitted in a loop. */
    private void verifyCode(boolean explicit) {
        if (state.page != Page.CODE || state.busy) return;
        if (!AuthInput.otp(state.code)) { if (explicit) showError("Enter the 6-digit code from your message."); return; }
        if (!explicit && state.code.equals(state.lastTriedCode)) return;
        state.lastTriedCode = state.code;
        state.busy = true; render();
        auth.verify(state.identity, state.phone, state.code, callback(state, Op.VERIFIED));
    }

    /**
     * Returning from the email app with a copied code fills it in. The clipboard is read only on this
     * screen, only a 6-digit number is taken, and nothing is stored. Android shows its own "pasted" notice.
     */
    private void fillFromClipboard(boolean explicit) {
        if (state.page != Page.CODE || state.busy) return;
        String code = clipboardCode();
        if (code == null) { if (explicit) showError("No 6-digit code found. Copy the code from the email, then tap Paste code."); return; }
        if (!explicit && (code.equals(state.lastTriedCode) || code.equals(state.code) || code.equals(state.clipboardCodeAtSend))) return;
        state.code = code;
        state.info = "Code filled in from your clipboard.";
        if (codeInput != null) codeInput.setText(code); else verifyCode(false);
    }

    private String clipboardCode() {
        android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
        try {
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                return OtpCode.find(clipboard.getPrimaryClip().getItemAt(0).coerceToText(this));
            }
        } catch (RuntimeException unavailable) { /* No clipboard access right now. */ }
        return null;
    }

    private void openEmailApp() {
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException none) {
            showError("No email app found. Open your email and copy the 6-digit code.");
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Clipboard access is allowed only while the app has focus (Android 10+).
        if (hasFocus && state != null && state.page == Page.CODE) fillFromClipboard(false);
    }

    private void agePage() {
        title("VERIFIED. ONE QUICK CHECK", "Are you 18\nor older?", "BetterChoice's food ratings and profile are designed for adults. We don't ask for your date of birth.");
        action("Yes, I'm 18 or older  →", true, () -> {
            state.draft.adultConfirmed = true;
            // Returning users who already finished the form only need their confirmation saved.
            if (state.draft.complete && state.draft.trialStarted()) { save(false); return; }
            state.page = nextPage(state.draft, false);
            render();
        });
        action("No, I'm under 18", false, () -> { state.page = Page.UNDER_AGE; render(); });
    }
    private void underAgePage() {
        title("THANKS FOR BEING HONEST", "BetterChoice is for\nadults right now.", "We can't create a profile for anyone under 18. You can sign out now; nothing else will be collected.");
        action("Sign out", true, () -> { auth.signOut(); state = new State(); state.owner = new WeakReference<>(this); render(); });
        action("I made a mistake, go back", false, () -> { state.page = Page.AGE; render(); });
    }
    /**
     * The doorway to the form. It used to state the number of questions three times over, once
     * wrongly, and explain optionality twice; now it says each thing once. The count is counted
     * rather than written down, so it cannot drift out of step with the form again.
     */
    private void introPage() {
        int screens = state.draft.remainingGroups().size();
        String heading = screens <= 1 ? "One last thing." : screens + " quick screens.";
        title("A LITTLE ABOUT YOU", heading + "\nAll optional.",
            "Your answers tailor food comparisons and help us check your declared restrictions. They do not set a daily diet budget.");
        body.addView(new UiKit.WelcomeArt(this), new LinearLayout.LayoutParams(-1, UiKit.dp(this, 120)));
        UiKit.gap(body, 20);

        // Offered only when it would actually do something. A button that cannot import is worse
        // than no button: it costs a tap and teaches the person the app is unreliable.
        HealthImport.Availability health = HealthImport.check(this);
        if (health == HealthImport.Availability.AVAILABLE) {
            action("Bring in height & weight from Health Connect", false, this::importFromHealth);
            text("Fitbit, Strava, HealthifyMe and Samsung Health all write there.", 12, UiKit.muted(this));
            UiKit.gap(body, 10);
        }

        action(screens == 0 ? "Review my answers  →" : "Let's go  →", true, () -> {
            // Decide the screens once, here, so none can disappear mid-answer.
            state.pending = state.draft.remainingGroups();
            if (state.pending.isEmpty()) for (int g = 0; g < Profile.GROUPS.length; g++) state.pending.add(g);
            state.step = 0; state.page = Page.QUESTION; render();
        });
        action("Skip for now", false, this::confirmSkip);
        action("Privacy & your choices", false, this::privacy);
    }

    private void importFromHealth() {
        try { startActivity(HealthImport.settings()); }
        catch (android.content.ActivityNotFoundException missing) {
            showError("Health Connect isn't available on this phone.");
        }
    }
    /**
     * One screen per group of related questions, and only the groups that still need an answer.
     *
     * <p>Two things make this feel shorter than it is. Related questions share a screen, because
     * height and weight are one thought and splitting them across two pages doubles the taps for no
     * gain. And a screen whose answers are already known is never shown: coming back to edit a
     * profile should not mean pressing Next past questions that are already done.
     */
    private void questionPage() {
        java.util.List<Integer> remaining = state.pending;
        if (remaining.isEmpty()) { save(false); return; }
        int position = Math.min(state.step, remaining.size() - 1);
        int group = remaining.get(position);

        LinearLayout progress = new LinearLayout(this);
        for (int i = 0; i < remaining.size(); i++) {
            View segment = new View(this);
            segment.setBackground(UiKit.shape(this, i <= position ? UiKit.green(this) : UiKit.line(this), 0, 4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UiKit.dp(this, 5), 1);
            lp.setMargins(0, 0, UiKit.dp(this, 5), 0);
            progress.addView(segment, lp);
        }
        body.addView(progress); UiKit.gap(body, 22);
        title((position + 1) + " OF " + remaining.size(), Profile.GROUP_TITLES[group], Profile.GROUP_REASONS[group]);

        for (int question : Profile.GROUPS[group]) {
            if (question == Profile.HEALTH) { healthQuestion(); return; }
            if (question == Profile.BUYS) { buysQuestion(); return; }
            if (question == Profile.GOALS) { goalsQuestion(); continue; }
            renderQuestion(question, Profile.GROUPS[group].length > 1);
        }
        UiKit.gap(body, 18);
        boolean last = position == remaining.size() - 1;
        action(last ? "Done  →" : "Continue  →", true, () -> next(false));
        action("Skip", false, () -> next(true));
        if (position > 0) action("←  Back", false, () -> { state.step--; state.error = ""; render(); });
        action("Skip the rest", false, this::confirmSkip);
    }

    /** One question inside a group. A shared screen labels each field so neither is ambiguous. */
    private void renderQuestion(int step, boolean labelled) {
        if (labelled) { UiKit.gap(body, 6); text(Profile.TITLES[step], 13, UiKit.muted(this)); }
        String[] choices = Profile.CHOICES[step];
        if (choices != null) {
            for (String option : choices) {
                boolean selected = state.draft.answers[step].equals(option)
                    || (option.equals("Self-describe") && state.draft.answers[step].startsWith("Self-described: "));
                Button choice = action((selected ? "✓  " : "") + option, selected,
                    () -> { state.draft.answers[step] = option; render(); });
                choice.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                choice.setSelected(selected);
            }
            if (step == Profile.GENDER && (state.draft.answers[step].equals("Self-describe")
                    || state.draft.answers[step].startsWith("Self-described: "))) {
                UiKit.gap(body, 16);
                answerInput = UiKit.input(this, "Your words (optional)", InputType.TYPE_CLASS_TEXT);
                answerInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(44)});
                answerInput.setText(state.draft.answers[step].replace("Self-described: ", "").replace("Self-describe", ""));
                body.addView(answerInput);
                watch(answerInput, value -> state.draft.answers[step] = "Self-described: " + value);
            }
            return;
        }
        String hint = step == Profile.AGE ? "Age in years" : step == Profile.WEIGHT ? "Weight in kg"
            : step == Profile.WAIST ? "Waist in cm (optional)" : step == Profile.PRE_PREGNANCY_WEIGHT ? "Weight before pregnancy, kg" : "Height in cm";
        int type = InputType.TYPE_CLASS_NUMBER | (step == Profile.AGE ? 0 : InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText field = UiKit.input(this, hint, type);
        field.setContentDescription(hint);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        field.setText(state.draft.answers[step]);
        body.addView(field);
        watch(field, value -> state.draft.answers[step] = value);
        answerInput = field;
    }

    /**
     * Allergens and conditions, ticked from a list rather than typed. This is the answer the safety
     * gate needs: without it nobody has an allergy on record and nothing can ever be blocked.
     *
     * <p>"None that I know of" is its own choice, and it is not the same as leaving the screen
     * blank. Blank means nobody asked; ticking it means the shopper was asked and said no — which is
     * still their word, not a guarantee, and the app never treats it as one.
     */
    private void healthQuestion() {
        java.util.List<String> picked = new java.util.ArrayList<>(state.draft.healthCodes());
        for (int i = 0; i < Profile.HEALTH_OPTIONS.length; i++) {
            String code = Profile.HEALTH_OPTIONS[i][0];
            if (i == 1) { UiKit.gap(body, 14); text("ALLERGENS", 12, UiKit.muted(this)); }
            if (i == Profile.FIRST_CONDITION) { UiKit.gap(body, 14); text("CONDITIONS", 12, UiKit.muted(this)); }
            boolean on = picked.contains(code);
            Button option = action((on ? "☑  " : "☐  ") + Profile.HEALTH_OPTIONS[i][1], on, () -> {
                java.util.List<String> next = new java.util.ArrayList<>(state.draft.healthCodes());
                if (next.contains(code)) next.remove(code);
                else if ("none".equals(code)) { next.clear(); next.add(code); }
                else { next.remove("none"); next.add(code); }
                state.draft.answers[Profile.HEALTH] = String.join(",", next);
                render();
            });
            option.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
        healthFollowUps(picked);
        UiKit.gap(body, 16);
        EditText other = UiKit.input(this, "Other allergy or condition (optional)",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        other.setSingleLine(false); other.setMinLines(2); other.setGravity(Gravity.TOP);
        other.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        other.setText(state.draft.answers[Profile.HEALTH_OTHER]);
        body.addView(other);
        watch(other, value -> state.draft.answers[Profile.HEALTH_OTHER] = value);
        answerInput = other;
        UiKit.gap(body, 10);
        text("Stays on this phone. Never sent to our servers.", 12, UiKit.muted(this));
        UiKit.gap(body, 18);
        action("Continue  →", true, () -> next(false));
        action("Skip", false, () -> next(true));
        action("←  Back", false, () -> { state.step--; state.error = ""; render(); });
        action("Skip the rest", false, this::confirmSkip);
    }

    /**
     * Goals, several allowed. Weight loss and weight gain exclude each other: choosing one clears the
     * other, so the form can never hold both.
     */
    private void goalsQuestion() {
        UiKit.gap(body, 6);
        text(Profile.TITLES[Profile.GOALS], 13, UiKit.muted(this));
        java.util.List<String> picked = new java.util.ArrayList<>(state.draft.goalCodes());
        for (String[] option : Profile.GOAL_OPTIONS) {
            String code = option[0];
            boolean on = picked.contains(code);
            Button choice = action((on ? "☑  " : "☐  ") + option[1], on, () -> {
                java.util.List<String> next = new java.util.ArrayList<>(state.draft.goalCodes());
                if (!next.remove(code)) {
                    next.add(code);
                    if ("loss".equals(code)) next.remove("gain");
                    if ("gain".equals(code)) next.remove("loss");
                }
                state.draft.answers[Profile.GOALS] = String.join(",", next);
                render();
            });
            choice.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
    }

    /** One single-choice follow-up, shown only when the condition it refines is ticked. */
    private void followUp(int key, String heading, String[][] options) {
        UiKit.gap(body, 10);
        text(heading, 12, UiKit.muted(this));
        for (String[] option : options) {
            boolean on = option[0].equals(state.draft.answers[key]);
            Button choice = action((on ? "◉  " : "○  ") + option[1], on, () -> {
                state.draft.answers[key] = on ? "" : option[0];
                render();
            });
            choice.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
    }

    /** Stage, type and severity questions under the health checklist; each changes a rule or a number. */
    private void healthFollowUps(java.util.List<String> picked) {
        boolean allergy = false;
        for (String code : picked) if (Allergen.byCode(code) != null || "wheat".equals(code)) allergy = true;
        if (allergy) followUp(Profile.TRACE_SEVERITY, "SHOULD 'MAY CONTAIN' COUNT AS UNSAFE FOR YOU?", Profile.SEVERITY_OPTIONS);
        if (picked.contains("pregnancy")) {
            followUp(Profile.PREGNANCY_STAGE, "HOW FAR ALONG?", Profile.PREGNANCY_OPTIONS);
            UiKit.gap(body, 8);
            EditText before = UiKit.input(this, "Weight before pregnancy, kg (optional)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            before.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
            before.setText(state.draft.answers[Profile.PRE_PREGNANCY_WEIGHT]);
            body.addView(before);
            watch(before, value -> state.draft.answers[Profile.PRE_PREGNANCY_WEIGHT] = value);
        } else if (picked.contains("breastfeeding")) {
            followUp(Profile.PREGNANCY_STAGE, "YOUR BABY'S AGE?", Profile.BREASTFEEDING_OPTIONS);
        }
        if (picked.contains("diabetes")) followUp(Profile.DIABETES_TYPE, "WHICH DIABETES?", Profile.DIABETES_OPTIONS);
        if (picked.contains("kidney")) followUp(Profile.KIDNEY_STAGE, "ARE YOU ON DIALYSIS?", Profile.KIDNEY_OPTIONS);
    }

    /** The aisles this shopper actually walks down, so the app can lead with what they buy. */
    private void buysQuestion() {
        java.util.List<String> picked = new java.util.ArrayList<>(state.draft.buyCodes());
        for (String[] option : Profile.BUY_OPTIONS) {
            String code = option[0];
            boolean on = picked.contains(code);
            Button choice = action((on ? "☑  " : "☐  ") + option[1], on, () -> {
                java.util.List<String> next = new java.util.ArrayList<>(state.draft.buyCodes());
                if (!next.remove(code)) next.add(code);
                state.draft.answers[Profile.BUYS] = String.join(",", next);
                render();
            });
            choice.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
        UiKit.gap(body, 18);
        action("Continue  →", true, () -> next(false));
        action("Skip", false, () -> next(true));
        action("←  Back", false, () -> { state.step--; state.error = ""; render(); });
        action("Skip the rest", false, this::confirmSkip);
    }

    private void confirmSkip() {
        boolean hasAnswers = false;
        for (String answer : state.draft.answers) if (!answer.isBlank()) { hasAnswers = true; break; }
        if (!hasAnswers) { save(true); return; }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Continue without a profile?")
            .setMessage("This clears the optional answers you've entered. You can add them later from the BetterChoice app.")
            .setNegativeButton("Keep answering", null).setPositiveButton("Clear & skip", (d, which) -> save(true)).show();
        dialog.getWindow().setHideOverlayWindows(true);
        SafeTouches.protect(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
    }
    private void next(boolean skip) {
        java.util.List<Integer> remaining = state.pending;
        if (remaining.isEmpty()) { save(false); return; }
        int position = Math.min(state.step, remaining.size() - 1);
        int group = remaining.get(position);
        java.util.List<Integer> questions = new java.util.ArrayList<>();
        for (int question : Profile.GROUPS[group]) questions.add(question);
        // The free-text box and the follow-ups live on the health screen without being screens of their own.
        if (questions.contains(Profile.HEALTH)) {
            questions.add(Profile.HEALTH_OTHER);
            state.draft.clearUnusedFollowUps();
            for (int followUp : new int[]{Profile.PREGNANCY_STAGE, Profile.PRE_PREGNANCY_WEIGHT, Profile.TRACE_SEVERITY,
                    Profile.DIABETES_TYPE, Profile.KIDNEY_STAGE}) questions.add(followUp);
        }
        for (int question : questions) {
            try { state.draft.answers[question] = skip ? "" : Profile.validate(question, state.draft.answers[question]); }
            catch (IllegalArgumentException invalid) { showError(invalid.getMessage()); return; }
        }
        if (position < remaining.size() - 1) { state.step++; render(); } else save(false);
    }
    private void save(boolean skipAll) {
        if (!account.sessionUsable()) { state.page = Page.RESTORE; state.error = "Please renew your session before saving."; render(); return; }
        if (skipAll) {
            Profile cleared = new Profile();
            cleared.adultConfirmed = state.draft.adultConfirmed; cleared.trialStartedAt = state.draft.trialStartedAt;
            state.draft = cleared;
        }
        state.draft.syncDerived();
        state.draft.complete = true; state.busy = true; render();
        auth.saveProfile(state.draft, callback(state, Op.PROFILE_SAVED));
    }
    private void trialPage() {
        title("YOUR FREE TRIAL", "3 months free.\nNo payment today.",
            "Use BetterChoice fully while we learn from your feedback. Your ratings and suggestions help make India eat healthier.");
        text("✓  Instant A · B · C ratings on packaged food\n✓  Works inside Blinkit, Zepto, Swiggy, Instamart, Zomato and Flipkart Minutes\n✓  Add missing product details to help everyone", 15, UiKit.ink(this));
        UiKit.gap(body, 16);
        text("Beta: no payment connected, nothing charged. We'll ask before any paid plan starts.", 13, UiKit.muted(this));
        action("Start my 3-month free trial  →", true, () -> {
            state.draft.trialStartedAt = Instant.now().toString();
            state.busy = true; render();
            auth.saveProfile(state.draft, callback(state, Op.TRIAL_STARTED));
        });
    }
    private void restorePage() {
        title("YOUR ACCOUNT", "Pick up where\nyou left off.", "We are reconnecting your verified account and saved preferences.");
        action("Try again", true, this::restore);
        action("Sign out", false, () -> { auth.signOut(); state = new State(); state.owner = new WeakReference<>(this); render(); });
    }
    private void restore() {
        if (!account.hasSession()) { state.page = Page.SIGN_IN; render(); return; }
        state.busy = true; render();
        if (account.signedIn()) loadProfile(); else auth.refresh(callback(state, Op.VERIFIED));
    }
    private void loadProfile() { state.page = Page.RESTORE; state.busy = true; render(); auth.loadProfile(callback(state, Op.PROFILE_LOADED)); }

    private enum Op { SENT, VERIFIED, PROFILE_LOADED, PROFILE_SAVED, TRIAL_STARTED }
    private static AuthController.Callback callback(State state, Op operation) {
        return message -> {
            state.busy = false; state.error = message == null ? "" : message;
            if (message == null && operation == Op.SENT) { state.page = Page.CODE; state.code = ""; state.info = ""; state.lastTriedCode = ""; }
            OnboardingActivity activity = state.owner.get();
            if (activity == null || activity.isDestroyed() || activity.isFinishing()) return;
            if (message == null) {
                switch (operation) {
                    case VERIFIED: state.code = ""; activity.loadProfile(); return;
                    case PROFILE_LOADED:
                        state.draft = activity.account.profile().copy();
                        if (activity.account.onboardingDone() && !EDIT_PROFILE.equals(activity.getIntent().getAction())) { activity.openCompanion(); return; }
                        state.page = nextPage(state.draft, EDIT_PROFILE.equals(activity.getIntent().getAction()));
                        break;
                    case PROFILE_SAVED:
                        if (state.draft.trialStarted()) { activity.openCompanion(); return; }
                        state.page = Page.TRIAL;
                        break;
                    case TRIAL_STARTED: activity.openCompanion(); return;
                    default: break;
                }
            } else if ((operation == Op.VERIFIED || operation == Op.PROFILE_LOADED) && activity.account.canUseBubble() &&
                !EDIT_PROFILE.equals(activity.getIntent().getAction())) {
                // Offline but still within the allowance: the companion keeps working from the saved account.
                state.error = ""; activity.openCompanion(); return;
            } else if (operation == Op.TRIAL_STARTED) state.draft.trialStartedAt = "";
            activity.render();
        };
    }
    static Page nextPage(Profile profile, boolean editing) {
        if (!profile.adultConfirmed) return Page.AGE;
        if (!profile.complete || editing) return Page.INTRO;
        if (!profile.trialStarted()) return Page.TRIAL;
        return Page.INTRO;
    }
    private void openCompanion() { startActivity(new Intent(this, MainActivity.class)); finish(); }
    private void showError(String message) {
        state.error = message;
        if (error != null) { error.setText(message); error.setVisibility(View.VISIBLE); error.announceForAccessibility(message); }
    }
    private interface Changed { void value(String text); }
    private static void watch(EditText input, Changed changed) {
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { changed.value(s.toString()); }
            public void afterTextChanged(Editable e) {}
        });
    }
    private void privacy() {
        new AlertDialog.Builder(this).setTitle("Your information, your choice")
            .setMessage(getString(R.string.onboarding_privacy))
            .setPositiveButton("Got it", null).show().getWindow().setHideOverlayWindows(true);
    }
}
