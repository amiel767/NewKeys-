// T1 : voix reellement actives et cout CPU selon le nombre de notes tenues.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_voice_check.cpp -o /tmp/pvc
#include <cstdio>
#include <cmath>
#include <chrono>
#include <vector>
#include <algorithm>
#include "aetherfx/fx_piano.h"

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
  if (bytes.empty()) { printf("no pack\n"); return 1; }

  PianoEngine eng;
  eng.prepare(48000.0, 256);
  eng.loadSampleBank(bytes.data(), bytes.size());
  printf("regions=%zu preload=%zu kVoices=%d\n", eng.bankRegions(), eng.preloadedRegions(), PianoEngine::kVoices);
  const float studio[17] = {0.55f, 0.45f, 0.50f, 0.65f, 0.55f, 0.40f, 0.60f, 0.84f,
                            0.45f, 0.55f, 0.30f, 0.45f, 0.45f, 0.25f, 0.55f, 0.125f, 0.60f};
  for (int i = 0; i < 17; ++i) eng.setNorm(i, studio[i]);

  std::vector<float> io(512, 0.f);
  auto block = [&]() {
    std::fill(io.begin(), io.end(), 0.f);
    eng.process(io.data(), 256);
    float pk = 0; for (int i = 0; i < 512; ++i) pk = std::max(pk, std::fabs(io[i]));
    return pk;
  };

  printf("\n== voix actives selon les notes tenues ==\n");
  for (int count : {1, 3, 6, 9, 12, 16, 20, 24}) {
    eng.allNotesOff();
    for (int b = 0; b < 20; ++b) block();
    for (int i = 0; i < count; ++i) eng.noteOn(40 + i * 3, 110);
    for (int b = 0; b < 20; ++b) block();
    printf("%2d notes jouees -> %2d voix actives", count, eng.activeVoices());
    if (eng.activeVoices() < count) printf("   <-- %d voix VOLee(s)", count - eng.activeVoices());
    printf("\n");
  }

  printf("\n== reponse au niveau : 1 note, crete en fonction de la velocite ==\n");
  printf("(lineaire = la crete doit suivre la velocite ; plateau = saturation)\n");
  for (int cfg = 0; cfg < 3; ++cfg) {
    // cfg 0 = preset ; 1 = sans marteau ; 2 = sans marteau ni touche ni resonateurs
    eng.setNorm(0, cfg == 0 ? 0.55f : 0.f);
    eng.setNorm(16, cfg == 2 ? 0.f : 0.60f);
    eng.setNorm(2, cfg == 2 ? 0.f : 0.50f);
    float ref = 0.f;
    printf("-- cfg %d (%s) --\n", cfg,
           cfg == 0 ? "preset studio" : cfg == 1 ? "marteau coupe" : "marteau+touche+resonateurs coupes");
    for (int vel : {20, 40, 60, 80, 100, 127}) {
      eng.allNotesOff();
      for (int b = 0; b < 20; ++b) block();
      eng.noteOn(60, vel);
      float peak = 0;
      for (int b = 0; b < 40; ++b) peak = std::max(peak, block());
      if (vel == 20) ref = peak;
      printf("  vel %3d : crete=%.4f   x%.2f (lineaire x%.2f)\n",
             vel, peak, peak / std::max(1e-6f, ref), vel / 20.f);
    }
  }

  printf("\n== linearite du pipeline : LEVEL balaye a velocite fixe (110) ==\n");
  eng.setNorm(0, 0.55f); eng.setNorm(16, 0.60f); eng.setNorm(2, 0.50f);
  float lref = 0.f;
  for (int pct : {10, 30, 50, 70, 100}) {
    eng.setNorm(7, pct / 100.f);
    eng.allNotesOff();
    for (int b = 0; b < 20; ++b) block();
    eng.noteOn(60, 110);
    float peak = 0;
    for (int b = 0; b < 40; ++b) peak = std::max(peak, block());
    if (pct == 10) lref = peak;
    printf("  LEVEL %3d%% : crete=%.4f   x%.2f (lineaire x%.2f)\n",
           pct, peak, peak / std::max(1e-6f, lref), pct / 10.f);
  }
  eng.setNorm(7, 0.84f);

  printf("\n== cout CPU : 1 s de rendu avec N voix tenues ==\n");
  for (int count : {6, 12, 24, 32}) {
    eng.allNotesOff();
    for (int b = 0; b < 20; ++b) block();
    for (int i = 0; i < count; ++i) eng.noteOn(36 + i * 2, 100);
    for (int b = 0; b < 20; ++b) block();
    const int blocks = 48000 / 256;
    auto t0 = std::chrono::steady_clock::now();
    for (int b = 0; b < blocks; ++b) block();
    auto t1 = std::chrono::steady_clock::now();
    const double ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
    printf("%2d voix : %6.1f ms de CPU pour 1000 ms d'audio (%.1f %%)\n", count, ms, ms / 10.0);
  }
  return 0;
}
