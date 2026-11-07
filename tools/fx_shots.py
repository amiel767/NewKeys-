#!/usr/bin/env python3
"""Compresse les captures FX en WebP basse qualite et ecrit une page d'index.

    python3 tools/fx_shots.py [qualite] [largeur]

Qualite par defaut 45, largeur max 900 px : assez pour juger une mise en page,
tres loin des ~150 Ko de PNG Roborazzi.
"""
import glob
import os
import sys

from PIL import Image

SRC = "app/src/test/screenshots"
OUT = "/workspace/dl/pub/fxshots"

Q = int(sys.argv[1]) if len(sys.argv) > 1 else 45
W = int(sys.argv[2]) if len(sys.argv) > 2 else 900

TITLES = {
    "fxslots_empty": "Rack vide",
    "fxslots_filled": "Rack rempli (Arp + Space)",
    "fxslots_catalog": "Catalogue",
    "fxslots_arp": "Editeur Arpeggiator",
    "fxslots_space": "Editeur Æther Space (fenetre agrandie)",
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, "*.webp")):
        os.remove(old)
    shots = sorted(glob.glob(os.path.join(SRC, "fxslots_*.png")))
    if not shots:
        sys.exit("aucune capture fxslots_*.png")
    cards = []
    for path in shots:
        name = os.path.splitext(os.path.basename(path))[0]
        img = Image.open(path).convert("RGB")
        if img.width > W:
            img = img.resize((W, round(img.height * W / img.width)), Image.LANCZOS)
        dst = os.path.join(OUT, name + ".webp")
        img.save(dst, "WEBP", quality=Q, method=5)
        cards.append(
            f'<figure><img src="{name}.webp" loading="lazy" alt="{name}">'
            f'<figcaption>{TITLES.get(name, name)}'
            f'<span>{img.width}x{img.height} &middot; {os.path.getsize(dst)//1024} Ko</span>'
            f'</figcaption></figure>'
        )
    html = f"""<!doctype html><html lang="fr"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Fenetre FX - captures</title>
<style>
 body{{margin:0;padding:28px;background:#0d1018;color:#e7ecf5;
      font:15px/1.5 -apple-system,Segoe UI,Roboto,sans-serif}}
 h1{{margin:0 0 6px;font-size:20px;font-weight:800}}
 p.sub{{margin:0 0 22px;color:#8b95b2;font-size:13px}}
 .grid{{display:grid;gap:18px;grid-template-columns:repeat(auto-fit,minmax(360px,1fr))}}
 figure{{margin:0;background:#161b28;border:1px solid rgba(255,255,255,.07);
        border-radius:14px;overflow:hidden}}
 img{{display:block;width:100%;height:auto;background:#05070c}}
 figcaption{{padding:10px 12px;font-size:13px;font-weight:700;color:#cdd6ea;
            display:flex;justify-content:space-between;gap:10px}}
 figcaption span{{color:#7c86a8;font-weight:600;font-size:12px}}
</style>
<h1>Fenetre FX</h1>
<p class="sub">Branche <b>design/fx-reverb</b> &middot; WebP qualite {Q}, largeur max {W} px</p>
<div class="grid">{''.join(cards)}</div>
</html>"""
    with open(os.path.join(OUT, "index.html"), "w") as f:
        f.write(html)
    total = sum(os.path.getsize(p) for p in glob.glob(os.path.join(OUT, "*.webp")))
    print(f"{len(cards)} images, {total//1024} Ko au total")


if __name__ == "__main__":
    main()
