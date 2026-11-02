package com.betterchoice.android;

import java.util.Locale;
import java.util.regex.Pattern;

/** Decides only whether a product looks like food or drink. It never grades a product by its name. */
final class FoodWords {
    private static final Pattern FOOD = Pattern.compile("\\b(" +
        "atta|flour|rice|dal|daal|lentils?|rajma|chana|moong|besan|poha|oats|muesli|cornflakes|granola|millet|ragi|jowar|bajra|quinoa|" +
        "bread|bun|pav|biscuits?|cookies?|crackers?|rusk|cake|muffin|chips|crisps|nachos|namkeen|bhujia|mixture|popcorn|makhana|" +
        "chocolates?|candy|toffee|sweets?|mithai|ladoo|laddu|halwa|ice ?cream|kulfi|dessert|jam|spread|peanut butter|honey|sugar|jaggery|" +
        "milk|curd|dahi|yogh?urt|paneer|cheese|butter|ghee|cream|lassi|buttermilk|chaas|tofu|eggs?|chicken|mutton|fish|prawns?|meat|" +
        "noodles|pasta|maggi|soup|sauce|ketchup|mayonnaise|pickle|chutney|masala|spices?|salt|oil|vinegar|" +
        "juice|drink|soda|cola|beverage|water|tea|coffee|shake|smoothie|energy drink|squash|sharbat|kombucha|corn flakes|" +
        "fruits?|apple|banana|mango|orange|grapes|vegetables?|onion|potato|tomato|spinach|nuts|almonds?|cashews?|walnuts?|raisins|dates|seeds|" +
        "snack|bar|protein|cereal|frozen|ready to eat|ready to cook|instant|mix|pizza|burger|sandwich|wrap|momos|samosa|paratha|roti)\\b");
    private static final Pattern NON_FOOD = Pattern.compile("\\b(" +
        "shampoo|conditioner|soap|body wash|face wash|detergent|cleaner|dishwash|toothpaste|toothbrush|deodorant|perfume|lotion|" +
        "sunscreen|diapers?|sanitary|tissues?|phone|charger|cable|earphones?|headphones?|battery|bulb|pet food|dog food|cat food|" +
        "notebook|pen|toy|shirt|t-shirt|shoes|lipstick|kajal|serum|hair oil|medicine|tablets?|capsules?|syrup for)\\b");

    static boolean isFood(String name) { return FOOD.matcher(name.toLowerCase(Locale.ROOT)).find(); }
    static boolean isNonFood(String name) { return NON_FOOD.matcher(name.toLowerCase(Locale.ROOT)).find(); }

    private FoodWords() {}
}
