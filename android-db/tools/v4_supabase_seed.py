"""Seeds version 4's catalogue tables (supabase/migrations/20261015120000_v4_catalogue_vector.sql).

Inputs:
  app/src/main/assets/catalogue/swaps_v1.json      the 973 products and 31 categories, as the app reads them
  ../export/products_export.jsonl                  each product's report and better candidates, from the app's own
                                                   engine (DbExportHarness, env BC_DB_EXPORT_DIR)
Writes supabase/seed/v4/:
  v4_catalogue_001.sql ...   chunks small enough for the Supabase SQL editor
  v4_catalogue_all.sql       everything in one file, for psql or the CLI
The name key and the name vector are computed by the database itself (generated columns), so the phone, the seed and
the lookup can never disagree on them.

Usage:  python tools/v4_supabase_seed.py
"""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = ROOT / 'app/src/main/assets/catalogue/swaps_v1.json'
EXPORT = ROOT.parent / 'export' / 'products_export.jsonl'
CHECKS = ROOT / 'catalogue' / 'label_checks.json'       # shop names checked against a listed pack: database aliases
OUT = ROOT / 'supabase' / 'seed' / 'v4'
CHUNK = 150
TAG = '$bcv4$'


def display_name(p):
    """As SwapCatalogue.Product.displayName(): the brand in front only when the name does not already say it."""
    brand, name = p.get('brand') or '', p['name']
    if not brand:
        return name
    first = re.split(r'[^\w]+', brand.lower(), maxsplit=1)[0]
    return name if first and first in name.lower() else brand + ' ' + name


def lit(value):
    if value is None:
        return 'null'
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, separators=(',', ':'))
    assert TAG not in text
    return TAG + text + TAG


def build():
    """The database's contents, as rows: shared by the SQL seed below and by tools/v4_supabase_load.py, so both load the
    same thing."""
    catalogue = json.loads(CATALOGUE.read_text(encoding='utf-8'))
    export = {}
    for line in open(EXPORT, encoding='utf-8'):
        row = json.loads(line)
        export[row['gtin']] = row
    products = catalogue['products']
    assert len(export) == len(products), (len(export), len(products))
    listed = {p['gtin'] for p in products}
    checks = json.loads(CHECKS.read_text(encoding='utf-8'))['checks'] if CHECKS.exists() else []
    aliases = sorted({(name, c['gtin']) for c in checks if c['gtin'] in listed for name in c.get('shop_names', [])})
    digest = hashlib.sha256(json.dumps([catalogue['groups'], products, sorted(export.items()), aliases], sort_keys=True,
                                       ensure_ascii=False).encode('utf-8')).hexdigest()[:10]
    version = 'v4-%s-%d-%s' % (catalogue.get('generated_at', 'unknown'), len(products), digest)
    groups = [dict(id=g['id'], ord=order, doc={k: v for k, v in g.items() if k != 'allow_step_within_red'})
              for order, g in enumerate(catalogue['groups'])]
    rows = [dict(gtin=p['gtin'], id=p['id'], group_id=p['group'], display_name=display_name(p), brand=p.get('brand'),
                 status='verified' if p.get('status') == 'verified' else 'draft', doc=p, report=export[p['gtin']]['report'],
                 alternatives=export[p['gtin']]['alternatives']) for p in products]
    return dict(version=version, groups=groups, products=rows, aliases=[dict(name=n, gtin=g) for n, g in aliases])


def main():
    data = build()
    version, aliases = data['version'], [(a['name'], a['gtin']) for a in data['aliases']]
    catalogue = dict(groups=data['groups'])
    products = data['products']

    statements = ['-- BetterChoice version 4 catalogue seed, %s. Open Food Facts data (c) Open Food Facts contributors, ODbL.' % version,
                  'begin;',
                  'delete from catalogue_v4.aliases;',
                  'delete from catalogue_v4.products;',
                  'delete from catalogue_v4.groups;',
                  'delete from catalogue_v4.meta;']
    for g in catalogue['groups']:
        statements.append('insert into catalogue_v4.groups (id, ord, doc) values (%s, %d, %s::jsonb);' % (lit(g['id']), g['ord'], lit(g['doc'])))
    rows = []
    for r in products:
        rows.append('(%s, %s, %s, %s, %s, %s, %s::jsonb, %s::jsonb, %s::text[])' % (
            lit(r['gtin']), lit(r['id']), lit(r['group_id']), lit(r['display_name']), lit(r['brand']),
            lit(r['status']), lit(r['doc']), lit(r['report']), lit('{' + ','.join(r['alternatives']) + '}')))
    for i in range(0, len(rows), CHUNK):
        statements.append('insert into catalogue_v4.products (gtin, id, group_id, display_name, brand, status, doc, report, '
                          'alternatives) values\n' + ',\n'.join(rows[i:i + CHUNK]) + ';')
    if aliases:   # the database normalises each name with the same function the lookup uses
        statements.append('insert into catalogue_v4.aliases (alias_key, gtin) values\n' + ',\n'.join(
            '(catalogue_v4.norm(%s), %s)' % (lit(name), lit(gtin)) for name, gtin in aliases) + '\non conflict (alias_key) do nothing;')
    statements.append('insert into catalogue_v4.meta (version, products) values (%s, %d);' % (lit(version), len(products)))
    statements.append('commit;')

    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob('v4_catalogue_*.sql'):
        old.unlink()
    (OUT / 'v4_catalogue_all.sql').write_text('\n'.join(statements) + '\n', encoding='utf-8')
    # The editor runs each file as one transaction: groups and the clearing first, products in chunks, meta last.
    head = statements[:6 + len(catalogue['groups'])] + ['commit;']
    parts = [head]
    product_statements = [s for s in statements if s.startswith('insert into catalogue_v4.products')]
    for s in product_statements:
        parts.append(['begin;', s, 'commit;'])
    parts.append(['begin;'] + statements[len(head) - 1 + len(product_statements):-1] + ['commit;'])
    for n, part in enumerate(parts, 1):
        (OUT / ('v4_catalogue_%03d.sql' % n)).write_text('\n'.join(part) + '\n', encoding='utf-8')
    size = (OUT / 'v4_catalogue_all.sql').stat().st_size
    print('version %s: %d groups, %d products, %d aliases, %d files, all-in-one %.1f MB' % (
        version, len(catalogue['groups']), len(products), len(aliases), len(parts), size / 1e6))


if __name__ == '__main__':
    main()
