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
