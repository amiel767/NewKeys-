"""Split the piano+strings preview into two standalone HTML files.

Both get the same animation softening:
  - the water field propagates less and damps faster (subtler ripples),
    and the shader displaces the image half as much;
  - falling particles spawn half as often, on a heavy-tailed (non-linear)
    interval instead of a uniform Poisson rate, and fall with a skewed,
    accelerating motion.

The piano file keeps the photographic scene with the 9 piano images.
The strings file drops the procedural sky and the floating instruments and
shows its 6 images directly, in the same composition as the piano.
"""
import base64
import os
import re
import sys

SRC = '/workspace/project/NewKeys-/app/assets/Æther Pianos & Strings · preview.html'
KIT = '/workspace/dl/uikit'
OUT = '/workspace/dl'

# --- strings preset -> image asset -------------------------------------------
STR_IMG = {
    'Solo Violin': 'strings_solo',
    'Solo Viola': 'strings_solo',
    'Solo Cello': 'strings_cello',
    'Solo Bass': 'strings_cello',
    'Section': 'strings_sec',
    'Orchestra': 'strings_orch',
    'Pizzicato': 'strings_pizz',
    'Pad': 'strings_pad',
}
PIANO_IMG = ['felt', 'studio', 'bright', 'mellow', 'dark', 'upright', 'lady', 'honky', 'electric']


def sub(h, old, new, n=1):
    """Replace and assert the anchor was found, so a silent no-op is impossible."""
    c = h.count(old)
    assert c == n, f'ancre absente ou ambigue ({c}x): {old[:90]}'
    return h.replace(old, new)


def soft_animations(h):
    # --- water: less propagation, faster damping -----------------------------
    h = sub(h, 'const cx=.30,cy=.13,dm=.9925;', 'const cx=.16,cy=.07,dm=.9880;')

    # --- shader: half the displacement, softer specular ----------------------
    h = sub(h,
            'vec2 d=g*.07*m+vec2(sin(uA.y*95.+t*1.2),cos(uA.x*70.+t))*.0007*m;',
            'vec2 d=g*.035*m+vec2(sin(uA.y*95.+t*1.2),cos(uA.x*70.+t))*.00035*m;')
    h = sub(h, 'return c+vec3(1.,.96,.88)*sp*.5;}', 'return c+vec3(1.,.96,.88)*sp*.26;}')
    h = sub(h,
            'vec2 d=g*.06+vec2(sin(uv.y*120.+t*1.3),cos(uv.x*80.+t*.8))*.0012*(.3+yy);',
            'vec2 d=g*.03+vec2(sin(uv.y*120.+t*1.3),cos(uv.x*80.+t*.8))*.0006*(.3+yy);')

    # --- drop amplitude halved (ambient + key press) -------------------------
    h = sub(h,
            'function ambientDrops(dt){nextDrop-=dt;if(nextDrop>0)return;'
            'const ux=.28+Math.random()*.44,uy=.72+Math.random()*.2;'
            'drop(ux,uy,.55+Math.random()*.9);'
            'if(Math.random()<.3)setTimeout(()=>drop(clamp(ux+(Math.random()-.5)*.08,.2,.8),'
            'clamp(uy+(Math.random()-.5)*.06,.68,.95),.4+Math.random()*.5),350+Math.random()*600);'
            'nextDrop=Math.exp(Math.log(4.2)+randn()*.75)}',
            'function ambientDrops(dt){nextDrop-=dt;if(nextDrop>0)return;'
            'const ux=.28+Math.random()*.44,uy=.72+Math.random()*.2;'
            'drop(ux,uy,.26+Math.random()*.44);'
            'if(Math.random()<.22)setTimeout(()=>drop(clamp(ux+(Math.random()-.5)*.08,.2,.8),'
            'clamp(uy+(Math.random()-.5)*.06,.68,.95),.18+Math.random()*.24),350+Math.random()*600);'
            'nextDrop=Math.exp(Math.log(5.6)+randn()*.9)}')
    h = sub(h, 'if(gl)drop(ux,.8,.5+vel*.9);', 'if(gl)drop(ux,.8,.24+vel*.44);')

    # --- particles: skewed fall speed, gentle gravity ------------------------
    h = sub(h,
            "vy:burst?-(30+Math.random()*50)*DPR:(8+Math.random()*16)*DPR*"
            "(ty=='dust'||ty=='star'||ty=='neon'?.15:1)",
            "vy:burst?-(30+Math.random()*50)*DPR:(5+Math.pow(Math.random(),1.8)*26)*DPR*"
            "(ty=='dust'||ty=='star'||ty=='neon'?.15:1)")
    h = sub(h, 'if(p.vy<0)p.vy+=40*DPR*dt;', 'p.vy+=(p.vy<0?40:5)*DPR*dt;')

    # --- spawn: half rate, heavy-tailed interval (non-linear, truly random) --
    h = sub(h,
            " if(Math.random()<dt*({petal:3.2,leaf:2.4,spark:4,dust:5,star:3,neon:4}[pType]||3))spawnP(1);drawP(dt,Tm)}",
            " ambientParticles(dt);drawP(dt,Tm)}")
    h = sub(h,
            'let nextDrop=2;const randn=',
            'const PRATE={petal:1.6,leaf:1.2,spark:2,dust:2.5,star:1.5,neon:2};let nextP=0;\n'
            '/* .25+1.75*u^2.4 has mean .7647, so divide it out: the mean interval\n'
            '   stays 1/PRATE (half the old rate) and only the spread is skewed. */\n'
            'const PTAIL=.76471;\n'
            'function ambientParticles(dt){nextP-=dt;if(nextP>0)return;spawnP(1);'
            'const u=Math.random();'
            'nextP=(.25+1.75*Math.pow(u,2.4))/PTAIL/(PRATE[pType]||1.5)}\n'
            'let nextDrop=2;const randn=')
    return h


def set_img(h, pairs):
    """Replace the IMG map with the given key -> data-uri pairs."""
    body = ', '.join(f'"{k}": "{v}"' for k, v in pairs)
    return re.sub(r'const IMG=\{.*?\};', 'const IMG={' + body + '};', h, count=1, flags=re.S)


def avif_b64(asset):
    p = f'{KIT}/{asset}_750.avif'
    assert os.path.exists(p), f'asset manquant: {p}'
    return 'data:image/avif;base64,' + base64.b64encode(open(p, 'rb').read()).decode()


def build_piano(h):
    h = set_img(h, [(k, avif_b64(f'piano_{k}')) for k in PIANO_IMG])
    h = sub(h, '<span data-m="piano" class="on">PIANO</span><span data-m="strings">STRINGS</span>',
            '<span data-m="piano" class="on">PIANO</span>')
    h = sub(h, '<title>Æther Pianos & Strings · preview</title>',
            '<title>Æther Pianos · preview</title>')
    return h


def build_strings(h):
    pairs = [(a, avif_b64(a)) for a in sorted(set(STR_IMG.values()))]
    h = set_img(h, pairs)

    # give each strings preset the id of its image
    for name, asset in STR_IMG.items():
        h = sub(h, "{n:'" + name + "',tag:", "{id:'" + asset + "',n:'" + name + "',tag:")

    # the violin SVG family is only used by the procedural scene, which this
    # file never shows; drop it so boot does not rasterize four unused images
    i = h.find('/* ---------- violin family')
    j = h.find('/* ---------- GL scene')
    assert i > 0 and j > i, 'bloc SVG violon introuvable'
    h = h[:i] + h[j:]

    # show the photograph, never the procedural sky / floating instruments
    h = sub(h, "function setMode(m){stopAll();S.mode=m;modeT=m=='strings'?1:0;",
            "function setMode(m){stopAll();S.mode=m;modeT=0;")
    h = sub(h, 'if(modeS>.003){drawInst(Tm)}', '')
    h = sub(h,
            'function selStr(i){stopAll();S.sm=i;S.X=SM[i].d.slice();applyModel(SM[i]);'
            'S.base=SM[i].base;PT=SM[i].pal;buildList();buildCtl();buildKB();applyAudio()}',
            'function selStr(i){stopAll();S.sm=i;S.X=SM[i].d.slice();applyModel(SM[i]);'
            'S.base=SM[i].base;PT=SM[i].pal;setBg(SM[i].id);buildList();buildCtl();buildKB();applyAudio()}')

    # boot straight into the strings photograph
    h = sub(h, "loadImgs(()=>{setBg(PM[0].id);buildKB();setMode('piano');",
            "loadImgs(()=>{setBg(SM[0].id);buildKB();setMode('strings');")
    h = sub(h, "['violin','viola','cello','bass'].forEach(k=>raster(k));\n", '')
    h = sub(h, 'gl:()=>!!gl,RAS,instSVG}', 'gl:()=>!!gl}')

    h = sub(h, '<span data-m="piano" class="on">PIANO</span><span data-m="strings">STRINGS</span>',
            '<span data-m="strings" class="on">STRINGS</span>')
    h = sub(h, '<title>Æther Pianos & Strings · preview</title>',
            '<title>Æther Strings · preview</title>')
    return h


if __name__ == '__main__':
    src = open(SRC, encoding='utf-8').read()
    for kind, fn in (('piano', build_piano), ('strings', build_strings)):
        h = fn(soft_animations(src))
        out = f'{OUT}/{kind}.html'
        open(out, 'w', encoding='utf-8').write(h)
        print(f'  {out:28} {os.path.getsize(out) / 1024:7.0f} Ko')
