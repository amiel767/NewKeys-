#!/usr/bin/env python3
"""Page de revue du design FX : avant / apres, zooms sur les potards, reference.

    python3 tools/design/fx_page.py

Ecrit dans /workspace/dl/pub/fxdesign (servi sur le port 12000). Les PNG Roborazzi
font ~3 Mo : tout est reencode en WebP (vignette 520 px, pleine 1500 px), comme
`tools/shots_page.py`. Les gros plans de potard sont recadres puis agrandis au
plus proche voisin, pour que la matiere du disque reste lisible.
"""
import os
import subprocess

from PIL import Image

OUT = "/workspace/dl/pub/fxdesign"
REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = os.path.join(REPO, "app/src/test/screenshots")
VALHALLA = "/workspace/dl/valhalla"
SCALE = 2450 / 891.0  # capture 2450 px pour un canvas de 891 unites


def webp(src, dst, width, q=80):
    im = Image.open(src).convert("RGB")
    w = min(width, im.width)
    if w != im.width:
        im = im.resize((w, round(w * im.height / im.width)), Image.LANCZOS)
    im.save(dst, "WEBP", quality=q, method=5)


def knob(src, dst, cx, cy, radius, out_radius=200, half=1.55):
    """Gros plan d'un potard, NORMALISE : le disque sort au meme rayon partout.

    `cx,cy,radius` sont en pixels natifs de la source. Sans cette normalisation,
    le potard de l'app (rayon 288 px dans une capture de 2450 px) et celui de la
    reference (rayon 54 px) ne se comparent pas : on ne jugerait que la taille.
    """
    im = Image.open(src).convert("RGB")
    h = int(radius * half)
    box = (cx - h, cy - h, cx + h, cy + h)
    c = im.crop(box)
    target = int(out_radius * half * 2)
    c = c.resize((target, target), Image.LANCZOS)
    c.save(dst, "WEBP", quality=90, method=5)


def main():
    os.makedirs(OUT, exist_ok=True)

    # « avant » = capture du commit precedent (potard 3D bombe).
    before = "/tmp/before_space.png"
    if not os.path.exists(before):
        with open(before, "wb") as f:
            subprocess.run(
                ["git", "show", "3acac9c~1:app/src/test/screenshots/fx_space.png"],
                cwd=REPO, stdout=f, check=True)

    after = os.path.join(SHOTS, "fx_space.png")
    webp(before, f"{OUT}/a_before.webp", 1500)
    webp(after, f"{OUT}/b_after.webp", 1500)
    for name in ("fx_space_knob_low", "fx_space_dropdown", "fx_space_shimmer",
                 "fx_rack", "fx_catalog"):
        p = os.path.join(SHOTS, name + ".png")
        if os.path.exists(p):
            webp(p, f"{OUT}/after_{name}.webp", 1500)
            webp(p, f"{OUT}/thumb_{name}.webp", 520, 70)

    # Gros plan du potard central, normalise au meme rayon de disque dans les
    # trois images. APP : centre mesure (145, 116) unites, disque rayon ~144 px.
    # REFERENCE : disque natif centre (985, 343), rayon 54 px.
    knob(before, f"{OUT}/k_before_big.webp", int(147 * SCALE), int(158 * SCALE), 210)
    knob(after, f"{OUT}/k_after_big.webp", int(147 * SCALE), int(158 * SCALE), 210)
    knob(after, f"{OUT}/k_after_small.webp", int(393 * SCALE), int(126 * SCALE), 24)
    ref = os.path.join(VALHALLA, "room_24.jpg")
    if os.path.exists(ref):
        knob(ref, f"{OUT}/k_ref.webp", 985, 343, 54)

    html = PAGE.format()
    with open(f"{OUT}/index.html", "w") as f:
        f.write(html)
    print("page:", f"{OUT}/index.html")


PAGE = """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FX Design - potards plats</title>
<style>
 :root{{--bg:#0b0d12;--panel:#141822;--line:#2a3040;--ink:#e8ecf5;--dim:#8b93a7;--gold:#e0b45c}}
 *{{box-sizing:border-box}}
 body{{margin:0;background:var(--bg);color:var(--ink);
   font:15px/1.5 -apple-system,Segoe UI,Roboto,sans-serif}}
 header{{padding:34px 28px 18px;border-bottom:1px solid var(--line)}}
 h1{{margin:0 0 6px;font-size:26px;letter-spacing:.5px}}
 h1 span{{color:var(--gold)}}
 .sub{{color:var(--dim);max-width:820px}}
 h2{{margin:38px 28px 12px;font-size:18px;color:var(--gold);
   border-bottom:1px solid var(--line);padding-bottom:8px}}
 .grid{{display:grid;gap:18px;padding:0 28px;grid-template-columns:repeat(auto-fit,minmax(340px,1fr))}}
 figure{{margin:0;background:var(--panel);border:1px solid var(--line);
   border-radius:10px;overflow:hidden}}
 figure img{{display:block;width:100%}}
 figcaption{{padding:9px 12px;font-size:13px;color:var(--dim)}}
 figcaption b{{color:var(--ink)}}
 .pair{{display:grid;gap:18px;padding:0 28px;grid-template-columns:1fr 1fr}}
 .knobs{{display:grid;gap:18px;padding:0 28px;
   grid-template-columns:repeat(auto-fit,minmax(210px,1fr))}}
 .knobs figure img{{image-rendering:pixelated}}
 .note{{margin:16px 28px;padding:12px 16px;background:#1a1408;
   border-left:3px solid var(--gold);border-radius:6px;color:#e6d7b0;font-size:14px}}
 a{{color:var(--gold)}}
</style></head><body>
<header>
  <h1>Fenetre FX - <span>potards plats</span> style Valhalla</h1>
  <p class="sub">Branche <code>design/fx-reverb</code>, commit <code>3acac9c</code>.
  Le potard 3D bombe est retire : disque plat, liseré fin, arc de valeur, aiguille
  blanche. Titre en Orbitron. Case preset = pastille compacte a droite.
  Reference : <code>/workspace/dl/valhalla/room_24.jpg</code>.</p>
</header>

<h2>1. Avant / apres (pleine fenetre)</h2>
<div class="pair">
  <figure><img src="a_before.webp"><figcaption><b>AVANT</b> - potard bombe, dome + collerette, titre Cinzel</figcaption></figure>
  <figure><img src="b_after.webp"><figcaption><b>APRES</b> - disque plat, braise contenue, titre Orbitron</figcaption></figure>
</div>

<h2>2. Gros plan - potard central</h2>
<div class="knobs">
  <figure><img src="k_before_big.webp"><figcaption><b>AVANT</b> - centre 164 / bord 43 : un dome</figcaption></figure>
  <figure><img src="k_after_big.webp"><figcaption><b>APRES</b> - bord 35,40,54 uni : plat</figcaption></figure>
  <figure><img src="k_ref.webp"><figcaption><b>REFERENCE Valhalla</b> - disque uni 121 / panneau 52</figcaption></figure>
</div>

<h2>3. Gros plan - petit potard de la grille</h2>
<div class="knobs">
  <figure><img src="k_after_small.webp"><figcaption>Disque uni (42,47,60), liseré fin, aiguille blanche. Aucun degre de relief.</figcaption></figure>
</div>

<div class="note">Le potard ne se lit plus que par : disque plus clair que le
panneau + filet clair au bord + arc de valeur + aiguille. C'est exactement la
grammaire de la reference.</div>

<h2>4. Etats du meme ecran</h2>
<div class="grid">
  <figure><a href="after_fx_space_shimmer.webp" target="_blank"><img loading="lazy" src="thumb_fx_space_shimmer.webp"></a><figcaption><b>Preset Shimmer</b> - fond bicolore</figcaption></figure>
  <figure><a href="after_fx_space_knob_low.webp" target="_blank"><img loading="lazy" src="thumb_fx_space_knob_low.webp"></a><figcaption><b>Potard central bas</b> - braise plus faible</figcaption></figure>
  <figure><a href="after_fx_space_dropdown.webp" target="_blank"><img loading="lazy" src="thumb_fx_space_dropdown.webp"></a><figcaption><b>Liste de presets ouverte</b> - ancree a droite</figcaption></figure>
  <figure><a href="after_fx_rack.webp" target="_blank"><img loading="lazy" src="thumb_fx_rack.webp"></a><figcaption><b>Le rack</b> - cartes d'effets</figcaption></figure>
  <figure><a href="after_fx_catalog.webp" target="_blank"><img loading="lazy" src="thumb_fx_catalog.webp"></a><figcaption><b>Catalogue</b> - ajout d'effet</figcaption></figure>
</div>
</body></html>
"""

if __name__ == "__main__":
    main()
