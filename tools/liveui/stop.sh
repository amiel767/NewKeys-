#!/usr/bin/env bash
# Stops the LiveUI preview (render loop + bridge).
set -u
stop_one() {
  local pids
  pids=$(pgrep -f "$1" || true)
  [ -n "$pids" ] && kill $pids 2>/dev/null
}
stop_one "com.soundstage.mixer.liveui.LiveUiLoop"
stop_one "tools/liveui/bridge.py"
echo "LiveUI stopped."
