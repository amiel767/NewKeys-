#!/usr/bin/env python3
"""Coarse difference grid between two renders, in dp coordinates.

Usage: diffgrid.py <ref> <cur> [cell_dp]
Prints the worst cells (x0,y0) with mean abs diff and both average colours.
"""
import sys

import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True


def load(p):
    return np.asarray(Image.open(p).convert('RGB'), dtype=np.float32)


def main():
    ref, cur = load(sys.argv[1]), load(sys.argv[2])
    cell = int(sys.argv[3]) if len(sys.argv) > 3 else 20
    h, w, _ = ref.shape
    ch, cw = cell * 3, cell * 3
    rows, cols = h // ch, w // cw
    cells = []
    for r in range(rows):
        for c in range(cols):
            a = ref[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw]
            b = cur[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw]
            d = float(np.abs(a - b).mean())
            cells.append((d, c * cell, r * cell, a.mean(axis=(0, 1)), b.mean(axis=(0, 1))))
    cells.sort(reverse=True, key=lambda t: t[0])
    print(f'worst {min(40, len(cells))} cells of {cell}dp (x, y, meanAbsDiff, refRGB, curRGB)')
    for d, x, y, ca, cb in cells[:40]:
        print(f'  ({x:3d},{y:3d})  {d:6.1f}   ref={ca.round().astype(int)}  cur={cb.round().astype(int)}')


if __name__ == '__main__':
    main()
