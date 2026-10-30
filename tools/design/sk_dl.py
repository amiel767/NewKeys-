"""Download the Sunday Keys help-center screenshots listed in urls.txt."""
import os
import subprocess

SRC = "/workspace/dl/sk/app/urls.txt"
OUT = "/workspace/dl/sk/app"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36"

os.makedirs(OUT, exist_ok=True)
urls = [u.strip() for u in open(SRC) if u.strip()]
ok = 0
for u in urls:
    name = u.rsplit("/", 1)[-1]
    dest = os.path.join(OUT, name)
    if os.path.exists(dest) and os.path.getsize(dest) > 2000:
        ok += 1
        continue
    r = subprocess.run(
        ["curl", "-sL", "--max-time", "60", "-A", UA, "-o", dest, "-w", "%{http_code}", u],
        capture_output=True, text=True,
    )
    size = os.path.getsize(dest) if os.path.exists(dest) else 0
    if r.stdout.strip() == "200" and size > 2000:
        ok += 1
    else:
        print("FAIL", r.stdout.strip(), size, name)

print(f"downloaded {ok}/{len(urls)}")
