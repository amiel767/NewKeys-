#!/usr/bin/env python3
"""Semantic map: classify each cell of the render into a symbol.

Symbols: ' ' background, 'P' panel, 'W' bright text, 'A' accent blue,
'R' red/pink, 'G' green, 'B' blue, '#' dark groove.
Usage: smap.py <img> [cols] [rows]
"""
import sys

import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True


def classify(px):
    r, g, b = float(px[0]), float(px[1]), float(px[2])
    mx = max(r, g, b)
    if mx > 200:
        return 'W'
    if r > 120 and r > g + 40 and r > b + 20:
        return 'R'
    if g > 130 and g > r + 40:
        return 'G'
    if b > 150 and b > r + 30:
        return 'A'
    if mx > 60:
        return 'P'
    if mx < 18:
        return '#'
    return '.'


def main():
    path = sys.argv[1]
    cols = int(sys.argv[2]) if len(sys.argv) > 2 else 178
    rows = int(sys.argv[3]) if len(sys.argv) > 3 else 41
    a = np.asarray(Image.open(path).convert('RGB'), dtype=np.float32)
    h, w, _ = a.shape
    by, bx = h // rows, w // cols
    # median-ish via max to keep thin text
    sub = a[:rows * by, :cols * bx].reshape(rows, by, cols, bx, 3).max(axis=(1, 3))
    for r in sub:
        print(''.join(classify(px) for px in r))


if __name__ == '__main__':
    main()
