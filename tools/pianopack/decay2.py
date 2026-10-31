#!/usr/bin/env python3
"""Double decroissance par partiel (modele de Weinreich), validee sur synthetique.

Pourquoi un T60 simple ne marche pas : la decroissance d'un partiel de piano
n'est PAS exponentielle.  Les 2-3 cordes de l'unisson partent en phase, se
depolarisent (decroissance rapide) puis se remettent en phase : la suite est une
decroissance lente.  C'est le "aftersound" du piano, decrit par Weinreich.

Un ajustement exponentiel simple sur -5..-35 dB tombe au milieu des deux regimes
et sa pente depend donc fortement de la fenetre choisie -- d'ou les T60 qui
oscillent (mesure sur C3 : h2 = 6,4 s, h3 = 15,4 s, h5 = 40,7 s).

Modele :
    e(t) = A1*exp(-t/tau1) + A2*exp(-t/tau2),   tau1 < tau2
Estimation par pelage : la queue donne (A2, tau2), on soustrait et on ajuste le
reste pour (A1, tau1).  Deux droites, pas d'optimisation non lineaire.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure import f0_nominal, fit_log_score, load_mono, midi_of  # noqa: E402

W, HOP = 4096, 1024


def stft_env(m, sr, fn, n_harm, dt_out=0.05):
    """Enveloppe du partiel, echantillonnee a dt_out, par max dans une bande etroite."""
    n = max((len(m) - W) // HOP, 0)
    fr = np.fft.rfftfreq(W, 1.0 / sr)
    rel = min(0.45 / n_harm, 0.02)
    lo, hi = int(np.searchsorted(fr, fn * (1 - rel))), int(np.searchsorted(fr, fn * (1 + rel)))
    hi = max(hi, lo + 1)
    if lo < 0 or hi > len(fr):
        return None, None
    win = np.hanning(W)
    env = np.empty(n)
    for i in range(n):
        env[i] = np.abs(np.fft.rfft(m[i * HOP:i * HOP + W] * win))[lo:hi].max()
    t = np.arange(n) * HOP / sr
    # reechantillonne a pas fixe
    if n < 2:
        return None, None
    tt = np.arange(t[0], t[-1], dt_out)
    ee = np.interp(tt, t, env)
    return tt, ee


def smooth_max(e, w):
    w = max(int(w), 1)
    pad = np.pad(e, w // 2, mode="edge")
    return np.array([pad[i:i + w].max() for i in range(len(e))])


def peel_fit(t, e, tail_frac=0.35):
    """Double exponentielle par pelage.  Renvoie tau1, tau2, A1, A2, et la qualite."""
    pk = e.max()
    if pk <= 0 or len(e) < 40:
        return None
    e = e / pk
    i0 = int(len(e) * (1 - tail_frac))
    tt, ee = t[i0:], e[i0:]
    ok = ee > 1e-4
    if ok.sum() < 12:
        return None
    s2, c2 = np.polyfit(tt[ok], np.log(ee[ok]), 1)
    if s2 >= 0:
        return None
    tau2 = -1.0 / s2
    A2 = float(np.exp(c2 + s2 * t[0]))
    # retirer la composante lente
    rest = e - A2 * np.exp(s2 * t)
    i1 = int(len(e) * 0.05)
    mask = (rest[i1:] > 1e-4) & (np.arange(len(rest) - i1) < len(rest) * 0.55)
    if mask.sum() < 10:
        return None
    tr, rr = t[i1:][mask], rest[i1:][mask]
    s1, c1 = np.polyfit(tr, np.log(rr), 1)
    if s1 >= 0 or s1 > s2:
        return None
    tau1 = -1.0 / s1
    A1 = float(np.exp(c1 + s1 * t[0]))
    model = A1 * np.exp(s1 * t) + A2 * np.exp(s2 * t)
    err = float(np.sqrt(np.mean((20 * np.log10(model + 1e-9) - 20 * np.log10(e + 1e-9)) ** 2)))
    return {"tau1": float(tau1), "tau2": float(tau2), "A1": A1, "A2": A2,
            "ratio": float(A1 / (A2 + 1e-12)), "rms_db": err,
            "t60_fast": float(2.303 * tau1), "t60_slow": float(2.303 * tau2)}


def synth_double(f0, B, tau1, tau2, A1, A2, sr=44100, dur=8.0, n_max=12, seed=0):
    rng = np.random.default_rng(seed)
    t = np.arange(int(dur * sr)) / sr
    y = np.zeros_like(t)
    for n in range(1, n_max + 1):
        fn = n * f0 * np.sqrt(1.0 + B * n * n)
        if fn > sr * 0.45:
            break
        a1, a2 = A1 / n ** 0.8, A2 / n ** 0.8
        env = a1 * np.exp(-t / tau1) + a2 * np.exp(-t / tau2)
        y += env * np.sin(2 * np.pi * fn * t + rng.uniform(0, 6.28))
    y += rng.normal(0, 1e-5, len(t))
    return y / np.abs(y).max() * 0.8


def validate():
    print("VALIDATION : double decroissance sur signal synthetique")
    print(f"{'cas':18s} {'tau1 vrai':>10s} {'tau1 fit':>10s} {'tau2 vrai':>10s} "
          f"{'tau2 fit':>10s} {'err1':>7s} {'err2':>7s}")
    cases = [("C2", 65.4, 1.5, 25.0), ("C3", 130.8, 1.0, 15.0),
             ("C4", 261.6, 0.7, 8.0), ("C5", 523.3, 0.4, 4.0)]
    ok = 0
    for note, f0, tau1, tau2 in cases:
        y = synth_double(f0, 1e-4, tau1, tau2, 0.7, 0.3)
        sr = 44100
        tt, ee = stft_env(y, sr, f0, 1, dt_out=0.05)
        r = peel_fit(tt, smooth_max(ee, 3))
        if not r:
            print(f"{note:18s} {tau1:10.2f} {'ECHEC':>10s} {tau2:10.2f} {'':>10s}")
            continue
        e1 = abs(r["tau1"] - tau1) / tau1 * 100
        e2 = abs(r["tau2"] - tau2) / tau2 * 100
        ok += (e1 < 25 and e2 < 25)
        print(f"{note:18s} {tau1:10.2f} {r['tau1']:10.2f} {tau2:10.2f} "
              f"{r['tau2']:10.2f} {e1:6.1f}% {e2:6.1f}%")
    print(f"-> {ok}/{len(cases)} a moins de 25 % sur les deux constantes")


def campaign(notes, layer, out_path):
    res = []
    for nt in notes:
        try:
            m, sr = load_mono("%s %s.flac" % (layer, nt))
        except Exception as e:
            print("  %-6s ECHEC %s" % (nt, e), file=sys.stderr)
            continue
        f0n = f0_nominal(nt)
        f0, B = fit_log_score(m, sr, f0n)
        row = {"note": nt, "midi": midi_of(nt), "f0": round(f0, 3), "B": B, "partials": []}
        for n in range(1, 11):
            fn = n * f0 * np.sqrt(1.0 + B * n * n)
            if fn > sr * 0.45:
                break
            tt, ee = stft_env(m, sr, fn, n)
            if tt is None or ee is None:
                continue
            r = peel_fit(tt, smooth_max(ee, 7))
            if r:
                r = {k: (round(v, 3) if isinstance(v, float) else v) for k, v in r.items()}
                r["n"] = n
                r["f"] = round(fn, 1)
                row["partials"].append(r)
        res.append(row)
        with open(out_path, "w") as fh:
            json.dump(res, fh, indent=1)
        p1 = row["partials"][0] if row["partials"] else {}
        print("  %-6s tau1=%6s tau2=%7s  rms=%5s dB" % (
            nt, p1.get("tau1"), p1.get("tau2"), p1.get("rms_db")), file=sys.stderr)
    print("ecrit %s (%d notes)" % (out_path, len(res)), file=sys.stderr)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--validate":
        validate()
    else:
        layer = sys.argv[1] if len(sys.argv) > 1 else "PP"
        spec = sys.argv[2] if len(sys.argv) > 2 else "C2,C3,C4,F4"
        if spec == "all":
            import urllib.request
            req = urllib.request.Request(
                "https://api.github.com/repos/sfzinstruments/SplendidGrandPiano"
                "/git/trees/master?recursive=1")
            tok = os.environ.get("GITHUB_TOKEN")
            if tok:
                req.add_header("Authorization", "Bearer " + tok)
            tree = json.load(urllib.request.urlopen(req, timeout=60))["tree"]
            have = sorted(x["path"].split("/")[1] for x in tree
                          if x["path"].startswith("Samples/"))
            notes = [n.rsplit(".", 1)[0].split(" ", 1)[1] for n in have
                     if n.startswith(layer + " ")]
        else:
            notes = spec.split(",")
        campaign(notes, layer, sys.argv[3] if len(sys.argv) > 3
                 else "tools/pianopack/out/decays.json")
