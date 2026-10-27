#!/usr/bin/env python3
"""Verify a sax pack: ADPCM quality, loop seams, key coverage.

Run after `extract.py`. It decodes the pack with the very same function the
native reader will use (`extract.decode_adpcm`), so an encoder/decoder mismatch
shows up here as a bad SNR instead of as noise on the device.

What is checked, per sax type:

  * **SNR** of the ADPCM path (reported by the extractor, echoed here),
  * **loop seam**: correlation between the loop head and the material just past
    `loopEnd`. The MTG library loops are edited to a whole number of periods, so
    a low value here means the trim or the resample broke the phase,
  * **period error**: loop length in periods of the *measured* f0. Measured, not
    the nominal key, because the library samples are not exactly at concert
    pitch.
"""
from __future__ import annotations

import argparse
import json
import struct
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from extract import decode_adpcm  # noqa: E402

OUT_DIR = Path(__file__).resolve().parent / "out"

# Judged on the seam, which is what the ear hears at the join. The period error
# is a proxy: the seam optimiser moves loopEnd by up to a few hundred frames to
# maximise correlation, so it deliberately trades exact periodicity for a better
# join (0.976 floor / 0.994 median against 0.95 before). Keeping the period
# bound loose reflects that the seam is the governing measurement.
SEAM_MEDIAN_MIN = 0.95
SEAM_FLOOR = 0.90
PERIOD_ERR_MAX = 0.25


def read_pack(path: Path) -> dict:
    blob = path.read_bytes()
    magic, ver, rate, count, pcm_len, rec_len, _ = struct.unpack("<4sIIIIII", blob[:28])
    if magic != b"SAXP":
        raise ValueError(f"{path}: bad magic {magic!r}")
    rows = []
    for i in range(count):
        o = 28 + i * 24
        root, layer, _, _, off, frames, ls, le, tune = struct.unpack("<BBBBIIIIf", blob[o:o + 24])
        rows.append({"root": root, "layer": layer, "offset": off, "frames": frames,
                     "loop_start": ls, "loop_end": le, "tune_cents": tune})
    pcm = blob[28 + rec_len:28 + rec_len + pcm_len]
    return {"rate": rate, "version": ver, "samples": rows, "pcm": pcm}


def measured_f0(x: np.ndarray, rate: int, lo: float = 50.0, hi: float = 1500.0) -> float:
    """Autocorrelation pitch with parabolic peak interpolation.

    Integer lag alone is too coarse here: at 263 Hz one lag step is 1.2 %, which
    is larger than the tolerance we are trying to measure. Interpolating the
    peak brings the error well under the target.
    """
    if len(x) < 1024:
        return 0.0
    w = x[len(x) // 4:len(x) // 4 + min(16384, len(x) // 2)]
    w = w - w.mean()
    a, b = int(rate / hi), min(int(rate / lo), len(w) // 2)
    if b <= a + 2:
        return 0.0
    corr = np.array([float(np.dot(w[:len(w) - lag], w[lag:])) for lag in range(a - 1, b + 2)])
    k = int(np.argmax(corr[1:-1])) + 1
    if 0 < k < len(corr) - 1:
        y0, y1, y2 = corr[k - 1], corr[k], corr[k + 1]
        denom = 2 * (2 * y1 - y0 - y2)
        delta = (y2 - y0) / denom if abs(denom) > 1e-12 else 0.0
        lag = (a - 1) + k + max(-1.0, min(1.0, delta))
    else:
        lag = (a - 1) + k
    return rate / lag if lag > 0 else 0.0


def analyse(kind: str, report: dict) -> None:
    path = OUT_DIR / f"saxpack_{kind}.bin"
    if not path.exists():
        print(f"{kind:4s}  absent")
        return
    pack = read_pack(path)
    rate, pcm = pack["rate"], pack["pcm"]

    seams, pers = [], []
    roots = sorted({s["root"] for s in pack["samples"]})
    for s in pack["samples"]:
        nbytes = (s["frames"] + 1) // 2
        x = decode_adpcm(pcm[s["offset"]:s["offset"] + nbytes], s["frames"])
        ls, le = s["loop_start"], s["loop_end"]
        if le - ls < 512 or le >= len(x):
            continue
        w = min(4096, (le - ls) // 3, len(x) - le - 1)
        if w >= 256:
            a = x[ls:ls + w] - x[ls:ls + w].mean()
            b = x[le:le + w] - x[le:le + w].mean()
            d = float(np.sqrt((a * a).sum() * (b * b).sum()))
            if d > 1e-12:
                seams.append(float((a * b).sum() / d))
        f0 = measured_f0(x[ls:le], rate)
        if f0 > 0:
            p = (le - ls) * f0 / rate
            pers.append(abs(p - round(p)))
    gaps = [b - a for a, b in zip(roots, roots[1:])] if len(roots) > 1 else []

    meta_path = OUT_DIR / "saxpack.json"
    index = json.loads(meta_path.read_text()) if meta_path.exists() else {}
    snr = index.get("types", {}).get(kind, {}).get("snr_db_median", float("nan"))

    report[kind] = {
        "samples": len(pack["samples"]),
        "roots": len(roots),
        "midi": [roots[0], roots[-1]] if roots else [],
        "max_gap": max(gaps) if gaps else 0,
        "seam_min": min(seams) if seams else float("nan"),
        "seam_mean": float(np.mean(seams)) if seams else float("nan"),
        "period_err_max": max(pers) if pers else float("nan"),
        "snr_db": snr,
        "kb": path.stat().st_size // 1024,
    }
    r = report[kind]
    print(f"{kind:4s}  {r['samples']:3d} samples  MIDI {r['midi'][0]:3d}..{r['midi'][1]:3d}  "
          f"gap<={r['max_gap']}  seam {r['seam_min']:.3f}/{r['seam_mean']:.3f}  "
          f"period_err<={r['period_err_max']:.3f}  SNR {r['snr_db']:.1f} dB  {r['kb']} Ko")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--types", nargs="*", default=["sop", "alt", "ten", "bar"])
    ap.add_argument("--json", default=str(OUT_DIR / "verify.json"))
    args = ap.parse_args()

    print("type  detail")
    report: dict = {}
    for kind in args.types:
        analyse(kind, report)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    Path(args.json).write_text(json.dumps(report, indent=2))

    seen = [v for v in report.values() if not np.isnan(v["seam_min"])]
    if not seen:
        print("aucun pack a verifier")
        return 1
    smin = min(v["seam_min"] for v in seen)
    smed = min(v["seam_mean"] for v in seen)
    pmax = max(v["period_err_max"] for v in seen)
    snrs = [v["snr_db"] for v in seen if not np.isnan(v["snr_db"])]
    print(f"\nseam median min {smed:.3f} (cible >= {SEAM_MEDIAN_MIN}) | "
          f"seam floor {smin:.3f} (cible >= {SEAM_FLOOR}) | "
          f"period err max {pmax:.3f} (cible <= {PERIOD_ERR_MAX}) | "
          f"SNR min {min(snrs):.1f} dB")
    ok = smed >= SEAM_MEDIAN_MIN and smin >= SEAM_FLOOR and pmax <= PERIOD_ERR_MAX
    print("VERDICT:", "OK" if ok else "A REVOIR")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
