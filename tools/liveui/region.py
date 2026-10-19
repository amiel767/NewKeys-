#!/usr/bin/env python3
"""Print an ASCII view of a dp region of one or two images side by side.

Usage:
  python3 region.py IMG x0 y0 x1 y1 [IMG2] [--cols N]
Coordinates are in dp (the images are 3x). """
import sys
import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True
CH = ' .:-=+*#%@'


def load(p):
    return np.asarray(Image.open(p).convert('RGB'), dtype=np.int16)


def block(a, x0, y0, x1, y1, cols, norm=False, sub=False):
    sub_ = a[y0 * 3:y1 * 3, x0 * 3:x1 * 3].astype(np.float32)
    h, w, _ = sub_.shape
    rows = max(1, int(cols * h / w * 0.5))
    ys = np.linspace(0, h - 1, rows).astype(int)
    xs = np.linspace(0, w - 1, cols).astype(int)
    sm = sub_[np.ix_(ys, xs)]
    if sub:
        bg = np.array([20.0, 27.0, 39.0])
        lum = np.abs(sm - bg).sum(axis=2)
        lum = np.clip(lum / 2.2, 0, 255)
    else:
        lum = 0.299 * sm[:, :, 0] + 0.587 * sm[:, :, 1] + 0.114 * sm[:, :, 2]
    if norm:
        lo, hi = float(lum.min()), float(lum.max())
        lum = (lum - lo) / max(1e-6, hi - lo) * 255.0
    return [''.join(CH[min(9, int(v / 256 * 10))] for v in row) for row in lum]


def main():
    a = sys.argv[1:]
    cols = 150
    if '--cols' in a:
        i = a.index('--cols')
        cols = int(a[i + 1])
        a = a[:i] + a[i + 2:]
    norm = '--norm' in a
    if norm:
        a = [v for v in a if v != '--norm']
    sub = '--sub' in a
    if sub:
        a = [v for v in a if v != '--sub']
    p1, x0, y0, x1, y1 = a[0], int(a[1]), int(a[2]), int(a[3]), int(a[4])
    rest = a[5:]
    r1 = block(load(p1), x0, y0, x1, y1, cols, norm, sub)
    if rest:
        r2 = block(load(rest[0]), x0, y0, x1, y1, cols, norm, sub)
        print(f'=== {p1} (left)  |  {rest[0]} (right) ===')
        for l, r in zip(r1, r2):
            print(l + ' | ' + r)
    else:
        print(f'=== {p1} dp({x0},{y0})-({x1},{y1}) ===')
        for r in r1:
            print(r)


if __name__ == '__main__':
    main()
