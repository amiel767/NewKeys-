"""Turn the raw demo render into a per-take-normalised MP3 and report levels.

Each take is normalised to the same RMS. Two reasons: the raw render bypasses the
app's master limiter (the instrument on its own runs hot), and a listening
comparison of timbres is only fair if the nine takes sit at the same loudness.
Original levels are printed before normalisation.
"""
import sys
import numpy as np
import lameenc

SRC = "/tmp/demo/raw.f32"
OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/demo/piano.mp3"
KBPS = int(sys.argv[2]) if len(sys.argv) > 2 else 80
SR = 44100
TARGET_RMS = 0.16

x = np.fromfile(SRC, dtype="<f4").reshape(-1, 2)
names = [l.split("\t")[0] for l in open("/tmp/demo/names.txt").read().strip().split("\n")]
n = len(names)
each = len(x) // n

print("total %.1f s, %d prises" % (len(x) / SR, n))
out = []
for k, name in enumerate(names):
    seg = x[k * each:(k + 1) * each]
    live = np.abs(seg).max(axis=1) > 1e-4
    if not live.any():
        print("  %-10s SILENCE" % name)
        out.append(seg)
        continue
    a = int(live.argmax())
    body = seg[a:]
    rms = float(np.sqrt((body ** 2).mean()))
    peak = float(np.abs(body).max())

    # Timbre is measured on the sustain: the attack is broadband and identical
    # across presets, so including it hides the differences between them.
    off = a + int(0.30 * SR)
    win = seg[off:off + (1 << 15), 0]
    spec = np.abs(np.fft.rfft(win * np.hanning(len(win))))
    freqs = np.fft.rfftfreq(len(win), 1 / SR)
    e = spec ** 2
    tot = max(e.sum(), 1e-12)
    cent = float((e * freqs).sum() / tot)
    hi = float(e[freqs > 5000].sum() / tot)
    print("  %-10s rms %.3f  peak %.3f  centr %5.0f Hz  >5k %4.1f%%"
          % (name, rms, peak, cent, 100 * hi))

    g = TARGET_RMS / max(rms, 1e-9)
    body = body * g
    p = float(np.abs(body).max())
    if p > 0.95:
        body *= 0.95 / p
    out.append(seg[:a])
    out.append(body)

y = np.concatenate(out)
pcm = (np.clip(y, -1, 1) * 32767).astype("<i2")
enc = lameenc.Encoder()
enc.set_bit_rate(KBPS)
enc.set_in_sample_rate(SR)
enc.set_channels(2)
enc.set_quality(3)
data = enc.encode(pcm.tobytes()) + enc.flush()
open(OUT, "wb").write(data)
print("MP3 %s : %.0f Ko (%d kbps, prises normalisees a RMS %.2f)"
      % (OUT, len(data) / 1024, KBPS, TARGET_RMS))
