// Banc : le piano est-il polyphonique ET a un niveau sain a travers le FxMixer
// (chaîne d'instrument + fader + bus master), avec la vraie banque ?
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_mix_check.cpp -o /tmp/pmc
#include <cstdio>
#include <cmath>
#include <vector>
#include <algorithm>
#include "aetherfx/fx_mixer.h"

using namespace aetherfx;

static std::vector<uint8_t> slurp(const char* p) {
  FILE* f = fopen(p, "rb");
  if (!f) return {};
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  std::vector<uint8_t> v(static_cast<size_t>(n));
  if (fread(v.data(), 1, v.size(), f) != v.size()) v.clear();
  fclose(f);
  return v;
}

static double rms(const std::vector<float>& v) {
  double s = 0; for (float x : v) s += (double)x * x;
  return std::sqrt(s / std::max<size_t>(1, v.size()));
}

int main(int argc, char** argv) {
  const char* pack = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
  const int preset = argc > 2 ? atoi(argv[2]) : 1;   // 1 = Studio
  auto bytes = slurp(pack);
  if (bytes.empty()) { printf("no pack\n"); return 1; }

  FxMixer fx;
  fx.prepare(48000.0, 256);
  if (!fx.setInstrument(0, 17)) { printf("no piano\n"); return 1; }
  if (!fx.loadInstrumentBank(0, bytes.data(), bytes.size())) { printf("bank refused\n"); return 1; }

  const float studio[17] = {0.55f, 0.45f, 0.50f, 0.65f, 0.55f, 0.40f, 0.60f, 0.84f,
                            0.45f, 0.55f, 0.30f, 0.45f, 0.45f, 0.25f, 0.55f, 0.125f, 0.60f};
  for (int i = 0; i < 17; ++i) fx.setInstrumentParam(0, i, studio[i]);

  std::vector<float> io(512, 0.f);
  auto block = [&]() {
    std::fill(io.begin(), io.end(), 0.f);
    fx.processStrip(0, io.data(), 256);
    float pk = 0; for (int i = 0; i < 512; ++i) pk = std::max(pk, std::fabs(io[i]));
    return std::pair<float, double>(pk, rms(io));
  };

  printf("preset=%d pack=%s\n", preset, pack);

  // 1 note
  fx.noteOn(0, 60, 100);
  for (int b = 0; b < 12; ++b) block();
  auto r1 = block();
  printf("1 note (60)   : crete=%.3f rms=%.4f\n", r1.first, r1.second);

  // 2e note : la 1re est-elle coupee ?
  fx.noteOn(0, 67, 100);
  for (int b = 0; b < 12; ++b) block();
  auto r2 = block();
  printf("2 notes (60+67): crete=%.3f rms=%.4f  (doit monter)\n", r2.first, r2.second);

  // 3e note
  fx.noteOn(0, 64, 100);
  for (int b = 0; b < 12; ++b) block();
  auto r3 = block();
  printf("3 notes        : crete=%.3f rms=%.4f\n", r3.first, r3.second);

  // On relache 67 et 64 : la 60 doit rester.
  fx.noteOff(0, 67); fx.noteOff(0, 64);
  for (int b = 0; b < 40; ++b) block();
  auto rk = block();
  printf("60 seule      : crete=%.3f rms=%.4f  (0 => la 60 a ete tuee)\n", rk.first, rk.second);

  // Accord 6 notes tenu 2 s : saturation ?
  fx.allNotesOff(0);
  for (int b = 0; b < 40; ++b) block();
  const int chord[] = {48, 55, 60, 64, 67, 72};
  for (int n : chord) fx.noteOn(0, n, 110);
  float peak = 0; int clip = 0;
  for (int b = 0; b < 375; ++b) { auto r = block(); peak = std::max(peak, r.first); if (r.first >= 0.999f) clip++; }
  printf("accord 6 notes 2 s : crete=%.3f blocs ecretes=%d/375\n", peak, clip);
  return 0;
}
