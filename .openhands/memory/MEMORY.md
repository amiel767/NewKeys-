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

## Session 2026-11-04 (suite) — « dain dain » : FONDU DE RACCORD trop court
- Cause racine : fondu de raccord d'UNE periode (~4 ms) -> continu mais audible.
  Porte a **200 ms** -> 0,0 dB. Mesure = **plus FORTE remontee d'enveloppe**
  (`tools/aetherfx/tail_echo.py`), PAS la somme (un tremolo de pack cumule 45 dB
  inaudibles) et PAS la modulation a la periode de boucle (non discriminante).
  Resultat : les 6 banques rejoignent le plancher « 1 couche ». Commit `e2b25bd`.
- Pieges de banc : un mode de dump ne doit pas desactiver les boucles ; borner les
  remontees d'enveloppe a 12 dB (le passage par un zero des couches donne des pics
  non physiques de 30-480 dB) ; verifier l'unite d'un champ avant de conclure.
- `repairLoops` ne force plus la longueur la plus courte si les couches sont a
  moins de 5 % (ep_ballad). Chargement 613 -> 387 ms (un seul decodage/region).
- Env : JDK a reinstaller apres wipe (openjdk-21-jdk-headless) ; SDK =
  `/workspace/android-sdk` ; `restore_env.sh` CASSE. Livraison : `tools/apk_page.py`
  -> `/workspace/dl/pub`, servir port 12000.
