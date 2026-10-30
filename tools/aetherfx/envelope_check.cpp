// Cherche la cause du timbre "jouet" dans la sortie du moteur.
//
// Hypothese : une boucle courte (170 ms) repetee pendant tout le sustain module
// l'amplitude a ~1/0,17 = 5,9 Hz.  C'est ce tremblement regulier qui s'entend comme
// un bourdonnement de jouet, pas comme une corde qui decroit.
//
// Mesure : spectre de l'enveloppe (0,5 a 15 Hz).  Une corde reelle donne un spectre
// lisse ; une boucle courte donne un pic net a sa frequence de repetition.
//   g++ -O2 -std=c++17 -I app/src/main/cpp envelope_check.cpp -o /tmp/ec
#include <cstdio>
#include <cmath>
#include <vector>
#include <string>
#include <algorithm>

#include "aetherfx/fx_piano.h"

using namespace aetherfx;

static std::vector<uint8_t> readFile(const char* p) {
  FILE* f = fopen(p, "rb");
  if (!f) { fprintf(stderr, "ouvrir %s\n", p); exit(1); }
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  std::vector<uint8_t> b(n);
  if (fread(b.data(), 1, n, f) != (size_t)n) exit(1);
  fclose(f);
  return b;
}

static float goertzel(const std::vector<float>& x, float sr, float f) {
  double wr = 2.0 * M_PI * f / sr, c = 2.0 * std::cos(wr), s0 = 0, s1 = 0, s2 = 0;
  for (size_t i = 0; i < x.size(); ++i) { s0 = x[i] + c * s1 - s2; s2 = s1; s1 = s0; }
  return static_cast<float>(std::sqrt(std::max(0.0, s1 * s1 + s2 * s2 - c * s1 * s2)));
}

// Enveloppe RMS par tranches de 5 ms, puis spectre de cette enveloppe.
static void envelopeSpectrum(const std::vector<float>& x, float sr,
                             std::vector<float>& env, std::vector<float>& out) {
  const int hop = static_cast<int>(0.005f * sr);
  env.clear();
  for (size_t i = 0; i + hop <= x.size(); i += hop) {
    double s = 0;
    for (int k = 0; k < hop; ++k) s += x[i + k] * x[i + k];
    env.push_back(static_cast<float>(std::sqrt(s / hop)));
  }
  out.clear();
  const float envRate = sr / hop;
  for (float f = 0.5f; f <= 15.f; f += 0.1f) out.push_back(goertzel(env, envRate, f));
}

static void renderSustained(PianoEngine& eng, int note, float sr, float secs,
                            std::vector<float>& out, bool loop) {
  eng.allNotesOff();
  eng.reset();
  eng.setLoopsEnabled(loop);
  eng.noteOn(note, 100);
  const int block = 512;
  std::vector<float> io(static_cast<size_t>(block) * 2);
  out.clear();
  for (int b = 0; b < static_cast<int>(secs * sr / block); ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    eng.process(io.data(), block);
    for (int i = 0; i < block; ++i) out.push_back((io[i * 2] + io[i * 2 + 1]) * 0.5f);
  }
}

static float centroid(const std::vector<float>& x, float sr, float a, float b) {
  int i0 = static_cast<int>(a * sr), i1 = std::min<int>(static_cast<int>(b * sr), static_cast<int>(x.size()));
  if (i1 - i0 < 64) return 0.f;
  std::vector<float> seg(x.begin() + i0, x.begin() + i1);
  double num = 0, den = 0;
  for (float f = 60.f; f < 12000.f; f *= 1.01f) {
    float m = goertzel(seg, sr, f);
    num += static_cast<double>(m) * m * f; den += static_cast<double>(m) * m;
  }
  return static_cast<float>(num / (den + 1e-12));
}

int main(int argc, char** argv) {
  // DUMPRAW <note> <secs> : ecrit le rendu brut (f32 mono) sur stdout, pour analyse.
  if (argc > 3 && std::string(argv[1]) == "DUMPRAW") {
    const float sr = 44100.f;
    std::vector<uint8_t> bytes = readFile(argc > 4 ? argv[4] : "app/src/main/assets/pianopack/piano.bin");
    PianoEngine e; e.prepare(sr, 512);
    if (!e.loadSampleBank(bytes.data(), bytes.size())) return 1;
    for (int i = 0; i < 12; ++i) e.setNorm(i, 0.5f);
    std::vector<float> x;
    renderSustained(e, atoi(argv[2]), sr, atof(argv[3]), x, true);
    fwrite(x.data(), sizeof(float), x.size(), stdout);
    return 0;
  }
  const char* pack = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
  const float sr = 44100.f;
  std::vector<uint8_t> bytes = readFile(pack);
  PianoEngine eng;
  eng.prepare(sr, 512);
  if (!eng.loadSampleBank(bytes.data(), bytes.size())) { fprintf(stderr, "pack illisible\n"); return 1; }
  for (int i = 0; i < 12; ++i) eng.setNorm(i, 0.5f);

  // Profil de l'enveloppe sur 6 s : une boucle courte tient un plateau plat, une
  // vraie corde decroit sans palier.  C'est ce qui distingue un piano d'un jouet.
  // Attaque echantillon par echantillon : un fondu d'entree meme court efface le
  // transitoire de marteau, et c'est lui qui fait "piano".  On compare le moteur a
  // l'echantillon brut de la meme note.
  printf("\nattaque, 1 ms par pas, 12 premiers ms (moteur)\n");
  for (int note : {48, 60}) {
    std::vector<float> x, env, sp;
    renderSustained(eng, note, sr, 0.2f, x, true);
    const int hop = static_cast<int>(0.001f * sr);
    printf("  note %d :", note);
    for (int k = 0; k + hop <= static_cast<int>(x.size()) && k < 12 * hop; k += hop) {
      float pk = 0;
      for (int i = 0; i < hop; ++i) pk = std::max(pk, std::abs(x[k + i]));
      printf(" %5.3f", pk);
    }
    printf("\n");
  }
  return 0;
}
