// Aether Voices: vowel formant filter + choir ensemble + breath. An EFFECT (works on any sound, best on pads, strings, synths).
// Params: 0 VowelX 1 VowelY (XY vowel map: I(.1,.9) U(.9,.9) E(.25,.55) O(.75,.55) A(.5,.1)) 2 Breath 3 Choir 4 Vibrato 5 Tone 6 Body(dry blend) 7 Space 8 Level
#pragma once
#include "fx_core.h"
#include "fx_space.h"

namespace aetherfx {

class VoicesFx : public Effect {
 public:
  int paramCount() const override { return 9; }
  void prepare(double sr, int maxBlock) override {
    sr_ = sr;
    for (auto& d : dl_) d.init(static_cast<int>(0.06 * sr) + 8);
    rev_.prepare(sr, maxBlock);
    for (int i = 0; i < 7; ++i) rev_.setNorm(i, kRevDef[i]);
    reset();
  }
  void reset() override {
    for (auto& b : bp_) b.reset(); for (auto& b : nbp_) b.reset(); tone_.reset(); for (auto& d : dl_) d.clear(); rev_.reset();
    for (auto& s : sm_) s.init = false; env_ = 0.f; ph_ = 0.f; fx_[0] = -1; for (int k = 0; k < 4; ++k) lfo_[k] = k * 0.25f;
  }
  void process(float* io, int n) override {
    NoDenormals nd;
    const float cf = smoothCoef(n, sr_, 0.03);
    const float vx = sm_[0].step(p(0), cf), vy = sm_[1].step(p(1), cf), breath = sm_[2].step(p(2), cf), choir = sm_[3].step(p(3), cf), vib = sm_[4].step(p(4), cf);
    const float tone = sm_[5].step(p(5), cf), body = sm_[6].step(p(6), cf), space = sm_[7].step(p(7), cf), lvl = sm_[8].step(p(8), cf);
    float f[3];
    formants(vx, vy, f);
    if (std::fabs(f[0] - fx_[0]) > 2.f || std::fabs(f[1] - fx_[1]) > 2.f || std::fabs(f[2] - fx_[2]) > 2.f) {
      for (int k = 0; k < 3; ++k) { fx_[k] = f[k]; bp_[k].setBandpass(sr_, f[k], kQ[k]); nbp_[k].setBandpass(sr_, f[k], kQ[k]); }
    }
    tone_.setHighShelf(sr_, 3000, (tone - 0.5f) * 12.f);
    rev_.setNorm(5, 0.55f * space);
    const float outG = dbToLin(-24.f + 30.f * lvl), vibDepth = vib * 0.0009f * static_cast<float>(sr_), aE = 1.f - std::exp(static_cast<float>(-1.0 / (0.02 * sr_)));
    static const float base[4] = {0.014f, 0.019f, 0.024f, 0.029f}, rate[4] = {0.31f, 0.43f, 0.57f, 0.71f}, wl[4] = {1.f, 0.4f, 0.8f, 0.3f}, wr[4] = {0.3f, 0.8f, 0.4f, 1.f};
    for (int i = 0; i < n; ++i) {
      const float dry0 = io[2 * i], dry1 = io[2 * i + 1], mono = 0.5f * (dry0 + dry1);
      env_ += aE * (std::fabs(mono) - env_);
      const float nz = noise_.next() * env_ * breath * 2.0f;
      const float vibMod = vibDepth * sineTable().at(ph_); ph_ += static_cast<float>(5.5 / sr_); if (ph_ >= 1.f) ph_ -= 1.f;
      for (int ch = 0; ch < 2; ++ch) {
        const float x = ch ? dry1 : dry0, nIn = nz;
        float fo = 0.f, no = 0.f;
        for (int k = 0; k < 3; ++k) { fo += bp_[k].process(ch, x) * kG[k]; no += nbp_[k].process(ch, nIn) * kG[k]; }
        float y = (fo * 2.4f + no * 2.0f) + x * body * 0.8f;
        dl_[ch].write(y);
        float ens = 0.f;
        for (int k = 0; k < 4; ++k) { const float d = (base[k] + 0.0012f * sineTable().at(lfo_[k])) * static_cast<float>(sr_) + vibMod; ens += dl_[ch].read(std::max(d, 2.f)) * (ch ? wr[k] : wl[k]); }
        y = y * (1.f - 0.5f * choir) + ens * 0.55f * choir;
        io[2 * i + ch] = tone_.process(ch, y) * outG;
      }
      for (int k = 0; k < 4; ++k) { lfo_[k] += static_cast<float>(rate[k] / sr_); if (lfo_[k] >= 1.f) lfo_[k] -= 1.f; }
    }
    if (space > 0.01f) rev_.process(io, n);
    for (int i = 0; i < 2 * n; ++i) io[i] = std::tanh(io[i] * 0.9f) / 0.9f;
  }
  // vowel map -> F1,F2,F3 (Hz): inverse-distance weighting of five vowels
  static void formants(float x, float y, float* out) {
    static const float pos[5][2] = {{0.1f, 0.9f}, {0.9f, 0.9f}, {0.25f, 0.55f}, {0.75f, 0.55f}, {0.5f, 0.1f}};
    static const float fm[5][3] = {{270, 2290, 3010}, {300, 870, 2240}, {530, 1840, 2480}, {570, 840, 2410}, {800, 1150, 2900}};
    float w[5], sw = 0.f;
    for (int i = 0; i < 5; ++i) { const float dx = x - pos[i][0], dy = y - pos[i][1], d2 = dx * dx + dy * dy + 1e-4f; w[i] = 1.f / (d2 * std::sqrt(d2)); sw += w[i]; }
    for (int k = 0; k < 3; ++k) { float v = 0.f; for (int i = 0; i < 5; ++i) v += w[i] * fm[i][k]; out[k] = v / sw; }
  }

 private:
  static constexpr float kQ[3] = {8.f, 11.f, 14.f}, kG[3] = {1.f, 0.7f, 0.45f};
  static constexpr float kRevDef[7] = {0.62f, 0.5f, 0.15f, 0.45f, 0.8f, 0.3f, 0.5f};
  double sr_ = 48000.0;
  Biquad bp_[3], nbp_[3], tone_;
  DelayLine dl_[2];
  SpaceReverb rev_;
  Noise noise_;
  BlockSmoother sm_[9];
  float env_ = 0.f, ph_ = 0.f, fx_[3] = {-1, -1, -1}, lfo_[4] = {0, 0.25f, 0.5f, 0.75f};
};

}  // namespace aetherfx
