"""OCR every Sunday Keys effect screenshot and print its label structure.

Runs tesseract on a contrast-stretched 2x upscale, then reports the detected text
lines plus the image size, so the layout grammar (title bar, control labels,
value readouts, footer band) can be compared across effects as text.
"""
import glob
import os
import subprocess
import sys

from PIL import Image, ImageOps

SRC = sys.argv[1] if len(sys.argv) > 1 else "/workspace/dl/sk/app"
GLOB = sys.argv[2] if len(sys.argv) > 2 else "effects_*.png"
MIN_W = int(sys.argv[3]) if len(sys.argv) > 3 else 800

for f in sorted(glob.glob(os.path.join(SRC, GLOB)), key=lambda p: int("".join(c for c in os.path.basename(p) if c.isdigit()) or 0)):
    im = Image.open(f)
    im.load()
    if im.width < MIN_W:
        print(f"=== {os.path.basename(f)} {im.width}x{im.height} (small, skipped)")
        continue
    g = ImageOps.autocontrast(im.convert("L"))
    g = g.resize((g.width * 2, g.height * 2), Image.LANCZOS)
    tmp = "/tmp/_sk_ocr.png"
    g.save(tmp)
    txt = subprocess.run(["tesseract", tmp, "-", "--psm", "6"], capture_output=True, text=True).stdout
    lines = [l.strip() for l in txt.splitlines() if l.strip()]
    print(f"=== {os.path.basename(f)} {im.width}x{im.height}  ({len(lines)} text lines)")
    for l in lines[:26]:
        print("   ", l)
