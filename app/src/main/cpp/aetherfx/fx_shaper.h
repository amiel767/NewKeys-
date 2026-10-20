// Aether instrument shapers: Piano(5) Sax(7) Strings(8) Guitar(9) Brass(12). They SHAPE the soundfont loaded on the track:
//  - computeGens(): SoundFont generator offsets to apply on the track's MIDI channel (control thread, fluid_synth_set_gen) + note-level options.
//  - ShaperFx::process(): the internal audio part (tone, body, ensemble, saturation, mutes, reverb, level).
#pragma once
#include "fx_core.h"
#include "fx_space.h"

namespace aetherfx {

enum SfGen { GEN_VIBLFOTOPITCH = 6, GEN_FILTERFC = 8, GEN_FILTERQ = 9, GEN_VIBLFOFREQ = 24, GEN_VOLENVATTACK = 34, GEN_VOLENVDECAY = 36, GEN_VOLENVRELEASE = 38 };
struct GenSet { int n = 0; int gen[12]; float val[12]; void add(int g, float v) { if (n < 12) { gen[n] = g; val[n] = v; ++n; } } };
struct PerfSet { float strumSpreadMs = 0.f; int bendSemis = 2; };  // note-level options handled by the audio engine

enum ShaperId { SH_PIANO = 5, SH_SAX = 7, SH_STRINGS = 8, SH_GUITAR = 9, SH_BRASS = 12 };
struct ShaperMap { int count, tone, body, space, level; };
inline ShaperMap shaperMap(int id) {
  switch (id) {
    case SH_PIANO: return {9, 5, 6, 7, 8};
    case SH_SAX: return {9, 5, 6, 7, 8};
    case SH_STRINGS: return {10, 6, 7, 8, 9};
    case SH_GUITAR: return {11, 7, 8, 9, 10};
    default: return {9, 5, 6, 7, 8};  // brass
  }
}
inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

inline void computeGens(int id, const float* v, GenSet& g, PerfSet& perf) {
  g.n = 0; perf = PerfSet();
  auto fc = [&](float cents) { g.add(GEN_FILTERFC, clampf(cents, -3600.f, 3600.f)); };
  switch (id) {
    case SH_PIANO:  // Felt Lid Pedal Room Width Tone Body Space Level
      fc((0.5f - v[0]) * 1800.f + (v[1] - 0.5f) * 1200.f + (v[5] - 0.5f) * 1800.f);
      g.add(GEN_VOLENVRELEASE, v[2] * 1200.f); g.add(GEN_VOLENVDECAY, v[2] * 600.f);
      break;
    case SH_SAX:  // Breath Reed Growl Vibrato Rate Tone Body Space Level
      fc((v[1] - 0.5f) * 2400.f + (v[5] - 0.5f) * 1800.f); g.add(GEN_FILTERQ, v[1] * 40.f);
      g.add(GEN_VOLENVATTACK, -(v[0] - 0.5f) * 400.f); g.add(GEN_VIBLFOTOPITCH, v[3] * 60.f);
      g.add(GEN_VIBLFOFREQ, 1200.f * std::log2((3.f + v[4] * 5.f) / 8.176f));
      break;
    case SH_STRINGS:  // Pressure Speed Motion Morph Time Color(UI only) Tone Body Space Level
      fc((v[0] - 0.5f) * 1800.f + (v[6] - 0.5f) * 1800.f);
      g.add(GEN_VOLENVATTACK, (0.5f - v[1]) * 1500.f + (v[4] - 0.5f) * 1500.f); g.add(GEN_VOLENVRELEASE, (v[4] - 0.5f) * 2000.f);
      g.add(GEN_VIBLFOTOPITCH, v[2] * 40.f);
      break;
    case SH_GUITAR:  // Body Pick Strum Mute Bend Pickup Finish(UI only) Tone Resonance Space Level
      fc((v[1] - 0.5f) * 1500.f + (v[5] - 0.5f) * 1500.f + (v[7] - 0.5f) * 1800.f - v[3] * 2000.f);
      g.add(GEN_VOLENVATTACK, -(v[1] - 0.5f) * 400.f); g.add(GEN_VOLENVDECAY, -v[3] * 1200.f); g.add(GEN_VOLENVRELEASE, -v[3] * 1800.f);
      perf.strumSpreadMs = v[2] * 60.f; perf.bendSemis = 2 + static_cast<int>(std::lround(v[4] * 10.f));
      break;
    default:  // Brass: Lips Bite Vibrato Section Mute Tone Body Space Level
      fc((v[0] - 0.5f) * 2400.f + (v[5] - 0.5f) * 1800.f);
      g.add(GEN_VOLENVATTACK, -(v[1] - 0.5f) * 500.f); g.add(GEN_VIBLFOTOPITCH, v[2] * 50.f);
      break;
  }
}

class ShaperFx : public Effect {
 public:
  explicit ShaperFx(int id) : id_(id), map_(shaperMap(id)) {}
  int paramCount() const override { return map_.count; }
  void prepare(double sr, int maxBlock) override {
    sr_ = sr; for (auto& d : dl_) d.init(static_cast<int>(0.06 * sr) + 8);
    rev_.prepare(sr, maxBlock); for (int i = 0; i < 7; ++i) rev_.setNorm(i, kRevDef[i]); reset();
  }
  void reset() override {
    for (auto& b : eq_) b.reset(); for (auto& d : dl_) d.clear(); rev_.reset(); for (auto& s : sm_) s.init = false;
    env_ = 0.f; ph_ = 0.f; for (int k = 0; k < 6; ++k) lfo_[k] = k / 6.f; cfgKey_ = -1;
  }
  void process(float* io, int n) override {
    NoDenormals nd;
    const float cf = smoothCoef(n, sr_, 0.03);
    const float tone = sm_[0].step(p(map_.tone), cf), body = sm_[1].step(p(map_.body), cf), space = sm_[2].step(p(map_.space), cf), lvl = sm_[3].step(p(map_.level), cf);
    float sat = 0.f, flutter = 0.f, breath = 0.f, wide = 1.f, ensMix = 0.f; int ensN = 0, mute = 0, gtrBody = 0;
    float roomAdd = 0.f;
    switch (id_) {
      case SH_PIANO: wide = 0.4f + 1.2f * p(4); roomAdd = 0.3f * p(3); break;
      case SH_SAX: sat = p(2); flutter = p(2) * 0.3f; breath = p(0); break;
      case SH_STRINGS: ensN = 1 + static_cast<int>(std::lround(p(3) * 5.f)); ensMix = std::min(1.f, p(3) * 1.4f); break;
      case SH_GUITAR: gtrBody = static_cast<int>(std::lround(p(0) * 3.f)); break;
      default: sat = p(1) * 0.8f; ensN = 1 + static_cast<int>(std::lround(p(3) * 4.f)); ensMix = std::min(1.f, p(3) * 1.2f); mute = static_cast<int>(std::lround(p(4) * 4.f)); break;
    }
    const int key = gtrBody * 8 + mute;
    float tiltDb = (tone - 0.5f) * 16.f;
    if (id_ == SH_GUITAR) tiltDb += (p(5) - 0.5f) * 8.f;
    eq_[0].setHighShelf(sr_, 3000, tiltDb);
    const float bodyHz = id_ == SH_PIANO ? 250.f : id_ == SH_SAX ? 350.f : id_ == SH_STRINGS ? 300.f : id_ == SH_GUITAR ? 100.f : 500.f;
    eq_[1].setPeaking(sr_, bodyHz, 0.9, (body - 0.5f) * 12.f);
    if (key != cfgKey_ || id_ == SH_GUITAR || id_ == SH_BRASS) {
      cfgKey_ = key;
      if (id_ == SH_GUITAR) {
        if (gtrBody == 0) { eq_[2].setPeaking(sr_, 100, 1.0, 3.0); eq_[3].setPeaking(sr_, 3000, 1.0, 2.0); }
        else if (gtrBody == 1) { eq_[2].setLowpass(sr_, 4500, 0.7); eq_[3].setPeaking(sr_, 200, 1.0, 2.0); }
        else if (gtrBody == 2) { eq_[2].setHighpass(sr_, 80, 0.7); eq_[3].setPeaking(sr_, 2500, 1.0, 3.0); }
        else { eq_[2].setLowpass(sr_, 7000, 0.7); eq_[3].setPeaking(sr_, 400, 1.0, 3.0); }
      } else if (id_ == SH_BRASS) {
        switch (mute) {
          case 1: eq_[2].setHighpass(sr_, 500, 0.7); eq_[3].setLowpass(sr_, 6500, 0.7); break;
          case 2: eq_[2].setLowpass(sr_, 2400, 0.7); eq_[3].setPeaking(sr_, 800, 1.0, 0.0); break;
          case 3: eq_[2].setHighpass(sr_, 450, 0.7); eq_[3].setPeaking(sr_, 1400, 2.0, 9.0); break;
          case 4: eq_[2].setLowpass(sr_, 1700, 0.7); eq_[3].setPeaking(sr_, 700, 1.0, 4.0); break;
          default: eq_[2].setPeaking(sr_, 1000, 1.0, 0.0); eq_[3].setPeaking(sr_, 1000, 1.0, 0.0); break;
        }
      }
    }
    const bool useCfg = id_ == SH_GUITAR || id_ == SH_BRASS;
    rev_.setNorm(5, std::min(1.f, 0.5f * space + roomAdd));
    const float outG = dbToLin(-24.f + 30.f * lvl), aE = 1.f - std::exp(static_cast<float>(-1.0 / (0.02 * sr_)));
    static const float base[6] = {0.012f, 0.016f, 0.020f, 0.024f, 0.028f, 0.032f}, rate[6] = {0.27f, 0.33f, 0.41f, 0.49f, 0.57f, 0.65f};
    const float g = 1.f + sat * 5.f;
    for (int i = 0; i < n; ++i) {
      float xl = io[2 * i], xr = io[2 * i + 1];
      const float mono = 0.5f * (xl + xr);
      env_ += aE * (std::fabs(mono) - env_);
      const float fl = flutter > 0.f ? 1.f - flutter * (0.5f + 0.5f * sineTable().at(ph_)) : 1.f; ph_ += static_cast<float>(7.0 / sr_); if (ph_ >= 1.f) ph_ -= 1.f;
      const float nz = breath > 0.f ? noise_.next() * env_ * breath * 0.5f : 0.f;
      for (int ch = 0; ch < 2; ++ch) {
        float y = (ch ? xr : xl) + nz;
        if (sat > 0.f) { const float s = std::tanh(g * y) / g * (1.f + 0.6f * sat); y = y * (1.f - 0.5f * sat) + s * 0.5f * sat * 2.f; }
        y = eq_[0].process(ch, y); y = eq_[1].process(ch, y);
        if (useCfg) { y = eq_[2].process(ch, y); y = eq_[3].process(ch, y); }
        if (ensN > 0) {
          dl_[ch].write(y); float ens = 0.f;
          for (int k = 0; k < ensN && k < 6; ++k) { const float d = (base[k] + 0.0010f * sineTable().at(lfo_[k])) * static_cast<float>(sr_); ens += dl_[ch].read(std::max(d, 2.f)) * (((k + ch) & 1) ? 0.9f : 0.4f); }
          y = y * (1.f - 0.4f * ensMix) + ens * (1.2f / static_cast<float>(ensN)) * ensMix;
        }
        io[2 * i + ch] = y * fl;
      }
      if (id_ == SH_PIANO && wide != 1.f) { const float m = 0.5f * (io[2 * i] + io[2 * i + 1]), s = 0.5f * (io[2 * i] - io[2 * i + 1]) * wide; io[2 * i] = m + s; io[2 * i + 1] = m - s; }
      for (int k = 0; k < 6; ++k) { lfo_[k] += static_cast<float>(rate[k] / sr_); if (lfo_[k] >= 1.f) lfo_[k] -= 1.f; }
    }
    if (space > 0.01f || roomAdd > 0.01f) rev_.process(io, n);
    for (int i = 0; i < 2 * n; ++i) io[i] = std::tanh(io[i] * outG * 0.9f) / 0.9f;
  }

 private:
  static constexpr float kRevDef[7] = {0.55f, 0.45f, 0.12f, 0.45f, 0.8f, 0.3f, 0.5f};
  int id_; ShaperMap map_; double sr_ = 48000.0;
  Biquad eq_[4]; DelayLine dl_[2]; SpaceReverb rev_; Noise noise_; BlockSmoother sm_[4];
  float env_ = 0.f, ph_ = 0.f, lfo_[6] = {0, .17f, .33f, .5f, .67f, .83f}; int cfgKey_ = -1;
};

}  // namespace aetherfx
