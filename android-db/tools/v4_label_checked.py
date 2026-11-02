"""Adds the packs in catalogue/label_checks.json to version 4's catalogue, from their open-data records.

Each check is a pack whose label a person has seen (on a shop's screen or in hand) and found equal to its Open Food Facts
record. The record is cleaned, graded and placed exactly as tools/off_india_catalogue.py does for every product (the
same functions), so a checked pack enters only if the builder's rules accept it; the one difference is the evidence the
check supplies (curate_swaps.declared_zero_settles). Products already in the catalogue are left as they are; their
'shop_names' still become database aliases (tools/v4_supabase_seed.py).

Reads ../../catalogue_build/off_india/export_india.jsonl (the open-data export the catalogue was built from); writes only
app/src/main/assets/catalogue/swaps_v1.json of version 4.

Usage:  python tools/v4_label_checked.py [--dry-run]
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import curate_swaps as cs  # noqa: E402
import off_india_catalogue as oic  # noqa: E402


def label_hit(check):
    """A label draft written as an open-data record, so the builder's own cleaning and rules apply to it unchanged."""
    import datetime
    p = check['per100']
    seen = int(datetime.datetime.strptime(check['checked'][:10], '%Y-%m-%d').replace(tzinfo=datetime.timezone.utc).timestamp())
    # Only what the label states; a missing figure stays missing and the builder's own rules decide (cs.clean).
    nutriments = {}
    if p.get('sodium_mg') is not None:
        nutriments['sodium_100g'] = p['sodium_mg'] / 1000
    for key, field in (('energy_kcal', 'energy-kcal_100g'), ('sugars', 'sugars_100g'), ('fat', 'fat_100g'),
                       ('proteins', 'proteins_100g'), ('carbohydrates', 'carbohydrates_100g'),
                       ('saturated_fat', 'saturated-fat_100g'), ('fibre', 'fiber_100g'), ('added_sugars', 'added-sugars_100g'),
                       ('trans_fat', 'trans-fat_100g')):
        if p.get(key) is not None:
            nutriments[field] = p[key]
    return {'code': check['gtin'], 'product_name': check['product_name'], 'brands': check['brand'], 'quantity': check['quantity'],
            'nutriments': nutriments, 'ingredients_text': check.get('ingredients', ''), 'created_t': seen, 'last_image_t': seen,
            'categories_tags': [], 'labels_tags': []}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dry-run', action='store_true')
    args = ap.parse_args()
    oic.install_groups()
    wanted = set(cs.LABEL_CHECKS)
    hits = {}
    with open(oic.BUILD / 'export_india.jsonl', encoding='utf-8') as f:
        for line in f:
            p = json.loads(line)
            if p.get('code') in wanted:
                hits[p['code']] = p
    data = json.loads(oic.APP.read_text(encoding='utf-8'))
    present = {p['gtin'] for p in data['products']}
    added = []
    for gtin, check in cs.LABEL_CHECKS.items():
        if gtin in present:
            print('already listed:', gtin, check['pack'])
            continue
        # A label draft is the pack on the shelf now: it was chosen because no usable record exists (an export record
        # older than 2025, or one that differs from the pack), so its figures come first.
        hit = label_hit(check) if check.get('label_draft') else hits.get(gtin)
        if hit is None and check.get('open_data_lookup'):
            # A record fetched by barcode because the bulk export lacks it (same source, same rules).
            hit = json.loads((oic.ROOT.parents[1] / check['open_data_lookup']).read_text(encoding='utf-8'))
            hit.setdefault('code', gtin)
        if hit is None:
            print('no open-data record and no label draft:', gtin, check['pack'])
            continue
        if not oic.recent(hit):
            print('label older than 2025 (founder\'s rule):', gtin)
            continue
        oic.CURRENT['categories'], oic.CURRENT['by'] = hit.get('categories_tags') or [], None
        rec, why = cs.clean(hit, None)
        if rec is None:
            print('rejected by the builder\'s rules:', gtin, why)
            continue
        rec['ingredients'] = (hit.get('ingredients_text_en') or hit.get('ingredients_text') or '').strip()[:1500]
        rec['grouped_by'] = oic.CURRENT['by'] or 'name'
        oic.enrich(rec, hit)
        cs.regrade(rec)
        rec['off_group'] = oic.category_group(rec)
        outliers = oic.composition_outliers([p for p in data['products'] if p.get('per100')] + [rec])
        if gtin in outliers:
            print('left out as another kind of food by composition:', gtin, outliers[gtin])
            continue
        rec['label_check'] = check['checked']
        if check.get('label_draft'):
            # Founder-approved (2026-10-16): figures from the pack's own label photo, kept as evidence.
            rec['id'] = 'label:' + gtin
            rec['source'] = 'Pack label (photo seen on Blinkit, %s), entered by BetterChoice; draft until a nutritionist checks it' \
                % check['checked'][:10]
            rec['source_url'] = check['evidence']
            rec['label_date'] = check['checked'][:10]
        added.append(rec)
        print('adding %s %s | %s | group %s | draft %s/%s' % (gtin, rec.get('brand'), rec.get('name'), rec.get('group'),
                                                              rec.get('draft_band'), rec.get('draft_letter')))
    if added and not args.dry_run:
        data['products'] += [oic.for_app(r) for r in added]
        # Written whole, then swapped in, so nothing reading the catalogue meanwhile sees half a file.
        tmp = oic.APP.with_name(oic.APP.name + '.tmp')
        tmp.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
        tmp.replace(oic.APP)
    print('%d added%s; catalogue now %d products' % (len(added), ' (dry run)' if args.dry_run else '',
                                                    len(data['products']) + (len(added) if args.dry_run else 0)))


if __name__ == '__main__':
    main()
