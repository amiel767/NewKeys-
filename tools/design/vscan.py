#!/usr/bin/env python3
"""Trouve les VRAIES captures d'interface dans le dossier de references.

    python3 tools/design/vscan.py

Une interface Valhalla se reconnait a sa signature : un fond sombre quasi plat et
des DISQUES uniformes (les potards). Une photo n'en a pas. On compte donc les
disques ronds uniformes et on classe la-dessus — pas sur « le plat », qui classe
les fonds de photo en tete.
"""
import glob
import os

import numpy as np
from PIL import Image
from scipy import ndimage

DIRS = ["/workspace/dl/valhalla"]


def discs_of(path, min_area=400):
    try:
        im = Image.open(path).convert("RGB")
    except Exception:
        return None
    a = np.asarray(im).astype(int)
    g = a[:, :, 0]
    uni = (abs(a[:, :, 0] - a[:, :, 1]) < 6) & (abs(a[:, :, 1] - a[:, :, 2]) < 6) \
        & (g > 80) & (g < 170)
    lab, n = ndimage.label(uni)
    out = []
    for i in range(1, n + 1):
        ys, xs = np.nonzero(lab == i)
        if len(xs) < min_area:
            continue
        w, h = xs.max() - xs.min() + 1, ys.max() - ys.min() + 1
        if 0.75 < w / h < 1.35 and len(xs) / (w * h) > 0.6:
            out.append((int(xs.mean()), int(ys.mean()), int(w)))
    # Fond sombre plat : part de pixels sombres.
    dark = float((g < 80).mean())
    return im.size, out, dark


def main():
    rows = []
    for d in DIRS:
        for p in sorted(glob.glob(os.path.join(d, "*"))):
            r = discs_of(p)
            if not r:
                continue
            size, ds, dark = r
            if len(ds) >= 3:
                rows.append((len(ds), os.path.basename(p), size, dark, sorted(d[2] for d in ds)))
    rows.sort(reverse=True)
    for n, name, size, dark, ds in rows[:18]:
        print(f"{name:26s} {size[0]}x{size[1]:<5d} potards {n:2d}  sombre {dark:.2f}  Ø {ds}")


if __name__ == "__main__":
    main()
