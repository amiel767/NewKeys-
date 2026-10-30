"""Encode one MP3 per piano model.

Each model gets its own file, so it can be listened to on its own rather than inside a
medley where the ear loses its reference. Every take is normalised to the same RMS: the
raw render bypasses the app's master limiter, and a comparison of timbres is only fair if
the nine sit at the same loudness.
"""
import os
import sys
import numpy as np
import lameenc

SR = 44100
TARGET_RMS = 0.16
KBPS = int(sys.argv[1]) if len(sys.argv) > 1 else 80
INDIR = "/tmp/demo"
OUTDIR = sys.argv[2] if len(sys.argv) > 2 else "/tmp/demo"
NAMES = ["Felt", "Studio", "Bright", "Mellow", "Dark", "Upright", "Lady D", "Honky", "Electric"]
SLUGS = ["felt", "studio", "bright", "mellow", "dark", "upright", "lady", "honky", "electric"]


def encode(x, path):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    enc = lameenc.Encoder()
    enc.set_bit_rate(KBPS)
    enc.set_in_sample_rate(SR)
    enc.set_channels(2)
    enc.set_quality(3)
    data = enc.encode(pcm.tobytes()) + enc.flush()
    open(path, "wb").write(data)
    return len(data)


total = 0
for name, slug in zip(NAMES, SLUGS):
    src = os.path.join(INDIR, "take_%s.f32" % slug)
    if not os.path.exists(src):
        print("  manquant : %s" % src)
        continue
    x = np.fromfile(src, dtype="<f4").reshape(-1, 2)
    live = np.abs(x).max(axis=1) > 1e-4
    a = int(live.argmax()) if live.any() else 0
    body = x[a:]
    rms = float(np.sqrt((body ** 2).mean()))
    peak = float(np.abs(body).max())

    off = a + int(0.30 * SR)
    win = x[off:off + (1 << 15), 0]
    if len(win) > (1 << 14):
        sp = np.abs(np.fft.rfft(win * np.hanning(len(win))))
        f = np.fft.rfftfreq(len(win), 1 / SR)
        e = sp ** 2
        cent = float((e * f).sum() / max(e.sum(), 1e-12))
        hi = float(e[f > 5000].sum() / max(e.sum(), 1e-12))
    else:
        cent = hi = 0.0

    g = TARGET_RMS / max(rms, 1e-9)
    b = body * g
    p = float(np.abs(b).max())
    if p > 0.95:
        b *= 0.95 / p
    y = np.concatenate([x[:a], b])
    n = encode(y, os.path.join(OUTDIR, "%s.mp3" % slug))
    total += n
    print("  %-9s %5.1f s  rms %.3f peak %.3f  centr %5.0f Hz  >5k %4.1f%%  %4.0f Ko"
          % (name, len(y) / SR, rms, peak, cent, 100 * hi, n / 1024))
print("total 9 MP3 : %.0f Ko" % (total / 1024))
