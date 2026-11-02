package com.betterchoice.android;

import android.graphics.Bitmap;
import android.graphics.Rect;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * BetterChoice acts inside the supported shopping apps and nowhere else. The window-level rules are
 * pinned in {@link BubbleExitTest}; this pins the operating-system filter and the screenshot area.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class ServiceScopeTest {

    private static String serviceConfig() throws Exception {
        for (String path : new String[] {"src/main/res/xml/bubble_service.xml", "app/src/main/res/xml/bubble_service.xml"}) {
            File file = new File(path);
            if (file.exists()) return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        }
        throw new AssertionError("bubble_service.xml not found from " + new File(".").getAbsolutePath());
    }

    /**
     * Android delivers events only from the packages listed in the service configuration. Leaving it
     * out means every app on the phone — messages, banking, photos — is delivered to the service.
     */
    @Test public void androidSendsEventsOnlyFromTheSupportedApps() throws Exception {
        Matcher m = Pattern.compile("android:packageNames=\"([^\"]+)\"").matcher(serviceConfig());
        assertTrue("The service must declare packageNames, or it hears from every app", m.find());
        Set<String> declared = new HashSet<>(Arrays.asList(m.group(1).split("\\s*,\\s*")));
        assertEquals("The OS filter and the in-app list must be the same apps", ForegroundPolicy.SUPPORTED, declared);
    }

    @Test public void theListIsTheShoppingAppsOnly() {
        assertEquals(new HashSet<>(Arrays.asList("com.grofers.customerapp", "com.zeptoconsumerapp", "in.swiggy.android",
            "in.swiggy.android.instamart", "com.application.zomato", "com.flipkart.android")), ForegroundPolicy.SUPPORTED);
        assertFalse(ForegroundPolicy.shouldShow("com.whatsapp", true, false));
        assertFalse(ForegroundPolicy.shouldShow("com.google.android.apps.nbu.paisa.user", true, false));
    }

    /** On Android 12 and 13 the capture is the whole display; only the shopping window's part is kept. */
    @Test public void aWholeScreenCaptureIsCutToTheShoppingWindow() {
        assertEquals(new Rect(0, 80, 1080, 2300), PackScanner.cropArea(1080, 2400, new Rect(0, 80, 1080, 2300)));
        assertEquals("Split screen: only the shopping half", new Rect(0, 1210, 1080, 2400),
            PackScanner.cropArea(1080, 2400, new Rect(0, 1210, 1080, 2400)));
        assertEquals("Clipped to the picture", new Rect(0, 0, 1080, 2400),
            PackScanner.cropArea(1080, 2400, new Rect(-10, -10, 1200, 2600)));
        assertNull("Off screen", PackScanner.cropArea(1080, 2400, new Rect(2000, 0, 3000, 100)));
        assertNull("Unknown window area", PackScanner.cropArea(1080, 2400, null));
    }

    @Test public void theCroppedPictureHoldsNothingOutsideTheWindow() {
        Bitmap full = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888);
        Bitmap cropped = PackScanner.cropToWindow(full, new Rect(0, 1210, 1080, 2400));
        assertNotNull(cropped);
        assertEquals(1080, cropped.getWidth());
        assertEquals(1190, cropped.getHeight());
        assertNull("No window area, no picture", PackScanner.cropToWindow(
            Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), null));
    }
}
