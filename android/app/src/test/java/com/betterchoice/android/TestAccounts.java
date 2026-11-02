package com.betterchoice.android;

import org.json.JSONObject;

/** In-memory accounts for host tests (Robolectric has no Android Keystore). Requires Robolectric for org.json. */
final class TestAccounts {
    static final class MemoryStorage implements AccountStore.Storage {
        String value;
        MemoryStorage(String value) { this.value = value; }
        @Override public String read() { return value == null ? "" : value; }
        @Override public void write(String next) { value = next; }
        @Override public void clear() { value = null; }
    }

    static AccountStore onboarded(long expiresAt) throws Exception {
        Profile profile = new Profile();
        profile.complete = true;
        profile.adultConfirmed = true;
        profile.trialStartedAt = "2026-09-17T10:00:00Z";
        JSONObject state = new JSONObject().put("project", AuthConfig.base())
            .put("user_id", "123e4567-e89b-42d3-a456-426614174000")
            .put("access_token", "access").put("refresh_token", "refresh").put("expires_at", expiresAt)
            .put("label", "•••• 4321").put("profile_loaded", true).put("profile", profile.json());
        return new AccountStore(new MemoryStorage(state.toString()));
    }

    static AccountStore onboarded() throws Exception { return onboarded(System.currentTimeMillis() + 3_600_000); }

    static AccountStore signedOut() { return new AccountStore(new MemoryStorage(null)); }

    private TestAccounts() {}
}
