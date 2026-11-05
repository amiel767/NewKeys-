import { useState, useRef, useEffect, useMemo } from "react";

/* ─────────────────────────────────────────────────────────────
   SHEETS — landscape smartphone preview (891 × 411 dp)

   Design reference for the Sheets module. Three surfaces, in the
   order the user meets them:

     1. Library  — the grid of sheet cards.
     2. Editor   — ribbon (40 %) + page (60 %).
     3. Floating — a card lifted OVER THE MIXER: no outline, the
        card's own colour, free to drag anywhere, showing one
        section or several, chosen explicitly.

   The floating card is the part being redesigned. Everything
   inside the device frame is the real design, in English.
   Controls under the frame are preview helpers only.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;

/* App chrome — mirrors AppColors.kt, the mixer's own palette. */
const APP = {
  bgTop: "#141B27", bgBottom: "#0F141D",
  ink: "#E9EDF5", soft: "#DCE2EE", mute: "#8690A4", dim: "#6F7A90",
  accent: "#8FB2E8", accentDeep: "#7FA3DD", accentText: "#0F1726",
  panelTop: "#232C3E", panelBottom: "#1A2230", well: "#0F141D",
};

/* Sheet colours — mirrors PalettePresets (the card is the sheet's identity). */
const SHEET_PAL = [
  "#7A5CFF", "#8B5CF6", "#A855F7", "#D946EF", "#E0508F", "#EC4899",
  "#F43F5E", "#EF4444", "#F08A3C", "#F97316", "#F59E0B", "#D9B13A",
  "#84CC16", "#22C55E", "#2FB58A", "#14B8A6", "#06B6D4", "#38BDF8",
  "#3F7BE8", "#6366F1",
];
const PAPER = "#F6EFDC";

/* Section tag colours — mirrors SectionKind.color(). */
const KIND = {
  Intro: "#7A5CFF", Verse: "#26A58A", "Pre-chorus": "#D97706", Chorus: "#E0508F",
  Bridge: "#3F7BE8", Solo: "#F08A3C", Outro: "#0F9E94", Tag: "#8B5CF6",
  Interlude: "#0891B2", Section: "#64748B",
};

const hexToRgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const rgba = (h, a) => { const [r, g, b] = hexToRgb(h); return `rgba(${r},${g},${b},${a})`; };
const lum = (h) => { const [r, g, b] = hexToRgb(h).map((v) => { const c = v / 255; return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; }); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
const inkOn = (h) => (lum(h) > 0.5 ? "#2B2A33" : "#FFFFFF");
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };

const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2'/></filter><rect width='100%' height='100%' filter='url(#n)' opacity='.55'/></svg>")}")`;

const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const SANS = "'Plus Jakarta Sans', ui-sans-serif, system-ui, -apple-system, sans-serif";
const HAND = "'Architects Daughter', 'Patrick Hand', cursive";

/* ── seed content ── */
const seedSheet = (id, title, color, key, bpm) => ({
  id, title, color, key, bpm, favorite: id === 1,
  updated: ["Today", "Yesterday", "Nov 1", "Oct 28"][id % 4],
  sections: [
    { kind: "Intro", name: "Intro", lines: [["C", "Am", "F", "G"]], lyric: "" },
    { kind: "Verse", name: "Verse", lines: [["C", "Am", "F", "G"], ["Am", "F", "C", "G"]], lyric: "Hold the line, the morning comes" },
    { kind: "Pre-chorus", name: "Pre-chorus", lines: [["Dm", "Am", "B♭", "F"]], lyric: "and the light is on your side" },
    { kind: "Chorus", name: "Chorus", lines: [["F", "C", "G", "Am"], ["F", "G", "C", "C"]], lyric: "We are the sound of the stage" },
    { kind: "Bridge", name: "Bridge", lines: [["Am", "G", "F", "F"]], lyric: "" },
    { kind: "Outro", name: "Outro", lines: [["C", "G", "C", "C"]], lyric: "" },
  ],
});

const SEED = [
  seedSheet(1, "Sunday Service", SHEET_PAL[0], "G", 74),
  seedSheet(2, "Acoustic Night", SHEET_PAL[8], "C", 92),
  seedSheet(3, "Warm Pads", SHEET_PAL[17], "F", 68),
  seedSheet(4, "Main Stage", SHEET_PAL[13], "D", 110),
  seedSheet(5, "Rehearsal", SHEET_PAL[4], "A", 84),
  seedSheet(6, "Blue Hour", SHEET_PAL[19], "B♭", 78),
];

/* ── icon set (inline SVG, 24-grid, stroke) ── */
function Ico({ n, size = 18, sw = 2 }) {
  const P = {
    mixer: <><path d="M4 6h16M4 12h16M4 18h16" /><circle cx="9" cy="6" r="2" fill="currentColor" stroke="none" /><circle cx="15" cy="12" r="2" fill="currentColor" stroke="none" /><circle cx="8" cy="18" r="2" fill="currentColor" stroke="none" /></>,
    search: <><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4 4" /></>,
    gear: <><circle cx="12" cy="12" r="3.2" /><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" /></>,
    plus: <><path d="M12 5v14M5 12h14" /></>,
    star: <><path d="M12 3.6l2.6 5.4 5.9.8-4.3 4.1 1.1 5.9L12 17l-5.3 2.8 1.1-5.9-4.3-4.1 5.9-.8z" /></>,
    lock: <><rect x="5" y="10.5" width="14" height="9.5" rx="2" /><path d="M8.5 10.5V8a3.5 3.5 0 017 0v2.5" /></>,
    unlock: <><rect x="5" y="10.5" width="14" height="9.5" rx="2" /><path d="M8.5 10.5V8a3.5 3.5 0 016.8-1.2" /></>,
    layers: <><path d="M12 3l8 4.5-8 4.5-8-4.5z" /><path d="M4 12l8 4.5 8-4.5M4 16.5L12 21l8-4.5" /></>,
    grid: <><rect x="4" y="4" width="7" height="7" rx="1.5" /><rect x="13" y="4" width="7" height="7" rx="1.5" /><rect x="4" y="13" width="7" height="7" rx="1.5" /><rect x="13" y="13" width="7" height="7" rx="1.5" /></>,
    back: <><path d="M15 5l-7 7 7 7" /></>,
    close: <><path d="M6 6l12 12M18 6L6 18" /></>,
    text: <><path d="M5 6h14M9 6v13M15 6v13" /></>,
    note: <><circle cx="7" cy="18" r="2.6" /><path d="M9.6 18V6l9-2v10" /><circle cx="16" cy="14" r="2.6" /></>,
  };
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ flex: "none" }}>{P[n] || null}</svg>
  );
}

/* ══════════════════════════════════════════════════════════════
   1. LIBRARY
   ══════════════════════════════════════════════════════════════ */
function SheetCard({ s, onOpen, onFloat, onMenu }) {
  const ink = inkOn(s.color);
  const first = s.sections.find((x) => x.lines.length);
  const preview = first.lines[0].join("   ");
  return (
    <div
      onDoubleClick={() => { vib(12); onFloat(s); }}
      onClick={() => { vib(6); onOpen(s); }}
      style={{
        position: "relative", height: 150, borderRadius: 16, overflow: "hidden", cursor: "pointer", userSelect: "none",
        background: `linear-gradient(180deg, ${s.color} 0%, ${rgba(s.color, 1)} 55%, ${shade(s.color, -0.35)} 100%)`,
        boxShadow: "0 10px 22px rgba(0,0,0,.38)", padding: "14px 18px",
        display: "flex", flexDirection: "column", justifyContent: "space-between",
      }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.06, mixBlendMode: "overlay", pointerEvents: "none" }} />
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ flex: 1, minWidth: 0, fontFamily: HAND, fontSize: 26, color: ink, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{s.title}</span>
        {s.favorite && <span style={{ color: "#F5C24B" }}><Ico n="star" size={16} sw={0} /></span>}
        <span style={{ padding: "5px 11px", borderRadius: 9, background: rgba(ink === "#FFFFFF" ? "#FFFFFF" : "#000000", 0.18), fontFamily: SANS, fontWeight: 700, fontSize: 13, color: ink }}>{s.key}</span>
      </div>
      <div style={{ fontFamily: HAND, fontSize: 28, color: ink, whiteSpace: "nowrap" }}>{preview}</div>
      <div style={{ display: "flex", justifyContent: "space-between", fontFamily: SANS, fontSize: 11, fontWeight: 600, color: rgba(ink === "#FFFFFF" ? "#FFFFFF" : "#000000", 0.75) }}>
        <span>{s.bpm} BPM · 4/4</span><span>{s.updated}</span>
      </div>
    </div>
  );
}
const shade = (h, t) => {
  const [r, g, b] = hexToRgb(h);
  const f = (v) => clamp(Math.round(t < 0 ? v * (1 + t) : v + (255 - v) * t), 0, 255);
  return `rgb(${f(r)},${f(g)},${f(b)})`;
};

function Library({ sheets, onOpen, onFloat, onMenu, notation, setNotation }) {
  return (
    <div style={{ position: "absolute", inset: 0, padding: "20px 28px", display: "flex", flexDirection: "column", gap: 14 }}>
      {/* header */}
      <div style={{ position: "relative", height: 44, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <Chip w={40} h={40} r={99} bg={APP.panelTop}><span style={{ color: APP.soft }}><Ico n="mixer" size={20} /></span></Chip>
          <div style={{ width: 220, height: 40, borderRadius: 99, background: APP.panelTop, display: "flex", alignItems: "center", padding: "0 16px", gap: 8, color: APP.mute }}>
            <Ico n="search" size={15} /><span style={{ fontFamily: SANS, fontSize: 14, fontWeight: 500 }}>Search</span>
          </div>
        </div>
        <div style={{ position: "absolute", left: "50%", transform: "translateX(-50%)", fontFamily: SANS, fontSize: 22, fontWeight: 800, color: "#fff" }}>Sheets</div>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <Seg a="1-7" b="A-G" sel={notation === "nashville" ? 0 : 1} onSel={(i) => setNotation(i === 0 ? "nashville" : "standard")} w={132} />
          <Chip w={40} h={40} r={99} bg={APP.panelTop}><span style={{ color: APP.soft }}><Ico n="gear" size={18} /></span></Chip>
        </div>
      </div>
      {/* setlist filter row */}
      <div style={{ display: "flex", gap: 8 }}>
        <Pill label={`All · ${sheets.length}`} on />
        <Pill label="★ Favorites" />
        <Pill label="Main Stage" />
        <Pill label="New setlist" />
      </div>
      {/* grid */}
      <div style={{ flex: 1, display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 14, overflow: "hidden" }}>
        {sheets.map((s) => <SheetCard key={s.id} s={s} onOpen={onOpen} onFloat={onFloat} onMenu={onMenu} />)}
      </div>
      {/* create FAB */}
      <div style={{ position: "absolute", right: 34, bottom: 30, width: 56, height: 56, borderRadius: 99, background: APP.accent, color: APP.accentText, display: "grid", placeItems: "center", boxShadow: "0 8px 20px rgba(0,0,0,.4)" }}>
        <Ico n="plus" size={26} sw={2.2} />
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════
   2. EDITOR
   ══════════════════════════════════════════════════════════════ */
function Editor({ s, onBack }) {
  const ink = inkOn(PAPER);
  const tabs = ["Chords", "Symbols", "Text", "Page"];
  return (
    <div style={{ position: "absolute", inset: 0, display: "flex" }}>
      {/* ribbon 40 % */}
      <div style={{ width: "40%", height: "100%", background: APP.panelBottom, padding: "16px 18px", display: "flex", flexDirection: "column", gap: 12, boxShadow: "8px 0 24px rgba(0,0,0,.35)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <Chip w={34} h={34} r={10} bg={APP.panelTop}><span style={{ color: APP.soft }} onClick={onBack}><Ico n="back" size={16} /></span></Chip>
          <span style={{ fontFamily: SANS, fontSize: 16, fontWeight: 800, color: "#fff" }}>{s.title}</span>
        </div>
        <div style={{ display: "flex", gap: 6 }}>
          {tabs.map((t, i) => <Pill key={t} label={t} on={i === 0} small />)}
        </div>
        <Ribbon title="Chord" summary="C · Am · F · G">
          <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
            {["C", "D", "E", "F", "G", "A", "B"].map((c) => <Tool key={c} label={c} />)}
          </div>
        </Ribbon>
        <Ribbon title="Quality" summary="major">
          <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
            {["maj", "m", "7", "maj7", "sus4", "°"].map((c, i) => <Tool key={c} label={c} on={i === 0} />)}
          </div>
        </Ribbon>
        <Ribbon title="Section" summary="Verse">
          <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
            {["Intro", "Verse", "Chorus", "Bridge"].map((c, i) => <Tool key={c} label={c} on={i === 1} />)}
          </div>
        </Ribbon>
      </div>
      {/* page 60 % */}
      <div style={{ flex: 1, background: `linear-gradient(180deg, ${PAPER} 0%, ${shade(PAPER, -0.06)} 100%)`, padding: "22px 28px", overflow: "hidden" }}>
        <div style={{ fontFamily: SANS, fontSize: 10, fontWeight: 800, letterSpacing: 1.2, color: rgba("#000000", 0.4) }}>KEY OF {s.key} · {s.bpm} BPM</div>
        {s.sections.filter((x) => x.lines.length).slice(0, 4).map((sec) => (
          <div key={sec.name} style={{ marginTop: 14 }}>
            <span style={{ display: "inline-block", padding: "3px 10px", borderRadius: 99, background: KIND[sec.kind], color: "#fff", fontFamily: SANS, fontSize: 11, fontWeight: 800 }}>{sec.name}</span>
            <div style={{ marginTop: 8, display: "flex", gap: 18 }}>
              {sec.lines[0].map((c, i) => (
                <span key={i} style={{ fontFamily: HAND, fontSize: 32, color: ink, borderLeft: i === 0 ? "2.5px solid rgba(0,0,0,.75)" : "none", paddingLeft: i === 0 ? 8 : 0 }}>{c}</span>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Ribbon({ title, summary, children }) {
  return (
    <div style={{ borderRadius: 12, background: APP.panelTop, padding: "10px 12px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <span style={{ fontFamily: SANS, fontSize: 13, fontWeight: 700, color: APP.soft }}>{title}</span>
        <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 600, color: APP.mute }}>{summary}</span>
      </div>
      {children}
    </div>
  );
}
const Tool = ({ label, on }) => (
  <span style={{ padding: "8px 12px", borderRadius: 10, background: on ? APP.accent : "#2A3446", color: on ? APP.accentText : APP.accent, fontFamily: SANS, fontSize: 12, fontWeight: 700 }}>{label}</span>
);

/* ══════════════════════════════════════════════════════════════
   3. MIXER BACKDROP (what the sheet floats over)
   ══════════════════════════════════════════════════════════════ */
function MixerBackdrop() {
  const cols = [SHEET_PAL[0], SHEET_PAL[8], SHEET_PAL[13], SHEET_PAL[17], SHEET_PAL[4]];
  const names = ["Keys", "Pad", "Bass", "Gtr", "Lead"];
  return (
    <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column" }}>
      {/* top bar */}
      <div style={{ height: 46, display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 22px", borderBottom: "1px solid rgba(255,255,255,.05)" }}>
        <span style={{ fontFamily: SANS, fontSize: 15, fontWeight: 800, color: APP.ink }}>Sunday Service</span>
        <div style={{ display: "flex", gap: 10 }}>
          <Chip w={34} h={34} r={10} bg={APP.panelTop}><span style={{ color: APP.accent }}><Ico n="layers" size={16} /></span></Chip>
          <Chip w={34} h={34} r={10} bg={APP.panelTop}><span style={{ color: APP.accent }}><Ico n="grid" size={16} /></span></Chip>
        </div>
      </div>
      {/* strips */}
      <div style={{ flex: 1, display: "flex", gap: 6, padding: "14px 22px" }}>
        {names.map((n, i) => (
          <div key={n} style={{ width: 126, borderRadius: 18, overflow: "hidden", position: "relative", background: `linear-gradient(180deg, ${APP.panelTop} 0%, ${APP.panelBottom} 55%)`, border: "1px solid rgba(255,255,255,.06)" }}>
            <div style={{ position: "absolute", inset: 0, background: `radial-gradient(130% 60% at 50% 100%, ${rgba(cols[i], 0.28)}, transparent 72%)` }} />
            <div style={{ position: "absolute", left: 6, right: 6, top: 7, height: 28, borderRadius: 10, background: APP.panelTop, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, fontFamily: SANS, fontSize: 11.5, fontWeight: 800, color: APP.ink }}>
              <span style={{ width: 5, height: 5, borderRadius: 3, background: cols[i] }} />{n}
            </div>
            <div style={{ position: "absolute", left: 51, width: 24, top: 80, height: 170, borderRadius: 12, background: "linear-gradient(180deg,#2E3347,#3A3F57)", boxShadow: "inset 0 2px 4px rgba(0,0,0,.45)" }}>
              <div style={{ position: "absolute", left: 9, top: 7, bottom: 7, width: 6, borderRadius: 3, background: "linear-gradient(180deg,#14161F,#242836)" }} />
              <div style={{ position: "absolute", left: 9.5, width: 5, bottom: 7, height: 60, borderRadius: 3, background: cols[i] }} />
            </div>
            <div style={{ position: "absolute", left: 40, width: 46, top: 80 + 60, height: 32, borderRadius: 7, background: "linear-gradient(180deg,#6E7799 0%, #4B5373 14%, #3F4766 100%)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.28), 0 6px 10px rgba(0,0,0,.5)" }} />
          </div>
        ))}
      </div>
      {/* bottom bar */}
      <div style={{ height: 54, display: "flex", alignItems: "center", justifyContent: "center", gap: 12, borderTop: "1px solid rgba(255,255,255,.05)" }}>
        <Chip w={40} h={40} r={12} bg={APP.panelTop}><span style={{ color: APP.accent }}><Ico n="note" size={18} /></span></Chip>
        <div style={{ width: 150, height: 8, borderRadius: 4, background: APP.well }}><div style={{ width: "62%", height: 8, borderRadius: 4, background: `linear-gradient(90deg, ${rgba(APP.accent, .45)}, ${APP.accent})` }} /></div>
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════
   4. FLOATING CARD  ← the redesign
   ══════════════════════════════════════════════════════════════ */
function FloatingCard({ s, pos, onPos, sections, mode, onPickSections, locked, onLock, onOpenEditor, onClose }) {
  const base = s.color;
  const ink = inkOn(base);
  const drag = useRef(null);
  const shown = sections.length ? sections : [s.sections[0]];

  const down = (e) => {
    if (e.target.closest("[data-ctl]")) return;
    drag.current = { x: e.clientX - pos.x, y: e.clientY - pos.y };
    e.currentTarget.setPointerCapture(e.pointerId);
  };
  const move = (e) => {
    if (!drag.current) return;
    onPos({ x: e.clientX - drag.current.x, y: e.clientY - drag.current.y });
  };
  const up = () => { drag.current = null; };

  return (
    <div
      onPointerDown={down} onPointerMove={move} onPointerUp={up}
      onDoubleClick={onOpenEditor}
      style={{
        position: "absolute", left: pos.x, top: pos.y, width: 372, maxHeight: "92%", cursor: "grab",
        borderRadius: 26, overflow: "hidden", touchAction: "none", userSelect: "none",
        /* NO OUTLINE: the card is its own colour, only a soft shadow lifts it */
        background: `linear-gradient(180deg, ${base} 0%, ${shade(base, -0.12)} 100%)`,
        boxShadow: "0 26px 60px rgba(0,0,0,.55)",
        animation: "rise .34s cubic-bezier(.2,.9,.25,1)",
      }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.06, mixBlendMode: "overlay", pointerEvents: "none" }} />
      {/* controls */}
      <div data-ctl style={{ position: "absolute", top: 10, right: 10, display: "flex", gap: 6, zIndex: 2 }}>
        <span onClick={() => { vib(8); onPickSections(); }} style={{ display: "grid", placeItems: "center", width: 28, height: 28, borderRadius: 9, background: rgba(ink === "#FFFFFF" ? "#000" : "#fff", 0.16), color: ink, cursor: "pointer" }}><Ico n="layers" size={15} /></span>
        <span onClick={() => { vib(8); onLock(); }} style={{ display: "grid", placeItems: "center", width: 28, height: 28, borderRadius: 9, background: rgba(ink === "#FFFFFF" ? "#000" : "#fff", 0.16), color: ink, cursor: "pointer", opacity: locked ? 0.95 : 0.5 }}><Ico n={locked ? "lock" : "unlock"} size={15} /></span>
      </div>
      <div style={{ padding: "22px 22px 20px", display: "flex", flexDirection: "column", gap: 14 }}>
        {shown.map((sec) => (
          <div key={sec.name}>
            <span style={{ display: "inline-block", padding: "3px 10px", borderRadius: 99, background: sec.colorOverride || KIND[sec.kind], color: "#fff", fontFamily: SANS, fontSize: 11, fontWeight: 800 }}>{sec.name}</span>
            <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 6 }}>
              {sec.lines.map((line, li) => (
                <div key={li} style={{ display: "flex", gap: 14 }}>
                  {line.map((c, i) => (
                    <span key={i} style={{ flex: 1, fontFamily: HAND, fontSize: 30, color: ink, borderLeft: i === 0 ? `2.5px solid ${rgba(ink === "#FFFFFF" ? "#fff" : "#000", .8)}` : "none", paddingLeft: i === 0 ? 6 : 0 }}>{c}</span>
                  ))}
                </div>
              ))}
            </div>
            {sec.lyric && <div style={{ marginTop: 8, fontFamily: SANS, fontSize: 13, fontWeight: 600, color: rgba(ink === "#FFFFFF" ? "#fff" : "#000", 0.72) }}>{sec.lyric}</div>}
          </div>
        ))}
      </div>
      {/* drag hint / close */}
      <div data-ctl style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 22px 14px" }}>
        <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 600, color: rgba(ink === "#FFFFFF" ? "#fff" : "#000", 0.6) }}>
          {locked ? "Locked — swipe and drag disabled" : "Drag anywhere · double-tap to edit"}
        </span>
        <span onClick={onClose} style={{ display: "grid", placeItems: "center", width: 26, height: 26, borderRadius: 9, background: rgba(ink === "#FFFFFF" ? "#000" : "#fff", 0.16), color: ink, cursor: "pointer" }}><Ico n="close" size={14} /></span>
      </div>
    </div>
  );
}

function SectionPicker({ s, sections, mode, setMode, onToggle, onClose }) {
  return (
    <div style={{ position: "absolute", right: 16, top: 48, width: 250, borderRadius: 16, background: APP.panelTop, boxShadow: "0 18px 44px rgba(0,0,0,.6)", padding: 12, zIndex: 5, animation: "rise .22s ease" }}>
      <div style={{ fontFamily: SANS, fontSize: 10, fontWeight: 800, letterSpacing: 1.2, color: APP.mute, marginBottom: 8 }}>SECTIONS SHOWN</div>
      <div style={{ display: "flex", gap: 6, marginBottom: 10 }}>
        <Pill label="One" on={mode === "one"} onClick={() => { vib(6); setMode("one"); }} small />
        <Pill label="Many" on={mode === "many"} onClick={() => { vib(6); setMode("many"); }} small />
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
        {s.sections.map((sec) => {
          const on = sections.some((x) => x.name === sec.name);
          return (
            <div key={sec.name} onClick={() => { vib(6); onToggle(sec); }} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px", borderRadius: 9, background: on ? rgba(APP.accent, 0.14) : "transparent", cursor: "pointer" }}>
              <span style={{ width: 9, height: 9, borderRadius: 5, background: KIND[sec.kind] }} />
              <span style={{ flex: 1, fontFamily: SANS, fontSize: 13, fontWeight: 600, color: on ? APP.soft : APP.mute }}>{sec.name}</span>
              {on && <span style={{ color: APP.accent }}><Ico n="layers" size={13} /></span>}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ── small shared bits ── */
const Chip = ({ w, h, r, bg, children }) => <div style={{ width: w, height: h, borderRadius: r, background: bg, display: "grid", placeItems: "center" }}>{children}</div>;
const Pill = ({ label, on, onClick, small }) => (
  <span onClick={onClick} style={{ padding: small ? "6px 12px" : "8px 16px", borderRadius: 99, background: on ? APP.accent : APP.panelTop, color: on ? APP.accentText : APP.mute, fontFamily: SANS, fontSize: small ? 12 : 13, fontWeight: 700, cursor: onClick ? "pointer" : "default", whiteSpace: "nowrap" }}>{label}</span>
);
const Seg = ({ a, b, sel, onSel, w }) => (
  <div style={{ width: w, height: 40, borderRadius: 99, background: APP.panelTop, display: "flex", padding: 3 }}>
    {[a, b].map((t, i) => (
      <span key={t} onClick={() => onSel(i)} style={{ flex: 1, display: "grid", placeItems: "center", borderRadius: 99, background: i === sel ? APP.accent : "transparent", color: i === sel ? APP.accentText : APP.mute, fontFamily: SANS, fontSize: 13, fontWeight: 700, cursor: "pointer" }}>{t}</span>
    ))}
  </div>
);

/* ══════════════════════════════════════════════════════════════
   STAGE
   ══════════════════════════════════════════════════════════════ */
export default function SheetsPreview() {
  const [view, setView] = useState("library");        // library | editor | floating
  const [notation, setNotation] = useState("nashville");
  const [active, setActive] = useState(null);          // sheet being floated
  const [pos, setPos] = useState({ x: 300, y: 60 });
  const [sections, setSections] = useState([]);
  const [mode, setMode] = useState("one");
  const [locked, setLocked] = useState(false);
  const [picker, setPicker] = useState(false);
  const [reduced, setReduced] = useState(false);
  const [vp, setVp] = useState({ w: 900, h: 500 });

  useEffect(() => {
    const on = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    on(); window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);

  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 128) / H, 1.5));

  const float = (s) => {
    setActive(s);
    setPos({ x: 300, y: 58 });
    setSections([s.sections.find((x) => x.lines.length) || s.sections[0]]);
    setMode("one"); setLocked(false); setPicker(false);
    setView("floating");
  };
  const toggleSection = (sec) => {
    setSections((cur) => {
      if (mode === "one") return [sec];
      const has = cur.some((x) => x.name === sec.name);
      return has ? cur.filter((x) => x.name !== sec.name) : [...cur, sec];
    });
  };
  const setOneMany = (m) => { setMode(m); if (m === "one") setSections((c) => c.slice(0, 1)); };

  return (
    <div style={{ minHeight: "100vh", background: "#0A0D13", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: 16, fontFamily: SANS }}>
      <style>{`
        @keyframes rise{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
        *{box-sizing:border-box}
        ${reduced ? "*{animation:none!important;transition:none!important}" : ""}
      `}</style>

      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: "0 0 0 " + Math.max(2, 3 * scale) + "px #1B212C, 0 24px 60px rgba(0,0,0,.6)", overflow: "hidden", flex: "none" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: `linear-gradient(180deg, ${APP.bgTop}, ${APP.bgBottom})` }}>

          {view === "library" && <Library sheets={SEED} notation={notation} setNotation={setNotation} onOpen={(s) => { setActive(s); setView("editor"); }} onFloat={float} onMenu={() => {}} />}
          {view === "editor" && active && <Editor s={active} onBack={() => setView("library")} />}

          {view === "floating" && active && (
            <>
              <MixerBackdrop />
              <FloatingCard
                s={active} pos={pos} onPos={setPos}
                sections={sections} mode={mode}
                onPickSections={() => setPicker((p) => !p)}
                locked={locked} onLock={() => setLocked((l) => !l)}
                onOpenEditor={() => setView("editor")}
                onClose={() => { setView("library"); setActive(null); }}
              />
              {picker && <SectionPicker s={active} sections={sections} mode={mode} setMode={setOneMany} onToggle={toggleSection} onClose={() => setPicker(false)} />}
            </>
          )}

          {view === "library" && (
            <div style={{ position: "absolute", left: 28, bottom: 8, fontFamily: MONO, fontSize: 10.5, color: APP.dim }}>
              double-tap a card to float it over the mixer
            </div>
          )}
        </div>
      </div>

      {/* preview helpers (outside the device) */}
      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", alignItems: "center", maxWidth: 820 }}>
        <span style={{ display: "inline-flex", alignItems: "center", gap: 8, padding: "7px 12px", borderRadius: 999, background: "#121823", border: "1px solid #232C3B", fontSize: 12, color: "#B6C0D2" }}>
          <span style={{ width: 8, height: 8, borderRadius: 4, background: view === "floating" ? "#7DD3A0" : "#566075" }} />
          {view === "library" ? "Library" : view === "editor" ? "Editor" : `Floating over mixer · ${sections.length} section${sections.length > 1 ? "s" : ""}`}
        </span>
        {[["Library", () => setView("library")], ["Editor", () => { if (active) setView("editor"); }], ["Float a sheet", () => float(SEED[0])], [reduced ? "Motion: off" : "Motion: on", () => setReduced((r) => !r)]].map(([l, fn]) => (
          <button key={l} onClick={fn} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>{l}</button>
        ))}
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 620, lineHeight: 1.5 }}>
        Double-tap a card to lift it over the mixer · drag it anywhere · the card keeps its own colour with no outline · the layers button chooses one section or several
      </div>
    </div>
  );
}
