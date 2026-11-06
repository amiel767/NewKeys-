import { useState, useRef, useCallback, useEffect, useMemo } from "react";

/* ─────────────────────────────────────────────────────────────────────────────
   ÆTHER ORGAN — console preview, proposition 2

   Disposition RELEVÉE sur la photo, pas inventée. Référence 1080 × 513,
   scène 1000 × 460 (même ratio que la fenêtre Kotlin). Facteur 1000/1080 = 0,926.

   Relevé (coordonnées référence → scène) :
     cadre bois                    0..1080        →  0..1000
     liseré haut                   y   9..32      →  y   8..30
     liseré bas                    y 133..157     →  y 123..145
     panneau cadre gauche          x  18..183     →  x  17..169
     tirettes 1..9                 x 203..241, 256..295, 309..349, 359..404,
                                   465..510, 624..669 (lignes claires mesurées)
                                   pas 53 → 49 ; largeur 39 → 36
                                   y 246..386 → y 228..357
     curseur + 2 knobs du centre   x 359..668     →  x 332..619
     section droite (2 knobs)      x 688..1061    →  x 637..982
     barre basse droite            x 899..1061 y 284..307 → x 832..982 y 263..284
     bande basse gauche            x  18..183 y 319..342  → x  17..169 y 295..317

   UNE SEULE rangée de tirettes : la rangée « corps bois » que j'avais crue
   distincte était le même objet lu trop grossièrement.
   ───────────────────────────────────────────────────────────────────────────── */

const W = 1000, H = 460;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const PLATE = "Cinzel, 'Times New Roman', serif";
const DISP = "'Space Grotesk', 'Segoe UI', system-ui, sans-serif";

const C = {
  wood1: "#6A3A20", wood2: "#46220F", wood3: "#8A5230", woodEdge: "#2A1408",
  slate1: "#3A4650", slate2: "#282C31", slateEdge: "#14181B",
  steel: "#E8EAEE", steel2: "#AEB6C0", steel3: "#5C646E",
  ink: "#E8EDF3", ink2: "#A8B4C2", ink3: "#6B7684",
  brass: "#C8A24A", brass2: "#8A6B24",
  led: "#FFB020", ledOff: "#2A1A08",
  cream: "#D8CFB4", glass: "#7FE8C8",
};

const FEET = ["16′", "5⅓′", "8′", "4′", "2⅔′", "2′", "1⅗′", "1⅓′", "1′"];

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
    x.strokeStyle = rnd() > 0.5 ? `rgba(28,10,2,${0.05 + rnd() * 0.15})` : `rgba(190,132,78,${0.03 + rnd() * 0.10})`;
    x.lineWidth = 0.6 + rnd() * 2.6; x.stroke();
  }
  for (let i = 0; i < 3; i++) {
    const cx = rnd() * w, cy = rnd() * h;
    for (let r = 2; r < 26; r += 2) {
      x.beginPath(); x.ellipse(cx, cy, r * 1.9, r, 0, 0, 6.283);
      x.strokeStyle = `rgba(26,9,2,${Math.max(0, 0.11 - r * 0.003)})`; x.lineWidth = 1.1; x.stroke();
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

const bevel = (r = 6, top = "rgba(255,255,255,.16)", bot = "rgba(0,0,0,.55)") => ({
  borderRadius: r,
  boxShadow: `inset 0 1px 0 ${top}, inset 0 -1px 0 ${bot}, 0 1px 2px rgba(0,0,0,.5)`,
});

/* ── TIRETTE — relevée : canal sombre flanqué de DEUX rails acier, échelle
      gravée dans le canal, index clair en bas. 39 px de large, pas 53. ───── */
function Drawbar({ foot, value, onChange, h = 130, steps = 25 }) {
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
  const railW = 4, gap = 2;
  const chanW = 36 - 2 * (railW + gap);      // 24
  const chanH = h - 14, chanTop = 6;
  const y = (chanH / steps) * (steps - value);   // 0 = en haut, 25 = en bas
  return (
    <div style={{ width: 36, height: h, position: "relative", userSelect: "none", cursor: "ns-resize" }}
      onPointerDown={down} onDoubleClick={() => set(Math.round(steps / 2))}>
      {/* rail gauche */}
      <div style={{ position: "absolute", left: 0, top: 0, width: railW, height: h, borderRadius: 2,
        background: `linear-gradient(90deg, ${C.steel3}, ${C.steel}, ${C.steel2})`,
        boxShadow: "0 1px 2px rgba(0,0,0,.6)" }} />
      {/* rail droit */}
      <div style={{ position: "absolute", right: 0, top: 0, width: railW, height: h, borderRadius: 2,
        background: `linear-gradient(90deg, ${C.steel2}, ${C.steel}, ${C.steel3})`,
        boxShadow: "0 1px 2px rgba(0,0,0,.6)" }} />
      {/* canal sombre + échelle gravée */}
      <div style={{ position: "absolute", left: railW + gap, top: chanTop, width: chanW, height: chanH,
        background: "linear-gradient(180deg, #14171A 0%, #0C0E10 100%)",
        boxShadow: "inset 0 2px 4px rgba(0,0,0,.9)" }}>
        {Array.from({ length: steps + 1 }).map((_, i) => (
          <div key={i} style={{ position: "absolute", left: i % 5 === 0 ? 0 : 3, width: i % 5 === 0 ? "100%" : "calc(100% - 6px)",
            height: i % 5 === 0 ? 1.6 : 1, top: (chanH / steps) * i,
            background: i % 5 === 0 ? "rgba(232,234,238,.72)" : "rgba(232,234,238,.30)" }} />
        ))}
        {/* index : la marque claire qui dit la position */}
        <div style={{ position: "absolute", left: -1, width: chanW + 2, top: y - 1.5, height: 3, borderRadius: 1.5,
          background: C.cream, boxShadow: `0 0 5px ${C.cream}88` }} />
      </div>
      <div style={{ position: "absolute", left: 0, top: h + 2, width: 36, textAlign: "center",
        font: `600 8px/1 ${PLATE}`, letterSpacing: ".08em", color: C.cream }}>{foot}</div>
    </div>
  );
}

/* ── KNOB 3D — dôme + couronne crantée, sans anneau chromé. ─────────────── */
function Knob3D({ label, value, onChange, size = 54, sub }) {
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
  const d = size, ang = -135 + 270 * value;
  return (
    <div style={{ width: d + 24, display: "flex", flexDirection: "column", alignItems: "center", gap: 2, userSelect: "none" }}>
      <div onPointerDown={down} onDoubleClick={() => set(0.5)} style={{ width: d, height: d, position: "relative", cursor: "ns-resize" }}>
        <div style={{ position: "absolute", inset: 0, borderRadius: "50%",
          background: "conic-gradient(from 200deg, #0A0B0D, #2C3037 22%, #14161A 48%, #343941 72%, #0A0B0D)",
          boxShadow: "0 3px 6px rgba(0,0,0,.65), inset 0 1px 0 rgba(255,255,255,.10)" }} />
        <div style={{ position: "absolute", inset: d * 0.09, borderRadius: "50%",
          background: "radial-gradient(circle at 34% 27%, #2E3238 0%, #23272E 46%, #14171B 100%)",
          boxShadow: `inset 0 ${d * 0.04}px ${d * 0.10}px rgba(255,255,255,.09), inset 0 -${d * 0.05}px ${d * 0.12}px rgba(0,0,0,.6)` }} />
        <div style={{ position: "absolute", inset: d * 0.085, borderRadius: "50%",
          background: "repeating-conic-gradient(from 0deg, rgba(0,0,0,.42) 0deg 7deg, rgba(255,255,255,.055) 7deg 12deg)",
          maskImage: `radial-gradient(circle, transparent ${d * 0.30}px, #000 ${d * 0.315}px)`,
          WebkitMaskImage: `radial-gradient(circle, transparent ${d * 0.30}px, #000 ${d * 0.315}px)` }} />
        <div style={{ position: "absolute", left: "50%", top: "50%", width: 2.4, height: d * 0.27, marginLeft: -1.2,
          borderRadius: 2, transformOrigin: "50% 0%", transform: `rotate(${ang}deg) translateY(-${d * 0.40}px)`,
          background: `linear-gradient(180deg, ${C.ink} 0%, ${C.brass} 100%)`, boxShadow: "0 0 3px rgba(0,0,0,.8)" }} />
        <svg width={d} height={d} style={{ position: "absolute", inset: 0, opacity: 0.5 }}>
          {Array.from({ length: 11 }).map((_, i) => {
            const a = (-135 + 270 * (i / 10)) * Math.PI / 180, r1 = d * 0.455, r2 = d * 0.5;
            return <line key={i} x1={d / 2 + Math.sin(a) * r1} y1={d / 2 - Math.cos(a) * r1}
              x2={d / 2 + Math.sin(a) * r2} y2={d / 2 - Math.cos(a) * r2}
              stroke={i % 5 === 0 ? C.brass : C.ink3} strokeWidth={i % 5 === 0 ? 1.6 : 1} />;
          })}
        </svg>
      </div>
      <div style={{ font: `600 8px/1 ${PLATE}`, letterSpacing: ".14em", color: C.ink2, textAlign: "center" }}>{label}</div>
      {sub != null && <div style={{ font: `500 8px/1 ${MONO}`, color: C.brass }}>{sub}</div>}
    </div>
  );
}

function Push({ label, on, onClick, w = 44, h = 26 }) {
  return (
    <button onClick={onClick} style={{ width: w, height: h, border: "none", cursor: "pointer", position: "relative",
      ...bevel(5), background: on ? "linear-gradient(180deg,#3A2A12,#241806)" : "linear-gradient(180deg,#3A3F47,#23272D)",
      display: "flex", alignItems: "center", justifyContent: "center", gap: 4, padding: 0 }}>
      <span style={{ width: 5, height: 5, borderRadius: "50%", background: on ? C.led : C.ledOff,
        boxShadow: on ? `0 0 6px ${C.led}` : "inset 0 1px 1px rgba(0,0,0,.8)" }} />
      <span style={{ font: `700 7.5px/1 ${PLATE}`, letterSpacing: ".08em", color: on ? "#F6E3B4" : C.ink2 }}>{label}</span>
    </button>
  );
}

function Rocker({ label, on, onClick, w = 40, h = 24 }) {
  return (
    <button onClick={onClick} style={{ width: w, height: h, border: "none", padding: 0, cursor: "pointer", position: "relative",
      ...bevel(5, "rgba(255,255,255,.10)", "rgba(0,0,0,.6)"), background: "#1A1D22" }}>
      <span style={{ position: "absolute", left: 2, right: 2, top: on ? 2 : h / 2, height: h / 2 - 2, borderRadius: 3,
        background: on ? `linear-gradient(180deg,${C.brass},${C.brass2})` : "linear-gradient(180deg,#4A5058,#2C3037)",
        boxShadow: "inset 0 1px 0 rgba(255,255,255,.24), 0 1px 2px rgba(0,0,0,.7)", transition: "top .09s" }} />
      <span style={{ position: "absolute", left: 0, right: 0, bottom: 2, font: `700 6.5px/1 ${PLATE}`,
        letterSpacing: ".06em", color: on ? "#F6E3B4" : C.ink3, textAlign: "center" }}>{label}</span>
    </button>
  );
}

function Screen({ lines, w = 150, h = 38 }) {
  return (
    <div style={{ width: w, height: h, borderRadius: 5, padding: "4px 7px", overflow: "hidden",
      background: "linear-gradient(180deg,#0B1418,#060B0D)", border: "1px solid #0A0E11",
      boxShadow: "inset 0 2px 5px rgba(0,0,0,.9), 0 1px 0 rgba(255,255,255,.06)" }}>
      {lines.map((l, i) => (
        <div key={i} style={{ font: `${l.big ? 600 : 500} ${l.big ? 11 : 8}px/${l.big ? 1.4 : 1.5} ${MONO}`,
          color: l.dim ? C.ink3 : l.color || C.glass, letterSpacing: ".04em",
          textShadow: l.dim ? "none" : `0 0 4px ${C.glass}59` }}>{l.t}</div>
      ))}
    </div>
  );
}

/* panneau : cadre en creux, comme les sections de la référence */
const Panel = ({ x, y, w, h, label, children }) => (
  <div style={{ position: "absolute", left: x, top: y, width: w, height: h, borderRadius: 7,
    background: "linear-gradient(180deg, rgba(255,255,255,.045), rgba(0,0,0,.20))",
    boxShadow: "inset 0 1px 0 rgba(255,255,255,.10), inset 0 -1px 0 rgba(0,0,0,.5), 0 1px 2px rgba(0,0,0,.45)" }}>
    {label && (
      <div style={{ position: "absolute", left: 8, top: 5, font: `700 7.5px/1 ${PLATE}`, letterSpacing: ".18em",
        color: C.brass, textShadow: "0 1px 0 rgba(0,0,0,.8)" }}>{label}</div>
    )}
    {children}
  </div>
);

const USER_PRESETS = ["Gospel Praise", "Blue Note Mellow", "Church Plenum", "Shout Full"];

/* ═══════════════════════════ CONSOLE ═══════════════════════════════════════ */
export default function OrganConsolePreview() {
  const wood = useTexture(makeWood, W, H);
  const slate = useTexture(makeSlate, W, H);

  const [db, setDb] = useState(() => [8, 8, 8, 0, 0, 0, 0, 0, 0]);
  const setD = (i, v) => setDb((d) => d.map((x, k) => (k === i ? v : x)));
  const [k, setK] = useState({ drive: .42, tone: .55, click: .30, room: .25, vol: .70 });
  const [sw, setSw] = useState({ vib: true, chor: false, perc: false, third: false, soft: false, fast: false, les: 1 });
  const [oct, setOct] = useState(0);
  const [preset, setPreset] = useState(USER_PRESETS[0]);
  const [open, setOpen] = useState(false);
  const t = (o) => setSw((s) => ({ ...s, ...o }));
  const pct = (v) => Math.round(v * 100);
  const reg = db.map((v) => v).join("").replace(/(.{3})(.{3})(.{3})/, "$1 $2 $3");

  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
      gap: 14, padding: 20, background: "#0A0C10", fontFamily: DISP }}>
      <div style={{ width: W, height: H, position: "relative", borderRadius: 22, overflow: "hidden", ...wood,
        boxShadow: `inset 0 0 0 1px ${C.woodEdge}, inset 0 2px 0 rgba(255,255,255,.10), 0 18px 40px rgba(0,0,0,.7)` }}>

        {/* liserés du cadre */}
        <div style={{ position: "absolute", inset: 5, borderRadius: 18, border: "1px solid rgba(255,220,180,.13)", pointerEvents: "none" }} />
        <div style={{ position: "absolute", inset: 9, borderRadius: 15, border: "1px solid rgba(0,0,0,.5)", pointerEvents: "none" }} />

        {/* ardoise */}
        <div style={{ position: "absolute", left: 14, top: 14, width: W - 28, height: H - 28, borderRadius: 13, ...slate,
          boxShadow: "inset 0 0 0 1px rgba(0,0,0,.6), inset 0 2px 6px rgba(0,0,0,.55)" }} />

        {/* ── LISERÉ HAUT (y 8..30) : titre + preset USER + OCTAVE ─────── */}
        <div style={{ position: "absolute", left: 17, top: 8, width: W - 34, height: 30, borderRadius: 6,
          display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 10px",
          background: "linear-gradient(180deg, rgba(255,255,255,.05), rgba(0,0,0,.16))",
          boxShadow: "inset 0 1px 0 rgba(255,255,255,.10)" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 9 }}>
            <div style={{ width: 22, height: 22, borderRadius: 6, display: "flex", alignItems: "center", justifyContent: "center",
              background: "linear-gradient(180deg, rgba(255,255,255,.10), rgba(0,0,0,.25))", ...bevel(6) }}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke={C.ink2} strokeWidth="2.6" strokeLinecap="round">
                <path d="M15 18l-6-6 6-6" />
              </svg>
            </div>
            <div style={{ font: `800 12px/1 ${PLATE}`, letterSpacing: ".16em", color: C.brass,
              textShadow: "0 1px 0 rgba(0,0,0,.8)" }}>ÆTHER ORGAN</div>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <button onClick={() => setOpen((v) => !v)} style={{ width: 172, height: 22, border: "none", cursor: "pointer",
              ...bevel(6), background: "linear-gradient(180deg,#2F353D,#1B1F25)", display: "flex", alignItems: "center",
              justifyContent: "space-between", padding: "0 8px" }}>
              <span style={{ font: `600 10.5px/1 ${DISP}`, color: C.ink }}>{preset}</span>
              <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke={C.ink3} strokeWidth="3" strokeLinecap="round">
                <path d="M6 9l6 6 6-6" />
              </svg>
            </button>
            <div style={{ display: "flex", alignItems: "center", gap: 3 }}>
              <span style={{ font: `700 7px/1 ${PLATE}`, letterSpacing: ".14em", color: C.ink3 }}>OCT</span>
              <button onClick={() => setOct((v) => Math.max(-2, v - 1))} style={{ width: 20, height: 22, border: "none",
                cursor: "pointer", ...bevel(5), background: "linear-gradient(180deg,#2F353D,#1B1F25)",
                color: C.ink, font: `700 12px/1 ${MONO}` }}>−</button>
              <div style={{ width: 30, height: 22, borderRadius: 5, display: "flex", alignItems: "center", justifyContent: "center",
                background: "linear-gradient(180deg,#0B1418,#060B0D)", boxShadow: "inset 0 2px 4px rgba(0,0,0,.9)",
                font: `600 11px/1 ${MONO}`, color: oct === 0 ? C.ink2 : C.glass }}>{oct > 0 ? `+${oct}` : oct}</div>
              <button onClick={() => setOct((v) => Math.min(2, v + 1))} style={{ width: 20, height: 22, border: "none",
                cursor: "pointer", ...bevel(5), background: "linear-gradient(180deg,#2F353D,#1B1F25)",
                color: C.ink, font: `700 12px/1 ${MONO}` }}>+</button>
            </div>
          </div>
        </div>

        {/* ── SECTION GAUCHE (x 17..169) : écran + tirettes de volume ──── */}
        <Panel x={17} y={46} w={152} h={124} label="UPPER">
          <div style={{ position: "absolute", left: 10, top: 20 }}>
            <Screen w={132} h={40} lines={[
              { t: "USER PRESET", dim: true },
              { t: preset.toUpperCase(), big: true, color: C.brass },
              { t: `OCT ${oct > 0 ? "+" + oct : oct}`, dim: true },
            ]} />
          </div>
          <div style={{ position: "absolute", left: 10, top: 70, right: 10 }}>
            {[["VOLUME", k.vol], ["BASS", k.tone]].map(([lab, val]) => (
              <div key={lab} style={{ marginBottom: 8 }}>
                <div style={{ font: `600 7px/1 ${PLATE}`, letterSpacing: ".14em", color: C.ink3, marginBottom: 3 }}>{lab}</div>
                <div style={{ height: 7, borderRadius: 4, position: "relative",
                  background: "linear-gradient(180deg,#0B0E12,#05070A)", boxShadow: "inset 0 2px 3px rgba(0,0,0,.9)" }}>
                  <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: `${val * 100}%`, borderRadius: 4,
                    background: `linear-gradient(180deg,${C.brass},${C.brass2})` }} />
                </div>
              </div>
            ))}
          </div>
        </Panel>

        {/* ── SECTION GAUCHE BASSE (y 295..317) ───────────────────────── */}
        <Panel x={17} y={295} w={152} h={52} label="LOWER">
          <div style={{ position: "absolute", left: 10, top: 22, right: 10 }}>
            <div style={{ height: 7, borderRadius: 4, position: "relative",
              background: "linear-gradient(180deg,#0B0E12,#05070A)", boxShadow: "inset 0 2px 3px rgba(0,0,0,.9)" }}>
              <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: `${k.room * 100}%`, borderRadius: 4,
                background: `linear-gradient(180deg,${C.brass},${C.brass2})` }} />
            </div>
            <div style={{ marginTop: 7, display: "flex", gap: 5 }}>
              <Push label="SOFT" on={sw.soft} onClick={() => t({ soft: !sw.soft })} w={44} h={20} />
              <Push label="FAST" on={sw.fast} onClick={() => t({ fast: !sw.fast })} w={44} h={20} />
            </div>
          </div>
        </Panel>

        {/* ── LES 9 TIRETTES (x 188..617, pas 49) : une seule rangée ──── */}
        <div style={{ position: "absolute", left: 188, top: 228, display: "flex", gap: 13 }}>
          {Array.from({ length: 9 }).map((_, i) => (
            <Drawbar key={i} foot={FEET[i]} value={db[i]} onChange={(v) => setD(i, v)} h={130} />
          ))}
        </div>
        <div style={{ position: "absolute", left: 188, top: 372, width: 466, textAlign: "center",
          font: `500 11px/1 ${MONO}`, color: C.cream, letterSpacing: ".08em" }}>{reg}</div>

        {/* ── SECTION CENTRE (x 332..619) : curseur + 2 knobs ─────────── */}
        <Panel x={332} y={205} w={287} h={132} label="DRAWBAR CURVE">
          <div style={{ position: "absolute", left: 12, top: 24, width: 118 }}>
            <div style={{ height: 9, borderRadius: 5, position: "relative",
              background: "linear-gradient(180deg,#0B0E12,#05070A)", boxShadow: "inset 0 2px 4px rgba(0,0,0,.9)" }}>
              <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: `${k.drive * 100}%`, borderRadius: 5,
                background: `linear-gradient(180deg,${C.brass},${C.brass2})` }} />
              {Array.from({ length: 20 }).map((_, i) => (
                <div key={i} style={{ position: "absolute", left: `${(i / 19) * 100}%`, top: 2, width: 1, height: 5,
                  background: "rgba(255,255,255,.10)" }} />
              ))}
            </div>
            <div style={{ marginTop: 8, display: "flex", gap: 4 }}>
              <Push label="UPPER" on onClick={() => {}} w={44} h={22} />
              <Push label="LOWER" on={sw.perc} onClick={() => t({ perc: !sw.perc })} w={44} h={22} />
            </div>
            <div style={{ marginTop: 5, display: "flex", gap: 4 }}>
              <Push label="VIB" on={sw.vib} onClick={() => t({ vib: !sw.vib })} w={42} h={22} />
              <Push label="CHOR" on={sw.chor} onClick={() => t({ chor: !sw.chor })} w={46} h={22} />
            </div>
          </div>
          <div style={{ position: "absolute", left: 142, top: 18, display: "flex", gap: 14 }}>
            <Knob3D label="DRIVE" value={k.drive} onChange={(v) => setK({ ...k, drive: v })} size={50} sub={pct(k.drive)} />
            <Knob3D label="TONE" value={k.tone} onChange={(v) => setK({ ...k, tone: v })} size={50} sub={pct(k.tone)} />
          </div>
        </Panel>

        {/* ── SECTION DROITE (x 637..982) : 2 knobs + percussion ──────── */}
        <Panel x={637} y={170} w={345} h={122} label="VOICING">
          <div style={{ position: "absolute", left: 16, top: 18, display: "flex", gap: 18 }}>
            <Knob3D label="KEY CLICK" value={k.click} onChange={(v) => setK({ ...k, click: v })} size={54} sub={pct(k.click)} />
            <Knob3D label="ROOM" value={k.room} onChange={(v) => setK({ ...k, room: v })} size={54} sub={pct(k.room)} />
          </div>
          <div style={{ position: "absolute", left: 200, top: 20 }}>
            <div style={{ font: `700 7.5px/1 ${PLATE}`, letterSpacing: ".16em", color: C.brass, marginBottom: 8 }}>PERCUSSION</div>
            <div style={{ display: "flex", gap: 5, marginBottom: 5 }}>
              <Push label="ON" on={sw.perc} onClick={() => t({ perc: !sw.perc })} w={38} h={22} />
              <Push label="3RD" on={sw.third} onClick={() => t({ third: !sw.third })} w={42} h={22} />
            </div>
            <div style={{ display: "flex", gap: 5 }}>
              <Rocker label="SOFT" on={sw.soft} onClick={() => t({ soft: !sw.soft })} />
              <Rocker label="FAST" on={sw.fast} onClick={() => t({ fast: !sw.fast })} />
            </div>
          </div>
        </Panel>

        {/* ── SECTION DROITE BASSE : rotary + sortie ──────────────────── */}
        <Panel x={637} y={310} w={345} h={100} label="ROTARY / OUTPUT">
          <div onClick={() => t({ les: (sw.les + 1) % 3 })} style={{ position: "absolute", left: 16, top: 24, width: 72, height: 40, cursor: "pointer" }}>
            <div style={{ position: "absolute", inset: 0, borderRadius: 7, background: "linear-gradient(180deg,#0B0E12,#05070A)",
              boxShadow: "inset 0 2px 5px rgba(0,0,0,.9)" }} />
            <div style={{ position: "absolute", left: "50%", top: "50%", width: 30, height: 30, marginLeft: -15, marginTop: -15,
              borderRadius: "50%", border: `2px solid ${sw.les ? C.brass : C.steel3}`,
              transform: `rotate(${sw.les * 40}deg)`, transition: "transform .12s" }}>
              <div style={{ position: "absolute", left: "50%", top: 2, width: 3, height: 10, marginLeft: -1.5, borderRadius: 2, background: C.steel2 }} />
            </div>
            <div style={{ position: "absolute", left: 0, right: 0, bottom: 2, textAlign: "center",
              font: `700 6.5px/1 ${PLATE}`, letterSpacing: ".10em", color: C.brass }}>{["STOP", "SLOW", "FAST"][sw.les]}</div>
          </div>
          <div style={{ position: "absolute", left: 108, top: 30, right: 16 }}>
            <div style={{ font: `600 7px/1 ${PLATE}`, letterSpacing: ".14em", color: C.ink3, marginBottom: 4 }}>OUTPUT</div>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <div style={{ flex: 1, height: 9, borderRadius: 5, position: "relative",
                background: "linear-gradient(180deg,#0B0E12,#05070A)", boxShadow: "inset 0 2px 4px rgba(0,0,0,.9)" }}>
                <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: `${k.vol * 100}%`, borderRadius: 5,
                  background: `linear-gradient(180deg,${C.brass},${C.brass2})`, boxShadow: `0 0 8px ${C.brass}55` }} />
              </div>
              <span style={{ font: `600 11px/1 ${MONO}`, color: C.ink2, width: 30 }}>{pct(k.vol)}</span>
            </div>
          </div>
        </Panel>

        {/* ── LISTE DES PRESETS USER ──────────────────────────────────── */}
        {open && (
          <div style={{ position: "absolute", right: 26, top: 40, width: 224, borderRadius: 9, overflow: "hidden",
            background: "linear-gradient(180deg,#262C34,#171B21)", zIndex: 20,
            boxShadow: "0 14px 30px rgba(0,0,0,.7), inset 0 0 0 1px rgba(255,255,255,.07)" }}>
            <div style={{ padding: "6px 10px", font: `700 7.5px/1 ${PLATE}`, letterSpacing: ".18em", color: C.brass,
              borderBottom: "1px solid rgba(0,0,0,.45)" }}>USER PRESETS</div>
            {USER_PRESETS.map((p) => (
              <button key={p} onClick={() => { setPreset(p); setOpen(false); }} style={{ display: "block", width: "100%",
                textAlign: "left", padding: "7px 10px", border: "none", cursor: "pointer",
                background: p === preset ? "rgba(200,162,74,.16)" : "transparent",
                color: p === preset ? "#F6E3B4" : C.ink2, font: `600 11px/1 ${DISP}` }}>{p}</button>
            ))}
          </div>
        )}
      </div>

      <div style={{ font: `500 11px/1.6 ${MONO}`, color: C.ink3, textAlign: "center" }}>
        tirette : glisser verticalement, double-clic = milieu · knob : glisser · pilule = presets USER
      </div>
    </div>
  );
}
