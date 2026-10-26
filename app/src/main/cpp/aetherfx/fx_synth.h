// Aether Synth: subtractive polysynth engine. 2 morphing oscillators (sine>tri>saw>square) + sub + noise, unison, TPT state-variable filter, ADSR, 2 LFOs, 4 macros.
// Params (32): 0 WaveA 1 WaveB 2 Detune 3 Sub 4 Noise 5 Unison(1..8) 6 Spread | 7 Cutoff(20Hz..20kHz log) 8 Resonance 9 Drive 10 EnvAmt 11 FilterType(0 LP,.33 HP,.67 BP,1 Notch)
// 12 Attack 13 Decay 14 Sustain 15 Release | 16 LFO1 rate 17 LFO1 depth 18 LFO1 shape(sin,tri,saw,sq) 19 LFO1 dest(cutoff,pitch,wave,level) 20 LFO2 rate 21 LFO2 depth (-> pitch)
// 22..25 Macro1..4 (fixed v1 assignment: M1 cutoff +-3 oct, M2 resonance, M3 WaveA morph, M4 drive) | 26 Mode(Poly,Mono,Legato) 27 Glide 28 Voices(1..16) 29 BendRange(1..12) 30 Tune(+-100c) 31 Level
#pragma once
#include "fx_source.h"

namespace aetherfx {

class SynthEngine : public SourceFx {
  static constexpr int kV = 16, kU = 8;
  struct Voice {
    bool on = false, gate = false; int note = -1, stage = 0; float vel = 0.f, env = 0.f, pit = 60.f; uint32_t age = 0;
    float phA[kU], phB[kU], phS = 0.f, ic[2][2] = {{0, 0}, {0, 0}};
  };

 public:
  int paramCount() const override { return 32; }
  void prepare(double sr, int) override { sr_ = sr; reset(); }
  void reset() override {
    for (auto& v : v_) { v = Voice(); for (int u = 0; u < kU; ++u) { v.phA[u] = u * 0.123f; v.phB[u] = u * 0.071f; } }
    l1_ = l2_ = 0.f; age_ = 0; bend_ = 0.f; held_.clear();
  }
  void noteOn(int note, int vel) override {
    if (vel <= 0) { noteOff(note); return; }
    const int mode = static_cast<int>(std::lround(p(26) * 2.f)), maxV = 1 + static_cast<int>(std::lround(p(28) * 15.f));
    held_.push_back(note);
    int idx = -1;
    if (mode > 0) {  // mono / legato: one voice
      idx = 0; bool wasGate = v_[0].gate;
      v_[0].note = note; v_[0].vel = vel / 127.f; v_[0].gate = true; v_[0].on = true; v_[0].age = ++age_;
      if (mode == 1 || !wasGate) { v_[0].stage = 1; if (v_[0].env < 1e-3f) v_[0].pit = static_cast<float>(note); }
      return;
    }
    for (int i = 0; i < maxV && i < kV; ++i) if (!v_[i].on) { idx = i; break; }
    if (idx < 0) {  // steal: oldest releasing, else oldest
      uint32_t best = 0xFFFFFFFFu; for (int i = 0; i < maxV && i < kV; ++i) { const uint32_t a = v_[i].stage == 4 ? v_[i].age : v_[i].age + 0x40000000u; if (a < best) { best = a; idx = i; } }
    }
    Voice& v = v_[idx]; v.on = true; v.gate = true; v.note = note; v.vel = vel / 127.f; v.stage = 1; v.env = 0.f; v.pit = static_cast<float>(note); v.age = ++age_;
    v.ic[0][0] = v.ic[0][1] = v.ic[1][0] = v.ic[1][1] = 0.f;
  }
  void noteOff(int note) override {
    for (size_t i = 0; i < held_.size(); ++i) if (held_[i] == note) { held_.erase(held_.begin() + static_cast<long>(i)); break; }
    const int mode = static_cast<int>(std::lround(p(26) * 2.f));
    if (mode > 0) {
      if (v_[0].note == note) { if (!held_.empty()) { v_[0].note = held_.back(); } else { v_[0].gate = false; v_[0].stage = 4; } }
      return;
    }
    for (auto& v : v_) if (v.on && v.gate && v.note == note) { v.gate = false; v.stage = 4; }
  }
  void allNotesOff() override { held_.clear(); for (auto& v : v_) if (v.on) { v.gate = false; v.stage = 4; } }
  void pitchBend(float b) override { bend_ = b; }

  void process(float* io, int n) override {
    NoDenormals nd;
    // Silent early-out: no voice sounding (releases included) means the block is
    // silence. Skips the oscillator/filter loop that otherwise ran on every block
    // for a loaded-but-idle instrument.
    bool any = false;
    for (auto& v : v_) if (v.on) { any = true; break; }
    if (!any) {
      std::fill(io, io + 2 * n, 0.f);
      setMeter(0, 0.f);
      activeVoices_ = 0;
      return;
    }
    const float wA = p(0), wB = p(1), detune = p(2) * 30.f, sub = p(3), noiseA = p(4) * 0.5f, spread = p(6) * 40.f;
    const int maxV = 1 + static_cast<int>(std::lround(p(28) * 15.f));
    int nU = 1 + static_cast<int>(std::lround(p(5) * 7.f)); nU = std::max(1, std::min(nU, 48 / std::max(1, maxV)));
    const float cut = p(7), res = p(8), drive0 = p(9), envAmt = p(10);
    const int ftype = static_cast<int>(std::lround(p(11) * 3.f));
    const float att = 0.001f * std::pow(4000.f, p(12)), dec = 0.01f * std::pow(400.f, p(13)), sus = p(14), rel = 0.01f * std::pow(500.f, p(15));
    const float l1r = 0.05f * std::pow(400.f, p(16)), l1d = p(17), l2r = 0.05f * std::pow(400.f, p(20)), l2d = p(21);
    const int l1s = static_cast<int>(std::lround(p(18) * 3.f)), l1dst = static_cast<int>(std::lround(p(19) * 3.f));
    const float m1 = p(22), m2 = p(23), m3 = p(24), m4 = p(25);
    const int mode = static_cast<int>(std::lround(p(26) * 2.f));
    const float glide = p(27) * 2.f, bendR = 1.f + std::round(p(29) * 11.f), tune = (p(30) - 0.5f) * 200.f, level = dbToLin(-24.f + 30.f * p(31)) * 0.25f;
    const float aAtt = 1.f / std::max(1.f, att * static_cast<float>(sr_)), cDec = 1.f - std::exp(static_cast<float>(-1.0 / (dec * sr_))), cRel = 1.f - std::exp(static_cast<float>(-1.0 / (rel * sr_)));
    const float gl = glide < 0.002f ? 1.f : 1.f - std::exp(static_cast<float>(-1.0 / (glide * sr_)));
    const float invSr = static_cast<float>(1.0 / sr_);
    const float k = std::max(0.08f, 2.f - 1.95f * clamp01(res + (m2 - 0.5f) * 0.6f)), drive = 1.f + std::max(0.f, std::min(1.f, drive0 + (m4 - 0.5f) * 0.6f)) * 8.f;
    std::fill(io, io + 2 * n, 0.f);
    const float wAm = clamp01(wA + (m3 - 0.5f)), invSqrtDrive = 1.f / std::sqrt(drive);
    const float norm = 1.f / std::sqrt(static_cast<float>(nU));
    int active = 0;
    for (int ii = 0; ii < n; ii += 8) {
      const int cnt = std::min(8, n - ii);
      const float lf1 = lfo(l1_, l1s), lf2 = sineTable().at(l2_);
      l1_ += l1r * invSr * cnt; if (l1_ >= 1.f) l1_ -= 1.f; l2_ += l2r * invSr * cnt; if (l2_ >= 1.f) l2_ -= 1.f;
      float cutMod = (m1 - 0.5f) * 6.f, pitMod = lf2 * l2d * 0.5f, waveMod = 0.f, lvMod = 1.f;
      if (l1dst == 0) cutMod += lf1 * l1d * 3.f; else if (l1dst == 1) pitMod += lf1 * l1d * 1.0f; else if (l1dst == 2) waveMod = lf1 * l1d * 0.5f; else lvMod = 1.f - l1d * 0.5f * (1.f - lf1 * 0.5f - 0.5f);
      for (auto& v : v_) {
        if (!v.on) continue;
        ++active;
        const float tPit = static_cast<float>(v.note) + bend_ * bendR + tune * 0.01f + pitMod;
        v.pit += (tPit - v.pit) * (mode == 0 ? 1.f : gl);
        const float base = 440.f * std::pow(2.f, (v.pit - 69.f) / 12.f);
        const float wa = clamp01(wAm + waveMod), wb = clamp01(wB + waveMod);
        const float fcHz = std::min(0.45f * static_cast<float>(sr_), std::max(20.f, 20.f * std::pow(1000.f, cut) * std::pow(2.f, cutMod + envAmt * v.env * 6.f)));
        const float g = std::tan(static_cast<float>(kPi) * fcHz * invSr), a1 = 1.f / (1.f + g * (g + k)), a2 = g * a1, a3 = g * a2;
        const float vg = std::pow(v.vel, 1.5f) * 1.2f;
        float iA[kU], iB[kU];
        for (int u = 0; u < nU; ++u) {
          const float cents = nU > 1 ? spread * ((2.f * u / static_cast<float>(nU - 1)) - 1.f) : 0.f;
          iA[u] = base * std::pow(2.f, cents / 1200.f) * invSr; iB[u] = base * std::pow(2.f, (cents + detune) / 1200.f) * invSr;
        }
        for (int s = 0; s < cnt; ++s) {
          // envelope
          switch (v.stage) {
            case 1: v.env += aAtt; if (v.env >= 1.f) { v.env = 1.f; v.stage = 2; } break;
            case 2: v.env += (sus - v.env) * cDec; if (std::fabs(v.env - sus) < 1e-4f) { v.env = sus; v.stage = 3; } break;
            case 3: v.env = sus; break;
            case 4: v.env += (0.f - v.env) * cRel; if (v.env < 1e-4f) { v.env = 0.f; v.on = false; } break;
            default: break;
          }
          if (!v.on) break;
          float sum[2] = {0.f, 0.f};
          for (int u = 0; u < nU; ++u) {
            const float incA = iA[u], incB = iB[u];
            float o = oscM(v.phA[u], incA, wa) + oscM(v.phB[u], incB, wb);
            v.phA[u] += incA; if (v.phA[u] >= 1.f) v.phA[u] -= 1.f; v.phB[u] += incB; if (v.phB[u] >= 1.f) v.phB[u] -= 1.f;
            const int ch = nU == 1 ? 2 : (u & 1);
            if (ch == 2) { sum[0] += o; sum[1] += o; } else sum[ch] += o;
          }
          const float subS = sub * sineTable().at(v.phS); v.phS += base * 0.5f * invSr; if (v.phS >= 1.f) v.phS -= 1.f;
          const float nz = noiseA * noise_.next();
          for (int ch = 0; ch < 2; ++ch) {
            float x = (sum[ch] * norm * 0.5f + subS + nz) ;
            x = fastTanh(x * drive) * invSqrtDrive;
            const float v3 = x - v.ic[ch][1], v1 = a1 * v.ic[ch][0] + a2 * v3, v2 = v.ic[ch][1] + a2 * v.ic[ch][0] + a3 * v3;
            v.ic[ch][0] = 2.f * v1 - v.ic[ch][0]; v.ic[ch][1] = 2.f * v2 - v.ic[ch][1];
            float y = ftype == 0 ? v2 : ftype == 1 ? x - k * v1 - v2 : ftype == 2 ? v1 : x - k * v1;
            io[2 * (ii + s) + ch] += y * v.env * vg * lvMod * level;
          }
        }
      }
    }
    for (int i = 0; i < 2 * n; ++i) io[i] = std::tanh(io[i]);
    setMeter(0, static_cast<float>(active > 0 ? 1 : 0)); activeVoices_ = std::min(kV, active / std::max(1, (n + 7) / 8));
  }
  int activeVoices() const { int c = 0; for (auto& v : v_) c += v.on; return c; }

 private:
  static float blep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.f; }
    if (t > 1.f - dt) { t = (t - 1.f) / dt; return t * t + t + t + 1.f; }
    return 0.f;
  }
  static float oscM(float ph, float dt, float w) {  // only the two neighbouring shapes are computed
    const float tr = 4.f * std::fabs(ph - 0.5f) - 1.f;
    if (w < 1.f / 3.f) { const float f = w * 3.f; return sineTable().at(ph) + (tr - sineTable().at(ph)) * f; }
    const float sw = 2.f * ph - 1.f - blep(ph, dt);
    if (w < 2.f / 3.f) { const float f = w * 3.f - 1.f; return tr + (sw - tr) * f; }
    const float sq = (ph < 0.5f ? 1.f : -1.f) + blep(ph, dt) - blep(ph + 0.5f >= 1.f ? ph - 0.5f : ph + 0.5f, dt);
    const float f = w * 3.f - 2.f; return sw + (sq - sw) * f;
  }
  static float fastTanh(float x) { if (x > 3.f) return 1.f; if (x < -3.f) return -1.f; const float x2 = x * x; return x * (27.f + x2) / (27.f + 9.f * x2); }
  static float lfo(float ph, int shape) {
    switch (shape) { case 0: return sineTable().at(ph); case 1: return 4.f * std::fabs(ph - 0.5f) - 1.f; case 2: return 2.f * ph - 1.f; default: return ph < 0.5f ? 1.f : -1.f; }
  }
  double sr_ = 48000.0; Voice v_[kV]; float l1_ = 0.f, l2_ = 0.f, bend_ = 0.f; uint32_t age_ = 0; std::vector<int> held_; Noise noise_; int activeVoices_ = 0;
};

}  // namespace aetherfx
