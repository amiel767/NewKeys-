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

**Ne PAS le lire en entier** (2,8 Mo : c'est ce qui gonfle une conversation). Il est
**desormais non suivi** et se regenere avec `python3 tools/gen_project_context.py`.
Pour retrouver un symbole, cibler :

```bash
grep -n -A 20 "class PianoEngine" PROJECT_CONTEXT.md   # un extrait, pas le fichier
```

`AGENTS.md` (ce fichier) reste la memoire des pieges et des decisions ;
`PROJECT_CONTEXT.md` n'est qu'une carte du code, a interroger par extraits.

## Session 2026-11-01 (Clone 5 / Économe 2) — « les touches n'atteignent jamais les Æther » : cause racine trouvee

### Le bug : `drainEvents()` etait APRES le retour anticipe `sfCount <= 0`

`SoundfontEngine::renderStereo()` faisait :

```cpp
int sfCount = fluid_synth_sfcount(mSynth);
if (sfCount <= 0) { fill 0; return; }   // <-- sort SANS drainer les evenements
drainEvents();
```

Or charger un instrument Æther **decharge le soundfont de la piste**
(`AeInstruments.onBeforeLoad` -> `unloadSoundFontFromSlot`). Des qu'il ne reste
plus **aucun** soundfont charge, `sfCount == 0` et `drainEvents()` n'est **plus
jamais appele** sur le chemin legacy : les NoteOn/NoteOff restent dans le ring.

Un seul bug, trois symptomes :

| Symptome rapporte | Mecanisme |
|---|---|
| « les touches n'atteignent jamais les Æther » | la note n'est jamais drainee vers `mSourceRouter` |
| notes bloquees | le NoteOff reste coince dans le ring, la voix ne s'arrete jamais |
| latence apparue « depuis je ne sais quelle modification » | le ring (2048) se remplit, deborde dans `mDropped` (256) puis **jette** ; le thread MIDI bloque sur le mutex de `pushEvent` |

**Correctif** : `drainEvents()` remonte **avant** le test `sfCount <= 0`. Les
appels `fluid_synth_*` sont sans effet quand il n'y a pas de soundfont, donc
drainer tot est sur. Le chemin FX (`renderChannels`) n'etait pas concerne : il
appelle deja `drainEvents()` en tete.

**Regle** : ne jamais placer un retour anticipe devant `drainEvents()`. Le drain
est la seule sortie des notes hors du ring ; le sauter fige **toutes** les
sources, pas seulement le canal concerne.

### Piege de verification (a refaire a l'identique)

`tools/aetherfx/midi_route_check.cpp` (nouveau) teste le routage **hors
Android** : `g++ -O2 -std=c++17 -I app/src/main/cpp`. Il a montre que le routage
lui-meme est sain (Organ/Synth/Leads/Sax : `paused=1 active=1`, peak 0,11..0,31 ;
Piano peak 0 a vide, normal sans banque chargee). **Le routage n'etait donc pas
le bug** — c'est ce qui a oriente vers le drain. Un banc qui valide la brique
isolee fait gagner une passe : ne pas conclure du symptome, mesurer la brique.

### Veille des pairs : RETIREE (2026-11-01)

Decision utilisateur : **ne plus fouiller les autres conversations**. Les anciennes
ne sont plus a jour ni actives (la cible `9c93aa40` « Clone 3 : économe » est
`PAUSED` depuis le 31/10 09:03, dernier commit `58af014`, alors que le depot est a
`3e355dd`). `tools/conv_sync.py` / `conv_watch.py` restent sur disque pour un
usage ponctuel, mais **ne plus les lancer en boucle**. Le travail se fait
desormais uniquement ici, sur l'etat reel du depot.

## Mode ULTRA economique (Clone 5, 2026-10-31) — la conversation ne gonfle plus

La consommation de l utilisateur est le **flux de conversation renvoye a chaque
tour**, pas le reseau du sandbox.  Une sortie de terminal de 10 Ko est repayee a
chaque tour suivant.  La regle du 2026-10-29 disait deja de filtrer ; celle-ci
ajoute le mecanisme, parce que « filtrer a la main » s oublie au bout de 30 tours.

**1. Ne jamais ecrire un script par heredoc.** Un heredoc renvoie tout le script
dans la sortie, a chaque appel.  Les scripts d analyse vivent sur disque
(`tools/audit/*.cpp`, `/tmp/*.py`) et on ne les relit que si besoin.

**2. Toute commande produit au plus 10 lignes.** Motif impose :
`cmd > /tmp/x.log 2>&1; tail -3 /tmp/x.log`.  Jamais `ls -la`, jamais un
`grep` sans `| head -N`, jamais un tableau complet.

**3. Les mesures s ecrivent sur disque et se lisent par agregat.** Un banc qui
sort 200 lignes ecrit `/tmp/mesure.txt` et n affiche que la ligne de synthese.
Le terminal n est pas un rapport, c est un canal de controle.

**4. Le veilleur (`tools/conv_sync.py`) est le seul gros lecteur tolere**, et il
imprime deja des agregats.  Il ne faut PAS le lancer en boucle : une fois avant,
une fois apres une passe de travail.  `--full` rejoue 9000+ evenements, donc
quasi jamais.

**5. Les builds longs en arriere-plan** (`nohup ... &`), resultat lu par
`tail -3`.  Un build de premier plan au-dela de 1080 s est coupe.

**Outils disponibles** : `tools/conv_peek.py <CONV_ID> --last` imprime
**uniquement le dernier message** d une conversation (et non sa derniere action) ;
le flux brut part dans `tools/.conv_peek/<id>.json` (gitignore).  C est l outil
pour « redonne-moi son dernier message » sans tirer 5 Mo dans le terminal.

## Mode economique de conversation (regle, 2026-10-29)

La consommation de l utilisateur = le flux de conversation renvoye a chaque tour,
PAS le reseau du sandbox. Une sortie de terminal de 10 Ko coute a chaque tour
suivant. Regles :

- Ecrire les scripts avec l editeur de fichiers, jamais par heredoc : un heredoc
  renvoie tout le script dans la sortie.
- Toujours filtrer : rediriger vers un log et ne montrer que `tail -3`, ou
  `| tail -5`. Jamais de listing complet ni de tableau de 60 lignes.
- Une commande par etape, pas trois. Pas de `ls -la` repete.
- Les gros artefacts (JSON de mesure, packs) s ecrivent sur disque et se
  consultent par agregats, pas en entier dans le terminal.

## Mode ULTRA economique — Clone 5 (regle, 2026-10-31)

Cette conversation est la **suite** de `9c93aa402a634cdfa4a1fbf759935777`
(« Clone 5 »), qui avait un flux de plusieurs Mo par tour. La cause n'etait pas
l'agent mais **le dump des observations dans la conversation** : chaque `grep -rn`
sur 117 fichiers Kotlin, chaque `sed -n`, chaque `ls` complet repart ensuite dans
le contexte a chaque tour.

### Regles de sortie (non negociables ici)

1. **Jamais de dump brut.** `grep`/`sed`/`ls`/`find` sont **toujours** pipes :
   `| head -n` / `| tail -n`, ou rediriges vers un fichier puis `tail -3`.
2. **Les gros artefacts restent sur disque.** Mesures, JSON, arbres : ecrits dans
   un fichier, consultes par agregats. Le terminal ne montre que l'agregat.
3. **Pas de `cat` d'un fichier de code.** Pour lire un fichier, l'editeur
   (`view` avec `view_range`) qui ne renvoie que la tranche demandee.
4. **Une commande par etape**, pas trois enchainees avec `;` : chaque sortie
   s'ajoute au contexte.
5. **Aucun heredoc** : il renvoie tout le script dans la sortie.
6. **Veille des pairs sans dump** : `tools/conv_watch.py <CID>` (1 a 6 lignes par
   poll) au lieu de `conv_sync.py --full` (transcript entier). Le dump brut va
   dans `tools/.conv_peek/<CID>.json` (gitignore), lisible par
   `tools/conv_analyse.py <CID> --tail N` / `--lastmsg` / `--grep PAT`.

### Outillage de veille (nouveau, Clone 5)

| Script | Role |
|---|---|
| `tools/conv_peek.py <CID>` | telecharge le flux brut sur disque, n'imprime que des agregats |
| `tools/conv_analyse.py <CID>` | relit le dump **sans reseau** : types, `--tail`, `--lastmsg`, `--grep` |
| `tools/conv_watch.py <CID>` | poll incremental, n'imprime que les **nouveaux messages** (watermark `tools/.conv_peek/.watch_<CID>.json`) |

`tools/.conv_peek/` est gitignore : un dump de conversation longue fait ~90 Mo et
ne doit jamais entrer dans un commit.

### Environnement (re-provisionne, verifie 2026-10-31)

```bash
sudo apt-get install -y openjdk-21-jdk-headless unzip zip wget tesseract-ocr ffmpeg lame
python3 -m pip install pillow numpy
# JDK: /usr/lib/jvm/java-21-openjdk-amd64 (le chemin /workspace/jdk/jdk21 n'existe plus)
# SDK: /workspace/android-sdk (platforms;android-36, build-tools;36.0.0,
#      ndk;26.1.10909125, cmake;3.22.1) + local.properties: sdk.dir=/workspace/android-sdk
# Keystore: ./debug.keystore (gitignore, a regenerer par keytool)
```

`:app:compileDebugKotlin --no-daemon --max-workers=2` -> **BUILD SUCCESSFUL**
(3m17). Piege : un `apt-get` lance en arriere-plan garde le verrou dpkg et bloque
toutes les installations suivantes — le tuer puis `dpkg --configure -a`.


## Pieges moteur piano (verifies, a relire AVANT de toucher fx_piano.h)

- **Cache de regions : le hit doit aller vers la QUEUE.** `decoded()` lit
  `&cache_.back()`, donc `rotate(begin, begin+i, begin+i+1)` renvoie la MAUVAISE
  region : toute note rejouee sortait transposee (A4 a +307 cents). La bonne forme
  est `rotate(begin+i, begin+i+1, end)`. Le bug ne se voit **que** si l'on rejoue
  des notes : un banc qui joue une note par preset ne sollicite jamais le cache.
- **`paramCount` Kotlin doit egaler `kParams` natif.** Un ecart tronque
  silencieusement les derniers indices (`padded()`), donc des controles entiers
  n'atteignent jamais le moteur. PIANO est passe de 16 a 17 (TOUCH).
- **Le marteau doit etre filtre, pas blanc.** Du bruit blanc pur donne un « tsss »
  metallique. Filtrer dans la bande de resonance du marteau (1,4-4 kHz), et
  **compenser le niveau** : le filtrage retire ~7,7 dB.
- **Mesurer attaque et sustain SEPAREMENT.** Le bruit blanc du marteau domine un
  centroide mesure sur la phrase entiere et fait croire a une regression de timbre
  qui n'existe pas. Sur 0-100 ms le timbre etait identique.
- **Une fenetre de queue dominee par la decroissance de la note** ne distingue pas
  « reverb plus courte » de « reverb plus faible ». Isoler la reverb sur une
  impulsion pour verifier qu'elle repond.
- **Un detecteur de hauteur par produit harmonique se trompe d'octave** (il
  s'effondre des qu'un harmonique manque). Somme normalisee + departage vers la
  plus haute frequence a score egal. Et **le banc doit se tester sur des sinus**
  avant de juger le moteur.

## Piano : reglages, packs, mesure (regles durables)

- **`PianoStudio::set()` a un garde PAR FAMILLE de reglages.** Les regrouper sous
  celui du micro etait un bug mesure : sur un moteur **vivant**, bouger DRIVE,
  PUNCH, TONE, PRESENCE, SPACE ou WIDTH ne faisait **rien** tant que le micro ne
  bougeait pas aussi (DRIVE 0 -> 1 restait a 0,0140 au lieu de passer de 0,0114 a
  0,0181). Un moteur **neuf** entre toujours dans la branche (`cPos_ = -1`) : un
  banc a une seule passe **masque** le bug. Toujours tester les deux chemins.
- **Un pack se normalise PAR COUCHE, pas par region.** Une cible RMS unique pour
  toutes les regions ecrase la dynamique : toutes les couches sortent au meme
  niveau et le piano n'a plus de nuance. La cible doit dependre de la couche
  (viser le rapport du pack acoustique, ~19 dB de la couche faible a la forte).
- **Le `volume=` du SFZ doit etre applique.** L'ignorer laisse passer les niveaux
  bruts des enregistrements. Mais il ne corrige pas tout : sur la banque
  Wurlitzer la dynamique de la **source** est **inversee** (mesure d'attaque : pp
  0,51 contre ff 0,19) et les volumes SFZ sont quasi egaux (-4,87 / -5,25 dB).
  La dynamique doit etre retablie dans le build.
- **Decoder un pack ADPCM : un predicteur PAR CANAL, nibbles par paires.** Les
  nibbles sont (pair = gauche, impair = droite) *dans l'octet*, pas alternes par
  echantillon. Un decodeur a predicteur unique sort un sinus a 0,035 RMS a 0,85 :
  **valider tout decodeur sur un sinus d'amplitude connue avant de mesurer un
  pack**. Trois mesures ont ete faussees par ce seul bug.
- **« Ca sature » ne se diagnostique pas a l'oeil.** Sur Felt : aucun ecretage
  (crete 0,22), aucune discontinuite (saut/RMS 0,58, le plus bas des 9), HNR le
  **meilleur** (16,3 dB), queue la plus propre. Le moteur est **lineaire** (la
  serie harmonique decroit sans harmoniques ajoutees). Mesurer avant de corriger.

## Historique des sessions (index)
Le detail est dans `docs/archive/AGENTS-history.md` (**non charge automatiquement**).
Ne lire que ce dont on a besoin :
```bash
grep -n -A 40 "cache de regions" docs/archive/AGENTS-history.md
```

- Session 2026-10-24 — nettoyage assets + integration Æther Instruments (discussion)
- Session 2026-10-26 — Æther Instruments integres (faits, commits 6ae6d92 → 0f8ebbe)
- Session 2026-10-26 — Æther Leads + Sax (zip final + ajustements DeepSeek)
- Session 2026-10-26 (parallele) — Æther : piste, fenetre, textes, perf
- Session 2026-10-26 — setlist/patch vierge + verre depoli (3 demandes)
- Session 2026-10-24 — pitch bend Æther, bonnet de fader Leads, UI MIDI
- Session 2026-10-27 — Sheets : cohérence visuelle, icônes, double-tap flottant
- Session 2026-10-27 — Æther Sax : banque echantillonnee + table harmonique mesuree
- Session 2026-10-27 (suite) — presets Æther v2 + anti-tac des leads
- Session 2026-10-27 (sync parallele) — Sheets flottant + Æther Lot B
- Session 2026-10-27 (suite) — fenetre Æther immediate, verre, presets Organ, caches
- Session 2026-10-28 — assets UI Pianos/Strings : dimensionner sur l'affichage (mesure)
- Session 2026-10-28 (suite) — previews separes + animations adoucies
- Session 2026-10-28 (2e) — synchro Clone 2 : merge + veille multi-pairs
- Session 2026-10-29 — Æther Piano (instrument #17, 9 presets)
- Session 2026-10-29 — UI Æther (faders, segments, power, nommage), snapshots, sax samples-only
- Session 2026-10-28 (3e) — Æther Piano : samples legers + boost (mesure)
- Session 2026-10-29 — Æther : presets utilisateur, Intensity, liste par categorie, pitch bend
- Session 2026-10-29 (suite) — UI Æther resserrée + voix échantillonnée du sax façonnée
- Session 2026-10-29 (suite) — navigateur de presets a 3 volets, humanize du sax, ecrêtage
- Session 2026-10-29 — sax Æther : knob Blend (parametre 22)
- Session 2026-10-30 — pack piano : trois bugs silencieux + demo refrain
- Session 2026-10-30 (suite) — le piano « son de jouet » : causes mesurees
- Session 2026-10-29 — piano : le son « jouet », causes mesurees
- Session 2026-10-29 (2e) — piano : la « guitare », la tenue et le continu
- Session 2026-10-29 (3e) — « toujours du solo de guitare, tous identiques » : le moteur ignorait les controles
- Session 2026-10-29 — Piano : la melodie etait juste, les presets non
- Session 2026-10-29 (suite) — Piano : etage studio, marteau, vraie banque electrique
- Session 2026-10-31 — carte piano, textes lisibles, vrai electrique (cause racine)
- Session 2026-10-31 — pack piano dense (Salamander) + sonie egalisee
- Session 2026-10-31 — fill de blues + BUG de cache : les notes repetees jouaient le mauvais echantillon
- Session 2026-10-31 (suite) — marteau, bruit de touche, reverb : trois causes mesurees
- Session 2026-10-31 (2e) — electrique : pack normalise par couche + garde de `set()` ; Felt raffine

## Session 2026-10-31 — pourquoi les 6 defauts ont ete « vus » apres correction

**La cause n'etait pas le code : l'APK teste ne contenait aucun correctif.**
Verifie par marqueurs entre `origin/main` et le commit local :

| Marqueur | `origin/main` (b717038, teste) | commit local |
|---|---|---|
| `x *= p(20) * 0.5f` (sax) | **absent** (encore `* 1.0f`) | present |
| `noteOwnedBySource_` (notes bloquees) | **0 occurrence** | 4 |
| cache piano en `shared_ptr` | **0** | present |

Le commit des correctifs n'etait **pas pousse** : l'appareil a donc teste la version
d'avant, ou le Blend sax valait encore 0,5, ou `soundfontPaused` etait relu a chaque
evenement (notes bloquees) et ou le cache de regions du piano etait un `vector`.
**Toujours verifier `git merge-base --is-ancestor <commit> origin/main` avant de
conclure qu'un correctif ne marche pas.**

### Le lien SoundFont (167 Mo) — mesure, et pourquoi il ne sert pas

`S90 FULL GRAND` (JSF, Polyphone, EMU8000) : 205 echantillons, 167 Mo de PCM,
1 seul preset, **aucune boucle** (`loopEnd == loopStart`), 10,5 s par note.

| Echantillon | centroide | energie > 5 kHz |
|---|---|---|
| #0 (grave) | 229 Hz | 0,06 % |
| #60 | 421 Hz | 0,18 % |
| #120 | 452 Hz | 0,16 % |
| #200 (aigu) | 1334 Hz | 4,42 % |

Deux raisons de ne pas l'utiliser, dans cet ordre :

1. **Aucune licence.** Le fichier n'a ni `ICOP` ni `ICMT` de copyright — seulement
   « by JSF ». Meme classe de probleme que les fichiers Telegram : impossible a
   embarquer.
2. **Pas meilleur.** Centroide 229-1334 Hz et presque rien au-dessus de 5 kHz :
   c'est exactement le profil de la banque deja utilisee (SplendidGrandPiano,
   centroide 502 Hz). 167 Mo pour aucun gain de timbre, et **sans boucles** — donc
   le meme probleme que Salamander (714 Mo, aucun `smpl`).

**Regle** : une banque lourde n'est pas une meilleure banque. Mesurer le centroide et
la fraction d'energie > 5 kHz **avant** de parler de taille. Le levier reel reste la
densite de notes racines, pas le poids.

## Session 2026-10-31 — UI Æther Piano (verre, disquette, en-tete)

- **`AeGlassPanel`** (`AeLightControls.kt`) remplace `AeGlassCard` (supprime) : le
  panneau floute la **photo de fond** au lieu d'un aplat opaque choisi d'apres l'encre.
  Le flou est un sous-echantillonnage 1/18 redessine en bilineaire, **pas** un
  RenderEffect : l'API 31 est requise, et un flou en modificateur frere flouterait
  aussi la fenetre posee dessus. La photo est mappee en cover-fit **puis translatee**
  de l'offset du panneau, sinon le flou montre une copie entiere compressee.
- **Case d'affichage du preset retiree** de l'en-tete (le nom vit deja dans la
  fenetre et le navigateur) ; ce qui reste est le bouton d'ouverture du navigateur.
- **Disquette conditionnelle** : `AeManager.isModified(strip)` compare les valeurs au
  preset selectionne (`false` pendant le glissement de 420 ms, sinon clignotement).
  Elle n'apparait que si un reglage a bouge. Agrandie 30x32 -> 34x34 + relief.
- **Sax, attaque** : le pack avait une attaque **plus molle** que la synthese qu'il
  remplace (0,39x contre 0,43x d'attaque/sustain) — un echantillon sans morsure
  d'anche se lit « synthetique ». Morsure portee de 35 ms/+0,9 a **30 ms/+2,6**.
  Resultat : 0,48 / 0,82 / 0,99 / 1,45 (alto/sop/ten/baryton), crete max 0,775,
  **0 ecrete**.
- **Echecs de banc PRÉEXISTANTS** (defauts du pack, pas des regressions) : soprano
  +193,9 cents (note 57), tenor +91,9 cents (note 72), humanize tenor 38 cents a 0,
  continuite baryton 7846 sauts. Verifier contre l'ancien binaire avant de conclure.

## Session 2026-10-31 (Clone 5 / Économe 2) — D1 sax, D3 notes bloquées, file MIDI

Reprise de la conversation `9c93aa402a634cdfa4a1fbf759935777` (dont le travail
est déjà un ancêtre de HEAD, rien à rattraper). **Règle de conversation** :
tout message est une dépense pour l'utilisateur ; sortie filtrée, une commande
par étape, pas de listing complet.

### D1 — « les samples du sax sont passés où ? »
Les samples **étaient** chargés et mixés : mettre le Blend à 0 change **96 à
144 %** du signal, sur les 4 banques (mesure `FxMixer`, blend 0 vs 1).
Le vrai défaut était la **valeur par défaut des presets enregistrés avant
l'ajout du paramètre** : `padded` (`AeCore.kt`) complétait les indices manquants
à **0,5**, ce qui coupait le pack de moitié — un blend de 0,5 sur 23 paramètres.
Corrigé : un indice ajouté en fin de contrat prend la valeur du **premier preset
d'usine** (Blend = 1,0). Règle : ne jamais compléter un contrat à 0,5 en
supposant un neutre ; lire la valeur de référence de l'instrument.

### D3 — notes bloquées : le moteur est innocent, trois causes dans l'app
1. **Le banc mesurait la réverbération, pas une voix.** Avec tous les paramètres
   à 0, les 5 instruments tombent à **exactement 0,00000** après `noteOff` :
   aucune voix ne survit. La queue LEADS/SAX à 1,5 s (0,0055 / 0,0196) est une
   queue de reverb, comportement voulu. **Métrique correcte** : voix seule vs
   avec effets. Ne pas comparer au niveau tenu avec un seuil arbitraire.
2. **Le clavier de la fenêtre ne relâchait pas ses notes** quand le geste est
   annulé (geste système, perte du pointeur) ou quand la fenêtre se ferme pendant
   un appui : `awaitPointerEventScope` **lève**, et le `!ch.pressed` n'arrive
   jamais. `PointerEventType.Cancel` **n'existe pas** dans cette version de
   Compose — utiliser `try { awaitPointerEventScope { ... } } finally { relâcher }`
   + un `DisposableEffect` sur le strip.
3. **Un NoteOff perdu quand la file d'événements est pleine** : `push()` renvoie
   false et l'événement disparaît — une note sans fin. Les événements refusés sont
   gardés (borné à 256) et réémis par le drain (`try_lock`, le thread audio ne
   bloque jamais) et par le tick de contrôle.
   **Piège de concurrence** : réémettre depuis le drain fait du thread audio un
   **second producteur** sur un anneau **SPSC**. `pushEvent` tient le verrou
   **sur le push** (pas seulement sur la liste d'attente) et `resendDroppedEvents`
   prend le même : le ring n'a jamais deux écrivains.

### Bancs conservés
`tools/aetherfx/stuck_notes_check.cpp` (D3, voix vs queue d'effet),
`tools/saxblend.cpp` → `/tmp/saxblend` (D1, contribution du pack).
Compilation hôte : `g++ -O2 -std=c++17 -I app/src/main/cpp` (aetherfx est
header-only). **Piège d'API** : `FxChain` exige un argument de constructeur et
n'expose pas `setInstrument` ; utiliser `FxMixer` (`prepare`, `setInstrument`,
`noteOn`, `processStrip`, `loadInstrumentBank`).

## Session 2026-10-31 (Clone 5 / Économe 2, suite) — etat fige, banque reelle

### La banque du pack est S90 Full Grand, PAS SplendidGrandPiano
Correction d'une erreur que j'ai ecrite plus haut dans ce fichier.  Le
`piano.bin` livre vient de **S90 Full Grand** (SF2 de JSF, 167 Mo, 205
echantillons, `isng` EMU8000) : structure reprise = deux prises par couche (pan
gauche/droite), **7 plages de velocite**, hauteur des racines ; reconstruit par
`extract_sf2.py` + `build_sf2.py`.  Mesure : **centroide 1 995 Hz a C4** contre
1 276 Hz pour SplendidGrandPiano, qui n'est plus que l'ancienne source (gardee
pour reference dans `ATTRIBUTION.md`).

Ne plus attribuer le timbre du piano a SplendidGrandPiano : c'est la banque
precedente.  Toute conclusion de timbre doit citer S90 Full Grand.

### Etat des packs (verifie)
| Fichier | Taille | Source |
|---|---|---|
| `pianopack/piano.bin` | 9 Mo | S90 Full Grand |
| `pianopack/electric.bin` | 1 Mo | GregSullivan Wurlitzer EP200 |
| `pianopack/ep_ballad/dx/piano/tine.bin` | 2 Mo chacun | SF2 E.Piano, 26 regions, `build_sf2.py --preset` |

### Les 6 defauts sont tous traites (D1-D6 + extras)
D1 sax (defaut de contrat `padded`), D2 piano accord (pre-decodage hors thread
audio), D3 notes bloquees (3 causes, voir section precedente), D4 carte piano
(grille 3+2 + `Spacer`), D5 boutons PED retires, D6 textes (fond choisi a
l'oppose de l'encre mesuree).  Extras : renommages, 4 banques EP, liste
defilante `AeModelList`.

### Ce qui reste ouvert, et qui n'est PAS du code
- **Le piano sonne « jouet »** (dernier retour utilisateur) : c'est un probleme
  de **banque**, pas de reglage.  Un filtre ne transforme pas un Steinway en un
  autre instrument.  Le seul levier reel est la **densite** (42 notes racines au
  lieu de 21), ou une autre banque sous licence propre.
- **Validation appareil** impossible ici (pas d'emulateur) : son reel, mute sur
  piste AEther, fontScale 1,3x.

### Lecon de methode (elle a coute du temps)
La numerotation D1-D6 vient d'un **resume tronque**, pas d'un message : elle
n'existe dans AUCUNE conversation (verifie sur CLONE5, la source
`ffdcf16d...`, CLONE2).  Ne pas partir en archeologie pour retrouver un brief :
demander la liste, ou lire le tracker de taches, et s'arreter vite.

### Mode economique (regle de conversation)
La consommation de l'utilisateur = le flux renvoye, pas le reseau du sandbox.
Sortie filtree (`tail`), une commande par etape, scripts par l'editeur de
fichiers (jamais en heredoc : le script entier revient), mesures sur disque
consultees par agregat.

## Session 2026-11-01 (Clone 5, econome) — MIDI Æther retrouve + latence buffer

### D3/D1 — les pistes Æther ne recevaient plus le MIDI (cause racine, verifiee)

`MixerViewModel.getActivePerformanceChannels` (~l.2508) ne routait une piste que si
`slot.soundFontId > 0`. Or charger un instrument Æther **decharge le soundfont** de la
piste (`AeInstruments.onBeforeLoad` -> `unloadSoundFontFromSlot` -> `soundFontId = -1`).
La piste Æther etait donc **exclue du routage** : plus aucune note ne l'atteignait, ni
clavier virtuel ni MIDI USB. En USB, `incomingChannel != null` et la liste retombait sur
`emptyList()` : silence total. C'est exactement « les Æther qui marchaient ne sont plus
atteints par MIDI ».

Correctif : une piste est jouable si elle porte un soundfont **ou** un instrument Æther
(`AeInstruments.isLoaded(idx)`). Commit `46bc7d3`.

**Regle** : toute condition de routage qui teste `soundFontId > 0` exclut les pistes Æther.
Il y a 2 sites (`:2376` = re-selection du program soundfont, legitime ; `:2517` = routage,
corrige). Verifier les deux avant de conclure.

### Latence — le buffer n'etait pas reapplique a la reconnexion

`safeStartEngine` rouvre le stream, ce qui remet le buffer natif a son defaut (**512**
frames) et non la valeur choisie (**256** cote app). `reconnectAudioStream` et sa variante
debouncee ne reappliquaient que les parametres DSP, jamais `setBufferSize` : chaque
reconnexion (casque, changement de peripherique, perte de focus) **doublait la latence de
jeu** jusqu'au redemarrage. `AudioEngine` retient desormais `configuredBufferSize` /
`configuredPolyphony` et `restoreAllEngineParameters` les reapplique. Commit `6592074`.

**Regle** : `safeStartEngine` remet le stream a ses defauts natifs. Tout reglage de stream
doit etre reapplique dans `restoreAllEngineParameters`, sinon il ne survit pas a une
reconnexion.

### Outil de veille economique : `tools/eco.py`

Remplace l'usage de `conv_peek --dump` (jusqu'a **92 Mo**) et de `conv_sync.py` qui
renvoient le flux entier. L'outil garde l'etat sur disque (`tools/.eco/`, gitignore),
ne regarde que les nouveaux evenements, resume chaque action en une ligne (jamais son
corps) et **borne la sortie en octets** (`--budget`, defaut 1500).

    python3 tools/eco.py <cid> --state     # une ligne
    python3 tools/eco.py <cid> --last      # dernier message de l'agent
    python3 tools/eco.py <cid>             # nouveautes seules, budgetees

Il lit aussi un dump local `tools/.conv_peek/<cid>.json` s'il existe, ce qui evite tout
reseau (le flux de 9128 evenements declenche un HTTP 429 en ligne). Le dump reste sur
disque : il n'entre jamais dans la sortie.

**Pieges de structure d'evenement (verifies)** : le texte d'un `MessageEvent` est sous
`llm_message`, pas `value` ; une action est sous `action` ; une observation sous
`observation`. Chercher dans `value` renvoie du vide.
