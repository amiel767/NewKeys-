// Banc de l'arpeggiateur, hors Android. g++ -O2 -std=c++17 -I app/src/main/cpp
//
// On ne mesure pas un son : on verifie la SEQUENCE de notes emises, frame par
// frame. C'est la seule facon de prouver l'horloge, le gate et le latch sans
// appareil.
#include "aetherfx/fx_arp.h"
#include <cstdio>
#include <vector>
#include <string>
#include <cmath>
using namespace aetherfx;

static int fails = 0;
static void ck(bool c, const std::string& what, const std::string& got = "") {
  printf("  %s %s%s\n", c ? "OK  " : "ECHEC", what.c_str(), got.empty() ? "" : ("  [" + got + "]").c_str());
  if (!c) fails++;
}

struct Ev { long frame; int note; int vel; bool on; };
struct Harness {
  std::vector<Ev> ev; long pos = 0; ArpEngine a;
  static void cb(void* ctx, int frame, int note, int vel, bool on) {
    auto* h = static_cast<Harness*>(ctx);
    h->ev.push_back({h->pos + frame, note, vel, on});
  }
  void run(int blocks, int framesPerBlock = 128) {
    for (int b = 0; b < blocks; ++b) { a.process(framesPerBlock, &Harness::cb, this); pos += framesPerBlock; }
  }
  std::vector<int> ons() const { std::vector<int> v; for (auto& e : ev) if (e.on) v.push_back(e.note); return v; }
  int soundingAt(long f) const { int n = 0; for (auto& e : ev) if (e.frame <= f) n += e.on ? 1 : -1; return n; }
};

static void setup(ArpEngine& a, ArpRate r, ArpMode m, int oct, float gate, float swing, bool hold) {
  a.prepare(48000.0);
  a.setTempo(120.0);
  a.setParam(ARP_ON, 1);
  a.setParam(ARP_RATE, (float)r / 6.0f + 1e-4f);
  a.setParam(ARP_MODE, (float)m / 6.0f + 1e-4f);
  a.setParam(ARP_OCTAVES, (oct - 1) / 3.999f);
  a.setParam(ARP_GATE, (gate - 0.05f) / 0.95f);
  a.setParam(ARP_SWING, swing / 0.66f);
  a.setParam(ARP_HOLD, hold ? 1 : 0);
  a.setParam(ARP_VELOCITY, 1.0f);
}

int main() {
  // ── 1. l'horloge : 1/16 a 120 BPM = 6000 frames ─────────────────────────
  printf("1. HORLOGE\n");
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true); h.a.noteIn(67, 100, true);
    h.run(200);                                   // 25600 frames
    std::vector<long> t; for (auto& e : h.ev) if (e.on) t.push_back(e.frame);
    ck(t.size() >= 4, "des notes sortent", "n=" + std::to_string(t.size()));
    if (t.size() >= 4) {
      ck(t[1] - t[0] == 6000 && t[2] - t[1] == 6000,
         "pas = 6000 frames exactement (1/16 @120)", std::to_string(t[1] - t[0]));
      ck(h.ev[0].frame == 0, "la premiere note tombe a la frame 0", std::to_string(h.ev[0].frame));
    }
    auto o = h.ons();
    ck(o.size() >= 3 && o[0] == 60 && o[1] == 64 && o[2] == 67, "mode Up monte 60,64,67",
       std::to_string(o[0]) + "," + std::to_string(o[1]) + "," + std::to_string(o[2]));
    ck(o.size() >= 4 && o[3] == 60, "puis reboucle sur 60");
  }

  // ── 2. le gate : 50 % => une note sur deux ──────────────────────────────
  printf("2. GATE\n");
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.run(120);
    long onF = -1, offF = -1;
    for (auto& e : h.ev) { if (e.on && onF < 0) onF = e.frame; if (!e.on && offF < 0) offF = e.frame; }
    ck(onF == 0, "la note s'allume a 0");
    ck(offF == 3000, "elle s'eteint a 3000 (50 % de 6000)", std::to_string(offF));
    ck(h.soundingAt(2999) == 1 && h.soundingAt(3001) == 0, "elle ne sonne plus apres le gate");
  }
  {
    // gate plein + mode CHORD : c'est le seul cas de legato pur. Le motif demande
    // les MEMES notes a chaque pas, donc rien n'est relache et rien n'est re-attaue.
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Chord, 1, 1.0f, 0, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true); h.a.noteIn(67, 100, true);
    h.run(120);
    int offs = 0; for (auto& e : h.ev) if (!e.on) offs++;
    ck(offs == 0, "gate plein + Chord : aucun NoteOff, l'accord tient", std::to_string(offs));
    auto o = h.ons();
    ck(o.size() == 3, "les 3 notes sont attaquees une seule fois", std::to_string(o.size()));
    // une note deja allumee ne doit JAMAIS etre rejouee : re-attaquer coupe sa queue
    std::vector<int> live;
    bool reattack = false;
    for (auto& e : h.ev) {
      if (e.on) { if (std::find(live.begin(), live.end(), e.note) != live.end()) reattack = true; live.push_back(e.note); }
      else { auto it = std::find(live.begin(), live.end(), e.note); if (it != live.end()) live.erase(it); }
    }
    ck(!reattack, "legato : aucune note re-attaquee (pas de clic)");
  }
  {
    // gate plein + mode UP : la note change a chaque pas. Elle DOIT donc etre
    // relachee — l'inverse serait un bug. Ce qui compte est qu'elle ne soit
    // jamais re-attaquee tant qu'elle sonne.
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 1.0f, 0, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true); h.a.noteIn(67, 100, true);
    h.run(120);
    int offs = 0; for (auto& e : h.ev) if (!e.on) offs++;
    ck(offs >= 2, "gate plein + Up : chaque note est relachee quand le pas change", std::to_string(offs));
    std::vector<int> live; bool reatk = false;
    for (auto& e : h.ev) {
      if (e.on) { if (std::find(live.begin(), live.end(), e.note) != live.end()) reatk = true; live.push_back(e.note); }
      else { auto it = std::find(live.begin(), live.end(), e.note); if (it != live.end()) live.erase(it); }
    }
    ck(!reatk, "et jamais re-attaquee tant qu'elle sonne");
    // tant qu'on TIENT l'accord, une note qui sonne est normale : le bilan n'est
    // pas nul. Il ne le devient qu'apres le relachement — c'est ce qu'on verifie.
    int before = 0; for (auto& e : h.ev) before += e.on ? 1 : -1;
    ck(before == 1, "on tient l'accord : une note sonne encore", std::to_string(before));
    h.a.noteIn(60, 0, false); h.a.noteIn(64, 0, false); h.a.noteIn(67, 0, false);
    h.run(20);
    int bal = 0; for (auto& e : h.ev) bal += e.on ? 1 : -1;
    ck(bal == 0, "apres relachement : plus rien allume", std::to_string(bal));
  }
  {
    // gate plein + UNE seule note : elle tient en continu, c'est un drone
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 1.0f, 0, false);
    h.a.noteIn(60, 100, true); h.run(120);
    ck(h.ons().size() == 1, "une seule note au gate plein tient en continu (drone)",
       std::to_string(h.ons().size()));
    ck(h.soundingAt(15000) == 1, "elle sonne encore a la fin");
  }

  // ── 3. la regle qui compte : ne jamais relacher ce qu'on n'a pas joue ───
  printf("3. INTEGRITE DES NOTES\n");
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true); h.a.noteIn(67, 100, true);
    h.run(60);
    h.a.allNotesOff();
    h.run(60);
    std::vector<int> live; // notes encore allumees a la fin
    for (auto& e : h.ev) { if (e.on) live.push_back(e.note); else { auto it = std::find(live.begin(), live.end(), e.note); if (it != live.end()) live.erase(it); } }
    ck(live.empty(), "apres allNotesOff, plus aucune note allumee", "reste " + std::to_string(live.size()));
    // chaque NoteOn doit avoir son NoteOff
    int bal = 0; for (auto& e : h.ev) bal += e.on ? 1 : -1;
    ck(bal == 0, "autant de NoteOn que de NoteOff", std::to_string(bal));
  }
  {
    // couper l'arp EN PLEINE note ne doit pas laisser la note allumee
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.run(10);
    h.a.setParam(ARP_ON, 0);
    h.run(10);
    int bal = 0; for (auto& e : h.ev) bal += e.on ? 1 : -1;
    ck(bal == 0, "couper l'arp relache la note en cours", std::to_string(bal));
  }

  // ── 4. les modes ────────────────────────────────────────────────────────
  printf("4. MODES\n");
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Down, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true); h.a.noteIn(67, 100, true);
    h.run(200);
    auto o = h.ons();
    ck(o.size() >= 4 && o[0] == 67 && o[1] == 64 && o[2] == 60, "Down descend 67,64,60",
       std::to_string(o[0]) + "," + std::to_string(o[1]) + "," + std::to_string(o[2]));
  }
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::UpDown, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true); h.a.noteIn(67, 100, true);
    h.run(200);
    auto o = h.ons();
    // 60 64 67 64 | 60 64 67 64 — les extremes NE se repetent pas
    ck(o.size() >= 5 && o[0]==60 && o[1]==64 && o[2]==67 && o[3]==64 && o[4]==60,
       "UpDown 60,64,67,64,60 (sans repeter les extremes)",
       std::to_string(o[0])+","+std::to_string(o[1])+","+std::to_string(o[2])+","+std::to_string(o[3])+","+std::to_string(o[4]));
  }
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Chord, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true); h.a.noteIn(67, 100, true);
    h.run(40);
    auto o = h.ons();
    ck(o.size() == 3 && o[0]==60 && o[1]==64 && o[2]==67, "Chord joue les 3 ensemble",
       std::to_string(o.size()));
    int bal = 0; for (auto& e : h.ev) bal += e.on ? 1 : -1;
    ck(bal == 0, "et les relache toutes", std::to_string(bal));
  }

  // ── 5. octaves ──────────────────────────────────────────────────────────
  printf("5. OCTAVES\n");
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 3, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true);
    h.run(300);
    auto o = h.ons();
    ck(o.size() >= 6 && o[0]==60 && o[1]==64 && o[2]==72 && o[3]==76 && o[4]==84 && o[5]==88,
       "3 octaves : 60,64,72,76,84,88",
       o.size() >= 6 ? (std::to_string(o[0])+","+std::to_string(o[1])+","+std::to_string(o[2])+","+std::to_string(o[3])+","+std::to_string(o[4])+","+std::to_string(o[5])) : "-");
  }

  // ── 6. swing : la paire garde son total ─────────────────────────────────
  printf("6. SWING\n");
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0.5f, false);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true);
    h.run(300);
    std::vector<long> t; for (auto& e : h.ev) if (e.on) t.push_back(e.frame);
    ck(t.size() >= 5, "des notes sortent", std::to_string(t.size()));
    if (t.size() >= 5) {
      long a = t[1]-t[0], b = t[2]-t[1];
      ck(a > b, "le 1er pas est plus long que le 2e (swing)", std::to_string(a)+" vs "+std::to_string(b));
      ck(std::llabs((a + b) - 12000) <= 1, "la paire fait toujours 12000 frames (tempo intact)",
         std::to_string(a + b));
    }
  }

  // ── 7. latch ────────────────────────────────────────────────────────────
  printf("7. LATCH (HOLD)\n");
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, true);
    h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true);
    h.run(200);
    h.a.noteIn(60, 0, false); h.a.noteIn(64, 0, false);   // on lache tout
    h.run(200);
    auto o = h.ons();
    long afterRelease = 0;
    for (auto& e : h.ev) if (e.on && e.frame > 200 * 128) afterRelease++;
    ck(afterRelease > 0, "les notes continuent apres avoir lache", std::to_string(afterRelease));
    ck(o.size() >= 6, "le motif tourne toujours", std::to_string(o.size()));
    // un nouveau press remplace le motif
    h.a.noteIn(72, 100, true);
    h.run(200);
    int has72 = 0, has60after = 0;
    for (auto& e : h.ev) if (e.on && e.frame > 400 * 128) { if (e.note == 72) has72++; if (e.note == 60) has60after++; }
    ck(has72 > 0, "le nouveau press joue bien 72", std::to_string(has72));
    ck(has60after == 0, "et remplace l'ancien accord (plus de 60)", std::to_string(has60after));
  }
  {
    // latch OFF : lacher relache
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.run(20);
    h.a.noteIn(60, 0, false); h.run(40);
    long after = 0; for (auto& e : h.ev) if (e.on && e.frame > 20 * 128) after++;
    ck(after == 0, "latch off : plus rien apres avoir lache", std::to_string(after));
    int bal = 0; for (auto& e : h.ev) bal += e.on ? 1 : -1;
    ck(bal == 0, "et aucune note ne reste allumee", std::to_string(bal));
  }

  // ── 8. precision : le pas ne depend PAS de la taille du bloc ────────────
  printf("8. INDEPENDANCE DU BLOC\n");
  {
    std::vector<long> ref;
    for (int fb : {64, 128, 256, 512, 1024}) {
      Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, false);
      h.a.noteIn(60, 100, true); h.a.noteIn(64, 100, true); h.a.noteIn(67, 100, true);
      h.run(24000 / fb + 1, fb);
      std::vector<long> t; for (auto& e : h.ev) if (e.on) t.push_back(e.frame);
      if (ref.empty()) ref = t;
      else {
        bool same = t.size() >= ref.size() && std::equal(ref.begin(), ref.end(), t.begin());
        ck(same, "bloc de " + std::to_string(fb) + " : memes frames que le bloc 64",
           t.size() >= 2 ? std::to_string(t[0]) + "/" + std::to_string(t[1]) : "-");
      }
    }
  }

  // ── 9. cas limites ──────────────────────────────────────────────────────
  printf("9. CAS LIMITES\n");
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, false);
    h.run(40);
    ck(h.ev.empty(), "aucune note tenue => silence total", std::to_string(h.ev.size()));
  }
  {
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.run(20);
    h.a.noteIn(60, 100, true);                    // double press
    h.run(60);
    int bal = 0; for (auto& e : h.ev) bal += e.on ? 1 : -1;
    ck(bal == 0, "double press ne deregle rien", std::to_string(bal));
  }
  {
    // une seule note : le motif la repete
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::UpDown, 1, 0.5f, 0, false);
    h.a.noteIn(60, 100, true); h.run(100);
    auto o = h.ons();
    bool all60 = !o.empty() && std::all_of(o.begin(), o.end(), [](int n){ return n == 60; });
    ck(all60, "une seule note : UpDown la repete sans planter", std::to_string(o.size()));
  }
  {
    // note haute + octaves : on ne doit pas depasser 127
    Harness h; setup(h.a, ArpRate::Sixteenth, ArpMode::Up, 4, 0.5f, 0, false);
    h.a.noteIn(120, 100, true); h.run(200);
    bool ok = true; for (int n : h.ons()) if (n > 127) ok = false;
    ck(ok, "aucune note au-dela de 127");
  }

  printf("\n%s (%d echec%s)\n", fails ? "ECHECS" : "TOUT PASSE", fails, fails > 1 ? "s" : "");
  return fails ? 1 : 0;
}
