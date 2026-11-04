#!/usr/bin/env python3
"""Mesure l'artefact « echo / delai » de la queue d'une note de piano.

Le defaut s'entend comme une remontee periodique de l'enveloppe : la note ne
decroit plus, elle « respire » au rythme de la boucle de l'echantillon.  Le banc
C++ (`hold_residual.cpp`) dumpe le signal, ce script en fait la lecture.

Le signal de reference est une note tenue 8 s.  Un piano sain decroit de facon
monotone ; toute remontee de plus de 0,5 dB dans la tenue est un artefact de
rebouclage, et l'ECART entre remontees vaut la longueur de boucle.

Usage :
    tools/aetherfx/tail_echo.py <dump.f32> [seconde_de_debut]
"""
import sys

import numpy as np

SR = 44100
RISE_DB = 0.5


def envelope(x, step_s=0.020):
    w = int(step_s * SR)
    n = len(x) // w
    return np.sqrt(np.mean(x[: n * w].reshape(n, w) ** 2, axis=1)), step_s


def main():
    path = sys.argv[1]
    start = float(sys.argv[2]) if len(sys.argv) > 2 else 1.0
    x = np.fromfile(path, dtype=np.float32)
    env, step = envelope(x)
    db = 20 * np.log10(np.maximum(env, 1e-9))
    d = np.diff(db)
    up = np.where(d > RISE_DB)[0]
    t = up * step
    t = t[t > start]
    gaps = np.diff(t)
    print(f"{path} : {len(x) / SR:.1f} s")
    print(f"  remontees >{RISE_DB} dB apres {start:.1f} s : {len(t)}")
    if len(gaps):
        vals, counts = np.unique(np.round(gaps, 2), return_counts=True)
        top = sorted(zip(counts, vals), reverse=True)[:3]
        print("  ecarts les plus frequents : "
              + ", ".join(f"{v:.2f} s x{c}" for c, v in top))
    # Metrique principale : la PLUS FORTE remontee d'enveloppe, en dB.
    #
    # C'est l'amplitude du « dain dain ».  On ne somme PAS les remontees : un
    # tremolo naturel du pack (tresor d'un piano electrique) cumule beaucoup de
    # petites remontees de 0,3 dB sans que rien ne s'entende, et le cumul devient
    # trompeur.  Ce qui s'entend, c'est une remontee isolee et forte.
    lo = int(start / step)
    seg = db[lo:]
    peak = seg.max()
    aud = seg[seg > peak - 25]        # on s'arrete ou la note n'est plus audible
    dd = np.diff(aud) if len(aud) > 1 else np.array([0.0])
    forte = dd[dd > 0.3]
    print(f"  REMONTEE MAX : {dd.max():5.2f} dB   ({len(forte)} remontees >0.3 dB, "
          f"cumul {forte.sum():.1f} dB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
