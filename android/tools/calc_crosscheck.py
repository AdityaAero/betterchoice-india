"""Independent re-implementation of the personal-reference calculator, for cross-checking the app.

Written separately from NutritionCalculator.java / DailyNeeds.java. It shares only the constants, which
tools/evidence_check.py verifies against the source documents, and computes every persona with Python
Decimal. The output is a fixture: CalculatorCrossCheckTest builds each profile in the app and requires
the same numbers to within 0.01.

Since 3.5.1 it also works through product cases: caffeine against NIN 2024's tolerable limit (declared
amounts and NIN's per-cup ranges), lactose estimated from a dairy label against the 12 g most adults with
lactose intolerance tolerate, and FSSAI's 30% comparative rule with its "source of fibre" condition.

Usage:  python tools/calc_crosscheck.py    (writes security/evidence/calculator-crosscheck-v3.5.json)
"""
import json
import random
import sys
from decimal import Decimal as D, getcontext
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import evidence_check  # noqa: E402

getcontext().prec = 34
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'security/evidence/calculator-crosscheck-v3.5.json'
E = {e['id']: D(e['value']) for e in evidence_check.registry()}


def number(raw, low, high):
    try:
        v = float(raw)
    except (TypeError, ValueError):
        return None
    return v if low <= v <= high else None


def sex_of(gender):
    return {'Woman': 'female', 'Man': 'male'}.get(gender, 'average')


def basal(sex, age, kg):
    middle, older = E['bmr.age.band.middle'], E['bmr.age.band.older']
    band = '30' if age is None else ('18' if D(age) < middle else '30' if D(age) < older else '60')
    return E['bmr.%s.%s.a' % (sex, band)] * kg + E['bmr.%s.%s.b' % (sex, band)]


def activity_level(sex, activity):
    level = {'Moderately active': 'moderate', 'Heavy physical work': 'heavy'}.get(activity, 'sedentary')
    sedentary = E['icmr.pal.sedentary']
    if level == 'sedentary':
        return sedentary
    return sedentary * E['icmr.eer.%s.%s' % (sex, level)] / E['icmr.eer.%s.sedentary' % sex]


def maintenance(sex, age, kg, activity):
    if sex == 'average':
        return (maintenance('female', age, kg, activity) + maintenance('male', age, kg, activity)) / 2
    india = 1 - E['icmr.bmr.reduction.2010'] - E['icmr.bmr.reduction.2020']
    return basal(sex, age, kg) * india * activity_level(sex, activity)


def compute(a):
    """a: the profile answers by key, as the app stores them."""
    health = [c for c in a.get('health_flags', '').split(',') if c]
    age = number(a.get('age'), 18, 120)
    age = None if age is None else int(age)
    adult = True if not a.get('age') else age is not None
    stated = number(a.get('weight_kg'), 30, 300)
    before = number(a.get('pre_pregnancy_weight'), 30, 300)
    pregnant, feeding = 'pregnancy' in health, 'breastfeeding' in health
    kg = before if pregnant and before is not None else stated
    height = number(a.get('height_cm'), 100, 250)
    out = {}
    if not adult or kg is None:
        return dict(basis='GENERIC', energyKcal=float(E['fssai.label.energy']), totalFatLimitG=float(E['fssai.label.fat']),
                    saturatedFatLimitG=float(E['fssai.label.saturated.fat']), sugarLimitG=float(E['fssai.label.added.sugar']),
                    proteinG=None)
    kg = D(str(kg))
    sex = sex_of(a.get('gender'))
    bmi = band = None
    if height is not None:
        m = D(str(height)) / 100
        bmi = kg / (m * m)
        band = 'UNDERWEIGHT' if bmi < E['nin.bmi.underweight.below'] else 'NORMAL' if bmi <= E['nin.bmi.normal.max'] \
            else 'OVERWEIGHT' if bmi <= E['nin.bmi.overweight.max'] else 'OBESE'
    base = maintenance(sex, age, kg, a.get('activity', ''))
    stage = a.get('pregnancy_stage', '')
    extra = D(0)
    if pregnant and stage in ('t2', 't3'):
        extra = E['icmr.pregnancy.kcal'] + (E['nin.pregnancy.undernourished.kcal'] if band == 'UNDERWEIGHT' else 0)
    elif not pregnant and feeding and stage in ('bf0', 'bf7'):
        extra = E['icmr.lactation.kcal.0to6'] if stage == 'bf0' else E['icmr.lactation.kcal.7to12']
    energy = base + extra
    ref = kg   # ICMR's RDA is per kg of body weight; no weight adjustment for obesity has a source on file
    diet = a.get('diet_detail') or {'Vegetarian': 'Vegetarian', 'Non-vegetarian': 'Non-vegetarian / both', 'Vegan': 'Vegan'}.get(a.get('diet', ''), '')
    per_kg = E['icmr.protein.vegetarian'] if diet in ('Vegetarian', 'Jain', 'Vegan') else E['icmr.protein.rda']
    kidney = 'kidney' in health
    dialysis = kidney and a.get('kidney_stage') == 'dialysis'
    protein = muscle_low = muscle_high = None
    if not (kidney and not dialysis):
        add = D(0)
        if pregnant and stage == 't2':
            add = E['icmr.protein.pregnancy.t2.rda']
        elif pregnant and stage == 't3':
            add = E['icmr.protein.pregnancy.t3.rda']
        elif not pregnant and feeding and stage == 'bf0':
            add = E['icmr.protein.lactation.0to6.rda']
        elif not pregnant and feeding and stage == 'bf7':
            add = E['icmr.protein.lactation.7to12.rda']
        protein = per_kg * ref + add
        goals = [g for g in a.get('goals', '').split(',') if g] or ([{'Muscle gain': 'muscle'}.get(a.get('goal', ''))] if a.get('goal') else [])
        if 'muscle' in goals:
            muscle_low, muscle_high = E['issn.protein.muscle.min'] * ref, E['nin.protein.muscle.max'] * ref

    def share(sid, per_gram):
        return energy * E[sid] / E[per_gram]

    out.update(basis='PERSONAL', energyKcal=energy, maintenanceKcal=base, stateExtraKcal=extra,
               totalFatLimitG=share('nin.fat.max.share', 'fssai.kcal.per.g.fat'),
               saturatedFatLimitG=share('who.sfa.max.share', 'fssai.kcal.per.g.fat'),
               transFatLimitG=share('who.tfa.max.share', 'fssai.kcal.per.g.fat'),
               sugarLimitG=share('who.free.sugars.max.share', 'fssai.kcal.per.g.carbohydrate'),
               sugarBetterG=share('who.free.sugars.better.share', 'fssai.kcal.per.g.carbohydrate'),
               carbohydrateLowG=share('nin.carbohydrate.share.low', 'fssai.kcal.per.g.carbohydrate'),
               carbohydrateHighG=share('nin.carbohydrate.share.high', 'fssai.kcal.per.g.carbohydrate'),
               fibreG=E['who.fibre.min'], sodiumLimitMg=E['who.sodium.max'], weightKg=ref,
               proteinG=protein, muscleProteinLowG=muscle_low, muscleProteinHighG=muscle_high,
               bmi=bmi, bmiBand=band)
    return {k: (float(v) if isinstance(v, D) else v) for k, v in out.items()}


NAMED = [
    ('P1 healthy woman', dict(gender='Woman', age='28', weight_kg='55', height_cm='160', activity='Mostly sitting',
                              diet_detail='Vegetarian', goals='aware')),
    ('P2 man, diabetes and blood pressure, weight loss and sugar control',
     dict(gender='Man', age='35', weight_kg='85', height_cm='170', activity='Moderately active', diet_detail='Non-vegetarian / both',
          goals='loss,sugar', health_flags='diabetes,hypertension', diabetes_type='type2')),
    ('P3 pregnant, third trimester, lactose, peanut',
     dict(gender='Woman', age='30', weight_kg='58', height_cm='158', activity='Mostly sitting', diet_detail='Vegetarian',
          goals='aware', health_flags='peanut,lactose,pregnancy', pregnancy_stage='t3', trace_severity='strict')),
    ('P4 vegan lifter', dict(gender='Man', age='24', weight_kg='52', height_cm='175', activity='Heavy physical work',
                             diet_detail='Vegan', goals='muscle,gain')),
    ('P5 underweight, wants weight loss', dict(gender='Woman', age='45', weight_kg='50', height_cm='165', activity='Mostly sitting',
                                               diet_detail='Eggetarian', goals='loss')),
    ('P6 older man, kidney, halal, muscle', dict(gender='Man', age='62', weight_kg='70', height_cm='168', activity='Mostly sitting',
                                                 diet_detail='Non-vegetarian (halal)', goals='muscle', health_flags='kidney',
                                                 kidney_stage='no_dialysis')),
    ('P7 non-binary, coeliac and sesame', dict(gender='Non-binary', age='40', weight_kg='68', height_cm='170', activity='Moderately active',
                                               diet_detail='Eggetarian', goals='aware', health_flags='gluten,sesame')),
    ('P8 breastfeeding 0-6 months, Jain', dict(gender='Woman', age='32', weight_kg='60', height_cm='162', activity='Mostly sitting',
                                               diet_detail='Jain', goals='aware', health_flags='breastfeeding', pregnancy_stage='bf0')),
]


def generated(count, seed=35):
    rng = random.Random(seed)
    people = []
    for i in range(count):
        health = rng.choice(['', 'diabetes', 'hypertension', 'kidney', 'pregnancy', 'breastfeeding', 'cholesterol', 'lactose',
                             'diabetes,hypertension', 'kidney,hypertension'])
        a = dict(gender=rng.choice(['Woman', 'Man', 'Non-binary', 'Prefer not to say', '']),
                 age=rng.choice(['19', '29', '30', '45', '59', '60', '72', '']),
                 weight_kg=rng.choice(['42', '55', '65', '78.5', '96', '130']),
                 height_cm=rng.choice(['150', '163', '171', '185', '']),
                 activity=rng.choice(['Mostly sitting', 'Moderately active', 'Heavy physical work', '']),
                 diet_detail=rng.choice(['Vegetarian', 'Eggetarian', 'Jain', 'Non-vegetarian / both', 'Non-vegetarian (halal)', 'Vegan', '']),
                 goals=rng.choice(['', 'loss', 'gain', 'muscle', 'sugar', 'loss,muscle', 'gain,muscle', 'loss,sugar', 'aware']),
                 health_flags=health)
        if 'pregnancy' in health:
            a['pregnancy_stage'] = rng.choice(['t1', 't2', 't3', ''])
            a['pre_pregnancy_weight'] = rng.choice(['', '52', '61'])
        if 'breastfeeding' in health:
            a['pregnancy_stage'] = rng.choice(['bf0', 'bf7', ''])
        if 'kidney' in health:
            a['kidney_stage'] = rng.choice(['no_dialysis', 'dialysis', 'unsure'])
        people.append(('G%02d' % (i + 1), a))
    return people


def caffeine_cases():
    """Caffeine: % of NIN's limit for declared amounts, and cups to the limit for NIN's per-cup ranges."""
    limit = E['nin.caffeine.limit']
    cases = []
    for name, per100, pack in [('energy drink 32 mg/100 ml, 250 ml can', '32', '250'), ('energy drink 30 mg/100 ml, 500 ml can', '30', '500'),
                               ('cola 9.6 mg/100 ml, 1250 ml bottle', '9.6', '1250')]:
        mg = D(per100) * D(pack) / 100
        cases.append(dict(name=name, per100=float(per100), pack=float(pack), packMg=float(mg), percentOfLimit=float(mg * 100 / limit)))
    cups = {}
    for kind in ('instant', 'brewed', 'tea'):
        low, high = E['nin.caffeine.%s.low' % kind], E['nin.caffeine.%s.high' % kind]
        cups[kind] = dict(fewest=float(limit / high), most=float(limit / low))
    cola = E['who.anc.caffeine.soft.drink']
    return dict(declared=cases, cupsToLimit=cups, colaPercentOfLimit=float(cola * 100 / limit))


def lactose_cases():
    """Lactose: sugars less declared added sugar, per serving, and the amount holding 12 g."""
    tolerated = E['lactose.tolerated.low']
    out = []
    for name, sugars, added, serving in [('toned milk', '4.8', None, None), ('flavoured milk', '10.2', '5.4', '180'),
                                         ('curd', '3.2', None, '100'), ('cheese slice', '1.5', None, '20'), ('no sugars', '0', None, None)]:
        lactose = max(D(sugars) - (D(added) if added else D(0)), D(0))
        row = dict(name=name, sugars=float(sugars), added=None if added is None else float(added),
                   serving=None if serving is None else float(serving), lactosePer100=float(lactose))
        if serving:
            row['lactosePerServing'] = float(lactose * D(serving) / 100)
        row['amountFor12g'] = None if lactose == 0 else float(tolerated * 100 / lactose)
        out.append(row)
    return out


def fibre_cases():
    """FSSAI: a swap 'for its fibre' needs 30% more and must be a source of fibre (3 g/100 g, 1.5 g/100 ml or 1.5 g/100 kcal)."""
    m = E['fssai.claims.comparative.min']
    out = []
    for before, after, kcal, liquid in [('2.0', '2.6', '450', False), ('2.0', '3.0', '450', False), ('0.2', '0.4', '450', False),
                                        ('0', '1.0', '60', False), ('0', '1.0', '400', False), ('1.0', '1.6', '60', True),
                                        ('2.3', '2.9', '380', False)]:
        b, a, k = D(before), D(after), D(kcal)
        source = a >= E['fssai.claims.fibre.source.%s' % ('liquid' if liquid else 'solid')] \
            or a * 100 / k >= E['fssai.claims.fibre.source.per.100kcal']
        out.append(dict(before=float(b), after=float(a), kcal=float(k), liquid=liquid,
                        improves=bool(a >= b * (1 + m) and a > b and source)))
    return out


def main():
    rows = []
    for name, answers in NAMED + generated(40):
        rows.append(dict(name=name, answers=answers, expected=compute(answers)))
    products = dict(caffeine=caffeine_cases(), lactose=lactose_cases(), fibre=fibre_cases())
    OUT.write_text(json.dumps(dict(version='v3.5.1', evidence=len(E), personas=rows, products=products), indent=1, ensure_ascii=False),
                   encoding='utf-8')
    print('wrote', OUT.relative_to(ROOT), len(rows), 'personas')
    for row in rows[:8]:
        e = row['expected']
        print('%-62s energy %7.1f  fat %5.1f  sat %4.1f  protein %s' % (row['name'][:62], e['energyKcal'], e['totalFatLimitG'],
              e['saturatedFatLimitG'], 'hidden' if e['proteinG'] is None else '%.1f' % e['proteinG']))


if __name__ == '__main__':
    main()
