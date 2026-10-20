import { useEffect, useId, useRef, useState } from "react";

const lerp = (a, b, t) => a + (b - a) * t;
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

/**
 * SustainPedal — square Topbar case.
 * value : MIDI CC64, 0–127 (continuous, half pedal supported)
 * size  : side of the case in px
 * accent: LED / under-glow color (any CSS color)
 */
export function SustainPedal({
  value = 0,
  size = 96,
  accent = "#8f7bff",
  label = "SUSTAIN",
  threshold = 64,
}) {
  const uid = useId().replace(/:/g, "");
  const target = clamp(value / 127, 0, 1);
  const [t, setT] = useState(target);
  const sim = useRef({ x: target, v: 0, target, raf: 0, last: 0 });

  // Spring: fast and firm when pressing, softer return when releasing.
  useEffect(() => {
    const s = sim.current;
    s.target = target;
    if (s.raf) return;
    s.last = performance.now();
    const tick = (now) => {
      const dt = Math.min(0.032, (now - s.last) / 1000);
      s.last = now;
      const pressing = s.target > s.x;
      const k = pressing ? 340 : 190;
      const c = pressing ? 26 : 19;
      s.v += (k * (s.target - s.x) - c * s.v) * dt;
      s.x += s.v * dt;
      setT(s.x);
      if (Math.abs(s.target - s.x) < 0.0008 && Math.abs(s.v) < 0.002) {
        s.x = s.target;
        s.v = 0;
        setT(s.x);
        s.raf = 0;
        return;
      }
      s.raf = requestAnimationFrame(tick);
    };
    s.raf = requestAnimationFrame(tick);
  }, [target]);

  useEffect(() => () => cancelAnimationFrame(sim.current.raf), []);

  const p = clamp(t, -0.05, 1.06); // tiny overshoot allowed
  const g = clamp(p, 0, 1);
  const on = value >= threshold;

  // Geometry (viewBox 100×100)
  const yb = 30, bw = 17;
  const yf = lerp(53, 66, p);
  const hw = lerp(27, 24, p);
  const th = lerp(13, 5, p);
  const top = `M${50 - bw} ${yb} L${50 + bw} ${yb} L${50 + hw} ${yf} L${50 - hw} ${yf} Z`;
  const front = `M${50 - hw} ${yf} L${50 + hw} ${yf} L${50 + hw - 1.6} ${yf + th} L${50 - hw + 1.6} ${yf + th} Z`;
  const grips = [0.4, 0.6, 0.8].map((f) => ({
    y: lerp(yb, yf, f),
    half: lerp(bw, hw, f) - 3.5,
  }));

  const id = (n) => `${n}-${uid}`;

  return (
    <div
      role="img"
      aria-label={`Sustain pedal ${on ? "down" : "up"}`}
      style={{
        position: "relative",
        width: size,
        height: size,
        borderRadius: size * 0.2,
        background: "linear-gradient(180deg,#242c45 0%,#171d32 100%)",
        border: "1px solid #2b3452",
        boxShadow:
          "inset 0 1px 0 rgba(255,255,255,.07), inset 0 -2px 6px rgba(0,0,0,.35), 0 3px 8px rgba(0,0,0,.45)",
        overflow: "hidden",
        flexShrink: 0,
        userSelect: "none",
        fontFamily: "'Plus Jakarta Sans', system-ui, sans-serif",
      }}
    >
      {/* ambient light that rises with the pedal */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          background: `radial-gradient(ellipse at 50% 88%, ${accent} 0%, transparent 62%)`,
          opacity: g * 0.2,
        }}
      />
      <svg viewBox="0 0 100 100" width={size} height={size} style={{ position: "absolute", inset: 0 }}>
        <defs>
          <linearGradient id={id("top")} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#4a5474" />
            <stop offset="0.55" stopColor="#5f6a8c" />
            <stop offset="1" stopColor="#7681a3" />
          </linearGradient>
          <linearGradient id={id("front")} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#363e5b" />
            <stop offset="1" stopColor="#1e243b" />
          </linearGradient>
          <linearGradient id={id("hinge")} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#7a85a6" />
            <stop offset="0.45" stopColor="#3f486a" />
            <stop offset="1" stopColor="#222940" />
          </linearGradient>
          <linearGradient id={id("base")} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#1d243b" />
            <stop offset="1" stopColor="#0e1324" />
          </linearGradient>
          <linearGradient id={id("sheen")} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#fff" stopOpacity="0" />
            <stop offset="0.5" stopColor="#fff" stopOpacity="0.14" />
            <stop offset="1" stopColor="#fff" stopOpacity="0" />
          </linearGradient>
          <filter id={id("b4")} x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="4" />
          </filter>
          <filter id={id("b2")} x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="1.6" />
          </filter>
          <clipPath id={id("clip")}>
            <path d={top} />
          </clipPath>
        </defs>

        {/* label + LED */}
        <text x="13" y="15" fontSize="6.4" fontWeight="600" letterSpacing="1.1" fill="#8793b3" opacity=".8">
          {label}
        </text>
        <circle cx="85" cy="13" r="5.5" fill={accent} opacity={g * 0.55} filter={`url(#${id("b2")})`} />
        <circle cx="85" cy="13" r="2.3" fill="#1b2137" stroke="#323c5c" strokeWidth=".7" />
        <circle cx="85" cy="13" r="2.3" fill={accent} opacity={clamp(g * 1.2, 0, 1)} />

        {/* base slab */}
        <rect x="15" y="74" width="70" height="13" rx="5" fill={`url(#${id("base")})`} stroke="#2a3250" strokeWidth=".8" />
        <path d="M20 74.9 H80" stroke="#fff" strokeOpacity=".09" strokeWidth=".8" strokeLinecap="round" />
        <circle cx="22" cy="81" r="1.3" fill="#080c18" stroke="#3a4466" strokeWidth=".5" />
        <circle cx="78" cy="81" r="1.3" fill="#080c18" stroke="#3a4466" strokeWidth=".5" />

        {/* under-glow + contact shadow */}
        <ellipse cx="50" cy="73.5" rx={lerp(12, 27, g)} ry="4" fill={accent} opacity={g * 0.55} filter={`url(#${id("b4")})`} />
        <ellipse cx="50" cy="76" rx={lerp(20, 28, g)} ry={lerp(3, 2, g)} fill="#000" opacity={lerp(0.22, 0.55, g)} filter={`url(#${id("b2")})`} />

        {/* hinge */}
        <rect x="28.5" y="20.5" width="4.5" height="13" rx="2" fill="#2a3150" />
        <rect x="67" y="20.5" width="4.5" height="13" rx="2" fill="#2a3150" />
        <rect x="31" y="22" width="38" height="9" rx="4.5" fill={`url(#${id("hinge")})`} />
        <path d="M35 23.6 H65" stroke="#fff" strokeOpacity=".28" strokeWidth=".8" strokeLinecap="round" />

        {/* pedal: front face, then top surface */}
        <path d={front} fill={`url(#${id("front")})`} stroke={`url(#${id("front")})`} strokeWidth="3" strokeLinejoin="round" />
        <path
          d={`M${50 - hw + 1} ${yf + th + 1.4} L${50 + hw - 1} ${yf + th + 1.4}`}
          stroke={accent}
          strokeWidth="1.2"
          strokeLinecap="round"
          opacity={g * 0.9}
        />
        <path d={top} fill={`url(#${id("top")})`} stroke={`url(#${id("top")})`} strokeWidth="3" strokeLinejoin="round" />
        <g clipPath={`url(#${id("clip")})`}>
          <rect x="20" y="28" width="60" height="42" fill={`url(#${id("sheen")})`} />
        </g>
        {grips.map((r, i) => (
          <g key={i} strokeLinecap="round">
            <line x1={50 - r.half} x2={50 + r.half} y1={r.y} y2={r.y} stroke="#0c1020" strokeOpacity=".55" strokeWidth="1.1" />
            <line x1={50 - r.half} x2={50 + r.half} y1={r.y + 1.1} y2={r.y + 1.1} stroke="#fff" strokeOpacity=".14" strokeWidth=".8" />
          </g>
        ))}
        {/* front lip highlight */}
        <path
          d={`M${50 - hw + 1.2} ${yf + 0.2} L${50 + hw - 1.2} ${yf + 0.2}`}
          stroke="#fff"
          strokeOpacity={lerp(0.4, 0.18, g)}
          strokeWidth="1"
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}

// ───────── Preview (not part of the component) ─────────
export default function Demo() {
  const [v, setV] = useState(0);
  const hold = (e) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    setV(127);
  };
  const release = () => setV(0);

  return (
    <div
      style={{
        minHeight: 360,
        background: "#0b1020",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 22,
        padding: 24,
        fontFamily: "'Plus Jakarta Sans', system-ui, sans-serif",
        color: "#9aa6c6",
      }}
    >
      <div
        onPointerDown={hold}
        onPointerUp={release}
        onPointerCancel={release}
        style={{ touchAction: "none", cursor: "pointer" }}
      >
        <SustainPedal value={v} size={120} />
      </div>
      <label style={{ display: "flex", alignItems: "center", gap: 12, fontSize: 13 }}>
        CC64
        <input
          type="range"
          min={0}
          max={127}
          value={v}
          onChange={(e) => setV(+e.target.value)}
          style={{ width: 200, accentColor: "#8f7bff" }}
        />
        <span style={{ width: 28, textAlign: "right", fontVariantNumeric: "tabular-nums" }}>{v}</span>
      </label>
      <div style={{ fontSize: 12, opacity: 0.7 }}>Hold the case or drag the slider to simulate the pedal</div>
    </div>
  );
}
