package com.betterchoice.android;

import java.util.*;

/** Versioned reference evidence. RDA/AI adequacy, reduction advice and ULs are different concepts.
 * No sex/life-stage-specific reference is inferred from an optional gender answer. */
final class NutrientReference {
    static final String VERSION = "india-primary-2022+nin-2024+efsa-2023-v3";
    static final String REVIEWED = "2026-10-01";
    static final String RDA_SOURCE = "https://www.fssai.gov.in/upload/advisories/2022/03/62414c50d2342Direction_RDA_03_02_2022.pdf";
    static final String LABEL_SOURCE = "https://fssai.gov.in/docs/food-law/regulations/Comp_Labelling%20Display_Version%20VIII_09_09_2025.pdf";
    static final String DGI_SOURCE = "https://nin.res.in/dietaryguidelines/pdfjs/locale/DGI_2024.pdf";
    static final String WHO_CARBS_SOURCE = "https://www.who.int/publications/i/item/9789240073593";
    enum Kind { RDA, AI, LABEL_REFERENCE, ENERGY_RANGE, REDUCTION, UL, ADI, INFORMATION }
    enum Status { PRIMARY, REVIEW_REQUIRED }
    static final class Ref {
        final String id, label, unit, source, note, jurisdiction, population, chemicalForm, scope, published, reviewed;
        final Kind kind;
        final Status status;
        final Double man, woman, rangeLow, rangeHigh;
        Ref(String id, String label, String unit, Kind kind, Double man, Double woman, String source,
            String jurisdiction, String population, String form, String scope, String published, String note) {
            this.id=id; this.label=label; this.unit=unit; this.kind=kind;
            boolean range=kind==Kind.ENERGY_RANGE || id.equals("vitamin_e");
            this.man=range ? null : man; this.woman=range ? null : woman;
            this.rangeLow=range ? man : null; this.rangeHigh=range ? woman : null;
            this.source=source; this.jurisdiction=jurisdiction; this.population=population; this.chemicalForm=form;
            this.scope=scope; this.published=published; this.reviewed=REVIEWED; this.note=note;
            this.status=Status.PRIMARY;
        }
    }
    private static final Map<String, Ref> REGISTRY = new LinkedHashMap<>();
    private static void india(String id, String label, String unit, Kind kind, double m, double w, String form, String note) {
        REGISTRY.put(id, new Ref(id,label,unit,kind,m,w,RDA_SOURCE,"India","Healthy nonpregnant adults; sedentary where activity-specific",
            form,"Food intake; not a toxicity threshold", "2022-02-03",note));
    }
    private static void label(String id, String label, String unit, double value) {
        REGISTRY.put(id, new Ref(id,label,unit,Kind.LABEL_REFERENCE,value,value,LABEL_SOURCE,"India",
            "Average adult label reference", "Declared nutrient", "Labelling percentage; not a personal prescription or UL", "2025-09-09",
            "Do not substitute for a personalised clinical target."));
    }
    static {
        label("energy","Energy","kcal",2000); label("total_fat","Total fat","g",67);
        label("saturated_fat","Saturated fat","g",22); label("trans_fat","Trans fat","g",2);
        label("added_sugar","Added sugars","g",50); label("sodium","Sodium","mg",2000);
        india("protein","Protein","g/kg/day",Kind.RDA,.83,.83,"Protein", "General adult reference, not an athletic target; reference-body-weight values are rounded in Annexure IA.");
        // Fibre: no adult figure in the RDA direction; WHO's minimum for every adult, as the calculator uses (who.fibre.min).
        REGISTRY.put("fibre", new Ref("fibre","Dietary fibre","g",Kind.AI,25.0,25.0,WHO_CARBS_SOURCE,"WHO","Adults",
            "Naturally occurring dietary fibre","Minimum intake from food; not a toxicity threshold","2023-07-17",
            "At least 25 g a day for every adult (WHO 2023 recommendation 4). The earlier 30/25 g sex split had no source on file."));
        india("calcium","Calcium","mg",Kind.RDA,1000,1000,"Calcium","Adequacy, not an upper limit.");
        india("magnesium","Magnesium","mg",Kind.RDA,385,325,"Food magnesium","Annexure IA. The ICMR-NIN 2020 brief note prints 440/370; this table follows the annexure it cites. Must not be confused with a supplemental-form UL.");
        india("iron","Iron","mg",Kind.RDA,19,29,"Iron","Life stage and physiological context required.");
        india("zinc","Zinc","mg",Kind.RDA,17,13.2,"Zinc","Indian reference; do not average with EFSA.");
        india("iodine","Iodine","ug",Kind.RDA,150,150,"Iodine","Annexure IA; the ICMR-NIN 2020 brief note agrees.");
        india("thiamine","Thiamine B1","mg",Kind.RDA,1.4,1.4,"Thiamine","Sedentary; different work categories have different figures.");
        india("riboflavin","Riboflavin B2","mg",Kind.RDA,2,1.9,"Riboflavin","Sedentary reference.");
        india("niacin","Niacin B3","mg",Kind.RDA,14,11,"Niacin equivalents","Sedentary; chemical-form conversion needs evidence.");
        india("vitamin_b6","Vitamin B6","mg",Kind.RDA,1.9,1.9,"Vitamin B6","Sedentary adequacy reference, not a UL.");
        india("folate","Folate","ug",Kind.RDA,300,220,"Folate","Do not treat folic acid and dietary folate equivalents as interchangeable.");
        india("vitamin_b12","Vitamin B12","ug",Kind.RDA,2.5,2.5,"Vitamin B12","Annexure IA (the ICMR-NIN 2020 brief note prints 2.2). Undeclared means unknown, including vegan products.");
        india("vitamin_c","Vitamin C","mg",Kind.RDA,80,65,"Vitamin C","Adult adequacy reference.");
        india("vitamin_a","Vitamin A","ug RE",Kind.RDA,1000,840,"Retinol equivalents","Retinol and carotenoids need chemical-form conversion.");
        india("vitamin_d","Vitamin D","ug",Kind.RDA,15,15,"Vitamin D","Annexure IA 600 IU, converted using Annexure IB 40 IU per ug.");
        india("vitamin_k","Vitamin K","ug",Kind.RDA,55,55,"K1/K2","Annexure IB.");
        india("biotin","Biotin","ug",Kind.AI,30,30,"Biotin","Annexure IB adequate intake (30 ug; 40 was a transcription error).");
        india("pantothenic","Pantothenic acid","mg",Kind.AI,5,5,"Pantothenic acid","Annexure IB adequate intake.");
        india("phosphorus","Phosphorus","mg",Kind.RDA,1000,1000,"Phosphorus","Annexure IB.");
        india("potassium","Potassium","mg",Kind.RDA,3500,3500,"Potassium","Do not optimise upward with kidney conditions or infer salt-substitute suitability.");
        india("copper","Copper","mg",Kind.RDA,2,2,"Copper","Annexure IA, other minerals (2 mg/day).");
        india("manganese","Manganese","mg",Kind.RDA,4,4,"Manganese","Annexure IB.");
        india("chromium","Chromium","ug",Kind.RDA,50,50,"Chromium","Annexure IB.");
        india("selenium","Selenium","ug",Kind.RDA,40,40,"Selenium","Annexure IB; do not confuse with European UL.");
        REGISTRY.put("vitamin_e",new Ref("vitamin_e","Vitamin E","mg",Kind.RDA,7.5,10.0,RDA_SOURCE,"India","Healthy adults",
            "Tocopherol","Food adequacy range 7.5-10 mg; no single target selected","2022-02-03","Preserve the range, not automatically its maximum."));
        REGISTRY.put("carbohydrate",new Ref("carbohydrate","Carbohydrate","% energy",Kind.ENERGY_RANGE,50.0,55.0,DGI_SOURCE,"India","General dietary pattern",
            "Carbohydrate","Whole-diet energy distribution 50-55%; not two sex-specific targets","2024-05-07","Do not sum independent maximum macro ranges or prescribe this to diabetes."));
        REGISTRY.put("vitamin_d_ul",new Ref("vitamin_d_ul","Vitamin D upper reference","ug VDE/day",Kind.UL,100.0,100.0,
            "https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2023.8145","Europe (EFSA fallback)","Adults",
            "Vitamin D equivalents; calcidiol needs conversion","Total chronic intake; not an adequacy goal or single-meal toxicity verdict","2023-08-08",
            "Indian adequacy is available; corresponding Indian UL not established in this app's verified sources. No exposure verdict without form and intake data."));
        for(String id:new String[]{"total_sugar","cholesterol","additive_exposure"}) REGISTRY.put(id,new Ref(id,id,"label-dependent",Kind.INFORMATION,null,null,
            LABEL_SOURCE,"India","General adults","Declared form","Information only; no numeric allowance","2025-09-09",
            id.equals("total_sugar") ? "Total sugars are not the same as added or free sugars." : "No exposure or remaining-intake verdict from a scanned ingredient name."));
    }
    static final class LabelReference {
        static final double ENERGY_KCAL=2000, TOTAL_FAT_G=67, SATURATED_FAT_G=22, TRANS_FAT_G=2, ADDED_SUGAR_G=50, SODIUM_MG=2000;
        private LabelReference() {}
    }
    static Ref get(String id) { return REGISTRY.get(id); }
    static List<Ref> all() { return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values())); }
    private NutrientReference() {}
}
