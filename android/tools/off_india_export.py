"""Reads Open Food Facts' official full CSV export and keeps the products sold in India with complete nutrition
facts and ingredients, in the same shape as the search API returns (so off_india_catalogue.py reads either).

Why the export: the search API now serves only the first 1,000 results to anonymous callers ("Due to heavy abuse
from robots, we are unable to serve this page to unidentified users"). For bulk use Open Food Facts publishes this
daily export under the ODbL. It is downloaded once (resumable), read as a stream, and can be deleted afterwards.

Writes ../../catalogue_build/off_india/export_india.jsonl and export_india.json (counts).

Usage:  python tools/off_india_export.py [--delete-download]
"""
import argparse
import csv
import gzip
import io
import json
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT.parents[1] / 'catalogue_build' / 'off_india'
URL = 'https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz'
UA = 'BetterChoiceIndia-curation/1.2 (India catalogue for nutritionist review)'
DOWNLOAD = BUILD / 'export' / 'en.openfoodfacts.org.products.csv.gz'
TAGS = ('categories_tags', 'labels_tags', 'countries_tags', 'ingredients_analysis_tags', 'traces_tags', 'states_tags',
        'data_quality_errors_tags', 'food_groups_tags', 'popularity_tags')
TEXT = ('code', 'product_name', 'generic_name', 'brands', 'quantity', 'serving_size', 'ingredients_text', 'stores',
        'image_url', 'image_ingredients_url', 'image_nutrition_url')
NUMBERS = ('created_t', 'last_modified_t', 'last_updated_t', 'last_image_t', 'serving_quantity', 'product_quantity',
           'unique_scans_n')
# A product is kept when its label figures are on record, whether or not Open Food Facts marks the record
# complete: "nutrition facts completed" is also set when "no nutrition label" is ticked, and a record can carry
# every figure while a photo or field elsewhere is still missing. The catalogue's own checks decide the rest.
CORE = ('sugars_100g', 'saturated-fat_100g', 'proteins_100g')
ENERGY = ('energy-kcal_100g', 'energy-kj_100g', 'energy_100g')     # any one; the catalogue converts kJ


def download():
    DOWNLOAD.parent.mkdir(parents=True, exist_ok=True)
    part = DOWNLOAD.with_suffix('.part')
    if DOWNLOAD.exists():
        return
    have = part.stat().st_size if part.exists() else 0
    req = urllib.request.Request(URL, headers={'User-Agent': UA, **({'Range': 'bytes=%d-' % have} if have else {})})
    with urllib.request.urlopen(req, timeout=120) as r, open(part, 'ab' if have else 'wb') as out:
        total = int(r.headers.get('Content-Length') or 0) + have
        done, last = have, time.time()
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            out.write(chunk)
            done += len(chunk)
            if time.time() - last > 30:
                print('  downloaded %.0f of %.0f MB' % (done / 1e6, total / 1e6), flush=True)
                last = time.time()
    part.rename(DOWNLOAD)


def number(v):
    try:
        return float(v) if v not in ('', None) else None
    except ValueError:
        return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--delete-download', action='store_true', help='remove the 1.3 GB export once read')
    args = ap.parse_args()
    download()
    csv.field_size_limit(sys.maxsize if sys.maxsize < 2 ** 31 else 2 ** 31 - 1)
    stats = dict(rows=0, india=0, complete=0)
    out_path = BUILD / 'export_india.jsonl'
    with gzip.open(DOWNLOAD, 'rb') as gz, open(out_path, 'w', encoding='utf-8') as out:
        reader = csv.reader(io.TextIOWrapper(gz, encoding='utf-8', errors='replace', newline=''), delimiter='\t',
                            quoting=csv.QUOTE_NONE)
        header = next(reader)
        col = {name: i for i, name in enumerate(header)}
        nutrient_cols = [(name[:-5], i) for name, i in col.items() if name.endswith('_100g')]
        for row in reader:
            stats['rows'] += 1
            if len(row) != len(header):
                continue
            if 'en:india' not in row[col['countries_tags']].split(','):
                continue
            stats['india'] += 1
            if any(row[col[c]] == '' for c in CORE) or all(row[col[c]] == '' for c in ENERGY) \
                    or (row[col['sodium_100g']] == '' and row[col['salt_100g']] == ''):
                continue
            stats['complete'] += 1      # label figures on record (energy, sugars, saturated fat, protein, sodium or salt)
            p = {k: row[col[k]] or None for k in TEXT}
            p.update({k: [t for t in row[col[k]].split(',') if t] for k in TAGS})
            p.update({k: number(row[col[k]]) for k in NUMBERS})
            p['allergens_tags'] = [t for t in row[col['allergens']].split(',') if t]
            p['nutriments'] = {name + '_100g': v for name, i in nutrient_cols if (v := number(row[i])) is not None}
            for k in ('created_t', 'last_modified_t', 'last_updated_t', 'last_image_t'):
                p[k] = int(p[k]) if p[k] is not None else None
            out.write(json.dumps(p, ensure_ascii=False) + '\n')
            if stats['rows'] % 500000 == 0:
                print('  read %d rows, India %d, complete %d' % (stats['rows'], stats['india'], stats['complete']), flush=True)
    stats.update(source=URL, at=time.strftime('%Y-%m-%dT%H:%M:%S'), export_bytes=DOWNLOAD.stat().st_size)
    (BUILD / 'export_india.json').write_text(json.dumps(stats, indent=1), encoding='utf-8')
    print('done:', stats)
    if args.delete_download:
        DOWNLOAD.unlink()
        print('deleted', DOWNLOAD)


if __name__ == '__main__':
    main()
