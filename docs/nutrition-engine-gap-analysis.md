# Nutrition engine: what the report asked for vs what is actually built

Checked rigorously against *BetterChoice India Nutrition Intelligence Platform: A Safety-First
Architecture*, as of 21 September 2026, app version 2.4.0.

**Headline: roughly a third of the report is implemented.** The safety architecture — the part the
report argues matters most — is in. Most of the intelligence layer above it is not.

---

## Built

| # | Report requirement | Where |
|---|---|---|
| 1 | Safety gate, **never averaged into a score** | `SafetyGate` — returns a verdict, and a test asserts the result class has no numeric field |
| 2 | Immutable priority hierarchy, safety above goals and price | `SafetyGate`, order fixed in code |
| 3 | Allergen state machine; "no allergies recorded" ≠ allergy-free | `SafetyProfile.Allergies` — KNOWN / NONE_KNOWN / UNSURE / NOT_ANSWERED |
| 4 | Never certify a food safe | tested: every mention of safety must carry a negation |
| 5 | Severe allergy treats "may contain" as real | `SafetyGate`, tested against the Beyond Snack declaration |
| 6 | Missing allergen info **blocks** rather than assuming fine | `SafetyGate`, `no_allergen_information` |
| 7 | Two allergen detectors: declaration **and** ingredient list | `Allergen.scan` |
| 8 | Diet as a hard constraint, vegan ≠ vegetarian on milk/egg | `SafetyGate.animalIngredient` |
| 9 | Normalise to per 100 g / per serving / per pack | `PackMath`, `NutritionParser` |
| 10 | NOT_DECLARED ≠ ZERO | nulls preserved throughout; `DailyImpact` omits undeclared nutrients |
| 11 | Versioned nutrient reference registry **as data, not code** | `NutrientReference`, version `icmr-nin-2020+who+fssai-1` |
| 12 | Four reference kinds: MINIMUM / UPPER / RANGE / INFORMATION | `NutrientReference.Kind` |
| 13 | LABEL_REFERENCE separate from PERSONAL_REFERENCE | `NutrientReference.LabelReference` vs `DailyNeeds` |
| 14 | Total sugar ≠ added sugar ≠ free sugar | separate registry entries with different kinds |
| 15 | Deterministic engine, versioned, no LLM in the nutrition path | `DailyNeeds`, `DailyImpact`, `NutriGrade` — all arithmetic |
| 16 | Personal reference from life-stage/sex/body | `DailyNeeds`, calibrated to ICMR reference adults |
| 17 | Portion truth: whole pack, not just the flattering serving | `DailyImpact.forPack` |

## Not built

| # | Report requirement | Status | Why it matters |
|---|---|---|---|
| 18 | **Four separate outputs** (quality / personal fit / safety / confidence) | Partial | Safety is separate. Quality is still a single A/B/C band, personal fit is a set of percentages, and **confidence is not computed at all** |
| 19 | **Confidence object** per field | Missing | The report wants `nutrition: 0.99, ingredients: 0.98`. Nothing carries a numeric confidence today |
| 20 | Ingredient knowledge graph: order, compound ingredients, nesting | Missing | Ingredient text is captured and scanned for allergens but never parsed into positions |
| 21 | INS additive table, explained not condemned | Missing | The flash cards mention INS numbers; no lookup exists |
| 22 | AS_SOLD vs AS_PREPARED | Missing | A soup powder and the made-up soup are silently comparable today. Real defect |
| 23 | Evidence object behind every statement | Missing | Cannot reconstruct "why did it say that" six months later |
| 24 | Version the user state used in each decision | Missing | No `analysis_id` / `ruleset_version` / `profile_version` record |
| 25 | Lactose intolerance ≠ milk allergy, with severity | Missing | Today lactose is folded into the MILK allergen group |
| 26 | Non-sugar sweetener nuance (WHO 2023) | Missing | A "zero sugar" drink can still score well with no caveat |
| 27 | Bounded micronutrient bonus | N/A then Missing | No micronutrient scoring exists, so fortification cannot yet rescue a bad product — but nothing prevents it once added |
| 28 | Category-aware alternatives, same platform | Missing | No alternatives engine at all |
| 29 | Condition modules: diabetes, hypertension, CKD, pregnancy | **Deliberately not built** | Needs clinical governance. A half-built condition model is worse than none |
| 30 | Clinician-entered nutrient targets | Missing | Follows from 29 |
| 31 | Child / adolescent mode | Missing | App is 18+; a minor should not get adult targets |
| 32 | Golden dataset, 5000 SKUs × profiles | Missing | Test fixtures are hand-written, not a corpus |
| 33 | Rules as YAML/data with owner, source, review date | Missing | Safety rules are Java, not data |
| 34 | Clinical governance pipeline (author → review → shadow → release) | Missing | Process, not code |
| 35 | European / France ruleset | Missing | Registry is structured to allow it; nothing built |

## The one thing to fix next

**Nothing asks the shopper about allergies.** The gate works and is tested, but every user is in
`NOT_ANSWERED`, so it only ever informs — it never blocks. Until onboarding asks, item 1 above is
scaffolding rather than protection.

---

## Daily values for a healthy adult — the baseline now in the registry

Man and woman are the ICMR-NIN 2020 reference adults: 20–39 years, 65 kg and 55 kg, sedentary work.

### Energy and macronutrients

| Nutrient | Man | Woman | Kind | Source |
|---|---:|---:|---|---|
| Energy | 2110 kcal | 1660 kcal | range | ICMR-NIN 2020 |
| Protein | 54 g | 46 g | minimum | ICMR (0.83 g/kg; 1 g/kg cereal-based) |
| Dietary fibre | 30 g | 30 g | minimum | ICMR (per 2000 kcal) |
| Total fat | ≤30% energy | ≤30% energy | range | WHO |
| Saturated fat | <10% energy | <10% energy | upper | WHO |
| Trans fat | <1% energy | <1% energy | upper | WHO |
| Added / free sugar | <10% energy (5% better) | same | upper | WHO |
| Total sugars | — | — | information | includes milk and fruit sugars |
| Carbohydrate | quality over quantity | — | range | WHO |
| Cholesterol | — | — | information | shown, never scored |

### Minerals

| Nutrient | Man | Woman | Kind | Source |
|---|---:|---:|---|---|
| Sodium | <2000 mg | <2000 mg | upper | WHO (= <5 g salt) |
| Calcium | 1000 mg | 1000 mg | minimum | ICMR |
| Iron | 19 mg | **29 mg** | minimum | ICMR |
| Zinc | 17 mg | 13 mg | minimum | ICMR |
| Magnesium | 440 mg | 370 mg | minimum | ICMR |
| Iodine | 150 µg | 150 µg | minimum | ICMR |
| Potassium | 3510 mg | 3510 mg | minimum | secondary — **never optimise upward without kidney status** |
| Phosphorus | 1000 mg | 1000 mg | minimum | secondary |
| Selenium | 40 µg | 40 µg | minimum | secondary (Adequate Intake) |
| Copper | 2 mg | 2 mg | minimum | secondary |
| Manganese | 4 mg | 4 mg | minimum | secondary |
| Chromium | 50 µg | 50 µg | minimum | secondary |

### Vitamins

| Nutrient | Man | Woman | Kind | Source |
|---|---:|---:|---|---|
| Vitamin A | 1000 µg | 840 µg | minimum | ICMR |
| Vitamin D | 600 IU | 600 IU | minimum | ICMR |
| Vitamin C | 80 mg | 65 mg | minimum | ICMR |
| Vitamin E | 10 mg | 10 mg | minimum | secondary (quoted 7.5–10) |
| Vitamin K | 55 µg | 55 µg | minimum | secondary |
| Folate (B9) | 300 µg | 220 µg | minimum | ICMR — rises sharply in pregnancy, not modelled |
| Vitamin B12 | 2.2 µg | 2.2 µg | minimum | ICMR — watch on a vegan diet |
| Thiamine (B1) | 1.8 mg | 1.7 mg | minimum | **conflicted** |
| Riboflavin (B2) | 2.5 mg | 2.4 mg | minimum | **conflicted** |
| Niacin (B3) | 18 mg | 14 mg | minimum | **conflicted** |
| Vitamin B6 | 2.4 mg | 1.9 mg | minimum | **conflicted** |
| Pantothenic acid (B5) | 5 mg | 5 mg | minimum | secondary |
| Biotin (B7) | 40 µg | 40 µg | minimum | secondary |

### FSSAI label reference — an average adult on a pack, **not** any real person

2000 kcal · total fat 67 g · saturated fat 22 g · trans fat 2 g · added sugar 50 g · sodium 2000 mg

### Verification status

- **Primary** — read directly from the official ICMR-NIN summary (`nin.res.in/rdabook/brief_note.pdf`),
  WHO, or FSSAI. Energy, protein, fibre, calcium, iron, zinc, magnesium, iodine, vitamins A/D/C,
  folate, B12, and all the WHO limits.
- **Secondary** — from a summary of ICMR-NIN 2020, not the full book. Potassium, phosphorus,
  selenium, copper, manganese, chromium, vitamin E, vitamin K, B5, biotin. **Confirm before launch.**
- **Conflicted** — the official tables and the secondary summary disagree on **thiamine, riboflavin,
  niacin and B6**. These scale with energy intake, so the two may simply be different activity
  levels. The app carries the official figures and flags the disagreement rather than hiding it.
  **A reviewer must settle these four.**

Labels almost never declare micronutrients, so these are shown as what a day needs — never as
something the app has measured in a product.
