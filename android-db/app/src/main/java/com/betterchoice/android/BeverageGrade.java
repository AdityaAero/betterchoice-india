package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;

/**
 * The drinks branch: the <b>updated Nutri-Score beverage algorithm (2023)</b>, per 100 ml.
 *
 * <p>Packaged food and drink are scored by two different methods, because one scale cannot serve
 * both. Four and a half grams of sugar is unremarkable in a biscuit and a great deal in a glass, and
 * a drink can be sweetened without sugar in a way a biscuit generally is not.
 *
 * <p>Adverse points come from energy, sugars, saturated fat and salt, plus <b>four points for any
 * non-sugar sweetener</b>. Favourable points come from protein, fibre and fruit, vegetable or legume
 * content. Two consequences matter and both are the algorithm's doing rather than a rule bolted on:
 * <ul>
 *   <li><b>Plain milk scores well.</b> Protein can earn up to seven favourable points, which is
 *       enough to carry toned milk to the top band. Judging it on the soft-drink sugar scale, where
 *       lactose is penalised like added sugar, had put it alongside cola.</li>
 *   <li><b>A zero-sugar drink cannot reach the top.</b> The four sweetener points put it in the
 *       middle band: better than the sugary version, and not water.</li>
 * </ul>
 *
 * <p>Bands follow the published beverage boundaries — water alone is A, then ≤2, 3–6, 7–9, ≥10 —
 * compressed into BetterChoice's three: <b>A</b> is water or ≤2, <b>B</b> is 3–6, <b>C</b> is ≥7.
 * That compression is our decision; the arithmetic underneath is not.
 */
final class BeverageGrade {
    static final String VERSION = "nutriscore-beverage-2023";

    // Published 2023 beverage thresholds. A value earns one point per threshold it exceeds.
    private static final double[] ENERGY_KJ = {30, 90, 150, 210, 240, 270, 300, 330, 360, 390};
    private static final double[] SUGARS_G = {0.5, 2, 3.5, 5, 6, 7, 8, 9, 10, 11};
    private static final double[] SAT_FAT_G = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    private static final double[] SALT_G = {0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0};
    private static final double[] PROTEIN_G = {1.2, 1.5, 1.8, 2.1, 2.4, 2.7, 3.0};
    private static final double[] FIBRE_G = {3, 4.1, 5.2, 6.3, 7.4};
    /** Sweeteners are a flat penalty, not a scale: presence is what the guidance is about. */
    private static final int SWEETENER_POINTS = 4;

    static NutriGrade.Result grade(Nutrition n, Beverage drink) {
        int energy = points(value(n.energyKcal) * 4.184, ENERGY_KJ);
        int sugars = points(value(n.sugarsG), SUGARS_G);
        int satFat = points(value(n.saturatedFatG), SAT_FAT_G);
        // Our labels carry sodium; the algorithm wants salt. FSSAI and EU both use sodium x 2.5.
        int salt = points(value(n.sodiumMg) * 2.5 / 1000, SALT_G);
        int sweetener = drink.hasSweetener ? SWEETENER_POINTS : 0;
        int negative = energy + sugars + satFat + salt + sweetener;

        int protein = points(value(n.proteinG), PROTEIN_G);
        int fibre = points(value(n.fibreG), FIBRE_G);
        int fruit = fruitPoints(drink.fruitPercent);
        int positive = protein + fibre + fruit;

        int raw = negative - positive;
        // Water is the reference drink and the only one the published bands give an A outright.
        NutriGrade.Band band = drink.isPlainWater() ? NutriGrade.Band.A
            : raw <= 2 ? NutriGrade.Band.A
            : raw <= 6 ? NutriGrade.Band.B
            : NutriGrade.Band.C;

        // Units follow the label: a drink whose figures came per 100 g says so, rather than
        // pretending they were per 100 ml.
        String per = " per " + n.unit();
        List<String> notes = new ArrayList<>();
        if (drink.kind != null && drink.kind.note != null) notes.add(drink.kind.note);
        List<String> concerns = new ArrayList<>();
        if (drink.hasSweetener) {
            concerns.add("Sweetened without sugar. Less sugar than the regular version, and not water.");
        }
        if (sugars >= 6) concerns.add("High sugar for a drink: " + Nutrition.number(n.sugarsG) + " g" + per);
        // Plain milk's 4–5 g is its own lactose, which its note already says; listing it again as
        // "some sugar" right under that would read as a contradiction. It still counts in the score.
        else if (sugars >= 3 && drink.kind != Beverage.Kind.MILK) {
            concerns.add("Some sugar: " + Nutrition.number(n.sugarsG) + " g" + per);
        }
        if (energy >= 6) concerns.add("Energy dense for a drink: " + Math.round(value(n.energyKcal)) + " kcal" + per);
        if (satFat >= 3) concerns.add("Saturated fat: " + Nutrition.number(n.saturatedFatG) + " g" + per);
        if (value(n.sodiumMg) > NutriGrade.HIGH_SODIUM_DRINK_MG) concerns.add("Salty for a drink: " + Nutrition.number(n.sodiumMg) + " mg sodium" + per);
        if (concerns.isEmpty() && band != NutriGrade.Band.A) concerns.add("Some sugar or energy for a drink");

        // Water speaks for itself: listing "low sodium" under "nothing beats water" adds nothing.
        List<String> positives = drink.isPlainWater() ? new ArrayList<>() : Highlights.of(n, drink, true);
        NutriGrade.addedSugar(n, per, concerns, positives);
        if (notes.isEmpty() && concerns.isEmpty() && positives.isEmpty()) notes.add("Little sugar, little energy");
        return new NutriGrade.Result(band, raw, notes, concerns, positives, NutriGrade.caveats(n), drink);
    }

    /** The nutrients this drink is "high" in, at the same levels its concern lines use. */
    static java.util.Set<String> highFlags(Nutrition n) {
        java.util.Set<String> high = new java.util.HashSet<>();
        if (points(value(n.sugarsG), SUGARS_G) >= 6) high.add("sugars");
        if (points(value(n.energyKcal) * 4.184, ENERGY_KJ) >= 6) high.add("energy");
        if (points(value(n.saturatedFatG), SAT_FAT_G) >= 3) high.add("saturated_fat");
        if (value(n.sodiumMg) > NutriGrade.HIGH_SODIUM_DRINK_MG) high.add("sodium");
        return high;
    }

    /** Fruit, vegetable and legume share. Nothing is credited unless the share is actually known. */
    private static int fruitPoints(int percent) {
        if (percent > 80) return 6;
        if (percent > 60) return 4;
        if (percent > 40) return 2;
        return 0;
    }

    private static double value(Double v) { return v == null ? 0 : v; }

    private static int points(double value, double[] thresholds) {
        int points = 0;
        for (double threshold : thresholds) if (value > threshold) points++;
        return points;
    }

    private BeverageGrade() {}
}
