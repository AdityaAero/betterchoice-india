package com.betterchoice.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class DailyImpactTest {
    private static Nutrition chips() {
        Nutrition n = NutriGradeTest.food(461.23, 3.18, 7.22, 2025.64, 7.49, 3.7);
        n.basisStated = true; n.servingSize = 20.0; n.netQuantity = 80.0;
        return n;
    }
    private static DailyImpact.Line line(DailyImpact i, String label) {
        return i.lines.stream().filter(l -> l.nutrient.equals(label)).findFirst().orElse(null);
    }
    @Test public void decimalCalculatorMatchesIndependentSodiumVectorsBeforeRounding() {
        DailyImpact.Line serving = line(DailyImpact.forServing(chips(), DailyNeeds.generic()), "Sodium");
        assertEquals(405.128, serving.exactAmount, 0.000000001);
        assertEquals(20.2564, serving.exactPercent, 0.000000001);
        DailyImpact.Line pack = line(DailyImpact.forPack(chips(), DailyNeeds.generic()), "Sodium");
        assertEquals(1620.512, pack.exactAmount, 0.000000001);
        assertEquals(81.0256, pack.exactPercent, 0.000000001);
    }
    @Test public void naturalMilkSugarIsNeverComparedWithAnAddedOrFreeSugarReference() {
        Nutrition milk = chips(); milk.beverage = true; milk.sugarsG = 4.8; milk.addedSugarsG = 0.0;
        DailyImpact impact = DailyImpact.of(milk, DailyNeeds.generic(), 200, false);
        assertNull(line(impact, "Sugars")); assertNull(line(impact, "Total sugars"));
        assertEquals(0, line(impact, "Added sugars").exactPercent, 0);
        milk.addedSugarsG = null;
        assertNull(line(DailyImpact.of(milk, DailyNeeds.generic(), 200, false), "Added sugars"));
    }
    @Test public void declaredAddedSugarUsesTheIndianLabelReference() {
        Nutrition n = chips(); n.addedSugarsG = 47.4;
        DailyImpact.Line sugar = line(DailyImpact.of(n, DailyNeeds.generic(), 20.2, false), "Added sugars");
        assertEquals(9.5748, sugar.exactAmount, 0.000000001);
        assertEquals(19.1496, sugar.exactPercent, 0.000000001);
    }
    @Test public void energyAndProteinAreNotUpperSafetyLimits() {
        DailyImpact impact = DailyImpact.forServing(chips(), DailyNeeds.forProfile(DailyNeedsTest.profile("Muscle gain")));
        assertFalse(line(impact, "Energy").isLimit);
        assertFalse(line(impact, "Protein").isLimit);
        assertTrue(line(impact, "Sodium").isLimit);
    }
    @Test public void invalidBasisAndNonFinitePortionsCannotProducePercentages() {
        Nutrition n = chips(); n.basisStated = false;
        // An unstated basis is read as the label's usual one; the card says the label did not say.
        assertNotNull(DailyImpact.of(n, DailyNeeds.generic(), 20, false));
        n.basisStated = true;
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, -1})
            assertNull(DailyImpact.of(n, DailyNeeds.generic(), value, false));
        n.sodiumMg = Double.NaN;
        assertNull(line(DailyImpact.of(n, DailyNeeds.generic(), 20, false), "Sodium"));
    }
    @Test public void largeContributionsAreNotSilentlyCappedAt999() {
        Nutrition n = chips(); n.sodiumMg = 40000.0;
        assertEquals(2000, line(DailyImpact.of(n, DailyNeeds.generic(), 100, false), "Sodium").percent);
    }
    @Test public void explanationDoesNotClaimConsumptionOrRemainingAllowance() {
        DailyImpact i = DailyImpact.forPack(chips(), DailyNeeds.generic());
        assertTrue(i.headline().contains("reference"));
        assertTrue(i.confidence().contains("not intake tracking"));
    }
}