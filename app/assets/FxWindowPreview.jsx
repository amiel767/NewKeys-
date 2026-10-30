import { useEffect, useRef, useState } from "react";

/* ═════════════════════════════════════════════════════════════════════════════
   ÆTHER FX — nouvelle fenêtre d'effets.

   CE QUI EST REPRIS, ET D'OÙ

   · Couleurs = AppColors.kt, les vraies de l'application :
       Accent      #8FB2E8   (l'accent de l'app est BLEU, pas violet)
       AccentDeep  #7FA3DD
       Ink         #E9EDF5   Soft #DCE2EE   Mute #8690A4   Dim #6F7A90
       PanelTop    #232C3E   PanelBottom #1A2230   Well #0F141D
       BgTop       #141B27   BgBottom #0F141D

   · Matière = fenêtre Track Settings : radius 22, dégradé #232C3E → #1A2230,
     bordure 1px blanc 10 %, puits #0C1119 → #0F1520 à ombre interne, bouton
     #2F3850 → #252D40 à liseré haut 10 %.

   · Navigateur de listes = AeWindow.kt → PresetList / CatRow, repris au plus
     près (voir CatalogPage) : panneau #151B29 → #0A0E17, filet clair en haut et
     sombre en bas, rail de catégories sur fond noir 8 %, ligne de catégorie
     26 de haut avec barre d'accent 2.5 × 14 et compteur mono, ligne de liste 28
     de haut, dégradé d'accent à 22 % pour la sélection, alternance 3 % / 1 %.

   · Règles du projet (FxModes.kt) : FxLogic.MAX_AUDIO = 3 effets audio au
     maximum, UN seul slot MIDI par piste (le choisir remplace le courant).

   · Géométrie du panneau Æther Space = FxGeometry.kt / FxKnobBoxes, les 7
     positions réelles. Elles dessinent une ELLIPSE : les potards sont répartis
     autour du centre (385, 130), pas en grille. C'est la disposition d'origine,
     on la garde telle quelle.

   LE SLOT CREUX (semi-3D)
   Quatre couches, dans l'ordre où la lumière les atteint : joues latérales →
   fond très sombre avec ombre portée interne en haut → lèvre basse ÉCLAIRÉE (la
   lumière vient du haut, donc le bas du creux la renvoie : c'est ce qui donne la
   profondeur) → cartouche posée au fond. Un logement vide ne montre que les
   trois premières couches.

   LA TAILLE
   Chaque effet déclare la taille de son panneau. La fenêtre s'y ajuste, et reste
   redimensionnable (poignée à droite, poignée de coin). « Auto » reprend la
   taille déclarée.
   ═════════════════════════════════════════════════════════════════════════════ */

const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const FONT = "Space Grotesk, Inter, system-ui, -apple-system, sans-serif";

/* ── AppColors.kt ──────────────────────────────────────────────────────────── */
const ACC = "#8FB2E8", ACC_DEEP = "#7FA3DD";
const INK = "#E9EDF5", SOFT = "#DCE2EE", MUTE = "#8690A4", DIM = "#6F7A90";
const PANEL_T = "#232C3E", PANEL_B = "#1A2230";
const WELL_A = "#0C1119", WELL_B = "#0F1520";
const BTN_T = "#2F3850", BTN_B = "#252D40";
const BG_T = "#141B27", BG_B = "#0F141D";
const LINE = "rgba(255,255,255,.06)", LINE2 = "rgba(255,255,255,.1)";
const ACC_TEXT = "#0F1726";

const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='140' height='140'>" +
  "<filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='3' stitchTiles='stitch'/>" +
  "<feColorMatrix type='saturate' values='0'/></filter>" +
  "<rect width='100%' height='100%' filter='url(#n)'/></svg>"
)}")`;

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const hx = (c, a) => {
  const n = parseInt(c.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
};

/* ── les effets réels (FxRegistry.kt, FxKind.EFFECT) ───────────────────────── */
const AUDIO_FX = [
  { id: 0,  name: "Æther Space",   type: "Reverb",     cat: "Time",     pw: 7, w: 857, h: 437, note: "Size, Decay, Pre-delay, Damp, Width, Mix, Diffuse" },
  { id: 1,  name: "Æther Echo",    type: "Delay",      cat: "Time",     pw: 4, w: 760, h: 400, note: "Feedback, Tone, Wow, Mix" },
  { id: 2,  name: "Prism Tone",    type: "Equalizer",  cat: "Tone",     pw: 4, w: 800, h: 408, note: "Low, Mid, High, Mid Q" },
  { id: 3,  name: "Iron Grip",     type: "Compressor", cat: "Dynamics", pw: 5, w: 820, h: 412, note: "Ratio, Knee, Attack, Release, Makeup" },
  { id: 4,  name: "Solar Ceiling", type: "Maximizer",  cat: "Dynamics", pw: 3, w: 700, h: 394, note: "Amount, Ceiling, Release" },
  { id: 10, name: "Æther Amp",     type: "Amp",        cat: "Drive",    pw: 6, w: 820, h: 412, note: "Drive, Bass, Mid, Treble, Presence, Level" },
  { id: 11, name: "Æther Bass",    type: "Amp",        cat: "Drive",    pw: 6, w: 820, h: 412, note: "Drive, Body, Mid, Treble, Presence, Level" },
  { id: 13, name: "Æther Voices",  type: "Voice",      cat: "Pitch",    pw: 5, w: 780, h: 406, note: "Voices, Detune, Formant, Width, Mix" },
];
/* ── les 5 effets MIDI (FxModes.kt) ────────────────────────────────────────── */
const MIDI_FX = [
  { id: 101, name: "Arpeggiator",     type: "MIDI", cat: "Pattern", pw: 3, w: 720, h: 394, note: "Subdivision, Style, Steps 1–16" },
  { id: 102, name: "Sequencer",       type: "MIDI", cat: "Pattern", pw: 4, w: 860, h: 428, note: "16 pas, vélocité par pas" },
  { id: 103, name: "Easy Chord",      type: "MIDI", cat: "Harmony", pw: 2, w: 660, h: 388, note: "Entrée, Sortie" },
  { id: 104, name: "Chord Sequencer", type: "MIDI", cat: "Harmony", pw: 4, w: 840, h: 422, note: "Tonalité, Gamme, Progression" },
  { id: 105, name: "Octave Up",       type: "MIDI", cat: "Pitch",   pw: 1, w: 600, h: 368, note: "Blend" },
];

const MAX_AUDIO = 3, MAX_MIDI = 1;

/* ── Æther Space : positions réelles (FxGeometry.kt / FxKnobBoxes[0]) ────────
   x, y = centre du potard dans l'espace de contenu 825 × 343 ; d = diamètre.
   ─────────────────────────────────────────────────────────────────────────── */
const SPACE_W = 825, SPACE_H = 343;
const SPACE_KNOBS = [
  { k: "size",  name: "Size",      x: 387.8, y: 53.8,  d: 54.8, def: 0.62 },
  { k: "decay", name: "Decay",     x: 614.1, y: 84.4,  d: 54.8, def: 0.70 },
  { k: "pre",   name: "Pre-delay", x: 665.3, y: 153.1, d: 64.0, def: 0.20 },
  { k: "damp",  name: "Damp",      x: 513.4, y: 208.2, d: 54.8, def: 0.45 },
  { k: "width", name: "Width",     x: 262.3, y: 208.2, d: 54.8, def: 0.80 },
  { k: "mix",   name: "Mix",       x: 105.7, y: 153.1, d: 54.8, def: 0.35 },
  { k: "diff",  name: "Diffuse",   x: 161.6, y: 84.4,  d: 54.8, def: 0.50 },
];
const SPACE_UNITS = { decay: "s", damp: "kHz", pre: "ms" };
const spaceFmt = (k, v) => {
  const r = k === "decay" ? 0.4 + v * 7.6 : k === "damp" ? 1 + v * 11 : k === "pre" ? v * 120 : v * 100;
  if (k === "decay") return r.toFixed(2) + " s";
  if (k === "damp") return r.toFixed(1) + " kHz";
  if (k === "pre") return Math.round(r) + " ms";
  return Math.round(r) + " %";
};

/* ── icônes, style Lucide (grille 24, trait 2, bouts ronds) ─────────────────── */
const Ico = ({ d, size = 16, sw = 2 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ display: "block", flex: "none" }}>
    {(Array.isArray(d) ? d : [d]).map((p, i) => <path key={i} d={p} />)}
  </svg>
);
const IC = {
  close: ["M18 6L6 18", "M6 6l12 12"], plus: ["M12 5v14", "M5 12h14"],
  power: ["M12 2v10", "M18.36 6.64a9 9 0 1 1-12.73 0"],
  chevronLeft: "M15 18l-6-6 6-6", trash: ["M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6"],
  drag: ["M9 6h.01", "M15 6h.01", "M9 12h.01", "M15 12h.01", "M9 18h.01", "M15 18h.01"],
  grid: ["M3 3h7v7H3z", "M14 3h7v7h-7z", "M14 14h7v7h-7z", "M3 14h7v7H3z"],
  midi: ["M9 18V5l12-2v13", "M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M18 19a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"],
  wave: "M2 12c2 0 2-4 4-4s2 8 4 8 2-8 4-8 2 4 4 4",
  sliders: ["M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3",
            "M1 14h6", "M9 8h6", "M17 16h6"],
};

/* ── surfaces ──────────────────────────────────────────────────────────────── */
const win = (x = {}) => ({ background: `linear-gradient(180deg,${PANEL_T},${PANEL_B})`, ...x });
const well = (x = {}) => ({ background: `linear-gradient(180deg,${WELL_A},${WELL_B})`,
  boxShadow: "inset 0 3px 12px rgba(0,0,0,.6), inset 0 0 0 1px rgba(255,255,255,.04)", ...x });
const btn = (x = {}) => ({ background: `linear-gradient(180deg,${BTN_T},${BTN_B})`, border: `1px solid ${LINE2}`,
  boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 2px 5px rgba(0,0,0,.35)", ...x });

/* ══ LE SLOT CREUX ═══════════════════════════════════════════════════════════ */
function HollowSlot({ height = 92, children, filled, accent = ACC, radius = 15, style }) {
  return (
    <div style={{ position: "relative", height, borderRadius: radius, padding: 3,
                  background: "linear-gradient(180deg,rgba(0,0,0,.5),rgba(255,255,255,.035))",
                  boxShadow: "0 1px 0 rgba(255,255,255,.05)", ...style }}>
      <div style={{ position: "absolute", inset: 3, borderRadius: radius - 3,
                    background: "linear-gradient(180deg,#05080E,#0A0E18)",
                    boxShadow: "inset 0 5px 12px rgba(0,0,0,.85), inset 0 -1px 0 rgba(255,255,255,.055), inset 2px 0 5px rgba(0,0,0,.5), inset -2px 0 5px rgba(0,0,0,.5)" }} />
      <div style={{ position: "absolute", left: 6, right: 6, bottom: 3.5, height: 1.4, borderRadius: 1,
                    background: `linear-gradient(90deg,transparent,${filled ? hx(accent, .34) : "rgba(255,255,255,.14)"},transparent)`,
                    filter: "blur(.3px)" }} />
      <div style={{ position: "absolute", inset: 6, borderRadius: radius - 6, display: "flex",
                    alignItems: "center", overflow: "hidden" }}>
        {children}
      </div>
    </div>
  );
}

/* ══ LA CARTOUCHE ════════════════════════════════════════════════════════════
   Toute la carte ouvre le panneau de l'effet. Pas de bouton « ouvrir » : c'est
   la carte elle-même qu'on touche. Le bouton d'alimentation et la corbeille
   restent, mais ils arrêtent la propagation pour ne pas ouvrir en même temps.
   ─────────────────────────────────────────────────────────────────────────── */
function Cartridge({ fx, on, onToggle, onOpen, onRemove, kind, tall, params }) {
  const acc = ACC;   // l'accent de l'app, pour toutes les cartes
  if (tall) return (
    <div onClick={onOpen} title="Open"
         style={{ position: "relative", flex: 1, height: "100%", borderRadius: 11, cursor: "pointer",
                  display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
                  gap: 10, padding: 14,
                  background: on
                    ? `linear-gradient(180deg,${hx(acc, .15)},${hx(acc, .04)}), linear-gradient(180deg,#28304A,#1C2336)`
                    : "linear-gradient(180deg,#1B2133,#151A28)",
                  border: `1px solid ${on ? hx(acc, .4) : "rgba(255,255,255,.05)"}`,
                  boxShadow: on ? `inset 0 1px 0 rgba(255,255,255,.13), 0 4px 12px rgba(0,0,0,.5)` : "none" }}>
      <div style={{ position: "absolute", left: 0, top: 10, bottom: 10, width: 2, borderRadius: 2,
                    background: `linear-gradient(180deg,${acc},${hx(acc, .27)})`, opacity: on ? 1 : 0.35 }} />
      <div style={{ width: 44, height: 44, borderRadius: 13, display: "grid", placeItems: "center",
                    color: on ? SOFT : MUTE,
                    background: `radial-gradient(circle at 50% 22%, ${hx(acc, .23)}, #141A28 78%)`,
                    border: `1px solid ${on ? hx(acc, .33) : "rgba(255,255,255,.06)"}`,
                    boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 2px 6px rgba(0,0,0,.5)" }}>
        <Ico d={kind === "midi" ? IC.midi : IC.wave} size={20} sw={2.1} />
      </div>
      <div style={{ textAlign: "center" }}>
        <div style={{ fontSize: 14, fontWeight: 800, color: on ? INK : MUTE }}>{fx.name}</div>
        <div style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".1em", textTransform: "uppercase",
                      color: on ? ACC : DIM, marginTop: 2 }}>{fx.type}</div>
      </div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 5, justifyContent: "center", maxWidth: 230 }}>
        {(params || []).map((t) => (
          <span key={t} style={{ padding: "3px 8px", borderRadius: 7, fontSize: 9.5, fontWeight: 700,
                                 color: DIM, background: "rgba(255,255,255,.045)",
                                 border: `1px solid ${LINE}` }}>{t}</span>
        ))}
      </div>
      <div style={{ position: "absolute", right: 9, top: 9, display: "flex", gap: 6 }}>
        <button onClick={(e) => { e.stopPropagation(); onToggle(); }} title="Engage / Bypass"
                style={{ width: 28, height: 28, borderRadius: 9, cursor: "pointer", display: "grid",
                         placeItems: "center", border: `1px solid ${on ? hx(ACC, .53) : LINE2}`,
                         background: on ? `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})` : `linear-gradient(180deg,${BTN_T},${BTN_B})`,
                         color: on ? ACC_TEXT : DIM }}>
          <Ico d={IC.power} size={13} sw={2.3} />
        </button>
        <button onClick={(e) => { e.stopPropagation(); onRemove(); }} title="Remove"
                style={{ ...btn({ width: 28, height: 28, borderRadius: 9, display: "grid", placeItems: "center",
                         color: DIM, cursor: "pointer" }) }}>
          <Ico d={IC.trash} size={13} />
        </button>
      </div>
    </div>
  );
  return (
    <div onClick={onOpen} title="Open"
         style={{ position: "relative", flex: 1, height: "100%", borderRadius: 11, display: "flex",
                  alignItems: "center", gap: 11, padding: "0 10px 0 9px", cursor: "pointer",
                  background: on
                    ? `linear-gradient(180deg,${hx(acc, .15)},${hx(acc, .05)}), linear-gradient(180deg,#28304A,#1C2336)`
                    : "linear-gradient(180deg,#1B2133,#151A28)",
                  border: `1px solid ${on ? hx(acc, .4) : "rgba(255,255,255,.05)"}`,
                  boxShadow: on
                    ? `inset 0 1px 0 rgba(255,255,255,.13), 0 4px 12px rgba(0,0,0,.5), 0 0 18px ${hx(acc, .13)}`
                    : "inset 0 1px 0 rgba(255,255,255,.05), 0 3px 8px rgba(0,0,0,.45)" }}>
      <div style={{ position: "absolute", left: 0, top: 6, bottom: 6, width: 2, borderRadius: 2,
                    background: `linear-gradient(180deg,${acc},${hx(acc, .27)})`, opacity: on ? 1 : 0.35 }} />

      <div style={{ width: 34, height: 34, borderRadius: 10, flex: "none", display: "grid", placeItems: "center",
                    color: on ? SOFT : MUTE,
                    background: `radial-gradient(circle at 50% 22%, ${hx(acc, .23)}, #141A28 78%)`,
                    border: `1px solid ${on ? hx(acc, .33) : "rgba(255,255,255,.06)"}`,
                    boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 2px 6px rgba(0,0,0,.5)" }}>
        <Ico d={kind === "midi" ? IC.midi : IC.wave} size={16} sw={2.1} />
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 2, minWidth: 0, flex: 1 }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
          <span style={{ fontSize: 13.5, fontWeight: 800, color: on ? INK : MUTE, whiteSpace: "nowrap",
                         overflow: "hidden", textOverflow: "ellipsis" }}>{fx.name}</span>
          <span style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".1em", textTransform: "uppercase",
                         color: on ? ACC : DIM }}>{fx.type}</span>
        </div>
        <span style={{ fontSize: 10.5, color: DIM, whiteSpace: "nowrap", overflow: "hidden",
                       textOverflow: "ellipsis" }}>{fx.note}</span>
      </div>

      <button onClick={(e) => { e.stopPropagation(); onToggle(); }} title="Engage / Bypass"
              style={{ width: 30, height: 30, borderRadius: 9, cursor: "pointer", display: "grid",
                       placeItems: "center", flex: "none", border: `1px solid ${on ? hx(ACC, .53) : LINE2}`,
                       background: on ? `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})` : `linear-gradient(180deg,${BTN_T},${BTN_B})`,
                       color: on ? ACC_TEXT : DIM,
                       boxShadow: on ? `0 0 13px ${hx(ACC, .33)}, inset 0 1px 0 rgba(255,255,255,.4)` : "inset 0 1px 0 rgba(255,255,255,.1)",
                       transition: "all .2s cubic-bezier(.3,1.4,.5,1)" }}>
        <Ico d={IC.power} size={14} sw={2.3} />
      </button>
      <button onClick={(e) => { e.stopPropagation(); onRemove(); }} title="Remove"
              style={{ ...btn({ width: 30, height: 30, borderRadius: 9, display: "grid", placeItems: "center",
                       color: DIM, cursor: "pointer", flex: "none" }) }}>
        <Ico d={IC.trash} size={14} />
      </button>
    </div>
  );
}

function EmptySlot({ label, height }) {
  return (
    <HollowSlot height={height}>
      <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 7,
                    fontSize: 10.5, fontWeight: 700, letterSpacing: ".12em", color: "#3A4258",
                    textTransform: "uppercase" }}>
        <Ico d={IC.plus} size={12} sw={2.4} /> {label}
      </div>
    </HollowSlot>
  );
}

function AddTile({ label, onClick, disabled, height = 42 }) {
  return (
    <button onClick={onClick} disabled={disabled}
            style={{ height, width: "100%", borderRadius: 13, cursor: disabled ? "default" : "pointer",
                     display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
                     fontFamily: FONT, fontSize: 12, fontWeight: 800, letterSpacing: ".03em",
                     color: disabled ? DIM : INK, opacity: disabled ? 0.5 : 1,
                     background: "linear-gradient(180deg,rgba(255,255,255,.075),rgba(255,255,255,.03))",
                     border: `1px dashed ${disabled ? "rgba(255,255,255,.09)" : hx(ACC, .4)}`,
                     boxShadow: disabled ? "none" : "inset 0 1px 0 rgba(255,255,255,.08)",
                     transition: "all .18s" }}>
      <Ico d={IC.plus} size={14} sw={2.5} /> {label}
    </button>
  );
}

/* ══ LA PAGE D'UN EFFET ══════════════════════════════════════════════════════
   Le panneau Æther Space, aux positions réelles de FxGeometry.kt : les 7 potards
   sont répartis en ellipse autour du centre (385, 130), pas en grille.
   ─────────────────────────────────────────────────────────────────────────── */
function SpaceKnob({ k, name, x, y, d, v, onChange }) {
  const drag = useRef(null);
  const [hot, setHot] = useState(false);
  const ang = -135 + v * 270;
  const r = d / 2, R = r - 4, circ = 2 * Math.PI * R, arc = circ * 0.75;
  return (
    <div style={{ position: "absolute", left: x - r, top: y - r, width: d, height: d }}>
      <div onPointerDown={(e) => { drag.current = { y: e.clientY, v }; setHot(true); e.currentTarget.setPointerCapture?.(e.pointerId); }}
           onPointerMove={(e) => { if (drag.current) onChange(clamp(drag.current.v + (drag.current.y - e.clientY) / 180, 0, 1)); }}
           onPointerUp={() => { drag.current = null; setHot(false); }}
           onPointerCancel={() => { drag.current = null; setHot(false); }}
           style={{ width: d, height: d, position: "relative", cursor: "ns-resize", touchAction: "none" }}>
        <svg width={d} height={d} style={{ display: "block", transform: "rotate(135deg)" }}>
          <circle cx={r} cy={r} r={R} fill="none" stroke="rgba(255,255,255,.06)" strokeWidth={3}
                  strokeDasharray={`${arc} ${circ}`} strokeLinecap="round" />
          <circle cx={r} cy={r} r={R} fill="none" stroke={ACC} strokeWidth={3}
                  strokeDasharray={`${arc * v} ${circ}`} strokeLinecap="round"
                  style={{ filter: hot ? `drop-shadow(0 0 4px ${ACC})` : "none" }} />
        </svg>
        <div style={{ position: "absolute", inset: 9, borderRadius: "50%",
                      background: "radial-gradient(circle at 50% 16%, #333D5E 0%, #232B45 42%, #141828 100%)",
                      border: `1px solid ${hot ? hx(ACC, .5) : "rgba(255,255,255,.09)"}`,
                      boxShadow: `inset 0 1px 0 rgba(255,255,255,.13), inset 0 -3px 8px rgba(0,0,0,.5), 0 4px 10px rgba(0,0,0,.5)${hot ? `, 0 0 16px ${hx(ACC, .33)}` : ""}`,
                      display: "grid", placeItems: "center" }}>
          <div style={{ width: 2.6, height: d * 0.25, borderRadius: 2,
                        background: `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})`,
                        transform: `rotate(${ang}deg) translateY(-${d * 0.115}px)`, transformOrigin: "50% 100%",
                        boxShadow: `0 0 7px ${hx(ACC, .66)}` }} />
        </div>
      </div>
      <div style={{ position: "absolute", top: d + 2, left: -20, right: -20, textAlign: "center",
                    fontSize: 9, fontWeight: 700, letterSpacing: ".07em", color: DIM, textTransform: "uppercase" }}>{name}</div>
      <div style={{ position: "absolute", top: d + 14, left: -20, right: -20, textAlign: "center",
                    fontSize: 11, fontWeight: 700, color: INK, fontFamily: MONO }}>{spaceFmt(k, v)}</div>
    </div>
  );
}

function SpacePanel({ vals, onChange, scale }) {
  return (
    <div style={{ width: SPACE_W * scale, height: SPACE_H * scale, position: "relative" }}>
      <div style={{ width: SPACE_W, height: SPACE_H, transform: `scale(${scale})`, transformOrigin: "top left",
                    position: "absolute", left: 0, top: 0, borderRadius: 14, ...well({ borderRadius: 14,
                    position: "absolute", overflow: "hidden" }) }}>
        {/* halo central : la salle s'éclaire depuis son milieu */}
        <div style={{ position: "absolute", inset: 0,
                      background: `radial-gradient(ellipse at 47% 38%, ${hx(ACC, .1)}, transparent 62%)` }} />
        {SPACE_KNOBS.map((kn) => (
          <SpaceKnob key={kn.k} {...kn} v={vals[kn.k]} onChange={onChange(kn.k)} />
        ))}
      </div>
    </div>
  );
}

/* ══ LA PAGE D'UNE LISTE ═════════════════════════════════════════════════════
   Texture et structure reprises de AeWindow.kt → PresetList / CatRow :
     panneau #151B29 → #0A0E17, clip 18, filet clair en haut (blanc 15 %,
     rentré de 14) et filet sombre en bas (noir 40 %, rentré de 14) ;
     en-tête : « EFFECTS » + titre + « n of m » en mono ;
     rail de catégories 214 de large sur fond noir 8 %, libellé CATEGORY,
     ligne de catégorie 26 de haut, rayon 7, fond accent 18 % si active,
     barre d'accent 2.5 × 14, nom en capitales, compteur mono à droite ;
     ligne de liste 28 de haut, rayon 7, sélection en dégradé d'accent 22 %,
     alternance blanc 3 % / 1 %, barre d'accent 3 × 15, index mono aligné à
     droite sur 20, puis le nom.
   ─────────────────────────────────────────────────────────────────────────── */
function CatalogPage({ kind, onPick, onBack, full }) {
  const list = kind === "midi" ? MIDI_FX : AUDIO_FX;
  const cats = [...new Set(list.map((f) => f.cat))];
  const [cat, setCat] = useState(null);
  const [sel, setSel] = useState(0);
  const shown = cat ? list.filter((f) => f.cat === cat) : list;
  const acc = ACC;

  const CatRow = ({ label, count, on, isAll, onClick }) => (
    <div onClick={onClick} style={{ height: 26, borderRadius: 7, cursor: "pointer", display: "flex",
         alignItems: "center", background: on ? hx(acc, .18) : "transparent" }}>
      <div style={{ marginLeft: 3, width: 2.5, height: 14, borderRadius: 2,
                    background: on ? acc : "transparent" }} />
      <span style={{ marginLeft: 8, flex: 1, fontSize: isAll ? 9 : 8.5, fontWeight: on ? 800 : 600,
                     letterSpacing: ".12em", textTransform: "uppercase", color: on ? "#FFFFFF" : MUTE }}>{label}</span>
      <span style={{ marginRight: 9, fontSize: 9, fontFamily: MONO, color: on ? acc : MUTE }}>{count}</span>
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "0 2px 9px" }}>
        <button onClick={onBack} style={{ ...btn({ width: 26, height: 26, borderRadius: 8, display: "grid",
                 placeItems: "center", color: MUTE, cursor: "pointer" }) }}>
          <Ico d={IC.chevronLeft} size={13} />
        </button>
        <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: ".18em", color: MUTE, textTransform: "uppercase" }}>
          {kind === "midi" ? "MIDI" : "AUDIO"}
        </span>
        <span style={{ fontSize: 10, color: acc, letterSpacing: ".1em", textTransform: "uppercase", fontWeight: 700 }}>
          {kind === "midi" ? "One per track" : `Up to ${MAX_AUDIO} per track`}
        </span>
        <span style={{ fontSize: 10, color: MUTE, fontFamily: MONO }}>{shown.length} of {list.length}</span>
        <div style={{ flex: 1 }} />
      </div>

      {/* le panneau, exactement la matière de PresetList */}
      <div style={{ flex: 1, minHeight: 0, borderRadius: 18, overflow: "hidden", position: "relative",
                    background: "linear-gradient(180deg,#151B29,#0A0E17)",
                    boxShadow: "inset 0 0 0 1px rgba(255,255,255,.04), 0 10px 30px rgba(0,0,0,.45)" }}>
        <div style={{ position: "absolute", left: 14, right: 14, top: 0, height: 1, background: "rgba(255,255,255,.15)" }} />
        <div style={{ position: "absolute", left: 14, right: 14, bottom: 0, height: 1, background: "rgba(0,0,0,.4)" }} />

        <div style={{ display: "flex", height: "100%", padding: 14, gap: 10 }}>
          {/* rail de catégories */}
          <div style={{ width: 214, flex: "none", display: "flex", flexDirection: "column" }}>
            <span style={{ fontSize: 8, fontWeight: 800, letterSpacing: ".18em", color: MUTE,
                           textTransform: "uppercase", padding: "0 0 6px 12px" }}>Category</span>
            <div style={{ flex: 1, background: "rgba(0,0,0,.08)", borderRadius: 10, padding: "7px 6px",
                          overflowY: "auto" }}>
              <CatRow label="All" count={list.length} on={cat === null} isAll onClick={() => { setCat(null); setSel(0); }} />
              {cats.map((c) => (
                <CatRow key={c} label={c} count={list.filter((f) => f.cat === c).length} on={cat === c}
                        onClick={() => { setCat(c); setSel(0); }} />
              ))}
            </div>
          </div>

          {/* la liste filtrée */}
          <div style={{ flex: 1, minWidth: 0, overflowY: "auto", display: "flex",
                        flexDirection: "column", gap: 1 }}>
            {shown.map((f, i) => {
              const on = i === sel;
              return (
                <div key={f.id} onClick={() => setSel(i)} onDoubleClick={() => onPick(f)}
                     style={{ height: 28, borderRadius: 7, display: "flex", alignItems: "center", flex: "none",
                              cursor: "pointer",
                              background: on ? `linear-gradient(90deg,${hx(acc, .22)},rgba(25,227,255,.03))`
                                             : i % 2 === 0 ? "linear-gradient(90deg,rgba(255,255,255,.028),rgba(255,255,255,.008))"
                                             : "transparent" }}>
                  <div style={{ marginLeft: 4, width: 3, height: 15, borderRadius: 2,
                                background: on ? acc : "transparent" }} />
                  <span style={{ marginLeft: 8, width: 20, textAlign: "right", fontSize: 9, fontFamily: MONO,
                                 color: on ? acc : MUTE }}>{i + 1}</span>
                  <span style={{ marginLeft: 8, flex: 1, fontSize: 11, fontWeight: on ? 700 : 500,
                                 color: on ? "#FFFFFF" : INK, whiteSpace: "nowrap", overflow: "hidden",
                                 textOverflow: "ellipsis" }}>{f.name}</span>
                  <span style={{ fontSize: 9, fontWeight: 700, letterSpacing: ".04em", textTransform: "uppercase",
                                 color: on ? acc : DIM, marginRight: 8 }}>{f.type}</span>
                  {cat === null && <span style={{ fontSize: 9, color: MUTE, marginRight: 8 }}>{f.cat}</span>}
                  <span style={{ fontSize: 9, fontFamily: MONO, color: MUTE, marginRight: 9 }}>{f.pw}p</span>
                  {!full && (
                    <button onClick={(e) => { e.stopPropagation(); onPick(f); }}
                            style={{ ...btn({ width: 22, height: 22, borderRadius: 7, display: "grid",
                                     placeItems: "center", color: MUTE, cursor: "pointer", marginRight: 6 }) }}>
                      <Ico d={IC.plus} size={11} sw={2.5} />
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

function Handle({ pos, onDown }) {
  return <div onPointerDown={onDown} style={{ position: "absolute", zIndex: 6, ...pos,
    cursor: pos.right !== undefined ? "ew-resize" : "nwse-resize" }} />;
}

/* ══ LA FENÊTRE ══════════════════════════════════════════════════════════════ */
function FxWindow() {
  const [audio, setAudio] = useState([AUDIO_FX[0]]);
  const [midi, setMidi] = useState([MIDI_FX[0]]);
  const [on, setOn] = useState({});
  const [view, setView] = useState(() => {
    const h = (typeof location !== "undefined" ? location.hash : "").replace("#", "");
    if (h === "midi") return { k: "catalog", kind: "midi" };
    if (h === "audio") return { k: "catalog", kind: "audio" };
    if (h === "effect-midi") return { k: "effect", fx: MIDI_FX[0] };
    if (h === "effect") return { k: "effect", fx: AUDIO_FX[0] };
    return { k: "rack" };
  });
  const [vals, setVals] = useState(() => Object.fromEntries(SPACE_KNOBS.map((k) => [k.k, k.def])));
  const [size, setSize] = useState({ w: 880, h: 412 });
  const [auto, setAuto] = useState(true);
  const drag = useRef(null);

  const engaged = (f) => on[f.id] !== false;
  const toggle = (f) => setOn((s) => ({ ...s, [f.id]: s[f.id] === false }));
  const setKnob = (k) => (v) => setVals((s) => ({ ...s, [k]: v }));

  useEffect(() => {
    if (view.k === "effect") setSize({ w: view.fx.w, h: view.fx.h });
    else if (view.k === "catalog") setSize({ w: 880, h: 430 });
    else setSize({ w: 880, h: 412 });
    setAuto(true);
  }, [view]);

  useEffect(() => {
    const move = (e) => {
      if (!drag.current) return;
      setAuto(false);
      const { x0, y0, w0, h0, ax, ay } = drag.current;
      setSize({ w: ax ? clamp(w0 + (e.clientX - x0), 560, 1100) : w0,
                h: ay ? clamp(h0 + (e.clientY - y0), 320, 660) : h0 });
    };
    const up = () => (drag.current = null);
    window.addEventListener("pointermove", move); window.addEventListener("pointerup", up);
    return () => { window.removeEventListener("pointermove", move); window.removeEventListener("pointerup", up); };
  }, []);
  const startDrag = (ax, ay) => (e) => { drag.current = { x0: e.clientX, y0: e.clientY, w0: size.w, h0: size.h, ax, ay }; };

  const pick = (f) => {
    if (view.k === "catalog" && view.kind === "midi") setMidi([f]);
    else if (view.k === "catalog" && audio.length < MAX_AUDIO) setAudio((a) => [...a, f]);
    setView({ k: "effect", fx: f });
  };

  const HEAD = 48, FOOT = 46, PAD = 14;
  const bodyH = size.h - HEAD - FOOT - PAD * 2;
  const GAP = 7, LABEL = 16, ADD = 42;
  /* Les logements prennent la place disponible : la colonne audio en tient
     exactement trois plus la tuile Add, sans laisser de vide sous le pied. */
  const slotH = clamp((bodyH - LABEL - ADD - 3 * GAP) / 3, 54, 104);
  /* La colonne MIDI n'a qu'un logement : il prend la hauteur qui reste pour que
     les deux colonnes se terminent au meme niveau. */
  const midiH = Math.max(slotH, bodyH - LABEL - ADD - 2 * GAP);
  const isSpace = view.k === "effect" && view.fx.id === 0;
  const panelScale = isSpace ? clamp(Math.min((size.w - PAD * 2) / SPACE_W, bodyH / SPACE_H), 0.4, 1.6) : 1;

  return (
    <div data-shot="fxwin" style={{ position: "relative", width: size.w, height: size.h, borderRadius: 22,
                  overflow: "hidden", fontFamily: FONT, color: INK, ...win(), border: `1px solid ${LINE2}`,
                  boxShadow: "0 26px 64px rgba(0,0,0,.6), inset 0 1px 0 rgba(255,255,255,.06)",
                  userSelect: "none", WebkitFontSmoothing: "antialiased", transition: "width .22s, height .22s" }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: .028, pointerEvents: "none" }} />

      {/* ══ EN-TÊTE — sobre, une seule ligne ══ */}
      <div style={{ position: "relative", height: HEAD, display: "flex", alignItems: "center", gap: 9,
                    padding: "0 12px", borderBottom: `1px solid ${LINE}`,
                    background: "linear-gradient(180deg,rgba(255,255,255,.03),transparent)" }}>
        {view.k !== "rack" ? (
          <button onClick={() => setView({ k: "rack" })} style={{ ...btn({ width: 26, height: 26, borderRadius: 8,
                   display: "grid", placeItems: "center", color: MUTE, cursor: "pointer" }) }}>
            <Ico d={IC.chevronLeft} size={13} />
          </button>
        ) : (
          <div style={{ width: 26, height: 26, borderRadius: 8, ...btn({ display: "grid", placeItems: "center",
                        color: MUTE }) }}><Ico d={IC.grid} size={14} /></div>
        )}
        <span style={{ fontSize: 12.5, fontWeight: 800 }}>
          {view.k === "effect" ? view.fx.name : view.k === "catalog" ? "Add effect" : "Track effects"}
        </span>
        <span style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".13em", color: MUTE, textTransform: "uppercase" }}>
          {view.k === "effect" ? view.fx.type : "Track 3"}
        </span>
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 9.5, fontFamily: MONO, color: MUTE }}>{Math.round(size.w)}×{Math.round(size.h)}</span>
        <button onClick={() => setAuto(true)} style={{ ...btn({ height: 22, padding: "0 9px", borderRadius: 7,
                 color: auto ? ACC : MUTE, fontSize: 10, fontWeight: 800, fontFamily: FONT, cursor: "pointer" }) }}>Auto</button>
        <button style={{ ...btn({ width: 26, height: 26, borderRadius: 8, display: "grid", placeItems: "center",
                 color: MUTE, cursor: "pointer" }) }}><Ico d={IC.close} size={14} /></button>
      </div>

      {/* ══ CORPS ══ */}
      <div style={{ position: "absolute", left: PAD, right: PAD, top: HEAD + PAD, height: bodyH, overflow: "hidden" }}>

        {view.k === "rack" && (
          <div style={{ display: "flex", gap: 12, height: "100%" }}>
            {/* ── SLOT AUDIO ── */}
            <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 8, minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, paddingLeft: 3 }}>
                <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: ".15em", textTransform: "uppercase", color: MUTE }}>
                  Slot Audio
                </span>
                <span style={{ fontSize: 9.5, fontFamily: MONO, color: audio.length >= MAX_AUDIO ? ACC : MUTE }}>
                  {audio.length}/{MAX_AUDIO}
                </span>
                <div style={{ flex: 1, height: 1, background: LINE }} />
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: GAP, overflowY: "auto", paddingRight: 2 }}>
                {audio.map((f) => (
                  <HollowSlot key={f.id} height={slotH} filled>
                    <Cartridge fx={f} kind="audio" on={engaged(f)} onToggle={() => toggle(f)}
                               onOpen={() => setView({ k: "effect", fx: f })}
                               onRemove={() => setAudio((a) => a.filter((x) => x.id !== f.id))} />
                  </HollowSlot>
                ))}
                {Array.from({ length: Math.max(0, MAX_AUDIO - audio.length - 1) }, (_, i) => (
                  <EmptySlot key={"e" + i} label="Empty audio slot" height={slotH} />
                ))}
                <AddTile label={audio.length >= MAX_AUDIO ? `Limit — ${MAX_AUDIO} audio effects` : "Add audio effect"}
                         disabled={audio.length >= MAX_AUDIO}
                         onClick={() => setView({ k: "catalog", kind: "audio" })} />
              </div>
            </div>

            {/* ── séparateur ── */}
            <div style={{ width: 1, flex: "none", position: "relative",
                          background: `linear-gradient(180deg,transparent,${LINE2} 18%,${LINE2} 82%,transparent)` }}>
              <div style={{ position: "absolute", left: -3, top: "50%", marginTop: -3, width: 7, height: 7,
                            borderRadius: 4, background: "#2A3244", border: `1px solid ${LINE2}` }} />
            </div>

            {/* ── SLOT MIDI ── */}
            <div style={{ flex: "0 0 34%", display: "flex", flexDirection: "column", gap: GAP }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, paddingLeft: 3 }}>
                <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: ".15em", textTransform: "uppercase", color: MUTE }}>
                  Slot MIDI
                </span>
                <span style={{ fontSize: 9.5, fontFamily: MONO, color: midi.length >= MAX_MIDI ? ACC : MUTE }}>
                  {midi.length}/{MAX_MIDI}
                </span>
                <div style={{ flex: 1, height: 1, background: LINE }} />
              </div>
              {midi.map((f) => (
                <HollowSlot key={f.id} height={midiH} filled style={{ flex: "none" }}>
                  <Cartridge fx={f} kind="midi" tall params={f.note.split(", ")} on={engaged(f)}
                             onToggle={() => toggle(f)}
                             onOpen={() => setView({ k: "effect", fx: f })}
                             onRemove={() => setMidi([])} />
                </HollowSlot>
              ))}
              <AddTile label={midi.length >= MAX_MIDI ? "One MIDI slot per track" : "Add MIDI effect"}
                       disabled={midi.length >= MAX_MIDI}
                       onClick={() => setView({ k: "catalog", kind: "midi" })} />
            </div>
          </div>
        )}

        {view.k === "catalog" && (
          <CatalogPage kind={view.kind} onPick={pick} onBack={() => setView({ k: "rack" })}
                       full={view.kind === "audio" && audio.length >= MAX_AUDIO} />
        )}

        {view.k === "effect" && (
          isSpace ? (
            <div style={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <SpacePanel vals={vals} onChange={setKnob} scale={panelScale} />
            </div>
          ) : (
            <div style={{ height: "100%", borderRadius: 16, ...well({ borderRadius: 16, display: "grid",
                          placeItems: "center", position: "relative", overflow: "hidden" }) }}>
              <div style={{ position: "absolute", inset: 0,
                            background: `radial-gradient(ellipse at 50% 0%, ${hx(ACC, .1)}, transparent 68%)` }} />
              <div style={{ textAlign: "center", position: "relative" }}>
                <div style={{ width: 46, height: 46, margin: "0 auto 10px", borderRadius: 14, display: "grid",
                              placeItems: "center", color: SOFT,
                              background: `radial-gradient(circle at 50% 22%, ${hx(ACC, .23)}, #141A28 78%)`,
                              border: `1px solid ${hx(ACC, .33)}` }}>
                  <Ico d={IC.sliders} size={21} sw={1.9} />
                </div>
                <div style={{ fontSize: 14, fontWeight: 800, marginBottom: 4 }}>{view.fx.name}</div>
                <div style={{ fontSize: 11, color: DIM, maxWidth: 360, lineHeight: 1.6 }}>{view.fx.note}</div>
                <div style={{ marginTop: 12, fontSize: 10, color: MUTE, fontFamily: MONO }}>
                  {view.fx.pw} parameters · panel {view.fx.w}×{view.fx.h}
                </div>
              </div>
            </div>
          )
        )}
      </div>

      {/* ══ PIED ══ */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: FOOT, display: "flex",
                    alignItems: "center", gap: 9, padding: "0 12px", borderTop: `1px solid ${LINE}`,
                    background: "linear-gradient(0deg,rgba(255,255,255,.026),transparent)" }}>
        <button style={{ ...btn({ height: 28, padding: "0 11px", borderRadius: 9, display: "inline-flex",
                 alignItems: "center", gap: 6, color: INK, fontSize: 11, fontWeight: 800, fontFamily: FONT,
                 cursor: "pointer" }) }}>
          <Ico d={IC.drag} size={13} /> Reorder
        </button>
        <span style={{ fontSize: 10, color: MUTE }}>{audio.length} audio · {midi.length} MIDI</span>
        <div style={{ flex: 1 }} />
        <button style={{ ...btn({ height: 28, padding: "0 14px", borderRadius: 9, border: 0,
                 background: `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})`, color: ACC_TEXT, fontSize: 11,
                 fontWeight: 900, fontFamily: FONT, cursor: "pointer", boxShadow: `0 3px 12px ${hx(ACC, .27)}` }) }}>
          Done
        </button>
      </div>

      <Handle pos={{ right: 0, top: HEAD, bottom: FOOT, width: 7 }} onDown={startDrag(true, false)} />
      <Handle pos={{ right: 0, bottom: 0, width: 18, height: 18 }} onDown={startDrag(true, true)} />
      <div style={{ position: "absolute", right: 4, bottom: 4, width: 9, height: 9, pointerEvents: "none",
                    borderRight: `2px solid ${DIM}`, borderBottom: `2px solid ${DIM}`, borderRadius: 2, opacity: .6 }} />
    </div>
  );
}

/* ── habillage de prévisualisation ─────────────────────────────────────────── */
export default function FxWindowPreview() {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const fit = () => setScale(clamp(Math.min((window.innerWidth - 40) / 1160, (window.innerHeight - 130) / 700), 0.3, 1.5));
    fit(); window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, []);
  return (
    <div style={{ minHeight: "100vh", background: `radial-gradient(ellipse at 50% 0%, ${BG_T}, #0A0E16 70%)`,
                  display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
                  gap: 14, padding: 20, boxSizing: "border-box", fontFamily: FONT }}>
      <div style={{ width: 1160 * scale, height: 700 * scale, position: "relative" }}>
        <div data-shot="fxpage" style={{ width: 1160, height: 700, transform: `scale(${scale})`, transformOrigin: "top left",
                      position: "absolute", left: 0, top: 0, borderRadius: 26, overflow: "hidden",
                      background: `linear-gradient(180deg,${BG_T},${BG_B})`, padding: 20, boxSizing: "border-box",
                      display: "flex", alignItems: "flex-start", justifyContent: "center",
                      boxShadow: "0 34px 90px rgba(0,0,0,.7), 0 0 0 1px rgba(255,255,255,.06)" }}>
          <div style={{ position: "absolute", inset: 0, overflow: "hidden", opacity: .5 }}>
            <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 74,
                          background: "linear-gradient(180deg,rgba(255,255,255,.03),rgba(0,0,0,.35))",
                          borderTop: "1px solid rgba(255,255,255,.05)" }} />
            <div style={{ position: "absolute", left: 18, right: 18, top: 40, bottom: 84, display: "flex", gap: 9 }}>
              {Array.from({ length: 8 }, (_, i) => (
                <div key={i} style={{ flex: 1, borderRadius: 10, display: "flex", flexDirection: "column",
                                      alignItems: "center", justifyContent: "flex-end", gap: 6, paddingBottom: 8,
                                      background: "linear-gradient(180deg,rgba(255,255,255,.035),rgba(255,255,255,.012))",
                                      border: "1px solid rgba(255,255,255,.045)" }}>
                  <div style={{ width: 4, height: `${40 + ((i * 37) % 55)}%`, borderRadius: 3,
                                background: `linear-gradient(180deg,${hx(ACC, .5)},${hx(ACC_DEEP, .2)})` }} />
                  <div style={{ width: 26, height: 8, borderRadius: 3, background: "rgba(255,255,255,.09)" }} />
                </div>
              ))}
            </div>
          </div>
          <div style={{ position: "relative" }}><FxWindow /></div>
        </div>
      </div>
      <div style={{ fontSize: 11.5, color: "#5B6580", textAlign: "center", maxWidth: 780, lineHeight: 1.65 }}>
        Æther FX — Slot Audio ({MAX_AUDIO} max) | Slot MIDI ({MAX_MIDI}). Toucher une carte ouvre le panneau
        de l'effet · « Add » ouvre la liste dans la fenêtre · ouvrir un effet adapte la taille de la fenêtre ·
        poignée de droite = largeur, poignée de coin = largeur et hauteur · « Auto » reprend la taille déclarée.
      </div>
    </div>
  );
}
