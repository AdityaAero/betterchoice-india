package com.betterchoice.android;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Zepto titles are long and descriptive: a flavour ("Cream & Onion"), a claim after a bar
 * ("| Rich in Cocoa") or a coating ("Coated Wafer") must not hide what the product is, or the card
 * shows no comparisons on Zepto for a product that gets them on Blinkit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class SwapCategoryTest {
    private static SwapCatalogue catalogue;

    private static String group(String title) throws Exception {
        if (catalogue == null) {
            for (String path : new String[] {"src/main/assets/" + SwapCatalogue.ASSET, "app/src/main/assets/" + SwapCatalogue.ASSET}) {
                File file = new File(path);
                if (file.exists()) catalogue = SwapCatalogue.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            }
        }
        SwapCatalogue.Group g = catalogue.groupFor(title, false);
        return g == null ? null : g.id;
    }

    /** Titles recorded on Zepto that had no comparisons at all. */
    @Test public void zeptoTitlesFindTheirCategory() throws Exception {
        assertEquals("salty_snacks", group("Crax Curls Chatpata Masala Puff Snacks | Crispy & Tasty"));
        assertEquals("salty_snacks", group("Farmley Cream & Onion  Makhana Roasted In Olive Oil Healthy Snacks"));
        assertEquals("salty_snacks", group("Pringles Sour Cream & Onion Potato Chips"));
        assertEquals("chocolate", group("Amul Dark Chocolate Bar | Rich in Cocoa"));
        assertEquals("chocolate", group("Cadbury Bournville Rich Cocoa 50% Dark Chocolate Bar"));
        assertEquals("chocolate", group("Nestle KitKat Delights Dark Choco Coated Wafer Chocolate Bar"));
        assertEquals("salty_snacks", group("Lay's Classic Salted | Crunchy Potato Chips"));
        // The claim after the bar names groundnut oil; the product before it is potato wafers.
        assertEquals("salty_snacks", group("Let's Try Rock Salt Potato Wafers | 100% Groundnut Oil | Fasting Snacks"));
    }

    /** What the exclusions are for still stays out. */
    @Test public void otherProductTypesStayOut() throws Exception {
        assertNotEquals("chocolate", group("Hershey's Chocolate Cocoa Powder"));
        assertNotEquals("chocolate", group("Yoga Bar Dark Chocolate Protein Bar | 20g Protein"));
        assertEquals("biscuits", group("Britannia Treat Chocolate Wafers"));
        assertEquals("biscuits", group("Dukes Cream Wafers Vanilla"));
        assertEquals("biscuits", group("Unibic Chocolate Chip Cookies"));
        assertEquals("a chikki is a sweet, not nuts", "sweets_mithai",
            group("Sweet Karam Coffee Crushed Peanut Chikki Bar | No White Sugar"));
        assertEquals("bread is bread, not a biscuit", "bread", group("English Oven Premium White bread| No Palm Oil"));
    }

    @Test public void everySwapGroupNamesTheFormOptionThatTurnsItOn() throws Exception {
        group("chips");
        for (String id : new String[] {"salty_snacks", "nuts_seeds", "biscuits", "soft_drinks", "fruit_drinks",
                "flavoured_milk", "ice_cream", "instant_noodles", "breakfast_cereals", "chocolate", "spreads"}) {
            String code = GoalComparisons.buyCode(id);
            assertNotNull(id, code);
            assertNotEquals(id, code, Profile.buyLabel(code));
        }
    }
}
