// Renders the reference JSX previews at the real device size (891 x 411 px) and
// captures one PNG per preview. The JSX files are the visual source of truth, so
// they must stay in the repo instead of a scratch directory that a sandbox reset
// would wipe. Run: node render.mjs
import esbuild from "esbuild";
import puppeteer from "puppeteer-core";
import { mkdirSync, writeFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const ASSETS = join(HERE, "..", "..", "app", "assets");
const OUT = join(HERE, "out");
mkdirSync(OUT, { recursive: true });

const PREVIEWS = [
  { file: "MixerPreview.jsx", name: "mixer", w: 891, h: 411 },
  { file: "DrumPadPreview_v2.jsx", name: "drumpad", w: 891, h: 411 },
  { file: "SoundfontsPreview.jsx", name: "soundfonts", w: 891, h: 411 },
  { file: "SettingsPreview.jsx", name: "settings", w: 891, h: 411 },
  { file: "SetlistsPreview.jsx", name: "setlists", w: 891, h: 411 },
];

// The previews scale the 891 x 411 device frame to fit the window
// (scale = min((w-16)/W, (h-190)/H, 1.5)). A 907 x 601 viewport makes every term
// >= 1 so the frame renders at exactly 1x, then the frame element is cropped out.
const VIEW_W = 907;
const VIEW_H = 601;

function chromePath() {
  for (const p of ["/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/google-chrome"])
    if (existsSync(p)) return p;
  throw new Error("no chromium found");
}

// A generated entry mounts the preview so the bundle owns React and the DOM setup.
async function bundle(entrySource) {
  const res = await esbuild.build({
    stdin: { contents: entrySource, resolveDir: HERE, loader: "jsx" },
    bundle: true,
    format: "iife",
    jsx: "automatic",
    loader: { ".jsx": "jsx" },
    write: false,
    logLevel: "silent",
    nodePaths: [join(HERE, "node_modules")],
    define: { "process.env.NODE_ENV": '"production"' },
  });
  return res.outputFiles[0].text;
}

const browser = await puppeteer.launch({
  executablePath: chromePath(),
  headless: "new",
  args: ["--no-sandbox", "--disable-gpu", "--force-device-scale-factor=1"],
});

for (const p of PREVIEWS) {
  const entry = `
    import React from "react";
    import { createRoot } from "react-dom/client";
    import Preview from ${JSON.stringify(join(ASSETS, p.file))};
    createRoot(document.getElementById("root")).render(React.createElement(Preview));
  `;
  const code = await bundle(entry);
  const page = await browser.newPage();
  await page.setViewport({ width: VIEW_W, height: VIEW_H, deviceScaleFactor: 1 });
  const html = `<!doctype html><html><head><meta charset="utf-8">
<style>html,body{margin:0;padding:0;background:#0B0F16;overflow:hidden}</style>
</head><body><div id="root"></div><script>${code}</script></body></html>`;
  await page.setContent(html, { waitUntil: "networkidle0" });
  await new Promise((r) => setTimeout(r, 800));
  // The device frame is the only element with width 891 and height 411; screenshot
  // its box so the surrounding page chrome and helper controls are excluded.
  let box = null;
  for (const h of await page.$$("div")) {
    const b = await h.boundingBox();
    if (b && Math.round(b.width) === 891 && Math.round(b.height) === 411) { box = b; break; }
  }
  const path = join(OUT, `${p.name}.png`);
  if (box) {
    await page.screenshot({
      path,
      clip: { x: box.x, y: box.y, width: 891, height: 411 },
    });
  } else {
    await page.screenshot({ path });
  }
  await page.close();
  console.log("rendered", p.name, box ? "clipped" : "full");
}

await browser.close();
console.log("done ->", OUT);
