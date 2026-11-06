#!/usr/bin/env python3
"""Profile l'enveloppe d'une note tenue, repliee sur la periode de boucle.

But : montrer s'il existe une MARCHE de niveau au point de rebouclage. C'est elle
qui s'entend comme un « dain dain » periodique : l'enveloppe remonte ou retombe a
chaque tour, et rien d'autre dans la chaine ne peut le produire.

    python3 tools/pianopack/loop_seam_profile.py app/src/main/assets/pianopack/piano.bin 60
"""
import sys, math
sys.path.insert(0, "tools/pianopack")
from sim_loop import load, decode, real_period, harmonize, flatten, read_loop  # noqa: E402


def main():
    path, note = sys.argv[1], int(sys.argv[2])
    quantize = len(sys.argv) > 3 and sys.argv[3] == "quant"
    rate, pcmraw, regs = load(path)
    for g in regs:
        g["pcm"] = decode(pcmraw, g["off"], g["frames"])
    per = {}
    for i, g in enumerate(regs):
        if g["le"] > g["ls"]:
            f0g = 440.0 * 2 ** ((g["root"] - 69) / 12.0)
            per[i] = real_period(g["pcm"], g["ls"], g["le"], rate, f0g) or (rate / f0g)
    regs2 = [dict(g) for g in regs]
    harmonize(regs2, quantize, rate, per)
    by_root = {}
    for i, g in enumerate(regs2):
        if g["le"] > g["ls"]:
            by_root.setdefault(g["root"], []).append(i)
    root = min(by_root, key=lambda r: abs(r - note))
    idxs = sorted(by_root[root], key=lambda i: regs2[i]["layer"])
    f0 = 440.0 * 2 ** ((note - 69) / 12.0)
    print(f"# {path} note={note} racine={root} couches={len(idxs)} "
          f"{'QUANT' if quantize else 'actuel'}")
    print(f"{'lay':>3} {'loopLen':>8} {'residu':>7} {'marcher':>8} {'pos':>6} {'avant/apres':>12}")
    for i in idxs:
        g = regs2[i]
        d = list(regs[i]["pcm"])
        flatten(d, g, rate)
        ratio = f0 / (440.0 * 2 ** ((g["root"] - 69) / 12.0))
        L = g["le"] - g["ls"]
        x = read_loop(d, g, int(4.0 * rate), ratio)
        w = int(0.002 * rate)
        env = [math.sqrt(sum(v * v for v in x[a:a + w]) / w)
               for a in range(0, len(x) - w, w)]
        env = env[len(env) // 3:]              # apres l'attaque
        nper = max(1, int(round(L / ratio / w)))   # fenetres par tour
        fold = [0.0] * nper
        cnt = [0] * nper
        for k, e in enumerate(env):
            fold[k % nper] += e
            cnt[k % nper] += 1
        fold = [fold[k] / max(1, cnt[k]) for k in range(nper)]
        mean = sum(fold) / nper
        if mean <= 0:
            continue
        lo = min(fold) / mean
        hi = max(fold) / mean
        step = 20 * math.log10(max(1e-9, fold[0]) / max(1e-9, fold[-1]))
        res = math.sqrt(sum((v / mean - 1) ** 2 for v in fold) / nper)
        imax = max(range(nper), key=lambda k: fold[k])
        imin = min(range(nper), key=lambda k: fold[k])
        print(f"{g['layer']:>3} {L:>8} {res * 100:>6.1f}% "
              f"{20 * math.log10(hi / lo):>7.1f}dB {imax / nper:>5.2f} "
              f"{step:>+11.2f}dB  (creux@{imin / nper:.2f})")


if __name__ == "__main__":
    main()
