import { useState, useRef, useEffect, useMemo } from "react";

/* ─────────────────────────────────────────────────────────────
   SOUNDFONTS WINDOW — landscape smartphone preview (891 × 411 dp)
   Everything inside the device frame is the real design, in English.
   The mixer behind the window is a simple simulation.
   Controls under the frame are preview helpers only.
   ───────────────────────────────────────────────────────────── */

const W = 891, H = 411;
const AC = "#8FB2E8";
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const MONO = "ui-monospace, SFMono-Regular, Menlo, monospace";

const WIN = { x: 116, y: 20, w: 660, h: 372 };

/* ── classification by name (first match wins) ── */
const RULES = [
  ["organ", /organ|b3|hammond|leslie|harmonium/i],
  ["drums", /drum|kit|perc|snare|kick|hi-?hat|cymbal|conga|bongo|tambour|timpani/i],
  ["bass", /bass/i],
  ["keys", /rhodes|wurl|clav|tine|e\.?piano|electric piano|harpsichord|accordion|acordeon|celesta/i],
  ["piano", /piano|grand|honky/i],
  ["guitar", /guitar|nylon|banjo|ukulele|mandolin|sitar/i],
  ["mallets", /vib|marimba|xylo|glock|bell|chime/i],
  ["strings", /string|violin|viola|cello|harp|pizzicato|orchestra/i],
  ["brass", /brass|trumpet|trombone|tuba|horn|cornet|flugel/i],
  ["wind", /flute|sax|oboe|clarinet|bassoon|piccolo|recorder|whistle/i],
  ["pads", /pad|choir|voice|aah|ooh|halo|sweep|atmos/i],
  ["synth", /lead|synth|saw|square|arp|pluck/i],
  ["fx", /fx|rain|sci-?fi|noise|echo|sound ?track|crystal|goblin/i],
];
const CATS = [
  { id: "piano", label: "Piano" }, { id: "keys", label: "Keys" }, { id: "organ", label: "Organ" },
  { id: "guitar", label: "Guitar" }, { id: "bass", label: "Bass" }, { id: "strings", label: "Strings" },
  { id: "brass", label: "Brass" }, { id: "wind", label: "Woodwind" }, { id: "synth", label: "Synth" },
  { id: "pads", label: "Pads" }, { id: "mallets", label: "Mallets" }, { id: "drums", label: "Drums & Perc" },
  { id: "fx", label: "FX" },
];
const SPECIAL = [{ id: "fav", label: "Favorites" }, { id: "other", label: "Other" }];
const catLabel = (id) => [...CATS, ...SPECIAL].find((c) => c.id === id)?.label || id;
const classify = (name) => {
  for (const [id, re] of RULES) if (re.test(name)) return id;
  return "other";
};

/* ── data ── */
const B0 = ["Grand Piano", "Bright Grand Piano", "Electric Grand Piano", "Honky-Tonk Piano", "Tine Electric Piano", "Chorused Electric Piano", "Harpsichord", "Clavinet", "Celesta", "Glockenspiel", "Vibraphone", "Marimba", "Xylophone", "Tubular Bells", "Hammond Organ", "Percussive Organ", "Rock Organ", "Church Organ", "Reed Organ", "Accordion", "Nylon Guitar", "Steel Guitar", "Jazz Guitar", "Clean Guitar", "Muted Guitar", "Overdriven Guitar", "Acoustic Bass", "Fingered Bass", "Picked Bass", "Fretless Bass", "Slap Bass", "Synth Bass", "Violin", "Viola", "Cello", "Pizzicato Strings", "Orchestral Harp", "String Ensemble", "Synth Strings", "Choir Aahs", "Voice Oohs", "Trumpet", "Trombone", "Tuba", "French Horn", "Brass Section", "Soprano Sax", "Alto Sax", "Oboe", "Clarinet", "Flute", "Pan Flute", "Square Lead", "Saw Lead", "New Age Pad", "Warm Pad", "Halo Pad", "Sweep Pad", "Rain", "Sci-Fi", "Standard Kit"];
const BANKS = [
  { id: 0, label: "Bank 0 (Main)", presets: B0 },
  { id: 1, label: "Bank 1", presets: ["Detuned Piano", "Stereo Grand", "Soft Rhodes", "Wurly", "Dream Pad", "Analog Saw", "Pluck Lead", "Soft Choir"] },
  { id: 2, label: "Bank 2", presets: ["Room Kit", "Brush Kit", "Electronic Kit", "Orchestra Kit", "Jazz Organ", "Gospel Organ"] },
  { id: 3, label: "Bank 3", presets: ["Dark Strings", "Slow Strings", "Warm Brass", "Solo Cello", "Solo Violin"] },
  { id: 4, label: "Bank 4", presets: ["Nylon Soft", "Steel Bright", "Funk Guitar", "Octave Guitar"] },
  { id: 5, label: "Bank 5", presets: ["Sub Bass", "Reese Bass", "Pulse Bass", "Wobble Lead"] },
];
const one = (presets) => [{ id: 0, label: "Bank 0 (Main)", presets }];
const SFS0 = [
  { id: "gu", name: "GeneralUser GS.sf2", size: "30,6 MB", banks: BANKS },
  { id: "bg", name: "241-Bassguitars.SF2", size: "80 KB", banks: one(["Finger Bass", "Pick Bass"]) },
  { id: "tr", name: "463-Trumpet.sf2", size: "1,6 MB", banks: one(["Trumpet", "Muted Trumpet", "Flugelhorn", "Brass Stab"]) },
  { id: "ac", name: "ACORDEON X5D.sf2", size: "7,6 MB", banks: one(["Musette", "Bandoneon", "Accordion Soft"]) },
  { id: "av", name: "Alto Soft Vib.sf2", size: "127,2 MB", banks: one(["Soft Vibraphone", "Alto Vib Hard", "Vib Tremolo"]) },
  { id: "aw", name: "Analog Warm Pad Vip.sf2", size: "40,4 MB", banks: one(["Warm Pad", "Warm Pad Slow", "Warm Pad Wide"]) },
  { id: "b3", name: "B3 Nord 1 Leslie.sf2", size: "12,8 MB", banks: one(["B3 Slow", "B3 Fast", "B3 Rock"]) },
  { id: "dk", name: "Drum Kit Studio.sf2", size: "22,0 MB", banks: one(["Studio Kit", "Tight Kit", "Big Room Kit"]) },
  { id: "fs", name: "Flute & Sax.sf2", size: "9,1 MB", banks: one(["Flute", "Alto Sax", "Tenor Sax"]) },
  { id: "pi", name: "Grand Piano Steinway.sf2", size: "210,4 MB", banks: one(["Steinway Grand", "Steinway Soft"]) },
  { id: "rh", name: "Rhodes Mark I.sf2", size: "14,3 MB", banks: one(["Rhodes Soft", "Rhodes Bright", "Rhodes Phase"]) },
  { id: "se", name: "Strings Ensemble Pro.sf2", size: "58,0 MB", banks: one(["Strings Legato", "Strings Staccato"]) },
  { id: "ze", name: "Zebra Unknown.sf2", size: "3,2 MB", banks: one(["Texture One", "Texture Two"]) },
  { id: "sw", name: "Sine Waves Collection.sf2", size: "1,1 MB", banks: one(["Sine 440", "Sine Sweep"]) },
];

const CSS = `
.sf *{box-sizing:border-box}
.sf button{font-family:inherit}
@keyframes rise{from{opacity:0;translate:0 8px}to{opacity:1;translate:0 0}}
.rise{animation:rise .24s cubic-bezier(.2,.8,.2,1) backwards}
@keyframes slideL{from{opacity:0;translate:14px 0}to{opacity:1;translate:0 0}}
@keyframes slideR{from{opacity:0;translate:-14px 0}to{opacity:1;translate:0 0}}
@keyframes roll{from{opacity:0;translate:0 7px}to{opacity:1;translate:0 0}}
.roll{animation:roll .2s ease-out}
@keyframes pop{0%{transform:scale(1)}40%{transform:scale(1.5) rotate(-12deg)}100%{transform:scale(1)}}
@keyframes bar{0%,100%{transform:scaleY(.35)}50%{transform:scaleY(1)}}
@keyframes fill{from{width:0}to{width:100%}}
@keyframes snackIn{from{opacity:0;translate:0 14px}to{opacity:1;translate:0 0}}
@keyframes menuIn{from{opacity:0;scale:.95}to{opacity:1;scale:1}}
@keyframes sheetIn{from{opacity:0;translate:0 24px}to{opacity:1;translate:0 0}}
@keyframes breathe{0%,100%{opacity:.5}50%{opacity:1}}
.press{transition:transform .2s cubic-bezier(.3,1.5,.5,1),background .25s,border-color .25s,opacity .25s}
.press:active{transform:scale(.985)}
.btn{transition:transform .15s cubic-bezier(.3,1.5,.5,1),filter .2s,background .2s}
.btn:active{transform:scale(.95)}
.btn:hover{filter:brightness(1.08)}
.noscroll::-webkit-scrollbar{width:0;height:0}
.noscroll{scrollbar-width:none}
`;

/* ── icons ── */
function Ico({ n, size = 16, color = "currentColor", sw = 2, fill = "none" }) {
  const P = {
    search: <><circle cx="11" cy="11" r="6.5" /><path d="M20 20l-4.2-4.2" /></>,
    plus: <path d="M12 5v14M5 12h14" />,
    x: <path d="M6 6l12 12M18 6L6 18" />,
    check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
    star: <path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9L3.5 9.7l5.9-.8z" />,
    dots: <><circle cx="12" cy="5" r="1.6" fill="currentColor" /><circle cx="12" cy="12" r="1.6" fill="currentColor" /><circle cx="12" cy="19" r="1.6" fill="currentColor" /></>,
    file: <><path d="M6 3h8l4 4v14H6z" /><path d="M14 3v4h4" /></>,
    note: <><path d="M9 18V6l10-2v12" /><circle cx="7" cy="18" r="2.4" /><circle cx="17" cy="16" r="2.4" /></>,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={color} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">{P[n]}</svg>
  );
}

function Roll({ value }) {
  const prev = useRef(value);
  const [k, setK] = useState(0);
  useEffect(() => { if (prev.current !== value) { prev.current = value; setK((a) => a + 1); } }, [value]);
  return <span key={k} className={k ? "roll" : ""} style={{ display: "inline-block" }}>{value}</span>;
}

function Hl({ text, q }) {
  const s = q.trim();
  if (!s) return text;
  const i = text.toLowerCase().indexOf(s.toLowerCase());
  if (i < 0) return text;
  return <>{text.slice(0, i)}<span style={{ color: AC, background: "rgba(143,178,232,.14)", borderRadius: 3 }}>{text.slice(i, i + s.length)}</span>{text.slice(i + s.length)}</>;
}

/* ── simulated mixer behind the window ── */
function MixerSim({ onName }) {
  const Blk = ({ x, y, w, h, r = 10, bg = "linear-gradient(180deg,#2A3347,#202839)", children, style, onClick }) => (
    <div onClick={onClick} style={{ position: "absolute", left: x, top: y, width: w, height: h, borderRadius: r, background: bg, border: "1px solid rgba(255,255,255,.06)", display: "flex", alignItems: "center", justifyContent: "center", color: "#C9D1E0", fontSize: 12, fontWeight: 700, cursor: onClick ? "pointer" : "default", ...style }}>{children}</div>
  );
  const cols = ["#E08A2E", "#2FB37A", "#3C7BE0", "#8A4FD8"];
  const names = ["Grand Piano", "Motif XF Galaxy DX", "Soft Trumpet", "Accordeon"];
  return (
    <div style={{ position: "absolute", inset: 0, background: "linear-gradient(180deg,#141B27,#0F141D)" }}>
      <Blk x={11} y={9} w={117} h={40}>TRANS 0</Blk>
      <Blk x={135} y={9} w={117} h={40}>OCT 0</Blk>
      <Blk x={258} y={9} w={306} h={40} bg="#0F141D" style={{ color: "#566075" }}>- - -</Blk>
      <Blk x={570} y={9} w={40} h={40} bg="#C2587A" style={{ color: "#fff" }}>⊘</Blk>
      <Blk x={617} y={9} w={77} h={40} style={{ fontSize: 10, letterSpacing: 1 }}>SUSTAIN</Blk>
      <Blk x={842} y={9} w={38} h={40}>⚙</Blk>
      {cols.map((c, i) => {
        const x = 11 + i * 132.2;
        return (
          <div key={i} style={{ position: "absolute", left: x, top: 53, width: 126, height: 284, borderRadius: 16, background: `linear-gradient(180deg,#1B2232 30%, ${c}22)`, border: "1px solid rgba(255,255,255,.06)" }}>
            <div onClick={i === 0 ? (e) => onName(e, "track") : undefined} className={i === 0 ? "btn" : ""}
              style={{ position: "absolute", left: 4, top: 5, width: 118, height: 25, borderRadius: 9, background: "#141A26", border: "1px solid rgba(255,255,255,.08)", display: "grid", placeItems: "center", color: "#E3E8F2", fontSize: 11.5, fontWeight: 800, cursor: i === 0 ? "pointer" : "default", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{names[i]}</div>
            <div style={{ position: "absolute", left: 33, top: 36, width: 42, height: 23, borderRadius: 7, background: "#2A3347", display: "grid", placeItems: "center", fontSize: 11, fontWeight: 800 }}>S</div>
            <div style={{ position: "absolute", left: 78, top: 36, width: 42, height: 23, borderRadius: 7, background: "#2A3347", display: "grid", placeItems: "center", fontSize: 11, fontWeight: 800 }}>M</div>
            <div style={{ position: "absolute", left: 55, top: 66, width: 15, height: 184, borderRadius: 8, background: "#0C1018" }} />
            <div style={{ position: "absolute", left: 55, top: 130, width: 15, height: 120, borderRadius: 8, background: c, opacity: 0.8 }} />
            <div style={{ position: "absolute", left: 37, top: 108, width: 52, height: 40, borderRadius: 7, background: "linear-gradient(180deg,#6A7396,#3C4466)", border: "1px solid rgba(255,255,255,.15)" }} />
          </div>
        );
      })}
      <Blk x={17} y={348} w={86} h={45}>CPU 12%</Blk>
      <Blk x={108} y={348} w={76} h={45}>116 BPM</Blk>
      <Blk x={189} y={348} w={89} h={45} bg="#8FB2E8" style={{ color: "#0F1726" }}>Default</Blk>
      <Blk x={282} y={348} w={88} h={45}>Snapshot 1</Blk>
      <Blk x={375} y={348} w={88} h={45}>Snapshot 2</Blk>
      <Blk x={697} y={348} w={125} h={45}>M ▬▬▬ FX</Blk>
      <Blk x={828} y={348} w={46} h={45}>♫</Blk>
    </div>
  );
}

/* ═══════════════ the window ═══════════════ */

function SoundfontWindow({ open, variant, origin, onClose, snack }) {
  const [tab, setTab] = useState("presets");
  const [dir, setDir] = useState(1);
  const [sfs, setSfs] = useState(SFS0);
  const [activeSf, setActiveSf] = useState("gu");
  const [bank, setBank] = useState(0);
  const [activeKey, setActiveKey] = useState("gu|0|0");
  const [pending, setPending] = useState(null);
  const [cat, setCat] = useState(null);
  const [query, setQuery] = useState("");
  const [favs, setFavs] = useState(() => new Set(["gu|0|10", "rh"]));
  const [over, setOver] = useState({});            // key -> category id (manual moves)
  const [leaving, setLeaving] = useState([]);
  const [load, setLoad] = useState(null);          // { id, p, ms }
  const [preview, setPreview] = useState(null);
  const [drag, setDrag] = useState(null);          // { key, label, x, y }
  const [hover, setHover] = useState(null);
  const [menu, setMenu] = useState(null);          // { key, kind, x, y }
  const [sheet, setSheet] = useState(null);        // { key, kind, name }
  const [popId, setPopId] = useState(null);
  const [indexOn, setIndexOn] = useState(false);
  const [indexLetter, setIndexLetter] = useState(null);
  const [hold, setHold] = useState(false);

  const listRef = useRef(null);
  const rowRefs = useRef({});
  const catRefs = useRef({});
  const winRef = useRef(null);
  const g = useRef({ key: null, name: "", x: 0, y: 0, t: null, held: false, dragging: false, moved: false, kind: "preset" });
  const idxT = useRef(null);
  const loadT = useRef(null);

  const sf = sfs.find((s) => s.id === activeSf);
  const multiBank = sf.banks.length > 1;
  const isPad = variant === "pad";

  /* keep the bank valid when the soundfont changes */
  useEffect(() => { if (!sf.banks.some((b) => b.id === bank)) setBank(sf.banks[0].id); }, [activeSf]); // eslint-disable-line

  /* pad variant starts on Pads */
  useEffect(() => { if (open) { setCat(isPad ? "pads" : null); setQuery(""); } }, [open, variant]); // eslint-disable-line

  /* base items of the current tab */
  const searching = query.trim().length > 0;
  const presetsAll = useMemo(() => sf.banks.flatMap((b) => b.presets.map((p, i) => ({ key: `${sf.id}|${b.id}|${i}`, kind: "preset", name: p, num: i, bank: b.id, bankLabel: b.label }))), [sf]);
  const base = useMemo(() => {
    if (tab === "all") return [...sfs].sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" })).map((s) => ({ key: s.id, kind: "sf", name: s.name, size: s.size }));
    return searching ? presetsAll : presetsAll.filter((p) => p.bank === bank);
  }, [tab, sfs, presetsAll, bank, searching]);
  const catOf = (it) => over[it.key] || classify(it.name);

  const counts = useMemo(() => {
    const src = tab === "all" ? base : presetsAll.filter((p) => p.bank === bank);
    const c = { fav: 0, other: 0 };
    CATS.forEach((x) => { c[x.id] = 0; });
    src.forEach((it) => { const k = over[it.key] || classify(it.name); c[k] = (c[k] || 0) + 1; if (favs.has(it.key)) c.fav++; });
    return c;
  }, [tab, base, presetsAll, bank, over, favs]);

  const visCats = [...CATS, ...SPECIAL].filter((c) => counts[c.id] > 0);

  /* leave a category that became empty */
  useEffect(() => { if (cat && !(counts[cat] > 0)) setCat(null); }, [counts, cat]);

  const visible = base
    .filter((it) => !searching || it.name.toLowerCase().includes(query.trim().toLowerCase()))
    .filter((it) => {
      if (searching || !cat) return true;           // a search looks in every category and bank
      if (cat === "fav") return favs.has(it.key);
      return catOf(it) === cat;
    });
  const shown = [...visible, ...base.filter((it) => leaving.includes(it.key) && !visible.some((v) => v.key === it.key))];

  const listKey = `${tab}|${bank}|${cat}|${query}|${activeSf}`;
  const letters = useMemo(() => Array.from(new Set(visible.map((v) => (/[a-z]/i.test(v.name[0]) ? v.name[0].toUpperCase() : "#")))), [visible.length, tab]); // eslint-disable-line

  /* actions */
  const toggleFav = (key) => {
    setFavs((s) => { const n = new Set(s); n.has(key) ? n.delete(key) : n.add(key); return n; });
    setPopId(key); setTimeout(() => setPopId(null), 460); vib(8);
    if (cat === "fav" && favs.has(key)) { setLeaving((l) => [...l, key]); setTimeout(() => setLeaving((l) => l.filter((x) => x !== key)), 280); }
  };
  const moveTo = (key, to, name) => {
    const prev = over[key];
    const before = over[key] || classify(name);
    if (before === to) return;
    setOver((o) => ({ ...o, [key]: to }));
    if (cat && cat !== to && !searching) { setLeaving((l) => [...l, key]); setTimeout(() => setLeaving((l) => l.filter((x) => x !== key)), 280); }
    vib(12);
    snack(`Moved to ${catLabel(to)}`, () => setOver((o) => { const n = { ...o }; if (prev) n[key] = prev; else delete n[key]; return n; }));
  };
  const pickPreset = (it) => {
    if (it.bank !== bank) setBank(it.bank);
    setPending(it.key); vib(8);
    setTimeout(() => { setActiveKey(it.key); setPending(null); }, 430);
  };
  const startLoad = (it) => {
    if (it.key === activeSf || load) return;
    const mb = parseFloat(it.size.replace(",", ".")) * (/KB/.test(it.size) ? 0.001 : 1);
    const ms = clamp(mb * 14, 650, 1800);
    setLoad({ id: it.key, p: 0 }); vib(8);
    const t0 = performance.now();
    clearInterval(loadT.current);
    loadT.current = setInterval(() => {
      const p = clamp((performance.now() - t0) / ms, 0, 1);
      setLoad({ id: it.key, p });
      if (p >= 1) {
        clearInterval(loadT.current); setLoad(null);
        setActiveSf(it.key); setActiveKey(`${it.key}|0|0`); setBank(0); vib(12);
        snack(`Loaded ${it.name}`);
      }
    }, 40);
  };
  const cancelLoad = () => { clearInterval(loadT.current); setLoad(null); };
  const importSf = () => {
    const id = "imp" + Date.now();
    const nsf = { id, name: "Imported Strings.sf2", size: "4,2 MB", banks: one(["Imported Strings", "Imported Pad"]) };
    setSfs((s) => [nsf, ...s]); setTab("all"); setCat(null); setQuery("");
    setLoad({ id, p: 0, imp: true });
    const t0 = performance.now(); clearInterval(loadT.current);
    loadT.current = setInterval(() => {
      const p = clamp((performance.now() - t0) / 1100, 0, 1);
      setLoad({ id, p, imp: true });
      if (p >= 1) { clearInterval(loadT.current); setLoad(null); snack("Imported Strings.sf2 added"); }
    }, 40);
  };
  const switchTab = (t) => { if (t === tab) return; setDir(t === "all" ? 1 : -1); setTab(t); setCat(isPad ? "pads" : null); setQuery(""); };

  /* press gesture: tap = pick · hold = audition · hold + move = drag to a category */
  const stopAll = () => { window.removeEventListener("pointermove", onMove); window.removeEventListener("pointerup", onUp); window.removeEventListener("pointercancel", onUp); };
  function onMove(e) {
    const s = g.current;
    if (!s.key) return;
    const dx = e.clientX - s.x, dy = e.clientY - s.y;
    const dist = Math.hypot(dx, dy);
    if (!s.held && dist > 8) { clearTimeout(s.t); s.moved = true; return; }
    if (s.held && !s.dragging && dist > 12) { s.dragging = true; setPreview(null); vib(10); }
    if (s.dragging) {
      const sr = winRef.current.getBoundingClientRect();
      const sc = sr.width / WIN.w;
      setDrag({ key: s.key, label: s.name, x: (e.clientX - sr.left) / sc, y: (e.clientY - sr.top) / sc });
      let hv = null;
      Object.entries(catRefs.current).forEach(([id, el]) => {
        if (!el) return;
        const r = el.getBoundingClientRect();
        if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) hv = id;
      });
      setHover(hv);
    }
  }
  function onUp() {
    const s = g.current;
    clearTimeout(s.t);
    if (s.dragging) {
      const target = hoverRef.current;
      if (target && target !== "fav") moveTo(s.key, target, s.name);
      else if (target === "fav") toggleFav(s.key);
      setDrag(null); setHover(null);
    } else if (s.held) {
      setPreview(null);
    } else if (!s.moved && s.tap) {
      s.tap();
    }
    g.current = { ...g.current, key: null, held: false, dragging: false, moved: false };
    stopAll();
  }
  const hoverRef = useRef(null);
  hoverRef.current = hover;
  const rowDown = (e, it, tap) => {
    if (e.target.closest("[data-nopress]")) return;
    clearTimeout(g.current.t);
    g.current = {
      key: it.key, name: it.name, x: e.clientX, y: e.clientY, held: false, dragging: false, moved: false, tap,
      t: setTimeout(() => { g.current.held = true; setPreview(it.key); vib(10); }, 360),
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointercancel", onUp);
  };
  useEffect(() => {
    const el = listRef.current;
    if (!el) return;
    const stop = (e) => { if (g.current.held || g.current.dragging) e.preventDefault(); };
    el.addEventListener("touchmove", stop, { passive: false });
    return () => el.removeEventListener("touchmove", stop);
  }, [tab, open]);
  useEffect(() => () => { stopAll(); clearInterval(loadT.current); }, []); // eslint-disable-line

  /* subtle A–Z index */
  const onListScroll = () => {
    if (tab !== "all" || searching) return;
    setIndexOn(true);
    clearTimeout(idxT.current);
    idxT.current = setTimeout(() => { setIndexOn(false); setIndexLetter(null); }, 1300);
  };
  const jumpLetter = (clientY, strip) => {
    const r = strip.getBoundingClientRect();
    const f = clamp((clientY - r.top) / r.height, 0, 0.999);
    const L = letters[Math.floor(f * letters.length)];
    setIndexLetter(L); setIndexOn(true);
    clearTimeout(idxT.current);
    idxT.current = setTimeout(() => { setIndexOn(false); setIndexLetter(null); }, 1300);
    const it = visible.find((v) => (/[a-z]/i.test(v.name[0]) ? v.name[0].toUpperCase() : "#") === L);
    const el = it && rowRefs.current[it.key];
    if (el && listRef.current) listRef.current.scrollTo({ top: el.offsetTop - 4, behavior: "auto" });
  };

  /* ── rows ── */
  const renderRow = (it, i) => {
    const isPreset = it.kind === "preset";
    const active = isPreset ? it.key === activeKey : it.key === activeSf;
    const isPending = pending === it.key;
    const isLeaving = leaving.includes(it.key) && !visible.some((v) => v.key === it.key);
    const isLoad = load && load.id === it.key;
    const dragging = drag && drag.key === it.key;
    const fav = favs.has(it.key);
    const tap = isPreset ? () => pickPreset(it) : () => (isLoad ? cancelLoad() : startLoad(it));
    return (
      <div key={it.key} ref={(el) => (rowRefs.current[it.key] = el)}
        style={{ maxHeight: isLeaving ? 0 : 44, opacity: isLeaving ? 0 : 1, marginBottom: isLeaving ? 0 : 4, overflow: "hidden", transition: "max-height .26s ease, opacity .2s, margin .26s" }}>
        <div className="rise" style={{ animationDelay: `${Math.min(i, 8) * 16}ms` }}>
          <div className="press" onPointerDown={(e) => rowDown(e, it, tap)}
            style={{
              position: "relative", height: 40, borderRadius: 12, display: "flex", alignItems: "center", gap: 10, padding: "0 6px 0 12px", overflow: "hidden", cursor: "pointer", userSelect: "none", touchAction: "pan-y",
              background: active ? "linear-gradient(180deg, rgba(255,255,255,.12), rgba(255,255,255,.07))" : "linear-gradient(180deg, rgba(255,255,255,.065), rgba(255,255,255,.03))",
              border: `1px solid ${active ? "rgba(255,255,255,.17)" : "rgba(255,255,255,.06)"}`,
              opacity: dragging ? 0.3 : 1, transform: preview === it.key ? "translateY(-1px)" : "none", boxShadow: preview === it.key ? "0 6px 16px rgba(0,0,0,.4)" : "none",
            }}>
            {isPreset ? (
              <span style={{ width: 24, fontSize: 11, fontWeight: 800, color: "#6F7A90", fontFamily: MONO, textAlign: "right", flex: "none" }}>{it.num}</span>
            ) : (
              <span style={{ width: 24, display: "grid", placeItems: "center", color: "#7B859A", flex: "none" }}><Ico n="file" size={17} /></span>
            )}
            <span style={{ flex: 1, minWidth: 0, display: "flex", alignItems: "baseline", gap: 8 }}>
              <span style={{ fontSize: 14.5, fontWeight: 700, color: "#E9EDF5", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}><Hl text={it.name} q={query} /></span>
              {isPreset && (searching || !multiBank) && <span style={{ fontSize: 10.5, color: "#6F7A90", whiteSpace: "nowrap", flex: "none" }}>{searching ? `${it.bankLabel} · #${it.num}` : `Preset #${it.num}`}</span>}
            </span>
            {preview === it.key && (
              <span style={{ display: "flex", alignItems: "center", gap: 2, height: 14, flex: "none" }}>
                {[0, 1, 2, 3].map((b) => <span key={b} style={{ width: 2.5, height: 14, borderRadius: 2, background: AC, transformOrigin: "50% 100%", animation: `bar ${0.5 + b * 0.12}s ease-in-out infinite` }} />)}
              </span>
            )}
            {!isPreset && <span style={{ fontSize: 12, fontWeight: 600, color: "#7B859A", fontFamily: MONO, flex: "none", minWidth: 58, textAlign: "right" }}>{it.size}</span>}
            {active && !isLoad && (
              <span style={{ display: "inline-flex", alignItems: "center", gap: 4, padding: "3px 8px", borderRadius: 8, background: "rgba(255,255,255,.1)", color: "#C7CFDD", fontSize: 9.5, letterSpacing: 1.1, fontWeight: 800, flex: "none" }}>
                <Ico n="check" size={11} sw={3} color={AC} />{isPreset ? "ACTIVE" : "LOADED"}
              </span>
            )}
            {isLoad && (
              <span data-nopress onClick={(e) => { e.stopPropagation(); cancelLoad(); }} style={{ width: 24, height: 24, flex: "none", position: "relative", display: "grid", placeItems: "center", cursor: "pointer" }}>
                <svg width="24" height="24" viewBox="0 0 24 24" style={{ transform: "rotate(-90deg)" }}>
                  <circle cx="12" cy="12" r="9" fill="none" stroke="rgba(255,255,255,.12)" strokeWidth="2.5" />
                  <circle cx="12" cy="12" r="9" fill="none" stroke={AC} strokeWidth="2.5" strokeLinecap="round" strokeDasharray={2 * Math.PI * 9} strokeDashoffset={2 * Math.PI * 9 * (1 - load.p)} />
                </svg>
                {!load.imp && <span style={{ position: "absolute", color: "#C7CFDD" }}><Ico n="x" size={9} sw={3} /></span>}
              </span>
            )}
            <button data-nopress onClick={() => toggleFav(it.key)} className="btn" aria-label="Favorite"
              style={{ width: 28, height: 28, border: 0, background: "transparent", cursor: "pointer", display: "grid", placeItems: "center", color: fav ? "#E6BC73" : "#566075", flex: "none", animation: popId === it.key ? "pop .45s" : "none" }}>
              <Ico n="star" size={15} sw={1.8} fill={fav ? "currentColor" : "none"} />
            </button>
            <button data-nopress onClick={(e) => { const r = e.currentTarget.getBoundingClientRect(); const wr = winRef.current.getBoundingClientRect(); const sc = wr.width / WIN.w; setMenu({ key: it.key, kind: it.kind, name: it.name, x: (r.right - wr.left) / sc, y: (r.bottom - wr.top) / sc }); }} className="btn" aria-label="More"
              style={{ width: 26, height: 28, border: 0, background: "transparent", cursor: "pointer", display: "grid", placeItems: "center", color: "#6F7A90", flex: "none" }}>
              <Ico n="dots" size={15} />
            </button>
            {isPending && <span style={{ position: "absolute", left: 0, bottom: 0, height: 2, background: AC, animation: "fill .43s linear forwards" }} />}
            {isLoad && load.imp && <span style={{ position: "absolute", left: 0, bottom: 0, height: 2, width: `${load.p * 100}%`, background: AC }} />}
          </div>
        </div>
      </div>
    );
  };

  /* ── window ── */
  const ox = origin.x - WIN.x, oy = origin.y - WIN.y;
  const tabIdx = tab === "presets" ? 0 : 1;
  const catIdx = visCats.findIndex((c) => c.id === cat);

  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 50, pointerEvents: open ? "auto" : "none" }}>
      <div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.62)", backdropFilter: "blur(2.5px)", opacity: open ? 1 : 0, transition: "opacity .28s" }} />
      <div ref={winRef} style={{
        position: "absolute", left: WIN.x, top: WIN.y, width: WIN.w, height: WIN.h, borderRadius: 24, overflow: "hidden",
        background: "linear-gradient(180deg,#232C3E,#1A2230)", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 28px 70px rgba(0,0,0,.6)",
        transformOrigin: `${ox}px ${oy}px`, transform: open ? "scale(1)" : "scale(.5)", opacity: open ? 1 : 0,
        transition: open ? "transform .38s cubic-bezier(.2,.9,.25,1), opacity .22s" : "transform .24s cubic-bezier(.4,0,.8,.4), opacity .18s",
      }}>
        {isPad && <div style={{ position: "absolute", inset: 0, pointerEvents: "none", background: `radial-gradient(60% 70% at 30% 20%, rgba(143,178,232,${hold ? 0.22 : 0.09}), transparent 70%)`, transition: "background .5s", animation: hold ? "none" : "breathe 5s ease-in-out infinite" }} />}

        {/* header */}
        <div style={{ position: "absolute", left: 14, right: 14, top: 14, height: 44, display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ flex: 1, height: 42, borderRadius: 14, border: "1px solid rgba(255,255,255,.1)", background: "rgba(15,20,29,.7)", display: "flex", alignItems: "center", gap: 8, padding: "0 12px", color: "#8690A4" }}>
            <Ico n="search" size={17} />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={tab === "all" ? "Search soundfonts" : "Search presets"}
              style={{ flex: 1, minWidth: 0, background: "transparent", border: 0, outline: "none", color: "#E9EDF5", fontSize: 14.5 }} />
            {query && <button onClick={() => setQuery("")} className="btn" style={{ border: 0, background: "transparent", color: "#8690A4", display: "grid", placeItems: "center", cursor: "pointer", padding: 0 }}><Ico n="x" size={14} /></button>}
          </div>
          <button onClick={importSf} className="btn" aria-label="Import" style={{ width: 42, height: 42, borderRadius: 21, border: "1px solid rgba(255,255,255,.2)", background: "linear-gradient(180deg,#9DBDEB,#7FA3DD)", color: "#0F1726", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="plus" size={19} sw={2.6} /></button>
          <button onClick={onClose} className="btn" aria-label="Close" style={{ width: 42, height: 42, borderRadius: 21, border: "1px solid rgba(255,255,255,.12)", background: "rgba(255,255,255,.07)", color: "#E9EDF5", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={17} /></button>
        </div>

        {/* left column */}
        <div style={{ position: "absolute", left: 14, top: 68, width: 154, bottom: 14, display: "flex", flexDirection: "column" }}>
          <div style={{ fontSize: 10, letterSpacing: 1.3, fontWeight: 700, color: "#7B859A", height: 16, textTransform: "uppercase" }}>{isPad ? "Pad sound" : "Track sound"}</div>
          <div style={{ position: "relative", display: "flex", padding: 3, borderRadius: 13, background: "rgba(15,20,29,.8)", border: "1px solid rgba(255,255,255,.08)", marginBottom: 8 }}>
            <div style={{ position: "absolute", top: 3, bottom: 3, left: 3, width: "calc((100% - 6px) / 2)", borderRadius: 10, background: "linear-gradient(180deg,#38445E,#2B3650)", boxShadow: "0 2px 6px rgba(0,0,0,.4)", transform: `translateX(${tabIdx * 100}%)`, transition: "transform .32s cubic-bezier(.3,1.35,.5,1)" }} />
            {[["presets", "Presets"], ["all", "All"]].map(([k, l]) => (
              <button key={k} onClick={() => switchTab(k)} style={{ position: "relative", flex: 1, height: 28, border: 0, background: "transparent", cursor: "pointer", fontSize: 12.5, fontWeight: 800, color: tab === k ? "#F1F4FA" : "#8690A4", transition: "color .2s" }}>{l}</button>
            ))}
          </div>
          <div className="noscroll" style={{ position: "relative", flex: 1, overflowY: "auto", paddingBottom: isPad ? 44 : 0 }}>
            <div style={{ position: "relative" }}>
              <div style={{ position: "absolute", left: 0, right: 0, top: 0, height: 32, borderRadius: 10, background: "linear-gradient(180deg, rgba(255,255,255,.11), rgba(255,255,255,.06))", transform: `translateY(${Math.max(0, catIdx) * 34}px)`, opacity: catIdx >= 0 ? 1 : 0, transition: "transform .32s cubic-bezier(.3,1.3,.5,1), opacity .2s" }} />
              {visCats.map((c, i) => {
                const on = c.id === cat, hv = hover === c.id && drag;
                return (
                  <div key={c.id}>
                    {c.id === "fav" && <div style={{ height: 1, background: "rgba(255,255,255,.07)", margin: "-1px 8px 3px" }} />}
                    <div ref={(el) => (catRefs.current[c.id] = el)} onClick={() => setCat(on ? null : c.id)} className="press"
                      style={{ position: "relative", height: 32, marginBottom: 2, padding: "0 10px", borderRadius: 10, display: "flex", alignItems: "center", cursor: "pointer", background: hv ? "rgba(143,178,232,.2)" : "transparent", boxShadow: hv ? "inset 0 0 0 1px rgba(143,178,232,.55)" : "none", transform: hv ? "scale(1.03)" : "none", transition: "background .18s, box-shadow .18s, transform .2s cubic-bezier(.3,1.5,.5,1)" }}>
                      <span style={{ flex: 1, fontSize: 13, fontWeight: 700, color: on ? "#F1F4FA" : c.id === "fav" ? "#E6BC73" : "#9AA5BA", transition: "color .2s" }}>{c.label}</span>
                      <span style={{ fontSize: 11, fontWeight: 700, color: on ? "#C7CFDD" : "#6F7A90", fontFamily: MONO }}><Roll value={counts[c.id]} /></span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          {isPad && (
            <button onPointerDown={() => setHold(true)} onPointerUp={() => setHold(false)} onPointerLeave={() => setHold(false)} onPointerCancel={() => setHold(false)} className="btn"
              style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 36, borderRadius: 13, border: "1px solid rgba(255,255,255,.14)", background: hold ? "rgba(143,178,232,.22)" : "rgba(255,255,255,.06)", color: hold ? AC : "#C7CFDD", display: "flex", alignItems: "center", justifyContent: "center", gap: 8, cursor: "pointer", fontSize: 12.5, fontWeight: 800, transition: "background .25s, color .25s", touchAction: "none" }}>
              <Ico n="note" size={15} /> Hold to hear
            </button>
          )}
        </div>

        {/* right column */}
        <div style={{ position: "absolute", left: 182, right: 14, top: 68, bottom: 14, display: "flex", flexDirection: "column" }}>
          {/* banks: always shown when the soundfont has several */}
          {tab === "presets" && multiBank && (
            <div className="noscroll" style={{ display: "flex", gap: 6, overflowX: "auto", paddingBottom: 8, flex: "none", opacity: searching ? 0.4 : 1, transition: "opacity .25s" }}>
              {sf.banks.map((b) => {
                const on = b.id === bank;
                return (
                  <button key={b.id} onClick={() => { setDir(b.id > bank ? 1 : -1); setBank(b.id); setQuery(""); }} className="btn"
                    style={{ flex: "none", height: 30, padding: "0 13px", borderRadius: 15, border: `1px solid ${on ? "rgba(255,255,255,.26)" : "rgba(255,255,255,.09)"}`, background: on ? "rgba(255,255,255,.13)" : "transparent", color: on ? "#F1F4FA" : "#8690A4", fontSize: 12, fontWeight: 800, cursor: "pointer", whiteSpace: "nowrap", transition: "background .25s, border-color .25s, color .25s" }}>{b.label}</button>
                );
              })}
            </div>
          )}
          {(cat || searching) && (
            <div style={{ display: "flex", gap: 6, paddingBottom: 6, flex: "none", alignItems: "center", height: 28 }}>
              {cat && <span onClick={() => setCat(null)} className="press" style={{ cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 5, padding: "3px 6px 3px 10px", borderRadius: 10, background: "rgba(255,255,255,.08)", fontSize: 11.5, fontWeight: 700, color: "#C7CFDD" }}>{catLabel(cat)}<Ico n="x" size={11} sw={2.6} /></span>}
              {searching && <span style={{ fontSize: 11.5, color: "#7B859A" }}>{visible.length} {visible.length === 1 ? "result" : "results"} in all banks</span>}
            </div>
          )}

          <div style={{ position: "relative", flex: 1, minHeight: 0 }}>
            <div ref={listRef} onScroll={onListScroll} className="noscroll" style={{ position: "absolute", inset: 0, overflowY: "auto", paddingRight: tab === "all" ? 12 : 0 }}>
              <div key={listKey} style={{ animation: `${dir > 0 ? "slideL" : "slideR"} .26s cubic-bezier(.2,.8,.2,1)` }}>
                {shown.length === 0 && <div style={{ padding: "22px 6px", fontSize: 13.5, color: "#7B859A" }}>{searching ? `Nothing matches “${query}”.` : "Nothing here yet."}</div>}
                {shown.map((it, i) => renderRow(it, i))}
              </div>
            </div>

            {/* subtle A–Z index: only while scrolling or touching, only on the soundfonts list */}
            {tab === "all" && !searching && letters.length > 3 && (
              <div
                onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); jumpLetter(e.clientY, e.currentTarget); }}
                onPointerMove={(e) => { if (e.buttons) jumpLetter(e.clientY, e.currentTarget); }}
                style={{ position: "absolute", right: 0, top: 4, bottom: 4, width: 14, display: "flex", flexDirection: "column", justifyContent: "space-between", alignItems: "center", opacity: indexOn ? 1 : 0, transition: "opacity .3s", touchAction: "none", pointerEvents: indexOn ? "auto" : "none" }}>
                {letters.map((L) => <span key={L} style={{ fontSize: 8.5, fontWeight: 800, color: indexLetter === L ? AC : "#6F7A90", fontFamily: MONO }}>{L}</span>)}
              </div>
            )}
            {indexLetter && (
              <div style={{ position: "absolute", right: 24, top: "50%", translate: "0 -50%", width: 40, height: 40, borderRadius: 14, background: "#2B3650", border: "1px solid rgba(255,255,255,.16)", boxShadow: "0 8px 20px rgba(0,0,0,.45)", display: "grid", placeItems: "center", fontSize: 18, fontWeight: 800, color: "#F1F4FA", animation: "menuIn .14s ease-out" }}>{indexLetter}</div>
            )}
          </div>
        </div>

        {/* row menu */}
        {menu && (
          <>
            <div onClick={() => setMenu(null)} style={{ position: "absolute", inset: 0, zIndex: 20 }} />
            <div style={{ position: "absolute", left: clamp(menu.x - 170, 8, WIN.w - 178), top: Math.min(menu.y + 4, WIN.h - 112), width: 170, zIndex: 21, borderRadius: 14, padding: 5, background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 14px 34px rgba(0,0,0,.55)", animation: "menuIn .16s ease-out", transformOrigin: "top right" }}>
              {[
                [favs.has(menu.key) ? "Remove favorite" : "Add to favorites", () => { toggleFav(menu.key); setMenu(null); }],
                ["Move to…", () => { setSheet({ key: menu.key, kind: menu.kind, name: menu.name }); setMenu(null); }],
              ].map(([l, fn]) => (
                <button key={l} onClick={fn} className="btn" style={{ width: "100%", height: 34, border: 0, borderRadius: 9, background: "transparent", color: "#DCE2EE", fontSize: 13, fontWeight: 700, cursor: "pointer", textAlign: "left", padding: "0 10px" }}>{l}</button>
              ))}
            </div>
          </>
        )}

        {/* Move to… sheet */}
        {sheet && (
          <div style={{ position: "absolute", inset: 0, zIndex: 30 }}>
            <div onClick={() => setSheet(null)} style={{ position: "absolute", inset: 0, background: "rgba(6,9,14,.5)", animation: "fadeIn .2s" }} />
            <div style={{ position: "absolute", left: 14, right: 14, bottom: 14, borderRadius: 20, padding: "14px 16px", background: "linear-gradient(180deg,#2A3447,#1E2736)", border: "1px solid rgba(255,255,255,.12)", boxShadow: "0 -10px 40px rgba(0,0,0,.5)", animation: "sheetIn .26s cubic-bezier(.2,.9,.25,1)" }}>
              <div style={{ display: "flex", alignItems: "center", marginBottom: 10 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 15, fontWeight: 800, color: "#F1F4FA" }}>Move to…</div>
                  <div style={{ fontSize: 11.5, color: "#7B859A", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{sheet.name} · now in {catLabel(over[sheet.key] || classify(sheet.name))}</div>
                </div>
                <button onClick={() => setSheet(null)} className="btn" style={{ width: 30, height: 30, borderRadius: 15, border: 0, background: "rgba(255,255,255,.07)", color: "#9AA5BA", display: "grid", placeItems: "center", cursor: "pointer" }}><Ico n="x" size={14} /></button>
              </div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 7 }}>
                {[...CATS, { id: "other", label: "Other" }].map((c) => {
                  const cur = (over[sheet.key] || classify(sheet.name)) === c.id;
                  return (
                    <button key={c.id} onClick={() => { moveTo(sheet.key, c.id, sheet.name); setSheet(null); }} className="btn"
                      style={{ height: 32, padding: "0 14px", borderRadius: 16, border: `1px solid ${cur ? "rgba(143,178,232,.6)" : "rgba(255,255,255,.1)"}`, background: cur ? "rgba(143,178,232,.14)" : "rgba(255,255,255,.04)", color: cur ? AC : "#C7CFDD", fontSize: 12.5, fontWeight: 700, cursor: "pointer" }}>{c.label}</button>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* drag ghost */}
        {drag && (
          <div style={{ position: "absolute", left: drag.x, top: drag.y, translate: "-30px -20px", zIndex: 40, pointerEvents: "none", width: 230, height: 40, borderRadius: 12, padding: "0 12px", display: "flex", alignItems: "center", background: "linear-gradient(180deg,#3A4663,#2B3650)", border: "1px solid rgba(255,255,255,.22)", boxShadow: "0 16px 34px rgba(0,0,0,.55)", scale: "1.03", fontSize: 14, fontWeight: 700, color: "#F1F4FA", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{drag.label}</div>
        )}
      </div>
    </div>
  );
}

/* ═══════════════ main ═══════════════ */

export default function SoundfontsPreview() {
  const [open, setOpen] = useState(false);
  const [variant, setVariant] = useState("track");
  const [origin, setOrigin] = useState({ x: 100, y: 72 });
  const [snack, setSnack] = useState(null);
  const [vp, setVp] = useState({ w: typeof window !== "undefined" ? window.innerWidth : 900, h: typeof window !== "undefined" ? window.innerHeight : 500 });
  const snackT = useRef(null);
  const stageRef = useRef(null);

  useEffect(() => {
    const on = () => setVp({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);
  const scale = Math.max(0.3, Math.min((vp.w - 16) / W, (vp.h - 120) / H, 1.5));
  const portrait = vp.h > vp.w;

  const showSnack = (text, undo) => {
    clearTimeout(snackT.current);
    setSnack({ text, undo, key: Date.now() });
    snackT.current = setTimeout(() => setSnack(null), undo ? 4500 : 1800);
  };
  const openWin = (v, pt) => { setVariant(v); setOrigin(pt); setOpen(true); vib(8); };
  const fromName = (e) => {
    const sr = stageRef.current.getBoundingClientRect();
    const r = e.currentTarget.getBoundingClientRect();
    openWin("track", { x: (r.left + r.width / 2 - sr.left) / scale, y: (r.top + r.height / 2 - sr.top) / scale });
  };

  return (
    <div className="sf" style={{ minHeight: "100vh", background: "#080B11", color: "#E9EDF5", fontFamily: "Inter, 'Segoe UI', system-ui, sans-serif", display: "flex", flexDirection: "column", alignItems: "center", padding: "8px 8px 12px", fontVariantNumeric: "tabular-nums" }}>
      <style>{CSS}</style>
      <div style={{ width: W * scale, height: H * scale, borderRadius: 24 * scale, boxShadow: `0 0 0 ${Math.max(2, 3 * scale)}px #1B212C, 0 24px 60px rgba(0,0,0,.6)`, overflow: "hidden", position: "relative", flex: "none" }}>
        <div ref={stageRef} style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: "top left", position: "relative", overflow: "hidden", background: "#0F141D" }}>
          <MixerSim onName={fromName} />
          <div style={{ position: "absolute", inset: 0, background: "rgba(8,11,18,.0)" }} />
          <SoundfontWindow open={open} variant={variant} origin={origin} onClose={() => setOpen(false)} snack={showSnack} />
          {snack && (
            <div key={snack.key} style={{ position: "absolute", left: "50%", bottom: 8, transform: "translateX(-50%)", zIndex: 90, animation: "snackIn .3s cubic-bezier(.2,.9,.25,1)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 16, padding: "9px 16px", borderRadius: 14, background: "#222C40", border: "1px solid rgba(255,255,255,.1)", boxShadow: "0 12px 30px rgba(0,0,0,.5)", fontSize: 13.5, fontWeight: 600, color: "#E9EDF5" }}>
                <span>{snack.text}</span>
                {snack.undo && <button onClick={() => { snack.undo(); setSnack(null); }} className="btn" style={{ border: 0, background: "transparent", color: AC, fontWeight: 800, fontSize: 12.5, letterSpacing: 1, cursor: "pointer" }}>UNDO</button>}
              </div>
            </div>
          )}
        </div>
      </div>

      <div style={{ marginTop: 14, display: "flex", flexWrap: "wrap", gap: 8, justifyContent: "center", maxWidth: 760 }}>
        {[["Open · Track sound", () => openWin("track", { x: 70, y: 72 })], ["Open · Pad sound", () => openWin("pad", { x: 70, y: 375 })]].map(([l, fn]) => (
          <button key={l} onClick={fn} style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid #2A3446", background: "#121823", color: "#C9D2E2", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>{l}</button>
        ))}
      </div>
      <div style={{ marginTop: 8, fontSize: 11.5, color: "#59647A", textAlign: "center", maxWidth: 620, lineHeight: 1.55 }}>
        Tap a row to load it · hold a row to audition · hold, then move, to drag it onto a category (or use ⋮ → Move to…) · star = favorite · + imports a soundfont · the first track name also opens the window
        {portrait && <><br />Rotate your phone to landscape for a larger view.</>}
      </div>
    </div>
  );
}
