# Convertisseur VectorDrawable -> SVG (vérification visuelle uniquement)
import re, sys, itertools
UID=itertools.count(1)
import xml.etree.ElementTree as ET
A='{http://schemas.android.com/apk/res/android}'; AAPT='{http://schemas.android.com/aapt}'
def color(c):
    c=c.lstrip('#'); 
    if len(c)==8: return '#'+c[2:], int(c[:2],16)/255
    return '#'+c, 1.0
def conv(path, scale=4):
    root=ET.parse(path).getroot()
    vw=float(root.get(A+'viewportWidth')); vh=float(root.get(A+'viewportHeight'))
    defs=[]; body=[]; n=[0]; pre=f'u{next(UID)}'
    def grad(g):
        n[0]+=1; gid=f'{pre}g{n[0]}'
        t=g.get(A+'type'); stops=''.join(
            f'<stop offset="{i.get(A+"offset")}" stop-color="{color(i.get(A+"color"))[0]}" stop-opacity="{color(i.get(A+"color"))[1]}"/>' for i in g.findall('item'))
        if t=='radial':
            defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{g.get(A+"centerX")}" cy="{g.get(A+"centerY")}" r="{g.get(A+"gradientRadius")}">{stops}</radialGradient>')
        else:
            defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(A+"startX")}" y1="{g.get(A+"startY")}" x2="{g.get(A+"endX")}" y2="{g.get(A+"endY")}">{stops}</linearGradient>')
        return f'url(#{gid})'
    def walk(el, out):
        for ch in el:
            if ch.tag=='group':
                tf=''
                px=ch.get(A+'pivotX','0'); py=ch.get(A+'pivotY','0')
                rot=ch.get(A+'rotation'); sy=ch.get(A+'scaleY'); sx=ch.get(A+'scaleX')
                if rot or sy or sx:
                    tf=f'translate({px} {py}) rotate({rot or 0}) scale({sx or 1} {sy or 1}) translate(-{px} -{py})'
                    # Android : scale puis rotation => matrice = T(p) R S T(-p)
                clip=ch.find('clip-path')
                inner=[]; walk(ch, inner)
                n[0]+=1; cid=f'{pre}c{n[0]}'
                if clip is not None:
                    defs.append(f'<clipPath id="{cid}"><path d="{clip.get(A+"pathData")}"/></clipPath>')
                    out.append(f'<g clip-path="url(#{cid})"'+(f' transform="{tf}"' if tf else '')+'>'+''.join(inner)+'</g>')
                else:
                    out.append('<g'+(f' transform="{tf}"' if tf else '')+'>'+''.join(inner)+'</g>')
            elif ch.tag=='path':
                at=f'd="{ch.get(A+"pathData")}"'
                fc=ch.get(A+'fillColor'); g=ch.find(AAPT+'attr')
                if g is not None: at+=f' fill="{grad(g.find("gradient"))}"'
                elif fc: c,a=color(fc); at+=f' fill="{c}"'
                else: at+=' fill="none"'
                if ch.get(A+'fillAlpha'): at+=f' fill-opacity="{ch.get(A+"fillAlpha")}"'
                if ch.get(A+'strokeColor'): at+=f' stroke="{color(ch.get(A+"strokeColor"))[0]}" stroke-width="{ch.get(A+"strokeWidth")}"'
                if ch.get(A+'strokeAlpha'): at+=f' stroke-opacity="{ch.get(A+"strokeAlpha")}"'
                if ch.get(A+'fillType')=='evenOdd': at+=' fill-rule="evenodd"'
                out.append(f'<path {at}/>')
    walk(root, body)
    return vw,vh,''.join(defs),''.join(body)
def svg_inline(path, scale):
    vw,vh,defs,body=conv(path)
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{vw*scale}" height="{vh*scale}" viewBox="0 0 {vw} {vh}"><defs>{defs}</defs>{body}</svg>'
if __name__=='__main__':
    print(svg_inline(sys.argv[1], float(sys.argv[2])))
