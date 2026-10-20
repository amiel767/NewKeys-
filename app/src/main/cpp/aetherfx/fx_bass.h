// Aether Bass: bass-amp head. Compressor -> (clean DI path + tube preamp + cabinet) blend -> 5-band graphic EQ.
// Params: 0 Drive 1 Comp 2 Blend(DI->amp) 3..7 EQ 63Hz/250Hz/1k/3k/8k (.5 = flat, +-12 dB) 8 Cab(0 1x15,.5 4x10,1 8x10) 9 Level(-24..+6 dB)
// Meters: 0 = gain reduction (dB), 1 = output peak (linear)
#pragma once
#include "fx_core.h"

namespace aetherfx {

class BassAmp : public Effect {
 public:
  int paramCount() const override { return 10; }
  void prepare(double sr, int) override { sr_ = sr; diHp_.setHighpass(sr, 30, 0.7); preHp_.setHighpass(sr, 40, 0.7); reset(); }
  void reset() override {
    diHp_.reset(); preHp_.reset(); for (auto& b : cab_) b.reset(); for (auto& b : eq_) b.reset();
    env_ = 0.f; gain_ = 1.f; for (auto& s : sm_) s.init = false; cabC_ = -1; eqC_[0] = -9; setMeter(0, 0.f); setMeter(1, 0.f);
  }
  void process(float* io, int n) override {
    NoDenormals nd;
    const float cf = smoothCoef(n, sr_, 0.02);
    const float drive = sm_[0].step(p(0), cf), comp = sm_[1].step(p(1), cf), blend = sm_[2].step(p(2), cf), lvl = sm_[8].step(p(9), cf);
    const int cab = static_cast<int>(std::lround(p(8) * 2.f));
    bool eqDirty = false;
    for (int b = 0; b < 5; ++b) { const float v = sm_[3 + b].step(p(3 + b), cf); if (std::fabs(v - eqC_[b]) > 0.002f) { eqC_[b] = v; eqDirty = true; } }
    if (eqDirty) {
      eq_[0].setLowShelf(sr_, 63, (eqC_[0] - 0.5f) * 24.f); eq_[1].setPeaking(sr_, 250, 1.0, (eqC_[1] - 0.5f) * 24.f);
      eq_[2].setPeaking(sr_, 1000, 1.0, (eqC_[2] - 0.5f) * 24.f); eq_[3].setPeaking(sr_, 3000, 1.0, (eqC_[3] - 0.5f) * 24.f);
      eq_[4].setHighShelf(sr_, 8000, (eqC_[4] - 0.5f) * 24.f);
    }
    if (cab != cabC_) {
      cabC_ = cab;
      if (cab == 0) { cab_[0].setLowpass(sr_, 3800, 0.7); cab_[1].setPeaking(sr_, 70, 1.0, 3.0); cab_[2].setPeaking(sr_, 400, 1.0, -2.0); cab_[3].setHighpass(sr_, 45, 0.7); }
      else if (cab == 1) { cab_[0].setLowpass(sr_, 5500, 0.7); cab_[1].setPeaking(sr_, 90, 1.0, 1.5); cab_[2].setPeaking(sr_, 800, 1.0, 2.0); cab_[3].setHighpass(sr_, 50, 0.7); }
      else { cab_[0].setLowpass(sr_, 6500, 0.7); cab_[1].setPeaking(sr_, 100, 1.0, 2.0); cab_[2].setPeaking(sr_, 600, 0.8, 1.0); cab_[3].setHighpass(sr_, 40, 0.7); }
    }
    const float ratio = 1.5f + comp * 6.5f, thrDb = -18.f, makeup = dbToLin(0.45f * (-thrDb) * (1.f - 1.f / ratio) * comp);
    const float aA = 1.f - std::exp(static_cast<float>(-1.0 / (0.008 * sr_))), aR = 1.f - std::exp(static_cast<float>(-1.0 / (0.12 * sr_)));
    const float g1 = 1.f + drive * 9.f, outG = dbToLin(-24.f + 30.f * lvl), ampComp = 0.7f / (1.f + 0.25f * (g1 - 1.f));
    float grMax = 0.f, pk = 0.f;
    for (int i = 0; i < n; ++i) {
      const float l = io[2 * i], r = io[2 * i + 1], lv = std::max(std::fabs(l), std::fabs(r));
      env_ += (lv > env_ ? aA : aR) * (lv - env_);
      const float db = linToDb(env_), over = db - thrDb, grDb = over > 0.f ? over * (1.f - 1.f / ratio) * (0.4f + 0.6f * comp) : 0.f;
      const float tg = dbToLin(-grDb); gain_ += (tg - gain_) * 0.01f; grMax = std::max(grMax, grDb);
      for (int ch = 0; ch < 2; ++ch) {
        const float x = io[2 * i + ch] * gain_ * (comp > 0.001f ? makeup : 1.f);
        const float di = diHp_.process(ch, x);
        float a = preHp_.process(ch, x);
        a = (std::tanh(g1 * a + 0.1f) - std::tanh(0.1f)) * ampComp;
        for (int b = 0; b < 4; ++b) a = cab_[b].process(ch, a);
        float y = di * (1.f - blend) + a * blend * 1.6f;
        for (int b = 0; b < 5; ++b) y = eq_[b].process(ch, y);
        y = std::tanh(y * outG * 0.9f) / 0.9f;
        io[2 * i + ch] = y; pk = std::max(pk, std::fabs(y));
      }
    }
    setMeter(0, grMax); setMeter(1, pk);
  }

 private:
  double sr_ = 48000.0;
  Biquad diHp_, preHp_, cab_[4], eq_[5];
  BlockSmoother sm_[9];
  float env_ = 0.f, gain_ = 1.f, eqC_[5] = {-9, -9, -9, -9, -9};
  int cabC_ = -1;
};

}  // namespace aetherfx
