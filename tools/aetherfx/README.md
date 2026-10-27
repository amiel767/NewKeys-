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
