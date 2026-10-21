import { useState } from "react";

/* ─────────────────────────────────────────────────────────────
   Settings sidebar icons — one family: 24 × 24 grid, 2 px stroke, round caps and joins.
   Audio: waveform · Interface: panel layout · MIDI: DIN connector · System: chip.
   Color is free (currentColor). Same size and weight, so they read as a set.
   ───────────────────────────────────────────────────────────── */

const base = (size, color, sw, children, rest) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" {...rest}>
    {children}
  </svg>
);

/* AUDIO — a symmetric waveform: five bars, tallest in the middle */
export function IconAudio({ size = 24, color = "currentColor", strokeWidth = 2, ...rest }) {
  return base(size, color, strokeWidth, <path d="M4 10.5v3M8 7v10M12 3.5v17M16 7v10M20 10.5v3" />, rest);
}

/* INTERFACE — an app window: header bar and a side panel */
export function IconInterface({ size = 24, color = "currentColor", strokeWidth = 2, ...rest }) {
  return base(size, color, strokeWidth, <>
    <rect x="3" y="4" width="18" height="16" rx="3" />
    <path d="M3 9.5h18M9.5 9.5V20" />
  </>, rest);
}

/* MIDI — the 5-pin DIN connector: ring and five pins on the upper half */
export function IconMidi({ size = 24, color = "currentColor", strokeWidth = 2, ...rest }) {
  const pins = [[6.8, 13], [8.2, 9], [12, 7.6], [15.8, 9], [17.2, 13]];
  return base(size, color, strokeWidth, <>
    <circle cx="12" cy="12" r="9" />
    {pins.map(([x, y]) => <circle key={x + "-" + y} cx={x} cy={y} r="0.9" fill={color} stroke="none" />)}
  </>, rest);
}

/* SYSTEM — a chip with pins on the four sides */
export function IconSystem({ size = 24, color = "currentColor", strokeWidth = 2, ...rest }) {
  return base(size, color, strokeWidth, <>
    <rect x="5" y="5" width="14" height="14" rx="2.5" />
    <rect x="9.5" y="9.5" width="5" height="5" rx="1" />
    <path d="M9.5 2v3M14.5 2v3M9.5 19v3M14.5 19v3M2 9.5h3M2 14.5h3M19 9.5h3M19 14.5h3" />
  </>, rest);
}

/* small demo: the sidebar as in the app (selected = accent color on a raised row) */
const TABS = [["Audio", IconAudio], ["Interface", IconInterface], ["MIDI", IconMidi], ["System", IconSystem]];

export default function SettingsTabIconsDemo() {
  const [sel, setSel] = useState("Interface");
  return (
    <div style={{ minHeight: "100vh", background: "#080B11", display: "grid", placeItems: "center", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif" }}>
      <div style={{ width: 230, padding: 12, borderRadius: 18, background: "#111824", display: "flex", flexDirection: "column", gap: 6 }}>
        {TABS.map(([name, Icon]) => {
          const on = sel === name;
          return (
            <button key={name} onClick={() => setSel(name)} style={{ height: 52, border: 0, borderRadius: 16, padding: "0 16px", display: "flex", alignItems: "center", gap: 16, cursor: "pointer", background: on ? "linear-gradient(180deg,#2B323F,#232A36)" : "transparent", color: on ? "#F1F4FA" : "#8E99AD", fontSize: 18, fontWeight: 800, transition: "background .25s, color .25s" }}>
              <span style={{ color: on ? "#8FB2E8" : "#8E99AD", display: "inline-flex", transition: "color .25s" }}><Icon size={24} /></span>
              {name}
            </button>
          );
        })}
      </div>
    </div>
  );
}
