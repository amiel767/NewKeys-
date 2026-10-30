import { useEffect, useRef, useState } from "react";

/* ═════════════════════════════════════════════════════════════════════════════
   ÆTHER FX — nouvelle fenêtre d'effets. 891 × 411 dp (la taille du device).

   CE QUI EST REPRIS, ET D'OÙ

   · Matière = fenêtre Track Settings (ui/tracksettings/TrackSettingsScreen.kt,
     source JSX TrackSettingsPreview.jsx). Jetons mesurés :
       fenêtre   radius 22, linear-gradient(180deg,#232C3E,#1A2230),
                 bordure 1px rgba(255,255,255,.1), ombre 0 26px 64px rgba(0,0,0,.6) ;
       en-tête   hauteur 66, filet bas rgba(255,255,255,.06), voile haut 3 % ;
       puits     linear-gradient(180deg,#0C1119,#0F1520) + inset 0 3px 12px rgba(0,0,0,.6)
                 + inset 0 0 0 1px rgba(255,255,255,.04) ;
       bouton    linear-gradient(180deg,#2F3850,#252D40), liseré haut 10 % ;
       rail      linear-gradient(180deg,#6E7799,#3F4766) ;
       accent    #A18AE0 ; texte #E3E8F2, atténué #9AA5BA.

   · Règle du projet (FxModes.kt) : UN seul slot MIDI par piste, TROIS effets
     audio au maximum. La pose d'un second MIDI remplace le premier.

   · Schéma demandé : Slot Audio | Slot MIDI, les deux groupes DÉTACHÉS.

   · Liste d'ajout : la texture du navigateur de presets d'Æther Space
     (rail de catégories, lignes numérotées, barre d'accent, compteur « n sur m »),
     mais dans sa PROPRE page, ouverte DANS la fenêtre.

   LE SLOT CREUX (semi-3D)
   Un slot est un logement en creux, pas un rectangle posé. Quatre couches :
     1. le fond du logement, très sombre, avec une ombre portée interne en haut ;
     2. la lèvre basse, éclairée — la lumière vient du haut, donc le bord inférieur
        du creux renvoie la lumière : c'est ce qui donne la profondeur ;
     3. les joues latérales, en dégradé, qui font la tranche ;
     4. la cartouche, posée au fond, avec sa propre lumière et son ombre sur la lèvre.
   Un logement vide ne montre que les couches 1 à 3 : on lit le vide.

   LA TAILLE
   Chaque effet déclare la taille de son panneau. La fenêtre s'y ajuste
   automatiquement, et reste redimensionnable à la main (poignée à droite et en
   bas à droite). « Auto » ramène à la taille déclarée par l'effet.
   ═════════════════════════════════════════════════════════════════════════════ */

const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const FONT = "Space Grotesk, Inter, system-ui, -apple-system, sans-serif";

/* ── jetons : la matière de Track Settings ─────────────────────────────────── */
const WIN1 = "#232C3E", WIN2 = "#1A2230";
const WELL1 = "#0C1119", WELL2 = "#0F1520";
const BTN1 = "#2F3850", BTN2 = "#252D40";
const RAIL1 = "#6E7799", RAIL2 = "#3F4766";
const ACC = "#A18AE0", ACC_L = "#C4B2F5", ACC_D = "#6E5FA6";
const TXT = "#E3E8F2", DIM = "#9AA5BA", MUTE = "#66708A";
const LINE = "rgba(255,255,255,.06)", LINE2 = "rgba(255,255,255,.1)";
const IN1 = "#C7D9F9", IN2 = "#9DB9EF";

const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='140' height='140'>" +
  "<filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='3' stitchTiles='stitch'/>" +
  "<feColorMatrix type='saturate' values='0'/></filter>" +
  "<rect width='100%' height='100%' filter='url(#n)'/></svg>"
)}")`;

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

/* ── les effets réels (FxRegistry.kt) : 8 audio (FxKind.EFFECT) ─────────────── */
const AUDIO_FX = [
  { id: 0, name: "Æther Space",   type: "Reverb",      cat: "Time",       acc: "#A99AD8", pw: 7, w: 880, h: 412, note: "Size, Decay, Pre-delay, Damp, Width, Mix, Diffuse" },
  { id: 1, name: "Æther Echo",    type: "Delay",       cat: "Time",       acc: "#7FB3E8", pw: 4, w: 760, h: 392, note: "Feedback, Tone, Wow, Mix" },
  { id: 2, name: "Prism Tone",    type: "Equalizer",   cat: "Tone",       acc: "#6FC7C0", pw: 4, w: 800, h: 400, note: "Low, Mid, High, Mid Q" },
  { id: 3, name: "Iron Grip",     type: "Compressor",  cat: "Dynamics",   acc: "#E8A05F", pw: 5, w: 820, h: 404, note: "Ratio, Knee, Attack, Release, Makeup" },
  { id: 4, name: "Solar Ceiling", type: "Maximizer",   cat: "Dynamics",   acc: "#E8C05F", pw: 3, w: 700, h: 386, note: "Amount, Ceiling, Release" },
  { id: 10, name: "Æther Amp",    type: "Amp",         cat: "Drive",      acc: "#D98A6A", pw: 6, w: 820, h: 404, note: "Drive, Bass, Mid, Treble, Presence, Level" },
  { id: 11, name: "Æther Bass",   type: "Amp",         cat: "Drive",      acc: "#C97F9E", pw: 6, w: 820, h: 404, note: "Drive, Body, Mid, Treble, Presence, Level" },
  { id: 13, name: "Æther Voices", type: "Voice",       cat: "Pitch",      acc: "#9BB8E8", pw: 5, w: 780, h: 398, note: "Voices, Detune, Formant, Width, Mix" },
];
/* ── les 5 effets MIDI (FxModes.kt) ────────────────────────────────────────── */
const MIDI_FX = [
  { id: 101, name: "Arpeggiator",      type: "MIDI", cat: "Pattern",  acc: "#8FB2E8", pw: 3, w: 720, h: 386, note: "Subdivision, Style, Steps 1–16" },
  { id: 102, name: "Sequencer",        type: "MIDI", cat: "Pattern",  acc: "#8FB2E8", pw: 4, w: 860, h: 420, note: "16 pas, vélocité par pas" },
  { id: 103, name: "Easy Chord",       type: "MIDI", cat: "Harmony",  acc: "#B99FE8", pw: 2, w: 660, h: 380, note: "Entrée, Sortie" },
  { id: 104, name: "Chord Sequencer",  type: "MIDI", cat: "Harmony",  acc: "#B99FE8", pw: 4, w: 840, h: 414, note: "Tonalité, Gamme, Progression" },
  { id: 105, name: "Octave Up",        type: "MIDI", cat: "Pitch",    acc: "#9BB8E8", pw: 1, w: 600, h: 360, note: "Blend" },
];

const MAX_AUDIO = 3;   // FxLogic.MAX_AUDIO
const MAX_MIDI = 1;    // un seul slot MIDI par piste

/* ── icônes, style Lucide (grille 24, trait 2, bouts ronds) ─────────────────── */
const Ico = ({ d, size = 16, sw = 2 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ display: "block", flex: "none" }}>
    {(Array.isArray(d) ? d : [d]).map((p, i) => <path key={i} d={p} />)}
  </svg>
);
const IC = {
  close: ["M18 6L6 18", "M6 6l12 12"],
  plus: ["M12 5v14", "M5 12h14"],
  power: ["M12 2v10", "M18.36 6.64a9 9 0 1 1-12.73 0"],
  chevronLeft: "M15 18l-6-6 6-6", chevronRight: "M9 18l6-6-6-6",
  trash: ["M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6"],
  drag: ["M9 6h.01", "M15 6h.01", "M9 12h.01", "M15 12h.01", "M9 18h.01", "M15 18h.01"],
  expand: ["M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7"],
  grid: ["M3 3h7v7H3z", "M14 3h7v7h-7z", "M14 14h7v7h-7z", "M3 14h7v7H3z"],
  midi: ["M9 18V5l12-2v13", "M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M18 19a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"],
  wave: "M2 12c2 0 2-4 4-4s2 8 4 8 2-8 4-8 2 4 4 4",
  settings: ["M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2 2 2 0 1 1-4 0 1.7 1.7 0 0 0-2.9-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.7 1.7 0 0 0 3 15a2 2 0 1 1 0-4 1.7 1.7 0 0 0 1.2-2.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.7 1.7 0 0 0 10 4.6a2 2 0 1 1 4 0 1.7 1.7 0 0 0 2.9 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1A1.7 1.7 0 0 0 21 11a2 2 0 1 1 0 4z"],
};

/* ── surfaces ──────────────────────────────────────────────────────────────── */
const panel = (x = {}) => ({ background: `linear-gradient(180deg,${WIN1},${WIN2})`, ...x });
const well = (x = {}) => ({
  background: `linear-gradient(180deg,${WELL1},${WELL2})`,
  boxShadow: "inset 0 3px 12px rgba(0,0,0,.6), inset 0 0 0 1px rgba(255,255,255,.04)", ...x,
});
const btn = (x = {}) => ({
  background: `linear-gradient(180deg,${BTN1},${BTN2})`,
  border: `1px solid ${LINE2}`,
  boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 2px 5px rgba(0,0,0,.35)", ...x,
});

/* ══ LE SLOT CREUX — semi-3D ═════════════════════════════════════════════════
   Quatre couches, dans l'ordre où la lumière les atteint :
     joues   → les tranches latérales, plus claires que le fond ;
     fond    → très sombre, ombre portée interne en haut (la lumière ne rentre pas) ;
     lèvre   → le bord bas du creux renvoie la lumière : liseré clair + ombre juste
               au-dessus, c'est ce qui fait lire la profondeur ;
     cartouche → posée au fond, elle projette une ombre sur la lèvre.
   ─────────────────────────────────────────────────────────────────────────── */
function HollowSlot({ height = 92, children, filled, accent = ACC, radius = 15, style }) {
  return (
    <div style={{ position: "relative", height, borderRadius: radius, padding: 3,
                  background: "linear-gradient(180deg,rgba(0,0,0,.5),rgba(255,255,255,.035))",
                  boxShadow: "0 1px 0 rgba(255,255,255,.05)", ...style }}>
      {/* le fond du logement */}
      <div style={{ position: "absolute", inset: 3, borderRadius: radius - 3,
                    background: `linear-gradient(180deg,#05080E,#0A0E18)`,
                    boxShadow: "inset 0 5px 12px rgba(0,0,0,.85), inset 0 -1px 0 rgba(255,255,255,.055), inset 2px 0 5px rgba(0,0,0,.5), inset -2px 0 5px rgba(0,0,0,.5)" }} />
      {/* la lèvre basse : la lumière vient du haut, donc le bas du creux est clair */}
      <div style={{ position: "absolute", left: 6, right: 6, bottom: 3.5, height: 1.4, borderRadius: 1,
                    background: `linear-gradient(90deg,transparent,${filled ? accent + "55" : "rgba(255,255,255,.14)"},transparent)`,
                    filter: "blur(.3px)" }} />
      {/* la cartouche, posée au fond */}
      <div style={{ position: "absolute", inset: 6, borderRadius: radius - 6, display: "flex",
                    alignItems: "center", overflow: "hidden" }}>
        {children}
      </div>
    </div>
  );
}

/* ── la cartouche : l'effet posé dans son logement ─────────────────────────── */
function Cartridge({ fx, on, onToggle, onOpen, onRemove, kind }) {
  const acc = fx.acc;
  return (
    <div style={{ position: "relative", flex: 1, height: "100%", borderRadius: 11, display: "flex",
                  alignItems: "center", gap: 11, padding: "0 11px 0 9px",
                  background: on
                    ? `linear-gradient(180deg,${acc}26,${acc}0D), linear-gradient(180deg,#28304A,#1C2336)`
                    : "linear-gradient(180deg,#1B2133,#151A28)",
                  border: `1px solid ${on ? acc + "66" : "rgba(255,255,255,.05)"}`,
                  boxShadow: on
                    ? `inset 0 1px 0 rgba(255,255,255,.13), 0 4px 12px rgba(0,0,0,.5), 0 0 18px ${acc}22`
                    : "inset 0 1px 0 rgba(255,255,255,.05), 0 3px 8px rgba(0,0,0,.45)" }}>
      {/* tranche gauche éclairée : la carte a une épaisseur */}
      <div style={{ position: "absolute", left: 0, top: 6, bottom: 6, width: 2, borderRadius: 2,
                    background: `linear-gradient(180deg,${acc},${acc}44)`, opacity: on ? 1 : 0.35 }} />

      {/* pastille d'identité : un anneau de la couleur de l'effet */}
      <div style={{ width: 34, height: 34, borderRadius: 10, flex: "none", display: "grid", placeItems: "center",
                    background: `radial-gradient(circle at 50% 22%, ${acc}3A, #141A28 78%)`,
                    border: `1px solid ${on ? acc + "55" : "rgba(255,255,255,.06)"}`,
                    boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 2px 6px rgba(0,0,0,.5)" }}>
        <Ico d={kind === "midi" ? IC.midi : IC.wave} size={16} sw={2.1} />
        <span style={{ position: "absolute", opacity: 0 }} />
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 2, minWidth: 0, flex: 1 }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
          <span style={{ fontSize: 13.5, fontWeight: 800, color: on ? TXT : DIM, whiteSpace: "nowrap",
                         overflow: "hidden", textOverflow: "ellipsis" }}>{fx.name}</span>
          <span style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".1em", textTransform: "uppercase",
                         color: on ? acc : MUTE }}>{fx.type}</span>
        </div>
        <span style={{ fontSize: 10.5, color: MUTE, whiteSpace: "nowrap", overflow: "hidden",
                       textOverflow: "ellipsis" }}>{fx.note}</span>
      </div>

      {/* « ouvrir » : le panneau de l'effet */}
      <button onClick={onOpen} title="Open"
              style={{ ...btn({ width: 30, height: 30, borderRadius: 9, display: "grid", placeItems: "center",
                       color: DIM, cursor: "pointer", flex: "none" }) }}>
        <Ico d={IC.expand} size={14} />
      </button>
      {/* engage / bypass */}
      <button onClick={onToggle} title="Engage / Bypass"
              style={{ width: 30, height: 30, borderRadius: 9, cursor: "pointer", display: "grid",
                       placeItems: "center", flex: "none", border: `1px solid ${on ? acc + "88" : LINE2}`,
                       background: on ? `linear-gradient(180deg,${ACC_L},${ACC_D})` : `linear-gradient(180deg,${BTN1},${BTN2})`,
                       color: on ? "#1B2440" : MUTE,
                       boxShadow: on ? `0 0 13px ${ACC}55, inset 0 1px 0 rgba(255,255,255,.4)` : "inset 0 1px 0 rgba(255,255,255,.1)",
                       transition: "all .2s cubic-bezier(.3,1.4,.5,1)" }}>
        <Ico d={IC.power} size={14} sw={2.3} />
      </button>
      <button onClick={onRemove} title="Remove"
              style={{ ...btn({ width: 30, height: 30, borderRadius: 9, display: "grid", placeItems: "center",
                       color: MUTE, cursor: "pointer", flex: "none" }) }}>
        <Ico d={IC.trash} size={14} />
      </button>
    </div>
  );
}

/* ── un logement vide : on lit le vide, pas un rectangle gris ──────────────── */
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

/* ── le bouton Add : une tuile d'action, en relief ─────────────────────────── */
function AddTile({ label, onClick, disabled, accent = ACC, height = 42 }) {
  return (
    <button onClick={onClick} disabled={disabled}
            style={{ height, width: "100%", borderRadius: 13, cursor: disabled ? "default" : "pointer",
                     display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
                     fontFamily: FONT, fontSize: 12, fontWeight: 800, letterSpacing: ".03em",
                     color: disabled ? MUTE : TXT, opacity: disabled ? 0.45 : 1,
                     background: `linear-gradient(180deg,rgba(255,255,255,.075),rgba(255,255,255,.03))`,
                     border: `1px dashed ${disabled ? "rgba(255,255,255,.09)" : accent + "66"}`,
                     boxShadow: disabled ? "none" : `inset 0 1px 0 rgba(255,255,255,.08)`,
                     transition: "all .18s" }}>
      <Ico d={IC.plus} size={14} sw={2.5} /> {label}
    </button>
  );
}

/* ══ LA PAGE D'AJOUT ═════════════════════════════════════════════════════════
   Même texture que le navigateur de presets d'Æther Space : rail de catégories
   à gauche avec compteur, lignes numérotées à droite, barre d'accent sur la
   ligne active, en-tête « n sur m ». Sa propre page, dans la fenêtre.
   ─────────────────────────────────────────────────────────────────────────── */
function CatalogPage({ kind, onPick, onBack, full }) {
  const list = kind === "midi" ? MIDI_FX : AUDIO_FX;
  const cats = [...new Set(list.map((f) => f.cat))];
  const [cat, setCat] = useState(null);
  const shown = cat ? list.filter((f) => f.cat === cat) : list;
  const acc = kind === "midi" ? "#8FB2E8" : ACC;

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
      {/* en-tête de page */}
      <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "0 2px 11px" }}>
        <button onClick={onBack} style={{ ...btn({ width: 28, height: 28, borderRadius: 9, display: "grid",
                 placeItems: "center", color: DIM, cursor: "pointer" }) }}>
          <Ico d={IC.chevronLeft} size={14} />
        </button>
        <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.15 }}>
          <span style={{ fontSize: 14, fontWeight: 800 }}>
            {kind === "midi" ? "Add MIDI effect" : "Add audio effect"}
          </span>
          <span style={{ fontSize: 10, color: MUTE }}>
            {shown.length} of {list.length}
            {kind === "midi" ? " · one per track, replaces the current one" : ` · up to ${MAX_AUDIO} per track`}
          </span>
        </div>
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase",
                       color: kind === "midi" ? "#8FB2E8" : ACC }}>{kind}</span>
      </div>

      <div style={{ display: "flex", gap: 11, flex: 1, minHeight: 0 }}>
        {/* rail de catégories */}
        <div style={{ width: 168, flex: "none", ...well({ borderRadius: 14, padding: 7,
                      display: "flex", flexDirection: "column", gap: 3, overflow: "hidden" }) }}>
          {[{ k: null, n: "All" }, ...cats.map((c) => ({ k: c, n: c }))].map(({ k, n }) => {
            const on = cat === k;
            const count = k ? list.filter((f) => f.cat === k).length : list.length;
            return (
              <div key={n} onClick={() => setCat(k)}
                   style={{ position: "relative", height: 32, borderRadius: 9, cursor: "pointer",
                            display: "flex", alignItems: "center", gap: 8, padding: "0 9px 0 12px",
                            background: on ? `linear-gradient(180deg,${acc}30,${acc}12)` : "transparent",
                            border: `1px solid ${on ? acc + "55" : "transparent"}` }}>
                {on && <div style={{ position: "absolute", left: 3, top: 7, bottom: 7, width: 2.5, borderRadius: 2,
                                     background: acc, boxShadow: `0 0 7px ${acc}` }} />}
                <span style={{ fontSize: 12, fontWeight: 700, color: on ? TXT : DIM, flex: 1 }}>{n}</span>
                <span style={{ fontSize: 10, fontFamily: MONO, color: on ? acc : MUTE }}>{count}</span>
              </div>
            );
          })}
        </div>

        {/* liste numérotée */}
        <div style={{ flex: 1, ...well({ borderRadius: 14, padding: 7, overflowY: "auto", minWidth: 0 }) }}>
          {shown.map((f, i) => (
            <div key={f.id} onClick={() => onPick(f)}
                 style={{ position: "relative", height: 44, borderRadius: 10, cursor: "pointer",
                          display: "flex", alignItems: "center", gap: 10, padding: "0 11px 0 14px",
                          marginBottom: 3,
                          background: i % 2 ? "rgba(255,255,255,.014)" : "transparent",
                          transition: "background .15s" }}>
              <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE, width: 18 }}>{String(i + 1).padStart(2, "0")}</span>
              <span style={{ width: 7, height: 7, borderRadius: 4, background: f.acc, flex: "none",
                             boxShadow: `0 0 8px ${f.acc}88` }} />
              <span style={{ fontSize: 12.5, fontWeight: 700, width: 118, whiteSpace: "nowrap",
                             overflow: "hidden", textOverflow: "ellipsis" }}>{f.name}</span>
              <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: ".09em", textTransform: "uppercase",
                             color: f.acc, width: 78, flex: "none" }}>{f.type}</span>
              <span style={{ fontSize: 10.5, color: MUTE, flex: 1, whiteSpace: "nowrap", overflow: "hidden",
                             textOverflow: "ellipsis" }}>{f.note}</span>
              {!full && <span style={{ fontSize: 9.5, fontFamily: MONO, color: MUTE }}>{f.pw}p</span>}
              <span style={{ ...btn({ width: 26, height: 26, borderRadius: 8, display: "grid", placeItems: "center",
                              color: DIM, flex: "none" }) }}><Ico d={IC.plus} size={13} sw={2.5} /></span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── poignée de redimensionnement ─────────────────────────────────────────── */
function Handle({ pos, onDown }) {
  const s = { position: "absolute", zIndex: 6, ...pos };
  return <div onPointerDown={onDown} style={{ ...s, cursor: pos.right !== undefined ? "ew-resize" : "nwse-resize" }} />;
}

/* ══ LA FENÊTRE ══════════════════════════════════════════════════════════════ */
function FxWindow() {
  const [audio, setAudio] = useState([AUDIO_FX[0]]);
  const [midi, setMidi] = useState([MIDI_FX[0]]);
  const [on, setOn] = useState({});          // id -> engaged
  const [view, setView] = useState(() => {
    const h = (typeof location !== "undefined" ? location.hash : "").replace("#", "");
    if (h === "midi") return { k: "catalog", kind: "midi" };
    if (h === "audio") return { k: "catalog", kind: "audio" };
    if (h === "effect") return { k: "effect", fx: AUDIO_FX[0] };
    if (h === "effect-midi") return { k: "effect", fx: MIDI_FX[0] };
    return { k: "rack" };
  });
  const [size, setSize] = useState({ w: 880, h: 412 });
  const [auto, setAuto] = useState(true);
  const drag = useRef(null);

  const engaged = (f) => on[f.id] !== false;
  const toggle = (f) => setOn((s) => ({ ...s, [f.id]: s[f.id] === false }));

  /* La fenêtre suit la taille déclarée par l'effet ouvert. */
  useEffect(() => {
    if (view.k === "effect") setSize({ w: view.fx.w, h: view.fx.h });
    else if (view.k === "catalog") setSize({ w: 840, h: 424 });
    else setSize({ w: 880, h: 412 });
    setAuto(true);
  }, [view]);

  /* Redimensionnement à la main. */
  useEffect(() => {
    const move = (e) => {
      if (!drag.current) return;
      setAuto(false);
      const { x0, y0, w0, h0, ax, ay } = drag.current;
      setSize({
        w: ax ? clamp(w0 + (e.clientX - x0), 520, 1100) : w0,
        h: ay ? clamp(h0 + (e.clientY - y0), 300, 640) : h0,
      });
    };
    const up = () => (drag.current = null);
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    return () => { window.removeEventListener("pointermove", move); window.removeEventListener("pointerup", up); };
  }, []);

  const startDrag = (ax, ay) => (e) => {
    drag.current = { x0: e.clientX, y0: e.clientY, w0: size.w, h0: size.h, ax, ay };
  };

  const pick = (f) => {
    if (view.k === "catalog" && view.kind === "midi") setMidi([f]);            // un seul MIDI : remplace
    else if (view.k === "catalog" && audio.length < MAX_AUDIO) setAudio((a) => [...a, f]);
    setView({ k: "effect", fx: f });
  };

  const slotH = 92;
  const bodyH = size.h - 66 - 52;   // en-tête + pied

  return (
    <div data-shot="fxwin" style={{ position: "relative", width: size.w, height: size.h, borderRadius: 22, overflow: "hidden",
                  fontFamily: FONT, color: TXT, ...panel(), border: `1px solid ${LINE2}`,
                  boxShadow: "0 26px 64px rgba(0,0,0,.6), inset 0 1px 0 rgba(255,255,255,.06)",
                  userSelect: "none", WebkitFontSmoothing: "antialiased", transition: "width .22s, height .22s" }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.028, pointerEvents: "none" }} />

      {/* ══ EN-TÊTE ══ */}
      <div style={{ position: "relative", height: 66, display: "flex", alignItems: "center", gap: 11,
                    padding: "0 16px", borderBottom: `1px solid ${LINE}`,
                    background: "linear-gradient(180deg,rgba(255,255,255,.03),transparent)" }}>
        <div style={{ width: 34, height: 34, borderRadius: 11, ...btn({ display: "grid", placeItems: "center",
                      color: DIM, cursor: "pointer" }) }}><Ico d={IC.grid} size={16} /></div>
        <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.12 }}>
          <span style={{ fontSize: 15.5, fontWeight: 800 }}>
            {view.k === "effect" ? view.fx.name : view.k === "catalog" ? "Add effect" : "Track effects"}
          </span>
          <span style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".14em", color: MUTE, textTransform: "uppercase" }}>
            {view.k === "effect" ? view.fx.type : "Track 3 · MIDI + Audio"}
          </span>
        </div>

        <div style={{ flex: 1 }} />

        <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>{size.w}×{size.h}</span>
        <button onClick={() => setAuto(true)} style={{ ...btn({ height: 26, padding: "0 10px", borderRadius: 9,
                 color: auto ? ACC_L : DIM, fontSize: 10.5, fontWeight: 800, fontFamily: FONT, cursor: "pointer" }) }}>
          Auto
        </button>
        <button style={{ ...btn({ width: 30, height: 30, borderRadius: 9, display: "grid", placeItems: "center",
                 color: MUTE, cursor: "pointer" }) }}><Ico d={IC.close} size={15} /></button>
      </div>

      {/* ══ CORPS ══ */}
      <div style={{ position: "absolute", left: 16, right: 16, top: 74, height: bodyH, overflow: "hidden" }}>

        {view.k === "rack" && (
          <div style={{ display: "flex", gap: 13, height: "100%", alignItems: "stretch" }}>
            {/* ── SLOT AUDIO ── */}
            <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 9, minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, paddingLeft: 3 }}>
                <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: ".15em", textTransform: "uppercase", color: DIM }}>
                  Slot Audio
                </span>
                <span style={{ fontSize: 10, fontFamily: MONO, color: audio.length >= MAX_AUDIO ? ACC_L : MUTE }}>
                  {audio.length}/{MAX_AUDIO}
                </span>
                <div style={{ flex: 1, height: 1, background: LINE }} />
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: 8, overflowY: "auto", paddingRight: 2 }}>
                {audio.map((f) => (
                  <HollowSlot key={f.id} height={slotH} filled accent={f.acc}>
                    <Cartridge fx={f} kind="audio" on={engaged(f)} onToggle={() => toggle(f)}
                               onOpen={() => setView({ k: "effect", fx: f })}
                               onRemove={() => setAudio((a) => a.filter((x) => x.id !== f.id))} />
                  </HollowSlot>
                ))}
                {Array.from({ length: Math.max(0, MAX_AUDIO - audio.length - 1) }, (_, i) => (
                  <EmptySlot key={"e" + i} label="Empty audio slot" height={slotH} />
                ))}
                <AddTile label={audio.length >= MAX_AUDIO ? `Limit reached — ${MAX_AUDIO} audio effects` : "Add audio effect"}
                         disabled={audio.length >= MAX_AUDIO}
                         onClick={() => setView({ k: "catalog", kind: "audio" })} />
              </div>
            </div>

            {/* ── LE SÉPARATEUR : les deux groupes sont détachés ── */}
            <div style={{ width: 1, flex: "none", background: `linear-gradient(180deg,transparent,${LINE2} 18%,${LINE2} 82%,transparent)`,
                          position: "relative" }}>
              <div style={{ position: "absolute", left: -3, top: "50%", marginTop: -3, width: 7, height: 7,
                            borderRadius: 4, background: "#2A3244", border: `1px solid ${LINE2}` }} />
            </div>

            {/* ── SLOT MIDI ── */}
            <div style={{ width: 300, flex: "none", display: "flex", flexDirection: "column", gap: 9 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, paddingLeft: 3 }}>
                <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: ".15em", textTransform: "uppercase", color: DIM }}>
                  Slot MIDI
                </span>
                <span style={{ fontSize: 10, fontFamily: MONO, color: midi.length >= MAX_MIDI ? ACC_L : MUTE }}>
                  {midi.length}/{MAX_MIDI}
                </span>
                <div style={{ flex: 1, height: 1, background: LINE }} />
              </div>

              {midi.map((f) => (
                <HollowSlot key={f.id} height={slotH} filled accent={f.acc}>
                  <Cartridge fx={f} kind="midi" on={engaged(f)} onToggle={() => toggle(f)}
                             onOpen={() => setView({ k: "effect", fx: f })}
                             onRemove={() => setMidi([])} />
                </HollowSlot>
              ))}
              <AddTile label={midi.length >= MAX_MIDI ? "One MIDI slot per track" : "Add MIDI effect"}
                       disabled={midi.length >= MAX_MIDI} accent="#8FB2E8"
                       onClick={() => setView({ k: "catalog", kind: "midi" })} />

              <div style={{ flex: 1 }} />
              {/* note de règle : ce que le projet impose, dit sans détour */}
              <div style={{ ...well({ borderRadius: 12, padding: "10px 12px" }) }}>
                <div style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: ".1em", textTransform: "uppercase",
                              color: MUTE, marginBottom: 5 }}>Rules</div>
                <div style={{ fontSize: 10.5, color: DIM, lineHeight: 1.6 }}>
                  {MAX_AUDIO} audio effects maximum, in chain order.<br />
                  {MAX_MIDI} MIDI effect per track. Choosing another<br />
                  one replaces the current effect.
                </div>
              </div>
            </div>
          </div>
        )}

        {view.k === "catalog" && (
          <CatalogPage kind={view.kind} onPick={pick} onBack={() => setView({ k: "rack" })}
                       full={view.kind === "audio" && audio.length >= MAX_AUDIO} />
        )}

        {view.k === "effect" && (
          <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, paddingBottom: 11 }}>
              <button onClick={() => setView({ k: "rack" })} style={{ ...btn({ width: 28, height: 28, borderRadius: 9,
                       display: "grid", placeItems: "center", color: DIM, cursor: "pointer" }) }}>
                <Ico d={IC.chevronLeft} size={14} />
              </button>
              <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase", color: MUTE }}>
                {view.fx.pw} parameters
              </span>
              <div style={{ flex: 1 }} />
              <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>
                panel {view.fx.w}×{view.fx.h}
              </span>
            </div>
            {/* le panneau de l'effet : ici la taille que l'effet déclare */}
            <div style={{ flex: 1, borderRadius: 16, ...well({ borderRadius: 16, display: "grid",
                          placeItems: "center", position: "relative", overflow: "hidden" }) }}>
              <div style={{ position: "absolute", inset: 0,
                            background: `radial-gradient(ellipse at 50% 0%, ${view.fx.acc}1A, transparent 68%)` }} />
              <div style={{ textAlign: "center", position: "relative" }}>
                <div style={{ width: 54, height: 54, margin: "0 auto 12px", borderRadius: 16, display: "grid",
                              placeItems: "center", background: `radial-gradient(circle at 50% 22%, ${view.fx.acc}3A, #141A28 78%)`,
                              border: `1px solid ${view.fx.acc}55`, boxShadow: `0 0 22px ${view.fx.acc}22` }}>
                  <Ico d={IC.settings} size={24} sw={1.9} />
                </div>
                <div style={{ fontSize: 15, fontWeight: 800, marginBottom: 4 }}>{view.fx.name}</div>
                <div style={{ fontSize: 11, color: MUTE, maxWidth: 380, lineHeight: 1.6 }}>{view.fx.note}</div>
                <div style={{ marginTop: 14, fontSize: 10, color: MUTE, fontFamily: MONO }}>
                  The effect's own panel opens here, at the size it declares.
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ══ PIED ══ */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 52, display: "flex",
                    alignItems: "center", gap: 9, padding: "0 16px", borderTop: `1px solid ${LINE}`,
                    background: "linear-gradient(0deg,rgba(255,255,255,.026),transparent)" }}>
        <button style={{ ...btn({ height: 32, padding: "0 13px", borderRadius: 10, display: "inline-flex",
                 alignItems: "center", gap: 7, color: TXT, fontSize: 11.5, fontWeight: 800, fontFamily: FONT,
                 cursor: "pointer" }) }}>
          <Ico d={IC.drag} size={14} /> Reorder
        </button>
        <span style={{ fontSize: 10.5, color: MUTE }}>
          {audio.length} audio · {midi.length} MIDI
        </span>
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 10.5, fontWeight: 700, color: MUTE }}>Resize the window, or pick an effect to fit its panel</span>
        <button style={{ ...btn({ height: 32, padding: "0 16px", borderRadius: 10, border: 0,
                 background: `linear-gradient(180deg,${IN1},${IN2})`, color: "#1B2440", fontSize: 11.5,
                 fontWeight: 900, fontFamily: FONT, cursor: "pointer", boxShadow: `0 3px 12px ${IN2}44` }) }}>
          Done
        </button>
      </div>

      {/* poignées : largeur à droite, largeur + hauteur en bas à droite */}
      <Handle pos={{ right: 0, top: 74, bottom: 52, width: 7 }} onDown={startDrag(true, false)} />
      <Handle pos={{ right: 0, bottom: 0, width: 18, height: 18 }} onDown={startDrag(true, true)} />
      <div style={{ position: "absolute", right: 4, bottom: 4, width: 10, height: 10, pointerEvents: "none",
                    borderRight: `2px solid ${MUTE}`, borderBottom: `2px solid ${MUTE}`, borderRadius: 2, opacity: .7 }} />
    </div>
  );
}

/* ── habillage de prévisualisation (le fond du mixer, sous la fenêtre) ─────── */
export default function FxWindowPreview() {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const fit = () => setScale(clamp(Math.min((window.innerWidth - 40) / 1140, (window.innerHeight - 120) / 700), 0.3, 1.5));
    fit(); window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, []);
  return (
    <div style={{ minHeight: "100vh", background: "radial-gradient(ellipse at 50% 0%, #141B27, #0A0E16 70%)",
                  display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
                  gap: 14, padding: 20, boxSizing: "border-box", fontFamily: FONT }}>
      <div style={{ width: 1140 * scale, height: 700 * scale, position: "relative" }}>
        <div data-shot="fxpage" style={{ width: 1140, height: 700, transform: `scale(${scale})`, transformOrigin: "top left",
                      position: "absolute", left: 0, top: 0, borderRadius: 26, overflow: "hidden",
                      background: "linear-gradient(180deg,#141B27,#0F141D)", padding: 24, boxSizing: "border-box",
                      display: "flex", alignItems: "flex-start", justifyContent: "center",
                      boxShadow: "0 34px 90px rgba(0,0,0,.7), 0 0 0 1px rgba(255,255,255,.06)" }}>
          {/* évocation du mixer sous la fenêtre : juste assez pour lire qu'elle flotte */}
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
                                background: "linear-gradient(180deg,rgba(199,217,249,.5),rgba(157,185,239,.2))" }} />
                  <div style={{ width: 26, height: 8, borderRadius: 3, background: "rgba(255,255,255,.09)" }} />
                </div>
              ))}
            </div>
          </div>
          <div style={{ position: "relative" }}><FxWindow /></div>
        </div>
      </div>
      <div style={{ fontSize: 11.5, color: "#5B6580", textAlign: "center", maxWidth: 760, lineHeight: 1.65 }}>
        Æther FX — test de design. Deux groupes détachés : Slot Audio ({MAX_AUDIO} max) | Slot MIDI ({MAX_MIDI}).
        Cliquer « Add » ouvre la liste dans la fenêtre · ouvrir un effet adapte la taille de la fenêtre ·
        la poignée de droite règle la largeur, celle du coin la largeur et la hauteur · « Auto » reprend la
        taille déclarée par l'effet.
      </div>
    </div>
  );
}
