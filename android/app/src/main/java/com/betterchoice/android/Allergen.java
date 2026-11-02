package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The allergen groups FSSAI requires a packaged food to declare, and how to spot them in a label.
 *
 * <p>FSSAI groups peanuts with tree nuts. This splits them, because a peanut allergy and a tree nut
 * allergy are different conditions and someone who reacts to one may eat the other safely. Merging
 * them would either block food a person can eat, or, far worse, let through food they cannot.
 *
 * <p>Two detectors run over every label, because they fail in different ways:
 * <ul>
 *   <li>the pack's own <b>"Contains"</b> and <b>"May contain"</b> declaration, which is the
 *       manufacturer's statement and the most authoritative thing on the pack;</li>
 *   <li>the <b>ingredient list</b>, which catches an allergen the declaration forgot.</li>
 * </ul>
 * Neither is trusted to rule an allergen <i>out</i>. Silence is not evidence of absence.
 */
enum Allergen {
    GLUTEN("gluten", "Cereals containing gluten",
        "wheat", "atta", "maida", "suji", "semolina", "rava", "barley", "rye", "oats", "oat",
        "spelt", "kamut", "malt", "seitan", "gluten", "daliya"),
    MILK("milk", "Milk",
        "milk", "milk solids", "skimmed milk", "milk powder", "butter", "butter oil", "ghee",
        "cheese", "paneer", "curd", "yoghurt", "yogurt", "dahi", "cream", "whey", "casein",
        "caseinate", "lactose", "khoya", "mawa", "malai"),
    EGG("egg", "Egg", "egg", "eggs", "albumen", "egg white", "egg yolk", "ovalbumin", "lysozyme"),
    FISH("fish", "Fish", "fish", "anchovy", "anchovies", "tuna", "salmon", "cod", "sardine",
        "fish oil", "surimi", "bombil"),
    CRUSTACEA("crustacea", "Crustacea", "prawn", "prawns", "shrimp", "crab", "lobster", "crayfish",
        "krill", "crustacean"),
    PEANUT("peanut", "Peanuts", "peanut", "peanuts", "groundnut", "groundnuts", "mungfali",
        "arachis", "peanut butter"),
    TREE_NUT("tree_nut", "Tree nuts", "almond", "almonds", "badam", "cashew", "cashews", "kaju",
        "walnut", "walnuts", "akhrot", "pistachio", "pista", "hazelnut", "pecan", "macadamia",
        "brazil nut", "tree nut", "tree nuts"),
    SOY("soy", "Soybeans", "soy", "soya", "soybean", "soya bean", "soybeans", "soy lecithin",
        "soya lecithin", "tofu", "edamame"),
    SULPHITE("sulphite", "Sulphites", "sulphite", "sulfite", "sulphur dioxide", "sulfur dioxide",
        "ins 220", "ins 221", "ins 222", "ins 223", "ins 224", "ins 225", "ins 227", "ins 228"),
    SESAME("sesame", "Sesame", "sesame", "til", "tahini", "gingelly");

    final String code;
    final String label;
    private final String[] terms;

    Allergen(String code, String label, String... terms) {
        this.code = code; this.label = label; this.terms = terms;
    }

    static Allergen byCode(String code) {
        for (Allergen a : values()) if (a.code.equals(code)) return a;
        return null;
    }

    /** What one label says about allergens, keeping declared and trace statements apart. */
    static final class Scan {
        /** "Contains: Milk" — the manufacturer says it is in there. */
        final List<Allergen> declared = new ArrayList<>();
        /** "May contain traces of Soy" — cross-contamination, not an ingredient. */
        final List<Allergen> traces = new ArrayList<>();
        /** Spotted in the ingredient list but absent from any declaration. */
        final List<Allergen> fromIngredients = new ArrayList<>();
        /** True when the label gave nothing to work with, which is not the same as "none". */
        boolean noInformation;
        /**
         * Wheat itself, for a wheat allergy, as opposed to every gluten cereal (coeliac disease).
         * Barley, rye and oats are a coeliac concern, not a wheat-allergy one.
         */
        boolean wheatIngredient, wheatTrace;

        boolean mentions(Allergen allergen) {
            return declared.contains(allergen) || traces.contains(allergen) || fromIngredients.contains(allergen);
        }

        /** Present as an ingredient, as opposed to a possible trace. */
        boolean containsAsIngredient(Allergen allergen) {
            return declared.contains(allergen) || fromIngredients.contains(allergen);
        }

        List<Allergen> all() {
            List<Allergen> everything = new ArrayList<>(declared);
            for (Allergen a : fromIngredients) if (!everything.contains(a)) everything.add(a);
            for (Allergen a : traces) if (!everything.contains(a)) everything.add(a);
            return everything;
        }
    }

    // "ALLERGEN DECLARATION: May Contains traces of Soy, Milk solids, Wheat & Mustard"
    private static final Pattern MAY_CONTAIN = Pattern.compile(
        "may\\s+contains?\\s*(?:traces\\s*(?:of)?)?\\s*[:\\-]?\\s*([^.\\n]{0,200})");
    private static final Pattern CONTAINS = Pattern.compile(
        "(?<!may\\s)(?<!may\\scontain\\s)\\bcontains\\s*[:\\-]\\s*([^.\\n]{0,200})");

    /**
     * Reads a label. `ingredients` is the ingredient statement; `allergenText` is any separate
     * allergen declaration. Either may be null — a pack often prints only one of them.
     */
    static Scan scan(String ingredients, String allergenText) {
        Scan scan = new Scan();
        String ing = lower(ingredients);
        String all = lower(allergenText);
        String combined = (ing + "\n" + all).trim();
        if (combined.isEmpty()) { scan.noInformation = true; return scan; }

        // The trace statement is read first so its terms are not also counted as ingredients.
        String traceText = "";
        Matcher may = MAY_CONTAIN.matcher(combined);
        while (may.find()) traceText += " " + may.group(1);
        for (Allergen a : values()) if (a.mentionedIn(traceText)) scan.traces.add(a);
        scan.wheatTrace = mentions(traceText, WHEAT_TERMS, NO_FALSE_FRIENDS);

        String declaredText = "";
        Matcher contains = CONTAINS.matcher(combined);
        while (contains.find()) declaredText += " " + contains.group(1);
        Matcher row = Pattern.compile("(?m)^allergen(?: information| declaration)?\\s*:?\\s*\\n([^\\n.]{1,200})").matcher(all);
        while (row.find()) {
            String value = row.group(1);
            if (!value.startsWith("may contain")) declaredText += " " + value;
        }
        for (Allergen a : values()) {
            if (a.mentionedIn(declaredText) && !scan.declared.contains(a)) scan.declared.add(a);
        }
        boolean wheatDeclared = mentions(declaredText, WHEAT_TERMS, NO_FALSE_FRIENDS);

        // What is left once every statement is cut out is the ingredient list itself. Each match is
        // removed where it stands: removing the statements as one joined string failed whenever a
        // label (or the page and its remembered evidence) carried two of them, and the trace words
        // were then read as ingredients.
        String rest = CONTAINS.matcher(MAY_CONTAIN.matcher(ing).replaceAll(" ")).replaceAll(" ");
        for (Allergen a : values()) {
            if (a.mentionedIn(rest) && !scan.declared.contains(a)) {
                // An ingredient outranks a trace statement. Wheat flour in the list with "may contain
                // barley" is gluten as an ingredient; keeping it only as a trace let someone with a
                // gluten allergy see a caution where the list itself says it is in there.
                scan.fromIngredients.add(a);
                scan.traces.remove(a);
            }
        }
        scan.wheatIngredient = wheatDeclared || mentions(rest, WHEAT_TERMS, NO_FALSE_FRIENDS);
        if (scan.wheatIngredient) scan.wheatTrace = false;
        scan.noInformation = combined.length() < 12;
        return scan;
    }

    /** Wheat and the wheat products Indian labels name; not barley, rye or oats. */
    static final String[] WHEAT_TERMS = {"wheat", "atta", "maida", "suji", "semolina", "rava", "daliya",
        "spelt", "kamut", "seitan", "durum"};
    private static final String[] NO_FALSE_FRIENDS = {};
    /**
     * Words that contain an allergen's term without being that allergen. Cocoa butter is a plant fat,
     * coconut milk is not dairy: flagging them as milk blocked dark chocolate for vegans.
     */
    private static final String[] MILK_FALSE_FRIENDS = {"cocoa butter", "peanut butter", "nut butter", "almond butter",
        "cashew butter", "shea butter", "kokum butter", "mango butter", "coconut milk", "coconut cream",
        "almond milk", "oat milk", "soy milk", "soya milk", "rice milk", "cream of tartar", "milk thistle"};

    private boolean mentionedIn(String text) {
        return mentions(text, terms, this == MILK ? MILK_FALSE_FRIENDS : NO_FALSE_FRIENDS);
    }

    /** Whole words only: "milk" must not fire on "milkweed", nor "oat" on "coat". */
    static boolean mentions(String text, String[] terms, String[] falseFriends) {
        if (text == null || text.isBlank()) return false;
        String cleaned = text;
        for (String friend : falseFriends) cleaned = cleaned.replace(friend, " ");
        for (String term : terms) {
            Matcher m = Pattern.compile("(?<![a-z])" + Pattern.quote(term) + "(?![a-z])").matcher(cleaned);
            if (m.find()) return true;
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace((char) 0xA0, ' ');
    }
}
