#!/usr/bin/env python3
"""Crop a dp region of one or two 3x renders and write an upscaled PNG.

Usage: crop.py IMG x0 y0 x1 y1 OUT.png [--zoom N] [--stack IMG2]
--stack puts a second image below the first (same dp region).
"""
import sys
from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True


def crop(p, x0, y0, x1, y1, zoom):
    im = Image.open(p).convert('RGB').crop((x0 * 3, y0 * 3, x1 * 3, y1 * 3))
    return im.resize((im.width * zoom // 3, im.height * zoom // 3), Image.NEAREST)


def main():
    a = sys.argv[1:]
    zoom = 3
    if '--zoom' in a:
        i = a.index('--zoom')
        zoom = int(a[i + 1])
        a = a[:i] + a[i + 2:]
    stack = None
    if '--stack' in a:
        i = a.index('--stack')
        stack = a[i + 1]
        a = a[:i] + a[i + 2:]
    p = a[0]
    x0, y0, x1, y1 = (int(v) for v in a[1:5])
    out = a[5]
    top = crop(p, x0, y0, x1, y1, zoom)
    if stack:
        bot = crop(stack, x0, y0, x1, y1, zoom)
        canvas = Image.new('RGB', (max(top.width, bot.width), top.height + bot.height + 6), (255, 0, 255))
        canvas.paste(top, (0, 0))
        canvas.paste(bot, (0, top.height + 6))
        canvas.save(out)
    else:
        top.save(out)
    print('wrote', out, top.size)


if __name__ == '__main__':
    main()
