#!/usr/bin/env bash
# Rebuilds the app and restarts the render loop. Nothing else changes:
# the bridge and the page stay up, so the same URL shows the new build.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LIVEUI_DIR=${LIVEUI_DIR:-/tmp/liveui}
pids=$(pgrep -f "com.soundstage.mixer.liveui.LiveUiLoop" || true)
[ -n "$pids" ] && kill $pids 2>/dev/null
sleep 1
rm -f "$LIVEUI_DIR/screen.png"
nohup "$ROOT/tools/liveui/loop.sh" > "$LIVEUI_DIR/loop.log" 2>&1 &
for i in $(seq 1 90); do
  [ -f "$LIVEUI_DIR/screen.png" ] && break
  sleep 2
done
echo "LiveUI refreshed (render: $([ -f "$LIVEUI_DIR/screen.png" ] && stat -c%s "$LIVEUI_DIR/screen.png" || echo none) bytes)"
