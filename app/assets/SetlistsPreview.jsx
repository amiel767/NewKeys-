import { useState, useRef, useEffect, useMemo } from "react";

/* ─────────────────────────────────────────────────────────────
   SETLISTS — landscape smartphone preview (891 × 411 dp)
   Everything inside the device frame is the real design, in English.
   Controls under the frame are preview helpers only.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;

const PAL = [
  { id: "blue", hex: "#8FB2E8", tx: "#A9C6F0" },
  { id: "amber", hex: "#D8A24A", tx: "#E6BC73" },
  { id: "rose", hex: "#C2587A", tx: "#DB8AA3" },
  { id: "sage", hex: "#86A394", tx: "#A5BFB1" },
];
const palOf = (id) => PAL.find((p) => p.id === id) || PAL[0];
const hexToRgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const rgba = (h, a) => { const [r, g, b] = hexToRgb(h); return `rgba(${r},${g},${b},${a})`; };
const lerp = (a, b, t) => a + (b - a) * t;
const HAP = { on: true };
const vib = (ms) => { if (!HAP.on) return; try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const DEFAULTS = { animatedBg: true, cardAuras: true, compactRows: false, patchDots: true, openWith: "tap", pinnedOnTop: true, rememberSort: true, lockDeletion: false, haptics: true };
const plural = (n) => `${n} ${n === 1 ? "patch" : "patches"}`;

function autoColor(items, afterId) {
  if (afterId) {
    const i = PAL.findIndex((p) => p.id === afterId);
    return PAL[(i + 1) % PAL.length].id;
  }
  const counts = PAL.map((p) => items.filter((s) => s.color === p.id).length);
  return PAL[counts.indexOf(Math.min(...counts))].id;
}

function fmtTime(ts) {
  const d = new Date(ts), n = new Date();
  const hm = d.toTimeString().slice(0, 5);
  const y = new Date(n); y.setDate(n.getDate() - 1);
  if (d.toDateString() === n.toDateString()) return `Today, ${hm}`;
  if (d.toDateString() === y.toDateString()) return `Yesterday, ${hm}`;
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

const seed = () => {
  const n = Date.now();
  return [
    { id: 1, name: "Main Stage", patches: 12, color: "blue", pinned: true, ts: n - 3 * 3600e3 },
    { id: 2, name: "Studio Jams", patches: 7, color: "amber", pinned: false, ts: n - 40 * 60e3 },
    { id: 3, name: "Ambient Pads", patches: 9, color: "sage", pinned: false, ts: n - 27 * 3600e3 },
    { id: 4, name: "Acoustic Night", patches: 5, color: "rose", pinned: false, ts: n - 6 * 24 * 3600e3 },
    { id: 5, name: "Warm-Up", patches: 3, color: "blue", pinned: false, ts: n - 5 * 3600e3 },
    { id: 6, name: "Sunday Service", patches: 8, color: "amber", pinned: true, ts: n - 3 * 24 * 3600e3 },
    { id: 7, name: "Rehearsal", patches: 6, color: "sage", pinned: false, ts: n - 9 * 24 * 3600e3 },
  ];
};

const CSS = `
.sl *{box-sizing:border-box}
.sl button{font-family:inherit}
@keyframes rise{from{opacity:0;translate:0 10px}to{opacity:1;translate:0 0}}
.rise{animation:rise .42s cubic-bezier(.2,.8,.2,1) backwards}
@keyframes roll{from{opacity:0;translate:0 9px}to{opacity:1;translate:0 0}}
.roll{animation:roll .22s ease-out}
@keyframes breathe{0%,100%{opacity:.3}50%{opacity:.7}}
@keyframes shimmer{0%{background-position:-240px 0}100%{background-position:240px 0}}
@keyframes pop{0%{transform:scale(1)}40%{transform:scale(1.55) rotate(-14deg)}100%{transform:scale(1)}}
@keyframes shrink{from{width:100%}to{width:0}}
@keyframes snackIn{from{opacity:0;translate:0 14px}to{opacity:1;translate:0 0}}
@keyframes menuIn{from{opacity:0;scale:.94}to{opacity:1;scale:1}}
.press{transition:transform .2s cubic-bezier(.3,1.5,.5,1),box-shadow .25s,background .25s}
.press:active{transform:scale(.98)}
.btn{transition:transform .15s cubic-bezier(.3,1.5,.5,1),filter .2s,background .2s}
.btn:active{transform:scale(.96)}
.btn:hover{filter:brightness(1.08)}
@keyframes auraDrift{0%{translate:0 0}50%{translate:90px 34px}100%{translate:30px -18px}}
.noscroll::-webkit-scrollbar{width:0;height:0}
.skel{background:linear-gradient(90deg,rgba(255,255,255,.04) 0%,rgba(255,255,255,.09) 50%,rgba(255,255,255,.04) 100%);background-size:480px 100%;animation:shimmer 1.4s linear infinite}
`;

/* ── tiny icon set ── */
function Ico({ n, size = 18, color = "currentColor", sw = 2 }) {
  const P = {
    search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
    gear: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></>,
    plus: <path d="M12 5v14M5 12h14" />,
    pin: <><path d="M12 16v6" /><path d="M8.5 3h7l-1 6 3 3.5H6.5L9.5 9z" /></>,
    trash: <><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" /></>,
    copy: <><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M5 15V6a2 2 0 0 1 2-2h9" /></>,
    check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
    x: <path d="M6 6l12 12M18 6L6 18" />,
    edit: <path d="M4 20h4L19 9l-4-4L4 16z" />,
    swatch: <><circle cx="12" cy="12" r="8" /><circle cx="9" cy="10" r="1" /><circle cx="14" cy="9" r="1" /><circle cx="15" cy="14" r="1" /></>,
    lock: <><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></>,
    backspace: <><path d="M21 5H9l-6 7 6 7h12z" /><path d="M13 9l4 6M17 9l-4 6" /></>,
    shift: <path d="M12 4l8 9h-5v7H9v-7H4z" />,
  };
  if (n === "dots")
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill={color}>
        <circle cx="12" cy="5" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="12" cy="19" r="2" />
      </svg>
    );
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">
      {P[n]}
    </svg>
  );
}

/* Rolling number: the new value slides in */
function Roll({ value }) {
  const prev = useRef(value);
  const [k, setK] = useState(0);
  useEffect(() => {
    if (prev.current !== value) { prev.current = value; setK((a) => a + 1); }
  }, [value]);
  return <span key={k} className={k ? "roll" : ""} style={{ display: "inline-block" }}>{value}</span>;
}

function PatchDots({ n, hex, max = 12 }) {
  const c = Math.min(n, max);
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
      {Array.from({ length: c }).map((_, i) => (
        <span key={i} style={{ width: 5, height: 5, borderRadius: 3, background: rgba(hex, 0.35 + 0.55 * (c > 1 ? i / (c - 1) : 1)) }} />
      ))}
      {n > max && <span style={{ fontSize: 10, color: "#8690A4", marginLeft: 2 }}>+{n - max}</span>}
      {n === 0 && <span style={{ fontSize: 10, color: "#566075" }}>empty</span>}
    </div>
  );
}

/* ── living background halos ── */
function Halos({ tint, scrollRef, reduced }) {
  const els = useRef([]);
  const tintRef = useRef(tint);
  tintRef.current = tint;
  const cfg = useMemo(() => [
    { c: "#7F98C4", r: 230, ax: 0.34, ay: 0.3, by: 0.60, fx: 0.0000739, fy: 0.0000898, px: 0, py: 1.2, a: 0.12, m: 0.5 },
    { c: "#566C9A", r: 280, ax: 0.38, ay: 0.26, by: 0.56, fx: 0.0000571, fy: 0.0000838, px: 2.1, py: 0.4, a: 0.10, m: 0.45 },
    { c: "#A8884F", r: 170, ax: 0.3, ay: 0.34, by: 0.62, fx: 0.0000967, fy: 0.0000661, px: 4.0, py: 2.6, a: 0.05, m: 0.6 },
    { c: "#9A5670", r: 200, ax: 0.28, ay: 0.3, by: 0.58, fx: 0.0000785, fy: 0.0001013, px: 1.0, py: 5.0, a: 0.05, m: 0.6 },
  ], []);

  useEffect(() => {
    const cur = cfg.map((b) => hexToRgb(b.c));
    let raf = 0, last = 0;
    const paint = (t) => {
      cfg.forEach((b, i) => {
        const el = els.current[i];
        if (!el) return;
        const base = hexToRgb(b.c);
        const tg = tintRef.current ? hexToRgb(tintRef.current).map((v, k) => lerp(base[k], v, b.m)) : base;
        cur[i] = cur[i].map((v, k) => lerp(v, tg[k], reduced ? 1 : 0.07));
        const x = W * (0.5 + b.ax * Math.sin(t * b.fx + b.px)) - b.r;
        const y = H * (b.by + b.ay * Math.cos(t * b.fy + b.py) * (0.75 + 0.25 * Math.sin(t * b.fy * 0.31 + b.px))) - b.r;
        const [r, g, bl] = cur[i].map(Math.round);
        el.style.transform = `translate3d(${x}px,${y}px,0)`;
        el.style.background = `radial-gradient(circle, rgba(${r},${g},${bl},${b.a}) 0%, rgba(${r},${g},${bl},0) 68%)`;
      });
    };
    if (reduced) {
      paint(0);
      const id = setInterval(() => paint(0), 300);
      return () => clearInterval(id);
    }
    const loop = (t) => {
      raf = requestAnimationFrame(loop);
      if (t - last < 33) return; // ~30 fps cap
      last = t;
      paint(t);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [reduced, cfg, scrollRef]);

  return (
    <div style={{ position: "absolute", inset: 0, overflow: "hidden", zIndex: 0, pointerEvents: "none" }}>
      {cfg.map((b, i) => (
        <div key={i} ref={(el) => (els.current[i] = el)}
          style={{ position: "absolute", left: 0, top: 0, width: b.r * 2, height: b.r * 2, borderRadius: "50%", willChange: "transform" }} />
      ))}
    </div>
  );
}

const OpenTag = ({ s }) => {
  const p = palOf(s.color);
  return (
    <span style={{ fontSize: 9, letterSpacing: 1.1, fontWeight: 700, padding: "2px 6px", borderRadius: 6, background: rgba(p.hex, 0.16), color: p.tx, boxShadow: `inset 0 0 0 1px ${rgba(p.hex, 0.28)}`, flex: "none" }}>OPEN</span>
  );
};

/* faint tinted contour: strongest toward the aura corner, fades out opposite */
function Contour({ hex, strength = 0.25, radius = 16 }) {
  return (
    <div style={{
      position: "absolute", inset: 0, borderRadius: radius, padding: 1, pointerEvents: "none",
      background: `linear-gradient(135deg, ${rgba(hex, strength)} 0%, ${rgba(hex, strength * 0.35)} 45%, rgba(255,255,255,.05) 100%)`,
      WebkitMask: "linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)", WebkitMaskComposite: "xor",
      mask: "linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)", maskComposite: "exclude",
    }} />
  );
}

/* ── top card (4 most recent, fixed) ── */
function Card({ s, i, isOpen, isLast, pinPop, reduced, aura, longOpen, onLong, onOpen, onMenu }) {
  const p = palOf(s.color);
  const timer = useRef(null);
  const fired = useRef(false);
  const pd = (e) => {
    if (!longOpen || e.target.closest("[data-nosw]")) return;
    fired.current = false; clearTimeout(timer.current);
    timer.current = setTimeout(() => { fired.current = true; vib(12); onLong(s.id); }, 450);
  };
  const pc = () => clearTimeout(timer.current);
  const dur = 60 + (s.id % 5) * 5;
  return (
    <div className="press rise" onClick={() => { if (fired.current) { fired.current = false; return; } onOpen(s.id); }}
      onPointerDown={pd} onPointerUp={pc} onPointerLeave={pc} onPointerCancel={pc}
      style={{
        animationDelay: `${i * 30}ms`, flex: "0 0 calc(25% - 9px)", minWidth: 0, height: 96, position: "relative", borderRadius: 16, cursor: "pointer",
        padding: "12px 8px 10px 14px", userSelect: "none", overflow: "hidden",
        background: isOpen ? "linear-gradient(180deg, rgba(255,255,255,.11), rgba(255,255,255,.06))" : "linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,.035))",
        boxShadow: "0 6px 14px rgba(0,0,0,.3)", transition: "background .3s",
      }}>
      {/* aura: a small tinted bubble drifting slowly inside the card */}
      {aura && <div style={{
        position: "absolute", left: -40, top: -50, width: 170, height: 170, borderRadius: "50%", pointerEvents: "none",
        background: `radial-gradient(circle, ${rgba(p.hex, 1)} 0%, ${rgba(p.hex, 0)} 66%)`,
        opacity: isOpen ? 0.2 : isLast ? 0.14 : 0.1, transition: "opacity .4s",
        animation: `auraDrift ${dur}s ease-in-out ${-i * 9}s infinite alternate`, animationPlayState: reduced ? "paused" : "running",
      }} />}
      <Contour hex={p.hex} strength={isOpen ? 0.4 : isLast ? 0.32 : 0.24} />
      <div style={{ position: "relative", display: "flex", alignItems: "center", gap: 8 }}>
        <div style={{ flex: 1, minWidth: 0, fontSize: 16, fontWeight: 700, color: "#E9EDF5", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{s.name}</div>
        {isOpen && <OpenTag s={s} />}
        {s.pinned && <span style={{ color: "#8F99AD", display: "inline-flex", animation: pinPop === s.id ? "pop .45s" : "none" }}><Ico n="pin" size={12} /></span>}
        <button data-nosw onClick={(e) => { e.stopPropagation(); onMenu(e, s.id); }} className="btn"
          style={{ width: 26, height: 26, margin: "0 -2px 0 -2px", border: 0, background: "transparent", color: "#7B859A", borderRadius: 13, display: "grid", placeItems: "center", cursor: "pointer", flex: "none" }}>
          <Ico n="dots" size={15} />
        </button>
      </div>
      <div style={{ position: "relative", marginTop: 6, fontSize: 13, fontWeight: 600, color: "#B4BED0", display: "flex", gap: 5 }}>
        <Roll value={s.patches} /> <span>{s.patches === 1 ? "patch" : "patches"}</span>
      </div>
      <div style={{ position: "relative", marginTop: 5, fontSize: 10, color: "#6F7A90", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>Last used: {fmtTime(s.ts)}</div>
    </div>
  );
}

/* ── list row with swipe-left delete ── */
function SwipeRow({ s, y, i, h, showDots, locked, onLocked, longOpen, onLong, removing, isOpen, isLast, pinPop, scaleRef, onOpen, onMenu, onDelete }) {
  const p = palOf(s.color);
  const [dx, setDx] = useState(0);
  const [drag, setDrag] = useState(false);
  const [armed, setArmed] = useState(false);
  const armedRef = useRef(false);
  const st = useRef({ down: false, x: 0, y: 0, moved: false, w: 800, lt: null, long: false });
  const ref = useRef(null);

  const down = (e) => {
    if (e.target.closest("[data-nosw]")) return;
    st.current = { down: true, x: e.clientX, y: e.clientY, moved: false, w: ref.current ? ref.current.offsetWidth : 800, lt: null, long: false };
    if (longOpen) {
      st.current.lt = setTimeout(() => {
        if (st.current.down && !st.current.moved) { st.current.long = true; vib(12); onLong(s.id); }
      }, 450);
    }
  };
  const move = (e) => {
    const t = st.current;
    if (!t.down) return;
    const sc = scaleRef.current;
    const rx = (e.clientX - t.x) / sc, ry = (e.clientY - t.y) / sc;
    if (!t.moved) {
      if (Math.abs(rx) > 8 && Math.abs(rx) > Math.abs(ry) * 1.2) {
        clearTimeout(t.lt);
        if (locked) { t.down = false; onLocked(); return; }
        t.moved = true; setDrag(true);
        try { e.currentTarget.setPointerCapture(e.pointerId); } catch (_) {}
      } else if (Math.abs(ry) > 8) { clearTimeout(t.lt); t.down = false; return; }
      else return;
    }
    const d = Math.min(0, rx);
    const eff = d > -24 ? d : -24 + (d + 24) * 0.75; // light resistance
    setDx(eff);
    const thr = Math.min(t.w * 0.4, 200);
    const a = eff <= -thr;
    if (a !== armedRef.current) { armedRef.current = a; setArmed(a); if (a) vib(14); }
  };
  const end = (cancel) => () => {
    const t = st.current;
    if (!t.down) return;
    t.down = false;
    clearTimeout(t.lt);
    if (t.moved) {
      setDrag(false);
      if (armedRef.current && !cancel) onDelete(s.id); else setDx(0);
      armedRef.current = false; setArmed(false);
    } else if (!cancel && !t.long) {
      onOpen(s.id);
    }
  };

  const GAP = 8; // visible gap between the card and the delete pill
  const pillW = Math.max(0, -dx - GAP);
  return (
    <div style={{
      position: "absolute", left: 0, right: 0, top: 0, height: h,
      transform: `translateY(${y}px) translateX(${removing ? -36 : 0}px)`, opacity: removing ? 0 : 1,
      transition: "transform .38s cubic-bezier(.2,.9,.25,1), opacity .22s",
    }}>
      <div className="rise" style={{ animationDelay: `${i * 30}ms`, height: "100%", position: "relative" }}>
        {/* delete pill, detached from the row */}
        <div style={{
          position: "absolute", right: 0, top: 0, bottom: 0, width: pillW, borderRadius: 13, overflow: "hidden",
          background: armed ? "#B8465F" : "#7A2E45",
          display: "flex", alignItems: "center", justifyContent: "center", color: "#fff",
          opacity: Math.min(1, pillW / 40), transition: drag ? "background .15s" : "width .38s, opacity .3s, background .15s",
        }}>
          {pillW > 36 && (
            <span style={{ display: "inline-flex", transform: `rotate(${armed ? -14 : 0}deg) scale(${armed ? 1.15 : 1})`, transition: "transform .25s cubic-bezier(.3,1.6,.5,1)" }}>
              <Ico n="trash" size={19} />
            </span>
          )}
        </div>
        <div ref={ref} className="press"
          onPointerDown={down} onPointerMove={move} onPointerUp={end(false)} onPointerCancel={end(true)}
          style={{
            position: "relative", height: "100%", borderRadius: 13, display: "flex", alignItems: "center", gap: 12, padding: "0 8px 0 16px",
            background: isOpen ? "linear-gradient(180deg, rgba(255,255,255,.12), rgba(255,255,255,.07))" : "linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,.04))",
            border: `1px solid ${isOpen ? "rgba(255,255,255,.17)" : isLast ? "rgba(255,255,255,.12)" : "rgba(255,255,255,.07)"}`,
            boxShadow: "0 3px 10px rgba(0,0,0,.25)",
            transform: `translateX(${dx}px)`, transition: drag ? "none" : "transform .38s cubic-bezier(.2,1.25,.4,1), background .3s, border-color .3s",
            touchAction: "pan-y", userSelect: "none", cursor: "pointer", overflow: "hidden",
          }}>
          <div style={{ flex: 1, minWidth: 0, display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ fontSize: 15, fontWeight: 700, color: "#E9EDF5", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{s.name}</span>
            {isOpen && <OpenTag s={s} />}
          </div>
          <div style={{ width: 84, fontSize: 12.5, fontWeight: 600, color: "#A3AEC2", display: "flex", gap: 5 }}>
            <Roll value={s.patches} /> <span>{s.patches === 1 ? "patch" : "patches"}</span>
          </div>
          {showDots && <div style={{ width: 126 }}><PatchDots n={s.patches} hex={p.hex} /></div>}
          <div style={{ width: 132, fontSize: 11.5, color: "#6F7A90" }}>Last used: {fmtTime(s.ts)}</div>
          <div style={{ width: 18, display: "grid", placeItems: "center", color: "#8F99AD", animation: pinPop === s.id ? "pop .45s" : "none" }}>
            {s.pinned && <Ico n="pin" size={14} />}
          </div>
          <button data-nosw onClick={(e) => { e.stopPropagation(); onMenu(e, s.id); }} className="btn"
            style={{ width: 32, height: 32, border: 0, background: "transparent", color: "#7B859A", borderRadius: 16, display: "grid", placeItems: "center", cursor: "pointer" }}>
            <Ico n="dots" size={17} />
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── page settings (opened by the gear) ── */
function Toggle({ on, onChange }) {
  return (
    <button role="switch" aria-checked={on} onClick={() => onChange(!on)} className="btn"
      style={{ width: 40, height: 22, borderRadius: 11, border: 0, padding: 0, position: "relative", cursor: "pointer", flex: "none", background: on ? "#8FB2E8" : "rgba(255,255,255,.14)", transition: "background .25s" }}>
      <span style={{ position: "absolute", top: 2, left: 2, width: 18, height: 18, borderRadius: 9, background: on ? "#0F1726" : "#C7CFDD", transform: `translateX(${on ? 18 : 0}px)`, transition: "transform .28s cubic-bezier(.3,1.4,.5,1), background .25s" }} />
    </button>
  );
}
function SRow({ label, k, st, set }) {
  return (
    <div style={{ display: "flex", alignItems: "center", height: 34, gap: 10 }}>
      <span style={{ flex: 1, fontSize: 13.5, fontWeight: 600, color: "#DCE2EE" }}>{label}</span>
      <Toggle on={st[k]} onChange={(v) => set(k, v)} />
    </div>
  );
}
function SLabel({ children }) {
  return <div style={{ fontSize: 10.5, letterSpacing: 1.3, fontWeight: 700, color: "#7B859A", height: 18 }}>{children}</div>;
}
function SettingsPanel({ st, set, onClose, onReset, onAll }) {
  const seg = [["tap", "Tap"], ["double", "Double tap"], ["long", "Hold"]];
  const idx = Math.max(0, seg.findIndex((x) => x[0] === st.openWith));
  const link = { height: 30, border: 0, background: "transparent", fontSize: 12.5, fontWeight: 700, cursor: "pointer", textAlign: "left", padding: 0 };
  return (
    <>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, zIndex: 44 }} />
      <div style={{ position: "absolute", right: 18, top: 60, width: 540, zIndex: 45, borderRadius: 18, padding: "14px 18px", background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 18px 44px rgba(0,0,0,.55)", animation: "menuIn .18s ease-out", transformOrigin: "top right" }}>
        <div style={{ display: "flex", alignItems: "center", height: 30, marginBottom: 8 }}>
          <div style={{ flex: 1, fontSize: 16, fontWeight: 800, color: "#F1F4FA" }}>Page settings</div>
          <button onClick={onClose} className="btn" style={{ width: 28, height: 28, border: 0, borderRadius: 14, background: "rgba(255,255,255,.06)", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={14} /></button>
        </div>
        <div style={{ display: "flex", gap: 28 }}>
          <div style={{ flex: 1 }}>
            <SLabel>DISPLAY</SLabel>
            <SRow label="Animated background" k="animatedBg" st={st} set={set} />
            <SRow label="Card auras" k="cardAuras" st={st} set={set} />
            <SRow label="Compact rows" k="compactRows" st={st} set={set} />
            <SRow label="Patch dots" k="patchDots" st={st} set={set} />
            <div style={{ height: 8 }} />
            <SLabel>SAFETY</SLabel>
            <SRow label="Lock deletion" k="lockDeletion" st={st} set={set} />
            <SRow label="Haptics" k="haptics" st={st} set={set} />
          </div>
          <div style={{ flex: 1 }}>
            <SLabel>BEHAVIOR</SLabel>
            <div style={{ fontSize: 13.5, fontWeight: 600, color: "#DCE2EE", margin: "6px 0 6px" }}>Open a setlist with</div>
            <div style={{ position: "relative", display: "flex", padding: 3, borderRadius: 12, background: "rgba(15,20,29,.8)", border: "1px solid rgba(255,255,255,.08)", marginBottom: 8 }}>
              <div style={{ position: "absolute", top: 3, bottom: 3, left: 3, width: "calc((100% - 6px) / 3)", borderRadius: 9, background: "linear-gradient(180deg,#38445E,#2B3650)", boxShadow: "0 2px 6px rgba(0,0,0,.4)", transform: `translateX(${idx * 100}%)`, transition: "transform .32s cubic-bezier(.3,1.35,.5,1)" }} />
              {seg.map(([k, l]) => (
                <button key={k} onClick={() => set("openWith", k)} style={{ position: "relative", flex: 1, height: 26, border: 0, background: "transparent", color: st.openWith === k ? "#F1F4FA" : "#8690A4", fontSize: 12, fontWeight: 700, cursor: "pointer", transition: "color .2s" }}>{l}</button>
              ))}
            </div>
            <SRow label="Pinned on top" k="pinnedOnTop" st={st} set={set} />
            <SRow label="Remember sort" k="rememberSort" st={st} set={set} />
            <div style={{ height: 8 }} />
            <button onClick={onReset} className="btn" style={{ ...link, display: "block", color: "#C7CFDD" }}>Reset page settings</button>
            <button onClick={onAll} className="btn" style={{ ...link, display: "block", color: "#8FB2E8" }}>All app settings</button>
          </div>
        </div>
      </div>
    </>
  );
}

/* ── create sheet (name + Duplicate / Blank) ── */
function Sheet({ items, openItem, onClose, onCreate }) {
  const KB = 132, SH = 246;
  const initial = useMemo(() => {
    let n = items.length + 1;
    while (items.some((s) => s.name === `Setlist ${n}`)) n++;
    return `Setlist ${n}`;
  }, []); // eslint-disable-line
  const [name, setName] = useState(initial);
  const [mode, setMode] = useState("blank");
  const [kbd, setKbd] = useState(true);
  const [shown, setShown] = useState(false);
  const [caps, setCaps] = useState(false);
  const inp = useRef(null);

  useEffect(() => {
    const r = requestAnimationFrame(() => setShown(true));
    const t = setTimeout(() => { if (inp.current) { inp.current.focus(); inp.current.select(); } }, 80);
    return () => { cancelAnimationFrame(r); clearTimeout(t); };
  }, []);

  const valid = name.trim().length > 0 && (mode === "blank" || !!openItem);
  const keep = (e) => e.preventDefault();
  const type = (ch) => setName((v) => (v.length >= 40 ? v : v + (caps ? ch.toUpperCase() : ch)) );
  const press = (ch) => { type(ch); if (caps) setCaps(false); };
  const done = () => { setKbd(false); if (inp.current) inp.current.blur(); };
  const top = !shown ? H : kbd ? H - KB - SH - 8 : (H - SH) / 2;

  const tile = ({ id, title, sub, icon, disabled }) => {
    const on = mode === id;
    return (
      <button key={id} onPointerDown={keep} onMouseDown={keep} disabled={disabled} onClick={() => !disabled && setMode(id)} className="btn"
        style={{
          flex: 1, textAlign: "left", display: "flex", gap: 12, alignItems: "center", padding: "12px 14px", borderRadius: 14, cursor: disabled ? "not-allowed" : "pointer",
          background: on ? "rgba(255,255,255,.08)" : "rgba(255,255,255,.04)", border: `1px solid ${on ? "rgba(143,178,232,.65)" : "rgba(255,255,255,.08)"}`,
          color: "#E9EDF5", opacity: disabled ? 0.45 : 1, transition: "background .2s, border-color .2s",
        }}>
        <span style={{ width: 34, height: 34, borderRadius: 10, display: "grid", placeItems: "center", background: on ? "#8FB2E8" : "rgba(255,255,255,.07)", color: on ? "#0F1726" : "#9AA5BA", transition: "background .2s" }}>
          <Ico n={icon} size={17} />
        </span>
        <span style={{ flex: 1, minWidth: 0 }}>
          <span style={{ display: "block", fontSize: 15, fontWeight: 700 }}>{title}</span>
          <span style={{ display: "block", fontSize: 11.5, color: "#8690A4", marginTop: 2, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{sub}</span>
        </span>
        <span style={{ width: 20, height: 20, borderRadius: 10, display: "grid", placeItems: "center", background: on ? "#8FB2E8" : "transparent", border: on ? "none" : "1.5px solid rgba(255,255,255,.2)", color: "#0F1726", transition: "all .2s" }}>
          {on && <Ico n="check" size={13} sw={3} />}
        </span>
      </button>
    );
  };

  const rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 40 }}>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.62)", backdropFilter: "blur(3px)", opacity: shown ? 1 : 0, transition: "opacity .3s" }} />
      <div style={{
        position: "absolute", left: "50%", top, width: 600, height: SH, marginLeft: -300, borderRadius: 22, padding: "16px 20px",
        background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 24px 60px rgba(0,0,0,.55)",
        transition: "top .38s cubic-bezier(.2,.9,.25,1)", display: "flex", flexDirection: "column", gap: 12,
      }}>
        <div style={{ display: "flex", alignItems: "center" }}>
          <div style={{ flex: 1, fontSize: 19, fontWeight: 800, color: "#F1F4FA" }}>New setlist</div>
          <button onClick={onClose} className="btn" style={{ width: 30, height: 30, border: 0, borderRadius: 15, background: "rgba(255,255,255,.06)", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={15} /></button>
        </div>
        <div>
          <div style={{ display: "flex", fontSize: 10, letterSpacing: 1.2, fontWeight: 700, color: "#7B859A", marginBottom: 6 }}>
            <span style={{ flex: 1 }}>NAME</span><span>{name.length}/40</span>
          </div>
          <input ref={inp} value={name} maxLength={40} placeholder="Setlist name"
            onChange={(e) => setName(e.target.value)} onFocus={() => setKbd(true)}
            onKeyDown={(e) => { if (e.key === "Enter" && valid) onCreate({ name, mode }); }}
            style={{ width: "100%", height: 44, borderRadius: 12, border: "1px solid rgba(143,178,232,.4)", background: "#0F141D", color: "#F1F4FA", fontSize: 16, fontWeight: 600, padding: "0 14px", outline: "none", caretColor: "#D8A24A", boxShadow: "inset 0 2px 6px rgba(0,0,0,.5)" }} />
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          {tile({ id: "duplicate", title: "Duplicate", icon: "copy", disabled: !openItem, sub: openItem ? `Copy of ${openItem.name}` : "No setlist open" })}
          {tile({ id: "blank", title: "Blank", icon: "plus", sub: "Start with no patches" })}
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: "auto" }}>
          <button onPointerDown={keep} onMouseDown={keep} onClick={onClose} className="btn"
            style={{ height: 38, padding: "0 20px", borderRadius: 12, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.04)", color: "#C7CFDD", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>Cancel</button>
          <button onPointerDown={keep} onMouseDown={keep} disabled={!valid} onClick={() => valid && onCreate({ name, mode })} className="btn"
            style={{ height: 38, padding: "0 26px", borderRadius: 12, border: 0, background: valid ? "linear-gradient(180deg,#9DBDEB,#7FA3DD)" : "rgba(143,178,232,.2)", color: valid ? "#0F1726" : "rgba(255,255,255,.35)", fontSize: 14, fontWeight: 800, cursor: valid ? "pointer" : "not-allowed", boxShadow: valid ? "0 4px 12px rgba(0,0,0,.3)" : "none" }}>Create</button>
        </div>
      </div>
      {/* on-screen keyboard (visual stand-in) */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: KB, background: "#0E131B", borderTop: "1px solid rgba(255,255,255,.07)", padding: "8px 70px", display: "flex", flexDirection: "column", gap: 6, transform: `translateY(${kbd && shown ? 0 : KB}px)`, transition: "transform .35s cubic-bezier(.2,.9,.25,1)" }}>
        {rows.map((r, ri) => (
          <div key={ri} style={{ display: "flex", justifyContent: "center", gap: 5 }}>
            {ri === 2 && <Key w={44} onClick={() => setCaps((c) => !c)} active={caps}><Ico n="shift" size={14} /></Key>}
            {r.split("").map((ch) => <Key key={ch} onClick={() => press(ch)}>{caps ? ch.toUpperCase() : ch}</Key>)}
            {ri === 2 && <Key w={44} onClick={() => setName((v) => v.slice(0, -1))}><Ico n="backspace" size={15} /></Key>}
          </div>
        ))}
        <div style={{ display: "flex", justifyContent: "center", gap: 5 }}>
          <Key w={56} onClick={() => {}}>?123</Key>
          <Key w={300} onClick={() => type(" ")}>&nbsp;</Key>
          <Key w={72} onClick={done} accent>Done</Key>
        </div>
      </div>
    </div>
  );
}
function Key({ children, onClick, w = 36, accent, active }) {
  return (
    <button onPointerDown={(e) => e.preventDefault()} onMouseDown={(e) => e.preventDefault()} onClick={onClick}
      style={{ width: w, height: 26, border: 0, borderRadius: 7, background: accent ? "#8FB2E8" : active ? "#3A465F" : "#232B3A", color: accent ? "#0F1726" : "#D6DDEA", fontSize: 12.5, fontWeight: accent ? 800 : 500, display: "grid", placeItems: "center", cursor: "pointer", boxShadow: "0 1px 0 rgba(0,0,0,.5)" }}>
      {children}
    </button>
  );
}

function Sec({ children, right }) {
  return (
    <div style={{ display: "flex", alignItems: "center", margin: "16px 0 9px", minHeight: 24 }}>
      <div style={{ flex: 1, fontSize: 11.5, letterSpacing: 1.3, textTransform: "uppercase", fontWeight: 700, color: "#8690A4" }}>{children}</div>
      {right}
    </div>
  );
}

/* ═══════════════ MAIN ═══════════════ */
export default function SetlistsPreview() {
  const [items, setItems] = useState(seed);
  const [openId, setOpenId] = useState(null);
  const [lastId, setLastId] = useState(2);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("recent");
  const [onlyPinned, setOnlyPinned] = useState(false);
  const [st, setSt] = useState(DEFAULTS);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const lastTap = useRef({ id: null, t: 0 });
  const lockHint = useRef(0);
  HAP.on = st.haptics;
  const setPref = (k, v) => { setSt((o) => ({ ...o, [k]: v })); vib(6); };
  const [loading, setLoading] = useState(true);
  const [menu, setMenu] = useState(null);
  const [sheet, setSheet] = useState(false);
  const [snack, setSnack] = useState(null);
  const [removing, setRemoving] = useState([]);
  const [pinPop, setPinPop] = useState(null);
  const [renameVal, setRenameVal] = useState("");
  const [reduced, setReduced] = useState(() => {
    try { return window.matchMedia("(prefers-reduced-motion: reduce)").matches; } catch (e) { return false; }
  });
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });

  const stageRef = useRef(null);
  const scaleRef = useRef(1);
  const scrollRef = useRef(0);
  const snackT = useRef(null);
  const undoRef = useRef(null);

  useEffect(() => {
    const on = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);
  useEffect(() => { const t = setTimeout(() => setLoading(false), 750); return () => clearTimeout(t); }, []);

  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 104) / H, 1.5));
  scaleRef.current = scale;
  const portrait = vp.h > vp.w;

  const showSnack = (s, ms = 1900) => {
    clearTimeout(snackT.current);
    setSnack({ ...s, key: Date.now(), ms });
    snackT.current = setTimeout(() => setSnack(null), ms);
  };

  /* actions */
  const openSetlist = (id) => {
    const s = items.find((x) => x.id === id);
    if (!s) return;
    vib(10);
    setMenu(null);
    setOpenId(id); setLastId(id);
    setItems((it) => it.map((x) => (x.id === id ? { ...x, ts: Date.now() } : x)));
    showSnack({ text: `“${s.name}” loaded · back to the mixer` });
  };
  const deleteSetlist = (id) => {
    const idx = items.findIndex((s) => s.id === id);
    if (idx < 0) return;
    undoRef.current = { item: items[idx], idx };
    setMenu(null);
    setRemoving((r) => [...r, id]);
    showSnack({ text: "Setlist deleted", undo: true }, 5000);
    setTimeout(() => {
      setItems((it) => it.filter((s) => s.id !== id));
      setRemoving((r) => r.filter((x) => x !== id));
      setOpenId((o) => (o === id ? null : o));
      setLastId((l) => (l === id ? null : l));
    }, 240);
  };
  const undo = () => {
    const u = undoRef.current;
    if (!u) return;
    setItems((it) => { const c = [...it]; c.splice(Math.min(u.idx, c.length), 0, u.item); return c; });
    undoRef.current = null;
    clearTimeout(snackT.current); setSnack(null); vib(8);
  };
  const togglePin = (id) => {
    setItems((it) => it.map((s) => (s.id === id ? { ...s, pinned: !s.pinned } : s)));
    setPinPop(id); setTimeout(() => setPinPop(null), 500);
    setMenu(null); vib(8);
  };
  const setColor = (id, color) => { setItems((it) => it.map((s) => (s.id === id ? { ...s, color } : s))); setMenu(null); vib(6); };
  const rename = (id, name) => { if (!name.trim()) return; setItems((it) => it.map((s) => (s.id === id ? { ...s, name: name.trim() } : s))); setMenu(null); };
  const duplicate = (id) => {
    const s = items.find((x) => x.id === id);
    if (!s) return;
    setItems((it) => [{ ...s, id: Date.now(), name: `${s.name} copy`, color: autoColor(it, s.color), pinned: false, ts: Date.now() }, ...it]);
    setMenu(null); vib(8); showSnack({ text: `“${s.name} copy” created` });
  };
  const create = ({ name, mode }) => {
    const base = mode === "duplicate" ? items.find((s) => s.id === openId) : null;
    const color = base ? autoColor(items, base.color) : autoColor(items);
    setItems((it) => [{ id: Date.now(), name: name.trim(), patches: base ? base.patches : 0, color, pinned: false, ts: Date.now() }, ...it]);
    setSheet(false); vib(10); showSnack({ text: `“${name.trim()}” created` });
  };
  const openMenu = (e, id) => {
    const r = e.currentTarget.getBoundingClientRect();
    const sr = stageRef.current.getBoundingClientRect();
    const sc = scaleRef.current;
    const s = items.find((x) => x.id === id);
    setRenameVal(s ? s.name : "");
    setMenu({ id, x: (r.right - sr.left) / sc, y: (r.bottom - sr.top) / sc, mode: "main" });
    vib(6);
  };
  const resetDemo = () => {
    setItems(seed()); setOpenId(null); setLastId(2); setQuery(""); setOnlyPinned(false); if (!st.rememberSort) setSort("recent");
    setMenu(null); setSheet(false); setSnack(null); setRemoving([]);
    setLoading(true); setTimeout(() => setLoading(false), 750);
  };
  const emptyDemo = () => {
    setItems([]); setOpenId(null); setLastId(null); setQuery(""); setMenu(null); setSheet(false); setSnack(null);
  };

  const onTapItem = (id) => {
    if (st.openWith === "tap") return openSetlist(id);
    if (st.openWith === "double") {
      const now = Date.now();
      if (lastTap.current.id === id && now - lastTap.current.t < 400) { lastTap.current = { id: null, t: 0 }; return openSetlist(id); }
      lastTap.current = { id, t: now };
      return showSnack({ text: "Double tap to open" }, 1100);
    }
    showSnack({ text: "Press and hold to open" }, 1100);
  };
  const onLongItem = (id) => { if (st.openWith === "long") openSetlist(id); };
  const onLocked = () => {
    const n = Date.now();
    if (n - lockHint.current > 1500) { lockHint.current = n; showSnack({ text: "Deletion is locked" }, 1300); }
  };

  /* derived */
  const q = query.trim().toLowerCase();
  const byTs = [...items].sort((a, b) => b.ts - a.ts);
  const topCards = byTs.slice(0, 4); // fixed: not affected by search or sort
  const sortFn = { recent: (a, b) => b.ts - a.ts, name: (a, b) => a.name.localeCompare(b.name), patches: (a, b) => b.patches - a.patches }[sort];
  const list = items
    .filter((s) => (!q || s.name.toLowerCase().includes(q)) && (!onlyPinned || s.pinned))
    .sort((a, b) => (st.pinnedOnTop ? b.pinned - a.pinned : 0) || sortFn(a, b)); // pinned stay on top of the list
  const totalPatches = items.reduce((n, s) => n + s.patches, 0);
  const openItem = items.find((s) => s.id === openId) || null;
  const tint = openItem ? palOf(openItem.color).hex : null;

  const rowH = st.compactRows ? 38 : 44;
  const ROW = rowH + 4;
  let c = 0;
  const pos = {};
  list.forEach((s) => { pos[s.id] = c * ROW; if (!removing.includes(s.id)) c++; });
  const listH = Math.max(c, 0) * ROW;

  const menuItem = menu ? items.find((s) => s.id === menu.id) : null;
  const menuTop = menu ? (menu.y + 6 + 200 > H ? Math.max(8, menu.y - 200 - 34) : menu.y + 6) : 0;
  const menuLeft = menu ? Math.min(Math.max(8, menu.x - 188), W - 196) : 0;

  const sortIdx = ["recent", "name", "patches"].indexOf(sort);
  const SEG = 66;
  const isEmpty = !loading && items.length === 0;

  return (
    <div className="sl" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>

      {/* device frame */}
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: "0 0 0 " + Math.max(2, 3 * scale) + "px #1B212C, 0 24px 60px rgba(0,0,0,.6)", overflow: "hidden", position: "relative", flex: "none" }}>
        <div ref={stageRef} style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
          <Halos tint={tint} scrollRef={scrollRef} reduced={reduced || !st.animatedBg} />

          {/* top bar */}
          <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 56, zIndex: 5, display: "flex", alignItems: "center", padding: "0 18px", gap: 10 }}>
            <button className="btn" onClick={() => showSnack({ text: "Cancel · back to the previous screen" })}
              style={{ height: 38, padding: "0 18px", borderRadius: 13, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.045)", color: "#D3DAE8", fontSize: 14.5, fontWeight: 700, cursor: "pointer" }}>Cancel</button>
            <div style={{ width: 230, height: 38, borderRadius: 13, border: "1px solid rgba(255,255,255,.1)", background: "rgba(15,20,29,.7)", display: "flex", alignItems: "center", gap: 8, padding: "0 12px", color: "#8690A4", opacity: isEmpty ? 0.45 : 1, transition: "opacity .3s" }}>
              <Ico n="search" size={16} />
              <input value={query} disabled={isEmpty} onChange={(e) => setQuery(e.target.value)} placeholder="Search setlists"
                style={{ flex: 1, minWidth: 0, background: "transparent", border: 0, outline: "none", color: "#E9EDF5", fontSize: 14 }} />
              {query && <button onClick={() => setQuery("")} className="btn" style={{ border: 0, background: "transparent", color: "#8690A4", display: "grid", placeItems: "center", cursor: "pointer", padding: 0 }}><Ico n="x" size={14} /></button>}
            </div>
            <div style={{ position: "absolute", left: "50%", top: 0, bottom: 0, transform: "translateX(-50%)", display: "flex", flexDirection: "column", justifyContent: "center", alignItems: "center", pointerEvents: "none" }}>
              <div style={{ fontSize: 20, fontWeight: 800, letterSpacing: 0.2, color: "#F4F6FB" }}>Setlists</div>
              <div style={{ fontSize: 10, color: "#6F7A90", marginTop: 1, whiteSpace: "nowrap" }}>
                <Roll value={items.length} /> {items.length === 1 ? "setlist" : "setlists"} · <Roll value={totalPatches} /> {totalPatches === 1 ? "patch" : "patches"}
              </div>
            </div>
            <div style={{ flex: 1 }} />
            <button className="btn" onClick={() => setSheet(true)}
              style={{ height: 38, padding: "0 16px 0 12px", borderRadius: 13, border: "1px solid rgba(255,255,255,.2)", background: "linear-gradient(180deg,#9DBDEB,#7FA3DD)", color: "#0F1726", fontSize: 14, fontWeight: 800, display: "flex", alignItems: "center", gap: 7, cursor: "pointer", boxShadow: "0 4px 12px rgba(0,0,0,.3)" }}>
              <Ico n="plus" size={16} sw={2.6} /> Create New Setlist
            </button>
            <button className="btn" onClick={() => { setMenu(null); setSettingsOpen((v) => !v); }} aria-label="Page settings"
              style={{ width: 38, height: 38, borderRadius: 13, border: `1px solid ${settingsOpen ? "rgba(255,255,255,.28)" : "rgba(255,255,255,.12)"}`, background: settingsOpen ? "rgba(255,255,255,.12)" : "rgba(255,255,255,.045)", color: "#D3DAE8", display: "grid", placeItems: "center", cursor: "pointer", transition: "background .25s, border-color .25s" }}>
              <Ico n="gear" size={18} />
            </button>
          </div>
          <div style={{ position: "absolute", left: 18, right: 18, top: 56, height: 1, background: "rgba(255,255,255,.07)", zIndex: 5 }} />

          {/* body: fixed header zone + scrolling list */}
          <div style={{ position: "absolute", left: 0, right: 0, top: 57, bottom: 0, zIndex: 2, display: "flex", flexDirection: "column" }}>
            {!isEmpty && (
              <div style={{ flex: "none", padding: "9px 18px 0" }}>
                <div style={{ fontSize: 10.5, letterSpacing: 1.3, textTransform: "uppercase", fontWeight: 700, color: "#7B859A", height: 14, marginBottom: 6 }}>Recently used</div>
                <div style={{ display: "flex", gap: 12, height: 96 }}>
                  {loading
                    ? [0, 1, 2, 3].map((i) => <div key={i} className="skel" style={{ flex: "0 0 calc(25% - 9px)", height: 96, borderRadius: 16 }} />)
                    : topCards.map((s, i) => <Card key={s.id} s={s} i={i} pinPop={pinPop} reduced={reduced || !st.animatedBg} aura={st.cardAuras} longOpen={st.openWith === "long"} onLong={onLongItem} isOpen={s.id === openId} isLast={s.id === lastId} onOpen={onTapItem} onMenu={openMenu} />)}
                </div>
                <div style={{ display: "flex", alignItems: "center", height: 30, marginTop: 9, marginBottom: 3 }}>
                  <div style={{ flex: 1, fontSize: 10.5, letterSpacing: 1.3, textTransform: "uppercase", fontWeight: 700, color: "#7B859A" }}>All setlists</div>
                  <button onClick={() => setOnlyPinned((v) => !v)} className="btn"
                    style={{ height: 26, padding: "0 11px 0 9px", marginRight: 14, borderRadius: 13, display: "flex", alignItems: "center", gap: 6, cursor: "pointer", fontSize: 12, fontWeight: 700,
                      border: `1px solid ${onlyPinned ? "rgba(255,255,255,.24)" : "rgba(255,255,255,.09)"}`, background: onlyPinned ? "rgba(255,255,255,.12)" : "transparent", color: onlyPinned ? "#E9EDF5" : "#8690A4", transition: "all .25s" }}>
                    <Ico n="pin" size={12} /> Pinned
                  </button>
                  <span style={{ fontSize: 12, color: "#6F7A90", marginRight: 10 }}>Sort by</span>
                  <div style={{ position: "relative", display: "flex", padding: 3, borderRadius: 12, background: "rgba(15,20,29,.8)", border: "1px solid rgba(255,255,255,.08)" }}>
                    <div style={{ position: "absolute", top: 3, left: 3, width: SEG, height: 22, borderRadius: 9, background: "linear-gradient(180deg,#38445E,#2B3650)", boxShadow: "0 2px 6px rgba(0,0,0,.4)", transform: `translateX(${sortIdx * SEG}px)`, transition: "transform .32s cubic-bezier(.3,1.35,.5,1)" }} />
                    {[["recent", "Recent"], ["name", "Name"], ["patches", "Patches"]].map(([k, l]) => (
                      <button key={k} onClick={() => setSort(k)} style={{ position: "relative", width: SEG, height: 22, border: 0, background: "transparent", color: sort === k ? "#F1F4FA" : "#8690A4", fontSize: 11.5, fontWeight: 700, cursor: "pointer", transition: "color .2s" }}>{l}</button>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* only this area scrolls */}
            <div className="noscroll" onScroll={(e) => (scrollRef.current = e.currentTarget.scrollTop)}
              style={{ flex: 1, minHeight: 0, overflowY: "auto", overflowX: "hidden", padding: "0 18px 16px" }}>
              {loading && [0, 1, 2, 3].map((i) => <div key={i} className="skel" style={{ height: rowH, borderRadius: 13, marginBottom: 4 }} />)}

              {isEmpty && (
                <div style={{ minHeight: 330, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 6 }}>
                  <div style={{ width: 204, height: 76, borderRadius: 16, border: "2px dashed rgba(255,255,255,.22)", background: "rgba(255,255,255,.025)", animation: "breathe 3s ease-in-out infinite", display: "grid", placeItems: "center", color: "rgba(255,255,255,.4)", marginBottom: 10 }}>
                    <Ico n="plus" size={24} />
                  </div>
                  <div style={{ fontSize: 19, fontWeight: 800, color: "#F1F4FA" }}>Your first setlist starts here</div>
                  <div style={{ fontSize: 13, color: "#8690A4", marginBottom: 10 }}>Group patches for a show.</div>
                  <button className="btn" onClick={() => setSheet(true)}
                    style={{ height: 40, padding: "0 22px", borderRadius: 13, border: "1px solid rgba(255,255,255,.2)", background: "linear-gradient(180deg,#9DBDEB,#7FA3DD)", color: "#0F1726", fontSize: 14, fontWeight: 800, cursor: "pointer", boxShadow: "0 4px 12px rgba(0,0,0,.3)" }}>Create setlist</button>
                </div>
              )}

              {!loading && !isEmpty && (
                <>
                  <div style={{ position: "relative", height: listH, transition: "height .38s cubic-bezier(.2,.9,.25,1)" }}>
                    {list.map((s, i) => (
                      <SwipeRow key={s.id} s={s} y={pos[s.id]} i={i} h={rowH} showDots={st.patchDots} locked={st.lockDeletion} onLocked={onLocked} longOpen={st.openWith === "long"} onLong={onLongItem} removing={removing.includes(s.id)} pinPop={pinPop}
                        isOpen={s.id === openId} isLast={s.id === lastId} scaleRef={scaleRef}
                        onOpen={onTapItem} onMenu={openMenu} onDelete={deleteSetlist} />
                    ))}
                  </div>
                  {list.length === 0 && (
                    <div style={{ padding: "18px 4px", fontSize: 13.5, color: "#7B859A" }}>
                      {onlyPinned && !q ? "No pinned setlists yet." : `No setlist matches “${query}”.`}
                    </div>
                  )}
                </>
              )}
            </div>
          </div>

          {/* ⋮ menu */}
          {menu && menuItem && (
            <>
              <div onClick={() => setMenu(null)} style={{ position: "absolute", inset: 0, zIndex: 29 }} />
              <div style={{ position: "absolute", left: menuLeft, top: menuTop, width: 188, zIndex: 30, borderRadius: 16, padding: 6, background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 16px 40px rgba(0,0,0,.55)", animation: "menuIn .16s ease-out", transformOrigin: "top right" }}>
                {menu.mode === "main" && (
                  <>
                    {[
                      [menuItem.pinned ? "Unpin" : "Pin to top", "pin", () => togglePin(menu.id)],
                      ["Rename", "edit", () => setMenu({ ...menu, mode: "rename" })],
                      ["Change color", "swatch", () => setMenu({ ...menu, mode: "color" })],
                      ["Duplicate", "copy", () => duplicate(menu.id)],
                    ].map(([l, ic, fn]) => (
                      <button key={l} onClick={fn} className="btn" style={{ width: "100%", height: 36, display: "flex", alignItems: "center", gap: 10, padding: "0 10px", border: 0, borderRadius: 10, background: "transparent", color: "#DCE2EE", fontSize: 13.5, fontWeight: 600, cursor: "pointer", textAlign: "left" }}><Ico n={ic} size={16} />{l}</button>
                    ))}
                    <div style={{ height: 1, background: "rgba(255,255,255,.07)", margin: "4px 6px" }} />
                    <button onClick={() => (st.lockDeletion ? onLocked() : deleteSetlist(menu.id))} className="btn" style={{ width: "100%", height: 36, display: "flex", alignItems: "center", gap: 10, padding: "0 10px", border: 0, borderRadius: 10, background: "transparent", color: st.lockDeletion ? "#6F7A90" : "#E8708F", fontSize: 13.5, fontWeight: 700, cursor: "pointer", textAlign: "left" }}><Ico n={st.lockDeletion ? "lock" : "trash"} size={16} />Delete</button>
                  </>
                )}
                {menu.mode === "color" && (
                  <div style={{ padding: "8px 8px 10px" }}>
                    <div style={{ fontSize: 10.5, letterSpacing: 1.2, fontWeight: 700, color: "#7B859A", marginBottom: 10 }}>COLOR</div>
                    <div style={{ display: "flex", gap: 10, justifyContent: "space-between" }}>
                      {PAL.map((p) => (
                        <button key={p.id} onClick={() => setColor(menu.id, p.id)} className="btn"
                          style={{ width: 34, height: 34, borderRadius: 17, border: menuItem.color === p.id ? "2px solid #fff" : "2px solid transparent", background: p.hex, display: "grid", placeItems: "center", color: "#0F1726", cursor: "pointer", boxShadow: "0 2px 6px rgba(0,0,0,.35)" }}>
                          {menuItem.color === p.id && <Ico n="check" size={15} sw={3} />}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                {menu.mode === "rename" && (
                  <div style={{ padding: "8px" }}>
                    <div style={{ fontSize: 10.5, letterSpacing: 1.2, fontWeight: 700, color: "#7B859A", marginBottom: 8 }}>RENAME</div>
                    <input autoFocus value={renameVal} maxLength={40} onChange={(e) => setRenameVal(e.target.value)} onKeyDown={(e) => e.key === "Enter" && rename(menu.id, renameVal)}
                      style={{ width: "100%", height: 36, borderRadius: 10, border: "1px solid rgba(143,178,232,.5)", background: "#0F141D", color: "#F1F4FA", fontSize: 14, padding: "0 10px", outline: "none" }} />
                    <button onClick={() => rename(menu.id, renameVal)} className="btn" style={{ width: "100%", height: 34, marginTop: 8, border: 0, borderRadius: 10, background: "#8FB2E8", color: "#0F1726", fontWeight: 800, fontSize: 13, cursor: "pointer" }}>Save</button>
                  </div>
                )}
              </div>
            </>
          )}

          {settingsOpen && (
            <SettingsPanel st={st} set={setPref} onClose={() => setSettingsOpen(false)}
              onReset={() => { setSt(DEFAULTS); vib(8); }}
              onAll={() => { setSettingsOpen(false); showSnack({ text: "Opens the global app settings" }); }} />
          )}

          {sheet && <Sheet items={items} openItem={openItem} onClose={() => setSheet(false)} onCreate={create} />}

          {/* snackbar */}
          {snack && (
            <div key={snack.key} style={{ position: "absolute", left: "50%", bottom: 16, transform: "translateX(-50%)", zIndex: 60, animation: "snackIn .3s cubic-bezier(.2,.9,.25,1)" }}>
              <div style={{ position: "relative", overflow: "hidden", display: "flex", alignItems: "center", gap: 18, minWidth: 260, padding: "11px 16px", borderRadius: 14, background: "#222C40", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 12px 30px rgba(0,0,0,.5)", fontSize: 13.5, fontWeight: 600, color: "#E9EDF5" }}>
                <span style={{ flex: 1 }}>{snack.text}</span>
                {snack.undo && <button onClick={undo} className="btn" style={{ border: 0, background: "transparent", color: "#8FB2E8", fontWeight: 800, fontSize: 13, letterSpacing: 1, cursor: "pointer" }}>UNDO</button>}
                {snack.undo && <div style={{ position: "absolute", left: 0, bottom: 0, height: 3, background: "#8FB2E8", animation: `shrink ${snack.ms}ms linear forwards` }} />}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* preview helpers (outside the device) */}
      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", alignItems: "center", maxWidth: 760 }}>
        <span style={{ display: "inline-flex", alignItems: "center", gap: 8, padding: "7px 12px", borderRadius: 999, background: "#121823", border: "1px solid #232C3B", fontSize: 12, color: "#B6C0D2" }}>
          <span style={{ width: 8, height: 8, borderRadius: 4, background: openId ? "#7DD3A0" : "#566075", transition: "background .3s" }} />
          Global Back: {openId ? "enabled (a setlist is open)" : "disabled (no setlist open)"}
        </span>
        {[["Reset demo", resetDemo], ["Empty state", emptyDemo], [reduced ? "Motion: off" : "Motion: on", () => setReduced((r) => !r)]].map(([l, fn]) => (
          <button key={l} onClick={fn} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>{l}</button>
        ))}
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 560, lineHeight: 1.5 }}>
        Tap a card to open · swipe a row left to delete · ⋮ for pin, rename, color · Create New Setlist for the sheet
        {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
      </div>
    </div>
  );
}
