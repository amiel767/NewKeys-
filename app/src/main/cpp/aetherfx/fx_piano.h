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
#include "fx_space.h"
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
             uint32_t loopStart, uint32_t loopEnd, float mic, float space, float tine,
             float hammer) {
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
    // Marteau : impulsion de bruit tres courte (2 ms) au moment de la frappe.  C'est
    // elle qui donne le « tac » percussif ; l'echantillon ne la porte pas assez,
    // surtout dans les graves ou l'attaque est noyee dans le corps de la corde.
    hammerAmt_ = 0.25f * hammer * (0.35f + 0.65f * velocity);
    hammerEnv_ = 1.f;
    hammerDecay_ = std::exp(-1.f / (0.0018f * sr));
    hammerNoise_.s = 0x2545F491u ^ (static_cast<uint32_t>(note) * 2654435761u);
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
      // resonateurs accordes sur la tine : c'est la barre frappe, pas la corde
      // enregistree.  Sans ce basculement, changer B ne s'entendait pas — les
      // partiels mesures restaient ceux de l'echantillon.
      //
      // Ce basculement etait calibre pour un piano ACOUSTIQUE filtre en tine.  Depuis
      // que l'electrique a sa propre banque (Wurlitzer EP200), il etouffe justement le
      // son qu'on veut entendre : l'echantillon porte deja la tine.  Mesure sur la
      // banque electrique, resonance 0,15 : l'ancien reglage donnait un centroide de
      // 363 Hz (sourd, « pas un piano electrique ») contre 780 Hz a smp 0,85 / rg 1,5.
      const float smp = tine_ ? 0.85f : 1.f;
      const float rg  = tine_ ? 1.50f : 0.85f;
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
      if (hammerEnv_ > 1e-4f) {
        // Bruit de marteau : large bande, donc il apporte aussi le claquement du
        // mecanisme que la banque a perdu a l'encodage.
        const float hn = hammerNoise_.next() * hammerEnv_ * hammerAmt_;
        outL[i] += hn;
        outR[i] += hn * 0.92f;
        hammerEnv_ *= hammerDecay_;
      }
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
  float hammerAmt_ = 0.f, hammerEnv_ = 0.f, hammerDecay_ = 0.f;
  Noise hammerNoise_;
  int note_ = -1;
  bool active_ = false, releasing_ = false;
  PianoResonators res_;
};

// ---------------------------------------------------------------------------
// Etage studio : c'est lui qui enleve le cote « sec ».
//
// Le rendu des cordes sortait droit au fader.  Un piano enregistre passe toujours
// par un micro (qui a une position et une distance), souvent par un ampli, et
// toujours par un compresseur.  Ces trois etages existaient deja ailleurs dans le
// projet (`fx_shaper.h` pour la forme, `fx_amp.h` pour l'ampli guitare) : on les
// reprend ici, adaptes au piano et branches dans la meme classe, donc sans saut
// d'echantillon entre les etages.
// ---------------------------------------------------------------------------
class PianoStudio {
 public:
  void prepare(double sr) {
    sr_ = static_cast<float>(sr);
    // Cablage du micro : le corps est un peaking grave, la proximite un shelf,
    // l'axe une pente d'aigus, le couvercle une coupure.  mem_ applique le
    // phenomene de proximite (shelf grave) et la perte d'aigus.
    for (int ch = 0; ch < 2; ++ch) { mem_[ch].setLowShelf(sr, 210, 0); mem_[ch].setHighShelf(sr, 3800, 0); }
    air_[0].setHighShelf(sr, 9000, 0); air_[1].setHighShelf(sr, 9000, 0);
    pre_.setHighpass(sr, 28, 0.7);
    rev_.prepare(sr, 1024);
    // Espace : un piano de concert est toujours pris dans une piece.  Sans ce
    // mixage la note s'arrete net, c'est le « sec » signale.
    static const float kRev[7] = {0.62f, 0.52f, 0.16f, 0.42f, 0.75f, 0.30f, 0.55f};
    for (int i = 0; i < 7; ++i) rev_.setNorm(i, kRev[i]);
    // Compresseur : detecteur a deux constantes (attaque / relachement).
    reset();
  }

  void reset() {
    for (auto& b : mem_) b.reset();
    for (auto& b : air_) b.reset();
    for (int i = 0; i < 2; ++i) { drive_[i].reset(); tone_[i].reset(); pres_[i].reset(); }
    pre_.reset(); rev_.reset();
    envF_ = 0.f; envS_ = 0.f; cf_ = -1; chKey_ = -1;
  }

  /**
   * Position et distance du micro, drive d'ampli, punch, piece.
   *
   * Tout est recalcule seulement quand un reglage bouge : les Biquad ont un
   * cout de calcul par echantillon, pas par bloc.
   */
  void set(float pos, float dist, float lid, float drive, float bass, float mid, float treb,
           float pres, float punch, float space, float width) {
    if (std::fabs(pos - cPos_) > 0.002f || std::fabs(dist - cDist_) > 0.002f ||
        std::fabs(lid - cLid_) > 0.002f) {
      cPos_ = pos; cDist_ = dist; cLid_ = lid;
      // Le micro tourne autour du piano.  Sous le couvercle (0) : grave renforce et
      // aigus amortis.  Face aux cordes (1) : brillant, peu de corps.
      const float prox = (1.f - pos) * 9.f;                    // phenomene de proximite
      const float air  = (pos - 0.5f) * 10.f;                  // axe du micro
      const float damp = -dist * 7.f - (1.f - lid) * 5.f;      // distance + couvercle ferme
      for (int ch = 0; ch < 2; ++ch) {
        mem_[ch].setLowShelf(sr_, 210, prox);
        mem_[ch].setHighShelf(sr_, 3800, damp);
      }
      air_[0].setHighShelf(sr_, 9000, air);
      air_[1].setHighShelf(sr_, 9000, air);
      const int key = static_cast<int>(std::lround(drive * 4.f));
      if (key != chKey_) {
        chKey_ = key;
        // Ampli a lampes : chaque cran durcit la pente et rabote le grave, ce qui
        // « punch » le rendu au lieu de simplement l'ecraser.
        const float g = 1.f + key * 1.1f;
        for (int ch2 = 0; ch2 < 2; ++ch2) drive_[ch2].setLowpass(sr_, 12000.f - key * 2600.f, 0.7 + key * 0.12f);
        driveG_ = g;
        // Compensation : sans elle, saturer faisait simplement baisser le niveau de
        // 9 dB au lieu de durcir le timbre.  g^-0,7 rend le gain unitaire a petit
        // signal tout en ecrasant les cretes.
        driveC_ = std::pow(g, -0.7f);
      }
      for (int ch2 = 0; ch2 < 2; ++ch2) {
        tone_[ch2].setLowShelf(sr_, 180, (bass - 0.5f) * 20.f);
        tone_[ch2].setPeaking(sr_, 750, 0.8, (mid - 0.5f) * 16.f);
        tone_[ch2].setHighShelf(sr_, 3200, (treb - 0.5f) * 20.f);
      }
      for (int ch2 = 0; ch2 < 2; ++ch2) pres_[ch2].setHighShelf(sr_, 5500, (pres - 0.5f) * 12.f);
      punchAmt_ = punch;
      spaceAmt_ = space;
      widthAmt_ = width;
    }
  }

  /** Un bloc stereo : mem -> ampli -> tone -> compresseur -> largeur. */
  void process(float* io, int n) {
    if (n <= 0) return;
    // Punch = formateur de transitoire, pas compresseur.  L'attaque d'un piano
    // dure moins de 5 ms : un compresseur a 8 ms de constante la laisse passer et
    // n'agit que sur la tenue, donc il ne « punch » rien.  On separe donc les deux
    // enveloppes : une rapide (l'attaque) et une lente (le corps), et le reglage
    // dose l'ecart.  Positif = attaque mise en avant (punch), negatif = attaque
    // arrondie (adoucir).  C'est le meme reglage dans les deux sens.
    const float aFast = 1.f - std::exp(-1.f / (0.0008f * sr_));
    const float aSlow = 1.f - std::exp(-1.f / (0.060f * sr_));
    for (int i = 0; i < n; ++i) {
      float l = io[2 * i], r = io[2 * i + 1];
      l = pre_.process(0, l); r = pre_.process(1, r);
      l = mem_[0].process(0, l); r = mem_[1].process(1, r);
      l = air_[0].process(0, l); r = air_[1].process(1, r);
      if (driveG_ > 1.001f) {                       // ampli : saturation douce + rabotage
        l = drive_[0].process(0, std::tanh(driveG_ * l) * driveC_);
        r = drive_[1].process(1, std::tanh(driveG_ * r) * driveC_);
      }
      l = tone_[0].process(0, l); r = tone_[1].process(1, r);
      l = pres_[0].process(0, l); r = pres_[1].process(1, r);
      // Deux enveloppes sur le canal le plus fort : un seul detecteur pour les deux
      // canaux, sinon l'image stereo pompe.
      const float pk = std::max(std::fabs(l), std::fabs(r));
      envF_ += aFast * (pk - envF_);
      envS_ += aSlow * (pk - envS_);
      // Rapport d'attaque : > 1 pendant le transitoire, ~1 pendant la tenue.
      const float ratio = envF_ / std::max(envS_, 1e-4f);
      // tanh borne la correction : sans lui un transitoire franc donnait +30 dB.
      // Centre neutre : 0,5 ne change RIEN, 0 adoucit l'attaque, 1 la met en avant.
      // Un reglage qui n'a pas de position neutre oblige a deviner sa valeur de repos.
      const float shape = std::tanh((ratio - 1.f) * (punchAmt_ - 0.5f) * 2.6f);
      l *= 1.f + shape; r *= 1.f + shape;
      if (widthAmt_ != 1.f) { const float m = 0.5f * (l + r), s = 0.5f * (l - r) * widthAmt_; l = m + s; r = m - s; }
      // Limiteur doux : la somme des voix atteignait 1,21 en crete, donc l'etage
      // studio (qui peut ajouter du punch) sortait au-dela de 1.  tanh borne sans
      // creer d'harmoniques audibles sous 0,8.
      io[2 * i] = std::tanh(l); io[2 * i + 1] = std::tanh(r);
    }
    // Reverb en dernier : elle doit entendre le compresseur, sinon elle pompe.
    if (spaceAmt_ > 0.01f) {
      rev_.setNorm(5, 0.10f + 0.55f * spaceAmt_);
      rev_.process(io, n);
    }
  }

  float space() const { return spaceAmt_; }
  void setSpace(float v) { spaceAmt_ = v; }

 private:
  float sr_ = 44100.f;
  Biquad mem_[2], air_[2], pre_, tone_[2], drive_[2], pres_[2];
  SpaceReverb rev_;
  float envF_ = 0.f, envS_ = 0.f, driveG_ = 1.f, driveC_ = 1.f;
  float punchAmt_ = 0.5f, spaceAmt_ = 0.3f, widthAmt_ = 1.f;
  float cPos_ = -1.f, cDist_ = -1.f, cLid_ = -1.f;
  int cf_ = -1, chKey_ = -1;
};

// ---------------------------------------------------------------------------
// Moteur.  16 parametres :
//   0 HAMMER  durete de marteau      8 RELEASE longueur de queue
//   1 TONE    brillance              9 VEL    sensibilite velocite
//   2 RESONANCE couplage resonateurs 10 PEDAL  bruit de pedale
//   3 LID     couvercle              11 DIST   distance du micro
//   4 MIC POS position du micro      12 DRIVE  ampli
//   5 SPACE   piece                  13 PUNCH  formateur de transitoire
//   6 WIDTH   largeur stereo         14 AMP EQ basse/mid/aigu (macro)
//   7 LEVEL   niveau                 15 MODEL  choix du modele (Kotlin l'ajoute en dernier)
// ---------------------------------------------------------------------------
class PianoEngine : public SourceFx {
 public:
  static constexpr int kParams = 16;
  static constexpr int kVoices = 12;

  int paramCount() const override { return kParams; }

  void prepare(double sr, int maxBlock) override {
    sr_ = static_cast<float>(sr);
    tmpL_.assign(static_cast<size_t>(std::max(maxBlock, 64)), 0.f);
    tmpR_.assign(static_cast<size_t>(std::max(maxBlock, 64)), 0.f);
    studio_.prepare(sr);
    reset();
  }

  void reset() override {
    for (auto& v : voices_) v.kill();
    studio_.reset();
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
    const float tine = (std::lround(p(15) * 8.f) >= 8.f) ? 1.f : 0.f;
    voices_[slot].start(pcm->data(), static_cast<uint32_t>(pcm->size() / 2),
                        g->root, g->tuneCents, note, v, sr_,
                        static_cast<float>(reader_.rate()),
                        loopsOn_ ? g->loopStart : 0u,
                        loopsOn_ ? g->loopEnd : 0u,
                        p(4), p(5), tine, p(0));
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
    if (!reader_.loaded() || n <= 0) {
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
    bool any = false;
    for (auto& v : voices_) if (v.active()) { any = true; break; }

    // Ordre des parametres (contrat de l'interface) : 0 HAMMER, 1 TONE,
    // 2 RESONANCE, 3 LID, 4 MIC, 5 SPACE, 6 WIDTH, 7 LEVEL, 8 RELEASE,
    // 9 VELOCITY, 10 PEDAL NOISE, 11 MODEL.
    // MIC et PEDAL NOISE ne sont pas encore traites ; MODEL est reserve au choix
    // discret du modele, que le moteur ignore pour l'instant.
    // HAMMER et LID agissent tous deux sur le haut du spectre : un marteau plus dur
    // et un couvercle plus ouvert eclairent le timbre, ce sont bien deux reglages.
    const float body = p(2), width = p(6);
    const float tine = (std::lround(p(15) * 8.f) >= 8.f) ? 1.f : 0.f;
    tineOn_ = tine > 0.5f;
    // HAMMER, TONE, LID et MIC sont quatre facons d'eclairer le timbre ; MIC
    // etait annonce mais jamais lu, et les trois autres se confondaient.
    // MIC ne figure plus ici : c'est desormais une POSITION, traitee par l'etage
    // studio.  L'y laisser faisait que deplacer le micro changeait aussi la
    // brillance des cordes, donc deux effets pour un seul reglage.
    const float bright = std::min(1.f, p(1) * (0.55f + 0.9f * p(0)) + p(3) * 0.25f);
    const float rel = 0.25f + p(8) * 5.0f;
    // Pas de parametre de maintien dans le contrat : on garde une valeur fixe.
    const float hold = 0.5f;
    for (auto& v : voices_) {
      if (!v.active()) continue;
      v.render(tmpL_.data(), tmpR_.data(), n, sr_, body, bright, width, rel, hold, tine > 0.5f);
    }
    // Etage studio : micro -> ampli -> tone -> compresseur -> espace.  Il tourne
    // meme sans voix active, sinon la queue de reverb serait coupee net.
    studio_.set(p(4), p(11), p(3), p(12), p(14), p(14), p(14), p(14), p(13), p(5), p(6));
    if (any) {
      for (int i = 0; i < n; ++i) { io[2 * i] = tmpL_[i]; io[2 * i + 1] = tmpR_[i]; }
    } else {
      std::fill(io, io + 2 * n, 0.f);
    }
    studio_.process(io, n);
    // Tremolo, apres l'etage studio pour qu'il module le timbre final et non
    // l'echantillon brut.  Profondeur fixe : c'est un trait de l'instrument, pas un
    // reglage — un piano electrique sans tremolo n'en est plus un.
    if (tineOn_) {
      const float dph = 6.2831853f * 5.0f / sr_;
      for (int i = 0; i < n; ++i) {
        const float a = 1.f - 0.22f * (0.5f + 0.5f * std::sin(tremPh_));
        const float b = 1.f - 0.22f * (0.5f + 0.5f * std::sin(tremPh_ + 1.9f));
        io[2 * i] *= a;
        io[2 * i + 1] *= b;
        tremPh_ += dph;
        if (tremPh_ > 6.2831853f) tremPh_ -= 6.2831853f;
      }
    }
    const float lvl = p(7) * (0.6f + 0.4f * p(5));
    // Passe-haut a 5 Hz (R = 1 - 2*pi*fc/sr), sur le signal d'instrument et non sur la
    // sortie : le tampon de sortie peut deja porter le mix d'autres sources.
    const float R = 1.f - 6.2831853f * 5.f / sr_;
    for (int i = 0; i < n; ++i) {
      const float yl = io[i * 2] - dcxL_ + R * dcyL_;
      dcxL_ = io[i * 2]; dcyL_ = yl;
      const float yr = io[i * 2 + 1] - dcxR_ + R * dcyR_;
      dcxR_ = io[i * 2 + 1]; dcyR_ = yr;
      io[i * 2] = yl * lvl;
      io[i * 2 + 1] = yr * lvl;
    }
    setMeter(0, std::min(1.f, std::abs(tmpL_[0]) + std::abs(tmpR_[0])));
  }

 private:
  bool loopsOn_ = true;
  PianoSampleReader reader_;
  PianoVoice voices_[kVoices];
  std::vector<float> tmpL_, tmpR_;
  PianoStudio studio_;
  // Bloqueur de continu, un par canal. La source est propre (DC -0,00004) mais la somme
  // des voix et la resonance en font apparaitre : mesure -0,08 sur une prise, soit 5,8 %
  // de la reserve de crete perdus en polarisation, et 80 % de l'energie sous 20 Hz.
  // Un piano ne produit rien sous 27 Hz (A0).
  float dcxL_ = 0.f, dcyL_ = 0.f, dcxR_ = 0.f, dcyR_ = 0.f;
  // Tremolo de l'electrique.  C'est LE trait qui fait reconnaitre un piano electrique :
  // le Wurlitzer EP200 en a un d'origine, et sans lui la banque sonne comme un piano
  // acoustique clair.  Phase separee par canal : le tremolo d'origine est stereo.
  float tremPh_ = 0.f;
  bool tineOn_ = false;
  float sr_ = 44100.f;
};

}  // namespace aetherfx
