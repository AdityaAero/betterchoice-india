package com.betterchoice.android;

import android.content.Context;
import android.os.Build;
import android.security.advancedprotection.AdvancedProtectionManager;

final class PlatformAccess {
    enum State { AVAILABLE, RESTRICTED, UNKNOWN }
    static State check(Context context) {
        if (Build.VERSION.SDK_INT < 37) return State.AVAILABLE;
        try {
            AdvancedProtectionManager manager = context.getSystemService(AdvancedProtectionManager.class);
            if (manager == null) return State.UNKNOWN;
            return manager.isAdvancedProtectionEnabled() ? State.RESTRICTED : State.AVAILABLE;
        } catch (RuntimeException unavailable) {
            return State.UNKNOWN;
        }
    }
    private PlatformAccess() {}
}
