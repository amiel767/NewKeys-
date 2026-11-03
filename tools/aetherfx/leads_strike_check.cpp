// Prints the boundary jump at EACH of eight repeated strikes, so a single bad strike is visible
// instead of being averaged into a maximum.
//
// Build: g++ -O2 -std=c++17 -I /tmp -I <tree> tools/aetherfx/leads_strike_check.cpp -o /tmp/lsc

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

static void perStrike(const char* name) {
  const LeadPreset* pr = find(name);
  if (!pr) return;
  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, pr->v[i]);
  eng.reset();

  const int gap = static_cast<int>(0.30 * SR);
  const int total = gap * 8 + static_cast<int>(0.4 * SR);
  std::vector<float> x;
  std::vector<float> buf(static_cast<size_t>(BLK) * 2);
  std::vector<int> at;
  int done = 0, next = 0;
  while (done < total) {
    if (next < 8 && done >= next * gap) {
      if (next > 0) eng.noteOff(60);
      eng.noteOn(60, 100);
      at.push_back(done);
      next++;
    }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK && static_cast<int>(x.size()) < total; ++i) x.push_back(buf[static_cast<size_t>(i) * 2]);
    done += BLK;
  }

  std::printf("%-22s", name);
  for (size_t k = 0; k < at.size(); ++k) {
    const int a = at[k];
    float pk = 0;
    for (int i = a; i < std::min(a + static_cast<int>(0.28 * SR), static_cast<int>(x.size())); ++i) pk = std::max(pk, std::fabs(x[i]));
    const float j = (a > 0) ? std::fabs(x[a] - x[a - 1]) : 0.f;
    const float b = (pk > 1e-6f && a > 0) ? j / pk : 0.f;
    std::printf(" %6.1f%%", b * 100.f);
  }
  std::printf("\n%-22s", "  (absolute jump)");
  for (size_t k = 0; k < at.size(); ++k) {
    const int a = at[k];
    std::printf(" %6.4f", (a > 0) ? std::fabs(x[a] - x[a - 1]) : 0.f);
  }
  std::printf("\n%-22s", "  (peak 0.28s)");
  for (size_t k = 0; k < at.size(); ++k) {
    const int a = at[k];
    float pk = 0;
    for (int i = a; i < std::min(a + static_cast<int>(0.28 * SR), static_cast<int>(x.size())); ++i) pk = std::max(pk, std::fabs(x[i]));
    std::printf(" %6.4f", pk);
  }
  std::printf("\n");
}

int main() {
  const char* tag =
#ifdef ENGINE_OLD
      "OLD";
#else
      "NEW";
#endif
  std::printf("boundary jump per strike, engine %s\n", tag);
  for (const char* n : {"Mono Saw", "Praise Lead", "Kompa Lead", "Fusion Mono", "Pure Sine"}) perStrike(n);
  return 0;
}
