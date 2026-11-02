# BetterChoice India: healthier-swap catalogue, review guide

**For:** the BetterChoice nutritionist  ·  **Round:** v1.1 (draft)  ·  **Updated:** 26 September 2026
(biscuits and drinks expanded; ingredient facts added)

## What this is, and what we need from you

When a shopper looks at a product in Blinkit, Zepto, Instamart or Flipkart, BetterChoice rates it
A/B/C. For B and C products it now suggests up to **three healthier swaps** of the same kind. The
swaps come from this catalogue.

A curation assistant (a script) has **drafted** the catalogue from Open Food Facts, a
volunteer-entered open database. It is a lead list, not verified data. Your review is what makes a
product trustworthy:

- **Draft products** are shown only in our internal test build, labelled "Draft, not yet checked by
  our nutritionist".
- **Only products you verify appear for real users.**

**Please:** check each row against the physical pack (or the brand's official label image), correct
any figures, approve or reject it as a swap, and add strong products that are missing. The biggest
gaps are chocolate, salty snacks and juices. At roughly 2 minutes per product, the 167 draft rows
take about 5–6 hours; biscuits and milk drinks (66 rows) are the priority.

## Files

| File | What it is |
|---|---|
| `swap_catalogue_review_v1.csv` | **The one to fill in.** 167 draft swap candidates, one row each. Opens in Excel or Google Sheets. Now includes the official Nutri-Score letter, what the ingredient list says, and the list itself (first 300 characters). |
| `rejected_records_v1.csv` | 2,943 records the assistant threw out, each with the reason. Useful for finding products worth re-entering from the pack. |
| `NUTRITIONIST_REVIEW_GUIDE.md` | This guide. |

## How to fill in the review sheet

Values are **per 100 g** (or **per 100 ml** for drinks), exactly as the pack's nutrition table
states them.

| Column | What to enter |
|---|---|
| `VERIFIED_FROM_PACK (Y/N)` | **Y** once you have checked the figures against the pack or the brand's official label. |
| `CORRECTED_VALUES` | Only figures that differ from the pack, e.g. `sugars_g=3.2; sodium_mg=120`. Names: `energy_kcal, sugars_g, saturated_fat_g, sodium_mg, protein_g, fibre_g, total_fat_g, trans_fat_g`. |
| `APPROVE_AS_SWAP (Y/N)` | **Y** if you would recommend it in place of a typical product in its group. **N** removes it. |
| `OCCASIONS` | Optional. Any of `snack; breakfast; meal; party`, separated by `;`. Blank keeps the group default. |
| `NOTES` | Anything we should know: reformulated, discontinued, regional only. |

**Adding a product** that is missing: add a new row and fill in `group`, `brand`, `name`, `pack`,
`unit` (g or ml), `gtin` (the barcode number, if known), the five core figures (`energy_kcal`,
`sugars_g`, `saturated_fat_g`, `sodium_mg`, `protein_g`), `fibre_g` if stated, allergens in
`contains` / `may_contain`, and **Y / Y**. Please type the figures from the pack yourself rather than
copying them from another website.

When you're done, send the file back. One command
(`python tools/apply_review.py`) writes your decisions into the app's catalogue:
- approved rows become verified, with your corrections applied;
- rows marked N are removed;
- new rows are added as verified.

## The rules the app applies

These are enforced by code for every swap. You don't need to check them row by row, but please tell
us if any of them is wrong.

1. **Same substitution group, same side of food/drink.** No swapping a drink for a food.
2. **A better band** than the product on screen, re-graded by the app itself. Foods use Nutri-Score
   2017 per 100 g; drinks use the Nutri-Score 2023 beverage method per 100 ml. A = Nutri-Score
   A/B, B = C, C = D/E.
3. **A real improvement in the group's focus nutrient:** whichever of the group's focus nutrients
   the original scores worst on must fall by **at least 25%** (for fibre, rise by at least 25% and
   at least 1 g). **And no focus nutrient may get worse** (5% allowed for label rounding): baked
   chips with more sodium than fried chips are not the healthier salty snack.
4. **No new red flag.** The swap may not be "high" in anything the original was not (sugar, saturated
   fat, sodium, energy, at the thresholds the card uses). The one exception is a trade-off you allow
   for a group (see spreads below), which the card always states.
5. **Safe for this shopper,** checked on their phone: their allergens, "may contain" if their allergy
   is severe, and vegetarian/vegan. A product with no ingredient information is never offered to
   someone with allergies.
6. **"Still red, a step better" (draft rule, biscuits and milk drinks only).** Almost no sweet
   biscuit reaches green or yellow. In these groups, a swap that is still red but one official
   Nutri-Score letter better (**E → D**) is allowed. The card says so in those words. Where greener
   options exist, one of the three slots is kept for the best like-for-like step (a better biscuit),
   so the shopper is not only told "have crackers instead". **Please confirm or reject this rule.**
7. **What the ingredient list says** is shown beside each swap and each rated product, read only from
   a declared list (FSSAI order is by weight, so the first ingredient is what it is mostly made of):
   - **Good (green):** "Oats / Whole wheat / Ragi / Millet … is the main ingredient", "No added sugar
     (or sweeteners)", "No palm oil".
   - **Counterpart (plain):** "Refined flour (maida) is the main ingredient", "Sugar is the main
     ingredient".

   "Wheat flour" alone is never claimed as whole grain; "atta" is. These facts never change the band.
8. **At most three swaps, one per brand.** Price and stock are checked live on the shop's screen when
   the shopper taps a swap. The replacement may cost **at most twice** the original, counting several
   packs together if the swap comes in smaller packs.

## The substitution groups: please confirm or change

| Group | Focus (what a swap must improve) | Default occasions | Draft swaps | Questions for you |
|---|---|---|---|---|
| Salty crunchy snacks | sodium, saturated fat, energy | snack, party | 4 | Should roasted chana and makhana count as swaps for fried chips? (Currently yes.) |
| Nuts & seeds (snacking) | sodium, saturated fat | snack, party | 24 | Chia and flax are excluded as cooking ingredients, not snacks. Agree? |
| Biscuits & cookies | sugars, saturated fat | snack, breakfast | **35** (3 green/yellow + 32 "a step better") | Do you accept the E → D step rule? Should savoury crackers count as swaps for sweet biscuits? Marie, digestive, atta and oats biscuits dominate the steps — please check their sugar against packs. |
| Chocolate & confectionery | sugars, saturated fat | snack | **0** | No chocolate in the open data reaches A/B. Is there any chocolate you'd recommend, or should we swap to another group? |
| Breakfast cereals | sugars, fibre | breakfast | 49 | Should ready-to-eat cereals (Chocos, cornflakes) swap to porridge oats, or only to other ready-to-eat cereals? |
| Instant noodles & quick meals | sodium, saturated fat | meal, snack | 3 | Some "high protein" noodles show very low sodium; please check against packs. |
| Peanut butter & sweet spreads | sugars, saturated fat | breakfast, snack | 5 | **Draft rule:** a swap may be higher in energy (nut butters are denser than chocolate spreads) and the card says "higher in calories". Confirm? |
| Ice cream & frozen desserts | sugars, saturated fat, energy | snack, party | 4 | Should frozen yogurt or kulfi count as swaps for ice cream? |
| Soft drinks & iced tea | sugars | snack, party, meal | **8** | Zero-sugar colas are scored B (sweetener penalty), soda water and kombucha A. Keep diet colas as swaps? Should still water be offered? |
| Juices & fruit drinks | sugars | snack, breakfast, meal | **4** | Mostly coconut water. Most juice records lack saturated fat or sodium, so please enter 5–10 real 100% juices from packs. |
| Milk drinks & milkshakes | sugars, saturated fat | snack, breakfast | **31** (29 + 2 "a step better") | Now includes plain toned/double-toned milk, unsweetened soy and almond milk, buttermilk and protein shakes. Are protein shakes sweetened with sucralose acceptable swaps? |

## What the assistant found in the open data

- Of about 3,450 Indian records, **505 passed the data checks** and **167 qualify**: A or B, or a
  "step better" D in biscuits and milk drinks.
- Ingredient lists were fetched one product at a time; **142 of the 167** have one.
- **Why records were rejected:**
  - 909 had no nutrition at all and 543 had no name or brand.
  - The rest had figures that cannot be right: energy that disagrees with the macros, values
    evidently entered per serving (e.g. potato chips at 100 kcal per 100 g), zero saturated fat in a
    fatty food, zero sodium in a salted snack or biscuit, "zero sugar" on a regular cola, toned milk
    with 0 g sugar (lactose is about 4.7 g), or the same figures copied under another brand name.
- **Draft rows may still contain errors of that kind.** Please be especially careful with:
  - sodium of 0 on any savoury product;
  - saturated fat below 12% of total fat in fried snacks;
  - figures identical across different products from the same brand (usually copied).

## Licence note

The draft rows derive from Open Food Facts (ODbL 1.0), and the app credits them wherever a draft is
shown. Figures you verify or type from the pack become BetterChoice's own records.
