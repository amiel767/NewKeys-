#!/usr/bin/env python3
"""Side-by-side and band-diff between a Compose render and its JSX reference.

The Compose render is captured at 3x (2673 x 1233 for an 891 x 411 dp screen);
the JSX reference is 1x. Both are normalised to 891 x 411 first so pixel counts
map 1:1 to dp and a diff means a real layout/colour difference, not a scale artefact.
"""
import sys
import pathlib

from PIL import Image, ImageChops, ImageDraw

W, H = 891, 411


def load(p):
    im = Image.open(p).convert("RGB")
    if im.size != (W, H):
        im = im.resize((W, H), Image.LANCZOS)
    return im


def bands(a, b, n=8):
    diff = ImageChops.difference(a, b)
    px = diff.load()
    rows = []
    bh = H // n
    for i in range(n):
        y0, y1 = i * bh, (i + 1) * bh if i < n - 1 else H
        tot = 0
        cnt = 0
        for y in range(y0, y1, 2):
            for x in range(0, W, 2):
                r, g, bl = px[x, y]
                tot += (r + g + bl) / 3
                cnt += 1
        rows.append((y0, y1, tot / max(cnt, 1)))
    return rows


def main():
    compose, jsx, out = sys.argv[1], sys.argv[2], sys.argv[3]
    a = load(compose)
    b = load(jsx)
    # stack: JSX on top, Compose below, with a 6px separator
    sep = 6
    canvas = Image.new("RGB", (W, H * 2 + sep), (20, 24, 32))
    canvas.paste(b, (0, 0))
    canvas.paste(a, (0, H + sep))
    d = ImageDraw.Draw(canvas)
    d.text((8, H + 2), "COMPOSE (below)  /  JSX (above)", fill=(255, 255, 255))
    canvas.save(out)
    print("saved", out)
    print("band  y0..y1   mean-diff")
    for y0, y1, v in bands(a, b):
        bar = "#" * int(min(v, 60) / 2)
        print(f"{y0:4d}..{y1:4d}  {v:6.1f}  {bar}")


if __name__ == "__main__":
    main()
