"""Measure the geometry of an FX reference PNG.

`reference_source_fx_window.html` shipped empty in fx-screens-spec.zip, so the
authoritative source (1) is unavailable and the layout has to be read back from
the render (source 3) and cross-checked against fx_layout_spec.json (source 2).

Usage:
    python3 tools/fxcompare/measure_ref.py ref-00-space
"""
from __future__ import annotations

import pathlib
import sys

import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
REFS = ROOT / "app/assets/fx-screens-spec/references"


def largest_blob(mask: np.ndarray) -> tuple[int, int, int, int, int]:
    """Largest 4-connected component; returns (count, x0, y0, x1, y1)."""
    h, w = mask.shape
    lab = np.zeros((h, w), np.int32)
    cur = 0
    best = (0, 0, 0, 0, 0)
    for y0 in range(h):
        for x0 in range(w):
            if not mask[y0, x0] or lab[y0, x0]:
                continue
            cur += 1
            stack = [(y0, x0)]
            lab[y0, x0] = cur
            n = 0
            ax0 = bx0 = x0
            ay0 = by0 = y0
            while stack:
                y, x = stack.pop()
                n += 1
                ax0, bx0 = min(ax0, x), max(bx0, x)
                ay0, by0 = min(ay0, y), max(by0, y)
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < h and 0 <= xx < w and mask[yy, xx] and not lab[yy, xx]:
                        lab[yy, xx] = cur
                        stack.append((yy, xx))
            if n > best[0]:
                best = (n, ax0, ay0, bx0, by0)
    return best


def main() -> int:
    name = sys.argv[1] if len(sys.argv) > 1 else "ref-00-space"
    path = REFS / f"{name}.png"
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(float)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    print(f"{name} {im.size}")

    # The orb is the one large bright, saturated region. Saturation separates it
    # from the near-grey chrome (tiles, knobs, text) around it.
    mx = a.max(2)
    mn = a.min(2)
    sat = mx - mn
    orb = (mx > 90) & (sat > 34)
    n, x0, y0, x1, y1 = largest_blob(orb)
    print(f"orb blob px={n} bbox=({x0},{y0})-({x1},{y1}) "
          f"center=({(x0 + x1) / 2:.1f},{(y0 + y1) / 2:.1f}) "
          f"size={x1 - x0 + 1}x{y1 - y0 + 1}")

    # Bright compact blobs = the knob rings. Threshold on luminance, drop the
    # orb, and report the remaining clusters above 0.3% of the frame.
    lum = a.mean(2)
    bright = lum > 150
    bright[y0:y1 + 1, x0:x1 + 1] = False
    h, w = bright.shape
    seen = np.zeros((h, w), bool)
    blobs = []
    for yy in range(h):
        for xx in range(w):
            if not bright[yy, xx] or seen[yy, xx]:
                continue
            stack = [(yy, xx)]
            seen[yy, xx] = True
            pts = []
            while stack:
                y, x = stack.pop()
                pts.append((y, x))
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < h and 0 <= nx < w and bright[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            stack.append((ny, nx))
            if len(pts) > 250:
                ys = [p[0] for p in pts]
                xs = [p[1] for p in pts]
                blobs.append((len(pts), min(xs), min(ys), max(xs), max(ys)))
    blobs.sort(reverse=True)
    print(f"bright blobs > 250 px: {len(blobs)}")
    for n, x0, y0, x1, y1 in blobs[:12]:
        print(f"  px={n:6d} ({x0:4d},{y0:4d})-({x1:4d},{y1:4d}) "
              f"c=({(x0 + x1) / 2:6.1f},{(y0 + y1) / 2:6.1f}) "
              f"{x1 - x0 + 1:4d}x{y1 - y0 + 1:4d}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
