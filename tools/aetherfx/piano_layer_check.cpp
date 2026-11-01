// T1 : dynamique reelle des couches du pack piano.
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_layer_check.cpp -o /tmp/plc
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

  PianoSampleReader r;
  if (!r.load(bytes.data(), bytes.size())) { printf("load failed\n"); return 1; }
  printf("pack=%s regions=%zu\n", path, r.regionCount());

  // Pour chaque velocite, quelle region est choisie et quel est son niveau.
  printf("\nvel -> (couche choisie, racine, crete, rms, tuneCents)\n");
  for (int vel : {10, 30, 60, 90, 127}) {
    const PianoSampleReader::Region* g = r.pick(60, vel);
    if (!g) continue;
    auto pcm = r.decoded(g);
    if (!pcm || pcm->empty()) continue;
    int pk = 0; double s = 0;
    for (size_t i = 0; i < pcm->size(); ++i) { pk = std::max(pk, std::abs((int)(*pcm)[i])); s += (double)(*pcm)[i] * (*pcm)[i]; }
    const double rms = std::sqrt(s / pcm->size()) / 32768.0;
    printf("vel %3d : couche=%d root=%3d crete=%.4f rms=%.4f tune=%+.2f cents\n",
           vel, g->layer, g->root, pk / 32768.0, rms, g->tuneCents);
  }

  // Niveau de chaque couche a C4 : c'est la dynamique que le pack porte vraiment.
  printf("\ncouche : crete et rms (racine la plus proche de 60)\n");
  double ref = 0;
  for (int layer = 0; layer < 6; ++layer) {
    const PianoSampleReader::Region* best = nullptr;
    int bestCost = 1 << 30;
    // pick() ne prend qu'une velocite : on balaie et on garde la region de cette couche.
    for (int vel = 0; vel < 128; ++vel) {
      const PianoSampleReader::Region* g = r.pick(60, vel);
      if (!g || g->layer != layer) continue;
      const int cost = std::abs(g->root - 60);
      if (cost < bestCost) { bestCost = cost; best = g; }
    }
    if (!best) continue;
    auto pcm = r.decoded(best);
    if (!pcm || pcm->empty()) continue;
    int pk = 0; double s = 0;
    for (size_t i = 0; i < pcm->size(); ++i) { pk = std::max(pk, std::abs((int)(*pcm)[i])); s += (double)(*pcm)[i] * (*pcm)[i]; }
    const double rms = std::sqrt(s / pcm->size()) / 32768.0;
    if (layer == 0) ref = rms;
    printf("couche %d (root %3d) : crete=%.4f rms=%.4f  %+.1f dB vs couche 0\n",
           layer, best->root, pk / 32768.0, rms, 20.0 * std::log10(std::max(1e-9, rms) / std::max(1e-9, ref)));
  }
  return 0;
}
