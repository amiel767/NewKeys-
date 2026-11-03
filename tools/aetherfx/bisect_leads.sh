#!/bin/bash
# Bisects the note-on discontinuity by neutralising one hypothesis at a time.
# Prints only the super-saw line, so the table stays small.
set -u
cd /workspace/project/NewKeys-

SRC=/tmp/dbg/aetherfx/fx_leads.h
cp /tmp/dbg/orig.h "$SRC"

run() {
  g++ -O2 -std=c++17 -I /tmp/dbg tools/aetherfx/leads_click_check.cpp -o /tmp/lcc_v 2>/tmp/err.txt || { echo "$1: COMPILE FAIL"; head -3 /tmp/err.txt; return; }
  line=$(/tmp/lcc_v | grep "^super saw  " | head -1)
  echo "$1 | $line"
}

echo "variant | super saw note 60"
run "V0 original"

# V1: reset the filter integrator state at note-on.
cp /tmp/dbg/orig.h "$SRC"
sed -i 's|      seed(v);\n|&|' "$SRC"
python3 - <<'PY'
p="/tmp/dbg/aetherfx/fx_leads.h"
s=open(p).read()
s=s.replace("      seed(v);\n      return;","      seed(v); v.ic[0] = 0.f; v.ic[1] = 0.f;\n      return;")
open(p,"w").write(s)
PY
run "V1 reset filter state at note-on"

# V2: remove the anti-click fade entirely (the sum reaches the saturator at full amplitude).
cp /tmp/dbg/orig.h "$SRC"
python3 - <<'PY'
p="/tmp/dbg/aetherfx/fx_leads.h"
s=open(p).read()
s=s.replace("sum = fastTanh(sum * .55f * v.satDrive * fade);","sum = fastTanh(sum * .55f * v.satDrive);")
open(p,"w").write(s)
PY
run "V2 no fade before saturator"

# V3: much longer fade (the fade ends exactly when the stage flips).
cp /tmp/dbg/orig.h "$SRC"
python3 - <<'PY'
p="/tmp/dbg/aetherfx/fx_leads.h"
s=open(p).read()
s=s.replace("const float fade = v.stage == 1 ? std::min(1.f, v.envT / std::max(1e-4f, Av)) : 1.f;",
            "const float fade = v.stage == 1 ? std::min(1.f, v.envT / std::max(1e-4f, Av * 4.f)) : 1.f;")
open(p,"w").write(s)
PY
run "V3 fade 4x longer"

# V4: no saturation at all.
cp /tmp/dbg/orig.h "$SRC"
python3 - <<'PY'
p="/tmp/dbg/aetherfx/fx_leads.h"
s=open(p).read()
s=s.replace("sum = fastTanh(sum * .55f * v.satDrive * fade);","sum = sum * .55f * v.satDrive * fade;")
open(p,"w").write(s)
PY
run "V4 no saturator"

# V5: no per-voice envelope timing jitter (varA / varD fixed at 1).
cp /tmp/dbg/orig.h "$SRC"
python3 - <<'PY'
p="/tmp/dbg/aetherfx/fx_leads.h"
s=open(p).read()
s=s.replace("const float Av = A * v.varA, Dv = D * v.varD;","const float Av = A, Dv = D;")
open(p,"w").write(s)
PY
run "V5 no envelope jitter"

# V6: saturator drive fixed (no per-voice drive jitter) AND state reset.
cp /tmp/dbg/orig.h "$SRC"
python3 - <<'PY'
p="/tmp/dbg/aetherfx/fx_leads.h"
s=open(p).read()
s=s.replace("      seed(v);\n      return;","      seed(v); v.ic[0] = 0.f; v.ic[1] = 0.f;\n      return;")
s=s.replace("sum = fastTanh(sum * .55f * v.satDrive * fade);","sum = fastTanh(sum * .55f * v.satDrive);")
open(p,"w").write(s)
PY
run "V6 reset state + no fade"

cp /tmp/dbg/orig.h "$SRC"
