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

