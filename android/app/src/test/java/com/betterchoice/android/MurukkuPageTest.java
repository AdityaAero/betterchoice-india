package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A real Swiggy product page where the nutrition table is on-screen text, captured from a phone on
 * 21 September 2026. The bubble stayed neutral on this screen, so this pins down which step failed.
 */
public class MurukkuPageTest {
    private static final int HEIGHT = 2392;
    private static final String SWIGGY = "in.swiggy.android";

    /** Laid out in the order and screen positions the accessibility tree reports them. */
    private static List<ScreenText> screen() {
        List<ScreenText> items = new ArrayList<>();
        int y = 0;
        items.add(new ScreenText("Let's Try Butter Murukku | Made with Rice Flour", y = 170, false));
        items.add(new ScreenText("₹89", y += 10, false));
        items.add(new ScreenText("₹120", y += 10, false));
        items.add(new ScreenText("Cuisine Type", y = 330, false));
        items.add(new ScreenText("Indian", y += 4, false));
        items.add(new ScreenText("Speciality", y = 410, false));
        items.add(new ScreenText("No Palm Oil, No Cholesterol, No Trans Fat", y += 4, false));
        items.add(new ScreenText("View Less", y = 535, true));
        items.add(new ScreenText("Nutritional info", y = 720, false));
        items.add(new ScreenText("SUMMARY", y = 835, false));
        items.add(new ScreenText("This product has", y = 910, false));
        items.add(new ScreenText("568 calories", y += 2, false));
        items.add(new ScreenText("10.6g protein", y += 2, false));
        items.add(new ScreenText("56.8g carbs", y = 990, false));
        items.add(new ScreenText("36.73g fats", y += 2, false));
        items.add(new ScreenText("in every 100g serve.", y += 2, false));
        items.add(new ScreenText("Calories", y = 1120, false));
        items.add(new ScreenText("568kcal", y, false));
        items.add(new ScreenText("Protein", y = 1205, false));
        items.add(new ScreenText("10.6g", y, false));
        items.add(new ScreenText("Carbohydrates", y = 1290, false));
        items.add(new ScreenText("56.8g", y, false));
        items.add(new ScreenText("Total Sugar", y = 1360, false));
        items.add(new ScreenText("0.7g", y, false));
        items.add(new ScreenText("Added Sugar", y = 1420, false));
        items.add(new ScreenText("0g", y, false));
        items.add(new ScreenText("Fats", y = 1475, false));
        items.add(new ScreenText("36.73g", y, false));
        items.add(new ScreenText("Saturated Fat", y = 1540, false));
        items.add(new ScreenText("17.5g", y, false));
        items.add(new ScreenText("Trans Fat", y = 1605, false));
        items.add(new ScreenText("0.1g", y, false));
        items.add(new ScreenText("Others", y = 1670, false));
        items.add(new ScreenText("Cholesterol", y = 1740, false));
        items.add(new ScreenText("1mg", y, false));
        items.add(new ScreenText("Sodium", y = 1805, false));
        items.add(new ScreenText("760mg", y, false));
        items.add(new ScreenText("View Less", y = 1930, true));
        items.add(new ScreenText("3", y = 2150, false));
        items.add(new ScreenText("Add to Cart", y += 10, true));
        return items;
    }

    @Test public void thePageIsRecognisedAsAFoodProduct() {
        StringBuilder notes = new StringBuilder();
        ProductPage page = ProductPage.detect(screen(), HEIGHT, SWIGGY, notes);
        assertNotNull("Page not detected: " + notes, page);
        assertTrue("Should be food: " + notes, page.food);
        assertEquals("Let's Try Butter Murukku | Made with Rice Flour", page.name);
        assertEquals(89.0, page.price, 0.01);
    }

    /**
     * The summary row reads "10.6g protein, 56.8g carbs", with each value before its label. Reading
     * it label-first walked past "protein" and returned the carbohydrate figure — which flatters the
     * rating, because protein counts in a product's favour.
     */
    @Test public void aValueWrittenBeforeItsLabelIsReadCorrectly() {
        // Sugar and sodium are present because one nutrient alone is never enough to grade at all.
        Nutrition n = NutritionParser.parse("This product has\n568 calories\n10.6g protein\n56.8g carbs\n"
            + "36.73g fats\nTotal Sugar\n0.7g\nSaturated Fat\n17.5g\nSodium\n760mg");
        assertNotNull(n);
        assertEquals("Not the 56.8 g of carbohydrate", 10.6, n.proteinG, 0.01);
        assertEquals(568, n.energyKcal, 0.01);
    }

    /** The mirror of the same trap: on one line, a label's own value must win. */
    @Test public void aLabelsOwnValueWinsOverThePrecedingNumber() {
        Nutrition n = NutritionParser.parse(
            "Per 100 g Energy 379 kcal Total Sugars 1 g Saturated Fat 1.2 g Sodium 6 mg Dietary Fibre 10 g Protein 13 g");
        assertNotNull(n);
        assertEquals("Protein is 13, not fibre's 10", 13.0, n.proteinG, 0.01);
        assertEquals(10.0, n.fibreG, 0.01);
    }

    @Test public void theNutritionTableOnThisPageIsRead() {
        ProductPage page = ProductPage.detect(screen(), HEIGHT, SWIGGY, null);
        assertNotNull(page);
        Nutrition n = NutritionParser.parse(page.text);
        System.out.println("PARSED: " + (n == null ? "NULL" : String.format(
            "energy=%s sugars=%s satfat=%s sodium=%s protein=%s fibre=%s",
            n.energyKcal, n.sugarsG, n.saturatedFatG, n.sodiumMg, n.proteinG, n.fibreG)));
        assertNotNull("The whole table is on screen as text; it must parse", n);
        assertEquals(568, n.energyKcal, 0.01);
        assertEquals("Sodium is printed as 760mg", 760, n.sodiumMg, 0.01);
        assertEquals(17.5, n.saturatedFatG, 0.01);
        assertEquals(0.7, n.sugarsG, 0.01);
        assertEquals(10.6, n.proteinG, 0.01);
    }

    @Test public void theWholeCascadeGradesIt() {
        GradeResult result = ProductAnalyzer.analyze(screen(), HEIGHT, SWIGGY,
            p -> PackScan.EMPTY, null, null);
        System.out.println("RESULT: " + result.state + " band=" + result.band);
        assertEquals(GradeResult.State.GRADED, result.state);
    }
}
