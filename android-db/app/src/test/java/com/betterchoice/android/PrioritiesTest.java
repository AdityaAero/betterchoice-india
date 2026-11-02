package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * What matters most comes first: processing, the kind of fat, sugar, refined grain — then saturated
 * fat, sodium and energy. Following van Tulleken (NOVA), Willett, and the Nutrition Society textbook.
 */
public class PrioritiesTest {

    @Test public void ultraProcessingIsReadFromTheIngredientList() {
        IngredientFacts cookie = IngredientFacts.of("Refined Wheat Flour (Maida), Sugar, Edible Vegetable Oil (Palm), "
            + "Cocoa Solids, Invert Sugar Syrup, Emulsifiers (322, 471), Raising Agents (503(ii), 500(ii)), Salt, "
            + "Artificial Flavouring Substances (Chocolate, Vanilla)", false);
        assertTrue(cookie.ultraProcessed());
        assertTrue(cookie.concerns.toString(), cookie.concerns.get(0).startsWith("Ultra-processed: contains"));

        IngredientFacts frooti = IngredientFacts.of("Water, Mango Pulp, Sugar, Acidity Regulator (INS 330), Antioxidant "
            + "(INS 300), Permitted Synthetic Food Colour (INS 110), and Added Flavour", true);
        assertTrue("A synthetic colour alone settles it", frooti.ultraProcessed());
    }

    @Test public void kitchenIngredientsAreNotUltraProcessed() {
        IngredientFacts peanuts = IngredientFacts.of("Peanuts (95%), Edible Vegetable Oil (Groundnut), Iodised Salt", false);
        assertFalse(peanuts.ultraProcessed());
        assertFalse(peanuts.good.contains("No ultra-processed ingredients"));
        IngredientFacts chocolate = IngredientFacts.of("Cocoa Mass, Sugar, Cocoa Butter, Emulsifier (Soy Lecithin)", false);
        assertFalse("One weak marker on its own is not enough", chocolate.ultraProcessed());
        IngredientFacts preserved = IngredientFacts.of("Tomato Paste, Sugar, Salt, Vinegar, Acidity Regulator (260), "
            + "Preservative (211)", false);
        assertFalse("Preservatives and acidity regulators are NOVA 3, not 4", preserved.ultraProcessed());
    }

    @Test public void hydrogenatedFatIsNamedFirst() {
        IngredientFacts vanaspati = IngredientFacts.of("Refined Wheat Flour, Sugar, Hydrogenated Vegetable Fat, Salt", false);
        assertTrue(vanaspati.hydrogenatedFat);
        List<String> ordered = Priorities.concerns(new ArrayList<>(vanaspati.concerns));
        assertTrue(ordered.toString(), ordered.get(0).startsWith("Hydrogenated fat"));
        assertFalse("A claim is not an ingredient",
            IngredientFacts.of("Whole Wheat Flour, Non-Hydrogenated Vegetable Oil, Salt", false).hydrogenatedFat);
    }

    @Test public void theCardLeadsWithProcessingAndSugarNotSodium() {
        List<String> lines = Arrays.asList("High sodium: 720 mg per 100 g", "Energy dense: 560 kcal per 100 g",
            "High sugar: 30 g per 100 g", "Ultra-processed: contains flavourings, emulsifiers",
            "Refined flour (maida) is the main ingredient", "High saturated fat: 12 g per 100 g");
        List<String> ordered = Priorities.concerns(new ArrayList<>(lines));
        assertEquals(Arrays.asList("Ultra-processed: contains flavourings, emulsifiers", "High sugar: 30 g per 100 g",
            "Refined flour (maida) is the main ingredient", "High saturated fat: 12 g per 100 g",
            "High sodium: 720 mg per 100 g", "Energy dense: 560 kcal per 100 g"), ordered);
    }

    @Test public void sodiumIsHighAtTheUkFrontOfPackLevel() {
        Nutrition n = new Nutrition();
        n.energyKcal = 480.0; n.sugarsG = 3.0; n.saturatedFatG = 4.0; n.proteinG = 8.0;
        n.sodiumMg = 540.0;
        assertFalse(NutriGrade.grade(n).concerns.toString().contains("High sodium"));
        n.sodiumMg = 720.0;
        assertTrue(NutriGrade.grade(n).concerns.contains("High sodium: 720 mg per 100 g"));
        Nutrition drink = new Nutrition();
        drink.energyKcal = 20.0; drink.sugarsG = 2.0; drink.saturatedFatG = 0.0; drink.sodiumMg = 350.0; drink.beverage = true;
        assertTrue(NutriGrade.grade(drink, Beverage.of(Beverage.Kind.OTHER)).concerns.toString().contains("Salty for a drink"));
    }

    /** Processing is explained separately and never rewrites the nutrient grade. */
    @Test public void ultraProcessingDoesNotRewriteTheNutrientRating() {
        List<ScreenText> s = new ArrayList<>();
        s.add(new ScreenText("Whey Protein Crisp Bar Chocolate Flavour", 650, false));
        s.add(new ScreenText("₹99", 700, false));
        s.add(new ScreenText("Product Details", 900, true));
        s.add(new ScreenText("Ingredients", 1000, false));
        s.add(new ScreenText("Whey Protein Isolate, Soy Protein Crisps, Maltodextrin, Sucralose (INS 955), Emulsifier (322), "
            + "Artificial Flavour (Chocolate)", 1010, false));
        s.add(new ScreenText("Nutrition Information (Per 100 g)", 1100, false));
        s.add(new ScreenText("Energy 330 kcal", 1120, false));
        s.add(new ScreenText("Protein 30 g", 1140, false));
        s.add(new ScreenText("Total Sugars 2 g", 1160, false));
        s.add(new ScreenText("Saturated Fat 1 g", 1180, false));
        s.add(new ScreenText("Sodium 150 mg", 1200, false));
        s.add(new ScreenText("Dietary Fibre 12 g", 1220, false));
        GradeResult r = ProductAnalyzer.analyze(s, 2400, "com.zeptoconsumerapp", p -> PackScan.EMPTY, null, null);
        assertEquals(GradeResult.State.GRADED, r.state);
        assertEquals("The A/B/C bubble remains the nutrient result", NutriGrade.Band.A, r.band);
        assertFalse(r.notes.toString(), r.notes.toString().contains("held"));
        assertTrue(r.concerns.toString(), r.concerns.get(0).startsWith("Ultra-processed"));
    }

    @Test public void goodPointsLeadWithWhatMattersMost() {
        List<String> ordered = Priorities.positives(new ArrayList<>(Arrays.asList("Low sodium: 30 mg per 100 g",
            "No palm oil", "Good protein: 12 g per 100 g", "Oats are the main ingredient", "High fibre: 9 g per 100 g",
            "No ultra-processed ingredients")));
        assertEquals("Oats are the main ingredient", ordered.get(0));
        assertTrue("sodium last, and at most five lines", ordered.size() == 5 && !ordered.contains("Low sodium: 30 mg per 100 g"));
    }
}
