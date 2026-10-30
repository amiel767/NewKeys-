# pianopack — samples piano legers + boost (analyse, tout mesure)

But : des samples **legers** (piano) qu'on **faconne** dans notre propre moteur pour
obtenir **9 presets piano de bonne qualite** (Felt, Studio, Bright, Mellow, Dark,
Upright, Lady D, Honky, Electric). Reference de qualite : Pianoteq tient en ~200 Mo.
On ne prend **rien** des FX existants et on ne copie **aucun** moteur : on cree.

Tous les chiffres ci-dessous sont **mesures**, pas estimes.

## 1. Licence = premier filtre

| Banque | Licence | Contenu | Boucles | Verdict |
|---|---|---|---|---|
| **SplendidGrandPiano** (AKAI/Steinway) | **domaine public** | 62 notes x 5 couches, 73 Mo | `smpl` present mais fin = EOF | **principal** |
| GregSullivan.E-Pianos | CC-BY 3.0 | Wurlitzer 2,3 Mo, CP80 10,5 Mo | vraies boucles, courtes | **secondaire** (Electric) |
| SalamanderGrandPiano (Yamaha C5) | CC-BY 3.0 | 16 couches, **714 Mo** | **aucun smpl** | rejete (trop lourd) |
| jlearman.jRhodes3c (Rhodes) | CC BY-NC-SA | 17,8 Mo | boucle | **rejete — non commercial** |

## 2. Taille du pack : ~1,4 Mo (mesure)

21 notes x 3 couches = 46 regions. FLAC 16,2 Mo -> pack **1,37 Mo**
(ratio 0,0844, **~30 Ko par region**). Pipeline : mono, 22,05 kHz, troncature
attaque+boucle, ADPCM 4 bits. En 5 couches x 1 note sur 2 : ~4 Mo.

## 3. Trouver la boucle (le smpl ne suffit pas)

Chez Splendid, `loopEnd` = derniere frame du fichier (seam -0,85) ; chez Salamander
il n'y a aucun `smpl`. Il faut donc **chercher** : fenetre de 0,35–1,6 s dans le
sustain, score = correlation tete de fenetre <-> segment une periode plus loin.
Mesure sur 4 notes/couches : **+0,91 .. +0,98**.

## 4. Le clic : mesurer le saut, pas la correlation

Boucler la fenetre brute saute **5–10x** le plus grand pas interne (ratio mesure vs
le 99,9e percentile des sauts internes) : clic audible. Un crossfade de 12 ms le
regle **a condition que le poids soit plein a l'index 0** (`w = linspace(1,0,X)`).

| Note | brut | crossfade 12 ms |
|---|---|---|
| FF C3 | 5,38 | **0,01** |
| PP C4 | 10,64 | **0,11** |
| MF A2 | 10,31 | **0,07** |

Avec le poids inverse, le ratio ne bouge pas (5,41 -> 5,60 de 12 a 100 ms).

## 5. Ce qu'on construit (et rien d'autre)

1. **Pack leger** (etape 2) : `pianopack_<couche>.bin` + manifeste JSON, meme
   conteneur que nos autres packs (header, un record par region, payload ADPCM).
2. **Lecteur** : region la plus proche, transposition, split par couche de velocite.
3. **Le boost — coeur du travail.** Prendre une note echantillonnee *simple* et en
   faire un piano credible :
   - **Resonance des cordes** : banque de resonateurs accordes sur les harmoniques
     de la note (le vrai marqueur d'un bon piano).
   - **Sympathetic / release** : les harmoniques continuent apres le relachement.
   - **Caisse & marteau** : composante percussive a l'attaque, bruit de marteau dose.
   - **Lid / micros / espace** : filtrage et mise en espace.
   - **Queue de note** : modele de decroissance, pas une simple boucle lineaire.
4. **9 presets** = 9 jeux de valeurs sur ces parametres, ajustes **contre les
   samples** (mesure), pas au hasard.
5. **UI** : cartes Piano et Strings (assets AVIF deja presents).

## 6. Etat du code piano existant

`app/assets/aether-piano-strings-kotlin.zip` fournit `AePianoUi.kt` (9 modeles,
12 parametres : Hardness, Tone, Resonance, Lid, Mic, Space, Width, Level, Release,
Velocity curve, Pedal noise, MODEL), `AeStringsUi.kt` (8 modeles, 13 parametres),
`AeWaterScene.kt`, `AeLightControls.kt`. `AeCore.kt` / `AeWindow.kt` du zip sont
**a ignorer** (on a les notres).

Les 12 parametres sont la base : on les **affine** et on les relie au moteur de boost.

---

## 5. Campagne de mesure complete (2026-10-29) — outils `measure.py` / `fit_validate.py`

`measure.py` mesure les parametres physiques sur **les 62 notes** de la banque et
ecrit `out/all_notes.json` (ecriture incrementale : un redemarrage du sandbox ne
perd pas le deja-mesure). `fit_validate.py` **valide l'estimateur sur du signal
synthetique** avant de l'appliquer : c'est la seule facon de savoir si une mesure
est juste.

### Ce qui est valide

- **f0 : ±5 cents** sur toute l'etendue (A0..F#5). Precision d'accordeur.
  La banque est accordee par elongation d'octave : les graves sont bas de ~5 c,
  les mediums hauts de ~3 c. On retrouve la courbe Railsback.
- **B : methode validee a 22/24** sur signal synthetique (erreurs 0,7 a 9 % pour
  B connu). Loi mesuree sur la plage fiable (A0..F#5) :
  `B(midi) = 2,29e-4 * 2^(0,1045*(midi-60)/12)`, soit **x2,39 par octave**.
  Ecart-type du residu 0,48 octave : la loi est un **filtre lisse**, pas une
  valeur exacte par note. Utilisable pour le boost, pas pour remplacer un mesure.
- **Transitoire : 93 ms (median), 279 ms max.** C'est la fenetre pendant laquelle
  le timbre se met en place. Le stockage doit garder l'attaque en clair.
- **Boucles : 27/62 notes** ont une boucle raccordable par autocorrelation a
  enveloppe normalisee. Les autres doivent etre stockees plus longues ou bouclees
  avec crossfade.

### Trois methodes testees, deux rejetees (chacune pour une raison mesuree)

| Methode | Synthetique | Rejet |
|---|---|---|
| 1. grille, somme des magnitudes | 23/24 | **rejetee** : B explose a 45 % d'etirement au-dessus de C5 (mauvais minimum) |
| 3. suivi a fenetre etroite (0,35 %) | 18/24 | **rejetee** : rate des que B est grand (seuls 1–2 partiels trouves) |
| **4. score en log-magnitude** | **22/24** | **retenue** : juste la ou les autres echouent (grand B) |

Piege de la methode 1 : diviser par le nombre de partiels trouves recompense un B
qui n'en place que quelques-uns. La methode 4 divise par `n_max` (constante) et
penalise les partiels hors bande (`log(eps)`).

### Ce qui n'est PAS encore valide

- **T60 par partiel : mesures erratiques** (sur C3, h1 = 0,9 s puis h4 = 15,9 s —
  impossible). Cause identifiee : le **battement d'unisson** module l'amplitude de
  chaque partiel, donc une regression exponentielle sur l'enveloppe brute ne
  converge pas. Il faut soit moyenner sur plusieurs pics, soit ajuster un modele
  de battement. A reprendre avant d'en tirer les temps de decroissance du boost.
- **B au-dessus de F#5** : la mesure diverge (les partiels ne sont plus resolus
  a 44,1 kHz). Plage fiable : A0..F#5.
- **Unison beating** : present dans le code, pas encore depouille sur la campagne.

### Recette

```bash
python3 tools/pianopack/measure.py --all --out tools/pianopack/out/all_notes.json
python3 tools/pianopack/fit_validate.py     # valide l'estimateur d'abord
```
