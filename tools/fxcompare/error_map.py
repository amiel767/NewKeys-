"""Locate where a scene differs from the reference PNG, cell by cell.

Once `color_audit.py` reports no missing colour, the residual error is no longer a
forgotten block: it is geometry, gradient extent, opacity or draw order. This tool
prints the mean absolute difference per cell of a grid over the 825x343 scene, so
the worst regions can be inspected directly.

Run from the repository root:  python3 tools/fxcompare/error_map.py [effect ...]
"""
import sys

import numpy as np
from PIL import Image

NUM = {"space": "00", "echo": "01", "prism-tone": "02", "iron-grip": "03", "solar-ceiling": "04",
       "piano": "05", "organ": "06", "sax": "07", "strings": "08", "guitar": "09", "amp": "10",
       "bass": "11", "brass": "12", "voices": "13", "synth": "14"}
BOX = (12.8 / 851.0, 39.4 / 393.0, 837.0 / 851.0, 381.6 / 393.0)
W, H = 825, 343


def reference(effect):
    im = Image.open(f"app/assets/fx-screens-spec/references/ref-{NUM[effect]}-{effect}.png").convert("RGB")
    w, h = im.size
    crop = im.crop((round(BOX[0] * w), round(BOX[1] * h), round(BOX[2] * w), round(BOX[3] * h)))
    return np.array(crop.resize((W, H), Image.LANCZOS)).astype(float)


def capture(effect):
    im = Image.open(f"app/src/test/screenshots/fx_window_{effect}.png").convert("RGB")
    return np.array(im.resize((W, H), Image.LANCZOS)).astype(float)


def main():
    effects = sys.argv[1:] or ["space", "prism-tone", "strings"]
    cols, rows = 15, 7
    cw, ch = W // cols, H // rows
    for effect in effects:
        d = np.abs(reference(effect) - capture(effect)).mean(axis=2)[: rows * ch, : cols * cw]
        grid = d.reshape(rows, ch, cols, cw).mean(axis=(1, 3))
        print(f"=== {effect} : total {d.mean():.2f} — erreur par cellule "
              f"({cols}x{rows}, x{cw} y{ch}) ===")
        print("      " + "".join(f"{c * cw:>6}" for c in range(cols)))
        for r in range(rows):
            print(f"y{r * ch:>4} " + "".join(f"{grid[r, c]:>6.1f}" for c in range(cols)))
        worst = np.dstack(np.unravel_index(np.argsort(grid.ravel())[::-1][:6], grid.shape))[0]
        print("  pires cellules : " + ", ".join(
            f"(x{c * cw}..{c * cw + cw}, y{r * ch}..{r * ch + ch}) {grid[r, c]:.1f}" for r, c in worst))
        print()


if __name__ == "__main__":
    main()
