import { useEffect, useMemo, useRef, useState } from "react";

/* ═════════════════════════════════════════════════════════════════════════════
   ÆTHER SPACE — test de design, reverb. 891 × 411 dp (la taille du device).

   RÉFÉRENCES RÉELLES (mesurées, pas devinées)

   · Sunday Keys — 20 captures officielles du centre d'aide
     (support.sundaysounds.com/article/3140-effects). Grammaire retenue :
       panneau plein cadre, 3 bandes (en-tête / corps / pied) ;
       onglets TEXTE « Settings | Output » (pas de segmented à pouce) ;
       grille de cellules : libellé au-dessus, contrôle au-dessous, valeur à droite ;
       DEUX contrôles seulement : le potard et le curseur horizontal ;
       Engage/Bypass = bouton à gauche du nom ;
       pied : Replace Effect / Discard / Confirm.

   · FabFilter Pro-R 2 — son contrôle central s'appelle littéralement « Space »
     et combine modèle de salle + temps de décroissance : exactement notre
     paramètre 0. C'est lui qui devient l'élément vivant.

   · Goodhertz LoHi — palette mesurée : fond #F0F0F0, accents cyan #20C0D0 /
     teal #008090, vert #407040. Relief par ombre portée douce, pas par bordure.

   · Soundtoys Crystallizer — mesuré : fond noir, accents chauds #502010/#503010.
     Confirme la règle : fond sombre neutre, UN accent, valeurs en mono.

   · Baby Audio Crystalline — accent ambre #F0C050, bleu #80A0E0 ; dispose ses
     réglages en colonnes de potards alignés.

   · Niches libres : icônes dessinées main dans le style Lucide (MIT) ;
     Space Grotesk (OFL, déjà dans res/font/) ; aucune dépendance ajoutée.

   LE VIVANT
   La scène est une salle en perspective. L'énergie part du point source, s'étale
   en anneaux le long du sol, frappe les murs et remonte en colonnes de lumière.
   Chaque paramètre pilote une grandeur réelle de cette énergie :
     Size   → distance des murs (profondeur de la salle)
     Decay  → constante de temps de l'extinction
     Pre    → retard du premier anneau
     Damp   → chute d'intensité avec la distance
     Width  → largeur de la salle
     Mix    → part de la queue vs le signal direct
     Diff   → flou des anneaux
   ═════════════════════════════════════════════════════════════════════════════ */

export const W = 891, H = 411;
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";
const FONT = "Space Grotesk, Inter, system-ui, -apple-system, sans-serif";

/* Palette : marine SK, accent Space #A99AD8 (fx_layout_spec.json). */
const BG1 = "#131728", BG2 = "#1A1F33";
const TILE1 = "#1C2138", TILE2 = "#151A2B";
const WELL = "#0D1120", WELL2 = "#101526";
const TXT = "#E9EDFB", DIM = "#828CAE", MUTE = "#59637F";
const LINE = "rgba(255,255,255,.075)", LINE2 = "rgba(255,255,255,.14)";
const IN1 = "#C7D9F9", IN2 = "#9DB9EF";
const ACC = "#A99AD8", ACC_L = "#C9BCF2", ACC_D = "#6E5FA6";
const CYAN = "#5FD4E0";   // accent secondaire (LoHi)

/* Grain : bruit fractal en SVG inline, la seule « texture » qui coûte 0 octet
   d'asset et reste nette à toute échelle. */
const NOISE = `url("data:image/svg+xml;utf8,${encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='140' height='140'>" +
  "<filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='3' stitchTiles='stitch'/>" +
  "<feColorMatrix type='saturate' values='0'/></filter>" +
  "<rect width='100%' height='100%' filter='url(#n)'/></svg>"
)}")`;

/* Rayures diagonales très fines : lit le panneau comme une matière, pas un aplat. */
const BRUSH = `url("data:image/svg+xml;utf8,${encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='6' height='6'>" +
  "<path d='M-1 5 L5 -1 M1 7 L7 1' stroke='white' stroke-opacity='.5' stroke-width='.7'/></svg>"
)}")`;

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;

/* ── les 7 paramètres réels (FxRegistry.kt FxInfo(0)) ──────────────────────── */
const PARAMS = [
  { k: "size",  name: "Size",      min: 0,   max: 100, unit: "%",  def: 0.62, knob: true  },
  { k: "decay", name: "Decay",     min: 0.4, max: 8,   unit: "s",  def: 0.70, knob: true  },
  { k: "pre",   name: "Pre-delay", min: 0,   max: 120, unit: "ms", def: 0.20, knob: true  },
  { k: "damp",  name: "Damp",      min: 1,   max: 12,  unit: "kHz",def: 0.45, knob: true  },
  { k: "width", name: "Width",     min: 0,   max: 100, unit: "%",  def: 0.80, knob: false },
  { k: "mix",   name: "Mix",       min: 0,   max: 100, unit: "%",  def: 0.35, knob: false },
  { k: "diff",  name: "Diffuse",   min: 0,   max: 100, unit: "%",  def: 0.50, knob: false },
];
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
  if (p.k === "decay") return raw.toFixed(2) + " s";
  if (p.k === "damp") return raw.toFixed(1) + " kHz";
  if (p.k === "pre") return Math.round(raw) + " ms";
  return Math.round(raw) + " %";
};

/* ── icônes, style Lucide (grille 24, trait 2, bouts ronds) ─────────────────── */
const Ico = ({ d, size = 16, sw = 2 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
       strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ display: "block", flex: "none" }}>
    {(Array.isArray(d) ? d : [d]).map((p, i) => <path key={i} d={p} />)}
  </svg>
);
const IC = {
  chevronLeft: "M15 18l-6-6 6-6", chevronRight: "M9 18l6-6-6-6",
  power: ["M12 2v10", "M18.36 6.64a9 9 0 1 1-12.73 0"],
  swap: ["M7 4v12", "M4 13l3 3 3-3", "M17 20V8", "M14 11l3-3 3 3"],
  check: "M20 6L9 17l-5-5", undo: ["M9 14L4 9l5-5", "M4 9h11a5 5 0 0 1 0 10h-3"],
  save: ["M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z", "M17 21v-8H7v8", "M7 3v5h8"],
  sliders: ["M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3",
            "M1 14h6", "M9 8h6", "M17 16h6"],
  wave: "M2 12c2 0 2-4 4-4s2 8 4 8 2-8 4-8 2 4 4 4",
};

/* ── surfaces réutilisables ────────────────────────────────────────────────── */
const tile = (extra = {}) => ({
  background: `linear-gradient(180deg,${TILE1},${TILE2})`,
  border: `1px solid ${LINE}`,
  boxShadow: "inset 0 1px 0 rgba(255,255,255,.055), 0 6px 16px rgba(0,0,0,.35)",
  borderRadius: 12,
  ...extra,
});
const well = (extra = {}) => ({
  background: `linear-gradient(180deg,${WELL},${WELL2})`,
  border: `1px solid rgba(0,0,0,.5)`,
  boxShadow: "inset 0 1px 3px rgba(0,0,0,.55), 0 1px 0 rgba(255,255,255,.045)",
  borderRadius: 9,
  ...extra,
});

/* ── le potard ─────────────────────────────────────────────────────────────── */
function Knob({ p, v, onChange, accent, size = 54 }) {
  const drag = useRef(null);
  const [hot, setHot] = useState(false);
  const ang = -135 + v * 270;

  const down = (e) => { drag.current = { y: e.clientY, v }; setHot(true); e.currentTarget.setPointerCapture?.(e.pointerId); };
  const move = (e) => { if (!drag.current) return; onChange(clamp(drag.current.v + (drag.current.y - e.clientY) / 180, 0, 1)); };
  const up = () => { drag.current = null; setHot(false); };

  const r = size / 2, R = r - 4.5, circ = 2 * Math.PI * R, arc = circ * 0.75;
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 3 }}>
      <div onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
           onDoubleClick={() => onChange(p.def)}
           style={{ width: size, height: size, position: "relative", cursor: "ns-resize", touchAction: "none" }}>
        <svg width={size} height={size} style={{ display: "block", transform: "rotate(135deg)" }}>
          <defs>
            <linearGradient id={`k${p.k}`} x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor={ACC_L} /><stop offset="1" stopColor={accent} />
            </linearGradient>
          </defs>
          <circle cx={r} cy={r} r={R} fill="none" stroke="rgba(255,255,255,.06)" strokeWidth={3.2}
                  strokeDasharray={`${arc} ${circ}`} strokeLinecap="round" />
          {/* graduation : 9 crans, lit le potard comme un instrument */}
          {Array.from({ length: 9 }, (_, i) => {
            const a = (i / 8) * 270 - 135 + 135;
            const rad = (a * Math.PI) / 180;
            const r1 = R - 5, r2 = R - (i % 2 ? 7 : 8.5);
            return <line key={i} x1={r + r1 * Math.cos(rad)} y1={r + r1 * Math.sin(rad)}
                         x2={r + r2 * Math.cos(rad)} y2={r + r2 * Math.sin(rad)}
                         stroke="rgba(255,255,255,.13)" strokeWidth={1} strokeLinecap="round" />;
          })}
          <circle cx={r} cy={r} r={R} fill="none" stroke={`url(#k${p.k})`} strokeWidth={3.2}
                  strokeDasharray={`${arc * v} ${circ}`} strokeLinecap="round"
                  style={{ transition: hot ? "none" : "stroke-dasharray .16s", filter: hot ? `drop-shadow(0 0 4px ${accent})` : "none" }} />
        </svg>
        {/* corps : sphère éclairée par le haut, ombre interne en bas */}
        <div style={{
          position: "absolute", inset: 10, borderRadius: "50%",
          background: `radial-gradient(circle at 50% 16%, #333D5E 0%, #232B45 42%, #141828 100%)`,
          border: `1px solid ${hot ? "rgba(169,154,216,.5)" : "rgba(255,255,255,.09)"}`,
          boxShadow: `inset 0 1px 0 rgba(255,255,255,.13), inset 0 -3px 8px rgba(0,0,0,.5), 0 4px 10px rgba(0,0,0,.5)${hot ? `, 0 0 16px ${accent}55` : ""}`,
          display: "grid", placeItems: "center",
        }}>
          <div style={{ width: 2.6, height: size * 0.25, borderRadius: 2,
                        background: `linear-gradient(180deg,${ACC_L},${accent})`,
                        transform: `rotate(${ang}deg) translateY(-${size * 0.115}px)`, transformOrigin: "50% 100%",
                        boxShadow: `0 0 7px ${accent}aa` }} />
        </div>
      </div>
      <div style={{ fontSize: 9, fontWeight: 700, letterSpacing: ".07em", color: DIM,
                    textTransform: "uppercase", whiteSpace: "nowrap", marginTop: 2 }}>{p.name}</div>
      <div style={{ fontSize: 11, fontWeight: 700, color: TXT, fontFamily: MONO, lineHeight: 1 }}>{fmt(p, v)}</div>
    </div>
  );
}

/* ── le curseur horizontal (SK : tout ce qui se lit en proportion) ─────────── */
function Slider({ p, v, onChange, accent, compact }) {
  const ref = useRef(null);
  const drag = useRef(false);
  const set = (e) => {
    const b = ref.current.getBoundingClientRect();
    onChange(clamp((e.clientX - b.left) / b.width, 0, 1));
  };
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: compact ? 4 : 6, minWidth: 0 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 8 }}>
        <span style={{ fontSize: 9, fontWeight: 700, letterSpacing: ".07em", color: DIM, textTransform: "uppercase",
                       whiteSpace: "nowrap" }}>{p.name}</span>
        <span style={{ fontSize: 10.5, fontWeight: 700, color: TXT, fontFamily: MONO }}>{fmt(p, v)}</span>
      </div>
      <div ref={ref} onPointerDown={(e) => { drag.current = true; set(e); e.currentTarget.setPointerCapture?.(e.pointerId); }}
           onPointerMove={(e) => drag.current && set(e)}
           onPointerUp={() => (drag.current = false)} onPointerCancel={() => (drag.current = false)}
           onDoubleClick={() => onChange(p.def)}
           style={{ height: 20, position: "relative", cursor: "ew-resize", touchAction: "none",
                    display: "flex", alignItems: "center" }}>
        <div style={{ position: "absolute", left: 0, right: 0, height: 4, borderRadius: 3, ...well({ borderRadius: 3 }) }} />
        <div style={{ position: "absolute", left: 0, width: `${v * 100}%`, height: 4, borderRadius: 3,
                      background: `linear-gradient(90deg,${ACC_D},${accent})`,
                      boxShadow: `0 0 8px ${accent}66` }} />
        <div style={{ position: "absolute", left: `calc(${v * 100}% - 7px)`, width: 14, height: 14, borderRadius: "50%",
                      background: `radial-gradient(circle at 50% 28%, #FFFFFF, ${IN2})`,
                      border: "1px solid rgba(0,0,0,.4)", boxShadow: "0 2px 6px rgba(0,0,0,.55), inset 0 -2px 3px rgba(0,0,0,.2)" }} />
      </div>
    </div>
  );
}

/* ── LA SCÈNE : une salle en perspective, vivante ───────────────────────────
   Le dessin suit l'ordre réel d'une reverb : la source émet, l'énergie s'étale
   le long du sol, atteint les murs, puis remonte. La brume marque l'absorption.
   ─────────────────────────────────────────────────────────────────────────── */
function Scene({ vals, fireRef, accent, hover }) {
  const cvs = useRef(null);
  const st = useRef({ rings: [], motes: [], t: 0, last: 0, next: 0.4, flash: 0, wall: 0 });
  const vref = useRef(vals);
  vref.current = vals;

  useEffect(() => {
    const c = cvs.current, ctx = c.getContext("2d");
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = c.getBoundingClientRect();
    c.width = Math.max(1, rect.width * dpr); c.height = Math.max(1, rect.height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const w = rect.width, h = rect.height;

    const S = st.current;
    if (!S.motes.length)
      S.motes = Array.from({ length: 40 }, () => ({
        x: Math.random() * w, y: Math.random() * h, r: 0.4 + Math.random() * 1.2,
        s: 4 + Math.random() * 12, p: Math.random() * 6.28,
      }));

    fireRef.current = () => { S.rings.push({ t: 0, delay: vref.current.pre * 0.5, seed: Math.random() * 99 }); S.flash = 1; };

    let raf;
    const draw = (ts) => {
      const dt = Math.min(0.05, (ts - S.last) / 1000 || 0.016);
      S.last = ts; S.t += dt;
      const v = vref.current;

      S.next -= dt;
      if (S.next <= 0) { S.rings.push({ t: 0, delay: v.pre * 0.5, seed: Math.random() * 99 }); S.flash = 1; S.next = 1.9 + Math.random() * 1.1; }
      S.flash = Math.max(0, S.flash - dt * 2.2);

      /* Géométrie de la salle : le sol fuit vers le point de fuite. */
      const hz = h * 0.40;                       // ligne d'horizon
      const floor = h * 0.98;                    // bord bas du sol
      const sx = w * 0.5;                        // point de fuite (source)
      const roomW = w * (0.16 + v.width * 0.30); // demi-largeur au fond
      const wallH = h * (0.10 + v.size * 0.26);  // hauteur des murs
      const depth = 0.30 + v.size * 0.62;        // profondeur de la salle

      ctx.clearRect(0, 0, w, h);

      /* 1. Brume de salle : plus la salle est grande, plus le fond se noie. */
      const fog = ctx.createLinearGradient(0, hz - wallH * 0.5, 0, floor);
      fog.addColorStop(0, `rgba(169,154,216,${0.02 + v.size * 0.05})`);
      fog.addColorStop(1, "rgba(20,26,44,0)");
      ctx.fillStyle = fog; ctx.fillRect(0, 0, w, h);

      /* 2. Le sol : dégradé + lignes de fuite (perspective réelle). */
      const fl = ctx.createLinearGradient(0, hz, 0, floor);
      fl.addColorStop(0, "rgba(14,19,35,.92)");
      fl.addColorStop(0.55, "rgba(22,28,48,.72)");
      fl.addColorStop(1, "rgba(31,38,63,.45)");
      ctx.fillStyle = fl;
      ctx.beginPath(); ctx.moveTo(sx - roomW, hz); ctx.lineTo(sx + roomW, hz);
      ctx.lineTo(w * 0.99, floor); ctx.lineTo(w * 0.01, floor); ctx.closePath(); ctx.fill();

      ctx.save();
      // lignes de fuite : convergentes, elles portent toute la perspective
      ctx.globalAlpha = 0.34;
      ctx.strokeStyle = "rgba(169,154,216,.7)"; ctx.lineWidth = 1;
      for (let i = 0; i <= 10; i++) {
        const t = i / 10, xFar = lerp(sx - roomW, sx + roomW, t), xNear = lerp(w * 0.01, w * 0.99, t);
        ctx.beginPath(); ctx.moveTo(xFar, hz); ctx.lineTo(xNear, floor); ctx.stroke();
      }
      // lignes de profondeur, resserrées par la perspective
      for (let i = 1; i <= 7; i++) {
        const p = Math.pow(i / 7, 1.8), y = lerp(hz, floor, p);
        const half = lerp(roomW, w * 0.49, p);
        ctx.globalAlpha = 0.30 * (1 - p * 0.45);
        ctx.beginPath(); ctx.moveTo(sx - half, y); ctx.lineTo(sx + half, y); ctx.stroke();
      }
      ctx.restore();

      /* 2b. La base du mur du fond : un trait net qui pose la salle. */
      ctx.beginPath(); ctx.moveTo(sx - roomW, hz); ctx.lineTo(sx + roomW, hz);
      ctx.strokeStyle = "rgba(169,154,216,.5)"; ctx.lineWidth = 1.3; ctx.stroke();

      /* 3. Les murs : deux pans qui se rejoignent au fond. */
      const wall = ctx.createLinearGradient(0, hz - wallH, 0, hz);
      const lit = 0.05 + v.mix * 0.16;
      wall.addColorStop(0, `rgba(169,154,216,${lit})`);
      wall.addColorStop(1, "rgba(169,154,216,0)");
      ctx.fillStyle = wall;
      ctx.beginPath(); ctx.moveTo(sx - roomW, hz); ctx.lineTo(sx - roomW, hz - wallH);
      ctx.lineTo(sx + roomW, hz - wallH); ctx.lineTo(sx + roomW, hz); ctx.closePath(); ctx.fill();

      /* 4. L'énergie : anneaux qui s'étalent le long du sol puis remontent. */
      const tau = 0.85 + v.decay * 3.6;
      const maxR = 0.18 + v.size * 0.95;         // en fraction de la demi-largeur du sol
      const stretch = 0.5 + v.width * 1.0;
      S.wall = Math.max(0, S.wall - dt * 1.6);

      S.rings = S.rings.filter((g) => {
        g.t += dt;
        if (g.t < g.delay) return true;
        const p = (g.t - g.delay) / tau;
        if (p >= 1) return false;

        const rad = maxR * Math.pow(p, 0.6);
        // Damp : les hautes fréquences meurent d'abord → l'anneau lointain pâlit
        const dampK = clamp(1 - v.damp * 0.6 * Math.pow(p, 0.75), 0, 1);
        const alpha = Math.pow(1 - p, 2.0) * (0.42 + v.mix * 0.75) * dampK;
        if (alpha < 0.004) return false;
        if (p > 0.82) S.wall = Math.min(1, S.wall + dt * 3);

        // position de l'anneau dans la salle, en perspective
        const y = lerp(hz, floor, Math.pow(p, 0.75));
        const half = lerp(roomW, w * 0.48, Math.pow(p, 0.75));

        const layers = 1 + Math.round(v.diff * 3.2);   // Diffuse = flou
        for (let i = 0; i < layers; i++) {
          const off = (i - (layers - 1) / 2) * (1.6 + v.diff * 6);
          const ry = lerp(hz, floor, Math.pow(clamp(p + off / (tau * 40), 0, 1), 0.75));
          const rx = half * (0.30 + rad * 0.7) * stretch;
          ctx.beginPath();
          ctx.ellipse(sx, ry, Math.max(0, rx), Math.max(0.7, rx * 0.23), 0, 0, 6.2832);
          ctx.strokeStyle = `rgba(198,184,248,${(alpha / layers) * (1 - i / (layers + 1))})`;
          ctx.lineWidth = 1.1 + v.diff * 1.7;
          ctx.stroke();
        }

        /* Bloom : la même ellipse, floutée et élargie, composée en additif.
           C'est ce qui fait « briller » l'énergie au lieu de la dessiner. */
        const rx2 = half * (0.30 + rad * 0.7) * stretch;
        if (rx2 > 2) {
          ctx.save();
          ctx.globalCompositeOperation = "lighter";
          ctx.filter = "blur(7px)";
          ctx.beginPath();
          ctx.ellipse(sx, y, rx2, Math.max(1, rx2 * 0.24), 0, 0, 6.2832);
          ctx.strokeStyle = `rgba(150,132,220,${alpha * 0.55})`;
          ctx.lineWidth = 5 + v.diff * 4;
          ctx.stroke();
          ctx.filter = "none";
          ctx.restore();
        }

        /* Remontée contre les murs : l'énergie qui repart vers le haut. */
        if (p > 0.55) {
          const wh = wallH * (0.35 + 0.65 * ((p - 0.55) / 0.45)) * S.wall;
          const wa = alpha * 0.55 * (1 - (p - 0.55) / 0.45);
          ctx.beginPath();
          ctx.moveTo(sx - roomW * 1.02, hz); ctx.lineTo(sx - roomW * 1.02, hz - wh);
          ctx.moveTo(sx + roomW * 1.02, hz); ctx.lineTo(sx + roomW * 1.02, hz - wh);
          ctx.strokeStyle = `rgba(200,186,246,${Math.max(0, wa)})`;
          ctx.lineWidth = 1.2; ctx.stroke();
        }
        return true;
      });

      /* 5. La source : pulse à chaque émission, éclaire le sol. */
      const pulse = Math.max(0, S.flash);
      const gl = ctx.createRadialGradient(sx, hz, 0, sx, hz, 30 + pulse * 46);
      gl.addColorStop(0, `rgba(214,206,255,${0.28 + pulse * 0.45})`);
      gl.addColorStop(1, "rgba(169,154,216,0)");
      ctx.fillStyle = gl;
      ctx.beginPath(); ctx.ellipse(sx, hz, 30 + pulse * 46, (30 + pulse * 46) * 0.5, 0, 0, 6.2832); ctx.fill();
      ctx.beginPath(); ctx.arc(sx, hz, 2.2 + pulse * 2.6, 0, 6.2832);
      ctx.fillStyle = `rgba(240,236,255,${0.72 + pulse * 0.28})`; ctx.fill();

      /* 6. Poussière : le grain vivant, jamais statique. */
      for (const m of S.motes) {
        m.y -= m.s * dt; m.p += dt * 0.9;
        if (m.y < hz - wallH) { m.y = floor; m.x = Math.random() * w; }
        const x = m.x + Math.sin(m.p) * 8;
        ctx.beginPath(); ctx.arc(x, m.y, m.r, 0, 6.2832);
        ctx.fillStyle = `rgba(206,214,255,${0.05 + 0.07 * (0.5 + 0.5 * Math.sin(m.p * 1.6))})`;
        ctx.fill();
      }

      /* 7. Vignette : le cadre respire, le centre se lit. */
      const vg = ctx.createRadialGradient(sx, h * 0.5, h * 0.15, sx, h * 0.5, w * 0.62);
      vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, "rgba(6,9,18,.55)");
      ctx.fillStyle = vg; ctx.fillRect(0, 0, w, h);

      raf = requestAnimationFrame(draw);
    };
    raf = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(raf);
  }, [accent]);

  return <canvas ref={cvs} style={{ width: "100%", height: "100%", display: "block" }} />;
}

/* ── la fenêtre ────────────────────────────────────────────────────────────── */
export function SpaceWindow() {
  const [vals, setVals] = useState(() => toVals("Hall"));
  const [preset, setPreset] = useState("Hall");
  const [tab, setTab] = useState("Settings");
  const [on, setOn] = useState(true);
  const [dirty, setDirty] = useState(false);
  const [hover, setHover] = useState(false);
  const [open, setOpen] = useState(false);
  const fireRef = useRef(() => {});

  const set = (k) => (v) => { setVals((s) => ({ ...s, [k]: v })); setDirty(true); };
  const pick = (n) => { setVals(toVals(n)); setPreset(n); setDirty(false); };
  const step = (d) => pick(PNAMES[(PNAMES.indexOf(preset) + d + PNAMES.length) % PNAMES.length]);
  const knobs = PARAMS.filter((p) => p.knob);
  const sliders = PARAMS.filter((p) => !p.knob);

  return (
    <div style={{ position: "relative", width: W, height: H, fontFamily: FONT, color: TXT,
                  background: `linear-gradient(180deg,${BG1},${BG2})`, overflow: "hidden",
                  userSelect: "none", WebkitFontSmoothing: "antialiased", letterSpacing: ".005em" }}>
      {/* texture : grain + rayures, deux couches très discrètes */}
      <div style={{ position: "absolute", inset: 0, backgroundImage: NOISE, opacity: 0.030, pointerEvents: "none" }} />
      <div style={{ position: "absolute", inset: 0, backgroundImage: BRUSH, opacity: 0.012, pointerEvents: "none" }} />
      {/* halo haut : donne de la profondeur au panneau */}
      <div style={{ position: "absolute", left: "-10%", right: "-10%", top: "-40%", height: "70%",
                    background: `radial-gradient(ellipse at 50% 50%, ${ACC}1A, transparent 70%)`,
                    pointerEvents: "none" }} />

      {/* ══ EN-TÊTE ══════════════════════════════════════════════════════════ */}
      <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 56, display: "flex",
                    alignItems: "center", gap: 9, padding: "0 14px", borderBottom: `1px solid ${LINE}`,
                    background: "linear-gradient(180deg,rgba(255,255,255,.028),transparent)" }}>
        <div style={{ width: 30, height: 30, ...tile({ borderRadius: 9 }), display: "grid", placeItems: "center",
                      color: DIM, cursor: "pointer" }}><Ico d={IC.chevronLeft} size={15} /></div>

        {/* Engage à gauche du nom — SK */}
        <button onClick={() => setOn((o) => !o)} title="Engage / Bypass"
                style={{ width: 30, height: 30, borderRadius: 9, cursor: "pointer", display: "grid", placeItems: "center",
                         border: `1px solid ${on ? "rgba(169,154,216,.6)" : LINE}`,
                         background: on ? `linear-gradient(180deg,${ACC_L},${ACC_D})` : "linear-gradient(180deg,#242A44,#1B2035)",
                         color: on ? "#241C3A" : MUTE,
                         boxShadow: on ? `0 0 16px ${ACC}66, inset 0 1px 0 rgba(255,255,255,.35)` : "inset 0 1px 0 rgba(255,255,255,.06)",
                         transition: "all .22s cubic-bezier(.3,1.4,.5,1)" }}>
          <Ico d={IC.power} size={15} sw={2.3} />
        </button>

        <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.08 }}>
          <div style={{ fontSize: 15, fontWeight: 700 }}>Æther Space</div>
          <div style={{ fontSize: 9, fontWeight: 700, letterSpacing: ".16em", color: MUTE, textTransform: "uppercase" }}>Reverb</div>
        </div>

        {/* onglets texte — SK */}
        <div style={{ display: "flex", gap: 15, marginLeft: 14 }}>
          {["Settings", "Output"].map((t) => (
            <div key={t} onClick={() => setTab(t)} style={{ fontSize: 12.5, fontWeight: 700, cursor: "pointer",
                 color: tab === t ? TXT : MUTE, paddingBottom: 3, position: "relative",
                 borderBottom: `2px solid ${tab === t ? ACC : "transparent"}`,
                 transition: "color .18s" }}>{t}</div>
          ))}
        </div>

        <div style={{ flex: 1 }} />

        {/* preset : pastille + flèches */}
        <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
          <div onClick={() => step(-1)} style={{ width: 26, height: 26, ...tile({ borderRadius: 8 }), display: "grid",
                placeItems: "center", color: DIM, cursor: "pointer" }}><Ico d={IC.chevronLeft} size={13} /></div>
          <div onClick={() => setOpen((o) => !o)} style={{ ...well({ borderRadius: 10, height: 30, minWidth: 146, padding: "0 11px",
                        display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10, cursor: "pointer" }) }}>
            <span style={{ fontSize: 12.5, fontWeight: 700 }}>{preset}</span>
            <span style={{ fontSize: 9.5, fontFamily: MONO, color: MUTE }}>{PNAMES.indexOf(preset) + 1}/{PNAMES.length}</span>
          </div>
          <div onClick={() => step(1)} style={{ width: 26, height: 26, ...tile({ borderRadius: 8 }), display: "grid",
                placeItems: "center", color: DIM, cursor: "pointer" }}><Ico d={IC.chevronRight} size={13} /></div>
        </div>
        <div style={{ width: 30, height: 30, ...tile({ borderRadius: 9 }), display: "grid", placeItems: "center",
                      color: DIM, cursor: "pointer" }}><Ico d={IC.save} size={15} /></div>
      </div>

      {/* ══ CORPS ════════════════════════════════════════════════════════════ */}
      <div style={{ position: "absolute", left: 14, right: 14, top: 68, bottom: 66, display: "flex", gap: 13 }}>

        {/* la scène vivante */}
        <div onPointerEnter={() => setHover(true)} onPointerLeave={() => setHover(false)}
             onPointerDown={() => fireRef.current()}
             style={{ position: "relative", flex: "0 0 52%", ...tile({ borderRadius: 15, overflow: "hidden",
                      padding: 0, cursor: "crosshair" }) }}>
          <Scene vals={vals} fireRef={fireRef} accent={ACC} hover={hover} />
          {/* cartouche : nom du preset + Decay, lisible par-dessus la scène */}
          <div style={{ position: "absolute", left: 12, top: 10, display: "flex", alignItems: "center", gap: 7,
                        padding: "4px 9px", borderRadius: 8, background: "rgba(10,14,26,.55)",
                        border: `1px solid ${LINE}`, backdropFilter: "blur(6px)", pointerEvents: "none" }}>
            <span style={{ width: 5, height: 5, borderRadius: 3, background: ACC, boxShadow: `0 0 7px ${ACC}` }} />
            <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: ".09em", textTransform: "uppercase", color: "#C6CEE8" }}>{preset}</span>
            <span style={{ fontSize: 10, fontFamily: MONO, color: MUTE }}>{fmt(PARAMS[1], vals.decay)}</span>
          </div>
          {/* pied de scène : l'invite, discrète */}
          <div style={{ position: "absolute", left: 12, bottom: 9, display: "flex", alignItems: "center", gap: 6,
                        fontSize: 9, fontWeight: 700, letterSpacing: ".1em", color: MUTE, textTransform: "uppercase",
                        pointerEvents: "none" }}>
            <Ico d={IC.wave} size={12} /> Toucher la salle pour relancer
          </div>
          {!on && (
            <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center",
                          background: "rgba(9,12,22,.66)", backdropFilter: "blur(2px)", fontSize: 12, fontWeight: 800,
                          letterSpacing: ".22em", color: MUTE, textTransform: "uppercase" }}>Bypassed</div>
          )}
        </div>

        {/* la grille de paramètres */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", justifyContent: "center", gap: 14,
                      minWidth: 0 }}>
          {tab === "Settings" ? (
            <>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 4, justifyItems: "center",
                            padding: "10px 6px 12px", ...tile({ borderRadius: 13 }) }}>
                {knobs.map((p) => <Knob key={p.k} p={p} v={vals[p.k]} onChange={set(p.k)} accent={ACC} size={54} />)}
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 13, padding: "0 8px" }}>
                {sliders.map((p) => <Slider key={p.k} p={p} v={vals[p.k]} onChange={set(p.k)} accent={ACC} />)}
              </div>
            </>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 14, padding: "0 8px" }}>
              <Slider p={PARAMS[5]} v={vals.mix} onChange={set("mix")} accent={ACC} />
              <div style={{ ...well({ padding: "11px 13px", display: "flex", justifyContent: "space-between",
                            alignItems: "center" }) }}>
                <span style={{ fontSize: 9, fontWeight: 700, letterSpacing: ".07em", color: DIM, textTransform: "uppercase" }}>Output Level</span>
                <span style={{ fontFamily: MONO, fontSize: 11.5, fontWeight: 700 }}>{(20 * Math.log10(0.55 + vals.mix * 0.9)).toFixed(1)} dB</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                {["L", "R"].map((ch, i) => (
                  <div key={ch} style={{ flex: 1, display: "flex", alignItems: "center", gap: 7 }}>
                    <span style={{ fontSize: 9, fontFamily: MONO, color: MUTE, width: 8 }}>{ch}</span>
                    <div style={{ flex: 1, height: 7, borderRadius: 4, ...well({ borderRadius: 4, overflow: "hidden" }) }}>
                      <div style={{ width: `${18 + vals.mix * 60 + i * 4}%`, height: "100%",
                                    background: `linear-gradient(90deg,${ACC_D},${IN1})`, boxShadow: `0 0 8px ${ACC}77` }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ══ PIED ═════════════════════════════════════════════════════════════ */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 54, display: "flex",
                    alignItems: "center", gap: 9, padding: "0 14px", borderTop: `1px solid ${LINE}`,
                    background: "linear-gradient(0deg,rgba(255,255,255,.026),transparent)" }}>
        <button style={btnGhost}><Ico d={IC.swap} size={14} /> Replace Effect</button>
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 10.5, fontWeight: 700, color: dirty ? ACC_L : MUTE, transition: "color .2s" }}>
          {dirty ? "Unsaved changes" : "In sync"}
        </span>
        <button style={{ ...btnGhost, opacity: dirty ? 1 : 0.4, cursor: dirty ? "pointer" : "default" }}
                disabled={!dirty}><Ico d={IC.undo} size={14} /> Discard</button>
        <button onClick={() => setDirty(false)} disabled={!dirty}
                style={{ ...btnPrimary, opacity: dirty ? 1 : 0.42, cursor: dirty ? "pointer" : "default" }}>
          <Ico d={IC.check} size={14} /> Confirm
        </button>
      </div>

      {/* sélecteur de preset déplié (aperçu du catalogue réel) */}
      {open && (
        <div style={{ position: "absolute", right: 14, top: 60, width: 250, ...tile({ borderRadius: 13, padding: 8,
                      background: "linear-gradient(180deg,#1E2440,#161B2D)", boxShadow: "0 22px 50px rgba(0,0,0,.62)" }) }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 5 }}>
            {PNAMES.map((n) => (
              <div key={n} onClick={() => { pick(n); setOpen(false); }}
                   style={{ padding: "7px 10px", borderRadius: 8, cursor: "pointer", fontSize: 11.5, fontWeight: 700,
                            color: n === preset ? "#1B2440" : TXT,
                            background: n === preset ? `linear-gradient(180deg,${IN1},${IN2})` : "rgba(255,255,255,.035)",
                            border: `1px solid ${n === preset ? "rgba(255,255,255,.4)" : LINE}` }}>{n}</div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

const btnGhost = { display: "inline-flex", alignItems: "center", gap: 6, height: 34, padding: "0 13px",
  borderRadius: 10, border: `1px solid ${LINE}`, background: "linear-gradient(180deg,rgba(255,255,255,.055),rgba(255,255,255,.02))",
  color: TXT, fontSize: 12, fontWeight: 700, fontFamily: FONT, cursor: "pointer",
  boxShadow: "inset 0 1px 0 rgba(255,255,255,.07)" };
const btnPrimary = { display: "inline-flex", alignItems: "center", gap: 6, height: 34, padding: "0 17px",
  borderRadius: 10, border: 0, background: `linear-gradient(180deg,${IN1},${IN2})`, color: "#1B2440",
  fontSize: 12, fontWeight: 900, fontFamily: FONT, cursor: "pointer",
  boxShadow: `0 4px 14px ${IN2}44, inset 0 1px 0 rgba(255,255,255,.5)` };

/* ── habillage de prévisualisation ──────────────────────────────────────────
   La page met le cadre 891 × 411 à l'échelle. Le point de transformation est en
   haut à gauche et le cadre est centré par des marges calculées : c'est ce qui
   évite que la partie gauche soit coupée (un transform-origin centré, ou un
   padding insuffisant, rognait le bord).
   ─────────────────────────────────────────────────────────────────────────── */
export default function FxSpacePreview() {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const fit = () => {
      const availW = window.innerWidth - 32;
      const availH = window.innerHeight - 92;
      setScale(clamp(Math.min(availW / W, availH / H), 0.35, 2));
    };
    fit();
    window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, []);
  const sw = W * scale, sh = H * scale;
  return (
    <div style={{ minHeight: "100vh", background: "radial-gradient(ellipse at 50% 0%, #141a2c, #0A0E16 70%)",
                  display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
                  gap: 14, padding: 16, fontFamily: FONT, boxSizing: "border-box" }}>
      <div style={{ width: sw, height: sh, position: "relative" }}>
        <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left",
                      position: "absolute", left: 0, top: 0, borderRadius: 18, overflow: "hidden",
                      boxShadow: "0 34px 90px rgba(0,0,0,.7), 0 0 0 1px rgba(255,255,255,.07)" }}>
          <SpaceWindow />
        </div>
      </div>
      <div style={{ fontSize: 11.5, color: "#5B6580", textAlign: "center", maxWidth: 700, lineHeight: 1.65 }}>
        Æther Space — test de design, {W} × {H} dp. Glisser un potard verticalement, un curseur
        horizontalement · double-clic = valeur du preset · cliquer la pastille de preset pour la liste ·
        toucher la salle relance la queue · onglets Settings / Output · le bouton à gauche du nom engage / bypasse.
      </div>
    </div>
  );
}
