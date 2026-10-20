# AGENTS.md — NewKeys- (SoundStage Mixer)

Mémoire persistante du dépôt. **Toujours vérifier un nom ici avant de l'utiliser** :
plusieurs briefs ont référencé des symboles inexistants, ce qui a causé des
malentendus coûteux.

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

## Symboles inexistants (ne pas les chercher)

Un seul nom de brief s'est révélé faux :
`loadSoundfontToSlot` — le vrai est `loadSoundFontForSlot(slotId, path, ...)`
(`viewmodel/MixerViewModel.kt:3112`).

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
