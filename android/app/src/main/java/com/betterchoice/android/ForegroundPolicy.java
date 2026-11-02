package com.betterchoice.android;

import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/** Pure decision logic: unknown/locked/system windows always fail closed. */
public final class ForegroundPolicy {
    public static final Set<String> SUPPORTED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "com.grofers.customerapp",          // Blinkit
        "com.zeptoconsumerapp",             // Zepto
        "in.swiggy.android",                // Swiggy (includes Instamart tab)
        "in.swiggy.android.instamart",      // Swiggy Instamart standalone app
        "com.application.zomato",           // Zomato
        "com.flipkart.android")));          // Flipkart, including Minutes

    public static boolean shouldShow(String packageName, boolean interactive, boolean locked) {
        return interactive && !locked && packageName != null && SUPPORTED.contains(packageName);
    }
    private ForegroundPolicy() {}
}
