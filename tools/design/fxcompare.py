#!/usr/bin/env python3
"""Comparaison PRECISE d'une capture FX contre une reference Valhalla.

Le but n'est pas le pixel-a-pixel (les tailles different) mais la MESURE de ce
qui se voit : la silhouette (structure des aretes), la matiere (histogramme de
luminance) et l'ambiance (teinte moyenne). On sort des nombres, plus une planche
cote-a-cote + difference pour l'oeil.

    python3 tools/design/fxcompare.py <capture.png> <reference.jpg> [--out tools/design/shots]

Sortie (agregats seulement, jamais les pixels) :
  silhouette  : correlation des cartes d'aretes, 0..1 (1 = meme structure)
  matiere     : distance de Bhattacharyya des histogrammes, 0..1 (0 = meme)
  teinte      : ecart de teinte moyenne en degres
  contraste   : ecart d'ecart-type de luminance
  score       : moyenne ponderee, 0..100 (100 = identique)
"""
from __future__ import annotations

import colorsys
import sys
from pathlib import Path

import numpy as np
from PIL import Image

NORM = (960, 400)


def load(p: Path, size=NORM) -> np.ndarray:
    im = Image.open(p).convert("RGB").resize(size, Image.LANCZOS)
    return np.asarray(im).astype(np.float32) / 255.0


def edges(a: np.ndarray) -> np.ndarray:
    g = a.mean(axis=2)
    gx = np.zeros_like(g)
    gy = np.zeros_like(g)
    gx[:, 1:-1] = np.abs(g[:, 2:] - g[:, :-2])
    gy[1:-1, :] = np.abs(g[2:, :] - g[:-2, :])
    e = np.hypot(gx, gy)
    return e / (e.max() + 1e-9)


def silhouette(a: np.ndarray, b: np.ndarray) -> float:
    ea, eb = edges(a).ravel(), edges(b).ravel()
    ea = ea - ea.mean()
    eb = eb - eb.mean()
    d = (np.linalg.norm(ea) * np.linalg.norm(eb)) + 1e-9
    return float(np.clip((ea @ eb) / d, 0, 1))


def hist(a: np.ndarray, bins: int = 48) -> np.ndarray:
    h, _ = np.histogram(a.mean(axis=2).ravel(), bins=bins, range=(0, 1), density=True)
    return h / (h.sum() + 1e-9)


def bhattacharyya(a: np.ndarray, b: np.ndarray) -> float:
    bc = np.sqrt(hist(a) * hist(b)).sum()
    return float(np.clip(1.0 - bc, 0, 1))


def mean_hue(a: np.ndarray) -> float:
    m = a.reshape(-1, 3).mean(axis=0)
    h, _, _ = colorsys.rgb_to_hsv(*m)
    return h * 360.0


def side_by_side(a: np.ndarray, b: np.ndarray, out: Path) -> None:
    ai = Image.fromarray((a * 255).astype(np.uint8))
    bi = Image.fromarray((b * 255).astype(np.uint8))
    d = np.abs(a - b).mean(axis=2)
    di = Image.fromarray((np.clip(d * 3, 0, 1) * 255).astype(np.uint8)).convert("RGB")
    w, h = NORM
    sheet = Image.new("RGB", (w, h * 3 + 16), (16, 16, 20))
    sheet.paste(ai, (0, 0))
    sheet.paste(bi, (0, h + 8))
    sheet.paste(di, (0, h * 2 + 16))
    sheet.save(out)


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) < 2:
        print(__doc__)
        raise SystemExit(1)
    cap, ref = Path(args[0]), Path(args[1])
    out = Path("tools/design/shots")
    if "--out" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--out") + 1])
    out.mkdir(parents=True, exist_ok=True)

    a, b = load(cap), load(ref)
    s = silhouette(a, b)
    m = bhattacharyya(a, b)
    dh = abs(mean_hue(a) - mean_hue(b))
    dh = min(dh, 360 - dh)
    dc = abs(float(a.std()) - float(b.std()))

    score = 100.0 * (
        0.45 * s
        + 0.25 * (1 - min(m * 2.0, 1))
        + 0.15 * (1 - min(dh / 180.0, 1))
        + 0.15 * (1 - min(dc / 0.20, 1))
    )

    print(f"capture   {cap.name}  ({Image.open(cap).size[0]}x{Image.open(cap).size[1]})")
    print(f"reference {ref.name}  ({Image.open(ref).size[0]}x{Image.open(ref).size[1]})")
    print(f"  silhouette {s:5.3f}   matiere {m:5.3f}   teinte {dh:5.1f} deg   contraste {dc:5.3f}")
    print(f"  SCORE {score:5.1f} / 100")

    name = f"cmp_{cap.stem}_vs_{ref.stem}.png"
    side_by_side(a, b, out / name)
    print(f"  planche {out / name}")


if __name__ == "__main__":
    main()
