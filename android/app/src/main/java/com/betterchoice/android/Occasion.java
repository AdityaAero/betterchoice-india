package com.betterchoice.android;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * What the shopper is buying for. Used only to order healthier swaps, never to decide what a product
 * is, and never to change its rating: breakfast cereal bought at 10 pm is still breakfast cereal.
 *
 * <p>Where the occasion comes from, strongest first: the chip the shopper taps on the card, then the
 * phone's clock as a weak hint. Browsing time is not eating time — quick-commerce orders are often for
 * later — which is why the clock only pre-selects a chip the shopper can change with one tap.
 *
 * <p>"Party &amp; sharing" deliberately says nothing about alcohol: alcohol promotion is restricted in
 * India, and a snack never offsets a drink.
 */
enum Occasion {
    SNACK("snack", "Snack"),
    BREAKFAST("breakfast", "Breakfast"),
    MEAL("meal", "Meal"),
    PARTY("party", "Party & sharing");

    final String code, label;
    Occasion(String code, String label) { this.code = code; this.label = label; }

    static Occasion fromCode(String code) {
        for (Occasion occasion : values()) if (occasion.code.equals(code)) return occasion;
        return null;
    }

    /**
     * The clock's guess. 5:00–10:30 breakfast; 12:00–15:30 and 19:00–22:30 a meal; Friday and
     * Saturday from 20:00 sharing; any other time a snack.
     */
    static Occasion fromClock(LocalDateTime now) {
        LocalTime t = now.toLocalTime();
        DayOfWeek day = now.getDayOfWeek();
        boolean weekendNight = (day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY) && !t.isBefore(LocalTime.of(20, 0));
        if (weekendNight) return PARTY;
        if (!t.isBefore(LocalTime.of(5, 0)) && t.isBefore(LocalTime.of(10, 30))) return BREAKFAST;
        if (!t.isBefore(LocalTime.of(12, 0)) && t.isBefore(LocalTime.of(15, 30))) return MEAL;
        if (!t.isBefore(LocalTime.of(19, 0)) && t.isBefore(LocalTime.of(22, 30))) return MEAL;
        return SNACK;
    }
}
