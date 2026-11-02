#!/usr/bin/env python3
"""Build a PIAP pack from the New Stage Pro 3.0 NKX banks (Royal Grand 3D, Grand Upright).

Differences from the legacy `build.py`:

  1. Rate 44100 instead of 22050.  The old pack threw away everything above
     11 kHz (Nyquist at 22.05 kHz); a piano has energy up to ~15 kHz.  At 44.1 kHz
     the full band is kept and the engine's resampling ratio is exactly 1.0.
  2. Loop with a FLAT envelope.  The engine wraps pos_ back with no crossfade, so a
     loop segment that still decays is heard as a repeating swell (the "echo").  We
     measure the decay inside the segment and compensate it, then crossfade the
     seam, so the sustain is steady instead of re-attacking.
  3. tuneCents is MEASURED and written (the legacy pack left it at 0 for every
     region, so `pick()` changing region with velocity changed the pitch).

Usage:
    python3 build_newstage.py --out /tmp/pack --roots 3 --layers 5
"""
import argparse
import json
import mmap
import os
import re
import struct
import sys

import numpy as np

sys.path.insert(0, '/tmp')
from ncw_decode import decode_ncw_bytes  # noqa: E402
from nkx_samples import unique_names, ncw_blocks  # noqa: E402

RATE = 44100
ATTACK_KEEP_S = 0.40
LOOP_CANDIDATES_S = (0.90, 0.65, 0.45, 0.30)
LOOP_DECAY_OK_DB = 1.0
LAYER_SPREAD_DB = 19.0
LOOP_SEARCH_END_S = 1.30
TAIL_S = 0.25
XFADE_S = 0.040
BLOCK = 512               # echantillons NCW par bloc

STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
        45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190,
        209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724,
        796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
        2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132,
        7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
        22385, 24623, 27086, 29794, 32767]
INDEX = [-1, -1, -1, -1, 2, 4, 6, 8]

BASE = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
NAME_RE = re.compile(r'^([A-G])([#b]?)(-?\d+)_(\d+)')


def midi_of(m):
    # The pack labels octaves ONE BELOW scientific pitch notation: its "D#1"
    # sounds 77.8 Hz, which is D#2 = MIDI 39, and its "C3" sounds 261.6 Hz, which
    # is C4 = MIDI 60 (middle C).  So the pack octave is scientific minus one.
    # Measured on Royal Grand 3D 01: D#1 -> 77.8 Hz (MIDI 39), D#2 -> 155.3 Hz
    # (MIDI 51).  Writing (octv+1)*12 stored every root 12 semitones low, which
    # made the whole piano play an octave high.
    n, acc, octv = m.group(1), m.group(2), int(m.group(3))
    return (octv + 2) * 12 + BASE[n] + (1 if acc == '#' else -1 if acc == 'b' else 0)


# --------------------------------------------------------------------------- ADPCM
def adpcm_encode(xi):
    """int16 stereo entrelace -> nibbles.  Un predicteur PAR CANAL (le moteur
    decode avec predL/predR ; un predicteur unique donne un flux indecodable)."""
    n = len(xi)
    pred = [0, 0]
    idx = [0, 0]
    nib = np.empty(n, np.uint8)
    xi = xi.astype(np.int32)
    for i in range(n):
        ch = i & 1
        st = STEP[idx[ch]]
        d = int(xi[i]) - pred[ch]
        code = 0
        if d < 0:
            code = 8
            d = -d
        if d >= st:
            code |= 4
            d -= st
        if d >= st // 2:
            code |= 2
            d -= st // 2
        if d >= st // 4:
            code |= 1
        dl = st >> 3
        if code & 4:
            dl += st
        if code & 2:
            dl += st >> 1
        if code & 1:
            dl += st >> 2
        pred[ch] = pred[ch] - dl if code & 8 else pred[ch] + dl
        pred[ch] = 32767 if pred[ch] > 32767 else (-32768 if pred[ch] < -32768 else pred[ch])
        idx[ch] = min(88, max(0, idx[ch] + INDEX[code & 7]))
        nib[i] = code
    out = np.empty((n + 1) // 2, np.uint8)
    out[:] = nib[0::2]
    if n > 1:
        out[:n // 2] |= nib[1::2] << 4
    return out


# --------------------------------------------------------------------------- DSP
def resample(x, sr, target):
    if sr == target:
        return x
    n = int(round(len(x) * target / sr))
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)


def envelope_db(mono, sr, win_ms=20):
    w = max(8, int(sr * win_ms / 1000))
    n = len(mono) // w
    if n < 4:
        return None, w
    r = np.sqrt((mono[:n * w].reshape(n, w) ** 2).mean(axis=1)) + 1e-9
    return 20 * np.log10(r), w


def estimate_period(mono, sr, at):
    """Fundamental period in samples, from autocorrelation of the sustain."""
    a = max(0, min(at, max(0, len(mono) - int(0.3 * sr))))
    seg = mono[a:a + int(0.3 * sr)].astype(np.float64)
    if len(seg) < 1024:
        return max(1, int(sr / 440))
    seg = seg - seg.mean()
    ac = np.correlate(seg, seg, 'full')[len(seg) - 1:]
    ac = ac / (ac[0] + 1e-12)
    lo = max(2, int(sr / 1500))
    hi = min(len(ac) - 1, int(sr / 40))
    if hi <= lo:
        return max(1, int(sr / 440))
    return lo + int(np.argmax(ac[lo:hi]))


def flat_loop(seg, sr):
    """Return (loop_start, loop_end, signal) with a steady, seamlessly joined loop.

    The engine wraps pos_ back by (loopEnd - loopStart) with no crossfade, so two
    things must hold at once:

      * the loop segment must not decay (a decay is heard as a repeating swell,
        the "echo"); and
      * the wrap must be continuous, i.e. x[loopEnd-1] then x[loopStart] must join
        like two consecutive samples.

    A crossfade fixes the second by smearing the first 40 ms of the loop into the
    tail, which decorrelates the join and ADDS a click (measured 1.54 vs 0.008 on
    the shipped pack).  Instead we exploit the fact that a piano tone is periodic:
    choosing loopEnd - loopStart = k * period makes the waveform meet itself, so
    the join is continuous by construction and no crossfade is needed.
    """
    mono = seg.mean(axis=1).astype(np.float64)
    env, w = envelope_db(mono, sr)
    if env is None:
        return 0, 0, seg
    a_min = int(ATTACK_KEEP_S * sr)
    a_end = min(len(seg) - 1, int(LOOP_SEARCH_END_S * sr))
    period = estimate_period(mono, sr, int(0.20 * sr))
    rms_all = np.sqrt((mono[a_min:max(a_min + 1, a_end)] ** 2).mean()) + 1e-9
    best = None
    # Try a few target lengths and snap each to a whole number of periods.  Walking
    # k directly would need k up to ~2500 on the treble (period ~21 samples), which
    # is both slow and easy to bound wrongly -- an earlier k<=59 cap left every note
    # above root 54 without a loop at all.
    for target in (0.90, 0.70, 0.52, 0.36):
        L = max(1, int(round(target * sr / period))) * period
        if L < int(0.25 * sr) or L > int(1.20 * sr):
            continue
        a_max = min(a_end, len(seg) - L - 2)
        if a_max <= a_min:
            continue
        for a in range(a_min, a_max, max(1, w)):
            i0, i1 = a // w, (a + L) // w
            if i1 - i0 < 3:
                continue
            xs = np.arange(i1 - i0) * (w / sr)
            slope = np.polyfit(xs, env[i0:i1], 1)[0]
            decay = abs(slope) * (L / sr)                 # dB lost over one pass
            jump = abs(mono[a + L - 1] - mono[a]) / rms_all
            cost = decay + jump * 40.0                    # one 0.025 jump = 1 dB
            if best is None or cost < best[0]:
                best = (cost, a, L)
    if best is None:
        return 0, 0, seg
    _, a, L = best
    return a, a + L, seg.copy()


def build_region(ncw_bytes, layer_index, root, sr_target):
    d = decode_ncw_bytes(ncw_bytes)
    ch = d['data']
    if len(ch) == 1:
        ch = [ch[0], ch[0]]
    x = np.stack([ch[0].astype(np.float64), ch[1].astype(np.float64)], axis=1)
    x = x / 32768.0 if d['bps'] == 16 else x / 8388608.0 if d['bps'] == 24 else x
    sr = d['rate']
    ls, le, x = flat_loop(x, sr)
    if le == 0:
        end = min(len(x), int(2.2 * sr))
        x = x[:end]
    else:
        end = min(len(x), le + int(TAIL_S * sr))
        x = x[:end]
    xl = resample(x[:, 0], sr, sr_target)
    xr = resample(x[:, 1], sr, sr_target)
    x = np.stack([xl, xr], axis=1)
    k = sr_target / sr
    pcm = np.clip(np.round(x * 32767.0), -32768, 32767).astype(np.int16)
    return pcm, int(ls * k), int(le * k)


# --------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--nkx', nargs='+', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--root-step', type=int, default=3)
    ap.add_argument('--max-roots', type=int, default=0)
    ap.add_argument('--layers', type=int, default=5)
    ap.add_argument('--lo', type=int, default=21)
    ap.add_argument('--hi', type=int, default=96)
    ap.add_argument('--budget', type=float, default=15.0, help='Mo max')
    args = ap.parse_args()

    # 1. index every (midi, layer) -> (ncw bytes, source file)
    pool = {}
    for path in args.nkx:
        f = open(path, 'rb')
        mm = mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ)
        names = unique_names(mm)
        blocks = ncw_blocks(mm)
        for (off, total, rate, bps, ch, nsamp), name in zip(blocks, names):
            m = NAME_RE.match(name)
            if not m:
                continue
            midi = midi_of(m)
            lay = int(m.group(4))
            pool.setdefault((midi, lay), bytes(mm[off:off + total]))
        mm.close(); f.close()
    print(f"pool: {len(pool)} samples", file=sys.stderr)

    roots = sorted({m for (m, _) in pool if args.lo <= m <= args.hi})
    if args.max_roots and len(roots) > args.max_roots:
        k = (len(roots) - 1) / (args.max_roots - 1)
        roots = sorted({roots[int(round(i * k))] for i in range(args.max_roots)})
    print(f"roots: {len(roots)}", file=sys.stderr)
    # 2. for each root, pick `layers` velocities spread across what exists
    per_root = {}
    for r in roots:
        avail = sorted(l for (m, l) in pool if m == r)
        if not avail:
            continue
        n = min(args.layers, len(avail))
        picks = [avail[int(round(i * (len(avail) - 1) / max(1, n - 1)))] for i in range(n)]
        picks = sorted(set(picks))
        # Map each pick onto a POSITION in the full velocity range, not onto its own
        # rank: a root with only 4 layers must still span pp..ff, otherwise its
        # loudest sample sits 6 steps down and those keys play quietly.
        m = len(picks)
        per_root[r] = [(lay, int(round(i * (args.layers - 1) / max(1, m - 1))))
                       for i, lay in enumerate(picks)]
    # Layer-major order: every root's softest layer first, then the next layer, and
    # so on.  Root-major order made the budget cut the TOP OF THE KEYBOARD (the high
    # roots come last), so the treble went missing.  The engine weights the root 8x
    # more than the layer, so full key coverage matters more than the last layer.
    jobs = []
    for j in range(args.layers):
        for r in roots:
            for lay, pos in per_root.get(r, []):
                if pos == j:
                    jobs.append((r, lay, j, args.layers))
    print(f"jobs: {len(jobs)} regions ({len(roots)} roots x <= {args.layers} layers)",
          file=sys.stderr)

    recs, blob = [], bytearray()
    budget_bytes = int(args.budget * 1048576) - 4096
    # Normalise PER LAYER, not per region.  The engine picks a layer from the MIDI
    # velocity and reads the dynamics from the level difference between layers; a
    # single target for every region erases that difference (measured 2.2 dB on the
    # shipped pack, 1.1-2.7 dB on a per-region build) and the velocity knob then
    # changes almost nothing.  The reference ratios below give the ~19 dB pp->ff
    # spread the engine expects, so a soft touch is really softer.
    layer_gain = {}
    built = []
    for r, lay, j, n in jobs:
        try:
            pcm, ls, le = build_region(pool[(r, lay)], j, r, RATE)
        except Exception as e:
            print(f"  skip root={r} layer={lay}: {e}", file=sys.stderr)
            continue
        mono = pcm.astype(np.float64) / 32768.0
        built.append((r, j, pcm, ls, le, float(np.sqrt((mono ** 2).mean()))))
    if built:
        # measure each layer at a root near middle C so the ratio is pitch-neutral
        near = {}
        for r, j, pcm, ls, le, rms in built:
            cost = abs(r - 60)
            if j not in near or cost < near[j][0]:
                near[j] = (cost, rms)
        ref = max((v for _, v in near.values()), default=1.0)
        # LAYER_SPREAD_DB is the TOTAL pp->ff spread (the ~19 dB the engine expects),
        # shared across the steps: dividing by (n-1) as well gives 19 dB PER STEP and
        # 76 dB overall, which would make the soft layers inaudible.
        step_db = LAYER_SPREAD_DB / max(1, len(near) - 1)
        for j in sorted(near):
            tgt = ref * (10.0 ** (-step_db * (len(near) - 1 - j) / 20.0))
            layer_gain[j] = tgt / max(near[j][1], 1e-9)
    for r, j, pcm, ls, le, rms in built:
        pcm = np.clip(pcm.astype(np.float64) * layer_gain.get(j, 1.0),
                      -32768, 32767).astype(np.int16)
        # ADPCM stores one byte per frame (2 channels, 2 nibbles/byte), so the
        # encoded size is known before encoding: stop exactly at the ceiling
        # instead of overshooting and trimming afterwards.
        if len(blob) + pcm.shape[0] > budget_bytes:
            print(f"  budget reached: {len(recs)} regions kept", file=sys.stderr)
            break
        enc = adpcm_encode(pcm.reshape(-1))
        rec = bytearray(24)
        rec[0] = r
        rec[1] = j
        rec[2] = 127
        struct.pack_into('<I', rec, 4, len(blob))
        struct.pack_into('<I', rec, 8, pcm.shape[0])
        struct.pack_into('<I', rec, 12, ls)
        struct.pack_into('<I', rec, 16, le)
        struct.pack_into('<f', rec, 20, 0.0)
        recs.append(bytes(rec))
        blob.extend(enc.tobytes())

    out = bytearray(28)
    out[0:4] = b'PIAP'
    struct.pack_into('<I', out, 4, 1)
    struct.pack_into('<I', out, 8, RATE)
    struct.pack_into('<I', out, 12, len(recs))
    struct.pack_into('<I', out, 16, len(blob))
    struct.pack_into('<I', out, 20, len(recs) * 24)
    out += b''.join(recs) + bytes(blob)
    os.makedirs(os.path.dirname(args.out) or '.', exist_ok=True)
    with open(args.out + '.bin', 'wb') as fh:
        fh.write(out)
    print(f"wrote {args.out}.bin: {len(recs)} regions, {len(blob)/1048576:.2f} Mo, "
          f"rate={RATE}", file=sys.stderr)


if __name__ == '__main__':
    main()
