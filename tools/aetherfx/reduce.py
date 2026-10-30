"""Halve the capture dimensions and re-encode, for publishing.

Roborazzi renders at 440 dpi, so the raw PNG is 2750 px wide for a 1000 dp stage.
Halving keeps every logical pixel and lands near 1:1 on a 2x display, which is
where these are actually viewed.
"""
import sys
import os
from PIL import Image

src = sys.argv[1]
dst = sys.argv[2]
im = Image.open(src).convert("RGB")
w, h = im.size
im = im.resize((w // 2, h // 2), Image.LANCZOS)
im.save(dst, "PNG", optimize=True)
print("%-26s %4dx%-5d -> %4dx%-5d  %6.0f Ko -> %5.0f Ko"
      % (os.path.basename(dst), w, h, w // 2, h // 2,
         os.path.getsize(src) / 1024, os.path.getsize(dst) / 1024))
