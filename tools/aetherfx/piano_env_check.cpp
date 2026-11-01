// Enveloppe reelle d'une note de piano : crete par bloc, 1 s.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_env_check.cpp -o /tmp/pec
#include <cstdio>
#include <cmath>
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

int main(int argc, char** argv) {
  const char* pack = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
  const int note = argc > 2 ? atoi(argv[2]) : 60;
  const int vel = argc > 3 ? atoi(argv[3]) : 100;
  auto bytes = slurp(pack);
  if (bytes.empty()) { printf("no pack\n"); return 1; }

  PianoEngine e;
  e.prepare(48000.0, 256);
  if (!e.loadSampleBank(bytes.data(), bytes.size())) { printf("load failed\n"); return 1; }
  const float preset[17] = {0.55f, 0.45f, 0.50f, 0.65f, 0.55f, 0.40f, 0.60f, 0.84f,
                            0.45f, 0.55f, 0.30f, 0.45f, 0.45f, 0.25f, 0.55f, 0.0f, 0.70f};
  for (int i = 0; i < 17; ++i) e.setNorm(i, preset[i]);

  std::vector<float> io(512, 0.f);
  e.noteOn(note, vel);
  printf("pack=%s note=%d vel=%d voix=%d\n", pack, note, vel, e.activeVoices());
  printf("bloc(ms)  crete     rms\n");
  double prev = 0;
  for (int b = 0; b < 200; ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    e.process(io.data(), 256);
    float pk = 0.f; double acc = 0;
    for (int i = 0; i < 256; ++i) { pk = std::max(pk, std::fabs(io[2 * i])); acc += (double)io[2 * i] * io[2 * i]; }
    const double rms = std::sqrt(acc / 256.0);
    if (b < 12 || b % 25 == 0) printf("%6.1f    %.4f    %.4f\n", b * 256.0 * 1000.0 / 48000.0, pk, rms);
    prev = rms;
  }
  printf("fin: voix=%d\n", e.activeVoices());
  return 0;
}
