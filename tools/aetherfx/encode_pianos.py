"""Encode un MP3 par piano/EP depuis les .f32 de demo_pianos_all.

Normalisation RMS identique pour les 12 : le rendu brut contourne le limiteur de
l'app, et une comparaison de timbres n'est juste qu'a sonie egale.  Sans ca le
modele le plus fort parait le meilleur.
"""
import glob, os, sys, numpy as np, lameenc

SR = 44100
TARGET = 0.16
KBPS = int(sys.argv[1]) if len(sys.argv) > 1 else 80
OUT = sys.argv[2] if len(sys.argv) > 2 else "/workspace/dl/pub/piano_all"
os.makedirs(OUT, exist_ok=True)

names = ["Felt", "Studio", "Bright", "Mellow", "Dark", "Cottage",
         "Parlor", "Saloon", "Tine Keys", "Ballad", "DX Bell", "E.Piano"]
rows = []
for i, src in enumerate(sorted(glob.glob("/tmp/demo_all/piano_*.f32"))):
    x = np.fromfile(src, dtype="<f4")
    rms = float(np.sqrt(np.mean(x.astype(np.float64) ** 2))) or 1e-9
    x = np.clip(x * (TARGET / rms), -1, 1)
    pcm = (x * 32767).astype("<i2")
    enc = lameenc.Encoder()
    enc.set_bit_rate(KBPS); enc.set_in_sample_rate(SR); enc.set_channels(2)
    enc.set_quality(2)
    data = enc.encode(pcm.tobytes()) + enc.flush()
    p = os.path.join(OUT, f"{i:02d}_{names[i].replace(' ', '_').replace('.', '')}.mp3")
    open(p, "wb").write(data)
    rows.append((i, names[i], len(data) // 1024))

tot = sum(r[2] for r in rows)
for i, n, k in rows: print(f"  {i:2d} {n:10s} {k:4d} Ko")
print(f"total {tot} Ko")
