#!/bin/bash
# Gardien du serveur d'aperçu (port 12000). Le sandbox tue les processus en
# arrière-plan entre les tours ; ce script les relance.
cd /workspace/project/NewKeys- || exit 1
while true; do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 4 http://localhost:12000/fxdesign/fx_slots.html 2>/dev/null)
  if [ "$code" != "200" ]; then
    pkill -f "tools/serve.py" 2>/dev/null
    setsid nohup python3 tools/serve.py > /tmp/serve.log 2>&1 < /dev/null &
    sleep 2
  fi
  sleep 10
done
