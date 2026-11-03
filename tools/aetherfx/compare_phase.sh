#!/bin/bash
# Compares three start-phase policies for a mono retrigger, measured on the real presets.
# The metric is the jump exactly at the note-on sample, which is where a repeated note clicks.
set -u
cd /workspace/project/NewKeys-
SRC=app/src/main/cpp/aetherfx/fx_leads.h
cp "$SRC" /tmp/fx_leads_current.h

measure() {
  g++ -O2 -std=c++17 -I /tmp -I app/src/main/cpp tools/aetherfx/leads_presets_check.cpp -o /tmp/lpc_v 2>/tmp/err.txt || { echo "$1: COMPILE FAIL"; head -3 /tmp/err.txt; return; }
  /tmp/lpc_v > /tmp/v.txt
  python3 -c "
import re,sys
v={}
for line in open('/tmp/v.txt'):
    m=re.match(r'^(.*?)\s+(-?\d+\.\d)%\s+(-?\d+\.\d)%\s+(\d+\.\d+)\s*\$', line)
    if m: v[m.group(1).strip()]=float(m.group(2))
print('$1 | mean %5.1f%%  max %6.1f%%  (%s)' % (sum(v.values())/len(v), max(v.values()), max(v,key=v.get)))
"
}

# Policy A: fresh phases on every retrigger (current).
cp /tmp/fx_leads_current.h "$SRC"
measure "A fresh phases   "

# Policy B: inherit the phase from the tail on a mono retrigger.
cp /tmp/fx_leads_current.h "$SRC"
python3 - <<'PY'
p = "app/src/main/cpp/aetherfx/fx_leads.h"
s = open(p).read()
s = s.replace("v.stage = 1; v.envT = 0.f; seed(v, !wasOn || sounding);", "v.stage = 1; v.envT = 0.f; seed(v, !wasOn);")
open(p, "w").write(s)
PY
measure "B inherit phase  "

# Policy C: no phase spread at all, i.e. the original behaviour, as a reference point.
cp /tmp/fx_leads_current.h "$SRC"
python3 - <<'PY'
p = "app/src/main/cpp/aetherfx/fx_leads.h"
s = open(p).read()
s = s.replace("""    if (freshPhase) {
      for (int i = 0; i < kOsc; ++i) v.ph[i] = (static_cast<float>(i) + r()) / static_cast<float>(kOsc);
    }""", "")
open(p, "w").write(s)
PY
measure "C no spread      "

cp /tmp/fx_leads_current.h "$SRC"
