// Goodizer: maximizer = drive + lookahead limiter (min-filter then box average => ceiling is guaranteed) + gentle colour.
// lookaheadMs = 0 for tracks (zero latency), ~1 ms for the master (latency is reported).
#pragma once
#include "fx_core.h"

namespace aetherfx {

class CeilingMaximizer : public Effect {
 public:
  explicit CeilingMaximizer(float lookaheadMs = 0.f) : lookMs_(lookaheadMs) {}
  int paramCount() const override { return 6; }
  void prepare(double sr, int) override {
    sr_ = sr;
    L_ = static_cast<int>(std::lround(lookMs_ * 0.001 * sr));
    dly_[0].init(L_ + 8); dly_[1].init(L_ + 8);
    dryD_[0].init(L_ + 8); dryD_[1].init(L_ + 8);
    reqRing_.assign(L_ + 1, 1.f); minRing_.assign(L_ + 1, 1.f);
    reset();
  }
  void reset() override {
    dly_[0].clear(); dly_[1].clear(); dryD_[0].clear(); dryD_[1].clear();
    std::fill(reqRing_.begin(), reqRing_.end(), 1.f); std::fill(minRing_.begin(), minRing_.end(), 1.f);
    sumMin_ = static_cast<double>(L_ + 1); ri_ = 0; gRel_ = 1.f;
    for (auto& s : sm_) s.init = false;
    setMeter(0, 0.f); setMeter(1, -120.f);
  }
  int latencySamples() const override { return L_; }
  // meter(0): gain reduction dB, meter(1): output peak dB
  void process(float* io, int frames) override {
    const float c = smoothCoef(frames, sr_, 0.03);
    const float amount = sm_[0].step(p(0), c), ceilDb = -6.f * (1.f - sm_[1].step(p(1), c)) * 1.f;
    const float ceil = dbToLin(ceilDb), drive = dbToLin(amount * 18.f);
    const float relMs = 10.f + 490.f * p(2), width = sm_[3].step(p(3), c), mix = sm_[4].step(p(4), c);
    const float outG = dbToLin((p(5) - 0.5f) * 24.f);
    const float relC = std::exp(-1.0f / (0.001f * relMs * static_cast<float>(sr_)));
    const float color = amount * 0.35f, sideG = 1.f + width * 0.6f;
    const float theta = mix * 1.57079632679f, dryG = std::cos(theta), wetG = std::sin(theta);
    const float mixDry = (mix >= 0.999f) ? 0.f : dryG, mixWet = (mix >= 0.999f) ? 1.f : wetG;
    const float invTanh = 1.0f / std::tanh(1.2f);
    float outPk = 0.f, grMax = 0.f;
    for (int n = 0; n < frames; ++n) {
      const float inL = io[2 * n], inR = io[2 * n + 1];
      const float m = 0.5f * (inL + inR), s = 0.5f * (inL - inR) * sideG;
      const float xl = (m + s) * drive, xr = (m - s) * drive;
      const float pk = std::max(std::fabs(xl), std::fabs(xr));
      const float req = pk > ceil ? ceil / pk : 1.f;
      float mn;
      if (L_ == 0) { mn = req; }
      else {
        reqRing_[ri_] = req;
        mn = 1.f;
        for (int k = 0; k <= L_; ++k) mn = std::min(mn, reqRing_[k]);
      }
      float avg;
      if (L_ == 0) { avg = mn; }
      else {
        sumMin_ += mn - minRing_[ri_];
        minRing_[ri_] = mn;
        avg = static_cast<float>(sumMin_ / (L_ + 1));
        if (++ri_ > L_) ri_ = 0;
      }
      gRel_ = std::min(avg, gRel_ + (1.f - gRel_) * (1.f - relC));
      float yl, yr, dl, dr;
      if (L_ == 0) { yl = xl; yr = xr; dl = inL; dr = inR; }
      else {
        dly_[0].write(xl); dly_[1].write(xr); dryD_[0].write(inL); dryD_[1].write(inR);
        yl = dly_[0].readInt(L_ + 1); yr = dly_[1].readInt(L_ + 1); dl = dryD_[0].readInt(L_ + 1); dr = dryD_[1].readInt(L_ + 1);
      }
      yl *= gRel_; yr *= gRel_;
      if (color > 0.f) {
        yl = yl * (1.f - color) + color * ceil * std::tanh(1.2f * yl / ceil) * invTanh;
        yr = yr * (1.f - color) + color * ceil * std::tanh(1.2f * yr / ceil) * invTanh;
      }
      const float oL = (dl * mixDry + yl * mixWet) * outG, oR = (dr * mixDry + yr * mixWet) * outG;
      io[2 * n] = oL; io[2 * n + 1] = oR;
      outPk = std::max(outPk, std::max(std::fabs(oL), std::fabs(oR)));
      grMax = std::max(grMax, -linToDb(gRel_));
    }
    setMeter(0, grMax); setMeter(1, linToDb(outPk));
  }

 private:
  double sr_ = 48000.0;
  float lookMs_;
  int L_ = 0, ri_ = 0;
  DelayLine dly_[2], dryD_[2];
  std::vector<float> reqRing_, minRing_;
  double sumMin_ = 1.0;
  float gRel_ = 1.f;
  BlockSmoother sm_[5];
};

}  // namespace aetherfx
