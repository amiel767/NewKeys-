#!/usr/bin/env python3
"""Simulate the engine's held-note playback and measure the "echo".

The engine plays a region, and while the key is held it wraps pos_ back by
(loopEnd - loopStart) with no crossfade.  A loop that still decays, or a seam that
jumps, makes the listener hear the note re-attack: the "echo".

This renders 4 seconds of held note exactly as the engine does (same decode, same
wrap) and reports:

  * modulation  = peak-to-peak of the envelope, in dB.  A steady sustain is < 1 dB;
    a re-attack is several dB.
  * reattack    = how much the envelope rises right after each wrap, in dB.
  * click       = |sample jump| at the wrap, relative to RMS.

Usage: python3 simulate_echo.py <pack.bin> [n_regions]
"""
import struct
import sys

import numpy as np

STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
        45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190,
        209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724,
        796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
        2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132,
        7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
        22385, 24623, 27086, 29794, 32767]
IDX = [-1, -1, -1, -1, 2, 4, 6, 8]


def decode_region(pcm, offset, frames):
    pred = [0, 0]
    idx = [0, 0]
    out = np.empty((frames, 2), np.int16)
    base = offset * 2
    for i in range(frames):
        for ch in (0, 1):
            n = base + i * 2 + ch
            b = pcm[n >> 1]
            code = (b & 0xF) if (n & 1) == 0 else (b >> 4)
            st = STEP[idx[ch]]
            dl = st >> 3
            if code & 4:
                dl += st
            if code & 2:
                dl += st >> 1
            if code & 1:
                dl += st >> 2
            pred[ch] = pred[ch] - dl if code & 8 else pred[ch] + dl
            pred[ch] = 32767 if pred[ch] > 32767 else (-32768 if pred[ch] < -32768 else pred[ch])
            idx[ch] = min(88, max(0, idx[ch] + IDX[code & 7]))
            out[i, ch] = pred[ch]
    return out


def envelope(x, sr, win_ms=25):
    w = max(8, int(sr * win_ms / 1000))
    n = len(x) // w
    return np.sqrt((x[:n * w].reshape(n, w) ** 2).mean(axis=1)) + 1e-9, w


def held_note(x, ls, le, seconds, sr):
    """Render as the engine does: play to loopEnd, wrap, repeat."""
    if le <= ls or le >= len(x):
        return x[:int(seconds * sr)]
    out = [x[:le]]
    total = le
    target = int(seconds * sr)
    while total < target:
        seg = x[ls:le]
        out.append(seg)
        total += len(seg)
    return np.concatenate(out)[:target]


def main():
    path = sys.argv[1]
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 12
    raw = open(path, 'rb').read()
    sr = struct.unpack_from('<I', raw, 8)[0]
    count = struct.unpack_from('<I', raw, 12)[0]
    pcm_len = struct.unpack_from('<I', raw, 16)[0]
    pcm = raw[28 + count * 24:28 + count * 24 + pcm_len]
    mods, reatt, clicks = [], [], []
    rows = []
    for i in range(count):
        r = raw[28 + i * 24:28 + (i + 1) * 24]
        ls = struct.unpack_from('<I', r, 12)[0]
        le = struct.unpack_from('<I', r, 16)[0]
        frames = struct.unpack_from('<I', r, 8)[0]
        off = struct.unpack_from('<I', r, 4)[0]
        if le <= ls or le >= frames:
            continue
        d = decode_region(pcm, off, frames)
        x = d.mean(axis=1).astype(float)
        y = held_note(x, ls, le, 6.0, sr)
        # Measure only AFTER the first pass: the natural decay from the attack to
        # the loop point is not the defect we are chasing.  The "echo" lives in the
        # repeated loop, so the window starts at the first wrap.
        env, w = envelope(y[le:int(5.5 * sr)], sr)
        if env.size < 8:
            continue
        db = 20 * np.log10(env)
        mod = np.percentile(db, 95) - np.percentile(db, 5)
        # Slope of the envelope inside the looped portion, in dB/s.  A steady
        # sustain is ~0; a loop that still decays swells once per pass.
        ts = np.arange(len(db)) * (w / sr)
        slope = float(np.polyfit(ts, db, 1)[0])
        # Mean step at each wrap: mean(50 ms after) - mean(50 ms before).
        n = max(1, int(0.05 * sr) // w)
        L = le - ls
        steps = []
        p = le
        while p + L <= len(y) - w * n:
            i = p // w
            if i - n >= 0 and i + n < env.size:
                steps.append(np.mean(db[i:i + n]) - np.mean(db[i - n:i]))
            p += L
        step = float(np.mean(steps)) if steps else 0.0
        # The engine wraps from le back to ls, so the audible join is between
        # x[le-1] and x[ls], not at the tail boundary.
        rms = np.sqrt((x[ls:le] ** 2).mean()) + 1e-9
        click = abs(x[ls] - x[le - 1]) / rms
        mods.append(mod); reatt.append(slope); clicks.append(click)
        rows.append((mod, slope, click, r[0], r[1], ls, le, step))
    print(f"{path}: {len(rows)} loop regions, sr={sr}")
    print(f"  envelope modulation (p95-p5): med={np.median(mods):5.2f} dB "
          f"max={max(mods):5.2f}  (<1 dB = steady, >3 dB = heard as re-attack)")
    print(f"  loop slope (envelope drift): med={np.median(reatt):+6.2f} dB/s "
          f"max|.|={max(abs(x) for x in reatt):5.2f}")
    print(f"  seam click                 : med={np.median(clicks):5.3f} "
          f"max={max(clicks):5.3f}  (>0.5 = audible)")
    for m, sl, c, root, lay, ls, le, st in sorted(rows, key=lambda z: -z[0])[:limit]:
        print(f"    root={root:3d} lay={lay} mod={m:5.2f} dB slope={sl:+6.2f} dB/s "
              f"step={st:+5.2f} dB click={c:5.3f}")


if __name__ == '__main__':
    main()
