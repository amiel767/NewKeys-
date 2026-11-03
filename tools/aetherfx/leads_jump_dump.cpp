// Dumps the biggest sample-to-sample jumps of the first 12 ms, with their sample index, so the shape
// of the note-on transient is visible rather than inferred.
// Build: g++ -O2 -std=c++17 -I <tree> tools/aetherfx/leads_jump_dump.cpp -o /tmp/ljd
#include <cmath>
#include <cstdio>
#include <vector>

#include "aetherfx/fx_leads.h"

using namespace aetherfx;
static constexpr double SR = 48000.0;
static constexpr int BLK = 256;

static void run(const char* label) {
  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, 0.5f);
  eng.setNorm(0, 0.f); eng.setNorm(1, 0.f); eng.setNorm(2, 0.f); eng.setNorm(3, 1.f); eng.setNorm(4, 0.f);
  eng.setNorm(10, 0.5f); eng.setNorm(11, 0.15f);
  eng.setNorm(12, 0.02f); eng.setNorm(13, 0.f); eng.setNorm(14, 0.45f); eng.setNorm(15, 0.8f); eng.setNorm(16, 0.3f);
  eng.setNorm(17, 0.f); eng.setNorm(18, 0.85f); eng.setNorm(19, 0.15f); eng.setNorm(20, 0.5f);
  eng.setNorm(21, 0.f); eng.setNorm(22, 0.f); eng.setNorm(23, 0.f); eng.setNorm(24, 0.f); eng.setNorm(25, 0.f);
  eng.setNorm(26, 0.f); eng.setNorm(27, 0.5f); eng.setNorm(28, 0.f); eng.setNorm(29, 0.f); eng.setNorm(30, 0.f);
  eng.setNorm(31, 0.8f); eng.setNorm(32, 0.5f);
  eng.reset();

  const int n = static_cast<int>(0.012 * SR);
  std::vector<float> x;
  std::vector<float> buf(static_cast<size_t>(BLK) * 2);
  bool played = false;
  while (static_cast<int>(x.size()) < n) {
    if (!played) { eng.noteOn(60, 100); played = true; }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK; ++i) x.push_back(buf[static_cast<size_t>(i) * 2]);
  }

  // Top jumps in the window.
  std::vector<std::pair<float, int>> jumps;
  for (int i = 0; i + 1 < n; ++i) jumps.push_back({std::fabs(x[i + 1] - x[i]), i});
  std::sort(jumps.begin(), jumps.end(), [](auto& a, auto& b) { return a.first > b.first; });

  std::printf("%s: top jumps in the first 12 ms\n", label);
  for (int k = 0; k < 6; ++k) {
    const int i = jumps[static_cast<size_t>(k)].second;
    std::printf("   jump %.5f at sample %4d (%6.3f ms)   x[%d..%d] = %.5f -> %.5f\n",
                jumps[static_cast<size_t>(k)].first, i, i * 1000.0 / SR, i, i + 1, x[i], x[i + 1]);
  }
  std::printf("\n");
}

int main() {
  run("V0 original");
  return 0;
}
