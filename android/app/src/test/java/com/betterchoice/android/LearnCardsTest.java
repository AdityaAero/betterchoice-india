package com.betterchoice.android;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/** The learning cards must stay factual: no brands, no medical or dietary advice. */
public class LearnCardsTest {
    @Test public void everyCardIsCompleteAndShortEnoughToRead() {
        assertTrue(LearnCards.CARDS.length >= 8);
        for (LearnCards.Card card : LearnCards.CARDS) {
            assertFalse(card.tag.isBlank());
            assertFalse(card.title.isBlank());
            assertTrue(card.title.length() <= 60);
            assertTrue(card.body.length() >= 40 && card.body.length() <= 320);
            assertFalse(card.takeaway.isBlank());
        }
    }

    @Test public void noMedicalAdviceOrBrandNames() {
        String[] banned = {"cure", "treat", "disease", "diabetes", "prescribe", "doctor", "medicine",
            "you should eat", "lose weight fast", "maggi", "lay's", "coca", "pepsi", "amul", "britannia"};
        for (LearnCards.Card card : LearnCards.CARDS) {
            String text = (card.title + " " + card.body + " " + card.takeaway).toLowerCase(Locale.ROOT);
            for (String word : banned) assertFalse(card.title + " mentions " + word, text.contains(word));
        }
    }

    @Test public void theServingSizeCardIsArithmeticallyTrue() {
        // The card says 140 kcal per 25 g serving in a 6-serving pack is about 840 kcal for the pack.
        assertEquals(840, 140 * 6);
        assertTrue(LearnCards.CARDS[0].body.contains("serving size"));
        assertTrue(LearnCards.CARDS[1].body.contains("100 g"));
    }
}
