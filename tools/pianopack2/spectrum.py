#!/usr/bin/env python3
"""Spectral content of a bank: how much energy lives above 11 kHz.

This is the measurement that decides the pack sample rate.  The previous pack
used 22.05 kHz because the Splendid bank had almost nothing above 4 kHz; if this
bank is different, that choice has to be revisited.

    python3 tools/pianopack2/spectrum.py --zip /workspace/nord/samples.zip
"""
from __future__ import annotations

import argparse
import io
import zipfile

import numpy as np

BANDS = [(0, 1000), (1000, 3000), (3000, 6000), (6000, 11000), (11000, 16000), (16000, 22000)]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", default="/workspace/nord/samples.zip")
    ap.add_argument("--names", default="C2v9,C3v9,C4v1,C4v9,C4v16,C5v9,C6v9,C7v9")
    a = ap.parse_args()

    import soundfile as sf
    z = zipfile.ZipFile(a.zip)

    print("fraction of energy per band, on the sustain (after 0.3 s)")
    print("  sample     " + "  ".join(f"{lo//1000}-{hi//1000}k" for lo, hi in BANDS))
    for nm in a.names.split(","):
        try:
            with z.open(f"samples/{nm}.flac") as fh:
                x, sr = sf.read(io.BytesIO(fh.read()), always_2d=True)
        except KeyError:
            print(f"  {nm:<10} missing")
            continue
        m = x.mean(axis=1)
        m = m[int(0.3 * sr):int(2.3 * sr)]
        n = 1 << 18
        w = np.hanning(len(m))
        S = np.abs(np.fft.rfft(m * w, n)) ** 2
        f = np.fft.rfftfreq(n, 1 / sr)
        tot = S.sum() + 1e-30
        frac = []
        for lo, hi in BANDS:
            k = (f >= lo) & (f < hi)
            frac.append(100.0 * S[k].sum() / tot)
        print(f"  {nm:<10} " + "  ".join(f"{v:5.1f}%" for v in frac))


if __name__ == "__main__":
    main()
