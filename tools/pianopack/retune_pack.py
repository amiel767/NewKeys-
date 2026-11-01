"""Recale l'accord de chaque region d'un pack PIAP, sans toucher a l'audio.

Le moteur applique `tuneCents` par region :
    rootF = nominal * 2^(tuneCents/1200)   puis   ratio = f / rootF
Donc la hauteur rendue vaut  nominal * 2^((D - tuneCents)/1200), ou D est la
derive REELLE de l'echantillon.  Regler `tuneCents = D` annule l'erreur : c'est
une correction, pas une transposition, et elle ne coute pas un octet.

Mesure de D : somme harmonique.  Chercher le maximum du spectre donne l'octave
au-dessus des que h2 depasse h1 (frequent au piano).  On note donc chaque
candidat f0 par la somme des amplitudes a k*f0, k=1..8, ponderees par 1/k :
une harmonique n'a pas d'harmoniques sous elle, donc elle perd.
"""
import json
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
IDX = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]


def decode(pk, n):
    pred = [0, 0]
    idx = [0, 0]
    out = np.empty(n, np.int16)
    for i in range(n):
        ch = i & 1
        b = int(pk[i >> 1])
        c = (b & 0xF) if (i & 1) == 0 else (b >> 4)
        st = STEP[idx[ch]]
        dl = st >> 3
        if c & 4:
            dl += st
        if c & 2:
            dl += st >> 1
        if c & 1:
            dl += st >> 2
        pred[ch] = pred[ch] - dl if c & 8 else pred[ch] + dl
        pred[ch] = 32767 if pred[ch] > 32767 else (-32768 if pred[ch] < -32768 else pred[ch])
        idx[ch] = min(88, max(0, idx[ch] + IDX[c & 7]))
        out[i] = pred[ch]
    return out


def detune_cents(x, sr, nominal):
    """Derive D de l'echantillon, en cents, par balayage fin.

    Meme methode que le banc `tools/aetherfx/piano_tune_check.cpp` : balayage de
    frequences a 0,2 Hz sur une fenetre longue, borne a +-6 % du nominal.

    Deux methodes ont ete essayees avant et ecartees :
      - le maximum du spectre : donne l'octave au-dessus des que h2 > h1, frequent
        au piano ;
      - la somme de maxima par candidat f0 avec raffinement parabolique : la somme
        de maxima est une fonction en dents de scie, le raffinement y est instable.
        Mesure : appliquer cette correction a fait passer C4 de -0,3 a -5,3 cents.
    """
    seg = x[:min(len(x), 1 << 17)]
    if len(seg) < 16384:
        return 0.0
    lo, hi = nominal * 0.94, nominal * 1.06
    best, bf = -1.0, nominal
    for f in np.arange(lo, hi, 0.2):
        t = np.arange(len(seg)) * (2 * np.pi * f / sr)
        re = float(np.dot(seg, np.cos(t)))
        im = float(np.dot(seg, np.sin(t)))
        m = re * re + im * im
        if m > best:
            best, bf = m, f
    return 1200.0 * np.log2(bf / nominal)


def main(src, dst=None, dry=False):
    d = bytearray(open(src, 'rb').read())
    rate, count, pcm_len, rec_len = struct.unpack('<IIII', bytes(d[8:24]))
    pcm = np.frombuffer(bytes(d[28 + rec_len:]), dtype=np.uint8)
    rows = []
    for i in range(count):
        r = 28 + i * 24
        root = d[r]
        layer = d[r + 1]
        off, frames = struct.unpack('<II', bytes(d[r + 4:r + 12]))
        if off + frames > pcm_len:
            continue
        x = decode(pcm[off:off + frames], frames * 2).astype(float)
        mono = (x[0::2] + x[1::2]) * 0.5
        a = int(0.25 * rate)
        seg = mono[a:]
        nominal = 440.0 * 2 ** ((root - 69) / 12.0)
        D = detune_cents(seg, rate, nominal)
        # Sous 3 cents on ne touche a rien : corriger une mesure au niveau de son
        # propre bruit ajoute une erreur la ou il n'y en avait pas.
        D = 0.0 if abs(D) < 3.0 else D
        rows.append((r, root, layer, D))
        if not dry:
            struct.pack_into('<f', d, r + 20, float(D))
    e = [x[3] for x in rows]
    print(f"{len(rows)} regions | derive avant correction : mediane {np.median(e):+.1f} "
          f"| min {min(e):+.1f} | max {max(e):+.1f} cents")
    print(f"  |derive| <= 3 cents : {sum(1 for v in e if abs(v) <= 3)} / {len(e)}")
    byroot = {}
    for _, root, _, D in rows:
        byroot.setdefault(root, []).append(D)
    spans = [(max(v) - min(v), k) for k, v in byroot.items() if len(v) > 1]
    if spans:
        print(f"  ecart entre couches d'une racine : mediane {np.median([s for s, _ in spans]):.1f} "
              f"| max {max(s for s, _ in spans):.1f} cents")
    if not dry:
        out = dst or src
        open(out, 'wb').write(bytes(d))
        print(f"  -> {out} : tuneCents recale sur la derive mesuree")
    return rows


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None,
         dry='--dry' in sys.argv)
