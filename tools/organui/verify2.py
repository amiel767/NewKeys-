import re, sys
sys.path.insert(0,'.')
from vd2svg import svg_inline
from playwright.sync_api import sync_playwright
html = open('/mnt/user-data/uploads/deepseek_html_20261107_a83460.html').read()
css = re.search(r'<style>(.*?)</style>', html, re.S).group(1)
js = html[html.index('const PAL = {'):html.index('const THUMB_H')]
D='../res/drawable/'
def img(name, pad, art_w, art_h, extra=''):
    s=svg_inline(D+name+'.xml',1)
    return f'<div style="position:absolute;left:{-pad}px;top:{-pad}px">{s}</div>'
def box(w,h,inner,extra=''): return f'<div style="position:relative;width:{w}px;height:{h}px;{extra}">{inner}</div>'
SC=4
knob_o=lambda S:f'<div class="knob-wrapper" style="--knob-size:{S}px"><div class="knob-base"></div><div class="knob-groove"></div><div class="knob-cap"><div class="knob-specular"></div></div></div>'
knob_m=lambda S: box(S,S,img(f'organ_knob_body_{S}',8,S,S)+img(f'organ_knob_cap_{S}',8,S,S))
rot_o='<div style="width:46px;height:46px;position:relative"><div class="rotary-switch" style="--knob-rotation:-135deg;position:absolute;inset:0"><div class="rotary-indicator"></div></div></div>'
rot_m=lambda: box(46,46,img('organ_rotary_switch',6,46,46),'transform:rotate(-135deg)')
tog_o='<div class="toggle-switch" data-pos="Pipe"><div class="toggle-handle"></div></div>'
tog_m=box(44,18,img('organ_toggle_track',4,44,18)+f'<div style="position:absolute;left:2px;top:2px;width:14px;height:14px;transform:translateX(13px)">{img("organ_toggle_handle",4,14,14)}</div>')
fv_o='<div style="position:relative;width:20px;height:26px"><div class="fader-thumb" style="left:0;transform:none;bottom:0"><div class="fader-led-bar"></div></div></div>'
fv_m=box(20,26,img('organ_fader_thumb_v',12,20,26))
fh_o='<div style="position:relative;width:22px;height:18px"><div class="h-fader-thumb" style="left:0;transform:none;top:0"><div class="h-fader-led-bar"></div></div></div>'
fh_m=box(22,18,img('organ_fader_thumb_h',12,22,18))
ns={}
exec(js.replace('const ','var ').replace('function rng','function rng'), {'document':None}, ns) if False else None
page=f'''<!doctype html><html><head><meta charset="utf-8"><style>{css}
body{{overflow:visible!important;width:auto!important;height:auto!important;background:#1d2332!important;display:block!important;touch-action:auto}}
.row{{display:flex;gap:70px;padding:30px 40px;align-items:flex-start;height:150px}}
.z{{transform:scale({SC});transform-origin:left top;width:60px;height:46px}}
.lab{{color:#fff;font:13px monospace;padding:4px 40px}}
</style></head><body>
<div class="lab">ORIGINAL</div><div class="row">{''.join(f'<div class="z">{x}</div>' for x in (knob_o(52),rot_o,tog_o,fv_o,fh_o))}</div>
<div class="lab" style="margin-top:80px">MON XML</div><div class="row">{''.join(f'<div class="z">{x}</div>' for x in (knob_m(52),rot_m(),tog_m,fv_m,fh_m))}</div>
</body></html>'''
open('/tmp/w.html','w').write(page)
# thumbs
thumb_page=f'''<!doctype html><html><head><meta charset="utf-8"><style>body{{background:#1d2332;margin:0;color:#fff;font:13px monospace}} .r{{display:flex;gap:40px;padding:20px}} .t{{position:relative;width:30px;height:46px;transform:scale(6);transform-origin:left top;margin-right:150px}} .t .p{{position:absolute;left:-8.2px;top:-8.2px}}</style></head><body>
<div>ORIGINAL</div><div class="r" id="o" style="height:300px"></div><div>MON XML</div><div class="r" id="m" style="height:340px"></div>
<script>{js}
for (const c of ['brown','white','black']) {{ const d=document.createElement('div'); d.className='t'; d.style.filter='drop-shadow(0 3px 5px rgba(0,0,0,.55))'; d.innerHTML=buildThumbSVG(c,c).replace('<svg ','<svg style="width:30px;height:46px;display:block" '); document.getElementById('o').appendChild(d); }}
const mine={{brown:`{svg_inline(D+'organ_thumb_brown.xml',1).replace(chr(10),'')}`,white:`{svg_inline(D+'organ_thumb_white.xml',1).replace(chr(10),'')}`,black:`{svg_inline(D+'organ_thumb_black.xml',1).replace(chr(10),'')}`}};
for (const c of ['brown','white','black']) {{ const d=document.createElement('div'); d.className='t'; d.innerHTML='<div class="p" style="width:46.22px;height:62.4px">'+mine[c].replace(/width="[^"]*" height="[^"]*"/,'width="46.22" height="62.4"')+'</div>'; document.getElementById('m').appendChild(d); }}
</script></body></html>'''
open('/tmp/t.html','w').write(thumb_page)
with sync_playwright() as p:
    b=p.chromium.launch()
    pg=b.new_page(viewport={'width':1400,'height':520}); pg.goto('file:///tmp/w.html'); pg.screenshot(path='/tmp/w.png')
    pg=b.new_page(viewport={'width':1100,'height':760}); pg.goto('file:///tmp/t.html'); pg.screenshot(path='/tmp/t.png')
    b.close()
print('ok')
