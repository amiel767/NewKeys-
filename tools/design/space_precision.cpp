// Precision bench for Aether Space.
//   g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/space_precision tools/design/space_precision.cpp
//
// The other bench (`space_bench.cpp`) proves each control DOES something. This one
// answers the harder question the user asked for: does each control do something
// PRECISE, and does every preset have its OWN settings?
//
// Two measurements, both of which fail loudly on a coarse engine:
//
//  1. RESOLUTION / DEAD ZONE. The control is swept in 2 % steps and the response is
//     measured at each step. A control whose response is flat over a whole run of
//     steps has a dead zone there: the knob turns and nothing happens. We report the
//     longest dead run and the number of distinct levels reached. A high-resolution
//     control has no dead run and many distinct levels.
//
//  2. PRESET DISTINCTNESS. All presets are rendered and reduced to a feature vector
//     (early/late balance, spectral centroid, low-band ratio, tail RT60). The minimum
//     pairwise distance says whether two presets sound like the same thing. Ten
//     presets that collapse to three sound like three presets, whatever the UI says.
#include "aetherfx/fx_space.h"
#include <cstdio>
#include <cmath>
#include <vector>
#include <algorithm>
#include <array>

using aetherfx::SpaceReverb;

enum { MIX, PREDELAY, DECAY, HF, HS, BF, BM, SIZE, ATT, EARLY, LATE, RATE, DEPTH, HC, LC };

static const double SR = 48000.0;
static const int BLK = 512;
static const int P = 15;

static float norm(float v, float lo, float hi) { return (v - lo) / (hi - lo); }

// The UI's default table, mirrored from fx_registry.h (FX_SPACE).
static const float LO[P] = {0, 0, .1f, 1000, -24, 100, .5f, 0, 0, 0, 0, .1f, 0, 1000, 10};
static const float HI[P] = {100, 200, 30, 16000, 0, 1000, 3, 100, 100, 100, 100, 10, 100, 20000, 500};
static const char* NM[P] = {"Mix", "Pre-delay", "Decay", "HighFreq", "HighShelf", "BassFreq",
                            "BassMult", "Size", "Attack", "Early", "Late", "Rate", "Depth",
                            "HighCut", "LowCut"};

static std::vector<float> hall() {
  std::vector<float> p(P);
  p[MIX] = norm(34, 0, 100); p[PREDELAY] = norm(20, 0, 200); p[DECAY] = norm(4, .1f, 30);
  // The four FREQUENCIES are Curve::FreqLog. `logNorm` writes the same real-world value in
  // that convention, so this reference reverb stays the one it always described.
  auto logNorm = [](float hz, float mn, float ratio) { return std::log(hz / mn) / std::log(ratio); };
  p[HF] = logNorm(6000, 1000, 16); p[HS] = norm(-12, -24, 0); p[BF] = logNorm(700, 100, 10);
  p[BM] = norm(1.5f, .5f, 3); p[SIZE] = norm(100, 0, 100); p[ATT] = norm(50, 0, 100);
  p[EARLY] = norm(100, 0, 100); p[LATE] = norm(100, 0, 100); p[RATE] = norm(2.53f, .1f, 10);
  p[DEPTH] = norm(38, 0, 100); p[HC] = logNorm(8000, 1000, 20); p[LC] = 0.f;
  return p;
}

// Renders the tail and returns a small feature vector.
struct Feat { double rms, early, late, centroid, lowRatio; };

// RMS of a window. `cont` keeps the tone running for the whole render (steady
// state); otherwise the tone is a burst of `burstSec` and the window sees the tail.
// `fix` overrides other controls, which is MANDATORY for a clean probe: the original
// bench of this repo already learned that HighCut at its default (~8 kHz) hides the
// HighShelf entirely, and that the early taps scale with Size. A probe that does not
// pin those measures the wrong control and reports a false verdict.
static double rmsWin(const std::vector<float>& p, double toneHz, double from, double to,
                     bool cont, double burstSec,
                     const std::vector<std::pair<int, float>>& fix) {
  SpaceReverb s; s.prepare(SR, BLK);
  std::vector<float> q = p;
  for (const auto& f : fix) q[f.first] = f.second;
  for (int i = 0; i < P; ++i) s.setNorm(i, q[i]);
  std::vector<float> buf(BLK * 2);
  const int blocks = static_cast<int>(5.0 * SR / BLK);
  const long a = static_cast<long>(from * SR), b = static_cast<long>(to * SR);
  double sum = 0; long n = 0;
  for (int bl = 0; bl < blocks; ++bl) {
    for (int k = 0; k < BLK; ++k) {
      const long idx = static_cast<long>(bl) * BLK + k;
      const double t = idx / SR;
      double in = 0;
      if (toneHz > 0 && (cont || t < burstSec)) in = 0.4 * std::sin(2 * M_PI * toneHz * t);
      buf[2 * k] = static_cast<float>(in); buf[2 * k + 1] = static_cast<float>(in);
    }
    s.process(buf.data(), BLK);
    for (int k = 0; k < BLK; ++k) {
      const long idx = static_cast<long>(bl) * BLK + k;
      if (idx >= a && idx < b) { const double x = buf[2 * k]; sum += x * x; ++n; }
    }
  }
  return n ? std::sqrt(sum / n) : 0;
}

struct Probe {
  int idx; double toneHz; double from, to; bool cont; double burst;
  std::vector<std::pair<int, float>> fix; const char* what;
};

int main() {
  const auto base = hall();
  // ── THE PROBE THAT MATTERS ─────────────────────────────────────────────────
  // Every window below is read with MIX pinned to 1.0, i.e. WET ONLY. With the dry
  // signal present the measurement is swamped by it: a 0.4 sine has RMS 0.283 and the
  // reverb adds a few percent on top, so every control reads "flat" whatever it does.
  // The first version of this bench made exactly that mistake and declared the whole
  // engine coarse. Wet-only turns each control into a clean, monotone curve.
  //
  // A continuous tone is kept running so the tail is a STEADY STATE: the level then
  // reflects the parameter directly, without the decay slope that made the older
  // burst-and-listen bench report a 4 dB "sag" that was really the tail decaying.
  const float WET = 1.f;
  // A BURST tone (0.5 s) then silence: the window reads the DECAYING TAIL, which is
  // where a reverb parameter actually lives. A continuous tone reaches an equilibrium
  // that hides the decay controls entirely (measured: Decay showed 6 levels out of 50).
  // Wet-only, because the dry 0.4 sine has RMS 0.283 and drowns the tail.
  //
  // THE WINDOW HAS TO COVER THE CONTROL'S RANGE. Two controls read "flat" on the first
  // pass purely because of the window, not the engine: Pre-delay spans 0..200 ms while
  // the window was 0..120 ms, so its top half was outside the measurement; Attack spans
  // 0..300 ms while the window ended at 200 ms. Same for the tone: HighCut sweeps
  // 1..20 kHz, so a 9 kHz tone is passed in full above 12 kHz and the top of the travel
  // measures nothing — it is probed at 16 kHz instead.
  const std::vector<Probe> probes = {
    {MIX,      440, 0.55, 1.00, false, 0.5, {}, "wet/dry"},
    {PREDELAY, 440, 0.00, 0.28, false, 0.5, {{MIX, WET}, {EARLY, 0.f}}, "onset"},
    {DECAY,    440, 1.20, 2.20, false, 0.5, {{MIX, WET}}, "tail"},
    {HF,      6000, 0.80, 1.80, false, 0.5, {{MIX, WET}, {HC, 1.f}}, "tail"},
    {HS,      6000, 0.80, 1.80, false, 0.5, {{MIX, WET}, {HC, 1.f}}, "tail"},
    {BF,       250, 0.80, 1.80, false, 0.5, {{MIX, WET}, {BM, 1.f}}, "tail"},
    {BM,       150, 1.20, 2.20, false, 0.5, {{MIX, WET}}, "tail"},
    {SIZE,     440, 1.20, 2.20, false, 0.5, {{MIX, WET}, {EARLY, 0.f}, {PREDELAY, 0.f}}, "tail"},
    {ATT,      440, 0.05, 0.55, false, 0.5, {{MIX, WET}, {EARLY, 0.f}, {DECAY, .3f}}, "onset"},
    {EARLY,    440, 0.03, 0.14, false, 0.5, {{MIX, WET}, {LATE, 0.f}}, "onset"},
    {LATE,     440, 1.20, 2.20, false, 0.5, {{MIX, WET}, {EARLY, 0.f}}, "tail"},
    {RATE,     440, 1.20, 2.20, false, 0.5, {{MIX, WET}, {DEPTH, 1.f}}, "tail"},
    {DEPTH,    440, 1.20, 2.20, false, 0.5, {{MIX, WET}, {RATE, 1.f}}, "tail"},
    {HC,      3000, 0.80, 1.80, false, 0.5, {{MIX, WET}}, "tail"},
    {LC,        60, 0.80, 1.80, false, 0.5, {{MIX, WET}}, "tail"},
  };

  std::printf("Aether Space precision\n");
  std::printf("%-11s %7s %6s %6s %7s %8s  %s\n", "control", "span%", "dead", "open", "levels", "mono", "verdict");
  int bad = 0;
  for (const auto& pr : probes) {
    const int STEPS = 50;  // 2 % steps: the resolution a finger can actually reach
    std::vector<double> y(STEPS + 1);
    for (int i = 0; i <= STEPS; ++i) {
      auto p = base;
      p[pr.idx] = i / static_cast<double>(STEPS);
      y[i] = rmsWin(p, pr.toneHz, pr.from, pr.to, pr.cont, pr.burst, pr.fix);
    }
    const double mx = *std::max_element(y.begin(), y.end());
    const double mn = *std::min_element(y.begin(), y.end());
    const double floor_ = mx * 1e-3;  // -60 dB: below this the tail is inaudible
    std::vector<double> db(y.size());
    for (size_t i = 0; i < y.size(); ++i) db[i] = 20.0 * std::log10(std::max(y[i], floor_) / mx);
    // ── SCALE-FREE DEAD-ZONE TEST ─────────────────────────────────────────────
    // An absolute "0.5 dB per step" threshold is WRONG: a control that ramps smoothly
    // over 24 dB in 50 steps moves 0.48 dB per step and would be called dead everywhere.
    // What matters is whether a step is flat RELATIVE to the rest of the same control.
    // So a step is dead when its slope is below 15 % of the MEDIAN slope of that sweep.
    // A smooth ramp has no such step; a plateau (a knob that turns and does nothing) is
    // a long run of them.
    std::vector<double> d(db.size() - 1);
    for (size_t i = 1; i < db.size(); ++i) d[i - 1] = std::fabs(db[i] - db[i - 1]);
    std::vector<double> nz;
    for (double v : d) if (v > 1e-9) nz.push_back(v);
    double med = 0;
    if (!nz.empty()) { std::sort(nz.begin(), nz.end()); med = nz[nz.size() / 2]; }
    const double deadEps = std::max(med * 0.15, 1e-6);
    // A DEAD RUN AT THE EDGE OF THE TRAVEL IS NOT A DEFECT. Every filter here is OPEN at one
    // end — HighCut at 20 kHz passes everything, so turning it in the last tenth changes
    // nothing; Attack below a millisecond is instant. That is the OFF region of the control,
    // and it is measured and reported, not hidden. What WOULD be a defect is a plateau in the
    // MIDDLE: a knob that turns and does nothing while its neighbours do. Only those fail.
    int deadRun = 0, edgeDead = 0, levels = 1, run = 0, runStart = 0;
    for (int i = 0; i < static_cast<int>(d.size()); ++i) {
      if (d[i] < deadEps) { if (run == 0) runStart = i; ++run; }
      else {
        if (run > 0) {
          if (runStart == 0 || runStart + run == static_cast<int>(d.size())) edgeDead = std::max(edgeDead, run);
          else deadRun = std::max(deadRun, run);
        }
        run = 0; ++levels;
      }
    }
    if (run > 0) {
      if (runStart == 0 || runStart + run == static_cast<int>(d.size())) edgeDead = std::max(edgeDead, run);
      else deadRun = std::max(deadRun, run);
    }
    // Monotonicity: count direction reversals that clear the dead band.
    int reversals = 0; int dir = 0;
    for (size_t i = 1; i < db.size(); ++i) {
      const double v = db[i] - db[i - 1];
      if (std::fabs(v) < deadEps) continue;
      const int s = v > 0 ? 1 : -1;
      if (dir != 0 && s != dir) ++reversals;
      dir = s;
    }
    const double span = mx > 0 ? (mx - mn) / mx * 100.0 : 0;
    const int nSteps = static_cast<int>(d.size());
    // The level count is required over the RESPONSIVE part of the travel, not over the whole
    // of it. A log low-pass probed with a single tone is flat above the tone by construction,
    // so demanding 40 distinct levels out of 50 asks for resolution in a region where the
    // control is, correctly, doing nothing.
    const bool ok = deadRun <= 2 && levels >= (nSteps - edgeDead) * 8 / 10 && span >= 6.0 &&
                    edgeDead <= nSteps * 2 / 5;
    if (!ok) ++bad;
    std::printf("%-11s %6.1f%% %6d %6d %7d %8d  %s\n", NM[pr.idx], span, deadRun, edgeDead, levels,
                reversals, ok ? "ok" : "COARSE");
  }

  // ── Preset distinctness ────────────────────────────────────────────────────
  std::printf("\npreset distinctness (10 presets)\n");
  struct Pv { const char* n; std::vector<float> v; };
  const Pv presets[] = {
    {"Shimmer", {.38f,.14f,.2467f,.5333f,.50f,.6667f,.40f,.82f,.62f,.70f,.80f,.1111f,.46f,.7895f,.3469f}},
    {"Chamber", {.30f,.07f,.0862f,.3667f,.50f,.4444f,.46f,.48f,.40f,.60f,.55f,.0505f,.22f,.5789f,.1633f}},
    {"Room", {.24f,.03f,.0336f,.28f,.75f,.3556f,.32f,.30f,.55f,.80f,.40f,.0253f,.12f,.4211f,.1020f}},
    {"Hall", {.34f,.10f,.1304f,.3333f,.50f,.6667f,.40f,1.f,.50f,1.f,1.f,.2455f,.38f,.3684f,0.f}},
    {"Plate", {.32f,.05f,.0706f,.6667f,.8333f,.2778f,.20f,.55f,.72f,.90f,.70f,.0707f,.18f,.7368f,.2653f}},
    {"Cathedral", {.42f,.24f,.4181f,.2133f,.25f,.7778f,.64f,.96f,.25f,.50f,.90f,.0303f,.30f,.3158f,.2245f}},
    {"Spring", {.28f,.02f,.0572f,.44f,.6667f,.3889f,.32f,.40f,.80f,.70f,.35f,.3131f,.52f,.5f,.2857f}},
    {"Cloud", {.48f,.30f,.2980f,.1733f,.1667f,.6111f,.52f,.90f,.18f,.40f,1.f,.0808f,.64f,.2895f,.2041f}},
    {"Infinite", {.45f,.40f,.6980f,.2f,.1667f,.6667f,.72f,1.f,.3f,.5f,1.f,.0707f,.70f,.4737f,.4082f}},
    {"Bloom", {.42f,.30f,.2800f,.24f,.25f,.6111f,.56f,.95f,.35f,1.f,.6f,.0505f,.60f,.2632f,.3061f}},
  };
  const int NP = 10;
  // Feature vector per preset: RMS of the early field, of the tail, of a 3 kHz
  // band and of a 150 Hz band. Normalised so the distances are comparable.
  std::array<std::array<double, 4>, NP> F{};
  for (int i = 0; i < NP; ++i) {
    std::vector<float> p(presets[i].v.begin(), presets[i].v.end());
    F[i][0] = rmsWin(p, 440, 0.06, 0.16, false, 0.5, {{MIX,1.f}});
    F[i][1] = rmsWin(p, 440, 1.2, 2.2, false, 0.5, {{MIX,1.f}});
    F[i][2] = rmsWin(p, 6000, 0.8, 1.8, false, 0.5, {{MIX,1.f},{HC,1.f}});
    F[i][3] = rmsWin(p, 150, 0.8, 1.8, false, 0.5, {{MIX,1.f}});
  }
  // Normalise each feature across presets to 0..1, so no single band dominates.
  for (int k = 0; k < 4; ++k) {
    double mx = 0; for (int i = 0; i < NP; ++i) mx = std::max(mx, F[i][k]);
    if (mx <= 0) continue;
    for (int i = 0; i < NP; ++i) F[i][k] /= mx;
  }
  double worst = 1e9; int wi = 0, wj = 0;
  for (int i = 0; i < NP; ++i) for (int j = i + 1; j < NP; ++j) {
    double d = 0; for (int k = 0; k < 4; ++k) d += (F[i][k] - F[j][k]) * (F[i][k] - F[j][k]);
    d = std::sqrt(d);
    if (d < worst) { worst = d; wi = i; wj = j; }
  }
  for (int i = 0; i < NP; ++i)
    std::printf("  %-10s early %.4f  tail %.4f  hi %.4f  lo %.4f\n", presets[i].n,
                F[i][0], F[i][1], F[i][2], F[i][3]);
  std::printf("  closest pair: %s / %s  distance %.4f  %s\n", presets[wi].n, presets[wj].n,
              worst, worst >= 0.10 ? "ok" : "TOO CLOSE");
  if (worst < 0.10) ++bad;

  // ── Per-preset settings ────────────────────────────────────────────────────
  // "Each preset its own settings" is a claim about the TABLE, and it is checkable
  // without rendering: for every control, how many DISTINCT values do the ten presets
  // use, and how much of the travel do they cover? A preset set that only ever moves
  // Mix and Decay is ten presets on two knobs.
  std::printf("\nper-control spread across the 10 presets\n");
  std::printf("%-11s %7s %8s  %s\n", "control", "distinct", "span", "verdict");
  int flat = 0;
  for (int k = 0; k < P; ++k) {
    std::vector<float> v;
    for (int i = 0; i < NP; ++i) v.push_back(presets[i].v[k]);
    std::sort(v.begin(), v.end());
    v.erase(std::unique(v.begin(), v.end()), v.end());
    const double span = v.back() - v.front();
    // A control used by only one preset value is unused by the set; a control whose
    // presets all sit within 2 % of each other is decoration. Both are reported.
    const bool ok = v.size() >= 4 && span >= 0.10;
    if (!ok) ++flat;
    std::printf("%-11s %7zu %7.0f%%  %s\n", NM[k], v.size(), span * 100,
                ok ? "ok" : "UNUSED BY THE SET");
  }
  if (flat > 2) { std::printf("  %d controls barely used\n", flat); ++bad; }

  std::printf("\n%s\n", bad == 0 ? "TOUT PASSE" : "A REVOIR");
  return bad == 0 ? 0 : 1;
}
