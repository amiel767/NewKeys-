#!/usr/bin/env python3
"""Block-max contrast view: shows any pixel differing from the app background.

Usage: bm.py <img> [cols] [rows]
"""
import sys

import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True
CH = ' .:-=+*#%@'


def view(path, cols=178, rows=41):
    a = np.asarray(Image.open(path).convert('RGB'), dtype=np.float32)
    bg = np.array([20.0, 27.0, 39.0])
    d = np.abs(a - bg).sum(axis=2)
    h, w = d.shape
    by, bx = h // rows, w // cols
    pool = d[:rows * by, :cols * bx].reshape(rows, by, cols, bx).max(axis=(1, 3))
    return [''.join(CH[min(9, int(v / 40))] for v in r) for r in pool]


def main():
    args = sys.argv[1:]
    cols = int(args[1]) if len(args) > 1 else 178
    rows = int(args[2]) if len(args) > 2 else 41
    for p in [args[0]]:
        print(f'##### {p}')
        for r in view(p, cols, rows):
            print(r)


if __name__ == '__main__':
    main()
