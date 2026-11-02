package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Goal priorities are comparison rules from {@link RecommendationContext}, not medical targets or a
 * health score. Every number in the lines below comes from {@link DailyNeeds}, i.e. the calculator.
 */
final class GoalComparisons {
    /** Food choices narrow recommendation cards, never nutrient evidence or restriction warnings. */
    static boolean allowsGroup(Profile p, String group) {
        if (p == null || p.buyCodes().isEmpty()) return true; // skipped form remains usable
        String code = buyCode(group);
        // Staples no "What do you buy" answer covers (atta, rice, millets) are never hidden by those answers.
        return code == null || p.buyCodes().contains(code);
    }
    /** The "What do you buy regularly?" answer that turns on comparisons for a swap group. */
    static String buyCode(String group) {
        switch (group == null ? "" : group) {
            case "salty_snacks": case "nuts_seeds": case "dried_fruit": return "snacks";
            case "biscuits": return "biscuits";
            case "soft_drinks": case "fruit_drinks": return "drinks";
            case "flavoured_milk": case "ice_cream": case "curd_yoghurt": case "cheese": case "paneer_tofu": return "dairy";
            case "instant_noodles": case "pasta_vermicelli": return "noodles";
            case "breakfast_cereals": return "breakfast";
            case "chocolate": case "sweets_mithai": return "chocolate";
            case "spreads": case "bread": case "cakes": case "jams": return "bread";
            case "ready_meals": case "frozen_snacks": return "readymade";
            case "oils_fats": return "oils";
            case "ketchup_sauces": case "mayo_dips": case "soups": case "pickles": case "masalas": return "masala";
            case "health_drinks": case "bars": return "health";
            default: return null;
        }
    }

    /** True when a dietitian should be suggested. Filtering still runs; nothing is switched off. */
    static boolean needsClinicalReview(Profile p) {
        return p != null && !RecommendationContext.of(p).referrals.isEmpty();
    }

    /** The ranking focus for a goal-driven comparison, or null for the general (awareness) path. */
    static String focus(Profile p) {
        return focus(RecommendationContext.of(p));
    }

    static String focus(RecommendationContext c) {
        switch (c.objective) {
            case LOWER_ENERGY: return "lower_energy";
            case HIGHER_ENERGY: return "higher_energy";
            case MORE_PROTEIN: return "protein";
            case PROTEIN_DENSITY: return "protein_density";
            case LOWER_SUGAR: return "lower_sugar";
            default: return null;
        }
    }

    static List<String> explain(Profile p, Nutrition n) {
        RecommendationContext c = RecommendationContext.of(p);
        DailyNeeds needs = DailyNeeds.of(c);
        List<String> lines = new ArrayList<>();
        if (needs.personal()) lines.add(needs.summary());
        if (c.bmi != null) lines.add("BMI " + NutritionCalculator.format(Locale.ROOT, c.bmi.doubleValue(), 1) + ": "
            + bandName(c.bmiBand) + " by NIN 2024's Asian cut-offs (" + ev("nin.bmi.underweight.below") + " / " + ev("nin.bmi.normal.max") + " / "
            + ev("nin.bmi.overweight.max") + "). BMI misjudges very muscular and older people.");
        if (Boolean.TRUE.equals(c.centralObesity)) lines.add("Your waist is above NIN's cut-off, so swaps must not add saturated or trans fat.");
        switch (c.objective) {
            case LOWER_ENERGY:
                lines.add("For your weight-loss goal: swaps need at least " + threshold() + " less energy for the same portion, without less protein or fibre.");
                if (needs.personal()) {
                    NutritionCalculator.Result[] range = NutritionCalculator.weightLossRange(
                        new NutritionCalculator.Result(java.math.BigDecimal.valueOf(needs.energyKcal), "kcal/day", ""));
                    lines.add("NIN 2024 describes weight-loss diets " + range[0].shown(0) + "-" + range[1].shown(0)
                        + " kcal a day for you (" + ev("nin.weightloss.deficit.low") + "-" + ev("nin.weightloss.deficit.high") + " below needs, never under "
                        + ev("nin.weightloss.min.kcal") + "), about half a kilo a week. The app does not set your diet.");
                }
                break;
            case HIGHER_ENERGY:
                lines.add("For your weight-gain goal: swaps need at least " + threshold() + " more energy for the same portion, with no less protein and no more sugar or saturated fat.");
                break;
            case MORE_PROTEIN:
                lines.add(Double.isFinite(needs.muscleProteinLowG)
                    ? "For your muscle goal: " + DailyNeeds.whole(needs.muscleProteinLowG) + "-" + DailyNeeds.whole(needs.muscleProteinHighG)
                        + " g protein a day (" + ev("issn.protein.muscle.min") + "-" + ev("nin.protein.muscle.max") + " g per kg: ISSN 2017, and NIN 2024 finds more than about "
                        + ev("nin.protein.muscle.max") + " adds nothing). Swaps need at least " + threshold() + " more protein for the same portion."
                    : "For your muscle goal: swaps need at least " + threshold() + " more protein for the same portion. Add your weight for a daily range.");
                break;
            case PROTEIN_DENSITY:
                lines.add("For weight loss with muscle: swaps need at least " + threshold() + " more protein per calorie, and no more energy for the same portion.");
                break;
            case LOWER_SUGAR:
                lines.add("For sugar control: swaps need at least " + threshold() + " less sugar (added sugar where declared) and no more carbohydrate for the same portion.");
                break;
            default:
                lines.add("For nutritional awareness: swaps must be in a better band and cut this product's worst nutrient by at least "
                    + threshold() + ", for the same portion.");
        }
        lines.addAll(c.notes);
        if (c.diabetes) lines.add("Diabetes: swaps never have more carbohydrate or sugar for the same portion" + (c.diabetesType1
            ? "; carbohydrate grams per portion are shown for carb counting." : ".") + " Treatment targets are not calculated.");
        if (c.hypertension) lines.add("Blood pressure: swaps never have more sodium, and lower sodium ranks first.");
        if (c.kidney) lines.add("Kidney condition: swaps never have more sodium" + (c.dialysis ? "" : " or protein")
            + "; potassium salt substitutes (WHO) and phosphate additives (KDIGO 2017) are left out.");
        if (c.cholesterol) lines.add("Cholesterol or heart: swaps never have more saturated or trans fat.");
        if (c.trimester != 0) lines.add("Pregnancy: products the label says are not for pregnant women are left out (FSSAI warnings: aspartame, acesulfame K, MSG, plant stanols).");
        if (c.lactation != 0) lines.add("Breastfeeding: products the label says are not for lactating mothers are left out.");
        if (c.lactose) lines.add("Lactose intolerance: products without milk ingredients rank first; milk is flagged, not blocked, "
            + "and dairy shows its lactose.");
        if (c.diet == DietRules.Diet.VEGAN) lines.add("Vegan: animal ingredients are filtered; undeclared B12 or other micronutrients remain unknown.");
        if (c.diet.provisional()) lines.add(c.diet.label + ": checked from the ingredient list; practice varies"
            + (c.diet == DietRules.Diet.HALAL ? ", and only a halal mark on the pack confirms it." : "."));
        if (!c.fromNote.isEmpty()) lines.add("From your note we also applied: " + String.join(", ", c.fromNote) + ". Edit your form if that's wrong.");
        if (!c.referrals.isEmpty()) lines.add("Worth talking to a dietitian: " + String.join("; ", c.referrals) + ".");
        if (n != null) {
            Double serving = PackMath.servingSize(n);
            lines.add(serving == null ? "No serving amount was read. Comparisons use the same 100 " + n.measure() + "; this is not a recommended portion."
                : "Comparisons use the same " + Nutrition.number(serving) + " " + n.measure() + " label serving from the original product.");
        }
        return lines;
    }

    /** The difference a swap must make, as a percentage: FSSAI's rule for a comparative claim. */
    static String threshold() { return NutritionCalculator.comparativeMin().movePointRight(2).stripTrailingZeros().toPlainString() + "%"; }

    private static String ev(String id) { return Evidence.d(id).stripTrailingZeros().toPlainString(); }

    private static String bandName(NutritionCalculator.BmiBand band) {
        switch (band) {
            case UNDERWEIGHT: return "underweight";
            case NORMAL: return "normal";
            case OVERWEIGHT: return "overweight";
            default: return "obese";
        }
    }

    /** Signed proportional improvement at an identical mass/volume; unknown values cannot qualify. */
    static double improvement(String focus, Nutrition original, Nutrition candidate) {
        if (focus == null || original == null || candidate == null || original.beverage != candidate.beverage) return Double.NaN;
        switch (focus) {
            case "protein_density": {
                if (!NutrientMath.positive(original.energyKcal) || !NutrientMath.positive(candidate.energyKcal)) return Double.NaN;
                if (!NutrientMath.positive(original.proteinG) || !NutrientMath.nonNegative(candidate.proteinG)) return Double.NaN;
                double a = original.proteinG / original.energyKcal, b = candidate.proteinG / candidate.energyKcal;
                return (b - a) / a;
            }
            case "lower_sugar": {
                boolean added = original.addedSugarsG != null && candidate.addedSugarsG != null;
                Double a = added ? original.addedSugarsG : original.sugarsG, b = added ? candidate.addedSugarsG : candidate.sugarsG;
                if (!NutrientMath.positive(a) || !NutrientMath.nonNegative(b)) return Double.NaN;
                return (a - b) / a;
            }
            default: {
                Double a = "protein".equals(focus) ? original.proteinG : original.energyKcal;
                Double b = "protein".equals(focus) ? candidate.proteinG : candidate.energyKcal;
                if (!NutrientMath.positive(a) || !NutrientMath.nonNegative(b)) return Double.NaN;
                return "lower_energy".equals(focus) ? (a - b) / a : (b - a) / a;
            }
        }
    }

    /** Share of sodium saved at the same mass/volume, for a shopper who recorded high blood pressure; else NaN. */
    static double sodiumReduction(SafetyProfile safety, Nutrition original, Nutrition candidate) {
        if (safety == null || !safety.has("hypertension") || original == null || candidate == null
                || original.beverage != candidate.beverage) return Double.NaN;
        if (!NutrientMath.positive(original.sodiumMg) || !NutrientMath.nonNegative(candidate.sodiumMg)) return Double.NaN;
        return (original.sodiumMg - candidate.sodiumMg) / original.sodiumMg;
    }

    static String benefit(String focus, Nutrition original, Nutrition candidate) {
        double share = improvement(focus, original, candidate);
        String what;
        switch (focus) {
            case "protein_density": what = "more protein per calorie"; break;
            case "lower_sugar": what = (original.addedSugarsG != null && candidate.addedSugarsG != null ? "less added sugar" : "less sugar"); break;
            case "protein": what = "more protein"; break;
            case "lower_energy": what = "less energy"; break;
            default: what = "more energy"; break;
        }
        Double serving = PackMath.servingSize(original);
        return Math.round(share * 100) + "% " + what + " for the same " + Nutrition.number(serving == null ? 100 : serving)
            + " " + original.measure() + " · at least " + threshold() + ", FSSAI's bar for a comparative claim; check the other nutrients too";
    }
    private GoalComparisons() {}
}
