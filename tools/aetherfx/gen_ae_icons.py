#!/usr/bin/env python3
"""Genere les icones Material utilisees, en ImageVector autonomes.

Retirer `material-icons-extended` (11 398 classes, une par icone) faisait tomber 15
references.  Ce script les reimplemente depuis les SVG officiels (Apache-2.0,
material-design-icons) dans les **memes paquets que la bibliotheque**
(`androidx.compose.material.icons.filled`, `.outlined`, `.automirrored.filled`),
donc les imports et les usages `Icons.Default.X` / `Icons.Outlined.X` existants
continuent de resoudre sans modifier aucun appelant.

    python3 tools/aetherfx/gen_ae_icons.py        # lit /tmp/svgs, ecrit app/src/main/java
"""
import os
import re
import sys

SRC = "/tmp/svgs"
OUT_ROOT = "app/src/main/java/androidx/compose/material/icons"

# nom SVG -> (identifiant Kotlin, paquets ou l'icone doit exister)
ICONS = {
    "folder": ("Folder", ["filled"]),
    "music_note": ("MusicNote", ["filled"]),
    "palette": ("Palette", ["filled"]),
    "content_copy": ("ContentCopy", ["filled"]),
    "exposure": ("Exposure", ["filled"]),
    "layers": ("Layers", ["filled"]),
    "unfold_less": ("UnfoldLess", ["filled"]),
    "unfold_more": ("UnfoldMore", ["filled"]),
    "volume_up": ("VolumeUp", ["filled"]),
    "pause": ("Pause", ["filled"]),
    "repeat": ("Repeat", ["filled"]),
    "tune": ("Tune", ["filled", "outlined"]),
    "sort": ("Sort", ["filled", "automirrored.filled"]),
    "undo": ("Undo", ["filled", "automirrored.filled"]),
    "redo": ("Redo", ["filled", "automirrored.filled"]),
}

# paquet -> (dossier, recepteur, nom de fichier)
PKG = {
    "filled": ("filled", "Icons.Filled", "AeIconsFilled.kt"),
    "outlined": ("outlined", "Icons.Outlined", "AeIconsOutlined.kt"),
    "automirrored.filled": ("automirrored/filled", "Icons.AutoMirrored.Filled", "AeIconsAutoMirrored.kt"),
}

NUM = r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?"


def parse(path):
    svg = open(path).read()
    m = re.search(r'<path[^>]*\sd="([^"]+)"', svg)
    return m.group(1) if m else ""


def tokens(d):
    return re.findall(r"[A-Za-z]|" + NUM, d)


def path_ops(d, mirror):
    """Convertit un `d` SVG en appels PathBuilder absolus. mirror : x -> 24-x (RTL)."""
    def X(v):
        return 24.0 - v if mirror else v

    tk = tokens(d)
    out = []
    x = y = 0.0
    start = None
    i = 0
    cmd = None
    while i < len(tk):
        t = tk[i]
        if t.isalpha():
            cmd = t
            i += 1
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            nx, ny = float(tk[i]), float(tk[i + 1])
            if rel:
                nx += x
                ny += y
            x, y = nx, ny
            start = (x, y)
            out.append(f"  moveTo({X(x)}f, {y}f)")
            i += 2
            cmd = "l" if rel else "L"      # les couples suivants d'un M sont des lineto
        elif c == "L":
            nx, ny = float(tk[i]), float(tk[i + 1])
            if rel:
                nx += x
                ny += y
            x, y = nx, ny
            out.append(f"  lineTo({X(x)}f, {y}f)")
            i += 2
        elif c == "H":
            x = float(tk[i]) + (x if rel else 0.0)
            out.append(f"  horizontalLineTo({X(x)}f)")
            i += 1
        elif c == "V":
            y = float(tk[i]) + (y if rel else 0.0)
            out.append(f"  verticalLineTo({y}f)")
            i += 1
        elif c == "C":
            a = [float(tk[i + k]) for k in range(6)]
            if rel:
                a = [a[0] + x, a[1] + y, a[2] + x, a[3] + y, a[4] + x, a[5] + y]
            x, y = a[4], a[5]
            out.append(f"  curveTo({X(a[0])}f, {a[1]}f, {X(a[2])}f, {a[3]}f, {X(a[4])}f, {a[5]}f)")
            i += 6
        elif c == "Z":
            out.append("  close()")
            if start:
                x, y = start
            i += 1
        else:
            i += 1
    return "\n".join(out)


def main():
    paths = {}
    for svg, (kt, pkgs) in ICONS.items():
        d = parse(os.path.join(SRC, svg + ".svg"))
        if not d:
            print(f"  !! pas de path : {svg}", file=sys.stderr)
            continue
        paths[kt] = d

    for pkg, (subdir, receiver, fname) in PKG.items():
        names = [kt for kt, pk in ICONS.values() if pkg in pk]
        if not names:
            continue
        mirror = pkg == "automirrored.filled"
        caches, props = [], []
        for kt in names:
            cache = f"_ae_{kt[0].lower()}{kt[1:]}_{'m' if mirror else 'f'}"
            caches.append(f"private var {cache}: ImageVector? = null")
            body = path_ops(paths[kt], mirror)
            props.append(
                f"""val {receiver}.{kt}: ImageVector
  get() = {cache} ?: ImageVector.Builder(
    name = "Ae{kt}", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
  ).apply {{
    path(fill = SolidColor(Color.Black)) {{
{body}
    }}
  }}.build().also {{ {cache} = it }}"""
            )
        head = f'''package androidx.compose.material.icons.{pkg}

// Genere par tools/aetherfx/gen_ae_icons.py — ne pas editer a la main.
// Icones Material officielles (Apache-2.0).  Reimplementees pour eviter la
// dependance `material-icons-extended` (11 398 classes, une par icone) dont
// {len(names)} seulement sont utilisees.

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

'''
        out_dir = os.path.join(OUT_ROOT, subdir)
        os.makedirs(out_dir, exist_ok=True)
        open(os.path.join(out_dir, fname), "w").write(
            head + "\n".join(caches) + "\n\n" + "\n\n".join(props) + "\n")
        print(f"  {pkg:22s} {len(names):2d} icones -> {os.path.join(out_dir, fname)}")


if __name__ == "__main__":
    main()
