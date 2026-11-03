// Measures the RELEASE, not the attack.
//
// The repeated-note bench showed a second transient that a single-note test cannot see: it sits at
// the note-off, not the note-on. The suspected cause is that noteOff sets the release stage without
// clearing envT, and envT accumulates without bound during the sustain stage. The release stage then
// sees an envT already past R and terminates the voice on its first sample.
//
// This bench holds a note, releases it, and measures the jump at the release against the waveform's
// own steady-state movement.
//
// Build: g++ -O2 -std=c++17 -I <tree> tools/aetherfx/leads_release_check.cpp -o /tmp/lrc

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

#include "aetherfx/fx_leads.h"

using namespace aetherfx;
static constexpr double SR = 48000.0;
static constexpr int BLK = 256;

static float worstJump(const std::vector<float>& x, int from, int to) {
  float w = 0.f;
  for (int i = from; i + 1 < to && i + 1 < static_cast<int>(x.size()); ++i) w = std::max(w, std::fabs(x[i + 1] - x[i]));
  return w;
}

static float peakOf(const std::vector<float>& x, int from, int to) {
  float p = 0.f;
  for (int i = from; i < to && i < static_cast<int>(x.size()); ++i) p = std::max(p, std::fabs(x[i]));
  return p;
}

// holdSec: how long the key stays down before the release. A short hold is the control: if the click
// only appears on long holds, that is the signature of an accumulated envT, not of the ramp itself.
static void report(const char* label, float layers[5], float rel, float holdSec) {
  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, 0.5f);
  for (int i = 0; i < 5; ++i) eng.setNorm(i, layers[i]);
  eng.setNorm(10, 0.5f); eng.setNorm(11, 0.15f);
  eng.setNorm(12, 0.02f); eng.setNorm(13, 0.f); eng.setNorm(14, 0.45f); eng.setNorm(15, 0.8f); eng.setNorm(16, rel);
  eng.setNorm(17, 0.f); eng.setNorm(18, 0.85f); eng.setNorm(19, 0.15f); eng.setNorm(20, 0.5f);
  for (int i = 21; i <= 26; ++i) eng.setNorm(i, 0.f);
  eng.setNorm(27, 0.5f); eng.setNorm(28, 0.f); eng.setNorm(29, 0.f); eng.setNorm(30, 0.f);
  eng.setNorm(31, 0.8f); eng.setNorm(32, 0.5f);
  eng.reset();

  const int releaseAt = static_cast<int>(holdSec * SR);
  const int total = releaseAt + static_cast<int>(0.5 * SR);
  std::vector<float> x;
  std::vector<float> buf(static_cast<size_t>(BLK) * 2);

  int done = 0;
  bool on = false, off = false;
  while (done < total) {
    if (!on) { eng.noteOn(60, 100); on = true; }
    if (!off && done >= releaseAt) { eng.noteOff(60); off = true; }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK && static_cast<int>(x.size()) < total; ++i) x.push_back(buf[static_cast<size_t>(i) * 2]);
    done += BLK;
  }

  // The release fires at the first block boundary at or after releaseAt.
  const int ra = ((releaseAt + BLK - 1) / BLK) * BLK;
  const float pre = peakOf(x, ra - static_cast<int>(0.05 * SR), ra);
  const float sus = worstJump(x, ra - static_cast<int>(0.05 * SR), ra);
  // The window must START one sample before the release: the jump that matters is the one from the
  // last sounding sample to the first released one, and a window opening at `ra` steps over it.
  const float jump = worstJump(x, ra - 1, ra + static_cast<int>(0.02 * SR));
  const float boundary = std::fabs(x[ra] - x[ra - 1]);
  const float excess = (jump - sus) / std::max(pre, 1e-6f);

  std::printf("%-26s hold %4.2fs | level before %.4f | boundary %.5f | worst %.5f | steady %.5f | excess %+6.1f%%  %s\n",
              label, holdSec, pre, boundary, jump, sus, excess * 100.f,
              excess > 0.25f ? "<== CUT" : "ok");
}

int main() {
  std::printf("=== Aether Leads: release discontinuity ===\n\n");
  float sine[5] = {1.f, 0.f, 0.f, 0.f, 0.f};
  float supersaw[5] = {0.f, 0.f, 0.f, 1.f, 0.f};
  float all[5] = {0.6f, 0.5f, 0.5f, 1.f, 0.5f};

  std::printf("--- hold length: a stale envT only bites once the hold exceeds the release time ---\n");
  for (float h : {0.05f, 0.15f, 0.30f, 0.50f, 1.00f, 2.00f}) report("sine", sine, 0.3f, h);
  std::printf("\n");
  for (float h : {0.05f, 0.50f, 2.00f}) report("super saw", supersaw, 0.3f, h);
  std::printf("\n");
  for (float h : {0.05f, 0.50f, 2.00f}) report("all layers", all, 0.3f, h);
  return 0;
}
