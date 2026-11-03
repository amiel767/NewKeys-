// Balayage FIN de la courbe de velocite (1..127) sur un pack : detecte les SAUTS.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_velocity_sweep.cpp -o /tmp/sweep
// Le detail par velocite va dans un fichier ; la sortie standard ne porte que le
// resume (regle economique du depot).  `crete` = niveau d'attaque, ce que l'oreille
// entend comme « plus fort » ; `rms` sert a detecter une inversion de niveau.
#include <cstdio>
#include <cmath>
#include <cstdlib>
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
  const int note = argc > 2 ? atoi(argv[2]) : 60;
  const char* out = argc > 3 ? argv[3] : "/tmp/sweep.txt";
  auto bytes = slurp(path);
  if (bytes.empty()) { printf("no pack\n"); return 1; }
  PianoEngine e;
  e.prepare(48000.0, 256);
  if (!e.loadSampleBank(bytes.data(), bytes.size())) { printf("load failed\n"); return 1; }
  for (int i = 0; i < e.paramCount(); ++i) e.setNorm(i, 0.5f);

  FILE* f = fopen(out, "w");
  std::vector<float> io(512, 0.f);
  double firstPeak = 0, prevPeak = -1, prevRms = -1;
  double maxUp = 0, maxDown = 0;
  int invRms = 0, invPeak = 0;
  for (int vel = 1; vel <= 127; ++vel) {
    // Laisser la note precedente mourir : sans cela sa queue de relachement
    // contamine le debut de la mesure et fausse la courbe.
    for (int b = 0; b < 120; ++b) e.process(io.data(), 256);
    e.noteOn(note, vel);
    double s = 0; float pk = 0.f;
    for (int b = 0; b < 16; ++b) {
      std::fill(io.begin(), io.end(), 0.f);
      e.process(io.data(), 256);
      for (int i = 0; i < 512; ++i) { pk = std::max(pk, std::fabs(io[i])); s += (double)io[i] * io[i]; }
    }
    e.allNotesOff();
    const double rms = std::sqrt(s / (16.0 * 512.0));
    if (firstPeak == 0) firstPeak = pk;
    if (f) fprintf(f, "%3d peak=%.5f rms=%.5f dBpeak=%+.2f dBrms=%+.2f\n", vel, pk, rms,
                   20 * std::log10(std::max<double>(pk, 1e-9) / firstPeak),
                   20 * std::log10(std::max(rms, 1e-9) / firstPeak));
    if (prevPeak >= 0) {
      const double dp = 20 * std::log10(std::max<double>(pk, 1e-9) / std::max(prevPeak, 1e-9));
      if (dp > maxUp) maxUp = dp;
      if (-dp > maxDown) maxDown = -dp;
      if (pk < prevPeak - 1e-6) ++invPeak;
    }
    if (prevRms >= 0 && rms < prevRms - 1e-6) ++invRms;
    prevPeak = pk; prevRms = rms;
  }
  if (f) fclose(f);
  printf("%s note=%d  invPeak=%d invRms=%d  saut_max_haut=%.2f dB  saut_max_bas=%.2f dB  detail=%s\n",
         path, note, invPeak, invRms, maxUp, maxDown, out);
  return (invPeak > 0) ? 2 : 0;
}
