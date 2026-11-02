package com.betterchoice.android;

import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class SecurityLifecycleTest {
    private Context context;
    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        new ConsentStore(context).revoke();
    }
    public static class ProbeService extends BubbleService {
        int windowReads;
        int privacySetups;
        boolean privacyAvailable = true;
        PlatformAccess.State access = PlatformAccess.State.AVAILABLE;
        boolean accountReady = true;
        @Override boolean accountAllowsBubble() { return accountReady; }
        @Override ProductWatcher createWatcher() { return null; }
        @Override List<AccessibilityWindowInfo> queryWindows() { windowReads++; return Collections.emptyList(); }
        @Override PlatformAccess.State platformAccess() { return access; }
        @Override boolean preparePrivateWindowAccess() { privacySetups++; return privacyAvailable; }
    }
    private static class TestService implements AutoCloseable {
        private final org.robolectric.android.controller.ServiceController<ProbeService> controller =
            Robolectric.buildService(ProbeService.class).create();
        ProbeService get() { return controller.get(); }
        @Override public void close() { controller.destroy(); }
    }
    @Test public void directSettingsEnableWithoutConsentNeverReadsWindows() {
        try (var controller = new TestService()) {
            ProbeService service = controller.get();
            service.onServiceConnected();
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertEquals(0, service.windowReads);
            assertEquals(0, service.privacySetups);
            assertFalse(BubbleService.isConnected());
        }
    }
    @Test public void consentWithoutVerifiedOnboardedAccountNeverReadsWindows() {
        new ConsentStore(context).accept();
        AccountStore.replaceForTests(TestAccounts.signedOut());
        try (var controller = new TestService()) {
            ProbeService service = controller.get();
            service.accountReady = false;
            service.onServiceConnected();
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertEquals(0, service.windowReads);
            assertFalse(BubbleService.isConnected());
        } finally { AccountStore.replaceForTests(null); }
    }
    @Test public void privacySetupFailureNeverReadsWindowsOrConnects() {
        new ConsentStore(context).accept();
        try (var controller = new TestService()) {
            ProbeService service = controller.get();
            service.privacyAvailable = false;
            service.onServiceConnected();
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertEquals(1, service.privacySetups);
            assertEquals(0, service.windowReads);
            assertFalse(BubbleService.isConnected());
        }
    }
    @Test public void consentPersistsAcrossStoreInstancesAndRejectsOldFutureOrMalformedVersions() {
        ConsentStore store = new ConsentStore(context);
        assertFalse(store.isCurrent());
        assertTrue(store.accept());
        assertTrue(new ConsentStore(context).isCurrent());
        for (int version : new int[]{0, 1, ConsentStore.CURRENT_VERSION + 1}) {
            context.getSharedPreferences(ConsentStore.FILE, 0).edit().putInt(ConsentStore.KEY, version).commit();
            assertFalse(new ConsentStore(context).isCurrent());
        }
        context.getSharedPreferences(ConsentStore.FILE, 0).edit().putString(ConsentStore.KEY, "invalid").commit();
        assertFalse(store.isCurrent());
    }
    @Test public void withdrawalStopsReadsAndCancelsDelayedReconciliation() {
        new ConsentStore(context).accept();
        try (var controller = new TestService()) {
            ProbeService service = controller.get();
            service.onServiceConnected();
            assertTrue(BubbleService.isConnected());
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            int before = service.windowReads;
            assertTrue(before > 0);
            new ConsentStore(context).revoke();
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            assertEquals(before, service.windowReads);
            assertFalse(BubbleService.isConnected());
        }
    }
    @Test public void reconnectAfterProcessRestartUsesStoredConsent() {
        new ConsentStore(context).accept();
        try (var first = new TestService()) {
            first.get().onServiceConnected();
            assertTrue(BubbleService.isConnected());
        }
        assertFalse(BubbleService.isConnected());
        try (var second = new TestService()) {
            second.get().onServiceConnected();
            assertTrue(BubbleService.isConnected());
            assertTrue(second.get().windowReads > 0);
        }
    }
    @Test public void restrictionAndUnknownStateFailClosedBeforeWindowRead() {
        new ConsentStore(context).accept();
        for (PlatformAccess.State state : new PlatformAccess.State[]{PlatformAccess.State.RESTRICTED, PlatformAccess.State.UNKNOWN}) {
            try (var controller = new TestService()) {
                ProbeService service = controller.get(); service.access = state;
                service.onServiceConnected();
                assertEquals(0, service.windowReads);
                assertFalse(BubbleService.isConnected());
            }
        }
    }
    @Test public void permissionUnbindCancelsPendingWork() {
        new ConsentStore(context).accept();
        try (var controller = new TestService()) {
            ProbeService service = controller.get(); service.onServiceConnected();
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            int before = service.windowReads;
            service.onUnbind(new Intent());
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertEquals(before, service.windowReads);
            assertFalse(BubbleService.isConnected());
        }
    }
}
