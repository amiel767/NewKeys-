#!/usr/bin/env python3
"""Extract a compact saxophone sample pack from the MTG.SoloSax SFZ library.

MTG Solo Saxophones (https://github.com/sfzinstruments/MTG.SoloSax) is
CC-BY 4.0 by MTG / programmed by kinwie. It ships 24-bit 48 kHz mono FLAC with
per-sample sustain loops recorded in the FLAC `riff`/`smpl` application block.

This script turns that library into a single file the native engine can read
without a decoder:

  * keeps the attack plus the sustain loop of every region (nothing else),
  * resamples 48 kHz -> 22.05 kHz (a sax has almost no energy above 8 kHz),
  * encodes 4-bit IMA ADPCM (half the bytes of 16-bit PCM, trivial to decode),
  * writes one `saxpack.bin` per sax type plus a shared `saxpack.json` manifest.

The loop points survive resampling and stay whole periods, so the loop still
joins without a click. Verify with `verify.py`.

Usage
-----
    python3 tools/saxpack/extract.py                 # all four types
    python3 tools/saxpack/extract.py --types alt     # just the alto
    python3 tools/saxpack/extract.py --layers f      # forte layer only
"""
from __future__ import annotations

import argparse
import io
import json
import re
import struct
import sys
import urllib.parse
import urllib.request
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

RAW = "https://raw.githubusercontent.com/sfzinstruments/MTG.SoloSax/master/"
OUT_DIR = Path(__file__).resolve().parent / "out"
TARGET_RATE = 22050
TYPES = ("sop", "alt", "ten", "bar")

# IMA ADPCM, the table pair from the WAV specification.
STEP = [
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
    50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230,
    253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
    1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
    3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
    11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
    32767,
]
IDX = [-1, -1, -1, -1, 2, 4, 6, 8]


def fetch(path: str) -> bytes:
    url = RAW + urllib.parse.quote(path)
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read()


def flac_meta(blob: bytes) -> dict:
    """Sample rate, frame count and loop points of a FLAC stream.

    The loop lives in an APPLICATION block whose payload starts with `riff`
    followed by the usual WAV chunks. It is *not* in the SFZ (which carries no
    `loop_*` opcode), and reading `smpl` without skipping its own size field
    yields shifted bytes that look like "no loop".
    """
    out: dict = {"loops": []}
    p, last = 4, False
    while not last and p < len(blob) - 4:
        head = blob[p]
        last = bool(head & 0x80)
        btype = head & 0x7F
        size = int.from_bytes(blob[p + 1:p + 4], "big")
        if btype == 0:
            v = int.from_bytes(blob[p + 14:p + 22], "big")
            out["rate"] = (v >> 44) & 0xFFFFF
            out["frames"] = v & 0xFFFFFFFFF
        elif btype == 2:
            body = blob[p + 4:p + 4 + size]
            if body[:4] == b"riff" and b"smpl" in body:
                sm = body[body.index(b"smpl") + 8:]  # skip id + size
                n = int.from_bytes(sm[28:32], "little")
                for k in range(n):
                    o = 36 + k * 24
                    out["loops"].append((
                        int.from_bytes(sm[o + 8:o + 12], "little"),
                        int.from_bytes(sm[o + 12:o + 16], "little"),
                    ))
        p += 4 + size
    return out


def region_map(kind: str, layer: str) -> list[dict]:
    """Regions of one velocity layer: key, sample name, gain and tune."""
    text = fetch(f"MTG Solo Saxophones/Data/{kind}_{layer}_rr1.txt").decode()
    rows = []
    for line in text.splitlines():
        m = re.search(r"key=(\d+).*?sample=(\w+)\.\$EXT", line)
        if not m:
            continue
        vol = re.search(r"volume=(-?[\d.]+)", line)
        tune = re.search(r"tune=(-?[\d.]+)", line)
        rows.append({
            "key": int(m.group(1)),
            "name": m.group(2),
            "gain_db": float(vol.group(1)) if vol else 0.0,
            "tune_cents": float(tune.group(1)) if tune else 0.0,
        })
    return rows


def encode_adpcm(x: np.ndarray) -> bytes:
    """4-bit IMA ADPCM. Two nibbles per byte, low nibble first."""
    pcm = np.clip(np.rint(x * 32767.0), -32768, 32767).astype(np.int32)
    out = bytearray()
    pred, index = 0, 0
    nib = 0
    for i, sample in enumerate(pcm):
        step = STEP[index]
        diff = int(sample) - pred
        code = 0
        if diff < 0:
            code = 8
            diff = -diff
        # successive approximation on the step fractions
        if diff >= step:
            code |= 4
            diff -= step
        if diff >= step >> 1:
            code |= 2
            diff -= step >> 1
        if diff >= step >> 2:
            code |= 1
        # reconstruct exactly as the decoder will, so the two never drift apart
        delta = step >> 3
        if code & 4:
            delta += step
        if code & 2:
            delta += step >> 1
        if code & 1:
            delta += step >> 2
        pred += -delta if code & 8 else delta
        pred = max(-32768, min(32767, pred))
        index = max(0, min(88, index + IDX[code & 7]))
        if i % 2 == 0:
            nib = code & 0x0F
        else:
            out.append(nib | ((code & 0x0F) << 4))
    if len(pcm) % 2:
        out.append(nib)
    return bytes(out)



def decode_adpcm(blob: bytes, frames: int) -> np.ndarray:
    """Inverse of encode_adpcm; one definition shared with verify.py."""
    out = np.empty(frames, dtype=np.float32)
    pred, index, n = 0, 0, 0
    for byte in blob:
        for code in (byte & 0x0F, (byte >> 4) & 0x0F):
            if n >= frames:
                break
            step = STEP[index]
            delta = step >> 3
            if code & 4:
                delta += step
            if code & 2:
                delta += step >> 1
            if code & 1:
                delta += step >> 2
            pred += -delta if code & 8 else delta
            pred = max(-32768, min(32767, pred))
            index = max(0, min(88, index + IDX[code & 7]))
            out[n] = pred / 32768.0
            n += 1
        if n >= frames:
            break
    return out


def estimate_period_frames(x: np.ndarray, rate: int, lo: float = 50.0, hi: float = 2000.0) -> float:
    """Fundamental period in frames, by autocorrelation with parabolic peak fit.

    Integer lag is not enough: at 1 kHz one frame is 4.5 % of a period, which is
    the same order as the misalignment we are correcting.
    """
    if len(x) < 2048:
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
        den = 2 * (2 * y1 - y0 - y2)
        d = (y2 - y0) / den if abs(den) > 1e-12 else 0.0
        return (a - 1) + k + max(-1.0, min(1.0, d))
    return float((a - 1) + k)


def seam_score(x: np.ndarray, ls: int, le: int) -> float:
    """Correlation between the loop head and the material just past loopEnd.

    This is the quantity the ear hears at the join: 1.0 is seamless.
    """
    w = min(4096, (le - ls) // 3, len(x) - le - 1)
    if w < 128:
        return -1.0
    a = x[ls:ls + w] - x[ls:ls + w].mean()
    b = x[le:le + w] - x[le:le + w].mean()
    d = float(np.sqrt((a * a).sum() * (b * b).sum()))
    return float((a * b).sum() / d) if d > 1e-12 else -1.0


def snap_loop_end(x: np.ndarray, ls: int, le: int, span: int = 384) -> int:
    """Move loopEnd by up to `span` frames to maximise the seam correlation.

    Resampling 48 kHz -> 22.05 kHz moves the loop points by a fraction of a
    period, so a join that was sample-exact in the library is no longer phase
    aligned. Estimating the period from the audio is unreliable here: on high
    notes the autocorrelation picks the second harmonic and the loop lands an
    octave out. Searching the seam directly has no such failure mode.
    """
    best_le, best = le, seam_score(x, ls, le)
    for cand in range(max(ls + 256, le - span), min(len(x) - 2, le + span) + 1):
        s = seam_score(x, ls, cand)
        if s > best:
            best, best_le = s, cand
    return best_le


def build_type(kind: str, layers: list[str], keep: float) -> tuple[bytes, dict]:
    """Download, trim, resample and encode one sax type."""
    samples: list[dict] = []
    pcm = bytearray()
    for layer in layers:
        for row in region_map(kind, layer):
            name = row["name"]
            try:
                raw = fetch(f"MTG Solo Saxophones/Samples/{name}.flac")
            except Exception as exc:                       # noqa: BLE001
                print(f"    ! {name}: {exc}", file=sys.stderr)
                continue
            meta = flac_meta(raw)
            if not meta.get("loops"):
                print(f"    ! {name}: no loop", file=sys.stderr)
                continue
            x, rate = sf.read(io.BytesIO(raw), dtype="float32")
            if x.ndim > 1:
                x = x[:, 0]
            loop_start, loop_end = meta["loops"][0]
            # keep the attack, then the loop with a little margin so a short
            # crossfade stays inside the recorded material
            span = int((loop_end - loop_start) * keep)
            end = min(len(x), loop_end + span)
            seg = x[:end].copy()
            if rate != TARGET_RATE:
                g = np.gcd(rate, TARGET_RATE)
                seg = resample_poly(seg, TARGET_RATE // g, rate // g).astype("float32")
                scale = TARGET_RATE / rate
                loop_start = int(round(loop_start * scale))
                loop_end = int(round(loop_end * scale))
                loop_end = snap_loop_end(seg, loop_start, loop_end)
                end = len(seg)
            # remove DC so the ADPCM predictor does not chase an offset
            seg = seg - float(seg.mean())
            peak = float(np.max(np.abs(seg)))
            if peak > 1e-6:
                seg = seg / peak * 0.9
            # seam before encoding: separates a bad trim/resample from ADPCM noise
            w = min(4096, (loop_end - loop_start) // 3, len(seg) - loop_end - 1)
            seam = 0.0
            if w >= 256:
                a = seg[loop_start:loop_start + w] - seg[loop_start:loop_start + w].mean()
                b = seg[loop_end:loop_end + w] - seg[loop_end:loop_end + w].mean()
                d = float(np.sqrt((a * a).sum() * (b * b).sum()))
                if d > 1e-12:
                    seam = float((a * b).sum() / d)
            data = encode_adpcm(seg)
            dec = decode_adpcm(data, len(seg))
            err = float(np.sqrt(np.mean((seg - dec) ** 2)))
            ref = float(np.sqrt(np.mean(seg ** 2)))
            snr_db = 20 * float(np.log10(ref / err)) if err > 1e-9 and ref > 1e-9 else 99.0
            samples.append({
                "name": name,
                "layer": layer,
                "root": row["key"],
                "tune_cents": row["tune_cents"],
                "gain_db": row["gain_db"],
                "offset": len(pcm),
                "frames": len(seg),
                "loop_start": max(1, min(loop_start, len(seg) - 2)),
                "loop_end": max(2, min(loop_end, len(seg) - 1)),
                "snr_db": round(snr_db, 1),
                "seam_pre": round(seam, 3),
            })
            pcm += data
    return bytes(pcm), {"type": kind, "rate": TARGET_RATE, "samples": samples}


def write_pack(kind: str, pcm: bytes, manifest: dict) -> None:
    """`SAXP` container: header, sample records, then the ADPCM payload."""
    rows = manifest["samples"]
    rec = b""
    for s in rows:
        rec += struct.pack(
            "<BBBBIIIIf",
            s["root"] & 0xFF,
            0 if s["layer"] == "p" else 1,
            0, 0,
            s["offset"], s["frames"],
            s["loop_start"], s["loop_end"],
            s["tune_cents"],
        )
    header = struct.pack(
        "<4sIIIIII",
        b"SAXP", 1, manifest["rate"], len(rows), len(pcm), len(rec), 0,
    )
    (OUT_DIR / f"saxpack_{kind}.bin").write_bytes(header + rec + pcm)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--types", nargs="*", default=list(TYPES))
    ap.add_argument("--layers", nargs="*", default=["f", "p"])
    ap.add_argument("--keep", type=float, default=1.0,
                    help="loop spans kept after loopEnd (1.0 = one full loop)")
    args = ap.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    index = {}
    for kind in args.types:
        print(f"[{kind}] extracting (layers: {', '.join(args.layers)})")
        pcm, manifest = build_type(kind, args.layers, args.keep)
        if not manifest["samples"]:
            print(f"[{kind}] nothing extracted", file=sys.stderr)
            continue
        write_pack(kind, pcm, manifest)
        snrs = [x["snr_db"] for x in manifest["samples"]]
        seams = [x["seam_pre"] for x in manifest["samples"]]
        index[kind] = {
            "file": f"saxpack_{kind}.bin",
            "rate": manifest["rate"],
            "samples": len(manifest["samples"]),
            "pcm_bytes": len(pcm),
            "snr_db_median": round(float(np.median(snrs)), 1) if snrs else 0.0,
            "seam_pre_min": round(min(seams), 3) if seams else 0.0,
            "seam_pre_median": round(float(np.median(seams)), 3) if seams else 0.0,
        }
        roots = sorted({s["root"] for s in manifest["samples"]})
        size = (OUT_DIR / f"saxpack_{kind}.bin").stat().st_size
        print(f"[{kind}] {len(manifest['samples'])} samples, "
              f"MIDI {roots[0]}..{roots[-1]}, {size / 1024:.0f} Ko")
    (OUT_DIR / "saxpack.json").write_text(
        json.dumps({"license": "CC-BY 4.0 — MTG Solo Saxophones (kinwie)",
                    "source": "https://github.com/sfzinstruments/MTG.SoloSax",
                    "types": index}, indent=2))
    total = sum(v["pcm_bytes"] for v in index.values())
    print(f"\ntotal PCM {total / 1024 / 1024:.2f} Mo")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
