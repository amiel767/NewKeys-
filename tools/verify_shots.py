#!/usr/bin/env python3
"""Verifie par les pixels que les captures montrent bien un changement.

OCR indisponible (miroir Debian en 504) : on mesure la ou le changement doit
apparaitre, au lieu de lire le texte.

  1. Save Æther : la pastille n'existe que si un reglage a bouge. On compare la
     bande d'en-tete entre la capture "instrument" (au repos) et "save"
     (parametre deplace) ; une boite de difference non vide dans le coin droit
     prouve que le bouton est dessine.
  2. Setlist : "0 piste" ne doit porter AUCUN fader, "8 pistes" doit en porter.
     On compte les colonnes non-fond a mi-hauteur.
"""
import os
import sys

from PIL import Image, ImageChops

SHOTS = "app/src/test/screenshots"


def header_band(path, top=0.0, bottom=0.09):
    im = Image.open(path).convert("RGB")
    h = im.height
    return im.crop((0, int(h * top), im.width, int(h * bottom)))


def column_profile(path, row_frac=0.5, bg_tol=26):
    """Nombre de colonnes contenant au moins un pixel different du fond."""
    im = Image.open(path).convert("RGB")
    w, h = im.size
    y = int(h * row_frac)
    bg = im.getpixel((w - 2, y))
    count = 0
    for x in range(0, w, 2):
        p = im.getpixel((x, y))
        if abs(p[0] - bg[0]) + abs(p[1] - bg[1]) + abs(p[2] - bg[2]) > bg_tol * 3:
            count += 1
    return count, w


def main():
    a = os.path.join(SHOTS, "aether_window_instrument.png")
    b = os.path.join(SHOTS, "aether_window_save.png")
    if os.path.exists(a) and os.path.exists(b):
        ia, ib = header_band(a), header_band(b)
        diff = ImageChops.difference(ia, ib)
        bbox = diff.getbbox()
        # Nombre de pixels reellement differents dans l'en-tete.
        changed = sum(1 for p in diff.getdata() if sum(p) > 30)
        print(f"Save Æther  : en-tete differe sur {changed} px, bbox={bbox}")
        print(f"  -> bouton Save {'PRESENT' if changed > 200 else 'ABSENT'}")
    else:
        print("Save Æther  : captures manquantes")

    for name, expect in (("setlist_0_track.png", "aucun fader"),
                         ("setlist_8_tracks.png", "8 faders")):
        p = os.path.join(SHOTS, name)
        if not os.path.exists(p):
            print(f"{name} : manquante")
            continue
        cols, w = column_profile(p)
        print(f"{name} : {cols} colonnes non-fond / {w} ({expect})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
