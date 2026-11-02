"""Curation assistant for the nutritionist: drafts the healthier-swap catalogue.

What it does, and what it deliberately does not do:
  * Pulls candidate products sold in India from Open Food Facts, one substitution group at a time.
  * Throws out any record whose numbers do not hang together (missing core values, saturated fat
    above total fat, sugars above carbohydrate, energy that disagrees with the macros by more than
    20%, or a data-quality error flagged by Open Food Facts itself).
  * Grades what is left with the same published methods the app uses (Nutri-Score 2017 food,
    Nutri-Score 2023 beverages) and keeps only products that grade A or B.
  * Writes the app's draft catalogue and a review sheet for the nutritionist.

  * It does NOT verify anything. Open Food Facts is volunteer-entered. Every product it writes is
    status "draft" until a nutritionist checks it against the physical pack; the app labels drafts
    as such and never presents them as verified.

Licence: the drafted records derive from Open Food Facts (ODbL 1.0), so the draft catalogue is
released under ODbL with attribution. Records a nutritionist re-enters from the pack are ours.

Usage:  python tools/curate_swaps.py [--out-app PATH] [--out-review DIR]
"""
import argparse
import csv
import datetime as dt
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Packs whose label a person has checked against their open-data record (catalogue/label_checks.json).
_CHECKS = ROOT / 'catalogue' / 'label_checks.json'
LABEL_CHECKS = {c['gtin']: c for c in json.loads(_CHECKS.read_text(encoding='utf-8'))['checks']} if _CHECKS.exists() else {}
FSSAI_LABEL_SATURATED_FAT_G = 22    # Evidence fssai.label.saturated.fat: FSSAI Labelling 2020, p10, regulation 5(3)(b)
API = 'https://search.openfoodfacts.org/search'
UA = 'BetterChoiceIndia-curation/1.0 (nutritionist draft; contact via app)'
FIELDS = ('code,product_name,product_name_en,brands,quantity,nutriments,ingredients_text_en,ingredients_text,'
          'allergens_tags,traces_tags,labels_tags,data_quality_errors_tags,categories_tags')
INDIA = 'countries_tags:"en:india"'

# --- Substitution groups -------------------------------------------------------------------------
# `match` / `exclude` are whole-word phrases the APP uses to place the product the shopper is looking
# at into a group. `focus` is what a swap must improve. `queries` is where this assistant looks.
GROUPS = [
    dict(id='flavoured_milk', name='Milk drinks & milkshakes', branch='drink', search='buttermilk',
         focus=['sugars', 'saturated_fat'], occasions=['snack', 'breakfast'],
         # DRAFT RULE: few milk drinks reach green or yellow, so a swap one official Nutri-Score letter
         # better (E -> D) is allowed and shown as "still red, a step better".
         allow_step_within_red=True,
         match=['milkshake', 'shake', 'flavoured milk', 'flavored milk', 'cold coffee', 'lassi', 'badam milk',
                'chocolate milk', 'rose milk', 'buttermilk', 'butter milk', 'chaas', 'chhach', 'kool',
                'toned milk', 'double toned', 'skimmed milk', 'slim milk', 'cow milk', 'buffalo milk', 'soy milk',
                'soya milk', 'almond milk', 'oat milk', 'curd drink', 'dahi drink', 'protein shake', 'milk drink',
                'chaach', 'chhaas', 'masala chaas'],
         exclude=['powder', 'mix', 'coconut milk', 'condensed', 'milk powder'],
         queries=['categories_tags:"en:flavoured-milks" AND ' + INDIA, 'milkshake ' + INDIA, 'lassi ' + INDIA,
                  'buttermilk ' + INDIA, 'chaas ' + INDIA, 'toned milk ' + INDIA, 'soy milk ' + INDIA,
                  'almond milk ' + INDIA, 'oat milk ' + INDIA, 'protein shake ' + INDIA,
                  'categories_tags:"en:milks" AND ' + INDIA]),
    dict(id='soft_drinks', name='Soft drinks & iced tea', branch='drink', search='zero sugar',
         focus=['sugars'], occasions=['snack', 'party', 'meal'],
         match=['cola', 'coke', 'soda', 'soft drink', 'carbonated', 'sprite', 'thums up', 'pepsi', 'fanta',
                '7up', 'mountain dew', 'limca', 'mirinda', 'fizz', 'tonic water', 'energy drink', 'sparkling',
                'iced tea', 'ice tea', 'kombucha', 'club soda', 'soda water'],
         exclude=['powder', 'baking soda'],
         queries=['categories_tags:"en:sodas" AND ' + INDIA, 'categories_tags:"en:carbonated-drinks" AND ' + INDIA,
                  'sparkling water ' + INDIA, 'zero sugar ' + INDIA, 'soda water ' + INDIA, 'iced tea ' + INDIA,
                  'kombucha ' + INDIA, 'categories_tags:"en:iced-teas" AND ' + INDIA]),
    dict(id='fruit_drinks', name='Juices & fruit drinks', branch='drink', search='coconut water',
         focus=['sugars'], occasions=['snack', 'breakfast', 'meal'],
         allow_step_within_red=True,
         match=['juice', 'nectar', 'fruit drink', 'mango drink', 'maaza', 'frooti', 'slice', 'aam panna',
                'jaljeera', 'jal jeera', 'coconut water', 'tender coconut', 'sharbat', 'squash', 'nimbu pani',
                'lemonade', 'cold pressed', 'kokum', 'sol kadhi', 'mango', 'orange', 'apple', 'guava',
                'pomegranate', 'litchi', 'lychee', 'cranberry', 'mixed fruit', 'santra', 'pineapple', 'grape',
                'fruit delight', 'pulpy', 'tropicana', 'minute maid'],
         # A drink to dilute, not one made from concentrate ("Concentrate Coconut Water" is ready to drink).
         exclude=['powder', 'fruit concentrate', 'juice concentrate', 'drink concentrate', 'jelly'],
         queries=['categories_tags:"en:fruit-juices" AND ' + INDIA, 'categories_tags:"en:nectars" AND ' + INDIA,
                  'coconut water ' + INDIA, 'juice ' + INDIA, 'tender coconut ' + INDIA, 'cold pressed ' + INDIA,
                  'nimbu pani ' + INDIA, 'aam panna ' + INDIA, 'jaljeera ' + INDIA,
                  'categories_tags:"en:fruit-based-beverages" AND ' + INDIA]),
    dict(id='spreads', name='Peanut butter & sweet spreads', branch='food', search='unsweetened peanut butter',
         focus=['sugars', 'saturated_fat'], occasions=['breakfast', 'snack'],
         # DRAFT RULE for the nutritionist to confirm: nut butters are more energy-dense than sweet
         # spreads, so a swap may be higher in calories provided the card says so.
         allow_new_high=['energy'],
         match=['peanut butter', 'almond butter', 'nut butter', 'spread', 'nutella', 'hazelnut spread',
                'chocolate spread'],
         exclude=['cheese spread', 'mayonnaise', 'butter 100', 'table butter', 'bar', 'powder'],
         queries=['categories_tags:"en:peanut-butters" AND ' + INDIA, 'peanut butter ' + INDIA,
                  'categories_tags:"en:sweet-spreads" AND ' + INDIA]),
    dict(id='ice_cream', name='Ice cream & frozen desserts', branch='food', search='low sugar ice cream',
         focus=['sugars', 'saturated_fat', 'energy'], occasions=['snack', 'party'],
         match=['ice cream', 'icecream', 'frozen dessert', 'kulfi', 'cone', 'sundae', 'cornetto', 'gelato',
                'frozen yogurt', 'frozen yoghurt'],
         exclude=['milkshake', 'shake', 'flavour', 'flavor'],
         queries=['categories_tags:"en:ice-creams" AND ' + INDIA, 'frozen dessert ' + INDIA, 'kulfi ' + INDIA]),
    dict(id='breakfast_cereals', name='Breakfast cereals', branch='food', search='muesli no added sugar',
         focus=['sugars', 'fibre'], occasions=['breakfast'],
         match=['cornflakes', 'corn flakes', 'chocos', 'cereal', 'muesli', 'granola', 'oats', 'wheat flakes',
                'bran flakes', 'ragi flakes', 'millet flakes', 'ragi bites'],
         exclude=['biscuit', 'cookies', 'noodles'],
         queries=['categories_tags:"en:breakfast-cereals" AND ' + INDIA, 'muesli ' + INDIA, 'oats ' + INDIA,
                  'granola ' + INDIA]),
    dict(id='chocolate', name='Chocolate & confectionery', branch='food', search='dark chocolate',
         focus=['sugars', 'saturated_fat'], occasions=['snack'],
         # DRAFT RULE: almost every chocolate, milk or dark, is Nutri-Score E, so a same-band swap
         # cannot exist. A bar one official letter better (E -> D) is allowed, as for biscuits.
         allow_step_within_red=True,
         match=['chocolate', 'dairy milk', '5 star', 'kitkat', 'kit kat', 'munch', 'perk', 'snickers', 'bournville',
                'galaxy', 'eclairs', 'toffee', 'candy'],
         exclude=['biscuit', 'cookie', 'cookies', 'chocolate milk', 'milkshake', 'shake', 'drink', 'spread', 'syrup', 'cake', 'ice cream',
                  'cereal', 'chocos', 'chocolate wafer', 'cream wafer', 'muesli', 'museli', 'granola', 'oats', 'protein', 'powder',
                  'whey', 'gainer', 'nutrition', 'masala'],
         queries=['categories_tags:"en:chocolates" AND ' + INDIA, 'dark chocolate ' + INDIA]),
    dict(id='biscuits', name='Biscuits & cookies', branch='food', search='digestive biscuits',
         focus=['sugars', 'saturated_fat'], occasions=['snack', 'breakfast'],
         # DRAFT RULE: almost no sweet biscuit reaches green or yellow, so a biscuit one official
         # Nutri-Score letter better (E -> D) is allowed and shown as "still red, a step better".
         allow_step_within_red=True,
         match=['biscuit', 'biscuits', 'cookie', 'cookies', 'bikis', 'rusk', 'digestive', 'marie', 'cracker',
                'crackers', 'cream wafer', 'chocolate wafer', 'bourbon', 'hide & seek', 'good day', 'oreo',
                'dark fantasy', 'jim jam', 'parle g', 'marie gold', 'nutrichoice', 'bounce', 'unibic',
                'nankhatai', 'nan khatai', 'shortbread', 'toast'],
         # Protein powders flavoured "cookies & cream" are not biscuits.
         exclude=['dog', 'bread', 'french toast', 'protein powder', 'whey', 'pro performance', 'isolate', 'powder'],
         queries=['categories_tags:"en:biscuits" AND ' + INDIA, 'digestive ' + INDIA, 'ragi biscuits ' + INDIA,
                  'oats biscuits ' + INDIA, 'marie ' + INDIA, 'categories_tags:"en:cookies" AND ' + INDIA,
                  'oats cookies ' + INDIA, 'ragi cookies ' + INDIA, 'millet cookies ' + INDIA, 'jowar ' + INDIA,
                  'multigrain biscuits ' + INDIA, 'cracker ' + INDIA, 'sugar free biscuits ' + INDIA,
                  'high fibre ' + INDIA, 'whole wheat biscuits ' + INDIA, 'atta biscuits ' + INDIA,
                  'nutrichoice ' + INDIA, 'protein cookies ' + INDIA, 'rusk ' + INDIA,
                  'categories_tags:"en:crackers" AND ' + INDIA]),
    dict(id='instant_noodles', name='Instant noodles & quick meals', branch='food', search='atta noodles',
         focus=['sodium', 'saturated_fat'], occasions=['meal', 'snack'],
         match=['noodles', 'maggi', 'yippee', 'ramen', 'hakka', 'cup noodles', 'instant pasta', 'top ramen',
                'chowmein'],
         exclude=[],
         queries=['categories_tags:"en:instant-noodles" AND ' + INDIA, 'noodles ' + INDIA, 'atta noodles ' + INDIA,
                  'millet noodles ' + INDIA]),
    dict(id='nuts_seeds', name='Nuts & seeds', branch='food', search='unsalted nuts',
         focus=['sodium', 'saturated_fat'], occasions=['snack', 'party'],
         match=['peanuts', 'peanut', 'almonds', 'almond', 'cashews', 'cashew', 'kaju', 'pistachio', 'pista',
                'walnuts', 'walnut', 'mixed nuts', 'trail mix', 'dry fruits', 'groundnut', 'pumpkin seeds',
                'sunflower seeds', 'roasted seeds', 'seed mix', 'nuts and seeds', 'seeds mix'],
         exclude=['butter', 'chikki', 'chocolate', 'choco', 'cocoa', 'milk', 'oil', 'biscuit', 'cookie', 'katli',
                  'bar', 'ball', 'laddoo', 'ladoo', 'chia', 'flax', 'flaxseed', 'basil', 'sabja', 'powder', 'flour'],
         queries=['categories_tags:"en:nuts" AND ' + INDIA, 'almonds ' + INDIA, 'peanuts ' + INDIA,
                  'trail mix ' + INDIA, 'seeds ' + INDIA]),
    dict(id='salty_snacks', name='Salty crunchy snacks', branch='food', search='roasted makhana',
         focus=['sodium', 'saturated_fat', 'energy'], occasions=['snack', 'party'],
         match=['chips', 'crisps', 'wafers', 'namkeen', 'bhujia', 'mixture', 'sev', 'puffs', 'puff', 'nachos', 'makhana',
                'fox nuts', 'roasted chana', 'kurkure', 'popcorn', 'sticks', 'rings', 'khakhra', 'chakli',
                'murukku', 'mathri', 'moong dal', 'bhel', 'chivda', 'chips', 'crispies', 'bites', 'papad', 'papadum',
                'appalam'],
         exclude=['chocolate', 'choco', 'biscuit', 'cookie', 'cake', 'cream wafer', 'cream roll', 'cream puff', 'vanilla',
                  'ice cream', 'bread'],
         queries=['categories_tags:"en:chips-and-fries" AND ' + INDIA, 'makhana ' + INDIA,
                  'roasted chana ' + INDIA, 'namkeen ' + INDIA, 'categories_tags:"en:salty-snacks" AND ' + INDIA,
                  'popcorn ' + INDIA, 'baked ' + INDIA]),
]

# --- Grading (mirrors NutriGrade / BeverageGrade in the app; the app re-grades at runtime) ------
# Nutri-Score 2023 food tables (Scientific Committee update report 2022, recap PDF p130-135), as in NutriGrade.
FOOD = dict(energy_kj=[335, 670, 1005, 1340, 1675, 2010, 2345, 2680, 3015, 3350],
            sugars=[3.4, 6.8, 10, 14, 17, 20, 24, 27, 31, 34, 37, 41, 44, 48, 51], sat=[1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
            salt=[0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.4, 2.6, 2.8, 3.0, 3.2, 3.4, 3.6, 3.8, 4.0],
            fibre=[3.0, 4.1, 5.2, 6.3, 7.4], protein=[2.4, 4.8, 7.2, 9.6, 12, 14, 17],
            sat_energy_kj=[120, 240, 360, 480, 600, 720, 840, 960, 1080, 1200], sat_ratio=[10, 16, 22, 28, 34, 40, 46, 52, 58, 64])
NUT_BUTTERS = ['peanut butter', 'almond butter', 'cashew butter', 'nut butter']
DRINK = dict(energy_kj=[30, 90, 150, 210, 240, 270, 300, 330, 360, 390], sugars=[0.5, 2, 3.5, 5, 6, 7, 8, 9, 10, 11],
             sat=[1, 2, 3, 4, 5, 6, 7, 8, 9, 10], salt=[0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0],
             protein=[1.2, 1.5, 1.8, 2.1, 2.4, 2.7, 3.0], fibre=[3, 4.1, 5.2, 6.3, 7.4])
SWEETENERS = ['aspartame', 'sucralose', 'acesulfame', 'saccharin', 'stevia', 'steviol', 'neotame',
              '950', '951', '952', '954', '955', '960', '961']
# Identical to Beverage.NUT_PULSE_WORDS / COMPOSITE_WORDS in the app, so the draft grade matches it.
NUT_WORDS = ['peanut', 'groundnut', 'almond', 'cashew', 'kaju', 'walnut', 'akhrot', 'pistachio', 'pista',
             'hazelnut', 'mixed nuts', 'dry fruits', 'roasted chana', 'chana', 'chickpea']
COMPOSITE = ['chocolate', 'bar', 'biscuit', 'cookie', 'cake', 'butter', 'spread', 'chikki', 'brittle', 'masala',
             'coated', 'besan', 'namkeen', 'bhujia', 'mixture', 'mix', 'honey', 'sweet', 'ladoo', 'laddoo', 'barfi',
             'burfi', 'halwa', 'cream', 'oil', 'flour', 'atta', 'milk', 'katli', 'roll', 'pak', 'sattu']


def letter(score, drink):
    """The official five-letter Nutri-Score. Our band C covers D and E; a step from E to D is real."""
    if drink:
        return 'B' if score <= 2 else 'C' if score <= 6 else 'D' if score <= 9 else 'E'
    return 'A' if score <= 0 else 'B' if score <= 2 else 'C' if score <= 10 else 'D' if score <= 18 else 'E'


# Ingredient facts, read only from a declared ingredient list. Mirrors IngredientFacts in the app.
GRAINS = [('rolled oats', 'Oats'), ('oat flour', 'Oats'), ('oats', 'Oats'), ('oat', 'Oats'),
          ('whole wheat', 'Whole wheat'), ('wholewheat', 'Whole wheat'), ('atta', 'Whole wheat'),
          ('whole grain', 'Whole grain'), ('wholegrain', 'Whole grain'), ('finger millet', 'Ragi'), ('ragi', 'Ragi'),
          ('pearl millet', 'Bajra'), ('bajra', 'Bajra'), ('jowar', 'Jowar'), ('sorghum', 'Jowar'),
          ('millets', 'Millet'), ('millet', 'Millet'), ('brown rice', 'Brown rice'), ('quinoa', 'Quinoa'),
          ('barley', 'Barley')]
REFINED = ['refined wheat flour', 'maida', 'refined flour', 'enriched flour']
ADDED_SUGAR = ['sugar', 'sugars', 'invert sugar', 'invert syrup', 'liquid glucose', 'glucose syrup', 'dextrose',
               'jaggery', 'honey', 'corn syrup', 'fructose', 'sucrose', 'cane sugar', 'brown sugar', 'glucose']
FATS = ['oil', 'fat', 'fats', 'butter', 'ghee', 'shortening', 'vanaspati', 'margarine']


def parts(ingredients):
    text = re.sub(r'^\s*ingredients?\s*[:\-]?\s*', '', (ingredients or '').strip(), flags=re.I)
    out, depth, cur = [], 0, ''
    for ch in text:
        if ch in '([':
            depth += 1
        elif ch in ')]':
            depth = max(0, depth - 1)
        if ch in ',;' and depth == 0:
            out.append(cur.strip()); cur = ''
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return [x for x in out if x]


def ingredient_facts(ingredients, drink):
    items = parts(ingredients)
    good, bad = [], []
    if len(items) < 2:
        return good, bad
    first = items[0].lower()
    whole = next((label for word, label in GRAINS if words(first, [word])), None)
    if words(first, REFINED):
        bad.append('Refined flour (maida) is the main ingredient')
    elif whole:
        good.append(('%s are the main ingredient' if whole == 'Oats' else '%s is the main ingredient') % whole)
    if not drink and words(first, ADDED_SUGAR):
        bad.append('Sugar is the main ingredient')
    text = ' '.join(items).lower()
    sugar = words(text, ADDED_SUGAR)
    sweetener = any(w in text for w in SWEETENERS)
    if not sugar and not sweetener:
        good.append('No added sugar or sweeteners')
    elif not sugar:
        good.append('No added sugar')
    if not drink and words(text, FATS) and 'palm' not in text:
        good.append('No palm oil')
    return good, bad


def pts(value, thresholds):
    return sum(1 for t in thresholds if value > t)


def grade(p, drink, ingredients):
    kj = p['energy_kcal'] * 4.184
    if drink:
        sweet = any(w in ingredients for w in SWEETENERS) or (
            words(p['name'], ['diet', 'zero sugar', 'sugar free', 'sugar-free', 'no sugar', 'zero'])
            and words(p['name'], SWEET_WORDS))
        neg = pts(kj, DRINK['energy_kj']) + pts(p['sugars'], DRINK['sugars']) + pts(p['saturated_fat'], DRINK['sat']) \
            + pts(p['sodium_mg'] * 2.5 / 1000, DRINK['salt']) + (4 if sweet else 0)
        pos = pts(p['protein'], DRINK['protein']) + pts(p.get('fibre') or 0, DRINK['fibre'])
        raw = neg - pos
        return ('A' if raw <= 2 else 'B' if raw <= 6 else 'C'), raw
    name = p['name'].lower()
    # 2023: nuts, seeds, nut butters, oils, ghee and butter are scored with the fats, oils, nuts and seeds
    # table; cheese keeps its protein points whatever its A points (as FoodCategory.kind in the app).
    fats = p.get('group') in ('nuts_seeds', 'oils_fats') or words(name, NUT_BUTTERS)
    cheese = words(name, ['cheese', 'paneer'])
    salt = pts(p['sodium_mg'] * 2.5 / 1000, FOOD['salt'])
    if fats:
        fat = p.get('total_fat') or 0
        ratio = 100 * p['saturated_fat'] / fat if fat > 0 else 0
        neg = pts(p['saturated_fat'] * 37, FOOD['sat_energy_kj']) + pts(p['sugars'], FOOD['sugars']) \
            + sum(1 for t in FOOD['sat_ratio'] if ratio >= t) + salt
        limit = 7
    else:
        neg = pts(kj, FOOD['energy_kj']) + pts(p['sugars'], FOOD['sugars']) + pts(p['saturated_fat'], FOOD['sat']) + salt
        limit = 11
    fib = pts(p.get('fibre') or 0, FOOD['fibre'])
    prot = pts(p['protein'], FOOD['protein'])
    score = neg - fib if (neg >= limit and not cheese) else neg - fib - prot
    letter_ = ('A' if score <= -6 else 'B' if score <= 2 else 'C' if score <= 10 else 'D' if score <= 18 else 'E') if fats \
        else ('A' if score <= 0 else 'B' if score <= 2 else 'C' if score <= 10 else 'D' if score <= 18 else 'E')
    p['_letter'] = letter_
    return ('A' if letter_ in 'AB' else 'B' if letter_ == 'C' else 'C'), score


# --- Fetch and clean ----------------------------------------------------------------------------
def fetch(query, pages=2):
    hits = []
    for page in range(1, pages + 1):
        url = API + '?' + urllib.parse.urlencode({'q': query, 'page_size': 100, 'page': page, 'fields': FIELDS})
        req = urllib.request.Request(url, headers={'User-Agent': UA})
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=40) as r:
                    data = json.loads(r.read().decode('utf-8'))
                break
            except Exception:
                time.sleep(3 * (attempt + 1))
        else:
            print('  ! failed', query)
            return hits
        hits.extend(data.get('hits', []))
        if len(data.get('hits', [])) < 100:
            break
        time.sleep(1)
    return hits


QTY = re.compile(r'(\d+(?:[.,]\d+)?)\s*(kg|g|gm|gms|grams?|ml|l|ltr|litres?|liters?)\b', re.I)


def quantity(text):
    if not text:
        return None, None
    m = QTY.search(str(text))
    if not m:
        return None, None
    v = float(m.group(1).replace(',', '.'))
    u = m.group(2).lower()
    if u in ('kg',):
        return v * 1000, 'g'
    if u in ('l', 'ltr', 'litre', 'litres', 'liter', 'liters'):
        return v * 1000, 'ml'
    return v, ('ml' if u == 'ml' else 'g')


def words(text, phrases):
    t = ' ' + re.sub(r'[^a-z0-9&]+', ' ', text.lower()) + ' '
    return any(re.search(r'(?<![a-z0-9])' + re.escape(p) + r'(?:s|es)?(?![a-z0-9])', t) for p in phrases)


NEGATED = re.compile(r'(?<![a-z0-9])(?:no|not from|without|free from) (?:added sugars?|palm oil|[a-z0-9&]+)(?![a-z0-9])')


def positive(text):
    """The name without what it says the product is not ("No Maida", "Not from Concentrate"): the word after a negation
    neither places a product in a group nor keeps it out of one (SwapCatalogue.Group.fits does the same)."""
    return NEGATED.sub(' ', re.sub(r'[^a-z0-9&]+', ' ', text.lower()))


def group_for(name, drink):
    # Zepto titles add claims after a bar: the part before it decides, as in the app's groupFor;
    # the whole title is read only when that part names no group.
    head = name.split('|')[0]
    for text in ([head] if len(head) < len(name) else []) + [name]:
        text = positive(text)
        for g in GROUPS:
            if (g['branch'] == 'drink') != drink:
                continue
            if words(text, g['match']) and not words(text, g['exclude']):
                return g['id']
    return None


# Energy per 100 g/ml that a real product in each group can have. Outside it, the record was almost
# certainly entered per serving or as prepared, and every other figure is scaled wrong with it.
PLAUSIBLE_KCAL = dict(salty_snacks=(300, 650), biscuits=(330, 560), chocolate=(380, 650),
                      breakfast_cereals=(300, 500), instant_noodles=(300, 520), nuts_seeds=(350, 720),
                      spreads=(350, 720), ice_cream=(80, 380), soft_drinks=(0, 70), fruit_drinks=(0, 80),
                      flavoured_milk=(15, 160))
ZERO_WORDS = ['diet', 'zero', 'zero sugar', 'sugar free', 'no sugar', 'no added sugar', 'soda', 'water', 'black',
              'max', 'light', 'sparkling']
SWEET_WORDS = ['cola', 'coke', 'soft drink', 'carbonated', 'energy drink', 'sports drink', 'iced tea', 'ice tea',
               'lemonade', 'nimbu pani', 'aerated', 'soda', 'pepsi', 'sprite', 'fanta', 'mountain dew', 'thums up']
FRUIT_WORDS = ['mango', 'orange', 'apple', 'grape', 'mixed fruit', 'pomegranate', 'guava', 'litchi', 'lychee',
               'pineapple', 'cranberry']
UNSALTED = ['unsalted', 'no salt', 'plain', 'raw', 'natural', 'no added salt']
SALTED = ['salted', 'masala', 'peri', 'spicy', 'flavour', 'flavoured', 'chatpata', 'tangy', 'chilli', 'pudina']


def implausible(gid, name, drink, core, fat):
    lo, hi = PLAUSIBLE_KCAL[gid]
    if not (lo <= core['energy_kcal'] <= hi):
        return 'energy %.0f kcal is not plausible for %s (likely per serving or as prepared)' % (core['energy_kcal'], gid)
    if not drink and core['saturated_fat'] == 0 and ((fat is not None and fat >= 5) or
                                                     (fat is None and core['energy_kcal'] >= 400)):
        return 'saturated fat 0 is not plausible when the food has fat'
    if gid in ('nuts_seeds', 'breakfast_cereals') and fat and core['saturated_fat'] > 0.4 * fat \
            and not words(name, ['coconut']):
        return 'saturated fat is %.0f%% of fat, not plausible for nuts, seeds or cereal' % (100 * core['saturated_fat'] / fat)
    if gid == 'salty_snacks' and fat and fat >= 20 and core['saturated_fat'] < 0.12 * fat:
        return 'saturated fat unusually low for a fried snack; check the pack'
    if gid == 'breakfast_cereals' and core['sodium_mg'] > 1200 and not words(name, ['masala', 'savoury', 'savory',
                                                                                  'tomato', 'herb', 'veggie']):
        return 'sodium %.0f mg not plausible for a plain cereal' % core['sodium_mg']
    if gid in ('salty_snacks', 'instant_noodles') and core['sodium_mg'] <= 5 and not words(name, UNSALTED):
        return 'sodium 0 is not plausible for a salted snack or noodles'
    if gid == 'nuts_seeds' and core['sodium_mg'] <= 5 and words(name, SALTED):
        return 'sodium 0 is not plausible for salted or flavoured nuts'
    if gid == 'biscuits' and core['sodium_mg'] <= 5:
        return 'sodium 0 is not plausible for a biscuit'
    if gid in ('biscuits', 'chocolate') and core['energy_kcal'] >= 400 and core['saturated_fat'] < 1:
        return 'saturated fat under 1 g is not plausible for a biscuit or chocolate'
    if gid == 'biscuits' and core['sugars'] <= 2 and not words(name, ['sugar free', 'no added sugar', 'cracker',
                                                                     'crackers', 'khakhra', 'salted', 'jeera']):
        return 'sugar under 2 g is not plausible for a sweet biscuit'
    if gid == 'flavoured_milk' and words(name, ['toned milk', 'double toned', 'skimmed milk', 'slim milk', 'cow milk',
                                                'buffalo milk', 'full cream', 'a2 milk']) \
            and not words(name, ['soy', 'soya', 'almond', 'oat']) and not (3 <= core['sugars'] <= 6.5):
        return 'milk with %.1f g sugar is not plausible (lactose is about 4.7 g)' % core['sugars']
    if gid == 'soft_drinks' and core['sugars'] <= 0.5 and not words(name, ZERO_WORDS):
        return 'zero sugar but the name does not say zero, diet or soda'
    if gid == 'fruit_drinks' and words(name, FRUIT_WORDS) and not words(name, ['coconut', 'water']) \
            and core['sugars'] < 5:
        return 'sugar under 5 g is not plausible for a fruit juice'
    return None


def clean(hit, query_group, ungrouped_ok=False):
    n = hit.get('nutriments') or {}
    brand = hit.get('brands')
    brand = (brand[0] if isinstance(brand, list) and brand else brand) or ''
    name = (hit.get('product_name_en') or hit.get('product_name') or '').strip()
    if not name or not brand or len(name) < 3:
        return None, 'no name or brand'
    if hit.get('data_quality_errors_tags'):
        return None, 'flagged by Open Food Facts: ' + ','.join(hit['data_quality_errors_tags'][:2])
    kcal = n.get('energy-kcal_100g')
    if kcal is None and n.get('energy_100g') is not None:
        kcal = float(n['energy_100g']) / 4.184
    sodium = n.get('sodium_100g')
    sodium_mg = float(sodium) * 1000 if sodium is not None else (float(n['salt_100g']) * 400 if n.get('salt_100g') is not None else None)
    sat = n.get('saturated-fat_100g')
    if sat is None and n.get('fat_100g') is not None:
        try:
            if float(n['fat_100g']) == 0:
                sat = 0.0       # no fat declared means no saturated fat; nothing is being assumed
        except (TypeError, ValueError):
            pass
    core = dict(energy_kcal=kcal, sugars=n.get('sugars_100g'), saturated_fat=sat,
                protein=n.get('proteins_100g'), sodium_mg=sodium_mg)
    missing = [k for k, v in core.items() if v is None]
    if missing:
        return None, 'missing ' + ','.join(missing)
    try:
        core = {k: float(v) for k, v in core.items()}
    except (TypeError, ValueError):
        return None, 'non-numeric value'
    fat, carbs, fibre = n.get('fat_100g'), n.get('carbohydrates_100g'), n.get('fiber_100g')
    fat = float(fat) if fat is not None else None
    carbs = float(carbs) if carbs is not None else None
    fibre = float(fibre) if fibre is not None else None
    trans = n.get('trans-fat_100g')
    if not (0 <= core['energy_kcal'] <= 900):
        return None, 'energy out of range'
    if fat is not None and core['saturated_fat'] > fat + 0.05:
        return None, 'saturated fat above total fat'
    if carbs is not None and core['sugars'] > carbs + 0.5:
        return None, 'sugars above carbohydrate'
    if core['sodium_mg'] > 40000:
        return None, 'sodium out of range'
    if fat is not None and carbs is not None and core['energy_kcal'] > 20:
        est = 4 * carbs + 4 * core['protein'] + 9 * fat + 2 * (fibre or 0)
        if abs(est - core['energy_kcal']) / core['energy_kcal'] > 0.20:
            return None, 'energy disagrees with macros (%.0f vs %.0f kcal)' % (core['energy_kcal'], est)
    qty, unit = quantity(hit.get('quantity'))
    drink = unit == 'ml'
    # Placed by its own name, exactly as the app will place the product a shopper is looking at.
    # Filing a record under whatever search found it put frozen peas among ice creams.
    gid = group_for(name, drink) or (group_for(name, False) if unit == 'ml' else None)
    if gid is None and unit is None and group_for(name, True):
        gid, drink = group_for(name, True), True
    if gid is None and not ungrouped_ok:
        return None, 'name does not fit any substitution group'
    if gid is not None:     # an ungrouped product is kept only for the record (category, database), never as a swap
        g = next(x for x in GROUPS if x['id'] == gid)
        if g['id'] in ('ice_cream', 'oils_fats'):   # sold by volume, labelled and scored as foods
            drink = False
        elif (g['branch'] == 'drink') != drink:
            return None, 'pack unit does not fit a %s group' % g['branch']
        why = implausible(gid, name, drink, core, fat)
        if why and why.startswith('saturated fat 0 ') and declared_zero_settles(hit.get('code'), gid, name, drink, core, fibre):
            why = None
        if why:
            return None, why
    ingredients = (hit.get('ingredients_text_en') or hit.get('ingredients_text') or '').strip()
    allergens = [t.split(':', 1)[-1].replace('-', ' ') for t in (hit.get('allergens_tags') or [])]
    traces = [t.split(':', 1)[-1].replace('-', ' ') for t in (hit.get('traces_tags') or [])]
    labels = hit.get('labels_tags') or []
    veg = 'vegan' if 'en:vegan' in labels else 'vegetarian' if 'en:vegetarian' in labels else \
        'non-vegetarian' if 'en:non-vegetarian' in labels else None
    rec = dict(id='off:' + hit['code'], gtin=hit['code'], brand=brand.strip(), name=name, group=gid,
               pack=qty, unit=unit, beverage=drink,
               per100=dict(energy_kcal=round(core['energy_kcal'], 1), sugars_g=round(core['sugars'], 2),
                           saturated_fat_g=round(core['saturated_fat'], 2), sodium_mg=round(core['sodium_mg'], 1),
                           protein_g=round(core['protein'], 2),
                           fibre_g=round(fibre, 2) if fibre is not None else None,
                           total_fat_g=round(fat, 2) if fat is not None else None,
                           trans_fat_g=round(float(trans), 2) if trans is not None else None),
               ingredients=ingredients[:900], contains=allergens, may_contain=traces, veg=veg,
               status='draft', source='Open Food Facts (ODbL), volunteer-entered',
               source_url='https://world.openfoodfacts.org/product/' + hit['code'])
    band, score = grade(dict(rec['per100'], energy_kcal=rec['per100']['energy_kcal'], sugars=core['sugars'],
                             saturated_fat=core['saturated_fat'], sodium_mg=core['sodium_mg'],
                             protein=core['protein'], fibre=fibre, name=name), drink, ingredients.lower())
    # A zero-sugar soft drink that is not plain soda water is sweetened with something even when the
    # ingredient list was never entered. The app reads this flag, so it scores it as the drink it is.
    rec['nss'] = any(w in ingredients.lower() for w in SWEETENERS) or (
        gid == 'soft_drinks' and core['sugars'] <= 0.5 and core['energy_kcal'] <= 5
        and not words(name, ['soda', 'water', 'club soda', 'sparkling water']))
    if rec['nss'] and not any(w in ingredients.lower() for w in SWEETENERS):
        band, score = grade(dict(rec['per100'], energy_kcal=rec['per100']['energy_kcal'], sugars=core['sugars'],
                                 saturated_fat=core['saturated_fat'], sodium_mg=core['sodium_mg'],
                                 protein=core['protein'], fibre=fibre, name=name), drink, 'sucralose')
    rec['draft_band'] = band
    rec['draft_score'] = score
    rec['draft_letter'] = letter(score, drink)
    good, bad = ingredient_facts(ingredients, drink)
    rec['ingredient_good'] = good
    rec['ingredient_concerns'] = bad
    return rec, None


def declared_zero_settles(code, gid, name, drink, core, fibre):
    """A record's saturated fat of 0 is accepted when it is the pack's own declaration, checked by a person, and the colour
    is the same anywhere between 0 and the most the pack's own %RDA allows (the next whole percent of FSSAI's 22 g a
    day, per serving, as per 100 g). A 0 that only means 'not entered' stays rejected."""
    check = LABEL_CHECKS.get(code)
    if not check or not check.get('saturated_fat_declared_zero') or not check.get('serving_g')             or check.get('saturated_fat_rda_percent') is None:
        return False
    bound = (check['saturated_fat_rda_percent'] + 1) / 100 * FSSAI_LABEL_SATURATED_FAT_G * 100 / check['serving_g']
    values = dict(energy_kcal=core['energy_kcal'], sugars=core['sugars'], sodium_mg=core['sodium_mg'],
                  protein=core['protein'], fibre=fibre, name=name, group=gid)
    low, _ = grade(dict(values, saturated_fat=0.0), drink, '')
    high, _ = grade(dict(values, saturated_fat=bound), drink, '')
    return low == high


def fetch_ingredients(code):
    """The search service does not return ingredient lists; the product lookup does."""
    url = 'https://world.openfoodfacts.org/api/v2/product/%s.json?fields=ingredients_text_en,ingredients_text' % code
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                product = json.loads(r.read().decode('utf-8')).get('product') or {}
            return (product.get('ingredients_text_en') or product.get('ingredients_text') or '').strip()
        except Exception:
            time.sleep(2 * (attempt + 1))
    return ''


def regrade(rec):
    """Grade again once the ingredient list is known: a sweetener changes a drink's score."""
    p = rec['per100']
    ingredients = (rec.get('ingredients') or '').lower()
    rec['nss'] = rec['nss'] or any(w in ingredients for w in SWEETENERS)
    values = dict(energy_kcal=p['energy_kcal'], sugars=p['sugars_g'], saturated_fat=p['saturated_fat_g'],
                  sodium_mg=p['sodium_mg'], protein=p['protein_g'], fibre=p['fibre_g'], name=rec['name'],
                  total_fat=p.get('total_fat_g'), group=rec['group'])
    band, score = grade(values, rec['beverage'], 'sucralose' if rec['nss'] else ingredients)
    rec['draft_band'], rec['draft_score'] = band, score
    rec['draft_letter'] = values.get('_letter') or letter(score, rec['beverage'])
    rec['ingredient_good'], rec['ingredient_concerns'] = ingredient_facts(rec.get('ingredients') or '', rec['beverage'])
    if rec['nss'] and 'No added sugar or sweeteners' in rec['ingredient_good']:
        rec['ingredient_good'][rec['ingredient_good'].index('No added sugar or sweeteners')] = 'No added sugar'


def regrade_only(path):
    """Regrade the stored catalogue with the current method, offline; retire what no longer qualifies."""
    raw = Path(path).read_text(encoding='utf-8')
    data = json.loads(raw)
    stepping = {g['id'] for g in data['groups'] if g.get('allow_step_within_red')}
    kept, retired = [], []
    for rec in data['products']:
        regrade(rec)
        ok = rec['draft_band'] in ('A', 'B') or (rec['draft_band'] == 'C' and rec['draft_letter'] == 'D' and rec['group'] in stepping)
        (kept if ok else retired).append(rec)
    data['products'] = kept
    data['retired_by_nutri_score_2023'] = [dict(gtin=r.get('gtin'), brand=r.get('brand'), name=r.get('name'), group=r.get('group'),
                                                letter=r['draft_letter']) for r in retired]
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=1) + ('\n' if raw.endswith('\n') else ''), encoding='utf-8')
    print('regraded %d products with Nutri-Score 2023: kept %d, retired %d' % (len(kept) + len(retired), len(kept), len(retired)))
    for r in retired:
        print('  retired', r['draft_letter'], r.get('group'), '|', r.get('brand'), r.get('name'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out-app', default=str(ROOT / 'app/src/main/assets/catalogue/swaps_v1.json'))
    ap.add_argument('--out-review', default=str(ROOT.parents[1] / 'nutritionist'))
    ap.add_argument('--regrade-only', action='store_true', help='regrade the existing catalogue offline')
    args = ap.parse_args()
    if args.regrade_only:
        regrade_only(args.out_app)
        return
    kept, rejected, seen, figures = {}, [], set(), set()
    for g in GROUPS:
        print('group', g['id'])
        for q in g['queries']:
            hits = fetch(q)
            print('  %-70s %d' % (q[:70], len(hits)))
            for h in hits:
                code = h.get('code')
                if not code or code in seen:
                    continue
                seen.add(code)
                rec, why = clean(h, g['id'])
                if rec is None:
                    rejected.append(dict(gtin=code, brand=str(h.get('brands'))[:40],
                                         name=str(h.get('product_name'))[:80], searched_for=g['id'], reason=why))
                    continue
                key = (rec['brand'].lower(), re.sub(r'\W+', ' ', rec['name'].lower()).strip(), rec['pack'])
                same_figures = tuple(
                    rec['per100'][k] for k in ('energy_kcal', 'sugars_g', 'saturated_fat_g', 'sodium_mg', 'protein_g'))
                if key in kept or same_figures in figures:
                    continue
                kept[key] = rec
                figures.add(same_figures)
            time.sleep(1)
    records = list(kept.values())
    stepping = {g['id'] for g in GROUPS if g.get('allow_step_within_red')}

    def qualifies(r):
        return r['draft_band'] in ('A', 'B') or (r['draft_band'] == 'C' and r['draft_letter'] == 'D'
                                                   and r['group'] in stepping)
    candidates = [r for r in records if qualifies(r)]
    print('fetching ingredient lists for %d candidates' % len(candidates))
    for r in candidates:
        if not r['ingredients']:
            r['ingredients'] = fetch_ingredients(r['gtin'])[:900]
            time.sleep(0.7)
        regrade(r)
    dropped = [r for r in candidates if not qualifies(r)]
    for r in dropped:
        rejected.append(dict(gtin=r['gtin'], brand=r['brand'], name=r['name'], searched_for=r['group'],
                             reason='no longer qualifies once its ingredients were read (sweetener)'))
    candidates = [r for r in candidates if qualifies(r)]
    candidates.sort(key=lambda r: (r['group'], r['draft_band'], r['draft_score']))
    now = dt.datetime.now(dt.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
    groups_out = [{k: v for k, v in g.items() if k != 'queries'} for g in GROUPS]
    catalogue = dict(version='swaps-v1-draft', generated_at=now,
                     licence='Open Database License (ODbL) 1.0 - draft records derived from Open Food Facts',
                     attribution='Product data (c) Open Food Facts contributors, openfoodfacts.org, ODbL.',
                     note='DRAFT. Every product is unverified until a nutritionist checks it against the pack.',
                     groups=groups_out, products=candidates)
    out = Path(args.out_app)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(catalogue, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    review = Path(args.out_review)
    review.mkdir(parents=True, exist_ok=True)
    cols = ['group', 'draft_band', 'nutri_score_letter', 'brand', 'name', 'pack', 'unit', 'gtin', 'energy_kcal', 'sugars_g',
            'saturated_fat_g', 'sodium_mg', 'protein_g', 'fibre_g', 'total_fat_g', 'contains', 'may_contain', 'veg',
            'ingredient_highlights', 'ingredient_concerns', 'ingredients',
            'source_url', 'VERIFIED_FROM_PACK (Y/N)', 'CORRECTED_VALUES', 'APPROVE_AS_SWAP (Y/N)', 'OCCASIONS', 'NOTES']
    with open(review / 'swap_catalogue_review_v1.csv', 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.writer(f)
        w.writerow(cols)
        for r in candidates:
            p = r['per100']
            w.writerow([r['group'], r['draft_band'], r['draft_letter'], r['brand'], r['name'], r['pack'], r['unit'], r['gtin'],
                        p['energy_kcal'], p['sugars_g'], p['saturated_fat_g'], p['sodium_mg'], p['protein_g'],
                        p['fibre_g'], p['total_fat_g'], '; '.join(r['contains']), '; '.join(r['may_contain']),
                        r['veg'] or '', '; '.join(r['ingredient_good']), '; '.join(r['ingredient_concerns']),
                        r['ingredients'][:300], r['source_url'], '', '', '', ';'.join(
                            next(g['occasions'] for g in GROUPS if g['id'] == r['group'])), ''])
    with open(review / 'rejected_records_v1.csv', 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.DictWriter(f, fieldnames=['searched_for', 'gtin', 'brand', 'name', 'reason'])
        w.writeheader()
        for r in rejected:
            w.writerow({k: r[k] for k in ['searched_for', 'gtin', 'brand', 'name', 'reason']})
    by_group = {}
    for r in records:
        by_group.setdefault(r['group'], {'A': 0, 'B': 0, 'C': 0})[r['draft_band']] += 1
    for r in candidates:
        by_group[r['group']].setdefault('step', 0)
        if r['draft_band'] == 'C':
            by_group[r['group']]['step'] += 1
    print('\nclean records', len(records), 'candidates (A/B)', len(candidates), 'rejected', len(rejected))
    for gid, c in sorted(by_group.items()):
        print('  %-18s A %3d  B %3d  C %3d  (E->D steps kept %d)' % (gid, c['A'], c['B'], c['C'], c.get('step', 0)))


if __name__ == '__main__':
    main()
