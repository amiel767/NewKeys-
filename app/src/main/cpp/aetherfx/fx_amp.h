// Aether Amp: electric-guitar amplifier. Pre-gain -> 2x oversampled tube-style stages -> tone stack -> power stage -> speaker cabinet + mic.
// Params (normalised): 0 Gain 1 Bass 2 Mid 3 Treble 4 Presence 5 Master 6 Channel(0 Clean,.5 Crunch,1 Lead) 7 MicPos 8 MicDist 9 Boost(on>.5) 10 Tremolo(on>.5) 11 Level(-24..+6 dB)
#pragma once
#include "fx_core.h"

namespace aetherfx {

class AmpFx : public Effect {
 public:
  int paramCount() const override { return 12; }
  void prepare(double sr, int) override {
    sr_ = sr; const double s2 = 2 * sr;
    for (int i = 0; i < 2; ++i) { up_[i].setLowpass(s2, 0.42 * sr, i ? 1.3066 : 0.5412); dn_[i].setLowpass(s2, 0.42 * sr, i ? 1.3066 : 0.5412); }
    preHp_.setHighpass(sr, 90, 0.7); inter_.setHighpass(s2, 240, 0.7);
    cabHp_.setHighpass(sr, 85, 0.7);
    reset();
  }
  void reset() override {
    for (auto& b : up_) b.reset(); for (auto& b : dn_) b.reset();
    preHp_.reset(); inter_.reset(); cabHp_.reset(); for (auto& b : ts_) b.reset(); for (auto& b : cab_) b.reset();
    for (auto& s : sm_) s.init = false; lfo_ = 0; dirty_ = true; lpF_ = -1;
  }
  void process(float* io, int n) override {
    NoDenormals nd;
    const float cf = smoothCoef(n, sr_, 0.02);
    const float gain = sm_[0].step(p(0), cf), bass = sm_[1].step(p(1), cf), mid = sm_[2].step(p(2), cf), treb = sm_[3].step(p(3), cf), pres = sm_[4].step(p(4), cf);
    const float master = sm_[5].step(p(5), cf), mpos = sm_[6].step(p(7), cf), mdist = sm_[7].step(p(8), cf), lvl = sm_[8].step(p(11), cf);
    const int chan = static_cast<int>(std::lround(p(6) * 2.f));
    const bool boost = p(9) > 0.5f, trem = p(10) > 0.5f;
    if (std::fabs(bass - cB_[0]) > 0.002f || std::fabs(mid - cB_[1]) > 0.002f || std::fabs(treb - cB_[2]) > 0.002f || std::fabs(pres - cB_[3]) > 0.002f || dirty_) {
      cB_[0] = bass; cB_[1] = mid; cB_[2] = treb; cB_[3] = pres;
      ts_[0].setLowShelf(sr_, 120, (bass - 0.5f) * 24.f); ts_[1].setPeaking(sr_, 700, 0.7, (mid - 0.5f) * 20.f);
      ts_[2].setHighShelf(sr_, 3200, (treb - 0.5f) * 24.f); ts_[3].setHighShelf(sr_, 5500, (pres - 0.5f) * 16.f);
    }
    if (std::fabs(mpos - cM_[0]) > 0.002f || std::fabs(mdist - cM_[1]) > 0.002f || dirty_) {
      cM_[0] = mpos; cM_[1] = mdist;
      cab_[0].setPeaking(sr_, 110, 1.0, 3.0); cab_[1].setPeaking(sr_, 450, 1.0, -3.0);
      cab_[2].setPeaking(sr_, 2800, 1.2, 4.0 * (1.0f - mpos)); cab_[3].setLowpass(sr_, 6500.0 - 3300.0 * mpos, 0.707);
      cab_[4].setLowShelf(sr_, 180, 6.0 * (1.0f - mdist)); cab_[5].setLowpass(sr_, 7800.0 - 1500.0 * mpos, 0.6);
    }
    dirty_ = false;
    const float inG = dbToLin(boost ? 9.f : 0.f), outLvl = dbToLin(-24.f + 30.f * lvl);
    float g1, g2 = 1.f, bias = 0.f, comp;
    if (chan == 0) { g1 = 1.f + gain * 5.f; comp = 1.f / (1.f + 0.38f * (g1 - 1.f)); }
    else if (chan == 1) { g1 = 3.f + gain * 22.f; bias = 0.15f; comp = 1.f / (0.9f + 0.1f * g1); }
    else { g1 = 6.f + gain * 38.f; g2 = 3.f + gain * 10.f; bias = 0.15f; comp = 1.f / (0.8f + 0.08f * g1); }
    const float dp = 0.6f + master * 2.5f, pwr = 0.5f + master * 1.5f, trInc = static_cast<float>(5.0 / sr_);
    for (int i = 0; i < n; ++i) {
      const float tg = trem ? 1.f - 0.5f * (0.5f + 0.5f * sineTable().at(lfo_)) : 1.f;
      lfo_ += trInc; if (lfo_ >= 1.f) lfo_ -= 1.f;
      for (int ch = 0; ch < 2; ++ch) {
        float x = preHp_.process(ch, io[2 * i + ch]) * inG, out = 0.f;
        for (int k = 0; k < 2; ++k) {
          float s = k == 0 ? 2.f * x : 0.f;
          s = up_[1].process(ch, up_[0].process(ch, s));
          float y = std::tanh(g1 * s + bias) - std::tanh(bias);
          if (chan == 2) { y = inter_.process(ch, y); y = std::tanh(g2 * y); }
          y = dn_[1].process(ch, dn_[0].process(ch, y));
          if (k == 1) out = y;
        }
        out *= comp * 1.4f;
        for (int b = 0; b < 4; ++b) out = ts_[b].process(ch, out);
        out = std::tanh(dp * out) / dp * pwr;
        out = cabHp_.process(ch, out);
        for (int b = 0; b < 6; ++b) out = cab_[b].process(ch, out);
        out *= tg * outLvl;
        io[2 * i + ch] = std::tanh(out * 0.9f) / 0.9f;
      }
    }
  }

 private:
  double sr_ = 48000.0;
  Biquad up_[2], dn_[2], preHp_, inter_, cabHp_, ts_[4], cab_[6];
  BlockSmoother sm_[9];
  float cB_[4] = {-1, -1, -1, -1}, cM_[2] = {-1, -1}, lfo_ = 0.f, lpF_ = -1.f;
  bool dirty_ = true;
};

}  // namespace aetherfx
