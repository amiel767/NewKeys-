"""Diff a Compose FX capture against its reference PNG, aligned.

The reference PNGs in `fx-screens-spec` are the window full-bleed at 1.94x
(1728x800 for a 891x411 window), so the capture only has to be resized onto
them. The percentages from compare_window.py are meaningless for these files:
its BOX crop was measured for a different page size and cuts the window.

Usage:
    python3 tools/fxcompare/diffwin.py <capture.png> <reference.png> [--map out.png]

Prints one line: PASS/FAIL, global error, and the worst 3x3 zones.
"""
from __future__ import annotations

import sys

import numpy as np
from PIL import Image, ImageChops, ImageDraw

DELTA = 24
THRESHOLD_PCT = 1.5


def load(path: str) -> Image.Image:
    return Image.open(path).convert("RGB")


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 2:
        print(__doc__)
        return 2
    cap = load(args[0])
    ref = load(args[1])
    if cap.size != ref.size:
        cap = cap.resize(ref.size, Image.LANCZOS)

    d = np.asarray(ImageChops.difference(cap, ref), dtype=np.int16)
    bad = (d > DELTA).any(axis=2)
    pct = 100.0 * bad.sum() / bad.size

    h, w = bad.shape
    zones = []
    gy, gx = 4, 4
    for j in range(gy):
        for i in range(gx):
            cell = bad[j * h // gy:(j + 1) * h // gy, i * w // gx:(i + 1) * w // gx]
            zones.append((100.0 * cell.mean(), i, j))
    zones.sort(reverse=True)

    ok = pct <= THRESHOLD_PCT
    worst = " ".join(f"({i},{j})={p:.0f}%" for p, i, j in zones[:3])
    print(f"{'PASS' if ok else 'FAIL'} · écart = {pct:.2f} % · pires zones {worst}")

    if "--map" in sys.argv:
        out = sys.argv[sys.argv.index("--map") + 1]
        vis = Image.fromarray(np.where(bad[..., None], np.array([255, 60, 60], np.uint8),
                                       np.asarray(cap, np.uint8)))
        ImageDraw.Draw(vis).grid = None
        vis.save(out)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
