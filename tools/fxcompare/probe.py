#!/usr/bin/env python3
"""Locate the real differences between a reference and a capture, per effect.

The plain pixel metric is dominated by animation phase (see AGENTS.md), so this
tool also builds an *edge* map: a Canny-style gradient magnitude of each image,
compared cell by cell. Edges are far less phase sensitive than raw luminance,
so a cell that is hot in both metrics is very likely a missing or displaced
element rather than a time offset.

Usage:
    python3 tools/fxcompare/probe.py voices iron-grip bass organ brass piano
    python3 tools/fxcompare/probe.py --zooms voices     # write zoom sheets
"""
from __future__ import annotations

import sys
import pathlib

import numpy as np
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
REFS = ROOT / "app/assets/fx-screens-spec/references"
CAPS = ROOT / "app/src/test/screenshots"
OUT = ROOT / "tools/fxcompare/zooms"

# `ref-<NN>-<tag>.png`, the file names used by fx-screens-spec.
NUM = {
    "space": "00", "echo": "01", "prism-tone": "02", "iron-grip": "03",
    "solar-ceiling": "04", "piano": "05", "organ": "06", "sax": "07",
    "strings": "08", "guitar": "09", "amp": "10", "bass": "11",
    "brass": "12", "voices": "13", "synth": "14",
}

# The 1728x800 references are whole windows; the 825x343 captures are the scene
# alone. This crop is the empirically optimal window on the known-good faces.
BOX = (26, 80, 1702, 776)
W, H = 825, 343
CW, CH = 55, 49  # error-map cell size
NCX, NCY = 15, 7


def load(tag: str) -> tuple[np.ndarray, np.ndarray]:
    ref = Image.open(REFS / f"ref-{NUM[tag]}-{tag}.png").convert("RGB")
    ref = np.array(ref.crop(BOX).resize((W, H), Image.LANCZOS)).astype(np.float32)
    cap = np.array(Image.open(CAPS / f"fx_window_{tag}.png").convert("RGB")
                   .resize((W, H), Image.LANCZOS)).astype(np.float32)
    return ref, cap


def edges(gray: np.ndarray) -> np.ndarray:
    gy, gx = np.gradient(gray)
    return np.hypot(gx, gy)


def cells(a: np.ndarray) -> np.ndarray:
    out = np.zeros((NCY, NCX), dtype=np.float32)
    for i in range(NCY):
        for j in range(NCX):
            out[i, j] = a[i * CH:(i + 1) * CH, j * CW:(j + 1) * CW].mean()
    return out


def analyse(tag: str, zooms: int = 0) -> tuple[float, float, list]:
    ref, cap = load(tag)
    rg, cg = ref.mean(axis=2), cap.mean(axis=2)
    px = cells(np.abs(rg - cg))
    ed = cells(np.abs(edges(rg) - edges(cg)))
    total = float(np.abs(ref - cap).mean())

    # Rank by the edge metric: it is the phase-robust one. The pixel score only
    # breaks ties and is reported so a pure-luminance drift stays visible.
    flat = [(ed[i, j], px[i, j], j, i) for i in range(NCY) for j in range(NCX)]
    flat.sort(reverse=True)
    print(f"\n=== {tag} (id {int(NUM[tag])}) — ecart {total:.2f} — "
          f"structure {ed.mean():.2f} / pixels {px.mean():.2f} ===")
    print("  pires cellules par STRUCTURE (et pixels) :")
    for e, p, j, i in flat[:5]:
        print(f"    x{j * CW:3d}..{j * CW + CW:3d}  y{i * CH:3d}..{i * CH + CH:3d}"
              f"   structure {e:6.1f}  pixels {p:6.1f}")

    if zooms:
        write_zoom_sheet(tag, flat[:zooms])
    return total, float(ed.mean()), flat


def write_zoom_sheet(tag: str, hot: list, scale: int = 4) -> None:
    """One row per hot cell: reference | capture | abs difference, magnified."""
    OUT.mkdir(parents=True, exist_ok=True)
    ref, cap = load(tag)
    pad = 12
    tiles = []
    for _, _, j, i in hot:
        x0, y0 = max(0, j * CW - pad), max(0, i * CH - pad)
        x1, y1 = min(W, (j + 1) * CW + pad), min(H, (i + 1) * CH + pad)
        r = ref[y0:y1, x0:x1].astype(np.float32)
        c = cap[y0:y1, x0:x1].astype(np.float32)
        d = np.abs(r - c).mean(axis=2)
        d = np.clip(d * 3.0, 0, 255)
        d3 = np.repeat(d[:, :, None], 3, axis=2)
        row = np.concatenate([r, np.full((r.shape[0], 4, 3), 40), c,
                              np.full((r.shape[0], 4, 3), 40), d3], axis=1)
        tiles.append((row, f"x{x0}..{x1} y{y0}..{y1}"))

    width = max(t.shape[1] for t, _ in tiles) * scale
    height = sum(t.shape[0] * scale + 16 for t, _ in tiles)
    sheet = Image.new("RGB", (width, height), (12, 15, 22))
    d = ImageDraw.Draw(sheet)
    y = 0
    for row, label in tiles:
        d.text((2, y + 2), f"{tag}  {label}", fill=(200, 220, 255))
        img = Image.fromarray(row.astype(np.uint8))
        img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        sheet.paste(img, (0, y + 14))
        y += row.shape[0] * scale + 16
    path = OUT / f"{tag}-zooms.png"
    sheet.save(path)
    print(f"    zooms -> {path.relative_to(ROOT)}  ({sheet.width}x{sheet.height})")


def main(argv: list[str]) -> int:
    zooms = 0
    args = []
    it = iter(argv)
    for a in it:
        if a == "--zooms":
            zooms = int(next(it, "3"))
        else:
            args.append(a)
    tags = args or list(NUM)
    unknown = [t for t in tags if t not in NUM]
    if unknown:
        print(f"inconnus : {unknown}", file=sys.stderr)
        return 2
    print("Ecart par effet, et structure (bords) :")
    rows = []
    for t in tags:
        total, struct, _ = analyse(t, zooms if zooms else 0)
        rows.append((struct, total, t))
    print("\n=== classement par STRUCTURE (robuste a la phase) ===")
    for s, tot, t in sorted(rows, reverse=True):
        print(f"  {t:14s} structure {s:6.2f}   pixels {tot:6.2f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
