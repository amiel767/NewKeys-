// Aether Organ: tonewheel organ engine (additive, 91-wheel style), two drawbar manuals, percussion, key click, vibrato/chorus, rotary speaker, drive.
// Params (31): 0..8 Upper drawbars 16' 5 1/3' 8' 4' 2 2/3' 2' 1 3/5' 1 1/3' 1' (0..8 steps) | 9..17 Lower | 18 PercOn 19 PercSoft 20 PercFast 21 Perc3rd
// 22 VibType (0..5 = V1 V2 V3 C1 C2 C3, value*5) 23 VibUpper 24 VibLower | 25 Rotary (0 Stop, .5 Slow, 1 Fast) | 26 Click 27 Tone 28 Drive 29 Space 30 Level
// Keys below the split (MIDI 60) play the LOWER manual, the others the UPPER manual.
#pragma once
#include "fx_source.h"
#include "fx_space.h"

namespace aetherfx {

class OrganEngine : public SourceFx {
 public:
  static constexpr int kSplit = 60;
  int paramCount() const override { return 31; }
  void prepare(double sr, int maxBlock) override {
    sr_ = sr;
    for (int w = 0; w < 128; ++w) inc_[w] = static_cast<float>(440.0 * std::pow(2.0, (w - 69) / 12.0) / sr);
    for (int i = 0; i < 2; ++i) { dlH_[i].init(static_cast<int>(0.01 * sr) + 8); dlD_[i].init(static_cast<int>(0.01 * sr) + 8); }
    dlV_.init(static_cast<int>(0.02 * sr) + 8);
    rev_.prepare(sr, maxBlock); for (int i = 0; i < 7; ++i) rev_.setNorm(i, kRevDef[i]);
    xo_[0].setLowpass(sr, 800, 0.5); xo_[1].setLowpass(sr, 800, 0.5);
    reset();
  }
  void reset() override {
    std::memset(amp_, 0, sizeof(amp_)); std::memset(ph_, 0, sizeof(ph_)); std::memset(held_, 0, sizeof(held_)); heldCount_ = 0;
    percEnv_ = 0.f; clickEnv_ = 0.f; hornSp_ = drumSp_ = 0.f; hornPh_ = drumPh_ = 0.f; vibPh_ = 0.f;
    for (auto& d : dlH_) d.clear(); for (auto& d : dlD_) d.clear(); dlV_.clear(); rev_.reset(); for (auto& b : xo_) b.reset(); tone_.reset();
    for (auto& s : sm_) s.init = false;
  }
  void noteOn(int note, int vel) override {
    if (note < 0 || note > 127) return;
    if (vel <= 0) { noteOff(note); return; }
    if (heldCount_ == 0) percEnv_ = 1.f;  // single trigger, like the hardware
    if (!held_[note]) { held_[note] = true; ++heldCount_; }
    clickEnv_ = 1.f;
  }
  void noteOff(int note) override { if (note >= 0 && note < 128 && held_[note]) { held_[note] = false; --heldCount_; } }
  void allNotesOff() override { std::memset(held_, 0, sizeof(held_)); heldCount_ = 0; }
  // The organ is additive: a bend scales every wheel increment by the same ratio, so the
  // drawbar stack glides as one instrument instead of detuning against itself.
  void pitchBend(float b) override { bend_ = b; }

  void process(float* io, int n) override {
    NoDenormals nd;
    // Silent early-out: no key held, no percussion/click tail, no reverb tail and the
    // Leslie stopped means the block is silence. Skips the per-sample chain (drawbars,
    // vibrato delay, Leslie, crossover, reverb, tanh) that otherwise ran for a
    // loaded-but-idle instrument. The rotary is excluded on purpose: it must keep
    // turning while it is running, or the horn would spin up again on the next note.
    const int rotNow = static_cast<int>(std::lround(p(25) * 2.f));
    if (heldCount_ == 0 && percEnv_ < 1e-4f && clickEnv_ < 1e-4f && rotNow == 0 && rev_.meter(0) < 1e-4f) {
      std::fill(io, io + 2 * n, 0.f);
      setMeter(0, 0.f);
      return;
    }
    const float cf = smoothCoef(n, sr_, 0.02);
    // Range comes from Track Settings via SourceFx::setPitchBendRange (default 2 st = 1 tone).
    const float bendRatio = std::pow(2.f, bend_ * static_cast<float>(bendSemis_) / 12.f);
    const float click = sm_[0].step(p(26), cf), tone = sm_[1].step(p(27), cf), drive = sm_[2].step(p(28), cf), space = sm_[3].step(p(29), cf), lvl = sm_[4].step(p(30), cf);
    int dbU[9], dbL[9];
    for (int j = 0; j < 9; ++j) { dbU[j] = static_cast<int>(std::lround(p(j) * 8.f)); dbL[j] = static_cast<int>(std::lround(p(9 + j) * 8.f)); }
    const bool percOn = p(18) > 0.5f, percSoft = p(19) > 0.5f, percFast = p(20) > 0.5f, perc3 = p(21) > 0.5f;
    const int vibType = static_cast<int>(std::lround(p(22) * 5.f)), vibOn = (p(23) > 0.5f || p(24) > 0.5f) ? 1 : 0;
    const int rot = static_cast<int>(std::lround(p(25) * 2.f));
    static const int kOff[9] = {-12, 7, 0, 12, 19, 24, 28, 31, 36};
    // target amplitude per wheel from the held keys (recomputed per block)
    std::memset(tw_, 0, sizeof(tw_)); std::memset(pw_, 0, sizeof(pw_));
    for (int k = 0; k < 128; ++k) {
      if (!held_[k]) continue;
      const bool upper = k >= kSplit; const int* db = upper ? dbU : dbL;
      for (int j = 0; j < 9; ++j) {
        if (upper && percOn && j == 8) continue;  // percussion cuts the 1' drawbar
        if (db[j] <= 0) continue;
        tw_[fold(k + kOff[j])] += drawAmp(db[j]);
      }
      if (upper && percOn) pw_[fold(k + (perc3 ? 19 : 12))] += percSoft ? 0.03f : 0.06f;
    }
    int na = 0;
    for (int w = 0; w < 128; ++w) if (amp_[w] > 1e-6f || tw_[w] > 0.f || pw_[w] > 0.f) act_[na++] = w;
    const float aC = 1.f - std::exp(static_cast<float>(-1.0 / (0.004 * sr_)));
    const float percDec = std::exp(static_cast<float>(-1.0 / ((percFast ? 0.18 : 0.6) * sr_))), clickDec = std::exp(static_cast<float>(-1.0 / (0.003 * sr_)));
    const float g = 1.f + drive * 5.f, post = 1.f + drive * 0.5f;
    static const float vdepth[3] = {0.25f, 0.5f, 0.9f};
    const float vd = vdepth[vibType % 3] * 0.001f * static_cast<float>(sr_);
    const float hornT = rot == 0 ? 0.f : (rot == 1 ? 0.8f : 6.7f), drumT = rot == 0 ? 0.f : (rot == 1 ? 0.7f : 5.9f);
    const float aH = 1.f - std::exp(static_cast<float>(-1.0 / (0.7 * sr_))), aD = 1.f - std::exp(static_cast<float>(-1.0 / (2.5 * sr_)));
    tone_.setHighShelf(sr_, 3000, (tone - 0.5f) * 16.f);
    rev_.setNorm(5, 0.5f * space);
    const float outG = dbToLin(-24.f + 30.f * lvl);
    for (int i = 0; i < n; ++i) {
      float s = 0.f;
      for (int q = 0; q < na; ++q) {
        const int w = act_[q];
        amp_[w] += (tw_[w] - amp_[w]) * aC;
        s += (amp_[w] + pw_[w] * percEnv_) * sineTable().at(ph_[w]);
        ph_[w] += inc_[w] * bendRatio; if (ph_[w] >= 1.f) ph_[w] -= 1.f;
      }
      percEnv_ *= percDec;
      if (clickEnv_ > 1e-4f) { s += noise_.next() * clickEnv_ * click * 0.25f; clickEnv_ *= clickDec; }
      s = std::tanh(g * s) / g * post;
      if (vibOn) {  // scanner vibrato (V) / chorus (C)
        dlV_.write(s);
        const float d = 2.f + vd * (0.5f + 0.5f * sineTable().at(vibPh_)); vibPh_ += static_cast<float>(6.8 / sr_); if (vibPh_ >= 1.f) vibPh_ -= 1.f;
        const float v = dlV_.read(d); s = vibType >= 3 ? 0.5f * (s + v) : v;
      }
      hornSp_ += (hornT - hornSp_) * aH; drumSp_ += (drumT - drumSp_) * aD;
      hornPh_ += hornSp_ / static_cast<float>(sr_); if (hornPh_ >= 1.f) hornPh_ -= 1.f;
      drumPh_ += drumSp_ / static_cast<float>(sr_); if (drumPh_ >= 1.f) drumPh_ -= 1.f;
      const float lo = xo_[1].process(0, xo_[0].process(0, s)), hi = s - lo;
      const float dsH = std::min(1.f, hornSp_ * 2.f), dsD = std::min(1.f, drumSp_ * 2.f);
      float out[2];
      for (int ch = 0; ch < 2; ++ch) {
        const float sh = sineTable().at(std::fmod(hornPh_ + 0.25f * ch, 1.f)), sd = sineTable().at(std::fmod(drumPh_ + 0.5f * ch, 1.f));
        dlH_[ch].write(hi); dlD_[ch].write(lo);
        const float h = dlH_[ch].read(2.f + dsH * 0.0006f * static_cast<float>(sr_) * (0.5f + 0.5f * sh)) * (1.f + 0.35f * dsH * sh);
        const float d = dlD_[ch].read(2.f + dsD * 0.0004f * static_cast<float>(sr_) * (0.5f + 0.5f * sd)) * (1.f + 0.25f * dsD * sd);
        out[ch] = tone_.process(ch, h + d) * outG;
      }
      io[2 * i] = out[0]; io[2 * i + 1] = out[1];
    }
    if (space > 0.01f) rev_.process(io, n);
    for (int i = 0; i < 2 * n; ++i) io[i] = std::tanh(io[i] * 0.9f) / 0.9f;
    setMeter(0, static_cast<float>(na));
  }
  int activeWheels() const { return static_cast<int>(meter(0)); }

 private:
  static int fold(int idx) { while (idx > 108) idx -= 12; while (idx < 0) idx += 12; return idx; }
  static float drawAmp(int d) { return 0.035f * std::pow(10.f, -3.f * (8 - d) / 20.f); }  // 3 dB per drawbar step
  static constexpr float kRevDef[7] = {0.55f, 0.45f, 0.12f, 0.45f, 0.8f, 0.3f, 0.5f};
  double sr_ = 48000.0;
  float inc_[128], amp_[128], ph_[128], tw_[128], pw_[128]; int act_[128]; bool held_[128]; int heldCount_ = 0;
  float bend_ = 0.f;
  float percEnv_ = 0.f, clickEnv_ = 0.f, hornSp_ = 0.f, drumSp_ = 0.f, hornPh_ = 0.f, drumPh_ = 0.f, vibPh_ = 0.f;
  DelayLine dlH_[2], dlD_[2], dlV_; SpaceReverb rev_; Biquad xo_[2], tone_; Noise noise_; BlockSmoother sm_[5];
};

}  // namespace aetherfx
