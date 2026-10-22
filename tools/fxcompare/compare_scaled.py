"""Compare a Compose capture with a reference PNG, tolerating a size mismatch.

`app/assets/fx-screens-spec/compare_refs.py` requires the capture and the
reference to be the exact same pixel size. Roborazzi captures at the density of
the test's @Config, while the references are fixed 2x renders, so the two rarely
match. This wrapper resizes the capture to the reference size first and reports
the scale factor it used, then applies the same threshold as compare_refs.py
(<= 1.5 % of pixels differing by more than 24/255 on any channel).

usage: python3 tools/fxcompare/compare_scaled.py <capture.png> <reference.png>
Exit code 0 = PASS.
"""
import sys

from PIL import Image, ImageChops

THRESHOLD_PCT = 1.5
CHANNEL_DELTA = 24


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2

    cap = Image.open(sys.argv[1]).convert("RGB")
    ref = Image.open(sys.argv[2]).convert("RGB")

    if cap.size != ref.size:
        print(f"note: capture {cap.size} -> resize to reference {ref.size}")
        cap = cap.resize(ref.size, Image.LANCZOS)

    diff = ImageChops.difference(cap, ref).split()
    mask = Image.new("L", ref.size, 0)
    for channel in diff:
        over = channel.point(lambda v: 255 if v > CHANNEL_DELTA else 0)
        mask = ImageChops.lighter(mask, over)

    total = ref.size[0] * ref.size[1]
    bad = sum(1 for v in mask.getdata() if v)
    pct = 100.0 * bad / total
    ok = pct <= THRESHOLD_PCT

    print(f"{'PASS' if ok else 'FAIL'} · écart = {pct:.2f} % (seuil {THRESHOLD_PCT} %)")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
