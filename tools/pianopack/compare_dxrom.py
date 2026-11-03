"""Compare le SF2 « ED DX7 ROMANCE TINES » aux banques EP deja embarquees.

Question tranchee : ce son est-il un « Tine » (modele 8) ou un « DX » (modele 10) ?
On mesure le timbre, pas le nom : centroide, energie >5 kHz, et surtout la
DECROISSANCE (un tine s'amortit vite, un DX cloche tient).

Le PIAP n'a pas de JSON pour les ep_*.bin : on relit l'en-tete et les
enregistrements de regions (24 octets) directement dans le .bin.
"""
import json
import os
import sys

import numpy as np
import soundfile as sf

REPO = "."
STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
        45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190,
        209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724,
        796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
        2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132,
        7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
        22385, 24623, 27086, 29794, 32767]
INDEX = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]


def adpcm_decode(blob, offset, frames):
    out = np.zeros((frames, 2), dtype=np.float64)
    pred = [0, 0]
    idx = [0, 0]
    for i in range(frames):
        b = blob[offset + i]
        for ch in range(2):
            code = (b & 0x0F) if ch == 0 else ((b >> 4) & 0x0F)
            st = STEP[idx[ch]]
            d = st >> 3
            if code & 4:
                d += st
            if code & 2:
                d += st >> 1
            if code & 1:
                d += st >> 2
            pred[ch] += -d if (code & 8) else d
            pred[ch] = max(-32768, min(32767, pred[ch]))
            idx[ch] = max(0, min(88, idx[ch] + INDEX[code & 7]))
            out[i, ch] = pred[ch]
    return out


def read_piap(path):
    b = open(path, "rb").read()
    assert b[:4] == b"PIAP", path
    rate = int.from_bytes(b[8:12], "little")
    nrec = int.from_bytes(b[12:16], "little")
    base = 28
    regions = []
    for k in range(nrec):
        rec = b[base + k * 24: base + k * 24 + 24]
        root = rec[0]
        layer = rec[1]
        off = int.from_bytes(rec[4:8], "little")
        frames = int.from_bytes(rec[8:12], "little")
        regions.append((root, layer, off, frames))
    return rate, regions, b


def timbre(x, sr):
    """(centroide Hz, %energie>5k, T-20dB s, attaque/sustain)."""
    if x.ndim > 1:
        x = x.mean(axis=1)
    x = x / (np.max(np.abs(x)) + 1e-9)
    seg = x[int(0.05 * sr):int(0.5 * sr)]
    if len(seg) < 2048:
        seg = x
    w = np.hanning(len(seg))
    X = np.abs(np.fft.rfft(seg * w))
    f = np.fft.rfftfreq(len(seg), 1 / sr)
    cen = float((X * f).sum() / (X.sum() + 1e-12))
    hi = float((X[f > 5000] ** 2).sum() / ((X ** 2).sum() + 1e-12))
    env = np.abs(x)
    k = max(1, int(0.005 * sr))
    env = np.convolve(env, np.ones(k) / k, "same")
    pk = env.max()
    idx = np.where(env < pk * 0.1)[0]
    t20 = idx[0] / sr if len(idx) else len(x) / sr
    return cen, hi, t20


def report(name, samples):
    """samples : liste de (root, x, sr). On resume sur les notes medianes."""
    c, h, t = [], [], []
    for root, x, sr in samples:
        cc, hh, tt = timbre(x, sr)
        c.append(cc)
        h.append(hh)
        t.append(tt)
    return (np.median(c), np.median(h) * 100, np.median(t), len(samples))


print("=== SF2 ED DX7 ROMANCE TINES ===")
m = json.load(open("/tmp/dxrom_out/mapping.json"))
byroot = {}
for e in m:
    if e["root"] < 24 or e["root"] > 108:
        continue
    byroot.setdefault(e["root"], []).append(e)
sf2_samples = []
for r in sorted(byroot):
    e = max(byroot[r], key=lambda z: z["n"])
    x, sr = sf.read(os.path.join("/tmp/dxrom_out", e["file"]))
    sf2_samples.append((r, x, sr))
cen, hi, t20, n = report("sf2", sf2_samples)
print("  median: centroide %.0f Hz | >5k %.2f%% | T-20dB %.2f s | %d notes" % (cen, hi, t20, n))

print()
for label, path in [("Tine Keys (m8)", "app/src/main/assets/pianopack/ep_tine.bin"),
                    ("Ballad (m9)", "app/src/main/assets/pianopack/ep_ballad.bin"),
                    ("DX Bell (m10)", "app/src/main/assets/pianopack/ep_dx.bin"),
                    ("E.Piano (m11)", "app/src/main/assets/pianopack/ep_piano.bin")]:
    rate, regions, blob = read_piap(path)
    samples = []
    for root, layer, off, frames in regions:
        if root < 24 or root > 108:
            continue
        if layer != max(r[1] for r in regions if r[0] == root):
            continue
        pcm = adpcm_decode(blob, 28 + int.from_bytes(blob[20:24], "little") + off, frames)
        samples.append((root, pcm, rate))
    cen, hi, t20, n = report(label, samples)
    print("  %-14s centroide %.0f Hz | >5k %.2f%% | T-20dB %.2f s | %d notes"
          % (label, cen, hi, t20, n))
