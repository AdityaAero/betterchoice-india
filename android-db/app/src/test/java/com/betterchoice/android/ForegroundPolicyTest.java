package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ForegroundPolicyTest {
    @Test public void allSixAppsAreSupported() {
        assertEquals(6, ForegroundPolicy.SUPPORTED.size());
        assertTrue(ForegroundPolicy.SUPPORTED.contains("in.swiggy.android.instamart"));
        for (String app : ForegroundPolicy.SUPPORTED) assertTrue(ForegroundPolicy.shouldShow(app, true, false));
    }
    @Test public void homeRecentsOtherAppsAndUnknownHide() {
        for (String app : new String[]{null, "", "com.android.launcher3", "com.android.systemui",
            "com.betterchoice.android", "com.android.settings", "com.grofers.customerapp.fake"}) {
            assertFalse(ForegroundPolicy.shouldShow(app, true, false));
        }
    }
    @Test public void screenOffOrLockedAlwaysHides() {
        for (String app : ForegroundPolicy.SUPPORTED) {
            assertFalse(ForegroundPolicy.shouldShow(app, false, false));
            assertFalse(ForegroundPolicy.shouldShow(app, true, true));
            assertFalse(ForegroundPolicy.shouldShow(app, false, true));
        }
    }
    @Test public void returningToAppNeedsNoActivityState() {
        assertTrue(ForegroundPolicy.shouldShow("com.grofers.customerapp", true, false));
        assertFalse(ForegroundPolicy.shouldShow("com.android.launcher3", true, false));
        assertTrue(ForegroundPolicy.shouldShow("com.grofers.customerapp", true, false));
    }
}
