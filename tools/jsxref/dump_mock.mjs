// Extrait la géométrie de la maquette FX depuis le DOM, en unités de maquette
// (891 × 411). On lit la position réelle des éléments, on ne devine pas.
// Usage: node tools/jsxref/dump_mock.mjs <url>
import puppeteer from "puppeteer-core";

const url = process.argv[2];
const browser = await puppeteer.launch({
  executablePath: process.env.CHROME_PATH || "/usr/bin/chromium",
  headless: "new",
  args: ["--no-sandbox", "--disable-dev-shm-usage"],
});
const page = await browser.newPage();
await page.setViewport({ width: 1000, height: 520, deviceScaleFactor: 1 });
await page.goto(url, { waitUntil: "networkidle0" });
await new Promise((r) => setTimeout(r, 1400));

const data = await page.evaluate(() => {
  const TARGET = 891 / 411;
  const win = [...document.querySelectorAll("div")].map((e) => {
    const r = e.getBoundingClientRect();
    return { e, r, err: Math.abs(r.width / r.height - TARGET) };
  }).filter((c) => c.r.width > 300 && c.r.height > 150 && c.err < 0.02)
    .sort((a, b) => b.r.width * b.r.height - a.r.width * a.r.height)[0];
  if (!win) return { error: "fenetre introuvable" };
  const wr = win.e.getBoundingClientRect();
  const k = 891 / wr.width; // facteur unites/pixel

  const out = [];
  const walk = (el, depth) => {
    for (const c of el.children) {
      const r = c.getBoundingClientRect();
      const txt = (c.innerText || "").replace(/\s+/g, " ").trim().slice(0, 40);
      const tag = c.tagName.toLowerCase();
      const bg = getComputedStyle(c).backgroundImage.slice(0, 30);
      out.push({
        d: depth, tag,
        x: +((r.x - wr.x) * k).toFixed(1),
        y: +((r.y - wr.y) * k).toFixed(1),
        w: +(r.width * k).toFixed(1),
        h: +(r.height * k).toFixed(1),
        fs: getComputedStyle(c).fontSize,
        t: txt,
        bg: bg === "none" ? "" : bg,
      });
      if (depth < 6) walk(c, depth + 1);
    }
  };
  walk(win.e, 0);
  return { count: out.length, nodes: out, winW: +wr.width.toFixed(1), winH: +wr.height.toFixed(1), k: +k.toFixed(5) };
});

console.log(JSON.stringify(data, null, 1));
await browser.close();
