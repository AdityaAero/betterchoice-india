package com.betterchoice.android;

import android.util.Log;

/** Callers pass fixed messages/timing only. Never accept package names or window data. */
final class DebugLog {
    static void event(String message) { if (BuildConfig.DEBUG) Log.i("BetterChoice", message); }
    /**
     * DEBUG BUILDS ONLY: what the screen reader saw, used to tune retailer page detection during
     * testing. Release builds strip Log calls and this method is never called from release paths.
     */
    static void diagnostic(String message) {
        if (!BuildConfig.DEBUG) return;
        // Some phones rate-limit an app's log (Vivo: persist.log.ratelimit=1). Analysis re-runs on
        // every scroll and repeated the same lines, and past the limit the whole log went silent.
        synchronized (RECENT) {
            if (!RECENT.add(message)) return;           // said recently: each run repeats its lines
            if (RECENT.size() > 24) RECENT.remove(RECENT.iterator().next());
        }
        Log.i("BetterChoiceScan", message);
        // The same line in the app's own private file, which an accuracy run reads with run-as: a
        // phone that rate-limits the log (as this Vivo does) otherwise leaves the run blind.
        java.io.File file = auditFile;
        if (file == null) return;
        synchronized (RECENT) {
            try (java.io.FileWriter out = new java.io.FileWriter(file, true)) {
                out.write(message.replace('\n', ' '));
                out.write('\n');
            } catch (java.io.IOException ignored) {
                // Diagnostics only; never let them affect the bubble.
            }
            if (file.length() > 4_000_000L) file.delete();
        }
    }
    private static final java.util.LinkedHashSet<String> RECENT = new java.util.LinkedHashSet<>();
    /** DEBUG BUILDS ONLY: set by the service to files/audit.log in the app's private storage. */
    static volatile java.io.File auditFile;
    static void failure(String operation) { if (BuildConfig.DEBUG) Log.w("BetterChoice", operation); }
    private DebugLog() {}
}
