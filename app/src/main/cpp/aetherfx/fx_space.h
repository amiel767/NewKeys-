// Aether Space: 8-line feedback delay network reverb (Householder matrix) with input
// diffusion, pre-delay, damping, bass multiplier, early/late balance, modulation and an
// output EQ.
//
// THE PARAMETER ORDER IS A CONTRACT. The UI pushes a normalised value by INDEX, so this
// list must stay in step with `fx_registry.h` (FX_SPACE), the generated `FxRegistry.kt`
// and `FxDesign.kt`:
//   0 MIX  1 PREDELAY  2 DECAY  3 HighFreq  4 HighShelf  5 BassFreq  6 BassMult  7 Size
//   8 Attack  9 Early  10 Late  11 Rate  12 Depth  13 HighCut  14 LowCut
//
// THE FOUR FREQUENCIES ARE NORMALISED LOGARITHMICALLY, and the registry declares
// Curve::FreqLog for them. A linear mapping spends most of the travel on frequencies
// nobody uses: HighFreq ran 1..16 kHz, so the whole useful band (1..3 kHz) sat in the
// first 13 % of the knob and the top two thirds was 8..16 kHz — inaudible on a reverb
// damping control. Log gives that same band 40 % of the travel. `freqHz()` is the ONE
// place the mapping lives, so the display and the engine cannot drift apart.
#pragma once
#include "fx_core.h"

namespace aetherfx {

class SpaceReverb : public Effect {
  static constexpr int N = 8;

 public:
  // The real frequency for a normalised value, for the four log-mapped controls.
  // 3 HighFreq: 1 kHz..16 kHz   5 BassFreq: 100 Hz..1 kHz
  // 13 HighCut: 1 kHz..20 kHz   14 LowCut: 10 Hz..500 Hz
  static float freqHz(int index, float norm) {
    const float n = clamp01(norm);
    switch (index) {
      case 3: return 1000.f * std::pow(16.f, n);
      case 5: return 100.f * std::pow(10.f, n);
      case 13: return 1000.f * std::pow(20.f, n);
      case 14: return 10.f * std::pow(50.f, n);
      default: return n;
    }
  }

  struct Allpass {
    std::vector<float> b; int i = 0;
    void init(int n) { b.assign(std::max(n, 1), 0.f); i = 0; }
    inline float process(float x, float g) { float d = b[i]; float v = x + g * d; b[i] = v; if (++i >= (int)b.size()) i = 0; return d - g * v; }
    void clear() { std::fill(b.begin(), b.end(), 0.f); i = 0; }
  };

 public:
  int paramCount() const override { return 15; }

  void prepare(double sr, int) override {
    sr_ = sr;
    const double sc = sr / 48000.0;
    static const int base[N] = {1117, 1319, 1553, 1801, 2083, 2389, 2713, 3079};
    static const int apl[4] = {142, 107, 379, 277};
    for (int i = 0; i < N; ++i) {
      base_[i] = static_cast<float>(base[i] * sc);
      line_[i].init(static_cast<int>(base[i] * sc * 2.6) + 128);
      const double w = 2.0 * kPi * (0.1 + 0.035 * i) / sr;
      rotC_[i] = static_cast<float>(std::cos(w)); rotS_[i] = static_cast<float>(std::sin(w));
    }
    for (int c = 0; c < 2; ++c) {
      pre_[c].init(static_cast<int>(0.24 * sr) + 8);
      for (int k = 0; k < 4; ++k) ap_[c][k].init(static_cast<int>(apl[k] * sc));
    }
    // Early reflections: six taps off the pre-delay line, in seconds, scaled by Size.
    static const float tapS[6] = {.0113f, .0171f, .0231f, .0293f, .0371f, .0437f};
    for (int k = 0; k < 6; ++k) tap_[k] = tapS[k] * static_cast<float>(sr);
    reset();
  }

  void reset() override {
    for (auto& l : line_) l.clear();
    for (int c = 0; c < 2; ++c) { pre_[c].clear(); for (auto& a : ap_[c]) a.clear(); }
    for (int i = 0; i < N; ++i) {
      lp_[i] = 0.f; low_[i] = 0.f; len_[i] = base_[i] * 1.2f;
      osS_[i] = std::sin(i * 0.37f); osC_[i] = std::cos(i * 0.37f);
    }
    for (auto& s : sm_) s.init = false;
    mixRamp_.init = false;
    atkGate_ = 0.f; lfo_ = 0.f;
    shelf_.reset(); hcut_.reset(); lcut_.reset();
    setMeter(0, 0.f);
  }

  void process(float* io, int frames) override {
    const float cf = smoothCoef(frames, sr_, 0.05);
    const float mix = sm_[0].step(p(0), cf), pre = sm_[1].step(p(1), cf), decay = sm_[2].step(p(2), cf);
    const float hf = sm_[3].step(p(3), cf), hs = sm_[4].step(p(4), cf), bf = sm_[5].step(p(5), cf);
    const float bm = sm_[6].step(p(6), cf), size = sm_[7].step(p(7), cf), att = sm_[8].step(p(8), cf);
    const float earlyG = sm_[9].step(p(9), cf), lateG = sm_[10].step(p(10), cf);
    const float rate = sm_[11].step(p(11), cf), depth = sm_[12].step(p(12), cf);
    const float hc = sm_[13].step(p(13), cf), lc = sm_[14].step(p(14), cf);

    const float sr = static_cast<float>(sr_);
    const float rt60 = 0.1f + 29.9f * decay;
    const float sizeScale = 0.55f + 1.2f * size;
    // BassMult stretches (or shortens) the low end's RT60 without touching the rest. The
    // parameter is NORMALISED, so map it to its real 0.5x..3x span first.
    //
    // STABILITY IS THE WHOLE TRICK HERE. Giving the low band a longer RT60 than the rest
    // means a larger feedback gain in that band, and a two-band SUM drives the loop gain
    // above 1: the tail then GROWS instead of decaying (measured: +34 dB over 4 s, which is
    // how this was found). The split below is therefore COMPLEMENTARY — the high band is
    // `lp - low`, so the two bands add back to exactly the filtered signal and the composite
    // loop gain is max(gLow, gHigh) at every frequency, never their sum.
    const float rtLow = rt60 * (0.5f + 2.5f * bm);
    float target[N], gLow[N], gHigh[N];
    for (int i = 0; i < N; ++i) {
      target[i] = base_[i] * sizeScale;
      gLow[i] = std::pow(10.0f, -3.0f * target[i] / (rtLow * sr));
      gHigh[i] = std::pow(10.0f, -3.0f * target[i] / (rt60 * sr));
    }
    // Damping: a one-pole at HighFreq on the feedback path. Log-mapped (see freqHz).
    const float dampHz = freqHz(3, hf);
    const float dampA = 1.0f - std::exp(-2.0f * static_cast<float>(kPi) * dampHz / sr);
    // The bass split, at BassFreq, as ONE pole. The complementary high band is formed as
    // `lp - low`, which keeps the two bands summing exactly to the filtered signal: with two
    // cascaded poles the split was no longer complementary and the loop became unstable.
    const float bassHz = freqHz(5, bf);
    const float bassA = 1.0f - std::exp(-2.0f * static_cast<float>(kPi) * bassHz / sr);
    // Attack: the tail fades in over `atkTime` when the input becomes active. At zero the
    // gate is transparent; a long attack holds the tail back on short notes, which is what
    // the control means on the reference. Quadratic, because a linear map made the 50 %
    // default a 175 ms fade — audible as a swell on every chord.
    //
    // The FLOOR is 1 ms, not the 0.8 ms this used to be: with 0.8 ms the bottom 15 % of
    // the travel was a single value (measured: 7 dead steps out of 50), because any
    // attack shorter than the shortest delay line is already "instant". 1 ms keeps the
    // control monotone right down to zero without lengthening a real attack.
    const float atkA = 1.0f - std::exp(-1.0f / ((0.001f + 0.35f * att * att) * sr));
    const float relA = 1.0f - std::exp(-1.0f / (0.25f * sr));
    const float preSamp = pre * 0.20f * sr;
    const float apg = 0.35f + 0.35f * lateG;
    const float modSamp = depth * 0.003f * sr;
    const float theta = mix * 1.57079632679f, dryG = std::cos(theta), wetG = std::sin(theta);
    const float wetStart = mixRamp_.init ? mixRamp_.cur : wetG;
    const float wetStep = (wetG - wetStart) / frames;
    float wg = wetStart;
    mixRamp_.cur = wetG; mixRamp_.init = true;
    // Output EQ, re-designed once per block: a shelf at HighFreq, then the two cuts.
    // The cuts are log-mapped, like the damping above.
    shelf_.setHighShelf(sr_, dampHz, -24.0 + 24.0 * hs);
    hcut_.setLowpass(sr_, freqHz(13, hc), 0.707);
    lcut_.setHighpass(sr_, freqHz(14, lc), 0.707);
    const float lfoInc = 2.0f * static_cast<float>(kPi) * (0.1f + 9.9f * rate) / sr;
    float peak = 0.f;
    for (int n = 0; n < frames; ++n) {
      wg += wetStep;
      lfo_ += lfoInc; if (lfo_ > 6.28318530718f) lfo_ -= 6.28318530718f;
      const float lfoS = std::sin(lfo_), lfoC = std::cos(lfo_);
      const float inL = io[2 * n], inR = io[2 * n + 1];
      pre_[0].write(inL); pre_[1].write(inR);
      float xl = pre_[0].read(std::max(preSamp, 1.f)), xr = pre_[1].read(std::max(preSamp, 1.f));
      // Early reflections: six taps, alternating polarity, so the field is not a single echo.
      float eL = 0.f, eR = 0.f;
      for (int k = 0; k < 6; ++k) {
        const float d = std::max(tap_[k] * sizeScale, 1.f);
        const float sgn = (k & 1) ? -1.f : 1.f;
        eL += sgn * pre_[0].read(d);
        eR += sgn * pre_[1].read(d);
      }
      eL *= 0.32f; eR *= 0.32f;
      for (int k = 0; k < 4; ++k) { xl = ap_[0][k].process(xl, apg); xr = ap_[1][k].process(xr, apg); }
      const float lvl = std::max(std::fabs(xl), std::fabs(xr));
      atkGate_ += (lvl > 1e-4f ? atkA : relA) * ((lvl > 1e-4f ? 1.f : 0.f) - atkGate_);
      const float tx = atkGate_, tl = xl * tx, tr = xr * tx;
      float y[N], sum = 0.f;
      for (int i = 0; i < N; ++i) {
        len_[i] += std::max(-0.01f, std::min(0.01f, target[i] - len_[i]));
        const float ns = osS_[i] * rotC_[i] + osC_[i] * rotS_[i];
        osC_[i] = osC_[i] * rotC_[i] - osS_[i] * rotS_[i];
        osS_[i] = ns;
        const float mod = modSamp * (osC_[i] * lfoC + osS_[i] * lfoS);
        const float d = line_[i].read(len_[i] + mod + modSamp + 1.f);
        lp_[i] += dampA * (d - lp_[i]);
        low_[i] += bassA * (lp_[i] - low_[i]);
        y[i] = gLow[i] * low_[i] + gHigh[i] * (lp_[i] - low_[i]);
        sum += y[i];
      }
      const float k2 = 2.0f / N;
      for (int i = 0; i < N; ++i) line_[i].write(0.35f * ((i & 1) ? tr : tl) + y[i] - k2 * sum);
      const float wl = (y[0] - y[1] + y[2] - y[3] + y[4] - y[5] + y[6] - y[7]) * 0.5f;
      const float wr = (y[0] + y[1] - y[2] - y[3] + y[4] + y[5] - y[6] - y[7]) * 0.5f;
      const float sL = shelf_.process(0, wl), sR = shelf_.process(1, wr);
      // The EQ shapes the REVERB, not the dry signal. Applying it to the dry as well left
      // the 10 Hz high-pass ringing after the input stopped, so Mix at zero was not silent.
      float wL = earlyG * eL + lateG * sL;
      float wR = earlyG * eR + lateG * sR;
      wL = lcut_.process(0, hcut_.process(0, wL));
      wR = lcut_.process(1, hcut_.process(1, wR));
      io[2 * n] = inL * dryG + wL * wg;
      io[2 * n + 1] = inR * dryG + wR * wg;
      peak = std::max(peak, std::max(std::fabs(io[2 * n]), std::fabs(io[2 * n + 1])));
    }
    setMeter(0, peak);
  }

 private:
  double sr_ = 48000.0;
  float base_[N] = {};
  float len_[N] = {}, lp_[N] = {}, low_[N] = {};
  float osS_[N] = {}, osC_[N] = {}, rotS_[N] = {}, rotC_[N] = {};
  float tap_[6] = {};
  DelayLine line_[N], pre_[2];
  Allpass ap_[2][4];
  Biquad shelf_, hcut_, lcut_;
  BlockSmoother sm_[15];
  Ramp mixRamp_;
  float atkGate_ = 0.f, lfo_ = 0.f;
};

}  // namespace aetherfx
