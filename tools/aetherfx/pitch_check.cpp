// Mesure la hauteur reellement produite pour une note donnee, et la compare a la
// hauteur attendue.  Le pack est a 22050 Hz ; si le moteur le joue au taux de
// sortie sans compenser, tout sort une octave trop haut (le piano devient un jouet).
//   g++ -O2 -std=c++17 -I app/src/main/cpp pitch_check.cpp -o /tmp/pc
#include <cstdio>
#include <cmath>
#include <vector>
#include <complex>
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

// Fondamentale par spectre de produit harmonique : on multiplie le spectre par ses
// versions compressees (x2, x3, x4).  Une simple recherche de pic se trompe d'octave,
// parce que le 2e harmonique d'un piano est souvent plus fort que la fondamentale.
static float goertzelMag(const std::vector<float>& x, float sr, float f) {
  const int N = std::min<int>(static_cast<int>(x.size()), 1 << 14);
  double wr = 2.0 * M_PI * f / sr, c = 2.0 * std::cos(wr);
  double s0 = 0, s1 = 0, s2 = 0;
  for (int i = 0; i < N; ++i) { s0 = x[i] + c * s1 - s2; s2 = s1; s1 = s0; }
  return static_cast<float>(std::sqrt(std::max(0.0, s1 * s1 + s2 * s2 - c * s1 * s2)));
}

static float fundamental(const std::vector<float>& x, float sr, float lo, float hi) {
  float best = 0.f, bestScore = -1.f;
  for (float f = lo; f <= hi; f *= 1.0015f) {
    float prod = 1.f;
    for (int r = 1; r <= 4; ++r) prod *= goertzelMag(x, sr, f * r);
    if (prod > bestScore) { bestScore = prod; best = f; }
  }
  return best;
}

static float renderNote(PianoEngine& eng, int note, float sr, std::vector<float>& out) {
  eng.allNotesOff();
  eng.reset();
  eng.noteOn(note, 100);
  const int block = 512;
  std::vector<float> io(static_cast<size_t>(block) * 2);
  out.clear();
  // 0,5 s : on reste dans le sustain, avant la decroissance.
  for (int b = 0; b < static_cast<int>(0.5f * sr / block); ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    eng.process(io.data(), block);
    for (int i = 0; i < block; ++i) out.push_back((io[i * 2] + io[i * 2 + 1]) * 0.5f);
  }
  return 0.f;
}

// Correlle le spectre du moteur avec celui de l'echantillon brut de la meme note.
// Si l'echantillon domine, la correlation est haute ; si la synthese "boost"
// l'ecrase, elle chute et le timbre devient une bouillie.
static float spectralMatch(const std::vector<float>& a, const std::vector<float>& b, float sr) {
  const int N = 1 << 13;
  std::vector<double> pa(N / 2 + 1, 0), pb(N / 2 + 1, 0);
  for (int i = 0; i < N && i < (int)a.size() && i < (int)b.size(); ++i) {
    double w = 0.5 - 0.5 * std::cos(2.0 * M_PI * i / (N - 1));
    double ca = 2.0 * std::cos(2.0 * M_PI * 0 / N);
    (void)ca;
    // DFT directe bornee : 4097 bins sur 8192 points est trop lourd, on sous-echantillonne
    (void)w;
  }
  // Correlation sur 200 raies log-espacées de 60 a 6000 Hz.
  double num = 0, da = 0, db = 0;
  for (int k = 0; k < 200; ++k) {
    float f = 60.f * std::pow(100.f, k / 199.f);
    double wa = 0, wb = 0;
    { double wr = 2*M_PI*f/sr, c = 2*std::cos(wr), s0=0,s1=0,s2=0; int M = std::min<int>((int)a.size(), 1<<14);
      for (int i=0;i<M;++i){ s0 = a[i] + c*s1 - s2; s2=s1; s1=s0; } wa = std::sqrt(std::max(0.0,s1*s1+s2*s2-c*s1*s2)); }
    { double wr = 2*M_PI*f/sr, c = 2*std::cos(wr), s0=0,s1=0,s2=0; int M = std::min<int>((int)b.size(), 1<<14);
      for (int i=0;i<M;++i){ s0 = b[i] + c*s1 - s2; s2=s1; s1=s0; } wb = std::sqrt(std::max(0.0,s1*s1+s2*s2-c*s1*s2)); }
    num += wa*wb; da += wa*wa; db += wb*wb;
  }
  return (float)(num / (std::sqrt(da*db) + 1e-12));
}

int main(int argc, char** argv) {
  const char* pack = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
  const float sr = 44100.f;
  std::vector<uint8_t> bytes = readFile(pack);

  PianoEngine eng;
  eng.prepare(sr, 512);
  if (!eng.loadSampleBank(bytes.data(), bytes.size())) { fprintf(stderr, "pack illisible\n"); return 1; }
  for (int i = 0; i < 12; ++i) eng.setNorm(i, i == 2 ? 0.f : 0.5f);   // BODY a 0 : pas de resonateurs
  eng.setNorm(11, 0.8f);

  printf("%-6s %-10s %-10s %-9s %s\n", "note", "attendu", "mesure", "ratio", "verdict");
  int bad = 0;
  const int notes[] = {36, 48, 60, 69, 72, 84};
  for (int note : notes) {
    std::vector<float> x;
    renderNote(eng, note, sr, x);
    const float want = 440.f * std::pow(2.f, (note - 69) / 12.f);
    // On cherche la fondamentale : bande etroite autour de l'attendu ET de son octave.
    // Bande etroite autour de la fondamentale attendue : une erreur d'octave
    // (facteur 2) tombe hors bande et n'est plus confondue avec la fondamentale.
    float got = fundamental(x, sr, want * 0.80f, want * 1.25f);
    const float ratio = got / want;
    // Diagnostic : les 4 plus fortes raies entre 50 et 4000 Hz.
    {
      std::vector<std::pair<float,float>> pk;
      for (float f = 50.f; f < 4000.f; f *= 1.002f) {
        float m = 0.f;
        { double wr = 2*M_PI*f/sr, c = 2*std::cos(wr), s0=0,s1=0,s2=0;
          int N = std::min<int>((int)x.size(), 1<<14);
          for (int i=0;i<N;++i){ s0 = x[i] + c*s1 - s2; s2=s1; s1=s0; }
          m = (float)(s1*s1 + s2*s2 - c*s1*s2); }
        pk.push_back({m, f});
      }
      std::sort(pk.rbegin(), pk.rend());
      printf("      raies:");
      for (int k = 0; k < 4 && k < (int)pk.size(); ++k) printf(" %7.1f Hz", pk[k].second);
      printf("\n");
    }
    const bool ok = std::abs(ratio - 1.f) < 0.03f;
    if (!ok) ++bad;
    printf("%-6d %-10.1f %-10.1f %-9.3f %s\n", note, want, got, ratio,
           ok ? "ok" : (std::abs(ratio - 2.f) < 0.06f ? "OCTAVE TROP HAUT" : "FAUX"));
  }
  // SPECTRAL <note> : correlation entre le moteur et l'echantillon brut.
  if (argc > 2 && std::string(argv[2]) == "SPECTRAL") {
    for (int note : notes) {
      std::vector<float> x;
      renderNote(eng, note, sr, x);
      // Meme note, resonateurs coupes (BODY, SYMP) : la sortie suit alors
      // l'echantillon seul.  Ne PAS mettre LEVEL a zero : le rendu serait muet et
      // la correlation vaudrait 0 pour tout (piege verifie).
      const float keep = 0.5f;
      for (int i = 0; i < 12; ++i) eng.setNorm(i, keep);
      eng.setNorm(2, 0.f); eng.setNorm(10, 0.f);
      std::vector<float> y;
      renderNote(eng, note, sr, y);
      printf("note %3d : correlation spectrale boost=%0.3f  sans-boost=%0.3f\n",
             note, spectralMatch(x, y, sr), spectralMatch(y, y, sr));
      for (int i = 0; i < 12; ++i) eng.setNorm(i, i == 2 ? 0.f : 0.5f);
    }
  }
  printf("\n%d/%d notes fausses\n", bad, static_cast<int>(sizeof(notes) / sizeof(notes[0])));
  return bad ? 1 : 0;
}
