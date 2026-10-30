"""Measure the real palette and structure of a plugin UI screenshot.

Reports the dominant colours (quantised), the vertical band structure from row
brightness, and a coarse luminance grid. Numbers only: this is how the design
decisions get grounded instead of guessed.
"""
import sys
from collections import Counter

import numpy as np
from PIL import Image


def bands(gray, rows=40):
    h = gray.shape[0]
    step = max(1, h // rows)
    out = []
    for y in range(0, h - step + 1, step):
        seg = gray[y:y + step]
        out.append((y, float(seg.mean()), float(seg.std())))
    return out


def main():
    path = sys.argv[1]
    topn = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    im = Image.open(path).convert("RGB")
    a = np.asarray(im)
    print(f"{path}  {im.width}x{im.height}")

    # Dominant colours, quantised to 16 levels per channel.
    q = (a // 16 * 16).reshape(-1, 3)
    cnt = Counter(map(tuple, q))
    total = len(q)
    print("\ndominant colours:")
    for c, n in cnt.most_common(topn):
        print(f"  #{c[0]:02X}{c[1]:02X}{c[2]:02X}  {100 * n / total:5.2f}%  rgb{c}")

    gray = np.asarray(im.convert("L"), dtype=float)
    print("\nvertical bands (y, mean, std):")
    for y, m, s in bands(gray, 28):
        bar = "#" * int(m / 6)
        print(f"  y{y:5d}  {m:6.1f} {s:6.1f}  {bar}")

    # Coarse luminance grid so the shape of the layout is visible as text.
    print("\nluminance grid:")
    rows, cols = 16, 56
    gh, gw = im.height // rows, im.width // cols
    ramp = " .:-=+*#%@"
    for r in range(rows):
        line = ""
        for c in range(cols):
            v = gray[r * gh:(r + 1) * gh, c * gw:(c + 1) * gw].mean()
            line += ramp[min(9, int(v / 25.6))]
        print("  " + line)


if __name__ == "__main__":
    main()
