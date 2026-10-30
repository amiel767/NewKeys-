#!/usr/bin/env python3
"""Mesure des parametres physiques d'une banque de piano echantillonnee.

Tout ce qui suit est mesure sur le signal, jamais estime a l'oeil.

  f0, B   : ajustement a deux parametres de la serie inharmonique
            f_n = n*f0*sqrt(1 + B*n^2).  C'est la methode des accordeurs :
            on cherche le couple (f0, B) dont les partiels tombent le mieux
            sur les pics du spectre.  Chercher f0 seul se trompe d'octave.
  T60_n   : decroissance de chaque partiel (regression sur -5..-35 dB).
  battement : ecart entre les cordes de l'unisson (2-3 pics dans le partiel 1).
  transitoire : duree avant stabilisation spectrale.
  boucle  : longueur de boucle raccordable (enveloppe normalisee).

Usage :
    python3 measure.py --notes C2,C3,C4 --layers PP,FF
    python3 measure.py --all
    python3 measure.py --all --out measurements.json
"""
import argparse
import collections
import json
import os
import sys
import urllib.parse
import urllib.request

import numpy as np
import soundfile as sf

REPO = "sfzinstruments/SplendidGrandPiano"
BRANCH = "master"
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")

NOTE_BASE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


# La banque nomme les fichiers avec la convention **Roland** : le do central est
# "C3".  La notation scientifique l'appelle C4.  Mesure du contenu des fichiers
# (pic fondamental par FFT) : "C2" porte 130 Hz, "C3" porte 262 Hz, "A#2" porte
# 233 Hz — soit exactement +1 octave par rapport a la notation scientifique.
#
# Sans ce decalage, chaque region recevait un root 12 demi-tons trop bas et le
# moteur transposait TOUT une octave trop haut : le piano sonnait maigre et
# criard, « comme une guitare ».  Mesure avant correction : note 60 demandee
# (C4 = 261,6 Hz) sortait a 525 Hz.
BANK_OCTAVE_OFFSET = 12


def midi_of(note):
    """'C3' -> 60.  Convention Roland de la banque (do central = C3)."""
    if len(note) > 1 and note[1] in "#b":
        step = NOTE_BASE[note[0]] + (1 if note[1] == "#" else -1)
        octave = int(note[2:])
    else:
        step = NOTE_BASE[note[0]]
        octave = int(note[1:])
    return 12 * (octave + 1) + step + BANK_OCTAVE_OFFSET


def f0_nominal(note):
    return 440.0 * 2.0 ** ((midi_of(note) - 69) / 12.0)


def sample_path(name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        url = ("https://raw.githubusercontent.com/%s/%s/Samples/%s"
               % (REPO, BRANCH, urllib.parse.quote(name)))
        with urllib.request.urlopen(url, timeout=120) as r:
            data = r.read()
        with open(path, "wb") as fh:
            fh.write(data)
    return path


def load_mono(name):
    x, sr = sf.read(sample_path(name), always_2d=True)
    return x.mean(axis=1).astype(np.float64), sr


# ---------------------------------------------------------------- f0 et B

def fit_log_score(m, sr, f0_nom, n_max=20, eps=1e-3):
    """Score en log-magnitude, puis raffinement.

    Pourquoi un score en log : sommer des magnitudes brutes laisse un seul pic
    fort dominer, et diviser par le nombre de partiels trouves recompense un B
    qui n'en place que quelques-uns.  Ici on divise par n_max (constante) et les
    partiels hors bande contribuent log(eps) : ils sont donc penalises.

    L'optimiseur de la methode 1 tombait dans un mauvais minimum dans les aigus
    (B mesure a 45 % d'etirement, contre toute physique) ; ce score ne le permet
    pas, car rater la serie harmonique coute cher.
    """
    a = int(0.2 * sr)
    seg = m[a:] if len(m) - a > 32768 else m
    W = 1 << 17
    if len(seg) >= W:
        step = (len(seg) - W) // 2
        S = np.zeros(W // 2 + 1)
        for c in range(3):
            S += np.abs(np.fft.rfft(seg[c * step:c * step + W] * np.hanning(W)))
        S /= 3
    else:
        W = 1 << int(np.ceil(np.log2(len(seg))))
        S = np.abs(np.fft.rfft(seg * np.hanning(len(seg)), n=W))
    S = S / (S.max() + 1e-30)
    df = sr / W
    fmax = sr * 0.45

    def mag(f):
        x = f / df
        k = int(x)
        if k < 1 or k >= len(S) - 1:
            return eps
        y0, y1, y2 = S[k - 1], S[k], S[k + 1]
        den = y0 - 2 * y1 + y2
        d = 0.5 * (y0 - y2) / den if abs(den) > 1e-12 else 0.0
        return max(y1 - 0.25 * (y0 - y2) * float(np.clip(d, -1, 1)), eps)

    def score(f0, B):
        tot = 0.0
        for n in range(1, n_max + 1):
            fn = n * f0 * np.sqrt(1.0 + B * n * n)
            tot += np.log(mag(fn)) if fn <= fmax else np.log(eps)
        return tot / n_max

    best = (-1e18, f0_nom, 1e-4)
    for cents in np.arange(-50, 50.1, 2.0):
        f0 = f0_nom * 2.0 ** (cents / 1200.0)
        for B in np.logspace(-5.5, -2.3, 40):
            sc = score(f0, B)
            if sc > best[0]:
                best = (sc, f0, B)
    _, f0c, Bc = best
    for cents in np.arange(-4, 4.01, 0.25):
        f0 = f0c * 2.0 ** (cents / 1200.0)
        for B in Bc * np.logspace(-0.12, 0.12, 13):
            sc = score(f0, B)
            if sc > best[0]:
                best = (sc, f0, B)
    return best[1], best[2]



def fit_f0_B(m, sr, f0_nom, n_partials=18):
    """Cherche (f0, B) par ajustement de peigne, puis affine.

    Le score est la somme des magnitudes aux positions predites, divisee par le
    nombre de partiels retenus : sans cette division, un B eleve qui place peu
    de partiels gagnerait artificiellement.
    """
    a = int(0.25 * sr)
    seg = m[a:] if len(m) - a > 8192 else m
    W = min(len(seg), 1 << 16)
    S = np.abs(np.fft.rfft(seg[:W] * np.hanning(W), n=W * 2))
    df = sr / (W * 2)
    fmax = sr * 0.45

    def score(f0, B):
        tot, n_used = 0.0, 0
        for n in range(1, n_partials + 1):
            fn = n * f0 * np.sqrt(1.0 + B * n * n)
            if fn > fmax:
                break
            k = int(round(fn / df))
            if 1 <= k < len(S) - 1:
                # interpolation parabolique : le pic reel n'est pas sur une case
                y0, y1, y2 = S[k - 1], S[k], S[k + 1]
                den = (y0 - 2 * y1 + y2)
                d = 0.5 * (y0 - y2) / den if abs(den) > 1e-12 else 0.0
                tot += y1 - 0.25 * (y0 - y2) * np.clip(d, -1, 1)
                n_used += 1
        return tot / n_used if n_used >= 3 else 0.0

    # passe grossiere
    best = (0.0, f0_nom, 1e-4)
    for cents in np.arange(-60.0, 60.1, 4.0):
        f0 = f0_nom * 2.0 ** (cents / 1200.0)
        for B in np.logspace(-5.2, -2.0, 26):
            sc = score(f0, B)
            if sc > best[0]:
                best = (sc, f0, B)
    # passe fine
    _, f0c, Bc = best
    for cents in np.arange(-6.0, 6.1, 0.5):
        f0 = f0c * 2.0 ** (cents / 1200.0)
        for B in Bc * np.logspace(-0.25, 0.25, 11):
            sc = score(f0, B)
            if sc > best[0]:
                best = (sc, f0, B)
    return best[1], best[2]


# ---------------------------------------------------------------- T60

def partial_decays(m, sr, f0, B, n_max=12):
    """T60 de chaque partiel : regression lineaire sur la portion -5..-35 dB."""
    hop, W = 1024, 4096
    n_frames = (len(m) - W) // hop
    if n_frames < 12:
        return []
    fr = np.fft.rfftfreq(W, 1.0 / sr)
    env = np.empty((n_frames, len(fr)))
    for i in range(n_frames):
        env[i] = np.abs(np.fft.rfft(m[i * hop:i * hop + W] * np.hanning(W)))
    t = np.arange(n_frames) * hop / sr
    out = []
    for n in range(1, n_max + 1):
        fn = n * f0 * np.sqrt(1.0 + B * n * n)
        if fn > sr * 0.45:
            break
        k = int(np.argmin(np.abs(fr - fn)))
        e = env[:, max(0, k - 2):k + 3].max(axis=1)
        pk = e.max()
        if pk <= 0:
            continue
        idx = np.where((e > pk * 10 ** (-35 / 20.0)) & (e < pk * 10 ** (-5 / 20.0)))[0]
        if len(idx) < 8:
            continue
        slope = np.polyfit(t[idx], 20 * np.log10(e[idx] + 1e-12), 1)[0]
        if slope < 0:
            out.append((n, float(fn), float(-60.0 / slope)))
    return out


# ---------------------------------------------------------------- battement

def unison_beating(m, sr, f0):
    """Ecart entre cordes de l'unisson : plusieurs pics dans le partiel 1."""
    W = min(len(m), 1 << 15)
    S = np.abs(np.fft.rfft(m[:W] * np.hanning(W), n=W * 2))
    df = sr / (W * 2)
    lo, hi = int(f0 * 0.965 / df), int(f0 * 1.035 / df)
    if hi - lo < 8:
        return None
    seg = S[lo:hi]
    pk = int(np.argmax(seg))
    peaks = [i for i in range(1, len(seg) - 1)
             if seg[i] > seg[i - 1] and seg[i] >= seg[i + 1] and seg[i] > seg[pk] * 0.12]
    others = [i for i in peaks if abs(i - pk) > 2]
    if not others:
        return None
    j = min(others, key=lambda i: abs(i - pk))
    return abs((j - pk) * df)


# ---------------------------------------------------------------- transitoire

def transient_end(m, sr):
    """Fin du transitoire : l'energie par bandes cesse de varier vite."""
    W, hop = 4096, 2048
    fr = np.fft.rfftfreq(W, 1.0 / sr)
    bands = [(fr >= a) & (fr < b) for a, b in
             ((60, 250), (250, 800), (800, 2500), (2500, 9000))]
    E = []
    for i in range(0, len(m) - W, hop):
        S = np.abs(np.fft.rfft(m[i:i + W] * np.hanning(W))) ** 2
        E.append([S[b].sum() for b in bands])
    if len(E) < 6:
        return None
    E = np.array(E)
    ipk = int(np.argmax(E.sum(axis=1)))
    for i in range(ipk + 2, len(E)):
        if E[i - 1].sum() > 0 and (E[i] / E[i - 1]).max() > 0.80:
            return float(i * hop / sr)
    return None


# ---------------------------------------------------------------- boucle

def find_loop(m, sr, f0):
    """Boucle raccordable.  L'enveloppe est aplatie avant correlation : un piano
    decroit, donc deux points espaces n'ont jamais la meme amplitude brute."""
    P = max(int(sr / f0), 8)
    W = max(2 * P, 64)
    env = np.sqrt(np.convolve(m * m, np.ones(W) / W, "same")) + 1e-9
    z = m / env
    t0 = max(int(0.30 * sr), 10 * P)
    if t0 + int(1.0 * sr) >= len(m):
        t0 = max(int(0.15 * sr), 6 * P)
        if t0 + int(0.4 * sr) >= len(m):
            return None
    ref = z[t0:t0 + 4 * P]
    if len(ref) < 2 * P:
        return None
    for L in range(8 * P, min(int(1.2 * sr), len(m) - t0 - 4 * P), max(P // 2, 4)):
        seg = z[t0 + L:t0 + L + 4 * P]
        if len(seg) < len(ref):
            break
        c = float(np.dot(ref, seg) / (np.linalg.norm(ref) * np.linalg.norm(seg) + 1e-12))
        if c > 0.96:
            return float(L / sr), c
    return None


# ---------------------------------------------------------------- pilote

def measure_one(name, do_loop=True):
    m, sr = load_mono(name)
    note = name.split(" ", 1)[1].rsplit(".", 1)[0]
    layer = name.split(" ", 1)[0]
    f0n = f0_nominal(note)
    f0, B = fit_log_score(m, sr, f0n)
    res = {
        "sample": name, "layer": layer, "note": note, "midi": midi_of(note),
        "sr": sr, "dur_s": round(len(m) / sr, 3),
        "f0_nominal": round(f0n, 3), "f0_fit": round(f0, 3),
        "cents_off": round(1200 * np.log2(f0 / f0n), 2),
        "B": float(B),
        "stretch_n10_pct": round(100 * (np.sqrt(1 + B * 100) - 1), 3),
        "transient_s": transient_end(m, sr),
        "beating_hz": unison_beating(m, sr, f0),
        "decays": [{"n": n, "f": round(f, 1), "t60": round(t, 2)}
                   for n, f, t in partial_decays(m, sr, f0, B)],
    }
    if do_loop:
        lp = find_loop(m, sr, f0)
        res["loop_s"], res["loop_corr"] = (round(lp[0], 3), round(lp[1], 4)) if lp else (None, None)
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--notes", default=None, help="ex: C2,C3,C4")
    ap.add_argument("--layers", default="PP", help="ex: PP,FF")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--no-loop", action="store_true")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    # inventaire de la banque (une seule requete d'arbre)
    tree_url = ("https://api.github.com/repos/%s/git/trees/%s?recursive=1"
                % (REPO, BRANCH))
    req = urllib.request.Request(tree_url)
    tok = os.environ.get("GITHUB_TOKEN")
    if tok:
        req.add_header("Authorization", "Bearer " + tok)
    with urllib.request.urlopen(req, timeout=60) as r:
        tree = json.load(r)["tree"]
    names = [x["path"].split("/")[1] for x in tree if x["path"].startswith("Samples/")]

    by_note = collections.defaultdict(list)
    for n in names:
        layer, note = n.split(" ", 1)
        by_note[note.rsplit(".", 1)[0]].append((layer, n))

    if args.all:
        jobs = [(nt, sorted(v)[0][1]) for nt, v in sorted(by_note.items(), key=lambda kv: midi_of(kv[0]))]
    else:
        notes = (args.notes or "C3").split(",")
        layers = args.layers.split(",")
        jobs = []
        for nt in notes:
            have = dict(by_note.get(nt, []))
            for L in layers:
                if L in have:
                    jobs.append((nt, have[L]))

    print("mesure de %d echantillons" % len(jobs), file=sys.stderr)
    out = []
    for i, (nt, nm) in enumerate(jobs, 1):
        try:
            r = measure_one(nm, do_loop=not args.no_loop)
        except Exception as e:
            print("  %-12s ECHEC %s" % (nm, e), file=sys.stderr)
            continue
        out.append(r)
        if args.out:
            with open(args.out, "w") as fh:
                json.dump(out, fh, indent=1)
        print("  %-12s f0=%7.2f (%+6.2fc)  B=%.3e  n10=%5.2f%%  T60h1=%s  loop=%s"
              % (nm, r["f0_fit"], r["cents_off"], r["B"], r["stretch_n10_pct"],
                 r["decays"][0]["t60"] if r["decays"] else "n/a",
                 r.get("loop_s")), file=sys.stderr)

    if args.out:
        with open(args.out, "w") as fh:
            json.dump(out, fh, indent=1)
        print("ecrit %s (%d entrees)" % (args.out, len(out)), file=sys.stderr)
    return out


if __name__ == "__main__":
    main()
