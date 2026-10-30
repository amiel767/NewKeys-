"""Render a screenshot as ASCII art so the layout can be read as text.

Each cell is the mean luminance of a block; the glyph ramp runs from dark to
light. Wide shots are also split into left/right halves at a configurable column
so a 16:9 screenshot stays readable in a terminal.
"""
import sys

import numpy as np
from PIL import Image

RAMP = " .:-=+*#%@"


def art(path, cols=100, split=None):
    im = Image.open(path)
    im.load()
    a = np.asarray(im.convert("RGB")).astype(np.float32)
    h, w, _ = a.shape
    if split:
        cut = int(w * split)
        return [art_of(a[:, :cut], cols, f"{path} LEFT {cut}px"),
                art_of(a[:, cut:], cols, f"{path} RIGHT {w - cut}px")]
    return [art_of(a, cols, f"{path} {w}x{h}")]


def art_of(a, cols, title):
    h, w, _ = a.shape
    rows = max(1, int(cols * h / w * 0.5))
    ys = np.linspace(0, h, rows + 1).astype(int)
    xs = np.linspace(0, w, cols + 1).astype(int)
    lum = a.mean(axis=2)
    # Dark UIs map everything to the bottom of the ramp. Stretch between the 2nd
    # and 98th percentile so panel edges and controls become visible.
    lo, hi = np.percentile(lum, 2), np.percentile(lum, 98)
    lum = np.clip((lum - lo) / max(1.0, hi - lo), 0, 1)
    lines = [f"--- {title} ---"]
    for i in range(rows):
        row = []
        for j in range(cols):
            blk = lum[ys[i]:ys[i + 1], xs[j]:xs[j + 1]]
            v = blk.mean() if blk.size else 0
            row.append(RAMP[min(len(RAMP) - 1, int(v * len(RAMP)))])
        lines.append("".join(row))
    return "\n".join(lines)


if __name__ == "__main__":
    cols = int(sys.argv[2]) if len(sys.argv) > 2 else 100
    sp = float(sys.argv[3]) if len(sys.argv) > 3 else None
    for chunk in art(sys.argv[1], cols, sp):
        print(chunk)
