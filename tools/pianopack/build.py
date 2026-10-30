#!/usr/bin/env python3
"""Construit le pack piano : troncature + boucle + 22 kHz stereo + ADPCM 4 bits.

C'est l'etape qui transforme les mesures en asset. Le poids final depend de
quatre leviers, chacun mesure :

  1. troncature : on garde l'attaque (fin du transitoire + marge) puis une boucle,
     au lieu des 8 s moyennes de la source.
  2. boucle : la fin du fichier est remplacee par un retour au point de boucle.
  3. taux : 22,05 kHz suffit (99,9 % de l'energie sous 4 kHz, mesure).
  4. ADPCM 4 bits : ratio mesure ~6,2 % du PCM 16 bits.

Le stereo est conserve : correlation L/R mesuree +0,02 a +0,34, ce sont deux
micros, pas un mono duplique.

Usage :
    python3 build.py --notes C2,C3,C4 --layers PP,Mp,MF,FF --out out/pack
"""
import argparse
import struct
import json
import os
import sys

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure import load_mono, sample_path, f0_nominal, fit_log_score, midi_of  # noqa: E402

RATE = 22050

# Duree de queue conservee apres le point de boucle.
TAIL_S = 0.6

# Ordre des couches de la banque, du plus doux au plus fort.
LAYERS = ["PP", "Mp", "Mf", "MF", "FF"]

# IMA ADPCM, tables normalisees
STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
        45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190,
        209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724,
        796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
        2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132,
        7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
        22385, 24623, 27086, 29794, 32767]
INDEX = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]


def adpcm_encode(xi):
    """xi : int16. Renvoie les nibbles (2 par octet) et le SNR de controle."""
    pred, idx = 0, 0
    nib = np.empty(len(xi), np.uint8)
    for i in range(len(xi)):
        st = STEP[idx]
        d = int(xi[i]) - pred
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
        pred = pred - dl if code & 8 else pred + dl
        pred = 32767 if pred > 32767 else (-32768 if pred < -32768 else pred)
        idx = min(88, max(0, idx + INDEX[code & 7]))
        nib[i] = code
    out = np.empty((len(nib) + 1) // 2, np.uint8)
    out[:] = nib[0::2]
    if len(nib) > 1:
        out[:len(nib) // 2] |= nib[1::2] << 4
    return out


def adpcm_decode(packed, n):
    pred, idx = 0, 0
    out = np.empty(n, np.int16)
    for i in range(n):
        b = int(packed[i >> 1])
        code = (b & 0xF) if (i & 1) == 0 else (b >> 4)
        st = STEP[idx]
        dl = st >> 3
        if code & 4:
            dl += st
        if code & 2:
            dl += st >> 1
        if code & 1:
            dl += st >> 2
        pred = pred - dl if code & 8 else pred + dl
        pred = 32767 if pred > 32767 else (-32768 if pred < -32768 else pred)
        idx = min(88, max(0, idx + INDEX[code & 7]))
        out[i] = pred
    return out


def lowpass(x, sr, cutoff):
    """FIR fenetre-sinc (Hamming). Sans lui, decimer 44,1 -> 22,05 replie tout
    ce qui depasse 11 kHz dans la bande audible : bruit audible ajoute, invisible
    sur un simple SNR global."""
    n = 257
    fc = cutoff / sr
    k = np.arange(n) - (n - 1) / 2.0
    h = 2 * fc * np.sinc(2 * fc * k) * np.hamming(n)
    h /= h.sum()
    return np.convolve(x, h, mode="same")


def resample(x, sr, target):
    if sr == target:
        return x
    x = lowpass(x, sr, 0.45 * target)
    n = int(round(len(x) * target / sr))
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)


def loop_region(m, sr, f0, xfade=None):
    """Point et longueur de boucle, par continuite de phase.

    L'ancienne version cherchait une longueur libre maximisee par correlation : elle
    ne retenait que la moitie des notes, les autres tombant sur une troncature a
    2,5 s.  Le sustain s'arretait net — le piano sonnait comme un jouet.

    Ici la longueur vaut un nombre ENTIER de periodes de f0 mesure : revenir de
    t0+L a t0 retombe sur la meme phase, donc le raccord est continu par
    construction.  Il ne reste qu'a choisir t0 et L, en gardant le plus petit saut.

    Le saut est juge par rapport au niveau local (RMS de la boucle), pas au plus
    grand pas interne ni a l'enveloppe : c'est la mesure qui correspond au clic
    entendu.  Un pas fin (P/6) et une plage de longueurs large (0,15 a 0,62 s)
    trouvent un raccord exact la ou un pas grossier en laissait passer.
    """
    P = max(int(round(sr / f0)), 8)
    lo = max(int(0.40 * sr), 8 * P)
    hi = min(int(1.00 * sr), len(m) - int(0.6 * sr))
    if hi - lo < 8 * P:
        return None, None
    step = max(P // 6, 1)
    best = None
    for cand in np.arange(0.55, 1.05, 0.05):
        L = max(8, int(round(cand * sr / P))) * P
        if lo + L + 2 >= len(m):
            continue
        for t0 in range(lo, min(hi, len(m) - L - 1), step):
            rms = float(np.sqrt(np.mean(m[t0:t0 + L] ** 2))) + 1e-9
            score = abs(m[t0] - m[t0 + L - 1]) / rms
            if best is None or score < best[0]:
                best = (score, t0, L)
    if best is None:
        return None, None
    return best[1], best[2]


def build_region(name):
    """Renvoie (pcm_stereo_int16, meta) pour une region."""
    x, sr = sf.read(sample_path(name), always_2d=True)
    if x.shape[1] == 1:
        x = np.repeat(x, 2, axis=1)
    mono = x.mean(axis=1)
    f0n = f0_nominal(name.split(" ", 1)[1].rsplit(".", 1)[0])
    f0, B = fit_log_score(mono, sr, f0n)

    t0, L = loop_region(mono, sr, f0)
    if t0 is None:
        # pas de boucle : on garde 2,5 s (borne haute), la fin est un fade
        keep = min(len(x), int(2.5 * sr))
        seg = x[:keep].copy()
        loop_start = loop_end = 0
    else:
        # Marge de queue APRES la boucle : au relachement le moteur sort de la
        # boucle et joue cette queue, au lieu de couper net sur le raccord.
        end = min(len(x), t0 + L + int(TAIL_S * sr))
        seg = x[:end].copy()
        loop_start = t0
        loop_end = t0 + L          # la queue apres loop_end n'est PAS dans la boucle

    seg = resample(seg[:, 0], sr, RATE), resample(seg[:, 1], sr, RATE)
    seg = np.stack(seg, axis=1)
    ls = int(loop_start * RATE / sr) if loop_start else 0
    le = int(loop_end * RATE / sr) if loop_end else 0

    pcm = np.clip(np.round(seg * 32767.0), -32768, 32767).astype(np.int16)
    meta = {"sample": name, "f0": round(f0, 3), "B": B,
            "rate": RATE, "n": int(pcm.shape[0]),
            "loop_start": ls, "loop_end": le,
            "dur_s": round(pcm.shape[0] / RATE, 3)}
    return pcm, meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--notes", default="C2,C3,C4")
    ap.add_argument("--layers", default="PP,Mp,MF,FF")
    ap.add_argument("--jobs", help="fichier 'couche note' par ligne (prioritaire)")
    ap.add_argument("--out", default="out/pack")
    args = ap.parse_args()

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    index, blob, n_regions = [], bytearray(), 0
    total_raw = 0
    if args.jobs:
        pairs = [tuple(l.split(None, 1)) for l in open(args.jobs).read().split("\n") if l.strip()]
        jobs = ["%s %s.flac" % (L, n) for L, n in pairs]
    else:
        jobs = ["%s %s.flac" % (L, n)
                for n in args.notes.split(",") for L in args.layers.split(",")]
    for name in jobs:
            try:
                pcm, meta = build_region(name)
            except Exception as e:
                print("  %-12s ECHEC %s" % (name, e), file=sys.stderr)
                continue
            enc = adpcm_encode(pcm.reshape(-1))
            total_raw += pcm.size * 2
            meta["offset"] = len(blob)
            meta["bytes"] = len(enc)
            index.append(meta)
            blob.extend(enc.tobytes())
            n_regions += 1
            print("  %-12s %5.2fs  boucle %s  %5.0f Ko"
                  % (name, meta["dur_s"], "oui" if meta["loop_end"] else "non",
                     len(enc) / 1024), file=sys.stderr)

    # Conteneur PIAP.  Le blob ADPCM seul n'est pas lisible par le moteur : il faut
    # l'en-tete (28 o) + une fiche de 24 o par region + la charge utile.
    recs = bytearray()
    for m in index:
        name = m["sample"]                      # "<couche> <note>.flac"
        layer_name, note_name = name[:-5].split(None, 1)
        rec = bytearray(24)
        rec[0] = midi_of(note_name)
        rec[1] = LAYERS.index(layer_name)
        rec[2] = 127                            # velocite maximale de la couche
        struct.pack_into("<I", rec, 4, m["offset"])
        # 1 octet ADPCM = 2 nibbles = 2 echantillons = 1 frame stereo.
        struct.pack_into("<I", rec, 8, m["n"])            # frames stereo
        struct.pack_into("<I", rec, 12, m["loop_start"])
        struct.pack_into("<I", rec, 16, m["loop_end"])
        recs += rec

    pcm_len = len(blob)
    out = bytearray(28)
    out[0:4] = b"PIAP"
    struct.pack_into("<I", out, 4, 1)          # version
    struct.pack_into("<I", out, 8, RATE)
    struct.pack_into("<I", out, 12, n_regions)
    struct.pack_into("<I", out, 16, pcm_len)
    struct.pack_into("<I", out, 20, len(recs))
    out += recs + blob

    with open(args.out + ".bin", "wb") as fh:
        fh.write(out)
    with open(args.out + ".json", "w") as fh:
        json.dump({"rate": RATE, "regions": index}, fh, indent=1)
    print("\n%d regions | pack %.2f Mo | PCM source %.2f Mo | ratio %.1f%%"
          % (n_regions, len(blob) / 1048576, total_raw / 1048576,
             100.0 * len(blob) / max(total_raw, 1)), file=sys.stderr)


if __name__ == "__main__":
    main()
