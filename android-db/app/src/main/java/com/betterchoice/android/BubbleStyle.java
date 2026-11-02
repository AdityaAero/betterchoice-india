package com.betterchoice.android;

/**
 * Bubble colours. Without a graded food item the bubble is a translucent blue chosen to contrast
 * with the shopping app's own brand colour; a graded item turns it dark green, yellow or red.
 */
final class BubbleStyle {
    static final int NAVY = 0x0D47A1;
    static final int SKY = 0x4FC3F7;
    static final int GREEN = 0x1B7F3B;
    static final int YELLOW = 0xF4C20D;
    static final int RED = 0xC62828;
    static final int NEUTRAL_ALPHA = 0x73;   // ~45%: visible but does not hide shopping content
    static final int GRADED_ALPHA = 0xE6;    // ~90%: the grade must be readable

    static final class Look {
        final int fill, stroke, textColor;
        final String label;
        final NutriGrade.Band band;
        Look(int fill, int stroke, int textColor, String label, NutriGrade.Band band) {
            this.fill = fill; this.stroke = stroke; this.textColor = textColor; this.label = label; this.band = band;
        }
    }

    /**
     * Brand backgrounds: Blinkit yellow, Swiggy orange and Zomato red read best against navy;
     * Zepto and Instamart purple and Flipkart blue need a light sky blue instead.
     */
    static boolean lightBlueFor(String packageName) {
        return "com.zeptoconsumerapp".equals(packageName) || "in.swiggy.android.instamart".equals(packageName) ||
            "com.flipkart.android".equals(packageName);
    }

    static Look neutral(String packageName, boolean analysing) {
        return neutral(packageName, analysing ? "…" : "BC");
    }

    /** Same translucent blue, but asking the user to open the label. */
    static Look asking(String packageName) { return neutral(packageName, "?"); }

    private static Look neutral(String packageName, String label) {
        boolean light = lightBlueFor(packageName);
        int base = light ? SKY : NAVY;
        return new Look(argb(NEUTRAL_ALPHA, base), argb(0x99, light ? NAVY : 0xFFFFFF),
            argb(0xE6, light ? NAVY : 0xFFFFFF), label, null);
    }

    static Look graded(NutriGrade.Band band) {
        switch (band) {
            case A: return new Look(argb(GRADED_ALPHA, GREEN), argb(0xFF, 0xFFFFFF), argb(0xFF, 0xFFFFFF), "A", band);
            case B: return new Look(argb(GRADED_ALPHA, YELLOW), argb(0xFF, 0xFFFFFF), argb(0xFF, 0x3A2E00), "B", band);
            default: return new Look(argb(GRADED_ALPHA, RED), argb(0xFF, 0xFFFFFF), argb(0xFF, 0xFFFFFF), "C", band);
        }
    }

    static int bandColor(NutriGrade.Band band) {
        return band == NutriGrade.Band.A ? GREEN : band == NutriGrade.Band.B ? YELLOW : RED;
    }

    static String meaning(NutriGrade.Band band) {
        switch (band) {
            case A: return "Generally the more nutritionally favourable option";
            case B: return "Middle / moderate option";
            default: return "Least favourable overall nutritional profile";
        }
    }

    /** Two or three words for the card's header, where the colour already says the rest. */
    static String shortMeaning(NutriGrade.Band band) {
        switch (band) {
            case A: return "Better choice";
            case B: return "Middle choice";
            default: return "Less favourable";
        }
    }

    static int argb(int alpha, int rgb) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    private BubbleStyle() {}
}
