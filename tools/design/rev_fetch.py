"""Download real reverb plugin UI screenshots for design study.

Fetches the product pages of well-regarded reverb plugins, extracts image
references (src / data-src / srcset / inline url()), keeps the ones whose name
looks like a UI shot, and saves the largest candidates. Grounds the visual
decisions in shipped products instead of guesswork.
"""
import os
import re
import subprocess
from urllib.parse import urljoin

OUT = "/workspace/dl/rev2"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

PAGES = {
    "crystalline": "https://babyaud.io/crystalline",
    "crystallizer": "https://www.soundtoys.com/product/crystallizer/",
    "lohi": "https://goodhertz.com/lohi/",
    "supermassive": "https://valhalladsp.com/shop/reverb/valhalla-supermassive/",
    "bigsky": "https://www.strymon.net/product/bigsky-mx/",
    "aberrant": "https://aberrantaudio.com/",
}

KEEP = re.compile(r"(ui|gui|screen|plug-?in|product|hero|main|crystal|reverb|"
                  r"supermassive|bigsky|blackhole|panel|interface|device)", re.I)
DROP = re.compile(r"(logo|icon|avatar|favicon|badge|flag|payment|footer|"
                  r"artist|press|quote|blog|news)", re.I)
IMG = re.compile(r"\.(?:png|jpe?g|webp|avif)", re.I)


def images(html, base):
    urls = set()
    for attr in ("src", "data-src", "data-lazy-src", "data-original"):
        for m in re.findall(rf'{attr}=["\']([^"\']+)["\']', html):
            urls.add(m)
    for m in re.findall(r'srcset=["\']([^"\']+)["\']', html):
        for part in m.split(","):
            u = part.strip().split(" ")[0]
            if u:
                urls.add(u)
    for m in re.findall(r"url\(([^)]+)\)", html):
        urls.add(m.strip("\"'"))
    out = []
    for u in urls:
        if not IMG.search(u):
            continue
        full = urljoin(base, u)
        if KEEP.search(full) and not DROP.search(full):
            out.append(full.split("?")[0])
    return sorted(set(out))


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, page in PAGES.items():
        html = subprocess.run(["curl", "-sL", "--max-time", "45", "-A", UA, page],
                              capture_output=True, text=True).stdout
        urls = images(html, page)
        print(f"{name}: {len(urls)} candidates")
        for i, u in enumerate(urls[:5]):
            ext = os.path.splitext(u)[1].lower() or ".jpg"
            dest = os.path.join(OUT, f"{name}_{i}{ext}")
            subprocess.run(["curl", "-sL", "--max-time", "45", "-A", UA, "-o", dest, u])
            size = os.path.getsize(dest) if os.path.exists(dest) else 0
            print(f"   {os.path.basename(dest):24s} {size // 1024:5d} KB  {u[:80]}")


if __name__ == "__main__":
    main()
