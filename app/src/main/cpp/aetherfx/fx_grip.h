// Iron Grip: feed-forward stereo-linked compressor with soft knee, log-domain smoothing, zero latency.
#pragma once
#include "fx_core.h"
#include "fx_registry.h"

namespace aetherfx {

class GripCompressor : public Effect {
 public:
  int paramCount() const override { return 6; }
  void prepare(double sr, int) override { sr_ = sr; reset(); }
  void reset() override { env_ = 0.f; setMeter(0, 0.f); setMeter(1, -120.f); setMeter(2, -120.f); }
  // meter(0): gain reduction in dB (>= 0), meter(1): input peak dB, meter(2): output peak dB
  void process(float* io, int frames) override {
    const float thr = -60.f + 60.f * p(0);
    const float ratio = kRatios[static_cast<int>(std::lround(p(1) * 5.f))];
    const float atkMs = 0.1f + 99.9f * p(2), relMs = 10.f + 990.f * p(3);
    const float knee = 24.f * p(4), makeup = 24.f * p(5);
    const float aA = std::exp(-1.0f / (0.001f * atkMs * static_cast<float>(sr_)));
    const float aR = std::exp(-1.0f / (0.001f * relMs * static_cast<float>(sr_)));
    const float slope = 1.0f / ratio - 1.0f;
    float inPk = 0.f, outPk = 0.f, grMax = 0.f;
    for (int n = 0; n < frames; ++n) {
      float l = io[2 * n], r = io[2 * n + 1];
      const float pk = std::max(std::fabs(l), std::fabs(r));
      inPk = std::max(inPk, pk);
      const float x = linToDb(pk);
      const float over = x - thr;
      float gr;  // negative or zero
      if (2.f * over < -knee) gr = 0.f;
      else if (2.f * std::fabs(over) <= knee && knee > 0.f) { const float t = over + knee * 0.5f; gr = slope * t * t / (2.f * knee); }
      else gr = slope * over;
      if (gr < env_) env_ = aA * env_ + (1.f - aA) * gr; else env_ = aR * env_ + (1.f - aR) * gr;
      grMax = std::max(grMax, -env_);
      const float g = std::pow(10.0f, (env_ + makeup) * 0.05f);
      l *= g; r *= g;
      io[2 * n] = l; io[2 * n + 1] = r;
      outPk = std::max(outPk, std::max(std::fabs(l), std::fabs(r)));
    }
    setMeter(0, grMax); setMeter(1, linToDb(inPk)); setMeter(2, linToDb(outPk));
  }

 private:
  double sr_ = 48000.0;
  float env_ = 0.f;
};

}  // namespace aetherfx
