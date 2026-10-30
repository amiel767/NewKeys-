"""Build a contact sheet of the Sunday Keys FX reference screenshots.

The `*-presets.png` variants are the same effect with its preset browser open;
for layout analysis the base shots matter most, so they are placed first.
"""
import glob
import os
import sys

from PIL import Image, ImageDraw

SRC = os.environ.get("SK_SRC", "/workspace/dl/sk/refs/references")
OUT = sys.argv[1] if len(sys.argv) > 1 else "/workspace/dl/sk/sheet_sk.png"
GLOB = os.environ.get("SK_GLOB", "*.png")

base = sorted(f for f in glob.glob(os.path.join(SRC, GLOB)) if not f.endswith("-presets.png"))
files = base + sorted(glob.glob(os.path.join(SRC, "*-presets.png")))

COLS = 5
THUMB_W = 380
PAD = 10
LABEL_H = 26

cells = []
for f in files:
    try:
        im = Image.open(f)
        im.load()
    except OSError:
        print("skipped (truncated):", os.path.basename(f), file=sys.stderr)
        continue
    im = im.convert("RGB")
    r = THUMB_W / im.width
    im = im.resize((THUMB_W, max(1, int(im.height * r))), Image.LANCZOS)
    cells.append((os.path.basename(f), im))

rows = (len(cells) + COLS - 1) // COLS
row_h = max(im.height for _, im in cells) + LABEL_H
sheet = Image.new("RGB", (COLS * (THUMB_W + PAD) + PAD, rows * (row_h + PAD) + PAD), (14, 18, 26))
d = ImageDraw.Draw(sheet)

for i, (name, im) in enumerate(cells):
    cx = PAD + (i % COLS) * (THUMB_W + PAD)
    cy = PAD + (i // COLS) * (row_h + PAD)
    sheet.paste(im, (cx, cy + LABEL_H))
    d.text((cx + 4, cy + 6), name.replace(".png", ""), fill=(200, 214, 240))

sheet.save(OUT, optimize=True)
print(OUT, sheet.size, os.path.getsize(OUT) // 1024, "KB")
