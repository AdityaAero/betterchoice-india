package com.betterchoice.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class DailyNeedsTest {
    static Profile profile(String goal) {
        Profile p = new Profile();
        p.answers[Profile.AGE] = "30"; p.answers[Profile.WEIGHT] = "70";
        p.answers[Profile.GOAL] = goal; p.adultConfirmed = true;
        return p;
    }
    @Test public void weightBasedProteinIsAdequacyNotAGoalPrescription() {
        for (String goal : new String[]{"Weight loss", "Weight gain", "Muscle gain"}) {
            DailyNeeds n = DailyNeeds.forProfile(profile(goal));
            // ICMR-NIN 2020 Table 2a: 0.83 g per kg (mixed diet).
            assertEquals(58.1, n.proteinG, 0.000001);
            // 70 kg, 30 years, no gender, no activity: the two equations averaged, mostly sitting.
            assertEquals(DailyNeeds.Basis.PERSONAL, n.basis);
            assertEquals(1947.1, n.energyKcal, 0.1);
            assertEquals(n.energyKcal, n.maintenanceKcal, 0);
            assertTrue(n.proteinNote.contains("not a maximum"));
        }
    }
    @Test public void onlyWomanOrManSelectsAnEquationAndVegetarianProteinIsOneGramPerKg() {
        Profile p = profile("Muscle gain");
        p.answers[Profile.GENDER] = "Non-binary"; p.answers[Profile.DIET] = "Vegan";
        DailyNeeds average = DailyNeeds.forProfile(p);
        assertEquals(NutritionCalculator.Sex.AVERAGE, average.sex);
        assertTrue(average.summary(), average.summary().contains("averaged"));
        // ICMR-NIN 2020 Table 2a note / 2b: 1 g per kg for cereal-based vegetarian diets.
        assertEquals(70.0, average.proteinG, 0.000001);
        assertEquals(0, average.micronutrients().length);
        p.answers[Profile.GENDER] = "Woman";
        DailyNeeds woman = DailyNeeds.forProfile(p);
        p.answers[Profile.GENDER] = "Man";
        DailyNeeds man = DailyNeeds.forProfile(p);
        assertTrue(woman.energyKcal < average.energyKcal && average.energyKcal < man.energyKcal);
        assertEquals("ICMR-NIN: protein does not depend on gender", woman.proteinG, man.proteinG, 0);
        assertEquals("WHO 2023: at least 25 g fibre for every adult", 25, woman.fibreG, 0);
        assertEquals(25, man.fibreG, 0);
    }
    @Test public void missingOrUnsafeMeasurementsStayUnknown() {
        Profile p = profile("");
        for (String weight : new String[]{"", "NaN", "Infinity", "-1", "500"}) {
            p.answers[Profile.WEIGHT] = weight;
            assertTrue(Double.isNaN(DailyNeeds.forProfile(p).proteinG));
        }
        assertTrue(Double.isNaN(DailyNeeds.generic().proteinG));
    }
    @Test public void kidneyHidesProteinButPregnancyIsPersonalised() {
        Profile kidney = profile(""); kidney.answers[Profile.HEALTH] = "kidney";
        DailyNeeds k = DailyNeeds.forProfile(kidney);
        assertEquals(DailyNeeds.Basis.PERSONAL, k.basis);
        assertTrue("only a clinician can set protein for a kidney condition", Double.isNaN(k.proteinG));
        assertTrue(k.proteinNote.contains("nephrologist"));
        Profile dialysis = profile(""); dialysis.answers[Profile.HEALTH] = "kidney"; dialysis.answers[Profile.KIDNEY_STAGE] = "dialysis";
        assertFalse(Double.isNaN(DailyNeeds.forProfile(dialysis).proteinG));
        Profile pregnant = profile(""); pregnant.answers[Profile.HEALTH] = "pregnancy";
        DailyNeeds p = DailyNeeds.forProfile(pregnant);
        assertEquals(DailyNeeds.Basis.PERSONAL, p.basis);
        assertEquals("stage not given: no addition is assumed", 0, p.stateExtraKcal, 0);
    }
    @Test public void labelReferenceAndSaltAreTraceableAndDistinct() {
        DailyNeeds n = DailyNeeds.generic();
        assertEquals(50, n.sugarLimitG, 0);
        assertEquals(2000, n.sodiumLimitMg, 0);
        assertEquals(5, n.saltLimitG(), 0);
        assertTrue(n.summary().contains("not a personal calorie target"));
    }
}
