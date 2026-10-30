"""Turn the raw float32 demo render into an MP3 and report per-preset levels.

The render is a flat stream of interleaved stereo float32; each of the 9 takes
occupies the same number of frames, so a take is just a slice.
"""
import sys
import numpy as np
import lameenc

SRC = "/tmp/demo/raw.f32"
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/demo/piano.mp3"
KBPS = int(sys.argv[2]) if len(sys.argv) > 2 else 96

x = np.fromfile(SRC, dtype="<f4").reshape(-1, 2)
names = [l.split("\t")[0] for l in open("/tmp/demo/names.txt").read().strip().split("\n")]
n = len(names)
each = len(x) // n

print("total %.1f s, %d prises" % (len(x) / 44100, n))
for k, name in enumerate(names):
    seg = x[k * each:(k + 1) * each]
    # Skip the leading silence before the first attack so the numbers describe the take.
    mono = np.abs(seg).max(axis=1)
    live = mono > 1e-4
    if not live.any():
        print("  %-10s SILENCE" % name)
        continue
    seg = seg[live.argmax():]
    rms = float(np.sqrt((seg ** 2).mean()))
    peak = float(np.abs(seg).max())
    clip = int((np.abs(seg) >= 0.999).sum())
    # Spectral centroid over the sustain, a proxy for "how bright it sounds".
    # Two presets that measure the same here are not actually different.
    # Skip the attack: its broadband click dominates the centroid and is identical
    # across presets, which is why a whole-take window measured the same for all 9.
    off = int(0.30 * 44100)
    win = seg[:, 0][off:off + (1 << 15)]
    spec = np.abs(np.fft.rfft(win * np.hanning(len(win))))
    freqs = np.fft.rfftfreq(len(win), 1 / 44100)
    centroid = float((spec * freqs).sum() / max(spec.sum(), 1e-9))
    e = spec ** 2
    tot = max(e.sum(), 1e-12)
    hi = float(e[freqs > 5000].sum() / tot)
    print("  %-10s rms %.3f  peak %.3f  ecretes %6d  centr %5.0f Hz  >5k %4.1f%%"
          % (name, rms, peak, clip, centroid, 100 * hi))

# Soft ceiling: keep the comparison honest without letting a hot take clip the encoder.
peak = float(np.abs(x).max())
if peak > 0.98:
    x = x * (0.98 / peak)

pcm = (np.clip(x, -1.0, 1.0) * 32767.0).astype("<i2")
enc = lameenc.Encoder()
enc.set_bit_rate(KBPS)
enc.set_in_sample_rate(44100)
enc.set_channels(2)
enc.set_quality(3)
data = enc.encode(pcm.tobytes()) + enc.flush()
open(OUT, "wb").write(data)
print("MP3 %s : %.0f Ko (%d kbps)" % (OUT, len(data) / 1024, KBPS))
