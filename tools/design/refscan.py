#!/usr/bin/env python3
"""Outils d'analyse d'une capture de reference : palette, structure, rendu ASCII.

Usage:
  refscan.py palette  IMG [--crop x0,y0,x1,y1] [--k N]
  refscan.py ascii    IMG --crop x0,y0,x1,y1 [--cols N] [--rows N]
  refscan.py bands    IMG [--axis y|x]      # bandes homogenes (separateurs)
  refscan.py probe    IMG --line y --x0 a --x1 b   # profil 1D
  refscan.py edges    IMG --crop ...        # detection de rectangles (panneaux)
"""
import sys, argparse
from PIL import Image

def load(p, crop=None):
    im = Image.open(p).convert('RGB')
    if crop:
        im = im.crop(tuple(int(v) for v in crop.split(',')))
    return im

def q(c, k=6):  # quantification douce
    return tuple((v//k)*k for v in c)

def palette(im, k=14):
    from collections import Counter
    c = Counter()
    px = im.load()
    for y in range(0, im.height, 2):
        for x in range(0, im.width, 2):
            c[q(px[x, y])] += 1
    tot = sum(c.values())
    out = []
    for col, n in c.most_common(k):
        out.append((n*100.0/tot, col))
    return out

RAMP = ' .:-=+*#%@'

def ascii_art(im, cols=110, rows=34, gamma=1.0):
    px = im.load()
    cw = im.width/cols; ch = im.height/rows
    lines = []
    for r in range(rows):
        line = ''
        for c in range(cols):
            tot = n = 0
            for y in range(int(r*ch), max(int(r*ch)+1, int((r+1)*ch)), max(1, int(ch/3))):
                for x in range(int(c*cw), max(int(c*cw)+1, int((c+1)*cw)), max(1, int(cw/3))):
                    if x < im.width and y < im.height:
                        tot += sum(px[x, y])/3; n += 1
            v = tot/max(n, 1)
            i = int((v/255.0)**gamma * (len(RAMP)-1))
            line += RAMP[i]
        lines.append(line)
    return lines

def bands(im, axis='y', step=1, tol=14):
    px = im.load()
    n = im.height if axis == 'y' else im.width
    m = im.width if axis == 'y' else im.height
    means = []
    for i in range(0, n, step):
        tot = 0
        for j in range(0, m, 3):
            tot += sum(px[j, i] if axis == 'y' else px[i, j])/3
        means.append(tot/len(range(0, m, 3)))
    out = []
    cur = None
    for i, v in enumerate(means):
        idx = i*step
        if cur is None or abs(v-cur[2]) > tol:
            if cur: out.append(cur)
            cur = [idx, idx, v]
        else:
            cur[1] = idx; cur[2] = (cur[2]+v)/2
    if cur: out.append(cur)
    return [(a, b, round(m, 1)) for a, b, m in out if b-a >= 2]

def probe(im, line, x0, x1, axis='x'):
    px = im.load()
    out = []
    for v in range(x0, x1):
        c = px[v, line] if axis == 'x' else px[line, v]
        out.append((v, tuple(c)))
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('mode'); ap.add_argument('img')
    ap.add_argument('--crop'); ap.add_argument('--k', type=int, default=14)
    ap.add_argument('--cols', type=int, default=110); ap.add_argument('--rows', type=int, default=34)
    ap.add_argument('--gamma', type=float, default=1.0)
    ap.add_argument('--axis', default='y'); ap.add_argument('--line', type=int, default=0)
    ap.add_argument('--x0', type=int, default=0); ap.add_argument('--x1', type=int, default=100)
    a = ap.parse_args()
    im = load(a.img, a.crop)
    if a.mode == 'palette':
        for pct, col in palette(im, a.k):
            print(f'  {pct:5.1f}%  rgb{col}  #{col[0]:02X}{col[1]:02X}{col[2]:02X}')
    elif a.mode == 'ascii':
        for i, l in enumerate(ascii_art(im, a.cols, a.rows, a.gamma)):
            print(f'{int(i*im.height/a.rows):4d} {l}')
    elif a.mode == 'bands':
        for x, y, m in bands(im, a.axis):
            print(f'  {x:5d}..{y:5d}  moy {m}')
    elif a.mode == 'probe':
        for v, c in probe(im, a.line, a.x0, a.x1, a.axis):
            print(f'  {v:5d}  rgb{c}  #{c[0]:02X}{c[1]:02X}{c[2]:02X}')

if __name__ == '__main__':
    main()
