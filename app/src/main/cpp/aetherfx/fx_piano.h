#pragma once
// Æther Piano — instrument echantillonne + boost (Kind::Source).
//
// Le sample porte l'attaque et le corps ; le boost produit ce que la banque ne
// peut pas donner, et la mesure a dit pourquoi (tools/pianopack/README.md) :
//
//   - la banque n'est PAS bouclable (correlation max 0,66..0,83 : les partiels
//     decroissent a des vitesses differentes, aucun point stationnaire).  Chaque
//     region fait 2,5 s et la QUEUE est produite ici.
//   - tau2 (queue lente, aftersound de Weinreich) n'est pas mesurable dans les
//     samples (la queue depasse la duree du fichier dans les graves, passe sous
//     le bruit dans les aigus).  Il est donc MODELISE : c'est le role de
//     l'enveloppe de relachement.
//   - tau1 (chute rapide) EST mesure : 0,478 s a C4, x0,59 par octave.
//   - l'inharmonicite B est mesuree : B = 2,29e-4 * 2^(0,1045*(midi-60)/12).
//     Les resonateurs sont accordes avec elle : c'est ce qui separe un piano
//     d'un orgue qui joue la meme note.

#include <cmath>
#include <cstring>
#include <vector>
#include <algorithm>

#include "fx_source.h"

namespace aetherfx {

// ---------------------------------------------------------------------------
// Lecteur de pack : "PIAP" + regions 24 o + ADPCM 4 bits STEREO entrelace.
// Meme disposition que le pack sax (fx_sax_samples.h), en stereo et avec 4
// couches de dynamique.  Le payload est copie : le tampon de l'appelant peut
// etre libere pendant qu'une note sonne (use-after-free constate cote sax).
// ---------------------------------------------------------------------------
class PianoSampleReader {
 public:
  struct Region {
    int root = 60;
    int layer = 0;                    // 0 = PP .. 4 = FF (5 couches dans la banque)
    uint32_t offset = 0, frames = 0, loopStart = 0, loopEnd = 0;
    float tuneCents = 0.f;
  };

  static constexpr int kStep[89] = {
      7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
      50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230,
      253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
      1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
      3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
      11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
      32767};
  static constexpr int kIdx[8] = {-1, -1, -1, -1, 2, 4, 6, 8};

  bool load(const uint8_t* bytes, size_t size) {
    clear();
    if (size < 28 || std::memcmp(bytes, "PIAP", 4) != 0) return false;
    uint32_t rate = 0, count = 0, pcmLen = 0, recLen = 0;
    std::memcpy(&rate, bytes + 8, 4);
    std::memcpy(&count, bytes + 12, 4);
    std::memcpy(&pcmLen, bytes + 16, 4);
    std::memcpy(&recLen, bytes + 20, 4);
    if (28ull + recLen + pcmLen > size || count == 0 || count > 4096) return false;
    rate_ = static_cast<int>(rate);
    for (uint32_t i = 0; i < count; ++i) {
      const uint8_t* r = bytes + 28 + i * 24;
      Region g;
      g.root = r[0];
      g.layer = r[1];
      std::memcpy(&g.offset, r + 4, 4);
      std::memcpy(&g.frames, r + 8, 4);
      std::memcpy(&g.loopStart, r + 12, 4);
      std::memcpy(&g.loopEnd, r + 16, 4);
      std::memcpy(&g.tuneCents, r + 20, 4);
      if (g.frames < 64) continue;
      // stereo entrelace : 2 echantillons par frame, 2 nibbles par octet
      if ((static_cast<uint64_t>(g.offset) + g.frames) * 2ull > pcmLen * 2ull) continue;
      regions_.push_back(g);
    }
    pcm_.assign(bytes + 28 + recLen, bytes + 28 + recLen + pcmLen);
    return !regions_.empty();
  }

  void clear() { regions_.clear(); pcm_.clear(); cache_.clear(); rate_ = 22050; }
  bool loaded() const { return !regions_.empty(); }
  int rate() const { return rate_; }

    size_t regionCount() const { return regions_.size(); }

  /** Region la plus proche en hauteur, en preferant la couche demandee. */
  const Region* pick(int note, int velocity) const {
    if (regions_.empty()) return nullptr;
    const int want = std::min(4, std::max(0, velocity * 5 / 128));
    const Region* best = nullptr;
    int bestCost = 1 << 30;
    for (const Region& g : regions_) {
      const int cost = std::abs(g.root - note) * 8 + std::abs(g.layer - want);
      if (cost < bestCost) { bestCost = cost; best = &g; }
    }
    return best;
  }

  /**
   * PCM stereo entrelace d'une region, decode une fois et garde.
   *
   * Le pack sax decodait la region entiere a chaque noteOn dans un tampon
   * unique : acceptable pour un instrument monophonique, ruineux pour un piano
   * polyphonique qui repete la meme note.  Les regions sont immuables, donc on
   * les decode une fois et on garde les dernieres utilisees (LRU bornee).
   * Decoder paresseusement dans la boucle de rendu serait quadratique : le
   * predicteur doit rejouer depuis le debut de la region a chaque acces.
   */
  const std::vector<int16_t>* decoded(const Region* g) {
    if (!g) return nullptr;
    const size_t key = static_cast<size_t>(g - regions_.data());
    for (size_t i = 0; i < cache_.size(); ++i) {
      if (cache_[i].first == key) {
        std::rotate(cache_.begin(), cache_.begin() + i, cache_.begin() + i + 1);
        return &cache_.back().second;
      }
    }
    std::vector<int16_t> out(static_cast<size_t>(g->frames) * 2);
    decodeRegion(*g, out.data());
    if (cache_.size() >= kCacheSlots) cache_.erase(cache_.begin());
    cache_.emplace_back(key, std::move(out));
    return &cache_.back().second;
  }

 private:
  static constexpr size_t kCacheSlots = 10;

  void decodeRegion(const Region& g, int16_t* dst) const {
    int predL = 0, idxL = 0, predR = 0, idxR = 0;
    const size_t first = static_cast<size_t>(g.offset) * 2;   // index de nibble
    for (uint32_t i = 0; i < g.frames; ++i) {
      const size_t nL = first + static_cast<size_t>(i) * 2;
      const size_t nR = nL + 1;
      auto nib = [&](size_t k) {
        const uint8_t b = pcm_[k >> 1];
        return static_cast<int>((k & 1) ? (b >> 4) : (b & 0xF));
      };
      auto step = [](int code, int& pred, int& idx) {
        const int st = kStep[idx];
        int dl = st >> 3;
        if (code & 4) dl += st;
        if (code & 2) dl += st >> 1;
        if (code & 1) dl += st >> 2;
        pred += (code & 8) ? -dl : dl;
        pred = std::max(-32768, std::min(32767, pred));
        idx = std::max(0, std::min(88, idx + kIdx[code & 7]));
        return pred;
      };
      dst[i * 2] = static_cast<int16_t>(step(nib(nL), predL, idxL));
      dst[i * 2 + 1] = static_cast<int16_t>(step(nib(nR), predR, idxR));
    }
  }

  std::vector<Region> regions_;
  std::vector<uint8_t> pcm_;
  std::vector<std::pair<size_t, std::vector<int16_t>>> cache_;
  int rate_ = 22050;
};

// ---------------------------------------------------------------------------
// Resonateurs accordes sur les harmoniques inharmoniques de la note.
// Le sample donne le timbre ; ces resonateurs donnent le corps, la longueur et
// le couplage entre cordes.  Filtres a deux poles, un par harmonique.
// ---------------------------------------------------------------------------
class PianoResonators {
 public:
  static constexpr int kN = 6;

  void setRate(float r) { rate_ = r; }

  void tune(float f0, float B) {
    for (int i = 0; i < kN; ++i) {
      const float n = static_cast<float>(i + 1);
      const float fn = n * f0 * std::sqrt(1.f + B * n * n);
      // Q decroissant avec l'harmonique : les aigus s'amortissent plus vite
      // Q large : a 260 la bande etait si etroite que le resonateur etait a peine
      // excite par le signal, donc RESONANCE ne s'entendait pas.
      const float q = 34.f / (1.f + 0.55f * n);
      const float w = 2.f * static_cast<float>(M_PI) * std::min(fn, 9000.f) / rate_;
      const float r = std::exp(-w / (2.f * q));
      // Normalise a gain de crete UNITE.  Avec le facteur 0,5 d'origine, chaque
      // mode ne pouvait qu'attenuer (creux de -6 dB) : RESONANCE ne faisait
      // qu'assombrir, et les modes ne sonnaient jamais.  Mesure : -0,9 dB dans le
      // grave au lieu d'une vraie resonance.
      b0_[i] = 1.f - r;
      a1_[i] = 2.f * r * std::cos(w);
      a2_[i] = -r * r;
      x1_[i] = x2_[i] = y1_[i] = y2_[i] = 0.f;
    }
  }

  void reset() {
    for (int i = 0; i < kN; ++i) x1_[i] = x2_[i] = y1_[i] = y2_[i] = 0.f;
  }

  float process(float x) {
    float acc = 0.f;
    for (int i = 0; i < kN; ++i) {
      const float y = b0_[i] * (x - x2_[i]) + a1_[i] * y1_[i] + a2_[i] * y2_[i];
      x2_[i] = x1_[i]; x1_[i] = x;
      y2_[i] = y1_[i]; y1_[i] = y;
      acc += y;
    }
    return acc * 0.55f;
  }

 private:
  float b0_[kN]{}, a1_[kN]{}, a2_[kN]{};
  float x1_[kN]{}, x2_[kN]{}, y1_[kN]{}, y2_[kN]{};
  float rate_ = 44100.f;
};

// ---------------------------------------------------------------------------
// Voix : sample stereo + resonateurs + enveloppe + queue modelisee.
// ---------------------------------------------------------------------------
class PianoVoice {
 public:
  void start(const int16_t* pcm, uint32_t frames, int root, float tuneCents,
             int note, float velocity, float sr, float packRate,
             uint32_t loopStart, uint32_t loopEnd, float mic, float space, float tine) {
    pcm_ = pcm; frames_ = frames; pos_ = 0.0;
    loopStart_ = loopStart; loopEnd_ = loopEnd;
    if (loopEnd_ <= loopStart_ || loopEnd_ > frames) loopStart_ = loopEnd_ = 0;
    const float rootF = 440.f * std::pow(2.f, (root - 69) / 12.f)
                        * std::pow(2.f, tuneCents / 1200.f);
    const float f = 440.f * std::pow(2.f, (note - 69) / 12.f);
    // packRate/sr : le pack est a 22050 Hz et la sortie a 44100.  Sans ce facteur
    // chaque note sort une octave trop haut — c'est le son de jouet.
    ratio_ = (rootF > 1e-6f) ? f / rootF * (packRate / sr) : 1.f;
    note_ = note; vel_ = velocity;
    active_ = true; releasing_ = false;
    // Constante de decroissance, calee sur la decroissance MESUREE des sources :
    // mediane ~8,6 dB/s dans le grave, ~8,4 a C4, ~12,9 une octave plus haut.
    // Le facteur (1 + 6*sustainHold) du rendu multiplie tau par 4 (hold = 0,5),
    // d'ou la base divisee d'autant : 0,255 s a C4 donne ~8,5 dB/s.
    // Sans ce calage, une note tenue restait a niveau constant — une corde de
    // guitare, pas un piano.
    // SPACE est la taille de la piece : une grande salle nourrit la corde, donc
    // allonge la decroissance.  Sans lui, les neuf modeles decroissaient tous en
    // 0,17 s et ne se distinguaient plus.
    tau1_ = 0.255f * std::pow(0.833f, static_cast<float>(note - 60) / 12.f) * (0.25f + 2.60f * space);
    // One-pole a 2 kHz : la charniere du shelf.
    // MIC deplace la charniere du shelf : 2000 Hz ferme (charniere basse, timbre
    // sombre) a 4200 Hz ouvert.  Le reglage etait annonce mais jamais lu.
    const float micHz = 2000.f + mic * 2200.f;
    shelfA_ = 1.f - std::exp(-2.f * static_cast<float>(M_PI) * micHz / sr);
    lpL_ = lpR_ = 0.f;
    res_.setRate(sr);
    res_.tune(f, inharmonicityOf(note, tine));
    res_.reset();
    env_ = 0.f; rel_ = 0.f; age_ = 0.f;
  }

  /** B mesuree : 2,29e-4 * 2^(0,1045*(midi-60)/12).  tine multiplie B : une tine
   *  de piano electrique est une barre frappee, pas une corde, donc ses partiels
   *  sont nettement inharmoniques — c'est ce qui « sonne electrique ». */
  static float inharmonicityOf(int note, float tine) {
    return 2.29e-4f * std::pow(2.f, 0.1045f * static_cast<float>(note - 60) / 12.f)
           * (1.f + 24.f * tine);
  }

  void release() { releasing_ = true; }
  void kill() { active_ = false; }
  bool active() const { return active_; }
  int note() const { return note_; }
  float age() const { return age_; }

  void render(float* outL, float* outR, int n, float sr,
              float body, float bright, float width, float releaseSec, float sustainHold,
              bool tine) {
    tine_ = tine;
    if (!active_ || !pcm_) return;
    // 0,5 ms et non 4 : le transitoire de marteau vit dans les 2 premieres ms,
    // une rampe de 4 ms l'efface et le piano perd son attaque.
    const float atk = 1.f / (0.0005f * sr);
    // bright 0 -> -6 dB d'aigus, bright 1 -> +6 dB.
    // Plage +-12 dB (etait +-6) : a +-6 les quatre reglages d'aigu se confondaient.
    const float shelfG = std::exp2((bright - 0.5f) * 4.0f);
    for (int i = 0; i < n; ++i) {
      // Tant que la touche est tenue, on tourne dans la boucle ; au relachement on
      // la quitte et on joue la queue enregistree, qui porte la decroissance reelle.
      if (loopEnd_ > loopStart_ && !releasing_ && pos_ >= loopEnd_) {
        pos_ -= (loopEnd_ - loopStart_);
      }
      const uint32_t i0 = static_cast<uint32_t>(pos_);
      const uint32_t i1 = i0 + 1;
      if (i1 >= frames_) { active_ = false; break; }
      const float fr = static_cast<float>(pos_ - i0);
      const int16_t* p = pcm_ + static_cast<size_t>(i0) * 2;
      const int16_t* q = pcm_ + static_cast<size_t>(i1) * 2;
      float sL = (p[0] + (q[0] - p[0]) * fr) * (1.f / 32768.f);
      float sR = (p[1] + (q[1] - p[1]) * fr) * (1.f / 32768.f);
      pos_ += ratio_;

      if (!releasing_) {
        env_ = std::min(1.f, env_ + atk);
      } else {
        rel_ += 1.f / (std::max(0.05f, releaseSec) * sr);
        if (rel_ >= 1.f) { active_ = false; break; }
      }
      age_ += 1.f / sr;
      const float tail = std::exp(-age_ / (tau1_ * (1.f + 6.f * sustainHold)));
      const float amp = env_ * (releasing_ ? (1.f - rel_) : 1.f) * tail;

      const float mono = (sL + sR) * 0.5f;
      const float res = res_.process(mono) * body;

      // En mode tine, l'echantillon (un piano acoustique) s'efface au profit des
      // resonateurs accordes sur la tine : c'est la barre frappee qu'on entend,
      // pas la corde enregistree.  Sans ce basculement, changer B ne s'entendait
      // pas — les partiels mesures restaient ceux de l'echantillon.
      const float smp = tine_ ? 0.35f : 1.f;
      const float rg  = tine_ ? 3.20f : 0.85f;
      float mL = sL * smp + res * rg;
      float mR = sR * smp + res * rg;

      const float mid = (mL + mR) * 0.5f;
      const float sid = (mL - mR) * 0.5f * width;
      mL = mid + sid;
      mR = mid - sid;

      // Shelf aigu a 2 kHz, +/-6 dB selon la brillance. L'ancien remelange
      // mono/hi ne deplacait le centroide que de 1 % : les presets sonnaient
      // identiques. Ici l'inclinaison du spectre est franche.
      lpL_ += (mL - lpL_) * shelfA_;
      lpR_ += (mR - lpR_) * shelfA_;
      mL = lpL_ + (mL - lpL_) * shelfG;
      mR = lpR_ + (mR - lpR_) * shelfG;

      outL[i] += mL * amp * vel_;
      outR[i] += mR * amp * vel_;
    }
  }

 private:
  const int16_t* pcm_ = nullptr;
  uint32_t frames_ = 0;
  double pos_ = 0.0;
  float ratio_ = 1.f, vel_ = 1.f;
  float env_ = 0.f, rel_ = 0.f, age_ = 0.f, tau1_ = 0.5f;
  uint32_t loopStart_ = 0, loopEnd_ = 0;
  // Shelf aigu par canal : c'est lui qui rend TONE/HAMMER/LID audibles.
  float lpL_ = 0.f, lpR_ = 0.f, shelfA_ = 0.25f;
  bool tine_ = false;
  int note_ = -1;
  bool active_ = false, releasing_ = false;
  PianoResonators res_;
};

// ---------------------------------------------------------------------------
// Moteur.  12 parametres :
//   0 HAMMER  durete de marteau      6 WIDTH  largeur stereo
//   1 TONE    brillance              7 LEVEL  niveau
//   2 RESONANCE couplage resonateurs 8 RELEASE longueur de queue
//   3 LID     couvercle (haut)       9 VEL    sensibilite velocite
//   4 MIC     (non traite)          10 PEDAL  (non traite)
//   5 SPACE   piece                 11 MODEL  choix du modele (ignore ici)
//   5 LID     couvercle (niveau HF) 11 LEVEL  niveau
// ---------------------------------------------------------------------------
class PianoEngine : public SourceFx {
 public:
  static constexpr int kParams = 12;
  static constexpr int kVoices = 12;

  int paramCount() const override { return kParams; }

  void prepare(double sr, int maxBlock) override {
    sr_ = static_cast<float>(sr);
    tmpL_.assign(static_cast<size_t>(std::max(maxBlock, 64)), 0.f);
    tmpR_.assign(static_cast<size_t>(std::max(maxBlock, 64)), 0.f);
    reset();
  }

  void reset() override {
    for (auto& v : voices_) v.kill();
    setMeter(0, 0.f);
  }

  void noteOn(int note, int vel) override {
    if (note < 0 || note > 127) return;
    if (vel <= 0) { noteOff(note); return; }
    if (!reader_.loaded()) return;
    const PianoSampleReader::Region* g = reader_.pick(note, vel);
    const std::vector<int16_t>* pcm = reader_.decoded(g);
    if (!pcm || pcm->empty()) return;
    int slot = -1;
    for (int i = 0; i < kVoices; ++i)
      if (!voices_[i].active()) { slot = i; break; }
    if (slot < 0) {
      slot = 0;
      for (int i = 1; i < kVoices; ++i)
        if (voices_[i].age() < voices_[slot].age()) slot = i;
    }
    // Courbe de velocite : sans elle, toutes les couches sonnent au meme niveau
    // et le choix de couche n'a plus de sens.
    const float v = std::pow(static_cast<float>(vel) / 127.f, 1.f - p(9) * 0.7f);
    // MODEL est enfin lu : index 8 = Electric, dont la corde devient une tine.
    const float tine = (std::lround(p(11) * 8.f) >= 8.f) ? 1.f : 0.f;
    voices_[slot].start(pcm->data(), static_cast<uint32_t>(pcm->size() / 2),
                        g->root, g->tuneCents, note, v, sr_,
                        static_cast<float>(reader_.rate()),
                        loopsOn_ ? g->loopStart : 0u,
                        loopsOn_ ? g->loopEnd : 0u,
                        p(4), p(5), tine);
  }

  void noteOff(int note) override {
    for (auto& v : voices_) if (v.active() && v.note() == note) v.release();
  }

  void allNotesOff() override { for (auto& v : voices_) v.kill(); }

  /** Boucle de sustain active ou non.  Une boucle courte module l'amplitude a sa
   *  frequence de repetition ; la couper supprime la modulation, au prix d'une note
   *  qui s'arrete a la fin de la queue enregistree. */
  void setLoopsEnabled(bool on) { loopsOn_ = on; }

  bool loadSampleBank(const uint8_t* bytes, size_t size) override {
    return reader_.load(bytes, size);
  }
  bool hasSampleBank() const override { return reader_.loaded(); }
  float sampleMix() const override { return reader_.loaded() ? 1.f : 0.f; }

  void process(float* io, int n) override {
    NoDenormals nd;
    bool any = false;
    for (auto& v : voices_) if (v.active()) { any = true; break; }
    if (!any || !reader_.loaded() || n <= 0) {
      std::fill(io, io + 2 * n, 0.f);
      setMeter(0, 0.f);
      return;
    }
    if (static_cast<int>(tmpL_.size()) < n) {
      tmpL_.assign(static_cast<size_t>(n), 0.f);
      tmpR_.assign(static_cast<size_t>(n), 0.f);
    }
    std::fill(tmpL_.begin(), tmpL_.begin() + n, 0.f);
    std::fill(tmpR_.begin(), tmpR_.begin() + n, 0.f);

    // Ordre des parametres (contrat de l'interface) : 0 HAMMER, 1 TONE,
    // 2 RESONANCE, 3 LID, 4 MIC, 5 SPACE, 6 WIDTH, 7 LEVEL, 8 RELEASE,
    // 9 VELOCITY, 10 PEDAL NOISE, 11 MODEL.
    // MIC et PEDAL NOISE ne sont pas encore traites ; MODEL est reserve au choix
    // discret du modele, que le moteur ignore pour l'instant.
    // HAMMER et LID agissent tous deux sur le haut du spectre : un marteau plus dur
    // et un couvercle plus ouvert eclairent le timbre, ce sont bien deux reglages.
    const float body = p(2), width = p(6);
    const float mic = p(4);
    const float tine = (std::lround(p(11) * 8.f) >= 8.f) ? 1.f : 0.f;
    // HAMMER, TONE, LID et MIC sont quatre facons d'eclairer le timbre ; MIC
    // etait annonce mais jamais lu, et les trois autres se confondaient.
    const float bright = std::min(1.f, p(1) * (0.55f + 0.9f * p(0)) + p(3) * 0.25f + (p(4) - 0.5f) * 0.6f);
    const float rel = 0.25f + p(8) * 5.0f;
    // Pas de parametre de maintien dans le contrat : on garde une valeur fixe.
    const float hold = 0.5f;
    for (auto& v : voices_) {
      if (!v.active()) continue;
      v.render(tmpL_.data(), tmpR_.data(), n, sr_, body, bright, width, rel, hold, tine > 0.5f);
    }
    const float lvl = p(7) * (0.6f + 0.4f * p(5));
    // Passe-haut a 5 Hz (R = 1 - 2*pi*fc/sr), sur le signal d'instrument et non sur la
    // sortie : le tampon de sortie peut deja porter le mix d'autres sources.
    const float R = 1.f - 6.2831853f * 5.f / sr_;
    for (int i = 0; i < n; ++i) {
      const float yl = tmpL_[i] - dcxL_ + R * dcyL_;
      dcxL_ = tmpL_[i]; dcyL_ = yl;
      const float yr = tmpR_[i] - dcxR_ + R * dcyR_;
      dcxR_ = tmpR_[i]; dcyR_ = yr;
      io[i * 2] += yl * lvl;
      io[i * 2 + 1] += yr * lvl;
    }
    setMeter(0, std::min(1.f, std::abs(tmpL_[0]) + std::abs(tmpR_[0])));
  }

 private:
  bool loopsOn_ = true;
  PianoSampleReader reader_;
  PianoVoice voices_[kVoices];
  std::vector<float> tmpL_, tmpR_;
  // Bloqueur de continu, un par canal. La source est propre (DC -0,00004) mais la somme
  // des voix et la resonance en font apparaitre : mesure -0,08 sur une prise, soit 5,8 %
  // de la reserve de crete perdus en polarisation, et 80 % de l'energie sous 20 Hz.
  // Un piano ne produit rien sous 27 Hz (A0).
  float dcxL_ = 0.f, dcyL_ = 0.f, dcxR_ = 0.f, dcyR_ = 0.f;
  float sr_ = 44100.f;
};

}  // namespace aetherfx
