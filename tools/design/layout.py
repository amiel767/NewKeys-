#!/usr/bin/env python3
"""Extrait la disposition d'une image d'interface.

Sort, pour une image : palette reelle, couverture alpha, puis chaque composant
visuel (panneau, knob, curseur, bouton, rail) avec sa boite exacte et son role
devine. Sert a traduire une capture en positions, au lieu de les inventer.

  layout.py image.png              tableau des composants
  layout.py image.png --map        carte texte classe par couleur
  layout.py image.png --layers     couches de couleur et leur boite englobante
"""
import sys, argparse
import numpy as np
from PIL import Image
from collections import deque, Counter


def load(p):
    im = Image.open(p).convert("RGBA")
    return np.asarray(im).astype(int)


def comps(mask, min_area=24, max_n=400):
    H, W = mask.shape
    seen = np.zeros_like(mask)
    out = []
    for y in range(H):
        for x in np.where(mask[y] & ~seen[y])[0]:
            if seen[y, x]:
                continue
            q = deque([(y, x)])
            seen[y, x] = True
            xa = xb = x
            ya = yb = y
            n = 0
            while q:
                cy, cx = q.popleft()
                n += 1
                xa = min(xa, cx); xb = max(xb, cx)
                ya = min(ya, cy); yb = max(yb, cy)
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = cy + dy, cx + dx
                    if 0 <= ny < H and 0 <= nx < W and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        q.append((ny, nx))
            if n >= min_area:
                out.append((n, xa, ya, xb, yb))
    out.sort(key=lambda t: -t[0])
    return out[:max_n]


def role(w, h, n):
    """Devine le role d'un composant d'apres sa forme et son remplissage."""
    ar = w / max(h, 1)
    fill = n / max(w * h, 1)
    if ar < 0.28 and h > 3 * w and h > 40:
        return "rail vertical"
    if ar > 3.5 and w > 3 * h and w > 40:
        return "rail horizontal"
    if 0.6 <= ar <= 1.65:
        if fill > 0.72:
            return "disque plein" if min(w, h) > 14 else "pastille"
        if fill > 0.45:
            return "knob" if min(w, h) > 16 else "petit bouton"
        return "anneau / cadre"
    if ar > 1.65:
        return "bouton large" if h < 34 else "panneau large"
    return "element vertical" if h < 90 else "panneau vertical"


def report(A, path):
    H, W, _ = A.shape
    rgb = A[:, :, :3]
    a = A[:, :, 3]
    print(f"=== {path}  {W}x{H} ===")
    tr = int((a < 16).sum())
    print(f"alpha : {tr*100//(W*H)}% transparent, {int((a>=250).sum())*100//(W*H)}% opaque")
    q = (rgb // 16 * 16)
    flat = q.reshape(-1, 3)
    uniq, cnt = np.unique(flat, axis=0, return_counts=True)
    print("palette (12 premieres) :")
    for i in np.argsort(-cnt)[:12]:
        c = uniq[i]
        print(f"   #{c[0]:02X}{c[1]:02X}{c[2]:02X}  {cnt[i]*100/(W*H):5.1f}%")
    solid = (a > 40)
    print("composants :")
    print("   aire     x0   y0   x1   y1    l    h   rempl   role")
    for n, xa, ya, xb, yb in comps(solid):
        w, h = xb - xa + 1, yb - ya + 1
        print(f"  {n:6d}  {xa:4d} {ya:4d} {xb:4d} {yb:4d}  {w:4d} {h:4d}  "
              f"{n/(w*h):5.2f}   {role(w,h,n)}")


def regions(A, path, k=14, min_area=90):
    """Segmente par couleur (k-moyennes) puis sort chaque region connexe.

    C'est le mode utile pour une interface opaque : chaque element (panneau,
    capot, bouton, disque) est une region de couleur presque uniforme.
    """
    H, W, _ = A.shape
    im = Image.fromarray(A[:, :, :3].astype(np.uint8))
    q = im.quantize(colors=k, method=Image.MEDIANCUT)
    lab = np.asarray(q)
    pal = q.getpalette()
    print(f"=== {path}  {W}x{H} — {k} regions de couleur ===")
    print("   aire     x0   y0   x1   y1    l    h   rempl   couleur")
    rows = []
    for L in range(k):
        m = (lab == L)
        if m.sum() < min_area:
            continue
        c = (pal[L * 3], pal[L * 3 + 1], pal[L * 3 + 2])
        for n, xa, ya, xb, yb in comps(m, min_area):
            w, h = xb - xa + 1, yb - ya + 1
            rows.append((n, xa, ya, xb, yb, w, h, c))
    rows.sort(key=lambda r: (r[2], r[1]))
    for n, xa, ya, xb, yb, w, h, c in rows:
        print(f"  {n:6d}  {xa:4d} {ya:4d} {xb:4d} {yb:4d}  {w:4d} {h:4d}  "
              f"{n/(w*h):5.2f}   #{c[0]:02X}{c[1]:02X}{c[2]:02X}  {role(w,h,n)}")


def layers(A, path):
    """Une couleur = une couche. Donne la boite englobante de chaque couche."""
    H, W, _ = A.shape
    rgb = A[:, :, :3]
    a = A[:, :, 3]
    q = (rgb // 24 * 24)
    flat = q.reshape(-1, 3)
    uniq, cnt = np.unique(flat, axis=0, return_counts=True)
    print(f"=== {path}  {W}x{H} — couches de couleur ===")
    print("   couleur     %     x0   y0   x1   y1    l    h")
    for i in np.argsort(-cnt)[:16]:
        if cnt[i] * 100 / (W * H) < 0.4:
            continue
        c = uniq[i]
        m = np.all(q == c, axis=2) & (a > 40)
        yy, xx = np.where(m)
        if len(yy) == 0:
            continue
        print(f"  #{c[0]:02X}{c[1]:02X}{c[2]:02X}  {cnt[i]*100/(W*H):5.1f}  "
              f"{xx.min():4d} {yy.min():4d} {xx.max():4d} {yy.max():4d}  "
              f"{xx.max()-xx.min()+1:4d} {yy.max()-yy.min()+1:4d}")


RAMP = " .:-=+*#%@"


def amap(A, path, cols=110):
    H, W, _ = A.shape
    rows = max(1, int(cols * H / W / 2))
    cw, ch = W / cols, H / rows
    print(f"=== {path}  {W}x{H} -> {cols}x{rows} cellules ({cw:.1f}x{ch:.1f} px) ===")
    for r in range(rows):
        line = ""
        for c in range(cols):
            x0, x1 = int(c * cw), max(int(c * cw) + 1, int((c + 1) * cw))
            y0, y1 = int(r * ch), max(int(r * ch) + 1, int((r + 1) * ch))
            blk = A[y0:y1, x0:x1]
            av = blk[:, :, 3].mean()
            if av < 40:
                line += " "
                continue
            v = blk[:, :, :3].mean()
            line += RAMP[min(9, int((v / 255) ** 1.15 * 9))]
        print(f"{int(r*ch):4d} {line}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("--map", action="store_true")
    ap.add_argument("--regions", action="store_true")
    ap.add_argument("-k", type=int, default=14)
    ap.add_argument("--layers", action="store_true")
    ap.add_argument("--cols", type=int, default=110)
    g = ap.parse_args()
    A = load(g.image)
    if g.map:
        amap(A, g.image, g.cols)
    elif g.regions:
        regions(A, g.image, g.k)
    elif g.layers:
        layers(A, g.image)
    else:
        report(A, g.image)
