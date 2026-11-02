package com.betterchoice.android;

import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import java.lang.reflect.Proxy;
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
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class BubbleExitTest {
    public static class ProbeService extends BubbleService {
        List<AccessibilityWindowInfo> windows = Collections.emptyList();
        int queries, rootReads, adds, removes;
        boolean assertHiddenBeforeRead, failRead;
        String rootPackage = "com.grofers.customerapp";
        View attached;
        @Override PlatformAccess.State platformAccess() { return PlatformAccess.State.AVAILABLE; }
        @Override boolean accountAllowsBubble() { return true; }
        @Override ProductWatcher createWatcher() { return null; }
        @Override boolean preparePrivateWindowAccess() { return true; }
        @Override List<AccessibilityWindowInfo> queryWindows() { queries++; return windows; }
        @Override String readRootPackage(AccessibilityWindowInfo window) {
            rootReads++;
            if (assertHiddenBeforeRead) assertNull("Bubble must be gone before root IPC", attached);
            if (failRead) throw new IllegalStateException("Window gone");
            return rootPackage;
        }
        @Override BubbleOverlay createBubble() {
            WindowManager manager = (WindowManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{WindowManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addView")) { attached = (View) args[0]; adds++; }
                    if (method.getName().equals("removeViewImmediate")) { attached = null; removes++; }
                    return null;
                });
            return new BubbleOverlay(this, manager);
        }
    }
    @Before public void consent() { new ConsentStore(RuntimeEnvironment.getApplication()).accept(); }
    private AccessibilityWindowInfo window(int id, int type, boolean focused, boolean active) {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        ReflectionHelpers.callInstanceMethod(window, "setId", from(int.class, id));
        ReflectionHelpers.callInstanceMethod(window, "setType", from(int.class, type));
        ReflectionHelpers.callInstanceMethod(window, "setFocused", from(boolean.class, focused));
        ReflectionHelpers.callInstanceMethod(window, "setActive", from(boolean.class, active));
        return window;
    }
    static final String BLINKIT = "com.grofers.customerapp";

    /** An event as a shopping app (or any app) would send it, from one of its windows. */
    static AccessibilityEvent eventFrom(String packageName, int windowId, int type) {
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setPackageName(packageName);
        ReflectionHelpers.setField(event, "mSourceWindowId", windowId);
        return event;
    }

    private void enter(ProbeService service) {
        service.windows = List.of(window(10, AccessibilityWindowInfo.TYPE_APPLICATION, true, true));
        service.onServiceConnected();
        assertNull("Nothing is shown until the shopping app announces its window", service.attached);
        assertEquals("A window nobody announced is never opened", 0, service.rootReads);
        service.onAccessibilityEvent(eventFrom(BLINKIT, 10, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
        assertNotNull(service.attached);
        assertEquals(1, service.rootReads);
    }
    private void tick(long millis) { shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
    @Test public void exitHidesAndNeverOpensTheNextAppsWindow() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get(); enter(service);
            service.windows = List.of(window(20, AccessibilityWindowInfo.TYPE_APPLICATION, true, true));
            service.assertHiddenBeforeRead = true;
            service.rootPackage = "example.home";
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            assertNull(service.attached);
            assertEquals("Home's window is recognised as not ours without being opened", 1, service.rootReads);
            tick(1000);
            int queries = service.queries;
            tick(1000);
            assertEquals("No polling on Home", queries, service.queries);
        } finally { controller.destroy(); }
    }
    @Test public void watchdogCatchesFocusChangeWithoutAnotherAccessibilityEvent() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get(); enter(service);
            // Simulates focus settling after Android's last transition notification.
            service.windows = List.of(window(20, AccessibilityWindowInfo.TYPE_APPLICATION, true, true));
            service.rootPackage = "example.home";
            service.assertHiddenBeforeRead = true;
            tick(BubbleService.VISIBLE_CHECK_MS);
            assertNull(service.attached);
            assertEquals(1, service.rootReads);
        } finally { controller.destroy(); }
    }
    @Test public void steadyWindowAndKeyboardDoNotRepeatedlyReadRootOrReattach() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get(); enter(service);
            service.windows = List.of(window(10, AccessibilityWindowInfo.TYPE_APPLICATION, true, false),
                window(30, AccessibilityWindowInfo.TYPE_INPUT_METHOD, false, true),
                window(40, AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY, false, false));
            tick(1000);
            assertNotNull(service.attached);
            assertEquals(1, service.rootReads);
            assertEquals(1, service.adds);
            assertEquals(0, service.removes);
        } finally { controller.destroy(); }
    }
    @Test public void newWindowFailureHidesAndReturnRevalidatesPackage() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get(); enter(service);
            // Blinkit opens a new screen, whose window then disappears before it can be checked.
            service.windows = List.of(window(20, AccessibilityWindowInfo.TYPE_APPLICATION, true, true));
            service.failRead = true;
            service.onAccessibilityEvent(eventFrom(BLINKIT, 20, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
            assertNull(service.attached);
            service.failRead = false;
            service.windows = List.of(window(10, AccessibilityWindowInfo.TYPE_APPLICATION, true, true));
            service.onAccessibilityEvent(eventFrom(BLINKIT, 10, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
            assertNotNull(service.attached);
            assertEquals(3, service.rootReads);
        } finally { controller.destroy(); }
    }

    // --- Only the shopping apps, never anything else -----------------------------------------------

    /** A chat or banking app in front is never opened, whatever events arrive. */
    @Test public void anotherAppInFrontIsNeverOpened() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get();
            service.windows = List.of(window(20, AccessibilityWindowInfo.TYPE_APPLICATION, true, true));
            service.rootPackage = "com.whatsapp";
            service.onServiceConnected();
            service.onAccessibilityEvent(eventFrom("com.whatsapp", 20, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
            service.onAccessibilityEvent(eventFrom("com.whatsapp", 20, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED));
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            tick(1000);
            assertNull(service.attached);
            assertEquals("No other app's window is ever read", 0, service.rootReads);
        } finally { controller.destroy(); }
    }

    /** A window id alone is not trusted: the window itself must turn out to be the shopping app's. */
    @Test public void aWindowThatIsNotTheAnnouncingAppIsRejected() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get();
            service.windows = List.of(window(10, AccessibilityWindowInfo.TYPE_APPLICATION, true, true));
            service.rootPackage = "com.whatsapp";
            service.onServiceConnected();
            service.onAccessibilityEvent(eventFrom(BLINKIT, 10, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
            assertNull(service.attached);
            assertEquals(1, service.rootReads);
            service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
            assertEquals("Once rejected, the window is not opened again", 1, service.rootReads);
        } finally { controller.destroy(); }
    }

    /** If the window-opening notice was missed, the shopping app's next content update is enough. */
    @Test public void aShoppingAppsContentUpdateBringsTheBubble() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get();
            service.windows = List.of(window(10, AccessibilityWindowInfo.TYPE_APPLICATION, true, true));
            service.onServiceConnected();
            assertNull(service.attached);
            service.onAccessibilityEvent(eventFrom(BLINKIT, 10, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED));
            assertNotNull(service.attached);
        } finally { controller.destroy(); }
    }
    @Test public void systemOrUnresolvedWindowsHideWithoutRootRead() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get(); enter(service);
            service.windows = List.of(window(20, AccessibilityWindowInfo.TYPE_SYSTEM, true, true));
            tick(BubbleService.VISIBLE_CHECK_MS);
            assertNull(service.attached);
            assertEquals(1, service.rootReads);
            service.windows = null;
            service.onAccessibilityEvent(null);
            assertNull(service.attached);
        } finally { controller.destroy(); }
    }
    @Test public void withdrawalCancelsVisibleWatchdog() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get(); enter(service);
            new ConsentStore(service).revoke();
            tick(0);
            assertNull(service.attached);
            int reads = service.queries;
            tick(1000);
            assertEquals(reads, service.queries);
            assertFalse(BubbleService.isConnected());
        } finally { controller.destroy(); }
    }
    @Test public void permissionLossCancelsVisibleWatchdog() {
        var controller = Robolectric.buildService(ProbeService.class).create();
        try {
            var service = controller.get(); enter(service);
            service.onUnbind(new Intent());
            assertNull(service.attached);
            int reads = service.queries;
            tick(1000);
            assertEquals(reads, service.queries);
        } finally { controller.destroy(); }
    }
}
