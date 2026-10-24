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
| Audio Kotlin | `model/AudioEngine.kt`, `audio/NativeAudioBridge.kt` |
| Audio natif | `cpp/audio_engine.{h,cpp}`, `cpp/soundfont_engine.{h,cpp}`, `cpp/native-lib.cpp` |
| FX Aether (moteur) | `cpp/aetherfx/*.h` (header-only, portable) |
| FX Aether (UI) | `com/aetherkeys/fx/ui/*` + `com/soundstage/mixer/ui/fx/*` |
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

---

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
