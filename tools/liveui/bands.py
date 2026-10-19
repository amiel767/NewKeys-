#!/usr/bin/env python3
"""Find horizontal bands and columns of panel-like content in a render."""
import sys
import numpy as np
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True
p = sys.argv[1] if len(sys.argv) > 1 else '/tmp/liveui/screen.png'
a = np.asarray(Image.open(p).convert('RGB'), dtype=np.int16)
h, w, _ = a.shape
bg = a[5, 5]
diff = np.abs(a - bg).sum(axis=2)
mask = diff > 30
print('bg', tuple(bg), 'size', w, h)
print('--- rows with content (dp) ---')
rowsum = mask.sum(axis=1)
for y in range(0, h, 3):
    v = rowsum[y]
    if v > 20:
        print(f'{y/3:6.1f}dp  {v}')
