// Which velocity layer does the engine actually pick, and for how much of the
// velocity range?  Reads the shipped pack and calls the reader's real pick().
//   g++ -O2 -std=c++17 -I app/src/main/cpp layer_reach.cpp -o /tmp/lr
#include <cstdio>
#include <cstdint>
#include <fstream>
#include <map>
#include <vector>
#include <string>

#include "aetherfx/fx_piano.h"

using namespace aetherfx;

int main(int argc, char** argv) {
  if (argc < 2) { std::fprintf(stderr, "usage: %s <piano.bin>\n", argv[0]); return 2; }
  std::ifstream f(argv[1], std::ios::binary);
  std::vector<uint8_t> bin((std::istreambuf_iterator<char>(f)),
                           std::istreambuf_iterator<char>());
  PianoSampleReader r;
  if (!r.load(bin.data(), bin.size())) { std::fprintf(stderr, "load failed\n"); return 2; }

  // Velocity range each layer owns, and the sample behind it for middle C's root.
  std::map<int, std::pair<int, int>> span;      // layer -> [vmin, vmax]
  std::map<int, std::string> example;
  for (int vel = 1; vel <= 127; ++vel) {
    const auto* g = r.pick(60, vel);
    if (!g) continue;
    auto& s = span[g->layer];
    if (s.first == 0 || vel < s.first) s.first = vel;
    if (vel > s.second) s.second = vel;
  }
  std::printf("layer  velocity range   share of 1..127\n");
  for (const auto& [layer, s] : span) {
    const int n = s.second - s.first + 1;
    std::printf("%5d  %6d .. %-6d   %5.1f%%\n", layer, s.first, s.second,
                100.0 * n / 127.0);
  }
  std::printf("\ndistinct layers reached: %zu\n", span.size());
  return 0;
}
