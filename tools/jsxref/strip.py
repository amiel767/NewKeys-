#!/usr/bin/env python3
"""Builds zoomed strip comparisons (JSX reference above, Compose below) per zone."""
import sys

from PIL import Image, ImageDraw

W, H = 891, 411


def load(p):
    im = Image.open(p).convert("RGB")
    if im.size != (W, H):
        im = im.resize((W, H), Image.LANCZOS)
    return im


def strip(name, compose, jsx, y0, y1, zoom=2):
    a = load(compose)
    b = load(jsx)
    h = y1 - y0
    ca = a.crop((0, y0, W, y1)).resize((W * zoom, h * zoom), Image.NEAREST)
    cb = b.crop((0, y0, W, y1)).resize((W * zoom, h * zoom), Image.NEAREST)
    canvas = Image.new("RGB", (W * zoom, h * zoom * 2 + 10), (10, 12, 18))
    canvas.paste(cb, (0, 0))
    canvas.paste(ca, (0, h * zoom + 10))
    d = ImageDraw.Draw(canvas)
    d.text((6, 2), "JSX", fill=(255, 220, 120))
    d.text((6, h * zoom + 12), "COMPOSE", fill=(120, 220, 255))
    out = f"/tmp/shots/strip_{name}.png"
    canvas.save(out)
    print("saved", out)


if __name__ == "__main__":
    compose, jsx, name, y0, y1 = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
    strip(name, compose, jsx, y0, y1)
