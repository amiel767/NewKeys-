// Aether Sax: harmonic reed source -> formant chain -> bell, with breath noise, growl AM,
// subtone, scoop/fall, key click, delayed vibrato and room. Monophonic with legato glide.
// Ported from the reference voice (`mkSax` / `relSax` of preview-leads-sax.html) including the
// DeepSeek adjustment patch: pitch-scaled formants, a per-note random spread, an asymmetric
// grit curve, a shared breath LFO driving noise / amplitude / detune, vibrato formant wobble
// and a curved fall on release.
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
    ampStage_ = 0; ampT_ = 0.f; ampLvl_ = 0.f; relT_ = 0.f; relR_ = .2f;
    ph_[0] = ph_[1] = ph_[2] = 0.f; vibPh_ = 0.f; vibDriftPh_ = 0.f; breathLfoPh_ = 0.f; growlPh_ = 0.f;
    scoopT_ = 0.f; fallT_ = 0.f; clickEnv_ = 0.f; relClickEnv_ = 0.f;
    q_ = -1.f; rng_ = 0x2545F491u; nVar_ = 1.f; pitchScale_ = 1.f; afScale_ = 1.f;
    nGainJit_ = 1.f; vfJit_ = 1.f; clickJit_ = 1.f; fallStart_ = 440.f; fallEnd_ = 440.f;
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
    if (note_ >= 0 && p(15) > .12f) { target_ = f; note_ = note; return; }  // legato glide
    note_ = note; freq_ = f; target_ = f; vel_ = vel / 127.f;
    ampStage_ = 1; ampT_ = 0.f; ampLvl_ = 0.f; fallT_ = 0.f;
    scoopT_ = p(10) > .02f ? 1.f : 0.f;
    clickEnv_ = 1.f;
    seed(f);
    for (auto& b : form_) b.reset();
    lpF_.reset(); nbF_.reset();
  }
  void noteOff(int note) override {
    for (size_t i = 0; i < held_.size(); ++i) if (held_[i] == note) { held_.erase(held_.begin() + static_cast<long>(i)); break; }
    if (!held_.empty()) { note_ = held_.back(); target_ = static_cast<float>(440.0 * std::pow(2.0, (held_.back() - 69) / 12.0)); return; }
    if (note_ < 0) return;
    note_ = -1;
    ampStage_ = 3;
    relT_ = 0.f;
    fallT_ = p(11) > .05f ? 1.f : 0.f;
    relR_ = fallT_ > 0.f ? .30f + p(11) * .36f : .1f + p(11) * .15f;
    fallStart_ = freq_;
    fallEnd_ = std::max(freq_ * std::pow(2.f, -p(11) * 4.5f / 12.f), 20.f);
    relClickEnv_ = p(9) > .01f ? (fallT_ > 0.f ? p(9) * .12f : p(9) * .22f) : 0.f;
  }
  void allNotesOff() override {
    held_.clear();
    if (note_ >= 0) { note_ = -1; ampStage_ = 3; relT_ = 0.f; fallT_ = 0.f; relR_ = .12f + p(11) * .18f; }
  }

  void process(float* io, int n) override {
    NoDenormals nd;
    if (ampStage_ == 0 && relClickEnv_ < 1e-4f && rev_.meter(0) < 1e-4f) {
      std::fill(io, io + 2 * n, 0.f);
      setMeter(0, 0.f);
      return;
    }
    const int ty = static_cast<int>(std::lround(p(0) * 3.f)), rd = static_cast<int>(std::lround(p(3) * 2.f)), mp = static_cast<int>(std::lround(p(2) * 2.f));
    const float br = clamp01(p(19) * .75f + RD_BR[rd] + MP_BR[mp]);
    const float q = 1.15f - br * .65f;
    if (std::fabs(q - q_) > 1e-4f) { q_ = q; buildTable(); }
    // Pitch-scaled formants (DeepSeek patch): a sax is not a fixed filter bank; the reed
    // formants track the fingered pitch by ~10%, the anchor formant by ~5%.
    const float f1 = TY_f1[ty] * pitchScale_, f2 = f1 * 2.7f, af = TY_af[ty] * afScale_;
    const float vf = (1400.f - p(18) * 700.f) * vfJit_, vfg = 3.f + p(18) * 4.f;
    const float att = .012f + std::pow(1.f - p(8), 2.f) * .14f * RD_ATT[rd];
    const float growlHz = 38.f + p(5) * 14.f, growlD = p(5) * .55f;
    const float breathNoise = p(4) * .2f * RD_NG[rd] * (mp == 2 ? .7f : 1.f) * nGainJit_;
    const float gritK = 1.f + p(7) * 8.f + MP_GRIT[mp];
    const float sub = p(6) * .7f;
    const float vibRate = 3.5f + p(13) * 4.f, vibDep = p(12) * 38.f, vibDelay = .12f + p(14) * .8f;
    const float legato = p(15) * p(15) * .4f / 3.f;
    const float invSr = static_cast<float>(1.0 / sr_);
    const float lpBase = 1500.f + br * 7000.f;
    const float formWob = p(12) * 10.f;  // vibrato also wobbles the two reed formants
    warm_.setLowShelf(sr_, 260, (p(18) - .5f) * 12.f);
    bright_.setHighShelf(sr_, 3200, (.5f - p(19)) * 10.f);
    rev_.setNorm(0, p(17)); rev_.setNorm(5, p(16) * .9f);
    // Breath LFO (DeepSeek patch): one slow LFO drives the noise level, the amplitude and the
    // detune, which is what makes the tone "breathe" instead of sitting flat.
    const float bLfoRate = .1f + p(4) * .4f;
    const float ngModDepth = p(4) * .15f + .03f, ampModDepth = p(4) * .05f + .01f, bPitchDepth = p(4) * 2.5f + 1.f;
    float peak = 0.f;
    for (int i = 0; i < n; ++i) {
      // ---- amplitude envelope ----
      float amp;
      if (ampStage_ == 1) { ampT_ += invSr; ampLvl_ = vel_ * .95f * std::min(1.f, ampT_ / std::max(1e-4f, att)); if (ampT_ >= att) { ampStage_ = 2; ampT_ = 0.f; } amp = ampLvl_; }
      else if (ampStage_ == 2) { ampT_ += invSr; ampLvl_ = vel_ * (.95f + (.75f - .95f) * std::min(1.f, ampT_ / .25f)); amp = ampLvl_; }
      else if (ampStage_ == 3) { relT_ += invSr; const float u = std::min(1.f, relT_ / std::max(1e-4f, relR_)); amp = ampLvl_ * (1.f - u); if (u >= 1.f) { ampStage_ = 0; amp = 0.f; } }
      else amp = 0.f;
      // ---- pitch: glide, scoop, fall (curved u^2, like setValueCurveAtTime) ----
      freq_ += (target_ - freq_) * (legato > 1e-5f ? legato : 1.f);
      float pitch = freq_;
      if (scoopT_ > 0.f) { scoopT_ -= invSr / .1f; if (scoopT_ < 0.f) scoopT_ = 0.f; pitch *= std::pow(2.f, -p(10) * 3.f / 12.f * scoopT_); }
      if (fallT_ > 0.f) {
        const float u = std::min(1.f, relT_ / std::max(1e-4f, relR_ * .9f));
        pitch = fallStart_ * std::pow(fallEnd_ / fallStart_, u * u);
      }
      // ---- vibrato (delayed), with a slow drift and a soft-clipped shape ----
      vibPh_ += static_cast<float>(vibRate * invSr); if (vibPh_ >= 1.f) vibPh_ -= 1.f;
      vibDriftPh_ += static_cast<float>(.13f * invSr); if (vibDriftPh_ >= 1.f) vibDriftPh_ -= 1.f;
      const float vibGate = ampStage_ == 3 ? 0.f : (ampT_ > vibDelay || ampStage_ == 2 ? 1.f : 0.f);
      float vshape = sineTable().at(vibPh_);
      vshape = vshape > .2f ? .2f + (vshape - .2f) * .7f : vshape;  // asymmetric soft clip
      const float vib = (vshape + sineTable().at(vibDriftPh_) * .5f) * vibDep * vibGate;
      // ---- shared breath LFO ----
      breathLfoPh_ += static_cast<float>(bLfoRate * invSr); if (breathLfoPh_ >= 1.f) breathLfoPh_ -= 1.f;
      const float bLfo = sineTable().at(breathLfoPh_);
      // ---- reed oscillators: two detuned copies + optional sub ----
      const float f = pitch * std::pow(2.f, (vib + bLfo * bPitchDepth) / 1200.f);
      const float inc1 = f * invSr, inc2 = f * std::pow(2.f, 6.f / 1200.f) * invSr, incS = f * .5f * invSr;
      float mix = reed(ph_[0]) * .55f + reed(ph_[1]) * .35f;
      ph_[0] += inc1; if (ph_[0] >= 1.f) ph_[0] -= 1.f;
      ph_[1] += inc2; if (ph_[1] >= 1.f) ph_[1] -= 1.f;
      if (sub > .02f) { mix += sineTable().at(ph_[2]) * sub; ph_[2] += incS; if (ph_[2] >= 1.f) ph_[2] -= 1.f; }
      // ---- breath noise through a bandpass, modulated by the breath LFO ----
      nbF_.setBandpass(sr_, f1 * 1.6f, .7f);
      mix += nbF_.process(0, noise_.next()) * breathNoise * (1.f + bLfo * ngModDepth);
      // ---- asymmetric grit shaper (positive and negative halves differ) ----
      float x = grit(mix, gritK);
      // ---- formant chain: two reed formants, the anchor formant and the warmth formant ----
      form_[0].setPeaking(sr_, f1 + vib * formWob, 2.5f, 9.f);
      form_[1].setPeaking(sr_, f2 + vib * formWob, 2.f, 6.f);
      form_[2].setPeaking(sr_, af, 1.5f, TY_afg[ty]);
      form_[3].setPeaking(sr_, vf, 1.8f, vfg);
      x = form_[0].process(0, x);
      x = form_[1].process(0, x);
      x = form_[2].process(0, x);
      x = form_[3].process(0, x);
      lpF_.setLowpass(sr_, fallT_ > 0.f ? 700.f : lpBase, .9f);
      x = lpF_.process(0, x);
      // ---- growl AM (fast) and breath AM (slow) ----
      growlPh_ += static_cast<float>(growlHz * invSr); if (growlPh_ >= 1.f) growlPh_ -= 1.f;
      x *= (1.f + sineTable().at(growlPh_) * growlD) * amp * (1.f + bLfo * ampModDepth);
      // ---- key click: at note-on, and a shorter one at release when there is a fall ----
      if (clickEnv_ > 1e-4f) {
        clickF_.setBandpass(sr_, 2800.f + (freq_ / 440.f - 1.f) * -200.f + clickJit_ * 400.f, 1.6f);
        x += clickF_.process(0, noise_.next()) * p(9) * .3f * (.6f + clickJit_ * .6f) * clickEnv_;
        clickEnv_ *= std::exp(static_cast<float>(-1.0 / (.018 * sr_)));
      }
      if (relClickEnv_ > 1e-4f) {
        clickF_.setBandpass(sr_, 2400.f + clickJit_ * 600.f, 1.6f);
        x += clickF_.process(0, noise_.next()) * relClickEnv_;
        relClickEnv_ *= std::exp(static_cast<float>(-1.0 / (.015 * sr_)));
      }
      // ---- warm/bright shelves then out ----
      x = bright_.process(0, warm_.process(0, x));
      x *= p(20) * .8f;
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
  // Asymmetric grit: the positive half is driven harder than the negative one, which is what
  // gives the reed its edge without sounding like a symmetric distortion.
  static float grit(float x, float k) {
    const float kp = k * 1.15f, kn = k * .9f;
    return x > 0.f ? fastTanh(x * kp) / fastTanh(kp) : fastTanh(x * kn) / fastTanh(kn);
  }
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
  float reed(float ph) const {
    const float x = ph * 1024.f;
    const int i = static_cast<int>(x);
    const float fr = x - i;
    return tbl_[i] + (tbl_[i + 1] - tbl_[i]) * fr;
  }
  // Per-note randomness (DeepSeek patch): formant pitch-scaling jitter, breath and warmth
  // jitter, key-click position. Mirrors the Math.random() calls of the reference mkSax().
  void seed(float f) {
    auto r = [this]() { rng_ ^= rng_ << 13; rng_ ^= rng_ >> 17; rng_ ^= rng_ << 5; return static_cast<float>(static_cast<int32_t>(rng_)) * (1.0f / 2147483648.0f); };
    r();
    nVar_ = .92f + (.5f + r() * .5f) * .16f;
    pitchScale_ = (1.f + std::log2(f / 440.f) * .10f) * nVar_;
    afScale_ = 1.f + std::log2(f / 440.f) * .05f;
    vfJit_ = .95f + (.5f + r() * .5f) * .1f;
    nGainJit_ = .85f + (.5f + r() * .5f) * .3f;
    clickJit_ = r();
  }

  double sr_ = 48000.0;
  float tbl_[1025] = {0};
  Biquad form_[4], lpF_, nbF_, clickF_, warm_, bright_;
  SpaceReverb rev_;
  Noise noise_;
  std::vector<int> held_;
  int note_ = -1, ampStage_ = 0;
  float freq_ = 440.f, target_ = 440.f, vel_ = 0.f, ampT_ = 0.f, ampLvl_ = 0.f, relT_ = 0.f, relR_ = .2f;
  float ph_[3] = {0, 0, 0}, vibPh_ = 0.f, vibDriftPh_ = 0.f, breathLfoPh_ = 0.f, growlPh_ = 0.f;
  float scoopT_ = 0.f, fallT_ = 0.f, clickEnv_ = 0.f, relClickEnv_ = 0.f;
  float q_ = -1.f, nVar_ = 1.f, pitchScale_ = 1.f, afScale_ = 1.f, nGainJit_ = 1.f, vfJit_ = 1.f, clickJit_ = 1.f;
  float fallStart_ = 440.f, fallEnd_ = 440.f;
  uint32_t rng_ = 0x2545F491u;
};

}  // namespace aetherfx
