"""Mesure la decroissance A L'INTERIEUR du segment de boucle du pack piano.

Le moteur reboucle sans fondu : si le segment decroit, chaque tour repart plus
fort que la fin du precedent -> une enveloppe qui remonte periodiquement, entendue
comme un « faux sustain » / un echo.  On decode l'ADPCM et on mesure, par region :

  * le niveau RMS au debut et a la fin de la boucle (decroissance sur un tour) ;
  * le saut du raccord (x[le-1] -> x[ls]) rapporte au RMS local ;
  * l'ecart entre le dernier bloc de la boucle et le premier bloc du tour suivant.
"""
import json
import sys

import numpy as np

STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
        45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190,
        209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724,
        796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
        2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132,
        7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
        22385, 24623, 27086, 29794, 32767]
INDEX = [-1, -1, -1, -1, 2, 4, 6, 8]


def decode(blob, offset, frames):
    out = np.zeros((frames, 2), dtype=np.float64)
    pred = [0, 0]; idx = [0, 0]
    for i in range(frames):
        b = blob[offset + i]
        for ch in range(2):
            code = (b & 0x0F) if ch == 0 else ((b >> 4) & 0x0F)
            st = STEP[idx[ch]]
            d = st >> 3
            if code & 4: d += st
            if code & 2: d += st >> 1
            if code & 1: d += st >> 2
            pred[ch] += -d if (code & 8) else d
            pred[ch] = max(-32768, min(32767, pred[ch]))
            idx[ch] = max(0, min(88, idx[ch] + INDEX[code & 7]))
            out[i, ch] = pred[ch]
    return out


def rms(x):
    return float(np.sqrt(np.mean(x ** 2))) + 1e-9


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/pianopack/piano"
    meta = json.load(open(base + ".json"))
    blob = open(base + ".bin", "rb").read()
    payload = 28 + int.from_bytes(blob[20:24], "little")
    rate = meta["rate"]
    win = int(0.020 * rate)

    rows = []
    for m in meta["regions"]:
        ls, le = m.get("loop_start", 0), m.get("loop_end", 0)
        if le <= ls or le <= 0:
            continue
        pcm = decode(blob[payload + m["offset"]:], 0, m["frames"])[:, 0]
        seg = pcm[ls:le]
        if len(seg) < 4 * win:
            continue
        head = rms(seg[:win]); tail = rms(seg[-win:])
        decay_db = 20 * np.log10(tail / head)
        seam = abs(pcm[ls] - pcm[le - 1]) / rms(seg)
        # Niveau du premier bloc APRES le raccord vs dernier bloc AVANT : c'est ce
        # saut de niveau que l'oreille entend comme une re-attaque.
        nxt = pcm[ls:ls + win] if le + win <= len(pcm) else seg[:win]
        jump_db = 20 * np.log10(rms(nxt) / tail)
        rows.append((m["root"], m["layer"], (le - ls) / rate, decay_db, seam, jump_db))

    print("regions avec boucle : %d / %d" % (len(rows), len(meta["regions"])))
    print("root lay  len_s  decay_dB  seam  jump_dB   (decay>1dB = pompe)")
    rows.sort(key=lambda r: -abs(r[3]))
    for r in rows[:12]:
        flag = "  <-- DECROIT" if r[3] < -1.0 else ""
        print("%4d %3d %6.2f %+8.2f %6.2f %+7.2f%s" % (r[0], r[1], r[2], r[3], r[4], r[5], flag))
    d = np.array([r[3] for r in rows])
    print("\ndecay: min %.2f  median %.2f  max %.2f dB | regions < -1 dB : %d/%d"
          % (d.min(), np.median(d), d.max(), int((d < -1).sum()), len(d)))
    s = np.array([r[4] for r in rows])
    print("seam : median %.2f  max %.2f | >2 : %d" % (np.median(s), s.max(), int((s > 2).sum())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
