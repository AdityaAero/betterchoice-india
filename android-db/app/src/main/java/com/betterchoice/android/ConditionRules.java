package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a label means for a physiological state or condition, from statements the label itself must
 * carry and from additive codes. Each rule cites its evidence entry; none is a clinical judgement.
 * <ul>
 *   <li>Pregnancy and breastfeeding: the FSSAI Schedule II warnings for aspartame, acesulfame K,
 *       MSG (pregnancy) and plant stanols, and any "not recommended for ... pregnant" statement;
 *       potassium salt substitutes fall outside WHO's recommendation for pregnant women. Caffeine is
 *       said with its amount by {@link CaffeineLactose}.</li>
 *   <li>Kidney conditions: potassium salt substitutes are outside WHO's recommendation for people with
 *       kidney impairment; phosphate additives are left out, as KDIGO 2017 (4.1.8) advises weighing
 *       phosphate additives in dietary advice for CKD stages 3a to 5D.</li>
 *   <li>Phenylketonuria: aspartame, by FSSAI's mandatory warning.</li>
 * </ul>
 */
final class ConditionRules {
    static final class Verdict {
        final List<String> vetoes = new ArrayList<>(), cautions = new ArrayList<>();
        boolean fits() { return vetoes.isEmpty(); }
    }

    private static final Pattern NOT_FOR_PREGNANT = Pattern.compile(
        "not\\s+recommended\\s+for[^.]{0,120}?(pregnant|lactating)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STANOLS = Pattern.compile("plant\\s+(stanol|sterol)|phytosterol|phytostanol",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern POTASSIUM_SALT = Pattern.compile("potassium\\s+chloride|low[\\s-]+sodium\\s+salt",
        Pattern.CASE_INSENSITIVE);
    static final String[] PHOSPHATE_CODES = {"338", "339", "340", "341", "343", "450", "451", "452", "541"};

    /**
     * @param trimester    1-3 when pregnant, -1 pregnant with stage unknown, 0 not pregnant
     * @param breastfeeding true while breastfeeding
     */
    static Verdict check(String ingredients, String labelText, int trimester, boolean breastfeeding,
                         boolean kidney, boolean phenylketonuria) {
        Verdict v = new Verdict();
        boolean pregnant = trimester != 0;
        String label = (ingredients == null ? "" : ingredients) + "\n" + (labelText == null ? "" : labelText);
        List<String> codes = codes(ingredients);
        if (pregnant || breastfeeding) {
            String who = pregnant ? "pregnancy" : "breastfeeding";
            if (codes.contains("951")) v.vetoes.add("aspartame (INS 951): FSSAI's label warning says not for pregnant or lactating mothers");
            if (codes.contains("950")) v.vetoes.add("acesulfame K (INS 950): FSSAI's label warning says not for pregnant or lactating mothers");
            if (codes.contains("962")) v.vetoes.add("aspartame-acesulfame salt (INS 962): FSSAI's label warning says not for pregnant or lactating mothers");
            if (STANOLS.matcher(label).find()) v.vetoes.add("plant stanols: FSSAI's label warning for pregnant and lactating women");
            if (NOT_FOR_PREGNANT.matcher(label).find() && v.vetoes.isEmpty()) v.vetoes.add("the label says it is not recommended during " + who);
            if (pregnant && POTASSIUM_SALT.matcher(label).find()) v.cautions.add("potassium salt substitute: WHO's recommendation does not cover pregnancy");
        }
        if (pregnant && codes.contains("621")) v.vetoes.add("MSG (INS 621): FSSAI's label warning says not for pregnant women");
        if (kidney) {
            if (codes.contains("508") || POTASSIUM_SALT.matcher(label).find())
                v.vetoes.add("potassium salt substitute: WHO's recommendation excludes kidney impairment");
            for (String code : PHOSPHATE_CODES) {
                if (codes.contains(code)) {
                    v.vetoes.add("phosphate additive (INS " + code + "): KDIGO's kidney guideline advises weighing phosphate additives in your diet");
                    break;
                }
            }
        }
        if (phenylketonuria && (codes.contains("951") || codes.contains("962") || label.toLowerCase(Locale.ROOT).contains("phenylalanine")))
            v.vetoes.add("aspartame: FSSAI's label warning says not for phenylketonurics");
        return v;
    }

    /** Declared INS codes, canonical and without the sub-letter ("451i" → "451"). */
    static List<String> codes(String ingredients) {
        List<String> out = new ArrayList<>();
        if (ingredients == null || ingredients.isBlank()) return out;
        for (IngredientParser.Additive additive : IngredientParser.parse(ingredients).additives) {
            String code = IngredientParser.canonicalCode(additive.code);
            String base = code.replaceFirst("(?<=\\d)[a-z]+$", "");
            if (!out.contains(code)) out.add(code);
            if (!out.contains(base)) out.add(base);
        }
        return out;
    }

    private ConditionRules() {}
}
