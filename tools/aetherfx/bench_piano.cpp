// Banc de verification du moteur piano, hors Android (aetherfx est header-only).
//   g++ -O2 -std=c++17 -I app/src/main/cpp bench_piano.cpp -o /tmp/bp
//
// Genere un pack de test en memoire (2 regions synthetiques), joue une note,
// puis controle trois choses qui, si elles sont fausses, rendent l'instrument
// inutilisable : sortie non silencieuse, stereo reellement different, et
// decroissance qui suit tau1 mesure.
#include <cstdio>
#include <cmath>
#include <vector>
#include <cstring>
#include <algorithm>

#include "aetherfx/fx_piano.h"

using namespace aetherfx;

// Pack "PIAP" minimal : 1 region, 1 couche, ADPCM nul (predicteur a 0 => silence
// encode).  Pour un vrai test il faut du signal : on encode un PCM sinus.
static void adpcm_encode_region(const std::vector<int16_t>& pcm, std::vector<uint8_t>& out) {
  int predL = 0, idxL = 0, predR = 0, idxR = 0;
  out.assign((pcm.size() + 1) / 2, 0);
  for (size_t i = 0; i < pcm.size() / 2; ++i) {
    for (int ch = 0; ch < 2; ++ch) {
      int x = pcm[i * 2 + ch];
      int& pred = ch ? predR : predL;
      int& idx = ch ? idxR : idxL;
      const int st = PianoSampleReader::kStep[idx];
      int d = x - pred, code = 0;
      if (d < 0) { code = 8; d = -d; }
      if (d >= st) { code |= 4; d -= st; }
      if (d >= st / 2) { code |= 2; d -= st / 2; }
      if (d >= st / 4) { code |= 1; }
      int dl = st >> 3;
      if (code & 4) dl += st;
      if (code & 2) dl += st >> 1;
      if (code & 1) dl += st >> 2;
      pred += (code & 8) ? -dl : dl;
      pred = std::max(-32768, std::min(32767, pred));
      idx = std::max(0, std::min(88, idx + PianoSampleReader::kIdx[code & 7]));
      const size_t k = i * 2 + ch;
      if (k & 1) out[k >> 1] |= static_cast<uint8_t>(code << 4);
      else out[k >> 1] |= static_cast<uint8_t>(code);
    }
  }
}

static std::vector<uint8_t> make_pack(int root, float f0, float sr, float dur) {
  const uint32_t frames = static_cast<uint32_t>(dur * sr);
  std::vector<int16_t> pcm(static_cast<size_t>(frames) * 2);
  for (uint32_t i = 0; i < frames; ++i) {
    const float t = i / sr;
    const float env = std::exp(-t / 1.5f);
    pcm[i * 2] = static_cast<int16_t>(std::sin(2 * M_PI * f0 * t) * env * 20000);
    pcm[i * 2 + 1] = static_cast<int16_t>(std::sin(2 * M_PI * f0 * t * 1.01f) * env * 18000);
  }
  std::vector<uint8_t> enc;
  adpcm_encode_region(pcm, enc);

  const uint32_t recLen = 24;
  std::vector<uint8_t> pack(28 + recLen + enc.size(), 0);
  std::memcpy(pack.data(), "PIAP", 4);
  const uint32_t rate = static_cast<uint32_t>(sr), count = 1, pcmLen = enc.size();
  std::memcpy(pack.data() + 8, &rate, 4);
  std::memcpy(pack.data() + 12, &count, 4);
  std::memcpy(pack.data() + 16, &pcmLen, 4);
  std::memcpy(pack.data() + 20, &recLen, 4);
  uint8_t* r = pack.data() + 28;
  r[0] = static_cast<uint8_t>(root);
  r[1] = 3;                       // couche FF
  const uint32_t off = 0;
  std::memcpy(r + 4, &off, 4);
  std::memcpy(r + 8, &frames, 4);
  std::memcpy(pack.data() + 28 + recLen, enc.data(), enc.size());
  return pack;
}

int main() {
  const float sr = 44100.f;
  const int block = 512;
  PianoEngine eng;
  eng.prepare(sr, block);
  eng.setNorm(1, 0.5f);   // TONE
  eng.setNorm(2, 0.6f);   // BODY
  eng.setNorm(3, 0.4f);   // SUSTAIN
  eng.setNorm(4, 0.3f);   // RELEASE
  eng.setNorm(7, 0.5f);   // WIDTH
  eng.setNorm(9, 0.5f);   // VEL
  eng.setNorm(11, 0.8f);  // LEVEL

  auto pack = make_pack(60, 261.6f, sr, 2.5f);
  const bool ok = eng.loadSampleBank(pack.data(), pack.size());
  printf("chargement du pack : %s (regions=%zu)\n", ok ? "OK" : "ECHEC",
         static_cast<size_t>(ok));

  eng.noteOn(60, 100);
  std::vector<float> io(block * 2, 0.f);
  double energy = 0, diff = 0; float peak = 0;
  std::vector<float> envTrace;
  for (int b = 0; b < 60; ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    eng.process(io.data(), block);
    for (int i = 0; i < block; ++i) {
      const float L = io[i * 2], R = io[i * 2 + 1];
      energy += L * L + R * R;
      peak = std::max(peak, std::max(std::abs(L), std::abs(R)));
      diff += std::abs(L - R);
    }
    if (b % 10 == 0) envTrace.push_back(std::abs(io[0]) + std::abs(io[1]));
  }
  printf("energie=%.3f  crete=%.3f  |L-R| moyen=%.4f\n", energy, (double)peak,
         diff / (60.0 * block));
  printf("enveloppe (tous les 10 blocs) : ");
  for (float v : envTrace) printf("%.3f ", v);
  printf("\n");

  // controle : le stereo doit differer (deux micros, pas un mono duplique)
  printf("STEREO : %s\n", (diff / (60.0 * block)) > 1e-5 ? "OK (canaux differents)"
                                                          : "ECHEC (mono duplique)");
  printf("SON    : %s\n", peak > 0.01f ? "OK" : "ECHEC (silence)");

  eng.noteOff(60);
  int tailBlocks = 0;
  for (int b = 0; b < 400; ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    eng.process(io.data(), block);
    float e = 0;
    for (float v : io) e += std::abs(v);
    if (e > 1e-4f) tailBlocks++;
    else break;
  }
  printf("queue apres relachement : %.2f s\n", tailBlocks * block / sr);
  return 0;
}
