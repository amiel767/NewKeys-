#!/usr/bin/env python3
"""Build the Æther window row artwork (the *real photographs*).

The rows used to show real photos of each instrument. They were produced by an
ad-hoc script whose output (`ae_row_*.webp`) was left **untracked**, so the
sandbox wipe deleted it and the rows silently fell back to vector minis. This
script is the tracked, reproducible replacement.

What it does
------------
1. Downloads the source photographs from Wikimedia Commons (each under a free
   licence, recorded in ATTRIBUTION.md next to the assets).
2. Cuts a true 3:1 window out of each (the row's artwork cell is 150x50 dp) so
   nothing is squashed, then normalises the exposure.
3. Applies a long left-side alpha dissolve (plus a short top/bottom feather), so
   the photo reads as a reflection inside the glass rather than a pasted sticker.
4. For Piano and Sax the source is already in the repo (`ae_piano_mellow.webp`
   and the `ae_sax_alto_gold.webp` cut-out), so nothing is downloaded for them.

Usage
-----
    python3 tools/aerows/build_rows.py            # regenerate into res/drawable-nodpi
    python3 tools/aerows/build_rows.py --check    # only report what exists

Network is only needed for Organ / Synth / Leads. Downloaded originals are cached
in `tools/aerows/src/` (gitignored) so a rerun does not hit the network again.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request

from PIL import Image, ImageEnhance, ImageFilter, ImageOps, ImageStat

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, "app/src/main/res/drawable-nodpi")
CACHE = os.path.join(REPO, "tools/aerows/src")

W, H = 1260, 420          # 3:1, the artwork cell's aspect at the row height
UA = "AetherKeys-asset-tool/1.0 (NewKeys-; image research)"

# Wikimedia Commons files. Every one is a free licence; see ATTRIBUTION.md.
SOURCES = {
    "organ": "File:Hammond B3 & Drawbars.jpg",
    "synth": "File:Sequential Prophet 10 Rev 4.png",
    "leads": "File:Minimoog Model D Synthesizer at SMEM Playroom.jpg",
}

# Crop window per instrument: (centre x, centre y, width fraction), plus options.
# Kept identical to the values the original photos were cut with, so the rebuilt
# assets match what the user approved.
CROPS = {
    "organ": dict(cx=0.50, cy=0.45, wfrac=0.80, target=110),
    "synth": dict(cx=0.50, cy=0.63, wfrac=0.78, target=110),
    "leads": dict(cx=0.50, cy=0.56, wfrac=0.72, target=110),
}


def smoothstep(t: float) -> float:
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def left_fade(w: int, h: int, ramp: float = 0.36, feather: float = 0.05) -> Image.Image:
    """Alpha mask: dissolve from the left, short feather top and bottom."""
    a = Image.new("L", (w, h), 255)
    px = a.load()
    fx, fy = max(1, int(w * feather)), max(1, int(h * feather))
    for x in range(w):
        ax = smoothstep(x / (ramp * w)) if x < ramp * w else 1.0
        for y in range(h):
            ay = 1.0
            if y < fy:
                ay = smoothstep(y / fy)
            elif y > h - fy:
                ay = smoothstep((h - y) / fy)
            px[x, y] = int(255 * ax * ay)
    return a.filter(ImageFilter.GaussianBlur(1.2))


def normalise(im: Image.Image, target: float, lo: float = 0.75, hi: float = 1.9) -> Image.Image:
    m = ImageStat.Stat(im).mean
    lum = m[0] * 0.3 + m[1] * 0.5 + m[2] * 0.2
    g = max(lo, min(hi, target / max(lum, 1)))
    return ImageEnhance.Contrast(ImageEnhance.Brightness(im).enhance(g)).enhance(1.10)


def commons_download(title: str, dst: str) -> str:
    """Fetch a Commons file (2000 px wide thumbnail) into the cache."""
    if os.path.exists(dst) and os.path.getsize(dst) > 10_000:
        return dst
    api = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {
            "action": "query", "format": "json", "formatversion": "2",
            "titles": title, "prop": "imageinfo",
            "iiprop": "url|size|extmetadata", "iiurlwidth": "2000",
        }
    )
    req = urllib.request.Request(api, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        d = json.load(r)
    page = d["query"]["pages"][0]
    if "imageinfo" not in page:
        raise RuntimeError(f"Commons has no imageinfo for {title!r}")
    ii = page["imageinfo"][0]
    url = ii.get("thumburl") or ii["url"]
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as r, open(dst, "wb") as f:
        f.write(r.read())
    return dst


def strip(src: str, dst_name: str, cx: float, cy: float, wfrac: float,
          flip: bool = False, target: float = 110) -> None:
    """Cut a true 3:1 window centred on (cx, cy) and fit it to the row cell."""
    im = Image.open(src).convert("RGB")
    cw = int(im.width * wfrac)
    ch = cw // 3
    if ch > im.height:
        ch, cw = im.height, im.height * 3
    x = max(0, min(im.width - cw, int(im.width * cx - cw / 2)))
    y = max(0, min(im.height - ch, int(im.height * cy - ch / 2)))
    im = im.crop((x, y, x + cw, y + ch))
    if flip:
        im = ImageOps.mirror(im)
    im = normalise(im.resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.4)), target)
    c = im.convert("RGBA")
    c.putalpha(left_fade(W, H))
    p = os.path.join(OUT, dst_name)
    c.save(p, "WEBP", quality=82, method=6)
    print(f"  {dst_name:22s} {os.path.getsize(p)//1024:3d} Ko  crop {cw}x{ch} @({x},{y})")


def sax_row(dst_name: str = "ae_row_sax.webp") -> None:
    """Sax: the repo already ships the cut-out, so re-fit it instead of downloading."""
    src = os.path.join(OUT, "ae_sax_alto_gold.webp")
    cut = Image.open(src).convert("RGBA")
    th = int(H * 0.94)
    tw = int(cut.width * th / cut.height)
    small = cut.resize((tw, th), Image.LANCZOS)
    m = ImageStat.Stat(small.convert("RGB"), small.getchannel("A")).mean
    g = max(0.8, min(2.6, 118 / max(m[0] * 0.3 + m[1] * 0.5 + m[2] * 0.2, 1)))
    body = ImageEnhance.Contrast(
        ImageEnhance.Brightness(small.convert("RGB")).enhance(g)
    ).enhance(1.08).convert("RGBA")
    body.putalpha(small.getchannel("A"))
    c = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    c.paste(body, (W - tw - int(W * 0.06), (H - th) // 2), body)
    ramp = left_fade(W, H)
    a = c.getchannel("A")
    px = Image.new("L", (W, H))
    px.putdata([min(v, r) for v, r in zip(a.get_flattened_data(), ramp.get_flattened_data())])
    c.putalpha(px)
    p = os.path.join(OUT, dst_name)
    c.save(p, "WEBP", quality=88, method=6)
    print(f"  {dst_name:22s} {os.path.getsize(p)//1024:3d} Ko  tw={tw}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="only report existing assets")
    args = ap.parse_args()

    names = [f"ae_row_{n}.webp" for n in ("organ", "synth", "leads", "piano", "sax")]
    if args.check:
        for n in names:
            p = os.path.join(OUT, n)
            state = f"{os.path.getsize(p)//1024} Ko" if os.path.exists(p) else "MANQUANT"
            print(f"  {n:22s} {state}")
        return 0

    os.makedirs(CACHE, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    print("Organ / Synth / Leads (Commons photos):")
    for key, title in SOURCES.items():
        dst = os.path.join(CACHE, key + os.path.splitext(title)[1].lower())
        commons_download(title, dst)
        strip(dst, f"ae_row_{key}.webp", **CROPS[key])

    print("Piano (repo asset, mirrored):")
    strip(os.path.join(OUT, "ae_piano_mellow.webp"), "ae_row_piano.webp",
          cx=0.55, cy=0.45, wfrac=1.00, flip=True, target=118)

    print("Sax (repo cut-out):")
    sax_row()
    return 0


if __name__ == "__main__":
    sys.exit(main())
