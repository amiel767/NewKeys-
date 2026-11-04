"""Audit des ressources Android potentiellement mortes.

Trois facons dont une ressource peut etre vivante :
1. reference directe `R.drawable.x`, `@drawable/x`, `R.font.y`, `@font/y` ;
2. nom CONSTRUIT par prefixe, ex. `getIdentifier("ae_piano_$id", ...)` : on
   extrait les litteraux de code et on considere vivant tout nom dont un
   litteral est un prefixe (`"ae_piano_"` -> `ae_piano_felt`) ;
3. variante de densite du meme nom logique.

Sortie : une ligne par ressource non referencee, groupee par type, avec la
taille.  Ne modifie rien.

Usage : python3 tools/audit_dead_res.py [--verbose]
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main")
RES = os.path.join(SRC, "res")

RES_DIRS = ["drawable", "drawable-nodpi", "mipmap-anydpi-v26", "mipmap-hdpi",
            "mipmap-mdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi",
            "font", "raw", "xml", "menu", "color", "anim"]


def collect_text():
    parts = []
    for base in (os.path.join(SRC, "java"), os.path.join(SRC, "kotlin")):
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if fn.endswith((".kt", ".java")):
                    parts.append(open(os.path.join(dp, fn), encoding="utf-8",
                                      errors="ignore").read())
    parts.append(open(os.path.join(SRC, "AndroidManifest.xml"), encoding="utf-8",
                      errors="ignore").read())
    for dp, _, fns in os.walk(RES):
        for fn in fns:
            if fn.endswith(".xml"):
                parts.append(open(os.path.join(dp, fn), encoding="utf-8",
                                  errors="ignore").read())
    return "\n".join(parts)


def main():
    verbose = "--verbose" in sys.argv
    text = collect_text()

    # 1. references directes
    used = set(re.findall(r"[Rr]\.(\w+)\.(\w+)", text))
    used |= set(re.findall(r"@(?:android:)?(\w+)/(\w+)", text))

    # 2. litteraux de code : un litteral prefixe d'un nom le rend vivant.
    #    Un nom est souvent CONSTRUIT ("ae_piano_$id") : on decoupe chaque
    #    chaine sur `$` et `{}` pour garder les morceaux litteraux.
    lits = set()
    for s in re.findall(r'"([^"\n]*)"', text):
        for piece in re.split(r"[$]|\{|\}|%s|%d|%f", s):
            piece = piece.strip()
            if len(piece) >= 3:
                lits.add(piece)
    lits = sorted(lits, key=len, reverse=True)

    def by_prefix(name):
        return any(lit.endswith("_") and name.startswith(lit) for lit in lits)

    by_name = defaultdict(list)
    for t in RES_DIRS:
        d = os.path.join(RES, t)
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            p = os.path.join(d, fn)
            if not os.path.isfile(p):
                continue
            by_name[(t.split("-")[0], fn.rsplit(".", 1)[0])].append(
                os.path.relpath(p, ROOT))

    dead, alive = [], []
    for (t, name), files in sorted(by_name.items()):
        ref = ((t, name) in used or ("drawable", name) in used
               or ("mipmap", name) in used or by_prefix(name))
        (alive if ref else dead).append((t, name, files))

    print("=== RESSOURCES POTENTIELLEMENT MORTES ===")
    tot = 0
    for t, name, files in dead:
        sz = sum(os.path.getsize(os.path.join(ROOT, f)) for f in files)
        tot += sz
        print(f"  {t}/{name}  ({sz/1024:.1f} Ko)")
    print(f"  -> {len(dead)} ressources, {tot/1024:.1f} Ko")
    if verbose:
        print("\n=== VIVANTES ===")
        for t, name, files in alive:
            print(f"  {t}/{name}")


if __name__ == "__main__":
    main()
