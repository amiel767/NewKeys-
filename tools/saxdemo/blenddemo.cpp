// Renders one phrase at three positions of the Blend knob (parameter 22): 0 = pure
// synthesis, 0.5 = the preset default, 1 = the sampled pack alone. Same performance,
// same seed of humanisation, so the only thing that moves is the blend.
#include "aetherfx/fx_sax.h"
#include <cstdio>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>
using namespace aetherfx;
static const int SR = 48000, BLK = 256;

static std::vector<uint8_t> slurp(const char* p) {
  FILE* f = fopen(p, "rb"); if (!f) { fprintf(stderr, "cannot open %s\n", p); exit(1); }
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  std::vector<uint8_t> v(n); if (fread(v.data(), 1, n, f) != (size_t)n) {} fclose(f); return v;
}

struct Note { int n; float dur; bool slur; };

static void render(SaxEngine& s, std::vector<float>& out, const Note* ph, int cnt) {
  std::vector<float> b(BLK * 2);
  for (int i = 0; i < cnt; i++) {
    s.noteOn(ph[i].n, 100);
    float left = ph[i].dur;
    while (left > 0) { std::fill(b.begin(), b.end(), 0.f); s.process(b.data(), BLK);
      for (int j = 0; j < BLK; j++) out.push_back(b[2 * j]); left -= BLK / (float)SR; }
    if (!ph[i].slur) { s.noteOff(ph[i].n);
      for (int k = 0; k < 10; k++) { std::fill(b.begin(), b.end(), 0.f); s.process(b.data(), BLK);
        for (int j = 0; j < BLK; j++) out.push_back(b[2 * j]); } }
  }
  s.noteOff(ph[cnt - 1].n);
  for (int k = 0; k < 16; k++) { std::fill(b.begin(), b.end(), 0.f); s.process(b.data(), BLK);
    for (int j = 0; j < BLK; j++) out.push_back(b[2 * j]); }
}

// float32 stereo (format tag 3): what tomp3.py reads.
static void wavF(const char* fn, const std::vector<float>& mono) {
  float pk = 0; for (float x : mono) pk = std::max(pk, std::fabs(x));
  float g = 0.89f / std::max(0.01f, pk);
  std::vector<float> inter(mono.size() * 2);
  for (size_t i = 0; i < mono.size(); i++) { inter[2*i] = mono[i]*g; inter[2*i+1] = mono[i]*g; }
  uint32_t dataBytes = (uint32_t)(inter.size() * 4), riff = 36 + dataBytes;
  FILE* f = fopen(fn, "wb");
  fwrite("RIFF", 1, 4, f); fwrite(&riff, 4, 1, f); fwrite("WAVEfmt ", 1, 8, f);
  uint32_t sub = 16; uint16_t tag = 3, ch = 2, bits = 32; uint32_t sr = SR;
  uint32_t br = SR * 2 * 4; uint16_t ba = 2 * 4;
  fwrite(&sub,4,1,f); fwrite(&tag,2,1,f); fwrite(&ch,2,1,f); fwrite(&sr,4,1,f);
  fwrite(&br,4,1,f); fwrite(&ba,2,1,f); fwrite(&bits,2,1,f);
  fwrite("data",1,4,f); fwrite(&dataBytes,4,1,f);
  fwrite(inter.data(), 4, inter.size(), f); fclose(f);
  double e = 0; for (float x : mono) e += (double)x * x;
  printf("  %-28s %.2f s  crete brute %.3f  rms %.4f\n", fn, mono.size() / (float)SR, pk, std::sqrt(e / mono.size()));
}

int main(int argc, char** argv) {
  const char* bank = argc > 1 ? argv[1] : "/workspace/project/NewKeys-/app/src/main/assets/saxpack/saxpack_alt.bin";
  const char* outdir = argc > 2 ? argv[2] : "/tmp/blenddemo";
  auto bin = slurp(bank);
  // Alto jazz-ballad preset, values in registry order (parameter 22 = Blend, set per pass).
  float P[23] = {0.33f,0.f,0.5f,0.5f,0.42f,0.05f,0.f,0.12f,0.45f,0.34f,0.30f,0.28f,
                 0.34f,0.5f,0.55f,0.5f,0.55f,0.5f,0.35f,0.5f,0.8f,0.45f,0.5f};
  Note ph[] = {{67,0.34f,true},{69,0.34f,true},{71,0.34f,true},{74,0.42f,false},
               {72,1.1f,false},{71,0.30f,false},{67,0.85f,false}};
  const float mixes[3] = {0.f, 0.5f, 1.f};
  const char* names[3] = {"blend_000_synthese", "blend_050_defaut", "blend_100_samples"};
  printf("banque : %s\n", bank);
  for (int k = 0; k < 3; k++) {
    SaxEngine s; s.prepare(SR, BLK);
    if (!s.loadSamples(bin.data(), bin.size())) { fprintf(stderr, "bank load failed\n"); return 1; }
    P[22] = mixes[k];
    for (int i = 0; i < 23; i++) s.setNorm(i, P[i]);
    std::vector<float> o; render(s, o, ph, 7);
    char path[512]; snprintf(path, sizeof path, "%s/%s.wav", outdir, names[k]);
    wavF(path, o);
  }
  return 0;
}
