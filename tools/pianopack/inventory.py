#!/usr/bin/env python3
"""Inventaire des couches et du poids, sans rien telecharger.

Question tranchee ici : combien de couches peut-on reellement embarquer ?
Le README de la banque annonce 5 couches de dynamique, mais les fichiers ne les
portent pas toutes sur les memes notes. On mesure avant de choisir.
"""
import collections
import json
import os
import urllib.request

REPO = "sfzinstruments/SplendidGrandPiano"


def tree():
    req = urllib.request.Request(
        "https://api.github.com/repos/%s/git/trees/master?recursive=1" % REPO)
    tok = os.environ.get("GITHUB_TOKEN")
    if tok:
        req.add_header("Authorization", "Bearer " + tok)
    return json.load(urllib.request.urlopen(req, timeout=60))["tree"]


def main():
    t = tree()
    files = [(x["path"].split("/")[1], x.get("size", 0))
             for x in t if x["path"].startswith("Samples/")]
    layers = collections.defaultdict(list)
    by_note = collections.defaultdict(dict)
    for name, size in files:
        layer, note = name.split(" ", 1)
        note = note.rsplit(".", 1)[0]
        layers[layer].append(size)
        by_note[note][layer] = size

    print("== couches presentes ==")
    for L in sorted(layers):
        v = layers[L]
        print("  %-4s %3d fichiers  %6.1f Mo" % (L, len(v), sum(v) / 1048576))

    print()
    print("== combien de notes ont N couches ==")
    c = collections.Counter(len(d) for d in by_note.values())
    for n in sorted(c, reverse=True):
        print("  %d couches : %2d notes" % (n, c[n]))

    # profil du pack : garder les notes qui ont le plus de couches
    print()
    print("== profil de pack (notes triees par couches dispo) ==")
    order = sorted(by_note.items(), key=lambda kv: -len(kv[1]))
    for target_notes, layers_keep in [(24, 4), (30, 4), (37, 4), (30, 5), (44, 4)]:
        sel = order[:target_notes]
        tot = 0
        used = 0
        for note, d in sel:
            for L in sorted(d, key=lambda x: -d[x])[:layers_keep]:
                tot += d[L]
                used += 1
        # le pack final : mono 22 kHz ADPCM 4 bits apres troncature a ~2 s.
        # La source fait 44,1 kHz stereo 16 bits ; ratio mesure 6,2 % sur 4 regions.
        print("  %2d notes x <=%d couches : %3d regions, %5.1f Mo source -> ~%4.1f Mo pack"
              % (target_notes, layers_keep, used, tot / 1048576,
                 tot * 0.062 / 1048576))

    print()
    print("== poids par couche dans le pack (ratio 6,2 %) ==")
    for L in sorted(layers):
        print("  %-4s %5.1f Mo source -> ~%4.2f Mo pack"
              % (L, sum(layers[L]) / 1048576, sum(layers[L]) * 0.062 / 1048576))


if __name__ == "__main__":
    main()
