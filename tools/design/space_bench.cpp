// Host bench for Aether Space. Compiles without Android:
//   g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/space_bench tools/design/space_bench.cpp
//
// Measures, for each control, that the knob DOES something, in the right direction, on the
// right band. A reverb that renders audio but ignores half its parameters is the failure
// mode this catches — the shipped engine had seven parameters and the UI drew ten.
//
// MEASURING A REVERB HONESTLY. Every trap below produced a false verdict here before the
// probe was fixed:
//  - Early reflections are a fixed short field fed from the input. They swamp any window
//    that starts too early, so a test about the LATE tail zeroes Early and Pre-delay first.
//  - Size must be probed with Early and Pre-delay OFF, because the early taps are fed from
//    the pre-delay line, whose depth scales with Size.
//  - Damping, the shelf and the two cuts are per-frequency effects: a 220 Hz tone cannot
//    show them. Each is probed at a frequency inside the band it governs.
//  - Attack is a fade-IN gate. A continuous tone reaches the same steady state whatever the
//    attack, so the probe is a SHORT burst and the window is right after it.
//  - BassMult is judged by band energy, not broadband RMS: inside a coupled FDN the two
//    bands share the feedback path, so an overall figure cannot tell "the lows got longer"
//    from "everything moved".
#include "aetherfx/fx_space.h"
#include <cstdio>
#include <cmath>
#include <vector>
#include <algorithm>

using aetherfx::SpaceReverb;

enum { MIX, PREDELAY, DECAY, HF, HS, BF, BM, SIZE, ATT, EARLY, LATE, RATE, DEPTH, HC, LC };

static const double SR = 48000.0;
static const int BLK = 512;

static float norm(float v, float lo, float hi) { return (v - lo) / (hi - lo); }

// The reference's "Space Hall", in the engine's own 0..1 domain.
static std::vector<float> hall() {
  std::vector<float> p(15);
  p[MIX] = norm(34, 0, 100); p[PREDELAY] = norm(20, 0, 200); p[DECAY] = norm(4, 0.1f, 30);
  p[HF] = norm(6000, 1000, 16000); p[HS] = norm(-12, -24, 0); p[BF] = norm(700, 100, 1000);
  p[BM] = norm(1.5f, 0.5f, 3); p[SIZE] = norm(100, 0, 100); p[ATT] = norm(50, 0, 100);
  p[EARLY] = norm(100, 0, 100); p[LATE] = norm(100, 0, 100); p[RATE] = norm(2.53f, 0.1f, 10);
  p[DEPTH] = norm(38, 0, 100); p[HC] = norm(8000, 1000, 20000); p[LC] = norm(10, 10, 500);
  return p;
}

// Renders `sec` seconds and returns the RMS of [fromSec, toSec].
// `toneHz > 0`: a steady sine. `burstSec > 0`: that many seconds of tone, then silence.
// Neither: a single click at t = 0.1 s.
static double rms(const std::vector<float>& p, double sec, double fromSec, double toSec,
                  double toneHz = 0, double burstSec = 0) {
  SpaceReverb s; s.prepare(SR, BLK);
  for (int i = 0; i < 15; ++i) s.setNorm(i, p[i]);
  std::vector<float> buf(BLK * 2);
  const int blocks = static_cast<int>(sec * SR / BLK);
  const long fromN = static_cast<long>(fromSec * SR), toN = static_cast<long>(toSec * SR);
  double sum = 0; long n = 0;
  for (int b = 0; b < blocks; ++b) {
    for (int k = 0; k < BLK; ++k) {
      const long idx = static_cast<long>(b) * BLK + k;
      const double t = double(idx) / SR;
      float x = 0.f;
      if (toneHz > 0 && (burstSec <= 0 || t < burstSec)) x = 0.4f * static_cast<float>(std::sin(2 * M_PI * toneHz * t));
      else if (toneHz <= 0) x = (idx > 4 * BLK && idx < 4 * BLK + 8) ? 0.6f : 0.f;
      buf[2 * k] = x; buf[2 * k + 1] = x;
    }
    s.process(buf.data(), BLK);
    for (int k = 0; k < BLK; ++k) {
      const long idx = static_cast<long>(b) * BLK + k;
      if (idx < fromN || idx >= toN) continue;
      const double v = buf[2 * k]; sum += v * v; ++n;
    }
  }
  return n ? std::sqrt(sum / n) : 0.0;
}

static double peakOf(const std::vector<float>& p, double sec) {
  SpaceReverb s; s.prepare(SR, BLK);
  for (int i = 0; i < 15; ++i) s.setNorm(i, p[i]);
  std::vector<float> buf(BLK * 2);
  double peak = 0;
  for (int b = 0; b < static_cast<int>(sec * SR / BLK); ++b) {
    for (int k = 0; k < BLK; ++k) {
      const long idx = static_cast<long>(b) * BLK + k;
      const float x = (idx > 4 * BLK && idx < 4 * BLK + 8) ? 0.6f : 0.f;
      buf[2 * k] = x; buf[2 * k + 1] = x;
    }
    s.process(buf.data(), BLK);
    for (int k = 0; k < BLK; ++k) peak = std::max(peak, (double)std::fabs(buf[2 * k]));
  }
  return peak;
}

// Normalised difference between two renders: "did the control change the sound at all".
// Unlike an RMS comparison this needs no guess about the direction, which makes it the right
// probe for modulation (Depth, Rate) — those smear the tail without changing its level.
static double diffRms(const std::vector<float>& p, const std::vector<float>& q,
                      double sec, double fromSec, double toSec, double toneHz = 0, double burstSec = 0) {
  auto render = [&](const std::vector<float>& prm) {
    SpaceReverb s; s.prepare(SR, BLK);
    for (int i = 0; i < 15; ++i) s.setNorm(i, prm[i]);
    std::vector<float> buf(BLK * 2);
    std::vector<double> out;
    for (int b = 0; b < static_cast<int>(sec * SR / BLK); ++b) {
      for (int k = 0; k < BLK; ++k) {
        const long idx = static_cast<long>(b) * BLK + k; const double t = double(idx) / SR;
        float x = 0.f;
        if (toneHz > 0) { if (burstSec <= 0 || t < burstSec) x = 0.4f * static_cast<float>(std::sin(2 * M_PI * toneHz * t)); }
        else if (idx > 4 * BLK && idx < 4 * BLK + 8) x = 0.6f;
        buf[2 * k] = x; buf[2 * k + 1] = x;
      }
      s.process(buf.data(), BLK);
      for (int k = 0; k < BLK; ++k) out.push_back(buf[2 * k]);
    }
    return out;
  };
  const auto a = render(p), b = render(q);
  const long fromN = static_cast<long>(fromSec * SR), toN = std::min<long>(static_cast<long>(toSec * SR), (long)a.size());
  double sd = 0, sa = 0, sb = 0; long n = 0;
  for (long i = fromN; i < toN; ++i) {
    const double d = a[i] - b[i]; sd += d * d; sa += (double)a[i] * a[i]; sb += (double)b[i] * b[i]; ++n;
  }
  if (!n) return 0.0;
  const double ref = std::sqrt(std::max(sa, sb) / n);
  return std::sqrt(sd / n) / std::max(ref, 1e-12);
}

static int failures = 0;
static void check(const char* name, bool ok) {
  std::printf("  %-28s %s\n", name, ok ? "ok" : "FAIL");
  if (!ok) ++failures;
}

// Fraction of the energy above `splitHz`, by DFT on a window taken late in the tail.
static double highFraction(const std::vector<float>& p, double splitHz) {
  const int N = 4096;
  SpaceReverb s; s.prepare(SR, BLK);
  for (int i = 0; i < 15; ++i) s.setNorm(i, p[i]);
  std::vector<float> buf(BLK * 2);
  std::vector<double> mono; mono.reserve(N);
  uint32_t seed = 12345u;
  auto rnd = [&seed]() { seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5; return float(int32_t(seed)) * (1.f / 2147483648.f); };
  for (int b = 0; b < 500; ++b) {
    for (int k = 0; k < BLK; ++k) { const float x = (b < 2) ? 0.5f * rnd() : 0.f; buf[2 * k] = x; buf[2 * k + 1] = x; }
    s.process(buf.data(), BLK);
    if (b >= 400) for (int k = 0; k < BLK && (int)mono.size() < N; ++k) mono.push_back(buf[2 * k]);
  }
  double lo = 0, hi = 0;
  for (int m = 1; m < N / 2; ++m) {
    double re = 0, im = 0;
    for (int t = 0; t < N; ++t) { const double w = 2 * M_PI * m * t / N; re += mono[t] * std::cos(w); im -= mono[t] * std::sin(w); }
    const double mag = re * re + im * im;
    if (m * SR / N < splitHz) lo += mag; else hi += mag;
  }
  return (lo + hi) > 0 ? hi / (lo + hi) : 0.0;
}

// Probes one control: returns the relative change in the measured figure between the two
// extremes, so a test can assert "it moves".
static double delta(const std::vector<float>& a, const std::vector<float>& b,
                    double sec, double from, double to, double toneHz = 0, double burstSec = 0) {
  const double ra = rms(a, sec, from, to, toneHz, burstSec), rb = rms(b, sec, from, to, toneHz, burstSec);
  return std::abs(rb - ra) / std::max(std::max(ra, rb), 1e-12);
}

int main() {
  std::printf("Aether Space bench\n");

  // 1. It makes sound and does not clip.
  {
    const double r = rms(hall(), 2.0, 0.05, 2.0);
    const double pk = peakOf(hall(), 2.0);
    std::printf("  hall rms=%.5f peak=%.5f\n", r, pk);
    check("renders audio", r > 1e-4);
    check("no clipping", pk <= 1.001);
  }

  // 2. Decay lengthens the tail, monotonically, across the whole range.
  {
    double prev = -1, lo = 0, hi = 0; bool mono = true;
    for (int i = 0; i <= 10; ++i) {
      auto p = hall(); p[DECAY] = i / 10.f; p[EARLY] = 0.f; p[PREDELAY] = 0.f;
      const double r = rms(p, 2.5, 1.0, 2.5);
      if (i == 0) lo = r; if (i == 10) hi = r;
      if (prev >= 0 && r < prev * 0.9) mono = false;
      prev = r;
    }
    std::printf("  decay tail(1s..2.5s) %.7f -> %.7f\n", lo, hi);
    check("decay lengthens the tail", hi > lo * 10);
    check("decay is monotone", mono);
  }

  // 3. Mix at zero is the dry input: nothing survives the note.
  {
    auto p = hall(); p[MIX] = 0.f;
    const double r = rms(p, 2.0, 0.5, 2.0);
    std::printf("  mix=0 tail rms=%.9f\n", r);
    check("mix=0 is silent", r < 1e-7);
  }

  // 4. Size stretches the tail. Early and Pre-delay off: the early taps are fed from the
  //    pre-delay line, so leaving them on hides the effect behind a short field.
  {
    auto a = hall(); a[SIZE] = 0.f; a[EARLY] = 0.f; a[PREDELAY] = 0.f;
    auto b = hall(); b[SIZE] = 1.f; b[EARLY] = 0.f; b[PREDELAY] = 0.f;
    const double d = diffRms(a, b, 2.5, 1.0, 2.5);
    const double ra = rms(a, 2.5, 1.0, 1.5), rb = rms(a, 2.5, 2.0, 2.5);
    std::printf("  size difference %.1f %% (tail still decays: %.6f -> %.6f)\n", d * 100, ra, rb);
    check("size changes the tail", d > 0.2);
    check("size keeps the tail decaying", rb < ra);
  }

  // 5. Every control must move the output, probed with the right signal.
  {
    // `fix` raises any control that would mask the one under test: the output low-pass sits
    // at 8 kHz by default, so a 6 kHz probe was being cut before it could show anything.
    struct P { const char* n; int i; double tone; double from; double to; double burst; int fix; float fixV; bool byLevel; };
    const P ps[] = {
      {"HighFreq", HF, 6000, 2.5, 4.0, 0, HC, 1.f, false},
      {"HighShelf", HS, 6000, 2.5, 4.0, 0, HC, 1.f, false},
      {"BassFreq", BF, 300, 2.5, 4.0, 0, HC, 1.f, true},
      {"BassMult", BM, 120, 2.5, 4.0, 0, HC, 1.f, true},
      {"Attack", ATT, 220, 0.12, 0.35, 0.1, HC, 1.f, true},
      {"Late", LATE, 220, 2.5, 4.0, 0, HC, 1.f, true},
      {"Rate", RATE, 220, 2.5, 4.0, 0, HC, 1.f, false},
      {"Depth", DEPTH, 220, 2.5, 4.0, 0, HC, 1.f, false},
      {"HighCut", HC, 12000, 2.5, 4.0, 0, HF, 1.f, false},
      {"LowCut", LC, 40, 2.5, 4.0, 0, HC, 1.f, true},
    };
    for (const auto& q : ps) {
      auto a = hall(); a[q.i] = 0.f; a[q.fix] = q.fixV; a[EARLY] = 0.f; a[PREDELAY] = 0.f;
      auto b = hall(); b[q.i] = 1.f; b[q.fix] = q.fixV; b[EARLY] = 0.f; b[PREDELAY] = 0.f;
      const double d = q.byLevel ? delta(a, b, 4.0, q.from, q.to, q.tone, q.burst)
                                 : diffRms(a, b, 4.0, q.from, q.to, q.tone, q.burst);
      std::printf("  %-12s %5.1f %%\n", q.n, d * 100);
      check(q.n, d > 0.05);
    }
  }

  // 6. Early and Late are separable, and the late tail is what carries the reverb.
  {
    auto e = hall(); e[EARLY] = 1.f; e[LATE] = 0.f;
    auto l = hall(); l[EARLY] = 0.f; l[LATE] = 1.f;
    // BOTH fields start as soon as the input does — an FDN is fed straight from the input, so
    // a "the early one is the first to sound" assertion is simply false and was a bad probe.
    // The real distinction is persistence: an early-only field dies with its taps, a late-only
    // field carries on. That is what is measured here.
    const double eEarly = rms(e, 2.0, 0.02, 0.15), eLate = rms(e, 2.0, 0.6, 2.0);
    const double lEarly = rms(l, 2.0, 0.02, 0.15), lLate = rms(l, 2.0, 0.6, 2.0);
    const double hallLate = rms(hall(), 2.0, 0.6, 2.0);
    const double eKeep = eEarly > 0 ? eLate / eEarly : 0, lKeep = lEarly > 0 ? lLate / lEarly : 0;
    std::printf("  early-only keeps %.5f | late-only keeps %.5f | hall late=%.6f\n", eKeep, lKeep, hallLate);
    check("Early alone leaves no tail", eKeep < 0.02);
    check("Late alone keeps a tail", lKeep > eKeep * 5 && lLate > 1e-6);
    check("the reverb needs Late", hallLate > eLate * 5);
  }

  // 7. BassMult acts on the LOW band and leaves the highs where they were.
  {
    auto a = hall(); a[BM] = 0.f; a[EARLY] = 0.f;
    auto b = hall(); b[BM] = 1.f; b[EARLY] = 0.f;
    const double fa = highFraction(a, 700), fb = highFraction(b, 700);
    std::printf("  bass  high-fraction %.4f -> %.4f\n", fa, fb);
    check("BassMult grows the lows", fb < fa * 0.9);
  }

  // 8. The tail decays: it never grows. This is the stability guard — the first version of
  //    the bass split pushed the loop gain above 1 and the tail rose 34 dB over four seconds.
  {
    auto p = hall(); p[BM] = 1.f; p[DECAY] = 1.f; p[EARLY] = 0.f; p[PREDELAY] = 0.f;
    const double early = rms(p, 6.0, 1.0, 1.5), late = rms(p, 6.0, 5.0, 5.5);
    std::printf("  long decay bm=1: %.6f at 1s -> %.6f at 5s\n", early, late);
    check("the tail decays", late < early);
    check("no runaway with bm=1", peakOf(p, 8.0) <= 1.001);
  }

  std::printf("%s\n", failures ? "ECHEC" : "TOUT PASSE");
  return failures ? 1 : 0;
}
