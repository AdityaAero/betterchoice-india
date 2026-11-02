package com.betterchoice.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Bringing height, weight and activity in from a health app instead of typing them.
 *
 * <p><b>On Android there is one correct place to read this from: Health Connect.</b> It is the
 * platform's own store, built into Android 14 and later, and Fitbit, Strava, HealthifyMe, Samsung
 * Health and Google Fit all write into it. Integrating each of those separately would mean a
 * different sign-in and a different API per brand, for data the phone already holds in one place.
 *
 * <p><b>Apple Watch cannot be read on Android at all.</b> Its data lives in Apple Health, which has
 * no Android interface — not a gap in this app, a gap that cannot be closed from this platform. The
 * screen says so rather than listing it and failing.
 *
 * <p>This class reports what is possible on <i>this</i> phone. Reading the records themselves needs
 * the {@code androidx.health.connect} client, whose API is Kotlin coroutines and so needs Kotlin on
 * the build; that is the remaining piece, and until it lands {@link #canRead} is false everywhere
 * and the screen offers typing instead. No button is shown that does nothing.
 */
final class HealthImport {
    /** Health Connect's own package, present on Android 14+ and installable before that. */
    private static final String HEALTH_CONNECT = "com.google.android.healthconnect.controller";
    private static final String HEALTH_CONNECT_LEGACY = "com.google.android.apps.healthdata";

    enum Availability {
        /** Health Connect is on this phone and could be asked for permission. */
        AVAILABLE,
        /** Not installed. On older Androids it can be installed from Play. */
        NOT_INSTALLED,
        /** The reader is not built yet, so nothing can be imported however available it is. */
        READER_NOT_BUILT
    }

    static Availability check(Context context) {
        if (!canRead()) return Availability.READER_NOT_BUILT;
        return installed(context) ? Availability.AVAILABLE : Availability.NOT_INSTALLED;
    }

    static boolean installed(Context context) {
        PackageManager packages = context.getPackageManager();
        for (String name : new String[]{HEALTH_CONNECT, HEALTH_CONNECT_LEGACY}) {
            try {
                packages.getPackageInfo(name, 0);
                return true;
            } catch (PackageManager.NameNotFoundException notHere) { /* try the next one */ }
        }
        return false;
    }

    /**
     * Whether this build can actually read records. False until the Health Connect client is on the
     * build, so that the onboarding screen never offers an import it cannot perform.
     */
    static boolean canRead() { return false; }

    /** Opens Health Connect so someone can see for themselves what is in it. */
    static Intent settings() {
        return new Intent("android.health.connect.action.HEALTH_HOME_SETTINGS");
    }

    /** What this app would ask for, and nothing else: the three answers the form needs. */
    static final String[] WANTED = {"Height", "Weight", "Steps, to suggest your activity level"};

    private HealthImport() {}
}
