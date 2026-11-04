"""Inspecte les boucles d'un pack PIAP (piano/EP) sans manifeste JSON.

Question : la boucle decroit-elle (-> « pompage » = faux sustain) et le raccord
saute-t-il ?  Le moteur reboucle a `loopEnd` vers `loopStart` sans fondu.

  python3 tools/pianopack/inspect_piap.py app/src/main/assets/pianopack/piano.bin

Le decodeur ADPCM est celui du pack : 4 bits STEREO entrelace, un nibble par
canal DANS l'octet (pair = gauche, impair = droite).
"""
import struct
import sys

import numpy as np

STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
        45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190,
        209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724,
        796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272,
        2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132,
        7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
        22385, 24623, 27086, 29794, 32767]
IDX = [-1, -1, -1, -1, 2, 4, 6, 8]


def decode(blob, offset, frames):
    """4 bits stereo entrelace -> (frames, 2) float."""
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
            idx[ch] = max(0, min(88, idx[ch] + IDX[code & 7]))
            out[i, ch] = pred[ch]
    return out / 32768.0


def main():
    path = sys.argv[1]
    blob = open(path, "rb").read()
    assert blob[:4] == b"PIAP", "pas un pack PIAP"
    rate, count, pcm_len, rec_len = struct.unpack_from("<IIII", blob, 8)
    rows = []
    for i in range(count):
        r = blob[28 + i * 24:28 + i * 24 + 24]
        root, layer = r[0], r[1]
        off, frames, ls, le = struct.unpack_from("<IIII", r, 4)
        if frames < 64:
            continue
        x = decode(blob[28 + rec_len:], off, frames)
        m = 0.5 * (x[:, 0] + x[:, 1])
        if le <= ls or le > frames:
            rows.append((root, layer, frames, 0.0, 0.0, 0, False))
            continue
        seg = m[ls:le]
        w = max(1, len(seg) // 4)
        head = np.sqrt((seg[:w] ** 2).mean()) or 1e-9
        tail = np.sqrt((seg[-w:] ** 2).mean()) or 1e-9
        decay_db = 20 * np.log10(tail / head)
        rms = np.sqrt((seg ** 2).mean()) or 1e-9
        jump = abs(m[le - 1] - m[ls]) / rms
        rows.append((root, layer, frames / rate, decay_db, jump, 1, True))

    looped = [r for r in rows if r[6]]
    print("regions : %d / %d | avec boucle : %d" % (len(rows), count, len(looped)))
    print("root lay len_s decay_dB seam")
    for r in sorted(rows, key=lambda z: (z[1], z[0]))[:14]:
        flag = "  <-- POMPE" if r[3] < -1 else ""
        print("%4d %3d %6.2f %+8.2f %6.2f%s" % (r[0], r[1], r[2], r[3], r[4], flag))
    d = np.array([r[3] for r in looped])
    s = np.array([r[4] for r in looped])
    print("\ndecay: min %.2f  median %.2f  max %.2f dB | < -1 dB : %d/%d"
          % (d.min(), np.median(d), d.max(), int((d < -1).sum()), len(d)))
    print("seam : median %.2f  max %.2f | >2 : %d"
          % (np.median(s), s.max(), int((s > 2).sum())))


if __name__ == "__main__":
    main()
