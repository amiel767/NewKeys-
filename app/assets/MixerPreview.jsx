import { useState, useRef, useEffect, useMemo } from "react";

/* ─────────────────────────────────────────────────────────────
   MIXER — landscape smartphone preview (891 × 411 dp)
   Everything inside the device frame is the real design, in English.
   Controls under the frame are preview helpers only.
   Sound is simulated: levels, chords and CPU values are not real.
   Unchanged by design: fader thumb and rail, FX / settings / on-off buttons.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const rgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const rgba = (h, a) => { const [r, g, b] = rgb(h); return `rgba(${r},${g},${b},${a})`; };

const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2'/></filter><rect width='100%' height='100%' filter='url(#n)' opacity='.55'/></svg>")}")`;

/* ── notation: letters A–G, ♯ or ♭ ── */
const FLAT = { "C#": "D♭", "D#": "E♭", "F#": "G♭", "G#": "A♭", "A#": "B♭" };
const note = (n, flats) => (flats && FLAT[n] ? FLAT[n] : n.replace("#", "♯"));
const name = (s, flats) => s.replace(/([A-G]#?)/g, (m) => note(m, flats));

const CHORDS = [
  { root: "A", q: "m7", bass: null, notes: ["A", "C", "E", "G"], alts: ["C6"], inv: "Root position" },
  { root: "C", q: "maj7", bass: null, notes: ["C", "E", "G", "B"], alts: ["Em/C"], inv: "Root position" },
  { root: "G", q: "7", bass: "B", notes: ["B", "D", "F", "G"], alts: ["B°/G"], inv: "1st inversion" },
  { root: "D", q: "m9", bass: null, notes: ["D", "F", "A", "C", "E"], alts: ["Fmaj7/D"], inv: "Root position" },
  { root: "F#", q: "°7", bass: null, notes: ["F#", "A", "C", "D#"], alts: ["A°7", "C°7", "D#°7"], inv: "Root position" },
  { root: "D", q: "7", bass: null, notes: ["D", "F#", "A", "C"], alts: ["F#°/D"], inv: "Root position" },
  { root: "C", q: "sus2", bass: null, notes: ["C", "D", "G"], alts: ["Gsus4/C"], inv: "Root position" },
];

/* channel colors as in the current app */
const CH0 = [
  { name: "Piano", color: "#E08A2E", v: 0.82, pan: -0.2, solo: false, mute: false, power: true },
  { name: "Motif XF Galaxy DX", color: "#2FB37A", v: 0.58, pan: 0, solo: false, mute: false, power: true },
  { name: "Analog Warm Pad", color: "#3C7BE0", v: 0.3, pan: 0.35, solo: false, mute: false, power: true },
  { name: "Accordeon", color: "#8A4FD8", v: 0.55, pan: 0, solo: false, mute: false, power: true },
];

const CSS = `
.mx *{box-sizing:border-box}
.mx button{font-family:inherit}
@keyframes roll{from{opacity:0;translate:0 9px}to{opacity:1;translate:0 0}}
@keyframes fadeUp{from{opacity:0;translate:0 6px}to{opacity:1;translate:0 0}}
@keyframes chipIn{from{opacity:0;scale:.7}to{opacity:1;scale:1}}
@keyframes flash{0%{background:rgba(194,88,122,.75)}100%{background:rgba(194,88,122,0)}}
@keyframes toast{from{opacity:0;translate:0 8px}to{opacity:1;translate:0 0}}
.press{transition:transform .14s cubic-bezier(.3,1.5,.5,1),background .25s,box-shadow .25s,border-color .25s,opacity .25s,color .25s}
.press:active{transform:scale(.96)}
`;

function Roll({ value }) {
  const prev = useRef(value);
  const [k, setK] = useState(0);
  useEffect(() => { if (prev.current !== value) { prev.current = value; setK((a) => a + 1); } }, [value]);
  return <span key={k} style={{ display: "inline-block", animation: k ? "roll .22s ease-out" : "none" }}>{value}</span>;
}

/* ── tiny drawn icons ── */
function Ico({ n, size = 16, color = "currentColor", sw = 2 }) {
  const P = {
    minus: <path d="M6 12h12" />,
    plus: <path d="M12 6v12M6 12h12" />,
    gear: <><circle cx="12" cy="12" r="3" /><path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7" /></>,
    feather: <><path d="M20.2 12.2a6 6 0 0 0-8.5-8.5L5 10.5V19h8.5z" /><path d="M16 8L2 22M17.5 15H9" /></>,
    patches: <><rect x="3" y="4" width="18" height="12" rx="3" /><path d="M7 9h10M7 12.5h6M8 20l3-4" /></>,
    panic: <><circle cx="12" cy="12" r="8.5" /><path d="M6 6l12 12" /><circle cx="9" cy="14" r=".6" fill="currentColor" /><circle cx="14.5" cy="9.5" r=".6" fill="currentColor" /></>,
    power: <><path d="M12 3v8" /><path d="M7.2 6.8a7 7 0 1 0 9.6 0" /></>,
    sliders: <><path d="M4 8h16M4 16h16" /><circle cx="9" cy="8" r="2.2" fill="currentColor" /><circle cx="15" cy="16" r="2.2" fill="currentColor" /></>,
    save: <><path d="M5 4h11l3 3v13H5z" /><path d="M8 4v5h7V4M8 20v-6h8v6" /></>,
    keys: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M8 5v14M12 5v14M16 5v14" /></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">{P[n]}</svg>;
}

/* ═══════════════ top bar ═══════════════ */

function Module({ children, style, pal }) {
  return (
    <div style={{
      height: 44, borderRadius: 14, display: "flex", alignItems: "center", color: "#E3E8F2", position: "relative",
      background: `linear-gradient(180deg, ${pal.panelTop}, ${pal.panelBottom})`,
      boxShadow: "inset 0 1px 0 rgba(255,255,255,.09), 0 5px 12px rgba(0,0,0,.35)", border: "1px solid rgba(255,255,255,.05)", ...style,
    }}>{children}</div>
  );
}

function RoundStep({ n, onClick, pal }) {
  return (
    <button onClick={onClick} className="press" style={{ width: 30, height: 30, borderRadius: 15, border: 0, cursor: "pointer", color: "#E3E8F2", display: "grid", placeItems: "center", background: `linear-gradient(180deg, rgba(255,255,255,.1), rgba(255,255,255,.04))`, boxShadow: "inset 0 1px 0 rgba(255,255,255,.12), 0 2px 5px rgba(0,0,0,.35)" }}><Ico n={n} size={15} sw={2.4} /></button>
  );
}

function Stepper({ label, value, onChange, pal }) {
  return (
    <Module pal={pal} style={{ width: 118, padding: "0 7px", gap: 4, justifyContent: "space-between" }}>
      <RoundStep n="minus" onClick={() => onChange(-1)} pal={pal} />
      <div style={{ textAlign: "center", lineHeight: 1.05 }}>
        <div style={{ fontSize: 9, letterSpacing: 1.4, fontWeight: 800, color: "#7B859A" }}>{label}</div>
        <div style={{ fontFamily: MONO, fontSize: 17, fontWeight: 800, color: "#F1F4FA" }}><Roll value={value} /></div>
      </div>
      <RoundStep n="plus" onClick={() => onChange(1)} pal={pal} />
    </Module>
  );
}

function ChordZone({ chord, flats, pal }) {
  const idle = !chord;
  const mainLen = chord ? (note(chord.root, flats) + chord.q + (chord.bass ? "/" + note(chord.bass, flats) : "")).length : 3;
  const key = chord ? chord.root + chord.q + (chord.bass || "") : "idle";
  return (
    <Module pal={pal} style={{ flex: 1, minWidth: 0, padding: "0 12px", gap: 12, overflow: "hidden" }}>
      <div key={key} style={{ minWidth: 92, display: "flex", alignItems: "baseline", whiteSpace: "nowrap", animation: "roll .24s ease-out" }}>
        {idle ? (
          <span style={{ fontSize: 20, fontWeight: 800, color: "#4F596E", letterSpacing: 4 }}>– – –</span>
        ) : (
          <>
            <span style={{ fontSize: mainLen > 6 ? 24 : 28, fontWeight: 800, color: "#F4F6FB", lineHeight: 1 }}>{note(chord.root, flats)}</span>
            <span style={{ fontSize: 15, fontWeight: 800, color: "#C9D2E4", marginLeft: 1 }}>{chord.q}</span>
            {chord.bass && <span style={{ fontSize: 15, fontWeight: 800, color: "#8FB2E8", marginLeft: 3 }}>/{note(chord.bass, flats)}</span>}
          </>
        )}
      </div>
      <div style={{ width: 1, alignSelf: "stretch", margin: "9px 0", background: "rgba(255,255,255,.08)" }} />
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 3, justifyContent: "center" }}>
        {idle ? (
          <span style={{ fontSize: 11.5, color: "#566075", fontWeight: 600 }}>Waiting for notes…</span>
        ) : (
          <>
            <div key={key + "a"} style={{ display: "flex", alignItems: "center", gap: 5, height: 18, whiteSpace: "nowrap", animation: "fadeUp .26s ease-out" }}>
              <span style={{ fontSize: 8.5, letterSpacing: 1.2, fontWeight: 800, color: "#6F7A90" }}>ALSO</span>
              {chord.alts.map((a) => <span key={a} style={{ padding: "1px 7px", borderRadius: 7, background: "rgba(255,255,255,.08)", fontSize: 11.5, fontWeight: 800, color: "#E3E8F2" }}>{name(a, flats)}</span>)}
            </div>
            <div key={key + "n"} style={{ display: "flex", alignItems: "center", gap: 4, height: 18, whiteSpace: "nowrap" }}>
              {chord.notes.map((n, i) => (
                <span key={n} style={{ minWidth: 20, height: 18, padding: "0 4px", borderRadius: 6, display: "grid", placeItems: "center", background: "rgba(143,178,232,.16)", color: "#B9CDF2", fontSize: 11, fontWeight: 800, fontFamily: MONO, animation: `chipIn .22s ease-out ${i * 40}ms backwards` }}>{note(n, flats)}</span>
              ))}
              <span style={{ marginLeft: 4, fontSize: 10, color: "#6F7A90", fontWeight: 700 }}>{chord.inv}</span>
            </div>
          </>
        )}
      </div>
    </Module>
  );
}

function IconBtn({ children, w = 44, onClick, active, style, pal, tint }) {
  return (
    <button onClick={onClick} className="press" style={{
      width: w, height: 44, borderRadius: 14, border: "1px solid rgba(255,255,255,.05)", cursor: "pointer", display: "grid", placeItems: "center", color: tint || "#C9D1E0", flex: "none",
      background: active ? "linear-gradient(180deg, rgba(143,178,232,.28), rgba(143,178,232,.14))" : `linear-gradient(180deg, ${pal.panelTop}, ${pal.panelBottom})`,
      boxShadow: "inset 0 1px 0 rgba(255,255,255,.09), 0 5px 12px rgba(0,0,0,.35)", ...style,
    }}>{children}</button>
  );
}

/* ═══════════════ channel strip ═══════════════ */

function PanKnob({ value, color, onChange, dim }) {
  const [drag, setDrag] = useState(null);
  const ang = value * 135;
  const p = (a, r) => { const t = ((a - 90) * Math.PI) / 180; return [16 + r * Math.cos(t), 16 + r * Math.sin(t)]; };
  const arc = (a1, a2, r) => { const [x1, y1] = p(a1, r), [x2, y2] = p(a2, r); return `M ${x1} ${y1} A ${r} ${r} 0 ${Math.abs(a2 - a1) > 180 ? 1 : 0} ${a2 >= a1 ? 1 : 0} ${x2} ${y2}`; };
  const id = useRef("p" + Math.random().toString(36).slice(2, 7)).current;
  const label = Math.abs(value) < 0.03 ? "C" : value < 0 ? `L ${Math.round(-value * 100)}` : `R ${Math.round(value * 100)}`;
  return (
    <div style={{ position: "relative", width: 30, height: 30, flex: "none", opacity: dim ? 0.55 : 1, transition: "opacity .3s" }}>
      <div
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); setDrag({ y: e.clientY, x: e.clientX, v: value }); }}
        onPointerMove={(e) => { if (drag) onChange(clamp(Math.round((drag.v + ((e.clientX - drag.x) - (e.clientY - drag.y)) / 90) * 100) / 100, -1, 1)); }}
        onPointerUp={() => setDrag(null)}
        onDoubleClick={() => onChange(0)}
        style={{ position: "absolute", inset: 0, cursor: "grab", touchAction: "none" }}>
        <svg width="30" height="30" viewBox="0 0 32 32">
          <defs><radialGradient id={id} cx="38%" cy="28%" r="78%"><stop offset="0" stopColor="#5D6788" /><stop offset=".6" stopColor="#2B3450" /><stop offset="1" stopColor="#151B2A" /></radialGradient></defs>
          <path d={arc(-135, 135, 14)} fill="none" stroke="rgba(255,255,255,.09)" strokeWidth="2" strokeLinecap="round" />
          {Math.abs(ang) > 1 && <path d={arc(0, ang, 14)} fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round" />}
          <circle cx="16" cy="17.2" r="10" fill="rgba(0,0,0,.5)" />
          <circle cx="16" cy="16" r="9.6" fill={`url(#${id})`} stroke="rgba(255,255,255,.14)" />
          <ellipse cx="13" cy="11.6" rx="4.8" ry="2.4" fill="rgba(255,255,255,.1)" />
          <g transform={`rotate(${ang} 16 16)`}><rect x="15.1" y="6.6" width="1.8" height="4.6" rx=".9" fill={color} /></g>
        </svg>
      </div>
      {drag && <div style={{ position: "absolute", left: "50%", top: -26, translate: "-50% 0", padding: "3px 8px", borderRadius: 8, background: "#2B3650", border: "1px solid rgba(255,255,255,.16)", fontFamily: MONO, fontSize: 11, fontWeight: 800, color: "#F1F4FA", whiteSpace: "nowrap", zIndex: 5 }}>{label}</div>}
    </div>
  );
}

function KeyBtn({ label, on, kind, onClick }) {
  const act = on ? (kind === "mute" ? { bg: "linear-gradient(180deg,#6A2E41,#4C2231)", fg: "#F2B0C0", glow: "rgba(194,88,122,.55)" } : { bg: "linear-gradient(180deg,#6B5524,#4D3C19)", fg: "#F4D79A", glow: "rgba(216,162,74,.55)" }) : null;
  return (
    <button onClick={onClick} className="press" style={{
      flex: 1, height: 28, borderRadius: 9, border: 0, cursor: "pointer", position: "relative", fontSize: 13, fontWeight: 800,
      color: act ? act.fg : "#B8C2D6", background: act ? act.bg : "linear-gradient(180deg,#3B4560,#2D364D)",
      boxShadow: act ? `inset 0 1px 0 rgba(255,255,255,.18), 0 0 12px ${act.glow}` : "inset 0 1px 0 rgba(255,255,255,.14), 0 3px 6px rgba(0,0,0,.35)",
    }}>{label}</button>
  );
}

function Strip({ c, level, dim, onChange, onHit, pal }) {
  const railTop = 80, railH = 170, thumbH = 32;
  const thumbTop = railTop + (1 - c.v) * (railH - thumbH);
  const dragRef = useRef(null);
  const moveTo = (e) => {
    const r = dragRef.current.getBoundingClientRect();
    const sc = r.height / railH;
    const y = (e.clientY - r.top) / sc - thumbH / 2;
    onChange({ v: clamp(1 - y / (railH - thumbH), 0, 1) });
  };
  const L = dim ? 0 : level;
  const base = dim ? 0.05 : 0.17;
  const A = base + L * 0.42;
  const col = c.color;
  return (
    <div onPointerDown={(e) => { if (!e.target.closest("[data-ctl]")) onHit(); }}
      style={{
        position: "relative", width: 126, height: 284, flex: "none", borderRadius: 18, overflow: "hidden", userSelect: "none",
        background: `linear-gradient(180deg, ${pal.panelTop} 0%, ${pal.surfaceBase} 55%)`,
        border: "1px solid rgba(255,255,255,.06)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.08), 0 8px 18px rgba(0,0,0,.38)",
      }}>
      {/* aura: always there at rest, it rises and brightens with the level of the sound */}
      <div style={{ position: "absolute", inset: 0, pointerEvents: "none", background: `radial-gradient(130% ${42 + L * 38}% at 50% 100%, ${rgba(col, A)}, transparent 72%), linear-gradient(0deg, ${rgba(col, A * 0.55)}, transparent ${34 + L * 24}%)`, transition: "background .08s linear" }} />
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 56, pointerEvents: "none", background: `radial-gradient(60% 100% at 50% 100%, ${rgba(col, 0.0 + L * 0.5)}, transparent 70%)`, mixBlendMode: "screen" }} />
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.05, mixBlendMode: "overlay", pointerEvents: "none" }} />

      {/* name plate */}
      <div data-ctl style={{
        position: "absolute", left: 6, right: 6, top: 7, height: 28, borderRadius: 10, display: "flex", alignItems: "center", gap: 6, padding: "0 9px",
        background: `linear-gradient(180deg, ${pal.panelTop}, ${pal.surfaceBase})`, border: "1px solid rgba(255,255,255,.07)",
        boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 3px 0 -1px rgba(0,0,0,.38), 0 9px 12px -6px rgba(0,0,0,.6)",
      }}>
        <span style={{ width: 5, height: 5, borderRadius: 3, flex: "none", background: col, opacity: dim ? 0.3 : 0.9, boxShadow: L > 0.12 ? `0 0 ${4 + L * 8}px ${col}` : "none", transition: "box-shadow .1s" }} />
        <span style={{ flex: 1, minWidth: 0, textAlign: "center", fontSize: c.name.length > 15 ? 10.5 : 11.5, fontWeight: 800, color: dim ? "#8F9AB0" : "#E9EDF5", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", textShadow: "0 1px 0 rgba(0,0,0,.45)" }}>{c.name}</span>
      </div>

      {/* pan + mute / solo */}
      <div data-ctl style={{ position: "absolute", left: 6, right: 6, top: 44, height: 30, display: "flex", alignItems: "center", gap: 5 }}>
        <PanKnob value={c.pan} color={col} onChange={(pan) => onChange({ pan })} dim={dim} />
        <KeyBtn label="S" on={c.solo} kind="solo" onClick={() => onChange({ solo: !c.solo })} />
        <KeyBtn label="M" on={c.mute} kind="mute" onClick={() => onChange({ mute: !c.mute })} />
      </div>

      {/* scale */}
      {[0.75, 0.5, 0.25].map((t) => <div key={t} style={{ position: "absolute", left: 6, top: railTop + (1 - t) * (railH - thumbH) + thumbH / 2, width: 22, height: 1, background: "rgba(255,255,255,.1)" }} />)}
      <div style={{ position: "absolute", right: 6, width: 38, top: railTop + 0.25 * (railH - thumbH) + thumbH / 2 - 14, textAlign: "right", fontSize: 10.5, color: "#7B859A", fontWeight: 700, borderBottom: "1px solid rgba(255,255,255,.1)", paddingBottom: 1 }}>0 dB</div>
      <div style={{ position: "absolute", right: 6, width: 30, top: railTop + railH - 22, textAlign: "right", fontSize: 10.5, color: "#7B859A", fontWeight: 700, borderBottom: "1px solid rgba(255,255,255,.1)", paddingBottom: 1 }}>−∞</div>

      {/* rail and thumb: unchanged look */}
      <div ref={dragRef} data-ctl style={{ position: "absolute", left: 51, width: 24, top: railTop, height: railH, borderRadius: 12, background: "linear-gradient(180deg,#2E3347,#3A3F57)", boxShadow: "inset 0 2px 4px rgba(0,0,0,.45)", opacity: c.power ? 1 : 0.55 }}>
        <div style={{ position: "absolute", left: 9, top: 7, bottom: 7, width: 6, borderRadius: 3, background: "linear-gradient(180deg,#14161F,#242836)" }} />
        <div style={{ position: "absolute", left: 9.5, width: 5, bottom: 7, top: thumbTop - railTop + thumbH / 2, borderRadius: 3, background: c.power ? col : "#4A5068", boxShadow: c.power ? `0 0 ${5 + L * 8}px ${rgba(col, 0.5)}` : "none" }} />
      </div>
      <div data-ctl
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); moveTo(e); e.currentTarget.dragging = true; }}
        onPointerMove={(e) => { if (e.currentTarget.dragging) moveTo(e); }}
        onPointerUp={(e) => { e.currentTarget.dragging = false; }}
        style={{ position: "absolute", left: 40, width: 46, top: thumbTop, height: thumbH, borderRadius: 7, cursor: "ns-resize", touchAction: "none", background: "linear-gradient(180deg,#6E7799 0%, #4B5373 14%, #3F4766 100%)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.28), 0 6px 10px rgba(0,0,0,.5)", border: "1px solid rgba(255,255,255,.1)" }}>
        <div style={{ position: "absolute", left: 8, right: 8, top: 15, height: 2.5, borderRadius: 2, background: col, opacity: 0.85 }} />
      </div>

      {/* FX · settings · on/off: unchanged */}
      <div data-ctl style={{ position: "absolute", left: 5, right: 5, bottom: 7, height: 24, display: "flex", gap: 4 }}>
        <button className="press" style={{ flex: 2, border: 0, borderRadius: 7, background: "linear-gradient(180deg,#3A4258,#2E3549)", color: "#D4DAE8", fontSize: 11.5, fontWeight: 800, cursor: "pointer" }}>FX</button>
        <button className="press" style={{ flex: 2, border: 0, borderRadius: 7, background: "linear-gradient(180deg,#3A4258,#2E3549)", color: "#D4DAE8", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="sliders" size={14} sw={2} /></button>
        <button onClick={() => onChange({ power: !c.power })} className="press" style={{ flex: 1.1, border: 0, borderRadius: 7, cursor: "pointer", display: "grid", placeItems: "center", color: c.power ? "#fff" : "#8F9AB0", background: c.power ? `linear-gradient(180deg, ${col}, ${rgba(col, 0.75)})` : "linear-gradient(180deg,#2B3144,#232838)", boxShadow: c.power ? `0 0 10px ${rgba(col, 0.4)}` : "none" }}><Ico n="power" size={13} sw={2.2} /></button>
      </div>
    </div>
  );
}

/* ═══════════════ bottom bar ═══════════════ */

function SnapCase({ label, num, active, onClick, pal, grow, maxW }) {
  const [ready, setReady] = useState(false);
  useEffect(() => { const t = setTimeout(() => setReady(true), 20); return () => clearTimeout(t); }, []);
  return (
    <button onClick={onClick} className="press" style={{
      flex: ready ? "1 1 0" : "0 1 0", minWidth: 0, maxWidth: ready ? maxW : 0, height: 48, borderRadius: 14, border: "1px solid rgba(255,255,255,.05)", cursor: "pointer", overflow: "hidden", padding: 0, opacity: ready ? 1 : 0,
      color: active ? "#0F1726" : "#DDE3EE",
      background: active ? "linear-gradient(180deg,#A6C5F1,#86AAE3)" : `linear-gradient(180deg, ${pal.panelTop}, ${pal.panelBottom})`,
      boxShadow: active ? "inset 0 1px 0 rgba(255,255,255,.45), 0 5px 12px rgba(0,0,0,.35)" : "inset 0 1px 0 rgba(255,255,255,.09), 0 5px 12px rgba(0,0,0,.35)",
      transition: "flex-grow .5s cubic-bezier(.2,.9,.25,1), max-width .5s cubic-bezier(.2,.9,.25,1), opacity .3s, background .3s, box-shadow .3s, color .3s",
      display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 1,
    }}>
      {label ? <span style={{ fontSize: 15, fontWeight: 800, letterSpacing: 0.4 }}>{label}</span> : (
        <>
          <span style={{ fontSize: 8.5, letterSpacing: 1.4, fontWeight: 800, opacity: active ? 0.7 : 0.55 }}>SNAPSHOT</span>
          <span style={{ fontSize: 18, fontWeight: 800, lineHeight: 1 }}>{num}</span>
        </>
      )}
    </button>
  );
}

const dbOf = (m) => (m <= 0.75 ? (m <= 0.001 ? -Infinity : -60 + (m / 0.75) * 60) : ((m - 0.75) / 0.25) * 6);
const dbText = (m) => { const d = dbOf(m); return d === -Infinity ? "−∞" : `${d > 0 ? "+" : d < 0 ? "−" : ""}${Math.abs(d).toFixed(1)}`; };

function MasterSlider({ value, onChange, pal }) {
  const ref = useRef(null);
  const set = (e) => { const r = ref.current.getBoundingClientRect(); onChange(clamp((e.clientX - r.left) / r.width, 0, 1)); };
  return (
    <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", marginBottom: 3 }}>
          <span style={{ fontSize: 9, letterSpacing: 1.4, fontWeight: 800, color: "#7B859A" }}>MASTER</span>
          <span style={{ fontFamily: MONO, fontSize: 11.5, fontWeight: 800, color: "#F1F4FA" }}>{dbText(value)}<span style={{ fontSize: 9, color: "#7B859A" }}> dB</span></span>
        </div>
        <div ref={ref} onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); set(e); e.currentTarget.dragging = true; }} onPointerMove={(e) => { if (e.currentTarget.dragging) set(e); }} onPointerUp={(e) => { e.currentTarget.dragging = false; }}
          style={{ position: "relative", height: 20, cursor: "ew-resize", touchAction: "none" }}>
          <div style={{ position: "absolute", left: 0, right: 0, top: 7, height: 6, borderRadius: 3, background: "#0E131C", boxShadow: "inset 0 1px 3px rgba(0,0,0,.6)" }} />
          <div style={{ position: "absolute", left: 0, top: 7, height: 6, width: `${value * 100}%`, borderRadius: 3, background: "linear-gradient(90deg, rgba(143,178,232,.45), #8FB2E8)" }} />
          <div style={{ position: "absolute", left: `${0.75 * 100}%`, top: 3, width: 1, height: 14, background: "rgba(255,255,255,.25)" }} />
          <div style={{ position: "absolute", top: 1, left: `${value * 100}%`, marginLeft: -9, width: 18, height: 18, borderRadius: 6, background: "linear-gradient(180deg,#6E7799,#3F4766)", border: "1px solid rgba(255,255,255,.14)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.3), 0 3px 6px rgba(0,0,0,.5)" }} />
        </div>
      </div>
      <button className="press" style={{ width: 30, height: 30, borderRadius: 10, border: 0, background: "linear-gradient(180deg,#3A4258,#2E3549)", color: "#D4DAE8", fontSize: 11, fontWeight: 800, cursor: "pointer", flex: "none" }}>FX</button>
    </div>
  );
}

/* ═══════════════ main ═══════════════ */

export default function MixerPreview() {
  const [hue, setHue] = useState(220);
  const [flats, setFlats] = useState(false);
  const [trans, setTrans] = useState(0);
  const [oct, setOct] = useState(0);
  const [sustain, setSustain] = useState(false);
  const [chans, setChans] = useState(CH0);
  const [sim, setSim] = useState(false);
  const [chordIdx, setChordIdx] = useState(null);
  const [snaps, setSnaps] = useState(1);              // snapshot cases besides Default (0 to 4)
  const [activeSnap, setActiveSnap] = useState(0);
  const [bpm, setBpm] = useState(120);
  const [master, setMaster] = useState(0.75);
  const [toast, setToast] = useState(null);
  const [panicFlash, setPanicFlash] = useState(0);
  const [, setTick] = useState(0);
  const lv = useRef([0, 0, 0, 0]);
  const hit = useRef([-9999, -9999, -9999, -9999]);
  const chRef = useRef(chans);
  chRef.current = chans;
  const simRef = useRef(sim);
  simRef.current = sim;
  const toastT = useRef(null);
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });

  useEffect(() => {
    const on = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 190) / H, 1.5));
  const portrait = vp.h > vp.w;

  /* palette from the hue (same formulas as AppPalette, at default tone and lightness) */
  const pal = useMemo(() => ({
    bgTop: `hsl(${hue - 3} 31% 12%)`, bgBottom: `hsl(${hue - 2} 30% 9.6%)`,
    panelTop: `hsl(${hue} 27% 19%)`, panelBottom: `hsl(${hue - 2} 29% 15%)`, surfaceBase: `hsl(${hue} 30% 14%)`,
  }), [hue]);

  const say = (t) => { clearTimeout(toastT.current); setToast({ t, k: Date.now() }); toastT.current = setTimeout(() => setToast(null), 1500); };

  /* simulated sound: levels follow an envelope; touching a strip body plays a note */
  useEffect(() => {
    let raf, last = 0;
    const loop = (ts) => {
      raf = requestAnimationFrame(loop);
      if (ts - last < 33) return;
      last = ts;
      const now = performance.now();
      const cs = chRef.current;
      const anySolo = cs.some((c) => c.solo);
      cs.forEach((c, i) => {
        let target = 0;
        if (simRef.current) {
          const t = now / 1000, p = [1.7, 2.3, 3.1, 2.0][i];
          const e = Math.max(0, Math.sin((t + i * 0.7) * ((Math.PI * 2) / p)));
          target = Math.pow(e, 1.6) * (0.55 + 0.45 * Math.abs(Math.sin(t * 1.9 + i)));
        }
        const since = now - hit.current[i];
        if (since < 1200) target = Math.max(target, Math.exp(-since / 380) * 0.95);
        target *= 0.35 + 0.65 * c.v;
        if (!c.power || c.mute || (anySolo && !c.solo)) target = 0;
        lv.current[i] += (target - lv.current[i]) * (target > lv.current[i] ? 0.35 : 0.07);
      });
      setTick((x) => (x + 1) % 100000);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);

  /* simulated chord detection */
  useEffect(() => {
    if (!sim) return;
    setChordIdx((i) => (i === null ? 0 : i));
    const id = setInterval(() => setChordIdx((i) => ((i === null ? -1 : i) + 1) % CHORDS.length), 2600);
    return () => clearInterval(id);
  }, [sim]);

  const upd = (i, patch) => setChans((cs) => cs.map((c, k) => (k === i ? { ...c, ...patch } : c)));
  const anySolo = chans.some((c) => c.solo);
  const chord = chordIdx === null ? null : CHORDS[chordIdx];
  const total = 1 + snaps;

  return (
    <div className="mx" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: `linear-gradient(180deg, ${pal.bgTop}, ${pal.bgBottom})`, transition: "background .3s" }}>
          {/* background: soft top light, a faint grain, a gentle vignette */}
          <div style={{ position: "absolute", inset: 0, background: "radial-gradient(80% 55% at 50% 0%, rgba(255,255,255,.045), transparent 70%), radial-gradient(120% 90% at 50% 100%, rgba(0,0,0,.28), transparent 65%)", pointerEvents: "none" }} />
          <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.045, mixBlendMode: "overlay", pointerEvents: "none" }} />
          {panicFlash > 0 && <div key={panicFlash} style={{ position: "absolute", inset: 0, pointerEvents: "none", animation: "flash .5s ease-out forwards", zIndex: 40 }} />}

          {/* top bar */}
          <div style={{ position: "absolute", left: 11, right: 11, top: 8, height: 44, display: "flex", gap: 7 }}>
            <Stepper label="TRANS" value={trans} onChange={(d) => setTrans((v) => clamp(v + d, -12, 12))} pal={pal} />
            <Stepper label="OCT" value={oct} onChange={(d) => setOct((v) => clamp(v + d, -3, 3))} pal={pal} />
            <ChordZone chord={chord} flats={flats} pal={pal} />
            <IconBtn w={44} pal={pal} onClick={() => { setPanicFlash((n) => n + 1); vib(14); say("All notes off"); }} tint="#fff" style={{ background: "linear-gradient(180deg,#D2587C,#B03F63)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.3), 0 5px 12px rgba(0,0,0,.35)" }}><Ico n="panic" size={22} sw={2.1} /></IconBtn>
            <button onClick={() => setSustain((s) => !s)} className="press" style={{ width: 84, height: 44, borderRadius: 14, border: "1px solid rgba(255,255,255,.05)", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 8, flex: "none", color: sustain ? "#F1F4FA" : "#9AA5BA", background: sustain ? "linear-gradient(180deg, rgba(143,178,232,.3), rgba(143,178,232,.15))" : `linear-gradient(180deg, ${pal.panelTop}, ${pal.panelBottom})`, boxShadow: "inset 0 1px 0 rgba(255,255,255,.09), 0 5px 12px rgba(0,0,0,.35)" }}>
              <span style={{ width: 8, height: 8, borderRadius: 4, background: sustain ? "#8FB2E8" : "#5A6379", boxShadow: sustain ? "0 0 8px #8FB2E8" : "none", transition: "background .25s, box-shadow .25s" }} />
              <span style={{ fontSize: 11.5, fontWeight: 800, letterSpacing: 1.2 }}>SUSTAIN</span>
            </button>
            <IconBtn pal={pal} w={44}><Ico n="feather" size={19} /></IconBtn>
            <IconBtn pal={pal} w={44}>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(3,7px)", gap: 3 }}>
                {["#E0588C", "#E0588C", "#A55BE0", "#E0588C", "#A55BE0", "#A55BE0", "#A55BE0", "#4F7FE6", "#4F7FE6"].map((c, i) => <span key={i} style={{ width: 7, height: 7, borderRadius: 2, background: c }} />)}
              </div>
            </IconBtn>
            <IconBtn pal={pal} w={44} tint="#8FB2E8"><Ico n="patches" size={20} /></IconBtn>
            <IconBtn pal={pal} w={44}><Ico n="gear" size={19} /></IconBtn>
          </div>

          {/* strips */}
          <div style={{ position: "absolute", left: 11, top: 60, display: "flex", gap: 6 }}>
            {chans.map((c, i) => (
              <Strip key={i} c={c} level={lv.current[i]} dim={!c.power || c.mute || (anySolo && !c.solo)} pal={pal}
                onChange={(p) => upd(i, p)} onHit={() => { hit.current[i] = performance.now(); }} />
            ))}
            <div className="press" onClick={() => say("Add sound")} style={{ width: 126, height: 284, flex: "none", borderRadius: 18, cursor: "pointer", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12, border: "1.5px dashed rgba(255,255,255,.14)", background: "rgba(255,255,255,.02)" }}>
              <span style={{ width: 44, height: 44, borderRadius: 14, display: "grid", placeItems: "center", color: "#8FB2E8", border: "1.5px solid rgba(143,178,232,.45)", background: "rgba(143,178,232,.08)" }}><Ico n="plus" size={22} sw={2.2} /></span>
              <span style={{ fontSize: 12.5, fontWeight: 800, color: "#8E99AD" }}>Add Sound</span>
            </div>
          </div>

          {/* bottom bar */}
          <div style={{ position: "absolute", left: 11, right: 11, bottom: 9, height: 48, display: "flex", gap: 6, alignItems: "stretch" }}>
            <div style={{ position: "absolute", left: 0, right: 0, top: -6, height: 1, background: "rgba(255,255,255,.06)" }} />
            <Module pal={pal} style={{ width: 94, height: 48, padding: "0 10px", flexDirection: "column", alignItems: "flex-start", justifyContent: "center", gap: 4, flex: "none" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 11.5, fontWeight: 800 }}><span style={{ width: 7, height: 7, borderRadius: 4, background: "#3CC48A" }} />CPU <span style={{ fontFamily: MONO }}>15%</span></div>
              <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 11.5, fontWeight: 800 }}><span style={{ width: 7, height: 7, borderRadius: 4, background: "#4BA8F0" }} />RAM <span style={{ fontFamily: MONO }}>325</span></div>
            </Module>
            <Module pal={pal} style={{ width: 92, height: 48, padding: "0 7px", justifyContent: "space-between", flex: "none" }}>
              <RoundStep n="minus" onClick={() => setBpm((b) => clamp(b - 1, 40, 240))} pal={pal} />
              <div style={{ textAlign: "center", lineHeight: 1.05 }}><div style={{ fontFamily: MONO, fontSize: 17, fontWeight: 800 }}><Roll value={bpm} /></div><div style={{ fontSize: 8.5, letterSpacing: 1.3, fontWeight: 800, color: "#7B859A" }}>BPM</div></div>
              <RoundStep n="plus" onClick={() => setBpm((b) => clamp(b + 1, 40, 240))} pal={pal} />
            </Module>

            {/* snapshot cases: 1 → 5, they share the room without squeezing the rest */}
            <div style={{ flex: 1, minWidth: 0, display: "flex", gap: 6, alignItems: "stretch" }}>
              <SnapCase key="def" label="Default" active={activeSnap === 0} onClick={() => setActiveSnap(0)} pal={pal} maxW={150} />
              {Array.from({ length: snaps }).map((_, i) => (
                <SnapCase key={"s" + (i + 1)} num={i + 1} active={activeSnap === i + 1} onClick={() => setActiveSnap(i + 1)} pal={pal} maxW={124} />
              ))}
              {total < 5 && (
                <button onClick={() => { setSnaps((s) => Math.min(4, s + 1)); vib(8); }} className="press" style={{ width: 44, height: 48, flex: "none", borderRadius: 14, border: "1px dashed rgba(255,255,255,.18)", background: "transparent", color: "#8E99AD", cursor: "pointer", display: "grid", placeItems: "center" }}><Ico n="plus" size={18} sw={2.3} /></button>
              )}
              <div style={{ flex: "0 1 0", minWidth: 0 }} />
            </div>

            <IconBtn pal={pal} w={46} style={{ height: 48 }} onClick={() => say("Snapshot saved")}><Ico n="save" size={19} /></IconBtn>
            <Module pal={pal} style={{ width: 178, height: 48, padding: "0 10px", flex: "none" }}>
              <div style={{ width: "100%" }}><MasterSlider value={master} onChange={setMaster} pal={pal} /></div>
            </Module>
            <IconBtn pal={pal} w={48} style={{ height: 48 }}><Ico n="keys" size={22} sw={1.8} /></IconBtn>
          </div>

          {toast && <div key={toast.k} style={{ position: "absolute", left: "50%", top: 62, transform: "translateX(-50%)", padding: "8px 14px", borderRadius: 12, background: "#222C40", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 10px 26px rgba(0,0,0,.5)", fontSize: 13, fontWeight: 600, animation: "toast .22s ease-out", zIndex: 50 }}>{toast.t}</div>}
        </div>
      </div>

      {/* preview helpers */}
      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", alignItems: "center", maxWidth: 780 }}>
        <button onClick={() => { const n = !sim; setSim(n); if (!n) setChordIdx(null); }} style={{ padding: "7px 14px", borderRadius: 999, border: `1px solid ${sim ? "#8FB2E8" : "#2A3446"}`, background: sim ? "rgba(143,178,232,.16)" : "#121823", color: sim ? "#B9CDF2" : "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>{sim ? "Stop simulation" : "Simulate playing"}</button>
        <button onClick={() => setFlats((f) => !f)} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Notation: {flats ? "♭ flats" : "♯ sharps"}</button>
        {CHORDS.map((c, i) => (
          <button key={i} onClick={() => { setSim(false); setChordIdx(i); }} style={{ padding: "7px 11px", borderRadius: 999, border: `1px solid ${chordIdx === i && !sim ? "#8FB2E8" : "#2A3446"}`, background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>{note(c.root, flats)}{c.q}{c.bass ? "/" + note(c.bass, flats) : ""}</button>
        ))}
        <button onClick={() => { setSim(false); setChordIdx(null); }} style={{ padding: "7px 11px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>No chord</button>
      </div>
      <div style={{ marginTop: 8, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", alignItems: "center" }}>
        <span style={{ fontSize: 12, color: "#7B859A" }}>Snapshot cases</span>
        {[1, 2, 3, 4, 5].map((n) => <button key={n} onClick={() => { setSnaps(n - 1); setActiveSnap((a) => Math.min(a, n - 1)); }} style={{ width: 34, height: 30, borderRadius: 10, border: `1px solid ${total === n ? "#8FB2E8" : "#2A3446"}`, background: total === n ? "rgba(143,178,232,.16)" : "#121823", color: "#C9D2E2", fontSize: 12.5, fontWeight: 800, cursor: "pointer" }}>{n}</button>)}
        <span style={{ fontSize: 12, color: "#7B859A", marginLeft: 8 }}>Hue</span>
        <input type="range" min="0" max="360" value={hue} onChange={(e) => setHue(+e.target.value)} style={{ width: 150, accentColor: "#8FB2E8" }} />
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 660, lineHeight: 1.55 }}>
        Touch the body of a strip to play a note: the aura at its base rises and brightens with the level, then settles back to its resting glow · mute, solo and power dim the aura · drag the pan knob (double tap resets) · fader thumb and rail are unchanged
        {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
      </div>
    </div>
  );
}
