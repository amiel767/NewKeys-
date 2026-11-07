// Prints the tail level over time for a few parameter settings, so a collapse or a
// runaway is visible directly instead of inferred from a single aggregate number.
//   g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/space_trace tools/design/space_trace.cpp
#include "aetherfx/fx_space.h"
#include <cstdio>
#include <cmath>
#include <vector>

using aetherfx::SpaceReverb;
enum { MIX, PREDELAY, DECAY, HF, HS, BF, BM, SIZE, ATT, EARLY, LATE, RATE, DEPTH, HC, LC };
static const double SR = 48000.0;
static const int BLK = 512;

static void trace(const char* label, std::vector<float> p) {
  SpaceReverb s; s.prepare(SR, BLK);
  for (int i = 0; i < 15; ++i) s.setNorm(i, p[i]);
  std::vector<float> buf(BLK * 2);
  uint32_t seed = 12345u;
  auto rnd = [&seed]() { seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5; return float(int32_t(seed)) * (1.f / 2147483648.f); };
  std::printf("%s\n", label);
  for (int b = 0; b < 480; b += 40) {
    double peak = 0, sum = 0;
    for (int i = 0; i < 40; ++i) {
      for (int k = 0; k < BLK; ++k) {
        const float x = ((b + i) < 2) ? 0.5f * rnd() : 0.f;
        buf[2 * k] = x; buf[2 * k + 1] = x;
      }
      s.process(buf.data(), BLK);
      for (int k = 0; k < BLK; ++k) { peak = std::max(peak, (double)std::fabs(buf[2 * k])); sum += (double)buf[2 * k] * buf[2 * k]; }
    }
    std::printf("  t=%5.2fs peak=%9.6f rms=%9.6f\n", b * BLK / SR, peak, std::sqrt(sum / (40.0 * BLK)));
  }
}

int main() {
  std::vector<float> base(15, 0.5f);
  base[MIX] = 0.34f; base[DECAY] = 0.3f; base[SIZE] = 0.8f; base[EARLY] = 0.f;
  auto a = base; a[BM] = 0.f;
  auto b = base; b[BM] = 1.f;
  auto c = base; c[BM] = 0.5f;
  trace("bm=0.0", a);
  trace("bm=0.5", c);
  trace("bm=1.0", b);
  return 0;
}
