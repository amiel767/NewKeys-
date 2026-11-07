#!/usr/bin/env python3
"""Trouve les captures d'INTERFACE Valhalla parmi un dossier d'images.

Une interface se reconnait a ses grandes plages PLATES (panneaux) et a ses
aretes nettes ; une photo n'en a pas. On classe donc par « fraction de pixels
plats » et par densite d'aretes, et on n'imprime qu'un classement court.

    python3 tools/design/vfind.py /workspace/dl/valhalla [--top 12]
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image


def score(p: Path) -> tuple[float, float, int, int] | None:
    try:
        im = Image.open(p).convert("L")
    except Exception:
        return None
    if im.width < 300 or im.height < 200:
        return None
    a = np.asarray(im).astype(np.float32)
    # Arete : gradient de Sobel simplifie.
    gx = np.abs(np.diff(a, axis=1)).mean()
    gy = np.abs(np.diff(a, axis=0)).mean()
    edges = (gx + gy) / 2
    # Plat : ecart local faible (bloc 8x8).
    h, w = a.shape
    h8, w8 = h // 8 * 8, w // 8 * 8
    blk = a[:h8, :w8].reshape(h8 // 8, 8, w8 // 8, 8).transpose(0, 2, 1, 3)
    flat = (blk.std(axis=(2, 3)) < 2.5).mean()
    return flat, edges, im.width, im.height


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "/workspace/dl/valhalla")
    top = 12
    if "--top" in sys.argv:
        top = int(sys.argv[sys.argv.index("--top") + 1])
    rows = []
    for p in sorted(root.iterdir()):
        if p.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
            continue
        s = score(p)
        if s:
            rows.append((p.name, *s))
    # Une interface : beaucoup de plat, peu d'arete moyenne, format large.
    rows.sort(key=lambda r: -(r[1] * 2.0 - min(r[2], 40) / 40.0))
    print(f"{len(rows)} images ; classement par « plat*2 - arete »")
    for name, flat, edges, w, h in rows[:top]:
        print(f"  {name:28s} plat {flat:5.2f}  arete {edges:5.2f}  {w}x{h}")


if __name__ == "__main__":
    main()
