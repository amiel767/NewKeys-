// Bundles a preview JSX into a single self-contained HTML page.
// Usage: node build_preview.mjs <preview.jsx> <out.html>
// esbuild resolves React from tools/jsxref/node_modules, so the page needs no
// server-side build step and can be opened directly from the served directory.
import esbuild from "esbuild";
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, "..", "..");
const [jsxArg, outArg] = process.argv.slice(2);
const jsx = resolve(jsxArg);
const out = resolve(outArg);

const entry = `
import React from "react";
import { createRoot } from "react-dom/client";
import Preview from ${JSON.stringify(jsx)};
createRoot(document.getElementById("root")).render(React.createElement(Preview));
`;

const res = await esbuild.build({
  stdin: { contents: entry, resolveDir: HERE, loader: "jsx" },
  bundle: true,
  format: "iife",
  jsx: "automatic",
  minify: true,
  write: false,
  logLevel: "silent",
  nodePaths: [join(REPO, "tools", "jsxref", "node_modules")],
  define: { "process.env.NODE_ENV": '"production"' },
});

const code = res.outputFiles[0].text;
const html = `<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Æther Space — test de design</title>
<style>html,body{margin:0;padding:0;background:#0B0F16}*{-webkit-font-smoothing:antialiased}</style>
</head><body><div id="root"></div><script>${code}</script></body></html>`;

mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, html);
console.log("wrote", out, Math.round(html.length / 1024), "KB");
