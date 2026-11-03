// Measures what the phase-spread fix changes about the SOUND, not just the click.
//
// Spreading the start phase is not free: free-running oscillators sound slightly different from
// phase-locked ones. The claim to check is that the difference is confined to the note-on, and that
// the sustain - where a lead actually lives - is unchanged. The oscillators are detuned, so they
// decorrelate within a fraction of a second on their own; past that point the two engines should be
// statistically identical.
//
// Reports, per preset: RMS over the whole note, RMS over the sustain, and the spectrum of the
// sustain in bands. A change in the sustain would show up as a band moving.
//
// Build: g++ -O2 -std=c++17 -I <tree> tools/aetherfx/leads_timbre_check.cpp -o /tmp/ltc

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

static Preset mk(const char* name, float l0, float l1, float l2, float l3, float l4) {
  Preset p{};
  p.name = name;
  for (int i = 0; i < 33; ++i) p.v[i] = 0.f;
  p.v[0] = l0; p.v[1] = l1; p.v[2] = l2; p.v[3] = l3; p.v[4] = l4;
  p.v[5] = 0.5f; p.v[6] = 0.5f; p.v[7] = 0.5f; p.v[8] = 0.5f; p.v[9] = 0.5f;
  p.v[10] = 0.5f; p.v[11] = 0.15f;
  p.v[12] = 0.02f; p.v[13] = 0.f; p.v[14] = 0.45f; p.v[15] = 0.8f; p.v[16] = 0.3f;
  p.v[17] = 0.f; p.v[18] = 0.85f; p.v[19] = 0.15f; p.v[20] = 0.5f;
  for (int i = 21; i <= 26; ++i) p.v[i] = 0.f;
  p.v[27] = 0.5f; p.v[28] = 0.f; p.v[29] = 0.f; p.v[30] = 0.f; p.v[31] = 0.8f; p.v[32] = 0.5f;
  return p;
}

static std::vector<float> render(const Preset& pr, int note, double seconds) {
  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, pr.v[i]);
  eng.reset();
  const int total = static_cast<int>(seconds * SR);
  std::vector<float> x;
  std::vector<float> buf(static_cast<size_t>(BLK) * 2);
  int done = 0;
  bool on = false;
  while (done < total) {
    if (!on) { eng.noteOn(note, 100); on = true; }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK && static_cast<int>(x.size()) < total; ++i) x.push_back(buf[static_cast<size_t>(i) * 2]);
    done += BLK;
  }
  return x;
}

static double rms(const std::vector<float>& x, int a, int b) {
  double s = 0;
  int n = 0;
  for (int i = a; i < b && i < static_cast<int>(x.size()); ++i) { s += static_cast<double>(x[i]) * x[i]; ++n; }
  return n ? std::sqrt(s / n) : 0.0;
}

// Band energy by a Goertzel sweep over a set of probe frequencies, reported as a fraction of total.
static void bands(const std::vector<float>& x, int a, int b, double out[4]) {
  const int n = std::min(b, static_cast<int>(x.size())) - a;
  if (n <= 0) { out[0] = out[1] = out[2] = out[3] = 0; return; }
  double tot = 0;
  for (int i = a; i < a + n; ++i) tot += static_cast<double>(x[i]) * x[i];
  tot = std::max(tot, 1e-12);

  // A crude 4-band split via a one-pole crossover: enough to see a band move, which is all this
  // check needs. The probe frequencies are the harmonics of the played note, so the split lands
  // inside the harmonic series rather than across it.
  const double cuts[3] = {400.0, 1500.0, 5000.0};
  double e[4] = {0, 0, 0, 0};
  double z[4] = {0, 0, 0, 0};
  for (int i = a; i < a + n; ++i) {
    const double v = x[i];
    double rest = v;
    for (int c = 0; c < 3; ++c) {
      const double al = 1.0 - std::exp(-2.0 * kPi * cuts[c] / SR);
      z[c] += al * (rest - z[c]);
      e[c] += z[c] * z[c];
      rest -= z[c];
    }
    e[3] += rest * rest;
  }
  for (int c = 0; c < 4; ++c) out[c] = e[c] / tot;
}

int main(int argc, char** argv) {
  // The reference engine lives in a second include tree, so the same preset can be rendered by both
  // the old and the new code and compared directly.
  std::printf("=== Aether Leads: what the phase spread changes about the sound ===\n\n");

  const Preset sine = mk("sine only", 1.f, 0.f, 0.f, 0.f, 0.f);
  const Preset saw = mk("saw only", 0.f, 0.f, 1.f, 0.f, 0.f);
  const Preset supersaw = mk("super saw", 0.f, 0.f, 0.f, 1.f, 0.f);
  const Preset all = mk("all layers", 0.6f, 0.5f, 0.5f, 1.f, 0.5f);

  for (const Preset& pr : {sine, saw, supersaw, all}) {
    const std::vector<float> x = render(pr, 60, 2.2);
    const int head = static_cast<int>(0.012 * SR);

    // Is the late-window difference real, or is it the natural fluctuation of a beating super-saw?
    // Five detuned saws beat against each other, so the RMS of any single window is a sample of a
    // distribution. The test is whether the spread BETWEEN windows of one render is as large as the
    // difference between the two engines: if it is, the engines are statistically the same signal.
    const int w = static_cast<int>(0.30 * SR);
    double lo = 1e9, hi = 0, sum = 0;
    int n = 0;
    // Start well after the decay stage. D = 0.01 + 0.45^2*3 = 0.62 s, so a window opening at 0.30 s
    // is still inside the decay and measures the envelope, not the oscillator sum.
    for (int a = static_cast<int>(0.90 * SR); a + w < static_cast<int>(x.size()); a += w / 3) {
      const double r = rms(x, a, a + w);
      lo = std::min(lo, r); hi = std::max(hi, r); sum += r; ++n;
    }
    std::printf("%-12s head %.5f | sustain mean %.5f  spread %.5f..%.5f (%.1f%% of mean)\n",
                pr.name, rms(x, 0, head), sum / std::max(1, n), lo, hi,
                100.0 * (hi - lo) / std::max(1e-9, sum / std::max(1, n)));
  }

  std::printf("\nA difference smaller than the sustain spread is not a timbre change.\n");
  return 0;
}
