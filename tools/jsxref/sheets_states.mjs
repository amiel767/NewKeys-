// Drives the Sheets preview through its three surfaces and screenshots each.
// Uses the preview's own helper buttons (deterministic) rather than hit-testing
// card geometry, so the driver does not depend on layout.
import puppeteer from "puppeteer-core";
import { existsSync } from "node:fs";

const URL = process.argv[2] || "http://localhost:12000/sheets/";
const OUT = process.argv[3] || "/workspace/dl/pub/sheets";

function chromePath() {
  for (const p of ["/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/google-chrome"])
    if (existsSync(p)) return p;
  throw new Error("no chromium found");
}

const browser = await puppeteer.launch({
  executablePath: chromePath(), headless: "new",
  args: ["--no-sandbox", "--disable-gpu", "--force-device-scale-factor=1", "--hide-scrollbars"],
});
const page = await browser.newPage();
await page.setViewport({ width: 915, height: 560, deviceScaleFactor: 2 });
const errors = [];
page.on("pageerror", (e) => errors.push(String(e)));
await page.goto(URL, { waitUntil: "networkidle0" });
await new Promise((r) => setTimeout(r, 900));

async function frame() {
  for (const h of await page.$$("div")) {
    const b = await h.boundingBox();
    if (b && Math.abs(b.width / b.height - 891 / 411) < 0.02 && b.width > 300) return h;
  }
  return null;
}
async function status() {
  return page.evaluate(() => {
    const s = [...document.querySelectorAll("span")].find((e) => /^(Library|Editor|Floating)/.test(e.textContent.trim()));
    return s ? s.textContent.trim() : "?";
  });
}
async function shot(name) {
  const f = await frame();
  if (f) await f.screenshot({ path: `${OUT}/${name}.png` });
  console.log("shot", name, "|", await status());
}
async function helper(label) {
  const ok = await page.evaluate((lbl) => {
    const el = [...document.querySelectorAll("button, span")].find((e) => e.textContent.trim() === lbl);
    if (el) { el.click(); return true; }
    return false;
  }, label);
  if (!ok) console.log("helper not found:", label);
  await new Promise((r) => setTimeout(r, 800));
  return ok;
}

await shot("01_library");
await helper("Float a sheet");
await shot("02_floating_one");

await page.evaluate(() => {
  const s = [...document.querySelectorAll("span")].find((e) => {
    const r = e.getBoundingClientRect();
    return Math.round(r.width) === 28 && Math.round(r.height) === 28;
  });
  if (s) s.click();
});
await new Promise((r) => setTimeout(r, 600));
await shot("03_picker_open");

await helper("Many");
await page.evaluate(() => {
  const rows = [...document.querySelectorAll("div")].filter((d) => {
    const t = d.textContent.trim();
    return (t === "Chorus" || t === "Bridge") && d.getBoundingClientRect().height < 44;
  });
  rows.forEach((r) => r.click());
});
await new Promise((r) => setTimeout(r, 700));
await shot("04_floating_many");

await helper("Editor");
await shot("05_editor");

console.log("errors:", errors.length ? errors.slice(0, 5) : "none");
await browser.close();
