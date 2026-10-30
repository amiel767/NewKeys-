"""Locate the control grid of a Sunday Keys effect screen.

Dials are the brightest compact blobs; sliders are long thin bright bars. The
tool labels each detected blob as dial/slider and prints its box, so the control
grammar (column x-positions, row y-positions, pitch) can be read as numbers.
"""
import sys

import numpy as np
from PIL import Image
from scipy import ndimage


def blobs(path, thr_pct=97.5, min_area=260, min_dim=20):
    im = Image.open(path)
    im.load()
    a = np.asarray(im.convert("RGB")).astype(np.float32)
    lum = a.mean(axis=2)
    thr = np.percentile(lum, thr_pct)
    m = lum > thr
    # Close small gaps so a dial ring with a dark centre is one blob.
    m = ndimage.binary_closing(m, structure=np.ones((5, 5)))
    lab, n = ndimage.label(m)
    out = []
    for sl in ndimage.find_objects(lab):
        y0, y1 = sl[0].start, sl[0].stop
        x0, x1 = sl[1].start, sl[1].stop
        w, h = x1 - x0, y1 - y0
        if w < min_dim or h < min_dim:
            continue
        area = int((lab[sl] > 0).sum())
        if area < min_area:
            continue
        # A dial is roughly square; a slider is a long bar.
        if w > 3 * h:
            kind = "slider"
        elif 0.6 <= w / h <= 1.7:
            kind = "dial"
        else:
            continue
        out.append((kind, x0, y0, w, h, area))
    out.sort(key=lambda t: (t[2], t[1]))
    return im.size, out


for p in sys.argv[1:]:
    size, bs = blobs(p)
    print(f"### {p.rsplit('/', 1)[-1]}  {size[0]}x{size[1]}  blobs={len(bs)}")
    for k, x, y, w, h, a in bs[:26]:
        print(f"   {k:6s} x={x:5d} y={y:5d} w={w:4d} h={h:4d} area={a:5d}")
