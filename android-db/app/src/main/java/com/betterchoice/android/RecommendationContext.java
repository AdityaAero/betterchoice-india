package com.betterchoice.android;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The form, read once into the decisions every later step uses: who this is (stage 0), what may
 * never be recommended (vetoes, stage 1), how their state changes the references (stage 2), and
 * what a better choice must achieve and must not worsen (stage 4).
 *
 * <p>Order of care, fixed: safety, then physiological state, then medical condition, then the body
 * goal, then general healthy eating. A lower level never undoes a higher one. Every answer, including
 * blank, "prefer not to say" and free text, leads somewhere defined; every override is written down
 * in {@link #notes} so the card can say why.
 */
final class RecommendationContext {
    enum Goal { LOSS, GAIN, MUSCLE, SUGAR, AWARE }

    /** What a swap must improve, by at least the goal threshold, for the same portion. */
    enum Objective { AWARENESS, LOWER_ENERGY, HIGHER_ENERGY, MORE_PROTEIN, PROTEIN_DENSITY, LOWER_SUGAR }

    /** What a swap must not make worse, for the same portion. */
    enum Constraint {
        ENERGY_NOT_HIGHER, ENERGY_NOT_LOWER, PROTEIN_NOT_LOWER, PROTEIN_NOT_HIGHER, PROTEIN_DENSITY_NOT_LOWER,
        FIBRE_NOT_LOWER, SUGAR_NOT_HIGHER, CARBS_NOT_HIGHER, SATFAT_NOT_HIGHER, TRANSFAT_NOT_HIGHER, SODIUM_NOT_HIGHER
    }

    /** Ranks otherwise equal swaps. */
    enum TieBreak { FIBRE_HIGHER, SODIUM_LOWER, PROTEIN_HIGHER, NO_MILK }

    final Profile profile;
    final boolean adult;
    final NutritionCalculator.Sex sex;
    final Integer age;
    /** Weight used for energy and BMI: pre-pregnancy weight when pregnant and given. */
    final BigDecimal weightKg, heightCm, waistCm;
    final NutritionCalculator.Activity activity;
    final BigDecimal bmi;
    final NutritionCalculator.BmiBand bmiBand;
    final Boolean centralObesity;
    final DietRules.Diet diet;
    final SafetyProfile safety;
    final Set<Goal> goals;
    /** 1-3 pregnant, -1 pregnant with stage not given, 0 not pregnant. */
    final int trimester;
    /** 1 = baby 0-6 months, 2 = 7-12 months, -1 breastfeeding with age not given, 0 not breastfeeding. */
    final int lactation;
    final boolean diabetes, diabetesType1, hypertension, kidney, dialysis, cholesterol, lactose, phenylketonuria;
    /** Free-text conditions the app has no rules for, named back to the shopper. */
    final List<String> unmodelled = new ArrayList<>();
    /** Conditions read from the free-text note, said on the card so the shopper can correct them. */
    final List<String> fromNote = new ArrayList<>();

    Objective objective;
    final EnumSet<Constraint> constraints = EnumSet.noneOf(Constraint.class);
    final Set<TieBreak> tieBreaks = new LinkedHashSet<>();
    final List<String> notes = new ArrayList<>();
    final List<String> referrals = new ArrayList<>();

    private RecommendationContext(Profile p) {
        this.profile = p == null ? new Profile() : p;
        Profile f = this.profile;
        Double ageValue = number(f.answers[Profile.AGE], 18, 120);
        this.age = ageValue == null ? null : (int) Math.floor(ageValue);
        this.adult = f.answers[Profile.AGE].isBlank() ? f.adultConfirmed || p == null : age != null;
        this.sex = sexOf(f.answers[Profile.GENDER]);
        this.activity = "Heavy physical work".equals(f.answers[Profile.ACTIVITY]) ? NutritionCalculator.Activity.HEAVY
            : "Moderately active".equals(f.answers[Profile.ACTIVITY]) ? NutritionCalculator.Activity.MODERATE
            : NutritionCalculator.Activity.SEDENTARY;
        this.safety = SafetyProfile.fromProfile(f);
        this.diet = DietRules.Diet.of(f.dietDetail());

        String stage = f.answers[Profile.PREGNANCY_STAGE];
        this.trimester = !safety.has("pregnancy") ? 0 : "t1".equals(stage) ? 1 : "t2".equals(stage) ? 2 : "t3".equals(stage) ? 3 : -1;
        this.lactation = !safety.has("breastfeeding") ? 0 : "bf0".equals(stage) ? 1 : "bf7".equals(stage) ? 2 : -1;

        String note = f.answers[Profile.HEALTH_OTHER] == null ? "" : f.answers[Profile.HEALTH_OTHER].toLowerCase(Locale.ROOT);
        boolean noteDiabetes = matches(note, "diabet|\\bsugar\\b|blood sugar"), noteBp = matches(note, "\\bbp\\b|blood pressure|hypertens");
        boolean noteKidney = matches(note, "kidney|renal|\\bckd\\b|dialysis"), noteHeart = matches(note, "cholesterol|heart|cardiac");
        this.phenylketonuria = matches(note, "\\bpku\\b|phenylketon");
        this.diabetes = safety.has("diabetes") || noteDiabetes;
        this.diabetesType1 = diabetes && "type1".equals(f.answers[Profile.DIABETES_TYPE]);
        this.hypertension = safety.has("hypertension") || noteBp;
        this.kidney = safety.has("kidney") || noteKidney;
        this.dialysis = kidney && ("dialysis".equals(f.answers[Profile.KIDNEY_STAGE]) || matches(note, "dialysis"));
        this.cholesterol = safety.has("cholesterol") || noteHeart;
        this.lactose = safety.has("lactose");
        if (noteDiabetes && !safety.has("diabetes")) fromNote.add("diabetes");
        if (noteBp && !safety.has("hypertension")) fromNote.add("high blood pressure");
        if (noteKidney && !safety.has("kidney")) fromNote.add("kidney condition");
        if (noteHeart && !safety.has("cholesterol")) fromNote.add("high cholesterol or heart disease");
        if (phenylketonuria) fromNote.add("phenylketonuria");
        for (String named : new String[]{"thyroid", "pcos", "pcod", "anaemia", "anemia", "gout", "ibs", "migraine", "fatty liver"}) {
            if (note.contains(named)) unmodelled.add(named);
        }
        if (!note.isBlank() && fromNote.isEmpty() && unmodelled.isEmpty() && !matches(note, "^\\s*(none|no|nil|na|n/a)\\s*$"))
            unmodelled.add(f.answers[Profile.HEALTH_OTHER].trim());

        // Stage 0 body size: pre-pregnancy weight when pregnant, because the pregnant weight inflates BMI.
        Double stated = number(f.answers[Profile.WEIGHT], 30, 300);
        Double before = number(f.answers[Profile.PRE_PREGNANCY_WEIGHT], 30, 300);
        Double height = number(f.answers[Profile.HEIGHT], 100, 250);
        Double waist = number(f.answers[Profile.WAIST], 40, 200);
        Double kg = trimester != 0 && before != null ? before : stated;
        this.weightKg = kg == null ? null : BigDecimal.valueOf(kg);
        this.heightCm = height == null ? null : BigDecimal.valueOf(height);
        this.waistCm = waist == null ? null : BigDecimal.valueOf(waist);
        this.bmi = weightKg != null && heightCm != null ? NutritionCalculator.bmi(weightKg, heightCm).value : null;
        this.bmiBand = bmi == null ? null : NutritionCalculator.bmiBand(bmi);
        this.centralObesity = NutritionCalculator.centralObesity(sex, waistCm);
        if (trimester != 0 && before == null && stated != null && heightCm != null)
            notes.add("BMI uses your current weight; add your weight before pregnancy for a truer reading.");

        this.goals = goalsOf(f.goalCodes());
        decide();
    }

    static RecommendationContext of(Profile p) { return new RecommendationContext(p); }

    /** Stage 4: objective from the goals, constraints from the conditions, then the overrides. */
    private void decide() {
        Set<Goal> g = EnumSet.copyOf(goals);
        boolean pregnantOrFeeding = trimester != 0 || lactation != 0;
        if (g.contains(Goal.LOSS) && pregnantOrFeeding) {
            g.remove(Goal.LOSS);
            notes.add("Weight-loss comparisons are off during " + (trimester != 0 ? "pregnancy" : "breastfeeding") + ".");
        }
        if (g.contains(Goal.LOSS) && bmiBand == NutritionCalculator.BmiBand.UNDERWEIGHT) {
            g.remove(Goal.LOSS);
            notes.add("Weight-loss comparisons are off: your BMI " + NutritionCalculator.format(Locale.ROOT, bmi.doubleValue(), 1)
                + " is under NIN's 18.5.");
            referrals.add("weight loss chosen with a BMI under 18.5");
        }
        if (g.contains(Goal.GAIN) && bmiBand == NutritionCalculator.BmiBand.OBESE)
            notes.add("Weight-gain comparisons only accept energy that comes with protein, without more sugar or saturated fat.");
        boolean kidneyLimitsProtein = kidney && !dialysis;
        if (kidneyLimitsProtein && g.contains(Goal.MUSCLE)) {
            g.remove(Goal.MUSCLE);
            notes.add("Protein-up comparisons are off for a kidney condition; ask your nephrologist about protein.");
        }

        boolean loss = g.contains(Goal.LOSS), gain = g.contains(Goal.GAIN), muscle = g.contains(Goal.MUSCLE), sugar = g.contains(Goal.SUGAR);
        if (loss && muscle) {
            objective = Objective.PROTEIN_DENSITY;
            constraints.add(Constraint.ENERGY_NOT_HIGHER);
        } else if (gain && muscle) {
            objective = Objective.MORE_PROTEIN;
            constraints.add(Constraint.ENERGY_NOT_LOWER);
        } else if (loss) {
            objective = Objective.LOWER_ENERGY;
            constraints.add(Constraint.PROTEIN_NOT_LOWER);
            constraints.add(Constraint.FIBRE_NOT_LOWER);
        } else if (gain) {
            objective = Objective.HIGHER_ENERGY;
            constraints.add(Constraint.PROTEIN_NOT_LOWER);
            constraints.add(Constraint.SUGAR_NOT_HIGHER);
            constraints.add(Constraint.SATFAT_NOT_HIGHER);
        } else if (muscle) {
            objective = Objective.MORE_PROTEIN;
            constraints.add(Constraint.PROTEIN_DENSITY_NOT_LOWER);
        } else if (sugar) {
            objective = Objective.LOWER_SUGAR;
            constraints.add(Constraint.CARBS_NOT_HIGHER);
        } else {
            objective = Objective.AWARENESS;
        }
        if (sugar && objective != Objective.LOWER_SUGAR) constraints.add(Constraint.SUGAR_NOT_HIGHER);

        if (diabetes) {
            constraints.add(Constraint.CARBS_NOT_HIGHER);
            constraints.add(Constraint.SUGAR_NOT_HIGHER);
            tieBreaks.add(TieBreak.FIBRE_HIGHER);
        }
        if (hypertension || kidney) {
            constraints.add(Constraint.SODIUM_NOT_HIGHER);
            tieBreaks.add(TieBreak.SODIUM_LOWER);
        }
        if (kidneyLimitsProtein) {
            constraints.remove(Constraint.PROTEIN_NOT_LOWER);   // the kidney limit wins over a goal's protein floor
            constraints.add(Constraint.PROTEIN_NOT_HIGHER);
        }
        if (cholesterol || Boolean.TRUE.equals(centralObesity)) {
            constraints.add(Constraint.SATFAT_NOT_HIGHER);
            constraints.add(Constraint.TRANSFAT_NOT_HIGHER);
            tieBreaks.add(TieBreak.FIBRE_HIGHER);
        }
        if (trimester != 0) { tieBreaks.add(TieBreak.PROTEIN_HIGHER); tieBreaks.add(TieBreak.FIBRE_HIGHER); }
        if (lactation != 0) tieBreaks.add(TieBreak.PROTEIN_HIGHER);
        if (lactose && !safety.allergens().contains(Allergen.MILK)) tieBreaks.add(TieBreak.NO_MILK);

        // When to suggest a dietitian, while every filter keeps working.
        if (trimester != 0 && diabetes) referrals.add("pregnancy with diabetes");
        if (dialysis) referrals.add("dialysis");
        int conditions = (diabetes ? 1 : 0) + (hypertension ? 1 : 0) + (kidney ? 1 : 0) + (cholesterol ? 1 : 0);
        if (conditions > 1) referrals.add("more than one medical condition");
        if (!unmodelled.isEmpty()) referrals.add("a condition this app has no rules for (" + String.join(", ", unmodelled) + ")");
    }

    boolean vegetarianProtein() {
        return diet == DietRules.Diet.VEGETARIAN || diet == DietRules.Diet.JAIN || diet == DietRules.Diet.VEGAN;
    }

    /** The label-level vetoes for this shopper: allergens, diet, and state or condition. */
    List<String> vetoes(String ingredients, String allergenText, String labelText) {
        List<String> out = new ArrayList<>();
        SafetyGate.Result gate = SafetyGate.check(ingredients, allergenText, safety);
        if (gate.blocked()) out.addAll(gate.messages.subList(0, Math.min(1, gate.messages.size())));
        DietRules.Verdict dietVerdict = DietRules.check(diet, ingredients, allergenText);
        for (String reason : dietVerdict.vetoes) out.add("Contains " + reason + ", which doesn't fit your " + diet.label.toLowerCase(Locale.ROOT) + " diet.");
        ConditionRules.Verdict state = ConditionRules.check(ingredients, labelText, trimester, lactation != 0, kidney, phenylketonuria);
        for (String reason : state.vetoes) out.add("Not for you now: " + reason + ".");
        return out;
    }

    /**
     * What this shopper should know about the product on screen, in numbers: its caffeine for anyone
     * (with WHO's pregnancy advice when pregnant), its lactose when lactose intolerance is recorded
     * and milk is not an allergy, and any condition caution its label raises.
     */
    List<String> productLines(String name, Nutrition n, String labelText, boolean drink) {
        List<String> out = new ArrayList<>();
        String ingredients = IngredientFacts.section(labelText == null ? "" : labelText);
        String caffeine = CaffeineLactose.caffeineLine(CaffeineLactose.caffeine(name, labelText, drink), n, trimester != 0);
        if (caffeine != null) out.add(caffeine);
        if (lactose && !safety.allergens().contains(Allergen.MILK)) {
            String line = CaffeineLactose.lactoseLine(name, n, ingredients, labelText, drink);
            if (line != null) out.add(line);
        }
        ConditionRules.Verdict state = ConditionRules.check(ingredients, labelText, trimester, lactation != 0, kidney, phenylketonuria);
        for (String caution : state.cautions) out.add("Note: " + caution + ".");
        return out;
    }

    static NutritionCalculator.Sex sexOf(String gender) {
        if ("Woman".equals(gender)) return NutritionCalculator.Sex.FEMALE;
        if ("Man".equals(gender)) return NutritionCalculator.Sex.MALE;
        return NutritionCalculator.Sex.AVERAGE;
    }

    private static Set<Goal> goalsOf(List<String> codes) {
        Set<Goal> set = EnumSet.noneOf(Goal.class);
        for (String code : codes) {
            switch (code) {
                case "loss": set.add(Goal.LOSS); break;
                case "gain": set.add(Goal.GAIN); break;
                case "muscle": set.add(Goal.MUSCLE); break;
                case "sugar": set.add(Goal.SUGAR); break;
                case "aware": set.add(Goal.AWARE); break;
                default: break;
            }
        }
        if (set.contains(Goal.LOSS) && set.contains(Goal.GAIN)) { set.remove(Goal.LOSS); set.remove(Goal.GAIN); }
        if (set.isEmpty()) set.add(Goal.AWARE);
        return set;
    }

    private static boolean matches(String text, String regex) {
        return text != null && java.util.regex.Pattern.compile(regex).matcher(text).find();
    }

    private static Double number(String raw, double min, double max) {
        if (raw == null || raw.isBlank()) return null;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) && value >= min && value <= max ? value : null;
        } catch (NumberFormatException bad) { return null; }
    }
}
