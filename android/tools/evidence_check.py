"""Checks every number in Evidence.java against the document it cites.

For each registry entry: the cited file must exist, the verbatim quote must appear in it, and the
entry's value must appear in that quote (as written, as a percentage, or in g where the value is mg).
Comparison ignores case, whitespace and dash style, because PDF text extraction varies in those.

Usage:  python tools/evidence_check.py            (exit code 1 on any failure)
"""
import html
import json
import re
import sys
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / 'app/src/main/java/com/betterchoice/android/Evidence.java'
DASHES = dict.fromkeys(map(ord, '–—−‐‑�'), '-')


def norm(text):
    return re.sub(r'\s+', '', text.translate(DASHES).lower())


def document_text(path):
    if path.suffix == '.pdf':
        import pypdf
        return ' '.join((p.extract_text() or '') for p in pypdf.PdfReader(str(path)).pages)
    raw = path.read_text(encoding='utf-8', errors='ignore')
    if path.suffix == '.html':
        raw = re.sub(r'<style.*?</style>|<script.*?</script>', '', raw, flags=re.S)
        return html.unescape(re.sub(r'<[^>]+>', ' ', raw))
    if path.suffix == '.json':
        data = json.loads(raw)
        results = data.get('resultList', {}).get('result', [])
        if results:
            return re.sub(r'<[^>]+>', '', results[0].get('abstractText', ''))
    return raw


def registry():
    src = REGISTRY.read_text(encoding='utf-8')
    files = dict(re.findall(r'static final String (\w+) = "([^"]+)";', src))
    call = re.compile(r'\b(v|review)\(\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]*)",\s*((?:\w+|"[^"]*")(?:\s*\+\s*"[^"]*")?),'
                      r'\s*"([^"]*)",\s*"([^"]*)"', re.S)

    def path(expr):   # FAO, "security/evidence/x.html" or JECFA + "62.html"
        return ''.join(files[p] if re.fullmatch(r'\w+', p) else p.strip('"') for p in re.split(r'\s*\+\s*', expr.strip()))
    entries = [dict(kind=k, id=i, value=val, unit=u, file=path(f), locator=loc, quote=q)
               for k, i, val, u, f, loc, q in call.findall(src)]
    declared = len(re.findall(r'^\s*(?:v|review)\(\s*"', src, re.M))
    if declared != len(entries):   # an entry the pattern cannot read must fail loudly, never be skipped
        raise SystemExit('registry has %d entries but only %d were parsed' % (declared, len(entries)))
    return entries


UNITS = 'zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen ' \
        'seventeen eighteen nineteen'.split()
TENS = 'twenty thirty forty fifty sixty seventy eighty ninety'.split()


def words(n):
    """An integer below 100 as a regulation spells it: 30 -> thirty, 25 -> twenty-five."""
    if n < 20:
        return UNITS[n]
    return TENS[n // 10 - 2] + ('' if n % 10 == 0 else '-' + UNITS[n % 10])


def value_in_quote(value, quote):
    q = norm(quote)
    d = Decimal(value)
    forms = {value, format(d.normalize(), 'f')}
    forms.add(format((d * 100).normalize(), 'f') + '%')       # 0.30 -> 30%
    forms.add(format((d / 1000).normalize(), 'f') + 'g')      # 2000 mg -> 2 g
    percent = d * 100
    if percent == percent.to_integral_value() and 0 <= percent < 100:
        forms.add(words(int(percent)) + ' per cent')          # 0.30 -> thirty per cent (FSSAI's wording)
    return any(norm(f) in q for f in forms)


def main():
    entries = registry()
    cache, failures = {}, []
    for e in entries:
        path = ROOT / e['file']
        if not path.exists():
            failures.append((e['id'], 'missing file ' + e['file']))
            continue
        if path not in cache:
            cache[path] = norm(document_text(path))
        if norm(e['quote']) not in cache[path]:
            failures.append((e['id'], 'quote not found in ' + e['file'] + ': ' + e['quote']))
        elif e['unit'] != 'rule' and not value_in_quote(e['value'], e['quote']):   # a rule is words, not a number
            failures.append((e['id'], 'value %s not in its quote: %s' % (e['value'], e['quote'])))
    print('evidence entries: %d, documents: %d, failures: %d' % (len(entries), len(cache), len(failures)))
    for i, why in failures:
        print('  FAIL', i, '-', why)
    return 1 if failures or not entries else 0


if __name__ == '__main__':
    sys.exit(main())
