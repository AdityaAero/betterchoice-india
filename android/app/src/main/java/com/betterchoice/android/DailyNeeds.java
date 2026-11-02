package com.betterchoice.android;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Daily references for this shopper, every number computed by {@link NutritionCalculator} from
 * {@link Evidence} and carried with its working.
 *
 * <ul>
 *   <li>Energy: FAO/WHO/UNU 2004 basal rate × ICMR-NIN 2020 Indian factor (0.90) × ICMR activity level,
 *       plus ICMR's pregnancy or lactation addition. The weight before pregnancy is used when given.</li>
 *   <li>Limits as shares of that energy: fat (NIN 2024), saturated and trans fat (WHO 2023), free sugars
 *       (WHO 2015), carbohydrate range (NIN 2024). Sodium (WHO 2012) and fibre (WHO 2023) are the same
 *       for everyone.</li>
 *   <li>Protein: ICMR 2020 RDA per kg (1 g/kg for vegetarian diets), on the weight at BMI 23 when the
 *       BMI is over 27.5, plus pregnancy or lactation RDA. Hidden for a kidney condition not on dialysis,
 *       where only a clinician can set it.</li>
 * </ul>
 * No usable weight, or not an adult: FSSAI's label reference for an average adult. The app never knows
 * what else is eaten, so it never claims a remaining allowance, and it never sets a deficit or surplus.
 */
final class DailyNeeds {
    static final String VERSION = "india-personal-2026-10-v4";
    enum Basis {
        PERSONAL("an estimate from your weight, age, gender and activity"),
        REFERENCE("a population reference"),
        GENERIC("FSSAI's 2000 kcal label reference, not your calorie requirement");
        final String description;
        Basis(String description) { this.description = description; }
    }

    final Basis basis;
    final NutritionCalculator.Sex sex;
    final RecommendationContext context;
    final double energyKcal, maintenanceKcal, stateExtraKcal, energyErrorKcal;
    /** Relative uncertainty of the energy estimate, from the basal equation's standard error. */
    final double energyUncertainty;
    final double proteinG, muscleProteinLowG, muscleProteinHighG, fibreG;
    final double sugarLimitG, sugarBetterG;
    final double totalFatLimitG, saturatedFatLimitG, transFatLimitG, sodiumLimitMg;
    final double carbohydrateLowG, carbohydrateHighG;
    final double weightKg;
    final String goal, proteinNote, energyNote;
    /** "How we calculated this": one line per number, formula with its inputs. */
    final List<String> workings;

    private DailyNeeds(RecommendationContext c) {
        this.context = c;
        this.goal = c == null ? "" : c.profile.answers[Profile.GOAL];
        List<String> work = new ArrayList<>();
        boolean personal = c != null && c.adult && c.weightKg != null;
        this.basis = personal ? Basis.PERSONAL : Basis.GENERIC;
        this.sex = personal ? c.sex : NutritionCalculator.Sex.AVERAGE;
        NutritionCalculator.Result fibre = NutritionCalculator.fibreMin();
        NutritionCalculator.Result sodium = NutritionCalculator.sodiumMax();
        this.fibreG = fibre.d();
        this.sodiumLimitMg = sodium.d();
        if (!personal) {
            this.energyKcal = Evidence.d("fssai.label.energy").doubleValue();
            this.maintenanceKcal = energyKcal;
            this.stateExtraKcal = 0; this.energyErrorKcal = 0; this.energyUncertainty = 0;
            this.totalFatLimitG = Evidence.d("fssai.label.fat").doubleValue();
            this.saturatedFatLimitG = Evidence.d("fssai.label.saturated.fat").doubleValue();
            this.transFatLimitG = Evidence.d("fssai.label.trans.fat").doubleValue();
            this.sugarLimitG = Evidence.d("fssai.label.added.sugar").doubleValue();
            NutritionCalculator.Result label = new NutritionCalculator.Result(BigDecimal.valueOf(energyKcal), "kcal/day", "FSSAI label reference");
            this.sugarBetterG = NutritionCalculator.sugarBetter(label).d();
            this.carbohydrateLowG = NutritionCalculator.carbohydrateLow(label).d();
            this.carbohydrateHighG = NutritionCalculator.carbohydrateHigh(label).d();
            this.proteinG = Double.NaN; this.muscleProteinLowG = Double.NaN; this.muscleProteinHighG = Double.NaN;
            this.weightKg = Double.NaN;
            this.proteinNote = "Protein needs are not estimated without adult eligibility and a valid stated weight.";
            this.energyNote = null;
            work.add("FSSAI label reference (Labelling and Display Regulations 5(3)): 2000 kcal, 67 g fat, 22 g saturated fat, "
                + "2 g trans fat, 50 g added sugar, 2000 mg sodium.");
            this.workings = Collections.unmodifiableList(work);
            return;
        }
        BigDecimal kg = c.weightKg;
        this.weightKg = kg.doubleValue();
        NutritionCalculator.Result maintenance = NutritionCalculator.maintenance(c.sex, c.age, kg, c.activity);
        int lactationBand = Math.max(0, c.lactation);
        NutritionCalculator.Result extra = NutritionCalculator.stateExtra(Math.max(0, c.trimester), lactationBand,
            c.bmiBand == NutritionCalculator.BmiBand.UNDERWEIGHT);
        NutritionCalculator.Result energy = new NutritionCalculator.Result(maintenance.value.add(extra.value), "kcal/day",
            maintenance.shown(0) + (extra.value.signum() > 0 ? " + " + extra.shown(0) : ""));
        NutritionCalculator.Result error = NutritionCalculator.maintenanceError(c.sex, c.age, c.activity);
        this.maintenanceKcal = maintenance.d();
        this.stateExtraKcal = extra.d();
        this.energyKcal = energy.d();
        this.energyErrorKcal = error.d();
        this.energyUncertainty = error.d() / energy.d();
        work.add("Energy to keep your weight: " + maintenance.working());
        if (extra.value.signum() > 0) work.add("Added for " + (c.trimester > 0 ? "pregnancy" : "breastfeeding") + ": " + extra.working());
        work.add("± " + error.shown(0) + " kcal: " + error.formula);

        NutritionCalculator.Result fat = NutritionCalculator.fatMax(energy), sat = NutritionCalculator.saturatedFatMax(energy);
        NutritionCalculator.Result trans = NutritionCalculator.transFatMax(energy), sugar = NutritionCalculator.sugarMax(energy);
        NutritionCalculator.Result better = NutritionCalculator.sugarBetter(energy);
        NutritionCalculator.Result carbLow = NutritionCalculator.carbohydrateLow(energy), carbHigh = NutritionCalculator.carbohydrateHigh(energy);
        this.totalFatLimitG = fat.d(); this.saturatedFatLimitG = sat.d(); this.transFatLimitG = trans.d();
        this.sugarLimitG = sugar.d(); this.sugarBetterG = better.d();
        this.carbohydrateLowG = carbLow.d(); this.carbohydrateHighG = carbHigh.d();
        work.add("Fat limit: " + fat.working());
        work.add("Saturated fat limit: " + sat.working());
        work.add("Trans fat limit: " + trans.working());
        work.add("Free sugars limit: " + sugar.working() + "; better under " + better.working());
        work.add("Carbohydrate range: " + carbLow.working() + " to " + carbHigh.shown(0) + " g");

        boolean kidneyLimitsProtein = c.kidney && !c.dialysis;
        if (kidneyLimitsProtein) {
            this.proteinG = Double.NaN; this.muscleProteinLowG = Double.NaN; this.muscleProteinHighG = Double.NaN;
            this.proteinNote = "Protein isn't estimated for a kidney condition: the right amount depends on your kidney function. Ask your nephrologist.";
        } else {
            NutritionCalculator.Result protein = NutritionCalculator.protein(kg, c.vegetarianProtein(),
                Math.max(0, c.trimester), lactationBand);
            this.proteinG = protein.d();
            work.add("Protein (adequacy, not a maximum): " + protein.working());
            if (c.goals.contains(RecommendationContext.Goal.MUSCLE)) {
                NutritionCalculator.Result[] range = NutritionCalculator.muscleProtein(kg);
                this.muscleProteinLowG = range[0].d(); this.muscleProteinHighG = range[1].d();
                work.add("Muscle-gain protein: " + range[0].working() + " to " + range[1].working());
            } else {
                this.muscleProteinLowG = Double.NaN; this.muscleProteinHighG = Double.NaN;
            }
            this.proteinNote = "Protein: ICMR-NIN RDA " + (c.vegetarianProtein()
                    ? Evidence.d("icmr.protein.vegetarian").stripTrailingZeros().toPlainString() + " g per kg for a vegetarian diet"
                    : Evidence.d("icmr.protein.rda").toPlainString() + " g per kg")
                + " of your weight, the same for men and women. An adequacy reference, not a maximum.";
        }
        work.add("Sodium: under " + sodium.shown(0) + " mg (WHO), the same for everyone. Fibre: at least " + fibre.shown(0) + " g (WHO).");

        StringBuilder note = new StringBuilder(String.format(Locale.ROOT, "About %s kcal a day (±%s)", round10(energyKcal),
            round10(energyErrorKcal)));
        note.append(extra.value.signum() > 0 ? ", including " + extra.shown(0) + " for " + (c.trimester > 0 ? "pregnancy" : "breastfeeding")
            : " keeps your current weight");
        note.append(String.format(Locale.ROOT, ": %s kg, %s, %s.", NutritionCalculator.format(Locale.ROOT, weightKg, 1),
            c.age == null ? "adult age not given" : c.age + " years",
            c.profile.answers[Profile.ACTIVITY].isBlank() ? "activity not given, so mostly sitting"
                : c.profile.answers[Profile.ACTIVITY].toLowerCase(Locale.ROOT)));
        if (c.sex == NutritionCalculator.Sex.AVERAGE) note.append(" Gender isn't Woman or Man, so the women's and men's equations are averaged.");
        this.energyNote = note.toString();
        this.workings = Collections.unmodifiableList(work);
    }

    static DailyNeeds forProfile(Profile profile) { return profile == null ? generic() : of(RecommendationContext.of(profile)); }
    static DailyNeeds of(RecommendationContext context) { return new DailyNeeds(context); }
    static DailyNeeds generic() { return new DailyNeeds(null); }

    boolean personal() { return basis == Basis.PERSONAL; }
    double saltLimitG() { return NutritionCalculator.saltFromSodium(BigDecimal.valueOf(sodiumLimitMg)).d() / 1000; }
    String[][] micronutrients() { return new String[0][]; } // Micronutrient targets need life-stage review.

    /** Acceptable daily intake of an additive for this body weight, in mg a day, or NaN. */
    double additiveAllowanceMg(double adiMgPerKg) {
        return Double.isFinite(weightKg) && adiMgPerKg > 0
            ? NutritionCalculator.adiPerDay(BigDecimal.valueOf(adiMgPerKg), BigDecimal.valueOf(weightKg)).d() : Double.NaN;
    }

    /** The weight the shopper gave, when plausible; ADIs scale with it whatever else is recorded. */
    static Double statedWeight(Profile profile) {
        if (profile == null) return null;
        String raw = profile.answers[Profile.WEIGHT];
        if (raw == null || raw.isBlank()) return null;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) && value >= 30 && value <= 300 ? value : null;
        } catch (NumberFormatException bad) { return null; }
    }

    String summary() {
        if (personal()) return energyNote;
        return String.format(Locale.ROOT, "%s kcal is an Indian label reference, not a personal calorie target.",
            Nutrition.number(energyKcal));
    }

    /** The limits in one line, for the card. */
    String limits() {
        if (!personal()) return "Label references: fat 67 g, saturated fat 22 g, trans fat 2 g, added sugar 50 g "
            + "(better under " + whole(sugarBetterG) + " g), sodium 2000 mg (salt 5 g). Add your weight, age, gender and activity for your own.";
        return String.format(Locale.ROOT, "Your daily limits: fat up to %s g (30%% of energy), saturated fat under %s g, "
                + "trans fat under %s g, added sugar under %s g (better under %s g), sodium under %s mg (salt %s g). "
                + "Carbohydrate %s-%s g (50-55%% of energy). %sFibre at least %s g.",
            whole(totalFatLimitG), whole(saturatedFatLimitG), oneDecimal(transFatLimitG), whole(sugarLimitG),
            whole(sugarBetterG), whole(sodiumLimitMg), oneDecimal(saltLimitG()), whole(carbohydrateLowG), whole(carbohydrateHighG),
            Double.isFinite(proteinG) ? "Protein at least " + whole(proteinG) + " g. " : "", whole(fibreG));
    }

    static String whole(double value) { return NutritionCalculator.format(Locale.ROOT, value, 0); }
    static String oneDecimal(double value) { return NutritionCalculator.format(Locale.ROOT, value, 1); }
    private static String round10(double value) { return whole(Math.round(value / 10.0) * 10.0); }
}
