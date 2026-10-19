#!/usr/bin/env bash
# Starts (or restarts) the LiveUI preview: render loop + HTTP bridge.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export LIVEUI_DIR=${LIVEUI_DIR:-/tmp/liveui}
export LIVEUI_PORT=${LIVEUI_PORT:-12000}
mkdir -p "$LIVEUI_DIR"

stop_one() { # $1 = pattern
  local pids
  pids=$(pgrep -f "$1" || true)
  [ -n "$pids" ] && kill $pids 2>/dev/null
}

stop_one "com.soundstage.mixer.liveui.LiveUiLoop"
stop_one "tools/liveui/bridge.py"
sleep 1

rm -f "$LIVEUI_DIR/cmd.json"
nohup "$ROOT/tools/liveui/loop.sh" > "$LIVEUI_DIR/loop.log" 2>&1 &
nohup python3 "$ROOT/tools/liveui/bridge.py" > "$LIVEUI_DIR/bridge.log" 2>&1 &
sleep 3

for i in $(seq 1 90); do
  [ -f "$LIVEUI_DIR/screen.png" ] && break
  sleep 2
done
if [ -f "$LIVEUI_DIR/screen.png" ]; then
  echo "LiveUI up on port $LIVEUI_PORT (render: $(stat -c%s "$LIVEUI_DIR/screen.png") bytes)"
else
  echo "LiveUI starting — no render yet, see $LIVEUI_DIR/loop.log"
fi
