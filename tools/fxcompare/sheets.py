"""Build one comparison sheet per effect: REFERENCE | CAPTURE | DIFF.

The diff panel paints the differing pixels (channel delta > 24) in red over a
greyed copy of the reference, so the eye lands on what is wrong instead of
having to read a percentage. Sheets are written to tools/fxcompare/sheets/.
"""
import pathlib
import sys

import numpy as np
from PIL import Image, ImageChops, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "sheets"
BOX = (12.8 / 851.0, 39.4 / 393.0, 837.0 / 851.0, 381.6 / 393.0)
DELTA = 24
TAGS = {
    0: "space", 1: "echo", 2: "prism-tone", 3: "iron-grip", 4: "solar-ceiling",
    5: "piano", 6: "organ", 7: "sax", 8: "strings", 9: "guitar", 10: "amp",
    11: "bass", 12: "brass", 13: "voices", 14: "synth",
}


def window(ref: Image.Image) -> Image.Image:
    w, h = ref.size
    return ref.crop((round(BOX[0] * w), round(BOX[1] * h), round(BOX[2] * w), round(BOX[3] * h)))


def sheet(effect: int, tag: str) -> float:
    ref_path = next((HERE.parent.parent / "app/assets/fx-screens-spec/references").glob(f"ref-*-{tag}.png"))
    ref = window(Image.open(ref_path).convert("RGB"))
    cap = Image.open(HERE.parent.parent / f"app/src/test/screenshots/fx_window_{tag}.png").convert("RGB")
    cap = cap.resize(ref.size, Image.LANCZOS)

    d = np.asarray(ImageChops.difference(cap, ref), dtype=np.int16)
    bad = (d > DELTA).any(axis=2)
    pct = 100.0 * bad.sum() / bad.size

    grey = np.asarray(ref).astype(np.float32)
    grey = (grey.mean(axis=2, keepdims=True) * 0.45 + 128 * 0.55).astype(np.uint8).repeat(3, axis=2)
    grey[bad] = (255, 40, 40)
    diff = Image.fromarray(grey)

    gap, bar = 10, 22
    w, h = ref.size
    out = Image.new("RGB", (w, h * 3 + gap * 2 + bar * 3), (18, 20, 26))
    dr = ImageDraw.Draw(out)
    y = 0
    for title, img in ((f"REFERENCE  {tag}", ref), (f"CAPTURE  {tag}", cap), (f"DIFF  {tag}   {pct:.2f} %", diff)):
        dr.text((6, y + 5), title, fill=(230, 234, 243))
        out.paste(img, (0, y + bar))
        y += h + bar + gap
    OUT.mkdir(exist_ok=True)
    out.save(OUT / f"{effect:02d}-{tag}.png")
    return pct


if __name__ == "__main__":
    wanted = [int(a) for a in sys.argv[1:]] or list(TAGS)
    res = {}
    for i in wanted:
        res[TAGS[i]] = sheet(i, TAGS[i])
    for t, v in sorted(res.items(), key=lambda x: -x[1]):
        print(f"{t:14s} {v:6.2f} %")
    print(f"{'MOYENNE':14s} {sum(res.values()) / len(res):6.2f} %")
