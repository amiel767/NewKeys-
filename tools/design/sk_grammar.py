"""Consolidated layout grammar of the Sunday Keys effect screens.

For each screen: panel bounds, the header band, the Settings/Output tab row, the
control rows (label row + control row), and the footer band. Output is a compact
table so several effects can be compared side by side.
"""
import glob
import os
import sys

import numpy as np
from PIL import Image


def panel_bounds(mask):
    """Bounding box of the region enclosed by the frame, ignoring the outer chrome."""
    rows = mask.mean(axis=1)
    cols = mask.mean(axis=0)
    ry = np.where(rows > 0.30)[0]
    rx = np.where(cols > 0.30)[0]
    return (rx[0], ry[0], rx[-1], ry[-1]) if len(rx) and len(ry) else (0, 0, 0, 0)


def row_bands(mask, min_strength=0.03, min_h=8):
    p = mask.mean(axis=1)
    on = p > min_strength
    out, i, n = [], 0, len(on)
    while i < n:
        if on[i]:
            j = i
            while j < n and on[j]:
                j += 1
            if j - i >= min_h:
                out.append((i, j))
            i = j
        else:
            i += 1
    return out


def analyze(path):
    im = Image.open(path)
    im.load()
    a = np.asarray(im.convert("RGB")).astype(np.int16)
    h, w, _ = a.shape
    lum = a.mean(axis=2)
    vals, counts = np.unique((lum // 6 * 6).astype(np.int32), return_counts=True)
    bg = vals[counts.argmax()]
    mask = np.abs(lum - bg) > 16
    px0, py0, px1, py1 = panel_bounds(mask)
    name = os.path.basename(path)
    print(f"{name:18s} {w:4d}x{h:<4d} panel={px0},{py0}..{px1},{py1} ({px1-px0}x{py1-py0})")
    rb = row_bands(mask)
    print("   rows:", " ".join(f"{y0}-{y1}" for y0, y1 in rb[:14]))


for f in sorted(glob.glob(sys.argv[1]), key=lambda p: int("".join(c for c in os.path.basename(p) if c.isdigit()) or 0)):
    try:
        analyze(f)
    except Exception as e:
        print(os.path.basename(f), "ERR", e)
