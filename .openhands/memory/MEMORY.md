# NewKeys- / SoundStage Mixer — memoire

**Lire AGENTS.md d'abord** (pieges + decisions, charge auto). Ceci est l'index.

## Pieges couteux
- « Compile + pousse » ne prouve PAS qu'une UI a bouge : verifier le fichier qui
  rend la fenetre, + capture Roborazzi/OCR. (2026-11-03 : 3 commits moteur, user
  voyait la meme interface.)
- `AeChip(c,idx,i,n)` = i/(n-1). Le moteur sax mappe `lround(p(0)*3)` sur 4 types :
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
- Ne JAMAIS declencher de workflow GitHub (release.yml = workflow_dispatch only).
- **Pack multi-couches : normaliser PAR COUCHE, jamais globalement.** Une cible RMS
  unique pour toutes les couches ecrase la dynamique -> courbe de velocite plate et
  NON monotone (frapper plus fort donne moins). Et pour un fondu entre deux prises de
  la MEME source (correlees) : fondu LINEAIRE, pas cos/sin (puissance constante).
- **Pack heterogene : NE PAS forcer une note a lire toutes ses couches sur une seule
  racine.** Mesure du 2026-11-04 (3e) : la racine 60 ne porte qu'UNE couche (la
  DOUCE) ; les couches fortes vivent sur la racine 59. Forcer la racine 60 a la note
  60 remplace la couche forte (rms 0,040) par la douce (0,001) -> **-18 dB** et
  timbre detruit. Une couche = une racine distincte dans ces packs. Le « dain dain »
  suppose du 04 n'a PAS ete confirme : l'enveloppe du moteur d'origine (fenetre 50 ms
  / hop 10 ms, detrend) est une decroissance MONOTONE lisse, sans ondulation.
  Attention : un banc a fenetre de tendance courte (250 ms) invente un « battement »
  qui n'existe pas — le valider sur un signal temoin (prise seule) avant de conclure.

## Environnement (verifie 2026-11-04, New 3 Design)
- **Le depot vit a la RACINE `/workspace/project`** (pas de sous-dossier `NewKeys-`).
  `restore_env.sh` y pointe encore -> casse, ne pas l'utiliser tel quel.
  Provisionnement SDK : `/workspace/provision_sdk.sh` (verifie, idempotent).
- `serve.py` doit demarrer APRES `apk_page.py` (sinon `/workspace/dl/pub` absent au
  demarrage -> il sert le cwd -> 404 sur /aether.apk).

## Session 2026-11-04 — T2/T5 velocite piano
- Cause unique mesuree : normalisation GLOBALE des couches (dynamique 13,6 -> 29,7 dB
  apres correction) + pas de dB inegaux du pack (saut T5) + fondu cos/sin sur signaux
  correles. Correctifs dans `fx_piano.h` (`preload`, `PianoVoice::process`). Commit
  `94d393e` pousse. Detail : `.openhands/memory/2026-11-04.md`.

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

## Carte du code Æther
- Fenetres : `AeWindow.kt` dispatch -> `AePianoUi.kt` / `AeSaxUi.kt` / ...
- Banques : `AeBanks` (`AeInstrumentHost.kt`) : `isElectricModel`, `pianoBankId`.
- Moteurs : `app/src/main/cpp/aetherfx/fx_piano.h` (17 params), `fx_sax.h` (23).

## Session 2026-11-03 (3e) — nettoyage ressources mortes (ea4d6f5)
- 40 ressources mortes retirees (ic_fx_* = vecteurs AeFxIcons en code, fonts
  inter/lexend/orbitron) + reliques racine (0.4500, B4/B5*.txt, copies racine
  de Setlists/Settings, .aistudio). Outil : `tools/audit_dead_res.py`.
- NE PAS toucher `ae_piano_*`/`ae_sax_*` : charges par `getIdentifier` (invisible
  a un grep de nom). `app/assets/` (JSX) conserve : reference de design.

## Session 2026-11-03 (suite) — Sax + planches
- **Bulldog = UN sax, profil ALTO** (centroide 2600-3400 Hz ; plage EXP1
  MIDI 50.9-86.3). « enleve le sax de meme nature » = l'alto change de source,
  PAS retirer les autres. Les 4 chips (n=4) restent ; Bari restaure.
- **Bulldog source perdue** : les liens `dl*.filesdwn.ir` sont 404, et le commit
  local `a67ad1d` (banque unique `saxpack.bin` 5,37 Mo, articulations) n'a JAMAIS
  ete pousse -> perdu au wipe. Un commit non pousse = perdu.
- Case de presets Sax masquee dans `AeWindow.kt` (`!= PIANO && != SAX`).
- **Planches piano/EP** : `tools/pianopack/build_plates.py` convertit les 12 PNG
  du zip (1376x768) en WebP 1500 px -> 770 Ko (contre 1254 Ko). Les 3 EP
  (ballad/dx/epiano) ont desormais leur photo (avant : repli sur `electric`).

## Session 2026-11-04 — faux sustain piano + « marteau » sax
- **Faux sustain piano (N1)** : 38/68 regions du pack bouclaient dans la partie
  qui DECROIT ; le lecteur rebouclait sans fondu ni gain. `build_newstage.py`
  annonce compenser mais ne le fait jamais (`faded=0`). Correction A LA LECTURE :
  `flattenLoop()` (`fx_piano.h`, appele par `decodeRegion`) aplatit le niveau de
  la boucle + fond le raccord sur une periode vers la phase AVANT `loopStart`
  (jamais vers la tete du loop). Chute 0,78->0,96, saut 0,77->0,06.
- **« Marteau » sax (N5) — CORRIGE le 2026-11-03** : ce n'est NI la boucle (saine,
  chute 0,99) NI le `bite`. Mesure : le `bite` ne pese que **2,6 %** du signal, et le
  desactiver laisse le MEME nombre de pics. Les « ts ts ts » tombent EXACTEMENT sur les
  attaques de notes (verifie en correlant les clics avec les noteOns du lick). C'est le
  transitoire d'attaque (crete/RMS 0,37) = clic de synthese delibere (fx_sax.h l.229,
  bande 2800 Hz, 18 ms) + attaque naturelle de l'echantillon. Correctif candidat :
  attaque adoucie (synth 50 ms + fondu echantillon 150 ms + clics coupes) -> 0,25.
  **Regle** : un clic qui suit chaque note n'est pas forcement un clic ajoute ; mesurer
  le transitoire d'attaque avant de retirer quoi que ce soit.
- **p(1) « Subtone » n'est lu nulle part** dans `fx_sax.h` (parametre mort).
- **Parametre 22 = knob « Character »** (0 = synthese, 1 = echantillon). Les 23
  presets sax sont a **1,0** -> tous les controles de synthese (Breath, Growl,
  Scoop, Grit, Attack) sont annules. C'est « les articulations pas branchees ».
- **Bulldog Sax V2** : l'utilisateur a declare le 2026-11-04 detenir la LICENCE
  et l'ACCORD du proprietaire. **Tous les fichiers qu'il enverra desormais sont
  livres avec accord** -> ne plus bloquer sur la licence ; reintegrer Bulldog.
  **INTEGRE le 2026-11-03** (commit `700879f`) : `tools/saxpack/build_bulldog.py`
  construit `saxpack_bulldog.bin` depuis les 36 `NN-EXP1.wav` (02 = MIDI 54, +1
  demi-ton/fichier, mesure par hauteur). `AeBanks.fileFor` : l'**alto** (type 1)
  joue Bulldog, repli `saxpack_alt.bin` dans `read()`. Boucle = entier de periodes
  (seam 0,98 -> 0,21), tenue plate sur 6 s.
- **Velocite piano/EP (T2/T5) : OK, pas de saut.** `piano_velocity_check` +
  balayage fin : courbe LISSE sur 1..127 (~41 dB), aucune inversion reelle. Le
  fondu a puissance constante entre couches (fx_piano.h ~l.1084) corrige le « ca
  saute » en haut de la velocite. Le faux « saut de 5 dB » venait d'un `printf("%d",
  double)` dans mon banc, pas du moteur.
- **T1 (Amp) : deja fait** — AMP (12) et PUNCH (13) retires de `AePianoUi.kt` ;
  index 12 recycle en bruit de touche. **T4 : fait** — groupes TONE/MIC/PLAY/
  MECHANICS (acoustique) et TONE/SPACE/PLAY/MECHANICS (EP).
- **Demos audio** : `tools/demos/audio/` (12 pianos + 4 sax, MP3) rendus par le
  moteur reel ; regen via `tools/demos/f32_to_mp3.py`. Sert de reference d'ecoute.
- **401 token_not_found_in_db** : rafraichissement par la plateforme via
  `OH_LLM_API_KEY_REFRESH_URL` / base `https://llm-proxy.app.all-hands.dev` /
  en-tete `X-Session-API-Key`. Probleme plateforme, pas depot ; sortir net.

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
