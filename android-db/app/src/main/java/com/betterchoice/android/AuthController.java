package com.betterchoice.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Serial network work; generation checks prevent late replies resurrecting signed-out sessions. */
final class AuthController {
    interface Callback { void done(String error); }
    interface Work { Object run() throws Exception; }
    interface Apply { void run(Object result) throws Exception; }
    // Holds only the application context, which lives as long as the process.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static AuthController instance;
    static synchronized AuthController get(Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            // Debug builds always route through TestMode, which forwards to Supabase unless a test session is active.
            AuthApi api = BuildConfig.DEBUG ? new AuthApi(TestMode.transport(app)) : new AuthApi();
            instance = new AuthController(app, AccountStore.get(context), api);
        }
        return instance;
    }
    /** Host tests only: each test needs its own controller bound to its own account store. */
    static synchronized void replaceForTests(AuthController controller) { instance = controller; }
    /** Online sign-in is possible, either through Supabase or the debug-only test backend. */
    static boolean available() { return AuthConfig.ready() || TestMode.enabled(); }
    /** Debug-only: signs in a local tester account without any email or SMS. */
    void startTestSession(Callback callback) {
        TestMode.startBypass(context);
        verify(TestMode.TESTER, false, TestMode.CODE, callback);
    }

    private final Context context;
    private final AccountStore store;
    private final AuthApi api;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private long generation;
    private boolean refreshing;
    private final Runnable refreshTimer = () -> refresh(null);
    private final Runnable expiryTimer;
    AuthController(Context context, AccountStore store, AuthApi api) {
        this.context = context; this.store = store; this.api = api;
        expiryTimer = store::changed;
    }
    AuthApi api() { return api; }
    private void submit(Work work, Apply apply, Callback callback) {
        long expected = generation;
        worker.execute(() -> {
            try {
                Object result = work.run();
                handler.post(() -> {
                    if (expected != generation) return;
                    try { apply.run(result); if (callback != null) callback.done(null); }
                    catch (Exception storageFailure) { if (callback != null) callback.done("Could not securely save your account. Please try again."); }
                });
            } catch (Exception failure) {
                String message = failure instanceof AuthApi.Failure || failure instanceof IllegalArgumentException ? failure.getMessage() :
                    "Connection interrupted. Check your internet and try again.";
                handler.post(() -> { if (expected == generation && callback != null) callback.done(message); });
            }
        });
    }
    void send(String identity, boolean phone, String captcha, Callback callback) {
        // A test session left over from earlier routes every request to the local stand-in, so no email
        // or SMS would ever be sent and the failure would be silent. Asking for a real code ends it.
        if (BuildConfig.DEBUG && !TestMode.TESTER.equals(identity)) TestMode.endBypass(context);
        String challenge = null;
        if (!phone) {
            String verifier = EmailLink.newVerifier();
            EmailLink.remember(context, verifier);
            challenge = EmailLink.challenge(verifier);
        }
        final String codeChallenge = challenge;
        submit(() -> { api.send(identity, phone, captcha, codeChallenge); return null; }, result -> {}, callback);
    }
    void exchangeEmailLink(String authCode, String verifier, Callback callback) {
        submit(() -> api.exchangeEmailLink(authCode, verifier), result -> { store.session((JSONObject) result); schedule(); }, callback);
    }
    void verify(String identity, boolean phone, String code, Callback callback) {
        submit(() -> api.verify(identity, phone, code), result -> { store.session((JSONObject) result); EmailLink.clear(context); schedule(); }, callback);
    }
    void loadProfile(Callback callback) {
        String id = store.userId(), token = store.accessToken();
        submit(() -> api.loadProfile(id, token), result -> {
            if (id.equals(store.userId())) store.profile(((Profile) result).withLocalAnswers(store.profile()));
        }, callback);
    }
    void saveProfile(Profile profile, Callback callback) {
        String id = store.userId(), token = store.accessToken(); Profile snapshot = profile.copy();
        submit(() -> { api.saveProfile(id, token, snapshot); return snapshot; }, result -> store.profile((Profile) result), callback);
    }
    void contribute(ProductPage page, Nutrition nutrition, String ingredients, double labelBasis, Callback callback) {
        contribute(page, nutrition, ingredients, labelBasis, null, callback);
    }
    void contribute(ProductPage page, Nutrition nutrition, String ingredients, double labelBasis, String gtin, Callback callback) {
        String token = store.accessToken();
        submit(() -> { api.contribute(token, page, nutrition, ingredients, labelBasis, gtin); return null; }, result -> {}, callback);
    }
    void resume() {
        if (!available() || !store.hasSession()) return;
        if (store.expiresAt() - System.currentTimeMillis() < 120000) refresh(null); else schedule();
    }
    void refresh(Callback callback) {
        if (refreshing || !store.hasSession()) { if (callback != null) callback.done("Please wait a moment and retry."); return; }
        refreshing = true;
        String refresh = store.refreshToken(); long expected = generation;
        worker.execute(() -> {
            try {
                JSONObject result = api.refresh(refresh);
                handler.post(() -> {
                    if (expected != generation) return;
                    refreshing = false;
                    try { store.session(result); schedule(); if (callback != null) callback.done(null); }
                    catch (Exception badSession) { signOut(); if (callback != null) callback.done("Please sign in again."); }
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (expected != generation) return;
                    refreshing = false;
                    if (error instanceof AuthApi.Failure && (((AuthApi.Failure) error).status == 400 || ((AuthApi.Failure) error).status == 401 || ((AuthApi.Failure) error).status == 403)) signOut();
                    else {
                        store.changed();
                        handler.postDelayed(refreshTimer, 60000);
                    }
                    if (callback != null) callback.done("Could not renew your session. Check your connection or sign in again.");
                });
            }
        });
    }
    private void schedule() {
        handler.removeCallbacks(refreshTimer); handler.removeCallbacks(expiryTimer);
        if (!store.hasSession()) return;
        long remaining = store.expiresAt() - System.currentTimeMillis();
        // Re-evaluate bubble access when the offline allowance ends, not merely when the token expires.
        handler.postDelayed(expiryTimer, Math.max(1, remaining + AccountStore.OFFLINE_ALLOWANCE_MS));
        handler.postDelayed(refreshTimer, Math.max(1000, remaining - 60000));
    }
    void signOut() {
        String access = store.accessToken();
        generation++; refreshing = false; handler.removeCallbacksAndMessages(null);
        BubbleService.turnOffConnectedService(); new ConsentStore(context).revoke(); store.clear();
        if (BuildConfig.DEBUG) TestMode.endBypass(context);
        if (!access.isEmpty() && available()) worker.execute(() -> { try { api.logout(access); } catch (Exception ignored) { /* Local sign-out must still complete offline. */ } });
    }
    void deleteAccount(Callback callback) {
        String token = store.accessToken();
        submit(() -> { api.deleteAccount(token); return null; }, result -> signOut(), callback);
    }
}
