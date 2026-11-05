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
</style>
</head>
<body>
<div class="card">
  <h1>SoundStage Mixer - <b>APK arm64</b></h1>
  <p class="sub">Build debug, une seule ABI (arm64-v8a), leger.</p>

  <a class="dl" href="/aether.apk" download>Telecharger l'APK ({human(size)})</a>

  <a class="dl" href="/demos/" style="margin-top:9px;background:linear-gradient(180deg,#243040,#161d26);border-color:#3b4a5e;color:#9fc0e8">Ecouter les demos : Isn't She Lovely</a>

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
