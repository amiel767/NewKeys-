// Aether Echo: four independent taps (time/level each), cross-fed ping-pong, feedback with tone filter, tape wow.
#pragma once
#include "fx_core.h"

namespace aetherfx {

class EchoDelay : public Effect {
 public:
  int paramCount() const override { return 12; }
  void prepare(double sr, int) override {
    sr_ = sr;
    for (auto& d : dl_) d.init(static_cast<int>(1.15 * sr) + 16);
    reset();
  }
  void reset() override {
    for (auto& d : dl_) d.clear();
    for (auto& f : fbLp_) f.reset();
    for (int k = 0; k < 4; ++k) { tapT_[k] = -1.f; }
    lfo1_ = lfo2_ = 0.f;
    mix_.init = false;
    setMeter(0, 0.f);
  }
  void process(float* io, int frames) override {
    float tgt[4], lvl[4], sumLvl = 0.f;
    for (int k = 0; k < 4; ++k) {
      tgt[k] = (10.f + 990.f * p(2 * k)) * 0.001f * static_cast<float>(sr_);
      lvl[k] = p(2 * k + 1);
      sumLvl += lvl[k];
      if (tapT_[k] < 0.f) tapT_[k] = tgt[k];
    }
    const float fb = p(8) * 0.95f / std::max(1.f, sumLvl);
    const float cutoff = 1000.f + 11000.f * p(9);
    for (auto& f : fbLp_) f.setCutoff(sr_, cutoff);
    const float wow = p(10), theta = p(11) * 1.57079632679f;
    const float dryG = std::cos(theta), wetEnd = std::sin(theta);
    const float wetStart = mix_.init ? mix_.cur : wetEnd, wetStep = (wetEnd - wetStart) / frames;
    float wg = wetStart; mix_.cur = wetEnd; mix_.init = true;
    const float w1 = 6.2831853f * 0.6f / static_cast<float>(sr_), w2 = 6.2831853f * 6.3f / static_cast<float>(sr_);
    float peak = 0.f;
    for (int n = 0; n < frames; ++n) {
      wg += wetStep;
      const float inL = io[2 * n], inR = io[2 * n + 1];
      lfo1_ += w1; lfo2_ += w2;
      if (lfo1_ > 6.2831853f) lfo1_ -= 6.2831853f;
      if (lfo2_ > 6.2831853f) lfo2_ -= 6.2831853f;
      const float mod = wow * (std::sin(lfo1_) * 0.0022f + std::sin(lfo2_) * 0.0003f) * static_cast<float>(sr_);
      float oL = 0.f, oR = 0.f;
      for (int k = 0; k < 4; ++k) {
        tapT_[k] += std::max(-0.05f, std::min(0.05f, tgt[k] - tapT_[k]));
        const float d = std::max(1.f, tapT_[k] + mod + 3.f);
        const float yl = dl_[0].read(d), yr = dl_[1].read(d);
        if (k & 1) { oL += lvl[k] * yr; oR += lvl[k] * yl; } else { oL += lvl[k] * yl; oR += lvl[k] * yr; }
      }
      const float fL = fbLp_[0].process(oL) * fb, fR = fbLp_[1].process(oR) * fb;
      dl_[0].write(inL + fR);
      dl_[1].write(inR + fL);
      const float outL = inL * dryG + oL * wg, outR = inR * dryG + oR * wg;
      io[2 * n] = outL; io[2 * n + 1] = outR;
      peak = std::max(peak, std::max(std::fabs(outL), std::fabs(outR)));
    }
    setMeter(0, peak);
  }

 private:
  double sr_ = 48000.0;
  DelayLine dl_[2];
  OnePole fbLp_[2];
  float tapT_[4] = {-1, -1, -1, -1};
  float lfo1_ = 0.f, lfo2_ = 0.f;
  Ramp mix_;
};

}  // namespace aetherfx
