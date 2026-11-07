const fs = require('fs');
const L = require('./lib');
const {f,rrectD,circleD,lin,rad,P,G,wrapVector,outerShadow,insetShadow} = L;
const out = (name, xml) => fs.writeFileSync(`../res/drawable/${name}.xml`, xml);
const big = (v) => `M-50,-50 H${f(v+50)} V${f(v+50)} H-50 Z`;
const circShape = (cx,cy,r) => { const fn = (e,dx,dy) => circleD(cx+dx, cy+dy, r+e); fn.dims=[2*r,2*r]; return fn; };
const circHole  = (cx,cy,r) => (e,dx,dy) => circleD(cx+dx, cy+dy, r-e);
const rrShape = (x,y,w,h,r4) => { const fn = (e,dx,dy) => rrectD(x+dx-e, y+dy-e, w+2*e, h+2*e, ...r4.map(r=>Math.max(0,r+e))); fn.dims=[w,h]; return fn; };
const rrHole  = (x,y,w,h,r4) => (e,dx,dy) => rrectD(x+dx+e, y+dy+e, w-2*e, h-2*e, ...r4.map(r=>Math.max(0,r-e)));
const ACC = '#8FB2E8';

// ========================= KNOBS (corps + capuchon) =========================
function knob(S) {
  const PAD = 8, V = S + 2*PAD, c = V/2, R = S/2;
  // ---- Corps : base + gorge (groove) ----
  let b = outerShadow(circShape(c,c,R), {A:0.5, blur:4, dy:2});
  const baseD = circleD(c,c,R);
  b += P({d: baseD, fill: lin(PAD,PAD,PAD+S,PAD+S,[[0,'#363e50'],[1,'#171b24']])});
  b += insetShadow(baseD, circHole(c,c,R), big(V), {A:0.9, blur:4, dy:-2});
  b += insetShadow(baseD, circHole(c,c,R), big(V), {A:0.2, color:'#FFFFFF', blur:4, dy:2});
  const Rg = R-2, grooveD = circleD(c,c,Rg);
  b += outerShadow(circShape(c,c,Rg), {A:0.08, color:'#FFFFFF', blur:1, dy:1});
  b += P({d: grooveD, fill:'#090b10'});
  b += insetShadow(grooveD, circHole(c,c,Rg), big(V), {A:1, blur:6, dy:3});
  out(`organ_knob_body_${S}`, wrapVector(`organ_knob_body_${S}`, V, V, V, V, b));

  // ---- Capuchon ----
  const Rc = R-6, cs = S-12, capD = circleD(c,c,Rc), ox = PAD+6;
  let k = outerShadow(circShape(c,c,Rc), {A:0.4, blur:2, dy:1});
  k += outerShadow(circShape(c,c,Rc), {A:0.7, blur:8, dy:4});
  k += P({d: capD, fill: rad(ox+0.35*cs, ox+0.35*cs, 0.9192*cs, [[0,'#5a657d'],[0.45,'#2a3141'],[0.85,'#151a24'],[1,'#0a0d13']])});
  k += insetShadow(capD, circHole(c,c,Rc), big(V), {A:0.1, color:'#FFFFFF', blur:4, dx:2, dy:2});
  k += insetShadow(capD, circHole(c,c,Rc), big(V), {A:0.8, blur:4, dx:-2, dy:-2});
  // reflet spéculaire : ellipse (left 18%, top 12%, 28% × 18% du capuchon) tournée de -40°
  const ex = ox + (0.18+0.14)*cs, ey = ox + (0.12+0.09)*cs, rx = 0.14*cs, ry = 0.09*cs;
  k += `<group android:pivotX="${f(ex)}" android:pivotY="${f(ey)}" android:rotation="-40" android:scaleY="${f(ry/rx)}">\n` +
       P({d: circleD(ex,ey,rx), fill: rad(ex,ey,rx*Math.SQRT2,[[0,'#FFFFFF',0.5],[0.7,'#FFFFFF',0],[1,'#FFFFFF',0]])}) + `</group>\n`;
  out(`organ_knob_cap_${S}`, wrapVector(`organ_knob_cap_${S}`, V, V, V, V, k));
}
[48,52,56].forEach(knob);

// ========================= ROTARY SWITCH (sélecteur de modèle) =========================
(function rotarySwitch(){
  const PAD = 6, V = 46 + 2*PAD, c = V/2, R = 23;
  let s = outerShadow(circShape(c,c,R), {A:0.8, blur:4, dy:2});
  s += P({d: circleD(c,c,R+0.5), stroke:'#FFFFFF', strokeAlpha:0.05, strokeWidth:1});
  const baseD = circleD(c,c,R);
  s += P({d: baseD, fill:'#050505'});
  let wedges = '';
  for (let i=0;i<18;i++) {                       // repeating-conic-gradient(#1a1a1a 0 10°, #050505 10° 20°)
    const a0 = (i*20)*Math.PI/180, a1 = (i*20+10)*Math.PI/180;
    const p0 = [c+R*Math.sin(a0), c-R*Math.cos(a0)], p1 = [c+R*Math.sin(a1), c-R*Math.cos(a1)];
    wedges += `M${f(c)},${f(c)} L${f(p0[0])},${f(p0[1])} A${R},${R} 0 0 1 ${f(p1[0])},${f(p1[1])} Z `;
  }
  s += P({d: wedges.trim(), fill:'#1A1A1A'});
  s += insetShadow(baseD, circHole(c,c,R), big(V), {A:1, blur:4});
  // calotte centrale (::after inset 25%)
  const Rc = 11.5, capD = circleD(c,c,Rc), ox = c-Rc;
  s += outerShadow(circShape(c,c,Rc), {A:0.9, blur:3, dy:1});
  s += P({d: capD, fill: rad(ox+0.3*23, ox+0.3*23, 0.7*23*Math.SQRT2, [[0,'#333333'],[0.6,'#111111'],[1,'#000000']])});
  s += insetShadow(capD, circHole(c,c,Rc), big(V), {A:0.8, blur:2, dx:-1, dy:-1});
  s += insetShadow(capD, circHole(c,c,Rc), big(V), {A:0.1, color:'#FFFFFF', blur:2, dx:1, dy:1});
  // repère blanc (3×11, top 3px) avec lueur
  const ix = c-1.5, iy = PAD+3;
  s += outerShadow(rrShape(ix,iy,3,11,[1,1,1,1]), {A:0.7, color:'#FFFFFF', blur:3});
  s += P({d: rrectD(ix,iy,3,11,1), fill:'#FFFFFF'});
  out('organ_rotary_switch', wrapVector('organ_rotary_switch', V, V, V, V, s));
})();

// ========================= TOGGLE (piste + poignée) =========================
(function toggle(){
  const PAD = 4, W = 44, H = 18, VW = W+2*PAD, VH = H+2*PAD;
  let t = outerShadow(rrShape(PAD,PAD,W,H,[4,4,4,4]), {A:0.1, color:'#FFFFFF', blur:0, dy:1});
  const d = rrectD(PAD,PAD,W,H,4);
  t += P({d, fill:'#050505'});
  t += insetShadow(d, rrHole(PAD,PAD,W,H,[4,4,4,4]), big(Math.max(VW,VH)), {A:0.9, blur:4, dy:2});
  out('organ_toggle_track', wrapVector('organ_toggle_track', VW, VH, VW, VH, t));
  const hs = 14, HV = hs+2*PAD;
  let h = outerShadow(rrShape(PAD,PAD,hs,hs,[3,3,3,3]), {A:0.8, blur:2, dy:1});
  h += P({d: rrectD(PAD,PAD,hs,hs,3), fill: lin(0,PAD,0,PAD+hs,[[0,'#555555'],[1,'#111111']])});
  out('organ_toggle_handle', wrapVector('organ_toggle_handle', HV, HV, HV, HV, h));
})();

// ========================= FADER THUMBS =========================
function faderThumb(name, w, h, radii, barW, barH, horizontalGradient) {
  const PAD = 12, VW = w+2*PAD, VH = h+2*PAD;
  let s = outerShadow(rrShape(PAD,PAD,w,h,radii), {A:0.6, blur:8, dy:4});
  const d = rrectD(PAD,PAD,w,h,...radii);
  const body = horizontalGradient
    ? lin(PAD,0,PAD+w,0,[[0,'#595E72'],[0.5,'#4E5067'],[1,'#2E3040']])      // thumb vertical : "to bottom"
    : lin(0,PAD,0,PAD+h,[[0,'#595E72'],[0.5,'#4E5067'],[1,'#2E3040']]);
  s += P({d, fill: body});
  s += insetShadow(d, rrHole(PAD,PAD,w,h,radii), big(Math.max(VW,VH)), {A:0.2, color:'#FFFFFF', blur:0, dy:1});
  // ::before — reflets latéraux
  // corps "to bottom" => reflets "to right" (et inversement)
  const ov = horizontalGradient
    ? lin(0,PAD,0,PAD+h,[[0,'#FFFFFF',0.1],[0.1,'#FFFFFF',0],[0.9,'#000000',0],[1,'#000000',0.3]])
    : lin(PAD,0,PAD+w,0,[[0,'#FFFFFF',0.1],[0.1,'#FFFFFF',0],[0.9,'#000000',0],[1,'#000000',0.3]]);
  s += P({d, fill: ov});
  const bx = PAD+(w-barW)/2, by = PAD+(h-barH)/2;
  s += outerShadow(rrShape(bx,by,barW,barH,[1,1,1,1]), {A:1, color:ACC, blur:6});
  s += P({d: rrectD(bx,by,barW,barH,1), fill: ACC});
  out(name, wrapVector(name, VW, VH, VW, VH, s));
}
// vertical : 20×26, radius 2 2 3 3, barre LED 12×2 — dégradé du corps "to bottom", reflets horizontaux
faderThumb('organ_fader_thumb_v', 20, 26, [2,2,3,3], 12, 2, false);
// horizontal : 22×18, radius 2, barre LED 2×10.8 — dégradé "to right", reflets verticaux
(function(){ })();
(function faderH(){
  const name='organ_fader_thumb_h', w=22, h=18, PAD=12, VW=w+2*PAD, VH=h+2*PAD, radii=[2,2,2,2];
  let s = outerShadow(rrShape(PAD,PAD,w,h,radii), {A:0.6, blur:8, dy:4});
  const d = rrectD(PAD,PAD,w,h,2);
  s += P({d, fill: lin(PAD,0,PAD+w,0,[[0,'#595E72'],[0.5,'#4E5067'],[1,'#2E3040']])});
  s += insetShadow(d, rrHole(PAD,PAD,w,h,radii), big(Math.max(VW,VH)), {A:0.2, color:'#FFFFFF', blur:0, dy:1});
  s += P({d, fill: lin(0,PAD,0,PAD+h,[[0,'#FFFFFF',0.1],[0.1,'#FFFFFF',0],[0.9,'#000000',0],[1,'#000000',0.3]])});
  const bw=2, bh=h*0.6, bx=PAD+(w-bw)/2, by=PAD+(h-bh)/2;
  s += outerShadow(rrShape(bx,by,bw,bh,[1,1,1,1]), {A:1, color:ACC, blur:6});
  s += P({d: rrectD(bx,by,bw,bh,1), fill: ACC});
  out(name, wrapVector(name, VW, VH, VW, VH, s));
})();
console.log('widgets ok');
