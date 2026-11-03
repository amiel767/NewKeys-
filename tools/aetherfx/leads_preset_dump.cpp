// Dumps the largest sample-to-sample jumps around a note-on for a REAL app preset, so the shape of
// the transient is visible instead of inferred. The preset bench reported a large excess on presets
// the synthetic tests called clean, so the cause is something the synthetic presets do not exercise.
//
// Build:
//   python3 tools/aetherfx/extract_leads_presets.py > /tmp/leads_presets.h
//   g++ -O2 -std=c++17 -I /tmp -I <tree> tools/aetherfx/leads_preset_dump.cpp -o /tmp/lpd

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

#include "leads_presets.h"
#include "aetherfx/fx_leads.h"

using namespace aetherfx;
static constexpr double SR = 48000.0;
static constexpr int BLK = 256;

static const LeadPreset* find(const char* name) {
  for (int i = 0; i < kLeadPresetCount; ++i)
    if (std::strcmp(kLeadPresets[i].name, name) == 0) return &kLeadPresets[i];
  return nullptr;
}

static void dump(const char* name, int note, int strike) {
  const LeadPreset* pr = find(name);
  if (!pr) { std::printf("preset %s not found\n", name); return; }

  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, pr->v[i]);
  eng.reset();

  const int gap = static_cast<int>(0.30 * SR);
  const int total = gap * (strike + 1) + static_cast<int>(0.4 * SR);
  std::vector<float> x;
  std::vector<float> buf(static_cast<size_t>(BLK) * 2);
  int done = 0, next = 0;
  while (done < total) {
    if (next <= strike && done >= next * gap) {
      if (next > 0) eng.noteOff(note);
      eng.noteOn(note, 100);
      next++;
    }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK && static_cast<int>(x.size()) < total; ++i) x.push_back(buf[static_cast<size_t>(i) * 2]);
    done += BLK;
  }

  const int a = strike * gap;
  const int n = static_cast<int>(0.012 * SR);
  const int mode = static_cast<int>(std::lround(pr->v[28] * 2.f));
  std::printf("\n--- %s, strike %d (mode %d, %s) ---\n", name, strike + 1, mode,
              mode == 0 ? "poly" : (mode == 1 ? "mono" : "legato"));
  std::printf("   samples around the note-on (index -8 .. +8):\n   ");
  for (int i = a - 8; i <= a + 8; ++i) std::printf(" %8.5f", x[i]);
  std::printf("\n   boundary jump = |x[a]-x[a-1]| = %.5f\n", std::fabs(x[a] - x[a - 1]));
  std::vector<std::pair<float, int>> jumps;
  for (int i = a; i + 1 < a + n; ++i) jumps.push_back({std::fabs(x[i + 1] - x[i]), i});
  std::sort(jumps.begin(), jumps.end(), [](auto& p, auto& q) { return p.first > q.first; });
  for (int k = 0; k < 3; ++k) {
    const int i = jumps[static_cast<size_t>(k)].second;
    std::printf("   jump %.5f at %+7.3f ms  x[%d]=%.5f -> x[%d]=%.5f\n",
                jumps[static_cast<size_t>(k)].first, (i - a) * 1000.0 / SR, i, x[i], i + 1, x[i + 1]);
  }
}

int main() {
  dump("Pure Sine", 60, 0);
  dump("Pure Sine", 60, 6);
  dump("Air Sine", 60, 1);
  dump("Mono Saw", 60, 7);
  dump("Gospel Supersaw", 60, 2);
  dump("Vintage Poly-Lead", 60, 4);
  return 0;
}
