# NewKeys- / SoundStage Mixer — memoire

**Lire AGENTS.md d'abord** (pieges + decisions, charge auto). Ceci est l'index.
Detail par session : `.openhands/memory/YYYY-MM-DD.md` (jamais charge auto).

## Regle d'or : un outil de reconstruction se committe
Le decodeur NCW a ete perdu DEUX fois (il vivait dans `/tmp`, et
`tools/pianopack/build_newstage.py` l'importait de la). Tout script qui sert a
reconstruire un livrable va dans `tools/`, jamais dans `/tmp`. Verifier ses
imports : `grep -n "sys.path.insert" tools/**/*.py`.
Le decodeur vit maintenant dans `tools/kontakt/ncw_decode.py` (commit `106f93d`).


## Mode economique (demande utilisateur, prioritaire)
- Toute commande sort <= 10 lignes : `cmd > /tmp/x.log 2>&1; tail -3`. Jamais de
  `ls -la`/`grep`/`cat` non filtres, jamais de heredoc de script (il revient en
  entier), jamais de listing complet. Mesures ecrites sur disque, lues par agregat.
  Une conversation qui gonfle coute a CHAQUE tour suivant.

## Pieges couteux
- « Compile + pousse » ne prouve PAS qu'une UI a bouge : verifier le fichier qui
  rend la fenetre + capture Roborazzi. (2026-11-03 : 3 commits moteur, meme UI.)
- **Roborazzi n'ecrit un PNG qu'avec `recordRoborazziDebug --tests ...`**, et il
  faut `--rerun` sinon UP-TO-DATE. `testDebugUnitTest` seul ne reecrit rien.
- `AeChip(c,idx,i,n)` = i/(n-1) ; le moteur sax mappe `lround(p(0)*3)` sur 4 types :
  retirer un chip impose de GARDER n=4.
- Sandbox vierge a chaque session : reinstaller JDK 21 (trixie n'a pas de 17),
  SDK a /workspace/android-sdk sans JDK. Roborazzi n'ecrit un PNG qu'avec
  `recordRoborazziDebug` (jamais `testDebugUnitTest` seul, jamais sans --tests).
- 401 « token_not_found_in_db » = proxy LiteLLM, pas le depot. Sortir net, pas
  de retry. Veille via dump local, zero reseau.
- **Mode economique (demande utilisateur) : toute commande sort <= 10 lignes.**
  `cmd > /tmp/x.log 2>&1; tail -3`. Jamais de `ls -la`/`grep`/`cat` non filtres,
  jamais de dump de script par heredoc (il revient en entier), jamais de listing
  complet. Mesures ecrites sur disque, lues par agregat. Une conversation qui
  gonfle coute a CHAQUE tour suivant.
- **Courbe `FreqLog` = balayage log de la plage du parametre**, `reel = min*(max/min)^n`,
  jamais une constante. La formule fixe `20*1000^n` ignorait min/max et annoncait
  6860 Hz pour un reglage declare 100..1000 Hz (BassFreq, + les 3 freq d'EQ). Deux
  copies a garder d'accord : C++ `aetherfx/fx_registry.h` et Kotlin
  `mixer/fx/FxRegistry.toReal`. Banc d'invariant : `tools/design/regcheck.cpp`.
- `grep -r` depuis `app/` ramasse `app/build/kspCaches/*` : une seule ligne de
  plusieurs centaines de Ko. Toujours `--include=*.kt`/`*.h` ou exclure `app/build/`.
- **Un banc doit echouer sur une faute connue** avant d'etre cru : `regcheck` remis
  sur l'ancienne formule sort 90 echecs. Un banc qui passe toujours ne prouve rien.
- Sandbox vierge a chaque session : JDK 21 a installer (trixie n'a pas de 17) ;
  SDK dans `/workspace/android-sdk` (sans JDK). `restore_env.sh` est CASSE.
- 401 « token_not_found_in_db » = proxy LiteLLM, pas le depot : sortir net.
- Ne JAMAIS declencher de workflow GitHub (release.yml = workflow_dispatch only).
- Un commit non pousse = perdu (wipe du sandbox).
- **Pack multi-couches : normaliser PAR COUCHE**, jamais globalement (sinon courbe de
  velocite plate/NON monotone). Fondu entre prises de la MEME source : LINEAIRE, pas
  cos/sin.
- **Pack heterogene** : une couche = une RACINE distincte. Forcer une note a lire
  toutes ses couches sur une racine la remplace par la plus faible (-18 dB, timbre
  detruit). Un banc a fenetre courte (250 ms) invente un « battement » inexistant.
- **Un filtre = un objet.** Trois appels `setLowShelf`/`setPeaking`/`setHighShelf`
  sur le MEME `Biquad` : seul le dernier survit. Mesurer un EQ sur du bruit blanc a
  bas niveau, jamais sur un accord.
- **Un clic qui suit chaque note n'est pas forcement un clic ajoute** : mesurer le
  transitoire d'attaque avant de retirer quoi que ce soit.
- Generateur de demo : rendre jusqu'au PROCHAIN evenement, pas par blocs fixes
  (sinon les attaques derivent). Verifier une ligne par controle spectral (FFT 16384,
  fenetre 150 ms) avant de publier.

## Environnement (verifie 2026-11-04)
- **Le depot vit a la RACINE `/workspace/project`.** SDK : `/workspace/android-sdk`
  (`ANDROID_HOME` non defini), JDK `/workspace/jdk/jdk-21.0.13+9`, Gradle 9.3.1.
  Build natif complet ~1m15. `app/app/` est un artefact de build parasite.
- `serve.py` doit demarrer APRES `apk_page.py` (sinon 404 sur /aether.apk). Livraison
  port 12000, `/workspace/dl/pub` -> https://work-1-.../demos/

## Session 2026-11-04 (New 2) — persistance de l'instrument Æther
- **`TrackChannel.aether` n'existait pas** (le champ n'etait que sur `TrackSnapshot`).
  Ajoute : c'est le porteur de l'instrument dans l'etat vivant.
- **Codec partage `model/AetherJson.kt`** (nom d'enum, pas l'ordinal ; `null` ->
  `JSONObject.NULL` ; `fromJson` d'un bloc vide -> `null`).
- **Un setlist ne portait pas `soundSource`** alors que `selectScene` le lit : d'ou
  la redemande du choix Æther/SF2 a chaque tap. Le writer du setlist ecrit
  desormais `soundSource` ET `aether` (les deux, sinon l'un des deux revient vide).
- **`syncAetherIntoState()`** (dans `persistCurrentStateDebounced`, a cote de
  `syncActiveSnapshot`) estampille les instruments vivants sur les pistes ; sans lui
  le writer ecrit `null`. Au chargement, `selectScene` rejoue
  `AeInstruments.manager.restoreFromSnapshot` et `unload` les pistes sans instrument.
- **Regle** : un instrument Æther vit dans `AeInstruments` + le moteur (par-process).
  Tout ce qui doit survivre doit etre recopie sur `TrackChannel` puis rejoue au load.
- Verifier `git log` AVANT d'implementer un brief : REQ1/REQ2 etaient deja pousses.
  Detail : `.openhands/memory/2026-11-04.md`.

## Sheets : Sets et fenetre flottante (2026-11-04)
- **Un Set est un DOSSIER** : `Setlist(id, name, sheetIds, colorArgb, createdAt,
  updatedAt)` dans `.library.json`. Le supprimer ne supprime AUCUNE feuille.
  Le filtrage vit dans `LibraryUiState.visibleSetlists()` / `visibleSheets()`
  (`LibraryFilter.{All,Favorites,Sets,OfSetlist}`).
- **`LibraryCard`** (`ui/home/HomeScreen.kt`) est le gabarit commun des cartes de
  bibliotheque (fond, sheen XML, tranche, ombre, rebond) ; `SheetCard` et
  `SetCard` n'apportent que le contenu. La preview des accords se cale sur la
  largeur reelle de la carte (`previewWidthFor`).
- **Flottant** : pastille fine en bas = hauteur + mode « remplir » (les sections
  suivantes s'affichent tant qu'elles entrent) ; pastille fine a gauche =
  largeur, et `fontScale = cardW / FloatingWidth` fait grandir les polices.
  Bornes `FloatingMinWidth` 260 dp / `FloatingMaxWidth` 620 dp.
- **Piege de test** : le nom d'un Set est sur la carte ET sur la pastille de
  filtre -> 2 noeuds. Taguer la carte (`set-card`). Et `OverlineLabel` passe en
  MAJUSCULES : chercher `SETS`, pas `Sets`.
- **Piege mesure** : la carte de Set prenait `sheetIds` (UUID) comme texte au lieu
  de resoudre les titres. Toujours resoudre un id vers son titre a l'affichage.
- **Reglages Sheets = un PANNEAU, pas un ecran** (commit `e2688d4`, 2026-11-04).
  `SheetsSettingsScreen` faisait `SheetsPageBackground{fillMaxSize}` et recouvrait la
  bibliotheque. Copie de Setlists (`SlSettingsPanel`) : `Box(align TopEnd)
  .padding(top=60,end=18)` + voile `detectTapGestures` qui ferme, croix 28 dp,
  materiau `.sheetsPanel()` (ombre 14 + degrade PanelTop->PanelBottom + liseré 0,10),
  apparition `.sheetsAppear()` (fondu + montee 8 dp + scale 0,96->1, 220 ms).
  Mesure : **538 x 334 dp** (Setlists ~540 x 314). Reparti en **2 colonnes de 4
  groupes** pour tenir sans scroll (un `verticalScroll` en 2 colonnes ne reduit rien).
  `SheetsColors.PanelTop/Accent/Well` == `SlColors.*` : aucune conversion de couleur.
  **Regle** : un reglage de page est une pop-up ; copier la forme du voisin qui fait
  deja la meme chose, ne pas en inventer une.
- **Verre des cartes (commit `fe3c5d9`)** : le fond prend la couleur PROPRE de la
  feuille (alpha 0,60 en degrade) au lieu de `lerp(SurfaceRaised, base, 0.30)` ; chroma
  mesure 28,8 -> 68,5 (lignes) et 24,8 -> 34,8 (cartes). L'`ombre` des cartes et des
  lignes est RETIREE (le modele `LibraryCard` ci-dessus la decrivait : c'est fini).
  Liste du bas : 43,5 dp par ligne, apercu d'accords 19 sp.
- **Piege de test** : `SheetsCaptureTest` ecrit les feuilles dans un dossier
  temporaire et la liste suit l'ordre du dossier, qui varie d'une execution a
  l'autre : les couleurs de lignes (teal/rose) **s'inversent** dans
  `sheets_home/sets/inside_a_set.png` (17-27 % de pixels). Pas une regression.
- **Piege de compilation** : `SwitchRow(label, checked, onChange = {...})` —
  `onChange` est un parametre NOMME, pas un lambda final.

## Carte du code Æther
- Fenetres : `AeWindow.kt` dispatch -> `AePianoUi.kt` / `AeSaxUi.kt` / ...
- Banques : `AeBanks` (`AeInstrumentHost.kt`) : `isElectricModel`, `pianoBankId`.
- Moteurs : `app/src/main/cpp/aetherfx/fx_piano.h` (20 params), `fx_sax.h` (23).
- Thread audio : `AudioEngine::onAudioReady` (`audio_engine.cpp`). Contrat : verrou
  `try_to_lock`, zero allocation, zero `LOGI`. `renderDirect()` n'est PAS le callback.
- Presets : `gen_presets.py` doit lire les 20 valeurs piano. `mastering.cpp ... retune`
  cale LEVEL (cible K-weighted mediane). Demos : `tools/demos/audio/` (MP3, moteur reel).

## Fait et a ne pas re-corriger
- **Boucle piano SAINE** (enveloppe de tenue identique boucle active/coupee). Le faux
  sustain venait des regions qui bouclaient dans la partie DECROISSANTE : `flattenLoop()`
  aplatit la boucle et fond le raccord vers la phase AVANT `loopStart`. Fondu de raccord
  porte a 200 ms (« dain dain »). Ondulation residuelle = battement modal de la REVERB,
  intrinseque.
- **« Marteau » sax = transitoire d'ATTaque** (clic de synthese fx_sax.h l.229, bande
  2800 Hz + attaque de l'echantillon). Le `bite` ne pese que 2,6 %. Correctif : attaque
  adoucie (synth 50 ms + fondu echantillon 150 ms).
- **Velocite piano/EP SAINE** : courbe lisse sur 1..127 (~41 dB), aucune inversion.
  Le fondu a puissance constante entre couches (fx_piano.h ~l.1084) corrige le haut.
- **Bulldog Sax** integre (commit 700879f, accord du proprietaire) : l'alto (type 1)
  joue `saxpack_bulldog.bin`, repli `saxpack_alt.bin`. Param 22 « Character » = 1,0 sur
  les 23 presets -> controles de synthese annules. `p(1)` « Subtone » = parametre mort.
- **Bulldog = UN sax, profil ALTO** (centroide 2600-3400 Hz). « enleve le sax de meme
  nature » = l'alto change de source, PAS retirer les autres.
- Case de presets Sax masquee dans `AeWindow.kt` (`!= PIANO && != SAX`).
- **Saccades audio (cc7a152)** : 3 violations temps reel — `resetMasterBus()` tenait
  `mRenderMutex` (drapeau atomique desormais), `resize()` dans le callback (scratchs
  pre-dimensionnes dans `openAndStartStream()`), `LOGI()` du temps de rendu (publie
  dans `mLastRenderUs`, journalise par le thread de controle).
- **Gain de rendu piano (cc7a152)** : acoustiques x1,70 / tine x1,15 dans `fx_piano.h`
  (gain de SORTIE, pas les LEVEL). Pic max 92,8 % sur accord 6 notes v=127, zero ecrete.
- **EQ piano (cc7a152)** : 3 gains dans la colonne de droite (x=735/830/919, y=349,
  taille 0,95), frequence du medium seule au milieu (x=450/524/598). Sans titre.
  Familles espacees de 84 (3 familles).
- **Fenetres Æther** : `SoundfontWindow` (inchangee) + `AetherInstrumentsWindow`
  (`instruments/AeInstrumentsWindow.kt`, popup AETHER). `SoundSourceChooserPopup`
  (popup SOUND_SOURCE) ouvert au clic sur le nom de piste. `addSoundTrack()` ne doit
  RIEN ouvrir (appele en boucle par le bridge LiveUI) ; le « + » passe par
  `addSoundTrackAndChooseSource()`.

## Session 2026-11-05 — FX : 3 slots audio, arp persistant, fenetre reduite (5b9b5d3)
- **Rack = 3 emplacements AUDIO** ; la 4e carte est l'ARPEGIATEUR (MIDI), pas un
  emplacement. Master = 4 audio sans MIDI. `TrackStrip{3,false}` / `master_{4,true}`.
- **L'arp (presence + armement) vit dans le rack** : toute action du rack doit
  l'ecrire, sinon suppression/bypass ne se persistent pas. Restaurer doit
  repousser la GRILLE, pas seulement rearmer le canal.
- **Roborazzi n'ecrit RIEN sans `-Proborazzi.test.record=true`** : mesurer un PNG
  perime fait croire que le code ne marche pas. Verifier le mtime.
- **Mesurer une fenetre = mesurer le PANNEAU**, pas le voile (seuil luminance).
- **3 echecs de suite PREEXISTANTS** : ExampleRobolectricTest (app_name =
  "Æther Keys", test attend "SoundStage"), GreetingScreenshotTest,
  PianoPackFormatTest (pack 160 regions, test attend 68). Hors perimetre FX.

## Session 2026-11-04 (suite) — fenêtre Æther Instruments + pop up de choix
- **Deux fenêtres séparées** : `SoundfontWindow` (inchangée) et
  `instruments/AeInstrumentsWindow.kt` → `AetherInstrumentsWindow`
  (`ActivePopup.AETHER`), coque copiée de SoundfontWindow (660x372, rayon 24,
  colonne gauche 154 dp = pastille seule). L'interface de l'instrument est
  hébergée **dans la même fenêtre** (redimensionnée à 1000x460 via `AeStage`),
  plus d'overlay isolé.
- **Pop up de choix** : `SoundSourceChooserPopup` (`ActivePopup.SOUND_SOURCE`),
  ouvert au clic sur le nom de piste, deux entrées (Soundfonts / Æther
  Instruments), **pas de « soon »**. Il est **à créer** — ne pas le chercher
  dans le repo, il n'existait pas (piège qui a coûté des passes au clone
  précédent : « On créer le pop up on est ici pour CRÉER »).
- La pastille « Æther Instruments » est **retirée définitivement** de
  `SoundfontWindow` (plus de paramètres `aether`/`aetherStrip`).
- Piano MECHANICS : **bas milieu**, `y=348`, `x=370+j*86`, échelle 1,35, sans titre.
  Mesure : colonne de 97 unites, finit a 445 (bord 460, panneaux de verre 430).
- **Cartes de la liste** (`AeRow`, `AeWindow.kt`) : rayon **7 dp** (pas 10 : un fort
  rayon se lit comme une boite dessinee), image en `Crop` pleine case + **3 masques
  `DstIn`** (fondu gauche, feather droit, feather vertical haut/bas) — c'est le
  feather vertical qui fait « reflet » au lieu d'une image collee. Le bouton Power
  est a GAUCHE de la cellule image (reservee par `Spacer(RowImageWidth)`).
- **`addSoundTrack()` ne doit RIEN ouvrir** : le bridge LiveUI
  (`LiveUiLoop.kt:146`) l'appelle en boucle pour grandir le mixer, un popup par
  appel parasite les captures. Le « + » de l'UI passe par
  `addSoundTrackAndChooseSource()` ; le pop up bascule de fenetre via
  `openSourceWindow(popup)` sans recreer de piste.
- Nom du preset Æther sur la plaque de piste : `AeManager.presetNameOn(strip)`
  (le sax renvoie « Sax », il n'a pas d'UI de preset), lu par `ChannelStripV2.kt:95`.
- Récupération après wipe : le dump `tools/.eco/stream_<cid>.json` + rejeu des
  éditions `file_editor` reconstruit un worktree perdu (voir daily log).

## Session 2026-11-04 (New 2, suite) — pistes : 0 piste, source unique, Save, RAM
Commit `e7a6c98`. `compileDebugKotlin` + `assembleDebug` OK (APK arm64 54,1 Mo).
- **Un setlist neuf ouvre sur 0 piste** : `visibleTracksCount` passe de 1 a **0**
  (createNewProject / createSetlistFromScreen / patch vierge) et la coercion
  `MixerScreen` devient **0..8**. **Piege** : 0 doit etre ECRIT dans le JSON du
  setlist, sinon le lecteur retombe sur `count { it.isEnabled }` = 8.
- **Choix de source demande UNE fois** : `TrackChannel.soundSource`
  ("" | "sf2" | "aether", persiste). Tap = ouvre la fenetre memorisee ;
  **appui long = rouvre le choix** (bascule SF2 <-> Æther).
- **Bouton Save Æther** : `SaveDiskBtn` -> `SaveButton`, pastille libellee (l'icone
  disquette se lisait comme une decoration). Piano x=916, autres x=590.
- **RAM dechargee — cause moteur, pas Kotlin** : `setInstrument(-1)` ne fait que
  RETIRER l'objet ; le collecteur `FxChain::collectGarbage()` attend
  `epoch + 3 blocs`, mais `epoch_` n'avance que dans `process()`, et
  `renderFxPath` est **gate par `mFx.hasActiveWork()`**. Plus aucun instrument ->
  plus de process -> **jamais de delete** : la banque (8-12 Mo) restait en RAM.
  Fix : `collectGarbage(force)` + `AudioEngine::fxCollectGarbageNow()` (prend
  `mRenderMutex`) + JNI + `AeHost.releaseNative()` depuis `AeManager.unload()`.
  **Regle** : un collecteur differe par « N blocs » ne tourne jamais quand le chemin
  de rendu est desactive. Forcer la liberation sous le verrou de rendu.
- **Affichage RAM** : lire `Debug.getPss()` (empreinte process reelle). La somme
  `Runtime` + `getNativeHeapAllocatedSize()` rate les banques du moteur, donc
  decharger ne montrait aucune baisse.
- **Fenetres** : zones vides = `clickable` absorbant (Æther + Track Settings) ;
  animation d'entree `scaleIn(0.55f)+fadeIn`. **Piege** : la fenetre est composee
  avec `isOpen` deja vrai -> `AnimatedVisibility` seul peint la taille finale des la
  1re frame ; garde d'une frame (`shown` via `LaunchedEffect(Unit)`).

## Session 2026-11-05 — finition FX Space (branche design/fx-reverb)
- **Captures Roborazzi : les PNG sortent dans `app/app/src/test/screenshots/`** (chemin
  relatif au module), et `-Proborazzi.test.record=true` **ne reecrit rien** si la tache
  est `UP-TO-DATE` : il faut `--rerun-tasks`. Un md5 inchange = cache, pas un bug.
- **Fond bicolore par preset** : un degrade entre deux teintes sombres ne se lit pas
  (243° contre 249° mesures). Il faut l'aplat + DEUX voiles radiaux colores (`h` et
  `h2`) en coins opposes. Verifier la teinte MESUREE.
- **Magma du gros potard pilote par la valeur** : capture `fx_space_knob_low` le prouve
  (noyau 80,9 -> 50,4 de luminance, 21257 -> 4778 px chauds). Braise contenue.
- **Liste deroulante** : dans Compose, un frere declare apres passe dessus — il faut un
  `Box` parent + `zIndex(2f)` + liste opaque. Declaree dans la colonne de la grille,
  elle passe dessous.
- Reference de design : `tools/design/SK_GRAMMAR.md` (Sunday Keys) ; Valhalla dans
  `/workspace/dl/valhalla/`. Livraison : `tools/shots_page.py` + `tools/serve.py` (12000).

## Session 2026-11-05 (suite) — FX : potards PLATS (fin du dome 3D Valhalla)
Branche `design/fx-reverb`, commit `3acac9c`. `main` reste a la conversation Æther
Piano `9c93aa40` — ne pas y pousser.
- **Le potard Valhalla est PLAT.** Mesure sur `room_24.jpg` : disque **uniforme 121**
  sur panneau **52** (rapport 2,3), **sans liseré** (transition franche en 1 px).
  Ni dôme, ni dégradé de corps, ni collerette. Il ne se lit que par disque plus
  clair + aiguille + coin de valeur. **Test** : luminance au CENTRE contre au BORD —
  centre >> bord = dôme (à supprimer), centre == bord = plat.
- **L'aiguille est SOMBRE** sur le disque clair (RGB 52), jamais blanche — corrige
  une note antérieure fausse. L'indicateur de valeur est un **coin plein** au bord
  (rayon 28→57 sur un disque de 54, ~25°), pas un arc d'épaisseur constante.
- `FxKnob.kt` refait plat ; `FxMagmaKnob.kt` : **meme pièce** que les petits
  (`drawFxKnob` partagé, deux échelles). Une « braise » à lobes était une invention.
- `FxFonts.display` = Orbitron (medium/bold) pour le titre de page (ex-Cinzel 27 sp).
- Case preset = **pastille compacte** (26 dp, nom du preset seul), à droite ; la
  liste déroulante est ancrée à droite en 168 dp.
- **Chemin de capture** : le test JVM a `app/` comme cwd, donc `captureRoboImage`
  doit écrire `src/test/screenshots/...` (pas `app/src/...`, qui crée `app/app/`).
- **Roborazzi n'écrit un PNG qu'en `recordRoborazziDebug`.** En `testDebugUnitTest`
  un test qui fait `assertTrue(File(...).exists())` échoue : le fichier n'est pas
  régénéré. Piège reconfirmé (coûte une passe à chaque fois).
- **`FxDesignTest`** (nouveau) exige `@RunWith(RobolectricTestRunner)` : `FxDesign`
  construit ses couleurs par `android.graphics.Color.parseColor` (absent sur JVM nue).
- **Geometrie des cellules** : `FxGeo.CELLS = 3`. A deux colonnes les rangees
  ecrasaient le potard (**52 unites mesurees au lieu de 96**, invisible a l'oeil).
  Le diametre est desormais adaptatif (`BoxWithConstraints` -> plus grand disque qui
  entre). Mesure apres : gros potard **197**, potard de cellule **75**.
- **Environnement** : le snapshot Debian renvoie **404** sur openjdk-21 ->
  télécharger Temurin (`api.adoptium.net`), `JAVA_HOME=/workspace/jdk/jdk-21.0.13+9`.

## Session 2026-11-05 — FX Space : potards plats, cellules, 15 reglages
- **Perimetre** : perazfinage Design des FX uniquement, branche `design/fx-reverb`
  (hors `main`). Fichiers : `AeSpaceScreen.kt`, `FxCard.kt`, `FxDesign.kt`,
  `FxEffectPage.kt`, `FxWindowState.kt`, `FxDesignHost.kt`, `fx_space.h`,
  `fx_registry.h`, les 2 `FxRegistry.kt`.
- **Le 3D est retire** : la carte Slots ne porte plus d'orbe (`FxOrb` = MORT,
  liste noire) mais **six potards plats** du preset (`AeSpaceSlotKnobs`),
  reglables depuis la carte (`setSlotValue`). Le gros potard DECAY est un anneau
  plat + aiguille, pas un dôme.
- **15 reglages** dans l'ordre du moteur ; l'UI pousse par INDEX -> l'accord
  catalogue <-> registre est verrouille par `FxDesignTest` (13/13, JVM pur).
- **Robolectric n'a pas de cache ici** : un test qui l'exige echoue sans reseau.
  `FxDesign` etant sans API Android, le test est JVM pur. **Mais** tout helper
  vivant dans un fichier Compose (ex. `spaceStyleFor` dans `AeSpaceScreen.kt`)
  tire les icones Android et leve `ExceptionInInitializerError` : le deplacer
  dans le catalogue pur.
- **Keystore de debug gitignore** : regenerer par `keytool -genkeypair` (alias
  `androiddebugkey`, pass `android`) sinon `assembleDebug` echoue a la signature.

## Fenêtre FX : nouvelle coque `FxSlotsWindow` (branche `design/fx-reverb`, commit `2867d39`)
- Le Kotlin de DeepSeek a été récupéré **dans le dépôt** (`git show 06826f3:FxSlotsWindow.kt`),
  pas par le lien (CloudFront bloque les IP datacenter). Le lien était mort, le fichier non.
- Modèle : 1 slot MIDI + 3 audio, catalogue dans la fenêtre, éditeur passé par paramètre.
  Catalogue limité à `Arp` (MIDI) et `Reverb`/Æther Space (audio) via la liste `CATALOG`.
- **Piège** : `FxSlotsController.state` est un **StateFlow** → `collectAsState()` obligatoire,
  `state.value` en composition ne recompose pas (catalogue/éditeur muets).
- Ancienne fenêtre + résidus supprimés (12 fichiers) ; le design des éditeurs conservé
  (`AeSpaceScreen`, `FxDesign`, `FxFonts`, `FxIcons`, `FxC`).
## Sheets — flottant + bibliotheque (New 2, 2026-11-04, commit 88f01af)
- **Imports Compose non-wildcard** : `Modifier.border`, `Spacer`, `fillMaxHeight`,
  `FontWeight` doivent etre importes nommement. `assertExists()` est une methode, pas
  un import (sinon Unresolved reference).
- **Refactor par script** : couper une plage par index peut DUPLIQUER une fonction si
  le marqueur de fin differe. Toujours `grep -c "private fun X"` apres la coupe.
- **Cartes de bibliotheque** : `SheetCard` (172 dp, tranche 6 dp, accords sur rail 29 px,
  tonalite/BPM/sections/date) + `SheetRow` (62 dp). Bascule grille/liste dans l'en-tete
  (`LayoutToggle`, icones `SheetsIcons.Grid`/`List`, ajoutees).
- **Flottant** : X en haut a droite, cadenas en bas a gauche, poignee au centre, pastille
  de section en bas a droite. Liste des sections DANS la carte (`InCardSectionPicker`),
  plus de popover externe. Balayage horizontal entre sections. Hauteur redimensionnable
  (150..conteneur-bottomInset), pas d'`animateContentSize` (il lisse la poignee).
- **Tap** : `combinedClickable(onClick = editeur, onDoubleClick = flotter, onLongClick = menu)`.
- **testTags** : « resize-grip », « section-strip », « section-picker ». Le texte « Verse »
  existe en double -> `onNodeWithText` echoue, utiliser le tag.
- **Voile de liste** : teinte de l'encre (`isLightInk` = luminance < 0.5).
- **Sandbox** : `apt-get update` AVANT `install openjdk-21-jdk-headless` (sinon
  « Unable to locate package »). tesseract-ocr introuvable sur ce miroir.
- **Bibliotheque = cartes 4 par ligne, plus de bascule liste** (decision 2026-11-04,
  commit da60455). `painterResource()` REFUSE un `<layer-list>` ; la profondeur de
  carte est un `<vector>` avec `<gradient>` par `<aapt:attr>` (`sheet_card_sheen.xml`).
- **Tonalite de carte** : plaque noire 90 % a texte blanc (lisible sur toute teinte).
  Titre en Plus Jakarta Sans (`SheetsType.CardTitle`), plus la police manuscrite.
- **Rebond des cases** (convention app, cf. `TrackBottomRow.kt`) :
  `collectIsPressedAsState` -> `animateFloatAsState(0.94f, spring(0.55f, 900f))`
  en `graphicsLayer`, + `HapticFeedbackType.TextHandleMove` au tap, `indication = null`.
- **Travail desormais dans le reel sur `main`**, plus de maquette JSX comme etape.

## Session 2026-11-05 — section KEYBOARDS (f7fb0c4)
- **Keyboards = SECTION, pas un nouvel instrument de bout en bout.** Meme moteur
  `fx_piano.h` (fxId 17) ; `AeInstrument.KEYBOARDS` est une 2e vue du meme moteur.
  Index de preset LOCAL a la section (0..3) -> GLOBAL (8..11) via
  `AeSections.modelIndex` (`AePianoUi.kt`). Le moteur cuit le seuil en `MODEL = idx/8f`
  donc on TRADUIT, on ne renumerote jamais.
- **Regle** : tout `== AeInstrument.PIANO` devient `AeSections.isKeyboardLike(...)`.
  Les `when` non exhaustifs servent de checklist : le compilateur liste les sites.
- **Tests** : le suite Robolectric COMPLET se bloque sur `GreetingScreenshotTest`
  (AppNotIdleException, worker qui ne rend jamais la main). Lancer CIBLE :
  `./gradlew :app:testDebugUnitTest --tests "com.soundstage.mixer.AeManagerTest" --tests "...AePianoCaptureTest" ...` (~40 s).
- **Echecs preexistants** (ne pas imputer) : `ExampleRobolectricTest` (app_name
  "SoundStage" vs "Æther Keys"), `PianoPackFormatTest` (attend 68 regions/44100 Hz,
  pack livre = 160). Perimes.
- **Memoire A/B presets** : `AePreset.slot`, persiste `"s"`, absent = A.- **PIEGE PAGE DE REVUE (cout d'une passe perdue)** : si la page est servie dans un
  SOUS-dossier (`/new3/index.html`), les `src`/`href` doivent etre RELATIFS AU
  SOUS-DOSSIER (`src="img.webp"`), jamais prefixes par le nom du dossier
  (`src="new3/img.webp"` -> le navigateur demande `/new3/new3/...` = 404, page
  « vide »). Verifier avec `curl -o /dev/null -w '%{http_code}'` sur CHAQUE image
  (les vignettes ET les liens « plein ») et lire le log du serveur : un 200 sur
  `index.html` ne dit RIEN sur les images. `python3 -m http.server` journalise
  chaque GET dans `/tmp/srv.log`.

## Session 2026-11-05 (2e) — consoles Organ/Leads/Synth (90d487c)
- **Claviers virtuels retires** de Organ/Leads/Synth ; la bande reprise par un
  pupitre de sortie (vumetre, registration, couches, formes d'onde).
- **FaderThumb recolorable** (`capTop`/`capBottom`, `showLed`) : son profil de
  luminance est sombre, il faut le NORMALISER puis le projeter entre variante
  claire et variante sombre — sinon blanc et noir se ressemblent.
- **`AeRealism.kt`** : bois, metal brosse, plastique, knob tourne, vis, vumetre,
  barres de couches. Procedural et deterministe (pas de bitmap).
- **Pieges Compose** : `size` de layout masque `DrawScope.size` dans un Canvas
  (utiliser `this.size`) ; `Brush.linearGradient` prend `start`/`end`, pas
  `startX`/`endX` ; imports a ne pas oublier : `lerp`, `nativeCanvas`, `toArgb`.
- **Param 29 (BendRange) du synth** : retire VOLONTAIREMENT (doublon Track
  Settings), ne pas le remettre. Aucun parametre moteur cache a reveler.
- Verifier en MESURANT l'image (au bon endroit) ; 74 tests, 0 echec.

## Session 2026-11-06 — Organ : mesurer la bonne zone (2bf3acd)
- **Le « knob du milieu » = le dial vibrato**, pas une tirette. Localiser par
  coordonnees avant de mesurer, sinon faux diagnostic garanti.
- Le « reflet blanc » venait du **SVG KNOB_METAL** (#F6F7FA), pas seulement de
  `drawPlasticKnob` (supprimee : plus d'appelant).
- Tirettes : rail **encastre aux deux bouts** (pied au-dessus, cran en dessous).
  Reference d'origine : `app/assets/aether-instruments-final.zip` -> AeControls.kt.
- Le **titre d'instrument** et le **nom de preset** sont deux choses : les deux
  s'affichent. La pilule doit lire le `manager` recu, PAS le singleton `AeInstruments`.
- **Jamais deux controles pour un meme parametre** (chips ROTARY = duplication du p25).
- Page de revue : `/workspace/dl/pub/new5/index.html` (sert `/workspace/dl/pub/`).

## Session 2026-11-06 (suite) — Organ : reference image + JSX
- **L'agent ne recoit AUCUNE image dans le chat.** L'utilisateur depose via le web GitHub
  (« Add files via upload » sur `origin/main`, `app/IMG_*.jpg`). Recuperer avec
  `git show origin/main:<chemin> > /tmp/ref.jpg` ; trouver via
  `git log --all --diff-filter=A --name-only --since="10 days ago" | grep -iE '\.(png|jpg)$'`.
- **Reference organ (1080x513)** : cadre **rouge carmin #B81436** (« pas rouge » = a
  remplacer par du bois), ardoise bleu 210 deg, rail acier **#F8F9FD** 10 px, capot
  **#232323**. 9 tirettes : slot **32**, pas **53**, 25 crans (6,4 px). 2e rangee =
  tirettes **abaissees** a corps **bois**. Palette sourde (value med 0,37) : booster
  saturation/contraste AVANT d'analyser la structure.
- **Rendu JSX headless** : `tools/design/render_jsx.sh` (/tmp/jsx, react18 + esbuild
  `--jsx=automatic` + chromium `--headless=new --screenshot`). `file://` masque les
  erreurs JS en « Script error. » -> servir en http pour lire la vraie erreur.
  Mesure : `tools/design/{refscan,refmap,refpanels}.py`.
- Branche `test/organ-design` (commit `2cdcf2d`) ; page de revue `/workspace/dl/pub/organ/`.

## Session 2026-11-07 (New 3) — faders Rotary sur FaderRail + purge ancienne UI (`2d34763`)
- **`FaderRailGeo`** (public, `FaderRail.kt`) = SEULE source de la geometrie de rainure
  (`REF_W=86`, `GROOVE_W=17`, `GROOVE_INSET_Y=36`, `scale`, `grooveInsetY`, `grooveWidth`).
  Tout capuchon se pose avec ces fonctions ; ne jamais recalculer 17/86 ou 36/86 a la main.
- `OrganControls.kt` : `OrganVFader`/`OrganHFader` = rail du mixeur (H = rail couche a 90,
  remplissage bipolaire depuis le milieu). Purge de l'ancienne UI orgue (morte depuis
  `AeWindow.kt:132 -> AeOrganScreen`) : `AeOrganUi.kt`, `AeMetalKnob`, `AeDrawbar`,
  `AeRocker`, `AeKeyboard`, `drawTonewheels`, `drawRotor`, `hash`/`drawWood`/`aeWood`,
  `organ_fader_thumb_h/_v.xml`. **`drawKnobRelief` reste** (lu par `AeGlassKnob`).
- **Roborazzi : `-Proborazzi.test.record=true`** (propriete Gradle). `-D...` ne reecrit
  RIEN (tache SKIPPED, silencieux).
- **Le token du remote `origin` est expire** -> `git push` bloque sur un mot de passe.
  Pousser via `git -c credential.helper='!f(){ echo username=x-access-token; echo password=$GITHUB_TOKEN; }; f' push origin HEAD:<branche>`.

## Session 2026-11-07 — UI orgue du zip integree (a4b5df3)
- **Le zip de l'utilisateur = `app/assets/organ_ui_kotlin.zip`** (commit `56a741f`,
  amiel767, sur `origin/main`), PAS `fx_organ_source.zip`. 6 .kt + 14 drawables +
  generateurs Node. Cible `com/aetherkeys/organ/` (package `com.aetherkeys.organ`).
- `AeOrganUi.kt` = GABARIT (cases vides) ; le zip = rendu reel. `OrganListener`
  = le contrat moteur (drawbars, perc, vibrato, rotary, cabinet, knobs, faders,
  octave, reverb, modele, preset). 16 presets dans `OrganState.kt`.
- **Le zip n'avait jamais compile** (19 erreurs). 4 pieges reutilisables :
  (1) local `val density = LocalDensity.current` masque le Float `density` de
  DrawScope ; (2) `maxHeight` perdu dans une lambda imbriquee (recepteur implicite) ;
  (3) lambda finale `OrganKnob(v) { }` se lie a `modifier`, pas a `onValueChange` ;
  (4) `var x` + `fun setX(..)` = collision de signature JVM -> renommer la fonction.
- Adaptations : `com.aetherkeys.R` -> `com.soundstage.mixer.R` ; `PlexMono` ->
  `res/font/share_tech_mono` (mono du depot). Gen. Node -> `tools/organui/`.
- **Verifie : `:app:compileDebugKotlin` BUILD SUCCESSFUL.** Detail : log 2026-11-07.
- Build : `--offline` echoue (plugin foojay non cache) ; job `&` suspendu par le
  shell -> relancer en avant-plan pour le code de sortie.

## Session 2026-11-04 (4e) — integration orgue (moteur + UI)
- Pont : `instruments/AeOrganBridge.kt` (`AeOrganListener` -> 36 params `fx_organ.h` ;
  `AeOrganScreen(c)`). Vibrato : maquette V1 C1 V2 C2 V3 C3 -> moteur V1 V2 V3 C1 C2 C3
  (`[0,3,1,4,2,5]`). Sans parametre moteur (ecrivent rien) : EQ Mid, Rotary Rise/Fall,
  Depth/Mic, Horn/Drum, Octave, Model. Pas de STOP rotary dans la maquette.
- `AeWindow.kt` : ORGAN -> `AeOrganScreen` ; titre + pastille preset supprimes (le panneau
  occupe toute la scene) ; `startInset=40f` degage la fleche retour.
- `animateColorAsState` = `androidx.compose.animation` (PAS `.core`).
- Perf : rail en `drawBehind` qui ne lit pas la valeur ; remplissage en `graphicsLayer`.
  Thumb = `FaderThumb` natif (20x26 vertical, 18x22 pivot 90). Commit `854aed6`.
- Tests : `OrganParamMappingTest` (10) + `OrganControlsInputTest` (5) verts. Drawbar haut=0 bas=8.
- 3 echecs de suite PREEXISTANTS hors sujet : `ExampleRobolectricTest`,
  `GreetingScreenshotTest`, `PianoPackFormatTest` (pack absent du depot).

## Session 2026-11-04 (4e) — orgue : contrat 46 + poignees de fader
- **`fx_organ.h` : 36 -> 46 params**, tous neutres a 0,5. `AeCore` ORGAN.paramCount=46,
  `discrete` += 42 (Model) et 44 (Octave). Pont `AeOrganBridge` : table de 46 lignes,
  `uiOnly` vide (plus aucun controle mort). Commit `037ea0e`.
- **Poignee de fader ecrasee** : un parent `width(13.dp)` CONTRAINT l'enfant ; `size()`
  y devient 13 dp et FaderThumb, qui centre son dessin, rend un capot minuscule.
  Utiliser `requiredSize` et centrer sur la largeur REELLE (`onSizeChanged`).
  La LED floutee du composant ne se lit pas : dessiner une barre nette.
- **Regle** : `noteOff` doit memoriser l'octave de l'appui (l'octave est un parametre
  vivant) ; ne JAMAIS re-transposer dans `noteOff`.
- **Outil** : `tools/aetherfx/organ_probe.cpp` (g++ direct) liste les parametres morts
  et les preconditions de lecture. `46 parametres, 0 mort` = contrat tenu.

## Session 2026-11-07 (New 3 Design) — capuchons de fader + residus orgue
- **Capuchon de fader decentre = centre DEUX fois.** Le conteneur centre deja
  l'enfant ; l'offset portait en plus `(RAIL_W - THUMB_W)/2` -> decalage de
  -3,5 unites (mesure : capot 477,5 vs rainure 488,5). Regle : sur un fader, le
  centrage en travers vient de `Modifier.align(...)`, l'offset ne porte QUE sur
  la course. Meme piege sur le fader couche. Commit `1abcdc5`.
- **`FaderThumb` : l'ombre deborde du cadre** (SH_H 1790 > H 1657). Si
  `showShadow=false` (comme sur l'orgue) il n'y a AUCUNE ombre ; un parent qui
  coupe le cadre la fait disparaitre aussi.
- **Fenetre d'instrument vs panneau** : le seul residu de la coque sur l'orgue
  etait le `SaveButton` de `AeInstrumentWindow` (gater `isModified` par
  `&& instrument != ORGAN`). `OCT/PRESET/ORGAN MODEL/FACTORY/USER` appartiennent
  a l'orgue. Il ne reste que `BackArrow` en haut a gauche.
- **NE PAS lancer toute la suite** : `:app:testDebugUnitTest` complet se bloque
  sur `GreetingScreenshotTest` (AppNotIdleException). Cibler les tests organe
  (`OrganControlsInputTest`, `OrganUiCaptureTest`, `AeInstrumentCaptureTest`,
  `AeManagerTest`) = 42/42 en ~13 s. Echecs preexistants hors sujet :
  `ExampleRobolectricTest`, `GreetingScreenshotTest`, `PianoPackFormatTest`.
- **Flèche retour de la fenêtre vs panneau orgue** : deux systemes de coordonnees
  independants. Flèche = `BackArrow(winTh, 10, 9, 30)` (scene 1000x460). Orgue =
  unites maquette 891x411 mises a l'echelle par `LocalDensity`. Un `startInset`
  qui decalait le bloc entier laissait une bande vide -> supprime ; seule la case
  OCT est poussee (`D.X1 + 30f`, largeur `D.COL1 - 30f`). Commit `e37623f`.
- **Pousser sur main** : `test/organ-design` etait en avance rapide sur
  `origin/main` -> `git push origin HEAD:main` (pas de fusion). Verifier d'abord
  `git merge-base --is-ancestor origin/main HEAD`.

