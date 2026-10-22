// Renders app/assets/aether-fx-package/fx-window-preview.html at the reference
// size and captures one PNG per effect, so the Compose captures can be compared
// against the actual preview instead of a possibly stale PNG.
import puppeteer from "../jsxref/node_modules/puppeteer-core/lib/esm/puppeteer/puppeteer-core.js";
import { mkdirSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..", "..");
const PREVIEW = join(ROOT, "app", "assets", "aether-fx-package", "fx-window-preview.html");
const OUT = join(HERE, "out");
mkdirSync(OUT, { recursive: true });

const EFFECTS = process.argv.slice(2).map(Number);

function chromePath() {
  for (const p of ["/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/google-chrome"]) {
    if (existsSync(p)) return p;
  }
  throw new Error("no chromium");
}

const browser = await puppeteer.launch({
  executablePath: chromePath(),
  headless: true,
  args: ["--no-sandbox", "--disable-dev-shm-usage", "--force-device-scale-factor=1",
         "--hide-scrollbars", "--font-render-hinting=none"],
});
const page = await browser.newPage();
await page.setViewport({ width: 907, height: 601, deviceScaleFactor: 2 });
await page.goto("file://" + PREVIEW, { waitUntil: "networkidle0" });
await new Promise(r => setTimeout(r, 1200));

for (const i of EFFECTS) {
  const ok = await page.evaluate((id) => {
    const w = window;
    if (typeof w.addFX !== "function") return "no addFX";
    try { w.addFX(id); } catch (e) { return String(e); }
    if (typeof w.open === "function") w.open(id);
    return "ok";
  }, i);
  await new Promise(r => setTimeout(r, 900));
  const el = await page.$("#st");
  await el.screenshot({ path: join(OUT, `prev-${String(i).padStart(2, "0")}.png`) });
  console.log(i, ok);
  await page.evaluate((id) => { try { window.delFX(id); } catch (e) {} }, i);
  await new Promise(r => setTimeout(r, 300));
}
await browser.close();
