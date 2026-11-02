"""Downloads every Open Food Facts product sold in India whose nutrition facts and ingredients are complete.

Open Food Facts publishes its data for reuse under the Open Database Licence (ODbL); this uses its public
search API at its published pace (no more than 10 searches a minute) with an identifying User-Agent.
Pages are saved one by one, so an interrupted run resumes where it stopped. No retailer site is read.

Output: ../../catalogue_build/off_india/pages/page_NNN.json and harvest.json (counts, timestamps).

Usage:  python tools/off_india_harvest.py
"""
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT.parents[1] / 'catalogue_build' / 'off_india'
API = 'https://world.openfoodfacts.org/api/v2/search'
UA = 'BetterChoiceIndia-curation/1.2 (India catalogue for nutritionist review)'
FIELDS = ','.join([
    'code', 'product_name', 'product_name_en', 'generic_name_en', 'brands', 'quantity', 'product_quantity',
    'product_quantity_unit', 'serving_size', 'serving_quantity', 'nutrition_data_per', 'nutriments',
    'ingredients_text_en', 'ingredients_text', 'ingredients_analysis_tags', 'allergens_tags', 'traces_tags',
    'labels_tags', 'categories_tags', 'food_groups_tags', 'countries_tags', 'stores_tags',
    'created_t', 'last_modified_t', 'last_updated_t', 'last_image_t'])
QUERY = dict(countries_tags_en='india', states_tags='en:nutrition-facts-completed,en:ingredients-completed',
             sort_by='created_t', page_size=100, fields=FIELDS)
PAUSE = 7        # seconds between searches: under 10 a minute
BACKOFF = 65     # after a refusal or an error page


def get(page):
    url = API + '?' + urllib.parse.urlencode(dict(QUERY, page=page))
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'application/json'})
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode('utf-8'))


def main():
    pages = OUT / 'pages'
    pages.mkdir(parents=True, exist_ok=True)
    total, page, failures = None, 1, 0
    while True:
        path = pages / ('page_%03d.json' % page)
        if path.exists():
            data = json.loads(path.read_text(encoding='utf-8'))
        else:
            try:
                data = get(page)
            except (urllib.error.URLError, json.JSONDecodeError, TimeoutError, ConnectionError) as e:
                failures += 1
                print('page %d failed (%s); waiting %d s' % (page, e, BACKOFF), flush=True)
                if failures > 8:
                    raise SystemExit('too many failures; run again later to resume')
                time.sleep(BACKOFF)
                continue
            failures = 0
            path.write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')
            time.sleep(PAUSE)
        total = data.get('count', total)
        got = len(data.get('products', []))
        print('page %d: %d products (of %s)' % (page, got, total), flush=True)
        if got == 0 or page * QUERY['page_size'] >= (total or 0):
            break
        page += 1
    products = [p for f in sorted(pages.glob('page_*.json')) for p in json.loads(f.read_text(encoding='utf-8')).get('products', [])]
    unique = {p['code']: p for p in products if p.get('code')}
    (OUT / 'harvest.json').write_text(json.dumps(dict(source=API, query={k: v for k, v in QUERY.items() if k != 'fields'},
                                                      reported=total, downloaded=len(products), unique=len(unique),
                                                      at=time.strftime('%Y-%m-%dT%H:%M:%S')), indent=1), encoding='utf-8')
    print('done: %d downloaded, %d unique of %s reported' % (len(products), len(unique), total))


if __name__ == '__main__':
    main()
