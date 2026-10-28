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
Conversation de reference : `dde6dffb48c5475a931918c2e3ff38f9`.

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

### Le clone peut etre tres en retard — verifier AVANT de conclure

Constate le 2026-10-27 : le clone local etait **44 commits derriere**
`origin/main`, et `git status` affichait 23 fichiers Æther **indexes** (`A`/`M`).
On pouvait croire a du travail non commite en danger. En realite ces fichiers
etaient **identiques a `origin/main`** : l'index avait ete rempli par un
`git add` anterieur, jamais commite.

```bash
git fetch origin main
git rev-list --left-right --count HEAD...origin/main   # gauche=local, droite=remote
# Preuve que rien n'est perdu : comparer l'index au remote, fichier par fichier
for f in $(git diff --cached --name-only); do
  a=$(git show ":$f" | md5sum); b=$(git show "origin/main:$f" | md5sum)
  [ "$a" = "$b" ] && echo "identique $f" || echo "DIFFERENT $f"
done
```

**Regles**

- Un index rempli n'est **pas** du travail perdu : comparer au remote avant de
  conclure quoi que ce soit.
- Jamais de `git reset --hard` sans branche de sauvegarde
  (`git branch -f backup/pre-sync-$(date +%Y%m%d-%H%M) HEAD`).
- `main` est la branche de travail ; `docs/fx-agent-pack` et `fx-preview-blocs`
  sont obsoletes (retard de 44 a 57 commits). Les 2 docs
  `docs/FX_WINDOW_AGENT_PACK.md` / `docs/SOUNDFONT_WINDOW_PACK.md` n'existent que
  dans `docs/fx-agent-pack` et les branches `backup/`.

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
| Presets (organ 22, synth 55, leads 32, sax 23) | `instruments/AePresets.kt` |
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

### Presets : la liste est la source, pas le brief

Les presets viennent de `presets-leads-sax.json` / `presets-organ-synth.json`
(dans `app/assets/aether-instruments-final.zip`, gitignore) et sont recopies
dans `AePresets.kt`. `param_order` du JSON donne l'ordre exact des 32 valeurs —
c'est lui qui permet de convertir une intention en tableau.

**Piege majeur — un brief externe decrit un moteur qui n'existe pas.** Les specs
recues parlaient de `osc1`/`osc2`/`fm`/`layer1`/`arpRate`/`chord`. Aucun n'est un
parametre de `fx_synth.h`, qui expose 32 parametres reels (WaveA/WaveB morphing,
Detune, Sub, Noise, Unison, Spread, Cutoff, Res, Drive, EnvAmt, FType, ADSR,
2 LFO, 4 macros, Mode, Glide, Voices, Bend, Tune, Level). Traduire vers ces
parametres ; ne pas inventer de champs. Deux consequences a annoncer :
- **pas de FM** dans ce moteur (soustractif) : les presets nommes « FM » sont
  rendus en sinus + enveloppe percussive, le nom ne decrit pas le timbre ;
- **pas d'arpeggiateur** : un preset « Arp » est un *son* d'arpege.
`arpRate`, `chord`, `unison2`, `det2`, `mix2` n'ont pas d'equivalent et sont
ignores. Toujours dire ces ecarts a l'utilisateur au lieu de les taire.

**Regle de classement** : « concentrer les leads dans Leads ». Un preset de
synth qui se joue en melodie montee (lead) va dans `leads`, meme si son genre
est Kompa/Gospel. `synth` garde les pads, basses, claviers, stabs, cordes, FX.

**Les chips de categorie** (`PresetList`) sont un `LazyRow` : elles defilent.
Ajouter des categories ne casse pas la mise en page.

### Etat / reste a faire
Fait : import, hote, gestionnaire, onglet + fenetre, exclusivite, retrait des FX,
early-out silence (organ/synth/leads/sax), polices, nom sur la plaque, tests.
Leads (15) et Sax (16) ajoutes au zip final, ajustements DeepSeek appliques
(drift par voix, formants suivis en pitch, LFO de souffle) ; verifies a l'hote et
par `assembleDebug` (4 ABI).
Presets v2 (2026-10-27) : leads remplaces (32), synth etendu a 55 en 11
categories fonctionnelles, anti-tac de l'attaque corrige dans `fx_leads.h`
(mesure : saut d'attaque -33 % sur les leads saw).
Reste : **validation appareil** (pas d'emulateur ici), persistance du choix
d'instrument par piste (setlist), captures Roborazzi de l'onglet.

---

## 10c. Æther Sax hybride — banques echantillonnees (recherche du 2026-10-27)

Objectif demande : **instrument hybride** — la synthese reste en **lit de fond
discret**, les samples deviennent le **timbre principal**. On ne supprime pas la
synthese : elle couvre les transitions entre zones d'echantillons (le point
faible d'un sampler) et garde le legato continu.

### Ce qu'on remplace, exactement

Dans `fx_sax.h`, `process()` genere la source en deux lignes :

```cpp
float mix = reed(ph_[0]) * .55f + reed(ph_[1]) * .35f;   // <-- source synthetique
mix += nbF_.process(0, noise_.next()) * breathNoise;      // <-- souffle (a garder)
```

Tout le reste — formants suivis en pitch, grit asymetrique, growl, subtone,
scoop/fall, vibrato retarde, clic, reverb — **reste intact**.

Le point cle : `f` est calcule **en amont** avec bend, scoop, fall et vibrato
deja appliques. Passer `f` a un lecteur d'echantillons fait donc porter
l'articulation existante au sample **sans une ligne de changement**.

### Banques : ce qui est utilisable et ce qui ne l'est pas

| Banque | Licence | Contenu | Verdict |
|---|---|---|---|
| **MTG.SoloSax** (`sfzinstruments/MTG.SoloSax`) | **CC-BY 4.0** | Soprano, Alto, Tenor, **Bari** — 737 FLAC 24 bit/48 kHz mono, 105,6 Mo | **Retenue** : les 4 types d'un coup |
| FreePats Tenor | **CC0 1.0** | 18 (small) / 46 (full), « infinite sustain loops » | Repli sans attribution |
| Iowa MIS | « without restrictions » | Soprano, Alto, 15 a 67 s | Pas de bari ni de tenor |

- **FluidR3 GM : ECARTE.** Son chunk `ICMT` dit **« DO NOT REDISTRIBUTE ANY OF
  THESE SAMPLES. »** Ne plus le proposer.
- **VSCO-2 CE : aucun sax** (0 fichier sur 3 313), malgre sa reputation.
- **Philharmonia** : a bien un sax (733 mp3, `saxophone.zip` imbrique de 12,8 Mo
  dans `Woodwind.zip`, extractible par requete Range) mais **le type n'est pas
  indique** et D3..G6 est trop aigu pour un baryton. Licence introuvable.
- **GeneralUser GS** : telechargement bloque (403), licence non verifiable.

### MTG.SoloSax — mesures a connaitre

- **Boucles impeccables** : correlation normalisee `x[loopStart]` / `x[loopEnd]`
  = **0,95 a 0,998** sur les 4 types. Controle sur un point arbitraire : 0,2 a
  0,99 -> la mesure discrimine.
- Boucle = **nombre entier de periodes** (ecart 0,005 a 0,115 sur 12 a 52
  periodes) -> pas de clic par construction.
- 3,6 a 4,9 s par sample ; boucle de 78 a 182 ms.
- **2 couches de velocite** (`p`/`f`), **3 round-robins**, souffle (`_b`) et clic
  (`_k`) separes.
- **Piege de parsing** : les points de boucle sont dans le bloc **APPLICATION
  `riff`/`smpl`** du FLAC, **pas** dans le SFZ (`Data/*.txt` n'a aucun opcode
  `loop_*`). Lire `smpl` a partir de `b.index(b'smpl')+8` ; sinon on lit des
  octets deplaces et on croit qu'il n'y a pas de boucle.
- **FreePats** : aucun vibrato grave dans les samples (**0 a 3 cents**, mesure par
  autocorrelation). Le `V.Depth`/`V.Rate` du moteur reste donc *le* vibrato de
  l'instrument.

### La synthese est trop pauvre — enveloppe harmonique mesuree

`buildTable()` est **deja additif** (32 harmoniques, `im[n] = (1/n^q) * (n impair
? 1 : .78)`). « Passer en additif » = **corriger la loi**, pas reecrire le moteur.

Mesure Goertzel sur la **boucle de sustain**, 33 notes par type, **f0 mesure par
autocorrelation** et non lu du SFZ : la banque est environ **5 % basse** (ratio
0,94 a 0,95 sur les 4 types), donc un f0 lu du SFZ decale toutes les harmoniques.

Enveloppe normalisee sur h1, moyennee par type :

| h | sop | alt | ten | bar | loi `1/n^1.15` |
|---|---|---|---|---|---|
| 1 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 |
| 2 | 0,904 | 0,594 | 0,869 | **1,886** | 0,351 |
| 3 | 0,512 | 0,477 | 0,782 | **2,520** | 0,283 |
| 4 | 0,224 | 0,232 | 0,452 | 1,600 | 0,158 |
| 5 | 0,135 | 0,210 | 0,411 | 1,989 | 0,157 |
| 6 | 0,062 | 0,174 | 0,326 | 1,004 | 0,099 |
| 8 | 0,037 | 0,118 | 0,218 | 0,976 | 0,071 |

Deux corrections par rapport a la table d'une passe precedente : elle moyennait
les 4 types ensemble (ce qui masquait le baryton) et prenait le f0 du SFZ.

**Le baryton a h2 et h3 AU-DESSUS de h1.** Verifie deux fois : f0 mesure (ratio
0,94, donc pas d'erreur d'octave) et spectre absolu (h1 a -3,9 dB contre un
maximum en h3). Un grand cone rayonne mal sa fondamentale : c'est une signature
acoustique reelle, pas un artefact. Ne pas « corriger ».

**Piege majeur — double formant.** Le spectre mesure contient deja le formant que
`form_[0..3]` reproduit. Appliquer l'enveloppe brute **compte le formant deux
fois**. Elle doit etre appliquee en **rapport** a la loi generique, normalisee en
niveau (c'est ce que fait `buildTable()` quand `measured_` est vrai). Verifie :
baryton h3 0,043 -> 0,141, soprano h2 0,143 -> 0,051 — le baryton gagne son
corps, le soprano s'epure, dans la bonne direction.

**Piege — la table n'est pas normalisee.** Sa crete varie d'un **facteur 4,5**
selon `q` (1,18 a 5,35) et le moteur s'appuie dessus. Toute modification de
`buildTable()` doit preserver ce comportement, d'ou la normalisation du *rapport*
et non de la table entiere.

### Cibles de taille (mesurees)

| | Brut 16 bits | 22,05 kHz | + ADPCM 4 bits |
|---|---|---|---|
| FreePats Tenor small (18 samples) | 7,0 Mo | 3,5 Mo | **1,8 Mo** |
| FreePats Tenor full (46 samples) | 26,8 Mo | 12,3 Mo | 6,2 Mo |

Compression retenue : **22,05 kHz + mono + ADPCM 4 bits** (le decodage reste
quasi gratuit sur le thread audio ; OGG est ecarte pour le risque d'underrun).

### Etat (au 2026-10-27)

Les quatre premiers points sont **faits et verifies sur l'hote** (`g++ -O2`) :

1. **Extraction** : `tools/saxpack/extract.py` -> 4 packs, **266 regions, 7,01 Mo**
   (sop 1823 Ko / alt 2138 / ten 1988 / bar 1234), MIDI 36..88, 2 couches de
   velocite. Verification `tools/saxpack/verify.py` : **couture 0,974 au pire,
   0,994 en mediane**, SNR 30,8 a 32,2 dB, couverture MIDI sans trou.
2. **Lecteur** : `aetherfx/fx_sax_samples.h` (nouveau). Keymap par region la plus
   proche, transposition, boucle, fondu de raccord, enveloppe attaque/relachement.
3. **Mixage hybride** : `SaxEngine::setSampleMix(0..1, defaut 0)` +
   `loadSamples()`. Le mixage se fait **apres** la chaine de formants (le sample
   porte le sien). A 0, la sortie est **bit-identique** a la synthese seule.
4. **Loi harmonique** : `ENV_SOP/ALT/TEN/BAR` + `setMeasuredTable(bool, defaut
   vrai)`.

**Fait depuis (2026-10-27)** : les 4 packs sont **embarques**
(`app/src/main/assets/saxpack/`, 7,01 Mo), le **pont JNI** est pose
(`fxLoadInstrumentBank` / `fxInstrumentHasBank` / `fxSetInstrumentSampleMix` /
`fxInstrumentSampleMix`) et **le chargement est automatique** au montage de
l'instrument. Verifie sur l'hote et par `assembleDebug` (4 ABI), 16 tests verts.

Reste : **validation appareil** (ecouter le mix a 0,7) et le **reglage du mix
dans l'UI** (il est fixe a 0,7 cote Kotlin, aucun potard ne l'expose encore).

### Trois pieges qui ont coute une passe chacun

- **Le reechantillonnage casse la couture.** A 48 kHz les boucles de la banque
  font un nombre entier de periodes ; a 22,05 kHz les points tombent entre deux
  frames et la correlation chute de **0,95 a 0,82** sur les aigus (mesure sur
  `sop_f_30`). Corrige par `snap_loop_end`, qui **recherche** `loopEnd` sur
  +/-384 frames par correlation de couture (retour a 0,99).
  **Ne PAS estimer la periode par autocorrelation pour recaler** : sur les aigus
  elle accroche l'harmonique 2 et la boucle tombe une octave a cote — couture
  mesuree **0,32**, pire qu'avant. Optimiser la couture elle-meme n'a pas ce
  mode de defaillance.
- **Le lecteur ne doit pas emprunter la memoire de l'appelant.** Garder un
  pointeur vers le tampon de l'appelant est un use-after-free des que l'asset est
  libere alors qu'une note sonne : segfault constate. `load()` copie la charge
  utile.
- **Le decodage ADPCM ne doit pas etre paresseux.** Le predicteur doit rejouer
  depuis le debut du region a chaque acces : le decodage a la demande dans la
  boucle de rendu est **quadratique**. Une passe par note dans un tampon reutilise.

### Pont JNI du pack (2026-10-27)

Le moteur ne connait pas les banques : il tient un `SourceFx*` et rien de plus.
L'ouverture se fait donc par la **classe de base**, `fx_source.h` :

```cpp
virtual bool loadSampleBank(const uint8_t*, size_t) { return false; }
virtual float sampleMix() const { return 0.f; }
virtual void setSampleMix(float) {}
virtual bool hasSampleBank() const { return false; }
```

`SaxEngine` les surcharge ; `Organ`/`Synth`/`Leads` gardent le defaut et
**refusent** (c'est le comportement attendu, pas une erreur). Chaine d'appel :
`fx_mixer.h` (`loadInstrumentBank`) -> `audio_engine.h` (`fxLoadInstrumentBank`,
**au-dessus du `private:`** l.912) -> `native-lib.cpp` -> `NativeAudioBridge.kt`
(`safeLoadInstrumentBank`) -> `AeInstrumentHost.loadBank`.

- **Le type de sax vient de `values[0]` du preset**, pas d'une seconde source :
  c'est exactement la valeur que le moteur lit pour choisir ses formants.
- **`AeBanks`** (`AeInstrumentHost.kt`) lit l'asset et **le garde en cache** :
  le natif copie, donc relire 2 Mo a chaque chargement serait du gaspillage.
- **Changer le type recharge la banque** (`AeManager.setParam`, index 0 du sax).
  Sans ca les samples restaient ceux de l'ancien type pendant que la synthese
  passait au nouveau. Le test `changingTheSaxTypeReloadsTheBank` le verrouille.
- **Un echec de banque ne casse pas l'instrument** : `loadBank` renvoie false,
  la synthese reste et joue seule (`aFailedBankLoadLeavesTheInstrumentUsable`).
- **Invariant a ne pas perdre** : a `sampleMix = 0` la sortie est
  **bit-identique** a la synthese pure (mesure : ecart max 0,000000000). C'est ce
  qui rend la regression detectable.

### Piege de niveau (mesure)

Normaliser en **crete** rend le niveau dependant du facteur de crete : les
transitoires du baryton le placaient **2,7x sous** la synthese avec laquelle il
doit se melanger. Les regions sont donc normalisees en **RMS (0,20)** avec un
plafond de crete a 0,95, et le moteur applique `kSampleGain` (3,05, mesure).
Resultat a mix 1,0 : **0,186 RMS contre 0,230** pour la synthese, **0 ecretage**
(la synthese seule en ecrete 24).

### Ce que la mesure dit du signal, et qui n'est pas un defaut

Les packs baryton contiennent ~400 « sauts » de continuite (pas local > 6x la
moyenne locale). Comparaison original 48 kHz vs decode ADPCM : **404 contre 387**.
Les transitoires sont donc **dans l'enregistrement** (morsure de l'anche), pas
dans l'encodage. Controle sur un sinus : **SNR 46,6 dB** pour l'ADPCM seul contre
29 a 32 dB sur du sax reel — c'est le signal qui est dur a coder, pas le codec qui
est mauvais.

### Formants : la colonne « `TY_f1` actuel » du guide etait FAUSSE

**Correction 2026-10-27.** La table plus haut annoncait `TY_f1` = 617/813/570/523.
Ces valeurs ne correspondent a **aucune** version de `fx_sax.h` : verifie par
`git show` sur `ca32bf4`, `9bc5107` et `a866928`, les trois commits qui l'ont
touche. Les vraies constantes sont :

```cpp
static constexpr float TY_f1[4]  = {900.f, 650.f, 520.f, 400.f};   // sop, alt, ten, bar
static constexpr float TY_af[4]  = {670.f, 837.f, 618.f, 570.f};   // formant d'anche
```

Les valeurs annoncees etaient celles de `TY_af`, etiquetees `TY_f1`. Un « ecart
mesure » a donc ete poursuivi contre une table qui n'existait pas.

**`TY_f1` est physiquement coherent** : 900/650/520/400 Hz **decroit** avec la
taille de l'instrument, ce que fait tout saxophone reel (la cavite s'allonge).
Ne pas la « corriger » sur la foi de l'ancienne note.

**Nouvelle mesure (LTAS, moyenne sur toutes les notes, boucles de sustain
seules, lissage 101 bins)** — elle ne peut PAS trancher, et c'est le resultat :

| type | sommet de la bosse | largeur a -6 dB |
|---|---|---|
| sop | 743 Hz | 1174 Hz |
| alt | 721 Hz | 1001 Hz |
| ten | 721 Hz | 915 Hz |
| bar | 657 Hz | 1130 Hz |

Le sommet suit a peine le type et la bosse est large de ~1 kHz : F1 et F2 se
recouvrent, on lit un maximum unique au lieu de deux formants. Meme mesure avec
une fenetre plus fine : les « pics » trouves suivent la fondamentale (bar : f0
190 Hz, pics a 1227 et 1766 Hz = harmoniques 6 et 9), ce ne sont pas des
formants. **Le pack ne permet donc pas de valider `TY_f1`, ni de l'invalider.**
La mesure decisive demande un son tenu joue par un vrai sax sur la meme note,
analyse sur appareil — a faire avec l'utilisateur, pas en aveugle.

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

## 16. Pitch bend : FluidSynth et les sources Æther ne se reglent pas pareil

`setPitchBendSensitivity(channel, semitones)` ne pousse que FluidSynth
(`fluid_synth_pitch_wheel_sens`). Des qu'une **source Æther** occupe le canal, ce
canal ne passe plus par FluidSynth : la plage de bend doit etre poussee **dans la
source**.

Regle : pour regler la plage de bend d'une piste, appeler
`AudioEngine.setTrackPitchBendRange(track, semitones)`. Cette methode pousse
**les deux** renderers (synth FluidSynth et `FxMixer`) — un appel a
`getEngine(...).setPitchBendSensitivity` seul laisserait l'instrument Æther sur sa
plage par defaut.

- Defaut : **2 demi-tons** (1 ton), cote natif (`SourceFx::bendSemis_ = 2`).
- Chaque source applique la plage a sa maniere : Leads/Synth multiplient la
  hauteur, Organ et Sax appliquent un **ratio** (une roue d'orgue ou un roseau ne
  se transpose pas par addition de demi-tons).
- Quand l'instrument est charge **apres** le reglage, il faut repousser la plage :
  `MixerViewModel` le fait dans le callback `onLoaded` de `AeInstruments`.

## 17. Les notes Æther et l'UI : un miroir, pas un rejeu

Les notes passent par la file native (`playMidiNote`/`stopMidiNote`) : le son est
bon, mais **aucun listener Kotlin ne voit le canal** (les callbacks MIDI ne portent
que le nom de note). L'UI d'un instrument Æther resterait donc inerte.

- `AudioEngine.onNoteRoutedListener(channel, note, velocity, on)` est le point de
  passage unique (USB MIDI, clavier virtuel, pads).
- Le ViewModel le branche sur `AeManager.mirrorNoteOn` / `mirrorNoteOff` : ces
  methodes mettent a jour `held` / `lastNote` **uniquement**.
- **Ne pas** y appeler `noteOn`/`noteOff` : la note partirait une seconde fois au
  moteur et doublerait la voix.
- Le sustain est respecte sans code special : pedale baissee, l'app n'emet pas le
  NoteOff, donc aucun `mirrorNoteOff` n'arrive et la touche reste allumee.

## 18. Bonnets de fader : utiliser `FaderThumb`, pas le SVG

Le cap des faders de piste est le composable Kotlin `FaderThumb`
(`ui/components/FaderThumb.kt`). `AeFader` (fenetre Leads) l'utilise aussi. Il est
**portrait** (ratio 1327/1657) : pour remplacer un ancien cap 38x26, garder le
**centre** du cap sur la valeur et laisser la boite changer de taille, sinon la
course du fader se decale. `FaderThumb` porte deja sa barre LED — ne pas en
dessiner une seconde dessous.

## 19. Etat d'un instrument Æther : conserver, ne pas remettre a zero

`AeManager` garde les reglages d'un instrument **decharge** dans `savedState`
(cle = `strip to AeInstrument`) : valeurs de knobs, index de preset, page.

- `unload(strip)` **enregistre** l'etat avant de liberer le moteur.
- `load(strip, inst)` **restaure** cet etat au lieu du preset 1 si une sauvegarde existe.
- Consequence attendue : fermer la fenetre, changer d'instrument, puis revenir rend
  exactement ce qui avait ete laisse. Ne pas « simplifier » en repartant du preset 1 :
  c'est une regression que l'utilisateur voit tout de suite.

### Double-tap sur un knob
`Modifier.aeKnobInput(c, idx)` (dans `AeControls.kt`) fait les deux gestes : le drag
regle la valeur, le **double-tap** la remet a `AeManager.presetValue(strip, idx)` —
la valeur du **preset courant**, pas une constante. Si le preset change, la cible du
reset suit.

### Master et instruments Æther
Une source Æther rend **hors** FluidSynth : `safeSetMasterVolume` ne la touche pas.
`setTrackVolume(0, …)` et `ensureEngineStarted()` poussent donc aussi
`safeFxStripGain(FX_STRIP_MASTER, vol)`. Si un jour le master ne bouge plus un
instrument, verifier **cette ligne** avant de chercher ailleurs.

### Fenetre soundfonts : quitter l'instrument
Cliquer une **categorie** soundfont dans la colonne de gauche remet `aetherTab = false`.
Sans ca, les cartes Æther restaient affichees et la fenetre semblait bloquee.

## 20. Travailler en parallele : `tools/conv_sync.py` en pratique

`python3 tools/conv_sync.py` interroge l'API Cloud sur la conversation parallele
(`dde6dffb48c5475a931918c2e3ff38f9` par defaut) et affiche ce qui a change depuis le
dernier appel. Le watermark est dans `tools/.conv_sync_state.json` (gitignore).

Sequence recommandee, **a chaque modification** :

```bash
python3 tools/conv_sync.py            # 1. avant : l'autre agent a-t-il bouge ?
# ... travail ...
python3 tools/conv_sync.py            # 2. apres
git fetch origin main && git rev-list --left-right --count HEAD...origin/main
```

- `--full` rejoue tout le flux, `--since <ISO>` part d'une date, `--json <f>` exporte.
- Si l'autre conversation touche **le meme fichier** : s'arreter et demander, ne pas
  ecraser son travail.
- Le remote peut avancer (uploads manuels de l'utilisateur). Avant de commiter :
  `git fetch origin main` et comparer. `git merge-base --is-ancestor <sha> HEAD`
  dit si un commit distant est deja dans l'arbre.
- **Ne pas pousser** sans demande explicite (regle AGENTS.md).

## 21. Regles apprises (session 2026-10-27, suite)

### Le verre ne se voit que sur une base opaque
Symptome : « le verre depoli s'applique partout sauf sur tel ecran ».
Cause : `Modifier.glassRow(...)` empile une **veil translucide** ; posee directement
sur le degrade du panneau, elle ne se distingue pas. Regle : toute ligne/carte
frostee passe d'abord une **couleur opaque** (`.background(Color(0xFF1E2637))`),
puis la veil. Verifie sur les lignes Presets et les cartes de la fenetre patches.

### Une entree de fenetre doit etre immediate
Un `fadeIn(tween(220))` + `scaleIn` sur une fenetre instrument se lit « ca rame »
et non « c'est fluide ». Regle : **entree sans animation**, sortie adoucie
(`fadeOut` + `scaleOut`). Ne pas mettre d'animation d'entree sur un panneau modal.

### `LocalAeScale.current` est `@Composable`
On ne peut pas l'appeler dans un lambda `pointerInput { }`. Lire la valeur **avant**
le `pointerInput` et la capturer. Erreur compile exacte :
`@Composable invocations can only happen from the context of a @Composable function`.

### Un selecteur a N crans se draggue, il ne se clique pas
Un cycle par clic (`(step+1)%N`) demande jusqu'a N-1 appuis : l'utilisateur le
ressent comme « il faut forcer ». Un drag vertical (`awaitEachGesture` + `drag`) est
le comportement attendu, identique aux potards.

### Caches indexes par chemin : purger a la suppression
`deleteSoundFontFile` retirait le fichier des listes mais laissait
`sfParsedPresets` / `sfFavorites` / `sfCategoryOverrides` indexes sur son nom.
Regle generale : **toute map derivee d'un fichier doit etre purgee quand le fichier
part**, sinon la memoire ne redescend jamais pendant la session.

### Le dechargement de patch est deja en place
Avant de chercher une fuite au changement de patch : `7f74d68` a introduit
`SnapshotLogic.retainedSoundfontPaths` / `slotsToRelease` et la purge des ids dans
`selectPatch`. Verifier `git merge-base --is-ancestor 7f74d68 HEAD` avant de
re-implementer.

### Veille de la conversation parallele
`python3 tools/conv_sync.py` affiche ce qui a change depuis le dernier appel
(watermark `tools/.conv_sync_state.json`, gitignore). A lancer avant/apres chaque
modification. Au 27/10, la conversation `dde6dffb…` est **en retard** sur `main`
(dernier evenement 26/10) : ses commits sont deja des ancetres de HEAD.
## 19. Assets UI (pianos, strings) : dimensionner sur l'affichage, pas sur la source

### La regle

Un fond d'interface n'a **jamais** besoin de plus de pixels qu'il n'en affiche.
Les generateurs (Qwen, Gemini) livrent du 2736×1520 ; c'est 3,6× la resolution
utile. Ce surplus ne se voit pas, il se paie.

### Comment on connait la resolution utile

Le preview (`app/assets/Æther Pianos & Strings · preview.html`) est un **canvas
WebGL**, pas une image CSS. On mesure au CDP sur le vrai rendu :

```bash
# chromium --remote-debugging-port=9334 --remote-allow-origins=*
# puis Runtime.evaluate sur canvas#gl : clientWidth/clientHeight et width/height
```

Resultats mesures :

| Grandeur | Valeur |
|---|---|
| Canvas telephone | 500 × 230 CSS |
| `devicePixelRatio` | plafonne a **1,5** |
| **Resolution utile telephone** | **750 × 345 px** |
| Filtrage de texture | `LINEAR` **sans mipmaps** |

Le filtrage est le point decisif. Sans mipmaps, une texture plus grande que sa
zone d'affichage est sous-echantillonnee en lineaire : elle **ajoute de
l'aliasing**. Au-dela de 750 px, on paie des octets pour une degradation.

### Paliers et format

| Palier | Largeur | Usage |
|---|---|---|
| `_750` | 750 px | telephone (500 CSS × 1,5) |
| `_1500` | 1500 px | desktop 2× |

Pas de 3× : le DPR est plafonne a 1,5, un palier 3× ne serait jamais lu.

**AVIF, pas WebP.** A qualite egale (SSIM ≥ 0,985) : WebP q80 @750 = 45,7 Ko /
0,967 ; AVIF q80 @750 = 64,5 Ko / **0,986**. L'AVIF est ~30 % plus petit a
qualite egale, et nettement meilleur a taille egale.

### La chaine

```bash
pip install pillow numpy scikit-image
python3 /workspace/dl/tools/make_all.py        # 15 instruments × 2 paliers
python3 /workspace/dl/tools/build_html.py SRC OUT   # reinjecte dans le HTML
```

`uikit.py` porte la logique, et deux details comptent :

- **`reference(src, width)` vise la source pleine resolution** vue a la taille
  d'affichage. Si on comparait l'image deja reduite a elle-meme, la metrique ne
  dirait rien.
- **`pick(...)` cherche la qualite la plus basse** qui tient le SSIM cible. La
  qualite fixe gaspille ; le seuil s'adapte au contenu.

### Resultats

| Lot | Avant | Apres |
|---|---|---|
| HTML complet | 11,61 Mo | **0,69 Mo** |
| 15 instruments × 2 paliers | 61,6 Mo (PNG) | **4,25 Mo** |

Tous a SSIM ≥ 0,985 mesure a la taille d'affichage. Cible < 10 Mo tenue avec
14× de marge.

### Pieges

- **`devicePixelRatio` est plafonne a 1,5** dans le preview. Ne pas dimensionner
  sur un DPR de 3 en croyant bien faire.
- **Toujours mesurer a la taille d'affichage.** Comparer un 750 px a un 2736 px
  donne un SSIM artificiellement bas qui pousse a sur-dimensionner.
- **Verifier que le navigateur decode vraiment.** Un `data:image/avif` peut etre
  accepte sans etre decode : `img.decode()` puis `naturalWidth > 0` tranche.
- **Les images du preview ne sont pas celles du depot.** Le HTML embarque ses
  propres images (NCC 0,41 avec les photos fournies). Apres remplacement, un
  ecart de luminance n'est pas un bug : corréler avec l'asset (0,72 = affiche,
  0,39 = non affiche) avant de conclure.
- **`/tmp` est efface a chaque crash sandbox.** Re-telecharger les sources Qwen
  et Gemini.

### Mappage Qwen (ordre du recap)

| Fichier | Preset |
|---|---|
| `raw_01` / `raw_02` / `raw_03` | Mellow / Lady D / Electric Keys |
| `raw_07` | Solo Violin + Viola |
| `raw_08` | Solo Cello + Bass |
| `raw_09` | Section |
| `raw_10` | Orchestra |
| `raw_11` | Pizzicato |
| Gemini | Pad (aurora cyan/violet) |

`raw_05` et `raw_06` sont une **premiere tentative rejetee** (controles sur les
cotes) : ne pas les reutiliser.

### Etat

30 fichiers AVIF dans `app/src/main/assets/instruments/` (4,4 Mo). Le HTML de
reference est dans le depot (commit `f731fb4`). Les **strings sont encore
proceduraux** dans le HTML : les 6 images ne sont pas branchees, a faire quand
l'UI strings sera portee en Compose.

## 20. Previews separes (Piano / Strings) et dosage des animations

### Deux fichiers, une seule source

`app/assets/Æther Pianos · preview.html` et `Æther Strings · preview.html` sont
**generes** depuis `Æther Pianos & Strings · preview.html` :

```bash
python3 tools/uiassets/split_preview.py
```

Ne pas editer les deux fichiers produits : editer la source, puis regenerer.
Le script passe par `sub(html, ancien, nouveau)` qui **asserte** que l'ancre
existe et est unique. Une ancre perimee leve immediatement au lieu de produire
un fichier silencieusement inchange — c'est la garantie qui compte ici.

### Ce que chaque fichier change

| | Piano | Strings |
|---|---|---|
| Onglet | `PIANO` seul | `STRINGS` seul |
| Slots | 9 (un par image) | 6 images pour 8 presets |
| Scene | photo | photo (`modeT=0` en dur) |
| Fond | `setBg(PM[i].id)` | `setBg(SM[i].id)` |
| SVG violons | conserve | **bloc retire** |

Le bloc `/* ---------- violin family` ne sert qu'a la scene procedurale. Comme
Strings ne la montre jamais, le garder ne faisait que rasteriser 4 canvas de
720 px au demarrage, pour rien.

### Verifier un mappage : lire la cle liee, pas l'image

`bgB` contient la cle de texture effectivement liee. Comparer `bgB` a l'id du
preset est **decisif** (8/8 strings, 9/9 pianos).

La correlation d'image, elle, ne l'est pas : toutes les pistes partagent une
meme composition sombre avec un centre clair, et les marges entre la bonne et
la deuxieme piste tombent a **0,002**. Un classement par NCC designait
`piano_lady` comme gagnant pour 6 pistes sur 9. Ne pas conclure d'un NCC.

### Dosage des animations

| Element | Avant | Apres |
|---|---|---|
| Propagation de l'onde | `.30,.13` | `.16,.07` |
| Amortissement | `.9925` | `.9880` |
| Deplacement image | `g*.07` | `g*.035` |
| Specular | `*sp*.5` | `*sp*.26` |
| Goutte ambiante | `.55+rand*.9` | `.26+rand*.44` |
| Goutte touche | `.5+vel*.9` | `.24+vel*.44` |
| Gravite | `+=40*dt` | `+=(p.vy<0?40:5)*dt` |
| Cadence | `petal:3.2` | `PRATE.petal=1.6` |

Mesure : particules 30,8 -> 15,7 (−49 %). Bruit de fond des vagues 0,149 ->
0,059. L'onde d'une goutte de reference retombe a 0,085 a t=3 s (0,142 avant).

### Le piege : normaliser la moyenne d'une loi non lineaire

Le spawn « vraiment random » utilise un intervalle a queue lourde :

```js
nextP=(.25+1.75*Math.pow(u,2.4))/PTAIL/(PRATE[pType]||1.5)
```

`PTAIL` vaut `0,76471` et **n'est pas cosmetique**. La moyenne de
`.25+1.75*u^2.4` est `0,25+1,75*E[u^2.4]`, et `E[u^2.4] = 1/(2,4+1) = 0,29412`.
Sans la division, la cadence reelle valait `1,6/0,7647 = 2,09` au lieu de 1,6 :
**−35 % au lieu des −50 % demandes**. La mesure l'a montre (30,8 -> 19,2 avant
correction, -> 15,7 apres).

Regle generale : des qu'on applique une fonction non lineaire a un tirage
uniforme, recalculer `E[f(u)]` et le diviser. Un reglage « qui a l'air bon » a
l'oeil peut etre faux de 15 points.

### Fichiers

- Generateur : `tools/uiassets/split_preview.py`
- Capture par preset : `tools/uiassets/cap_presets.py`
- Planches de contact : `tools/uiassets/sheets/`

## 21. Developpement en parallele avec un autre agent (veille)

Deux conversations OpenHands travaillent le meme depot. Chacune pousse sur `origin/main` ;
il faut donc **rebaser/merger avant chaque session** et **verifier ce que l'autre a fait**.

```bash
python3 tools/conv_sync.py --all      # ce que chaque pair a produit depuis le dernier check
```

`tools/conv_sync.py` porte une table `PEERS` (nom, id de conversation). `--all` les
parcourt, le defaut est le pair actif. Le watermark vit dans
`tools/.conv_sync_state.json` (gitignore). **Le lancer avant et apres chaque modification.**

Pieges verifies :

- **Les deux agents ecrivent dans `AGENTS.md` et `GUIDE_AGENT.md`** : c'est la seule source
  de conflit de merge. Les sections sont additives -> garder les deux cotes.
- `git fetch` marche avec `$GITHUB_TOKEN` ; le token en dur dans l'URL du remote expire.
- Ne jamais `checkout --theirs` sur ces docs : cela effacerait le travail local.
