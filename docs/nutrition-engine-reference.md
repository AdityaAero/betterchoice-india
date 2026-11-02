# Daily requirements engine — figures, sources and what they can honestly claim

Written for the nutrition/regulatory reviewer the architecture report calls for, and for the
engineers maintaining `DailyNeeds.java` and `DailyImpact.java`. **Every number below should be
signed off by a qualified reviewer before this reaches real customers.** It is implemented as a
deterministic, versioned engine precisely so that it can be audited rather than trusted.

Engine version: `icmr-nin-2020+who-1`

---

## 1. Where each figure comes from

### ICMR-NIN, *Nutrient Requirements for Indians*, RDA 2020

Verified against the NIN summary document (`nin.res.in/rdabook/brief_note.pdf`), Tables 1a, 2a, 3, 4.

**Energy, kcal/day (Table 1a)**

| | Sedentary | Moderate | Heavy |
|---|---:|---:|---:|
| Adult man | **2110** | 2710 | 3470 |
| Adult woman | **1660** | 2130 | 2720 |

The engine uses the **sedentary** column as its default. People shopping through a delivery app are
not, on average, doing heavy work, and overstating the energy budget would make every product look
proportionally harmless. Activity level is not currently asked during onboarding.

Pregnancy (+350) and lactation (+600 / +520) are **deliberately not implemented**. The app does not
ask about pregnancy and must not infer it.

**Protein (Table 2a)** — RDA **0.83 g/kg/day** (EAR 0.66). ICMR adds a footnote: for cereal-based
diets with lower protein quality, **1 g/kg/day**. The engine applies the footnote to vegetarian and
vegan profiles, which is the common Indian case.

**Dietary fibre** — **30 g per 2000 kcal**, scaled to the person's energy.

**Reference adult** — 20–39 years, **65 kg** (man), **55 kg** (woman). Used when weight is skipped.

**Micronutrients (Tables 3 and 4), RDA 2020**

| Nutrient | Man | Woman |
|---|---:|---:|
| Calcium | 1000 mg | 1000 mg |
| Iron | 19 mg | **29 mg** |
| Zinc | 17 mg | 13 mg |
| Magnesium | 440 mg | 370 mg |
| Vitamin C | 80 mg | 65 mg |
| Folate | 300 µg DFE | 220 µg DFE |
| Vitamin B12 | 2.2 µg | 2.2 µg |
| Vitamin A | 1000 µg | 840 µg |
| Vitamin D | 600 IU | 600 IU |

These are shown as **targets for the day only**. Indian packaged-food labels almost never declare
iron, calcium or zinc, so the app cannot and does not claim to measure progress against them.

### WHO limits

| Limit | Value | Basis |
|---|---|---|
| Free sugars | < 10% of energy (better: < 5%) | share of energy, 4 kcal/g |
| Saturated fat | < 10% of energy | share of energy, 9 kcal/g |
| Trans fat | < 1% of energy | share of energy, 9 kcal/g |
| Sodium | **< 2000 mg/day** (salt < 5 g) | absolute, same for every adult |

---

## 2. How a personal energy figure is produced

Onboarding already collects gender, age, weight and height. When all four are present:

```
BMR (Mifflin-St Jeor)
  man   = 10·kg + 6.25·cm − 5·age + 5
  woman = 10·kg + 6.25·cm − 5·age − 161

maintenance = BMR × 1.35        (sedentary)
```

**The 1.35 multiplier is calibrated, not borrowed.** It is chosen so the personal estimate
reproduces ICMR-NIN's own reference figures at the reference body:

| | Mifflin × 1.35 | ICMR-NIN 2020 | Difference |
|---|---:|---:|---:|
| Man, 65 kg, 170 cm, 30 y | 2116 kcal | 2110 kcal | +0.3% |
| Woman, 55 kg, 160 cm, 30 y | 1673 kcal | 1660 kcal | +0.8% |

So the two halves of the engine agree with each other instead of differing by a few hundred
kilocalories depending on which fields the user filled in. This agreement is pinned by a test.

**Goal adjustment** — about half a kilogram a week, the rate that is sustainable and does not strip
lean mass:

| Goal | Change | Protein |
|---|---:|---|
| Weight loss | −500 kcal | 1.2 g/kg |
| Weight gain | +400 kcal | ICMR baseline |
| Muscle gain | +300 kcal | 1.6 g/kg |
| Improve nutritional awareness | none | ICMR baseline |

Hard floor: **1200 kcal** (women) and **1500 kcal** (men). Below these, a day of food cannot
reliably supply the micronutrients in the table above, so the deficit is capped regardless of the
arithmetic.

The 1.2 and 1.6 g/kg protein figures are **sports-nutrition practice, not ICMR**. The app says so on
screen wherever it uses them.

---

## 3. What the predictor actually predicts

`DailyImpact` divides label values by the limits above, for one label serving and for the whole
pack. It is arithmetic, not a model — reproducible, checkable by hand, and identical every run.

Worked example, the Beyond Snack banana chips panel (461.23 kcal, sat fat 7.22 g, sodium
2025.64 mg per 100 g), against a 65 kg / 170 cm / 30-year-old man:

| Portion | Sodium | % of a 2000 mg day |
|---|---:|---:|
| One 20 g serving | 405 mg | 20% |
| The 80 g pack | 1620 mg | **81%** |

That gap is the point of the feature. The serving looks unremarkable; the pack, which is what
actually gets eaten, is four fifths of a day's sodium.

### Reliability — what this can and cannot claim

Two errors stack, and **they do not stack equally**:

| Line | Numerator | Denominator | Honest status |
|---|---|---|---|
| **Sodium %** | label value | WHO's 2000 mg, absolute for every adult | **firm** — no profile error at all |
| Energy, sugar, sat fat % | label value | estimated daily energy | **a band** — inherits the estimate's error |

Mifflin-St Jeor reproduces measured BMR within roughly ten per cent for most adults, and worse for
some; body composition, not height and weight, drives the residual. And the label itself is
approximate — the Beyond Snack panel is literally headed *"Approximate Composition"*, and FSSAI
permits tolerances.

So the engine marks each line `firm` or not, and the card states the basis rather than printing
every figure to the same false precision. **An app that says "81% of your sodium" about a firm WHO
limit and "about a fifth of your energy" about an estimate is more accurate than one that prints
both to two decimal places.**

### What it deliberately does not do

- No inference of diabetes, hypertension, pregnancy or any condition from shopping behaviour.
- No medical advice. It is guidance for healthy adults.
- No assumption of sex from a name; a non-binary or withheld gender falls back to a neutral 2000 kcal
  day, which is the basis Indian labels themselves use for % RDA.
- No invented zeros: a nutrient the label did not declare is left out, never shown as 0.

---

## 4. Open questions for the reviewer

1. Is **sedentary** the right default, or should onboarding ask activity level and use the moderate
   column for some users?
2. ICMR's cereal-diet protein footnote is applied to all vegetarian and vegan profiles. Is that the
   correct reading, or should it depend on the actual staple pattern?
3. Should the **5% free-sugars** target be shown as the headline instead of 10%, given Indian
   consumption patterns?
4. Trans fat is computed at 1% of energy but Indian labels increasingly declare 0 g. Is a percentage
   meaningful here, or should any non-zero trans fat simply be flagged outright?
5. Should micronutrient targets be shown at all while no label declares them, or does showing an
   un-measurable target erode trust?
