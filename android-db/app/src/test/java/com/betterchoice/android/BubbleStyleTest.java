package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class BubbleStyleTest {
    private static int alpha(int argb) { return (argb >>> 24) & 0xFF; }
    private static int rgb(int argb) { return argb & 0xFFFFFF; }

    @Test public void neutralBubbleIsTranslucentBlueContrastingEachApp() {
        for (String app : ForegroundPolicy.SUPPORTED) {
            BubbleStyle.Look look = BubbleStyle.neutral(app, false);
            assertNull(look.band);
            assertEquals("BC", look.label);
            assertTrue("Neutral bubble must be see-through", alpha(look.fill) < 0x80);
            int expected = BubbleStyle.lightBlueFor(app) ? BubbleStyle.SKY : BubbleStyle.NAVY;
            assertEquals(app, expected, rgb(look.fill));
        }
        // Blue/purple brands get light blue; yellow, orange and red brands get navy.
        assertEquals(BubbleStyle.SKY, rgb(BubbleStyle.neutral("com.flipkart.android", false).fill));
        assertEquals(BubbleStyle.SKY, rgb(BubbleStyle.neutral("com.zeptoconsumerapp", false).fill));
        assertEquals(BubbleStyle.NAVY, rgb(BubbleStyle.neutral("com.grofers.customerapp", false).fill));
        assertEquals(BubbleStyle.NAVY, rgb(BubbleStyle.neutral("com.application.zomato", false).fill));
    }

    @Test public void gradedBubbleUsesTheThreeBandColoursAndIsReadable() {
        assertEquals(BubbleStyle.GREEN, rgb(BubbleStyle.graded(NutriGrade.Band.A).fill));
        assertEquals(BubbleStyle.YELLOW, rgb(BubbleStyle.graded(NutriGrade.Band.B).fill));
        assertEquals(BubbleStyle.RED, rgb(BubbleStyle.graded(NutriGrade.Band.C).fill));
        for (NutriGrade.Band band : NutriGrade.Band.values()) {
            BubbleStyle.Look look = BubbleStyle.graded(band);
            assertEquals(band.name(), look.label);
            assertTrue(alpha(look.fill) > 0xC0);
        }
        assertEquals("Generally the more nutritionally favourable option", BubbleStyle.meaning(NutriGrade.Band.A));
        assertEquals("Middle / moderate option", BubbleStyle.meaning(NutriGrade.Band.B));
        assertEquals("Least favourable overall nutritional profile", BubbleStyle.meaning(NutriGrade.Band.C));
    }
}
