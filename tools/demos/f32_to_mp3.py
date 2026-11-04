#!/usr/bin/env python3
"""Convertit les demos .f32 (PCM stereo 44,1 kHz) en MP3 normalise -1 dBFS.

    python3 tools/demos/f32_to_mp3.py <src_dir_f32> <dst_dir_mp3>

Sert a regenerer tools/demos/audio/ apres un changement du moteur.
"""
import glob
import os
import subprocess
import sys
import wave

import numpy as np


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(dst, exist_ok=True)
    n = 0
    for f in sorted(glob.glob(os.path.join(src, "*.f32"))):
        x = np.fromfile(f, dtype="<f4")
        peak = float(np.abs(x).max()) or 1.0
        x = np.clip(x / peak * 0.89, -1.0, 1.0)
        pcm = (x * 32767).astype("<i2")
        base = os.path.basename(f)[:-4]
        wav = os.path.join(dst, base + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(44100)
            w.writeframes(pcm.tobytes())
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
             "-codec:a", "libmp3lame", "-b:a", "192k",
             os.path.join(dst, base + ".mp3")],
            check=True,
        )
        os.remove(wav)
        n += 1
    print(f"{n} fichiers -> {dst}")


if __name__ == "__main__":
    main()
