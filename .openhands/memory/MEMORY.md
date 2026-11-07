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
- **Maven Central renvoie 429 depuis la sandbox** (curl direct aussi). Robolectric
  bloque alors TOUTE la suite de tests (2 artefacts : `nativeruntime-dist-compat` au
  classpath, `android-all-instrumented:<v>` telecharge au runtime dans `~/.m2`).
  Fix sans toucher le depot : `./gradlew -I /workspace/init-mirror.gradle ...` avec
  `maven.aliyun.com/repository/public` **en premier** + prefetch du jar android-all
  (+ son `.pom`) dans `~/.m2/repository/org/robolectric/android-all-instrumented/`.
- **`--configuration-cache` casse `:app:testDebugUnitTest`** (serialization error
  `DefaultArtifactCollection`) : ajouter `--no-configuration-cache` pour les tests.
- `restore_env.sh` (depot) ne pose que JAVA_HOME + keystore : il **n'installe pas** le
  SDK ni `local.properties`. Recreer `/workspace/install_sdk.sh` (cmdline-tools +
  platforms;android-36 + build-tools;36.0.0 + cmake;3.22.1 + ndk;26.1.10909125) et
  `/workspace/build.sh`. JDK : `/usr/lib/jvm/java-21-openjdk-amd64`.
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

## FX Design (fenêtre FX) — 2026-11-06

- **L'EQ est « Æther Prism »** (`FxId`/`FxDesign` id **2**, `FX_TONE` cote moteur),
  pas « Sculpt ». Palette **Rouge Royal** par defaut, bleu en variante.
- **Page Kotlin** : `com/aetherkeys/fx/FxEqPage.kt` (courbe a gauche + 3 colonnes
  Low/Mid/High a droite, pied Mode Shelf/Bell/Tilt + Output). Les donnees et
  l'arithmetique sont dans `FxDesign.kt` (`FxDesign.PRISM`, `object FxEq`).
  Montee dans `FxEffectPage.kt` (`if (fx.eq)`) et l'en-tete presets dans `FxWindow.kt`.
  `FxDesignHost.kt` traite Prism **comme** Space (vrai effet de chaine : `engineSlot`).
- **Disposition retenue** : trois colonnes cote a cote (chacune gros potard de gain
  + petit Freq, le Mid a en plus son Q). Ne **pas** revenir au selecteur de bande
  unique (`.band-seg`) : l'utilisateur ne l'a pas aime.
- **12 presets pro** (Flat, Low Cut, Warmth, Air, Presence, De-Mud, Vocal,
  Acoustic, Piano, Kick, Snare, Telephone) recales dans les **3** registres
  (2 Kotlin + `fx_registry.h`). `FxEqLogicTest` verifie l'egalite design<->moteur.
- **Pieges verifies** :
  - `Modifier.weight` est une extension de `RowScope` : hors scope dans un
    `@Composable` prive, l'erreur ne dit que « Unresolved reference 'weight' ».
    Passer le `Modifier` en parametre.
  - `FxDesign` initialise des couleurs via `android.graphics.Color.parseColor` :
    un test **JUnit nu** echoue (`Method parseColor not mocked`). Tester sous
    **Robolectric** (`@RunWith(RobolectricTestRunner::class)`).
  - `tools/design/preview.py serve <dir>` : router sur le dossier publie lui-meme,
    pas son parent — sinon `/eq.html` repond 404 et l'on capture une page blanche.
  - **Regenerer un artefact publie** : comparer d'abord le generateur local a
    `origin/main` (`git diff origin/main --stat -- tools/xxx_page.py`). Un worktree
    en retard republie l'ancien monde (cas vecu sur `sheets_page.py`).
- **Livraison** : `tools/eq_page.py` publie maquette vivante + comparaisons
  maquette|Kotlin (WebP) sous `/workspace/dl/pub/eqdesign/`, et ecrit aussi
  `/workspace/dl/pub/index.html` pour que le lien racine mene a l'EQ.

## Arpeggiateur — peaufinage (2026-11-07, branche `design/tests`)

- **Le bug « random joue DownUp »** : `MODE` etait envoye `mode / 5f` sur une table
  d'affichage qui ne suit PAS l'ordre du moteur (`ArpMode` = Up, Down, UpDown,
  DownUp, Random, Chord). « random » (index de page 3) tombait sur DownUp (index 3).
  Idem `RATE` : `sub / 3f` sur 4 subdivisions alors que le moteur en a 6, donc
  « 1/8 » jouait 1/16 et « 1/32 » saturait. Corrige par `object ArpMapping`
  (`aetherkeys/fx/ArpMapping.kt`) : **un seul endroit** fait page <-> moteur.
- **`FxDesign.SUBS` suit maintenant l'ordre de `ArpRate`** (6 entrees, ajout de
  `1/8T`, `1/16T`) : l'index EST le taux. `STYLES` reste dans l'ordre de la PAGE
  (random en 4e), la correspondance vit dans `ArpMapping.MODES = [0,1,2,4]`.
- **L'arp est PERSISTANT** : `ArpEngine` (`soundstage/mixer/fx/ArpEngine.kt`) est
  l'helper partage (push/arm/allNotesOff/applyTo/toPersisted). Il est restaure au
  boot, au changement de patch/setlist et a la suppression du patch actif
  (`MixerViewModel.restoreArpForActivePatch`), **sans ouvrir la fenetre FX**.
  Fermer la fenetre ne desarme PLUS l'arp (avant : `onDispose` -> `setArpArmed(false)`).
- **UN ARP PAR PISTE (correction utilisateur, commit 52de119)** : un arp s'ajoute A
  LA DEMANDE sur **n'importe quelle** piste d'un patch, et **plusieurs** tournent
  ensemble. Le slot MIDI est **vide par defaut** (`FxChain.midi = null` : rien n'est
  precharge). Cote moteur, **un arp PAR CANAL MIDI** (`std::array<ArpEngine,
  kMaxChannels> mArps` + masques atomiques `mArpArmedMask`/`mArpEmitMask`), plus
  l'ancien `mArpChannel` unique. `restoreArpForActivePatch` arme **chaque** piste
  (0..7) qui porte un arp vivant. Piege : l'emission survit au desarmement (sinon
  les notes deja allumees sonnent sans fin) ; `runArp` **trie `arpOut_` par frame**
  (deux canaux d'un meme bloc s'entrelacent sinon).
  Ancien modele « premier-seul » : FAUX, ne pas y revenir.
- **Persistance debouncee** : la grille pousse au moteur a chaque changement mais
  n'ecrit le rack qu'apres 600 ms (`FxDesignHost` : `saveArp()` dans `collectLatest`).
  `pushArp()` ecrit aussi `engine.arp` pour que la sauvegarde 500 ms du rack
  enregistre bien la derniere grille.
- **Banc natif** `tools/arp/arp_check.cpp` (hors Android, `g++ -O2 -std=c++17
  -I app/src/main/cpp`) : 11 sections, dont la **table des taux** (miroir de
  `FxDesign.SUBS` : 1/4=24000, 1/8=12000, 1/16=6000, 1/32=3000, 1/8T=8000,
  1/16T=4000 frames a 120 BPM). `TOUT PASSE` (0 echec).

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
