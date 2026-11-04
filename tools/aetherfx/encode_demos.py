"""Encode les demos (pianos/EP + sax) en MP3 legers, un fichier par instrument.

Normalisation RMS commune : le rendu brut contourne le limiteur de l'app, et une
comparaison de timbres n'est juste qu'a sonie egale.  Un silence de tete est retire
pour que chaque fichier commence sur la premiere note.

Debit volontairement bas (defaut 48 kbps) : ce sont des demos d'ecoute, pas des
masters.  Le sax est encode en MONO (sa largeur stereo n'apporte rien et coute
deux fois le poids) ; le piano reste stereo (banques stereo).
"""
import glob, os, sys
import numpy as np
import lameenc

SR = 44100
TARGET = 0.16
KBPS = int(sys.argv[1]) if len(sys.argv) > 1 else 48
OUT = sys.argv[2] if len(sys.argv) > 2 else "/workspace/dl/pub/demos"
os.makedirs(OUT, exist_ok=True)

PIANO_NAMES = ["Felt", "Studio", "Bright", "Mellow", "Dark", "Cottage",
               "Parlor", "Saloon", "Tine Keys", "Ballad", "DX Bell", "E.Piano"]
SAX_SLUGS = ["soprano", "alto", "tenor", "baryton"]


def load_stereo(path):
    x = np.fromfile(path, dtype="<f4").reshape(-1, 2)
    live = np.abs(x).max(axis=1) > 1e-4
    a = int(live.argmax()) if live.any() else 0
    return x[a:]


def encode(x, path, channels):
    if channels == 1:
        x = x.mean(axis=1, keepdims=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    enc = lameenc.Encoder()
    enc.set_bit_rate(KBPS); enc.set_in_sample_rate(SR); enc.set_channels(channels)
    enc.set_quality(3)
    data = enc.encode(pcm.tobytes()) + enc.flush()
    open(path, "wb").write(data)
    return len(data)


rows = []
for i, src in enumerate(sorted(glob.glob("/tmp/demo_all/piano_*.f32"))):
    x = load_stereo(src)
    x = x * (TARGET / (float(np.sqrt((x.astype(np.float64) ** 2).mean())) or 1e-9))
    name = PIANO_NAMES[i] if i < len(PIANO_NAMES) else f"model_{i}"
    p = os.path.join(OUT, f"piano_{i:02d}_{name.replace(' ', '_').replace('.', '')}.mp3")
    rows.append((f"piano {name}", encode(x, p, 2) // 1024, x.shape[0] / SR))

for slug in SAX_SLUGS:
    src = f"/tmp/demo_sax/sax_{slug}.f32"
    if not os.path.exists(src):
        print(f"  manquant : {src}"); continue
    x = load_stereo(src)
    x = x * (TARGET / (float(np.sqrt((x.astype(np.float64) ** 2).mean())) or 1e-9))
    p = os.path.join(OUT, f"sax_{slug}.mp3")
    rows.append((f"sax   {slug}", encode(x, p, 1) // 1024, x.shape[0] / SR))

for n, k, d in rows:
    print(f"  {n:16s} {k:4d} Ko  {d:4.1f} s")
print(f"TOTAL {sum(r[1] for r in rows)} Ko ({len(rows)} fichiers) -> {OUT}")
