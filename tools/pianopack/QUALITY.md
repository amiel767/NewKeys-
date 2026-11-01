# Ce qui manque pour de vrais presets haute qualite

Reponse mesuree, pas d'opinion. Chaque chiffre vient d'un banc conserve dans
`tools/aetherfx/` ou `tools/pianopack/`.

## Le vrai probleme : nos 9 « pianos » sont UN seul instrument regle 9 fois

Mesure sur les 9 presets, empreinte spectrale de la meme note : **7 sur 9
bit-identiques**. Les 8 modeles acoustiques sont la meme banque
(SplendidGrandPiano, puis S90 Full Grand) passee dans 8 reglages. « Dark » n'est
pas un piano sombre : c'est le meme piano filtre.

Regle a retenir : **un timbre different demande une banque differente, pas un
reglage different.** Un Steinway filtre ne devient pas un tine : l'inharmonicite
d'une corde (B ~ 1e-4) et celle d'une barre frappee (B ~ 1e-2) n'ont pas la meme
loi, donc aucun filtre ne les relie.

## Ce qui manque, par ordre de gain

### 1. Des banques distinctes, pas des reglages (le gain principal)

| Ce qu'on veut | Ce qu'il faut | Etat |
|---|---|---|
| Tine / Rhodes | banque a barre frappee | GregSullivan E-Pianos, CC-BY 3.0 |
| Clavinet / DX | FM ou banque dediee | **pas de FM dans le moteur** — il faut une banque |
| Droit (« Cottage ») | une vraie banque de droit | aucune disponible librement |
| Queue de concert | banque 16 couches | Salamander CC-BY, mais 714 Mo |

Le SF2 « E.Piano » fourni (413 Mo) apporte exactement ce qui manque : 4 timbres
differents, pas un timbre regle 4 fois.

### 2. Une reserve de crete (headroom)

Sans elle, un accord de 10 notes atteint 2,96 et le limiteur ecrase. Mesure faite
sur `PianoForge.hpp` : `kHeadroom = 0,30` + `tanh` ramene 10 voix de 2,96 a
**0,921**, zero ecretage. Le moteur de l'app a un limiteur doux, mais la reserve
en amont reste insuffisante.

### 3. La bande 3-6 kHz

SNR de l'ADPCM 4 bits par bande, aller-retour reel : 0-1 kHz **47 dB**, 1-3 kHz
**31 dB**, 3-6 kHz **5 a 12 dB**, 6-11 kHz **-14 a -22 dB** (le bruit depasse le
signal). Comme la banque precedente ne portait presque rien au-dessus de 5 kHz,
le haut du spectre etait du bruit pur — c'est la texture « electrique ».

Le nouveau pack corrige la cause (la source porte vraiment de l'aigu : 10,73 %
d'energie en 2-5 kHz contre 5,94 %). Si on veut aller plus loin : **5 bits ou
mu-law 8 bits** (mesure : +0,6 dB seulement dans la bande 3-6 kHz, donc pas
decisif la ou le bruit est au niveau du signal).

### 4. Le T60 (duree de decroissance) n'est pas un parametre

Aucun des 17 parametres ne commande la decroissance de facon explicite. SPACE la
porte indirectement. Un vrai reglage « longueur de corde / harmoniques
sympathetiques » manque.

### 5. Les harmoniques sympathetiques et la pedale

Le moteur ne modelise pas les cordes non frappees qui vibrent par sympathie, ni
la resonances de la pedale forte. C'est une grande part du « corps » d'un piano a
queue. `PianoResonators` (6 modes) en tient lieu, mais reste un substitut.

### 6. Parametres presents mais jamais lus

Mesure par balayage 0 -> 1 en dB par bande :

| Parametre | Effet mesure |
|---|---|
| MIC (avant correctif) | 0,0 dB — deplacait la charniere d'un shelf dont le gain vaut 1 |
| PEDAL NOISE | 0,0 dB — jamais lu (controle retire de l'UI) |
| MODEL | lu depuis le correctif (bascule tine) |

Regle : **un controle qui ne fait rien est pire qu'un controle absent.** Le
retirer ou l'implementer, mais pas le laisser.

## Ce qui a ete corrige dans cette session

| Defaut | Cause mesuree | Correctif | Preuve |
|---|---|---|---|
| Note fausse | derive de la banque | — (le pack etait deja juste) | 0/61 notes > 10 cents, pire 3,3 |
| Touches instables | decodage ADPCM sur le thread audio | pre-decodage au chargement | 1,47 ms -> **0,01 ms** |
| Pack desaccorde | ancienne banque | nouvelle banque | 261,58 Hz pour 261,63 attendu |
| Coutures de boucle | mauvais point de boucle | longueur = nombre entier de periodes | mediane 0,06 x pas interne |

## Environnement

- Bancs : `tools/aetherfx/piano_tune_check.cpp` (accord, stabilite, accord, voix),
  `tools/pianopack/retune_pack.py` (derive par region), `tools/pianopack/zones_sf2.py`
  (structure d'un SF2 avant extraction).
- Regle d'outillage : les bancs vivent **dans le depot**, pas dans `/tmp` — `/tmp`
  est efface a chaque redemarrage du sandbox, et deux bancs ont ete perdus ainsi.
