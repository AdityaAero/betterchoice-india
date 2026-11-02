"""Which categories can hold green and yellow products at all: every Open Food Facts India record on this machine
(catalogue_build/off_india/export_india.jsonl), cleaned and placed exactly as the catalogue builder does, graded with
the builder's Nutri-Score 2023 grader (curate_swaps.grade, the draft approximation of the app's engine; the app's own
engine decides before anything is added), and counted per category, with and without the founder's 2025-label rule.
Products already listed are counted apart.

Usage: python tools/v4_colour_supply.py
"""
import collections
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
import curate_swaps as cs  # noqa: E402
import off_india_catalogue as oic  # noqa: E402


def main():
    oic.install_groups()
    listed = {p['gtin'] for p in json.loads(oic.APP.read_text(encoding='utf-8'))['products']}
    supply = collections.defaultdict(collections.Counter)
    with open(oic.BUILD / 'export_india.jsonl', encoding='utf-8') as f:
        for line in f:
            hit = json.loads(line)
            if not hit.get('code') or hit['code'] in listed:
                continue
            oic.CURRENT['categories'], oic.CURRENT['by'] = hit.get('categories_tags') or [], None
            rec, why = cs.clean(hit, None)
            if rec is None or not rec.get('group'):
                continue
            colour = {'A': 'green', 'B': 'yellow', 'C': 'red'}[rec['draft_band']]
            era = '2025+' if oic.recent(hit) else 'older'
            supply[rec['group']][colour + ' ' + era] += 1
    print('Unlisted open-data products that pass the builder, by draft colour (2025+ label / older label):')
    print('%-18s %13s %13s' % ('category', 'green', 'yellow'))
    for g in [x['id'] for x in json.loads(oic.APP.read_text(encoding='utf-8'))['groups']]:
        c = supply[g]
        print('%-18s %5d / %5d   %5d / %5d' % (g, c['green 2025+'], c['green older'], c['yellow 2025+'], c['yellow older']))


if __name__ == '__main__':
    main()
