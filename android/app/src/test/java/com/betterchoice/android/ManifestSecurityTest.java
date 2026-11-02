package com.betterchoice.android;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class ManifestSecurityTest {
    /** Non-exported components merged in from Google ML Kit (on-device text recognition) and AndroidX. */
    static final Set<String> LIBRARY_COMPONENTS = new HashSet<>(Arrays.asList(
        "com.google.mlkit.common.internal.MlKitComponentDiscoveryService",
        "com.google.mlkit.common.internal.MlKitInitProvider",
        "com.google.android.gms.common.api.GoogleApiActivity",
        "androidx.startup.InitializationProvider",
        "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
        "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
        "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver"));

    @Test public void onlyLauncherLinkTargetAndSystemBoundServiceAreExported() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(),
            PackageManager.GET_SERVICES | PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS);
        ServiceInfo bubble = null;
        for (ServiceInfo service : info.services) {
            if (service.name.equals(BubbleService.class.getName())) bubble = service;
            else {
                assertFalse(service.name, service.exported);
                assertTrue(service.name, LIBRARY_COMPONENTS.contains(service.name));
            }
        }
        assertNotNull(bubble);
        assertTrue(bubble.exported);
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", bubble.permission);
        for (ActivityInfo activity : info.activities) {
            if (activity.name.equals(OnboardingActivity.class.getName()) || activity.name.equals(AuthLinkActivity.class.getName())) assertTrue(activity.exported);
            else assertFalse(activity.name, activity.exported);
        }
        if (info.providers != null) for (ProviderInfo provider : info.providers) {
            assertFalse(provider.name, provider.exported);
            assertTrue(provider.name, LIBRARY_COMPONENTS.contains(provider.name));
        }
        if (info.receivers != null) for (ActivityInfo receiver : info.receivers) {
            assertFalse(receiver.name, receiver.exported);
            assertTrue(receiver.name, LIBRARY_COMPONENTS.contains(receiver.name));
        }
    }

    @Test public void permissionsAreLimitedToAccountNetworkAndBubble() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertEquals(new HashSet<>(Arrays.asList(
            "android.permission.INTERNET",
            "android.permission.HIDE_OVERLAY_WINDOWS",
            "android.permission.QUERY_ADVANCED_PROTECTION_MODE",
            // Added by ML Kit's transport library and AndroidX; neither grants access to user data.
            "android.permission.ACCESS_NETWORK_STATE",
            "com.betterchoice.india.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")),
            new HashSet<>(Arrays.asList(info.requestedPermissions)));
    }
}
