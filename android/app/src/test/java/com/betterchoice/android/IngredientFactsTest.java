package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

/** What a declared ingredient list says, good and not, read only from the list itself. */
public class IngredientFactsTest {

    @Test public void refinedFlourAndSugarUpFrontAreSaidPlainly() {
        IngredientFacts f = IngredientFacts.of("Ingredients: Refined Wheat Flour (Maida), Sugar, Edible Vegetable Oil "
            + "(Palm), Invert Sugar Syrup, Raising Agents (503(ii), 500(ii)), Iodised Salt, Emulsifier (322)", false);
        assertTrue(f.concerns.toString(), f.concerns.contains("Refined flour (maida) is the main ingredient"));
        assertFalse(f.good.toString(), f.good.contains("No palm oil"));
        assertFalse(f.good.toString().contains("No added sugar"));
    }

    @Test public void wholeGrainFirstIsNamed() {
        IngredientFacts oats = IngredientFacts.of("Rolled Oats (62%), Whole Wheat Flour (Atta), Jaggery, Rice Bran Oil, Salt", false);
        assertTrue(oats.good.toString(), oats.good.contains("Oats are the main ingredient"));
        assertTrue("Rice bran oil, not palm", oats.good.contains("No palm oil"));
        assertFalse("Jaggery is added sugar", oats.good.toString().contains("No added sugar"));

        IngredientFacts ragi = IngredientFacts.of("Ragi Flour (40%), Whole Wheat Flour, Rice Bran Oil, Salt, Spices", false);
        assertTrue(ragi.good.toString(), ragi.good.contains("Ragi is the main ingredient"));
        assertFalse("Absence from a possibly partial list is not a sugar claim", ragi.good.contains("No added sugar or sweeteners"));
    }

    @Test public void atta_isWholeWheatButPlainWheatFlourIsNotClaimed() {
        assertTrue(IngredientFacts.of("Wheat Flour (Atta), Sugar, Palm Oil", false).good.contains("Whole wheat is the main ingredient"));
        assertFalse("\"Wheat flour\" alone is not claimed as whole grain",
            IngredientFacts.of("Wheat Flour, Sugar, Palm Oil", false).good.toString().contains("main ingredient"));
    }

    @Test public void drinksAreReadAsDrinks() {
        IngredientFacts juice = IngredientFacts.of("Water, Mango Pulp (20%), Sugar, Acidity Regulator (330)", true);
        assertFalse("Water is first in every drink; sugar-first is a food rule", juice.concerns.contains("Sugar is the main ingredient"));
        assertFalse(juice.good.toString(), juice.good.toString().contains("No added sugar"));
        IngredientFacts diet = IngredientFacts.of("Carbonated Water, Acidity Regulator (338), Sweeteners (951, 950), Caffeine", true);
        assertFalse(diet.good.toString(), diet.good.contains("No added sugar"));
        assertFalse("Sweetened, so not 'no sweeteners'", diet.good.contains("No added sugar or sweeteners"));
    }

    @Test public void nothingIsSaidWithoutARealList() {
        assertTrue(IngredientFacts.of(null, false).good.isEmpty());
        assertEquals("One plain ingredient is the least processed a food can be",
            java.util.Collections.singletonList("Just one ingredient: peanuts"),
            Priorities.positives(new java.util.ArrayList<>(IngredientFacts.of("Peanuts", false).good)).subList(0, 1));
        assertFalse("A cut-off fragment is not a list", IngredientFacts.of("Wheat Flour (40", false).read);
        assertEquals("Brackets keep an additive's numbers together", 3,
            IngredientFacts.parts("Flour, Emulsifier (322, 471), Salt").size());
    }

    @Test public void theListIsCutOutOfAWholeProductPage() {
        String page = "Product Details\nIngredients\nRefined Wheat Flour (Maida), Sugar, Edible Vegetable Oil (Palm)\n"
            + "Nutrition Information (Per 100 g)\nEnergy 480 kcal\nFSSAI Lic. 10012011000168";
        String list = IngredientFacts.section(page);
        assertEquals("Refined Wheat Flour (Maida), Sugar, Edible Vegetable Oil (Palm)", list);
        assertNull(IngredientFacts.section("Nutrition Information\nEnergy 480 kcal"));
    }
}
