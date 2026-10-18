import { useState, useRef, useEffect } from "react";

/* ─────────────────────────────────────────────────────────────
   DRUMPAD v2 — landscape smartphone preview (891 × 411 dp)
   Everything inside the device frame is the real design, in English.
   Controls under the frame are preview helpers only.
   No sound is produced: hits, drones and loops are visual and timed.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const AC = "#8FB2E8";
const WARM = "#D8A24A";
const ROSE = "#C2587A";
const BPM = 116;
const BAR = (4 * 60000) / BPM;          // one bar in ms
const BEAT = BAR / 4;
const T0 = typeof performance !== "undefined" ? performance.now() : 0;
const nextBoundary = (now) => T0 + (Math.floor((now - T0) / BAR) + 1) * BAR;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";

/* mature palette, same luminosity: steel, plum, dusty rose, sand, sage, teal, copper, slate */
const PALETTE = ["#7F9BC7", "#9A86C2", "#C27A8E", "#C9A15C", "#86A394", "#6FB3A8", "#B58E6B", "#8D97B8"];
const NOTES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
const viv = (i, a = 1) => `hsl(${(i * 30 + 205) % 360} 84% 61% / ${a})`;   // vivid, one hue per note

const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2'/></filter><rect width='100%' height='100%' filter='url(#n)' opacity='.55'/></svg>")}")`;

const INIT_PADS = [
  { name: "Tambourin N", vol: 100, mode: "one", sample: "Tambourin N" },
  { name: "Chimes 2", vol: 84, mode: "one", sample: "Chimes 2" },
  { name: "Empty", vol: 71, mode: "one", sample: null },
  { name: "Empty", vol: 88, mode: "one", sample: null },
  { name: "Kick Club", vol: 78, mode: "one", sample: "Kick Club" },
  { name: "Clap Tight", vol: 74, mode: "one", sample: "Clap Tight" },
  { name: "Ambient Texture", vol: 65, mode: "loop", sample: "Ambient Texture" },
  { name: "Hat Closed", vol: 92, mode: "one", sample: "Hat Closed" },
];
const FOLDERS0 = [{ id: "drums", name: "Drums" }, { id: "perc", name: "Percussion" }, { id: "loops", name: "Loops" }, { id: "imp", name: "Imported" }];
const SAMPLES0 = [
  ["Kick Club", "drums", "0:01", "84 KB"], ["Kick Soft", "drums", "0:01", "61 KB"], ["Snare Crack", "drums", "0:01", "72 KB"], ["Snare Rim", "drums", "0:01", "40 KB"],
  ["Clap Tight", "drums", "0:01", "55 KB"], ["Hat Closed", "drums", "0:01", "22 KB"], ["Hat Open", "drums", "0:02", "48 KB"], ["Tom Low", "drums", "0:01", "66 KB"],
  ["Tambourin N", "perc", "0:02", "120 KB"], ["Chimes 2", "perc", "0:03", "210 KB"], ["Shaker Light", "perc", "0:01", "34 KB"], ["Conga High", "perc", "0:01", "58 KB"], ["Cowbell", "perc", "0:01", "30 KB"],
  ["Groove 90 bpm", "loops", "0:21", "1,9 MB"], ["Funk Break", "loops", "0:08", "740 KB"], ["Ambient Texture", "loops", "0:16", "1,4 MB"],
].map(([name, folder, dur, size], i) => ({ id: "s" + i, name, folder, dur, size }));
const KITS0 = [
  { id: "dk", name: "DrumKit", s: ["Tambourin N", "Chimes 2", null, null, "Kick Club", "Clap Tight", "Ambient Texture", "Hat Closed"] },
  { id: "lo", name: "Lo-Fi Kit", s: ["Kick Soft", "Snare Rim", "Hat Open", "Shaker Light", "Tom Low", "Clap Tight", "Cowbell", "Conga High"] },
  { id: "pc", name: "Percussion Set", s: ["Conga High", "Shaker Light", "Cowbell", "Tambourin N", "Chimes 2", "Tom Low", "Hat Closed", null] },
];

const CSS = `
.dp *{box-sizing:border-box}
.dp button{font-family:inherit}
@keyframes ringOut{0%{transform:scale(.35);opacity:.8}100%{transform:scale(2.7);opacity:0}}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes armed{0%,100%{opacity:.35}50%{opacity:1}}
@keyframes breathe{0%,100%{opacity:.45}50%{opacity:1}}
@keyframes drone{0%,100%{box-shadow:0 0 0 0 var(--g)}50%{box-shadow:0 0 18px 2px var(--g)}}
@keyframes recPulse{0%,100%{opacity:.55}50%{opacity:1}}
@keyframes rise{from{opacity:0;translate:0 12px;scale:.94}to{opacity:1;translate:0 0;scale:1}}
@keyframes fadeIn{from{opacity:0}to{opacity:1}}
@keyframes menuIn{from{opacity:0;scale:.95}to{opacity:1;scale:1}}
@keyframes sheetIn{from{opacity:0;translate:0 22px}to{opacity:1;translate:0 0}}
@keyframes fill{from{width:0}to{width:100%}}
@keyframes bar{0%,100%{transform:scaleY(.35)}50%{transform:scaleY(1)}}
.press{transition:transform .14s cubic-bezier(.3,1.5,.5,1),background .25s,box-shadow .25s,border-color .25s,opacity .25s}
.press:active{transform:scale(.97)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

function Ico({ n, size = 16, color = "currentColor", sw = 2 }) {
  const P = {
    gear: <><circle cx="12" cy="12" r="3" /><path d="M12 3v2.2M12 18.8V21M3 12h2.2M18.8 12H21M5.6 5.6l1.6 1.6M16.8 16.8l1.6 1.6M5.6 18.4l1.6-1.6M16.8 7.2l1.6-1.6" /></>,
    mix: <><path d="M5 4v16M12 4v16M19 4v16" /><circle cx="5" cy="14" r="2" fill="currentColor" /><circle cx="12" cy="8" r="2" fill="currentColor" /><circle cx="19" cy="12" r="2" fill="currentColor" /></>,
    play: <path d="M8 5l11 7-11 7z" fill="currentColor" />,
    stop: <rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor" />,
    x: <path d="M6 6l12 12M18 6L6 18" />,
    plus: <path d="M12 5v14M5 12h14" />,
    check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
    chev: <path d="M9 6l6 6-6 6" />,
    folder: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />,
    wave: <path d="M3 12h2M7 8v8M11 4v16M15 8v8M19 10v4" />,
    search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">{P[n]}</svg>;
}

function Roll({ value }) {
  const prev = useRef(value);
  const [k, setK] = useState(0);
  useEffect(() => { if (prev.current !== value) { prev.current = value; setK((a) => a + 1); } }, [value]);
  return <span key={k} style={{ display: "inline-block", animation: k ? "rise .22s ease-out" : "none" }}>{value}</span>;
}

/* ── generic bits ── */
function Seg({ options, value, onChange, w, h = 22, warn }) {
  const idx = Math.max(0, options.findIndex((o) => o[0] === value));
  return (
    <div style={{ position: "relative", display: "flex", padding: 3, borderRadius: 12, background: "rgba(15,20,29,.8)", border: "1px solid rgba(255,255,255,.08)", width: w }}>
      <div style={{ position: "absolute", top: 3, bottom: 3, left: 3, width: `calc((100% - 6px) / ${options.length})`, borderRadius: 9, background: "linear-gradient(180deg,#38445E,#2B3650)", boxShadow: "0 2px 6px rgba(0,0,0,.4)", transform: `translateX(${idx * 100}%)`, transition: "transform .32s cubic-bezier(.3,1.35,.5,1)" }} />
      {options.map(([k, l]) => (
        <button key={String(k)} onClick={() => onChange(k)} style={{ position: "relative", flex: 1, height: h, border: 0, background: "transparent", cursor: "pointer", fontSize: 11.5, fontWeight: 800, color: value === k ? (warn && k === "edit" ? WARM : "#F1F4FA") : "#8690A4", transition: "color .25s" }}>{l}</button>
      ))}
    </div>
  );
}

function TopBar() {
  const Pill = ({ children, style }) => (
    <div style={{ height: 40, borderRadius: 13, padding: "0 14px", display: "flex", alignItems: "center", gap: 8, background: "linear-gradient(180deg,#2A3347,#202839)", border: "1px solid rgba(255,255,255,.08)", boxShadow: "0 4px 10px rgba(0,0,0,.3)", color: "#E3E8F2", fontSize: 14, fontWeight: 700, ...style }}>{children}</div>
  );
  return (
    <div style={{ height: 52, display: "flex", alignItems: "center", gap: 8, padding: "0 10px" }}>
      <Pill style={{ width: 40, padding: 0, justifyContent: "center" }}><Ico n="gear" size={17} /></Pill>
      <Pill><span>DrumPad</span><span style={{ color: AC }}>›</span></Pill>
      <div style={{ flex: 1 }} />
      <Pill>Swing 54%</Pill>
      <Pill style={{ padding: "0 6px" }}><span style={{ width: 28, height: 28, borderRadius: 14, background: "#202839", display: "grid", placeItems: "center" }}>−</span><span style={{ fontFamily: MONO, minWidth: 74, textAlign: "center" }}>{BPM} BPM</span><span style={{ width: 28, height: 28, borderRadius: 14, background: "#202839", display: "grid", placeItems: "center" }}>+</span></Pill>
      <Pill><Ico n="mix" size={15} /> Mix</Pill>
    </div>
  );
}

/* ═══════════════ TonicPad: persistent drones ═══════════════ */

function NoteKey({ label, oct, idx, on, ghost, onToggle }) {
  const [down, setDown] = useState(false);
  const [rings, setRings] = useState([]);
  const burst = (e) => {
    const r = e.currentTarget.getBoundingClientRect();
    const id = Math.random();
    setRings((rs) => [...rs, { id, x: e.clientX - r.left, y: e.clientY - r.top }]);
    setTimeout(() => setRings((rs) => rs.filter((x) => x.id !== id)), 560);
  };
  return (
    <button
      onPointerDown={(e) => { setDown(true); burst(e); onToggle(); }}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      style={{
        position: "relative", overflow: "hidden", border: 0, borderRadius: 13, cursor: "pointer", touchAction: "none", "--g": viv(idx, 0.6),
        color: on ? "#FFFFFF" : viv(idx, 1), fontSize: 13.5, fontWeight: 800, textShadow: on ? "0 1px 2px rgba(0,0,0,.45)" : "none",
        background: on ? `linear-gradient(180deg, ${viv(idx, 0.98)}, ${viv(idx, 0.66)})` : `linear-gradient(180deg, ${viv(idx, 0.26)}, #1B2336 92%)`,
        boxShadow: on ? `inset 0 1px 0 rgba(255,255,255,.35)` : ghost ? `inset 0 0 0 1.5px ${viv(idx, 0.9)}` : `inset 0 1px 0 rgba(255,255,255,.1), inset 0 0 0 1px ${viv(idx, 0.38)}, 0 3px 8px rgba(0,0,0,.35)`,
        transform: down ? "translateY(2px) scale(.965)" : "none",
        transition: "transform .14s cubic-bezier(.3,1.5,.5,1), background .35s, box-shadow .35s, color .3s",
        animation: on ? "drone 3.4s ease-in-out infinite" : "none",
      }}>
      <span style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.06, mixBlendMode: "overlay", pointerEvents: "none" }} />
      {rings.map((r) => <span key={r.id} style={{ position: "absolute", left: r.x - 22, top: r.y - 22, width: 44, height: 44, borderRadius: 22, border: `1.5px solid ${on ? "#fff" : viv(idx, 1)}`, pointerEvents: "none", animation: "ringOut .55s ease-out forwards" }} />)}
      <span style={{ position: "relative" }}>{label}</span>
      {on && <span style={{ position: "absolute", right: 6, bottom: 3, fontSize: 8.5, fontFamily: MONO, color: "rgba(255,255,255,.85)", fontWeight: 800 }}><Roll value={oct} /></span>}
    </button>
  );
}

function TonicPad({ drones, onToggle, onClear, multi, onMulti, octave, onOct, ghostNotes, now }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, height: "100%" }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 5, flex: 1 }}>
        {NOTES.map((n, i) => (
          <NoteKey key={n} label={n} oct={octave} idx={i} on={drones.includes(n)} ghost={now - (ghostNotes[n] || -9999) < 420} onToggle={() => onToggle(n)} />
        ))}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 5, height: 28 }}>
        <span style={{ fontSize: 11, color: "#7B859A", fontWeight: 700 }}>Oct</span>
        {[["−", -1], ["+", 1]].map(([l, d]) => (
          <button key={l} onClick={() => onOct(d)} className="press" style={{ width: 24, height: 24, borderRadius: 8, border: "1px solid rgba(255,255,255,.1)", background: "#243047", color: "#E3E8F2", fontSize: 13, fontWeight: 700, cursor: "pointer", display: "grid", placeItems: "center" }}>{l}</button>
        )).reduce((a, b, i) => (i === 0 ? [a, <span key="o" style={{ fontFamily: MONO, fontSize: 13, fontWeight: 800, minWidth: 12, textAlign: "center" }}><Roll value={octave} /></span>, b] : a.concat(b)), [])}
        <div style={{ flex: 1 }} />
        <button onClick={onMulti} className="press" style={{ height: 24, padding: "0 8px", borderRadius: 8, border: `1px solid ${multi ? "rgba(143,178,232,.55)" : "rgba(255,255,255,.1)"}`, background: multi ? "rgba(143,178,232,.14)" : "transparent", color: multi ? AC : "#8690A4", fontSize: 10.5, fontWeight: 800, cursor: "pointer" }}>Multi</button>
        <button onClick={onClear} className="press" style={{ height: 24, padding: "0 8px", borderRadius: 8, border: "1px solid rgba(255,255,255,.1)", background: "transparent", color: drones.length ? "#C7CFDD" : "#566075", fontSize: 10.5, fontWeight: 800, cursor: "pointer" }}>Clear</button>
      </div>
    </div>
  );
}

/* ═══════════════ 3D volume knob ═══════════════ */

function VolumeKnob({ value, onChange, size = 52, label = true }) {
  const drag = useRef(null);
  const angle = -135 + (value / 100) * 270;
  const arc = (a1, a2, r) => {
    const p = (a) => { const t = ((a - 90) * Math.PI) / 180; return [22 + r * Math.cos(t), 22 + r * Math.sin(t)]; };
    const [x1, y1] = p(a1), [x2, y2] = p(a2);
    return `M ${x1} ${y1} A ${r} ${r} 0 ${Math.abs(a2 - a1) > 180 ? 1 : 0} 1 ${x2} ${y2}`;
  };
  const id = useRef("k" + Math.random().toString(36).slice(2, 7)).current;
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
      <div
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); drag.current = { y: e.clientY, v: value }; }}
        onPointerMove={(e) => { if (drag.current) onChange(clamp(Math.round(drag.current.v + (drag.current.y - e.clientY) * 0.6), 0, 100)); }}
        onPointerUp={() => { drag.current = null; }}
        onDoubleClick={() => onChange(80)}
        style={{ position: "relative", width: size, height: size, flex: "none", cursor: "ns-resize", touchAction: "none" }}>
        <svg width={size} height={size} viewBox="0 0 44 44" style={{ position: "absolute", inset: 0 }}>
          <defs>
            <radialGradient id={id + "b"} cx="36%" cy="28%" r="78%"><stop offset="0" stopColor="#626C90" /><stop offset=".55" stopColor="#2C3550" /><stop offset="1" stopColor="#141A29" /></radialGradient>
            <linearGradient id={id + "a"} x1="0" x2="1"><stop offset="0" stopColor={AC} stopOpacity=".45" /><stop offset="1" stopColor={AC} /></linearGradient>
          </defs>
          <path d={arc(-135, 135, 19)} fill="none" stroke="rgba(255,255,255,.08)" strokeWidth="3" strokeLinecap="round" />
          <path d={arc(-135, angle, 19)} fill="none" stroke={`url(#${id}a)`} strokeWidth="3" strokeLinecap="round" />
          <circle cx="22" cy="23.6" r="14.5" fill="rgba(0,0,0,.5)" />
          <circle cx="22" cy="22" r="14" fill={`url(#${id}b)`} stroke="rgba(255,255,255,.14)" />
          <circle cx="22" cy="22" r="10.2" fill="none" stroke="rgba(255,255,255,.07)" />
          <ellipse cx="18" cy="15.5" rx="7" ry="3.6" fill="rgba(255,255,255,.1)" />
          <g transform={`rotate(${angle} 22 22)`}><rect x="20.8" y="9.4" width="2.4" height="6.6" rx="1.2" fill={AC} /></g>
        </svg>
      </div>
      {label && (
        <div>
          <div style={{ fontSize: 9.5, letterSpacing: 1.2, fontWeight: 700, color: "#7B859A" }}>VOLUME</div>
          <div style={{ fontFamily: MONO, fontSize: 17, fontWeight: 800, color: "#F1F4FA" }}>{value}<span style={{ fontSize: 10.5, color: "#7B859A" }}>%</span></div>
        </div>
      )}
    </div>
  );
}

/* ═══════════════ FX ═══════════════ */

function FxCase({ fx, onOpen }) {
  const on = Object.values(fx).filter((f) => f.on).length;
  const timer = useRef(null); const fired = useRef(false);
  return (
    <div
      onPointerDown={() => { fired.current = false; timer.current = setTimeout(() => { fired.current = true; vib(10); onOpen(); }, 420); }}
      onPointerUp={() => { clearTimeout(timer.current); if (!fired.current) onOpen(); }}
      onPointerLeave={() => clearTimeout(timer.current)}
      className="press"
      style={{ flex: 1, borderRadius: 14, cursor: "pointer", padding: "8px 10px", display: "flex", flexDirection: "column", justifyContent: "space-between", background: on ? "linear-gradient(180deg,#34304A,#262238)" : "linear-gradient(180deg,#2A3347,#1F2739)", boxShadow: on ? `inset 0 0 0 1px ${WARM}66` : "inset 0 1px 0 rgba(255,255,255,.07), 0 3px 8px rgba(0,0,0,.35)", touchAction: "none", userSelect: "none" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span style={{ fontSize: 12.5, fontWeight: 800, color: on ? "#F1F4FA" : "#9AA5BA" }}>FX</span>
        <span style={{ fontFamily: MONO, fontSize: 10.5, color: on ? WARM : "#566075", fontWeight: 800 }}>{on}/4</span>
      </div>
      <div style={{ display: "flex", gap: 5 }}>
        {Object.entries(fx).map(([k, f]) => <span key={k} style={{ flex: 1, height: 4, borderRadius: 2, background: f.on ? WARM : "rgba(255,255,255,.1)", transition: "background .25s", boxShadow: f.on ? `0 0 6px ${WARM}88` : "none" }} />)}
      </div>
    </div>
  );
}

function Sheet({ children, onClose, top = 56, w = 340 }) {
  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 30 }}>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.55)", animation: "fadeIn .2s" }} />
      <div style={{ position: "absolute", left: "50%", top, width: w, marginLeft: -w / 2, borderRadius: 20, padding: 14, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 20px 50px rgba(0,0,0,.55)", animation: "menuIn .2s ease-out" }}>{children}</div>
    </div>
  );
}
const CloseBtn = ({ onClick }) => <button onClick={onClick} style={{ width: 28, height: 28, borderRadius: 14, border: 0, background: "rgba(255,255,255,.07)", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={13} /></button>;

function FxSheet({ fx, setFx, onClose }) {
  return (
    <Sheet onClose={onClose}>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 10 }}><div style={{ flex: 1, fontSize: 15, fontWeight: 800, color: "#F1F4FA" }}>Pad effects</div><CloseBtn onClick={onClose} /></div>
      {Object.entries(fx).map(([k, f]) => (
        <div key={k} style={{ display: "flex", alignItems: "center", gap: 10, height: 40 }}>
          <button onClick={() => setFx((s) => ({ ...s, [k]: { ...s[k], on: !s[k].on } }))} style={{ width: 40, height: 22, borderRadius: 11, border: 0, cursor: "pointer", position: "relative", background: f.on ? WARM : "rgba(255,255,255,.14)", transition: "background .25s", flex: "none" }}>
            <span style={{ position: "absolute", top: 2, left: 2, width: 18, height: 18, borderRadius: 9, background: f.on ? "#1A1F2B" : "#C7CFDD", transform: `translateX(${f.on ? 18 : 0}px)`, transition: "transform .28s cubic-bezier(.3,1.4,.5,1)" }} />
          </button>
          <span style={{ width: 62, fontSize: 13, fontWeight: 700, color: "#E9EDF5" }}>{f.label}</span>
          <div onPointerDown={(e) => { const r = e.currentTarget.getBoundingClientRect(); const set = (x) => setFx((s) => ({ ...s, [k]: { ...s[k], amt: clamp(Math.round(((x - r.left) / r.width) * 100), 0, 100) } })); set(e.clientX); e.currentTarget.setPointerCapture(e.pointerId); e.currentTarget.onpointermove = (ev) => set(ev.clientX); e.currentTarget.onpointerup = () => { e.currentTarget.onpointermove = null; }; }}
            style={{ flex: 1, height: 22, borderRadius: 7, background: "#0F141D", position: "relative", cursor: "pointer", touchAction: "none", boxShadow: "inset 0 1px 3px rgba(0,0,0,.6)" }}>
            <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: `${f.amt}%`, borderRadius: 7, background: f.on ? `linear-gradient(90deg,${WARM}66,${WARM})` : "rgba(143,178,232,.25)", transition: "width .08s" }} />
          </div>
          <span style={{ width: 30, textAlign: "right", fontFamily: MONO, fontSize: 11.5, color: "#9AA5BA" }}>{f.amt}</span>
        </div>
      ))}
    </Sheet>
  );
}

/* ═══════════════ semi-3D pad ═══════════════ */

function LedRing({ color, mode, dur, radius = 18, width = 2.5 }) {
  return (
    <div style={{
      position: "absolute", inset: 0, borderRadius: radius, padding: width, pointerEvents: "none", overflow: "hidden",
      opacity: mode === "off" ? 0 : 1, transition: "opacity .45s",
      WebkitMask: "linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)", WebkitMaskComposite: "xor",
      mask: "linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)", maskComposite: "exclude",
    }}>
      <div style={{
        position: "absolute", inset: "-70%",
        background: mode === "run" ? `conic-gradient(from 0deg, transparent 0 55%, ${color} 100%)` : color,
        animation: mode === "run" ? `spin ${dur}s linear infinite` : mode === "queued" ? "armed .52s ease-in-out infinite" : "none",
      }} />
    </div>
  );
}

/* One single surface, like a rubber pad: soft top light, thin top edge, color floods the whole pad on a hit. */
function Pad({ index, pad, color, mode, runner, flash, ghost, queued, dragOver, onDown, onUp, appear }) {
  const [down, setDown] = useState(false);
  const [spec, setSpec] = useState([30, 20]);
  const [rings, setRings] = useState([]);
  const empty = !pad.sample;
  const ref = useRef(null);
  const place = (e) => {
    const r = ref.current.getBoundingClientRect();
    setSpec([clamp(((e.clientX - r.left) / r.width) * 100, 5, 95), clamp(((e.clientY - r.top) / r.height) * 100, 5, 95)]);
  };
  const burst = (e) => {
    const r = ref.current.getBoundingClientRect();
    const id = Math.random();
    setRings((rs) => [...rs, { id, x: e.clientX - r.left, y: e.clientY - r.top }]);
    setTimeout(() => setRings((rs) => rs.filter((x) => x.id !== id)), 700);
  };
  const lit = flash && !empty;
  const ledMode = queued ? "queued" : runner ? "run" : "off";
  return (
    <div ref={ref}
      onPointerDown={(e) => { setDown(true); place(e); if (!empty || mode === "edit") burst(e); onDown(index); }}
      onPointerMove={(e) => { if (down) place(e); }}
      onPointerUp={() => { setDown(false); onUp(index); }}
      onPointerLeave={() => setDown(false)}
      style={{
        position: "relative", overflow: "hidden", borderRadius: 18, cursor: "pointer", userSelect: "none", touchAction: "none",
        background: empty ? "linear-gradient(160deg,#1D2537,#141A28)" : `linear-gradient(160deg, ${color}40 0%, #1F2739 52%, #151B2A 100%)`,
        boxShadow: down ? "inset 0 2px 9px rgba(0,0,0,.55)" : `inset 0 1px 0 rgba(255,255,255,.15), 0 7px 14px rgba(0,0,0,.42)${dragOver ? `, 0 0 0 2px ${AC}` : ""}`,
        transform: down ? "translateY(2px) scale(.978)" : "none",
        transition: "transform .14s cubic-bezier(.3,1.5,.5,1), box-shadow .25s, background .4s",
        animation: appear ? `rise .42s cubic-bezier(.2,.9,.25,1) ${index * 34}ms backwards` : "none",
      }}>
      {/* soft light and grain, same surface (no visible edges) */}
      <div style={{ position: "absolute", inset: 0, background: "radial-gradient(120% 90% at 18% 0%, rgba(255,255,255,.11), transparent 55%)", pointerEvents: "none" }} />
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.07, mixBlendMode: "overlay", pointerEvents: "none" }} />
      {/* the color floods the pad from the touch point */}
      <div style={{ position: "absolute", inset: 0, background: `radial-gradient(110% 140% at ${spec[0]}% ${spec[1]}%, ${color}, ${color}99 55%, ${color}40)`, opacity: lit ? 0.95 : 0, transition: lit ? "opacity .04s" : "opacity .5s ease-out", pointerEvents: "none", mixBlendMode: "screen" }} />
      {ghost && <div style={{ position: "absolute", inset: 0, background: "radial-gradient(85% 85% at 50% 45%, rgba(143,178,232,.26), transparent 72%)", pointerEvents: "none", animation: "fadeIn .1s" }} />}
      {rings.map((r) => <span key={r.id} style={{ position: "absolute", left: r.x - 70, top: r.y - 70, width: 140, height: 140, borderRadius: 70, background: `radial-gradient(circle, ${color}aa, transparent 62%)`, pointerEvents: "none", animation: "ringOut .7s ease-out forwards" }} />)}
      <div style={{ position: "absolute", inset: 0, padding: "10px 12px 9px", display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 6 }}>
          <span style={{ fontSize: 13.5, fontWeight: 800, color: empty ? "#6F7A90" : "#F1F4FA", textShadow: lit ? "0 1px 2px rgba(0,0,0,.5)" : "none", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{empty ? "+ Assign" : pad.name}</span>
          {mode === "edit" && <span style={{ width: 22, height: 22, borderRadius: 11, background: "rgba(255,255,255,.1)", display: "grid", placeItems: "center", color: "#B5BFD3", flex: "none" }}><Ico n="chev" size={11} /></span>}
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", fontSize: 10.5, fontWeight: 700, color: lit ? "#fff" : "#8E99AE" }}>
          <span style={{ fontFamily: MONO }}>D{index + 1}{pad.mode === "loop" && !empty ? " · loop" : ""}</span>
          <span style={{ fontFamily: MONO }}>{empty ? "" : `${pad.vol}%`}</span>
        </div>
      </div>
      <LedRing color={color} mode={ledMode} dur={(BAR * 2) / 1000} />
    </div>
  );
}

/* ═══════════════ live looper case: orbit view, no waveform ═══════════════ */

const STATE_COLOR = { rec: ROSE, queued: WARM, play: AC, overdub: WARM, stopped: "#7B859A", empty: "#566075" };
const STATE_LABEL = { rec: "REC", queued: "QUEUED", play: "PLAY", overdub: "OVERDUB", stopped: "STOPPED", empty: "EMPTY" };

function LoopCase({ L, now, onTap, onHold, lenMode }) {
  const timer = useRef(null); const fired = useRef(false);
  const st = L.state;
  const col = st === "queued" ? (L.queued === "rec" ? ROSE : WARM) : STATE_COLOR[st];
  const ringBars = st === "rec" ? (lenMode === "auto" ? 1 : lenMode) : L.bars;
  const loopMs = L.bars * BAR;
  let head = null, barNow = 0;
  if (st === "play" || st === "overdub") {
    if (now >= L.startAt) { const ph = ((now - L.startAt) / loopMs) % 1; head = ph; barNow = Math.floor(ph * L.bars) + 1; }
  } else if (st === "rec") {
    const el = (now - L.recStart) / BAR;
    head = (el % ringBars) / ringBars; barNow = Math.floor(el) + 1;
  }
  const pt = (f, r) => { const a = f * Math.PI * 2 - Math.PI / 2; return [50 + r * Math.cos(a), 50 + r * Math.sin(a)]; };
  const layerRs = [38, 31, 24, 17];
  const arcPath = (f0, f1, r) => { const [x0, y0] = pt(f0, r), [x1, y1] = pt(f1, r); return `M ${x0} ${y0} A ${r} ${r} 0 ${f1 - f0 > 0.5 ? 1 : 0} 1 ${x1} ${y1}`; };
  const recEvents = st === "rec" ? L.tmp.map((e) => ({ f: (e.tb % ringBars) / ringBars, c: e.color })) : [];
  const beat = Math.floor(((now - T0) % BAR) / BEAT);
  return (
    <div
      onPointerDown={() => { fired.current = false; timer.current = setTimeout(() => { fired.current = true; vib(10); onHold(L.id); }, 480); }}
      onPointerUp={() => { clearTimeout(timer.current); if (!fired.current) onTap(L.id); }}
      onPointerLeave={() => clearTimeout(timer.current)}
      className="press"
      style={{
        position: "relative", borderRadius: 19, cursor: "pointer", userSelect: "none", touchAction: "none", overflow: "hidden",
        background: "linear-gradient(160deg,#222B3E,#161C2B)", boxShadow: "inset 1.5px 1.5px 0 rgba(255,255,255,.1), inset -1.5px -1.5px 0 rgba(0,0,0,.45), 0 8px 14px rgba(0,0,0,.4)",
        border: `1px solid ${st === "empty" ? "rgba(255,255,255,.06)" : col + "66"}`,
      }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.06, mixBlendMode: "overlay", pointerEvents: "none" }} />
      <LedRing color={col} mode={st === "rec" || st === "overdub" ? "solid" : st === "queued" ? "queued" : "off"} dur={2} radius={19} width={2} />
      <div style={{ position: "absolute", inset: 0, padding: "8px 10px", display: "flex", gap: 10, alignItems: "center" }}>
        <svg viewBox="0 0 100 100" style={{ width: 96, height: 96, flex: "none" }}>
          <circle cx="50" cy="50" r="44" fill="none" stroke="rgba(255,255,255,.08)" strokeWidth="3" />
          {Array.from({ length: ringBars }).map((_, i) => { const [x1, y1] = pt(i / ringBars, 41.5), [x2, y2] = pt(i / ringBars, 47); return <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} stroke={barNow === i + 1 && head !== null ? col : "rgba(255,255,255,.28)"} strokeWidth="1.6" strokeLinecap="round" />; })}
          {layerRs.map((r, i) => <circle key={i} cx="50" cy="50" r={r} fill="none" stroke={i < L.layers ? "rgba(255,255,255,.13)" : "rgba(255,255,255,.04)"} strokeWidth="1" />)}
          {L.layers.length !== undefined ? null : null}
          {L.data.map((layer, li) => layer.map((e, k) => { const [x, y] = pt(e.tb / L.bars, layerRs[li]); return <circle key={`${li}-${k}`} cx={x} cy={y} r="2.5" fill={e.color} opacity="0.95" />; }))}
          {recEvents.map((e, k) => { const [x, y] = pt(e.f, layerRs[0]); return <circle key={"r" + k} cx={x} cy={y} r="2.5" fill={e.c} />; })}
          {L.tmp && st === "overdub" && L.tmp.map((e, k) => { const [x, y] = pt(e.tb / L.bars, layerRs[Math.min(L.data.length, 3)]); return <circle key={"o" + k} cx={x} cy={y} r="2.5" fill={e.color} />; })}
          {head !== null && <>
            <path d={arcPath(Math.max(0, head - 0.14), head, 44)} fill="none" stroke={col} strokeWidth="3" strokeLinecap="round" opacity="0.45" />
            <circle cx={pt(head, 44)[0]} cy={pt(head, 44)[1]} r="4.2" fill={col} style={{ filter: `drop-shadow(0 0 5px ${col})` }} />
          </>}
          <text x="50" y="47" textAnchor="middle" fontSize="11" fontWeight="800" fill="#F1F4FA" fontFamily={MONO}>{st === "empty" ? "+" : st === "rec" ? "●" : st === "stopped" ? "❚❚" : st === "queued" ? "…" : `${barNow || 1}/${L.bars}`}</text>
          <text x="50" y="60" textAnchor="middle" fontSize="6.5" fontWeight="700" fill="#8E99AE" fontFamily="Inter, system-ui, sans-serif">{st === "empty" ? "tap to arm" : `${st === "rec" ? "bar " + barNow : L.bars + (L.bars === 1 ? " bar" : " bars")}`}</text>
        </svg>
        <div style={{ flex: 1, minWidth: 0, height: "100%", display: "flex", flexDirection: "column", justifyContent: "space-between", padding: "4px 0" }}>
          <div>
            <div style={{ fontFamily: MONO, fontSize: 11, fontWeight: 800, color: "#8E99AD" }}>L{L.id + 1}</div>
            <div style={{ fontSize: 12.5, fontWeight: 800, color: st === "empty" ? "#6F7A90" : col, letterSpacing: 0.4, animation: st === "rec" || st === "queued" ? "recPulse 1s ease-in-out infinite" : "none" }}>{STATE_LABEL[st]}</div>
          </div>
          <div>
            <div style={{ display: "flex", gap: 4, marginBottom: 6 }}>
              {[0, 1, 2, 3].map((i) => <span key={i} style={{ flex: 1, height: 4, borderRadius: 2, background: i < L.data.length ? col : "rgba(255,255,255,.09)", boxShadow: i < L.data.length ? `0 0 5px ${col}77` : "none", transition: "background .3s" }} />)}
            </div>
            <div style={{ display: "flex", gap: 5 }}>
              {[0, 1, 2, 3].map((i) => <span key={i} style={{ width: 6, height: 6, borderRadius: 3, background: st !== "empty" && beat === i ? col : "rgba(255,255,255,.12)", transition: "background .08s" }} />)}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ editor and manager ═══════════════ */

function PadEditor({ index, pad, color, onChange, onClose, onClear, onAssign }) {
  return (
    <Sheet onClose={onClose} top={46} w={430}>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 12 }}>
        <span style={{ width: 10, height: 10, borderRadius: 5, background: color, marginRight: 10, boxShadow: `0 0 8px ${color}` }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 800, color: "#F1F4FA" }}>Pad D{index + 1}</div>
          <div style={{ fontSize: 11.5, color: "#7B859A", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{pad.sample || "No sample assigned"}</div>
        </div>
        <CloseBtn onClick={onClose} />
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 16, marginBottom: 12 }}>
        <VolumeKnob value={pad.vol} onChange={(v) => onChange({ vol: v })} size={56} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 11, color: "#9AA5BA", fontWeight: 700, marginBottom: 6 }}>Mode</div>
          <Seg options={[["one", "One-shot"], ["loop", "Loop"]]} value={pad.mode} onChange={(m) => onChange({ mode: m })} h={26} />
        </div>
      </div>
      <button onClick={onAssign} className="press" style={{ width: "100%", height: 40, borderRadius: 13, border: "1px solid rgba(255,255,255,.14)", background: "rgba(255,255,255,.06)", color: "#E9EDF5", fontSize: 13.5, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 8, marginBottom: 10 }}><Ico n="folder" size={15} /> {pad.sample ? "Change sample" : "Assign sample"}</button>
      <div style={{ display: "flex", justifyContent: "space-between" }}>
        <button onClick={onClear} className="press" style={{ height: 34, padding: "0 14px", borderRadius: 12, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.05)", color: "#C7CFDD", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>Clear pad</button>
        <button onClick={onClose} className="press" style={{ height: 34, padding: "0 22px", borderRadius: 12, border: 0, background: "linear-gradient(180deg,#9DBDEB,#7FA3DD)", color: "#0F1726", fontSize: 13, fontWeight: 800, cursor: "pointer" }}>Done</button>
      </div>
    </Sheet>
  );
}

function Manager({ open, tab, setTab, openTarget, pads, samples, setSamples, folders, setFolders, kits, setKits, activeKit, loadKit, onAssign, onClose, say, usedBy }) {
  const [folder, setFolder] = useState(null);       // null = Library
  const [mode, setMode] = useState("assign");        // assign | organize
  const [target, setTarget] = useState(0);
  const [sel, setSel] = useState([]);
  const [preview, setPreview] = useState(null);
  const [moveOpen, setMoveOpen] = useState(false);
  const [importing, setImporting] = useState(null);
  const [popPad, setPopPad] = useState(null);
  const hold = useRef(null);

  useEffect(() => { if (open) { setTarget(openTarget ?? 0); setMode("assign"); setSel([]); if (openTarget !== null) setTab("samples"); } }, [open]); // eslint-disable-line

  const list = samples.filter((s) => !folder || s.folder === folder);
  const countOf = (id) => samples.filter((s) => s.folder === id).length;
  const assigning = tab === "samples" && mode === "assign";

  const rowTap = (s) => {
    if (assigning) {
      onAssign(target, s);
      setPopPad(target); setTimeout(() => setPopPad(null), 420);
      if (openTarget === null) setTarget((t) => (t + 1) % 8);
      return;
    }
    setSel((a) => (a.includes(s.id) ? a.filter((x) => x !== s.id) : [...a, s.id]));
  };
  const importFile = () => {
    const id = "imp" + Date.now();
    setSamples((x) => [{ id, name: "Imported Sample", folder: "imp", dur: "0:03", size: "180 KB" }, ...x]);
    setImporting({ id });
    setTimeout(() => { setImporting(null); say("Imported Sample added"); }, 1000);
  };
  const newFolder = () => { const n = folders.length + 1; setFolders((f) => [...f, { id: "f" + Date.now(), name: `Folder ${n}` }]); say("Folder created"); };
  const del = () => {
    const gone = samples.filter((s) => sel.includes(s.id));
    setSamples((x) => x.filter((s) => !sel.includes(s.id)));
    setSel([]);
    say(`${gone.length} deleted`, () => setSamples((x) => [...gone, ...x]));
  };
  const moveTo = (fid) => { setSamples((x) => x.map((s) => (sel.includes(s.id) ? { ...s, folder: fid } : s))); say(`Moved ${sel.length} to ${folders.find((f) => f.id === fid).name}`); setSel([]); setMoveOpen(false); };

  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 40, pointerEvents: open ? "auto" : "none" }}>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.62)", backdropFilter: "blur(2.5px)", opacity: open ? 1 : 0, transition: "opacity .28s" }} />
      <div style={{ position: "absolute", left: 116, top: 20, width: 660, height: 372, borderRadius: 24, overflow: "hidden", background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 28px 70px rgba(0,0,0,.6)", transformOrigin: "90px 340px", transform: open ? "scale(1)" : "scale(.5)", opacity: open ? 1 : 0, transition: open ? "transform .38s cubic-bezier(.2,.9,.25,1), opacity .22s" : "transform .24s ease-in, opacity .18s" }}>
        <div style={{ position: "absolute", left: 14, right: 14, top: 14, height: 44, display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ flex: 1, height: 42, borderRadius: 14, border: "1px solid rgba(255,255,255,.1)", background: "rgba(15,20,29,.7)", display: "flex", alignItems: "center", gap: 8, padding: "0 12px", color: "#8690A4" }}>
            <Ico n="search" size={17} /><span style={{ fontSize: 14.5 }}>{tab === "kits" ? "Search kits" : "Search samples"}</span>
          </div>
          {tab === "samples" && <button onClick={importFile} aria-label="Import" style={{ width: 42, height: 42, borderRadius: 21, border: "1px solid rgba(255,255,255,.2)", background: "linear-gradient(180deg,#9DBDEB,#7FA3DD)", color: "#0F1726", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="plus" size={19} sw={2.6} /></button>}
          <button onClick={onClose} aria-label="Close" style={{ width: 42, height: 42, borderRadius: 21, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.07)", color: "#E9EDF5", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={17} /></button>
        </div>

        <div style={{ position: "absolute", left: 14, top: 68, width: 154, bottom: 14, display: "flex", flexDirection: "column" }}>
          <div style={{ fontSize: 10, letterSpacing: 1.3, fontWeight: 700, color: "#7B859A", height: 16, textTransform: "uppercase" }}>{assigning ? `Assign to D${target + 1}` : "Kit & samples"}</div>
          <div style={{ marginBottom: 8 }}><Seg options={[["kits", "Kits"], ["samples", "Samples"]]} value={tab} onChange={(t) => { setTab(t); setSel([]); }} h={28} /></div>
          {tab === "samples" && (
            <>
              <div style={{ marginBottom: 8 }}><Seg options={[["assign", "Assign"], ["organize", "Organize"]]} value={mode} onChange={(m) => { setMode(m); setSel([]); }} h={24} /></div>
              <div className="noscroll" style={{ flex: 1, overflowY: "auto" }}>
                {[{ id: null, name: "Library", n: samples.length }, ...folders.map((f) => ({ id: f.id, name: f.name, n: countOf(f.id) }))].map((f) => {
                  const on = folder === f.id;
                  return (
                    <div key={String(f.id)} onClick={() => setFolder(f.id)} className="press" style={{ height: 32, marginBottom: 2, padding: "0 10px", borderRadius: 10, display: "flex", alignItems: "center", gap: 8, cursor: "pointer", background: on ? "linear-gradient(180deg,rgba(255,255,255,.11),rgba(255,255,255,.06))" : "transparent" }}>
                      <span style={{ color: on ? AC : "#6F7A90", display: "inline-flex" }}><Ico n="folder" size={14} /></span>
                      <span style={{ flex: 1, fontSize: 13, fontWeight: 700, color: on ? "#F1F4FA" : "#9AA5BA", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{f.name}</span>
                      <span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 700, color: "#6F7A90" }}><Roll value={f.n} /></span>
                    </div>
                  );
                })}
              </div>
              <button onClick={newFolder} className="press" style={{ height: 30, borderRadius: 11, border: "1px dashed rgba(255,255,255,.18)", background: "transparent", color: "#9AA5BA", fontSize: 12, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6 }}><Ico n="plus" size={13} /> New folder</button>
            </>
          )}
        </div>

        <div style={{ position: "absolute", left: 182, right: 14, top: 68, bottom: 14 }}>
          <div className="noscroll" style={{ position: "absolute", inset: 0, overflowY: "auto", paddingBottom: assigning ? 62 : sel.length ? 52 : 0 }}>
            {tab === "kits" && kits.map((k, i) => {
              const active = k.id === activeKit;
              return (
                <div key={k.id} onClick={() => loadKit(k)} className="press" style={{ height: 52, marginBottom: 5, borderRadius: 14, padding: "0 12px", display: "flex", alignItems: "center", gap: 12, cursor: "pointer", background: active ? "linear-gradient(180deg,rgba(255,255,255,.12),rgba(255,255,255,.07))" : "linear-gradient(180deg,rgba(255,255,255,.065),rgba(255,255,255,.03))", border: `1px solid ${active ? "rgba(255,255,255,.17)" : "rgba(255,255,255,.06)"}`, animation: `rise .26s ease-out ${i * 30}ms backwards` }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14.5, fontWeight: 800, color: "#E9EDF5" }}>{k.name}</div>
                    <div style={{ display: "flex", gap: 3, marginTop: 5 }}>{k.s.map((s, j) => <span key={j} style={{ width: 14, height: 4, borderRadius: 2, background: s ? PALETTE[j % 8] : "rgba(255,255,255,.1)", opacity: s ? 0.9 : 1 }} />)}</div>
                  </div>
                  {active && <span style={{ display: "inline-flex", alignItems: "center", gap: 4, padding: "3px 8px", borderRadius: 8, background: "rgba(255,255,255,.1)", color: "#C7CFDD", fontSize: 9.5, letterSpacing: 1.1, fontWeight: 800 }}><Ico n="check" size={11} sw={3} color={AC} />ACTIVE</span>}
                </div>
              );
            })}
            {tab === "kits" && (
              <button onClick={() => { const n = kits.length + 1; setKits((x) => [...x, { id: "k" + Date.now(), name: `Kit ${n}`, s: pads.map((p) => p.sample) }]); say("Kit saved from current pads"); }} className="press" style={{ width: "100%", height: 40, borderRadius: 13, border: "1px dashed rgba(255,255,255,.18)", background: "transparent", color: "#9AA5BA", fontSize: 12.5, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6 }}><Ico n="plus" size={13} /> Save current pads as a kit</button>
            )}
            {tab === "samples" && list.length === 0 && <div style={{ padding: "22px 6px", fontSize: 13.5, color: "#7B859A" }}>This folder is empty. Import files with +.</div>}
            {tab === "samples" && list.map((s, i) => {
              const on = sel.includes(s.id);
              const used = usedBy(s.name);
              return (
                <div key={s.id} className="press"
                  onPointerDown={() => { clearTimeout(hold.current); hold.current = setTimeout(() => { setPreview(s.id); vib(8); }, 360); }}
                  onPointerUp={() => { clearTimeout(hold.current); if (preview === s.id) setPreview(null); else rowTap(s); }}
                  onPointerLeave={() => { clearTimeout(hold.current); setPreview(null); }}
                  style={{ position: "relative", height: 40, marginBottom: 4, borderRadius: 12, padding: "0 10px 0 12px", display: "flex", alignItems: "center", gap: 10, cursor: "pointer", userSelect: "none", overflow: "hidden", background: on ? "linear-gradient(180deg,rgba(143,178,232,.16),rgba(143,178,232,.08))" : "linear-gradient(180deg,rgba(255,255,255,.065),rgba(255,255,255,.03))", border: `1px solid ${on ? "rgba(143,178,232,.45)" : "rgba(255,255,255,.06)"}`, animation: `rise .22s ease-out ${Math.min(i, 8) * 16}ms backwards` }}>
                  {!assigning && <span style={{ width: 18, height: 18, borderRadius: 6, flex: "none", border: on ? "none" : "1.5px solid rgba(255,255,255,.22)", background: on ? AC : "transparent", display: "grid", placeItems: "center", color: "#0F1726", transition: "background .2s" }}>{on && <Ico n="check" size={12} sw={3.2} />}</span>}
                  <span style={{ flex: 1, minWidth: 0, fontSize: 14.5, fontWeight: 700, color: "#E9EDF5", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{s.name}</span>
                  {preview === s.id && <span style={{ display: "flex", gap: 2, height: 14 }}>{[0, 1, 2, 3].map((b) => <span key={b} style={{ width: 2.5, height: 14, borderRadius: 2, background: AC, transformOrigin: "50% 100%", animation: `bar ${0.5 + b * 0.12}s ease-in-out infinite` }} />)}</span>}
                  {used && <span style={{ padding: "2px 7px", borderRadius: 7, background: "rgba(255,255,255,.08)", color: "#B5BFD3", fontSize: 10.5, fontWeight: 800, fontFamily: MONO }}>{used}</span>}
                  <span style={{ fontFamily: MONO, fontSize: 11.5, fontWeight: 600, color: "#7B859A", minWidth: 36, textAlign: "right" }}>{s.dur}</span>
                  <span style={{ fontFamily: MONO, fontSize: 11.5, fontWeight: 600, color: "#7B859A", minWidth: 54, textAlign: "right" }}>{s.size}</span>
                  {importing && importing.id === s.id && <span style={{ position: "absolute", left: 0, bottom: 0, height: 2, background: AC, animation: "fill 1s linear forwards" }} />}
                </div>
              );
            })}
          </div>

          {/* target pads: tap a pad, then tap a sample; the target moves on to the next pad */}
          {assigning && (
            <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 54, borderRadius: 16, padding: 6, display: "flex", gap: 5, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 -8px 22px rgba(0,0,0,.4)", animation: "sheetIn .22s ease-out" }}>
              {pads.map((p, i) => {
                const on = i === target;
                return (
                  <button key={i} onClick={() => { if (openTarget === null) setTarget(i); }} className="press"
                    style={{ flex: 1, minWidth: 0, borderRadius: 11, border: 0, cursor: openTarget === null ? "pointer" : "default", padding: "4px 5px", textAlign: "left", position: "relative", overflow: "hidden",
                      background: on ? `linear-gradient(160deg, ${PALETTE[i % 8]}88, #1F2739)` : `linear-gradient(160deg, ${PALETTE[i % 8]}30, #1A2133)`,
                      boxShadow: on ? `inset 0 0 0 1.5px ${PALETTE[i % 8]}, 0 0 14px ${PALETTE[i % 8]}55` : "inset 0 1px 0 rgba(255,255,255,.1)",
                      opacity: openTarget !== null && !on ? 0.4 : 1, transform: popPad === i ? "scale(1.06)" : "none", transition: "transform .22s cubic-bezier(.3,1.6,.5,1), box-shadow .25s, background .25s, opacity .25s" }}>
                    <div style={{ fontFamily: MONO, fontSize: 9.5, fontWeight: 800, color: on ? "#fff" : "#8E99AE" }}>D{i + 1}</div>
                    <div style={{ fontSize: 10, fontWeight: 700, color: p.sample ? "#E9EDF5" : "#566075", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{p.sample || "+"}</div>
                  </button>
                );
              })}
            </div>
          )}

          {!assigning && sel.length > 0 && (
            <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 44, borderRadius: 14, padding: "0 8px 0 14px", display: "flex", alignItems: "center", gap: 10, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 -8px 24px rgba(0,0,0,.4)", animation: "sheetIn .22s ease-out" }}>
              <span style={{ flex: 1, fontSize: 13, fontWeight: 800, color: "#F1F4FA" }}>{sel.length} selected</span>
              <button onClick={() => setMoveOpen(true)} className="press" style={{ height: 30, padding: "0 14px", borderRadius: 11, border: "1px solid rgba(255,255,255,.14)", background: "rgba(255,255,255,.07)", color: "#E9EDF5", fontSize: 12.5, fontWeight: 800, cursor: "pointer" }}>Move</button>
              <button onClick={del} className="press" style={{ height: 30, padding: "0 14px", borderRadius: 11, border: "1px solid rgba(255,255,255,.14)", background: "rgba(255,255,255,.07)", color: "#E9EDF5", fontSize: 12.5, fontWeight: 800, cursor: "pointer" }}>Delete</button>
              <button onClick={() => setSel([])} style={{ width: 30, height: 30, borderRadius: 15, border: 0, background: "transparent", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={14} /></button>
            </div>
          )}
        </div>

        {moveOpen && (
          <div style={{ position: "absolute", inset: 0, zIndex: 5 }}>
            <div onClick={() => setMoveOpen(false)} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.5)", animation: "fadeIn .2s" }} />
            <div style={{ position: "absolute", left: 14, right: 14, bottom: 14, borderRadius: 20, padding: "14px 16px", background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", animation: "sheetIn .26s cubic-bezier(.2,.9,.25,1)" }}>
              <div style={{ fontSize: 15, fontWeight: 800, color: "#F1F4FA", marginBottom: 10 }}>Move {sel.length} to…</div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 7 }}>
                {folders.map((f) => <button key={f.id} onClick={() => moveTo(f.id)} className="press" style={{ height: 32, padding: "0 14px", borderRadius: 16, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.05)", color: "#C7CFDD", fontSize: 12.5, fontWeight: 700, cursor: "pointer" }}>{f.name}</button>)}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* ═══════════════ main ═══════════════ */

const mkLoop = (id) => ({ id, state: "empty", bars: 1, data: [], tmp: [], recStart: 0, odStart: 0, at: 0, startAt: 0, queued: null, last: -1, prev: "empty", lenMode: "auto" });

export default function DrumPadPreview() {
  const [mode, setMode] = useState("play");
  const [surface, setSurface] = useState("pads");
  const [drones, setDrones] = useState([]);
  const [multi, setMulti] = useState(true);
  const [octave, setOctave] = useState(3);
  const [pads, setPads] = useState(INIT_PADS);
  const [flashAt, setFlashAt] = useState({});
  const [playingLoop, setPlayingLoop] = useState({});
  const [volume, setVolume] = useState(80);
  const [fx, setFx] = useState({ reverb: { label: "Reverb", on: true, amt: 40 }, delay: { label: "Delay", on: false, amt: 30 }, filter: { label: "Filter", on: false, amt: 50 }, shimmer: { label: "Shimmer", on: true, amt: 40 } });
  const [fxOpen, setFxOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [mgr, setMgr] = useState({ open: false, tab: "kits", assign: null });
  const [samples, setSamples] = useState(SAMPLES0);
  const [folders, setFolders] = useState(FOLDERS0);
  const [kits, setKits] = useState(KITS0);
  const [activeKit, setActiveKit] = useState("dk");
  const [lenMode, setLenMode] = useState("auto");
  const [quant, setQuant] = useState(true);
  const [menu, setMenu] = useState(null);
  const [toast, setToast] = useState(null);
  const [appear, setAppear] = useState(0);
  const [, setTick] = useState(0);
  const loops = useRef(Array.from({ length: 8 }, (_, i) => mkLoop(i)));
  const ghostPads = useRef({});
  const ghostNotes = useRef({});
  const toastT = useRef(null);
  const stageRef = useRef(null);
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });

  useEffect(() => {
    const on = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 150) / H, 1.5));
  const portrait = vp.h > vp.w;
  const now = performance.now();

  const say = (text, undo) => { clearTimeout(toastT.current); setToast({ text, undo, key: Date.now() }); toastT.current = setTimeout(() => setToast(null), undo ? 4200 : 1700); };

  /* ── looper engine ── */
  const finishRec = (L, t) => {
    const elapsed = t - L.recStart;
    const bars = L.lenMode === "auto" ? Math.max(1, Math.round(elapsed / BAR)) : L.lenMode;
    const ev = L.tmp.map((e) => { let tb = e.tb % bars; if (quant) tb = (Math.round(tb * 16) / 16) % bars; return { ...e, tb }; });
    L.bars = bars; L.data = [ev]; L.tmp = []; L.state = "play"; L.startAt = L.recStart + bars * BAR; L.last = -1;
  };
  const commitOverdub = (L) => {
    const ev = L.tmp.map((e) => { let tb = e.tb % L.bars; if (quant) tb = (Math.round(tb * 16) / 16) % L.bars; return { ...e, tb }; });
    if (L.data.length < 4) L.data.push(ev); L.tmp = []; L.state = "play"; L.last = -1;
  };
  const step = (t) => {
    loops.current.forEach((L) => {
      if (L.state === "queued" && t >= L.at) {
        if (L.queued === "rec") { L.state = "rec"; L.recStart = L.at; L.tmp = []; }
        else if (L.queued === "overdub") { L.state = "overdub"; L.odStart = L.at; L.tmp = []; }
        else if (L.queued === "play") { L.state = "play"; L.startAt = L.at; L.last = -1; }
        L.queued = null;
      }
      if (L.state === "rec" && L.lenMode !== "auto" && t - L.recStart >= L.lenMode * BAR) finishRec(L, t);
      if (L.state === "overdub" && t - L.odStart >= L.bars * BAR) commitOverdub(L);
      if ((L.state === "play" || L.state === "overdub") && t >= L.startAt) {
        const f = (((t - L.startAt) / (L.bars * BAR)) % 1 + 1) % 1;
        const prev = L.last;
        L.last = f;
        if (prev >= 0) {
          L.data.forEach((layer) => layer.forEach((e) => {
            const x = e.tb / L.bars;
            const crossed = prev <= f ? x > prev && x <= f : x > prev || x <= f;
            if (crossed) { if (e.kind === "pad") ghostPads.current[e.idx] = t; else ghostNotes.current[e.idx] = t; }
          }));
        }
      }
    });
  };
  useEffect(() => {
    let raf, last = 0;
    const loop = (ts) => { raf = requestAnimationFrame(loop); if (ts - last < 33) return; last = ts; step(performance.now()); setTick((x) => x + 1); };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [quant]); // eslint-disable-line

  const record = (kind, idx, color) => {
    const t = performance.now();
    loops.current.forEach((L) => {
      if (L.state === "rec") { const tb = (t - L.recStart) / BAR; if (tb >= 0) L.tmp.push({ tb, kind, idx, color }); }
      else if (L.state === "overdub") L.tmp.push({ tb: ((t - L.odStart) / BAR) % L.bars, kind, idx, color });
    });
  };

  const tapLoop = (i) => {
    const L = loops.current[i]; const t = performance.now();
    const busy = loops.current.some((x) => x.id !== i && (x.state === "rec" || (x.state === "queued" && x.queued === "rec")));
    if (L.state === "empty") {
      if (busy) { say("One case records at a time"); return; }
      L.prev = "empty"; L.state = "queued"; L.queued = "rec"; L.at = nextBoundary(t); L.lenMode = lenMode;
    } else if (L.state === "queued") { L.state = L.prev; L.queued = null; }
    else if (L.state === "rec") finishRec(L, t);
    else if (L.state === "play") {
      const loopMs = L.bars * BAR; const base = Math.max(L.startAt, t);
      L.prev = "play"; L.state = "queued"; L.queued = "overdub";
      L.at = L.startAt + (Math.floor((base - L.startAt) / loopMs) + 1) * loopMs;
    } else if (L.state === "overdub") commitOverdub(L);
    else if (L.state === "stopped") { L.state = "queued"; L.queued = "play"; L.prev = "stopped"; L.at = nextBoundary(t); }
    vib(8); setTick((x) => x + 1);
  };
  const holdLoop = (i) => {
    const st = stageRef.current.getBoundingClientRect();
    setMenu({ i, x: 330, y: 140 });
  };
  const loopAction = (i, a) => {
    const L = loops.current[i];
    if (a === "undo" && L.data.length) { L.data.pop(); if (!L.data.length) { loops.current[i] = mkLoop(i); } say(`L${i + 1} · last layer removed`); }
    if (a === "double" && L.bars < 8) { L.data = L.data.map((layer) => layer.concat(layer.map((e) => ({ ...e, tb: e.tb + L.bars })))); L.bars *= 2; say(`L${i + 1} doubled to ${L.bars} bars`); }
    if (a === "stop") { L.state = "stopped"; }
    if (a === "play") { L.state = "queued"; L.queued = "play"; L.prev = "stopped"; L.at = nextBoundary(performance.now()); }
    if (a === "clear") { loops.current[i] = mkLoop(i); say(`L${i + 1} cleared`); }
    setMenu(null);
  };
  const stopAll = () => loops.current.forEach((L) => { if (L.state === "play" || L.state === "overdub") L.state = "stopped"; });
  const playAll = () => loops.current.forEach((L) => { if (L.state === "stopped") { L.state = "queued"; L.queued = "play"; L.prev = "stopped"; L.at = nextBoundary(performance.now()); } });

  /* ── pads ── */
  const padDown = (i) => {
    if (mode === "edit") return;
    const p = pads[i]; if (!p.sample) return;
    vib(6);
    setFlashAt((f) => ({ ...f, [i]: performance.now() }));
    if (p.mode === "loop") setPlayingLoop((s) => ({ ...s, [i]: !s[i] }));
    record("pad", i, PALETTE[i % 8]);
  };
  const padUp = (i) => { if (mode === "edit") setEditing(i); };
  const updPad = (i, patch) => setPads((ps) => ps.map((p, k) => (k === i ? { ...p, ...patch } : p)));

  /* ── drones ── */
  const toggleDrone = (n) => {
    vib(6);
    record("note", n, viv(NOTES.indexOf(n)));
    setDrones((d) => {
      if (d.includes(n)) return d.filter((x) => x !== n);
      if (!multi) return [n];
      const next = [...d, n];
      return next.length > 4 ? next.slice(next.length - 4) : next;
    });
  };

  const loadKit = (k) => {
    setPads((ps) => ps.map((p, i) => ({ ...p, sample: k.s[i], name: k.s[i] || "Empty" })));
    setActiveKit(k.id); setPlayingLoop({}); setAppear((a) => a + 1); say(`Kit “${k.name}” loaded`);
  };
  const assignSample = (i, s) => {
    updPad(i, { sample: s.name, name: s.name });
    setFlashAt((f) => ({ ...f, [i]: performance.now() }));
    say(`${s.name} → D${i + 1}`);
    if (mgr.assign !== null) { setMgr({ open: false, tab: "samples", assign: null }); setEditing(null); }
  };
  const usedBy = (name) => { const u = pads.map((p, i) => (p.sample === name ? `D${i + 1}` : null)).filter(Boolean); return u.length ? u.join(" ") : ""; };

  const anyRec = loops.current.find((L) => L.state === "rec" || L.state === "overdub" || (L.state === "queued" && L.queued !== "play"));
  const beat = Math.floor(((now - T0) % BAR) / BEAT);
  const activeCount = loops.current.filter((L) => L.state !== "empty").length;
  const anyStopped = loops.current.some((L) => L.state === "stopped");

  return (
    <div className="dp" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div ref={stageRef} style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
          <TopBar />

          {/* left column */}
          <div style={{ position: "absolute", left: 10, top: 56, width: 196, bottom: 10, display: "flex", flexDirection: "column", gap: 8 }}>
            <div style={{ flex: 1, minHeight: 0 }}>
              <TonicPad drones={drones} onToggle={toggleDrone} onClear={() => { setDrones([]); vib(8); }} multi={multi} onMulti={() => setMulti((m) => !m)} octave={octave} onOct={(d) => setOctave((o) => clamp(o + d, 0, 7))} ghostNotes={ghostNotes.current} now={now} />
            </div>
            <div style={{ display: "flex", gap: 8, height: 62 }}>
              <div style={{ flex: "1 1 auto", padding: "4px 8px", borderRadius: 14, background: "linear-gradient(180deg,#2A3347,#1F2739)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.07), 0 3px 8px rgba(0,0,0,.35)", display: "flex", alignItems: "center" }}>
                <VolumeKnob value={volume} onChange={setVolume} />
              </div>
              <FxCase fx={fx} onOpen={() => setFxOpen(true)} />
            </div>
            <div style={{ display: "flex", gap: 6 }}>
              <div className="press" onClick={() => say("Opens the Pad sound window")} style={{ flex: 1, height: 30, borderRadius: 10, padding: "0 9px", display: "flex", alignItems: "center", gap: 6, background: "linear-gradient(180deg,#2A3347,#1F2739)", boxShadow: "0 3px 8px rgba(0,0,0,.3)", cursor: "pointer", minWidth: 0 }}>
                <span style={{ color: AC, display: "inline-flex" }}><Ico n="wave" size={12} /></span><span style={{ fontSize: 10.5, color: "#7B859A", fontWeight: 700 }}>Pad</span><span style={{ fontSize: 10.5, color: "#E3E8F2", flex: 1, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>Warm Pad</span>
              </div>
              <div className="press" onClick={() => setMgr({ open: true, tab: "kits", assign: null })} style={{ flex: 1, height: 30, borderRadius: 10, padding: "0 9px", display: "flex", alignItems: "center", gap: 6, background: "linear-gradient(180deg,#2A3347,#1F2739)", boxShadow: "0 3px 8px rgba(0,0,0,.3)", cursor: "pointer", minWidth: 0 }}>
                <span style={{ color: AC, display: "inline-flex" }}><Ico n="folder" size={12} /></span><span style={{ fontSize: 10.5, color: "#7B859A", fontWeight: 700 }}>Kit</span><span style={{ fontSize: 10.5, color: "#E3E8F2", flex: 1, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{kits.find((k) => k.id === activeKit)?.name}</span><span style={{ color: "#6F7A90", display: "inline-flex" }}><Ico n="chev" size={11} /></span>
              </div>
            </div>
          </div>

          {/* right side */}
          <div style={{ position: "absolute", left: 216, right: 10, top: 56, bottom: 10, display: "flex", flexDirection: "column", gap: 8 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, height: 30 }}>
              <Seg options={[["pads", "Pads"], ["loops", "Loops"]]} value={surface} onChange={(s) => { setSurface(s); setAppear((a) => a + 1); }} w={160} />
              {surface === "pads" ? <Seg options={[["play", "Play"], ["edit", "Edit"]]} value={mode} onChange={setMode} w={124} warn /> : (
                <>
                  <Seg options={[["auto", "Auto"], [1, "1"], [2, "2"], [4, "4"], [8, "8"]]} value={lenMode} onChange={setLenMode} w={190} />
                  <Seg options={[[true, "1/16"], [false, "Free"]]} value={quant} onChange={setQuant} w={104} />
                </>
              )}
              <div style={{ display: "flex", gap: 5, alignItems: "center", marginLeft: 4 }}>
                {[0, 1, 2, 3].map((i) => <span key={i} style={{ width: 7, height: 7, borderRadius: 4, background: beat === i ? (i === 0 ? AC : "#C7CFDD") : "rgba(255,255,255,.12)", transition: "background .06s", boxShadow: beat === i && i === 0 ? `0 0 8px ${AC}` : "none" }} />)}
              </div>
              <div style={{ flex: 1 }} />
              {anyRec && surface === "pads" && (
                <button onClick={() => tapLoop(anyRec.id)} className="press" style={{ height: 28, padding: "0 12px", borderRadius: 14, border: `1px solid ${ROSE}`, background: "rgba(194,88,122,.14)", color: "#E3A0B4", fontSize: 11.5, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", gap: 7 }}>
                  <span style={{ width: 8, height: 8, borderRadius: 4, background: ROSE, animation: "recPulse .9s ease-in-out infinite" }} />L{anyRec.id + 1} · {anyRec.state === "queued" ? "ARMED" : anyRec.state === "rec" ? "REC" : "OVERDUB"}
                </button>
              )}
              {surface === "loops" && (
                <>
                  <span style={{ fontSize: 11.5, color: "#7B859A", fontWeight: 700 }}>{activeCount}/8 used</span>
                  <button onClick={() => (anyStopped ? playAll() : stopAll())} className="press" style={{ height: 28, padding: "0 12px", borderRadius: 10, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.05)", color: "#C7CFDD", fontSize: 11.5, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", gap: 6 }}><Ico n={anyStopped ? "play" : "stop"} size={11} /> {anyStopped ? "Play all" : "Stop all"}</button>
                </>
              )}
            </div>
            <div style={{ flex: 1, minHeight: 0, display: "grid", gridTemplateColumns: "repeat(4,1fr)", gridTemplateRows: "1fr 1fr", gap: 9 }}>
              {surface === "pads" && pads.map((p, i) => (
                <Pad key={`p${i}-${appear}`} index={i} pad={p} color={PALETTE[i % 8]} mode={mode}
                  runner={!!playingLoop[i] && p.mode === "loop"} flash={now - (flashAt[i] || -9999) < 360} ghost={now - (ghostPads.current[i] || -9999) < 380}
                  queued={false} onDown={padDown} onUp={padUp} appear={appear > 0} />
              ))}
              {surface === "loops" && loops.current.map((L) => (
                <div key={`l${L.id}-${appear}`} style={{ display: "flex", animation: `rise .4s cubic-bezier(.2,.9,.25,1) ${L.id * 34}ms backwards` }}>
                  <div style={{ flex: 1, position: "relative" }}><div style={{ position: "absolute", inset: 0 }}><LoopCase L={L} now={now} onTap={tapLoop} onHold={holdLoop} lenMode={lenMode} /></div></div>
                </div>
              ))}
            </div>
          </div>

          {fxOpen && <FxSheet fx={fx} setFx={setFx} onClose={() => setFxOpen(false)} />}
          {editing !== null && !mgr.open && (
            <PadEditor index={editing} pad={pads[editing]} color={PALETTE[editing % 8]} onChange={(patch) => updPad(editing, patch)} onClose={() => setEditing(null)}
              onClear={() => { updPad(editing, { sample: null, name: "Empty" }); setEditing(null); say("Pad cleared"); }}
              onAssign={() => setMgr({ open: true, tab: "samples", assign: editing })} />
          )}
          <Manager open={mgr.open} tab={mgr.tab} setTab={(t) => setMgr((m) => ({ ...m, tab: t }))} openTarget={mgr.assign} pads={pads} samples={samples} setSamples={setSamples} folders={folders} setFolders={setFolders} kits={kits} setKits={setKits} activeKit={activeKit} loadKit={loadKit} onAssign={assignSample} onClose={() => setMgr((m) => ({ ...m, open: false, assign: null }))} say={say} usedBy={usedBy} />

          {menu && (() => { const L = loops.current[menu.i]; const can = L.state !== "empty"; return (
            <div style={{ position: "absolute", inset: 0, zIndex: 35 }}>
              <div onClick={() => setMenu(null)} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.35)" }} />
              <div style={{ position: "absolute", left: 330, top: 120, width: 190, borderRadius: 16, padding: 6, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 16px 40px rgba(0,0,0,.55)", animation: "menuIn .16s ease-out" }}>
                <div style={{ fontSize: 10.5, letterSpacing: 1.3, fontWeight: 700, color: "#7B859A", padding: "6px 10px" }}>LOOP {menu.i + 1}</div>
                {[["Undo last layer", "undo", can && L.data.length > 0], ["Double length", "double", can && L.bars < 8 && L.data.length > 0], [L.state === "stopped" ? "Play" : "Stop", L.state === "stopped" ? "play" : "stop", can], ["Clear", "clear", can]].map(([l, a, ok]) => (
                  <button key={a} disabled={!ok} onClick={() => loopAction(menu.i, a)} style={{ width: "100%", height: 34, border: 0, borderRadius: 10, background: "transparent", color: ok ? "#DCE2EE" : "#566075", fontSize: 13, fontWeight: 700, cursor: ok ? "pointer" : "default", textAlign: "left", padding: "0 10px" }}>{l}</button>
                ))}
              </div>
            </div>
          ); })()}

          {toast && (
            <div key={toast.key} style={{ position: "absolute", left: "50%", bottom: 12, transform: "translateX(-50%)", zIndex: 90, display: "flex", alignItems: "center", gap: 14, padding: "8px 14px", borderRadius: 12, background: "#222C40", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 10px 26px rgba(0,0,0,.5)", fontSize: 13, fontWeight: 600, color: "#E9EDF5", animation: "fadeIn .2s" }}>
              <span>{toast.text}</span>
              {toast.undo && <button onClick={() => { toast.undo(); setToast(null); }} style={{ border: 0, background: "transparent", color: AC, fontWeight: 800, fontSize: 12, letterSpacing: 1, cursor: "pointer" }}>UNDO</button>}
            </div>
          )}
        </div>
      </div>

      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", maxWidth: 760 }}>
        {[["Play", () => { setSurface("pads"); setMode("play"); }], ["Edit", () => { setSurface("pads"); setMode("edit"); }], ["Loops", () => setSurface("loops")], ["Kits & samples", () => setMgr({ open: true, tab: "kits", assign: null })]].map(([l, fn]) => (
          <button key={l} onClick={fn} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>{l}</button>
        ))}
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 660, lineHeight: 1.55 }}>
        Try the looper: Loops → tap L1 (it starts on the next bar) → switch to Pads and play → tap the REC pill or L1 again to close the take, then L1 again to overdub. Tonic notes are drones: tap to hold, tap again to release (4 max, they follow the octave). Edit → tap a pad to change its volume, mode or sample.
        {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
      </div>
    </div>
  );
}
