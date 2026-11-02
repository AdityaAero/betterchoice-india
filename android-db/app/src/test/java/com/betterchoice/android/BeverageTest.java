package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Drinks, judged on their own terms. The two failures this pins down were both serious: a zero-sugar
 * cola came out identical to water, and plain toned milk came out identical to cola.
 */
public class BeverageTest {

    private static Nutrition drink(double kcal, Double sugars, Double satFat, Double sodium, Double protein) {
        Nutrition n = new Nutrition();
        n.energyKcal = kcal; n.sugarsG = sugars; n.saturatedFatG = satFat;
        n.sodiumMg = sodium; n.proteinG = protein; n.beverage = true;
        return n;
    }

    private static NutriGrade.Band band(Nutrition n, String name, String ingredients) {
        return NutriGrade.grade(n, Beverage.classify(name, ingredients, n.beverage)).band;
    }

    // --- Water is the reference -------------------------------------------------------------------

    @Test public void waterIsTheOnlyDrinkThatReachesTheTopBand() {
        Nutrition water = drink(0, 0.0, 0.0, 0.0, 0.0);
        assertEquals(NutriGrade.Band.A, band(water, "Bisleri Packaged Drinking Water 1 L", null));
        assertEquals(NutriGrade.Band.A, band(water, "Himalayan Natural Mineral Water 1 L", null));
    }

    /**
     * The bug this exists to stop. A zero-sugar cola scores almost nothing on the drink scale, so it
     * used to come out A — dark green, indistinguishable from water. WHO's 2023 guideline advises
     * against non-sugar sweeteners as a way to manage weight, so it is better than the sugary
     * version and it is not water.
     */
    @Test public void aZeroSugarColaIsBetterThanColaAndStillNotWater() {
        Nutrition diet = drink(0.2, 0.0, 0.0, 10.0, 0.0);
        NutriGrade.Result result = NutriGrade.grade(diet,
            Beverage.classify("Diet Coke Can 300 ml", "carbonated water, caramel colour, aspartame (INS 951)", true));
        assertEquals("Never as good as water", NutriGrade.Band.B, result.band);
        assertTrue(result.reasons.toString(), result.reasons.contains("Better than the sugary version. Still not water."));

        assertEquals(NutriGrade.Band.B, band(diet, "Coca-Cola Zero Sugar 300 ml", "carbonated water, acesulfame potassium"));
        assertEquals("Detected from the sweetener alone", NutriGrade.Band.B,
            band(diet, "Some Cola 300 ml", "water, sucralose, flavour"));
    }

    @Test public void fullSugarColaStaysInTheBottomBand() {
        Nutrition cola = drink(42, 10.6, 0.0, 5.0, 0.0);
        NutriGrade.Result result = NutriGrade.grade(cola,
            Beverage.classify("Coca-Cola Original Taste 750 ml", "carbonated water, sugar, caramel colour", true));
        assertEquals(NutriGrade.Band.C, result.band);
        assertTrue(result.reasons.toString().contains("High sugar"));
    }

    // --- Milk is food in liquid form ---------------------------------------------------------------

    /**
     * Toned milk — the milk most of India drinks — reaches the top band, and nothing was special-
     * cased to get it there. Protein earns up to seven favourable points on the 2023 beverage scale,
     * which is enough to carry it. Under the old drink scale it landed on C, beside cola, because
     * lactose was penalised exactly like added sugar.
     */
    @Test public void tonedMilkReachesTheTopBandOnItsProtein() {
        Nutrition milk = drink(58, 4.8, 1.9, 45.0, 3.2);
        NutriGrade.Result result = NutriGrade.grade(milk,
            Beverage.classify("Amul Toned Milk 500 ml", "toned milk", true));
        assertEquals(NutriGrade.Band.A, result.band);
        assertTrue(result.positives.toString(), result.positives.contains("Good protein: 3.2 g per 100 ml"));
        assertTrue(result.notes.toString(), result.notes.contains("Its sugar is lactose, not added."));
        assertFalse("Its lactose must not be listed again as a concern: " + result.concerns,
            result.concerns.toString().contains("sugar"));
    }

    /** Almond, soy and oat drinks carry no lactose; saying they do would be false. */
    @Test public void aPlantMilkIsNeverSaidToContainLactose() {
        Nutrition almond = drink(18, 0.4, 0.1, 40.0, 0.6);
        NutriGrade.Result result = NutriGrade.grade(almond,
            Beverage.classify("So Good Almond Milk Unsweetened 1 L", "water, almonds (2.5%)", true));
        assertEquals(Beverage.Kind.PLANT_MILK, result.identity.kind);
        assertFalse(result.reasons.toString(), result.reasons.toString().contains("lactose, not added"));
        Nutrition sweet = drink(55, 6.0, 0.3, 40.0, 3.0);
        assertEquals("Sugar above the drink low-sugar level means sugar was added", Beverage.Kind.SWEETENED,
            NutriGrade.grade(sweet, Beverage.classify("Soya Milk Chocolate 200 ml", "water, soybean, sugar", true)).identity.kind);
    }

    @Test public void plainMilkWithCandyLevelSugarIsNotCalledPlainMilk() {
        Nutrition sweet = drink(80, 11.0, 2.0, 45.0, 3.0);
        NutriGrade.Result result = NutriGrade.grade(sweet, Beverage.classify("Nandini Milk 200 ml", "milk, sugar", true));
        assertEquals(Beverage.Kind.SWEETENED, result.identity.kind);
        assertFalse(result.reasons.toString(), result.reasons.toString().contains("lactose, not added"));
    }

    /**
     * Full cream milk does not, and that is the algorithm rather than a choice: 87 kcal and 3.4 g of
     * saturated fat per 100 ml outweigh the same protein credit. Reported as it comes out, because
     * quietly lifting it would make every other grade less trustworthy.
     */
    @Test public void fullCreamMilkIsNotTheSameAsTonedMilk() {
        Nutrition full = drink(87, 4.7, 3.4, 45.0, 3.2);
        NutriGrade.Band band = NutriGrade.grade(full, Beverage.classify("Amul Gold Full Cream Milk 500 ml", "milk", true)).band;
        assertNotEquals("Energy density and saturated fat carry it down", NutriGrade.Band.A, band);
    }

    @Test public void aPlantDrinkIsJudgedOnTheSameScale() {
        Nutrition soy = drink(45, 2.5, 0.3, 40.0, 3.0);
        soy.fibreG = 0.5;
        assertEquals(NutriGrade.Band.A, band(soy, "Soy Milk Unsweetened 1 L", "water, soybean"));
    }

    @Test public void aSweetenedMilkDrinkIsNotPlainMilk() {
        Nutrition shake = drink(90, 12.0, 2.0, 50.0, 3.0);
        assertNotEquals("A milkshake is a sweetened drink", NutriGrade.Band.A,
            band(shake, "Amul Kool Flavoured Milk Rose 200 ml", "toned milk, sugar, rose flavour"));
        assertNotEquals(NutriGrade.Band.A, band(shake, "Sweet Lassi 200 ml", "curd, sugar"));
    }

    // --- Juice: "no added sugar" is not "low sugar" ------------------------------------------------

    /**
     * Juice earns fruit points and still carries its sugar, so it lands in the middle band rather
     * than with cola at one end or water at the other. That is the 2023 algorithm's own arithmetic:
     * 10 adverse points for energy and sugar, 6 favourable for being entirely fruit.
     */
    @Test public void oneHundredPercentJuiceLandsBetweenWaterAndCola() {
        Nutrition juice = drink(45, 9.0, 0.0, 1.0, 0.7);
        juice.fibreG = 0.2;
        NutriGrade.Result result = NutriGrade.grade(juice,
            Beverage.classify("Real 100% Orange Juice 1 L", "orange juice concentrate, water", true));
        assertEquals(NutriGrade.Band.B, result.band);
        assertTrue(result.reasons.toString(),
            result.reasons.contains("No added sugar, but fruit sugars still count: WHO calls these free sugars."));
        assertTrue("The fruit credit must be visible", result.reasons.toString().contains("100% fruit"));
    }

    /** A fruit drink is mostly not fruit, so it earns nothing without a stated share. */
    @Test public void aFruitDrinkEarnsNoFruitCreditWithoutAStatedShare() {
        assertEquals(0, Beverage.classify("Mango Fruit Drink 200 ml", "water, mango pulp, sugar", true).fruitPercent);
        assertEquals("A stated share is believed", 20,
            Beverage.classify("Mango Nectar 200 ml", "water, 20% mango pulp, sugar", true).fruitPercent);
        assertEquals("Juice is fruit outright", 100,
            Beverage.classify("Tropicana 100% Apple Juice 1 L", "apple juice", true).fruitPercent);
    }

    /** Coconut water's sugar is natural and still counts; being all fruit carries it. */
    @Test public void coconutWaterIsRecognisedInItsOwnRight() {
        Nutrition coconut = drink(19, 2.6, 0.0, 25.0, 0.7);
        Beverage kind = Beverage.classify("Tender Coconut Water 200 ml", "coconut water", true);
        assertEquals(Beverage.Kind.COCONUT_WATER, kind.kind);
        assertEquals(NutriGrade.Band.A, NutriGrade.grade(coconut, kind).band);
    }

    @Test public void aJuiceDrinkIsToldApartFromRealJuice() {
        assertEquals(Beverage.Kind.JUICE_DRINK,
            Beverage.classify("Frooti Mango Fruit Drink 200 ml", "water, mango pulp, sugar", true).kind);
        assertEquals(Beverage.Kind.JUICE,
            Beverage.classify("Tropicana 100% Apple Juice 1 L", "apple juice", true).kind);
    }

    // --- Classification --------------------------------------------------------------------------

    @Test public void flavouredOrSweetenedWaterIsNotWater() {
        assertNotEquals(Beverage.Kind.WATER,
            Beverage.classify("Flavoured Water Lemon 500 ml", "water, sugar, lemon flavour", true).kind);
        assertNotEquals(Beverage.Kind.WATER,
            Beverage.classify("Sparkling Water with Sucralose", "carbonated water, sucralose", true).kind);
        assertEquals(Beverage.Kind.WATER,
            Beverage.classify("Kinley Packaged Drinking Water 1 L", "water, added minerals", false).kind);
    }

    @Test public void unsweetenedTeaAndCoffeeAreCloseToWater() {
        Nutrition tea = drink(1, 0.0, 0.0, 1.0, 0.0);
        assertEquals(NutriGrade.Band.A, band(tea, "Organic Green Tea Bags", "green tea leaves"));
    }

    @Test public void aFoodIsUnaffectedByAnyOfThis() {
        Nutrition chips = new Nutrition();
        chips.energyKcal = 461.23; chips.sugarsG = 3.18; chips.saturatedFatG = 7.22;
        chips.sodiumMg = 2025.64; chips.fibreG = 7.49; chips.proteinG = 3.7;
        Beverage identity = Beverage.classify("Beyond Snack Banana Chips 80 g", "raw banana, rice bran oil, salt", false);
        assertFalse(identity.isDrink());
        assertTrue("Chips are a food by name", identity.definitelyFood);
        assertEquals("Banana is not a nut or pulse, so no credit", 0, identity.fruitPercent);
        assertEquals(NutriGrade.Band.C, NutriGrade.grade(chips, identity).band);
    }

    /** Water bottles rarely print a nutrition table, and there is nothing in one to measure. */
    @Test public void waterWithoutAPanelDoesNotInventSodium() {
        java.util.List<ScreenText> screen = new java.util.ArrayList<>();
        screen.add(new ScreenText("Bisleri Packaged Drinking Water 1 L", 300, false));
        screen.add(new ScreenText("₹20", 340, false));
        screen.add(new ScreenText("Product Details", 600, true));
        screen.add(new ScreenText("Packaged drinking water with added minerals", 680, false));
        screen.add(new ScreenText("ADD", 900, true));
        GradeResult result = ProductAnalyzer.analyze(screen, 2400, "com.grofers.customerapp",
            p -> PackScan.EMPTY, null, null);
        assertEquals(GradeResult.State.NEEDS_INPUT, result.state);
        assertNull(result.band);
        assertNull(result.nutrition);
    }
}
