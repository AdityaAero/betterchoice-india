"""Applies the nutritionist's review sheet to the app's swap catalogue.

For each row of nutritionist/swap_catalogue_review_v1.csv:
  * APPROVE_AS_SWAP = N                      -> the product is removed from the catalogue.
  * VERIFIED_FROM_PACK = Y and APPROVE = Y   -> status becomes "verified"; CORRECTED_VALUES are applied
                                              ("sugars_g=3.2; sodium_mg=120"); OCCASIONS replace the
                                              group default when given.
  * anything else                            -> left as a draft, untouched.

Only verified products are ever shown in release builds. Drafts stay visible, labelled, in test
builds only. Run after every review round, then rebuild the app.

Usage:  python tools/apply_review.py [--sheet PATH] [--catalogue PATH] [--reviewer NAME]
"""
import argparse
import csv
import datetime as dt
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIELDS = {'energy_kcal', 'sugars_g', 'saturated_fat_g', 'sodium_mg', 'protein_g', 'fibre_g', 'total_fat_g', 'trans_fat_g'}


def yes(value):
    return str(value or '').strip().lower() in ('y', 'yes', 'true', '1')


def no(value):
    return str(value or '').strip().lower() in ('n', 'no', 'false', '0')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--sheet', default=str(ROOT.parents[1] / 'nutritionist' / 'swap_catalogue_review_v1.csv'))
    ap.add_argument('--catalogue', default=str(ROOT / 'app/src/main/assets/catalogue/swaps_v1.json'))
    ap.add_argument('--reviewer', default='BetterChoice nutritionist')
    args = ap.parse_args()
    catalogue = json.loads(Path(args.catalogue).read_text(encoding='utf-8'))
    by_gtin = {p['gtin']: p for p in catalogue['products']}
    verified = removed = corrected = 0
    problems = []
    groups = {g['id'] for g in catalogue['groups']}
    added = 0
    with open(args.sheet, newline='', encoding='utf-8-sig') as f:
        for row in csv.DictReader(f):
            product = by_gtin.get(row.get('gtin', '').strip())
            if product is None:
                # A new row typed from a pack: added as verified when complete and approved.
                if not (yes(row.get('VERIFIED_FROM_PACK (Y/N)')) and yes(row.get('APPROVE_AS_SWAP (Y/N)'))):
                    continue
                try:
                    per100 = {k: float(row[k]) for k in ('energy_kcal', 'sugars_g', 'saturated_fat_g', 'sodium_mg', 'protein_g')}
                    for k in ('fibre_g', 'total_fat_g'):
                        per100[k] = float(row[k]) if row.get(k) else None
                except (KeyError, ValueError):
                    problems.append('new row %r: energy, sugars, saturated fat, sodium and protein are all required' % row.get('name'))
                    continue
                if row.get('group') not in groups or not row.get('brand') or not row.get('name'):
                    problems.append('new row %r: needs a known group, a brand and a name' % row.get('name'))
                    continue
                code = row.get('gtin', '').strip() or 'pack-%d' % (len(catalogue['products']) + 1)
                unit = (row.get('unit') or 'g').strip()
                catalogue['products'].append(dict(
                    id='pack:' + code, gtin=code, brand=row['brand'].strip(), name=row['name'].strip(), group=row['group'],
                    pack=float(row['pack']) if row.get('pack') else None, unit=unit, beverage=unit == 'ml',
                    per100=per100, ingredients='', contains=[x.strip() for x in (row.get('contains') or '').split(';') if x.strip()],
                    may_contain=[x.strip() for x in (row.get('may_contain') or '').split(';') if x.strip()],
                    veg=row.get('veg') or None, nss=False, status='verified',
                    source='Entered from the pack by ' + args.reviewer, source_url=None,
                    reviewed_at=dt.date.today().isoformat()))
                added += 1
                continue
            if no(row.get('APPROVE_AS_SWAP (Y/N)')):
                catalogue['products'].remove(product)
                removed += 1
                continue
            if not (yes(row.get('VERIFIED_FROM_PACK (Y/N)')) and yes(row.get('APPROVE_AS_SWAP (Y/N)'))):
                continue
            for part in (row.get('CORRECTED_VALUES') or '').split(';'):
                if '=' not in part:
                    continue
                key, value = (x.strip() for x in part.split('=', 1))
                if key not in FIELDS:
                    problems.append('%s: unknown field %r' % (product['gtin'], key))
                    continue
                try:
                    product['per100'][key] = float(value)
                    corrected += 1
                except ValueError:
                    problems.append('%s: %s is not a number' % (product['gtin'], value))
            occasions = [o.strip() for o in (row.get('OCCASIONS') or '').split(';') if o.strip()]
            if occasions:
                product['occasions'] = occasions
            product['status'] = 'verified'
            product['source'] = 'Checked against the pack by ' + args.reviewer
            product['reviewed_at'] = dt.date.today().isoformat()
            if row.get('NOTES'):
                product['review_notes'] = row['NOTES'][:300]
            verified += 1
    Path(args.catalogue).write_text(json.dumps(catalogue, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    print('verified %d, added %d, removed %d, values corrected %d' % (verified, added, removed, corrected))
    for p in problems:
        print('  ! ' + p)


if __name__ == '__main__':
    main()
