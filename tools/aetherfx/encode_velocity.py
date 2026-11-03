"""Encode les prises de demo velocite en MP3, normalisees au meme RMS.

La demo brute contourne le limiteur master de l'app : comparer des timbres n'est
juste que si les prises sont au meme niveau. On normalise donc au RMS cible, avec
un plafond dur a 0.99 pour ne pas ecreter.
"""
import os
import sys

import lameenc
import numpy as np

SR = 44100
TARGET_RMS = 0.16
KBPS = 96
INDIR = sys.argv[1] if len(sys.argv) > 1 else "/tmp/vel"
OUTDIR = sys.argv[2] if len(sys.argv) > 2 else INDIR
TAKES = ["vel_scale", "vel_chord", "vel_repeat"]


def encode(x, path):
    pcm = (np.clip(x, -1.0, 1.0) * 32767).astype("<i2")
    enc = lameenc.Encoder()
    enc.set_bit_rate(KBPS)
    enc.set_in_sample_rate(SR)
    enc.set_channels(2)
    enc.set_quality(3)
    data = enc.encode(pcm.tobytes()) + enc.flush()
    with open(path, "wb") as f:
        f.write(data)
    return len(data)


for name in TAKES:
    src = os.path.join(INDIR, name + ".f32")
    if not os.path.exists(src):
        print("  manquant : %s" % src)
        continue
    x = np.fromfile(src, dtype="<f4").reshape(-1, 2)
    rms = float(np.sqrt(np.mean(x.astype(np.float64) ** 2)))
    gain = TARGET_RMS / max(rms, 1e-6)
    x = x * gain
    peak = float(np.max(np.abs(x)))
    if peak > 0.99:
        x = x * (0.99 / peak)
    out = os.path.join(OUTDIR, name + ".mp3")
    n = encode(x, out)
    print("  %-11s %5.1f s  rms %.3f  crete %.3f  %4d Ko"
          % (name, x.shape[0] / SR, float(np.sqrt(np.mean(x.astype(np.float64) ** 2))),
             float(np.max(np.abs(x))), n // 1024))
