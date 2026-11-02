package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The grade, and the fork between the two branches that produce it.
 *
 * <p><b>Packaged food</b> is scored here with the 2023 Nutri-Score food algorithm per 100 g (Scientific
 * Committee update report 2022, recap pages 130-135; every table row is an {@link Evidence} entry):
 * energy, sugars, saturated fat and salt against protein, fibre and the fruit, vegetable and legume
 * share. Fats, oils, nuts and seeds have their own table (energy from saturates, saturates as a share
 * of fat); cheese keeps its protein; red meat counts at most 2 protein points.
 * Nutri-Score A/B map to A (dark green), C to B (yellow), D/E to C (red).
 *
 * <p><b>Drinks</b> are scored by {@link BeverageGrade} with the updated 2023 beverage algorithm per
 * 100 ml, which the food method cannot stand in for: its thresholds are far lower, it credits fruit
 * content, and it penalises non-sugar sweeteners, none of which the food method does.
 *
 * <p>Either way the result says both sides: what counts against the product and, separately, what
 * is genuinely good about it ({@link Highlights}). The good side is never folded into the band by
 * this code — the published method already weighs it — it is only made visible.
 */
final class NutriGrade {
    enum Band { A, B, C }

    /** Which 2023 table and rule applies. */
    enum FoodKind { GENERAL, CHEESE, RED_MEAT, FATS_OILS_NUTS }

    static final class Result {
        final Band band;
        final int score;
        /** What it was scored as, and anything that frames the numbers. Shown first. */
        final List<String> notes;
        /** What counts against it. */
        final List<String> concerns;
        /** What is genuinely good about it. Shown in green, below the concerns. */
        final List<String> positives;
        /** The limits of this reading: undeclared values, a converted basis. Shown quietly, last. */
        final List<String> caveats;
        /** All of the above in display order, for callers that only need the text. */
        final List<String> reasons;
        /** What the product was finally scored as, after the label's own numbers were checked. */
        final Beverage identity;
        /** The food table used; general for drinks. */
        final FoodKind kind;

        Result(Band band, int score, List<String> notes, List<String> concerns, List<String> positives,
               List<String> caveats, Beverage identity) {
            this(band, score, notes, concerns, positives, caveats, identity, FoodKind.GENERAL);
        }

        Result(Band band, int score, List<String> notes, List<String> concerns, List<String> positives,
               List<String> caveats, Beverage identity, FoodKind kind) {
            this.band = band; this.score = score; this.identity = identity; this.kind = kind;
            this.notes = Collections.unmodifiableList(notes);
            this.concerns = Collections.unmodifiableList(concerns);
            this.positives = Collections.unmodifiableList(positives);
            this.caveats = Collections.unmodifiableList(caveats);
            List<String> all = new ArrayList<>(notes);
            all.addAll(concerns); all.addAll(positives); all.addAll(caveats);
            this.reasons = Collections.unmodifiableList(all);
        }

        boolean scoredAsDrink() { return identity != null && identity.isDrink(); }

        /**
         * The official five-letter Nutri-Score behind the band. Our three bands fold A/B together
         * and D/E together; the letter lets a swap from E to D be recognised as a real step.
         */
        char letter() {
            if (scoredAsDrink()) {
                if (identity.isPlainWater()) return 'A';
                return score <= 2 ? 'B' : score <= 6 ? 'C' : score <= 9 ? 'D' : 'E';
            }
            // 2023 thresholds: general A up to 0, fats, oils, nuts and seeds A up to -6; B to 2, C to 10, D to 18.
            int topOfA = kind == FoodKind.FATS_OILS_NUTS ? -6 : 0;
            return score <= topOfA ? 'A' : score <= 2 ? 'B' : score <= 10 ? 'C' : score <= 18 ? 'D' : 'E';
        }
    }

    // Nutri-Score 2023 food tables (evidence ns2023.*): a value above a threshold earns its point.
    static final double[] ENERGY_KJ = {335, 670, 1005, 1340, 1675, 2010, 2345, 2680, 3015, 3350};
    static final double[] SUGARS = {3.4, 6.8, 10, 14, 17, 20, 24, 27, 31, 34, 37, 41, 44, 48, 51};
    static final double[] SAT_FAT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    static final double[] SALT_G = {0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.4, 2.6, 2.8, 3.0, 3.2, 3.4, 3.6, 3.8, 4.0};
    static final double[] PROTEIN = {2.4, 4.8, 7.2, 9.6, 12, 14, 17};
    static final double[] FIBRE = {3.0, 4.1, 5.2, 6.3, 7.4};
    /** Fats, oils, nuts and seeds: energy from saturates (kJ per 100 g) and saturates as % of fat. */
    static final double[] SAT_ENERGY_KJ = {120, 240, 360, 480, 600, 720, 840, 960, 1080, 1200};
    static final double[] SAT_RATIO = {10, 16, 22, 28, 34, 40, 46, 52, 58, 64};
    /** The card's "high" lines keep the UK front-of-pack levels: 22.5 g sugars, 5 g saturated fat. */
    private static final double HIGH_SUGARS_G = 22.5, HIGH_SAT_FAT_G = 5;
    /** UK front-of-pack "high": 1.5 g salt per 100 g (600 mg sodium); 0.75 g per 100 ml for drinks. */
    static final double HIGH_SODIUM_FOOD_MG = 600;
    static final double HIGH_SODIUM_DRINK_MG = 300;

    static Result grade(Nutrition n) { return grade(n, Beverage.NOT_A_DRINK); }

    /**
     * Whether a missing sugar, saturated-fat or sodium figure could change the band. The method
     * scores a missing value as zero, the most favourable case; this scores it again at the worst
     * the label allows (saturated fat can never exceed a declared total fat) and says whether the
     * band survives. True when nothing is missing.
     */
    static boolean bandSettled(Nutrition n, Beverage identity) {
        return bandSettled(n, identity, FoodKind.GENERAL);
    }

    /** As above, scored with the table the product is scored with (fats and oils, cheese, red meat). */
    static boolean bandSettled(Nutrition n, Beverage identity, FoodKind kind) {
        if (n.missingNegatives().isEmpty()) return true;
        return grade(bestCase(n), identity, kind).band == grade(worstCase(n), identity, kind).band;
    }

    /**
     * The label at the best it could be: a missing figure at the least the label allows. That is zero, except that
     * total sugars are never below a declared added sugar ("including 12 g of added sugar").
     */
    static Nutrition bestCase(Nutrition n) {
        Nutrition best = n.copy();
        if (best.sugarsG == null && n.addedSugarsG != null) best.sugarsG = n.addedSugarsG;
        return best;
    }

    /**
     * The label at the worst it could be: each missing sugar, saturated-fat or sodium figure at the most the label
     * allows (saturated fat never above a declared total fat, sugars never above declared carbohydrate).
     */
    static Nutrition worstCase(Nutrition n) {
        Nutrition worst = n.copy();
        if (worst.sugarsG == null) worst.sugarsG = n.carbohydrateG != null ? n.carbohydrateG : 100.0;
        if (worst.saturatedFatG == null) worst.saturatedFatG = n.totalFatG != null ? n.totalFatG : 100.0;
        if (worst.sodiumMg == null) worst.sodiumMg = 5000.0;
        return worst;
    }

    /**
     * The fork between the two branches. Drinks go to {@link BeverageGrade}, which runs the updated
     * 2023 beverage algorithm; everything else stays here on the food method. They are separate
     * because one scale cannot serve both — and because a drink can be sweetened without sugar,
     * which the food method has no way to see.
     */
    static Result grade(Nutrition n, Beverage identity) {
        return grade(n, identity, FoodKind.GENERAL);
    }

    /** As above, with the 2023 food table chosen by what the product is (see {@link FoodCategory#kind}). */
    static Result grade(Nutrition n, Beverage identity, FoodKind kind) {
        Beverage settled = identify(n, identity);
        if (settled.isDrink()) return BeverageGrade.grade(n, settled);
        return food(n, settled, kind == null ? FoodKind.GENERAL : kind);
    }

    /**
     * What the product is scored as. The label's own numbers get the last word on the kind of drink
     * ({@link Beverage#checkedAgainst}); and a label stating its values per 100 ml is evidence of a
     * drink unless something has positively identified a food — ice cream is labelled per 100 ml
     * and is still eaten.
     */
    static Beverage identify(Nutrition n, Beverage identity) {
        Beverage settled = identity == null ? Beverage.NOT_A_DRINK : identity.checkedAgainst(n);
        if (!settled.isDrink() && n.beverage && !settled.definitelyFood) return Beverage.of(Beverage.Kind.OTHER);
        return settled;
    }

    /** The packaged-food branch: the 2023 Nutri-Score food algorithm, per 100 g. */
    private static Result food(Nutrition n, Beverage identity, FoodKind kind) {
        double kj = n.energyKcal * Evidence.d("fao.kj.per.kcal").doubleValue();
        boolean fats = kind == FoodKind.FATS_OILS_NUTS;
        // Fats, oils, nuts and seeds: energy from saturates and the saturates share of fat replace
        // energy and saturated fat. A missing figure scores zero, as the method requires.
        int energy = fats ? points(value(n.saturatedFatG) * Evidence.d("ns2023.fats.kj.per.g.saturates").doubleValue(), SAT_ENERGY_KJ)
            : points(kj, ENERGY_KJ);
        int satFat = fats ? ratioPoints(n) : points(value(n.saturatedFatG), SAT_FAT);
        int sugars = points(value(n.sugarsG), SUGARS);
        int sodium = points(saltG(n), SALT_G);
        int fibre = points(value(n.fibreG), FIBRE);
        int protein = points(value(n.proteinG), PROTEIN);
        if (kind == FoodKind.RED_MEAT) protein = Math.min(protein, Evidence.d("ns2023.red.meat.protein.max").intValue());
        // Fruit, vegetables and legumes (nuts no longer count here in 2023). Credited only when the
        // share is stated or the product is plainly nothing else: guessing a share would be invention.
        int nuts = nutPoints(identity.fruitPercent);
        int negative = energy + sugars + satFat + sodium;
        // 2023: at 11 A points or more (7 for fats, oils, nuts and seeds) protein stops offsetting them,
        // except for cheese, whose protein always counts.
        int limit = Evidence.d(fats ? "ns2023.fats.protein.rule" : "ns2023.general.protein.rule").intValue();
        int score = negative >= limit && kind != FoodKind.CHEESE ? negative - fibre - nuts : negative - fibre - protein - nuts;
        Band band = foodBand(score);

        String per = " per " + n.unit();
        List<String> notes = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        if (value(n.sugarsG) > HIGH_SUGARS_G) concerns.add("High sugar: " + Nutrition.number(n.sugarsG) + " g" + per);
        if (value(n.saturatedFatG) > HIGH_SAT_FAT_G) concerns.add("High saturated fat: " + Nutrition.number(n.saturatedFatG) + " g" + per);
        // "High" at the UK front-of-pack level, 1.5 g salt (600 mg sodium) per 100 g. It still counts in
        // full inside the score from 90 mg up; the card just doesn't lead with it below "high".
        if (n.sodiumMg != null && n.sodiumMg > HIGH_SODIUM_FOOD_MG) concerns.add("High sodium: " + Nutrition.number(n.sodiumMg) + " mg" + per);
        if (energy >= 7) concerns.add("Energy dense: " + Math.round(n.energyKcal) + " kcal" + per);
        // Not part of the method, but a sourced line: FSSAI allows "trans fat free" below 0.2 g.
        if (n.transFatG != null && n.transFatG >= 0.2) {
            concerns.add("Trans fat: " + Nutrition.number(n.transFatG) + " g" + per + " (best kept near zero)");
        }
        if (concerns.isEmpty() && band != Band.A) concerns.add(moderate(n, per, sugars, satFat, sodium, energy));

        List<String> positives = Highlights.of(n, identity, false);
        addedSugar(n, per, concerns, positives);
        if (positives.isEmpty() && concerns.isEmpty()) {
            // Only what was read can be called not high: a figure missing from the label is never cleared.
            List<String> read = new ArrayList<>();
            if (n.sugarsG != null) read.add("sugar");
            if (n.saturatedFatG != null) read.add("saturated fat");
            if (n.sodiumMg != null) read.add("sodium");
            if (!read.isEmpty()) notes.add("Nothing high in " + (read.size() == 1 ? read.get(0)
                : String.join(", ", read.subList(0, read.size() - 1)) + " or " + read.get(read.size() - 1)));
        }
        if (fats) notes.add("Scored as a fat, oil, nut or seed (Nutri-Score 2023): by its saturated fat share, not its calories.");
        return new Result(band, score, notes, concerns, positives, caveats(n), identity, kind);
    }

    /** Names the largest middling contributor, instead of a vague "moderate something". */
    private static String moderate(Nutrition n, String per, int sugars, int satFat, int sodium, int energy) {
        int top = Math.max(Math.max(sugars, satFat), Math.max(sodium, energy));
        if (top == sugars && n.sugarsG != null) return "Some sugar: " + Nutrition.number(n.sugarsG) + " g" + per;
        if (top == satFat && n.saturatedFatG != null) return "Some saturated fat: " + Nutrition.number(n.saturatedFatG) + " g" + per;
        if (top == sodium && n.sodiumMg != null) return "Some sodium: " + Nutrition.number(n.sodiumMg) + " mg" + per;
        return "Fairly energy dense: " + Math.round(n.energyKcal) + " kcal" + per;
    }

    /**
     * What the label's own added-sugar line says. It never changes the score, which counts total
     * sugar as the method requires; it tells the shopper how much of that sugar was put there.
     */
    static void addedSugar(Nutrition n, String per, List<String> concerns, List<String> positives) {
        if (n.addedSugarsG == null) return;
        if (n.addedSugarsG >= 0.5) {
            concerns.add(n.sugarsG != null && n.sugarsG > n.addedSugarsG + 0.05
                ? "Added sugar: " + Nutrition.number(n.addedSugarsG) + " g of its " + Nutrition.number(n.sugarsG) + " g sugar" + per
                : "Added sugar: " + Nutrition.number(n.addedSugarsG) + " g" + per);
        } else if (n.sugarsG != null && n.sugarsG > 0.5) {
            positives.add("No added sugar, as the label declares");
        }
    }

    static List<String> caveats(Nutrition n) {
        List<String> caveats = new ArrayList<>();
        if (n.basisNote != null) caveats.add(n.basisNote);
        if (n.energyFromMacros) caveats.add("Energy is not on the label: worked out from its protein, carbohydrate and fat, "
            + "by FSSAI's factors.");
        if (n.ocrRepaired) caveats.add("Some figures in the photo were small and hard to read. Open the nutrition "
            + "photo full-screen and zoom in, and I'll read it again.");
        // A missing sugar, saturated-fat or sodium figure is reported by GradeResult.graded, which shows a colour
        // only when no value of the missing figure could change it, and says so.
        return caveats;
    }

    /**
     * The nutrients this product is "high" in — exactly the levels at which the card prints a
     * "High …" line. A healthier swap may never be high in something the original was not, so these
     * are computed once, here, for both branches.
     */
    static java.util.Set<String> highFlags(Nutrition n, boolean drink) {
        if (drink) return BeverageGrade.highFlags(n);
        java.util.Set<String> high = new java.util.HashSet<>();
        if (value(n.sugarsG) > HIGH_SUGARS_G) high.add("sugars");
        if (value(n.saturatedFatG) > HIGH_SAT_FAT_G) high.add("saturated_fat");
        if (value(n.sodiumMg) > HIGH_SODIUM_FOOD_MG) high.add("sodium");
        if (n.energyKcal != null && points(n.energyKcal * Evidence.d("fao.kj.per.kcal").doubleValue(), ENERGY_KJ) >= 7) high.add("energy");
        return high;
    }

    /** Food-method points for one adverse nutrient, used to find what a product is worst at. */
    static int foodPoints(String nutrient, Nutrition n) {
        switch (nutrient) {
            case "sugars": return points(value(n.sugarsG), SUGARS);
            case "saturated_fat": return points(value(n.saturatedFatG), SAT_FAT);
            case "sodium": return points(saltG(n), SALT_G);
            case "energy": return n.energyKcal == null ? 0 : points(n.energyKcal * Evidence.d("fao.kj.per.kcal").doubleValue(), ENERGY_KJ);
            default: return 0;
        }
    }

    /** Salt in g per 100 g from the declared sodium, by WHO's pairing (2 g sodium = 5 g salt). */
    static double saltG(Nutrition n) {
        if (n.sodiumMg == null) return 0;
        return NutritionCalculator.saltFromSodium(java.math.BigDecimal.valueOf(n.sodiumMg)).d() / 1000;
    }

    /** Saturates as a share of total fat: 0 points under 10 %, one more at each step, 10 at 64 % or more. */
    static int ratioPoints(Nutrition n) {
        if (n.saturatedFatG == null || n.totalFatG == null || n.totalFatG <= 0) return 0;
        double ratio = 100 * n.saturatedFatG / n.totalFatG;
        int p = 0;
        for (double t : SAT_RATIO) if (ratio >= t) p++;
        return p;
    }

    /** The food method's fruit, vegetable and legume points (unchanged in 2023). */
    static int nutPoints(int percent) {
        if (percent > 80) return 5;
        if (percent > 60) return 2;
        if (percent > 40) return 1;
        return 0;
    }

    private static Band foodBand(int score) {
        if (score <= 2) return Band.A;   // Nutri-Score A (<= -1) and B (0..2)
        if (score <= 10) return Band.B;  // Nutri-Score C
        return Band.C;                   // Nutri-Score D and E
    }

    private static double value(Double v) { return v == null ? 0 : v; }

    private static int points(double value, double[] thresholds) {
        int points = 0;
        for (double threshold : thresholds) if (value > threshold) points++;
        return points;
    }

    private NutriGrade() {}
}
