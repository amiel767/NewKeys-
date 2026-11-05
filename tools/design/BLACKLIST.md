# FX Design — liste noire (ne pas toucher)

La consigne de la conversation : **pérazfinage Design des FX uniquement**. Tout ce
qui n'est pas l'interface FX **câblée** est hors périmètre. Cette liste existe pour
qu'un agent qui arrive ne « finisse » pas du code mort : plusieurs passes ont déjà
été perdues à retoucher des fichiers que rien n'affiche.

## Hors périmètre (liste noire)

| Symbole / fichier | Pourquoi |
|---|---|
| `AeGlassKeyboard`, `AeLightSlider` | plus appelés par aucune UI (vérifié) |
| `MaterialYouPalette.kt` (`MaterialYouStyle`) | code mort, jamais consommé |
| `ui/components/TrackSettingsDialog.kt` | **n'existe plus** — ne pas le chercher |
| `VirtualPianoKeyboard`, `SplitterKeyboard` | supprimés (ancien clavier) |
| `KeyboardLayerMapper` | n'existe plus (seul `KeyPositionMapper` subsiste) |
| `loadSoundfontToSlot` | nom de brief inexistant → `loadSoundFontForSlot` |
| FX hors interface : `fx_piano.h`, `fx_sax.h`, `audio_engine.cpp`, packs `.bin` | moteur audio, tenus par la conversation Æther Piano sur `main` |
| `ui/settings/MidiMappingPage.kt`, `MappingRegistry`, `MappingCatalog` | hors FX |
| `ui/setlists/*`, `ui/sheets/*` | autres modules |

## Ce qui EST dans le périmètre

Uniquement la fenêtre FX **telle qu'elle se rend** :

- `com.aetherkeys.fx.FxWindow` — la coque (rack, catalogue, page d'effet)
- `FxEffectPage.kt`, `FxKnob.kt`, `FxMagmaKnob.kt`, `FxButtons.kt`, `FxCard.kt`
- `FxDesign.kt` (palettes par preset), `FxGeo.kt` (géométrie), `FxC.kt` (couleurs)
- `FxFonts.kt` (Orbitron), `FxRegistry.kt` (catalogue des 15 effets)
- Les captures de revue (`tools/design/fx_page.py`) et les tests d'invariant

## Règle de vérification

Avant de conclure qu'un changement est visible : **mesurer la capture**, pas lire
le diff. Le disque du potard valait 40 de luminance quand on croyait en dessiner un
beau — c'est l'œil qui a été trompé, pas la mesure.

```bash
export JAVA_HOME=/workspace/jdk/jdk-21.0.13+9
export ANDROID_HOME=/workspace/android-sdk
./gradlew :app:recordRoborazziDebug --tests "com.soundstage.mixer.FxDetachedCaptureTest"
python3 tools/design/fx_page.py    # page reference|rendu sur le port 12000
```

**Ne jamais lancer `recordRoborazziDebug` sans `--tests`** : `LiveUiLoop.run()` est
une boucle infinie et la suite se bloque.
