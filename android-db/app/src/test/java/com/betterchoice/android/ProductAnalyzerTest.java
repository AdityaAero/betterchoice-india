package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductAnalyzerTest {
    static final int HEIGHT = 2400;
    static final String BLINKIT = "com.grofers.customerapp";

    static List<ScreenText> productPage(String name, String... extra) {
        List<ScreenText> items = new ArrayList<>(Arrays.asList(
            new ScreenText("Search for \"atta dal and more\"", 60, true),
            new ScreenText("Delivery in 8 minutes", 30, false),
            new ScreenText(name, 420, false),
            new ScreenText("₹20", 480, false),
            new ScreenText("ADD", 500, true),
            new ScreenText("Product Details", 900, true)));
        int top = 1000;
        for (String line : extra) items.add(new ScreenText(line, top += 40, false));
        return items;
    }

    /** Nothing readable in the pack photo. */
    static ProductAnalyzer.Pack noPack() { return page -> PackScan.EMPTY; }
    /** Text recognised in the pack photo, with no barcode in frame. */
    static ProductAnalyzer.Pack packText(String text) { return page -> new PackScan(text, null); }
    /** A database that knows nothing, by name or by number. */
    static ProductAnalyzer.Database noDatabase() { return database(null, null); }
    static ProductAnalyzer.Database database(Nutrition byName, Nutrition byNumber) {
        return database(byName, byNumber, null);
    }
    static ProductAnalyzer.Database database(Nutrition byName, Nutrition byNumber, Nutrition openData) {
        return new ProductAnalyzer.Database() {
            @Override public Nutrition find(ProductPage page) { return byName; }
            @Override public Nutrition byGtin(String gtin) { return byNumber; }
            @Override public Nutrition byGtinOpenData(String gtin) { return openData; }
        };
    }

    static final class Memory implements ProductAnalyzer.Memory {
        String skippedKey; Nutrition mine;
        @Override public boolean skipped(String key) { return key.equals(skippedKey); }
        @Override public Nutrition userDetails(String key) { return mine; }
    }

    @Test public void detectsTitlePriceAndFood() {
        ProductPage page = ProductPage.detect(productPage("Lay's India's Magic Masala Potato Chips 50 g"), HEIGHT, BLINKIT);
        assertNotNull(page);
        assertEquals("Lay's India's Magic Masala Potato Chips 50 g", page.name);
        assertEquals(20.0, page.price, 0.001);
        assertTrue(page.food);
    }

    @Test public void listingSearchAndPaymentScreensAreNotProductPages() {
        List<ScreenText> listing = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            listing.add(new ScreenText("Snack pack number " + i + " 50 g", 300 + i * 150, false));
            listing.add(new ScreenText("ADD", 320 + i * 150, true));
        }
        assertNull(ProductPage.detect(listing, HEIGHT, BLINKIT));
        List<ScreenText> payment = productPage("Amul Taaza Toned Milk 500 ml", "Proceed to Pay");
        assertNull(ProductPage.detect(payment, HEIGHT, BLINKIT));
        assertNull(ProductPage.detect(new ArrayList<>(), HEIGHT, BLINKIT));
    }

    @Test public void cadburyTitleIsNotMistakenForAnAddButton() {
        ProductPage page = ProductPage.detect(productPage("Cadbury Dairy Milk Chocolate Bar 50 g"), HEIGHT, BLINKIT);
        assertNotNull(page);
        assertEquals("Cadbury Dairy Milk Chocolate Bar 50 g", page.name);
    }

    @Test public void nutritionOnThePageWinsFirst() {
        List<ScreenText> screen = productPage("Lay's India's Magic Masala Potato Chips 50 g", NutritionParserTest.CHIPS_TABLE.split("\n"));
        int[] photoCalls = {0};
        GradeResult r = ProductAnalyzer.analyze(screen, HEIGHT, BLINKIT,
            page -> { photoCalls[0]++; return PackScan.EMPTY; }, noDatabase(), new Memory());
        assertEquals(GradeResult.State.GRADED, r.state);
        assertEquals(GradeResult.Source.LABEL_TEXT, r.source);
        assertEquals(NutriGrade.Band.C, r.band);
        assertEquals("Label text must avoid a screenshot", 0, photoCalls[0]);
    }

    @Test public void packPhotoIsSecond() {
        GradeResult r = ProductAnalyzer.analyze(productPage("Quaker Rolled Oats 1 kg"), HEIGHT, BLINKIT,
            packText("NUTRITION INFORMATION per 100g Energy 379 kcal Total Sugars 1 g Saturated Fat 1.2 g Sodium 6 mg Dietary Fibre 10 g Protein 13 g"),
            new ProductAnalyzer.Database() {
                @Override public Nutrition find(ProductPage page) { fail("Database must not be queried when the photo is readable"); return null; }
                @Override public Nutrition byGtin(String gtin) { return null; }
                @Override public Nutrition byGtinOpenData(String gtin) { return null; }
            }, new Memory());
        assertEquals(GradeResult.Source.PACK_PHOTO, r.source);
        assertEquals(NutriGrade.Band.A, r.band);
    }

    @Test public void rememberedPageNutritionStillCollectsLaterIngredientPhoto() {
        ProductAnalyzer.Memory memory = new ProductAnalyzer.Memory() {
            @Override public boolean skipped(String key) { return false; }
            @Override public Nutrition userDetails(String key) { return null; }
            @Override public String nutritionText(String key, String text) {
                return text + "\n" + NutritionParserTest.CHIPS_TABLE;
            }
        };
        GradeResult r = ProductAnalyzer.analyze(productPage("Salted Potato Chips 50 g"), HEIGHT, BLINKIT,
            packText("Ingredients: Potato, Palm Oil, Milk Solids, Soy.\nAllergen Advice: Contains Milk and Soy."),
            noDatabase(), memory);
        assertEquals(GradeResult.Source.LABEL_TEXT, r.source);
        assertEquals(NutriGrade.Band.C, r.band);
        assertTrue(r.labelText.contains("Milk Solids"));
        assertNotNull(r.ingredients);
        assertTrue(r.ingredients.read);
        assertTrue(r.labelText.contains("Allergen Advice"));
    }

    @Test public void userDetailsThenDatabaseThenAskTheUser() {
        Memory memory = new Memory();
        Nutrition db = NutriGradeTest.food(400, 10.0, 3.0, 300.0, 2.0, 12.0);
        GradeResult fromDb = ProductAnalyzer.analyze(productPage("Masala Oats Cup 40 g"), HEIGHT, BLINKIT, noPack(), database(db, null), memory);
        assertEquals(GradeResult.Source.DATABASE, fromDb.source);
        // Nutri-Score 2023 scores these values 11 (D): red. See NutriGradeTest.moderateSnackIsYellow.
        assertEquals(NutriGrade.Band.C, fromDb.band);

        memory.mine = NutriGradeTest.food(379, 1.0, 1.2, 6.0, 10.0, 13.0);
        GradeResult mine = ProductAnalyzer.analyze(productPage("Masala Oats Cup 40 g"), HEIGHT, BLINKIT, noPack(), database(db, null), memory);
        assertEquals(GradeResult.Source.USER, mine.source);

        GradeResult unknown = ProductAnalyzer.analyze(productPage("Masala Oats Cup 40 g"), HEIGHT, BLINKIT, noPack(), noDatabase(), new Memory());
        assertEquals(GradeResult.State.NEEDS_INPUT, unknown.state);
        assertNull(unknown.band);
    }

    @Test public void skippedAndNonFoodItemsStayNeutral() {
        Memory memory = new Memory();
        ProductPage page = ProductPage.detect(productPage("Masala Oats Cup 40 g"), HEIGHT, BLINKIT);
        memory.skippedKey = page.key();
        assertEquals(GradeResult.State.SKIPPED,
            ProductAnalyzer.analyze(productPage("Masala Oats Cup 40 g"), HEIGHT, BLINKIT, packText("x"), noDatabase(), memory).state);
        assertEquals(GradeResult.State.NOT_FOOD,
            ProductAnalyzer.analyze(productPage("Dove Intense Repair Shampoo 180 ml"), HEIGHT, BLINKIT, noPack(), noDatabase(), new Memory()).state);
        assertEquals(GradeResult.State.NOT_PRODUCT,
            ProductAnalyzer.analyze(List.of(new ScreenText("Home", 10, true)), HEIGHT, BLINKIT, noPack(), noDatabase(), new Memory()).state);
    }

    @Test public void nameAloneNeverProducesAGrade() {
        GradeResult r = ProductAnalyzer.analyze(productPage("Coca-Cola Soft Drink 750 ml"), HEIGHT, BLINKIT, noPack(), noDatabase(), new Memory());
        assertEquals(GradeResult.State.NEEDS_INPUT, r.state);
    }

    // --- Barcode on the pack -------------------------------------------------------------------

    private static final String MAGGI = "08901058000450";

    @Test public void theBarcodeIdentifiesThePackBeforeAnyNameMatching() {
        Nutrition byNumber = NutriGradeTest.food(379, 1.0, 1.2, 6.0, 10.0, 13.0);
        Nutrition byName = NutriGradeTest.food(500, 30.0, 12.0, 700.0, 1.0, 5.0);
        GradeResult r = ProductAnalyzer.analyze(productPage("Maggi 2-Minute Noodles 70 g"), HEIGHT, BLINKIT,
            page -> new PackScan(null, MAGGI), database(byName, byNumber), new Memory());
        assertEquals(GradeResult.Source.BARCODE, r.source);
        assertEquals("The pack's own number beats a name that could match a different size",
            NutriGrade.Band.A, r.band);
        assertEquals(MAGGI, r.gtin);
    }

    @Test public void anUnknownBarcodeFallsThroughToReadingTheLabel() {
        GradeResult r = ProductAnalyzer.analyze(productPage("Quaker Rolled Oats 1 kg"), HEIGHT, BLINKIT,
            page -> new PackScan("NUTRITION INFORMATION per 100g Energy 379 kcal Total Sugars 1 g Saturated Fat 1.2 g Sodium 6 mg Dietary Fibre 10 g Protein 13 g", MAGGI),
            noDatabase(), new Memory());
        assertEquals(GradeResult.Source.PACK_PHOTO, r.source);
        assertEquals("The number is still worth keeping for the contribution", MAGGI, r.gtin);
    }

    @Test public void theOpenDatabaseAnswersWhenOursHasNotLearnedThePackYet() {
        Nutrition open = NutriGradeTest.food(379, 1.0, 1.2, 6.0, 10.0, 13.0);
        GradeResult r = ProductAnalyzer.analyze(productPage("Quaker Rolled Oats 1 kg"), HEIGHT, BLINKIT,
            page -> new PackScan(null, MAGGI), database(null, null, open), new Memory());
        assertEquals(GradeResult.Source.OPEN_FOOD_FACTS, r.source);
        assertEquals(NutriGrade.Band.A, r.band);
        assertEquals(MAGGI, r.gtin);
    }

    @Test public void ourOwnDatabaseIsPreferredOverTheOpenOne() {
        Nutrition ours = NutriGradeTest.food(379, 1.0, 1.2, 6.0, 10.0, 13.0);
        Nutrition open = NutriGradeTest.food(500, 30.0, 12.0, 700.0, 1.0, 5.0);
        GradeResult r = ProductAnalyzer.analyze(productPage("Quaker Rolled Oats 1 kg"), HEIGHT, BLINKIT,
            page -> new PackScan(null, MAGGI), database(null, ours, open), new Memory());
        assertEquals(GradeResult.Source.BARCODE, r.source);
        assertEquals(NutriGrade.Band.A, r.band);
    }

    @Test public void aBarcodeAloneIsNeverEnoughForAGrade() {
        GradeResult r = ProductAnalyzer.analyze(productPage("Coca-Cola Soft Drink 750 ml"), HEIGHT, BLINKIT,
            page -> new PackScan(null, MAGGI), noDatabase(), new Memory());
        assertEquals(GradeResult.State.NEEDS_INPUT, r.state);
        assertNull(r.band);
        assertEquals(MAGGI, r.gtin);
    }
}
