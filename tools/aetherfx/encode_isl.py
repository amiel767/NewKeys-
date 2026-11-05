"""Encode la demo « Isn't She Lovely » (.f32 -> MP3 legers).

    python3 tools/aetherfx/encode_isl.py <src_dir_f32> <dst_dir_mp3>

Meme regle que encode_demos.py : normalisation RMS commune (une comparaison de
timbres n'est juste qu'a sonie egale), silence de tete retire, et debit
volontairement bas (64 kbps) car ce sont des demos d'ecoute, pas des masters.
"""
import glob
import os
import sys

import lameenc
import numpy as np

SR = 44100
TARGET = 0.16
KBPS = 64
NAMES = ["Felt", "Studio", "Bright", "Mellow", "Dark", "Cottage",
         "Parlor", "Saloon", "Tine_Keys", "Ballad", "DX_Bell", "E_Piano"]


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(dst, exist_ok=True)
    total = 0
    for f in sorted(glob.glob(os.path.join(src, "isl_*.f32"))):
        x = np.fromfile(f, dtype="<f4").reshape(-1, 2)
        live = np.abs(x).max(axis=1) > 1e-4
        a = int(live.argmax()) if live.any() else 0
        x = x[a:]
        rms = float(np.sqrt((x.astype(np.float64) ** 2).mean())) or 1e-9
        x = np.clip(x * (TARGET / rms), -1.0, 1.0)
        pcm = (x * 32767).astype("<i2")
        enc = lameenc.Encoder()
        enc.set_bit_rate(KBPS)
        enc.set_in_sample_rate(SR)
        enc.set_channels(2)
        enc.set_quality(3)
        data = enc.encode(pcm.tobytes()) + enc.flush()
        i = int(os.path.basename(f).split("_")[1])
        name = NAMES[i] if i < len(NAMES) else f"model_{i}"
        path = os.path.join(dst, f"isl_{i:02d}_{name}.mp3")
        with open(path, "wb") as out:
            out.write(data)
        total += len(data)
        print(f"  {name:12s} {len(data) // 1024:4d} Ko  {x.shape[0] / SR:4.1f} s")
    print(f"TOTAL {total // 1024} Ko -> {dst}")


if __name__ == "__main__":
    main()
