#!/usr/bin/env python3
"""Finds, per region, whether a Compose render is shifted against its JSX reference.

A plain pixel diff cannot tell "different content" from "same content drawn a few
pixels off". This searches a small (+/-8 px) offset window and reports the shift
that minimises the difference, so a non-zero best shift is a real layout offset
while a low residual means the content matches once aligned.
"""
import sys

import numpy as np
from PIL import Image

W, H = 891, 411

REGIONS = {
    "topbar": (0, 0, W, 52),
    "chord": (100, 0, 560, 52),
    "body": (0, 52, W, 352),
    "bottombar": (0, 352, W, 411),
}


def load(p):
    im = Image.open(p).convert("RGB")
    if im.size != (W, H):
        im = im.resize((W, H), Image.LANCZOS)
    return np.asarray(im, dtype=np.float32)


def best_shift(a, b, max_shift=8):
    best = (1e9, 0, 0)
    for dy in range(-max_shift, max_shift + 1):
        for dx in range(-max_shift, max_shift + 1):
            y0, y1 = max(0, dy), min(H, H + dy)
            x0, x1 = max(0, dx), min(W, W + dx)
            sa = a[y0:y1, x0:x1]
            sb = b[y0 - dy:y1 - dy, x0 - dx:x1 - dx]
            d = np.abs(sa - sb).mean()
            if d < best[0]:
                best = (d, dx, dy)
    return best


def main():
    compose, jsx = sys.argv[1], sys.argv[2]
    a = load(compose)
    b = load(jsx)
    print(f"{'region':10s} {'raw':>7s} {'aligned':>8s} {'dx':>4s} {'dy':>4s}")
    for name, (x0, y0, x1, y1) in REGIONS.items():
        ra = a[y0:y1, x0:x1]
        rb = b[y0:y1, x0:x1]
        raw = np.abs(ra - rb).mean()
        # shift search inside the region only
        best = (1e9, 0, 0)
        for dy in range(-8, 9):
            for dx in range(-8, 9):
                yy0, yy1 = max(0, dy), (y1 - y0) + min(0, dy)
                xx0, xx1 = max(0, dx), (x1 - x0) + min(0, dx)
                sa = ra[yy0:yy1, xx0:xx1]
                sb = rb[yy0 - dy:yy1 - dy, xx0 - dx:xx1 - dx]
                d = np.abs(sa - sb).mean()
                if d < best[0]:
                    best = (d, dx, dy)
        print(f"{name:10s} {raw:7.1f} {best[0]:8.1f} {best[1]:4d} {best[2]:4d}")


if __name__ == "__main__":
    main()
