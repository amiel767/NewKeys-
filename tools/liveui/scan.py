#!/usr/bin/env python3
"""Print RGB values along a horizontal or vertical scanline (dp coords)."""
import sys
import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True
p = sys.argv[1]
axis = sys.argv[2]  # 'h' or 'v'
fixed = float(sys.argv[3])
lo, hi = float(sys.argv[4]), float(sys.argv[5])
step = float(sys.argv[6]) if len(sys.argv) > 6 else 1.0
a = np.asarray(Image.open(p).convert('RGB'), dtype=np.int16)
if axis == 'h':
    y = int(fixed * 3)
    for x in np.arange(lo, hi, step):
        r, g, b = a[y, int(x * 3)]
        print(f'x={x:7.1f}dp  ({r:3d},{g:3d},{b:3d})')
else:
    x = int(fixed * 3)
    for y in np.arange(lo, hi, step):
        r, g, b = a[int(y * 3), x]
        print(f'y={y:7.1f}dp  ({r:3d},{g:3d},{b:3d})')
