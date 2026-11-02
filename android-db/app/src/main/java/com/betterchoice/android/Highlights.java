package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;

/**
 * What is genuinely good about a product, said even when the product grades badly.
 *
 * <p>The card used to list only what was wrong. That is not neutral: a bag of salted peanuts with
 * 25 g of protein, 8 g of fibre and mostly unsaturated fat showed only its sodium and saturated fat,
 * and read as junk. A fair account names both sides. The band still weighs everything; this only
 * makes sure the good half is said out loud.
 *
 * <p>Every line is a food-law claim level, not a feeling. These are the levels at which FSSAI's
 * Advertising and Claims Regulations (2018) — and the EU's Regulation 1924/2006, which they
 * closely follow — let a maker print the claim on the pack:
 * <ul>
 *   <li><b>Protein</b> needs both an amount (10% of the Indian daily requirement per 100 g, 5% per
 *       100 ml) <i>and</i> a share of the energy (12%, or 20% for "high"). The energy share is what
 *       stops a chocolate bar with 8 g of protein, only 6% of its calories, being praised for it.</li>
 *   <li><b>Fibre</b>: 3 g per 100 g, or 6 g for "high".</li>
 *   <li><b>Low sugar, low sodium, low saturated fat</b>: 5 g sugar per 100 g (2.5 g per 100 ml),
 *       sodium 120 mg, saturated fat 1.5 g per 100 g (0.75 g per 100 ml) and under a tenth of the
 *       energy. "Practically no sugar" is the 0.5 g sugar-free level.</li>
 *   <li><b>Mostly unsaturated fat</b>: at least seven tenths of the fat is neither saturated nor
 *       trans — the fat WHO advises choosing instead.</li>
 * </ul>
 * A figure the label did not declare produces no line. Nothing here is inferred.
 */
final class Highlights {
    /** 10% of the ICMR-NIN 2020 reference protein requirement (54 g a day) — FSSAI's "source" level. */
    static final double PROTEIN_SOURCE_FOOD_G = 5.4;
    static final double PROTEIN_SOURCE_DRINK_G = 2.7;
    /** FSSAI's "low" claim levels per 100 g (per 100 ml for drinks), named once and shared with {@link CardFacts}. */
    static final double LOW_SUGAR_FOOD_G = 5.0, LOW_SUGAR_DRINK_G = 2.5;
    static final double LOW_SODIUM_MG = 120;
    static final double LOW_SAT_FAT_FOOD_G = 1.5, LOW_SAT_FAT_DRINK_G = 0.75;
    /** At most this many lines, so the good news never buries the band or the concerns. */
    static final int MAX_LINES = 4;

    static List<String> of(Nutrition n, Beverage identity, boolean drink) {
        List<String> good = new ArrayList<>();
        if (n == null) return good;
        String per = " per " + n.unit();

        String protein = protein(n, drink);
        if (protein != null) good.add(protein + ": " + Nutrition.number(n.proteinG) + " g" + per);
        if (n.fibreG != null && n.fibreG >= 3) {
            good.add((n.fibreG >= 6 ? "High fibre: " : "Good fibre: ") + Nutrition.number(n.fibreG) + " g" + per);
        }
        if (n.totalFatG != null && n.saturatedFatG != null && n.totalFatG >= 3) {
            double trans = n.transFatG == null ? 0 : n.transFatG;
            double unsaturated = n.totalFatG - n.saturatedFatG - trans;
            if (unsaturated / n.totalFatG >= 0.7) {
                good.add("Mostly unsaturated fat: " + Nutrition.number(round1(unsaturated)) + " of "
                    + Nutrition.number(n.totalFatG) + " g — the kind to choose");
            }
        }
        if (identity != null && identity.fruitPercent > 40) {
            if (identity.isDrink()) good.add(identity.fruitPercent + "% fruit, which counts in its favour");
            else good.add(identity.fruitPercent >= 90 ? "Mostly pulses, which counts in its favour"
                : identity.fruitPercent + "% pulses, which counts in its favour");
        }
        if (n.sugarsG != null) {
            if (n.sugarsG <= 0.5) good.add("Practically no sugar");
            else if (n.sugarsG <= (drink ? LOW_SUGAR_DRINK_G : LOW_SUGAR_FOOD_G)) good.add("Low sugar: " + Nutrition.number(n.sugarsG) + " g" + per);
        }
        if (n.sodiumMg != null && n.sodiumMg <= LOW_SODIUM_MG) {
            good.add("Low sodium: " + Nutrition.number(n.sodiumMg) + " mg" + per);
        }
        if (lowSaturatedFat(n, drink)) good.add("Low saturated fat");
        return good.size() > MAX_LINES ? new ArrayList<>(good.subList(0, MAX_LINES)) : good;
    }

    /** "Good protein", "High protein", or null when neither claim level is met. */
    static String protein(Nutrition n, boolean drink) {
        if (n.proteinG == null) return null;
        double floor = drink ? PROTEIN_SOURCE_DRINK_G : PROTEIN_SOURCE_FOOD_G;
        if (n.proteinG < floor) return null;
        // With no energy figure there is no share to check, so only the amount is required.
        double share = n.energyKcal == null || n.energyKcal <= 0 ? 1 : n.proteinG * 4 / n.energyKcal;
        if (share >= 0.20 && n.proteinG >= floor * 2) return "High protein";
        if (share >= 0.12) return "Good protein";
        return null;
    }

    private static boolean lowSaturatedFat(Nutrition n, boolean drink) {
        if (n.saturatedFatG == null) return false;
        double satAndTrans = n.saturatedFatG + (n.transFatG == null ? 0 : n.transFatG);
        if (satAndTrans > (drink ? LOW_SAT_FAT_DRINK_G : LOW_SAT_FAT_FOOD_G)) return false;
        return n.energyKcal == null || n.energyKcal <= 0 || satAndTrans * 9 / n.energyKcal <= 0.10;
    }

    private static double round1(double value) { return Math.round(value * 10) / 10.0; }

    private Highlights() {}
}
