import puppeteer from "puppeteer-core";
import { existsSync } from "node:fs";
const chrome = ["/usr/bin/chromium","/usr/bin/chromium-browser","/usr/bin/google-chrome"].find(existsSync);
const b = await puppeteer.launch({ executablePath: chrome, headless: "new", args:["--no-sandbox","--disable-gpu"] });
const p = await b.newPage();
await p.setViewport({ width: 1500, height: 900, deviceScaleFactor: 1 });
await p.goto("http://localhost:12001/fxwin.html#effect", { waitUntil: "networkidle0" });
await new Promise(r => setTimeout(r, 1200));

const knobAt = (i) => p.evaluate((idx) => {
  const root = document.querySelector('[data-shot="fxwin"]');
  const all = [...root.querySelectorAll('div')].filter(e => e.style.cursor === 'ns-resize');
  const r = all[idx].getBoundingClientRect();
  return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
}, i);
// valeur affichee sous le potard (2e ligne apres le nom)
const knobValue = (i) => p.evaluate((idx) => {
  const root = document.querySelector('[data-shot="fxwin"]');
  const all = [...root.querySelectorAll('div')].filter(e => e.style.cursor === 'ns-resize');
  const box = all[idx].parentElement;
  const t = [...box.querySelectorAll('div')].map(e => e.textContent.trim()).filter(Boolean);
  return t[t.length - 1];
}, i);

// amplitude de l'onde = nombre de pixels tres lumineux, echantillonne 14 fois sur 2,8 s
const waveTrace = async () => {
  const out = [];
  for (let k = 0; k < 14; k++) {
    out.push(await p.evaluate(() => {
      const c = document.querySelector('[data-shot="fxwin"] canvas');
      const d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data;
      let n = 0; for (let i = 3; i < d.length; i += 4) if (d[i] > 150) n++;
      return n;
    }));
    await new Promise(r => setTimeout(r, 200));
  }
  return out;
};
const stats = (a) => {
  const m = a.reduce((x, y) => x + y, 0) / a.length;
  const v = a.reduce((x, y) => x + (y - m) ** 2, 0) / a.length;
  return { moy: Math.round(m), ecart: Math.round(Math.sqrt(v)), max: Math.max(...a) };
};

const v0 = await knobValue(1);
const short = stats(await waveTrace());

const k = await knobAt(1);
await p.mouse.move(k.x, k.y); await p.mouse.down();
await p.mouse.move(k.x, k.y - 130, { steps: 14 }); await p.mouse.up();
await new Promise(r => setTimeout(r, 600));
const v1 = await knobValue(1);
const long = stats(await waveTrace());

console.log("Decay affiche :", v0, "->", v1);
console.log("Decay court :", JSON.stringify(short));
console.log("Decay long  :", JSON.stringify(long));
console.log("l'onde occupe plus de surface quand Decay monte :",
            long.moy > short.moy ? "OUI" : "NON");
await b.close();
