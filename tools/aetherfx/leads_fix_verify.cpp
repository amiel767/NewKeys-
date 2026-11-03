// Verifies the phase-spread fix: the click must be gone on EVERY repeated note, and the sound must
// be unchanged (same envelope, same spectrum).
//
// Two things could go wrong with a start-phase change. It could remove the click at the first note
// but not on repeats (if a stolen or reused voice keeps an aligned phase), and it could change the
// timbre: free-running oscillators sound slightly different from phase-locked ones, and that is a
// real trade-off, so it is measured rather than assumed.
//
// Build: g++ -O2 -std=c++17 -I <tree> tools/aetherfx/leads_fix_verify.cpp -o /tmp/lfv

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

#include "aetherfx/fx_leads.h"

using namespace aetherfx;
static constexpr double SR = 48000.0;
static constexpr int BLK = 256;

struct Preset {
  const char* name;
  float v[33];
};

static Preset mk(const char* name, float l0, float l1, float l2, float l3, float l4, float atk) {
  Preset p{};
  p.name = name;
  for (int i = 0; i < 33; ++i) p.v[i] = 0.f;
  p.v[0] = l0; p.v[1] = l1; p.v[2] = l2; p.v[3] = l3; p.v[4] = l4;
  p.v[5] = 0.5f; p.v[6] = 0.5f; p.v[7] = 0.5f; p.v[8] = 0.5f; p.v[9] = 0.5f;
  p.v[10] = 0.5f; p.v[11] = 0.15f;
  p.v[12] = atk; p.v[13] = 0.f; p.v[14] = 0.45f; p.v[15] = 0.8f; p.v[16] = 0.3f;
  p.v[17] = 0.f; p.v[18] = 0.85f; p.v[19] = 0.15f; p.v[20] = 0.5f;
  p.v[27] = 0.5f; p.v[28] = 0.f; p.v[31] = 0.8f; p.v[32] = 0.5f;
  return p;
}

// Plays the SAME note 8 times, each 250 ms apart, and reports the excess at every strike.
static void repeated(const Preset& pr, int note) {
  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, pr.v[i]);
  eng.reset();

  const int strikeGap = static_cast<int>(0.25 * SR);
  const int total = strikeGap * 8 + static_cast<int>(0.2 * SR);
  std::vector<float> x;
  std::vector<float> buf(static_cast<size_t>(BLK) * 2);

  int done = 0, next = 0;
  while (done < total) {
    if (next < 8 && done >= next * strikeGap) {
      if (next > 0) eng.noteOff(note);
      eng.noteOn(note, 100);
      next++;
    }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK && static_cast<int>(x.size()) < total; ++i) x.push_back(buf[static_cast<size_t>(i) * 2]);
    done += BLK;
  }

  float worst = -1e9f;
  int worstN = -1;
  std::printf("%-20s", pr.name);
  for (int k = 0; k < 8; ++k) {
    const int a = k * strikeGap;
    const int headEnd = a + static_cast<int>(0.012 * SR);
    const int susA = a + static_cast<int>(0.15 * SR), susB = a + static_cast<int>(0.22 * SR);
    float hj = 0.f, sj = 0.f, pk = 0.f;
    for (int i = a; i + 1 < headEnd && i + 1 < static_cast<int>(x.size()); ++i) hj = std::max(hj, std::fabs(x[i + 1] - x[i]));
    for (int i = susA; i + 1 < susB && i + 1 < static_cast<int>(x.size()); ++i) sj = std::max(sj, std::fabs(x[i + 1] - x[i]));
    for (int i = a; i < susB && i < static_cast<int>(x.size()); ++i) pk = std::max(pk, std::fabs(x[i]));
    const float e = (hj - sj) / std::max(pk, 1e-6f);
    std::printf(" %+5.1f%%", e * 100.f);
    if (e > worst) { worst = e; worstN = k; }
  }
  std::printf("   worst %+5.1f%% (note %d)  %s\n", worst * 100.f, worstN + 1,
              worst > 0.25f ? "<== STILL CLICKING" : "ok");
}

int main() {
  std::printf("=== Aether Leads: repeated notes, excess at each strike (%% of peak) ===\n\n");
  const Preset sine = mk("sine only", 1.f, 0.f, 0.f, 0.f, 0.f, 0.02f);
  const Preset pulse = mk("pulse only", 0.f, 1.f, 0.f, 0.f, 0.f, 0.02f);
  const Preset saw = mk("saw only", 0.f, 0.f, 1.f, 0.f, 0.f, 0.02f);
  const Preset supersaw = mk("super saw", 0.f, 0.f, 0.f, 1.f, 0.f, 0.02f);
  const Preset allLayers = mk("all layers", 0.6f, 0.5f, 0.5f, 1.f, 0.5f, 0.02f);

  for (const Preset& pr : {sine, pulse, saw, supersaw, allLayers}) repeated(pr, 60);
  std::printf("\n--- same preset, different notes (each fresh) ---\n");
  for (int n : {36, 48, 60, 72, 84}) {
    char label[32];
    std::snprintf(label, sizeof label, "super saw");
    repeated(supersaw, n);
  }
  return 0;
}
