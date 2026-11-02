package com.betterchoice.android;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every number the calculator uses, with the document it comes from and a verbatim quote.
 *
 * <p>Nothing here is written from memory. Each entry names a file in {@code security/evidence/},
 * where in that file the value is printed, and a short quote containing it.
 * {@code tools/evidence_check.py} opens each file and fails if a quote is not there;
 * {@code EvidenceRegistryTest} fails if a file is missing. Scanned tables are quoted from a machine
 * OCR transcript ({@code tools/ocr_rda_annexure.py}), never from a hand transcription. An entry the
 * sources do not settle is {@link Status#NEEDS_REVIEW} and is not used by the app; the judgement
 * calls a dietitian should review are listed by {@code tools/dietitian_sheet.py}.
 */
final class Evidence {
    static final String VERSION = "evidence-2026-10-01b";

    enum Status { VERIFIED, NEEDS_REVIEW }

    static final class Entry {
        final String id, value, unit, file, locator, quote, note;
        final Status status;
        Entry(String id, String value, String unit, String file, String locator, String quote, Status status, String note) {
            this.id = id; this.value = value; this.unit = unit; this.file = file; this.locator = locator;
            this.quote = quote; this.status = status; this.note = note;
        }
        BigDecimal number() { return new BigDecimal(value); }
    }

    // Source files, relative to the project root.
    static final String FAO = "security/evidence/fao-who-unu-2004-energy.pdf";
    static final String ICMR = "security/evidence/icmr-nin-2020-brief-note.pdf";
    static final String NIN = "security/evidence/research-nin-2024.txt";
    static final String WHO_SUGARS = "security/evidence/who-sugars-2015.pdf";
    static final String WHO_FATS = "security/evidence/who-sfa-tfa-2023.pdf";
    static final String WHO_SODIUM = "security/evidence/who-sodium-2012.pdf";
    static final String WHO_CARBS = "security/evidence/who-carbohydrate-2023.pdf";
    static final String FSSAI_LABEL = "security/evidence/fssai-labelling-2020-v8-2025.pdf";
    static final String FSSAI_2016 = "security/evidence/fssai-additives-operationalisation-2016.pdf";
    static final String ISSN = "security/evidence/issn-protein-2017.json";
    static final String FSSAI_CLAIMS = "security/evidence/fssai-claims-2018-compendium-2022.pdf";
    static final String KDIGO = "security/evidence/kdigo-2017-ckd-mbd.xml";
    static final String WHO_ANC = "security/evidence/who-antenatal-care-2016.pdf";
    static final String LACTOSE = "security/evidence/lactose-review-2010.json";
    static final String RDA_OCR = "security/evidence/reference-rda-annexure-ocr.txt";
    static final String NUTRISCORE = "security/evidence/nutriscore-2022-update-food.pdf";
    static final String JECFA = "security/evidence/jecfa/jecfa-";
    static final String EFSA = "security/evidence/efsa/epmc-";

    private static final Map<String, Entry> ALL = new LinkedHashMap<>();

    /** A value printed in its source; the optional note says how the app applies it. */
    private static void v(String id, String value, String unit, String file, String locator, String quote, String... note) {
        ALL.put(id, new Entry(id, value, unit, file, locator, quote, Status.VERIFIED, note.length == 0 ? null : note[0]));
    }

    private static void review(String id, String value, String unit, String file, String locator, String quote, String why) {
        ALL.put(id, new Entry(id, value, unit, file, locator, quote, Status.NEEDS_REVIEW, why));
    }

    static {
        // --- Basal metabolic rate: FAO/WHO/UNU 2004, Table 5.2 (Schofield), kcal/day = a x kg + b ---
        v("bmr.male.18.a", "15.057", "kcal/kg/day", FAO, "PDF p44, Table 5.2, men 18-30", "15.057kg + 692.2");
        v("bmr.male.18.b", "692.2", "kcal/day", FAO, "PDF p44, Table 5.2, men 18-30", "15.057kg + 692.2");
        v("bmr.male.30.a", "11.472", "kcal/kg/day", FAO, "PDF p44, Table 5.2, men 30-60", "11.472kg + 873.1");
        v("bmr.male.30.b", "873.1", "kcal/day", FAO, "PDF p44, Table 5.2, men 30-60", "11.472kg + 873.1");
        v("bmr.male.60.a", "11.711", "kcal/kg/day", FAO, "PDF p44, Table 5.2, men 60+", "11.711kg + 587.7");
        v("bmr.male.60.b", "587.7", "kcal/day", FAO, "PDF p44, Table 5.2, men 60+", "11.711kg + 587.7");
        v("bmr.female.18.a", "14.818", "kcal/kg/day", FAO, "PDF p44, Table 5.2, women 18-30", "14.818kg + 486.6");
        v("bmr.female.18.b", "486.6", "kcal/day", FAO, "PDF p44, Table 5.2, women 18-30", "14.818kg + 486.6");
        v("bmr.female.30.a", "8.126", "kcal/kg/day", FAO, "PDF p44, Table 5.2, women 30-60", "8.126kg + 845.6");
        v("bmr.female.30.b", "845.6", "kcal/day", FAO, "PDF p44, Table 5.2, women 30-60", "8.126kg + 845.6");
        v("bmr.female.60.a", "9.082", "kcal/kg/day", FAO, "PDF p44, Table 5.2, women 60+", "9.082kg + 658.5");
        v("bmr.female.60.b", "658.5", "kcal/day", FAO, "PDF p44, Table 5.2, women 60+", "9.082kg + 658.5");
        // Age bands of Table 5.2: 18-30, 30-60 and 60 or more.
        v("bmr.age.band.middle", "30", "years", FAO, "PDF p44, Table 5.2", "30–60 646 0.048kg + 3.653");
        v("bmr.age.band.older", "60", "years", FAO, "PDF p44, Table 5.2", "30–60 646 0.048kg + 3.653");
        // Standard errors of estimate, for the uncertainty shown with the energy estimate.
        v("bmr.male.18.see", "153", "kcal/day", FAO, "PDF p44, Table 5.2", "15.057kg + 692.2 153");
        v("bmr.male.30.see", "167", "kcal/day", FAO, "PDF p44, Table 5.2", "11.472kg + 873.1 167");
        v("bmr.male.60.see", "164", "kcal/day", FAO, "PDF p44, Table 5.2", "11.711kg + 587.7 164");
        v("bmr.female.18.see", "119", "kcal/day", FAO, "PDF p44, Table 5.2", "14.818kg + 486.6 119");
        v("bmr.female.30.see", "111", "kcal/day", FAO, "PDF p44, Table 5.2", "8.126kg + 845.6 111");
        v("bmr.female.60.see", "108", "kcal/day", FAO, "PDF p44, Table 5.2", "9.082kg + 658.5 108");

        // --- ICMR-NIN 2020: Indian adjustment, activity and reference energies ---
        v("icmr.bmr.reduction.2010", "0.05", "fraction", ICMR, "p2, Physical activity level", "used 5% reduction in BMR");
        v("icmr.bmr.reduction.2020", "0.05", "fraction", ICMR, "p2, BMR", "further reduced the BMR by another 5 % for adults");
        v("icmr.pal.sedentary", "1.40", "PAL", ICMR, "p2, Physical activity level", "to 1.40 for sedentary population");
        v("icmr.eer.male.sedentary", "2110", "kcal/day", ICMR, "p4, Table 1a", "Sedentary work 2110 2320");
        v("icmr.eer.male.moderate", "2710", "kcal/day", ICMR, "p4, Table 1a", "Moderate work 2710 2730");
        v("icmr.eer.male.heavy", "3470", "kcal/day", ICMR, "p4, Table 1a", "Heavy work 3470 3490");
        v("icmr.eer.female.sedentary", "1660", "kcal/day", ICMR, "p4, Table 1a", "Sedentary work 1660 1900");
        v("icmr.eer.female.moderate", "2130", "kcal/day", ICMR, "p4, Table 1a", "Moderate work 2130 2230");
        v("icmr.eer.female.heavy", "2720", "kcal/day", ICMR, "p4, Table 1a", "Heavy work 2720 2850");
        v("icmr.pregnancy.kcal", "350", "kcal/day", ICMR, "p4, Table 1a", "Pregnant + 350");
        v("nin.pregnancy.kcal.from.trimester", "2", "rule", NIN, "line 1682 (first trimester: line 1681, no addition)",
            "Second & third trimester: An additional 350 Kcal");
        v("nin.pregnancy.undernourished.kcal", "100", "kcal/day", NIN, "line 1683", "an additional 100 Kcal per day");
        v("icmr.lactation.kcal.0to6", "600", "kcal/day", ICMR, "p4, Table 1a", "Lactating (0-6m) +600");
        v("icmr.lactation.kcal.7to12", "520", "kcal/day", ICMR, "p4, Table 1a", "Lactating (7-12m) +520");

        // --- Protein ---
        v("icmr.protein.rda", "0.83", "g/kg/day", ICMR, "p5, Table 2a", "65 0.66 0.83 42.9 54.0");
        v("icmr.protein.ear", "0.66", "g/kg/day", ICMR, "p5, Table 2a", "65 0.66 0.83 42.9 54.0");
        v("icmr.protein.vegetarian", "1.0", "g/kg/day", ICMR, "p5-6, Table 2b, Recommended protein for vegetarians",
            "Recommended protein for vegetarians Protein recommendation: RDA 2010 Body weight kg Protein g/kg/d Total daily Requirement (g) Males 60 1.0 60",
            "ICMR 2020 keeps its 2010 figure as Table 2b for vegetarians (and Table 2a's footnote gives 1 g/kg for cereal-based diets); "
                + "applied to Vegetarian, Jain and Vegan diets.");
        v("icmr.protein.pregnancy.t2.rda", "9.5", "g/day", ICMR, "p5, Table 2a", "2nd Trimester +7.6 +9.5");
        v("icmr.protein.pregnancy.t3.rda", "22.0", "g/day", ICMR, "p5, Table 2a", "3rd Trimester +17.6 +22.0");
        v("icmr.protein.lactation.0to6.rda", "16.9", "g/day", ICMR, "p5, Table 2a", "0-6 months +13.6 +16.9");
        v("icmr.protein.lactation.7to12.rda", "13.2", "g/day", ICMR, "p5, Table 2a", "6-12 months +10.6 +13.2");
        v("nin.protein.muscle.max", "1.6", "g/kg/day", NIN, "line 4293", "greater than ~1.6g/kg/day do not contribute any");
        v("issn.protein.muscle.min", "1.4", "g/kg/day", ISSN, "abstract, point 3",
            "1.4-2.0 g protein/kg body weight/day (g/kg/d) is sufficient for most exercising individuals",
            "The lower bound of ISSN's range; NIN 2024 sets the upper end (nin.protein.muscle.max), so the app shows where the two agree.");

        // --- Energy shares and limits ---
        v("nin.fat.max.share", "0.30", "share of energy", NIN, "line 5847", "The total fat intake should not exceed 30% of");
        v("who.sfa.max.share", "0.10", "share of energy", WHO_FATS, "PDF p11, SFA recommendation 1",
            "reduce saturated fatty acid intake to 10% of total energy intake");
        v("who.tfa.max.share", "0.01", "share of energy", WHO_FATS, "PDF p14, TFA recommendation 1",
            "reduce trans -fatty acid intake to 1% of total energy intake");
        v("who.free.sugars.max.share", "0.10", "share of energy", WHO_SUGARS, "PDF p12",
            "reducing the intake of free sugars to less than 10% of total energy intake");
        v("who.free.sugars.better.share", "0.05", "share of energy", WHO_SUGARS, "PDF p34, Table 6",
            "below 5% of total energy intake");
        v("nin.carbohydrate.share.low", "0.50", "share of energy", NIN, "line 619", "50% –55% of total calories from");
        v("nin.carbohydrate.share.high", "0.55", "share of energy", NIN, "line 619", "50% –55% of total calories from");
        v("who.sodium.max", "2000", "mg/day", WHO_SODIUM, "PDF p10", "<2 g/day sodium (5 g/day salt)");
        v("who.salt.max", "5", "g/day", WHO_SODIUM, "PDF p10", "<2 g/day sodium (5 g/day salt)");
        v("who.fibre.min", "25", "g/day", WHO_CARBS, "PDF p10, recommendation 4",
            "at least 25 g per day of naturally occurring dietary fibre");
        v("fssai.kcal.per.g.carbohydrate", "4", "kcal/g", FSSAI_LABEL, "p13, regulation 5(3)(e)", "Carbohydrates 4 kcal/g");
        v("fssai.kcal.per.g.protein", "4", "kcal/g", FSSAI_LABEL, "p13, regulation 5(3)(e)", "Protein 4 kcal/g");
        v("fssai.kcal.per.g.fat", "9", "kcal/g", FSSAI_LABEL, "p13, regulation 5(3)(e)", "Fat 9 kcal/g");

        // --- FSSAI label reference for an average adult: used when no weight is known ---
        v("fssai.label.energy", "2000", "kcal/day", FSSAI_LABEL, "p10, regulation 5(3)(b)", "on the basis of 2000kcal energy");
        v("fssai.label.fat", "67", "g/day", FSSAI_LABEL, "p10, regulation 5(3)(b)", "67 g total fat");
        v("fssai.label.saturated.fat", "22", "g/day", FSSAI_LABEL, "p10, regulation 5(3)(b)", "22 g saturated fat");
        v("fssai.label.trans.fat", "2", "g/day", FSSAI_LABEL, "p10, regulation 5(3)(b)", "2 g trans fat");
        v("fssai.label.added.sugar", "50", "g/day", FSSAI_LABEL, "p10, regulation 5(3)(b)", "50 g added sugar");
        v("fssai.label.sodium", "2000", "mg/day", FSSAI_LABEL, "p10, regulation 5(3)(b)", "2000 mg of sodium (5 g salt)");

        // --- Body size ---
        v("nin.bmi.underweight.below", "18.5", "kg/m2", NIN, "line 4419", "BMI should be between 18.5 to");
        v("nin.bmi.normal.max", "23", "kg/m2", NIN, "line 4430", "Overweight: BMI ranging over 23 to 27.5");
        v("nin.bmi.overweight.max", "27.5", "kg/m2", NIN, "line 4484", "Obesity: BMI above 27.5");
        v("nin.waist.male.max", "90", "cm", NIN, "line 4490", "circumference of >90cm for men and >80cm for women");
        v("nin.waist.female.max", "80", "cm", NIN, "line 4490", "circumference of >90cm for men and >80cm for women");

        // --- Weight loss (information only; the app never sets a deficit) ---
        v("nin.weightloss.min.kcal", "1000", "kcal/day", NIN, "line 4548", "Weight reduction diets should not be less than 1000");
        v("nin.weightloss.deficit.low", "500", "kcal/day", NIN, "line 4619", "500–750 Kcal/day deficit");
        v("nin.weightloss.deficit.high", "750", "kcal/day", NIN, "line 4619", "500–750 Kcal/day deficit");
        v("nin.weightloss.max.deficit.share", "0.40", "share of energy", NIN, "line 4624", "not to go beyond 40% energy deficit");

        // --- NIN 2024 Table 15.1 thresholds per 100 g or 100 ml ---
        v("nin.hfss.liquid.kcal", "70", "kcal/100 ml", NIN, "line 6082", "Calorie (kcal) 70 250");
        v("nin.hfss.solid.kcal", "250", "kcal/100 g", NIN, "line 6082", "Calorie (kcal) 70 250");
        v("nin.hfss.liquid.salt", "175", "mg/100 ml", NIN, "line 6083", "Salt (mg) 175 625");
        v("nin.hfss.solid.salt", "625", "mg/100 g", NIN, "line 6083", "Salt (mg) 175 625");
        v("nin.hfss.liquid.added.sugar", "2.0", "g/100 ml", NIN, "line 6084", "Added sugar (g) 2.0 3");
        v("nin.hfss.solid.added.sugar", "3", "g/100 g", NIN, "line 6084", "Added sugar (g) 2.0 3");
        v("nin.hfss.liquid.added.fat", "1.5", "g/100 ml", NIN, "line 6085", "Added fat (g) 1.5 4.2");
        v("nin.hfss.solid.added.fat", "4.2", "g/100 g", NIN, "line 6085", "Added fat (g) 1.5 4.2");
        v("nin.hfss.class3.kcal", "500", "kcal/100 g", NIN, "line 6168", "Energy: >500 Kcal");
        v("nin.portion.oil.g", "5", "g", NIN, "line 6951", "Oil/ghee 5 1 teaspoon");

        // --- Additives: JECFA (WHO database pages) first, EFSA where it differs (Europe PMC abstracts) ---
        v("jecfa.adi.951", "40", "mg/kg bw/day", JECFA + "62.html", "aspartame, evaluation 2023", "ADI: 0–40 mg/kg bw");
        v("jecfa.adi.950", "15", "mg/kg bw/day", JECFA + "926.html", "acesulfame potassium, 1990", "ADI: 0-15 mg/kg bw");
        v("jecfa.adi.955", "15", "mg/kg bw/day", JECFA + "2340.html", "sucralose, 1990", "ADI: 0-15 mg/kg bw");
        v("jecfa.adi.954", "5", "mg/kg bw/day", JECFA + "3164.html", "saccharin group, 1993", "ADI: 0-5 mg/kg bw");
        v("jecfa.adi.961", "2", "mg/kg bw/day", JECFA + "5107.html", "neotame, 2003", "ADI: 0-2 mg/kg bw");
        v("jecfa.adi.210", "20", "mg/kg bw/day", JECFA + "4530.html", "benzoic acid and benzoates group, 2021",
            "ADI: 0–20 mg/kg bw");
        v("jecfa.adi.319", "0.7", "mg/kg bw/day", JECFA + "236.html", "TBHQ, 1997", "ADI: 0-0.7 mg/kg bw");
        v("jecfa.adi.320", "0.5", "mg/kg bw/day", JECFA + "2140.html", "BHA, 1988", "ADI: 0-0.5 mg/kg bw");
        v("jecfa.adi.321", "0.3", "mg/kg bw/day", JECFA + "2142.html", "BHT, 1995", "ADI: 0-0.3 mg/kg bw");
        v("jecfa.adi.621.not.specified", "0", "rule", JECFA + "2267.html", "glutamates group, 1987",
            "ADI: NOT SPECIFIED Comments: Group ADI for L-glutamic acid");
        v("efsa.adi.954", "9", "mg/kg bw/day", EFSA + "saccharin.json", "EFSA 2024 re-evaluation",
            "ADI of 9 mg/kg body weight (bw) per day");
        v("efsa.adi.621", "30", "mg/kg bw/day", EFSA + "glutamate.json", "EFSA 2017, as glutamic acid",
            "group acceptable daily intake (ADI) of 30 mg/kg bw per day");
        v("efsa.adi.321", "0.25", "mg/kg bw/day", EFSA + "bht.json", "EFSA 2012", "derived an ADI of 0.25 mg/kg bw/day");
        v("efsa.adi.950", "15", "mg/kg bw/day", EFSA + "acesulfame.json", "EFSA 2025 re-evaluation",
            "established an acceptable daily intake (ADI) of 15 mg/kg body weight (bw) per day");
        v("efsa.adi.961", "10", "mg/kg bw/day", EFSA + "neotame.json", "EFSA 2025 re-evaluation",
            "acceptable daily intake (ADI) of 10 mg/kg bw per day for neotame");
        v("efsa.adi.955", "15", "mg/kg bw/day", EFSA + "sucralose.json", "EFSA 2026 re-evaluation",
            "current ADI of 15 mg/kg bw per day of sucralose");
        v("efsa.tio2.not.safe", "171", "rule", EFSA + "tio2.json", "EFSA 2021 opinion",
            "E 171 can no longer be considered as safe when used as a food additive");
        v("eu.colours.warning", "1", "rule", "security/evidence/eu/eu-1333-2008-annex-v.html", "Regulation (EC) 1333/2008 Annex V",
            "may have an adverse effect on activity and attention in children");
        v("eu.paraben.216.deleted", "216", "rule", "security/evidence/eu/eu-dir-2006-52-annex-i.html", "Directive 2006/52/EC Annex I",
            "E 216 Propyl p-hydroxybenzoate");
        v("fssai.bromate.not.allowed", "924", "rule", FSSAI_2016, "notice of 20 June 2016, para 5",
            "use of Potassium bromate as an additive in any food is not allowed");

        // --- FSSAI label statements the rules read (Labelling and Display Regulations, Schedule II) ---
        v("fssai.nonveg.definition", "1", "rule", FSSAI_LABEL, "p2-3, regulation 2(o)",
            "or eggs or products of any animal origin, but does not include milk, milk products, honey or bees wax or carnauba wax or shellac");
        v("fssai.caffeine.declaration", "1", "rule", FSSAI_LABEL, "p28, Schedule II item 3", "Added caffeine CONTAINS CAFFEINE");
        v("fssai.aspartame.warning", "951", "rule", FSSAI_LABEL, "p30, Schedule II",
            "Not recommended for phenylketonurics; for children suffering from seizure disorders; pregnant and lactating mothers");
        v("fssai.acesulfame.warning", "950", "rule", FSSAI_LABEL, "p30, Schedule II",
            "Not recommended for children; pregnant and lactating mothers");
        v("fssai.msg.warning", "621", "rule", FSSAI_LABEL, "p32, Schedule II item 4",
            "NOT RECOMMENDED FOR INFANTS BELOW -12 MONTHS AND PREGNANT WOMEN");
        v("fssai.stanol.warning", "1", "rule", FSSAI_LABEL, "p32, Schedule II item 7",
            "May not be nutritionally appropriate for pregnant and lactating women");

        // --- Kidney and pregnancy: salt substitutes and phosphate additives ---
        v("who.lsss.excludes.kidney", "1", "rule", "security/evidence/who-salt-substitutes-2025.pdf", "PDF p12",
            "excluding individuals with kidney impairments");
        v("who.lsss.not.pregnant", "1", "rule", "security/evidence/who-salt-substitutes-2025.pdf", "PDF p12",
            "This recommendation is intended for adults (not pregnant women or children)");
        v("kdigo.phosphate.additives", "1", "rule", KDIGO, "KDIGO 2017 CKD-MBD update, Recommendation 4.1.8 (Not Graded), CKD G3a-G5D",
            "It is reasonable to consider phosphate source (e.g., animal, vegetable, additives) in making dietary recommendations",
            "Applied as: products declaring a phosphate additive are left out of swaps and named on the card for anyone with a kidney condition.");
        v("kdigo.phosphate.scope", "1", "rule", KDIGO, "Recommendation 4.1.8 (2D)",
            "In patients with CKD G3a–G5D, we suggest limiting dietary phosphate intake");

        // --- Caffeine: NIN 2024 (everyone) and WHO antenatal care 2016 (pregnancy) ---
        v("nin.caffeine.limit", "300", "mg/day", NIN, "lines 5752-5754", "caffeine intake does not exceed the tolerable limits (300mg/day)");
        v("nin.caffeine.cup.ml", "150", "ml", NIN, "line 5749", "A cup (150ml) of brewed coffee contains");
        v("nin.caffeine.brewed.low", "80", "mg/cup", NIN, "line 5750", "brewed coffee contains 80–120mg of caffeine");
        v("nin.caffeine.brewed.high", "120", "mg/cup", NIN, "line 5750", "brewed coffee contains 80–120mg of caffeine");
        v("nin.caffeine.instant.low", "50", "mg/cup", NIN, "line 5750", "instant coffee contains 50–65mg");
        v("nin.caffeine.instant.high", "65", "mg/cup", NIN, "line 5750", "instant coffee contains 50–65mg");
        v("nin.caffeine.tea.low", "30", "mg/cup", NIN, "line 5751", "tea contains 30–65mg of caffeine");
        v("nin.caffeine.tea.high", "65", "mg/cup", NIN, "line 5751", "tea contains 30–65mg of caffeine");
        v("nin.caffeine.pregnancy.minimise", "1", "rule", NIN, "lines 1804-1806",
            "Excess intake of beverages containing caffeine like coffee and tea adversely affect fetal growth and hence should be minimized");
        v("who.anc.caffeine.pregnancy", "300", "mg/day", WHO_ANC, "PDF p56 (printed p38), Recommendation A.10",
            "For pregnant women with high daily caffeine intake (more than 300 mg per day)");
        v("who.anc.caffeine.soft.drink", "50", "mg/serving", WHO_ANC, "PDF p56 (printed p38), A.10 remarks",
            "soft drinks (colas and iced tea) usually contain less than 50 mg per 250 mL serving");
        v("who.anc.caffeine.soft.drink.ml", "250", "ml", WHO_ANC, "PDF p56 (printed p38), A.10 remarks",
            "soft drinks (colas and iced tea) usually contain less than 50 mg per 250 mL serving");

        // --- Lactose ---
        v("nin.lactose.in.milk", "1", "rule", NIN, "lines 820-822", "while lactose is found in milk, sucrose is the table sugar");
        v("nin.lactose.advice", "1", "rule", NIN, "lines 2714-2716",
            "reducing the quantity of milk taken at a time or taking milk along with a cereal-pulse meal or taking milk in the form of curd");
        v("lactose.tolerated.low", "12", "g", LACTOSE, "Shaukat et al. 2010 systematic review, abstract",
            "12 to 15 g of lactose (approximately 1 cup of milk) is well tolerated by most adults",
            "The lower figure is used, so the card errs towards caution.");
        v("lactose.tolerated.high", "15", "g", LACTOSE, "Shaukat et al. 2010 systematic review, abstract",
            "12 to 15 g of lactose (approximately 1 cup of milk) is well tolerated by most adults");
        v("fssai.claims.lactose.free", "0.1", "g/100 g", FSSAI_CLAIMS, "p14, Schedule I item 18 (as substituted)",
            "Lactose content in the product shall not exceed 0.1 per cent.");

        // --- FSSAI food category system (Appendix A), the taxonomy every product is placed in ---
        v("fssai.fcs.1", "1", "rule", FSSAI_2016, "p20", "1.0 Dairy products and analogues");
        v("fssai.fcs.2", "2", "rule", FSSAI_2016, "p7", "2.0 Fats and oils, and fat emulsions");
        v("fssai.fcs.3", "3", "rule", FSSAI_2016, "p8", "3.0 Edible ices, including sherbet and sorbet");
        v("fssai.fcs.4", "4", "rule", FSSAI_2016, "p8", "4.0 Fruits and vegetables (including mushrooms and fungi");
        v("fssai.fcs.5", "5", "rule", FSSAI_2016, "p10", "5.0 Confectionery");
        v("fssai.fcs.6", "6", "rule", FSSAI_2016, "p11", "6.0 Cereals and cereal products");
        v("fssai.fcs.7", "7", "rule", FSSAI_2016, "p12", "7.0 Bakery wares");
        v("fssai.fcs.8", "8", "rule", FSSAI_2016, "p13", "8.0 Meat and meat products including poultry");
        v("fssai.fcs.9", "9", "rule", FSSAI_2016, "p14", "9.0 Fish and fish products");
        v("fssai.fcs.10", "10", "rule", FSSAI_2016, "p15", "10.0 Eggs and egg products");
        v("fssai.fcs.11", "11", "rule", FSSAI_2016, "p15", "11.0 Sweeteners, including honey");
        v("fssai.fcs.12", "12", "rule", FSSAI_2016, "p16", "12.0 Salts, spices, soups, sauces, salads and protein products");
        v("fssai.fcs.13", "13", "rule", FSSAI_2016, "p17", "13.0 Foodstuffs intended for particular nutritional uses");
        v("fssai.fcs.14", "14", "rule", FSSAI_2016, "p18", "14.0 Beverages, excluding dairy products");
        v("fssai.fcs.15", "15", "rule", FSSAI_2016, "p19", "15.0 Ready-to-eat savouries");
        v("fssai.fcs.16", "16", "rule", FSSAI_2016, "p65", "16.0 Prepared foods");

        // --- Nutri-Score 2023 for foods: Scientific Committee update report 2022, recap (PDF p130-135) ---
        // Each row is quoted as printed; NutriScore2023Test checks the arrays in NutriGrade against these rows.
        v("fao.kj.per.kcal", "4.184", "kJ/kcal", FAO, "PDF p15", "1 kcal = 4.184 kJ");
        v("ns2023.general.a.0", "0", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "0 < 335 ≤ 3.4 ≤ 1 ≤ 0.2");
        v("ns2023.general.a.1", "1", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "1 > 335 > 3.4 > 1 > 0.2");
        v("ns2023.general.a.2", "2", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "2 > 670 > 6.8 > 2 > 0.4");
        v("ns2023.general.a.3", "3", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "3 > 1005 > 10 > 3 > 0.6");
        v("ns2023.general.a.4", "4", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "4 > 1340 > 14 > 4 > 0.8");
        v("ns2023.general.a.5", "5", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "5 > 1675 > 17 > 5 > 1");
        v("ns2023.general.a.6", "6", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "6 > 2010 > 20 > 6 > 1.2");
        v("ns2023.general.a.7", "7", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "7 > 2345 > 24 > 7 > 1.4");
        v("ns2023.general.a.8", "8", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "8 > 2680 > 27 > 8 > 1.6");
        v("ns2023.general.a.9", "9", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "9 > 3015 > 31 > 9 > 1.8");
        v("ns2023.general.a.10", "10", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "10 > 3350 > 34 > 10 > 2");
        v("ns2023.general.a.11", "11", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "11 > 37 > 2.2");
        v("ns2023.general.a.12", "12", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "12 > 41 > 2.4");
        v("ns2023.general.a.13", "13", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "13 > 44 > 2.6");
        v("ns2023.general.a.14", "14", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "14 > 48 > 2.8");
        v("ns2023.general.a.15", "15", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "15 > 51 > 3");
        v("ns2023.general.a.16", "16", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "16 > 3.2");
        v("ns2023.general.a.17", "17", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "17 > 3.4");
        v("ns2023.general.a.18", "18", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "18 > 3.6");
        v("ns2023.general.a.19", "19", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "19 > 3.8");
        v("ns2023.general.a.20", "20", "rule", NUTRISCORE, "PDF p130, 1.1 A points", "20 > 4");
        v("ns2023.c.0", "0", "rule", NUTRISCORE, "PDF p130, 1.2.1 C points", "0 ≤ 2.4 ≤ 3.0 ≤ 40");
        v("ns2023.c.1", "1", "rule", NUTRISCORE, "PDF p130, 1.2.1 C points", "1 > 2.4 > 3.0 > 40");
        v("ns2023.c.2", "2", "rule", NUTRISCORE, "PDF p130, 1.2.1 C points", "2 > 4.8 > 4.1 > 60");
        v("ns2023.c.3", "3", "rule", NUTRISCORE, "PDF p130, 1.2.1 C points", "3 > 7.2 > 5.2 -");
        v("ns2023.c.4", "4", "rule", NUTRISCORE, "PDF p130, 1.2.1 C points", "4 > 9.6 > 6.3 -");
        v("ns2023.c.5", "5", "rule", NUTRISCORE, "PDF p130, 1.2.1 C points", "5 > 12 > 7.4 > 80");
        v("ns2023.c.6", "6", "rule", NUTRISCORE, "PDF p130, 1.2.1 C points", "6 > 14");
        v("ns2023.c.7", "7", "rule", NUTRISCORE, "PDF p130, 1.2.1 C points", "7 > 17");
        v("ns2023.fats.a.0", "0", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "0 ≤ 120 ≤ 3.4 < 10 ≤ 0.2");
        v("ns2023.fats.a.1", "1", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "1 > 120 > 3.4 < 16 > 0.2");
        v("ns2023.fats.a.2", "2", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "2 > 240 > 6.8 < 22 > 0.4");
        v("ns2023.fats.a.3", "3", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "3 > 360 > 10 < 28 > 0.6");
        v("ns2023.fats.a.4", "4", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "4 > 480 > 14 < 34 > 0.8");
        v("ns2023.fats.a.5", "5", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "5 > 600 > 17 < 40 > 1.0");
        v("ns2023.fats.a.6", "6", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "6 > 720 > 20 < 46 > 1.2");
        v("ns2023.fats.a.7", "7", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "7 > 840 > 24 < 52 > 1.4");
        v("ns2023.fats.a.8", "8", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "8 > 960 > 27 < 58 > 1.6");
        v("ns2023.fats.a.9", "9", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "9 > 1080 > 31 < 64 > 1.8");
        v("ns2023.fats.a.10", "10", "rule", NUTRISCORE, "PDF p133, 2.2 A points", "10 > 1200 > 34 ≥ 64 > 2.0");
        v("ns2023.fats.kj.per.g.saturates", "37", "kJ/g", NUTRISCORE, "PDF p133, footnote", "× 37");
        v("ns2023.red.meat.protein.max", "2", "points", NUTRISCORE, "PDF p131", "the number of maximum protein points is set at 2 points");
        v("ns2023.general.protein.rule", "11", "points", NUTRISCORE, "PDF p132, 1.3", "Points A ≥ 11");
        v("ns2023.cheese.rule", "11", "rule", NUTRISCORE, "PDF p132, 1.3", "Points A < 11 or for Cheese");
        v("ns2023.fats.protein.rule", "7", "points", NUTRISCORE, "PDF p135, 2.4", "Points A ≥ 7");
        v("ns2023.general.thresholds", "0", "rule", NUTRISCORE, "PDF p132, 1.4",
            "Min to 0 A Dark green 1 to 2 B Light green 3 to 10 C Yellow 11 to 18 D Light Orange 19 to Max E Dark orange");
        v("ns2023.fats.thresholds", "-6", "rule", NUTRISCORE, "PDF p135, 2.1",
            "Min to -6 A Dark green -5 to 2 B Light green 3 to 10 C Yellow 11 to 18 D Light Orange 19 to Max E Dark orange");
        v("ns2023.fats.category", "1", "rule", NUTRISCORE, "PDF p133, 2.1",
            "This category includes fats and oils from plant or animal sources, including cream, margarines, butters and oils");

        // --- What counts as a real difference: FSSAI Advertising and Claims Regulations 2018 ---
        // Regulation 5(4)(a) as substituted (operationalised 26 Oct 2021, compliance from 1 Jul 2022) replaced
        // "at least twenty-five per cent ... and a minimum absolute difference" with thirty per cent.
        v("fssai.claims.comparative.min", "0.30", "share", FSSAI_CLAIMS, "p4, regulation 5(4)(a) as substituted",
            "at least thirty per cent. in the energy value or nutrient content except micronutrient",
            "A swap must differ by at least this much on the nutrient it is chosen for, as a comparative claim on a label must.");
        // When the original has none of a nutrient a percentage is meaningless; the content claim decides.
        v("fssai.claims.fibre.source.solid", "3", "g/100 g", FSSAI_CLAIMS, "p14, Schedule I item 14 (as substituted)",
            "3 g of fibre per 100 g or 1.5 g of fibre per 100 ml or 1.5 g of fibre per 100kcal");
        v("fssai.claims.fibre.source.liquid", "1.5", "g/100 ml", FSSAI_CLAIMS, "p14, Schedule I item 14 (as substituted)",
            "3 g of fibre per 100 g or 1.5 g of fibre per 100 ml or 1.5 g of fibre per 100kcal");
        v("fssai.claims.fibre.source.per.100kcal", "1.5", "g/100 kcal", FSSAI_CLAIMS, "p14, Schedule I item 14 (as substituted)",
            "1.5 g of fibre per 100 ml or 1.5 g of fibre per 100kcal");

        // --- Micronutrient references: FSSAI RDA direction of 3 Feb 2022 (the source NutrientReference cites) ---
        // The annexures are scans; each value is quoted from the machine OCR transcript.
        v("fssai.rda.biotin", "30", "ug/day", RDA_OCR, "Annexure IB, page6.jpg rows", "Biotin** | 30 μg");
        v("fssai.rda.vitamin.k", "55", "ug/day", RDA_OCR, "Annexure IB, page6.jpg rows", "Vitamin K* (Kl K2) | 55 μg");
        v("fssai.rda.pantothenic", "5", "mg/day", RDA_OCR, "Annexure IB, page6.jpg rows", "Pantothenic acid** | 5 mg");
        v("fssai.rda.copper", "2", "mg/day", RDA_OCR, "Annexure IA other minerals, page5.jpg rows", "Coppar | 2 mg/day");
        v("fssai.rda.phosphorus", "1000", "mg/day", RDA_OCR, "Annexure IA other minerals, page5.jpg rows", "Phosphorous | 1000mg/day");
        v("fssai.rda.potassium", "3500", "mg/day", RDA_OCR, "Annexure IA other minerals, page5.jpg rows", "Potassium | 3500 mg/day");
        v("fssai.rda.manganese", "4", "mg/day", RDA_OCR, "Annexure IA other minerals, page5.jpg rows", "Manganese | 4 mg/day");
        v("fssai.rda.chromium", "50", "ug/day", RDA_OCR, "Annexure IA other minerals, page5.jpg rows", "Chromnm | 50 μg/day");
        v("fssai.rda.selenium", "40", "ug/day", RDA_OCR, "Annexure IA other minerals, page5.jpg rows", "Selemm | 40 μg/day");
        v("fssai.rda.iodine.male", "150", "ug/day", RDA_OCR, "Annexure IA minerals crop, iodine strip: man, woman, pregnant, lactating",
            "Todine | 150 | 150 | 250 | 280");
        v("fssai.rda.iodine.female", "150", "ug/day", RDA_OCR, "Annexure IA minerals crop, iodine strip: man, woman, pregnant, lactating",
            "Todine | 150 | 150 | 250 | 280");
        v("fssai.rda.magnesium.male", "385", "mg/day", RDA_OCR, "Annexure IA minerals crop, magnesium strip: man, woman, then children",
            "385 | 325 | 75 | 135");
        v("fssai.rda.magnesium.female", "325", "mg/day", RDA_OCR, "Annexure IA minerals crop, woman's row: CHO, calcium, magnesium, iron, zinc, iodine",
            "130 | 1000 | 325 | 29 | 13 2 | 150");
        v("fssai.rda.b12.male", "2.5", "ug/day", RDA_OCR, "Annexure IA vitamins crop, B12 strip: man, then pregnancy addition",
            "B12 | 2.5 | +0.25");
        v("fssai.rda.b12.female", "2.5", "ug/day", RDA_OCR, "Annexure IA vitamins crop, woman's row: B6, folate, B12, vitamin A",
            "19 | 220 | 2.5 | 840");
        // The ICMR-NIN 2020 brief note prints different adult figures for two of these (Tables 3-4,
        // columns RDA 2010, RDA 2020, EAR 2020, IOM). Recorded so the conflict is visible; not used.
        review("icmr.brief.magnesium.male", "440", "mg/day", ICMR, "p6, Table 3 (males), RDA 2020 column",
            "Magnesium (mg) 340 440 370 400",
            "Conflicts with FSSAI's 2022 annexure (385); NutrientReference follows the annexure it cites.");
        review("icmr.brief.magnesium.female", "370", "mg/day", ICMR, "p6, Table 4 (females), RDA 2020 column",
            "Magnesium (mg) 310 370 310 310",
            "Conflicts with FSSAI's 2022 annexure (325); NutrientReference follows the annexure it cites.");
        review("icmr.brief.b12", "2.2", "ug/day", ICMR, "p6, Tables 3-4, RDA 2020 column",
            "Vitamin B12 (µg) 1 2.2 2 2.4",
            "Conflicts with FSSAI's 2022 annexure (2.5); NutrientReference follows the annexure it cites.");
        v("icmr.brief.iodine", "150", "ug/day", ICMR, "p6, Tables 3-4, RDA 2020 column", "Iodine (µg) 150 150 95 150",
            "Agrees with the annexure; Codex's 140 matched neither source.");
    }

    static Entry get(String id) {
        Entry e = ALL.get(id);
        if (e == null) throw new IllegalArgumentException("No evidence entry " + id);
        return e;
    }

    static BigDecimal d(String id) { return get(id).number(); }

    static List<Entry> all() { return Collections.unmodifiableList(new ArrayList<>(ALL.values())); }

    private Evidence() {}
}
