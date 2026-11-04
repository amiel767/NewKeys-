#!/usr/bin/env python3
"""Ecrit la page d'ecoute des demos (theme sombre/or, un lecteur par instrument).

    python3 tools/aetherfx/demos_page.py /workspace/dl/pub/demos

La page ne fait que lister les MP3 deja presents : l'encodage est fait par
encode_demos.py (48 kbps, sax en mono), les rendus par demo_pianos_all.cpp /
demo_sax.cpp.  Regenerer les trois a chaque changement de moteur ou de preset,
sinon la page rejoue un instrument perime.
"""
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "/workspace/dl/pub/demos"

PIANOS = [
    ("Felt", "piano_00_Felt.mp3", "piano.bin", "queue, feutre : peu d'attaque, tout en queue"),
    ("Studio", "piano_01_Studio.mp3", "piano.bin", "queue, le neutre de reference"),
    ("Bright", "piano_02_Bright.mp3", "piano.bin", "clavier ouvert, attaques nettes"),
    ("Mellow", "piano_03_Mellow.mp3", "piano.bin", "doux, fondu"),
    ("Dark", "piano_04_Dark.mp3", "piano.bin", "sombre, pour la main gauche"),
    ("Cottage (droit)", "piano_05_Cottage.mp3", "upright.bin", "droit, corps boise"),
    ("Parlor (droit)", "piano_06_Parlor.mp3", "upright.bin", "droit clair, medium"),
    ("Saloon", "piano_07_Saloon.mp3", "upright.bin", "bastringue, mordant"),
    ("Tine Keys", "piano_08_Tine_Keys.mp3", "ep_tine.bin", "Wurlitzer, tine"),
    ("Ballad", "piano_09_Ballad.mp3", "ep_ballad.bin", "electrique doux"),
    ("DX Bell", "piano_10_DX_Bell.mp3", "ep_dx.bin", "DX7, cloche"),
    ("E.Piano", "piano_11_EPiano.mp3", "ep_piano.bin", "electrique, tine"),
]
SAXS = [
    ("Alto (Bulldog)", "sax_bulldog.mp3", "saxpack_bulldog.bin", "l'alto unique : une anche, profil alto"),
    ("Soprano", "sax_soprano.mp3", "saxpack_sop.bin", "MTG CC-BY"),
    ("Alto (MTG)", "sax_alto.mp3", "saxpack_alt.bin", "MTG CC-BY, comparaison"),
    ("Tenor", "sax_tenor.mp3", "saxpack_ten.bin", "MTG CC-BY"),
    ("Baryton", "sax_baryton.mp3", "saxpack_bar.bin", "MTG CC-BY"),
]


def card(name, f, bank, note):
    return f'''    <div class="card">
      <div class="top"><span class="name">{name}</span><span class="meta">48k</span></div>
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
    rows = [card(*p) for p in PIANOS]
    srows = [card(*s) for s in SAXS]
    page = f'''<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AEther - demos legeres (pianos, EP, sax)</title>
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
  .sub{{color:var(--dim);margin:0 0 22px;font-size:13.5px}}
  h2{{font-size:13px;text-transform:uppercase;letter-spacing:1.4px;color:var(--gold);
     margin:30px 0 12px;font-weight:700}}
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
</style>
</head>
<body>
<div class="wrap">
  <h1>AEther - <b>demos legeres</b></h1>
  <p class="sub">Phrases reelles jouees par le <b>moteur de l'app</b> (rendu hors Android), un fichier par instrument.
     Presets lus depuis <code>AePresets.kt</code>, banques du depot. Encodage 48 kbps - ~950 Ko au total.</p>

  <p class="sub" style="margin:0 0 22px"><a class="dl" href="/aether.apk" download>Telecharger l'APK (57,3 Mo)</a>
     &nbsp; build du jour : piano (velocite T2, saturation T1), sax (clic), RAM (T5).</p>

  <h2>Pianos &amp; pianos electriques <span class="tag">12 - queue + droit + 4 EP</span></h2>
  <div class="grid">
{chr(10).join(rows)}
  </div>

  <h2>Saxophones <span class="tag">5 - Bulldog alto + MTG CC-BY</span></h2>
  <div class="grid">
{chr(10).join(srows)}
  </div>

  <div class="note">
    <b>Bulldog Sax 2 :</b> banque de l'<b>alto</b> (<code>saxpack_bulldog.bin</code>, 36 notes).
    Ecoute « Alto (Bulldog) » et compare avec « Alto (MTG) ». Soprano, tenor et baryton
    gardent les banques <b>MTG Solo Sax (CC-BY)</b>.
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
    if(audio.duration)fill.style.right=(100-audio.currentTime/audio.duration*100)+'%';
    const s=Math.floor(audio.currentTime);
    t.textContent=Math.floor(s/60)+':'+String(s%60).padStart(2,'0');
  }};
  track.onclick=(e)=>{{ if(audio.duration){{const r=track.getBoundingClientRect();
    audio.currentTime=(e.clientX-r.left)/r.width*audio.duration;}}}};
}});
</script>
</body></html>
'''
    path = os.path.join(OUT, "index.html")
    with open(path, "w") as f:
        f.write(page)
    print(f"page: {path}  ({len(PIANOS)} pianos + {len(SAXS)} sax)")


if __name__ == "__main__":
    main()
