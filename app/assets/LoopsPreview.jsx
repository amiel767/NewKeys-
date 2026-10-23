import { useState, useRef, useEffect } from "react";

/* ─────────────────────────────────────────────────────────────
   LIVE LOOPER — landscape smartphone preview (891 × 411 dp)
   Page selector (DrumPad is an empty page here), Loop patches saved with the setlist,
   8 looper cases, focus view, case settings sheet.
   No audio: recorded events are visual only. The left column is simulated.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const AMBER = "#D8A24A", ROSE = "#C2587A", AC = "#8FB2E8", SAGE = "#86A394";
const PAL = ["#7F9BC7", "#9A86C2", "#C27A8E", "#C9A15C", "#86A394", "#6FB3A8", "#B58E6B", "#8D97B8"];
const BPM = 119, BAR = (4 * 60000) / BPM, BEAT = BAR / 4, SIX = BEAT / 4;
const T0 = performance.now();
const SETLIST = "Sunday Show";
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const rgba = (h, a) => { const r = parseInt(h.slice(1, 3), 16), g = parseInt(h.slice(3, 5), 16), b = parseInt(h.slice(5, 7), 16); return `rgba(${r},${g},${b},${a})`; };
const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2'/></filter><rect width='100%' height='100%' filter='url(#n)' opacity='.55'/></svg>")}")`;

const nextAt = (now, q) => {
  const u = q === "bar" ? BAR : q === "16" ? SIX : 0;
  return u ? T0 + (Math.floor((now - T0) / u) + 1) * u : now;
};
const mk = (i) => ({
  id: i, name: "", state: "empty", bars: 0, layers: 0, events: [], startAt: 0, recStart: 0, lenMode: "auto", queued: null, at: 0,
  mute: false, vol: 100, layerMute: [false, false, false, false], redo: [],
  src: { pads: true, tonic: true, keys: true }, stopMode: "end", after: "play", oneshot: false,
});
const snapCase = (c) => ({ name: c.name, bars: c.bars, layers: c.layers, events: c.events.map((e) => ({ ...e })), mute: c.mute, vol: c.vol, layerMute: [...c.layerMute], lenMode: c.lenMode, src: { ...c.src }, stopMode: c.stopMode, after: c.after, oneshot: c.oneshot });
const sigOf = (arr) => arr.map((c) => `${c.layers ? c.bars : 0}|${c.layers}|${c.events.length}|${c.mute ? 1 : 0}|${c.vol}|${c.layerMute.join("")}|${c.name}|${c.stopMode}|${c.after}|${c.oneshot ? 1 : 0}|${c.src.pads ? 1 : 0}${c.src.tonic ? 1 : 0}${c.src.keys ? 1 : 0}`).join(";");
const demoSnap = (spec) => Array.from({ length: 8 }, (_, i) => {
  const s = spec.find((x) => x[0] === i);
  const base = snapCase(mk(i));
  if (!s) return base;
  const [, bars, layers] = s;
  return { ...base, bars, layers, events: Array.from({ length: 9 }, (_, k) => ({ rel: BAR * bars * (k / 9), layer: k % layers })) };
});
const INIT_PATCHES = [
  { id: "p1", name: "Intro pad", cases: demoSnap([[0, 4, 2]]) },
  { id: "p2", name: "Verse groove", cases: demoSnap([[0, 2, 2], [1, 1, 3], [2, 4, 1]]) },
  { id: "p3", name: "Chorus stack", cases: demoSnap([[0, 2, 3], [1, 2, 2], [2, 1, 1], [3, 4, 2], [4, 2, 1]]) },
  { id: "p4", name: "Outro", cases: demoSnap([]) },
];

const CSS = `
.lp *{box-sizing:border-box}
.lp button{font-family:inherit}
.lp input{font-family:inherit}
@keyframes blink{0%,100%{opacity:1}50%{opacity:.35}}
@keyframes breathe{0%,100%{opacity:.82}50%{opacity:1}}
@keyframes recPulse{0%,100%{opacity:.6}50%{opacity:1}}
@keyframes sheetIn{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:translateY(0)}}
@keyframes winIn{from{opacity:0;transform:scale(.94) translateY(8px)}to{opacity:1;transform:scale(1) translateY(0)}}
@keyframes menuIn{from{opacity:0;transform:scaleY(.85)}to{opacity:1;transform:scaleY(1)}}
@keyframes fadeIn{from{opacity:0}to{opacity:1}}
@keyframes toast{from{opacity:0;transform:translate(-50%,8px)}to{opacity:1;transform:translate(-50%,0)}}
.press{transition:transform .14s cubic-bezier(.3,1.5,.5,1),background .25s,box-shadow .25s,border-color .25s,opacity .25s}
.press:active{transform:scale(.95)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

function Ico({ n, size = 14, color = "currentColor", sw = 2.1 }) {
  const P = {
    rec: <circle cx="12" cy="12" r="6" fill={color} stroke="none" />,
    play: <path d="M8 5.5l10 6.5-10 6.5z" fill={color} stroke="none" />,
    stop: <rect x="7" y="7" width="10" height="10" rx="1.6" fill={color} stroke="none" />,
    undo: <><path d="M9 14L4.5 9.5 9 5" /><path d="M4.5 9.5H14a5.5 5.5 0 0 1 0 11h-3" /></>,
    redo: <><path d="M15 14l4.5-4.5L15 5" /><path d="M19.5 9.5H10a5.5 5.5 0 0 0 0 11h3" /></>,
    more: <><circle cx="6" cy="12" r="1.6" fill={color} stroke="none" /><circle cx="12" cy="12" r="1.6" fill={color} stroke="none" /><circle cx="18" cy="12" r="1.6" fill={color} stroke="none" /></>,
    x: <path d="M6 6l12 12M18 6L6 18" />,
    back: <path d="M19 12H5M11 6l-6 6 6 6" />,
    down: <path d="M6 9l6 6 6-6" />,
    left: <path d="M15 6l-6 6 6 6" />,
    right: <path d="M9 6l6 6-6 6" />,
    check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
    layers: <><path d="M12 3l9 5-9 5-9-5z" /><path d="M3 13l9 5 9-5" /></>,
    speaker: <><path d="M4 9.5h3.5L12 6v12l-4.5-3.5H4z" /><path d="M15.5 9a4 4 0 0 1 0 6" /></>,
    mute: <><path d="M4 9.5h3.5L12 6v12l-4.5-3.5H4z" /><path d="M16 9.5l4 5M20 9.5l-4 5" /></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">{P[n]}</svg>;
}

function Seg({ options, value, onChange, w, h = 20, fs = 11 }) {
  const idx = Math.max(0, options.findIndex((o) => o[0] === value));
  return (
    <div style={{ position: "relative", display: "flex", padding: 2, borderRadius: 10, background: "rgba(14,19,28,.85)", boxShadow: "inset 0 1px 3px rgba(0,0,0,.55)", width: w, flex: w ? "none" : 1 }}>
      <div style={{ position: "absolute", top: 2, bottom: 2, left: 2, width: `calc((100% - 4px) / ${options.length})`, borderRadius: 8, background: "linear-gradient(180deg,#3A4663,#2B3650)", boxShadow: "0 2px 5px rgba(0,0,0,.4)", transform: `translateX(${idx * 100}%)`, transition: "transform .3s cubic-bezier(.3,1.35,.5,1)" }} />
      {options.map(([k, l]) => (
        <button key={String(k)} onClick={() => onChange(k)} style={{ position: "relative", flex: 1, height: h, border: 0, background: "transparent", cursor: "pointer", fontSize: fs, fontWeight: 800, color: value === k ? "#F1F4FA" : "#8690A4", transition: "color .25s", padding: 0, whiteSpace: "nowrap" }}>{l}</button>
      ))}
    </div>
  );
}

function Group({ label, children, style }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 3, flex: "none", ...style }}>
      <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: 1.2, color: "#6F7A90", paddingLeft: 3 }}>{label}</span>
      {children}
    </div>
  );
}

function Pill({ children, style, onClick }) {
  return (
    <div onClick={onClick} className={onClick ? "press" : ""} style={{ height: 44, borderRadius: 14, padding: "0 14px", display: "flex", alignItems: "center", gap: 8, background: "linear-gradient(180deg,#2A3347,#202839)", border: "1px solid rgba(255,255,255,.07)", boxShadow: "0 4px 10px rgba(0,0,0,.3)", color: "#E3E8F2", fontSize: 14, fontWeight: 800, cursor: onClick ? "pointer" : "default", whiteSpace: "nowrap", ...style }}>{children}</div>
  );
}

function TextField({ value, onChange, placeholder, w, autoFocus, onEnter }) {
  const [v, setV] = useState(value);
  return (
    <input value={v} autoFocus={autoFocus} placeholder={placeholder} maxLength={22}
      onChange={(e) => { setV(e.target.value); onChange(e.target.value); }}
      onKeyDown={(e) => { if (e.key === "Enter" && onEnter) onEnter(); }}
      style={{ height: 30, width: w, flex: w ? "none" : 1, minWidth: 0, borderRadius: 10, border: "1px solid rgba(255,255,255,.12)", background: "#0E131C", color: "#F1F4FA", fontSize: 13, fontWeight: 700, padding: "0 10px", outline: "none", boxShadow: "inset 0 1px 3px rgba(0,0,0,.5)" }} />
  );
}

/* ── state, position and ring ── */

function view(c, now) {
  if (c.state === "play" || c.state === "overdub") {
    const f = (((now - c.startAt) / (c.bars * BAR)) % 1 + 1) % 1;
    return { f, bar: Math.floor(f * c.bars) + 1 };
  }
  if (c.state === "rec") {
    const f = (((now - c.recStart) / BAR) % 1 + 1) % 1;
    return { f, bar: Math.floor((now - c.recStart) / BAR) + 1 };
  }
  return { f: null, bar: 0 };
}

function stateInfo(c, now, color) {
  if (c.queued) {
    if (c.queued === "rec" && c.state === "empty") {
      const n = Math.max(1, Math.ceil((c.at - now) / BAR));
      return { label: "ARMED", col: AMBER, blink: true, sub: `in ${n} bar${n > 1 ? "s" : ""}` };
    }
    return { label: "QUEUED", col: AMBER, blink: true, sub: "next bar" };
  }
  switch (c.state) {
    case "rec": return { label: "REC", col: ROSE, pulse: true };
    case "play": return { label: "PLAY", col: color, breathe: !c.mute };
    case "overdub": return { label: "OVERDUB", col: AMBER, pulse: true };
    case "stopped": return { label: "STOPPED", col: "#7B859A" };
    default: return { label: "EMPTY", col: "#566075" };
  }
}

function Badge({ info, size = 11 }) {
  return <span style={{ fontSize: size, fontWeight: 900, letterSpacing: 0.6, color: info.col, whiteSpace: "nowrap", animation: info.blink ? "blink .9s ease-in-out infinite" : info.pulse ? "recPulse 1s ease-in-out infinite" : info.breathe ? "breathe 2.4s ease-in-out infinite" : "none" }}>{info.label}</span>;
}

function Ring({ c, color, size, info, v }) {
  const pt = (f, r) => { const a = f * Math.PI * 2 - Math.PI / 2; return [50 + r * Math.cos(a), 50 + r * Math.sin(a)]; };
  const hasLoop = c.layers > 0 && c.bars > 0;
  const centre = c.state === "empty" && !c.queued ? "+" : c.state === "rec" ? "●" : c.state === "stopped" ? "❚❚" : c.queued ? "…" : String(v.bar);
  const len = c.state === "rec" ? BAR : c.bars * BAR;
  return (
    <svg viewBox="0 0 100 100" style={{ width: size, height: size, flex: "none", overflow: "visible", opacity: c.mute ? 0.55 : 1, transition: "opacity .3s" }}>
      <circle cx="50" cy="50" r="44" fill="none" stroke="rgba(255,255,255,.08)" strokeWidth="3" />
      {hasLoop && Array.from({ length: c.bars }).map((_, i) => {
        const [x1, y1] = pt(i / c.bars, 41), [x2, y2] = pt(i / c.bars, 47);
        return <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} stroke={v.f !== null && Math.floor(v.f * c.bars) === i ? color : "rgba(255,255,255,.26)"} strokeWidth="1.8" strokeLinecap="round" />;
      })}
      {[32, 24, 16, 8].map((r, i) => <circle key={i} cx="50" cy="50" r={r} fill="none" stroke={i < c.layers ? rgba(color, 0.2) : "rgba(255,255,255,.035)"} strokeWidth="1" />)}
      {len > 0 && (hasLoop || c.state === "rec") && c.events.map((e, k) => {
        const fr = (((e.rel % len) + len) % len) / len;
        const [x, y] = pt(fr, 32 - Math.min(e.layer, 3) * 8);
        return <circle key={k} cx={x} cy={y} r="3" fill={color} opacity={c.layerMute[e.layer] ? 0.15 : 0.55 + 0.15 * Math.min(e.layer, 3)} />;
      })}
      {v.f !== null && <>
        <path d={(() => { const s = Math.max(0, v.f - 0.12); const [x0, y0] = pt(s, 44), [x1, y1] = pt(v.f, 44); return `M ${x0} ${y0} A 44 44 0 0 1 ${x1} ${y1}`; })()} fill="none" stroke={info.col} strokeWidth="3.4" strokeLinecap="round" opacity="0.5" />
        <circle cx={pt(v.f, 44)[0]} cy={pt(v.f, 44)[1]} r="4.6" fill={info.col} style={{ filter: `drop-shadow(0 0 5px ${info.col})` }} />
      </>}
      <text x="50" y="50" textAnchor="middle" dominantBaseline="central" fontSize={centre.length > 1 ? 15 : 22} fontWeight="800" fill={c.state === "empty" ? "#566075" : "#F1F4FA"} fontFamily={MONO}>{centre}</text>
    </svg>
  );
}

function LayerBars({ c, color, gap = 4, h = 5 }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap, width: "100%" }}>
      {[0, 1, 2, 3].map((i) => {
        const on = i < c.layers, muted = on && c.layerMute[i], building = (c.state === "overdub" || c.state === "rec") && i === c.layers;
        return <span key={i} style={{ height: h, borderRadius: h / 2, background: muted ? "transparent" : on ? color : building ? rgba(ROSE, 0.6) : "rgba(255,255,255,.09)", border: muted ? `1px solid ${rgba(color, 0.5)}` : "none", boxShadow: on && !muted ? `0 0 5px ${rgba(color, 0.5)}` : "none", transition: "background .35s, box-shadow .35s", animation: building ? "blink .8s ease-in-out infinite" : "none" }} />;
      })}
    </div>
  );
}

/* ── the four visible actions (+ redo in focus). Long press: play → mute, undo → redo ── */

function ActionBtn({ b, c, labels }) {
  const t = useRef(null), fired = useRef(false);
  const down = () => { fired.current = false; if (b.hold && !b.dis) t.current = setTimeout(() => { fired.current = true; vib(12); b.hold(c); }, 450); };
  const up = () => { clearTimeout(t.current); if (!fired.current && !b.dis) b.fn(c); };
  return (
    <button onPointerDown={down} onPointerUp={up} onPointerLeave={() => clearTimeout(t.current)} aria-disabled={b.dis} title={b.title} className="press"
      style={{ flex: b.key === "more" && !labels ? 0.7 : 1, minWidth: 0, borderRadius: 10, border: `1px solid ${b.on ? rgba(b.col, 0.6) : "rgba(255,255,255,.06)"}`, cursor: b.dis ? "default" : "pointer", background: b.on ? rgba(b.col, 0.2) : "linear-gradient(180deg,#2F3850,#252D40)", color: b.dis ? "#434C61" : b.on ? b.col : "#C9D2E2", display: "flex", alignItems: "center", justifyContent: "center", gap: 6, boxShadow: b.on ? `0 0 12px ${rgba(b.col, 0.35)}` : "inset 0 1px 0 rgba(255,255,255,.08), 0 2px 4px rgba(0,0,0,.3)", opacity: b.dis ? 0.55 : 1, padding: 0, fontSize: 12.5, fontWeight: 800, touchAction: "none", userSelect: "none" }}>
      <Ico n={b.icon} size={labels ? 15 : 13} color={b.on && b.key === "rec" ? b.col : undefined} />
      {labels && <span>{b.text}</span>}
    </button>
  );
}

function Actions({ c, color, h, labels, handlers }) {
  const running = c.state === "play" || c.state === "overdub" || c.state === "rec";
  const busy = c.state === "rec" || c.state === "overdub";
  const btns = [
    { key: "rec", icon: "rec", text: c.state === "play" || c.state === "stopped" ? "Overdub" : "Record", title: "Record / overdub", on: busy, col: ROSE, fn: handlers.rec },
    { key: "play", icon: running ? "stop" : "play", text: running ? "Stop" : "Play", title: "Play / stop · hold to mute", on: c.state === "play", col: color, fn: handlers.play, hold: handlers.mute },
    { key: "undo", icon: "undo", text: "Undo", title: "Undo layer · hold to redo", on: false, col: AC, fn: handlers.undo, hold: handlers.redo, dis: (c.layers === 0 && c.redo.length === 0) || busy },
    ...(labels ? [{ key: "redo", icon: "redo", text: "Redo", title: "Redo layer", on: false, col: AC, fn: handlers.redo, dis: c.redo.length === 0 || busy }] : []),
    { key: "more", icon: "more", text: "More", title: "Case settings", on: false, col: AC, fn: handlers.more },
  ];
  return (
    <div style={{ display: "flex", gap: 5, height: h, flex: "none" }}>
      {btns.map((b) => <ActionBtn key={b.key} b={b} c={c} labels={labels} />)}
    </div>
  );
}

const frame = (c, color) => ({
  position: "relative", borderRadius: 16, overflow: "hidden", minWidth: 0, minHeight: 0, height: "100%",
  background: c.state === "play" || c.state === "overdub" || c.state === "rec" ? `linear-gradient(160deg, ${rgba(color, c.mute ? 0.08 : 0.2)}, #161C2B 62%)` : "linear-gradient(160deg,#222B3E,#161C2B)",
  border: `1px solid ${c.state === "empty" ? "rgba(255,255,255,.06)" : rgba(color, 0.4)}`,
  boxShadow: "inset 1px 1px 0 rgba(255,255,255,.07), 0 6px 12px rgba(0,0,0,.38)",
  transition: "background .4s, border-color .4s",
});

function posText(c, v, info) {
  if (c.queued) return info.sub;
  if (c.state === "empty") return c.redo.length ? "undone · hold ↶" : "tap ● to arm";
  if (c.state === "stopped") return `${c.bars} bar${c.bars > 1 ? "s" : ""}`;
  if (c.state === "rec") return `bar ${v.bar}`;
  return `${v.bar} / ${c.bars}`;
}

function CaseFull({ c, now, color, handlers, onFocus }) {
  const v = view(c, now), info = stateInfo(c, now, color);
  return (
    <div style={{ ...frame(c, color), padding: "7px 9px", display: "flex", flexDirection: "column", gap: 6 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 6, height: 18, flex: "none" }}>
        <button onClick={() => onFocus(c.id)} title="Focus" style={{ border: 0, background: "transparent", padding: 0, cursor: "pointer", color: "#C9D2E2", fontFamily: MONO, fontSize: 11.5, fontWeight: 800 }}>L{c.id + 1}</button>
        <Badge info={info} />
        {c.mute && c.layers > 0 && <span style={{ fontSize: 8.5, fontWeight: 900, letterSpacing: 0.6, color: AMBER, border: `1px solid ${rgba(AMBER, 0.5)}`, borderRadius: 5, padding: "0 4px" }}>MUTE</span>}
        <div style={{ flex: 1 }} />
        {c.layers > 0 && c.bars > 0 && <span style={{ fontFamily: MONO, fontSize: 10, fontWeight: 800, color: "#8E99AE" }}>{c.bars} bar{c.bars > 1 ? "s" : ""}</span>}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 9, flex: 1, minHeight: 0 }}>
        <Ring c={c} color={color} size={68} info={info} v={v} />
        <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 6, justifyContent: "center" }}>
          <span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 800, color: c.state === "empty" ? "#566075" : "#C9D2E2", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{posText(c, v, info)}</span>
          <LayerBars c={c} color={color} gap={4} h={6} />
        </div>
      </div>
      <Actions c={c} color={color} h={28} handlers={handlers} />
    </div>
  );
}

function CaseChip({ c, now, color, active, onSelect }) {
  const info = stateInfo(c, now, color);
  return (
    <button onClick={() => onSelect(c.id)} className="press" style={{ height: 33, borderRadius: 10, padding: "0 10px", display: "flex", alignItems: "center", gap: 8, cursor: "pointer", border: `1px solid ${active ? rgba(color, 0.7) : "rgba(255,255,255,.06)"}`, background: active ? rgba(color, 0.18) : "linear-gradient(180deg,#222B3E,#1A2132)", color: "#C9D2E2", flex: "none" }}>
      <span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 800 }}>L{c.id + 1}</span>
      <span style={{ width: 7, height: 7, borderRadius: 4, background: info.col, boxShadow: c.state === "empty" ? "none" : `0 0 6px ${info.col}`, animation: info.blink || info.pulse ? "recPulse .9s ease-in-out infinite" : "none" }} />
      <span style={{ flex: 1, textAlign: "right", fontFamily: MONO, fontSize: 9.5, fontWeight: 800, color: "#7B859A" }}>{c.layers ? `${c.bars}b·${c.layers}` : ""}</span>
    </button>
  );
}

function VolSlider({ c, color, onTouch }) {
  const ref = useRef(null);
  const set = (e) => { const r = ref.current.getBoundingClientRect(); c.vol = Math.round(Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)) * 100); onTouch(); };
  return (
    <div ref={ref} onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); set(e); }} onPointerMove={(e) => { if (e.buttons) set(e); }}
      style={{ position: "relative", flex: 1, height: 18, cursor: "pointer", touchAction: "none" }}>
      <div style={{ position: "absolute", left: 0, right: 0, top: 7, height: 4, borderRadius: 2, background: "#0E131C", boxShadow: "inset 0 1px 2px rgba(0,0,0,.6)" }} />
      <div style={{ position: "absolute", left: 0, top: 7, height: 4, width: `${c.vol}%`, borderRadius: 2, background: `linear-gradient(90deg, ${rgba(color, 0.4)}, ${color})` }} />
      <div style={{ position: "absolute", top: 2, left: `${c.vol}%`, marginLeft: -7, width: 14, height: 14, borderRadius: 5, background: "linear-gradient(180deg,#6E7799,#3F4766)", border: "1px solid rgba(255,255,255,.16)", boxShadow: "0 2px 4px rgba(0,0,0,.5)" }} />
    </div>
  );
}

function FocusView({ c, now, color, handlers, onBack, cases, colorOf, onSelect, onRemoveLayer, onTouch }) {
  const v = view(c, now), info = stateInfo(c, now, color);
  const perLayer = (i) => c.events.filter((e) => e.layer === i).length;
  return (
    <div style={{ position: "absolute", inset: 0, display: "flex", gap: 8, animation: "fadeIn .25s" }}>
      <div style={{ ...frame(c, color), flex: 1, padding: "10px 14px", display: "flex", flexDirection: "column", gap: 8 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, height: 30, flex: "none" }}>
          <button onClick={onBack} className="press" style={{ height: 28, padding: "0 11px", borderRadius: 9, border: "1px solid rgba(255,255,255,.1)", background: "rgba(255,255,255,.05)", color: "#C9D2E2", fontSize: 11.5, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", gap: 6 }}><Ico n="back" size={12} /> All cases</button>
          <span style={{ fontFamily: MONO, fontSize: 14, fontWeight: 800, color: "#E9EDF5" }}>L{c.id + 1}</span>
          {c.name && <span style={{ fontSize: 13, fontWeight: 800, color: "#C9D2E2" }}>{c.name}</span>}
          <Badge info={info} size={13} />
          <div style={{ flex: 1 }} />
          {c.layers > 0 && <span style={{ fontFamily: MONO, fontSize: 12, fontWeight: 800, color: "#8E99AE" }}>{c.bars} bar{c.bars > 1 ? "s" : ""} · {c.layers} layer{c.layers > 1 ? "s" : ""}</span>}
        </div>
        <div style={{ display: "flex", gap: 18, flex: 1, minHeight: 0, alignItems: "center" }}>
          <Ring c={c} color={color} size={158} info={info} v={v} />
          <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 5, justifyContent: "center" }}>
            <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: 1.3, color: "#6F7A90" }}>LAYERS</span>
            {[0, 1, 2, 3].map((i) => i < c.layers ? (
              <div key={i} style={{ height: 30, borderRadius: 10, border: `1px solid ${rgba(color, c.layerMute[i] ? 0.2 : 0.4)}`, background: rgba(color, c.layerMute[i] ? 0.04 : 0.12), display: "flex", alignItems: "center", gap: 8, padding: "0 5px 0 11px", opacity: c.layerMute[i] ? 0.7 : 1 }}>
                <span style={{ width: 6, height: 15, borderRadius: 3, background: c.layerMute[i] ? "transparent" : color, border: c.layerMute[i] ? `1px solid ${color}` : "none" }} />
                <span style={{ flex: 1, fontSize: 12.5, fontWeight: 800, color: "#E9EDF5" }}>Layer {i + 1}</span>
                <span style={{ fontFamily: MONO, fontSize: 10.5, color: "#8E99AE" }}>{perLayer(i)} hits</span>
                <button onClick={() => { c.layerMute[i] = !c.layerMute[i]; onTouch(); }} className="press" title="Mute layer" style={{ width: 24, height: 24, borderRadius: 8, border: 0, background: c.layerMute[i] ? rgba(AMBER, 0.25) : "rgba(255,255,255,.08)", color: c.layerMute[i] ? AMBER : "#C9D2E2", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n={c.layerMute[i] ? "mute" : "speaker"} size={12} sw={2.2} /></button>
                <button onClick={() => onRemoveLayer(c, i)} className="press" title="Remove layer" style={{ width: 24, height: 24, borderRadius: 8, border: 0, background: "rgba(255,255,255,.08)", color: "#C9D2E2", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={11} sw={2.6} /></button>
              </div>
            ) : (
              <div key={i} style={{ height: 30, borderRadius: 10, border: "1px dashed rgba(255,255,255,.1)", display: "flex", alignItems: "center", padding: "0 12px", fontSize: 12.5, fontWeight: 700, color: "#4F596E" }}>Layer {i + 1}</div>
            ))}
            <div style={{ display: "flex", alignItems: "center", gap: 10, height: 28, marginTop: 2 }}>
              <span style={{ color: "#8E99AE", display: "inline-flex" }}><Ico n="speaker" size={14} /></span>
              <VolSlider c={c} color={color} onTouch={onTouch} />
              <span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 800, color: "#E9EDF5", width: 34, textAlign: "right" }}>{c.vol}%</span>
              <button onClick={() => { c.mute = !c.mute; onTouch(); }} className="press" style={{ height: 26, padding: "0 10px", borderRadius: 9, border: `1px solid ${c.mute ? rgba(AMBER, 0.6) : "rgba(255,255,255,.1)"}`, background: c.mute ? rgba(AMBER, 0.2) : "rgba(255,255,255,.05)", color: c.mute ? AMBER : "#C9D2E2", fontSize: 11, fontWeight: 800, cursor: "pointer" }}>Mute</button>
            </div>
          </div>
        </div>
        <Actions c={c} color={color} h={40} labels handlers={handlers} />
      </div>
      <div style={{ width: 96, display: "flex", flexDirection: "column", gap: 5 }}>
        {cases.map((x) => <CaseChip key={x.id} c={x} now={now} color={colorOf(x)} active={x.id === c.id} onSelect={onSelect} />)}
      </div>
    </div>
  );
}

/* ── case settings sheet (⋯) ── */

function ActionSheet({ c, color, onClose, onDouble, onHalf, onDuplicate, onClear, onMidi, onTouch }) {
  const idle = c.state !== "rec" && c.state !== "overdub";
  const tiles = [
    ["×2", "Double", c.layers > 0 && c.bars * 2 <= 8 && idle, onDouble],
    ["÷2", "Half", c.bars > 1 && c.layers > 0 && idle, onHalf],
    ["⧉", "Duplicate", c.layers > 0 && c.state !== "rec", onDuplicate],
    ["⌁", "MIDI learn", true, onMidi],
    ["✕", "Clear", c.state !== "empty" || c.redo.length > 0, onClear],
  ];
  const chip = (k, l) => (
    <button key={k} onClick={() => { c.src[k] = !c.src[k]; onTouch(); }} className="press" style={{ height: 24, padding: "0 11px", borderRadius: 12, border: `1px solid ${c.src[k] ? rgba(AC, 0.55) : "rgba(255,255,255,.1)"}`, background: c.src[k] ? rgba(AC, 0.16) : "transparent", color: c.src[k] ? "#D5E3FA" : "#7B859A", fontSize: 11.5, fontWeight: 800, cursor: "pointer" }}>{l}</button>
  );
  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 30, animation: "fadeIn .2s" }}>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.5)", borderRadius: 16 }} />
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, borderRadius: 16, padding: "10px 12px 12px", background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 -12px 30px rgba(0,0,0,.5)", animation: "sheetIn .26s cubic-bezier(.2,.9,.25,1)", display: "flex", flexDirection: "column", gap: 9 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ width: 8, height: 8, borderRadius: 4, background: color, boxShadow: `0 0 8px ${color}` }} />
          <span style={{ flex: 1, fontSize: 14, fontWeight: 900, color: "#F1F4FA" }}>L{c.id + 1}{c.name ? ` · ${c.name}` : ""} · {c.layers ? `${c.bars} bar${c.bars > 1 ? "s" : ""} · ${c.layers} layer${c.layers > 1 ? "s" : ""}` : "empty"}</span>
          <button onClick={onClose} style={{ width: 28, height: 28, borderRadius: 14, border: 0, background: "rgba(255,255,255,.07)", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={12} /></button>
        </div>
        <div style={{ display: "flex", gap: 6 }}>
          {tiles.map(([g, l, ok, fn]) => (
            <button key={l} disabled={!ok} onClick={() => fn(c)} className="press" style={{ flex: 1, height: 48, borderRadius: 11, border: "1px solid rgba(255,255,255,.06)", cursor: ok ? "pointer" : "default", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 2, background: ok ? "linear-gradient(180deg,rgba(255,255,255,.08),rgba(255,255,255,.03))" : "transparent", color: ok ? "#E9EDF5" : "#4F596E" }}>
              <span style={{ fontFamily: MONO, fontSize: 15, fontWeight: 800, color: ok ? (l === "Clear" ? "#E3A0B4" : AC) : "#4F596E", lineHeight: 1 }}>{g}</span>
              <span style={{ fontSize: 10.5, fontWeight: 800 }}>{l}</span>
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 14, alignItems: "flex-end" }}>
          <Group label="RECORDS"><div style={{ display: "flex", gap: 5 }}>{chip("pads", "Pads")}{chip("tonic", "Tonic")}{chip("keys", "Keys")}</div></Group>
          <Group label="STOP AT"><Seg options={[["end", "End of loop"], ["now", "Now"]]} value={c.stopMode} onChange={(k) => { c.stopMode = k; onTouch(); }} w={150} h={22} fs={10.5} /></Group>
          <Group label="AFTER RECORDING"><Seg options={[["play", "Play"], ["overdub", "Overdub"]]} value={c.after} onChange={(k) => { c.after = k; onTouch(); }} w={130} h={22} fs={10.5} /></Group>
          <Group label="MODE"><Seg options={[[false, "Loop"], [true, "One-shot"]]} value={c.oneshot} onChange={(k) => { c.oneshot = k; onTouch(); }} w={118} h={22} fs={10.5} /></Group>
        </div>
        <Group label="NAME" style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
          <TextField value={c.name} onChange={(t) => { c.name = t; }} placeholder={`L${c.id + 1}`} />
        </Group>
      </div>
    </div>
  );
}

/* ── Loop patches: saved with the setlist ── */

function PatchWindow({ patches, activeId, dirty, cases, colorOf, onClose, onLoad, onSave, onSaveNew, onRevert, onRename, onDuplicate, onOverwrite, onDelete }) {
  const [open, setOpen] = useState(null);
  const [renaming, setRenaming] = useState(null);
  const active = patches.find((p) => p.id === activeId);
  const stats = (cs) => { const used = cs.filter((x) => x.layers > 0); return { used: used.length, bars: used.reduce((m, x) => Math.max(m, x.bars), 0) }; };
  const mini = (cs) => (
    <div style={{ display: "flex", gap: 3 }}>{cs.map((x, i) => <span key={i} style={{ width: 13, height: 5, borderRadius: 2, background: x.layers > 0 ? PAL[i % 8] : "rgba(255,255,255,.1)" }} />)}</div>
  );
  const chipBtn = (label, fn, danger, dis) => (
    <button key={label} disabled={dis} onClick={fn} className="press" style={{ height: 26, padding: "0 10px", borderRadius: 9, border: `1px solid ${danger ? "rgba(194,88,122,.4)" : "rgba(255,255,255,.1)"}`, background: danger ? "rgba(194,88,122,.1)" : "rgba(255,255,255,.05)", color: dis ? "#4F596E" : danger ? "#E3A0B4" : "#C9D2E2", fontSize: 11, fontWeight: 800, cursor: dis ? "default" : "pointer" }}>{label}</button>
  );
  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 55, animation: "fadeIn .2s" }}>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.62)", backdropFilter: "blur(2px)" }} />
      <div style={{ position: "absolute", left: 120, right: 120, top: 58, bottom: 12, borderRadius: 22, overflow: "hidden", background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 26px 64px rgba(0,0,0,.6)", animation: "winIn .32s cubic-bezier(.2,.9,.25,1)", display: "flex", flexDirection: "column" }}>
        <div style={{ height: 62, display: "flex", alignItems: "center", gap: 12, padding: "0 16px", borderBottom: "1px solid rgba(255,255,255,.06)", flex: "none" }}>
          <span style={{ width: 4, height: 32, borderRadius: 2, background: AC, boxShadow: `0 0 12px ${rgba(AC, 0.5)}` }} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 17, fontWeight: 900, color: "#F4F6FB" }}>Loop patches</div>
            <div style={{ fontSize: 11.5, color: "#7B859A", marginTop: 2 }}>Setlist · {SETLIST} · saved with the setlist</div>
          </div>
          <button onClick={onClose} className="press" style={{ width: 34, height: 34, borderRadius: 17, border: "1px solid rgba(255,255,255,.1)", background: "linear-gradient(180deg,#2F3850,#252D40)", color: "#E3E8F2", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={14} sw={2.2} /></button>
        </div>
        <div style={{ flex: 1, minHeight: 0, display: "flex", gap: 14, padding: "12px 16px 14px" }}>
          <div className="noscroll" style={{ flex: 1, minWidth: 0, overflowY: "auto", display: "flex", flexDirection: "column", gap: 6 }}>
            {patches.map((p) => {
              const on = p.id === activeId, st = stats(p.cases), exp = open === p.id;
              return (
                <div key={p.id} style={{ borderRadius: 13, border: `1px solid ${on ? rgba(AC, 0.55) : "rgba(255,255,255,.06)"}`, background: on ? `linear-gradient(180deg, ${rgba(AC, 0.16)}, ${rgba(AC, 0.06)})` : "linear-gradient(180deg,rgba(255,255,255,.065),rgba(255,255,255,.03))", flex: "none" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, height: 52, padding: "0 8px 0 12px" }}>
                    <div onClick={() => onLoad(p.id)} style={{ flex: 1, minWidth: 0, cursor: "pointer", display: "flex", flexDirection: "column", gap: 5 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                        <span style={{ fontSize: 13.5, fontWeight: 800, color: "#F1F4FA", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{p.name}</span>
                        {on && <span style={{ fontSize: 8.5, fontWeight: 900, letterSpacing: 1, color: AC, border: `1px solid ${rgba(AC, 0.5)}`, borderRadius: 5, padding: "1px 5px" }}>ACTIVE</span>}
                        {on && dirty && <span style={{ width: 7, height: 7, borderRadius: 4, background: AMBER, boxShadow: `0 0 6px ${AMBER}` }} />}
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>{mini(p.cases)}<span style={{ fontFamily: MONO, fontSize: 10, fontWeight: 700, color: "#7B859A" }}>{st.used ? `${st.used} case${st.used > 1 ? "s" : ""} · ${st.bars} bar${st.bars > 1 ? "s" : ""} max` : "empty"}</span></div>
                    </div>
                    <button onClick={() => setOpen(exp ? null : p.id)} className="press" style={{ width: 30, height: 30, borderRadius: 10, border: 0, background: exp ? "rgba(255,255,255,.1)" : "transparent", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="more" size={15} /></button>
                  </div>
                  {exp && (
                    <div style={{ display: "flex", gap: 6, alignItems: "center", padding: "0 10px 10px", flexWrap: "wrap", animation: "fadeIn .2s" }}>
                      {renaming === p.id ? (
                        <>
                          <TextField value={p.name} onChange={(t) => onRename(p.id, t)} autoFocus onEnter={() => setRenaming(null)} />
                          {chipBtn("Done", () => setRenaming(null))}
                        </>
                      ) : (
                        <>
                          {chipBtn("Rename", () => setRenaming(p.id))}
                          {chipBtn("Duplicate", () => onDuplicate(p.id))}
                          {chipBtn("Overwrite with current", () => onOverwrite(p.id), false, on)}
                          {chipBtn("Delete", () => onDelete(p.id), true, on || patches.length <= 1)}
                        </>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          <div style={{ width: 196, flex: "none", display: "flex", flexDirection: "column", gap: 8 }}>
            <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: 1.3, color: "#6F7A90" }}>CURRENT</span>
            <div style={{ borderRadius: 13, border: "1px solid rgba(255,255,255,.06)", background: "linear-gradient(180deg,rgba(255,255,255,.065),rgba(255,255,255,.03))", padding: 11, display: "flex", flexDirection: "column", gap: 8 }}>
              <div style={{ fontSize: 14, fontWeight: 900, color: "#F1F4FA", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{active ? active.name : "Unsaved loops"}</div>
              <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 11.5, fontWeight: 800, color: dirty ? AMBER : SAGE }}>
                <span style={{ width: 7, height: 7, borderRadius: 4, background: dirty ? AMBER : SAGE, boxShadow: `0 0 6px ${dirty ? AMBER : SAGE}` }} />
                {dirty ? "Unsaved changes" : "Saved"}
              </div>
              <div style={{ display: "flex", gap: 3 }}>{cases.map((x, i) => <span key={i} style={{ flex: 1, height: 6, borderRadius: 3, background: x.layers > 0 ? colorOf(x) : "rgba(255,255,255,.1)" }} />)}</div>
            </div>
            <button onClick={onSave} disabled={!dirty} className="press" style={{ height: 38, borderRadius: 12, border: 0, cursor: dirty ? "pointer" : "default", background: dirty ? "linear-gradient(180deg,#9DBDEB,#7FA3DD)" : "rgba(255,255,255,.06)", color: dirty ? "#0F1726" : "#566075", fontSize: 13, fontWeight: 900 }}>Save</button>
            <button onClick={onSaveNew} className="press" style={{ height: 36, borderRadius: 12, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.05)", color: "#E3E8F2", fontSize: 12.5, fontWeight: 800, cursor: "pointer" }}>Save as new patch</button>
            <button onClick={onRevert} disabled={!dirty} className="press" style={{ height: 32, borderRadius: 11, border: "1px solid rgba(255,255,255,.1)", background: "transparent", color: dirty ? "#C9D2E2" : "#4F596E", fontSize: 12, fontWeight: 800, cursor: dirty ? "pointer" : "default" }}>Revert to saved</button>
            <div style={{ flex: 1 }} />
            <div style={{ fontSize: 10.5, color: "#59647A", lineHeight: 1.5 }}>Patches belong to the setlist. They are stored with it and travel with it.</div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ MAIN ═══════════════ */

export default function LoopsPreview() {
  const cases = useRef(Array.from({ length: 8 }, (_, i) => mk(i)));
  const [, setTick] = useState(0);
  const touch = () => setTick((x) => (x + 1) % 100000);
  const [page, setPage] = useState("looper");
  const [menu, setMenu] = useState(false);
  const [quant, setQuant] = useState("bar");
  const [lenSel, setLenSel] = useState("auto");
  const [countIn, setCountIn] = useState(1);
  const [sim, setSim] = useState(true);
  const [focus, setFocus] = useState(null);
  const [sheet, setSheet] = useState(null);
  const [confirm, setConfirm] = useState(false);
  const [toast, setToast] = useState(null);
  const [patches, setPatches] = useState(INIT_PATCHES);
  const [activeId, setActiveId] = useState("p2");
  const [patchWin, setPatchWin] = useState(false);
  const [ask, setAsk] = useState(null);
  const quantRef = useRef(quant); quantRef.current = quant;
  const lenRef = useRef(lenSel); lenRef.current = lenSel;
  const countRef = useRef(countIn); countRef.current = countIn;
  const simRef = useRef(sim); simRef.current = sim;
  const toastT = useRef(null);
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });

  useEffect(() => {
    const on = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);
  const say = (t, undoFn) => { clearTimeout(toastT.current); setToast({ t, k: Date.now(), undoFn }); toastT.current = setTimeout(() => setToast(null), undoFn ? 4200 : 1700); };

  /* ── engine (visual only) ── */
  const finishRec = (c, end) => {
    c.bars = c.lenMode === "auto" ? Math.max(1, Math.round((end - c.recStart) / BAR)) : c.lenMode;
    c.events.forEach((e) => { e.rel = e.rel % (c.bars * BAR); });
    c.layers = 1; c.state = c.after === "overdub" ? "overdub" : "play"; c.startAt = c.recStart; c.queued = null; c.redo = [];
  };
  const apply = (c, type, at) => {
    if (type === "rec") { c.state = "rec"; c.recStart = at; c.events = []; c.redo = []; }
    else if (type === "finish") finishRec(c, at);
    else if (type === "overdub") { if (c.state === "stopped") c.startAt = at; c.state = "overdub"; }
    else if (type === "commit") { c.layers = Math.min(4, c.layers + 1); c.state = "play"; c.redo = []; }
    else if (type === "play") { c.state = "play"; c.startAt = at; }
    else if (type === "stop") { if (c.state === "overdub") c.layers = Math.min(4, c.layers + 1); c.state = "stopped"; }
  };
  const queue = (c, type) => {
    const now = performance.now();
    let at = nextAt(now, quantRef.current);
    if (type === "rec" && countRef.current > 0) at += countRef.current * BAR;
    if (type === "stop" && c.stopMode === "now") at = now;
    c.queued = type; c.at = at;
  };
  const reset = (c) => Object.assign(c, mk(c.id));
  const removeLayer = (c, i) => {
    c.events = c.events.filter((e) => e.layer !== i).map((e) => (e.layer > i ? { ...e, layer: e.layer - 1 } : e));
    c.layerMute.splice(i, 1); c.layerMute.push(false);
    c.layers -= 1;
    if (c.layers <= 0) reset(c);
  };

  useEffect(() => {
    let raf, last = 0;
    const loop = (ts) => {
      raf = requestAnimationFrame(loop);
      if (ts - last < 33) return;
      last = ts;
      const now = performance.now();
      cases.current.forEach((c) => {
        if (c.queued && now >= c.at) { apply(c, c.queued, c.at); c.queued = null; }
        if (c.state === "rec" && c.lenMode !== "auto" && now - c.recStart >= c.lenMode * BAR) finishRec(c, c.recStart + c.lenMode * BAR);
        if (simRef.current && (c.state === "rec" || c.state === "overdub") && Math.random() < 0.12) {
          const loopLen = c.bars * BAR;
          c.events.push({ rel: c.state === "rec" ? now - c.recStart : (((now - c.startAt) % loopLen) + loopLen) % loopLen, layer: c.state === "rec" ? 0 : c.layers });
        }
      });
      setTick((x) => (x + 1) % 100000);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);

  /* ── case actions ── */
  const busyOther = (c) => cases.current.some((x) => x !== c && (x.state === "rec" || x.state === "overdub" || x.queued === "rec"));
  const recPress = (c) => {
    vib(6);
    if (c.queued && c.state === "empty") { c.queued = null; return; }
    if (c.state === "empty") { if (busyOther(c)) { say("One case records at a time"); return; } c.lenMode = lenRef.current; queue(c, "rec"); return; }
    if (c.state === "rec") { queue(c, "finish"); return; }
    if (c.state === "play" || c.state === "stopped") { if (c.layers >= 4) { say("4 layers max"); return; } queue(c, "overdub"); return; }
    if (c.state === "overdub") queue(c, "commit");
  };
  const playPress = (c) => {
    vib(6);
    if (c.state === "rec") queue(c, "finish");
    else if (c.state === "play" || c.state === "overdub") queue(c, "stop");
    else if (c.state === "stopped") queue(c, "play");
    else say("Record a layer first");
  };
  const mutePress = (c) => { if (c.layers === 0) return; c.mute = !c.mute; say(`L${c.id + 1} ${c.mute ? "muted" : "unmuted"}`); };
  const undo = (c) => {
    if (c.layers === 0 || c.state === "rec" || c.state === "overdub") return;
    const i = c.layers - 1;
    c.redo.push({ events: c.events.filter((e) => e.layer === i).map((e) => ({ ...e })), mute: c.layerMute[i], bars: c.bars });
    c.events = c.events.filter((e) => e.layer !== i); c.layerMute[i] = false; c.layers -= 1;
    if (c.layers === 0) { c.state = "empty"; c.queued = null; }
    vib(6); say(`L${c.id + 1} · last layer removed`);
  };
  const redo = (c) => {
    if (!c.redo.length) { say("Nothing to redo"); return; }
    const r = c.redo.pop();
    c.bars = r.bars; c.events = c.events.concat(r.events.map((e) => ({ ...e, layer: c.layers })));
    c.layerMute[c.layers] = r.mute; c.layers += 1;
    if (c.state === "empty") { c.state = "stopped"; c.startAt = nextAt(performance.now(), "bar"); }
    vib(6); say(`L${c.id + 1} · layer restored`);
  };
  const stopAll = () => cases.current.forEach((c) => { if (c.state === "play" || c.state === "overdub") queue(c, "stop"); });
  const playAll = () => cases.current.forEach((c) => { if (c.state === "stopped" && c.layers > 0) queue(c, "play"); });
  const clearAll = () => { cases.current.forEach(reset); setConfirm(false); setFocus(null); say("All cases cleared"); };
  const doDouble = (c) => { const old = c.bars * BAR; c.events = c.events.concat(c.events.map((e) => ({ ...e, rel: e.rel + old }))); c.bars *= 2; setSheet(null); say(`L${c.id + 1} · ${c.bars} bars`); };
  const doHalf = (c) => { c.bars = c.bars / 2; c.events.forEach((e) => { e.rel = e.rel % (c.bars * BAR); }); setSheet(null); say(`L${c.id + 1} · ${c.bars} bar${c.bars > 1 ? "s" : ""}`); };
  const doDuplicate = (c) => {
    const t = cases.current.find((x) => x.id !== c.id && x.state === "empty" && x.layers === 0);
    if (!t) { say("No empty case"); return; }
    Object.assign(t, mk(t.id), snapCase(c), { state: "stopped", startAt: c.startAt });
    setSheet(null); say(`Copied to L${t.id + 1} · press play`);
  };
  const doClear = (c) => {
    const saved = { ...snapCase(c), state: c.layers ? "stopped" : "empty", startAt: c.startAt };
    reset(c); setSheet(null);
    say(`L${c.id + 1} cleared`, () => { Object.assign(c, mk(c.id), saved); });
  };
  const doMidi = (c) => { setSheet(null); say(`MIDI learn: map the actions of L${c.id + 1}`); };
  const handlers = { rec: recPress, play: playPress, mute: mutePress, undo, redo, more: (c) => setSheet(c.id) };

  /* ── patches ── */
  const activePatch = patches.find((p) => p.id === activeId);
  const dirty = !activePatch || sigOf(cases.current) !== sigOf(activePatch.cases);
  const applyPatch = (p) => {
    cases.current.forEach((c, i) => {
      const s = p.cases[i];
      Object.assign(c, mk(i), { ...s, events: s.events.map((e) => ({ ...e })), layerMute: [...s.layerMute], src: { ...s.src } });
      if (c.layers > 0) { c.state = "stopped"; c.startAt = nextAt(performance.now(), "bar"); queue(c, "play"); }
    });
    setActiveId(p.id); setFocus(null); setSheet(null);
    say(`“${p.name}” loaded · starts on the next bar`);
  };
  const requestLoad = (id) => {
    const p = patches.find((x) => x.id === id);
    if (!p) return;
    if (id === activeId && !dirty) return;
    if (dirty && activePatch) { setAsk({ to: id }); return; }
    applyPatch(p);
  };
  const stepPatch = (d) => {
    const i = patches.findIndex((p) => p.id === activeId);
    const n = patches[(i + d + patches.length) % patches.length];
    requestLoad(n.id);
  };
  const savePatch = () => { setPatches((ps) => ps.map((p) => (p.id === activeId ? { ...p, cases: cases.current.map(snapCase) } : p))); say("Patch saved with the setlist"); };
  const saveNew = () => {
    const id = "p" + Date.now();
    const name = `Loop patch ${patches.length + 1}`;
    setPatches((ps) => [...ps, { id, name, cases: cases.current.map(snapCase) }]); setActiveId(id); say(`“${name}” saved`);
  };
  const revert = () => { const p = patches.find((x) => x.id === activeId); if (p) applyPatch(p); };
  const renamePatch = (id, name) => setPatches((ps) => ps.map((p) => (p.id === id ? { ...p, name } : p)));
  const dupPatch = (id) => setPatches((ps) => { const p = ps.find((x) => x.id === id); const i = ps.indexOf(p); const copy = { ...p, id: "p" + Date.now(), name: p.name + " copy", cases: p.cases.map((s) => ({ ...s, events: s.events.map((e) => ({ ...e })), layerMute: [...s.layerMute], src: { ...s.src } })) }; const n = [...ps]; n.splice(i + 1, 0, copy); return n; });
  const overwritePatch = (id) => { setPatches((ps) => ps.map((p) => (p.id === id ? { ...p, cases: cases.current.map(snapCase) } : p))); say("Patch overwritten"); };
  const deletePatch = (id) => {
    const p = patches.find((x) => x.id === id), idx = patches.indexOf(p);
    setPatches((ps) => ps.filter((x) => x.id !== id));
    say(`“${p.name}” deleted`, () => setPatches((ps) => { const n = [...ps]; n.splice(Math.min(idx, n.length), 0, p); return n; }));
  };

  const now = performance.now();
  const beatN = Math.floor(((now - T0) % BAR) / BEAT);
  const barN = Math.floor((now - T0) / BAR) + 1;
  const used = cases.current.filter((c) => c.layers > 0).length;
  const anyPlaying = cases.current.some((c) => c.state === "play" || c.state === "overdub");
  const anyStopped = cases.current.some((c) => c.state === "stopped" && c.layers > 0);
  const colorOf = (c) => PAL[c.id % PAL.length];
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 150) / H, 1.5));
  const portrait = vp.h > vp.w;
  const sheetCase = sheet !== null ? cases.current[sheet] : null;
  const focusCase = focus !== null ? cases.current[focus] : null;
  const pages = [["drumpad", "DrumPad"], ["looper", "LiveLooper"]];
  const askTarget = ask ? patches.find((p) => p.id === ask.to) : null;

  return (
    <div className="lp" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
          <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.04, mixBlendMode: "overlay", pointerEvents: "none" }} />

          {/* top bar */}
          <div style={{ position: "absolute", left: 10, right: 10, top: 8, height: 44, display: "flex", alignItems: "center", gap: 8, zIndex: 45 }}>
            <Pill><Ico n="back" size={15} /> Mixer</Pill>
            <div style={{ position: "relative" }}>
              <Pill onClick={() => setMenu((m) => !m)} style={{ minWidth: 150, justifyContent: "space-between", borderColor: menu ? "rgba(143,178,232,.5)" : "rgba(255,255,255,.07)" }}>
                <span>{page === "looper" ? "LiveLooper" : "DrumPad"}</span>
                <span style={{ color: AC, display: "inline-flex", transform: menu ? "rotate(180deg)" : "none", transition: "transform .25s" }}><Ico n="down" size={16} sw={2.4} /></span>
              </Pill>
              {menu && (
                <>
                  <div onClick={() => setMenu(false)} style={{ position: "fixed", inset: 0 }} />
                  <div style={{ position: "absolute", left: 0, top: 50, width: 190, borderRadius: 14, padding: 5, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 16px 40px rgba(0,0,0,.55)", transformOrigin: "top", animation: "menuIn .18s ease-out", zIndex: 5 }}>
                    {pages.map(([k, l]) => (
                      <button key={k} onClick={() => { setPage(k); setMenu(false); setFocus(null); setSheet(null); vib(6); }} className="press" style={{ width: "100%", height: 40, border: 0, borderRadius: 10, cursor: "pointer", display: "flex", alignItems: "center", gap: 10, padding: "0 12px", background: page === k ? "rgba(143,178,232,.14)" : "transparent", color: page === k ? "#F1F4FA" : "#B5BFD3", fontSize: 14, fontWeight: 800 }}>
                        <span style={{ flex: 1, textAlign: "left" }}>{l}</span>
                        {page === k && <span style={{ color: AC, display: "inline-flex" }}><Ico n="check" size={15} sw={2.6} /></span>}
                      </button>
                    ))}
                  </div>
                </>
              )}
            </div>
            {page === "looper" && (
              <div style={{ display: "flex", alignItems: "center", height: 44, borderRadius: 14, overflow: "hidden", background: "linear-gradient(180deg,#2A3347,#202839)", border: "1px solid rgba(255,255,255,.07)", boxShadow: "0 4px 10px rgba(0,0,0,.3)" }}>
                <button onClick={() => stepPatch(-1)} className="press" title="Previous patch" style={{ width: 34, height: 44, border: 0, background: "transparent", color: "#C9D2E2", cursor: "pointer", display: "grid", placeItems: "center" }}><Ico n="left" size={16} sw={2.4} /></button>
                <button onClick={() => setPatchWin(true)} className="press" title="Loop patches" style={{ height: 44, minWidth: 156, maxWidth: 190, border: 0, borderLeft: "1px solid rgba(255,255,255,.06)", borderRight: "1px solid rgba(255,255,255,.06)", background: "transparent", color: "#E3E8F2", cursor: "pointer", display: "flex", alignItems: "center", gap: 8, padding: "0 12px" }}>
                  <span style={{ color: AC, display: "inline-flex" }}><Ico n="layers" size={15} sw={2} /></span>
                  <span style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", lineHeight: 1.1, minWidth: 0 }}>
                    <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: 1.1, color: "#6F7A90" }}>LOOP PATCH</span>
                    <span style={{ fontSize: 13, fontWeight: 800, maxWidth: 112, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{activePatch ? activePatch.name : "Unsaved"}</span>
                  </span>
                  {dirty && <span style={{ width: 7, height: 7, borderRadius: 4, background: AMBER, boxShadow: `0 0 6px ${AMBER}`, flex: "none" }} />}
                </button>
                <button onClick={() => stepPatch(1)} className="press" title="Next patch" style={{ width: 34, height: 44, border: 0, background: "transparent", color: "#C9D2E2", cursor: "pointer", display: "grid", placeItems: "center" }}><Ico n="right" size={16} sw={2.4} /></button>
              </div>
            )}
            <div style={{ flex: 1 }} />
            <Pill style={{ padding: "0 8px", gap: 6 }}>
              <span style={{ width: 28, height: 28, borderRadius: 14, background: "#202839", display: "grid", placeItems: "center" }}>−</span>
              <span style={{ fontFamily: MONO, minWidth: 84, textAlign: "center" }}>{BPM} BPM</span>
              <span style={{ width: 28, height: 28, borderRadius: 14, background: "#202839", display: "grid", placeItems: "center" }}>+</span>
            </Pill>
            <Pill style={{ width: 44, padding: 0, justifyContent: "center" }}>⚙</Pill>
          </div>

          {page === "drumpad" ? (
            <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 10, borderRadius: 16, border: "1px dashed rgba(255,255,255,.08)", display: "grid", placeItems: "center", color: "#3F485B", fontSize: 13, fontWeight: 700 }}>DrumPad is not part of this preview</div>
          ) : (
            <>
              {/* left column: simulated */}
              <div style={{ position: "absolute", left: 10, top: 62, width: 168, bottom: 10, display: "flex", flexDirection: "column", gap: 6, opacity: 0.5, pointerEvents: "none" }}>
                <div style={{ flex: 1, display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 5 }}>
                  {["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"].map((n, i) => <div key={n} style={{ borderRadius: 10, background: `linear-gradient(180deg, hsl(${(i * 30 + 205) % 360} 60% 26%), #1B2336)`, display: "grid", placeItems: "center", fontSize: 12, fontWeight: 800, color: `hsl(${(i * 30 + 205) % 360} 84% 68%)` }}>{n}</div>)}
                </div>
                <div style={{ height: 28, borderRadius: 10, background: "#1B2336", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 800, color: "#8E99AD" }}>− Oct 4 +</div>
                <div style={{ height: 56, display: "flex", gap: 6 }}>
                  <div style={{ width: 56, borderRadius: 14, background: "#1B2336" }} />
                  <div style={{ flex: 1, borderRadius: 14, background: "#1B2336", padding: "8px 10px", display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
                    <span style={{ fontSize: 12, fontWeight: 800, color: "#C9D2E2" }}>Loops <span style={{ color: AMBER }}>{used}/8</span></span>
                    <div style={{ display: "flex", gap: 3 }}>{cases.current.map((c) => <span key={c.id} style={{ flex: 1, height: 5, borderRadius: 2, background: c.layers === 0 ? "rgba(255,255,255,.1)" : colorOf(c) }} />)}</div>
                  </div>
                </div>
                <div style={{ height: 28, display: "flex", gap: 6 }}>{["Pad None", "Kit Drum"].map((t) => <div key={t} style={{ flex: 1, borderRadius: 10, background: "#1B2336", display: "grid", placeItems: "center", fontSize: 11, fontWeight: 800, color: "#8E99AD" }}>{t}</div>)}</div>
              </div>

              {/* right area */}
              <div style={{ position: "absolute", left: 188, right: 10, top: 60, bottom: 8, display: "flex", flexDirection: "column", gap: 6 }}>
                <div style={{ height: 38, display: "flex", alignItems: "flex-end", gap: 10, flex: "none" }}>
                  <Group label="QUANTIZE"><Seg options={[["off", "Off"], ["16", "1/16"], ["bar", "Bar"]]} value={quant} onChange={setQuant} w={112} /></Group>
                  <Group label="LENGTH"><Seg options={[["auto", "Auto"], [1, "1"], [2, "2"], [4, "4"], [8, "8"]]} value={lenSel} onChange={setLenSel} w={152} /></Group>
                  <Group label="COUNT-IN"><Seg options={[[0, "Off"], [1, "1"], [2, "2"]]} value={countIn} onChange={setCountIn} w={96} /></Group>
                  <Group label={`BAR ${barN}`}>
                    <div style={{ height: 24, display: "flex", alignItems: "center", gap: 5, padding: "0 3px" }}>
                      {[0, 1, 2, 3].map((i) => <span key={i} style={{ width: 8, height: 8, borderRadius: 4, background: beatN === i ? (i === 0 ? AC : "#C7CFDD") : "rgba(255,255,255,.12)", boxShadow: beatN === i && i === 0 ? `0 0 8px ${AC}` : "none" }} />)}
                    </div>
                  </Group>
                  <div style={{ flex: 1 }} />
                  <button onClick={() => (anyStopped ? playAll() : stopAll())} disabled={!anyPlaying && !anyStopped} className="press" style={{ height: 28, padding: "0 11px", borderRadius: 10, border: "1px solid rgba(255,255,255,.1)", background: "rgba(255,255,255,.05)", color: "#C9D2E2", fontSize: 11.5, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", gap: 6, opacity: !anyPlaying && !anyStopped ? 0.4 : 1, whiteSpace: "nowrap", marginBottom: 2 }}>
                    <Ico n={anyStopped ? "play" : "stop"} size={11} /> {anyStopped ? "Play all" : "Stop all"}
                  </button>
                  <button onClick={() => setConfirm(true)} disabled={used === 0} className="press" style={{ height: 28, padding: "0 11px", borderRadius: 10, border: "1px solid rgba(194,88,122,.4)", background: "rgba(194,88,122,.1)", color: "#E3A0B4", fontSize: 11.5, fontWeight: 800, cursor: "pointer", opacity: used ? 1 : 0.4, whiteSpace: "nowrap", marginBottom: 2 }}>Clear all</button>
                </div>

                <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                  {focusCase ? (
                    <FocusView c={focusCase} now={now} color={colorOf(focusCase)} handlers={handlers} onBack={() => setFocus(null)} cases={cases.current} colorOf={colorOf} onSelect={setFocus} onRemoveLayer={removeLayer} onTouch={touch} />
                  ) : (
                    <div style={{ position: "absolute", inset: 0, display: "grid", gridTemplateColumns: "repeat(4,minmax(0,1fr))", gridTemplateRows: "repeat(2,minmax(0,1fr))", gap: 6 }}>
                      {cases.current.map((c, i) => (
                        <div key={c.id} style={{ minWidth: 0, minHeight: 0, animation: `fadeIn .3s ease-out ${i * 25}ms backwards` }}>
                          <CaseFull c={c} now={now} color={colorOf(c)} handlers={handlers} onFocus={setFocus} />
                        </div>
                      ))}
                    </div>
                  )}
                  {sheetCase && <ActionSheet c={sheetCase} color={colorOf(sheetCase)} onClose={() => setSheet(null)} onDouble={doDouble} onHalf={doHalf} onDuplicate={doDuplicate} onClear={doClear} onMidi={doMidi} onTouch={touch} />}
                </div>
              </div>
            </>
          )}

          {patchWin && (
            <PatchWindow patches={patches} activeId={activeId} dirty={dirty} cases={cases.current} colorOf={colorOf} onClose={() => setPatchWin(false)}
              onLoad={(id) => { setPatchWin(false); requestLoad(id); }} onSave={savePatch} onSaveNew={saveNew} onRevert={() => { setPatchWin(false); revert(); }}
              onRename={renamePatch} onDuplicate={dupPatch} onOverwrite={overwritePatch} onDelete={deletePatch} />
          )}

          {ask && askTarget && (
            <div style={{ position: "absolute", inset: 0, zIndex: 65, display: "grid", placeItems: "center", background: "rgba(6,9,14,.55)", animation: "fadeIn .2s" }}>
              <div style={{ width: 380, borderRadius: 18, padding: 18, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 20px 50px rgba(0,0,0,.55)", animation: "sheetIn .26s ease-out" }}>
                <div style={{ fontSize: 16, fontWeight: 900, color: "#F1F4FA" }}>Unsaved changes in “{activePatch ? activePatch.name : "loops"}”</div>
                <div style={{ fontSize: 12.5, color: "#8E99AE", marginTop: 6, lineHeight: 1.5 }}>Load “{askTarget.name}” and keep your changes first?</div>
                <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 16 }}>
                  <button onClick={() => setAsk(null)} className="press" style={{ height: 36, padding: "0 14px", borderRadius: 11, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.05)", color: "#C9D2E2", fontSize: 13, fontWeight: 800, cursor: "pointer" }}>Cancel</button>
                  <button onClick={() => { const p = askTarget; setAsk(null); applyPatch(p); }} className="press" style={{ height: 36, padding: "0 14px", borderRadius: 11, border: "1px solid rgba(194,88,122,.4)", background: "rgba(194,88,122,.1)", color: "#E3A0B4", fontSize: 13, fontWeight: 800, cursor: "pointer" }}>Discard</button>
                  <button onClick={() => { const p = askTarget; savePatch(); setAsk(null); applyPatch(p); }} className="press" style={{ height: 36, padding: "0 16px", borderRadius: 11, border: 0, background: "linear-gradient(180deg,#9DBDEB,#7FA3DD)", color: "#0F1726", fontSize: 13, fontWeight: 900, cursor: "pointer" }}>Save and load</button>
                </div>
              </div>
            </div>
          )}

          {confirm && (
            <div style={{ position: "absolute", inset: 0, zIndex: 60, display: "grid", placeItems: "center", background: "rgba(6,9,14,.55)", animation: "fadeIn .2s" }}>
              <div style={{ width: 360, borderRadius: 18, padding: 18, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 20px 50px rgba(0,0,0,.55)", animation: "sheetIn .26s ease-out" }}>
                <div style={{ fontSize: 16, fontWeight: 900, color: "#F1F4FA" }}>Clear all 8 cases?</div>
                <div style={{ fontSize: 12.5, color: "#8E99AE", marginTop: 6, lineHeight: 1.5 }}>Every layer of every case is erased. The saved patch is not touched.</div>
                <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 16 }}>
                  <button onClick={() => setConfirm(false)} className="press" style={{ height: 36, padding: "0 16px", borderRadius: 11, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.05)", color: "#C9D2E2", fontSize: 13, fontWeight: 800, cursor: "pointer" }}>Cancel</button>
                  <button onClick={clearAll} className="press" style={{ height: 36, padding: "0 16px", borderRadius: 11, border: 0, background: "linear-gradient(180deg,#B8465F,#8E2F45)", color: "#FFE7EE", fontSize: 13, fontWeight: 800, cursor: "pointer" }}>Clear all</button>
                </div>
              </div>
            </div>
          )}

          {toast && (
            <div key={toast.k} style={{ position: "absolute", left: "50%", bottom: 14, transform: "translateX(-50%)", display: "flex", alignItems: "center", gap: 14, padding: "8px 14px", borderRadius: 12, background: "#222C40", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 10px 26px rgba(0,0,0,.5)", fontSize: 13, fontWeight: 700, animation: "toast .22s ease-out", zIndex: 70, whiteSpace: "nowrap" }}>
              <span>{toast.t}</span>
              {toast.undoFn && <button onClick={() => { toast.undoFn(); setToast(null); touch(); }} style={{ border: 0, background: "transparent", color: AC, fontWeight: 900, fontSize: 12, letterSpacing: 1, cursor: "pointer" }}>UNDO</button>}
            </div>
          )}
        </div>
      </div>

      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", alignItems: "center", maxWidth: 780 }}>
        <button onClick={() => setSim((s) => !s)} style={{ padding: "7px 14px", borderRadius: 999, border: `1px solid ${sim ? AC : "#2A3446"}`, background: sim ? "rgba(143,178,232,.16)" : "#121823", color: sim ? "#B9CDF2" : "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Simulate playing: {sim ? "on" : "off"}</button>
        <button onClick={() => { cases.current.forEach(reset); setFocus(null); }} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Reset cases</button>
        <button onClick={() => setPatchWin(true)} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Open loop patches</button>
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 720, lineHeight: 1.55 }}>
        The patch chip in the top bar loads the previous or next loop patch (tap its name for the management window; the amber dot means unsaved changes) · on a case: hold Play to mute, hold Undo to redo · tap a case number to focus it (layer mute, remove, volume) · ⋯ opens the case settings
        {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
      </div>
    </div>
  );
}
