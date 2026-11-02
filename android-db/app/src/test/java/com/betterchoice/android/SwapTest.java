package com.betterchoice.android;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Healthier swaps: the curated catalogue, every rule that decides a swap, the live check on the
 * shop's own screen, and the 2x price rule. Robolectric only because the catalogue is JSON.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class SwapTest {

    private static SwapCatalogue real() throws Exception {
        for (String path : new String[] {"src/main/assets/" + SwapCatalogue.ASSET, "app/src/main/assets/" + SwapCatalogue.ASSET}) {
            File file = new File(path);
            if (file.exists()) return SwapCatalogue.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        }
        throw new AssertionError("catalogue asset not found");
    }

    private static Nutrition food(double kcal, double sugar, double sat, double sodium, double protein, Double fibre) {
        Nutrition n = new Nutrition();
        n.energyKcal = kcal; n.sugarsG = sugar; n.saturatedFatG = sat; n.sodiumMg = sodium; n.proteinG = protein; n.fibreG = fibre;
        return n;
    }

    // A small catalogue with known answers, so each rule is tested on its own.
    private static final String TEST_CATALOGUE = "{\"version\":\"t\",\"groups\":[" +
        "{\"id\":\"salty_snacks\",\"name\":\"Salty crunchy snacks\",\"branch\":\"food\",\"search\":\"makhana\"," +
        "\"focus\":[\"sodium\",\"saturated_fat\",\"energy\"],\"occasions\":[\"snack\",\"party\"]," +
        "\"match\":[\"chips\",\"makhana\",\"roasted chana\",\"namkeen\"],\"exclude\":[\"chocolate\"]}],\"products\":[" +
        product("a", "Crunch Co", "Roasted Makhana Pudina", 60, 360, 0.5, 3.0, 190, 7, 3.7, "verified", "fox nuts, salt", "") + "," +
        product("b", "Chana House", "Roasted Chana Plain", 150, 300, 1.0, 0.5, 30, 19, 26, "draft", "bengal gram", "") + "," +
        product("c", "Crunch Co", "Roasted Makhana Peri Peri", 60, 380, 1.0, 3.5, 250, 7, 3.0, "verified", "fox nuts", "") + "," +
        product("d", "Sweet Crisp", "Honey Makhana", 60, 420, 28, 3.0, 60, 6, 2.0, "verified", "fox nuts, sugar", "") + "," +
        product("e", "Nutty", "Roasted Chana Masala", 150, 380, 2.0, 1.0, 400, 18, 15, "verified", "bengal gram, peanut oil", "peanut") + "," +
        product("f", "Salty Pro", "Baked Chips Salted", 60, 430, 2.0, 2.0, 700, 7, 5.0, "verified", "potato, salt", "") +
        "]}";

    private static String product(String id, String brand, String name, double pack, double kcal, double sugar, double sat,
                                  double sodium, double protein, double fibre, String status, String ingredients, String contains) {
        return "{\"id\":\"" + id + "\",\"brand\":\"" + brand + "\",\"name\":\"" + name + "\",\"group\":\"salty_snacks\"," +
            "\"pack\":" + pack + ",\"unit\":\"g\",\"beverage\":false,\"per100\":{\"energy_kcal\":" + kcal + ",\"sugars_g\":" + sugar +
            ",\"saturated_fat_g\":" + sat + ",\"sodium_mg\":" + sodium + ",\"protein_g\":" + protein + ",\"fibre_g\":" + fibre + "}," +
            "\"ingredients\":\"" + ingredients + "\",\"contains\":[" + (contains.isEmpty() ? "" : "\"" + contains + "\"") +
            "],\"may_contain\":[],\"status\":\"" + status + "\",\"nss\":false}";
    }

    /** Potato chips, the typical red product: 540 kcal, 14 g saturated fat, 650 mg sodium. */
    private static Nutrition chips() {
        Nutrition n = food(540, 2.0, 14, 650, 6.5, 3.0);
        n.netQuantity = 52.0;
        return n;
    }

    // --- The catalogue the curation assistant drafted ----------------------------------------------

    @Test public void theDraftCatalogueLoadsAndEveryRecordIsLabelledAndSourced() throws Exception {
        SwapCatalogue catalogue = real();
        // The original eleven groups plus the 2025 Open Food Facts India build's twenty; every one sits in an
        // FSSAI category and is switched on by a "What do you buy" answer (or, for staples, always).
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (SwapCatalogue.Group g : catalogue.groups) {
            assertTrue("duplicate group " + g.id, ids.add(g.id));
            assertNotNull(g.id + " has no FSSAI category", FoodCategory.fromSwapGroup(g.id));
            assertTrue(g.id + " has no buy option", GoalComparisons.buyCode(g.id) != null || "flours_grains".equals(g.id));
        }
        assertTrue(ids.containsAll(java.util.Arrays.asList("flavoured_milk", "soft_drinks", "fruit_drinks", "spreads", "ice_cream",
            "breakfast_cereals", "chocolate", "biscuits", "instant_noodles", "nuts_seeds", "salty_snacks")));
        assertTrue("catalogue has " + catalogue.products.size(), catalogue.products.size() >= 50);
        for (SwapCatalogue.Product p : catalogue.products) {
            assertNotNull(p.id, catalogue.groups.stream().filter(g -> g.id.equals(p.groupId)).findFirst().orElse(null));
            assertEquals("Nothing is verified until the nutritionist checks the pack: " + p.id, "draft", p.status);
            assertNotNull("Every draft cites its source: " + p.id, p.sourceUrl);
            assertNotNull(p.per100.sugarsG); assertNotNull(p.per100.saturatedFatG); assertNotNull(p.per100.sodiumMg);
        }
    }

    /**
     * The catalogue holds products of every colour, so a red or yellow product can be compared like for like; the app
     * grades each one itself with the table it uses for it (nuts and nut butters on the 2023 fats table) and decides
     * on the phone which are better for the product on screen.
     */
    @Test public void theCatalogueHoldsEveryColourAndTheAppGradesEachOne() throws Exception {
        SwapCatalogue catalogue = real();
        java.util.Map<NutriGrade.Band, Integer> bands = new java.util.EnumMap<>(NutriGrade.Band.class);
        for (SwapCatalogue.Product p : catalogue.products) {
            Beverage identity = Beverage.classify(p.displayName(), p.ingredients, p.per100.beverage);
            if (p.sweetener) identity = identity.withSweetener();
            NutriGrade.Result grade = NutriGrade.grade(p.per100, identity, FoodCategory.kind(p.displayName(), p.groupId));
            assertNotNull(p.displayName(), grade.band);
            bands.merge(grade.band, 1, Integer::sum);
        }
        for (NutriGrade.Band band : NutriGrade.Band.values()) assertTrue(bands.toString(), bands.getOrDefault(band, 0) >= 100);
    }

    @Test public void theProductOnScreenIsPlacedInTheRightGroup() throws Exception {
        SwapCatalogue c = real();
        assertEquals("salty_snacks", c.groupFor("Lay's India's Magic Masala Potato Chips", false).id);
        assertEquals("chocolate", c.groupFor("Cadbury Dairy Milk Chocolate Bar", false).id);
        assertEquals("biscuits", c.groupFor("Sunfeast Dark Fantasy Vanilla Sandwich Creme Biscuits", false).id);
        assertEquals("instant_noodles", c.groupFor("Maggi 2-Minute Masala Noodles", false).id);
        assertEquals("breakfast_cereals", c.groupFor("Kellogg's Chocos", false).id);
        assertEquals("spreads", c.groupFor("Nutella Hazelnut Spread with Cocoa", false).id);
        assertEquals("nuts_seeds", c.groupFor("Classic Salted Peanuts", false).id);
        assertEquals("ice_cream", c.groupFor("Kwality Wall's Cornetto Double Chocolate", false).id);
        assertEquals("soft_drinks", c.groupFor("Coca-Cola Original Taste Soft Drink", true).id);
        assertEquals("fruit_drinks", c.groupFor("Maaza Mango Drink", true).id);
        assertEquals("flavoured_milk", c.groupFor("Amul Kool Kesar Flavoured Milk", true).id);
        assertNull("A drink is never placed in a food group", c.groupFor("Maaza Mango Drink", false));
        assertNull("Nothing curated for this", c.groupFor("Tata Salt Iodised", false));
    }

    // --- The rules -----------------------------------------------------------------------------------

    @Test public void onlyBetterBandsWithARealImprovementAndNoNewRedFlag() throws Exception {
        SwapCatalogue c = SwapCatalogue.parse(TEST_CATALOGUE);
        SwapEngine.Outcome out = SwapEngine.recommend(c, "Classic Salted Potato Chips", chips(), NutriGrade.Band.C,
            false, SafetyProfile.unknown(), Occasion.SNACK, true);
        assertEquals("salty_snacks", out.group.id);
        List<String> names = new ArrayList<>();
        for (SwapEngine.Swap s : out.swaps) names.add(s.product.name);
        assertFalse("Honey makhana is high in sugar, which the chips were not", names.contains("Honey Makhana"));
        assertFalse("Baked chips with more sodium than the chips are not the healthier salty snack, even with less fat",
            names.contains("Baked Chips Salted"));
        assertTrue(names.toString(), names.contains("Roasted Chana Plain"));
        assertTrue(names.toString(), names.contains("Roasted Chana Masala"));
        for (SwapEngine.Swap s : out.swaps) {
            assertTrue(s.band.ordinal() < NutriGrade.Band.C.ordinal());
            Set<String> high = NutriGrade.highFlags(s.product.per100, false);
            assertTrue(s.product.name + " " + high, NutriGrade.highFlags(chips(), false).containsAll(high));
        }
    }

    @Test public void oneSwapPerBrandAndNeverMoreThanThree() throws Exception {
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(TEST_CATALOGUE), "Potato Chips", chips(),
            NutriGrade.Band.C, false, SafetyProfile.unknown(), Occasion.SNACK, true);
        assertTrue(out.swaps.size() <= SwapEngine.MAX_SWAPS);
        long crunchCo = out.swaps.stream().filter(s -> s.product.brand.equals("Crunch Co")).count();
        assertTrue("Two makhana flavours from one brand are one choice, not two", crunchCo <= 1);
    }

    @Test public void draftsAreNeverShownOutsideTestBuilds() throws Exception {
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(TEST_CATALOGUE), "Potato Chips", chips(),
            NutriGrade.Band.C, false, SafetyProfile.unknown(), Occasion.SNACK, false);
        for (SwapEngine.Swap s : out.swaps) assertFalse(s.product.name, s.draft());
    }

    /** The shopper's allergies are checked on the phone; the catalogue never learns them. */
    @Test public void aSwapContainingTheShoppersAllergenIsLeftOutAndCounted() throws Exception {
        SafetyProfile peanut = SafetyProfile.of(SafetyProfile.Allergies.KNOWN, Collections.singletonList(Allergen.PEANUT),
            SafetyProfile.Severity.SEVERE, "");
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(TEST_CATALOGUE), "Potato Chips", chips(),
            NutriGrade.Band.C, false, peanut, Occasion.SNACK, true);
        for (SwapEngine.Swap s : out.swaps) assertNotEquals("Roasted Chana Masala", s.product.name);
        assertTrue(out.hiddenForSafety >= 1);
    }

    @Test public void anAlreadyGoodProductGetsNoSwaps() throws Exception {
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(TEST_CATALOGUE), "Roasted Makhana Classic",
            food(350, 0.5, 2.5, 150, 8, 4.0), NutriGrade.Band.A, false, SafetyProfile.unknown(), Occasion.SNACK, true);
        assertTrue(out.swaps.isEmpty());
    }

    @Test public void theWhyLineNamesTheBiggestRealChange() throws Exception {
        SwapCatalogue.Group g = SwapCatalogue.parse(TEST_CATALOGUE).groups.get(0);
        // Chips' saturated fat (14 g) is their worst score; 0.5 g is the biggest real change.
        assertEquals("96% less saturated fat", SwapEngine.why(g, chips(), food(300, 1, 0.5, 30, 19, 26.0), false));
        assertEquals("Half the saturated fat", SwapEngine.why(g, chips(), food(540, 2, 7, 640, 6, 3.0), false));
    }

    /** On the real draft catalogue: a sugary cola gets far less sugary swaps, never another sugary one. */
    @Test public void aColaGetsSwapsWithFarLessSugar() throws Exception {
        Nutrition cola = new Nutrition();
        cola.energyKcal = 44.0; cola.sugarsG = 10.9; cola.saturatedFatG = 0.0; cola.sodiumMg = 7.0; cola.beverage = true;
        cola.netQuantity = 750.0;
        SwapEngine.Outcome out = SwapEngine.recommend(real(), "Coca-Cola Original Taste Soft Drink", cola, NutriGrade.Band.C,
            true, SafetyProfile.unknown(), Occasion.PARTY, true);
        assertFalse(out.swaps.isEmpty());
        for (SwapEngine.Swap s : out.swaps) {
            assertTrue(s.product.name, s.product.per100.sugarsG <= 10.9 * 0.25);
            assertFalse(s.product.name, NutriGrade.highFlags(s.product.per100, true).contains("sugars"));
            if (s.product.sweetener) assertFalse("A sweetened drink is never 'no sweeteners'",
                s.ingredients.good.contains("No added sugar or sweeteners"));
        }
    }

    @Test public void whenNothingIsBetterTheCardSaysSoInsteadOfGuessing() throws Exception {
        // A fixed catalogue, so the test does not depend on what the live one happens to hold: the only other biscuit
        // has 14% less sugar and the same fat, which is no real difference (FSSAI's 30%), so nothing is offered.
        String slight = "{\"version\":\"t\",\"groups\":[{\"id\":\"biscuits\",\"name\":\"Biscuits & cookies\",\"branch\":\"food\","
            + "\"focus\":[\"sugars\",\"saturated_fat\"],\"occasions\":[\"snack\"],\"match\":[\"biscuit\",\"biscuits\",\"cookies\",\"dark fantasy\"],"
            + "\"exclude\":[]}],\"products\":[{\"id\":\"s\",\"brand\":\"Sweet Bake\",\"name\":\"Choco Cream Biscuits\",\"group\":\"biscuits\","
            + "\"pack\":100,\"unit\":\"g\",\"beverage\":false,\"per100\":{\"energy_kcal\":500,\"sugars_g\":31,\"saturated_fat_g\":11,"
            + "\"sodium_mg\":180,\"protein_g\":6,\"fibre_g\":2},\"ingredients\":\"Refined wheat flour, sugar, palm oil\",\"contains\":[],"
            + "\"may_contain\":[],\"status\":\"draft\"}]}";
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(slight), "Dark Fantasy Choco Fills", creamBiscuit(),
            NutriGrade.Band.C, 'E', false, SafetyProfile.unknown(), Occasion.SNACK, true);
        assertEquals("biscuits", out.group.id);
        assertTrue(out.swaps.isEmpty());
        assertEquals("We know of no biscuits & cookies that is better than this one.", out.emptyReason);
    }

    // --- Biscuits: green first, then the best of the rest, each with its own colour -----------------------

    private static final String BISCUITS = "{\"version\":\"t\",\"groups\":[{\"id\":\"biscuits\",\"name\":\"Biscuits & cookies\"," +
        "\"branch\":\"food\",\"focus\":[\"sugars\",\"saturated_fat\"],\"occasions\":[\"snack\"]," +
        "\"match\":[\"biscuit\",\"biscuits\",\"cookies\",\"crackers\",\"marie\",\"dark fantasy\"],\"exclude\":[]}],\"products\":[" +
        "{\"id\":\"m\",\"brand\":\"Plain Bake\",\"name\":\"Marie Biscuits\",\"group\":\"biscuits\",\"pack\":250,\"unit\":\"g\"," +
        "\"beverage\":false,\"per100\":{\"energy_kcal\":445,\"sugars_g\":21.9,\"saturated_fat_g\":5.2,\"sodium_mg\":319," +
        "\"protein_g\":8,\"fibre_g\":2}," +
        "\"ingredients\":\"Refined wheat flour (maida), sugar, edible vegetable oil (palm), invert syrup, salt\"," +
        "\"contains\":[\"gluten\"],\"may_contain\":[],\"status\":\"draft\"}," +
        "{\"id\":\"o\",\"brand\":\"Oat Co\",\"name\":\"Oats Digestive Biscuits\",\"group\":\"biscuits\",\"pack\":200,\"unit\":\"g\"," +
        "\"beverage\":false,\"per100\":{\"energy_kcal\":470,\"sugars_g\":14,\"saturated_fat_g\":8,\"sodium_mg\":350," +
        "\"protein_g\":8,\"fibre_g\":7}," +
        "\"ingredients\":\"Rolled oats (45%), whole wheat flour (atta), jaggery, rice bran oil, salt\"," +
        "\"contains\":[\"gluten\"],\"may_contain\":[],\"status\":\"draft\"}]}";

    /** A cream biscuit: 505 kcal, 36 g sugar, 11 g saturated fat. Nutri-Score E. */
    private static Nutrition creamBiscuit() {
        Nutrition n = food(505, 36, 11, 180, 6, 2.0);
        n.netQuantity = 75.0;
        return n;
    }

    /**
     * Founder (8 Oct 2026): three recommendations from the same category. Where no green biscuit exists, a still-red
     * one is offered only when it is a real improvement, and says so: a better letter (E to D), or the same letter
     * with this product's weakest point (sugar) cut by at least 30%. Never a worse score.
     */
    @Test public void aStillRedBiscuitIsOfferedOnlyAsARealImprovementAndSaysSo() throws Exception {
        NutriGrade.Result current = NutriGrade.grade(creamBiscuit());
        assertEquals(NutriGrade.Band.C, current.band);
        assertEquals('E', current.letter());
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(BISCUITS), "Sunfeast Dark Fantasy Choco Fills",
            creamBiscuit(), current.band, current.letter(), false, SafetyProfile.unknown(), Occasion.SNACK, true);
        assertEquals(2, out.swaps.size());
        assertEquals("Only 2 better biscuits & cookies we know of suit you.", out.shortfall);
        assertEquals("the better letter first", "Oats Digestive Biscuits", out.swaps.get(0).product.name);
        assertEquals("Still red, Nutri-Score D: a step better than this product", out.swaps.get(0).standing);
        assertEquals("Still red, the same Nutri-Score (E) as this product", out.swaps.get(1).standing);
        for (SwapEngine.Swap s : out.swaps) {
            assertTrue(s.product.name, s.letter <= 'E');
            assertTrue(s.checks.toString(), s.checks.stream().anyMatch(c -> c.startsWith("Less sugar, this product's weakest point: 36 g")));
        }
    }

    /** Three green crackers from three brands, plus the D digestive and the E Marie in BISCUITS. */
    private static final String THREE_GREEN_AND_A_STEP = BISCUITS.replace("]}", "," +
        "{\"id\":\"r\",\"brand\":\"Millet Co\",\"name\":\"Ragi Crackers\",\"group\":\"biscuits\",\"pack\":150,\"unit\":\"g\"," +
        "\"beverage\":false,\"per100\":{\"energy_kcal\":420,\"sugars_g\":3,\"saturated_fat_g\":2,\"sodium_mg\":300,\"protein_g\":10,\"fibre_g\":8}," +
        "\"ingredients\":\"Ragi flour, whole wheat flour, rice bran oil, salt\",\"contains\":[\"gluten\"],\"may_contain\":[],\"status\":\"draft\"}," +
        "{\"id\":\"j\",\"brand\":\"Jowar Co\",\"name\":\"Jowar Crackers\",\"group\":\"biscuits\",\"pack\":150,\"unit\":\"g\"," +
        "\"beverage\":false,\"per100\":{\"energy_kcal\":400,\"sugars_g\":2,\"saturated_fat_g\":1.5,\"sodium_mg\":280,\"protein_g\":11,\"fibre_g\":9}," +
        "\"ingredients\":\"Jowar flour, whole wheat flour, rice bran oil, salt\",\"contains\":[\"gluten\"],\"may_contain\":[],\"status\":\"draft\"}," +
        "{\"id\":\"g\",\"brand\":\"Grain Co\",\"name\":\"Multigrain Crackers\",\"group\":\"biscuits\",\"pack\":150,\"unit\":\"g\"," +
        "\"beverage\":false,\"per100\":{\"energy_kcal\":410,\"sugars_g\":5,\"saturated_fat_g\":2.5,\"sodium_mg\":250,\"protein_g\":9,\"fibre_g\":6}," +
        "\"ingredients\":\"Whole wheat flour, oats, rice bran oil, salt\",\"contains\":[\"gluten\"],\"may_contain\":[],\"status\":\"draft\"}]}");

    @Test public void greenComesFirstThenTheBestOfTheRestEachSayingItsColour() throws Exception {
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(THREE_GREEN_AND_A_STEP), "Sunfeast Dark Fantasy Choco Fills",
            creamBiscuit(), NutriGrade.Band.C, 'E', false, SafetyProfile.unknown(), Occasion.SNACK, true);
        assertEquals(SwapEngine.MAX_SWAPS, out.swaps.size());
        assertNull(out.shortfall);
        List<NutriGrade.Band> bands = new ArrayList<>();
        for (SwapEngine.Swap s : out.swaps) {
            bands.add(s.band);
            assertTrue(s.checks.toString(), s.checks.stream().anyMatch(c -> c.startsWith("Less sugar, this product's weakest point: 36 g")));
            assertTrue(s.standing, s.standing.startsWith(s.band == NutriGrade.Band.A ? "Green" : s.band == NutriGrade.Band.B ? "Yellow" : "Still red"));
            assertTrue(s.checks.toString(), s.checks.get(0).startsWith(s.band == NutriGrade.Band.A ? "Green: Nutri-Score" : "Nutri-Score"));
        }
        List<NutriGrade.Band> sorted = new ArrayList<>(bands);
        Collections.sort(sorted);
        assertEquals("green before yellow before red", sorted, bands);
        assertEquals(NutriGrade.Band.A, bands.get(0));
        assertTrue("the still-red Marie waits behind every better colour",
            out.swaps.stream().noneMatch(s -> s.product.name.equals("Marie Biscuits")));
    }

    /** Every check that applies to the shopper is listed with its numbers: allergy, diet, and each condition limit. */
    @Test public void theChecksFollowTheShoppersOwnProfile() throws Exception {
        Profile p = new Profile();
        p.adultConfirmed = true;
        java.util.Arrays.fill(p.answers, "");
        p.answers[Profile.HEALTH] = "peanut,cholesterol";
        p.answers[Profile.DIET_DETAIL] = "Vegetarian";
        p.answers[Profile.GOALS] = "aware";
        p.syncDerived();
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(THREE_GREEN_AND_A_STEP), "Sunfeast Dark Fantasy Choco Fills",
            creamBiscuit(), NutriGrade.Band.C, 'E', false, SafetyProfile.fromProfile(p), Occasion.SNACK, true, p);
        assertFalse(out.emptyReason, out.swaps.isEmpty());
        java.util.List<String> checks = out.swaps.get(0).checks;
        assertTrue(checks.toString(), checks.stream().anyMatch(c -> c.startsWith("No peanuts in its ingredients or allergen statement")));
        assertTrue(checks.toString(), checks.stream().anyMatch(c -> c.startsWith("Vegetarian: no meat, fish or egg in its list")));
        assertTrue(checks.toString(), checks.stream().anyMatch(c -> c.startsWith("Saturated fat not higher: ") && c.endsWith("vs 11 g per 100 g")));
        // A shopper with high blood pressure gets none of these crackers: each has more sodium than the cream biscuit.
        p.answers[Profile.HEALTH] = "peanut,hypertension";
        p.syncDerived();
        SwapEngine.Outcome bp = SwapEngine.recommend(SwapCatalogue.parse(THREE_GREEN_AND_A_STEP), "Sunfeast Dark Fantasy Choco Fills",
            creamBiscuit(), NutriGrade.Band.C, 'E', false, SafetyProfile.fromProfile(p), Occasion.SNACK, true, p);
        assertTrue(bp.swaps.isEmpty());
        assertTrue(bp.emptyReason, bp.emptyReason.contains("more sodium"));
    }

    @Test public void aSameScoreSwapNeedsARealCutInTheWeakestPoint() throws Exception {
        // 2023: energy 1862 kJ 5 + sugars 16 g 4 + saturates 5.2 g 5 + salt 0.80 g 3 = 17, fibre 0 -> 17, D.
        Nutrition marie = food(445, 16.0, 5.2, 319, 8, 2.0);
        NutriGrade.Result current = NutriGrade.grade(marie);
        assertEquals('D', current.letter());
        // The other Marie is E (worse); the digestive is D with 12% less sugar and more saturated fat: neither is better.
        SwapEngine.Outcome out = SwapEngine.recommend(SwapCatalogue.parse(BISCUITS), "Some Marie Biscuits", marie,
            current.band, current.letter(), false, SafetyProfile.unknown(), Occasion.SNACK, true);
        assertTrue(out.swaps.toString(), out.swaps.isEmpty());
    }

    @Test public void theOfficialLettersSitUnderTheBands() {
        Nutrition water = new Nutrition();
        water.energyKcal = 0.0; water.sugarsG = 0.0; water.saturatedFatG = 0.0; water.sodiumMg = 0.0; water.beverage = true;
        assertEquals('A', NutriGrade.grade(water, Beverage.classify("Bisleri Packaged Drinking Water 1 L", null, true)).letter());
        Nutrition cola = new Nutrition();
        cola.energyKcal = 44.0; cola.sugarsG = 10.9; cola.saturatedFatG = 0.0; cola.sodiumMg = 7.0; cola.beverage = true;
        assertEquals('E', NutriGrade.grade(cola, Beverage.classify("Coca-Cola 750 ml", null, true)).letter());
        assertEquals('D', NutriGrade.grade(food(445, 16.0, 5.2, 319, 8, 2.0)).letter());
        // With 21.9 g sugar the same biscuit is 19 under 2023: E.
        assertEquals('E', NutriGrade.grade(food(445, 21.9, 5.2, 319, 8, 2.0)).letter());
    }

    /** The product being viewed gets its ingredient facts too, read from the page's own list. */
    @Test public void theRatedProductShowsWhatItsIngredientListSays() {
        java.util.List<ScreenText> screen = new ArrayList<>();
        screen.add(new ScreenText("Tata Soulfull Ragi Bites Choco Fills", 300, false));
        screen.add(new ScreenText("₹120", 340, false));
        screen.add(new ScreenText("Product Details", 600, true));
        screen.add(new ScreenText("Ingredients", 640, false));
        screen.add(new ScreenText("Refined Wheat Flour (Maida), Sugar, Ragi Flour (10%), Palm Oil, Cocoa Solids", 660, false));
        screen.add(new ScreenText("Nutrition Information (Per 100 g)", 700, false));
        screen.add(new ScreenText("Energy 470 kcal", 720, false));
        screen.add(new ScreenText("Total Sugars 30 g", 740, false));
        screen.add(new ScreenText("Saturated Fat 9 g", 760, false));
        screen.add(new ScreenText("Sodium 200 mg", 780, false));
        screen.add(new ScreenText("Protein 7 g", 800, false));
        GradeResult r = ProductAnalyzer.analyze(screen, 2400, "com.grofers.customerapp", p -> PackScan.EMPTY, null, null);
        assertEquals(GradeResult.State.GRADED, r.state);
        assertTrue(r.concerns.toString(), r.concerns.contains("Refined flour (maida) is the main ingredient"));
        assertFalse("The name says ragi; the list says maida first", r.positives.toString().contains("Ragi is the main"));
    }

    // --- One rule for every category: three from the same category, never worse ----------------------------

    /**
     * Founder (8 Oct 2026): three recommendations from the same category of the product, on every platform. Typical
     * red products from the categories that had no green swap: each now gets three, from its own category.
     */
    @Test public void threeChoicesFromTheSameCategoryForTypicalRedProducts() throws Exception {
        SwapCatalogue c = real();
        Object[][] products = {
            {"Britannia Pure Magic Choco Stars Chunky Choco Topped Cookie", food(494, 22, 11, 300, 7, 2.0), false},
            {"Cadbury Dairy Milk Chocolate Bar", food(531, 57, 19.6, 129, 7.9, 1.0), false},
            {"Lay's Classic Salted | Crunchy Potato Chips", food(553, 0.6, 15.8, 500, 6.7, 3.0), false},
            {"Maggi Masala 2 Minutes Instant Noodles", food(384, 1.8, 8.2, 1000, 8.2, 2.0), false}};
        for (Object[] p : products) {
            Nutrition n = (Nutrition) p[1];
            n.basisStated = true;
            NutriGrade.Result grade = NutriGrade.grade(n);
            SwapEngine.Outcome out = SwapEngine.recommend(c, (String) p[0], n, grade.band, grade.letter(), (Boolean) p[2],
                SafetyProfile.unknown(), Occasion.SNACK, true);
            assertNotNull((String) p[0], out.group);
            assertEquals(p[0] + ": " + out.emptyReason + " " + out.shortfall, SwapEngine.MAX_SWAPS, out.swaps.size());
            Set<String> names = new java.util.HashSet<>();
            for (SwapEngine.Swap s : out.swaps) {
                assertEquals(out.group.id, s.product.groupId);
                assertTrue("never the same product twice", names.add(s.product.displayName()));
                assertNotNull(s.standing);
            }
        }
    }

    /**
     * Every red or yellow product in the catalogue, taken in turn as the one on screen: whatever is offered is from its
     * own category, never a worse letter, a real difference (a better letter with 30% less of something the category
     * is judged on, or the same letter with the weakest point cut by 30%), nothing the category is judged on higher,
     * and nothing new "high". The rule holds for the whole class, not for chosen examples.
     */
    @Test public void noSwapAnywhereIsWorseOrMerelySideways() throws Exception {
        SwapCatalogue c = real();
        int products = 0, swaps = 0, three = 0;
        for (SwapCatalogue.Product p : c.products) {
            SwapCatalogue.Group group = c.groups.stream().filter(g -> g.id.equals(p.groupId)).findFirst().orElse(null);
            if (group == null || group.drink != p.beverage || !group.fits(p.displayName())) continue;
            Beverage identity = Beverage.classify(p.displayName(), p.ingredients, p.per100.beverage);
            NutriGrade.Result grade = NutriGrade.grade(p.per100, identity, FoodCategory.kind(p.displayName(), group.id));
            if (grade.band == NutriGrade.Band.A) continue;
            products++;
            SwapEngine.Outcome out = SwapEngine.recommend(c, p.displayName(), p.per100, grade.band, grade.letter(), p.beverage,
                SafetyProfile.unknown(), null, true);
            if (out.group == null || !out.group.id.equals(group.id)) continue;
            char current = SwapEngine.letterOf(p.per100, grade.letter(), p.displayName(), p.beverage, group);
            String worst = SwapEngine.worstFocus(group, p.per100, p.beverage);
            if (out.swaps.size() == SwapEngine.MAX_SWAPS) three++;
            for (SwapEngine.Swap s : out.swaps) {
                swaps++;
                String who = p.displayName() + " -> " + s.product.displayName();
                assertEquals(who, group.id, s.product.groupId);
                assertTrue(who, s.letter <= current);
                List<String> real = SwapEngine.realDifferences(group, p.per100, s.product.per100);
                assertFalse(who, real.isEmpty());
                assertTrue(who, s.letter < current || real.contains(worst));
                assertFalse(who, SwapEngine.worseOnFocus(group, p.per100, s.product.per100));
                Set<String> high = NutriGrade.highFlags(s.product.per100, s.product.beverage);
                high.removeAll(NutriGrade.highFlags(p.per100, p.beverage));
                high.removeAll(group.allowNewHigh);
                assertTrue(who + " " + high, high.isEmpty());
            }
        }
        assertTrue("products " + products, products > 300);
        assertTrue("three choices for most red and yellow products: " + three + " of " + products, three * 2 > products);
        assertTrue(swaps > 0);
    }

    /**
     * What the shop itself says the product is decides its category. Zepto prints "Product Type: Soya Chunks" for "ZOFF Soya
     * Chunks - Soya Nuggets", whose name alone matched frozen nuggets: a protein product, so no category rather than a
     * wrong one. Without a declared type the name decides, as before.
     */
    @Test public void theShopsOwnProductTypeDecidesTheCategory() throws Exception {
        SwapCatalogue c = real();
        assertNull(c.placeFor("ZOFF Soya Chunks - Soya Nuggets", "Soya Chunks", false));
        assertEquals("frozen_snacks", c.placeFor("ZOFF Soya Chunks - Soya Nuggets", null, false).id);
        assertEquals("chocolate", c.placeFor("Cadbury Dairy Milk Chocolate Bar", "Chocolate", false).id);
        assertEquals("biscuits", c.placeFor("Sunfeast Dark Fantasy Choco Fills", "Cookies", false).id);
        assertEquals("salty_snacks", c.placeFor("Balaji Wafers Masala Masti", "Chips", false).id);
        List<ScreenText> screen = new ArrayList<>();
        screen.add(new ScreenText("ZOFF Soya Chunks - Soya Nuggets", 300, false));
        screen.add(new ScreenText("₹29", 340, false));
        screen.add(new ScreenText("Highlights", 600, false));
        screen.add(new ScreenText("Product Type", 650, 114, false));
        screen.add(new ScreenText("Soya Chunks", 650, 408, false));
        screen.add(new ScreenText("Nutrition Information", 700, false));
        ProductPage page = ProductPage.detect(screen, 2392, "com.zeptoconsumerapp");
        assertNotNull(page);
        assertEquals("Soya Chunks", page.productType());
    }

    /** The same product, named the way each shop writes it, lands in the same category everywhere. */
    @Test public void eachShopsWayOfNamingAProductFindsTheSameCategory() throws Exception {
        SwapCatalogue c = real();
        String[][] names = {
            {"Britannia Good Day Cashew Cookies", "Britannia Good Day Cashew Cookies | Crunchy & Buttery",
                "BRITANNIA Good Day Cashew Cookies  (200 g)", "Britannia Good Day Cashew Cookies 200g"},
            {"Lay's India's Magic Masala Potato Chips", "Lay's India's Magic Masala Chips | Party Pack",
                "LAY'S India's Magic Masala Potato Chips  (52 g)", "Lays Indias Magic Masala Chips 52 g"},
            {"Cadbury Dairy Milk Chocolate Bar", "Cadbury Dairy Milk Chocolate | Smooth & Creamy",
                "Cadbury Dairy Milk Chocolate Bars  (13.2 g)", "Cadbury Dairy Milk Chocolate 13.2g"}};
        for (String[] variants : names) {
            String group = c.groupFor(variants[0], false).id;
            for (String v : variants) assertEquals(v, group, c.groupFor(v, false).id);
        }
    }

    // --- The live check on the shop's screen, and the 2x rule --------------------------------------------

    private static SwapCheck.Pending pending(double originalPrice, Double originalQty) throws Exception {
        SwapCatalogue c = SwapCatalogue.parse(TEST_CATALOGUE);
        SwapCatalogue.Product makhana = c.products.get(0);   // Crunch Co Roasted Makhana Pudina, 60 g
        SwapEngine.Swap swap = new SwapEngine.Swap(makhana, NutriGrade.Band.A, "70% less sodium", 1);
        return new SwapCheck.Pending(swap, "com.grofers.customerapp", originalPrice, originalQty, "Potato Chips", 0);
    }

    private static List<ScreenText> results(String stockText) {
        List<ScreenText> s = new ArrayList<>();
        s.add(new ScreenText("Showing results for \"crunch co roasted makhana pudina\"", 200, false));
        s.add(new ScreenText("Lays Classic Salted Chips", 300, false));
        s.add(new ScreenText("52 g", 320, false));
        s.add(new ScreenText("₹20", 340, false));
        s.add(new ScreenText("ADD", 360, true));
        s.add(new ScreenText("8 MINS", 400, false));
        s.add(new ScreenText("Crunch Co Roasted Makhana Pudina Flavour", 420, false));
        s.add(new ScreenText("60 g", 440, false));
        s.add(new ScreenText("₹45", 460, false));
        s.add(new ScreenText("₹50", 470, false));
        s.add(new ScreenText(stockText, 480, true));
        return s;
    }

    @Test public void theSwapIsFoundInStockWithItsOwnPriceNotItsNeighbours() throws Exception {
        SwapCheck.Result r = SwapCheck.find(results("ADD"), pending(60, 52.0));
        assertEquals(SwapCheck.State.IN_STOCK, r.state);
        assertEquals("The selling price, not the MRP beside it or the chips above it", 45.0, r.price, 0.001);
        assertEquals(60.0, r.pack, 0.001);
        assertEquals(1, r.packs);
        assertTrue(r.withinBudget);
        assertEquals(120.0, r.ceiling, 0.001);
        assertEquals(75.0, r.per100, 0.001);
    }

    @Test public void outOfStockIsSaidPlainly() throws Exception {
        assertEquals(SwapCheck.State.OUT_OF_STOCK, SwapCheck.find(results("Notify me"), pending(60, 52.0)).state);
    }

    @Test public void notOnScreenIsNotFoundRatherThanAGuess() throws Exception {
        List<ScreenText> other = new ArrayList<>();
        other.add(new ScreenText("Lays Classic Salted Chips", 300, false));
        other.add(new ScreenText("₹20", 340, false));
        assertEquals(SwapCheck.State.NOT_FOUND, SwapCheck.find(other, pending(60, 52.0)).state);
    }

    /** Smaller packs count together: two 60 g packs replace a 150 g one, and must fit the 2x limit together. */
    @Test public void theBudgetCountsEveryPackNeededAndUsesTwiceTheOriginal() throws Exception {
        SwapCheck.Result r = SwapCheck.find(results("ADD"), pending(40, 150.0));
        assertEquals(3, r.packs);   // 150 / 60 = 2.5, rounded to 3 packs
        assertEquals(135.0, r.combinedCost, 0.001);
        assertEquals(80.0, r.ceiling, 0.001);
        assertFalse(r.withinBudget);
    }

    @Test public void theSearchWordsAreTheBrandAndTheProductNotThePackSize() throws Exception {
        assertEquals("crunch co roasted makhana pudina", SwapCheck.query(SwapCatalogue.parse(TEST_CATALOGUE).products.get(0)));
    }

    /**
     * Peanut butter is the classic swap for a chocolate spread, but it is more energy-dense. The
     * nutritionist's draft rule lets that one trade-off through for spreads, and the card must say it.
     */
    @Test public void anAllowedTradeOffIsStatedNextToTheBenefit() throws Exception {
        Nutrition nutella = food(539, 56.3, 10.6, 43, 6.3, 3.4);
        nutella.netQuantity = 350.0;
        SwapEngine.Outcome out = SwapEngine.recommend(real(), "Nutella Hazelnut Spread with Cocoa", nutella, NutriGrade.Band.C,
            false, SafetyProfile.unknown(), Occasion.BREAKFAST, true);
        assertEquals("spreads", out.group.id);
        assertFalse(out.emptyReason, out.swaps.isEmpty());
        char nutellaLetter = SwapEngine.letterOf(nutella, '?', "Nutella Hazelnut Spread with Cocoa", false, out.group);
        for (SwapEngine.Swap s : out.swaps) {
            boolean energyHigh = NutriGrade.highFlags(s.product.per100, false).contains("energy");
            assertEquals(s.why, energyHigh, s.why.contains("higher in calories"));
            assertTrue(s.product.name, s.letter <= nutellaLetter);
            assertFalse(s.product.name, SwapEngine.realDifferences(out.group, nutella, s.product.per100).isEmpty());
        }
    }

    @Test public void eachShopHasASearchLinkAndZomatoHasNone() {
        assertTrue(RetailerSearch.links("com.grofers.customerapp", "roasted makhana").get(0)
            .startsWith("https://blinkit.com/s/?q=roasted+makhana"));
        assertTrue(RetailerSearch.links("com.zeptoconsumerapp", "x").get(0).contains("search?query=x"));
        assertTrue(RetailerSearch.links("in.swiggy.android", "x").get(0).startsWith("https://www.instamart.in/search?query="));
        assertFalse(RetailerSearch.supports("com.application.zomato"));
    }

    // --- Occasion ------------------------------------------------------------------------------------------

    @Test public void theClockOnlyGuessesTheOccasion() {
        assertEquals(Occasion.BREAKFAST, Occasion.fromClock(LocalDateTime.of(2026, 9, 23, 8, 0)));
        assertEquals(Occasion.MEAL, Occasion.fromClock(LocalDateTime.of(2026, 9, 23, 13, 0)));
        assertEquals(Occasion.SNACK, Occasion.fromClock(LocalDateTime.of(2026, 9, 23, 17, 0)));
        assertEquals(Occasion.MEAL, Occasion.fromClock(LocalDateTime.of(2026, 9, 23, 20, 30)));
        assertEquals("Friday night", Occasion.PARTY, Occasion.fromClock(LocalDateTime.of(2026, 9, 25, 21, 0)));
        assertEquals(Occasion.SNACK, Occasion.fromClock(LocalDateTime.of(2026, 9, 23, 23, 30)));
        assertFalse("Party wording never mentions alcohol", Occasion.PARTY.label.toLowerCase().contains("alcohol"));
    }

    // --- The product name and price stay right while scrolling ---------------------------------------------

    private static List<ScreenText> appyFizzTop() {
        List<ScreenText> s = new ArrayList<>();
        s.add(new ScreenText("Delivery in 8 minutes", 60, false));
        s.add(new ScreenText("Appy Fizz Apple Soft Drink | Pet | Fizzy & Refreshing", 900, false));
        s.add(new ScreenText("₹60", 980, false));
        s.add(new ScreenText("Product Details", 1300, true));
        s.add(new ScreenText("Refreshing sparkling apple beverage, caffeine-free natural formulation, convenient resealable bottle", 1400, false));
        s.add(new ScreenText("ADD", 2200, true));
        return s;
    }

    private static List<ScreenText> appyFizzScrolled() {
        List<ScreenText> s = new ArrayList<>();
        s.add(new ScreenText("Product Details", 150, true));
        s.add(new ScreenText("Refreshing sparkling apple beverage, caffeine-free natural formulation, convenient resealable bottle", 300, false));
        s.add(new ScreenText("Nutrition Information", 700, false));
        s.add(new ScreenText("Similar products", 1500, false));
        s.add(new ScreenText("Sprite Lime Soft Drink 750 ml", 1600, false));
        s.add(new ScreenText("₹48", 1650, false));
        s.add(new ScreenText("ADD", 1700, true));
        return s;
    }

    @Test public void aDescriptionIsNotTakenForTheProductName() {
        assertTrue(ProductPage.titleLike("Appy Fizz Apple Soft Drink | Pet | Fizzy & Refreshing"));
        assertFalse(ProductPage.titleLike("Refreshing sparkling apple beverage, caffeine-free natural formulation, convenient resealable bottle"));
        ProductPage top = ProductPage.detect(appyFizzTop(), 2400, "com.grofers.customerapp");
        assertEquals("Appy Fizz Apple Soft Drink | Pet | Fizzy & Refreshing", top.name);
        assertEquals(60.0, top.price, 0.001);
    }

    /** The bug from the phone test: scrolled down, it became "Refreshing sparkling…" at ₹48. */
    @Test public void scrollingKeepsTheSameProductAndItsOwnPrice() {
        ProductPage top = ProductPage.detect(appyFizzTop(), 2400, "com.grofers.customerapp");
        ProductPage scrolled = ProductPage.detect(appyFizzScrolled(), 2400, "com.grofers.customerapp", null, top);
        assertNotNull(scrolled);
        assertEquals(top.name, scrolled.name);
        assertEquals("Never the similar product's ₹48", 60.0, scrolled.price, 0.001);
        assertEquals(top.key(), scrolled.key());
    }

    @Test public void aNewProductPageIsStillRecognisedAsNew() {
        ProductPage top = ProductPage.detect(appyFizzTop(), 2400, "com.grofers.customerapp");
        List<ScreenText> next = new ArrayList<>();
        next.add(new ScreenText("Delivery in 8 minutes", 60, false));
        next.add(new ScreenText("Sprite Lime Flavoured Soft Drink 750 ml", 900, false));
        next.add(new ScreenText("₹48", 980, false));
        next.add(new ScreenText("Product Details", 1300, true));
        ProductPage sprite = ProductPage.detect(next, 2400, "com.grofers.customerapp", null, top);
        assertEquals("Sprite Lime Flavoured Soft Drink 750 ml", sprite.name);
        assertEquals(48.0, sprite.price, 0.001);
    }
}
