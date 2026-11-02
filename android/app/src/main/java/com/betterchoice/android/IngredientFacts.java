package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a declared ingredient list says, in plain words: the good (whole grain or millet as the main
 * ingredient, no added sugar, no palm oil) and its counterpart (refined flour or sugar as the main
 * ingredient).
 *
 * <p>Indian labels list ingredients in descending order by weight (FSSAI), so the first ingredient
 * is what the product is mostly made of. Every statement here is read from the list itself; with no
 * list, or a list too short to trust, nothing is said. These facts sit beside the nutrition rating
 * and never change it — the band is decided by the published method alone.
 *
 * <p>The curation assistant ({@code tools/curate_swaps.py}) applies the same rules, so the
 * nutritionist's review sheet and the app always say the same thing.
 */
final class IngredientFacts {
    final List<String> good = new ArrayList<>();
    final List<String> concerns = new ArrayList<>();
    /** Factual label details that are neither praise nor a warning. */
    final List<String> information = new ArrayList<>();
    /** Ordered, de-duplicated INS/E codes read from the declared list. */
    final List<IngredientParser.Additive> additives = new ArrayList<>();
    /**
     * Ingredients of exclusive industrial use found in the list, by kind ("flavourings", "colours"…).
     * These are the markers of the NOVA group 4 definition that Chris van Tulleken's
     * <i>Ultra-Processed People</i> is built on: "at least one ingredient that you wouldn't usually
     * find in a standard home kitchen". Preservatives, acidity regulators and raising agents are not
     * counted: NOVA places foods with only those in group 3, processed but not ultra-processed.
     */
    final List<String> ultraMarkers = new ArrayList<>();
    /** A marker that on its own makes a product ultra-processed: sweeteners, colours, hydrogenated fat… */
    boolean strongMarker;
    /** Partially hydrogenated oil, vanaspati or interesterified fat: the industrial source of trans fat. */
    boolean hydrogenatedFat;
    /** A list was actually read; without one, nothing here says anything. */
    boolean read;

    /** Ultra-processed by the NOVA reading: one strong marker, or two different kinds of weaker ones. */
    boolean ultraProcessed() { return read && (strongMarker || ultraMarkers.size() >= 2); }

    /**
     * Kinds of industrial ingredient, as FSSAI labels name them (class name plus INS number), with
     * whether one alone settles it. Order is the order they are listed on the card.
     */
    private static final Object[][] MARKERS = {
        {"hydrogenated fat", true, new String[] {"partially hydrogenated", "hydrogenated", "vanaspati"}},
        {"industrial fats", true, new String[] {"interesterified", "margarine", "bakery shortening", "shortening"}},
        {"sweeteners", true, new String[] {"sweetener", "sweeteners", "aspartame", "sucralose", "acesulfame",
            "saccharin", "neotame", "steviol", "stevia"}},
        {"colours", true, new String[] {"colour", "colours", "color", "colors", "caramel colour", "food colour",
            "tartrazine", "sunset yellow", "carmoisine", "ponceau"}},
        {"flavour enhancers", true, new String[] {"flavour enhancer", "flavour enhancers", "flavor enhancer",
            "monosodium glutamate", "msg", "disodium inosinate", "disodium guanylate", "ribonucleotide",
            "ribonucleotides", "yeast extract"}},
        {"modified starch", true, new String[] {"modified starch", "modified starches", "maltodextrin",
            "modified maize starch", "modified corn starch"}},
        {"protein isolates", true, new String[] {"protein isolate", "hydrolysed", "hydrolyzed", "caseinate",
            "whey protein concentrate", "soy protein isolate", "protein hydrolysate"}},
        {"flavourings", false, new String[] {"flavour", "flavours", "flavouring", "flavourings", "flavor", "flavors",
            "flavoring", "flavorings", "nature identical", "artificial flavour"}},
        {"emulsifiers", false, new String[] {"emulsifier", "emulsifiers", "emulsifying", "lecithin",
            "mono and diglycerides", "polysorbate"}},
        {"thickeners and gums", false, new String[] {"stabiliser", "stabilisers", "stabilizer", "stabilizers",
            "thickener", "thickeners", "thickening", "gelling agent", "xanthan", "guar gum", "carrageenan",
            "gum arabic", "cellulose gum", "carboxymethyl"}},
        {"industrial sugars", false, new String[] {"invert sugar", "invert syrup", "liquid glucose", "glucose syrup",
            "corn syrup", "high fructose", "dextrose", "fructose syrup"}},
        {"humectants and glazing agents", false, new String[] {"humectant", "humectants", "glazing agent",
            "anticaking", "anti caking", "glycerol", "sorbitol", "polyol"}},
    };

    /** Whole grains, with the name to say. Longer phrases first, so "rolled oats" wins over "oats". */
    private static final String[][] GRAINS = {
        {"rolled oats", "Oats"}, {"oat flour", "Oats"}, {"oats", "Oats"}, {"oat", "Oats"},
        {"whole wheat", "Whole wheat"}, {"wholewheat", "Whole wheat"}, {"atta", "Whole wheat"},
        {"whole grain", "Whole grain"}, {"wholegrain", "Whole grain"}, {"finger millet", "Ragi"}, {"ragi", "Ragi"},
        {"pearl millet", "Bajra"}, {"bajra", "Bajra"}, {"jowar", "Jowar"}, {"sorghum", "Jowar"},
        {"millets", "Millet"}, {"millet", "Millet"}, {"brown rice", "Brown rice"}, {"quinoa", "Quinoa"},
        {"barley", "Barley"}};
    private static final String[] REFINED = {"refined wheat flour", "maida", "refined flour", "enriched flour"};
    private static final String[] ADDED_SUGAR = {"sugar", "sugars", "invert sugar", "invert syrup", "liquid glucose",
        "glucose syrup", "dextrose", "jaggery", "honey", "corn syrup", "fructose", "sucrose", "cane sugar",
        "brown sugar", "glucose"};
    private static final String[] SWEETENERS = {"aspartame", "sucralose", "acesulfame", "saccharin", "stevia",
        "steviol", "neotame", "erythritol", "monk fruit", "mogroside", "sweetener",
        "950", "951", "952", "954", "955", "960", "961", "968"};
    private static final String[] FATS = {"oil", "fat", "fats", "butter", "ghee", "shortening", "vanaspati", "margarine"};

    /** True when the list names a non-sugar sweetener, by name or INS number. */
    static boolean hasSweetener(String ingredients) {
        if (ingredients == null) return false;
        String all = normalize(ingredients);
        for (String s : SWEETENERS) if (all.contains(s)) return true;
        return false;
    }

    /** True when any item, even a one-item list, names a sugar: sugar, jaggery, honey, glucose, syrups. */
    static boolean namesSugar(String ingredients) {
        return ingredients != null && Beverage.containsWord(normalize(ingredients), ADDED_SUGAR);
    }

    /** True when the list declares any added sugar: sugar, jaggery, honey, glucose, syrups. */
    static boolean addsSugar(String ingredients) {
        List<String> items = parts(ingredients);
        return items.size() >= 2 && Beverage.containsWord(normalize(String.join(" ", items)), ADDED_SUGAR);
    }

    private static final String[] WATER = {"water", "carbonated water", "treated water", "purified water",
        "drinking water", "soda water"};
    private static final Pattern FRUIT_SHARE = Pattern.compile("(?i)(\\d{1,3}(?:\\.\\d{1,2})?)\\s*%");
    private static final String[] FRUIT_PART = {"pulp", "juice", "fruit", "concentrate", "puree"};

    static IngredientFacts of(String ingredients, boolean drink) {
        IngredientFacts facts = new IngredientFacts();
        IngredientParser.Result parsed = IngredientParser.parse(ingredients);
        List<String> items = parsed.rawItems();
        facts.additives.addAll(parsed.additives);
        boolean single = singleIngredient(items);
        if (items.size() < 2 && !single) return facts;      // too short to read anything from
        facts.read = true;
        if (single) {
            // One plain ingredient is as unprocessed as packaged food gets (NOVA group 1).
            facts.good.add("Just one ingredient: " + items.get(0).toLowerCase(Locale.ROOT));
        }
        processing(items, facts);
        if (drink) drinkFacts(items, facts);
        String first = normalize(items.get(0));
        String grain = null;
        for (String[] g : GRAINS) if (Beverage.containsWord(first, new String[] {g[0]})) { grain = g[1]; break; }
        if (Beverage.containsWord(first, REFINED)) facts.concerns.add("Refined flour (maida) is the main ingredient");
        else if (grain != null) facts.good.add(grain + ("Oats".equals(grain) ? " are" : " is") + " the main ingredient");
        if (!drink && Beverage.containsWord(first, ADDED_SUGAR)) facts.concerns.add("Sugar is the main ingredient");
        String all = normalize(String.join(" ", items));
        boolean sugar = Beverage.containsWord(all, ADDED_SUGAR);
        boolean sweetener = false;
        for (String s : SWEETENERS) if (all.contains(s)) { sweetener = true; break; }
        // A partial/OCR ingredient list cannot prove the absence of sugar or sweeteners.
        if (!drink && Beverage.containsWord(all, FATS) && !all.contains("palm")) facts.good.add("No palm oil");
        Double cocoa = cocoaShare(String.join(", ", items));
        if (cocoa != null) facts.information.add("Cocoa solids: " + Nutrition.number(cocoa) + "%, as the label states");
        addAdditiveInformation(facts);
        return facts;
    }

    /**
     * "Cocoa solids 55%", "Cocoa Solids (Cocoa Mass, Cocoa Butter) 70%", "70% cocoa". The share a
     * dark chocolate is chosen by. Cocoa butter alone is not cocoa solids, and nothing is guessed.
     */
    private static final Pattern COCOA = Pattern.compile(
        "(?i)(?:cocoa solids|cocoa mass|cocoa liquor|cocoa content|minimum cocoa)[^0-9%\\n]{0,40}?(\\d{1,3}(?:\\.\\d)?)\\s*%"
        + "|(\\d{1,3}(?:\\.\\d)?)\\s*%\\s*cocoa(?! butter)");

    static Double cocoaShare(String ingredients) {
        if (ingredients == null) return null;
        Matcher m = COCOA.matcher(ingredients);
        if (!m.find()) return null;
        double share = Double.parseDouble(m.group(1) != null ? m.group(1) : m.group(2));
        return share > 0 && share <= 100 ? share : null;
    }

    /** Additive codes are label facts. They never change the A/B/C grade or imply harm. */
    private static void addAdditiveInformation(IngredientFacts facts) {
        if (facts.additives.isEmpty()) return;
        List<String> shown = new ArrayList<>();
        int limit = Math.min(3, facts.additives.size());
        for (int i = 0; i < limit; i++) shown.add(facts.additives.get(i).display());
        String more = facts.additives.size() > limit ? " (+" + (facts.additives.size() - limit) + " more)" : "";
        facts.information.add("Label declares: " + String.join("; ", shown) + more);
    }

    /**
     * How processed the product is, from its ingredients. Said first on the card, because all three
     * references put it first: what the food is made of comes before any single nutrient.
     */
    private static void processing(List<String> items, IngredientFacts facts) {
        String all = normalize(String.join(" ", items));
        for (Object[] marker : MARKERS) {
            String[] words = (String[]) marker[2];
            if (!Beverage.containsWord(all, words)) continue;
            String kind = (String) marker[0];
            if ("hydrogenated fat".equals(kind)) {
                // "Non-hydrogenated" and "no hydrogenated fat" are claims, not ingredients.
                if (all.matches(".*\\b(?:non|no|free from|without) hydrogenated.*") && !all.contains("vanaspati")) continue;
                facts.hydrogenatedFat = true;
            }
            facts.ultraMarkers.add(kind);
            if ((Boolean) marker[1]) facts.strongMarker = true;
        }
        if (facts.hydrogenatedFat) facts.concerns.add("Hydrogenated fat (vanaspati), the industrial source of trans fat");
        if (facts.ultraProcessed()) {
            List<String> named = new ArrayList<>(facts.ultraMarkers);
            named.remove("hydrogenated fat");      // already said on its own line, first
            if (named.isEmpty()) named.add("hydrogenated fat");
            facts.concerns.add("Ultra-processed: contains " + String.join(", ", named.subList(0, Math.min(3, named.size()))));
        }
    }

    /**
     * Drinks are mostly water, so what matters is what comes after it. "Water, Sugar, Mango Pulp
     * (11.2%)" is a sugared drink with a little fruit in it, whatever the shop calls it.
     */
    private static void drinkFacts(List<String> items, IngredientFacts facts) {
        int i = 0;
        while (i < items.size() && Beverage.containsWord(normalize(items.get(i)), WATER)
               && normalize(items.get(i)).split(" ").length <= 3) i++;
        if (i > 0 && i < items.size() && Beverage.containsWord(normalize(items.get(i)), ADDED_SUGAR)
            && !Beverage.containsWord(normalize(items.get(i)), FRUIT_PART)) {
            facts.concerns.add("Sugar is the main ingredient after water");
        }
        for (String item : items) {
            String lower = normalize(item);
            if (!Beverage.containsWord(lower, FRUIT_PART)) continue;
            Matcher m = FRUIT_SHARE.matcher(item);
            if (!m.find()) continue;
            double share = Double.parseDouble(m.group(1));
            String what = item.replaceAll("\\(.*", "").trim().toLowerCase(Locale.ROOT);
            if (share > 0 && share < 50) facts.concerns.add("Only " + Nutrition.number(share) + "% fruit (" + what + ")");
            break;
        }
    }

    /** The ingredient list split at top-level commas; "Emulsifier (322, 471)" stays one item. */
    static List<String> parts(String ingredients) {
        List<String> out = new ArrayList<>();
        if (ingredients == null) return out;
        String text = ingredients.trim().replaceFirst("(?i)^\\s*ingredients?\\s*[:\\-]?\\s*", "");
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == '(' || c == '[') depth++;
            else if ((c == ')' || c == ']') && depth > 0) depth--;
            if ((c == ',' || c == ';') && depth == 0) {
                if (current.toString().trim().length() > 0) out.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.toString().trim().length() > 0) out.add(current.toString().trim());
        // An unclosed bracket ("WHEAT FLOUR (ATTA(63%), SUGAR, …") swallows the whole list into one
        // item. Typed and photographed lists have these slips; split at the commas instead.
        if (out.size() == 1 && depth > 0 && text.split(",").length >= 3) {
            out.clear();
            for (String piece : text.split("[,;]")) if (piece.trim().length() > 0) out.add(piece.trim());
        }
        return out;
    }

    /** "Rolled Oats": one plain ingredient, a few words, no numbers, no additive. */
    private static boolean singleIngredient(List<String> items) {
        if (items.size() != 1) return false;
        String item = items.get(0);
        String lower = item.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\b(cholesterol|sodium|energy|protein|carbohydrate|nutrition|allergen)\\b.*")) return false;
        return item.split("\\s+").length <= 5 && !item.matches(".*\\d.*") && item.length() >= 3;
    }

    /**
     * The row names shop pages print after "Ingredients". Each is the start of another row, never
     * part of the list: Zepto's "Jumbo Rolled Oats" was followed by "Unit / 1 pack (400 g)", and the
     * three were read as one ingredient, so a single-ingredient oat pack said nothing about itself.
     */
    private static final Pattern ROW_KEY = Pattern.compile("(?i)^(?:flavou?r|packaging(?: type)?|brand|type|"
        + "weight|net (?:weight|quantity|qty)|serve size|serving size|unit|item included|item form|dietary preference|"
        + "diet type|variant|product type|key features|allergen information|allergens?|fssai licen[cs]e(?: number)?|"
        + "storage instructions?|shelf life|country of origin|manufacturer(?: details| name| address)?|"
        + "marketed by|seller(?: name)?|return policy|expiry date|customer care(?: details)?|description|"
        + "disclaimer|cuisine|spice level|quantity|pack of|size|no\\. of (?:pieces|units)|speciality|"
        + "nutrition(?:al)? (?:information|facts|info)|highlights|information|directions(?: to use| for use)?|"
        + "how to (?:use|prepare|make)|preparation(?: method)?|usage|instructions?(?: for use)?)(?:\\s*[:\\-].*|\\s*)$");

    /** Where a label's ingredient list starts; it runs until the next section of the label. */
    /** A line that is only the heading, as shop pages print the "Ingredients" row. */
    private static final Pattern KEY_LINE = Pattern.compile(
        "(?im)^[ \\t]*(?:ingredients?|ingredient list|list of ingredients)[ \\t]*:?[ \\t]*$");
    /** "Ingredients: …" inline, as packs print it. */
    private static final Pattern INLINE = Pattern.compile("(?i)(?<![a-z])ingredients?[ \\t]*[:\\-][ \\t]*");
    private static final Pattern END = Pattern.compile("(?i)\\n\\s*(?:nutrition|nutritional|allergen|contains\\s*:|"
        + "may contain|fssai|manufactured|marketed|packed by|storage|store in|best before|shelf life|net (?:qty|quantity|wt|weight)|"
        + "customer care|country of origin|disclaimer|key features|description)");

    /**
     * The ingredient list out of a whole product page or pack photo, or null when there is none.
     * Shop pages label it "Ingredients"; the list ends where the next labelled section begins.
     */
    static String section(String labelText) {
        if (labelText == null) return null;
        // A line that is only the heading (a shop page's "Ingredients" row) wins over the word in a
        // sentence: Zepto shows "Colour comes from ingredients" above the real list.
        Matcher start = KEY_LINE.matcher(labelText);
        boolean row = start.find();
        if (!row) {
            start = INLINE.matcher(labelText);
            if (!start.find()) return null;
        }
        String rest = labelText.substring(start.end()).replaceFirst("^\\s+", "");
        Matcher end = END.matcher(rest);
        boolean delimited = end.find();
        String block = delimited ? rest.substring(0, end.start()) : rest;
        // The list is its own row. The next line joins it only when it plainly continues the list
        // (the row ended on a comma or an open bracket, or the line starts in lower case, as a pack
        // photo's wrapped lines do). Otherwise the next row — "Flavour: Mango", "Packaging Type" — is
        // not read as ingredients, where "Flavour" would count as a flavouring.
        String[] lines = block.split("\n");
        StringBuilder list = new StringBuilder(lines[0].trim());
        for (int i = 1; i < lines.length && list.length() < 700; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) break;
            if (ROW_KEY.matcher(line).matches()) break;
            String sofar = list.toString();
            // Before a known end marker, a wrapped line joins only when it reads as part of a list
            // (an ingredient list is comma-separated, bracketed and percentaged). A screen control
            // or a heading between the list and the marker — "Back", "Search", "Share" — never does,
            // whatever it says: decided by the line's shape, not by a list of words.
            boolean listLike = line.indexOf(',') >= 0 || line.indexOf('(') >= 0 || line.indexOf('%') >= 0
                || line.indexOf(';') >= 0;
            boolean continues = sofar.endsWith(",") || sofar.endsWith("(") || sofar.endsWith("&")
                || sofar.toLowerCase(Locale.ROOT).endsWith(" and") || Character.isLowerCase(line.charAt(0))
                || line.startsWith("(") || openBrackets(sofar) > 0 || (delimited && listLike);
            if (!continues) break;
            list.append(' ').append(line);
        }
        String text = list.toString().trim();
        if (text.length() > 700) text = text.substring(0, 700);
        // A shop row's value may be one short word ("Tea", "Oats"); inline text needs more to trust.
        return text.length() < (row ? 3 : 8) ? null : text;
    }

    private static int openBrackets(String text) {
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == '(' || c == '[') depth++;
            else if ((c == ')' || c == ']') && depth > 0) depth--;
        }
        return depth;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}&%]+", " ").trim();
    }

    private IngredientFacts() {}
}
