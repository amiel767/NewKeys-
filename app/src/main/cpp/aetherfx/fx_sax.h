// Aether Sax: harmonic reed source -> formant chain -> bell, with breath noise, growl AM,
// subtone, scoop/fall, key click, delayed vibrato and room. Monophonic with legato glide,
// exactly like the reference voice (`mkSax` / `relSax` of preview-leads-sax.html).
// Params (21): 0 type (Soprano,Alto,Tenor,Bari) 1 finish (visual) 2 mouthpiece (Jazz,Classic,Rubber)
// 3 reed (Soft,Medium,Hard) 4 breath 5 growl 6 subtone 7 grit 8 attack 9 click 10 scoop 11 fall
// 12 vibrato depth 13 vibrato rate 14 vibrato delay 15 legato 16 room 17 room size 18 warm 19 bright 20 level
#pragma once
#include "fx_source.h"
#include "fx_space.h"

namespace aetherfx {

class SaxEngine : public SourceFx {
 public:
  int paramCount() const override { return 21; }

  void prepare(double sr, int maxBlock) override {
    sr_ = sr;
    rev_.prepare(sr, maxBlock);
    reset();
  }
  void reset() override {
    ampStage_ = 0; ampT_ = 0.f; ampLvl_ = 0.f; relT_ = 0.f; relR_ = 0.f;
    ph_[0] = ph_[1] = ph_[2] = 0.f; vibPh_ = 0.f; vibDriftPh_ = 0.f; growlPh_ = 0.f; breathPh_ = 0.f;
    scoopT_ = 0.f; fallT_ = 0.f; clickEnv_ = 0.f;
    growlG_ = 1.f; breathG_ = 1.f; q_ = -1.f;
    held_.clear(); note_ = -1; freq_ = 440.f; target_ = 440.f;
    for (auto& b : form_) b.reset();
    lpF_.reset(); nbF_.reset(); clickF_.reset(); warm_.reset(); bright_.reset();
    rev_.reset(); setMeter(0, 0.f);
  }

  void noteOn(int note, int vel) override {
    if (note < 0 || note > 127) return;
    if (vel <= 0) { noteOff(note); return; }
    held_.push_back(note);
    const float f = static_cast<float>(440.0 * std::pow(2.0, (note - 69) / 12.0));
    const float legato = p(15);
    if (note_ >= 0 && legato > .12f) {  // glide to the new note, keep the sound
      target_ = f; note_ = note;
      return;
    }
    note_ = note; freq_ = f; target_ = f;
    vel_ = vel / 127.f;
    ampStage_ = 1; ampT_ = 0.f; ampLvl_ = 0.f; fallT_ = 0.f;
    scoopT_ = p(10) > .02f ? 1.f : 0.f;
    clickEnv_ = 1.f;
    for (auto& b : form_) b.reset();
    lpF_.reset(); nbF_.reset();
  }
  void noteOff(int note) override {
    for (size_t i = 0; i < held_.size(); ++i) if (held_[i] == note) { held_.erase(held_.begin() + static_cast<long>(i)); break; }
    if (!held_.empty()) { note_ = held_.back(); target_ = static_cast<float>(440.0 * std::pow(2.0, (held_.back() - 69) / 12.0)); return; }
    if (note_ < 0) return;
    note_ = -1;
    ampStage_ = 3;
    relR_ = .12f + p(11) * .18f;
    relT_ = 0.f;
    fallT_ = p(11) > .05f ? 1.f : 0.f;
  }
  void allNotesOff() override { held_.clear(); if (note_ >= 0) { note_ = -1; ampStage_ = 3; relR_ = .12f + p(11) * .18f; relT_ = 0.f; } }

  void process(float* io, int n) override {
    NoDenormals nd;
    if (ampStage_ == 0 && rev_.meter(0) < 1e-4f) {
      std::fill(io, io + 2 * n, 0.f);
      setMeter(0, 0.f);
      return;
    }
    const int ty = static_cast<int>(std::lround(p(0) * 3.f)), rd = static_cast<int>(std::lround(p(3) * 2.f)), mp = static_cast<int>(std::lround(p(2) * 2.f));
    const float f1 = TY_f1[ty], af = TY_af[ty], afg = TY_afg[ty];
    const float br = clamp01(p(19) * .75f + RD_BR[rd] + MP_BR[mp]);
    const float q = 1.15f - br * .65f;
    if (std::fabs(q - q_) > 1e-4f) { q_ = q; buildTable(); }
    const float att = .012f + std::pow(1.f - p(8), 2.f) * .14f * RD_ATT[rd];
    const float growlHz = 38.f + p(5) * 14.f, growlD = p(5) * .55f;
    const float breathHz = .1f + .4f * .5f, breathNoise = p(4) * .2f * RD_NG[rd] * (mp == 2 ? .7f : 1.f);
    const float gritK = 1.f + p(7) * 8.f + MP_GRIT[mp];
    const float sub = p(6) * .7f, clickG = p(9) * .3f;
    const float vibRate = 3.5f + p(13) * 4.f, vibDep = p(12) * 38.f, vibDelay = .12f + p(14) * .8f;
    const float legato = p(15) * p(15) * .4f / 3.f;
    const float invSr = static_cast<float>(1.0 / sr_);
    const float lpBase = 1500.f + br * 7000.f;
    const float endF = target_ * std::pow(2.f, -p(11) * 4.5f / 12.f);
    warm_.setLowShelf(sr_, 260, (p(18) - .5f) * 12.f);
    bright_.setHighShelf(sr_, 3200, (.5f - p(19)) * 10.f);
    rev_.setNorm(0, p(17)); rev_.setNorm(5, p(16) * .9f);
    float peak = 0.f;
    for (int i = 0; i < n; ++i) {
      // ---- envelopes ----
      float amp;
      if (ampStage_ == 1) { ampT_ += invSr; ampLvl_ = vel_ * .95f * std::min(1.f, ampT_ / std::max(1e-4f, att)); if (ampT_ >= att) { ampStage_ = 2; ampT_ = 0.f; } amp = ampLvl_; }
      else if (ampStage_ == 2) { ampT_ += invSr; ampLvl_ = vel_ * (.95f + (.75f - .95f) * std::min(1.f, ampT_ / .25f)); amp = ampLvl_; }
      else if (ampStage_ == 3) { relT_ += invSr; const float u = std::min(1.f, relT_ / std::max(1e-4f, relR_)); amp = ampLvl_ * (1.f - u); if (u >= 1.f) { ampStage_ = 0; amp = 0.f; } }
      else amp = 0.f;
      // ---- pitch: glide, scoop, fall ----
      freq_ += (target_ - freq_) * (legato > 1e-5f ? legato : 1.f);
      float pitch = freq_;
      if (scoopT_ > 0.f) { scoopT_ -= invSr / .1f; if (scoopT_ < 0.f) scoopT_ = 0.f; pitch *= std::pow(2.f, -p(10) * 3.f / 12.f * scoopT_); }
      if (fallT_ > 0.f) { fallT_ -= invSr / std::max(1e-4f, relR_ * .9f); if (fallT_ < 0.f) fallT_ = 0.f; pitch = freq_ * std::pow(2.f, -p(11) * 4.5f / 12.f * (1.f - fallT_)); }
      // ---- vibrato (delayed) ----
      vibPh_ += static_cast<float>(vibRate * invSr); if (vibPh_ >= 1.f) vibPh_ -= 1.f;
      vibDriftPh_ += static_cast<float>(.15f * invSr); if (vibDriftPh_ >= 1.f) vibDriftPh_ -= 1.f;
      const float vibGate = ampStage_ == 3 ? 0.f : (ampT_ > vibDelay || ampStage_ == 2 ? 1.f : 0.f);
      const float vib = (sineTable().at(vibPh_) + sineTable().at(vibDriftPh_) * .25f) * vibDep * vibGate;
      // ---- reed oscillators: two detuned copies + optional sub ----
      const float f = pitch * std::pow(2.f, vib / 1200.f);
      const float inc1 = f * invSr, inc2 = f * std::pow(2.f, 6.f / 1200.f) * invSr, incS = f * .5f * invSr;
      float mix = reed(ph_[0], inc1) * .55f + reed(ph_[1], inc2) * .35f;
      ph_[0] += inc1; if (ph_[0] >= 1.f) ph_[0] -= 1.f;
      ph_[1] += inc2; if (ph_[1] >= 1.f) ph_[1] -= 1.f;
      if (sub > .02f) { mix += sineTable().at(ph_[2]) * sub; ph_[2] += incS; if (ph_[2] >= 1.f) ph_[2] -= 1.f; }
      // ---- breath noise through a bandpass ----
      nbF_.setBandpass(sr_, f1 * 1.6f, .7f);
      breathPh_ += static_cast<float>(breathHz * invSr); if (breathPh_ >= 1.f) breathPh_ -= 1.f;
      const float bMod = 1.f + sineTable().at(breathPh_) * .3f;
      mix += nbF_.process(0, noise_.next()) * breathNoise * bMod;
      // ---- grit waveshaper + formant chain ----
      float x = fastTanh(mix * gritK * 1.15f) * (1.f / std::max(1e-3f, fastTanh(gritK * 1.15f)));
      form_[0].setPeaking(sr_, f1, 2.5, 9.f);
      form_[1].setPeaking(sr_, f1 * 2.7f, 2.f, 6.f);
      form_[2].setPeaking(sr_, af, 1.5f, afg);
      form_[3].setPeaking(sr_, 1400.f - p(18) * 700.f, 1.8f, 3.f + p(18) * 4.f);
      x = form_[0].process(0, x);
      x = form_[1].process(0, x);
      x = form_[2].process(0, x);
      x = form_[3].process(0, x);
      lpF_.setLowpass(sr_, fallT_ > 0.f ? 700.f : lpBase, .9f);
      x = lpF_.process(0, x);
      // ---- growl AM (fast) and breath AM (slow) ----
      growlPh_ += static_cast<float>(growlHz * invSr); if (growlPh_ >= 1.f) growlPh_ -= 1.f;
      growlG_ = 1.f + sineTable().at(growlPh_) * growlD;
      breathG_ = 1.f + sineTable().at(breathPh_) * (p(4) * .05f + .01f);
      x *= growlG_ * amp * breathG_;
      // ---- key click ----
      if (clickEnv_ > 1e-4f) {
        clickF_.setBandpass(sr_, 2800.f, 1.6f);
        x += clickF_.process(0, noise_.next()) * clickG * clickEnv_ * 3.f;
        clickEnv_ *= std::exp(static_cast<float>(-1.0 / (.018 * sr_)));
      }
      // ---- warm/bright shelves then out ----
      x = bright_.process(0, warm_.process(0, x));
      x *= p(20) * 1.1f;
      io[2 * i] += x; io[2 * i + 1] += x;
      peak = std::max(peak, std::fabs(x));
    }
    if (p(16) > 0.01f) rev_.process(io, n);
    setMeter(0, peak);
  }

 private:
  static constexpr float TY_f1[4] = {900.f, 650.f, 520.f, 400.f};
  static constexpr float TY_af[4] = {670.f, 837.f, 618.f, 570.f};
  static constexpr float TY_afg[4] = {-8.f, -9.f, -10.f, -11.f};
  static constexpr float RD_BR[3] = {.1f, 0.f, .14f};
  static constexpr float MP_BR[3] = {.15f, -.1f, 0.f};
  static constexpr float RD_NG[3] = {1.3f, 1.f, .8f};
  static constexpr float RD_ATT[3] = {1.3f, 1.f, .8f};
  static constexpr float MP_GRIT[3] = {1.2f, 0.f, .5f};

  static float fastTanh(float x) { if (x > 3.f) return 1.f; if (x < -3.f) return -1.f; const float x2 = x * x; return x * (27.f + x2) / (27.f + 9.f * x2); }
  // Harmonic reed table: im[n] = (1/n^q) * (n odd ? 1 : .78), like saxWave() in the reference.
  void buildTable() {
    constexpr int N = 1024, H = 32;
    for (int i = 0; i < N; ++i) {
      const float t = static_cast<float>(i) / N;
      float s = 0.f;
      for (int h = 1; h <= H; ++h) s += std::sin(2.f * static_cast<float>(kPi) * h * t) * std::pow(1.f / h, q_) * ((h & 1) ? 1.f : .78f);
      tbl_[i] = s;
    }
    tbl_[N] = tbl_[0];
  }
  float reed(float ph, float inc) const {
    (void)inc;
    const float x = ph * 1024.f;
    const int i = static_cast<int>(x);
    const float fr = x - i;
    return tbl_[i] + (tbl_[i + 1] - tbl_[i]) * fr;
  }

  double sr_ = 48000.0;
  float tbl_[1025] = {0};
  Biquad form_[4], lpF_, nbF_, clickF_, warm_, bright_;
  SpaceReverb rev_;
  Noise noise_;
  std::vector<int> held_;
  int note_ = -1, ampStage_ = 0;
  float freq_ = 440.f, target_ = 440.f, vel_ = 0.f, ampT_ = 0.f, ampLvl_ = 0.f, relT_ = 0.f, relR_ = .2f;
  float ph_[3] = {0, 0, 0}, vibPh_ = 0.f, vibDriftPh_ = 0.f, growlPh_ = 0.f, breathPh_ = 0.f;
  float scoopT_ = 0.f, fallT_ = 0.f, clickEnv_ = 0.f, growlG_ = 1.f, breathG_ = 1.f, q_ = -1.f;
};

}  // namespace aetherfx
