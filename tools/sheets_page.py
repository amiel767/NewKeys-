#!/usr/bin/env python3
"""Page de revue du design Sheets : la maquette vivante + ses etats captures.

Le JSX (app/assets/SheetsPreview.jsx) est la reference visuelle ; cette page
l'expose a cote des captures d'etats, pour juger sans lancer de build Android.

Usage : python3 tools/sheets_page.py
"""
from __future__ import annotations

import html
import os
from pathlib import Path

from PIL import Image

PUB = Path("/workspace/dl/pub/sheets")
SRC = Path("app/src/test/screenshots")

# Meme convention que tools/shots_page.py : une capture ne se livre jamais en
# PNG brut. Vignette 520 px q68 pour la grille, pleine 1500 px q78 pour le
# detail. Un resize a la moitie de la capture perd la moitie de la definition :
# la pleine largeur doit rester 1500 px.
THUMB_W = 520
THUMB_Q = 68
FULL_W = 1500
FULL_Q = 78


def encode(src: Path) -> tuple[str, str] | None:
    """Reencode une capture PNG en deux WebP ; renvoie (vignette, pleine)."""
    if not src.exists():
        return None
    im = Image.open(src).convert("RGB")
    tw = min(THUMB_W, im.width)
    tim = im.resize((tw, round(tw * im.height / im.width)), Image.LANCZOS)
    tpath = PUB / f"{src.stem}.thumb.webp"
    tim.save(tpath, "WEBP", quality=THUMB_Q, method=5)

    fw = min(FULL_W, im.width)
    fim = im if fw == im.width else im.resize((fw, round(fw * im.height / im.width)), Image.LANCZOS)
    fpath = PUB / f"{src.stem}.webp"
    fim.save(fpath, "WEBP", quality=FULL_Q, method=5)
    return tpath.name, fpath.name
OUT = PUB / "index_page.html"

STATES = [
    ("01_library.png", "Bibliothèque · grille", "Cartes riches : tranche de couleur, titre, accords sur rail, tonalité, BPM, nombre de sections, date."),
    ("02_floating_one.png", "Flottant · une section", "Double-tap sur une carte : elle se pose sur le mixer, une section. X en haut à droite, verrou en bas à gauche, poignée au centre."),
    ("03_picker_open.png", "Sections DANS la carte", "Tap sur la carte ou sur la pastille : la liste s'ouvre dans la carte, pas dans un popover externe."),
    ("04_floating_many.png", "Flottant · plusieurs sections", "Verse + Chorus + Bridge ; le balayage horizontal passe d'une section à l'autre."),
    ("05_editor.png", "Éditeur", "Ruban 40 % / page 60 %, quatre onglets."),
]

# Rendus Compose réels (Roborazzi), pas la maquette : ce que le code fait vraiment.
REAL = [
    ("sheets_home_real.png", "L'accueil, rendu par Compose",
     "Les Sets sont en haut, toujours visibles : une rangée de cartes de 96 dp, la même taille que la page Setlists. En dessous, la liste des feuilles, une par ligne de 44 dp, avec l'aperçu des accords et les informations."),
    ("sheets_sets_real.png", "Les Sets, rendus par Compose",
     "Un Set est une carte large : nom choisi, couleur propre, aperçu des premières feuilles et de leurs accords, nombre de feuilles. On entre dans un Set par sa carte ; le Set ouvert se marque « OUVERT »."),
    ("sheets_inside_a_set_real.png", "Dans un Set, rendu par Compose",
     "Ouvrir un Set ne montre que ses feuilles, dans l'ordre de jeu. La rangée de Sets reste visible pour passer d'un Set à l'autre ; « ← Tout » en sort."),
    ("sheets_floating_quick_edit_real.png", "L'édition rapide, rendue par Compose",
     "Un appui long sur la scène ouvre la ligne d'accords de la section affichée, au clavier système. C'est le langage du chart (« 1 4 | 5 6m | % »), donc rien de nouveau à apprendre ; l'éditeur complet reste au double tap."),
    ("sheets_floating_real.png", "Le flottant, rendu par Compose",
     "X fondu en haut à droite, verrou en bas à gauche, pastilles de redimensionnement quasi invisibles collées aux bords (bas = hauteur, gauche = largeur), pastille de section en bas à droite."),
    ("sheets_floating_tall_real.png", "La pastille du bas allonge la fenêtre",
     "On tire la pastille fine du bas : la fenêtre s'allonge et la section suivante s'affiche dès qu'elle entre dans la hauteur."),
    ("sheets_floating_wide_real.png", "La pastille de gauche élargit, les polices suivent",
     "On tire la pastille fine de gauche : la fenêtre s'élargit, les accords se replacent et la taille des polices grandit avec la largeur."),
    ("sheets_floating_sections_real.png", "La liste de sections, rendue par Compose",
     "La liste s'ouvre DANS la carte, par-dessus le chart, avec un voile à la teinte de l'encre."),
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


def clean_stale_pngs() -> None:
    """Retire du dossier publie les PNG bruts : la page ne sert que du WebP."""
    for old in PUB.glob("*.png"):
        old.unlink()


def main() -> None:
    clean_stale_pngs()
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
        "<h2>Les cinq états de la maquette</h2>",
        "<div class=grid>",
    ]
    for f, title, desc in STATES:
        if not (PUB / f).exists():
            continue
        parts.append(
            f"<figure><img src='{html.escape(f)}' alt='{html.escape(title)}'>"
            f"<figcaption><b>{html.escape(title)}</b><span>{html.escape(desc)}</span></figcaption></figure>"
        )
    parts.append("</div>")
    parts.append("<h2>Le rendu Android réel</h2>")
    parts.append("<div class=note>La maquette JSX est la référence de design ; voici ce que le code Compose produit vraiment, "
                 "capturé par Roborazzi. C'est ce rendu qui a mis au jour la carte invisible sans rectangle d'origine.</div>")
    parts.append("<div class=grid>")
    before = after = 0
    for f, title, desc in REAL:
        src = SRC / f.replace("_real.png", ".png")
        enc = encode(src)
        if enc is None:
            continue
        thumb, full = enc
        before += src.stat().st_size
        after += (PUB / full).stat().st_size
        parts.append(
            f"<figure><a href='{html.escape(full)}'><img loading=lazy src='{html.escape(thumb)}' alt='{html.escape(title)}'></a>"
            f"<figcaption><b>{html.escape(title)}</b><span>{html.escape(desc)}</span></figcaption></figure>"
        )
    parts.append("</div>")
    if before:
        parts.append(
            f"<div class=note>{before / 1048576:.1f} Mo de PNG brut &rarr; {after / 1048576:.2f} Mo de WebP "
            f"(vignette {THUMB_W} px, pleine {FULL_W} px). Cliquer une image ouvre la version pleine.</div>"
        )
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
