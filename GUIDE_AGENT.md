# GUIDE AGENT — Æther Keys / SoundStage Mixer

> **A l'agent qui ouvre ce depot :** lis ce fichier en premier, puis
> `AGENTS.md` (memoire des pieges et des decisions) et, pour le code,
> `PROJECT_CONTEXT.md` (tous les fichiers actifs inline).
> `PROTOCOLE_AGENT_PRECISION.md` cadre la methode ; ce guide-ci explique **ou**
> sont les choses et **comment** les manipuler sans casser.

---

## 1. En une minute

Application Android (Kotlin + Compose + audio natif C++/FluidSynth) : lecteur de
soundfonts live, mixage multipiste, FX Aether, clavier virtuel, drum pads,
setlists.

- Module Gradle : `:app`
- `compileSdk 36`, `minSdk 26`, `targetSdk` aligne, Kotlin 2.2.10, AGP 9.1.1
- Build natif : CMake + NDK pour **4 ABI** (`aetherfx/`, FluidSynth vendore)
- Deux namespaces coexistent (piege classique) :

| Namespace | Role |
|---|---|
| `com.soundstage.mixer` | L'app principale (mixer, drumpad, settings, fx) |
| `com.aetherkeys.sheets` / `com.aetherkeys.setlists` / `com.aetherkeys.fx` | Modules separes |

`SettingsScreen`, `SetlistsScreen`, `SlSegmented` existent en **double** dans les
deux namespaces : toujours donner le chemin complet.

---

## 2. Mettre l'environnement en place (sandbox effacee a chaque session)

```bash
nohup /workspace/restore_env.sh >/dev/null 2>&1 &   # idempotent, ~5-10 min
tail -f /workspace/tools_logs/restore_env.log
```

Il installe : JDK 21 (Temurin), `cmdline-tools`, `platforms;android-36`,
`build-tools;36.0.0`, `platform-tools`, `cmake;3.22.1`, `ndk;27.0.12077973`,
Pillow/numpy/scipy, et ecrit `local.properties`. Il **detecte** le JDK present
(ne pas coder `/workspace/jdk/jdk21` en dur : ce chemin n'existe pas toujours).

Apres un reset, regenerer aussi le keystore (gitignore) :

```bash
JAVA_HOME=/workspace/jdk/jdk-21 /workspace/jdk/jdk-21/bin/keytool -genkeypair \
  -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

---

## 3. Compiler

```bash
/workspace/build.sh :app:compileDebugKotlin   # ~3 min a froid
/workspace/build.sh :app:assembleDebug        # APK ~42 Mo
```

`build.sh` exporte `JAVA_HOME`/`ANDROID_HOME` et borne la memoire
(`--no-daemon --max-workers=2`). **Ne jamais lancer deux builds Gradle en
parallele** : deux daemons font tomber la sandbox (verifier
`pgrep -f gradle` avant).

Succes attendu : `BUILD SUCCESSFUL`. Seul warning connu : `quadraticBezierTo`
deprecie (`ui/fx/FxInstrumentScenes.kt`, `ui/components/v2/MixerIconsV2.kt`).
**Tout autre message d'erreur est une regression.**

---

## 4. Captures d'ecran (Roborazzi + Robolectric, pas d'emulateur)

```bash
/workspace/build.sh :app:recordRoborazziDebug --tests "com.soundstage.mixer.FxWindowCaptureTest"
```

- Il **faut** la tache `recordRoborazziDebug` : `testDebugUnitTest` seul n'ecrit
  **aucun PNG** (le test passe quand meme, silencieusement).
- **Toujours** passer `--tests` : `LiveUiLoop.run()` est une boucle infinie pilotee
  par un processus externe (`cmd.json`) et bloque la suite.
- Qualifier paysage obligatoire :
  `@Config(qualifiers = "w851dp-h393dp-440dpi", sdk = [36])`.
  `RobolectricDeviceQualifiers.Pixel6` rend en **portrait** (trompeur).
- Un `MixerScreen` complet peut lever `AppNotIdleException` (animations infinies) :
  `mainClock.autoAdvance = false` puis `advanceTimeBy(...)` avant `captureRoboImage`.
- Sortie : `app/src/test/screenshots/`.

Tests Robolectric en JVM partagee : une exception non capturee d'un test est
**reattribuee au suivant** (`UncaughtExceptionsBeforeTest`). Lancer les tests
**un par un** ou par petits lots, jamais toute la suite.

---

## 5. Carte du code

Point d'entree : `MainActivity` -> `ui/MixerScreen.kt` (composeur central).
`PROJECT_CONTEXT.md` contient le source complet des 117 Kotlin actifs + 25 natifs.

| Domaine | Fichiers cles |
|---|---|
| Mixer V2 | `ui/MixerScreen.kt`, `ui/components/v2/*` |
| Etat / logique | `viewmodel/MixerViewModel.kt` (gros), `model/MixerState.kt` |
| Snapshots / patches | `model/SnapshotLogic.kt` (decisions pures), `model/SnapshotJson.kt` (codec JSON unique), `ui/components/v2/MixerBottomBarV2.kt` (cases + disquette) |
| Audio Kotlin | `model/AudioEngine.kt`, `audio/NativeAudioBridge.kt` |
| Audio natif | `cpp/audio_engine.{h,cpp}`, `cpp/soundfont_engine.{h,cpp}`, `cpp/native-lib.cpp` |
| FX Aether (moteur) | `cpp/aetherfx/*.h` (header-only, portable) |
| FX Aether (UI) | `com/aetherkeys/fx/ui/*` + `com/soundstage/mixer/ui/fx/*` |
| Instruments Aether (Organ/Synth) | `instruments/Ae*.kt` (UI+logique), hote : `instruments/AeInstrumentHost.kt` |
| Clavier virtuel | `ui/components/VirtualKeyboard.kt` |
| DrumPad / Looper | `ui/pages/DrumPadScreen.kt`, `ui/components/drumpad/*`, `audio/LooperEngine.kt` |
| LiveLooper (nouvelle version) | `looper/LiveLooperModel.kt`, `looper/LiveLooperUi.kt` (source de verite visuelle : `app/assets/LoopsPreview.jsx`) |
| Track Settings | `ui/tracksettings/TrackSettingsScreen.kt` |
| Settings | `ui/settings/SettingsScreen.kt`, `ui/settings/MidiMappingPage.kt` |
| Setlists | `com/aetherkeys/setlists/*` |

### Source de verite visuelle = le JSX, pas les briefs

`app/assets/*.jsx` (`MixerPreview`, `DrumPadPreview_v2`, `SoundfontsPreview`,
`SettingsPreview`, `SetlistsPreview`, `LoopsPreview`, `VirtualKeyboardPreview`,
`TrackSettingsPreview`, `SustainPedal`, `SettingsTabIcons`) sont la reference de
design. Plusieurs briefs ont contenu des specs **erronees** : partir du JSX.

Attention : le JSX dessine sur un canvas avec `scale()`. Ses `fontSize`/`height`
sont des **pixels de canvas**, pas des dp. Aucun facteur d'echelle officiel.

---

## 6. Pieges verifies (ne pas les re-decouvrir)

- **Ecran par defaut = `SETLISTS`**, pas `MIXER` : `MixerViewModel` restaure la
  page sauvegardee en asynchrone. Une commande de capture sans `page` montre
  Setlists.
- **`FxMixer` est atteint via `mFx`, prive** (`audio_engine.h:994`, sous le
  `private:` de la l.936) : toute nouvelle methode publique d'`AudioEngine` doit
  etre placee **au-dessus** de ce `private:`, sinon erreur « private member ».
- **`FxRegistry.info(id)` doit rester borne** (`all.getOrElse(id) { all[0] }`) :
  un id hors plage levait depuis un callback Compose et tuait la fenetre.
- **`Modifier.shadow` ne se rend pas** dans la capture Roborazzi, et se peint
  **au-dessus** du contenu. Utiliser `Modifier.raised(...)` (`ui/fx/FxSurface.kt`).
- **Un `DrawScope` travaille en pixels** ; les scenes FX sont en unites logiques.
  Sans `scale(density, density)` la scene ne remplit que le coin haut-gauche.
- **`animate { }` recoit `(value, velocity)`** : prendre la valeur, pas la vitesse.
  (C'est exactement le bug du fader corrige le 2026-10-24.)
- **Ne pas ajouter de `padding(bottom)` au clavier virtuel** : un espaceur le
  decale deja au-dessus de la bottom bar.
- **`Omni` (canal MIDI 0) existe volontairement** ; `-1` est la sentinelle OMNI de
  `SettingsDrawer`. Ne pas « corriger ».
- **Panic** : `triggerPanic()` doit vider **et** les queues d'effets Aether
  (`safeFxPanic()`) **et** les voix FluidSynth + la chaine master legacy, sinon
  ca continue de sonner.
- **Une surface de reference se porte par "canvas logique fixe + un seul scale"** :
  les previews JSX sont dessinees dans un cadre fixe (LiveLooper : 891x411). Un
  `weight(1f)` ou un `fillMaxSize()` etire les colonnes et **ecrase les textes**
  (c'etait le bug de la grille LiveLooper). Mesurer une seule fois
  `scale = min(maxW/W, maxH/H)` et l'appliquer a un enfant en taille logique.
  Pour une **fenetre flottante**, borner `scale` a `1f` : elle ne doit jamais
  grandir au-dela de sa taille de reference, seulement retrecir.
- **`widthIn(max = ...)` sur un bandeau** : un `Spacer(Modifier.weight(1f))` dans
  un `Box` aux contraintes maximales etire le fond sur tout l'ecran (bandeaux Undo
  pleine largeur). Borner la largeur (`widthIn(max = 420.dp)`) et centrer.
- **Horloge d'animation** : ne jamais lire un etat qui s'actualise a 60 Hz dans un
  composeur de page entiere (il recompose la top bar, le TonicPad, etc.). Isoler
  dans un petit composable (`LiveLooperHost`) et passer un drapeau `active` pour
  tomber au repos (~4 Hz au lieu de 30 Hz).
- **Deux `FxRegistry` coexistent** : `com.aetherkeys.fx.FxRegistry` et
  `com.soundstage.mixer.fx.FxRegistry`. Dans `com.aetherkeys.fx.ui` (sans import),
  `FxRegistry.STRIP_MASTER` ne resout pas : utiliser le chemin complet.
- **Un `snapshotFlow` de sauvegarde doit observer *tout* ce qui se persiste.**
  Le veilleur de `FxOverlay.kt` ne regardait que `effectIds` + `bypassed` ;
  `controller.instrumentId` en etait absent, donc choisir un instrument ne
  declenchait aucun `save()`. Regle : la liste des cles observees = la liste des
  champs du blob sauvegarde.
- **Un bypass doit se voir sur la carte, pas seulement sur la bande.** La
  reference applique `filter:saturate(.2) brightness(.7)` aux **deux**. Un rendu
  qui ne grise que la bande ouverte laisse le rack paraitre inchange.
- **Un test qui passe ne prouve rien tant qu'on ne l'a pas fait echouer.**
  Retirer le correctif et relancer : s'il passe encore, le test n'exerce pas le
  chemin. Deux faux positifs ont ete payes sur ce depot :
  - `Thread.sleep` ne remplace pas l'idling du looper. Robolectric met le looper
    principal **en pause** : une affectation faite depuis `Dispatchers.Main`
    n'est jamais jouee, et l'assertion lit la valeur par defaut du demarrage.
    Utiliser `shadowOf(Looper.getMainLooper()).idle()` dans une boucle d'attente.
  - `openProject()` ecrit `activeProjectName` de facon **synchrone**, avant que
    `selectScene` ait fini. Attendre `activeSceneId`, pose seulement dans le bloc
    Main de `selectScene`.
- **Une capture de page doit etre prise apres la restauration asynchrone.**
  `MixerViewModel` restaure son etat (et sa page) en differe : creer une setlist
  **avant** `setContent` la fait ecraser. La creer **apres** (dans `runOnIdle`,
  avec une attente idlee sur `activeSceneId`).
- **`SectionLabel` met son texte en majuscules** (`text.uppercase()`). Un
  `onNodeWithText("Virtual keyboard")` echoue : chercher `"VIRTUAL KEYBOARD"`.
- **`visibleTracksCount` est la source du nombre de faders** :
  `MixerScreen` rend `tracks.take(visibleTracksCount.coerceIn(1, 8))`. Une
  setlist qui ne l'ecrit pas retombe sur `tracks.count { it.isEnabled }` = 8,
  puisque toutes les pistes sont livrees actives.
- **Un meter a 20 Hz derriere un blur plein ecran provoque la saccade.** Chaque
  mise a jour d'etat recompose le sous-arbre et force la passe hors ecran du
  RenderEffect. Ralentir la boucle tant qu'une fenetre flottante est ouverte
  (`activePopup.isFloatingWindow()`).
- **Clavier virtuel — le halo LED doit etre peint EN DERNIER.** Il etait dessine
  avant la boucle des touches, donc recouvert par leur remplissage : « LED » ne
  se distinguait pas de « Color ». Le dessiner apres, en `BlendMode.Plus`, sur
  une face de touche quasi noire — c'est l'addition qui fait le neon.
  `KeyboardTheme.neon` commande la largeur du bloom. Themes : 14 au total,
  dont `oled` (faces de touches litteralement `#000000`).
- **`setContent` ne peut etre appele qu'une fois par test.** Pour deux captures,
  muter un etat detenu **hors** composition (un `mutableStateOf` cree dans
  `setContent` est reinitialise a chaque recomposition, et les deux PNG sortent
  au meme md5). Et entre les deux : `waitForIdle()`, pas `runOnIdle` sous
  horloge manuelle.

---

- **Snapshot : un seul codec, un seul point de synchro.**
  `SnapshotJson` est le **seul** codec de `TrackSnapshot`/`SubSceneSnapshot` : la
  forme etait ecrite a la main **six fois** (3 lecteurs, 3 writers), donc tout champ
  oublie disparaissait en silence. Ne pas re-inliner un lecteur.
  `syncActiveSnapshot()` rafraichit le snapshot actif depuis
  `persistCurrentStateDebounced()` (point d'etranglement unique de toute mutation) :
  un snapshot est une copie **vivante**, pas one-shot. Gardes : pas de synchro
  pendant un rappel (`isRecallingSnapshot`) ni en armement.
- **`customColorArgb` absent != 0.** `null` = pas de surcharge, `0` = noir opaque.
  Toujours tester `has()` avant `optInt`, sinon une piste devient noire au rappel.
- **`org.json` est stubé en test JVM pur** (tout leve « not mocked »). Les tests de
  round-trip JSON passent par Robolectric ; les decisions pures restent JVM pur.
- **Drum/Pad (slots 8/9) ne sont jamais liberes** par un changement de patch : ils
  appartiennent aux surfaces, pas au patch.

## 7. Travailler en parallele (plusieurs agents sur le meme depot)

Le depot est developpe par plusieurs conversations OpenHands en meme temps.

1. **Avant** toute modification : `python3 tools/conv_sync.py` -> liste ce que
   l'autre conversation a fait depuis le dernier appel.
2. Faire le travail.
3. **Apres** la modification : relancer `python3 tools/conv_sync.py`.
4. Avant de commiter : `git fetch origin main && git rebase origin/main`
   (le remote peut avancer : uploads manuels, autre agent).
5. **Ne pas pousser** sans demande explicite de l'utilisateur.

`conv_sync.py` lit l'API Cloud (`OPENHANDS_API_KEY`) et suit un watermark dans
`tools/.conv_sync_state.json` (gitignore). `--full` rejoue tout, `--json <f>`
exporte les nouveaux evenements.

Si `conv_sync` montre que l'autre agent touche **le meme fichier** : s'arreter et
demander, ne pas ecraser.

---

## 8. Conventions de commit

```
[Mixer V2] <resume court> — Compile: OK

Co-authored-by: openhands <openhands@all-hands.dev>
```

- `git config` absent -> `user.name = openhands`, `user.email = openhands@all-hands.dev`.
- Ne pas commiter : `debug.keystore`, `local.properties`, `build/`, `.gradle`,
  `tools/__pycache__`, les arbres extraits des zips (`app/assets/*-package/`,
  `app/assets/fx-screens-spec/`).
- `PROJECT_CONTEXT.md` est genere **et** commite (`python3 tools/gen_project_context.py`) ;
  `.gitattributes` coupe son diff et le marque `linguist-generated`.

---

## 9. Ou sont les choses utiles

| Besoin | Chemin |
|---|---|
| Memoire pieges/decisions | `AGENTS.md` |
| Code actif inline | `PROJECT_CONTEXT.md` |
| Methode de travail | `PROTOCOLE_AGENT_PRECISION.md` |
| Ce guide | `GUIDE_AGENT.md` |
| Regenerer le contexte | `tools/gen_project_context.py` |
| Suivre l'autre conversation | `tools/conv_sync.py` |
| Comparer une capture FX | `tools/fxcompare/compare_window.py` |
| Banc de rendu LiveUI | `tools/liveui/` |
| Extraire le JSX de reference | `tools/jsxref/` |
| Environnement / build | `/workspace/restore_env.sh`, `/workspace/build.sh` |

---

## 10. Page LiveLooper (DrumPad + LiveLooper)

Le DrumPad et le LiveLooper sont **la meme page** (`DrumPadScreen`) : seul le
panneau droit change. Un selecteur dans la top bar bascule (`LooperPageSelector`).

- **Colonne gauche partagee** : `TonicPadColumn` (grille 3x4, octave, Multi,
  Clear) est utilisee par les deux pages. Ne pas la dupliquer — c'est ce qui a
  fait « disparaitre » le TonicPad lors d'un port precedent.
- **Panneau droit** : `LiveLooperContent` / `LiveLooperHost`
  (`com/soundstage/mixer/looper/LiveLooperUi.kt`). Il contient la rangee
  Transport puis la grille 2x4 des cases.
- **Modele/logique** : `com/soundstage/mixer/looper/LiveLooperModel.kt`
  (`LooperController`, `LooperLogic` pur). Le VM l'instancie (`liveLooper`),
  alimente l'horloge (`transportClock.currentBarPosition()` en mesures) et
  rejoue via `replayLoopEvent`.

### Il n'y a qu'UN looper : ne pas ressusciter l'ancien

L'ancien moteur evenementiel a ete **supprime** (`audio/LooperEngine.kt`,
`audio/DrumLooperEngine.kt`, `LoopsGrid`, `DpLoopCase`, `DpLoopMenu`, le segment
Pads/Loops). Ne pas le chercher, ne pas le recreer. Tout le looper vit dans
`com/soundstage/mixer/looper/`.

**Ne pas confondre** avec deux fonctionnalites voisines qui, elles, restent :
`SeamlessLoopTrimmer` (utilise par `model/DjLoopEngine.kt`) et
`DrumPadLooperEngine` (enregistre une boucle de batterie vers un WAV, branchee
dans `MixerScreen`). Ce ne sont pas l'interface LiveLooper.

### Robustesse : borner a la frontiere, pas au point d'usage

Le LiveLooper plantait sur l'appareil a cause d'acces `cases[id]` non bornes.
Regle appliquee (et a conserver) :

- `LoopPatchJson.caseFromJson` borne **tout** entier lu du JSON : `id` (0..7),
  `layers` (0..MAX_LAYERS), `bars` (0..MAX_BARS). Un `patches.json` ancien ou
  edite a la main ne doit jamais produire un etat hors bornes.
- Les actions du controleur (`onRec`, `onPlay`, `onUndo`, `onRemoveLayer`,
  `onVolume`…) passent par `caseOrNull(id)` / `updateCase`, jamais `cases[id]` :
  un `focusId`/`sheetCaseId` perime atteint le controleur comme argument, et une
  exception levee depuis un callback Compose demonte la page sans retour.
- `loopCaseColor(id)` borne **des deux cotes** (le `%` de Kotlin garde le signe).
- Regression : `LooperStressTest.everyActionWithAnOutOfRangeIdIsIgnoredInsteadOfThrowing`
  passe -1, 8, 99, Int.MIN/MAX a chaque action.

### Regle de layout : la grille est FLUIDE, pas mise a l'echelle

Le JSX (`LoopsPreview.jsx`, panneau droit `left:188 right:10 top:60 bottom:8`)
pose une grille `repeat(4, minmax(0,1fr))` x `repeat(2, minmax(0,1fr))` : les
cases se partagent la place disponible.

**Ne pas** remplacer par un canvas logique fixe + `graphicsLayer(scale=min(...))`
(idiome de `FxWindowHost`). Sur un telephone ce scale ecrase les deux rangees en
bandes fines — les cases passent de ~120 dp a ~45 dp et les numeros se lisent
comme « une ligne ». Mesure : 45,5 dp (casse) contre 120,4 dp (correct) en 640dp.

**Consequence sur les taps** : une case de 45,5 dp ne peut pas contenir son
contenu (en-tete 18 + gap 6 + `ActionRow` 28 + gap 6 = 58 dp). La rangee de
boutons Record/Play/Undo/More etait **rognee hors de la case** : on ne pouvait
plus rien declencher. Un probleme de « cablage » peut n'etre qu'un probleme de
taille — verifier la geometrie avant de suspecter le binding.

Le patch window, lui, **est** une fenetre flottante : elle garde sa taille
dessinee (651x341) et ne fait que retrecir (`coerceAtMost(1f)`).

### Piege n1 : `lineHeight` Material3 ecrase les controles

`ui/theme/Type.kt` fixe `lineHeight = 24.sp`. Un `Text` qui herite de ce style
mesure **24 dp de haut meme a 8,5 sp**. Dans une ligne de hauteur fixe il ne
laisse presque rien au controle du dessous :

- `Transport` (`height(38.dp)`) : le libelle de groupe prend 24 dp -> le
  segmented Quantize/Length/Count-in tombe a **6,5 dp** (une simple ligne).
- `LoopPatchChip` (`height(44.dp)`) : deux libelles empiles = 48 dp -> rognes.

**Regle** : tout `Text` dans une hauteur contrainte prend `lineHeight = fontSize`.
Symptome typique : « le controle est ecrase », pas « le texte est decale ».

**Mesure** : profil vertical de bandes (fraction de pixels non-fond par ligne).
Comparez la hauteur mesuree a la hauteur **declaree** : 20 dp attendus qui en
mesurent 6,5 = un ancetre contraint.

### Verifier une capture sans emulateur

```bash
/workspace/build.sh :app:recordRoborazziDebug \
  --tests "com.soundstage.mixer.DrumPadLiveLooperCaptureTest" \
  --tests "com.soundstage.mixer.LLPhoneCaptureTest"
```

Puis mesurer les bandes de cartes (Pillow) : deux rangees d'environ 120 dp en
640dp, 137 dp en 851dp, pour une reference a 146 dp en 891dp.

---

## 10b. Instruments Aether (Organ / Synth / Leads / Sax) — sur une piste, comme un soundfont

Organ, Synth, Leads et Sax ne sont **plus** des entrees du rack FX. Ils se chargent sur une
piste depuis la **fenetre soundfonts** (onglet « Æther Instruments » a gauche),
et se comportent exactement comme un soundfont : MIDI, volume, pan, mute, couleur,
nom sur la plaque. Ce sont des **instruments reels**, pas des effets.

| Element | Chemin |
|---|---|
| Hote moteur (AeHost -> JNI) | `instruments/AeInstrumentHost.kt` (`AeInstrumentHost`) |
| Gestionnaire applicatif (singleton) | `instruments/AeInstrumentHost.kt` (`AeInstruments`) |
| Logique + sessions | `instruments/AeCore.kt` (`AeManager`, `AeSession`, `AeHost`) |
| Onglet + fenetre instrument | `instruments/AeWindow.kt` |
| Rendu / polices / SVG | `instruments/AeStage.kt`, `AeDraw.kt`, `AeSvgAssets.kt` |
| UI Organ / Synth | `instruments/AeOrganUi.kt`, `AeSynthUi.kt`, `AeControls.kt` |
| UI Leads / Sax | `instruments/AeLeadsUi.kt`, `AeDrawLeads.kt`, `AeSaxUi.kt`, `AeSaxAssets.kt` |
| Presets (organ 22, synth 28, leads 30, sax 23) | `instruments/AePresets.kt` |
| Moteurs DSP natifs | `cpp/aetherfx/fx_organ.h`, `fx_synth.h`, `fx_leads.h`, `fx_sax.h` |
| Point d'entree fenetre | `ui/components/SoundfontWindow.kt` (params `aether`, `aetherStrip`) |
| Appelant | `ui/MixerScreen.kt` (`AeInstruments.manager`, `activeSoundfontSlotId`) |
| Polices au demarrage | `SoundStageApplication.onCreate` -> `initAetherInstruments(this)` |
| Tests | `app/src/test/.../AeManagerTest.kt` (8 tests, JVM pur, `FakeHost`) |

### Regles a ne pas casser

- **Le son ne passe pas par Kotlin.** `routeEventToSource` (natif) route deja les
  notes vers la source des que la piste porte un instrument. Kotlin ne fait que
  charger/regler/afficher. Ne pas ajouter de hook de notes cote VM.
- **Un instrument Æther et un soundfont s'excluent** sur une piste :
  `AeInstruments.onBeforeLoad` decharge le SF ; `loadSoundFontForSlot` decharge
  l'instrument. Le dernier charge gagne.
- **Le registre garde les ids 6, 14, 15, 16.** Seul `FxAddOverlay` les filtre
  (`AE_RETIRED_INSTRUMENT_IDS = setOf(6, 14, 15, 16)`). Le routage natif et la
  persistance en ont besoin.
- **`FX_SAX = 7` est un shaper ; le sax source s'appelle `FX_SAX_AE = 16`.** Ne pas
  confondre les deux ids.
- **`FxRegistry.info(id)` doit rester borne** (`all.getOrElse(id) { all[0] }`) : un id
  hors plage sortait droit d'un callback Compose.
- **Les moteurs sont header-only et portables** : `g++ -O2 -std=c++17 -I app/src/main/cpp`
  les compile sur l'hote, sans Android ni emulateur. C'est la verification la moins chere
  avant un build NDK complet.
- **L'onglet n'existe que pour `SoundfontVariant.Track`** (slots 0..7).
- **`AeFonts` a des replis systeme** : sans `initAetherInstruments`, l'UI tourne
  en SansSerif/Monospace, en silence. Ne pas retirer l'appel.
- **`AeSvgAssets.kt`** : ne jamais ecrire `svg/*.svg` dans une KDoc — le `/*`
  ouvre un commentaire imbrique en Kotlin et la compilation casse.
- **Le manager pousse le preset 1 en entier** au chargement. Ne pas re-pousser
  les params cote VM.

### Etat / reste a faire
Fait : import, hote, gestionnaire, onglet + fenetre, exclusivite, retrait des FX,
early-out silence (organ/synth/leads/sax), polices, nom sur la plaque, tests.
Leads (15) et Sax (16) ajoutes au zip final, ajustements DeepSeek appliques
(drift par voix, formants suivis en pitch, LFO de souffle) ; verifies a l'hote et
par `assembleDebug` (4 ABI).
Reste : **validation appareil** (pas d'emulateur ici), persistance du choix
d'instrument par piste (setlist), captures Roborazzi de l'onglet.

---

## 11. Track colors (Settings > Interface)

Sous-page de reglage des couleurs de pistes, cablee au mixer.

| Element | Chemin |
|---|---|
| Mode, palettes, logique pure | `ui/components/TrackColors.kt` |
| Sous-page UI | `ui/settings/TrackColorsPage.kt` |
| Carte d'entree (Interface) | `SettingsScreen.kt` -> `TrackColorsCard` |
| Resolution par piste | `TrackChannel.resolvedColor(scheme)` (`VerticalFader.kt`) |
| Scheme + CompositionLocal | `MixerScreen.kt` (`effectiveScheme`, `LocalTrackColors`) |

**Principe** : `MixerScreen` resout le scheme **une fois** (avec la derive) et le
publie par `CompositionLocalProvider(LocalTrackColors provides ...)`. Les strips
lisent `LocalTrackColors.current` ; ils ne resolvent **jamais** une couleur
eux-memes. Un override par piste (`TrackChannel.customColorArgb`) gagne toujours
sur le scheme. Le **master** garde sa couleur propre, il ne suit pas les modes.

**Modes** : `Palette` (5 palettes nommees), `Random` (8 tirages figes), `Single`
(une seule couleur). Changer de mode **efface** les overrides par piste, sinon le
scheme ne se verrait pas.

**Derive « Dynamic »** : +-14 degres de teinte sur 90 s, imperceptible frame a
frame. `TrackColorsLogic.driftColor` est du **Kotlin pur** (pas
d'`android.graphics`) pour tourner en test JVM et en preview.

**Persistance** : prefs **plates** (hors blob d'etat) : `track_color_mode`,
`track_color_palette`, `track_color_random`, `track_color_single`,
`track_color_dynamic`. Restaurees par `restoreTrackColorSettings()`, appelee
**toujours** — ne pas les remettre dans le `if (saved != null)`.

**Piege de test** : capturer un element hors ecran d'une `Column` scrollable par
`performScrollTo()` -> `OutOfMemoryError` (ShadowTrace). Utiliser un viewport
haut (`@Config(qualifiers = "w851dp-h1600dp-440dpi")`). Un screenshot ne prouve
pas un cablage : cliquer l'element et verifier que le callback a ete appele.

---

## 12. Ajouter une palette de pistes

Dans `ui/components/TrackColors.kt`, declarer un `TrackPalette` puis l'ajouter a
`TrackPalettes.all` (l'ordre est l'ordre d'affichage). Contrainte : **8 couleurs
distinctes**, sinon Random tire des voies identiques et Palette donne deux pistes
de la meme couleur. `TrackColorsLogicTest` le verifie pour chaque palette, donc
une palette non conforme casse le build de test — c'est voulu.

---

## 13. Piege : mesurer une taille pendant une animation

`onSizeChanged` livre la taille **courante**, pas la taille au repos. Lire une
hauteur dedans pour reserver une fente la fige sur la valeur du **milieu** de
l'animation. Regle : latch seulement une fois l'animation posee (`delay` >= la
duree) et seulement dans l'etat de repos ; poser la toute premiere mesure tout de
suite pour ne pas sauter au premier affichage.

Symptome typique : « l'interface d'arriere bouge » un instant quand on replie.
Cas reel : `MixerScreen.kt`, la fente du clavier virtuel
(`keyboardRestHeight` / `keyboardMeasuredPx`), corrige le 2026-10-25.

Garde-fou : `KeyboardFoldStabilityTest` mesure la bottom bar (`testTag
mixer_bottom_bar`) pendant toute l'animation de repli. Toujours verifier qu'un
test de regression **echoue** sans le correctif (ici : 71 dp de poussee).

---

## 14. Piege : une scene a geometrie fixe ne doit pas suivre le fontScale

`AeT` (`instruments/AeStage.kt`) passe par `size.fs()` qui produit des **`sp`**.
Toute la geometrie de la scene Æther est en `dp` (`su()`), et `AeStage` ne figeait
pas le `fontScale`. Consequence : un utilisateur reglant la taille de police
systeme a **1,3x** obtenait des libelles 30 % plus gros que leur case, alors que
les cases, elles, ne bougeaient pas — textes ecrases, valeurs decalees.

Regle : des qu'un ecran est une **surface a ratio fixe** (scene, canvas de preview,
HUD dessine), figer le `fontScale` dans le `CompositionLocalProvider` :

```kotlin
CompositionLocalProvider(
    LocalAeScale provides s,
    LocalDensity provides Density(d.density, fontScale = 1f),
) { /* ... */ }
```

**Pourquoi les captures ne le voyaient pas** : Roborazzi tourne en `fontScale = 1`.
Un bug de fontScale est **invisible dans tous les tests de capture** — il ne se
reproduit qu'a l'oeil, sur un appareil regle autrement. Ne pas conclure « les
captures sont bonnes donc l'ecran est bon » pour ce genre de defaut.

**Complements dans `AeT`** : `lineHeight = fontSize` (le cadre Material3 par defaut
de 24 sp fait plonger un libelle de 9 unites dans une case de 12) et
`wrapContentWidth(unbounded = true)` (la reference ne tronque jamais).

---

## 15. Piege : une fenetre flottante qui n'arrete pas ce qui tourne derriere

Symptome : le blur et les fenetres (Track Settings, Patches, FX) paraissent lourds,
saccades. Le reflexe est d'accuser le flou. C'etait faux.

**Cause reelle** : `startPeakMeterSimulation` (`MixerViewModel`) mettait a jour les
VU-metres a 5 Hz **meme quand une fenetre couvrait le mixer**. Chaque
`_uiState.update` recomposait tout le sous-arbre, ce qui **forcait le blur a refaire
sa passe hors ecran** a chaque tick. Le cout n'etait pas le flou, mais le nombre de
recompositions qu'il devait suivre.

Regle : quand une surface plein ecran couvre le mixer (`activePopup.isFloatingWindow()`),
**geler** toute boucle d'animation qui n'est pas visible : VU-metres (`delay(200);
continue`) et derive des couleurs de piste. Elles reprennent seules a la fermeture.

Meme logique pour toute animation de fond : se demander « est-ce visible ? » avant
« est-ce que ca coute cher ? ».

---

## Persistance des scenes : la regle du « lecteur permissif »

`selectScene` / `openProject` lisent les cles du JSON de scene avec un repli :
**cle absente = garder l'etat courant**. C'est voulu (compatibilite avec les
fichiers ecrits par les anciennes versions), mais le corollaire est severe :

> tout ecrivain qui omet une cle ne produit pas une scene vide, il produit une
> scene qui **herite** de l'etat precedent.

C'est exactement ce qui faisait apparaitre les soundfonts du dernier Setlist dans
un Setlist neuf (`createSetlistFromScreen` n'ecrivait pas `tracks`/`audioSlots`).

Regles :
1. Tout ecrivain de scene/patch passe par `model/TrackJson.kt`
   (`TrackJson.toJson`, `AudioSlotJson.toJson`). Ne jamais ecrire ces cles a la main.
2. Un « nouveau X vierge » doit aussi **decharger le moteur natif** :
   `safeAllNotesOff()` + `safeUnloadAllSoundFonts(ENGINE_FADER)` + `safeTrimMemory()`.
   `createNewProject` le faisait, `createNewPatch` non.
3. Quand on ajoute une fonction « creer un nouveau ... », verifier les **deux**
   moities : l'etat Kotlin **et** le moteur natif.

## Cartes soundfont : le traitement verre

`SoundfontWindow.kt` porte `Modifier.glassRow(active, opaqueBase)`. A utiliser pour
toute ligne de liste. Points a ne pas rater :
- le contour doit rester **tres faible** (7 % de blanc en repos) ; un contour a 15 %
  fait « dur » et c'est ce que l'utilisateur rejette ;
- `opaqueBase = true` est **obligatoire** pour les lignes a swipe-to-delete (le
  fond translucide laisserait voir la poubelle) ;
- l'ordre des modificateurs compte : `shadow` -> `clip` -> fond -> bord -> liseré.
