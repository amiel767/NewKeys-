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

## 6. Decroissance : modele double (2026-10-29) — `decay2.py`

Un T60 simple ne peut pas marcher sur cette banque. La decroissance d'un partiel
de piano n'est **pas exponentielle** : les 2-3 cordes de l'unisson partent en
phase, se depolarisent (chute rapide) puis se remettent en phase (queue lente).
C'est l'**aftersound** decrit par Weinreich. Un ajustement simple tombe entre les
deux regimes, et sa pente depend donc de la fenetre choisie -- d'ou les T60 qui
oscillaient d'un facteur 40 d'un partiel au suivant.

Modele retenu, estime par **pelage** (deux droites, pas d'optimisation non
lineaire) : `e(t) = A1*exp(-t/tau1) + A2*exp(-t/tau2)`.

### Valide

- **tau1 (decroissance rapide)** : loi propre, **x0,59 par octave**,
  soit **0,478 s a C4**. Les aigus meurent plus vite, comme attendu.
- Modele valide sur synthetique : 3/4 cas a moins de 25 % (le seul echec est C2,
  ou tau2 = 25 s depasse la duree du fichier : non mesurable, pas faux).

### Non mesurable depuis cette banque (et pourquoi)

- **tau2 (queue lente)** : les valeurs sont instables (946 s sur B-1, 3 236 s sur
  B6). Deux causes, toutes deux dans les donnees, pas dans la methode :
  dans les graves la queue depasse la duree du fichier ; dans les aigus le niveau
  est passe sous le plancher de bruit. Seules 27/61 notes donnent un ajustement
  a moins de 3 dB d'erreur.
  Consequence pratique : **on ne mesure pas tau2, on le modelise.** L'aftersound
  est un phenomene connu dont le rapport typique est 10-30x ; la queue de note
  sera produite par le boost (resonance + release), pas lue dans les samples.
- **Battement d'unisson** : mesure par `decay.py`, pas encore depouille.
  Ordre de grandeur observe : 0,3-0,5 Hz dans le medium (soit ~10-20 cents entre
  cordes), coherent avec un accord reel.

### Recette

```bash
python3 tools/pianopack/decay2.py --validate                      # modele d'abord
python3 tools/pianopack/decay2.py PP all tools/pianopack/out/decays.json
python3 tools/pianopack/decay.py C3 PP out/beating.json           # battement
```

## 7. Ce que le boost doit produire (synthese des mesures)

| Parametre | Source | Valeur |
|---|---|---|
| f0 par note | mesure | ±5 cents (courbe Railsback) |
| Inharmonicite B | mesure, A0..F#5 | `2,29e-4 * 2^(0,1045*(midi-60)/12)` |
| tau1 (chute rapide) | mesure | `0,478 s` a C4, x0,59 par octave |
| tau2 (queue) | **a modeliser** | non mesurable (cf. 6) |
| Transitoire | mesure | 93 ms median, 279 ms max |
| Boucles | mesure | 27/62 notes raccordables |
| Battement d'unisson | mesure partielle | 0,3-0,5 Hz (medium) |

Les trois premiers sont prets a piloter le moteur. La queue de note, la resonance
sympathique et le bruit de marteau restent a produire par le boost.

## 8. Le pack, construit et mesure (2026-10-29) — `build.py` / `verify_pack.py`

Le pack reel : **30 notes x <=4 couches = 107 regions, 4,17 Mo**.
Stereo, 22 050 Hz, ADPCM 4 bits. **Sous le budget de 10 Mo avec 2,4x de marge.**

| Grandeur | Valeur mesuree |
|---|---|
| Poids du pack | **4,17 Mo** (PCM source 16,68 Mo, ratio 25 %) |
| SNR ADPCM | 19,4 dB median, 16,9 dB min |
| Poids par couche | PP 1,03 · Mp 1,20 · Mf 0,57 · MF 0,84 · FF 1,09 Mo |
| Energie >11 kHz | 0,01 % (filtre anti-repliement verifie) |

### Correction : le ratio n est pas 6,2 %

Le 6,2 % du README portait sur le **FLAC compresse** (deja 50 % du PCM), donc il
surestimait la compression d ADPCM d un facteur 4. Le vrai ratio, PCM 16 bits vers
ADPCM 4 bits, est **25 %**. Les estimations de poids des sections 2 et 5 sont donc
a multiplier par 4 : le pack de 46 regions pese ~5,5 Mo, pas 1,4 Mo.

### La banque n est PAS bouclable (mesure)

Correlation maximale a enveloppe normalisee : **0,66 a 0,83** sur PP/MF C3 et C2.
Le seuil utile est > 0,90. Raison : les partiels d un piano decroissent a des
vitesses differentes, donc le timbre change entre deux points eloignes -- il n y a
pas de section stationnaire a recoller. Conclusion : **aucune region n est bouclee**,
on garde 2,5 s et **la queue est produite par le boost**. C est le meme partage que
pour tau2 (section 6) : le sustain appartient au moteur, pas au sample.

### Bug attrape avant de figer le pack

Le reechantillonnage 44,1 -> 22,05 kHz etait une interpolation lineaire **sans
filtre anti-repliement** : tout ce qui depasse 11 kHz se repliait dans la bande
audible. Un SNR global ne le voit pas (l erreur est faible), mais le bruit ajoute
s entend. Corrige par un FIR fenetre-sinc de 257 taps ; controle : 0,01 % d energie
au-dessus de 11 kHz.

Piege de mesure associe : calculer la FFT sur le signal **entrelace L/R** double la
frequence apparente et fait croire a 50 % d energie haute. Toujours mesurer par canal.

### Recette

```bash
python3 tools/pianopack/inventory.py                       # couches disponibles
python3 tools/pianopack/build.py --jobs jobs.txt --out out/pack
python3 tools/pianopack/verify_pack.py out/pack
```

## 9. Pack final retenu (2026-10-29)

Le pack retenu fait **196 regions, 6,56 Mo, 62 notes (A#2..G6)**, SNR ADPCM
median **20,1 dB**. C'est le jeu complet du cache de la banque, pas un
sous-echantillonnage : toutes les notes jouables ont leur region.

Le pack 30 notes (107 regions, 4,17 Mo) reste constructible et suffit si l'espace
compte : la couverture passe alors a A#2..F6 et l'ecart entre deux regions monte
a un demi-ton, ce qui rend la transposition audible sur les aigus.

Chemin dans l'APK : \`assets/pianopack/piano.bin\` (+ \`.json\` pour reference).
