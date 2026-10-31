#!/usr/bin/env python3
"""Verifie que chaque region du pack sonne a la hauteur de son root.

C'est le controle qui manquait.  Les bancs precedents validaient le moteur avec un
pack **synthetique** construit sur place : ils ne pouvaient pas voir que les
racines du **vrai** pack etaient decalees d'une octave.  Le bug a survecu a une
passe de correction pour cette seule raison.  On verifie donc deux choses :

  1. la convention de nommage (`midi_of`) — test pur, deterministe ;
  2. le contenu reel des regions — mesure de hauteur, avec le detecteur lui-meme
     valide au prealable sur un signal synthetique de hauteur connue.

Le detecteur cherche la correlation autour de la hauteur attendue ET autour du
double.  C'est la signature du bug cherche : les regions etaient etiquetees une
octave trop bas, donc leur contenu tombe sur 2 x la hauteur attendue.

Usage :
    python3 tools/pianopack/verify_pitch.py [chemin/piano.bin]
"""
import os
import struct
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from measure import midi_of  # noqa: E402

STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
        45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190,
        209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724,
        796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
        2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132,
        7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500,
        20350, 22385, 24623, 27086, 29794, 32767]
IDX = [-1, -1, -1, -1, 2, 4, 6, 8]

TOL = 0.03          # 3 % = un demi-ton ; un decalage d'octave vaut 100 %
MIN_STRENGTH = 0.5  # en dessous, l'autocorrelation ne permet pas de conclure
ROOT_MIN, ROOT_MAX = 21, 88


def decode(pcm, offset, frames):
    """PCM stereo entrelace d'une region (ADPCM 4 bits, IMA)."""
    out = np.zeros(frames * 2, dtype=np.int16)
    pl = il = pr = ir = 0
    first = offset * 2
    for i in range(frames):
        for ch in (0, 1):
            k = first + i * 2 + ch
            b = pcm[k >> 1]
            code = (b >> 4) if (k & 1) else (b & 0xF)
            st = STEP[il if ch == 0 else ir]
            dl = st >> 3
            if code & 4:
                dl += st
            if code & 2:
                dl += st >> 1
            if code & 1:
                dl += st >> 2
            if ch == 0:
                pl += -dl if code & 8 else dl
                pl = max(-32768, min(32767, pl))
                il = max(0, min(88, il + IDX[code & 7]))
                out[i * 2] = pl
            else:
                pr += -dl if code & 8 else dl
                pr = max(-32768, min(32767, pr))
                ir = max(0, min(88, ir + IDX[code & 7]))
                out[i * 2 + 1] = pr
    return out


def _peak_near(ac, sr, f, span=0.25):
    """Meilleure correlation dans f*(1-span) .. f*(1+span), affinee.

    La fenetre est etroite a dessein : dans ±25 %, aucune ambiguite d'octave
    n'existe (l'octave est a 100 %), donc le maximum y designe la fondamentale
    si elle est la.  Une fenetre large laisse entrer les harmoniques.
    """
    n = len(ac)
    lo = max(2, int(sr / (f * (1.0 + span))))
    hi = min(int(sr / (f * (1.0 - span))), n - 2)
    if hi <= lo:
        return 0.0, 0.0
    seg = ac[lo:hi]
    j = int(np.argmax(seg))
    peak = float(seg[j])
    i = lo + j
    if 1 < i < n - 1:
        a, b, c = ac[i - 1], ac[i], ac[i + 1]
        den = a - 2 * b + c
        if abs(den) > 1e-12:
            i = i + 0.5 * (a - c) / den
    return sr / i, peak


def measure(sig, sr, expected):
    """(f0 mesure, verdict) ou verdict vaut 'ok', 'octave', 'faux' ou 'faible'."""
    x = sig - sig.mean()
    n = min(len(x), int(sr * 1.5))
    x = x[:n].astype(np.float64)
    ac = np.correlate(x, x, "full")[n - 1:]
    ac /= ac[0] + 1e-12

    f_near, s_near = _peak_near(ac, sr, expected)
    f_dbl, s_dbl = _peak_near(ac, sr, expected * 2.0)

    if s_near >= MIN_STRENGTH:
        return f_near, "ok" if abs(f_near / expected - 1.0) < TOL else "faux"
    # Pas de periodicite a la hauteur annoncee, mais il y en a une au double :
    # la region est etiquetee une octave trop bas.  C'est le bug cherche.
    if s_dbl >= MIN_STRENGTH and s_dbl > s_near:
        return f_dbl, "octave"
    return f_near, "faible"


def lowest_peak_ratio(sig, sr, expected, floor_ratio=0.6):
    """Rapport entre la plus BASSE raie significative et la hauteur attendue.

    C'est le discriminateur juste pour ce bug : les regions etaient etiquetees une
    octave trop bas, donc leur contenu a sa plus basse raie a 2 x l'attendu.

    Mesurer la plus basse raie est plus sur que l'autocorrelation, qui ne peut pas
    separer « contenu a 2f0 » de « contenu a f0 » : un signal a 2f0 est aussi
    periodique a f0, donc les deux donnent la meme correlation.  Le spectre, lui,
    montre la plus basse raie sans ambiguite.

    La faiblesse connue — la fondamentale d'un piano peut etre sous la 2e
    harmonique — est neutralisee par la MEDIANE sur tout le clavier : quelques
    regions isolees ne deplacement pas la mediane, alors qu'un decalage global
    la place a 2,0.
    """
    x = sig - sig.mean()
    n = min(len(x), int(sr * 1.5))
    seg = x[int(0.15 * sr):int(0.15 * sr) + 16384]
    if len(seg) < 4096:
        return None
    seg = seg - seg.mean()
    S = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
    fr = np.fft.rfftfreq(len(seg), 1.0 / sr)
    if S.max() <= 0:
        return None
    S = S / S.max()
    # plus basse raie depassant 15 % du maximum, au-dessus de 25 Hz
    for i in range(3, len(fr) - 1):
        # On ignore tout ce qui est sous 0,6 x l'attendu : les sous-harmoniques
        # d'enregistrement y vivent (mesure : 0,77 median sur un pack juste) et
        # fausseraient la mediane.  Aucun risque de masquer le bug : celui-ci
        # place le contenu a 2 x l'attendu, bien au-dessus du plancher.
        if fr[i] < expected * floor_ratio:
            continue
        if S[i] >= 0.15 and S[i] > S[i - 1] and S[i] >= S[i + 1]:
            # interpolation parabolique pour la precision
            a, b, c = S[i - 1], S[i], S[i + 1]
            den = a - 2 * b + c
            f = fr[i]
            if abs(den) > 1e-12:
                f = fr[i] + 0.5 * (a - c) / den * (fr[1] - fr[0])
            return f / expected if expected else None
    return None


def self_test():
    """Valide le discriminateur sur des signaux de hauteur connue.

    Un test de mesure n'a de valeur que si la mesure elle-meme est prouvee.  On
    verifie qu'il rend ~1,0 pour un contenu a la bonne hauteur, et ~2,0 pour un
    contenu decale d'une octave — le cas du bug.
    """
    sr = 22050
    t = np.arange(int(sr * 2)) / sr
    ok = True
    for f0 in (65.41, 130.81, 261.63, 523.25):
        # fondamentale faible, 2e et 3e fortes : le cas normal sur un piano
        sig = (0.30 * np.sin(2 * np.pi * f0 * t)
               + 1.00 * np.sin(2 * np.pi * 2 * f0 * t)
               + 0.55 * np.sin(2 * np.pi * 3 * f0 * t))
        r = lowest_peak_ratio(sig, sr, f0)
        if r is None or abs(r - 1.0) > 0.03:
            print("AUTO-TEST ECHOUE a %.2f Hz : rapport %s (attendu 1,0)" % (f0, r))
            ok = False
    # contenu decale d'une octave : le rapport doit valoir 2,0
    f0 = 130.81
    sig = (0.30 * np.sin(2 * np.pi * 2 * f0 * t)
           + 1.00 * np.sin(2 * np.pi * 4 * f0 * t)
           + 0.55 * np.sin(2 * np.pi * 6 * f0 * t))
    r = lowest_peak_ratio(sig, sr, f0)
    if r is None or abs(r - 2.0) > 0.05:
        print("AUTO-TEST ECHOUE : un contenu a 2x devrait donner 2,0, donne %s" % r)
        ok = False
    if ok:
        print("auto-test du discriminateur : hauteurs justes -> 1,0 ; octave -> 2,0")
    return ok


def check_roundtrip(path, samples=("FF A#2.flac", "PP A0.flac", "Mp G3.flac")):
    """Le pack sur disque se decode-t-il en ce que l'encodeur a recu ?

    C'est le controle qui aurait attrape le pire bug du pack : l'encodeur
    utilisait UN predicteur sur le flux stereo entrelace, alors que le moteur en
    utilise UN PAR CANAL.  Le flux etait donc indecodable — SNR mesure -7 dB,
    correlation 0,01 — et le piano sonnait comme un vieil enregistrement.  Le sax
    n'a jamais eu ce defaut parce qu'il est mono : un seul canal, donc un seul
    predicteur des deux cotes.

    On compare ici la region telle qu'elle est ecrite dans le pack, decodee par
    le decodeur du moteur, avec la sortie de `build_region` — l'entree exacte de
    l'encodeur.  Un bon ADPCM 4 bits a 22 kHz donne 25-35 dB.
    """
    import json
    from build import build_region
    if not os.path.exists(path):
        print("pack absent, aller-retour non verifie")
        return True
    json_path = path[:-4] + ".json" if path.endswith(".bin") else path + ".json"
    if not os.path.exists(json_path):
        print("index absent, aller-retour non verifie")
        return True
    J = json.load(open(json_path))
    raw = open(path, "rb").read()
    rate, count, pcm_len, rec_len = struct.unpack("<IIII", raw[8:24])
    pcm = raw[28 + rec_len:28 + rec_len + pcm_len]

    snrs = []
    for name in samples:
        idx = next((k for k, r in enumerate(J["regions"]) if r["sample"] == name), None)
        if idx is None:
            continue
        try:
            ref, _ = build_region(name)
        except Exception:
            continue
        rec = raw[28 + idx * 24:28 + (idx + 1) * 24]
        off, frames = struct.unpack("<II", rec[4:12])
        dec = decode(pcm, off, frames)
        a = (ref[:, 0].astype(np.float64) + ref[:, 1]) / 2 / 32768.0
        b = (dec[0::2].astype(np.float64) + dec[1::2]) / 2 / 32768.0
        m = min(len(a), len(b))
        a, b = a[:m], b[:m]
        snr = 10 * np.log10((a ** 2).sum() / (((a - b) ** 2).sum() + 1e-12))
        snrs.append(snr)

    if not snrs:
        print("aucune region comparable, aller-retour non verifie")
        return True
    med = float(np.median(snrs))
    print("aller-retour encodeur -> decodeur du moteur : SNR median %.1f dB" % med)
    if med < 20.0:
        print("  ECHEC : le flux du pack ne se decode pas (attendu > 20 dB).")
        print("  Verifier que l'encodeur utilise un predicteur PAR CANAL.")
        return False
    return True


def check_convention():
    """La banque nomme le do central « C3 » (convention Roland).

    Mesure : le fichier « C3 » contient 262 Hz, « C2 » 130 Hz, « A0 » 55 Hz.  En
    notation scientifique ces noms valent MIDI 48 / 36 / 21 — soit 12 demi-tons
    sous leur contenu reel.  C'est la cause racine du « son de jouet ».
    """
    want = {"C3": 60, "C4": 72, "A0": 33, "A#2": 58}
    bad = [(n, m, midi_of(n)) for n, m in want.items() if midi_of(n) != m]
    if bad:
        print("CONVENTION FAUSSE :")
        for name, mid, got in bad:
            print("   %-4s devrait valoir MIDI %d, vaut %d" % (name, mid, got))
        return False
    print("convention de nommage : C3 -> MIDI 60, A0 -> MIDI 33 (Roland), correct")
    return True


def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        root_dir, "app/src/main/assets/pianopack/piano.bin")

    ok = True
    if not check_convention():
        ok = False
    if not self_test():
        ok = False
    if not check_roundtrip(path):
        ok = False

    raw = open(path, "rb").read()
    if raw[:4] != b"PIAP":
        print("magic inattendu : %r" % raw[:4])
        return 1
    rate, count, pcm_len, rec_len = struct.unpack("<IIII", raw[8:24])
    pcm = raw[28 + rec_len:28 + rec_len + pcm_len]

    print("\npack %s : %d regions, %d Hz" % (os.path.basename(path), count, rate))

    ratios, seen = [], set()
    for i in range(count):
        r = raw[28 + i * 24:28 + (i + 1) * 24]
        note = r[0]
        off, frames = struct.unpack("<II", r[4:12])
        if note in seen:
            continue
        seen.add(note)
        if frames < 256 or not (ROOT_MIN <= note <= ROOT_MAX):
            continue
        sig = decode(pcm, off, frames)
        sig = (sig[0::2] + sig[1::2]) / 2.0 / 32768.0
        exp = 440.0 * 2.0 ** ((note - 69) / 12.0)
        r_ = lowest_peak_ratio(sig, rate, exp)
        if r_ is not None:
            ratios.append((note, r_))

    if not ratios:
        print("aucune region exploitable")
        return 1

    vals = np.array([r for _, r in ratios])
    med = float(np.median(vals))
    # La mediane vaut ~1,0 si les etiquettes sont justes, ~2,0 si elles sont une
    # octave trop bas (le contenu est alors a 2 x l'attendu).  On tolere 8 % : les
    # regions isolees ou la fondamentale manque ne deplacement pas la mediane.
    good = abs(med - 1.0) <= 0.08
    near1 = int((np.abs(vals - 1.0) <= 0.10).sum())
    near2 = int((np.abs(vals - 2.0) <= 0.10).sum())
    print("%d notes mesurees ; rapport median contenu/attendu = %.3f" % (len(ratios), med))
    print("   notes a la bonne hauteur : %d | une octave trop haut : %d" % (near1, near2))
    if good:
        print("les regions sonnent a la hauteur de leur root (median ~1,0)")
    else:
        ok = False
        print("DECALAGE GLOBAL : median %.3f (1,0 = juste, 2,0 = une octave trop bas)" % med)
        off_oct = np.log2(med) if med > 0 else 0.0
        print("   soit %.2f octave de decalage" % off_oct)

    if not ok:
        return 1
    print("\nOK : convention et hauteurs verifiees")
    return 0


if __name__ == "__main__":
    sys.exit(main())
