import { useEffect, useRef, useState } from "react";
import { Scene } from "./FxSpacePreview.jsx";

/* ═════════════════════════════════════════════════════════════════════════════
   ÆTHER FX — fenêtre d'effets.

   RÉFÉRENCE : `fx-window-preview.html` (la fenêtre réellement branchée dans
   l'app) pour la structure, et le système de matière du rail de fader
   (`FaderRail.kt`) pour les surfaces. On suit, on n'invente pas.

   ÉCRAN : 891 × 411, le cadre de l'app.

   LES CLÉS DE PARAMÈTRES
   `Scene` de FxSpacePreview lit `size decay pre damp width mix diff`. Les clés
   internes de ce fichier sont EXACTEMENT celles-là, sinon un réglage bouge à
   l'écran et pas dans la salle. C'est ce qui est arrivé avec « pre-delay » et
   « diffuse », qui ne faisaient rien.
   ═════════════════════════════════════════════════════════════════════════════ */

const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const FONT = "Space Grotesk, Inter, system-ui, -apple-system, sans-serif";

const WIN_T = "#1F2635", WIN_B = "#131925";
const INK = "#DFE4EE", DIM = "#8D96AB", MUTE = "#5F6880";
const LINE = "rgba(255,255,255,.07)", LINE2 = "rgba(255,255,255,.13)";
const IN1 = "#C7D9F9", IN2 = "#9DB9EF", IN_TEXT = "#1B2440";
const ACC = "#8FB2E8";
const RAIL = "#2D3041", RAIL2 = "#474A63";

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

/* ── les icônes ───────────────────────────────────────────────────────────── */
const P = {
  back: "M15 18l-6-6 6-6", close: ["M18 6L6 18", "M6 6l12 12"], plus: ["M12 5v14", "M5 12h14"],
  power: ["M12 2v9", "M18.36 6.64a9 9 0 1 1-12.73 0"], trash: ["M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13"],
  search: ["M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M21 21l-4.3-4.3"],
  wave: "M2 12c2 0 2-4 4-4s2 8 4 8 2-8 4-8 2 4 4 4",
  sliders: ["M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6"],
  check: "M20 6L9 17l-5-5",
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

/* La disquette des Æther Instruments (`FloppyDiskIcon`, BottomBar.kt:936) :
   corps à coin chanfreiné en haut à droite, volet, fenêtre du volet, étiquette. */
function Floppy({ armed, size = 15, color = "#8E95A5" }) {
  const w = size, h = size, c = w * .12, n = w * .22;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" style={{ display: "block", flex: "none" }}>
      <path d={`M${c} 0 H${w - n} L${w} ${n} V${h - c} A${c} ${c} 0 0 1 ${w - c} ${h} H${c} A${c} ${c} 0 0 1 0 ${h - c} V${c} A${c} ${c} 0 0 1 ${c} 0 Z`}
            transform={`scale(${24 / w})`} fill={armed ? "#FF2A55" : color} />
      <rect x={w * .21} y={0} width={w * .58} height={h * .38} rx={c * .25} fill="#0C101A" />
      <rect x={w * .315} y={h * .084} width={w * .151} height={h * .209} rx="1" fill={armed ? "#FF2A55" : color} />
      <rect x={w * .14} y={h * .58} width={w * .72} height={h * .40} rx={c * .4}
            fill={armed ? "#FFB3BA" : "#ECEFF1"} opacity={.9} />
      <rect x={w * .24} y={h * .70} width={w * .52} height={h * .09} rx=".6" fill="#0C101A" opacity={.55} />
    </svg>
  );
}

/* ── les effets ───────────────────────────────────────────────────────────── */
const AUDIO = [
  { id: 0, name: "Æther Space", type: "Reverb", fam: "Space", accent: "#A99AD8", icon: P.wave, space: true,
    params: [["Size", "size", .62, 0, 100, " %"], ["Decay", "decay", .7, .4, 8, " s"],
             ["Pre-delay", "pre", .2, 0, 120, " ms"], ["Damp", "damp", .45, 1, 12, " kHz"],
             ["Width", "width", .8, 0, 100, " %"], ["Mix", "mix", .35, 0, 100, " %"],
             ["Diffuse", "diff", .5, 0, 100, " %"]],
    presets: { Hall: [.62, .7, .2, .45, .8, .35, .5], Plate: [.4, .5, .1, .7, .9, .3, .7],
               Room: [.25, .3, .05, .5, .6, .25, .3], Cathedral: [.95, .95, .35, .3, 1, .45, .8],
               Shimmer: [.7, .85, .25, .2, 1, .5, .9], Chamber: [.35, .4, .08, .55, .7, .28, .45],
               Bloom: [.8, .8, .3, .35, .95, .42, 1], Infinite: [1, 1, .4, .25, 1, .6, .95] } },
  { id: 1, name: "Æther Echo", type: "Delay", fam: "Space", accent: "#D4B06A", icon: P.sliders,
    params: [["Time", "time", .45, 20, 1200, " ms"], ["Feedback", "fb", .38, 0, 95, " %"],
             ["Tone", "tone", .55, 0, 100, " %"], ["Wow", "wow", .18, 0, 100, " %"],
             ["Width", "width", .7, 0, 100, " %"], ["Mix", "mix", .3, 0, 100, " %"]],
    presets: { Slapback: [.12, .18, .6, .1, .5, .28], Eighth: [.38, .42, .55, .22, .7, .32],
               Dotted: [.56, .48, .5, .25, .8, .34], Ambient: [.72, .62, .38, .4, .95, .42],
               Dub: [.6, .78, .28, .55, .9, .45] } },
  { id: 2, name: "Prism Tone", type: "Equalizer", fam: "Tone", accent: "#8FD0FF", icon: P.sliders,
    params: [["Low", "low", .5, -15, 15, " dB"], ["Mid", "mid", .5, -15, 15, " dB"],
             ["High", "high", .5, -15, 15, " dB"], ["Mid Q", "q", .4, .3, 6, ""],
             ["Frequency", "freq", .5, 100, 8000, " Hz"], ["Mix", "mix", 1, 0, 100, " %"]],
    presets: { Flat: [.5, .5, .5, .4, .5, 1], Warm: [.62, .46, .54, .5, .45, 1],
               Bright: [.46, .52, .66, .45, .6, 1], Telephone: [.28, .68, .3, .8, .6, 1],
               Scoop: [.66, .32, .62, .5, .5, 1] } },
  { id: 3, name: "Iron Grip", type: "Compressor", fam: "Dynamics", accent: "#B8C2D4", icon: P.sliders,
    params: [["Threshold", "thr", .48, -60, 0, " dB"], ["Ratio", "ratio", .35, 1, 20, ":1"],
             ["Attack", "atk", .25, .1, 100, " ms"], ["Release", "rel", .52, 10, 1000, " ms"],
             ["Knee", "knee", .4, 0, 24, " dB"], ["Makeup", "gain", .4, 0, 24, " dB"]],
    presets: { Glue: [.55, .2, .35, .6, .6, .3], Gentle: [.4, .1, .25, .5, .7, .2],
               Punch: [.5, .6, .6, .3, .3, .5], Limit: [.7, .95, .05, .2, .1, .6],
               Vocal: [.45, .4, .3, .55, .8, .35], DrumBus: [.5, .4, .2, .25, .3, .45] } },
  { id: 4, name: "Solar Ceiling", type: "Maximizer", fam: "Dynamics", accent: "#C9852C", icon: P.sliders,
    params: [["Amount", "amt", .5, 0, 100, " %"], ["Ceiling", "ceil", .92, -6, 0, " dB"],
             ["Release", "rel", .4, 10, 500, " ms"], ["Width", "width", .3, 0, 100, " %"],
             ["Mix", "mix", 1, 0, 100, " %"], ["Output", "out", .5, -12, 12, " dB"]],
    presets: { Transparent: [.3, .95, .6, .2, 1, .5], Loud: [.75, .9, .35, .4, 1, .55],
               Punch: [.6, .9, .2, .2, .9, .55], Wide: [.5, .92, .5, .8, 1, .5] } },
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
const fmt = ([, v, mn, mx, u]) => {
  const x = mn + v * (mx - mn);
  const d = Math.abs(x) < 10 && !Number.isInteger(mx - mn) ? x.toFixed(2) : Math.round(x);
  return `${d}${u}`;
};

/* ── les surfaces : la matière du rail de fader ───────────────────────────── */
const railMat = (x = {}) => ({
  background: `linear-gradient(180deg,${RAIL},#313443 10%,#373A4B 38%,#3A3C4F 60%,#3E4155 85%,${RAIL2})`,
  boxShadow: `inset 0 1px 0 rgba(255,255,255,.16), inset 1px 0 0 rgba(255,255,255,.07),
              inset -1px 0 0 rgba(0,0,0,.16), inset 0 -1px 0 rgba(255,255,255,.10), 0 3px 8px rgba(0,0,0,.45)`,
  ...x });
const well = (x = {}) => ({ background: "linear-gradient(180deg,#0E1420,#0B0F18)",
  boxShadow: "inset 0 1px 0 rgba(255,255,255,.14), inset 0 3px 10px rgba(0,0,0,.62), inset 0 0 0 1px rgba(255,255,255,.04)",
  borderRadius: 12, ...x });
const ghost = (x = {}) => ({ background: `linear-gradient(180deg,${RAIL},#252839)`,
  border: `1px solid ${LINE2}`, boxShadow: "inset 0 1px 0 rgba(255,255,255,.13), 0 2px 5px rgba(0,0,0,.35)",
  borderRadius: 10, ...x });

/* ══ LE POTARD, PARTAGÉ PAR TOUS LES FX ════════════════════════════════════
   Onze anneaux empilés, dans l'ordre où la lumière les touche :
     ombre portée · collerette extérieure · chanfrein haut · corps (cône) ·
     filet · cuvette (ombrage inversé) · rainure gravée · index lumineux ·
     reflet de vitre · halo de l'index · ombre basse.
   C'est la même construction que le rail : la lumière vient d'en haut à gauche.
   ─────────────────────────────────────────────────────────────────────────── */
function Knob({ label, value, onChange, display, accent = IN1, size = 48, onReset, onChangeStart }) {
  const drag = useRef(null);
  const [hot, setHot] = useState(false);
  const ang = -140 + clamp(value, 0, 1) * 280;
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 3, width: size + 18 }}>
      <div onPointerDown={(e) => { drag.current = { y: e.clientY, v: value };
                                    e.currentTarget.setPointerCapture(e.pointerId); setHot(true); onChangeStart?.(); }}
           onPointerMove={(e) => { if (!drag.current) return;
             onChange(clamp(drag.current.v + (drag.current.y - e.clientY) / 150, 0, 1)); }}
           onPointerUp={() => { drag.current = null; setHot(false); }}
           onDoubleClick={() => onReset && onReset()}
           style={{ width: size, height: size, position: "relative", cursor: "ns-resize", touchAction: "none" }}>
        <div style={{ position: "absolute", left: "14%", right: "14%", bottom: -2, height: 5, borderRadius: "50%",
                      background: "radial-gradient(ellipse, rgba(0,0,0,.6), transparent 72%)", filter: "blur(1.2px)" }} />
        <div style={{ position: "absolute", inset: 0, borderRadius: "50%",
                      background: `linear-gradient(160deg, ${RAIL2}, ${RAIL} 55%, #23263a)`,
                      boxShadow: `0 3px 9px rgba(0,0,0,.62), inset 0 1px 0 rgba(255,255,255,.2)` }} />
        <div style={{ position: "absolute", inset: "3%", borderRadius: "50%",
                      background: "conic-gradient(from 210deg, rgba(255,255,255,.16), rgba(255,255,255,0) 28%, rgba(0,0,0,.2) 62%, rgba(255,255,255,.12))",
                      opacity: .8 }} />
        <div style={{ position: "absolute", inset: "7%", borderRadius: "50%",
                      background: "radial-gradient(circle at 36% 24%, #4A4E64, #343850 42%, #23263A 82%, #1B1E2E)",
                      boxShadow: "inset 0 1px 1px rgba(255,255,255,.16), inset 0 -2px 5px rgba(0,0,0,.5)" }} />
        <div style={{ position: "absolute", inset: "19%", borderRadius: "50%",
                      background: "radial-gradient(circle at 50% 118%, rgba(255,255,255,.07), rgba(0,0,0,.42) 72%)",
                      boxShadow: "inset 0 1px 3px rgba(0,0,0,.65), inset 0 -1px 0 rgba(255,255,255,.07)" }} />
        <div style={{ position: "absolute", inset: "23%", borderRadius: "50%",
                      background: "radial-gradient(circle at 42% 22%, rgba(255,255,255,.05), rgba(0,0,0,.5) 78%)",
                      boxShadow: "inset 0 2px 5px rgba(0,0,0,.7)" }} />
        <div style={{ position: "absolute", left: "50%", top: "50%", width: size * .42, height: size * .42,
                      marginLeft: -size * .21, marginTop: -size * .21, transform: `rotate(${ang}deg)`,
                      borderRadius: "50%", pointerEvents: "none" }}>
          <div style={{ position: "absolute", left: "50%", top: 0, width: Math.max(1.6, size * .038),
                        height: size * .2, marginLeft: -Math.max(.8, size * .019), borderRadius: 2,
                        background: accent, boxShadow: `0 0 6px ${hx(accent, .85)}, 0 0 12px ${hx(accent, .4)}` }} />
        </div>
        <div style={{ position: "absolute", inset: "5%", borderRadius: "50%", pointerEvents: "none",
                      background: "linear-gradient(200deg, rgba(255,255,255,.11) 0%, rgba(255,255,255,.03) 26%, rgba(255,255,255,0) 46%)" }} />
        {hot && <div style={{ position: "absolute", inset: "-3%", borderRadius: "50%", pointerEvents: "none",
                              boxShadow: `0 0 0 1.5px ${hx(accent, .5)}, 0 0 14px ${hx(accent, .3)}` }} />}
      </div>
      <span style={{ fontSize: 8, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase",
                     color: hot ? INK : DIM, whiteSpace: "nowrap", maxWidth: size + 18,
                     overflow: "hidden", textOverflow: "ellipsis" }}>{label}</span>
      <span style={{ fontSize: 9.5, fontFamily: MONO, color: hot ? INK : "#7E879C" }}>{display}</span>
    </div>
  );
}

/* ══ LE FADER, PARTAGÉ PAR TOUS LES FX ═════════════════════════════════════
   Le rail des pistes, AMINCI — sa largeur, pas sa hauteur ni son échelle. Le
   bonnet est celui des pistes (`FaderThumb`), redessiné avec ses vraies cotes :
   ratio 1327/1657, LED à y=791 sur 1657, liseré latéral, reflet en haut.
   ─────────────────────────────────────────────────────────────────────────── */
const RAIL_W = 13, CAP_W = 26, CAP_H = 32;

function Fader({ label, value, onChange, display, accent = IN1, onReset, onChangeStart }) {
  const box = useRef(null);
  const drag = useRef(null);
  const [hot, setHot] = useState(false);
  const travel = 74;
  const capY = (1 - clamp(value, 0, 1)) * travel;
  const setFrom = (clientY) => {
    const r = box.current.getBoundingClientRect();
    onChange(clamp(1 - (clientY - r.top - CAP_H / 2) / travel, 0, 1));
  };
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 3, width: CAP_W + 16 }}>
      <div ref={box} onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId);
                                              setHot(true); onChangeStart?.(); setFrom(e.clientY); }}
           onPointerMove={(e) => { if (hot) setFrom(e.clientY); }}
           onPointerUp={() => setHot(false)}
           onDoubleClick={() => onReset && onReset()}
           style={{ width: CAP_W, height: travel + CAP_H, position: "relative", cursor: "ns-resize", touchAction: "none" }}>
        {/* le rail aminci : même matière que celui des pistes */}
        <div style={{ position: "absolute", left: "50%", top: CAP_H / 2, height: travel, width: RAIL_W,
                      marginLeft: -RAIL_W / 2, borderRadius: RAIL_W / 2, ...railMat({ borderRadius: RAIL_W / 2 }) }} />
        <div style={{ position: "absolute", left: "50%", top: CAP_H / 2 + 3, height: travel - 6, width: RAIL_W - 7,
                      marginLeft: -(RAIL_W - 7) / 2, borderRadius: (RAIL_W - 7) / 2,
                      background: "linear-gradient(180deg,#07090F,#0C0F18)",
                      boxShadow: "inset 0 2px 5px rgba(0,0,0,.85)" }} />
        {/* la partie parcourue, dans l'accent */}
        <div style={{ position: "absolute", left: "50%", bottom: CAP_H / 2 + 3, width: RAIL_W - 7,
                      height: Math.max(0, travel - 6 - (travel * clamp(value, 0, 1))),
                      marginLeft: -(RAIL_W - 7) / 2, borderRadius: (RAIL_W - 7) / 2,
                      background: `linear-gradient(180deg,${hx(accent, .28)},${hx(accent, .85)})`,
                      boxShadow: `0 0 8px ${hx(accent, .35)}` }} />
        {/* le bonnet */}
        <div style={{ position: "absolute", left: "50%", top: capY, width: CAP_W, height: CAP_H,
                      marginLeft: -CAP_W / 2, borderRadius: 7, overflow: "hidden",
                      background: `linear-gradient(180deg, #6E7488 0%, #565C70 18%, #43485C 52%, #383D50 78%, #2E3344 100%)`,
                      boxShadow: hot ? `0 0 0 1.5px ${hx(accent, .6)}, 0 6px 13px rgba(0,0,0,.65)`
                                     : "0 5px 11px rgba(0,0,0,.6), inset 0 1px 0 rgba(255,255,255,.24)",
                      transition: hot ? "none" : "box-shadow .15s" }}>
          <div style={{ position: "absolute", inset: 0,
                        background: "linear-gradient(90deg, rgba(255,255,255,.16), rgba(255,255,255,0) 22%, rgba(0,0,0,0) 74%, rgba(0,0,0,.26))" }} />
          <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: "38%",
                        background: "linear-gradient(180deg, rgba(255,255,255,.22), rgba(255,255,255,0))" }} />
          <div style={{ position: "absolute", left: 4, right: 4, top: "58%", height: 2, borderRadius: 2,
                        background: accent, opacity: .85, boxShadow: `0 0 6px ${hx(accent, .6)}` }} />
          <div style={{ position: "absolute", left: 4, right: 4, bottom: 4, height: 1,
                        background: "rgba(255,255,255,.1)" }} />
        </div>
      </div>
      <span style={{ fontSize: 8, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase",
                     color: hot ? INK : DIM, whiteSpace: "nowrap", maxWidth: CAP_W + 16,
                     overflow: "hidden", textOverflow: "ellipsis" }}>{label}</span>
      <span style={{ fontSize: 9.5, fontFamily: MONO, color: hot ? INK : "#7E879C" }}>{display}</span>
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
    <div style={{ display: "flex", gap: 2, padding: 3, borderRadius: 11, background: "#0B0F18",
                  border: `1px solid ${LINE}`, boxShadow: "inset 0 2px 6px rgba(0,0,0,.55)" }}>
      {options.map(([k, l, ic]) => {
        const on = value === k;
        return (
          <button key={k} onClick={() => onChange(k)} title={l}
                  style={{ height, minWidth: on ? 92 : 38, borderRadius: 8, border: 0, cursor: "pointer",
                           background: on ? `linear-gradient(180deg,${IN1},${IN2})` : "transparent",
                           color: on ? IN_TEXT : "#8791B5", display: "flex", alignItems: "center",
                           justifyContent: "center", gap: 6, fontSize: 11, fontWeight: 800,
                           fontFamily: FONT, transition: "all .18s" }}>
            {ic && <Ico d={ic} size={14} />}{on && l}
          </button>
        );
      })}
    </div>
  );
}

/* ══ LA CARTE ══════════════════════════════════════════════════════════════
   Elle occupe EXACTEMENT la place d'un logement : même hauteur, même largeur,
   même rayon. Ajouter un effet ne change donc rien à la géométrie du rack.
   Style de `fx-window-preview.html` : type en petites capitales, nom en grand,
   visuel au milieu, ligne de pied avec la LED.
   ─────────────────────────────────────────────────────────────────────────── */
function FxCard({ fx, midi, on, onToggle, onOpen, onRemove, onSwipe, height }) {
  const [hot, setHot] = useState(false);
  const [dy, setDy] = useState(0);
  const [swiping, setSwiping] = useState(false);
  const fireRef = useRef(() => {});
  const drag = useRef(null);
  const vals = Object.fromEntries((fx.params || []).map(([, k, v]) => [k, v]));
  const ac = fx.accent || ACC;
  const pct = clamp(-dy / 90, 0, 1);
  return (
    <div style={{ position: "relative", flex: 1, minWidth: 0, height }}>
      {/* la piste de suppression, révélée par le glissement */}
      <div style={{ position: "absolute", inset: 0, borderRadius: 16, overflow: "hidden",
                    background: `linear-gradient(180deg, ${hx("#FF3B5C", .10 + pct * .5)}, ${hx("#FF3B5C", .04 + pct * .34)})`,
                    boxShadow: "inset 0 0 0 1px rgba(255,59,92,.28)",
                    display: "flex", alignItems: "center", justifyContent: "center" }}>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 6,
                      opacity: .45 + pct * .55, transform: `scale(${.9 + pct * .14})` }}>
          <Ico d={P.trash} size={20} sw={2} style={{ color: "#FF7A90" }} />
          <span style={{ fontSize: 8, fontWeight: 900, letterSpacing: ".16em", textTransform: "uppercase",
                         color: "#FF7A90" }}>{pct > .82 ? "Release" : "Swipe up"}</span>
        </div>
      </div>
      <div onClick={() => { if (!swiping && dy === 0) onOpen(); }}
           onPointerEnter={() => setHot(true)} onPointerLeave={() => setHot(false)}
           onPointerDown={(e) => { drag.current = { y: e.clientY, t: e.timeStamp }; setSwiping(false); }}
           onPointerMove={(e) => {
             if (!drag.current) return;
             const d = e.clientY - drag.current.y;
             if (d < 0) { setDy(d); setSwiping(true); e.currentTarget.setPointerCapture(e.pointerId); }
             else if (d > 12) { drag.current = null; }
           }}
           onPointerUp={(e) => {
             if (!drag.current) return;
             const dt = Math.max(1, e.timeStamp - drag.current.t);
             const v = -dy / dt;                        // px par ms
             const flick = v > 0.55;                    // un geste bref suffit
             drag.current = null;
             if (-dy > 74 || flick) onSwipe();
             else { setDy(0); setSwiping(false); }
           }}
           style={{ position: "absolute", inset: 0, borderRadius: 16, padding: 10, cursor: "pointer",
                    display: "flex", flexDirection: "column", gap: 7,
                    transform: `translateY(${dy}px) scale(${1 - pct * .04})`,
                    opacity: 1 - pct * .55,
                    transition: drag.current ? "none" : "transform .42s cubic-bezier(.16,1,.3,1), opacity .3s",
                    background: `linear-gradient(180deg,${hot ? "#373E4C" : "#323845"},${hot ? "#252A37" : "#222733"})`,
                    border: `1px solid ${LINE2}`,
                    boxShadow: "0 8px 16px rgba(0,0,0,.5), inset 0 1px 0 rgba(255,255,255,.11)" }}>
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

        <div style={{ fontSize: 18, lineHeight: 1.05, fontWeight: 700, color: on ? INK : "#98A1B4",
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
            {midi ? "MIDI effect" : (fx.presets ? Object.keys(fx.presets)[0] : fx.type)}
          </span>
        </div>
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

/* ══ LE LOGEMENT VIDE : un creux, rien d'autre que le « + » ════════════════ */
function EmptySlot({ onAdd, height, label }) {
  return (
    <button onClick={onAdd} style={{ flex: 1, minWidth: 0, height, borderRadius: 16, cursor: "pointer",
             border: "none", padding: 0, fontFamily: FONT, position: "relative",
             background: "linear-gradient(180deg,#0E1420,#0B0F18)",
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

/* ══ LA FEUILLE D'AJOUT ════════════════════════════════════════════════════ */
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
      <div style={{ display: "flex", alignItems: "center", gap: 6, paddingBottom: 10, flex: "none" }}>
        {fams.map((f) => (
          <button key={f} onClick={() => setFam(f)}
                  style={{ height: 26, padding: "0 12px", borderRadius: 9, cursor: "pointer",
                           fontFamily: FONT, fontSize: 10.5, fontWeight: 800, letterSpacing: ".06em",
                           background: fam === f ? `linear-gradient(180deg,${IN1},${IN2})` : "rgba(255,255,255,.04)",
                           border: `1px solid ${fam === f ? "transparent" : LINE}`,
                           color: fam === f ? IN_TEXT : "#8791B5" }}>{f}</button>
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

/* ══ LE PANNEAU D'UN EFFET ═════════════════════════════════════════════════
   Un seul en-tête : celui de la fenêtre. La pastille de preset et la disquette
   sont dans la barre de titre. Au centre : la salle vivante (Space) ou le
   visuel, et la grille de réglages — knobs ET faders dans la MÊME case.
   ─────────────────────────────────────────────────────────────────────────── */
function FxPanel({ fx, vals, setVal, preset, onPreset, onSave, dirty, on, onToggle }) {
  const fireRef = useRef(() => {});
  const names = fx.presets ? Object.keys(fx.presets) : [];
  const idx = names.indexOf(preset);
  const step = (d) => { const n = names[(idx + d + names.length) % names.length]; if (n) onPreset(n); };
  const get = (k) => vals[k] ?? .5;
  const knobs = fx.params.slice(0, 4);
  const faders = fx.params.slice(4);
  return (
    <div style={{ display: "flex", gap: 12, height: "100%", minHeight: 0 }}>
      <div onClick={() => fireRef.current()}
           style={{ flex: "1 1 52%", minWidth: 0, position: "relative", borderRadius: 15, overflow: "hidden",
                    cursor: fx.space ? "crosshair" : "default", ...well({ borderRadius: 15 }) }}>
        {fx.space
          ? <Scene vals={vals} fireRef={fireRef} accent={fx.accent} hover />
          : <div style={{ height: "100%", display: "grid", placeItems: "center" }}>
              <MiniGraph type={fx.type} accent={fx.accent} on />
            </div>}
        <div style={{ position: "absolute", left: 11, top: 9, display: "flex", alignItems: "center", gap: 7,
                      padding: "4px 9px", borderRadius: 8, background: "rgba(10,14,26,.55)",
                      border: `1px solid ${LINE2}`, backdropFilter: "blur(6px)", pointerEvents: "none" }}>
          <span style={{ width: 5, height: 5, borderRadius: 3, background: fx.accent, boxShadow: `0 0 7px ${fx.accent}` }} />
          <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: ".09em", textTransform: "uppercase", color: "#C6CEE8" }}>{preset}</span>
          {fx.space && <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>{(.4 + get("decay") * 7.6).toFixed(1)} s</span>}
        </div>
        {fx.space && (
          <div style={{ position: "absolute", left: 11, bottom: 8, fontSize: 8.5, fontWeight: 800,
                        letterSpacing: ".1em", textTransform: "uppercase", color: MUTE, pointerEvents: "none" }}>
            Touch the room to re-fire
          </div>
        )}
        {!on && <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center",
                              background: "rgba(9,12,22,.66)", fontSize: 12, fontWeight: 800,
                              letterSpacing: ".2em", color: MUTE, textTransform: "uppercase" }}>Bypassed</div>}
      </div>

      <div style={{ flex: "1 1 48%", minWidth: 0, display: "flex", flexDirection: "column", gap: 9 }}>
        <div style={{ flex: 1, minHeight: 0, display: "flex", alignItems: "center", justifyContent: "space-around",
                      gap: 6, padding: "6px 4px", ...well({ borderRadius: 13 }) }}>
          {knobs.map(([n, k, def, mn, mx, u]) => (
            <Knob key={k} label={n} value={get(k)} accent={fx.accent} size={48}
                  onChange={(v) => setVal(k, v)} onReset={() => setVal(k, def)}
                  onChangeStart={() => onPreset && dirty === false && onPreset("Custom")}
                  display={fmt([n, get(k), mn, mx, u])} />
          ))}
          {faders.map(([n, k, def, mn, mx, u]) => (
            <Fader key={k} label={n} value={get(k)} accent={fx.accent}
                   onChange={(v) => setVal(k, v)} onReset={() => setVal(k, def)}
                   onChangeStart={() => onPreset && dirty === false && onPreset("Custom")}
                   display={fmt([n, get(k), mn, mx, u])} />
          ))}
        </div>
      </div>
    </div>
  );
}

/* ══ PANNEAU ARPEGGIATOR ═══════════════════════════════════════════════════ */
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

/* ══ LA FENÊTRE ═════════════════════════════════════════════════════════════ */
const HEAD = 46, PAD = 12, BODY_H = 411 - HEAD - PAD * 2;

function FxWindow() {
  const [midi, setMidi] = useState(() => ({ fx: MIDI[0], on: true }));
  const [audio, setAudio] = useState(() => [{ fx: AUDIO[0], on: true }]);
  const [vals, setVals] = useState(() => Object.fromEntries(
    AUDIO[0].params.map(([, k, v]) => [k, v])));
  const [preset, setPreset] = useState("Hall");
  const [custom, setCustom] = useState(false);
  const [view, setView] = useState(() => {
    const h = (typeof location !== "undefined" ? location.hash : "").replace("#", "");
    if (h === "midi") return { k: "catalog", kind: "midi" };
    if (h === "audio") return { k: "catalog", kind: "audio" };
    if (h === "arp") return { k: "effect", fx: MIDI[0] };
    if (h === "space") return { k: "effect", fx: AUDIO[0] };
    return { k: "rack" };
  });
  const [t, setT] = useState(0);
  const [arp, setArp] = useState(() => ({
    sub: 2, style: "up", steps: 16,
    vel: Array.from({ length: 16 }, (_, i) => (i % 4 === 0 ? 1 : i % 2 === 0 ? .78 : .62)),
    on: Array(16).fill(true),
  }));

  /* Changer un réglage à la main fait passer le preset à Custom, tout de suite. */
  const setVal = (k, v) => {
    setVals((s) => ({ ...s, [k]: v }));
    if (!custom) { setCustom(true); setPreset("Custom"); }
  };
  const applyPreset = (n) => {
    const row = AUDIO[0].presets[n];
    if (row) setVals(Object.fromEntries(AUDIO[0].params.map(([, k], i) => [k, row[i]])));
    setPreset(n); setCustom(n === "Custom");
  };
  const names = Object.keys(AUDIO[0].presets);
  const idx = names.indexOf(preset);
  const step = (d) => applyPreset(names[(idx + d + names.length) % names.length] || names[0]);

  useEffect(() => {
    const id = setInterval(() => setT((x) => x + 1), 110);
    return () => clearInterval(id);
  }, []);
  const cur = Math.floor(t / 2) % arp.steps;

  const inEffect = view.k === "effect";
  const isSpace = inEffect && view.fx.space;
  const isArp = inEffect && view.fx.type === "arp";
  const isMidiFx = inEffect && !view.fx.space && view.fx.type !== "arp";

  /* UN SEUL EN-TÊTE. En mode rack il dit « Effects » ; dans un effet il dit son
     nom. Le fil d'Ariane vit à gauche, les actions à droite. */
  const title = inEffect ? view.fx.name : view.k === "catalog" ? "Add effect" : "Effects";

  return (
    <div data-shot="fxwin" style={{ position: "relative", width: 891, height: 411, borderRadius: 20, overflow: "hidden",
                  fontFamily: FONT, color: INK, background: `linear-gradient(180deg,${WIN_T},${WIN_B})`,
                  border: `1px solid ${LINE2}`, boxShadow: "0 24px 50px rgba(0,0,0,.7), inset 0 1px 0 rgba(255,255,255,.08)",
                  userSelect: "none", WebkitFontSmoothing: "antialiased", display: "flex", flexDirection: "column" }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: .05, mixBlendMode: "overlay", pointerEvents: "none" }} />

      <div style={{ height: HEAD, flex: "none", display: "flex", alignItems: "center", gap: 10, padding: "0 12px",
                    position: "relative", zIndex: 2 }}>
        {view.k !== "rack" ? (
          <button onClick={() => setView({ k: "rack" })} style={{ ...ghost({ width: 28, height: 28, display: "grid",
                   placeItems: "center", color: DIM, cursor: "pointer" }) }}><Ico d={P.back} size={14} /></button>
        ) : (
          <span style={{ width: 4, height: 24, borderRadius: 2, background: "#7F9BC7", flex: "none" }} />
        )}
        <span style={{ fontSize: 13, fontWeight: 900, letterSpacing: ".02em" }}>{title}</span>

        {/* la pastille de preset : elle n'existe que dans un effet qui en a */}
        {isSpace && (
          <>
            <button onClick={() => step(-1)} style={{ ...ghost({ width: 24, height: 24, display: "grid",
                     placeItems: "center", color: DIM, cursor: "pointer", borderRadius: 8 }) }}>
              <Ico d={P.back} size={12} /></button>
            <div style={{ height: 26, minWidth: 152, display: "flex", alignItems: "center", justifyContent: "space-between",
                          gap: 9, padding: "0 10px", ...well({ borderRadius: 8, height: 26 }) }}>
              <span style={{ fontSize: 11, fontWeight: 800, color: custom ? "#F2C97D" : INK }}>{preset}</span>
              <span style={{ fontSize: 9, fontFamily: MONO, color: MUTE }}>
                {custom ? "custom" : `${idx + 1}/${names.length}`}
              </span>
            </div>
            <button onClick={() => step(1)} style={{ ...ghost({ width: 24, height: 24, display: "grid",
                     placeItems: "center", color: DIM, cursor: "pointer", borderRadius: 8 }) }}>
              <Ico d={P.back} size={12} style={{ transform: "rotate(180deg)" }} /></button>
          </>
        )}

        <div style={{ flex: 1 }} />

        {view.k === "rack" && (
          <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase", color: MUTE }}>
            {audio.length + (midi.fx ? 1 : 0)} / {MAX_AUDIO + MAX_MIDI}
          </span>
        )}

        {/* la disquette : même icône que les Æther Instruments */}
        {isSpace && (
          <button onClick={() => onSave && onSave(preset)} title="Save preset"
                  style={{ ...ghost({ width: 28, height: 28, display: "grid", placeItems: "center",
                           cursor: "pointer", opacity: custom ? 1 : .45 }) }}>
            <Floppy armed={custom} size={14} />
          </button>
        )}

        <button title="Close" style={{ ...ghost({ width: 28, height: 28, display: "grid", placeItems: "center",
                 color: DIM, cursor: "pointer" }) }}><Ico d={P.close} size={14} /></button>
      </div>

      <div style={{ flex: 1, minHeight: 0, padding: `0 ${PAD}px ${PAD}px`, position: "relative", zIndex: 2 }}>
        {view.k === "rack" && (
          <div style={{ display: "flex", gap: 10, height: "100%" }}>
            {Array.from({ length: MAX_AUDIO }, (_, i) => {
              const a = audio[i];
              return a
                ? <FxCard key={a.fx.id} fx={a.fx} height={BODY_H} on={a.on}
                          onToggle={() => setAudio((s) => s.map((x, k) => k === i ? { ...x, on: !x.on } : x))}
                          onOpen={() => setView({ k: "effect", fx: a.fx })}
                          onSwipe={() => setAudio((s) => s.filter((_, k) => k !== i))}
                          onRemove={() => setAudio((s) => s.filter((_, k) => k !== i))} />
                : <EmptySlot key={"e" + i} height={BODY_H} label="Audio"
                             onAdd={() => setView({ k: "catalog", kind: "audio" })} />;
            })}
            {/* le MIDI est à DROITE, après les audio */}
            {midi.fx
              ? <FxCard fx={midi.fx} midi height={BODY_H} on={midi.on}
                        onToggle={() => setMidi((m) => ({ ...m, on: !m.on }))}
                        onOpen={() => setView({ k: "effect", fx: midi.fx })}
                        onSwipe={() => setMidi({ fx: null, on: true })}
                        onRemove={() => setMidi({ fx: null, on: true })} />
              : <EmptySlot height={BODY_H} label="MIDI" onAdd={() => setView({ k: "catalog", kind: "midi" })} />}
          </div>
        )}

        {view.k === "catalog" && (
          <Catalog kind={view.kind} onBack={() => setView({ k: "rack" })}
                   full={view.kind === "audio" && audio.length >= MAX_AUDIO}
                   onPick={(f) => {
                     if (view.kind === "midi") { setMidi({ fx: f, on: true }); setView({ k: "effect", fx: f }); }
                     else { setAudio((s) => [...s, { fx: f, on: true }]); setView({ k: "effect", fx: f }); }
                   }} />
        )}

        {inEffect && (
          isArp
            ? <ArpPanel p={arp} set={setArp} cur={cur} />
            : <FxPanel fx={view.fx} vals={vals} setVal={setVal} preset={preset}
                       onPreset={applyPreset} onSave={() => {}} dirty={custom}
                       on={isSpace ? audio[0].on : (isMidiFx ? midi.on : true)}
                       onToggle={() => (isSpace ? setAudio((s) => s.map((x, k) => k === 0 ? { ...x, on: !x.on } : x))
                                                : setMidi((m) => ({ ...m, on: !m.on })))} />
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
        Æther FX — écran 891 × 411. Un seul en-tête. MIDI à droite. Glisser une carte vers le haut pour
        la supprimer. Knobs et faders dans la même case, dans la matière du rail des pistes.
      </div>
    </div>
  );
}
