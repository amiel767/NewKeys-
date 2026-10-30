"""Fetch Sunday Keys help-center articles and collect their embedded screenshots.

The help center is a HelpScout docs site; article bodies live in the raw HTML and
their images are served from a CloudFront bucket. jina.ai is used as a fallback
renderer because the site rejects some plain user agents.
"""
import os
import re
import subprocess
import sys

OUT = "/workspace/dl/sk/app"
BASE = "https://support.sundaysounds.com/article/"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36"

ARTICLES = sys.argv[1:] or [
    "3140-effects",
    "3146-app-overview---what-you-see-on-screen",
    "3141-sounds",
    "3143-patches",
    "3139-patch-snapshots",
    "3135-additional-controls",
]

IMG_RE = re.compile(
    r'(?:https?:)?//[a-z0-9.\-]*(?:cloudfront|helpscout)[^\s"\'<>\\]*\.(?:jpg|jpeg|png|webp|gif)',
    re.I,
)

os.makedirs(OUT, exist_ok=True)
found = []
for slug in ARTICLES:
    url = BASE + slug
    html = subprocess.run(
        ["curl", "-sL", "--max-time", "45", "-A", UA, url],
        capture_output=True, text=True,
    ).stdout
    if len(html) < 3000:
        html = subprocess.run(
            ["curl", "-s", "--max-time", "60", "https://r.jina.ai/" + url],
            capture_output=True, text=True,
        ).stdout
    urls = {("https:" + u if u.startswith("//") else u).split("?")[0] for u in IMG_RE.findall(html)}
    # Skip theme chrome (logos, favicons, stylesheet assets).
    urls = {u for u in urls if "/docs/assets/" in u and "stylesheets" not in u and "/ico/" not in u}
    print(f"{slug}: html={len(html)} images={len(urls)}")
    found.extend(sorted(urls))

with open(os.path.join(OUT, "urls.txt"), "w") as fh:
    fh.write("\n".join(sorted(set(found))))
print("total unique:", len(set(found)))
