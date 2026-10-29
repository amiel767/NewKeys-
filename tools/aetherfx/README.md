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
