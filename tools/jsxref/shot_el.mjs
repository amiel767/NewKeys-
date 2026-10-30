// Captures one element of a preview page, by data-shot attribute.
// The element is located through the DOM (not by guessing a size), so a page
// that scales its frame still yields an exact, untruncated crop.
// Usage: node shot_el.mjs <url> <data-shot> <out.png> [waitMs]
import puppeteer from "puppeteer-core";
import { existsSync } from "node:fs";

const [url, key, out, waitMs] = process.argv.slice(2);
const WAIT = Number(waitMs || 2200);

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
await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 });
await page.goto(url, { waitUntil: "networkidle0" });
await new Promise((r) => setTimeout(r, WAIT));

const el = await page.$(`[data-shot="${key}"]`);
if (!el) throw new Error(`no element with data-shot=${key}`);
const box = await el.boundingBox();
await page.screenshot({ path: out, clip: { x: box.x, y: box.y, width: box.width, height: box.height } });
console.log("shot", key, "->", out, `${Math.round(box.width)}x${Math.round(box.height)}`);
await browser.close();
