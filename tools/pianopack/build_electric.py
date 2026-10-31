#!/usr/bin/env python3
"""Construit le pack du piano ELECTRIQUE depuis la banque GregSullivan (CC-BY 3.0).

Reutilise les briques de build.py (boucle, crossfade, ADPCM, conteneur PIAP) : seul
le plan de regions change, puisqu'ici la source est un SFZ (Wurlitzer EP200) et non
une arborescence `<couche> <note>.flac`.

Usage : python3 build_electric.py --sfz <chemin.sfz> --samples <dossier> --out out/wurli
"""
import argparse
import os
import re
import struct
import sys

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build import RATE, TAIL_S, adpcm_encode, loop_region, resample  # noqa: E402
from measure import f0_nominal  # noqa: E402

# Les 4 couches du SFZ, ramenees sur l'echelle 0..4 du moteur.
LAYER_OF = {"pp": 0, "mp": 1, "f": 2, "ff": 4}
_NOTE = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]


def note_name(midi):
    return "%s%d" % (_NOTE[midi % 12], midi // 12 - 1)


def parse_sfz(path):
    """Regions du SFZ.  Chaque <region> herite des valeurs du <group> courant."""
    d = re.sub(r"//.*", "", open(path, encoding="utf8", errors="ignore").read())
    group, cur, out = {}, None, []
    for tok in re.finditer(r"<(\w+)>|(\w+)=([^\s]+)", d):
        if tok.group(1):
            tag = tok.group(1)
            if tag == "group":
                group = {}
                cur = group
            elif tag == "region":
                cur = dict(group)
                out.append(cur)
            continue
        if cur is not None:
            cur[tok.group(2)] = tok.group(3)
    return [r for r in out if "sample" in r and "pitch_keycenter" in r]


def build_region(rec, samples_dir):
    path = os.path.join(samples_dir, rec["sample"].replace("$EXT", "flac"))
    x, sr = sf.read(path, always_2d=True)
    if x.shape[1] == 1:
        x = np.repeat(x, 2, axis=1)
    mono = x.mean(axis=1)
    centre = int(rec["pitch_keycenter"])
    tune = float(rec.get("tune", 0))
    # La hauteur de reference integre le `tune` du SFZ : l'echantillon s'ecarte de
    # sa note de keycenter de ce nombre de cents, et le moteur applique le reste.
    f0 = f0_nominal(note_name(centre)) * (2.0 ** (tune / 1200.0))
    t0, L = loop_region(mono, sr, f0)
    if t0 is None:
        seg = x[:min(len(x), int(2.5 * sr))].copy()
        ls = le = 0
    else:
        seg = x[:min(len(x), t0 + L + int(TAIL_S * sr))].copy()
        ls, le = int(t0 * RATE / sr), int((t0 + L) * RATE / sr)
    seg = np.stack([resample(seg[:, 0], sr, RATE), resample(seg[:, 1], sr, RATE)], axis=1)
    pcm = np.clip(np.round(seg * 32767.0), -32768, 32767).astype(np.int16)
    return pcm, ls, le, tune


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sfz", required=True)
    ap.add_argument("--samples", required=True)
    ap.add_argument("--out", default="out/wurli")
    args = ap.parse_args()
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)

    recs, blob, kept = bytearray(), bytearray(), 0
    for r in parse_sfz(args.sfz):
        layer = r.get("group_label", "").lower()
        if layer not in LAYER_OF:
            continue
        try:
            pcm, ls, le, tune = build_region(r, args.samples)
        except Exception as e:  # noqa: BLE001
            print("  ECHEC %s : %s" % (r["sample"], e), file=sys.stderr)
            continue
        enc = adpcm_encode(pcm.reshape(-1))
        rec = bytearray(24)
        rec[0] = int(r["pitch_keycenter"])
        rec[1] = LAYER_OF[layer]
        rec[2] = 127
        struct.pack_into("<I", rec, 4, len(blob))
        struct.pack_into("<I", rec, 8, pcm.shape[0])
        struct.pack_into("<I", rec, 12, ls)
        struct.pack_into("<I", rec, 16, le)
        struct.pack_into("<f", rec, 20, tune)
        recs += rec
        blob += enc.tobytes()
        kept += 1
        print("  %-10s %-3s %5.2fs boucle %s %5.1f Ko"
              % (r["sample"], layer, pcm.shape[0] / RATE, "oui" if le else "non",
                 len(enc) / 1024), file=sys.stderr)

    out = bytearray(28)
    out[0:4] = b"PIAP"
    struct.pack_into("<I", out, 4, 1)
    struct.pack_into("<I", out, 8, RATE)
    struct.pack_into("<I", out, 12, kept)
    struct.pack_into("<I", out, 16, len(blob))
    struct.pack_into("<I", out, 20, len(recs))
    out += recs + blob
    open(args.out + ".bin", "wb").write(out)
    print("\n%d regions | pack %.2f Mo" % (kept, len(out) / 1048576), file=sys.stderr)


if __name__ == "__main__":
    main()
