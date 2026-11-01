#!/usr/bin/env python3
"""Encode the blues takes to MP3 and write the listening page.

    python3 tools/aetherfx/blues_page.py /tmp/blues /workspace/dl/pub/blues

The .f32 files are raw interleaved float32 stereo at 44.1 kHz, written by
demo_blues.cpp.  They are artefacts, not sources: regenerate them whenever the
engine or the presets change, otherwise the page plays an old instrument.
"""
from __future__ import annotations

import html
import os
import sys

import lameenc
import numpy as np

SR = 44100
ORDER = ["Honky", "Studio", "Bright", "Upright", "Felt", "Dark",
         "Electric Keys", "Lady D", "Mellow"]
NOTE = {
    "Honky": "le piano bastringue : le fill y est le plus mordant",
    "Studio": "le neutre, la reference d'ecoute",
    "Bright": "clavier ouvert, attaques nettes",
    "Upright": "droit, corps boise",
    "Felt": "feutre : presque pas d'attaque, tout en queue",
    "Dark": "sombre, pour la main gauche",
    "Electric Keys": "banque Wurlitzer, tine + tremolo",
    "Lady D": "droit clair, registre medium",
    "Mellow": "doux, fondu",
}


def slug(name: str) -> str:
    return name.lower().replace(" ", "_")


def encode(src: str, dst: str) -> float:
    x = np.fromfile(src, dtype="<f4").reshape(-1, 2)
    peak = float(np.max(np.abs(x))) if x.size else 0.0
    # Leave the takes at the engine's own level; the page's player is the only
    # gain stage, so nothing is normalised here and the models stay comparable.
    pcm = np.clip(x * 32767.0, -32768, 32767).astype("<i2")
    enc = lameenc.Encoder()
    enc.set_bit_rate(128)
    enc.set_in_sample_rate(SR)
    enc.set_channels(2)
    enc.set_quality(2)
    data = enc.encode(pcm.tobytes())
    data += enc.flush()
    with open(dst, "wb") as f:
        f.write(bytes(data))
    return peak


def main() -> None:
    src_dir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/blues"
    out_dir = sys.argv[2] if len(sys.argv) > 2 else "/workspace/dl/pub/blues"
    os.makedirs(out_dir, exist_ok=True)

    rows = []
    for name in ORDER:
        src = os.path.join(src_dir, f"blues_{slug(name)}.f32")
        if not os.path.exists(src):
            print(f"  {name}: absent")
            continue
        dst = os.path.join(out_dir, f"blues_{slug(name)}.mp3")
        peak = encode(src, dst)
        size = os.path.getsize(dst) / 1024
        print(f"  {name:<14} peak {peak:.3f}  {size:6.0f} Ko")
        rows.append((name, f"blues_{slug(name)}.mp3", peak))

    cards = "\n".join(
        f'''    <div class="card">
      <div class="head"><b>{html.escape(n)}</b><span>peak {p:.2f}</span></div>
      <p>{html.escape(NOTE.get(n, ""))}</p>
      <audio controls preload="none" src="{html.escape(f)}"></audio>
    </div>'''
        for n, f, p in rows
    )

    page = f"""<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Blues fill — Æther Piano</title>
<style>
  :root {{ color-scheme: dark; }}
  body {{ margin:0; background:#0b0e14; color:#e7ecf5;
         font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif; }}
  header {{ padding:28px 20px 6px; max-width:900px; margin:0 auto; }}
  h1 {{ margin:0 0 6px; font-size:22px; letter-spacing:.2px; }}
  .sub {{ color:#8b98ad; font-size:14px; }}
  main {{ max-width:900px; margin:0 auto; padding:16px 20px 60px; }}
  .fill {{ background:#131926; border:1px solid #1f2937; border-radius:12px;
           padding:14px 16px; margin:18px 0 26px; font-size:14px; }}
  .fill code {{ color:#7dd3fc; }}
  .card {{ background:#131926; border:1px solid #1f2937; border-radius:12px;
           padding:14px 16px; margin:12px 0; }}
  .head {{ display:flex; justify-content:space-between; align-items:baseline; }}
  .head span {{ color:#8b98ad; font-size:12px; font-variant-numeric:tabular-nums; }}
  .card p {{ margin:4px 0 10px; color:#a9b6c9; font-size:13px; }}
  audio {{ width:100%; height:34px; }}
  footer {{ color:#6b7688; font-size:12px; max-width:900px; margin:0 auto;
            padding:0 20px 40px; }}
</style></head>
<body>
<header>
  <h1>Blues fill — Æther Piano</h1>
  <p class="sub">Neuf modeles, le meme fill, pack piano dense (232 regions, 22,05 kHz).</p>
</header>
<main>
  <div class="fill">
    <b>Le fill :</b> turnaround chromatique descendant, <code>C5 B4 A#4 A4 G#4 G4 F#4 F4 E4 D#4 D4 C4</code>
    &mdash; douze croches, exactement une octave, atterrissage sur la tonique tenue.<br>
    Huit mesures de blues en do a 96 BPM : deux mesures de boogie shuffle, le fill,
    le shuffle, le fill. La basse est ecrite note a note, pas laissee a un symbole d'accord.
  </div>
{cards}
</main>
<footer>
  Genere par <code>tools/aetherfx/demo_blues.cpp</code> (moteur reel, hors Android) et
  encode par <code>tools/aetherfx/blues_page.py</code>. La justesse de chaque note est
  verifiee separement : <code>/tmp/db piano.bin presets.txt verify</code> &mdash; 12/12 a moins de 25 cents.
</footer>
</body></html>
"""
    with open(os.path.join(out_dir, "index.html"), "w") as f:
        f.write(page)
    print(f"\npage: {out_dir}/index.html  ({len(rows)} prises)")


if __name__ == "__main__":
    main()
