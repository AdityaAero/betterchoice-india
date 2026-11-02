package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Flipkart Minutes pages as the phone showed them on 27 September 2026 (Bikaji Classic Salted Chips,
 * Sunfeast Dark Fantasy Bourbon). Each item carries its left edge, because Flipkart's highlights and
 * specifications are two-column grids.
 */
public class FlipkartRegressionTest {
    static final String FLIPKART = "com.flipkart.android";
    static final int HEIGHT = 2392;

    /** text, top, left; clickable items are marked with a leading "!" */
    private static List<ScreenText> screen(Object... rows) {
        List<ScreenText> items = new ArrayList<>();
        for (int i = 0; i < rows.length; i += 3) {
            String t = (String) rows[i];
            boolean click = t.startsWith("!");
            items.add(new ScreenText(click ? t.substring(1) : t, (Integer) rows[i + 1], (Integer) rows[i + 2], click));
        }
        return items;
    }

    static List<ScreenText> bikajiTop() {
        return screen(
            "!Key Highlights", 289, 0, "BESTSELLER", 358, 83, "Expiry", 1269, 712, "26 Nov 2026", 1269, 817,
            "Bikaji", 1430, 44, "Classic Salted Chips (85 g)", 1491, 44, "!44% OFF, 50, ₹28", 1590, 44,
            "44% OFF", 1590, 44, "50", 1590, 340, "₹28", 1590, 431, "Selected Quantity:", 1744, 44, "85 g", 1744, 398,
            "Product highlights", 1880, 44, "Pack of", 1990, 44, "Brand", 1990, 573, "1", 2034, 44, "Bikaji", 2034, 573);
    }

    static List<ScreenText> bikajiDetails() {
        return screen(
            "Product highlights", 368, 44, "Pack of", 484, 44, "Brand", 484, 573, "1", 528, 44, "Bikaji", 528, 573,
            "Type", 816, 44, "Base Ingredient", 816, 573, "Chips", 860, 44, "Potato", 860, 573,
            "!All details", 1152, 0, "!Specifications", 1279, 33, "!Manufacturer info", 1279, 402,
            "Organic", 1435, 0, "Flavor", 1435, 540, "No", 1479, 0, "Salty", 1479, 540,
            "Food Preference", 1578, 0, "Container Type", 1578, 540, "Vegetarian", 1622, 0, "Pouch", 1622, 540,
            "Ingredients", 1721, 0,
            "Potatoes (65%), Edible Vegetable Oil (Palmolein Oil and/or Cottonseed Oil), Iodised Salt (1.5%)", 1765, 0,
            "Nutrient Content", 1919, 0, "Baked", 1919, 540, "NA", 1963, 0, "No", 1963, 540);
    }

    @Test public void theBrandLineJoinsTheName() {
        ProductPage page = ProductPage.detect(bikajiTop(), HEIGHT, FLIPKART);
        assertNotNull(page);
        assertEquals("Bikaji Classic Salted Chips (85 g)", page.name);
        assertEquals(28.0, page.price, 0.001);
        assertTrue(page.food);
    }

    @Test public void aGridPairsEachLabelWithItsOwnValue() {
        ProductPage page = ProductPage.detect(bikajiTop(), HEIGHT, FLIPKART);
        assertTrue(page.text, page.text.contains("Pack of\n1\nBrand\nBikaji"));
        ProductPage details = ProductPage.detect(bikajiDetails(), HEIGHT, FLIPKART, null, page);
        assertTrue(details.text.contains("Flavor\nSalty"));
        assertTrue(details.text.contains("Nutrient Content\nNA\nBaked\nNo"));
    }

    @Test public void ingredientsFromAllDetailsAreRead() {
        ProductPage page = ProductPage.detect(bikajiTop(), HEIGHT, FLIPKART);
        ProductPage details = ProductPage.detect(bikajiDetails(), HEIGHT, FLIPKART, null, page);
        assertEquals("Potatoes (65%), Edible Vegetable Oil (Palmolein Oil and/or Cottonseed Oil), Iodised Salt (1.5%)",
            IngredientFacts.section(details.text));
        // "Nutrient Content: NA" is no panel: nothing is invented.
        assertNull(NutritionParser.parse(details.text));
    }

    @Test public void theExpiryDateIsNeverTheName() {
        List<ScreenText> top = screen("Expiry", 1269, 712, "12 May 2027", 1269, 817,
            "Sunfeast Dark Fantasy", 1450, 44, "Bourbon Biscotti Biscuit (99 g)", 1515, 44, "₹14", 1600, 431,
            "Product highlights", 1880, 44);
        ProductPage page = ProductPage.detect(top, HEIGHT, FLIPKART);
        assertEquals("Sunfeast Dark Fantasy Bourbon Biscotti Biscuit (99 g)", page.name);
    }

    /** What the phone read from Parle Hide & Seek's nutrition photo on Flipkart Minutes, verbatim. */
    static final String HIDE_AND_SEEK_PHOTO = "Q Search for products\nNUTRITIONAL INFORMATION\n"
        + "SERVE SIZE: 125g PER SERVE AMOUNT PER SERVE %\nSERVINGS PER CÖNTAINER: 10 PER CONTRIBUTION\n"
        + "100 g TO RDA\nENERGY (kcal) 518 3.2\nPROTEIN (g) 4.4\nCARBOHYDRATE (9) 67.0\nTOTAL SUGARS (g) 31.5\n"
        + "ADDED SUGARS TO) 30.2 7.5\nTOTAL FAT (g) 25.8 4.8\nSATURÄTED FAT (9) 13.4 7.7\nTRANS FAT (g) 0 0\n"
        + "CHOLESTEROL (mg) 3.9\nSODIUM 192 1.2\n(mg)\nAPPROXVALUES RDA REQUJIREMENT FOR AVERAGE ADULT PER DAY (200 Kcal)\n"
        + "4.2 % 247 Expiry 13 Mar 2027\nPARLE\nHide & Seek Milano Choco Chip Biscotti Biscuit (120\ng)\n"
        + "53% OFF 420 56\nSelected Quantity: 120 g\n120 g\n(117/250g)\nApply for Card, EMI and Pay Later\nAdd to Cart";

    @Test public void aPhotoPanelsPer100ColumnIsReadAsSuch() {
        Nutrition n = NutritionParser.parse(HIDE_AND_SEEK_PHOTO);
        assertNotNull(n);
        assertTrue(n.basisStated);
        assertEquals(518, n.energyKcal, 0.001);
        assertEquals(31.5, n.sugarsG, 0.001);
        assertEquals(30.2, n.addedSugarsG, 0.001);
        assertEquals(25.8, n.totalFatG, 0.001);
        assertEquals(13.4, n.saturatedFatG, 0.001);
        assertEquals(192, n.sodiumMg, 0.001);
        assertEquals(4.4, n.proteinG, 0.001);
    }

    /** The same photo read again: this time the "100 g" header was lost altogether. */
    static final String HIDE_AND_SEEK_SECOND_READ = "Q Search for products\nNUTRITIONAL INFORMATION\n"
        + "SERVE SIZE: 125g PER SERVE AMOUNT PER SERVE %\nSERVINGS PER CÖNTAINER: 10 CONTRIBUTON\nTO RDA\n"
        + "ENERGY (kcal) 518 3.2\nPROTEIN (g) 4.4\nCARBOHYDRATE (9) 67.0\nTOTAL SUGARS (9) 31.5\nADDED SUGARS O) 30.2 75\n"
        + "TOTAL FAT (g) 25.8 4.8\nSATURÄTED FAT (9) 13.4 7.7\nTRANS FAT (g) 0\nCHOLESTEROL (mg) 3.9\nSODIUM (ng) 192 12\n"
        + "AAPPROXVALUES ROA REQUIREMENT FOR AVERAGE ADULT PER DAY (2000 Kcal)\n4.2 % 247 Expiry 13 Mar 2027\nPARLE\n"
        + "Hide & Seek Milano Choco Chip Biscotti Biscuit (120\ng)\n53% OFF 420 56\nSelected Quantity: 120 g\n120 g\n(117/250g)";

    @Test public void aLostHeaderDoesNotTurnThePer100ColumnIntoPerServe() {
        Nutrition n = NutritionParser.parse(HIDE_AND_SEEK_SECOND_READ);
        assertNotNull(n);
        assertEquals(518, n.energyKcal, 0.001);
        assertEquals(31.5, n.sugarsG, 0.001);
        assertEquals(13.4, n.saturatedFatG, 0.001);
        assertEquals(192, n.sodiumMg, 0.001);
        assertFalse("the basis was not read, and the card will say so", n.basisStated);
    }

    @Test public void commonPhotoMisreadsAreRepaired() {
        Nutrition n = NutritionParser.parse("Nutritional Information\n(Approx Values)\nPer 100g (% RDA Per Serve - 20g); "
            + "Energy 486 kcal (4.9%),\nProtein 6.8g, Carbohydrate 71.5g, Total Sugars 25.79,\n"
            + "Added Sugars 24.6g (9.8:%), Total Fat 19.59 (5.8%),\nSaturated Fat 9.8g (9.1%), Trans Fat (0ther Than Naturally\n"
            + "Occuring Trans Fat) 0.02g (0.2%), Cholesterol o.5mg,\nSodium 272.omg (2.7%).");
        assertEquals(25.7, n.sugarsG, 0.001);
        assertEquals(19.5, n.totalFatG, 0.001);
        assertEquals(272, n.sodiumMg, 0.001);
        assertEquals(9.8, n.saturatedFatG, 0.001);
        Nutrition q = NutritionParser.parse("Per 100 g\nEnergy 486 kcal\nTotal sugars 25.7 g\nSATURATED FAT9.8 q(9.1)\nSodium 272 mg");
        assertEquals(9.8, q.saturatedFatG, 0.001);
    }

    @Test public void aShopPagesTwoDecimalValueIsNotAMisreadUnit() {
        Nutrition n = NutritionParser.parse("Energy (kcal) 267, Protein (g) 6.89, Total Fat (g) 2.5, Trans Fat (g) 0.19, "
            + "Saturated Fat (g) 0.9, Total Sugars (g) 3.5, Sodium (mg) 450");
        assertEquals(6.89, n.proteinG, 0.001);
        assertEquals(0.19, n.transFatG, 0.001);
    }

    /** Sunfeast Dark Fantasy Bourbon's nutrition photo, as the phone read it (27 September). */
    @Test public void bourbonPhotoIsReadWhole() {
        Nutrition n = NutritionParser.parse("Q Search for products\nNUTRITIONAL\nINFORMATION\n"
            + "PER 100g (% RDA PER SERVE - 20 g)\nENERGY 479 kcal (4.8%)\nPROTEIN 5.4 g\nCARBOHYDRATE 73.5 g\n"
            + "TOTAL SUGARS 36.4 g\nADDED SsUGARS -34.2 g (13.6%)\nTOTAL FAT 18.2 g (5.4%)\nTRANS FAT\n"
            + "(oTHER THAN NATURALLY OCcURRING TRANS FAT) -- 0.1 g (1.0%)\nSATURATED FAT -.. 8.8 g (8.2%)\n"
            + "CHOLESTEROL 0.0 mg\nSODIUM --- 93.2 mg (0.9%)\nAPPROX. NO. OF SERVES\nPER PACK - 5.58\n"
            + "Sunfeast Dark Fantasy\nBourbon Biscotti Biscuit (99 g)\nSelected Quantity: 99 g\n(R35/250g)");
        assertEquals(479, n.energyKcal, 0.001);
        assertEquals(36.4, n.sugarsG, 0.001);
        assertEquals(34.2, n.addedSugarsG, 0.001);
        assertEquals(18.2, n.totalFatG, 0.001);
        assertEquals(8.8, n.saturatedFatG, 0.001);
        assertEquals(93.2, n.sodiumMg, 0.001);
        assertEquals(5.4, n.proteinG, 0.001);
    }

    /** Lay's India's Magic Masala nutrition photo as read on the page (27 September). */
    @Test public void laysOilBlendSaturatedFatTakesTheHigherValue() {
        Nutrition n = NutritionParser.parse("Q Search for products\nNUTRITIONAL INFORMATION AA SERVE SIZE20\n"
            + "Nutrients Per 100 g %RDA Per Serve\nEnergy 546 kcal 5%\nProtein 6.9 g\nCarbohydrate 54.9 g\n"
            + "Total Sugars 1.1g\nAdded Sugars 0.7g 0.3%\nNO ARTIFICIAL\nTotal Fat 33.2 g 10%\n&FLAVOuRS\n"
            + "14.9 g° 14%0\nSaturated Fat OR OR\nLays\n7.6 g 7%\nTrans Fat 0.1g 1%\nSodium 663 mg 7%");
        assertEquals(546, n.energyKcal, 0.001);
        assertEquals(1.1, n.sugarsG, 0.001);
        assertEquals(33.2, n.totalFatG, 0.001);
        assertEquals(14.9, n.saturatedFatG, 0.001);
        assertEquals(663, n.sodiumMg, 0.001);
    }

    /** Uncle Chipps' nutrition photo at normal size: the decimal points were lost. */
    @Test public void lostDecimalsAreRestoredWhereTheValueIsImpossible() {
        Nutrition n = NutritionParser.parse("NUTRITIONAL INFORMATION SERVE SIZE 20g-*\nNutrients Per 100 g 96RDA Per Serve\n"
            + "Energy 536 kcal 5%\nProtein 6.8g\nCarbohydrate 54.2g\nTotal Sugars 12g\nAdded Sugars 03g 0.1%\n"
            + "Total Fat 324g 10%\nSaturated Fat 146 g 13%°\nOR OR\n74g 7%\nTrans Fat 0.1g 19%\nSodium 897 mg 9%");
        assertNotNull(n);
        assertEquals(32.4, n.totalFatG, 0.001);
        assertEquals(14.6, n.saturatedFatG, 0.001);
        assertEquals(0.3, n.addedSugarsG, 0.001);
        assertEquals(897, n.sodiumMg, 0.001);
        assertTrue("a repaired reading asks for a closer look", n.ocrRepaired);
        assertFalse(NutritionParser.parse(HIDE_AND_SEEK_PHOTO.replace("SATURÄTED FAT (9)", "SATURATED FAT (g)")
            .replace("CARBOHYDRATE (9)", "CARBOHYDRATE (g)")).ocrRepaired);
    }

    @Test public void aUnitPriceIsNotABasis() {
        // Only per-serve figures and a serving size, with Flipkart's "(₹33/100g)" beneath the photo.
        Nutrition n = NutritionParser.parse("Nutrition information per serve (30 g)\nEnergy 150 kcal\nTotal sugars 6 g\n"
            + "Saturated fat 3 g\nSodium 120 mg\n(₹33/100g)");
        assertEquals(500, n.energyKcal, 0.001);
    }

    @Test public void britannia5050PhotoIsReadAsLabelled() {
        Nutrition n = NutritionParser.parse("NUTRITION INFORMATION\nServing Size: 12g (5 Biscuits)\nNo. of Servings per pack - About 3\n"
            + "Per 100g product | Per serve (%) RDA^\nEnergy-525kcal(3), Protein-7.7g, Carbohydrate-63.8g,\n"
            + "Total Sugars-9.8g, Added Sugars-7.5g(2),\nTotal Fat-26.5g(5), Saturated fatty acids-13g(7),\n"
            + "Mono unsaturated fatty acids-10.4g, Poly\nunsaturated fatty acids-2.7g, Trans fatty acids-0g(0),\n"
            + "Cholesterol-3.8mg, Sodium-1065mg(6).");
        assertEquals(525, n.energyKcal, 0.001);
        assertEquals(13, n.saturatedFatG, 0.001);
        assertEquals(1065, n.sodiumMg, 0.001);
        assertEquals(7.5, n.addedSugarsG, 0.001);
    }

    @Test public void zeptoIsNotRegrouped() {
        List<ScreenText> rows = screen("Protein", 1755, 174, "7g", 1755, 935, "Dietary Fiber", 1800, 174, "4g", 1800, 934);
        ProductPage page = ProductPage.detect(new ArrayList<>(rows) {{
            add(new ScreenText("Product Name is  Test Cookies Pack", 141, 317, false)); }}, HEIGHT, ZeptoRegressionTest.ZEPTO);
        assertTrue(page.text.contains("Protein\n7g\nDietary Fiber\n4g"));
    }
}
