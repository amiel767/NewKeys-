#!/usr/bin/env python3
"""T60 par partiel, robuste au battement d'unisson.

Pourquoi la version precedente echouait : un partiel de piano est porte par 2-3
cordes legerement desaccordees.  Leur somme est modulee en amplitude (battement),
donc l'enveloppe d'un partiel oscille.  Une regression exponentielle sur
l'enveloppe brute ne converge pas -- mesure sur C3 : h1 = 0,9 s puis h4 = 15,9 s.

Correctif : on prend l'ENVELOPPE SUPERIEURE (max glissant sur ~0,35 s) avant la
regression.  La crête du battement suit la decroissance moyenne du partiel, les
oscillations disparaissent.  On mesure aussi le taux de battement, qui est
n * (ecart d'accord entre cordes) et doit donc doubler a chaque octave.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure import f0_nominal, fit_log_score, load_mono, midi_of  # noqa: E402

W, HOP = 4096, 1024


def stft(m, sr):
    n = max((len(m) - W) // HOP, 0)
    fr = np.fft.rfftfreq(W, 1.0 / sr)
    S = np.empty((n, len(fr)))
    win = np.hanning(W)
    for i in range(n):
        S[i] = np.abs(np.fft.rfft(m[i * HOP:i * HOP + W] * win))
    return S, fr, HOP / sr


def band_envelope(S, fr, fn, rel):
    lo = int(np.searchsorted(fr, fn * (1 - rel)))
    hi = int(np.searchsorted(fr, fn * (1 + rel)))
    if hi <= lo:
        hi = lo + 1
    if lo < 0 or hi > S.shape[1]:
        return None
    return S[:, lo:hi].max(axis=1)


def running_max(e, w):
    w = max(int(w), 1)
    pad = np.pad(e, w // 2, mode="edge")
    return np.array([pad[i:i + w].max() for i in range(len(e))])


def t60(e, dt):
    """T60, seulement si le partiel decroit assez pour qu'on puisse le mesurer.

    Un T60 de 95 s sur un fichier de 12 s est un artefact : la regression porte
    sur la seule portion disponible et l'extrapole.  On exige donc une chute
    reelle d'au moins 30 dB ; sinon on renvoie la chute observee, ce qui borne
    la valeur par le bas au lieu d'inventer un chiffre.
    """
    pk = e.max()
    if pk <= 0:
        return None, 0.0
    db = 20 * np.log10(e / pk + 1e-12)
    drop = float(-db.min())
    idx = np.where((db < -5.0) & (db > -35.0))[0]
    if len(idx) < 8:
        return None, drop
    slope = np.polyfit(idx * dt, db[idx], 1)[0]
    if slope >= 0:
        return None, drop
    return (float(-60.0 / slope) if drop >= 30.0 else None), drop


def beat_hz(e, dt):
    """Frequence de modulation de l'enveloppe, tendance polynomiale retiree."""
    if len(e) < 64:
        return None
    t = np.arange(len(e)) * dt
    trend = np.polyval(np.polyfit(t, e, 2), t)
    d = e - trend
    sp = np.abs(np.fft.rfft(d * np.hanning(len(d))))
    f = np.fft.rfftfreq(len(d), dt)
    m = (f > 0.3) & (f < 20)
    if not m.any():
        return None
    k = np.argmax(sp[m])
    return float(f[m][k])


def analyse(name, n_max=10):
    m, sr = load_mono(name)
    note = name.split(" ", 1)[1].rsplit(".", 1)[0]
    f0n = f0_nominal(note)
    f0, B = fit_log_score(m, sr, f0n)
    S, fr, dt = stft(m, sr)
    smooth = max(int(0.35 / dt), 3)
    out = {"sample": name, "note": note, "midi": midi_of(note),
           "f0": round(f0, 3), "B": B, "partials": []}
    for n in range(1, n_max + 1):
        fn = n * f0 * np.sqrt(1.0 + B * n * n)
        if fn > sr * 0.45:
            break
        rel = min(0.45 / n, 0.02)          # < demi-espacement entre partiels
        e = band_envelope(S, fr, fn, rel)
        if e is None or e.max() <= 0:
            continue
        raw, drop_r = t60(e, dt)
        e_up = running_max(e, smooth)
        up, drop_u = t60(e_up, dt)
        out["partials"].append({
            "n": n, "f": round(fn, 1),
            "t60_raw": round(raw, 2) if raw else None,
            "t60_up": round(up, 2) if up else None,
            "drop_db": round(drop_u, 1),
            "beat_hz": (round(b, 2) if (b := beat_hz(e, dt)) else None),
        })
    return out


def main():
    notes = sys.argv[1].split(",") if len(sys.argv) > 1 else None
    layer = sys.argv[2] if len(sys.argv) > 2 else "PP"
    out_path = sys.argv[3] if len(sys.argv) > 3 else "tools/pianopack/out/decays.json"
    if notes is None:
        import urllib.request
        req = urllib.request.Request(
            "https://api.github.com/repos/sfzinstruments/SplendidGrandPiano/git/trees/master?recursive=1")
        tok = os.environ.get("GITHUB_TOKEN")
        if tok:
            req.add_header("Authorization", "Bearer " + tok)
        tree = json.load(urllib.request.urlopen(req, timeout=60))["tree"]
        have = set(x["path"].split("/")[1] for x in tree if x["path"].startswith("Samples/"))
        notes = [n.rsplit(".", 1)[0] for n in sorted(have) if n.startswith(layer + " ")]
    res = []
    for i, nt in enumerate(notes, 1):
        try:
            r = analyse("%s %s.flac" % (layer, nt))
        except Exception as e:
            print("  %-6s ECHEC %s" % (nt, e), file=sys.stderr)
            continue
        res.append(r)
        with open(out_path, "w") as fh:
            json.dump(res, fh, indent=1)
        h1 = next((p for p in r["partials"] if p["n"] == 1), {})
        print("  %-6s n=%2d  h1 raw=%6s up=%6s  beat=%5s Hz"
              % (nt, len(r["partials"]), h1.get("t60_raw"), h1.get("t60_up"),
                 h1.get("beat_hz")), file=sys.stderr)
    print("ecrit %s (%d notes)" % (out_path, len(res)), file=sys.stderr)


if __name__ == "__main__":
    main()
