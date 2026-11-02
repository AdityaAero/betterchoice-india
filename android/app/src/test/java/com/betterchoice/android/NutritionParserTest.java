package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class NutritionParserTest {
    static final String CHIPS_TABLE = "Nutritional Information (Per 100g)\nEnergy (kcal)\n536\nProtein (g)\n6.6\nCarbohydrate (g)\n52\n" +
        "Total Sugars (g)\n0.5\nAdded Sugars (g)\n0\nTotal Fat (g)\n33\nSaturated Fat (g)\n3.1\nTrans Fat (g)\n0.1\nSodium (mg)\n700\nDietary Fibre (g)\n4.4";

    @Test public void readsRetailerTableWithBracketedUnitsOnSeparateLines() {
        Nutrition n = NutritionParser.parse(CHIPS_TABLE);
        assertNotNull(n);
        assertFalse(n.beverage);
        assertEquals(536, n.energyKcal, 0.01);
        assertEquals(0.5, n.sugarsG, 0.01);
        assertEquals(3.1, n.saturatedFatG, 0.01);
        assertEquals(700, n.sodiumMg, 0.01);
        assertEquals(4.4, n.fibreG, 0.01);
        assertEquals(6.6, n.proteinG, 0.01);
    }

    @Test public void prefersKcalWhenKilojoulesAreAlsoListed() {
        Nutrition n = NutritionParser.parse("Per 100 g: Energy 2243 kJ / 536 kcal, Total sugars 0.5 g, Saturated fat 3.1 g, Sodium 700 mg");
        assertNotNull(n);
        assertEquals(536, n.energyKcal, 0.01);
    }

    @Test public void convertsKilojoulesAndSaltWhenThatIsAllThereIs() {
        Nutrition n = NutritionParser.parse("Typical values per 100g\nEnergy 1674 kJ\nSugars 10 g\nSaturates 3 g\nSalt 0.75 g");
        assertNotNull(n);
        assertEquals(400, n.energyKcal, 0.5);
        assertEquals(300, n.sodiumMg, 0.5);
    }

    @Test public void scalesPerServingTablesOnlyWhenServingSizeIsStated() {
        Nutrition n = NutritionParser.parse("Serving size 30 g\nEnergy 160 kcal\nTotal sugars 9 g\nSaturated fat 2 g\nSodium 120 mg");
        assertNotNull(n);
        assertEquals(533.3, n.energyKcal, 0.1);
        assertEquals(30, n.sugarsG, 0.01);
        assertEquals(400, n.sodiumMg, 0.1);
    }

    @Test public void detectsBeveragesPer100ml() {
        Nutrition n = NutritionParser.parse("Nutrition Information per 100 ml\nEnergy 42 kcal\nTotal Sugars 10.6 g\nSodium 10 mg");
        assertNotNull(n);
        assertTrue(n.beverage);
    }

    @Test public void productNamesAndPartialTablesAreNotNutrition() {
        assertNull(NutritionParser.parse("Red Bull Energy Drink 250 ml\n₹125\nAdd to cart"));
        assertNull(NutritionParser.parse("Energy 120 kcal\nProtein 4 g"));
        assertNull(NutritionParser.parse(""));
        assertNull(NutritionParser.parse(null));
    }

    @Test public void implausibleValuesAreRejected() {
        assertNull(NutritionParser.parse("Per 100 g\nEnergy 5000 kcal\nSugars 10 g\nSodium 100 mg"));
    }

    // --- A real Indian back-of-pack panel: Beyond Snack banana chips -------------------------------
    // Transcribed from the pack photo. Two numeric columns (per 100 g, then % RDA per serve), the
    // basis written as "Per 100gms", and a serving size stated as "20g Per Serving".
    static final String BEYOND_SNACK = String.join("\n",
        "NUTRITIONAL INFORMATION",
        "Approximate Composition",
        "Serving Size", "20g Per Serving",
        "Nutrients", "Per 100gms", "% RDA per Serve",
        "Energy Values", "461.23 KCal", "4.61%",
        "Total Fat", "28.3 g", "8.45%",
        "Saturated Fat", "7.22 g", "6.56%",
        "Trans Fat", "0 g", "0.00%",
        "Cholesterol", "-", "-",
        "Protein", "3.7 g", "1.48%",
        "Carbohydrates", "70.32 g", "5.11%",
        "Total Sugars", "3.18 g", "1.27%",
        "Added Sugar", "2.32 g", "0.93%",
        "Dietary Fibres", "7.49 g", "4.99%",
        "Sodium", "2025.64 mg", "20.26%",
        "Percent Recommended Dietary Allowances are based on a 2000 Kcal diet for an average adult.");

    private static void assertBeyondSnack(Nutrition n) {
        assertNotNull("The panel is fully legible; it must produce a rating", n);
        assertEquals(461.23, n.energyKcal, 0.01);
        assertEquals(3.18, n.sugarsG, 0.01);
        assertEquals(7.22, n.saturatedFatG, 0.01);
        assertEquals("Sodium is four digits on this pack", 2025.64, n.sodiumMg, 0.01);
        assertEquals(7.49, n.fibreG, 0.01);
        assertEquals(3.7, n.proteinG, 0.01);
        assertEquals("Stated as \"20g Per Serving\"", 20.0, n.servingSize, 0.01);
        assertFalse(n.beverage);
        assertEquals("2025 mg of sodium per 100 g is not a green light", NutriGrade.Band.C, NutriGrade.grade(n).band);
    }

    @Test public void readsTheRealPanelWhenColumnsComeOutAsSeparateBlocks() {
        assertBeyondSnack(NutritionParser.parse(BEYOND_SNACK));
    }

    /** The same pack, but with each table row on one line, which is how ML Kit usually emits a table. */
    static final String BEYOND_SNACK_ROWS = String.join("\n",
        "NUTRITIONAL INFORMATION",
        "Approximate Composition",
        "Serving Size 20g Per Serving",
        "Nutrients Per 100gms % RDA per Serve",
        "Energy Values 461.23 KCal 4.61%",
        "Total Fat 28.3 g 8.45%",
        "Saturated Fat 7.22 g 6.56%",
        "Trans Fat 0 g 0.00%",
        "Cholesterol - -",
        "Protein 3.7 g 1.48%",
        "Carbohydrates 70.32 g 5.11%",
        "Total Sugars 3.18 g 1.27%",
        "Added Sugar 2.32 g 0.93%",
        "Dietary Fibres 7.49 g 4.99%",
        "Sodium 2025.64 mg 20.26%",
        "Percent Recommended Dietary Allowances are based on a 2000 Kcal diet for an average adult.");

    /**
     * This is the case that used to return nothing at all. "Per 100gms" was not recognised as the
     * basis, so the serving size on the row above was used instead and every value was multiplied by
     * five; 2306 kcal then failed the sanity check and the whole panel was discarded.
     */
    @Test public void readsTheRealPanelWhenEachRowIsOneLine() {
        assertBeyondSnack(NutritionParser.parse(BEYOND_SNACK_ROWS));
    }

    @Test public void perHundredIsRecognisedHoweverThePackWritesIt() {
        for (String basis : new String[]{"Per 100gms", "Per 100 gms", "per100g", "/100 g", "(100 g)", "Per 100 grams"}) {
            Nutrition n = NutritionParser.parse(basis + "\nEnergy 400 kcal\nTotal Sugars 10 g\nSaturated Fat 5 g\nSodium 300 mg");
            assertNotNull("Basis not recognised: " + basis, n);
            assertEquals("Values must not be rescaled: " + basis, 400, n.energyKcal, 0.01);
        }
    }

    /** Some panels put "% RDA" in the first column. The percentage must never become the value. */
    @Test public void aPercentageColumnIsNeverMistakenForTheValue() {
        Nutrition n = NutritionParser.parse(String.join("\n",
            "Nutrients % RDA per Serve Per 100 g",
            "Energy 4.61% 461 kcal",
            "Total Sugars 1.27% 3.18 g",
            "Saturated Fat 6.56% 7.22 g",
            "Protein 1.48% 3.7 g",
            "Sodium 20.26% 2025 mg"));
        assertNotNull(n);
        assertEquals(461, n.energyKcal, 0.01);
        assertEquals(3.18, n.sugarsG, 0.01);
        assertEquals("Not 1.48", 3.7, n.proteinG, 0.01);
        assertEquals("Not 20.26", 2025, n.sodiumMg, 0.01);
    }

    @Test public void largeSodiumValuesAreNotTruncated() {
        Nutrition n = NutritionParser.parse("Per 100 g\nEnergy 400 kcal\nTotal Sugars 2 g\nSodium 12500 mg");
        assertNotNull(n);
        assertEquals("12500, not 1250", 12500, n.sodiumMg, 0.01);
    }
}
