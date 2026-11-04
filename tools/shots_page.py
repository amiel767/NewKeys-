#!/usr/bin/env python3
"""Ecrit la page des captures d'ecran, en versions compressees.

    python3 tools/shots_page.py [dossier_sortie]      # defaut /workspace/dl/pub/shots

Les PNG Roborazzi de `app/src/test/screenshots/` font ~3 Mo piece (99 Mo au
total) : trop lourds pour etre consultes dans un navigateur. Chaque capture est
donc reencodee en WebP, en deux tailles — une vignette pour la grille, une image
pleine pour l'agrandissement — et la page ne charge les vignettes qu'a l'ecran
(`loading="lazy"`).

La page reprend le theme de `demos_page.py` (sombre/or) pour que les deux
livraisons se lisent comme un seul document.
"""
import glob
import os
import sys

from PIL import Image

SRC = "app/src/test/screenshots"
OUT = sys.argv[1] if len(sys.argv) > 1 else "/workspace/dl/pub/shots"

THUMB_W = 520
THUMB_Q = 68
FULL_W = 1500
FULL_Q = 78

# Prefixe de fichier -> (titre de section, ordre d'affichage). Le premier prefixe
# qui correspond gagne ; l'ordre des sections suit cette liste.
SECTIONS = [
    ("aether_window", "Fenetre Æther Instruments", 0),
    ("sound_source_chooser", "Fenetre Æther Instruments", 0),
    ("ae_", "Æther (instruments)", 1),
    ("fx_", "Fenetre FX", 2),
    ("vkb_", "Clavier virtuel", 3),
    ("virtual_keyboard", "Clavier virtuel", 3),
    ("mixer_", "Mixer", 4),
    ("full_mixer", "Mixer", 4),
    ("channel_strip", "Mixer", 4),
    ("bottom_bar", "Mixer", 4),
    ("fader_thumb", "Mixer", 4),
    ("setlist_", "Setlists", 5),
    ("sheets_", "Sheets", 5),
    ("settings_", "Reglages", 6),
    ("sf_", "Soundfonts", 7),
    ("track_settings", "Mixer", 4),
    ("live_looper", "Live Looper", 8),
    ("ll_", "Live Looper", 8),
    ("drumpad", "DrumPad", 8),
    ("greeting", "Divers", 9),
]

SECTION_ORDER = []
for _, name, order in SECTIONS:
    if name not in SECTION_ORDER:
        SECTION_ORDER.append(name)
SECTION_ORDER.sort(key=lambda n: next(o for _, s, o in SECTIONS if s == n))


def section_of(basename: str) -> str:
    for prefix, name, _ in SECTIONS:
        if basename.startswith(prefix):
            return name
    return "Divers"


def label_of(basename: str) -> str:
    return basename[:-4].replace("_", " ")


def encode(src: str, out_dir: str) -> tuple[str, str]:
    """Reencode une capture en deux WebP ; renvoie (vignette, pleine)."""
    base = os.path.basename(src)[:-4]
    im = Image.open(src).convert("RGB")
    tw = min(THUMB_W, im.width)
    tim = im.resize((tw, round(tw * im.height / im.width)), Image.LANCZOS)
    tpath = os.path.join(out_dir, f"{base}.thumb.webp")
    tim.save(tpath, "WEBP", quality=THUMB_Q, method=5)

    fw = min(FULL_W, im.width)
    fim = im if fw == im.width else im.resize((fw, round(fw * im.height / im.width)), Image.LANCZOS)
    fpath = os.path.join(out_dir, f"{base}.webp")
    fim.save(fpath, "WEBP", quality=FULL_Q, method=5)
    return tpath, fpath


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    srcs = sorted(glob.glob(os.path.join(SRC, "*.png")))
    if not srcs:
        print(f"aucune capture dans {SRC}")
        return 1

    by_section: dict[str, list[tuple[str, str, str]]] = {}
    before = after = 0
    skipped = []
    for src in srcs:
        base = os.path.basename(src)
        # `GreetingScreenshotTest` fails on a clean checkout and leaves a truncated
        # PNG behind; one unreadable file must not cost the whole page.
        try:
            thumb, full = encode(src, OUT)
        except (OSError, ValueError):
            skipped.append(base)
            continue
        before += os.path.getsize(src)
        after += os.path.getsize(thumb) + os.path.getsize(full)
        by_section.setdefault(section_of(base), []).append(
            (label_of(base), os.path.basename(thumb), os.path.basename(full))
        )

    blocks = []
    for name in SECTION_ORDER:
        items = by_section.get(name)
        if not items:
            continue
        cards = "\n".join(
            f'''    <figure class="shot">
      <a href="{full}" target="_blank" rel="noopener"><img loading="lazy" src="{thumb}" alt="{label}"></a>
      <figcaption>{label}</figcaption>
    </figure>'''
            for label, thumb, full in items
        )
        blocks.append(
            f'''  <h2>{name} <span class="tag">{len(items)}</span></h2>
  <div class="grid">
{cards}
  </div>'''
        )

    page = f'''<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AEther - captures d'ecran</title>
<style>
  :root{{--bg:#0d0f13;--panel:#161a21;--panel2:#1e232c;--line:#2b313c;
        --ink:#e8ecf2;--dim:#8b95a5;--gold:#f2b84a;--gold2:#c8892a;}}
  *{{box-sizing:border-box}}
  body{{margin:0;background:radial-gradient(1200px 700px at 20% -10%,#1a1f28,#0d0f13 60%);
       color:var(--ink);font:15px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
       padding:28px 18px 60px}}
  .wrap{{max-width:1080px;margin:0 auto}}
  h1{{font-size:22px;margin:0 0 4px;letter-spacing:.3px}}
  h1 b{{color:var(--gold)}}
  .sub{{color:var(--dim);margin:0 0 22px;font-size:13.5px}}
  h2{{font-size:13px;text-transform:uppercase;letter-spacing:1.4px;color:var(--gold);
     margin:30px 0 12px;font-weight:700}}
  .grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:12px}}
  .shot{{margin:0;background:linear-gradient(180deg,var(--panel2),var(--panel));
        border:1px solid var(--line);border-radius:12px;padding:8px;display:flex;
        flex-direction:column;gap:7px}}
  .shot img{{display:block;width:100%;border-radius:7px;background:#0a0c10}}
  .shot a{{display:block;line-height:0}}
  figcaption{{color:var(--dim);font-size:11.5px;letter-spacing:.2px}}
  .tag{{display:inline-block;background:#20262f;border:1px solid var(--line);border-radius:6px;
       padding:1px 7px;font-size:11px;color:var(--dim);margin-left:5px}}
  .note{{background:#141821;border:1px solid var(--line);border-left:3px solid var(--gold2);
        border-radius:8px;padding:11px 13px;color:var(--dim);font-size:12.5px;margin:18px 0}}
  .note b{{color:var(--ink)}}
  code{{color:var(--gold2)}}
</style>
</head>
<body>
<div class="wrap">
  <h1>AEther - <b>captures d'ecran</b></h1>
  <p class="sub">{len(srcs) - len(skipped)} rendus Compose reels (Roborazzi, hors emulateur), reencodees en WebP.
     Vignette {THUMB_W} px pour la grille, image pleine {FULL_W} px au clic.
     {before / 1048576:.0f} Mo de PNG &rarr; {after / 1048576:.1f} Mo de WebP.</p>

{chr(10).join(blocks)}

  <div class="note">
    <b>Regenerer :</b> les captures viennent de
    <code>./gradlew :app:recordRoborazziDebug --tests "com.soundstage.mixer.*CaptureTest"</code>,
    puis <code>python3 tools/shots_page.py</code> reecrit cette page et les images.
  </div>
</div>
</body></html>
'''
    with open(os.path.join(OUT, "index.html"), "w") as f:
        f.write(page)
    print(f"page: {os.path.join(OUT, 'index.html')}")
    print(f"{len(srcs) - len(skipped)} captures, {before / 1048576:.0f} Mo -> {after / 1048576:.1f} Mo "
          f"({100 * after / max(before, 1):.0f} %)")
    if skipped:
        print(f"ignorees (illisibles) : {', '.join(skipped)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
