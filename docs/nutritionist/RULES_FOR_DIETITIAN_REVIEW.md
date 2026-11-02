# BetterChoice India: rules for a dietitian's review

Prepared 2026-10-01 from the app's evidence registry (216 entries, every quote checked against its document by `tools/evidence_check.py`). Regenerate with `python tools/dietitian_sheet.py`.

**What the app does.** BetterChoice reads a packaged food's label on Indian quick-commerce apps, rates it A/B/C on nutrients (Nutri-Score 2023, in three bands), and suggests up to three swaps. A shopper's profile (diet, goals, allergies, conditions, pregnancy or breastfeeding, body size) changes what is left out, what a swap must not worsen, and the personal lines on the card. It never prescribes a diet or says a food suits a condition.

**What we ask.** Every number below comes from a cited guideline or regulation. What we cannot settle ourselves is how those sources are *applied*. For each item, please tick one box and add any comment. Nothing here has been reviewed by a registered dietitian yet; until it is, the card's "How we calculated this" says the judgement calls await a dietitian's review.

| # | Rule | App does now | Decision needed |
|---|---|---|---|
| 1 | What counts as a better swap | 30% difference (FSSAI comparative claim) | Add an absolute minimum? |
| 2 | Protein reference in obesity | RDA on stated weight | Use an adjusted weight? |
| 3 | Vegetarian protein | 1 g/kg for vegetarian, Jain, vegan | Keep, or 0.83 g/kg? |
| 4 | Muscle-gain protein range | 1.4-1.6 g/kg | Keep the narrower range? |
| 5 | Phosphate additives in kidney disease | Left out of swaps, "Not for you" | Veto or caution? Which stages? |
| 6 | Kidney: protein not estimated | Hidden unless on dialysis | Agree? |
| 7 | Caffeine | Amount vs 300 mg/day; pregnancy note | A lower pregnancy figure? |
| 8 | Lactose | Upper estimate vs 12 g at a time | Agree with the method? |
| 9 | Jain and halal ingredient lists | Word lists below | Corrections? |
| 10 | Micronutrient references | FSSAI 2022 annexure | Annexure or ICMR brief note? |

## 1. What counts as a better swap

**Now:** a swap chosen for a goal (less energy, more protein, less sugar, more protein per calorie) must differ from the original by at least 30% on that nutrient, for the same portion. In awareness mode (no goal) it must be in a better band and cut the original's worst nutrient by 30%. A swap offered for its fibre must also be a "source of fibre": at least 3 g per 100 g, 1.5 g per 100 ml, or 1.5 g per 100 kcal.

**Source:** FSSAI Advertising and Claims Regulations 2018, regulation 5(4)(a) as substituted (operationalised 26 Oct 2021, compliance from 1 Jul 2022): "at least thirty per cent. in the energy value or nutrient content except micronutrient" (fssai-claims-2018-compendium-2022.pdf, p4, regulation 5(4)(a) as substituted). Schedule I item 14 for "source of fibre".

**Why it needs you:** the regulation governs claims on labels, and we borrow it as the bar for "meaningfully better". The 2018 text also required a minimum absolute difference: "at least twenty-five per cent. in the energy value or nutrient content and a minimum absolute difference", a condition the 2021 substitution dropped. Without it, a small baseline can pass (0.2 g to 0.14 g sugar is 30% less). Codex's earlier 25% figure had no source.

Effect of moving from 25% to 30%, replaying 71 rated product pages recorded on Zepto: generic swaps 91 -> 90.

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 2. Protein reference weight in obesity

**Now:** protein = ICMR RDA x stated body weight (pre-pregnancy weight during pregnancy), the same for every BMI. Example, persona P2 (man, 85 kg, 170 cm, BMI 29.4): 0.83 x 85 = 70.6 g a day.

**Alternative:** an adjusted weight, e.g. the weight at BMI 23 (the top of NIN 2024's normal range) for the person's height: 66.5 kg, giving 55.2 g. Version 3.5.0 did this; it was removed because no source on file states it.

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 3. Vegetarian protein

**Now:** 1 g/kg for Vegetarian, Jain and Vegan diets; 0.83 g/kg otherwise. Source: ICMR-NIN 2020 brief note, p5-6, Table 2b, Recommended protein for vegetarians: "Recommended protein for vegetarians Protein recommendation: RDA 2010 Body weight kg Protein g/kg/d Total daily Requirement (g) Males 60 1.0 60".

**Why it needs you:** ICMR's 2020 RDA of 0.83 g/kg assumes a cereal-legume-milk ratio of 3:1:2.5; Table 2b keeps the 2010 figure for vegetarians. Eggetarian diets get 0.83 g/kg.

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 4. Muscle-gain protein range

**Now:** 1.4-1.6 g/kg a day, shown only with a muscle-gain goal. Lower bound: ISSN 2017, "1.4-2.0 g protein/kg body weight/day" for most exercising individuals. Upper bound: NIN 2024, "greater than ~1.6g/kg/day do not contribute any".

**Why it needs you:** the app shows only where the two sources agree. ISSN's full range goes higher.

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 5. Phosphate additives in kidney disease

**Now:** for anyone who records a kidney condition (any stage, on dialysis or not), a product declaring INS 338, 339, 340, 341, 343, 450, 451, 452, 541 shows "Not for you" and is left out of swaps.

**Source:** KDIGO 2017 CKD-MBD update, recommendation 4.1.8: "In patients with CKD G3a–G5D, we suggest limiting dietary phosphate intake" (2D), and "It is reasonable to consider phosphate source (e.g., animal, vegetable, additives) in making dietary recommendations" (Not Graded).

**Why it needs you:** KDIGO addresses stages G3a-G5D and the treatment of hyperphosphataemia; the form does not ask stage beyond "on dialysis". Options: keep the veto; make it a caution on the product (still left out of swaps); apply it only on dialysis. Please also check the code list.

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 6. Kidney disease: protein not estimated

**Now:** for a kidney condition not on dialysis, no protein figure is shown ("depends on your kidney function") and swaps may not have more protein. On dialysis, protein is estimated as usual and not constrained. Potassium salt substitutes are left out (who-salt-substitutes-2025.pdf, PDF p12).

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 7. Caffeine

**Now:** the card states the caffeine in the product: the declared amount, else NIN 2024's per-cup ranges (80-120 mg brewed coffee, 50-65 mg instant, 30-65 mg tea, per 150 ml cup), else WHO's "soft drinks (colas and iced tea) usually contain less than 50 mg per 250 mL serving" for colas. Each is set against NIN 2024's tolerable limit of 300 mg a day. In pregnancy the card adds WHO's antenatal recommendation A.10 ("For pregnant women with high daily caffeine intake (more than 300 mg per day)") and NIN's advice to minimise caffeine. Caffeine is never a veto unless the label itself says the product is not for pregnant or lactating women.

**Why it needs you:** EFSA's 2015 caffeine opinion and 2010 lactose opinion could not be downloaded (efsa.europa.eu refused every request), so neither is used. Should pregnancy or breastfeeding use a lower daily figure, or should energy drinks be left out in pregnancy even when the label carries no warning?

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 8. Lactose

**Now:** for a shopper with lactose intolerance (and no milk allergy), dairy products show "up to X g lactose": the label's sugars less any declared added sugar (milk's sugar is lactose: "while lactose is found in milk, sucrose is the table sugar"). It is compared with 12 g, the lower end of "12 to 15 g of lactose (approximately 1 cup of milk) is well tolerated by most adults" (lactose-review-2010.json, Shaukat et al. 2010 systematic review, abstract), and the card adds NIN's advice: "reducing the quantity of milk taken at a time or taking milk along with a cereal-pulse meal or taking milk in the form of curd". A "lactose-free" claim means at most 0.1 g per 100 g (fssai-claims-2018-compendium-2022.pdf, p14, Schedule I item 18 (as substituted)).

**Why it needs you:** the estimate is an upper bound (fruit sugars and fermentation lower the true figure), and the tolerated amount is per sitting, not per day.

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 9. Jain and halal ingredient lists

Practice varies; the app says so on the card, and for halal only a halal mark confirms it.

- Jain, left out: onion, garlic, potato, carrot, radish, beetroot, beet root, ginger, turnip, sweet potato, yam, honey, mushroom
- Halal, left out: pork, ham, bacon, lard, pepperoni, wine, rum, beer, brandy, whisky, liqueur, alcohol, ethanol
- Halal, caution (source decides): gelatin, gelatine, rennet, animal fat, tallow, carmine
- Emulsifiers whose source is rarely stated (caution for vegetarian and halal): INS 471, 472e, 481, 482, 631, 627, 635

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## 10. Micronutrient references (not yet shown to shoppers)

The app's micronutrient table follows FSSAI's RDA direction of 3 Feb 2022, read from its scanned annexures by OCR (`security/evidence/reference-rda-annexure-ocr.txt`; the page images are in `security/evidence/rda-annexure/`). Corrected on 1 Oct 2026: biotin 30 ug (was 40), copper 2 mg (was 1.7), iodine 150 ug (was 140), magnesium 385/325 mg (was 440/370), vitamin B12 2.5 ug (was 2.2), fibre 25 g (WHO; was 30/25 with no source). The ICMR-NIN 2020 brief note disagrees with the annexure on:

- `icmr.brief.magnesium.male` = 440 mg/day (icmr-nin-2020-brief-note.pdf, p6, Table 3 (males), RDA 2020 column): "Magnesium (mg) 340 440 370 400"
- `icmr.brief.magnesium.female` = 370 mg/day (icmr-nin-2020-brief-note.pdf, p6, Table 4 (females), RDA 2020 column): "Magnesium (mg) 310 370 310 310"
- `icmr.brief.b12` = 2.2 ug/day (icmr-nin-2020-brief-note.pdf, p6, Tables 3-4, RDA 2020 column): "Vitamin B12 (µg) 1 2.2 2 2.4"

**Why it needs you:** which source should the app follow, and please confirm the OCR-read values against the scans.

Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________

## Settled by a source (no decision needed)

These were shown as "provisional" in version 3.5.0 and now rest on a guideline or regulation:

- swap threshold: `fssai.claims.comparative.min`, fssai-claims-2018-compendium-2022.pdf, p4, regulation 5(4)(a) as substituted
- vegetarian protein: `icmr.protein.vegetarian`, icmr-nin-2020-brief-note.pdf, p5-6, Table 2b, Recommended protein for vegetarians
- muscle-gain lower bound: `issn.protein.muscle.min`, issn-protein-2017.json, abstract, point 3
- kidney phosphate additives: `kdigo.phosphate.additives`, kdigo-2017-ckd-mbd.xml, KDIGO 2017 CKD-MBD update, Recommendation 4.1.8 (Not Graded), CKD G3a-G5D

Reviewer: ______________________  Registration no.: __________  Date: __________  Signature: __________
