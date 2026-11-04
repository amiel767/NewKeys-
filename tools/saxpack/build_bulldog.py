"""Construit un pack SAXP depuis les samples Bulldog Sax 2 (36 notes EXP1).

  python3 tools/saxpack/build_bulldog.py "/chemin/BULLDOG SAX 2/Instruments/Bulldog Sax 2 Samples"

Les EXP1 (`NN-EXP1.wav`, 44,1 kHz stereo 24 bits) sont 36 notes CHROMATIQUES
consecutives (02 -> MIDI 54, +1 demi-ton par fichier, verifie par mesure de
hauteur).  On prend le canal gauche, on descend a 22050 Hz, on normalise en RMS,
on place une boucle dans la partie TENUE (plate) et on encode en ADPCM IMA.

Le lecteur natif (fx_sax_samples.h) choisit la region par `root` le plus proche
et transpose ; les 36 racines donnent donc une couverture exacte sans transposition.
"""
import argparse
import os
import struct
import sys
import wave
from pathlib import Path

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract import encode_adpcm, snap_loop_end  # noqa: E402

TARGET_RATE = 22050
TARGET_RMS = 0.20
PEAK_CEIL = 0.95
# 02-EXP1 = MIDI 54 (mesure : 184,5 Hz).  Les fichiers suivants montent d'un demi-ton.
MIDI_BASE = 54


def read_wav_mono(path: str) -> tuple[np.ndarray, int]:
    w = wave.open(path, "rb")
    rate, ch, sw = w.getframerate(), w.getnchannels(), w.getsampwidth()
    raw = w.readframes(w.getnframes())
    if sw == 3:
        a = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3)
        x = (a[:, 0].astype(np.int32)
             | (a[:, 1].astype(np.int32) << 8)
             | (a[:, 2].astype(np.int8).astype(np.int32) << 16))
        x = x.astype(np.float64) / float(2 ** 23)
    elif sw == 2:
        x = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768.0
    else:
        x = np.frombuffer(raw, dtype="<i4").astype(np.float64) / float(2 ** 31)
    if ch > 1:
        x = x[::ch]
    return x, rate


def resample(x: np.ndarray, src: int, dst: int) -> np.ndarray:
    if src == dst:
        return x
    from math import gcd
    from scipy.signal import resample_poly
    g = gcd(src, dst)
    return resample_poly(x, dst // g, src // g).astype(np.float64)


def seam_score(x: np.ndarray, ls: int, le: int, w: int) -> float:
    a = x[ls:ls + w] - x[ls:ls + w].mean()
    b = x[le:le + w] - x[le:le + w].mean()
    d = float(np.sqrt((a * a).sum() * (b * b).sum()))
    return float((a * b).sum() / d) if d > 1e-12 else 0.0


def hold_loop(x: np.ndarray, rate: int) -> tuple[int, int]:
    """Place (loopStart, loopEnd) dans la partie tenue.

    Un raccord continu demande que la forme d'onde en `loopEnd` reprenne la phase
    de `loopStart`.  On part donc d'une longueur candidate (~3 periodes) et on
    cherche, pour plusieurs longueurs, le `loopEnd` qui maximise la correlation du
    raccord (seam).  Une longueur trop grande (0,35 s) n'a presque jamais de phase
    alignee : mesure sur Bulldog, seam median 0,98 (mauvais) contre 0,03 pour les
    packs MTG.  On garde la meilleure des candidates.
    """
    n = len(x)
    win = int(0.02 * rate)
    if n < 4 * win:
        return 1, max(2, n - 1)
    env = np.array([np.sqrt((x[k:k + win] ** 2).mean()) for k in range(0, n - win, win)])
    peak = env.max() + 1e-12
    below = np.where(env < 0.80 * peak)[0]
    start = (below[0] * win) if len(below) else int(0.5 * rate)
    start = max(start, int(0.25 * rate))
    # periode estimee (autocorrelation) pour viser un nombre ENTIER de periodes
    seg = x[start:start + int(0.15 * rate)]
    ac = np.correlate(seg, seg, "full")[len(seg) - 1:]
    fmin, fmax = int(rate / 1200), int(rate / 90)
    per = int(np.argmax(ac[fmin:fmax]) + fmin) if fmax < len(ac) else 128
    w = min(2048, max(128, per * 2))
    best = None
    for kper in (2, 3, 4, 6, 8, 12):
        ls = start
        le = min(n - w - 2, ls + max(w, per * kper))
        if le - ls < 64:
            continue
        le = snap_loop_end(x, ls, le, span=max(384, per))
        s = seam_score(x, ls, le, w)
        if best is None or s > best[0]:
            best = (s, ls, le)
    if best is None:
        return 1, max(2, n - 1)
    return max(1, best[1]), max(best[1] + 2, min(best[2], n - 1))


def build(samples_dir: str, out_dir: str) -> int:
    rows, pcm = [], bytearray()
    files = sorted(f for f in os.listdir(samples_dir) if f.lower().endswith("-exp1.wav"))
    if not files:
        print("aucun fichier *-EXP1.wav trouve", file=sys.stderr)
        return 1
    for i, name in enumerate(files):
        x, rate = read_wav_mono(os.path.join(samples_dir, name))
        x = resample(x, rate, TARGET_RATE)
        x = x - float(x.mean())
        rms = float(np.sqrt(np.mean(x ** 2)))
        peak = float(np.max(np.abs(x)))
        gain = TARGET_RMS / rms if rms > 1e-9 else 1.0
        if peak * gain > PEAK_CEIL:
            gain = PEAK_CEIL / peak
        x = x * gain
        ls, le = hold_loop(x, TARGET_RATE)
        data = encode_adpcm(x)
        rows.append({
            "root": MIDI_BASE + i, "layer": 1, "offset": len(pcm),
            "frames": len(x), "ls": ls, "le": le, "cents": 0.0,
        })
        pcm += data
        print("  %-14s root=%2d  %.2fs  loop %.2f-%.2f s" %
              (name, MIDI_BASE + i, len(x) / TARGET_RATE, ls / TARGET_RATE, le / TARGET_RATE))

    rec = b"".join(struct.pack("<BBBBIIIIf", r["root"] & 0xFF, r["layer"], 0, 0,
                               r["offset"], r["frames"], r["ls"], r["le"], r["cents"])
                   for r in rows)
    header = struct.pack("<4sIIIIII", b"SAXP", 1, TARGET_RATE, len(rows), len(pcm), len(rec), 0)
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, "saxpack_bulldog.bin")
    Path(out).write_bytes(header + rec + bytes(pcm))
    print("-> %s : %d regions, %d octets" % (out, len(rows), len(header) + len(rec) + len(pcm)))
    return 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("samples_dir")
    ap.add_argument("--out", default="app/src/main/assets/saxpack")
    a = ap.parse_args()
    sys.exit(build(a.samples_dir, a.out))
