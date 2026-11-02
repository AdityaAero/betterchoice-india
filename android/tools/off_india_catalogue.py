"""Builds the catalogue from the Open Food Facts India harvest (tools/off_india_harvest.py), 2025 onwards only.

For every harvested product it:
  * keeps it only if its record was created, or its pack photographed, on or after 1 Jan 2025 (Open Food
    Facts' last-modified date is not used: its own bots touch records constantly);
  * cleans it with curate_swaps.clean (name, brand, per-100 values, energy consistency, plausibility per group);
  * places it in a swap group by its name, as the app will place the product a shopper is looking at; the
    17 groups below extend the original 11 so every FSSAI category with meaningful swaps has one;
  * grades it with Nutri-Score 2023 (the app regrades at runtime).

Writes:
  ../../catalogue_build/off_india/products_2025.json  every valid 2025+ product, grouped or not (harness, Supabase)
  ../../catalogue_build/off_india/rejected_2025.csv   what was left out and why
  app/src/main/assets/catalogue/swaps_v1.json          groups + swap candidates (status draft until a nutritionist approves)

Usage:  python tools/off_india_catalogue.py [--dry-run]
Reads the full export (tools/off_india_export.py) and any API pages; it refuses to rewrite the app catalogue
until a complete source exists (export_india.json or harvest.json).
"""
import argparse
import csv
import datetime
import json
import sys
import time
import urllib.request
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import curate_swaps as cs  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT.parents[1] / 'catalogue_build' / 'off_india'
APP = ROOT / 'app/src/main/assets/catalogue/swaps_v1.json'
SINCE = int(datetime.datetime(2025, 1, 1, tzinfo=datetime.timezone.utc).timestamp())

# kcal: the energy per 100 g a real product in the group can have; outside it the record was almost
# certainly entered per serving or as prepared (a data check, as PLAUSIBLE_KCAL in curate_swaps).
NEW_GROUPS = [
    dict(id='curd_yoghurt', name='Curd, yoghurt & shrikhand', branch='food', focus=['sugars', 'saturated_fat'],
         occasions=['snack', 'meal', 'breakfast'], kcal=(35, 320),
         match=['curd', 'dahi', 'yoghurt', 'yogurt', 'greek yogurt', 'greek yoghurt', 'skyr', 'shrikhand', 'mishti doi',
                'misti doi', 'probiotic curd'],
         exclude=['drink', 'lassi', 'smoothie', 'frozen', 'powder', 'starter', 'culture', 'dip', 'bar', 'coated', 'chips',
                  'raisins', 'bites', 'melts']),
    dict(id='cheese', name='Cheese & cheese spreads', branch='food', focus=['sodium', 'saturated_fat'],
         occasions=['meal', 'snack', 'breakfast'], kcal=(150, 450),
         match=['cheese', 'mozzarella', 'cheddar', 'cream cheese', 'cheese slices', 'cheese spread', 'gouda', 'parmesan',
                'feta', 'processed cheese'],
         exclude=['balls', 'puffs', 'crackers', 'popcorn', 'cheesecake', 'sauce', 'dip', 'biscuit', 'cookie', 'chips',
                  'nachos', 'pizza', 'burger', 'sandwich', 'pasta', 'noodles', 'macaroni', 'corn', 'bites', 'rings',
                  'frozen', 'masala', 'cottage cheese', 'paneer', 'panner', 'tofu', 'snack', 'straws', 'garlic bread',
                  'sourdough', 'bread', 'bun', 'toast']),
    dict(id='paneer_tofu', name='Paneer & tofu', branch='food', focus=['saturated_fat', 'sodium'],
         occasions=['meal'], kcal=(60, 380),
         match=['paneer', 'panner', 'tofu', 'cottage cheese', 'soya paneer'],
         exclude=['tikka', 'masala', 'curry', 'makhani', 'makhanwala', 'butter masala', 'biryani', 'ready', 'frozen',
                  'pakoda', 'pakora', 'snack', 'sandwich', 'pizza', 'roll', 'momos']),
    dict(id='bread', name='Bread, buns & pav', branch='food', focus=['sodium', 'sugars', 'fibre'],
         occasions=['breakfast', 'meal', 'snack'], kcal=(180, 360),
         match=['bread', 'pav', 'bun', 'burger bun', 'pizza base', 'kulcha', 'pita', 'tortilla', 'wrap', 'loaf',
                'sandwich bread', 'brown bread', 'multigrain bread', 'sourdough', 'chapati', 'roti', 'phulka'],
         exclude=['crumbs', 'breadcrumbs', 'bread crumbs', 'sticks', 'garlic bread', 'toast', 'rusk', 'croutons',
                  'pudding', 'mix', 'premix', 'spread', 'biscuit', 'cake', 'frozen', 'stuffed', 'pakoda']),
    dict(id='jams', name='Jams & fruit spreads', branch='food', focus=['sugars'],
         occasions=['breakfast', 'snack'], kcal=(40, 340),
         match=['jam', 'marmalade', 'fruit spread', 'fruit preserve', 'murabba', 'jelly'],
         exclude=['jelly beans', 'jelly sweets', 'candy', 'jelly crystals', 'jelly powder', 'gummies', 'biscuit', 'cookie',
                  'wafer', 'cake']),
    dict(id='flours_grains', name='Atta, flours, rice & millets', branch='food', focus=['fibre', 'sodium'],
         occasions=['meal', 'breakfast'], kcal=(300, 430),
         match=['atta', 'flour', 'besan', 'maida', 'rice', 'basmati', 'brown rice', 'millet', 'millets', 'ragi', 'jowar',
                'bajra', 'quinoa', 'poha', 'suji', 'sooji', 'rava', 'dalia', 'daliya', 'broken wheat', 'sattu', 'barley',
                'buckwheat', 'kuttu', 'amaranth', 'rajgira', 'foxtail', 'kodo', 'barnyard', 'little millet'],
         exclude=['noodles', 'pasta', 'biscuit', 'cookie', 'cookies', 'cake', 'chips', 'crackers', 'cracker', 'rice cake',
                  'puffed', 'murmura', 'bran oil', 'oil', 'ready', 'mix', 'premix', 'idli', 'dosa', 'upma', 'khichdi',
                  'pulao', 'flakes', 'cereal', 'muesli', 'bar', 'bites', 'puffs', 'papad', 'namkeen', 'ladoo', 'laddu',
                  'chikki', 'instant', 'meal', 'malt', 'drink', 'porridge mix', 'rice crispies', 'pops']),
    dict(id='pasta_vermicelli', name='Pasta & vermicelli', branch='food', focus=['fibre', 'sodium'],
         occasions=['meal'], kcal=(300, 420),
         match=['pasta', 'macaroni', 'penne', 'spaghetti', 'fusilli', 'vermicelli', 'seviyan', 'sevai', 'semiya',
                'lasagne', 'fettuccine', 'farfalle'],
         exclude=['sauce', 'cup', 'ready', 'kheer', 'payasam', 'noodles']),
    dict(id='ready_meals', name='Ready meals & instant mixes', branch='food', focus=['sodium', 'saturated_fat', 'energy'],
         occasions=['meal'], kcal=(40, 480),
         match=['ready to eat', 'ready meal', 'heat and eat', 'heat & eat', 'instant mix', 'idli mix', 'dosa mix',
                'upma mix', 'poha mix', 'khichdi', 'biryani', 'pulao', 'dal makhani', 'rajma', 'chole', 'chana masala',
                'curry', 'sabzi', 'pav bhaji', 'dal tadka', 'kadhi', 'dhokla mix', 'vada mix', 'instant upma',
                'instant poha', 'bisibele', 'bisi bele', 'pongal'],
         exclude=['noodles', 'pasta', 'masala powder', 'curry powder', 'curry paste', 'spice', 'chips', 'namkeen',
                  'frozen', 'gravy base']),
    dict(id='frozen_snacks', name='Frozen snacks', branch='food', focus=['sodium', 'saturated_fat', 'energy'],
         occasions=['snack', 'meal'], kcal=(100, 400),
         match=['french fries', 'fries', 'nuggets', 'samosa', 'momos', 'spring roll', 'patty', 'patties', 'tikki',
                'smiles', 'garlic bread', 'pizza', 'paratha', 'kebab', 'cutlet', 'veggie fingers', 'frozen snack',
                'hash brown'],
         exclude=['frozen dessert', 'ice cream', 'peas', 'sweet corn', 'mixed veg', 'fruit', 'berries', 'yogurt',
                  'yoghurt', 'kulfi', 'pizza base', 'pizza sauce', 'masala powder', 'mix']),
    dict(id='oils_fats', name='Cooking oils, ghee & butter', branch='food', focus=['saturated_fat'],
         occasions=['meal'], kcal=(650, 920),
         match=['oil', 'ghee', 'butter', 'vanaspati', 'margarine', 'cooking oil', 'mustard oil', 'groundnut oil',
                'sunflower oil', 'rice bran oil', 'olive oil', 'coconut oil', 'sesame oil', 'gingelly oil', 'til oil',
                'canola oil', 'soybean oil', 'soyabean oil', 'palm oil', 'blended oil', 'kachi ghani'],
         exclude=['peanut butter', 'almond butter', 'nut butter', 'butter milk', 'buttermilk', 'cookies', 'cookie',
                  'biscuit', 'popcorn', 'chicken', 'paneer', 'masala', 'chips', 'sardine', 'tuna', 'in oil', 'hair',
                  'massage', 'essential oil', 'body', 'oil free', 'butterscotch', 'cocoa butter', 'shea', 'garlic',
                  'naan', 'roti', 'chakli', 'murukku', 'toffee', 'candy', 'cake', 'croissant', 'pav']),
    dict(id='ketchup_sauces', name='Ketchup, sauces & chutneys', branch='food', focus=['sugars', 'sodium'],
         occasions=['meal', 'snack'], kcal=(10, 330),
         match=['ketchup', 'tomato sauce', 'chilli sauce', 'chili sauce', 'hot sauce', 'pasta sauce', 'pizza sauce',
                'schezwan', 'soy sauce', 'soya sauce', 'barbecue sauce', 'bbq sauce', 'sweet chilli', 'sauce', 'chutney',
                'salsa', 'pizza pasta sauce'],
         exclude=['mayonnaise', 'mayo', 'chips', 'nachos', 'cheese', 'dip', 'noodles', 'pasta ', 'mix', 'powder']),
    dict(id='mayo_dips', name='Mayonnaise, dips & sandwich spreads', branch='food',
         focus=['saturated_fat', 'sodium', 'energy'], occasions=['snack', 'meal'], kcal=(80, 760),
         match=['mayonnaise', 'mayo', 'sandwich spread', 'dip', 'hummus', 'cheese dip', 'tandoori mayo'],
         exclude=['chips', 'nachos', 'biscuit']),
    dict(id='soups', name='Instant soups', branch='food', focus=['sodium', 'saturated_fat'],
         occasions=['meal', 'snack'], kcal=(240, 480),
         match=['soup'], exclude=['soup sticks', 'noodles', 'bowl']),
    dict(id='pickles', name='Pickles', branch='food', focus=['sodium'],
         occasions=['meal'], kcal=(30, 450),
         match=['pickle', 'achar', 'achaar', 'aachar'], exclude=['chips', 'namkeen', 'masala powder']),
    dict(id='health_drinks', name='Health drinks & protein powders', branch='food', focus=['sugars'],
         occasions=['breakfast', 'snack'], kcal=(300, 460),
         match=['bournvita', 'horlicks', 'complan', 'boost', 'malt', 'health drink', 'nutrition drink', 'protein powder',
                'whey', 'plant protein', 'drink mix', 'milk mix', 'ragi malt', 'mass gainer', 'gainer', 'chocolate drink',
                'health food drink', 'protinex', 'ensure'],
         exclude=['biscuit', 'cookie', 'bar', 'cereal', 'infant', 'baby', 'cerelac', 'toddler', 'stage', 'pediasure']),
    dict(id='bars', name='Protein & snack bars', branch='food', focus=['sugars', 'saturated_fat'],
         occasions=['snack'], kcal=(300, 560),
         match=['protein bar', 'energy bar', 'granola bar', 'muesli bar', 'nutrition bar', 'snack bar', 'date bar',
                'millet bar', 'oats bar', 'fruit bar', 'nut bar', 'nutty bar', 'protein bars', 'whey bar'],
         exclude=['chocolate bar']),
    dict(id='sweets_mithai', name='Indian sweets & mithai', branch='food', focus=['sugars', 'saturated_fat'],
         occasions=['snack', 'party'], kcal=(200, 600), allow_step_within_red=True,
         match=['rasgulla', 'gulab jamun', 'soan papdi', 'soanpapdi', 'ladoo', 'laddu', 'laddoo', 'barfi', 'burfi',
                'halwa', 'kaju katli', 'peda', 'petha', 'mysore pak', 'chikki', 'gajak', 'rasmalai', 'kalakand',
                'mithai', 'sweets', 'jalebi', 'kheer', 'payasam', 'chena', 'sandesh'],
         exclude=['premix', 'instant mix', 'mix powder', 'powder', 'biscuit', 'cookie', 'cake']),
    dict(id='cakes', name='Cakes, muffins & brownies', branch='food', focus=['sugars', 'saturated_fat'],
         occasions=['snack', 'party'], kcal=(250, 560), allow_step_within_red=True,
         match=['cake', 'cakes', 'muffin', 'brownie', 'swiss roll', 'cupcake', 'pastry', 'mawa cake', 'plum cake',
                'tea cake', 'fruit cake', 'choco pie', 'chocopie'],
         exclude=['cheesecake mix', 'cake mix', 'premix', 'rice cake', 'rice cakes', 'pancake mix', 'ice cream', 'biscuit',
                  'cookie', 'rusk', 'bar', 'protein']),
    dict(id='dried_fruit', name='Dried fruit', branch='food', focus=['sugars'],
         occasions=['snack'], kcal=(200, 420),
         match=['dates', 'khajur', 'raisins', 'kishmish', 'prunes', 'dried figs', 'anjeer', 'anjir', 'apricots',
                'dried apricot', 'dried cranberries', 'dried blueberries', 'dried mango', 'dried kiwi', 'dried fruit',
                'dehydrated', 'fruit chips', 'amla candy'],
         exclude=['bar', 'syrup', 'chutney', 'pickle', 'ladoo', 'laddu', 'cake', 'biscuit', 'cookie', 'nuts and', 'trail mix',
                  'mixed nuts', 'chocolate', 'coated']),
    dict(id='masalas', name='Masalas & spice mixes', branch='food', focus=['sodium'],
         occasions=['meal'], kcal=(150, 520),
         match=['masala', 'spice mix', 'sambar powder', 'rasam powder', 'podi', 'seasoning', 'garam masala',
                'chaat masala', 'pav bhaji masala', 'biryani masala', 'curry powder', 'kitchen king', 'meat masala',
                'chicken masala'],
         exclude=['chips', 'namkeen', 'noodles', 'oats', 'peanuts', 'cashew', 'makhana', 'papad', 'ready to eat', 'paneer',
                  'chai', 'tea', 'soda', 'dosa', 'pasta', 'popcorn', 'puffs', 'khakhra', 'bhujia', 'sev', 'mixture',
                  'chana', 'corn', 'biscuit', 'chikki', 'pickle', 'achar', 'turmeric', 'haldi']),
]

# Open Food Facts' own categories, used only when the name places a product in no group (brand-only names
# such as "Fuse" or "Smoodh"). Specific tags only; each record says how it was placed.
CATEGORY_GROUPS = [
    ('en:flavoured-milks', 'flavoured_milk'), ('en:milkshakes', 'flavoured_milk'), ('en:sodas', 'soft_drinks'),
    ('en:carbonated-drinks', 'soft_drinks'), ('en:energy-drinks', 'soft_drinks'), ('en:fruit-juices', 'fruit_drinks'),
    ('en:nectars', 'fruit_drinks'), ('en:peanut-butters', 'spreads'), ('en:nut-butters', 'spreads'),
    ('en:ice-creams', 'ice_cream'), ('en:frozen-desserts', 'ice_cream'), ('en:breakfast-cereals', 'breakfast_cereals'),
    ('en:mueslis', 'breakfast_cereals'), ('en:granolas', 'breakfast_cereals'), ('en:chocolates', 'chocolate'),
    ('en:chocolate-candies', 'chocolate'), ('en:candies', 'chocolate'), ('en:biscuits', 'biscuits'), ('en:cookies', 'biscuits'),
    ('en:crackers', 'biscuits'), ('en:instant-noodles', 'instant_noodles'), ('en:nuts', 'nuts_seeds'),
    ('en:chips-and-fries', 'salty_snacks'), ('en:crisps', 'salty_snacks'), ('en:yogurts', 'curd_yoghurt'),
    ('en:cheeses', 'cheese'), ('en:breads', 'bread'), ('en:jams', 'jams'), ('en:marmalades', 'jams'),
    ('en:pastas', 'pasta_vermicelli'), ('en:ketchup', 'ketchup_sauces'), ('en:sauces', 'ketchup_sauces'),
    ('en:mayonnaises', 'mayo_dips'), ('en:soups', 'soups'), ('en:pickles', 'pickles'),
    ('en:protein-powders', 'health_drinks'), ('en:cereal-bars', 'bars'), ('en:protein-bars', 'bars'),
    ('en:cakes', 'cakes'), ('en:muffins', 'cakes'), ('en:dried-fruits', 'dried_fruit'), ('en:spice-mixes', 'masalas'),
    ('en:vegetable-oils', 'oils_fats'), ('en:ghee', 'oils_fats'), ('en:butters', 'oils_fats'), ('en:flours', 'flours_grains'),
    ('en:rices', 'flours_grains')]
CURRENT = dict(categories=[])
_name_group = cs.group_for


def group_by_name_or_category(name, drink):
    gid = _name_group(name, drink)
    if gid is not None:
        CURRENT['by'] = 'name'
        return gid
    branch = 'drink' if drink else 'food'
    for tag, group in CATEGORY_GROUPS:
        g = next(x for x in cs.GROUPS if x['id'] == group)
        if tag in CURRENT['categories'] and (g['branch'] == branch or group in ('ice_cream', 'oils_fats')) \
                and not cs.words(name, g['exclude']):
            CURRENT['by'] = 'off-category ' + tag
            return group
    return None


def install_groups():
    for g in NEW_GROUPS:
        cs.PLAUSIBLE_KCAL[g['id']] = g['kcal']
        cs.GROUPS.append({k: v for k, v in g.items() if k != 'kcal'})
    cs.group_for = group_by_name_or_category


def stamp(p):
    """When this product's label was last evidenced: its newest pack photo, else when its record was created."""
    created, image = p.get('created_t') or 0, p.get('last_image_t') or 0
    return max(created, image)


def recent(p):
    return (p.get('created_t') or 0) >= SINCE or (p.get('last_image_t') or 0) >= SINCE


def enrich(rec, hit):
    """Fields the curation cleaner does not take but the app's rules read (as enrich_catalogue.py)."""
    n = hit.get('nutriments') or {}
    num = lambda v: float(v) if isinstance(v, (int, float)) or (isinstance(v, str) and v.replace('.', '', 1).isdigit()) else None
    per100 = rec['per100']
    carb, added = num(n.get('carbohydrates_100g')), num(n.get('added-sugars_100g'))
    if carb is not None and carb <= 100 and carb + 0.5 >= per100['sugars_g']:
        per100['carbohydrate_g'] = round(carb, 2)
    if added is not None and added <= per100['sugars_g'] + 0.5:
        per100['added_sugars_g'] = round(added, 2)
    tags = hit.get('ingredients_analysis_tags') or []
    labels = hit.get('labels_tags') or []
    if rec.get('veg') is None:
        rec['veg'] = 'vegan' if 'en:vegan' in tags else 'non-vegetarian' if 'en:non-vegetarian' in tags \
            else 'vegetarian' if 'en:vegetarian' in tags else None
    if 'en:halal' in labels:
        rec['halal'] = True
    serving = num(hit.get('serving_quantity'))
    if serving:
        rec['serving_g'] = serving
    ts = stamp(hit)
    rec['label_date'] = datetime.datetime.fromtimestamp(ts, datetime.timezone.utc).strftime('%Y-%m-%d')
    rec['off_created'] = datetime.datetime.fromtimestamp(hit.get('created_t') or 0, datetime.timezone.utc).strftime('%Y-%m-%d')
    rec['categories'] = [t for t in (hit.get('categories_tags') or []) if t.startswith('en:')][:12]
    rec['labels'] = labels[:12]
    # How often shoppers scan it on Open Food Facts: the best open signal of what is actually on the shelf.
    rec['scans'] = int(hit.get('unique_scans_n') or 0)
    for key in ('image_url', 'image_ingredients_url', 'image_nutrition_url'):
        if hit.get(key):
            rec[key.replace('_url', '')] = hit[key]


def earlier_dates(gtins):
    """Created and photo dates for earlier catalogue products the harvest did not return (cached)."""
    cache_path = BUILD / 'earlier_dates.json'
    cache = json.loads(cache_path.read_text(encoding='utf-8')) if cache_path.exists() else {}
    for gtin in gtins:
        if gtin in cache:
            continue
        url = 'https://world.openfoodfacts.org/api/v2/product/%s.json?fields=created_t,last_image_t' % gtin
        req = urllib.request.Request(url, headers={'User-Agent': 'BetterChoiceIndia-curation/1.2 (India catalogue for nutritionist review)'})
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                cache[gtin] = json.loads(r.read().decode('utf-8')).get('product') or {}
        except Exception as e:      # left unknown, so treated as not evidenced from 2025
            print('  date lookup failed for', gtin, e)
            continue
        cache_path.write_text(json.dumps(cache), encoding='utf-8')
        time.sleep(1)               # product lookups: well under 100 a minute
    return cache


def eligible(rec, stepping):
    """A swap candidate: every valid product placed in a group, whatever its colour. Whether it is better than the
    product on screen, and right for this shopper, is decided on the phone by the app's engine against that product
    (founder, 8 Oct 2026: three recommendations from the same category on every platform; green first, and a yellow
    or still-red one only when it is a real improvement, said as such)."""
    return bool(rec.get('group'))


# What the app reads from each product (SwapCatalogue.Product); the rest stays in products_2025.json for the
# nutritionist and the database, so the catalogue shipped in the app stays small.
APP_FIELDS = ('id', 'gtin', 'brand', 'name', 'group', 'unit', 'pack', 'beverage', 'nss', 'halal', 'veg', 'per100',
              'serving_g', 'ingredients', 'contains', 'may_contain', 'status', 'source', 'source_url', 'label_date',
              'draft_band', 'draft_letter', 'off_group')


def for_app(rec):
    return {k: rec[k] for k in APP_FIELDS if k in rec and rec[k] not in (None, '', [])}


def category_group(rec):
    """The group Open Food Facts' own categories put a product in, or None."""
    tags = rec.get('categories') or []
    for tag, group in CATEGORY_GROUPS:
        if tag in tags:
            return group
    return None


# Composition: a product whose protein, fat or carbohydrate per 100 g lies far outside what the rest of its group holds
# (beyond six interquartile ranges of the group's own quartiles: twice Tukey's "far out" fence, because groups are small
# and mixed, so a protein milkshake or an almond cookie stays) is another kind of food that a flavour word filed here
# ("Chocolate Isolate" is a protein powder, "Grated coconut" is not a dried fruit). It stays a candidate only when Open
# Food Facts' own category confirms the group. No product or group is named in this rule.
FENCE, MIN_GROUP = 6.0, 8


def quantile(values, f):
    values = sorted(values)
    i = f * (len(values) - 1)
    lo = int(i)
    hi = min(lo + 1, len(values) - 1)
    return values[lo] + (values[hi] - values[lo]) * (i - lo)


def composition_outliers(products):
    """{gtin: 'protein 77 g, outside 0-17 g for chocolate'} for products their own group's composition does not support."""
    by_group = {}
    for p in products:
        if p.get('group'):
            by_group.setdefault(p['group'], []).append(p)
    out = {}
    for group, members in by_group.items():
        for key, label in (('protein_g', 'protein'), ('total_fat_g', 'fat'), ('carbohydrate_g', 'carbohydrate')):
            values = [m['per100'][key] for m in members if m['per100'].get(key) is not None]
            if len(values) < MIN_GROUP:
                continue
            q1, q3 = quantile(values, .25), quantile(values, .75)
            spread = max(q3 - q1, 1.0)
            lo, hi = q1 - FENCE * spread, q3 + FENCE * spread
            for m in members:
                v = m['per100'].get(key)
                if v is None or lo <= v <= hi or category_group(m) == group or m['gtin'] in out:
                    continue
                out[m['gtin']] = '%s %.1f g outside %.1f-%.1f g for its group (%s)' % (label, v, max(lo, 0), hi, group)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dry-run', action='store_true', help='report only; never rewrite the app catalogue')
    ap.add_argument('--since', type=int, default=2025, help='earliest label year (dry runs only may change it)')
    args = ap.parse_args()
    if args.since != 2025 and not args.dry_run:
        raise SystemExit('the catalogue keeps labels from 2025 onwards, as the founder asked; other years are for dry runs')
    global SINCE
    SINCE = int(datetime.datetime(args.since, 1, 1, tzinfo=datetime.timezone.utc).timestamp())
    complete = (BUILD / 'export_india.json').exists() or (BUILD / 'harvest.json').exists()
    if not args.dry_run and not complete:
        raise SystemExit('no complete source yet (export_india.json or harvest.json); run with --dry-run until there is')
    install_groups()
    hits = {}
    for f in sorted((BUILD / 'pages').glob('page_*.json')):       # API pages, if any
        for p in json.loads(f.read_text(encoding='utf-8')).get('products', []):
            if p.get('code'):
                hits[p['code']] = p
    export = BUILD / 'export_india.jsonl'                           # the full export, preferred where both have a product
    if (BUILD / 'export_india.json').exists() and export.exists():
        with open(export, encoding='utf-8') as f:
            for line in f:
                p = json.loads(line)
                if p.get('code'):
                    hits[p['code']] = p
    # Grades from the app's own engine (CatalogueCoverageHarness, products_graded.jsonl) decide over the draft
    # grades here wherever they exist: a candidate the app would not offer must not enter the catalogue.
    java = {}
    if (BUILD / 'products_graded.jsonl').exists():
        with open(BUILD / 'products_graded.jsonl', encoding='utf-8') as f:
            for line in f:
                g = json.loads(line)
                java[g['gtin']] = g
    reasons, products = Counter(), []
    rejected = []
    old_records = 0
    for code, hit in hits.items():
        if not recent(hit):
            old_records += 1
            continue
        CURRENT['categories'], CURRENT['by'] = hit.get('categories_tags') or [], None
        rec, why = cs.clean(hit, None, ungrouped_ok=True)
        if rec is None:
            reasons[why.split(' (')[0] if why else '?'] += 1
            rejected.append(dict(gtin=code, name=(hit.get('product_name') or '')[:80], brand=str(hit.get('brands') or '')[:40], reason=why))
            continue
        rec['ingredients'] = (hit.get('ingredients_text_en') or hit.get('ingredients_text') or '').strip()[:1500]
        if rec.get('group'):
            rec['grouped_by'] = CURRENT['by'] or 'name'
        enrich(rec, hit)
        cs.regrade(rec)
        if rec['gtin'] in java:
            g = java[rec['gtin']]
            rec['draft_band'], rec['draft_letter'], rec['draft_score'] = g['band'], g['letter'], g['score']
            rec['graded_by'] = 'app engine'
        products.append(rec)

    # Always built from the catalogue as it was before the 2025 build (pinned beside the build), so re-running
    # gives the same result instead of building on its own previous output.
    base = BUILD / 'swaps_v1.original.json'
    data = json.loads((base if base.exists() else APP).read_text(encoding='utf-8'))
    # The app places products with the catalogue's own groups, so they are written from the same definitions.
    defined = {g['id']: g for g in cs.GROUPS}
    for g in data['groups']:
        for key in ('match', 'exclude', 'focus', 'occasions'):
            g[key] = defined[g['id']][key]
    old_groups = {g['id'] for g in data['groups']}
    data['groups'] += [{k: v for k, v in g.items() if k != 'kcal'} for g in NEW_GROUPS if g['id'] not in old_groups]
    stepping = {g['id'] for g in data['groups'] if g.get('allow_step_within_red')}
    fresh = {p['gtin'] for p in products}
    dates = {} if args.dry_run else earlier_dates([p['gtin'] for p in data['products'] if p.get('gtin') and p['gtin'] not in fresh])
    for p in data['products']:
        if p.get('gtin') in java:
            g = java[p['gtin']]
            p['draft_band'], p['draft_letter'], p['draft_score'] = g['band'], g['letter'], g['score']
    dated = [p for p in data['products'] if p.get('gtin') in fresh or recent(dates.get(p.get('gtin'), {}))]
    kept_old = [p for p in dated if eligible(p, None)]
    brief = lambda p: dict(gtin=p.get('gtin'), brand=p.get('brand'), name=p.get('name'), group=p.get('group'),
                           letter=p.get('draft_letter'))
    pre_2025 = [brief(p) for p in data['products'] if p not in dated]
    data.pop('earlier_not_green', None)
    known = {p['gtin'] for p in kept_old}
    outliers = composition_outliers(products)
    for p in products:
        # Open Food Facts' own reading of what the product is, kept so the app can check it against the group.
        p['off_group'] = category_group(p)
    added = [p for p in products if p.get('group') and eligible(p, stepping) and p['gtin'] not in known and p['gtin'] not in outliers]
    data['products'] = [for_app(p) for p in kept_old + added]
    with open(BUILD / 'composition_outliers.csv', 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['gtin', 'brand', 'name', 'group', 'reason'])
        for p in products:
            if p['gtin'] in outliers:
                w.writerow([p['gtin'], p.get('brand'), p.get('name'), p.get('group'), outliers[p['gtin']]])
    for g in data['groups']:
        g.pop('allow_step_within_red', None)       # the engine's one rule replaces per-group steps within red
    data['version'] = 'swaps_v1+off-india-2025'
    data['generated_at'] = datetime.date.today().isoformat()
    data['note'] = ('Drafts from Open Food Facts India, labels evidenced from 1 Jan 2025 (record created or pack '
                    'photographed); a nutritionist approves each before release builds show it.')
    data['not_in_2025_harvest'] = pre_2025
    if not args.dry_run:
        backup = BUILD / ('swaps_v1.before-%s.json' % time.strftime('%Y%m%d-%H%M%S'))
        raw = APP.read_text(encoding='utf-8')
        backup.write_text(raw, encoding='utf-8')     # the catalogue as it was, kept beside the build
        APP.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')

    BUILD.mkdir(parents=True, exist_ok=True)
    (BUILD / 'products_2025.json').write_text(json.dumps(products, ensure_ascii=False), encoding='utf-8')
    with open(BUILD / 'rejected_2025.csv', 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=['gtin', 'brand', 'name', 'reason'])
        w.writeheader()
        w.writerows(rejected)
    by_group = Counter(p.get('group') or '(none)' for p in products)
    cand = Counter(p['group'] for p in data['products'])
    print('harvested %d unique; before 2025 %d; 2025+ valid %d; rejected %d' % (len(hits), old_records, len(products), len(rejected)))
    print('rejections:', reasons.most_common(12))
    print('app catalogue: kept %d earlier products with 2025+ labels, %d not in the 2025 harvest, added %d; total %d'
          % (len(kept_old), len(pre_2025), len(added), len(data['products'])))
    print('left out as another kind of food by composition: %d (composition_outliers.csv)' % len(outliers))
    for g in data['groups']:
        print('  %-18s valid %4d  candidates %4d' % (g['id'], by_group.get(g['id'], 0), cand.get(g['id'], 0)))
    print('  %-18s valid %4d' % ('(no group)', by_group.get('(none)', 0)))


if __name__ == '__main__':
    main()
