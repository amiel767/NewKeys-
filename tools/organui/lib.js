// ===== Primitives de génération VectorDrawable (Android XML) =====
const Q = [-1.6449,-1.0364,-0.6745,-0.3853,-0.1257,0.1257,0.3853,0.6745,1.0364,1.6449]; // quantiles N(0,1), K=10
const f = (n) => { const s = (Math.round(n*1000)/1000).toString(); return s === '-0' ? '0' : s; };
const hexA = (hex, a) => {
  hex = hex.replace('#','');
  if (hex.length === 3) hex = hex.split('').map(c=>c+c).join('');
  const A = Math.round(Math.max(0,Math.min(1,a))*255).toString(16).padStart(2,'0').toUpperCase();
  return '#' + A + hex.toUpperCase();
};
const rrectD = (x,y,w,h,tl,tr,br,bl) => {
  if (tr === undefined) { tr = br = bl = tl; }
  if (w <= 0 || h <= 0) return null;
  const mx = Math.min(w,h)/2; tl=Math.min(tl,mx); tr=Math.min(tr,mx); br=Math.min(br,mx); bl=Math.min(bl,mx);
  const a = (r,dx,dy) => r>0 ? `A${f(r)},${f(r)} 0 0 1 ${f(dx)},${f(dy)} ` : `L${f(dx)},${f(dy)} `;
  return `M${f(x+tl)},${f(y)} H${f(x+w-tr)} ` + a(tr,x+w,y+tr) + `V${f(y+h-br)} ` + a(br,x+w-br,y+h)
    + `H${f(x+bl)} ` + a(bl,x,y+h-bl) + `V${f(y+tl)} ` + a(tl,x+tl,y) + 'Z';
};
const circleD = (cx,cy,r) => r<=0 ? null : `M${f(cx-r)},${f(cy)} A${f(r)},${f(r)} 0 1,1 ${f(cx+r)},${f(cy)} A${f(r)},${f(r)} 0 1,1 ${f(cx-r)},${f(cy)} Z`;

const gradAttr = (g, attrName='android:fillColor') => {
  const items = g.stops.map(s => `<item android:offset="${f(s[0])}" android:color="${hexA(s[1], s[2]===undefined?1:s[2])}"/>`).join('');
  if (g.type === 'radial')
    return `<aapt:attr name="${attrName}"><gradient android:type="radial" android:centerX="${f(g.cx)}" android:centerY="${f(g.cy)}" android:gradientRadius="${f(g.r)}">${items}</gradient></aapt:attr>`;
  return `<aapt:attr name="${attrName}"><gradient android:type="linear" android:startX="${f(g.x1)}" android:startY="${f(g.y1)}" android:endX="${f(g.x2)}" android:endY="${f(g.y2)}">${items}</gradient></aapt:attr>`;
};
const lin = (x1,y1,x2,y2,stops) => ({type:'linear',x1,y1,x2,y2,stops});
const rad = (cx,cy,r,stops) => ({type:'radial',cx,cy,r,stops});

// path: {d, fill:'#rrggbb'|gradient, fillAlpha, stroke, strokeAlpha, strokeWidth, evenOdd}
const P = (o) => {
  if (!o.d) return '';
  let attrs = `android:pathData="${o.d}"`;
  let child = '';
  if (o.fill) {
    if (typeof o.fill === 'string') attrs += ` android:fillColor="${o.fill.toUpperCase().length===4?o.fill:o.fill.toUpperCase()}"`;
    else child = gradAttr(o.fill);
  }
  if (o.fillAlpha !== undefined && o.fillAlpha < 1) attrs += ` android:fillAlpha="${f(o.fillAlpha)}"`;
  if (o.stroke) attrs += ` android:strokeColor="${o.stroke.toUpperCase()}" android:strokeWidth="${f(o.strokeWidth)}"`;
  if (o.strokeAlpha !== undefined && o.strokeAlpha < 1) attrs += ` android:strokeAlpha="${f(o.strokeAlpha)}"`;
  if (o.evenOdd) attrs += ' android:fillType="evenOdd"';
  return child ? `<path ${attrs}>${child}</path>\n` : `<path ${attrs}/>\n`;
};
const G = (clipD, inner) => `<group>\n<clip-path android:pathData="${clipD}"/>\n${inner}</group>\n`;
const wrapVector = (name, wDp, hDp, vw, vh, body) =>
`<?xml version="1.0" encoding="utf-8"?>
<!-- ${name} — généré (AetherKeys / Organ). Ne pas éditer à la main : voir gen/*.js -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="${f(wDp)}dp"
    android:height="${f(hDp)}dp"
    android:viewportWidth="${f(vw)}"
    android:viewportHeight="${f(vh)}">
${body}</vector>
`;

// Ombre portée extérieure "box-shadow: dx dy blur spread rgba(color,A)" — shapeFn(e) -> pathData (forme gonflée de e)
const erf = (x) => { const t = 1/(1+0.3275911*Math.abs(x)); const y = 1-(((((1.061405429*t-1.453152027)*t)+1.421413741)*t-0.284496736)*t+0.254829592)*t*Math.exp(-x*x); return x>=0?y:-y; };
function outerShadow(shapeFn, {A, color='#000000', blur, dx=0, dy=0, spread=0}) {
  const sigma = blur/2; const qs = sigma === 0 ? [0] : Q; const K = qs.length;
  // petites formes : l'ombre floutée ne dépasse jamais l'intensité au centre d'un pavé w×h flouté (erf)
  if (sigma > 0 && shapeFn.dims) { const [w,h] = shapeFn.dims; A = A * Math.pow(erf((w+2*spread)/(2*Math.SQRT2*sigma)) * erf((h+2*spread)/(2*Math.SQRT2*sigma)), 0.6); }
  const es = qs.map(q => sigma*q + spread).sort((a,b)=>b-a);
  let out = '', prev = 0;
  es.forEach((e,j) => {
    const T = A*(j+1)/K; const a = (T-prev)/(1-prev); prev = T;
    const d = shapeFn(e, dx, dy);
    if (d) out += P({d, fill: color, fillAlpha: a});
  });
  return out;
}
// Ombre interne "inset dx dy blur rgba(color,A)" — holeFn(e,dx,dy) -> pathData du trou (forme décalée, rétrécie de e); bigD = grand rectangle
function insetShadow(clipD, holeFn, bigD, {A, color='#000000', blur, dx=0, dy=0}) {
  const sigma = blur/2; const qs = sigma === 0 ? [0] : Q; const K = qs.length;
  const es = qs.map(q => sigma*q).sort((a,b)=>b-a);
  let out = '', prev = 0;
  es.forEach((e,j) => {
    const T = A*(j+1)/K; const a = (T-prev)/(1-prev); prev = T;
    const hole = holeFn(e, dx, dy);
    if (sigma === 0 && e === 0) { /* bord net */ }
    if (e <= -9999) return;
    out += P({d: bigD + (hole ? ' ' + hole : ''), fill: color, fillAlpha: a, evenOdd: true});
  });
  return G(clipD, out);
}
module.exports = {f,hexA,rrectD,circleD,lin,rad,P,G,wrapVector,outerShadow,insetShadow,Q};
