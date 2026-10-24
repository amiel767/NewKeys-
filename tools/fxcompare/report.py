"""Build the session report: contact sheet of every capture + FX error table.

Writes `tools/fxcompare/report.png` (the whole window, reference beside capture
for the 15 effects, sorted by error) and prints the table. The reference crop and
the threshold are the same as compare_window.py, so the numbers match it.
"""
import pathlib
import sys

import numpy as np
from PIL import Image, ImageChops, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
SHOTS = ROOT / "app/src/test/screenshots"
REFS = ROOT / "app/assets/fx-screens-spec/references"
OUT = HERE / "report.png"

BOX = (12.8 / 851.0, 39.4 / 393.0, 837.0 / 851.0, 381.6 / 393.0)
DELTA = 24
THRESHOLD = 1.5
TAGS = {
    0: "space", 1: "echo", 2: "prism-tone", 3: "iron-grip", 4: "solar-ceiling",
    5: "piano", 6: "organ", 7: "sax", 8: "strings", 9: "guitar", 10: "amp",
    11: "bass", 12: "brass", 13: "voices", 14: "synth",
}


def window(ref):
    w, h = ref.size
    return ref.crop((round(BOX[0] * w), round(BOX[1] * h), round(BOX[2] * w), round(BOX[3] * h)))


def error(tag):
    ref = window(Image.open(next(REFS.glob(f"ref-*-{tag}.png"))).convert("RGB"))
    cap = Image.open(SHOTS / f"fx_window_{tag}.png").convert("RGB").resize(ref.size, Image.LANCZOS)
    d = np.asarray(ImageChops.difference(cap, ref), dtype=np.int16)
    pct = 100.0 * (d > DELTA).any(axis=2).mean()
    return ref, cap, pct


def main():
    rows = []
    for eid, tag in TAGS.items():
        try:
            ref, cap, pct = error(tag)
            rows.append((pct, eid, tag, ref, cap))
        except FileNotFoundError:
            continue
    rows.sort(key=lambda r: r[0])

    cols, cw, ch = 2, 470, 196
    head, label = 34, 22
    W = cols * cw + 12
    H = head + len(rows) * (ch + label) + 12
    sheet = Image.new("RGB", (W, H), (18, 21, 28))
    dr = ImageDraw.Draw(sheet)
    dr.text((10, 10), "Aether FX — REFERENCE (gauche) vs CAPTURE (droite), trie par ecart",
            fill=(200, 210, 225))
    y = head
    for i, (pct, eid, tag, ref, cap) in enumerate(rows):
        col = i % cols
        x = 6 + col * cw
        half = (cw - 8) // 2
        rh = int(ch * half / (cw / 2)) if False else ch
        r2 = ref.resize((half, rh), Image.LANCZOS)
        c2 = cap.resize((half, rh), Image.LANCZOS)
        sheet.paste(r2, (x, y))
        sheet.paste(c2, (x + half + 4, y))
        colr = (110, 220, 140) if pct <= THRESHOLD else (240, 150, 90)
        dr.text((x + 2, y + rh + 2), f"{eid:2d} {tag:<14} {pct:5.2f} %", fill=colr)
        y += rh + label
    sheet.save(OUT)
    print(f"ecrit {OUT}  ({W}x{H})")
    for pct, eid, tag, _, _ in rows:
        print(f"  {eid:2d} {tag:<14} {pct:5.2f} %")
    mean = sum(r[0] for r in rows) / len(rows)
    print(f"  moyenne        {mean:5.2f} %  ({len(rows)} effets)")


if __name__ == "__main__":
    sys.exit(main())
