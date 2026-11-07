"""Genere demo_presets.h depuis AePresets.kt.

La table des presets ne doit PAS etre recopiee a la main : la version precedente
l'etait, et elle s'etait desalignee (LEVEL en 11 au lieu de MODEL). Ici la source
est le fichier Kotlin lui-meme, donc la demo joue exactement ce que l'app charge.
"""
import re
from pathlib import Path

SRC = Path("app/src/main/java/com/soundstage/mixer/instruments/AePresets.kt")
OUT = Path("tools/aetherfx/demo_presets.h")
PIANO_SRC = Path("app/src/main/java/com/soundstage/mixer/instruments/AePianoUi.kt")

text = SRC.read_text(encoding="utf-8")
# Chaque instrument est un bloc "val <nom>: List<AePreset> = listOf( ... )"
blocks = re.findall(r"val (\w+): List<AePreset> = listOf\((.*?)\n    \)", text, re.S)
assert blocks, "aucun bloc AePreset trouve"

def lit(x):
    """Litteral C++ flottant : `g` seul rendrait `1f`, qui n'est pas un flottant."""
    t = f"{x:g}"
    return t + ("" if ("." in t or "e" in t or "inf" in t or "nan" in t) else ".") + "f"


def entries(body):
    out = []
    for m in re.finditer(r'AePreset\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*floatArrayOf\(([^)]*)\)', body):
        name, cat, vals = m.group(1), m.group(2), m.group(3)
        v = [x.strip().rstrip("f") for x in vals.split(",") if x.strip()]
        out.append((name, cat, [float(x) for x in v]))
    return out

lines = ["// GENERE par gen_demo_presets.py — ne pas editer a la main.",
         "// Source : app/src/main/java/com/soundstage/mixer/instruments/AePresets.kt",
         "#pragma once", "struct DemoPreset { const char* name; const char* cat; int n; float v[34]; };", ""]
counts = {}
for name, body in blocks:
    es = entries(body)
    counts[name] = len(es)
    lines.append(f"static const DemoPreset k_{name}[] = {{")
    for n, c, v in es:
        vv = ", ".join(lit(x) for x in v)
        lines.append(f'    {{"{n}", "{c}", {len(v)}, {{{vv}}}}},')
    lines.append("};")
    lines.append(f"static const int k_{name}_n = {len(es)};")
    lines.append("")
# --- Piano : les modeles vivent dans AePianoUi.kt, avec une formule de valeurs ---
ptext = PIANO_SRC.read_text(encoding="utf-8")
models = re.findall(
    r'AePianoModel\("(\w+)",\s*"([^"]+)",\s*"[^"]*",\s*\w+,\s*col\(0x[0-9A-Fa-f]+\),\s*.*?AePType\.\w+,\s*floatArrayOf\(([^)]*)\),\s*(\d+)\)',
    ptext)
assert models, "aucun modele piano trouve"
lines.append("// Piano : mirror EXACT de AePianoModel.values (20 parametres).")
lines.append("static const DemoPreset k_piano[] = {")
for mid, name, vals, idx in models:
    d = [float(x.strip().rstrip("f")) for x in vals.split(",") if x.strip()]
    i = int(idx)
    v = [0.0] * 20
    for j in range(15):
        v[j] = d[j]
    v[10] = 0.20 + 0.30 * d[0]   # DAMPER
    v[12] = 0.15 + 0.40 * d[0]   # KEY
    v[13] = 0.0                  # libre
    v[15] = i / 8.0              # MODEL
    v[16] = 0.25 + 0.60 * d[0]   # HAMMER N
    v[17] = d[15]                # EQ MID
    v[18] = d[16]                # EQ FREQ
    v[19] = d[17]                # EQ HIGH
    vv = ", ".join(lit(x) for x in v)
    lines.append(f'    {{"{name}", "{mid}", {len(v)}, {{{vv}}}}},')
lines.append("};")
lines.append(f"static const int k_piano_n = {len(models)};")
counts["piano"] = len(models)
OUT.write_text("\n".join(lines), encoding="utf-8")
print(" ".join(f"{k}={v}" for k, v in counts.items()))
