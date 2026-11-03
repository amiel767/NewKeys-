# Banc Æther FX (hôte, sans Android)

Ces outils compilent les sources `aetherfx/` sur l'hôte : le package est
**header-only et portable** (les gardes SSE de `fx_core.h` couvrent x86 et ARM),
donc aucun Android/NDK n'est requis.

## `bend_range_check.cpp` — plage de bend des instruments Æther

Vérifie que `SourceFx::setPitchBendRange(int)` (piloté par « Pitch bend range »
de Track Settings) atteint bien les **4 sources**. Mesure la fréquence de crête
du spectre (Goertzel) roue centrée vs roue à fond, et compare le ratio à
`2^(semis/12)`.

```bash
cd tools/aetherfx
g++ -O2 -std=c++17 -o bend_range_check bend_range_check.cpp && ./bend_range_check
```

Attendu : **12/12 PASS**, erreur ≤ 0,2 %. Le défaut de chaque source est 2 demi-tons.

## `leads_presets_check.cpp` — le « tactactac » des leads, sur les vrais presets

C'est le banc de référence pour le clic de note des Leads. Il joue **les 32 presets
réels de l'app** (extraits de `AePresets.kt` par `extract_leads_presets.py`, jamais
recopiés) sur **huit frappes de la même note**, et mesure le saut **exactement à
l'échantillon de note-on**.

```bash
python3 tools/aetherfx/extract_leads_presets.py > /tmp/leads_presets.h
g++ -O2 -std=c++17 -I /tmp -I app/src/main/cpp tools/aetherfx/leads_presets_check.cpp -o /tmp/lpc
/tmp/lpc
```

### La métrique qui compte : `excess`, pas `boundary`

Une dent de scie rebascule **une fois par période** (un créneau deux fois) : un
échantillon de chaque période porte donc un saut pleine amplitude. Si ce hasard
tombe sur l'échantillon de note-on, une lecture naïve « quelle est la taille du
saut au note-on » annonce un clic qui n'est que **l'onde elle-même**.

`boundary` = saut au note-on / pic. `periodic` = plus grand saut ailleurs dans la
tête. **`excess = boundary - periodic`** est la seule grandeur audible : ce que la
frontière ajoute au saut que l'onde fait déjà. Sans cette soustraction, `Mono Saw`
annonçait 103 % (un rebasculement de scie tombant pile sur la frontière) alors que
la frontière était propre à 0,003.

### Résultat (moteur avant / après)

| | excès moyen | excès max | presets > 25 % |
|---|---|---|---|
| avant | 43,8 % | 389,1 % | **16** |
| après | **0,3 %** | **7,5 %** | **0** |

Aucune régression. `periodic` est inchangé (l'onde n'a pas bougé).

### Les trois pièges de mesure (chacun a coûté une passe)

- **La phase n'était pas la cause.** `Mono Saw` restait à 103 % avec phase
  aléatoire, phase héritée **et** sans étalement : c'était le rebasculement de
  l'onde (voir ci-dessus).
- **Le sustain monte** sur plusieurs presets, jusqu'à +81 % (`Praise Lead`). Ce
  n'est pas un défaut : en legato la note **s'éteignait sous le doigt** (voir
  `noteOn`) et tient désormais. Le banc `leads_release_check.cpp` couvre ce cas.
- **Les noms de presets contiennent des espaces** : parser la sortie par `awk` sur
  `$1` casse les noms. Utiliser la regex ancrée sur `%`.

## `leads_strike_check.cpp` — le saut frontière frappe par frappe

Un maximum sur huit frappes cache **laquelle** est mauvaise. Ce banc imprime, pour
chaque frappe, le saut frontière en pourcentage, en absolu et le pic — c'est lui
qui a montré que `Mono Saw` était mauvais à la frappe 3 **seulement**, puis qui a
permis de trancher entre deux outils qui se contredisaient (l'un imprimait le pic
sur 0,28 s, l'autre sur la fenêtre de 12 ms).

```bash
g++ -O2 -std=c++17 -I /tmp -I app/src/main/cpp tools/aetherfx/leads_strike_check.cpp -o /tmp/lsc && /tmp/lsc
```

## `leads_release_check.cpp` — le saut au relâchement

Le relâchement doit démarrer **au niveau où la note est**, pas au niveau de
sustain : une note relâchée pendant son attaque remontait d'un coup. Le banc tient
la note 0,05 / 0,15 / 0,30 / 1,00 / 2,00 s puis mesure le saut à la frontière du
relâchement. Toutes les durées doivent rester sous le saut interne de la note.

## `leads_preset_dump.cpp` — les échantillons bruts autour d'une frontière

Imprime `x[a-8..a+8]` autour du note-on et les plus grands sauts des 12 ms qui
suivent, pour un preset et une frappe donnés. C'est l'outil qui a révélé le saut de
0,127 sur `Mono Saw` frappe 3 et permis de comprendre que c'était un rebasculement
de scie et non un clic.

## `extract_leads_presets.py` — les presets réels vers du C++

Lit `AePresets.kt` et écrit `kLeadPresets` (33 valeurs par preset, l'intensité
ajoutée par Kotlin comprise). **Ne jamais recopier un preset à la main dans un
banc** : une table écrite à la main se désaligne silencieusement du fichier (déjà
arrivé sur le piano). Le générateur écrit `0.0f` et non `0f` — Kotlin accepte `0f`,
le C++ non.

Pièges du banc (chacun a coûté une passe) :
- utiliser le **vrai preset** de `AePresets.kt` (« Pure Sine » pour les Leads) :
  un timbre bricolé d'un seul paramètre rend la crête ambiguë ;
- mesurer par **pic du spectre**, pas par zero-crossings ni autocorrélation
  (vibrato + drift de la voix Leads, ambiguïté d'octave) ;
- la fenêtre d'analyse doit rester courte : le LFO de drift des Leads étale un
  spectre long.

## `sax_voice_check.cpp` — voix échantillonnée du sax (mix 1.0)

Le sax embarque une banque (`saxpack_*.bin`) et le moteur tourne à `sampleMix = 1.0` :
**les samples sont la voix**, la synthèse est pondérée à zéro. Trois défauts en découlaient,
tous mesurés ici (le moteur DSP est header-only, donc ce banc tourne sans Android) :

| Défaut | Avant | Après |
|---|---|---|
| Bend / vibrato / scoop / fall | **sans effet** sur la voix échantillonnée (calculés sur la branche synthèse seule) | bend +101,7 ¢ et +200,1 ¢ (attendu 100/200) |
| Clic d'anche | somme **avant** le mix → jeté par le mix | après le mix ; attaque/sustain **0,49x** (synthèse 0,41x) |
| Attaque de l'enregistrement | **127 ms** pour atteindre 50 % de la crête (8 % du RMS sustain dans les 5 premières ms) | démarrage sur l'onset trouvé par région → niveau en **5 ms** |

Le vibrato suit la profondeur : 15,2 ¢ (0,0) → 31,2 ¢ (0,3) → 87,9 ¢ (0,8).

```bash
cd tools/aetherfx
g++ -O2 -std=c++17 -I ../../app/src/main/cpp -o sax_voice_check sax_voice_check.cpp
./sax_voice_check ../../app/src/main/assets/saxpack/saxpack_alt.bin
```

Attendu : **TOUT PASSE (0 échec)** — bend dans quelques cents, vibrato croissant,
attaque ≥ 0,4x le sustain, **0 saut** > 0,25 sur C3..C6.

**Piège de mesure (deux méthodes fausses avant la bonne)** : un pic de Goertzel sur 25 ms
résout ~40 Hz, soit 250 cents — plus grossier que le vibrato mesuré (il annonçait 53 ¢ de
« vibrato » à profondeur 0) ; les passages par zéro sur un signal peu filtré comptent
l'harmonique 2, forte au sax (il lisait exactement le double de la fréquence). La méthode
retenue est l'**autocorrélation** sur un signal filtré à 1,3·f0.
