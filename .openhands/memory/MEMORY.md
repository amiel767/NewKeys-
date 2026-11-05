# NewKeys- / SoundStage Mixer — memoire

**Lire AGENTS.md d'abord** (pieges + decisions, charge auto). Ceci est l'index.
Detail par session : `.openhands/memory/YYYY-MM-DD.md` (jamais charge auto).

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
