"""Construit un pack PIAP depuis l'extraction d'un SF2 (voir extract_sf2.py).

Trois differences avec build.py (banque SplendidGrandPiano) :

1. **Stereo vraie.**  Chaque couche du SF2 porte DEUX prises, une panoramique a
   gauche (pan < 0) et une a droite (pan > 0).  On les place cote a cote au lieu de
   les sommer : l'image stereo est celle de l'enregistrement, sans calcul.

2. **4 couches depuis 7 plages.**  Le SF2 decoupe la velocite en 7 plages ; le
   moteur en veut 4 (`pick()` fait `velocity * layerCount / 128`).  On fusionne
   0-20 + 21-41 -> PP, 42-63 -> MP, 64-84 -> MF, 85-105 + 106-127 -> FF.

3. **Boucle longue, queue longue.**  Les echantillons font 10,5 s et decroissent
   vraiment : une boucle courte rejoue une partie deja amortie et le sustain
   « pompe ».  On cherche une boucle d'un nombre entier de periodes de f0 et on
   garde 1,5 s de queue apres le point de boucle.

Usage : python3 build_sf2.py --src /tmp/sf2x --out out/sf2pack
"""
import argparse
import json
import os
import struct
import sys

import numpy as np
import soundfile as sf

RATE = 44100
TAIL_S = 1.5

# Fusion des 7 plages du SF2 vers les 4 couches du moteur.
LAYER_OF_VEL = [(0, 41, 0), (42, 63, 1), (64, 84, 2), (85, 127, 3)]

STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
        45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190,
        209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724,
        796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
        2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132,
        7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
        22385, 24623, 27086, 29794, 32767]
INDEX = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]


def adpcm_encode(xi):
    """xi : int16 entrelace L/R. Un etat de predicteur PAR CANAL.

    Le moteur decode avec un predicteur par canal ; un encodeur a predicteur unique
    produit un flux qu'il ne sait pas decoder (mesure : SNR -13,9 dB au lieu de +33).
    """
    out = bytearray(len(xi) // 2)
    pred = [0, 0]
    idx = [0, 0]
    for i, s in enumerate(xi):
        ch = i & 1
        st = STEP[idx[ch]]
        d = int(s) - pred[ch]
        code = 8 if d < 0 else 0
        if d < 0:
            d = -d
        dl = st >> 3
        if d >= st:
            code |= 4
            d -= st
            dl += st
        if d >= st >> 1:
            code |= 2
            d -= st >> 1
            dl += st >> 1
        if d >= st >> 2:
            code |= 1
            dl += st >> 2
        pred[ch] += -dl if code & 8 else dl
        pred[ch] = 32767 if pred[ch] > 32767 else (-32768 if pred[ch] < -32768 else pred[ch])
        idx[ch] = min(88, max(0, idx[ch] + INDEX[code & 7]))
        out[i >> 1] |= code << (4 if i & 1 else 0)
    return bytes(out)


def lowpass(x, sr, cutoff):
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


def estimate_f0(x, sr, hint):
    """f0 par autocorrelation, cherche autour de la hauteur nominale.

    Chercher le maximum brut de l'autocorrelation accroche l'harmonique 2 sur un
    piano : la fondamentale y est souvent plus faible que h2.  On borne donc la
    recherche autour de la note racine annoncee par le SF2.
    """
    lo = max(int(sr / (hint * 1.06)), 8)
    hi = min(int(sr / (hint * 0.94)), len(x) // 2)
    if hi <= lo:
        return hint
    seg = x[int(0.2 * sr):int(0.2 * sr) + 4 * hi]
    seg = seg - seg.mean()
    ac = np.correlate(seg, seg, 'full')[len(seg) - 1:]
    if hi >= len(ac):
        return hint
    lag = lo + int(np.argmax(ac[lo:hi]))
    return sr / lag if lag > 0 else hint


def loop_region(l, r, sr, f0):
    """(t0, L) : un seul point de boucle pour les DEUX canaux.

    Le conteneur ne porte qu'un `loopStart` pour la region entiere : choisir une
    phase par canal ne sert a rien, la seconde est jetee a l'ecriture.  On minimise
    donc le pire des deux canaux, avec un seul t0.

    Les deux prises viennent de micros differents : il reste presque toujours un
    saut residuel.  `bake_crossfade` le supprime ensuite.
    """
    P = max(int(round(sr / f0)), 8)
    lo = max(int(0.30 * sr), 8 * P)
    hi = min(int(2.00 * sr), len(l) - int(TAIL_S * sr) - 8 * P, len(r) - int(TAIL_S * sr) - 8 * P)
    if hi - lo < 8 * P:
        return None, None
    step = max(P // 8, 1)
    best = None
    for cand in np.arange(0.40, 1.25, 0.05):
        L = max(8, int(round(cand * sr / P))) * P
        top = min(hi, len(l) - L - 1, len(r) - L - 1)
        if lo + L + 2 >= len(l) or top <= lo:
            continue
        for t0 in range(lo, top, step):
            rl = float(np.sqrt(np.mean(l[t0:t0 + L] ** 2))) + 1e-9
            rr = float(np.sqrt(np.mean(r[t0:t0 + L] ** 2))) + 1e-9
            score = max(abs(l[t0] - l[t0 + L - 1]) / rl, abs(r[t0] - r[t0 + L - 1]) / rr)
            if best is None or score < best[0]:
                best = (score, t0, L)
    if best is None:
        return None, None
    return best[1], best[2]


def bake_crossfade(x, t0, L, xfade):
    """Fond la FIN de la boucle dans le segment qui PRECEDE t0, par canal.

    Le moteur saute de t0+L-1 a t0 sans rien faire.  Pour que ce saut soit un pas
    ordinaire, il faut que x[t0+L-1] tende vers x[t0-1] : on melange donc les
    derniers `xfade` echantillons avec [t0-xfade, t0), le poids valant 1 au debut
    du fondu et 0 a la fin.

    Melanger avec [t0, t0+xfade) au lieu du segment PRECEDENT ne raccorde rien :
    le dernier echantillon tend alors vers x[t0+xfade-1], et le saut conserve
    exactement la distance `xfade`.  Mesure du premier essai : 5,06 median, 27,9
    au pire, soit pire que sans fondu.
    """
    if xfade <= 1 or t0 + L > len(x) or t0 < xfade:
        return x
    x = x.copy()
    xfade = min(xfade, L // 3, t0)
    for i in range(xfade):
        w = 1.0 - i / xfade
        t = t0 + L - xfade + i
        x[t] = w * x[t] + (1.0 - w) * x[t0 - xfade + i]
    return x


def seam_ratio(x, t0, L):
    """Raccord a la couture, en multiples du plus grand pas interne (99,9e)."""
    seg = x[t0:t0 + L]
    if len(seg) < 64:
        return 0.0
    internal = float(np.percentile(np.abs(np.diff(seg)), 99.9)) + 1e-9
    return abs(seg[0] - seg[-1]) / internal


def build_region(left, right, sr, root):
    """(frames stereo int16, meta) pour une region, depuis ses deux prises."""
    f0 = estimate_f0(left, sr, 440.0 * 2 ** ((root - 69) / 12.0))
    t0, L = loop_region(left, right, sr, f0)
    if t0 is None:
        end = min(len(left), len(right), int(2.5 * sr))
        loop_start = loop_end = 0
    else:
        end = min(len(left), len(right), t0 + L + int(TAIL_S * sr))
        loop_start, loop_end = t0, t0 + L

    l = resample(left[:end], sr, RATE)
    r = resample(right[:end], sr, RATE)
    sc = RATE / sr
    ls, le = int(loop_start * sc), int(loop_end * sc)
    faded = 0
    if le > ls > 0:
        # Le fondu n'est applique QUE si la boucle ne se raccorde pas deja.
        # Chercher un nombre entier de periodes de f0 suffit le plus souvent
        # (mesure : 0,01 sur le canal gauche) ; melanger 15 ms de deux instants
        # differents d'une decroissance n'apporte alors qu'un defaut.  Il ne sert
        # qu'aux regions ou les deux micros ne retombent pas en phase.
        before = max(seam_ratio(l, ls, le - ls), seam_ratio(r, ls, le - ls))
        if before > 2.0:
            xf = int(0.015 * RATE)
            l = bake_crossfade(l, ls, le - ls, xf)
            r = bake_crossfade(r, ls, le - ls, xf)
            faded = 1
    n = min(len(l), len(r))
    st = np.empty(2 * n, np.int16)
    st[0::2] = np.clip(np.round(l[:n] * 32767.0), -32768, 32767)
    st[1::2] = np.clip(np.round(r[:n] * 32767.0), -32768, 32767)
    return st, dict(root=root, f0=round(f0, 2), rate=RATE, n=n,
                    loop_start=ls, loop_end=le, faded=faded)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--roots', default='')
    args = ap.parse_args()

    mapping = json.load(open(os.path.join(args.src, 'mapping.json')))
    # Une entree par (racine, plage de velocite) : les deux prises d'une paire.
    pairs = {}
    for z in mapping:
        if z['vel_lo'] == 0 and z['vel_hi'] == 127:
            continue                      # zone de repli, pas une vraie couche
        layer = next((L for lo, hi, L in LAYER_OF_VEL if z['vel_lo'] >= lo and z['vel_hi'] <= hi), None)
        if layer is None:
            continue
        pairs.setdefault((z['root'], layer), []).append(z)

    want = {int(x) for x in args.roots.split(',') if x} if args.roots else None
    blob = bytearray()
    recs = bytearray()
    index = []
    for (root, layer) in sorted(pairs):
        if want and root not in want:
            continue
        zs = pairs[(root, layer)]
        lefts = [z for z in zs if z['pan'] < 0]
        rights = [z for z in zs if z['pan'] >= 0]
        if not lefts or not rights:
            continue
        l, slr = sf.read(os.path.join(args.src, lefts[0]['file']))
        r, srr = sf.read(os.path.join(args.src, rights[0]['file']))
        if l.ndim > 1:
            l = l.mean(axis=1)
        if r.ndim > 1:
            r = r.mean(axis=1)
        st, meta = build_region(l.astype(np.float64), r.astype(np.float64), slr, root)
        packed = adpcm_encode(st)
        meta['offset'] = len(blob)
        meta['layer'] = layer
        meta['frames'] = meta['n']
        meta['tune_cents'] = round(lefts[0]['fine'] + lefts[0]['coarse'] * 100.0, 2)
        blob += packed
        rec = bytearray(24)
        rec[0] = root
        rec[1] = layer
        rec[2] = 127
        struct.pack_into('<I', rec, 4, meta['offset'])
        struct.pack_into('<I', rec, 8, meta['frames'])
        struct.pack_into('<I', rec, 12, meta['loop_start'])
        struct.pack_into('<I', rec, 16, meta['loop_end'])
        struct.pack_into('<f', rec, 20, meta['tune_cents'])
        recs += rec
        index.append(meta)

    out = bytearray(28)
    out[0:4] = b'PIAP'
    struct.pack_into('<I', out, 4, 1)
    struct.pack_into('<I', out, 8, RATE)
    struct.pack_into('<I', out, 12, len(index))
    struct.pack_into('<I', out, 16, len(blob))
    struct.pack_into('<I', out, 20, len(recs))
    out += recs + blob

    os.makedirs(os.path.dirname(args.out) or '.', exist_ok=True)
    with open(args.out + '.bin', 'wb') as fh:
        fh.write(out)
    json.dump({'rate': RATE, 'regions': index}, open(args.out + '.json', 'w'), indent=1)
    dur = sum(m['n'] for m in index) / RATE
    print(f"{len(index)} regions | {len(out)/1048576:.2f} Mo | duree totale {dur:.1f} s "
          f"| {dur/len(index):.2f} s/region | taux {RATE} Hz", file=sys.stderr)


if __name__ == '__main__':
    main()
