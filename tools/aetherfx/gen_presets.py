#!/usr/bin/env python3
"""Export AePianoUi.kt presets to a text table for the mastering bench.

The bench and the app must read the SAME numbers.  A hand-copied table drifted
once already (LEVEL sat at index 11 instead of MODEL), so this extracts from the
Kotlin source rather than duplicating it.

    python3 tools/aetherfx/gen_presets.py > /tmp/presets.txt
"""
from __future__ import annotations

import re
import sys

SRC = "app/src/main/java/com/soundstage/mixer/instruments/AePianoUi.kt"
ROW = re.compile(r'AePianoModel\("(\w+)",\s*"([^"]*)".*?floatArrayOf\(([^)]*)\),\s*(\d+)\)')


def main() -> None:
    text = open(SRC, encoding="utf-8").read()
    rows = ROW.findall(text)
    if not rows:
        sys.exit("no AePianoModel rows found -- did the constructor change?")
    for _id, name, vals, idx in rows:
        v = [float(x.strip().rstrip("f")) for x in vals.split(",") if x.strip()]
        # Mirror of AePianoModel.values (20 parameters): 0..14 = d, then the
        # derived mechanic noises and the 3-band EQ, then MODEL.
        out = [0.0] * 20
        for j in range(15):
            out[j] = v[j]
        out[10] = 0.20 + 0.30 * v[0]   # DAMPER
        out[12] = 0.15 + 0.40 * v[0]   # KEY
        out[13] = 0.0                  # free
        out[15] = int(idx) / 8.0       # MODEL
        out[16] = 0.25 + 0.60 * v[0]   # HAMMER N
        out[17] = v[15]                # EQ MID
        out[18] = v[16]                # EQ FREQ
        out[19] = v[17]                # EQ HIGH
        print(f"{name}|" + ",".join(f"{x:.4f}" for x in out))


if __name__ == "__main__":
    main()
