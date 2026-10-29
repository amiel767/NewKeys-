# AGENTS.md — NewKeys- (SoundStage Mixer)

Mémoire persistante du dépôt. **Toujours vérifier un nom ici avant de l'utiliser** :
plusieurs briefs ont référencé des symboles inexistants, ce qui a causé des
malentendus coûteux.

> **Pour un agent qui arrive :** lire `GUIDE_AGENT.md` d'abord (mise en route,
> build, captures, carte du code, travail en parallèle), puis ce fichier pour les
> pièges et décisions, et `PROJECT_CONTEXT.md` pour le code actif inline.

> **Historique archive** : les sessions FX (B8-1), LiveLooper et sandbox
> 2026-10-19 -> 2026-10-27 sont dans `docs/archive/AGENTS-history.md`
> (non charge automatiquement — le lire a la demande).

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

## Conventions

- **Veille de la conversation parallele — OBLIGATOIRE avant ET apres chaque
  modification.** Ce depot est developpe par plusieurs conversations OpenHands
  en meme temps. La conversation de reference est
  `dde6dffb48c5475a931918c2e3ff38f9`. Lancer `python3 tools/conv_sync.py`
  (incremental, watermark dans `tools/.conv_sync_state.json`) avant de toucher un
  fichier et apres l'avoir touche. `--full` rejoue tout le flux.
  Si l'autre conversation touche **le meme fichier** : s'arreter et demander, ne
  pas ecraser.
- **Se mettre a jour avant de travailler** : le clone local peut etre tres en
  retard (44 commits constates le 2026-10-27). Toujours
  `git fetch origin main` puis comparer `HEAD...origin/main` AVANT de conclure
  quoi que ce soit sur l'etat du depot. Un index rempli (`git status` avec des
  `A`/`M`) n'est pas du travail perdu : verifier par md5 contre `origin/main`.
- Commits : `[Mixer V2] <résumé> — Compile: OK`, avec
  `Co-authored-by: openhands <openhands@all-hands.dev>`.
- Ne pas pousser sans demande explicite.
- **Ne JAMAIS déclencher de workflow GitHub (release.yml ou autre) sans l'accord
  explicite de l'utilisateur.** Règle posée le 2026-10-26. Construire l'APK en
  local et pousser le commit est acceptable ; lancer une release ne l'est pas.
  Si une release est utile, la proposer et attendre le feu vert.
- Avant toute modification : rebaser sur le remote (il arrive que l'utilisateur
  ajoute des assets via l'UI GitHub, ce qui crée une divergence sans conflit).
- Le remote peut recevoir des commits « Add files via upload » qui ne touchent
  que `app/assets/` — vérifier avec `git diff --name-only` avant de rebaser.

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

## PROJECT_CONTEXT.md — mise en contexte d'un agent (nouveau)

`PROJECT_CONTEXT.md` (2,7 Mo) inline le **source complet de chaque fichier actif** :
117 Kotlin (53 926 l.) + 25 natifs. Le code mort est exclu du corps et liste en annexe
(12 fichiers verifies par scan d'atteignabilite) ; FluidSynth vendore (269 fichiers)
est resume. Regenere avec `python3 tools/gen_project_context.py`.

**A lire en premier** quand on reprend le depot sans contexte : il remplace une
exploration de l'arbre. `AGENTS.md` (ce fichier) reste la memoire des pieges et des
decisions ; `PROJECT_CONTEXT.md` est la carte du code.

## Session 2026-10-24 — nettoyage assets + integration Æther Instruments (discussion)

### Briefs obsoletes supprimes (commit `9ce221a`)
19 fichiers retires de `app/assets/` : `B6-1..5.txt`, `B6-fix.txt`,
`Brief B6 Fix 2.txt`, `Brief Mixer.txt`, `Brief Mixer 2..7.txt`,
`BRIEF MIXER 8 — Polish V2.txt`, `B8-1-brief-coque-fx.txt`, `Reprise.txt`,
`VirtualKeyboard_Agent_Brief.md`, `brief-integration-sustain-pedal.txt`.
Aucun n'etait reference par le code ni les outils. **Conserves** : tous les
`*Preview.jsx`, les `.kt` de reference, et tous les zips.
Les mentions `VirtualKeyboard_Agent_Brief.md` (l.194) et
`brief-integration-sustain-pedal.txt` (l.466) ci-dessus sont donc historiques.

### `aether-instruments-kotlin-v2.zip` (recu, commit `0c7c6a9`)
Portage Kotlin/Compose Organ + Synth pour la **fenetre soundfonts**, package
`com.aetherkeys.instruments`, 22 fichiers. A lire avant integration :
- `AeInstrument.ORGAN(6)`, `SYNTH(14)` — **mêmes ids que `FxRegistry`** ;
- `AeHost` mappe 1:1 sur `NativeAudioBridge.setTrackInstrument/Param` + noteOn/off ;
- dependance unique : `com.caverock:androidsvg-aar:1.4` (**absente** du build) ;
- polices : Cinzel **absente** de `res/font/` (sora/jakarta/share_tech_mono presents) ;
- `PATCH-SoundfontWindow.md` : les 5 ancres verifiees tombent juste sur le
  `SoundfontWindow.kt` actuel (l.298/303/755/844/289/956).
- `preview-aether-instruments.html` = 55 489 o, non vide (le « 0 octet » ne se
  reproduit pas).

### Piege de la note vers une SOURCE (verifie, compte pour les saccades)
`routeEventToSource` (audio_engine.cpp:563) gate sur `mFx.soundfontPaused(channel)`,
et `soundfontPaused` n'est vrai que si `kindOf(id) == Source` (fx_mixer.h:96).
Donc **seuls Organ (6) et Synth (14) captent les notes** ; Piano/Sax/Strings/
Guitar/Brass (SHAPER) ne les captent pas. Le routage passe par la file
`EngineMidiEvent` drainee sur le thread audio (`drainEvents`) : le JNI `noteOn`
n'appelle jamais `mFx` directement.

### Cout a vide du chemin FX (pour la cible « pas de saccade »)
`renderFxPath` sort tot si `!hasActiveWork()` ; mais des qu'un instrument existe
quelque part, les **10 canaux** passent par `renderChannels` (`fluid_synth_process`
planar + entrelacement), et une strip inactive est repliee directement. Le vrai
poste de depense mesure reste FluidSynth multi-canal, pas le mixeur FX.

## Session 2026-10-26 — Æther Instruments integres (faits, commits 6ae6d92 → 0f8ebbe)

Directive validee : **retirer Organ et Synth des choix FX** et les charger
**directement dans une piste**, ou ils se comportent **exactement comme un
soundfont** (MIDI, volume, pan, mute, couleur) et comptent comme des
**instruments reels, pas des effets**.

### Ce qui a ete fait
1. **Import** (commit `c2cd92e`) : 9 `Ae*.kt` dans
   `app/src/main/java/com/soundstage/mixer/instruments/` (package renomme
   `com.aetherkeys.instruments` → `com.soundstage.mixer.instruments`),
   `cinzel.ttf` + `Cinzel-OFL.txt`, `androidsvg = "1.4"` dans `libs.versions.toml`.
   **Piege corrige** : le KDoc de `AeSvgAssets.kt` contenait `svg/*.svg` → le `/*`
   interne ouvrait un commentaire imbrique non ferme. Reformule.
2. **Hote + gestionnaire** (commit `6ae6d92`) :
   `AeInstrumentHost` (AeHost → `NativeAudioBridge`), `AeInstruments` (singleton
   applicatif, survit a la fermeture de la fenetre), 4 wrappers `safe*` ajoutes a
   `NativeAudioBridge`. `SoundfontWindow` recoit les **5 modifications** de
   `PATCH-SoundfontWindow.md` (onglet Æther a gauche, contenu a droite, fenetre
   1000x460 en surcouche). `MixerScreen` passe `aether` + `aetherStrip`.
3. **Exclusivite + retrait des FX** (commit `5016704`) :
   `FxAddOverlay` filtre `AE_RETIRED_INSTRUMENT_IDS = {6, 14}` (le registre les
   garde : routage et persistance les resolvent encore). `AeInstruments.onBeforeLoad`
   (pose par le VM) decharge le soundfont de la piste ; `loadSoundFontForSlot`
   decharge l'instrument Æther. **Un instrument Æther et un soundfont sont la meme
   chose sur une piste** : le dernier charge gagne.
4. **Early-out silence** (meme commit) : `fx_organ.h` / `fx_synth.h` sortent tot
   quand aucune voix ne sonne. L'orgue **exclut le Leslie en marche** (`rotNow != 0`)
   et tient compte de la queue de reverb (`rev_.meter(0)`), sinon le horn
   redemarrerait ou la queue serait coupee.
5. **Polices + nom + tests** (commit `0f8ebbe`) :
   `initAetherInstruments(context)` dans `SoundStageApplication.onCreate` assigne
   Cinzel/Share Tech Mono/Plus Jakarta/Sora a `AeFonts` (sinon repli systeme).
   `ChannelStripV2` et les lignes du clavier affichent le nom de l'instrument Æther
   a la place du patch (lecture de `AeInstruments.sessions`, etat snapshot).
   `AeManagerTest` (8 tests JVM purs, `FakeHost`) : 8/8 OK.

### Pieges verifies de cette integration
- **Le natif gagne, pas Kotlin.** `routeEventToSource` (audio_engine.cpp:563) route
  deja les notes vers la source quand `soundfontPaused(strip)` est vrai. **Aucun
  hook Kotlin n'est necessaire pour le son** ; Kotlin ne fait que load/params/UI.
- **`aspectRatio`** n'etait pas importe dans `SoundfontWindow.kt` (compilation
  echouee une fois).
- **L'onglet Æther n'existe que pour `SoundfontVariant.Track`** (slots 0..7). Pad
  (9) et Drum (8) gardent leur fenetre inchangee.
- **`AeFonts` a des replis systeme** : sans `initAetherInstruments`, l'UI tourne
  mais en SansSerif/Monospace. C'est silencieux — d'ou l'appel explicite.
- **`AeSvgAssets.kt`** : le `/*` dans un KDoc **ouvre un commentaire imbrique en
  Kotlin**. Ne jamais ecrire `svg/*.svg` dans une KDoc ; dire « le dossier svg ».
- **Le manager pousse le preset 1 en entier** au chargement
  (`AeManager.load` → `host.setParam` par index). Ne pas re-pousser cote VM.

### Reste (cette session s'arrete ici, consignes a venir)
- Validation **appareil** (pas d'emulateur ici) : son reel de l'orgue/synth sur une
  piste, absence de saccade a l'ajout d'un FX.
- Persistance du choix d'instrument Æther par piste (setlist) — non branchee.
- Captures Roborazzi de l'onglet Æther (l'onglet et la fenetre ne sont pas encore
  captures).

## Session 2026-10-26 — Æther Leads + Sax (zip final + ajustements DeepSeek)

Conversation source (parallele) : `dde6dffb48c5475a931918c2e3ff38f9`, surveillee par
`python3 tools/conv_sync.py` (watermark dans `tools/.conv_sync_state.json`, gitignore).
Son travail **est deja dans notre historique** : `origin/main` = `e12e9e0` est un
**ancetre** de notre HEAD (`git merge-base --is-ancestor origin/main HEAD` -> vrai),
donc « behind 0, ahead 11 ». Aucun rebase necessaire. Verifier quand meme avant chaque
passe : le remote avance par « Add files via upload ».

### Ce qui a ete fait
1. **Import du zip `aether-instruments-final.zip`** : 4 fichiers neufs
   (`AeDrawLeads.kt`, `AeLeadsUi.kt`, `AeSaxAssets.kt`, `AeSaxUi.kt`), 9 fichiers
   partages mis a jour (dont `AePresets.kt` avec 30 presets Leads + 23 Sax),
   12 webp `ae_sax_<type>_<finition>.webp` dans `res/drawable-nodpi/`.
2. **`fx_leads.h`** (`LeadsEngine : public SourceFx`, 32 params, 6 voix) : 5 couches
   (sine / pulse a largeur variable / saw / super-saw 5 detunes / soft triangle x2),
   enveloppe AHDSR pilotant amplitude **et** filtre, SVF TPT low/high/band, puis bus
   chorus -> tone (shelves) -> delay -> reverb et un bus vibrato.
3. **`fx_sax.h`** (`SaxEngine`, 21 params) : table d'anche harmonique -> 4 formants
   (2 formants d'anche + ancre + chaleur), bruit de souffle passe-bande, growl (AM rapide),
   subtone, scoop/fall, clic de cle, vibrato retarde, room. Monophonique, glide legato.
4. **Registre** : `FX_LEADS = 15`, `FX_SAX_AE = 16` (tous deux `Kind::Source`).
   **Piege** : `FX_SAX = 7` existait deja (shaper) — le nouveau sax s'appelle
   `FX_SAX_AE` pour ne pas entrer en collision. `FX_COUNT` passe a 17.
   `info(id)` est desormais **borne** (`all[id >= 0 && id < FX_COUNT ? id : FX_SPACE]`) :
   meme classe de bug que le `all[-1]` historique.
5. **`fx_chain.h`** : `#include "fx_leads.h"` / `"fx_sax.h"` + `makeInstrument()`
   construit les deux moteurs. `kindOf()` les classe `Kind::Source`.
6. **Kotlin (2 namespaces)** : `FxRegistry.kt` regenere depuis `gen_kotlin.cpp`
   (`constName` etendu), `instrumentIds` -> `listOf(5,6,7,8,9,12,14,15,16)`,
   `AeInstrument.LEADS(15,...,32)` / `SAX(16,...,21)`.
7. **`FxAddOverlay`** : `AE_RETIRED_INSTRUMENT_IDS = setOf(6, 14, 15, 16)` — Leads et
   Sax sortent du rack FX comme Organ/Synth et se chargent via l'onglet Æther.

### Ajustements DeepSeek (le lien `chat.deepseek.com/share/...` est bloque par CloudFront ;
lire via `curl -s https://r.jina.ai/<url>` — le proxy jina renvoie le markdown)
- **Leads** : a l'appui, chaque voix tire un LFO de drift lent (0,12..0,47 Hz,
  1,5..4 cents), un offset de detune fixe (+-3,5 cents), un etalement des temps A/D
  (+-12 %), un jitter de Q (+-0,25) et une drive de saturation (1,1..1,5), via un
  **xorshift seede par voix**. `osc()` ne porte plus le vibrato : le detune total
  (`vib + voiceOff + drift`) est calcule en amont.
- **Sax** : les formants **suivent la hauteur** (x10 % pour les formants d'anche,
  x5 % pour l'ancre) — un sax n'est pas un banc de filtres fixe. Ajouts : table d'anche
  pilotee par le souffle, **courbe de grit asymetrique** (demi-positive plus poussee),
  **LFO de souffle partage** (bruit + amplitude + detune), vibrato retarde a forme
  ecretee avec wobble des formants, chute de hauteur en **u^2** au relachement, clic de
  cle a l'appui **et** au relachement.
- Sortie sax abaissee de `1.1` a `0.8` : la mesure hote donnait un peak de 0,99 (trop
  pres de l'ecretage) ; 0,8 ramene le peak a 0,72 avec 0 echantillon ecrete.

### Verification (hote, pas d'emulateur)
`aetherfx` est **header-only et portable** : les deux moteurs se compilent avec
`g++ -O2 -std=c++17 -I app/src/main/cpp`, sans Android. Banc `/tmp/render.cpp` :
- Leads : peak 0,31 / rms 0,14 / 0 ecrete, **0 discontinuite**.
- Sax : peak 0,72 / rms 0,23 / 0 ecrete. Les « sauts » mesures sont **interieurs**
  (669) et non aux frontieres de bloc (3) : c'est la pente de l'onde d'anche, pas un
  joint de bloc.
`assembleDebug` (Kotlin + NDK, 4 ABI) -> **BUILD SUCCESSFUL**, APK 45,5 Mo,
`libnative-lib.so` porte bien `LeadsEngine::*` et `SaxEngine::*`.

### Reste
- Validation **appareil** (le son reel des deux moteurs sur une piste).
- Persistance du choix d'instrument Æther par piste (setlist) — toujours non branchee.

- Captures Roborazzi de l'onglet Æther et des deux fenetres d'instrument.
### Correction 2026-10-26 — le halo armé était un bloom, pas un liseré

Retour utilisateur : « pas ce genre de halo moche agressif… très fine et subtile
fondu propre à chaque case, pas ce truc qui remplit tout qui déborde ».

**Deux bugs empilés, chacun expliquant un symptôme :**

1. **Remplissait tout.** `recordingHalo` était un `Brush.radialGradient` étalé sur
   124 % de la case. Un dégradé radial **ignore la forme arrondie** : il peint le
   rectangle entier, coins compris, et les 12 % de débord se posaient sur les cases
   voisines. Remplacé par des `drawRoundRect` concentriques en `Stroke`, strictement
   à l'intérieur des bornes : le liseré suit les coins et rien ne sort.

2. **Était invisible au repos… puis peint sous le fond.** `drawBehind` peint **avant**
   le `.background()` de `MixerModuleV2` : le dégradé opaque de la case recouvrait le
   liseré. C'est pourquoi seul le débord du bloom se voyait — le bloom lui-même
   passait sous le fond, seules ses parties extérieures ressortaient. Corrigé en
   `drawWithContent` (`drawContent()` puis les anneaux), comme l'aura des pistes qui
   dessine dans la boîte déjà remplie.

**Profil mesuré** (capture 2340 px, traversée du bord gauche d'une case) :
`49 → 37 → 27 → 19 → 10 → 3 → 0` sur **6 px**, intérieur à 0. Profondeur 3,2 dp,
7 anneaux, alpha quadratique.

**Adoucis aussi** : LED resserrée (rayon 0,30 → 0,17 de la case, alpha 0,85 → 0,40),
remplissage de la case armée passé de `#5A2334` à `#3E2430`, intensité du liseré des
cases 1,0 → 0,62.

**Test garde-fou** : `armed_rim_is_visible_and_stays_inside_the_cases`
(`BottomBarCaptureTest`) vérifie les **deux** régressions à la fois — > 400 px tièdes
(sinon le liseré est invisible) et **0** px tiède dans les 700 px de droite (master +
clavier, sinon il déborde). 5/5 PASS.

## Session 2026-10-26 (parallele) — Æther : piste, fenetre, textes, perf

Conversation parallele (celle-ci) menee en meme temps qu'une autre qui poussait
`7f74d68` (snapshots) puis `2b4482e` (halo arme) sur `main`. **Aucun chevauchement
de fichiers** : cette session ne touche que `instruments/`, `SoundfontWindow.kt`,
`MixerScreen.kt`, `MixerViewModel.kt`, `NativeAudioBridge.kt`, `fx_mixer.h`.
Rebaser sur `origin/main` avant de pousser (regle habituelle).

### F — mute / solo / power n'avaient aucun effet sur une piste Æther

**Cause racine.** Quand un instrument Æther occupe une piste, `FxMixer::soundfontPaused(strip)`
est vrai : FluidSynth ne rend plus ce canal, c'est la **chaîne de strip** qui le rend.
Or tout le controle de volume du mixer passait par CC7 (`setChannelVolume` /
`safeSetTrackVolume`), donc dans le vide. Un mute ne coupait rien.

**Correctif** : `pushFxStripMix(ch, volume, pan?)` (`MixerViewModel`) miroite le fader
sur le strip **uniquement** quand `safeFxSoundfontPaused(ch)` est vrai — sur une piste
soundfont le strip reste vierge (le gain y appartient a la fenetre FX).
Appele depuis `applyTrackVolumes`, `setTrackVolume`, `setTrackPan`, et par le nouveau
hook `AeInstruments.onLoaded` (sinon l'instrument demarrait a l'unite).
`effectiveTrackVolume(track, anySolo)` factorise la regle off/mute/solo.

**Symetrie** : `FxMixer::setInstrument(strip, -1)` remet gain=1, mute=false, pan=0
(`fx_mixer.h`). Sans ca, le gain residuel attenuait le soundfont charge ensuite.

### N1 — soundfont a 1 preset : ne plus atterrir sur Presets

`SoundfontWindow.kt` : `tab = if (loadedFileId != null && loadedPresets.size >= 2) Presets else All`.
Un banc a un seul preset n'a rien a choisir : la liste ferait une ligne.

### A — les textes de scene (cause racine : `sp` + fontScale)

`AeT` passe par `size.fs()` qui produit des **`sp`**. Or toute la geometrie de la scene
est en `dp` (`su()`), et `AeStage` ne figeait pas le `fontScale`. Un utilisateur reglant
la taille de police systeme a 1,3x obtenait des libelles **30 % plus gros que leur case**
alors que la geometrie ne bougeait pas → textes ecrases, valeurs decalees. Les captures
Roborazzi (fontScale = 1) ne le montraient jamais.
- `AeStage` : `CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1f))`.
- `AeT` : `lineHeight = fontSize` (sinon le cadre Material3 de 24 sp fait plonger un
  libelle de 9 unites dans une case de 12), `wrapContentWidth(unbounded = true)` (le
  preview ne tronque jamais), et centrage dans la case selon `align`.

### E / D / B / C / G — fenetre d'instrument

- **E** : rouvrir la fenetre soundfonts d'une piste qui porte deja un instrument ouvre
  **directement** l'interface de l'instrument (`sessions.containsKey`), pas le choix des
  4 cartes. La fleche retour ramene au choix.
- **D** : le `✕` en haut a droite est remplace par une **fleche retour** sobre en haut a
  gauche (`BackArrow`, chevron dessine en Canvas). Titre decale a x=58.
- **B** : `AeTile` passe d'un empilement de `Box` absolues a une **`Column` a 3 bandes**
  (chips / visuel `weight(1f)` / titres) : les collisions titre-artwork disparaissent.
  Le bouton `Unload` monte dans la rangee du haut.
- **C** : `AeInstrumentsTabItem` resserre (padding 14/11 → 10/9, 13/9 sp → 12/8 sp,
  `lineHeight` pose).
- **G** : ouverture/fermeture de la fenetre en `AnimatedVisibility` (fade 220 ms +
  `scaleIn` 0.94 → 1).

### H — drawbar : suivi direct du doigt

`AeDrawbar` suivait le doigt **a travers** `animateFloatAsState` : le ressort amortissait
chaque pas, d'ou la sensation de trainer. Le cap suit maintenant `target` pendant le drag
(`dragging`), le ressort ne sert plus qu'aux changements externes (preset, undo).

### N2 — lourdeur du blur / TrackSettings / Patches : la vraie cause

Ce n'etait **pas** le flou lui-meme mais **ce qui bougeait derriere**.
`startPeakMeterSimulation` (`MixerViewModel`) mettait a jour les VU-metres a 5 Hz meme
quand une fenetre flottante couvrait le mixer : chaque `_uiState.update` recomposait tout
le sous-arbre **et forcait le blur a refaire sa passe hors ecran**. La boucle fait
desormais `delay(200); continue` (gel) tant que `activePopup.isFloatingWindow()`.
Meme traitement pour la derive des couleurs de piste (`MixerScreen`) : figee sous une
fenetre, elle ne coute plus de frames.

### I — allocations de la scene Sax

`AeAnim.parts` allouait un `FloatArray` par particule et par frame. Pool de recyclage
(`spareParts`) + `spawnPart(...)`. Fps au repos de la fenetre : 80 ms → **125 ms**.
`AeAnim` : les particules mortes retournent au pool au lieu d'etre jetees.

### Verification

`:app:compileDebugKotlin` et `:app:assembleDebug` → BUILD SUCCESSFUL.
Point non teste ici : le rendu **appareil** (le mute sur piste Æther, le fontScale a
1,3x). Les captures Roborazzi tournent en fontScale = 1 et ne peuvent pas voir le bug A.

## Session 2026-10-26 — setlist/patch vierge + verre depoli (3 demandes)

### 1. Bug « des soundfonts se chargent tout seuls dans un nouveau Setlist »

**Ce n'etait pas un chargement spontane du moteur, mais un heritage d'etat.**
`createSetlistFromScreen` (`MixerViewModel.kt`) ecrivait un JSON de scene **sans**
les cles `tracks` et `audioSlots` (et le patch embarque n'avait pas non plus ses
`tracks`). Or `selectScene` traite une cle **absente** comme « garder l'etat
courant » — c'est une regle de compatibilite pour les vieux fichiers. Resultat :
le nouveau Setlist heritait des pistes en memoire, `soundfontName` compris, et le
loader les repoussait dans le moteur natif. L'utilisateur voyait donc les
soundfonts du dernier Setlist apparaitre dans un Setlist qu'il n'avait jamais
rempli.

Correctifs :
- `model/TrackJson.kt` (nouveau) : codec partage `TrackJson` / `AudioSlotJson`.
  La duplication etait la cause — un seul ecrivain oubliait les cles.
- `createSetlistFromScreen` ecrit desormais `tracks`, `audioSlots`, `activePatchId`
  et les `tracks` du patch.
- `createNewPatch(isBlank)` : il ne dechargeait **pas** le moteur natif (contrairement
  a `createNewProject`). Ajout de `safeAllNotesOff` + `safeUnloadAllSoundFonts` +
  `safeTrimMemory`.

**Regle a retenir** : un lecteur qui traite une cle absente comme « garder l'etat
courant » transforme tout oubli d'ecriture en fuite d'etat. Tout nouvel ecrivain de
scene/patch doit passer par `TrackJson`/`AudioSlotJson`, jamais ecrire le JSON a la main.

### 2. Verre depoli des cartes soundfont

`Modifier.glassRow(active, opaqueBase)` (`SoundfontWindow.kt`) remplace les
remplissages plats `0xFF1F2836` / `0xFF19212E` de `SfPresetRow` et `SfFileRow`.
Trois couches : voile en degrade, liseré clair 1 px sur le bord haut, bloom d'accent
a gauche pour la ligne **chargee**. Le contour passe de 15 % a **7 %** de blanc en
repos (c'etait le contour dur qui paraissait « esthetique faible »).
`opaqueBase = true` pour les lignes de **fichier** : leur pastille poubelle de
swipe-to-delete est peinte dessous, un fond translucide la laisserait transparaitre.

Mesure de controle (`SoundfontGlassCaptureTest`, 2 captures) : ligne chargee
**+28,7** de luminance moyenne contre une ligne au repos, liseré a +88 sur la
premiere rangee, et **0 difference** en dehors de la ligne (diff max 2/255).

### 3. Case « Æther Instruments » : sous-titre retire

`AeInstrumentsTabItem` (`instruments/AeWindow.kt`) : la seconde ligne
« Organ · Synth · Leads · Sax » est supprimee ; `Column` devient `Box` centre.

### Environnement / remote

`git fetch` echoue avec le token en dur (`ghu_...` expire) : remettre l'URL avec
`${GITHUB_TOKEN}`. `origin/main` avance par petits commits « Add files via upload »
ou docs — verifier `git log HEAD..origin/main` avant de rebaser.

**Rapport HTML de session** (style impose) :
`/workspace/dl/session-ae-2026-10-26/index.html` — servi sur les deux ports.
Verifie avec `python3 tools/conv_sync.py` : la conversation parallele
`dde6dffb` n'a plus rien ecrit apres le 2026-10-26T18:41:48 (regle « pas de
workflow sans accord »). Le travail de cette session ne recouvre aucun de ses
fichiers (`MixerBottomBarV2.kt`, `SnapshotJson.kt`, `AppStatePersistence.kt`,
`BottomBarCaptureTest.kt`).

## Session 2026-10-24 — pitch bend Æther, bonnet de fader Leads, UI MIDI

Trois demandes utilisateur, toutes verifiees. **Compile : OK** (`assembleDebug`,
Kotlin + NDK 4 ABI).

### 1. Pitch bend des Æther Instruments : plage pilotee par Track Settings

Symptome : le bend partait a **1 demi-ton** et le reglage « Pitch bend range » de
Track Settings n'avait **aucun effet** sur les instruments Æther.

Cause : une source rend **hors de FluidSynth**. `setPitchBendSensitivity` ne
pousse que `fluid_synth_pitch_wheel_sens`, jamais consulte pour un canal dont la
source s'est emparé. Chaque source avait donc sa plage en dur :

| Source | Avant | Apres |
|---|---|---|
| Leads (`fx_leads.h`) | `bend_ * 2.f` fixe | `bend_ * bendSemis_` |
| Synth (`fx_synth.h`) | `1 + round(p(29)*11)` (1 a 12, dependait du knob « Bend ») | `bendSemis_` |
| Organ (`fx_organ.h`) | **ignore** (pas d'override) | ratio sur les increments de roues |
| Sax (`fx_sax.h`) | **ignore** | ratio sur la hauteur du roseau |

La plage vient desormais de `SourceFx::setPitchBendRange(int)` (defaut **2** =
**1 ton**), propagee par
`FxChain::setPitchBendRange` -> `FxMixer::setInstrumentPitchBendRange` ->
`AudioEngine::setTrackPitchBendRange(track, semis)` (nouvelle methode publique,
**au-dessus** du `private:`) qui pousse **les deux** renderers :
`mSynthEngine.setPitchBendSensitivity` **et** `mFx.setInstrumentPitchBendRange`.
Le JNI `setPitchBendRange` route par cette methode (plus par `getEngine`).

Kotlin : `MixerViewModel` pousse la plage au **chargement** de l'instrument
(`AeInstruments.onLoaded` -> `safeSetPitchBendRange(slot, track.pitchBendRange)`),
sinon la source gardait son defaut jusqu'au prochain reglage.

**Preuve hote** (`/workspace/audit/bend_test5.cpp`, `g++ -O2`) : frequence de
crete mesuree roue centree vs roue a fond, 4 sources, 3 plages (1/2/12 st),
12/12 PASS, erreur **<= 0,17 %**. Pieges du banc : un preset bricole d'un seul
parametre rend un timbre ambigu (le vrai preset « Pure Sine » donne 0,10 %), et
la voix Leads porte un vibrato + drift qui exige une fenetre d'analyse courte.

Note : le knob **Bend** du Synth (param 29) reste affiche (fidele au preview) mais
n'est plus la plage — c'est Track Settings qui commande, comme demande.

### 2. Bonnet des faders Leads = celui des pistes

`AeFader` (`AeLeadsUi.kt`) utilisait un SVG plat (`AeSvgs.FADER_CAP`). Remplacé par
le **composable Kotlin `FaderThumb`** (`ui/components/FaderThumb.kt`), celui des
faders de piste. `FaderThumb` est **portrait** (ratio 1327/1657) alors que la boite
SVG etait 38x26 : la boite garde le **centre** du cap sur la valeur
(`at(22 - capW/2, capY + 13 - capH/2, capW, capH)`), donc la course est inchangee.
La barre LED maison sous le cap a ete **retiree** : `FaderThumb` en porte deja une.
`AeSvgs.FADER_CAP` n'a plus d'appelant (laisse en place, inoffensif).

### 3. L'UI des Æther Instruments ne voyait pas les notes MIDI

Le son repondait (le natif route bien NoteOn/NoteOff vers la source) mais l'UI
restait inerte : `AeManager.onMidiNoteOn` n'est appele **par personne**. Les notes
sont consommees par la file native avant tout listener Kotlin, et
`onMidiNoteOnListener` ne porte que le **nom** de note, pas le canal.

Correctif : nouveau hook `AudioEngine.onNoteRoutedListener(channel, note, velocity, on)`
emis par `playMidiNote`/`stopMidiNote` — le **point de passage unique** (USB MIDI,
clavier virtuel, pads). Le ViewModel le branche sur
`AeManager.mirrorNoteOn`/`mirrorNoteOff`, qui mettent a jour **uniquement**
`AeSession.held` / `lastNote` / `noteEvents`. Ne **pas** utiliser `noteOn`/`noteOff`
la : elles repousseraient la note au moteur et la doubleraient. Le sustain est
respecte sans code special : pedale baissee, l'app n'emet pas le NoteOff (la note
attend dans sa propre liste), donc le miroir reste allume.

**Tests** : `AeManagerTest` **12/12 PASS**, dont 4 nouveaux garde-fous
(`mirrorNoteOnLightsTheKeyWithoutReplayingIt` verifie qu'aucune note ne repart,
`heldPedalKeepsTheMirroredKeyLitUntilItLifts`, etc.).

### Pieges du banc (a relire avant de toucher au bend)

- `setPitchBendRange` du JNI doit passer par `AudioEngine::setTrackPitchBendRange`,
  pas par `getEngine(...)`, sinon la source est muette au bend.
- Une nouvelle methode publique d'`AudioEngine` va **au-dessus** du `private:`.
- Mesurer un bend par suivi de hauteur est fragile : utiliser le **pic du spectre
  mesure** (Goertzel, pas de zero-crossings) et le **vrai preset** de l'app.

### Banc de bend preserve : `tools/aetherfx/`

`tools/aetherfx/bend_range_check.cpp` (README a cote) rejoue la preuve de plage de
bend **hors Android** : `g++ -O2 -std=c++17` suffit, `aetherfx` est header-only et
portable. 12/12 PASS attendu. C'est le seul moyen de valider le bend sans device,
puisque le chemin DSP est le meme que dans l'APK.

## Session 2026-10-27 — Sheets : cohérence visuelle, icônes, double-tap flottant

Travail en parallèle de la conversation `dde6dffb48c5475a931918c2e3ff38f9`
(faders/VU Leads, halo armé, instruments Æther). Aucun conflit : le local contenait
déjà `origin/main` (`0b76712`), vérifié par `git merge-base --is-ancestor`.

### 1. Chrome de Sheets aligné sur l'app (commit à venir)
`ui/theme/Theme.kt` : `SheetsColors` ne porte plus de valeurs propres — il **délègue**
à `AppColors` (BgTop/BgBottom/PanelTop/PanelBottom/Well/Dim/Accent/Mute/Soft…).
Nouveau `SheetsPageBackground` (dégradé BgTop→BgBottom + halo radial) utilisé par
Home, Settings et la coquille d'éditeur : les trois écrans peignaient un
`SheetsColors.Background` **plat**, c'est ce qui les faisait lire comme une autre app.
**Non touché volontairement** : couleurs de cartes (`Sheet.colorArgb`, `SheetPalette`)
et page de droite de l'édition — identité de la feuille, pas du chrome.

### 2. Emojis remplacés par des icônes vectorielles
`ui/components/SheetsIcons.kt` (nouveau) : 20 `ImageVector` maison, grille 24×24,
trait 2 px, caps/joins round — même famille que `SettingsTabIcons`. Couvre Back,
Undo, Redo, Play, Edit, Settings, Star, Plus, Lock, Unlock, ChevronDown/Right, Close
et les **6 marques musicales** (Fermata, Stop, Push, Accent, Segno, Coda).
- `GlyphButton(glyph: String, …)` → `GlyphButton(icon: ImageVector, …)`.
  Appelants migrés : HomeScreen (`⚙`, `+`, `★`), EditorScreen (`←`, `↶`, `↷`, `▶`,
  `◀ Prev` → `Prev`), SheetsSettingsScreen (`←`), FloatingSheet (`🔒`/`🔓`).
- `Drawer` : `▾`/`▸` → `SheetsIcons.ChevronDown`/`ChevronRight`.
- `ChartView.Mark.glyph()` (chaînes `⌒ ◇ < > § ⊕`) → `Mark.icon()`, rendues en `Row`
  d'`Icon` 12 dp. Le rendu des marques ne dépend plus de la police.
- **Piège** : `SimpleSlider`/`SwitchRow` ont été alignés sur l'app (rail `Well`,
  pouce `#DCE2EE`, switch 40×22 avec `animateColorAsState` + `spring`) — les anciennes
  valeurs (44×26, rail translucide) juraient à côté de `SetSwitch`.

### 3. Double-tap sur une carte → flotte AU-DESSUS du mixer
Avant : `FloatingSheetHost` vivait **dans** la page Sheets. Désormais le double-tap
(déjà câblé sur `SheetCard.onFloat`) remonte au mixer :
`HomeScreen.onFloatOverMixer` → `SheetsRoot` → `MixerScreen` →
`viewModel.openFloatingSheet(sheet.toSheetItem(uiState.sheets.size))` +
`navigateBackToMixer()`. Le même chemin sert au bouton « scène » de l'éditeur.
- `model/SheetItemBridge.kt` (nouveau) : `Sheet.toSheetItem()` aplatit la grille
  (une ligne par groupe de mesures, paroles en dessous) en texte Standard — la
  fenêtre flottante reconvertit elle-même vers la notation courante.
- `HomeScreen` : `SheetCard` perd `hidden`/`alpha` et le host local ; le paramètre
  `bottomInset`/`FloatingSheetHost` ne sert plus dans cette page.

### 4. Capture de vérification
`app/src/test/java/com/soundstage/mixer/SheetsCaptureTest.kt` (nouveau) :
`w891dp-h411dp-440dpi`, 3 feuilles écrites via le **vrai** `SheetsRepository` dans un
dossier temporaire, puis `waitUntil` sur « Midnight Drive » — la bibliothèque se lit
sur `Dispatchers.IO`, une capture sans attente rend une page **vide** (piège vérifié :
416 Ko vide → 857 Ko avec les cartes).
Mesure : les 3 cartes sont aux couleurs/positions attendues
(Midnight 634,189 · Salt 89,193 · Paper 355,190 en unités dp).

### 5. Segmented `1-7 / A-G` = composition du `Presets / All` (2026-10-27)
Demande utilisateur : « le truc 1-7 / A-G doit ressembler à la même composition que
Presets/All de la fenêtre soundfonts ». Avant : deux `Pill` dans une boîte
`SheetsColors.Chip` — rien à voir.
- Nouveau `SegmentedToggle(options, selectedIndex, onSelect, modifier, height)` dans
  `ui/components/Components.kt`, porté de `SfTabBar` (`SoundfontWindow.kt:975-1020`) :
  conteneur arrondi **12 dp**, fond `#CC0F141D`, bordure **1 dp blanc 8 %**,
  padding **3 dp** ; pouce arrondi **9 dp**, dégradé `#38445E → #2B3650`, déplacé par
  `animateDpAsState(spring(dampingRatio = 0.72f, stiffness = 500f))` ; libellés
  **12 sp bold** (`#F1F4FA` actif / `Mute` inactif) avec `animateColorAsState(tween(200))`.
  Le nombre de segments est libre (`segW = maxWidth / count`) — réutilisable ailleurs.
- Utilisé dans `HomeHeader` : `Modifier.width(132.dp)`, index 0 = Nashville (1-7),
  1 = Standard (A-G).
- Mesure sur la capture : conteneur x 682→812 (130 dp), pouce x 684→746 (63 dp = la
  moitié), texte « A-G » dans la moitié droite. Conforme.

## Session 2026-10-27 — Æther Sax : banque echantillonnee + table harmonique mesuree

Travail mene **en parallele** de la conversation `dde6dffb48c5475a931918c2e3ff38f9`
(veille par `tools/conv_sync.py`). Branche `main`, **aucun push**.

### Le sax est desormais hybride (synthese + samples)

- `app/src/main/cpp/aetherfx/fx_sax_samples.h` (**nouveau**) : lecteur des packs.
  En-tete + 24 o/region + ADPCM 4 bits. Region la plus proche, transposition,
  boucle, fondu de raccord, enveloppe attaque/relachement.
- `SaxEngine::setSampleMix(0..1, defaut **0**)` et `loadSamples()`. Le mixage se
  fait **apres** `form_[0..3]` : le sample porte deja son formant, le passer dans
  la chaine le compterait deux fois. A mix 0 la sortie est **bit-identique** a la
  synthese seule (verifie) — aucune regression tant qu'aucun pack n'est charge.
- `SaxEngine::setMeasuredTable(bool, defaut **vrai**)` : table harmonique
  mesuree (voir plus bas).
- **Reste** : embarquer les assets, pont JNI pour `loadSamples`/`setSampleMix`,
  validation appareil.

### `tools/saxpack/` — constructeur de packs (nouveau)

`extract.py` (MTG Solo Saxophones, CC-BY 4.0 -> 4 packs) et `verify.py`
(couture / SNR / couverture MIDI). Resultat : **266 regions, 7,01 Mo**,
couture **0,974 mini / 0,994 mediane**, SNR 30,8 a 32,2 dB, MIDI 36..88.
La sortie (`out/`) est gitignoree : regenerable, la banque source fait 105 Mo.
`ATTRIBUTION.md` porte le texte CC-BY (obligation) et le repli CC0 (FreePats).

**Piege de licence, a ne pas rouvrir** : FluidR3 GM dit « DO NOT REDISTRIBUTE »
dans son chunk `ICMT` ; VSCO-2 CE n'a **aucun** sax (0 sur 3313).

### Table harmonique mesuree — deux pieges

Enveloppes `ENV_SOP/ALT/TEN/BAR` dans `fx_sax.h`, mesurees sur 33 notes/type avec
le **f0 mesure** (la banque est ~5 % basse ; un f0 lu du SFZ decale tout).

1. **Double formant** : le spectre mesure contient deja le formant de
   `form_[0..3]`. L'enveloppe est appliquee **en rapport** a la loi generique et
   **normalisee en niveau**, jamais brute.
2. **La table n'est pas normalisee** : sa crete varie d'un facteur **4,5** avec
   `q` (1,18 a 5,35) et le moteur s'y appuie. Preserver ce comportement.

**Le baryton a h2 et h3 au-dessus de h1** — verifie par f0 mesure (ratio 0,94,
pas d'erreur d'octave) et spectre absolu (h1 a -3,9 dB, maximum en h3). Grand cone
= fondamentale mal rayonnee. C'est reel, ne pas « corriger ».

### Trois pieges qui ont coute une passe

- **Le reechantillonnage casse la couture** : les boucles sont un nombre entier de
  periodes a 48 kHz, plus a 22,05 kHz -> 0,95 devient 0,82 sur les aigus.
  `snap_loop_end` **recherche** `loopEnd` sur +/-384 frames (retour a 0,99).
  **Ne pas recaler par estimation de periode** : l'autocorrelation accroche
  l'harmonique 2 sur les aigus, la boucle tombe une octave a cote, couture 0,32.
- **Le lecteur ne doit pas emprunter la memoire de l'appelant** : use-after-free
  des que l'asset est libere pendant qu'une note sonne (segfault constate).
  `load()` copie la charge utile.
- **Le decodage ADPCM ne doit pas etre paresseux** : le predicteur doit rejouer
  depuis le debut du region, donc le decodage dans la boucle de rendu est
  **quadratique**. Une passe par note dans un tampon reutilise.

### Niveau : normaliser en RMS, pas en crete

Le facteur de crete des transitoires du baryton placait le pack **2,7x sous** la
synthese. Regions normalisees en **RMS 0,20** (plafond de crete 0,95) +
`kSampleGain` 3,05 cote moteur. Resultat a mix 1,0 : **0,186 contre 0,230 RMS**,
**0 ecretage** (la synthese seule en ecrete 24).

### Ce qui n'est PAS un defaut (evite une fausse chasse)

~400 « sauts » de continuite mesures dans les packs baryton : original 48 kHz
**404** contre decode ADPCM **387**. Ils sont **dans l'enregistrement** (morsure
de l'anche). Controle sinus : ADPCM seul **46,6 dB** contre 29-32 dB sur du sax
reel — c'est le signal qui est dur a coder.

### Formants : ecart mesure avec `TY_f1`

Pic d'enveloppe mesure : sop 766 (`TY_f1` 617), alt 398 (**813**), ten 375 (570),
bar 342 (523). Soprano et alto divergent nettement. A creuser sur appareil.

### Environnement (rappel)

`JAVA_HOME=/workspace/jdk/jdk-21.0.13+9` (le chemin `jdk21` **n'existe plus** :
`restore_env.sh` le detecte, mais un export en dur casse le build).
Verification hote du moteur, sans Android : `g++ -O2 -std=c++17 -I app/src/main/cpp`.

## Session 2026-10-27 (suite) — presets Æther v2 + anti-tac des leads

Brief externe (discussion DeepSeek partagee). Les sources de verite restent les
JSON : `app/assets/aether-instruments-final.zip` -> `presets-*.json`, recopies
dans `instruments/AePresets.kt`. **`param_order` du JSON donne l'ordre exact des
32 valeurs** — c'est lui qui permet de convertir une intention en tableau.

### Piege majeur : le brief decrit un moteur qui n'existe pas
Le brief ecrivait des presets en `osc1`/`osc2`/`fm`/`layer1`/`arpRate`/`chord`.
**Aucun** n'est un parametre de `fx_synth.h` (32 params reels : WaveA/WaveB,
Detune, Sub, Noise, Unison, Spread, Cutoff, Res, Drive, EnvAmt, FType, ADSR,
2 LFO, 4 macros, Mode, Glide, Voices, Bend, Tune, Level). Il faut **traduire**,
jamais inventer de champs, et **annoncer les ecarts** :
- pas de FM dans ce moteur : les presets nommes « FM » sont un sinus +
  enveloppe percussive (le nom ne decrit pas le timbre) ;
- pas d'arpeggiateur : un preset « Arp » est un *son* d'arpege.

### « tac tac » des leads — corrige et mesure (fx_leads.h)
Trois correctifs, pas quatre : la 4e du brief (marge de 5 ms sur `t0`) est propre
a WebAudio, ou l'evenement est programme dans le futur. Ici la note est deja
echantillonnee, il n'y a pas de rattrapage de tampon a couvrir.
1. attaque minimale **8 ms** ;
2. le sweep de filtre demarre **apres** l'attaque (il balayait pendant, d'ou un
   « ping » resonnant) ;
3. fondu du mix d'oscillateurs **avant** le saturateur (sinon il voit une somme
   pleine amplitude des le 1er echantillon : c'est le clic).
Mesure (pire saut echantillon a echantillon, 25 ms) : Gospel Supersaw
0,1248 -> 0,0839 (**-33 %**), Super Saw Lead 0,1252 -> 0,0856 (**-32 %**),
Pluck Lead 0,1086 -> 0,0989. Les sinus ne cliquaient pas : inchanges.

### Regle de classement (utilisateur)
« Concentrer les leads dans Leads » : un preset qui se joue en **melodie** va
dans `leads`, meme si son genre est Kompa ou Gospel (d'ou `Kompa Poly Saw Lead`
et `Kompa Guyad Lead` cote leads). `synth` garde pads, basses, claviers, stabs,
cordes, FX. Trois leads ont ete retires de `synth` pour cette raison (Silk Lead,
Warm Analog Lead, Zouk Love Lead) ; les 24 autres presets de l'app sont
**conserves** (identite Kompa/Zouk/Haiti, 808, Reese...) : le brief decrivait
« la nouvelle liste », il ne demandait pas de supprimer l'existant.

### Comptes
`AePresets.kt` : organ 22 (inchange), **synth 55**, **leads 32**, sax 23
(inchange). `organ` et `sax` verifies **octet pour octet** contre HEAD.
Les chips de categorie (`PresetList`) sont un `LazyRow` : elles defilent, donc
18 categories passent sans casser la mise en page.

## Session 2026-10-27 (sync parallele) — Sheets flottant + Æther Lot B

Contexte : reprise apres la conversation parallele `dde6dffb48c5475a931918c2e3ff38f9`.
**Tout son travail est deja dans l'arbre** (dernier commit remote `8513678`, ma base).
L'outil de veille est `python3 tools/conv_sync.py` (watermark dans
`tools/.conv_sync_state.json`, gitignore) : a lancer **avant et apres** chaque
modification. Il a confirme 0 nouvel evenement cote parallele pendant cette session.

### Sheets — fenetre flottante du mixer (commit `acc035d`)
`ui/components/FloatingSheetWindow.kt` (code mort) supprime. Le mixer reutilise
desormais `FloatingSheetHost` (le design valide des Sheets) :
`MixerViewModel` porte `floatingSheet` / `floatingSheetIsLeft` / `floatingSheetScale`
+ `openFloatingSheet` / `toggleFloatingSheetPosition` / `setFloatingSheetPosition` /
`closeFloatingSheet` (via `Sheet.toSheetItem(...)`). Le call-site `SheetsRoot`
passe `SheetsRepository(viewModel.fileManager.sheetsDir)`.

### Æther — Lot B (commits `f461f24`, `41b4a53`)
- **Etat conserve au dechargement** : `AeManager.savedState` (cle = strip + instrument)
  garde valeurs, index de preset et page. `load()` les restaure ; `unload()` les
  enregistre. Recharger un instrument **n'est plus une remise a zero**.
  Test : `AeManagerTest.reloadingAnInstrumentRestoresItsKnobsAndPreset` +
  `switchingBackToAnInstrumentKeepsItsStateToo` (15 tests, 0 echec).
- **Puce « LOADED » -> petite LED verte** (`AeWindow.AeTile`) : la carte se lit deja
  selectionnee par sa bordure, un chip de la taille du titre encombrait.
- **Double-tap sur un knob = reset** a la valeur du preset courant
  (`AeManager.presetValue`, `Modifier.aeKnobInput`). Le reset lit le **preset**, pas
  une constante : il defait le reglage d'origine.
- **Fader master pousse a la source** : `setTrackVolume(0, …)` et `ensureEngineStarted`
  envoient aussi `safeFxStripGain(FX_STRIP_MASTER, vol)`. Sans ca, un instrument Æther
  (qui rend **hors** FluidSynth) gardait son propre gain et le slider master ne le
  bougeait pas.
- **SoundfontWindow** : choisir une **categorie** soundfont quitte l'interface
  instrument (`aetherTab = false`). Avant, les cartes Æther restaient affichees pendant
  la navigation du soundfont — la fenetre semblait bloquee sur l'instrument.

### Compilation / tests
`:app:compileDebugKotlin` -> BUILD SUCCESSFUL. `AeManagerTest` -> 15/15.
Rappel : deux builds Gradle en parallele tuent la sandbox ; un seul a la fois.

## Session 2026-10-27 (suite) — fenetre Æther immediate, verre, presets Organ, caches

Reprise de la liste « Traite les restants » (27/10). `tools/conv_sync.py` confirme
**0 nouvel evenement** cote conversation parallele `dde6dffb…` (dernier = 26/10
18:41) : elle est en retard sur `main`, tout son travail est deja un ancetre de HEAD.

### Fenetre Æther : ouverture immediate (point 4)
`SoundfontWindow.kt` : la fenetre instrument passe de `fadeIn(tween(220))` +
`scaleIn(spring)` a une **entree sans animation**. Le delai se lisait « ca met du
temps puis ca apparait ». La **sortie** garde son fade (`fadeOut(160)` +
`scaleOut(tween(180))`) : elle ne genait personne et adoucit la fermeture.
`scaleIn` reste utilise ailleurs (l.519, 1204) — l'import reste necessaire.

### Verre depoli : base opaque obligatoire (point 5)
Regle generale, verifiee deux fois : **`glassRow` ne se voit que sur une base
opaque**. Sans elle, la veil translucide se pose directement sur le degrade du
panneau et parait plate — c'est exactement le symptome « le verre ne s'applique
pas ».
- Lignes de la page **Presets** : `glassRow(active, opaqueBase = true)`.
- Cartes de la fenetre **patches** (`PatchesOverlay.kt`) : meme traitement (fond
  opaque `0xFF1E2637`/`0xFF171E2B`, veil, lisere haut 1 px via `drawWithContent`).
  L'import `androidx.compose.ui.draw.drawWithContent` a ete ajoute.

### Presets Organ : standards, plus de Kompa (point 6)
`AePresets.kt` : les 5 presets `Reggae / Kompa` (Reggae Bubble, Ska Stab, Kompa
Skank, Kompa Warm Pad, Zouk Love Pad) sont remplaces par les standards demandes :
**B3 Full / B3 Fast / B3 Slow / Full Bar / Pipe Organ**. Le reste (Jazz, Gospel,
Church, Rock/Blues, Neo-Soul, Pop/Funk) est inchange.

### Organ : dial vibrato + knobs du bas
- Le selecteur **V1..C3** ne reagissait qu'au clic (`(step+1)%6`) : jusqu'a 5 appuis
  pour traverser. Il suit desormais le **drag vertical** (`awaitEachGesture` +
  `drag`), comme les autres potards. Piege : lire `LocalAeScale.current` **hors**
  du `pointerInput` (l'appel est `@Composable`, interdit dans le lambda).
- **CLICK / DRIVE / TONE / SPACE / LEVEL** : 34 -> **40** unites, la rangee
  paraissait trop petite sous les libelles.

### Scroll de la liste soundfonts conserve (point 13)
`rememberLazyListState` est hoiste au niveau `MixerScreen` et passe en parametre a
`SoundfontWindow` / `SfList` (`rowsListState`). Fermer puis rouvrir la fenetre ne
remet plus la liste en haut.

### Fuite memoire (point 12)
Le dechargement du patch sortant est **deja fait** (`7f74d68`, ancetre de HEAD :
`SnapshotLogic.retainedSoundfontPaths` / `slotsToRelease` + purge des ids dans
`selectPatch`). Fuite residuelle corrigee : `deleteSoundFontFile` retirait le
fichier des listes mais laissait `sfParsedPresets`, `sfParsingFiles`,
`sfFavorites` et `sfCategoryOverrides` indexes sur son nom/chemin — la liste de
presets deja parsee restait en memoire toute la session. Purges a la suppression.

### Commits
`fe055c7` (fenetre/verre/presets/scroll), `96af4de` (Organ dial + knobs),
`916689e` (purge caches). `:app:compileDebugKotlin` = BUILD SUCCESSFUL a chaque fois.

## Session 2026-10-28 — assets UI Pianos/Strings : dimensionner sur l'affichage (mesure)

Demande : images de piano comme **fonds d'UI**, reduire le poids **sans perdre la
nettete**, cible **< 10 Mo** d'assets. Les 3 derniers pianos (mellow/lady/electric)
arrivent de Qwen, le 6e string (PAD) de Gemini.

### La mesure qui tranche : la resolution utile est 750 px, pas 1280

Le preview (`app/assets/Æther Pianos & Strings · preview.html`) est un **canvas
WebGL**, pas du CSS. Mesure au CDP sur le vrai rendu :

| Grandeur | Valeur |
|---|---|
| Canvas telephone | **500 × 230 CSS** |
| `devicePixelRatio` | **plafonne a 1,5** (`Math.min(devicePixelRatio||1,1.5)`) |
| Resolution utile telephone | **750 × 345 px** |
| Filtrage de texture | `LINEAR`, **sans mipmaps** |

Le dernier point est decisif : sans mipmaps, une texture plus grande que sa zone
d'affichage est sous-echantillonnee en lineaire — elle **ajoute de l'aliasing**.
Au-dela de 750 px on paie des octets pour une degradation. Les sources Qwen font
2736×1520, soit **3,6× la resolution utile**.

### Format : AVIF, pas WebP (mesure a qualite egale)

| Format | Taille | SSIM |
|---|---|---|
| WebP q80 @750 | 45,7 Ko | 0,967 |
| **AVIF q80 @750** | 64,5 Ko | **0,986** |
| PNG source | 7,7 Mo | 1,000 |

A qualite egale l'AVIF est ~30 % plus petit ; a taille egale il est nettement
meilleur. **Decodage verifie** dans le moteur de rendu : `img.decode()` puis
`naturalWidth` vaut bien 750 sur `data:image/avif;base64,...` — le navigateur ne
l'ignore pas silencieusement.

### Resultats

| Lot | Avant | Apres |
|---|---|---|
| HTML complet | 11,61 Mo | **0,69 Mo** |
| 15 instruments × 2 paliers | 61,6 Mo (PNG) | **4,25 Mo** |

SSIM ≥ 0,985 pour **tous** les fichiers, mesure **a la taille d'affichage**.
Cible « < 10 Mo » tenue avec 14× de marge.

### Outils (dans `/workspace/dl/tools/`)

- `uikit.py` : `prep` (LANCZOS, un seul passage), `reference` (la cible est la
  **source pleine resolution** vue a la taille d'affichage, pas l'image deja
  reduite — sinon on se compare a soi-meme), `pick` (cherche la qualite la plus
  **basse** qui tient le SSIM cible).
- `make_all.py` : 15 instruments × 2 paliers (750 telephone / 1500 desktop).
- `build_html.py` : reinjecte les AVIF dans le HTML par slot.

### Pieges verifies

- **`devicePixelRatio` est plafonne a 1,5.** Ne pas dimensionner sur un DPR de 3.
- **Comparer un 750 px a un 2736 px** donne un SSIM artificiellement bas qui
  pousse a sur-dimensionner. Toujours mesurer a la taille d'affichage.
- **Les images du preview ne sont pas celles du depot.** Le HTML d'origine
  embarque *ses* images (NCC 0,41 avec les photos fournies). Un ecart de
  luminance apres remplacement n'est pas un bug : corréler avec l'asset
  (0,72 = affiche, 0,39 = non affiche) avant de conclure.
- **`/tmp` est efface a chaque crash sandbox.** Les sources Qwen (`/tmp/qwen/`)
  et Gemini (`/tmp/gemini_img_0.png`) doivent etre re-telechargees.
- **Mappage Qwen** : `raw_01/02/03` = mellow/lady/electric ;
  `raw_07..11` = solo, cello+bass, section, orchestra, pizzicato ;
  Gemini = pad. `raw_05`/`raw_06` = **1re tentative rejetee**, ne pas reutiliser.

### Etat

- 30 fichiers AVIF dans `app/src/main/assets/instruments/` (4,4 Mo).
- Le HTML de reference est **desormais dans le depot** (commit `f731fb4`,
  « Add files via upload »), identique octet pour octet a la source de travail.
- **Les strings restent proceduraux** dans le HTML (particules `pet`/`pal`) :
  les 6 images ne sont pas encore branchees, les slots strings n'existent pas
  cote `IMG`. A faire quand l'UI strings sera portee en Compose.

## Session 2026-10-28 (suite) — previews separes + animations adoucies

Demande : **deux HTML separes** (piano / strings) pour juger le rendu, **reduire
les animations** (eau plus calme, chutes -50 % et **non lineaires**), et pour
Strings **les images immediatement, meme composition que le piano** (plus
d'instruments flottants).

### Deux fichiers, une source

`app/assets/Æther Pianos · preview.html` et `Æther Strings · preview.html` sont
**generes** depuis `Æther Pianos & Strings · preview.html` par
`tools/uiassets/split_preview.py` (ne pas editer les deux fichiers a la main :
editer la source puis regenerer). Le script utilise `sub()` qui **asserte** que
l'ancre existe — une ancre perimee leve au lieu de produire un no-op silencieux.

- **Piano** : 9 slots, onglet `PIANO` unique, photos AVIF 750 px.
- **Strings** : 6 slots (solo/cello/... partagent leur image), onglet `STRINGS`
  unique, `setBg(SM[i].id)` au lieu de `PT=SM[i].pal`.
- Le selecteur PIANO/STRINGS est retire dans les deux : chaque fichier est
  dedie, un onglet mort n'aurait aucun sens.

### Strings : la photographie, pas la scene procedurale

Le preview d'origine bascule sur `procScene` (ciel procedural + instruments SVG
flottants sur l'eau) via `modeT=m=='strings'?1:0`. Pour Strings :
`modeT=0` en dur, `if(modeS>.003){drawInst(Tm)}` supprime, et le **bloc SVG des
violons entier est retire** (`/* ---------- violin family` -> `/* ---------- GL
scene`). Ce bloc etait rasterise au demarrage en 4 canvas de 720 px pour rien.
Fichier : 777 Ko -> 759 Ko, et plus de rasterisation inutile.

Preuve du cablage : `bgB` (cle de texture liee) egale l'id du preset pour
**8/8** strings et **9/9** pianos. Verifier autrement est trompeur — la
correlation d'image est dominee par la luminance globale (marges de 0,002 entre
pistes), donc non discriminante.

### Animations adoucies (les deux fichiers)

| Element | Avant | Apres |
|---|---|---|
| Propagation de l'onde `cx,cy` | `.30,.13` | **`.16,.07`** |
| Amortissement `dm` | `.9925` | **`.9880`** |
| Deplacement image (shader) | `g*.07` | **`g*.035`** |
| Specular image | `*sp*.5` | **`*sp*.26`** |
| Deplacement eau proc. | `g*.06` | **`g*.03`** |
| Goutte ambiante | `.55+rand*.9` | **`.26+rand*.44`** |
| Goutte touche | `.5+vel*.9` | **`.24+vel*.44`** |
| Gravite particules | `+=40*dt` | **`+=(p.vy<0?40:5)*dt`** |
| Cadence de spawn | `petal:3.2` (Poisson) | **`PRATE.petal=1.6`** |

**Mesure** (CDP, 12 echantillons sur 6 s) : particules **30,8 -> 15,7**
(−49 %). Bruit de fond des vagues **0,149 -> 0,059** (2,5x plus calme) ; apres
une goutte de reference l'onde retombe a 0,085 a t=3 s (contre 0,142).

### Le piege du spawn non lineaire : normaliser la moyenne

La demande « non lineaire, vraiment du random » est implementee par un
intervalle a queue lourde : `(.25+1.75*u^2.4)/PTAIL/(PRATE[pType])`.

**Sans `PTAIL`**, la moyenne du multiplicateur vaut `0,25+1,75*E[u^2.4]` =
`0,25+1,75*0,29412` = **0,7647** : la cadence reelle etait `1,6/0,7647 = 2,09`
au lieu de 1,6, soit **−35 % au lieu de −50 %**. Mesure initiale : 30,8 -> 19,2.
Corrige par `const PTAIL=.76471` (division). Verifier ce genre de formule par
`E[f(u)] = integral`, jamais a l'oeil.

### Ou sont les choses

- Generateur : `tools/uiassets/split_preview.py`
- Capture par preset : `tools/uiassets/cap_presets.py` (selections via l'API du
  preview, attente du crossfade avant capture)
- Planches : `tools/uiassets/sheets/sheet_{piano,strings}.png`
- Fichiers servis : `/workspace/dl/pub/` (port 12000)

### Etat de cablage des AVIF (verifie 2026-10-28)

**Aucun code Kotlin ne reference les AVIF.** `grep -rn "instruments/" --include=*.kt`
et la recherche des noms d'asset (`piano_felt`, `strings_solo`) ne renvoient rien.
Les 30 fichiers de `app/src/main/assets/instruments/` ne sont consommes que par
les **previews HTML**. Consequences :

- l'APK embarque 4,25 Mo d'images jamais lues par l'app (a brancher, ou a retirer
  du module si les previews suffisent) ;
- `app/assets/*.html` n'est **pas** dans l'APK (seul `src/main/assets` l'est), donc
  les 2,2 Mo de previews ne pesent rien sur le binaire.

`./gradlew :app:compileDebugKotlin` -> BUILD SUCCESSFUL (1m32), 0 erreur.

### Synchronisation conversation parallele (2026-10-28)

Le commit `4ac5f58` (« Snapshot recall », session du 10-25) de la conversation
parallele est un **ancetre de HEAD** : son travail est bien integre. Les sessions
10-25 / 10-26 / 10-27 sont presentes dans ce fichier (14 occurrences). Le remote
`origin/main` et le local etaient identiques apres rebase — rien a rattraper.

## Session 2026-10-28 (2e) — synchro Clone 2 : merge + veille multi-pairs

Contexte : deux conversations tournent en parallele. **Clone 2** = `f0aa78669e1640bca4708537876e5ce2`
(Æther Piano + Strings, assets AVIF). L'ancienne (`dde6dffb…`) = moteur FX/mixer.

### Merge fait (commit `2cd2195`)

Clone 2 avait pousse 6 commits sur `origin/main` (`f731fb4`..`6eb86e4`) : 30 AVIF dans
`app/src/main/assets/instruments/`, les 3 previews HTML, `tools/uiassets/*`.
**Seuls `AGENTS.md` et `GUIDE_AGENT.md` conflictaient** (les deux agents y ajoutent des
sections) : resolution en **gardant les deux cotes** (sections purement additives).
`:app:compileDebugKotlin` -> BUILD SUCCESSFUL apres merge.

### Veille multi-pairs (`tools/conv_sync.py`)

Le script suit desormais **plusieurs conversations** via une table `PEERS` :
`--all` les parcourt toutes, le defaut est `CLONE2`. Watermark par conversation dans
`tools/.conv_sync_state.json`. **A lancer avant ET apres chaque modification.**

### Etat des assets (Clone 2, verifie)

- 30 AVIF (`_750` telephone / `_1500` desktop), 4,4 Mo, dans `app/src/main/assets/instruments/`.
- **Aucun code Kotlin ne les reference** : ils ne sont consommes que par les previews HTML.
- Regle de dimensionnement : la resolution utile du preview est **750 px** (canvas 500 CSS
  × `devicePixelRatio` plafonne a 1,5), filtrage `LINEAR` **sans mipmaps** -> sur-dimensionner
  degrade. Format **AVIF** (SSIM ≥ 0,985 a la taille d'affichage).
- Les **strings restent proceduraux** dans le HTML : 6 images pretes mais slots non branches.

## Session 2026-10-29 — UI Æther (faders, segments, power, nommage), snapshots, sax samples-only

### 1. Faders Leads = rail du mixer
`AeFader` (`AeLeadsUi.kt`) dessinait un SVG plat (`AeSvgs.FADER_TRACK`) + un bonnet SVG.
Désormais le rail est **`FaderRail`** (`ui/components/FaderRail.kt`, celui des pistes) et le
bonnet **`FaderThumb`**, tous deux réutilisés tels quels. Le rail fait 86 unités de large dans
son propre espace (scalé depuis la boîte de 24) et sa rainure est **inset de 36 unités** en
haut et en bas — c'est ce que `CAP_TOP`/`CAP_BOTTOM` couvrent déjà, donc la course du bonnet
ne change pas. `AeSvgs.FADER_TRACK` n'a plus d'appelant (laissé en place).

### 2. `AeSeg` : segmented control à l'échelle de la scène
Nouveau composable (`AeControls.kt`), porté du `Segmented` de Track Settings : piste + pouce
qui glisse (`animateFloatAsState`, spring). Utilisé pour l'**octave par couche** des Leads
(« −1 0 +1 »), qui était 3 `AeChip` indépendants : trois boutons ne se lisent pas comme un
réglage à trois positions. `AeSeg` sert aussi de base aux groupes LP/HP/BP.

**Piège** : `h` est un `Number` (type scène) → `h - 4` ne compile pas, écrire
`h.toFloat() - 4f`. Et un paramètre `onSelect` suivi d'un lambda trailing ne se résout pas
(déclaré après `w`/`accent` mais avant `h`) → nommer l'argument.

### 3. Chips : état enfoncé, pas de pastille
`AeChip` sélectionné n'est plus un dégradé plein + contour : c'est un **puits sombre** avec
ombre interne en haut, libellé dans la couleur d'accent et une **lueur radiale** derrière. Un
contrôle « enfoncé » doit paraître creusé ; le plein se lisait comme un autocollant.

### 4. `AePowerIcon` : centrage + relief + rebond
Deux bugs dans la version précédente :
- **centrage** : le glyphe (arc + tige) a son centre optique au-dessus du centre de l'arc →
  centrer l'arc dans la boîte poussait la marque vers le bas. L'arc est posé sur un cercle
  décalé de `s*0.055` vers le bas, la tige part du haut de ce cercle.
- **relief** : plus de contour dur ; puits + ombre interne, glyphe embossé (copie sombre
  décalée + copie allumée), et l'appui répond par un spring (`Animatable` + `detectTapGestures`).

### 5. Feuille de nommage : largeur bornée + matière
`NameSheet` était un `Dialog` contenant une `Column` : un Dialog se dimensionne sur son
contenu, donc la feuille s'étalait sur toute la largeur de la fenêtre. Corrigé par
`Modifier.width(340.dp)`, un **grain vertical** déterministe (pas de scintillement), un liseré
haut clair / bas sombre, un champ en puits, et un bouton Save en relief.

### 6. Snapshots ↔ instruments Æther (nouveau)
`TrackSnapshot.aether: AetherTrackState?` (`MixerState.kt`) = `instrument` (**nom d'enum**,
pas l'ordinal : les ordinaux ont déjà changé), `preset`, `values`. Codec dans `SnapshotJson`
(clés `aetherInstrument` / `aetherPreset` / `aetherValues`).
- `AeManager.snapshotState(strip)` / `restoreFromSnapshot(strip, st)`.
- `captureSnapshot` (VM) ajoute le bloc ; `recallSnapshotDirect` le restaure **après** la passe
  soundfont (les deux sont exclusifs sur une piste : charger l'un décharge l'autre) et
  `unload(strip)` quand le snapshot n'a pas de bloc.
- **Blob ancien** : les clés absentes donnent `null`, jamais un instrument inventé. Un bloc
  `aetherInstrument` **sans** `aetherValues` est ignoré (rien à restaurer).

### 7. Sax : samples seuls (plus d'hybride)
`loadBank(mix)` passe de `0.7` à **`1.0`** : le pack porte la voix, la synthèse n'est plus
mixée dessous. Un mix < 1 reste possible pour un A/B de diagnostic.

### 8. Pack sax : accord corrigé (cause racine)
Le champ `tuneCents` livré **ne décrivait pas l'audio** : le lecteur compense par
`rootF *= 2^(tuneCents/1200)`, donc une valeur fausse transposait la note. Mesure sur le pack
décodé (ADPCM) : **+28 cents en moyenne (sop), +11 (alt/bar), +16 (ten)**, et le Do5 de l'alto
restait **+44 cents** même après correction (sa région n'était pas mesurable par le test
harmonique). Outil : `/workspace/audit/retune.cpp` (fondamentale confirmée par énergie en 2f/3f,
repli par octave la plus proche du root, réécriture du champ). Résultat moteur : **max 12 cents**
(contre 52). **Piège** : ce repli par octave est nécessaire — sans lui les régions non
mesurables gardent leur valeur périmée, et c'est exactement ce qui laissait le Do5 faux.

### 9. Lecteur d'échantillons : deux clics corrigés (`fx_sax_samples.h`)
Le mix à 1.0 a révélé ce que la synthèse masquait. Mesuré : saut de **0,57** contre **0,09**
pour la synthèse, **une fois par période de boucle** (~95 ms).
- **Cause** : le crossfade fondait la queue de boucle vers la **tête** `x[ls..ls+xf)` alors que
  le rebouclage repart à `ls` → la dernière frame fondue était à `xf` frames après `ls`, d'où
  un saut de 3 ms à chaque tour. Corrigé en fondant vers la **phase précédente**
  (`x[ls-xf..ls)`). Saut max : 0,57 → **0,155**.
- **Release** : `rel` du lecteur valait 0,09/frame (~1 ms). Acceptable sous la synthèse (elle
  porte son propre release), pas en samples seuls → clic au relâchement. Passé à `3.5e-4`
  (~60 ms) : pas max dans la queue 0,09 → **0,004**.
- **Piège de mesure** : un test de seuil `|x| < ε` déclenche sur le premier passage par zéro
  d'une note encore sonore (« release 1 ms »). Mesurer en **RMS fenêtré**.

**Deux lectures de `retune` à ne pas confondre.** `ecart moyen +28,5 cents` est la dérive
**de l'enregistrement** (le lecteur la compense : c'est ce que le champ stocke désormais).
`regions dont tuneCents change de plus de 2 cents : 0` est la preuve que le champ est à jour.
Comparer la dérive mesurée au champ stocké, jamais au zéro : un écart moyen non nul est normal.

**Ne pas mesurer la justesse sur une mélodie rendue en continu.** Les fenêtres d'analyse
chevauchent les notes suivantes et le résultat est faux (annoncé +100 cents sur un moteur
juste). Mesurer **note isolée**, et lire les **trois pics** du spectre : sur ce sax, la
fondamentale est souvent *plus faible* que l'harmonique 2 (mesuré : pic le plus fort à
+1206 cents, soit exactement une octave). Chiffres moteur, preset réel, vibrato inclus :
synthèse +0,3 à +9 cents, pack +2 à +18 cents.

### Tests
`AeManagerTest` **25** (dont 5 snapshots), `AetherSnapshotJsonTest` **5** (nouveau, Robolectric
— `org.json` lève « not mocked » sur la JVM nue), `SnapshotLogicTest` **11**. 0 échec.

## Session 2026-10-28 (3e) — Æther Piano : samples legers + boost (mesure)

But : des samples **legers** qu'on **faconne** nous-memes pour obtenir **9 presets
piano de bonne qualite** (Pianoteq tient en ~200 Mo). On ne prend **rien** des FX
existants et on ne copie aucun moteur : on cree le boost.

### Licence = premier filtre (verifie)

| Banque | Licence | Contenu | Boucles | Verdict |
|---|---|---|---|---|
| **SplendidGrandPiano** (AKAI/Steinway) | **domaine public** | 62 notes x 5 couches, 73 Mo | `smpl` present mais fin = EOF | **principal** |
| GregSullivan.E-Pianos | CC-BY 3.0 | Wurlitzer 2,3 Mo, CP80 10,5 Mo | vraies boucles, courtes | **secondaire** (Electric) |
| SalamanderGrandPiano | CC-BY 3.0 | 16 couches, **714 Mo** | **aucun `smpl`** | rejete (trop lourd) |
| jlearman.jRhodes3c | CC BY-**NC**-SA | 17,8 Mo | boucle | **rejete — non commercial** |

### Taille : ~1,4 Mo (mesure sur les octets reels)

21 notes x 3 couches = 46 regions. FLAC 16,2 Mo -> pack **1,37 Mo** (ratio 0,0844,
**~30 Ko par region**). Pipeline : mono, 22,05 kHz, troncature attaque+boucle,
ADPCM 4 bits. 5 couches x 1 note sur 2 -> ~4 Mo.

### Trouver la boucle (le `smpl` ne suffit pas)

Splendid : `loopEnd` = derniere frame du fichier (seam −0,85). Salamander : aucun
`smpl`. Il faut **chercher** : fenetre 0,35–1,6 s dans le sustain, score =
correlation tete de fenetre <-> segment une periode plus loin -> **+0,91..+0,98**.

### Le clic : mesurer le saut, pas la correlation

Boucler la fenetre brute saute **5–10x** le plus grand pas interne (vs 99,9e
percentile des sauts internes) : clic audible. Crossfade de 12 ms regle le probleme
**si le poids est plein a l'index 0** (`w = linspace(1,0,X)`) :

| Note | brut | crossfade 12 ms |
|---|---|---|
| FF C3 | 5,38 | **0,01** |
| PP C4 | 10,64 | **0,11** |
| MF A2 | 10,31 | **0,07** |

Avec le poids inverse, le ratio ne bouge pas (5,41 -> 5,60 de 12 a 100 ms).

### Le boost (a creer, pas a copier)

Moteur qui prend une note echantillonnee simple et en fait un piano credible :
resonateurs de cordes accordes sur les harmoniques, sympathetic/release, caisse &
marteau, lid/micros/espace, queue de note modelisee. Les **12 parametres** du code
piano existant (`AePianoUi.kt`) sont la base : on les **affine**.

Plan et chiffres : `tools/pianopack/README.md`.


## Session 2026-10-29 — Æther : presets utilisateur, Intensity, liste par categorie, pitch bend

Branche `main`, **aucun push**. Base `f68727c` (cartes Æther). Commit `7bf93a9`.

### Sandbox reinitialise entre les sessions
Le JDK et le `debug.keystore` **disparaissent** (comme `/tmp`). Restaurer :
`sudo apt-get install -y openjdk-21-jdk-headless` (JAVA_HOME `/usr/lib/jvm/java-21-openjdk-amd64`)
puis `keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey
-keypass android -keyalg RSA -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`.
Sans le keystore, `assembleDebug` echoue sur `validateSigningDebug` (**le natif compile deja** :
ce n'est pas une regression). `debug.keystore` est gitignore.

### Version Compose des tests differente
`androidx.compose.ui.test.onNode` **n'est pas** une fonction d'import dans cette version :
`composeTestRule.onNode(...)` est une **methode de membre**, aucun import. Idem `onRoot()`.
`click`/`swipeUp`/`hasTestTag`/`isDialog` s'importent normalement.

### Point 1 — presets utilisateur (`AeUserStore.kt`)
JSON par instrument dans `SharedPreferences("aether_instruments")`, cle `user_<INSTRUMENT>`,
forme `[{"n":..,"v":[..]}]`. Miroir **`SnapshotStateList`** : la liste ouverte doit se
recomposer a la sauvegarde. `AeManager.presets()` = usine **+** utilisateur, donc un preset
sauve s'indexe comme les autres ; `saveUserPreset` / `deleteUserPreset` (jamais un preset
d'usine). UI : bouton disquette (`FloppyDiskIcon`) dans l'en-tete -> `NameSheet` (Dialog).

### Point 3 — Intensity = parametre natif 32 (Leads et Synth)
`kMaxParams` 32 -> **34**, `paramCount()` 33, `AeInstrument.SYNTH/LEADS` paramCount **33**.
**Neutre a 0,5** : verifie sur hote (`/workspace/audit/intensity.cpp`, `g++ -O2`) — la sortie
est identique au bit pres. Les presets anterieurs (32 valeurs) sont **completes a 0,5** au
chargement (`AeManager.padded`), donc inchanges ; sans ca le 33e knob serait ignore.

### Point 4 — liste par categorie
En-tetes de categorie dans **une seule** `LazyColumn` (plus de filtre qui masquait tout).
Choisir **laisse la liste ouverte** (audition) ; tap a l'exterieur ferme.
`testTag("aePresetList")` pour faire defiler jusqu'au groupe `User` dans la capture.

### Point 5 — pitch bend : Track Settings seule autorite
`audio_engine.h/cpp` portent `mTrackBendRange[8]` (defaut **2**). Le shaper repoussait sa
propre valeur a chaque mouvement de knob (`c.pitchSens = -1` puis re-push a 2), ce qui
**annulait le reglage** et faisait diverger l'affiche et le son. Les 2 sites de reset
relisent maintenant `mTrackBendRange`. Knob **Bend** (mort) retire de `AeSynthUi` ; le knob
**Voices** garde son libelle (le brief de la session perdue le disait inutile, mais il est
cable en natif — verifier avant de le retirer).

### Captures
`AePresetUiCaptureTest` : `ae_preset_list` (groupe User + Delete, mesure : or y≈384),
`ae_preset_naming`, `ae_leads_intensity` (remplissage cyan 177,8 dp = 0,5×370 sous les chips).

## Session 2026-10-29 (suite) — UI Æther resserrée + voix échantillonnée du sax façonnée

Commit `fc64eaf`. Conversation parallèle `dde6dffb…` : **aucun nouvel événement** (son
dernier travail est un ancêtre de HEAD). Mode économique : pas de `conv_sync` en boucle,
médias publiés en **JPEG q76 (1200 px)** et **MP3 mono 80 kbit/s**, jamais de PNG.

### UI — mesures sur captures, pas à l'œil

- **Rails de leads** : largeur réduite, **affichage de niveau retiré** (vérifié : 0 px clair
  dans la bande y262..280 sous les rails).
- **`AeSeg`** : le pouce était décalé de 2 unités par segment (il débordait de 3 unités à
  droite à l'index 1). Centrage corrigé. Creusé sans contour.
- **`AePageTabs`** (nouveau, `AeControls.kt`) : géométrie d'en-tête **partagée** Synth + Leads,
  x 264..532. Avant, les onglets Leads commençaient à 354 et la rangée Synth courait jusqu'à
  682 **sous la disquette à 620** : c'est le chevauchement signalé. En-tête réordonné :
  retour 10..40 | titre 58 | onglets 264..532 | disque 552..582 | préc 596..626 |
  pastille preset 640..866 | suiv 880..910.
- **Liste de presets** : refonte en **index compact à 2 colonnes** (30 unités/ligne au lieu de
  ~40), en-têtes de catégorie à filet + compteur, sélection par **barre d'accent** à gauche
  (plus de bloc lumineux), lignes alternées à 2 %, **vignette de défilement**. ~150 presets
  lisibles au lieu d'une dizaine. Mesuré : 10 lignes à gauche / 5 à droite visibles, gouttière
  centrale vide (2 colonnes réelles), barre d'accent à x 20..22.
- **Knobs** : agrandis (sax 42, organ 44, leads 46-58, synth 38-50) avec **positions
  recalculées**. La boîte tactile est bornée à `sz + 22` : au-delà elle dépasserait le pas le
  plus serré des fenêtres (66 unités, synth) et deux knobs se partageraient une bande tactile.
  Un audit par rectangles est **trompeur** : il faut filtrer par page rendue, sinon il
  « détecte » des collisions entre des pages différentes.
- **`AePals.of(instrument)`** : couleur résolue sans session (la pastille de preset en a besoin).

### Sax — les samples SONT la voix (`sampleMix = 1.0`), donc trois défauts

| Défaut | Cause | Correctif | Mesure |
|---|---|---|---|
| Bend / vibrato / scoop / fall **muets** | calculés dans la boucle, appliqués à la synthèse ; le mixage **remplace** toute la somme | `FxSaxSampleReader::setPitchScale()` poussé 1x/bloc | bend **+101,7 ¢** / **+200,1 ¢** (attendu 100/200) |
| **Pas de clic d'anche** | le clic était **sommé avant** le mix → jeté | déplacé **après** le mixage | attaque/sustain **0,08x → 0,49x** |
| **Attaque molle (127 ms)** | la région joue depuis la frame 0, mais l'enregistrement monte lentement (8 % du RMS sustain dans les 5 premières ms) | démarrage sur un **onset** trouvé 1x par région (45 % du RMS), borné au loopStart | niveau en **5 ms** |

Vibrato monotone : **15,2 ¢ (0,0) → 31,2 ¢ (0,3) → 87,9 ¢ (0,8)**. Relâchement du lecteur
ralenti (`3,5e-4 → 1,6e-4`) pour éviter le clic quand les samples portent la voix seule.

### Piège de mesure (a coûté 3 passes) — mesurer une hauteur DANS une note de sax

- Pic de **Goertzel** sur 25 ms : résolution ~40 Hz = **250 cents**, plus grossier que le
  vibrato mesuré → annonçait 53 ¢ de « vibrato » à profondeur 0.
- **Passages par zéro** sur un signal peu filtré : comptent l'**harmonique 2**, forte au sax
  → lisaient exactement **le double** de la fréquence.
- Méthode retenue : **autocorrélation** sur un signal filtré à **1,3·f0** (3 pôles, 18 dB/oct),
  interpolation parabolique du pic. Bend retrouvé à 1,7 ¢ près.

### Banc conservé : `tools/aetherfx/sax_voice_check.cpp`

`g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/svc tools/aetherfx/sax_voice_check.cpp`
puis `./svc app/src/main/assets/saxpack/saxpack_alt.bin` → **TOUT PASSE (0 échec)**.
C'est le seul moyen de valider la voix sax sans appareil.

### Page de recette (légère)

`/workspace/dl/pub/index.html` servi sur **port 12000** : 13 JPEG (~600 Ko) + 3 MP3.
**Piège** : cette page contenait déjà les médias de la session précédente — ne pas écraser
`index.html` sans reprendre les fichiers existants (`ls *.jpg *.mp3`).

## Session 2026-10-29 (suite) — navigateur de presets a 3 volets, humanize du sax, ecrêtage

Trois chantiers menes d'affilee. Conversation economique (pas de `conv_sync` repete,
medias en JPEG/MP3 leger). HEAD de depart : `fc64eaf`.

### 1. Navigateur de presets : troisieme forme, inspiree des plug-ins

L'utilisateur a demande « un autre style d'affichage » (recherche web indisponible : quota
Tavily epuise). La liste etait deja passee d'un empilement de barres pleine largeur (8
visibles) a un index 2 colonnes (~30 visibles). Les deux ont ete **remplaces** par un
navigateur a **3 volets**, la forme des navigateurs Arturia Analog Lab / Serum :

| Volet | Contenu |
|---|---|
| rail gauche (214 u) | « All » + chaque categorie avec son **compte** ; selection = pastille remplie + barre d'accent |
| liste droite | les presets **filtres**, **numerotes**, categorie affichee quand le filtre est « All » |
| entete | « N of M » sous le titre — c'est ce qui fait lire le filtre comme un filtre |

`var cat by remember(s.instrument) { mutableStateOf<String?>(null) }` : `null` = All. La
categorie est stockee par **nom**, pas par index, pour survivre a la reconstruction de la
liste quand un preset utilisateur est sauve ou supprime.

**Piege du test de capture** : l'ancien test scrollait jusqu'en bas (12 `swipeUp`) pour
montrer la categorie User — la capture ne montrait donc plus le design. Il est scinde en
deux : `ae_leads_preset_list` (haut, sans scroll) et `ae_preset_user` (bas, entree User +
bouton supprimer).

### 2. Sax : « avec faconneur sonne moins bon que le libre sans faconneur »

Le pack **n'etait pas desaccorde** (mesure sur 22 notes : ecart qui **change de signe**,
signature du suivi de formants aleatoire par note, pas d'un biais). Le vrai defaut etait
**l'ecrêtage**, et il venait de la **reverb**, pas du pack :

| Mesure (Level 1.0, note 60) | pack | synthese |
|---|---|---|
| crete **reverb coupee** | 0,908 | 0,920 |
| crete **reverb active** | **1,294** | 0,832 |
| echantillons > 0,95 | 8,4 % | 0 % |

La reverb ajoutait **+43 %** de crete. Le limiteur du mixer ecrasait donc chaque crete de
sustain de la voix echantillonnee — c'est cette distorsion, plus que le timbre, que
l'utilisateur entendait.

**Correctif** : ecrêtage doux (genou a 0,75, `tanh`) **sur la somme, apres la reverb**.
Place **dans la boucle** il ne faisait rien (la reverb est ajoutee ensuite) — c'est le
piege. Resultat : crete **1,294 -> 0,994**, synthese **intacte** (0,832, elle ne depasse
jamais 0,75 en sec). 0 echantillon ecrêtait des deux cotes.

Le gain de sortie avait ete monte a 1,30 sur une mesure prise avec un autre preset :
mesure sur 6 combinaisons instrument/anche, pack et synthese sortent au **meme RMS a 25 %
pres** (0,99 / 1,04 / 0,99 / 1,20 / 0,91 / 1,25). Gain remis a **1,0** (mesure), pas 1,30.

### 3. Humanize (22e parametre du sax)

`fx_sax.h` : `paramCount()` 21 -> **22**, index 21 = Humanize, applique a l'attaque
(`humAtk_`) et a la profondeur de vibrato (`humVib_`). Mesure : etalement de hauteur
**10,3 cents a 0 / 27,1 cents a 1** sur 14 repetitions. UI : slider `AeIntensitySlider`
sous OUTPUT (pas dans la grille d'articulation : c'est le modele de joueur, pas un reglage
d'articulation). Les 23 presets sax sont passes de 21 a 22 valeurs (`padded` dans
`AeCore` gere les anciens fichiers, defaut 0,5).

### Banc `tools/aetherfx/sax_voice_check.cpp` — 7 sections, 0 echec

Ajoute : legato (le slur **deplace** la voix echantillonnee), humanize, accord.
**Trois pieges de mesure** corriges dans le banc lui-meme, a relire avant d'y toucher :

- **vibrato** : la loi est **lineaire** (`p(12) * 38` cents). A 0,3 elle ne fait que
  11 cents, sous le plancher de mesure (~12 cents) — le banc signalait « pas de
  changement » pour un vibrato qui marchait. Utiliser 0 / 0,5 / 1,0.
- **legato** : a `legato = 0` le moteur **rearticule** (nouvel echantillon + attaque +
  glide). Mesurer a 0,5 s tombait dans l'attaque ; mesurer a 0,72 s.
- **accord** : une fenetre de 0,12 s ne resout que **22 cents** a 659 Hz — le banc
  mesurait sa propre resolution. Fenetre 0,25 s, seuil 40 cents (le suivi de formants
  aleatoire par note deplace le pic de tens de quelques dizaines de cents).

Le seuil de continuite (sauts > 0,25) reste a **0** sur les 7 notes testes.
