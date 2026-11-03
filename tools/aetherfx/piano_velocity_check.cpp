// Verifie que la sortie du moteur piano est MONOTONE en velocite, sur chaque pack.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_velocity_check.cpp -o /tmp/pv
//
// Le bug rapporte (« les touches repondent a des velocites bizarres, desordre
// total ») venait d'une courbe de velocite NON monotone : le moteur melangeait
// `v` et `v^2` selon un « deficit » mesure, donc certaines touches sortaient plus
// faibles quand on frappait plus fort.  Ce banc mesure la crete et le RMS pour
// chaque velocite et signale toute INVERSION.
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
  const char* path = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
  auto bytes = slurp(path);
  if (bytes.empty()) { printf("no pack %s\n", path); return 1; }

  PianoEngine e;
  e.prepare(48000.0, 256);
  if (!e.loadSampleBank(bytes.data(), bytes.size())) { printf("load failed\n"); return 1; }
  // Preset neutre : VEL a 0,5 = courbe lineaire, tout le reste au milieu.
  for (int i = 0; i < e.paramCount(); ++i) e.setNorm(i, 0.5f);

  std::vector<float> io(512, 0.f);
  printf("pack=%s regions=%zu preload=%zu\n", path, e.bankRegions(), e.preloadedRegions());
  printf(" vel   crete      rms   dB vs vel20\n");
  double prev = -1.0;
  int inversions = 0;
  double first = 0.0;
  for (int vel : {20, 40, 60, 80, 100, 127}) {
    std::fill(io.begin(), io.end(), 0.f);
    e.noteOn(60, vel);
    double s = 0; float pk = 0.f;
    for (int b = 0; b < 12; ++b) {                    // ~128 ms
      std::fill(io.begin(), io.end(), 0.f);
      e.process(io.data(), 256);
      for (int i = 0; i < 512; ++i) { pk = std::max(pk, std::fabs(io[i])); s += (double)io[i] * io[i]; }
    }
    e.allNotesOff();
    const double rms = std::sqrt(s / (12.0 * 512.0));
    if (first == 0.0) first = rms;
    const double db = 20.0 * std::log10(std::max(rms, 1e-9) / std::max(first, 1e-9));
    printf("%4d  %.4f  %.5f  %+6.1f\n", vel, pk, rms, db);
    if (prev >= 0.0 && rms < prev - 1e-6) { printf("  ^ INVERSION (plus faible que la velocite precedente)\n"); ++inversions; }
    prev = rms;
  }
  printf("inversions=%d\n", inversions);
  return inversions == 0 ? 0 : 2;
}
