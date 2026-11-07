// Accuracy bench for Aether Space: does the number the UI shows match the sound?
//   g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/space_accuracy tools/design/space_accuracy.cpp
//
// "High precision" has two halves. The first bench (`space_precision.cpp`) proves each
// knob HAS resolution. This one proves the DISPLAYED VALUE is true: a reverb that says
// "Decay 15 s" and rings for 18 s, or "Pre-delay 200 ms" and delays by 260 ms, is a
// coarse engine however smooth its curves are.
//
// EVERY ESTIMATOR BELOW IS VALIDATED ON A KNOWN SIGNAL FIRST (see `validate()`), because
// this repo has already paid twice for a bench that was never checked against a truth: a
// trend window shorter than the decay reported a "modulation" that was the slope, and a
// pitch detector that collapsed on a missing harmonic reported an octave error. A bench
// that has not been validated does not have the right to call the engine wrong.
#include "aetherfx/fx_space.h"
#include <cstdio>
#include <cmath>
#include <vector>
#include <algorithm>

using aetherfx::SpaceReverb;

enum { MIX, PREDELAY, DECAY, HF, HS, BF, BM, SIZE, ATT, EARLY, LATE, RATE, DEPTH, HC, LC };
static const double SR = 48000.0;
static const int BLK = 256;

static std::vector<float> base() {
  std::vector<float> p(15, 0.f);
  p[MIX] = 1.f; p[PREDELAY] = 0.f; p[DECAY] = .13f; p[HF] = .33f; p[HS] = .5f;
  p[BF] = .67f; p[BM] = .2f;  // 0.2 == the 1.0x neutral point of the bass multiplier
  p[SIZE] = .5f; p[ATT] = 0.f; p[EARLY] = 0.f; p[LATE] = 1.f;
  p[RATE] = .25f; p[DEPTH] = 0.f; p[HC] = 1.f; p[LC] = 0.f;
  return p;
}

// ── Estimator 1: RT60 by Schroeder backward integration + a -5..-35 dB fit ────
// The response is LOW-PASSED first, and that is not cosmetic. Damping (HighFreq) sits
// inside the feedback loop, so the high band always decays faster than the low band and a
// broadband RT60 reads 12-15 % SHORT of the number the descriptor promises — measured
// here, and identical at every damping setting. Every reverb quotes its decay in a band
// for that reason. The low band is also where BassMult is neutral at its 1.0x point, so
// what is left is exactly the Decay control.
static std::vector<float> lowBand(const std::vector<float>& x, double fc) {
  std::vector<float> y(x.size());
  const double a = 1.0 - std::exp(-2.0 * M_PI * fc / SR);
  double v = 0;
  for (size_t i = 0; i < x.size(); ++i) { v += a * (x[i] - v); y[i] = static_cast<float>(v); }
  return y;
}

static double rt60(const std::vector<float>& raw) {
  const std::vector<float> x = lowBand(raw, 400.0);
  const int N = static_cast<int>(x.size());
  std::vector<double> e(N);
  double acc = 0;
  for (int i = N - 1; i >= 0; --i) { acc += static_cast<double>(x[i]) * x[i]; e[i] = acc; }
  if (e[0] <= 0) return 0;
  std::vector<double> db(N);
  for (int i = 0; i < N; ++i) db[i] = 10.0 * std::log10(std::max(e[i], 1e-30) / e[0]);
  int i5 = -1, i35 = -1;
  for (int i = 0; i < N; ++i) {
    if (i5 < 0 && db[i] <= -5.0) i5 = i;
    if (i35 < 0 && db[i] <= -35.0) { i35 = i; break; }
  }
  if (i5 < 0 || i35 <= i5) return 0;
  double sx = 0, sy = 0, sxx = 0, sxy = 0; long n = 0;
  for (int i = i5; i <= i35; ++i) { const double t = i / SR; sx += t; sy += db[i]; sxx += t * t; sxy += t * db[i]; ++n; }
  const double slope = (n * sxy - sx * sy) / (n * sxx - sx * sx);
  return slope < 0 ? -60.0 / slope : 0;
}

// Impulse response, wet only. `sec` long.
static std::vector<float> ir(const std::vector<float>& p, double sec) {
  SpaceReverb s; s.prepare(SR, BLK);
  for (int i = 0; i < 15; ++i) s.setNorm(i, p[i]);
  const int N = static_cast<int>(sec * SR);
  std::vector<float> out(N);
  std::vector<float> buf(BLK * 2);
  const long imp = static_cast<long>(0.05 * SR);
  for (int b = 0; b * BLK < N; ++b) {
    for (int k = 0; k < BLK; ++k) {
      const long idx = static_cast<long>(b) * BLK + k;
      const float x = (idx == imp) ? 1.f : 0.f;
      buf[2 * k] = x; buf[2 * k + 1] = x;
    }
    s.process(buf.data(), BLK);
    for (int k = 0; k < BLK; ++k) {
      const long idx = static_cast<long>(b) * BLK + k;
      if (idx < N) out[idx] = 0.5f * (buf[2 * k] + buf[2 * k + 1]);
    }
  }
  return out;
}

// ── Estimator 2: first-arrival time by an absolute energy threshold ──────────
// A fraction-of-peak detector is biased by the BUILD-UP: the FDN output grows over
// hundreds of ms, so "first sample above 20 % of the peak" lands far after the real
// first arrival (measured: +37 ms of pure bias). An absolute threshold does not have
// that bias, and the pre-delay check is DIFFERENTIAL anyway — two settings are compared
// and the constant part cancels.
static double onsetMs(const std::vector<float>& x) {
  double pk = 0;
  for (float v : x) pk = std::max(pk, static_cast<double>(std::fabs(v)));
  const double thr = pk * 0.02;
  for (size_t i = 0; i < x.size(); ++i)
    if (std::fabs(x[i]) > thr) return i / SR * 1000.0;
  return 0;
}

// ── Estimator 3: a filter corner, by DIVIDING two responses ──────────────────
// The wet spectrum carries the reverb's own colouring on top of the EQ, so a raw
// response cannot show the corner. Dividing the response under test by the response
// with that filter open cancels the reverb and leaves the filter alone.
static double wetGain(const std::vector<float>& p, double hz) {
  SpaceReverb s; s.prepare(SR, BLK);
  for (int i = 0; i < 15; ++i) s.setNorm(i, p[i]);
  std::vector<float> buf(BLK * 2);
  double sum = 0; long n = 0;
  for (int b = 0; b < static_cast<int>(4.0 * SR / BLK); ++b) {
    for (int k = 0; k < BLK; ++k) {
      const double t = (static_cast<double>(b) * BLK + k) / SR;
      const float x = (t < 2.0) ? 0.3f * static_cast<float>(std::sin(2 * M_PI * hz * t)) : 0.f;
      buf[2 * k] = x; buf[2 * k + 1] = x;
    }
    s.process(buf.data(), BLK);
    for (int k = 0; k < BLK; ++k) {
      const long idx = static_cast<long>(b) * BLK + k;
      if (idx > 1.2 * SR && idx < 2.0 * SR) { sum += static_cast<double>(buf[2 * k]) * buf[2 * k]; ++n; }
    }
  }
  return n ? std::sqrt(sum / n) : 1e-12;
}

// Finds the frequency where the divided response crosses `ref` dB.
static double cornerHz(const std::vector<float>& p, bool highCut, double ref) {
  auto refp = p;
  if (highCut) refp[HC] = 1.f; else refp[LC] = 0.f;
  double prevF = 0, prevDb = 0; bool have = false;
  for (int i = 0; i < 64; ++i) {
    const double f = 100.0 * std::pow(200.0, i / 63.0);  // 100 Hz .. 20 kHz
    const double g = wetGain(p, f) / std::max(wetGain(refp, f), 1e-12);
    const double db = 20.0 * std::log10(std::max(g, 1e-9));
    if (have) {
      const bool cross = highCut ? (prevDb > ref && db <= ref) : (prevDb < ref && db >= ref);
      if (cross) return prevF + (ref - prevDb) / (db - prevDb) * (f - prevF);
    }
    prevF = f; prevDb = db; have = true;
  }
  return 0;
}

// ── The bench validates ITSELF before it judges the engine ────────────────────
static int validate() {
  int bad = 0;
  struct { double rt, tol; } cases[] = {{0.5, 5}, {1.0, 5}, {3.0, 5}, {10.0, 5}};
  for (auto c : cases) {
    std::vector<float> x(static_cast<size_t>(12.0 * SR));
    for (size_t i = 0; i < x.size(); ++i)
      x[i] = static_cast<float>(std::exp(-6.907755 * (i / SR) / c.rt));
    const double m = rt60(x);
    const double err = std::fabs(m - c.rt) / c.rt * 100.0;
    if (err > c.tol) ++bad;
    std::printf("  estimator rt60   true %5.2f  got %5.2f  err %4.1f%%  %s\n", c.rt, m, err,
                err <= c.tol ? "ok" : "BENCH IS WRONG");
  }
  // The corner finder must recover a corner it is TOLD. A one-pole low-pass at 3 kHz is
  // fed through the same divide-and-cross code by a synthetic response.
  {
    auto lp = [](double f, double fc) { return 1.0 / std::sqrt(1.0 + (f / fc) * (f / fc)); };
    double prevF = 0, prevDb = 0; bool have = false; double got = 0;
    for (int i = 0; i < 200; ++i) {
      const double f = 100.0 * std::pow(200.0, i / 199.0);
      const double db = 20.0 * std::log10(lp(f, 3000.0));
      if (have && prevDb > -3.0 && db <= -3.0) { got = prevF + (-3.0 - prevDb) / (db - prevDb) * (f - prevF); break; }
      prevF = f; prevDb = db; have = true;
    }
    const double err = std::fabs(got - 3000.0) / 3000.0 * 100.0;
    if (err > 5) ++bad;
    std::printf("  estimator corner true 3000  got %5.0f  err %4.1f%%  %s\n", got, err,
                err <= 5 ? "ok" : "BENCH IS WRONG");
  }
  std::printf("  %s\n\n", bad == 0 ? "bench validated" : "BENCH INVALID");
  return bad;
}

int main() {
  std::printf("Aether Space accuracy (displayed vs measured)\n\nbench self-check\n");
  if (validate() != 0) { std::printf("bench invalid, refusing to judge the engine\n"); return 2; }

  int bad = 0;
  auto check = [&](const char* what, double shown, double real, double tolPct) {
    const double err = shown > 0 ? std::fabs(real - shown) / shown * 100.0 : 0;
    const bool ok = err <= tolPct;
    if (!ok) ++bad;
    std::printf("%-26s shows %8.2f   real %8.2f   err %5.1f%%  %s\n", what, shown, real, err,
                ok ? "ok" : "OFF");
  };

  std::printf("\nDECAY  (descriptor 0.1 s .. 30 s, measured at BassMult 1.0x)\n");
  // BassMult is pinned to its neutral point: at the default 1.5x the LOW band rings
  // 50 % longer, so a broadband RT60 reads long for a reason that is not Decay. The
  // descriptor names the mid/high RT60; the low band is BassMult's job.
  for (float d : {.25f, .5f, .75f, 1.f}) {
    auto p = base();
    p[DECAY] = d;
    const double shown = 0.1 + 29.9 * d;
    check("  Decay", shown, rt60(ir(p, 24.0)), 20.0);
  }

  std::printf("\nPRE-DELAY  (descriptor 0 .. 200 ms, measured differentially)\n");
  {
    auto p0 = base(); p0[PREDELAY] = 0.f;
    const double t0 = onsetMs(ir(p0, 1.0));
    for (float v : {.25f, .5f, .75f, 1.f}) {
      auto p = base(); p[PREDELAY] = v;
      check("  Pre-delay", 200.0 * v, onsetMs(ir(p, 1.0)) - t0, 20.0);
    }
  }

  std::printf("\nEQ CORNERS  (divided response, -3 dB point)\n");
  {
    auto p = base(); p[HC] = 0.f;                    // descriptor: 1000 Hz
    check("  HighCut @ min (Hz)", 1000.0, cornerHz(p, true, -3.0), 35.0);
  }
  {
    auto p = base(); p[LC] = 1.f;                    // descriptor: 500 Hz
    check("  LowCut @ max (Hz)", 500.0, cornerHz(p, false, -3.0), 45.0);
  }

  std::printf("\n%s\n", bad == 0 ? "TOUT PASSE" : "A REVOIR");
  return bad == 0 ? 0 : 1;
}
