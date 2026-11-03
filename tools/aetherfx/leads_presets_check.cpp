// Runs the REAL app presets through the engine and reports, for each, the note-on discontinuity and
// the sustain level. Built against two include trees (old and new engine) so the two can be compared
// directly: the fix must remove the click without moving the sustain.
//
// Build:
//   python3 tools/aetherfx/extract_leads_presets.py > /tmp/leads_presets.h
//   g++ -O2 -std=c++17 -I /tmp -I <tree> tools/aetherfx/leads_presets_check.cpp -o /tmp/lpc

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

#include "leads_presets.h"
#include "aetherfx/fx_leads.h"

using namespace aetherfx;
static constexpr double SR = 48000.0;
static constexpr int BLK = 256;

// Plays the same note eight times and returns the worst excess at a strike, plus the sustain level.
// Repeated notes are the case that matters: the user reports the click on every note, and a single
// strike hides anything that only appears once the voice has been released and reused.
struct Result {
  float boundary = 0.f;   // jump at the note-on sample itself, as a fraction of the note peak
  float excess = 0.f;     // boundary minus the waveform's own step: the only audible click
  float periodic = 0.f;   // largest jump elsewhere in the head: the waveform's own edges
  float sustainRms = 0.f;
  float peak = 0.f;
};

static Result run(const LeadPreset& pr, int note) {
  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, pr.v[i]);
  eng.reset();

  const int gap = static_cast<int>(0.30 * SR);
  const int total = gap * 8 + static_cast<int>(0.4 * SR);
  std::vector<float> x;
  std::vector<float> buf(static_cast<size_t>(BLK) * 2);
  std::vector<int> strikeAt;

  int done = 0, next = 0;
  while (done < total) {
    if (next < 8 && done >= next * gap) {
      if (next > 0) eng.noteOff(note);
      eng.noteOn(note, 100);
      strikeAt.push_back(done);  // block-aligned, and this is the first sample the voice renders
      next++;
    }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK && static_cast<int>(x.size()) < total; ++i) x.push_back(buf[static_cast<size_t>(i) * 2]);
    done += BLK;
  }

  Result r;
  double susSum = 0;
  int susN = 0;
  for (size_t k = 0; k < strikeAt.size(); ++k) {
    const int a = strikeAt[k];
    if (a < 1 || a >= static_cast<int>(x.size())) continue;
    const int headEnd = std::min(a + static_cast<int>(0.012 * SR), static_cast<int>(x.size()));
    const int sA = std::min(a + static_cast<int>(0.20 * SR), static_cast<int>(x.size()));
    const int sB = std::min(a + static_cast<int>(0.28 * SR), static_cast<int>(x.size()));

    float pk = 0;
    for (int i = a; i < sB; ++i) pk = std::max(pk, std::fabs(x[i]));
    if (pk < 1e-6f) continue;

    // Two numbers, and the difference between them is the whole point.
    //
    // A sawtooth resets once per period and a pulse twice, so SOME sample in every period carries a
    // full-amplitude step. If that sample happens to be the one where the note starts, a naive
    // "how big is the jump at the note-on" reading reports a click that is really just the waveform.
    // The largest jump elsewhere in the same note is the waveform's own step; only what the boundary
    // adds on top of it can be heard as a click.
    const float boundary = std::fabs(x[a] - x[a - 1]) / pk;
    float periodic = 0;
    for (int i = a; i + 1 < headEnd; ++i) {
      if (i == a - 1) continue;
      periodic = std::max(periodic, std::fabs(x[i + 1] - x[i]));
    }
    periodic = periodic / pk;

    r.boundary = std::max(r.boundary, boundary);
    r.excess = std::max(r.excess, boundary - periodic);
    r.periodic = std::max(r.periodic, periodic);
    r.peak = std::max(r.peak, pk);
    for (int i = sA; i < sB; ++i) { susSum += static_cast<double>(x[i]) * x[i]; ++susN; }
  }
  r.sustainRms = susN ? static_cast<float>(std::sqrt(susSum / susN)) : 0.f;
  return r;
}

int main() {
  const char* tag =
#ifdef ENGINE_OLD
      "OLD";
#else
      "NEW";
#endif
  std::printf("engine %s | %d presets, 8 repeated notes, note 60\n", tag, kLeadPresetCount);
  std::printf("%-28s %11s %11s %11s %10s\n", "preset", "excess", "boundary", "periodic", "sustain");
  for (int i = 0; i < kLeadPresetCount; ++i) {
    const Result r = run(kLeadPresets[i], 60);
    std::printf("%-28s %10.1f%% %10.1f%% %10.1f%% %10.5f\n", kLeadPresets[i].name,
                r.excess * 100.f, r.boundary * 100.f, r.periodic * 100.f, r.sustainRms);
  }
  return 0;
}
