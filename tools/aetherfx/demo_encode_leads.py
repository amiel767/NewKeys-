"""Encode the leads demo takes to MP3.

Reads the raw stereo float files written by demo_leads.cpp, normalises each take to the same RMS so
the two builds can be compared by ear, and writes one MP3 per take. Both directories are expected to
hold the same take names.
"""
import os
import subprocess
import sys

import numpy as np

SR = 48000
TARGET_RMS = 0.16
KBPS = 96

NAMES = ["pure_sine", "mono_saw", "gospel_supersaw", "praise_lead"]


def load(path):
    return np.fromfile(path, dtype="<f4")


def encode(x, path):
    x = np.clip(x, -1.0, 1.0)
    pcm = (x * 32767.0).astype("<i2")
    # ffmpeg is the only encoder available here; lameenc is not installed in this sandbox.
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(SR), "-ac", "1",
         "-i", "pipe:0", "-b:a", f"{KBPS}k", path],
        input=pcm.tobytes(), check=True,
    )


def main():
    old_dir, new_dir, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out_dir, exist_ok=True)
    for name in NAMES:
        for tag, d in (("old", old_dir), ("new", new_dir)):
            p = os.path.join(d, name + ".f32")
            if not os.path.exists(p):
                print(f"missing {p}")
                continue
            x = load(p)
            # Stereo interleaved -> mono for the demo.
            x = x.reshape(-1, 2).mean(axis=1)
            rms = float(np.sqrt(np.mean(x ** 2))) or 1e-9
            x = x * (TARGET_RMS / rms)
            out = os.path.join(out_dir, f"{name}_{tag}.mp3")
            encode(x, out)
            peak = float(np.max(np.abs(x)))
            print(f"{out}  rms {TARGET_RMS:.3f} peak {peak:.3f}")


if __name__ == "__main__":
    main()
