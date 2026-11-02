package com.betterchoice.android;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PackMathTest {
    /** The worked example from the brief: 150 g chips, 25 g serving, 6 servings. */
    private static Nutrition chips() {
        Nutrition n = NutriGradeTest.food(520, 8.0, 12.0, 700.0, 4.0, 6.0);
        n.netQuantity = 150.0; n.servingSize = 25.0; n.servingsPerPack = 6.0;
        return n;
    }

    @Test public void servingAndPackColumnsComeFromPer100() {
        List<PackMath.Row> rows = PackMath.table(chips());
        PackMath.Row energy = rows.get(0);
        assertEquals("Energy", energy.nutrient);
        assertEquals(520, energy.per100, 0.01);
        assertEquals(130, energy.perServing, 0.01);   // 520 × 25 / 100
        assertEquals(780, energy.perPack, 0.01);      // 520 × 150 / 100
        PackMath.Row sodium = rows.stream().filter(r -> r.nutrient.equals("Sodium")).findFirst().orElseThrow();
        assertEquals(1050, sodium.perPack, 0.01);
        assertEquals("175 mg", sodium.perServingText());
    }

    @Test public void missingNutrientsAreLeftOutAndMissingPackGivesNoColumns() {
        Nutrition n = NutriGradeTest.food(400, 10.0, null, 300.0, null, null);
        List<PackMath.Row> rows = PackMath.table(n);
        assertEquals(3, rows.size());
        assertNull(rows.get(0).perServing);
        assertNull(rows.get(0).perPack);
        assertEquals("—", rows.get(0).perPackText());
    }

    @Test public void servingSizeAndCountAreDerivedFromEachOther() {
        Nutrition onlyCount = NutriGradeTest.food(400, 5.0, 2.0, 100.0, null, null);
        onlyCount.netQuantity = 500.0; onlyCount.servingsPerPack = 10.0;
        assertEquals(50, PackMath.servingSize(onlyCount), 0.01);

        Nutrition onlySize = NutriGradeTest.food(400, 5.0, 2.0, 100.0, null, null);
        onlySize.netQuantity = 500.0; onlySize.servingSize = 40.0;
        assertEquals(12.5, PackMath.servingsPerPack(onlySize), 0.01);

        assertNull(PackMath.servingSize(NutriGradeTest.food(400, 5.0, 2.0, 100.0, null, null)));
    }

    @Test public void oneSittingPacksAreFlagged() {
        assertTrue(PackMath.singleSittingPack(chips()));
        Nutrition familyPack = chips(); familyPack.netQuantity = 800.0;
        assertFalse(PackMath.singleSittingPack(familyPack));
        Nutrition bottle = chips(); bottle.beverage = true; bottle.netQuantity = 330.0;
        assertTrue("A 330 ml bottle is one sitting", PackMath.singleSittingPack(bottle));
    }

    @Test public void summaryStatesOnlyWhatTheLabelSaid() {
        assertEquals("150 g pack  ·  label serving 25 g  ·  6 servings", PackMath.packSummary(chips()));
        Nutrition sparse = NutriGradeTest.food(400, 5.0, 2.0, 100.0, null, null);
        sparse.netQuantity = 90.0;
        assertEquals("90 g pack", PackMath.packSummary(sparse));
        assertEquals("", PackMath.packSummary(NutriGradeTest.food(400, 5.0, 2.0, 100.0, null, null)));
    }

    @Test public void valuesPrintedForAServingAreConvertedToPer100() {
        // A 45 g pack that prints "per 45 g": 240 kcal, 12 g sugars, 5 g saturated fat, 300 mg sodium.
        Nutrition asPrinted = NutriGradeTest.food(240, 12.0, 5.0, 300.0, null, 3.0);
        Nutrition per100 = PackMath.toPer100(asPrinted, 45);
        assertEquals(533.33, per100.energyKcal, 0.01);
        assertEquals(26.67, per100.sugarsG, 0.01);
        assertEquals(11.11, per100.saturatedFatG, 0.01);
        assertEquals(666.67, per100.sodiumMg, 0.01);
        assertEquals("The printed basis becomes the serving size", 45, per100.servingSize, 0.01);
        assertNull("Untouched values stay null", per100.fibreG);
    }

    @Test public void aPer100LabelIsLeftExactlyAsTyped() {
        Nutrition asPrinted = NutriGradeTest.food(240, 12.0, 5.0, 300.0, 2.0, 3.0);
        Nutrition per100 = PackMath.toPer100(asPrinted, 100);
        assertEquals(240, per100.energyKcal, 0.001);
        assertEquals(12, per100.sugarsG, 0.001);
        assertNull(per100.servingSize);
    }

    @Test public void conversionChangesTheRatingWhenItShould() {
        // Printed per 30 g: looks small. Per 100 g it is clearly a red item.
        Nutrition asPrinted = NutriGradeTest.food(150, 10.0, 3.5, 210.0, 0.5, 1.0);
        assertEquals(NutriGrade.Band.B, NutriGrade.grade(asPrinted).band);
        assertEquals(NutriGrade.Band.C, NutriGrade.grade(PackMath.toPer100(asPrinted, 30)).band);
    }

    @Test public void aBasisOfZeroIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PackMath.toPer100(NutriGradeTest.food(100, 1.0, 1.0, 1.0, null, null), 0));
    }

    @Test public void parserReadsPackAndServingFromLabelText() {
        Nutrition n = NutritionParser.parse("Net Qty 150 g\nServing size 25 g\nServings per pack 6\n" +
            "Nutrition Information per 100 g\nEnergy 520 kcal\nTotal Sugars 8 g\nSaturated Fat 12 g\nSodium 700 mg");
        assertNotNull(n);
        assertEquals(150, n.netQuantity, 0.01);
        assertEquals(25, n.servingSize, 0.01);
        assertEquals(6, n.servingsPerPack, 0.01);
        assertEquals(520, n.energyKcal, 0.01);
    }

    @Test public void kilogramsAndLitresBecomeGramsAndMillilitres() {
        assertEquals(1000, NutritionParser.netQuantity("Net Quantity 1 kg"), 0.01);
        assertEquals(1500, NutritionParser.netQuantity("Net volume 1.5 L"), 0.01);
        assertEquals(500, NutritionParser.netQuantity("Pack size 500 ml"), 0.01);
        assertNull(NutritionParser.netQuantity("Delivery in 8 minutes"));
        assertNull(NutritionParser.netQuantity(null));
    }
}
