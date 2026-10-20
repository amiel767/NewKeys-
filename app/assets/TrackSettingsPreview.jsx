import { useState, useRef, useMemo } from "react";

/* ─────────────────────────────────────────────────────────────
   TRACK SETTINGS — landscape smartphone preview (891 × 411 dp)
   Track window, Velocity editor, MIDI page, Note range page.
   Everything inside the device frame is the real design, in English.
   Controls under the frame are preview helpers only. No sound is produced.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const AC = "#8FB2E8";
const TR = "#A18AE0";
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const NOTE = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
const BLACK = [1, 3, 6, 8, 10];
const RMIN = 36, RMAX = 96;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const mName = (m) => NOTE[((m % 12) + 12) % 12] + (Math.floor(m / 12) - 1);
const rgba = (h, a) => { const r = parseInt(h.slice(1, 3), 16), g = parseInt(h.slice(3, 5), 16), b = parseInt(h.slice(5, 7), 16); return `rgba(${r},${g},${b},${a})`; };
const mix = (a, b, t) => { const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16)), pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16)); return "#" + pa.map((v, i) => Math.round(v + (pb[i] - v) * t).toString(16).padStart(2, "0")).join(""); };
const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2'/></filter><rect width='100%' height='100%' filter='url(#n)' opacity='.55'/></svg>")}")`;

/* ── velocity model ── */
const XS = [0, 0.25, 0.5, 0.75, 1];
const PRESETS = [
  { id: "linear", label: "Linear", f: (x) => x },
  { id: "soft", label: "Soft", f: (x) => Math.pow(x, 0.6) },
  { id: "hard", label: "Hard", f: (x) => Math.pow(x, 1.7) },
  { id: "scurve", label: "S-curve", f: (x) => (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2) },
  { id: "compress", label: "Compress", f: (x) => 0.5 + (x - 0.5) * 0.5 },
  { id: "custom", label: "Custom", f: null },
];
const ysOf = (f) => XS.map((x) => clamp(f(x), 0, 1));
const DEF_VEL = { preset: "linear", ys: ysOf((x) => x), floor: 0, ceiling: 127, offset: 0, humanize: 0, fixed: false };

function evalCurve(ys, x) {
  x = clamp(x, 0, 1);
  const n = ys.length - 1;
  const seg = Math.min(n - 1, Math.floor(x * n));
  const t = x * n - seg;
  const p0 = ys[Math.max(0, seg - 1)], p1 = ys[seg], p2 = ys[seg + 1], p3 = ys[Math.min(n, seg + 2)];
  const t2 = t * t, t3 = t2 * t;
  return clamp(0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3), 0, 1);
}
function outputOf(input, vel) {
  if (vel.fixed) return 100;
  const y = evalCurve(vel.ys, input / 127);
  return Math.round(clamp(vel.floor + (vel.ceiling - vel.floor) * y + vel.offset, 1, 127));
}
const miniPath = (fn, w, h) => {
  let d = "";
  for (let i = 0; i <= 16; i++) { const x = i / 16, y = clamp(fn(x), 0, 1); d += (i ? "L" : "M") + (1 + x * (w - 2)).toFixed(1) + " " + (1 + (1 - y) * (h - 2)).toFixed(1) + " "; }
  return d;
};

const DEVICES = [
  { id: "all", name: "All devices", sub: "Listen to everything connected" },
  { id: "kb", name: "Keystation 88", sub: "USB · connected" },
  { id: "pad", name: "Launchkey 61", sub: "USB · connected" },
];

const CSS = `
.ts *{box-sizing:border-box}
.ts button{font-family:inherit}
@keyframes winIn{from{opacity:0;scale:.9;translate:0 10px}to{opacity:1;scale:1;translate:0 0}}
@keyframes slideL{from{opacity:0;translate:26px 0}to{opacity:1;translate:0 0}}
@keyframes slideR{from{opacity:0;translate:-26px 0}to{opacity:1;translate:0 0}}
@keyframes rise{from{opacity:0;translate:0 8px}to{opacity:1;translate:0 0}}
@keyframes ring{0%{box-shadow:0 0 0 0 rgba(161,138,224,.6)}100%{box-shadow:0 0 0 9px rgba(161,138,224,0)}}
@keyframes toast{from{opacity:0;translate:0 8px}to{opacity:1;translate:0 0}}
.press{transition:transform .14s cubic-bezier(.3,1.5,.5,1),background .25s,box-shadow .25s,border-color .25s,color .25s}
.press:active{transform:scale(.96)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

function Ico({ n, size = 16, color = "currentColor", sw = 2 }) {
  const P = {
    back: <path d="M19 12H5M11 6l-6 6 6 6" />,
    x: <path d="M6 6l12 12M18 6L6 18" />,
    chev: <path d="M9 6l6 6-6 6" />,
    check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
    reset: <><path d="M20 12a8 8 0 1 1-2.6-5.9" /><path d="M20 4v4.5h-4.5" /></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">{P[n]}</svg>;
}

function IconBtn({ n, onClick, size = 34 }) {
  return (
    <button onClick={onClick} className="press" style={{ width: size, height: size, borderRadius: size / 2, border: "1px solid rgba(255,255,255,.1)", cursor: "pointer", display: "grid", placeItems: "center", color: "#E3E8F2", background: "linear-gradient(180deg,#2F3850,#252D40)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 3px 8px rgba(0,0,0,.35)", flex: "none" }}>
      <Ico n={n} size={15} sw={2.2} />
    </button>
  );
}

function Head({ title, sub, color, onBack, onClose, right }) {
  return (
    <div style={{ height: 66, display: "flex", alignItems: "center", gap: 12, padding: "0 16px 0 16px", borderBottom: "1px solid rgba(255,255,255,.06)", flex: "none", background: "linear-gradient(180deg,rgba(255,255,255,.03),transparent)" }}>
      {onBack ? <IconBtn n="back" onClick={onBack} /> : <span style={{ width: 4, height: 34, borderRadius: 2, background: color, boxShadow: `0 0 12px ${rgba(color, 0.55)}`, flex: "none" }} />}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 18, fontWeight: 800, color: "#F4F6FB", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{title}</div>
        {sub && <div style={{ fontSize: 11.5, color: "#7B859A", marginTop: 2, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{sub}</div>}
      </div>
      {right}
      <IconBtn n="x" onClick={onClose} />
    </div>
  );
}

function Label({ children }) {
  return <div style={{ fontSize: 10, letterSpacing: 1.4, fontWeight: 800, color: "#6F7A90", height: 20, display: "flex", alignItems: "center" }}>{children}</div>;
}

function Switch({ on, onChange, color }) {
  return (
    <button role="switch" aria-checked={on} onClick={() => onChange(!on)} className="press" style={{ width: 38, height: 21, borderRadius: 11, border: 0, padding: 0, position: "relative", cursor: "pointer", flex: "none", background: on ? color : "rgba(255,255,255,.14)", transition: "background .25s", boxShadow: on ? `0 0 10px ${rgba(color, 0.35)}` : "none" }}>
      <span style={{ position: "absolute", top: 2, left: 2, width: 17, height: 17, borderRadius: 9, background: on ? "#0F1726" : "#C7CFDD", transform: `translateX(${on ? 17 : 0}px)`, transition: "transform .28s cubic-bezier(.3,1.4,.5,1)" }} />
    </button>
  );
}

function StepBtn({ n, onClick }) {
  return <button onClick={onClick} className="press" style={{ width: 26, height: 26, borderRadius: 9, border: 0, cursor: "pointer", background: "linear-gradient(180deg,#3A4258,#2E3549)", color: "#E3E8F2", fontSize: 14, fontWeight: 700, boxShadow: "inset 0 1px 0 rgba(255,255,255,.12), 0 2px 4px rgba(0,0,0,.35)" }}>{n}</button>;
}

function Stepper({ value, min, max, onChange, fmt }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
      <StepBtn n="−" onClick={() => onChange(clamp(value - 1, min, max))} />
      <span style={{ minWidth: 48, textAlign: "center", fontFamily: MONO, fontSize: 13, fontWeight: 800, color: "#F1F4FA" }}>{fmt ? fmt(value) : value}</span>
      <StepBtn n="+" onClick={() => onChange(clamp(value + 1, min, max))} />
    </div>
  );
}

function Seg({ options, value, onChange, color, w }) {
  const idx = Math.max(0, options.findIndex((o) => o[0] === value));
  return (
    <div style={{ position: "relative", display: "flex", padding: 3, borderRadius: 11, background: "#0E131C", boxShadow: "inset 0 1px 3px rgba(0,0,0,.55)", width: w }}>
      <div style={{ position: "absolute", top: 3, bottom: 3, left: 3, width: `calc((100% - 6px) / ${options.length})`, borderRadius: 8, background: "linear-gradient(180deg,#3A4663,#2B3650)", boxShadow: "0 2px 5px rgba(0,0,0,.4)", transform: `translateX(${idx * 100}%)`, transition: "transform .3s cubic-bezier(.3,1.35,.5,1)" }} />
      {options.map(([k, l]) => (
        <button key={String(k)} onClick={() => onChange(k)} style={{ position: "relative", flex: 1, height: 26, border: 0, background: "transparent", cursor: "pointer", fontSize: 12, fontWeight: 800, color: value === k ? (color || "#F1F4FA") : "#8690A4", transition: "color .25s" }}>{l}</button>
      ))}
    </div>
  );
}

function Slide({ label, value, min, max, step = 1, onChange, fmt, color, dim }) {
  const ref = useRef(null);
  const frac = (value - min) / (max - min);
  const set = (e) => {
    const r = ref.current.getBoundingClientRect();
    const f = clamp((e.clientX - r.left) / r.width, 0, 1);
    onChange(Math.round((min + f * (max - min)) / step) * step);
  };
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 3, opacity: dim ? 0.45 : 1, transition: "opacity .25s" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", fontSize: 11.5, color: "#9AA5BA", fontWeight: 700 }}>
        <span>{label}</span>
        <span style={{ fontFamily: MONO, fontSize: 12, color: "#E9EDF5", fontWeight: 800 }}>{fmt ? fmt(value) : value}</span>
      </div>
      <div ref={ref} onPointerDown={(e) => { if (dim) return; e.currentTarget.setPointerCapture(e.pointerId); set(e); }} onPointerMove={(e) => { if (e.buttons && !dim) set(e); }}
        style={{ position: "relative", height: 18, cursor: dim ? "default" : "pointer", touchAction: "none" }}>
        <div style={{ position: "absolute", left: 0, right: 0, top: 7, height: 4, borderRadius: 2, background: "#0E131C", boxShadow: "inset 0 1px 2px rgba(0,0,0,.6)" }} />
        <div style={{ position: "absolute", left: 0, top: 7, height: 4, width: `${frac * 100}%`, borderRadius: 2, background: `linear-gradient(90deg, ${rgba(color, 0.4)}, ${color})` }} />
        <div style={{ position: "absolute", top: 2, left: `${frac * 100}%`, marginLeft: -7, width: 14, height: 14, borderRadius: 5, background: "linear-gradient(180deg,#6E7799,#3F4766)", border: "1px solid rgba(255,255,255,.16)", boxShadow: "0 2px 4px rgba(0,0,0,.5)" }} />
      </div>
    </div>
  );
}

/* ═══════════════ TRACK PAGE ═══════════════ */

function NavRow({ title, hint, onClick, right, color }) {
  return (
    <div onClick={onClick} className="press" style={{ height: 50, display: "flex", alignItems: "center", gap: 10, padding: "0 10px 0 12px", borderRadius: 13, cursor: "pointer", background: "linear-gradient(180deg,rgba(255,255,255,.065),rgba(255,255,255,.03))", border: "1px solid rgba(255,255,255,.06)", boxShadow: "0 3px 8px rgba(0,0,0,.25)" }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13.5, fontWeight: 800, color: "#E9EDF5", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{title}</div>
        <div style={{ fontSize: 11, color: "#7B859A", marginTop: 2, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{hint}</div>
      </div>
      {right}
      <span style={{ color: "#6F7A90", display: "inline-flex" }}><Ico n="chev" size={15} /></span>
    </div>
  );
}

function ValueChip({ children, color }) {
  return <span style={{ padding: "3px 8px", borderRadius: 8, background: rgba(color, 0.14), border: `1px solid ${rgba(color, 0.35)}`, color: "#E9EDF5", fontSize: 11, fontWeight: 800, fontFamily: MONO, whiteSpace: "nowrap" }}>{children}</span>;
}

function MiniKeys({ low, high, color }) {
  const x = (m) => clamp((m - RMIN) / (RMAX - RMIN), 0, 1) * 76;
  return (
    <svg width="78" height="14" viewBox="0 0 78 14" style={{ flex: "none" }}>
      <rect x="0.5" y="0.5" width="77" height="13" rx="3" fill="#0E131C" stroke="rgba(255,255,255,.08)" />
      <rect x={x(low) + 1} y="3" width={Math.max(3, x(high) - x(low))} height="8" rx="2" fill={color} opacity="0.85" />
    </svg>
  );
}

function TrackPage({ color, name, go, vel, midi, range, transp, setTransp, oct, setOct, bend, setBend, volume, setVolume, pan, setPan, sustain, setSustain, aftertouch, setAftertouch, program, setProgram }) {
  const devName = DEVICES.find((d) => d.id === midi.dev).name;
  const sparkFn = (x) => outputOf(x * 127, vel) / 127;
  return (
    <div style={{ display: "flex", gap: 16, padding: "12px 16px 14px", height: "100%" }}>
      <div className="noscroll" style={{ width: 268, flex: "none", overflowY: "auto", display: "flex", flexDirection: "column", gap: 7 }}>
        <Label>PERFORMANCE</Label>
        <NavRow title="Velocity & Dynamic Response" hint={`${vel.preset === "custom" ? "Custom" : PRESETS.find((p) => p.id === vel.preset).label} curve`} onClick={() => go("velocity")} color={color}
          right={<svg width="56" height="22" viewBox="0 0 56 22" style={{ flex: "none" }}><path d={miniPath(sparkFn, 56, 22)} fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>} />
        <NavRow title="MIDI" hint="Input device and channel" onClick={() => go("midi")} color={color}
          right={<ValueChip color={color}>{devName.split(" ")[0]} · {midi.ch === 0 ? "Omni" : "Ch " + midi.ch}</ValueChip>} />
        <NavRow title="Note range" hint="Lowest and highest key" onClick={() => go("range")} color={color}
          right={<><MiniKeys low={range.low} high={range.high} color={color} /><span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 800, color: "#C9D2E2", whiteSpace: "nowrap" }}>{mName(range.low)}–{mName(range.high)}</span></>} />
        <div style={{ height: 6 }} />
        <Label>PLAYBACK</Label>
        {[["Sustain pedal", "Hold notes while the pedal is down", sustain, setSustain], ["Aftertouch", "Send pressure to the sound", aftertouch, setAftertouch], ["Program change", "Accept incoming patch changes", program, setProgram]].map(([t, h, on, set]) => (
          <div key={t} style={{ height: 40, display: "flex", alignItems: "center", gap: 10, padding: "0 4px" }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#E9EDF5" }}>{t}</div>
              <div style={{ fontSize: 10.5, color: "#6F7A90", marginTop: 1 }}>{h}</div>
            </div>
            <Switch on={on} onChange={set} color={color} />
          </div>
        ))}
      </div>

      <div className="noscroll" style={{ flex: 1, minWidth: 0, overflowY: "auto", display: "flex", flexDirection: "column", gap: 8 }}>
        <Label>TONE & LEVEL</Label>
        {[
          ["Transpose", "Shifts this track in semitones", <Stepper value={transp} min={-12} max={12} onChange={setTransp} fmt={(v) => (v > 0 ? "+" + v : v)} />],
          ["Octave", "Moves this track by octaves", <Seg w={238} options={[-3, -2, -1, 0, 1, 2, 3].map((v) => [v, v > 0 ? "+" + v : v < 0 ? "−" + Math.abs(v) : "0"])} value={oct} onChange={setOct} color={color} />],
          ["Pitch bend range", "Semitones reached at full bend", <Stepper value={bend} min={1} max={12} onChange={setBend} fmt={(v) => v + " st"} />],
        ].map(([t, h, ctl]) => (
          <div key={t} style={{ minHeight: 48, display: "flex", alignItems: "center", gap: 10, padding: "4px 4px" }}>
            <div style={{ flex: "0 0 112px", minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#E9EDF5" }}>{t}</div>
              <div style={{ fontSize: 10.5, color: "#6F7A90", marginTop: 1 }}>{h}</div>
            </div>
            <div style={{ flex: 1, minWidth: 0, display: "flex", justifyContent: "flex-end" }}>{ctl}</div>
          </div>
        ))}
        <div style={{ height: 4 }} />
        <Label>DEFAULTS AT LOAD</Label>
        <div style={{ padding: "2px 6px" }}><Slide label="Volume" value={volume} min={0} max={127} onChange={setVolume} color={color} /></div>
        <div style={{ padding: "2px 6px" }}><Slide label="Pan" value={pan} min={-64} max={63} onChange={setPan} color={color} fmt={(v) => (v === 0 ? "C" : v < 0 ? "L " + -v : "R " + v)} /></div>
      </div>
    </div>
  );
}

/* ═══════════════ VELOCITY PAGE (premium curve editor) ═══════════════ */

function VelocityPage({ vel, setVel, test, setTest, color }) {
  const svgRef = useRef(null);
  const [drag, setDrag] = useState(null);
  const ys = vel.ys;
  const path = useMemo(() => {
    let d = "";
    for (let i = 0; i <= 80; i++) { const x = i / 80, y = evalCurve(ys, x); d += (i ? "L" : "M") + (12 + x * 256).toFixed(1) + " " + (10 + (1 - y) * 166).toFixed(1) + " "; }
    return d;
  }, [ys]);
  const out = outputOf(test, vel);
  const dotX = 12 + (test / 127) * 256, dotY = 10 + (1 - evalCurve(ys, test / 127)) * 166;
  const setPoint = (i, y) => { const n = [...ys]; n[i] = clamp(y, 0, 1); setVel({ ...vel, ys: n, preset: "custom" }); };
  const move = (e) => {
    if (drag === null) return;
    const r = svgRef.current.getBoundingClientRect();
    const sy = ((e.clientY - r.top) / r.height) * 200;
    setPoint(drag, 1 - (sy - 10) / 166);
  };
  const choose = (p) => setVel({ ...vel, preset: p.id, ys: p.f ? ysOf(p.f) : vel.ys });
  const custom = (x) => evalCurve(ys, x);

  return (
    <div style={{ display: "flex", gap: 10, padding: "12px 16px 14px", height: "100%" }}>
      {/* presets */}
      <div style={{ width: 120, flex: "none", display: "flex", flexDirection: "column", gap: 5 }}>
        <Label>PRESET</Label>
        {PRESETS.map((p) => {
          const on = vel.preset === p.id;
          const fn = p.f || custom;
          return (
            <button key={p.id} onClick={() => choose(p)} className="press" style={{ height: 36, borderRadius: 11, border: `1px solid ${on ? rgba(color, 0.55) : "rgba(255,255,255,.06)"}`, cursor: "pointer", display: "flex", alignItems: "center", gap: 8, padding: "0 8px 0 9px", background: on ? `linear-gradient(180deg, ${rgba(color, 0.2)}, ${rgba(color, 0.08)})` : "linear-gradient(180deg,rgba(255,255,255,.06),rgba(255,255,255,.03))", color: on ? "#F4F6FB" : "#9AA5BA", fontSize: 12.5, fontWeight: 800, textAlign: "left" }}>
              <svg width="26" height="18" viewBox="0 0 26 18" style={{ flex: "none" }}><path d={miniPath(fn, 26, 18)} fill="none" stroke={on ? color : "#566075"} strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" /></svg>
              <span style={{ flex: 1 }}>{p.label}</span>
            </button>
          );
        })}
      </div>

      {/* curve panel: sunk well */}
      <div style={{ width: 300, flex: "none", borderRadius: 16, padding: "10px 12px 10px", display: "flex", flexDirection: "column", gap: 6, background: "linear-gradient(180deg,#0C1119,#0F1520)", boxShadow: "inset 0 3px 12px rgba(0,0,0,.6), inset 0 0 0 1px rgba(255,255,255,.04)" }}>
        <div style={{ display: "flex", alignItems: "center", height: 18 }}>
          <span style={{ flex: 1, fontSize: 12, fontWeight: 800, color: "#C9D2E2" }}>{vel.preset === "custom" ? "Custom curve" : PRESETS.find((p) => p.id === vel.preset).label + " curve"}</span>
          <span style={{ fontFamily: MONO, fontSize: 11.5, color: "#7B859A", fontWeight: 700 }}>In <b style={{ color: "#E9EDF5" }}>{test}</b> → Out <b style={{ color }}>{out}</b></span>
        </div>
        <svg ref={svgRef} viewBox="0 0 280 200" width="100%" style={{ touchAction: "none", flex: 1, minHeight: 0 }}
          onPointerMove={move} onPointerUp={() => setDrag(null)} onPointerCancel={() => setDrag(null)}>
          <defs>
            <linearGradient id="vfill" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor={color} stopOpacity="0.38" /><stop offset="1" stopColor={color} stopOpacity="0" /></linearGradient>
          </defs>
          {[0.25, 0.5, 0.75].map((g) => <line key={"h" + g} x1="12" x2="268" y1={10 + (1 - g) * 166} y2={10 + (1 - g) * 166} stroke="rgba(255,255,255,.06)" />)}
          {[0.25, 0.5, 0.75].map((g) => <line key={"v" + g} x1={12 + g * 256} x2={12 + g * 256} y1="10" y2="176" stroke="rgba(255,255,255,.06)" />)}
          <line x1="12" x2="268" y1="176" y2="176" stroke="rgba(255,255,255,.12)" />
          <line x1="12" x2="12" y1="10" y2="176" stroke="rgba(255,255,255,.12)" />
          <line x1="12" x2="268" y1={dotY} y2={dotY} stroke={rgba(color, 0.35)} strokeDasharray="3 3" />
          <line x1={dotX} x2={dotX} y1="10" y2="176" stroke={rgba(color, 0.35)} strokeDasharray="3 3" />
          <path d={`${path} L 268 176 L 12 176 Z`} fill="url(#vfill)" />
          <path d={path} fill="none" stroke={color} strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" style={{ filter: `drop-shadow(0 0 4px ${rgba(color, 0.5)})` }} />
          <circle cx={dotX} cy={dotY} r="4.5" fill="#F4F6FB" style={{ filter: `drop-shadow(0 0 6px ${color})` }} />
          {XS.map((x, i) => {
            const cx = 12 + x * 256, cy = 10 + (1 - ys[i]) * 166, active = drag === i;
            return (
              <g key={i} onPointerDown={(e) => { e.stopPropagation(); svgRef.current.setPointerCapture(e.pointerId); setDrag(i); }} style={{ cursor: "ns-resize" }}>
                <circle cx={cx} cy={cy} r="12" fill="transparent" />
                <circle cx={cx} cy={cy} r={active ? 6.2 : 4.8} fill="#0F1520" stroke={color} strokeWidth="2" />
                <circle cx={cx} cy={cy} r="1.8" fill="#F4F6FB" />
              </g>
            );
          })}
          <text x="8" y="12" fontSize="8" fill="#6F7A90" fontFamily={MONO}>127</text>
          <text x="8" y="180" fontSize="8" fill="#6F7A90" fontFamily={MONO}>0</text>
          <text x="12" y="192" fontSize="8" fill="#6F7A90" fontFamily={MONO}>0</text>
          <text x="134" y="192" fontSize="8" fill="#6F7A90" fontFamily={MONO} textAnchor="middle">64</text>
          <text x="268" y="192" fontSize="8" fill="#6F7A90" fontFamily={MONO} textAnchor="end">127</text>
        </svg>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontSize: 11, color: "#7B859A", fontWeight: 700, flex: "none" }}>Test input</span>
          <div style={{ flex: 1 }}><Slide label="" value={test} min={0} max={127} onChange={setTest} color={color} /></div>
        </div>
      </div>

      {/* parameters */}
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 9 }}>
        <Label>SHAPE</Label>
        <div style={{ display: "flex", flexDirection: "column", gap: 9, padding: "2px 2px" }}>
          <Slide label="Floor" value={vel.floor} min={0} max={126} onChange={(v) => setVel({ ...vel, floor: Math.min(v, vel.ceiling - 1), preset: "custom" })} color={color} dim={vel.fixed} />
          <Slide label="Ceiling" value={vel.ceiling} min={1} max={127} onChange={(v) => setVel({ ...vel, ceiling: Math.max(v, vel.floor + 1), preset: "custom" })} color={color} dim={vel.fixed} />
          <Slide label="Offset" value={vel.offset} min={-32} max={32} onChange={(v) => setVel({ ...vel, offset: v, preset: "custom" })} color={color} dim={vel.fixed} fmt={(v) => (v > 0 ? "+" + v : v)} />
          <Slide label="Humanize" value={vel.humanize} min={0} max={24} onChange={(v) => setVel({ ...vel, humanize: v })} color={color} dim={vel.fixed} fmt={(v) => "±" + v} />
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ height: 32, borderRadius: 11, padding: "0 9px 0 11px", display: "flex", alignItems: "center", gap: 8, background: "linear-gradient(180deg,rgba(255,255,255,.06),rgba(255,255,255,.03))", border: "1px solid rgba(255,255,255,.06)" }}>
          <span style={{ flex: 1, fontSize: 11.5, fontWeight: 800, color: "#E9EDF5", whiteSpace: "nowrap" }}>Fixed velocity</span>
          {vel.fixed && <span style={{ fontFamily: MONO, fontSize: 10.5, fontWeight: 800, color }}>100</span>}
          <Switch on={vel.fixed} onChange={(v) => setVel({ ...vel, fixed: v })} color={color} />
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ MIDI PAGE ═══════════════ */

function MidiPage({ color, midi, setMidi }) {
  return (
    <div style={{ display: "flex", gap: 16, padding: "12px 16px 14px", height: "100%" }}>
      <div style={{ width: 236, flex: "none", display: "flex", flexDirection: "column", gap: 6 }}>
        <Label>LISTENS TO</Label>
        {DEVICES.map((d) => {
          const on = midi.dev === d.id;
          return (
            <div key={d.id} onClick={() => setMidi({ ...midi, dev: d.id })} className="press" style={{ height: 52, borderRadius: 13, cursor: "pointer", display: "flex", alignItems: "center", gap: 10, padding: "0 12px", border: `1px solid ${on ? rgba(color, 0.55) : "rgba(255,255,255,.06)"}`, background: on ? `linear-gradient(180deg, ${rgba(color, 0.2)}, ${rgba(color, 0.07)})` : "linear-gradient(180deg,rgba(255,255,255,.06),rgba(255,255,255,.03))", boxShadow: on ? `0 0 14px ${rgba(color, 0.18)}` : "none" }}>
              <span style={{ width: 16, height: 16, borderRadius: 8, flex: "none", border: `1.5px solid ${on ? color : "rgba(255,255,255,.22)"}`, display: "grid", placeItems: "center" }}>
                {on && <span style={{ width: 8, height: 8, borderRadius: 4, background: color, boxShadow: `0 0 8px ${color}` }} />}
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13.5, fontWeight: 800, color: "#F1F4FA", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{d.name}</div>
                <div style={{ fontSize: 10.5, color: "#7B859A", marginTop: 2, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{d.sub}</div>
              </div>
            </div>
          );
        })}
      </div>

      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 6 }}>
        <Label>CHANNEL</Label>
        <div onClick={() => setMidi({ ...midi, ch: 0 })} className="press" style={{ height: 34, borderRadius: 11, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12.5, fontWeight: 800, color: midi.ch === 0 ? "#0F1726" : "#C9D2E2", background: midi.ch === 0 ? color : "linear-gradient(180deg,#2F3850,#252D40)", boxShadow: midi.ch === 0 ? `0 0 14px ${rgba(color, 0.35)}` : "inset 0 1px 0 rgba(255,255,255,.08)", transition: "background .25s, color .25s" }}>Omni · all channels</div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 6 }}>
          {Array.from({ length: 16 }, (_, i) => i + 1).map((c) => {
            const on = midi.ch === c;
            return (
              <button key={c} onClick={() => setMidi({ ...midi, ch: c })} className="press" style={{ height: 32, borderRadius: 10, border: 0, cursor: "pointer", fontFamily: MONO, fontSize: 12.5, fontWeight: 800, color: on ? "#0F1726" : "#C9D2E2", background: on ? color : "linear-gradient(180deg,#2F3850,#252D40)", boxShadow: on ? `0 0 12px ${rgba(color, 0.4)}` : "inset 0 1px 0 rgba(255,255,255,.08), 0 2px 4px rgba(0,0,0,.3)", transition: "background .25s, color .25s" }}>{c}</button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ NOTE RANGE PAGE: range bar + NeoTune-style keyboard ═══════════════ */

function RangePage({ color, range, setRange, name }) {
  const svgRef = useRef(null);
  const [drag, setDrag] = useState(null);
  const [pressed, setPressed] = useState(null);
  const keys = []; for (let m = RMIN; m <= RMAX; m++) keys.push(m);
  const whites = keys.filter((m) => !BLACK.includes(m % 12));
  const blacks = keys.filter((m) => BLACK.includes(m % 12));
  const VW = 576, WW = VW / whites.length, BW = WW * 0.6;
  const BAR_H = 40, KY = 56, WH = 110, BH = 64;
  const inR = (m) => m >= range.low && m <= range.high;
  const wx = (m) => whites.indexOf(m) * WW;
  const bx = (m) => whites.filter((x) => x < m).length * WW;
  const cx = (m) => (BLACK.includes(m % 12) ? bx(m) : wx(m) + WW / 2);
  const xl = cx(range.low), xh = cx(range.high);
  const count = range.high - range.low + 1;
  const lit = mix(color, "#FFFFFF", 0.35);
  const litDeep = mix(color, "#000000", 0.25);

  const nearest = (x) => {
    let best = RMIN, bd = 1e9;
    keys.forEach((m) => { const d = Math.abs(cx(m) - x); if (d < bd) { bd = d; best = m; } });
    return best;
  };
  const xFromEvent = (e) => { const r = svgRef.current.getBoundingClientRect(); return ((e.clientX - r.left) / r.width) * VW; };
  const applyDrag = (e) => {
    if (!drag) return;
    const m = nearest(xFromEvent(e));
    if (drag === "low") setRange({ ...range, low: clamp(m, RMIN, range.high - 1) });
    else setRange({ ...range, high: clamp(m, range.low + 1, RMAX) });
  };
  const tap = (m) => {
    if (Math.abs(m - range.low) <= Math.abs(m - range.high)) setRange({ ...range, low: clamp(m, RMIN, range.high - 1) });
    else setRange({ ...range, high: clamp(m, range.low + 1, RMAX) });
    setPressed(m); setTimeout(() => setPressed(null), 160); vib(6);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10, padding: "12px 16px 10px", height: "100%" }}>
      <div style={{ display: "flex", gap: 10 }}>
        {[["LOWEST", "low", RMIN, range.high - 1], ["HIGHEST", "high", range.low + 1, RMAX]].map(([t, k, mn, mx]) => (
          <div key={k} style={{ flex: 1, height: 46, borderRadius: 13, padding: "0 12px", display: "flex", alignItems: "center", gap: 10, background: "linear-gradient(180deg,rgba(255,255,255,.065),rgba(255,255,255,.03))", border: "1px solid rgba(255,255,255,.06)" }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 9.5, letterSpacing: 1.4, fontWeight: 800, color: "#6F7A90" }}>{t}</div>
              <div style={{ fontFamily: MONO, fontSize: 17, fontWeight: 800, color, marginTop: 1 }}>{mName(range[k])}</div>
            </div>
            <Stepper value={range[k]} min={mn} max={mx} onChange={(v) => setRange({ ...range, [k]: v })} fmt={() => ""} />
          </div>
        ))}
      </div>

      <div style={{ flex: 1, minHeight: 0, display: "flex" }}>
        <svg ref={svgRef} viewBox={`0 0 ${VW} ${KY + WH + 14}`} width="100%" height="100%" preserveAspectRatio="xMidYMid meet"
          style={{ touchAction: "none", overflow: "visible" }}
          onPointerMove={applyDrag} onPointerUp={() => setDrag(null)} onPointerCancel={() => setDrag(null)}>
          <defs>
            <linearGradient id="nwh" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#FFFFFF" /><stop offset=".7" stopColor="#F1F3F7" /><stop offset="1" stopColor="#D9DDE6" /></linearGradient>
            <linearGradient id="nwl" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor={lit} /><stop offset=".7" stopColor={color} /><stop offset="1" stopColor={litDeep} /></linearGradient>
            <linearGradient id="nbh" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#2A3040" /><stop offset=".5" stopColor="#141823" /><stop offset="1" stopColor="#07090E" /></linearGradient>
            <linearGradient id="nbl" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor={lit} /><stop offset=".5" stopColor={color} /><stop offset="1" stopColor={litDeep} /></linearGradient>
            <linearGradient id="nbar" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor={lit} /><stop offset="1" stopColor={color} /></linearGradient>
            <linearGradient id="nout" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#1C2231" /><stop offset="1" stopColor="#141A26" /></linearGradient>
            <filter id="nks" x="-10%" y="-5%" width="120%" height="115%"><feDropShadow dx="0" dy="1.6" stdDeviation="1.1" floodColor="#000" floodOpacity=".55" /></filter>
            <filter id="nkb" x="-20%" y="-5%" width="140%" height="125%"><feDropShadow dx="0" dy="3" stdDeviation="1.8" floodColor="#000" floodOpacity=".7" /></filter>
          </defs>

          {/* range bar: full width, selected span in track color */}
          <rect x="0" y="0" width={VW} height={BAR_H} rx="14" fill="url(#nout)" stroke="rgba(255,255,255,.06)" />
          <rect x={xl} y="0" width={Math.max(4, xh - xl)} height={BAR_H} rx="14" fill="url(#nbar)" />
          <rect x={xl} y="0" width={Math.max(4, xh - xl)} height="14" rx="7" fill="#FFFFFF" opacity=".14" />
          <text x={(xl + xh) / 2} y={BAR_H / 2 + 5} fontSize="13" fontWeight="800" textAnchor="middle" fill="#0F1726" fontFamily="Inter, system-ui, sans-serif">{name}</text>
          <rect x={xl + 10} y={BAR_H / 2 - 9} width="44" height="18" rx="6" fill="#0F1726" opacity=".78" />
          <text x={xl + 32} y={BAR_H / 2 + 4} fontSize="10.5" fontWeight="800" textAnchor="middle" fill="#F1F4FA" fontFamily={MONO}>{mName(range.low)}</text>
          <rect x={xh - 54} y={BAR_H / 2 - 9} width="44" height="18" rx="6" fill="#0F1726" opacity=".78" />
          <text x={xh - 32} y={BAR_H / 2 + 4} fontSize="10.5" fontWeight="800" textAnchor="middle" fill="#F1F4FA" fontFamily={MONO}>{mName(range.high)}</text>

          {[["low", xl], ["high", xh]].map(([k, x]) => (
            <g key={k} onPointerDown={(e) => { e.currentTarget.ownerSVGElement.setPointerCapture(e.pointerId); setDrag(k); vib(6); }} style={{ cursor: "ew-resize" }}>
              <circle cx={x} cy={BAR_H / 2} r="17" fill="transparent" />
              <circle cx={x} cy={BAR_H / 2 + 1.5} r={drag === k ? 11 : 10} fill="rgba(0,0,0,.35)" />
              <circle cx={x} cy={BAR_H / 2} r={drag === k ? 11.5 : 10} fill="#FFFFFF" style={{ transition: "r .15s" }} />
              <circle cx={x} cy={BAR_H / 2} r="3.6" fill="#0F1726" />
            </g>
          ))}

          {/* keyboard frame */}
          <rect x="-6" y={KY - 8} width={VW + 12} height={WH + 24} rx="14" fill="#0A0D14" stroke="rgba(255,255,255,.05)" />

          {/* white keys */}
          {whites.map((m) => {
            const x = wx(m) + 0.5, w = WW - 1, on = inR(m), pr = pressed === m;
            return (
              <g key={m} onPointerDown={() => tap(m)} style={{ cursor: "pointer" }}>
                <rect x={x} y={KY + (pr ? 1.5 : 0)} width={w} height={WH} rx="3" fill={on ? "url(#nwl)" : "url(#nwh)"} stroke="rgba(0,0,0,.28)" strokeWidth=".6" filter="url(#nks)" />
                <rect x={x + 1} y={KY + (pr ? 1.5 : 0) + WH - 16} width={w - 2} height="16" rx="2" fill={on ? "#000" : "#000"} opacity=".05" />
                <rect x={x + w / 2 - 6} y={KY + WH - 12} width="12" height="4" rx="2" fill={on ? "#FFFFFF" : "#C9CED9"} opacity={on ? 0.95 : 0.6} style={{ filter: on ? `drop-shadow(0 0 3px ${color})` : "none" }} />
              </g>
            );
          })}

          {/* black keys */}
          {blacks.map((m) => {
            const c = bx(m), l = c - BW / 2, on = inR(m), pr = pressed === m;
            return (
              <g key={m} onPointerDown={() => tap(m)} style={{ cursor: "pointer" }} filter="url(#nkb)">
                <rect x={l} y={KY + (pr ? 1.2 : 0)} width={BW} height={BH} rx="2.5" fill={on ? "url(#nbl)" : "url(#nbh)"} stroke="rgba(0,0,0,.6)" strokeWidth=".6" />
                <rect x={l + 1.5} y={KY + (pr ? 1.2 : 0) + 1.5} width={BW - 3} height="3" rx="1.2" fill="#FFFFFF" opacity={on ? 0.35 : 0.14} />
                <rect x={c - 4.5} y={KY + BH - 11} width="9" height="3.5" rx="1.8" fill={on ? "#FFFFFF" : "#3A4152"} opacity={on ? 0.95 : 1} style={{ filter: on ? `drop-shadow(0 0 3px ${color})` : "none" }} />
              </g>
            );
          })}
        </svg>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#7B859A", fontWeight: 700 }}>
        <span>Drag a handle, or tap a key to move the nearest limit</span>
        <span style={{ fontFamily: MONO, color: "#C9D2E2" }}>{count} notes</span>
      </div>
    </div>
  );
}

/* ═══════════════ MAIN ═══════════════ */

export default function TrackSettingsPreview() {
  const [screen, setScreen] = useState("track");
  const [dir, setDir] = useState(1);
  const [vel, setVel] = useState(DEF_VEL);
  const [midi, setMidi] = useState({ dev: "all", ch: 0 });
  const [range, setRange] = useState({ low: 36, high: 84 });
  const [transp, setTransp] = useState(0);
  const [oct, setOct] = useState(0);
  const [bend, setBend] = useState(2);
  const [volume, setVolume] = useState(100);
  const [pan, setPan] = useState(0);
  const [sustain, setSustain] = useState(true);
  const [aftertouch, setAftertouch] = useState(false);
  const [program, setProgram] = useState(true);
  const [test, setTest] = useState(64);
  const [toast, setToast] = useState(null);
  const [winKey, setWinKey] = useState(0);
  const color = TR;
  const trackName = "Track 4";
  const sound = "Organ B3 Fast";

  const go = (s) => { setDir(s === "track" ? -1 : 1); setScreen(s); vib(6); };
  const say = (t) => { setToast({ t, k: Date.now() }); setTimeout(() => setToast(null), 1500); };

  const titles = {
    track: { t: trackName + " Settings", s: "Sound: " + sound },
    velocity: { t: "Velocity & Dynamic Response", s: trackName + " · " + sound },
    midi: { t: "MIDI", s: trackName + " listens to" },
    range: { t: "Note range", s: trackName + " · " + mName(range.low) + " – " + mName(range.high) },
  };
  const cur = titles[screen];

  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 130) / H, 1.5));
  const portrait = vp.h > vp.w;

  return (
    <div className="ts" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
          {/* dimmed mixer behind the window */}
          <div style={{ position: "absolute", left: 11, top: 10, width: 869, height: 52, borderRadius: 14, background: "#1B2232", opacity: 0.5 }} />
          {[0, 1, 2, 3].map((i) => <div key={i} style={{ position: "absolute", left: 11 + i * 132, top: 70, width: 126, height: 300, borderRadius: 18, background: "#1B2232", opacity: 0.5 }} />)}
          <div style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.6)", backdropFilter: "blur(2px)" }} />

          <div key={winKey} style={{ position: "absolute", left: "50%", top: "50%", width: 600, height: 370, marginLeft: -300, marginTop: -185, borderRadius: 22, overflow: "hidden", background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 26px 64px rgba(0,0,0,.6)", animation: "winIn .38s cubic-bezier(.2,.9,.25,1)", display: "flex", flexDirection: "column" }}>
            <Head title={cur.t} sub={cur.s} color={color}
              onBack={screen !== "track" ? () => go("track") : null}
              onClose={() => say("Closes the window")}
              right={screen === "velocity" ? (
                <button onClick={() => setVel(DEF_VEL)} className="press" style={{ height: 30, padding: "0 12px", borderRadius: 10, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.05)", color: "#C7CFDD", fontSize: 12, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", gap: 6, marginRight: 4 }}><Ico n="reset" size={13} /> Reset</button>
              ) : null} />
            <div key={screen} className="noscroll" style={{ flex: 1, minHeight: 0, overflow: "hidden", animation: `${dir > 0 ? "slideL" : "slideR"} .28s cubic-bezier(.2,.9,.25,1)` }}>
              {screen === "track" && <TrackPage color={color} go={go} vel={vel} midi={midi} range={range}
                transp={transp} setTransp={setTransp} oct={oct} setOct={setOct} bend={bend} setBend={setBend}
                volume={volume} setVolume={setVolume} pan={pan} setPan={setPan}
                sustain={sustain} setSustain={setSustain} aftertouch={aftertouch} setAftertouch={setAftertouch}
                program={program} setProgram={setProgram} />}
              {screen === "velocity" && <VelocityPage vel={vel} setVel={setVel} test={test} setTest={setTest} color={color} />}
              {screen === "midi" && <MidiPage color={color} midi={midi} setMidi={setMidi} />}
              {screen === "range" && <RangePage color={color} range={range} setRange={setRange} name={trackName} />}
            </div>
            {screen === "velocity" && (
              <div style={{ height: 0 }} />
            )}
          </div>

          {toast && <div key={toast.k} style={{ position: "absolute", left: "50%", bottom: 14, transform: "translateX(-50%)", padding: "8px 14px", borderRadius: 12, background: "#222C40", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 10px 26px rgba(0,0,0,.5)", fontSize: 13, fontWeight: 600, animation: "toast .22s ease-out", zIndex: 60 }}>{toast.t}</div>}
        </div>
      </div>

      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", maxWidth: 760 }}>
        {[["Track settings", () => go("track")], ["Velocity", () => go("velocity")], ["MIDI", () => go("midi")], ["Note range", () => go("range")]].map(([l, fn]) => (
          <button key={l} onClick={fn} style={{ padding: "7px 14px", borderRadius: 999, border: `1px solid ${screen === l.toLowerCase().split(" ")[0] || (screen === "track" && l === "Track settings") ? rgba(color, 0.6) : "#2A3446"}`, background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>{l}</button>
        ))}
        <button onClick={() => { setVel(DEF_VEL); setMidi({ dev: "all", ch: 0 }); setRange({ low: 36, high: 84 }); setTransp(0); setOct(0); setBend(2); setVolume(100); setPan(0); setSustain(true); setAftertouch(false); setProgram(true); setScreen("track"); setWinKey((k) => k + 1); }} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Reset demo</button>
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 660, lineHeight: 1.55 }}>
        Velocity: drag the points of the curve, or pick a preset (Custom appears once you edit) · the test slider shows the output live · Note range: tap a key to move the nearest limit · MIDI: pick the device, then the channel (Omni or 1 to 16)
        {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
      </div>
    </div>
  );
}
