#!/usr/bin/env python3
"""Compare the JSX reference render with the Compose render.

Both are produced at 3x of an 891x411 canvas, so pixel (x, y) refers to the
same dp (x/3, y/3) in both. Usage:

    python3 cmp.py ref.png cur.png [--ascii x0,y0,x1,y1] [--diff]
"""
import sys
import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True


def load(p):
    return np.asarray(Image.open(p).convert('RGB'), dtype=np.int16)


def ascii_map(a, cols=170, rows=None, y0=0, y1=None, x0=0, x1=None):
    sub = a[y0:y1, x0:x1]
    h, w, _ = sub.shape
    rows = rows or max(1, int(cols * h / w * 0.5))
    ys = np.linspace(0, h - 1, rows).astype(int)
    xs = np.linspace(0, w - 1, cols).astype(int)
    sm = sub[np.ix_(ys, xs)]
    lum = (0.299 * sm[:, :, 0] + 0.587 * sm[:, :, 1] + 0.114 * sm[:, :, 2])
    chars = ' .:-=+*#%@'
    out = []
    for r in range(rows):
        out.append(''.join(chars[min(len(chars) - 1, int(v / 256 * len(chars)))] for v in lum[r]))
    return '\n'.join(out)


def main():
    args = sys.argv[1:]
    ref = load(args[0])
    cur = load(args[1])
    rest = args[2:]

    if '--diff' in rest:
        d = np.abs(ref - cur).sum(axis=2)
        print('mean abs diff', round(float(d.mean()), 2), 'max', int(d.max()))
        print('pct pixels > 60:', round(float((d > 60).mean() * 100), 2), '%')
        # block diff map
        bh, bw = 30, 30
        H, W = d.shape
        print('--- diff map (block mean, 30px blocks) ---')
        chars = ' .:-=+*#%@'
        for by in range(0, H, bh * 2):
            line = ''
            for bx in range(0, W, bw):
                blk = d[by:by + bh * 2, bx:bx + bw]
                m = blk.mean() if blk.size else 0
                line += chars[min(9, int(m / 256 * 10))]
            print(f'{by/3:6.1f}dp {line}')
        return

    if '--ascii' in rest:
        i = rest.index('--ascii')
        box = [int(v) * 3 for v in rest[i + 1].split(',')]
        x0, y0, x1, y1 = box
        print('===== JSX REFERENCE =====')
        print(ascii_map(ref, y0=y0, y1=y1, x0=x0, x1=x1))
        print('===== COMPOSE =====')
        print(ascii_map(cur, y0=y0, y1=y1, x0=x0, x1=x1))
        return

    # default: compare brightness profiles
    print('shapes', ref.shape, cur.shape)


if __name__ == '__main__':
    main()
