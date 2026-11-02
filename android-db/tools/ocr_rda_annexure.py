"""Machine transcript of the scanned RDA annexures in FSSAI's RDA direction of 3 Feb 2022.

The annexure pages are images, so their numbers cannot be quoted from text. This script OCRs them
(rapidocr-onnxruntime; deterministic) and writes security/evidence/reference-rda-annexure-ocr.txt,
which Evidence.java quotes and tools/evidence_check.py checks. Nothing in the transcript is typed by
hand: a value that OCR does not read is not quoted.

Inputs (all in security/evidence/):
  rda-annexure/page4.jpg, page5.jpg, page6.jpg  the annexure pages of reference-rda.pdf
  reference-rda-annexure-ia-minerals.png         Annexure IA crop, protein to thiamine
  reference-rda-annexure-ia-vitamins.png         Annexure IA crop, riboflavin to vitamin D

Three passes: whole pages read row by row; the two crops turned upright and read row by row (one row
per population group); and each crop cut into overlapping strips, one per nutrient, read in order
(sedentary man first, then sedentary woman, pregnancy, lactation, children).

Run with the OCR environment, from the project root:
  python tools/ocr_rda_annexure.py
"""
from pathlib import Path

from PIL import Image, ImageOps
from rapidocr_onnxruntime import RapidOCR

EV = Path(__file__).resolve().parents[1] / 'security' / 'evidence'
OUT = EV / 'reference-rda-annexure-ocr.txt'
TMP = EV / 'rda-annexure' / '_ocr.png'
ocr = RapidOCR()


def read(im):
    im.convert('RGB').save(TMP)
    result, _ = ocr(str(TMP))
    return [(sum(p[1] for p in b) / 4, sum(p[0] for p in b) / 4, t) for b, t, s in (result or [])]


def rows(boxes, gap):
    out, current, last_y = [], [], None
    for y, x, t in sorted(boxes):
        if last_y is not None and abs(y - last_y) > gap:
            out.append(current)
            current = []
        current.append((x, t))
        last_y = y
    if current:
        out.append(current)
    return [' | '.join(t for x, t in sorted(r)) for r in out]


def pages():
    lines = []
    for name, rot in (('page4.jpg', 90), ('page5.jpg', 0), ('page6.jpg', 90)):
        im = Image.open(EV / 'rda-annexure' / name).convert('RGB')
        if rot:
            im = im.rotate(rot, expand=True)
        im = im.resize((im.width * 2, im.height * 2))
        lines.append('=== page %s (rotated %d, x2), rows ===' % (name, rot))
        lines += rows(read(im), 18)
    return lines


def crop_rows(name):
    im = Image.open(EV / name).convert('L').rotate(-90, expand=True)
    im = ImageOps.autocontrast(im.resize((int(im.width * 2.5), int(im.height * 2.5)), Image.LANCZOS))
    return ['=== %s (upright, x2.5), one row per population group ===' % name] + rows(read(im), 14 * 2.5)


def crop_strips(name, step, height):
    base = Image.open(EV / name).convert('L')
    lines = ['=== %s, strips of %d px every %d px, one nutrient per strip, values in group order ===' % (name, height, step)]
    for y0 in range(0, base.height - height + 1, step):
        im = base.crop((0, y0, base.width, y0 + height)).rotate(-90, expand=True)
        im = ImageOps.autocontrast(im.resize((im.width * 3, im.height * 3), Image.LANCZOS))
        values = [t for y, x, t in sorted(read(im))]
        if values:
            lines.append('strip y%d-%d: %s' % (y0, y0 + height, ' | '.join(values)))
    return lines


def main():
    lines = pages()
    lines += crop_rows('reference-rda-annexure-ia-minerals.png')
    lines += crop_rows('reference-rda-annexure-ia-vitamins.png')
    lines += crop_strips('reference-rda-annexure-ia-minerals.png', 25, 65)
    lines += crop_strips('reference-rda-annexure-ia-vitamins.png', 20, 60)
    TMP.unlink(missing_ok=True)
    OUT.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
