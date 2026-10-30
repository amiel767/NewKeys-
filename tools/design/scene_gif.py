"""Capture the live scene as a light animated GIF.

Frames are grabbed with puppeteer, cropped to the scene panel and downscaled, so
the result stays small enough to publish while still showing that the scene is
animated (and that the parameters move it).
"""
import os
import subprocess
import sys

from PIL import Image

SHOT = "/workspace/project/NewKeys-/tools/jsxref/shot_preview.mjs"
URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:12001/space.html"
OUT = sys.argv[2] if len(sys.argv) > 2 else "/workspace/dl/pub/space_scene.gif"
N = int(sys.argv[3]) if len(sys.argv) > 3 else 12
STEP = int(sys.argv[4]) if len(sys.argv) > 4 else 220

frames = []
for i in range(N):
    t = 900 + i * STEP
    raw = f"/tmp/g{i}.png"
    subprocess.run(["node", SHOT, URL, raw, str(t)], capture_output=True)
    if not os.path.exists(raw):
        continue
    im = Image.open(raw).convert("RGB")
    w, h = im.size
    # the scene panel is the left ~52% of the body
    crop = im.crop((int(w * 0.035), int(h * 0.19), int(w * 0.495), int(h * 0.835)))
    frames.append(crop.resize((300, int(300 * crop.height / crop.width)), Image.LANCZOS))

if not frames:
    raise SystemExit("no frames captured")

# A shared palette keeps the GIF small; quantise each frame against the first.
pal = frames[0].quantize(colors=128, method=Image.MEDIANCUT)
frames = [f.quantize(palette=pal, dither=Image.FLOYDSTEINBERG) for f in frames]
frames[0].save(OUT, save_all=True, append_images=frames[1:], duration=STEP, loop=0, optimize=True)
print("wrote", OUT, os.path.getsize(OUT) // 1024, "KB", len(frames), "frames")
