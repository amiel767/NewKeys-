#!/usr/bin/env bash
# Rend un composant JSX en PNG, sans navigateur piloté.
#
#   tools/design/render_jsx.sh design/OrganConsolePreview.jsx /tmp/out.png [largeur] [hauteur]
#
# Pourquoi : l'agent ne voit pas les images. Ce script produit une capture, et
# tools/design/refscan.py / refmap.py / refpanels.py la mesurent au pixel.
#
# Dépendances : node, chromium, et un dossier /tmp/jsx avec react + react-dom + esbuild
#   mkdir -p /tmp/jsx && cd /tmp/jsx && npm init -y && npm i react@18 react-dom@18 esbuild
set -u
SRC="$1"; OUT="$2"; WIN_W="${3:-1040}"; WIN_H="${4:-540}"
WORK=/tmp/jsx
mkdir -p "$WORK" "$(dirname "$OUT")"
cp "$SRC" "$WORK/Organ.jsx"
cat > "$WORK/entry.jsx" <<'EOF'
import React from "react";
import { createRoot } from "react-dom/client";
import C from "./Organ.jsx";
createRoot(document.getElementById("root")).render(<C />);
EOF
cat > "$WORK/index.html" <<'EOF'
<!doctype html><html><head><meta charset="utf-8">
<style>html,body{margin:0;background:#0A0C10}</style></head>
<body><div id="root"></div><script src="bundle.js"></script></body></html>
EOF
cd "$WORK" || exit 1
./node_modules/.bin/esbuild entry.jsx --bundle --outfile=bundle.js \
  --loader:.jsx=jsx --jsx=automatic --define:process.env.NODE_ENV='"production"' >/dev/null || exit 1
pkill -f "http.server 13001" 2>/dev/null
setsid nohup python3 -m http.server 13001 --bind 127.0.0.1 >/dev/null 2>&1 &
sleep 1
chromium --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size="$WIN_W,$WIN_H" \
  --screenshot="$OUT" --virtual-time-budget=5000 \
  "http://127.0.0.1:13001/index.html" >/dev/null 2>&1
python3 - "$OUT" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
print(f"{sys.argv[1]}  {im.size[0]}x{im.size[1]}  ({im.size[0]//2}x{im.size[1]//2} a l'echelle 1)")
PY
