// Capture la maquette FX (le JSX compilé) au pixel, sans navigateur interactif.
// Usage: node tools/fxcompare/shoot_mock.mjs <url> <out.png> [clipW clipH]
//
// La fenêtre de la maquette est posée dans une page qui peut avoir des marges :
// on mesure d'abord la boîte de l'élément racine, puis on capture EXACTEMENT
// cette boîte. Capturer le viewport entier donnerait une image avec du fond, et
// toute comparaison de cotes serait faussée par la marge.
import puppeteer from "puppeteer-core";

const [url, out, clipW, clipH] = process.argv.slice(2);
if (!url || !out) {
  console.error("usage: shoot_mock.mjs <url> <out.png> [clipW clipH]");
  process.exit(1);
}

const CHROME = process.env.CHROME_PATH ||
  "/root/.cache/puppeteer/chrome/linux-131.0.6778.204/chrome-linux64/chrome";

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: "new",
  args: ["--no-sandbox", "--disable-dev-shm-usage", "--force-device-scale-factor=2"],
});

const page = await browser.newPage();
await page.setViewport({ width: 1000, height: 520, deviceScaleFactor: 2 });
await page.goto(url, { waitUntil: "networkidle0" });
await new Promise((r) => setTimeout(r, 1400));

// La fenêtre est le conteneur dont le rapport largeur/hauteur vaut 891/411.
// On le trouve par son RAPPORT, pas par sa taille : la page applique un `scale`
// d'ajustement, donc sa largeur réelle n'est jamais exactement 891 et la
// chercher par valeur échouerait dès que la fenêtre change de taille.
const TARGET = 891 / 411;
const handle = await page.evaluateHandle((target) => {
  const cands = [...document.querySelectorAll("div")].map((e) => {
    const r = e.getBoundingClientRect();
    return { e, w: r.width, h: r.height, err: Math.abs(r.width / r.height - target) };
  }).filter((c) => c.w > 300 && c.h > 150 && c.err < 0.02);
  cands.sort((a, b) => b.w * b.h - a.w * a.h);
  return cands[0] ? cands[0].e : document.body;
}, TARGET);

const el = handle.asElement();
const box = await el.boundingBox();
await el.screenshot({ path: out });
console.log(JSON.stringify({ w: box.width, h: box.height }));
await browser.close();

