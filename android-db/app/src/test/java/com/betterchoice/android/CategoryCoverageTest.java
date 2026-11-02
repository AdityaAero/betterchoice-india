package com.betterchoice.android;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** No category is left without a route: every FSSAI category, form option and swap group is placed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CategoryCoverageTest {
    @Test public void everyFssaiCategoryHasEvidenceAndGuidance() {
        assertEquals(16, FoodCategory.Fcs.values().length);
        for (FoodCategory.Fcs fcs : FoodCategory.Fcs.values()) {
            assertNotNull(fcs.name(), Evidence.get(fcs.evidence()));
            for (boolean liquid : new boolean[]{false, true}) {
                java.util.List<String> lines = FoodCategory.guidance(new FoodCategory.Placement(fcs, false, false), liquid);
                assertTrue(fcs.name(), lines.size() >= 2);
                assertTrue(lines.get(0), lines.get(0).contains(liquid ? "70 kcal" : "250 kcal"));
            }
        }
        // Even a product no category matches gets guidance.
        assertFalse(FoodCategory.guidance(FoodCategory.place("Zzz product", false, null), false).isEmpty());
    }

    @Test public void oneRealProductPerCategoryIsPlaced() {
        Object[][] samples = {
            {"Amul Masti Dahi Curd 400 g", FoodCategory.Fcs.DAIRY}, {"Fortune Kachi Ghani Mustard Oil", FoodCategory.Fcs.FATS_OILS},
            {"Kwality Wall's Cornetto Ice Cream", FoodCategory.Fcs.EDIBLE_ICES}, {"Tata Sampann Moong Dal", FoodCategory.Fcs.FRUIT_VEG_PULSES_NUTS},
            {"Haldiram's Soan Papdi", FoodCategory.Fcs.CONFECTIONERY}, {"Aashirvaad Whole Wheat Atta", FoodCategory.Fcs.CEREALS},
            {"Britannia Bread White", FoodCategory.Fcs.BAKERY}, {"Licious Chicken Seekh Kebab", FoodCategory.Fcs.MEAT},
            {"Fresh Rohu Fish Curry Cut", FoodCategory.Fcs.FISH}, {"Farm Fresh White Eggs 6 pcs", FoodCategory.Fcs.EGGS},
            {"Dabur Honey", FoodCategory.Fcs.SWEETENERS}, {"Kissan Fresh Tomato Ketchup", FoodCategory.Fcs.SALTS_SPICES_SAUCES},
            {"MuscleBlaze Whey Protein Powder", FoodCategory.Fcs.PARTICULAR_USES}, {"Paper Boat Aamras Juice", FoodCategory.Fcs.BEVERAGES},
            {"Haldiram's Aloo Bhujia Namkeen", FoodCategory.Fcs.SAVOURIES}, {"MTR Ready To Eat Rajma Masala Curry", FoodCategory.Fcs.PREPARED}};
        for (Object[] s : samples) assertEquals((String) s[0], s[1], FoodCategory.place((String) s[0], false, null).category);
        assertTrue(FoodCategory.place("Nestle Cerelac Baby Food", false, null).infantFood);
    }

    @Test public void everyFormOptionAndSwapGroupMapsToACategory() throws Exception {
        for (String[] option : Profile.BUY_OPTIONS) assertFalse(option[0], FoodCategory.forBuyOption(option[0]).isEmpty());
        File asset = new File("src/main/assets/" + SwapCatalogue.ASSET);
        if (!asset.exists()) asset = new File("app/src/main/assets/" + SwapCatalogue.ASSET);
        SwapCatalogue cat = SwapCatalogue.parse(new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8));
        for (SwapCatalogue.Group g : cat.groups) assertNotNull(g.id, FoodCategory.fromSwapGroup(g.id));
    }
}
