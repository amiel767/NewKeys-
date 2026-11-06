import { useState, useRef, useCallback, useEffect, useMemo } from "react";

/* ─────────────────────────────────────────────────────────────────────────────
   ÆTHER ORGAN — console preview (paysage, scène 1000 × 460 comme la fenêtre Kotlin)

   Traduction d'une console d'orgue à roues phoniques photographiée, avec nos choix :
     · le cadre est en BOIS — l'original est rouge carmin ;
     · l'ardoise garde ses gris froids, réchauffés pour épouser le bois ;
     · pas de pitch bend ni de sustain ; PROGRAM devient le nom du preset USER ;
     · TRANSPOSE retiré ; KEY RANGE devient OCT +/- ;
     · knobs 3D, tirettes et boutons : les nôtres, pas une copie.

   Mesures de la référence (1080 × 513) reportées sur 1000 × 460 :
     cadre 6 px · ardoise · panneau HAUT (226,56)-(642,107) · BAS (191,244)-(679,512)
     9 tirettes : slot 32 de large, pas 53 ; rail acier #F8F9FD ; capot #232323
     rangée du bas = tirettes abaissées, corps en bois, sans crans ni chiffres
     à droite : 2 knobs puis 2 autres, 3 rockers en haut, écran à gauche
   ───────────────────────────────────────────────────────────────────────────── */

const W = 1000, H = 460;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const PLATE = "Cinzel, 'Times New Roman', serif";
const DISP = "'Space Grotesk', 'Segoe UI', system-ui, sans-serif";

/* ── palette : bois + ardoise réchauffée ─────────────────────────────────── */
const C = {
  wood1: "#6A3A20", wood2: "#46220F", wood3: "#8A5230", woodEdge: "#2A1408",
  slate1: "#3C4854", slate2: "#2A2E33", slateEdge: "#15191C",
  steel: "#F8F9FD", steel2: "#B9C2CC", steel3: "#6C7480",
  capHi: "#3A3A3C", capLo: "#0E0E10",
  brass: "#C8A24A", brass2: "#8A6B24",
  ink: "#E8EDF3", ink2: "#A8B4C2", ink3: "#6B7684",
  led: "#FFB020", ledOff: "#2A1A08",
  cream: "#D8CFB4", glass: "#7FE8C8",
};

const FEET = ["16′", "5⅓′", "8′", "4′", "2⅔′", "2′", "1⅗′", "1⅓′", "1′"];
const CAPS = ["brown", "brown", "brown", "white", "white", "black", "black", "black", "black"];

/* ── bois : veinage procédural ───────────────────────────────────────────── */
function makeWood(w, h) {
  const c = document.createElement("canvas");
  c.width = w; c.height = h;
  const x = c.getContext("2d");
  const g = x.createLinearGradient(0, 0, 0, h);
  g.addColorStop(0, C.wood3); g.addColorStop(0.34, C.wood1); g.addColorStop(1, C.wood2);
  x.fillStyle = g; x.fillRect(0, 0, w, h);
  let seed = 20261106;
  const rnd = () => (seed = (seed * 1664525 + 1013904223) % 4294967296) / 4294967296;
  for (let i = 0; i < 110; i++) {
    const y0 = rnd() * h, amp = 2 + rnd() * 8, f = 0.003 + rnd() * 0.013, ph = rnd() * 6.28;
    x.beginPath();
    for (let px = 0; px <= w; px += 4) x.lineTo(px, y0 + Math.sin(px * f + ph) * amp);
    x.strokeStyle = rnd() > 0.5
      ? `rgba(28,10,2,${0.05 + rnd() * 0.15})`
      : `rgba(190,132,78,${0.03 + rnd() * 0.10})`;
    x.lineWidth = 0.6 + rnd() * 2.6; x.stroke();
  }
  for (let i = 0; i < 3; i++) {
    const cx = rnd() * w, cy = rnd() * h;
    for (let r = 2; r < 26; r += 2) {
      x.beginPath(); x.ellipse(cx, cy, r * 1.9, r, 0, 0, 6.283);
      x.strokeStyle = `rgba(26,9,2,${Math.max(0, 0.11 - r * 0.003)})`;
      x.lineWidth = 1.1; x.stroke();
    }
  }
  return c;
}

/* ── ardoise : grain fin ─────────────────────────────────────────────────── */
function makeSlate(w, h) {
  const c = document.createElement("canvas");
  c.width = w; c.height = h;
  const x = c.getContext("2d");
  x.fillStyle = C.slate2; x.fillRect(0, 0, w, h);
  const img = x.getImageData(0, 0, w, h), d = img.data;
  let seed = 7;
  const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
  for (let i = 0; i < d.length; i += 4) {
    const n = (rnd() - 0.5) * 18;
    d[i] = Math.max(0, Math.min(255, d[i] + n * 0.8));
    d[i + 1] = Math.max(0, Math.min(255, d[i + 1] + n * 0.9));
    d[i + 2] = Math.max(0, Math.min(255, d[i + 2] + n));
  }
  x.putImageData(img, 0, 0);
  return c;
}

function useTexture(fn, w, h) {
  const ref = useRef(null);
  if (!ref.current) ref.current = fn(w, h);
  const url = useMemo(() => ref.current.toDataURL(), []);
  return { backgroundImage: `url(${url})`, backgroundSize: "cover" };
}

/* ── relief : biseau clair en haut, ombre en bas ─────────────────────────── */
const bevel = (r = 6, top = "rgba(255,255,255,.16)", bot = "rgba(0,0,0,.55)") => ({
  borderRadius: r,
  boxShadow: `inset 0 1px 0 ${top}, inset 0 -1px 0 ${bot}, 0 1px 2px rgba(0,0,0,.5)`,
});

/* ── KNOB 3D — le nôtre : dôme bombé, couronne crantée, pointeur clair.
      Pas d'anneau chromé : c'est ce qui faisait « copie » sur la référence. ─ */
function Knob3D({ label, value, onChange, size = 56, cap = "#2E3238", accent = C.brass, sub }) {
  const drag = useRef(null);
  const set = useCallback((v) => onChange && onChange(Math.max(0, Math.min(1, v))), [onChange]);
  const down = (e) => { e.preventDefault(); drag.current = { y: e.clientY, v: value }; };
  useEffect(() => {
    const move = (e) => { if (drag.current) set(drag.current.v + (drag.current.y - e.clientY) / 170); };
    const up = () => { drag.current = null; };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    return () => { window.removeEventListener("pointermove", move); window.removeEventListener("pointerup", up); };
  }, [set]);
  const d = size;
  const ang = -135 + 270 * value;
  return (
    <div style={{ width: d + 26, display: "flex", flexDirection: "column", alignItems: "center", gap: 3, userSelect: "none" }}>
      <div onPointerDown={down} onDoubleClick={() => set(0.5)} style={{ width: d, height: d, position: "relative", cursor: "ns-resize" }}>
        {/* socle : anneau sombre, donne l'épaisseur */}
        <div style={{ position: "absolute", inset: 0, borderRadius: "50%",
          background: "conic-gradient(from 200deg, #0A0B0D, #2C3037 22%, #14161A 48%, #343941 72%, #0A0B0D)",
          boxShadow: "0 3px 6px rgba(0,0,0,.65), inset 0 1px 0 rgba(255,255,255,.10)" }} />
        {/* dôme : point clair discret en haut à gauche */}
        <div style={{ position: "absolute", inset: d * 0.09, borderRadius: "50%",
          background: `radial-gradient(circle at 34% 27%, ${cap} 0%, #23272E 46%, #14171B 100%)`,
          boxShadow: `inset 0 ${d * 0.04}px ${d * 0.10}px rgba(255,255,255,.09), inset 0 -${d * 0.05}px ${d * 0.12}px rgba(0,0,0,.6)` }} />
        {/* couronne crantée : 14 dents — ce qui le rend nôtre */}
        <div style={{ position: "absolute", inset: d * 0.085, borderRadius: "50%",
          background: "repeating-conic-gradient(from 0deg, rgba(0,0,0,.42) 0deg 7deg, rgba(255,255,255,.055) 7deg 12deg)",
          maskImage: `radial-gradient(circle, transparent ${d * 0.30}px, #000 ${d * 0.315}px)`,
          WebkitMaskImage: `radial-gradient(circle, transparent ${d * 0.30}px, #000 ${d * 0.315}px)` }} />
        {/* pointeur : c'est lui qui dit la valeur */}
        <div style={{ position: "absolute", left: "50%", top: "50%", width: 2.4, height: d * 0.27, marginLeft: -1.2,
          borderRadius: 2, transformOrigin: "50% 0%",
          transform: `rotate(${ang}deg) translateY(-${d * 0.40}px)`,
          background: `linear-gradient(180deg, ${C.ink} 0%, ${accent} 100%)`, boxShadow: "0 0 3px rgba(0,0,0,.8)" }} />
        {/* graduations, teintées bois */}
        <svg width={d} height={d} style={{ position: "absolute", inset: 0, opacity: 0.5 }}>
          {Array.from({ length: 11 }).map((_, i) => {
            const a = (-135 + 270 * (i / 10)) * Math.PI / 180;
            const r1 = d * 0.455, r2 = d * 0.5;
            return <line key={i}
              x1={d / 2 + Math.sin(a) * r1} y1={d / 2 - Math.cos(a) * r1}
              x2={d / 2 + Math.sin(a) * r2} y2={d / 2 - Math.cos(a) * r2}
              stroke={i % 5 === 0 ? accent : C.ink3} strokeWidth={i % 5 === 0 ? 1.6 : 1} />;
          })}
        </svg>
      </div>
      <div style={{ font: `600 8.5px/1 ${PLATE}`, letterSpacing: ".16em", color: C.ink2, textAlign: "center" }}>{label}</div>
      {sub != null && <div style={{ font: `500 8px/1 ${MONO}`, color: C.brass, letterSpacing: ".06em" }}>{sub}</div>}
    </div>
  );
}

/* ── TIRETTE — la nôtre : rail acier encastré aux deux bouts, crans, capot
      noir mat biseauté avec une gorge de doigt. Le pied en haut, le cran en
      bas : les deux ont leur bande, ils ne touchent jamais le rail. ──────── */
function Drawbar({ foot, cap, value, onChange, h = 250, steps = 25 }) {
  const drag = useRef(null);
  const set = useCallback((v) => onChange(Math.max(0, Math.min(steps, Math.round(v)))), [onChange, steps]);
  const down = (e) => { e.preventDefault(); drag.current = { y: e.clientY, v: value }; };
  useEffect(() => {
    const move = (e) => { if (drag.current) set(drag.current.v + (drag.current.y - e.clientY) / (h / steps)); };
    const up = () => { drag.current = null; };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    return () => { window.removeEventListener("pointermove", move); window.removeEventListener("pointerup", up); };
  }, [set, h, steps]);
  const railTop = 14, railH = h - railTop - 24, capH = 30;
  const y = railTop + (railH - capH) * (value / steps);
  const P = { brown: ["#6B4426", "#3A2312"], white: ["#D9D3C4", "#8A8478"], black: ["#33343A", "#0D0E11"] }[cap];
  return (
    <div style={{ width: 34, height: h, position: "relative", userSelect: "none", cursor: "ns-resize" }}
      onPointerDown={down} onDoubleClick={() => set(Math.round(steps / 2))}>
      {/* rail */}
      <div style={{ position: "absolute", left: 12, top: railTop, width: 10, height: railH, borderRadius: 3,
        background: `linear-gradient(90deg, ${C.steel3} 0%, ${C.steel} 42%, ${C.steel2} 58%, ${C.steel3} 100%)`,
        boxShadow: "inset 0 0 3px rgba(0,0,0,.7), 0 1px 2px rgba(0,0,0,.6)" }}>
        {Array.from({ length: steps + 1 }).map((_, i) => (
          <div key={i} style={{ position: "absolute", left: 0, width: "100%", height: 1,
            top: (railH / steps) * i, background: i % 5 === 0 ? "rgba(0,0,0,.62)" : "rgba(0,0,0,.26)" }} />
        ))}
      </div>
      {/* capot */}
      <div style={{ position: "absolute", left: 1, top: y, width: 32, height: capH, borderRadius: 5,
        background: `linear-gradient(180deg, ${P[0]} 0%, ${P[0]} 42%, ${P[1]} 100%)`,
        boxShadow: "inset 0 1px 0 rgba(255,255,255,.28), inset 0 -2px 3px rgba(0,0,0,.5), 0 2px 4px rgba(0,0,0,.65)" }}>
        <div style={{ position: "absolute", left: 3, right: 3, top: capH * 0.38, height: capH * 0.24, borderRadius: 2,
          background: "rgba(0,0,0,.38)", boxShadow: "inset 0 1px 2px rgba(0,0,0,.7), inset 0 -1px 0 rgba(255,255,255,.10)" }} />
      </div>
      <div style={{ position: "absolute", left: 0, top: 0, width: 34, textAlign: "center",
        font: `600 8px/1 ${PLATE}`, letterSpacing: ".10em", color: C.cream }}>{foot}</div>
      <div style={{ position: "absolute", left: 0, top: h - 20, width: 34, textAlign: "center",
        font: `500 13px/1 ${MONO}`, color: C.ink }}>{value}</div>
    </div>
  );
}

/* ── BOUTON — le nôtre : poussoir à LED à gauche du libellé. ─────────────── */
function Push({ label, on, onClick, w = 46 }) {
  return (
    <button onClick={onClick} style={{ width: w, height: 30, border: "none", cursor: "pointer", position: "relative",
      ...bevel(6), background: on ? "linear-gradient(180deg, #3A2A12, #241806)" : "linear-gradient(180deg, #3A3F47, #23272D)",
      display: "flex", alignItems: "center", justifyContent: "center", gap: 5, padding: 0 }}>
      <span style={{ width: 5, height: 5, borderRadius: "50%", background: on ? C.led : C.ledOff,
        boxShadow: on ? `0 0 6px ${C.led}` : "inset 0 1px 1px rgba(0,0,0,.8)" }} />
      <span style={{ font: `700 8px/1 ${PLATE}`, letterSpacing: ".10em", color: on ? "#F6E3B4" : C.ink2 }}>{label}</span>
    </button>
  );
}

/* ── ROCKER — le nôtre : came plate, pas de bascule chromée. ─────────────── */
function Rocker({ label, on, onClick, w = 44, h = 26 }) {
  return (
    <button onClick={onClick} style={{ width: w, height: h, border: "none", padding: 0, cursor: "pointer", position: "relative",
      ...bevel(5, "rgba(255,255,255,.10)", "rgba(0,0,0,.6)"), background: "#1A1D22" }}>
      <span style={{ position: "absolute", left: 2, right: 2, top: on ? 2 : h / 2, height: h / 2 - 2, borderRadius: 3,
        background: on ? `linear-gradient(180deg, ${C.brass}, ${C.brass2})` : "linear-gradient(180deg, #4A5058, #2C3037)",
        boxShadow: "inset 0 1px 0 rgba(255,255,255,.24), 0 1px 2px rgba(0,0,0,.7)", transition: "top .09s" }} />
      <span style={{ position: "absolute", left: 0, right: 0, bottom: 3, font: `700 7px/1 ${PLATE}`,
        letterSpacing: ".08em", color: on ? "#F6E3B4" : C.ink3, textAlign: "center" }}>{label}</span>
    </button>
  );
}

/* ── ÉCRAN : afficheur à cristaux, verre teinté. ─────────────────────────── */
function Screen({ lines, w = 186, h = 46 }) {
  return (
    <div style={{ width: w, height: h, borderRadius: 5, padding: "5px 8px", overflow: "hidden",
      background: "linear-gradient(180deg, #0B1418 0%, #060B0D 100%)",
      boxShadow: "inset 0 2px 5px rgba(0,0,0,.9), 0 1px 0 rgba(255,255,255,.06)", border: "1px solid #0A0E11" }}>
      {lines.map((l, i) => (
        <div key={i} style={{ font: `${l.big ? 600 : 500} ${l.big ? 12 : 8.5}px/${l.big ? 1.5 : 1.6} ${MONO}`,
          color: l.dim ? C.ink3 : l.color || C.glass, letterSpacing: ".05em", textAlign: l.right ? "right" : "left",
          textShadow: l.dim ? "none" : `0 0 4px ${C.glass}59` }}>{l.t}</div>
      ))}
    </div>
  );
}

const Cap = ({ children, x, y, w, align = "center" }) => (
  <div style={{ position: "absolute", left: x, top: y, width: w, textAlign: align,
    font: `800 8.5px/1 ${PLATE}`, letterSpacing: ".22em", color: C.brass,
    textShadow: "0 1px 0 rgba(0,0,0,.7)" }}>{children}</div>
);

const USER_PRESETS = ["Gospel Praise", "Blue Note Mellow", "Church Plenum", "Shout Full"];

/* ═══════════════════════════ CONSOLE ═══════════════════════════════════════ */
export default function OrganConsolePreview() {
  const wood = useTexture(makeWood, W, H);
  const slate = useTexture(makeSlate, W, H);

  const [db, setDb] = useState(() =>
    [8, 8, 8, 0, 0, 0, 0, 0, 0, 8, 6, 5, 0, 0, 0, 0, 0, 0]);
  const setD = (i, v) => setDb((d) => d.map((x, k) => (k === i ? v : x)));
  const [k, setK] = useState({ drive: .42, tone: .55, click: .30, room: .25, vol: .70 });
  const [sw, setSw] = useState({ vib: true, chor: false, perc: false, third: false, soft: false, fast: false, les: 1 });
  const [oct, setOct] = useState(0);
  const [preset, setPreset] = useState(USER_PRESETS[0]);
  const [open, setOpen] = useState(false);
  const t = (o) => setSw((s) => ({ ...s, ...o }));

  const reg = (off) => db.slice(off, off + 9).map((v) => Math.round(v / 8 * 8)).join("").replace(/(.{3})(.{3})(.{3})/, "$1 $2 $3");
  const pct = (v) => Math.round(v * 100);

  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
      gap: 14, padding: 20, background: "#0A0C10", fontFamily: DISP }}>
      <div style={{ width: W, height: H, position: "relative", borderRadius: 24, overflow: "hidden", ...wood,
        boxShadow: `inset 0 0 0 1px ${C.woodEdge}, inset 0 2px 0 rgba(255,255,255,.10), 0 18px 40px rgba(0,0,0,.7)` }}>

        {/* filets du cadre chantourné */}
        <div style={{ position: "absolute", inset: 5, borderRadius: 20, border: "1px solid rgba(255,220,180,.13)", pointerEvents: "none" }} />
        <div style={{ position: "absolute", inset: 9, borderRadius: 17, border: "1px solid rgba(0,0,0,.5)", pointerEvents: "none" }} />

        {/* ardoise */}
        <div style={{ position: "absolute", left: 14, top: 14, width: W - 28, height: H - 28, borderRadius: 14, ...slate,
          boxShadow: "inset 0 0 0 1px rgba(0,0,0,.6), inset 0 2px 6px rgba(0,0,0,.55)" }} />

        {/* ── EN-TÊTE ─────────────────────────────────────────────────── */}
        <div style={{ position: "absolute", left: 22, top: 20, width: 320, height: 40, display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ width: 30, height: 30, borderRadius: 8, display: "flex", alignItems: "center", justifyContent: "center",
            background: "linear-gradient(180deg, rgba(255,255,255,.10), rgba(0,0,0,.25))", ...bevel(8) }}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke={C.ink2} strokeWidth="2.4" strokeLinecap="round">
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </div>
          <div>
            <div style={{ font: `800 14px/1.1 ${PLATE}`, letterSpacing: ".18em", color: C.brass,
              textShadow: "0 1px 0 rgba(0,0,0,.8)" }}>ÆTHER ORGAN</div>
            <div style={{ font: `500 8.5px/1.4 ${MONO}`, letterSpacing: ".10em", color: C.ink3 }}>TONEWHEEL CONSOLE</div>
          </div>
        </div>

        {/* preset USER + OCT : remplace PROGRAM, KEY RANGE et TRANSPOSE */}
        <div style={{ position: "absolute", right: 22, top: 20, display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ textAlign: "right" }}>
            <div style={{ font: `700 7.5px/1 ${PLATE}`, letterSpacing: ".18em", color: C.ink3, marginBottom: 4 }}>USER PRESET</div>
            <button onClick={() => setOpen((v) => !v)} style={{ width: 190, height: 32, border: "none", cursor: "pointer",
              ...bevel(8), background: "linear-gradient(180deg, #2F353D, #1B1F25)", display: "flex", alignItems: "center",
              justifyContent: "space-between", padding: "0 10px" }}>
              <span style={{ font: `600 12px/1 ${DISP}`, color: C.ink }}>{preset}</span>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke={C.ink3} strokeWidth="3" strokeLinecap="round">
                <path d="M6 9l6 6 6-6" />
              </svg>
            </button>
          </div>
          <div style={{ textAlign: "center" }}>
            <div style={{ font: `700 7.5px/1 ${PLATE}`, letterSpacing: ".18em", color: C.ink3, marginBottom: 4 }}>OCTAVE</div>
            <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
              <button onClick={() => setOct((v) => Math.max(-2, v - 1))} style={{ width: 26, height: 32, border: "none",
                cursor: "pointer", ...bevel(7), background: "linear-gradient(180deg, #2F353D, #1B1F25)",
                color: C.ink, font: `700 15px/1 ${MONO}` }}>−</button>
              <div style={{ width: 44, height: 32, borderRadius: 7, display: "flex", alignItems: "center", justifyContent: "center",
                background: "linear-gradient(180deg, #0B1418, #060B0D)", boxShadow: "inset 0 2px 5px rgba(0,0,0,.9)",
                font: `600 14px/1 ${MONO}`, color: oct === 0 ? C.ink2 : C.glass,
                textShadow: oct ? `0 0 5px ${C.glass}66` : "none" }}>{oct > 0 ? `+${oct}` : oct}</div>
              <button onClick={() => setOct((v) => Math.min(2, v + 1))} style={{ width: 26, height: 32, border: "none",
                cursor: "pointer", ...bevel(7), background: "linear-gradient(180deg, #2F353D, #1B1F25)",
                color: C.ink, font: `700 15px/1 ${MONO}` }}>+</button>
            </div>
          </div>
        </div>

        {/* ── MANUEL HAUT ─────────────────────────────────────────────── */}
        <Cap x={54} y={72} w={340} align="left">UPPER MANUAL</Cap>
        <div style={{ position: "absolute", left: 264, top: 70, width: 130, textAlign: "right",
          font: `500 13px/1 ${MONO}`, color: C.cream, letterSpacing: ".06em" }}>{reg(0)}</div>
        <div style={{ position: "absolute", left: 54, top: 92, display: "flex", gap: 19 }}>
          {Array.from({ length: 9 }).map((_, i) => (
            <Drawbar key={i} foot={FEET[i]} cap={CAPS[i]} value={db[i]} onChange={(v) => setD(i, v)} />
          ))}
        </div>

        {/* ── MANUEL BAS : la rangée des tirettes abaissées, corps en bois ── */}
        <Cap x={606} y={72} w={340} align="right">LOWER MANUAL</Cap>
        <div style={{ position: "absolute", left: 816, top: 70, width: 130, textAlign: "right",
          font: `500 13px/1 ${MONO}`, color: C.cream, letterSpacing: ".06em" }}>{reg(9)}</div>
        <div style={{ position: "absolute", left: 606, top: 92, display: "flex", gap: 19 }}>
          {Array.from({ length: 9 }).map((_, i) => (
            <Drawbar key={i} foot={FEET[i]} cap="brown" value={db[9 + i]} onChange={(v) => setD(9 + i, v)} />
          ))}
        </div>

        {/* ── PANNEAU CENTRAL ─────────────────────────────────────────── */}
        <Cap x={402} y={64} w={196}>VIBRATO / CHORUS</Cap>
        <div style={{ position: "absolute", left: 424, top: 82 }}>
          <Knob3D label="DEPTH" value={k.tone} onChange={(v) => setK({ ...k, tone: v })} size={50} sub={pct(k.tone)} />
        </div>
        <div style={{ position: "absolute", left: 506, top: 82 }}>
          <Knob3D label="RATE" value={k.room} onChange={(v) => setK({ ...k, room: v })} size={50} sub={pct(k.room)} />
        </div>
        <div style={{ position: "absolute", left: 424, top: 168, display: "flex", gap: 5 }}>
          <Push label="VIB" on={sw.vib} onClick={() => t({ vib: !sw.vib })} w={44} />
          <Push label="CHOR" on={sw.chor} onClick={() => t({ chor: !sw.chor })} w={48} />
        </div>
        <div style={{ position: "absolute", left: 424, top: 206, display: "flex", gap: 5 }}>
          <Push label="UPPER" on onClick={() => {}} w={46} />
          <Push label="LOWER" on={sw.perc} onClick={() => t({ perc: !sw.perc })} w={46} />
        </div>

        <Cap x={402} y={252} w={196}>PERCUSSION</Cap>
        <div style={{ position: "absolute", left: 432, top: 270 }}>
          <Knob3D label="DECAY" value={k.click} onChange={(v) => setK({ ...k, click: v })} size={42} sub={pct(k.click)} />
        </div>
        <div style={{ position: "absolute", left: 498, top: 270, display: "flex", gap: 5 }}>
          <Push label="ON" on={sw.perc} onClick={() => t({ perc: !sw.perc })} w={40} />
          <Push label="3RD" on={sw.third} onClick={() => t({ third: !sw.third })} w={44} />
        </div>

        <Cap x={402} y={336} w={196}>ROTARY</Cap>
        {/* le rotor : seul, au centre du panneau */}
        <div onClick={() => t({ les: (sw.les + 1) % 3 })}
          style={{ position: "absolute", left: 462, top: 352, width: 76, height: 42, cursor: "pointer" }}>
          <div style={{ position: "absolute", inset: 0, borderRadius: 8, background: "linear-gradient(180deg, #0B0E12, #05070A)",
            boxShadow: "inset 0 2px 5px rgba(0,0,0,.9), 0 1px 0 rgba(255,255,255,.06)" }} />
          <div style={{ position: "absolute", left: "50%", top: "50%", width: 34, height: 34, marginLeft: -17, marginTop: -17,
            borderRadius: "50%", border: `2px solid ${sw.les ? C.brass : C.steel3}`,
            transform: `rotate(${sw.les * 40}deg)`, transition: "transform .12s" }}>
            <div style={{ position: "absolute", left: "50%", top: 2, width: 3, height: 12, marginLeft: -1.5, borderRadius: 2,
              background: C.steel2 }} />
          </div>
          <div style={{ position: "absolute", left: 0, right: 0, bottom: 3, textAlign: "center",
            font: `700 7px/1 ${PLATE}`, letterSpacing: ".10em", color: C.brass }}>{["STOP", "SLOW", "FAST"][sw.les]}</div>
        </div>

        {/* ── ÉCRAN + ROCKERS + VOICING ───────────────────────────────── */}
        <div style={{ position: "absolute", left: 54, top: 348 }}>
          <Screen w={186} h={46} lines={[
            { t: "USER PRESET", dim: true },
            { t: preset.toUpperCase(), big: true, color: C.brass },
            { t: `OCT ${oct > 0 ? "+" + oct : oct}   UPPER ${reg(0)}`, dim: true },
          ]} />
        </div>

        <div style={{ position: "absolute", left: 664, top: 60, display: "flex", gap: 8 }}>
          <Rocker label="SOFT" on={sw.soft} onClick={() => t({ soft: !sw.soft })} />
          <Rocker label="FAST" on={sw.fast} onClick={() => t({ fast: !sw.fast })} />
          <Rocker label="3RD" on={sw.third} onClick={() => t({ third: !sw.third })} />
        </div>

        <Cap x={664} y={110} w={300} align="left">VOICING</Cap>
        <div style={{ position: "absolute", left: 672, top: 130, display: "flex", gap: 22 }}>
          <Knob3D label="DRIVE" value={k.drive} onChange={(v) => setK({ ...k, drive: v })} size={58} sub={pct(k.drive)} />
          <Knob3D label="TONE" value={k.tone} onChange={(v) => setK({ ...k, tone: v })} size={58} sub={pct(k.tone)} />
        </div>
        <div style={{ position: "absolute", left: 672, top: 226, display: "flex", gap: 22 }}>
          <Knob3D label="KEY CLICK" value={k.click} onChange={(v) => setK({ ...k, click: v })} size={58} sub={pct(k.click)} />
          <Knob3D label="ROOM" value={k.room} onChange={(v) => setK({ ...k, room: v })} size={58} sub={pct(k.room)} />
        </div>

        {/* ── SORTIE ──────────────────────────────────────────────────── */}
        <Cap x={54} y={402} w={200} align="left">OUTPUT</Cap>
        <div style={{ position: "absolute", left: 54, top: 418, display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 240, height: 10, borderRadius: 5, position: "relative",
            background: "linear-gradient(180deg, #0B0E12, #05070A)", boxShadow: "inset 0 2px 4px rgba(0,0,0,.9)" }}>
            <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: `${k.vol * 100}%`, borderRadius: 5,
              background: `linear-gradient(180deg, ${C.brass}, ${C.brass2})`, boxShadow: `0 0 8px ${C.brass}55` }} />
            {Array.from({ length: 24 }).map((_, i) => (
              <div key={i} style={{ position: "absolute", left: `${(i / 23) * 100}%`, top: 2, width: 1, height: 6,
                background: "rgba(255,255,255,.10)" }} />
            ))}
          </div>
          <input type="range" min="0" max="1" step="0.01" value={k.vol} onChange={(e) => setK({ ...k, vol: +e.target.value })}
            style={{ width: 140, accentColor: C.brass }} />
          <span style={{ font: `600 12px/1 ${MONO}`, color: C.ink2, width: 34 }}>{pct(k.vol)}</span>
        </div>

        {/* ── LISTE DES PRESETS USER ──────────────────────────────────── */}
        {open && (
          <div style={{ position: "absolute", right: 22, top: 58, width: 240, borderRadius: 10, overflow: "hidden",
            background: "linear-gradient(180deg, #262C34, #171B21)", zIndex: 20,
            boxShadow: "0 14px 30px rgba(0,0,0,.7), inset 0 0 0 1px rgba(255,255,255,.07)" }}>
            <div style={{ padding: "7px 10px", font: `700 8px/1 ${PLATE}`, letterSpacing: ".18em", color: C.brass,
              borderBottom: "1px solid rgba(0,0,0,.45)" }}>USER PRESETS</div>
            {USER_PRESETS.map((p) => (
              <button key={p} onClick={() => { setPreset(p); setOpen(false); }} style={{ display: "block", width: "100%",
                textAlign: "left", padding: "8px 10px", border: "none", cursor: "pointer",
                background: p === preset ? "rgba(200,162,74,.16)" : "transparent",
                color: p === preset ? "#F6E3B4" : C.ink2, font: `600 11.5px/1 ${DISP}` }}>{p}</button>
            ))}
          </div>
        )}
      </div>

      <div style={{ font: `500 11px/1.6 ${MONO}`, color: C.ink3, textAlign: "center" }}>
        tirette : glisser verticalement, double-clic = milieu · knob : glisser, double-clic = centre ·
        pilule en haut à droite = presets USER
      </div>
    </div>
  );
}
