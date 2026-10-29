// Aether Sax: harmonic reed source -> formant chain -> bell, with breath noise, growl AM,
// subtone, scoop/fall, key click, delayed vibrato and room. Monophonic with legato glide.
// Ported from the reference voice (`mkSax` / `relSax` of preview-leads-sax.html) including the
// DeepSeek adjustment patch: pitch-scaled formants, a per-note random spread, an asymmetric
// grit curve, a shared breath LFO driving noise / amplitude / detune, vibrato formant wobble
// and a curved fall on release.
// Params (21): 0 type (Soprano,Alto,Tenor,Bari) 1 finish (visual) 2 mouthpiece (Jazz,Classic,Rubber)
// 3 reed (Soft,Medium,Hard) 4 breath 5 growl 6 subtone 7 grit 8 attack 9 click 10 scoop 11 fall
// 12 vibrato depth 13 vibrato rate 14 vibrato delay 15 legato 16 room 17 room size 18 warm 19 bright 20 level
// 21 humanize (adds a per-note random spread to pitch, attack, level and vibrato)
#pragma once
#include "fx_source.h"
#include "fx_space.h"
#include "fx_sax_samples.h"

namespace aetherfx {

class SaxEngine : public SourceFx {
 public:
  int paramCount() const override { return 22; }

  void prepare(double sr, int maxBlock) override {
    sr_ = sr;
    rev_.prepare(sr, maxBlock);
    smp_.setOutputRate(sr);
    reset();
  }
  void reset() override {
    ampStage_ = 0; ampT_ = 0.f; ampLvl_ = 0.f; relT_ = 0.f; relR_ = .2f;
    ph_[0] = ph_[1] = ph_[2] = 0.f; vibPh_ = 0.f; vibDriftPh_ = 0.f; breathLfoPh_ = 0.f; growlPh_ = 0.f;
    scoopT_ = 0.f; fallT_ = 0.f; clickEnv_ = 0.f; relClickEnv_ = 0.f; biteT_ = 1.f;
    q_ = -1.f; rng_ = 0x2545F491u; nVar_ = 1.f; pitchScale_ = 1.f; afScale_ = 1.f;
    nGainJit_ = 1.f; vfJit_ = 1.f; clickJit_ = 1.f; fallStart_ = 440.f; fallEnd_ = 440.f;
    hum_ = 0.f; humPitch_ = 1.f; humLevel_ = 1.f; humAtk_ = 1.f; humVib_ = 1.f;
    held_.clear(); note_ = -1; freq_ = 440.f; target_ = 440.f;
    for (auto& b : form_) b.reset();
    lpF_.reset(); nbF_.reset(); clickF_.reset(); warm_.reset(); bright_.reset();
    smp_.allNotesOff();
    rev_.reset(); setMeter(0, 0.f);
  }

  void noteOn(int note, int vel) override {
    if (note < 0 || note > 127) return;
    if (vel <= 0) { noteOff(note); return; }
    held_.push_back(note);
    const float f = static_cast<float>(440.0 * std::pow(2.0, (note - 69) / 12.0));
    if (note_ >= 0 && p(15) > .12f) {
      // Legato slur: the oscillator glides and the *sample* is retargeted, not re-attacked.
      // Without the retarget the sampled voice stayed on the previous note (the engine used to
      // return before touching the reader), so a slur was a stuck note at mix 1.0.
      target_ = f;
      note_ = note;
      smp_.retarget(f);
      return;
    }
    note_ = note; freq_ = f; target_ = f; vel_ = vel / 127.f;
    ampStage_ = 1; ampT_ = 0.f; ampLvl_ = 0.f; fallT_ = 0.f; biteT_ = 0.f;
    scoopT_ = p(10) > .02f ? 1.f : 0.f;
    clickEnv_ = 1.f;
    smp_.noteOn(note, vel);
    seed(f);
    humanize();
    for (auto& b : form_) b.reset();
    lpF_.reset(); nbF_.reset();
  }
  void noteOff(int note) override {
    for (size_t i = 0; i < held_.size(); ++i) if (held_[i] == note) { held_.erase(held_.begin() + static_cast<long>(i)); break; }
    if (!held_.empty()) { note_ = held_.back(); target_ = static_cast<float>(440.0 * std::pow(2.0, (held_.back() - 69) / 12.0)); return; }
    if (note_ < 0) return;
    note_ = -1;
    smp_.noteOff();
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
    smp_.allNotesOff();
    if (note_ >= 0) { note_ = -1; ampStage_ = 3; relT_ = 0.f; fallT_ = 0.f; relR_ = .12f + p(11) * .18f; }
  }
  // Bends the reed stack by the Track Settings range (default 2 st = 1 tone). Applied on top
  // of the scoop/fall curves, so a bend during a scoop still tracks the finger.
  void pitchBend(float b) override { bend_ = b; }

  /**
   * Enables the measured harmonic table (the "additive" correction). Default true.
   * Off restores the generic 1/n^q voice, which is what earlier builds sounded
   * like; kept so the change can be A/B'd on a device.
   */
  void setMeasuredTable(bool on) { if (on != measured_) { measured_ = on; buildTable(); } }
  bool measuredTable() const { return measured_; }

  /**
   * Hybrid mix: 0 = pure synthesis, 1 = pure samples. The pack supplies the
   * timbre and the synthesis stays under it as a bed, so the instrument keeps a
   * continuous voice across the gaps between sampled zones and through legato.
   * With no pack loaded the reader is inactive and this has no effect.
   */
  // SourceFx hooks: the engine reaches the bank through these, so it never needs to
  // know which source it holds.
  bool loadSampleBank(const uint8_t* bytes, size_t size) override { return smp_.load(bytes, size); }
  float sampleMix() const override { return sampleMix_; }
  void setSampleMix(float v) override { sampleMix_ = clamp01(v); }
  bool hasSampleBank() const override { return smp_.loaded(); }

  /** Hand over a pack built by tools/saxpack/extract.py. Returns false if malformed. */
  bool loadSamples(const uint8_t* bytes, size_t size) { return smp_.load(bytes, size); }
  bool hasSamples() const { return smp_.loaded(); }

  void process(float* io, int n) override {
    NoDenormals nd;
    if (ampStage_ == 0 && relClickEnv_ < 1e-4f && rev_.meter(0) < 1e-4f && !smp_.active()) {
      std::fill(io, io + 2 * n, 0.f);
      setMeter(0, 0.f);
      return;
    }
    const int ty = static_cast<int>(std::lround(p(0) * 3.f)), rd = static_cast<int>(std::lround(p(3) * 2.f)), mp = static_cast<int>(std::lround(p(2) * 2.f));
    // The reed table is built from the type (envelope) and the brightness (q);
    // both are parameters, so the table has to follow them.
    if (ty != ty_) { ty_ = ty; buildTable(); }
    const float br = clamp01(p(19) * .75f + RD_BR[rd] + MP_BR[mp]);
    const float q = 1.15f - br * .65f;
    if (std::fabs(q - q_) > 1e-4f) { q_ = q; buildTable(); }
    // Pitch-scaled formants (DeepSeek patch): a sax is not a fixed filter bank; the reed
    // formants track the fingered pitch by ~10%, the anchor formant by ~5%.
    const float f1 = TY_f1[ty] * pitchScale_, f2 = f1 * 2.7f, af = TY_af[ty] * afScale_;
    const float vf = (1400.f - p(18) * 700.f) * vfJit_, vfg = 3.f + p(18) * 4.f;
    const float att = (.012f + std::pow(1.f - p(8), 2.f) * .14f * RD_ATT[rd]) * humAtk_;
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
    // Last sample's live frequency, for the sample reader's pitch factor (see the block
    // comment after the loop). Seeded on the target so an idle block reads as "no detune".
    float lastF = target_;
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
      pitch *= std::pow(2.f, bend_ * static_cast<float>(bendSemis_) / 12.f);
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
      const float vib = (vshape + sineTable().at(vibDriftPh_) * .5f) * vibDep * humVib_ * vibGate;
      // ---- shared breath LFO ----
      breathLfoPh_ += static_cast<float>(bLfoRate * invSr); if (breathLfoPh_ >= 1.f) breathLfoPh_ -= 1.f;
      const float bLfo = sineTable().at(breathLfoPh_);
      // ---- reed oscillators: two detuned copies + optional sub ----
      const float f = pitch * humPitch_ * std::pow(2.f, (vib + bLfo * bPitchDepth) / 1200.f);
      lastF = f;
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
      // ---- hybrid mix: the pack supplies the timbre, the synthesis stays under it ----
      // Applied after the formant chain on purpose: the recorded sax already carries
      // its own formant, so running it through form_[0..3] would apply it twice.
      // The sample keeps its own envelope, hence the mix happens after `amp`.
      if (sampleMix_ > .001f && smp_.active()) {
        float sm = 0.f;
        smp_.render(&sm, 1, kSampleGain);
        x = x * (1.f - sampleMix_) + sm * sampleMix_;
      }
      // ---- key click: at note-on, and a shorter one at release when there is a fall ----
      // Both sit *after* the mix, not before it. They used to be summed into the synthesis
      // branch, so at mix 1.0 the mix replaced the whole sum and threw the click away: the
      // measured attack of the sampled voice was 0.08x its sustain, against 0.41x for the
      // synthesis, which is the "no punch" the user heard. The reed slap belongs to the
      // instrument, not to one of its two sound sources.
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
      // ---- bite ----
      // A saxophone note starts with a reed slap the microphone never captures, so at mix 1.0
      // the instrument had no attack transient at all. A short lift gated to the first ~35 ms
      // of the note restores it; it is applied after the mix, so both voices get it.
      biteT_ += invSr;
      if (biteT_ < .035f) {
        const float u = 1.f - biteT_ / .035f;
        x *= 1.f + u * u * (0.9f + p(7) * 0.6f);
      }
      // Output gain. This was 0.8, chosen when the synthesis alone carried the voice. Measured
      // at Level 0.8 across six instrument/reed combinations, the pack and the synthesis come
      // out at the same RMS within 25% (ratios 0.99, 1.04, 0.99, 1.20, 0.91, 1.25 — mean 1.06),
      // so 1.0 is what matches them and 0.8 left the sampled voice about 15% quieter than the
      // synth it replaces. A first correction to 1.30 overshot in the other direction (+38%).
      x *= p(20) * 1.0f;
      io[2 * i] += x * humLevel_; io[2 * i + 1] += x * humLevel_;
      peak = std::max(peak, std::fabs(x));
    }
    if (p(16) > 0.01f) rev_.process(io, n);
    // ---- soft clip, on the SUM, after the reverb ----
    // A real recording has a far higher crest factor than the additive voice, and the reverb is
    // added on top of it: measured at Level 1.0, the pack alone reaches 0.91 dry but 1.29 once
    // the room is added, with 8.4 % of its samples above 0.95, against 0.92 and 0 % for the
    // synthesis. The mixer's limiter was therefore squashing every sustain peak of the sampled
    // voice — that distortion, more than the timbre, is why it was judged worse than the plain
    // synthesis. Placing the knee *after* the reverb is what makes it effective: inside the loop
    // it did nothing, because the reverb is added afterwards.
    //
    // The knee starts at 0.75, so nothing the synthesis produces (peak 0.92 only with the room
    // off) is touched in the dry path, and the pack's peaks are rounded rather than clipped.
    // The alternative — lowering kSampleGain until the peaks match — would leave the pack 35 %
    // quieter than the instrument it replaces.
    for (int i = 0; i < n; ++i) {
      for (int c = 0; c < 2; ++c) {
        float& v = io[2 * i + c];
        if (v > .75f) v = .75f + .25f * std::tanh((v - .75f) / .25f);
        else if (v < -.75f) v = -.75f + .25f * std::tanh((v + .75f) / .25f);
      }
    }
    // Push the live pitch factor to the sample reader once per block, after the loop: bend,
    // vibrato, scoop and fall are computed inside it, and at mix 1.0 the samples ARE the
    // voice, so without this none of those controls could be heard.
    smp_.setPitchScale((target_ > 1e-6f) ? lastF / target_ : 1.f);
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

  // Measured harmonic envelopes of the MTG Solo Saxophones library (CC-BY 4.0),
  // normalised to h1. Taken by Goertzel on the sustain loop of 33 notes per type
  // with the fundamental measured (not read from the SFZ: the library is tuned
  // about 5 % flat). These are *relative* corrections against the old 1/n^1.15
  // law, not absolute spectra: the measured signal already carries the formant
  // that form_[0..3] reproduces, so using the raw envelope would apply it twice.
  // See tools/saxpack/ and the session note in AGENTS.md.
  static constexpr float ENV_SOP[32] = {
      1.000f, 0.904f, 0.512f, 0.224f, 0.135f, 0.062f, 0.039f, 0.037f,
      0.028f, 0.020f, 0.014f, 0.011f, 0.008f, 0.007f, 0.007f, 0.005f,
      0.004f, 0.003f, 0.004f, 0.003f, 0.002f, 0.002f, 0.001f, 0.001f,
      0.001f, 0.001f, 0.001f, 0.001f, 0.000f, 0.000f, 0.001f, 0.000f,
  };
  static constexpr float ENV_ALT[32] = {
      1.000f, 0.594f, 0.477f, 0.232f, 0.210f, 0.174f, 0.117f, 0.118f,
      0.092f, 0.064f, 0.047f, 0.044f, 0.043f, 0.045f, 0.043f, 0.039f,
      0.032f, 0.024f, 0.021f, 0.017f, 0.016f, 0.013f, 0.010f, 0.007f,
      0.005f, 0.004f, 0.004f, 0.002f, 0.002f, 0.002f, 0.002f, 0.002f,
  };
  static constexpr float ENV_TEN[32] = {
      1.000f, 0.869f, 0.782f, 0.452f, 0.411f, 0.326f, 0.265f, 0.218f,
      0.165f, 0.171f, 0.176f, 0.157f, 0.133f, 0.105f, 0.081f, 0.065f,
      0.064f, 0.058f, 0.053f, 0.044f, 0.040f, 0.039f, 0.034f, 0.037f,
      0.029f, 0.027f, 0.025f, 0.021f, 0.016f, 0.012f, 0.013f, 0.012f,
  };
  // The baritone genuinely has h2 and h3 above h1: a large conical bore radiates
  // its fundamental weakly. Verified with the measured f0 (ratio 0.94, so no
  // octave error), and confirmed on the absolute spectrum (h1 -3.9 dB against a
  // maximum at h3). It is a real acoustic signature, not a measurement artefact.
  static constexpr float ENV_BAR[32] = {
      1.000f, 1.886f, 2.520f, 1.600f, 1.989f, 1.004f, 1.082f, 0.976f,
      0.967f, 0.867f, 0.867f, 0.709f, 0.609f, 0.484f, 0.518f, 0.569f,
      0.512f, 0.439f, 0.432f, 0.410f, 0.404f, 0.324f, 0.337f, 0.327f,
      0.272f, 0.254f, 0.266f, 0.231f, 0.205f, 0.166f, 0.142f, 0.141f,
  };
  static const float* envTable(int ty) {
    switch (ty) { case 0: return ENV_SOP; case 2: return ENV_TEN; case 3: return ENV_BAR; default: return ENV_ALT; }
  }

  static float fastTanh(float x) { if (x > 3.f) return 1.f; if (x < -3.f) return -1.f; const float x2 = x * x; return x * (27.f + x2) / (27.f + 9.f * x2); }
  // Asymmetric grit: the positive half is driven harder than the negative one, which is what
  // gives the reed its edge without sounding like a symmetric distortion.
  static float grit(float x, float k) {
    const float kp = k * 1.15f, kn = k * .9f;
    return x > 0.f ? fastTanh(x * kp) / fastTanh(kp) : fastTanh(x * kn) / fastTanh(kn);
  }
  // Harmonic reed table. Two modes:
  //
  //  * default: the generic law im[n] = (1/n^q) * (n odd ? 1 : .78), like saxWave()
  //    in the reference preview. The table is NOT normalised, so the oscillator
  //    level already changes with `q` (peak spans 1.18 to 5.35 across the brightness
  //    range) and the engine relies on that.
  //  * measured: the same law multiplied by the measured envelope of the type,
  //    normalised in level so that only the *shape* changes. This is the "additive"
  //    correction: the real sax keeps far more energy in harmonics 2..6 than 1/n^1.15,
  //    which is what its edge comes from. The envelope is applied as a ratio against
  //    the generic law because the measured spectrum already contains the formant
  //    that form_[0..3] reproduces; using it raw would apply the formant twice.
  void buildTable() {
    constexpr int N = 1024, H = 32;
    const float* env = measured_ ? envTable(ty_) : nullptr;
    // normalise the correction so the peak matches the unmeasured table
    float norm = 1.f;
    if (env) {
      float a = 0.f, b = 0.f;
      for (int i = 0; i < N; ++i) {
        const float t = static_cast<float>(i) / N;
        float plain = 0.f, corr = 0.f;
        for (int h = 1; h <= H; ++h) {
          const float g = std::pow(1.f / h, q_) * ((h & 1) ? 1.f : .78f);
          const float s = std::sin(2.f * static_cast<float>(kPi) * h * t);
          plain += s * g;
          corr += s * g * env[h - 1];
        }
        a = std::max(a, std::fabs(plain));
        b = std::max(b, std::fabs(corr));
      }
      norm = (b > 1e-6f) ? a / b : 1.f;
    }
    for (int i = 0; i < N; ++i) {
      const float t = static_cast<float>(i) / N;
      float s = 0.f;
      for (int h = 1; h <= H; ++h) {
        float g = std::pow(1.f / h, q_) * ((h & 1) ? 1.f : .78f);
        if (env) g *= env[h - 1] * norm;
        s += std::sin(2.f * static_cast<float>(kPi) * h * t) * g;
      }
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

  /**
   * Rolls the per-note human variation.
   *
   * At 0 the instrument is exact: every note has the same attack, level, intonation and
   * vibrato, which is what makes a MIDI line sound machine-made. At 1 a player's spread is
   * applied — up to 14 cents of intonation, 22 % of attack time, 1.8 dB of level and 30 % of
   * vibrato depth, all drawn per note from the same generator [seed] uses, so it is
   * deterministic for a given note sequence and does not drift between takes.
   *
   * The pitch spread is the one that matters most for a sax: players never land exactly on the
   * tempered pitch, and a fixed offset per note is what the ear reads as "played" rather than
   * "triggered". It is applied to the samples as well, through the same `f` the reader follows.
   */
  void humanize() {
    hum_ = p(21);
    if (hum_ < 1e-4f) { humPitch_ = 1.f; humLevel_ = 1.f; humAtk_ = 1.f; humVib_ = 1.f; return; }
    auto r = [this]() { rng_ ^= rng_ << 13; rng_ ^= rng_ >> 17; rng_ ^= rng_ << 5; return static_cast<float>(static_cast<int32_t>(rng_)) * (1.0f / 2147483648.0f); };
    humPitch_ = std::pow(2.f, ((.5f + r() * .5f) * 2.f - 1.f) * hum_ * 14.f / 1200.f);
    humLevel_ = std::pow(10.f, ((.5f + r() * .5f) * 2.f - 1.f) * hum_ * 1.8f / 20.f);
    humAtk_ = 1.f + ((.5f + r() * .5f) * 2.f - 1.f) * hum_ * .22f;
    humVib_ = 1.f + ((.5f + r() * .5f) * 2.f - 1.f) * hum_ * .30f;
  }

  double sr_ = 48000.0;
  float tbl_[1025] = {0};
  // Measured-table state. `measured_` is the "additive" correction: when on, the
  // reed table carries the harmonic envelope measured on the real instruments
  // instead of the generic 1/n^q law, which is what removes the synthetic
  // character. Exposed so a preset or the UI can fall back to the old voice.
  int ty_ = 1;
  bool measured_ = true;
  SaxSampleReader smp_;
  float sampleMix_ = 0.f;   // 0 = pure synthesis, 1 = pure samples
  // Level trim so a note played from the pack matches the synthesis it blends
  // with. Measured on the baritone at mix 1: synthesis 0.230 RMS, pack 0.0753,
  // so the pack sits 3.05x lower. The pack itself is normalised to a canonical
  // 0.20 RMS by tools/saxpack/extract.py, which keeps it free of clipping; the
  // balance is the engine's business, not the asset's.
  static constexpr float kSampleGain = 3.05f;
  Biquad form_[4], lpF_, nbF_, clickF_, warm_, bright_;
  SpaceReverb rev_;
  Noise noise_;
  std::vector<int> held_;
  int note_ = -1, ampStage_ = 0;
  float bend_ = 0.f;
  float freq_ = 440.f, target_ = 440.f, vel_ = 0.f, ampT_ = 0.f, ampLvl_ = 0.f, relT_ = 0.f, relR_ = .2f;
  float ph_[3] = {0, 0, 0}, vibPh_ = 0.f, vibDriftPh_ = 0.f, breathLfoPh_ = 0.f, growlPh_ = 0.f;
  float biteT_ = 1.f;
  // Humanize: per-note spread (see humanize()).
  float hum_ = 0.f, humPitch_ = 1.f, humLevel_ = 1.f, humAtk_ = 1.f, humVib_ = 1.f;
  float scoopT_ = 0.f, fallT_ = 0.f, clickEnv_ = 0.f, relClickEnv_ = 0.f;
  float q_ = -1.f, nVar_ = 1.f, pitchScale_ = 1.f, afScale_ = 1.f, nGainJit_ = 1.f, vfJit_ = 1.f, clickJit_ = 1.f;
  float fallStart_ = 440.f, fallEnd_ = 440.f;
  uint32_t rng_ = 0x2545F491u;
};

}  // namespace aetherfx
