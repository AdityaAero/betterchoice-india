"""How many green and yellow products each category of the list holds, by the BetterChoice app's own algorithm.

The colour is the app engine's: DbExportHarness grades every listed product with CatalogueDb.grade -> GradeResult /
NutriGrade (Nutri-Score 2023 in three bands: A/B green, C yellow, D/E red, with the app's FSSAI and ICMR-NIN rules), from
the product's nutrition record, and writes it to ../export/products_export.jsonl. Nothing here comes from a shop.

Usage: python tools/v4_colour_gaps.py [GREEN_TARGET YELLOW_TARGET]   (default 15 10)
"""
import collections
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.stdout.reconfigure(encoding='utf-8', errors='replace')


def main():
    green_target = int(sys.argv[1]) if len(sys.argv) > 1 else 15
    yellow_target = int(sys.argv[2]) if len(sys.argv) > 2 else 10
    catalogue = json.loads((ROOT / 'app/src/main/assets/catalogue/swaps_v1.json').read_text(encoding='utf-8'))
    names = {g['id']: g['name'] for g in catalogue['groups']}
    group_of = {p['gtin']: p['group'] for p in catalogue['products']}
    count = collections.defaultdict(collections.Counter)
    for line in open(ROOT.parent / 'export' / 'products_export.jsonl', encoding='utf-8'):
        row = json.loads(line)
        count[group_of[row['gtin']]][row['report'].get('colour', 'not rated')] += 1
    print('%-18s %-36s %5s %6s %4s %9s   need: green yellow' % ('category', 'name', 'green', 'yellow', 'red', 'not rated'))
    need_g = need_y = 0
    for gid, name in names.items():
        c = count[gid]
        g, y = max(0, green_target - c['green']), max(0, yellow_target - c['yellow'])
        need_g, need_y = need_g + g, need_y + y
        print('%-18s %-36s %5d %6d %4d %9d   %4d %6d' % (gid, name[:36], c['green'], c['yellow'], c['red'], c['not rated'], g, y))
    print('still needed across %d categories: %d green, %d yellow' % (len(names), need_g, need_y))


if __name__ == '__main__':
    main()
