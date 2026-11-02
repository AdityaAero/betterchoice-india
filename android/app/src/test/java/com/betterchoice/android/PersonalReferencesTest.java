package com.betterchoice.android;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Personal daily references from the form, checked against the Indian evidence they come from. */
public class PersonalReferencesTest {
    static Profile person(String gender, String age, String kg, String activity, String goal) {
        Profile p = new Profile();
        p.answers[Profile.GENDER] = gender; p.answers[Profile.AGE] = age; p.answers[Profile.WEIGHT] = kg;
        p.answers[Profile.ACTIVITY] = activity; p.answers[Profile.GOAL] = goal; p.adultConfirmed = true;
        return p;
    }

    /** NIN Dietary Guidelines 2024, diet tables for adults: weight, activity and the energy they provide. */
    @Test public void reproducesTheNinReferenceEnergiesWithinFourPercent() {
        Object[][] anchors = {
            {"Man", "55", "Mostly sitting", 1900}, {"Man", "65", "Mostly sitting", 2080},
            {"Man", "55", "Moderately active", 2400}, {"Man", "65", "Moderately active", 2680},
            {"Woman", "50", "Mostly sitting", 1600}, {"Woman", "55", "Mostly sitting", 1660},
            {"Woman", "50", "Moderately active", 2000}, {"Woman", "55", "Moderately active", 2125}};
        for (String age : new String[]{"25", "45"}) {
            for (Object[] a : anchors) {
                double energy = DailyNeeds.forProfile(person((String) a[0], age, (String) a[1], (String) a[2], "")).energyKcal;
                int nin = (Integer) a[3];
                assertEquals(a[0] + " " + a[1] + " kg " + a[2] + " age " + age, nin, energy, nin * 0.04);
            }
        }
    }

    /** ICMR-NIN 2020 Table 1a at its own reference weights: 65 kg man, 55 kg woman. */
    @Test public void reproducesIcmrTable1aWithinTwoPercentForMen() {
        String[][] levels = {{"Mostly sitting", "2110"}, {"Moderately active", "2710"}, {"Heavy physical work", "3470"}};
        for (String[] level : levels) {
            double energy = DailyNeeds.forProfile(person("Man", "25", "65", level[0], "")).energyKcal;
            assertEquals(level[0], Double.parseDouble(level[1]), energy, Double.parseDouble(level[1]) * 0.02);
        }
    }

    @Test public void limitsAreSharesOfTheShoppersOwnEnergy() {
        DailyNeeds n = DailyNeeds.forProfile(person("Man", "25", "65", "Mostly sitting", ""));
        // 15.057 x 65 + 692.2 = 1670.9 kcal basal; x (1 - 0.05 - 0.05) x 1.40 = 2105.3 kcal (ICMR-NIN 2020 lists 2110).
        assertEquals(2105.3, n.energyKcal, 0.1);
        assertEquals(n.energyKcal * 0.30 / 9, n.totalFatLimitG, 1e-6);
        assertEquals(n.energyKcal * 0.10 / 9, n.saturatedFatLimitG, 1e-6);
        assertEquals(n.energyKcal * 0.01 / 9, n.transFatLimitG, 1e-6);
        assertEquals(n.energyKcal * 0.10 / 4, n.sugarLimitG, 1e-6);
        assertEquals(n.energyKcal * 0.05 / 4, n.sugarBetterG, 1e-6);
        assertEquals(n.energyKcal * 0.50 / 4, n.carbohydrateLowG, 1e-6);
        assertEquals(n.energyKcal * 0.55 / 4, n.carbohydrateHighG, 1e-6);
        assertEquals("sodium is the same for everyone", 2000, n.sodiumLimitMg, 0);
        assertEquals(53.95, n.proteinG, 0.001);
        assertTrue(n.limits(), n.limits().contains("fat up to 70 g"));
        assertFalse("every number has its working", n.workings.isEmpty());
        assertTrue(n.workings.get(0), n.workings.get(0).contains("15.057 × 65 + 692.2"));
    }

    @Test public void aHeavierMoreActivePersonGetsHigherLimitsButTheSameSodiumAndFibre() {
        DailyNeeds light = DailyNeeds.forProfile(person("Woman", "35", "52", "Mostly sitting", ""));
        DailyNeeds heavy = DailyNeeds.forProfile(person("Woman", "35", "80", "Heavy physical work", ""));
        assertTrue(heavy.totalFatLimitG > light.totalFatLimitG);
        assertTrue(heavy.sugarLimitG > light.sugarLimitG);
        assertEquals(light.sodiumLimitMg, heavy.sodiumLimitMg, 0);
        assertEquals("WHO 2023: one adult minimum", light.fibreG, heavy.fibreG, 0);
    }

    @Test public void muscleGoalGetsARangeCappedWhereNinSaysMoreAddsNothing() {
        DailyNeeds n = DailyNeeds.forProfile(person("Man", "28", "70", "Moderately active", "Muscle gain"));
        assertEquals("ISSN 2017 lower bound 1.4 g/kg", 98, n.muscleProteinLowG, 1e-6);
        assertEquals("NIN 2024 cap 1.6 g/kg", 112, n.muscleProteinHighG, 1e-6);
        String text = String.join(" ", GoalComparisons.explain(person("Man", "28", "70", "Moderately active", "Muscle gain"), null));
        assertTrue(text, text.contains("98-112 g protein a day"));
    }

    @Test public void weightLossIsExplainedButNoDietIsSet() {
        DailyNeeds n = DailyNeeds.forProfile(person("Woman", "40", "68", "Mostly sitting", "Weight loss"));
        assertEquals("the reference is maintenance, not a diet", n.maintenanceKcal, n.energyKcal, 0);
        String text = String.join(" ", GoalComparisons.explain(person("Woman", "40", "68", "Mostly sitting", "Weight loss"), null));
        assertTrue(text, text.contains("The app does not set your diet"));
        assertTrue(text, text.contains("never under 1000"));
    }

    @Test public void theCardRowsNameTheShoppersOwnLimits() {
        Nutrition chips = new Nutrition();
        chips.energyKcal = 550.0; chips.totalFatG = 34.0; chips.saturatedFatG = 15.0; chips.sugarsG = 1.0;
        chips.sodiumMg = 700.0; chips.carbohydrateG = 55.0; chips.proteinG = 7.0; chips.basisStated = true;
        DailyNeeds n = DailyNeeds.forProfile(person("Man", "25", "65", "Mostly sitting", ""));
        DailyImpact impact = DailyImpact.of(chips, n, 52, false);
        DailyImpact.Line fat = null, carbs = null;
        for (DailyImpact.Line line : impact.lines) {
            if (line.nutrient.equals("Total fat")) fat = line;
            if (line.nutrient.equals("Carbohydrate")) carbs = line;
        }
        assertNotNull(fat); assertNotNull(carbs);
        assertTrue(fat.isLimit); assertFalse("carbohydrate is a range, not a limit", carbs.isLimit);
        assertEquals(17.68 / n.totalFatLimitG * 100, fat.exactPercent, 1e-6);
        assertTrue(fat.reference, fat.reference.startsWith("your daily limit (70 g)"));
        assertTrue(impact.confidence().contains("not intake tracking"));
    }

    @Test public void additivesSayTheAmountIsUnknownAndNeverThatTheyAreWithinALimit() {
        List<IngredientParser.Additive> declared = IngredientParser.parse(
            "Carbonated water, acidity regulator (338), sweeteners (951, 950), preservative (211), colour (171)").additives;
        String all = String.join(" | ", AdditiveLimits.lines(declared, 70.0));
        assertTrue(all, all.contains("Contains aspartame (INS 951): acceptable daily intake 40 mg per kg a day (JECFA), about 2800 mg a day at your 70 kg"));
        assertTrue(all, all.contains("Contains acesulfame potassium (INS 950)"));
        // JECFA raised the benzoate group ADI to 0-20 mg/kg bw in 2021 and withdrew 0-5.
        assertTrue(all, all.contains("sodium benzoate (INS 211): acceptable daily intake 20 mg per kg"));
        assertTrue(all, all.contains("titanium dioxide (INS 171). EFSA concluded in 2021"));
        assertTrue(all, all.contains("doesn't say how much it contains"));
        String lower = all.toLowerCase(java.util.Locale.ROOT);
        assertFalse(lower.contains("within")); assertFalse(lower.contains("below the"));
        assertFalse("phosphoric acid has no record here", all.contains("338"));
    }

    @Test public void additiveLimitsWithoutAWeightStayPerKilogram() {
        List<IngredientParser.Additive> declared = IngredientParser.parse("Sugar, flavour enhancer (621)").additives;
        String line = String.join(" ", AdditiveLimits.lines(declared, null));
        assertTrue(line, line.contains("monosodium glutamate (INS 621): acceptable daily intake 30 mg per kg a day (EFSA; JECFA set no number)"));
        assertFalse(line.contains("at your"));
    }
}
