package com.betterchoice.android;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlinkitRegressionTest {
    @Test public void additiveSuffixDoesNotConsumeTheNextWord() {
        IngredientParser.Result parsed = IngredientParser.parse(
            "Leavening Agents (INS 503 ii, INS 500 ii), Emulsifiers (INS 322 from Soy, INS 472e, INS 435 and INS 471)");
        assertEquals(java.util.List.of("503ii", "500ii", "322", "472e", "435", "471"),
            parsed.additives.stream().map(a -> a.code).collect(java.util.stream.Collectors.toList()));
    }
    @Test public void wrappedCapitalisedIngredientPhotoRetainsAdditives() {
        String photo = "INGREDIENTS\nRefined Wheat Flour (Maida), Sugar, Edible Vegetable\n"
            + "Oil (Palm), Milk Solids, Dry Nuts (1%) (Almond and\n"
            + "Cashew), Golden Syrup, Leavening Agents (INS 503 ii,\n"
            + "INS 500 ii), Emulsifiers (INS 322e from Soy, INS 472e,\n"
            + "INS 435 and INS 471), Iodised Salt, Artificial Flavouring\n"
            + "Substances (Saffron, Cashew, Almond & Milk).\n"
            + "Allergen Advice: Contains Wheat, Milk, Soy and Nut.";
        LabelEvidence evidence = new LabelEvidence();
        evidence.observe(photo);
        evidence.observe("Ingredients: Refined Wheat Flour (Maida), Sugar, Edible Vegetable");
        IngredientFacts facts = IngredientFacts.of(IngredientFacts.section(evidence.text()), false);
        assertTrue(facts.ultraMarkers.contains("emulsifiers"));
        assertTrue(facts.ultraMarkers.contains("flavourings"));
        assertFalse(facts.additives.isEmpty());
        assertFalse(facts.good.contains("No ultra-processed ingredients"));
        assertTrue(evidence.text().contains("Soy and Nut"));
    }

    @Test public void partialListNeverClaimsAbsenceOfProcessingMarkers() {
        IngredientFacts facts = IngredientFacts.of("Refined Wheat Flour, Sugar, Edible Vegetable", false);
        assertFalse(facts.good.contains("No ultra-processed ingredients"));
        assertEquals("Wheat Flour, Sugar", IngredientFacts.section(
            "Ingredients\nWheat Flour, Sugar\nFlavour: Mango\nNutrition Information\nEnergy 100 kcal"));
    }
    @Test public void repeatedRowBasisIsNotANutrientValue() {
        Nutrition n = NutritionParser.parse("Nutritional Information\n"
            + "Protein Per 100 g 5 g\nTotal Sugar Per 100 g 24.6 g\n"
            + "Added Sugar Per 100 g 22.9 g\nTotal Fat Per 100 g 21.2 g\n"
            + "Saturated Fat Per 100 g 9.5 g\nSodium Per 100 g 195 mg\n"
            + "Energy Per 100 g 477 kcal\nServing size 30 g");
        assertNotNull(n);
        assertTrue(n.basisStated);
        assertEquals(24.6, n.sugarsG, .001);
        assertEquals(22.9, n.addedSugarsG, .001);
        assertEquals(9.5, n.saturatedFatG, .001);
        assertEquals(195, n.sodiumMg, .001);
        assertEquals(5, n.proteinG, .001);
        assertEquals(477, n.energyKcal, .001);
    }

    @Test public void toolbarAfterRailInTraversalStillIdentifiesChips() {
        ProductPage page = ProductPage.detect(List.of(
            new ScreenText("Classic Salted", 1082, false),
            new ScreenText("Chips & Crisps", 1082, false),
            new ScreenText("View details", 1034, true),
            new ScreenText("Similar products", 2063, false),
            new ScreenText("Dove Shampoo", 2172, false),
            new ScreenText("Lay's Classic Salted Potato Chips", 148, false),
            new ScreenText("₹35", 2227, false)), 2392, "com.grofers.customerapp");
        assertNotNull(page);
        assertEquals("Lay's Classic Salted Potato Chips", page.name);
        assertTrue(page.food);
    }

    @Test public void minutesInProductNameAreAllowed() {
        ProductPage page = ProductPage.detect(ProductAnalyzerTest.productPage(
            "Maggi 2 Minutes Instant Noodles Made With Quality Spices"),
            2400, ProductAnalyzerTest.BLINKIT);
        assertNotNull(page);
        assertTrue(page.name.startsWith("Maggi"));
        assertTrue(page.food);
    }

    @Test public void ingredientPhotoSurvivesLaterNutritionPhoto() {
        LabelEvidence facts = new LabelEvidence();
        facts.observe("Ingredients: Refined Wheat Flour, Milk Solids, Soy Lecithin INS 322.\n"
            + "Allergen advice: Contains Wheat, Milk, Soy, Nuts");
        facts.observe("Nutrition per 100g Energy 477 kcal Total Sugar 24.6g");
        assertTrue(facts.text().contains("Milk Solids"));
        assertTrue(facts.text().contains("Soy, Nuts"));
        assertFalse(facts.text().contains("477"));
        assertEquals(1, IngredientFacts.of(IngredientFacts.section(facts.text()), false).additives.size());
    }

    @Test public void explicitlyBasedRowsSurviveScrollButNeverMixGramAndMlPanels() {
        LabelEvidence facts = new LabelEvidence();
        facts.observe("Total Sugar Per 100 g 5.3 g\nTotal Fat Per 100 g 0 g");
        facts.observe("Sodium Per 100 g 8 mg\nEnergy Per 100 g 24 kcal");
        Nutrition n = NutritionParser.parse(facts.panel());
        assertNotNull(n);
        assertEquals(5.3, n.sugarsG, .001);
        facts.observe("Energy Per 100 ml 30 kcal");
        assertNull(NutritionParser.parse(facts.panel()));
        assertFalse(facts.panel().contains("5.3"));
    }

    @Test public void capturedChipsNoodlesColaAndMilkRowsReadTheirActualAmounts() {
        double[][] panels = {{553, .6, 13, 500, 6.7}, {384, 1.8, 8.2, 1000, 8},
            {44, 10.6, 0, 8.5, 0}, {63, 4, 2.9, 40, 3}};
        for (double[] p : panels) {
            Nutrition n = NutritionParser.parse("Nutritional Information\n"
                + "Total Sugar Per 100 g " + p[1] + " g\n"
                + "Saturated Fat Per 100 g " + p[2] + " g\n"
                + "Sodium Per 100 g " + p[3] + " mg\n"
                + "Protein Per 100 g " + p[4] + " g\n"
                + "Energy Per 100 g " + p[0] + " kcal");
            assertNotNull(n);
            assertEquals(p[0], n.energyKcal, .001);
            assertEquals(p[1], n.sugarsG, .001);
            assertEquals(p[2], n.saturatedFatG, .001);
            assertEquals(p[3], n.sodiumMg, .001);
            assertEquals(p[4], n.proteinG, .001);
        }
    }

    @Test public void scrolledPanelWorksWhenSectionHeadingHasLeftTheScreen() {
        ProductPage anchor = ProductPage.of("Coca-Cola Soft Drink", 38.0, ProductAnalyzerTest.BLINKIT);
        ProductPage page = ProductPage.detect(List.of(
            new ScreenText("Coca-Cola Soft Drink", 762, false),
            new ScreenText("Added Sugar Per 100 g 10.6 g", 920, false),
            new ScreenText("Saturated Fat Per 100 g 0 g", 1120, false),
            new ScreenText("Sodium Per 100 g 8.5 mg", 1820, false),
            new ScreenText("Energy Per 100 g 44 kcal", 1890, false),
            new ScreenText("₹38", 2190, false)), 2392, ProductAnalyzerTest.BLINKIT, null, anchor);
        assertNotNull(page);
        assertEquals(anchor.key(), page.key());
        assertTrue(page.food);
    }
}
