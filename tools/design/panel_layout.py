#!/usr/bin/env python3
"""Table de disposition etiquetee d'une interface.

Combine la segmentation par couleur (boites des composants) avec l'OCR
(etiquettes et leurs positions), pour produire la liste des controles avec
leur boite et leur nom. C'est ce qui permet de traduire une capture en
positions au lieu de les inventer.

  panel_layout.py image.png
"""
import sys, csv, subprocess, tempfile, os
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layout import load, comps


def ocr_words(path, scale=4):
    """Rend les mots OCR avec leur position en coordonnees de l'image d'origine."""
    im = Image.open(path).convert("L")
    big = im.resize((im.width * scale, im.height * scale), Image.LANCZOS)
    tmp = tempfile.mktemp(suffix=".png")
    big.save(tmp)
    base = tempfile.mktemp()
    subprocess.run(["tesseract", tmp, base, "--psm", "11", "tsv"],
                   capture_output=True)
    out = []
    try:
        for r in csv.DictReader(open(base + ".tsv"), delimiter="\t"):
            try:
                c = float(r["conf"])
            except (TypeError, ValueError):
                continue
            t = (r.get("text") or "").strip()
            if c > 25 and len(t) >= 2:
                out.append((int(r["left"]) // scale, int(r["top"]) // scale, t))
    except FileNotFoundError:
        pass
    finally:
        for f in (tmp, base + ".tsv"):
            if os.path.exists(f):
                os.remove(f)
    return out


def nearest(word, x, y):
    """Etiquette la plus proche d'un point, ponderee vers la droite et le bas."""
    wx, wy, t = word
    d = ((wx - x) * 1.0) ** 2 + ((wy - y) * 1.6) ** 2
    return d, t


def main(path, k=12, min_area=200):
    A = load(path)
    im = Image.open(path).convert("RGB")
    q = im.quantize(colors=k, method=Image.MEDIANCUT)
    lab = np.asarray(q)
    pal = q.getpalette()
    H, W = lab.shape
    words = ocr_words(path)
    boxes = []
    for L in range(k):
        m = (lab == L)
        if m.sum() < min_area:
            continue
        c = (pal[L * 3], pal[L * 3 + 1], pal[L * 3 + 2])
        for n, xa, ya, xb, yb in comps(m, min_area):
            boxes.append((n, xa, ya, xb, yb, c))
    boxes.sort(key=lambda r: (r[2], r[1]))
    print(f"=== {path}  {W}x{H} — {len(boxes)} composants, {len(words)} mots OCR ===")
    print("   aire     x0   y0   x1   y1    l    h   couleur    etiquette")
    for n, xa, ya, xb, yb, c in boxes:
        cx, cy = (xa + xb) // 2, (ya + yb) // 2
        cand = sorted(((nearest(w, cx, yb + 2), w[2]) for w in words))[:2]
        lab_txt = " / ".join(t for _, t in cand) if cand else ""
        print(f"  {n:6d}  {xa:4d} {ya:4d} {xb:4d} {yb:4d}  {xb-xa+1:4d} {yb-ya+1:4d}  "
              f"#{c[0]:02X}{c[1]:02X}{c[2]:02X}  {lab_txt}")


if __name__ == "__main__":
    p = sys.argv[1]
    k = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    main(p, k)
