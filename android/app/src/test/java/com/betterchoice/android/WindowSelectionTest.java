package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class WindowSelectionTest {
    @Test public void noWindowsIsUnknown() {
        assertNull(new WindowSelection<String>().selected());
    }
    @Test public void focusWinsOverActiveInSplitScreen() {
        WindowSelection<String> selection = new WindowSelection<>();
        selection.consider("shopping", false, false, true);
        selection.consider("other", false, true, false);
        assertEquals("other", selection.selected());
    }
    @Test public void keyboardAndOverlayDoNotReplaceApp() {
        WindowSelection<String> selection = new WindowSelection<>();
        selection.consider("shopping", false, true, false);
        selection.consider("keyboard", true, true, true);
        selection.consider("bubble", true, true, true);
        assertEquals("shopping", selection.selected());
    }
    @Test public void focusedSystemWindowIsNotIgnored() {
        WindowSelection<String> selection = new WindowSelection<>();
        selection.consider("notification shade", false, true, false);
        selection.consider("shopping", false, false, true);
        assertEquals("notification shade", selection.selected());
    }
    @Test public void activeFallbackAndUnfocusedVisibleWindows() {
        WindowSelection<String> selection = new WindowSelection<>();
        selection.consider("shopping", false, false, true);
        selection.consider("visible background", false, false, false);
        assertEquals("shopping", selection.selected());
    }
    /**
     * Our open card takes key focus (so Back closes it) and Android then reports no window focused or active (as
     * seen on a phone: shop, system bars and card all unfocused). That is not leaving the shop.
     */
    @Test public void ourOpenCardIsNotLeavingTheShop() {
        WindowSelection<String> selection = new WindowSelection<>();
        selection.consider("shopping", false, false, false);
        selection.consider("status bar", false, false, false);
        selection.consider("card", true, false, false);
        selection.overlay();
        assertNull(selection.selected());
        assertTrue(selection.onlyOverlayInFront());
        // Another app, the home screen or the notification shade brings a focused window of its own.
        WindowSelection<String> other = new WindowSelection<>();
        other.consider("home screen", false, true, true);
        other.overlay();
        assertFalse(other.onlyOverlayInFront());
        assertEquals("home screen", other.selected());
        // Without an overlay of ours, nothing in front is just nothing in front.
        assertFalse(new WindowSelection<String>().onlyOverlayInFront());
    }
}
