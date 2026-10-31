#!/usr/bin/env python3
"""Verifie un pack construit : decodage, SNR, integrite, poids par couche.

Un pack ne se juge pas sur son poids mais sur ce qu'il restitue. On decode
l'ADPCM et on compare au PCM source (avant encodage) : c'est le seul controle
qui detecte une table d'adaptation fausse ou un decoupage debloque.
"""
import json
import os
import sys

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build import RATE, adpcm_decode, build_region  # noqa: E402
from measure import sample_path  # noqa: E402


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "/tmp/pack30"
    meta = json.load(open(base + ".json"))
    blob = open(base + ".bin", "rb").read()
    regions = meta["regions"]
    print("pack %s : %d regions, %.2f Mo, %d Hz"
          % (os.path.basename(base), len(regions), len(blob) / 1048576, meta["rate"]))

    snrs, ends = [], []
    step = max(len(regions) // 12, 1)
    for r in regions[::step]:
        n = r["n"]
        packed = blob[r["offset"]:r["offset"] + r["bytes"]]
        dec = adpcm_decode(packed, n)
        # reference : la meme region rejouee (deterministe)
        pcm, _ = build_region(r["sample"])
        ref = pcm.reshape(-1)[:n]
        err = ref.astype(np.float64) - dec.astype(np.float64)
        snr = 10 * np.log10((ref.astype(np.float64) ** 2).sum() / (err ** 2).sum() + 1e-30)
        snrs.append(snr)
        ends.append(abs(int(dec[-1]) - int(dec[0])))

    snrs = np.array(snrs)
    print("SNR ADPCM : min %.1f dB, median %.1f dB" % (snrs.min(), np.median(snrs)))
    print("saut de fin de region : max %d (sur 32768)" % max(ends))

    # poids par couche
    tot = {}
    for r in regions:
        L = r["sample"].split(" ", 1)[0]
        tot[L] = tot.get(L, 0) + r["bytes"]
    print("poids : " + "  ".join("%s %.2f Mo" % (k, v / 1048576)
                                for k, v in sorted(tot.items())))

    # notes couvertes et espacement
    midis = sorted({r["sample"].split(" ", 1)[1].rsplit(".", 1)[0] for r in regions})
    print("notes couvertes : %d (%s .. %s)" % (len(midis), midis[0], midis[-1]))


if __name__ == "__main__":
    main()
