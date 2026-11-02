package com.betterchoice.android;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Every combination of form answers reaches a defined decision, and every rule of the decision tree
 * holds for it: 7 diets x 12 valid goal sets x 35 condition sets (0-2 conditions) x 5 BMI states x
 * 3 allergen sets x 3 sex references = 132,300 profiles. A 756-profile slice also runs through the
 * swap engine on real products, and no recommended swap may break a veto or a constraint.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class DecisionMatrixTest {
    static final String[] DIETS = {"Vegetarian", "Eggetarian", "Jain", "Non-vegetarian / both", "Non-vegetarian (halal)", "Vegan", ""};
    static final String[] CONDITIONS = {"diabetes", "hypertension", "kidney-nd", "kidney-d", "pregnancy", "breastfeeding", "lactose", "cholesterol"};
    static final String[][] BMI = {{"65", ""}, {"50", "175"}, {"65", "170"}, {"75", "170"}, {"95", "170"}};
    static final String[] ALLERGENS = {"", "milk", "peanut,tree_nut,sesame"};
    static final String[] SEXES = {"Woman", "Man", "Non-binary"};

    static List<String> goalSets() {
        String[] goals = {"loss", "gain", "muscle", "sugar"};
        List<String> sets = new ArrayList<>();
        for (int mask = 0; mask < 16; mask++) {
            List<String> set = new ArrayList<>();
            for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) set.add(goals[i]);
            if (set.contains("loss") && set.contains("gain")) continue;
            sets.add(String.join(",", set));
        }
        return sets;
    }

    static List<List<String>> conditionSets() {
        List<List<String>> sets = new ArrayList<>();
        sets.add(Collections.emptyList());
        for (int i = 0; i < CONDITIONS.length; i++) {
            sets.add(Collections.singletonList(CONDITIONS[i]));
            for (int j = i + 1; j < CONDITIONS.length; j++) {
                List<String> pair = Arrays.asList(CONDITIONS[i], CONDITIONS[j]);
                if (pair.contains("kidney-nd") && pair.contains("kidney-d")) continue;
                if (pair.contains("pregnancy") && pair.contains("breastfeeding")) continue;
                sets.add(pair);
            }
        }
        return sets;
    }

    static Profile profile(String sex, String diet, String goals, List<String> conditions, String[] bmi, String allergens) {
        Profile p = new Profile();
        p.adultConfirmed = true;
        p.answers[Profile.GENDER] = sex; p.answers[Profile.AGE] = "35"; p.answers[Profile.ACTIVITY] = "Moderately active";
        p.answers[Profile.WEIGHT] = bmi[0]; p.answers[Profile.HEIGHT] = bmi[1];
        p.answers[Profile.DIET_DETAIL] = diet; p.answers[Profile.GOALS] = goals;
        List<String> health = new ArrayList<>();
        if (!allergens.isEmpty()) health.addAll(Arrays.asList(allergens.split(",")));
        for (String c : conditions) {
            switch (c) {
                case "kidney-nd": health.add("kidney"); p.answers[Profile.KIDNEY_STAGE] = "no_dialysis"; break;
                case "kidney-d": health.add("kidney"); p.answers[Profile.KIDNEY_STAGE] = "dialysis"; break;
                case "pregnancy": health.add("pregnancy"); p.answers[Profile.PREGNANCY_STAGE] = "t2"; break;
                case "breastfeeding": health.add("breastfeeding"); p.answers[Profile.PREGNANCY_STAGE] = "bf0"; break;
                default: health.add(c);
            }
        }
        p.answers[Profile.HEALTH] = String.join(",", health);
        return p;
    }

    @Test public void everyCombinationReachesADefinedDecisionThatObeysTheTree() {
        List<String> goalSets = goalSets();
        List<List<String>> conditionSets = conditionSets();
        assertEquals(12, goalSets.size());
        assertEquals(35, conditionSets.size());
        int checked = 0;
        for (String sex : SEXES) for (String diet : DIETS) for (String goals : goalSets) for (List<String> cond : conditionSets)
            for (String[] bmi : BMI) for (String allergens : ALLERGENS) {
                Profile p = profile(sex, diet, goals, cond, bmi, allergens);
                RecommendationContext c = RecommendationContext.of(p);
                DailyNeeds n = DailyNeeds.of(c);
                String who = sex + "/" + diet + "/" + goals + "/" + cond + "/" + bmi[0] + "x" + bmi[1] + "/" + allergens;
                assertNotNull(who, c.objective);
                boolean pregnantOrFeeding = cond.contains("pregnancy") || cond.contains("breastfeeding");
                boolean kidneyNd = cond.contains("kidney-nd"), kidneyD = cond.contains("kidney-d");
                if (pregnantOrFeeding || c.bmiBand == NutritionCalculator.BmiBand.UNDERWEIGHT) {
                    assertNotEquals(who, RecommendationContext.Objective.LOWER_ENERGY, c.objective);
                    assertNotEquals(who, RecommendationContext.Objective.PROTEIN_DENSITY, c.objective);
                }
                if (kidneyNd) {
                    assertNotEquals(who, RecommendationContext.Objective.MORE_PROTEIN, c.objective);
                    assertNotEquals(who, RecommendationContext.Objective.PROTEIN_DENSITY, c.objective);
                    assertTrue(who, c.constraints.contains(RecommendationContext.Constraint.PROTEIN_NOT_HIGHER));
                    assertFalse(who, c.constraints.contains(RecommendationContext.Constraint.PROTEIN_NOT_LOWER));
                    assertTrue(who, Double.isNaN(n.proteinG));
                }
                if (kidneyD) {
                    assertFalse(who, c.constraints.contains(RecommendationContext.Constraint.PROTEIN_NOT_HIGHER));
                    assertTrue(who, Double.isFinite(n.proteinG));
                    assertTrue(who, c.referrals.contains("dialysis"));
                }
                if (kidneyNd || kidneyD || cond.contains("hypertension"))
                    assertTrue(who, c.constraints.contains(RecommendationContext.Constraint.SODIUM_NOT_HIGHER));
                if (cond.contains("diabetes")) {
                    assertTrue(who, c.constraints.contains(RecommendationContext.Constraint.CARBS_NOT_HIGHER));
                    assertTrue(who, c.constraints.contains(RecommendationContext.Constraint.SUGAR_NOT_HIGHER));
                    if (cond.contains("pregnancy")) assertTrue(who, c.referrals.contains("pregnancy with diabetes"));
                }
                if (cond.contains("cholesterol")) {
                    assertTrue(who, c.constraints.contains(RecommendationContext.Constraint.SATFAT_NOT_HIGHER));
                    assertTrue(who, c.constraints.contains(RecommendationContext.Constraint.TRANSFAT_NOT_HIGHER));
                }
                if (goals.contains("sugar")) assertTrue(who, c.objective == RecommendationContext.Objective.LOWER_SUGAR
                    || c.constraints.contains(RecommendationContext.Constraint.SUGAR_NOT_HIGHER));
                assertFalse(who, c.constraints.contains(RecommendationContext.Constraint.ENERGY_NOT_HIGHER)
                    && c.constraints.contains(RecommendationContext.Constraint.ENERGY_NOT_LOWER));
                int medical = 0;
                for (String m : new String[]{"diabetes", "hypertension", "cholesterol"}) if (cond.contains(m)) medical++;
                if (kidneyNd || kidneyD) medical++;
                if (medical > 1) assertTrue(who, c.referrals.contains("more than one medical condition"));
                assertTrue(who, Double.isFinite(n.energyKcal) && n.energyKcal > 0);
                if (cond.contains("pregnancy")) assertTrue(who, n.stateExtraKcal >= 350);
                if (cond.contains("breastfeeding")) assertEquals(who, 600, n.stateExtraKcal, 0);
                if (Double.isFinite(n.proteinG) && !pregnantOrFeeding) {
                    // ICMR's RDA on the stated weight, whatever the BMI: no weight adjustment has a source.
                    double perKg = Evidence.d(c.vegetarianProtein() ? "icmr.protein.vegetarian" : "icmr.protein.rda").doubleValue();
                    assertEquals(who, perKg * n.weightKg, n.proteinG, 1e-6);
                }
                assertFalse(who, n.workings.isEmpty());
                checked++;
            }
        assertEquals(132_300, checked);
    }

    // ------------------------------------------------------------ the swap slice

    private static SwapCatalogue catalogue() throws Exception {
        for (String path : new String[]{"src/main/assets/" + SwapCatalogue.ASSET, "app/src/main/assets/" + SwapCatalogue.ASSET}) {
            File file = new File(path);
            if (file.exists()) return SwapCatalogue.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        }
        throw new AssertionError("catalogue asset not found");
    }

    private static Nutrition n(double kcal, double sugar, double sat, double sodium, double fat, double protein, double carbs, double fibre) {
        Nutrition n = new Nutrition();
        n.energyKcal = kcal; n.sugarsG = sugar; n.saturatedFatG = sat; n.sodiumMg = sodium; n.totalFatG = fat;
        n.proteinG = protein; n.carbohydrateG = carbs; n.fibreG = fibre; n.basisStated = true;
        return n;
    }

    @Test public void noRecommendedSwapBreaksAVetoOrAConstraint() throws Exception {
        SwapCatalogue cat = catalogue();
        Object[][] products = {
            {"Lay's Classic Salted | Crunchy Potato Chips", n(553, 0.6, 15.8, 500, 35.1, 6.7, 50.0, 3.0)},
            {"Unibic Cashew Badam Cookies", n(477, 24.6, 9.5, 195, 21.2, 5.0, 66.0, 1.5)},
            {"Cadbury Dairy Milk Chocolate Bar", n(531, 57.0, 19.6, 129, 29.0, 7.9, 60.4, 1.0)},
            {"Maggi Masala 2 Minutes Instant Noodles", n(384, 1.8, 8.2, 1000, 12.5, 8.2, 58.0, 2.0)}};
        String[] single = {"none", "diabetes", "hypertension", "kidney-nd", "kidney-d", "pregnancy", "breastfeeding", "lactose", "cholesterol"};
        int profiles = 0, swaps = 0;
        for (String diet : DIETS) for (String goals : goalSets()) for (String condition : single) {
            List<String> cond = "none".equals(condition) ? Collections.emptyList() : Collections.singletonList(condition);
            Profile p = profile("Woman", diet, goals, cond, BMI[2], "");
            RecommendationContext c = RecommendationContext.of(p);
            SafetyProfile safety = SafetyProfile.fromProfile(p);
            profiles++;
            for (Object[] product : products) {
                Nutrition current = (Nutrition) product[1];
                NutriGrade.Result grade = NutriGrade.grade(current, Beverage.NOT_A_DRINK);
                SwapEngine.Outcome out = SwapEngine.recommend(cat, (String) product[0], current, grade.band, grade.letter(),
                    false, safety, null, true, p);
                for (SwapEngine.Swap s : out.swaps) {
                    swaps++;
                    SwapCatalogue.Product cand = s.product;
                    Nutrition x = cand.per100;
                    String who = diet + "/" + goals + "/" + condition + " -> " + cand.displayName();
                    assertNull(who, SwapEngine.vetoFor(cand, safety, c));
                    assertTrue(who, DietRules.check(c.diet, cand.ingredients, cand.allergenText()).fits());
                    if (c.diet.vegetarianFamily()) assertNotEquals(who, "non-vegetarian", cand.veg);
                    assertNull(who, SwapEngine.brokenConstraint(c, current, x));
                    if (c.diabetes) {
                        assertNotNull(who + ": carbohydrate must be known", x.carbohydrateG);
                        assertTrue(who, x.carbohydrateG <= current.carbohydrateG * 1.05 + 0.05);
                    }
                    if (c.hypertension || c.kidney) assertTrue(who, x.sodiumMg <= current.sodiumMg * 1.05 + 0.05);
                    if (c.kidney && !c.dialysis) assertTrue(who, x.proteinG <= current.proteinG * 1.05 + 0.05);
                    if (c.trimester != 0 || c.lactation != 0) assertTrue(who,
                        ConditionRules.check(cand.ingredients, null, c.trimester, c.lactation != 0, false, false).fits());
                    if (c.objective == RecommendationContext.Objective.LOWER_ENERGY) {
                        if (s.meetsGoal) assertTrue(who, x.energyKcal <= current.energyKcal * 0.70 + 1e-9);
                        else {
                            assertNotNull(who, s.goalNote);
                            assertTrue(who, x.energyKcal <= current.energyKcal * 1.05 + 0.05);
                        }
                    }
                    NutriGrade.Result now = NutriGrade.grade(current, Beverage.NOT_A_DRINK);
                    assertTrue(who, s.letter <= SwapEngine.letterOf(current, now.letter(), (String) product[0], false, out.group));
                }
            }
        }
        assertEquals(756, profiles);
        assertTrue("the slice must actually recommend something, got " + swaps, swaps > 0);
    }

    @Test public void noSwapContainsAChosenAllergen() throws Exception {
        SwapCatalogue cat = catalogue();
        Nutrition cookies = n(477, 24.6, 9.5, 195, 21.2, 5.0, 66.0, 1.5);
        Nutrition chips = n(553, 0.6, 15.8, 500, 35.1, 6.7, 50.0, 3.0);
        for (String allergens : new String[]{"milk", "peanut,tree_nut,sesame", "wheat", "gluten", "soy", "egg"}) {
            for (String severity : new String[]{"strict", "ingredients", ""}) {
                Profile p = profile("Man", "", "", Collections.emptyList(), BMI[2], allergens);
                p.answers[Profile.TRACE_SEVERITY] = severity;
                SafetyProfile safety = SafetyProfile.fromProfile(p);
                for (Object[] product : new Object[][]{{"Unibic Cashew Badam Cookies", cookies}, {"Lay's Classic Salted Chips", chips}}) {
                    Nutrition current = (Nutrition) product[1];
                    NutriGrade.Result grade = NutriGrade.grade(current, Beverage.NOT_A_DRINK);
                    SwapEngine.Outcome out = SwapEngine.recommend(cat, (String) product[0], current, grade.band, grade.letter(),
                        false, safety, null, true, p);
                    for (SwapEngine.Swap s : out.swaps) {
                        Allergen.Scan scan = Allergen.scan(s.product.ingredients, s.product.allergenText());
                        for (String code : allergens.split(",")) {
                            Allergen a = Allergen.byCode(code);
                            String who = allergens + "/" + severity + " -> " + s.product.displayName();
                            if (a != null) {
                                assertFalse(who, scan.containsAsIngredient(a));
                                if (!"ingredients".equals(severity)) assertFalse(who + " (trace)", scan.traces.contains(a));
                            } else if ("wheat".equals(code)) {
                                assertFalse(who, scan.wheatIngredient);
                            }
                        }
                    }
                }
            }
        }
    }
}
