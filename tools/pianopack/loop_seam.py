#!/usr/bin/env python3
"""Inspecte un pack PIAP : regions, boucles, et surtout la CONTINUITE du raccord.

But : dire si la boucle d'une region est un nombre ENTIER de periodes, si son
niveau decroit dans la boucle, et si le raccord saute. C'est ce qui produit le
« dain dain » (rebouclage audible) que dix corrections n'ont pas supprime.

Usage :
    python3 tools/pianopack/loop_seam.py app/src/main/assets/pianopack/piano.bin
"""
import struct, sys, math
from collections import defaultdict

KSTEP = [7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,
         80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,
         494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,
         2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,
         9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767]
KIDX = [-1,-1,-1,-1,2,4,6,8]

def decode_region(pcm, off, frames):
    predL = idxL = predR = idxR = 0
    first = off * 2
    out = [0] * (frames * 2)
    for i in range(frames):
        nL = first + i*2
        nR = nL + 1
        for (nib_idx, which) in ((nL,0),(nR,1)):
            b = pcm[nib_idx >> 1]
            code = (b >> 4) if (nib_idx & 1) else (b & 0xF)
            if which == 0:
                st = KSTEP[idxL]; dl = st >> 3
                if code & 4: dl += st
                if code & 2: dl += st >> 1
                if code & 1: dl += st >> 2
                predL += -dl if (code & 8) else dl
                predL = max(-32768, min(32767, predL))
                idxL = max(0, min(88, idxL + KIDX[code & 7]))
                out[i*2] = predL
            else:
                st = KSTEP[idxR]; dl = st >> 3
                if code & 4: dl += st
                if code & 2: dl += st >> 1
                if code & 1: dl += st >> 2
                predR += -dl if (code & 8) else dl
                predR = max(-32768, min(32767, predR))
                idxR = max(0, min(88, idxR + KIDX[code & 7]))
                out[i*2+1] = predR
    return out

def rms(x):
    if not x: return 0.0
    return math.sqrt(sum(v*v for v in x)/len(x))

def main():
    path = sys.argv[1]
    b = open(path, "rb").read()
    assert b[:4] == b"PIAP", b[:4]
    rate, count, pcmLen, recLen = struct.unpack_from("<IIII", b, 8)
    pcm = b[28+recLen:28+recLen+pcmLen]
    regs = []
    for i in range(count):
        r = b[28+i*24:28+i*24+24]
        root, layer = r[0], r[1]
        off, frames, ls, le, tune = struct.unpack_from("<IIIIf", r, 4)
        if frames < 64: continue
        regs.append(dict(root=root, layer=layer, off=off, frames=frames,
                         ls=ls, le=le, tune=tune))
    print(f"# {path}  rate={rate} regions={len(regs)}")
    print(f"{'root':>4} {'lay':>3} {'frames':>7} {'loopLen':>8} {'periodes':>8} "
          f"{'decay/tour dB':>13} {'saut raccord':>12}")
    by_root = defaultdict(list)
    for g in regs:
        by_root[g["root"]].append(g)
    for root in sorted(by_root):
        for g in sorted(by_root[root], key=lambda x: x["layer"]):
            ls, le = g["ls"], g["le"]
            if le <= ls or le > g["frames"]:
                print(f"{root:>4} {g['layer']:>3} {g['frames']:>7}   pas de boucle")
                continue
            pcmr = decode_region(pcm, g["off"], g["frames"])
            L = le - ls
            f0 = 440.0 * 2**((root-69)/12.0)
            period = rate / f0
            nper = L / period
            # niveau du 1er bloc vs dernier bloc de la boucle
            w = max(1, L // 8)
            head = rms(pcmr[2*ls:2*(ls+w)])
            tail = rms(pcmr[2*(le-w):2*le])
            decay = 20*math.log10(max(1e-9, tail)/max(1e-9, head))
            # saut au raccord : |x[le-1] - x[ls]| vs RMS local
            d = abs(pcmr[2*(le-1)] - pcmr[2*ls]) / 32768.0
            seam = d / max(1e-9, head/32768.0)
            print(f"{root:>4} {g['layer']:>3} {g['frames']:>7} {L:>8} {nper:>8.2f} "
                  f"{decay:>13.2f} {seam:>12.2f}")

if __name__ == "__main__":
    main()
