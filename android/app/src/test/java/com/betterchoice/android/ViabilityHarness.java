package com.betterchoice.android;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Viability across 1,000+ real Indian products (Open Food Facts India, labels from 2025): each product is written into a
 * real recorded Zepto product page in the two ways Zepto prints nutrition ("Energy (kcal) 345.0, Protein (g) 52.0, ..."
 * and "Protein(52.5g), Carbohydrate(31.5g), ..." with no energy), read by the app's own pipeline exactly as the bubble
 * reads a screen, and compared with the label's true figures. For six shoppers (no condition, high blood pressure,
 * diabetes, heart, weight loss, muscle) it records what the card says in dark red and dark green and how many better
 * choices it offers. Runs only when BC_VIABILITY_DIR holds products_2025.json; otherwise it does nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class ViabilityHarness {
    static final String TEMPLATE = "../../../audit/zepto-3.7.1/amul_dark_chocolate_bar_rich_in_cocoa.json";
    static final String[][] PERSONAS = {{"normal", "", "aware"}, {"bp", "hypertension", "aware"}, {"diabetes", "diabetes", "aware"},
        {"heart", "cholesterol", "aware"}, {"loss", "", "loss"}, {"muscle", "", "muscle"}};

    @Test public void measureViability() throws Exception {
        String dir = System.getenv("BC_VIABILITY_DIR");
        if (dir == null || dir.isEmpty() || !new File(dir, "products_2025.json").exists()) return;
        String catalogueJson = new String(Files.readAllBytes(new File("src/main/assets/catalogue/swaps_v1.json").toPath()), StandardCharsets.UTF_8);
        SwapCatalogue catalogue = SwapCatalogue.parse(catalogueJson);
        JSONObject wrapper = new JSONObject();
        wrapper.put("groups", new JSONObject(catalogueJson).getJSONArray("groups"));
        JSONArray raw = new JSONArray(new String(Files.readAllBytes(new File(dir, "products_2025.json").toPath()), StandardCharsets.UTF_8));
        wrapper.put("products", raw);
        SwapCatalogue all = SwapCatalogue.parse(wrapper.toString());
        Map<String, Integer> scans = new HashMap<>();
        for (int i = 0; i < raw.length(); i++) scans.put(raw.getJSONObject(i).optString("gtin"), raw.getJSONObject(i).optInt("scans"));
        Map<String, String> groupNames = new HashMap<>();
        for (SwapCatalogue.Group g : all.groups) groupNames.put(g.id, g.name);
        Map<String, Profile> people = new LinkedHashMap<>();
        for (String[] p : PERSONAS) people.put(p[0], CardLayoutTest.profile(p[1], p[2]));
        String template = new String(Files.readAllBytes(new File(TEMPLATE).toPath()), StandardCharsets.UTF_8);

        File out = new File(dir, "viability");
        out.mkdirs();
        try (PrintWriter csv = new PrintWriter(new File(out, "products.csv"), "UTF-8")) {
            List<String> header = new ArrayList<>(List.of("gtin", "category", "scans", "brand", "name", "true_letter", "true_band",
                "read_state", "read_letter", "read_band", "read_matches", "no_energy_state", "no_energy_letter", "no_energy_matches"));
            for (String[] p : PERSONAS) { header.add(p[0] + "_red"); header.add(p[0] + "_green"); header.add(p[0] + "_choices"); }
            header.add("normal_choice_colours");
            csv.println(String.join(",", header));
            for (SwapCatalogue.Product p : all.products) {
                String group = p.groupId == null || p.groupId.isEmpty() || "null".equals(p.groupId) ? null : p.groupId;
                FoodCategory.Placement placed = FoodCategory.place(p.displayName(), p.beverage, group);
                String category = group != null ? groupNames.get(group)
                    : placed.category != null ? "Other: " + placed.category.name : "Other: unplaced";
                NutriGrade.Result truth = CatalogueCoverageHarness.grade(p);
                GradeResult read = read(page(template, p, zeptoTable(p.per100)));
                String[] noEnergy = {"", ""};
                boolean noEnergyMatch = false;
                String bracketed = zeptoBracketed(p.per100);
                if (bracketed != null) {
                    GradeResult r2 = read(page(template, p, bracketed));
                    noEnergy[0] = r2 == null ? "NONE" : r2.state.name();
                    noEnergy[1] = r2 == null || r2.state != GradeResult.State.GRADED ? "" : String.valueOf(r2.letter);
                    noEnergyMatch = r2 != null && r2.state == GradeResult.State.GRADED && r2.band == truth.band;
                }
                boolean graded = read != null && read.state == GradeResult.State.GRADED;
                List<String> row = new ArrayList<>(List.of(q(p.gtin), q(category), String.valueOf(scans.getOrDefault(p.gtin, 0)),
                    q(p.brand), q(p.name), String.valueOf(truth.letter()), truth.band.name(),
                    read == null ? "NONE" : read.state.name(), graded ? String.valueOf(read.letter) : "", graded ? read.band.name() : "",
                    String.valueOf(graded && read.band == truth.band), noEnergy[0], noEnergy[1], bracketed == null ? "" : String.valueOf(noEnergyMatch)));
                String colours = "";
                for (Map.Entry<String, Profile> e : people.entrySet()) {
                    if (!graded) { row.add(""); row.add(""); row.add(""); continue; }
                    RecommendationContext person = RecommendationContext.of(e.getValue());
                    CardFacts facts = CardFacts.of(read, person);
                    row.add(q(join(facts.bad)));
                    row.add(q(join(facts.good)));
                    SwapEngine.Outcome o = SwapEngine.recommend(catalogue, read.page.name, read.nutrition, read.band, read.letter,
                        read.drinkKind != null, SafetyProfile.fromProfile(e.getValue()), null, true, e.getValue());
                    row.add(String.valueOf(o.group == null ? -1 : o.swaps.size()));
                    if ("normal".equals(e.getKey())) {
                        StringBuilder c = new StringBuilder();
                        for (SwapEngine.Swap s : o.swaps) c.append(s.band.name());
                        colours = c.toString();
                    }
                }
                row.add(colours);
                csv.println(String.join(",", row));
            }
        }
    }

    /** Zepto's usual nutrition line: units in brackets after each label, as on "Energy (kcal) 521.0, Protein (g) 6.4, ...". */
    static String zeptoTable(Nutrition n) {
        List<String> parts = new ArrayList<>();
        add(parts, "Energy (kcal)", n.energyKcal);
        add(parts, "Protein (g)", n.proteinG);
        add(parts, "Carbohydrates (g)", n.carbohydrateG);
        add(parts, "Total Sugars (g)", n.sugarsG);
        add(parts, "Added Sugars (g)", n.addedSugarsG);
        add(parts, "Dietary Fibre (g)", n.fibreG);
        add(parts, "Total Fat (g)", n.totalFatG);
        add(parts, "Saturated Fat (g)", n.saturatedFatG);
        add(parts, "Trans Fat (g)", n.transFatG);
        add(parts, "Sodium (mg)", n.sodiumMg);
        return String.join(", ", parts);
    }

    /** The other way Zepto prints it, with no energy: "Nutrients per 100g Protein(52.5g), ..., Sodium(108mg)". */
    static String zeptoBracketed(Nutrition n) {
        if (n.proteinG == null || n.carbohydrateG == null || n.totalFatG == null) return null;
        List<String> parts = new ArrayList<>();
        parts.add("Protein(" + num(n.proteinG) + "g)");
        parts.add("Carbohydrate(" + num(n.carbohydrateG) + "g)");
        if (n.sugarsG != null) parts.add("Sugar(" + num(n.sugarsG) + "g)");
        if (n.addedSugarsG != null) parts.add("Added Sugar(" + num(n.addedSugarsG) + "g)");
        if (n.fibreG != null) parts.add("Dietary Fibre(" + num(n.fibreG) + "g)");
        parts.add("Fat(" + num(n.totalFatG) + "g)");
        if (n.saturatedFatG != null) parts.add("Saturated Fat(" + num(n.saturatedFatG) + "g)");
        if (n.transFatG != null) parts.add("Trans Fat(" + num(n.transFatG) + "g)");
        if (n.sodiumMg != null) parts.add("Sodium(" + num(n.sodiumMg) + "mg)");
        return "Nutrients per " + (n.beverage ? "100ml " : "100g ") + String.join(", ", parts);
    }

    private static void add(List<String> parts, String label, Double v) { if (v != null) parts.add(label + " " + num(v)); }

    private static String num(double v) { return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", ".0"); }

    /** The recorded Zepto page, with this product's name, pack, ingredients, allergens and nutrition in it. */
    static List<List<ScreenText>> page(String template, SwapCatalogue.Product p, String nutrition) throws Exception {
        JSONArray screens = new JSONObject(template).getJSONArray("screens");
        String name = p.displayName().replace("\n", " ");
        String pack = p.pack == null ? "1 pack" : "1 pack (" + p.packText() + ")";
        String ingredients = p.ingredients == null ? "" : p.ingredients.replace("\n", " ");
        String allergens = p.contains.isEmpty() ? "No allergen information" : "Contains: " + String.join(", ", p.contains);
        List<List<ScreenText>> out = new ArrayList<>();
        for (int i = 0; i < screens.length(); i++) {
            JSONArray items = screens.getJSONArray(i);
            List<ScreenText> screen = new ArrayList<>();
            for (int j = 0; j < items.length(); j++) {
                JSONObject it = items.getJSONObject(j);
                String text = it.getString("text");
                if (text.startsWith("Energy (kcal) 521.0")) text = nutrition;
                else if (text.startsWith("Sugar, Cocoa Solids")) { if (ingredients.isEmpty()) continue; text = ingredients; }
                else if (text.equals("Contains: Added Flavour")) text = allergens;
                else if (text.startsWith("55% cocoa content")) text = "Freshly packed";
                text = text.replace("Amul Dark Chocolate Bar | Rich in Cocoa", name).replace("Dark Chocolate 55%", "Regular")
                    .replace("Dark Chocolate", "Packaged food").replace("1 x Chocolate Bar", "1 x Pack").replace("1 pc (150 g)", pack)
                    .replace("150 g", p.pack == null ? "1 pack" : p.packText());
                screen.add(new ScreenText(text, it.getInt("top"), it.optInt("left", 0), it.optBoolean("clickable")));
            }
            out.add(screen);
        }
        return out;
    }

    /** The bubble's own reading of a page, screen by screen as the shopper scrolls: the last rating it reached. */
    static GradeResult read(List<List<ScreenText>> screens) {
        Map<String, LabelEvidence> remembered = new HashMap<>();
        ProductAnalyzer.Memory memory = new ProductAnalyzer.Memory() {
            @Override public boolean skipped(String key) { return false; }
            @Override public Nutrition userDetails(String key) { return null; }
            @Override public String evidence(String key, String text) {
                LabelEvidence facts = remembered.computeIfAbsent(key, k -> new LabelEvidence());
                facts.observe(text);
                return facts.text() + "\n" + (text == null ? "" : text);
            }
            @Override public String nutritionText(String key, String text) {
                LabelEvidence facts = remembered.get(key);
                return text + "\n" + (facts == null ? "" : facts.panel());
            }
        };
        ProductPage anchor = null;
        GradeResult best = null, last = null;
        for (List<ScreenText> screen : screens) {
            GradeResult r = ProductAnalyzer.analyze(screen, 2392, "com.zeptoconsumerapp", x -> PackScan.EMPTY, null, memory, anchor);
            anchor = r.page;
            if (r.page != null) last = r;
            if (r.state == GradeResult.State.GRADED) best = r;
        }
        return best != null ? best : last;
    }

    private static String join(List<CardFacts.Line> lines) {
        List<String> out = new ArrayList<>();
        for (CardFacts.Line l : lines) out.add(l.text);
        return String.join(" | ", out);
    }

    private static String q(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"").replace('\n', ' ') + "\"";
    }
}
