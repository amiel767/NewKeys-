// T1 : l'etage "ampli" sature-t-il par defaut ? Mesure des produits de distorsion
// d'une note seule quand DRIVE (param 12) varie.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_drive_check.cpp -o /tmp/pdc
#include <cstdio>
#include <cmath>
#include <vector>
#include <complex>
#include <algorithm>
#include "aetherfx/fx_piano.h"

using namespace aetherfx;

static std::vector<uint8_t> slurp(const char* p) {
  FILE* f = fopen(p, "rb"); if (!f) return {};
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  std::vector<uint8_t> v((size_t)n);
  if (fread(v.data(), 1, v.size(), f) != v.size()) v.clear();
  fclose(f); return v;
}

// Energie a la frequence f sur la fenetre (Goertzel complexe).
static double mag(const std::vector<float>& x, double f, double sr) {
  std::complex<double> acc{0, 0};
  const double w = 2.0 * M_PI * f / sr;
  for (size_t i = 0; i < x.size(); ++i) acc += (double)x[i] * std::exp(std::complex<double>(0.0, -w * (double)i));
  return std::abs(acc) / x.size();
}

int main() {
  auto bytes = slurp("app/src/main/assets/pianopack/piano.bin");
  if (bytes.empty()) return 1;
  PianoEngine eng;
  eng.prepare(48000.0, 256);
  eng.loadSampleBank(bytes.data(), bytes.size());
  const float studio[17] = {0.55f, 0.45f, 0.50f, 0.65f, 0.55f, 0.40f, 0.60f, 0.84f,
                            0.45f, 0.55f, 0.30f, 0.45f, 0.45f, 0.25f, 0.55f, 0.125f, 0.60f};
  for (int i = 0; i < 17; ++i) eng.setNorm(i, studio[i]);
  eng.setNorm(5, 0.f);   // SPACE 0 : la reverb ne doit pas polluer la mesure

  std::vector<float> io(512, 0.f);
  const double f0 = 440.0 * std::pow(2.0, (60 - 69) / 12.0);

  printf("note 60 a vel 100, DRIVE (12) balaye.  Reverb coupee.\n");
  printf("DRIVE  gain-ampli  crete   rms    crete/rms  H2/H1   H3/H1   disto(hors 6 1res)\n");
  for (float drive : {0.0f, 0.25f, 0.45f, 0.70f, 0.90f, 1.0f}) {
    eng.setNorm(12, drive);
    eng.allNotesOff();
    for (int b = 0; b < 30; ++b) { std::fill(io.begin(), io.end(), 0.f); eng.process(io.data(), 256); }
    eng.noteOn(60, 100);
    std::vector<float> cap;
    for (int b = 0; b < 40; ++b) {
      std::fill(io.begin(), io.end(), 0.f);
      eng.process(io.data(), 256);
      for (int i = 0; i < 256; ++i) cap.push_back(0.5f * (io[2 * i] + io[2 * i + 1]));
    }
    double pk = 0, s = 0;
    for (float v : cap) { pk = std::max(pk, (double)std::fabs(v)); s += (double)v * v; }
    const double rms = std::sqrt(s / cap.size());
    const double h1 = mag(cap, f0, 48000.0);
    const double h2 = mag(cap, f0 * 2, 48000.0);
    const double h3 = mag(cap, f0 * 3, 48000.0);
    double hi = 0;
    for (int h = 7; h <= 30; ++h) hi += mag(cap, f0 * h, 48000.0);
    const int key = (int)std::lround(drive * 4.f);
    printf("%.2f   %5.2f      %.4f %.4f  %6.2f    %6.4f  %6.4f  %8.4f\n",
           drive, 1.f + key * 1.1f, pk, rms, pk / std::max(1e-9, rms), h2 / std::max(1e-9, h1),
           h3 / std::max(1e-9, h1), hi / std::max(1e-9, h1));
  }
  return 0;
}
