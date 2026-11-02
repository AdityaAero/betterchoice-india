package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Juice has to be shown, not named. Found on a phone: Zepto lists Frooti as "Mango Fruit Juice", and
 * the card said "No added sugar, but fruit sugars still count" and "100% fruit". The label says
 * Water, Sugar, Mango Pulp (11.2%), and declares 14.2 g of added sugar per 100 ml.
 */
public class JuiceEvidenceTest {
    private static final String TITLE = "Frooti Mango Fruit Juice | Ready to Drink Beverage";
    private static final String INGREDIENTS = "Water, Sugar, Mango Pulp (11.2%), Acidity Regulators (INS 330, INS 331(iii)), "
        + "Nature-identical Flavouring Substances (Mango), Stabilizer (INS 466), Preservative (INS 211), "
        + "Antioxidant (INS 300), and Food Colour (INS 110)";
    private static final String NUTRITION = "Per 100 ml\nEnergy (kcal) 63.2 (RDA 3.2%), Protein (g) 0.0 (RDA 0.0%), Carbohydrate (g) 15.8, "
        + "Total Sugars (g) 15.6, Added Sugars (as Sucrose) (g) 14.2 (RDA 28.4%), Natural Fruit Sugars (g) 1.4, "
        + "Total Fat (g) 0.0 (RDA 0.0%), Sodium (mg) 26.1 (RDA 1.3%)";

    private static GradeResult frooti(boolean withIngredients, String nutrition) {
        List<ScreenText> s = new ArrayList<>();
        s.add(new ScreenText(TITLE, 650, false));
        s.add(new ScreenText("Net quantity: 1 pc (600 ml)", 680, false));
        s.add(new ScreenText("₹32", 730, false));
        s.add(new ScreenText("Product Details", 900, true));
        if (withIngredients) {
            s.add(new ScreenText("Ingredients", 1000, false));
            s.add(new ScreenText(INGREDIENTS, 1010, false));
        }
        s.add(new ScreenText("Nutrition Information", 1100, false));
        s.add(new ScreenText(nutrition, 1110, false));
        return ProductAnalyzer.analyze(s, 2400, "com.zeptoconsumerapp", p -> PackScan.EMPTY, null, null);
    }

    @Test public void frootiIsASugaredJuiceDrinkAndTheCardSaysSo() {
        GradeResult r = frooti(true, NUTRITION);
        assertEquals(GradeResult.State.GRADED, r.state);
        assertEquals(Beverage.Kind.JUICE_DRINK, r.drinkKind);
        assertEquals(NutriGrade.Band.C, r.band);
        String all = String.join(" | ", r.reasons);
        assertFalse("Never 'no added sugar' about a sugared drink: " + all, all.contains("No added sugar"));
        assertFalse("Never '100% fruit' from a shop's title: " + all, all.contains("100% fruit"));
        assertTrue(r.notes.toString(), r.notes.contains("Part juice, part sugar."));
        assertTrue(r.concerns.toString(), r.concerns.contains("Added sugar: 14.2 g of its 15.6 g sugar per 100 ml"));
        assertTrue(r.concerns.toString(), r.concerns.contains("Sugar is the main ingredient after water"));
        assertTrue(r.concerns.toString(), r.concerns.contains("Only 11.2% fruit (mango pulp)"));
    }

    @Test public void aDrinkWithNoStatedBasisIsRatedAsADrinkAndSaysSo() {
        GradeResult r = frooti(true, NUTRITION.replace("Per 100 ml\n", ""));
        assertEquals(GradeResult.State.GRADED, r.state);
        assertEquals(NutriGrade.Band.C, r.band);
        assertNotNull("scored as a drink", r.drinkKind);
        String all = r.caveats.toString();
        assertTrue(all, all.contains("per 100 g or per 100 ml"));
        assertTrue(all, all.contains("Rated as per 100 ml"));
    }

    @Test public void statedPer100MlAndZeroFatMeansZeroSaturatedFat() {
        GradeResult r = frooti(true, NUTRITION);
        assertEquals("100 ml", r.nutrition.unit());
        for (String line : r.concerns) assertFalse(line, line.contains("per 100 g"));
        assertEquals(0.0, r.nutrition.saturatedFatG, 0.0001);
        assertFalse(r.caveats.toString(), r.caveats.toString().contains("saturated fat"));
        assertEquals(14.2, r.nutrition.addedSugarsG, 0.0001);
    }

    /** Before the ingredients scroll into view: no claim either way, and no fruit credit. */
    @Test public void withoutEvidenceNothingIsClaimed() {
        String noAdded = NUTRITION.replace("Added Sugars (as Sucrose) (g) 14.2 (RDA 28.4%), ", "");
        GradeResult r = frooti(false, noAdded);
        assertEquals(Beverage.Kind.FRUIT_DRINK, r.drinkKind);
        String all = String.join(" | ", r.reasons);
        assertFalse(all, all.contains("No added sugar"));
        assertFalse(all, all.contains("% fruit"));
    }

    @Test public void theLabelsAddedSugarLineIsEnoughOnItsOwn() {
        assertEquals(Beverage.Kind.JUICE_DRINK, frooti(false, NUTRITION).drinkKind);
    }

    @Test public void realJuiceStillHasToBeShownAndIsBelievedWhenItIs() {
        assertEquals(Beverage.Kind.JUICE, Beverage.classify("Real 100% Orange Juice 1 L", "orange juice concentrate, water", true).kind);
        assertEquals(100, Beverage.classify("Tropicana 100% Apple Juice 1 L", "apple juice", true).fruitPercent);
        Beverage stated = Beverage.classify("Raw Pressery Orange Juice 250 ml", "No added sugar. Cold pressed orange juice", true);
        assertEquals(Beverage.Kind.JUICE, stated.kind);
        assertEquals("'No added sugar' alone does not prove a fruit share", 0, stated.fruitPercent);
        assertEquals(Beverage.Kind.FRUIT_DRINK, Beverage.classify("Paper Boat Mango Juice 200 ml", null, true).kind);
    }

    @Test public void theCardRefreshesWhenMoreOfTheLabelIsRead() {
        GradeResult before = frooti(false, NUTRITION.replace("Added Sugars (as Sucrose) (g) 14.2 (RDA 28.4%), ", ""));
        GradeResult after = frooti(true, NUTRITION);
        assertEquals(before.band, after.band);
        assertFalse("Same band, different facts: the card must update", after.sameAs(before));
    }
}
