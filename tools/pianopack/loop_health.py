#!/usr/bin/env python3
"""Sante des boucles d'un pack PIAP : est-ce que le raccord peut etre muet ?

Pour chaque region on mesure :
  - periodes = longueur de boucle / periode REELLE (autocorrelation dans la boucle)
    -> doit etre un ENTIER, sinon le raccord saute (le fondu d'une periode ne
       retombe pas sur la meme phase) ;
  - pre/head = niveau de l'echantillon AVANT loopStart / niveau du debut de boucle.
    Le fondu du moteur tire la queue vers ces echantillons : s'ils sont plus FORTS,
    le raccord remonte en niveau -> bosse periodique (« dain ») ;
  - head/tail = niveau debut/fin de boucle (ce que `flattenLoop` aplatit).

Usage :
    python3 tools/pianopack/loop_health.py app/src/main/assets/pianopack/piano.bin
"""
import struct, sys, math
from collections import defaultdict

KSTEP = [7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,
         80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,
         494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,
         2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,
         9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767]
KIDX = [-1,-1,-1,-1,2,4,6,8]


def decode(pcm, off, frames):
    predL = idxL = predR = idxR = 0
    first = off * 2
    out = [0.0] * (frames * 2)
    for i in range(frames):
        for which in (0, 1):
            n = first + i * 2 + which
            b = pcm[n >> 1]
            code = (b >> 4) if (n & 1) else (b & 0xF)
            if which == 0:
                st = KSTEP[idxL]; dl = st >> 3
                if code & 4: dl += st
                if code & 2: dl += st >> 1
                if code & 1: dl += st >> 2
                predL += -dl if (code & 8) else dl
                predL = max(-32768, min(32767, predL))
                idxL = max(0, min(88, idxL + KIDX[code & 7]))
                out[i * 2] = predL
            else:
                st = KSTEP[idxR]; dl = st >> 3
                if code & 4: dl += st
                if code & 2: dl += st >> 1
                if code & 1: dl += st >> 2
                predR += -dl if (code & 8) else dl
                predR = max(-32768, min(32767, predR))
                idxR = max(0, min(88, idxR + KIDX[code & 7]))
                out[i * 2 + 1] = predR
    return out


def rms(x):
    return math.sqrt(sum(v * v for v in x) / len(x)) if x else 0.0


def mono(x):
    return [0.5 * (x[2 * i] + x[2 * i + 1]) for i in range(len(x) // 2)]


def real_period(m, ls, le, rate, nominal_hz):
    """Periode par autocorrelation dans la boucle, autour de la periode nominale."""
    seg = m[ls:le]
    n = len(seg)
    if n < 256:
        return 0.0, 0.0
    mean = sum(seg) / n
    seg = [v - mean for v in seg]
    nom = rate / nominal_hz
    lo = max(8, int(nom * 0.85))
    hi = min(n // 4, int(nom * 1.18))
    e0 = sum(v * v for v in seg) or 1.0
    best, bestlag = -2.0, 0
    for lag in range(lo, hi):
        a = sum(seg[i] * seg[i + lag] for i in range(0, n - lag))
        a /= e0
        if a > best:
            best, bestlag = a, lag
    return float(bestlag), best


def main():
    path = sys.argv[1]
    b = open(path, "rb").read()
    if b[:4] != b"PIAP":
        sys.exit("pas un PIAP")
    rate, count, pcmLen, recLen = struct.unpack_from("<IIII", b, 8)
    pcm = b[28 + recLen:28 + recLen + pcmLen]
    regs = []
    for i in range(count):
        r = b[28 + i * 24:28 + i * 24 + 24]
        off, frames, ls, le, tune = struct.unpack_from("<IIIIf", r, 4)
        if frames < 64:
            continue
        regs.append(dict(root=r[0], layer=r[1], off=off, frames=frames,
                         ls=ls, le=le, tune=tune))
    print(f"# {path}  rate={rate} regions={len(regs)}")
    print(f"{'root':>4} {'lay':>3} {'loopLen':>8} {'per.reelle':>10} {'periodes':>9} "
          f"{'frac':>6} {'pre/head dB':>11} {'head/tail dB':>12}")
    by_root = defaultdict(list)
    for g in regs:
        by_root[g["root"]].append(g)
    bad_per, bad_pre = 0, 0
    tot = 0
    for root in sorted(by_root):
        for g in sorted(by_root[root], key=lambda x: x["layer"]):
            ls, le = g["ls"], g["le"]
            if le <= ls or le > g["frames"]:
                continue
            tot += 1
            d = decode(pcm, g["off"], g["frames"])
            m = mono(d)
            f0 = 440.0 * 2 ** ((root - 69) / 12.0)
            per, _ = real_period(m, ls, le, rate, f0)
            L = le - ls
            nper = L / per if per else 0
            frac = abs(nper - round(nper)) if per else 1
            w = max(1, min(rate // 100, L // 8))
            head = rms(m[ls:ls + w])
            tail = rms(m[le - w:le])
            pre = rms(m[max(0, ls - w):ls])
            pre_db = 20 * math.log10(max(1e-9, pre) / max(1e-9, head))
            ht_db = 20 * math.log10(max(1e-9, head) / max(1e-9, tail))
            if frac > 0.08:
                bad_per += 1
            if pre_db > 1.0:
                bad_pre += 1
            print(f"{root:>4} {g['layer']:>3} {L:>8} {per:>10.1f} {nper:>9.2f} "
                  f"{frac:>6.2f} {pre_db:>11.2f} {ht_db:>12.2f}")
    print(f"\n# boucles non entieres : {bad_per}/{tot}")
    print(f"# boucles ou l'avant-loop est plus fort : {bad_pre}/{tot}")


if __name__ == "__main__":
    main()
