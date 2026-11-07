#!/usr/bin/env python3
"""Carte de structure d'une reference : ou sont les panneaux, les potards, le texte.

    python3 tools/design/vlayout.py <image>

Sort une carte en blocs (une lettre par bloc) et les cotes des potards detectes.
Sert a traduire une maquette en geometrie, sans la regarder a l'oeil.
"""
import sys

import numpy as np
from PIL import Image
from scipy import ndimage


def classify(px):
    r, g, b = int(px[0]), int(px[1]), int(px[2])
    mx, mn = max(r, g, b), min(r, g, b)
    if mx > 200:
        return "W"          # texte / clair
    if mx - mn > 40:
        return "C"          # couleur saturee
    if mx < 30:
        return "."          # noir
    if mx < 60:
        return ":"          # panneau tres sombre
    if mx < 90:
        return "-"          # panneau sombre
    if mx < 150:
        return "="          # potard
    return "+"              # gris clair


def main():
    path = sys.argv[1]
    cols = int(sys.argv[2]) if len(sys.argv) > 2 else 120
    im = Image.open(path).convert("RGB")
    w, h = im.size
    a = np.asarray(im)
    rows = max(1, round(cols * h / w / 2.1))
    print(f"{path}  {w}x{h}  carte {cols}x{rows}  (1 bloc = {w/cols:.0f}px)")

    lines = []
    for by in range(rows):
        line = ""
        for bx in range(cols):
            y0, y1 = int(by * h / rows), max(int((by + 1) * h / rows), int(by * h / rows) + 1)
            x0, x1 = int(bx * w / cols), max(int((bx + 1) * w / cols), int(bx * w / cols) + 1)
            blk = a[y0:y1, x0:x1].reshape(-1, 3)
            line += classify(np.median(blk, axis=0))
        lines.append(line)
    for i, l in enumerate(lines):
        print(f"{int(i*h/rows):4d} {l}")

    # Potards : disques uniformes, gris moyen, ronds.
    g = a[:, :, 0].astype(int)
    uni = (abs(a[:, :, 0].astype(int) - a[:, :, 1]) < 5) & \
          (abs(a[:, :, 1].astype(int) - a[:, :, 2]) < 5) & (g > 95) & (g < 160)
    lab, n = ndimage.label(uni)
    discs = []
    for i in range(1, n + 1):
        ys, xs = np.nonzero(lab == i)
        if len(xs) < 500:
            continue
        bw, bh = xs.max() - xs.min() + 1, ys.max() - ys.min() + 1
        if 0.75 < bw / bh < 1.35 and len(xs) / (bw * bh) > 0.55:
            discs.append((int(xs.mean()), int(ys.mean()), bw))
    discs.sort(key=lambda t: (t[1], t[0]))
    print(f"\npotards : {len(discs)}")
    for x, y, d in discs:
        print(f"  centre ({x:4d},{y:4d})  diametre {d}")
    if len(discs) > 1:
        dxs = sorted({d[0] for d in discs})
        gaps = [b - a2 for a2, b in zip(dxs, dxs[1:])]
        print(f"  diametre median {sorted(d[2] for d in discs)[len(discs)//2]}"
              f"  pas horizontal median {sorted(gaps)[len(gaps)//2] if gaps else '-'}")


if __name__ == "__main__":
    main()
