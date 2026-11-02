"""Writes the dietitian review sheet: the judgement calls in BetterChoice's recommendation rules.

Every number in the sheet is read from the evidence registry (Evidence.java, via evidence_check), the
calculator cross-check fixture or the app's own rule lists; every quoted alternative is checked against
its document. So the sheet cannot drift from what the app does. Re-run it after any rule change.

Usage:  python tools/dietitian_sheet.py   (writes ../../nutritionist/RULES_FOR_DIETITIAN_REVIEW.md)
"""
import json
import re
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import evidence_check as ec  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT.parents[1] / 'nutritionist' / 'RULES_FOR_DIETITIAN_REVIEW.md'
JAVA = ROOT / 'app/src/main/java/com/betterchoice/android'
ENTRIES = {e['id']: e for e in ec.registry()}
_docs = {}


def v(eid):
    """A registry value as printed, e.g. 0.30 -> '0.3', 1.0 -> '1'."""
    d = Decimal(ENTRIES[eid]['value']).normalize()
    return format(d, 'f')


def r1(x):
    """One decimal, rounded half up as the app shows it."""
    return str(Decimal(x).quantize(Decimal('0.1'), rounding=ROUND_HALF_UP))


def pct(eid):
    return format((Decimal(ENTRIES[eid]['value']) * 100).normalize(), 'f') + '%'


def src(eid):
    e = ENTRIES[eid]
    return '%s, %s' % (Path(e['file']).name, e['locator'])


def quote(eid):
    return '"%s"' % ENTRIES[eid]['quote']


def checked(file, text):
    """A quote from a document that the app does not use as a value, verified like a registry quote."""
    path = ROOT / file
    if path not in _docs:
        _docs[path] = ec.norm(ec.document_text(path))
    if ec.norm(text) not in _docs[path]:
        raise SystemExit('quote not found in %s: %s' % (file, text))
    return '"%s"' % text


def java_array(cls, name):
    src_text = (JAVA / (cls + '.java')).read_text(encoding='utf-8')
    m = re.search(r'%s\s*=\s*\{([^}]*)\}' % name, src_text)
    return re.findall(r'"([^"]+)"', m.group(1))


def persona(prefix):
    data = json.loads((ROOT / 'security/evidence/calculator-crosscheck-v3.5.json').read_text(encoding='utf-8'))
    return next(p for p in data['personas'] if p['name'].startswith(prefix))


def audit_effect():
    base = ROOT.parents[1] / 'audit'
    old, new = base / 'zepto-3.5.0/_app_results.jsonl', base / 'zepto-3.5.1/_app_results.jsonl'
    if not (old.exists() and new.exists()):
        return None
    load = lambda p: [json.loads(line) for line in p.read_text(encoding='utf-8').splitlines() if line.strip()]
    a, b = load(old), load(new)
    rated = sum(1 for r in b if r.get('band'))
    return rated, sum(r.get('swap_count', 0) for r in a), sum(r.get('swap_count', 0) for r in b)


def main():
    p2 = persona('P2')
    kg = Decimal(p2['answers']['weight_kg'])
    m = Decimal(p2['answers']['height_cm']) / 100
    adjusted_kg = Decimal(v('nin.bmi.normal.max')) * m * m
    rda = Decimal(v('icmr.protein.rda'))
    effect = audit_effect()
    small = Decimal('0.2')   # an illustrative baseline, not a reference value
    smaller = (small * (1 - Decimal(ENTRIES['fssai.claims.comparative.min']['value']))).normalize()
    phosphates = ', '.join(java_array('ConditionRules', 'PHOSPHATE_CODES'))
    review = [e for e in ENTRIES.values() if e['kind'] == 'review']

    old_rule = checked('security/evidence/fssai-claims-2018-compendium-2022.pdf',
                       'at least twenty-five per cent. in the energy value or nutrient content and a minimum absolute difference')
    issn_full = checked('security/evidence/issn-protein-2017.json', '1.4-2.0 g protein/kg body weight/day')
    nin_muscle = quote('nin.protein.muscle.max')
    kdigo_scope = quote('kdigo.phosphate.scope')
    efsa_note = 'EFSA\'s 2015 caffeine opinion and 2010 lactose opinion could not be downloaded (efsa.europa.eu refused every request), so neither is used.'

    lines = []
    w = lines.append
    w('# BetterChoice India: rules for a dietitian\'s review')
    w('')
    w('Prepared %s from the app\'s evidence registry (%d entries, every quote checked against its document by '
      '`tools/evidence_check.py`). Regenerate with `python tools/dietitian_sheet.py`.' % ('2026-10-01', len(ENTRIES)))
    w('')
    w('**What the app does.** BetterChoice reads a packaged food\'s label on Indian quick-commerce apps, rates it A/B/C on '
      'nutrients (Nutri-Score 2023, in three bands), and suggests up to three swaps. A shopper\'s profile (diet, goals, '
      'allergies, conditions, pregnancy or breastfeeding, body size) changes what is left out, what a swap must not '
      'worsen, and the personal lines on the card. It never prescribes a diet or says a food suits a condition.')
    w('')
    w('**What we ask.** Every number below comes from a cited guideline or regulation. What we cannot settle ourselves is '
      'how those sources are *applied*. For each item, please tick one box and add any comment. Nothing here has been '
      'reviewed by a registered dietitian yet; until it is, the card\'s "How we calculated this" says the judgement calls '
      'await a dietitian\'s review.')
    w('')
    w('| # | Rule | App does now | Decision needed |')
    w('|---|---|---|---|')
    w('| 1 | What counts as a better swap | %s difference (FSSAI comparative claim) | Add an absolute minimum? |' % pct('fssai.claims.comparative.min'))
    w('| 2 | Protein reference in obesity | RDA on stated weight | Use an adjusted weight? |')
    w('| 3 | Vegetarian protein | %s g/kg for vegetarian, Jain, vegan | Keep, or %s g/kg? |' % (v('icmr.protein.vegetarian'), v('icmr.protein.rda')))
    w('| 4 | Muscle-gain protein range | %s-%s g/kg | Keep the narrower range? |' % (v('issn.protein.muscle.min'), v('nin.protein.muscle.max')))
    w('| 5 | Phosphate additives in kidney disease | Left out of swaps, "Not for you" | Veto or caution? Which stages? |')
    w('| 6 | Kidney: protein not estimated | Hidden unless on dialysis | Agree? |')
    w('| 7 | Caffeine | Amount vs %s mg/day; pregnancy note | A lower pregnancy figure? |' % v('nin.caffeine.limit'))
    w('| 8 | Lactose | Upper estimate vs %s g at a time | Agree with the method? |' % v('lactose.tolerated.low'))
    w('| 9 | Jain and halal ingredient lists | Word lists below | Corrections? |')
    w('| 10 | Micronutrient references | FSSAI 2022 annexure | Annexure or ICMR brief note? |')
    w('')

    def decision():
        w('')
        w('Decision: [ ] keep as is   [ ] change (say how below)   Comment: ________________________________')
        w('')

    w('## 1. What counts as a better swap')
    w('')
    w('**Now:** a swap chosen for a goal (less energy, more protein, less sugar, more protein per calorie) must differ from '
      'the original by at least %s on that nutrient, for the same portion. In awareness mode (no goal) it must be in a better '
      'band and cut the original\'s worst nutrient by %s. A swap offered for its fibre must also be a "source of fibre": '
      'at least %s g per 100 g, %s g per 100 ml, or %s g per 100 kcal.' % (
          pct('fssai.claims.comparative.min'), pct('fssai.claims.comparative.min'), v('fssai.claims.fibre.source.solid'),
          v('fssai.claims.fibre.source.liquid'), v('fssai.claims.fibre.source.per.100kcal')))
    w('')
    w('**Source:** FSSAI Advertising and Claims Regulations 2018, regulation 5(4)(a) as substituted (operationalised '
      '26 Oct 2021, compliance from 1 Jul 2022): %s (%s). Schedule I item 14 for "source of fibre".' % (
          quote('fssai.claims.comparative.min'), src('fssai.claims.comparative.min')))
    w('')
    w('**Why it needs you:** the regulation governs claims on labels, and we borrow it as the bar for "meaningfully better". '
      'The 2018 text also required a minimum absolute difference: %s, a condition the 2021 substitution dropped. Without it, '
      'a small baseline can pass (%s g to %s g sugar is %s less). Codex\'s earlier 25%% figure had no source.' % (
          old_rule, small, smaller, pct('fssai.claims.comparative.min')))
    if effect:
        w('')
        w('Effect of moving from 25%% to %s, replaying %d rated product pages recorded on Zepto: generic swaps %d -> %d.'
          % (pct('fssai.claims.comparative.min'), effect[0], effect[1], effect[2]))
    decision()

    w('## 2. Protein reference weight in obesity')
    w('')
    w('**Now:** protein = ICMR RDA x stated body weight (pre-pregnancy weight during pregnancy), the same for every BMI. '
      'Example, persona P2 (man, %s kg, %s cm, BMI %s): %s x %s = %s g a day.' % (
          p2['answers']['weight_kg'], p2['answers']['height_cm'], r1(str(p2['expected']['bmi'])), v('icmr.protein.rda'), kg, r1(rda * kg)))
    w('')
    w('**Alternative:** an adjusted weight, e.g. the weight at BMI %s (the top of NIN 2024\'s normal range) for the '
      'person\'s height: %s kg, giving %s g. Version 3.5.0 did this; it was removed because no source on file states it.'
      % (v('nin.bmi.normal.max'), r1(adjusted_kg), r1(rda * adjusted_kg)))
    decision()

    w('## 3. Vegetarian protein')
    w('')
    w('**Now:** %s g/kg for Vegetarian, Jain and Vegan diets; %s g/kg otherwise. Source: ICMR-NIN 2020 brief note, %s: %s.'
      % (v('icmr.protein.vegetarian'), v('icmr.protein.rda'), ENTRIES['icmr.protein.vegetarian']['locator'], quote('icmr.protein.vegetarian')))
    w('')
    w('**Why it needs you:** ICMR\'s 2020 RDA of %s g/kg assumes a cereal-legume-milk ratio of 3:1:2.5; Table 2b keeps the '
      '2010 figure for vegetarians. Eggetarian diets get %s g/kg.' % (v('icmr.protein.rda'), v('icmr.protein.rda')))
    decision()

    w('## 4. Muscle-gain protein range')
    w('')
    w('**Now:** %s-%s g/kg a day, shown only with a muscle-gain goal. Lower bound: ISSN 2017, %s for most exercising '
      'individuals. Upper bound: NIN 2024, %s.' % (v('issn.protein.muscle.min'), v('nin.protein.muscle.max'), issn_full, nin_muscle))
    w('')
    w('**Why it needs you:** the app shows only where the two sources agree. ISSN\'s full range goes higher.')
    decision()

    w('## 5. Phosphate additives in kidney disease')
    w('')
    w('**Now:** for anyone who records a kidney condition (any stage, on dialysis or not), a product declaring INS %s shows '
      '"Not for you" and is left out of swaps.' % phosphates)
    w('')
    w('**Source:** KDIGO 2017 CKD-MBD update, recommendation 4.1.8: %s (2D), and %s (Not Graded).' % (kdigo_scope, quote('kdigo.phosphate.additives')))
    w('')
    w('**Why it needs you:** KDIGO addresses stages G3a-G5D and the treatment of hyperphosphataemia; the form does not ask '
      'stage beyond "on dialysis". Options: keep the veto; make it a caution on the product (still left out of swaps); '
      'apply it only on dialysis. Please also check the code list.')
    decision()

    w('## 6. Kidney disease: protein not estimated')
    w('')
    w('**Now:** for a kidney condition not on dialysis, no protein figure is shown ("depends on your kidney function") and '
      'swaps may not have more protein. On dialysis, protein is estimated as usual and not constrained. Potassium salt '
      'substitutes are left out (%s).' % src('who.lsss.excludes.kidney'))
    decision()

    w('## 7. Caffeine')
    w('')
    w('**Now:** the card states the caffeine in the product: the declared amount, else NIN 2024\'s per-cup ranges '
      '(%s-%s mg brewed coffee, %s-%s mg instant, %s-%s mg tea, per %s ml cup), else WHO\'s "%s" for colas. Each is set '
      'against NIN 2024\'s tolerable limit of %s mg a day. In pregnancy the card adds WHO\'s antenatal recommendation A.10 '
      '(%s) and NIN\'s advice to minimise caffeine. Caffeine is never a veto unless the label itself says the product is '
      'not for pregnant or lactating women.' % (
          v('nin.caffeine.brewed.low'), v('nin.caffeine.brewed.high'), v('nin.caffeine.instant.low'), v('nin.caffeine.instant.high'),
          v('nin.caffeine.tea.low'), v('nin.caffeine.tea.high'), v('nin.caffeine.cup.ml'), ENTRIES['who.anc.caffeine.soft.drink']['quote'],
          v('nin.caffeine.limit'), quote('who.anc.caffeine.pregnancy')))
    w('')
    w('**Why it needs you:** %s Should pregnancy or breastfeeding use a lower daily figure, or should energy drinks be '
      'left out in pregnancy even when the label carries no warning?' % efsa_note)
    decision()

    w('## 8. Lactose')
    w('')
    w('**Now:** for a shopper with lactose intolerance (and no milk allergy), dairy products show "up to X g lactose": the '
      'label\'s sugars less any declared added sugar (milk\'s sugar is lactose: %s). It is compared with %s g, the lower end of '
      '%s (%s), and the card adds NIN\'s advice: %s. A "lactose-free" claim means at most %s g per 100 g (%s).' % (
          quote('nin.lactose.in.milk'), v('lactose.tolerated.low'), quote('lactose.tolerated.low'), src('lactose.tolerated.low'),
          quote('nin.lactose.advice'), v('fssai.claims.lactose.free'), src('fssai.claims.lactose.free')))
    w('')
    w('**Why it needs you:** the estimate is an upper bound (fruit sugars and fermentation lower the true figure), and the '
      'tolerated amount is per sitting, not per day.')
    decision()

    w('## 9. Jain and halal ingredient lists')
    w('')
    w('Practice varies; the app says so on the card, and for halal only a halal mark confirms it.')
    w('')
    w('- Jain, left out: %s' % ', '.join(java_array('DietRules', 'JAIN_AVOID')))
    w('- Halal, left out: %s' % ', '.join(java_array('DietRules', 'NOT_HALAL')))
    w('- Halal, caution (source decides): %s' % ', '.join(java_array('DietRules', 'HALAL_SOURCE_NOT_STATED')))
    w('- Emulsifiers whose source is rarely stated (caution for vegetarian and halal): INS %s'
      % ', '.join(java_array('DietRules', 'SOURCE_NOT_STATED')))
    decision()

    w('## 10. Micronutrient references (not yet shown to shoppers)')
    w('')
    w('The app\'s micronutrient table follows FSSAI\'s RDA direction of 3 Feb 2022, read from its scanned annexures by OCR '
      '(`security/evidence/reference-rda-annexure-ocr.txt`; the page images are in `security/evidence/rda-annexure/`). '
      'Corrected on 1 Oct 2026: biotin %s ug (was 40), copper %s mg (was 1.7), iodine %s ug (was 140), magnesium %s/%s mg '
      '(was 440/370), vitamin B12 %s ug (was 2.2), fibre %s g (WHO; was 30/25 with no source). The ICMR-NIN 2020 brief note '
      'disagrees with the annexure on:' % (
          v('fssai.rda.biotin'), v('fssai.rda.copper'), v('fssai.rda.iodine.male'), v('fssai.rda.magnesium.male'),
          v('fssai.rda.magnesium.female'), v('fssai.rda.b12.male'), v('who.fibre.min')))
    w('')
    for e in review:
        w('- `%s` = %s %s (%s, %s): "%s"' % (e['id'], e['value'], e['unit'], Path(e['file']).name, e['locator'], e['quote']))
    w('')
    w('**Why it needs you:** which source should the app follow, and please confirm the OCR-read values against the scans.')
    decision()

    w('## Settled by a source (no decision needed)')
    w('')
    w('These were shown as "provisional" in version 3.5.0 and now rest on a guideline or regulation:')
    w('')
    for eid, what in [('fssai.claims.comparative.min', 'swap threshold'), ('icmr.protein.vegetarian', 'vegetarian protein'),
                      ('issn.protein.muscle.min', 'muscle-gain lower bound'), ('kdigo.phosphate.additives', 'kidney phosphate additives')]:
        w('- %s: `%s`, %s' % (what, eid, src(eid)))
    w('')
    w('Reviewer: ______________________  Registration no.: __________  Date: __________  Signature: __________')
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print('wrote', OUT, '(%d lines)' % len(lines))


if __name__ == '__main__':
    main()
