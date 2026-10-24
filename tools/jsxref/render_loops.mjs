// Renders the LiveLooper reference (LoopsPreview.jsx) at its native 891x411 canvas.
import esbuild from "esbuild";
import puppeteer from "puppeteer-core";
import { mkdirSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ASSETS = join(HERE, "..", "..", "app", "assets");
const OUT = join(HERE, "out");
mkdirSync(OUT, { recursive: true });

function chromePath() {
  for (const p of ["/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/google-chrome"])
    if (existsSync(p)) return p;
  throw new Error("no chromium found");
}

const entry = `
  import React from "react";
  import { createRoot } from "react-dom/client";
  import Preview from ${JSON.stringify(join(ASSETS, "LoopsPreview.jsx"))};
  createRoot(document.getElementById("root")).render(React.createElement(Preview));
`;
const res = await esbuild.build({
  stdin: { contents: entry, resolveDir: HERE, loader: "jsx" },
  bundle: true, format: "iife", jsx: "automatic",
  loader: { ".jsx": "jsx" }, write: false, logLevel: "silent",
  nodePaths: [join(HERE, "node_modules")],
  define: { "process.env.NODE_ENV": '"production"' },
});
const code = res.outputFiles[0].text;

const browser = await puppeteer.launch({
  executablePath: chromePath(), headless: "new",
  args: ["--no-sandbox", "--disable-gpu", "--force-device-scale-factor=1"],
});
const page = await browser.newPage();
await page.setViewport({ width: 907, height: 601, deviceScaleFactor: 1 });
const html = `<!doctype html><html><head><meta charset="utf-8">
<style>html,body{margin:0;padding:0;background:#0B0F16;overflow:hidden}</style>
</head><body><div id="root"></div><script>${code}</script></body></html>`;
await page.setContent(html, { waitUntil: "networkidle0" });
await new Promise((r) => setTimeout(r, 1500));
let box = null;
for (const h of await page.$$("div")) {
  const b = await h.boundingBox();
  if (b && Math.round(b.width) === 891 && Math.round(b.height) === 411) { box = b; break; }
}
const path = join(OUT, "loops.png");
await page.screenshot({ path, clip: box ? { x: box.x, y: box.y, width: 891, height: 411 } : undefined });
console.log("rendered", box ? "clipped 891x411" : "full page");
await browser.close();
