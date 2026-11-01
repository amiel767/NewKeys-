// Banc : le moteur piano est-il polyphonique, et a quel niveau sort-il ?
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_poly_check.cpp -o /tmp/ppc
//
// Le pack reel est lu depuis app/src/main/assets/pianopack/piano.bin.
// Mesures : nombre de voix actives apres un accord, crete/RMS, et si la
// premiere note reste audible quand une seconde arrive (mode legato = non).
#include <cstdio>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>

#include "aetherfx/fx_piano.h"

using namespace aetherfx;

static std::vector<uint8_t> slurp(const char* p) {
  FILE* f = fopen(p, "rb");
  if (!f) { fprintf(stderr, "cannot open %s\n", p); return {}; }
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  std::vector<uint8_t> v(static_cast<size_t>(n));
  if (fread(v.data(), 1, v.size(), f) != v.size()) v.clear();
  fclose(f);
  return v;
}

int main() {
  auto bytes = slurp("app/src/main/assets/pianopack/piano.bin");
  if (bytes.empty()) return 1;
  printf("pack %zu octets\n", bytes.size());

  PianoEngine e;
  e.prepare(48000.0, 256);
  printf("loadSampleBank -> %d\n", (int)e.loadSampleBank(bytes.data(), bytes.size()));
  printf("regions %zu  predecodees %zu\n", e.bankRegions(), e.preloadedRegions());

  // Reglages d'un preset acoustique typique (Studio), valeurs normalisees.
  const float preset[17] = {0.55f, 0.45f, 0.50f, 0.65f, 0.55f, 0.40f, 0.60f, 0.84f,
                            0.45f, 0.55f, 0.30f, 0.45f, 0.45f, 0.25f, 0.55f, 0.0f, 0.70f};
  for (int i = 0; i < 17; ++i) e.setNorm(i, preset[i]);

  std::vector<float> io(512, 0.f);
  auto block = [&](int frames) {
    std::fill(io.begin(), io.end(), 0.f);
    e.process(io.data(), frames);
    float pk = 0.f; double acc = 0;
    for (int i = 0; i < frames; ++i) { pk = std::max(pk, std::fabs(io[2 * i])); acc += (double)io[2 * i] * io[2 * i]; }
    return std::pair<float, double>(pk, std::sqrt(acc / frames));
  };

  // 1. Une note seule.
  e.allNotesOff();
  e.noteOn(60, 100);
  auto r1 = block(256);
  printf("\nUNE NOTE 60 : voix=%d crete=%.3f rms=%.4f\n", e.activeVoices(), r1.first, r1.second);

  // 2. Accord de trois notes, la premiere tenue.
  e.noteOn(64, 100);
  e.noteOn(67, 100);
  auto r3 = block(256);
  printf("ACCORD 60+64+67 (60 tenu) : voix=%d crete=%.3f rms=%.4f\n", e.activeVoices(), r3.first, r3.second);

  // 3. La note 60 est-elle TOUJOURS la ?  On l'isole : on coupe les deux autres
  //    et on regarde si quelque chose reste (mode legato = la 60 a ete tuee).
  e.noteOff(64); e.noteOff(67);
  for (int b = 0; b < 40; ++b) block(256);
  auto rKeep = block(256);
  printf("60 SEULE apres 64/67 relachees : voix=%d rms=%.4f\n", e.activeVoices(), rKeep.second);

  // 4. Niveau max sur un accord tenu 2 s (saturation ?).
  e.allNotesOff();
  const int chord[] = {48, 55, 60, 64, 67, 72};
  for (int n : chord) e.noteOn(n, 110);
  float peak = 0.f; int clipped = 0;
  for (int b = 0; b < 375; ++b) { auto r = block(256); peak = std::max(peak, r.first); if (r.first >= 0.999f) clipped++; }
  printf("\nACCORD 6 NOTES 2 s : voix=%d crete=%.3f blocs ecretes=%d/375\n", e.activeVoices(), peak, clipped);

  // 5. Polyphonie reelle : 12 notes, combien de voix actives ?
  e.allNotesOff();
  for (int i = 0; i < 12; ++i) e.noteOn(48 + i, 100);
  block(256);
  printf("12 NOTES : voix actives=%d\n", e.activeVoices());
  return 0;
}
