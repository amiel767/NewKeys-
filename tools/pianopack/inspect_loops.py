"""Report on the sustain loops stored in the piano pack.

The engine never uses loopStart/loopEnd, so every note is a one-shot that stops
dead at the end of the region. Before wiring the loop in, check the stored points
are usable: a loop whose ends do not match produces a click on every wrap, which
is worse than the cut.

Seam quality is the step across the wrap divided by the largest step the signal
takes internally. A ratio near 1 means the wrap is indistinguishable from the
waveform's own motion; a large ratio means a click.
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
INDEX = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]

BIN = "app/src/main/assets/pianopack/piano.bin"
JSON = "app/src/main/assets/pianopack/piano.json"


def decode(blob, offset, frames):
    """ADPCM -> (frames, 2) float array. One byte per stereo frame, L in the low nibble."""
    out = np.zeros((frames, 2), dtype=np.float64)
    pred = [0, 0]
    idx = [0, 0]
    for i in range(frames):
        b = blob[offset + i]
        for ch in range(2):
            code = (b & 0x0F) if ch == 0 else ((b >> 4) & 0x0F)
            st = STEP[idx[ch]]
            d = st >> 3
            if code & 4:
                d += st
            if code & 2:
                d += st >> 1
            if code & 1:
                d += st >> 2
            pred[ch] += -d if (code & 8) else d
            pred[ch] = max(-32768, min(32767, pred[ch]))
            idx[ch] = max(0, min(88, idx[ch] + INDEX[code & 7]))
            out[i, ch] = pred[ch]
    return out


def main():
    blob = open(BIN, "rb").read()
    meta = json.load(open(JSON))
    assert blob[:4] == b"PIAP", "pack sans en-tete PIAP"
    rate = int.from_bytes(blob[8:12], "little")
    regions = meta["regions"]
    payload = 28 + int.from_bytes(blob[20:24], "little")

    ratios = []
    print("fichier  %5s %6s %6s %7s %8s" % ("rate", "frames", "boucle", "seam", "sustain"))
    for m in regions:
        if m["loop_end"] <= 0:
            continue
        frames = m["n"]
        pcm = decode(blob[payload + m["offset"]:], 0, frames)
        ls, le = m["loop_start"], m["loop_end"]
        if not (0 <= ls < le <= frames):
            print("%-22s boucle hors bornes %d..%d / %d" % (m["sample"], ls, le, frames))
            continue
        mono = pcm[:, 0]
        # Meme metrique que loop_region : saut du raccord rapporte au niveau local.
        # Juger contre le plus grand pas interne surestime le defaut d'un facteur
        # ~4 (l'ADPCM ajoute du bruit qui gonfle les grands pas) ; le RMS local est
        # ce qui correspond au clic entendu.
        seam = abs(mono[ls] - mono[le - 1])
        rms = float(np.sqrt(np.mean(mono[ls:le] ** 2))) + 1e-9
        ratio = seam / rms
        ratios.append(ratio)
        if len(ratios) <= 8:
            print("%-22s %5d %6d %6d %7.2f %7.2fs"
                  % (m["sample"], rate, frames, le - ls, ratio, (le - ls) / rate))

    if ratios:
        r = np.array(ratios)
        print("\n%d boucles | seam min %.2f  median %.2f  max %.2f | >2 : %d | >5 : %d"
              % (len(r), r.min(), np.median(r), r.max(),
                 int((r > 2).sum()), int((r > 5).sum())))
        print("sustain median %.2f s"
              % np.median([(m["loop_end"] - m["loop_start"]) / rate
                           for m in regions if m["loop_end"] > 0]))
    print("%d regions sans boucle sur %d" % (sum(1 for m in regions if m["loop_end"] <= 0), len(regions)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
