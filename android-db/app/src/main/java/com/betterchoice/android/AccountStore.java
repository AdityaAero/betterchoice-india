package com.betterchoice.android;

import android.content.Context;
import java.util.ArrayList;
import org.json.JSONObject;

/** One account per installation. All mutating calls are on the main thread. */
final class AccountStore {
    interface Storage { String read() throws Exception; void write(String value) throws Exception; void clear(); }
    /** The bubble keeps working this long past token expiry while a phone is offline. */
    static final long OFFLINE_ALLOWANCE_MS = 72L * 60 * 60 * 1000;
    private static AccountStore instance;
    static synchronized AccountStore get(Context context) {
        if (instance == null) instance = new AccountStore(new SecureVault(context.getApplicationContext()));
        return instance;
    }
    /** Host tests only: Robolectric has no Android Keystore. */
    static synchronized void replaceForTests(AccountStore store) { instance = store; }

    private final Storage storage;
    private volatile JSONObject state = new JSONObject();
    private final ArrayList<Runnable> listeners = new ArrayList<>();
    AccountStore(Storage storage) {
        this.storage = storage;
        try {
            String raw = storage.read(); if (!raw.isEmpty()) state = new JSONObject(raw);
            if (!state.optString("project", "").equals(AuthConfig.base())) { state = new JSONObject(); storage.clear(); }
        }
        catch (Exception unreadable) { state = new JSONObject(); storage.clear(); }
    }
    boolean signedIn() { return !userId().isEmpty() && !accessToken().isEmpty() && expiresAt() > System.currentTimeMillis(); }
    boolean hasSession() { return !userId().isEmpty() && !refreshToken().isEmpty(); }
    /** A verified session that has not been offline for longer than the allowance. */
    boolean sessionUsable() { return hasSession() && System.currentTimeMillis() < expiresAt() + OFFLINE_ALLOWANCE_MS; }
    boolean onboardingDone() {
        Profile profile = profile();
        return profileLoaded() && profile.complete && profile.adultConfirmed && profile.trialStarted();
    }
    boolean canUseBubble() { return sessionUsable() && onboardingDone(); }
    boolean testMode() { return state.optBoolean("test_mode", false); }
    String userId() { return state.optString("user_id", ""); }
    String accessToken() { return state.optString("access_token", ""); }
    String refreshToken() { return state.optString("refresh_token", ""); }
    String label() { return state.optString("label", "Your account"); }
    long expiresAt() { return state.optLong("expires_at", 0); }
    boolean profileLoaded() { return state.optBoolean("profile_loaded", false); }
    Profile profile() { return Profile.fromJson(state.optJSONObject("profile")); }
    void session(JSONObject response) throws Exception {
        JSONObject user = response.getJSONObject("user");
        String id = user.getString("id"), access = response.getString("access_token"), refresh = response.getString("refresh_token");
        long expiresIn = response.getLong("expires_in");
        boolean confirmed = !user.optString("email_confirmed_at", "").isBlank() || !user.optString("phone_confirmed_at", "").isBlank();
        if (!confirmed || user.optBoolean("is_anonymous", false) || !id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") || access.isEmpty() || refresh.isEmpty() || expiresIn <= 0 || expiresIn > 86400)
            throw new IllegalArgumentException("Invalid authentication response");
        JSONObject next = id.equals(userId()) ? new JSONObject(state.toString()) : new JSONObject();
        next.put("project", AuthConfig.base()).put("user_id", id).put("access_token", access).put("refresh_token", refresh)
            .put("expires_at", System.currentTimeMillis() + expiresIn * 1000)
            .put("test_mode", response.optBoolean("test_mode", false));
        String email = user.optString("email", ""), phone = user.optString("phone", "");
        next.put("label", AuthInput.mask(email.isBlank() ? phone : email, email.isBlank()));
        persist(next);
    }
    void profile(Profile profile) throws Exception {
        JSONObject next = new JSONObject(state.toString());
        next.put("profile", profile.json()).put("profile_loaded", true);
        persist(next);
    }
    private void persist(JSONObject next) throws Exception { storage.write(next.toString()); state = next; changed(); }
    void clear() { state = new JSONObject(); storage.clear(); changed(); }
    void observe(Runnable listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    void unobserve(Runnable listener) { listeners.remove(listener); }
    void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
}
