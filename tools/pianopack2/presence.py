#!/usr/bin/env python3
"""Does the bank itself have a presence band, or is it dull to begin with?

The complaint is that the piano lacks presence and only differs by reverb.  Two
causes are possible and they need separating: either the bank has nothing in
2-5 kHz (no codec can invent it), or the 4-bit codec destroys what is there.

    python3 tools/pianopack2/presence.py
"""
from __future__ import annotations

import io
import zipfile

import numpy as np

from codec import read_flac, encode, decode, resample

ZIP = "/workspace/nord/samples.zip"
# Loud layer only: presence is what a fortissimo has and a pianissimo does not.
NOTES = ["C4v16", "E4v16", "A4v16", "C5v16", "A5v16"]
EDGES = [500, 1000, 2000, 3000, 5000, 8000]


def band_db(x: np.ndarray, sr: int) -> list[float]:
    n = 1 << 16
    if x.shape[0] < n:
        n = 1 << int(np.floor(np.log2(x.shape[0])))
    w = np.hanning(n)
    freqs = np.fft.rfftfreq(n, 1 / sr)
    acc = np.zeros(len(EDGES) + 1)
    for ch in range(x.shape[1]):
        p = np.abs(np.fft.rfft(x[:n, ch] * w)) ** 2
        tot = p[1:].sum()
        edges = [0] + EDGES + [sr / 2]
        for i in range(len(edges) - 1):
            m = (freqs >= edges[i]) & (freqs < edges[i + 1])
            acc[i] += p[m].sum() / tot
    acc /= x.shape[1]
    return [10 * np.log10(v + 1e-30) for v in acc]


def main() -> None:
    z = zipfile.ZipFile(ZIP)
    names = [n for n in z.namelist() if n.lower().endswith(".flac")]
    labels = [f"{EDGES[i]//1000 if EDGES[i]>=1000 else EDGES[i]}"
              f"-{EDGES[i+1]//1000 if EDGES[i+1]>=1000 else EDGES[i+1]}k"
              for i in range(len(EDGES) - 1)]
    print("source, couche forte, energie par bande en dB du total\n")
    print(f"{'note':8s} {'0-500':>7s} " + " ".join(f"{l:>7s}" for l in labels))
    src_rows = []
    for want in NOTES:
        cand = [n for n in names if n.endswith(f"/{want}.flac")]
        if not cand:
            continue
        x, sr = read_flac(z, cand[0])
        b = band_db(x, sr)
        src_rows.append(b)
        print(f"{want:8s} " + " ".join(f"{v:7.1f}" for v in b))
    src = np.mean(src_rows, axis=0)

    print("\nmeme mesure apres le codec du pack (4 bits, 22,05 kHz)\n")
    print(f"{'note':8s} {'0-500':>7s} " + " ".join(f"{l:>7s}" for l in labels))
    for want in NOTES:
        cand = [n for n in names if n.endswith(f"/{want}.flac")]
        if not cand:
            continue
        x, sr = read_flac(z, cand[0])
        mono = x.mean(axis=1)
        y = resample(mono, sr, 22050)
        y = np.clip(np.round(y * 32767.0), -32768, 32767).astype(np.int16)
        st = np.repeat(y, 2)
        dec = decode(encode(st, bits=4), len(st)).astype(np.float64)
        dec = dec.reshape(-1, 2) / 32768.0
        b = band_db(dec, 22050)
        print(f"{want:8s} " + " ".join(f"{v:7.1f}" for v in b))

    print("\nsource, moyenne : " + " ".join(f"{v:7.1f}" for v in src))
    print("Lecture : si la bande 2-3k et 3-5k de la source est deja tres basse,")
    print("le piano est sourd a la base et aucun codec ne peut y remedier.")


if __name__ == "__main__":
    main()
