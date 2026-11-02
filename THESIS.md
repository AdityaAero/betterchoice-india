# The thesis behind BetterChoice India

> **In one sentence:** at the moment of choice on a quick-commerce app, a single trustworthy colour plus one
> concrete, same-category, personally safe alternative can make the healthier product the easy product,
> provided every number behind it is sourced, every unknown is admitted, and the shopper's health data never
> leaves their phone.

This document sets out the problem, the hypothesis, the design principles that follow from it, how the app
implements them, what the evidence collected so far shows, and what is still unproven.

---

## Contents

1. [The problem](#1-the-problem)
2. [The hypothesis](#2-the-hypothesis)
3. [Design principles](#3-design-principles)
4. [How the system works](#4-how-the-system-works)
5. [What the evidence so far shows](#5-what-the-evidence-so-far-shows)
6. [Limitations](#6-limitations)
7. [Research questions and validation plan](#7-research-questions-and-validation-plan)
8. [Roadmap: from screen to voice](#8-roadmap-from-screen-to-voice)
9. [References](#9-references)

---

## 1. The problem

**Diet-related disease is now common in India.** The ICMR-INDIAB national study (2023) estimated that **11.4%** of
Indian adults have diabetes, **15.3%** have prediabetes and **35.5%** have hypertension [1]. NFHS-5 (2019–21)
found about **one in four** adults aged 15–49 overweight or obese [2]. Packaged foods high in fat, sugar and salt
(HFSS) are a recognised contributor, and the ICMR-NIN *Dietary Guidelines for Indians* (2024) advise limiting
them [3].

**The moment of purchase has moved to a phone screen.** Packaged food is increasingly bought on 10-minute
delivery apps such as Blinkit, Zepto, Swiggy Instamart and Flipkart Minutes. On these apps:

- The nutrition panel is usually a **back-of-pack photo** among marketing images, or it sits behind a
  "View details" sheet, and sometimes it is not shown at all.
- Product pages are designed for speed: a choice takes seconds.
- Retailer-typed nutrition text is **not always faithful to the pack** (§5.3).
- There is no healthier-alternative prompt. Search and ranking optimise for conversion, not nutrition.

**Labels alone do not solve it.** The FSSAI *Labelling and Display* regulations (2020) require a nutrition panel
[4], but a table of numbers per 100 g is hard to compare under time pressure. Front-of-pack schemes such as
Nutri-Score were designed to make that comparison instant [5].

## 2. The hypothesis

> **H1.** Shown at the moment of choice, a single colour derived from the pack's own nutrients, together with
> one to three healthier products *from the same category*, shifts purchases towards healthier products more than
> nutrition information alone.

The design rests on three premises:

1. **The swap is the intervention; the colour is the trigger.** A red light without an alternative leaves the
   shopper with nothing to do. A same-category swap (better chips, not a salad) keeps the decision small.
2. **Trust is earned by provenance.** A colour is only persuasive if it is right. Every threshold must trace
   to a regulation or guideline, and every missing value must be admitted, not guessed.
3. **Personal relevance must not compromise safety or privacy.** Allergies, diet rules, pregnancy and chronic
   conditions change which swaps are *safe*. They must never be weakened to show more options, and they must
   stay on the device.

## 3. Design principles

| Principle | What it means in the app |
|---|---|
| **Calculator-first** | No number on a card is written by hand or by a language model. Every constant lives in `Evidence` with its source document in `security/evidence/`, and tests fail if a constant cites a missing file or the calculator types a number of its own. |
| **Colour from nutrients only** | The 🟢/🟡/🔴 band is the same for everyone. Personalisation adds vetoes, "for you" notes and the ranking of swaps; it never changes the colour. |
| **Unknown is not zero** | If a missing value (say saturated fat) could change the colour, the product is not graded and the card says what is missing. |
| **Fixed order of care** | Safety (allergen, contraindication) → physiological state (pregnancy, lactation, age) → medical condition → body goal → healthy eating for everyone → taste and price. A lower level never undoes a higher one. |
| **Strict filters need positive evidence** | A swap shown to a vegetarian, vegan, Jain, halal or allergic shopper needs a mark, tag or clean ingredient declaration; silence is not consent. |
| **Same category** | Swaps come from the same substitution group as the product on screen, decided by the same name rules on the phone and in the catalogue builder. |
| **Refer, but keep helping** | Pregnancy with diabetes, dialysis and multiple conditions show a "talk to a dietitian" banner, while the safe filters keep working. No diet is prescribed; no food is said to treat a condition. |
| **Private by design** | Profile and health answers stay on the phone. No screenshots are stored. Reading is limited to allow-listed shopping apps, with a versioned consent screen. |
| **Robust fixes, not special cases** | A reading or grading error is fixed at its general cause and covered by a class-wide test, never by a rule for one product or one page. |

## 4. How the system works

### 4.1 Reading

`BubbleService` (an Android accessibility service, consented and allow-listed to shopping apps) observes the
product page. Three readers run, cheapest first:

1. **Page text:** the product title, highlights and the "details" sheet (`ScreenReader`, `ProductPage`).
2. **Pack photos:** on-device OCR (Google ML Kit text recognition) of the photo that looks like a nutrition
   table (`PackScanner`, `OcrRows`).
3. **Barcode:** ML Kit barcode scanning with GS1 check-digit validation (`Gtin`), used to identify the exact pack.

### 4.2 Parsing

`NutritionParser` turns label text into a `Nutrition` record, always **per 100 g or per 100 ml**. A per-serving
table is scaled only when the serving size is stated. It tolerates the usual OCR and label noise: lost decimal
points, "g" read as "9", wrapped rows, and columns for per-serve and %RDA. A repaired reading is marked uncertain.

### 4.3 Grading

`NutriGrade` and `GradeResult` implement **Nutri-Score 2023**, with its separate algorithms for general foods,
fats/oils/nuts/seeds, cheese and beverages (including the non-nutritive sweetener rule for drinks). The letter
is shown in three bands: A/B 🟢, C 🟡, D/E 🔴. Indian rules add the reasons and safety checks: FSSAI labelling and
additive limits (`AdditiveLimits`, `InsAdditives`) and the ICMR-NIN 2024 HFSS thresholds.

### 4.4 Personalisation

A short on-device profile (age band, sex, height, weight, activity, diet, allergens, conditions, goals) feeds
`NutritionCalculator` (energy and nutrient references with a derivation trace), `SafetyGate` (allergen, diet and
condition vetoes) and `RecommendationContext` (which objective ranks the swaps: weight loss, weight gain, muscle,
sugar control). The decision tree covers every combination of answers. Conflicting goals have defined overrides;
for example, a weight-loss objective is switched off during pregnancy or when BMI is under 18.5.

### 4.5 Recommendation

`SwapEngine` picks up to three alternatives from a curated catalogue (998 products, 31 categories) that:

- are in the same substitution group;
- pass the shopper's vetoes and condition constraints;
- are healthier by the same engine;
- and improve the shopper's objective.

When nothing qualifies, it relaxes in a fixed order and says what was relaxed. Vetoes and condition limits are
never relaxed.

### 4.6 Catalogue and database edition

The catalogue is built from Open Food Facts India records whose label photo is from 2025 or later, cleaned and
checked by the same rules the app uses (`tools/curate_swaps.py`). The **database edition** (`android-db/`) moves the
catalogue to Supabase Postgres. A single read-only RPC resolves a shop's product title by exact name, then by
alias, then by a hybrid of trigram similarity and a `pgvector` HNSW index. Strict acceptance rules guard against
wrong matches, and a phone-side cache keeps latency low. The shopper's health filters still run privately on the
phone.

## 5. What the evidence so far shows

All figures below come from this repository's own tools and test runs (October 2026).

### 5.1 Healthier options are unevenly available

Grading the full catalogue with the app's engine:

| Colour | Products |
|---|---|
| 🟢 Green | 230 |
| 🟡 Yellow | 177 |
| 🔴 Red | 583 |
| Not rated (a figure that could change the colour is missing) | 8 |

- **14 of 31 categories have no green product at all** under Nutri-Score 2023: ice cream, chocolate, biscuits,
  cheese, jams, ready meals, sauces, mayonnaise and dips, instant soups, pickles, Indian sweets, cakes, dried
  fruit, and masalas.
- **5 categories have neither green nor yellow**: biscuits, jams, mayonnaise and dips, instant soups, Indian sweets.
- Only **4 categories** reach the target of 15 green and 10 yellow: milk drinks, breakfast cereals, curd and
  yoghurt, and bread.

This is a finding in itself. In much of the packaged "treat" aisle, the healthiest available choice is a
*less unhealthy* one, so the app must be able to say "this is the better red" honestly instead of implying it is
healthy.

### 5.2 Reading labels from retailer photos is feasible, but identity is the bottleneck

A field test on Blinkit (25–26 October 2026) collected 91 product pages. Each label was read by OCR from the pack
photo and cross-checked against Blinkit's own nutrition text; discrepancies were settled by reading the photo.

- **The installed app's live colour agreed with the cross-checked label colour for 53 of 55 products** (96%).
- **51% of products (46 of 91) had no readable barcode in any retailer photo**: printed too small, blurred, hidden
  behind a sticker box, or absent (QR code only). Without a barcode a product cannot be identified uniquely, so
  it cannot be added to the catalogue. **Identity, not nutrition, is the main data bottleneck.**
- A check-digit-valid barcode was decoded from a photo that contained **no barcode at all**. A single decode is
  therefore never trusted: a code must be read twice, appear printed, match an open-data record for the same
  brand, or be confirmed by a person.
- Some packs use GS1 **restricted-circulation numbers** (prefix 20–29), which are not global identifiers and must
  never be used as keys.

### 5.3 Retailer text is not the label

- **Rounding changes colours.** One retailer page rounded saturated fat from "not more than 3.97 g" to "3 g",
  which turned a red product yellow.
- **Typos are copied.** One label printed "Sodium 210.00 g" (its own %RDA shows 210 mg), and the retailer text
  repeated it. Another printed "Sodium (g) 7" against an edible-salt ingredient list, so the sodium is genuinely
  unknowable from the pack.
- **Required figures are missing.** A mustard oil label had no saturated-fat row, and a paneer label had no sodium.
  The engine correctly declined to grade both ("Missing: saturated fat, which could change the colour").

### 5.4 OCR errors are systematic and fixable at their cause

Recurring patterns:

- "0 g" read as "60";
- "7g" read as "79";
- "<0.5" read as "40.5";
- a "Mono*unsaturat*ed" row taken for saturated fat;
- "45gServings" glued together.

Each was fixed as a general rule (unit-aware column reading, an energy-balance check that macros must add up to
the stated energy within 20%, word-boundary nutrient names), never as a special case.

## 6. Limitations

- **Nutritionist sign-off is pending.** Every catalogue record is a draft until a qualified nutritionist checks
  it against the pack. Release builds show no drafts.
- **Nutri-Score was developed and validated in Europe.** Its fit for Indian staples (ghee, paneer, namkeen,
  mithai) is an open question. The app shows Indian HFSS reasons alongside, and would show FSSAI's Indian
  Nutrition Rating once it is in force.
- **The accessibility API is a policy risk.** App stores and newer Android versions restrict accessibility
  services that are not for disability support. A screen-capture (MediaProjection) path is on the roadmap.
- **Coverage is thin where it matters most** (§5.1), and limited by identifiable packs (§5.2).
- **No outcome evidence yet.** H1 is untested in the field; agreement and coverage figures are about the
  instrument, not its effect on behaviour.

## 7. Research questions and validation plan

| # | Question | Method |
|---|---|---|
| RQ1 | How accurately does the app read and grade what is on the pack? | Paired comparison against physical packs across apps and categories: per-nutrient agreement and colour agreement. |
| RQ2 | How often is a healthier same-category alternative available? | Coverage of viewed products with at least one green or yellow swap, by category and city. |
| RQ3 | Does the swap change what people buy? | Randomised field trial: colour only vs colour + swap vs control, measured on basket nutrient quality. |
| RQ4 | Is personalisation safe? | Exhaustive decision-matrix tests and adversarial cases: zero allergen or diet false negatives; no weight-loss ranking in pregnancy. |
| RQ5 | Who is left out? | Usability with low-literacy, elderly and non-English users, which motivates voice (below). |

## 8. Roadmap: from screen to voice

The current app is visual: a bubble and a card. The next step is a **voice assistant** for the same engine, so
that a shopper can ask "*Is this good for my sugar?*" and hear the answer in English or Hindi. This matters most
for people who find small print and English labels hardest, often the people with the most to gain. The engine,
evidence and privacy rules stay the same; only the way of asking and answering changes. Other next steps:

- nutritionist verification of the catalogue;
- more green and yellow swaps in thin categories;
- the MediaProjection capture path;
- the Indian Nutrition Rating once in force;
- the RQ3 field trial.

## 9. References

1. Anjana RM, Unnikrishnan R, Deepa M, et al. Metabolic non-communicable disease health report of India: the
   ICMR-INDIAB national cross-sectional study (ICMR-INDIAB-17). *Lancet Diabetes Endocrinol.* 2023;11(7):474–489.
2. International Institute for Population Sciences (IIPS) and ICF. *National Family Health Survey (NFHS-5),
   2019–21: India.* Mumbai: IIPS; 2021.
3. ICMR–National Institute of Nutrition. *Dietary Guidelines for Indians.* Hyderabad: ICMR-NIN; 2024.
   (On file: [`android/security/evidence/research-nin-2024.pdf`](android/security/evidence/research-nin-2024.pdf))
4. Food Safety and Standards Authority of India. *Food Safety and Standards (Labelling and Display)
   Regulations, 2020.* (On file: [`android/security/evidence/fssai-labelling-2020-v8-2025.pdf`](android/security/evidence/fssai-labelling-2020-v8-2025.pdf))
5. Scientific Committee of the Nutri-Score. *Update of the Nutri-Score algorithm: report* (foods, 2022;
   beverages, 2023). (On file: [`android/security/evidence/nutriscore-2022-update-food.pdf`](android/security/evidence/nutriscore-2022-update-food.pdf))
6. Open Food Facts. *Open Food Facts database*, Open Database License (ODbL) 1.0. https://world.openfoodfacts.org

---

*BetterChoice India gives general nutrition information from product labels. It is not medical advice.*
