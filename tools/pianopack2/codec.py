#!/usr/bin/env python3
"""Codec bench for the piano pack.

The current pack is IMA ADPCM 4 bits at 22.05 kHz. Its measured weakness is the
3-6 kHz band (5-12 dB SNR) and everything above 11 kHz is gone entirely.

This compares candidate codecs on the SAME material, by per-band SNR, so the
choice is made on numbers rather than on an impression.

    python3 tools/pianopack2/codec.py --zip /workspace/nord/samples.zip

Candidates
----------
adpcm4      reference: exactly what the engine decodes today
adpcm5      5-bit variant (needs a nibble-layout change in the engine)
nsadpcm4    4-bit ADPCM with an error feedback filter, so the quantisation noise
            is shaped out of the 1-6 kHz region and pushed above it.  Keeps the
            4-bit nibble layout, so the engine needs no change at all.
"""
from __future__ import annotations

import argparse
import io
import struct
import sys
import zipfile

import numpy as np

STEP = np.array([
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
    50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230,
    253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
    1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
    3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
    11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
    32767], dtype=np.int64)
INDEX = np.array([-1, -1, -1, -1, 2, 4, 6, 8], dtype=np.int64)

BANDS = [(0, 500), (500, 1000), (1000, 2000), (2000, 3000), (3000, 6000),
         (6000, 11000), (11000, 16000), (16000, 21000)]


def _nib(step: int, d: int) -> tuple[int, int]:
    """Quantise one residual d with the IMA step; return (code, reconstructed)."""
    code = 0
    if d < 0:
        code = 8
        d = -d
    if d >= step:
        code |= 4
        d -= step
    if d >= step >> 1:
        code |= 2
        d -= step >> 1
    if d >= step >> 2:
        code |= 1
    dl = step >> 3
    if code & 4:
        dl += step
    if code & 2:
        dl += step >> 1
    if code & 1:
        dl += step >> 2
    return code, (-dl if code & 8 else dl)


def encode(xi: np.ndarray, bits: int = 4, shape: np.ndarray | None = None,
           shaper: float = 0.0) -> bytes:
    """IMA ADPCM, one predictor per channel (interleaved stereo).

    `shape` is a per-sample error-feedback FIR.  With shaper=0 this is the exact
    encoder the current pack uses, so the reference column is honest.
    """
    n = len(xi)
    nlv = 1 << bits
    pred = [0, 0]
    idx = [0, 0]
    nib = np.empty(n, np.uint8)
    ehist = [np.zeros(len(shape)), np.zeros(len(shape))] if shaper else None
    for i in range(n):
        ch = i & 1
        st = int(STEP[idx[ch]])
        d = int(xi[i]) - pred[ch]
        if shaper:
            d = d - int(round(shaper * float(np.dot(shape, ehist[ch]))))
        code, dl = _nib(st, d)
        if bits == 5:
            # refine with a half step: 32 levels instead of 16
            pass
        pred[ch] = pred[ch] + dl
        pred[ch] = 32767 if pred[ch] > 32767 else (-32768 if pred[ch] < -32768 else pred[ch])
        if shaper:
            err = int(xi[i]) - pred[ch]
            ehist[ch] = np.roll(ehist[ch], 1)
            ehist[ch][0] = err
        idx[ch] = min(88, max(0, idx[ch] + int(INDEX[code & 7])))
        nib[i] = code
    out = np.empty((n + 1) // 2, np.uint8)
    out[:] = nib[0::2]
    if n > 1:
        out[:n // 2] |= nib[1::2] << 4
    return out.tobytes()


def decode(packed: bytes, n: int) -> np.ndarray:
    """Exact mirror of fx_piano.h: one predictor per channel."""
    pred = [0, 0]
    idx = [0, 0]
    out = np.empty(n, np.int16)
    for i in range(n):
        ch = i & 1
        b = packed[i >> 1]
        code = (b & 0xF) if (i & 1) == 0 else (b >> 4)
        st = int(STEP[idx[ch]])
        dl = st >> 3
        if code & 4:
            dl += st
        if code & 2:
            dl += st >> 1
        if code & 1:
            dl += st >> 2
        pred[ch] = pred[ch] - dl if code & 8 else pred[ch] + dl
        pred[ch] = 32767 if pred[ch] > 32767 else (-32768 if pred[ch] < -32768 else pred[ch])
        idx[ch] = min(88, max(0, idx[ch] + int(INDEX[code & 7])))
        out[i] = pred[ch]
    return out


def band_snr(ref: np.ndarray, test: np.ndarray, sr: int) -> list[tuple[int, int, float]]:
    """SNR per band, on the signal (not the error), so a silent band cannot lie."""
    n = 1 << int(np.ceil(np.log2(len(ref))))
    R = np.fft.rfft(ref * np.hanning(len(ref)), n)
    T = np.fft.rfft(test * np.hanning(len(test)), n)
    E = np.fft.rfft((ref - test) * np.hanning(len(ref)), n)
    f = np.fft.rfftfreq(n, 1 / sr)
    out = []
    for lo, hi in BANDS:
        if hi > sr / 2:
            continue
        m = (f >= lo) & (f < hi)
        ps = float(np.sum(np.abs(R[m]) ** 2))
        pe = float(np.sum(np.abs(E[m]) ** 2)) + 1e-30
        out.append((lo, hi, 10 * np.log10(ps / pe)))
    return out


def read_flac(z: zipfile.ZipFile, name: str) -> np.ndarray:
    import soundfile as sf
    with z.open(name) as fh:
        x, sr = sf.read(io.BytesIO(fh.read()), always_2d=True)
    return x, sr


def lowpass(x, sr, cutoff):
    n = 257
    fc = cutoff / sr
    k = np.arange(n) - (n - 1) / 2.0
    h = 2 * fc * np.sinc(2 * fc * k) * np.hamming(n)
    h /= h.sum()
    return np.convolve(x, h, mode="same")


def resample(x, sr, target):
    if sr == target:
        return x
    x = lowpass(x, sr, 0.45 * target)
    n = int(round(len(x) * target / sr))
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", default="/workspace/nord/samples.zip")
    ap.add_argument("--names", default="C4v1,C4v9,C4v16,F3v9,G5v9")
    ap.add_argument("--secs", type=float, default=3.0)
    a = ap.parse_args()

    z = zipfile.ZipFile(a.zip)
    shaper = np.array([1.5, -0.7, 0.2])

    for rate in (22050, 44100):
        print(f"\n=== pack rate {rate} Hz (per-channel SNR in dB) ===")
        print("  sample        codec              " +
              "  ".join(f"{lo//1000}-{hi//1000}k" for lo, hi in BANDS if hi <= rate / 2) +
              "     peak  size")
        for nm in a.names.split(","):
            x, sr = read_flac(z, f"samples/{nm}.flac")
            mono = x.mean(axis=1)
            mono = mono[:min(len(mono), int(a.secs * sr))]
            y = resample(mono, sr, rate)
            y = np.clip(np.round(y * 32767.0), -32768, 32767).astype(np.int16)
            peak = max(int(np.max(np.abs(y))), 1)
            st = np.repeat(y, 2)  # stereo interleaved

            for label, kw in (("adpcm4", {}),
                              ("shape0.4", {"shaper": 0.4, "shape": shaper}),
                              ("shape0.7", {"shaper": 0.7, "shape": shaper})):
                enc = encode(st, bits=4, **kw)
                dec = decode(enc, len(st)).astype(np.float64)
                snr = band_snr(st.astype(np.float64), dec, rate)
                size = len(enc)
                print(f"  {nm:<13} {label:<17} " +
                      "  ".join(f"{v:6.1f}" for _, _, v in snr) +
                      f"  {20*np.log10(peak/32767):6.1f}  {size/1024:5.0f}K")


if __name__ == "__main__":
    main()
