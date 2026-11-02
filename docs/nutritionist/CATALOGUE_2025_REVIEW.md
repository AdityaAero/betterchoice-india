# Swap catalogue from Open Food Facts India, labels from 2025: review pack

For the certified nutritionist who approves BetterChoice's swap suggestions, and for the founder.

## What was built

- **Source:** Open Food Facts' official full export (https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz, read 2026-10-02), the open database under the ODbL. Blinkit's website was not used: it blocks automated access and its terms forbid scraping.
- **Selection:** products tagged as sold in India (21188 of 4535553 in the export), with nutrition facts and ingredients marked complete (1424), whose label is evidenced from 1 January 2025: the record was created, or the pack photographed, on or after that date. 1268 of those passed the data checks (name, brand, per-100 g figures, energy consistent with the macronutrients, plausible for the category).
- **Grading:** Nutri-Score 2023, by the app's own engine, the same code that grades a product on the shopper's screen.
- **Groups:** 31 swap groups covering every FSSAI food category where a like-for-like swap makes sense; 973 draft candidates of every colour. Since 8 October 2026 the card shows three choices from the same category: green first, then a yellow or still-red product only when it is a real improvement for that shopper (a Nutri-Score letter no worse, FSSAI's 30% difference on what the category is judged on, nothing else higher), each labelled with its colour. Products another reading puts in a different FSSAI category, or whose composition is far outside their group's, are not offered (`composition_outliers.csv`).
- **Earlier catalogue:** 973 of the 143 earlier products have labels from 2025 and were kept; 123 older ones were taken out (listed in the catalogue under `not_in_2025_harvest`, and recoverable).

## How deep it is

Measured with the app's engine: for each group, up to 12 of its real red and amber products were taken as the product on screen, and for each of 30 single profile choices the engine listed every catalogue product it would accept as a swap (allergy, diet and condition vetoes; goal and condition constraints; FSSAI's 30% difference). A cell counts the distinct products accepted for at least one of those products.

- **418 of 930** category x profile cells have 10 or more alternatives; **693** have at least 3 (the card shows up to 3).
- 616 distinct products appear in the recommendation lists.

| Group | Candidates | Profile choices (of 30) with 10+ | Choices with fewer than 3 |
|---|---|---|---|
| Milk drinks & milkshakes | 57 | 27 | Vegan, Milk allergy |
| Soft drinks & iced tea | 33 | 13 | Weight gain |
| Juices & fruit drinks | 29 | 24 | Weight gain, Kidney, not on dialysis |
| Peanut butter & sweet spreads | 31 | 27 | Peanut allergy |
| Ice cream & frozen desserts | 36 | 26 | Vegan, Milk allergy |
| Breakfast cereals | 58 | 29 | Coeliac (gluten-free) |
| Chocolate & confectionery | 57 | 0 | High cholesterol / heart, Kidney, not on dialysis |
| Biscuits & cookies | 80 | 24 | Wheat allergy, Coeliac (gluten-free) |
| Instant noodles & quick meals | 34 | 19 | Weight gain, Wheat allergy, Coeliac (gluten-free) |
| Nuts & seeds | 28 | 29 | none |
| Salty crunchy snacks | 102 | 29 | none |
| Curd, yoghurt & shrikhand | 53 | 27 | Vegan, Milk allergy |
| Cheese & cheese spreads | 18 | 0 | Vegan, Milk allergy |
| Paneer & tofu | 17 | 0 | Vegan, Weight gain, Milk allergy |
| Bread, buns & pav | 58 | 27 | Wheat allergy, Coeliac (gluten-free) |
| Jams & fruit spreads | 8 | 0 | Healthy adult (awareness), Vegetarian, Eggetarian, Jain, Non-veg / both, Non-veg halal, Vegan, Weight loss, Weight gain, Sugar control, Milk allergy, Egg allergy, Fish allergy, Shellfish allergy, Peanut allergy, Tree-nut allergy, Soy allergy, Wheat allergy, Coeliac (gluten-free), Sesame allergy, Sulphite allergy, Lactose intolerance, Diabetes (type 2), High blood pressure, High cholesterol / heart, Kidney, not on dialysis, Kidney, on dialysis, Pregnant (2nd trimester), Breastfeeding (0-6 months) |
| Atta, flours, rice & millets | 29 | 28 | none |
| Pasta & vermicelli | 9 | 0 | all |
| Ready meals & instant mixes | 8 | 0 | Jain, Weight loss, Weight gain, Muscle gain, Diabetes (type 2), High cholesterol / heart |
| Frozen snacks | 12 | 0 | Vegetarian, Eggetarian, Jain, Non-veg halal, Vegan, Milk allergy, Egg allergy, Fish allergy, Shellfish allergy, Peanut allergy, Tree-nut allergy, Soy allergy, Wheat allergy, Coeliac (gluten-free), Sesame allergy, Sulphite allergy |
| Cooking oils, ghee & butter | 36 | 30 | none |
| Ketchup, sauces & chutneys | 15 | 0 | Jain, Weight gain |
| Mayonnaise, dips & sandwich spreads | 5 | 0 | all |
| Instant soups | 1 | 0 | all |
| Pickles | 7 | 0 | all |
| Health drinks & protein powders | 55 | 28 | none |
| Protein & snack bars | 14 | 0 | Vegetarian, Eggetarian, Jain, Non-veg halal, Vegan, Milk allergy, Egg allergy, Fish allergy, Shellfish allergy, Peanut allergy, Tree-nut allergy, Soy allergy, Wheat allergy, Coeliac (gluten-free), Sesame allergy, Sulphite allergy, Kidney, not on dialysis |
| Indian sweets & mithai | 14 | 0 | Weight loss |
| Cakes, muffins & brownies | 21 | 7 | Vegan, Weight gain, Milk allergy, Wheat allergy, Coeliac (gluten-free) |
| Dried fruit | 22 | 1 | Weight gain, Milk allergy, Egg allergy, Fish allergy, Shellfish allergy, Peanut allergy, Tree-nut allergy, Soy allergy, Wheat allergy, Coeliac (gluten-free), Sesame allergy, Sulphite allergy, High blood pressure, Kidney, not on dialysis, Kidney, on dialysis |
| Masalas & spice mixes | 26 | 23 | Weight gain |

| Profile choice | Groups with 10+ alternatives (of 31) |
|---|---|
| Healthy adult (awareness) | 17 |
| Vegetarian | 16 |
| Eggetarian | 16 |
| Jain | 13 |
| Non-veg / both | 17 |
| Non-veg halal | 15 |
| Vegan | 9 |
| Weight loss | 12 |
| Weight gain | 8 |
| Muscle gain | 15 |
| Sugar control | 16 |
| Milk allergy | 8 |
| Egg allergy | 15 |
| Fish allergy | 15 |
| Shellfish allergy | 15 |
| Peanut allergy | 13 |
| Tree-nut allergy | 14 |
| Soy allergy | 13 |
| Wheat allergy | 11 |
| Coeliac (gluten-free) | 10 |
| Sesame allergy | 14 |
| Sulphite allergy | 14 |
| Lactose intolerance | 17 |
| Diabetes (type 2) | 16 |
| High blood pressure | 16 |
| High cholesterol / heart | 15 |
| Kidney, not on dialysis | 11 |
| Kidney, on dialysis | 15 |
| Pregnant (2nd trimester) | 16 |
| Breastfeeding (0-6 months) | 16 |

## Why some cells are short

- **Too few better products on record.** Where a category holds few products with 2025 labels in Open Food Facts India (jams, mayonnaise, soups, pickles, ready meals), fewer than 3 are better than a given product. Including older labels adds little: most older Indian records have no nutrition figures.
- **Goals ask for a 30% difference within the category.** Weight loss and weight gain need 30% less or more energy for the same portion, which few categories offer (all breakfast cereals are 350-400 kcal per 100 g).
- **Diets and allergies filter on positive evidence.** A vegan swap needs a vegan tag or a clean ingredient list; products with no ingredient list are left out for anyone with an allergy.

Ways to fill the gaps: scan packs in the app's "Add details" flow (each becomes a contribution to approve), add them to Open Food Facts (which then flows into the next build), or license a feed such as GS1 India DataKart.

## How to review

1. Open `catalogue_2025_recommendations.csv`. Each row is one alternative for one category and one profile choice, best first, with its label figures and a link to the product's Open Food Facts page and photos.
2. Check the pack: figures against the photo, the veg mark, the allergen statement. `indian_gs1_prefix = no` means the barcode was not issued in India: confirm it is sold here.
3. Fill `nutritionist_decision` with approve or reject (and a note). Approved products become `verified` in the app catalogue and `approved` in Supabase; only then do release builds show them.

Open Food Facts data: (c) Open Food Facts contributors, ODbL. Credit it wherever it is shown.
