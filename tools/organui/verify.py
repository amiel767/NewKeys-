import re, sys, asyncio
sys.path.insert(0,'.')
from vd2svg import svg_inline
from playwright.sync_api import sync_playwright
html = open('/mnt/user-data/uploads/deepseek_html_20261107_a83460.html').read()
css = re.search(r'<style>(.*?)</style>', html, re.S).group(1)
# extraire les fonctions de thumb du HTML d'origine
js = html[html.index('const PAL = {'):html.index('const THUMB_H')]
SC=4
def knob_orig(S):
    return f'''<div class="knob-wrapper" style="--knob-size:{S}px;"><div class="knob-base"></div><div class="knob-groove"></div><div class="knob-cap"><div class="knob-specular"></div></div></div>'''
def knob_mine(S):
    b=svg_inline(f'../res/drawable/organ_knob_body_{S}.xml',1); c=svg_inline(f'../res/drawable/organ_knob_cap_{S}.xml',1)
    V=S+16
    return f'<div style="position:relative;width:{S}px;height:{S}px"><div style="position:absolute;left:-8px;top:-8px;width:{V}px;height:{V}px">{b}</div><div style="position:absolute;left:-8px;top:-8px;width:{V}px;height:{V}px">{c}</div></div>'
page = f'''<!doctype html><html><head><meta charset="utf-8"><style>{css}
body{{overflow:visible!important;width:auto!important;height:auto!important;background:#1b2130!important;display:block!important;touch-action:auto}}
.row{{display:flex;gap:80px;padding:60px 40px;align-items:center}}
.z{{transform:scale({SC});transform-origin:left top;width:90px;height:90px}}
.lab{{color:#fff;font:14px monospace;padding:0 40px}}
</style></head><body>
<div class="lab">ORIGINAL (CSS)</div><div class="row">{''.join(f'<div class="z">{knob_orig(S)}</div>' for S in (48,52,56))}</div>
<div class="lab" style="margin-top:220px">MON XML</div><div class="row">{''.join(f'<div class="z">{knob_mine(S)}</div>' for S in (48,52,56))}</div>
</body></html>'''
open('/tmp/knobs.html','w').write(page)
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':1300,'height':1000}); pg.goto('file:///tmp/knobs.html'); pg.screenshot(path='/tmp/knobs.png'); b.close()
print('done')
