import { useState, useRef, useEffect } from "react";

/* ─────────────────────────────────────────────────────────────
   VIRTUAL KEYBOARD — landscape smartphone preview (891 × 411 dp)
   One container: pitch bend, layer lines (one per active track), keyboard.
   The mixer around it is a simulation. Controls under the frame are preview helpers.
   No sound is produced.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const NOTE = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
const BLACK = [1, 3, 6, 8, 10];
const RMIN = 24, RMAX = 108;                       // C1 … C8
const RW = 832;
const FRAME = "#10151F", WELL = "#080B11";   // the container never changes color                                    // width of the lines and keys area
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const mName = (m) => NOTE[m % 12].replace("#", "♯") + (Math.floor(m / 12) - 1);
const rgba = (h, a) => { const r = parseInt(h.slice(1, 3), 16), g = parseInt(h.slice(3, 5), 16), b = parseInt(h.slice(5, 7), 16); return `rgba(${r},${g},${b},${a})`; };

const KEYS = []; for (let m = RMIN; m <= RMAX; m++) KEYS.push(m);
const WHITES = KEYS.filter((m) => !BLACK.includes(m % 12));
const BLACKS = KEYS.filter((m) => BLACK.includes(m % 12));
const WW = RW / WHITES.length, BW = WW * 0.6;
const KH = 50, BH = 31;
const wx = (m) => WHITES.indexOf(m) * WW;
const bx = (m) => WHITES.filter((x) => x < m).length * WW;
const cx = (m) => (BLACK.includes(m % 12) ? bx(m) : wx(m) + WW / 2);
const nearest = (x) => { let b = RMIN, d = 1e9; KEYS.forEach((m) => { const dd = Math.abs(cx(m) - x); if (dd < d) { d = dd; b = m; } }); return b; };
const PX_PER_SEMI = RW / (RMAX - RMIN);

/* track colors, flat, as in the app */
const TRACKS = [
  { name: "S90 Full Grand", color: "#E68A29" },
  { name: "Motif XF Galaxy DX", color: "#36B582" },
  { name: "Analog Warm Pad", color: "#3B82F6" },
  { name: "Organ B3 Fast", color: "#9333EA" },
];

/* keyboard themes */
const THEMES = [
  { id: "classic", name: "Classic", frame: "#10151F", well: "#080B11", edge: "#9AA2B1", w: ["#FFFFFF", "#F1F3F7", "#D5D9E2"], b: ["#2D3240", "#14171F", "#06080C"], label: "#7B8396", press: "#8FB2E8", pressB: "#3F73C9" },
  { id: "blue", name: "Blue", frame: "#0C2347", well: "#06142B", edge: "#8FA3C4", w: ["#F7FAFF", "#E4ECF8", "#C9D6EA"], b: ["#1E4276", "#12294D", "#081730"], label: "#6F87AE", press: "#3D8BFF", pressB: "#2A6FE0" },
  { id: "cream", name: "Cream", frame: "#241A12", well: "#120C07", edge: "#B79F78", w: ["#FFF8E6", "#EFDDB8", "#D6C196"], b: ["#4C3B2C", "#2B2017", "#140E09"], label: "#8E7A5A", press: "#F5A524", pressB: "#D98A0F" },
  { id: "red", name: "Red", frame: "#5A1019", well: "#2E080E", edge: "#C9A0A4", w: ["#FFFFFF", "#F6E9EA", "#E1C9CC"], b: ["#8E202D", "#5C111B", "#2E0910"], label: "#A9777C", press: "#E5313F", pressB: "#C71F2D" },
  { id: "dark", name: "Dark", frame: "#14161B", well: "#08090B", edge: "#0A0B0E", w: ["#5A5F6E", "#3D414D", "#2B2E37"], b: ["#131418", "#0A0B0E", "#030304"], label: "#8B91A0", press: "#FF3B5C", pressB: "#E0284A", hl: 0.2 },
  { id: "violet", name: "Violet", frame: "#1C1336", well: "#0E091D", edge: "#A99BC9", w: ["#F8F4FF", "#E5DDF7", "#CFC3EA"], b: ["#3E2D6E", "#251848", "#120B27"], label: "#8878B0", press: "#9A5CFF", pressB: "#7C3DE0" },
  { id: "emerald", name: "Emerald", frame: "#08241C", well: "#04120E", edge: "#9CBDAE", w: ["#F4FCF8", "#DAEFE3", "#C0DDCC"], b: ["#14513F", "#0B2F25", "#05150F"], label: "#6E9886", press: "#22C98B", pressB: "#12A872" },
  { id: "sunset", name: "Sunset", frame: "#2A1208", well: "#150A04", edge: "#C4A08C", w: ["#FFF6EE", "#F4DCCB", "#DDBDA6"], b: ["#552424", "#331313", "#180808"], label: "#A07C68", press: "#FF6B3D", pressB: "#E0522A" },
  { id: "graphite", name: "Graphite", frame: "#151821", well: "#0A0C11", edge: "#8C93A1", w: ["#EEF0F4", "#CDD2DA", "#B2B8C3"], b: ["#3C414C", "#1F2229", "#0C0E12"], label: "#707888", press: "#7DD3FC", pressB: "#38BDF8", hl: 0.6 },
];

const CSS = `
.vk *{box-sizing:border-box}
.vk button{font-family:inherit}
@keyframes fadeIn{from{opacity:0}to{opacity:1}}
.press{transition:transform .14s cubic-bezier(.3,1.5,.5,1),background .25s,border-color .25s,color .25s,box-shadow .25s}
.press:active{transform:scale(.96)}
`;

/* ═══════════════ the simulated mixer around the keyboard ═══════════════ */

function Mixer({ dim }) {
  const tr = [
    { n: "S90 Full Grand", c: "#E68A29", v: 0.78, on: true }, { n: "Motif XF Galaxy DX", c: "#36B582", v: 0.5, on: true },
    { n: "Analog Warm Pad", c: "#3B82F6", v: 0.42, on: true }, { n: "Organ B3 Fast", c: "#9333EA", v: 0.6, on: true },
    { n: "Full Strings", c: "#7B859A", v: 0.68, on: false }, { n: "Ebow GTR Pad", c: "#7B859A", v: 0.82, on: false },
  ];
  const pill = { background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.05)", borderRadius: 12, color: "#C9D2E2", fontSize: 11, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center" };
  return (
    <div style={{ position: "absolute", inset: 0 }}>
      <div style={{ position: "absolute", left: 5, right: 5, top: 3, height: 38, display: "flex", gap: 5 }}>
        <div style={{ ...pill, width: 97 }}>TRANS 0</div><div style={{ ...pill, width: 97 }}>OCT 0</div>
        <div style={{ ...pill, flex: 1, color: "#566075", justifyContent: "flex-start", padding: "0 14px", gap: 24 }}><span>– – –</span><span style={{ fontWeight: 600 }}>Waiting for notes…</span></div>
        <div style={{ ...pill, width: 38, background: "linear-gradient(180deg,#D2587C,#B03F63)" }} />
        {[56, 38, 38, 38, 38].map((w, i) => <div key={i} style={{ ...pill, width: w }} />)}
      </div>
      {tr.map((t, i) => (
        <div key={i} style={{ position: "absolute", left: 5 + i * 132, top: 45, width: 126, height: 238, borderRadius: 18, overflow: "hidden", background: "linear-gradient(180deg,#232C3E,#161C2A 55%)", border: "1px solid rgba(255,255,255,.05)" }}>
          {t.on && <div style={{ position: "absolute", inset: 0, background: `radial-gradient(130% 42% at 50% 100%, ${rgba(t.c, 0.2)}, transparent 72%)` }} />}
          <div style={{ position: "absolute", left: 6, right: 6, top: 8, height: 24, borderRadius: 9, background: "#1B2232", border: "1px solid rgba(255,255,255,.06)", display: "grid", placeItems: "center", fontSize: 10.5, fontWeight: 800, color: t.on ? "#E9EDF5" : "#8F9AB0" }}>{t.n}</div>
          <div style={{ position: "absolute", left: 6, right: 6, top: 40, height: 24, display: "flex", gap: 5 }}>
            <div style={{ width: 24, borderRadius: 12, background: "#171D2B" }} /><div style={{ flex: 1, borderRadius: 8, background: "#2D364D" }} /><div style={{ flex: 1, borderRadius: 8, background: "#2D364D" }} />
          </div>
          <div style={{ position: "absolute", left: 51, width: 24, top: 74, height: 130, borderRadius: 12, background: "#2E3347" }}>
            <div style={{ position: "absolute", left: 9, top: 6, bottom: 6, width: 6, borderRadius: 3, background: "#14161F" }} />
            {t.on && <div style={{ position: "absolute", left: 9.5, width: 5, bottom: 6, top: `${(1 - t.v) * 100}%`, borderRadius: 3, background: t.c }} />}
          </div>
          <div style={{ position: "absolute", left: 40, width: 46, top: 74 + (1 - t.v) * 100, height: 30, borderRadius: 7, background: "linear-gradient(180deg,#6E7799,#3F4766)" }} />
          <div style={{ position: "absolute", left: 5, right: 5, bottom: 6, height: 22, display: "flex", gap: 4 }}>
            <div style={{ flex: 2, borderRadius: 7, background: "#2E3549" }} /><div style={{ flex: 2, borderRadius: 7, background: "#2E3549" }} />
            <div style={{ flex: 1.1, borderRadius: 7, background: t.on ? t.c : "#232838" }} />
          </div>
        </div>
      ))}
      <div style={{ position: "absolute", left: 797, top: 45, width: 100, height: 238, borderRadius: 18, border: "1.5px solid rgba(255,255,255,.12)" }} />
      <div style={{ position: "absolute", left: 5, right: 5, top: 288, height: 44, display: "flex", gap: 5 }}>
        <div style={{ ...pill, width: 80 }}>CPU 15%</div><div style={{ ...pill, width: 80 }}>120 BPM</div>
        <div style={{ ...pill, width: 96, background: "linear-gradient(180deg,#A6C5F1,#86AAE3)", color: "#0F1726", fontSize: 13 }}>Default</div>
        <div style={{ ...pill, width: 96 }}>SNAPSHOT 3</div><div style={{ ...pill, width: 34 }}>+</div>
        <div style={{ flex: 1 }} /><div style={{ ...pill, width: 40 }} /><div style={{ ...pill, width: 190 }}>MASTER 60%</div><div style={{ ...pill, width: 44 }} />
      </div>
      <div style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.55)", opacity: dim ? 1 : 0, transition: "opacity .45s", pointerEvents: "none" }} />
    </div>
  );
}

/* ═══════════════ pitch bend ═══════════════ */

function PitchBend({ th, h }) {
  const [v, setV] = useState(0);
  const [drag, setDrag] = useState(false);
  const ref = useRef(null);
  const set = (e) => { const r = ref.current.getBoundingClientRect(); setV(clamp(1 - ((e.clientY - r.top) / r.height) * 2, -1, 1)); };
  const pad = 11, travel = h / 2 - pad;
  return (
    <div ref={ref}
      onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); setDrag(true); set(e); }}
      onPointerMove={(e) => { if (drag) set(e); }}
      onPointerUp={() => { setDrag(false); setV(0); }}
      style={{ position: "relative", width: 34, height: h, flex: "none", borderRadius: 10, background: WELL, boxShadow: "inset 0 2px 7px rgba(0,0,0,.65), 0 0 0 1px rgba(255,255,255,.06)", touchAction: "none", cursor: "ns-resize", transition: "height .55s cubic-bezier(.2,1.1,.3,1)" }}>
      <div style={{ position: "absolute", left: 16, width: 2, top: pad, bottom: pad, borderRadius: 1, background: "rgba(255,255,255,.1)" }} />
      <div style={{ position: "absolute", left: 16, width: 2, borderRadius: 1, background: th.press, top: v >= 0 ? `calc(50% - ${v * travel}px)` : "50%", height: Math.abs(v) * travel, transition: drag ? "none" : "all .38s cubic-bezier(.3,1.6,.5,1)" }} />
      <div style={{ position: "absolute", left: 5, right: 5, height: 11, borderRadius: 6, top: `calc(50% - 5.5px - ${v * travel}px)`, background: "linear-gradient(180deg,#FFFFFF,#CED3DE)", boxShadow: "0 2px 4px rgba(0,0,0,.5), inset 0 1px 0 #fff", transition: drag ? "none" : "top .38s cubic-bezier(.3,1.6,.5,1)" }}>
        <div style={{ position: "absolute", left: 5, right: 5, top: 5, height: 1, background: "rgba(0,0,0,.25)" }} />
      </div>
    </div>
  );
}

/* ═══════════════ keyboard (NeoTune-style relief, no indicators on the keys) ═══════════════ */

function Keys({ th, led, down, onDown, onUp, onEnter }) {
  return (
    <svg viewBox={`0 0 ${RW} ${KH}`} width="100%" height={KH} style={{ display: "block", overflow: "visible", touchAction: "none" }}>
      <defs>
        <linearGradient id="kw" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor={th.w[0]} /><stop offset=".72" stopColor={th.w[1]} /><stop offset="1" stopColor={th.w[2]} /></linearGradient>
        <linearGradient id="kb" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor={th.b[0]} /><stop offset=".55" stopColor={th.b[1]} /><stop offset="1" stopColor={th.b[2]} /></linearGradient>
        <linearGradient id="kbf" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#fff" stopOpacity="0" /><stop offset="1" stopColor="#fff" stopOpacity=".13" /></linearGradient>
        <filter id="kws" x="-10%" y="-5%" width="120%" height="115%"><feDropShadow dx="0" dy="1" stdDeviation=".7" floodColor="#000" floodOpacity=".5" /></filter>
        <filter id="kbs" x="-30%" y="-5%" width="160%" height="125%"><feDropShadow dx="0" dy="2.2" stdDeviation="1.3" floodColor="#000" floodOpacity=".65" /></filter>
      </defs>
      {WHITES.map((m) => {
        const x = wx(m) + 0.45, w = WW - 0.9, on = down[m], dy = on ? 1 : 0;
        return (
          <g key={m} onPointerDown={(e) => { e.currentTarget.releasePointerCapture && e.currentTarget.releasePointerCapture(e.pointerId); onDown(m); }} onPointerUp={() => onUp(m)} onPointerLeave={() => onUp(m)} onPointerEnter={(e) => { if (e.buttons) onDown(m); }}
            style={{ cursor: "pointer", filter: on && led ? `drop-shadow(0 0 5px ${th.press})` : "none" }}>
            <rect x={x} y={dy} width={w} height={KH} rx="2.6" fill={on ? th.press : "url(#kw)"} stroke={th.edge} strokeOpacity=".55" strokeWidth=".6" filter="url(#kws)" />
            <rect x={x + 0.9} y={dy + 0.9} width={w - 1.8} height="2" rx="1" fill="#fff" opacity={on ? 0.35 : (th.hl ?? 0.8)} />
            <rect x={x + 1} y={dy + KH - 7} width={w - 2} height="6" rx="2" fill="#000" opacity={on ? 0.1 : 0.06} />
            {on && <rect x={x} y={dy} width={w} height="7" rx="2.6" fill="#000" opacity=".14" />}
            {m % 12 === 0 && <text x={x + w / 2} y={KH - 9} fontSize="7" textAnchor="middle" fontFamily={MONO} fontWeight="700" fill={on ? "#fff" : th.label} opacity=".9">{mName(m)}</text>}
          </g>
        );
      })}
      <g filter="url(#kbs)">
        {BLACKS.map((m) => {
          const c = bx(m), l = c - BW / 2, on = down[m], dy = on ? 0.8 : 0;
          return (
            <g key={m} onPointerDown={(e) => { e.currentTarget.releasePointerCapture && e.currentTarget.releasePointerCapture(e.pointerId); onDown(m); }} onPointerUp={() => onUp(m)} onPointerLeave={() => onUp(m)} onPointerEnter={(e) => { if (e.buttons) onDown(m); }}
              style={{ cursor: "pointer", filter: on && led ? `drop-shadow(0 0 5px ${th.pressB})` : "none" }}>
              <rect x={l} y={dy} width={BW} height={BH} rx="2.2" fill={on ? th.pressB : "url(#kb)"} stroke="rgba(0,0,0,.55)" strokeWidth=".6" />
              <rect x={l + 1.2} y={dy + 1.2} width={BW - 2.4} height="2.2" rx="1" fill="#fff" opacity={on ? 0.28 : 0.15} />
              <rect x={l + 0.8} y={dy + BH - 9} width={BW - 1.6} height="8.2" rx="1.6" fill="url(#kbf)" />
              <line x1={l + 0.9} x2={l + 0.9} y1={dy + 3} y2={dy + BH - 4} stroke="#fff" strokeOpacity=".12" />
            </g>
          );
        })}
      </g>
    </svg>
  );
}

/* ═══════════════ main ═══════════════ */

const REST = { h: 2.6, gap: 0.5, zone: 5 };
const OPEN = { h: 29, gap: 3.2, zone: 25 };

export default function VirtualKeyboardPreview() {
  const [themeId, setThemeId] = useState("classic");
  const [led, setLed] = useState(false);                       // pressed keys: simple color by default
  const [open, setOpen] = useState(false);
  const [ranges, setRanges] = useState(TRACKS.map(() => ({ low: RMIN, high: RMAX })));
  const [drag, setDrag] = useState(null);
  const [down, setDown] = useState({});
  const [touch, setTouch] = useState(0);
  const colRef = useRef(null);
  const th = THEMES.find((t) => t.id === themeId);
  const g = open ? OPEN : REST;

  /* fold back by itself after a few quiet seconds */
  useEffect(() => {
    if (!open || drag) return;
    const t = setTimeout(() => setOpen(false), 5000);
    return () => clearTimeout(t);
  }, [open, drag, touch]);

  const xOf = (e) => { const r = colRef.current.getBoundingClientRect(); return ((e.clientX - r.left) * RW) / r.width; };
  const barL = (i) => cx(ranges[i].low) - WW / 2;
  const barR = (i) => cx(ranges[i].high) + WW / 2;

  const startDrag = (e, i, type) => {
    e.stopPropagation();
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({ i, type, x0: xOf(e), orig: { ...ranges[i] } });
    setTouch((t) => t + 1); vib(6);
  };
  const moveDrag = (e) => {
    if (!drag) return;
    const x = xOf(e), { i, type, orig, x0 } = drag;
    setRanges((rs) => rs.map((r, k) => {
      if (k !== i) return r;
      if (type === "low") return { ...r, low: clamp(nearest(x - 12 + WW / 2), RMIN, r.high - 1) };
      if (type === "high") return { ...r, high: clamp(nearest(x + 12 - WW / 2), r.low + 1, RMAX) };
      const wdt = orig.high - orig.low, d = Math.round((x - x0) / PX_PER_SEMI);
      const low = clamp(orig.low + d, RMIN, RMAX - wdt);
      return { low, high: low + wdt };
    }));
  };
  const endDrag = () => setDrag(null);
  const press = (m) => { setDown((d) => (d[m] ? d : { ...d, [m]: true })); setTouch((t) => t + 1); };
  const release = (m) => setDown((d) => { if (!d[m]) return d; const n = { ...d }; delete n[m]; return n; });

  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });
  useEffect(() => { const on = () => setVp({ w: window.innerWidth, h: window.innerHeight }); window.addEventListener("resize", on); return () => window.removeEventListener("resize", on); }, []);
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 200) / H, 1.5));
  const portrait = vp.h > vp.w;
  const ease = "cubic-bezier(.2,1.12,.3,1)";

  return (
    <div className="vk" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
          <Mixer dim={open} />

          {/* the container: pitch bend + lines + keyboard */}
          <div onPointerDown={() => setTouch((t) => t + 1)} style={{ position: "absolute", left: 5, right: 5, bottom: 0, borderRadius: 16, padding: "6px 6px 5px", background: FRAME, border: "1px solid rgba(255,255,255,.07)", boxShadow: open ? "0 -14px 34px rgba(0,0,0,.55)" : "0 -4px 12px rgba(0,0,0,.35)", display: "flex", gap: 4, alignItems: "flex-end", transition: "background .4s, box-shadow .4s" }}>
            <PitchBend th={th} h={open ? 74 : 46} />

            <div ref={colRef} style={{ position: "relative", width: RW, flex: "none" }} onPointerMove={moveDrag} onPointerUp={endDrag} onPointerCancel={endDrag}>
              {/* grip, only when open: tap to fold */}
              <div onClick={() => setOpen(false)} style={{ position: "absolute", left: "50%", top: -5, width: 44, height: 12, marginLeft: -22, display: "grid", placeItems: "center", cursor: "pointer", opacity: open ? 1 : 0, pointerEvents: open ? "auto" : "none", transition: "opacity .3s" }}>
                <span style={{ width: 30, height: 3.5, borderRadius: 2, background: "rgba(255,255,255,.28)" }} />
              </div>

              {/* octave marks, behind the lines */}
              <div style={{ position: "absolute", left: 0, right: 0, top: 0, bottom: KH, pointerEvents: "none" }}>
                {KEYS.filter((m) => m % 12 === 0).map((m) => <span key={m} style={{ position: "absolute", left: wx(m), top: 0, bottom: 0, width: 1, background: "rgba(255,255,255,.1)", opacity: open ? 1 : 0.5, transition: "opacity .4s" }} />)}
              </div>

              {/* layer lines: thin at rest, they unfold to full size when touched */}
              <div onClick={() => { if (!open) { setOpen(true); vib(8); } }} style={{ position: "relative", cursor: open ? "default" : "pointer", paddingTop: open ? 2 : 3, transition: `padding .5s ${ease}` }}>
                {TRACKS.map((t, i) => {
                  const L = barL(i), R = barR(i), act = drag && drag.i === i;
                  const dly = open ? i * 45 : (TRACKS.length - 1 - i) * 30;
                  const hTr = `height .6s ${ease} ${dly}ms, margin-top .6s ${ease} ${dly}ms, border-radius .5s ease ${dly}ms`;
                  return (
                    <div key={t.name} style={{ position: "relative", height: g.h, marginTop: i ? g.gap : 0, transition: hTr }}>
                      <div onPointerDown={(e) => { if (open) startDrag(e, i, "move"); }}
                        style={{ position: "absolute", left: L, width: R - L, top: 0, height: "100%", background: t.color, borderRadius: open ? 10 : 2, cursor: open ? "grab" : "pointer", touchAction: "none", transform: act ? "translateY(-1px)" : "none", boxShadow: act ? "0 6px 14px rgba(0,0,0,.45)" : "none", transition: `${hTr}, transform .15s, box-shadow .15s` }}>
                        <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", gap: 8, opacity: open ? 1 : 0, transition: `opacity .3s ease ${open ? 260 + dly : 0}ms`, pointerEvents: "none", whiteSpace: "nowrap" }}>
                          <span style={{ padding: "1px 7px", borderRadius: 7, background: "rgba(10,14,22,.82)", fontFamily: MONO, fontSize: 10.5, fontWeight: 800, color: "#fff" }}>{mName(ranges[i].low)}</span>
                          <span style={{ fontSize: 12.5, fontWeight: 800, color: "#fff", textShadow: "0 1px 1px rgba(0,0,0,.25)" }}>{t.name}</span>
                          <span style={{ padding: "1px 7px", borderRadius: 7, background: "rgba(10,14,22,.82)", fontFamily: MONO, fontSize: 10.5, fontWeight: 800, color: "#fff" }}>{mName(ranges[i].high)}</span>
                        </div>
                      </div>
                      {[["low", L + 12], ["high", R - 12]].map(([k, x]) => (
                        <div key={k} onPointerDown={(e) => { if (open) startDrag(e, i, k); }}
                          style={{ position: "absolute", left: x - 11, top: "50%", width: 22, height: 22, marginTop: -11, display: "grid", placeItems: "center", cursor: "ew-resize", touchAction: "none", opacity: open ? 1 : 0, pointerEvents: open ? "auto" : "none", transform: open ? "scale(1)" : "scale(.3)", transition: `opacity .25s ease ${open ? 280 + dly : 0}ms, transform .45s ${ease} ${open ? 260 + dly : 0}ms` }}>
                          <span style={{ width: act ? 14 : 12, height: act ? 14 : 12, borderRadius: 7, background: "#fff", display: "grid", placeItems: "center", boxShadow: "0 1px 3px rgba(0,0,0,.35)", transition: "all .15s" }}><span style={{ width: 5, height: 5, borderRadius: 3, background: "#0F1726" }} /></span>
                        </div>
                      ))}
                    </div>
                  );
                })}
                <div style={{ height: g.zone, transition: `height .6s ${ease}` }} />
              </div>

              {/* keys */}
              <div style={{ position: "relative", height: KH, borderRadius: 4 }}>
                <Keys th={th} led={led} down={down} onDown={press} onUp={release} />
                {/* selection halo, only while a line is being moved */}
                {TRACKS.map((t, i) => (
                  <div key={t.name} style={{ position: "absolute", left: barL(i), width: barR(i) - barL(i), top: 0, height: KH, background: rgba(t.color, 0.4), borderRadius: 3, opacity: drag && drag.i === i ? 1 : 0, transition: "opacity .16s", pointerEvents: "none" }} />
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* preview helpers */}
      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", maxWidth: 780 }}>
        <button onClick={() => setOpen((o) => !o)} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>{open ? "Fold the lines" : "Unfold the lines"}</button>
        <button onClick={() => setRanges(TRACKS.map(() => ({ low: RMIN, high: RMAX })))} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Reset ranges</button>
        <span style={{ fontSize: 12, color: "#7B859A", marginLeft: 8 }}>Pressed keys</span>
        {[[false, "Color"], [true, "LED"]].map(([v, l]) => (
          <button key={l} onClick={() => setLed(v)} style={{ padding: "7px 14px", borderRadius: 999, border: `1px solid ${led === v ? "#8FB2E8" : "#2A3446"}`, background: led === v ? "rgba(143,178,232,.16)" : "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>{l}</button>
        ))}
      </div>
      <div style={{ marginTop: 10, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", maxWidth: 780 }}>
        {THEMES.map((t) => (
          <button key={t.id} onClick={() => setThemeId(t.id)} style={{ padding: "6px 8px 5px", borderRadius: 12, border: `1px solid ${themeId === t.id ? "#8FB2E8" : "#2A3446"}`, background: themeId === t.id ? "rgba(143,178,232,.12)" : "#121823", cursor: "pointer", display: "flex", flexDirection: "column", alignItems: "center", gap: 4, minWidth: 64 }}>
            <svg width="46" height="26" viewBox="0 0 46 26">
              <rect width="46" height="26" rx="5" fill={FRAME} />
              {[0, 1, 2, 3].map((k) => <rect key={k} x={3 + k * 10.5} y="4" width="9.5" height="19" rx="1.6" fill={k === 1 ? t.press : t.w[1]} />)}
              {[0, 1, 3].map((k) => <rect key={k} x={9 + k * 10.5} y="4" width="6" height="11" rx="1.2" fill={t.b[1]} />)}
            </svg>
            <span style={{ fontSize: 11, fontWeight: 700, color: "#C9D2E2" }}>{t.name}</span>
          </button>
        ))}
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 680, lineHeight: 1.55 }}>
        Tap the thin lines to unfold them (they fold back after a few quiet seconds, or tap the grip) · drag a white handle to change a limit, or drag the bar to slide the whole range: a halo of the line color lights the keys while you move it · pitch bend springs back to center · the theme and the pressed-key style are preview helpers here
        {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
      </div>
    </div>
  );
}
