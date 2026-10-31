#!/usr/bin/env python3
"""Build a dense PIAP pack from an SFZ bank (Salamander Grand Piano V3, CC-BY).

Why a second builder instead of editing tools/pianopack/build.py
----------------------------------------------------------------
The first pack carried 21 root notes x 3 layers = 46 regions, 1.37 MB.  That
density is what makes a note sound "stretched" between two roots, and it is the
real quality limit -- not the codec.

Measured before choosing anything:

  * the bank has 0.0 % of its sustain energy above 3 kHz (tools/pianopack2/
    spectrum.py), so 22.05 kHz is the right pack rate and 44.1 kHz would only
    buy codec noise;
  * per-region normalisation buys <= 0.5 dB of SNR (codec.py), so it is not
    worth an engine change;
  * error-feedback noise shaping makes the 4-bit quantiser unstable
    (codec.py: -8 dB instead of +51 dB in the bass) -- rejected.

So the size budget goes into DENSITY: every root the bank has (3 semitones
apart, 29 of them) and every velocity layer (16).  The codec, the container and
the decoder stay exactly what the engine already reads.

Usage
-----
    python3 tools/pianopack2/build2.py --zip /workspace/nord/samples.zip \
        --sfz /workspace/nord/sal.sfz --out out/piano_dense
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import struct
import sys
import zipfile

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pianopack"))
from build import adpcm_encode, lowpass, resample  # noqa: E402  (bit-exact encoder)

RATE = 22050
TAIL_S = 0.9          # queue conservee apres le point de boucle
MAGIC = b"PIAP"
REC = 24

NOTE_BASE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
KV = re.compile(r'(\w+)=("[^"]*"|\S+)')


def midi_of(note: str) -> int:
    """'C3' -> 60 (Roland convention, do central = C3).  See measure.py."""
    if len(note) > 1 and note[1] in "#b":
        step = NOTE_BASE[note[0]] + (1 if note[1] == "#" else -1)
        octave = int(note[2:])
    else:
        step = NOTE_BASE[note[0]]
        octave = int(note[1:])
    return 12 * (octave + 1) + step + 12


def parse_sfz(path: str) -> list[dict]:
    group: dict = {}
    regions: list[dict] = []
    for raw in open(path, encoding="utf-8", errors="replace"):
        line = re.sub(r"//.*", "", raw).strip()
        if not line:
            continue
        if line.startswith(("<group>", "<global>", "<control>")):
            group.update({k: v.strip('"') for k, v in KV.findall(line)})
        elif line.startswith("<region>"):
            r = dict(group)
            r.update({k: v.strip('"') for k, v in KV.findall(line)})
            regions.append(r)
    return regions


def note_name(root: int) -> str:
    names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
    m = root - 12
    return f"{names[m % 12]}{m // 12 - 1}"


def loop_region(m: np.ndarray, sr: int, f0: float) -> tuple[int, int] | None:
    """Loop point and length, by whole periods of the measured f0.

    Returning from t0+L to t0 lands on the same phase, so the seam is continuous
    by construction.  The score is the step at the seam relative to the local RMS
    of the loop -- the measure that matches the click you hear.

    Vectorised with a cumulative sum: the previous version evaluated an RMS per
    candidate, which is ~500 M operations per region and takes minutes for a
    pack of several hundred regions.
    """
    P = max(int(round(sr / f0)), 8)
    lo = max(int(0.35 * sr), 8 * P)
    hi = min(int(1.10 * sr), len(m) - int(TAIL_S * sr) - 1)
    if hi - lo < 8 * P:
        return None
    c = np.concatenate([[0.0], np.cumsum(m.astype(np.float64) ** 2)])
    best = None
    for cand in np.arange(0.50, 1.10, 0.05):
        L = max(8, int(round(cand * sr / P))) * P
        if lo + L + 2 >= len(m):
            continue
        t = np.arange(lo, min(hi, len(m) - L - 1), max(P // 8, 1))
        if not len(t):
            continue
        energy = c[t + L] - c[t]
        rms = np.sqrt(np.maximum(energy, 1e-12) / L)
        score = np.abs(m[t] - m[t + L - 1]) / rms
        k = int(np.argmin(score))
        if best is None or score[k] < best[0]:
            best = (float(score[k]), int(t[k]), L)
    if best is None:
        return None
    return best[1], best[2]


def build_region(x: np.ndarray, sr: int, f0: float, rate: int) -> tuple[np.ndarray, int, int, float]:
    """Truncate, loop, resample.  Returns (stereo int16, loopStart, loopEnd, peak)."""
    mono = x.mean(axis=1)
    found = loop_region(mono, sr, f0)
    if found is None:
        end = min(len(x), int(2.5 * sr))
        ls = le = 0
    else:
        t0, L = found
        end = min(len(x), t0 + L + int(TAIL_S * sr))
        ls, le = t0, t0 + L
    x = x[:end]

    # Anti-alias filter BEFORE decimation: linear interpolation alone folds
    # everything above half the target rate back into the audible band.  A global
    # SNR does not see it, so this is not optional.
    chans = [resample(x[:, c], sr, rate) for c in range(x.shape[1])]
    seg = np.stack(chans, axis=1) if len(chans) > 1 else np.repeat(chans[0][:, None], 2, axis=1)
    peak = float(np.max(np.abs(seg))) or 1.0
    pcm = np.clip(np.round(seg * 32767.0), -32768, 32767).astype(np.int16)
    return pcm, int(ls * rate / sr), int(le * rate / sr), peak


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", default="/workspace/nord/samples.zip")
    ap.add_argument("--sfz", default="/workspace/nord/sal.sfz")
    ap.add_argument("--out", default="out/piano_dense")
    ap.add_argument("--roots", default="", help="comma list of MIDI roots (default: all)")
    ap.add_argument("--layers", type=int, default=16, help="engine velocity layers")
    ap.add_argument("--rate", type=int, default=22050, help="pack sample rate")
    ap.add_argument("--limit", type=int, default=0, help="debug: stop after N regions")
    a = ap.parse_args()

    import soundfile as sf

    regions = [r for r in parse_sfz(a.sfz) if r.get("trigger") != "release"
               and "pitch_keycenter" in r and "lovel" in r]
    by_root: dict[int, list[dict]] = {}
    for r in regions:
        by_root.setdefault(int(r["pitch_keycenter"]), []).append(r)
    for v in by_root.values():
        v.sort(key=lambda r: int(r["lovel"]))

    roots = ([int(v) for v in a.roots.split(",") if v.strip()] if a.roots
             else sorted(by_root))
    n_layers = a.layers
    z = zipfile.ZipFile(a.zip)
    os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)

    index, blob = [], bytearray()
    total_raw = 0
    for root in roots:
        cands = by_root.get(root)
        if not cands:
            print(f"  root {root} ({note_name(root)}) absent", file=sys.stderr)
            continue
        f0 = 440.0 * 2.0 ** ((root - 69) / 12.0)
        # Spread the engine's N layers evenly over the SFZ layers this root has,
        # so every engine layer gets a sample instead of a neighbour repeated.
        picks = [cands[round(i * (len(cands) - 1) / (n_layers - 1))] for i in range(n_layers)]
        for layer, r in enumerate(picks):
            try:
                with z.open(r["sample"]) as fh:
                    x, sr = sf.read(io.BytesIO(fh.read()), always_2d=True)
            except KeyError:
                print(f"  {r['sample']} absent", file=sys.stderr)
                continue
            pcm, ls, le, peak = build_region(x, sr, f0, a.rate)
            enc = adpcm_encode(pcm.reshape(-1))
            total_raw += pcm.size * 2
            index.append({
                "root": root, "layer": layer, "sample": r["sample"],
                "f0": round(f0, 3), "rate": a.rate, "n": int(pcm.shape[0]),
                "loop_start": ls, "loop_end": le, "peak": round(peak, 4),
                "tune": float(r.get("tune", 0)),
                "offset": len(blob), "bytes": len(enc),
                "dur_s": round(pcm.shape[0] / a.rate, 3),
            })
            blob.extend(enc.tobytes())
            if a.limit and len(index) >= a.limit:
                break
        print(f"  {note_name(root):<4} {len(picks)} couches  "
              f"{index[-1]['dur_s'] if index else 0:.2f}s  "
              f"{len(blob)/1048576:.2f} Mo", file=sys.stderr)
        if a.limit and len(index) >= a.limit:
            break

    recs = bytearray()
    for m in index:
        rec = bytearray(REC)
        rec[0] = m["root"]
        rec[1] = m["layer"]
        rec[2] = 127
        struct.pack_into("<I", rec, 4, m["offset"])
        struct.pack_into("<I", rec, 8, m["n"])          # frames stereo
        struct.pack_into("<I", rec, 12, m["loop_start"])
        struct.pack_into("<I", rec, 16, m["loop_end"])
        struct.pack_into("<f", rec, 20, m["tune"])      # cents, as the engine reads it
        recs += rec

    out = bytearray(28)
    out[0:4] = MAGIC
    struct.pack_into("<I", out, 4, 1)
    struct.pack_into("<I", out, 8, a.rate)
    struct.pack_into("<I", out, 12, len(index))
    struct.pack_into("<I", out, 16, len(blob))
    struct.pack_into("<I", out, 20, len(recs))
    out += recs + blob

    with open(a.out + ".bin", "wb") as fh:
        fh.write(out)
    with open(a.out + ".json", "w") as fh:
        json.dump({"rate": a.rate, "layers": n_layers, "regions": index}, fh, indent=1)

    print(f"\n{len(index)} regions | {n_layers} couches | pack {len(blob)/1048576:.2f} Mo | "
          f"fichier {len(out)/1048576:.2f} Mo | PCM {total_raw/1048576:.1f} Mo | "
          f"ratio {100.0*len(blob)/max(total_raw,1):.1f}%", file=sys.stderr)


if __name__ == "__main__":
    main()
