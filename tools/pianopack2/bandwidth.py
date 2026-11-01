#!/usr/bin/env python3
"""Where does the piano bank actually lose energy?  Decides the pack sample rate.

Two notes in AGENTS.md disagree: one says the bank has 0 % of its energy above
3 kHz (so 22.05 kHz is plenty), the other says 44.1 kHz buys +10 dB of in-band
SNR.  Both can be true if the first measured the wrong thing.  This measures the
source spectrum directly, on several notes and velocity layers, and reports the
energy above a set of cutoffs.

    python3 tools/pianopack2/bandwidth.py
"""
from __future__ import annotations

import io
import zipfile

import numpy as np

from codec import read_flac

ZIP = "/workspace/nord/samples.zip"
# One note per octave, softest and loudest layer: the extremes bracket the band.
NOTES = ["A2v1", "A2v16", "C4v1", "C4v16", "A5v1", "A5v16", "C7v1", "C7v16"]
CUTOFFS = [2000, 3000, 4000, 6000, 8000, 11000, 16000, 20000]


def band_energy_above(x: np.ndarray, sr: int, f: int) -> float:
    """Fraction of total energy above f, averaged over channels."""
    n = 1 << 16
    if x.shape[0] < n:
        n = 1 << int(np.floor(np.log2(x.shape[0])))
    w = np.hanning(n)
    freqs = np.fft.rfftfreq(n, 1 / sr)
    out = []
    for ch in range(x.shape[1]):
        seg = x[:n, ch] * w
        p = np.abs(np.fft.rfft(seg)) ** 2
        tot = p[1:].sum()
        out.append(p[freqs >= f].sum() / tot if tot > 0 else 0.0)
    return float(np.mean(out))


def main() -> None:
    z = zipfile.ZipFile(ZIP)
    names = [n for n in z.namelist() if n.lower().endswith(".flac")]
    # The SFZ points at samples/<name>.flac; find each by suffix.
    print(f"bank: {len(names)} flac files\n")
    print(f"{'note':8s} " + " ".join(f"{c/1000:5.0f}k" for c in CUTOFFS))
    rows = []
    for want in NOTES:
        cand = [n for n in names if n.endswith(f"/{want}.flac") or n.endswith(f"{want}.flac")]
        if not cand:
            print(f"{want:8s} absent")
            continue
        x, sr = read_flac(z, cand[0])
        fracs = [band_energy_above(x, sr, c) for c in CUTOFFS]
        rows.append(fracs)
        print(f"{want:8s} " + " ".join(f"{100*v:5.2f}" for v in fracs))

    if rows:
        mean = np.mean(rows, axis=0)
        print("\n%-8s " % "moyenne" + " ".join(f"{100*v:5.2f}" for v in mean))
        print("\nLecture : pourcentage d'energie au-dessus de la frequence.")
        print("Un pack a 22,05 kHz ne peut rien porter au-dessus de 11 kHz.")


if __name__ == "__main__":
    main()
