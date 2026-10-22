"""Audit the port against source 1 (the preview HTML), not against the PNGs.

For each effect it extracts the hex colours of the preview's `function <name>()`
body and the ones the port's matching `fun DrawScope.scene<Name>()` writes, then
reports the difference. A colour used by the preview but absent from the port is
a strong signal of a missing drawing block, because the port copies the preview's
palette literally (`parseHex("#rrggbb")` / `Color(0xFFrrggbb)`).

Run from the repository root:  python3 tools/fxcompare/color_audit.py
"""
import pathlib
import re

PREVIEW = "app/assets/aether-fx-package/fx-window-preview.html"
SOURCES = [
    "app/src/main/java/com/soundstage/mixer/ui/fx/FxInstrumentScenes.kt",
    "app/src/main/java/com/soundstage/mixer/ui/fx/FxScenes.kt",
]

# (effect id, preview function name, Kotlin scene function suffix)
EFFECTS = [
    (0, "space", "sceneOrb"), (1, "echo", "sceneEcho"), (2, "tone", "sceneEq"),
    (3, "grip", "sceneGrip"), (4, "ceiling", "sceneCeil"), (5, "piano", "scenePiano"),
    (6, "organ", "sceneOrgan"), (7, "sax", "sceneSax"), (8, "strings", "sceneStrings"),
    (9, "guitar", "sceneGuitar"), (10, "amp", "sceneAmp"), (11, "bass", "sceneBass"),
    (12, "brass", "sceneBrass"), (13, "voices", "sceneVoices"), (14, "synth", "sceneSynth"),
]


def _balanced(text, start):
    """Return the `{...}` block starting at the first brace at or after `start`."""
    j = text.find("{", start)
    depth = 0
    for k in range(j, len(text)):
        if text[k] == "{":
            depth += 1
        elif text[k] == "}":
            depth -= 1
            if depth == 0:
                return text[j : k + 1]
    return text[j:]


def preview_body(html, name):
    i = html.find("function " + name + "(")
    return _balanced(html, i) if i >= 0 else ""


def kotlin_body(sources, name):
    for text in sources.values():
        m = re.search(r"fun\s+DrawScope\." + name + r"\s*\(", text)
        if m:
            return _balanced(text, m.end())
    return ""


def preview_colours(body):
    return {m.group(1).lower() for m in re.finditer(r"#([0-9a-fA-F]{6})\b", body)}


def kotlin_colours(body):
    found = {m.group(1).lower() for m in re.finditer(r"0x[fF]{2}([0-9a-fA-F]{6})", body)}
    found |= {m.group(1).lower() for m in re.finditer(r'"#([0-9a-fA-F]{6})"', body)}
    for m in re.finditer(r"0x([0-9a-fA-F]{8})", body):
        v = m.group(1).lower()
        if v.startswith("ff"):
            found.add(v[2:])
    return found


def main():
    html = pathlib.Path(PREVIEW).read_text()
    sources = {f: pathlib.Path(f).read_text() for f in SOURCES}
    rows = []
    for effect_id, pname, kname in EFFECTS:
        expected = preview_colours(preview_body(html, pname))
        actual = kotlin_colours(kotlin_body(sources, kname))
        rows.append((len(expected - actual), effect_id, pname, len(expected), sorted(expected - actual)))
    print(f'{"id":>2} {"effect":9s} {"preview":>8} {"missing":>8}')
    for missing, effect_id, pname, total, _ in rows:
        print(f"{effect_id:>2} {pname:9s} {total:>8} {missing:>8}")
    print("\nmissing colours, worst first:")
    for missing, effect_id, pname, total, colours in sorted(rows, reverse=True):
        if missing:
            print(f"  {effect_id:>2} {pname:9s} {missing:>2}/{total} : {colours}")


if __name__ == "__main__":
    main()
