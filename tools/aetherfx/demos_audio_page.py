#!/usr/bin/env python3
"""Ecrit la page d'ecoute des demos (couplet + plage/vitesse/tenue).

    python3 tools/aetherfx/demos_audio_page.py /workspace/dl/pub/demos

La page ne fait que lister les MP3 deja presents : les rendus viennent de
demo_isnt_she_lovely.cpp / demo_range.cpp, l'encodage de encode_isl.py /
encode_range.py.  Regenerer les trois a chaque changement de moteur, sinon la
page rejoue un instrument perime.
"""
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "/workspace/dl/pub/demos"

# (nom affiche, banque, description)
MODELS = [
    ("Felt", "piano.bin", "queue, feutre : peu d'attaque"),
    ("Studio", "piano.bin", "queue, le neutre de reference"),
    ("Bright", "piano.bin", "clavier ouvert, attaques nettes"),
    ("Mellow", "piano.bin", "doux, fondu"),
    ("Dark", "piano.bin", "sombre, pour la main gauche"),
    ("Cottage (droit)", "upright.bin", "droit, corps boise"),
    ("Parlor (droit)", "upright.bin", "droit clair, medium"),
    ("Saloon", "upright.bin", "bastringue, mordant"),
    ("Tine Keys", "ep_tine.bin", "Wurlitzer, tine"),
    ("Ballad", "ep_ballad.bin", "electrique doux"),
    ("DX Bell", "ep_dx.bin", "DX7, cloche"),
    ("E.Piano", "ep_piano.bin", "electrique, tine"),
]
SLUGS = ["Felt", "Studio", "Bright", "Mellow", "Dark", "Cottage",
         "Parlor", "Saloon", "Tine_Keys", "Ballad", "DX_Bell", "E_Piano"]


def card(name, f, bank, note, kbps):
    return f'''    <div class="card">
      <div class="top"><span class="name">{name}</span><span class="meta">{kbps}</span></div>
      <div class="bank">{bank}</div>
      <p class="note-inline">{note}</p>
      <div class="bar">
        <button class="play" title="lecture">&#9654;</button>
        <div class="track"><div class="fill"></div></div>
        <div class="t">0:00</div>
      </div>
      <audio preload="none" src="{f}"></audio>
    </div>'''


def main():
    isl = [card(n, f"isl_{i:02d}_{s}.mp3", b, d, "64k")
           for i, ((n, b, d), s) in enumerate(zip(MODELS, SLUGS))]
    rng = [card(n, f"rng_{i:02d}_{s}.mp3", b, d, "48k")
           for i, ((n, b, d), s) in enumerate(zip(MODELS, SLUGS))]

    page = f'''<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AEther - demos (couplet + plage C0-C7)</title>
<style>
  :root{{--bg:#0d0f13;--panel:#161a21;--panel2:#1e232c;--line:#2b313c;
        --ink:#e8ecf2;--dim:#8b95a5;--gold:#f2b84a;--gold2:#c8892a;}}
  *{{box-sizing:border-box}}
  body{{margin:0;background:radial-gradient(1200px 700px at 20% -10%,#1a1f28,#0d0f13 60%);
       color:var(--ink);font:15px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
       padding:28px 18px 60px}}
  .wrap{{max-width:920px;margin:0 auto}}
  h1{{font-size:22px;margin:0 0 4px;letter-spacing:.3px}}
  h1 b{{color:var(--gold)}}
  .sub{{color:var(--dim);margin:0 0 18px;font-size:13.5px}}
  h2{{font-size:13px;text-transform:uppercase;letter-spacing:1.4px;color:var(--gold);
     margin:32px 0 12px;font-weight:700}}
  .grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:10px}}
  .card{{background:linear-gradient(180deg,var(--panel2),var(--panel));border:1px solid var(--line);
        border-radius:12px;padding:12px 13px;display:flex;flex-direction:column;gap:9px}}
  .card .top{{display:flex;justify-content:space-between;align-items:baseline;gap:8px}}
  .name{{font-weight:650;font-size:14.5px}}
  .meta{{color:var(--dim);font-size:11.5px;white-space:nowrap}}
  .bank{{color:var(--gold2);font-size:11px;letter-spacing:.3px}}
  .note-inline{{margin:0;color:var(--dim);font-size:12px}}
  audio{{display:none}}
  .bar{{display:flex;align-items:center;gap:9px}}
  .play{{flex:0 0 34px;height:34px;border-radius:50%;border:1px solid var(--gold2);
        background:radial-gradient(circle at 35% 30%,#3a3020,#241d12);color:var(--gold);
        font-size:13px;cursor:pointer;display:flex;align-items:center;justify-content:center}}
  .play:hover{{background:radial-gradient(circle at 35% 30%,#4a3d28,#2e2416)}}
  .track{{flex:1;height:6px;border-radius:3px;background:#0a0c10;overflow:hidden;position:relative;cursor:pointer}}
  .fill{{position:absolute;inset:0 100% 0 0;background:linear-gradient(90deg,var(--gold2),var(--gold))}}
  .t{{flex:0 0 40px;text-align:right;color:var(--dim);font-size:11px;font-variant-numeric:tabular-nums}}
  .note{{background:#141821;border:1px solid var(--line);border-left:3px solid var(--gold2);
        border-radius:8px;padding:11px 13px;color:var(--dim);font-size:12.5px;margin:18px 0}}
  .note b{{color:var(--ink)}}
  .tag{{display:inline-block;background:#20262f;border:1px solid var(--line);border-radius:6px;
       padding:1px 7px;font-size:11px;color:var(--dim);margin-right:5px}}
  a.dl{{display:inline-block;background:linear-gradient(180deg,#3a3020,#241d12);
       border:1px solid var(--gold2);color:var(--gold);text-decoration:none;
       padding:8px 14px;border-radius:9px;font-weight:600;font-size:13.5px}}
  a.dl:hover{{background:linear-gradient(180deg,#4a3d28,#2e2416)}}
  code{{background:#0a0c10;border:1px solid var(--line);border-radius:5px;padding:1px 5px;
       font-size:12px;color:var(--ink)}}
</style>
</head>
<body>
<div class="wrap">
  <h1>AEther - <b>demos d'ecoute</b></h1>
  <p class="sub">Joue par le <b>moteur de l'app</b> (rendu hors Android), un fichier par instrument.
     Presets lus depuis <code>AePianoUi.kt</code>, banques du depot. Debit bas volontaire (48-64 kbps).</p>

  <p class="sub" style="margin:0 0 18px">
     <a class="dl" href="/aether.apk" download>Telecharger l'APK</a>
     &nbsp; meme moteur, correctif de boucle (fondu de raccord 200 ms) inclus.</p>

  <div class="note">
    <b>1. Le couplet de « Isn't She Lovely »</b> : mi majeur, 12/8. Grille
    <code>C#m7 | F#9 | B9sus4 | E</code>, basse tenue sous la ligne chantee
    (reconstituee en pentatonique de mi). Ecouter les <b>notes tenues</b> : une note
    saine decroit regulierement, une enveloppe qui « rebondit » par vagues est le
    defaut de boucle corrige ici.
    <br><br>
    <b>2. La plage C0 a C7</b> : chaque octave jouee <b>piano (vel 42) puis forte
    (vel 118)</b>, chaque note tenue 2,8 s. Mesure sur les 12 modeles : l'ecart
    pp-&gt;ff va de <b>+9,9 a +23,2 dB</b> (moyenne 17,9 dB), aucune note muette.
    C'est le test que la velocite change bien le timbre du grave a l'aigu, et que la
    tenue longue ne « bat » nulle part.
  </div>

  <h2>1. Couplet - Isn't She Lovely <span class="tag">12 modeles</span></h2>
  <div class="grid">
{chr(10).join(isl)}
  </div>

  <h2>2. Plage C0-C7, velocite pp/ff, tenue longue <span class="tag">12 modeles</span></h2>
  <div class="grid">
{chr(10).join(rng)}
  </div>
</div>
<script>
const cards=[...document.querySelectorAll('.card')];
let current=null;
cards.forEach(c=>{{
  const audio=c.querySelector('audio'),btn=c.querySelector('.play'),
        fill=c.querySelector('.fill'),t=c.querySelector('.t'),track=c.querySelector('.track');
  btn.onclick=()=>{{
    if(current&&current!==audio)current.pause();
    if(audio.paused){{audio.play();current=audio;}}else audio.pause();
  }};
  audio.onplay=()=>{{btn.innerHTML='&#10074;&#10074;';}};
  audio.onpause=()=>{{btn.innerHTML='&#9654;';}};
  audio.onended=()=>{{btn.innerHTML='&#9654;';fill.style.right='100%';}};
  audio.ontimeupdate=()=>{{
    const p=audio.duration?audio.currentTime/audio.duration*100:0;
    fill.style.right=(100-p)+'%';
    const s=Math.floor(audio.currentTime);
    t.textContent=Math.floor(s/60)+':'+String(s%60).padStart(2,'0');
  }};
  track.onclick=e=>{{
    const r=track.getBoundingClientRect();
    audio.currentTime=Math.max(0,Math.min(1,(e.clientX-r.left)/r.width))*audio.duration;
  }};
}});
</script>
</body></html>
'''
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "index.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(page)
    print(f"page   : {path}")
    print(f"mp3    : {len(isl) + len(rng)} fichiers attendus dans {OUT}")


if __name__ == "__main__":
    main()
