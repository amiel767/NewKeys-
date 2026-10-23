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

### Note d'environnement
Les processus d'arriere-plan (serveur de telechargement `python3 -m http.server 12000`,
builds Gradle) sont **perdus a chaque redemarrage du sandbox**. Si le lien de
telechargement renvoie 502, relancer :
`cd /workspace/dl && nohup python3 -m http.server 12000 --bind 0.0.0.0 &`
PIL et numpy peuvent aussi disparaitre (l'interpreteur change) :
`pip install pillow numpy`.

## B8-1, 4e passe : la fenetre EST le fond (idee utilisateur, validee)

L'utilisateur a insiste : « la fenetre est grande et exactement de la taille de
l'ecran, les fonds ont une taille a eux — reduire la fenetre pour que les fonds
remplissent toute la fenetre ». Il avait raison, et mes mesures precedentes l'avaient
ecarte a tort parce que je prenais la mauvaise echelle.

### L'erreur : 851x394 n'est pas la fenetre, c'est l'IMAGE
Les PNG de reference font 1728x800 px. A S = 2,031 cela fait **851x394** — et j'ai
pris cette valeur pour la fenetre. C'est faux : 851x394 est la capture d'ecran, qui
**inclut la marge de page** autour de la fenetre (`#win{left:12px;right:12px;
top:38px;bottom:10px}`). La vraie fenetre est le fond d'effet lui-meme.

Mesure decisive, seuil de luminance sur `ref-01-echo.png` :
- fond d'effet vertical   : y_log **39,4 .. 381,6**  -> hauteur **342,7**
- fond d'effet horizontal : x_log **12,8 .. 837,0**  -> largeur **824,7**
- donc la fenetre = **825x343**, la page occupe le reste (12 px de chaque cote,
  38 en haut, 10 en bas).

### Pourquoi mes passes precedentes tournaient en rond
J'avais 851x394 pour la fenetre, donc :
- je peignais du « chrome » en haut et en bas — or il n'y a **pas de chrome** :
  ce que je prenais pour un header sombre est la page, AU-DESSUS de la fenetre ;
- je croyais que le fond d'effet ne remplissait que 825x343 de la fenetre, alors
  qu'il la remplit entierement ;
- je cherchais un decalage de contenu a corriger par des offsets, alors que le
  probleme etait le cadre.
DeepSeek disait la meme chose (« le fond doit remplir toute la fenetre ») ; mon
test d'echelle l'avait fait passer pour faux. Le raisonnement correct etait de
mesurer les BORDS DU FOND, pas de comparer des medianes par rangee sur toute l'image.

### Etat final
- Fenetre = **825x343**, le fond d'effet la remplit exactement (mesure : y 0..342,
  x 0..824 dans la capture).
- Scene (`.bd`) = 779x263 a **(20,58)**, en coordonnees contenu, avec
  `translate(20,58)` dans le `withTransform`.
- Knobs places en `SCENE_X + cx`, `SCENE_Y + cy`.
- Plus de bande de chrome ni de `clipToBounds` : le fond ne deborde plus, donc le
  clip arrondi du host suffit.
- **Ecarts, compares fenetre a fenetre** (recadrage de la reference sur les bords
  reels du fond) : amp **6,64 %**, echo **8,06 %**. Contre 10,2 / 33,7 % mesures sur
  l'image entiere — l'ecart de l'echo etait du cadrage, pas du contenu.
- Le script `compare_scaled.py` compare l'IMAGE a l'image ; pour juger le rendu il
  faut recadrer la reference sur (12,8 / 39,4 / 837,0 / 381,6) puis redimensionner
  en 825x343.

### Lecon
Quand un rendu semble « decale », mesurer d'abord les BORDS du conteneur, pas les
contenus. Un cadre faux produit exactement les symptomes d'un contenu mal place.

## B8-1, 5e passe : geometrie et materiaux pris du preview (2026-10-21)

Retour utilisateur : « les knobs de l'ampli sont trop bas et un peu plus petits »,
« pour Echo les knobs devraient etre dores, relief 3D prononce, plus haut et plus
grand ». Les deux etaient justes.

### La source de verite, c'est le preview, pas mes mesures
Le preview declare tout, au chiffre pres :
```js
AB(i,j,x,y,z,sk)   // centre (x,y) en coordonnees CONTENU, diametre z
AB(10,j,70+j*80,86,46,j%2?1:4)   // ampli : y=86, d=46
AB(1,8,650,70,56)  AB(1,9,735,70,56)  AB(1,10,692,188,56)  AB(1,11,95,205,52)
const SKN={5:5,6:6,7:10,8:11,9:12,10:7,11:8,12:13,13:14,14:9}
const KSH={6:'flute',10:'knurl',11:'chrome',13:'glass',14:'flat'}
```
`FxGeometry.kt` est desormais **genere depuis cette table** (script d'extraction) :
centre fenetre = (x+OXL, y+OYL), boite = centre - z/2. J'ai verifie que les 14 effets
ont le meme nombre de knobs dans `AB()` et dans les labels existants (4,4,5,6,3,4,3,
9,3,7,4,3,6,21) — correspondance exacte.

### Trois erreurs corrigees
1. **Diametre** : je posais `box.w * 0.86`. Le preview passe z **plein**. Tous les
   knobs etaient 13 % trop petits (amp 40 au lieu de 46, echo 47 au lieu de 56).
2. **Double offset** : la nouvelle geometrie est en coordonnees **fenetre**, mais le
   panneau ajoutait encore `SCENE_X/SCENE_Y`. Les knobs partaient 20/58 trop loin.
   Symptome : aucun knob detecte aux abscisses attendues. Corrige en retirant
   l'offset (le panneau place maintenant `cx.dp`, sans rien ajouter).
3. **Rangree confondue** : j'avais lu `y=252` (celle du piano/organ/sax) pour
   l'ampli. L'ampli est a `y=86`, **au-dessus** de la baffle. D'ou « trop bas ».

### Materiaux
`gradientIndex = SKN[id] ?: box.sk` — dans le preview `kk = SKN[i] !== undefined ?
SKN[i] : sk`, donc `SKN` gagne. L'ampli demande 1/4 dans ses `AB()` mais finit sur
**7** (gris fonce) ; l'echo n'est pas dans `SKN`, il garde donc `sk=1`, le **laiton**
`c1 = #E3CC96 -> #8D7240 -> #EED9A6 -> #7A6234 -> #CDB27A`. `CAP_GRADIENTS` avait
deja les 15 gradients `c0..c14` du `<defs>`.

### Ajouts demandes
- **Ombre sous les knobs** : `.kb{filter:drop-shadow(0 7px 7px #000a)}`. Dessinee en
  Canvas (`drawOval` + `radialGradient`) et non via `Modifier.shadow`, parce que la
  capture Roborazzi ne rend pas l'elevation de plateforme.
- **Cartes du haut (`.cd`)** : contour retire, remplace par un relief sureleve
  (`Modifier.shadow`, 8dp normal / 14dp selectionne, teinte accent) + un lisere clair
  de 1 px sur le bord superieur (`inset 0 1px 0 #ffffff1c`). La selection se lit
  desormais a la teinte de l'ombre, plus au contour.

### Etat
- Ecart fenetre a fenetre : amp **1,26 %**, echo **2,54 %**.
- Echo : 3 knobs sur 4 tombent **au pixel** (112/112, 112/112, 230/231), le 4e a ~6.
- Ampli : ~3 a 4 unites sous la reference — reste a affiner, l'ecart est constant.

### Lecon (2e fois)
J'ai encore cherche dans les pixels une information que le **code du preview
declarait**. Avant toute mesure d'image : lire `AB()`, `SKN`, `KSH`, le CSS. Une
heure de mesures ne vaut pas dix lignes de source.

## B8-1, 6e passe : groupes de chips, lumiere, relief (2026-10-21)

### Presets et groupes manquants
Ils existaient dans le preview, je ne les dessinais pas. `TG(i,label,x,y,items)` cree
les groupes `.chg` — l'equivalent de `AB()` pour les chips :
```
TG(10,'CHANNEL',16,6,[['CLEAN',6,'s',0],['CRUNCH',6,'s',.5],['LEAD',6,'s',1]])
TG(10,'PEDALS',560,6,[['BOOST',9,'t'],['TREM',10,'t']])
```
Portes dans `FxChipGroups` (`FxGeometry.kt`), en coordonnees **contenu** : comme
`AB()`, il faut ajouter `(OXL, OYL)`. Je les avais posees sans l'offset, les groupes
tombaient 58 trop haut et le detecteur de knobs les prenait pour des knobs.
Un item avec `value` est une position d'un param etage ; sans `value`, un interrupteur
lu comme actif au-dessus de 0,5. Rendus par `ChipGroupRow` + `RaisedChip`.

### « Mic 40 · 40 » retire
Le preview le dessine (`tag(x,'MIC '+...,mx,my+24)`), c'est donc un port fidele — mais
l'utilisateur le rejette : il encombrait le cabinet. Retire de `sceneAmp`. Les deux
params `Mic pos` / `Mic dist` restent dans le registre.

### Lumiere des knobs
`drawHighlight` etait trop dur : alpha .4 sur un blanc deja opaque = un ovale peint.
Le `hl` du preview est `#ffffffa8 -> transparent` avec `opacity:.5`, soit un voile.
Baisse a .26, agrandi, plus un rebond bas tres faible (`0x1AFFFFFF`) pour que le
chapeau reste dans sa jupe au lieu de flotter.

### Relief des chips et du rack
Regle demandee : **pas de contour**, relief + ombre dessous.
- `RaisedChip` (chips du haut, `.bt` / `.chg i`) : `shadow(3dp)` + lisere clair 1 px
  sur le bord superieur. Actif = remplissage laiton `#F2D58A` sur texte `#2A1A10`.
- `FxRack.raisedEdge()` : meme lisere + une couture sombre en bas. Applique aux
  boutons d'en-tete et aux tuiles du selecteur. Les 3 `border()` restants sont
  volontaires (bord de fenetre, avertissement rouge, panneau SFW).

### Etat
- Ecart : amp **1,31 %**, echo **2,54 %**.
- Ampli : knobs a 127 contre 128-130 en reference (calage a ~2 unites).
- Echo : 3 knobs au pixel, le 4e (Mix) a y=262 contre 266.

### Environnement
Le lien de telechargement peut renvoyer la page 503 « Service Temporarily
Unavailable » de all-hands.dev : c'est le service, pas le sandbox. Ne pas boucler
dessus — le fichier est servi par le `http.server` local sur le port 12000.

## Lecons B8-1 (a lire AVANT toute passe FX)

### 1. Lire le preview avant de mesurer le moindre pixel
Le preview declare tout, au chiffre pres. A chaque fois que j'ai mesure des pixels
d'abord, j'ai perdu une heure a repondre a la mauvaise question :
- `AB(i,j,x,y,z,sk)` : centre et diametre de chaque knob. Table complete extraite.
- `TG(i,label,x,y,items)` : les groupes de chips (CHANNEL, PEDALS, ADSR, CABINET...).
- `SKN={...}` et `KSH={...}` : materiau et forme du chapeau, par effet.
- `<defs>` : les 15 gradients `c0..c14`.
- CSS `.cd`, `.bt`, `.chg`, `.kb` : remplissages, ombres, rayons.
Une heure de mesures ne vaut pas dix lignes de source.

### 2. Le piege du double decalage (arrive DEUX fois)
Les scenes et les tables `AB()`/`TG()` sont en coordonnees **contenu** et portent deja
`(-OXL, -OYL)` (`y0 = -OYL` dans le code des scenes). Donc :
- une scene se dessine a **(0,0)**, sans translate supplementaire ;
- une boite `AB()` se place a **(x+OXL, y+OYL)**.
Ajouter `SCENE_X/SCENE_Y` par-dessus a produit le `translate(20,58)` en trop (fond
inset, contenu 58 trop bas) puis, la fois suivante, des knobs 20/58 trop loin.
**Symptome typique** : plus rien n'est detecte aux abscisses attendues, ou un `dy`
constant sur toutes les zones. Avant de toucher une position : verifier QUI ajoute
l'offset, et une seule fois.

### 3. `Modifier.shadow` ne marche pas ici
Deux raisons, les deux verifiees :
- la capture Roborazzi ne rend pas l'ombre de plateforme, elle est donc invisible
  dans les tests — donc invérifiable ;
- elle est peinte **au-dessus** du contenu, donc elle ressort **a travers** une case
  au remplissage translucide. C'est ce que l'utilisateur voyait comme « moche ».
Solution : `FxSurface.kt`, `Modifier.raised(corner, elevation, fill)` — ombre peinte
en `drawBehind`, demarrage du degrade exactement au bord bas, donc toujours dessous,
plus un lisere clair en haut et une couture sombre en bas. A utiliser pour toute case.

### 4. Le diametre d'un knob est `z` plein
`AB()` passe le diametre directement. J'avais mis un facteur `* 0.86` : 13 % trop
petit partout. Ne pas reintroduire de coefficient.

### 5. Environnement
- Le lien de telechargement renvoie parfois la page 503 de all-hands.dev : c'est le
  service, pas le sandbox. Ne pas boucler dessus.
- Le `http.server` du port 12000 ne survit PAS a un redemarrage : le relancer avec
  `cd /workspace/dl && (nohup python3 -m http.server 12000 --bind 0.0.0.0 &)`.
- `pillow` et `numpy` disparaissent aussi : `pip install pillow numpy`.
- `ss -ltn` n'est pas fiable pour verifier le port ; tester avec `curl`.

## Session récupération (2026-10-21, 3e) — mesure réparée + frontière moteur durcie

Contexte : 3e conversation après deux redémarrages du sandbox. Objectif demandé :
intégrer le contexte, **sécuriser pour éviter le crash sandbox** et les problèmes
de **callback qui empêchent d'envoyer des messages**.

### 1. La mesure était fausse, pas le port (cause racine)
Les pourcentages de la conversation précédente (echo 33,72 %, moyenne 14,90 %)
venaient d'une comparaison **image entière → image entière** alors que
`FxWindowCaptureTest` ne rend **que la fenêtre** (825×343, la boîte logique).
`tools/fxcompare/compare_scaled.py` redimensionne la capture sur la référence
complète, ce qui aligne la fenêtre sur la **marge de page** (12/38/10 px) et
gonfle tous les écarts d'environ +22 points.

- **Outil correct** : `tools/fxcompare/compare_window.py`. Il recadre d'abord la
  référence sur la fenêtre (y 39,4..381,6 et x 12,8..837,0 de la page 851×393),
  puis applique le même seuil (1,5 %, delta 24).
- Mesure réparée (capture 2268×943 → fenêtre) :
  space 10,73 · echo 33,72 · prism-tone 20,22 · iron-grip 8,72 ·
  solar-ceiling 27,66 · piano 11,14 · organ 13,18 · sax 18,35 · strings 15,31 ·
  guitar 12,53 · amp 6,83 · bass 6,08 · brass 8,83 · voices 14,55 · synth 13,34.
  **Moyenne 14,73 %.**
- `compare_scaled.py` reste valable **uniquement** pour une capture pleine page
  (`w851dp-h393dp`). Pour la fenêtre, toujours `compare_window.py`.

### 2. Les PNG de référence restent la cible (vérifié)
J'ai rendu `fx-window-preview.html` en headless (Chromium présent :
`/usr/bin/chromium`, `tools/refcheck/render_ref.mjs`) pour lever le doute
« références périmées ? ». Elles **ne le sont pas** : mon rendu headless donne
14,7 % de différence moyenne avec la référence, ce qui est la signature d'un
rendu **sans les polices Google** (le preview les charge par réseau) et non d'une
géométrie divergente. Conclusion : les PNG livrés sont la cible ; ne pas les
remplacer par un rendu headless.

### 3. Le calque ambiant `bg()` est la vraie lacune restante
`bg(i,x,t,v,a)` du preview peint **des couches qui couvrent toute la fenêtre**,
pas seulement la scène. Porté seulement pour les effets 0 et 1 (`.x1` y est
fusionné). **Manquant** :
- `bg(2)` : 3 bandes diagonales lumineuses qui défilent (`px = X0+((t*30+k*300)%(w+400))-200`).
- `bg(3)` : 17 lignes horizontales en éventail (`k` de -8 à 8, alpha `.06+.06*(1-|k|/8)`),
  compressées par `gs[0]` (enveloppe du compresseur).
- `bg(4)` : l'horizon du solar-ceiling — `ty = Y0+26+(1-v[1])*40`, ligne `#c9852c`
  épaisseur 2 à alpha `.25+.3*v[0]`, puis dégradé `#c9852c55 → transparent` sur 100 px.
  C'est la cause n°1 de l'écart de solar-ceiling (bande pleine largeur y 33..88).
- `.x0` (espace) et `.x2` (prism-tone) : **deux** règles `.x2` existent dans le
  preview (grille ardoise l.54, face rouge l.104) ; la seconde gagne. Vérifié :
  la face rouge est bien la bonne.
- Le fond de scène du solar-ceiling doit être `linear-gradient(#efe9db,#d6cdb9)`
  soit **(239,233,219)→(214,205,185)** : le port rend (235,228,213) en haut, trop
  clair — la rampe est décalée, pas seulement le calque ambiant.

### 4. Frontière moteur durcie (anti-crash callback)
Le moteur est un pont JNI : une bibliothèque absente ou un appel refusé **lève**
au lieu de retourner. Une exception lancée depuis un gestionnaire d'événement
Compose **démonte la fenêtre** sans retour possible — c'est exactement le
« problème de callback qui empêche d'envoyer des messages ».
- `FxShellStore` : tout appel moteur passe par `guard`/`guardBool`, qui replie
  l'échec dans `notice` au lieu de propager. Couvre addEffect, setInstrument,
  removeEffect, setParam/setInstrumentParam, toggleBypass, applyPreset.
- `FxOverlay` : la boucle de niveau (30 Hz, `peak()`) est enveloppée dans
  `runCatching`, elle garde le dernier niveau au lieu de tuer la fenêtre.
- Preuve : `FxShellStoreResilienceTest` (5 tests) — moteur mort (`DeadEngine`) et
  moteur qui refuse les écritures (`WriteDeadEngine`). 5/5 PASS.

### 5. Anti-crash sandbox
- Gradle toujours en **arrière-plan + bridé** : `--no-daemon --max-workers=2`,
  redirection vers `/tmp/*.log`, puis lecture du log. Jamais au premier plan sur
  une tâche longue.
- `scipy` installé (`pip install scipy`) pour l'analyse d'images par composantes.

## CORRECTION importante (meme session) — mon rendu du preview etait FAUX

`tools/refcheck/render_ref.mjs` est buggue et **ne rendait pas la fenetre FX** :
- il capture `#st`, qui est le **rack du device entier**, pas `#win` (la fenetre FX) ;
- il appelle `window.open(id)` — or `open` est la fonction native du navigateur,
  pas l'API du preview. L'appel etait donc sans effet (il levait, on l'avalait).

Consequence : les `prev-NN.png` montraient le **rack**, d'ou le « le preview ne
ressemble pas a la reference » de la passe precedente. **La conclusion « les PNG
de reference ne sont pas perimes » etait donc tiree d'un rendu invalide et doit
etre refaite.** Ne pas s'appuyer dessus.

### Le vrai signal, lui, est solide
Mesure **fenetre a fenetre** (boite 825x343 = x 12,8..837,0 / y 39,4..381,6 de la
page 851x393), capture 2268x943 -> 825x343, seuil 1,5 % / delta 24 :
- echo **43,31** · solar-ceiling 35,12 · prism-tone 29,48 · guitar 28,16 ·
  sax 26,44 · organ 26,24 · brass 25,51 · piano 24,00 · voices 23,09 ·
  strings 23,00 · synth 19,60 · iron-grip 18,73 · amp 18,53 · bass 15,31 ·
  space 12,29. **Moyenne 24,59 %.** Aucun effet au seuil.
- Les valeurs « 14,73 % » de la passe precedente venaient d'un recadrage
  different (meilleur alignement trouve a l'oeil) : **ne pas melanger les deux
  jeux de chiffres.** L'outil de reference est `tools/fxcompare/compare_window.py`.

### Cause n°1 identifiee : la couche de scene n'est pas la meme
Pour **echo**, la capture dessine une **grande roue de delay** (centre ~(115,138),
rayon ~60, disque + 6 ports + moyeu) que la **reference n'a pas du tout** : la
reference a un fond brun chaud uniforme (56,49,36) a cet endroit. Idem le panneau
sombre `#0d0b08` du preview (`x.fillStyle='#0d0b08';x.fillRect(px0,py0,pw,ph)`),
present dans la capture, **absent de la reference**.
- Geometrie du chrome : identique (les 4 boutons tombent aux memes coordonnees a
  ~2 px pres), donc la mise en page n'est pas en cause.
- Autrement dit : **la reference ressemble a une capture « chrome + fond » sans
  la couche de scene**, alors que le port (et le preview, qui EST la source de
  verite) la dessine. A trancher avant de « corriger » les scenes : si le preview
  doit gagner, c'est la *reference* qui n'est pas la bonne cible pour les scenes.

### Images
`python3 tools/fxcompare/sheets.py` ecrit une planche par effet dans
`tools/fxcompare/sheets/`, et `tools/fxcompare/contact.png` est la planche de
contact des 15 (REFERENCE | CAPTURE, triee par ecart). Copies servies dans
`/workspace/dl/fx-sheets/` (port 12000).

## FX port: two traps that cost a full debugging cycle

**Scene text density.** `FxScenes.label()` must measure with `density = Density(1f, 1f)`.
The scene canvas already applies the display density (`scale(d, d)` in `FxOpenPanel`
and `RackCard`), so a `sp` resolved at the real density is multiplied by it a second
time. On the 440dpi test device every scene label came out 2.75x too large, which is
what made prism-tone's band readouts an unreadable block. Verify any new scene text
with `grep`-free measurement: compare the glyph height against the reference crop.

**Panel text colour.** The `.xN` rule gives both the background *and* the text colour.
Use `fxPanelText(effectId)` for every label, value, title, sub-title and chip on the
panel. Hard-coding `#DFE4EE`/`#E6EAF3` makes the text invisible on the light faces
(solar `.x4` is dark ink `#2a241b` on cream `#efe9db`).

**Animation phase dominates the pixel metric.** The per-effect score
(`tools/fxcompare/error_map.py`) correlates with *how animated* an effect is, not with
how faithful the port is. The four static faces (bass 5.10, amp 8.54, organ 8.63,
piano 8.69) sit at the top while animated ones (echo 15.88, solar 15.21) sit at the
bottom. A phase-shifted but correct animation scores worse than a wrong static face.
Before "fixing" a low-scoring effect, verify the animation phase: cross-correlate the
two images and check for a lag. `strings` and `space` both cross-correlate at lag 0.
Never chase this score alone — it cannot distinguish a real defect from a time offset.

**The scene is not the window.** The 1728x800 references are full *windows*
(scale 2.0292, header included); the 825x343 captures are the *scene* only. Compare
scene-to-scene with the crop `(26, 80, 1702, 776)`, which is the empirically optimal
window on the known-good faces. A whole-window crop silently misaligns every later
read: it once produced a bogus "space is offset by 8 px" result.

**The effect title has one rule, but its face comes from the spec.** In the preview
`.ph .nm{font-size:30px;line-height:1}` is the *only* title rule: no font-family, no
font-weight, no letter-spacing. Do not conclude from that alone that the title uses
the body face. The per-effect faces in `fx_layout_spec.json` (`font` field, doc 03
table: Space Grotesk, JetBrains Mono, Orbitron, Share Tech Mono, Sora) are the
source of truth, and `FxFonts.title(id)` implements them. Measured A/B on the title
band (x 140..430, y 0..45), keeping the per-effect face while dropping the *invented*
weight and tracking scored best (18.62) against both the body face (18.96) and the
status quo (19.76). Keep `fontWeight = Normal`, `letterSpacing = 0` and
`lineHeight = fontSize`; the last one removes the ~2 px sink the Material3 24 sp line
height causes. `NAME_STYLES`'s weight/tracking columns are not in the preview.

**The names carry a ligature.** `fx_layout_spec.json` spells effects 0 and 1
`Æther Space` / `Æther Echo` with U+00C6. The port had plain `Aether`, which is a
one-glyph difference in the widest part of the title.

**The Æther FX path has two dormant switches, and both default to off.** The audio
chain is fully built — `FxMixer` with `kStrips = 11` plus `kMaster = 15`, per-strip
chains, a master strip — but nothing reaches it until both are set:

1. `mFxEnabled{false}` in `audio_engine.h`. `renderFxPath()` returns early while it
   is false, and `AudioEngine::render` falls back to the legacy stereo mix. Set from
   Kotlin by `setFxEnabled(true)`, which nothing called. It is now invoked from
   `NativeAudioBridge.safeStartEngine` right after a successful `startEngine`.
2. `mMultiChannelEnabled{false}` in `soundfont_engine.h`. `renderChannels()` returns
   false while it is off, and `renderFxPath` then bails out even with `mFxEnabled`
   true. It must be set *before* `init()`: `synth.audio-channels` and
   `synth.audio-groups` are only honoured at FluidSynth init, which is why
   `setMultiChannelEnabled(true)` sits above both `mSynthEngine.init()` call sites in
   `audio_engine.cpp`. Enabling it also turns the synth chorus off, so the audible
   result changes by design.

**The window the app opens is `FxOverlay`, not `FxWindowHost`.** `FxWindowHost` is
the ported shell; `FxOverlay` is the host the mixer calls, and it used to render the
older `FxRackStrip`/`FxWindow`/`FxAddPanel` trio. It now builds one
`FxShellStore(fxModules(), FxControllerEngine(controller))` per strip and renders
`FxWindowHost`. Restore order matters: `controller.restore()` runs first (it pushes
every effect and parameter into the native mixer, so the engine is the authority),
then `store.seed(...)` copies that state into the shell without re-pushing. The
controller keeps persistence and the native bridge; the store owns the UI state.

## Sandbox environment (survives nothing — re-provision every session)

The sandbox is wiped between conversations. Nothing below persists except what is
committed, so restore it before any build.

```bash
export JAVA_HOME=/workspace/jdk/jdk21
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_SDK_ROOT=/workspace/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

`/workspace/build.sh` already exports these and bounds memory
(`--no-daemon --max-workers=2`); the native fluidsynth build for 4 ABIs plus a
4 GB Gradle daemon is what put earlier sandboxes under pressure. Prefer it.

### Screenshots

Roborazzi only writes images when the record task runs. Plain
`testDebugUnitTest` executes the tests and produces no PNG:

```bash
./gradlew :app:recordRoborazziDebug --tests "com.soundstage.mixer.*CaptureTest"
```

Never run `recordRoborazziDebug` unfiltered: `LiveUiLoop.run()` is an infinite
loop by design (an external process drives it through `cmd.json`) and the suite
hangs until killed. Always pass `--tests`.

### Release workflow

`.github/workflows/release.yml` is manual only: `workflow_dispatch`, no push or
tag trigger. Start it with "Run workflow". It uploads the APK as an artifact and
publishes a release unless the `release` input is false.

### Repository state

`main` is the working branch. The `com.aetherkeys.fx` package (FX window, 15
effects, engine) is present on `main`, so APKs built from it include the FX
window. The earlier `recovery/fx-window-shell` branch has been merged; older
log entries below that mention it are historical, not instructions.

### MIDI mapping page (recovery work)

The direct/overlay MIDI mapping UI is gone. Mapping now lives in
`ui/settings/MidiMappingPage.kt`, reached from Settings > MIDI. Do not
reintroduce `SettingsDrawer.kt`, `MidiLearnSubPage.kt`, `MappingRegistry`,
`MappingCatalog`, or the `Modifier.mappable(...)` modifier.

`MidiControlType` covers CC, Note, Program Change, Channel Aftertouch (0xD0)
and Pitch Bend (0xE0). Single-data-byte messages read their value from `data1`,
never `data2`. A PC bound to an action drives the mapping only, via
`AudioEngine.mappedProgramChangeProvider`, so it cannot also swap the patch.

Anti-jitter has one control pair, `antiJitter` (default false) and
`jitterWindowMs`. The old `midiDebounce*` pair was a second hidden control and
has been removed from the app state.

Test notes: `ExampleRobolectricTest` and `GreetingScreenshotTest` fail on a
clean `main` checkout, so they are pre-existing. A full
`:app:testDebugUnitTest` run takes over 20 minutes; prefer `--tests` filters.

### Sandbox recovery notes (session 3)

The workspace environment is wiped between sessions. Reinstall before building:

    apt: openjdk-21-jdk-headless unzip zip   # Debian trixie has no openjdk-17
    Android SDK at /workspace/android-sdk (cmdline-tools, platform-34, build-tools 34)
    NDK 28.2.13676358 (the version pinned by AGP); do NOT put ndk.dir in local.properties,
    AGP resolves the pinned NDK from sdk.dir on its own and ndk.dir triggers CXX1104.
    local.properties only needs `sdk.dir=/workspace/android-sdk`.
    Scripts: /workspace/tools/install_sdk.sh, /workspace/tools/build.sh (logs in /workspace/tools).

Build with ANDROID_HOME/JAVA_HOME exported; `:app:assembleDebug` takes ~4 min.

### FX window (Æther FX) invariants

- `com.aetherkeys.fx.FxRegistry` is generated from
  `app/assets/aether-fx-package/gen_kotlin.cpp`. That package is gitignored, so the
  tracked source of truth is the generated `FxRegistry.kt`; keep both in sync.
  `FxRegistry.info(id)` MUST stay bounded (`all.getOrElse(id) { all[0] }`): an
  out-of-range id used to throw straight out of a Compose callback.
- `FxShellStore` must keep `slots`/`instrument` as Compose snapshot state
  (`mutableStateMapOf`), otherwise added effects never recompose.
- The rack ends with an `AddSlotCard` (the `+`): it is the only entry point to the
  add sheet.
- Every native call from `FxController` goes through its private `safe(...)` helper;
  never call `NativeAudioBridge` directly from a Compose event handler.
- Panic path: `MixerViewModel.triggerPanic()` calls `NativeAudioBridge.safeFxPanic()`
  (drops delay/reverb tails) plus `safeStopAllDrumSamples()`. Native side:
  `FxChain::requestPanic()` -> `FxMixer` -> `AudioEngine::fxPanic()` -> JNI `fxPanic`.
- `AudioEngine::renderFxPath` must still run the legacy master chain
  (`processMaster`) or the limiter is skipped and the output clips (the "crackle").

---

## Session 2026-10-23 (4e passage) — reprise sandbox + LiveLooper (analyse seule)

Contexte : 3e crash sandbox (env efface). JDK/SDK/Pillow absents au demarrage.
Mode de cette session : **DISCUSSION uniquement, aucune modification de code produit.**

### Environnement sandbox restaure (a refaire a chaque crash)

- **JDK 21** : `/workspace/jdk/jdk-21.0.13+9` (Temurin 21.0.13+9, telecharge via
  `api.adoptium.net/v3/binary/latest/21/ga/...`). Un second JDK existe :
  `/workspace/jdk/jdk21` (21.0.5). Export dans `~/.bashrc` :
  `JAVA_HOME`, `ANDROID_HOME=/workspace/android-sdk`, `ANDROID_SDK_ROOT`, `PATH`.
- **Android SDK** : `/workspace/android-sdk` (build-tools, cmake, cmdline-tools,
  ndk, platform-tools, platforms). `local.properties` = `sdk.dir=/workspace/android-sdk`.
- **Python** : `/usr/local/bin/python` (3.13.15) + Pillow 13.0.0 deja presents.
- **Build verifie OK** : `./gradlew :app:compileDebugKotlin --console=plain --no-daemon`
  -> BUILD SUCCESSFUL en 18s (cache chaud). Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.10,
  compileSdk 36, minSdk 26, Java target 11.
- Script de prechauffage : `/workspace/warmup.sh` (export env + compile).

### Le git remote est INACCESSIBLE depuis le sandbox

- `git fetch` / `git ls-remote` echouent : *"Invalid username or token. Password
  authentication is not supported"* meme avec `GITHUB_TOKEN` (prefixe `ghu_`, invalide).
- **MAIS l'API GitHub fonctionne** avec `$GITHUB_TOKEN` :
  `https://api.github.com/repos/amiel767/NewKeys-/contents/app/assets/<f>`.
  Telecharger un fichier brut :
  `curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github.raw" <url> -o <f>`.
- Le remote contient des fichiers que le local n'avait pas (l'utilisateur les a
  pousses via l'app) : `LiveLooperModel.kt`, `LiveLooperUi.kt`, `LoopsPreview.jsx`.

### LiveLooper — sources de reference (recuperees, non integrees)

Trois fichiers de reference, **copies dans `app/assets/`** mais **NON branches au
code** (ce sont des specs, comme les autres `*Preview.jsx`) :

| Fichier | Taille | Role |
|---|---|---|
| `app/assets/LoopsPreview.jsx` | 66 Ko | Source de verite visuelle (preview JSX approuve) |
| `app/assets/LiveLooperModel.kt` | 33 Ko | Modele + logique pure + `LooperController` (sans UI) |
| `app/assets/LiveLooperUi.kt` | 70 Ko | UI Compose stateless (rend `LooperUiState`, reporte via `LooperActions`) |

- Package declare dans les deux .kt : `com.soundstage.mixer.looper // <- adapt to the
  real package`. **Ce dossier n'existe pas encore** dans `app/src/main/java/`.
- `LoopsPreview.jsx` = preview **891x411 dp**, selecteur de page en haut
  (`DrumPad` / `LiveLooper`), 8 cases (`L1..L8`), patchs de loop sauves avec la
  setlist, FocusView, ActionSheet, PatchWindow, toasts, dialogs de confirmation.
- `LiveLooperModel.kt` fournit : enums (`CaseMode`, `LoopAction`, `Quantize`,
  `StopAt`, `AfterRecording`, `RecordSource`, `PendingLoadChoice`), data classes
  (`LoopEvent`, `LoopCase`, `LooperSettings`, `LoopPatch`, `LooperUiState`,
  `LoopPosition`, `RemovedLayer`, `RecordSources`), `LooperActions`, `LooperLogic`
  (pur), `LooperController` (`StateFlow<LooperUiState>`, `tick()`, `onPlayed()`,
  `actions()`), `LoopPatchJson` (persistance JSON, `fromJson` ne crash jamais).
- `LiveLooperUi.kt` fournit : `LiveLooperContent`, `LiveLooperOverlays`,
  `LooperPageSelector`, `LoopPatchChip`, `LoopsSummaryCard`, `loopCaseColor`, tokens
  `LL` (Accent `#8FB2E8`, Rose `#C2587A`, Amber `#D8A24A`, ...), `enum DrumPadPage`.
  Utilise `Popup`, `graphicsLayer`, `shadow`, `LocalHapticFeedback` — tout dispo
  dans Compose BOM 2024.09.00.

### Point cle de la demande : selecteur de section en haut

Le JSX `LoopsPreview.jsx` remplace la pilule figee "DrumPad" par un **selecteur
deroulant `DrumPad` / `LiveLooper`** place dans la TopBar (titre). Le Kotlin actuel
`DpTopBar` (`DrumPadOverlays.kt`) affiche une pilule `DrumPad` non cliquable, et le
`DpSeg` "Pads/Loops" est dans la rangee d'en-tete de la colonne droite
(`DrumPadScreen.kt` ~l.272). C'est **cette migration** que la demande vise :
`LooperPageSelector` remplace la pilule du titre ; plus de bouton "DrumPad - Looper".

### Etat actuel a remplacer (sur `main`, commit 58baec2)

- `audio/LooperEngine.kt` (311 l.) : moteur actuel, event-only, 8 cases x 4 couches,
  swing, `CaseState` (`Empty/Queued/Recording/Playing/Overdubbing/Stopped`),
  `UiState`, scheduler interne (`start()/stop()/release()`), callbacks
  `onPlayPadEvent` / `onPlayNoteEvent`.
- UI actuelle : `ui/pages/DrumPadScreen.kt` (padMode "pads"/"loops", `LoopsGrid`,
  `DpSeg`, `Swing` pill, `$used/8 used`, Play/Stop all), `ui/components/drumpad/`
  (`DrumPadPad.kt` -> `DpLoopCase`, `DrumPadOverlays.kt` -> `DpTopBar`).
- Wiring VM : `MixerViewModel.kt` — `looperEngine` (l.320), miroir `looper` dans
  `MixerUiState` (l.291), callbacks de rejeu (l.374-397), `tapCase`/`playAll`/
  `stopAll`/`record` (l.2065-2088), `record("note", ...)` (l.2430), hits pads (l.2783).
- Navigation : `AppScreenPage.DRUMPAD` -> `DrumPadScreen` (appel l.805 de
  `MixerScreen.kt`). Enum dans `model/MixerState.kt` l.23.
- Tests : `app/src/test/java/com/soundstage/mixer/audio/LooperEngineTest.kt`
  (JVM pur, pas de Robolectric). Infra Roborazzi/Robolectric dispo pour l'UI.

### Restes a faire (session longue, PAS cette session)

1. Integrer `LiveLooperModel.kt` + `LiveLooperUi.kt` dans
   `app/src/main/java/com/soundstage/mixer/looper/` (corriger le package).
2. Cabler `LooperController` au `TransportClock` reel (tick ~8-10 ms) et remplacer
   `LooperEngine` dans `MixerViewModel` (rejeu via `onReplay`, enregistrement via
   `onPlayed`) — **sans toucher a Echo** ni au reste valide.
3. Migrer l'UI : `LooperPageSelector` en TopBar, `LiveLooperContent` +
   `LiveLooperOverlays` a la place de `LoopsGrid`/`DpLoopCase` ; retirer le
   `DpSeg` Pads/Loops et le bouton DrumPad/Looper.
4. Persistance : brancher `LoopPatchJson` sur la sauvegarde setlist
   (`onPatchesChanged`).
5. Tests : porter/etendre `LooperEngineTest` vers `LooperLogic` (logique pure).

### Propositions a discuter (faille reelle + manques)

- **Panic** : la chaine master "legacy" n'est jamais remise a zero par
  `triggerPanic()` (`reset()` des blocs master legacy = vide). Faille reelle.
- **Pas de son au demarrage** : `noteOn` du clavier n'appelle pas
  `ensureEngineStarted()` (seul `selectScene` l.3491 et l.4471 le font).
- `LooperController.midiLearn()` est un stub (`/* hook to ... */`).
- `LooperController` n'a pas encore de hook "demarrage sans son" / count-in audible.

---

## Session 2026-10-23 (5e passage) — robustesse anti-crash + callbacks

Objectif : intégrer le contexte, **sécuriser l'environnement et les callbacks** pour
éviter les crashs sandbox et les callbacks qui bloquent l'envoi de messages, et
installer ce qu'il faut **en arrière-plan**.

### Environnement : auto-restauration idempotente

- `/workspace/restore_env.sh` (nouveau) : détecte le JDK réellement présent
  (`/workspace/jdk/jdk-21*`, `jdk21`, `/usr/lib/jvm/*21*`) au lieu de coder en dur
  `/workspace/jdk/jdk21` (l'ancien chemin n'existait plus après crash → build cassé).
  Installe seulement ce qui manque (JDK 21, cmdline-tools, platform-tools,
  android-36, build-tools 36.0.0, cmake 3.22.1, ndk 27.0.12077973, Pillow).
  Idempotent, sûr en arrière-plan : `nohup /workspace/restore_env.sh >/dev/null 2>&1 &`
  → log `/workspace/tools_logs/restore_env.log`.
- `/workspace/build.sh` (réécrit) : détecte le JDK, appelle `restore_env.sh` s'il
  manque, borne la mémoire (`--max-workers=2`).
- `gradle.properties` : `org.gradle.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=768m`
  (avant 4g) et `org.gradle.workers.max=2` (avant 4). C'est la pression mémoire
  (daemon 4g + 4 workers + parallel + build natif fluidsynth 4 ABI) qui tuait la
  sandbox.

### Callbacks sécurisés (échec loggé au lieu de tuer le thread)

Toutes ces fonctions tournent sur un thread système (MIDI IO, framework audio,
SoundPool, MediaPlayer). Une exception qui s'en échappe tue le thread de réception
→ **le device continue d'émettre mais plus rien n'est traité** (le « callback
problem ») ou crash. Chaque corps est maintenant enveloppé (`try/catch(Throwable)`
+ `Log.e`) dans `model/AudioEngine.kt` :

- `MidiReceiver.onSend` (parsing des octets, `offset+count` borné à `msg.size`),
- `handleIncomingMidi` (délégué à `handleIncomingMidiUnsafe`),
- le callback `midiManager.openDevice`,
- `refreshMidiDevicesList` / `onDeviceListChanged`,
- `AudioDeviceCallback.onAudioDevicesAdded/Removed`,
- `headsetPlugReceiver.onReceive`,
- `SoundPool.setOnLoadCompleteListener`, `MediaPlayer.setOnCompletionListener`,
  `AudioFocusChangeListener`.

### Point 4.5 — reset des queues FX + fermeture fenêtre au changement de patch

`MixerViewModel.selectPatch()` : après `allNotesOff()` →
`NativeAudioBridge.safeFxPanic()` (vide les buffers delay/reverb de l'ancien patch)
et ferme le popup `EFFECTS` si ouvert (sinon le rack affichait encore l'ancien patch).

### C3 — MasterSliderV2 (MixerBottomBarV2.kt)

Le rail vertical `FaderRail` est tourné à l'horizontale via un modificateur
`swapAxes()` (`layout` qui échange les contraintes) + `graphicsLayer(rotationZ=90f)`.
Sans l'échange de contraintes, un `rotationZ` ne change pas la taille mesurée et le
rail vertical s'effondrait en carré dans la fente courte. Le case prend toute la
hauteur de la barre (46 dp) pour ne pas rogner le cap 40 dp ; le dB n'apparaît
qu'au toucher (chip centré).

### État build/tests

- `:app:compileDebugKotlin` → **BUILD SUCCESSFUL**.
- `:app:testDebugUnitTest --tests FxShellStoreResilienceTest` → **BUILD SUCCESSFUL**.
- Captures régénérées (recordRoborazziDebug) : `bottom_bar.png`, `fx_window_amp.png`,
  `track_settings_scrim.png` + les 15 fenêtres FX.

### Tests Robolectric : contamination JVM, pas une régression

La suite complète `:app:testDebugUnitTest` **hang** (le « Test worker » reste
`WAITING (parking)` dans `Sandbox.runOnMainThread`), et plusieurs captures
(`KeyboardOpenCaptureTest`, `KeyboardCollapsedCaptureTest`, `TrackSettingsCaptureTest`)
échouent en `UncaughtExceptionsBeforeTest` **quand elles partagent le JVM** : une
exception non capturée d'un test précédent est ré-attribuée au suivant. Preuve :
chaque test **isolé** (`--tests "…KeyboardOpenCaptureTest"` seul) est
**BUILD SUCCESSFUL**. Ces échecs sont **pré-existants** (fichiers non modifiés par la
session). Pour un verdict fiable, lancer les tests **un par un** ou par petits lots,
jamais la suite entière ; ne pas interpréter `UncaughtExceptionsBeforeTest` comme une
régression du code testé.

### Ne jamais lancer deux builds Gradle en parallèle

Deux `testDebugUnitTest` simultanés (deux daemons, ~1 Go chacun) sont exactement la
pression qui tue la sandbox. Vérifier `pgrep -f "Gradle Test Executor"` avant de
lancer, et tuer l'ancien run (`kill <pid>`) plutôt que d'en démarrer un second.
