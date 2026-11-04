"""Inspecte les boucles des banques sax livrees (SAXP).

Meme question que pour le piano : la boucle decroit-elle (-> « pompage ») et le
raccord saute-t-il ?  Le lecteur sax a un crossfade, donc un saut brut est deja
amorti, mais une boucle qui decroit reste un faux sustain.

Usage : python3 tools/saxpack/inspect_loops.py [app/src/main/assets/saxpack]
"""
import glob
import os
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


def decode_mono(blob, offset, frames):
    out = np.zeros(frames, dtype=np.float64)
    pred = 0; index = 0; f = 0
    nbytes = (frames + 1) // 2
    for b in range(nbytes):
        byte = blob[offset + b]
        for half in (0, 1):
            if f >= frames:
                break
            code = ((byte >> 4) & 0xF) if half else (byte & 0xF)
            st = STEP[index]
            d = st >> 3
            if code & 4: d += st
            if code & 2: d += st >> 1
            if code & 1: d += st >> 2
            pred += -d if (code & 8) else d
            pred = max(-32768, min(32767, pred))
            index = max(0, min(88, index + IDX[code & 7]))
            out[f] = pred; f += 1
    return out


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/saxpack"
    for path in sorted(glob.glob(os.path.join(base, "*.bin"))):
        blob = open(path, "rb").read()
        if blob[:4] != b"SAXP":
            print("%s : pas SAXP" % path); continue
        rate = struct.unpack_from("<I", blob, 8)[0]
        count = struct.unpack_from("<I", blob, 12)[0]
        recLen = struct.unpack_from("<I", blob, 20)[0]
        payload = 28 + recLen
        w = int(0.020 * rate)
        rows = []
        for i in range(count):
            r = blob[28 + i * 24: 28 + (i + 1) * 24]
            root, layer = r[0], r[1]
            off, frames, ls, le = struct.unpack_from("<IIII", r, 4)
            if frames < 64 or le <= ls:
                continue
            pcm = decode_mono(blob, payload + off, frames)
            seg = pcm[ls:le]
            if len(seg) < 2 * w:
                continue
            drop = np.sqrt((seg[-w:] ** 2).mean() + 1e-12) / (np.sqrt((seg[:w] ** 2).mean() + 1e-12))
            seam = abs(pcm[ls] - pcm[le - 1]) / (np.sqrt((seg ** 2).mean()) + 1e-12)
            rows.append((root, layer, (le - ls) / rate, drop, seam))
        if not rows:
            print("%s : aucune boucle" % os.path.basename(path)); continue
        d = np.array([r[3] for r in rows]); s = np.array([r[4] for r in rows])
        print("%-16s %d boucles | chute median %.2f (min %.2f, <0.8 : %d) | seam median %.2f max %.2f"
              % (os.path.basename(path), len(rows), np.median(d), d.min(),
                 int((d < 0.8).sum()), np.median(s), s.max()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
