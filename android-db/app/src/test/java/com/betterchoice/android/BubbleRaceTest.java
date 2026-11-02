package com.betterchoice.android;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class BubbleRaceTest {
    @Test public void failedAttachmentCanRetryAndRepeatedHideIsSafe() {
        Context context = RuntimeEnvironment.getApplication();
        int[] adds = {0}, removes = {0};
        WindowManager manager = (WindowManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{WindowManager.class}, (p, method, args) -> {
            if (method.getName().equals("addView") && ++adds[0] == 1) throw new WindowManager.BadTokenException();
            if (method.getName().equals("removeViewImmediate")) { removes[0]++; throw new IllegalArgumentException("detached"); }
            return null;
        });
        BubbleOverlay bubble = new BubbleOverlay(context, manager);
        bubble.show(0); bubble.show(0); bubble.show(0);
        assertEquals(2, adds[0]);
        bubble.hide(); bubble.hide();
        assertEquals(2, removes[0]);
    }
    @Test public void dragAfterWindowRemovalDoesNotCrashOrKeepStaleView() {
        View[] attached = {null}; int[] adds = {0};
        WindowManager manager = (WindowManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{WindowManager.class}, (p, method, args) -> {
            if (method.getName().equals("addView")) { attached[0] = (View) args[0]; adds[0]++; }
            if (method.getName().equals("updateViewLayout")) throw new IllegalArgumentException("detached");
            return null;
        });
        BubbleOverlay bubble = new BubbleOverlay(RuntimeEnvironment.getApplication(), manager);
        bubble.show(0);
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0, 10, action, action == MotionEvent.ACTION_DOWN ? 1 : 100, 100, 0);
            attached[0].dispatchTouchEvent(event); event.recycle();
        }
        bubble.show(0);
        assertEquals(2, adds[0]);
        bubble.hide();
    }
}
