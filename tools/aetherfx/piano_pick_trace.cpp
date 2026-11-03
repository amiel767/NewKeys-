// Trace la region choisie et le gain applique pour chaque velocite, sur une note.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_pick_trace.cpp -o /tmp/trace
// Sert a comprendre une INVERSION de la courbe de velocite : on voit si le
// probleme vient du choix de couche, du gain de correction, ou de la lecture.
#include <cstdio>
#include <cmath>
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

int main(int argc, char** argv) {
  const char* path = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/ep_dx.bin";
  const int note = argc > 2 ? atoi(argv[2]) : 60;
  auto bytes = slurp(path);
  if (bytes.empty()) { printf("no pack\n"); return 1; }
  PianoEngine e;
  e.prepare(48000.0, 256);
  e.loadSampleBank(bytes.data(), bytes.size());
  for (int i = 0; i < e.paramCount(); ++i) e.setNorm(i, 0.5f);
  printf("pack=%s regions=%zu preload=%zu note=%d\n", path, e.bankRegions(), e.preloadedRegions(), note);
  for (double sec : {0.128, 0.25, 0.4, 0.8, 1.5, 3.0}) {
    printf("  fenetre %.3fs : ", sec);
    for (int vel : {30, 63, 64, 96, 127})
      printf("v%d=%.5f ", vel, e.pickedRms(note, vel, sec));
    printf("\n");
  }
  printf(" vel  layer root  gain      lin^g\n");
  int vlo = argc > 3 ? atoi(argv[3]) : 1, vhi = argc > 4 ? atoi(argv[4]) : 127;
  for (int vel = vlo; vel <= vhi; ++vel) {
    const auto* g = e.pickRegion(note, vel);
    if (!g) { printf("%4d  none\n", vel); continue; }
    const float lin = static_cast<float>(vel) / 127.f;
    const float gamma = std::pow(2.f, (0.5f - 0.5f) * 1.36f);
    const float v = std::pow(lin, gamma);
    printf("%4d  L%d root=%d tune=%+.1f frames=%u loop=%u..%u  rms=%.5f gain=%.5f  lin^g=%.5f\n",
           vel, g->layer, g->root, g->tuneCents, g->frames, g->loopStart, g->loopEnd,
           e.regionRms(g->root, g->layer), e.layerGain(g->root, g->layer), v);
  }
  return 0;
}
