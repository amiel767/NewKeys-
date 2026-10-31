#!/usr/bin/env python3
"""How different are adjacent velocity layers in the bank?

This decides the split between "more layers" and "higher sample rate" for a
fixed size budget.  If adjacent layers of the 16 are nearly identical, spending
the bytes on layers is waste; if they differ a lot, layers win.

Reports, per root, the spectral distance between layer i and layer i+1, and the
distance between layer i and the layer the pack would pick if it kept only 8.

    python3 tools/pianopack2/layers.py --zip /workspace/nord/samples.zip
"""
from __future__ import annotations

import argparse
import io
import re
import zipfile

import numpy as np

KV = re.compile(r'(\w+)=("[^"]*"|\S+)')


def parse(path: str) -> dict[int, list[dict]]:
    group, regions = {}, []
    for raw in open(path, encoding="utf-8", errors="replace"):
        line = re.sub(r"//.*", "", raw).strip()
        if not line:
            continue
        if line.startswith(("<group>", "<global>", "<control>")):
            group.update({k: v.strip('"') for k, v in KV.findall(line)})
        elif line.startswith("<region>"):
            r = dict(group)
            r.update({k: v.strip('"') for k, v in KV.findall(line)})
            regions.append(r)
    out: dict[int, list[dict]] = {}
    for r in regions:
        if r.get("trigger") == "release" or "pitch_keycenter" not in r or "lovel" not in r:
            continue
        out.setdefault(int(r["pitch_keycenter"]), []).append(r)
    for v in out.values():
        v.sort(key=lambda r: int(r["lovel"]))
    return out


def spec(x: np.ndarray, sr: int) -> np.ndarray:
    """Log-magnitude spectrum on a fixed sustain window, normalised."""
    m = x.mean(axis=1)
    m = m[int(0.3 * sr):int(1.8 * sr)]
    n = 1 << 15
    S = np.abs(np.fft.rfft(m * np.hanning(len(m)), n))
    S = S / (np.linalg.norm(S) + 1e-30)
    return 20 * np.log10(S + 1e-9)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", default="/workspace/nord/samples.zip")
    ap.add_argument("--sfz", default="/workspace/nord/sal.sfz")
    ap.add_argument("--roots", default="45,57,63,72,84,96")
    a = ap.parse_args()

    import soundfile as sf
    z = zipfile.ZipFile(a.zip)
    by_root = parse(a.sfz)

    print("mean |spectral difference| in dB between adjacent source layers")
    print("  root   n  adj(16)  keep8 vs full   verdict")
    for root in [int(v) for v in a.roots.split(",")]:
        cands = by_root.get(root)
        if not cands:
            continue
        specs = []
        for r in cands:
            try:
                with z.open(r["sample"]) as fh:
                    x, sr = sf.read(io.BytesIO(fh.read()), always_2d=True)
            except KeyError:
                specs.append(None)
                continue
            specs.append(spec(x, sr))
        ok = [(i, s) for i, s in enumerate(specs) if s is not None]
        adj = [float(np.mean(np.abs(specs[i] - specs[i + 1])))
               for i, _ in ok[:-1] if specs[i + 1] is not None]
        # what an 8-layer pack keeps: nearest source layer to each of 8 targets
        picks = [round(i * (len(cands) - 1) / 7) for i in range(8)]
        err = []
        for j in range(len(cands)):
            near = min(picks, key=lambda k: abs(k - j))
            if specs[j] is not None and specs[near] is not None:
                err.append(float(np.mean(np.abs(specs[j] - specs[near]))))
        print(f"  {root:<5} {len(ok):<3} {np.mean(adj):7.1f}  {np.mean(err):13.1f}   "
              f"{'8 suffit' if np.mean(err) < np.mean(adj) else '16 utile'}")


if __name__ == "__main__":
    main()
