const fs = require('fs');
const L = require('./lib');
const {f,rrectD,lin,rad,P,G,wrapVector,outerShadow} = L;

// ---- Données copiées à l'identique du HTML ----
const PAL = {
  brown: { aTop:"#A4745A",aBot:"#945F44",band:"#BE8864",cTop:"#94603F",cBot:"#7C4A2D",dTop:"#5C3720",dMid:"#4A2A17",dBot:"#52301B",wL1:"#AA6C3C",wL2:"#7E4727",wR1:"#7B4A2C",wR2:"#5E3620",bL1:"#8A5530",bL2:"#6A3D20",bR1:"#6C4025",bR2:"#55321C",rimL:"#E2A266",rimR:"#9C6A42",ridge:"#C8946C",scLight:"#E8C0A0",scDark:"#3A1E0E",dust:"#F0D8C0" },
  white: { aTop:"#F3EEDC",aBot:"#E3DCC4",band:"#FFFDF2",cTop:"#E6DFC8",cBot:"#CEC6AB",dTop:"#B9B197",dMid:"#A69E84",dBot:"#A8A086",wL1:"#F6F1E0",wL2:"#D8D0B6",wR1:"#CFC7AC",wR2:"#B4AC92",bL1:"#E4DCC4",bL2:"#C6BEA4",bR1:"#BDB59B",bR2:"#A59D84",rimL:"#FFFFFF",rimR:"#D9D2BA",ridge:"#FFFFFF",scLight:"#FFFFFF",scDark:"#6B6347",dust:"#FFFFFF" },
  black: { aTop:"#4A4642",aBot:"#3C3835",band:"#6A645E",cTop:"#3A3633",cBot:"#2A2724",dTop:"#1E1B19",dMid:"#151311",dBot:"#1A1816",wL1:"#5A5550",wL2:"#34302D",wR1:"#302D2A",wR2:"#1F1D1B",bL1:"#3E3A37",bL2:"#272422",bR1:"#252220",bR2:"#181615",rimL:"#8C857D",rimR:"#4A4642",ridge:"#76706A",scLight:"#A8A19A",scDark:"#000000",dust:"#CFC8C0" }
};
const SIL = "M310,281 L700,281 Q705,281 705,286 L705,893 Q705,898 700,898 L310,898 Q305,898 305,893 L305,286 Q305,281 310,281 Z";
const FA = "M315,285 L695,285 L695,546 L315,546 Z";
const FC = "M315,546 L695,546 L695,654 L315,654 Z";
const FD = "M315,654 L695,654 L695,893 L315,893 Z";
const ARCS = [["M382,415 Q420,372 478,352",0.28,1.2],["M460,392 Q510,360 575,350",0.18,1.0],["M546,318 Q575,312 605,322",0.15,0.9],["M362,330 Q382,340 410,336",0.15,0.9]];
function rng(seed) { let s=seed; return () => (s = (s*16807)%2147483647)/2147483647; }

// ---- Repère : viewBox SVG d'origine (305 281 400 617) + marge PAD pour l'ombre portée ----
const PAD = 110, OX = 305 - PAD, OY = 281 - PAD;
const X = (x) => x - OX, Y = (y) => y - OY;
const tr = (d) => d.replace(/(-?\d+\.?\d*),(-?\d+\.?\d*)/g, (m,a,b) => `${f(X(+a))},${f(Y(+b))}`);
const line = (x1,y1,x2,y2) => `M${f(X(x1))},${f(Y(y1))} L${f(X(x2))},${f(Y(y2))}`;

function tex(seed,x,y,w,h,light,dark,dust,l0,l1,p) {
  const r = rng(seed); let out='';
  const scratch = (col,a0,a1) => {
    const sx=x+r()*w, sy=y+r()*h, len=l0+r()*(l1-l0);
    const ang=((-50+r()*100)*Math.PI)/180, al=a0+r()*(a1-a0), sw=0.5+r()*0.8;
    out += P({d: line(sx,sy,sx+Math.cos(ang)*len,sy+Math.sin(ang)*len), stroke: col, strokeAlpha: al, strokeWidth: sw});
  };
  for(let i=0;i<light;i++) scratch(p.scLight,0.08,0.22);
  for(let i=0;i<dark;i++) scratch(p.scDark,0.08,0.18);
  for(let i=0;i<dust;i++) {
    const cx=x+r()*w, cy=y+r()*h, rad_=0.6+r()*0.8, al=0.1+r()*0.2;
    const d = `M${f(X(cx-rad_))},${f(Y(cy))} a${f(rad_)},${f(rad_)} 0 1,0 ${f(rad_*2)},0 a${f(rad_)},${f(rad_)} 0 1,0 ${f(-rad_*2)},0 Z`;
    out += P({d, fill: p.dust, fillAlpha: al});
  }
  return out;
}

function thumb(colorKey) {
  const p = PAL[colorKey];
  // ombre portée : filter drop-shadow(0 3px 5px rgba(0,0,0,.55)) — 1 dp = 617/46 unités
  const u = 617/46;
  let body = outerShadow((e,dx,dy)=>rrectD(X(305)-e+dx, Y(281)-e+dy, 400+2*e, 617+2*e, Math.max(0,5+e)),
    {A:0.55, color:'#000000', blur:5*u, dy:3*u});

  const hz = (x1,x2,stops) => lin(X(x1),0,X(x2),0,stops);
  const vt = (y1,y2,stops) => lin(0,Y(y1),0,Y(y2),stops);
  let s = '';
  s += P({d: tr(`M305,281 L705,281 L705,898 L305,898 Z`), fill: hz(305,705,[[0,p.wL1],[0.15,p.wL2],[0.85,p.wR1],[1,p.wR2]])});
  s += P({d: tr(`M305,650 L705,650 L705,898 L305,898 Z`), fill: hz(305,705,[[0,p.bL1],[0.15,p.bL2],[0.85,p.bR1],[1,p.bR2]]), fillAlpha: 0.7});
  s += tex(11,305,281,400,617,30,10,40,5,20,p);
  // Face A
  s += P({d: tr(FA), fill: vt(285,546,[[0,p.aTop],[1,p.aBot]])});
  s += P({d: tr(FA), fill: hz(315,695,[[0,'#FFFFFF',0.08],[1,'#000000',0.15]])});
  s += P({d: tr(FA), fill: rad(X(410),Y(340),220,[[0,'#FFFFFF',0.18],[1,'#FFFFFF',0]])});
  s += P({d: tr(FA), fill: rad(X(560),Y(410),70,[[0,'#000000',0.08],[1,'#000000',0]])});
  s += P({d: tr(FA), fill: vt(440,546,[[0,p.band,0],[0.55,p.band,0.55],[1,p.band,0.35]])});
  s += G(tr(FA), tex(21,315,285,380,261,70,26,70,6,38,p) + ARCS.map(([d,al,sw])=>P({d:tr(d), stroke:p.scLight, strokeAlpha:al, strokeWidth:sw})).join(''));
  s += P({d: line(315,285,695,285), stroke:p.rimL, strokeAlpha:0.6, strokeWidth:1.8});
  s += P({d: line(315,286,315,546), stroke:p.rimL, strokeAlpha:0.55, strokeWidth:1.8});
  s += P({d: line(695,286,695,546), stroke:p.scDark, strokeAlpha:0.5, strokeWidth:1.8});
  // Face C
  s += P({d: tr(FC), fill: vt(546,654,[[0,p.cTop],[1,p.cBot]])});
  s += P({d: tr(FC), fill: hz(315,695,[[0,'#FFFFFF',0.06],[1,'#000000',0.12]])});
  s += G(tr(FC), tex(31,315,546,380,108,110,40,50,5,26,p));
  s += P({d: line(320,547,690,547), stroke:p.ridge, strokeAlpha:0.75, strokeWidth:2});
  s += P({d: line(315,547,315,654), stroke:p.rimL, strokeAlpha:0.45, strokeWidth:1.5});
  s += P({d: line(695,547,695,654), stroke:p.scDark, strokeAlpha:0.45, strokeWidth:1.5});
  // Face D
  s += P({d: tr(FD), fill: vt(654,893,[[0,p.dTop],[0.38,p.dMid],[1,p.dBot]])});
  s += P({d: tr(FD), fill: lin(X(330),Y(660),X(500),Y(800),[[0,'#FFFFFF',0.13],[1,'#FFFFFF',0]])});
  s += P({d: tr(FD), fill: hz(315,695,[[0,'#000000',0],[1,'#000000',0.18]])});
  s += G(tr(FD), tex(41,315,654,380,239,18,10,40,6,22,p));
  s += P({d: line(320,654,690,654), stroke:p.ridge, strokeAlpha:0.55, strokeWidth:2.2});
  s += P({d: line(315,654,315,893), stroke:p.rimL, strokeAlpha:0.35, strokeWidth:1.5});
  s += P({d: line(695,654,695,893), stroke:p.scDark, strokeAlpha:0.45, strokeWidth:1.5});
  s += P({d: line(315,895,695,895), stroke:p.rimL, strokeAlpha:0.25, strokeWidth:1.5});
  body += G(tr(SIL), s);
  body += P({d: tr(SIL), stroke:p.scDark, strokeAlpha:0.4, strokeWidth:1.2});

  const vw = 400 + 2*PAD, vh = 617 + 2*PAD, sc = 46/617; // échelle SVG d'origine (meet) : 46 dp de haut
  return wrapVector(`organ_thumb_${colorKey}`, vw*sc, vh*sc, vw, vh, body);
}
for (const c of ['brown','white','black']) {
  fs.writeFileSync(`../res/drawable/organ_thumb_${c}.xml`, thumb(c));
}
console.log('thumbs ok', (400+2*PAD)*46/617, (617+2*PAD)*46/617);
