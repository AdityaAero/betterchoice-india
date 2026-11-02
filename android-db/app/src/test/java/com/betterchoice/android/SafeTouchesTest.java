package com.betterchoice.android;

import android.view.MotionEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class SafeTouchesTest {
    @Test public void overlayAppearingMidGestureCannotLeaveAClickableUp() {
        for (int flag : new int[]{MotionEvent.FLAG_WINDOW_IS_OBSCURED, MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED}) {
            SafeTouches.GestureGuard guard = new SafeTouches.GestureGuard();
            assertFalse(guard.reject(MotionEvent.ACTION_DOWN, 0));
            assertTrue(guard.reject(MotionEvent.ACTION_MOVE, flag));
            assertTrue(guard.reject(MotionEvent.ACTION_UP, 0));
            assertFalse(guard.reject(MotionEvent.ACTION_DOWN, 0));
            assertFalse(guard.reject(MotionEvent.ACTION_UP, 0));
        }
    }
    @Test public void fullyObscuredDownAndCancelCannotAuthorizeAnything() {
        SafeTouches.GestureGuard guard = new SafeTouches.GestureGuard();
        assertTrue(guard.reject(MotionEvent.ACTION_DOWN, MotionEvent.FLAG_WINDOW_IS_OBSCURED));
        assertTrue(guard.reject(MotionEvent.ACTION_CANCEL, 0));
        assertFalse(guard.reject(MotionEvent.ACTION_DOWN, 0));
    }
}
