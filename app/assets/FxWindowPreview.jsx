import { useEffect, useRef, useState } from "react";
import { SpaceWindow, W as SPACE_W, H as SPACE_H } from "./FxSpacePreview.jsx";

/* ═════════════════════════════════════════════════════════════════════════════
   ÆTHER FX — fenêtre d'effets.

   ÉCRAN STANDARD
   Le cadre de référence est celui de l'app : 891 × 411, comme tous les JSX du
   dépôt (MixerPreview, TrackSettingsPreview, FxSpacePreview…). C'est la taille du
   téléphone. Pas de mise à l'échelle « tablette ».

   ÆTHER SPACE : LE VRAI PANNEAU, IMPORTÉ
   Le panneau violet n'est pas redessiné : `SpaceWindow` est IMPORTÉ de
   FxSpacePreview.jsx et rendu tel quel, à sa taille native 891 × 411. Une seule
   source, donc aucune dérive possible entre les deux maquettes.

   LES SLOTS
   Des logements verticaux COURTS, côte à côte. Un logement VIDE montre un creux
   (lèvre basse éclairée, ombre portée en haut) avec un « + » au centre. Un
   logement REMPLI n'est pas un creux : c'est une CARTE posée, avec sa matière
   propre, sa tranche éclairée, et une ombre portée SOUS elle. Les deux états se
   lisent donc immédiatement : creux = vide, carte = occupé.

   LA LISTE D'EFFETS
   Moderne, minimale, professionnelle : pas de rail de catégories, pas de
   numérotation, pas de colonnes. Une grille de cartes larges avec un glyphe, le
   nom, le type et une ligne d'explication. La sélection est un liseré d'accent
   et un fond qui se relève. Quelques effets seulement : on peut se permettre
   l'aération.
   ═════════════════════════════════════════════════════════════════════════════ */

const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const FONT = "Space Grotesk, Inter, system-ui, -apple-system, sans-serif";

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

/* ── les effets réels ─────────────────────────────────────────────────────── */
const AUDIO_FX = [
  { id: 0,  name: "Æther Space",   type: "Reverb",     cat: "Time",     pw: 7, w: 891, h: 411, note: "Size, Decay, Pre-delay, Damp, Width, Mix, Diffuse", space: true, glyph: ["M12 3a9 9 0 1 0 9 9", "M12 3v9h9", "M3 12a9 9 0 0 0 9 9"] },
  { id: 1,  name: "Æther Echo",    type: "Delay",      cat: "Time",     pw: 4, w: 891, h: 411, note: "Feedback, Tone, Wow, Mix", glyph: ["M3 12h4l3-7 4 14 3-7h4"] },
  { id: 2,  name: "Prism Tone",    type: "Equalizer",  cat: "Tone",     pw: 4, w: 891, h: 411, note: "Low, Mid, High, Mid Q", glyph: ["M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6"] },
  { id: 3,  name: "Iron Grip",     type: "Compressor", cat: "Dynamics", pw: 5, w: 891, h: 411, note: "Ratio, Knee, Attack, Release, Makeup", glyph: ["M4 20L20 4", "M4 4h6", "M4 4v6", "M20 20h-6", "M20 20v-6"] },
  { id: 4,  name: "Solar Ceiling", type: "Maximizer",  cat: "Dynamics", pw: 3, w: 891, h: 411, note: "Amount, Ceiling, Release", glyph: ["M3 20h18", "M7 20v-6", "M12 20V7", "M17 20v-9"] },
  { id: 10, name: "Æther Amp",     type: "Amp",        cat: "Drive",    pw: 6, w: 891, h: 411, note: "Drive, Bass, Mid, Treble, Presence, Level", glyph: ["M6 8h12l2 12H4L6 8z", "M9 8V5h6v3", "M8 14h8"] },
  { id: 11, name: "Æther Bass",    type: "Amp",        cat: "Drive",    pw: 6, w: 891, h: 411, note: "Drive, Body, Mid, Treble, Presence, Level", glyph: ["M4 16c3 0 3-8 6-8s3 8 6 8 3-6 4-6"] },
  { id: 13, name: "Æther Voices",  type: "Voice",      cat: "Pitch",    pw: 5, w: 891, h: 411, note: "Voices, Detune, Formant, Width, Mix", glyph: ["M12 4a3 3 0 1 0 0 6 3 3 0 0 0 0-6z", "M6 20v-2a6 6 0 0 1 12 0v2"] },
];
const MIDI_FX = [
  { id: 101, name: "Arpeggiator",     type: "Arp",     cat: "Pattern", pw: 5, w: 891, h: 411, note: "Turns held notes into a pattern", arp: true, glyph: ["M3 18V9", "M8 18V5", "M13 18v-6", "M18 18V7", "M21 18V4"] },
  { id: 102, name: "Sequencer",       type: "Seq",     cat: "Pattern", pw: 4, w: 891, h: 411, note: "One note plays a sequence of notes", glyph: ["M3 6h4v4H3z", "M10 6h4v4h-4z", "M17 6h4v4h-4z", "M3 14h4v4H3z", "M10 14h4v4h-4z"] },
  { id: 103, name: "Easy Chord",      type: "Easy",    cat: "Harmony", pw: 1, w: 891, h: 411, note: "One note plays a programmed chord", glyph: ["M12 3v10", "M12 13a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M12 3l5 3"] },
  { id: 104, name: "Chord Sequencer", type: "Chords",  cat: "Harmony", pw: 6, w: 891, h: 411, note: "Plays a chord progression", glyph: ["M4 20V6", "M4 6l16-2v14", "M9 14a2 2 0 1 0 0 4 2 2 0 0 0 0-4z", "M17 12a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"] },
  { id: 105, name: "Octave Up",       type: "Octave",  cat: "Pitch",   pw: 1, w: 891, h: 411, note: "Adds an octave above every note", glyph: ["M12 4l6 7h-4v9h-4v-9H6l6-7z"] },
];
const MAX_AUDIO = 3, MAX_MIDI = 1;

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
  sliders: ["M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6"],
  play: ["M8 5.5l10 6.5-10 6.5z"], rand: ["M4 7h4l8 10h4", "M4 17h4l2-2.5", "M14 9.5L16 7h4", "M18 5l2 2-2 2", "M18 15l2 2-2 2"],
  hold: ["M12 3v18", "M5 7h14", "M5 17h14"], check: ["M20 6L9 17l-5-5"],
};

const win = (x = {}) => ({ background: `linear-gradient(180deg,${PANEL_T},${PANEL_B})`, ...x });
const well = (x = {}) => ({ background: `linear-gradient(180deg,${WELL_A},${WELL_B})`,
  boxShadow: "inset 0 3px 12px rgba(0,0,0,.6), inset 0 0 0 1px rgba(255,255,255,.04)", ...x });
const btn = (x = {}) => ({ background: `linear-gradient(180deg,${BTN_T},${BTN_B})`, border: `1px solid ${LINE2}`,
  boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 2px 5px rgba(0,0,0,.35)", ...x });

/* ══ LOGEMENT VIDE = CREUX ═══════════════════════════════════════════════════
   Un creux se lit par sa lèvre basse éclairée (la lumière vient du haut, donc
   le bord inférieur la renvoie) et par l'ombre portée à l'intérieur du bord
   haut. Un logement REMPLI n'utilise pas ce composant : il porte une carte.
   ─────────────────────────────────────────────────────────────────────────── */
function EmptySlot({ height = 190, onAdd, label }) {
  return (
    <div style={{ position: "relative", height, width: "100%", borderRadius: 15, padding: 3,
                  background: "linear-gradient(180deg,rgba(0,0,0,.52),rgba(255,255,255,.04))",
                  boxShadow: "0 1px 0 rgba(255,255,255,.05)" }}>
      <div style={{ position: "absolute", inset: 3, borderRadius: 12,
                    background: "linear-gradient(180deg,#04070C,#090D16)",
                    boxShadow: "inset 0 6px 14px rgba(0,0,0,.9), inset 0 -1px 0 rgba(255,255,255,.06), inset 3px 0 6px rgba(0,0,0,.55), inset -3px 0 6px rgba(0,0,0,.55)" }} />
      <div style={{ position: "absolute", left: 8, right: 8, bottom: 3.5, height: 1.4, borderRadius: 1,
                    background: "linear-gradient(90deg,transparent,rgba(255,255,255,.15),transparent)", filter: "blur(.3px)" }} />
      <button onClick={onAdd} style={{ position: "absolute", inset: 6, borderRadius: 12, cursor: "pointer",
               display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 9,
               border: "none", background: "transparent", padding: 0, fontFamily: FONT }}>
        <div style={{ width: 42, height: 42, borderRadius: 13, display: "grid", placeItems: "center", color: MUTE,
                      background: "linear-gradient(180deg,rgba(255,255,255,.07),rgba(255,255,255,.02))",
                      border: `1px dashed ${hx(ACC, .32)}`,
                      boxShadow: "inset 0 1px 0 rgba(255,255,255,.07)" }}>
          <Ico d={IC.plus} size={19} sw={2.4} />
        </div>
        {label && (
          <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: ".14em", color: "#454E66",
                         textTransform: "uppercase" }}>{label}</span>
        )}
      </button>
    </div>
  );
}

/* ══ LOGEMENT REMPLI = CARTE ═════════════════════════════════════════════════
   Une carte de haute qualité, pas un creux : trois plans empilés (la tranche
   basse, le corps, la lumière du haut), un liseré de séparation net, un reflet
   diagonal, et une ombre portée SOUS la carte — c'est elle qui donne le
   décollement. L'accent ne teinte que ce qui est engagé.
   ─────────────────────────────────────────────────────────────────────────── */
function FxCard({ fx, on, onToggle, onOpen, onRemove, height }) {
  const [hot, setHot] = useState(false);
  const lift = hot ? 2 : 0;
  return (
    <div onClick={onOpen} onPointerEnter={() => setHot(true)} onPointerLeave={() => setHot(false)}
         style={{ position: "relative", height, width: "100%", cursor: "pointer",
                  transform: `translateY(${-lift}px)`,
                  transition: "transform .16s cubic-bezier(.3,1.3,.5,1)" }}>
      {/* l'ombre portée SOUS la carte : c'est ce qui la décolle du fond */}
      <div style={{ position: "absolute", left: 5, right: 5, bottom: -7, height: 16, borderRadius: "50%",
                    background: "radial-gradient(ellipse at 50% 0%, rgba(0,0,0,.62), rgba(0,0,0,0) 72%)",
                    filter: "blur(1px)", transition: "opacity .16s", opacity: hot ? 0.85 : 1 }} />
      {/* la tranche : le bord inférieur de la carte, éclairé */}
      <div style={{ position: "absolute", inset: 0, top: 4, borderRadius: 14,
                    background: "linear-gradient(180deg,#39425C,#232A3D)",
                    boxShadow: "0 2px 0 rgba(255,255,255,.05)" }} />
      {/* le corps */}
      <div style={{ position: "absolute", inset: 0, borderRadius: 14, overflow: "hidden",
                    background: "linear-gradient(180deg,#2A3348 0%,#212938 52%,#1A2130 100%)",
                    border: `1px solid ${on ? hx(ACC, .38) : "rgba(255,255,255,.09)"}`,
                    boxShadow: on
                      ? `inset 0 1px 0 rgba(255,255,255,.14), inset 0 -14px 26px rgba(0,0,0,.34), 0 0 0 1px ${hx(ACC, .1)}`
                      : "inset 0 1px 0 rgba(255,255,255,.11), inset 0 -14px 26px rgba(0,0,0,.34)" }}>
        {/* reflet diagonal : la lumière du haut qui glisse sur la matière */}
        <div style={{ position: "absolute", left: -30, right: -30, top: -50, height: 90,
                      background: "linear-gradient(180deg,rgba(255,255,255,.11),rgba(255,255,255,0))",
                      transform: "rotate(-9deg)", pointerEvents: "none" }} />
        {/* teinte d'accent, seulement si la carte est engagée */}
        {on && <div style={{ position: "absolute", inset: 0, pointerEvents: "none",
                             background: `radial-gradient(ellipse at 50% 0%, ${hx(ACC, .13)}, transparent 62%)` }} />}

        <div style={{ position: "relative", height: "100%", display: "flex", flexDirection: "column",
                      padding: 12 }}>
          <div style={{ display: "flex", alignItems: "flex-start", gap: 9 }}>
            <div style={{ width: 36, height: 36, borderRadius: 11, flex: "none", display: "grid", placeItems: "center",
                          color: on ? SOFT : MUTE,
                          background: on
                            ? `radial-gradient(circle at 50% 20%, ${hx(ACC, .3)}, #161C2B 76%)`
                            : "radial-gradient(circle at 50% 20%, rgba(255,255,255,.09), #161C2B 76%)",
                          border: `1px solid ${on ? hx(ACC, .35) : "rgba(255,255,255,.08)"}`,
                          boxShadow: "inset 0 1px 0 rgba(255,255,255,.12), 0 2px 6px rgba(0,0,0,.45)" }}>
              <Ico d={fx.glyph} size={17} sw={2} />
            </div>
            <div style={{ minWidth: 0, flex: 1, paddingTop: 1 }}>
              <div style={{ fontSize: 12.5, fontWeight: 800, color: on ? INK : "#98A2B6",
                            whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{fx.name}</div>
              <div style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: ".13em", textTransform: "uppercase",
                            color: on ? ACC : DIM, marginTop: 2 }}>{fx.type}</div>
            </div>
          </div>

          <div style={{ marginTop: 9, fontSize: 10, lineHeight: 1.5, color: "#78829A",
                        display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>
            {fx.note}
          </div>

          <div style={{ flex: 1 }} />

          <div style={{ display: "flex", gap: 6 }}>
            <button onClick={(e) => { e.stopPropagation(); onToggle(); }} title="Engage / Bypass"
                    style={{ flex: 1, height: 26, borderRadius: 8, cursor: "pointer", display: "grid",
                             placeItems: "center", border: `1px solid ${on ? hx(ACC, .5) : "rgba(255,255,255,.1)"}`,
                             background: on ? `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})` : "linear-gradient(180deg,#2F3850,#252D40)",
                             color: on ? ACC_TEXT : DIM,
                             boxShadow: on ? `0 0 12px ${hx(ACC, .3)}, inset 0 1px 0 rgba(255,255,255,.4)` : "inset 0 1px 0 rgba(255,255,255,.08)",
                             transition: "all .2s cubic-bezier(.3,1.4,.5,1)" }}>
              <Ico d={IC.power} size={13} sw={2.3} />
            </button>
            <button onClick={(e) => { e.stopPropagation(); onRemove(); }} title="Remove"
                    style={{ width: 26, height: 26, borderRadius: 8, display: "grid", placeItems: "center",
                             color: DIM, cursor: "pointer", background: "linear-gradient(180deg,#2F3850,#252D40)",
                             border: "1px solid rgba(255,255,255,.1)",
                             boxShadow: "inset 0 1px 0 rgba(255,255,255,.08)" }}>
              <Ico d={IC.trash} size={13} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ══ LA LISTE D'EFFETS — moderne, minimale, professionnelle ══════════════════
   Une grille de cartes larges : glyphe, nom, type, explication. Pas de rail de
   catégories, pas de numéros, pas de colonnes de métadonnées. La sélection est
   un liseré d'accent net et un fond qui se relève légèrement.
   ─────────────────────────────────────────────────────────────────────────── */
function CatalogPage({ kind, onPick, onBack, full, selRef }) {
  const list = kind === "midi" ? MIDI_FX : AUDIO_FX;
  const [sel, setSel] = useState(0);
  selRef.current = sel;
  const cols = 2;

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 9, padding: "0 1px 11px" }}>
        <button onClick={onBack} style={{ ...btn({ width: 26, height: 26, borderRadius: 8, display: "grid",
                 placeItems: "center", color: MUTE, cursor: "pointer" }) }}>
          <Ico d={IC.chevronLeft} size={13} />
        </button>
        <span style={{ fontSize: 12.5, fontWeight: 800 }}>Add {kind === "midi" ? "MIDI" : "audio"} effect</span>
        <span style={{ fontSize: 9.5, color: MUTE, letterSpacing: ".09em", textTransform: "uppercase", fontWeight: 700 }}>
          {kind === "midi" ? "one per track" : `up to ${MAX_AUDIO} per track`}
        </span>
        <div style={{ flex: 1 }} />
        {full && <span style={{ fontSize: 9.5, color: ACC, fontWeight: 700 }}>limit reached</span>}
      </div>

      <div style={{ flex: 1, minHeight: 0, overflowY: "auto", paddingRight: 3,
                    display: "grid", gridTemplateColumns: `repeat(${cols}, 1fr)`, gap: 9, alignContent: "start" }}>
        {list.map((f, i) => {
          const on = i === sel;
          return (
            <div key={f.id} onClick={() => setSel(i)} onDoubleClick={() => !full && onPick(f)}
                 onPointerEnter={() => setSel(i)}
                 style={{ position: "relative", height: 68, borderRadius: 13, cursor: "pointer",
                          display: "flex", alignItems: "center", gap: 11, padding: "0 12px",
                          background: on
                            ? `linear-gradient(180deg,${hx(ACC, .13)},rgba(255,255,255,.015)), linear-gradient(180deg,#252E42,#1C2433)`
                            : "linear-gradient(180deg,#20283A,#1A2130)",
                          border: `1px solid ${on ? hx(ACC, .45) : "rgba(255,255,255,.07)"}`,
                          boxShadow: on
                            ? `inset 0 1px 0 rgba(255,255,255,.12), 0 6px 16px rgba(0,0,0,.45), 0 0 0 1px ${hx(ACC, .08)}`
                            : "inset 0 1px 0 rgba(255,255,255,.07), 0 3px 9px rgba(0,0,0,.32)",
                          transition: "all .16s cubic-bezier(.3,1.2,.5,1)" }}>
              <div style={{ width: 36, height: 36, borderRadius: 11, flex: "none", display: "grid", placeItems: "center",
                            color: on ? SOFT : MUTE,
                            background: on
                              ? `radial-gradient(circle at 50% 20%, ${hx(ACC, .3)}, #141A28 76%)`
                              : "radial-gradient(circle at 50% 20%, rgba(255,255,255,.08), #141A28 76%)",
                            border: `1px solid ${on ? hx(ACC, .34) : "rgba(255,255,255,.07)"}`,
                            boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 2px 6px rgba(0,0,0,.4)" }}>
                <Ico d={f.glyph} size={17} sw={1.9} />
              </div>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
                  <span style={{ fontSize: 12.5, fontWeight: 800, color: on ? INK : "#A8B2C6" }}>{f.name}</span>
                  <span style={{ fontSize: 8.5, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase",
                                 color: on ? ACC : DIM }}>{f.type}</span>
                </div>
                <div style={{ fontSize: 10, color: "#7C869C", marginTop: 3, whiteSpace: "nowrap",
                              overflow: "hidden", textOverflow: "ellipsis" }}>{f.note}</div>
              </div>
              <div style={{ width: 30, height: 30, borderRadius: 10, flex: "none", display: "grid", placeItems: "center",
                            color: full ? "#3E4658" : on ? ACC_TEXT : DIM,
                            background: full ? "rgba(255,255,255,.03)"
                              : on ? `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})` : "rgba(255,255,255,.045)",
                            border: `1px solid ${on && !full ? "transparent" : "rgba(255,255,255,.08)"}`,
                            boxShadow: on && !full ? `0 3px 10px ${hx(ACC, .3)}` : "none",
                            transition: "all .16s" }}>
                <Ico d={full ? IC.check : IC.plus} size={14} sw={2.4} />
              </div>
            </div>
          );
        })}
      </div>

    </div>
  );
}

function Handle({ pos, onDown }) {
  return <div onPointerDown={onDown} style={{ position: "absolute", zIndex: 6, ...pos,
    cursor: pos.right !== undefined ? "ew-resize" : "nwse-resize" }} />;
}

/* ══ LA FENÊTRE ══════════════════════════════════════════════════════════════
   Cadre de référence : 891 × 411, celui de l'app.
   ─────────────────────────────────────────────────────────────────────────── */
function FxWindow() {
  const [audio, setAudio] = useState([AUDIO_FX[0]]);
  const [midi, setMidi] = useState([]);
  const [on, setOn] = useState({});
  const [view, setView] = useState(() => {
    const h = (typeof location !== "undefined" ? location.hash : "").replace("#", "");
    if (h === "midi") return { k: "catalog", kind: "midi" };
    if (h === "audio") return { k: "catalog", kind: "audio" };
    if (h === "space") return { k: "effect", fx: AUDIO_FX[0] };
    return { k: "rack" };
  });
  const drag = useRef(null);
  const catalogSel = useRef(0);

  const engaged = (f) => on[f.id] !== false;
  const toggle = (f) => setOn((s) => ({ ...s, [f.id]: s[f.id] === false }));
  const pick = (f) => {
    if (view.k === "catalog" && view.kind === "midi") setMidi([f]);
    else if (view.k === "catalog" && audio.length < MAX_AUDIO) setAudio((a) => [...a, f]);
    setView({ k: "effect", fx: f });
  };

  const HEAD = 46, FOOT = 44, PAD = 12;
  const bodyH = 411 - HEAD - FOOT - PAD * 2;
  const cardH = clamp(bodyH - 20, 150, 240);
  /* Space est dessine a sa taille native 891 x 411 : on le met a l'echelle du
     corps de la fenetre, la plus grande qui tienne dans les deux sens. */
  const spaceScale = Math.min((891 - PAD * 2) / 891, bodyH / 411);

  return (
    <div data-shot="fxwin" style={{ position: "relative", width: 891, height: 411, borderRadius: 20,
                  overflow: "hidden", fontFamily: FONT, color: INK, ...win(), border: `1px solid ${LINE2}`,
                  boxShadow: "0 26px 64px rgba(0,0,0,.6), inset 0 1px 0 rgba(255,255,255,.06)",
                  userSelect: "none", WebkitFontSmoothing: "antialiased" }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: .028, pointerEvents: "none" }} />

      {/* EN-TÊTE */}
      <div style={{ position: "relative", height: HEAD, display: "flex", alignItems: "center", gap: 9,
                    padding: "0 12px", borderBottom: `1px solid ${LINE}`,
                    background: "linear-gradient(180deg,rgba(255,255,255,.03),transparent)" }}>
        {view.k !== "rack" ? (
          <button onClick={() => setView({ k: "rack" })} style={{ ...btn({ width: 26, height: 26, borderRadius: 8,
                   display: "grid", placeItems: "center", color: MUTE, cursor: "pointer" }) }}>
            <Ico d={IC.chevronLeft} size={13} />
          </button>
        ) : (
          <div style={{ width: 26, height: 26, borderRadius: 8, ...btn({ display: "grid", placeItems: "center", color: MUTE }) }}>
            <Ico d={IC.grid} size={14} />
          </div>
        )}
        <span style={{ fontSize: 12.5, fontWeight: 800 }}>
          {view.k === "effect" ? view.fx.name : view.k === "catalog" ? "Add effect" : "Track effects"}
        </span>
        <span style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".13em", color: MUTE, textTransform: "uppercase" }}>
          {view.k === "effect" ? view.fx.type : "Track 3"}
        </span>
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 9.5, fontFamily: MONO, color: MUTE }}>891×411</span>
        <button style={{ ...btn({ width: 26, height: 26, borderRadius: 8, display: "grid", placeItems: "center",
                 color: MUTE, cursor: "pointer" }) }}><Ico d={IC.close} size={14} /></button>
      </div>

      {/* CORPS */}
      <div style={{ position: "absolute", left: PAD, right: PAD, top: HEAD + PAD, height: bodyH, overflow: "hidden" }}>

        {view.k === "rack" && (
          <div style={{ display: "flex", gap: 11, height: "100%" }}>
            {/* SLOT AUDIO */}
            <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 8, minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, paddingLeft: 3 }}>
                <span style={{ fontSize: 9, fontWeight: 800, letterSpacing: ".16em", textTransform: "uppercase", color: MUTE }}>
                  Slot Audio
                </span>
                <span style={{ fontSize: 9, fontFamily: MONO, color: audio.length >= MAX_AUDIO ? ACC : MUTE }}>
                  {audio.length}/{MAX_AUDIO}
                </span>
                <div style={{ flex: 1, height: 1, background: LINE }} />
              </div>
              <div style={{ flex: 1, display: "flex", gap: 9, minHeight: 0 }}>
                {Array.from({ length: MAX_AUDIO }, (_, i) => {
                  const f = audio[i];
                  return (
                    <div key={f ? f.id : "e" + i} style={{ flex: 1, minWidth: 0 }}>
                      {f
                        ? <FxCard fx={f} height={cardH} on={engaged(f)} onToggle={() => toggle(f)}
                                  onOpen={() => setView({ k: "effect", fx: f })}
                                  onRemove={() => setAudio((a) => a.filter((x) => x.id !== f.id))} />
                        : <EmptySlot height={cardH} label="Empty" onAdd={() => setView({ k: "catalog", kind: "audio" })} />}
                    </div>
                  );
                })}
              </div>
            </div>

            {/* séparateur */}
            <div style={{ width: 1, flex: "none", position: "relative",
                          background: `linear-gradient(180deg,transparent,${LINE2} 18%,${LINE2} 82%,transparent)` }}>
              <div style={{ position: "absolute", left: -3, top: "50%", marginTop: -3, width: 7, height: 7,
                            borderRadius: 4, background: "#2A3244", border: `1px solid ${LINE2}` }} />
            </div>

            {/* SLOT MIDI */}
            <div style={{ flex: "0 0 27%", display: "flex", flexDirection: "column", gap: 8, minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, paddingLeft: 3 }}>
                <span style={{ fontSize: 9, fontWeight: 800, letterSpacing: ".16em", textTransform: "uppercase", color: MUTE }}>
                  Slot MIDI
                </span>
                <span style={{ fontSize: 9, fontFamily: MONO, color: midi.length >= MAX_MIDI ? ACC : MUTE }}>
                  {midi.length}/{MAX_MIDI}
                </span>
                <div style={{ flex: 1, height: 1, background: LINE }} />
              </div>
              {midi.length
                ? <FxCard fx={midi[0]} height={cardH} on={engaged(midi[0])} onToggle={() => toggle(midi[0])}
                          onOpen={() => setView({ k: "effect", fx: midi[0] })}
                          onRemove={() => setMidi([])} />
                : <EmptySlot height={cardH} label="Empty" onAdd={() => setView({ k: "catalog", kind: "midi" })} />}
            </div>
          </div>
        )}

        {view.k === "catalog" && (
          <CatalogPage kind={view.kind} onPick={pick} onBack={() => setView({ k: "rack" })}
                       selRef={catalogSel}
                       full={view.kind === "audio" && audio.length >= MAX_AUDIO} />
        )}

        {/* LE PANNEAU DE L'EFFET : celui de Space est le vrai, importé tel quel */}
        {view.k === "effect" && (
          view.fx.space ? (
            <div style={{ width: "100%", height: "100%", display: "grid", placeItems: "center" }}>
              <div style={{ width: 891 * spaceScale, height: 411 * spaceScale, position: "relative" }}>
                <div style={{ width: 891, height: 411, transform: `scale(${spaceScale})`, transformOrigin: "top left",
                              position: "absolute", left: 0, top: 0, borderRadius: 20, overflow: "hidden",
                              boxShadow: "0 12px 34px rgba(0,0,0,.5)" }}>
                  <SpaceWindow />
                </div>
              </div>
            </div>
          ) : (
            <div style={{ width: "100%", height: "100%", borderRadius: 16, ...well({ borderRadius: 16,
                          display: "grid", placeItems: "center", position: "relative", overflow: "hidden" }) }}>
              <div style={{ position: "absolute", inset: 0,
                            background: `radial-gradient(ellipse at 50% 0%, ${hx(ACC, .1)}, transparent 68%)` }} />
              <div style={{ textAlign: "center", position: "relative" }}>
                <div style={{ width: 46, height: 46, margin: "0 auto 10px", borderRadius: 14, display: "grid",
                              placeItems: "center", color: SOFT,
                              background: `radial-gradient(circle at 50% 22%, ${hx(ACC, .23)}, #141A28 78%)`,
                              border: `1px solid ${hx(ACC, .33)}` }}>
                  <Ico d={view.fx.glyph} size={21} sw={1.9} />
                </div>
                <div style={{ fontSize: 14, fontWeight: 800, marginBottom: 4 }}>{view.fx.name}</div>
                <div style={{ fontSize: 11, color: DIM, maxWidth: 360, lineHeight: 1.6 }}>{view.fx.note}</div>
                <div style={{ marginTop: 12, fontSize: 10, color: MUTE, fontFamily: MONO }}>
                  {view.fx.pw} parameters
                </div>
              </div>
            </div>
          )
        )}
      </div>

      {/* PIED */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: FOOT, display: "flex",
                    alignItems: "center", gap: 9, padding: "0 12px", borderTop: `1px solid ${LINE}`,
                    background: "linear-gradient(0deg,rgba(255,255,255,.026),transparent)" }}>
        {view.k === "catalog" ? (
          <>
            <span style={{ fontSize: 10, color: MUTE }}>
              {view.kind === "midi" ? "One MIDI effect per track." : `Up to ${MAX_AUDIO} audio effects per track.`}
            </span>
            <div style={{ flex: 1 }} />
            <button onClick={() => setView({ k: "rack" })} style={{ ...btn({ height: 27, padding: "0 13px",
                     borderRadius: 9, color: MUTE, fontSize: 11, fontWeight: 800, fontFamily: FONT, cursor: "pointer" }) }}>
              Cancel
            </button>
            <button onClick={() => pick((view.kind === "midi" ? MIDI_FX : AUDIO_FX)[catalogSel.current])}
                    style={{ ...btn({ height: 27, padding: "0 17px", borderRadius: 9, border: 0,
                             background: `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})`, color: ACC_TEXT, fontSize: 11,
                             fontWeight: 900, fontFamily: FONT, cursor: "pointer",
                             boxShadow: `0 3px 12px ${hx(ACC, .27)}` }) }}>
              Add to track
            </button>
          </>
        ) : (
          <>
            <button style={{ ...btn({ height: 27, padding: "0 11px", borderRadius: 9, display: "inline-flex",
                     alignItems: "center", gap: 6, color: INK, fontSize: 11, fontWeight: 800, fontFamily: FONT, cursor: "pointer" }) }}>
              <Ico d={IC.drag} size={13} /> Reorder
            </button>
            <span style={{ fontSize: 10, color: MUTE }}>{audio.length} audio · {midi.length} MIDI</span>
            <div style={{ flex: 1 }} />
            <button style={{ ...btn({ height: 27, padding: "0 15px", borderRadius: 9, border: 0,
                     background: `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})`, color: ACC_TEXT, fontSize: 11,
                     fontWeight: 900, fontFamily: FONT, cursor: "pointer", boxShadow: `0 3px 12px ${hx(ACC, .27)}` }) }}>
              Done
            </button>
          </>
        )}
      </div>

      <Handle pos={{ right: 0, top: HEAD, bottom: FOOT, width: 7 }} onDown={(e) => (drag.current = e)} />
      <Handle pos={{ right: 0, bottom: 0, width: 18, height: 18 }} onDown={(e) => (drag.current = e)} />
      <div style={{ position: "absolute", right: 4, bottom: 4, width: 9, height: 9, pointerEvents: "none",
                    borderRight: `2px solid ${DIM}`, borderBottom: `2px solid ${DIM}`, borderRadius: 2, opacity: .6 }} />
    </div>
  );
}

/* ── habillage : l'écran de l'app, 891 × 411, mis à l'échelle pour la fenêtre ─ */
export default function FxWindowPreview() {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const fit = () => setScale(Math.min(1, clamp(Math.min((window.innerWidth - 60) / 891, (window.innerHeight - 130) / 411), 0.35, 1)));
    fit(); window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, []);
  return (
    <div style={{ minHeight: "100vh", background: `radial-gradient(ellipse at 50% 0%, ${BG_T}, #0A0E16 70%)`,
                  display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
                  gap: 14, padding: 16, boxSizing: "border-box", fontFamily: FONT }}>
      <div style={{ width: 891 * scale, height: 411 * scale, position: "relative" }}>
        <div data-shot="fxpage" style={{ width: 891, height: 411, transform: `scale(${scale})`, transformOrigin: "top left",
                      position: "absolute", left: 0, top: 0, borderRadius: 20, overflow: "hidden",
                      background: `linear-gradient(180deg,${BG_T},${BG_B})`, padding: 0, boxSizing: "border-box",
                      boxShadow: "0 34px 90px rgba(0,0,0,.7), 0 0 0 1px rgba(255,255,255,.06)" }}>
          <FxWindow />
        </div>
      </div>
      <div style={{ fontSize: 11.5, color: "#5B6580", textAlign: "center", maxWidth: 800, lineHeight: 1.65 }}>
        Æther FX — écran {SPACE_W} × {SPACE_H}, celui de l'app. Slots verticaux courts : creux quand vides,
        carte posée avec ombre portée quand remplis. Space est le vrai panneau, importé de FxSpacePreview.jsx.
      </div>
    </div>
  );
}
