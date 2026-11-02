package com.betterchoice.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class ConsentUiTest {
    @Before public void reset() throws Exception {
        AccountStore.replaceForTests(TestAccounts.onboarded());
        new ConsentStore(RuntimeEnvironment.getApplication()).revoke();
        // Robolectric does not automatically grant this normal manifest permission.
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(android.Manifest.permission.HIDE_OVERLAY_WINDOWS);
    }
    @org.junit.After public void clearAccount() { AccountStore.replaceForTests(null); }
    @Test public void signedOutUsersAreSentToOnboarding() {
        AccountStore.replaceForTests(TestAccounts.signedOut());
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertTrue(controller.get().isFinishing());
            Intent next = org.robolectric.Shadows.shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(OnboardingActivity.class.getName(), next.getComponent().getClassName());
        }
    }
    static Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) {
                Button found = button(parent.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
    @Test public void cancelAndExternalLaunchCannotGrantConsent() {
        Intent hostile = new Intent(Intent.ACTION_VIEW, Uri.parse("betterchoice://enable?consent=true"))
            .putExtra("disclosure_version", ConsentStore.CURRENT_VERSION).putExtra("enabled", true);
        try (var controller = Robolectric.buildActivity(MainActivity.class, hostile).setup()) {
            MainActivity activity = controller.get();
            assertFalse(new ConsentStore(activity).isCurrent());
            button(activity.getWindow().getDecorView(), activity.getString(R.string.setup_bubble)).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertFalse(new ConsentStore(activity).isCurrent());
        }
    }
    @Test public void acceptIsExplicitAndTurnOffRevokesIt() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            button(activity.getWindow().getDecorView(), activity.getString(R.string.setup_bubble)).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertFalse(new ConsentStore(activity).isCurrent());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(new ConsentStore(activity).isCurrent());
            button(activity.getWindow().getDecorView(), activity.getString(R.string.turn_off)).performClick();
            assertFalse(new ConsentStore(activity).isCurrent());
        }
    }
    @Test public void backgroundingInvalidatesConsentAndStaleCallbacks() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            button(activity.getWindow().getDecorView(), activity.getString(R.string.setup_bubble)).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            Button staleAgree = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            controller.pause();
            assertFalse(dialog.isShowing());
            staleAgree.performClick();
            assertFalse(new ConsentStore(activity).isCurrent());
            controller.resume();
            staleAgree.performClick();
            assertFalse(new ConsentStore(activity).isCurrent());
            button(activity.getWindow().getDecorView(), activity.getString(R.string.setup_bubble)).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(new ConsentStore(activity).isCurrent());
        }
    }
    @Test public void fullAndPartialObscuredTouchesDoNotGrantConsent() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            button(activity.getWindow().getDecorView(), activity.getString(R.string.setup_bubble)).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Button agree = ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE);
            assertTrue(agree.getFilterTouchesWhenObscured());
            for (int flag : new int[]{MotionEvent.FLAG_WINDOW_IS_OBSCURED, MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED}) {
                for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                    MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
                    pointer.id = 0; pointer.toolType = MotionEvent.TOOL_TYPE_FINGER;
                    MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
                    coords.x = 5; coords.y = 5; coords.pressure = 1; coords.size = 1;
                    MotionEvent event = MotionEvent.obtain(0, 10, action, 1,
                        new MotionEvent.PointerProperties[]{pointer}, new MotionEvent.PointerCoords[]{coords},
                        0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flag);
                    agree.dispatchTouchEvent(event); event.recycle();
                }
                assertFalse(new ConsentStore(activity).isCurrent());
            }
            // Accessibility/keyboard activation still invokes the same explicit action.
            agree.performClick();
            assertTrue(new ConsentStore(activity).isCurrent());
        }
    }
}
