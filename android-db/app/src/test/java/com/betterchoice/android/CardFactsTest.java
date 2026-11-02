package com.betterchoice.android;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * What the card says for whom, as rules over whole classes of shopper, not single products: a condition makes its
 * nutrient count from where a food stops being "low" in it; everyone sees the general "high" levels and the good.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CardFactsTest {
    private static GradeResult rated(double kcal, double sugar, double sat, double sodium, double protein, double fibre) {
        Nutrition n = new Nutrition();
        n.basisStated = true;
        n.energyKcal = kcal; n.sugarsG = sugar; n.saturatedFatG = sat; n.sodiumMg = sodium; n.proteinG = protein; n.fibreG = fibre;
        n.totalFatG = sat * 2; n.carbohydrateG = 50.0;
        return GradeResult.graded(n, GradeResult.Source.LABEL_TEXT, null);
    }

    private static RecommendationContext person(String health, String goals) {
        return RecommendationContext.of(CardLayoutTest.profile(health, goals));
    }

    @Test public void eachConditionCountsItsNutrientFromAboveLowAndSaysWhy() {
        // 400 mg sodium, 12 g sugar, 3 g saturated fat: none "high", each above FSSAI's "low" level.
        GradeResult r = rated(380, 12, 3, 400, 8, 3);
        CardFacts bp = CardFacts.of(r, person("hypertension", "aware"));
        assertTrue(bp.bad.toString(), bp.bad.get(0).personal && bp.bad.get(0).text.toLowerCase().contains("sodium") && bp.bad.get(0).text.endsWith("your BP"));
        CardFacts sugar = CardFacts.of(r, person("diabetes", "aware"));
        assertTrue(sugar.bad.toString(), sugar.bad.stream().anyMatch(l -> l.personal && l.text.contains("Sugar") && l.text.endsWith("your diabetes")));
        CardFacts heart = CardFacts.of(r, person("cholesterol", "aware"));
        assertTrue(heart.bad.toString(), heart.bad.stream().anyMatch(l -> l.personal && l.text.contains("Saturated fat") && l.text.endsWith("your heart")));
        CardFacts everyone = CardFacts.of(r, person("", "aware"));
        for (CardFacts.Line l : everyone.bad) assertFalse("no personal tag without the condition: " + l, l.personal);
        assertFalse("a moderate sodium is not flagged for everyone as if it were theirs",
            everyone.bad.stream().anyMatch(l -> l.text.contains("not low")));
    }

    @Test public void theGoodIsSaidForEveryoneAndTiedToTheirGoalWhenItHelps() {
        GradeResult soya = rated(345, 7, 0.1, 30, 52, 13);
        CardFacts everyone = CardFacts.of(soya, person("", "aware"));
        assertTrue(everyone.good.toString(), everyone.good.stream().anyMatch(l -> l.text.startsWith("High protein · 52 g")));
        assertTrue(everyone.good.toString(), everyone.good.stream().anyMatch(l -> l.text.startsWith("High fibre · 13 g")));
        CardFacts muscle = CardFacts.of(soya, person("", "muscle"));
        assertTrue(muscle.good.toString(), muscle.good.get(0).text.startsWith("High protein") && muscle.good.get(0).text.endsWith("your muscle goal"));
        CardFacts kidney = CardFacts.of(soya, person("kidney", "muscle"));
        for (CardFacts.Line l : kidney.good) assertFalse("protein is never praised as 'for you' with kidney disease: " + l,
            l.text.contains("protein") && l.personal);
    }

    @Test public void atMostThreeOfEachOnTheFaceAndTheRestBehindTheDetails() {
        GradeResult r = rated(560, 40, 12, 900, 2, 0);
        CardFacts f = CardFacts.of(r, person("hypertension,diabetes,cholesterol", "aware"));
        assertTrue(f.bad.size() <= CardFacts.MAX_EACH);
        assertTrue(f.good.size() <= CardFacts.MAX_EACH);
        assertEquals("every personal reason comes before the general ones", true,
            f.bad.stream().allMatch(l -> l.personal) || !f.moreBad.stream().anyMatch(l -> l.personal));
    }

    @Test public void linesAreShortenedWithoutLosingTheirFigures() {
        assertEquals("High protein · 52 g", CardFacts.compact("High protein: 52 g per 100 g"));
        assertEquals("Added sugar · 12 g of 15.6 g sugar", CardFacts.compact("Added sugar: 12 g of its 15.6 g sugar per 100 g"));
        assertEquals("No added sugar", CardFacts.compact("No added sugar, as the label declares"));
        assertEquals("Trans fat · 0.6 g", CardFacts.compact("Trans fat: 0.6 g per 100 g (best kept near zero)"));
    }

    /** Ingredient evidence never reaches the face of the card; it waits in its own menu. */
    @Test public void ingredientLinesGoToTheirOwnMenu() {
        String label = "Nutrition Information per 100 g\nEnergy 470 kcal\nTotal Sugars 30 g\nSaturated Fat 9 g\nSodium 200 mg\nProtein 7 g\n"
            + "Ingredients: Refined Wheat Flour (Maida), Sugar, Palm Oil, Cocoa Solids";
        Nutrition n = NutritionParser.parse(label);
        GradeResult r = GradeResult.graded(n, GradeResult.Source.LABEL_TEXT, ProductPage.of("Choco Cookies", 30.0, "com.zeptoconsumerapp"),
            null, Beverage.NOT_A_DRINK, label);
        CardFacts f = CardFacts.of(r, person("", "aware"));
        assertFalse(f.ingredientConcerns.toString(), f.ingredientConcerns.isEmpty());
        for (CardFacts.Line l : f.bad) assertFalse("ingredient line on the face: " + l, f.ingredientConcerns.contains(l.text));
    }

    /** Zepto's "Protein(52.5g), ..., Fat(0.8g)" with no energy: worked out by FSSAI's factors, not left unread. */
    @Test public void aLabelWithoutEnergyStillRatesFromItsMacronutrients() {
        Nutrition n = NutritionParser.parse("Nutrition Information\nNutrients per 100g Protein(52.5g), Carbohydrate(31.5g), Sugar(4.73g), "
            + "Added Sugar(0g), Fat(0.8g), Saturated Fat(0.2g), Trans Fat(0.02g), Cholesterol(0.1mg), Sodium(108mg)");
        assertNotNull(n);
        assertTrue(n.energyFromMacros);
        assertEquals(4 * 52.5 + 4 * 31.5 + 9 * 0.8, n.energyKcal, 0.001);
        GradeResult r = GradeResult.graded(n, GradeResult.Source.LABEL_TEXT, null);
        assertEquals("a 52 g-protein, low-sodium food is green", NutriGrade.Band.A, r.band);
        assertTrue(r.caveats.toString(), r.caveats.stream().anyMatch(c -> c.startsWith("Energy is not on the label")));
    }
}
