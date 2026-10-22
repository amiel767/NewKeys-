"""Compare a window-only Compose capture with its reference PNG.

`FxWindowCaptureTest` renders just the FX window (825x343 logical, the
reference's PWL x PHL) because that is the whole component: the page margin
around it in the reference PNG belongs to the screenshot, not the window.
`compare_scaled.py` resizes the whole capture onto the whole reference, which
lines the window up with the page margin instead — that is what made the
percentages jump when the test stopped rendering the page.

This crops the reference down to the window first (the window spans y 39.4..381.6
and x 12.8..837.0 of the 851x393 page, measured on the PNGs), then resizes the
capture onto it, and applies the same threshold as compare_refs.py.

usage: python3 tools/fxcompare/compare_window.py <capture.png> <reference.png>
Exit code 0 = PASS.
"""
import sys

import numpy as np
from PIL import Image, ImageChops

THRESHOLD_PCT = 1.5
CHANNEL_DELTA = 24

# Window box in reference pixels, as fractions of the 1728x800 reference.
BOX = (12.8 / 851.0, 39.4 / 393.0, 837.0 / 851.0, 381.6 / 393.0)


def window_of(ref: Image.Image) -> Image.Image:
    w, h = ref.size
    return ref.crop((round(BOX[0] * w), round(BOX[1] * h), round(BOX[2] * w), round(BOX[3] * h)))


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2

    cap = Image.open(sys.argv[1]).convert("RGB")
    ref = window_of(Image.open(sys.argv[2]).convert("RGB"))

    if cap.size != ref.size:
        cap = cap.resize(ref.size, Image.LANCZOS)

    d = np.asarray(ImageChops.difference(cap, ref), dtype=np.int16)
    bad = int((d > CHANNEL_DELTA).any(axis=2).sum())
    pct = 100.0 * bad / (ref.size[0] * ref.size[1])
    print(f"{'PASS' if pct <= THRESHOLD_PCT else 'FAIL'} · écart = {pct:.2f} % (seuil {THRESHOLD_PCT} %)")
    return 0 if pct <= THRESHOLD_PCT else 1


if __name__ == "__main__":
    sys.exit(main())
