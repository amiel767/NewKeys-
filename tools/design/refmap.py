#!/usr/bin/env python3
"""Carte de regions : classe chaque pixel (bois, ardoise, metal, or, fond) et
imprime une carte de symboles. Bien plus lisible qu'un rendu de luminance.
Usage: refmap.py IMG [--crop x0,y0,x1,y1] [--cols N] [--rows N] [--legend]
"""
import argparse
from PIL import Image

def classify(r, g, b):
    mx, mn = max(r, g, b), min(r, g, b)
    sat = mx - mn
    v = (r+g+b)/3
    if sat > 26 and r > g >= b and r-b > 24:        # bois / cuivre
        return 'W' if r > 120 else 'w'
    if sat > 26 and b > r and b-r > 18:             # accent bleu/cyan
        return 'B' if v > 90 else 'b'
    if sat > 30 and g > r and g > b:                # vert
        return 'G'
    if sat > 40 and r > 150 and g > 100 and b < 90: # or / laiton
        return 'Y'
    if v > 205: return '#'                          # blanc / chrome
    if v > 140: return '='                          # gris clair
    if v > 88:  return '-'                          # gris moyen
    if v > 46:  return ':'                          # sombre
    return ' '                                      # tres sombre

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('img'); ap.add_argument('--crop')
    ap.add_argument('--cols', type=int, default=120); ap.add_argument('--rows', type=int, default=48)
    ap.add_argument('--legend', action='store_true')
    a = ap.parse_args()
    im = Image.open(a.img).convert('RGB')
    if a.crop: im = im.crop(tuple(int(v) for v in a.crop.split(',')))
    px = im.load(); cw = im.width/a.cols; ch = im.height/a.rows
    print(f'# {im.width}x{im.height} -> {a.cols}x{a.rows} cellules ({cw:.1f}x{ch:.1f} px)')
    for r in range(a.rows):
        line = ''
        for c in range(a.cols):
            # vote majoritaire sur la cellule
            from collections import Counter
            cnt = Counter()
            for y in range(int(r*ch), max(int(r*ch)+1, int((r+1)*ch)), max(1, int(ch/3))):
                for x in range(int(c*cw), max(int(c*cw)+1, int((c+1)*cw)), max(1, int(cw/3))):
                    if x < im.width and y < im.height:
                        cnt[classify(*px[x, y])] += 1
            line += cnt.most_common(1)[0][0] if cnt else ' '
        print(f'{int(r*ch):4d} {line}')
    if a.legend:
        print('\nW/w bois  B/b accent bleu  G vert  Y or  # blanc  = gris clair  - gris  : sombre  (espace) noir')

if __name__ == '__main__':
    main()
