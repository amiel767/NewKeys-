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
        v = [x.strip() for x in vals.split(",") if x.strip()]
        # The Kotlin constructor appends the model index as the 16th value.
        out = ",".join(v + [f"{int(idx) / 8:.4f}"])
        print(f"{name}|{out}")


if __name__ == "__main__":
    main()
