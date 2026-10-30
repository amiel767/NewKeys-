import { useEffect, useRef, useState } from "react";
import { Scene } from "./FxSpacePreview.jsx";

/* ═════════════════════════════════════════════════════════════════════════════
   ÆTHER FX — fenêtre d'effets.

   RÉFÉRENCE : `fx-window-preview.html`, la fenêtre réellement branchée dans
   l'app. C'est elle qui donne le style des cartes, la barre de titre, la
   pellicule de slots et la feuille d'ajout. On la suit, on ne l'invente pas.

   ÉCRAN : 891 × 411, le cadre de l'app.

   LA FENÊTRE EST LA FENÊTRE
   Le rack n'est pas une boîte dans une boîte : la barre de titre, la pellicule de
   cartes et la feuille d'ajout occupent la fenêtre entière. Space ne « tient » pas
   dans une case — il remplace le corps de la fenêtre, comme dans l'app.

   PALETTE : celle de la fenêtre (marine + bleu pâle), pour tout. L'accent propre
   de l'effet ne marque que son identité, jamais le mobilier.

   PAS DE LOGIQUE INUTILE
   Pas de compteur en double, pas de « Reorder » décoratif, pas de libellé de
   géométrie affiché à l'écran.
   ═════════════════════════════════════════════════════════════════════════════ */

const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const FONT = "Space Grotesk, Inter, system-ui, -apple-system, sans-serif";

/* La fenêtre : marine et bleu pâle. */
const WIN_T = "#1F2635", WIN_B = "#131925";
const CARD_T = "#323845", CARD_B = "#222733";
const WELL = "#0E1420", WELL2 = "#0B0F18";
const INK = "#DFE4EE", DIM = "#8D96AB", MUTE = "#5F6880";
const LINE = "rgba(255,255,255,.07)", LINE2 = "rgba(255,255,255,.13)";
const IN1 = "#C7D9F9", IN2 = "#9DB9EF", IN_TEXT = "#1B2440";
const ACC = "#8FB2E8";

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const hx = (c, a) => {
  const n = parseInt(c.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
};

const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'>" +
  "<filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2'/>" +
  "<feColorMatrix type='saturate' values='0'/></filter>" +
  "<rect width='100%' height='100%' filter='url(#n)'/></svg>"
)}")`;

/* ── les icônes (les nôtres, conservées) ──────────────────────────────────── */
const P = {
  back: "M15 18l-6-6 6-6", close: ["M18 6L6 18", "M6 6l12 12"], plus: ["M12 5v14", "M5 12h14"],
  power: ["M12 2v9", "M18.36 6.64a9 9 0 1 1-12.73 0"], trash: ["M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13"],
  search: ["M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M21 21l-4.3-4.3"],
  wave: "M2 12c2 0 2-4 4-4s2 8 4 8 2-8 4-8 2 4 4 4",
  sliders: ["M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6"],
  down: "M6 9l6 6 6-6", check: "M20 6L9 17l-5-5",
  midi: ["M9 18V5l12-2v13", "M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M18 19a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"],
  up: "M3 19h4v-4h4v-4h4V7h4", dn: "M3 5h4v4h4v4h4v4h4",
  ud: "M3 18l4-6 5-6 5 6 4 6", du: "M3 6l4 6 5 6 5-6 4-6",
  rnd: "M3 14l4-6 4 8 4-10 5 8", chd: ["M12 4v3", "M12 10.5v3", "M12 17v3"],
  seq: ["M3 6h4v4H3z", "M10 6h4v4h-4z", "M17 6h4v4h-4z", "M3 14h4v4H3z", "M10 14h4v4h-4z"],
  easy: ["M12 3v10", "M12 13a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M12 3l5 3"],
  oct: "M12 4l6 7h-4v9h-4v-9H6l6-7z",
  chords: ["M4 20V6", "M4 6l16-2v14", "M9 14a2 2 0 1 0 0 4 2 2 0 0 0 0-4z", "M17 12a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"],
};
const Ico = ({ d, size = 16, sw = 2, style }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"
       style={{ display: "block", flex: "none", ...style }}>
    {(Array.isArray(d) ? d : [d]).map((p, i) => <path key={i} d={p} />)}
  </svg>
);

/* ── les effets : noms et icônes de l'app ─────────────────────────────────── */
const AUDIO = [
  { id: 0, name: "Æther Space", type: "Reverb", fam: "Space", accent: "#A99AD8", icon: P.wave, space: true,
    params: [["Size", .62, 0, 100, " %"], ["Decay", .7, .4, 8, " s"], ["Pre-delay", .2, 0, 120, " ms"],
             ["Damp", .45, 1, 12, " kHz"], ["Width", .8, 0, 100, " %"], ["Mix", .35, 0, 100, " %"],
             ["Diffuse", .5, 0, 100, " %"]],
    presets: ["Hall", "Plate", "Room", "Cathedral", "Shimmer", "Chamber", "Bloom", "Infinite"] },
  { id: 1, name: "Æther Echo", type: "Delay", fam: "Space", accent: "#D4B06A", icon: P.sliders },
  { id: 2, name: "Prism Tone", type: "Equalizer", fam: "Tone", accent: "#8FD0FF", icon: P.sliders },
  { id: 3, name: "Iron Grip", type: "Compressor", fam: "Dynamics", accent: "#B8C2D4", icon: P.sliders,
    params: [["Threshold", .48, -60, 0, " dB"], ["Ratio", .35, 1, 20, ""], ["Attack", .25, .1, 100, " ms"],
             ["Release", .52, 10, 1000, " ms"], ["Knee", .4, 0, 24, " dB"], ["Makeup", .4, 0, 24, " dB"]],
    presets: ["Glue", "Gentle", "Punch", "Limit", "Vocal Leveler", "Drum Bus", "Parallel"] },
  { id: 4, name: "Solar Ceiling", type: "Maximizer", fam: "Dynamics", accent: "#C9852C", icon: P.sliders },
];
const MIDI = [
  { id: 101, type: "arp", name: "Arpeggiator", fam: "Pattern", icon: P.up, desc: "Turns held notes into a pattern" },
  { id: 102, type: "seq", name: "Sequencer", fam: "Pattern", icon: P.seq, desc: "One note plays a sequence of notes" },
  { id: 103, type: "easy", name: "Easy Chord", fam: "Harmony", icon: P.easy, desc: "One note plays a programmed chord" },
  { id: 104, type: "chords", name: "Chord Sequencer", fam: "Harmony", icon: P.chords, desc: "Plays a chord progression" },
  { id: 105, type: "octave", name: "Octave Up", fam: "Pitch", icon: P.oct, desc: "Adds an octave above every note" },
];
const MAX_AUDIO = 3, MAX_MIDI = 1;
const STYLES = [["up", "Up", P.up], ["down", "Down", P.dn], ["updown", "Up-Down", P.ud],
                ["downup", "Down-Up", P.du], ["random", "Random", P.rnd], ["chord", "Chord", P.chd]];
const SUBS = ["1/4", "1/8", "1/16", "1/32"];
const fmt = ([n, v, mn, mx, u]) => {
  const x = mn + v * (mx - mn);
  return `${Math.abs(x) < 10 ? x.toFixed(2) : Math.round(x)}${u}`;
};

/* ── surfaces de la fenêtre ───────────────────────────────────────────────── */
const card = (x = {}) => ({ background: `linear-gradient(180deg,${CARD_T},${CARD_B})`, border: `1px solid ${LINE2}`,
  boxShadow: "0 8px 16px rgba(0,0,0,.5), inset 0 1px 0 rgba(255,255,255,.11)", borderRadius: 16, ...x });
const well = (x = {}) => ({ background: `linear-gradient(180deg,${WELL},${WELL2})`,
  boxShadow: "inset 0 3px 10px rgba(0,0,0,.6), inset 0 0 0 1px rgba(255,255,255,.04)", borderRadius: 12, ...x });
const ghost = (x = {}) => ({ background: "linear-gradient(180deg,rgba(255,255,255,.09),rgba(255,255,255,.03))",
  border: `1px solid ${LINE2}`, boxShadow: "inset 0 1px 0 rgba(255,255,255,.09)", borderRadius: 10, ...x });
const primary = (x = {}) => ({ background: `linear-gradient(180deg,${IN1},${IN2})`, color: IN_TEXT,
  boxShadow: `0 3px 12px ${hx(IN2, .3)}, inset 0 1px 0 rgba(255,255,255,.5)`, borderRadius: 10, border: 0, ...x });

/* ── le potard : glisser vertical, double-clic = valeur du preset ─────────── */
function Knob({ label, value, onChange, display, accent = IN1, size = 50, onReset }) {
  const drag = useRef(null);
  const [hot, setHot] = useState(false);
  const ang = -140 + clamp(value, 0, 1) * 280;
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 4, width: size + 20 }}>
      <div onPointerDown={(e) => { drag.current = { y: e.clientY, v: value };
                                    e.currentTarget.setPointerCapture(e.pointerId); setHot(true); }}
           onPointerMove={(e) => { if (!drag.current) return;
             onChange(clamp(drag.current.v + (drag.current.y - e.clientY) / 150, 0, 1)); }}
           onPointerUp={() => { drag.current = null; setHot(false); }}
           onDoubleClick={() => onReset && onReset()}
           style={{ width: size, height: size, position: "relative", cursor: "ns-resize", touchAction: "none" }}>
        <div style={{ position: "absolute", inset: 0, borderRadius: "50%",
                      background: "radial-gradient(circle at 34% 26%, #454E63, #1A1F2C 78%)",
                      boxShadow: hot ? `0 0 0 2px ${hx(accent, .45)}, 0 5px 14px rgba(0,0,0,.6)`
                                     : "0 5px 14px rgba(0,0,0,.6), inset 0 1px 0 rgba(255,255,255,.12)" }} />
        <div style={{ position: "absolute", inset: 5, borderRadius: "50%",
                      background: "radial-gradient(circle at 38% 24%, #333B4E, #171C28 82%)",
                      boxShadow: "inset 0 2px 5px rgba(0,0,0,.7)" }} />
        <div style={{ position: "absolute", left: "50%", top: "50%", width: 2, height: size * .28,
                      marginLeft: -1, marginTop: -size * .28, transformOrigin: "50% 100%",
                      transform: `rotate(${ang}deg)`, borderRadius: 2,
                      background: accent, boxShadow: `0 0 7px ${hx(accent, .8)}` }} />
      </div>
      <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: ".13em", textTransform: "uppercase",
                     color: hot ? INK : DIM, whiteSpace: "nowrap" }}>{label}</span>
      <span style={{ fontSize: 10, fontFamily: MONO, color: hot ? INK : "#7E879C" }}>{display}</span>
    </div>
  );
}

function Stepper({ value, min, max, onChange, label }) {
  const b = (t, fn) => (
    <button onClick={fn} style={{ ...ghost({ width: 26, height: 26, display: "grid", placeItems: "center",
             color: DIM, cursor: "pointer", borderRadius: 8 }) }}>{t}</button>
  );
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
      {label && <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: ".13em", textTransform: "uppercase",
                               color: DIM }}>{label}</span>}
      {b("−", () => onChange(clamp(value - 1, min, max)))}
      <span style={{ minWidth: 26, textAlign: "center", fontSize: 13, fontWeight: 800, fontFamily: MONO }}>{value}</span>
      {b("+", () => onChange(clamp(value + 1, min, max)))}
    </div>
  );
}

function Seg({ options, value, onChange, height = 34 }) {
  return (
    <div style={{ display: "flex", gap: 2, padding: 3, borderRadius: 11, background: WELL2,
                  border: `1px solid ${LINE}`, boxShadow: "inset 0 2px 6px rgba(0,0,0,.5)" }}>
      {options.map(([k, l, ic]) => {
        const on = value === k;
        return (
          <button key={k} onClick={() => onChange(k)} title={l}
                  style={{ height, minWidth: on ? 96 : 40, borderRadius: 8, border: 0, cursor: "pointer",
                           background: on ? "linear-gradient(180deg,#5B6591,#49527A)" : "transparent",
                           color: on ? "#FFFFFF" : "#8791B5", display: "flex", alignItems: "center",
                           justifyContent: "center", gap: 7, fontSize: 11.5, fontWeight: 700,
                           fontFamily: FONT, transition: "all .18s" }}>
            {ic && <Ico d={ic} size={15} />}{on && l}
          </button>
        );
      })}
    </div>
  );
}

/* ══ LA CARTE ═══════════════════════════════════════════════════════════════
   Style de `fx-window-preview.html` : type en petites capitales, nom en grand,
   le visuel de l'effet au milieu, une ligne de pied avec la LED. Elle prend TOUTE
   la hauteur du corps : jamais tronquée.
   ─────────────────────────────────────────────────────────────────────────── */
function FxCard({ fx, midi, on, onToggle, onOpen, onRemove, height, accent }) {
  const [hot, setHot] = useState(false);
  const fireRef = useRef(() => {});
  const vals = Object.fromEntries((fx.params || []).map(([n, v]) => [n.toLowerCase().replace(/[^a-z]/g, ""), v]));
  const ac = accent || fx.accent || ACC;
  return (
    <div onClick={onOpen} onPointerEnter={() => setHot(true)} onPointerLeave={() => setHot(false)}
         style={{ ...card({ height, flex: 1, minWidth: 0, padding: 10, cursor: "pointer",
                  display: "flex", flexDirection: "column", gap: 7,
                  transform: hot ? "translateY(-3px)" : "none",
                  transition: "transform .18s cubic-bezier(.2,.9,.3,1)",
                  opacity: on ? 1 : .62 }) }}>
      <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
        <span style={{ fontSize: 8, fontWeight: 800, letterSpacing: ".16em", textTransform: "uppercase",
                       color: on ? ac : DIM, flex: 1, whiteSpace: "nowrap", overflow: "hidden",
                       textOverflow: "ellipsis" }}>{fx.type}</span>
        <button onClick={(e) => { e.stopPropagation(); onToggle(); }} title="Engage / Bypass"
                style={{ width: 20, height: 20, borderRadius: 6, border: 0, cursor: "pointer",
                         display: "grid", placeItems: "center", flex: "none",
                         background: on ? `linear-gradient(180deg,${IN1},${IN2})` : "rgba(255,255,255,.07)",
                         color: on ? IN_TEXT : MUTE }}>
          <Ico d={P.power} size={11} sw={2.4} />
        </button>
        <button onClick={(e) => { e.stopPropagation(); onRemove(); }} title="Remove"
                style={{ width: 20, height: 20, borderRadius: 6, border: 0, cursor: "pointer",
                         display: "grid", placeItems: "center", flex: "none",
                         background: "rgba(255,255,255,.05)", color: MUTE, opacity: hot ? 1 : 0,
                         transition: "opacity .15s" }}>
          <Ico d={P.trash} size={11} />
        </button>
      </div>

      <div style={{ fontSize: 19, lineHeight: 1.05, fontWeight: 700, color: on ? INK : "#98A1B4",
                    whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{fx.name}</div>

      <div style={{ flex: 1, minHeight: 0, borderRadius: 11, overflow: "hidden", position: "relative",
                    background: "#12161F", boxShadow: "inset 0 2px 7px rgba(0,0,0,.7), inset 0 0 0 1px rgba(255,255,255,.04)" }}>
        {fx.space
          ? <Scene vals={vals} fireRef={fireRef} accent={ac} hover={false} />
          : midi ? <MiniSteps accent={ac} on={on} /> : <MiniGraph type={fx.type} accent={ac} on={on} />}
        {!on && <div style={{ position: "absolute", inset: 0, background: "rgba(10,13,20,.6)" }} />}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 9, fontWeight: 700,
                    color: DIM, flex: "none" }}>
        <span style={{ width: 8, height: 8, borderRadius: 4, flex: "none",
                       background: on ? "#C9D2E6" : "#2A3040",
                       boxShadow: on ? "0 0 7px rgba(201,210,230,.6)" : "inset 0 1px 2px rgba(0,0,0,.8)" }} />
        <span style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
          {midi ? "MIDI effect" : (fx.presets ? fx.presets[0] : fx.type)}
        </span>
      </div>
    </div>
  );
}

function MiniGraph({ type, accent, on }) {
  const paths = {
    Delay: "M4 34h10c6 0 6-22 12-22s6 22 12 22h8",
    Equalizer: "M4 34c8 0 8-22 16-22s8 22 16 22",
    Compressor: "M4 12c10 0 12 22 22 22",
    Maximizer: "M4 30c10 0 14 4 22 4",
  };
  const d = paths[type] || paths.Equalizer;
  return (
    <svg viewBox="0 0 56 44" style={{ width: "100%", height: "100%" }} preserveAspectRatio="none">
      <defs><linearGradient id={"g" + type} x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor={accent} stopOpacity=".32" /><stop offset="1" stopColor={accent} stopOpacity="0" />
      </linearGradient></defs>
      <path d={`${d} V44 H4 Z`} fill={`url(#g${type})`} />
      <path d={d} fill="none" stroke={on ? accent : MUTE} strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

function MiniSteps({ accent, on }) {
  const h = [.9, .6, .75, .5, 1, .55, .7, .45, .85, .6, .8, .5, .95, .55, .7, .45];
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 3, height: "100%", padding: 8 }}>
      {h.map((v, i) => (
        <div key={i} style={{ flex: 1, height: `${v * 100}%`, borderRadius: 3,
                              background: on ? `linear-gradient(180deg,${accent},${hx(accent, .45)})` : "#2A3040" }} />
      ))}
    </div>
  );
}

/* ══ LE SLOT VIDE ═══════════════════════════════════════════════════════════
   Un creux : ombre portée à l'intérieur du bord haut, lèvre basse éclairée, et
   rien d'autre que le « + ». Pas de tuile d'ajout en bas de fenêtre.
   ─────────────────────────────────────────────────────────────────────────── */
function EmptySlot({ onAdd, height, label }) {
  return (
    <button onClick={onAdd} style={{ flex: 1, minWidth: 0, height, borderRadius: 16, cursor: "pointer",
             border: "none", padding: 0, fontFamily: FONT, position: "relative",
             background: `linear-gradient(180deg,${WELL},${WELL2})`,
             boxShadow: "inset 0 7px 16px rgba(0,0,0,.75), inset 0 0 0 1px rgba(255,255,255,.05), inset 3px 0 7px rgba(0,0,0,.4), inset -3px 0 7px rgba(0,0,0,.4)" }}>
      <div style={{ position: "absolute", left: 10, right: 10, bottom: 2, height: 1.4, borderRadius: 1,
                    background: "linear-gradient(90deg,transparent,rgba(255,255,255,.14),transparent)" }} />
      <div style={{ height: "100%", display: "flex", flexDirection: "column", alignItems: "center",
                    justifyContent: "center", gap: 10 }}>
        <div style={{ width: 40, height: 40, borderRadius: 13, display: "grid", placeItems: "center",
                      color: MUTE, background: "rgba(255,255,255,.04)", border: `1px dashed ${hx(ACC, .3)}` }}>
          <Ico d={P.plus} size={18} sw={2.4} />
        </div>
        <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: ".14em", textTransform: "uppercase",
                       color: "#454E66" }}>{label}</span>
      </div>
    </button>
  );
}

/* ══ LA FEUILLE D'AJOUT ═════════════════════════════════════════════════════
   La forme de la référence : un titre, des familles en pastilles, une recherche,
   et une grille de tuiles larges — glyphe, nom, description. Un clic ajoute.
   ─────────────────────────────────────────────────────────────────────────── */
function Catalog({ kind, onPick, onBack, full }) {
  const list = kind === "midi" ? MIDI : AUDIO;
  const fams = ["All", ...Array.from(new Set(list.map((f) => f.fam)))];
  const [fam, setFam] = useState("All");
  const [q, setQ] = useState("");
  const [sel, setSel] = useState(0);
  const shown = list.filter((f) => (fam === "All" || f.fam === fam) &&
                                   (!q || f.name.toLowerCase().includes(q.toLowerCase())));
  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 9, paddingBottom: 10, flex: "none" }}>
        <button onClick={onBack} style={{ ...ghost({ width: 28, height: 28, display: "grid", placeItems: "center",
                 color: DIM, cursor: "pointer" }) }}><Ico d={P.back} size={14} /></button>
        <span style={{ fontSize: 13, fontWeight: 800 }}>Add {kind === "midi" ? "MIDI" : "audio"} effect</span>
        <span style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".11em", textTransform: "uppercase", color: MUTE }}>
          {kind === "midi" ? "one per track" : `up to ${MAX_AUDIO} per track`}
        </span>
        <div style={{ flex: 1 }} />
        {full && <span style={{ fontSize: 9.5, fontWeight: 800, color: ACC }}>limit reached</span>}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 6, paddingBottom: 10, flex: "none" }}>
        {fams.map((f) => (
          <button key={f} onClick={() => setFam(f)}
                  style={{ height: 26, padding: "0 12px", borderRadius: 9, cursor: "pointer",
                           fontFamily: FONT, fontSize: 10.5, fontWeight: 800, letterSpacing: ".06em",
                           background: fam === f ? "linear-gradient(180deg,#5B6591,#49527A)" : "rgba(255,255,255,.04)",
                           border: `1px solid ${fam === f ? "transparent" : LINE}`,
                           color: fam === f ? "#FFFFFF" : "#8791B5" }}>{f}</button>
        ))}
        <div style={{ flex: 1 }} />
        <div style={{ display: "flex", alignItems: "center", gap: 7, height: 28, padding: "0 11px", width: 168,
                      ...well({ borderRadius: 9, height: 28 }) }}>
          <span style={{ color: MUTE }}><Ico d={P.search} size={13} /></span>
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search…"
                 style={{ flex: 1, minWidth: 0, border: 0, outline: 0, background: "transparent",
                          color: INK, fontFamily: FONT, fontSize: 11 }} />
        </div>
      </div>

      <div style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "grid",
                    gridTemplateColumns: "1fr 1fr", gap: 9, alignContent: "start" }}>
        {shown.map((f, i) => {
          const on = i === sel;
          return (
            <button key={f.id} onClick={() => setSel(i)} onDoubleClick={() => !full && onPick(f)}
                    onPointerEnter={() => setSel(i)}
                    style={{ height: 68, borderRadius: 13, cursor: full ? "default" : "pointer", textAlign: "left",
                             display: "flex", alignItems: "center", gap: 11, padding: "0 13px", fontFamily: FONT,
                             background: on ? `linear-gradient(180deg,${hx(ACC, .13)},rgba(255,255,255,.015)),linear-gradient(180deg,#252E42,#1C2433)`
                                            : "linear-gradient(180deg,#20283A,#1A2130)",
                             border: `1px solid ${on ? hx(ACC, .45) : LINE}`,
                             boxShadow: on ? "inset 0 1px 0 rgba(255,255,255,.12), 0 6px 16px rgba(0,0,0,.45)"
                                           : "inset 0 1px 0 rgba(255,255,255,.06), 0 3px 9px rgba(0,0,0,.3)",
                             transition: "all .16s" }}>
              <span style={{ width: 36, height: 36, borderRadius: 11, flex: "none", display: "grid", placeItems: "center",
                             color: on ? INK : MUTE,
                             background: on ? `radial-gradient(circle at 50% 20%, ${hx(ACC, .3)}, #141A28 78%)`
                                            : "radial-gradient(circle at 50% 20%, rgba(255,255,255,.08), #141A28 78%)",
                             border: `1px solid ${on ? hx(ACC, .34) : LINE}` }}>
                <Ico d={f.icon} size={17} sw={1.9} />
              </span>
              <span style={{ minWidth: 0, flex: 1 }}>
                <span style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
                  <span style={{ fontSize: 12.5, fontWeight: 800, color: on ? INK : "#A8B2C6" }}>{f.name}</span>
                  <span style={{ fontSize: 8, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase",
                                 color: on ? ACC : DIM }}>{f.type}</span>
                </span>
                <span style={{ display: "block", fontSize: 10, color: "#7C869C", marginTop: 3, whiteSpace: "nowrap",
                               overflow: "hidden", textOverflow: "ellipsis" }}>
                  {f.desc || `${f.type} · ${f.fam}`}
                </span>
              </span>
              <span style={{ width: 28, height: 28, borderRadius: 9, flex: "none", display: "grid", placeItems: "center",
                             color: full ? "#3E4658" : on ? IN_TEXT : DIM,
                             background: full ? "rgba(255,255,255,.03)" : on ? `linear-gradient(180deg,${IN1},${IN2})` : "rgba(255,255,255,.045)",
                             border: on && !full ? "none" : `1px solid ${LINE}` }}>
                <Ico d={full ? P.check : P.plus} size={13} sw={2.4} />
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

/* ══ PANNEAU SPACE ══════════════════════════════════════════════════════════
   Il occupe le CORPS de la fenêtre, pas une case. À gauche la salle vivante (la
   vraie scène de FxSpacePreview, importée), à droite les réglages.
   ─────────────────────────────────────────────────────────────────────────── */
function SpacePanel({ fx, vals, setVal, preset, setPreset, on, onToggle }) {
  const fireRef = useRef(() => {});
  const idx = fx.presets.indexOf(preset);
  const step = (d) => setPreset(fx.presets[(idx + d + fx.presets.length) % fx.presets.length]);
  const get = (k) => vals[k] ?? 0.5;
  const key = (n) => n.toLowerCase().replace(/[^a-z]/g, "");
  return (
    <div style={{ display: "flex", gap: 12, height: "100%", minHeight: 0 }}>
      <div onClick={() => fireRef.current()}
           style={{ flex: "1 1 54%", minWidth: 0, position: "relative", borderRadius: 15, overflow: "hidden",
                    cursor: "crosshair", ...well({ borderRadius: 15 }) }}>
        <Scene vals={vals} fireRef={fireRef} accent={fx.accent} hover />
        <div style={{ position: "absolute", left: 11, top: 9, display: "flex", alignItems: "center", gap: 7,
                      padding: "4px 9px", borderRadius: 8, background: "rgba(10,14,26,.55)",
                      border: `1px solid ${LINE2}`, backdropFilter: "blur(6px)", pointerEvents: "none" }}>
          <span style={{ width: 5, height: 5, borderRadius: 3, background: fx.accent, boxShadow: `0 0 7px ${fx.accent}` }} />
          <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: ".09em", textTransform: "uppercase", color: "#C6CEE8" }}>{preset}</span>
          <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>{(0.4 + get("decay") * 7.6).toFixed(1)} s</span>
        </div>
        <div style={{ position: "absolute", left: 11, bottom: 8, fontSize: 8.5, fontWeight: 800,
                      letterSpacing: ".1em", textTransform: "uppercase", color: MUTE, pointerEvents: "none" }}>
          Touch the room to re-fire
        </div>
        {!on && <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center",
                              background: "rgba(9,12,22,.66)", fontSize: 12, fontWeight: 800,
                              letterSpacing: ".2em", color: MUTE, textTransform: "uppercase" }}>Bypassed</div>}
      </div>

      <div style={{ flex: "1 1 46%", minWidth: 0, display: "flex", flexDirection: "column", gap: 9 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 6, flex: "none" }}>
          <button onClick={() => step(-1)} style={{ ...ghost({ width: 26, height: 26, display: "grid",
                   placeItems: "center", color: DIM, cursor: "pointer" }) }}><Ico d={P.back} size={13} /></button>
          <div style={{ flex: 1, minWidth: 0, height: 28, display: "flex", alignItems: "center",
                        justifyContent: "space-between", padding: "0 11px", ...well({ borderRadius: 9, height: 28 }) }}>
            <span style={{ fontSize: 11.5, fontWeight: 800, whiteSpace: "nowrap", overflow: "hidden",
                           textOverflow: "ellipsis" }}>{preset}</span>
            <span style={{ fontSize: 9.5, fontFamily: MONO, color: MUTE }}>{idx + 1}/{fx.presets.length}</span>
          </div>
          <button onClick={() => step(1)} style={{ ...ghost({ width: 26, height: 26, display: "grid",
                   placeItems: "center", color: DIM, cursor: "pointer" }) }}>
            <Ico d={P.back} size={13} style={{ transform: "rotate(180deg)" }} /></button>
          <button onClick={onToggle} title="Engage / Bypass"
                  style={{ ...ghost({ width: 28, height: 28, display: "grid", placeItems: "center",
                           cursor: "pointer", color: on ? IN_TEXT : MUTE,
                           background: on ? `linear-gradient(180deg,${IN1},${IN2})` : undefined }) }}>
            <Ico d={P.power} size={13} sw={2.3} />
          </button>
        </div>

        <div style={{ flex: 1, minHeight: 0, display: "grid", gridTemplateColumns: "repeat(4,1fr)",
                      alignContent: "center", justifyItems: "center", gap: 8, padding: "8px 4px",
                      ...well({ borderRadius: 13 }) }}>
          {fx.params.slice(0, 4).map((p) => (
            <Knob key={p[0]} label={p[0]} value={get(key(p[0]))} accent={fx.accent} size={50}
                  onChange={(v) => setVal(key(p[0]), v)}
                  onReset={() => setVal(key(p[0]), p[1])}
                  display={fmt([p[0], get(key(p[0])), p[1], p[2], p[4]])} />
          ))}
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 10, padding: "0 4px", flex: "none" }}>
          {fx.params.slice(4).map((p) => {
            const k = key(p[0]), v = get(k);
            return (
              <div key={p[0]} style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase",
                               color: DIM }}>{p[0]}</span>
                <div onPointerDown={(e) => {
                       const r = e.currentTarget.getBoundingClientRect();
                       const set = (x) => setVal(k, clamp((x - r.left) / r.width, 0, 1));
                       set(e.clientX); e.currentTarget.setPointerCapture(e.pointerId);
                       const mv = (m) => set(m.clientX);
                       e.currentTarget.onpointermove = mv;
                       e.currentTarget.onpointerup = () => { e.currentTarget.onpointermove = null; };
                     }}
                     onDoubleClick={() => setVal(k, p[1])}
                     style={{ height: 8, borderRadius: 5, cursor: "ew-resize", position: "relative",
                              ...well({ borderRadius: 5, height: 8 }) }}>
                  <div style={{ width: `${v * 100}%`, height: "100%", borderRadius: 5,
                                background: `linear-gradient(90deg,${hx(fx.accent, .6)},${IN1})` }} />
                </div>
                <span style={{ fontSize: 9.5, fontFamily: MONO, color: "#7E879C" }}>{fmt([p[0], v, p[1], p[2], p[4]])}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/* ══ PANNEAU ARPEGGIATOR ════════════════════════════════════════════════════
   L'interface réelle, portée de FxModesPreview : subdivision, style à 6 icônes,
   nombre de pas, 16 barres de vélocité tirables, 16 boutons numérotés, le curseur
   de pas. Rien d'inventé : ni octaves ni swing, qui n'existent pas dans ArpParams.
   ─────────────────────────────────────────────────────────────────────────── */
function ArpPanel({ p, set, cur }) {
  const setVel = (i, e) => {
    const r = e.currentTarget.getBoundingClientRect();
    set((s) => ({ ...s, vel: s.vel.map((x, k) => (k === i ? clamp(1 - (e.clientY - r.top) / r.height, .08, 1) : x)) }));
  };
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 9, height: "100%", minHeight: 0 }}>
      <div style={{ height: 56, flex: "none", display: "flex", alignItems: "center", gap: 16, padding: "0 14px",
                    ...well({ borderRadius: 13 }) }}>
        <Knob label="Subdivision" value={p.sub / 3} size={44}
              onChange={(v) => set((s) => ({ ...s, sub: Math.round(v * 3) }))} display={SUBS[p.sub]} />
        <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: ".13em", textTransform: "uppercase", color: DIM }}>Style</span>
        <Seg options={STYLES} value={p.style} onChange={(k) => set((s) => ({ ...s, style: k }))} />
        <div style={{ flex: 1 }} />
        <Stepper label="Steps" value={p.steps} min={1} max={16} onChange={(v) => set((s) => ({ ...s, steps: v }))} />
      </div>

      <div style={{ flex: 1, minHeight: 0, display: "flex", gap: 10, padding: "10px 12px", ...well({ borderRadius: 13 }) }}>
        <div style={{ width: 42, flex: "none", display: "flex", flexDirection: "column", alignItems: "center",
                      justifyContent: "space-between", color: DIM, fontSize: 17, fontWeight: 700 }}>
          <span>+</span>
          <span style={{ writingMode: "vertical-rl", transform: "rotate(180deg)", fontSize: 10, fontWeight: 700 }}>Velocity</span>
          <span>−</span>
        </div>
        <div style={{ flex: 1, display: "grid", gridTemplateColumns: "repeat(16,1fr)", gap: 5, minWidth: 0 }}>
          {Array.from({ length: 16 }).map((_, i) => {
            const active = i < p.steps, eng = p.on[i];
            return (
              <div key={i} style={{ display: "flex", flexDirection: "column", gap: 6, minWidth: 0 }}>
                <div onPointerDown={(e) => { if (!active) return; e.currentTarget.setPointerCapture(e.pointerId); setVel(i, e); }}
                     onPointerMove={(e) => { if (active && e.buttons) setVel(i, e); }}
                     style={{ flex: 1, borderRadius: 8, position: "relative", overflow: "hidden", touchAction: "none",
                              cursor: active ? "ns-resize" : "default",
                              background: active ? "#171B2E" : "rgba(255,255,255,.03)",
                              border: `1px solid ${active ? LINE : "transparent"}` }}>
                  {active && (
                    <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: `${p.vel[i] * 100}%`,
                                  borderRadius: 7, background: eng ? "linear-gradient(180deg,#6F79AE,#4D5788)" : "#323A5C",
                                  filter: cur === i ? "brightness(1.4)" : "none", transition: "filter .06s" }}>
                      <div style={{ margin: "6px auto 0", width: 14, height: 5,
                                    borderTop: "2px solid rgba(255,255,255,.35)",
                                    borderBottom: "2px solid rgba(255,255,255,.35)" }} />
                    </div>
                  )}
                </div>
                <button onClick={() => active && set((s) => ({ ...s, on: s.on.map((x, k) => (k === i ? !x : x)) }))}
                        style={{ height: 34, width: "100%", borderRadius: 9, cursor: active ? "pointer" : "default",
                                 fontFamily: FONT, fontSize: 12.5, fontWeight: 800, opacity: active ? 1 : .35,
                                 border: `1px solid ${active && eng ? "rgba(255,255,255,.16)" : LINE}`,
                                 background: active && eng ? "linear-gradient(180deg,#6B75A8,#505A8C)" : "#1F2539",
                                 color: "#E7ECFB" }}>{i + 1}</button>
              </div>
            );
          })}
        </div>
      </div>

      <div style={{ height: 16, flex: "none", display: "flex", alignItems: "center", justifyContent: "center", gap: 5 }}>
        {Array.from({ length: 16 }).map((_, i) => (
          <span key={i} style={{ width: cur === i ? 7 : 5, height: cur === i ? 7 : 5, borderRadius: 4,
                                 background: cur === i ? IN1 : (i % 4 === 0 ? "#3E4658" : "#2A3040"),
                                 boxShadow: cur === i ? `0 0 8px ${IN1}` : "none", transition: "all .08s" }} />
        ))}
      </div>
    </div>
  );
}

function GenericPanel({ fx, on }) {
  return (
    <div style={{ height: "100%", display: "grid", placeItems: "center",
                  ...well({ borderRadius: 15, position: "relative", overflow: "hidden" }) }}>
      <div style={{ position: "absolute", inset: 0,
                    background: `radial-gradient(ellipse at 50% 0%, ${hx(fx.accent, .12)}, transparent 66%)` }} />
      <div style={{ textAlign: "center", position: "relative" }}>
        <div style={{ width: 52, height: 52, margin: "0 auto 12px", borderRadius: 15, display: "grid", placeItems: "center",
                      color: INK, background: `radial-gradient(circle at 50% 22%, ${hx(fx.accent, .26)}, #141A28 80%)`,
                      border: `1px solid ${hx(fx.accent, .34)}` }}>
          <Ico d={fx.icon} size={23} sw={1.9} />
        </div>
        <div style={{ fontSize: 15, fontWeight: 800, color: on ? INK : MUTE }}>{fx.name}</div>
        <div style={{ fontSize: 10, fontWeight: 800, letterSpacing: ".13em", textTransform: "uppercase",
                      color: fx.accent, marginTop: 4 }}>{fx.type}</div>
        <div style={{ fontSize: 11, color: MUTE, marginTop: 10, maxWidth: 340, lineHeight: 1.6 }}>
          {fx.params ? `${fx.params.length} parameters` : "Panel not specified yet"}
        </div>
      </div>
    </div>
  );
}

/* ══ LA FENÊTRE ═════════════════════════════════════════════════════════════ */
const HEAD = 46, PAD = 12, BODY_H = 411 - HEAD - PAD * 2;

function FxWindow() {
  const [midi, setMidi] = useState(() => ({ fx: MIDI[0], on: true }));
  const [audio, setAudio] = useState(() => [{ fx: AUDIO[0], on: true }]);
  const [view, setView] = useState(() => {
    const h = (typeof location !== "undefined" ? location.hash : "").replace("#", "");
    if (h === "midi") return { k: "catalog", kind: "midi" };
    if (h === "audio") return { k: "catalog", kind: "audio" };
    if (h === "arp") return { k: "effect", fx: MIDI[0], isMidi: true };
    if (h === "space") return { k: "effect", fx: AUDIO[0] };
    return { k: "rack" };
  });
  const [preset, setPreset] = useState("Hall");
  const [vals, setVals] = useState(() => Object.fromEntries(
    AUDIO[0].params.map(([n, v]) => [n.toLowerCase().replace(/[^a-z]/g, ""), v])));
  const [t, setT] = useState(0);
  const [arp, setArp] = useState(() => ({
    sub: 2, style: "up", steps: 16,
    vel: Array.from({ length: 16 }, (_, i) => (i % 4 === 0 ? 1 : i % 2 === 0 ? .78 : .62)),
    on: Array(16).fill(true),
  }));
  const TABLE = { Hall: [.62, .7, .2, .45, .8, .35, .5], Plate: [.4, .5, .1, .7, .9, .3, .7],
                  Room: [.25, .3, .05, .5, .6, .25, .3], Cathedral: [.95, .95, .35, .3, 1, .45, .8],
                  Shimmer: [.7, .85, .25, .2, 1, .5, .9], Chamber: [.35, .4, .08, .55, .7, .28, .45],
                  Bloom: [.8, .8, .3, .35, .95, .42, 1], Infinite: [1, 1, .4, .25, 1, .6, .95] };
  const pickPreset = (n) => {
    setPreset(n);
    const row = TABLE[n];
    if (row) setVals(Object.fromEntries(AUDIO[0].params.map(([nm], i) => [nm.toLowerCase().replace(/[^a-z]/g, ""), row[i]])));
  };

  useEffect(() => {
    const id = setInterval(() => setT((x) => x + 1), 110);
    return () => clearInterval(id);
  }, []);
  const cur = Math.floor(t / 2) % arp.steps;

  const title = view.k === "effect" ? view.fx.name : view.k === "catalog" ? "Add effect" : "Effects";

  return (
    <div data-shot="fxwin" style={{ position: "relative", width: 891, height: 411, borderRadius: 20, overflow: "hidden",
                  fontFamily: FONT, color: INK, background: `linear-gradient(180deg,${WIN_T},${WIN_B})`,
                  border: `1px solid ${LINE2}`, boxShadow: "0 24px 50px rgba(0,0,0,.7), inset 0 1px 0 rgba(255,255,255,.08)",
                  userSelect: "none", WebkitFontSmoothing: "antialiased", display: "flex", flexDirection: "column" }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: .05, mixBlendMode: "overlay", pointerEvents: "none" }} />

      {/* BARRE DE TITRE */}
      <div style={{ height: HEAD, flex: "none", display: "flex", alignItems: "center", gap: 10, padding: "0 12px",
                    position: "relative", zIndex: 2 }}>
        {view.k !== "rack" ? (
          <button onClick={() => setView({ k: "rack" })} style={{ ...ghost({ width: 28, height: 28, display: "grid",
                   placeItems: "center", color: DIM, cursor: "pointer" }) }}><Ico d={P.back} size={14} /></button>
        ) : (
          <span style={{ width: 4, height: 24, borderRadius: 2, background: "#7F9BC7", flex: "none" }} />
        )}
        <span style={{ fontSize: 13, fontWeight: 900, letterSpacing: ".02em" }}>{title}</span>
        {view.k === "rack" && (
          <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase", color: MUTE }}>
            {audio.length + (midi.fx ? 1 : 0)} / {MAX_AUDIO + MAX_MIDI}
          </span>
        )}
        <div style={{ flex: 1 }} />
        {view.k === "rack" && audio.length < MAX_AUDIO && (
          <button onClick={() => setView({ k: "catalog", kind: "audio" })}
                  style={{ ...ghost({ height: 28, padding: "0 12px", display: "flex", alignItems: "center", gap: 6,
                           color: INK, fontSize: 11, fontWeight: 800, fontFamily: FONT, cursor: "pointer" }) }}>
            <Ico d={P.plus} size={13} sw={2.4} /> Effect
          </button>
        )}
        <button title="Close" style={{ ...ghost({ width: 28, height: 28, display: "grid", placeItems: "center",
                 color: DIM, cursor: "pointer" }) }}><Ico d={P.close} size={14} /></button>
      </div>

      {/* CORPS */}
      <div style={{ flex: 1, minHeight: 0, padding: `0 ${PAD}px ${PAD}px`, position: "relative", zIndex: 2 }}>
        {view.k === "rack" && (
          <div style={{ display: "flex", gap: 10, height: "100%" }}>
            {midi.fx
              ? <FxCard fx={midi.fx} midi height={BODY_H} on={midi.on}
                        onToggle={() => setMidi((m) => ({ ...m, on: !m.on }))}
                        onOpen={() => setView({ k: "effect", fx: midi.fx, isMidi: true })}
                        onRemove={() => setMidi({ fx: null, on: true })} />
              : <EmptySlot height={BODY_H} label="MIDI" onAdd={() => setView({ k: "catalog", kind: "midi" })} />}

            {Array.from({ length: MAX_AUDIO }, (_, i) => {
              const a = audio[i];
              return a
                ? <FxCard key={a.fx.id} fx={a.fx} height={BODY_H} on={a.on}
                          onToggle={() => setAudio((s) => s.map((x, k) => k === i ? { ...x, on: !x.on } : x))}
                          onOpen={() => setView({ k: "effect", fx: a.fx })}
                          onRemove={() => setAudio((s) => s.filter((_, k) => k !== i))} />
                : <EmptySlot key={"e" + i} height={BODY_H} label="Audio"
                             onAdd={() => setView({ k: "catalog", kind: "audio" })} />;
            })}
          </div>
        )}

        {view.k === "catalog" && (
          <Catalog kind={view.kind} onBack={() => setView({ k: "rack" })}
                   full={view.kind === "audio" && audio.length >= MAX_AUDIO}
                   onPick={(f) => {
                     if (view.kind === "midi") { setMidi({ fx: f, on: true }); setView({ k: "effect", fx: f, isMidi: true }); }
                     else { setAudio((s) => [...s, { fx: f, on: true }]); setView({ k: "effect", fx: f }); }
                   }} />
        )}

        {view.k === "effect" && (
          view.fx.space
            ? <SpacePanel fx={view.fx} vals={vals} setVal={(k, v) => setVals((s) => ({ ...s, [k]: v }))}
                          preset={preset} setPreset={pickPreset} on={audio[0].on}
                          onToggle={() => setAudio((s) => s.map((x, k) => k === 0 ? { ...x, on: !x.on } : x))} />
            : view.fx.type === "arp"
              ? <ArpPanel p={arp} set={setArp} cur={cur} />
              : <GenericPanel fx={view.fx} on />
        )}
      </div>
    </div>
  );
}

/* ── habillage : l'écran de l'app, jamais agrandi ─────────────────────────── */
export default function FxWindowPreview() {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const fit = () => setScale(Math.min(1, clamp(Math.min((window.innerWidth - 60) / 891,
                                                          (window.innerHeight - 120) / 411), .35, 1)));
    fit(); window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, []);
  return (
    <div style={{ minHeight: "100vh", background: "radial-gradient(ellipse at 50% 0%, #141B27, #0A0E16 70%)",
                  display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
                  gap: 14, padding: 16, boxSizing: "border-box", fontFamily: FONT }}>
      <div style={{ width: 891 * scale, height: 411 * scale, position: "relative" }}>
        <div style={{ width: 891, height: 411, transform: `scale(${scale})`, transformOrigin: "top left",
                      position: "absolute", left: 0, top: 0, borderRadius: 20, overflow: "hidden",
                      boxShadow: "0 34px 90px rgba(0,0,0,.72), 0 0 0 1px rgba(255,255,255,.06)" }}>
          <FxWindow />
        </div>
      </div>
      <div style={{ fontSize: 11.5, color: "#5B6580", textAlign: "center", maxWidth: 820, lineHeight: 1.65 }}>
        Æther FX — écran 891 × 411. Space occupe le corps de la fenêtre ; l'Arpeggiator est l'interface
        réelle de FxModesPreview ; la feuille d'ajout suit fx-window-preview.html.
      </div>
    </div>
  );
}
