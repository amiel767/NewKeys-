import { useEffect, useMemo, useRef, useState } from "react";

/* ─────────────────────────────────────────────────────────────────────────────
   ÆTHER SPACE — test de design, reverb (891 × 411 dp, la taille du device)

   Ce qui est repris, et d'où :

   · Grammaire Sunday Keys (relevée sur 20 captures officielles,
     support.sundaysounds.com/article/3140-effects) :
       - panneau plein cadre, trois bandes : en-tête / corps / pied ;
       - onglets texte « Settings / Output » (pas de segmented à pouce) ;
       - grille de cellules : libellé au-dessus, contrôle au-dessous, valeur à droite ;
       - deux contrôles seulement : le potard et le curseur horizontal ;
       - les réglages de proportion (Mix, Width, Diffuse) sont des curseurs ;
       - Engage/Bypass = bouton à gauche du nom ;
       - pied : Replace Effect / Discard Changes / Confirm.

   · FabFilter Pro-R 2 : le contrôle central s'appelle « Space » et combine
     modèle de salle + temps de décroissance. C'est ce que fait notre param 0.
     On en fait l'élément vivant : la scène montre la queue de réverbération.

   · Texture : marine + grain, tuiles en léger relief, sélection bleu pâle
     (le vocabulaire des captures SK).

   · Niches libres : icônes dessinées à la main dans le style Lucide (MIT),
     police Space Grotesk (déjà dans res/font/), aucune dépendance externe.

   La scène est vivante parce qu'elle montre ce que fait une reverb : une
   énergie qui part d'un point, s'étale, et s'éteint. Chaque paramètre agit
   sur une propriété mesurable de cette énergie — pas sur une décoration.
   ───────────────────────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const FONT = "Space Grotesk, Inter, system-ui, sans-serif";

/* Palette : marine SK + accent Æther Space (#A99AD8, relevé dans fx_layout_spec.json). */
const BG1 = "#131728", BG2 = "#1A1F33";
const TILE1 = "#1B2036", TILE2 = "#161A2C";
const WELL = "#0E1220";
const TXT = "#E7ECFB", DIM = "#7C86A8", MUTE = "#5B6590";
const LINE = "rgba(255,255,255,.07)";
const IN1 = "#C7D9F9", IN2 = "#9DB9EF";      // sélection bleu pâle (SK)
const ACC = "#A99AD8", ACC_D = "#7E6FB0";    // accent Space
const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2'/></filter><rect width='100%' height='100%' filter='url(#n)' opacity='.5'/></svg>"
)}")`;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

/* ── les 7 paramètres réels (FxRegistry.kt, FxInfo(0)) ─────────────────────── */
const PARAMS = [
  { k: "size",  name: "Size",      min: 0,   max: 100, unit: "%",  def: 0.62, knob: true  },
  { k: "decay", name: "Decay",     min: 0.4, max: 8,   unit: "s",  def: 0.70, knob: true  },
  { k: "pre",   name: "Pre-delay", min: 0,   max: 120, unit: "ms", def: 0.20, knob: true  },
  { k: "damp",  name: "Damp",      min: 1,   max: 12,  unit: "kHz",def: 0.45, knob: true  },
  { k: "width", name: "Width",     min: 0,   max: 100, unit: "%",  def: 0.80, knob: false },
  { k: "mix",   name: "Mix",       min: 0,   max: 100, unit: "%",  def: 0.35, knob: false },
  { k: "diff",  name: "Diffuse",   min: 0,   max: 100, unit: "%",  def: 0.50, knob: false },
];

/* Les 8 presets réels (FxRegistry.kt) — valeurs normalisées, dans l'ordre des params. */
const PRESETS = {
  Hall:      [0.62, 0.70, 0.20, 0.45, 0.80, 0.35, 0.50],
  Plate:     [0.40, 0.50, 0.10, 0.70, 0.90, 0.30, 0.70],
  Room:      [0.25, 0.30, 0.05, 0.50, 0.60, 0.25, 0.30],
  Cathedral: [0.95, 0.95, 0.35, 0.30, 1.00, 0.45, 0.80],
  Shimmer:   [0.70, 0.85, 0.25, 0.20, 1.00, 0.50, 0.90],
  Chamber:   [0.35, 0.40, 0.08, 0.55, 0.70, 0.28, 0.45],
  Bloom:     [0.80, 0.80, 0.30, 0.35, 0.95, 0.42, 1.00],
  Infinite:  [1.00, 1.00, 0.40, 0.25, 1.00, 0.60, 0.95],
};
const PNAMES = Object.keys(PRESETS);
const toVals = (n) => Object.fromEntries(PARAMS.map((p, i) => [p.k, PRESETS[n][i]]));
const fmt = (p, v) => {
  const raw = p.min + (p.max - p.min) * v;
  if (p.k === "decay") return raw.toFixed(2) + p.unit;
  if (p.k === "pre") return Math.round(raw) + p.unit;
  if (p.k === "damp") return (raw).toFixed(1) + p.unit;
  return Math.round(raw) + p.unit;
};

/* ── icônes dessinées dans le style Lucide (grille 24, trait 2, bouts ronds) ── */
const Ico = ({ d, size = 16, sw = 2, fill = "none" }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke="currentColor"
       strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ display: "block" }}>
    {Array.isArray(d) ? d.map((p, i) => <path key={i} d={p} />) : <path d={d} />}
  </svg>
);
const IC = {
  chevronLeft: "M15 18l-6-6 6-6",
  chevronRight: "M9 18l6-6-6-6",
  power: ["M12 2v10", "M18.36 6.64a9 9 0 1 1-12.73 0"],
  wave: "M2 12c2 0 2-4 4-4s2 8 4 8 2-8 4-8 2 4 4 4",
  sparkle: ["M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"],
  swap: ["M7 4v12", "M4 13l3 3 3-3", "M17 20V8", "M14 11l3-3 3 3"],
  check: "M20 6L9 17l-5-5",
  undo: ["M9 14L4 9l5-5", "M4 9h11a5 5 0 0 1 0 10h-3"],
  save: ["M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z", "M17 21v-8H7v8", "M7 3v5h8"],
};

/* ── le potard ─────────────────────────────────────────────────────────────── */
function Knob({ p, v, onChange, accent, size = 52 }) {
  const ref = useRef(null);
  const drag = useRef(null);
  const [hot, setHot] = useState(false);
  const ang = -135 + v * 270;

  const down = (e) => {
    drag.current = { y: e.clientY ?? e.touches[0].clientY, v };
    setHot(true);
    e.currentTarget.setPointerCapture?.(e.pointerId);
  };
  const move = (e) => {
    if (!drag.current) return;
    const y = e.clientY ?? e.touches[0].clientY;
    onChange(clamp(drag.current.v + (drag.current.y - y) / 170, 0, 1));
  };
  const up = () => { drag.current = null; setHot(false); };

  const r = size / 2, arc = 2 * Math.PI * (r - 4) * 0.75;
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 5 }}>
      <div ref={ref} onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
           onDoubleClick={() => onChange(p.def)}
           style={{ width: size, height: size, position: "relative", cursor: "ns-resize", touchAction: "none" }}>
        <svg width={size} height={size} style={{ display: "block", transform: "rotate(135deg)" }}>
          <circle cx={r} cy={r} r={r - 4} fill="none" stroke="rgba(255,255,255,.07)" strokeWidth={3}
                  strokeDasharray={`${arc} ${2 * Math.PI * (r - 4)}`} strokeLinecap="round" />
          <circle cx={r} cy={r} r={r - 4} fill="none" stroke={accent} strokeWidth={3}
                  strokeDasharray={`${arc * v} ${2 * Math.PI * (r - 4)}`} strokeLinecap="round"
                  style={{ transition: hot ? "none" : "stroke-dasharray .18s" }} />
        </svg>
        {/* le corps : léger relief, comme les tuiles SK */}
        <div style={{
          position: "absolute", inset: 9, borderRadius: "50%",
          background: `radial-gradient(120% 120% at 50% 18%, #2A3350, ${TILE2})`,
          border: `1px solid ${hot ? "rgba(169,154,216,.5)" : LINE}`,
          boxShadow: "inset 0 1px 0 rgba(255,255,255,.09), 0 3px 8px rgba(0,0,0,.45)",
          display: "grid", placeItems: "center",
        }}>
          <div style={{ width: 2.5, height: size * 0.24, borderRadius: 2, background: accent,
                        transform: `rotate(${ang}deg) translateY(-${size * 0.11}px)`, transformOrigin: "50% 100%",
                        boxShadow: `0 0 6px ${accent}88` }} />
        </div>
      </div>
      <div style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".05em", color: DIM,
                    textTransform: "uppercase", whiteSpace: "nowrap" }}>{p.name}</div>
      <div style={{ fontSize: 11, fontWeight: 700, color: TXT, fontFamily: MONO, marginTop: -3 }}>{fmt(p, v)}</div>
    </div>
  );
}

/* ── le curseur horizontal (SK : tout ce qui se lit en proportion) ─────────── */
function Slider({ p, v, onChange, accent }) {
  const ref = useRef(null);
  const drag = useRef(false);
  const set = (e) => {
    const b = ref.current.getBoundingClientRect();
    const x = (e.clientX ?? e.touches[0].clientX) - b.left;
    onChange(clamp(x / b.width, 0, 1));
  };
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, minWidth: 0 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <span style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".05em", color: DIM,
                       textTransform: "uppercase" }}>{p.name}</span>
        <span style={{ fontSize: 11, fontWeight: 700, color: TXT, fontFamily: MONO }}>{fmt(p, v)}</span>
      </div>
      <div ref={ref} onPointerDown={(e) => { drag.current = true; set(e); e.currentTarget.setPointerCapture?.(e.pointerId); }}
           onPointerMove={(e) => drag.current && set(e)}
           onPointerUp={() => (drag.current = false)} onPointerCancel={() => (drag.current = false)}
           onDoubleClick={() => onChange(p.def)}
           style={{ height: 22, position: "relative", cursor: "ew-resize", touchAction: "none", display: "flex", alignItems: "center" }}>
        <div style={{ position: "absolute", left: 0, right: 0, height: 4, borderRadius: 3,
                      background: WELL, boxShadow: "inset 0 1px 2px rgba(0,0,0,.6)" }} />
        <div style={{ position: "absolute", left: 0, width: `${v * 100}%`, height: 4, borderRadius: 3,
                      background: `linear-gradient(90deg,${ACC_D},${accent})` }} />
        <div style={{ position: "absolute", left: `calc(${v * 100}% - 7px)`, width: 14, height: 14,
                      borderRadius: "50%", background: `radial-gradient(circle at 50% 30%, #F2F5FF, ${IN2})`,
                      border: "1px solid rgba(0,0,0,.35)", boxShadow: "0 2px 6px rgba(0,0,0,.5)" }} />
      </div>
    </div>
  );
}

/* ── la scène vivante ───────────────────────────────────────────────────────
   Une source au sol émet des anneaux. Chaque paramètre pilote une grandeur
   réelle de cette énergie :
     Size   → rayon maximal des anneaux
     Decay  → constante de temps de l'extinction
     Pre    → retard du premier anneau
     Damp   → chute d'intensité avec le rayon (les grands anneaux s'éteignent)
     Width  → étirement horizontal (stéréo)
     Mix    → part d'énergie de la queue vs le signal direct
     Diff   → flou des anneaux (nombre de traits décalés)
   ─────────────────────────────────────────────────────────────────────────── */
function Scene({ vals, onFire, fireRef, accent, hover }) {
  const cvs = useRef(null);
  const state = useRef({ rings: [], motes: [], t: 0, last: 0, next: 0 });
  const vref = useRef(vals);
  vref.current = vals;

  useEffect(() => {
    const c = cvs.current, ctx = c.getContext("2d");
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = c.getBoundingClientRect();
    c.width = rect.width * dpr; c.height = rect.height * dpr;
    ctx.scale(dpr, dpr);
    const w = rect.width, h = rect.height;
    const sx = w * 0.5, sy = h * 0.66;

    const S = state.current;
    if (!S.motes.length)
      S.motes = Array.from({ length: 34 }, () => ({
        x: Math.random() * w, y: Math.random() * h,
        r: 0.5 + Math.random() * 1.3, s: 3 + Math.random() * 9, p: Math.random() * 6.3,
      }));

    const fire = () => {
      const v = vref.current;
      S.rings.push({ t: 0, delay: v.pre * 0.55, seed: Math.random() * 100 });
    };
    fireRef.current = fire;

    let raf;
    const draw = (ts) => {
      const dt = Math.min(0.05, (ts - S.last) / 1000 || 0.016);
      S.last = ts; S.t += dt;
      const v = vref.current;

      // cadence d'émission : la reverb répond, elle ne tourne pas en boucle
      S.next -= dt;
      if (S.next <= 0) { fire(); S.next = 1.5 + Math.random() * 0.9; }

      ctx.clearRect(0, 0, w, h);

      // halo de fond : la « salle »
      const halo = ctx.createRadialGradient(sx, sy, 0, sx, sy, w * (0.30 + v.size * 0.45));
      halo.addColorStop(0, `rgba(169,154,216,${0.05 + v.mix * 0.10})`);
      halo.addColorStop(1, "rgba(169,154,216,0)");
      ctx.fillStyle = halo; ctx.fillRect(0, 0, w, h);

      // l'énergie : anneaux qui s'étalent et s'éteignent
      const tau = 0.9 + v.decay * 3.4;
      const maxR = 24 + v.size * (w * 0.42);
      const stretch = 0.55 + v.width * 0.95;
      S.rings = S.rings.filter((g) => {
        g.t += dt;
        if (g.t < g.delay) return true;
        const a = g.t - g.delay;
        const p = a / tau;
        if (p >= 1) return false;
        const rad = maxR * Math.pow(p, 0.62);
        const dampK = 1 - v.damp * 0.55 * Math.pow(p, 0.8);   // Damp coupe les grands anneaux
        let alpha = Math.pow(1 - p, 2.1) * (0.32 + v.mix * 0.5) * Math.max(0, dampK);
        if (alpha < 0.004) return false;

        const layers = 1 + Math.round(v.diff * 3);           // Diffuse = flou
        for (let i = 0; i < layers; i++) {
          const o = (i - (layers - 1) / 2) * (2.2 + v.diff * 7);
          ctx.beginPath();
          ctx.ellipse(sx, sy, Math.max(0, rad + o) * stretch, Math.max(0, rad + o) * 0.42, 0, 0, 6.2832);
          ctx.strokeStyle = `rgba(169,154,216,${(alpha / layers) * (1 - i / (layers + 1))})`;
          ctx.lineWidth = 1.1 + v.diff * 1.5;
          ctx.stroke();
        }
        return true;
      });

      // la source : un point qui pulse à chaque émission
      const pulse = S.next > 1.5 ? (2.2 - S.next) : 0;
      ctx.beginPath(); ctx.arc(sx, sy, 2.6 + pulse * 2.4, 0, 6.2832);
      ctx.fillStyle = `rgba(220,228,255,${0.5 + Math.max(0, pulse) * 0.35})`;
      ctx.fill();
      ctx.beginPath(); ctx.arc(sx, sy, 9 + pulse * 5, 0, 6.2832);
      ctx.strokeStyle = `rgba(169,154,216,${0.22 + Math.max(0, pulse) * 0.2})`;
      ctx.lineWidth = 1; ctx.stroke();

      // poussière : donne le grain vivant
      ctx.save();
      for (const m of S.motes) {
        m.y -= m.s * dt; m.p += dt * 0.8;
        if (m.y < -4) { m.y = h + 4; m.x = Math.random() * w; }
        const x = m.x + Math.sin(m.p) * 7;
        ctx.beginPath(); ctx.arc(x, m.y, m.r, 0, 6.2832);
        ctx.fillStyle = `rgba(200,214,255,${0.05 + 0.06 * (0.5 + 0.5 * Math.sin(m.p * 1.7))})`;
        ctx.fill();
      }
      ctx.restore();

      raf = requestAnimationFrame(draw);
    };
    raf = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(raf);
  }, [accent]);

  return (
    <div onPointerDown={() => onFire()} style={{ position: "absolute", inset: 0, cursor: "crosshair" }}>
      <canvas ref={cvs} style={{ width: "100%", height: "100%", display: "block" }} />
      {/* courbe d'extinction, en haut : lecture directe du Decay */}
      <svg viewBox="0 0 200 46" preserveAspectRatio="none"
           style={{ position: "absolute", left: 12, right: 12, top: 8, height: 46, width: "calc(100% - 24px)",
                    opacity: hover ? 0.95 : 0.6, transition: "opacity .3s" }}>
        <path d={`M0 6 C ${40 + vals.decay * 60} 6, ${60 + vals.decay * 80} 42, 200 42`}
              fill="none" stroke={accent} strokeWidth="1.4" vectorEffect="non-scaling-stroke" />
        <path d={`M0 6 C ${40 + vals.decay * 60} 6, ${60 + vals.decay * 80} 42, 200 42 L200 46 L0 46 Z`}
              fill={`url(#g)`} opacity=".22" />
        <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={accent} /><stop offset="1" stopColor={accent} stopOpacity="0" />
        </linearGradient></defs>
      </svg>
    </div>
  );
}

/* ── l'écran ───────────────────────────────────────────────────────────────── */
function SpaceWindow() {
  const [vals, setVals] = useState(() => toVals("Hall"));
  const [preset, setPreset] = useState("Hall");
  const [tab, setTab] = useState("Settings");
  const [on, setOn] = useState(true);
  const [dirty, setDirty] = useState(false);
  const [hover, setHover] = useState(false);
  const fireRef = useRef(() => {});
  const accent = ACC;

  const set = (k) => (v) => { setVals((s) => ({ ...s, [k]: v })); setDirty(true); };
  const pick = (n) => { setVals(toVals(n)); setPreset(n); setDirty(false); };
  const step = (d) => pick(PNAMES[(PNAMES.indexOf(preset) + d + PNAMES.length) % PNAMES.length]);

  const knobs = PARAMS.filter((p) => p.knob);
  const sliders = PARAMS.filter((p) => !p.knob);

  return (
    <div style={{ position: "relative", width: W, height: H, fontFamily: FONT, color: TXT,
                  background: `linear-gradient(180deg,${BG1},${BG2})`, overflow: "hidden",
                  userSelect: "none", WebkitFontSmoothing: "antialiased" }}>
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.035, pointerEvents: "none" }} />

      {/* ── EN-TÊTE (bande 1, comme SK) ─────────────────────────────────────── */}
      <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 54, display: "flex",
                    alignItems: "center", gap: 10, padding: "0 14px", borderBottom: `1px solid ${LINE}` }}>
        <div style={{ width: 30, height: 30, borderRadius: 9, border: `1px solid ${LINE}`, display: "grid",
                      placeItems: "center", color: DIM, cursor: "pointer", background: "rgba(255,255,255,.02)" }}>
          <Ico d={IC.chevronLeft} size={15} />
        </div>
        {/* Engage/Bypass à gauche du nom — SK */}
        <button onClick={() => setOn((o) => !o)} title="Engage / Bypass"
                style={{ width: 30, height: 30, borderRadius: 9, cursor: "pointer", display: "grid", placeItems: "center",
                         border: `1px solid ${on ? "rgba(169,154,216,.55)" : LINE}`,
                         background: on ? `linear-gradient(180deg,${ACC},${ACC_D})` : "#232842",
                         color: on ? "#1B2440" : MUTE,
                         boxShadow: on ? "0 0 14px rgba(169,154,216,.4)" : "none", transition: "all .2s" }}>
          <Ico d={IC.power} size={15} sw={2.3} />
        </button>

        <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.05 }}>
          <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: ".01em" }}>Æther Space</div>
          <div style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: ".14em", color: MUTE, textTransform: "uppercase" }}>Reverb</div>
        </div>

        {/* onglets texte Settings / Output — SK, pas de segmented à pouce */}
        <div style={{ display: "flex", gap: 16, marginLeft: 16 }}>
          {["Settings", "Output"].map((t) => (
            <div key={t} onClick={() => setTab(t)} style={{ fontSize: 12.5, fontWeight: 700, cursor: "pointer",
                 color: tab === t ? TXT : MUTE, paddingBottom: 3,
                 borderBottom: `2px solid ${tab === t ? accent : "transparent"}` }}>{t}</div>
          ))}
        </div>

        <div style={{ flex: 1 }} />

        {/* sélecteur de preset : pastille + flèches, à droite de l'en-tête */}
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <div onClick={() => step(-1)} style={{ width: 26, height: 26, borderRadius: 8, border: `1px solid ${LINE}`,
                display: "grid", placeItems: "center", color: DIM, cursor: "pointer" }}><Ico d={IC.chevronLeft} size={13} /></div>
          <div style={{ minWidth: 132, height: 30, padding: "0 12px", borderRadius: 10, display: "flex",
                        alignItems: "center", justifyContent: "space-between",
                        background: `linear-gradient(180deg,${TILE1},${TILE2})`, border: `1px solid ${LINE}`,
                        boxShadow: "inset 0 1px 0 rgba(255,255,255,.06)" }}>
            <span style={{ fontSize: 12.5, fontWeight: 700 }}>{preset}</span>
            <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>{PNAMES.indexOf(preset) + 1}/{PNAMES.length}</span>
          </div>
          <div onClick={() => step(1)} style={{ width: 26, height: 26, borderRadius: 8, border: `1px solid ${LINE}`,
                display: "grid", placeItems: "center", color: DIM, cursor: "pointer" }}><Ico d={IC.chevronRight} size={13} /></div>
        </div>
        <div style={{ width: 30, height: 30, borderRadius: 9, border: `1px solid ${LINE}`, display: "grid",
                      placeItems: "center", color: DIM, cursor: "pointer", marginLeft: 2 }}><Ico d={IC.save} size={15} /></div>
      </div>

      {/* ── CORPS (bande 2) : la scène vivante + la grille de paramètres ────── */}
      <div style={{ position: "absolute", left: 14, right: 14, top: 66, bottom: 66, display: "flex", gap: 14 }}>

        {/* la scène — l'élément vivant */}
        <div onPointerEnter={() => setHover(true)} onPointerLeave={() => setHover(false)}
             style={{ position: "relative", flex: "1 1 58%", borderRadius: 16, overflow: "hidden",
                      background: `linear-gradient(180deg,${TILE1},${TILE2})`, border: `1px solid ${LINE}`,
                      boxShadow: "inset 0 1px 0 rgba(255,255,255,.05), 0 10px 26px rgba(0,0,0,.4)" }}>
          <Scene vals={vals} onFire={() => fireRef.current()} fireRef={fireRef} accent={accent} hover={hover} />
          <div style={{ position: "absolute", left: 14, bottom: 10, display: "flex", alignItems: "center", gap: 7,
                        fontSize: 10, fontWeight: 700, letterSpacing: ".08em", color: MUTE, textTransform: "uppercase",
                        pointerEvents: "none" }}>
            <Ico d={IC.wave} size={13} /> Tail — toucher la scène pour relancer
          </div>
          {!on && (
            <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center",
                          background: "rgba(11,15,22,.62)", fontSize: 12, fontWeight: 800,
                          letterSpacing: ".2em", color: MUTE, textTransform: "uppercase" }}>Bypassed</div>
          )}
        </div>

        {/* la grille — 2 lignes, potards puis curseurs (grammaire SK) */}
        <div style={{ flex: "1 1 42%", display: "flex", flexDirection: "column", justifyContent: "center", gap: 16 }}>
          {tab === "Settings" ? (
            <>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 6, justifyItems: "center" }}>
                {knobs.map((p) => <Knob key={p.k} p={p} v={vals[p.k]} onChange={set(p.k)} accent={accent} size={50} />)}
              </div>
              <div style={{ height: 1, background: LINE, margin: "0 6px" }} />
              <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 14, padding: "0 6px" }}>
                {sliders.map((p) => <Slider key={p.k} p={p} v={vals[p.k]} onChange={set(p.k)} accent={accent} />)}
              </div>
            </>
          ) : (
            /* page Output : la sortie, comme SK la sépare de Settings */
            <div style={{ display: "flex", flexDirection: "column", gap: 18, padding: "0 10px" }}>
              <Slider p={PARAMS[5]} v={vals.mix} onChange={set("mix")} accent={accent} />
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center",
                            padding: "12px 14px", borderRadius: 12, background: WELL, border: `1px solid ${LINE}` }}>
                <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: ".06em", color: DIM, textTransform: "uppercase" }}>Level</span>
                <span style={{ fontFamily: MONO, fontSize: 12, fontWeight: 700 }}>-2.4 dB</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ flex: 1, height: 6, borderRadius: 4, background: WELL, overflow: "hidden",
                              boxShadow: "inset 0 1px 2px rgba(0,0,0,.6)" }}>
                  <div style={{ width: `${18 + vals.mix * 62}%`, height: "100%",
                                background: `linear-gradient(90deg,${ACC_D},${IN1})` }} />
                </div>
                <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>L</span>
                <div style={{ flex: 1, height: 6, borderRadius: 4, background: WELL, overflow: "hidden",
                              boxShadow: "inset 0 1px 2px rgba(0,0,0,.6)" }}>
                  <div style={{ width: `${16 + vals.mix * 58}%`, height: "100%",
                                background: `linear-gradient(90deg,${ACC_D},${IN1})` }} />
                </div>
                <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>R</span>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── PIED (bande 3) : Replace / Discard / Confirm — SK ──────────────── */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 54, display: "flex",
                    alignItems: "center", gap: 10, padding: "0 14px", borderTop: `1px solid ${LINE}` }}>
        <button style={btnGhost}><Ico d={IC.swap} size={14} /> Replace Effect</button>
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 11, color: dirty ? ACC : MUTE, fontWeight: 700 }}>
          {dirty ? "Unsaved changes" : "In sync"}
        </span>
        <button style={btnGhost} disabled={!dirty}><Ico d={IC.undo} size={14} /> Discard</button>
        <button onClick={() => setDirty(false)} disabled={!dirty}
                style={{ ...btnPrimary, opacity: dirty ? 1 : 0.45, cursor: dirty ? "pointer" : "default" }}>
          <Ico d={IC.check} size={14} /> Confirm
        </button>
      </div>
    </div>
  );
}
const btnGhost = { display: "inline-flex", alignItems: "center", gap: 7, height: 34, padding: "0 14px",
  borderRadius: 10, border: `1px solid ${LINE}`, background: "rgba(255,255,255,.03)", color: TXT,
  fontSize: 12, fontWeight: 700, fontFamily: FONT, cursor: "pointer" };
const btnPrimary = { display: "inline-flex", alignItems: "center", gap: 7, height: 34, padding: "0 18px",
  borderRadius: 10, border: 0, background: `linear-gradient(180deg,${IN1},${IN2})`, color: "#1B2440",
  fontSize: 12, fontWeight: 900, fontFamily: FONT, cursor: "pointer" };

/* ── l'habillage de prévisualisation (le cadre du device, mis à l'échelle) ─── */
export default function FxSpacePreview() {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const fit = () => setScale(clamp(Math.min((window.innerWidth - 24) / W, (window.innerHeight - 96) / H), 0.4, 1.6));
    fit(); window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, []);
  return (
    <div style={{ minHeight: "100vh", background: "#0B0F16", display: "flex", flexDirection: "column",
                  alignItems: "center", justifyContent: "center", gap: 12, padding: 12 }}>
      <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "center center",
                    borderRadius: 18, overflow: "hidden", boxShadow: "0 30px 80px rgba(0,0,0,.65)" }}>
        <SpaceWindow />
      </div>
      <div style={{ fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 720, lineHeight: 1.6,
                    fontFamily: FONT }}>
        Æther Space — test de design. Glisser un potard verticalement, un curseur horizontalement ·
        double-clic = valeur du preset · toucher la scène relance la queue · onglets Settings / Output ·
        le bouton à gauche du nom engage / bypasse.
      </div>
    </div>
  );
}
