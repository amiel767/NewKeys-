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

/**
 * Limiteur doux a genou : lineaire sous `knee`, arrondi au-dessus, borne a 1.
 *
 * Un `tanh` applique a tout le signal distordait des le niveau de jeu normal
 * (voir l'etage studio).  Ici la reponse est exactement l'identite tant que le
 * signal reste sous le genou, donc un piano joue a sa crete habituelle (0,31) est
 * inchange ; seul un depassement est arrondi.
 */
inline float softClip(float x, float knee = 0.8f) {
  const float a = std::fabs(x);
  if (a <= knee) return x;
  const float over = (a - knee) / (1.f - knee);
  const float shaped = knee + (1.f - knee) * std::tanh(over);
  return x < 0.f ? -shaped : shaped;
}

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
    if (28ull + recLen + pcmLen > size || count == 0 || count > 8192) return false;
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
    // Couches REELLEMENT presentes, triees.  Un pack peut n'avoir que les indices
    // 0 et 3 (electric.bin) : compter `max+1` = 4 faisait alors adresser des couches
    // vides, et le fondu entre couches tombait a cote.  C'est cette liste, et non
    // le numero de couche, qui indexe la selection.
    for (const Region& g : regions_)
      if (std::find(layers_.begin(), layers_.end(), g.layer) == layers_.end())
        layers_.push_back(g.layer);
    std::sort(layers_.begin(), layers_.end());
    layerCount_ = static_cast<int>(layers_.size());
    pcm_.assign(bytes + 28 + recLen, bytes + 28 + recLen + pcmLen);
    // Hors du thread audio : `load()` tourne sur le thread UI.  Voir preload().
    preload();
    return !regions_.empty();
  }

  void clear() {
    regions_.clear();
    pcm_.clear();
    cache_.clear();
    pre_.clear();
    layers_.clear();
    rate_ = 22050;
    layerCount_ = 1;
  }
  bool loaded() const { return !regions_.empty(); }
  int rate() const { return rate_; }
  int layerCount() const { return layerCount_; }

    size_t regionCount() const { return regions_.size(); }
    size_t preloadedCount() const { return pre_.size(); }
    /** Ecart de niveau faible->fort du pack, mesure au chargement. 0 si inconnu. */
    float layerSpreadDb() const { return layerSpreadDb_; }
    /** RMS mesure de (racine, couche), pour diagnostic. */
    double layerRmsAt(int root, int layer) const {
      return rootLayerRms_[static_cast<size_t>(std::min(127, std::max(0, root))) * 8 +
                           std::min(7, std::max(0, layer))];
    }
    /** Gain de correction de niveau de (racine, couche). Rampe monotone, <= 1. */
    float layerGain(int root, int layer) const {
      const int r = std::min(127, std::max(0, root));
      const int L = std::min(7, std::max(0, layer));
      return rootLayerGain_[static_cast<size_t>(r) * 8 + L];
    }
    /** Crete lineaire du pack, normalisee a 1.0 (mesuree au chargement). */
    float peak() const { return peak_; }
    /** Facteur applique a la lecture pour ramener la crete sous le genou. */
    float normGain() const { return normGain_; }

  /** Region la plus proche en hauteur, en preferant la couche demandee.

   *  La couche est mise a l'echelle sur le nombre de couches DU PACK, pas sur
   *  un 5 code en dur.  Avec 5 en dur et un pack a 16 couches, les 11 couches
   *  du haut n'etaient jamais jouees (le pack dense etait paye sans etre lu).
   */
  const Region* pick(int note, int velocity) const {
    if (regions_.empty()) return nullptr;
    const int n = layerCount_ > 0 ? layerCount_ : 1;
    const int want = layers_[std::min(n - 1, std::max(0, velocity * n / 128))];
    // Couche EXACTE d'abord.  La couche porte le timbre de la frappe, et
    // l'espacement du pack (48 racines pour 88 touches, soit ~1,8 demi-ton)
    // rend l'etirement de hauteur inaudible a cote.
    //
    // L'ancien cout melangeait les deux termes avec un poids de 8 sur la hauteur :
    // `|root - note| * 8 + |layer - want|`.  Une racine a un demi-ton pesait donc
    // 8, ce qui battait n'importe quel ecart de couche.  Sur toute note dont la
    // racine la plus proche n'a PAS la couche demandee, le moteur rejouait la
    // couche 0 quelle que soit la velocite.  Mesure (`piano_pick_trace.cpp`) :
    // 0,0 dB de dynamique sur la note 60 contre 18,7 dB sur la 63 — le clavier
    // repondait « n'importe comment » selon la touche.
    const Region* best = nullptr;
    int bestCost = 1 << 30;
    for (const Region& g : regions_) {
      if (g.layer != want) continue;
      const int cost = std::abs(g.root - note);
      if (cost < bestCost) { bestCost = cost; best = &g; }
    }
    if (best) return best;
    // Aucune region ne porte cette couche : on retombe sur la couche la plus
    // proche, en gardant la hauteur comme critere secondaire.
    bestCost = 1 << 30;
    for (const Region& g : regions_) {
      const int cost = std::abs(g.root - note) * 8 + std::abs(g.layer - want);
      if (cost < bestCost) { bestCost = cost; best = &g; }
    }
    return best;
  }

  /** Region EXACTE d'une couche donnee (racine la plus proche), ou nullptr.
   *  Sert au fondu entre deux couches : on lit les deux regions a la fois. */
  const Region* pickLayer(int note, int layerIndex) const {
    if (layerIndex < 0 || layerIndex >= static_cast<int>(layers_.size())) return nullptr;
    const int layer = layers_[layerIndex];
    const Region* best = nullptr;
    int bestCost = 1 << 30;
    for (const Region& g : regions_) {
      if (g.layer != layer) continue;
      const int cost = std::abs(g.root - note);
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
  /**
   * PCM stereo entrelace d'une region, decode une fois et garde.
   *
   * Rend un shared_ptr et non un pointeur nu : la voix garde ainsi sa region
   * vivante meme si le cache evince l'entree.  Avec un pointeur nu, `erase`
   * detruisait le vecteur pendant qu'une note tenue le lisait encore — memoire
   * liberee, donc une note qui sort « une autre note » au hasard de ce qui a
   * reecrit le bloc.
   */
  std::shared_ptr<const std::vector<int16_t>> decoded(const Region* g) {
    if (!g) return nullptr;
    const size_t key = static_cast<size_t>(g - regions_.data());
    // Pre-decode : aucune allocation, aucun decodage sur le thread audio.
    for (size_t i = 0; i < pre_.size(); ++i)
      if (pre_[i].first == key) return pre_[i].second;
    for (size_t i = 0; i < cache_.size(); ++i) {
      if (cache_[i].first == key) {
        // Le coup se deplace en QUEUE, la ou l'appelant le lit.  L'ancien
        // rotate(begin, begin+i, begin+i+1) le mettait en TETE, donc
        // &cache_.back() rendait une AUTRE region et la voix jouait le mauvais
        // echantillon : +307 cents mesure sur A4 (trois demi-tons, l'ecart entre
        // les racines du pack).
        std::rotate(cache_.begin() + i, cache_.begin() + i + 1, cache_.end());
        return cache_.back().second;
      }
    }
    auto out = std::make_shared<std::vector<int16_t>>(static_cast<size_t>(g->frames) * 2);
    decodeRegion(*g, out->data());
    if (cache_.size() >= kCacheSlots) cache_.erase(cache_.begin());
    cache_.emplace_back(key, out);
    return out;
  }

 private:
  static constexpr size_t kCacheSlots = 10;

  /**
   * Budget du pre-decodage, en Mo de PCM int16.
   *
   * Mesure du cout de decodage dans `noteOn` (cache MISS) : mediane 0,00 ms mais
   * p90 1,24 ms et **max 1,47 ms** sur x86, pour un bloc audio de 5,80 ms.  Or
   * les notes arrivent par la file `EngineMidiEvent`, drainee SUR le thread
   * audio : une nouvelle note payait donc son decodage dans le budget du bloc.
   * Un accord ou une gamme enchaine les MISS (le cache ne tient que 10 regions
   * pour 68), et 3-4 notes suffisent a depasser le bloc.  Sur ARM, plus lent,
   * c'est un trou audio audible — la note qui « saute ».
   *
   * Le pack piano (68 regions, 9,95 Mo ADPCM) tient entierement dans 48 Mo.
   * Un pack plus gros (le pack electrique en preparera un) degrade proprement :
   * on pre-decode les regions les plus proches de C4 en premier, les autres
   * passent par le cache LRU.
   */
  static constexpr size_t kPreloadBudgetSamples = 24u * 1024u * 1024u;  // 48 Mo int16

  /** Decode une fois, au chargement, les regions les plus utiles. */
  void preload() {
    pre_.clear();
    layerRms_.assign(8, 0.0);
    layerBest_.assign(8, 1 << 30);
    std::vector<size_t> order(regions_.size());
    for (size_t i = 0; i < order.size(); ++i) order[i] = i;
    // Le clavier se joue surtout autour du centre : on sert d'abord les racines
    // les plus proches de C4 (60), puis on elargit.
    std::sort(order.begin(), order.end(), [&](size_t a, size_t b) {
      const int da = std::abs(regions_[a].root - 60);
      const int db = std::abs(regions_[b].root - 60);
      if (da != db) return da < db;
      return regions_[a].layer < regions_[b].layer;
    });
    size_t used = 0;
    for (size_t idx : order) {
      const Region& g = regions_[idx];
      const size_t need = static_cast<size_t>(g.frames) * 2;
      if (used + need > kPreloadBudgetSamples) continue;
      auto out = std::make_shared<std::vector<int16_t>>(need);
      decodeRegion(g, out->data());
      measureLayer(g, *out);
      pre_.emplace_back(idx, std::move(out));
      used += need;
    }
    // Ecart de niveau entre la couche la plus faible et la plus forte, a hauteur
    // egale.  C'est LUI qui porte la dynamique de l'instrument : un pack normalise
    // par couche fait ~19 dB, le pack acoustique livre n'en fait que 2,2 (mesure
    // `tools/aetherfx/piano_layer_check.cpp`), donc la velocite ne changeait
    // quasiment pas la sonie et le piano sonnait « ecrase ».
    double lo = 0, hi = 0;
    for (double r : layerRms_) {
      if (r <= 0) continue;
      if (lo <= 0 || r < lo) lo = r;
      if (r > hi) hi = r;
    }
    layerSpreadDb_ = (lo > 0 && hi > 0) ? static_cast<float>(20.0 * std::log10(hi / lo)) : 0.f;

    // Toutes les regions sont ramenees au MEME niveau de reference.  Le passage
    // d'une couche a l'autre ne change plus le NIVEAU, seulement le TIMBRE.
    //
    // C'est la seule facon de supprimer le « saut brutal a forte velocite » signale.
    // `pick()` choisit UNE region, et dans ces packs une couche n'est PAS repartie
    // sur les memes racines que les autres : la couche 0 de la note 60 est enregistree
    // a la racine 60, les couches 1..7 a la racine 59.  Changer de couche change donc
    // AUSSI de racine, donc de niveau enregistre : mesure 9,1 dB de saut d'une
    // velocite a la suivante sur `electric.bin` (couches espacees de 19 dB) et 14 dB
    // sur `piano.bin`.  Une correction par (racine, couche) ne pouvait rien y faire :
    // la cible doit etre la MEME pour toutes les regions.
    //
    // La cible est le niveau RMS (ENERGIE) moyen des regions du pack : c'est lui qui
    // fait la SONIE perçue d'une note tenue.  Normaliser sur la crete a ete mesure
    // PIRE (les crêtes de couches different surtout par leur attaque, ce qui ne
    // s'entend pas comme « plus fort » : la marche residuelle montait a 5 dB au lieu
    // de 2).  L'energie seule donne la courbe la plus lisse.  Gains bornes 0,05..20 :
    // une region quasi silencieuse n'est pas amplifiee jusqu'au bruit de fond.
    rootLayerGain_ = std::vector<float>(128 * 8, 1.f);
    {
      // Normalisation PAR COUCHE, avec RE-ESPACEMENT REGULIER.
      //
      // Deux problemes mesures, une seule correction :
      //
      // 1. Une cible UNIQUE pour toutes les couches (version precedente, 0,142)
      //    ecrasait la dynamique : le sustain valait 0,00 dB de la velocite 20 a
      //    127, avec des inversions (frapper plus fort donnait MOINS de son).
      //    L'ecart ENTRE couches est ce qui porte la dynamique du clavier ; il faut
      //    le conserver.
      //
      // 2. Les enregistrements d'un pack n'ont pas des pas de dynamique EGaux :
      //    mesure sur `piano.bin`, +0,6 dB de la couche 5 a la 6, puis +5,0 dB de
      //    la 6 a la 7.  Suivre ces niveaux bruts fait « sauter » la sonie a la
      //    frontiere d'une couche (le « ca saute a forte velocite »).
      //
      // On garde donc l'ECART TOTAL du pack (couche faible -> forte, ~20 dB) mais
      // on le repartit GEOMETRIQUEMENT : chaque couche est posee a un pas de dB
      // constant.  A l'interieur d'une couche, les racines sont alignees sur la
      // cible de la couche (le moteur change de racine en changeant de couche, donc
      // un ecart residuel ferait un saut).
      double layerMean[8];
      bool layerHas[8];
      for (int L = 0; L < 8; ++L) {
        double e = 0.0;
        int n = 0;
        for (int root = 0; root < 128; ++root) {
          const double r = rootLayerRms_[static_cast<size_t>(root) * 8 + L];
          if (r > 0.0) { e += r * r; ++n; }
        }
        layerHas[L] = n > 0;
        layerMean[L] = n > 0 ? std::sqrt(e / static_cast<double>(n)) : 0.0;
      }
      int first = -1, last = -1;
      double lo = 0.0, hi = 0.0;
      for (int L = 0; L < 8; ++L) {
        if (!layerHas[L]) continue;
        if (first < 0) first = L;
        last = L;
        if (lo <= 0.0 || layerMean[L] < lo) lo = layerMean[L];
        if (layerMean[L] > hi) hi = layerMean[L];
      }
      const int nl = last - first + 1;
      for (int L = 0; L < 8; ++L) {
        if (!layerHas[L]) continue;
        const double target =
            (nl > 1 && lo > 0.0 && hi > lo)
                ? lo * std::pow(hi / lo, static_cast<double>(L - first) / (nl - 1))
                : layerMean[L];
        for (int root = 0; root < 128; ++root) {
          const size_t k = static_cast<size_t>(root) * 8 + L;
          const double r = rootLayerRms_[k];
          if (r <= 0.0) continue;
          rootLayerGain_[k] = static_cast<float>(std::min(20.0, std::max(0.05, target / r)));
        }
      }
    }

    // Crete APRES correction de couche : les gains peuvent depasser 1 (couche
    // faible remontee), donc mesurer la crete brute du pack sous-estimerait la
    // sortie reelle.  Le constructeur (`build_sf2.py`) fait `clip(round(l*32767))`
    // sans normalisation, donc la couche forte sort a 1,000 sur TOUS les packs.  Le
    // moteur multiplie ensuite par la courbe de velocite : 1,0 x 1,0 ecrete, et la
    // somme des voix depasse encore.  On ramene la crete a 0,85, ce qui laisse
    // 1,4 dB de marge sous le genou du limiteur (0,8) meme sur un accord fort.
    double peak = 0.0;
    for (const auto& kv : pre_) {
      const Region& g = regions_[kv.first];
      const float lg = layerGain(g.root, g.layer);
      const auto& pcm = *kv.second;
      for (size_t i = 0; i < pcm.size(); ++i)
        peak = std::max(peak, std::abs(static_cast<double>(pcm[i])) / 32768.0 * lg);
    }
    peak_ = static_cast<float>(peak);
    normGain_ = (peak_ > 0.85f) ? (0.85f / peak_) : 1.f;
  }

  /**
   * RMS d'une region, pour deux usages : le niveau de sa couche (dynamique
   * globale, mesuree pres de C4) et le niveau de son couple (racine, couche),
   * qui sert a reparer une dynamique inversee.
   *
   * La fenetre est le DEBUT de la note (0,4 s), pas la region entiere.  Un piano
   * s'entend sur son attaque et son debut de tenue ; une queue longue gonfle la
   * moyenne sans rien changer au niveau percu.  Mesurer la region entiere faisait
   * croire a deux couches egales alors que l'une decroit deux fois plus vite —
   * c'est ce qui laissait un saut de -3,8 dB sur `ep_ballad` apres correction.
   */
  void measureLayer(const Region& g, const std::vector<int16_t>& pcm) {
    if (g.layer < 0 || g.layer >= 8 || pcm.empty()) return;
    const size_t frames = pcm.size() / 2;
    const size_t window = std::min<size_t>(frames, static_cast<size_t>(0.25 * rate_));
    double s = 0;
    for (size_t i = 0; i < window; ++i) {
      const double l = pcm[2 * i] / 32768.0;
      const double r = pcm[2 * i + 1] / 32768.0;
      s += l * l + r * r;
    }
    const double rms = std::sqrt(s / std::max<size_t>(1, window * 2));
    if (g.root >= 0 && g.root < 128)
      rootLayerRms_[static_cast<size_t>(g.root) * 8 + g.layer] = rms;
    const int cost = std::abs(g.root - 60);
    if (cost >= layerBest_[g.layer]) return;
    layerBest_[g.layer] = cost;
    layerRms_[g.layer] = rms;
  }

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
#ifdef PIANO_NO_LOOP_FIX
    (void)g;
#else
    flattenLoop(dst, g);
#endif
  }

  /** Aplatit le niveau de la boucle pour que le raccord ne « re-attaque » pas.
   *
   * Le docstring de `build_newstage.py` annonce compenser la decroissance de la
   * boucle, mais le code ne le fait jamais (`faded` vaut 0 sur les 68 regions) et
   * le lecteur reboucle SANS fondu ni gain (contrairement au lecteur sax, qui en a
   * un). Or 38 des 68 regions posent leur boucle dans la partie qui DECROIT :
   * mesure (`tools/pianopack/loop_decay.py`) -2,2 dB/tour en median, jusqu'a
   * -25 dB. L'enveloppe descend donc puis REMONTE d'autant a chaque tour : c'est
   * le « faux sustain » entendu comme un echo.
   *
   * On ramene la boucle au niveau de son premier bloc (gain = RMS_tete / RMS_local,
   * borne pour ne pas souffler le plancher ADPCM), puis on fond le raccord sur une
   * PERIODE.  Une periode exacte est ce qui rend le fondu invisible : la boucle est
   * un nombre entier de periodes, donc le dernier echantillon fondu retombe sur la
   * phase qui precede `loopStart` et le rebouclage reste continu (un fondu plus
   * court ou plus long laisse une marche, cf. le commentaire du lecteur sax).
   * Fait au decodage, hors du thread audio. */
  void flattenLoop(int16_t* dst, const Region& g) const {
    const uint32_t ls = g.loopStart, le = g.loopEnd;
    if (le <= ls || le > g.frames) return;
    const uint32_t L = le - ls;
    // Fenetres sur la pile : `decoded()` peut etre appele sur le thread audio (cache MISS),
    // et cette fonction ne doit rien allouer la.  Au-dela de kMaxWin fenetres on elargit la
    // fenetre plutot que d'allouer.
    constexpr uint32_t kMaxWin = 512;
    uint32_t win = std::max<uint32_t>(1u, static_cast<uint32_t>(0.010 * rate_));
    if (L / win > kMaxWin) win = L / kMaxWin + 1;
    const uint32_t nwin = std::max<uint32_t>(2u, L / win);
    float raw[kMaxWin], gs[kMaxWin];
    for (uint32_t k = 0; k < nwin; ++k) {
      const uint32_t a = ls + static_cast<uint32_t>(static_cast<uint64_t>(k) * L / nwin);
      const uint32_t b = ls + static_cast<uint32_t>(static_cast<uint64_t>(k + 1) * L / nwin);
      double s = 0;
      for (uint32_t i = a; i < b; ++i) {
        const double l = dst[2 * i] / 32768.0, r = dst[2 * i + 1] / 32768.0;
        s += l * l + r * r;
      }
      raw[k] = static_cast<float>(std::sqrt(s / std::max<uint32_t>(1u, (b - a) * 2)));
    }
    const float head = std::max(1e-6f, raw[0]);
    for (uint32_t k = 0; k < nwin; ++k)
      gs[k] = std::min(12.f, head / std::max(1e-6f, raw[k]));
    // Lissee : un gain par fenetre brute module l'enveloppe au rythme des fenetres.
    float smoothed[kMaxWin];
    for (uint32_t k = 0; k < nwin; ++k)
      smoothed[k] = (k == 0 || k + 1 == nwin)
                        ? gs[k]
                        : 0.25f * gs[k - 1] + 0.5f * gs[k] + 0.25f * gs[k + 1];
    for (uint32_t k = 0; k < nwin; ++k) {
      const uint32_t a = ls + static_cast<uint32_t>(static_cast<uint64_t>(k) * L / nwin);
      const uint32_t b = ls + static_cast<uint32_t>(static_cast<uint64_t>(k + 1) * L / nwin);
      const float g0 = smoothed[k];
      const float g1 = (k + 1 < nwin) ? smoothed[k + 1] : smoothed[0];
      const uint32_t span = std::max<uint32_t>(1u, b - a);
      for (uint32_t i = a; i < b; ++i) {
        const float gg = g0 + (g1 - g0) * (static_cast<float>(i - a) / static_cast<float>(span));
        for (int c = 0; c < 2; ++c) {
          const float v = dst[2 * i + c] * gg;
          dst[2 * i + c] = static_cast<int16_t>(std::max(-32768.f, std::min(32767.f, v)));
        }
      }
    }
    // Fondu du raccord, sur une periode, vers la phase QUI PRECEDE loopStart — jamais
    // vers la tete du loop.  Fondre vers x[ls..ls+xf) laisse le dernier echantillon
    // fondu xf echantillons APRES loopStart alors que le rebouclage repart de
    // loopStart : chaque tour saute de ce que la forme d'onde fait en xf
    // echantillons (le clic periodique mesure a 0,57 cote sax).  Comme la boucle
    // fait un nombre entier de periodes, la phase d'avant loopStart est celle qui
    // suit reellement x[le-1].
    const float f0 = 440.f * std::pow(2.f, (g.root - 69) / 12.f);
    uint32_t xf = static_cast<uint32_t>(std::max(1.f, rate_ / std::max(20.f, f0)));
    xf = std::min(xf, std::min(L / 2, ls));   // il faut de la place AVANT loopStart
    for (uint32_t i = 0; i < xf; ++i) {
      const float u = static_cast<float>(i + 1) / static_cast<float>(xf + 1);
      for (int c = 0; c < 2; ++c) {
        const float tail = dst[2 * (le - xf + i) + c];
        const float pre = dst[2 * (ls - xf + i) + c];
        dst[2 * (le - xf + i) + c] =
            static_cast<int16_t>(std::max(-32768.f, std::min(32767.f, tail * (1.f - u) + pre * u)));
      }
    }
  }

  std::vector<Region> regions_;
  std::vector<int> layers_;   // indices de couche reellement presents, tries
  std::vector<uint8_t> pcm_;
  std::vector<std::pair<size_t, std::shared_ptr<const std::vector<int16_t>>>> cache_;
  // Regions decodees au chargement, jamais evincees : le thread audio n'y fait
  // aucune allocation.  Triees par cle croissante, donc la recherche lineaire
  // s'arrete tot ; a 68 regions elle est de toute facon negligeable.
  std::vector<std::pair<size_t, std::shared_ptr<const std::vector<int16_t>>>> pre_;
  int rate_ = 22050;
  int layerCount_ = 1;   // nombre de couches reellement presentes dans le pack
  std::vector<double> layerRms_;   // RMS a C4 par couche, mesuree au chargement
  std::vector<int> layerBest_;     // distance de hauteur de la region mesuree
  float layerSpreadDb_ = 0.f;      // ecart faible->fort du pack, en dB
  // RMS de chaque (racine, couche), puis gain qui ramene chaque region au niveau
  // de reference du pack.  Table plate : 128 racines x 8 couches.
  std::vector<double> rootLayerRms_ = std::vector<double>(128 * 8, 0.0);
  std::vector<float> rootLayerGain_ = std::vector<float>(128 * 8, 1.f);
  float peak_ = 0.f;       // crete mesuree du pack, normalisee a 1.0
  float normGain_ = 1.f;   // facteur de lecture pour rester sous le genou
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
  /** Bend courant, lisse.  Le piano ne l'avait pas du tout : `pitchBend` tombait
   *  dans l'implementation vide de SourceFx, donc la roue ne faisait rien sur un
   *  instrument Æther, ou sautait par demi-ton quand le moteur reaccordait la voix. */
  void setBend(float b, int semis) { bendTarget_ = b * static_cast<float>(semis); }

  /** Une couche prete a lire : le PCM et ce qu'il faut pour le parcourir. */
  struct Src {
    std::shared_ptr<const std::vector<int16_t>> pcm;
    uint32_t frames = 0, loopStart = 0, loopEnd = 0;
    double pos = 0.0;
    float ratio = 1.f;   // avance par echantillon (hauteur + correction pack/sortie)
    float gain = 1.f;    // egalisation de niveau de la couche (au chargement)
  };

  /** `a` = couche de la velocite, `b` = couche au-dessus, `blend` = part de `b`.
   *  Lire les deux et les melanger est ce qui supprime la marche de niveau : la
   *  couche change d'un coup, donc sans recouvrement le niveau sautait (mesure
   *  jusqu'a 4 dB a la frontiere 63->64 d'un pack electrique). */
  void start(const Src& a, const Src& b, float blend,
             int note, float velocity, float sr,
             float mic, float space, float tine,
             float hammer, float hammerNoise, float keyNoise, float damperNoise,
             float gain) {
    gain_ = gain;
    damperAmt_ = damperNoise;
    srcA_ = a; srcB_ = b;
    blend_ = (b.pcm && b.frames > 1) ? std::min(1.f, std::max(0.f, blend)) : 0.f;
    note_ = note; vel_ = velocity;
    active_ = true; releasing_ = false; keyDown_ = true;
    const float f = 440.f * std::pow(2.f, (note - 69) / 12.f);
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
    bendNow_ = bendTarget_;
    bendCoef_ = 1.f - std::exp(-1.f / (0.012f * sr));
    // Marteau : impulsion de bruit au moment de la frappe.  Elle n'est pas blanche.
    // Le bruit blanc pur donnait un « tsss » metallique : une frappe reelle est un
    // « toc » dont l'energie est centree sur la resonance du marteau, entre 1,5 et
    // 4 kHz selon la hauteur.  On filtre donc le bruit dans cette bande, avec une
    // queue plus longue dans le grave (le feutre amortit lentement) et plus courte
    // dans l'aigu (frappe seche).  Deux bandes plutot qu'une : un passe-bande seul
    // reste « souffle », le passe-bas ajoute le corps de la frappe.
    const float hammerHz = std::max(1400.f, 3400.f - (note - 21) * 12.f);
    const float hDec = (note < 60) ? 0.0026f : 0.0013f;
    hpA_ = 1.f - std::exp(-2.f * static_cast<float>(M_PI) * (hammerHz * 0.55f) / sr);
    lpA_ = 1.f - std::exp(-2.f * static_cast<float>(M_PI) * (hammerHz * 1.85f) / sr);
    hpL_ = hpR_ = lpL2_ = lpR2_ = 0.f;
    // Le filtrage retire beaucoup d'energie (bande 1,6-5,4 kHz au lieu de tout le
    // spectre) : sans compensation l'attaque perdait 7,7 dB et le piano redevenait
    // mou.  On ramene la frappe au niveau percu d'avant, mais avec un « toc » a la
    // place du « tsss ».
    //
    // Trois bruits SEPARES, avec des graines independantes : le marteau (la frappe,
    // depend de la durete), la touche (le doigt + la mecanique, depend du toucher)
    // et l'etouffoir (le feutre qui retombe, seulement au relachement).  Un seul
    // generateur partage les faisait « sonner » ensemble a chaque note, ce qui se
    // joue comme un clic mecanique unique et non comme un piano.
    hammerAmt_ = 0.42f * hammerNoise * (0.12f + 0.88f * velocity * velocity);
    hammerEnv_ = 1.f;
    hammerDecay_ = std::exp(-1.f / (hDec * sr));
    noiseHammer_.s = 0x2545F491u ^ (static_cast<uint32_t>(note) * 2654435761u) ^ 0x9E3779B9u;

    keyAmt_ = 0.075f * keyNoise * (0.20f + 0.80f * velocity * velocity);
    keyEnv_ = 1.f;
    keyDecay_ = std::exp(-1.f / (0.011f * sr));
    keyHz_ = std::max(900.f, 2200.f - (note - 21) * 7.f);
    kA_ = 1.f - std::exp(-2.f * static_cast<float>(M_PI) * (keyHz_ * 0.6f) / sr);
    kB_ = 1.f - std::exp(-2.f * static_cast<float>(M_PI) * (keyHz_ * 1.5f) / sr);
    kL_ = kR_ = 0.f;
    noiseKey_.s = 0x85EBCA6Bu ^ (static_cast<uint32_t>(note) * 2246822519u) ^ 0x27D4EB2Fu;

    damperEnv_ = 0.f;
    damperDecay_ = std::exp(-1.f / (0.028f * sr));
    noiseDamper_.s = 0xC2B2AE35u ^ (static_cast<uint32_t>(note) * 3266489917u) ^ 0x165667B1u;
    dL_ = dR_ = 0.f;
  }

  /** B mesuree : 2,29e-4 * 2^(0,1045*(midi-60)/12).  tine multiplie B : une tine
   *  de piano electrique est une barre frappee, pas une corde, donc ses partiels
   *  sont nettement inharmoniques — c'est ce qui « sonne electrique ». */
  static float inharmonicityOf(int note, float tine) {
    return 2.29e-4f * std::pow(2.f, 0.1045f * static_cast<float>(note - 60) / 12.f)
           * (1.f + 24.f * tine);
  }

  void release() {
    releasing_ = true;
    // Le bruit de touche s'arrete au relachement : la mecanique a fini son geste.
    // Le laisser courir ferait entendre un « clic » isole apres la note eteinte.
    keyEnv_ = 0.f;
    // Le feutre de l'etouffoir retombe : bruit court, decale sous la touche.  C'est
    // le « damper noise » que Pianoteq expose, et il n'arrive QUE sur le relachement.
    damperEnv_ = 1.f;
  }
  void kill() { active_ = false; keyEnv_ = 0.f; hammerEnv_ = 0.f; damperEnv_ = 0.f; srcA_.pcm.reset(); srcB_.pcm.reset(); }

  /** Touche relachee.  Si la pedale est enfoncee, la corde continue (aucune rampe) ;
   *  sinon l'etouffoir tombe.  C'est ce qui manquait : la pedale ne faisait que
   *  retarder l'extinction au lieu de tenir la note. */
  void liftKey() {
    keyDown_ = false;
    if (!sustained_) release();
  }
  /** Etat de la pedale.  Quand elle remonte, les touches deja relachees s'eteignent. */
  void setSustained(bool on) {
    sustained_ = on;
    if (!on && !keyDown_ && !releasing_) release();
  }
  bool active() const { return active_; }
  bool releasing() const { return releasing_; }
  int note() const { return note_; }
  float age() const { return age_; }

  /** Lit un echantillon stereo d'une source et avance son curseur. Renvoie faux
   *  quand la source est finie (hors boucle). */
  bool readOne(Src& s, float bendRatio, float& l, float& r) {
    if (!s.pcm || s.frames < 2) return false;
    if (s.loopEnd > s.loopStart && !releasing_ && s.pos >= s.loopEnd)
      s.pos -= (s.loopEnd - s.loopStart);
    const uint32_t i0 = static_cast<uint32_t>(s.pos);
    const uint32_t i1 = i0 + 1;
    if (i1 >= s.frames) return false;
    const float fr = static_cast<float>(s.pos - i0);
    const int16_t* p = s.pcm->data() + static_cast<size_t>(i0) * 2;
    const int16_t* q = s.pcm->data() + static_cast<size_t>(i1) * 2;
    l = (p[0] + (q[0] - p[0]) * fr) * (1.f / 32768.f) * s.gain;
    r = (p[1] + (q[1] - p[1]) * fr) * (1.f / 32768.f) * s.gain;
    s.pos += s.ratio * bendRatio;
    return true;
  }

  void render(float* outL, float* outR, int n, float sr,
              float body, float bright, float width, float releaseSec, float sustainHold,
              bool tine) {
    tine_ = tine;
    if (!active_ || !srcA_.pcm || srcA_.pcm->empty()) return;
    // 0,5 ms et non 4 : le transitoire de marteau vit dans les 2 premieres ms,
    // une rampe de 4 ms l'efface et le piano perd son attaque.
    const float atk = 1.f / (0.0005f * sr);
    // bright 0 -> -6 dB d'aigus, bright 1 -> +6 dB.
    // Plage +-12 dB (etait +-6) : a +-6 les quatre reglages d'aigu se confondaient.
    const float shelfG = std::exp2((bright - 0.5f) * 4.0f);
    for (int i = 0; i < n; ++i) {
      // Bend lisse : un saut de ratio d'un bloc a l'autre claque.  12 ms de
      // constante donne un glissando continu, comme une roue qu'on pousse.
      bendNow_ += (bendTarget_ - bendNow_) * bendCoef_;
      const float bendRatio = std::pow(2.f, bendNow_ / 12.f);
      const float step = bendRatio;   // le ratio propre a chaque couche est dans Src

      // Deux couches lues ensemble, fondues a puissance constante.  Chaque source
      // a son propre curseur mais le meme ratio : elles tournent dans le meme temps,
      // donc le fondu reste coherent sur toute la duree de la note.
      float sL = 0.f, sR = 0.f;
      if (blend_ <= 0.f) {
        if (!readOne(srcA_, step, sL, sR)) { active_ = false; break; }
      } else {
        float aL = 0.f, aR = 0.f, bL = 0.f, bR = 0.f;
        const bool okA = readOne(srcA_, step, aL, aR);
        const bool okB = readOne(srcB_, step, bL, bR);
        if (!okA && !okB) { active_ = false; break; }
        // Fondu LINEAIRE, pas a puissance constante.
        //
        // Les deux couches d'une meme note sont deux enregistrements de la MEME
        // corde, donc fortement CORRELEES.  Le fondu cos/sin (puissance constante)
        // suppose deux signaux INDEPENDANTS : sur deux signaux correles il ajoute
        // jusqu'a +3 dB au milieu du fondu.  Mesure sur `piano.bin` : une bosse de
        // +3 dB vers vel 100 puis un creux de -1,7 dB vers vel 95 — exactement le
        // « la velocite repond n'importe comment ».  La somme lineaire, elle,
        // interpole le niveau reel entre les deux couches.
        const float ca = 1.f - blend_, cb = blend_;
        sL = aL * ca + bL * cb;
        sR = aR * ca + bR * cb;
      }

      const bool held = !releasing_ || sustained_;
      if (held) {
        env_ = std::min(1.f, env_ + atk);
      } else {
        rel_ += 1.f / (std::max(0.05f, releaseSec) * sr);
        if (rel_ >= 1.f) { active_ = false; break; }
      }
      age_ += 1.f / sr;
      const float tail = std::exp(-age_ / (tau1_ * (1.f + 6.f * sustainHold)));
      const float amp = env_ * (held ? 1.f : (1.f - rel_)) * tail;

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

      // gain_ = normalisation de crete du pack (mesuree au chargement) : le pack
      // brut sortait a 1,0 et le produit avec la courbe de velocite ecretait.
      const float out = amp * vel_ * gain_;
      outL[i] += mL * out;
      outR[i] += mR * out;
      if (hammerEnv_ > 1e-4f) {
        // Frappe filtree : passe-haut puis passe-bas, deux fois, pour une pente de
        // 12 dB/oct de chaque cote.  C'est ce qui transforme le « tsss » en « toc ».
        float hn = noiseHammer_.next() * hammerEnv_ * hammerAmt_;
        hpL_ += hpA_ * (hn - hpL_);
        hpR_ += hpA_ * (hn * 0.94f - hpR_);
        lpL2_ += lpA_ * (hpL_ - lpL2_);
        lpR2_ += lpA_ * (hpR_ - lpR2_);
        outL[i] += lpL2_;
        outR[i] += lpR2_;
        hammerEnv_ *= hammerDecay_;
      }
      if (keyEnv_ > 1e-4f) {
        // Contact de touche : meme filtre, plus bas et plus long.  Passe par le meme
        // chemin stereo legerement desequilibre, comme le marteau.
        float tn = noiseKey_.next() * keyEnv_ * keyAmt_;
        kL_ += kA_ * (tn - kL_);
        kR_ += kA_ * (tn * 0.90f - kR_);
        const float tl = kB_ * kL_, tr = kB_ * kR_;
        outL[i] += tl;
        outR[i] += tr;
        keyEnv_ *= keyDecay_;
      }
      if (damperEnv_ > 1e-4f) {
        // Etouffoir : feutre qui retombe sur la corde, uniquement au relachement.
        // Plus bas et plus court que la touche (c'est un contact amorti), et son
        // niveau suit DAMPER NOISE, le reglage dedie.
        float dn = noiseDamper_.next() * damperEnv_ * damperAmt_ * 0.5f;
        dL_ += kA_ * (dn - dL_);
        dR_ += kA_ * (dn * 0.88f - dR_);
        outL[i] += kB_ * dL_;
        outR[i] += kB_ * dR_;
        damperEnv_ *= damperDecay_;
      }
    }
  }

 private:
  Src srcA_, srcB_;
  float blend_ = 0.f;   // part de la couche superieure dans le fondu
  float vel_ = 1.f;
  float bendTarget_ = 0.f, bendNow_ = 0.f;
  float bendCoef_ = 0.f;
  float env_ = 0.f, rel_ = 0.f, age_ = 0.f, tau1_ = 0.5f;
  // Shelf aigu par canal : c'est lui qui rend TONE/HAMMER/LID audibles.
  float lpL_ = 0.f, lpR_ = 0.f, shelfA_ = 0.25f;
  bool tine_ = false;
  float gain_ = 1.f;
  float hammerAmt_ = 0.f, hammerEnv_ = 0.f, hammerDecay_ = 0.f;
  float hpA_ = 0.5f, lpA_ = 0.5f, hpL_ = 0.f, hpR_ = 0.f, lpL2_ = 0.f, lpR2_ = 0.f;
  // Bruit de touche : enveloppe separee, coupee par le NOTE OFF (ce n'est pas un
  // son tenu, c'est un contact).
  float keyAmt_ = 0.f, keyEnv_ = 0.f, keyDecay_ = 0.f, keyHz_ = 0.f;
  float kA_ = 0.5f, kB_ = 0.5f, kL_ = 0.f, kR_ = 0.f;
  // Bruit d'etouffoir : seulement au relachement, enveloppe propre.
  float damperAmt_ = 0.f, damperEnv_ = 0.f, damperDecay_ = 0.f;
  float dL_ = 0.f, dR_ = 0.f;
  // Trois generateurs independants : un seul partage faisait « sonner » marteau,
  // touche et etouffoir ensemble, ce qui se joue comme un clic mecanique unique.
  Noise noiseHammer_, noiseKey_, noiseDamper_;
  int note_ = -1;
  bool active_ = false, releasing_ = false, sustained_ = false, keyDown_ = false;
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
    for (int i = 0; i < 2; ++i) { tone_[i].reset(); pres_[i].reset(); }
    pre_.reset(); rev_.reset();
    cf_ = -1;
  }

  /**
   * Position et distance du micro, couvercle, egaliseur, piece, largeur.
   *
   * L'ampli et le formateur de transitoire sont PARTIS.  Ils n'existent sur aucun
   * piano acoustique, et la saturation `tanh` etait en serie sur toute la sortie :
   * un pack deja cale a sa crete ne pouvait qu'y ecreter.  C'est ce que l'utilisateur
   * a entendu comme « Amp detruit le son ».  Pianoteq n'a pas d'etape ampli.
   *
   * Tout est recalcule seulement quand un reglage bouge : les Biquad ont un
   * cout de calcul par echantillon, pas par bloc.
   */
  void set(float pos, float dist, float lid, float bass, float mid, float treb,
           float pres, float space, float width) {
    // Chaque famille de reglages a SON propre garde.  Les regrouper sous celui du
    // micro etait un bug mesure : une fois le preset pose, bouger TONE, PRESENCE,
    // SPACE ou WIDTH ne faisait plus rien tant que le micro ne bougeait pas aussi.
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
    }
    if (std::fabs(bass - cBass_) > 0.002f || std::fabs(mid - cMid_) > 0.002f ||
        std::fabs(treb - cTreb_) > 0.002f) {
      cBass_ = bass; cMid_ = mid; cTreb_ = treb;
      for (int ch2 = 0; ch2 < 2; ++ch2) {
        tone_[ch2].setLowShelf(sr_, 180, (bass - 0.5f) * 20.f);
        tone_[ch2].setPeaking(sr_, 750, 0.8, (mid - 0.5f) * 16.f);
        tone_[ch2].setHighShelf(sr_, 3200, (treb - 0.5f) * 20.f);
      }
    }
    if (std::fabs(pres - cPres_) > 0.002f) {
      cPres_ = pres;
      for (int ch2 = 0; ch2 < 2; ++ch2) pres_[ch2].setHighShelf(sr_, 5500, (pres - 0.5f) * 12.f);
    }
    spaceAmt_ = space;
    widthAmt_ = width;
  }

  /** Un bloc stereo : micro -> egaliseur -> largeur -> reverb. */
  void process(float* io, int n) {
    if (n <= 0) return;
    for (int i = 0; i < n; ++i) {
      float l = io[2 * i], r = io[2 * i + 1];
      l = pre_.process(0, l); r = pre_.process(1, r);
      l = mem_[0].process(0, l); r = mem_[1].process(1, r);
      l = air_[0].process(0, l); r = air_[1].process(1, r);
      l = tone_[0].process(0, l); r = tone_[1].process(1, r);
      l = pres_[0].process(0, l); r = pres_[1].process(1, r);
      if (widthAmt_ != 1.f) { const float m = 0.5f * (l + r), s = 0.5f * (l - r) * widthAmt_; l = m + s; r = m - s; }
      // Limiteur doux, LINEAIRE sous le genou.  Un `tanh(x)` sec est non lineaire des
      // le premier echantillon : a la crete normale du piano (0,31 mesuree) il rendait
      // deja 0,300, soit une distorsion permanente meme a volume de jeu modere — c'est
      // ce qui s'entendait comme « ca sature » sur un accord.  Sous 0,8 on ne touche
      // plus au signal ; au-dela, le genou arrondit jusqu'a 1,0, ce qui garde la
      // protection contre le depassement que la somme des voix peut produire.
      io[2 * i] = softClip(l); io[2 * i + 1] = softClip(r);
    }
    // Reverb en dernier : elle doit entendre le compresseur, sinon elle pompe.
    // Deux reglages, pas un : DECAY et le melange.  Les valeurs figees (RT60 4,35 s
    // a 52 % de melange) donnaient une cathedrale — mesure : la queue restait a
    // -11,4 dB sous la note une seconde apres le relachement, et la reverb doublait
    // le niveau.  C'est ce qui faisait dire « juste un effet de reverb bizarre » :
    // elle masquait le timbre des modeles.  Une piece de piano tient en 1,2 a 2,5 s.
    if (spaceAmt_ > 0.01f) {
      rev_.setNorm(1, 0.04f + 0.10f * spaceAmt_);   // piece : RT60 ~1,2 s .. 1,9 s mesures
      rev_.setNorm(5, 0.03f + 0.16f * spaceAmt_);   // melange : la queue reste derriere
      rev_.process(io, n);
    }
  }

  float space() const { return spaceAmt_; }
  void setSpace(float v) { spaceAmt_ = v; }

 private:
  float sr_ = 44100.f;
  Biquad mem_[2], air_[2], pre_, tone_[2], pres_[2];
  SpaceReverb rev_;
  float spaceAmt_ = 0.3f, widthAmt_ = 1.f;
  float cPos_ = -1.f, cDist_ = -1.f, cLid_ = -1.f;
  float cBass_ = -1.f, cMid_ = -1.f, cTreb_ = -1.f, cPres_ = -1.f;
  int cf_ = -1;
};

// ---------------------------------------------------------------------------
// Moteur.  17 parametres (contrat de l'interface ; l'ordre est fige) :
//   0 HAMMER   durete de marteau      9 VEL      courbe de velocite (monotone)
//   1 TONE     brillance              10 DAMPER  bruit d'etouffoir (au relachement)
//   2 RESONANCE couplage resonateurs  11 DIST    distance du micro
//   3 LID      couvercle              12 KEY     bruit de touche (a l'appui)
//   4 MIC POS  position du micro      13 (libre)  ex-PUNCH, retire
//   5 SPACE    piece                  14 EQ      basse/mid/aigu (macro)
//   6 WIDTH    largeur stereo         15 MODEL   choix du modele (Kotlin, en dernier)
//   7 LEVEL    niveau                 16 HAMMER N bruit de marteau (la frappe)
//   8 RELEASE  longueur de queue
//
// DRIVE (12) et PUNCH (13) sont RETIRES : deux reglages qu'aucun piano acoustique
// n'a, et dont la saturation en serie ecrasait un pack deja a sa crete (« Amp
// detruit le son »).  L'index 12 est recycle en bruit de touche, l'index 13 reste
// libre (les presets enregistres le portent, le moteur l'ignore).  Le bruit de
// marteau reprend l'index 16 : c'est le meme contrat a 17 que l'ancien, donc aucun
// preset enregistre n'est tronque.
// ---------------------------------------------------------------------------
class PianoEngine : public SourceFx {
 public:
  static constexpr int kParams = 17;
  // 12 voix ne tiennent pas un accord tenu avec la pedale : des la 13e note le
  // moteur volait une voix.  Mesure : 2,5 % de CPU a 32 voix contre 2,7 % a 12
  // (le cout est domine par l'etage studio, pas par la voix), donc la marge est
  // gratuite et un accord de 10 doigts + pedale ne coupe plus rien.
  static constexpr int kVoices = 32;

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
    int slot = -1;
    for (int i = 0; i < kVoices; ++i)
      if (!voices_[i].active()) { slot = i; break; }
    if (slot < 0) {
      // Toutes les voix sont prises : on vole la plus ANCIENNE d'abord, et parmi
      // les relachees avant les tenues.  La version precedente prenait `age()` le
      // PLUS PETIT, donc la note la plus REEENTE : jouer un accord coupe la note
      // qu'on venait de frapper, ce qui se joue « une seule note a la fois ».
      int oldest = -1, oldestFree = -1;
      for (int i = 0; i < kVoices; ++i) {
        if (voices_[i].releasing()) {
          if (oldestFree < 0 || voices_[i].age() > voices_[oldestFree].age()) oldestFree = i;
        } else {
          if (oldest < 0 || voices_[i].age() > voices_[oldest].age()) oldest = i;
        }
      }
      slot = (oldestFree >= 0) ? oldestFree : oldest;
    }
    // Courbe de velocite MONOTONE.  L'ancienne version melangeait la velocite brute
    // avec son carre selon un « deficit » mesure sur le pack ; le resultat n'etait
    // pas une fonction croissante, donc certaines touches repondaient a l'envers
    // (c'est le « velocite bizarre, desordre total »).  Ici on n'utilise QUE des
    // exposants : v = (vel/127)^g.  g = 1 (lin), 0,6 (doux, plus de niveau a faible
    // velocite), 1,6 (dur, il faut frapper).  Toujours strictement croissant.
    const float lin = static_cast<float>(vel) / 127.f;
    const float gamma = std::pow(2.f, (0.5f - p(9)) * 1.36f);   // 0 -> 1.6, 0.5 -> 1, 1 -> 0.6
    const float v = std::pow(lin, gamma);
    // MODEL est enfin lu : index 8 = Electric, dont la corde devient une tine.
    const float tine = (std::lround(p(15) * 8.f) >= 8.f) ? 1.f : 0.f;
    // Deux couches lues ENSEMBLE et fondues.  La couche porte le TIMBRE (marteau
    // plus dur, tine plus mordant) et change d'un coup ; sans recouvrement le
    // niveau sautait a la frontiere (mesure jusqu'a 4 dB sur un pack electrique,
    // l'utilisateur decrivait « ca saute » dans le haut de la velocite).  On lit
    // donc la couche de la velocite et celle AU-DESSUS, avec un fondu a puissance
    // constante : le timbre glisse au lieu de basculer.
    const int nLayers = reader_.layerCount() > 0 ? reader_.layerCount() : 1;
    const float vlayer = v * static_cast<float>(nLayers);
    int lo = static_cast<int>(vlayer);
    float blend = vlayer - static_cast<float>(lo);
    if (lo >= nLayers - 1) { lo = nLayers - 1; blend = 0.f; }
    if (lo < 0) { lo = 0; blend = 0.f; }
    auto mkSrc = [&](const PianoSampleReader::Region* rg) {
      PianoVoice::Src s;
      if (!rg) return s;
      auto p = reader_.decoded(rg);
      if (!p || p->empty()) return s;
      const float rootF = 440.f * std::pow(2.f, (rg->root - 69) / 12.f)
                          * std::pow(2.f, rg->tuneCents / 1200.f);
      const float f = 440.f * std::pow(2.f, (note - 69) / 12.f);
      s.pcm = p; s.frames = static_cast<uint32_t>(p->size() / 2);
      s.ratio = (rootF > 1e-6f) ? f / rootF * (static_cast<float>(reader_.rate()) / sr_) : 1.f;
      s.loopStart = loopsOn_ ? rg->loopStart : 0u;
      s.loopEnd = loopsOn_ ? rg->loopEnd : 0u;
      if (s.loopEnd <= s.loopStart || s.loopEnd > s.frames) s.loopStart = s.loopEnd = 0;
      s.gain = reader_.layerGain(rg->root, rg->layer);
      return s;
    };
    const auto srcA = mkSrc(reader_.pickLayer(note, lo));
    const auto srcB = mkSrc(reader_.pickLayer(note, lo + 1));
    if (!srcA.pcm && !srcB.pcm) return;
    // Si la couche du dessus manque, on ne fond rien : srcA seul porte la note.
    if (!srcB.pcm) blend = 0.f;
    if (!srcA.pcm) { /* seule B existe : elle porte tout */ blend = 1.f; }
    const float gain = reader_.normGain();
    voices_[slot].start(srcA, srcB, blend, note, v, sr_,
                        p(4), p(5), tine,
                        p(0),      // HAMMER : un marteau dur claque plus fort
                        p(16),     // HAMMER N : bruit de la frappe (depend de la durete)
                        p(12),     // KEY : bruit de touche (ex-DRIVE, index recycle)
                        p(10),     // DAMPER : bruit d'etouffoir (au relachement)
                        gain);
    voices_[slot].setSustained(sustained_);
    voices_[slot].setBend(bend_, bendSemis_);
  }

  void noteOff(int note) override {
    // La touche remonte : si la pedale est enfoncee la corde continue, sinon
    // l'etouffoir tombe.  Le moteur appelait `release()` directement, donc la
    // pedale ne tenait jamais la note.
    for (auto& v : voices_) if (v.active() && v.note() == note) v.liftKey();
  }

  /** Pedale sustain.  L'etat est retenu pour les notes suivantes (une note jouee
   *  pedale enfoncee n'est pas coupee a son relachement) et applique aux voix en
   *  cours : remonter la pedale eteint celles dont la touche est deja levee. */
  void sustain(bool on) override {
    sustained_ = on;
    for (auto& v : voices_) v.setSustained(on);
  }

  void allNotesOff() override { for (auto& v : voices_) v.kill(); }

  /**
   * Pitch bend, en demi-tons selon la plage de Track Settings.
   *
   * Le piano ne l'implementait pas : SourceFx::pitchBend est vide par defaut, donc
   * la roue etait inerte sur un piano Æther.  Les voix l'appliquent dans la boucle
   * de lecture, avec un lissage de 12 ms — un bend qui saute d'un bloc s'entend
   * comme un demi-ton sec.
   */
  void pitchBend(float b) override {
    bend_ = b;
    for (auto& v : voices_) v.setBend(b, bendSemis_);
  }
  void setPitchBendRange(int semis) override {
    SourceFx::setPitchBendRange(semis);
    for (auto& v : voices_) v.setBend(bend_, bendSemis_);
  }

  /** Boucle de sustain active ou non.  Une boucle courte module l'amplitude a sa
   *  frequence de repetition ; la couper supprime la modulation, au prix d'une note
   *  qui s'arrete a la fin de la queue enregistree. */
  void setLoopsEnabled(bool on) { loopsOn_ = on; }

  bool loadSampleBank(const uint8_t* bytes, size_t size) override {
    return reader_.load(bytes, size);
  }
  bool hasSampleBank() const override { return reader_.loaded(); }

  /** Voix encore actives.  Sert aux bancs a detecter une note bloquee : une voix
   *  active alors qu'aucune touche n'est tenue est un bug, pas un etat normal. */
  size_t bankRegions() const { return reader_.regionCount(); }
  size_t preloadedRegions() const { return reader_.preloadedCount(); }

  /** Diagnostic : region choisie et gain de correction, pour tracer une inversion
   *  de velocite (`tools/aetherfx/piano_pick_trace.cpp`).  Lecture seule. */
  const PianoSampleReader::Region* pickRegion(int note, int vel) const { return reader_.pick(note, vel); }
  float layerGain(int root, int layer) const { return reader_.layerGain(root, layer); }
  double regionRms(int root, int layer) const { return reader_.layerRmsAt(root, layer); }
  /** RMS de la region choisie sur les `sec` premieres secondes, gain applique.
   *  Sert a accorder la fenetre de normalisation sur celle qu'on entend. */
  double pickedRms(int note, int vel, double sec) {
    const PianoSampleReader::Region* g = reader_.pick(note, vel);
    if (!g) return 0.0;
    auto pcm = reader_.decoded(g);
    if (!pcm || pcm->empty()) return 0.0;
    const size_t win = std::min<size_t>(pcm->size() / 2, static_cast<size_t>(sec * reader_.rate()));
    double s = 0;
    for (size_t i = 0; i < win; ++i) {
      const double l = (*pcm)[2 * i] / 32768.0, r = (*pcm)[2 * i + 1] / 32768.0;
      s += l * l + r * r;
    }
    return std::sqrt(s / std::max<size_t>(1, win * 2)) * reader_.layerGain(g->root, g->layer);
  }
  float packPeak() const { return reader_.peak(); }
  float packNormGain() const { return reader_.normGain(); }

  int activeVoices() const {
    int n = 0;
    for (const auto& v : voices_) if (v.active()) ++n;
    return n;
  }
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
    // 2 RESONANCE, 3 LID, 4 MIC POS, 5 SPACE, 6 WIDTH, 7 LEVEL, 8 RELEASE,
    // 9 VELOCITY, 10 DAMPER, 11 MIC DIST, 12 KEY, 14 EQ, 15 MODEL, 16 HAMMER N.
    // DAMPER, KEY, HAMMER N et MIC DIST sont lus dans noteOn (les bruits naissent
    // avec la note) ; MODEL choisit la banque ; EQ et l'etage studio sont ici.
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
    // Etouffoir : 0,08 a 0,63 s.  La plage precedente (0,25 + 5,0*p = jusqu'a
    // 5,25 s) n'etait pas un relachement de piano mais une queue de pedalier : avec
    // 12 voix, un jeu rapide epuisait le polyphony et volait des voix, ce qui se
    // joue « une note ne sonne pas comme la precedente ».  Un etouffoir reel retombe
    // en 0,1 a 0,3 s ; la queue, elle, est deja portee par l'echantillon et la reverb.
    const float rel = 0.08f + p(8) * 0.55f;
    // Pas de parametre de maintien dans le contrat : on garde une valeur fixe.
    const float hold = 0.5f;
    for (auto& v : voices_) {
      if (!v.active()) continue;
      v.render(tmpL_.data(), tmpR_.data(), n, sr_, body, bright, width, rel, hold, tine > 0.5f);
    }
    // Etage studio : micro -> egaliseur -> largeur -> espace.  Il tourne meme sans
    // voix active, sinon la queue de reverb serait coupee net.  Plus d'ampli ni de
    // punch : les deux index sont retires du contrat (voir l'en-tete du moteur).
    studio_.set(p(4), p(11), p(3), p(14), p(14), p(14), p(14), p(5), p(6));
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
  bool sustained_ = false;   // etat de la pedale, retenu pour les notes suivantes
  PianoSampleReader reader_;
  float bend_ = 0.f;
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
