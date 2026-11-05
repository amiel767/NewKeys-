#!/usr/bin/env python3
"""Page de revue du design Sheets : la maquette vivante + ses etats captures.

Le JSX (app/assets/SheetsPreview.jsx) est la reference visuelle ; cette page
l'expose a cote des captures d'etats, pour juger sans lancer de build Android.

Usage : python3 tools/sheets_page.py
"""
from __future__ import annotations

import html
from pathlib import Path

PUB = Path("/workspace/dl/pub/sheets")
OUT = PUB / "index_page.html"

STATES = [
    ("01_library.png", "Bibliothèque · grille", "Cartes riches : tranche de couleur, titre, accords sur rail, tonalité, BPM, nombre de sections, date."),
    ("01b_list.png", "Bibliothèque · liste", "Même contenu en lignes compactes : bascule grille/liste dans l'en-tête."),
    ("02_floating_one.png", "Flottant · une section", "Double-tap sur une carte : elle se pose sur le mixer, une section. X en haut à droite, verrou en bas à gauche, poignée au centre."),
    ("03_picker_open.png", "Sections DANS la carte", "Tap sur la carte ou sur la pastille : la liste s'ouvre dans la carte, pas dans un popover externe."),
    ("04_floating_many.png", "Flottant · plusieurs sections", "Verse + Chorus + Bridge ; le balayage horizontal passe d'une section à l'autre."),
    ("05_editor.png", "Éditeur", "Ruban 40 % / page 60 %, quatre onglets."),
]

# Rendus Compose réels (Roborazzi), pas la maquette : ce que le code fait vraiment.
REAL = [
    ("sheets_home_real.png", "La bibliothèque, rendue par Compose", "Capture Android réelle : cartes redessinées (tranche, accords, tonalité, BPM, sections, date) et bascule grille/liste."),
    ("sheets_floating_real.png", "Le flottant, rendu par Compose", "Capture Android réelle : X en haut à droite, verrou en bas à gauche, poignée de redimensionnement au centre, pastille de section en bas à droite."),
]

STYLE = """
:root{--bg:#0A0D13;--panel:#151922;--line:#242a36;--ink:#e8ecf3;--dim:#8b95a7;--gold:#d9a441;--tag:#7aa2f7}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
header{padding:26px 32px 18px;border-bottom:1px solid var(--line);background:linear-gradient(180deg,#171c26,#0A0D13)}
h1{margin:0 0 6px;font-size:22px}
h1 span{color:var(--gold)}
.sub{color:var(--dim);font-size:13.5px}
.sub code{background:#1d2330;padding:1px 6px;border-radius:5px;color:var(--gold);font-size:12.5px}
.wrap{padding:22px 32px 60px;max-width:1200px}
.cta{display:inline-flex;align-items:center;gap:10px;margin:0 0 24px;padding:12px 20px;border-radius:12px;
background:var(--tag);color:#0A0D13;font-weight:800;text-decoration:none}
.cta small{font-weight:600;opacity:.75}
h2{font-size:16px;margin:30px 0 12px;color:var(--gold)}
.grid{display:grid;grid-template-columns:1fr;gap:22px}
figure{margin:0;background:var(--panel);border:1px solid var(--line);border-radius:12px;overflow:hidden}
figure img{display:block;width:100%;height:auto;background:#0A0D13}
figcaption{padding:12px 16px;font-size:13.5px}
figcaption b{display:block;font-size:14.5px;margin-bottom:3px}
figcaption span{color:var(--dim)}
.note{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:16px 20px;font-size:13.5px;color:var(--dim)}
.note b{color:var(--ink)}
.note ul{margin:8px 0 0;padding-left:20px}
.note li{margin:4px 0}
"""


def main() -> None:
    parts = [
        "<!doctype html><html lang=fr><meta charset=utf-8>",
        "<meta name=viewport content='width=device-width,initial-scale=1'>",
        "<title>Refonte Sheets — maquette</title>",
        f"<style>{STYLE}</style>",
        "<header><h1>Refonte <span>Sheets</span> — maquette de référence</h1>",
        "<div class=sub>Source : <code>app/assets/SheetsPreview.jsx</code> · 891 × 411 dp, "
        "la même convention que les autres maquettes du dépôt.</div></header>",
        "<div class=wrap>",
        "<a class=cta href='/sheets/'>▶ Ouvrir la maquette vivante <small>interactive — cliquable</small></a>",
        "<h2>Les six états</h2>",
        "<div class=grid>",
    ]
    for f, title, desc in STATES:
        if not (PUB / f).exists():
            continue
        parts.append(
            f"<figure><img src='/{html.escape(f)}' alt='{html.escape(title)}'>"
            f"<figcaption><b>{html.escape(title)}</b><span>{html.escape(desc)}</span></figcaption></figure>"
        )
    parts.append("</div>")
    parts.append("<h2>Le rendu Android réel</h2>")
    parts.append("<div class=note>La maquette JSX est la référence de design ; voici ce que le code Compose produit vraiment, "
                 "capturé par Roborazzi. C'est ce rendu qui a mis au jour la carte invisible sans rectangle d'origine.</div>")
    parts.append("<div class=grid>")
    for f, title, desc in REAL:
        if not (PUB / f).exists():
            continue
        parts.append(
            f"<figure><img src='/{html.escape(f)}' alt='{html.escape(title)}'>"
            f"<figcaption><b>{html.escape(title)}</b><span>{html.escape(desc)}</span></figcaption></figure>"
        )
    parts.append("</div>")
    parts.append(
        "<h2>Les sept points demandés</h2>"
        "<div class=note>"
        "<ul>"
        "<li>① Le texte en bas de la carte est <b>retiré</b> (plus de « Drag anywhere · double-tap to edit »).</li>"
        "<li>② Sections : <b>balayage</b> gauche/droite <b>et</b> tap qui ouvre la <b>liste dans la carte</b>.</li>"
        "<li>③ <b>X à droite</b> en haut, <b>cadenas en bas à gauche</b>.</li>"
        "<li>④ La carte s'<b>allonge</b> ; redimensionnable par <b>pincement</b> / poignée, avec <b>retour haptique</b> au glissement.</li>"
        "<li>⑤ <b>Double-tap</b> = flotter ; <b>tap simple</b> dans la bibliothèque = ouvrir l'éditeur.</li>"
        "<li>⑥ Cartes de bibliothèque <b>redessinées</b> : tranche de couleur, accords sur rail, tonalité, BPM, sections, date — et bascule grille/liste.</li>"
        "<li>⑦ Cohérence matérielle : palette app, rayons, typo, ruban 40 / page 60 inchangés.</li>"
        "</ul>"
        "</div>"
    )
    parts.append("</div></html>")
    OUT.write_text("\n".join(parts), encoding="utf-8")
    print(f"ecrit {OUT} ({OUT.stat().st_size} o)")


if __name__ == "__main__":
    main()
