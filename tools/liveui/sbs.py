#!/usr/bin/env python3
"""Side-by-side ASCII of the same dp region of two 3x renders.

Usage: sbs.py REF.png CUR.png x0 y0 x1 y1 [--cols N] [--sub]
The images are 3x, so coordinates are dp. With --sub, luminance is measured as
distance from the panel background, which makes dark-on-dark details visible.
"""
import sys
import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True
CH = ' .:-=+*#%@'
BG = np.array([20.0, 27.0, 39.0])


def load(p):
    return np.asarray(Image.open(p).convert('RGB'), dtype=np.float32)


def render(a, x0, y0, x1, y1, cols, sub):
    s = a[y0 * 3:y1 * 3, x0 * 3:x1 * 3]
    h, w, _ = s.shape
    rows = max(1, int(cols * h / w * 0.5))
    ys = np.linspace(0, h - 1, rows).astype(int)
    xs = np.linspace(0, w - 1, cols).astype(int)
    sm = s[np.ix_(ys, xs)]
    if sub:
        lum = np.clip(np.abs(sm - BG).sum(axis=2) / 2.2, 0, 255)
    else:
        lum = 0.299 * sm[:, :, 0] + 0.587 * sm[:, :, 1] + 0.114 * sm[:, :, 2]
    return [''.join(CH[min(9, int(v / 256 * 10))] for v in row) for row in lum]


def main():
    a = sys.argv[1:]
    cols = 160
    if '--cols' in a:
        i = a.index('--cols')
        cols = int(a[i + 1])
        a = a[:i] + a[i + 2:]
    sub = '--sub' in a
    if sub:
        a = [v for v in a if v != '--sub']
    ref_p, cur_p = a[0], a[1]
    x0, y0, x1, y1 = (int(v) for v in a[2:6])
    r1 = render(load(ref_p), x0, y0, x1, y1, cols, sub)
    r2 = render(load(cur_p), x0, y0, x1, y1, cols, sub)
    print(f'== dp({x0},{y0})-({x1},{y1})  REF {ref_p} (left) | CUR {cur_p} (right) ==')
    for l, r in zip(r1, r2):
        print(l + ' | ' + r)


if __name__ == '__main__':
    main()
