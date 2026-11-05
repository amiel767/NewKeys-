#!/usr/bin/env python3
"""Planche de reference Valhalla : le document de travail, en zones annotees.

    python3 tools/design/vref_page.py

Decoupe `room_24.jpg` (la capture de Room, la plus nette) en zones — en-tete,
potards, colonne de gauche, bas — et les pose dans une page, avec les mesures.
C'est ce qu'on regarde avant de coder, et ce qu'on met a cote du rendu.
"""
import os

from PIL import Image

OUT = "/workspace/dl/pub/fxdesign"
SRC = "/workspace/dl/valhalla/room_24.jpg"


def crop(src, dst, box, scale=1.0):
    im = Image.open(src).convert("RGB").crop(box)
    if scale != 1.0:
        im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    im.save(dst, "WEBP", quality=88, method=5)
    return im.size


def main():
    os.makedirs(OUT, exist_ok=True)
    crop(SRC, f"{OUT}/ref_full.webp", (0, 0, 1608, 918), 1.0)
    crop(SRC, f"{OUT}/ref_header.webp", (0, 0, 1608, 210), 1.4)
    # Un potard : 108 px de diametre, centre (985, 344). On cadre large.
    crop(SRC, f"{OUT}/ref_knob.webp", (985 - 90, 344 - 90, 985 + 90, 344 + 90), 3.2)
    crop(SRC, f"{OUT}/ref_left.webp", (0, 200, 880, 660), 1.5)
    crop(SRC, f"{OUT}/ref_bottom.webp", (0, 660, 1608, 918), 1.5)
    print("ok")


if __name__ == "__main__":
    main()
