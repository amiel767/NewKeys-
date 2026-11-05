#!/usr/bin/env python3
"""Page de revue du design FX : REFERENCE et RENDU cote a cote.

    python3 tools/design/fx_page.py

C'est la demande : « les rapports doivent montrer l'image de reference et le
design produit cote a cote ». Chaque section pose la reference Valhalla a gauche
et notre rendu a droite, a la meme echelle de lecture.

Les gros plans de potard sont NORMALISES : le disque sort au meme rayon dans
toutes les images, sinon on ne juge que la taille et pas la matiere. Sans cette
normalisation, le potard de l'app (rayon 271 px dans une capture de 2450 px) et
celui de la reference (rayon 54 px) ne se comparent pas.

Ecrit dans /workspace/dl/pub/fxdesign, servi sur le port 12000. Les PNG Roborazzi
pesent ~3 Mo : tout est reencode en WebP.
"""
import os
import subprocess

from PIL import Image

OUT = "/workspace/dl/pub/fxdesign"
REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = os.path.join(REPO, "app/src/test/screenshots")
VALHALLA = "/workspace/dl/valhalla"
SCALE = 2450 / 891.0  # capture 2450 px pour un canvas de 891 unites
REF = os.path.join(VALHALLA, "room_24.jpg")


def webp(src, dst, width, q=80):
    im = Image.open(src).convert("RGB")
    w = min(width, im.width)
    if w != im.width:
        im = im.resize((w, round(w * im.height / im.width)), Image.LANCZOS)
    im.save(dst, "WEBP", quality=q, method=5)


def knob(src, dst, cx, cy, radius, out_radius=200, half=1.55):
    """Gros plan d'un potard, NORMALISE : le disque sort au meme rayon partout."""
    im = Image.open(src).convert("RGB")
    h = int(radius * half)
    c = im.crop((cx - h, cy - h, cx + h, cy + h))
    t = int(out_radius * half * 2)
    c.resize((t, t), Image.LANCZOS).save(dst, "WEBP", quality=90, method=5)


def shot(name):
    return os.path.join(SHOTS, name + ".png")


def main():
    os.makedirs(OUT, exist_ok=True)

    # « avant » = capture du commit precedent (potard bombe, grille unique).
    before = "/tmp/before_space.png"
    if not os.path.exists(before):
        with open(before, "wb") as f:
            subprocess.run(
                ["git", "show", "3acac9c:app/src/test/screenshots/fx_space.png"],
                cwd=REPO, stdout=f, check=True)

    after = shot("fx_space")
    webp(before, f"{OUT}/a_before.webp", 1500)
    webp(after, f"{OUT}/b_after.webp", 1500)
    webp(REF, f"{OUT}/ref_full.webp", 1500)
    for name in ("fx_space_shimmer", "fx_space_knob_low", "fx_space_dropdown",
                 "fx_rack", "fx_catalog"):
        if os.path.exists(shot(name)):
            webp(shot(name), f"{OUT}/after_{name}.webp", 1500)
            webp(shot(name), f"{OUT}/thumb_{name}.webp", 520, 70)

    # Gros plans normalises : le disque au meme rayon dans les trois images.
    # APP : gros potard centre (155, 223) unites, rayon du disque 98 unites.
    # APP : potard de cellule centre (358, 85) unites, rayon 37 unites.
    # REF : disque natif centre (985, 343), rayon 54 px.
    knob(after, f"{OUT}/k_app_big.webp", int(155 * SCALE), int(223 * SCALE), 98 * SCALE)
    knob(after, f"{OUT}/k_app_cell.webp", int(358 * SCALE), int(85 * SCALE), 37 * SCALE)
    knob(before, f"{OUT}/k_before_big.webp", int(147 * SCALE), int(158 * SCALE), 210)
    if os.path.exists(REF):
        knob(REF, f"{OUT}/k_ref.webp", 985, 343, 54)

    # Le titre, recadre et agrandi : c'est la mise en forme qu'on veut comparer.
    for tag, src in (("hall", shot("fx_space")), ("room", shot("fx_space_shimmer"))):
        if os.path.exists(src):
            im = Image.open(src).convert("RGB")
            s = SCALE
            c = im.crop((int(10 * s), int(60 * s), int(300 * s), int(120 * s)))
            c.resize((c.width, c.height), Image.LANCZOS).save(
                f"{OUT}/title_{tag}.webp", "WEBP", quality=90, method=5)

    with open(f"{OUT}/index.html", "w") as f:
        f.write(PAGE)
    print("page:", f"{OUT}/index.html")


PAGE = """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FX Design - reference et rendu cote a cote</title>
<style>
 :root{--bg:#0b0d12;--panel:#141822;--line:#2a3040;--ink:#e8ecf5;--dim:#8b93a7;--gold:#e0b45c}
 *{box-sizing:border-box}
 body{margin:0;background:var(--bg);color:var(--ink);
   font:15px/1.5 -apple-system,Segoe UI,Roboto,sans-serif}
 header{padding:34px 28px 18px;border-bottom:1px solid var(--line)}
 h1{margin:0 0 6px;font-size:26px;letter-spacing:.5px}
 h1 span{color:var(--gold)}
 .sub{color:var(--dim);max-width:900px}
 h2{margin:38px 28px 6px;font-size:18px;color:var(--gold);
   border-bottom:1px solid var(--line);padding-bottom:8px}
 h2 em{color:var(--dim);font-style:normal;font-size:13px;margin-left:10px}
 .lead{margin:8px 28px 0;color:var(--dim);font-size:14px;max-width:1000px}
 .pair{display:grid;gap:18px;padding:14px 28px;grid-template-columns:1fr 1fr}
 .pair.three{grid-template-columns:1fr 1fr 1fr}
 figure{margin:0;background:var(--panel);border:1px solid var(--line);
   border-radius:10px;overflow:hidden}
 figure img{display:block;width:100%}
 figcaption{padding:9px 12px;font-size:13px;color:var(--dim)}
 figcaption b{color:var(--ink)}
 .ref{outline:2px solid #3a5f8a}
 .ours{outline:2px solid #7a6a35}
 .note{margin:16px 28px;padding:12px 16px;background:#1a1408;
   border-left:3px solid var(--gold);border-radius:6px;color:#e6d7b0;font-size:14px}
 table{margin:14px 28px;border-collapse:collapse;font-size:14px}
 th,td{border:1px solid var(--line);padding:7px 12px;text-align:left}
 th{background:#181d29;color:var(--gold)}
 td.num{font-family:ui-monospace,monospace;color:#cfe0ff}
 a{color:var(--gold)}
</style></head><body>
<header>
  <h1>Fenetre FX - <span>reference et rendu cote a cote</span></h1>
  <p class="sub">Branche <code>design/fx-reverb</code>. Reference : capture
  <b>Valhalla Room</b> (<code>room_24.jpg</code>, 1608x918). Chaque section pose la
  reference a gauche, notre rendu a droite. Les gros plans de potard sont
  normalises au meme rayon de disque, sinon on ne compare que la taille.</p>
</header>

<h2>1. La fenetre entiere <em>la reference a gauche, notre rendu a droite</em></h2>
<p class="lead">Le rendu remplit toute la fenetre : plus de bande d'en-tete vide,
plus de reserve sous les controles. Le titre est ecrit dans le corps, les reglages
sont separes en cellules, et le gros potard porte le reglage principal.</p>
<div class="pair">
  <figure class="ref"><img src="ref_full.webp">
    <figcaption><b>REFERENCE - Valhalla Room</b><br>Fond sombre, sept disques
    plats, libelles au-dessus. Le mot « ValhallaRoom » est ecrit DANS le panneau,
    pas dans une bande a lui.</figcaption></figure>
  <figure class="ours"><img src="b_after.webp">
    <figcaption><b>NOTRE RENDU - Æther Space (Hall)</b><br>Colonne gauche : les
    commandes, le titre, le gros potard. Colonne droite : neuf cellules separees,
    un potard par cellule. Rien ne deborde, rien n'est vide.</figcaption></figure>
</div>

<h2>2. Avant / apres <em>ce qui a change</em></h2>
<div class="pair">
  <figure><img src="a_before.webp">
    <figcaption><b>AVANT</b> - bande d'en-tete de 46 unites, une seule grille a
    neuf potards de 46, potard central bombe (un dome), titre Cinzel.</figcaption></figure>
  <figure><img src="b_after.webp">
    <figcaption><b>APRES</b> - page pleine, cellules separees a 75 unites, gros
    potard plat de 197, titre Orbitron dont le STYLE suit le preset.</figcaption></figure>
</div>

<h2>3. Le potard <em>le point qui a demande plusieurs passes</em></h2>
<p class="lead">Trois chiffres commandent tout : le disque de la reference vaut
<b>121 de luminance</b> sur un panneau a <b>52</b> (rapport 2,3) ; il est
<b>uniforme</b> sur tout son rayon ; il n'a <b>pas de liseré</b> (transition
franche en un pixel). Notre premiere version avait un disque a 40 : il se
confondait avec le panneau.</p>
<div class="pair three">
  <figure class="ref"><img src="k_ref.webp">
    <figcaption><b>REFERENCE</b><br>Disque uni 121 / panneau 52. Aiguille sombre.
    Coin de valeur rouge au bord, rayon 28 a 57.</figcaption></figure>
  <figure><img src="k_before_big.webp">
    <figcaption><b>AVANT</b><br>Centre 164, bord 43 : un dome. Trois lobes, une
    lueur qui grandit. C'est l'invention qu'on a retiree.</figcaption></figure>
  <figure class="ours"><img src="k_app_big.webp">
    <figcaption><b>APRES</b><br>Disque plat, aiguille sombre, coin de valeur a la
    teinte du preset. Meme piece que les petits potards.</figcaption></figure>
</div>
<div class="pair">
  <figure class="ours"><img src="k_app_cell.webp">
    <figcaption><b>Potard de cellule</b><br>La meme piece a l'echelle 75 unites.
    Le disque, l'aiguille et le coin sont ceux du gros.</figcaption></figure>
</div>

<h2>4. Le titre change avec le preset <em>corps, interlettre, graisse</em></h2>
<p class="lead">Le mot ne change pas — c'est sa mise en forme qui suit le preset,
comme le wordmark d'un plugin. Hall s'ecrit large et gras, Shimmer large et tres
espace, Room serre et petit.</p>
<div class="pair">
  <figure class="ours"><img src="title_hall.webp">
    <figcaption><b>Preset Hall</b> - 30 sp, interlettre 1,4, gras</figcaption></figure>
  <figure class="ours"><img src="title_room.webp">
    <figcaption><b>Preset Shimmer</b> - 33 sp, interlettre 4,0, medium</figcaption></figure>
</div>

<h2>5. Les mesures</h2>
<table>
  <tr><th>Element</th><th>Reference Valhalla</th><th>Notre rendu</th><th>Verdict</th></tr>
  <tr><td>Disque / panneau</td><td class="num">121 / 52 = 2,3</td>
      <td class="num">110 / 26 = 4,2</td><td>Disque plus clair que la reference : lisible</td></tr>
  <tr><td>Liseré du disque</td><td>absent (transition 1 px)</td><td>absent</td><td>conforme</td></tr>
  <tr><td>Aiguille</td><td>sombre</td><td>sombre</td><td>conforme</td></tr>
  <tr><td>Indicateur de valeur</td><td>coin plein au bord, ~25 deg</td>
      <td>coin plein au bord, 25 deg</td><td>conforme</td></tr>
  <tr><td>Course</td><td>270 deg (135 a 405)</td><td>270 deg</td><td>conforme</td></tr>
  <tr><td>Ø potard de cellule</td><td class="num">108 px / 1608 = 6,7 %</td>
      <td class="num">75 / 891 = 8,4 %</td><td>relativement plus gros, voulu</td></tr>
  <tr><td>Bande d'en-tete</td><td>aucune (le mot est dans le panneau)</td>
      <td>aucune</td><td>conforme</td></tr>
  <tr><td>Reglages groupes</td><td>cellules separees</td><td>cellules separees</td>
      <td>conforme</td></tr>
</table>

<h2>6. Etats du meme ecran</h2>
<div class="pair three">
  <figure><a href="after_fx_space_shimmer.webp" target="_blank"><img loading="lazy" src="thumb_fx_space_shimmer.webp"></a><figcaption><b>Preset Shimmer</b> - fond bicolore, titre aere</figcaption></figure>
  <figure><a href="after_fx_space_knob_low.webp" target="_blank"><img loading="lazy" src="thumb_fx_space_knob_low.webp"></a><figcaption><b>Reglage principal bas</b> - le coin recule</figcaption></figure>
  <figure><a href="after_fx_space_dropdown.webp" target="_blank"><img loading="lazy" src="thumb_fx_space_dropdown.webp"></a><figcaption><b>Liste de presets ouverte</b></figcaption></figure>
  <figure><a href="after_fx_rack.webp" target="_blank"><img loading="lazy" src="thumb_fx_rack.webp"></a><figcaption><b>Le rack</b> - cartes d'effets</figcaption></figure>
  <figure><a href="after_fx_catalog.webp" target="_blank"><img loading="lazy" src="thumb_fx_catalog.webp"></a><figcaption><b>Catalogue</b> - ajout d'effet</figcaption></figure>
</div>
</body></html>
"""

if __name__ == "__main__":
    main()
