package com.betterchoice.android;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * The open database answers in its own units and shapes; these pin the conversion into ours.
 * Runs under Robolectric because org.json is part of the Android framework: in a plain unit test it
 * is the stubbed android.jar and every parse quietly returns nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class OpenFoodFactsTest {
    private static final String MAGGI = "08901058000450";

    private static OpenFoodFacts returning(String body) {
        return new OpenFoodFacts(url -> body);
    }

    @Test public void readsAnIndianPackIntoPerHundredValues() {
        Nutrition n = returning("{\"status\":1,\"product\":{\"product_name\":\"Rolled Oats\","
            + "\"quantity\":\"1 kg\",\"serving_size\":\"40 g\",\"nutriments\":{"
            + "\"energy-kcal_100g\":379,\"sugars_100g\":1,\"saturated-fat_100g\":1.2,"
            + "\"sodium_100g\":0.006,\"fiber_100g\":10,\"proteins_100g\":13}}}").find(MAGGI);
        assertNotNull(n);
        assertEquals(379, n.energyKcal, 0.001);
        assertEquals("Their sodium is grams, ours is milligrams", 6.0, n.sodiumMg, 0.001);
        assertEquals(10.0, n.fibreG, 0.001);
        assertEquals("1 kg becomes 1000 g", 1000.0, n.netQuantity, 0.001);
        assertEquals(40.0, n.servingSize, 0.001);
        assertEquals(NutriGrade.Band.A, NutriGrade.grade(n).band);
    }

    @Test public void saltIsConvertedToSodiumWhenSodiumIsMissing() {
        // Enough nutrients to be gradeable at all: one number is never enough for a rating.
        Nutrition n = returning("{\"status\":1,\"product\":{\"nutriments\":{"
            + "\"energy-kcal_100g\":500,\"sugars_100g\":2,\"salt_100g\":1.25}}}").find(MAGGI);
        assertNotNull(n);
        assertEquals("salt 1.25 g = sodium 500 mg", 500.0, n.sodiumMg, 0.5);
    }

    @Test public void kilojoulesAreUsedWhenCaloriesAreMissing() {
        Nutrition n = returning("{\"status\":1,\"product\":{\"nutriments\":{"
            + "\"energy_100g\":2000,\"sugars_100g\":2,\"saturated-fat_100g\":1}}}").find(MAGGI);
        assertNotNull(n);
        assertEquals("2000 kJ is about 478 kcal", 478.0, n.energyKcal, 1.0);
    }

    @Test public void numbersSentAsStringsStillParse() {
        Nutrition n = returning("{\"status\":1,\"product\":{\"nutriments\":{"
            + "\"energy-kcal_100g\":\"250\",\"sugars_100g\":\"12.5\",\"saturated-fat_100g\":\"3\"}}}").find(MAGGI);
        assertNotNull(n);
        assertEquals(250, n.energyKcal, 0.001);
        assertEquals(12.5, n.sugarsG, 0.001);
        assertEquals(3.0, n.saturatedFatG, 0.001);
    }

    /** A single nutrient is never enough to put a letter on a product. */
    @Test public void oneLonelyNumberIsNotGradeable() {
        assertNull(returning("{\"status\":1,\"product\":{\"nutriments\":{"
            + "\"energy-kcal_100g\":500,\"salt_100g\":1.25}}}").find(MAGGI));
    }

    @Test public void anUnknownOrEmptyProductIsNotGuessedAt() {
        assertNull(returning("{\"status\":0}").find(MAGGI));
        assertNull("No nutriments at all", returning("{\"status\":1,\"product\":{}}").find(MAGGI));
        assertNull("No energy means no Nutri-Score", returning("{\"status\":1,\"product\":{\"nutriments\":{\"sugars_100g\":3}}}").find(MAGGI));
        assertNull(returning("").find(MAGGI));
    }

    @Test public void aBadBarcodeIsNeverSentToTheNetwork() {
        boolean[] called = {false};
        OpenFoodFacts off = new OpenFoodFacts(url -> { called[0] = true; return "{\"status\":1}"; });
        assertNull(off.find("8901058000451"));   // wrong check digit
        assertNull(off.find(null));
        assertFalse("A number we know is invalid must not become a request", called[0]);
    }

    @Test public void aNetworkFailureIsJustAMissNotACrash() {
        OpenFoodFacts off = new OpenFoodFacts(url -> { throw new java.io.IOException("offline"); });
        assertNull(off.find(MAGGI));
    }

    @Test public void barePackSizesParseIntoGramsAndMillilitres() {
        assertEquals(150.0, OpenFoodFacts.amount("150 g", 20000), 0.001);
        assertEquals(1000.0, OpenFoodFacts.amount("1 kg", 20000), 0.001);
        assertEquals(500.0, OpenFoodFacts.amount("500 ml", 20000), 0.001);
        assertEquals(2000.0, OpenFoodFacts.amount("2 L", 20000), 0.001);
        assertEquals(30.5, OpenFoodFacts.amount("30,5 g", 20000), 0.001);
        assertNull("Beyond a believable pack", OpenFoodFacts.amount("50 kg", 20000));
        assertNull(OpenFoodFacts.amount("family pack", 20000));
        assertNull(OpenFoodFacts.amount(null, 20000));
    }

    @Test public void chipsAreNotADrinkBecauseOfAFoodsAndBeveragesTag() throws Exception {
        org.json.JSONObject product = new org.json.JSONObject("{\"categories_tags\":[\"en:plant-based-foods-and-beverages\","
            + "\"en:snacks\",\"en:crisps\"]}");
        assertFalse(OpenFoodFacts.beverage(product));
        org.json.JSONObject cola = new org.json.JSONObject("{\"categories_tags\":[\"en:beverages\",\"en:sodas\"]}");
        assertTrue(OpenFoodFacts.beverage(cola));
    }

}
