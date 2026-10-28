import { useState, useRef, useEffect, useMemo, memo } from "react";

/* ─────────────────────────────────────────────────────────────
   TRACKS (DAW) — landscape smartphone preview (891 × 411 dp)
   Flat, Bandlab-like: solid colors, no gloss. Pages: Mixer (simulated), Tracks, DrumPad (simulated).
   The Step Drum editor opens full screen from the Tracks page. No audio is produced.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411, HEAD = 170, ROW = 64, RULER = 24, BARS = 16;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const AC = "#8FB2E8", RED = "#E5484D", AMBER = "#D8A24A";
const BG = "#12161D", PANEL = "#171C26", PANEL2 = "#1D2430", LINE = "rgba(255,255,255,.06)";
const CL = ["#F2994A", "#34C38F", "#4C8DF6", "#9B6BF2", "#F2557A", "#2CC5C0"];
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const rgba = (h, a) => { const r = parseInt(h.slice(1, 3), 16), g = parseInt(h.slice(3, 5), 16), b = parseInt(h.slice(5, 7), 16); return `rgba(${r},${g},${b},${a})`; };
const rng = (seed) => { let s = seed >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); };
const LANES = [["Kick", "#F2994A"], ["Snare", "#F2557A"], ["Clap", "#9B6BF2"], ["Hi-hat", "#34C38F"], ["Open hat", "#2CC5C0"], ["Tom", "#4C8DF6"], ["Perc", "#E5B94A"], ["Crash", "#8D97B8"]];
const ZOOMS = [32, 48, 64, 96, 128];
const SNAPS = [["bar", "Bar", 1], ["4", "1/4", 0.25], ["8", "1/8", 0.125], ["16", "1/16", 0.0625], ["off", "Off", 0]];
const MENU_PAGES = [["mixer", "Mixer"], ["tracks", "Tracks"], ["drumpad", "DrumPad"]];

const mkPattern = (rows, accents = []) => {
  const lanes = LANES.map(() => Array(32).fill(0));
  rows.forEach(([l, steps]) => steps.forEach((s) => { lanes[l][s] = 1; lanes[l][s + 16] = 1; }));
  accents.forEach(([l, s]) => { lanes[l][s] = 2; });
  return { steps: 16, lanes };
};
const PAT0 = () => [
  mkPattern([[0, [0, 4, 8, 10]], [1, [4, 12]], [2, [12]], [3, [0, 2, 4, 6, 8, 10, 12, 14]], [4, [14]], [5, [11]], [6, [3, 7, 15]], [7, [0]]], [[0, 0], [7, 0]]),
  mkPattern([[0, [0, 6, 8]], [1, [4, 12]], [3, [0, 4, 8, 12]], [6, [2, 10]]]),
  mkPattern([[0, [0, 3, 8, 11]], [1, [4, 12, 15]], [3, [0, 2, 4, 6, 8, 10, 12, 14]]]),
  mkPattern([]),
];
const TRACKS0 = [
  { id: 1, name: "Drums", kind: "drums", color: CL[0], sound: "Drum Kit", vol: 0.8, mute: false, solo: false, arm: false },
  { id: 2, name: "Bass", kind: "midi", color: CL[1], sound: "Sub Bass", vol: 0.7, mute: false, solo: false, arm: false },
  { id: 3, name: "Piano", kind: "midi", color: CL[2], sound: "Grand Piano", vol: 0.75, mute: false, solo: false, arm: false },
  { id: 4, name: "Strings", kind: "midi", color: CL[3], sound: "Full Strings", vol: 0.6, mute: false, solo: false, arm: false },
  { id: 5, name: "Vocals", kind: "audio", color: CL[4], sound: "Audio", vol: 0.85, mute: false, solo: false, arm: false },
  { id: 6, name: "Guitar", kind: "audio", color: CL[5], sound: "Audio", vol: 0.65, mute: false, solo: false, arm: false },
];
const CLIPS0 = [
  [1, 0, 4, 0], [1, 4, 4, 1], [1, 8, 4, 0], [1, 12, 4, 2],
  [2, 0, 8], [2, 8, 8], [3, 2, 2], [3, 4, 4], [3, 8, 4], [4, 4, 8], [4, 12, 4], [5, 4, 4], [5, 8, 4], [6, 0, 6],
].map(([track, start, len, pat], i) => ({ id: i + 1, track, start, len, pat: pat ?? 0, seed: 11 + i * 7 }));

const CSS = `
.dw *{box-sizing:border-box}
.dw button{font-family:inherit}
@keyframes pageIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}
@keyframes menuIn{from{opacity:0;transform:scaleY(.85)}to{opacity:1;transform:scaleY(1)}}
@keyframes slideUp{from{opacity:0;transform:translateY(40px)}to{opacity:1;transform:translateY(0)}}
@keyframes fadeIn{from{opacity:0}to{opacity:1}}
@keyframes blink{0%,100%{opacity:1}50%{opacity:.35}}
@keyframes pop{0%{transform:scale(.7)}60%{transform:scale(1.08)}100%{transform:scale(1)}}
@keyframes toast{from{opacity:0;transform:translate(-50%,8px)}to{opacity:1;transform:translate(-50%,0)}}
.press{transition:transform .12s cubic-bezier(.3,1.5,.5,1),background .2s,border-color .2s,opacity .2s,color .2s}
.press:active{transform:scale(.94)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

const P = {
  play: <path d="M8 5.5l10 6.5-10 6.5z" fill="currentColor" stroke="none" />,
  pause: <path d="M8 6h3v12H8zM13 6h3v12h-3z" fill="currentColor" stroke="none" />,
  stop: <rect x="7" y="7" width="10" height="10" rx="1.6" fill="currentColor" stroke="none" />,
  rec: <circle cx="12" cy="12" r="6" fill="currentColor" stroke="none" />,
  rew: <><path d="M6 5v14" /><path d="M19 6l-9 6 9 6z" fill="currentColor" stroke="none" /></>,
  loop: <><path d="M17 2l4 4-4 4" /><path d="M3 11V9a4 4 0 0 1 4-4h14" /><path d="M7 22l-4-4 4-4" /><path d="M21 13v2a4 4 0 0 1-4 4H3" /></>,
  metro: <><path d="M9 4h6l3 17H6z" /><path d="M12 17l4-9" /></>,
  undo: <><path d="M9 14L4.5 9.5 9 5" /><path d="M4.5 9.5H14a5.5 5.5 0 0 1 0 11h-3" /></>,
  redo: <><path d="M15 14l4.5-4.5L15 5" /><path d="M19.5 9.5H10a5.5 5.5 0 0 0 0 11h3" /></>,
  plus: <path d="M12 5v14M5 12h14" />, minus: <path d="M5 12h14" />,
  x: <path d="M6 6l12 12M18 6L6 18" />, check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
  down: <path d="M6 9l6 6 6-6" />, back: <path d="M19 12H5M11 6l-6 6 6 6" />,
  cursor: <path d="M5 3l14 7-6 2-2 6z" fill="currentColor" />,
  scissors: <><circle cx="6" cy="6" r="3" /><circle cx="6" cy="18" r="3" /><path d="M8.6 8L20 20M8.6 16L20 4" /></>,
  eraser: <path d="M7 17l-3-3 9-9 6 6-6 6zM13 19h7" />,
  trash: <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" />,
  copy: <><rect x="8" y="8" width="12" height="12" rx="2" /><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" /></>,
  edit: <path d="M4 20l1-4L16 5l3 3L8 19z" />,
  mixer: <><path d="M5 4v16M12 4v16M19 4v16" /><circle cx="5" cy="14" r="2" fill="currentColor" /><circle cx="12" cy="8" r="2" fill="currentColor" /><circle cx="19" cy="12" r="2" fill="currentColor" /></>,
  tracks: <><rect x="3" y="4" width="18" height="4" rx="1.5" /><rect x="3" y="10" width="12" height="4" rx="1.5" /><rect x="3" y="16" width="16" height="4" rx="1.5" /></>,
  pads: <><rect x="4" y="4" width="7" height="7" rx="1.5" /><rect x="13" y="4" width="7" height="7" rx="1.5" /><rect x="4" y="13" width="7" height="7" rx="1.5" /><rect x="13" y="13" width="7" height="7" rx="1.5" /></>,
  wave: <path d="M4 10.5v3M8 7v10M12 3.5v17M16 7v10M20 10.5v3" />,
  keys: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M8 5v14M12 5v14M16 5v14" /></>,
  rand: <><path d="M4 7h4l8 10h4M4 17h4l2-2.5M14 9.5L16 7h4" /><path d="M18 5l2 2-2 2M18 15l2 2-2 2" /></>,
};
function Ico({ n, size = 16, color = "currentColor", sw = 2 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ color }}>{P[n]}</svg>;
}

function Seg({ options, value, onChange, w, h = 24, fs = 11.5 }) {
  const idx = Math.max(0, options.findIndex((o) => o[0] === value));
  return (
    <div style={{ position: "relative", display: "flex", padding: 2, borderRadius: 9, background: "#0E1218", width: w, flex: "none" }}>
      <div style={{ position: "absolute", top: 2, bottom: 2, left: 2, width: `calc((100% - 4px) / ${options.length})`, borderRadius: 7, background: "#2B3447", transform: `translateX(${idx * 100}%)`, transition: "transform .25s cubic-bezier(.3,1.3,.5,1)" }} />
      {options.map(([k, l]) => (
        <button key={String(k)} onClick={() => onChange(k)} style={{ position: "relative", flex: 1, height: h, border: 0, background: "transparent", cursor: "pointer", fontSize: fs, fontWeight: 700, color: value === k ? "#F1F4FA" : "#7B859A", padding: 0, display: "grid", placeItems: "center", transition: "color .2s" }}>
          {typeof l === "string" ? l : l}
        </button>
      ))}
    </div>
  );
}

function Btn({ children, onClick, active, color, w, h = 30, style, title, disabled }) {
  return (
    <button onClick={onClick} disabled={disabled} title={title} className="press" style={{ width: w, minWidth: w, height: h, padding: w ? 0 : "0 12px", borderRadius: 10, border: `1px solid ${active ? rgba(color || AC, 0.55) : LINE}`, background: active ? rgba(color || AC, 0.2) : PANEL2, color: disabled ? "#434C61" : active ? (color || AC) : "#C9D2E2", cursor: disabled ? "default" : "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6, fontSize: 12, fontWeight: 700, whiteSpace: "nowrap", flex: "none", ...style }}>{children}</button>
  );
}

/* ═══════════════ clips ═══════════════ */

const ClipView = memo(function ClipView({ clip, track, ppb, selected, dim, pat, onDown, onDouble }) {
  const w = Math.max(6, clip.len * ppb - 2), h = ROW - 8;
  const body = useMemo(() => {
    const r = rng(clip.seed);
    if (track.kind === "audio") {
      const n = Math.max(2, Math.floor(w / 3.5));
      let d = "";
      for (let i = 0; i < n; i++) {
        const env = 0.35 + 0.65 * Math.abs(Math.sin(i / n * Math.PI * (2 + clip.len / 4) + clip.seed));
        const a = (0.2 + 0.8 * r()) * env * (h - 22) * 0.5;
        const x = 2 + i * 3.5, cy = 14 + (h - 14) / 2;
        d += `M${x},${cy - a}V${cy + a}`;
      }
      return <svg width={w} height={h} style={{ position: "absolute", left: 0, top: 0 }}><path d={d} stroke="rgba(10,14,22,.5)" strokeWidth="2" strokeLinecap="round" /></svg>;
    }
    if (track.kind === "midi") {
      const bass = track.name === "Bass";
      const n = Math.max(2, Math.floor(clip.len * (bass ? 4 : 6)));
      const rects = [];
      let t = 0;
      for (let i = 0; i < n && t < clip.len; i++) {
        const dur = (bass ? 0.5 : 0.25) * (1 + Math.floor(r() * 2));
        const pitch = (bass ? 3 : 8) + Math.floor(r() * (bass ? 6 : 14));
        rects.push(<rect key={i} x={t * ppb + 2} y={16 + (h - 24) * (1 - pitch / (bass ? 10 : 24))} width={Math.max(2, dur * ppb - 2)} height="3" rx="1.5" fill="rgba(255,255,255,.85)" />);
        t += dur * (r() < 0.3 ? 0.5 : 1);
      }
      return <svg width={w} height={h} style={{ position: "absolute", left: 0, top: 0 }}>{rects}</svg>;
    }
    const cells = [];
    const sw = ppb / 16;
    for (let b = 0; b < clip.len; b++) for (let s = 0; s < 16; s++) {
      const si = (b * 16 + s) % pat.steps;
      pat.lanes.forEach((lane, l) => { if (lane[si]) cells.push(<rect key={`${b}-${s}-${l}`} x={b * ppb + s * sw + 1} y={16 + l * 4.6} width={Math.max(1.5, sw - 1)} height="3.4" rx="1" fill={lane[si] === 2 ? "#fff" : "rgba(255,255,255,.7)"} />); });
    }
    return <svg width={w} height={h} style={{ position: "absolute", left: 0, top: 0 }}>{cells}</svg>;
  }, [clip.len, clip.seed, track.kind, track.name, ppb, pat, w, h]);
  return (
    <div onPointerDown={(e) => onDown(e, clip)} onDoubleClick={() => onDouble(clip)}
      style={{ position: "absolute", left: clip.start * ppb + 1, top: 4, width: w, height: h, borderRadius: 8, background: track.color, overflow: "hidden", cursor: "grab", touchAction: "none", opacity: dim ? 0.4 : 1, outline: selected ? "2px solid #fff" : "none", outlineOffset: -2, transition: "opacity .2s", userSelect: "none" }}>
      <div style={{ position: "absolute", left: 6, top: 2, fontSize: 9.5, fontWeight: 800, color: "rgba(10,14,22,.75)", whiteSpace: "nowrap" }}>{track.kind === "drums" ? `Pattern ${"ABCD"[clip.pat]}` : track.name}</div>
      {body}
    </div>
  );
});

/* ═══════════════ top bar and navigation ═══════════════ */

function NavMenu({ page, onSelect }) {
  const [open, setOpen] = useState(false);
  const cur = MENU_PAGES.find((p) => p[0] === page);
  const icon = { mixer: "mixer", tracks: "tracks", drumpad: "pads" };
  return (
    <div style={{ position: "relative", flex: "none" }}>
      <button onClick={() => setOpen((o) => !o)} className="press" style={{ height: 44, minWidth: 150, borderRadius: 12, padding: "0 12px", border: `1px solid ${open ? rgba(AC, 0.5) : LINE}`, background: PANEL2, color: "#E9EDF5", display: "flex", alignItems: "center", gap: 9, fontSize: 14, fontWeight: 800, cursor: "pointer" }}>
        <span style={{ color: AC, display: "inline-flex" }}><Ico n={icon[page]} size={17} /></span>
        <span style={{ flex: 1, textAlign: "left" }}>{cur[1]}</span>
        <span style={{ color: AC, display: "inline-flex", transform: open ? "rotate(180deg)" : "none", transition: "transform .2s" }}><Ico n="down" size={15} sw={2.4} /></span>
      </button>
      {open && (
        <>
          <div onClick={() => setOpen(false)} style={{ position: "fixed", inset: 0, zIndex: 80 }} />
          <div style={{ position: "absolute", left: 0, top: 50, width: 190, borderRadius: 12, padding: 5, background: "#222A38", border: `1px solid ${LINE}`, boxShadow: "0 14px 36px rgba(0,0,0,.5)", transformOrigin: "top", animation: "menuIn .16s ease-out", zIndex: 81 }}>
            {MENU_PAGES.map(([k, l]) => (
              <button key={k} onClick={() => { setOpen(false); onSelect(k); }} className="press" style={{ width: "100%", height: 40, border: 0, borderRadius: 9, display: "flex", alignItems: "center", gap: 10, padding: "0 10px", cursor: "pointer", background: page === k ? rgba(AC, 0.14) : "transparent", color: page === k ? "#F1F4FA" : "#B5BFD3", fontSize: 14, fontWeight: 800 }}>
                <span style={{ color: page === k ? AC : "#7B859A", display: "inline-flex" }}><Ico n={icon[k]} size={17} /></span>
                <span style={{ flex: 1, textAlign: "left" }}>{l}</span>
                {page === k && <Ico n="check" size={15} color={AC} sw={2.6} />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

/* ═══════════════ Step Drum (full screen) ═══════════════ */

function StepDrum({ patterns, setPatterns, patIdx, setPatIdx, tracks, pos, playing, onPlay, onClose, bpm }) {
  const pat = patterns[patIdx];
  const [tool, setTool] = useState("draw");
  const [swing, setSwing] = useState(54);
  const [lm, setLm] = useState(LANES.map(() => ({ mute: false, solo: false })));
  const paint = useRef(null);
  const anySolo = lm.some((x) => x.solo);
  const steps = pat.steps;
  const cur = playing ? Math.floor(((pos * 16) % steps + steps) % steps) : -1;
  const setCell = (l, s, v) => setPatterns((ps) => ps.map((p, i) => (i !== patIdx ? p : { ...p, lanes: p.lanes.map((lane, li) => (li !== l ? lane : lane.map((c, si) => (si === s ? v : c)))) })));
  const valueFor = (cell) => (tool === "erase" ? 0 : tool === "accent" ? (cell === 2 ? 1 : 2) : (cell ? 0 : 1));
  const cellAt = (e) => { const el = document.elementFromPoint(e.clientX, e.clientY); if (!el || el.dataset.lane === undefined) return null; return [+el.dataset.lane, +el.dataset.step]; };
  const down = (e) => { const c = cellAt(e); if (!c) return; e.currentTarget.setPointerCapture(e.pointerId); const v = valueFor(pat.lanes[c[0]][c[1]]); paint.current = v; setCell(c[0], c[1], v); };
  const move = (e) => { if (paint.current === null) return; const c = cellAt(e); if (c && pat.lanes[c[0]][c[1]] !== paint.current) setCell(c[0], c[1], paint.current); };
  const setLen = (n) => setPatterns((ps) => ps.map((p, i) => (i !== patIdx ? p : { steps: n, lanes: p.lanes.map((lane) => (n === 32 && p.steps === 16 ? lane.map((c, si) => (si >= 16 ? lane[si - 16] : c)) : lane)) })));
  const clear = () => setPatterns((ps) => ps.map((p, i) => (i !== patIdx ? p : { ...p, lanes: p.lanes.map((l) => l.map(() => 0)) })));
  const random = () => setPatterns((ps) => ps.map((p, i) => {
    if (i !== patIdx) return p;
    const dens = [0.22, 0.12, 0.08, 0.5, 0.1, 0.08, 0.15, 0.03];
    return { ...p, lanes: p.lanes.map((lane, l) => lane.map((_, s) => (Math.random() < dens[l] * (s % 4 === 0 ? 1.8 : 1) ? (Math.random() < 0.15 ? 2 : 1) : 0))) };
  }));
  const gap = steps === 32 ? 2 : 3;
  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 60, background: BG, animation: "slideUp .3s cubic-bezier(.2,.9,.25,1)" }}>
      <div style={{ position: "absolute", left: 10, right: 10, top: 8, height: 44, display: "flex", alignItems: "center", gap: 8 }}>
        <Btn onClick={onClose} h={44} style={{ borderRadius: 12, fontSize: 14, fontWeight: 800 }}><Ico n="back" size={16} /> Tracks</Btn>
        <Seg options={[0, 1, 2, 3].map((i) => [i, "ABCD"[i]])} value={patIdx} onChange={setPatIdx} w={124} h={32} fs={13} />
        <Seg options={[[16, "16"], [32, "32"]]} value={steps} onChange={setLen} w={84} h={32} fs={12.5} />
        <Seg options={[["draw", "Draw"], ["accent", "Accent"], ["erase", "Erase"]]} value={tool} onChange={setTool} w={174} h={32} fs={12} />
        <div style={{ display: "flex", alignItems: "center", gap: 8, height: 44, padding: "0 12px", borderRadius: 12, background: PANEL2, border: `1px solid ${LINE}`, flex: "none" }}>
          <span style={{ fontSize: 10, fontWeight: 800, color: "#7B859A", letterSpacing: 1 }}>SWING</span>
          <input type="range" min="50" max="75" value={swing} onChange={(e) => setSwing(+e.target.value)} style={{ width: 70, accentColor: AC }} />
          <span style={{ fontFamily: MONO, fontSize: 12, fontWeight: 800, color: "#E9EDF5", width: 28 }}>{swing}%</span>
        </div>
        <Btn onClick={random} h={44} style={{ borderRadius: 12 }}><Ico n="rand" size={15} /> Random</Btn>
        <Btn onClick={clear} h={44} style={{ borderRadius: 12 }}>Clear</Btn>
        <div style={{ flex: 1 }} />
        <Btn onClick={onPlay} w={44} h={44} active={playing} color={AC} style={{ borderRadius: 12 }}><Ico n={playing ? "pause" : "play"} size={18} /></Btn>
      </div>

      <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 8, display: "flex", flexDirection: "column", gap: 3 }}>
        <div style={{ display: "flex", gap: 8, height: 14, alignItems: "flex-end" }}>
          <div style={{ width: 128, flex: "none", fontSize: 9.5, fontWeight: 800, letterSpacing: 1.2, color: "#566075" }}>{tracks.find((t) => t.kind === "drums")?.name.toUpperCase()} · PATTERN {"ABCD"[patIdx]}</div>
          <div style={{ flex: 1, display: "grid", gridTemplateColumns: `repeat(${steps}, 1fr)`, gap }}>
            {Array.from({ length: steps }).map((_, s) => <div key={s} style={{ textAlign: "center", fontFamily: MONO, fontSize: 9, fontWeight: 800, color: cur === s ? AC : s % 4 === 0 ? "#8E99AE" : "#434C61" }}>{s % 4 === 0 ? s / 4 + 1 : "·"}</div>)}
          </div>
        </div>
        <div style={{ flex: 1, display: "flex", gap: 8, minHeight: 0 }}>
          <div style={{ width: 128, flex: "none", display: "flex", flexDirection: "column", gap: 3 }}>
            {LANES.map(([name, color], l) => (
              <div key={name} style={{ flex: 1, minHeight: 0, borderRadius: 8, background: PANEL, display: "flex", alignItems: "center", gap: 6, padding: "0 6px 0 0", overflow: "hidden", opacity: lm[l].mute || (anySolo && !lm[l].solo) ? 0.5 : 1 }}>
                <span style={{ width: 4, alignSelf: "stretch", background: color }} />
                <span style={{ flex: 1, fontSize: 12, fontWeight: 800, color: "#E3E8F2", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{name}</span>
                {[["M", "mute", AMBER], ["S", "solo", AC]].map(([t, k, c]) => (
                  <button key={k} onClick={() => setLm((a) => a.map((x, i) => (i === l ? { ...x, [k]: !x[k] } : x)))} style={{ width: 20, height: 20, borderRadius: 6, border: 0, cursor: "pointer", fontSize: 10, fontWeight: 800, background: lm[l][k] ? c : "#262E3E", color: lm[l][k] ? "#10151F" : "#8E99AE", padding: 0 }}>{t}</button>
                ))}
              </div>
            ))}
          </div>
          <div onPointerDown={down} onPointerMove={move} onPointerUp={() => { paint.current = null; }} onPointerCancel={() => { paint.current = null; }}
            style={{ flex: 1, display: "flex", flexDirection: "column", gap: 3, touchAction: "none", minWidth: 0 }}>
            {LANES.map(([name, color], l) => (
              <div key={name} style={{ flex: 1, minHeight: 0, display: "grid", gridTemplateColumns: `repeat(${steps}, 1fr)`, gap, opacity: lm[l].mute || (anySolo && !lm[l].solo) ? 0.45 : 1 }}>
                {pat.lanes[l].slice(0, steps).map((v, s) => (
                  <div key={s} data-lane={l} data-step={s}
                    style={{ borderRadius: 6, background: v ? color : (Math.floor(s / 4) % 2 ? "#212837" : "#1A202C"), position: "relative", filter: cur === s ? "brightness(1.35)" : "none", transition: "background .12s", animation: v ? "pop .18s ease-out" : "none" }}>
                    {v === 2 && <div style={{ position: "absolute", left: "50%", top: "50%", width: 8, height: 8, marginLeft: -4, marginTop: -4, borderRadius: 4, background: "#fff", pointerEvents: "none" }} />}
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ simulated pages ═══════════════ */

function MixerPage({ tracks, setTracks }) {
  return (
    <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 8, display: "flex", gap: 6, animation: "pageIn .28s ease-out" }}>
      {tracks.map((t) => (
        <div key={t.id} style={{ width: 118, flex: "none", borderRadius: 12, background: PANEL, display: "flex", flexDirection: "column", alignItems: "center", gap: 8, padding: "8px 8px 10px", opacity: t.mute ? 0.5 : 1 }}>
          <div style={{ width: "100%", height: 22, borderRadius: 7, background: PANEL2, display: "grid", placeItems: "center", fontSize: 11, fontWeight: 800, color: "#E3E8F2" }}>{t.name}</div>
          <div style={{ display: "flex", gap: 5 }}>
            {[["M", "mute", AMBER], ["S", "solo", AC]].map(([l, k, c]) => (
              <button key={k} onClick={() => setTracks((ts) => ts.map((x) => (x.id === t.id ? { ...x, [k]: !x[k] } : x)))} style={{ width: 36, height: 22, borderRadius: 7, border: 0, cursor: "pointer", fontSize: 11, fontWeight: 800, background: t[k] ? c : "#262E3E", color: t[k] ? "#10151F" : "#8E99AE" }}>{l}</button>
            ))}
          </div>
          <div onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); const r = e.currentTarget.getBoundingClientRect(); const set = (ev) => setTracks((ts) => ts.map((x) => (x.id === t.id ? { ...x, vol: clamp(1 - (ev.clientY - r.top) / r.height, 0, 1) } : x))); set(e); e.currentTarget.onpointermove = set; e.currentTarget.onpointerup = () => { e.currentTarget.onpointermove = null; }; }}
            style={{ flex: 1, width: 30, borderRadius: 8, background: "#0E1218", position: "relative", touchAction: "none", cursor: "ns-resize" }}>
            <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: `${t.vol * 100}%`, borderRadius: 8, background: t.color }} />
          </div>
          <span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 800, color: "#8E99AE" }}>{Math.round(t.vol * 100)}</span>
        </div>
      ))}
    </div>
  );
}

function PadsPage() {
  const [hit, setHit] = useState(null);
  return (
    <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 8, display: "grid", gridTemplateColumns: "repeat(4,1fr)", gridTemplateRows: "repeat(2,1fr)", gap: 8, animation: "pageIn .28s ease-out" }}>
      {LANES.map(([n, c], i) => (
        <div key={n} onPointerDown={() => { setHit(i); setTimeout(() => setHit(null), 160); }} style={{ borderRadius: 14, background: hit === i ? c : rgba(c, 0.2), border: `1px solid ${rgba(c, 0.4)}`, display: "grid", placeItems: "center", fontSize: 14, fontWeight: 800, color: hit === i ? "#10151F" : "#E3E8F2", transition: "background .12s", cursor: "pointer", userSelect: "none" }}>{n}</div>
      ))}
    </div>
  );
}

/* ═══════════════ MAIN ═══════════════ */

export default function TracksPreview() {
  const [page, setPage] = useState("tracks");
  const [tracks, setTracks] = useState(TRACKS0);
  const [clips, setClips] = useState(CLIPS0);
  const [patterns, setPatterns] = useState(PAT0);
  const [patIdx, setPatIdx] = useState(0);
  const [stepOpen, setStepOpen] = useState(false);
  const [pos, setPos] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [rec, setRec] = useState(false);
  const [loopOn, setLoopOn] = useState(false);
  const [metro, setMetro] = useState(false);
  const [bpm, setBpm] = useState(119);
  const [snap, setSnap] = useState("4");
  const [zi, setZi] = useState(2);
  const [tool, setTool] = useState("select");
  const [sel, setSel] = useState(null);
  const [selTrack, setSelTrack] = useState(2);
  const [addMenu, setAddMenu] = useState(false);
  const [toast, setToast] = useState(null);
  const [undoStack, setUndoStack] = useState([]);
  const [redoStack, setRedoStack] = useState([]);
  const scrollRef = useRef(null), drag = useRef(null), recStart = useRef(0), live = useRef({});
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 120) / H, 1.5));
  const ppb = ZOOMS[zi], snapV = SNAPS.find((s) => s[0] === snap)[2];
  const LOOP = [4, 8];
  live.current = { playing, rec, loopOn, bpm, ppb, tracks };
  const say = (t) => { setToast({ t, k: Date.now() }); setTimeout(() => setToast((x) => (x && Date.now() - x.k >= 1500 ? null : x)), 1600); };
  const snapTo = (v) => (snapV ? Math.round(v / snapV) * snapV : v);

  useEffect(() => { const on = () => setVp({ w: window.innerWidth, h: window.innerHeight }); window.addEventListener("resize", on); return () => window.removeEventListener("resize", on); }, []);

  /* transport */
  useEffect(() => {
    let raf, last = performance.now(), acc = 0;
    const loop = (t) => {
      raf = requestAnimationFrame(loop);
      const dt = (t - last) / 1000; last = t; acc += dt;
      if (!live.current.playing || acc < 0.033) return;
      const step = acc * (live.current.bpm / 240); acc = 0;
      setPos((p) => {
        let n = p + step;
        if (live.current.loopOn && p < LOOP[1] && n >= LOOP[1]) n = LOOP[0] + (n - LOOP[1]);
        if (n >= BARS) { n = BARS; setTimeout(() => stopAll(), 0); }
        return n;
      });
      const el = scrollRef.current;
      if (el) {
        const x = HEAD + posRef.current * live.current.ppb;
        if (x > el.scrollLeft + el.clientWidth - 40) el.scrollLeft = x - HEAD - 20;
        else if (x < el.scrollLeft + HEAD) el.scrollLeft = Math.max(0, x - HEAD - 20);
      }
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);
  const posRef = useRef(0); posRef.current = pos;

  const finishRec = () => {
    const len = posRef.current - recStart.current;
    const arm = live.current.tracks.find((t) => t.arm);
    if (len > 0.1 && arm) {
      const l = Math.max(0.25, snapV ? Math.round(len / snapV) * snapV : len);
      pushUndo();
      setClips((cs) => [...cs, { id: Date.now(), track: arm.id, start: snapTo(recStart.current), len: l, pat: 0, seed: Math.floor(Math.random() * 999) }]);
      say(`Recorded on ${arm.name}`);
    }
  };
  const stopAll = () => { if (live.current.rec && live.current.playing) finishRec(); setPlaying(false); setRec(false); };
  const togglePlay = () => { if (playing) { if (rec) finishRec(); setPlaying(false); setRec(false); } else { recStart.current = posRef.current; setPlaying(true); } };
  const toggleRec = () => {
    if (!tracks.some((t) => t.arm)) { say("Arm a track first (R)"); return; }
    if (rec) { if (playing) finishRec(); setRec(false); } else { recStart.current = posRef.current; setRec(true); if (!playing) setPlaying(true); }
  };
  const pushUndo = () => { setUndoStack((u) => [...u.slice(-20), clips]); setRedoStack([]); };
  const undo = () => { if (!undoStack.length) return; setRedoStack((r) => [...r, clips]); setClips(undoStack[undoStack.length - 1]); setUndoStack((u) => u.slice(0, -1)); };
  const redo = () => { if (!redoStack.length) return; setUndoStack((u) => [...u, clips]); setClips(redoStack[redoStack.length - 1]); setRedoStack((r) => r.slice(0, -1)); };
  const upd = (id, patch) => setTracks((ts) => ts.map((t) => (t.id === id ? { ...t, ...patch } : t)));

  /* clips */
  const clipDown = (e, clip) => {
    e.stopPropagation();
    setSel(clip.id); setSelTrack(clip.track);
    if (tool === "erase") { pushUndo(); setClips((cs) => cs.filter((c) => c.id !== clip.id)); setSel(null); return; }
    if (tool === "split") {
      const r = e.currentTarget.getBoundingClientRect();
      const at = snapTo(((e.clientX - r.left) / scale) / ppb);
      if (at > 0.01 && at < clip.len - 0.01) splitClip(clip, at);
      return;
    }
    e.currentTarget.setPointerCapture(e.pointerId);
    drag.current = { id: clip.id, x0: e.clientX, s0: clip.start, moved: false, pushed: false };
  };
  const clipMove = (e) => {
    const d = drag.current; if (!d) return;
    const dx = (e.clientX - d.x0) / scale / ppb;
    if (!d.pushed && Math.abs(dx) > 0.02) { pushUndo(); d.pushed = true; }
    setClips((cs) => cs.map((c) => (c.id === d.id ? { ...c, start: clamp(snapTo(d.s0 + dx), 0, BARS - c.len) } : c)));
  };
  const splitClip = (clip, at) => {
    pushUndo();
    setClips((cs) => cs.flatMap((c) => (c.id !== clip.id ? [c] : [{ ...c, len: at }, { ...c, id: Date.now(), start: c.start + at, len: c.len - at, seed: c.seed + 5 }])));
  };
  const selClip = clips.find((c) => c.id === sel);
  const doDup = () => { if (!selClip) return; if (selClip.start + selClip.len * 2 > BARS) { say("No room after the clip"); return; } pushUndo(); const n = { ...selClip, id: Date.now(), start: selClip.start + selClip.len }; setClips((cs) => [...cs, n]); setSel(n.id); };
  const doSplit = () => { if (!selClip) return; const at = pos - selClip.start; if (at <= 0.01 || at >= selClip.len - 0.01) { say("Put the playhead inside the clip"); return; } splitClip(selClip, snapTo(at) || at); };
  const doDel = () => { if (!selClip) return; pushUndo(); setClips((cs) => cs.filter((c) => c.id !== selClip.id)); setSel(null); };
  const doEdit = (c = selClip) => {
    if (!c) return;
    const t = tracks.find((x) => x.id === c.track);
    if (t.kind === "drums") { setPatIdx(c.pat); setStepOpen(true); } else say(t.kind === "midi" ? "Piano roll is not part of this preview" : "Audio editor is not part of this preview");
  };
  const addTrack = (kind) => {
    const n = tracks.length + 1, color = CL[(n - 1) % CL.length];
    const names = { audio: "Audio", midi: "Instrument", drums: "Drums" };
    setTracks((ts) => [...ts, { id: Date.now(), name: `${names[kind]} ${n}`, kind, color, sound: kind === "audio" ? "Audio" : kind === "drums" ? "Drum Kit" : "Grand Piano", vol: 0.75, mute: false, solo: false, arm: false }]);
    setAddMenu(false); say("Track added");
    setTimeout(() => { if (scrollRef.current) scrollRef.current.scrollTop = 9999; }, 50);
  };
  const rulerDown = (e) => { const r = e.currentTarget.getBoundingClientRect(); const set = (ev) => setPos(clamp(((ev.clientX - r.left) / scale) / ppb, 0, BARS)); set(e); e.currentTarget.setPointerCapture(e.pointerId); e.currentTarget.onpointermove = set; e.currentTarget.onpointerup = () => { e.currentTarget.onpointermove = null; }; };

  const anySolo = tracks.some((t) => t.solo);
  const bar = Math.floor(pos) + 1, beat = Math.floor((pos % 1) * 4) + 1;
  const secs = (pos * 240) / bpm, mm = String(Math.floor(secs / 60)).padStart(2, "0"), ss = String(Math.floor(secs % 60)).padStart(2, "0");
  const totalW = BARS * ppb;
  const recLen = rec && playing ? Math.max(0, pos - recStart.current) : 0;
  const armTrack = tracks.find((t) => t.arm);
  const metroOn = metro && playing && (pos % 0.25) < 0.04;

  return (
    <div className="dw" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: BG }}>

          {/* top bar */}
          <div style={{ position: "absolute", left: 10, right: 10, top: 8, height: 44, display: "flex", alignItems: "center", gap: 8, zIndex: 70 }}>
            <NavMenu page={page} onSelect={(p) => { setPage(p); setStepOpen(false); }} />
            {page === "tracks" ? (
              <>
                <div style={{ display: "flex", gap: 5, flex: "none" }}>
                  <Btn w={40} h={44} onClick={() => setPos(0)} title="Start" style={{ borderRadius: 12 }}><Ico n="rew" size={17} /></Btn>
                  <Btn w={44} h={44} onClick={togglePlay} active={playing} color={AC} title="Play / pause" style={{ borderRadius: 12 }}><Ico n={playing ? "pause" : "play"} size={19} /></Btn>
                  <Btn w={40} h={44} onClick={stopAll} title="Stop" style={{ borderRadius: 12 }}><Ico n="stop" size={16} /></Btn>
                  <Btn w={40} h={44} onClick={toggleRec} active={rec} color={RED} title="Record" style={{ borderRadius: 12 }}><Ico n="rec" size={18} /></Btn>
                  <Btn w={40} h={44} onClick={() => setLoopOn((v) => !v)} active={loopOn} color={AC} title="Loop" style={{ borderRadius: 12 }}><Ico n="loop" size={16} /></Btn>
                  <Btn w={40} h={44} onClick={() => setMetro((v) => !v)} active={metro} color={AMBER} title="Metronome" style={{ borderRadius: 12 }}><Ico n="metro" size={16} /></Btn>
                </div>
                <div style={{ height: 44, borderRadius: 12, padding: "0 14px", background: PANEL2, border: `1px solid ${LINE}`, display: "flex", alignItems: "center", gap: 12, flex: "none" }}>
                  <span style={{ width: 8, height: 8, borderRadius: 4, background: metroOn ? AMBER : "#2B3447" }} />
                  <div style={{ lineHeight: 1.05 }}>
                    <div style={{ fontFamily: MONO, fontSize: 17, fontWeight: 800 }}>{String(bar).padStart(3, "0")}.{beat}</div>
                    <div style={{ fontFamily: MONO, fontSize: 10, color: "#7B859A", fontWeight: 700 }}>{mm}:{ss}</div>
                  </div>
                </div>
                <div style={{ height: 44, borderRadius: 12, padding: "0 6px", background: PANEL2, border: `1px solid ${LINE}`, display: "flex", alignItems: "center", gap: 4, flex: "none" }}>
                  <Btn w={26} h={26} onClick={() => setBpm((b) => clamp(b - 1, 40, 240))} style={{ borderRadius: 13, background: "#262E3E" }}><Ico n="minus" size={13} /></Btn>
                  <div style={{ textAlign: "center", width: 48, lineHeight: 1.05 }}><div style={{ fontFamily: MONO, fontSize: 16, fontWeight: 800 }}>{bpm}</div><div style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: 1, color: "#7B859A" }}>BPM</div></div>
                  <Btn w={26} h={26} onClick={() => setBpm((b) => clamp(b + 1, 40, 240))} style={{ borderRadius: 13, background: "#262E3E" }}><Ico n="plus" size={13} /></Btn>
                </div>
                <div style={{ height: 44, borderRadius: 12, padding: "0 12px", background: PANEL2, border: `1px solid ${LINE}`, display: "grid", placeItems: "center", fontFamily: MONO, fontSize: 14, fontWeight: 800, flex: "none" }}>4/4</div>
                <div style={{ flex: 1 }} />
                <Btn w={40} h={44} onClick={undo} disabled={!undoStack.length} title="Undo" style={{ borderRadius: 12 }}><Ico n="undo" size={16} /></Btn>
                <Btn w={40} h={44} onClick={redo} disabled={!redoStack.length} title="Redo" style={{ borderRadius: 12 }}><Ico n="redo" size={16} /></Btn>
              </>
            ) : <div style={{ flex: 1 }} />}
          </div>

          {page === "mixer" && <MixerPage tracks={tracks} setTracks={setTracks} />}
          {page === "drumpad" && <PadsPage />}

          {page === "tracks" && (
            <div style={{ animation: "pageIn .28s ease-out" }}>
              {/* toolbar */}
              <div style={{ position: "absolute", left: 10, right: 10, top: 58, height: 30, display: "flex", alignItems: "center", gap: 8, zIndex: 20 }}>
                <Seg options={[["select", <Ico key="a" n="cursor" size={14} />], ["split", <Ico key="b" n="scissors" size={14} />], ["erase", <Ico key="c" n="eraser" size={14} />]]} value={tool} onChange={setTool} w={120} h={26} />
                <Seg options={SNAPS.map(([k, l]) => [k, l])} value={snap} onChange={setSnap} w={200} h={26} fs={11} />
                <div style={{ display: "flex", alignItems: "center", gap: 4, flex: "none" }}>
                  <Btn w={28} h={28} onClick={() => setZi((z) => Math.max(0, z - 1))} disabled={zi === 0} style={{ borderRadius: 8 }}><Ico n="minus" size={13} /></Btn>
                  <span style={{ fontSize: 10, fontWeight: 800, color: "#7B859A", width: 34, textAlign: "center", letterSpacing: 0.8 }}>ZOOM</span>
                  <Btn w={28} h={28} onClick={() => setZi((z) => Math.min(ZOOMS.length - 1, z + 1))} disabled={zi === ZOOMS.length - 1} style={{ borderRadius: 8 }}><Ico n="plus" size={13} /></Btn>
                </div>
                <div style={{ flex: 1 }} />
                {selClip && (
                  <div style={{ display: "flex", gap: 5, animation: "fadeIn .2s" }}>
                    <Btn h={28} onClick={doDup} style={{ borderRadius: 8 }}><Ico n="copy" size={13} /> Duplicate</Btn>
                    <Btn h={28} onClick={doSplit} style={{ borderRadius: 8 }}><Ico n="scissors" size={13} /> Split</Btn>
                    <Btn h={28} onClick={() => doEdit()} style={{ borderRadius: 8 }}><Ico n="edit" size={13} /> Edit</Btn>
                    <Btn h={28} onClick={doDel} color={RED} style={{ borderRadius: 8 }}><Ico n="trash" size={13} /></Btn>
                  </div>
                )}
                <div style={{ position: "relative", flex: "none" }}>
                  <Btn h={28} onClick={() => setAddMenu((v) => !v)} active={addMenu} style={{ borderRadius: 8 }}><Ico n="plus" size={13} /> Track</Btn>
                  {addMenu && (
                    <>
                      <div onClick={() => setAddMenu(false)} style={{ position: "fixed", inset: 0, zIndex: 80 }} />
                      <div style={{ position: "absolute", right: 0, top: 34, width: 170, borderRadius: 12, padding: 5, background: "#222A38", border: `1px solid ${LINE}`, boxShadow: "0 14px 36px rgba(0,0,0,.5)", transformOrigin: "top right", animation: "menuIn .16s ease-out", zIndex: 81 }}>
                        {[["audio", "Audio", "wave"], ["midi", "Instrument", "keys"], ["drums", "Drums", "pads"]].map(([k, l, ic]) => (
                          <button key={k} onClick={() => addTrack(k)} className="press" style={{ width: "100%", height: 38, border: 0, borderRadius: 9, display: "flex", alignItems: "center", gap: 10, padding: "0 10px", cursor: "pointer", background: "transparent", color: "#D5DCE8", fontSize: 13, fontWeight: 700 }}>
                            <span style={{ color: AC, display: "inline-flex" }}><Ico n={ic} size={16} /></span>{l}
                          </button>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              </div>

              {/* arrangement */}
              <div style={{ position: "absolute", left: 10, right: 10, top: 94, bottom: 8, borderRadius: 12, overflow: "hidden", background: "#141922", border: `1px solid ${LINE}` }}>
                <div ref={scrollRef} className="noscroll" style={{ position: "absolute", inset: 0, overflow: "auto" }} onPointerMove={clipMove} onPointerUp={() => { drag.current = null; }}>
                  <div style={{ width: HEAD + totalW, minHeight: "100%", position: "relative" }}>
                    {/* ruler */}
                    <div style={{ display: "flex", position: "sticky", top: 0, zIndex: 8, height: RULER }}>
                      <div style={{ width: HEAD, flex: "none", position: "sticky", left: 0, zIndex: 9, background: "#1A202B", borderRight: `1px solid ${LINE}`, borderBottom: `1px solid ${LINE}`, display: "flex", alignItems: "center", padding: "0 10px", fontSize: 9.5, fontWeight: 800, letterSpacing: 1.2, color: "#566075" }}>{tracks.length} TRACKS</div>
                      <div onPointerDown={rulerDown} style={{ width: totalW, flex: "none", position: "relative", background: "#1A202B", borderBottom: `1px solid ${LINE}`, cursor: "pointer", touchAction: "none" }}>
                        <div style={{ position: "absolute", left: LOOP[0] * ppb, width: (LOOP[1] - LOOP[0]) * ppb, top: 0, bottom: 0, background: rgba(AC, loopOn ? 0.22 : 0.08), borderTop: `3px solid ${loopOn ? AC : "#3A4458"}` }} />
                        {Array.from({ length: BARS }).map((_, b) => (
                          <div key={b} style={{ position: "absolute", left: b * ppb, top: 0, bottom: 0, borderLeft: `1px solid ${b % 4 === 0 ? "rgba(255,255,255,.18)" : "rgba(255,255,255,.08)"}`, paddingLeft: 4, fontFamily: MONO, fontSize: 9.5, fontWeight: 700, color: "#7B859A", paddingTop: 5 }}>{b + 1}</div>
                        ))}
                        <div style={{ position: "absolute", left: pos * ppb - 6, top: RULER - 9, width: 0, height: 0, borderLeft: "6px solid transparent", borderRight: "6px solid transparent", borderTop: "9px solid #fff", pointerEvents: "none" }} />
                      </div>
                    </div>

                    {/* tracks */}
                    {tracks.map((t) => {
                      const dim = t.mute || (anySolo && !t.solo);
                      const sel_ = selTrack === t.id;
                      return (
                        <div key={t.id} style={{ display: "flex", height: ROW }}>
                          <div onClick={() => setSelTrack(t.id)} style={{ width: HEAD, flex: "none", height: ROW, position: "sticky", left: 0, zIndex: 5, background: sel_ ? PANEL2 : PANEL, borderRight: `1px solid ${LINE}`, borderBottom: `1px solid ${LINE}`, display: "flex", transition: "background .2s" }}>
                            <div style={{ width: 5, background: t.color, flex: "none" }} />
                            <div style={{ flex: 1, minWidth: 0, padding: "5px 8px 5px 8px", display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
                              <div style={{ display: "flex", alignItems: "center", gap: 6, height: 16 }}>
                                <span style={{ color: t.color, display: "inline-flex" }}><Ico n={t.kind === "audio" ? "wave" : t.kind === "midi" ? "keys" : "pads"} size={13} /></span>
                                <span style={{ flex: 1, fontSize: 12.5, fontWeight: 800, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{t.name}</span>
                                {t.kind === "drums" && <button onClick={(e) => { e.stopPropagation(); setPatIdx(0); setStepOpen(true); }} className="press" style={{ height: 16, padding: "0 6px", borderRadius: 5, border: 0, background: rgba(t.color, 0.22), color: t.color, fontSize: 9, fontWeight: 800, cursor: "pointer" }}>STEP</button>}
                              </div>
                              <div style={{ fontSize: 9.5, color: "#6F7A90", fontWeight: 600, height: 11, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{t.sound}</div>
                              <div style={{ display: "flex", alignItems: "center", gap: 4, height: 20 }}>
                                {[["M", "mute", AMBER], ["S", "solo", AC], ["R", "arm", RED]].map(([l, k, c]) => (
                                  <button key={k} onClick={(e) => { e.stopPropagation(); upd(t.id, k === "arm" ? { arm: !t.arm } : { [k]: !t[k] }); if (k === "arm" && !t.arm) setTracks((ts) => ts.map((x) => (x.id === t.id ? { ...x, arm: true } : { ...x, arm: false }))); }} style={{ width: 22, height: 20, borderRadius: 6, border: 0, cursor: "pointer", fontSize: 10, fontWeight: 800, padding: 0, background: t[k] ? c : "#262E3E", color: t[k] ? "#10151F" : "#8E99AE", transition: "background .15s" }}>{l}</button>
                                ))}
                                <div onPointerDown={(e) => { e.stopPropagation(); e.currentTarget.setPointerCapture(e.pointerId); const r = e.currentTarget.getBoundingClientRect(); const set = (ev) => upd(t.id, { vol: clamp((ev.clientX - r.left) / r.width, 0, 1) }); set(e); e.currentTarget.onpointermove = set; e.currentTarget.onpointerup = () => { e.currentTarget.onpointermove = null; }; }}
                                  style={{ flex: 1, height: 20, position: "relative", cursor: "pointer", touchAction: "none", marginLeft: 3 }}>
                                  <div style={{ position: "absolute", left: 0, right: 0, top: 8, height: 4, borderRadius: 2, background: "#0E1218" }} />
                                  <div style={{ position: "absolute", left: 0, top: 8, height: 4, width: `${t.vol * 100}%`, borderRadius: 2, background: t.color }} />
                                  <div style={{ position: "absolute", top: 4, left: `${t.vol * 100}%`, marginLeft: -5, width: 10, height: 12, borderRadius: 3, background: "#D5DCE8" }} />
                                </div>
                              </div>
                            </div>
                          </div>
                          <div onPointerDown={() => setSel(null)} style={{ width: totalW, flex: "none", height: ROW, position: "relative", borderBottom: `1px solid ${LINE}`, background: `repeating-linear-gradient(90deg, rgba(255,255,255,.045) 0 1px, transparent 1px ${ppb}px)` }}>
                            {ppb >= 64 && <div style={{ position: "absolute", inset: 0, background: `repeating-linear-gradient(90deg, rgba(255,255,255,.02) 0 1px, transparent 1px ${ppb / 4}px)`, pointerEvents: "none" }} />}
                            {clips.filter((c) => c.track === t.id).map((c) => (
                              <ClipView key={c.id} clip={c} track={t} ppb={ppb} selected={sel === c.id} dim={dim} pat={patterns[c.pat]} onDown={clipDown} onDouble={doEdit} />
                            ))}
                            {rec && playing && armTrack && armTrack.id === t.id && recLen > 0 && (
                              <div style={{ position: "absolute", left: recStart.current * ppb + 1, top: 4, width: recLen * ppb, height: ROW - 8, borderRadius: 8, background: rgba(RED, 0.85), overflow: "hidden" }}>
                                <div style={{ position: "absolute", left: 6, top: 2, fontSize: 9.5, fontWeight: 800, color: "rgba(10,14,22,.8)", animation: "blink 1s infinite" }}>● REC</div>
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    })}
                    {/* playhead */}
                    <div style={{ position: "absolute", top: RULER, bottom: 0, left: HEAD + pos * ppb - 1, width: 2, background: "#fff", opacity: 0.9, zIndex: 4, pointerEvents: "none" }} />
                  </div>
                </div>
              </div>
            </div>
          )}

          {stepOpen && (
            <StepDrum patterns={patterns} setPatterns={setPatterns} patIdx={patIdx} setPatIdx={setPatIdx} tracks={tracks} pos={pos} playing={playing} bpm={bpm}
              onPlay={togglePlay} onClose={() => setStepOpen(false)} />
          )}
          {toast && <div key={toast.k} style={{ position: "absolute", left: "50%", bottom: 14, transform: "translateX(-50%)", padding: "8px 14px", borderRadius: 10, background: "#222C40", border: `1px solid ${LINE}`, fontSize: 13, fontWeight: 700, animation: "toast .2s ease-out", zIndex: 90, whiteSpace: "nowrap" }}>{toast.t}</div>}
        </div>
      </div>
      <div style={{ marginTop: 10, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 720, lineHeight: 1.55 }}>
        The page name (top left) opens the list Mixer / Tracks / DrumPad · tap the ruler to move the playhead, drag a clip to move it (it snaps to the chosen grid), double-tap a drum clip or press STEP to open the full screen Step Drum editor · Mixer and DrumPad are simulated
      </div>
    </div>
  );
}
