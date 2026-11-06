import { useState, useRef } from "react";

const P = {
  woodMid: "#6B3F1D", woodDark: "#3E2210", woodLight: "#93612E", grain: "#2A150A",
  cardTop: "#4A413B", cardBottom: "#362F2B", border: "#1C1411",
  hdrTop: "#CDBBA5", hdrBottom: "#9F8D79", hdrText: "#2A1A10",
  text: "#F2E6D6", muted: "#B9A892", amber: "#FFB04A", glow: "#FF8A1F",
  green: "#6BFF8A", red: "#FF4B3A", display: "#120B07",
};
const START = 135, SWEEP = 270;
const pt = (a, r) => [Math.cos((a * Math.PI) / 180) * r, Math.sin((a * Math.PI) / 180) * r];
const arc = (a0, a1, r) => {
  const [x0, y0] = pt(a0, r), [x1, y1] = pt(a1, r);
  return `M${x0} ${y0} A${r} ${r} 0 ${a1 - a0 > 180 ? 1 : 0} 1 ${x1} ${y1}`;
};

function Led({ on, color = P.red, s = 7, onClick }) {
  return (
    <span onClick={onClick} style={{
      width: s, height: s, borderRadius: "50%", display: "inline-block", cursor: onClick ? "pointer" : "default",
      background: on ? `radial-gradient(circle at 35% 30%, #fff, ${color} 55%, ${color}99)` : "radial-gradient(circle at 35% 30%, #5a3a33, #1a0e0b)",
      boxShadow: on ? `0 0 7px 2px ${color}77` : "none", border: "1px solid #0a0706",
    }} />
  );
}

function Knob({ value, onChange, size = 52, label, scale = [], steps = 0 }) {
  const drag = useRef(null);
  const has = scale.length > 0;
  const body = has ? 28 : 35, face = body * 0.84, a = START + SWEEP * value;
  const [ix, iy] = pt(a, face * 0.95), [jx, jy] = pt(a, face * 0.25);
  const onDown = (e) => { e.currentTarget.setPointerCapture(e.pointerId); drag.current = { y: e.clientY, v: value }; };
  const onMove = (e) => {
    if (!drag.current) return;
    let v = Math.min(1, Math.max(0, drag.current.v - (e.clientY - drag.current.y) / 110));
    if (steps) v = Math.round(v * steps) / steps;
    onChange(v);
  };
  const id = useRef("k" + Math.random().toString(36).slice(2, 7)).current;
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", touchAction: "none" }}>
      <svg width={size} height={size} viewBox="-50 -50 100 100" onPointerDown={onDown} onPointerMove={onMove} onPointerUp={() => (drag.current = null)}>
        <defs>
          <linearGradient id={id + "r"} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#8A817A" /><stop offset="1" stopColor="#1F1B18" /></linearGradient>
          <radialGradient id={id + "f"} cx="0.35" cy="0.3" r="0.9"><stop offset="0" stopColor="#8A817A" /><stop offset="0.5" stopColor="#4E4741" /><stop offset="1" stopColor="#1F1B18" /></radialGradient>
        </defs>
        {!has && <>
          <path d={arc(START, START + SWEEP, 45)} stroke="#17120F" strokeWidth="5" fill="none" strokeLinecap="round" />
          {value > 0.005 && <path d={arc(START, a, 45)} stroke={P.amber} strokeWidth="5" fill="none" strokeLinecap="round" />}
        </>}
        {has && scale.map((t, i) => {
          const ang = START + (SWEEP * i) / (scale.length - 1);
          const [x1, y1] = pt(ang, 37), [x2, y2] = pt(ang, 42), [tx, ty] = pt(ang, 48 * 0.93);
          return <g key={i}><line x1={x1} y1={y1} x2={x2} y2={y2} stroke={P.muted} strokeWidth="1.6" />
            <text x={tx} y={ty + 4} fontSize="12" fontWeight="700" fill={P.text} textAnchor="middle">{t}</text></g>;
        })}
        <circle cx="0" cy={body * 0.14} r={body * 1.08} fill="#000" opacity="0.4" />
        <circle r={body} fill={`url(#${id}r)`} />
        <circle r={face} fill={`url(#${id}f)`} />
        <ellipse cx={-face * 0.25} cy={-face * 0.5} rx={face * 0.6} ry={face * 0.3} fill="#fff" opacity="0.2" />
        <line x1={jx} y1={jy} x2={ix} y2={iy} stroke={P.amber} strokeOpacity="0.4" strokeWidth="7" strokeLinecap="round" />
        <line x1={jx} y1={jy} x2={ix} y2={iy} stroke="#FFF1DA" strokeWidth="3.5" strokeLinecap="round" />
      </svg>
      {label && <span style={{ fontSize: 7, color: P.muted }}>{label.toUpperCase()}</span>}
    </div>
  );
}

const Btn = ({ w = 40, h = 16, onClick, children }) => (
  <button onClick={onClick} style={{
    width: w, height: h, borderRadius: h / 2.6, border: "1px solid #000", cursor: "pointer", color: P.text, fontSize: 9, padding: 0,
    background: "linear-gradient(#3A3532,#0E0C0B)", boxShadow: "0 2px 3px #0008, inset 0 1px 0 #ffffff29",
  }}>{children}</button>
);
const LedBtn = ({ label, on, set, color, w = 38 }) => (
  <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 3 }}>
    <span style={{ display: "flex", gap: 3, alignItems: "center", fontSize: 8, fontWeight: 700, color: P.text }}><Led on={on} color={color} s={6} />{label.toUpperCase()}</span>
    <Btn w={w} onClick={() => set(!on)} />
  </div>
);
const Opt = ({ label, on, set }) => (
  <span onClick={set} style={{ display: "flex", gap: 4, alignItems: "center", fontSize: 8, fontWeight: 700, cursor: "pointer", color: on ? P.text : P.muted }}><Led on={on} s={6} />{label}</span>
);
const Card = ({ title, led, setLed, style, children }) => (
  <div style={{ display: "flex", flexDirection: "column", borderRadius: 8, overflow: "hidden", border: `1px solid ${P.border}`, background: `linear-gradient(${P.cardTop},${P.cardBottom})`, boxShadow: "0 3px 6px #0008", ...style }}>
    {title && <div style={{ height: 18, display: "flex", gap: 6, alignItems: "center", justifyContent: "center", background: `linear-gradient(${P.hdrTop},${P.hdrBottom})`, color: P.hdrText, fontSize: 10, fontWeight: 700, letterSpacing: 0.8 }}>
      {led !== undefined && <Led on={led} s={8} onClick={() => setLed(!led)} />}{title.toUpperCase()}</div>}
    <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "space-evenly", padding: 6 }}>{children}</div>
  </div>
);
const Display = ({ children, w, fs = 11 }) => (
  <div style={{ width: w, height: 18, display: "grid", placeItems: "center", background: P.display, border: "1px solid #5A4636", borderRadius: 4, color: P.amber, fontFamily: "monospace", fontWeight: 700, fontSize: fs }}>{children}</div>
);

const num = Array.from({ length: 11 }, (_, i) => String(i));
const eqS = ["-15dB", "-10", "-5", "0", "5", "10", "15dB"];
const presets = ["My Gospel B3", "Jazz Smoke", "Church Full", "Rock Perc"];

export default function OrganPreview() {
  const [s, setS] = useState({ preset: 0, model: 0.9, tone: 0, oct: 0, rot: true, fast: false, cab: true, vib: true, vm: 5, soft: true, pfast: true, third: true, eq: true, tr: 0.5, mid: 0.5, bass: 0.5, drv: true, drive: 0.15, rev: true, revA: 0.35, rt: 1 });
  const u = (k) => (v) => setS((o) => ({ ...o, [k]: v }));
  const wood = { width: 800, height: 360, boxSizing: "border-box", padding: 8, display: "flex", gap: 6, position: "relative", overflow: "hidden", borderRadius: 12, fontFamily: "system-ui,sans-serif",
    background: `repeating-linear-gradient(176deg, ${P.grain}22 0 1px, transparent 1px 7px), linear-gradient(${P.woodMid},${P.woodDark},${P.woodMid})`, boxShadow: "inset 0 0 90px #000a" };
  return (
    <div style={{ display: "grid", placeItems: "center", padding: 12, background: "#111" }}>
      <div style={wood}>
        <div style={{ flex: 0.17, display: "flex", flexDirection: "column", gap: 6 }}>
          <Card title="Organ Model" style={{ flex: 1.7 }}>
            <Knob size={56} value={s.model} onChange={u("model")} scale={num} steps={10} />
            <div style={{ display: "grid", gap: 3 }}>{["B3", "PIPE", "FARF"].map((l, i) => <Opt key={l} label={l} on={s.tone === i} set={() => u("tone")(i)} />)}</div>
            <div style={{ display: "grid", justifyItems: "center", gap: 3 }}>
              <span style={{ fontSize: 8, fontWeight: 700, color: P.text }}>OCT</span>
              <div style={{ display: "flex", gap: 4, alignItems: "center" }}>
                <Btn w={24} h={18} onClick={() => u("oct")(Math.max(-3, s.oct - 1))}>−</Btn>
                <Display w={34}>{s.oct > 0 ? "+" + s.oct : s.oct}</Display>
                <Btn w={24} h={18} onClick={() => u("oct")(Math.min(3, s.oct + 1))}>+</Btn>
              </div>
            </div>
          </Card>
          <Card title="Rotary" led={s.rot} setLed={u("rot")} style={{ flex: 0.8 }}>
            <div style={{ display: "flex", gap: 4 }}><LedBtn label="Speed" on={s.fast} set={u("fast")} color={P.green} /><LedBtn label="Cabinet" on={s.cab} set={u("cab")} /></div>
          </Card>
        </div>
        <div style={{ flex: 0.46, display: "flex", flexDirection: "column", gap: 6 }}>
          <Card title="Presets" style={{ height: 80 }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center", width: "100%" }}>
              <Btn w={34} h={22} onClick={() => u("preset")((s.preset + presets.length - 1) % presets.length)}>◀</Btn>
              <div style={{ flex: 1, height: 32, display: "grid", placeItems: "center", background: P.display, border: "1px solid #5A4636", borderRadius: 5, color: P.amber, fontFamily: "monospace", fontWeight: 700, fontSize: 14, textShadow: `0 0 8px ${P.glow}` }}>{presets[s.preset]}</div>
              <Btn w={34} h={22} onClick={() => u("preset")((s.preset + 1) % presets.length)}>▶</Btn>
            </div>
            <span style={{ fontSize: 7, color: P.muted }}>USER PRESET</span>
          </Card>
          <Card style={{ flex: 1 }}><span style={{ color: P.muted, fontSize: 12 }}>DRAWBARS</span></Card>
        </div>
        <div style={{ flex: 0.37, display: "flex", flexDirection: "column", gap: 6 }}>
          <div style={{ display: "flex", gap: 6, height: 86 }}>
            <Card title="Vibrato" led={s.vib} setLed={u("vib")} style={{ flex: 1 }}>
              <div style={{ display: "flex", gap: 14 }}>{[0, 1].map((c) => <div key={c} style={{ display: "grid", gap: 5 }}>{[0, 1, 2].map((r) => { const i = c * 3 + r; return <Opt key={i} label={["V1", "V2", "V3", "C1", "C2", "C3"][i]} on={s.vm === i} set={() => u("vm")(i)} />; })}</div>)}</div>
            </Card>
            <Card title="B3 Percussion" style={{ flex: 1.2 }}>
              <div style={{ display: "flex", gap: 4 }}><LedBtn label="Soft" on={s.soft} set={u("soft")} w={34} /><LedBtn label="Fast" on={s.pfast} set={u("pfast")} w={34} /><LedBtn label="Third" on={s.third} set={u("third")} w={34} /></div>
            </Card>
          </div>
          <div style={{ display: "flex", gap: 6, flex: 1, minHeight: 0 }}>
            <Card title="Equalizer" led={s.eq} setLed={u("eq")} style={{ flex: 1.1 }}>
              <Knob value={s.tr} onChange={u("tr")} label="Treble" scale={eqS} />
              <Knob value={s.mid} onChange={u("mid")} label="Mid" scale={eqS} />
              <Knob value={s.bass} onChange={u("bass")} label="Bass" scale={eqS} />
            </Card>
            <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 6 }}>
              <Card title="Drive" led={s.drv} setLed={u("drv")} style={{ flex: 1 }}><Knob value={s.drive} onChange={u("drive")} label="Amount" scale={num} /></Card>
              <Card title="Reverb" led={s.rev} setLed={u("rev")} style={{ flex: 1.3 }}>
                <Knob value={s.revA} onChange={u("revA")} label="Amount" scale={num} />
                <div style={{ display: "flex", gap: 5 }}>{["ROOM", "STAGE", "HALL"].map((l, i) => <Opt key={l} label={l} on={s.rt === i} set={() => u("rt")(i)} />)}</div>
                <Btn w={36} h={14} onClick={() => u("rt")((s.rt + 1) % 3)} />
              </Card>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
