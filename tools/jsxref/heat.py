#!/usr/bin/env python3
"""Writes a diff heatmap and measures the bevel contrast on the snapshot cases."""
import sys

import numpy as np
from PIL import Image

W, H = 891, 411


def load(p):
    im = Image.open(p).convert("RGB")
    if im.size != (W, H):
        im = im.resize((W, H), Image.LANCZOS)
    return im


def main():
    compose, jsx, out = sys.argv[1], sys.argv[2], sys.argv[3]
    a = np.asarray(load(compose), dtype=np.int16)
    b = np.asarray(load(jsx), dtype=np.int16)
    d = np.abs(a - b).mean(axis=2).astype(np.uint8)
    # heatmap: dark blue = match, bright red = mismatch
    heat = np.zeros((H, W, 3), dtype=np.uint8)
    heat[..., 0] = np.clip(d * 3, 0, 255)
    heat[..., 2] = np.clip(255 - d * 3, 0, 255)
    Image.fromarray(heat).save(out)
    print("heatmap", out, "mean diff", round(float(d.mean()), 1))

    # bevel probe: bottom bar snapshot cases sit around y 352..411.
    # A relief shows a light top edge and a dark bottom edge on the same column.
    for x in (60, 110, 160):
        col = a[352:411, x].mean(axis=1)
        top = col[2:6].mean()
        mid = col[20:30].mean()
        bot = col[52:58].mean()
        print(f"  x={x}: top={top:6.1f} mid={mid:6.1f} bottom={bot:6.1f} "
              f"bevel={top - bot:6.1f}")


if __name__ == "__main__":
    main()
