import { useState, useRef, useEffect } from "react";

/* ─────────────────────────────────────────────────────────────
   ARPEGGIATOR + CHORD SEQUENCER — landscape smartphone preview (891 × 411 dp)
   Flat, Bandlab-like design (same family as the Tracks page). No audio is produced:
   the playing step and the sounding notes are shown on the keyboard strip.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const AC = "#8FB2E8", OR = "#F2994A", RED = "#E5484D", AMBER = "#D8A24A";
const BG = "#12161D", PANEL = "#171C26", PANEL2 = "#1D2430", LINE = "rgba(255,255,255,.06)";
const DEG = ["#4C8DF6", "#9B6BF2", "#F2557A", "#34C38F", "#F2994A", "#2CC5C0", "#E5B94A"];
const NN = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const rgba = (h, a) => { const r = parseInt(h.slice(1, 3), 16), g = parseInt(h.slice(3, 5), 16), b = parseInt(h.slice(5, 7), 16); return `rgba(${r},${g},${b},${a})`; };
const rng = (seed) => { let s = seed >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); };
const nm = (m) => NN[m % 12] + (Math.floor(m / 12) - 1);

/* ── music logic ── */
const QUAL = { maj: [0, 4, 7], min: [0, 3, 7], "7": [0, 4, 7, 10], maj7: [0, 4, 7, 11], m7: [0, 3, 7, 10], sus2: [0, 2, 7], sus4: [0, 5, 7], dim: [0, 3, 6], add9: [0, 4, 7, 14] };
const SUF = { maj: "", min: "m", "7": "7", maj7: "maj7", m7: "m7", sus2: "sus2", sus4: "sus4", dim: "dim", add9: "add9" };
const SCALES = { major: [0, 2, 4, 5, 7, 9, 11], minor: [0, 2, 3, 5, 7, 8, 10] };
const DIA = { major: ["maj", "min", "min", "maj", "maj", "min", "dim"], minor: ["min", "dim", "maj", "min", "min", "maj", "maj"] };
const ROMAN = { major: ["I", "ii", "iii", "IV", "V", "vi", "vii°"], minor: ["i", "ii°", "III", "iv", "v", "VI", "VII"] };
const PRESETS = [["I–V–vi–IV", [[0, 2], [4, 2], [5, 2], [3, 2]]], ["vi–IV–I–V", [[5, 2], [3, 2], [0, 2], [4, 2]]], ["ii–V–I", [[1, 2], [4, 2], [0, 4]]], ["I–vi–IV–V", [[0, 2], [5, 2], [3, 2], [4, 2]]]];
const RATES = [["4", "1/4", 4], ["8", "1/8", 8], ["16", "1/16", 16], ["32", "1/32", 32], ["16t", "1/16T", 24]];
const PATTERNS = [["up", "Up"], ["down", "Down"], ["updown", "Up·Down"], ["downup", "Down·Up"], ["random", "Random"], ["played", "Played"], ["converge", "Converge"], ["chord", "Chord"]];

const diaChord = (keyPc, scale, d, sevenths) => {
  let q = DIA[scale][d];
  if (sevenths) q = q === "maj" ? (scale === "major" ? (d === 4 ? "7" : "maj7") : (d === 6 ? "7" : "maj7")) : q === "min" ? "m7" : q;
  return { root: (keyPc + SCALES[scale][d]) % 12, q };
};
const chordNotes = (c) => {
  let n = QUAL[c.q].map((i) => 48 + c.root + i + 12 * (c.oct || 0));
  for (let k = 0; k < c.inv; k++) n.push(n.shift() + 12);
  if (c.voicing === "open" && n.length > 2) n[1] += 12;
  if (c.voicing === "drop2" && n.length > 2) n[n.length - 2] -= 12;
  return n.sort((a, b) => a - b);
};
const chordName = (c) => { const n = chordNotes(c); return NN[c.root] + SUF[c.q] + (c.inv > 0 ? "/" + NN[n[0] % 12] : ""); };
function buildSeq(notes, pattern, octs) {
  if (!notes.length) return [];
  const base = [...notes].sort((a, b) => a - b), exp = [], played = [];
  for (let o = 0; o < octs; o++) { base.forEach((n) => exp.push(n + 12 * o)); notes.forEach((n) => played.push(n + 12 * o)); }
  const pp = (a) => (a.length < 2 ? a : [...a, ...a.slice(1, -1).reverse()]);
  switch (pattern) {
    case "up": return exp;
    case "down": return [...exp].reverse();
    case "updown": return pp(exp);
    case "downup": return pp([...exp].reverse());
    case "random": { const r = rng(7); return [...exp].sort(() => r() - 0.5); }
    case "played": return played;
    case "converge": { const out = []; let l = 0, h = exp.length - 1; while (l <= h) { out.push(exp[l++]); if (l <= h) out.push(exp[h--]); } return out; }
    default: return [exp];
  }
}
const asList = (o) => (o == null ? [] : Array.isArray(o) ? o : [o]);

const CSS = `
.ac *{box-sizing:border-box}
.ac button{font-family:inherit}
@keyframes pageIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}
@keyframes fadeIn{from{opacity:0}to{opacity:1}}
@keyframes pop{0%{transform:scale(.8)}60%{transform:scale(1.06)}100%{transform:scale(1)}}
@keyframes toast{from{opacity:0;transform:translate(-50%,8px)}to{opacity:1;transform:translate(-50%,0)}}
.press{transition:transform .12s cubic-bezier(.3,1.5,.5,1),background .2s,border-color .2s,opacity .2s,color .2s}
.press:active{transform:scale(.94)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

const P = {
  play: <path d="M8 5.5l10 6.5-10 6.5z" fill="currentColor" stroke="none" />,
  pause: <path d="M8 6h3v12H8zM13 6h3v12h-3z" fill="currentColor" stroke="none" />,
  back: <path d="M19 12H5M11 6l-6 6 6 6" />, plus: <path d="M12 5v14M5 12h14" />, minus: <path d="M5 12h14" />,
  left: <path d="M15 6l-6 6 6 6" />, right: <path d="M9 6l6 6-6 6" />,
  copy: <><rect x="8" y="8" width="12" height="12" rx="2" /><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" /></>,
  trash: <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" />,
  rand: <><path d="M4 7h4l8 10h4M4 17h4l2-2.5M14 9.5L16 7h4" /><path d="M18 5l2 2-2 2M18 15l2 2-2 2" /></>,
};
function Ico({ n, size = 16, sw = 2 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">{P[n]}</svg>;
}
function Seg({ options, value, onChange, w, h = 26, fs = 11.5 }) {
  const idx = Math.max(0, options.findIndex((o) => o[0] === value));
  return (
    <div style={{ position: "relative", display: "flex", padding: 2, borderRadius: 9, background: "#0E1218", width: w, flex: "none" }}>
      <div style={{ position: "absolute", top: 2, bottom: 2, left: 2, width: `calc((100% - 4px) / ${options.length})`, borderRadius: 7, background: "#2B3447", transform: `translateX(${idx * 100}%)`, transition: "transform .25s cubic-bezier(.3,1.3,.5,1)" }} />
      {options.map(([k, l]) => (
        <button key={String(k)} onClick={() => onChange(k)} style={{ position: "relative", flex: 1, height: h, border: 0, background: "transparent", cursor: "pointer", fontSize: fs, fontWeight: 700, color: value === k ? "#F1F4FA" : "#7B859A", padding: 0, whiteSpace: "nowrap", transition: "color .2s" }}>{l}</button>
      ))}
    </div>
  );
}
function Btn({ children, onClick, active, color, w, h = 30, style, disabled }) {
  return (
    <button onClick={onClick} disabled={disabled} className="press" style={{ width: w, minWidth: w, height: h, padding: w ? 0 : "0 12px", borderRadius: 10, border: `1px solid ${active ? rgba(color || AC, 0.55) : LINE}`, background: active ? rgba(color || AC, 0.2) : PANEL2, color: disabled ? "#434C61" : active ? (color || AC) : "#C9D2E2", cursor: disabled ? "default" : "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6, fontSize: 12, fontWeight: 700, whiteSpace: "nowrap", flex: "none", ...style }}>{children}</button>
  );
}
const Label = ({ children }) => <div style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: 1.3, color: "#566075", height: 14 }}>{children}</div>;
function Slide({ label, value, min, max, onChange, fmt }) {
  const ref = useRef(null);
  const f = (value - min) / (max - min);
  const set = (e) => { const r = ref.current.getBoundingClientRect(); onChange(Math.round(min + clamp((e.clientX - r.left) / r.width, 0, 1) * (max - min))); };
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, fontWeight: 700, color: "#9AA5BA" }}><span>{label}</span><span style={{ fontFamily: MONO, color: "#E9EDF5" }}>{fmt ? fmt(value) : value}</span></div>
      <div ref={ref} onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); set(e); }} onPointerMove={(e) => { if (e.buttons) set(e); }} style={{ position: "relative", height: 16, cursor: "pointer", touchAction: "none" }}>
        <div style={{ position: "absolute", left: 0, right: 0, top: 6, height: 4, borderRadius: 2, background: "#0E1218" }} />
        <div style={{ position: "absolute", left: 0, top: 6, height: 4, width: `${f * 100}%`, borderRadius: 2, background: AC }} />
        <div style={{ position: "absolute", top: 2, left: `${f * 100}%`, marginLeft: -6, width: 12, height: 12, borderRadius: 6, background: "#E3E8F2" }} />
      </div>
    </div>
  );
}

function MiniKeys({ from = 36, octaves = 3, held = [], lit = [], onToggle, h = 56 }) {
  const whites = [], blacks = [];
  for (let m = from; m < from + 12 * octaves; m++) ([1, 3, 6, 8, 10].includes(m % 12) ? blacks : whites).push(m);
  const Wn = whites.length;
  return (
    <div style={{ position: "relative", height: h, display: "flex", gap: 1 }}>
      {whites.map((m) => (
        <div key={m} onClick={() => onToggle && onToggle(m)} style={{ flex: 1, borderRadius: "0 0 4px 4px", background: lit.includes(m) ? OR : held.includes(m) ? AC : "#E4E8EF", cursor: onToggle ? "pointer" : "default", display: "flex", alignItems: "flex-end", justifyContent: "center", transition: "background .08s" }}>
          {m % 12 === 0 && <span style={{ fontFamily: MONO, fontSize: 8, fontWeight: 800, color: "#7B8396", marginBottom: 2 }}>{nm(m)}</span>}
        </div>
      ))}
      {blacks.map((m) => {
        const idx = whites.filter((x) => x < m).length;
        return <div key={m} onClick={() => onToggle && onToggle(m)} style={{ position: "absolute", left: `${((idx - 0.3) / Wn) * 100}%`, width: `${(0.6 / Wn) * 100}%`, top: 0, height: "62%", borderRadius: "0 0 3px 3px", background: lit.includes(m) ? OR : held.includes(m) ? AC : "#1A2130", cursor: onToggle ? "pointer" : "default", transition: "background .08s" }} />;
      })}
    </div>
  );
}

/* ═══════════════ ARPEGGIATOR PAGE ═══════════════ */

function ArpPage({ a, set, held, lit, stepIdx, toggleHeld, seq }) {
  const len = a.length;
  const setVel = (i, e) => { const r = e.currentTarget.getBoundingClientRect(); const v = clamp(1 - (e.clientY - r.top) / r.height, 0.05, 1); set((s) => ({ ...s, vel: s.vel.map((x, k) => (k === i ? v : x)), on: s.on.map((x, k) => (k === i ? true : x)) })); };
  return (
    <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 8, display: "flex", gap: 10, animation: "pageIn .25s ease-out" }}>
      <div className="noscroll" style={{ width: 270, flex: "none", borderRadius: 12, background: PANEL, padding: 12, display: "flex", flexDirection: "column", gap: 8, overflowY: "auto" }}>
        <Label>INPUT</Label>
        <Seg options={[["keys", "Keys"], ["chords", "Chord sequencer"]]} value={a.source} onChange={(v) => set((s) => ({ ...s, source: v }))} w={246} h={26} />
        <Label>PATTERN</Label>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 5 }}>
          {PATTERNS.map(([k, l]) => <Btn key={k} h={28} active={a.pattern === k} onClick={() => set((s) => ({ ...s, pattern: k }))} style={{ fontSize: 10.5, padding: 0, borderRadius: 8 }}>{l}</Btn>)}
        </div>
        <Label>RATE</Label>
        <Seg options={RATES.map(([k, l]) => [k, l])} value={a.rate} onChange={(v) => set((s) => ({ ...s, rate: v }))} w={246} h={26} fs={11} />
        <div style={{ display: "flex", gap: 10, alignItems: "flex-end" }}>
          <div><Label>OCTAVES</Label><Seg options={[1, 2, 3, 4].map((n) => [n, String(n)])} value={a.oct} onChange={(v) => set((s) => ({ ...s, oct: v }))} w={120} h={26} /></div>
          <div><Label>VELOCITY</Label><Seg options={[["played", "As played"], ["fixed", "Fixed"]]} value={a.velMode} onChange={(v) => set((s) => ({ ...s, velMode: v }))} w={118} h={26} fs={10.5} /></div>
        </div>
        <Slide label="Gate" value={a.gate} min={10} max={100} onChange={(v) => set((s) => ({ ...s, gate: v }))} fmt={(v) => v + "%"} />
        <Slide label="Swing" value={a.swing} min={50} max={75} onChange={(v) => set((s) => ({ ...s, swing: v }))} fmt={(v) => v + "%"} />
        <Btn h={30} active={a.hold} onClick={() => set((s) => ({ ...s, hold: !s.hold }))} style={{ borderRadius: 8 }}>{a.hold ? "Hold: on (notes latched)" : "Hold: off"}</Btn>
      </div>

      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 8 }}>
        <div style={{ borderRadius: 12, background: PANEL, padding: "8px 10px", display: "flex", flexDirection: "column", gap: 5 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, height: 16 }}>
            <Label>{a.source === "keys" ? "HELD NOTES · tap the keys" : "NOTES FROM THE CHORD SEQUENCER"}</Label>
            <div style={{ flex: 1 }} />
            <span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 800, color: AC }}>{[...held].sort((x, y) => x - y).map(nm).join("  ") || "—"}</span>
          </div>
          <MiniKeys from={36} octaves={3} held={held} lit={lit} onToggle={a.source === "keys" ? toggleHeld : null} h={52} />
        </div>
        <div style={{ flex: 1, minHeight: 0, borderRadius: 12, background: PANEL, padding: "8px 10px 10px", display: "flex", flexDirection: "column", gap: 6 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, height: 26 }}>
            <Label>STEPS</Label>
            <Seg options={[[8, "8"], [12, "12"], [16, "16"]]} value={len} onChange={(v) => set((s) => ({ ...s, length: v }))} w={110} h={22} fs={11} />
            <div style={{ flex: 1 }} />
            <Btn h={24} onClick={() => set((s) => ({ ...s, vel: s.vel.map(() => 0.3 + Math.random() * 0.7), on: s.on.map(() => Math.random() > 0.2) }))} style={{ borderRadius: 7, fontSize: 11 }}><Ico n="rand" size={12} /> Random</Btn>
            <Btn h={24} onClick={() => set((s) => ({ ...s, vel: s.vel.map((_, i) => (i % 4 === 0 ? 1 : 0.65)), on: s.on.map(() => true) }))} style={{ borderRadius: 7, fontSize: 11 }}>Reset</Btn>
          </div>
          <div style={{ flex: 1, minHeight: 0, display: "grid", gridTemplateColumns: `repeat(${len}, 1fr)`, gap: 4 }}>
            {Array.from({ length: len }).map((_, i) => {
              const cur = stepIdx >= 0 && stepIdx % len === i;
              const out = seq.length ? seq[i % seq.length] : null;
              const on = a.on[i];
              return (
                <div key={i} style={{ display: "flex", flexDirection: "column", gap: 3, borderRadius: 7, background: cur ? "rgba(255,255,255,.07)" : "transparent", padding: 2, transition: "background .08s" }}>
                  <div style={{ textAlign: "center", fontFamily: MONO, fontSize: 9, fontWeight: 800, color: cur ? AC : i % 4 === 0 ? "#8E99AE" : "#434C61" }}>{i + 1}</div>
                  <div style={{ textAlign: "center", fontFamily: MONO, fontSize: 9.5, fontWeight: 800, height: 12, color: on ? (cur ? OR : "#C9D2E2") : "#3A4458", whiteSpace: "nowrap", overflow: "hidden" }}>{out == null ? "" : Array.isArray(out) ? "ALL" : nm(out)}</div>
                  <div onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); setVel(i, e); }} onPointerMove={(e) => { if (e.buttons) setVel(i, e); }}
                    style={{ flex: 1, borderRadius: 6, background: "#0E1218", position: "relative", overflow: "hidden", touchAction: "none", cursor: "ns-resize" }}>
                    <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: `${a.vel[i] * 100}%`, background: on ? (cur ? OR : AC) : "#2B3447", transition: "background .08s" }} />
                  </div>
                  <button onClick={() => set((s) => ({ ...s, on: s.on.map((x, k) => (k === i ? !x : x)) }))} style={{ height: 20, borderRadius: 6, border: 0, cursor: "pointer", background: on ? rgba(AC, 0.25) : "#222A38", color: on ? AC : "#566075", fontSize: 9, fontWeight: 800, padding: 0 }}>{on ? "ON" : "OFF"}</button>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ CHORD SEQUENCER PAGE ═══════════════ */

function ChordPage({ c, set, litNotes, pos, loopBars, arpOn }) {
  const sel = c.chords.find((x) => x.id === c.sel);
  let acc = 0;
  const blocks = c.chords.map((ch) => { const b = { ch, start: acc }; acc += ch.len; return b; });
  const total = acc, room = 8 - total;
  const degOf = (ch) => SCALES[c.scale].findIndex((iv) => (c.keyPc + iv) % 12 === ch.root);
  const upd = (patch) => set((s) => ({ ...s, chords: s.chords.map((x) => (x.id === s.sel ? { ...x, ...patch } : x)) }));
  const place = (d) => {
    const dc = diaChord(c.keyPc, c.scale, d, c.sevenths);
    if (sel) { upd(dc); return; }
    if (room < 1) return;
    const n = { id: Date.now(), ...dc, len: Math.min(2, room), inv: 0, voicing: "close", oct: 0 };
    set((s) => ({ ...s, chords: [...s.chords, n], sel: n.id }));
  };
  const loadPreset = (arr) => set((s) => ({ ...s, chords: arr.map(([d, len], i) => ({ id: Date.now() + i, ...diaChord(s.keyPc, s.scale, d, s.sevenths), len, inv: 0, voicing: "close", oct: 0 })), sel: null }));
  const notes = sel ? chordNotes(sel) : [];
  return (
    <div style={{ position: "absolute", left: 10, right: 10, top: 62, bottom: 8, display: "flex", flexDirection: "column", gap: 8, animation: "pageIn .25s ease-out" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, height: 32 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 4, height: 32, padding: "0 4px", borderRadius: 9, background: PANEL2, border: `1px solid ${LINE}`, flex: "none" }}>
          <Btn w={24} h={24} onClick={() => set((s) => ({ ...s, keyPc: (s.keyPc + 11) % 12 }))} style={{ borderRadius: 7 }}><Ico n="left" size={13} /></Btn>
          <div style={{ width: 46, textAlign: "center", fontFamily: MONO, fontSize: 14, fontWeight: 800 }}><span style={{ fontSize: 8, color: "#7B859A", marginRight: 4 }}>KEY</span>{NN[c.keyPc]}</div>
          <Btn w={24} h={24} onClick={() => set((s) => ({ ...s, keyPc: (s.keyPc + 1) % 12 }))} style={{ borderRadius: 7 }}><Ico n="right" size={13} /></Btn>
        </div>
        <Seg options={[["major", "Major"], ["minor", "Minor"]]} value={c.scale} onChange={(v) => set((s) => ({ ...s, scale: v }))} w={124} h={28} />
        <Btn h={32} active={c.sevenths} onClick={() => set((s) => ({ ...s, sevenths: !s.sevenths }))} style={{ borderRadius: 9 }}>7ths</Btn>
        <div style={{ width: 1, height: 20, background: LINE }} />
        {PRESETS.map(([n, arr]) => <Btn key={n} h={32} onClick={() => loadPreset(arr)} style={{ borderRadius: 9, fontSize: 11 }}>{n}</Btn>)}
        <div style={{ flex: 1 }} />
        <Seg options={[["block", "Block"], ["strum", "Strum"], ["arp", "Arp"]]} value={c.style} onChange={(v) => set((s) => ({ ...s, style: v }))} w={150} h={28} />
      </div>

      <div style={{ position: "relative", height: 100, borderRadius: 12, background: PANEL, overflow: "hidden", background: `${PANEL} repeating-linear-gradient(90deg, rgba(255,255,255,.06) 0 1px, transparent 1px 12.5%)` }}>
        {blocks.map(({ ch, start }) => {
          const d = degOf(ch), col = d >= 0 ? DEG[d] : "#7B859A";
          const active = pos >= start && pos < start + ch.len;
          return (
            <div key={ch.id} onClick={() => set((s) => ({ ...s, sel: s.sel === ch.id ? null : ch.id }))}
              style={{ position: "absolute", left: `calc(${(start / 8) * 100}% + 2px)`, width: `calc(${(ch.len / 8) * 100}% - 4px)`, top: 6, bottom: 6, borderRadius: 9, background: col, cursor: "pointer", overflow: "hidden", outline: c.sel === ch.id ? "2px solid #fff" : "none", outlineOffset: -2, transition: "left .25s, width .25s" }}>
              <div style={{ position: "absolute", left: 9, top: 6, color: "rgba(10,14,22,.85)", lineHeight: 1.05 }}>
                <div style={{ fontSize: 20, fontWeight: 900 }}>{d >= 0 ? ROMAN[c.scale][d] : "·"}</div>
                <div style={{ fontSize: 13, fontWeight: 800 }}>{chordName(ch)}</div>
              </div>
              <div style={{ position: "absolute", left: 9, bottom: 7, fontFamily: MONO, fontSize: 9.5, fontWeight: 800, color: "rgba(10,14,22,.7)" }}>{ch.len} bar{ch.len > 1 ? "s" : ""}</div>
              {active && <div style={{ position: "absolute", left: 0, bottom: 0, height: 4, width: `${((pos - start) / ch.len) * 100}%`, background: "rgba(10,14,22,.55)" }} />}
            </div>
          );
        })}
        {room > 0 && (
          <button onClick={() => place(0)} className="press" style={{ position: "absolute", left: `calc(${(total / 8) * 100}% + 2px)`, width: `calc(${(room / 8) * 100}% - 4px)`, top: 6, bottom: 6, borderRadius: 9, border: "1.5px dashed rgba(255,255,255,.14)", background: "transparent", color: "#566075", fontSize: 12, fontWeight: 800, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6 }}><Ico n="plus" size={14} /> Add chord</button>
        )}
        <div style={{ position: "absolute", top: 0, bottom: 0, left: `${(pos / 8) * 100}%`, width: 2, background: "#fff", opacity: 0.9, pointerEvents: "none" }} />
      </div>

      <div style={{ display: "flex", gap: 6, height: 44 }}>
        {SCALES[c.scale].map((_, d) => {
          const dc = diaChord(c.keyPc, c.scale, d, c.sevenths);
          return (
            <button key={d} onClick={() => place(d)} className="press" style={{ flex: 1, borderRadius: 10, border: 0, background: PANEL2, cursor: "pointer", overflow: "hidden", display: "flex", flexDirection: "column", padding: 0 }}>
              <div style={{ height: 4, background: DEG[d] }} />
              <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", lineHeight: 1.1 }}>
                <span style={{ fontSize: 10, fontWeight: 800, color: DEG[d] }}>{ROMAN[c.scale][d]}</span>
                <span style={{ fontSize: 13, fontWeight: 800, color: "#E9EDF5" }}>{NN[dc.root] + SUF[dc.q]}</span>
              </div>
            </button>
          );
        })}
      </div>

      <div style={{ flex: 1, minHeight: 0, borderRadius: 12, background: PANEL, padding: "8px 10px", display: "flex", flexDirection: "column", gap: 6, opacity: sel ? 1 : 0.55, pointerEvents: sel ? "auto" : "none", transition: "opacity .2s" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, height: 28 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 3, flex: "none" }}>
            <Btn w={24} h={24} onClick={() => sel && upd({ root: (sel.root + 11) % 12 })} style={{ borderRadius: 7 }}><Ico n="left" size={12} /></Btn>
            <div style={{ width: 52, textAlign: "center", fontSize: 13, fontWeight: 800 }}>{sel ? chordName(sel) : "—"}</div>
            <Btn w={24} h={24} onClick={() => sel && upd({ root: (sel.root + 1) % 12 })} style={{ borderRadius: 7 }}><Ico n="right" size={12} /></Btn>
          </div>
          <div className="noscroll" style={{ flex: 1, display: "flex", gap: 4, overflowX: "auto" }}>
            {Object.keys(QUAL).map((q) => <Btn key={q} h={26} active={sel && sel.q === q} onClick={() => upd({ q })} style={{ borderRadius: 7, fontSize: 11, padding: "0 9px" }}>{q}</Btn>)}
          </div>
          <Seg options={[[1, "1 bar"], [2, "2 bars"], [4, "4 bars"]]} value={sel ? sel.len : 2} onChange={(v) => { if (v - sel.len <= room) upd({ len: v }); }} w={170} h={24} fs={10.5} />
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8, height: 28 }}>
          <Seg options={[[0, "Root"], [1, "1st inv"], [2, "2nd inv"]]} value={sel ? sel.inv : 0} onChange={(v) => upd({ inv: Math.min(v, QUAL[sel.q].length - 1) })} w={176} h={24} fs={10.5} />
          <Seg options={[["close", "Close"], ["open", "Open"], ["drop2", "Drop 2"]]} value={sel ? sel.voicing : "close"} onChange={(v) => upd({ voicing: v })} w={176} h={24} fs={10.5} />
          <div style={{ display: "flex", alignItems: "center", gap: 3, flex: "none" }}>
            <span style={{ fontSize: 9.5, fontWeight: 800, color: "#566075", letterSpacing: 1 }}>OCT</span>
            <Btn w={24} h={24} onClick={() => sel && upd({ oct: clamp((sel.oct || 0) - 1, -1, 1) })} style={{ borderRadius: 7 }}><Ico n="minus" size={12} /></Btn>
            <span style={{ width: 22, textAlign: "center", fontFamily: MONO, fontSize: 12, fontWeight: 800 }}>{sel ? sel.oct || 0 : 0}</span>
            <Btn w={24} h={24} onClick={() => sel && upd({ oct: clamp((sel.oct || 0) + 1, -1, 1) })} style={{ borderRadius: 7 }}><Ico n="plus" size={12} /></Btn>
          </div>
          <div style={{ flex: 1 }} />
          <span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 800, color: AC }}>{notes.map(nm).join("  ")}</span>
          <Btn h={26} onClick={() => { if (sel && sel.len <= room) { const n = { ...sel, id: Date.now() }; set((s) => ({ ...s, chords: [...s.chords, n], sel: n.id })); } }} style={{ borderRadius: 7 }}><Ico n="copy" size={12} /> Duplicate</Btn>
          <Btn h={26} color={RED} onClick={() => set((s) => ({ ...s, chords: s.chords.filter((x) => x.id !== s.sel), sel: null }))} style={{ borderRadius: 7 }}><Ico n="trash" size={12} /></Btn>
        </div>
        <div style={{ flex: 1, minHeight: 0, display: "flex", alignItems: "flex-end" }}>
          <div style={{ width: "100%" }}><MiniKeys from={48} octaves={3} held={[]} lit={litNotes} h={40} /></div>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ MAIN ═══════════════ */

export default function ArpChordPreview() {
  const [mode, setMode] = useState("arp");
  const [playing, setPlaying] = useState(false);
  const [pos, setPos] = useState(0);
  const [bpm, setBpm] = useState(119);
  const [toast, setToast] = useState(null);
  const [arp, setArp] = useState({ on: Array(16).fill(true), vel: Array.from({ length: 16 }, (_, i) => (i % 4 === 0 ? 1 : i % 2 === 0 ? 0.75 : 0.55)), pattern: "up", rate: "16", oct: 2, gate: 70, swing: 54, hold: false, velMode: "played", length: 16, source: "keys" });
  const [heldKeys, setHeldKeys] = useState([60, 64, 67]);
  const [ch, setCh] = useState({
    keyPc: 0, scale: "major", sevenths: false, style: "block", sel: null,
    chords: [[0, 2], [4, 2], [5, 2], [3, 2]].map(([d, len], i) => ({ id: i + 1, ...diaChord(0, "major", d, false), len, inv: 0, voicing: "close", oct: 0 })),
  });
  const live = useRef({}); live.current = { playing, bpm };
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 110) / H, 1.5));
  useEffect(() => { const on = () => setVp({ w: window.innerWidth, h: window.innerHeight }); window.addEventListener("resize", on); return () => window.removeEventListener("resize", on); }, []);
  const say = (t) => { setToast({ t, k: Date.now() }); setTimeout(() => setToast(null), 1500); };

  const total = ch.chords.reduce((a, c) => a + c.len, 0);
  const loopBars = Math.max(1, total || 8);
  const loopRef = useRef(8); loopRef.current = loopBars;
  useEffect(() => {
    let raf, last = performance.now(), acc = 0;
    const loop = (t) => {
      raf = requestAnimationFrame(loop);
      acc += (t - last) / 1000; last = t;
      if (!live.current.playing || acc < 0.025) return;
      const step = acc * (live.current.bpm / 240); acc = 0;
      setPos((p) => (p + step >= loopRef.current ? p + step - loopRef.current : p + step));
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);

  /* what sounds right now */
  let acc2 = 0, curChord = null, startOfCur = 0;
  for (const c of ch.chords) { if (pos >= acc2 && pos < acc2 + c.len) { curChord = c; startOfCur = acc2; } acc2 += c.len; }
  const selChord = ch.chords.find((c) => c.id === ch.sel);
  const chordForArp = (playing ? curChord : null) || selChord || ch.chords[0];
  const sourceNotes = arp.source === "chords" ? (chordForArp ? chordNotes(chordForArp) : []) : heldKeys;
  const spb = RATES.find((r) => r[0] === arp.rate)[2];
  const seq = buildSeq(sourceNotes, arp.pattern, arp.oct);
  const stepCounter = playing ? Math.floor(pos * spb) : -1;
  const arpStep = stepCounter >= 0 ? stepCounter % arp.length : -1;
  const arpOut = stepCounter >= 0 && seq.length && arp.on[arpStep] ? asList(seq[stepCounter % seq.length]) : [];
  let chordLit = [];
  if (playing && curChord) {
    const cn = chordNotes(curChord), inCh = pos - startOfCur;
    if (ch.style === "block") chordLit = cn;
    else if (ch.style === "strum") chordLit = cn.filter((_, i) => inCh * 4 >= i * 0.125);
    else { const sq = buildSeq(cn, arp.pattern, arp.oct); const o = sq.length ? sq[Math.floor(pos * spb) % sq.length] : null; chordLit = asList(o); }
  }
  const toggleHeld = (m) => setHeldKeys((h) => (h.includes(m) ? h.filter((x) => x !== m) : [...h, m]));

  return (
    <div className="ac" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: BG }}>
          <div style={{ position: "absolute", left: 10, right: 10, top: 8, height: 44, display: "flex", alignItems: "center", gap: 8, zIndex: 5 }}>
            <Btn h={44} onClick={() => say("Back to Tracks")} style={{ borderRadius: 12, fontSize: 14, fontWeight: 800 }}><Ico n="back" size={16} /> Tracks</Btn>
            <Seg options={[["arp", "Arpeggiator"], ["chords", "Chord sequencer"]]} value={mode} onChange={setMode} w={280} h={36} fs={13} />
            <div style={{ flex: 1 }} />
            <Btn h={44} active={arp.on.some(Boolean) && arp.hold} onClick={() => say(mode === "arp" ? "Arpeggiator is active on this track" : "Style Arp uses the arpeggiator settings")} style={{ borderRadius: 12, display: "none" }}>ARP</Btn>
            <div style={{ height: 44, borderRadius: 12, padding: "0 6px", background: PANEL2, border: `1px solid ${LINE}`, display: "flex", alignItems: "center", gap: 4, flex: "none" }}>
              <Btn w={26} h={26} onClick={() => setBpm((b) => clamp(b - 1, 40, 240))} style={{ borderRadius: 13, background: "#262E3E" }}><Ico n="minus" size={13} /></Btn>
              <div style={{ textAlign: "center", width: 48, lineHeight: 1.05 }}><div style={{ fontFamily: MONO, fontSize: 16, fontWeight: 800 }}>{bpm}</div><div style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: 1, color: "#7B859A" }}>BPM</div></div>
              <Btn w={26} h={26} onClick={() => setBpm((b) => clamp(b + 1, 40, 240))} style={{ borderRadius: 13, background: "#262E3E" }}><Ico n="plus" size={13} /></Btn>
            </div>
            <Btn w={44} h={44} active={playing} color={AC} onClick={() => setPlaying((p) => !p)} style={{ borderRadius: 12 }}><Ico n={playing ? "pause" : "play"} size={19} /></Btn>
          </div>

          {mode === "arp"
            ? <ArpPage a={arp} set={setArp} held={sourceNotes} lit={arpOut} stepIdx={arpStep} toggleHeld={toggleHeld} seq={seq} />
            : <ChordPage c={ch} set={setCh} litNotes={chordLit} pos={playing ? pos : -1} loopBars={loopBars} arpOn={arp.on.some(Boolean)} />}

          {toast && <div key={toast.k} style={{ position: "absolute", left: "50%", bottom: 14, transform: "translateX(-50%)", padding: "8px 14px", borderRadius: 10, background: "#222C40", border: `1px solid ${LINE}`, fontSize: 13, fontWeight: 700, animation: "toast .2s ease-out", zIndex: 90, whiteSpace: "nowrap" }}>{toast.t}</div>}
        </div>
      </div>
      <div style={{ marginTop: 10, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 700, lineHeight: 1.55 }}>
        Arpeggiator: tap the keys to hold notes (or take them from the chord sequencer), choose a pattern, rate and octaves, drag the step bars for velocity · Chord sequencer: pick a key, tap a diatonic chord to add or replace it, edit quality, inversion and voicing, choose Block, Strum or Arp · press play to see the sounding notes light up
      </div>
    </div>
  );
}
