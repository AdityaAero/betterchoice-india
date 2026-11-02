package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;

/** Declared portion contribution. Scanning/purchasing never establishes consumption or daily totals. */
final class DailyImpact {
    static final class Line {
        final String nutrient, amount, reference;
        final int percent;
        final double exactPercent, exactAmount;
        final boolean firm, isLimit;
        Line(String nutrient, String amount, double exactAmount, double exactPercent, boolean firm, boolean isLimit,
             String reference) {
            this.nutrient = nutrient; this.amount = amount; this.exactAmount = exactAmount;
            this.exactPercent = exactPercent; this.percent = (int)Math.min(Integer.MAX_VALUE, Math.round(exactPercent));
            this.firm = firm; this.isLimit = isLimit; this.reference = reference;
        }
        @Override public String toString() { return nutrient + " " + amount + " = " + percent + "%"; }
    }
    final DailyNeeds needs;
    final double portionG;
    final boolean wholePack;
    final List<Line> lines;
    final Line binding;
    final String measure;
    private DailyImpact(DailyNeeds needs, double portion, boolean wholePack, List<Line> lines, Line binding, String measure) {
        this.needs = needs; this.portionG = portion; this.wholePack = wholePack;
        this.lines = lines; this.binding = binding; this.measure = measure;
    }
    static DailyImpact forServing(Nutrition per100, DailyNeeds needs) {
        Double serving = per100 == null ? null : PackMath.servingSize(per100);
        return serving == null ? null : of(per100, needs, serving, false);
    }
    static DailyImpact forPack(Nutrition per100, DailyNeeds needs) {
        return per100 == null || !NutrientMath.positive(per100.packAmount()) ? null
            : of(per100, needs, per100.packAmount(), true);
    }
    static DailyImpact of(Nutrition n, DailyNeeds needs, double quantity, boolean wholePack) {
        if (n == null || needs == null || !NutrientMath.positive(quantity)) return null;
        boolean mine = needs.personal();
        String limit = mine ? "your daily limit" : "the Indian label reference";
        List<Line> lines = new ArrayList<>();
        add(lines, "Energy", n.energyKcal, quantity, needs.energyKcal, "kcal", false, false,
            mine ? "your estimated daily energy" : "the 2000 kcal label reference");
        add(lines, "Total fat", n.totalFatG, quantity, needs.totalFatLimitG, "g", false, true,
            limit + " (" + DailyNeeds.whole(needs.totalFatLimitG) + " g)");
        add(lines, "Saturated fat", n.saturatedFatG, quantity, needs.saturatedFatLimitG, "g", false, true,
            limit + " (" + DailyNeeds.whole(needs.saturatedFatLimitG) + " g)");
        add(lines, "Trans fat", n.transFatG, quantity, needs.transFatLimitG, "g", false, true,
            limit + " (" + DailyNeeds.oneDecimal(needs.transFatLimitG) + " g)");
        // A share of the upper end of the NIN range: carbohydrate is a range to sit within, not a limit.
        add(lines, "Carbohydrate", n.carbohydrateG, quantity, needs.carbohydrateHighG, "g", false, false,
            "the upper end of " + (mine ? "your " : "") + "50-55% energy range (" + DailyNeeds.whole(needs.carbohydrateHighG) + " g)");
        // Total sugars are not free/added sugars. Only the explicitly declared added value is comparable.
        add(lines, "Added sugars", n.addedSugarsG, quantity, needs.sugarLimitG, "g", false, true,
            limit + " (" + DailyNeeds.whole(needs.sugarLimitG) + " g; better under " + DailyNeeds.whole(needs.sugarBetterG) + " g)");
        add(lines, "Sodium", n.sodiumMg, quantity, needs.sodiumLimitMg, "mg", true, true, "the 2000 mg daily limit");
        add(lines, "Fibre", n.fibreG, quantity, needs.fibreG, "g", false, false,
            "the WHO minimum (" + DailyNeeds.whole(needs.fibreG) + " g)");
        add(lines, "Protein", n.proteinG, quantity, needs.proteinG, "g", false, false,
            "your RDA (" + DailyNeeds.whole(needs.proteinG) + " g, adequacy)");
        Line binding = null;
        for (Line line : lines) if (line.isLimit && (binding == null || line.exactPercent > binding.exactPercent)) binding = line;
        return new DailyImpact(needs, quantity, wholePack, lines, binding, n.measure());
    }
    private static void add(List<Line> lines, String label, Double per100, double quantity,
                            double reference, String unit, boolean firm, boolean limit, String of) {
        Double amount = NutrientMath.portion(per100, quantity);
        Double percent = NutrientMath.percent(amount, reference);
        if (percent == null) return;
        double display = "mg".equals(unit) || "kcal".equals(unit) ? Math.round(amount) : Math.round(amount * 10) / 10.0;
        lines.add(new Line(label, Nutrition.number(display) + " " + unit, amount, percent, firm, limit, of));
    }
    String headline() {
        return (wholePack ? "Whole pack" : Nutrition.number(portionG) + " " + measure)
            + (needs.personal() ? ": share of your daily references" : ": contribution to adult reference intakes");
    }
    String confidence() {
        String scope = "References are not toxicity thresholds. This is label arithmetic, not intake tracking: "
            + "what else you eat that day is unknown. ";
        if (needs.personal()) return needs.summary() + " Limits are shares of that energy (NIN 2024 and WHO); "
            + "sodium is 2000 mg for everyone. " + scope + needs.proteinNote;
        return "India: FSSAI label references (2000 kcal, fat 67 g, added sugars 50 g, saturated fat 22 g, trans fat 2 g, "
            + "sodium 2000 mg). " + scope + "Add your weight, age, gender and activity for your own references.";
    }
}
