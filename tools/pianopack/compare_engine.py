"""Compare what the engine renders to the original recording of the same note.

The samples are good (SplendidGrandPiano, public domain). If the engine's C4 does
not look like the recording's C4, the fault is in my pipeline, not the source.
Reports the three things that make a piano sound like a piano: how bright it is,
how fast it decays, and whether the top end survives.
"""
import sys
import json
import numpy as np

sys.path.insert(0, "tools/pianopack")
from measure import load_mono, sample_path, f0_nominal  # noqa: E402
from inspect_loops import decode  # noqa: E402

BIN = "app/src/main/assets/pianopack/piano.bin"
JSON = "app/src/main/assets/pianopack/piano.json"


def centroid(x, sr, a, b):
    """Spectral centroid of the window [a, b) in seconds."""
    seg = x[int(a * sr):int(b * sr)]
    if len(seg) < 64:
        return 0.0
    sp = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
    f = np.fft.rfftfreq(len(seg), 1 / sr)
    e = sp ** 2
    return float((e * f).sum() / max(e.sum(), 1e-12))


def hiFrac(x, sr, a, b, cut=8000):
    seg = x[int(a * sr):int(b * sr)]
    if len(seg) < 64:
        return 0.0
    sp = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
    f = np.fft.rfftfreq(len(seg), 1 / sr)
    e = sp ** 2
    return float(e[f > cut].sum() / max(e.sum(), 1e-12))


def decayDbPerSec(x, sr, a=0.4, b=2.0):
    """Slope of the level in dB per second between a and b."""
    win = int(0.05 * sr)
    lv = []
    for t in np.arange(a, b, 0.05):
        seg = x[int(t * sr):int(t * sr) + win]
        lv.append(20 * np.log10(np.sqrt(np.mean(seg ** 2)) + 1e-9))
    lv = np.array(lv)
    ts = np.arange(a, a + 0.05 * len(lv), 0.05)[:len(lv)]
    ok = np.isfinite(lv)
    if ok.sum() < 4:
        return 0.0
    return float(np.polyfit(ts[ok], lv[ok], 1)[0])


def main():
    blob = open(BIN, "rb").read()
    meta = json.load(open(JSON))
    payload = 28 + int.from_bytes(blob[20:24], "little")
    rate = int.from_bytes(blob[8:12], "little")
    regions = meta["regions"]

    # Composition : combien de racines, combien de couches.
    notes = sorted({m["sample"].split(" ", 1)[1][:-5] for m in regions})
    lay = sorted({m["sample"].split(" ", 1)[0] for m in regions})
    print("pack : %d regions | %d notes racines | %d couches (%s) | %d Hz"
          % (len(regions), len(notes), len(lay), ",".join(lay), rate))

    print("\n%-10s %-7s %-7s %-7s %-7s %-7s" %
          ("note", "ctr_src", "ctr_pck", "hi_src", "hi_pck", "dB/s"))
    for name in ["FF C2.flac", "FF C3.flac", "FF C4.flac", "MF C5.flac", "FF C6.flac"]:
        m = next((x for x in regions if x["sample"] == name), None)
        if m is None:
            print("%-10s absent du pack" % name)
            continue
        src, sr = load_mono(sample_path(name))
        pcm = decode(blob[payload + m["offset"]:], 0, m["n"])[:, 0]
        # Meme fenetre de temps pour les deux : le sustain, apres l'attaque.
        cs = centroid(src, sr, 0.4, 1.2)
        cp = centroid(pcm, rate, 0.4, 1.2)
        hs = hiFrac(src, sr, 0.4, 1.2)
        hp = hiFrac(pcm, rate, 0.4, 1.2)
        print("%-10s %-7.0f %-7.0f %-6.2f%% %-6.2f%% %-7.1f"
              % (name, cs, cp, 100 * hs, 100 * hp, decayDbPerSec(src, sr)))

    # Verifie que le raccord de boucle tombe sur la meme phase APRES reechantillonnage.
    print("\nraccord de boucle, verifie dans le pack (pas dans la source) :")
    worst = []
    for m in regions:
        if m["loop_end"] <= 0:
            continue
        pcm = decode(blob[payload + m["offset"]:], 0, m["n"])[:, 0]
        ls, le = m["loop_start"], m["loop_end"]
        rms = float(np.sqrt(np.mean(pcm[ls:le] ** 2))) + 1e-9
        worst.append((abs(pcm[ls] - pcm[le - 1]) / rms, m["sample"], le - ls))
    worst.sort(reverse=True)
    r = np.array([w[0] for w in worst])
    print("  saut/RMS : median %.3f | >0.5 : %d | >1.0 : %d sur %d"
          % (np.median(r), int((r > 0.5).sum()), int((r > 1.0).sum()), len(r)))
    print("  longueur de boucle mediane : %.0f ms"
          % np.median([w[2] / rate * 1000 for w in worst]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
