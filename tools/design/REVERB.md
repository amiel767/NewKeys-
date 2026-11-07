# Æther Space — la reverb, réglage par réglage

Document de référence de l'effet **Æther Space** (id moteur 0). Il décrit ce que
chaque réglage *fait réellement*, la valeur qu'il affiche, et comment on l'a
mesuré. Quatre bancs le vérifient et le tiennent à jour :

| Banc | Ce qu'il prouve |
|---|---|
| `tools/design/space_bench.cpp` | la reverb fonctionne : elle rend de l'audio, ne sature pas, et chaque réglage a un effet |
| `tools/design/space_precision.cpp` | chaque réglage a de la **résolution** : zones mortes, niveaux distincts, inversions de sens, et l'étendue des 10 presets |
| `tools/design/space_accuracy.cpp` | la valeur **affichée est vraie** : RT60, pré-délai et cornes d'EQ mesurés contre l'annonce |
| `tools/design/regcheck.cpp` | le **contrat** de la table : `paramCount()` du moteur, taille des presets, bornes des courbes, accord registre ↔ moteur |

```bash
g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/space_bench      tools/design/space_bench.cpp
g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/space_precision  tools/design/space_precision.cpp
g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/space_accuracy   tools/design/space_accuracy.cpp
g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/regcheck         tools/design/regcheck.cpp
/tmp/space_bench && /tmp/space_precision && /tmp/space_accuracy && /tmp/regcheck
```

Les quatre doivent afficher `TOUT PASSE`. `space_accuracy` **valide d'abord ses
propres estimateurs** sur des signaux connus et refuse de juger le moteur si l'un
d'eux échoue : un banc non validé n'a pas le droit de déclarer un moteur faux.
`regcheck` a été validé de la même façon : remis sur l'ancienne formule de
fréquence, il sort 90 échecs.

## Structure du moteur (`app/src/main/cpp/aetherfx/fx_space.h`)

Un **FDN à 8 lignes** (retard en réseau, matrice de Householder), alimenté par une
diffusion d'entrée, avec pré-délai, amortissement, multiplicateur de graves,
balance early/late, modulation et un EQ de sortie.

```
entrée ─► pré-délai ─► diffusion (2 allpass) ─┬─► early (2 taps) ──┐
                                              │                     ├─► mix ─► EQ ─► sortie
                                              └─► FDN 8 lignes ─────┘
                                                  (amortissement + graves + modulation)
```

## Les 15 réglages

L'ordre ci-dessous est un **contrat** : l'UI pousse une valeur normalisée *par
index*. Il doit rester identique dans `fx_space.h`, `fx_registry.h` (FX_SPACE),
`FxRegistry.kt` et `FxDesign.kt`.

| # | Réglage | Plage affichée | Ce qu'il fait | Courbe |
|---|---|---|---|---|
| 0 | Mix | 0..100 % | dosage sec/mouillé | linéaire |
| 1 | Pre-delay | 0..200 ms | retard avant la queue ; le pré-délai *est* le délai d'onset | linéaire |
| 2 | Decay | 0,1..30 s | RT60 **dans la bande basse** (voir ci-dessous) | linéaire |
| 3 | HighFreq | 1..16 kHz | amortissement : fréquence du passe-bas **dans** la boucle | log |
| 4 | HighShelf | −24..0 dB | étage de shelf aigu en sortie, à la fréquence ci-dessus | linéaire |
| 5 | BassFreq | 100..1000 Hz | fréquence de coupure du multiplicateur de graves | log |
| 6 | BassMult | 0,5..3 × | durée relative de la queue **dans les graves** (1,0 = neutre) | linéaire |
| 7 | Size | 0..100 % | longueurs des lignes du FDN → densité et diffusion | linéaire |
| 8 | Attack | 0..100 % | temps de montée de la queue (fondu d'entrée) | linéaire |
| 9 | Early | 0..100 % | niveau du champ précoce | linéaire |
| 10 | Late | 0..100 % | niveau du champ tardif ; **seul** à porter une queue | linéaire |
| 11 | Rate | 0,1..10 Hz | vitesse de la modulation | linéaire |
| 12 | Depth | 0..100 % | profondeur de la modulation (jusqu'à ±3 ms) | linéaire |
| 13 | HighCut | 1..20 kHz | passe-bas d'étage de sortie | log |
| 14 | LowCut | 10..500 Hz | passe-haut d'étage de sortie | log |

### Pourquoi quatre réglages sont en logarithme

`HighFreq`, `BassFreq`, `HighCut` et `LowCut` sont déclarés `Curve::FreqLog` et
normalisés `réel = min × ratio^n`. En linéaire, `HighFreq` occupait 1..16 kHz :
toute la bande utile (1..3 kHz) tenait dans les **13 %** premiers de la course et
les deux tiers du haut étaient des fréquences inaudibles pour un amortissement de
reverb. En log, cette même bande occupe **40 %** de la course.

La conversion vit à **un seul endroit**, `SpaceReverb::freqHz()`, et l'affichage
la suit (`FxParamD.logRatio`) : ils ne peuvent pas diverger.

### Le balayage doit être dérivé de la plage du paramètre

La courbe `FreqLog` était définie par une formule **fixe**, `20 × 1000^n`, qui ne
regardait ni `min` ni `max`. Elle ne tombait juste que par hasard sur un réglage
20 Hz..20 kHz. Conséquence mesurée : `BassFreq` (plage 100..1000 Hz) annonçait
**6860 Hz** en haut de course, et l'effet EQ était faux sur ses trois fréquences.

La loi est maintenant **le balayage log de la plage déclarée** :
`réel = min × (max/min)^n`. Elle donne la bonne valeur quel que soit le couple
min/max, et `SpaceReverb::freqHz()` (qui écrivait déjà `1000 × 16^n`) en est
exactement le cas particulier de Space. Toute nouvelle courbe de fréquence doit
passer par cette loi : `min > 0` est la seule condition.

## Ce que les mesures disent, et les pièges

**Decay est un RT60 de bande basse.** L'amortissement est *dans* la boucle : l'aigu
décroît toujours plus vite que le grave. Un RT60 pleine bande lit donc 12 à 15 %
court, à toutes les valeurs d'amortissement (mesuré). C'est pourquoi toute reverb
annonce sa durée dans une bande. Le banc mesure en dessous de 400 Hz, où `BassMult`
est neutre à son point 1,0× : ce qui reste est exactement le réglage Decay.

**Attack a une zone ouverte en bas de course.** Son plancher est 1 ms ; sous la plus
courte ligne de retard, une attaque est déjà instantanée. Les 10 % du bas (1..5 ms)
sont donc une zone *ouverte*, pas une zone morte. Le banc distingue les deux : une
zone plate **au bord** de la course est légitime et reportée en colonne `open` ; une
zone plate **au milieu** est un défaut et fait échouer le banc.

**Les sondes doivent couvrir la course du réglage.** Trois faux verdicts venaient
uniquement de la sonde, chacun corrigé :

- mesurer avec le signal **sec** présent : un sinus à 0,28 d'amplitude noie la
  queue, et tout réglage paraît plat → mesures en mouillé seul (`Mix = 1`) ;
- fenêtre plus courte que la course : `Pre-delay` (0..200 ms) lu sur 0..120 ms,
  `Attack` (0..300 ms) lu sur 0..200 ms → les deux semblaient morts ;
- sonde à 9 kHz pour un passe-bas balayant 1..20 kHz : rien ne bouge au-dessus de
  12 kHz → sonde déplacée à 16 kHz.

## Chaque preset porte ses propres réglages

Les 10 presets sont des jeux complets de 15 valeurs. Le banc de précision le
vérifie **sur la table**, sans rendu : pour chaque réglage, combien de valeurs
*distinctes* les presets utilisent, et quelle part de la course ils couvrent.

| Réglage | valeurs distinctes | étendue |
|---|---|---|
| Mix | 9 | 24 % |
| Pre-delay | 9 | 38 % |
| Decay | 10 | 66 % |
| HighFreq | 10 | 49 % |
| HighShelf | 6 | 67 % |
| BassFreq | 7 | 50 % |
| BassMult | 8 | 52 % |
| Size | 9 | 70 % |
| Attack | 10 | 62 % |
| Early | 7 | 60 % |
| Late | 8 | 65 % |
| Rate | 8 | 29 % |
| Depth | 10 | 58 % |
| HighCut | 10 | 53 % |
| LowCut | 10 | 41 % |

Aucun réglage n'est laissé de côté. Le banc signale `UNUSED BY THE SET` si un
réglage tombe sous 4 valeurs distinctes ou 10 % d'étendue.

Les presets sont aussi comparés **deux à deux** sur quatre descripteurs de rendu
(champ précoce, queue, aigu, grave) : la paire la plus proche doit rester à plus de
0,10 de distance. Aujourd'hui : Chamber / Spring à 0,212.

## Les 10 presets

| Preset | Intention | Decay | Amort. | Taille | Caractère |
|---|---|---|---|---|---|
| Shimmer | queue brillante et modulée | 7,5 s | 9 kHz | 82 % | plateau large, aigu ouvert |
| Chamber | petite pièce, tôt | 2,7 s | 6,5 kHz | 48 % | précoce marqué |
| Room | pièce courte, proche | 1,1 s | 5,2 kHz | 30 % | le plus sec |
| Hall | grande salle équilibrée | 4,0 s | 6 kHz | 100 % | référence du moteur |
| Plate | tôle dense et claire | 2,2 s | 11 kHz | 55 % | attaque douce, sans graves |
| Cathedral | très longue, sombre | 12,6 s | 4,2 kHz | 96 % | graves renforcés |
| Spring | ressort, modulé vite | 1,8 s | 7,6 kHz | 40 % | Rate 3,2 Hz, le plus animé |
| Cloud | nappe longue et douce | 9,0 s | 3,6 kHz | 90 % | attaque lente, grave fort |
| Infinite | queue quasi sans fin | 21 s | 4 kHz | 100 % | Decay maximal, grave 2,3× |
| Bloom | montée lente | 8,5 s | 4,6 kHz | 95 % | précoce plein, queue derrière |

## Portée (patch, pas global)

L'effet est porté par le **slot de patch** : chaque patch garde ses réglages et ses
presets utilisateur. Le catalogue est statique (`FxInfoD`), le patch n'y ajoute que
ses valeurs — sauvegarder un preset utilisateur n'altère pas les presets d'usine.
