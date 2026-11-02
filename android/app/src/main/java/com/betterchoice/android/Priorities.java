package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What matters most, first. The order follows the three references the founder chose:
 * <ul>
 *   <li><b>How processed it is</b> — van Tulleken, <i>Ultra-Processed People</i> (the NOVA system):
 *       what a food is made of comes before any single nutrient.</li>
 *   <li><b>The kind of fat</b> — Willett, <i>Eat, Drink, and Be Healthy</i>: partially hydrogenated
 *       (trans) fat is the one fat to avoid outright; what replaces saturated fat matters.</li>
 *   <li><b>Sugar, then refined grain</b> — Willett and the Nutrition Society textbook (WHO/SACN: free
 *       sugars under 5–10% of energy, fibre at least 30 g a day): sugary drinks and refined starch
 *       before salt.</li>
 *   <li><b>Saturated fat, then sodium, then energy density</b>. Sodium still counts in full inside the
 *       score; it is simply not what the card leads with.</li>
 * </ul>
 * Ingredient facts are ordered on the explanation card but never alter the nutrient grade.
 */
final class Priorities {

    private static final String[][] CONCERN_ORDER = {
        {"Hydrogenated fat", "Trans fat"},
        {"Ultra-processed"},
        {"Added sugar", "High sugar", "Sugar is the main", "Some sugar", "Sweetened without sugar", "Only "},
        {"Refined flour"},
        {"High saturated fat", "Some saturated fat", "Saturated fat"},
        {"High sodium", "Salty", "Some sodium"},
        {"Energy dense", "Fairly energy dense"},
    };

    private static final String[][] GOOD_ORDER = {
        {"Oats are", "Whole wheat is", "Whole grain is", "Ragi is", "Millet is", "Jowar is", "Bajra is",
            "Brown rice is", "Quinoa is", "Barley is", "Mostly nuts", "% nuts", "% fruit"},
        {"High fibre", "Good fibre"},
        {"High protein", "Good protein"},
        {"Mostly unsaturated"},
        {"Just one ingredient", "No ultra-processed"},
        {"No added sugar"},
        {"Practically no sugar", "Low sugar"},
        {"Low saturated fat"},
        {"No palm oil"},
        {"Low sodium"},
    };

    static List<String> concerns(List<String> lines) { return ordered(lines, CONCERN_ORDER, Integer.MAX_VALUE); }

    /** The good side, most important first, at most five lines so it never buries the rating. */
    static List<String> positives(List<String> lines) { return ordered(lines, GOOD_ORDER, 5); }

    private static List<String> ordered(List<String> lines, String[][] order, int max) {
        List<String> sorted = new ArrayList<>();
        for (String line : lines) if (!sorted.contains(line)) sorted.add(line);
        sorted.sort(Comparator.comparingInt(line -> rank(line, order)));   // stable: ties keep their order
        return sorted.size() > max ? new ArrayList<>(sorted.subList(0, max)) : sorted;
    }

    private static int rank(String line, String[][] order) {
        for (int i = 0; i < order.length; i++) {
            for (String start : order[i]) if (line.startsWith(start) || (start.startsWith("%") && line.contains(start))) return i;
        }
        return order.length;
    }

    private Priorities() {}
}
