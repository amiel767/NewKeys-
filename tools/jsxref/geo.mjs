import puppeteer from "puppeteer-core";
import { existsSync } from "node:fs";
const chrome = ["/usr/bin/chromium","/usr/bin/chromium-browser","/usr/bin/google-chrome"].find(existsSync);
const b = await puppeteer.launch({ executablePath: chrome, headless: "new", args:["--no-sandbox","--disable-gpu"] });
const p = await b.newPage();
await p.setViewport({ width: 1600, height: 900, deviceScaleFactor: 1 });
await p.goto("http://localhost:12001/fxwin2.html", { waitUntil: "networkidle0" });
await new Promise(r => setTimeout(r, 3000));
console.log(JSON.stringify(await p.evaluate(() => {
  const root = document.querySelector('[data-shot="fxwin"]');
  const rr = root.getBoundingClientRect();
  const rel = (e) => { const r = e.getBoundingClientRect();
    return { x: +(r.x-rr.x).toFixed(1), y: +(r.y-rr.y).toFixed(1), w: +r.width.toFixed(1), h: +r.height.toFixed(1) }; };
  // une carte : le div qui porte 'flex: 0 0 calc'
  const card = [...root.querySelectorAll('div')].find(e => (e.getAttribute('style')||'').includes('flex: 0 0 calc'));
  const row = card ? card.parentElement : null;
  const kids = row ? [...row.children] : [];
  return { fenetre: rel(root), rangee: row ? rel(row) : null,
           cartes: kids.map(rel),
           // les sous-elements d'une carte
           dansUneCarte: card ? [...card.children].map(rel) : [] };
}), null, 1));
await b.close();
