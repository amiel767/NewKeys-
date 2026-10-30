// Screenshots the Space design preview at the device size so the result can be
// checked without a browser. Waits for the canvas animation to settle, then
// captures the framed window and writes it next to the served page.
import puppeteer from "puppeteer-core";
import { existsSync } from "node:fs";

const URL = process.argv[2] || "http://localhost:12001/space.html";
const OUT = process.argv[3] || "/workspace/dl/pub/space_shot.png";
const WAIT = Number(process.argv[4] || 2600);

function chromePath() {
  for (const p of ["/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/google-chrome"])
    if (existsSync(p)) return p;
  throw new Error("no chromium found");
}

const browser = await puppeteer.launch({
  executablePath: chromePath(),
  headless: "new",
  args: ["--no-sandbox", "--disable-gpu", "--force-device-scale-factor=1", "--hide-scrollbars"],
});
const page = await browser.newPage();
await page.setViewport({ width: 915, height: 512, deviceScaleFactor: 2 });
await page.goto(URL, { waitUntil: "networkidle0" });
await new Promise((r) => setTimeout(r, WAIT));

// The framed device is the element whose untransformed size is 891 x 411.
let box = null;
for (const h of await page.$$("div")) {
  const b = await h.boundingBox();
  if (b && Math.round(b.width) > 850 && Math.round(b.width) < 920 &&
      Math.round(b.height) > 380 && Math.round(b.height) < 430) { box = b; break; }
}
await page.screenshot({ path: OUT, clip: box ? { x: box.x, y: box.y, width: box.width, height: box.height } : undefined });
console.log("shot ->", OUT, box ? `${Math.round(box.width)}x${Math.round(box.height)}` : "full page");
await browser.close();
