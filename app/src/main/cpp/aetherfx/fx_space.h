// Aether Space: 8-line feedback delay network reverb (Householder matrix), input diffusion, pre-delay, damping, modulation.
#pragma once
#include "fx_core.h"

namespace aetherfx {

class SpaceReverb : public Effect {
  static constexpr int N = 8;
  struct Allpass {
    std::vector<float> b; int i = 0;
    void init(int n) { b.assign(std::max(n, 1), 0.f); i = 0; }
    inline float process(float x, float g) { float d = b[i]; float v = x + g * d; b[i] = v; if (++i >= (int)b.size()) i = 0; return d - g * v; }
    void clear() { std::fill(b.begin(), b.end(), 0.f); i = 0; }
  };

 public:
  int paramCount() const override { return 7; }
  void prepare(double sr, int) override {
    sr_ = sr;
    const double sc = sr / 48000.0;
    static const int base[N] = {1117, 1319, 1553, 1801, 2083, 2389, 2713, 3079};
    static const int apl[4] = {142, 107, 379, 277};
    for (int i = 0; i < N; ++i) {
      base_[i] = static_cast<float>(base[i] * sc); line_[i].init(static_cast<int>(base[i] * sc * 1.9) + 64);
      const double w = 2.0 * kPi * (0.1 + 0.035 * i) / sr; rotC_[i] = static_cast<float>(std::cos(w)); rotS_[i] = static_cast<float>(std::sin(w));
    }
    for (int c = 0; c < 2; ++c) {
      pre_[c].init(static_cast<int>(0.13 * sr) + 8);
      for (int k = 0; k < 4; ++k) ap_[c][k].init(static_cast<int>(apl[k] * sc));
    }
    reset();
  }
  void reset() override {
    for (auto& l : line_) l.clear();
    for (int c = 0; c < 2; ++c) { pre_[c].clear(); for (auto& a : ap_[c]) a.clear(); }
    for (int i = 0; i < N; ++i) { lp_[i] = 0.f; len_[i] = base_[i] * 1.2f; osS_[i] = std::sin(i * 0.37f); osC_[i] = std::cos(i * 0.37f); }
    sm_[0].init = false; sm_[1].init = false; sm_[2].init = false; sm_[3].init = false; sm_[4].init = false; sm_[5].init = false; sm_[6].init = false;
    mixRamp_.init = false; widthRamp_.init = false;
    setMeter(0, 0.f);
  }
  void process(float* io, int frames) override {
    const float cf = smoothCoef(frames, sr_, 0.05);
    const float size = sm_[0].step(p(0), cf), decay = sm_[1].step(p(1), cf), pre = sm_[2].step(p(2), cf), damp = sm_[3].step(p(3), cf);
    const float width = sm_[4].step(p(4), cf), mix = sm_[5].step(p(5), cf), diff = sm_[6].step(p(6), cf);
    const float rt60 = 0.4f + 7.6f * decay;
    const float sizeScale = 0.55f + 1.2f * size;
    float target[N], g[N];
    for (int i = 0; i < N; ++i) { target[i] = base_[i] * sizeScale; g[i] = std::pow(10.0f, -3.0f * target[i] / (rt60 * static_cast<float>(sr_))); }
    const float dampA = 1.0f - std::exp(static_cast<float>(-2.0 * kPi * (1000.0 + 11000.0 * damp) / sr_));
    const float preSamp = pre * 0.12f * static_cast<float>(sr_);
    const float apg = 0.3f + 0.45f * diff;
    const float modDepth = (2.0f + 10.0f * diff) * static_cast<float>(sr_ / 48000.0);
    const float theta = mix * 1.57079632679f, dryG = std::cos(theta), wetG = std::sin(theta);
    const float sideG = width * 1.4f;
    const float wetStart = mixRamp_.init ? mixRamp_.cur : wetG;
    const float wetStep = (wetG - wetStart) / frames;
    float wg = wetStart;
    mixRamp_.cur = wetG; mixRamp_.init = true;
    float peak = 0.f;
    for (int n = 0; n < frames; ++n) {
      wg += wetStep;
      const float inL = io[2 * n], inR = io[2 * n + 1];
      pre_[0].write(inL); pre_[1].write(inR);
      float xl = pre_[0].read(std::max(preSamp, 1.f)), xr = pre_[1].read(std::max(preSamp, 1.f));
      for (int k = 0; k < 4; ++k) { xl = ap_[0][k].process(xl, apg); xr = ap_[1][k].process(xr, apg); }
      float y[N], sum = 0.f;
      for (int i = 0; i < N; ++i) {
        len_[i] += std::max(-0.01f, std::min(0.01f, target[i] - len_[i]));
        const float ns = osS_[i] * rotC_[i] + osC_[i] * rotS_[i];
        osC_[i] = osC_[i] * rotC_[i] - osS_[i] * rotS_[i];
        osS_[i] = ns;
        const float d = line_[i].read(len_[i] + modDepth * osS_[i] + modDepth + 1.f);
        lp_[i] += dampA * (d - lp_[i]);
        y[i] = g[i] * lp_[i];
        sum += y[i];
      }
      const float k2 = 2.0f / N;
      for (int i = 0; i < N; ++i) line_[i].write(0.35f * ((i & 1) ? xr : xl) + y[i] - k2 * sum);
      const float wl = (y[0] - y[1] + y[2] - y[3] + y[4] - y[5] + y[6] - y[7]) * 0.5f;
      const float wr = (y[0] + y[1] - y[2] - y[3] + y[4] + y[5] - y[6] - y[7]) * 0.5f;
      const float m = 0.5f * (wl + wr), s = 0.5f * (wl - wr) * sideG;
      const float oL = inL * dryG + (m + s) * wg, oR = inR * dryG + (m - s) * wg;
      io[2 * n] = oL; io[2 * n + 1] = oR;
      peak = std::max(peak, std::max(std::fabs(oL), std::fabs(oR)));
    }
    setMeter(0, peak);
  }

 private:
  double sr_ = 48000.0;
  float base_[N] = {};
  float len_[N] = {}, lp_[N] = {}, osS_[N] = {}, osC_[N] = {}, rotS_[N] = {}, rotC_[N] = {};
  DelayLine line_[N], pre_[2];
  Allpass ap_[2][4];
  BlockSmoother sm_[7];
  Ramp mixRamp_, widthRamp_;
};

}  // namespace aetherfx
