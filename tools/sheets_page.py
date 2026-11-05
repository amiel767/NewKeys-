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
    ("01_library.png", "Bibliothèque", "Grille de cartes, chaque carte a la couleur de la feuille."),
    ("02_floating_one.png", "Flottant · une section", "Double-tap sur une carte : elle se pose sur le mixer, sans contour, une section."),
    ("03_picker_open.png", "Sélecteur de sections", "Le bouton calques choisit ce qui s'affiche."),
    ("04_floating_many.png", "Flottant · plusieurs sections", "Verse + Chorus + Bridge, défilables dans la carte."),
    ("05_editor.png", "Éditeur", "Ruban 40 % / page 60 %, quatre onglets."),
]

# Rendus Compose réels (Roborazzi), pas la maquette : ce que le code fait vraiment.
REAL = [
    ("floating_real.png", "Le flottant, rendu par Compose", "Capture Android réelle : carte à la couleur de la feuille, 371 dp, sans contour."),
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
        "<h2>Les cinq états</h2>",
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
        "<h2>Ce que la maquette fixe</h2>"
        "<div class=note>"
        "<b>Le flottant, la partie en refonte :</b>"
        "<ul>"
        "<li>Double-tap sur une carte → la carte se pose <b>sur le mixer</b>, à sa couleur, <b>sans contour</b>.</li>"
        "<li>Déplaçable <b>n'importe où</b>, sans aimantation (aujourd'hui : aimant gauche/droite seulement).</li>"
        "<li>Le bouton <b>calques</b> choisit <b>une</b> section ou <b>plusieurs</b> (Verse, Chorus, Bridge…).</li>"
        "<li>Verrou et double-tap pour ouvrir l'éditeur restent.</li>"
        "</ul>"
        "<b>Ce qui ne change pas :</b> palette app, rayons, typo, grille de la bibliothèque, ruban 40 / page 60."
        "</div>"
    )
    parts.append("</div></html>")
    OUT.write_text("\n".join(parts), encoding="utf-8")
    print(f"ecrit {OUT} ({OUT.stat().st_size} o)")


if __name__ == "__main__":
    main()
