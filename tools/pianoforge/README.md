# PianoForge.hpp — analyse et vérification (hors Android)

Source : `app/assets/PianoForge.hpp`, poussé par « Add files via upload ». Copié ici
pour la traçabilité, avec la version corrigée `PianoForge.fixed.hpp`.

Header-only C++17, portable : `g++ -O2 -std=c++17 -Wall -Wextra -pedantic` compile
sans un seul avertissement, et le chemin temps réel produit du son.

## Ce que fait le moteur (trois étages)

1. **Analyse, hors ligne.** YIN (de Cheveigné & Kawahara) pour accorder chaque
   échantillon, bande limitée par zone selon la transposition maximale, boucle
   verrouillée sur la période avec aplatissement de niveau et fondu à puissance
   constante, stockage 16 bits avec dither TPDF.
2. **Voix, temps réel.** Rééchantillonneur sinc à fenêtre de Kaiser (24 taps,
   512 phases), 3 têtes de lecture désaccordées, courbe vélocité → niveau + timbre
   (shelf aigu + passe-bas suivi en hauteur), bruit de marteau, étouffoir, pédale.
3. **Résonance.** 88 cordes à guide d'onde numérique (retard fractionnaire de
   Thiran), et une réponse de table d'harmonie synthétique convoluée par FFT
   partitionnée.

## Mesures (hôte, pas d'appareil)

| Grandeur | Résultat |
|---|---|
| Compilation `-Wall -Wextra -pedantic` | **0 avertissement** |
| Justesse de hauteur | **0,20 cent** au pire (l'offset est le pas de recherche) |
| Boucle de sustain, 15 notes | **9 / 15** seulement → **15 / 15** après correctif |
| Saut à la couture de boucle | 5,35 × le RMS local → **0,069 ×** (77 × mieux) |
| Accord de 1 voix | peak **1,12** (écrête) → **0,384** après correctif |
| Accord de 10 voix | peak **2,96** (écrête) → **0,921**, 0 échantillon écrêté |
| Temps réel, 10 voix à 48 kHz | **× 14,7** |
| Mémoire | **8,2 Mo** pour 30 zones (≈ 30 Ko par région) |

## Deux bugs, chacun corrigé par une ligne

### 1. La boucle échoue sur 40 % des notes

`makeLoop` calcule `L = reps * llround(period)`. Or `llround(sr/f0)` n'est pas la
période : à C4, `48000/261,63 = 183,47`, arrondi à 183, soit **0,26 % d'erreur**.
Le fondu de boucle raccorde alors deux phases différentes, la corrélation tombe
sous 0,5 et **aucune boucle n'est produite** — la note est coupée à la fin de
l'échantillon.

Correction : arrondir le **nombre de périodes**, pas la période.

```cpp
const size_t L = (size_t)std::llround((double)reps * period);   // au lieu de reps * P
```

Mesuré sur un signal *parfaitement* périodique : 6 échecs sur 15 disparaissent.
Corrélation avec l'erreur d'arrondi : à 0,02 % la boucle passe, à 0,26 % elle échoue.

### 2. Le gain n'a aucune réserve de crête

L'échantillon est normalisé à 0,95 de crête, et la voix ne ramène rien : une seule
note écrête déjà. Le « gentle soft clip » `l/(1+0,15·|l|)` sature à 6,7, donc il ne
borne rien du tout dans la plage utile.

Correction : réserve par voix + limiteur qui borne réellement.

```cpp
static constexpr float kHeadroom = 0.30f;      // par voix
v.gain = (0.01f + 0.99f * std::pow(vel, p_.velGamma)) * kHeadroom;
// sortie
L[i] = std::tanh(l); R[i] = std::tanh(r);
```

## Comparaison avec le moteur de l'app (`aetherfx/fx_piano.h`)

| | existant | PianoForge |
|---|---|---|
| Source | pack ADPCM 4 bits, 22,05 kHz | WAV 16 bits, bande limitée par zone |
| Boucle | `loop_start`/`loop_end` du pack | bakée, verrouillée période, fondu |
| Cordes | résonateurs modaux (filtres) | 88 guides d'onde (Thiran) |
| Table | queue modélisée | IR synthétique + convolution FFT |
| Voix | 12 | 64 |
| Désaccord | non | 3 têtes par note |
| Coût | 8,2 Mo (196 régions) | ≈ 30 Ko par région |

## Jugement

Le code est **de référence** : YIN, Thiran, sinc de Kaiser, RBJ, convolution
partitionnée — les bonnes méthodes, correctement citées, avec les contraintes
temps réel respectées (aucune allocation dans `process()`, file SPSC, dénormaux
coupés). Sa justesse (0,20 cent) est meilleure que celle de l'existant.

Il n'est **pas utilisable tel quel** : la boucle échoue sur 40 % des notes et le
gain écrête dès la première. Les deux corrections tiennent en deux lignes et sont
mesurées ici.

## Reproduction

```bash
cd /tmp && cp /workspace/project/NewKeys-/tools/pianoforge/PianoForge.fixed.hpp .
g++ -O3 -std=c++17 -I /workspace/project/NewKeys-/app/src/main/cpp bench.cpp -o bench
```
