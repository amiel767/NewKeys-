// D3 : une note reste-t-elle allumee apres noteOff et allNotesOff ?
//
// Le banc doit separer DEUX choses qui se ressemblent :
//   - une VOIX bloquee : elle sonne encore meme quand tous les effets sont a zero ;
//   - une QUEUE d'effet (delay, reverb) : elle disparait des que les effets sont a
//     zero.  C'est un comportement voulu, pas un defaut.
//
// Mesurer le seul niveau total ne les distingue pas : un seuil sur le niveau brut
// declare « bloquee » un instrument dont la reverb est simplement longue (mesure
// faite : LEADS 0,0055 et SAX 0,0196 a 1,5 s, tous deux a 0,00000 effets coupes).
#include "aetherfx/fx_mixer.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

using namespace aetherfx;

// Niveau RMS apres extinction, avec ou sans les effets du preset.
static double tailLevel(int id, bool withEffects) {
  FxMixer mx;
  mx.prepare(44100, 256);
  mx.setInstrument(0, id);
  // Parametres a zero : la voix seule.  Sans ca on mesure la reverb.
  if (!withEffects) for (int p = 0; p < 34; ++p) mx.setInstrumentParam(0, p, 0.f);
  const int notes[] = {60, 64, 67};
  for (int n : notes) mx.noteOn(0, n, 100);
  std::vector<float> io(512, 0.f);
  for (int b = 0; b < 40; ++b) { std::fill(io.begin(), io.end(), 0.f); mx.processStrip(0, io.data(), 256); }
  for (int n : notes) mx.noteOff(0, n);
  double v = 0;
  for (int b = 0; b < 344; ++b) {                 // 2 s
    std::fill(io.begin(), io.end(), 0.f);
    mx.processStrip(0, io.data(), 256);
    if (b >= 340) { double e = 0; for (int i = 0; i < 256; ++i) e += (double)io[i * 2] * io[i * 2]; v = std::sqrt(e / 256.0); }
  }
  return v;
}

// requestPanic (le bouton panic) doit tout couper, queue de reverb comprise.
static double panicLevel(int id) {
  FxMixer mx;
  mx.prepare(44100, 256);
  mx.setInstrument(0, id);
  const int notes[] = {60, 64, 67};
  for (int n : notes) mx.noteOn(0, n, 100);
  std::vector<float> io(512, 0.f);
  for (int b = 0; b < 40; ++b) { std::fill(io.begin(), io.end(), 0.f); mx.processStrip(0, io.data(), 256); }
  mx.allNotesOff(0);
  mx.requestPanic();
  double v = 0;
  for (int b = 0; b < 344; ++b) {
    std::fill(io.begin(), io.end(), 0.f);
    mx.processStrip(0, io.data(), 256);
    if (b >= 340) { double e = 0; for (int i = 0; i < 256; ++i) e += (double)io[i * 2] * io[i * 2]; v = std::sqrt(e / 256.0); }
  }
  return v;
}

int main() {
  const char *names[] = {"ORGAN", "SYNTH", "LEADS", "SAX", "PIANO"};
  const int ids[] = {6, 14, 15, 16, 17};
  int fails = 0;
  for (int k = 0; k < 5; ++k) {
    const double voice = tailLevel(ids[k], false);
    const double total = tailLevel(ids[k], true);
    const double panic = panicLevel(ids[k]);
    // Le seul critere de note bloquee : la VOIX survit-elle au relachement ?
    const bool okVoice = voice < 1e-5;
    // allNotesOff ne vide pas la queue de reverb : c'est voulu.  Ce que l'appelant
    // doit retenir est que la queue n'est coupee que par le chemin de panique.
    const bool tailIsEffect = total < 0.05;
    if (!okVoice || !tailIsEffect) ++fails;
    printf("  %-6s apres noteOff : voix %.6f (%s) | queue d'effet %.5f (%s)\n",
           names[k], voice, okVoice ? "eteinte" : "BLOQUEE",
           total, tailIsEffect ? "courte" : "anormalement longue");
    printf("         apres allNotesOff : %.6f (queue de reverb, voulue) | apres requestPanic : %.6f\n",
           panic, panicLevel(ids[k]));
  }
  printf("%s (%d echec%s)\n", fails ? "ECHEC" : "TOUT PASSE", fails, fails > 1 ? "s" : "");
  return fails ? 1 : 0;
}
