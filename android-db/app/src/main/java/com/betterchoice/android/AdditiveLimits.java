package com.betterchoice.android;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceptable daily intakes and regulatory notes for additives a shopper is likely to ask about. Every
 * figure is an {@link Evidence} entry: JECFA from the WHO database page for the substance, EFSA from
 * the abstract of its opinion. Where EFSA's figure could not be retrieved, only JECFA's is shown.
 *
 * <p>An ADI is milligrams per kilogram of body weight per day, over a lifetime. Indian labels declare
 * an additive but never how much, so the card says "contains X; the amount is not declared" and gives
 * the shopper's own ADI for scale. It never says a product is within or below a safe limit.
 */
final class AdditiveLimits {
    static final String VERSION = "adi-registry-2026-10";

    static final class Record {
        final String code, name;
        /** Evidence ids, or null: JECFA's numerical ADI, JECFA's "not specified", EFSA's ADI. */
        final String jecfaId, efsaId;
        final boolean jecfaNotSpecified;
        /** Evidence ids for regulatory notes, each with the sentence the card shows. */
        final Map<String, String> notes;
        Record(String code, String name, String jecfaId, boolean jecfaNotSpecified, String efsaId, Map<String, String> notes) {
            this.code = code; this.name = name; this.jecfaId = jecfaId; this.jecfaNotSpecified = jecfaNotSpecified;
            this.efsaId = efsaId; this.notes = notes;
        }
        BigDecimal jecfa() { return jecfaId == null ? null : Evidence.d(jecfaId); }
        BigDecimal efsa() { return efsaId == null ? null : Evidence.d(efsaId); }
    }

    private static final String SOUTHAMPTON = "In the EU, a food with this colour must carry the warning "
        + "\"may have an adverse effect on activity and attention in children\".";
    private static final Map<String, Record> RECORDS = new LinkedHashMap<>();

    private static Map<String, String> note(String evidenceId, String sentence) {
        Map<String, String> m = new LinkedHashMap<>();
        Evidence.get(evidenceId);   // fails at class load if the note has no evidence
        m.put(evidenceId, sentence);
        return m;
    }

    private static void add(String code, String name, String jecfaId, boolean notSpecified, String efsaId, Map<String, String> notes) {
        RECORDS.put(code, new Record(code, name, jecfaId, notSpecified, efsaId, notes == null ? Collections.emptyMap() : notes));
    }

    static {
        add("951", "aspartame", "jecfa.adi.951", false, null,
            note("fssai.aspartame.warning", "FSSAI requires the label to say: not recommended for phenylketonurics, children with seizure disorders, or pregnant and lactating mothers."));
        add("950", "acesulfame potassium", "jecfa.adi.950", false, "efsa.adi.950",
            note("fssai.acesulfame.warning", "FSSAI requires the label to say: not recommended for children, or pregnant and lactating mothers."));
        add("954", "saccharin", "jecfa.adi.954", false, "efsa.adi.954", null);
        add("955", "sucralose", "jecfa.adi.955", false, "efsa.adi.955", null);
        add("961", "neotame", "jecfa.adi.961", false, "efsa.adi.961", null);
        add("620", "glutamic acid", null, true, "efsa.adi.621", null);
        add("621", "monosodium glutamate", null, true, "efsa.adi.621",
            note("fssai.msg.warning", "FSSAI requires the label to say: not recommended for infants below 12 months and pregnant women."));
        add("622", "monopotassium glutamate", null, true, "efsa.adi.621", null);
        // Benzoates: one JECFA group ADI (2021), expressed as benzoic acid.
        add("210", "benzoic acid", "jecfa.adi.210", false, null, null);
        add("211", "sodium benzoate", "jecfa.adi.210", false, null, null);
        add("212", "potassium benzoate", "jecfa.adi.210", false, null, null);
        add("213", "calcium benzoate", "jecfa.adi.210", false, null, null);
        add("319", "TBHQ", "jecfa.adi.319", false, null, null);
        add("320", "BHA", "jecfa.adi.320", false, null, null);
        add("321", "BHT", "jecfa.adi.321", false, "efsa.adi.321", null);
        for (String[] colour : new String[][]{{"102", "tartrazine"}, {"104", "quinoline yellow"}, {"110", "sunset yellow FCF"},
                {"122", "carmoisine"}, {"124", "ponceau 4R"}, {"129", "allura red"}}) {
            add(colour[0], colour[1], null, false, null, note("eu.colours.warning", SOUTHAMPTON));
        }
        add("171", "titanium dioxide", null, false, null,
            note("efsa.tio2.not.safe", "EFSA concluded in 2021 that it can no longer be considered safe as a food additive."));
        add("216", "propylparaben", null, false, null,
            note("eu.paraben.216.deleted", "Removed from the EU's permitted food additives (Directive 2006/52/EC)."));
        add("924a", "potassium bromate", null, false, null,
            note("fssai.bromate.not.allowed", "FSSAI: not allowed as an additive in any food since 2016."));
    }

    static Record find(String code) {
        if (code == null) return null;
        String canonical = IngredientParser.canonicalCode(code);
        Record exact = RECORDS.get(canonical);
        return exact != null ? exact : RECORDS.get(canonical.replaceFirst("(?<=\\d)[a-z]+$", ""));
    }

    static Map<String, Record> all() { return Collections.unmodifiableMap(RECORDS); }

    /** One line per declared additive with a record: what it is, its ADI for this weight, and that the amount is unknown. */
    static List<String> lines(List<IngredientParser.Additive> additives, Double weightKg) {
        List<String> lines = new ArrayList<>();
        if (additives == null) return lines;
        for (IngredientParser.Additive additive : additives) {
            Record r = find(additive.code);
            if (r == null) continue;
            StringBuilder line = new StringBuilder("Contains " + r.name + " (INS " + IngredientParser.printedCode(additive.code) + ")");
            BigDecimal jecfa = r.jecfa(), efsa = r.efsa();
            BigDecimal adi = jecfa != null ? jecfa : efsa;
            if (adi != null) {
                line.append(": acceptable daily intake ").append(plain(adi)).append(" mg per kg a day (")
                    .append(jecfa != null ? "JECFA" : "EFSA; JECFA set no number");
                if (jecfa != null && efsa != null && efsa.compareTo(jecfa) != 0) line.append("; EFSA ").append(plain(efsa));
                line.append(")");
                if (weightKg != null) {
                    NutritionCalculator.Result perDay = NutritionCalculator.adiPerDay(adi, BigDecimal.valueOf(weightKg));
                    line.append(", about ").append(perDay.shown(1)).append(" mg a day at your ").append(plain(BigDecimal.valueOf(weightKg))).append(" kg");
                }
                line.append(". The pack doesn't say how much it contains, so no exposure is estimated.");
            } else if (r.jecfaNotSpecified) {
                line.append(": JECFA set no numerical limit (\"not specified\").");
            } else {
                line.append(".");
            }
            for (String sentence : r.notes.values()) line.append(" ").append(sentence);
            lines.add(line.toString());
        }
        return lines;
    }

    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private AdditiveLimits() {}
}
