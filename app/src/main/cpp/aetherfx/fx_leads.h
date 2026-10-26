// Aether Leads: 5-layer poly/mono lead synth (sine, pulse, saw, super-saw, soft-tri),
// per-voice AHDSR amp + filter envelope, TPT state-variable filter, then a shared bus:
// chorus -> tone shelves -> delay -> reverb -> out. Vibrato is a single bus LFO on the
// oscillator detune, exactly like the reference.
// Params (32): 0..4 layer levels (sine, square, saw, super, soft) | 5..9 octave selectors (0.5/1/2)
// 10 pulse width 11 detune | 12 Attack 13 Hold 14 Decay 15 Sustain 16 Release | 17 filter type (LP,HP,BP)
// 18 cutoff (40Hz..20kHz, 40*500^n) 19 resonance 20 env amount | 21 delay time 22 feedback 23 mix
// 24 reverb size 25 reverb mix 26 chorus 27 tone | 28 mode (Poly,Mono,Legato) 29 glide 30 vibrato 31 level
#pragma once
#include "fx_source.h"
#include "fx_space.h"

namespace aetherfx {

class LeadsEngine : public SourceFx {
  static constexpr int kV = 6, kOsc = 10;
  struct Voice {
    bool on = false, gate = false;
    int note = -1, stage = 0;
    float vel = 0.f, pit = 60.f, target = 60.f, age = 0.f, envT = 0.f;
    // Per-voice randomness (DeepSeek patch): slow drift LFO, fixed detune offset, envelope
    // timing spread, filter-Q jitter and saturation drive. Seeded at note-on so a voice is
    // stable for its whole life but no two voices are identical.
    float driftPh = 0.f, driftPh2 = .5f, driftDepth = 2.5f, voiceOff = 0.f, varA = 1.f, varD = 1.f, qJit = 0.f, satDrive = 1.3f;
    uint32_t rng = 1u;
    float ph[kOsc] = {0};
    float ic[2] = {0, 0};
  };

 public:
  int paramCount() const override { return 32; }

  void prepare(double sr, int maxBlock) override {
    sr_ = sr;
    const int sc = static_cast<int>(sr / 48000.0);
    dl_[0].init(static_cast<int>(0.06 * sr) + 8);
    dl_[1].init(static_cast<int>(0.06 * sr) + 8);
    echo_.init(static_cast<int>(1.25 * sr) + 8);
    rev_.prepare(sr, maxBlock);
    reset();
  }
  void reset() override {
    for (auto& v : v_) v = Voice();
    for (int i = 0; i < 2; ++i) { dl_[i].clear(); modPh_[i] = i * 0.3f; }
    echo_.clear(); vibPh_ = 0.f; held_.clear();
    rev_.reset();
    setMeter(0, 0.f);
  }

  void noteOn(int note, int vel) override {
    if (note < 0 || note > 127) return;
    if (vel <= 0) { noteOff(note); return; }
    const int mode = static_cast<int>(std::lround(p(28) * 2.f));
    if (mode == 0) {  // poly: one voice per note, oldest stolen beyond kV
      int idx = -1;
      for (int i = 0; i < kV; ++i) if (!v_[i].on) { idx = i; break; }
      if (idx < 0) { float best = 1e9f; for (int i = 0; i < kV; ++i) { const float a = v_[i].gate ? v_[i].age + 1e6f : v_[i].age; if (a < best) { best = a; idx = i; } } }
      Voice& v = v_[idx];
      const float prev = v.on ? v.pit : static_cast<float>(note);
      v.on = true; v.gate = true; v.note = note; v.vel = vel / 127.f; v.stage = 1; v.envT = 0.f;
      v.pit = prev; v.target = static_cast<float>(note); v.age = ++age_;
      seed(v);
      return;
    }
    // mono / legato: a single voice, gliding from the previous pitch
    held_.push_back(note);
    Voice& v = v_[0];
    const bool wasOn = v.on;
    const float prev = v.on ? v.pit : static_cast<float>(note);
    v.note = note; v.vel = vel / 127.f; v.on = true; v.gate = true; v.target = static_cast<float>(note); v.age = ++age_;
    if (mode == 1 || !wasOn) { v.stage = 1; v.envT = 0.f; seed(v); }
    v.pit = prev;
  }
  void noteOff(int note) override {
    const int mode = static_cast<int>(std::lround(p(28) * 2.f));
    if (mode == 0) {
      for (auto& v : v_) if (v.on && v.gate && v.note == note) { v.gate = false; v.stage = 5; }
      return;
    }
    for (size_t i = 0; i < held_.size(); ++i) if (held_[i] == note) { held_.erase(held_.begin() + static_cast<long>(i)); break; }
    if (v_[0].note != note) return;
    if (!held_.empty()) v_[0].note = held_.back(), v_[0].target = static_cast<float>(held_.back());
    else { v_[0].gate = false; v_[0].stage = 5; }
  }
  void allNotesOff() override { held_.clear(); for (auto& v : v_) if (v.on) { v.gate = false; v.stage = 5; } }
  void pitchBend(float b) override { bend_ = b; }

  void process(float* io, int n) override {
    NoDenormals nd;
    bool any = false;
    for (auto& v : v_) if (v.on) { any = true; break; }
    if (!any && tail_ < 1e-4f && rev_.meter(0) < 1e-4f) {
      std::fill(io, io + 2 * n, 0.f);
      setMeter(0, 0.f);
      return;
    }
    const float lvl[5] = {std::pow(p(0), 1.4f) * .9f, std::pow(p(1), 1.4f) * .42f, std::pow(p(2), 1.4f) * .34f, std::pow(p(3), 1.4f) * .16f, std::pow(p(4), 1.4f) * .6f};
    const float pw = .05f + p(10) * .45f, det = p(11) * 18.f;
    const float A = .002f + p(12) * p(12) * 2.5f, H = p(13) * p(13) * 1.5f, D = .01f + p(14) * p(14) * 3.f, S = p(15);
    const float R = .01f + p(16) * p(16) * 4.f;
    const int ftype = static_cast<int>(std::lround(p(17) * 2.f));
    const float fc0 = 40.f * std::pow(500.f, p(18)), pk = std::min(18000.f, fc0 * std::pow(2.f, p(20) * 6.f));
    const float kfBase = .5f + p(19) * p(19) * 14.f;
    const int mode = static_cast<int>(std::lround(p(28) * 2.f));
    const float glTime = p(29) * p(29) * 1.2f / 3.f;
    const float gl = glTime < .002f ? 1.f : 1.f - std::exp(static_cast<float>(-1.0 / (glTime * sr_)));
    const float vibD = p(30) * 45.f, outG = p(31) * 1.1f;
    const float invSr = static_cast<float>(1.0 / sr_);
    const float echoT = (0.06f + p(21) * p(21) * .84f) * static_cast<float>(sr_);
    const float echoFb = p(22) * .82f, echoW = p(23) * .8f, choW = p(26) * .7f;
    toneHi_.setHighShelf(sr_, 3500, (p(27) - .5f) * 18.f);
    toneLo_.setLowShelf(sr_, 220, (.5f - p(27)) * 8.f);
    rev_.setNorm(0, p(24)); rev_.setNorm(5, p(25) * .9f);
    float peak = 0.f;
    for (int i = 0; i < n; ++i) {
      vibPh_ += static_cast<float>(5.4 / sr_); if (vibPh_ >= 1.f) vibPh_ -= 1.f;
      const float vib = sineTable().at(vibPh_) * vibD;
      float mono = 0.f;
      for (auto& v : v_) {
        if (!v.on) continue;
        stepEnv(v, A, H, D, R, invSr);
        if (v.stage >= 6) { v.on = false; continue; }
        const float tgt = v.target + bend_ * 2.f;
        v.pit += (tgt - v.pit) * (mode == 0 ? 1.f : gl);
        const float f = 440.f * std::pow(2.f, (v.pit - 69.f) / 12.f);
        // Per-voice slow drift (DeepSeek patch): a 0.12..0.47 Hz LFO on the detune, so held
        // voices never sit perfectly still. depth 1.5..4 cents.
        v.driftPh += static_cast<float>(0.12f + v.driftPh2 * 0.35f) * invSr; if (v.driftPh >= 1.f) v.driftPh -= 1.f;
        const float drift = sineTable().at(v.driftPh) * v.driftDepth;
        const float detuneAll = vib + v.voiceOff + drift;
        const float fc = fcAt(v, fc0, pk, S, A * v.varA, D * v.varD);
        const float kf = 1.f / (kfBase + v.qJit);
        const float g = std::tan(static_cast<float>(kPi) * std::min(fc, .45f * static_cast<float>(sr_)) * invSr);
        const float a1 = 1.f / (1.f + g * (g + kf)), a2 = g * a1, a3 = g * a2;
        float sum = 0.f;
        sum += osc(v, 0, f, oct(p(5)), detuneAll, 0, lvl[0], pw, invSr);
        sum += osc(v, 1, f, oct(p(6)), detuneAll, 1, lvl[1], pw, invSr);
        sum += osc(v, 2, f, oct(p(7)), detuneAll, 2, lvl[2], pw, invSr);
        for (int k = 0; k < 5; ++k) sum += osc(v, 3 + k, f, oct(p(8)), detuneAll + (k - 2) * det, 2, lvl[3], pw, invSr);
        sum += osc(v, 8, f, oct(p(9)), detuneAll - 5.f, 3, lvl[4], pw, invSr);
        sum += osc(v, 9, f, oct(p(9)), detuneAll + 5.f, 3, lvl[4], pw, invSr);
        sum = fastTanh(sum * .55f * v.satDrive);
        const float v3 = sum - v.ic[1], v1 = a1 * v.ic[0] + a2 * v3, v2 = v.ic[1] + a2 * v.ic[0] + a3 * v3;
        v.ic[0] = 2.f * v1 - v.ic[0]; v.ic[1] = 2.f * v2 - v.ic[1];
        const float y = ftype == 0 ? v2 : ftype == 1 ? sum - kf * v1 - v2 : v1;
        mono += y * ampAt(v, v.vel, A * v.varA, D * v.varD, S, R);
      }
      // ---- shared bus: chorus -> tone shelves -> delay -> (reverb below) ----
      float ch = mono;
      for (int c = 0; c < 2; ++c) {
        modPh_[c] += static_cast<float>((c == 0 ? 0.7 : 1.1) / sr_); if (modPh_[c] >= 1.f) modPh_[c] -= 1.f;
        const float d = (c == 0 ? .016f : .023f) * static_cast<float>(sr_) + sineTable().at(modPh_[c]) * .003f * static_cast<float>(sr_);
        dl_[c].write(mono);
        ch += dl_[c].read(d) * choW;
      }
      float x = toneHi_.process(0, toneLo_.process(0, ch));
      const float d = echo_.read(echoT);
      echo_.write(x + d * echoFb);
      x = (x + d * echoW) * outG;
      io[2 * i] += x; io[2 * i + 1] += x;
      peak = std::max(peak, std::fabs(x));
    }
    if (p(25) > 0.01f) rev_.process(io, n);
    tail_ = peak;
    setMeter(0, peak);
  }

 private:
  static float oct(float v) { return std::pow(2.f, static_cast<float>(std::lround(v * 2.f)) - 1.f); }
  static float fastTanh(float x) { if (x > 3.f) return 1.f; if (x < -3.f) return -1.f; const float x2 = x * x; return x * (27.f + x2) / (27.f + 9.f * x2); }
  static float blep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.f; }
    if (t > 1.f - dt) { t = (t - 1.f) / dt; return t * t + t + t + 1.f; }
    return 0.f;
  }
  // shape 0 sine, 1 pulse, 2 saw, 3 triangle. dt = phase increment, for the band-limited edges.
  static float wave(int shape, float ph, float dt, float pw) {
    switch (shape) {
      case 0: return sineTable().at(ph);
      case 1: {
        float s = ph < pw ? 1.f : -1.f;
        s += blep(ph, dt);
        s -= blep(std::fmod(ph - pw + 1.f, 1.f), dt);
        return s;
      }
      case 2: return 2.f * ph - 1.f - blep(ph, dt);
      default: return 4.f * std::fabs(ph - .5f) - 1.f;
    }
  }
  float osc(Voice& v, int slot, float f, float octMul, float cents, int shape, float gain, float pw, float invSr) {
    if (gain <= .02f) return 0.f;
    const float inc = f * octMul * std::pow(2.f, cents / 1200.f) * invSr;
    float& ph = v.ph[slot];
    const float s = wave(shape, ph, inc, pw);
    ph += inc; if (ph >= 1.f) ph -= 1.f;
    return s * gain;
  }
  // Per-voice randomness seeded at note-on: drift LFO rate/depth, a fixed detune offset
  // (voiceOff, +-3.5 cents), envelope timing spread (+-12%), filter-Q jitter and saturation
  // drive. Mirrors the Math.random() calls of the reference mkLead().
  static void seed(Voice& v) {
    auto r = [&v]() { v.rng ^= v.rng << 13; v.rng ^= v.rng >> 17; v.rng ^= v.rng << 5; return static_cast<float>(static_cast<int32_t>(v.rng)) * (1.0f / 2147483648.0f); };
    r();
    v.driftPh2 = .5f + r() * .5f;
    v.driftDepth = 1.5f + (.5f + r() * .5f) * 2.5f;
    v.voiceOff = r() * 7.f;
    v.varA = .88f + (.5f + r() * .5f) * .24f;
    v.varD = .88f + (.5f + r() * .5f) * .24f;
    v.qJit = r() * .5f;
    v.satDrive = 1.1f + (.5f + r() * .5f) * .4f;
  }
  // One stage machine drives both the amplitude and the filter envelope (same A/H/D).
  static void stepEnv(Voice& v, float A, float H, float D, float R, float invSr) {
    v.envT += invSr;
    switch (v.stage) {
      case 1: if (v.envT >= A) { v.stage = 2; v.envT = 0.f; } break;
      case 2: if (v.envT >= H) { v.stage = 3; v.envT = 0.f; } break;
      case 3: if (v.envT >= D) { v.stage = 4; v.envT = 0.f; } break;
      case 5: if (v.envT >= R) { v.stage = 6; } break;
      default: break;
    }
  }
  static float ampAt(const Voice& v, float vel, float A, float D, float S, float R) {
    switch (v.stage) {
      case 1: return vel * std::min(1.f, v.envT / std::max(1e-4f, A));
      case 2: return vel;
      case 3: return vel * (1.f - (1.f - S) * std::min(1.f, v.envT / std::max(1e-4f, D)));
      case 4: return vel * S;
      case 5: return vel * S * (1.f - std::min(1.f, v.envT / std::max(1e-4f, R)));
      default: return 0.f;
    }
  }
  static float fcAt(const Voice& v, float fc0, float pk, float S, float A, float D) {
    const float hi = std::max(pk, fc0 + 1.f), end = std::max(fc0 * std::pow(2.f, S * 6.f), 20.f);
    switch (v.stage) {
      case 1: return fc0 * std::pow(hi / fc0, std::min(1.f, v.envT / std::max(1e-4f, A)));
      case 2: return hi;
      case 3: return hi * std::pow(end / hi, std::min(1.f, v.envT / std::max(1e-4f, D)));
      default: return end;
    }
  }

  double sr_ = 48000.0;
  Voice v_[kV];
  DelayLine dl_[2], echo_;
  SpaceReverb rev_;
  Biquad toneLo_, toneHi_;
  std::vector<int> held_;
  float modPh_[2] = {0, 0}, vibPh_ = 0.f, bend_ = 0.f, age_ = 0.f, tail_ = 0.f;
};

}  // namespace aetherfx
