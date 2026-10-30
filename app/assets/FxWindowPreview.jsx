import { useEffect, useRef, useState } from "react";

/* ═════════════════════════════════════════════════════════════════════════════
   ÆTHER FX — fenêtre d'effets.  Écran 891 × 411.

   STRUCTURE : `fx-window-preview.html`, la fenêtre réellement branchée.
   ORBE DE SPACE : `orb()` de cette même référence, porté tel quel. Il change
     DÉJÀ de couleur — `hue = 268 - damp * 75`, du violet au bleu. On ne
     réinvente pas, on porte.
   POTARD ET BONNET : dessinés ici, inédits. La référence n'est pas recopiée.
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
  /* Power : un anneau presque ferme, ouvert en haut, avec une hampe courte.
     Trace a la main sur un carre de 24, centre optique au milieu du cercle. */
  power: ["M12 7.2v5.6", "M7.9 9.3a6.1 6.1 0 1 0 8.2 0"],
  /* Corbeille : couvercle a deux extremites, anse, corps bombe qui se retrecit,
     et deux nervures. Rien de plus : quatre traits lisibles. */
  trash: ["M3.8 6.6h16.4", "M9.4 6.6V4.7a.9.9 0 0 1 .9-.9h3.4a.9.9 0 0 1 .9.9v1.9",
          "M6.1 6.6l.78 12.4a1.8 1.8 0 0 0 1.8 1.7h6.64a1.8 1.8 0 0 0 1.8-1.7l.78-12.4",
          "M10.2 10.6v6.2", "M13.8 10.6v6.2"],
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

/* ── la disquette : celle des Æther Instruments, avec son animation ───────── */
function Floppy({ armed, size = 15 }) {
  const w = 24, h = 24, c = w * .12, n = w * .22;
  const body = armed ? "#FF2A55" : "#8E95A5";
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" style={{ display: "block", flex: "none", overflow: "visible" }}>
      {armed && (
        <path d={`M${c} 0 H${w - n} L${w} ${n} V${h - c} A${c} ${c} 0 0 1 ${w - c} ${h} H${c} A${c} ${c} 0 0 1 0 ${h - c} V${c} A${c} ${c} 0 0 1 ${c} 0 Z`}
              fill="#FF2A55" style={{ animation: "flopPulse 1.6s ease-in-out infinite" }} />
      )}
      <path d={`M${c} 0 H${w - n} L${w} ${n} V${h - c} A${c} ${c} 0 0 1 ${w - c} ${h} H${c} A${c} ${c} 0 0 1 0 ${h - c} V${c} A${c} ${c} 0 0 1 ${c} 0 Z`}
            fill={body} />
      <rect x={w * .21} y={0} width={w * .58} height={h * .38} rx={c * .25} fill="#0C101A" />
      <rect x={w * .315} y={h * .084} width={w * .151} height={h * .209} rx="1" fill={body} />
      <rect x={w * .14} y={h * .58} width={w * .72} height={h * .40} rx={c * .4}
            fill={armed ? "#FFB3BA" : "#ECEFF1"} opacity={.92} />
      <rect x={w * .24} y={h * .70} width={w * .52} height={h * .09} rx=".6" fill="#0C101A" opacity={.55} />
      {armed && <rect x={w * .14} y={h * .58} width={w * .72} height={h * .40} rx={c * .4} fill="#fff"
                     style={{ animation: "flopWrite 1.6s ease-out infinite" }} />}
    </svg>
  );
}

/* ── les effets ───────────────────────────────────────────────────────────── */
/* Space : dix réglages. Les trois derniers sont nouveaux — Low Cut et High
   Shelf sont des réglages de reverb qu'on attend, Mod l'ondulation stéréo.
   Les presets portent DIX valeurs, dans l'ordre de `params`. */
const AUDIO = [
  { id: 0, name: "Æther Space", type: "Reverb", fam: "Space", accent: "#A99AD8", icon: P.wave, space: true,
    params: [
      ["Size", "size", .62, 0, 100, " %"], ["Decay", "decay", .7, .4, 8, " s"],
      ["Pre-delay", "pre", .2, 0, 120, " ms"], ["Damp", "damp", .45, 1, 12, " kHz"],
      ["Width", "width", .8, 0, 100, " %"], ["Mix", "mix", .35, 0, 100, " %"],
      ["Diffuse", "diff", .5, 0, 100, " %"], ["Low Cut", "lowcut", .25, 20, 400, " Hz"],
      ["Hi Shelf", "shelf", .55, -12, 6, " dB"], ["Mod", "mod", .3, 0, 100, " %"],
    ],
    presets: {
      Hall:         [.62, .70, .20, .45, .80, .35, .50, .30, .50, .35],
      Plate:        [.40, .50, .10, .70, .90, .30, .70, .35, .60, .25],
      Room:         [.25, .30, .05, .50, .60, .25, .30, .20, .40, .15],
      Cathedral:    [.95, .95, .35, .30, 1.0, .45, .80, .45, .55, .55],
      Shimmer:      [.70, .85, .25, .20, 1.0, .50, .90, .40, .70, .65],
      Chamber:      [.35, .40, .08, .55, .70, .28, .45, .25, .45, .20],
      Bloom:        [.80, .80, .30, .35, .95, .42, 1.0, .35, .65, .60],
      Infinite:     [1.0, 1.0, .40, .25, 1.0, .60, .95, .50, .50, .70],
      Ambience:     [.55, .88, .30, .60, .95, .28, .85, .40, .45, .45],
      Spring:       [.30, .45, .02, .75, .55, .40, .25, .15, .70, .80],
      "Dark Hall":  [.78, .82, .28, .22, .85, .38, .65, .55, .35, .30],
      "Bright Hall":[.60, .62, .16, .78, .88, .32, .55, .20, .68, .40],
      "Vocal Plate":[.38, .46, .06, .66, .95, .30, .78, .42, .58, .20],
      "Drum Room":  [.28, .26, .01, .42, .70, .24, .35, .12, .46, .10],
      "Chorus Verb":[.66, .74, .22, .58, 1.0, .40, .72, .30, .62, .85],
      Gated:        [.44, .22, .00, .68, .60, .34, .30, .25, .52, .00],
    },
    palettes: {
      /* Chaque preset emprunte ses couleurs a une nebuleuse reelle : la teinte
         principale au coeur, la teinte secondaire aux volutes, et le bord sombre
         a la poussiere. Ce sont des presets EXISTANTS — on ne fait que leur
         donner des couleurs de reference. */
      Hall:          { h: 268, dh: 75, s: 48, l: 74, core: "#eee8ff", rim: "#1d1a38" },  // Voile, bleu-violet
      Plate:         { h: 196, dh: 40, s: 62, l: 76, core: "#f0fbff", rim: "#0f2530" },  // Pléiades, bleu pale
      Room:          { h: 158, dh: 34, s: 34, l: 68, core: "#eafaf4", rim: "#12241f" },  // vert sourd
      Cathedral:     { h: 246, dh: 60, s: 56, l: 66, core: "#e9e4ff", rim: "#161034" },  // indigo profond
      Shimmer:       { h: 312, dh: 90, h2: 186, multi: true, s: 66, l: 76,
                       core: "#fff0fb", rim: "#2a1030" },                                // Carène : magenta + cyan
      Chamber:       { h: 172, dh: 40, s: 40, l: 70, core: "#eefffb", rim: "#0e2a28" },  // turquoise
      Bloom:         { h: 328, dh: 70, h2: 268, multi: true, s: 58, l: 74,
                       core: "#ffeef6", rim: "#2c1030" },                                // Rosette : rose + violet
      Infinite:      { h: 210, dh: 50, s: 30, l: 84, core: "#ffffff", rim: "#101a2e" },  // blanc glacé
      Ambience:      { h: 224, dh: 44, s: 36, l: 64, core: "#e6ecff", rim: "#0d1424" },  // bleu nuit
      Spring:        { h: 34,  dh: 26, s: 62, l: 70, core: "#fff4e2", rim: "#2a1a0c" },  // ambre
      /* Tête de Cheval : la lueur orange derriere la poussiere noire. */
      "Dark Hall":   { h: 22,  dh: 16, h2: 268, multi: true, s: 68, l: 58,
                       core: "#ffd9a8", rim: "#07050e" },
      /* Nébuleuse du Crabe : soufre jaune et oxygene vert. */
      "Bright Hall": { h: 48,  dh: 30, h2: 118, multi: true, s: 56, l: 74,
                       core: "#fff8e2", rim: "#2a2410" },
      /* Nébuleuse de l'Aigle (Piliers de la Création) : or et jade. */
      "Vocal Plate": { h: 42,  dh: 26, h2: 152, multi: true, s: 52, l: 74,
                       core: "#fff2dc", rim: "#241c0e" },
      /* Nébuleuse de l'Hélice : oeil cyan dans un halo vert. */
      "Drum Room":   { h: 186, dh: 40, h2: 138, multi: true, s: 46, l: 66,
                       core: "#e8fbff", rim: "#0c2224" },
      /* Nébuleuse du Papillon : cyan et magenta separes par la poussiere. */
      "Chorus Verb": { h: 184, dh: 40, h2: 300, multi: true, s: 60, l: 74,
                       core: "#eaffff", rim: "#0c2430" },
      /* Nébuleuse de l'Homunculus : magenta profond et orange. */
      Gated:         { h: 318, dh: 30, h2: 26, multi: true, s: 64, l: 62,
                       core: "#ffe4f4", rim: "#26081c" },
    },
  },
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
const fmt = (v, mn, mx, u) => {
  const x = mn + v * (mx - mn);
  const d = Math.abs(x) < 10 && !Number.isInteger(mx - mn) ? x.toFixed(2) : Math.round(x);
  return `${d}${u}`;
};

/* ── les surfaces ─────────────────────────────────────────────────────────── */
const well = (x = {}) => ({ background: "linear-gradient(180deg,#0E1420,#0B0F18)",
  boxShadow: "inset 0 1px 0 rgba(255,255,255,.14), inset 0 3px 10px rgba(0,0,0,.62), inset 0 0 0 1px rgba(255,255,255,.04)",
  borderRadius: 12, ...x });
const ghost = (x = {}) => ({ background: `linear-gradient(180deg,${RAIL},#252839)`,
  border: `1px solid ${LINE2}`, boxShadow: "inset 0 1px 0 rgba(255,255,255,.13), 0 2px 5px rgba(0,0,0,.35)",
  borderRadius: 10, ...x });

const CSS = `
/* LA CAUSE DU DEBORDEMENT. Une carte est un <div> (content-box) ; un logement
   est un <button>, que le navigateur met en border-box. Le padding 10x2 et la
   bordure 1x2 de la carte s'ajoutaient donc a sa hauteur : +22 px dans les deux
   sens, et elle debordait du logement qu'elle remplacait. */
.fxwin, .fxwin *{box-sizing:border-box}
@keyframes flopPulse{0%,100%{opacity:0}45%{opacity:.5}}
@keyframes flopWrite{0%{opacity:0}12%{opacity:.75}34%{opacity:0}}
@keyframes fxCue{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:translateY(0)}}
.fxscroll::-webkit-scrollbar{width:5px;height:5px}
.fxscroll::-webkit-scrollbar-thumb{background:rgba(255,255,255,.13);border-radius:3px}
.fxscroll::-webkit-scrollbar-track{background:transparent}
.fxscroll{scrollbar-width:thin;scrollbar-color:rgba(255,255,255,.13) transparent}
`;

/* Les dégradés partagés par tous les potards : définis une fois. */
const KnobDefs = () => (
  <svg width="0" height="0" style={{ position: "absolute" }} aria-hidden="true">
    <defs>
      <radialGradient id="kBody" cx=".36" cy=".26" r=".88">
        <stop offset="0" stopColor="#767D92" />
        <stop offset=".32" stopColor="#525869" />
        <stop offset=".70" stopColor="#373C4E" />
        <stop offset="1" stopColor="#212538" />
      </radialGradient>
      <radialGradient id="kDome" cx=".40" cy=".22" r=".82">
        <stop offset="0" stopColor="#959CB2" />
        <stop offset=".45" stopColor="#565C70" />
        <stop offset="1" stopColor="#282C3E" />
      </radialGradient>
      <linearGradient id="kGlass" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="#fff" stopOpacity=".30" />
        <stop offset=".5" stopColor="#fff" stopOpacity=".05" />
        <stop offset="1" stopColor="#fff" stopOpacity="0" />
      </linearGradient>
      <linearGradient id="kSkirt" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="#3E4356" />
        <stop offset=".55" stopColor="#2A2E40" />
        <stop offset="1" stopColor="#1A1E2E" />
      </linearGradient>
    </defs>
  </svg>
);

/* ══ LE POTARD ═════════════════════════════════════════════════════════════
   Dessin inédit. Un potard usiné, vu de trois quarts :
     piste + valeur en arc · collerette cannelée · corps bombé · dôme usiné ·
     anneau gravé · index lumineux · reflet de vitre · ombre basse.
   La lumière vient d'en haut à gauche, comme le rail des pistes.
   ─────────────────────────────────────────────────────────────────────────── */
const gaugePt = (deg, r) => {
  const a = deg * Math.PI / 180;
  return [50 + r * Math.cos(a), 50 + r * Math.sin(a)];
};
const gaugeArc = (from, to, r) => {
  const [x1, y1] = gaugePt(from, r), [x2, y2] = gaugePt(to, r);
  const large = to - from > 180 ? 1 : 0;
  return `M${x1.toFixed(2)} ${y1.toFixed(2)} A${r} ${r} 0 ${large} 1 ${x2.toFixed(2)} ${y2.toFixed(2)}`;
};

function Knob({ label, value, onChange, display, accent = IN1, size = 46, onReset, onChangeStart }) {
  const drag = useRef(null);
  const [hot, setHot] = useState(false);
  const v = clamp(value, 0, 1);
  const A0 = 135, SWEEP = 270;
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2, width: size + 16 }}>
      <div onPointerDown={(e) => { drag.current = { y: e.clientY, v };
                                    e.currentTarget.setPointerCapture(e.pointerId);
                                    setHot(true); onChangeStart?.(); }}
           onPointerMove={(e) => { if (!drag.current) return;
             onChange(clamp(drag.current.v + (drag.current.y - e.clientY) / 160, 0, 1)); }}
           onPointerUp={() => { drag.current = null; setHot(false); }}
           onDoubleClick={() => onReset && onReset()}
           style={{ width: size, height: size, position: "relative", cursor: "ns-resize", touchAction: "none" }}>
        <svg viewBox="0 0 100 100" width={size} height={size} style={{ display: "block", overflow: "visible" }}>
          <path d={gaugeArc(A0, A0 + SWEEP, 45)} fill="none" stroke="rgba(255,255,255,.08)"
                strokeWidth="2.6" strokeLinecap="round" />
          {v > .012 && (
            <path d={gaugeArc(A0, A0 + v * SWEEP, 45)} fill="none" stroke={accent}
                  strokeWidth="2.6" strokeLinecap="round"
                  style={{ filter: `drop-shadow(0 0 2.5px ${hx(accent, .85)})` }} />
          )}
          <ellipse cx="50" cy="94" rx="24" ry="4.5" fill="rgba(0,0,0,.5)" style={{ filter: "blur(2.4px)" }} />
          <circle cx="50" cy="50" r="36" fill="url(#kSkirt)" />
          <circle cx="50" cy="50" r="36" fill="none" stroke="#000" strokeOpacity=".8" strokeWidth="7"
                  strokeDasharray="1.3 2.1" />
          <circle cx="50" cy="50" r="36" fill="none" stroke="rgba(255,255,255,.12)" strokeWidth=".7" />
          <circle cx="50" cy="50" r="30.5" fill="url(#kBody)" stroke="rgba(255,255,255,.22)" strokeWidth=".9" />
          <circle cx="50" cy="50" r="30.5" fill="none" stroke="rgba(0,0,0,.45)" strokeWidth="1.6"
                  strokeDasharray="30 200" strokeDashoffset="-30" />
          <circle cx="50" cy="50" r="21" fill="url(#kDome)" stroke="rgba(0,0,0,.42)" strokeWidth=".7" />
          <circle cx="50" cy="50" r="15.6" fill="none" stroke="rgba(255,255,255,.14)" strokeWidth=".8" />
          <circle cx="50" cy="50" r="15.6" fill="none" stroke="rgba(0,0,0,.3)" strokeWidth=".5"
                  strokeDasharray="14 200" strokeDashoffset="-14" />
          <g transform={`rotate(${225 + v * SWEEP} 50 50)`}>
            <rect x="48.8" y="14.5" width="2.4" height="12.5" rx="1.2" fill={accent}
                  style={{ filter: `drop-shadow(0 0 3px ${hx(accent, .9)})` }} />
            <rect x="48.8" y="14.5" width="2.4" height="4.5" rx="1.2" fill="#fff" opacity=".75" />
          </g>
          <ellipse cx="40.5" cy="35" rx="13.5" ry="7.5" transform="rotate(-33 40.5 35)"
                   fill="url(#kGlass)" opacity=".7" />
          {hot && <circle cx="50" cy="50" r="45.5" fill="none" stroke={hx(accent, .5)} strokeWidth="1.5" />}
        </svg>
      </div>
      <span style={{ fontSize: 7.5, fontWeight: 800, letterSpacing: ".1em", textTransform: "uppercase",
                     color: hot ? INK : DIM, whiteSpace: "nowrap", maxWidth: size + 16,
                     overflow: "hidden", textOverflow: "ellipsis" }}>{label}</span>
      <span style={{ fontSize: 9, fontFamily: MONO, color: hot ? INK : "#7E879C" }}>{display}</span>
    </div>
  );
}

/* ══ LE FADER ══════════════════════════════════════════════════════════════
   Rail : la matière de celui des pistes (`FaderRail.kt`), aminci — sa largeur
   seulement. Bonnet : dessin inédit, en six plans — chanfrein haut, cannelures
   verticales, inlay central, LED dans l'accent, chanfrein bas, ombre portée.
   ─────────────────────────────────────────────────────────────────────────── */
const RAIL_H = 11, CAP_D = 22;

function HSlider({ label, value, onChange, display, accent = IN1, onReset, onChangeStart }) {
  const box = useRef(null);
  const drag = useRef(null);
  const [hot, setHot] = useState(false);
  const v = clamp(value, 0, 1);
  const setFrom = (clientX) => {
    const r = box.current.getBoundingClientRect();
    onChange(clamp((clientX - r.left - CAP_D / 2) / Math.max(1, r.width - CAP_D), 0, 1));
  };
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, width: "100%" }}>
      <span style={{ width: 52, flex: "none", fontSize: 7.5, fontWeight: 800, letterSpacing: ".08em",
                     textTransform: "uppercase", color: hot ? INK : DIM, whiteSpace: "nowrap",
                     overflow: "hidden", textOverflow: "ellipsis" }}>{label}</span>
      <div ref={box} onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId);
                                              setHot(true); onChangeStart?.(); setFrom(e.clientX); }}
           onPointerMove={(e) => { if (hot) setFrom(e.clientX); }}
           onPointerUp={() => setHot(false)}
           onDoubleClick={() => onReset && onReset()}
           style={{ flex: 1, minWidth: 0, height: CAP_D + 4, position: "relative",
                    cursor: "ew-resize", touchAction: "none" }}>
        <div style={{ position: "absolute", left: 0, right: 0, top: "50%", height: RAIL_H,
                      marginTop: -RAIL_H / 2, borderRadius: RAIL_H / 2,
                      background: `linear-gradient(180deg,${RAIL},#313443 10%,#373A4B 38%,#3A3C4F 60%,#3E4155 85%,${RAIL2})`,
                      boxShadow: `inset 0 1px 0 rgba(255,255,255,.16), inset 1px 0 0 rgba(255,255,255,.07),
                                  inset -1px 0 0 rgba(0,0,0,.16), inset 0 -1px 0 rgba(255,255,255,.1),
                                  0 3px 8px rgba(0,0,0,.45)` }} />
        <div style={{ position: "absolute", left: 3, right: 3, top: "50%", height: RAIL_H - 6,
                      marginTop: -(RAIL_H - 6) / 2, borderRadius: (RAIL_H - 6) / 2,
                      background: "linear-gradient(180deg,#07090F,#0C0F18)",
                      boxShadow: "inset 0 2px 5px rgba(0,0,0,.85)" }} />
        <div style={{ position: "absolute", left: 3, top: "50%", height: RAIL_H - 6,
                      marginTop: -(RAIL_H - 6) / 2, width: `calc(${v * 100}% - 6px)`,
                      borderRadius: (RAIL_H - 6) / 2,
                      background: `linear-gradient(90deg,${hx(accent, .25)},${hx(accent, .9)})`,
                      boxShadow: `0 0 8px ${hx(accent, .4)}` }} />
        {/* LE BONNET : un cercle bombe. Huit plans concentriques, lumiere en
            haut a gauche comme le rail et les potards. Aucune rayure. */}
        <div style={{ position: "absolute", top: 2, left: `calc(${v * 100}% - ${CAP_D / 2}px)`,
                      width: CAP_D, height: CAP_D, borderRadius: "50%",
                      background: "linear-gradient(160deg, #7C8397 0%, #5A6072 34%, #41465A 68%, #2E3344 100%)",
                      boxShadow: hot
                        ? `0 0 0 1.5px ${hx(accent, .65)}, 0 7px 15px rgba(0,0,0,.68)`
                        : "0 6px 13px rgba(0,0,0,.62), inset 0 1px 0 rgba(255,255,255,.28), inset 0 -1.5px 0 rgba(0,0,0,.45)" }}>
          <div style={{ position: "absolute", inset: "7%", borderRadius: "50%",
                        background: "conic-gradient(from 210deg, rgba(255,255,255,.14), rgba(255,255,255,0) 30%, rgba(0,0,0,.2) 64%, rgba(255,255,255,.1))" }} />
          <div style={{ position: "absolute", inset: "13%", borderRadius: "50%",
                        background: "radial-gradient(circle at 36% 26%, #6C7387, #4B5164 44%, #333849 80%, #262B3B)" }} />
          <div style={{ position: "absolute", inset: "28%", borderRadius: "50%",
                        background: `radial-gradient(circle at 42% 30%, ${hx(accent, .55)}, ${hx(accent, .18)} 62%, rgba(0,0,0,.3))`,
                        boxShadow: `inset 0 1px 2px rgba(0,0,0,.5), 0 0 9px ${hx(accent, .45)}` }} />
          <div style={{ position: "absolute", inset: "38%", borderRadius: "50%",
                        background: `radial-gradient(circle at 44% 32%, #fff, ${accent} 58%, ${hx(accent, .5)})`,
                        boxShadow: `0 0 7px ${hx(accent, .8)}` }} />
          <div style={{ position: "absolute", inset: "6%", borderRadius: "50%", pointerEvents: "none",
                        background: "linear-gradient(200deg, rgba(255,255,255,.2) 0%, rgba(255,255,255,.04) 30%, rgba(255,255,255,0) 50%)" }} />
        </div>
      </div>
      <span style={{ width: 44, flex: "none", textAlign: "right", fontSize: 9, fontFamily: MONO,
                     color: hot ? INK : "#7E879C" }}>{display}</span>
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
            {Ico && ic && <Ico d={ic} size={14} />}{on && l}
          </button>
        );
      })}
    </div>
  );
}

/* ══ L'ORBE DE SPACE ═══════════════════════════════════════════════════════
   Porte de `orb()` de `fx-window-preview.html`. Chaque preset porte sa PROPRE
   PALETTE : c'est elle qui donne sa couleur a l'orbe, pas un accent unique.
   Les palettes `multi` melangent DEUX teintes — c'est le cocktail : Galaxy et
   Nebula melangent violet et cyan, Black Hole melange orange et violet, Aurora
   vert et violet, Shimmer magenta et cyan, Bloom rose et violet.
   Tout est lisse : changer de preset fait GLISSER la teinte, la taille et les
   sept reglages vers leur nouvelle valeur au lieu de sauter.
   ─────────────────────────────────────────────────────────────────────────── */
const LERP_KEYS = ["size", "decay", "pre", "damp", "width", "mix", "diff"];
const easeTo = (cur, target, dt, speed) => cur + (target - cur) * Math.min(1, dt * speed);

function Orb({ vals, palette, accent, fireRef }) {
  const cvs = useRef(null);
  const st = useRef({ t: 0, last: 0, flash: 0, motes: null, sm: null, pal: null });
  const vref = useRef(vals); vref.current = vals;
  const pref = useRef(palette); pref.current = palette;

  useEffect(() => {
    const c = cvs.current, ctx = c.getContext("2d");
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = c.getBoundingClientRect();
    c.width = Math.max(1, rect.width * dpr); c.height = Math.max(1, rect.height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const W = rect.width, H = rect.height;
    const S = st.current;
    if (!S.motes) S.motes = Array.from({ length: 70 }, () => ({ x: Math.random(), y: Math.random(), p: Math.random() * 6.28 }));
    if (!S.sm) { S.sm = {}; for (const k of LERP_KEYS) S.sm[k] = vref.current[k] ?? .5; }
    const base = pref.current || { h: 268, dh: 75, s: 48, l: 74, core: "#eee8ff", rim: "#1d1a38" };
    if (!S.pal) S.pal = { h: base.h, dh: base.dh ?? 0, s: base.s ?? 48, l: base.l ?? 74, h2: base.h2 ?? base.h,
                          multi: base.multi ? 1 : 0 };
    fireRef.current = () => { S.flash = 1; };

    /* Melanger deux teintes en DEGRES fait traverser le cercle chromatique a
       l'envers : 30 (orange) et 268 (violet) donnent 149, un vert. On melange
       donc en RVB — c'est ce qui donne un vrai cocktail et non un vert. */
    const toRgb = (h, sat, lig) => {
      const a = sat / 100 * Math.min(lig / 100, 1 - lig / 100);
      const f = (n) => {
        const k = (n + h / 30) % 12;
        return Math.round(255 * (lig / 100 - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))));
      };
      return [f(0), f(8), f(4)];
    };
    const mix = (c1, c2, u, alpha) => {
      const r = Math.round(c1[0] + (c2[0] - c1[0]) * u);
      const g = Math.round(c1[1] + (c2[1] - c1[1]) * u);
      const b = Math.round(c1[2] + (c2[2] - c1[2]) * u);
      return `rgba(${r},${g},${b},${alpha})`;
    };
    const colAt = (u, alpha) => {
      const p = S.pal;
      const A = toRgb(p.h, p.s, p.l), B = toRgb(p.h2, p.s, p.l);
      return mix(A, B, p.multi * u, alpha);
    };

    let raf;
    const draw = (ts) => {
      const dt = Math.min(.05, (ts - S.last) / 1000 || .016);
      S.last = ts; S.t += dt;
      const t = S.t;

      /* tout glisse vers sa cible : c'est ce qui rend le changement fluide */
      const pv = vref.current, sm = S.sm;
      for (const k of LERP_KEYS) sm[k] = easeTo(sm[k], pv[k] ?? sm[k], dt, 4.2);
      const pt = pref.current || base, pp = S.pal;
      pp.h = easeTo(pp.h, pt.h, dt, 3.0);
      pp.dh = easeTo(pp.dh, pt.dh ?? 0, dt, 3.0);
      pp.s = easeTo(pp.s, pt.s ?? 48, dt, 3.0);
      pp.l = easeTo(pp.l, pt.l ?? 74, dt, 3.0);
      pp.h2 = easeTo(pp.h2, pt.h2 ?? pt.h, dt, 3.0);
      pp.multi = easeTo(pp.multi, pt.multi ? 1 : 0, dt, 2.6);

      const sz = sm.size, dc = sm.decay, pd = sm.pre, wd = sm.width, mx = sm.mix, df = sm.diff;
      const cx = W / 2, cy = H * .45;
      /* la taille : bornee pour que les anneaux ne sortent JAMAIS de la case */
      const R = Math.min(W * .135, H * .115) * (.78 + sz * .62) * (1 + .03 * Math.sin(t * 2));
      const CORE = pt.core || "#eee8ff", RIM = pt.rim || "#1d1a38";
      ctx.clearRect(0, 0, W, H);

      let g = ctx.createRadialGradient(cx, cy, R * .3, cx, cy, R * 4.2);
      g.addColorStop(0, colAt(.4, .18 + .48 * mx));
      g.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);

      for (const m of S.motes) {
        ctx.globalAlpha = .2 + .5 * Math.abs(Math.sin(t * 1.3 + m.p));
        ctx.fillStyle = "#fff"; ctx.fillRect(m.x * W, m.y * H, 1.2, 1.2);
      }

      for (let w = 0; w < 3; w++) {
        const u = (t * (.12 + sz * .2) + w / 3) % 1;
        if (u < pd * .4) continue;
        ctx.globalAlpha = (1 - u) * .4; ctx.strokeStyle = colAt(.15, 1); ctx.lineWidth = 1.6;
        ctx.beginPath(); ctx.arc(cx, cy, R * (1.1 + u * 4.2), 0, 6.2832); ctx.stroke();
      }

      let rx = R * 1.75, ry = rx * (.3 + wd * .18), tl = -.35 + Math.sin(t * .25) * wd * .55;
      ctx.globalAlpha = .3 + .5 * mx; ctx.strokeStyle = colAt(.3, 1); ctx.lineWidth = 2.2;
      ctx.setLineDash([4 + dc * 36, 9]); ctx.lineDashOffset = -t * 30 * (.3 + wd);
      ctx.beginPath(); ctx.ellipse(cx, cy, rx, ry, tl, 0, 6.2832); ctx.stroke(); ctx.setLineDash([]);

      rx = R * 2.7; ry = rx * .36; tl = .25 + Math.cos(t * .2) * wd * .4;
      const N = 22 + Math.round(sz * 14), L = 3 + Math.round(dc * 6);
      for (let p = 0; p < N; p++) {
        const an = t * (.25 + wd * .9) + p * 6.2832 / N;
        for (let k = 0; k < L; k++) {
          const aa = an - k * .08 * (.4 + dc), wob = Math.sin(aa * 5 + t * 3) * df * R * .45;
          const ca = Math.cos(aa), sa = Math.sin(aa);
          const X = (rx + wob) * ca, Y = (ry + wob * .4) * sa;
          ctx.globalAlpha = (1 - k / L) * .85;
          ctx.fillStyle = k ? colAt(p / N, 1) : "#fff";
          ctx.beginPath();
          ctx.arc(cx + X * Math.cos(tl) - Y * Math.sin(tl),
                  cy + X * Math.sin(tl) + Y * Math.cos(tl), 2.1 * (1 - k / L * .5), 0, 6.2832);
          ctx.fill();
        }
      }

      /* borne : les anneaux restent dans la case */
      rx = Math.min(R * 3.7, W * .30); ry = rx * .30; tl = -.6 + Math.sin(t * .17) * wd * .3;
      for (let e = 0; e < 3; e++) {
        ctx.globalAlpha = .05 + .25 * dc * (1 - e / 3); ctx.strokeStyle = colAt(.6, 1); ctx.lineWidth = 1;
        ctx.beginPath(); ctx.ellipse(cx, cy, rx * (1 + e * .06), ry * (1 + e * .06), tl, 0, 6.2832); ctx.stroke();
      }
      for (let q = 0; q < 3; q++) {
        const an = -t * (.18 + wd * .6) + q * 2.094, T2 = 8 + Math.round(dc * 20);
        for (let k = 0; k < T2; k++) {
          const aa = an + k * .05, ca = Math.cos(aa), sa = Math.sin(aa);
          ctx.globalAlpha = (1 - k / T2) * (.3 + .6 * mx);
          ctx.fillStyle = k ? colAt(q / 3, 1) : "#fff";
          ctx.beginPath();
          ctx.arc(cx + rx * ca * Math.cos(tl) - ry * sa * Math.sin(tl),
                  cy + rx * ca * Math.sin(tl) + ry * sa * Math.cos(tl), 3 * (1 - k / T2 * .6), 0, 6.2832);
          ctx.fill();
        }
      }

      if (S.flash > 0) {
        ctx.globalAlpha = S.flash * .5; ctx.strokeStyle = "#fff"; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.arc(cx, cy, R * (1 + (1 - S.flash) * 1.7), 0, 6.2832); ctx.stroke();
        S.flash = Math.max(0, S.flash - dt * 1.8);
      }

      /* la sphere : le coeur clair, la teinte au milieu, le bord sombre */
      ctx.globalAlpha = 1;
      g = ctx.createRadialGradient(cx - R * .35, cy - R * .4, R * .1, cx, cy, R);
      g.addColorStop(0, CORE);
      g.addColorStop(.38, colAt(.55, 1));
      g.addColorStop(1, RIM);
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, R, 0, 6.2832); ctx.fill();

      ctx.save();
      ctx.beginPath(); ctx.arc(cx, cy, R, 0, 6.2832); ctx.clip();
      ctx.strokeStyle = "#fff"; ctx.lineWidth = .8;
      for (let q = 0; q < 3; q++) {
        ctx.globalAlpha = .1 + .1 * df;
        ctx.beginPath();
        ctx.ellipse(cx, cy, R * Math.abs(Math.cos(t * .4 * (.5 + wd) + q * 1.05)), R, 0, 0, 6.2832);
        ctx.stroke();
      }
      ctx.restore();

      ctx.globalAlpha = .08 + .4 * mx; ctx.strokeStyle = "#fff"; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.arc(cx, cy, R * 1.08, 0, 6.2832); ctx.stroke();
      ctx.globalAlpha = 1;
      raf = requestAnimationFrame(draw);
    };
    raf = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(raf);
  }, []);
  /* Le canvas est en ABSOLU dans sa case : sinon sa largeur intrinseque (300 px
     par defaut) force le flex item a grandir et la carte deborde du logement. */
  return <canvas ref={cvs} style={{ position: "absolute", inset: 0, width: "100%", height: "100%", display: "block" }} />;
}

/* ══ LES PRESETS : une case, qui se deroule ════════════════════════════════
   Avant : vingt chips sur une ligne, il fallait tirer horizontalement pour
   trouver le vingtieme. Maintenant : une case fermee qui montre le preset
   courant, et qui se deroule a la demande. Deux colonnes, defilement vertical.
   ─────────────────────────────────────────────────────────────────────────── */
function PresetRow({ fx, preset, custom, onPick }) {
  const [open, setOpen] = useState(false);
  const names = Object.keys(fx.presets || {});
  const i = Math.max(0, names.indexOf(preset));
  const step = (d) => onPick(names[(i + d + names.length) % names.length]);
  return (
    <div style={{ position: "relative", flex: "none", zIndex: open ? 12 : 1 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
        <span style={{ fontSize: 8, fontWeight: 800, letterSpacing: ".15em", textTransform: "uppercase",
                       color: MUTE, flex: "none", width: 52 }}>Presets</span>
        <button onClick={() => step(-1)} style={{ ...ghost({ width: 26, height: 28, display: "grid",
                 placeItems: "center", color: DIM, cursor: "pointer", borderRadius: 8, flex: "none" }) }}>
          <Ico d={P.back} size={13} /></button>
        <button onClick={() => setOpen((o) => !o)}
                style={{ flex: 1, minWidth: 0, height: 28, borderRadius: 9, cursor: "pointer", padding: "0 11px",
                         display: "flex", alignItems: "center", gap: 9, fontFamily: FONT,
                         background: "linear-gradient(180deg,#0E1420,#0B0F18)",
                         boxShadow: "inset 0 1px 0 rgba(255,255,255,.14), inset 0 3px 10px rgba(0,0,0,.62), inset 0 0 0 1px rgba(255,255,255,.04)",
                         border: `1px solid ${open ? hx(ACC, .4) : "transparent"}`, textAlign: "left" }}>
          <span style={{ width: 5, height: 5, borderRadius: 3, flex: "none",
                         background: `hsl(${(fx.palettes && fx.palettes[preset] && fx.palettes[preset].h) || 268},`
                                   + `${(fx.palettes && fx.palettes[preset] && fx.palettes[preset].s) || 48}%,`
                                   + `${(fx.palettes && fx.palettes[preset] && fx.palettes[preset].l) || 74}%)` }} />
          <span style={{ flex: 1, minWidth: 0, fontSize: 12, fontWeight: 800, color: INK, whiteSpace: "nowrap",
                         overflow: "hidden", textOverflow: "ellipsis" }}>{preset}</span>
          {custom && <span style={{ fontSize: 9, fontWeight: 800, color: "#F2C97D", flex: "none" }}>Custom</span>}
          <span style={{ fontSize: 9.5, fontFamily: MONO, color: MUTE, flex: "none" }}>
            {String(i + 1).padStart(2, "0")}/{names.length}</span>
          <Ico d={P.down} size={13} style={{ color: MUTE, flex: "none",
                transform: `rotate(${open ? 180 : 0}deg)`, transition: "transform .2s" }} />
        </button>
        <button onClick={() => step(1)} style={{ ...ghost({ width: 26, height: 28, display: "grid",
                 placeItems: "center", color: DIM, cursor: "pointer", borderRadius: 8, flex: "none" }) }}>
          <Ico d={P.back} size={13} style={{ transform: "rotate(180deg)" }} /></button>
      </div>

      {open && (
        <>
          <div onClick={() => setOpen(false)} style={{ position: "fixed", inset: 0, zIndex: 11 }} />
          <div className="fxscroll" style={{ position: "absolute", left: 58, right: 32, top: 32, zIndex: 12,
                        maxHeight: 208, overflowY: "auto", display: "grid", gridTemplateColumns: "1fr 1fr",
                        gap: 4, padding: 6, borderRadius: 12,
                        background: "linear-gradient(180deg,#1B2231,#10141D)",
                        border: `1px solid ${hx(ACC, .3)}`,
                        boxShadow: "0 18px 40px rgba(0,0,0,.7), inset 0 1px 0 rgba(255,255,255,.08)",
                        animation: "fxCue .16s ease-out" }}>
            {names.map((n, k) => {
              const on = n === preset;
              return (
                <button key={n} onClick={() => { onPick(n); setOpen(false); }}
                        style={{ height: 30, borderRadius: 8, cursor: "pointer", padding: "0 10px",
                                 display: "flex", alignItems: "center", gap: 8, fontFamily: FONT,
                                 fontSize: 11.5, fontWeight: on ? 800 : 600, textAlign: "left",
                                 background: on ? `linear-gradient(90deg,${hx(ACC, .22)},rgba(255,255,255,.02))`
                                                : "rgba(255,255,255,.03)",
                                 border: `1px solid ${on ? hx(ACC, .4) : "transparent"}`,
                                 color: on ? INK : "#96A0B4" }}>
                  <span style={{ width: 4, height: 4, borderRadius: 2, flex: "none",
                                 background: on ? ACC : "#39415A" }} />
                  <span style={{ flex: 1, minWidth: 0, whiteSpace: "nowrap", overflow: "hidden",
                                 textOverflow: "ellipsis" }}>{n}</span>
                  <span style={{ fontSize: 8.5, fontFamily: MONO, color: MUTE }}>
                    {String(k + 1).padStart(2, "0")}</span>
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

/* ══ LA CARTE ══════════════════════════════════════════════════════════════
   Elle occupe EXACTEMENT la place d'un logement : même hauteur, même largeur.
   ─────────────────────────────────────────────────────────────────────────── */
function FxCard({ fx, midi, on, onToggle, onOpen, onRemove, height }) {
  const [hot, setHot] = useState(false);
  const fireRef = useRef(() => {});
  const vals = Object.fromEntries((fx.params || []).map(([, k, v]) => [k, v]));
  const first = fx.presets ? Object.keys(fx.presets)[0] : null;
  const pal = fx.palettes ? fx.palettes[first] : undefined;
  const ac = fx.accent || ACC;
  return (
    <div onClick={onOpen} onPointerEnter={() => setHot(true)} onPointerLeave={() => setHot(false)}
         style={{ flex: "0 0 calc((100% - 30px) / 4)", minWidth: 0, height, borderRadius: 16, padding: 10,
                  overflow: "hidden", cursor: "pointer",
                  display: "flex", flexDirection: "column", gap: 7,
                  transform: hot ? "translateY(-3px)" : "none",
                  transition: "transform .18s cubic-bezier(.2,.9,.3,1)",
                  background: `linear-gradient(180deg,${hot ? "#373E4C" : "#323845"},${hot ? "#252A37" : "#222733"})`,
                  border: `1px solid ${LINE2}`,
                  boxShadow: "0 8px 16px rgba(0,0,0,.5), inset 0 1px 0 rgba(255,255,255,.11)" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
        <span style={{ fontSize: 8, fontWeight: 800, letterSpacing: ".16em", textTransform: "uppercase",
                       color: on ? ac : DIM, flex: 1, whiteSpace: "nowrap", overflow: "hidden",
                       textOverflow: "ellipsis" }}>{fx.type}</span>
        <button onClick={(e) => { e.stopPropagation(); onToggle(); }} title="Engage / Bypass"
                style={{ width: 22, height: 22, borderRadius: 7, cursor: "pointer", padding: 0,
                         display: "grid", placeItems: "center", flex: "none",
                         background: on ? `linear-gradient(180deg,${IN1},${IN2})`
                                        : "linear-gradient(180deg,#343B4C,#232837)",
                         border: `1px solid ${on ? "transparent" : "rgba(255,255,255,.14)"}`,
                         boxShadow: on ? "0 0 8px rgba(199,217,249,.35)"
                                       : "inset 0 1px 0 rgba(255,255,255,.12), 0 2px 4px rgba(0,0,0,.4)",
                         color: on ? IN_TEXT : "#8E97AC" }}>
          <Ico d={P.power} size={12} sw={2.1} />
        </button>
        <button onClick={(e) => { e.stopPropagation(); onRemove(); }} title="Remove"
                style={{ width: 22, height: 22, borderRadius: 7, cursor: "pointer", padding: 0,
                         display: "grid", placeItems: "center", flex: "none",
                         background: "linear-gradient(180deg,#3A4152,#272C3B)",
                         border: "1px solid rgba(255,255,255,.16)",
                         boxShadow: "inset 0 1px 0 rgba(255,255,255,.14), 0 2px 4px rgba(0,0,0,.4)",
                         color: "#AEB7C8", opacity: hot ? 1 : 0, transition: "opacity .15s, background .15s" }}
                onPointerEnter={(e) => { e.currentTarget.style.background = "linear-gradient(180deg,#5A2A38,#3A1B25)";
                                          e.currentTarget.style.color = "#FF8FA3"; }}
                onPointerLeave={(e) => { e.currentTarget.style.background = "linear-gradient(180deg,#3A4152,#272C3B)";
                                          e.currentTarget.style.color = "#AEB7C8"; }}>
          <Ico d={P.trash} size={12} sw={1.9} />
        </button>
      </div>
      <div style={{ fontSize: 18, lineHeight: 1.05, fontWeight: 700, color: on ? INK : "#98A1B4",
                    whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{fx.name}</div>
      <div style={{ flex: 1, minHeight: 0, minWidth: 0, borderRadius: 11, overflow: "hidden",
                    position: "relative",
                    background: "#12161F", boxShadow: "inset 0 2px 7px rgba(0,0,0,.7), inset 0 0 0 1px rgba(255,255,255,.04)" }}>
        {fx.space
          ? <Orb vals={vals} palette={pal} accent={ac} fireRef={fireRef} />
          : midi ? <MiniSteps accent={ac} on={on} /> : <MiniGraph type={fx.type} accent={ac} on={on} />}
        {!on && <div style={{ position: "absolute", inset: 0, background: "rgba(10,13,20,.6)" }} />}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 9, fontWeight: 700,
                    color: DIM, flex: "none" }}>
        <span style={{ width: 8, height: 8, borderRadius: 4, flex: "none",
                       background: on ? "#C9D2E6" : "#2A3040",
                       boxShadow: on ? "0 0 7px rgba(201,210,230,.6)" : "inset 0 1px 2px rgba(0,0,0,.8)" }} />
        <span style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
          {midi ? "MIDI effect" : (first || fx.type)}
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

function EmptySlot({ onAdd, height, label }) {
  return (
    <button onClick={onAdd} style={{ flex: "0 0 calc((100% - 30px) / 4)", minWidth: 0, height, borderRadius: 16, cursor: "pointer",
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
      <div className="fxscroll" style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "grid",
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
   Presets à gauche (défilants) · orbe au centre · réglages à droite.
   Les quatre premiers réglages sont des potards EN HAUT, les suivants des
   faders EN BAS, sans partage de la case.
   ─────────────────────────────────────────────────────────────────────────── */
function FxPanel({ fx, vals, setVal, preset, custom, onPick, on, onToggle }) {
  const fireRef = useRef(() => {});
  const get = (k) => vals[k] ?? .5;
  const knobs = fx.params.slice(0, 4);
  const sliders = fx.params.slice(4);
  const names = fx.presets ? Object.keys(fx.presets) : [];
  const pal = fx.palettes ? (fx.palettes[preset] || fx.palettes[names[0]]) : undefined;
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10, height: "100%", minHeight: 0 }}>
      <PresetRow fx={fx} preset={preset} custom={custom} onPick={onPick} />

      <div style={{ flex: 1, minHeight: 0, display: "flex", gap: 12 }}>
        <div onClick={() => fireRef.current()}
             style={{ flex: "1 1 0", width: 0, minWidth: 0, position: "relative", borderRadius: 15, overflow: "hidden",
                      cursor: fx.space ? "crosshair" : "default", ...well({ borderRadius: 15 }) }}>
          {fx.space
            ? <Orb vals={vals} palette={pal} accent={fx.accent} fireRef={fireRef} />
            : <div style={{ height: "100%", display: "grid", placeItems: "center" }}>
                <div style={{ width: "58%", height: "54%" }}>
                  <MiniGraph type={fx.type} accent={fx.accent} on />
                </div>
              </div>}
          <div style={{ position: "absolute", left: 11, top: 9, display: "flex", alignItems: "center", gap: 7,
                        padding: "4px 9px", borderRadius: 8, background: "rgba(10,14,26,.55)",
                        border: `1px solid ${LINE2}`, backdropFilter: "blur(6px)", pointerEvents: "none" }}>
            <span style={{ width: 5, height: 5, borderRadius: 3,
                           background: `hsl(${(pal && pal.h) || 268},${(pal && pal.s) || 48}%,${(pal && pal.l) || 74}%)`,
                           boxShadow: `0 0 7px hsl(${(pal && pal.h) || 268},${(pal && pal.s) || 48}%,${(pal && pal.l) || 74}%)` }} />
            <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: ".09em", textTransform: "uppercase",
                           color: "#C6CEE8" }}>{custom ? "Custom" : preset}</span>
            {fx.space && <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>
              {(.4 + get("decay") * 7.6).toFixed(1)} s</span>}
          </div>
          {fx.space && (
            <div style={{ position: "absolute", left: 11, bottom: 8, fontSize: 8.5, fontWeight: 800,
                          letterSpacing: ".1em", textTransform: "uppercase", color: MUTE, pointerEvents: "none" }}>
              Touch the orb to re-fire
            </div>
          )}
          {!on && <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center",
                                background: "rgba(9,12,22,.66)", fontSize: 12, fontWeight: 800,
                                letterSpacing: ".2em", color: MUTE, textTransform: "uppercase" }}>Bypassed</div>}
        </div>

        <div style={{ width: 268, flex: "none", display: "flex", flexDirection: "column", gap: 8, minHeight: 0 }}>
          <div style={{ height: 94, flex: "none", display: "flex", alignItems: "center",
                        justifyContent: "space-around", ...well({ borderRadius: 13 }) }}>
            {knobs.map(([n, k, def, mn, mx, u]) => (
              <Knob key={k} label={n} value={get(k)} accent={fx.accent} size={44}
                    onChange={(v) => setVal(k, v)} onReset={() => setVal(k, def)}
                    display={fmt(get(k), mn, mx, u)} />
            ))}
          </div>
          <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column",
                        justifyContent: "space-evenly", padding: "8px 12px", ...well({ borderRadius: 13 }) }}>
            {sliders.map(([n, k, def, mn, mx, u]) => (
              <HSlider key={k} label={n} value={get(k)} accent={fx.accent}
                       onChange={(v) => setVal(k, v)} onReset={() => setVal(k, def)}
                       display={fmt(get(k), mn, mx, u)} />
            ))}
          </div>
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
  const [vals, setVals] = useState(() => Object.fromEntries(AUDIO[0].params.map(([, k, v]) => [k, v])));
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

  const setVal = (k, v) => {
    setVals((s) => ({ ...s, [k]: v }));
    if (!custom) setCustom(true);      // on garde le preset choisi : sa palette reste
  };
  const applyPreset = (n) => {
    const row = AUDIO[0].presets[n];
    if (row) setVals(Object.fromEntries(AUDIO[0].params.map(([, k], i) => [k, row[i]])));
    setPreset(n); setCustom(false);
  };

  useEffect(() => {
    const id = setInterval(() => setT((x) => x + 1), 110);
    return () => clearInterval(id);
  }, []);
  const cur = Math.floor(t / 2) % arp.steps;

  const inEffect = view.k === "effect";
  const isArp = inEffect && view.fx.type === "arp";
  const isSpace = inEffect && !!view.fx.space;
  const title = inEffect ? view.fx.name : view.k === "catalog" ? "Add effect" : "Effects";

  return (
    <div data-shot="fxwin" className="fxwin" style={{ position: "relative", width: 891, height: 411, borderRadius: 20, overflow: "hidden",
                  fontFamily: FONT, color: INK, background: `linear-gradient(180deg,${WIN_T},${WIN_B})`,
                  border: `1px solid ${LINE2}`, boxShadow: "0 24px 50px rgba(0,0,0,.7), inset 0 1px 0 rgba(255,255,255,.08)",
                  userSelect: "none", WebkitFontSmoothing: "antialiased", display: "flex", flexDirection: "column" }}>
      <style>{CSS}</style>
      <KnobDefs />
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
        <div style={{ flex: 1 }} />
        {view.k === "rack" && (
          <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: ".12em", textTransform: "uppercase", color: MUTE }}>
            {audio.length + (midi.fx ? 1 : 0)} / {MAX_AUDIO + MAX_MIDI}
          </span>
        )}
        {isSpace && (
          <button title="Save preset" style={{ ...ghost({ width: 28, height: 28, display: "grid", placeItems: "center",
                   cursor: "pointer", opacity: custom ? 1 : .5 }) }}>
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
                          onRemove={() => setAudio((s) => s.filter((_, k) => k !== i))} />
                : <EmptySlot key={"e" + i} height={BODY_H} label="Audio"
                             onAdd={() => setView({ k: "catalog", kind: "audio" })} />;
            })}
            {midi.fx
              ? <FxCard fx={midi.fx} midi height={BODY_H} on={midi.on}
                        onToggle={() => setMidi((m) => ({ ...m, on: !m.on }))}
                        onOpen={() => setView({ k: "effect", fx: midi.fx })}
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
            : <FxPanel fx={view.fx} vals={vals} setVal={setVal} preset={preset} custom={custom}
                       onPick={applyPreset}
                       on={isSpace ? audio[0].on : midi.on}
                       onToggle={() => (isSpace ? setAudio((s) => s.map((x, k) => k === 0 ? { ...x, on: !x.on } : x))
                                                : setMidi((m) => ({ ...m, on: !m.on })))} />
        )}
      </div>
    </div>
  );
}

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
      <div style={{ fontSize: 11.5, color: "#5B6580", textAlign: "center", maxWidth: 840, lineHeight: 1.65 }}>
        Æther FX — 891 × 411. L'orbe est celui de l'app ; chaque preset porte sa palette, et tout glisse
        au changement. Presets en haut, potards et sliders à droite, orbe au centre.
      </div>
    </div>
  );
}
