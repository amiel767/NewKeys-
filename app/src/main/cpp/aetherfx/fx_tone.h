// Prism Tone: 3-band EQ (low shelf, parametric mid, high shelf) + output trim. Minimum phase, zero latency.
#pragma once
#include "fx_core.h"

namespace aetherfx {

class ToneEq : public Effect {
 public:
  int paramCount() const override { return 8; }
  void prepare(double sr, int) override { sr_ = sr; reset(); }
  void reset() override {
    for (auto& b : bq_) b.reset();
    for (auto& s : sm_) s.init = false;
    outRamp_.init = false;
    updateCoefs(32);
  }
  // analytic response of the current smoothed filters, for UI curve drawing
  double responseDb(double f) const { return bq_[0].magDb(sr_, f) + bq_[1].magDb(sr_, f) + bq_[2].magDb(sr_, f); }

  void process(float* io, int frames) override {
    const float outT = dbToLin((p(7) - 0.5f) * 24.f);
    float outStep = outRamp_.begin(outT, frames);
    float og = outRamp_.cur;
    outRamp_.cur = outT;
    for (int pos = 0; pos < frames; pos += 32) {
      const int n = std::min(32, frames - pos);
      updateCoefs(n);
      for (int i = pos; i < pos + n; ++i) {
        og += outStep;
        float l = io[2 * i], r = io[2 * i + 1];
        l = bq_[2].process(0, bq_[1].process(0, bq_[0].process(0, l)));
        r = bq_[2].process(1, bq_[1].process(1, bq_[0].process(1, r)));
        io[2 * i] = l * og; io[2 * i + 1] = r * og;
      }
    }
  }

 private:
  void updateCoefs(int chunk) {
    const float c = smoothCoef(chunk, sr_, 0.03);
    const double lf = 20.0 * std::pow(1000.0, sm_[0].step(p(0), c)), lg = (sm_[1].step(p(1), c) - 0.5) * 24.0;
    const double mf = 20.0 * std::pow(1000.0, sm_[2].step(p(2), c)), mg = (sm_[3].step(p(3), c) - 0.5) * 24.0, mq = 0.3 + 3.7 * sm_[4].step(p(4), c);
    const double hf = 20.0 * std::pow(1000.0, sm_[5].step(p(5), c)), hg = (sm_[6].step(p(6), c) - 0.5) * 24.0;
    bq_[0].setLowShelf(sr_, lf, lg);
    bq_[1].setPeaking(sr_, mf, mq, mg);
    bq_[2].setHighShelf(sr_, hf, hg);
  }
  double sr_ = 48000.0;
  Biquad bq_[3];
  BlockSmoother sm_[7];
  Ramp outRamp_;
};

}  // namespace aetherfx
