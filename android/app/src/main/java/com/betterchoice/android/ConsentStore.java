package com.betterchoice.android;

import android.content.Context;
import android.content.SharedPreferences;

/** Only the disclosure version is persisted; no timestamp, app history or identifiers. */
final class ConsentStore {
    // Version 5: guided label prompts and what an "Add details" submission shares.
    static final int CURRENT_VERSION = 5;
    static final String FILE = "consent";
    static final String KEY = "disclosure_version";
    private final SharedPreferences preferences;

    ConsentStore(Context context) { preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    boolean isCurrent() {
        try { return preferences.getInt(KEY, 0) == CURRENT_VERSION; }
        catch (ClassCastException malformed) { return false; }
    }
    boolean accept() { return preferences.edit().clear().putInt(KEY, CURRENT_VERSION).commit(); }
    boolean revoke() { return preferences.edit().clear().commit(); }
    void observe(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }
    void removeObserver(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
