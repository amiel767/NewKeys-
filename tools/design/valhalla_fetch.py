"""Download every Valhalla DSP product image and report which are real UIs.

`rev_fetch.py` only keeps the first five candidates per page, which on the
Valhalla site are shop thumbnails. The plugin interfaces are further down the
page, so this walks the full image list and reports the pixel size of each
download: a real UI shot is wide (>= 900 px) and large (> 60 KB).
"""
from __future__ import annotations

import os
import re
import subprocess
from urllib.parse import urljoin

from PIL import Image

OUT = "/workspace/dl/valhalla"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

PAGES = {
    "room": "https://valhalladsp.com/shop/reverb/valhalla-room/",
    "vintageverb": "https://valhalladsp.com/shop/reverb/valhalla-vintage-verb/",
    "plate": "https://valhalladsp.com/shop/reverb/valhalla-plate/",
    "shimmer": "https://valhalladsp.com/shop/reverb/valhalla-shimmer/",
    "supermassive": "https://valhalladsp.com/shop/reverb/valhalla-supermassive/",
    "delay": "https://valhalladsp.com/shop/delay/valhalla-delay/",
    "ubermod": "https://valhalladsp.com/shop/modulation/valhalla-uber-mod/",
    "room_manual": "https://valhalladsp.com/wp-content/uploads/",
}

IMG = re.compile(r"\.(?:png|jpe?g|webp|avif)$", re.I)
DROP = re.compile(r"(logo|icon|avatar|favicon|badge|flag|payment|footer|"
                  r"artist|press|quote|blog|news|cart|star|arrow)", re.I)


def image_urls(html: str, base: str) -> list[str]:
    raw: set[str] = set()
    for attr in ("src", "data-src", "data-lazy-src", "data-original"):
        raw.update(re.findall(rf'{attr}=["\']([^"\']+)["\']', html))
    for m in re.findall(r'srcset=["\']([^"\']+)["\']', html):
        for part in m.split(","):
            u = part.strip().split(" ")[0]
            if u:
                raw.add(u)
    out = []
    for u in raw:
        full = urljoin(base, u.strip().strip("\"'")).split("?")[0]
        if IMG.search(full) and not DROP.search(full):
            out.append(full)
    return sorted(set(out))


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    for name, page in PAGES.items():
        if name == "room_manual":
            continue
        html = subprocess.run(["curl", "-sL", "--max-time", "45", "-A", UA, page],
                              capture_output=True, text=True).stdout
        urls = image_urls(html, page)
        print(f"\n### {name}: {len(urls)} images")
        for i, u in enumerate(urls):
            ext = os.path.splitext(u)[1].lower() or ".jpg"
            dest = os.path.join(OUT, f"{name}_{i:02d}{ext}")
            if not (os.path.exists(dest) and os.path.getsize(dest) > 1000):
                subprocess.run(["curl", "-sL", "--max-time", "45", "-A", UA,
                                "-o", dest, u], capture_output=True)
            if not os.path.exists(dest):
                continue
            kb = os.path.getsize(dest) // 1024
            try:
                w, h = Image.open(dest).size
            except Exception:
                w = h = 0
            tag = "UI?" if (w >= 900 and kb >= 60) else "   "
            print(f"  {tag} {os.path.basename(dest):26s} {kb:5d} KB {w:5d}x{h:<5d} {u[-60:]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
