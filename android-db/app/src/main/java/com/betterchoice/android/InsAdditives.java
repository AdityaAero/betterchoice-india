package com.betterchoice.android;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small, versioned lookup for additive codes commonly seen on Indian packaged-food labels.
 *
 * <p>The code printed on a label is the evidence. This table only expands that code to a familiar
 * name and functional class. It does not decide whether an additive is permitted for a particular
 * product category, safe for a particular person, or evidence that the product is healthy.
 * Category permissions and limits belong in the separately reviewed FSSAI rules module.</p>
 *
 * <p>Names are aligned to the FSSAI Food Products Standards and Food Additives compendium. Unknown
 * codes remain visible as codes instead of being guessed.</p>
 */
final class InsAdditives {
    static final String VERSION = "fssai-fpsfa-common-2025-05-07";
    static final String SOURCE = "FSSAI Food Products Standards and Food Additives compendium";

    static final class Entry {
        final String code;
        final String name;
        final String function;

        Entry(String code, String name, String function) {
            this.code = code;
            this.name = name;
            this.function = function;
        }
    }

    private static final Map<String, Entry> ENTRIES;
    static {
        Map<String, Entry> entries = new LinkedHashMap<>();
        add(entries, "102", "tartrazine", "colour");
        add(entries, "110", "sunset yellow FCF", "colour");
        add(entries, "122", "carmoisine", "colour");
        add(entries, "124", "ponceau 4R", "colour");
        add(entries, "129", "allura red AC", "colour");
        add(entries, "133", "brilliant blue FCF", "colour");
        add(entries, "140", "chlorophylls", "colour");
        add(entries, "150d", "sulphite ammonia caramel", "colour");
        add(entries, "171", "titanium dioxide", "colour");
        add(entries, "170i", "calcium carbonate", "acidity regulator / anticaking agent");
        add(entries, "202", "potassium sorbate", "preservative");
        add(entries, "210", "benzoic acid", "preservative");
        add(entries, "211", "sodium benzoate", "preservative");
        add(entries, "212", "potassium benzoate", "preservative");
        add(entries, "213", "calcium benzoate", "preservative");
        add(entries, "223", "sodium metabisulphite", "preservative");
        add(entries, "224", "potassium metabisulphite", "preservative");
        add(entries, "234", "nisin", "preservative");
        add(entries, "290", "carbon dioxide", "packaging gas");
        add(entries, "307b", "alpha-tocopherol", "antioxidant");
        add(entries, "319", "tert-butylhydroquinone (TBHQ)", "antioxidant");
        add(entries, "320", "butylated hydroxyanisole (BHA)", "antioxidant");
        add(entries, "321", "butylated hydroxytoluene (BHT)", "antioxidant");
        add(entries, "322", "lecithins", "emulsifier");
        add(entries, "322i", "lecithin", "emulsifier");
        add(entries, "330", "citric acid", "acidity regulator");
        add(entries, "331", "sodium citrates", "acidity regulator");
        add(entries, "334", "tartaric acid", "acidity regulator");
        add(entries, "338", "phosphoric acid", "acidity regulator");
        add(entries, "339i", "monosodium phosphate", "acidity regulator");
        add(entries, "339ii", "disodium phosphate", "acidity regulator");
        add(entries, "340", "potassium phosphates", "acidity regulator");
        add(entries, "407", "carrageenan", "thickener / stabiliser");
        add(entries, "412", "guar gum", "thickener / stabiliser");
        add(entries, "415", "xanthan gum", "thickener / stabiliser");
        add(entries, "418", "gellan gum", "thickener / stabiliser");
        add(entries, "422", "glycerol", "humectant");
        add(entries, "435", "polysorbate 60", "emulsifier");
        add(entries, "440", "pectins", "gelling agent");
        add(entries, "450i", "disodium diphosphate", "raising agent / stabiliser");
        add(entries, "451i", "pentasodium triphosphate", "stabiliser");
        add(entries, "452", "polyphosphates", "stabiliser");
        add(entries, "466", "sodium carboxymethyl cellulose", "thickener / stabiliser");
        add(entries, "471", "mono- and diglycerides of fatty acids", "emulsifier");
        add(entries, "472e", "diacetyltartaric and fatty acid esters of glycerol", "emulsifier");
        add(entries, "500i", "sodium carbonate", "raising agent");
        add(entries, "500ii", "sodium hydrogen carbonate", "raising agent");
        add(entries, "501i", "potassium carbonate", "raising agent");
        add(entries, "503i", "ammonium carbonate", "raising agent");
        add(entries, "503ii", "ammonium hydrogen carbonate", "raising agent");
        add(entries, "551", "silicon dioxide", "anticaking agent");
        add(entries, "621", "monosodium L-glutamate", "flavour enhancer");
        add(entries, "627", "disodium guanylate", "flavour enhancer");
        add(entries, "631", "disodium inosinate", "flavour enhancer");
        add(entries, "950", "acesulfame potassium", "sweetener");
        add(entries, "951", "aspartame", "sweetener");
        add(entries, "952", "cyclamates", "sweetener");
        add(entries, "954", "saccharins", "sweetener");
        add(entries, "955", "sucralose", "sweetener");
        add(entries, "960", "steviol glycosides", "sweetener");
        add(entries, "961", "neotame", "sweetener");
        add(entries, "965", "maltitols", "sweetener");
        ENTRIES = Collections.unmodifiableMap(entries);
    }

    static Entry find(String code) {
        if (code == null) return null;
        String canonical = IngredientParser.canonicalCode(code);
        Entry exact = ENTRIES.get(canonical);
        if (exact != null) return exact;
        String base = canonical.replaceFirst("[a-z]+$", "");
        return ENTRIES.get(base);
    }

    private static void add(Map<String, Entry> entries, String code, String name, String function) {
        entries.put(code, new Entry(code, name, function));
    }

    private InsAdditives() {}
}
