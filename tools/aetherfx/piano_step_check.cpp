// Mesure ISOLEE d'une seule velocite : un moteur neuf par mesure, donc aucune queue
// de reverberation d'une note precedente ne contamine le resultat.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_step_check.cpp -o /tmp/step
// Sert a trancher : un « saut » vu dans un balayage continu est-il reel, ou un
// artefact de la queue de reverb qui chevauche la mesure suivante ?
#include <cstdio>
#include <cmath>
#include <cstdlib>
#include <vector>
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

static double measure(const std::vector<uint8_t>& bytes, int note, int vel, double sec) {
  PianoEngine e;
  e.prepare(48000.0, 256);
  e.loadSampleBank(bytes.data(), bytes.size());
  for (int i = 0; i < e.paramCount(); ++i) e.setNorm(i, 0.5f);
  std::vector<float> io(512, 0.f);
  e.noteOn(note, vel);
  double s = 0; long frames = 0;
  const long want = static_cast<long>(sec * 48000.0 / 256.0);
  for (long b = 0; b < want; ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    e.process(io.data(), 256);
    for (int i = 0; i < 512; ++i) s += (double)io[i] * io[i];
    frames += 512;
  }
  return std::sqrt(s / std::max<long>(1, frames));
}

int main(int argc, char** argv) {
  const char* path = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/ep_ballad.bin";
  const int note = argc > 2 ? atoi(argv[2]) : 60;
  const double sec = argc > 3 ? atof(argv[3]) : 0.5;
  auto bytes = slurp(path);
  if (bytes.empty()) { printf("no pack\n"); return 1; }
  printf("%s note=%d fenetre=%.2fs (moteur neuf par mesure)\n", path, note, sec);
  double ref = 0;
  for (int vel = 28; vel <= 127; vel += 1) {
    const double r = measure(bytes, note, vel, sec);
    if (ref == 0) ref = r;
    printf("vel %3d rms=%.5f  %+6.2f dB\n", vel, r, 20 * std::log10(r / ref));
  }
  return 0;
}
