package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns per-100 values into the two numbers a label hides: what one declared serving contains, and
 * what the whole pack contains. Comparison between products always stays on the per-100 basis;
 * these columns exist only to show the real eating occasion.
 */
final class PackMath {
    /** A pack this size or smaller is commonly finished in one sitting, so the pack column matters most. */
    static final double SINGLE_SITTING_G = 200;

    static final class Row {
        final String nutrient, unit;
        final double per100;
        final Double perServing, perPack;
        Row(String nutrient, String unit, double per100, Double perServing, Double perPack) {
            this.nutrient = nutrient; this.unit = unit; this.per100 = per100;
            this.perServing = perServing; this.perPack = perPack;
        }
        String format(Double value) { return value == null ? "—" : Nutrition.number(round(value)) + " " + unit; }
        String per100Text() { return format(per100); }
        String perServingText() { return format(perServing); }
        String perPackText() { return format(perPack); }
    }

    /** Serving size stated on the label, or derived from net quantity and servings per pack. */
    static Double servingSize(Nutrition n) {
        if (NutrientMath.positive(n.servingSize)) return n.servingSize;
        if (NutrientMath.positive(n.packAmount()) && NutrientMath.positive(n.servingsPerPack)) {
            return n.packAmount() / n.servingsPerPack;
        }
        return null;
    }

    /** Servings stated on the label, or net quantity divided by the serving size. */
    static Double servingsPerPack(Nutrition n) {
        if (NutrientMath.positive(n.servingsPerPack)) return n.servingsPerPack;
        Double serving = n.servingSize;
        if (NutrientMath.positive(n.packAmount()) && NutrientMath.positive(serving)) return n.packAmount() / serving;
        return null;
    }

    static boolean singleSittingPack(Nutrition n) {
        Double pack = n.packAmount();
        return pack != null && pack > 0 && pack <= (n.beverage ? 500 : SINGLE_SITTING_G);
    }

    /** The three-column table. Rows with no per-100 value are left out. */
    static List<Row> table(Nutrition n) {
        Double serving = servingSize(n);
        Double pack = n.packAmount();
        List<Row> rows = new ArrayList<>();
        add(rows, "Energy", "kcal", n.energyKcal, serving, pack);
        add(rows, "Carbohydrate", "g", n.carbohydrateG, serving, pack);
        add(rows, "Total sugars", "g", n.sugarsG, serving, pack);
        add(rows, "Added sugars", "g", n.addedSugarsG, serving, pack);
        add(rows, "Total fat", "g", n.totalFatG, serving, pack);
        add(rows, "Saturated fat", "g", n.saturatedFatG, serving, pack);
        add(rows, "Trans fat", "g", n.transFatG, serving, pack);
        add(rows, "Sodium", "mg", n.sodiumMg, serving, pack);
        add(rows, "Fibre", "g", n.fibreG, serving, pack);
        add(rows, "Protein", "g", n.proteinG, serving, pack);
        return rows;
    }

    private static void add(List<Row> rows, String nutrient, String unit, Double per100, Double serving, Double netQuantity) {
        if (!NutrientMath.nonNegative(per100)) return;
        rows.add(new Row(nutrient, unit, per100,
            serving == null ? null : NutrientMath.portion(per100, serving),
            netQuantity == null ? null : NutrientMath.portion(per100, netQuantity)));
    }

    /** "150 g pack · label serving 30 g · 5 servings", with only the parts the label actually stated. */
    static String packSummary(Nutrition n) {
        StringBuilder text = new StringBuilder();
        String measure = n.measure();
        if (n.packAmount() != null) text.append(Nutrition.number(n.packAmount())).append(" ").append(measure).append(" pack");
        Double serving = servingSize(n);
        if (serving != null) {
            if (text.length() > 0) text.append("  ·  ");
            text.append("label serving ").append(Nutrition.number(round(serving))).append(" ").append(measure);
        }
        Double servings = servingsPerPack(n);
        if (servings != null && servings >= 1.05) {
            if (text.length() > 0) text.append("  ·  ");
            text.append(Nutrition.number(round(servings))).append(" servings");
        }
        return text.toString();
    }

    /**
     * Converts values written for some other basis (a 30 g serving, a 45 g pack, a 250 ml bottle)
     * to the per-100 basis everything else uses. Indian labels must give per 100 g/ml as well, but
     * many packs print only the serving column, so the user types what they see and this scales it.
     */
    static Nutrition toPer100(Nutrition asPrinted, double basis) {
        if (!NutrientMath.positive(basis)) throw new IllegalArgumentException("Basis must be finite and more than zero");
        Nutrition n = asPrinted.copy();
        n.basisStated = true;
        if (basis == 100) return n;
        
        n.energyKcal = scale(n.energyKcal, basis);
        n.sugarsG = scale(n.sugarsG, basis);
        n.saturatedFatG = scale(n.saturatedFatG, basis);
        n.sodiumMg = scale(n.sodiumMg, basis);
        n.fibreG = scale(n.fibreG, basis);
        n.proteinG = scale(n.proteinG, basis);
        n.addedSugarsG = scale(n.addedSugarsG, basis);
        n.totalFatG = scale(n.totalFatG, basis);
        n.transFatG = scale(n.transFatG, basis);
        n.carbohydrateG = scale(n.carbohydrateG, basis);
        // The printed basis is usually the serving size; keep it unless the label stated one.
        if (n.servingSize == null) n.servingSize = basis;
        return n;
    }

    private static Double scale(Double value, double basis) {
        return NutrientMath.normalize(value, basis);
    }

    private static double round(double value) { return Math.round(value * 10) / 10.0; }

    private PackMath() {}
}
