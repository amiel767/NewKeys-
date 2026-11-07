"""Regenerates the Space entry of both generated FxRegistry.kt files from fx_registry.h.

The registries are GENERATED (gen_kotlin.cpp). That generator is not in the tree, so this
script performs the one edit that matters here: it rewrites the FX_SPACE parameter list and
preset table so the Kotlin mirror matches the native header exactly.

    python3 tools/design/sync_space_registry.py            # check only, exit 1 on drift
    python3 tools/design/sync_space_registry.py --write    # rewrite the two files

The order of the parameter list is a CONTRACT: the UI pushes a normalised value by index, so
a mismatch silently sends the wrong control to the wrong DSP parameter.
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]

# (name, min, max, unit, decimals, default, linear)
PARAMS = [
    ("Mix",        0,     100,  "%",  0, 0.34, True),
    ("Pre-delay",  0,     200,  "ms", 0, 0.10, True),
    ("Decay",      0.1,   30,   "s",  2, 0.13, True),
    ("HighFreq",   1000,  16000,"Hz", 0, 0.33, True),
    ("HighShelf",  -24,   0,    "dB", 1, 0.50, True),
    ("BassFreq",   100,   1000, "Hz", 0, 0.67, True),
    ("BassMult",   0.5,   3,    "X",  2, 0.40, True),
    ("Size",       0,     100,  "%",  0, 1.00, True),
    ("Attack",     0,     100,  "%",  0, 0.50, True),
    ("Early",      0,     100,  "%",  0, 1.00, True),
    ("Late",       0,     100,  "%",  0, 1.00, True),
    ("Rate",       0.1,   10,   "Hz", 2, 0.25, True),
    ("Depth",      0,     100,  "%",  0, 0.38, True),
    ("HighCut",    1000,  20000,"Hz", 0, 0.37, True),
    ("LowCut",     10,    500,  "Hz", 0, 0.00, True),
]

PRESETS = [
    ("Shimmer",   [.38, .14, .2467, .5333, .50, .6667, .40, .82, .62, .70, .80, .1111, .46, .7895, .3469]),
    ("Chamber",   [.30, .07, .0862, .3667, .50, .4444, .46, .48, .40, .60, .55, .0505, .22, .5789, .1633]),
    ("Room",      [.24, .03, .0336, .28, .75, .3556, .32, .30, .55, .80, .40, .0253, .12, .4211, .1020]),
    ("Hall",      [.34, .10, .1304, .3333, .50, .6667, .40, 1., .50, 1., 1., .2455, .38, .3684, 0.]),
    ("Plate",     [.32, .05, .0706, .6667, .8333, .2778, .20, .55, .72, .90, .70, .0707, .18, .7368, .2653]),
    ("Cathedral", [.42, .24, .4181, .2133, .25, .7778, .64, .96, .25, .50, .90, .0303, .30, .3158, .2245]),
    ("Spring",    [.28, .02, .0572, .44, .6667, .3889, .32, .40, .80, .70, .35, .3131, .52, .5, .2857]),
    ("Cloud",     [.48, .30, .2980, .1733, .1667, .6111, .52, .90, .18, .40, 1., .0808, .64, .2895, .2041]),
    ("Infinite",  [.45, .40, .6980, .2, .1667, .6667, .72, 1., .3, .5, 1., .0707, .70, .4737, .4082]),
    ("Bloom",     [.42, .30, .2800, .24, .25, .6111, .56, .95, .35, 1., .6, .0505, .60, .2632, .3061]),
]

FILES = [
    ROOT / "app/src/main/java/com/aetherkeys/fx/FxRegistry.kt",
    ROOT / "app/src/main/java/com/soundstage/mixer/fx/FxRegistry.kt",
]


def fnum(x):
    """Kotlin float literal, trailing dot dropped for whole numbers."""
    if x == int(x):
        return "%df" % int(x)
    return "%gf" % x


def entry(indent, name):
    p = ",\n".join(
        "%s    FxParam(\"%s\", %s, %s, \"%s\", %d, %s, FxCurve.LINEAR, false)"
        % (indent, n, fnum(lo), fnum(hi), u, d, fnum(df))
        for (n, lo, hi, u, d, df, _lin) in PARAMS
    )
    r = ",\n".join(
        "%s    FxPreset(\"%s\", listOf(%s))" % (indent, n, ", ".join(fnum(v) for v in vals))
        for (n, vals) in PRESETS
    )
    return ("FxInfo(0, \"%s\", \"Reverb\", FxKind.EFFECT,\n"
            "%s    listOf(\n%s,\n%s    ),\n"
            "%s    listOf(\n%s,\n%s    ))," % (name, indent, p, indent, indent, r, indent))


def patch(text, indent):
    start = text.index("FxInfo(0, \"")
    # The two mirrors spell the effect differently ("Aether Space" / "Æther Space"); the name
    # is cosmetic, so it is preserved and only the parameters and presets are rewritten.
    name = text[start + len("FxInfo(0, \""):text.index("\",", start)]
    end = text.index("FxInfo(1, ", start)
    return text[:start] + entry(indent, name) + "\n" + text[end:]


def main():
    write = "--write" in sys.argv
    drift = False
    for f in FILES:
        text = f.read_text()
        indent = " " * (len(text.splitlines()[0]) - len(text.splitlines()[0].lstrip()) + 4)
        indent = " " * 8
        new = patch(text, indent)
        if new != text:
            drift = True
            print("drift:", f.relative_to(ROOT))
            if write:
                f.write_text(new)
    if write:
        print("rewritten" if drift else "already in sync")
    elif drift:
        print("registries are stale — run with --write")
        return 1
    else:
        print("registries in sync")
    return 0


if __name__ == "__main__":
    sys.exit(main())
