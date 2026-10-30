import puppeteer from "puppeteer-core";
import { existsSync } from "node:fs";
const chrome = ["/usr/bin/chromium","/usr/bin/chromium-browser","/usr/bin/google-chrome"].find(existsSync);
const b = await puppeteer.launch({ executablePath: chrome, headless: "new", args:["--no-sandbox","--disable-gpu"] });
const p = await b.newPage();
await p.setViewport({ width: 1500, height: 900, deviceScaleFactor: 1 });
await p.goto("http://localhost:12001/fxwin.html#effect", { waitUntil: "networkidle0" });
await new Promise(r => setTimeout(r, 1500));

const orbBox = async () => p.evaluate(() => {
  const c = document.querySelector('[data-shot="fxwin"] canvas');
  const r = c.getBoundingClientRect();
  return { x: r.x, y: r.y, w: r.width, h: r.height };
});
// rayon apparent = etendue horizontale des pixels bleutes du canvas
const measure = async (box) => {
  const buf = await p.screenshot({ clip: { x: box.x, y: box.y, width: box.w, height: box.h } });
  return buf;
};
const spread = (buf) => {
  // decode PNG via sharp-less : on passe par le DOM a la place
  return null;
};

// On mesure dans le DOM : le canvas est re-rendu a chaque frame, donc on lit
// directement les pixels par getImageData.
const readSpread = () => p.evaluate(() => {
  const c = document.querySelector('[data-shot="fxwin"] canvas');
  const ctx = c.getContext("2d");
  const { width, height } = c;
  const d = ctx.getImageData(0, 0, width, height).data;
  let minx = 1e9, maxx = -1, bright = 0;
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const i = (y * width + x) * 4;
    if (d[i + 2] > 90 && d[i + 3] > 40) { bright++; if (x < minx) minx = x; if (x > maxx) maxx = x; }
  }
  return { spread: maxx - minx, bright, w: width };
});

// trouver la poignee du potard Size : il est le premier de la liste, en haut
const knob = await p.evaluate(() => {
  const root = document.querySelector('[data-shot="fxwin"]');
  const rb = root.getBoundingClientRect();
  const all = [...root.querySelectorAll('div')].filter(e => e.style.cursor === 'ns-resize');
  const r = all[0].getBoundingClientRect();
  return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
});
console.log("Size knob at", Math.round(knob.x), Math.round(knob.y));

const before = await readSpread();
console.log("AVANT  ", JSON.stringify(before));

await p.mouse.move(knob.x, knob.y);
await p.mouse.down();
await p.mouse.move(knob.x, knob.y - 120, { steps: 12 });   // pousse Size vers le haut
await p.mouse.up();
await new Promise(r => setTimeout(r, 400));
const after = await readSpread();
console.log("APRES  ", JSON.stringify(after));

await b.close();
