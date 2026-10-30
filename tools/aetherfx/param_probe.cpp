// Sonde : le moteur reagit-il aux parametres ? Joue C4 en faisant varier UN parametre
// a la fois et imprime RMS + centroide. Si la ligne ne bouge pas, le parametre est mort.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/param_probe.cpp -o /tmp/pp
#include <cstdio>
#include <cmath>
#include <cstring>
#include <vector>
#include <string>
#include <algorithm>

#include "aetherfx/fx_piano.h"

using namespace aetherfx;

static std::vector<uint8_t> readFile(const char* p) {
  FILE* f = fopen(p, "rb");
  if (!f) { fprintf(stderr, "pack illisible\n"); exit(1); }
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  std::vector<uint8_t> b((size_t)n);
  if (fread(b.data(), 1, b.size(), f) != b.size()) exit(1);
  fclose(f);
  return b;
}

static void stats(PianoEngine& e, float sr, float& rms, float& cent) {
  const int block = 512;
  std::vector<float> io((size_t)block * 2), acc;
  for (int b = 0; b < (int)(1.2f * sr / block); ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    e.process(io.data(), block);
    acc.insert(acc.end(), io.begin(), io.end());
  }
  std::vector<float> m;
  for (size_t i = 0; i < acc.size(); i += 2) m.push_back((acc[i] + acc[i + 1]) * 0.5f);
  double s = 0; for (float v : m) s += (double)v * v;
  rms = (float)std::sqrt(s / std::max<size_t>(1, m.size()));
  // centroide sur la fenetre 0,3-0,8 s
  size_t a = (size_t)(0.3 * sr), L = 16384;
  std::vector<double> re(L), im(L);
  for (size_t i = 0; i < L && a + i < m.size(); ++i) {
    double w = 0.5 - 0.5 * std::cos(2 * M_PI * i / (L - 1));
    re[i] = m[a + i] * w; im[i] = 0;
  }
  double num = 0, den = 0;
  for (int k = 1; k < L / 2; ++k) {
    double sr2 = 0, si2 = 0;
    for (int n2 = 0; n2 < L; ++n2) {
      double ph = -2 * M_PI * k * n2 / L;
      sr2 += re[n2] * std::cos(ph); si2 += re[n2] * std::sin(ph);
    }
    double p = sr2 * sr2 + si2 * si2, f = k * sr / L;
    num += p * f; den += p;
  }
  cent = (float)(num / std::max(1e-12, den));
}

int main(int argc, char** argv) {
  std::vector<uint8_t> bytes = readFile(argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin");
  const char* names[12] = {"HAMMER", "TONE", "RESONANCE", "LID", "MIC", "SPACE",
                           "WIDTH", "LEVEL", "RELEASE", "VELOCITY", "PEDAL", "MODEL"};
  printf("%-11s %-18s %-18s %s\n", "parametre", "valeur 0", "valeur 1", "ecart");
  for (int i = 0; i < 12; ++i) {
    float r0, c0, r1, c1;
    for (int v = 0; v < 2; ++v) {
      PianoEngine e; e.prepare(44100.f, 512);
      if (!e.loadSampleBank(bytes.data(), bytes.size())) return 1;
      for (int k = 0; k < 12; ++k) e.setNorm(k, 0.5f);
      e.setNorm(i, v ? 1.f : 0.f);
      e.reset();
      e.noteOn(60, 100);
      float r, c; stats(e, 44100.f, r, c);
      if (v == 0) { r0 = r; c0 = c; } else { r1 = r; c1 = c; }
    }
    printf("%-11s rms %.4f c %5.0f   rms %.4f c %5.0f   dRMS %+.1f%% dC %+.1f%%\n",
           names[i], r0, c0, r1, c1, 100 * (r1 - r0) / std::max(1e-9f, r0),
           100 * (c1 - c0) / std::max(1e-9f, c0));
  }
  return 0;
}
