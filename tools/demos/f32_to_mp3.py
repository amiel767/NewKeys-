#!/usr/bin/env python3
"""Convertit les demos .f32 (PCM stereo 44,1 kHz) en MP3 normalise -1 dBFS.

    python3 tools/demos/f32_to_mp3.py <src_dir_f32> <dst_dir_mp3>

Sert a regenerer tools/demos/audio/ apres un changement du moteur.
"""
import glob
import os
import sys

import lameenc
import numpy as np


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(dst, exist_ok=True)
    n = 0
    for f in sorted(glob.glob(os.path.join(src, "*.f32"))):
        x = np.fromfile(f, dtype="<f4").reshape(-1, 2)
        peak = float(np.abs(x).max()) or 1.0
        pcm = (np.clip(x / peak * 0.89, -1.0, 1.0) * 32767).astype("<i2")
        enc = lameenc.Encoder()
        enc.set_bit_rate(192)
        enc.set_in_sample_rate(44100)
        enc.set_channels(2)
        enc.set_quality(2)
        base = os.path.basename(f)[:-4]
        data = enc.encode(pcm.tobytes()) + enc.flush()
        with open(os.path.join(dst, base + ".mp3"), "wb") as out:
            out.write(data)
        n += 1
    print(f"{n} fichiers -> {dst}")


if __name__ == "__main__":
    main()
