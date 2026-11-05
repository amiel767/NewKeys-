#!/usr/bin/env python3
"""Trouve les potards ronds d'une capture Valhalla, par analyse de blobs.

Un potard Valhalla : un disque de rayon R dont le CONTOUR est nettement plus
clair que le panneau plat qui l'entoure, et dont le CENTRE est plus sombre que
le contour. On cherche ces disques par un score de bord circulaire.

    python3 tools/design/vknobs.py /workspace/dl/valhalla/room_24.jpg
"""
from __future__ import annotations

import sys

import numpy as np
from PIL import Image


def main() -> None:
    p = sys.argv[1]
    im = Image.open(p).convert("L")
    a = np.asarray(im).astype(np.float32)
    h, w = a.shape
    g = np.abs(np.diff(a, axis=1))
    g = np.pad(g, ((0, 0), (0, 1)))

    best = []
    for R in range(18, 90, 3):
        step = max(4, R // 3)
        # Un anneau echantillonne : 48 points sur le cercle.
        ang = np.linspace(0, 2 * np.pi, 48, endpoint=False)
        dx = (R * np.cos(ang)).astype(int)
        dy = (R * np.sin(ang)).astype(int)
        for cy in range(R + 2, h - R - 2, step):
            for cx in range(R + 2, w - R - 2, step):
                ring = g[cy + dy, cx + dx]
                inside = a[cy - R // 3:cy + R // 3, cx - R // 3:cx + R // 3]
                outside = a[cy - R - 6:cy - R + 2, cx - R // 3:cx + R // 3]
                if outside.size == 0 or inside.size == 0:
                    continue
                score = ring.mean() * 2.0 + (outside.mean() - inside.mean()) * 0.6
                best.append((score, cx, cy, R))

    best.sort(reverse=True)
    kept = []
    for s, cx, cy, R in best:
        if all((cx - k[1]) ** 2 + (cy - k[2]) ** 2 > (R * 1.1) ** 2 for k in kept):
            kept.append((s, cx, cy, R))
        if len(kept) >= 14:
            break
    print(f"{p} {w}x{h} — {len(kept)} potards candidats")
    for s, cx, cy, R in sorted(kept, key=lambda k: (k[2] // 40, k[1])):
        print(f"  centre ({cx:5d},{cy:4d})  R {R:3d}  score {s:5.1f}")


if __name__ == "__main__":
    main()
