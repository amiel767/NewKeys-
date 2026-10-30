import { useEffect, useMemo, useRef, useState } from "react";

/* ═════════════════════════════════════════════════════════════════════════════
   ÆTHER FX — nouvelle fenêtre d'effets.

   LES SLOTS (v3)
   Des logements VERTICAUX avec un petit aperçu de l'effet, pas des creux
   horizontaux. Un logement vide n'affiche rien d'autre qu'un « + » à
   l'intérieur : pas de tuile Add en bas, c'est le logement lui-même qui appelle.
   Le slot MIDI est détaché du slot audio, à droite, séparé par un filet.

   L'ORBE VIVANTE (Æther Space)
   Une sphère semi-3D : des points répartis sur la sphère, projetés en
   perspective, tournent lentement ; un halo radial au centre donne la matière ;
   une onde parcourt la surface à chaque émission. Tous les réglages la pilotent :
     Size  → rayon de la sphère          Decay → vitesse d'extinction de l'onde
     Pre   → retard de l'onde            Damp  → chute d'amplitude avec la latitude
     Width → aplatissement horizontal    Mix   → énergie de l'onde
     Diffuse → dispersion des points (le flou de la surface)

   L'ARPPEGIATEUR
   Adapté du JSX DeepSeek (app/assets/ArpChordPreview.jsx) à la fenêtre, et
   aligné sur les VRAIS paramètres du registre : ArpParams(sub, style, steps,
   vel, on) avec ArpStyle(Up, Down, Up-Down, Down-Up, Random, Chord) et
   Subdivisions 1/4, 1/8, 1/16, 1/32. Rien d'inventé.

   LA LISTE MIDI
   Les 5 vrais effets (FxModes.kt → MidiType) : Arpeggiator, Octave Up,
   Easy Chord, Sequencer, Chord Sequencer, avec leur vrai descripteur.

   MISE À L'ÉCHELLE
   La fenêtre se dessine à une largeur de référence puis s'ajuste : la maquette
   montre un écran standard (pas un onglet), et la fenêtre prend proprement la
   taille du panneau déclaré par l'effet.
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
const AMBER = "#D8A24A";

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

/* ── les effets audio réels (FxRegistry.kt, FxKind.EFFECT) ─────────────────── */
const AUDIO_FX = [
  { id: 0,  name: "Æther Space",   type: "Reverb",     cat: "Time",     pw: 7, w: 857, h: 437, note: "Size, Decay, Pre-delay, Damp, Width, Mix, Diffuse", orb: true },
  { id: 1,  name: "Æther Echo",    type: "Delay",      cat: "Time",     pw: 4, w: 760, h: 400, note: "Feedback, Tone, Wow, Mix" },
  { id: 2,  name: "Prism Tone",    type: "Equalizer",  cat: "Tone",     pw: 4, w: 800, h: 408, note: "Low, Mid, High, Mid Q" },
  { id: 3,  name: "Iron Grip",     type: "Compressor", cat: "Dynamics", pw: 5, w: 820, h: 412, note: "Ratio, Knee, Attack, Release, Makeup" },
  { id: 4,  name: "Solar Ceiling", type: "Maximizer",  cat: "Dynamics", pw: 3, w: 700, h: 394, note: "Amount, Ceiling, Release" },
  { id: 10, name: "Æther Amp",     type: "Amp",        cat: "Drive",    pw: 6, w: 820, h: 412, note: "Drive, Bass, Mid, Treble, Presence, Level" },
  { id: 11, name: "Æther Bass",    type: "Amp",        cat: "Drive",    pw: 6, w: 820, h: 412, note: "Drive, Body, Mid, Treble, Presence, Level" },
  { id: 13, name: "Æther Voices",  type: "Voice",      cat: "Pitch",    pw: 5, w: 780, h: 406, note: "Voices, Detune, Formant, Width, Mix" },
];
/* ── les 5 effets MIDI réels (FxModes.kt → MidiType) ──────────────────────── */
const MIDI_FX = [
  { id: 101, name: "Arpeggiator",     type: "Arp",     cat: "Pattern", pw: 5, w: 880, h: 452, note: "Subdivision, Style, Steps, Velocity per step, Hold", arp: true },
  { id: 102, name: "Sequencer",       type: "Seq",     cat: "Pattern", pw: 4, w: 880, h: 456, note: "Subdivision, Steps, notes per step" },
  { id: 103, name: "Easy Chord",      type: "Easy",    cat: "Harmony", pw: 1, w: 720, h: 400, note: "One note plays a programmed chord" },
  { id: 104, name: "Chord Sequencer", type: "Chords",  cat: "Harmony", pw: 6, w: 900, h: 460, note: "Key, Scale, progression" },
  { id: 105, name: "Octave Up",       type: "Octave",  cat: "Pitch",   pw: 1, w: 640, h: 372, note: "Adds an octave above every note" },
];

const MAX_AUDIO = 3, MAX_MIDI = 1;

/* ── Æther Space : positions réelles (FxGeometry.kt / FxKnobBoxes[0]) ──────── */
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
const spaceFmt = (k, v) => {
  const r = k === "decay" ? 0.4 + v * 7.6 : k === "damp" ? 1 + v * 11 : k === "pre" ? v * 120 : v * 100;
  if (k === "decay") return r.toFixed(2) + " s";
  if (k === "damp") return r.toFixed(1) + " kHz";
  if (k === "pre") return Math.round(r) + " ms";
  return Math.round(r) + " %";
};

/* ── Arp : vrais domaines (FxModes.kt) ─────────────────────────────────────── */
const SUBS = [["1/4", 1], ["1/8", 2], ["1/16", 4], ["1/32", 8]];
const ARP_STYLES = [["Up", "Up"], ["Down", "Down"], ["UpDown", "Up-Down"], ["DownUp", "Down-Up"], ["Random", "Random"], ["Chord", "Chord"]];

/* ── icônes, style Lucide ──────────────────────────────────────────────────── */
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
  play: ["M8 5.5l10 6.5-10 6.5z"], rand: ["M4 7h4l8 10h4", "M4 17h4l2-2.5", "M14 9.5L16 7h4", "M18 5l2 2-2 2", "M18 15l2 2-2 2"],
  hold: ["M12 3v18", "M5 7h14", "M5 17h14"],
};

const win = (x = {}) => ({ background: `linear-gradient(180deg,${PANEL_T},${PANEL_B})`, ...x });
const well = (x = {}) => ({ background: `linear-gradient(180deg,${WELL_A},${WELL_B})`,
  boxShadow: "inset 0 3px 12px rgba(0,0,0,.6), inset 0 0 0 1px rgba(255,255,255,.04)", ...x });
const btn = (x = {}) => ({ background: `linear-gradient(180deg,${BTN_T},${BTN_B})`, border: `1px solid ${LINE2}`,
  boxShadow: "inset 0 1px 0 rgba(255,255,255,.1), 0 2px 5px rgba(0,0,0,.35)", ...x });

/* ══════════════════════════════════════════════════════════════════════════════
   L'ORBE VIVANTE — sphère semi-3D
   Des points sont répartis sur une sphère (spirale de Fibonacci, donc régulière),
   tournent autour de l'axe vertical, et sont projetés en perspective. Une onde
   parcourt la surface : son rayon croît avec le temps, son amplitude décroît
   selon Decay et la latitude selon Damp. Un halo radial donne la matière, un
   reflet en haut à gauche donne la lumière, et l'ombre en bas donne le volume.
   ────────────────────────────────────────────────────────────────────────────── */
function LivingOrb({ vals, size = 260, onPulse }) {
  const cvs = useRef(null);
  const S = useRef({ t: 0, last: 0, next: 0.6, waves: [], spin: 0, flash: 0 });
  const vref = useRef(vals); vref.current = vals;

  useEffect(() => {
    const c = cvs.current, ctx = c.getContext("2d");
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    c.width = size * dpr; c.height = size * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const N = 460;
    const pts = [];
    const GA = Math.PI * (3 - Math.sqrt(5));
    for (let i = 0; i < N; i++) {
      const y = 1 - (i / (N - 1)) * 2;
      const r = Math.sqrt(Math.max(0, 1 - y * y));
      const th = GA * i;
      pts.push({ x: Math.cos(th) * r, y, z: Math.sin(th) * r, lat: Math.abs(y) });
    }
    if (onPulse) onPulse.current = () => { S.current.waves.push({ t: 0 }); S.current.flash = 1; };

    let raf;
    const draw = (ts) => {
      const dt = Math.min(0.05, (ts - S.current.last) / 1000 || 0.016);
      S.current.last = ts; S.current.t += dt;
      const v = vref.current;
      const st = S.current;

      st.next -= dt;
      if (st.next <= 0) { st.waves.push({ t: 0 }); st.flash = 1; st.next = 1.1 + Math.random() * 0.9; }
      st.flash = Math.max(0, st.flash - dt * 1.8);
      st.spin += dt * 0.16;

      const cx = size / 2, cy = size / 2;
      const R = size * (0.14 + v.size * 0.30);     // Size → rayon
      const flat = 1 - v.width * 0.22;             // Width → aplatissement
      const tau = 0.7 + v.decay * 3.2;             // Decay → extinction
      st.waves = st.waves.filter((w) => { w.t += dt; return w.t < tau; });

      ctx.clearRect(0, 0, size, size);

      /* halo extérieur : la lumière que l'orbe rend */
      const halo = ctx.createRadialGradient(cx, cy, R * 0.6, cx, cy, R * 2.5);
      halo.addColorStop(0, hx(ACC, 0.16 + v.mix * 0.16));
      halo.addColorStop(0.55, hx(ACC, 0.05));
      halo.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = halo; ctx.fillRect(0, 0, size, size);

      /* les points, triés par profondeur pour que l'avant recouvre l'arrière */
      const proj = pts.map((p, i) => {
        const a = st.spin;
        const x = p.x * Math.cos(a) - p.z * Math.sin(a);
        const z = p.x * Math.sin(a) + p.z * Math.cos(a);
        const persp = 1 + z * 0.28;
        return { sx: cx + x * R * flat * persp, sy: cy + p.y * R * persp, z, lat: p.lat, i };
      }).sort((a, b) => a.z - b.z);

      /* l'onde : elle part du pôle et descend ; l'amplitude dépend de Damp */
      let amp = 0;
      for (const w of st.waves) {
        const prog = w.t / tau;
        amp = Math.max(amp, Math.pow(1 - prog, 1.8) * (0.35 + v.mix * 0.9));
      }
      const wavePhase = st.waves.length ? st.waves[st.waves.length - 1].t / tau : 1;

      for (const p of proj) {
        const front = 1 - Math.abs(p.lat - wavePhase * 1.1) / 0.42;
        const wave = front > 0 ? front * amp * (1 - v.damp * 0.7 * p.lat) : 0;
        const depth = (p.z + 1) / 2;                    // 0 derrière, 1 devant
        const scatter = 0.5 + v.diff * 0.5;
        const a = (0.10 + depth * 0.55) * scatter + wave * 0.55;
        const rad = (0.9 + depth * 1.5) * (1 + wave * 0.9);
        ctx.beginPath();
        ctx.arc(p.sx, p.sy, rad, 0, 6.2832);
        ctx.fillStyle = `rgba(${190 + wave * 55},${205 + wave * 40},255,${clamp(a, 0, 0.95)})`;
        ctx.fill();
      }

      /* la source : un point chaud au centre, il pulse à chaque émission */
      const pulse = st.flash;
      const core = ctx.createRadialGradient(cx, cy, 0, cx, cy, R * (0.5 + pulse * 0.5));
      core.addColorStop(0, `rgba(232,238,255,${0.55 + pulse * 0.4})`);
      core.addColorStop(0.4, hx(ACC, 0.22 + pulse * 0.3));
      core.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = core;
      ctx.beginPath(); ctx.arc(cx, cy, R * (0.5 + pulse * 0.5), 0, 6.2832); ctx.fill();

      /* lumière en haut à gauche, ombre en bas : c'est ce qui fait le volume */
      const sh = ctx.createRadialGradient(cx - R * 0.4, cy - R * 0.45, 0, cx, cy, R * 1.25);
      sh.addColorStop(0, "rgba(255,255,255,.13)");
      sh.addColorStop(0.5, "rgba(255,255,255,0)");
      sh.addColorStop(1, "rgba(0,0,0,.34)");
      ctx.fillStyle = sh;
      ctx.beginPath(); ctx.arc(cx, cy, R * 1.15, 0, 6.2832); ctx.fill();

      raf = requestAnimationFrame(draw);
    };
    raf = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(raf);
  }, [size]);

  return <canvas ref={cvs} style={{ width: size, height: size, display: "block" }} />;
}

/* ══ UN SLOT VERTICAL avec aperçu ════════════════════════════════════════════
   Le logement est vertical : l'aperçu occupe le haut, le nom et les commandes
   le bas. Vide, il ne montre qu'un « + » au centre, qui ouvre la liste.
   ─────────────────────────────────────────────────────────────────────────── */
function VerticalSlot({ height = 200, children, filled, onAdd, accent = ACC }) {
  return (
    <div style={{ position: "relative", height, width: "100%", borderRadius: 14, padding: 3,
                  background: "linear-gradient(180deg,rgba(0,0,0,.5),rgba(255,255,255,.035))",
                  boxShadow: "0 1px 0 rgba(255,255,255,.05)" }}>
      <div style={{ position: "absolute", inset: 3, borderRadius: 11,
                    background: "linear-gradient(180deg,#05080E,#0A0E18)",
                    boxShadow: "inset 0 5px 12px rgba(0,0,0,.85), inset 0 -1px 0 rgba(255,255,255,.055), inset 2px 0 5px rgba(0,0,0,.5), inset -2px 0 5px rgba(0,0,0,.5)" }} />
      <div style={{ position: "absolute", left: 6, right: 6, bottom: 3.5, height: 1.4, borderRadius: 1,
                    background: `linear-gradient(90deg,transparent,${filled ? hx(accent, .34) : "rgba(255,255,255,.14)"},transparent)`,
                    filter: "blur(.3px)" }} />
      {!filled ? (
        <button onClick={onAdd} style={{ position: "absolute", inset: 6, borderRadius: 11, cursor: "pointer",
                 display: "grid", placeItems: "center", border: "none", background: "transparent", padding: 0 }}>
          <div style={{ width: 44, height: 44, borderRadius: 14, display: "grid", placeItems: "center",
                        color: MUTE, background: "linear-gradient(180deg,rgba(255,255,255,.06),rgba(255,255,255,.02))",
                        border: `1px dashed ${hx(accent, .34)}` }}>
            <Ico d={IC.plus} size={20} sw={2.4} />
          </div>
        </button>
      ) : (
        <div style={{ position: "absolute", inset: 6, borderRadius: 11, display: "flex",
                      flexDirection: "column", overflow: "hidden" }}>{children}</div>
      )}
    </div>
  );
}

/* ── l'aperçu d'un effet : une vignette dessinée, propre à son type ────────── */
function FxThumb({ fx, vals, big }) {
  const h = big ? 96 : 64;
  return (
    <div style={{ position: "relative", height: h, flex: "none", overflow: "hidden",
                  background: `radial-gradient(ellipse at 50% 30%, ${hx(ACC, .12)}, rgba(6,9,16,.9) 72%)`,
                  borderBottom: `1px solid ${LINE}` }}>
      {fx.orb ? <OrbThumb vals={vals} h={h} />
        : fx.arp ? <ArpThumb h={h} />
        : <BarsThumb fx={fx} h={h} />}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 22,
                    background: "linear-gradient(0deg,rgba(6,9,16,.95),transparent)" }} />
    </div>
  );
}
function OrbThumb({ vals, h }) {
  const c = useRef(null);
  useEffect(() => {
    const cv = c.current, ctx = cv.getContext("2d");
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = cv.clientWidth, H = h;
    cv.width = w * dpr; cv.height = H * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const cx = w / 2, cy = H / 2, R = H * (0.16 + vals.size * 0.26);
    ctx.clearRect(0, 0, w, H);
    const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, R * 2);
    g.addColorStop(0, hx(ACC, .3)); g.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(cx, cy, R * 2, 0, 6.2832); ctx.fill();
    for (let i = 0; i < 90; i++) {
      const a = i * 2.399, y = 1 - (i / 89) * 2, r = Math.sqrt(Math.max(0, 1 - y * y));
      const x = Math.cos(a) * r * (1 - vals.width * .22);
      ctx.beginPath(); ctx.arc(cx + x * R, cy + y * R, 1, 0, 6.2832);
      ctx.fillStyle = `rgba(200,214,255,${0.2 + ((y + 1) / 2) * 0.5})`; ctx.fill();
    }
  }, [h, vals.size, vals.width]);
  return <canvas ref={c} style={{ width: "100%", height: h, display: "block" }} />;
}
function ArpThumb({ h }) {
  const steps = [0.9, 0.5, 0.7, 0.4, 0.95, 0.55, 0.75, 0.45, 0.88, 0.52, 0.68, 0.42, 0.92, 0.5, 0.72, 0.48];
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 2, height: h, padding: "8px 9px 10px" }}>
      {steps.map((s, i) => (
        <div key={i} style={{ flex: 1, height: `${s * 100}%`, borderRadius: 2,
                              background: i % 4 === 0 ? ACC : hx(ACC, .42) }} />
      ))}
    </div>
  );
}
function BarsThumb({ fx, h }) {
  const seed = (fx.id * 37) % 7;
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 3, height: h, padding: "10px 12px" }}>
      {Array.from({ length: fx.pw * 4 }, (_, i) => {
        const v = 0.25 + ((i * 13 + seed * 7) % 10) / 14;
        return <div key={i} style={{ flex: 1, height: `${v * 100}%`, borderRadius: 2,
                                     background: hx(ACC, 0.2 + v * 0.5) }} />;
      })}
    </div>
  );
}

/* ── la carte posée dans le logement vertical ──────────────────────────────── */
function FxCard({ fx, on, onToggle, onOpen, onRemove, vals }) {
  return (
    <div onClick={onOpen} title="Open" style={{ flex: 1, display: "flex", flexDirection: "column",
                  cursor: "pointer", position: "relative",
                  background: on ? `linear-gradient(180deg,${hx(ACC, .10)},transparent 55%)` : "transparent",
                  border: `1px solid ${on ? hx(ACC, .32) : "rgba(255,255,255,.05)"}`, borderRadius: 11,
                  overflow: "hidden" }}>
      <FxThumb fx={fx} vals={vals} />
      <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 5, padding: "9px 10px 10px" }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 6, minWidth: 0 }}>
          <span style={{ fontSize: 12.5, fontWeight: 800, color: on ? INK : MUTE, whiteSpace: "nowrap",
                         overflow: "hidden", textOverflow: "ellipsis" }}>{fx.name}</span>
          <span style={{ fontSize: 9, fontWeight: 700, letterSpacing: ".09em", textTransform: "uppercase",
                         color: on ? ACC : DIM, flex: "none" }}>{fx.type}</span>
        </div>
        <span style={{ fontSize: 10, color: DIM, lineHeight: 1.45, overflow: "hidden",
                       display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical" }}>{fx.note}</span>
        <div style={{ flex: 1 }} />
        <div style={{ display: "flex", gap: 6 }}>
          <button onClick={(e) => { e.stopPropagation(); onToggle(); }} title="Engage / Bypass"
                  style={{ flex: 1, height: 26, borderRadius: 8, cursor: "pointer", display: "grid",
                           placeItems: "center", border: `1px solid ${on ? hx(ACC, .5) : LINE2}`,
                           background: on ? `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})` : `linear-gradient(180deg,${BTN_T},${BTN_B})`,
                           color: on ? ACC_TEXT : DIM }}>
            <Ico d={IC.power} size={13} sw={2.3} />
          </button>
          <button onClick={(e) => { e.stopPropagation(); onRemove(); }} title="Remove"
                  style={{ ...btn({ width: 26, height: 26, borderRadius: 8, display: "grid", placeItems: "center",
                           color: DIM, cursor: "pointer" }) }}>
            <Ico d={IC.trash} size={13} />
          </button>
        </div>
      </div>
    </div>
  );
}

/* ══ LE PANNEAU ÆTHER SPACE : les 7 potards réels + l'orbe au centre ═════════ */
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

function SpacePanel({ vals, onChange, scale, pulseRef }) {
  return (
    <div style={{ width: SPACE_W * scale, height: SPACE_H * scale, position: "relative" }}>
      <div style={{ width: SPACE_W, height: SPACE_H, transform: `scale(${scale})`, transformOrigin: "top left",
                    position: "absolute", left: 0, top: 0, borderRadius: 14, ...well({ borderRadius: 14,
                    position: "absolute", overflow: "hidden" }) }}>
        <div style={{ position: "absolute", inset: 0,
                      background: `radial-gradient(ellipse at 47% 38%, ${hx(ACC, .07)}, transparent 62%)` }} />
        {/* l'orbe au centre exact de l'ellipse : le vide prend son sens */}
        <div style={{ position: "absolute", left: 385 - 150, top: 130 - 150 }}>
          <LivingOrb vals={vals} size={300} onPulse={pulseRef} />
        </div>
        {SPACE_KNOBS.map((kn) => (
          <SpaceKnob key={kn.k} {...kn} v={vals[kn.k]} onChange={onChange(kn.k)} />
        ))}
      </div>
    </div>
  );
}

/* ══ LE PANNEAU ARPEGGIATOR ══════════════════════════════════════════════════
   Adapté du JSX DeepSeek à la fenêtre, et calé sur les vrais ArpParams :
   sub (Subdivisions 1/4..1/32), style (ArpStyle), steps, vel[], on[].
   ─────────────────────────────────────────────────────────────────────────── */
function ArpPanel({ a, set, scale }) {
  const W = 830, H = 400;
  const [held, setHeld] = useState(() => new Set([48, 52, 55]));
  const [step, setStep] = useState(-1);

  useEffect(() => {
    let raf, t0 = performance.now();
    const tick = (ts) => {
      const beats = (ts - t0) / 500;                 // ~120 BPM
      const per = SUBS.find(([k]) => k === a.sub)?.[1] ?? 2;
      setStep(Math.floor(beats * per) % a.steps);
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [a.sub, a.steps]);

  const notes = useMemo(() => [...held].sort((x, y) => x - y), [held]);
  const seq = useMemo(() => {
    if (!notes.length) return [];
    const up = [...notes];
    const dn = [...notes].reverse();
    const ud = up.length < 2 ? up : [...up, ...dn.slice(1, -1)];
    switch (a.style) {
      case "Down": return dn;
      case "UpDown": return ud;
      case "DownUp": return [...ud].reverse();
      case "Random": { const r = [...up]; for (let i = r.length - 1; i > 0; i--) { const j = (i * 7 + 3) % (i + 1); [r[i], r[j]] = [r[j], r[i]]; } return r; }
      case "Chord": return [up];
      default: return up;
    }
  }, [notes, a.style]);

  const toggle = (m) => setHeld((s) => { const n = new Set(s); n.has(m) ? n.delete(m) : n.add(m); return n; });
  const setVel = (i, e) => {
    const r = e.currentTarget.getBoundingClientRect();
    const v = clamp(1 - (e.clientY - r.top) / r.height, 0.05, 1);
    set((s) => ({ ...s, vel: s.vel.map((x, k) => (k === i ? v : x)), on: s.on.map((x, k) => (k === i ? true : x)) }));
  };
  const nm = (m) => ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"][m % 12] + (Math.floor(m / 12) - 1);

  const Label = ({ children }) => (
    <div style={{ fontSize: 9, fontWeight: 800, letterSpacing: 1.2, color: MUTE, marginBottom: 4 }}>{children}</div>
  );
  const Seg = ({ options, value, onChange, h = 24, fs = 11 }) => (
    <div style={{ position: "relative", display: "flex", padding: 2, borderRadius: 8,
                  background: "rgba(0,0,0,.35)", border: `1px solid ${LINE}` }}>
      <div style={{ position: "absolute", top: 2, bottom: 2, left: 2, width: `calc((100% - 4px) / ${options.length})`,
                    borderRadius: 6, background: `linear-gradient(180deg,${BTN_T},${BTN_B})`,
                    boxShadow: "0 1px 4px rgba(0,0,0,.4)",
                    transform: `translateX(${Math.max(0, options.findIndex((o) => o[0] === value)) * 100}%)`,
                    transition: "transform .25s cubic-bezier(.3,1.3,.5,1)" }} />
      {options.map(([k, l]) => (
        <button key={String(k)} onClick={() => onChange(k)}
                style={{ position: "relative", flex: 1, height: h, border: 0, background: "transparent",
                         cursor: "pointer", fontSize: fs, fontWeight: 700, fontFamily: FONT, padding: 0,
                         color: value === k ? INK : MUTE, transition: "color .2s" }}>{l}</button>
      ))}
    </div>
  );

  return (
    <div style={{ width: W * scale, height: H * scale, position: "relative" }}>
      <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left",
                    position: "absolute", left: 0, top: 0, display: "flex", gap: 10 }}>
        {/* colonne des réglages */}
        <div style={{ width: 252, flex: "none", display: "flex", flexDirection: "column", gap: 9,
                      ...well({ borderRadius: 12, padding: 11 }) }}>
          <div>
            <Label>SUBDIVISION</Label>
            <Seg options={SUBS.map(([k]) => [k, k])} value={a.sub} onChange={(v) => set((s) => ({ ...s, sub: v }))} />
          </div>
          <div>
            <Label>STYLE</Label>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 5 }}>
              {ARP_STYLES.map(([k, l]) => (
                <button key={k} onClick={() => set((s) => ({ ...s, style: k }))}
                        style={{ height: 26, borderRadius: 8, cursor: "pointer", fontFamily: FONT,
                                 fontSize: 10, fontWeight: 800, padding: 0,
                                 border: `1px solid ${a.style === k ? hx(ACC, .5) : LINE}`,
                                 background: a.style === k ? hx(ACC, .18) : "rgba(255,255,255,.035)",
                                 color: a.style === k ? ACC : DIM }}>{l}</button>
              ))}
            </div>
          </div>
          <div>
            <Label>STEPS</Label>
            <Seg options={[["8", 8], ["12", 12], ["16", 16]]} value={a.steps}
                 onChange={(v) => set((s) => ({ ...s, steps: v, vel: Array.from({ length: v }, (_, i) => s.vel[i] ?? 0.6), on: Array.from({ length: v }, (_, i) => s.on[i] ?? true) }))} />
          </div>
          <div style={{ flex: 1 }} />
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <button onClick={() => set((s) => ({ ...s, hold: !s.hold }))}
                    style={{ ...btn({ flex: 1, height: 30, borderRadius: 9, cursor: "pointer", fontFamily: FONT,
                             fontSize: 11, fontWeight: 800, display: "flex", alignItems: "center",
                             justifyContent: "center", gap: 6,
                             color: a.hold ? ACC_TEXT : DIM,
                             background: a.hold ? `linear-gradient(180deg,#C7D9F9,${ACC_DEEP})` : `linear-gradient(180deg,${BTN_T},${BTN_B})` }) }}>
              <Ico d={IC.hold} size={13} /> {a.hold ? "Hold on" : "Hold off"}
            </button>
          </div>
        </div>

        {/* touches tenues + séquenceur de pas */}
        <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 9 }}>
          <div style={{ ...well({ borderRadius: 12, padding: "8px 10px 10px" }) }}>
            <div style={{ display: "flex", alignItems: "center", height: 16, marginBottom: 5 }}>
              <Label>HELD NOTES · TAP THE KEYS</Label>
              <div style={{ flex: 1 }} />
              <span style={{ fontFamily: MONO, fontSize: 10.5, fontWeight: 800, color: ACC }}>
                {notes.map(nm).join("  ") || "—"}
              </span>
            </div>
            <MiniKeys held={held} seq={seq} step={step} onToggle={toggle} />
          </div>

          <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column",
                        ...well({ borderRadius: 12, padding: "8px 10px 10px" }) }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
              <Label>STEPS · DRAG A BAR TO SET VELOCITY</Label>
              <div style={{ flex: 1 }} />
              <button onClick={() => set((s) => ({ ...s, vel: s.vel.map(() => 0.3 + Math.random() * 0.7), on: s.on.map(() => true) }))}
                      style={{ ...btn({ height: 22, padding: "0 9px", borderRadius: 7, cursor: "pointer",
                               color: DIM, fontSize: 10, fontWeight: 800, fontFamily: FONT,
                               display: "inline-flex", alignItems: "center", gap: 5 }) }}>
                <Ico d={IC.rand} size={11} /> Random
              </button>
            </div>
            <div style={{ flex: 1, minHeight: 0, display: "grid", gridTemplateColumns: `repeat(${a.steps}, 1fr)`, gap: 3 }}>
              {Array.from({ length: a.steps }, (_, i) => {
                const cur = step === i;
                const on = a.on[i];
                return (
                  <div key={i} style={{ display: "flex", flexDirection: "column", gap: 3, borderRadius: 6,
                                        background: cur ? "rgba(255,255,255,.07)" : "transparent", padding: 2 }}>
                    <div style={{ textAlign: "center", fontFamily: MONO, fontSize: 8.5, fontWeight: 800,
                                  color: cur ? ACC : i % 4 === 0 ? MUTE : "#3E4657" }}>{i + 1}</div>
                    <div style={{ textAlign: "center", fontFamily: MONO, fontSize: 8.5, fontWeight: 800,
                                  height: 11, color: on ? (cur ? AMBER : SOFT) : "#39415200",
                                  whiteSpace: "nowrap", overflow: "hidden" }}>
                      {on && seq.length ? nm(seq[i % seq.length]) : ""}
                    </div>
                    <div onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); setVel(i, e); }}
                         onPointerMove={(e) => { if (e.buttons) setVel(i, e); }}
                         style={{ flex: 1, minHeight: 18, borderRadius: 5, background: "#0A0E16",
                                  position: "relative", overflow: "hidden", touchAction: "none", cursor: "ns-resize" }}>
                      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: `${a.vel[i] * 100}%`,
                                    background: on ? (cur ? AMBER : ACC) : "#2B3447", transition: "background .08s" }} />
                    </div>
                    <button onClick={() => set((s) => ({ ...s, on: s.on.map((x, k) => (k === i ? !x : x)) }))}
                            style={{ height: 17, borderRadius: 5, border: 0, cursor: "pointer", fontFamily: MONO,
                                     fontSize: 8.5, fontWeight: 800, padding: 0,
                                     background: on ? hx(ACC, .22) : "rgba(255,255,255,.04)",
                                     color: on ? ACC : MUTE }}>{on ? "ON" : "—"}</button>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── Mini clavier, du JSX DeepSeek ─────────────────────────────────────────── */
function MiniKeys({ held, seq, step, onToggle, from = 48, octaves = 2 }) {
  const whites = [], blacks = [];
  for (let m = from; m < from + 12 * octaves; m++) ([1, 3, 6, 8, 10].includes(m % 12) ? blacks : whites).push(m);
  const litNote = seq.length && step >= 0 ? seq[step % seq.length] : null;
  const Wn = whites.length;
  return (
    <div style={{ position: "relative", height: 46, display: "flex", gap: 1 }}>
      {whites.map((m) => {
        const isHeld = held.has(m), isLit = litNote === m;
        return (
          <div key={m} onClick={() => onToggle(m)}
               style={{ flex: 1, borderRadius: "0 0 4px 4px", cursor: "pointer", position: "relative",
                        background: isLit ? AMBER : isHeld ? ACC : "#DCE2EE",
                        transition: "background .08s" }} />
        );
      })}
      {blacks.map((m) => {
        const idx = whites.filter((x) => x < m).length;
        const isHeld = held.has(m), isLit = litNote === m;
        return (
          <div key={m} onClick={() => onToggle(m)}
               style={{ position: "absolute", left: `${((idx - 0.3) / Wn) * 100}%`, width: `${(0.6 / Wn) * 100}%`,
                        top: 0, height: "62%", borderRadius: "0 0 3px 3px", cursor: "pointer",
                        background: isLit ? AMBER : isHeld ? ACC : "#1A2130", transition: "background .08s" }} />
        );
      })}
    </div>
  );
}

/* ══ LA LISTE D'EFFETS ═══════════════════════════════════════════════════════
   Texture reprise de AeWindow.kt → PresetList / CatRow (le navigateur de presets
   d'Æther Space) : panneau #151B29 → #0A0E17, rail de catégories, lignes de 28
   avec barre d'accent, sélection en dégradé d'accent 22 %.
   ─────────────────────────────────────────────────────────────────────────── */
function CatalogPage({ kind, onPick, onBack, full }) {
  const list = kind === "midi" ? MIDI_FX : AUDIO_FX;
  const cats = [...new Set(list.map((f) => f.cat))];
  const [cat, setCat] = useState(null);
  const [sel, setSel] = useState(0);
  const shown = cat ? list.filter((f) => f.cat === cat) : list;

  const CatRow = ({ label, count, on, isAll, onClick }) => (
    <div onClick={onClick} style={{ height: 26, borderRadius: 7, cursor: "pointer", display: "flex",
         alignItems: "center", background: on ? hx(ACC, .18) : "transparent" }}>
      <div style={{ marginLeft: 3, width: 2.5, height: 14, borderRadius: 2, background: on ? ACC : "transparent" }} />
      <span style={{ marginLeft: 8, flex: 1, fontSize: isAll ? 9 : 8.5, fontWeight: on ? 800 : 600,
                     letterSpacing: ".12em", textTransform: "uppercase", color: on ? "#FFFFFF" : MUTE }}>{label}</span>
      <span style={{ marginRight: 9, fontSize: 9, fontFamily: MONO, color: on ? ACC : MUTE }}>{count}</span>
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
        <span style={{ fontSize: 10, color: ACC, letterSpacing: ".1em", textTransform: "uppercase", fontWeight: 700 }}>
          {kind === "midi" ? "One per track" : `Up to ${MAX_AUDIO} per track`}
        </span>
        <span style={{ fontSize: 10, color: MUTE, fontFamily: MONO }}>{shown.length} of {list.length}</span>
        <div style={{ flex: 1 }} />
      </div>

      <div style={{ flex: 1, minHeight: 0, borderRadius: 18, overflow: "hidden", position: "relative",
                    background: "linear-gradient(180deg,#151B29,#0A0E17)",
                    boxShadow: "inset 0 0 0 1px rgba(255,255,255,.04), 0 10px 30px rgba(0,0,0,.45)" }}>
        <div style={{ position: "absolute", left: 14, right: 14, top: 0, height: 1, background: "rgba(255,255,255,.15)" }} />
        <div style={{ position: "absolute", left: 14, right: 14, bottom: 0, height: 1, background: "rgba(0,0,0,.4)" }} />

        <div style={{ display: "flex", height: "100%", padding: 14, gap: 10 }}>
          <div style={{ width: 214, flex: "none", display: "flex", flexDirection: "column" }}>
            <span style={{ fontSize: 8, fontWeight: 800, letterSpacing: ".18em", color: MUTE,
                           textTransform: "uppercase", padding: "0 0 6px 12px" }}>Category</span>
            <div style={{ flex: 1, background: "rgba(0,0,0,.08)", borderRadius: 10, padding: "7px 6px", overflowY: "auto" }}>
              <CatRow label="All" count={list.length} on={cat === null} isAll onClick={() => { setCat(null); setSel(0); }} />
              {cats.map((c) => (
                <CatRow key={c} label={c} count={list.filter((f) => f.cat === c).length} on={cat === c}
                        onClick={() => { setCat(c); setSel(0); }} />
              ))}
            </div>
          </div>

          <div style={{ flex: 1, minWidth: 0, overflowY: "auto", display: "flex", flexDirection: "column", gap: 1 }}>
            {shown.map((f, i) => {
              const on = i === sel;
              return (
                <div key={f.id} onClick={() => setSel(i)} onDoubleClick={() => onPick(f)}
                     style={{ height: 28, borderRadius: 7, display: "flex", alignItems: "center", flex: "none",
                              cursor: "pointer",
                              background: on ? `linear-gradient(90deg,${hx(ACC, .22)},rgba(25,227,255,.03))`
                                             : i % 2 === 0 ? "linear-gradient(90deg,rgba(255,255,255,.028),rgba(255,255,255,.008))"
                                             : "transparent" }}>
                  <div style={{ marginLeft: 4, width: 3, height: 15, borderRadius: 2, background: on ? ACC : "transparent" }} />
                  <span style={{ marginLeft: 8, width: 20, textAlign: "right", fontSize: 9, fontFamily: MONO,
                                 color: on ? ACC : MUTE }}>{i + 1}</span>
                  <span style={{ marginLeft: 8, flex: 1, fontSize: 11, fontWeight: on ? 700 : 500,
                                 color: on ? "#FFFFFF" : INK, whiteSpace: "nowrap", overflow: "hidden",
                                 textOverflow: "ellipsis" }}>{f.name}</span>
                  <span style={{ fontSize: 9, fontWeight: 700, letterSpacing: ".04em", textTransform: "uppercase",
                                 color: on ? ACC : DIM, marginRight: 8 }}>{f.type}</span>
                  {cat === null && <span style={{ fontSize: 9, color: MUTE, marginRight: 8 }}>{f.cat}</span>}
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
  const [midi, setMidi] = useState([]);
  const [on, setOn] = useState({});
  const [view, setView] = useState(() => {
    const h = (typeof location !== "undefined" ? location.hash : "").replace("#", "");
    if (h === "midi") return { k: "catalog", kind: "midi" };
    if (h === "audio") return { k: "catalog", kind: "audio" };
    if (h === "arp") return { k: "effect", fx: MIDI_FX[0] };
    if (h === "effect") return { k: "effect", fx: AUDIO_FX[0] };
    return { k: "rack" };
  });
  const [vals, setVals] = useState(() => Object.fromEntries(SPACE_KNOBS.map((k) => [k.k, k.def])));
  const [arp, setArp] = useState({
    sub: "1/16", style: "Up", steps: 16, hold: false,
    vel: [1, .62, .78, .62, .95, .62, .78, .62, .88, .62, .78, .62, .92, .62, .78, .62],
    on: Array(16).fill(true),
  });
  const [size, setSize] = useState({ w: 880, h: 452 });
  const [auto, setAuto] = useState(true);
  const drag = useRef(null);
  const pulse = useRef(() => {});

  const engaged = (f) => on[f.id] !== false;
  const toggle = (f) => setOn((s) => ({ ...s, [f.id]: s[f.id] === false }));
  const setKnob = (k) => (v) => setVals((s) => ({ ...s, [k]: v }));

  useEffect(() => {
    if (view.k === "effect") setSize({ w: view.fx.w, h: view.fx.h });
    else if (view.k === "catalog") setSize({ w: 880, h: 430 });
    else setSize({ w: 880, h: 452 });
    setAuto(true);
  }, [view]);

  useEffect(() => {
    const move = (e) => {
      if (!drag.current) return;
      setAuto(false);
      const { x0, y0, w0, h0, ax, ay } = drag.current;
      setSize({ w: ax ? clamp(w0 + (e.clientX - x0), 560, 1100) : w0,
                h: ay ? clamp(h0 + (e.clientY - y0), 340, 680) : h0 });
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
  const isSpace = view.k === "effect" && view.fx.orb;
  const isArp = view.k === "effect" && view.fx.arp;
  const panelScale = isSpace ? clamp(Math.min((size.w - PAD * 2) / SPACE_W, bodyH / SPACE_H), 0.4, 1.6)
    : isArp ? clamp(Math.min((size.w - PAD * 2) / 830, bodyH / 400), 0.4, 1.6) : 1;
  /* Hauteur de logement : la colonne audio en tient trois, la MIDI un seul. */
  const slotH = clamp(bodyH, 150, 340);

  return (
    <div data-shot="fxwin" style={{ position: "relative", width: size.w, height: size.h, borderRadius: 22,
                  overflow: "hidden", fontFamily: FONT, color: INK, ...win(), border: `1px solid ${LINE2}`,
                  boxShadow: "0 26px 64px rgba(0,0,0,.6), inset 0 1px 0 rgba(255,255,255,.06)",
                  userSelect: "none", WebkitFontSmoothing: "antialiased", transition: "width .22s, height .22s" }}>
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
        <span style={{ fontSize: 9.5, fontFamily: MONO, color: MUTE }}>{Math.round(size.w)}×{Math.round(size.h)}</span>
        <button onClick={() => setAuto(true)} style={{ ...btn({ height: 22, padding: "0 9px", borderRadius: 7,
                 color: auto ? ACC : MUTE, fontSize: 10, fontWeight: 800, fontFamily: FONT, cursor: "pointer" }) }}>Auto</button>
        <button style={{ ...btn({ width: 26, height: 26, borderRadius: 8, display: "grid", placeItems: "center",
                 color: MUTE, cursor: "pointer" }) }}><Ico d={IC.close} size={14} /></button>
      </div>

      {/* CORPS */}
      <div style={{ position: "absolute", left: PAD, right: PAD, top: HEAD + PAD, height: bodyH, overflow: "hidden" }}>

        {view.k === "rack" && (
          <div style={{ display: "flex", gap: 12, height: "100%" }}>
            {/* SLOT AUDIO : logements verticaux côte à côte */}
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
              <div style={{ flex: 1, display: "flex", gap: 9, minHeight: 0 }}>
                {Array.from({ length: MAX_AUDIO }, (_, i) => {
                  const f = audio[i];
                  return f ? (
                    <div key={f.id} style={{ flex: 1, minWidth: 0 }}>
                      <VerticalSlot height={slotH} filled>
                        <FxCard fx={f} on={engaged(f)} vals={vals} onToggle={() => toggle(f)}
                                onOpen={() => setView({ k: "effect", fx: f })}
                                onRemove={() => setAudio((a) => a.filter((x) => x.id !== f.id))} />
                      </VerticalSlot>
                    </div>
                  ) : (
                    <div key={"e" + i} style={{ flex: 1, minWidth: 0 }}>
                      <VerticalSlot height={slotH} onAdd={() => setView({ k: "catalog", kind: "audio" })} />
                    </div>
                  );
                })}
              </div>
            </div>

            {/* séparateur : les deux groupes sont détachés */}
            <div style={{ width: 1, flex: "none", position: "relative",
                          background: `linear-gradient(180deg,transparent,${LINE2} 18%,${LINE2} 82%,transparent)` }}>
              <div style={{ position: "absolute", left: -3, top: "50%", marginTop: -3, width: 7, height: 7,
                            borderRadius: 4, background: "#2A3244", border: `1px solid ${LINE2}` }} />
            </div>

            {/* SLOT MIDI : un seul logement vertical */}
            <div style={{ flex: "0 0 30%", display: "flex", flexDirection: "column", gap: 8, minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, paddingLeft: 3 }}>
                <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: ".15em", textTransform: "uppercase", color: MUTE }}>
                  Slot MIDI
                </span>
                <span style={{ fontSize: 9.5, fontFamily: MONO, color: midi.length >= MAX_MIDI ? ACC : MUTE }}>
                  {midi.length}/{MAX_MIDI}
                </span>
                <div style={{ flex: 1, height: 1, background: LINE }} />
              </div>
              {midi.length ? (
                <VerticalSlot height={slotH} filled>
                  <FxCard fx={midi[0]} on={engaged(midi[0])} vals={vals} onToggle={() => toggle(midi[0])}
                          onOpen={() => setView({ k: "effect", fx: midi[0] })}
                          onRemove={() => setMidi([])} />
                </VerticalSlot>
              ) : (
                <VerticalSlot height={slotH} onAdd={() => setView({ k: "catalog", kind: "midi" })} />
              )}
            </div>
          </div>
        )}

        {view.k === "catalog" && (
          <CatalogPage kind={view.kind} onPick={pick} onBack={() => setView({ k: "rack" })}
                       full={view.kind === "audio" && audio.length >= MAX_AUDIO} />
        )}

        {view.k === "effect" && (
          <div style={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center" }}>
            {isSpace ? <SpacePanel vals={vals} onChange={setKnob} scale={panelScale} pulseRef={pulse} />
              : isArp ? <ArpPanel a={arp} set={setArp} scale={panelScale} />
              : (
                <div style={{ width: "100%", height: "100%", borderRadius: 16, ...well({ borderRadius: 16,
                              display: "grid", placeItems: "center", position: "relative", overflow: "hidden" }) }}>
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
              )}
          </div>
        )}
      </div>

      {/* PIED */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: FOOT, display: "flex",
                    alignItems: "center", gap: 9, padding: "0 12px", borderTop: `1px solid ${LINE}`,
                    background: "linear-gradient(0deg,rgba(255,255,255,.026),transparent)" }}>
        <button style={{ ...btn({ height: 28, padding: "0 11px", borderRadius: 9, display: "inline-flex",
                 alignItems: "center", gap: 6, color: INK, fontSize: 11, fontWeight: 800, fontFamily: FONT, cursor: "pointer" }) }}>
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

/* ── habillage : un écran STANDARD (pas un onglet), aux proportions du device ─ */
export default function FxWindowPreview() {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const fit = () => setScale(clamp(Math.min((window.innerWidth - 60) / 1000, (window.innerHeight - 150) / 700), 0.3, 1.4));
    fit(); window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, []);
  return (
    <div style={{ minHeight: "100vh", background: `radial-gradient(ellipse at 50% 0%, ${BG_T}, #0A0E16 70%)`,
                  display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
                  gap: 14, padding: 20, boxSizing: "border-box", fontFamily: FONT }}>
      <div style={{ width: 1000 * scale, height: 700 * scale, position: "relative" }}>
        <div data-shot="fxpage" style={{ width: 1000, height: 700, transform: `scale(${scale})`, transformOrigin: "top left",
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
      <div style={{ fontSize: 11.5, color: "#5B6580", textAlign: "center", maxWidth: 820, lineHeight: 1.65 }}>
        Æther FX — écran standard. Slots verticaux avec aperçu · un logement vide n'affiche qu'un « + » ·
        Slot Audio ({MAX_AUDIO} max) | Slot MIDI ({MAX_MIDI}, détaché). Toucher une carte ouvre son panneau ;
        l'orbe d'Æther Space réagit à tous les réglages ; l'Arpeggiator suit les vrais paramètres.
      </div>
    </div>
  );
}
