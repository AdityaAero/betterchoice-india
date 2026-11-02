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
 * A product's name places it in a group, and what the name says the product is not ("No Maida", "Not from
 * Concentrate", "No Palm Oil") does not: the word after a negation neither puts it in a group nor keeps it out of one.
 * Run on the real catalogue's groups, the ones the phone uses (the builder, curate_swaps.positive, does the same).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class GroupNegationTest {

    private static SwapCatalogue real() throws Exception {
        for (String path : new String[] {"src/main/assets/" + SwapCatalogue.ASSET, "app/src/main/assets/" + SwapCatalogue.ASSET}) {
            File file = new File(path);
            if (file.exists()) return SwapCatalogue.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        }
        throw new AssertionError("catalogue asset not found");
    }

    private static String group(SwapCatalogue c, String name, boolean drink) {
        SwapCatalogue.Group g = c.groupFor(name, drink);
        return g == null ? null : g.id;
    }

    @Test public void negatedWordsDoNotPlaceAProduct() throws Exception {
        SwapCatalogue c = real();
        // "maida" is a flour word: a vermicelli made without it is still vermicelli.
        assertEquals("pasta_vermicelli", group(c, "Bambino Regular Vermicelli No Maida", false));
        assertEquals("pasta_vermicelli", group(c, "Bambino 100% Suji, No Maida Roasted Vermicelli", false));
        assertEquals("flours_grains", group(c, "Good Life Maida", false));
        // "No Palm Oil" on a nut butter does not make it an oil.
        assertEquals("spreads", group(c, "Pintola Peanut Butter No Palm Oil", false));
        assertEquals("spreads", group(c, "No Sugar Peanut Butter", false));
    }

    @Test public void negatedWordsDoNotKeepAProductOut() throws Exception {
        SwapCatalogue c = real();
        // A coconut water "not from concentrate", or one made from concentrate, is ready to drink: a juice-group drink.
        assertEquals("fruit_drinks", group(c, "Real Activ Coconut Water Not from Concentrate", true));
        assertEquals("fruit_drinks", group(c, "Real Activ Concentrate Coconut Water Refreshing Hydration", true));
        // A concentrate to dilute stays out.
        assertNull(group(c, "Mapro Mango Fruit Concentrate", true));
    }

    @Test public void aNegationIsOnlyTheWordAfterIt() {
        // One word, or a known pair, is taken away; the rest of the name still counts.
        String name = SwapCatalogue.NEGATED.matcher("no sugar peanut butter").replaceAll(" ");
        assertTrue(name.contains("peanut butter"));
        assertFalse(SwapCatalogue.NEGATED.matcher("no added sugar muesli").replaceAll(" ").contains("sugar"));
        assertFalse(SwapCatalogue.NEGATED.matcher("chips no palm oil").replaceAll(" ").contains("oil"));
    }
}
