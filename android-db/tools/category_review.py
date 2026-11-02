"""Category review for the nutritionist: per category, the 20 Indian products shoppers scan most (labels from 2025),
graded by the app's engine, and the curated list of recommended products (green first) with the user choices each passes.

Reads ../../catalogue_build/off_india/ (products_2025.json, products_graded.jsonl, recommendations.csv, coverage.json)
and the evidence registry (salt from sodium by WHO's pairing). Writes to ../../nutritionist/:
  CATEGORY_REVIEW_2025.md, category_review_2025_products.csv, category_review_2025_recommended.csv

"Most scanned" is Open Food Facts' unique-scan count: the best open signal of what is actually on Indian shelves.
Whether a product is listed on Blinkit, Zepto or Flipkart Minutes on a given day is not known here; the app checks
that on the shop's own screen when a shopper taps a swap.

Usage:  python tools/category_review.py
"""
import csv
import json
import sys
from collections import defaultdict
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import evidence_check as ec  # noqa: E402
from catalogue_review_pack import LABELS  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT.parents[1] / 'catalogue_build' / 'off_india'
OUT = ROOT.parents[1] / 'nutritionist'
APP = ROOT / 'app/src/main/assets/catalogue/swaps_v1.json'
PER_CATEGORY = 20
FIRST = ['biscuits', 'chocolate', 'soft_drinks', 'fruit_drinks', 'flavoured_milk', 'salty_snacks', 'nuts_seeds']
E = {e['id']: Decimal(e['value']) for e in ec.registry()}
SALT_PER_MG_SODIUM = E['who.salt.max'] * 1000 / E['who.sodium.max']     # WHO: 5 g salt = 2000 mg sodium


def salt_g(sodium_mg):
    return None if sodium_mg is None else float(Decimal(str(sodium_mg)) * SALT_PER_MG_SODIUM / 1000)


def f(v, digits=1):
    return '' if v is None else ('%.' + str(digits) + 'f') % v


def main():
    products = json.loads((BUILD / 'products_2025.json').read_text(encoding='utf-8'))
    graded = {}
    for line in open(BUILD / 'products_graded.jsonl', encoding='utf-8'):
        g = json.loads(line)
        graded[g['gtin']] = g
    cov = json.loads((BUILD / 'coverage.json').read_text(encoding='utf-8'))
    groups = {g['id']: g for g in json.loads(APP.read_text(encoding='utf-8'))['groups']}
    profiles = list(next(iter(cov.values()))['profiles'].keys())
    passes = defaultdict(set)          # gtin -> profile choices it is accepted for
    why = {}
    for r in csv.DictReader(open(BUILD / 'recommendations.csv', encoding='utf-8', newline='')):
        passes[r['gtin']].add(r['profile_choice'])
        why.setdefault(r['gtin'], r['why'])

    by_group = defaultdict(dict)
    for p in products:
        if not p.get('group') or p['gtin'] not in graded:
            continue
        key = ((p.get('brand') or '').lower(), p['name'].lower())   # one row per product, the most scanned record
        best = by_group[p['group']].get(key)
        if best is None or (p.get('scans') or 0) > (best.get('scans') or 0):
            by_group[p['group']][key] = p
    order = FIRST + sorted((g for g in by_group if g not in FIRST), key=lambda g: -len(by_group[g]))

    OUT.mkdir(parents=True, exist_ok=True)
    plist = open(OUT / 'category_review_2025_products.csv', 'w', newline='', encoding='utf-8')
    pw = csv.writer(plist)
    pw.writerow(['category', 'rank_by_scans', 'brand', 'name', 'gtin', 'scans', 'label_date', 'nutri_score', 'colour', 'energy_kcal',
                 'sugars_g', 'added_sugars_g', 'saturated_fat_g', 'salt_g', 'protein_g', 'fibre_g', 'ingredients_on_record',
                 'recommended_for', 'photo', 'open_food_facts'])
    rlist = open(OUT / 'category_review_2025_recommended.csv', 'w', newline='', encoding='utf-8')
    rw = csv.writer(rlist)
    rw.writerow(['category', 'brand', 'name', 'gtin', 'nutri_score', 'energy_kcal', 'sugars_g', 'saturated_fat_g', 'salt_g', 'protein_g',
                 'fibre_g', 'veg', 'recommended_for_count'] + [LABELS[p] for p in profiles] + ['photo', 'open_food_facts',
                 'nutritionist_decision', 'nutritionist_note'])
    colour = {'A': 'green', 'B': 'yellow', 'C': 'red'}
    md = ['# Category review: what Indian shoppers buy, and the better choices that suit each person', '',
          'For the certified nutritionist and the founder. Labels from 2025 (Open Food Facts India, ODbL). Grades are the app\'s own '
          '(Nutri-Score 2023 in three colours). A product is **recommended** when the app accepts it for that person against the '
          'category\'s real red and yellow products: a Nutri-Score letter no worse, FSSAI\'s 30%% difference on what the category is '
          'judged on (or on their goal), nothing else higher, and their allergies, diet and conditions. The card shows three, green '
          'first, each with its colour. Salt is sodium x %s (WHO\'s pairing of its limits).' % format(SALT_PER_MG_SODIUM.normalize(), 'f'), '',
          'Availability on Blinkit, Zepto or Flipkart Minutes is not known from open data; the app checks it on the shop\'s own '
          'screen when a shopper taps a swap.', '']
    summary = ['| Category | Products on record | Listed | Green | Recommended for someone | of which green |', '|---|---|---|---|---|---|']
    sections = []
    for gid in order:
        if gid not in by_group or gid not in cov:
            continue
        items = sorted(by_group[gid].values(), key=lambda p: (-(p.get('scans') or 0), p.get('label_date') or ''), reverse=False)
        top = items[:PER_CATEGORY]
        green = [p for p in items if graded[p['gtin']]['band'] == 'A']
        recommended = [p for p in items if passes.get(p['gtin'])]
        name = groups[gid]['name']
        summary.append('| %s | %d | %d | %d | %d | %d |' % (name, len(items), len(top), len(green), len(recommended),
                                                           sum(1 for p in recommended if graded[p['gtin']]['band'] == 'A')))
        s = ['## %s' % name, '', '**The %d most-scanned products** (of %d on record with 2025 labels):' % (len(top), len(items)), '',
             '| # | Product | Nutri-Score | kcal | Sugars g | Sat fat g | Salt g | Protein g | Fibre g | Recommended for |',
             '|---|---|---|---|---|---|---|---|---|---|']
        for i, p in enumerate(top, 1):
            g, n = graded[p['gtin']], p['per100']
            choices = passes.get(p['gtin'], set())
            rec = '%d of %d choices' % (len(choices), len(profiles)) if choices else 'no one (not better for any choice)'
            s.append('| %d | %s %s | %s (%s) | %s | %s | %s | %s | %s | %s | %s |' % (
                i, p.get('brand') or '', p['name'], g['letter'], colour[g['band']], f(n.get('energy_kcal'), 0), f(n.get('sugars_g')),
                f(n.get('saturated_fat_g')), f(salt_g(n.get('sodium_mg')), 2), f(n.get('protein_g')), f(n.get('fibre_g')), rec))
            pw.writerow([name, i, p.get('brand'), p['name'], p['gtin'], p.get('scans') or 0, p.get('label_date'), g['letter'], colour[g['band']],
                         n.get('energy_kcal'), n.get('sugars_g'), n.get('added_sugars_g'), n.get('saturated_fat_g'), f(salt_g(n.get('sodium_mg')), 2),
                         n.get('protein_g'), n.get('fibre_g'), 'yes' if p.get('ingredients') else 'no',
                         '; '.join(LABELS[c] for c in profiles if c in choices), p.get('image', ''), p.get('source_url')])
        s.append('')
        if recommended:
            s.append('**Curated recommendations**, green first (%d):' % len(recommended))
            s.append('')
            for p in sorted(recommended, key=lambda p: (graded[p['gtin']]['band'], -len(passes[p['gtin']]))):
                g, n, choices = graded[p['gtin']], p['per100'], passes[p['gtin']]
                missing = [LABELS[c] for c in profiles if c not in choices]
                s.append('- **%s %s** (Nutri-Score %s, %s): %s kcal, sugars %s g, saturated fat %s g, salt %s g, protein %s g, fibre %s g per 100 %s. '
                         'Recommended for %d of %d choices%s.' % (
                             p.get('brand') or '', p['name'], g['letter'], colour[g['band']], f(n.get('energy_kcal'), 0), f(n.get('sugars_g')),
                             f(n.get('saturated_fat_g')), f(salt_g(n.get('sodium_mg')), 2), f(n.get('protein_g')), f(n.get('fibre_g')),
                             'ml' if p.get('beverage') else 'g', len(choices), len(profiles),
                             '' if not missing else '; not for: ' + ', '.join(missing)))
                rw.writerow([name, p.get('brand'), p['name'], p['gtin'], g['letter'], n.get('energy_kcal'), n.get('sugars_g'),
                             n.get('saturated_fat_g'), f(salt_g(n.get('sodium_mg')), 2), n.get('protein_g'), n.get('fibre_g'), p.get('veg') or '',
                             len(choices)] + ['yes' if c in choices else '' for c in profiles] + [p.get('image', ''), p.get('source_url'), '', ''])
        else:
            closest = sorted(items, key=lambda p: graded[p['gtin']]['score'])[:3]
            s.append('**No product better than the others** among the %d on record for any choice, so the app recommends none here '
                     'rather than a sideways one. Best by Nutri-Score: %s.' % (len(items), '; '.join('%s %s (%s, score %d)' % (
                         p.get('brand') or '', p['name'], graded[p['gtin']]['letter'], graded[p['gtin']]['score']) for p in closest)))
        s.append('')
        sections.append('\n'.join(s))
    plist.close()
    rlist.close()
    md += summary + [''] + sections + ['Open Food Facts data: (c) Open Food Facts contributors, ODbL.']
    (OUT / 'CATEGORY_REVIEW_2025.md').write_text('\n'.join(md) + '\n', encoding='utf-8')
    print('\n'.join(summary))


if __name__ == '__main__':
    main()
