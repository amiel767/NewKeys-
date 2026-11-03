#!/usr/bin/env python3
"""Convertit les PNG de modele piano (zip « stitch_chat_discussion_app ») en
planches WebP pour res/drawable-nodpi/ae_piano_<id>.webp.

Les 12 modeles de AePianoModels.all ont desormais chacun leur photo : les trois
electriques (Ballad, DX, E.Piano) tombaient avant sur l'image de Tine Keys.

    python3 tools/pianopack/build_plates.py <dossier_des_png>
"""
import glob
import os
import sys

from PIL import Image

# Nom du PNG dans le zip -> id du modele dans AePianoModels.all
NAME_TO_ID = {
    "Felt": "felt",
    "Studio": "studio",
    "Bright": "bright",
    "Mellow": "mellow",
    "Dark": "dark",
    "Cottage": "upright",
    "Parlor": "lady",
    "Saloon": "honky",
    "Tine": "electric",
    "Ballad": "ballad",
    "DX": "dx",
    "EP": "epiano",
}

WIDTH = 1500          # meme largeur que les planches precedentes
QUALITY = 80          # ~140 Ko par planche, comme les anciennes
OUT = "app/src/main/res/drawable-nodpi"


def main(src: str) -> int:
    total = 0
    for png, mid in sorted(NAME_TO_ID.items()):
        path = os.path.join(src, f"{png}.png")
        if not os.path.exists(path):
            print(f"  absent : {path}")
            continue
        im = Image.open(path).convert("RGB")
        h = round(WIDTH * im.height / im.width)
        im = im.resize((WIDTH, h), Image.LANCZOS)
        dst = os.path.join(OUT, f"ae_piano_{mid}.webp")
        im.save(dst, "WEBP", quality=QUALITY, method=6)
        size = os.path.getsize(dst)
        total += size
        print(f"  {png:8s} -> ae_piano_{mid}.webp  {im.width}x{im.height}  {size // 1024} Ko")
    print(f"total {total // 1024} Ko")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    raise SystemExit(main(sys.argv[1]))
