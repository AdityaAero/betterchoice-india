"""Adds what the recommendation decision tree needs to the draft swap catalogue, from Open Food Facts.

Per product, looked up by its barcode (never by name):
  * per100.carbohydrate_g and per100.added_sugars_g (diabetes and sugar-control constraints);
  * may_contain, from OFF's traces tags (severe-allergy trace checks), only where empty;
  * veg, from OFF's ingredient analysis (vegan / vegetarian / non-vegetarian), only where unknown;
  * halal, true only when OFF records a halal label;
  * serving_g, the declared serving size.
Nothing the nutritionist set is overwritten; an absent OFF value stays absent (unknown is not zero).
The records remain drafts derived from Open Food Facts (ODbL), as before.

Usage:  python tools/enrich_catalogue.py
"""
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CAT = ROOT / 'app/src/main/assets/catalogue/swaps_v1.json'
API = 'https://world.openfoodfacts.org/api/v2/product/%s.json?fields=nutriments,traces_tags,ingredients_analysis_tags,labels_tags,serving_quantity'
UA = 'BetterChoiceIndia-curation/1.1 (catalogue enrichment; nutritionist draft)'
TRACES = {'en:milk': 'Milk', 'en:gluten': 'Gluten', 'en:nuts': 'Tree nuts', 'en:peanuts': 'Peanuts', 'en:soybeans': 'Soy',
          'en:sesame-seeds': 'Sesame', 'en:eggs': 'Egg', 'en:fish': 'Fish', 'en:crustaceans': 'Crustacea',
          'en:sulphur-dioxide-and-sulphites': 'Sulphites'}


def fetch(gtin):
    for attempt in range(4):
        req = urllib.request.Request(API % gtin, headers={'User-Agent': UA})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode('utf-8')).get('product') or {}
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 3:
                raise
            time.sleep(65)   # rate limited: wait a minute and try again


def number(value):
    try:
        v = float(value)
        return v if v == v and v >= 0 else None
    except (TypeError, ValueError):
        return None


def main():
    raw = CAT.read_text(encoding='utf-8')
    data = json.loads(raw)
    stats = dict(products=0, looked_up=0, carbohydrate=0, added_sugars=0, traces=0, veg=0, halal=0, serving=0, failed=0)
    for p in data['products']:
        stats['products'] += 1
        gtin = p.get('gtin')
        if not gtin or 'carbohydrate_g' in (p.get('per100') or {}):
            continue   # no barcode, or already enriched on an earlier run
        try:
            off = fetch(gtin)
        except Exception as error:   # network or not found: leave the record as it is
            stats['failed'] += 1
            print('  lookup failed', gtin, error)
            time.sleep(6.5)
            continue
        stats['looked_up'] += 1
        n = off.get('nutriments') or {}
        per100 = p.setdefault('per100', {})
        sugars = per100.get('sugars_g')
        carb = number(n.get('carbohydrates_100g'))
        # A carbohydrate figure below the sugars, or above 100 g, is a data-entry error: left out.
        if carb is not None and carb <= 100 and (sugars is None or carb + 0.5 >= sugars) and 'carbohydrate_g' not in per100:
            per100['carbohydrate_g'] = round(carb, 2)
            stats['carbohydrate'] += 1
        added = number(n.get('added-sugars_100g'))
        if added is not None and (sugars is None or added <= sugars + 0.5) and 'added_sugars_g' not in per100:
            per100['added_sugars_g'] = round(added, 2)
            stats['added_sugars'] += 1
        traces = [TRACES[t] for t in off.get('traces_tags') or [] if t in TRACES]
        if traces and not p.get('may_contain'):
            p['may_contain'] = sorted(set(traces))
            stats['traces'] += 1
        tags = off.get('ingredients_analysis_tags') or []
        if not p.get('veg'):
            veg = 'vegan' if 'en:vegan' in tags else 'non-vegetarian' if 'en:non-vegetarian' in tags \
                else 'vegetarian' if 'en:vegetarian' in tags else None
            if veg:
                p['veg'] = veg
                stats['veg'] += 1
        if 'en:halal' in (off.get('labels_tags') or []):
            p['halal'] = True
            stats['halal'] += 1
        serving = number(off.get('serving_quantity'))
        if serving and 'serving_g' not in p:
            p['serving_g'] = serving
            stats['serving'] += 1
        time.sleep(6.5)   # stay well inside Open Food Facts' rate limit
    data['enriched'] = dict(source='Open Food Facts API v2 by barcode', date=time.strftime('%Y-%m-%d'), counts=stats)
    CAT.write_text(json.dumps(data, ensure_ascii=False, indent=1) + ('\n' if raw.endswith('\n') else ''), encoding='utf-8')
    print(json.dumps(stats))


if __name__ == '__main__':
    main()
