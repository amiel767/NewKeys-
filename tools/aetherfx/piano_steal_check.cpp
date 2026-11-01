// T1 : (a) quelle voix est volee quand le piano sature ses 12 voix,
//      (b) quel crete sort quand beaucoup de notes sont tenues.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_steal_check.cpp -o /tmp/psc
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

int main() {
  auto bytes = slurp("app/src/main/assets/pianopack/piano.bin");
  if (bytes.empty()) return 1;

  FxMixer fx;
  fx.prepare(48000.0, 256);
  fx.setInstrument(0, 17);
  fx.loadInstrumentBank(0, bytes.data(), bytes.size());
  const float studio[17] = {0.55f, 0.45f, 0.50f, 0.65f, 0.55f, 0.40f, 0.60f, 0.84f,
                            0.45f, 0.55f, 0.30f, 0.45f, 0.45f, 0.25f, 0.55f, 0.125f, 0.60f};
  for (int i = 0; i < 17; ++i) fx.setInstrumentParam(0, i, studio[i]);

  std::vector<float> io(512, 0.f);
  auto block = [&]() {
    std::fill(io.begin(), io.end(), 0.f);
    fx.processStrip(0, io.data(), 256);
    float pk = 0; for (int i = 0; i < 512; ++i) pk = std::max(pk, std::fabs(io[i]));
    return pk;
  };

  // PianoEngine n'expose pas les voix par le FxMixer ; on passe par un moteur nu.
  printf("== FxMixer : crete selon le nombre de notes tenues ==\n");
  for (int count : {1, 3, 6, 9, 12, 16, 20, 24}) {
    fx.allNotesOff(0);
    for (int b = 0; b < 30; ++b) block();
    for (int i = 0; i < count; ++i) fx.noteOn(0, 40 + i * 3, 110);
    float peak = 0; int clip = 0;
    for (int b = 0; b < 60; ++b) { const float p = block(); peak = std::max(peak, p); if (p >= 0.999f) clip++; }
    printf("%2d notes tenues : crete=%.3f  blocs ecretes=%d/60\n", count, peak, clip);
  }
  return 0;
}
