#!/bin/bash
# Tests the phase-alignment hypothesis: all oscillators of a voice start at phase 0, so the
# discontinuities of the 5 detuned saws of a super-saw land on the same sample at note-on.
set -u
cd /workspace/project/NewKeys-
SRC=/tmp/dbg/aetherfx/fx_leads.h

run() {
  cp /tmp/dbg/orig.h "$SRC"
  python3 - "$1" <<'PY'
import sys
p = "/tmp/dbg/aetherfx/fx_leads.h"
s = open(p).read()
which = sys.argv[1]
if which == "V7":
    s = s.replace("    v.satDrive = 1.1f + (.5f + r() * .5f) * .4f;",
                  "    v.satDrive = 1.1f + (.5f + r() * .5f) * .4f;\n    for (int i = 0; i < kOsc; ++i) v.ph[i] = .5f + r() * .5f;")
elif which == "V8":
    s = s.replace("    v.satDrive = 1.1f + (.5f + r() * .5f) * .4f;",
                  "    v.satDrive = 1.1f + (.5f + r() * .5f) * .4f;\n    for (int i = 0; i < kOsc; ++i) v.ph[i] = .5f + r() * .5f;")
    s = s.replace("    auto r = [&v]() {", "    v.rng = v.rng * 2654435761u + 0x9E3779B9u;\n    auto r = [&v]() {")
elif which == "V9":
    # spread the 5 saws by detune only, phases all zero: shows the alignment is the cause
    s = s.replace("float ph[kOsc] = {0};", "float ph[kOsc] = {0};")
open(p, "w").write(s)
PY
  g++ -O2 -std=c++17 -I /tmp/dbg tools/aetherfx/leads_click_check.cpp -o /tmp/lcc_v 2>/tmp/err.txt || { echo "$2: COMPILE FAIL"; head -3 /tmp/err.txt; return; }
  echo "$2 | $(/tmp/lcc_v | grep '^super saw  ' | head -1)"
  echo "$2 | $(/tmp/lcc_v | grep '^all layers ' | head -1)"
}

echo "variant | super saw / all layers, note 60"
run V0 "V0 original           "
run V7 "V7 random start phase "
run V8 "V8 phase + per-voice rng"

cp /tmp/dbg/orig.h "$SRC"
