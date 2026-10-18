import { useState, useRef, useEffect, useMemo, createContext, useContext } from "react";

/* ─────────────────────────────────────────────────────────────
   SETTINGS + MIDI MAPPING — landscape smartphone preview (891 × 411 dp)
   Everything inside the device frame is the real design, in English.
   Controls under the frame are preview helpers only.
   The mapping pages are SIMULATED here (the real app will render its real components).
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";

const DEF = {
  buffer: 256, poly: 128, headroom: 1.0,
  autoFit: false, hue: 220, accSat: 1.0, bgTone: 1.6, bgLight: 1.0,
  jitter: true, jitterMs: 15,
  notation: "sharps", keepOn: true,
};

const Ctx = createContext({ c: "#8FB2E8", a: (x) => `rgba(143,178,232,${x})` });
const useAc = () => useContext(Ctx);

const CSS = `
.st *{box-sizing:border-box}
.st button{font-family:inherit}
@keyframes rise{from{opacity:0;translate:0 10px}to{opacity:1;translate:0 0}}
.rise{animation:rise .4s cubic-bezier(.2,.8,.2,1) backwards}
@keyframes menuIn{from{opacity:0;scale:.95}to{opacity:1;scale:1}}
@keyframes snackIn{from{opacity:0;translate:0 14px}to{opacity:1;translate:0 0}}
@keyframes fadeIn{from{opacity:0}to{opacity:1}}
@keyframes frameIn{from{opacity:0;scale:.97}to{opacity:1;scale:1}}
@keyframes chipIn{from{opacity:0;transform:scale(.55)}to{opacity:1;transform:scale(1)}}
@keyframes chipOut{from{opacity:1;transform:scale(1)}to{opacity:0;transform:scale(.5)}}
@keyframes breathe{0%,100%{scale:1}50%{scale:1.06}}
@keyframes ring{0%{box-shadow:0 0 0 0 var(--ring)}100%{box-shadow:0 0 0 9px transparent}}
@keyframes shrink{from{width:100%}to{width:0}}
@keyframes dim{from{opacity:0}to{opacity:1}}
.press{transition:transform .2s cubic-bezier(.3,1.5,.5,1),background .25s,opacity .25s}
.press:active{transform:scale(.985)}
.btn{transition:transform .15s cubic-bezier(.3,1.5,.5,1),filter .2s,background .2s}
.btn:active{transform:scale(.96)}
.btn:hover{filter:brightness(1.08)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

/* ── icons ── */
function Ico({ n, size = 18, color = "currentColor", sw = 2 }) {
  const P = {
    back: <path d="M19 12H5M11 6l-6 6 6 6" />,
    chev: <path d="M9 6l6 6-6 6" />,
    search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
    x: <path d="M6 6l12 12M18 6L6 18" />,
    plus: <path d="M12 5v14M5 12h14" />,
    check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
    reset: <><path d="M20 12a8 8 0 1 1-2.6-5.9" /><path d="M20 4v4.5h-4.5" /></>,
    wave: <path d="M3 12h2M7 8v8M11 4v16M15 8v8M19 10v4M21 12h0" />,
    swatch: <><circle cx="12" cy="12" r="8.5" /><circle cx="8.5" cy="10" r="1" /><circle cx="13" cy="8.5" r="1" /><circle cx="15.5" cy="13" r="1" /></>,
    midi: <><circle cx="12" cy="12" r="9" /><circle cx="7" cy="12" r="1" /><circle cx="9" cy="7.5" r="1" /><circle cx="12" cy="6.5" r="1" /><circle cx="15" cy="7.5" r="1" /><circle cx="17" cy="12" r="1" /></>,
    gear: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></>,
    trash: <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" />,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">
      {P[n]}
    </svg>
  );
}

/* ═══════════════ shared primitives (same language as the Setlists screen) ═══════════════ */

function Card({ children, style }) {
  return (
    <div style={{
      borderRadius: 16, background: "linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,.035))",
      border: "1px solid rgba(255,255,255,.07)", boxShadow: "0 4px 12px rgba(0,0,0,.28)", ...style,
    }}>{children}</div>
  );
}
const Divider = () => <div style={{ height: 1, background: "rgba(255,255,255,.06)", margin: "0 14px" }} />;

function SecLabel({ children, right }) {
  return (
    <div style={{ display: "flex", alignItems: "center", height: 22, margin: "14px 2px 7px" }}>
      <div style={{ flex: 1, fontSize: 10.5, letterSpacing: 1.3, textTransform: "uppercase", fontWeight: 700, color: "#7B859A" }}>{children}</div>
      {right}
    </div>
  );
}

function R({ i = 0, children }) {
  return <div className="rise" style={{ animationDelay: `${i * 45}ms` }}>{children}</div>;
}

function Row({ title, hint, children, h = 58, split = 1, dim, onClick, chevron }) {
  return (
    <div onClick={onClick} className={onClick ? "press" : ""}
      style={{ display: "flex", alignItems: "center", gap: 16, minHeight: h, padding: "9px 14px", opacity: dim ? 0.45 : 1, transition: "opacity .25s", cursor: onClick ? "pointer" : "default" }}>
      <div style={{ flex: split, minWidth: 0 }}>
        <div style={{ fontSize: 14.5, fontWeight: 700, color: "#E9EDF5" }}>{title}</div>
        {hint && <div style={{ fontSize: 11.5, color: "#7B859A", marginTop: 2, lineHeight: 1.35 }}>{hint}</div>}
      </div>
      {children !== undefined && (
        <div style={{ flex: 1.3, minWidth: 0, display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 10 }}>{children}</div>
      )}
      {chevron && <span style={{ color: "#7B859A", display: "inline-flex" }}><Ico n="chev" size={16} /></span>}
    </div>
  );
}

function Switch({ on, onChange, disabled }) {
  const ac = useAc();
  return (
    <button role="switch" aria-checked={on} disabled={disabled} onClick={() => onChange(!on)} className="btn"
      style={{ width: 40, height: 22, borderRadius: 11, border: 0, padding: 0, position: "relative", cursor: disabled ? "not-allowed" : "pointer", flex: "none", background: on ? ac.c : "rgba(255,255,255,.14)", transition: "background .25s" }}>
      <span style={{ position: "absolute", top: 2, left: 2, width: 18, height: 18, borderRadius: 9, background: on ? "#0F1726" : "#C7CFDD", transform: `translateX(${on ? 18 : 0}px)`, transition: "transform .28s cubic-bezier(.3,1.4,.5,1), background .25s" }} />
    </button>
  );
}

function Seg({ options, value, onChange, tall }) {
  const ac = useAc();
  const n = options.length;
  const idx = Math.max(0, options.findIndex((o) => o.v === value));
  return (
    <div style={{ position: "relative", display: "flex", width: "100%", padding: 3, borderRadius: 13, background: "rgba(15,20,29,.8)", border: "1px solid rgba(255,255,255,.08)" }}>
      <div style={{ position: "absolute", top: 3, bottom: 3, left: 3, width: `calc((100% - 6px) / ${n})`, borderRadius: 10, background: "linear-gradient(180deg,#38445E,#2B3650)", boxShadow: "0 2px 6px rgba(0,0,0,.4)", transform: `translateX(${idx * 100}%)`, transition: "transform .32s cubic-bezier(.3,1.35,.5,1)" }} />
      {options.map((o) => {
        const on = o.v === value;
        return (
          <button key={String(o.v)} onClick={() => onChange(o.v)}
            style={{ position: "relative", flex: 1, height: tall ? 44 : 28, border: 0, background: "transparent", cursor: "pointer", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 1, color: on ? ac.c : "#8690A4", transition: "color .2s" }}>
            <span style={{ fontSize: 13.5, fontWeight: 800 }}>{o.label}</span>
            {o.sub && <span style={{ fontSize: 9.5, fontWeight: 600, color: on ? "#AEB8CA" : "#5F6A80", transition: "color .2s" }}>{o.sub}</span>}
          </button>
        );
      })}
    </div>
  );
}

function Slider({ value, min, max, step, onChange, disabled }) {
  const ac = useAc();
  const ref = useRef(null);
  const drag = useRef(false);
  const t = clamp((value - min) / (max - min), 0, 1);
  const fromX = (cx) => {
    const r = ref.current.getBoundingClientRect();
    const f = clamp((cx - r.left) / r.width, 0, 1);
    const v = Math.round((min + f * (max - min)) / step) * step;
    onChange(+v.toFixed(3));
  };
  return (
    <div ref={ref}
      onPointerDown={(e) => { if (disabled) return; drag.current = true; try { e.currentTarget.setPointerCapture(e.pointerId); } catch (_) {} fromX(e.clientX); }}
      onPointerMove={(e) => { if (drag.current) fromX(e.clientX); }}
      onPointerUp={() => { drag.current = false; }}
      onPointerCancel={() => { drag.current = false; }}
      style={{ position: "relative", flex: 1, minWidth: 60, height: 28, touchAction: "none", cursor: disabled ? "not-allowed" : "pointer" }}>
      <div style={{ position: "absolute", left: 0, right: 0, top: 11, height: 6, borderRadius: 3, background: "#0F141D", boxShadow: "inset 0 1px 3px rgba(0,0,0,.6)" }} />
      <div style={{ position: "absolute", left: 0, top: 11, height: 6, width: `${t * 100}%`, borderRadius: 3, background: `linear-gradient(90deg, ${ac.a(0.45)}, ${ac.c})` }} />
      <div style={{ position: "absolute", top: 4, left: `${t * 100}%`, width: 20, height: 20, marginLeft: -10, borderRadius: 10, background: "linear-gradient(180deg,#4A5675,#2B3550)", border: "1px solid rgba(255,255,255,.22)", boxShadow: "0 2px 6px rgba(0,0,0,.5)", display: "grid", placeItems: "center" }}>
        <span style={{ width: 6, height: 6, borderRadius: 3, background: ac.c }} />
      </div>
    </div>
  );
}

function ValueTag({ children, w = 62 }) {
  const ac = useAc();
  return <span style={{ width: w, textAlign: "right", fontSize: 13, fontWeight: 800, color: ac.c, fontFamily: MONO, flex: "none" }}>{children}</span>;
}

function ResetBtn({ show, onClick }) {
  return (
    <button onClick={onClick} className="btn" aria-label="Reset"
      style={{ width: 24, height: 24, border: 0, borderRadius: 12, background: "rgba(255,255,255,.06)", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer", flex: "none", opacity: show ? 1 : 0, pointerEvents: show ? "auto" : "none", scale: show ? "1" : ".6", transition: "opacity .25s, scale .25s" }}>
      <Ico n="reset" size={13} />
    </button>
  );
}

function SliderRow({ title, hint, value, min, max, step, onChange, def, fmt, dim }) {
  const changed = Math.abs(value - def) > 1e-6;
  return (
    <Row title={title} hint={hint} dim={dim} h={56}>
      <Slider value={value} min={min} max={max} step={step} onChange={onChange} disabled={dim} />
      <ValueTag>{fmt(value)}</ValueTag>
      <ResetBtn show={changed && !dim} onClick={() => onChange(def)} />
    </Row>
  );
}

/* ═══════════════ settings content ═══════════════ */

const BUF = [64, 128, 256, 512, 1024].map((v) => ({ v, label: String(v), sub: `~${((v / 44100) * 1000).toFixed(1)} ms` }));
const POLY = [64, 128, 256, 512].map((v) => ({ v, label: String(v) }));

const TABS = [
  { id: "audio", label: "Audio", icon: "wave", sub: "Audio engine & latency" },
  { id: "interface", label: "Interface", icon: "swatch", sub: "Layout & colors" },
  { id: "midi", label: "MIDI", icon: "midi", sub: "Controllers & mapping" },
  { id: "system", label: "System", icon: "gear", sub: "Language & display" },
];

const ITEMS = [
  { tab: "audio", label: "Audio buffer size", kw: "latency buffer ms" },
  { tab: "audio", label: "Maximum polyphony", kw: "voices notes" },
  { tab: "audio", label: "Synth output headroom", kw: "gain fluidsynth clipping" },
  { tab: "interface", label: "Auto-fit faders", kw: "layout scroll mixer" },
  { tab: "interface", label: "Primary interface hue", kw: "color theme" },
  { tab: "interface", label: "Accent color saturation", kw: "color" },
  { tab: "interface", label: "Background tone saturation", kw: "color" },
  { tab: "interface", label: "Background lightness", kw: "color brightness" },
  { tab: "midi", label: "Anti-jitter filter", kw: "debounce burst" },
  { tab: "midi", label: "Filter sensitivity window", kw: "debounce ms" },
  { tab: "midi", label: "Map Mixer", kw: "midi learn controller" },
  { tab: "midi", label: "Map DrumPad", kw: "midi learn pads" },
  { tab: "midi", label: "Reset to defaults", kw: "midi" },
  { tab: "midi", label: "Clear all mappings", kw: "midi" },
  { tab: "system", label: "Language", kw: "english" },
  { tab: "system", label: "Musical notation", kw: "sharps flats" },
  { tab: "system", label: "Keep screen on", kw: "sleep" },
];

/* mapping data model */
const MX_ACTIONS = [
  { id: "mx.tap", name: "Tap Tempo" },
  { id: "mx.metro", name: "Metronome On / Off" },
  { id: "mx.snapprev", name: "Previous Snapshot" },
  { id: "mx.snapnext", name: "Next Snapshot" },
];
const DP_ACTIONS = [{ id: "dp.stop", name: "Loop Stop" }];
const MX_FIXED = [
  { id: "mx.trans-", name: "Transpose −1 Semitone", x: 25, y: 29 },
  { id: "mx.trans+", name: "Transpose +1 Semitone", x: 113, y: 29 },
  { id: "mx.oct-", name: "Keyboard Octave −1", x: 149, y: 29 },
  { id: "mx.oct+", name: "Keyboard Octave +1", x: 237, y: 29 },
  { id: "mx.prev", name: "Previous Patch", x: 288, y: 29 },
  { id: "mx.next", name: "Next Patch", x: 534, y: 29 },
  { id: "mx.panic", name: "Panic / All Notes Off", x: 590, y: 29 },
  { id: "mx.patches", name: "Open Patches Drawer", x: 813, y: 29 },
  { id: "mx.settings", name: "Open Settings Drawer", x: 860, y: 29 },
  { id: "mx.default", name: "Default Snapshot", x: 233, y: 372 },
  { id: "mx.snap1", name: "Snapshot 1", x: 326, y: 372 },
  { id: "mx.snap2", name: "Snapshot 2", x: 419, y: 372 },
  { id: "mx.snap3", name: "Snapshot 3", x: 512, y: 372 },
  { id: "mx.snap4", name: "Snapshot 4", x: 605, y: 372 },
  { id: "mx.master", name: "Master Volume", x: 760, y: 372, full: true },
];
const DP_PADS = Array.from({ length: 8 }, (_, i) => ({
  id: `dp.pad${i + 1}`, name: `Drum Pad ${i + 1} (Trigger)`,
  x: [289, 459, 629, 798][i % 4], y: i < 4 ? 145 : 317, full: true,
}));

function mixGeo(autoFit) {
  const pitch = autoFit ? 108.6 : 132.2;
  const sx = autoFit ? 0.81 : 1;
  const contentW = autoFit ? W : 11 + 8 * pitch + 11;
  return { pitch, sx, contentW };
}
function mixStripChips(autoFit) {
  const { pitch, sx } = mixGeo(autoFit);
  const out = [];
  for (let i = 0; i < 8; i++) {
    const base = 11 + i * pitch;
    out.push({ id: `mx.t${i + 1}.solo`, name: `Track ${i + 1} · Solo`, px: base + 54.2 * sx, py: 101, mode: autoFit ? "num" : "cc" });
    out.push({ id: `mx.t${i + 1}.mute`, name: `Track ${i + 1} · Mute / Unmute`, px: base + 98.6 * sx, py: 101, mode: autoFit ? "num" : "cc" });
    out.push({ id: `mx.t${i + 1}.fader`, name: `Track ${i + 1} · Fader Volume`, px: base + 62.5 * sx, py: 175, mode: "full" });
  }
  return out;
}
const idsOf = (page) => page === "mixer"
  ? [...MX_FIXED.map((c) => c.id), ...mixStripChips(false).map((c) => c.id), ...MX_ACTIONS.map((c) => c.id)]
  : [...DP_PADS.map((c) => c.id), ...DP_ACTIONS.map((c) => c.id)];
const NAMES = {};
[...MX_FIXED, ...mixStripChips(false), ...MX_ACTIONS, ...DP_PADS, ...DP_ACTIONS].forEach((c) => { NAMES[c.id] = c.name; });

const msgLabel = (m, mode = "full") => {
  const head = `${m.kind === "cc" ? "CC" : "N"}${m.num}`;
  if (mode === "num") return String(m.num);
  if (mode === "cc") return head;
  return `${head}·${m.ch ? m.ch : "All"}`;
};
const msgFull = (m) => `${m.kind === "cc" ? "CC" : "Note"} ${m.num} · ${m.ch ? "Ch " + m.ch : "Omni (All Ch)"}`;
const sameMsg = (a, b) => a && b && a.kind === b.kind && a.num === b.num && a.ch === b.ch;

const INIT_MAP = {
  "mx.master": { kind: "cc", num: 7, ch: 0 },
  "mx.t1.fader": { kind: "cc", num: 21, ch: 1 },
  "mx.t1.mute": { kind: "cc", num: 22, ch: 1 },
  "dp.pad1": { kind: "note", num: 36, ch: 10 },
};
const DEFAULT_MAP = { "mx.master": { kind: "cc", num: 7, ch: 0 } };

function AudioTab({ cfg, set }) {
  const warn = cfg.headroom > 1.2;
  return (
    <>
      <R i={0}><SecLabel>Audio engine</SecLabel></R>
      <R i={1}>
        <Card>
          <Row title="Audio buffer size" hint="A smaller buffer lowers key-touch latency. Adjust to your device's performance." split={0.8} h={74}>
            <Seg options={BUF} value={cfg.buffer} onChange={(v) => set("buffer", v)} tall />
          </Row>
          <Divider />
          <Row title="Maximum polyphony" hint="Notes the synth engine can play at once (voices)." split={0.8} h={62}>
            <Seg options={POLY} value={cfg.poly} onChange={(v) => set("poly", v)} />
          </Row>
        </Card>
      </R>
      <R i={2}><SecLabel>Output</SecLabel></R>
      <R i={3}>
        <Card>
          <SliderRow title="Synth output headroom" hint="FluidSynth master gain · Standard: 1.20x" value={cfg.headroom} min={0.5} max={2} step={0.05} def={1.0} fmt={(v) => `${v.toFixed(2)}x`} onChange={(v) => set("headroom", v)} />
          <div style={{ overflow: "hidden", maxHeight: warn ? 34 : 0, opacity: warn ? 1 : 0, transition: "max-height .3s ease, opacity .3s" }}>
            <div style={{ padding: "0 14px 10px", fontSize: 11.5, fontWeight: 600, color: "#D8A24A" }}>High gain may cause digital clipping.</div>
          </div>
        </Card>
      </R>
    </>
  );
}

function InterfaceTab({ cfg, set }) {
  const changed = cfg.hue !== DEF.hue || cfg.accSat !== DEF.accSat || cfg.bgTone !== DEF.bgTone || cfg.bgLight !== DEF.bgLight;
  return (
    <>
      <R i={0}><SecLabel>Layout</SecLabel></R>
      <R i={1}>
        <Card>
          <Row title="Auto-fit faders" hint={cfg.autoFit ? "On: all faders fit the screen." : "Off: fixed width with scroll."} h={58}>
            <Switch on={cfg.autoFit} onChange={(v) => set("autoFit", v)} />
          </Row>
        </Card>
      </R>
      <R i={2}>
        <SecLabel right={
          <button onClick={() => { set("hue", DEF.hue); set("accSat", DEF.accSat); set("bgTone", DEF.bgTone); set("bgLight", DEF.bgLight); }} className="btn"
            style={{ border: 0, background: "transparent", fontSize: 12, fontWeight: 700, color: "#9AA5BA", cursor: "pointer", opacity: changed ? 1 : 0, pointerEvents: changed ? "auto" : "none", transition: "opacity .25s" }}>Reset colors</button>
        }>Colors</SecLabel>
      </R>
      <R i={3}>
        <Card>
          <SliderRow title="Primary interface hue" value={cfg.hue} min={0} max={360} step={1} def={DEF.hue} fmt={(v) => `${v}°`} onChange={(v) => set("hue", v)} />
          <Divider />
          <SliderRow title="Accent color saturation" value={cfg.accSat} min={0} max={2} step={0.05} def={DEF.accSat} fmt={(v) => `${v.toFixed(2)}x`} onChange={(v) => set("accSat", v)} />
          <Divider />
          <SliderRow title="Background tone saturation" value={cfg.bgTone} min={0} max={3} step={0.05} def={DEF.bgTone} fmt={(v) => `${v.toFixed(2)}x`} onChange={(v) => set("bgTone", v)} />
          <Divider />
          <SliderRow title="Background lightness" value={cfg.bgLight} min={0.5} max={2} step={0.05} def={DEF.bgLight} fmt={(v) => `${v.toFixed(2)}x`} onChange={(v) => set("bgLight", v)} />
        </Card>
      </R>
    </>
  );
}

function MidiTab({ cfg, set, mapped, onMap, onConfirm }) {
  const count = (p) => idsOf(p).filter((id) => mapped[id]).length;
  return (
    <>
      <R i={0}><SecLabel>Input</SecLabel></R>
      <R i={1}>
        <Card>
          <Row title="Anti-jitter filter" hint="Removes parasitic burst signals from worn buttons and encoders." split={1.4} h={60}>
            <Switch on={cfg.jitter} onChange={(v) => set("jitter", v)} />
          </Row>
          <Divider />
          <SliderRow title="Filter sensitivity window" value={cfg.jitterMs} min={1} max={30} step={1} def={DEF.jitterMs} fmt={(v) => `${v} ms`} dim={!cfg.jitter} onChange={(v) => set("jitterMs", v)} />
        </Card>
      </R>
      <R i={2}><SecLabel>MIDI mapping</SecLabel></R>
      <R i={3}>
        <Card>
          <Row title="Map Mixer" hint="Faders, mute and solo, snapshots, master and live." onClick={() => onMap("mixer")} chevron split={1.6} h={58}>
            <span style={{ fontSize: 12, fontWeight: 700, color: "#9AA5BA", fontFamily: MONO }}>{count("mixer")} / {idsOf("mixer").length}</span>
          </Row>
          <Divider />
          <Row title="Map DrumPad" hint="Pad triggers 1–8 and loop stop." onClick={() => onMap("drum")} chevron split={1.6} h={58}>
            <span style={{ fontSize: 12, fontWeight: 700, color: "#9AA5BA", fontFamily: MONO }}>{count("drum")} / {idsOf("drum").length}</span>
          </Row>
        </Card>
      </R>
      <R i={4}><SecLabel>Management</SecLabel></R>
      <R i={5}>
        <Card>
          <Row title="Reset to defaults" hint="Restore the standard CC#7 volume and snapshot mapping." onClick={() => onConfirm("reset")} chevron split={1.6} h={56} />
          <Divider />
          <Row title="Clear all mappings" hint="Unassign every controller and start blank." onClick={() => onConfirm("clear")} chevron split={1.6} h={56} />
        </Card>
      </R>
    </>
  );
}

function SystemTab({ cfg, set, snack }) {
  return (
    <>
      <R i={0}><SecLabel>Language & display</SecLabel></R>
      <R i={1}>
        <Card>
          <Row title="Language" onClick={() => snack("Opens the language list")} chevron h={54} split={1.2}>
            <span style={{ fontSize: 13, color: "#9AA5BA", fontWeight: 600 }}>English</span>
          </Row>
          <Divider />
          <Row title="Musical notation" hint={cfg.notation === "sharps" ? "Sharps (♯ · C#, D#…)" : "Flats (♭ · Db, Eb…)"} split={1.2} h={62}>
            <div style={{ width: 150 }}>
              <Seg options={[{ v: "sharps", label: "♯" }, { v: "flats", label: "♭" }]} value={cfg.notation} onChange={(v) => set("notation", v)} />
            </div>
          </Row>
          <Divider />
          <Row title="Keep screen on" hint={cfg.keepOn ? "Active (prevents screen sleep)." : "Off (the screen can sleep)."} split={1.2} h={58}>
            <Switch on={cfg.keepOn} onChange={(v) => set("keepOn", v)} />
          </Row>
        </Card>
      </R>
    </>
  );
}

/* ═══════════════ simulated pages for the mapping view ═══════════════ */

const Blk = ({ x, y, w, h, r = 10, bg = "linear-gradient(180deg,#2A3347,#202839)", children, style }) => (
  <div style={{ position: "absolute", left: x, top: y, width: w, height: h, borderRadius: r, background: bg, border: "1px solid rgba(255,255,255,.06)", display: "flex", alignItems: "center", justifyContent: "center", color: "#C9D1E0", fontSize: 12, fontWeight: 700, ...style }}>{children}</div>
);
const TCOL = ["#E08A2E", "#2FB37A", "#3C7BE0", "#8A4FD8", "#D8497E", "#26A99C", "#E5A11C", "#5F7BD6"];

function MixerPage({ autoFit, scrollRef, onScroll }) {
  const { pitch, sx, contentW } = mixGeo(autoFit);
  return (
    <div style={{ position: "absolute", inset: 0, background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
      {/* top bar */}
      <Blk x={11} y={9} w={117} h={40}>TRANS 0</Blk>
      <Blk x={135} y={9} w={117} h={40}>OCT 0</Blk>
      <Blk x={258} y={9} w={306} h={40} bg="#0F141D" style={{ color: "#566075" }}>- - -</Blk>
      <Blk x={570} y={9} w={40} h={40} bg="#C2587A" style={{ color: "#fff" }}>⊘</Blk>
      <Blk x={617} y={9} w={77} h={40} style={{ fontSize: 10, letterSpacing: 1 }}>SUSTAIN</Blk>
      <Blk x={701} y={9} w={40} h={40}>✎</Blk>
      <Blk x={748} y={9} w={40} h={40}>▦</Blk>
      <Blk x={795} y={9} w={40} h={40} style={{ color: "#8FB2E8" }}>▤</Blk>
      <Blk x={842} y={9} w={38} h={40}>⚙</Blk>
      {/* strips (scrolls horizontally unless auto-fit) */}
      <div ref={scrollRef} onScroll={onScroll} className="noscroll"
        style={{ position: "absolute", left: 0, top: 53, width: W, height: 290, overflowX: autoFit ? "hidden" : "auto", overflowY: "hidden" }}>
        <div style={{ position: "relative", width: contentW, height: 290 }}>
          {Array.from({ length: 8 }).map((_, i) => {
            const bx = 11 + i * pitch, sw = 126 * sx, col = TCOL[i];
            return (
              <div key={i} style={{ position: "absolute", left: bx, top: 0, width: sw, height: 284, borderRadius: 16, background: `linear-gradient(180deg,#1B2232 30%, ${col}22)`, border: "1px solid rgba(255,255,255,.06)" }}>
                <div style={{ position: "absolute", left: 4 * sx, top: 5, width: 118 * sx, height: 25, borderRadius: 9, background: "#141A26", border: "1px solid rgba(255,255,255,.05)" }} />
                <div style={{ position: "absolute", left: 4 * sx, top: 36, width: 26 * sx, height: 24, borderRadius: 12, background: "#10151F" }} />
                <div style={{ position: "absolute", left: 33 * sx, top: 36, width: 42 * sx, height: 23, borderRadius: 7, background: "#2A3347", display: "grid", placeItems: "center", color: "#C9D1E0", fontSize: 11, fontWeight: 800 }}>S</div>
                <div style={{ position: "absolute", left: 78 * sx, top: 36, width: 42 * sx, height: 23, borderRadius: 7, background: "#2A3347", display: "grid", placeItems: "center", color: "#C9D1E0", fontSize: 11, fontWeight: 800 }}>M</div>
                <div style={{ position: "absolute", left: 55 * sx, top: 66, width: 15, height: 184, borderRadius: 8, background: "#0C1018" }} />
                <div style={{ position: "absolute", left: 55 * sx, top: 130, width: 15, height: 120, borderRadius: 8, background: col, opacity: 0.8 }} />
                <div style={{ position: "absolute", left: 37 * sx, top: 108, width: 52 * sx, height: 40, borderRadius: 7, background: "linear-gradient(180deg,#6A7396,#3C4466)", border: "1px solid rgba(255,255,255,.15)" }} />
                <div style={{ position: "absolute", left: 4 * sx, top: 256, width: 44 * sx, height: 22, borderRadius: 7, background: "#2A3347" }} />
                <div style={{ position: "absolute", left: 51 * sx, top: 256, width: 44 * sx, height: 22, borderRadius: 7, background: "#2A3347" }} />
                <div style={{ position: "absolute", left: 98 * sx, top: 256, width: 22 * sx, height: 22, borderRadius: 7, background: i < 2 ? col : "#202839" }} />
              </div>
            );
          })}
        </div>
      </div>
      {/* bottom bar */}
      <Blk x={17} y={348} w={86} h={45}>CPU 19%</Blk>
      <Blk x={108} y={348} w={76} h={45}>116 BPM</Blk>
      <Blk x={189} y={348} w={89} h={45} bg="#8FB2E8" style={{ color: "#0F1726" }}>Default</Blk>
      {[282, 375, 468, 561].map((x, i) => <Blk key={x} x={x} y={348} w={88} h={45}>Snapshot {i + 1}</Blk>)}
      <Blk x={654} y={348} w={37} h={45}>▣</Blk>
      <Blk x={697} y={348} w={125} h={45}>M ▬▬▬ FX</Blk>
      <Blk x={828} y={348} w={46} h={45}>♫</Blk>
    </div>
  );
}

function DrumPage() {
  const keys = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
  const pads = [["TAMBOURIN N", "100%"], ["Chimes 2", "84%"], ["C2", "71%"], ["C2", "88%"], ["C2", "78%"], ["C2", "74%"], ["C2", "65%"], ["C2", "92%"]];
  return (
    <div style={{ position: "absolute", inset: 0, background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
      <Blk x={11} y={9} w={869} h={40} r={14} style={{ justifyContent: "space-between", padding: "0 18px" }}>
        <span>⚙&nbsp;&nbsp;&nbsp;Loop Player ›</span><span>Swing 54%&nbsp;&nbsp;&nbsp;−&nbsp;116 BPM&nbsp;+&nbsp;&nbsp;&nbsp;Mix</span>
      </Blk>
      {keys.map((k, i) => <Blk key={k} x={11 + (i % 3) * 64} y={63 + Math.floor(i / 3) * 46} w={60} h={42} style={{ fontSize: 14 }}>{k}</Blk>)}
      <Blk x={11} y={252} w={188} h={150} r={14} style={{ alignItems: "flex-start", justifyContent: "flex-start", padding: 12, flexDirection: "column", gap: 8, fontSize: 11 }}>
        <span>Oct − 3 +</span><span>Vol 80%</span><span>Shimmer 40%</span><span>Pad: -</span><span>Kit: DrumKit</span>
      </Blk>
      {pads.map(([n, p], i) => (
        <Blk key={i} x={[207, 377, 547, 717][i % 4]} y={i < 4 ? 63 : 235} w={164} h={165} r={16} style={{ alignItems: "flex-start", justifyContent: "space-between", flexDirection: "column", padding: 12, fontSize: 14 }}>
          <span>{n}</span><span style={{ fontSize: 11, color: "#8690A4", width: "100%", display: "flex", justifyContent: "space-between" }}><span>D{i + 1}</span><span>{p}</span></span>
        </Blk>
      ))}
    </div>
  );
}

/* ═══════════════ mapping view ═══════════════ */

const SC = 0.89;            // the frozen page is shown at 89% so the bottom bar never covers a chip
const FX_ = (W - W * SC) / 2; // frame left
const FY_ = 6;              // frame top

function MapChip({ x, y, rank, label, state, onTap, onHold, leaving, total }) {
  const holdT = useRef(null);
  const fired = useRef(false);
  const ac = useAc();
  const empty = state === "empty";
  const col = state === "conflict" ? "#D8A24A" : ac.c;
  const delay = leaving ? Math.max(0, (total - rank) * 7) : 160 + Math.min(rank * 11, 460);
  return (
    <div
      onPointerDown={() => { fired.current = false; clearTimeout(holdT.current); holdT.current = setTimeout(() => { fired.current = true; onHold && onHold(); }, 520); }}
      onPointerUp={() => { clearTimeout(holdT.current); if (!fired.current) onTap(); }}
      onPointerLeave={() => clearTimeout(holdT.current)}
      onPointerCancel={() => clearTimeout(holdT.current)}
      style={{ position: "absolute", left: x, top: y, translate: "-50% -50%", zIndex: state === "listening" || state === "conflict" ? 5 : 2, cursor: "pointer", touchAction: "none" }}>
      <div style={{
        animation: leaving ? `chipOut .22s ease-in ${delay}ms forwards` : `chipIn .3s cubic-bezier(.3,1.5,.5,1) ${delay}ms backwards, breathe 3.6s ease-in-out ${delay + 700}ms infinite`,
        minWidth: 20, height: 20, padding: empty ? 0 : "0 7px", borderRadius: 10, display: "flex", alignItems: "center", justifyContent: "center", gap: 4,
        background: state === "listening" ? ac.a(0.28) : state === "conflict" ? "rgba(216,162,74,.18)" : empty ? "rgba(255,255,255,.13)" : "#273045",
        border: `1px solid ${state === "listening" || state === "conflict" ? col : empty ? "rgba(255,255,255,.26)" : "rgba(255,255,255,.28)"}`,
        boxShadow: "0 2px 8px rgba(0,0,0,.5)", color: "#E3E8F2", fontFamily: MONO, fontSize: 10, fontWeight: 800,
        "--ring": col,
        ...(state === "listening" ? { animation: "ring 1.1s ease-out infinite" } : {}),
      }}>
        {empty ? <Ico n="plus" size={11} sw={2.6} /> : (
          <>
            {state === "mapped" && <span style={{ width: 4, height: 4, borderRadius: 2, background: ac.c }} />}
            <span>{state === "listening" ? "…" : label}</span>
          </>
        )}
      </div>
    </div>
  );
}

function MappingView({ page, autoFit, mapped, setMapped, onDone, simRef, snack, ac }) {
  const [listen, setListen] = useState(null);
  const [conflict, setConflict] = useState(null);
  const [leaving, setLeaving] = useState(false);
  const [actionsOpen, setActionsOpen] = useState(false);
  const [scroll, setScroll] = useState(0);
  const scrollRef = useRef(null);
  const geo = mixGeo(autoFit);

  useEffect(() => { setScroll(0); if (scrollRef.current) scrollRef.current.scrollLeft = 0; }, [autoFit, page]);

  const fixed = page === "mixer" ? MX_FIXED : DP_PADS;
  const strips = page === "mixer" ? mixStripChips(autoFit) : [];
  const actions = page === "mixer" ? MX_ACTIONS : DP_ACTIONS;

  const ranks = useMemo(() => {
    const all = [...fixed.map((c) => ({ id: c.id, px: c.x, py: c.y })), ...strips.map((c) => ({ id: c.id, px: c.px, py: c.py }))];
    all.sort((a, b) => (Math.round(a.py / 70) - Math.round(b.py / 70)) || (a.px - b.px));
    const m = {};
    all.forEach((c, i) => { m[c.id] = i; });
    return { m, total: all.length };
  }, [page, autoFit]); // eslint-disable-line

  const stateOf = (id) => {
    if (listen === id) return conflict ? "conflict" : "listening";
    if (conflict && conflict.other === id) return "conflict";
    return mapped[id] ? "mapped" : "empty";
  };
  const tap = (id) => { setConflict(null); setListen(id); setActionsOpen(false); vib(8); };
  const hold = (id) => {
    if (!mapped[id]) return;
    setMapped((m) => { const n = { ...m }; delete n[id]; return n; });
    snack(`Mapping cleared · ${NAMES[id]}`); vib(14);
  };

  simRef.current = (msg) => {
    if (!listen) { snack("Tap a chip first"); return; }
    const other = Object.keys(mapped).find((k) => k !== listen && sameMsg(mapped[k], msg));
    if (other) { setConflict({ id: listen, other, msg }); vib(10); return; }
    setMapped((m) => ({ ...m, [listen]: msg }));
    snack(`${NAMES[listen]} → ${msgFull(msg)}`);
    setListen(null); vib(12);
  };
  const replace = () => {
    setMapped((m) => { const n = { ...m }; delete n[conflict.other]; n[conflict.id] = conflict.msg; return n; });
    snack(`${NAMES[conflict.id]} → ${msgFull(conflict.msg)}`);
    setConflict(null); setListen(null); vib(12);
  };
  const finish = () => {
    setListen(null); setConflict(null); setActionsOpen(false); setLeaving(true);
    setTimeout(onDone, 520);
  };

  /* hidden strips (scroll) */
  let hidL = 0, hidR = 0;
  if (page === "mixer") {
    for (let i = 0; i < 8; i++) {
      const bx = 11 + i * geo.pitch, ex = bx + 126 * geo.sx;
      if (ex <= scroll + 6) hidL += 3;
      else if (bx >= scroll + W - 6) hidR += 3;
    }
  }
  const jump = (d) => { if (scrollRef.current) scrollRef.current.scrollBy({ left: d, behavior: "smooth" }); };

  const ids = idsOf(page);
  const done = ids.filter((id) => mapped[id]).length;
  const modeFor = (c) => c.mode || "full";
  const labelFor = (id, mode) => (mapped[id] ? msgLabel(mapped[id], mode) : "");

  return (
    <div style={{ position: "absolute", inset: 0, background: "#0A0E15", animation: "fadeIn .3s ease-out" }}>
      {/* frozen page */}
      <div style={{ position: "absolute", left: FX_, top: FY_, width: W * SC, height: H * SC, borderRadius: 16, overflow: "hidden", border: "1px solid rgba(255,255,255,.08)", boxShadow: "0 18px 40px rgba(0,0,0,.55)", animation: "frameIn .35s cubic-bezier(.2,.9,.25,1)" }}>
        <div style={{ width: W, height: H, transform: `scale(${SC})`, transformOrigin: "0 0", position: "relative" }}>
          {page === "mixer" ? <MixerPage autoFit={autoFit} scrollRef={scrollRef} onScroll={(e) => setScroll(e.currentTarget.scrollLeft)} /> : <DrumPage />}
        </div>
        <div style={{ position: "absolute", inset: 0, background: "rgba(8,11,18,.68)", backdropFilter: "blur(1.2px)", pointerEvents: "none", animation: "dim .35s ease-out" }} />
      </div>

      {/* chips: fixed controls */}
      {fixed.map((c) => (
        <MapChip key={c.id} x={FX_ + c.x * SC} y={FY_ + c.y * SC} rank={ranks.m[c.id]} total={ranks.total} leaving={leaving}
          label={labelFor(c.id, "full")} state={stateOf(c.id)} onTap={() => tap(c.id)} onHold={() => hold(c.id)} />
      ))}

      {/* chips: scrolling strips (clipped to the strips area) */}
      {page === "mixer" && (
        <div style={{ position: "absolute", left: FX_, top: FY_ + 53 * SC, width: W * SC, height: 290 * SC, overflow: "hidden", pointerEvents: "none" }}>
          {strips.map((c) => (
            <div key={c.id} style={{ pointerEvents: "auto" }}>
              <MapChip x={(c.px - scroll) * SC} y={(c.py - 53) * SC} rank={ranks.m[c.id]} total={ranks.total} leaving={leaving}
                label={labelFor(c.id, modeFor(c))} state={stateOf(c.id)} onTap={() => tap(c.id)} onHold={() => hold(c.id)} />
            </div>
          ))}
        </div>
      )}

      {/* scroll indicators */}
      {page === "mixer" && !autoFit && (
        <>
          <button onClick={() => jump(-300)} className="btn" style={{ position: "absolute", left: FX_ + 6, top: FY_ + 175 * SC, translate: "0 -50%", zIndex: 6, opacity: hidL ? 1 : 0, pointerEvents: hidL ? "auto" : "none", transition: "opacity .25s", border: "1px solid rgba(255,255,255,.2)", background: "#222C40", color: "#DCE2EE", borderRadius: 12, height: 24, padding: "0 9px", fontSize: 11, fontWeight: 800, fontFamily: MONO, cursor: "pointer" }}>‹ {hidL}</button>
          <button onClick={() => jump(300)} className="btn" style={{ position: "absolute", right: FX_ + 6, top: FY_ + 175 * SC, translate: "0 -50%", zIndex: 6, opacity: hidR ? 1 : 0, pointerEvents: hidR ? "auto" : "none", transition: "opacity .25s", border: "1px solid rgba(255,255,255,.2)", background: "#222C40", color: "#DCE2EE", borderRadius: 12, height: 24, padding: "0 9px", fontSize: 11, fontWeight: 800, fontFamily: MONO, cursor: "pointer" }}>{hidR} ›</button>
        </>
      )}

      {/* listening / conflict card */}
      {listen && (
        <div style={{ position: "absolute", left: "50%", bottom: 52, marginLeft: -215, width: 430, zIndex: 20, borderRadius: 16, padding: "12px 16px", background: "linear-gradient(180deg,#232C3E,#1A2230)", border: `1px solid ${conflict ? "rgba(216,162,74,.5)" : ac.a(0.4)}`, boxShadow: "0 16px 40px rgba(0,0,0,.55)", animation: "menuIn .18s ease-out", display: "flex", alignItems: "center", gap: 14 }}>
          <div style={{ width: 10, height: 10, borderRadius: 5, background: conflict ? "#D8A24A" : ac.c, "--ring": conflict ? "#D8A24A" : ac.c, animation: "ring 1.1s ease-out infinite", flex: "none" }} />
          <div style={{ flex: 1, minWidth: 0 }}>
            {!conflict ? (
              <>
                <div style={{ fontSize: 14, fontWeight: 800, color: "#F1F4FA" }}>Move a control on your controller</div>
                <div style={{ fontSize: 11.5, color: "#8690A4", marginTop: 2, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                  {NAMES[listen]}{mapped[listen] ? ` · now ${msgFull(mapped[listen])}` : ""}
                </div>
              </>
            ) : (
              <>
                <div style={{ fontSize: 14, fontWeight: 800, color: "#F1F4FA" }}>{msgFull(conflict.msg)} is already used</div>
                <div style={{ fontSize: 11.5, color: "#D8A24A", marginTop: 2, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>by {NAMES[conflict.other]}</div>
              </>
            )}
          </div>
          {conflict && (
            <button onClick={replace} className="btn" style={{ height: 32, padding: "0 14px", borderRadius: 11, border: 0, background: ac.c, color: "#0F1726", fontSize: 13, fontWeight: 800, cursor: "pointer" }}>Replace</button>
          )}
          <button onClick={() => { setListen(null); setConflict(null); }} className="btn" style={{ height: 32, padding: "0 14px", borderRadius: 11, border: "1px solid rgba(255,255,255,.14)", background: "rgba(255,255,255,.05)", color: "#C7CFDD", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>Cancel</button>
        </div>
      )}

      {/* other actions popover */}
      {actionsOpen && (
        <>
          <div onClick={() => setActionsOpen(false)} style={{ position: "absolute", inset: 0, zIndex: 18 }} />
          <div style={{ position: "absolute", left: "50%", bottom: 52, marginLeft: -150, width: 300, zIndex: 19, borderRadius: 16, padding: 8, background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 16px 40px rgba(0,0,0,.55)", animation: "menuIn .18s ease-out" }}>
            <div style={{ fontSize: 10.5, letterSpacing: 1.3, fontWeight: 700, color: "#7B859A", padding: "4px 8px 6px" }}>ACTIONS WITHOUT A BUTTON ON SCREEN</div>
            {actions.map((a) => {
              const m = mapped[a.id];
              return (
                <div key={a.id} onClick={() => tap(a.id)} className="press" style={{ display: "flex", alignItems: "center", gap: 10, height: 38, padding: "0 8px", borderRadius: 10, cursor: "pointer" }}>
                  <span style={{ flex: 1, fontSize: 13.5, fontWeight: 600, color: "#DCE2EE" }}>{a.name}</span>
                  <span style={{ minWidth: 20, height: 20, padding: m ? "0 7px" : 0, borderRadius: 10, display: "inline-flex", alignItems: "center", justifyContent: "center", gap: 4, background: m ? "#273045" : "rgba(255,255,255,.13)", border: "1px solid rgba(255,255,255,.26)", color: "#E3E8F2", fontFamily: MONO, fontSize: 10, fontWeight: 800 }}>
                    {m ? <><span style={{ width: 4, height: 4, borderRadius: 2, background: ac.c }} />{msgLabel(m)}</> : <Ico n="plus" size={11} sw={2.6} />}
                  </span>
                </div>
              );
            })}
          </div>
        </>
      )}

      {/* mapping bar */}
      <div style={{ position: "absolute", left: "50%", bottom: 8, marginLeft: -270, width: 540, height: 38, zIndex: 21, borderRadius: 19, display: "flex", alignItems: "center", gap: 14, padding: "0 6px 0 18px", background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 8px 22px rgba(0,0,0,.5)", transform: leaving ? "translateY(60px)" : "none", transition: "transform .3s ease-in", animation: "snackIn .35s cubic-bezier(.2,.9,.25,1) .1s backwards" }}>
        <span style={{ fontSize: 13, fontWeight: 800, color: "#F1F4FA" }}>Mapping · {page === "mixer" ? "Mixer" : "DrumPad"}</span>
        <span style={{ fontSize: 12, fontWeight: 700, color: "#9AA5BA", fontFamily: MONO }}>{done} / {ids.length} mapped</span>
        <div style={{ flex: 1 }} />
        <button onClick={() => setActionsOpen((v) => !v)} className="btn" style={{ height: 28, padding: "0 12px", borderRadius: 14, border: "1px solid rgba(255,255,255,.14)", background: actionsOpen ? "rgba(255,255,255,.12)" : "rgba(255,255,255,.05)", color: "#C7CFDD", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Other actions</button>
        <button onClick={finish} className="btn" style={{ height: 28, padding: "0 20px", borderRadius: 14, border: 0, background: "linear-gradient(180deg,#9DBDEB,#7FA3DD)", color: "#0F1726", fontSize: 13, fontWeight: 800, cursor: "pointer" }}>Done</button>
      </div>
    </div>
  );
}

/* ═══════════════ settings page shell ═══════════════ */

function Confirm({ kind, onCancel, onConfirm }) {
  const clear = kind === "clear";
  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 40, display: "grid", placeItems: "center" }}>
      <div onClick={onCancel} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.62)", backdropFilter: "blur(3px)", animation: "fadeIn .25s" }} />
      <div style={{ position: "relative", width: 380, borderRadius: 20, padding: "18px 20px", background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 24px 60px rgba(0,0,0,.55)", animation: "menuIn .2s ease-out" }}>
        <div style={{ fontSize: 17, fontWeight: 800, color: "#F1F4FA" }}>{clear ? "Clear all mappings?" : "Reset to defaults?"}</div>
        <div style={{ fontSize: 13, color: "#8690A4", marginTop: 6, lineHeight: 1.45 }}>
          {clear ? "Every MIDI assignment will be removed. This can't be undone." : "Only the standard CC#7 volume mapping is kept. Other assignments are removed."}
        </div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 16 }}>
          <button onClick={onCancel} className="btn" style={{ height: 36, padding: "0 18px", borderRadius: 12, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.05)", color: "#C7CFDD", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>Cancel</button>
          <button onClick={onConfirm} className="btn" style={{ height: 36, padding: "0 20px", borderRadius: 12, border: 0, background: clear ? "#B8465F" : "#8FB2E8", color: clear ? "#fff" : "#0F1726", fontSize: 14, fontWeight: 800, cursor: "pointer" }}>{clear ? "Clear" : "Reset"}</button>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ main ═══════════════ */

export default function SettingsPreview() {
  const [screen, setScreen] = useState("settings");
  const [mapPage, setMapPage] = useState("mixer");
  const [tab, setTab] = useState("audio");
  const [cfg, setCfg] = useState(DEF);
  const [mapped, setMapped] = useState(INIT_MAP);
  const [query, setQuery] = useState("");
  const [confirm, setConfirm] = useState(null);
  const [snack, setSnack] = useState(null);
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });
  const snackT = useRef(null);
  const simRef = useRef(null);
  const scaleRef = useRef(1);

  useEffect(() => {
    const on = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);

  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 130) / H, 1.5));
  scaleRef.current = scale;
  const portrait = vp.h > vp.w;

  const showSnack = (text, ms = 1900) => {
    clearTimeout(snackT.current);
    setSnack({ text, key: Date.now() });
    snackT.current = setTimeout(() => setSnack(null), ms);
  };
  const set = (k, v) => setCfg((o) => ({ ...o, [k]: v }));

  /* accent and background follow the interface sliders */
  const hue = cfg.hue - 4;
  const accSat = clamp(66 * cfg.accSat, 0, 95);
  const ac = useMemo(() => ({
    c: `hsl(${hue} ${accSat}% 73%)`,
    a: (x) => `hsl(${hue} ${accSat}% 73% / ${x})`,
  }), [hue, accSat]);
  const bgS = clamp(31 * (cfg.bgTone / 1.6), 0, 70);
  const bgL = clamp(12 * cfg.bgLight, 4, 24);
  const bg = `linear-gradient(180deg, hsl(${cfg.hue - 3} ${bgS}% ${bgL}%), hsl(${cfg.hue - 2} ${bgS * 0.97}% ${bgL * 0.8}%))`;

  const t = TABS.find((x) => x.id === tab);
  const tabIdx = TABS.findIndex((x) => x.id === tab);
  const q = query.trim().toLowerCase();
  const results = q ? ITEMS.filter((i) => (i.label + " " + i.kw).toLowerCase().includes(q)) : null;

  const openMap = (p) => { setMapPage(p); setScreen("mapping"); vib(8); };
  const doConfirm = () => {
    if (confirm === "clear") { setMapped({}); showSnack("All mappings cleared"); }
    else { setMapped(DEFAULT_MAP); showSnack("Mappings reset to defaults"); }
    setConfirm(null); vib(12);
  };

  const MSGS = [
    { kind: "cc", num: 7, ch: 1, t: "CC 7 · Ch 1" },
    { kind: "cc", num: 21, ch: 1, t: "CC 21 · Ch 1" },
    { kind: "cc", num: 64, ch: 2, t: "CC 64 · Ch 2" },
    { kind: "note", num: 38, ch: 10, t: "Note 38 · Ch 10" },
  ];

  return (
    <Ctx.Provider value={ac}>
      <div className="st" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
        <style>{CSS}</style>

        <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
          <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: bg, transition: "background .3s" }}>

            {/* ── settings page (no halos, no auras) ── */}
            <div style={{ position: "absolute", inset: 0 }}>
              {/* header */}
              <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 62, display: "flex", alignItems: "center", gap: 14, padding: "0 18px" }}>
                <button onClick={() => showSnack("Back to the app")} className="btn" aria-label="Back"
                  style={{ width: 40, height: 40, borderRadius: 20, border: "1px solid rgba(255,255,255,.1)", background: "rgba(255,255,255,.07)", color: "#E9EDF5", display: "grid", placeItems: "center", cursor: "pointer", flex: "none" }}>
                  <Ico n="back" size={19} />
                </button>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 21, fontWeight: 800, color: "#F4F6FB", lineHeight: 1.1 }}>Settings</div>
                  <div key={tab} className="rise" style={{ fontSize: 12, color: "#7B859A", marginTop: 2 }}>{results ? "Search results" : t.sub}</div>
                </div>
                <div style={{ flex: 1 }} />
                <div style={{ width: 240, height: 38, borderRadius: 13, border: "1px solid rgba(255,255,255,.1)", background: "rgba(15,20,29,.7)", display: "flex", alignItems: "center", gap: 8, padding: "0 12px", color: "#8690A4" }}>
                  <Ico n="search" size={16} />
                  <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search settings"
                    style={{ flex: 1, minWidth: 0, background: "transparent", border: 0, outline: "none", color: "#E9EDF5", fontSize: 14 }} />
                  {query && <button onClick={() => setQuery("")} className="btn" style={{ border: 0, background: "transparent", color: "#8690A4", display: "grid", placeItems: "center", cursor: "pointer", padding: 0 }}><Ico n="x" size={14} /></button>}
                </div>
              </div>
              <div style={{ position: "absolute", left: 18, right: 18, top: 62, height: 1, background: "rgba(255,255,255,.07)" }} />

              {/* tab rail */}
              <div style={{ position: "absolute", left: 18, top: 78, width: 196 }}>
                <div style={{ position: "relative" }}>
                  <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 46, borderRadius: 14, background: "linear-gradient(180deg, rgba(255,255,255,.11), rgba(255,255,255,.06))", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 4px 12px rgba(0,0,0,.28)", transform: `translateY(${tabIdx * 52}px)`, opacity: results ? 0.35 : 1, transition: "transform .34s cubic-bezier(.3,1.3,.5,1), opacity .25s" }} />
                  {TABS.map((x) => {
                    const on = x.id === tab && !results;
                    return (
                      <button key={x.id} onClick={() => { setQuery(""); setTab(x.id); }} className="btn"
                        style={{ position: "relative", display: "flex", alignItems: "center", gap: 12, width: "100%", height: 46, marginBottom: 6, padding: "0 14px", border: 0, background: "transparent", cursor: "pointer", borderRadius: 14, color: on ? ac.c : "#8690A4", transition: "color .25s" }}>
                        <Ico n={x.icon} size={18} />
                        <span style={{ fontSize: 14.5, fontWeight: 700, color: on ? "#F1F4FA" : "#9AA5BA", transition: "color .25s" }}>{x.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* content */}
              <div className="noscroll" style={{ position: "absolute", left: 232, right: 0, top: 63, bottom: 0, overflowY: "auto", padding: "0 18px 24px 0" }}>
                <div key={results ? "search" : tab}>
                  {results ? (
                    <>
                      <R i={0}><SecLabel>{results.length} {results.length === 1 ? "result" : "results"}</SecLabel></R>
                      <R i={1}>
                        <Card>
                          {results.length === 0 && <div style={{ padding: "18px 14px", fontSize: 13.5, color: "#7B859A" }}>No setting matches “{query}”.</div>}
                          {results.map((r, i) => (
                            <div key={r.label}>
                              {i > 0 && <Divider />}
                              <Row title={r.label} onClick={() => { setQuery(""); setTab(r.tab); }} chevron h={48}>
                                <span style={{ fontSize: 11, fontWeight: 700, color: "#9AA5BA", padding: "3px 9px", borderRadius: 8, background: "rgba(255,255,255,.07)" }}>{TABS.find((x) => x.id === r.tab).label}</span>
                              </Row>
                            </div>
                          ))}
                        </Card>
                      </R>
                    </>
                  ) : tab === "audio" ? <AudioTab cfg={cfg} set={set} />
                    : tab === "interface" ? <InterfaceTab cfg={cfg} set={set} />
                    : tab === "midi" ? <MidiTab cfg={cfg} set={set} mapped={mapped} onMap={openMap} onConfirm={setConfirm} />
                    : <SystemTab cfg={cfg} set={set} snack={showSnack} />}
                </div>
              </div>
            </div>

            {/* ── mapping view ── */}
            {screen === "mapping" && (
              <div style={{ position: "absolute", inset: 0, zIndex: 30 }}>
                <MappingView page={mapPage} autoFit={cfg.autoFit} mapped={mapped} setMapped={setMapped}
                  onDone={() => setScreen("settings")} simRef={simRef} snack={showSnack} ac={ac} />
              </div>
            )}

            {confirm && <Confirm kind={confirm} onCancel={() => setConfirm(null)} onConfirm={doConfirm} />}

            {snack && (
              <div key={snack.key} style={{ position: "absolute", left: "50%", top: 14, transform: "translateX(-50%)", zIndex: 60, animation: "snackIn .3s cubic-bezier(.2,.9,.25,1)" }}>
                <div style={{ padding: "10px 16px", borderRadius: 14, background: "#222C40", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 12px 30px rgba(0,0,0,.5)", fontSize: 13.5, fontWeight: 600, color: "#E9EDF5", maxWidth: 520, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{snack.text}</div>
              </div>
            )}
          </div>
        </div>

        {/* preview helpers (outside the device) */}
        <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", alignItems: "center", maxWidth: 760 }}>
          {[["Settings", () => { setScreen("settings"); }], ["Mapping · Mixer", () => openMap("mixer")], ["Mapping · DrumPad", () => openMap("drum")]].map(([l, fn]) => (
            <button key={l} onClick={fn} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>{l}</button>
          ))}
        </div>
        {screen === "mapping" && (
          <div style={{ marginTop: 10, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", alignItems: "center", maxWidth: 760 }}>
            <span style={{ fontSize: 12, color: "#7B859A" }}>Simulate an incoming MIDI message:</span>
            {MSGS.map((m) => (
              <button key={m.t} onClick={() => simRef.current && simRef.current({ kind: m.kind, num: m.num, ch: m.ch })}
                style={{ padding: "7px 12px", borderRadius: 999, border: "1px solid #2F3B52", background: "#16202F", color: "#DCE2EE", fontSize: 12, fontWeight: 700, fontFamily: MONO, cursor: "pointer" }}>{m.t}</button>
            ))}
          </div>
        )}
        <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 600, lineHeight: 1.5 }}>
          {screen === "mapping"
            ? "Tap a chip, then simulate a message · long-press a mapped chip to clear it · scroll the mixer · Done to leave"
            : "MIDI tab → Map Mixer / Map DrumPad opens the mapping view · Interface tab changes the colors and the Auto-fit setting live"}
          {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
        </div>
      </div>
    </Ctx.Provider>
  );
}
