#!/usr/bin/env python3
"""Simule la lecture d'une note par le moteur piano (hors C++) et mesure le
residu d'enveloppe dans la tenue : c'est le « Daiiiinnn-dain-dain » ressenti.

On reproduit fidelement ce que fait `PianoSampleReader` :
  - `harmonizeLoops()` : toutes les couches d'une racine prennent la MEME longueur
    de boucle (la plus courte, en frames) ;
  - `flattenLoop()`   : niveau de boucle aplati + fondu du raccord ;
  - `readOne()`       : rebouclage par soustraction de (loopEnd - loopStart).
Puis on somme deux couches avec le fondu du moteur, et on mesure l'enveloppe.

    python3 tools/pianopack/sim_loop.py app/src/main/assets/pianopack/piano.bin 60
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
    out = [0] * (frames * 2)
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


def load(path):
    b = open(path, "rb").read()
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
    return rate, pcm, regs


def real_period(mono, ls, le, rate, f0):
    seg = mono[ls:le]
    n = len(seg)
    if n < 256:
        return 0.0
    mean = sum(seg) / n
    seg = [v - mean for v in seg]
    nom = rate / f0
    e0 = sum(v * v for v in seg) or 1.0
    best, bestlag = -2.0, 0
    for lag in range(max(8, int(nom * 0.85)), min(n // 4, int(nom * 1.18))):
        a = sum(seg[i] * seg[i + lag] for i in range(0, n - lag)) / e0
        if a > best:
            best, bestlag = a, lag
    return float(bestlag)


def harmonize(regs, quantize, rate, per):
    """Reproduit harmonizeLoops. quantize=False = version actuelle (frames brutes) ;
    quantize=True = longueur d'abord arrondie a un entier de periodes."""
    by_root = defaultdict(list)
    for i, g in enumerate(regs):
        if g["le"] > g["ls"]:
            by_root[g["root"]].append(i)
    for root, idxs in by_root.items():
        if quantize:
            nper = {}
            for i in idxs:
                nper[i] = max(1, round((regs[i]["le"] - regs[i]["ls"]) / per[i]))
            common = min(nper.values())
            for i in idxs:
                regs[i]["le"] = regs[i]["ls"] + int(round(common * per[i]))
        else:
            common = min(regs[i]["le"] - regs[i]["ls"] for i in idxs)
            for i in idxs:
                regs[i]["le"] = regs[i]["ls"] + common
    return regs


def flatten(pcm, g, rate):
    """Reproduit flattenLoop (gain par fenetre + fondu de raccord)."""
    ls, le = g["ls"], g["le"]
    if le <= ls or le > g["frames"]:
        return
    L = le - ls
    win = max(1, int(0.010 * rate))
    nwin = max(2, L // win)
    raw = []
    for k in range(nwin):
        a = ls + k * L // nwin
        b = ls + (k + 1) * L // nwin
        s = 0.0
        for i in range(a, b):
            s += pcm[2 * i] ** 2 + pcm[2 * i + 1] ** 2
        raw.append(math.sqrt(s / max(1, (b - a) * 2)))
    head = max(1e-6, raw[0])
    gs = [min(12.0, head / max(1e-6, r)) for r in raw]
    sm = [gs[0] if k == 0 else (gs[-1] if k + 1 == nwin else
          0.25 * gs[k - 1] + 0.5 * gs[k] + 0.25 * gs[k + 1]) for k in range(nwin)]
    for k in range(nwin):
        a = ls + k * L // nwin
        b = ls + (k + 1) * L // nwin
        g0, g1 = sm[k], (sm[k + 1] if k + 1 < nwin else sm[0])
        span = max(1, b - a)
        for i in range(a, b):
            gg = g0 + (g1 - g0) * ((i - a) / span)
            pcm[2 * i] *= gg
            pcm[2 * i + 1] *= gg
    f0 = 440.0 * 2 ** ((g["root"] - 69) / 12.0)
    xf = int(max(1, rate / max(20.0, f0)))
    xf = min(xf, min(L // 2, ls))
    for i in range(xf):
        u = (i + 1) / (xf + 1)
        for c in range(2):
            tail = pcm[2 * (le - xf + i) + c]
            pre = pcm[2 * (ls - xf + i) + c]
            pcm[2 * (le - xf + i) + c] = tail * (1 - u) + pre * u


def read_loop(pcm, g, n, ratio):
    """Lecture avec rebouclage identique a readOne (mono)."""
    ls, le = g["ls"], g["le"]
    pos = float(ls)
    out = [0.0] * n
    frames = g["frames"]
    for i in range(n):
        if le > ls and pos >= le:
            pos -= (le - ls)
        i0 = int(pos)
        i1 = i0 + 1
        if i1 >= frames:
            break
        fr = pos - i0
        out[i] = 0.5 * ((pcm[2 * i0] + (pcm[2 * i1] - pcm[2 * i0]) * fr) +
                        (pcm[2 * i0 + 1] + (pcm[2 * i1 + 1] - pcm[2 * i0 + 1]) * fr))
        pos += ratio
    return out


def residual(x, rate):
    w = int(0.005 * rate)
    env = [math.sqrt(sum(v * v for v in x[a:a + w]) / w)
           for a in range(0, len(x) - w, w)]
    if len(env) < 40:
        return 0.0, 0.0
    env = env[int(0.3 * rate / w):]
    N = len(env)
    M = max(2, int(0.25 * rate / w))
    res = []
    for i in range(N):
        a, b = max(0, i - M), min(N, i + M + 1)
        t = sum(env[a:b]) / (b - a)
        res.append(env[i] / max(1e-12, t) - 1.0)
    rms = math.sqrt(sum(v * v for v in res) / N)
    e0 = sum(v * v for v in res) or 1.0
    best, bl = 0.0, 0
    for lag in range(int(0.05 * rate / w), min(N // 2, int(0.6 * rate / w))):
        a = sum(res[i] * res[i + lag] for i in range(N - lag)) / e0
        if a > best:
            best, bl = a, lag
    return rms, bl * w / rate


def main():
    path, note = sys.argv[1], int(sys.argv[2])
    quantize = len(sys.argv) > 3 and sys.argv[3] == "quant"
    rate, pcmraw, regs = load(path)
    global d
    # decode toutes les regions utiles
    for g in regs:
        g["pcm"] = decode(pcmraw, g["off"], g["frames"])
    f0 = 440.0 * 2 ** ((note - 69) / 12.0)
    per = {}
    for i, g in enumerate(regs):
        if g["le"] > g["ls"]:
            f0g = 440.0 * 2 ** ((g["root"] - 69) / 12.0)
            per[i] = real_period(g["pcm"], g["ls"], g["le"], rate, f0g) or (rate / f0g)
    regs2 = [dict(g) for g in regs]
    harmonize(regs2, quantize, rate, per)
    # couches disponibles sur la racine la plus proche de la note
    by_root = defaultdict(list)
    for i, g in enumerate(regs2):
        by_root[g["root"]].append(i)
    root = min(by_root, key=lambda r: abs(r - note))
    idxs = sorted(by_root[root], key=lambda i: regs2[i]["layer"])
    print(f"# {path} note={note} racine={root} couches={len(idxs)} "
          f"{'QUANTIFIE' if quantize else 'actuel'}")
    print(f"{'lay':>3} {'loopLen':>8} {'periodes':>9} {'residu':>8} {'periode':>8}")
    for i in idxs:
        g = regs2[i]
        d = list(regs[i]["pcm"])
        flatten(d, g, rate)
        ratio = f0 / (440.0 * 2 ** ((g["root"] - 69) / 12.0))
        x = read_loop(d, g, int(6.0 * rate), ratio)
        rms, p = residual(x, rate)
        L = g["le"] - g["ls"]
        print(f"{g['layer']:>3} {L:>8} {L / per[i]:>9.2f} {rms * 100:>7.1f}% {p:>7.2f}s")


if __name__ == "__main__":
    main()
