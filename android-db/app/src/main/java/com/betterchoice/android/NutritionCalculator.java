package com.betterchoice.android;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every personal and per-product number the card shows. Pure decimal arithmetic on values from
 * {@link Evidence}; no constant is typed here. Each {@link Result} carries the formula with its
 * actual inputs and the evidence entries used, so "How we calculated this" can show the working.
 * An independent Python implementation ({@code tools/calc_crosscheck.py}) must agree with it.
 */
final class NutritionCalculator {
    static final MathContext MC = MathContext.DECIMAL64;
    static final BigDecimal ONE = BigDecimal.ONE, HUNDRED = BigDecimal.valueOf(100), THOUSAND = BigDecimal.valueOf(1000);

    enum Sex { FEMALE, MALE, AVERAGE }
    enum Activity { SEDENTARY, MODERATE, HEAVY }
    enum BmiBand { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }

    /** A computed value and its working. */
    static final class Result {
        final BigDecimal value;
        final String unit, formula;
        final List<String> sources;
        Result(BigDecimal value, String unit, String formula, String... sources) {
            this.value = value; this.unit = unit; this.formula = formula;
            this.sources = Collections.unmodifiableList(Arrays.asList(sources));
        }
        double d() { return value.doubleValue(); }
        String shown(int decimals) {
            return value.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        String working() { return formula + " = " + shown(1) + " " + unit; }
    }

    private static BigDecimal e(String id) { return Evidence.d(id); }
    private static String n(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    // ---------------------------------------------------------------- body size

    static Result bmi(BigDecimal kg, BigDecimal cm) {
        BigDecimal m = cm.divide(HUNDRED, MC);
        return new Result(kg.divide(m.multiply(m, MC), MC), "kg/m²", n(kg) + " ÷ " + n(m) + "²");
    }

    static BmiBand bmiBand(BigDecimal bmi) {
        if (bmi.compareTo(e("nin.bmi.underweight.below")) < 0) return BmiBand.UNDERWEIGHT;
        if (bmi.compareTo(e("nin.bmi.normal.max")) <= 0) return BmiBand.NORMAL;
        if (bmi.compareTo(e("nin.bmi.overweight.max")) <= 0) return BmiBand.OVERWEIGHT;
        return BmiBand.OBESE;
    }

    /** NIN 2024 abdominal obesity. Only Woman/Man answers select a cut-off; otherwise unknown (null). */
    static Boolean centralObesity(Sex sex, BigDecimal waistCm) {
        if (waistCm == null || sex == Sex.AVERAGE) return null;
        return waistCm.compareTo(e(sex == Sex.MALE ? "nin.waist.male.max" : "nin.waist.female.max")) > 0;
    }

    // ---------------------------------------------------------------- energy

    private static String band(Integer age) {
        BigDecimal middle = e("bmr.age.band.middle"), older = e("bmr.age.band.older");
        // An adult who did not give an age: the middle band, the widest one.
        if (age == null) return middle.toPlainString();
        BigDecimal years = BigDecimal.valueOf(age);
        return years.compareTo(middle) < 0 ? "18" : years.compareTo(older) < 0 ? middle.toPlainString() : older.toPlainString();
    }

    static Result bmr(Sex sex, Integer age, BigDecimal kg) {
        if (sex == Sex.AVERAGE) {
            Result f = bmr(Sex.FEMALE, age, kg), m = bmr(Sex.MALE, age, kg);
            return new Result(f.value.add(m.value).divide(BigDecimal.valueOf(2), MC), "kcal/day",
                "(" + f.formula + " + " + m.formula + ") ÷ 2", "bmr.female." + band(age) + ".a", "bmr.male." + band(age) + ".a");
        }
        String key = "bmr." + (sex == Sex.MALE ? "male" : "female") + "." + band(age);
        BigDecimal a = e(key + ".a"), b = e(key + ".b");
        return new Result(a.multiply(kg, MC).add(b), "kcal/day", n(a) + " × " + n(kg) + " + " + n(b), key + ".a", key + ".b");
    }

    /** ICMR-NIN 2020: 5% (2010) and another 5% (2020) off the FAO/WHO/UNU basal rate for Indian adults. */
    static BigDecimal indiaFactor() {
        return ONE.subtract(e("icmr.bmr.reduction.2010")).subtract(e("icmr.bmr.reduction.2020"));
    }

    /**
     * Activity level. Sedentary is stated (1.40); moderate and heavy keep ICMR's own ratios to it, read
     * from its reference energies (Table 1a), so the estimate reproduces ICMR at its reference weights.
     */
    static BigDecimal pal(Sex sex, Activity activity) {
        if (sex == Sex.AVERAGE) {
            return pal(Sex.FEMALE, activity).add(pal(Sex.MALE, activity)).divide(BigDecimal.valueOf(2), MC);
        }
        BigDecimal sedentary = e("icmr.pal.sedentary");
        if (activity == Activity.SEDENTARY) return sedentary;
        String who = sex == Sex.MALE ? "male" : "female";
        String level = activity == Activity.MODERATE ? "moderate" : "heavy";
        return sedentary.multiply(e("icmr.eer." + who + "." + level), MC).divide(e("icmr.eer." + who + ".sedentary"), MC);
    }

    /** Energy to keep the current weight: basal rate × India factor × activity level. */
    static Result maintenance(Sex sex, Integer age, BigDecimal kg, Activity activity) {
        if (sex == Sex.AVERAGE) {
            Result f = maintenance(Sex.FEMALE, age, kg, activity), m = maintenance(Sex.MALE, age, kg, activity);
            return new Result(f.value.add(m.value).divide(BigDecimal.valueOf(2), MC), "kcal/day",
                "average of the women's (" + f.shown(0) + ") and men's (" + m.shown(0) + ") estimates",
                "bmr.female." + band(age) + ".a", "bmr.male." + band(age) + ".a", "icmr.pal.sedentary");
        }
        Result basal = bmr(sex, age, kg);
        BigDecimal factor = indiaFactor(), pal = pal(sex, activity);
        return new Result(basal.value.multiply(factor, MC).multiply(pal, MC), "kcal/day",
            "(" + basal.formula + ") × " + n(factor) + " × " + pal.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
            basal.sources.get(0), basal.sources.get(1), "icmr.bmr.reduction.2010", "icmr.bmr.reduction.2020", "icmr.pal.sedentary");
    }

    /** One standard error of the basal equation carried through the same factors: the ± on the card. */
    static Result maintenanceError(Sex sex, Integer age, Activity activity) {
        if (sex == Sex.AVERAGE) {
            BigDecimal f = maintenanceError(Sex.FEMALE, age, activity).value, m = maintenanceError(Sex.MALE, age, activity).value;
            return new Result(f.max(m), "kcal/day", "the larger of the two equations' errors", "bmr.female." + band(age) + ".see");
        }
        String key = "bmr." + (sex == Sex.MALE ? "male" : "female") + "." + band(age) + ".see";
        return new Result(e(key).multiply(indiaFactor(), MC).multiply(pal(sex, activity), MC), "kcal/day",
            n(e(key)) + " × " + n(indiaFactor()) + " × activity level", key);
    }

    /** Extra energy in pregnancy (from the second trimester; more when underweight) or while breastfeeding. */
    static Result stateExtra(int trimester, int lactationMonthsBand, boolean underweight) {
        if (trimester > 0) {
            if (trimester < e("nin.pregnancy.kcal.from.trimester").intValue()) {
                return new Result(BigDecimal.ZERO, "kcal/day", "first trimester: no addition", "nin.pregnancy.kcal.from.trimester");
            }
            BigDecimal extra = e("icmr.pregnancy.kcal");
            if (underweight) extra = extra.add(e("nin.pregnancy.undernourished.kcal"));
            return new Result(extra, "kcal/day", underweight ? n(e("icmr.pregnancy.kcal")) + " + "
                + n(e("nin.pregnancy.undernourished.kcal")) + " (underweight)" : n(extra) + " (trimester " + trimester + ")",
                "icmr.pregnancy.kcal", "nin.pregnancy.undernourished.kcal");
        }
        if (lactationMonthsBand > 0) {
            String id = lactationMonthsBand == 1 ? "icmr.lactation.kcal.0to6" : "icmr.lactation.kcal.7to12";
            return new Result(e(id), "kcal/day", n(e(id)) + " (breastfeeding)", id);
        }
        return new Result(BigDecimal.ZERO, "kcal/day", "none");
    }

    // ---------------------------------------------------------------- protein

    /**
     * RDA: 0.83 g/kg (1.0 for vegetarian diets) on the stated body weight, as ICMR states it, plus the
     * pregnancy or lactation RDA. No weight adjustment for obesity: no source on file gives one.
     */
    static Result protein(BigDecimal referenceKg, boolean vegetarianDiet, int trimester, int lactationMonthsBand) {
        String perKgId = vegetarianDiet ? "icmr.protein.vegetarian" : "icmr.protein.rda";
        BigDecimal base = e(perKgId).multiply(referenceKg, MC);
        String extraId = trimester == 2 ? "icmr.protein.pregnancy.t2.rda" : trimester == 3 ? "icmr.protein.pregnancy.t3.rda"
            : lactationMonthsBand == 1 ? "icmr.protein.lactation.0to6.rda" : lactationMonthsBand == 2 ? "icmr.protein.lactation.7to12.rda" : null;
        if (extraId == null) {
            return new Result(base, "g/day", n(e(perKgId)) + " × " + n(referenceKg), perKgId);
        }
        return new Result(base.add(e(extraId)), "g/day", n(e(perKgId)) + " × " + n(referenceKg) + " + " + n(e(extraId)), perKgId, extraId);
    }

    static Result[] muscleProtein(BigDecimal referenceKg) {
        BigDecimal low = e("issn.protein.muscle.min"), high = e("nin.protein.muscle.max");
        return new Result[] {
            new Result(low.multiply(referenceKg, MC), "g/day", n(low) + " × " + n(referenceKg), "issn.protein.muscle.min"),
            new Result(high.multiply(referenceKg, MC), "g/day", n(high) + " × " + n(referenceKg), "nin.protein.muscle.max")};
    }

    // ---------------------------------------------------------------- limits from energy

    /** Grams a share of energy buys: energy × share ÷ kcal per gram (FSSAI conversion factors). */
    static Result grams(Result energy, String shareId, String kcalPerGramId) {
        BigDecimal share = e(shareId), perGram = e(kcalPerGramId);
        return new Result(energy.value.multiply(share, MC).divide(perGram, MC), "g/day",
            new Result(energy.value, "", "").shown(0) + " × " + n(share) + " ÷ " + n(perGram), shareId, kcalPerGramId);
    }

    /**
     * Energy per 100 g from the declared protein, carbohydrate and fat, by the factors FSSAI sets for labelling
     * (Labelling and Display Regulations 2020, regulation 5(3)(e)): the way the label's own energy is worked out.
     */
    static double energyFromMacros(double proteinG, double carbohydrateG, double fatG) {
        return proteinG * Evidence.d("fssai.kcal.per.g.protein").doubleValue()
            + carbohydrateG * Evidence.d("fssai.kcal.per.g.carbohydrate").doubleValue()
            + fatG * Evidence.d("fssai.kcal.per.g.fat").doubleValue();
    }

    static Result fatMax(Result energy) { return grams(energy, "nin.fat.max.share", "fssai.kcal.per.g.fat"); }
    static Result saturatedFatMax(Result energy) { return grams(energy, "who.sfa.max.share", "fssai.kcal.per.g.fat"); }
    static Result transFatMax(Result energy) { return grams(energy, "who.tfa.max.share", "fssai.kcal.per.g.fat"); }
    static Result sugarMax(Result energy) { return grams(energy, "who.free.sugars.max.share", "fssai.kcal.per.g.carbohydrate"); }
    static Result sugarBetter(Result energy) { return grams(energy, "who.free.sugars.better.share", "fssai.kcal.per.g.carbohydrate"); }
    static Result carbohydrateLow(Result energy) { return grams(energy, "nin.carbohydrate.share.low", "fssai.kcal.per.g.carbohydrate"); }
    static Result carbohydrateHigh(Result energy) { return grams(energy, "nin.carbohydrate.share.high", "fssai.kcal.per.g.carbohydrate"); }

    static Result sodiumMax() { return new Result(e("who.sodium.max"), "mg/day", "WHO", "who.sodium.max"); }
    static Result fibreMin() { return new Result(e("who.fibre.min"), "g/day", "WHO", "who.fibre.min"); }

    /** Salt from sodium, by WHO's own pairing of the two limits (2 g sodium = 5 g salt). */
    static Result saltFromSodium(BigDecimal sodiumMg) {
        BigDecimal factor = e("who.salt.max").multiply(THOUSAND, MC).divide(e("who.sodium.max"), MC);
        return new Result(sodiumMg.multiply(factor, MC), "mg", n(sodiumMg) + " × " + n(factor), "who.salt.max", "who.sodium.max");
    }

    /** NIN's weight-loss guidance around the maintenance estimate. Information only; never a target. */
    static Result[] weightLossRange(Result energy) {
        BigDecimal floor = e("nin.weightloss.min.kcal").max(energy.value.multiply(ONE.subtract(e("nin.weightloss.max.deficit.share")), MC));
        BigDecimal low = energy.value.subtract(e("nin.weightloss.deficit.high")).max(floor);
        BigDecimal high = energy.value.subtract(e("nin.weightloss.deficit.low")).max(floor);
        return new Result[] {
            new Result(low, "kcal/day", "max(" + new Result(energy.value, "", "").shown(0) + " − " + n(e("nin.weightloss.deficit.high"))
                + ", floor " + new Result(floor, "", "").shown(0) + ")", "nin.weightloss.deficit.high", "nin.weightloss.min.kcal",
                "nin.weightloss.max.deficit.share"),
            new Result(high, "kcal/day", new Result(energy.value, "", "").shown(0) + " − " + n(e("nin.weightloss.deficit.low")),
                "nin.weightloss.deficit.low")};
    }

    // ---------------------------------------------------------------- products

    /** Amount in a portion: per-100 value × grams ÷ 100. Null when either is unknown. */
    static Result portion(Double per100, double quantity, String unit) {
        if (per100 == null || !Double.isFinite(per100) || per100 < 0 || !(quantity > 0) || !Double.isFinite(quantity)) return null;
        BigDecimal q = BigDecimal.valueOf(quantity);
        return new Result(BigDecimal.valueOf(per100).multiply(q, MC).divide(HUNDRED, MC), unit,
            n(BigDecimal.valueOf(per100)) + " × " + n(q) + " ÷ 100");
    }

    static Result percentOf(Result amount, Result reference) {
        if (amount == null || reference == null || reference.value.signum() <= 0) return null;
        return new Result(amount.value.multiply(HUNDRED, MC).divide(reference.value, MC), "%",
            amount.shown(1) + " × 100 ÷ " + reference.shown(1));
    }

    /** Share by which a candidate is lower (positive) than the original; null when either is unknown. */
    static BigDecimal reduction(Double original, Double candidate) {
        if (original == null || candidate == null || !(original > 0) || candidate < 0) return null;
        return BigDecimal.valueOf(original).subtract(BigDecimal.valueOf(candidate)).divide(BigDecimal.valueOf(original), MC);
    }

    /** The share by which a swap must differ on the nutrient it is chosen for (FSSAI claims regulation 5(4)(a)). */
    static BigDecimal comparativeMin() { return e("fssai.claims.comparative.min"); }

    /**
     * FSSAI Schedule I "source of fibre": at least 3 g per 100 g (1.5 g per 100 ml for a liquid), or
     * 1.5 g per 100 kcal. Unknown fibre is not a source.
     */
    static boolean fibreSource(Nutrition n) {
        if (n == null || n.fibreG == null || n.fibreG < 0) return false;
        BigDecimal fibre = BigDecimal.valueOf(n.fibreG);
        if (fibre.compareTo(e(n.beverage ? "fssai.claims.fibre.source.liquid" : "fssai.claims.fibre.source.solid")) >= 0) return true;
        if (n.energyKcal == null || !(n.energyKcal > 0)) return false;
        BigDecimal per100kcal = fibre.multiply(HUNDRED, MC).divide(BigDecimal.valueOf(n.energyKcal), MC);
        return per100kcal.compareTo(e("fssai.claims.fibre.source.per.100kcal")) >= 0;
    }

    // ---------------------------------------------------------------- caffeine and lactose

    /** Caffeine as a share of the tolerable limit NIN 2024 gives for a day. */
    static Result caffeineShare(Result mg) {
        return percentOf(mg, new Result(e("nin.caffeine.limit"), "mg/day", "NIN 2024", "nin.caffeine.limit"));
    }

    /** How many portions reach NIN 2024's tolerable caffeine limit: limit ÷ mg per portion. */
    static Result portionsToCaffeineLimit(BigDecimal mgPerPortion) {
        if (mgPerPortion == null || mgPerPortion.signum() <= 0) return null;
        return new Result(e("nin.caffeine.limit").divide(mgPerPortion, MC), "portions",
            n(e("nin.caffeine.limit")) + " ÷ " + n(mgPerPortion), "nin.caffeine.limit");
    }

    /**
     * Upper estimate of lactose per 100 g or ml of a dairy food: its sugars less any declared added
     * sugar, because milk's own sugar is lactose (NIN 2024). Fermentation and lactose-free processing
     * only lower it. Null when sugars are not on the label.
     */
    static Result lactoseUpTo(Double sugars, Double addedSugars) {
        if (sugars == null || !Double.isFinite(sugars) || sugars < 0) return null;
        BigDecimal total = BigDecimal.valueOf(sugars);
        BigDecimal added = addedSugars == null || !(addedSugars > 0) ? BigDecimal.ZERO : BigDecimal.valueOf(addedSugars);
        return new Result(total.subtract(added).max(BigDecimal.ZERO), "g", n(total) + " − " + n(added) + " added", "nin.lactose.in.milk");
    }

    /** The amount of a product that holds a given quantity of a nutrient: quantity × 100 ÷ per-100 value. */
    static Result amountHolding(Result per100, BigDecimal quantity, String unit) {
        if (per100 == null || per100.value.signum() <= 0 || quantity == null) return null;
        return new Result(quantity.multiply(HUNDRED, MC).divide(per100.value, MC), unit,
            n(quantity) + " × 100 ÷ " + per100.shown(1));
    }

    static Result adiPerDay(BigDecimal mgPerKg, BigDecimal kg) {
        return new Result(mgPerKg.multiply(kg, MC), "mg/day", n(mgPerKg) + " × " + n(kg));
    }

    /** NIN 2024 Table 15.1/15.2 reading of a label, per 100 g (solid) or 100 ml (liquid). */
    static final class NinClass {
        final int energyClass;            // 1, 2 or 3 for solids; 0 when energy is unknown or for a liquid
        final Boolean energyHigh, saltHigh, addedSugarHigh;   // null = not on the label
        final List<String> reasons;
        NinClass(int energyClass, Boolean energyHigh, Boolean saltHigh, Boolean addedSugarHigh, List<String> reasons) {
            this.energyClass = energyClass; this.energyHigh = energyHigh; this.saltHigh = saltHigh;
            this.addedSugarHigh = addedSugarHigh; this.reasons = reasons;
        }
        boolean hfss() {
            return Boolean.TRUE.equals(energyHigh) || Boolean.TRUE.equals(saltHigh) || Boolean.TRUE.equals(addedSugarHigh);
        }
    }

    /** {@code nutsOrSeeds}: NIN does not class foods naturally high in energy, such as nuts and seeds, as HFSS. */
    static NinClass ninClass(Nutrition n, boolean liquid, boolean nutsOrSeeds) {
        List<String> reasons = new ArrayList<>();
        String kind = liquid ? "liquid" : "solid";
        String per = liquid ? "100 ml" : "100 g";
        Boolean energyHigh = null, saltHigh = null, sugarHigh = null;
        int energyClass = 0;
        if (n.energyKcal != null) {
            BigDecimal kcal = BigDecimal.valueOf(n.energyKcal), limit = e("nin.hfss." + kind + ".kcal");
            energyHigh = !nutsOrSeeds && kcal.compareTo(limit) > 0;
            if (!liquid) energyClass = kcal.compareTo(limit) <= 0 ? 1 : kcal.compareTo(e("nin.hfss.class3.kcal")) <= 0 ? 2 : 3;
            if (energyHigh) reasons.add("Energy " + n(kcal) + " kcal per " + per + " is over NIN's " + n(limit));
        }
        if (n.sodiumMg != null) {
            Result salt = saltFromSodium(BigDecimal.valueOf(n.sodiumMg));
            BigDecimal limit = e("nin.hfss." + kind + ".salt");
            saltHigh = salt.value.compareTo(limit) > 0;
            if (saltHigh) reasons.add("Salt " + salt.shown(0) + " mg per " + per + " is over NIN's " + n(limit) + " mg");
        }
        if (n.addedSugarsG != null) {
            BigDecimal limit = e("nin.hfss." + kind + ".added.sugar");
            sugarHigh = BigDecimal.valueOf(n.addedSugarsG).compareTo(limit) > 0;
            if (sugarHigh) reasons.add("Added sugar " + n(BigDecimal.valueOf(n.addedSugarsG)) + " g per " + per + " is over NIN's " + n(limit) + " g");
        }
        return new NinClass(energyClass, energyHigh, saltHigh, sugarHigh, reasons);
    }

    static String grams(BigDecimal value) { return value.setScale(0, RoundingMode.HALF_UP).toPlainString(); }

    /** A number for display; "—" when there is no number (an unknown is never shown as 0). */
    static String format(Locale locale, double value, int decimals) {
        if (!Double.isFinite(value)) return "—";
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private NutritionCalculator() {}
}
