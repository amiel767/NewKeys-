// Aether Organ: tonewheel organ engine (additive, 91-wheel style), two drawbar manuals, percussion, key click, vibrato/chorus, rotary speaker, drive.
// Params (31): 0..8 Upper drawbars 16' 5 1/3' 8' 4' 2 2/3' 2' 1 3/5' 1 1/3' 1' (0..8 steps) | 9..17 Lower | 18 PercOn 19 PercSoft 20 PercFast 21 Perc3rd
// 22 VibType (0..5 = V1 V2 V3 C1 C2 C3, value*5) 23 VibUpper 24 VibLower | 25 Rotary (0 Stop, .5 Slow, 1 Fast) | 26 Click 27 Tone 28 Drive 29 Space 30 Level
// 31 ReverbMode (0 room, .5 stage, 1 hall) | 32 Treble 33 Cabinet 34 Amount 35 VelSense
// Appended, all neutral at .5 so a preset saved before they existed still sounds the same:
// 36 Mid (peaking 800 Hz, +/-18 dB) | 37 RotRise 38 RotFall (rotor ramp, .5 = the hardware's 0.7 s
// horn / 2.5 s drum) | 39 RotDepth 40 RotMic 41 RotBalance (Leslie AM, Doppler and horn/drum mix)
// 42 Model (0 Pipe, .5 B3, 1 Farf) | 43 ModelDial (extra top character) | 44 Octave (.5 = 0)
// 45 Bass (low shelf 250 Hz, +/-18 dB)
// The five appended params default to 0.5, which is the neutral value for each: mode .5 is the
// original reverb voicing, treble .5 is a flat shelf, cabinet .5 is the unmodified Leslie, amount
// .5 is the original send and velSense .5 keeps velocity ignored, as it always was. A preset saved
// before they existed therefore still sounds exactly as it did.
// Keys below the split (MIDI 60) play the LOWER manual, the others the UPPER manual.
#pragma once
#include "fx_source.h"
#include "fx_space.h"

namespace aetherfx {

class OrganEngine : public SourceFx {
 public:
  static constexpr int kSplit = 60;
  int paramCount() const override { return 46; }
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
    std::memset(oct_, 0, sizeof(oct_));
    for (auto& v : vel_) v = 1.f;
    percEnv_ = 0.f; clickEnv_ = 0.f; hornSp_ = drumSp_ = 0.f; hornPh_ = drumPh_ = 0.f; vibPh_ = 0.f;
    for (auto& d : dlH_) d.clear(); for (auto& d : dlD_) d.clear(); dlV_.clear(); rev_.reset(); for (auto& b : xo_) b.reset(); tone_.reset();
    for (auto& s : sm_) s.init = false;
    mid_.reset(); bs_.reset(); mdlLow_.reset(); mdlTop_.reset(); atkEnv_ = 1.f;
  }
  void noteOn(int note, int vel) override {
    if (note < 0 || note > 127) return;
    if (vel <= 0) { noteOff(note); return; }
    const int oct = octaveNow();
    const int n = note + 12 * oct;
    if (n < 0 || n > 127) return;
    // The octave is a live parameter now, so a key can be released at a different setting than
    // it was pressed at. Remember the shift per key, or the note would hang on a new octave.
    oct_[note] = oct;
    if (heldCount_ == 0) percEnv_ = 1.f;  // single trigger, like the hardware
    if (!held_[n]) { held_[n] = true; ++heldCount_; }
    // Velocity, only when VelSense asks for it. 0.5 maps to unity, so the default leaves the
    // wheel amplitude untouched and the keyboard's own velocity curve out of the organ's tone.
    const float vs = p(35);
    vel_[n] = 1.f + (vs - 0.5f) * 2.f * (vel / 127.f - 1.f) * 0.8f;
    clickEnv_ = 1.f; atkEnv_ = 0.f;  // the pipe model needs the attack to start from zero
  }
  void noteOff(int note) override {
    if (note < 0 || note > 127) return;
    const int n = note + 12 * oct_[note];
    oct_[note] = 0;
    if (n >= 0 && n < 128 && held_[n]) { held_[n] = false; --heldCount_; }
  }
  void allNotesOff() override {
    std::memset(held_, 0, sizeof(held_)); std::memset(oct_, 0, sizeof(oct_)); heldCount_ = 0;
  }
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
    // p(42) >= 0.5 keeps the early-out off the pipe model, whose wheels decay over seconds after
    // the key is released: skipping the block would cut that tail dead.
    if (heldCount_ == 0 && percEnv_ < 1e-4f && clickEnv_ < 1e-4f && rotNow == 0 && p(42) >= 0.5f && rev_.meter(0) < 1e-4f) {
      std::fill(io, io + 2 * n, 0.f);
      setMeter(0, 0.f);
      return;
    }
    const float cf = smoothCoef(n, sr_, 0.02);
    // Range comes from Track Settings via SourceFx::setPitchBendRange (default 2 st = 1 tone).
    const float bendRatio = std::pow(2.f, bend_ * static_cast<float>(bendSemis_) / 12.f);
    const float click = sm_[0].step(p(26), cf), tone = sm_[1].step(p(27), cf), drive = sm_[2].step(p(28), cf), space = sm_[3].step(p(29), cf), lvl = sm_[4].step(p(30), cf);
    const float treble = sm_[5].step(p(32), cf), cab = sm_[6].step(p(33), cf), amt = sm_[7].step(p(34), cf);
    const float mid = sm_[8].step(p(36), cf), bass = sm_[9].step(p(45), cf);
    const float rRise = sm_[10].step(p(37), cf), rFall = sm_[11].step(p(38), cf);
    const float rDepth = sm_[12].step(p(39), cf), rMic = sm_[13].step(p(40), cf), rBal = sm_[14].step(p(41), cf);
    const float model = sm_[15].step(p(42), cf), mdlDial = sm_[16].step(p(43), cf);
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
        tw_[fold(k + kOff[j])] += drawAmp(db[j]) * vel_[k];
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
    tone_.setHighShelf(sr_, 3000, (tone - 0.5f) * 16.f);
    // Mid is the band the reference's three-knob EQ has and Tone cannot reach: a bell at 800 Hz.
    mid_.setPeaking(sr_, 800, 0.9, (mid - 0.5f) * 36.f);
    // Bass is the reference's own low shelf, the counterpart of Treble at the other end.
    bs_.setLowShelf(sr_, 250, (bass - 0.5f) * 36.f);
    // Organ model, the reference's B3 / PIPE / FARF selector. It is a real voicing switch, not a
    // label: the pipes lose the wheel fold-back and the bottom octave, the Farf narrows to its own
    // band and its wheels decay, the B3 is untouched at .5.
    const float mdlP = clamp01((0.5f - model) * 2.f), mdlF = clamp01((model - 0.5f) * 2.f);
    const float pipeG = 1.f - 0.45f * mdlP, pipeHP = 90.f * mdlP;
    // Wheel decay: a real pipe does not hold forever and a Farfisa's divider does not either.
    const float wDecay = mdlP > 0.f ? std::exp(static_cast<float>(-1.0 / (7.0 * sr_))) : 1.f;
    const float mdlTilt = 3.5f * mdlF + (mdlDial - 0.5f) * 8.f;
    mdlLow_.setLowShelf(sr_, 400, -6.f * mdlF);
    mdlTop_.setHighShelf(sr_, 2500, mdlTilt);
    mdlHP_[0].setHighpass(sr_, pipeHP, 0.7); mdlHP_[1].setHighpass(sr_, pipeHP, 0.7);
    // Rotor ramps, from the panel's Rise / Fall. .5 is the hardware's 0.7 s / 2.5 s.
    const float rampH = 0.7f * std::pow(2.f, (rRise - 0.5f) * 4.f);
    const float rampD = 2.5f * std::pow(2.f, (rFall - 0.5f) * 4.f);
    // Leslie depth / mic / balance: .5 leaves the reference's own 0.70, 0.50 and even mix.
    const float amH = 0.70f * (rDepth * 2.f), amD = 0.50f * (rDepth * 2.f);
    const float dopH = 0.0006f * (rMic * 2.f), dopD = 0.0004f * (rMic * 2.f);
    const float balH = std::pow(2.f, (rBal - 0.5f) * 2.f), balD = std::pow(2.f, (0.5f - rBal) * 2.f);
    const float aH = 1.f - std::exp(static_cast<float>(-1.0 / (rampH * sr_))), aD = 1.f - std::exp(static_cast<float>(-1.0 / (rampD * sr_)));
    // The pipes have a slower attack than a tonewheel; the wheel model is a percussive key.
    const float atkC = model < 0.5f ? 1.f - std::exp(static_cast<float>(-1.0 / (0.025 * sr_))) : 1.f;
    // Treble is the reference's own treble shelf, on top of Tone: Tone voices the organ, Treble
    // trims the room it is played in.
    treb_.setHighShelf(sr_, 4500, (treble - 0.5f) * 18.f);
    // Reverb mode, the reference's ROOM / STAGE / HALL: at 0.5 it is exactly kRevDef, so the
    // default mode does not change how any existing preset sounds.
    const float md = (p(31) - 0.5f) * 2.f;
    for (int i = 0; i < 7; ++i) rev_.setNorm(i, clamp01(kRevDef[i] + md * kRevMode[i]));
    rev_.setNorm(5, clamp01(0.5f * space + (amt - 0.5f) * 0.6f));
    const float outG = dbToLin(-24.f + 30.f * lvl);
    for (int i = 0; i < n; ++i) {
      float s = 0.f;
      for (int q = 0; q < na; ++q) {
        const int w = act_[q];
        amp_[w] += (tw_[w] - amp_[w]) * aC;
        s += (amp_[w] + pw_[w] * percEnv_) * sineTable().at(ph_[w]);
        ph_[w] += inc_[w] * bendRatio; if (ph_[w] >= 1.f) ph_[w] -= 1.f;
        amp_[w] *= wDecay;
      }
      percEnv_ *= percDec;
      atkEnv_ += (1.f - atkEnv_) * atkC;
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
        const float h = dlH_[ch].read(2.f + dsH * dopH * static_cast<float>(sr_) * (0.5f + 0.5f * sh)) * (1.f + amH * cab * dsH * sh);
        const float d = dlD_[ch].read(2.f + dsD * dopD * static_cast<float>(sr_) * (0.5f + 0.5f * sd)) * (1.f + amD * cab * dsD * sd);
        const float y = mdlHP_[ch].process(ch, (h * balH + d * balD) * pipeG);
        const float z = bs_.process(ch, mid_.process(ch, mdlTop_.process(ch, mdlLow_.process(ch, treb_.process(ch, tone_.process(ch, y))))));
        out[ch] = z * outG * atkEnv_;
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
  // Octave, from the panel's Oct +/-: -2..+2 whole octaves, centred on the neutral .5.
  int octaveNow() const { return static_cast<int>(std::lround((p(44) - 0.5f) * 4.f)); }
  static float drawAmp(int d) { return 0.035f * std::pow(10.f, -3.f * (8 - d) / 20.f); }  // 3 dB per drawbar step
  static constexpr float kRevDef[7] = {0.55f, 0.45f, 0.12f, 0.45f, 0.8f, 0.3f, 0.5f};
  // Per-mode offset from kRevDef: ROOM dries it, HALL opens it. At mode 0.5 the offset is zero.
  static constexpr float kRevMode[7] = {-0.22f, -0.18f, -0.05f, -0.10f, 0.15f, 0.05f, 0.28f};
  double sr_ = 48000.0;
  float inc_[128], amp_[128], ph_[128], tw_[128], pw_[128], vel_[128]; int act_[128]; bool held_[128]; int oct_[128]; int heldCount_ = 0;
  float bend_ = 0.f;
  float percEnv_ = 0.f, clickEnv_ = 0.f, hornSp_ = 0.f, drumSp_ = 0.f, hornPh_ = 0.f, drumPh_ = 0.f, vibPh_ = 0.f, atkEnv_ = 1.f;
  DelayLine dlH_[2], dlD_[2], dlV_; SpaceReverb rev_; Biquad xo_[2], tone_, treb_, mid_, bs_, mdlLow_, mdlTop_, mdlHP_[2]; Noise noise_; BlockSmoother sm_[17];
};

}  // namespace aetherfx
