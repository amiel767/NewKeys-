import { useState, useRef, useEffect } from "react";

/* ─────────────────────────────────────────────────────────────
   MIDI EFFECT MODES — landscape smartphone preview (891 × 411 dp)
   Style of the Sunday Keys screenshots: navy background with grain, soft relief tiles, pale blue selection.
   One MIDI effect slot per track (+ up to 3 audio effects). The 16 steps are always visible: the step count
   only enables the first N, the others stay there, dimmed. Swing is a global setting (simulated mixer bar).
   Notes of the Sequencer / Easy Chord are set by playing a key on the keyboard (simulated under the frame).
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const IN1 = "#C7D9F9", IN2 = "#9DB9EF", OUT1 = "#6B75A8", OUT2 = "#505A8C", EMPTY = "#1F2539", BTN = "#252B45";
const TXT = "#E7ECFB", DIM = "#7C86A8", ORG = "#F2A33A", LINE = "rgba(255,255,255,.07)";
const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2'/></filter><rect width='100%' height='100%' filter='url(#n)' opacity='.55'/></svg>")}")`;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const NN = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
const nm = (m) => NN[m % 12] + (Math.floor(m / 12) - 1);

/* ── music ── */
const QUAL = { maj: [0, 4, 7], min: [0, 3, 7], "7": [0, 4, 7, 10], maj7: [0, 4, 7, 11], m7: [0, 3, 7, 10], sus2: [0, 2, 7], sus4: [0, 5, 7], dim: [0, 3, 6], add9: [0, 4, 7, 14] };
const SUF = { maj: "", min: "m", "7": "7", maj7: "maj7", m7: "m7", sus2: "sus2", sus4: "sus4", dim: "dim", add9: "add9" };
const SCALES = { major: [0, 2, 4, 5, 7, 9, 11], minor: [0, 2, 3, 5, 7, 8, 10] };
const DIA = { major: ["maj", "min", "min", "maj", "maj", "min", "dim"], minor: ["min", "dim", "maj", "min", "min", "maj", "maj"] };
const ROMAN = { major: ["I", "ii", "iii", "IV", "V", "vi", "vii°"], minor: ["i", "ii°", "III", "iv", "v", "VI", "VII"] };
const diaChord = (key, scale, d, sev) => { let q = DIA[scale][d]; if (sev) q = q === "maj" ? (d === 4 ? "7" : "maj7") : q === "min" ? "m7" : q; return { root: (key + SCALES[scale][d]) % 12, q }; };
const chordNotes = (c) => { const n = QUAL[c.q].map((i) => 48 + c.root + i + 12 * (c.oct || 0)); for (let k = 0; k < c.inv; k++) n.push(n.shift() + 12); if (c.voicing === "open" && n.length > 2) n[1] += 12; if (c.voicing === "drop2" && n.length > 2) n[n.length - 2] -= 12; return n.sort((a, b) => a - b); };
const chordName = (c) => NN[c.root] + SUF[c.q] + (c.inv > 0 ? "/" + NN[chordNotes(c)[0] % 12] : "");

const SUBS = [["1/4", 1], ["1/8", 2], ["1/16", 4], ["1/32", 8]];
const STYLES = [["up", "Up"], ["down", "Down"], ["updown", "Up-Down"], ["downup", "Down-Up"], ["random", "Random"], ["chord", "Chord"]];
const MIDI_DEFS = {
  arp: { name: "Arpeggiator", glyph: "⤴", desc: "Turns held notes into a pattern" },
  octave: { name: "Octave Up", glyph: "⇧", desc: "Adds an octave above every note" },
  easy: { name: "Easy Chord", glyph: "♬", desc: "One note plays a programmed chord" },
  seq: { name: "Sequencer", glyph: "▤", desc: "One note plays a sequence of notes" },
  chords: { name: "Chord Sequencer", glyph: "♪", desc: "Plays a chord progression" },
};
const AUDIO_DEFS = { delay: "Delay", reverb: "Reverb", chorus: "Chorus", tremolo: "Tremolo", rotor: "Rotor", overdrive: "Overdrive" };

const mkMidi = (type) => {
  let params;
  if (type === "arp") params = { sub: 2, style: "up", steps: 16, vel: Array.from({ length: 16 }, (_, i) => (i % 4 === 0 ? 1 : i % 2 === 0 ? 0.78 : 0.62)), on: Array(16).fill(true) };
  else if (type === "octave") params = { blend: 60 };
  else if (type === "easy") params = { rows: [[24, [24, 48, 55]], [26, [26, 48, 53]], [28, [28, 48, 55]], [29, [29, 48, 53, 55]], [31, [31, 47, 55]]].map(([i, o]) => ({ in: i, out: o })) };
  else if (type === "seq") params = { sub: 2, vel: 100, steps: 16, rows: [[24, 55], [26, 53], [28, 55], [29, 53]].map(([i, top]) => ({ in: i, out: [48, top, 48, 48, top, 48, 48, top, 48, top, 48, 48, top, 48, 48, top] })) };
  else params = { keyPc: 0, scale: "major", sevenths: false, style: "block", sel: 1, chords: [[0, 2], [4, 2], [5, 2], [3, 2]].map(([d, len], i) => ({ id: i + 1, ...diaChord(0, "major", d, false), len, inv: 0, voicing: "close", oct: 0 })) };
  return { type, on: true, params };
};
const summary = (m) => {
  const p = m.params;
  if (m.type === "arp") return `${STYLES.find((s) => s[0] === p.style)[1]} · ${SUBS[p.sub][0]} · ${p.steps} steps`;
  if (m.type === "octave") return `Blend ${p.blend}%`;
  if (m.type === "easy") return `${p.rows.length} chord${p.rows.length > 1 ? "s" : ""} programmed`;
  if (m.type === "seq") return `${p.rows.length} sequences · ${p.steps} steps · ${SUBS[p.sub][0]}`;
  return `${p.chords.length} chords · ${NN[p.keyPc]} ${p.scale}`;
};

const CSS = `
.fm *{box-sizing:border-box}
.fm button{font-family:inherit}
@keyframes pageIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}
@keyframes winIn{from{opacity:0;transform:scale(.94) translateY(8px)}to{opacity:1;transform:scale(1) translateY(0)}}
@keyframes slideUp{from{opacity:0;transform:translateY(40px)}to{opacity:1;transform:translateY(0)}}
@keyframes fadeIn{from{opacity:0}to{opacity:1}}
@keyframes listen{0%,100%{box-shadow:0 0 0 2px rgba(199,217,249,.9)}50%{box-shadow:0 0 0 2px rgba(199,217,249,.25)}}
@keyframes toast{from{opacity:0;transform:translate(-50%,8px)}to{opacity:1;transform:translate(-50%,0)}}
.press{transition:transform .12s cubic-bezier(.3,1.5,.5,1),background .2s,opacity .2s,border-color .2s}
.press:active{transform:scale(.95)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

const P = {
  power: <><path d="M12 3v8" /><path d="M7.2 6.8a7 7 0 1 0 9.6 0" /></>,
  back: <path d="M19 12H5M11 6l-6 6 6 6" />, x: <path d="M6 6l12 12M18 6L6 18" />, plus: <path d="M12 5v14M5 12h14" />, minus: <path d="M5 12h14" />,
  trash: <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" />,
  help: <><circle cx="12" cy="12" r="9" /><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 1-1 1.7M12 17h.01" /></>,
  replace: <><path d="M20 12a8 8 0 0 0-14-5.3L4 9" /><path d="M4 4v5h5" /><path d="M4 12a8 8 0 0 0 14 5.3L20 15" /><path d="M20 20v-5h-5" /></>,
  down: <path d="M6 9l6 6 6-6" />, go: <><circle cx="12" cy="12" r="9" /><path d="M8 12h8M12 8l4 4-4 4" /></>,
  play: <path d="M8 5.5l10 6.5-10 6.5z" fill="currentColor" stroke="none" />, pause: <path d="M8 6h3v12H8zM13 6h3v12h-3z" fill="currentColor" stroke="none" />,
  left: <path d="M15 6l-6 6 6 6" />, right: <path d="M9 6l6 6-6 6" />, copy: <><rect x="8" y="8" width="12" height="12" rx="2" /><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" /></>,
  up: <path d="M3 19h4v-4h4v-4h4V7h4" />, dn: <path d="M3 5h4v4h4v4h4v4h4" />, ud: <path d="M3 18l4-6 5-6 5 6 4 6" />, du: <path d="M3 6l4 6 5 6 5-6 4-6" />,
  rnd: <path d="M3 14l4-6 4 8 4-10 5 8" />, chd: <path d="M12 4v3M12 10.5v3M12 17v3" />,
};
const STYLE_ICO = { up: "up", down: "dn", updown: "ud", downup: "du", random: "rnd", chord: "chd" };
function Ico({ n, size = 16, sw = 2 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">{P[n]}</svg>;
}

const tile = (kind, extra) => ({
  borderRadius: 10, cursor: "pointer", display: "grid", placeItems: "center", fontSize: 12, fontWeight: 800, padding: 0,
  border: kind === "empty" ? "1px solid rgba(255,255,255,.05)" : "1px solid rgba(255,255,255,.16)",
  background: kind === "in" ? `linear-gradient(180deg,${IN1},${IN2})` : kind === "out" ? `linear-gradient(180deg,${OUT1},${OUT2})` : kind === "dark" ? "#2B3254" : EMPTY,
  color: kind === "in" ? "#1B2440" : kind === "dark" ? "#8791B5" : TXT, boxShadow: kind === "empty" ? "none" : "inset 0 1px 0 rgba(255,255,255,.14), 0 2px 4px rgba(0,0,0,.3)", ...extra,
});
const Btn = ({ children, onClick, style, w, h = 36 }) => (
  <button onClick={onClick} className="press" style={{ height: h, width: w, padding: w ? 0 : "0 14px", borderRadius: 11, border: `1px solid ${LINE}`, background: BTN, color: "#D5DCF2", fontSize: 12.5, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 7, whiteSpace: "nowrap", flex: "none", ...style }}>{children}</button>
);
const PowerBtn = ({ on, onChange, size = 38 }) => (
  <button onClick={(e) => { e.stopPropagation(); onChange(!on); }} className="press" style={{ width: size, height: size, borderRadius: 11, border: on ? "1px solid rgba(255,255,255,.35)" : `1px solid ${LINE}`, cursor: "pointer", flex: "none", display: "grid", placeItems: "center", background: on ? `linear-gradient(180deg,${IN1},${IN2})` : "#232842", color: on ? "#1B2440" : "#5B6590", boxShadow: on ? "0 0 14px rgba(157,185,239,.35)" : "none", transition: "all .2s" }}><Ico n="power" size={size * 0.5} sw={2.4} /></button>
);

function Knob({ value, min, max, onChange, label, display, size = 46 }) {
  const f = (value - min) / (max - min), ang = -135 + f * 270, drag = useRef(null);
  const p = (a, r) => { const t = ((a - 90) * Math.PI) / 180; return [22 + r * Math.cos(t), 22 + r * Math.sin(t)]; };
  const arc = (a1, a2, r) => { const [x1, y1] = p(a1, r), [x2, y2] = p(a2, r); return `M ${x1} ${y1} A ${r} ${r} 0 ${a2 - a1 > 180 ? 1 : 0} 1 ${x2} ${y2}`; };
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
      <svg width={size} height={size} viewBox="0 0 44 44" style={{ touchAction: "none", cursor: "ns-resize", flex: "none" }}
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); drag.current = { y: e.clientY, v: value }; }}
        onPointerMove={(e) => { if (drag.current) onChange(Math.round(clamp(drag.current.v + ((drag.current.y - e.clientY) / 120) * (max - min), min, max))); }}
        onPointerUp={() => { drag.current = null; }}>
        <circle cx="22" cy="22" r="17" fill="#141829" stroke="rgba(255,255,255,.08)" />
        <path d={arc(-135, 135, 19.5)} fill="none" stroke="rgba(255,255,255,.1)" strokeWidth="2.4" strokeLinecap="round" />
        {f > 0.01 && <path d={arc(-135, ang, 19.5)} fill="none" stroke={IN2} strokeWidth="2.4" strokeLinecap="round" />}
        <g transform={`rotate(${ang} 22 22)`}><rect x="21" y="8" width="2" height="8" rx="1" fill="#DCE8FF" /></g>
      </svg>
      <div style={{ lineHeight: 1.25 }}><div style={{ fontSize: 10.5, color: DIM, fontWeight: 600 }}>{label}</div><div style={{ fontSize: 13, fontWeight: 800, color: "#CFE0FF" }}>{display}</div></div>
    </div>
  );
}
const Stepper = ({ value, min, max, onChange }) => (
  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
    <span style={{ fontSize: 10.5, color: DIM, fontWeight: 600 }}>Steps</span>
    <div style={{ display: "flex", alignItems: "center", height: 40, borderRadius: 11, background: "#2A3050", border: `1px solid ${LINE}`, overflow: "hidden" }}>
      <button onClick={() => onChange(clamp(value - 1, min, max))} style={{ width: 40, height: 40, border: 0, background: "transparent", color: TXT, cursor: "pointer" }}><Ico n="minus" size={16} sw={2.6} /></button>
      <div style={{ width: 50, textAlign: "center", fontSize: 15, fontWeight: 800, borderLeft: `1px solid ${LINE}`, borderRight: `1px solid ${LINE}`, lineHeight: "40px" }}>{value}</div>
      <button onClick={() => onChange(clamp(value + 1, min, max))} style={{ width: 40, height: 40, border: 0, background: "transparent", color: TXT, cursor: "pointer" }}><Ico n="plus" size={16} sw={2.6} /></button>
    </div>
  </div>
);
function Seg({ options, value, onChange, w, h = 26, fs = 11.5 }) {
  const idx = Math.max(0, options.findIndex((o) => o[0] === value));
  return (
    <div style={{ position: "relative", display: "flex", padding: 2, borderRadius: 10, background: "#161A2C", width: w, flex: "none", border: `1px solid ${LINE}` }}>
      <div style={{ position: "absolute", top: 2, bottom: 2, left: 2, width: `calc((100% - 4px) / ${options.length})`, borderRadius: 8, background: "#5B6591", transform: `translateX(${idx * 100}%)`, transition: "transform .25s cubic-bezier(.3,1.3,.5,1)" }} />
      {options.map(([k, l]) => <button key={String(k)} onClick={() => onChange(k)} style={{ position: "relative", flex: 1, height: h, border: 0, background: "transparent", cursor: "pointer", fontSize: fs, fontWeight: 700, color: value === k ? "#fff" : "#8791B5", padding: 0, whiteSpace: "nowrap" }}>{l}</button>)}
    </div>
  );
}
const Dots = ({ cur, n = 16 }) => (
  <div style={{ display: "flex", gap: 4, height: 16, flex: "none" }}>
    {Array.from({ length: n }).map((_, i) => <div key={i} style={{ flex: 1, borderRadius: 5, background: cur === i ? IN2 : "#121626", color: cur === i ? "#1B2440" : "#4A5278", fontSize: 8.5, fontWeight: 800, display: "grid", placeItems: "center", transition: "background .06s" }}>{i + 1}</div>)}
  </div>
);

/* ═══════════════ editors ═══════════════ */

function ArpEditor({ p, set, cur }) {
  const setVel = (i, e) => { const r = e.currentTarget.getBoundingClientRect(); const v = clamp(1 - (e.clientY - r.top) / r.height, 0.08, 1); set((s) => ({ ...s, vel: s.vel.map((x, k) => (k === i ? v : x)) })); };
  return (
    <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 56, display: "flex", flexDirection: "column", gap: 8, animation: "pageIn .25s ease-out" }}>
      <div style={{ height: 58, flex: "none", borderRadius: 14, background: "rgba(255,255,255,.035)", border: `1px solid ${LINE}`, display: "flex", alignItems: "center", gap: 16, padding: "0 14px" }}>
        <Knob value={p.sub} min={0} max={3} onChange={(v) => set((s) => ({ ...s, sub: v }))} label="Subdivision" display={SUBS[p.sub][0]} />
        <div style={{ flex: 1, display: "flex", justifyContent: "center", gap: 14, alignItems: "center" }}>
          <span style={{ fontSize: 10.5, color: DIM, fontWeight: 600 }}>Style</span>
          <div style={{ display: "flex", gap: 2, padding: 3, borderRadius: 12, background: "#161A2C", border: `1px solid ${LINE}` }}>
            {STYLES.map(([k, l]) => (
              <button key={k} onClick={() => set((s) => ({ ...s, style: k }))} className="press" title={l} style={{ height: 38, minWidth: p.style === k ? 104 : 46, borderRadius: 9, border: 0, cursor: "pointer", background: p.style === k ? "#5B6591" : "transparent", color: p.style === k ? "#fff" : "#8791B5", display: "flex", alignItems: "center", justifyContent: "center", gap: 8, fontSize: 12.5, fontWeight: 700, transition: "all .2s" }}>
                <Ico n={STYLE_ICO[k]} size={17} />{p.style === k && l}
              </button>
            ))}
          </div>
        </div>
        <Stepper value={p.steps} min={1} max={16} onChange={(v) => set((s) => ({ ...s, steps: v }))} />
      </div>
      <div style={{ flex: 1, minHeight: 0, borderRadius: 14, background: "rgba(255,255,255,.03)", border: `1px solid ${LINE}`, padding: "10px 12px 8px", display: "flex", flexDirection: "column", gap: 6 }}>
        <div style={{ flex: 1, minHeight: 0, display: "flex", gap: 10 }}>
          <div style={{ width: 44, flex: "none", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "space-between", color: DIM, fontSize: 18, fontWeight: 700 }}>
            <span>+</span><span style={{ writingMode: "vertical-rl", transform: "rotate(180deg)", fontSize: 11, fontWeight: 600 }}>Velocity</span><span>−</span>
          </div>
          <div style={{ flex: 1, display: "grid", gridTemplateColumns: "repeat(16,1fr)", gap: 5, minWidth: 0 }}>
            {Array.from({ length: 16 }).map((_, i) => {
              const active = i < p.steps, eng = p.on[i];
              return (
                <div key={i} style={{ display: "flex", flexDirection: "column", gap: 6, minWidth: 0 }}>
                  <div onPointerDown={(e) => { if (!active) return; e.currentTarget.setPointerCapture(e.pointerId); setVel(i, e); }} onPointerMove={(e) => { if (active && e.buttons) setVel(i, e); }}
                    style={{ flex: 1, borderRadius: 9, background: active ? "#171B2E" : "rgba(255,255,255,.03)", position: "relative", overflow: "hidden", touchAction: "none", cursor: active ? "ns-resize" : "default", border: `1px solid ${active ? "rgba(255,255,255,.05)" : "transparent"}` }}>
                    {active && <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: `${p.vel[i] * 100}%`, borderRadius: 8, background: eng ? `linear-gradient(180deg,#6F79AE,#4D5788)` : "#323A5C", opacity: cur === i ? 1 : 0.92, filter: cur === i ? "brightness(1.35)" : "none", transition: "filter .06s" }}>
                      <div style={{ margin: "6px auto 0", width: 16, borderTop: "2px solid rgba(255,255,255,.35)", borderBottom: "2px solid rgba(255,255,255,.35)", height: 6 }} />
                    </div>}
                  </div>
                  <button onClick={() => active && set((s) => ({ ...s, on: s.on.map((x, k) => (k === i ? !x : x)) }))} className="press" style={{ ...tile(active ? (eng ? "out" : "dark") : "empty", { height: 36, width: "100%", fontSize: 13, cursor: active ? "pointer" : "default", opacity: active ? 1 : 0.4 }) }}>{i + 1}</button>
                </div>
              );
            })}
          </div>
        </div>
        <Dots cur={cur} />
      </div>
    </div>
  );
}

function RowsEditor({ p, set, kind, listen, setListen, cur, steps }) {
  const maxRows = kind === "seq" ? 8 : 99, cells = kind === "seq" ? 16 : 8;
  const addRow = () => set((s) => ({ ...s, rows: [...s.rows, { in: clamp(s.rows[s.rows.length - 1].in + 2, 24, 90), out: kind === "seq" ? Array(16).fill(48) : [48] }] }));
  return (
    <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 56, display: "flex", flexDirection: "column", gap: 8, animation: "pageIn .25s ease-out" }}>
      {kind === "seq" && (
        <div style={{ height: 58, flex: "none", borderRadius: 14, background: "rgba(255,255,255,.035)", border: `1px solid ${LINE}`, display: "flex", alignItems: "center", gap: 40, padding: "0 14px" }}>
          <Knob value={p.sub} min={0} max={3} onChange={(v) => set((s) => ({ ...s, sub: v }))} label="Subdivision" display={SUBS[p.sub][0]} />
          <Knob value={p.vel} min={10} max={100} onChange={(v) => set((s) => ({ ...s, vel: v }))} label="Velocity" display={p.vel + " %"} />
          <div style={{ flex: 1 }} />
          <Stepper value={p.steps} min={1} max={16} onChange={(v) => set((s) => ({ ...s, steps: v }))} />
        </div>
      )}
      <div className="noscroll" style={{ flex: 1, minHeight: 0, overflowY: "auto", borderRadius: 14, background: "rgba(255,255,255,.03)", border: `1px solid ${LINE}`, padding: "8px 14px", display: "flex", flexDirection: "column", gap: 6 }}>
        <div style={{ display: "flex", gap: 10, fontSize: 10, color: DIM, fontWeight: 600 }}><span style={{ width: 24 }} /><span style={{ width: 54 }}>Input</span><span>Output</span></div>
        {p.rows.map((r, i) => (
          <div key={i} style={{ display: "flex", alignItems: "center", gap: 10, flex: "none" }}>
            <span style={{ width: 24, fontSize: 11, color: DIM, fontWeight: 700 }}>{i + 1}.</span>
            <button onClick={() => setListen({ r: i, c: "in" })} style={{ ...tile("in", { width: 54, height: 44, animation: listen && listen.r === i && listen.c === "in" ? "listen 1s infinite" : "none" }) }}>{nm(r.in)}</button>
            <div style={{ flex: 1, display: "grid", gridTemplateColumns: `repeat(${cells},1fr)`, gap: 4, minWidth: 0 }}>
              {Array.from({ length: cells }).map((_, k) => {
                const m = r.out[k], on = kind !== "seq" || k < steps, ls = listen && listen.r === i && listen.c === k;
                return <button key={k} onClick={() => on && setListen({ r: i, c: k })} style={{ ...tile(m == null ? "empty" : "out", { height: 44, opacity: on ? 1 : 0.32, cursor: on ? "pointer" : "default", animation: ls ? "listen 1s infinite" : "none", outline: kind === "seq" && cur === k && on ? "2px solid rgba(199,217,249,.55)" : "none", outlineOffset: -2 }) }}>{m == null ? "" : nm(m)}</button>;
              })}
            </div>
            <button onClick={() => set((s) => ({ ...s, rows: s.rows.length > 1 ? s.rows.filter((_, k) => k !== i) : s.rows }))} className="press" style={{ width: 30, height: 36, border: 0, background: "transparent", color: "#6F85B5", cursor: "pointer", display: "grid", placeItems: "center" }}><Ico n="trash" size={18} /></button>
          </div>
        ))}
        {p.rows.length < maxRows && <Btn h={38} onClick={addRow} style={{ alignSelf: "flex-start", marginLeft: 34, background: "#1A1F33" }}><Ico n="plus" size={14} /> {kind === "seq" ? "Add Sequence (Up to 8)" : "Add Chord"}</Btn>}
        {listen && <div style={{ fontSize: 11.5, color: IN2, fontWeight: 700, paddingLeft: 34, animation: "fadeIn .2s" }}>Play a key on your keyboard to set this note</div>}
      </div>
      {kind === "seq" && <Dots cur={cur} />}
    </div>
  );
}

function OctaveEditor({ p, set }) {
  return (
    <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 56, display: "grid", placeItems: "center", animation: "pageIn .25s ease-out" }}>
      <div style={{ width: 440, borderRadius: 16, background: "rgba(255,255,255,.04)", border: `1px solid ${LINE}`, padding: 22, display: "flex", alignItems: "center", gap: 24 }}>
        <Knob value={p.blend} min={0} max={100} onChange={(v) => set((s) => ({ ...s, blend: v }))} label="Blend" display={p.blend + " %"} size={78} />
        <div style={{ fontSize: 12.5, color: "#A3ACCB", lineHeight: 1.55 }}>Adds an octave above every note you play. Blend sets the velocity of the added note.</div>
      </div>
    </div>
  );
}

function ChordEditor({ p, set, pos }) {
  const tlRef = useRef(null);
  const sel = p.chords.find((c) => c.id === p.sel);
  let acc = 0;
  const blocks = p.chords.map((ch) => { const b = { ch, start: acc }; acc += ch.len; return b; });
  const room = 8 - acc;
  const upd = (patch) => set((s) => ({ ...s, chords: s.chords.map((x) => (x.id === s.sel ? { ...x, ...patch } : x)) }));
  const place = (d) => {
    const dc = diaChord(p.keyPc, p.scale, d, p.sevenths);
    if (sel) return upd(dc);
    if (room < 1) return;
    const n = { id: Date.now(), ...dc, len: Math.min(2, room), inv: 0, voicing: "close", oct: 0 };
    set((s) => ({ ...s, chords: [...s.chords, n], sel: n.id }));
  };
  return (
    <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 56, display: "flex", flexDirection: "column", gap: 8, animation: "pageIn .25s ease-out" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, height: 34, flex: "none" }}>
        <Btn w={34} h={34} onClick={() => set((s) => ({ ...s, keyPc: (s.keyPc + 11) % 12 }))}><Ico n="left" size={14} /></Btn>
        <div style={{ width: 54, textAlign: "center", fontSize: 15, fontWeight: 800 }}><span style={{ fontSize: 9, color: DIM, marginRight: 5 }}>KEY</span>{NN[p.keyPc]}</div>
        <Btn w={34} h={34} onClick={() => set((s) => ({ ...s, keyPc: (s.keyPc + 1) % 12 }))}><Ico n="right" size={14} /></Btn>
        <Seg options={[["major", "Major"], ["minor", "Minor"]]} value={p.scale} onChange={(v) => set((s) => ({ ...s, scale: v }))} w={130} h={28} />
        <Btn h={34} onClick={() => set((s) => ({ ...s, sevenths: !s.sevenths }))} style={{ background: p.sevenths ? "#5B6591" : BTN }}>7ths</Btn>
        <div style={{ flex: 1 }} />
        <Seg options={[["block", "Block"], ["strum", "Strum"], ["arp", "Arp"]]} value={p.style} onChange={(v) => set((s) => ({ ...s, style: v }))} w={170} h={28} />
      </div>
      <div ref={tlRef} style={{ position: "relative", height: 84, flex: "none", borderRadius: 14, border: `1px solid ${LINE}`, overflow: "hidden", background: "repeating-linear-gradient(90deg, rgba(255,255,255,.05) 0 1px, transparent 1px 12.5%), rgba(255,255,255,.03)" }}>
        {blocks.map(({ ch, start }) => {
          const d = SCALES[p.scale].findIndex((iv) => (p.keyPc + iv) % 12 === ch.root), active = pos >= start && pos < start + ch.len;
          return (
            <div key={ch.id} onClick={() => set((s) => ({ ...s, sel: ch.id }))} style={{ position: "absolute", left: `calc(${(start / 8) * 100}% + 3px)`, width: `calc(${(ch.len / 8) * 100}% - 6px)`, top: 6, bottom: 6, ...tile("out", { display: "block", textAlign: "left" }), outline: p.sel === ch.id ? "2px solid #C7D9F9" : "none", outlineOffset: -2, overflow: "hidden", transition: "left .2s, width .2s" }}>
              <div style={{ padding: "6px 10px", lineHeight: 1.1 }}><div style={{ fontSize: 17, fontWeight: 900 }}>{d >= 0 ? ROMAN[p.scale][d] : "·"}</div><div style={{ fontSize: 12.5, fontWeight: 800, color: "#DCE6FF" }}>{chordName(ch)}</div><div style={{ fontSize: 9.5, color: "#B4BEE0", marginTop: 3 }}>{ch.len} bar{ch.len > 1 ? "s" : ""}</div></div>
              {active && <div style={{ position: "absolute", left: 0, bottom: 0, height: 3, width: `${((pos - start) / ch.len) * 100}%`, background: IN1 }} />}
              <div onPointerDown={(e) => { e.stopPropagation(); e.currentTarget.setPointerCapture(e.pointerId); const rect = tlRef.current.getBoundingClientRect(), max = ch.len + room; const mv = (ev) => { const nl = clamp(Math.round(((ev.clientX - rect.left) / rect.width) * 8) - start, 1, max); set((s) => ({ ...s, chords: s.chords.map((x) => (x.id === ch.id ? { ...x, len: nl } : x)) })); }; e.currentTarget.onpointermove = mv; e.currentTarget.onpointerup = () => { e.currentTarget.onpointermove = null; }; }}
                style={{ position: "absolute", right: 0, top: 0, bottom: 0, width: 18, cursor: "ew-resize", display: "grid", placeItems: "center", touchAction: "none" }}><span style={{ width: 3, height: 24, borderRadius: 2, background: "rgba(255,255,255,.4)" }} /></div>
            </div>
          );
        })}
        {room > 0 && <button onClick={() => place(0)} className="press" style={{ position: "absolute", left: `calc(${((8 - room) / 8) * 100}% + 3px)`, width: `calc(${(room / 8) * 100}% - 6px)`, top: 6, bottom: 6, borderRadius: 10, border: "1.5px dashed rgba(255,255,255,.15)", background: "transparent", color: DIM, fontSize: 12, fontWeight: 700, cursor: "pointer" }}>+ Add chord</button>}
        <div style={{ position: "absolute", top: 0, bottom: 0, left: `${(Math.max(pos, 0) / 8) * 100}%`, width: 2, background: IN1, opacity: pos >= 0 ? 0.9 : 0 }} />
      </div>
      <div style={{ display: "flex", gap: 6, height: 44, flex: "none" }}>
        {SCALES[p.scale].map((_, d) => { const dc = diaChord(p.keyPc, p.scale, d, p.sevenths); return <button key={d} onClick={() => place(d)} className="press" style={{ ...tile("out", { flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", lineHeight: 1.15 }) }}><span style={{ fontSize: 10, color: "#C9D5F5" }}>{ROMAN[p.scale][d]}</span><span style={{ fontSize: 13 }}>{NN[dc.root] + SUF[dc.q]}</span></button>; })}
      </div>
      <div style={{ flex: 1, minHeight: 0, borderRadius: 14, background: "rgba(255,255,255,.03)", border: `1px solid ${LINE}`, padding: 10, display: "flex", flexDirection: "column", gap: 8, opacity: sel ? 1 : 0.5, pointerEvents: sel ? "auto" : "none" }}>
        <div className="noscroll" style={{ display: "flex", gap: 5, overflowX: "auto" }}>{Object.keys(QUAL).map((q) => <button key={q} onClick={() => upd({ q })} style={{ ...tile(sel && sel.q === q ? "in" : "out", { height: 32, minWidth: 52, padding: "0 10px", flex: "none" }) }}>{q}</button>)}</div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Seg options={[[0, "Root"], [1, "1st"], [2, "2nd"]]} value={sel ? sel.inv : 0} onChange={(v) => upd({ inv: Math.min(v, QUAL[sel.q].length - 1) })} w={150} h={26} />
          <Seg options={[["close", "Close"], ["open", "Open"], ["drop2", "Drop 2"]]} value={sel ? sel.voicing : "close"} onChange={(v) => upd({ voicing: v })} w={170} h={26} />
          <div style={{ display: "flex", gap: 4, flex: 1, justifyContent: "center" }}>{(sel ? chordNotes(sel) : []).map((m, i) => <div key={i} style={{ ...tile("out", { width: 42, height: 32, cursor: "default" }) }}>{nm(m)}</div>)}</div>
          <Btn h={32} onClick={() => { if (sel && sel.len <= room) { const n = { ...sel, id: Date.now() }; set((s) => ({ ...s, chords: [...s.chords, n], sel: n.id })); } }}><Ico n="copy" size={14} /></Btn>
          <Btn h={32} onClick={() => set((s) => ({ ...s, chords: s.chords.filter((x) => x.id !== s.sel), sel: null }))} style={{ color: "#E58AA0" }}><Ico n="trash" size={14} /></Btn>
        </div>
      </div>
    </div>
  );
}

function Editor({ m, set, setOn, t, bpm, sink, onReplace, onDiscard, onConfirm, say, sim }) {
  const [listen, setListen] = useState(null);
  const p = m.params, name = MIDI_DEFS[m.type].name;
  const spb = m.type === "arp" || m.type === "seq" ? SUBS[p.sub][1] : 4;
  const cur = sim ? Math.floor((t * bpm) / 60 * spb) % (p.steps || 16) : -1;
  const pos = sim ? ((t * bpm) / 240) % 8 : -1;
  sink.current = (note) => {
    if (!listen) return;
    set((s) => ({ ...s, rows: s.rows.map((r, i) => (i !== listen.r ? r : listen.c === "in" ? { ...r, in: note } : { ...r, out: Object.assign([...r.out], { [listen.c]: note }) })) }));
    if (listen.c !== "in") setListen((l) => ({ ...l, c: l.c + 1 < (m.type === "seq" ? p.steps : 8) ? l.c + 1 : l.c }));
  };
  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 60, background: "linear-gradient(180deg,#131728,#1A1F33)", animation: "slideUp .3s cubic-bezier(.2,.9,.25,1)" }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.05, mixBlendMode: "overlay", pointerEvents: "none" }} />
      <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 54, background: "#0F1220", borderBottom: `1px solid ${LINE}`, display: "flex", alignItems: "center", gap: 8, padding: "0 12px" }}>
        <Btn onClick={onReplace}><Ico n="replace" size={15} /> Replace Effect</Btn>
        <Btn onClick={() => say("Presets list")}>Presets <Ico n="down" size={13} /></Btn>
        <Btn onClick={() => say("Help")}><Ico n="help" size={15} /> Help</Btn>
        <div style={{ flex: 1, display: "flex", justifyContent: "center", alignItems: "center", gap: 8, fontSize: 15, fontWeight: 800 }}><span style={{ width: 14, height: 14, borderRadius: 4, background: ORG }} />{name}</div>
        <span style={{ fontSize: 10.5, color: DIM, fontWeight: 600 }}>Effect {m.on ? "ON" : "OFF"}</span>
        <PowerBtn on={m.on} onChange={setOn} />
      </div>
      {m.type === "arp" && <ArpEditor p={p} set={set} cur={cur} />}
      {(m.type === "seq" || m.type === "easy") && <RowsEditor p={p} set={set} kind={m.type} listen={listen} setListen={setListen} cur={cur} steps={p.steps} />}
      {m.type === "octave" && <OctaveEditor p={p} set={set} />}
      {m.type === "chords" && <ChordEditor p={p} set={set} pos={pos} />}
      <div style={{ position: "absolute", left: 10, right: 10, bottom: 8, height: 40, display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 16 }}>
        <button onClick={onDiscard} style={{ border: 0, background: "transparent", color: "#B5BEDB", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>Discard Changes</button>
        <button onClick={onConfirm} className="press" style={{ height: 40, padding: "0 30px", borderRadius: 11, border: 0, background: `linear-gradient(180deg,${IN1},${IN2})`, color: "#1B2440", fontSize: 13.5, fontWeight: 900, cursor: "pointer" }}>Confirm</button>
      </div>
    </div>
  );
}

/* ═══════════════ FX window: one Add FX, catalog in the same window ═══════════════ */

function Card({ glyph, name, sub, on, onToggle, onOpen, onRemove }) {
  return (
    <div onClick={onOpen} className="press" style={{ height: 64, flex: "none", borderRadius: 14, background: "rgba(255,255,255,.04)", border: `1px solid ${LINE}`, display: "flex", alignItems: "center", gap: 12, padding: "0 12px", cursor: "pointer", opacity: on ? 1 : 0.65, animation: "pageIn .25s ease-out" }}>
      <span style={{ ...tile("out", { width: 40, height: 40, fontSize: 18, cursor: "default", flex: "none" }) }}>{glyph}</span>
      <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontSize: 14.5, fontWeight: 800 }}>{name}</div><div style={{ fontSize: 11, color: DIM, fontWeight: 600, marginTop: 2 }}>{sub}</div></div>
      <button onClick={(e) => { e.stopPropagation(); onRemove(); }} className="press" style={{ width: 32, height: 36, border: 0, background: "transparent", color: "#6F85B5", cursor: "pointer", display: "grid", placeItems: "center" }}><Ico n="trash" size={17} /></button>
      <PowerBtn on={on} onChange={onToggle} />
    </div>
  );
}

function FxWindow({ track, st, view, setView, onClose, onOpenMidi, onToggleMidi, onRemoveMidi, onToggleAudio, onRemoveAudio, onPick, say }) {
  const [cat, setCat] = useState("midi");
  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 50 }}>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(6,8,16,.66)", animation: "fadeIn .2s" }} />
      <div style={{ position: "absolute", left: 100, right: 100, top: 26, bottom: 24, borderRadius: 20, background: "linear-gradient(180deg,#1B2036,#161A2C)", border: `1px solid ${LINE}`, boxShadow: "0 24px 60px rgba(0,0,0,.6)", animation: "winIn .3s cubic-bezier(.2,.9,.25,1)", display: "flex", flexDirection: "column", overflow: "hidden" }}>
        <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.05, mixBlendMode: "overlay", pointerEvents: "none" }} />
        <div style={{ height: 58, flex: "none", display: "flex", alignItems: "center", gap: 12, padding: "0 14px", borderBottom: `1px solid ${LINE}`, position: "relative" }}>
          {view === "picker" ? <Btn w={36} h={36} onClick={() => setView("list")}><Ico n="back" size={16} /></Btn> : <span style={{ width: 4, height: 28, borderRadius: 2, background: track.color }} />}
          <div style={{ flex: 1, textAlign: view === "picker" ? "center" : "left" }}><div style={{ fontSize: 16, fontWeight: 900 }}>{view === "picker" ? "Select Effect" : `${track.name} · Effects`}</div></div>
          <Btn w={36} h={36} onClick={onClose}><Ico n="x" size={15} /></Btn>
        </div>
        {view === "list" ? (
          <div className="noscroll" style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: "12px 16px 16px", display: "flex", flexDirection: "column", gap: 8, position: "relative" }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: DIM, letterSpacing: 1.2 }}>MIDI EFFECT · 1 SLOT</div>
            {st.midi && <Card glyph={MIDI_DEFS[st.midi.type].glyph} name={MIDI_DEFS[st.midi.type].name} sub={summary(st.midi)} on={st.midi.on} onToggle={onToggleMidi} onOpen={onOpenMidi} onRemove={onRemoveMidi} />}
            {st.audio.length > 0 && <div style={{ fontSize: 10, fontWeight: 700, color: DIM, letterSpacing: 1.2, marginTop: 4 }}>AUDIO EFFECTS · UP TO 3</div>}
            {st.audio.map((a) => <Card key={a.id} glyph="≋" name={AUDIO_DEFS[a.type]} sub="Audio effect" on={a.on} onToggle={() => onToggleAudio(a.id)} onOpen={() => say("Audio effect editors are not part of this preview")} onRemove={() => onRemoveAudio(a.id)} />)}
            <button onClick={() => setView("picker")} className="press" style={{ height: !st.midi && !st.audio.length ? 120 : 54, flex: "none", borderRadius: 14, border: "1.5px dashed rgba(255,255,255,.16)", background: "transparent", color: IN2, fontSize: 14.5, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 8, marginTop: 4 }}><Ico n="plus" size={17} sw={2.4} /> Add FX</button>
          </div>
        ) : (
          <div style={{ flex: 1, minHeight: 0, display: "flex", animation: "pageIn .22s ease-out", position: "relative" }}>
            <div style={{ width: 150, flex: "none", padding: 10, display: "flex", flexDirection: "column", gap: 5, borderRight: `1px solid ${LINE}` }}>
              {[["midi", "MIDI"], ["audio", "Audio"]].map(([k, l]) => <button key={k} onClick={() => setCat(k)} className="press" style={{ ...tile(cat === k ? "out" : "empty", { height: 42, justifyContent: "start", padding: "0 14px", fontSize: 13.5 }), display: "flex", alignItems: "center" }}>{l}</button>)}
            </div>
            <div className="noscroll" key={cat} style={{ flex: 1, overflowY: "auto", padding: 12 }}>
              <div style={{ fontSize: 11, color: "#9AA4C8", lineHeight: 1.5, marginBottom: 10, display: "flex", gap: 8 }}><span style={{ color: IN2 }}><Ico n="help" size={15} /></span>{cat === "midi" ? "A track has one MIDI effect. Choosing another one replaces the current effect." : "A track can use up to three audio effects."}</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
                {(cat === "midi" ? Object.entries(MIDI_DEFS).map(([k, d]) => [k, d.name, d.desc]) : Object.entries(AUDIO_DEFS).map(([k, n]) => [k, n, "Audio effect"])).map(([k, n, desc]) => (
                  <button key={k} onClick={() => onPick(cat, k)} className="press" style={{ height: 58, borderRadius: 12, border: `1px solid ${LINE}`, background: "rgba(255,255,255,.045)", color: "#E7ECFB", cursor: "pointer", display: "flex", alignItems: "center", gap: 10, padding: "0 12px", textAlign: "left", animation: "pageIn .22s ease-out" }}>
                    <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontSize: 13.5, fontWeight: 800 }}>{n}</div><div style={{ fontSize: 10.5, color: DIM, marginTop: 2, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{desc}</div></div>
                    <span style={{ color: "#8FA5D8" }}><Ico n="go" size={22} /></span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* ═══════════════ main (simulated mixer) ═══════════════ */

const TR = [{ id: 1, name: "Piano", color: "#7F9BC7", vol: 0.75 }, { id: 2, name: "Bass", color: "#86A394", vol: 0.7 }, { id: 3, name: "Strings", color: "#9A86C2", vol: 0.6 }, { id: 4, name: "Drums", color: "#C9A15C", vol: 0.8 }];

export default function FxModesPreview() {
  const [fx, setFx] = useState(() => ({ 1: { midi: mkMidi("arp"), audio: [{ id: 1, type: "reverb", on: true }] }, 2: { midi: null, audio: [] }, 3: { midi: mkMidi("easy"), audio: [] }, 4: { midi: null, audio: [] } }));
  const [fxTrack, setFxTrack] = useState(null);
  const [view, setView] = useState("list");
  const [editing, setEditing] = useState(null);
  const [swing, setSwing] = useState(54);
  const [bpm] = useState(119);
  const [sim, setSim] = useState(false);
  const [t, setT] = useState(0);
  const [toast, setToast] = useState(null);
  const sink = useRef(null), live = useRef({});
  live.current = { sim };
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 150) / H, 1.5));
  useEffect(() => { const on = () => setVp({ w: window.innerWidth, h: window.innerHeight }); window.addEventListener("resize", on); return () => window.removeEventListener("resize", on); }, []);
  useEffect(() => {
    let raf, last = performance.now(), acc = 0;
    const loop = (ts) => { raf = requestAnimationFrame(loop); acc += (ts - last) / 1000; last = ts; if (!live.current.sim || acc < 0.025) return; const d = acc; acc = 0; setT((x) => x + d); };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);
  const say = (m) => { setToast({ m, k: Date.now() }); setTimeout(() => setToast(null), 1600); };
  const track = TR.find((x) => x.id === fxTrack), st = fxTrack ? fx[fxTrack] : null;
  const setTr = (tid, fn) => setFx((f) => ({ ...f, [tid]: fn(f[tid]) }));
  const ed = editing ? fx[editing.tid].midi : null;

  return (
    <div className="fm" style={{ minHeight: "100vh", background: "#080A12", color: TXT, fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B2030, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: "linear-gradient(180deg,#131728,#1A1F33)" }}>
          <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.05, mixBlendMode: "overlay", pointerEvents: "none" }} />
          <div style={{ position: "absolute", left: 12, right: 12, top: 8, height: 44, display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ fontSize: 16, fontWeight: 800 }}>Mixer</span>
            <span style={{ fontSize: 11, color: DIM, fontWeight: 600 }}>simulated · tap FX on a track</span>
            <div style={{ flex: 1 }} />
            <div style={{ height: 40, borderRadius: 12, background: BTN, border: `1px solid ${LINE}`, display: "flex", alignItems: "center", gap: 4, padding: "0 6px" }} title="Global setting, shared by every MIDI effect">
              <Btn w={28} h={28} onClick={() => setSwing((s) => clamp(s - 1, 50, 75))} style={{ borderRadius: 14, background: "#2F365A" }}><Ico n="minus" size={13} /></Btn>
              <div style={{ width: 82, textAlign: "center", fontSize: 12.5, fontWeight: 800 }}>Swing {swing}%</div>
              <Btn w={28} h={28} onClick={() => setSwing((s) => clamp(s + 1, 50, 75))} style={{ borderRadius: 14, background: "#2F365A" }}><Ico n="plus" size={13} /></Btn>
            </div>
          </div>
          <div style={{ position: "absolute", left: 12, right: 12, top: 62, bottom: 12, display: "flex", gap: 8 }}>
            {TR.map((tr) => {
              const n = (fx[tr.id].midi && fx[tr.id].midi.on ? 1 : 0) + fx[tr.id].audio.filter((a) => a.on).length;
              return (
                <div key={tr.id} style={{ flex: 1, borderRadius: 16, background: "rgba(255,255,255,.04)", border: `1px solid ${LINE}`, padding: "10px 10px 12px", display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
                  <div style={{ ...tile("out", { width: "100%", height: 28, cursor: "default", borderBottom: `3px solid ${tr.color}` }) }}>{tr.name}</div>
                  <div style={{ flex: 1, width: 30, borderRadius: 9, background: "#121626", position: "relative" }}><div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: `${tr.vol * 100}%`, borderRadius: 9, background: `linear-gradient(180deg,${OUT1},${OUT2})` }} /></div>
                  <button onClick={() => { setFxTrack(tr.id); setView("list"); }} className="press" style={{ ...tile(n ? "in" : "out", { width: "100%", height: 36, display: "flex", gap: 8, alignItems: "center", justifyContent: "center" }) }}>FX{n > 0 && <span style={{ minWidth: 18, height: 18, borderRadius: 9, background: "#1B2440", color: IN1, fontSize: 10.5, display: "grid", placeItems: "center", padding: "0 4px" }}>{n}</span>}</button>
                </div>
              );
            })}
          </div>

          {track && (
            <FxWindow track={track} st={st} view={view} setView={setView} onClose={() => setFxTrack(null)} say={say}
              onOpenMidi={() => setEditing({ tid: track.id, snap: JSON.parse(JSON.stringify(st.midi.params)) })}
              onToggleMidi={(v) => setTr(track.id, (s) => ({ ...s, midi: { ...s.midi, on: v } }))}
              onRemoveMidi={() => setTr(track.id, (s) => ({ ...s, midi: null }))}
              onToggleAudio={(id) => setTr(track.id, (s) => ({ ...s, audio: s.audio.map((a) => (a.id === id ? { ...a, on: !a.on } : a)) }))}
              onRemoveAudio={(id) => setTr(track.id, (s) => ({ ...s, audio: s.audio.filter((a) => a.id !== id) }))}
              onPick={(cat, type) => {
                if (cat === "midi") { const had = !!st.midi; setTr(track.id, (s) => ({ ...s, midi: mkMidi(type) })); setView("list"); say(had ? `Replaced by ${MIDI_DEFS[type].name}` : `${MIDI_DEFS[type].name} added`); }
                else if (st.audio.length >= 3) say("Up to 3 audio effects");
                else { setTr(track.id, (s) => ({ ...s, audio: [...s.audio, { id: Date.now(), type, on: true }] })); setView("list"); say(`${AUDIO_DEFS[type]} added`); }
              }} />
          )}
          {ed && (
            <Editor m={ed} sim={sim} t={t} bpm={bpm} sink={sink} say={say}
              set={(u) => setTr(editing.tid, (s) => ({ ...s, midi: { ...s.midi, params: typeof u === "function" ? u(s.midi.params) : u } }))}
              setOn={(v) => setTr(editing.tid, (s) => ({ ...s, midi: { ...s.midi, on: v } }))}
              onReplace={() => { setEditing(null); setView("picker"); }}
              onDiscard={() => { setTr(editing.tid, (s) => ({ ...s, midi: { ...s.midi, params: editing.snap } })); setEditing(null); }}
              onConfirm={() => { setEditing(null); say("Changes confirmed"); }} />
          )}
          {toast && <div key={toast.k} style={{ position: "absolute", left: "50%", bottom: 14, transform: "translateX(-50%)", padding: "8px 14px", borderRadius: 10, background: "#262C4A", border: `1px solid ${LINE}`, fontSize: 13, fontWeight: 700, animation: "toast .2s ease-out", zIndex: 90, whiteSpace: "nowrap" }}>{toast.m}</div>}
        </div>
      </div>

      <div style={{ marginTop: 12, display: "flex", flexWrap: "wrap", gap: 6, justifyContent: "center", alignItems: "center", maxWidth: 780 }}>
        <button onClick={() => setSim((s) => !s)} style={{ padding: "7px 14px", borderRadius: 999, border: `1px solid ${sim ? IN2 : "#2A3050"}`, background: sim ? "rgba(157,185,239,.16)" : "#12162A", color: "#C9D5F5", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Simulate playing: {sim ? "on" : "off"}</button>
        <span style={{ fontSize: 11.5, color: "#59647A", margin: "0 4px" }}>Simulated keyboard key:</span>
        {[["C3", 48], ["D3", 50], ["E3", 52], ["F3", 53], ["G3", 55], ["A3", 57], ["B3", 59], ["C4", 60]].map(([l, m]) => <button key={l} onClick={() => sink.current && sink.current(m)} style={{ padding: "6px 10px", borderRadius: 8, border: "1px solid #2A3050", background: "#12162A", color: "#C9D5F5", fontSize: 11.5, fontWeight: 700, cursor: "pointer" }}>{l}</button>)}
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 720, lineHeight: 1.55 }}>
        FX opens the track's effects (one MIDI slot, up to three audio) · "Add FX" opens the catalog in the same window; picking a MIDI effect replaces the current one · tap a card to edit it · in the Sequencer and Easy Chord, tap a tile then play a key (simulated by the buttons above) · the step count only enables the first N steps, the others stay dimmed · Swing is global
      </div>
    </div>
  );
}
