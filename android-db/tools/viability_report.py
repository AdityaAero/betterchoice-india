"""Viability report for the founder and the nutritionist: 1,000+ real Indian products (Open Food Facts India, labels from
2025) read the way the bubble reads a Zepto page, rated, and explained to six kinds of shopper.

Reads ../../catalogue_build/off_india/viability/products.csv (from the ViabilityHarness unit test, env BC_VIABILITY_DIR).
Writes ../../nutritionist/VIABILITY_1000_PRODUCTS.md and viability_1000_products.csv.

Usage:  python tools/viability_report.py
"""
import csv
import shutil
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT.parents[1] / 'catalogue_build' / 'off_india' / 'viability' / 'products.csv'
OUT = ROOT.parents[1] / 'nutritionist'
PERSONAS = [('normal', 'No condition'), ('bp', 'High blood pressure'), ('diabetes', 'Diabetes'), ('heart', 'Heart / cholesterol'),
            ('loss', 'Weight loss'), ('muscle', 'Muscle gain')]
COLOUR = {'A': 'green', 'B': 'yellow', 'C': 'red'}


def pct(a, b):
    return '%d%%' % round(100.0 * a / b) if b else '-'


def main():
    rows = list(csv.DictReader(open(SRC, encoding='utf-8')))
    n = len(rows)
    rated = [r for r in rows if r['read_state'] == 'GRADED']
    match = [r for r in rated if r['read_matches'] == 'true']
    ne = [r for r in rows if r['no_energy_state']]
    ne_rated = [r for r in ne if r['no_energy_state'] == 'GRADED']
    ne_match = [r for r in ne if r['no_energy_matches'] == 'true']
    by_cat = defaultdict(list)
    for r in rows:
        by_cat[r['category']].append(r)
    cats = sorted(by_cat, key=lambda c: (c.startswith('Other'), -len(by_cat[c])))

    def red_with_good(group):
        red = [r for r in group if r['read_band'] == 'C']
        return sum(1 for r in red if r['normal_green']), len(red)

    def personal(group, key, word):
        return sum(1 for r in group if word in r[key + '_red'])

    lines = []
    w = lines.append
    w('# Viability: 1,000+ products read, rated and explained')
    w('')
    w('For the founder and the certified nutritionist. %d real Indian packaged foods and drinks (Open Food Facts India, labels '
      'from 2025, ODbL) were written into a real recorded Zepto product page, in the two ways Zepto prints nutrition, and read '
      'by the app\'s own pipeline exactly as the bubble reads a screen. Six shoppers were then shown the card: no condition, high '
      'blood pressure, diabetes, heart or cholesterol, weight loss and muscle gain. Zepto itself was not browsed at this scale '
      '(bulk automated access to its app); products are the ones Indian shoppers buy, by brand and category.' % n)
    w('')
    w('## Reading')
    w('')
    w('- Zepto\'s usual format ("Energy (kcal) 345.0, Protein (g) 52.0, ..."): **%d of %d rated** (%s); the colour matches the '
      'label\'s true figures for **%d** (%s of those rated).' % (len(rated), n, pct(len(rated), n), len(match), pct(len(match), len(rated))))
    w('- Zepto\'s bracket format with no energy ("Protein(52.5g), Carbohydrate(31.5g), ..."; ZOFF Soya Chunks was listed '
      'this way): **%d of %d rated**, colour matches for **%d** (%s). Energy is worked out from protein, carbohydrate and fat '
      'by FSSAI\'s factors, and the card says so.' % (len(ne_rated), len(ne), len(ne_match), pct(len(ne_match), len(ne_rated))))
    mism = [r for r in rated if r['read_matches'] != 'true']
    if mism:
        w('- Where the colour differed (%d): %s.' % (len(mism), '; '.join('%s %s (label %s, read %s)' % (
            r['brand'], r['name'], r['true_letter'], r['read_letter']) for r in mism[:12])))
    w('')
    w('## How often is it red?')
    w('')
    letters = Counter(r['true_letter'] for r in rows)
    bands = Counter(r['true_band'] for r in rows)
    w('Across all %d: green %d (%s), yellow %d (%s), red %d (%s). Of the red, Nutri-Score D %d and E %d: on Nutri-Score\'s own '
      'five-colour scale D is orange and only E is red, so showing the official scale would turn %s of products from red to '
      'orange.' % (n, bands['A'], pct(bands['A'], n), bands['B'], pct(bands['B'], n), bands['C'], pct(bands['C'], n),
                   letters['D'], letters['E'], pct(letters['D'], n)))
    good, red = red_with_good(rated)
    w('')
    w('Red products still show what is good about them: **%d of %d** red products (%s) get at least one dark-green line '
      '(protein, fibre, low sugar, low sodium, unsaturated fat) for a shopper with no condition.' % (good, red, pct(good, red)))
    w('')
    w('| Category | Products | Green | Yellow | Red (D / E) | Red with a green line | 3 choices (no condition) |')
    w('|---|---|---|---|---|---|---|')
    for c in cats:
        g = by_cat[c]
        b = Counter(r['true_band'] for r in g)
        l = Counter(r['true_letter'] for r in g)
        gd, rd = red_with_good([r for r in g if r['read_state'] == 'GRADED'])
        need = [r for r in g if r['read_band'] in ('B', 'C') and r['normal_choices'] not in ('', '-1')]
        three = sum(1 for r in need if r['normal_choices'] == '3')
        w('| %s | %d | %d | %d | %d (%d / %d) | %s | %s |' % (c, len(g), b['A'], b['B'], b['C'], l['D'], l['E'],
                                                          pct(gd, rd), '%d of %d' % (three, len(need)) if need else '-'))
    w('')
    w('## What each shopper sees')
    w('')
    w('Dark red on the card is what counts against the product; the shopper\'s own reasons come first, tagged ("your BP", '
      '"your diabetes", "your heart"), and a condition makes its nutrient count from where a food stops being "low" in it '
      '(FSSAI\'s claim levels: 120 mg sodium, 5 g sugar, 1.5 g saturated fat per 100 g). Dark green is what is good about it.')
    w('')
    w('| Shopper | Products with a personal dark-red line | Red and yellow products with 3 better choices | ... with none |')
    w('|---|---|---|---|')
    for key, label in PERSONAS:
        tagged = sum(1 for r in rated if ' · your ' in r[key + '_red'])
        need = [r for r in rated if r['read_band'] in ('B', 'C') and r[key + '_choices'] not in ('', '-1')]
        three = sum(1 for r in need if r[key + '_choices'] == '3')
        none = sum(1 for r in need if r[key + '_choices'] == '0')
        w('| %s | %s | %d of %d (%s) | %d |' % (label, '%d (%s)' % (tagged, pct(tagged, len(rated))) if key != 'normal' else '-',
                                              three, len(need), pct(three, len(need)), none))
    bp_sodium = sum(1 for r in rated if 'your BP' in r['bp_red'])
    plain_sodium = sum(1 for r in rated if 'sodium' in r['normal_red'].lower())
    w('')
    w('High blood pressure: sodium is flagged on **%d** products (%s), against %d for a shopper with no condition, who sees it '
      'only at the general "high" level (600 mg per 100 g).' % (bp_sodium, pct(bp_sodium, len(rated)), plain_sodium))
    w('')
    w('## Example cards (no condition, then high blood pressure)')
    w('')
    shown = 0
    for r in rated:
        if shown >= 12:
            break
        if r['read_band'] == 'C' and r['normal_green'] and r['bp_red'] != r['normal_red']:
            w('- **%s %s** (%s, Nutri-Score %s). No condition: red %s; green %s. High BP: red %s.' % (
                r['brand'], r['name'], r['category'], r['read_letter'], r['normal_red'] or '-', r['normal_green'] or '-', r['bp_red'] or '-'))
            shown += 1
    w('')
    w('## Files')
    w('')
    w('`viability_1000_products.csv`: one row per product, with the label\'s true letter, what the app read, and for each '
      'shopper the dark-red and dark-green lines and how many better choices the card offers (-1: no curated category).')
    w('')
    w('Open Food Facts data: (c) Open Food Facts contributors, ODbL.')
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'VIABILITY_1000_PRODUCTS.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    shutil.copyfile(SRC, OUT / 'viability_1000_products.csv')
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
