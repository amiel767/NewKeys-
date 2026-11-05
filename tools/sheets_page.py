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
     "Les Sets sont en haut, toujours visibles : une rangée de cartes de verre dépoli de 272 × 96 dp. L'aperçu d'accords y occupe toute la rangée basse, à 21 sp. En dessous, la liste, une feuille par ligne de 58 dp, aperçu à 19 sp."),
    ("sheets_sets_real.png", "Les Sets, rendus par Compose",
     "Un Set est une carte large : nom choisi, couleur propre, aperçu des premières feuilles et de leurs accords, nombre de feuilles. On entre dans un Set par sa carte ; le Set ouvert se marque « OUVERT »."),
    ("sheets_inside_a_set_real.png", "Dans un Set, rendu par Compose",
     "Ouvrir un Set ne montre que ses feuilles, dans l'ordre de jeu. La rangée de Sets reste visible pour passer d'un Set à l'autre ; « ← Tout » en sort."),
    ("sheets_settings_real.png", "The settings, rendered by Compose",
     "Every control explains itself: the notation group says what the choice does to the charts on screen, the default key spells Bb rather than A#, and the scene switches say what they change."),
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
        "<title>Refonte Sheets — accueil et scène</title>",
        f"<style>{STYLE}</style>",
        "<header><h1>Refonte <span>Sheets</span> — accueil et scène flottante</h1>",
        "<div class=sub>Rendu <b>réel</b> du code Compose, capturé par Roborazzi en 2450 × 1130 "
        "(w851dp-h393dp-440dpi, la convention des autres captures du dépôt). "
        "Les PNG bruts pèsent 4,4 Mo ; la page ne sert que du WebP.</div></header>",
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
    parts.append("<h2>Les neuf captures</h2>")
    parts.append("<div class=note>Chaque vignette pèse ~3 Ko ; cliquer ouvre la version 1500 px. "
                 "Aucun PNG brut n'est servi : une capture Roborazzi pèse ~0,5 Mo, la même en WebP ~18 Ko.</div>")
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
        "<h2>Ce que la refonte change</h2>"
        "<div class=note>"
        "<ul>"
        "<li>① Les Sets <b>ne sont plus un filtre</b> ni une grille : ce sont des <b>cartes en haut</b>, "
        "toujours visibles, à la <b>même hauteur que la page Setlists</b> (96 dp).</li>"
        "<li>② <b>Pas d'icône dossier, pas d'aperçu de Set</b> : la carte porte le nom choisi, sa couleur, "
        "les premières feuilles et l'aperçu de leurs accords.</li>"
        "<li>③ Le contrôle <b>« + Set »</b> est aligné avec <b>Favoris</b> ; le nom d'un Set n'apparaît "
        "qu'à un seul endroit (sa carte).</li>"
        "<li>④ La liste du bas a <b>grandi</b> : une feuille par ligne de <b>58 dp</b> (avant 44), "
        "et l'aperçu d'accords passe de 15 à <b>19 sp</b>.</li>"
        "<li>⑤ Dans un Set, la rangée reste visible pour <b>passer d'un Set à l'autre</b> ; « ← Tout » en sort.</li>"
        "<li>⑥ La scène flottante est <b>allégée</b> : pastilles quasi invisibles collées aux bords, X fondu.</li>"
        "<li>⑦ <b>Édition rapide</b> : un appui long relit la ligne d'accords de la section affichée, "
        "au clavier système — et le mixer suit la section affichée, plus seulement la première.</li>"
        "<li>⑧ <b>Orthographe des notes</b> : elle suit l'armure. Une même feuille écrivait « A# » en Bb "
        "et « Ab » en A ; désormais une seule table de noms, dérivée de <code>Theory.noteName</code>, "
        "partagée par la liste, l'éditeur et l'export.</li>"
        "<li>⑨ <b>Réglages</b> refaits pour la nouvelle interface : chaque contrôle explique ce qu'il change, "
        "et le réglage « sens du balayage », stocké mais <b>jamais lu</b> depuis toujours, est enfin branché.</li>"
        "<li>⑩ <b>Verre dépoli</b> : les cartes et les lignes n'ont plus de contour dessiné. Un fond translucide "
        "teinté, un grain fin, un reflet spéculaire en diagonale, une lumière d'arête en haut et un liseré "
        "qui n'apparaît que là où la lumière accroche. Mesuré : le reflet décroît de 79 à 49 de la gauche "
        "vers la droite, et le bord haut ne présente <b>aucun saut</b> de luminance — c'est une lumière, pas un trait.</li>"
        "<li>⑪ L'<b>aperçu d'accords</b> des cartes de Set passe de 14 à <b>21 sp</b> (+50 %) : il occupe "
        "désormais toute la rangée basse, le compteur de feuilles remontant dans l'en-tête.</li>"
        "</ul>"
        "</div>"
    )
    parts.append("</div></html>")
    OUT.write_text("\n".join(parts), encoding="utf-8")
    print(f"ecrit {OUT} ({OUT.stat().st_size} o)")


if __name__ == "__main__":
    main()
