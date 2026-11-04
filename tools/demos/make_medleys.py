#!/usr/bin/env python3
"""Assemble les medleys (12 pianos, 5 sax) depuis les .f32 de demo.

    python3 tools/demos/make_medleys.py <src_dir_f32> <dst_dir_mp3>

Chaque prise est normalisee au meme crete avant concatenation (comparaison de
timbres a sonie egale), avec un silence de 0,6 s entre les instruments.
"""
import glob
import os
import sys

import lameenc
import numpy as np

SR = 44100
GAP = 0.6


def encode(x, path, kbps=192):
    pcm = (np.clip(x, -1.0, 1.0) * 32767).astype("<i2")
    enc = lameenc.Encoder()
    enc.set_bit_rate(kbps)
    enc.set_in_sample_rate(SR)
    enc.set_channels(2)
    enc.set_quality(2)
    with open(path, "wb") as out:
        out.write(enc.encode(pcm.tobytes()) + enc.flush())


def medley(files, out):
    gap = np.zeros((int(GAP * SR), 2), dtype="<f4")
    segs = []
    for f in files:
        x = np.fromfile(f, dtype="<f4").reshape(-1, 2)
        peak = float(np.abs(x).max()) or 1.0
        segs += [x / peak * 0.89, gap]
    y = np.concatenate(segs)
    encode(y, out)
    print("%-40s %.1f s" % (out, len(y) / SR))


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(dst, exist_ok=True)
    medley(sorted(glob.glob(os.path.join(src, "piano_*.f32"))),
           os.path.join(dst, "_MEDLEY_pianos.mp3"))
    medley(sorted(glob.glob(os.path.join(src, "sax_*.f32"))),
           os.path.join(dst, "_MEDLEY_sax.mp3"))


if __name__ == "__main__":
    main()
