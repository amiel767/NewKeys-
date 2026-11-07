import sys; sys.path.insert(0,'.')
import xml.etree.ElementTree as ET, copy
from vd2svg import svg_inline
from playwright.sync_api import sync_playwright
A='{http://schemas.android.com/apk/res/android}'
src='../res/drawable/organ_knob_cap_52.xml'
def variant(keep):
    t=ET.parse(src); r=t.getroot(); kids=list(r)
    for i,k in enumerate(kids):
        if not keep(i,k): r.remove(k)
    t.write('/tmp/v.xml'); return svg_inline('/tmp/v.xml',5)
kids=list(ET.parse(src).getroot())
print([ (i,k.tag) for i,k in enumerate(kids)][:3], len(kids))
gi=[i for i,k in enumerate(kids) if k.tag=='path' and k.find('{http://schemas.android.com/aapt}attr') is not None]
print('gradient path idx', gi)
vs=[('fill only',lambda i,k:i in gi),('fill+shadows',lambda i,k:i<=gi[0]),('all',lambda i,k:True),('no groups',lambda i,k:k.tag!='group')]
html='<body style="background:#1b2130;display:flex;gap:20px;padding:20px">'+''.join(f'<div style="color:#fff;font:12px monospace">{n}<br>{variant(f)}</div>' for n,f in vs)+'</body>'
open('/tmp/d.html','w').write(html)
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':1500,'height':450}); pg.goto('file:///tmp/d.html'); pg.screenshot(path='/tmp/d.png'); b.close()
