"""Measure the parameter-grid columns of a Sunday Keys effect screen.

A screen is a grid of parameter cells: each cell has a label, a control (knob or
slider) and a value readout. The columns are recovered by profiling ink along x
inside a given row band, which yields the cell pitch and the grid origin.
"""
import sys

import numpy as np
from PIL import Image


def cols_in(path, y0, y1, min_gap=14):
    im = Image.open(path)
    im.load()
    a = np.asarray(im.convert("RGB")).astype(np.int16)
    lum = a.mean(axis=2)
    vals, counts = np.unique((lum // 6 * 6).astype(np.int32), return_counts=True)
    bg = vals[counts.argmax()]
    m = (np.abs(lum - bg) > 16)[y0:y1]
    p = m.mean(axis=0)
    on = p > 0.06
    out, i, n = [], 0, len(on)
    while i < n:
        if on[i]:
            j = i
            while j < n and (on[j] or (j + min_gap < n and on[j:j + min_gap].any())):
                j += 1
            if j - i > 8:
                out.append((i, j))
            i = j
        else:
            i += 1
    return out


path = sys.argv[1]
for spec in sys.argv[2:]:
    y0, y1 = (int(v) for v in spec.split(":"))
    cs = cols_in(path, y0, y1)
    print(f"{path.rsplit('/', 1)[-1]}  rows {y0}..{y1}  -> {len(cs)} columns")
    for x0, x1 in cs:
        print(f"    x {x0:5d}..{x1:5d}  w={x1-x0:4d}  centre={(x0+x1)//2:5d}")
