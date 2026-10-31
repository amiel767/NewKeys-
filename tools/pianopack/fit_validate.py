#!/usr/bin/env python3
"""Valide l'estimateur d'inharmonicite sur du signal synthetique, puis compare
deux methodes sur les vraies prises.

Probleme de la methode 1 (recherche sur grille) : le score somme les magnitudes
aux positions predites.  Quand les partiels sont serres (graves) ou que le
fondamental est faible, plusieurs B donnent un score voisin et l'optimiseur
choisit un mauvais minimum -- d'ou B qui augmente vers les aigus, contre toute
physique.

Methode 2 : les partiels sont localises un par un, puis on ajuste
    f_n^2 = n^2*f0^2 + B*f0^2*n^4
qui est LINEAIRE en (f0^2, B*f0^2).  Moindres carres, donc pas de minimum
local.  C'est la methode des accordeurs (Entropy Piano Tuner, etc.).
"""
import os
import sys

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure import fit_f0_B, fit_log_score, load_mono, f0_nominal  # noqa: E402


def synth(f0, B, sr=44100, dur=3.0, n_max=20, seed=0):
    """Note de piano synthetique : partiels inharmoniques, amplitudes decroissantes."""
    rng = np.random.default_rng(seed)
    t = np.arange(int(dur * sr)) / sr
    y = np.zeros_like(t)
    for n in range(1, n_max + 1):
        fn = n * f0 * np.sqrt(1.0 + B * n * n)
        if fn > sr * 0.45:
            break
        amp = 1.0 / (n ** 1.1)
        decay = np.exp(-t * (1.5 + 0.35 * n))
        y += amp * decay * np.sin(2 * np.pi * fn * t + rng.uniform(0, 2 * np.pi))
    y += rng.normal(0, 1e-5, len(t))
    return y / np.abs(y).max() * 0.8


def pick_peaks(m, sr, f0_guess, B_guess=1e-4, n_max=18, iters=4, win_pct=1.2):
    """Localise chaque partiel autour de sa position predite, puis raffine.

    La fenetre est etroite (1,2 %) et suit la prediction : une fenetre large
    laisserait le partiel voisin gagner.  Interpolation parabolique pour une
    precision sous-la-case, indispensable pour B.
    """
    a = int(0.2 * sr)
    seg = m[a:] if len(m) - a > 16384 else m
    W = 1 << 17
    if len(seg) > W:
        # moyenne de plusieurs tranches : le spectre est plus stable
        n_ch = 3
        step = (len(seg) - W) // max(n_ch - 1, 1)
        S = np.zeros(W // 2 + 1)
        for c in range(n_ch):
            S += np.abs(np.fft.rfft(seg[c * step:c * step + W] * np.hanning(W)))
        S /= n_ch
    else:
        W = 1 << int(np.ceil(np.log2(len(seg))))
        S = np.abs(np.fft.rfft(seg * np.hanning(len(seg)), n=W))
    df = sr / W

    f0, B = f0_guess, B_guess
    peaks = []
    for _ in range(iters):
        peaks = []
        for n in range(1, n_max + 1):
            fn = n * f0 * np.sqrt(1.0 + B * n * n)
            if fn > sr * 0.45:
                break
            half = max(int(fn * win_pct / 100.0 / df), 3)
            k = int(round(fn / df))
            lo, hi = max(1, k - half), min(len(S) - 2, k + half)
            if hi <= lo:
                continue
            kp = lo + int(np.argmax(S[lo:hi]))
            y0, y1, y2 = S[kp - 1], S[kp], S[kp + 1]
            den = y0 - 2 * y1 + y2
            d = 0.5 * (y0 - y2) / den if abs(den) > 1e-12 else 0.0
            d = float(np.clip(d, -1.0, 1.0))
            peaks.append((n, (kp + d) * df, y1))
        if len(peaks) < 4:
            break
        f0, B = lsq(peaks)
    return peaks, f0, B


def lsq(peaks):
    """Moindres carres sur f_n^2 = n^2*f0^2 + B*f0^2*n^4  (lineaire)."""
    ns = np.array([p[0] for p in peaks], float)
    fs = np.array([p[1] for p in peaks], float)
    y = fs ** 2
    A = np.column_stack([ns ** 2, ns ** 4])
    sol, *_ = np.linalg.lstsq(A, y, rcond=None)
    a, b = sol
    if a <= 0:
        return float(np.sqrt(max(y[0], 1e-9))), 0.0
    return float(np.sqrt(a)), float(max(b / a, 0.0))


def weighted_lsq(peaks):
    """Pondere par la magnitude : un partiel fort donne une position fiable."""
    ns = np.array([p[0] for p in peaks], float)
    fs = np.array([p[1] for p in peaks], float)
    ws = np.array([p[2] for p in peaks], float)
    ws = ws / (ws.max() + 1e-12)
    y = fs ** 2
    A = np.column_stack([ns ** 2, ns ** 4])
    sw = np.sqrt(ws)
    sol, *_ = np.linalg.lstsq(A * sw[:, None], y * sw, rcond=None)
    a, b = sol
    if a <= 0:
        return float(np.sqrt(max(y[0], 1e-9))), 0.0
    return float(np.sqrt(a)), float(max(b / a, 0.0))


def avg_spectrum(m, sr, n_ch=3, W=1 << 17):
    """Spectre moyenne sur plusieurs tranches : les partiels ressortent du bruit."""
    a = int(0.2 * sr)
    seg = m[a:] if len(m) - a > 4 * W else m
    if len(seg) < W:
        W = 1 << int(np.ceil(np.log2(len(seg))))
        return np.abs(np.fft.rfft(seg * np.hanning(len(seg)), n=W)), sr / W
    step = (len(seg) - W) // max(n_ch - 1, 1)
    S = np.zeros(W // 2 + 1)
    for c in range(n_ch):
        S += np.abs(np.fft.rfft(seg[c * step:c * step + W] * np.hanning(W)))
    return S / n_ch, sr / W


def track_partials(m, sr, f0_guess, B_guess=1e-4, n_max=20, iters=6,
                   win_pct=0.35, snr_min=8.0):
    """Suit les partiels de proche en proche, fenetre etroite, seuil de bruit.

    Trois protections, chacune repondant a un echec observe sur les vraies prises :

    1. fenetre ETROITE (0,35 %).  Une fenetre large (1,2 %) laisse le partiel
       voisin gagner des que la prediction derive d'un demi-ton.
    2. seuil de bruit : un partiel sous snr_min x le bruit local est rejete.
       Sans ce seuil, l'optimiseur aligne des pics de bruit -- c'est ce qui
       faisait exploser B a 45 % d'etirement dans les aigus.
    3. suivi du grave vers l'aigu : le partiel n est predit depuis l'ajustement
       courant, lui-meme nourri par les partiels 1..n-1 deja confirmes.
    """
    S, df = avg_spectrum(m, sr)
    # Bruit local : mediane sur une fenetre de 101 cases.  Une "fraction du
    # spectre" ne veut rien dire ici (les cases sont lineaires en frequence) et
    # une fenetre proportionnelle rend la boucle quadratique.
    win = 101
    pad = np.pad(S, win // 2, mode="edge")
    noise = np.array([np.median(pad[i:i + win]) for i in range(len(S))])

    f0, B = f0_guess, B_guess
    peaks = []
    for _ in range(iters):
        peaks = []
        for n in range(1, n_max + 1):
            fn = n * f0 * np.sqrt(1.0 + B * n * n)
            if fn > sr * 0.45:
                break
            half = max(int(fn * win_pct / 100.0 / df), 3)
            k = int(round(fn / df))
            lo, hi = max(1, k - half), min(len(S) - 2, k + half)
            if hi <= lo:
                continue
            kp = lo + int(np.argmax(S[lo:hi]))
            y0, y1, y2 = S[kp - 1], S[kp], S[kp + 1]
            den = y0 - 2 * y1 + y2
            d = 0.5 * (y0 - y2) / den if abs(den) > 1e-12 else 0.0
            d = float(np.clip(d, -1.0, 1.0))
            if y1 < snr_min * noise[kp]:      # partiel noye dans le bruit
                continue
            peaks.append((n, (kp + d) * df, y1))
        if len(peaks) < 4:
            break
        f0, B = weighted_lsq(peaks)
    return peaks, f0, B




def validate():
    print("=" * 86)
    print("VALIDATION sur signal synthetique (B connu)")
    print("=" * 86)
    print(f"{'cas':20s} {'B vrai':>10s} {'M1 grille':>11s} {'M3 suivi':>11s} "
          f"{'M4 log':>11s} {'err M4':>8s}")
    cases = []
    for note, f0 in [("A0", 27.5), ("C2", 65.4), ("C3", 130.8), ("C4", 261.6),
                     ("C5", 523.3), ("C6", 1046.5)]:
        for B in [1e-5, 1e-4, 5e-4, 2e-3]:
            cases.append((f"{note} B={B:.0e}", f0, B))
    ok1 = ok3 = ok4 = 0
    for name, f0, B in cases:
        y = synth(f0, B)
        _, B1 = fit_f0_B(y, 44100, f0)
        _, _, B3 = track_partials(y, 44100, f0, B_guess=B * 0.5)
        _, B4 = fit_log_score(y, 44100, f0)
        e1 = abs(B1 - B) / B * 100
        e3 = abs(B3 - B) / B * 100
        e4 = abs(B4 - B) / B * 100
        ok1 += e1 < 20
        ok3 += e3 < 20
        ok4 += e4 < 20
        flag = "" if e4 < 20 else "   <-- M4 rate"
        print(f"{name:20s} {B:10.2e} {B1:11.2e} {B3:11.2e} {B4:11.2e} "
              f"{e4:7.1f}%{flag}")
    print()
    n = len(cases)
    print(f"methode 1 (grille)   : {ok1}/{n}")
    print(f"methode 3 (suivi)    : {ok3}/{n}")
    print(f"methode 4 (log)      : {ok4}/{n}")
    return ok1, ok3, ok4


def on_real():
    print()
    print("=" * 74)
    print("APPLICATION aux vraies prises (methode 4 : score log)")
    print("=" * 74)
    print(f"{'note':6s} {'midi':>4s} {'f0':>9s} {'cents':>7s} {'B':>11s} "
          f"{'n10 %':>7s}")
    from measure import midi_of
    rows = []
    for note in ["A0", "C1", "C2", "C3", "C4", "C5", "C6", "C7"]:
        name = f"PP {note}.flac"
        try:
            m, sr = load_mono(name)
        except Exception:
            continue
        f0n = f0_nominal(note)
        f0, B = fit_log_score(m, sr, f0n)
        cents = 1200 * np.log2(f0 / f0n)
        n10 = 100 * (np.sqrt(1 + B * 100) - 1)
        rows.append((midi_of(note), f0, B))
        print(f"{note:6s} {midi_of(note):4d} {f0:8.2f} {cents:+7.2f} {B:11.3e} "
              f"{n10:7.2f}")
    if len(rows) >= 4:
        mm = np.array([r[0] for r in rows], float)
        bb = np.array([r[2] for r in rows])
        k, b0 = np.polyfit(mm - 60, np.log2(bb), 1)
        print()
        print(f"loi : B = {2**b0:.3e} * 2^({k:.4f}*(midi-60)/12)")
        print(f"  facteur par OCTAVE : {2**(k*12):.3f}")
        print(f"  physique : pour un piano, B DECROIT du grave vers le medium")
        print(f"             (cordes plus courtes et plus legeres), puis peut")
        print(f"             remonter dans le dernier octave (cordes tres courtes).")
    return rows


if __name__ == "__main__":
    validate()
    on_real()
