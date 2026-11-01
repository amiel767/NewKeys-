// Banc d'accord et de stabilite du piano Æther, hors Android.
//
// Verifie les trois reproches utilisateur, chacun par une mesure :
//   1. la note jouee est-elle juste ?            -> balayage chromatique
//   2. la meme touche donne-t-elle la meme note ? -> appuis repetes
//   3. une note tenue bouge-t-elle quand on en ajoute une autre ? -> accord
// Plus les deux defauts de voix : vol de voix et relachement (clics).
//
// Compilation :
//   g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/ptc \
//       tools/aetherfx/piano_tune_check.cpp
//   ./ptc app/src/main/assets/pianopack/piano.bin
//
// Pourquoi ces methodes de mesure (chacune a coute une passe) :
//   - un pic de spectre simple donne l'octave au-dessus des que h2 > h1, ce qui
//     arrive souvent au piano : on mesure par correlation sur une fenetre LONGUE
//     et un balayage fin (0,2 Hz), borne a +-6 % de la note visee ;
//   - une fenetre courte (1 024 echantillons) resout ~280 cents : on mesurerait
//     son propre bruit ;
//   - un filtre de bande Q=1,2 laisse passer les autres notes d'un accord : on
//     mesure par balayage, pas par filtrage.
#include "aetherfx/fx_piano.h"

#include <algorithm>
#include <cmath>
#include <complex>
#include <cstdio>
#include <vector>

using namespace std;
using namespace aetherfx;

static double pitch(const vector<float>& x, int sr, double hint) {
  double lo = hint * 0.94, hi = hint * 1.06, best = -1, bf = hint;
  for (double f = lo; f <= hi; f += 0.2) {
    double re = 0, im = 0, w = 2 * M_PI * f / sr;
    for (size_t i = 0; i < x.size(); ++i) {
      const double a = w * i;
      re += x[i] * cos(a);
      im -= x[i] * sin(a);
    }
    const double m = re * re + im * im;
    if (m > best) { best = m; bf = f; }
  }
  return bf;
}

static void setup(PianoEngine& e) {
  for (int i = 0; i < 17; ++i) e.setNorm(i, 0.5f);
  e.setNorm(0, 0.6f);    // TONE
  e.setNorm(11, 0.75f);  // DRIVE
  e.setNorm(15, 0.f);    // MODEL = acoustique
}

static void capture(PianoEngine& e, vector<float>& mono, int blocks, double from_s,
                    double to_s, int sr, int blk) {
  vector<float> io(blk * 2);
  for (int b = 0; b < blocks; ++b) {
    fill(io.begin(), io.end(), 0.f);
    e.process(io.data(), blk);
    // Comparaison en double : `(int)(1e9 * 44100)` deborde un int32 et rendait
    // la condition toujours fausse (le banc mesurait du silence).
    if ((double)b * blk >= from_s * sr && (double)b * blk < to_s * sr)
      for (int i = 0; i < blk; ++i) mono.push_back((io[2 * i] + io[2 * i + 1]) * 0.5f);
  }
}

static double maxJump(const vector<float>& x, size_t from, size_t to) {
  double m = 0;
  for (size_t i = max(from, (size_t)1); i < min(to, x.size()); ++i)
    m = max(m, fabs((double)x[i] - x[i - 1]));
  return m;
}

int main(int argc, char** argv) {
  const char* path = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
  FILE* f = fopen(path, "rb");
  if (!f) { printf("fichier introuvable : %s\n", path); return 1; }
  fseek(f, 0, SEEK_END);
  long n = ftell(f);
  fseek(f, 0, SEEK_SET);
  vector<uint8_t> bytes(n);
  const size_t got = fread(bytes.data(), 1, n, f);
  fclose(f);
  if ((long)got != n) { printf("lecture partielle %ld/%ld\n", (long)got, n); return 1; }

  const int SR = 44100, BLK = 256;
  int fails = 0;

  // ---- 1. balayage chromatique ----
  printf("=== 1. justesse, balayage chromatique 36..96 ===\n");
  int bad = 0, worstNote = 0;
  double worst = 0;
  for (int note = 36; note <= 96; ++note) {
    PianoEngine e;
    e.prepare(SR, BLK);
    e.loadSampleBank(bytes.data(), bytes.size());
    setup(e);
    e.noteOn(note, 100);
    vector<float> mono;
    capture(e, mono, 150, 0.10, 0.90, SR, BLK);
    const double ref = 440.0 * pow(2.0, (note - 69) / 12.0);
    const double c = 1200 * log2(pitch(mono, SR, ref) / ref);
    if (fabs(c) > worst) { worst = fabs(c); worstNote = note; }
    if (fabs(c) > 10) { ++bad; printf("  note %3d : %+7.1f cents  <-- FAUX\n", note, c); }
  }
  printf("  %d/61 fausses (>10 cents) | pire : note %d a %.1f cents\n", bad, worstNote, worst);
  if (bad) ++fails;

  // ---- 2. appuis repetes ----
  printf("=== 2. stabilite, 6 appuis de la meme touche ===\n");
  for (int note : {55, 60, 72}) {
    double mn = 1e9, mx = -1e9;
    for (int rep = 0; rep < 6; ++rep) {
      PianoEngine e;
      e.prepare(SR, BLK);
      e.loadSampleBank(bytes.data(), bytes.size());
      setup(e);
      e.noteOn(note, 100);
      vector<float> mono;
      capture(e, mono, 60, 0.05, 0.30, SR, BLK);
      const double ref = 440.0 * pow(2.0, (note - 69) / 12.0);
      const double c = 1200 * log2(pitch(mono, SR, ref) / ref);
      mn = min(mn, c);
      mx = max(mx, c);
    }
    printf("  note %d : etendue entre appuis %.1f cents (%+.1f .. %+.1f)\n", note, mx - mn, mn, mx);
    if (mx - mn > 5) { printf("    <-- INSTABLE\n"); ++fails; }
  }

  // ---- 3. une note tenue bouge-t-elle si on en ajoute ? ----
  printf("=== 3. accord C4-E4-G4-C5 : hauteur de chaque note tenue ===\n");
  {
    int notes[] = {60, 64, 67, 72};
    double ref[4];
    for (int k = 0; k < 4; ++k) ref[k] = 440.0 * pow(2.0, (notes[k] - 69) / 12.0);
    double alone[4];
    for (int k = 0; k < 4; ++k) {
      PianoEngine e;
      e.prepare(SR, BLK);
      e.loadSampleBank(bytes.data(), bytes.size());
      setup(e);
      e.noteOn(notes[k], 100);
      vector<float> mono;
      capture(e, mono, 200, 0.10, 1.10, SR, BLK);
      alone[k] = pitch(mono, SR, ref[k]);
    }
    PianoEngine e;
    e.prepare(SR, BLK);
    e.loadSampleBank(bytes.data(), bytes.size());
    setup(e);
    for (int k = 0; k < 4; ++k) e.noteOn(notes[k], 100);
    vector<float> mono;
    capture(e, mono, 200, 0.10, 1.10, SR, BLK);
    for (int k = 0; k < 4; ++k) {
      const double hz = pitch(mono, SR, ref[k]);
      const double drift = 1200 * log2(hz / alone[k]);
      printf("  n%d : %+6.1f cents seul, ecart en accord %+5.1f cents\n", notes[k],
             1200 * log2(alone[k] / ref[k]), drift);
      if (fabs(drift) > 8) { printf("    <-- BOUGE\n"); ++fails; }
    }
  }

  // ---- 4. vol de voix et relachement ----
  printf("=== 4. voix : vol et relachement ===\n");
  {
    PianoEngine e;
    e.prepare(SR, BLK);
    e.loadSampleBank(bytes.data(), bytes.size());
    setup(e);
    for (int k = 0; k < 12; ++k) e.noteOn(40 + k * 3, 100);  // 12 voix = la limite
    vector<float> mono;
    capture(e, mono, 40, 0.0, 1e9, SR, BLK);
    const size_t before = mono.size();
    for (int k = 0; k < 20; ++k) e.noteOn(48 + k * 2, 100);  // force le vol
    capture(e, mono, 80, 0.0, 1e9, SR, BLK);
    const double q = maxJump(mono, before / 2, before);
    const double after = maxJump(mono, before + 64, mono.size());
    printf("  vol de voix : pas max %.4f -> %.4f (ratio %.1f)\n", q, after, after / max(q, 1e-9));
    if (after / max(q, 1e-9) > 3.0) { printf("    <-- CLIC\n"); ++fails; }
  }
  {
    PianoEngine e;
    e.prepare(SR, BLK);
    e.loadSampleBank(bytes.data(), bytes.size());
    setup(e);
    e.noteOn(60, 100);
    vector<float> mono;
    capture(e, mono, 60, 0.0, 1e9, SR, BLK);
    const size_t rel = mono.size();
    e.noteOff(60);
    capture(e, mono, 400, 0.0, 1e9, SR, BLK);
    const double q = maxJump(mono, rel / 2, rel);
    const double after = maxJump(mono, rel + 64, rel + 4000);
    double tail = 0;
    for (size_t i = mono.size() - 2000; i < mono.size(); ++i) tail = max(tail, fabs((double)mono[i]));
    printf("  relachement : pas max %.4f -> %.4f (ratio %.1f) | reste %.6f %s\n", q, after,
           after / max(q, 1e-9), tail, tail < 0.002 ? "(eteinte)" : "(NE S'ETEINT PAS)");
    if (after / max(q, 1e-9) > 3.0 || tail >= 0.002) ++fails;
  }

  printf("\n%s (%d echec%s)\n", fails ? "ECHECS" : "TOUT PASSE", fails, fails > 1 ? "s" : "");
  return fails ? 1 : 0;
}
