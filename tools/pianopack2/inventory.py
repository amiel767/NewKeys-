#!/usr/bin/env python3
"""Inventory an SFZ bank: roots, velocity layers, note spans, generator opcodes.

Used to decide density (how many roots and layers a pack should carry) and to
read the bank's OWN effect settings (amp_veltrack, ampeg, tune, filter) instead
of inventing them.

    python3 tools/pianopack2/inventory.py /workspace/nord/sal.sfz
"""
from __future__ import annotations

import argparse
import re
from collections import Counter, defaultdict

KV = re.compile(r'(\w+)=("[^"]*"|\S+)')


def parse(path: str) -> tuple[list[dict], dict]:
    group: dict = {}
    regions: list[dict] = []
    for raw in open(path, encoding="utf-8", errors="replace"):
        line = re.sub(r"//.*", "", raw).strip()
        if not line:
            continue
        if line.startswith(("<group>", "<global>", "<control>")):
            for k, v in KV.findall(line):
                group[k] = v.strip('"')
            continue
        if line.startswith("<region>"):
            r = dict(group)
            for k, v in KV.findall(line):
                r[k] = v.strip('"')
            regions.append(r)
    return regions, group


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("sfz")
    a = ap.parse_args()
    regs, group = parse(a.sfz)

    print(f"regions: {len(regs)}   group: {group}")
    opcodes = Counter(k for r in regs for k in r)
    print("opcodes:", dict(opcodes.most_common()))

    roots = sorted({int(r["pitch_keycenter"]) for r in regs if "pitch_keycenter" in r})
    print(f"roots: {len(roots)}  {roots}")

    per_root = defaultdict(list)
    for r in regs:
        if "pitch_keycenter" in r:
            per_root[int(r["pitch_keycenter"])].append(r)
    layers = Counter(len(v) for v in per_root.values())
    print("layers per root:", dict(layers))

    gaps = [b - a for a, b in zip(roots, roots[1:])]
    print("root gaps:", Counter(gaps))

    spans = Counter((r.get("lokey"), r.get("hikey")) for r in regs)
    print("note spans:", dict(list(spans.items())[:6]))

    if regs:
        r0 = regs[0]
        print("region[0]:", {k: r0[k] for k in list(r0)[:10]})


if __name__ == "__main__":
    main()
