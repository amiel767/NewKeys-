// Track Settings bend range -> Æther sources. Measures the ACTUAL peak frequency of the
// rendered voice (fine Goertzel grid) with the wheel centred and fully up, then compares
// the ratio to 2^(semis/12). Robust to the per-voice drift and vibrato: no pitch tracker,
// no assumption about the unbent frequency — only the peak of the spectrum in a wide band.
#include "../../app/src/main/cpp/aetherfx/fx_leads.h"
#include "../../app/src/main/cpp/aetherfx/fx_organ.h"
#include "../../app/src/main/cpp/aetherfx/fx_synth.h"
#include "../../app/src/main/cpp/aetherfx/fx_sax.h"
#include <cstdio>
#include <cmath>
#include <vector>
#include <algorithm>
using namespace aetherfx;
static const int SR = 48000, BLK = 256, WIN = 8192;

static double goertzel(const std::vector<float>& x, double f) {
  const double w = 2.0 * M_PI * f / SR, coeff = 2.0 * std::cos(w);
  double s1 = 0, s2 = 0;
  for (float v : x) { const double s0 = v + coeff * s1 - s2; s2 = s1; s1 = s0; }
  return std::sqrt(s1 * s1 + s2 * s2 - coeff * s1 * s2);
}
static std::vector<float> render(SourceFx& s, int note, float bend, int semis) {
  s.setPitchBendRange(semis);
  s.noteOn(note, 100);
  s.pitchBend(bend);
  std::vector<float> buf(BLK * 2), x;
  x.reserve(WIN);
  for (int i = 0; i < 60; ++i) { std::fill(buf.begin(), buf.end(), 0.f); s.process(buf.data(), BLK); }
  while (static_cast<int>(x.size()) < WIN) {
    std::fill(buf.begin(), buf.end(), 0.f); s.process(buf.data(), BLK);
    for (int j = 0; j < BLK && static_cast<int>(x.size()) < WIN; ++j) x.push_back(buf[j * 2]);
  }
  return x;
}
// Peak of the spectrum in [fLo, fHi], 0.1 Hz grid.
static double peakHz(const std::vector<float>& x, double fLo, double fHi) {
  double best = -1, bf = fLo;
  for (double f = fLo; f <= fHi; f += 0.1) { const double m = goertzel(x, f); if (m > best) { best = m; bf = f; } }
  return bf;
}
static void check(const char* name, SourceFx& s, int note) {
  const double f0 = 440.0 * std::pow(2.0, (note - 69) / 12.0);
  // The unbent voice can sit up to a semitone off (Tune, drift): search +-1.5 semitones.
  const double base = peakHz(render(s, note, 0.f, 2), f0 * 0.91, f0 * 1.10);
  for (int semis : {1, 2, 12}) {
    const double expect = base * std::pow(2.0, semis / 12.0);
    const double got = peakHz(render(s, note, 1.f, semis), expect * 0.93, expect * 1.07);
    const double err = 100.0 * std::fabs(got - expect) / expect;
    std::printf("%-6s semis=%2d  base=%7.2fHz  expect=%8.2fHz  got=%8.2fHz  err=%5.2f%%  %s\n",
                name, semis, base, expect, got, err, err < 1.5 ? "PASS" : "FAIL");
  }
}
static void organPreset(OrganEngine& s) { for (int i = 0; i < 31; ++i) s.setNorm(i, 0.f); s.setNorm(2, 1.f); s.setNorm(30, 0.8f); }
// The real "Pure Sine" preset of AePresets.leads. A hand-made one-parameter voice is
// not what the user hears and its spectrum confuses the peak finder (5 layers here).
static const float kLeadsPureSine[32] = {0.8f,0,0,0,0,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.3f,0.02f,0,0.3f,0.8f,0.25f,0,0.8f,0.1f,0.2f,0.4f,0.3f,0,0.5f,0.15f,0,0.5f,0.5f,0.1f,0.15f,0.75f};
static void leadsPreset(LeadsEngine& s) { for (int i = 0; i < 32; ++i) s.setNorm(i, kLeadsPureSine[i]); }
static void synthPreset(SynthEngine& s) { for (int i = 0; i < 32; ++i) s.setNorm(i, 0.f); s.setNorm(0, 1.f); s.setNorm(14, 1.f); s.setNorm(31, 0.75f); }
static void saxPreset(SaxEngine& s) { for (int i = 0; i < 21; ++i) s.setNorm(i, 0.f); s.setNorm(12, 0.f); s.setNorm(20, 0.75f); }

int main() {
  { LeadsEngine s; s.prepare(SR, BLK); leadsPreset(s); check("Leads", s, 60); }
  { SynthEngine s; s.prepare(SR, BLK); synthPreset(s); check("Synth", s, 60); }
  { OrganEngine s; s.prepare(SR, BLK); organPreset(s); check("Organ", s, 60); }
  { SaxEngine s;   s.prepare(SR, BLK); saxPreset(s);   check("Sax", s, 60); }
  return 0;
}
