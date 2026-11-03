#!/usr/bin/env bash
# Source unique d'une cle LLM valide pour le proxy app.all-hands.dev.
#
# La cle du runtime expire quand le sandbox est re-provisionne : le proxy
# repond alors 401 « token_not_found_in_db ». La cle du conteneur
# (SESSION_API_KEY) sert a en redemander une fraiche, sans redemarrer.
#
# Usage :  K=$(tools/api_key.sh) || echo "pas de cle"
set -euo pipefail

sess="${SESSION_API_KEY:-}"
if [ -z "$sess" ] && [ -r /proc/1/environ ]; then
  sess=$(tr '\0' '\n' < /proc/1/environ | sed -n 's/^SESSION_API_KEY=//p')
fi

url="${OH_LLM_API_KEY_REFRESH_URL:-}"
if [ -n "$sess" ] && [ -n "$url" ]; then
  key=$(curl -fsS -m 20 -H "X-Session-API-Key: $sess" "$url" | tr -d '"' | tr -d '\n')
  if [ -n "$key" ]; then
    printf '%s\n' "$key"
    exit 0
  fi
fi

if [ -n "${OPENHANDS_API_KEY:-}" ]; then
  printf '%s\n' "$OPENHANDS_API_KEY"
  exit 0
fi

echo "aucune cle disponible (SESSION_API_KEY et OPENHANDS_API_KEY absents)" >&2
exit 1
