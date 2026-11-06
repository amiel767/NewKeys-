#!/usr/bin/env python3
"""Detecte les panneaux (rectangles) d'une capture : seuillage du fond, composantes
connexes, boites englobantes triees par aire.
Usage: refpanels.py IMG [--crop x0,y0,x1,y1] [--min-area N] [--mode bg|dark|light] [--top N]
"""
import argparse, numpy as np
from PIL import Image
from collections import deque

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('img'); ap.add_argument('--crop')
    ap.add_argument('--min-area', type=int, default=900)
    ap.add_argument('--mode', default='dark', choices=['dark', 'light', 'sat'])
    ap.add_argument('--top', type=int, default=40)
    a = ap.parse_args()
    im = Image.open(a.img).convert('RGB')
    if a.crop: im = im.crop(tuple(int(v) for v in a.crop.split(',')))
    A = np.asarray(im).astype(np.int16)
    v = A.mean(axis=2)
    if a.mode == 'dark':
        mask = v < np.percentile(v, 40)
    elif a.mode == 'light':
        mask = v > np.percentile(v, 70)
    else:
        mask = (A.max(axis=2) - A.min(axis=2)) > 30
    H, W = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    boxes = []
    for y0 in range(H):
        for x0 in range(W):
            if not mask[y0, x0] or seen[y0, x0]: continue
            q = deque([(y0, x0)]); seen[y0, x0] = True
            xa = xb = x0; ya = yb = y0; n = 0
            while q:
                y, x = q.popleft(); n += 1
                if x < xa: xa = x
                if x > xb: xb = x
                if y < ya: ya = y
                if y > yb: yb = y
                for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    ny, nx = y+dy, x+dx
                    if 0 <= ny < H and 0 <= nx < W and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True; q.append((ny, nx))
            if n >= a.min_area:
                boxes.append((n, xa, ya, xb, yb))
    boxes.sort(reverse=True)
    print(f'# {W}x{H}  {len(boxes)} regions >= {a.min_area}px  (aire, x0,y0 -> x1,y1, w x h)')
    for n, xa, ya, xb, yb in boxes[:a.top]:
        print(f'  {n:7d}  ({xa:4d},{ya:4d}) -> ({xb:4d},{yb:4d})   {xb-xa+1:4d} x {yb-ya+1:3d}')

if __name__ == '__main__':
    main()
