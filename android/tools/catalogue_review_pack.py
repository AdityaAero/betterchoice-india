"""Writes the nutritionist's review pack for the 2025 Open Food Facts India catalogue, from the build outputs
(off_india_catalogue.py, CatalogueCoverageHarness). Every number is read from those files.

Writes to ../../nutritionist/:
  CATALOGUE_2025_REVIEW.md                what was built, coverage, gaps, and how to review
  catalogue_2025_recommendations.csv      per category and profile choice, the best alternatives (decision column)
  catalogue_2025_coverage_matrix.csv      the full category x profile-choice matrix

Usage:  python tools/catalogue_review_pack.py
"""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT.parents[1] / 'catalogue_build' / 'off_india'
OUT = ROOT.parents[1] / 'nutritionist'
APP = ROOT / 'app/src/main/assets/catalogue/swaps_v1.json'

LABELS = {'healthy': 'Healthy adult (awareness)', 'diet:vegetarian': 'Vegetarian', 'diet:eggetarian': 'Eggetarian',
          'diet:jain': 'Jain', 'diet:non_veg': 'Non-veg / both', 'diet:halal': 'Non-veg halal', 'diet:vegan': 'Vegan',
          'goal:loss': 'Weight loss', 'goal:gain': 'Weight gain', 'goal:muscle': 'Muscle gain', 'goal:sugar': 'Sugar control',
          'allergy:milk': 'Milk allergy', 'allergy:egg': 'Egg allergy', 'allergy:fish': 'Fish allergy',
          'allergy:crustacea': 'Shellfish allergy', 'allergy:peanut': 'Peanut allergy', 'allergy:tree_nut': 'Tree-nut allergy',
          'allergy:soy': 'Soy allergy', 'allergy:wheat': 'Wheat allergy', 'allergy:gluten': 'Coeliac (gluten-free)',
          'allergy:sesame': 'Sesame allergy', 'allergy:sulphite': 'Sulphite allergy', 'condition:lactose': 'Lactose intolerance',
          'condition:diabetes': 'Diabetes (type 2)', 'condition:hypertension': 'High blood pressure',
          'condition:cholesterol': 'High cholesterol / heart', 'condition:kidney': 'Kidney, not on dialysis',
          'condition:dialysis': 'Kidney, on dialysis', 'condition:pregnancy': 'Pregnant (2nd trimester)',
          'condition:breastfeeding': 'Breastfeeding (0-6 months)'}


def main():
    cov = json.loads((BUILD / 'coverage.json').read_text(encoding='utf-8'))
    products = {p['gtin']: p for p in json.loads((BUILD / 'products_2025.json').read_text(encoding='utf-8'))}
    catalogue = json.loads(APP.read_text(encoding='utf-8'))
    source = json.loads((BUILD / 'export_india.json').read_text(encoding='utf-8'))
    groups = {g['id']: g for g in catalogue['groups']}
    profiles = list(next(iter(cov.values()))['profiles'].keys())
    OUT.mkdir(parents=True, exist_ok=True)

    with open(OUT / 'catalogue_2025_coverage_matrix.csv', 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['group', 'group_name', 'candidates', 'red_amber_products_tested'] + [LABELS[p] for p in profiles])
        for gid, g in cov.items():
            w.writerow([gid, g['name'], g['candidates'], g['originals']] + [g['profiles'][p]['distinct'] for p in profiles])

    rows = list(csv.DictReader(open(BUILD / 'recommendations.csv', encoding='utf-8', newline='')))
    with open(OUT / 'catalogue_2025_recommendations.csv', 'w', newline='', encoding='utf-8') as f:
        fields = ['group_name', 'profile', 'rank', 'brand', 'name', 'gtin', 'indian_gs1_prefix', 'label_date', 'band', 'letter',
                  'qualifies_for', 'originals', 'energy_kcal', 'sugars_g', 'added_sugars_g', 'saturated_fat_g', 'sodium_mg',
                  'protein_g', 'fibre_g', 'carbohydrate_g', 'veg', 'why', 'source_url', 'nutritionist_decision', 'nutritionist_note']
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        for r in rows:
            p = products.get(r['gtin'], {})
            w.writerow(dict(group_name=r['group_name'], profile=LABELS.get(r['profile_choice'], r['profile_choice']), rank=r['rank'],
                            brand=r['brand'], name=r['name'], gtin=r['gtin'], indian_gs1_prefix='yes' if r['gtin'].startswith('890') else 'no',
                            label_date=p.get('label_date', ''), band=r['band'], letter=r['letter'], qualifies_for=r['qualifies_for'],
                            originals=r['originals'], energy_kcal=r['energy_kcal'], sugars_g=r['sugars_g'],
                            added_sugars_g=r['added_sugars_g'], saturated_fat_g=r['saturated_fat_g'], sodium_mg=r['sodium_mg'],
                            protein_g=r['protein_g'], fibre_g=r['fibre_g'], carbohydrate_g=r['carbohydrate_g'], veg=r['veg'],
                            why=r['why'], source_url=r['source_url'], nutritionist_decision='', nutritionist_note=''))

    cells = [(gid, p, g['profiles'][p]['distinct']) for gid, g in cov.items() for p in profiles]
    ten = sum(1 for c in cells if c[2] >= 10)
    three = sum(1 for c in cells if c[2] >= 3)
    cand = Counter(p['group'] for p in catalogue['products'])
    unique = len({r['gtin'] for r in rows})
    by_profile = {p: sum(1 for gid in cov if cov[gid]['profiles'][p]['distinct'] >= 10) for p in profiles}
    lines = []
    w = lines.append
    w('# Swap catalogue from Open Food Facts India, labels from 2025: review pack')
    w('')
    w('For the certified nutritionist who approves BetterChoice\'s swap suggestions, and for the founder.')
    w('')
    w('## What was built')
    w('')
    w('- **Source:** Open Food Facts\' official full export (%s, read %s), the open database under the ODbL. Blinkit\'s '
      'website was not used: it blocks automated access and its terms forbid scraping.' % (source['source'], source['at'][:10]))
    w('- **Selection:** products tagged as sold in India (%d of %d in the export), with nutrition facts and ingredients '
      'marked complete (%d), whose label is evidenced from 1 January 2025: the record was created, or the pack '
      'photographed, on or after that date. %d of those passed the data checks (name, brand, per-100 g figures, energy '
      'consistent with the macronutrients, plausible for the category).'
      % (source['india'], source['rows'], source['complete'], len(products)))
    w('- **Grading:** Nutri-Score 2023, by the app\'s own engine, the same code that grades a product on the shopper\'s screen.')
    w('- **Groups:** %d swap groups covering every FSSAI food category where a like-for-like swap makes sense; %d draft '
      'candidates of every colour. Since 8 October 2026 the card shows three choices from the same category: green first, '
      'then a yellow or still-red product only when it is a real improvement for that shopper (a Nutri-Score letter no '
      'worse, FSSAI\'s 30%% difference on what the category is judged on, nothing else higher), each labelled with its '
      'colour. Products another reading puts in a different FSSAI category, or whose composition is far outside their '
      'group\'s, are not offered (`composition_outliers.csv`).' % (len(groups), sum(cand.values())))
    w('- **Earlier catalogue:** %d of the 143 earlier products have labels from 2025 and were kept; %d older ones were '
      'taken out (listed in the catalogue under `not_in_2025_harvest`, and recoverable).'
      % (len([p for p in catalogue['products'] if not p.get('graded_by')]), len(catalogue.get('not_in_2025_harvest', []))))
    w('')
    w('## How deep it is')
    w('')
    w('Measured with the app\'s engine: for each group, up to 12 of its real red and amber products were taken as the '
      'product on screen, and for each of 30 single profile choices the engine listed every catalogue product it would '
      'accept as a swap (allergy, diet and condition vetoes; goal and condition constraints; FSSAI\'s 30% difference). '
      'A cell counts the distinct products accepted for at least one of those products.')
    w('')
    w('- **%d of %d** category x profile cells have 10 or more alternatives; **%d** have at least 3 (the card shows up to 3).'
      % (ten, len(cells), three))
    w('- %d distinct products appear in the recommendation lists.' % unique)
    w('')
    w('| Group | Candidates | Profile choices (of 30) with 10+ | Choices with fewer than 3 |')
    w('|---|---|---|---|')
    for gid, g in cov.items():
        low = [LABELS[p] for p in profiles if g['profiles'][p]['distinct'] < 3]
        n10 = sum(1 for p in profiles if g['profiles'][p]['distinct'] >= 10)
        w('| %s | %d | %d | %s |' % (g['name'], g['candidates'], n10,
                                      'none' if not low else ('all' if len(low) == len(profiles) else ', '.join(low))))
    w('')
    w('| Profile choice | Groups with 10+ alternatives (of %d) |' % len(cov))
    w('|---|---|')
    for p in profiles:
        w('| %s | %d |' % (LABELS[p], by_profile[p]))
    w('')
    w('## Why some cells are short')
    w('')
    w('- **Too few better products on record.** Where a category holds few products with 2025 labels in Open Food Facts '
      'India (jams, mayonnaise, soups, pickles, ready meals), fewer than 3 are better than a given product. Including older '
      'labels adds little: most older Indian records have no nutrition figures.')
    w('- **Goals ask for a 30% difference within the category.** Weight loss and weight gain need 30% less or more energy '
      'for the same portion, which few categories offer (all breakfast cereals are 350-400 kcal per 100 g).')
    w('- **Diets and allergies filter on positive evidence.** A vegan swap needs a vegan tag or a clean ingredient list; '
      'products with no ingredient list are left out for anyone with an allergy.')
    w('')
    w('Ways to fill the gaps: scan packs in the app\'s "Add details" flow (each becomes a contribution to approve), add '
      'them to Open Food Facts (which then flows into the next build), or license a feed such as GS1 India DataKart.')
    w('')
    w('## How to review')
    w('')
    w('1. Open `catalogue_2025_recommendations.csv`. Each row is one alternative for one category and one profile choice, '
      'best first, with its label figures and a link to the product\'s Open Food Facts page and photos.')
    w('2. Check the pack: figures against the photo, the veg mark, the allergen statement. `indian_gs1_prefix = no` means '
      'the barcode was not issued in India: confirm it is sold here.')
    w('3. Fill `nutritionist_decision` with approve or reject (and a note). Approved products become `verified` in the app '
      'catalogue and `approved` in Supabase; only then do release builds show them.')
    w('')
    w('Open Food Facts data: (c) Open Food Facts contributors, ODbL. Credit it wherever it is shown.')
    (OUT / 'CATALOGUE_2025_REVIEW.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print('wrote review pack: %d/%d cells with 10+, %d with 3+, %d recommendation rows' % (ten, len(cells), three, len(rows)))


if __name__ == '__main__':
    main()
