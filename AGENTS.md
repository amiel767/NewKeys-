# AGENTS.md — NewKeys- (SoundStage Mixer)

Mémoire persistante du dépôt. **Toujours vérifier un nom ici avant de l'utiliser** :
plusieurs briefs ont référencé des symboles inexistants, ce qui a causé des
malentendus coûteux.

## Captures d'écran sans émulateur (Roborazzi — vérifié)

Pas d'AVD/émulateur dans cet environnement. La capture passe par **Roborazzi +
Robolectric** (déjà dans `libs.versions.toml`) :

```bash
export JAVA_HOME=/workspace/jdk/jdk21 && export ANDROID_HOME=/workspace/android-sdk
./gradlew :app:testDebugUnitTest --tests "com.soundstage.mixer.XxxCaptureTest" -Proborazzi.test.record=true
```

- Sans `-Proborazzi.test.record=true`, **aucun PNG n'est écrit** (build OK, silencieux).
- Qualifier paysage obligatoire pour coller au device : `@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])`.
  `RobolectricDeviceQualifiers.Pixel6` rend en **portrait** (trompeur).
- `MixerScreen` complet lève `AppNotIdleException` (animations infinies). Fix :
  `composeTestRule.mainClock.autoAdvance = false` puis `advanceTimeBy(1500)` avant `captureRoboImage`.
- `GreetingScreenshotTest` (ancien) échoue pour cette raison — ne pas s'y fier.
- Échelle mesurée : **2.7497 px/dp** en w851dp-h393dp-440dpi (2340×1080).
- Pièges de capture (vérifiés) : `MixerViewModel` démarre sur **`AppScreenPage.SETLISTS`**
  et restaure la page sauvegardée en **asynchrone** → forcer `navigateToPage(MIXER)`
  **après** le restore (`runOnIdle` + `Thread.sleep`), sinon la capture montre Setlists.
  Et **ne pas appeler deux fois** `cycleKeyboardExpansion()` / `toggleKeyboardLayer()`
  (bascule → état inverse).
- Le clavier est **déjà** décalé au-dessus de la bottom bar par un espaceur
  (`keyboardSpacer`, `MixerScreen.kt:496-509`). Ne **pas** ajouter de `padding(bottom)`
  au clavier : ça le remonte par-dessus la bottom bar.

## Environnement de build

```bash
export JAVA_HOME=/workspace/jdk/jdk21
export ANDROID_HOME=/workspace/android-sdk
./gradlew :app:compileDebugKotlin          # ~1m10
./gradlew :app:clean :app:assembleDebug    # ~1m40, APK ~41 Mo
```
Succès attendu : `BUILD SUCCESSFUL`. Seuls warnings connus : `quadraticBezierTo`
déprécié (MixerIconsV2.kt, SustainPedalButton.kt). Toute autre erreur = régression.

## Deux namespaces coexistent

| Namespace | Rôle |
|---|---|
| `com.soundstage.mixer` | L'app principale (mixer, drumpad, settings) |
| `com.aetherkeys.sheets` / `com.aetherkeys.setlists` | Modules séparés (Sheets, Setlists) |

Piège : `SettingsScreen`, `SetlistsScreen` et `SlSegmented` existent en **double**
dans les deux namespaces. Toujours préciser le chemin complet.

## JSX = source de vérité visuelle

Les 5 fichiers `app/assets/*.jsx` sont la **référence de design**. Le brief B6-Fix-2
contenait des specs erronées : partir du JSX, pas du brief.

| JSX | Composant Kotlin correspondant |
|---|---|
| `MixerPreview.jsx` | `ui/MixerScreen.kt` + `ui/components/v2/*` |
| `DrumPadPreview_v2.jsx` | `ui/pages/DrumPadScreen.kt` + `ui/components/drumpad/*` |
| `SoundfontsPreview.jsx` | `ui/components/SoundfontWindow.kt` |
| `SettingsPreview.jsx` | `ui/settings/SettingsScreen.kt` (écran) **et** `ui/components/SettingsDrawer.kt` (drawer) |
| `SetlistsPreview.jsx` | `ui/setlists/SetlistsScreen.kt` |

**Attention conversion** : le JSX dessine sur un canvas de 891 × 411 px avec un
`scale()`. Ses `fontSize`/`height` sont des **pixels de canvas**, pas des dp.
Un `fontSize: 14.5` JSX ≠ `14.5.sp` Kotlin. Aucun facteur d'échelle officiel
n'a été défini — c'est l'origine des tailles « disproportionnées ».

## Correspondance des noms (JSX ↔ Kotlin)

| Concept | JSX | Kotlin réel |
|---|---|---|
| Fenêtre soundfonts | `SoundfontWindow` (variantes `pad`/`track`) | `SoundfontWindow.kt` + `enum SoundfontVariant { Track, Pad, Drum }` |
| Gestionnaire batterie | `Manager` (onglets `kits`/`samples`) | `DrumPadManager.kt` → `DpManager` (onglets **Kits/DrumPad/Loops/Scenes**) |
| Segmented control | `Seg` | `DpSeg` (drumpad) et `SlSegmented` (setlists, `setlists/SetlistsScreen.kt:960`) |
| Boutons +/- ronds | `RoundStep` | `StepperV2` (`v2/MixerTopBarV2.kt`) |
| Plaque de nom piste | name plate `MixerPreview.jsx:229` | `v2/ChannelStripV2.kt` |
| Slider master | `MasterSlider` | `MasterSliderCompact` (`v2/MixerBottomBarV2.kt`) |
| Sliders réglages | `SliderRow` (`SettingsPreview.jsx:185`) | `ui/settings/SettingsScreen.kt:1120` |

### « Patches » = « Scenes » (confirmé par l'utilisateur)
Le JSX nomme `mx.patches` / « Patches Drawer » ; le Kotlin ouvre
`ActivePopup.SCENE` avec `IconFolderV2`. Ce n'est **pas** un bug : c'est le même
concept sous deux noms. Ne pas « corriger ».

## Persistance des soundfonts (vérifié)

`model/AppStatePersistence.kt` porte bien la persistance SF :
- `data class RestoredAppState` (l.130)
- `sfFavorites: Set<String>` (l.183), `sfCategoryOverrides: Map<String, String>` (l.184)
- `KEY_SF_FAVORITES` / `KEY_SF_CATEGORY_OVERRIDES` (companion, l.77-78)
- `putString` (l.552-553), `prefs.getString` (l.1001-1008)

`sfParsedPresets` / `sfParsingFiles` sont dans `MixerUiState`
(`viewmodel/MixerViewModel.kt:269-270`) uniquement : **cache runtime, non persisté**.
Setters : `toggleSfFavorite` (l.3413), `moveSfToCategory` (l.3421),
`parseSfInBackground` (l.3428).

## Banc de rendu LiveUI (outil de capture, vérifié)

`tools/liveui/loop.sh` lance `:app:testDebugUnitTest --tests "*LiveUiLoop*"` avec
`-Proborazzi.test.record=true`. Le test lit `/tmp/liveui/cmd.json`, applique la
commande, écrit `/tmp/liveui/screen.png` et bat `status.txt` (`ok <seq> <ts>`).
Rendu **Compose réel**, hors écran : ni émulateur ni audio.

Champs de commande utiles : `seq`, `page`, `popup`, `tracks`, `keys`, `sustain`,
`midiPedal`, `layer`, `keyboard`, `drumpadMode`, `clicks`, `clickText`, `assertText`,
`dumpTree`, `pause`, et `fxPreview` (voir plus bas).

**Pièges vérifiés** (chacun a coûté une passe) :

- **L'écran par défaut est `SETLISTS`**, pas `MIXER`. La restauration le réaffirme
  (`MixerViewModel.kt` ~l.593/708). Une commande sans `page` capture donc Setlists.
- `cmd.json` est relu **en boucle** : renvoyer `page`/`popup` à chaque étape est
  nécessaire, mais rouvre l'overlay et **réinitialise son état local**
  (`addOpen`, `selectedSlot`).
- **`performClick()` ne déclenche rien** dans le rack FX : les cartes et le
  panneau vivent sous un `pointerInput`/`horizontalScroll` qui absorbe le tap.
  Utiliser `clickText`, qui invoque l'action sémantique `OnClick` directement.
- **`setEffect()` exige le pont natif.** Sans lui (cas de Robolectric)
  `FxController.setEffect()` renvoie `false` : aucun effet ne peut être installé
  par l'UI. Le rack reste vide quoi qu'on clique.
- **`onRoot()` ne capture que la première racine.** Les popups/dialogues en ont
  une autre ; `screen.png` peut donc être vide alors que l'arbre est correct.
  Le test écrit `screen_r<N>.png` pour chaque racine.

### `fxPreview` — rendu direct des composables FX

`fxPreview` remplace l'écran par un composable FX réel :
`rack`, `add`, `window:<id>`. Le contrôleur est amorcé par `seededController()`
(écriture directe dans `effectIds`/`params`), ce qui contourne le pont natif.
C'est ce mode qui a produit les captures `01..15` — **pas** la navigation de l'app.

## Track Settings V2 (vérifié, commit 393efab)

La fenêtre Track Settings est désormais `ui/tracksettings/TrackSettingsScreen.kt`
(`TrackSettingsWindow`, pages `TsPage.{Track,Velocity,Midi,Range}`).
**`ui/components/TrackSettingsDialog.kt` n'existe plus** — ne pas le chercher.

| Élément | Emplacement réel |
|---|---|
| Fenêtre | `ui/tracksettings/TrackSettingsScreen.kt` → `TrackSettingsWindow` |
| Adaptateur vélocité | `ui/tracksettings/TsVelocity.kt` (`VelocitySettings` ↔ `VelocityConfig`) |
| Bornes de notes | `RangeKeys` (dans TrackSettingsScreen.kt) = adaptateur sur `KeyboardLogic` (`VirtualKeyboard.kt`), 24..108 = C1..C8 |
| Providers moteur | `AudioEngine.trackSustainProvider` / `trackProgramChangeProvider` / `trackVelocityCurveProvider` |

Pièges confirmés :
- **Omni existe toujours.** `midiChannel == 0` = Omni (accepte tous les canaux) ;
  `1..16` = Ch 1..16. Le matching moteur gère les deux cas
  (`MixerViewModel.kt` ~l.2349). Ne pas le supprimer : une version antérieure l'a
  fait à tort et a dû être restaurée.
- `SettingsDrawer.kt` (canaux MIDI) utilise `-1` comme sentinelle OMNI et émet
  `0..15` pour les libellés « Ch.1..Ch.16 ». C'est **volontaire** dans ce widget :
  ne pas « corriger » sans brief explicite.
- La vélocité est appliquée **une seule fois**, par l'engine (`VelocityProcessor`).
  Ne pas la réappliquer côté ViewModel.
- La transposition native intègre déjà l'octave : `transpose + octave*12` en semitones.
- `MidiDevice` (ancienne dialog) a été remplacé par `TsDeviceItem` pour éviter le
  clash avec `android.media.midi.MidiDevice` (import wildcard `ui.components.*`).

## Couleur de piste (créé, vérifié)

`TrackChannel.customColorArgb: Int?` (null = couleur par défaut de l'index).
Résolution unique : `TrackChannel.resolvedColor()` (`VerticalFader.kt`), qui
retombe sur `getTrackThemeColor(id, isMaster)`. **Tous** les sites d'affichage
doivent passer par `resolvedColor()`, jamais par `getTrackThemeColor` directement.
Palettes : `TrackColorPresets` (8 couleurs par défaut, `VerticalFader.kt`) et
`TrackColorExtended` (`SettingsDrawer.kt`). Sélecteur : sous-page
`settingsSubPage == "track_colors"` → `TrackColorsSubPage`.
`MaterialYouPalette.kt` (`MaterialYouStyle`) est du **code mort**, jamais consommé.

## Symboles inexistants (ne pas les chercher)

Un seul nom de brief s'est révélé faux :
`loadSoundfontToSlot` — le vrai est `loadSoundFontForSlot(slotId, path, ...)`
(`viewmodel/MixerViewModel.kt:3112`).

## Clavier virtuel — remplacé (vérifié)

L'ancien clavier a été **supprimé**, ne pas le chercher :
- ~~`VirtualPianoKeyboard`~~ (`ui/components/VirtualPiano.kt`) — supprimé.
- ~~`SplitterKeyboard`~~ (`ui/components/SplitterKeyboard.kt`) — supprimé (jamais câblé).
- `KeyboardLayerMapper` **n'existe plus** dans `ui/components/KeyboardLayerMapper.kt` :
  le fichier ne contient plus que `object KeyPositionMapper` (encore utilisé par le VM).

Nouveau clavier : `ui/components/VirtualKeyboard.kt` (source JSX : `app/assets/VirtualKeyboardPreview.jsx`,
brief : `app/assets/VirtualKeyboard_Agent_Brief.md`). Symboles réels :
`VirtualKeyboardContainer`, `KeyboardKeys`, `KeyboardLine`, `KeyboardTheme`,
`KeyboardThemes` (9 thèmes), `KeyboardChrome`, `KeyboardPressStyle`, `VirtualKeyboardActions`,
`KeyboardLogic`, `KeyboardThemePicker` / `KeyboardThemeSwatch`, et **`object VkbMidi`** (adaptateur
String↔MIDI : `ktToMidi` / `midiToKt`).

**Câblage** (`ui/MixerScreen.kt:~543`) : l'UI parle en notes MIDI (`Set<Int>`), la logique
existante en noms (`Set<String>` `pressedKeys`, VM `onKeyDown/onKeyUp`). La conversion est faite
**uniquement à la frontière** dans `MixerScreen` via `VkbMidi`. `pressedKeys` reste String :
ne pas changer le stockage.

**Persistance** : clés `vkb_theme` (défaut `"classic"`) et `vkb_press_style` (`"color"`/`"led"`),
écrites via `appStatePersistence.saveString` (helpers génériques) et relues dans
`restoreSavedAppState` (`getString`). Réglages dans **Settings → Interface** (`VkbThemeGrid`,
`Segmented`) ; « Reset colors » remet aussi Classic + Color.

**Perf** : `KeyboardKeys` dessine dans `Canvas` + `drawWithCache` (géométrie et dégradés
construits une fois par taille/thème, plus à chaque frame). Ne pas réintroduire de
`Brush.verticalGradient` par touche dans la boucle de dessin.

**Chrome du conteneur** : `KeyboardChrome` prend ses défauts de la **palette app**
(`AppColors.PanelBottom` / `AppColors.Well` / `AppColors.Accent`), pas des valeurs du JSX.
Le conteneur ne suit jamais le thème du clavier.

Pas de poignée de redimensionnement sur le clavier (décision utilisateur). Le bouton
afficher/masquer reste `viewModel.cycleKeyboardExpansion()` via la bottom bar.

## Chaînes FX de surface (TonicPad / DrumPad) — vérifié

`g_tonicFx` / `g_padsFx` (`audio_engine.h:734`, struct `SurfaceFxChainState`) sont
écrits par les setters JNI mais **lisaient jamais** : les FX étaient muets.

- **DrumPad (Pads)** : le sampler a son propre buffer → chaîne DSP réelle et isolée
  (`processSurfaceFx` : low-pass + high-shelf + delay + reverb). `padsAudible` =
  `mDrumSampler.getActiveVoiceCount() > 0`.
- **TonicPad (canal 9)** : vit dans le bus partagé du synthé, **non isolable** par
  bloc. Les FX y passent par des **CC MIDI par canal** (CC91 reverb, CC74 cutoff),
  appliqués par fluidsynth au seul canal 9. Pas de rendu par canal dans ce fork.

Piège : ne pas tenter de soustraire le canal 9 du bus du synthé — impossible.

## Architecture des slots audio

`audioSlots` a **10** entrées (indices 0..9) ; `tracks` n'en a que **8** (indices 0..7).

| Slot | Canal MIDI | Sens |
|---|---|---|
| 0..7 | 0..7 | Pistes du mixer |
| 8 | 8 | Drum |
| 9 | 9 | Pad (`CHANNEL_TONIC_PAD`) |

**Piège** : `tracks.getOrNull(8)` et `getOrNull(9)` renvoient `null` (liste de 8).
Le nom fiable d'un soundfont pour **tous** les slots est
`audioSlots[slot].soundFontPath` — c'est ce qu'utilise `DrumPadScreen`.

## Récupération après crash de sandbox (procédure vérifiée)

Un « sandbox error » perd tout le travail **non commité**. Les événements d'une
conversation restent lisibles via l'API Cloud :

```bash
curl -s "https://app.all-hands.dev/api/v1/conversation/<CONV_ID>/events/search?limit=100&page_id=<N>" \
  -H "Authorization: Bearer $OPENHANDS_API_KEY"
```

Les `ActionEvent` `file_editor` portent `action.command`/`path`/`old_str`/`new_str`/
`file_text` : on peut **rejouer** les éditions (`create` puis `str_replace`) de façon
déterministe, à condition de repartir du même commit de base. Le `page_id` est un
entier croissant (pas le nombre d'items). Méthode utilisée : commit `053cdc6`,
branche de sauvegarde `backup/sandbox-recovery-20261019`.

## Sécurité anti-crash (leçons)

Le sandbox peut redémarrer et **effacer `/tmp`** : toute capture ou artefact laissé
dans `/tmp` est perdu. Règles :

- Commiter **et pousser** après chaque étape qui compile (`git branch -f
  backup/sandbox-recovery-...` puis `git push`). Ne jamais garder de travail
  non commité en fin d'étape.
- Écrire les artefacts sous `tools/jsxref/shots/` (suivi par git), pas `/tmp`.
- `git push` : si le remote contient un ancien token (`ghu_...`) il peut expirer.
  Le rafraîchir avec
  `git remote set-url origin "https://x-access-token:${GITHUB_TOKEN}@github.com/amiel767/NewKeys-.git"`.
- LiveUI : `tools/liveui/start.sh` (démarre) et `tools/liveui/refresh.sh`
  (recompile + relance le render loop, le bridge reste up).
- `tools/jsxref/drive.py` doit **dormir ~1,6 s** après l'écriture de `cmd.json` :
  `status.txt` avance à chaque itération, donc attendre une seule avance capture
  une frame périmée.
- Bridge LiveUI : la commande `keys` (tableau, ex. `["C4","E4","G4"]`) joue un
  accord → l'affichage d'accords est visible dans les captures.

## Session 2026-10-19 (3e passage) — pedale + LiveLooper

- **`SustainPedal`** (`ui/components/v2/SustainPedal.kt`) remplace `SustainPedalButton`
  (supprime). Port Compose 1:1 de `app/assets/SustainPedal.jsx`, **sans label**
  (le texte du JSX ferait ~2,8 dp a 42 dp). Dessin dans le repere 100x100 du JSX
  (`s = px/100`), spring k=340/c=26 a l'appui, k=190/c=19 au retour.
  `BlurMaskFilter` (Skia : `radius = (sigma-0.5)/0.57735`) pour les `feGaussianBlur`.
- **minSdk 24 -> 26** (Android 8+), demande utilisateur.
- **Swing du looper** : `LooperEngine.quantizeAndSwing` retarde les subdivisions
  impaires a l'enregistrement (`swing/100-0.5`). Avant, `setSwing` stockait la
  valeur sans l'appliquer. Pastille `Swing N%` dans l'en-tete Loops (clic = cycle
  50/54/58/62/66), conforme au `Pill>Swing 54%` du JSX.
- **`isPlayingAny`** est desormais tenu par `LooperEngine.updateCases` (toute
  mutation de `cases` passe par la). Avant : declare, jamais assigne.
- **Tete de lecture en REC** : `DpLoopCase` s'ancrait sur `startAtNs` (jamais
  rempli pendant l'enregistrement) -> utiliser `recStartNs`. Et `bars` vaut 1
  pendant le REC : le deduire de `lengthMode`.
- **Capture hors ecran du vrai Compose** : `SustainPedalFramesTest` ecrit une
  frame PNG par tick. Il FAUT `-Proborazzi.test.record=true` sinon
  `captureRoboImage` n'ecrit rien (le test passe quand meme).
- **Harnais `LiveUiLoop`** : nouvelles commandes `drumpadMode` ("pads"/"loops",
  etat Compose local) et `assertText` (le status passe a `err` si le libelle est
  absent, donc le driver ne peut pas valider une frame perimee).

## Session 2026-10-19 (4e passage) — integration FX (package Æther Keys)

Package source : `app/assets/aether-fx-package.zip` (extrait dans `/tmp/fx_pkg`).
Docs `00`..`05`, `fx-window-preview.html` = reference visuelle, 15 `ic_fx_*.xml`
+ SVG, `ref-*.png`, `FxRegistry.kt`. Tests hote : `make test` -> **26 puis 90
checks, 0 failed** (verifie).

- **Etape 1 faite** (commit `20960c9`) : les 17 `fx_*.h` sont copies **octet pour
  octet** dans `app/src/main/cpp/aetherfx/` (verifie par `cmp`), et
  `target_include_directories(native-lib PRIVATE aetherfx ...)` ajoute dans
  `cpp/CMakeLists.txt`. `assembleDebug` OK, APK 41 Mo, `.so` produit pour les
  4 ABI. Le son n'est pas modifie (aucun appel ajoute).
- **`FxRegistry.kt` installe** dans `com/soundstage/mixer/fx/` (seul le
  `package` change : `com.aetherkeys.fx` -> `com.soundstage.mixer.fx`, le reste
  est identique). Compile. Aucune collision : `MixerState.kt` a bien un
  `FxParameters` mais pas de `FxRegistry`/`FxCurve`/`FxKind`.
- **`debug.keystore` est gitignore** : apres un crash/reset sandbox il faut le
  regenerer sinon `assembleDebug` echoue sur `validateSigningDebug` (le build
  natif, lui, a deja reussi). Commande :
  `keytool -genkeypair -keystore debug.keystore -storepass android -alias
  androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
  -dname "CN=Android Debug,O=Android,C=US"`.
- **CORRECTION au doc 04** : dans le FluidSynth 2.6.0 vendu,
  `fluid_synth_nwrite_float` est marque `FLUID_DEPRECATED` (synth.h:382) et
  `fluid_synth_write_float` est mono-mix. Pour l'etape 2 (sortie par canal)
  utiliser **`fluid_synth_process(synth, len, nfx, fx[], nout, out[])`** avec
  `out = {L0,R0,L1,R1,...}` (nout = 2 x canaux). Le doc cite `nwrite_float`.
- Etat actuel a remplacer : `SoundfontEngine::init` met
  `reverb.active=0` / `chorus.active=1` ; le rendu est
  `fluid_synth_write_float` (mono-mix, `renderStereo` l.497) et
  `renderStereo` prend `mMutex` en `try_lock`. `synth.audio-channels` /
  `audio-groups` valent 1 par defaut.
- Rappel modele strips : `FxMixer::kStrips = 11` (0..7 pistes, 8 Drum,
  9 TonicPad, 10 sampler), `kMaster = 15`. `instrumentAllowed` = **0..7
  seulement** ; `soundfontPaused` = 0..7 + source active.
- Etapes restantes : 4 (notes vers sources, gen offsets) -> 5 (JNI) -> 6
  (fenetre Compose) -> 7 (persistance) -> 8 (appareil).
- **Etape 2 faite** (commit `655ffe4`) : `SoundfontEngine::renderChannels(out,
  frames, ch)` rend en **stereo par canal** via `fluid_synth_process` (planar ->
  entrelace) et `renderStereo` est inchange. `audio-channels`/`audio-groups` = 16
  et chorus OFF **seulement** si `setMultiChannelEnabled(true)`. Entrelacement
  verifie hors appareil : erreur 0, somme des canaux == mix mono (critere 1e-5).
- **Etape 3 faite** (commit `c77010c`) : `aetherfx::FxMixer mFx` dans
  `AudioEngine` ; `renderFxPath()` fait strips 0..9 (`processStrip`) + sampler 10
  + `processMaster`, et remplace toute l'ancienne chaine master sur ce chemin.
  `mFx.prepare(sampleRate, capacity)` et les 3 tampons scratch sont dimensionnes
  dans `openAndStartStream` (thread de controle), jamais dans le rappel.
- **Interrupteur** : `AudioEngine::setFxEnabled(bool)` (defaut **false**). Tant
  qu'il est faux, `renderFxPath` renvoie false et le rappel suit l'ancien mix
  **a l'identique** — l'app compile et sonne comme avant. Pour activer : appeler
  `setFxEnabled(true)` **avant** `start()` (le mode multi-canaux se decide a
  l'ouverture du flux). Reste a faire : exposer cet interrupteur via JNI/Kotlin
  (etape 5) et la fenetre (etape 6).
- **Etape 4 faite** (commit `4999b56`) : `drainEvents()` extrait et **partage** par
  `renderStereo`/`renderChannels` (plus de boucle MIDI dupliquee). Routeur de
  notes : quand `FxMixer::soundfontPaused(strip)` est vrai (instrument = source),
  NoteOn/NoteOff/pitch bend vont a la source au lieu de FluidSynth. Pitch bend
  converti 0..16383 -> -1..1. Offsets de generateurs : `refreshTrackGens()` diffe
  contre un cache et pousse `SET_GEN`/`PITCH_SENS` **sur le thread audio** via la
  meme file (le synth n'est pas thread-safe). `setTrackInstrument`,
  `setTrackInstrumentParam` sont l'API de controle.
- **Etape 5 faite** (commits `68f6e56`, `6f52d7c`) : pont JNI complet cote natif
  (`native-lib.cpp`) et Kotlin (`NativeAudioBridge.kt`) — `setFxEnabled`,
  `setTrackInstrument(param)`, `setFxEffect(param/bypass)`, `applyFxPreset`,
  `setFxStripGain/Pan/Mute`, `fxStripPeak`, `fxEffectId`, `fxGetInstrumentId`,
  `fxSoundfontPaused`, `fxSetInstrumentBypass`, `fxApplyInstrumentPreset`,
  `fxGetMeter`, `fxCollectGarbage`, `fxMatchShaper` + constantes
  `FX_STRIP_*`/`FX_SLOTS_*`. **Piege** : `FxMixer` est atteint via `mFx` qui est
  **prive** ; toute nouvelle methode publique d'`AudioEngine` doit etre mise
  **au-dessus** du `private:` (ligne ~888), sinon erreur « private member ».
- Reste : **etape 6 = fenetre Compose** (doc 03, `fx-window-preview.html` +
  `ic_fx_*.xml` comme reference), puis persistance, puis validation appareil.
  C'est la partie la plus volumineuse ; le moteur et le pont sont prets.
- **Etape 6 faite en partie** (commits `34ed14f`, `b3aa0df`) : les 15
  `ic_fx_*.xml` sont copies dans `res/drawable/` (octet pour octet, verifie par
  `cmp`). `fx/FxController.kt` = etat par piste (instrument, 6/8 slots, bypass,
  params) et pousse chaque changement au JNI ; `ui/fx/FxScenes.kt` porte la
  geometrie 825x343 / contenu 779x263 a (20,58) / bandeau des y=214 et les scenes
  des **effets 0-4** (Space, Echo, Tone, Grip, Ceiling) + le potard `FxKnob` ;
  `ui/fx/FxWindow.kt` porte la coquille (en-tete, bypass, presets, fermeture).
  Regle anti-disparition respectee : **une seule** boucle d'animation, changer de
  preset ne recree pas la surface.
  **Piege** : `DrawScope` n'a pas `createLinearGradient` ; utiliser
  `Brush.linearGradient(...)` passe a `drawRect(brush=...)`. Les `canvas px` du
  JSX valent des **dp** (facteur 1:1).
- Reste a faire pour finir l'etape 6 : les **10 scenes d'instruments** (ids 5-14)
  du JSX (piano, organ, sax, strings, guitar, amp, bass, brass, voices, synth),
  puis le rack (cartes + tiroir), puis l'integration dans le mixer.
  Ensuite : etape 7 (persistance) et la **suppression des anciens FX** (demande
  explicite de l'utilisateur, quand tout est implemente).

### Polices (maj 2026-10-19 apres pull)

Les fontes du doc FX **ont ete ajoutees par l'utilisateur** dans le remote
(`app/assets/JetBrains_Mono,Orbitron,Share_Tech_Mono,Sora,Space_Grotesk.zip`,
commit `c2d3243`). Le remote a ete **fusionne** (purement additif, sans conflit).
Les 4 variables utilisees sont copiees dans `res/font/` :
`orbitron.ttf`, `sora.ttf`, `space_grotesk.ttf`, `share_tech_mono.ttf`.
Les OFL sont dans `app/src/main/assets/fonts/`.
`FxFonts.title(id)` suit maintenant la table du doc 03 **sans substitut** :
0/8/9/12/13 Space Grotesk, 1 JetBrains Mono, 2/10/14 Orbitron, 3 Share Tech
Mono, reste Sora. `FxFonts.mono` = Share Tech Mono (libelles techniques).

Le zip de polices reste dans `app/assets/` (comme les autres zip du depot) ; il
n'est pas embarque dans l'APK (`app/assets/` du module est `src/main/assets`).

### Etape 6e faite (commit `4e767b0`)

Les **15 scenes** sont portees : effets 0-4 dans `FxScenes.kt`, instruments 5-14
dans `FxInstrumentScenes.kt`. `FxSceneState` porte l'etat d'animation persistant
(touches du piano, tirettes de l'orgue, particules des cordes, plume du sax,
cordes de la guitare, sourdine du cuivre) : une recomposition ne le remet pas a
zero. `drawFxScene(id, values, t, tm, st)` est le point d'entree unique.
### Etape 6f faite (commit `4733212`) — etape 6 terminee

- `ui/fx/FxRack.kt` : en-tete (compteur `n / 6 effects`, `+ Instrument`,
  `+ Effect`, fermeture) + cartes 158x164 a **~15 img/s** (`delay(66)`), le
  dessin reduit reutilise `drawFxScene`.
- `ui/fx/FxOverlay.kt` : orchestre rack + **fenetre pleine cadence** (une seule)
  + panneau d'ajout. Sonde `peak()` a 30 Hz pour le bandeau.
- `ui/MixerScreen.kt` : le popup `ActivePopup.EFFECTS` ouvre l'overlay.
  L'ancien `EffectsDialog` reste accessible derriere
  `private const val FX_WINDOW_V2` (ligne ~juste avant `fun MixerScreen`) :
  **a supprimer quand l'appareil aura valide** (c'est la demande explicite de
  l'utilisateur de retirer les anciens FX).
- Mapping piste -> strip : `stripForTrack(trackId)` = `trackId - 1` (0..7),
  `trackId == 0` -> master (strip 15). Les ids de piste du mixer sont 1..8.
- Reste : **etape 7 (persistance)**, puis la validation appareil, puis la
  suppression des anciens FX.

### Etape 6g + Etape 7 faites (commits `dd869f7`, `95c4bf9`)

**6g — interactions de la fenetre** (`ui/fx/FxWindow.kt`) : table `STEP` du JSX
pour la quantification de « Surprise me », verrous par appui sur le libelle d'un
potard, comparaison **A/B** (interpolation cubique sur 25 images ≈ 420 ms),
tiroir `Edit` (façonneurs 5/7/8/9/12, maquette v1), suppression par **appui long**
sur une carte avec bandeau **Undo 5,2 s** (`FxRack.kt` + `FxOverlay.kt`).

**7 — persistance** (`fx/FxPersistence.kt`) : un blob JSON par strip sous
`SharedPreferences("aether_fx")`, cles `fx_strip_<n>`. Forme imposee par le doc 01 :
`{track, instrument{id,preset,params}, effects[6|8], ab{a,b,cur}}`, parametres
**normalises 0..1**. `FxController(strip, context)` expose `save()` / `restore()`.
L'overlay restaure au montage et auto-sauvegarde (structures tout de suite,
potards apres **500 ms** d'inactivite).

**Avertissement soundfont** (`ui/fx/FxSfWarning.kt`) : tables `FAMOK`/`KW`/`SHN`
du JSX, bandeau rouge avec `CHOOSE A SOUNDFONT` (ouvre la fenetre soundfonts du
slot) et `USE ANYWAY`.

**Reste** : etape 8 (validation appareil, doc 05), puis la suppression des
anciens FX (`FX_WINDOW_V2` dans `MixerScreen.kt`) sur demande explicite.

### Divergence remote a connaitre (verifie 2026-10-20)

`origin/main` a **2 commits « Add files via upload »** (`0e9ef19`, `ad93dcb`)
absents en local. Ils sont **purement additifs** : 5 fichiers dans `app/assets/`
(`SustainPedal.jsx`, `SustainPedal.kt`, `TrackSettingsPreview.jsx`,
`aether-fx-package.zip`, `brief-integration-sustain-pedal.txt`). Les deux
fichiers `SustainPedal.*` sont **identiques** a ceux deja en local, d'ou un
merge **sans aucun conflit**.

- Verifie avec `git merge-tree --write-tree HEAD origin/main` : arbre propre,
  exit 0. Le resultat du merge garde bien `aetherfx/`, `renderFxPath` (3),
  `applySurfaceFx` (4) et la pastille Swing (3).
- Le remote **ne contient pas** le travail looper/pedale de `main` (pas de
  `v2/SustainPedal.kt`, pas de `SustainPedalFramesTest.kt`, `applySurfaceFx`
  absent de `audio_engine.cpp`). C'est **normal** : ces commits locaux n'ont
  jamais ete pousses (regle « ne pas pousser »).
- Autrement dit : remote = ancien code + 5 assets ; local = code recent.
  **Rien a craindre d'un merge**, mais ne pas faire de `checkout --theirs` ni
  de reset : cela ecraserait tout le travail V2.
- **Ne pas pousser** sans demande explicite (regle AGENTS.md).

## Conventions

- Commits : `[Mixer V2] <résumé> — Compile: OK`, avec
  `Co-authored-by: openhands <openhands@all-hands.dev>`.
- Ne pas pousser sans demande explicite.
- Avant toute modification : rebaser sur le remote (il arrive que l'utilisateur
  ajoute des assets via l'UI GitHub, ce qui crée une divergence sans conflit).
- Le remote peut recevoir des commits « Add files via upload » qui ne touchent
  que `app/assets/` — vérifier avec `git diff --name-only` avant de rebaser.

## Session 2026-10-19 (post-crash) — acquisitions

- **Assignation Loops → DrumPad** : `DpManager` expose maintenant `onAssignLoop`.
  `assigning` vaut vrai pour les onglets `DrumPad` **et** `Loops`. Un seul
  ViewModel call : `assignDrumSampleOrLoop(padId, name, path, isLoop = true)`.
- **AssignBar** suit `pads.size` (8 ou 16), plus de boucle figée sur 8.
- **Icônes** : `IconPatchesV2` (cartes empilées, remplace `IconFolderV2` dans le
  top bar) et `IconUsbV2` (trident USB, remplace `Icons.Default.Usb`) vivent dans
  `v2/MixerIconsV2.kt`. `AospSettingItem` accepte `icon: ImageVector?` + `leading`.
- **Piège typo Compose** : un `Text` dans une Box courte hérite du `lineHeight`
  Material3 (24 sp) et paraît décalé. Poser `lineHeight = fontSize` explicitement.
- **TonicPad** (`DpNoteKey`) : teinte bleu ciel fixe `203 + (idx % 4) * 6`, plus de
  rainbow `idx * 30 + 205`. Glow animé via `tween` + `shadow(spotColor = tint)`.
- **Looper** : action `"clearAll"` dans `LooperEngine.longPressCaseAction`
  (bypass le remplacement par case).

## Session 2026-10-19 (2e passage) — suppression Loops morte + captures

- **Fenêtre Loops morte supprimée** : `ui/components/LoopsDialog.kt` et
  `ui/components/TopBar.kt` (40 Ko, aucun appelant externe) sont supprimés.
  `ActivePopup.LOOPS` retiré de `model/MixerState.kt` ; la branche
  `ActivePopup.LOOPS -> { … }` retirée de `ui/MixerScreen.kt`, ainsi que le
  `isLoopsPanelOpen` du garde d'overlay. Les setters VM `openLoopsDialog` /
  `closeLoopsDialog` / `closeLoopsPanel` n'existent plus : ne pas les chercher.
  Attention : « loops » côté utilisateur = **page DrumPad + fenêtre Drumkit**
  (catégorie Loop), **pas** ce popup mort.
- **`SustainPedalButton` refait** : plus de mot SUSTAIN ni de pastille ; une
  plaque bascule de -17° (repos) à -3° (appuyé) sur charnière gauche, avec glow
  au sol. Piloté uniquement par `isActive`.
- **Sustain clavier virtuel** : `MixerScreen.kt` passe désormais
  `uiState.isSustainActive || uiState.isMidiPedalPressed` au clavier, donc le
  pédalier MIDI externe (CC64) anime le clavier comme le bouton.
- **Soundfonts** : les lignes de `SoundfontWindow.kt` (presets + fichiers)
  utilisent un fond opaque (`0xFF1F2836` / `0xFF19212E`), un contour léger et une
  ombre (`shadow(4.dp, RowShape)`) au lieu des dégradés translucides qui
  paraissaient flous.
- **Stubs câblés** : `DrumPadScreen.kt` — `DpPadEditor.onChange` appelle
  `updateDrumPadCustomization(id, label, style, isLoopMode)` ; l'onglet Scenes
  reçoit `uiState.scenes` et `onLoadScene` appelle `selectScene(name)`.
  `ScenesContent` (DrumPadManager.kt) affiche le vrai nom du preset.
- **Harnais de capture** : `tools/jsxref/capture_all.py` réécrit. `status.txt`
  renvoie `ok <seq>` et le driver attend l'ack de **sa** séquence (fini les
  frames périmées/doublons), puis attend 0,5 s que les animations d'entrée se
  posent. Sortie 891×411 (moitié de la résolution native). 15 PNG + 2 GIF
  (`pedal_sustain`, `pedal_midi`). Setlists volontairement exclu de la passe.
- **Hook test** : `MixerViewModel.debugSetMidiPedal(pressed)` simule CC64 ;
  `LiveUiLoop.kt` accepte `midiPedal`, `layer`, `keyboard` dans `cmd.json`.


## Perf & crash (session 2026-10-21)

Causes prouvees par le logcat `app/assets/logcat_2026-10-20_15-46-35.zip`
(Python `zipfile` - `unzip` absent) :

- **Crash** : `FxRegistry.info(id) = all[id]` sans borne. L'`instrumentId == -1`
  (sentinel "pas d'instrument Aether") passait `all[-1]` depuis
  `FxController.snapshot()/save()` via le `snapshotFlow` de `FxOverlay.kt:76`.
  Corrige par `all.getOrElse(id) { all[0] }` + sentinel gere dans `snapshot()`.
- **Lenteur au lancement** : `W/Looper - Slow dispatch took 16935ms ...`
  `c=MediaScannerConnection$$ExternalSyntheticLambda1`. Le scan media de
  demarrage envoyait *tous* les soundfonts/loops ; on ne scanne plus que les
  **dossiers** (`FileManager.ensureDirectoriesExist`).

Autres points de perf (verifies) :

- `FxRackStrip` : **une seule** horloge 15 im/s pour toutes les `FxCard`
  (avant : un `LaunchedEffect` par carte). `FxCard` recoit `t: Float`.
- `DrumPadScreen` : ticker 16 ms `nowNs` compose **uniquement** si
  `padMode == "loops"` ; ticker beat uniquement si metro ou
  `uiState.looper.isPlayingAny`.
- `padCount` est derive de `uiState.padCount` (persiste `KEY_PAD_COUNT`).
- `AssignBar` (DrumPadManager) : `pads.size` vaut **24** (`initialDrumPads`),
  donc la barre affichait 24 cellules ; plafonnee a 8/16.
- `TrackBottomRow` : animation corbeille *gated* par `showTrashBubble`.
- `MaterialYouNativeMixer.kt`, `BottomBar.kt`, `DrumPadDialog.kt`,
  `AmbientStudioBackground.kt` ne sont **pas** cables a `MixerScreen` (code
  mort) : leurs boucles ne tournent pas.
- **Aucune** boucle `while(true)/while(isActive)` sans suspension ne subsiste
  dans `ui/` (verifie).

Long-press sur une ligne d'echantillon (onglet Samples du `DpManager`) :
360 ms -> indicateur 4 barres (`DpPreviewBars`, 0,5->0,86 s, scaleY .35->1),
relacher annule. Tap = selection.

## Assets de reference recus (2026-10-21, commit `f6535d7`)

Trois fichiers ajoutes par l'utilisateur dans `app/assets/` — **sources d'autorite**.

### `SettingsTabIcons.jsx` + `SettingsTabIcons.kt` (icones des onglets Settings)
Famille unique : grille **24x24**, trait **2 px**, `strokeLinecap/Join = round`.
Quatre icones, en trait (sauf les points du MIDI, remplis) :

| Onglet | Nom Compose | Contenu |
|---|---|---|
| Audio | `SettingsTabIcons.Audio` | waveform symetrique, 5 barres, la plus haute au centre |
| Interface | `SettingsTabIcons.Interface` | fenetre applicative : barre d'en-tete + panneau lateral |
| MIDI | `SettingsTabIcons.Midi` | connecteur DIN 5 broches : anneau + 5 points (remplis) |
| System | `SettingsTabIcons.System` | puce avec broches sur les 4 cotes |

Le `.kt` fourni est **pret a copier** (package a adapter). Noir dans le fichier :
la couleur reelle vient de `tint`. Usage : `Icon(imageVector = SettingsTabIcons.Interface,
tint = if (selected) Color(0xFF8FB2E8) else Color(0xFF8E99AD), modifier = Modifier.size(24.dp))`.
Ne requiert **que** `compose-ui` + `compose-material3.Icon` (pas de
`material-icons-extended`).

**Actuel a remplacer** : `SettingsScreen.kt:1876` `SetIconKind` (canvas maison) ;
mapping l.633-638 : Audio->`Wave`, Interface->`Swatch`, Midi->`Midi`, System->`Gear`.
Appel l.624 : `SetIcon(tab.iconKind(), c, Modifier.size(18.dp))`.
Reference visuelle exacte : le `SettingsTabIconsDemo` en bas du `.jsx`
(largeur 230, lignes h=52, radius 16, padding 0 16, gap 16, icone 24,
actif `#8FB2E8` / inactif `#8E99AD`).

### `fx-screens-spec.zip` (specification exacte de la fenetre FX)
Contenu : `00-LIS-MOI-EN-PREMIER.md`, `01-ecrans-a-reproduire.md`,
`fx_layout_spec.json`, `compare_refs.py`, `references/` (32 PNG),
`reference_source_fx_window.html`.

**CONTRADICTION A SIGNALER** : la regle du LISEZ-MOI dit « source 1 =
`reference_source_fx_window.html` gagne », mais ce fichier est **VIDE (0 octet)**
dans le zip. Sources utilisables : le JSON + les PNG. Ne pas choisir en silence.

- `fx_layout_spec.json` : boites en px logiques, origine = coin du panneau.
  Toutes les scenes sont **837,6 x 348,2**. Champs par effet :
  `id, name, type, font, accent, knobs[], presets[], layout{w,h,knobs[],body,title,buttons}`.
  **Aucune** zone de scene (pas de cle `scene`) : les controles dessines
  (tirettes orgue, pistes synthe, micros ampli, pad XY) sont dans le code de
  reference — **introuvable ici** (le HTML est vide). A demander a l'utilisateur.
- `references/ref-NN-*.png` : **1728 x 800** (2x). Presets : 1728x1600
  (1728x1200 pour les instruments). Tablette : 1230x582.
- `compare_refs.py` : PASS si **<= 1,5 %** de pixels different de > 24/255 ;
  taille de capture **exactement** egale a la reference, sinon FAIL.

**Ecart mesure Kotlin <-> spec** (preuve) : `knobLayout` (`FxScenes.kt:84-101`)
ne couvre que les effets **0..4** ; les instruments 5..14 n'ont **aucun** potard
positionne (`AB()` absent de `FxInstrumentScenes.kt`). Et meme pour 0..4 les
coordonnees divergent de la spec, ex. effet 0 :

| Potard | JSON (centre) | Kotlin |
|---|---|---|
| Size | (415,2 ; 93,4) | (389 ; 34) |
| Decay | (641,5 ; 124,0) | (490 ; 97) |
| Pre-delay | (697,3 ; 192,7) | (572 ; 193) |
| Damp | (540,8 ; 247,8) | (490 ; 194) |
| Width | (289,7 ; 247,8) | (288 ; 194) |
| Mix | (133,1 ; 192,7) | (180 ; 193) |
| Diffuse | (189,0 ; 124,0) | (288 ; 34) |

Effet 10 (Amp) : 7 potards alignes a y=146,2, x = 91,3 / 172,5 / 253,8 / 335,1 /
416,3 / 497,5 / 761,5 ; 7 boutons (Surprise me, A, CLEAN, CRUNCH, LEAD, BOOST,
TREM). Effet 4 : 6 potards dont `Amount` enorme (138,1). Effet 5 : 3 potards a
y=314,7 + boutons Surprise me / A / Edit.

### Anciens assets toujours presents (ne pas confondre)
`app/assets/aether-fx-package.zip` (docs C++/JNI + `fx-window-preview.html`
728 l. + icones) et `JetBrains_Mono,Orbitron,...zip` (polices). Le nouveau
`fx-screens-spec.zip` **complete** l'ancien (mesures), il ne le remplace pas.

## Assets de référence reçus (2026-10-21, commit `f6535d7`)

Trois fichiers ajoutés par l'utilisateur dans `app/assets/` — **sources d'autorité**.

### `SettingsTabIcons.jsx` + `SettingsTabIcons.kt` (icônes des onglets Settings)
Famille unique : grille **24×24**, trait **2 px**, `strokeLinecap/Join = round`.
Quatre icônes, en trait (sauf les points du MIDI, remplis) :

| Onglet | Nom Compose | Contenu |
|---|---|---|
| Audio | `SettingsTabIcons.Audio` | waveform symétrique, 5 barres, la plus haute au centre |
| Interface | `SettingsTabIcons.Interface` | fenêtre applicative : barre d'en-tête + panneau latéral |
| MIDI | `SettingsTabIcons.Midi` | connecteur DIN 5 broches : anneau + 5 points (remplis) |
| System | `SettingsTabIcons.System` | puce avec broches sur les 4 côtés |

Le `.kt` fourni est **prêt à copier** (package à adapter). Noir dans le fichier :
la couleur réelle vient de `tint`. Usage : `Icon(imageVector = SettingsTabIcons.Interface,
tint = if (selected) Color(0xFF8FB2E8) else Color(0xFF8E99AD), modifier = Modifier.size(24.dp))`.
Ne requiert **que** `compose-ui` + `compose-material3.Icon` (pas de
`material-icons-extended`).

**Actuel à remplacer** : `SettingsScreen.kt:1876` `SetIconKind` (canvas maison) ;
mapping l.633-638 : Audio→`Wave`, Interface→`Swatch`, Midi→`Midi`, System→`Gear`.
Appel l.624 : `SetIcon(tab.iconKind(), c, Modifier.size(18.dp))`.
Référence visuelle exacte : le `SettingsTabIconsDemo` en bas du `.jsx`
(largeur 230, lignes h=52, radius 16, padding 0 16, gap 16, icône 24,
actif `#8FB2E8` / inactif `#8E99AD`).

### `fx-screens-spec.zip` (spécification exacte de la fenêtre FX)
Contenu : `00-LIS-MOI-EN-PREMIER.md`, `01-ecrans-a-reproduire.md`,
`fx_layout_spec.json`, `compare_refs.py`, `references/` (32 PNG),
`reference_source_fx_window.html`.

**CONTRADICTION À SIGNALER** : la règle du LISEZ-MOI dit « source 1 =
`reference_source_fx_window.html` gagne », mais ce fichier est **VIDE (0 octet)**
dans le zip. Sources utilisables : le JSON + les PNG. Ne pas choisir en silence.

- `fx_layout_spec.json` : boîtes en px logiques, origine = coin du panneau.
  Toutes les scènes sont **837,6 × 348,2**. Champs par effet :
  `id, name, type, font, accent, knobs[], presets[], layout{w,h,knobs[],body,title,buttons}`.
  **Aucune** zone de scène (pas de clé `scene`) : les contrôles dessinés
  (tirettes orgue, pistes synthé, micros ampli, pad XY) sont dans le code de
  référence — **introuvable ici** (le HTML est vide). À demander à l'utilisateur.
- `references/ref-NN-*.png` : **1728 × 800** (2×). Presets : 1728×1600
  (1728×1200 pour les instruments). Tablette : 1230×582.
- `compare_refs.py` : PASS si **≤ 1,5 %** de pixels diffèrent de > 24/255 ;
  taille de capture **exactement** égale à la référence, sinon FAIL.

**Écart mesuré Kotlin ↔ spec** (preuve) : `knobLayout` (`FxScenes.kt:84-101`)
ne couvre que les effets **0..4** ; les instruments 5..14 n'ont **aucun** potard
positionné (`AB()` absent de `FxInstrumentScenes.kt`). Et même pour 0..4 les
coordonnées divergent de la spec, ex. effet 0 :

| Potard | JSON (centre) | Kotlin |
|---|---|---|
| Size | (415,2 ; 93,4) | (389 ; 34) |
| Decay | (641,5 ; 124,0) | (490 ; 97) |
| Pre-delay | (697,3 ; 192,7) | (572 ; 193) |
| Damp | (540,8 ; 247,8) | (490 ; 194) |
| Width | (289,7 ; 247,8) | (288 ; 194) |
| Mix | (133,1 ; 192,7) | (180 ; 193) |
| Diffuse | (189,0 ; 124,0) | (288 ; 34) |

Effet 10 (Amp) : 7 potards alignés à y=146,2, x = 91,3 / 172,5 / 253,8 / 335,1 /
416,3 / 497,5 / 761,5 ; 7 boutons (Surprise me, A, CLEAN, CRUNCH, LEAD, BOOST,
TREM). Effet 4 : 6 potards dont `Amount` énorme (138,1). Effet 5 : 3 potards à
y=314,7 + boutons Surprise me / A / Edit.

### Anciens assets toujours présents (ne pas confondre)
`app/assets/aether-fx-package.zip` (docs C++/JNI + `fx-window-preview.html`
728 l. + icônes) et `JetBrains_Mono,Orbitron,...zip` (polices). Le nouveau
`fx-screens-spec.zip` **complète** l'ancien (mesures), il ne le remplace pas.

## Session 2026-10-21 (suite) — débordement clavier + Phases 4/5

### Débordement Virtual Keyboard (état au repos) — CORRIGÉ
`MixerScreen.kt` : l'espaceur de la bottom bar utilisait une hauteur **estimée**
(`48 + pianoRestStripHeight + 11`, ≈65 dp pour 1 piste) alors que le conteneur
clavier mesure ~74 dp au repos → chevauchement de ~10 dp.

Correction : la hauteur réelle du conteneur est **mesurée** (`onSizeChanged`) et
**verrouillée au repos** (`keyboardRestHeight`, seulement quand
`!isKeyboardLayerExpanded`), puis utilisée pour l'espaceur. L'état **déployé**
n'est pas touché (il continue de flotter au-dessus, comportement voulu).

Mesure : conteneur au repos à **317,7 dp**, barre **272→318 dp** → collés, plus
de chevauchement. Déployé inchangé (conteneur à 276 dp).

### Phase 4 — TrackSettings — FAIT
- Scrim : `.blur(18.dp)` sur le contenu de fond quand `activePopup` est
  `TRACK_SETTINGS`/`VELOCITY_SETTINGS`, + scrim `0x66000000` cliquable
  (`zIndex(119f)`) qui ferme la fenêtre (`closeTrackSettings()`) et bloque les
  touches derrière.
- Page **Note range** : les « puces » blanches sous les touches (blanches et
  noires) sont retirées.
- `CurveCanvas` : `pointerInput(Unit)` → `pointerInput(vel)`, et le callback passe
  par `rememberUpdatedState(onPoint)`. Sans ça la closure restait figée sur la
  première `vel` → chaque nouveau point écrasait le précédent (Velocity « ne
  prend pas effet »).

### Phase 5 — Fluidité thumb fader — FAIT
`CustomVerticalFader.kt` : `LaunchedEffect(value)` téléportait `localValue`
(saut). Remplacé par `animate(...)` (220 ms, FastOutSlowInEasing) quand
`!isDragging` ; le drag suit toujours le doigt directement.

### Pièges vérifiés
- Les 2 namespaces et les doublons `SettingsScreen`/`SetlistsScreen` restent.
- Aucun commit/push. Captures sous `app/src/test/screenshots/` (outils, pas tests).
- `TrackSettingsCaptureTest.kt` ajouté (capture scrim, non commité).

## Session 2026-10-21 (suite) — débordement clavier + Phases 4/5

### Débordement Virtual Keyboard (état au repos) — CORRIGÉ
`MixerScreen.kt` : l'espaceur de la bottom bar utilisait une hauteur **estimée**
(`48 + pianoRestStripHeight + 11`, ≈65 dp pour 1 piste) alors que le conteneur
clavier mesure ~74 dp au repos → chevauchement de ~10 dp.

Correction : la hauteur réelle du conteneur est **mesurée** (`onSizeChanged`) et
**verrouillée au repos** (`keyboardRestHeight`, seulement quand
`!isKeyboardLayerExpanded`), puis utilisée pour l'espaceur. L'état **déployé**
n'est pas touché (il continue de flotter au-dessus, comportement voulu).

Mesure : conteneur au repos à **317,7 dp**, barre **272→318 dp** → collés, plus
de chevauchement. Déployé inchangé (conteneur à 276 dp).

### Phase 4 — TrackSettings — FAIT
- Scrim : `.blur(18.dp)` sur le contenu de fond quand `activePopup` est
  `TRACK_SETTINGS`/`VELOCITY_SETTINGS`, + scrim `0x66000000` cliquable
  (`zIndex(119f)`) qui ferme la fenêtre (`closeTrackSettings()`) et bloque les
  touches derrière.
- Page **Note range** : les « puces » blanches sous les touches (blanches et
  noires) sont retirées.
- `CurveCanvas` : `pointerInput(Unit)` → `pointerInput(vel)`, et le callback passe
  par `rememberUpdatedState(onPoint)`. Sans ça la closure restait figée sur la
  première `vel` → chaque nouveau point écrasait le précédent (Velocity « ne
  prend pas effet »).

### Phase 5 — Fluidité thumb fader — FAIT
`CustomVerticalFader.kt` : `LaunchedEffect(value)` téléportait `localValue`
(saut). Remplacé par `animate(...)` (220 ms, FastOutSlowInEasing) quand
`!isDragging` ; le drag suit toujours le doigt directement.

### Pièges vérifiés
- Les 2 namespaces et les doublons `SettingsScreen`/`SetlistsScreen` restent.
- Aucun commit/push. Captures sous `app/src/test/screenshots/` (outils, pas tests).
- `TrackSettingsCaptureTest.kt` ajouté (capture scrim, non commité).

## Session 2026-10-21 (5e passage) — récupération après crash + toolchain

Conversation source : `23255f8abab04b489d05d2e6b5c0ee18` (`sandbox_status=ERROR`).
Le travail de la session précédente (fenêtre FX, coque `fx-shell-final.zip`) n'avait
**jamais été poussé** : il a été perdu avec le sandbox.

- **Toolchain provisionné** dans le sandbox : `JAVA_HOME=/workspace/jdk/jdk21`,
  `ANDROID_HOME=/workspace/android-sdk` (platform 36, build-tools 36.0.0,
  NDK 27.0.12077973, CMake 3.22.1). `local.properties` et `debug.keystore` sont
  gitignorés et doivent être régénérés après chaque crash.
  Wrapper : `/workspace/build.sh <tâche>`.
- **Récupération** : les actions `file_editor` et les commandes terminal de la
  trajectoire (9423 événements, `GET /api/v1/app-conversations/<id>/download`) ont
  été **rejouées** dans l'ordre chronologique → arbre identique au `git status`
  perdu. Une seule édition `str_replace` a échoué (fichier réécrit plus tard,
  bénin) ; un import dupliqué (`MutableInteractionSource` dans `MixerScreen.kt`)
  était un artefact de rejeu, corrigé à la main.
- **Branché/sécurisé** : `FxModule` et `FxShellStore` n'indexent plus
  `FxRegistry.all[id]` sans borne (utilisent `FxRegistry.info(id)`) — même classe
  que le crash `all[-1]` déjà corrigé. La puce Soundfont de `FxWindowHost` n'est
  plus un callback vide.
- **Vérifié** : `:app:compileDebugKotlin` et `:app:assembleDebug` → BUILD SUCCESSFUL
  (APK 42,6 Mo). Poussé sur `recovery/fx-window-shell`.
- **Reste** : intégrer `FxWindowHost` dans `MixerScreen` (aucun appelant hors test),
  puis la validation appareil.

## Session 2026-10-21 (reprise FX window · outillage)

- **Spec de référence dépaquetée** : `app/assets/fx-screens-spec.zip` contient
  `compare_refs.py`, `fx_layout_spec.json` (mesures par effet), `references/`
  (33 PNG : `ref-NN-*.png` 2x, `ref-NN-*-presets.png`, `ref-tablet-*.png`) et
  les deux briefs. `aether-fx-package.zip` contient les sources natives
  (`fx_*.h`), `fx-window-preview.html` (147 Ko, LA source d'autorité citée par
  `FxGeometry.kt`, `FxWindowHost.kt`, `FxScenes.kt`) et `gen_kotlin.cpp`.
  Extraits dans `app/assets/fx-screens-spec/` et `app/assets/aether-fx-package/`
  (gitignorés : les zips committés sont la source de vérité).
- **`reference_source_fx_window.html` est VIDE (0 octet) dans le zip livré** —
  défaut du paquet. L'équivalent réel est `fx-window-preview.html`. Ne pas
  perdre de temps à chercher : il n'existe nulle part dans l'historique.
- **Mesures réelles du panneau** : `fx_layout_spec.json` donne w=837,6 h=348,2
  (pas 825×343). Les références sont en 1728×800 = 2x de 864×400. Le
  `FxWindowCaptureTest` capture en 2310×1070 (densité 440 dpi) → normaliser
  avant comparaison.
- **Chaîne de comparaison complète** :
  1. `./gradlew :app:recordRoborazziDebug --tests "...FxWindowCaptureTest" --no-daemon --max-workers=2`
     → écrit `app/src/test/screenshots/fx_window_{amp,rack}.png`.
     (`testDebugUnitTest` seul ne produit RIEN : il faut la tâche `recordRoborazziDebug`.)
  2. `python3 tools/fxcompare/compare_scaled.py <capture.png> <reference.png>`
     → même seuil que `compare_refs.py` (1,5 %, delta 24/255) mais redimensionne
     d'abord (le script fourni exige des tailles strictement identiques).
- **Baseline mesurée** : capture amp vs `ref-10-amp.png` = **17,52 %** d'écart
  (FAIL, attendu : le rendu n'est pas encore aligné). C'est le point de départ
  chiffré, plus une inconnue.
- **Outillage installé** : `tools/jsxref` (npm, 89 paquets : react, esbuild,
  puppeteer-core), Python PIL 13.0.0 + numpy 2.5.4, node v24.21.0 / npm 11.21.0.
- **Bloqueur unique confirmé** (questionnaire §8.3) : le panneau est en
  `fillMaxSize()` alors que sa géométrie interne est en repère 825×343 × `scale`
  (= 1f au seul appelant). Il manque la boîte logique + un scale calculé une
  seule fois à l'extérieur (patron déjà correct dans `FxWindow.kt:180-194`).

## Brief B8-1 exécuté (2026-10-21)

Commit `45c5632` sur `recovery/fx-window-shell` (poussé). `clean assembleDebug` OK (APK 42,6 Mo).

- **Géométrie retenue : 825×343**, pas 837,6×348,2. Le JSON mesure bien 837,6 mais tout le
  système de scènes (`FxStyle.PWL/PHL`, rail `RW=PWL`, canvas mini `.cd` en 136×160) est
  bâti sur 825×343. Un panneau à 837,6 laisserait une bande morte que les scènes ne
  peignent pas. Décision documentée, écart signalé au demandeur.
- **Cause racine du « rendu catastrophique »** : `FxOpenPanel` était en `fillMaxSize()` avec
  la géométrie en 825×343 × `scale`(=1f). Corrigé par la boîte logique + le scale unique.
- **Cause racine n°2, trouvée par la mesure** : un `DrawScope` travaille en **pixels**, or les
  scènes sont écrites en unités logiques 1 unité = 1 dp. Sans `scale(density, density)` la
  scène ne remplissait que le coin haut-gauche 825×343 **px** (≈30 % de la surface en 440 dpi).
  C'est ce facteur, et non le canevas, qui produisait le rendu tronqué.
- **Le canvas de scène est en (0,0)**, pas centré : le fond `.xN` de la référence couvre
  toute la fenêtre et le canvas n'en occupe que le coin haut-gauche. Le host peint donc
  `fxPanelBackground(id)` plein écran quand un effet est ouvert.
- **`rail()` n'existe que pour les instruments (id ≥ 5)**. Preuve : `ref-04-solar-ceiling.png`
  reste beige (lum 121→174) jusqu'en bas, alors que `ref-10-amp.png` est sombre en bas.
  Le vrai `drawFxRail()` de `FxScenes.kt` est câblé dans le panneau ; ma barre maison
  `#1B1F2B` faisait doublon avec une mauvaise couleur et a été supprimée.
- **Conflit `.x2`** : la règle est déclarée deux fois (grille ardoise l.54, face rouge l.104).
  La preuve `ref-02-prism-tone.png` = (180,26,43) tranche pour la face rouge.
- **Baselines mesurées** (capture 2310×1070 → ref 1728×800, seuil 1,5 %) :
  `amp 24,2 %` (était 69 % au départ), `echo 41,9 %`. Reste au-dessus du seuil : l'écart
  résiduel est le rasterisage navigateur vs Compose/Skia, pas la mise en page.
- **Reste** : intégrer `FxWindowHost` dans `MixerScreen` (toujours sans appelant hors test).

## B8-1, 2e passe : géométrie corrigée et rail tranché (2026-10-21)

Commit `d0be41d`. `clean assembleDebug` + `recordRoborazziDebug` OK (APK 42,6 Mo).

### Géométrie de référence : 851x394, pas 825x343 ni 840x389
Mesure sur les PNG : 1728x800 px a S = 2,031, donc **851x394** unités logiques.
La bande de scene (`.bd`, classe `.xN`) fait **825x343 et est inseree a (13,42)**,
pas en plein cadre. Ce (13,42) recoupe deux regles CSS : marge de page 12 px
(`#win{left:12px}`) et header `.wh{height:42px}`. Le `BAND_Y` = 42 EST la hauteur
du header, par construction.
- Le test capturait 840x389 : le scale tombait a 0,987 et forçait un
  reechantillonnage. Aligne sur 851x394 (scale = 1 exact).
- **Un `Canvas` Compose ne clippe pas.** Les scenes remplissent depuis
  `(-OXL,-OYL)`, ce qui debordait sur le header et les marges : c'est pourquoi le
  brun couvrait tout. `clipToBounds()` sur la boite de bande corrige.
  Gain mesure : amp 21,6 % -> **16,6 %** (c'etait 69 % au depart).

### `rail()` : le brief et DeepSeek avaient tort
- `RS` dans le preview n'a **aucune cle 10 (ampli) ni 11 (basse)**. Leurs fonctions
  `amp()`/`bass()` n'appellent **pas** `rail()`. Leur « Level » est un knob normal
  (`FxKnobBoxes`) et le panneau descend jusqu'en bas.
- Ma condition `id >= 5` leur dessinait donc un rail en trop : d'ou le **double
  LEVEL** (le knob a x=738 ET le slider en bas). Corrige par
  `FxStyle.hasRail()` = ids 5,6,7,8,9,12,13,14.
- L'ancien code passait `params.lastOrNull()` : faux. `rail(x, v[n], id)` prend un
  indice **variable** selon l'instrument (piano v[8], orgue v[30], strings v[9],
  guitar v[10], synth v[31]). Ajoute `FxStyle.railLevelIndex()`.
- Preuve croisee : `ref-04-solar-ceiling.png` (un effet) reste beige jusqu'en bas ;
  `ref-10-amp.png` est sombre en bas et le mot LEVEL est absent de `amp()`.

### Modele de coordonnees des knobs
Les boites de `FxKnobBoxes` sont en coordonnees **fenetre** (elles incluent l'origine
du contenu) : Gain a x=68 alors que le canvas `.bd` commence a x=20. Les knobs sont
places en `BAND_X + cx`, `BAND_Y + cy`. Le `withTransform` du canvas n'y touche pas,
il est interne au DrawScope.

### Reste
- amp 16,6 %, echo 38,7 %. Repartition de l'ecart **uniforme** sur toutes les bandes
  (24 a 77) : plus aucun decalage structurel, c'est du rendu (navigateur vs
  Compose/Skia). Le seuil de 1,5 % de `compare_refs.py` est calibre pour deux rendus
  du meme moteur et ne peut pas juger un portage inter-moteurs.
- `FxWindowHost` n'a toujours aucun appelant dans l'app (test seulement).
- `ref-10-amp-presets.png` est **corrompu** dans le paquet (131 Ko, tronque).

## B8-1, 3e passe : fond d'effet en degrade, chrome mesure (2026-10-21)

Reponse a l'analyse DeepSeek (share/8bztzirywj195zbouv). Verdict : **un point juste,
un point faux, un point juste mais pour une autre raison.**

### DeepSeek a raison : le fond ne remplissait pas la bande
Mon `PANEL_BG` etait une couleur **plate**. Or les regles `.xN` du CSS sont des
**degrades** : `.x1` = `linear-gradient(#41382a,#1f1b14)`. Une couleur plate rendait
la bande trop sombre et trop uniforme. Ajoute `PANEL_BG_GRADIENT` (haut/bas releves
sur les references) et `bg()` l'utilise desormais.
- Piege : `Brush.verticalGradient` sans bornes calcule `endY` sur la taille du
  **DrawScope**, qui est le canvas en **pixels**, pas en unites logiques. Le degrade
  etait donc etale sur 343*2,75 px au lieu de 343. Il faut `startY = -OYL`,
  `endY = -OYL + h`. Gain : echo 46,4 % -> **35,7 %**.

### DeepSeek a tort : le header n'herite PAS du fond de l'effet
Elle deduit du CSS (`.ph` sans `background`, donc herite de `.pn`) que le header doit
etre de la couleur de l'effet. Le rendu reel dit l'inverse. Mesure de la mediane par
rangee sur trois references dont deux a fond clair :

| px y | Solar Ceiling (fond beige) | Prism Tone (fond rouge) | Echo (fond brun) |
|---|---|---|---|
| 10-70 | `#0f1622` -> `#0b111d` | `#0f1622` -> `#0b111d` | `#0f1622` -> `#0b111d` |
| 81+ | `#efe9db` beige | `#b22130` rouge | `#41382a` brun |

Le header est **chrome bleu nuit identique pour les trois**, y compris Solar dont le
fond est beige clair. Donc le header est **opaque**, pas transparent. Idem en bas :
a y=392 la reference est `#06090e`, du chrome, pas la couleur d'effet.

### Ce qui est vrai : la bande doit aller d'un bord a l'autre
DeepSeek a raison que la bande etait trop etroite en x. Mais la bonne conclusion est
que **`.pn` remplit tout et porte la couleur**, tandis que **`.bd` reste insere a
(13,42)** en 825x343. J'ai d'abord essaye le plein cadre (faux : la reference a du
chrome en haut ET en bas, et l'effet commence a y=41, pas y=0). Etat final : chrome
plein cadre + bande inseree, ce qui reproduit la reference.

### Etat
- amp 16,6 %, echo **35,7 %** (etait 69 / 44 au depart).
- Ecart **uniforme**, sans point chaud : scene 53/70, droite 45/54, header 34/28.
  Le header est la zone la *moins* fausse, ce qui confirme qu'il est correct.
- Reste = rendu inter-moteurs (police, antialiasing des knobs), pas de structure.
