package com.betterchoice.android;

import android.content.Context;

/** Release builds have no test sign-in and no skip-sign-in option. */
final class TestMode {
    static final String CODE = "";
    static final String BYPASS_LABEL = "";
    static final String TESTER = "";

    static boolean enabled() { return false; }

    static boolean canBypass(Context context) { return false; }

    static boolean bypassActive(Context context) { return false; }

    static void startBypass(Context context) { throw new IllegalStateException("Not available in release builds"); }

    static void endBypass(Context context) { /* Nothing to clear. */ }

    static AuthApi.Transport transport(Context context) { throw new IllegalStateException("Test mode is not available in release builds"); }

    private TestMode() {}
}
