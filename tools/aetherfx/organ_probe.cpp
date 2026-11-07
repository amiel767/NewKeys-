// Sonde de l'orgue : chaque parametre agit-il vraiment sur le signal ? Joue un accord,
// fait varier UN parametre a la fois et imprime RMS + centroide. Une ligne qui ne bouge
// pas signale un controle mort — le piege exact que la maquette HTML contenait (ses trois
// potards EQ ecrivaient tous dans la meme variable `tone`).
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/organ_probe.cpp -o /tmp/op && /tmp/op
#include <cstdio>
#include <cmath>
#include <cstring>
#include <vector>
#include <string>
#include <algorithm>

#include "aetherfx/fx_organ.h"

using namespace aetherfx;

// Un param qui doit etre neutre pour que les autres se lisent : les tirettes neutres sont
// a 1/8 (un seul pas), pas a 0.5 (quatre pas et demi), donc la sonde les pose explicitement.
static void setNeutral(OrganEngine& e) {
  for (int i = 0; i < e.paramCount(); ++i) e.setNorm(i, 0.f);
  for (int j = 0; j < 9; ++j) { e.setNorm(j, 1.f / 8.f); e.setNorm(9 + j, 0.f); }  // upper 8' seul
  e.setNorm(22, 0.6f);   // Vibrato
  e.setNorm(23, 1.f);    // Vib upper
  e.setNorm(24, 1.f);    // Vib lower
  e.setNorm(25, 1.f);    // Rotary
  e.setNorm(26, 0.3f);   // Click
  e.setNorm(27, 0.55f);  // Tone
  e.setNorm(28, 0.4f);   // Drive
  e.setNorm(30, 0.8f);   // Level
  e.setNorm(31, 0.5f);   // RevMode
  e.setNorm(32, 0.5f);   // Treble
  e.setNorm(33, 0.5f);   // Cabinet
  e.setNorm(34, 0.5f);   // Amount
  e.setNorm(35, 0.5f);   // VelSense
  // New contract (36..45), all neutral at 0.5 so the classic voicing is what the probe hears.
  for (int i = 36; i < e.paramCount(); ++i) e.setNorm(i, 0.5f);
}

static void stats(OrganEngine& e, float sr, float& rms, float& cent) {
  const int block = 512;
  std::vector<float> io((size_t)block * 2), acc;
  for (int b = 0; b < (int)(1.2f * sr / block); ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    e.process(io.data(), block);
    acc.insert(acc.end(), io.begin(), io.end());
  }
  std::vector<float> m;
  for (size_t i = 0; i < acc.size(); i += 2) m.push_back((acc[i] + acc[i + 1]) * 0.5f);
  double s = 0; for (float v : m) s += (double)v * v;
  rms = (float)std::sqrt(s / std::max<size_t>(1, m.size()));

  size_t a = (size_t)(0.3 * sr), L = 16384;
  std::vector<double> re(L), im(L);
  for (size_t i = 0; i < L && a + i < m.size(); ++i) {
    double w = 0.5 - 0.5 * std::cos(2 * M_PI * i / (L - 1));
    re[i] = m[a + i] * w; im[i] = 0;
  }
  double num = 0, den = 0;
  for (int k = 1; k < L / 2; ++k) {
    double sr2 = 0, si2 = 0;
    for (int n2 = 0; n2 < L; ++n2) {
      double ph = -2 * M_PI * k * n2 / L;
      sr2 += re[n2] * std::cos(ph); si2 += re[n2] * std::sin(ph);
    }
    double p = sr2 * sr2 + si2 * si2, f = k * sr / L;
    num += p * f; den += p;
  }
  cent = (float)(num / std::max(1e-12, den));
}

// Un parametre ne peut agir que si sa precondition est remplie : les modificateurs de
// percussion ne pesent rien quand la percussion est eteinte, les tirettes Lower rien
// quand seul le manuel superieur joue, RevMode rien sans envoi de reverb. Sans cette
// etape la sonde declare 15 « morts » qui n'en sont pas.
static void precondition(OrganEngine& e, int idx) {
  if (idx >= 19 && idx <= 21) e.setNorm(18, 1.f);          // PercSoft/Fast/3rd exigent PercOn
  if (idx == 31) { e.setNorm(29, 0.6f); e.setNorm(34, 0.5f); }  // RevMode exige un envoi et un Amount neutre
  if (idx == 34) e.setNorm(29, 0.6f);                      // Amount exige un envoi (Space > 0)
  if (idx == 24 || idx == 25) e.setNorm(22, 0.6f);         // VibUpper/Lower exigent le vibrato
  // Le moteur lit upper||lower : allumer un seul ne se voit pas si l'autre est deja allume.
  // On eteint l'autre manuel pour isoler le parametre mesure.
  if (idx == 23) e.setNorm(24, 0.f);
  if (idx == 24) e.setNorm(23, 0.f);
  // Depth / Mic / Balance ne se lisent que quand le rotor tourne : a l'arret les deux
  // dephasages et l'AM valent 1, donc le parametre ne peut rien deplacer.
  if (idx >= 39 && idx <= 41) e.setNorm(25, 1.f);
  // Les rampes Rise / Fall se lisent sur la transition STOP -> FAST : a vitesse constante
  // (valeur neutre 1 = FAST) la rampe est deja finie, donc le parametre ne peut rien deplacer.
  if (idx == 37 || idx == 38) e.setNorm(25, 0.5f);
  // Le dial de mode n'agit qu'hors du B3 : au neutre (0.5) l'ecart de mode est nul.
  if (idx == 43) e.setNorm(42, 0.f);
}

// Mesure un parametre : valeur basse contre valeur haute, tout le reste identique.
// Les DEUX manuels jouent, sinon la moitie du moteur reste muette.
struct Mesure { float r0, c0, r1, c1; };
static Mesure mesurer(int idx, float v0, float v1) {
  Mesure m{};
  for (int v = 0; v < 2; ++v) {
    OrganEngine e; e.prepare(48000.f, 512);
    setNeutral(e);
    precondition(e, idx);
    e.setNorm(idx, v ? v1 : v0);
    e.reset();
    e.noteOn(55, 100); e.noteOn(60, 100); e.noteOn(64, 100); e.noteOn(67, 100);
    float r, c; stats(e, 48000.f, r, c);
    if (v == 0) { m.r0 = r; m.c0 = c; } else { m.r1 = r; m.c1 = c; }
  }
  return m;
}

int main() {
  const int N = OrganEngine{}.paramCount();
  const char* noms[64] = {
    "Upper 16'","Upper 5⅓'","Upper 8'","Upper 4'","Upper 2⅔'","Upper 2'","Upper 1⅗'","Upper 1⅓'","Upper 1'",
    "Lower 16'","Lower 5⅓'","Lower 8'","Lower 4'","Lower 2⅔'","Lower 2'","Lower 1⅗'","Lower 1⅓'","Lower 1'",
    "PercOn","PercSoft","PercFast","Perc3rd","Vibrato","VibUpper","VibLower","Rotary","Click","Tone",
    "Drive","Space","Level","RevMode","Treble","Cabinet","Amount","VelSense",
    "Mid","RotRise","RotFall","RotDepth","RotMic","RotBal","Model","ModelDial","Octave","Bass"};

  printf("%-11s %-20s %-20s %s\n", "param", "valeur basse", "valeur haute", "verdict");
  int morts = 0;
  for (int i = 0; i < N; ++i) {
    // Les tirettes sont en pas 0..8 : on compare 0 a 8. Les autres, 0 a 1.
    float v0 = 0.f, v1 = (i < 18) ? 1.f : 1.f;
    Mesure m = mesurer(i, v0, v1);
    float dr = 100.f * (m.r1 - m.r0) / std::max(1e-9f, m.r0);
    float dc = 100.f * (m.c1 - m.c0) / std::max(1e-9f, m.c0);
    // Un parametre "agit" s'il deplace le niveau ou le timbre de plus de 0,5 %.
    bool agit = std::fabs(dr) > 0.5f || std::fabs(dc) > 0.5f;
    if (!agit) ++morts;
    printf("%-11s rms %.4f c %5.0f   rms %.4f c %5.0f   %s (dRMS %+.1f%% dC %+.1f%%)\n",
           noms[i], m.r0, m.c0, m.r1, m.c1, agit ? "AGIT" : "*** MORT ***", dr, dc);
  }
  printf("\n%d parametres, %d morts\n", N, morts);
  // Regression : l'octave est un parametre vivant, donc une touche peut etre relachee sur un
  // autre octave que celui de l'appui. Sans memoire par touche, la note reste bloquee.
  {
    OrganEngine e; e.prepare(48000.f, 512);
    setNeutral(e);
    e.noteOn(60, 100);
    e.setNorm(44, 1.f);   // +2 octaves pendant que la touche est tenue
    e.noteOff(60);
    float r, c; stats(e, 48000.f, r, c);
    const int restant = e.activeWheels();
    printf("octave tenue puis relachee : %d roues actives %s\n", restant, restant == 0 ? "OK" : "*** NOTE BLOQUEE ***");
    if (restant != 0) ++morts;
  }
  return morts == 0 ? 0 : 1;
}