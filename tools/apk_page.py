#!/usr/bin/env python3
"""Page de livraison de l'APK, avec bouton de telechargement direct.

Copie l'APK debug arm64 dans le dossier servi et ecrit une page qui pointe
dessus, avec sa taille, son empreinte et la date du build. Le theme reprend
celui de `demos_page.py` / `shots_page.py` (sombre/or).

    python3 tools/apk_page.py            # build courant -> /workspace/dl/pub
    python3 tools/apk_page.py --check    # n'ecrit rien, dit ce qui est publie
"""
from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import sys
from datetime import datetime, timezone

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUB = "/workspace/dl/pub"
APK_SRC = os.path.join(REPO, "app/build/outputs/apk/debug/app-debug.apk")
APK_DST = os.path.join(PUB, "aether.apk")

# Captures des changements de cette build, montrees sous le bouton. Le fichier
# vient de `tools/shots_page.py` (WebP compresse) ; la legende dit ce qu'on
# regarde, pas ce que le code a fait.
CHANGES = [
    ("setlist_0_track", "Setlist neuf : aucune piste, seulement le « + »",
     "Un setlist cree ouvre sur zero fader. Le « + » cree la premiere piste puis "
     "demande sa source sonore."),
    ("setlist_8_tracks", "Le « + » reconstruit bien les huit pistes",
     "La creation de pistes n'a pas ete cassee : le bouton remonte a huit."),
    ("sound_source_chooser", "Choix de la source sonore (Æther ou SF2)",
     "Demande une seule fois par piste, au premier tap. Ensuite la plaque de nom "
     "ouvre directement la fenetre choisie ; l'appui long rouvre le choix."),
    ("aether_window_save", "Bouton « Save » lisible (Æther Piano)",
     "L'icone disquette se lisait comme une decoration. Le bouton est desormais "
     "une pastille libellee, montree des qu'un reglage a bouge."),
    ("aether_window_instrument", "Fenetre Æther Piano (interface integree)",
     "La meme fenetre heberge la liste et l'interface de l'instrument ; elle "
     "s'agrandit au lieu d'ouvrir un second calque."),
]


def human(n: int) -> str:
    return f"{n / 1_048_576:.1f} Mo"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    if not os.path.exists(APK_SRC):
        print(f"APK absent : {APK_SRC}\n  -> lancer : ./gradlew :app:assembleDebug")
        return 1
    size = os.path.getsize(APK_SRC)
    sha = hashlib.sha256(open(APK_SRC, "rb").read()).hexdigest()[:16]
    stamp = datetime.fromtimestamp(os.path.getmtime(APK_SRC), timezone.utc)
    when = stamp.strftime("%Y-%m-%d %H:%M UTC")

    if args.check:
        pub = os.path.getsize(APK_DST) if os.path.exists(APK_DST) else 0
        print(f"source : {human(size)}  {when}  sha256:{sha}")
        print(f"publie : {human(pub) if pub else 'ABSENT'}")
        return 0

    os.makedirs(PUB, exist_ok=True)
    shutil.copy2(APK_SRC, APK_DST)

    # Les vignettes WebP sont ecrites par `tools/shots_page.py` dans `pub/shots/`.
    # Si elles manquent, la page se construit quand meme, sans les images.
    shots_dir = os.path.join(PUB, "shots")
    blocks = []
    for name, title, caption in CHANGES:
        thumb = os.path.join(shots_dir, f"{name}.thumb.webp")
        full = os.path.join(shots_dir, f"{name}.webp")
        if not os.path.exists(thumb):
            continue
        blocks.append(
            f'    <div class="shot">'
            f'<a href="/shots/{name}.webp" target="_blank">'
            f'<img src="/shots/{name}.thumb.webp" alt="{title}" loading="lazy"></a>'
            f'<div class="cap"><b>{title}</b><span>{caption}</span></div></div>'
        )
    gallery = "\n".join(blocks) if blocks else (
        '    <div class="note">Captures non generees : lancer '
        '<code>tools/shots_page.py</code>.</div>'
    )

    page = f'''<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SoundStage Mixer - APK arm64</title>
<style>
  :root{{--bg:#0d0f13;--panel:#161a21;--panel2:#1e232c;--line:#2b313c;
        --ink:#e8ecf2;--dim:#8b95a5;--gold:#f2b84a;--gold2:#c8892a;}}
  *{{box-sizing:border-box}}
  body{{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
       background:radial-gradient(1200px 700px at 20% -10%,#1a1f28,#0d0f13 60%);
       color:var(--ink);font:15px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
       padding:28px 18px}}
  .card{{max-width:560px;width:100%;background:linear-gradient(180deg,var(--panel2),var(--panel));
        border:1px solid var(--line);border-radius:16px;padding:26px 24px;text-align:center}}
  h1{{font-size:21px;margin:0 0 4px;letter-spacing:.3px}}
  h1 b{{color:var(--gold)}}
  .sub{{color:var(--dim);font-size:13px;margin:0 0 22px}}
  a.dl{{display:block;background:linear-gradient(180deg,#3a3020,#241d12);
       border:1px solid var(--gold2);color:var(--gold);text-decoration:none;
       padding:15px 18px;border-radius:12px;font-weight:700;font-size:16.5px;letter-spacing:.3px}}
  a.dl:hover{{background:linear-gradient(180deg,#4a3d28,#2e2416)}}
  .meta{{color:var(--dim);font-size:12px;margin-top:14px;line-height:1.7}}
  .meta code{{background:#0a0c10;border:1px solid var(--line);border-radius:5px;padding:1px 6px;
             font-size:11.5px;color:var(--ink)}}
  .tag{{display:inline-block;background:#20262f;border:1px solid var(--line);border-radius:6px;
       padding:2px 8px;font-size:11px;color:var(--dim);margin:0 3px 6px}}
  .note{{background:#141821;border:1px solid var(--line);border-left:3px solid var(--gold2);
        border-radius:8px;padding:11px 13px;color:var(--dim);font-size:12.5px;margin:18px 0 0;text-align:left}}
  .note b{{color:var(--ink)}}
  h2{{font-size:15px;margin:26px 0 4px;color:var(--gold);letter-spacing:.4px;
      text-transform:uppercase;font-size:12.5px}}
  .shots{{display:grid;grid-template-columns:1fr;gap:16px;margin-top:14px}}
  .shot{{background:#10141b;border:1px solid var(--line);border-radius:12px;
        overflow:hidden;text-align:left}}
  .shot img{{display:block;width:100%;height:auto;background:#0a0c10}}
  .shot .cap{{padding:10px 13px 12px}}
  .shot .cap b{{display:block;color:var(--ink);font-size:13px;margin-bottom:3px}}
  .shot .cap span{{color:var(--dim);font-size:12px;line-height:1.55}}
</style>
</head>
<body>
<div class="card">
  <h1>SoundStage Mixer - <b>APK arm64</b></h1>
  <p class="sub">Build debug, une seule ABI (arm64-v8a), leger.</p>

  <a class="dl" href="/aether.apk" download>Telecharger l'APK ({human(size)})</a>

  <div class="meta">
    <span class="tag">arm64-v8a</span><span class="tag">debug</span><span class="tag">{human(size)}</span><br>
    build : {when}<br>
    sha256 : <code>{sha}</code>
  </div>

  <div class="note">
    <b>Installation</b> : copier sur l'appareil, ouvrir le fichier, autoriser
    l'installation depuis cette source si Android le demande. Build de test
    (signature debug), a remplacer par une release signee pour une vraie
    distribution.
  </div>

  <h2>Ce qui a change</h2>
  <div class="shots">
{gallery}
  </div>
</div>
</body></html>
'''
    with open(os.path.join(PUB, "index.html"), "w") as f:
        f.write(page)
    print(f"publie : {APK_DST} ({human(size)})  sha256:{sha}")
    print(f"page   : {PUB}/index.html")
    return 0


if __name__ == "__main__":
    sys.exit(main())
