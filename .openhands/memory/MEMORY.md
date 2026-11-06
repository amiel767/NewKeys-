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

## Environnement (verifie 2026-11-04, New 2)
- **Le depot vit dans `/workspace/project/NewKeys-`** (verifie : `git rev-parse
  --show-toplevel`). Une note anterieure disait « a la racine » : c'etait faux pour
  cette session, ne pas s'y fier.
- `serve.py` doit demarrer APRES `apk_page.py` (sinon `/workspace/dl/pub` absent au
  demarrage -> il sert le cwd -> 404 sur /aether.apk).
- Le sandbox peut se **reinitialiser en pleine session** : le SDK survit, le JDK non
  (`apt-get update` puis `openjdk-21-jdk-headless`). Pillow/numpy a reinstaller.

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

## Sheets : Sets et fenetre flottante (2026-11-04)
- **Un Set est un DOSSIER** : `Setlist(id, name, sheetIds, colorArgb, createdAt,
  updatedAt)` dans `.library.json`. Le supprimer ne supprime AUCUNE feuille.
  Filtrage : `LibraryUiState.visibleSetlists()` / `visibleSheets()`
  (`LibraryFilter.{All,Favorites,Sets,OfSetlist}`).
- **Accueil refait le 2026-11-04 (New 2, commits `8722eea`/`4c16e14`)** : plus de
  grille a 4 colonnes, plus de filtre « Sets », plus d'icone dossier, plus d'apercu
  de Set. **Rangee de cartes de Set en haut** (236x96 dp ; 96 dp = la hauteur de
  `SetlistsScreen.kt:559`) + **liste verticale des feuilles** (44 dp = `compactRows`
  de Setlists). On entre dans un Set par sa carte ; `← Tout` en sort. La rangee de
  Sets RESTE visible dans un Set (pour passer de l'un a l'autre).
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
- **Piege de test** : `SheetsCaptureTest` ecrit les feuilles dans un dossier
  temporaire et la liste suit l'ordre du dossier : il varie d'une execution a
  l'autre, donc les couleurs de lignes (teal/rose) **s'inversent** dans
  `sheets_home/sets/inside_a_set.png` (17-27 % de pixels). Pas une regression.
- **Piege de compilation** : `SwitchRow(label, checked, onChange = {...})` —
  `onChange` est un parametre NOMME, pas un lambda final.
- **Piege de test** : un nom de Set en double (pastille de filtre + carte) fait
  echouer `onNodeWithText` (« found 2 nodes »). C'est ce qui a fait retirer les
  Setlists de la rangee de filtres.
- **Flottant** : pastille fine en bas = hauteur + mode « remplir » ; pastille a
  gauche = largeur, `fontScale = cardW / FloatingWidth`. Pastilles quasi invisibles
  (alpha 0,18) et X fondu (0,45), cibles tactiles conservees.
  **Gestes** : appui long = edition rapide, double tap = editeur complet, tap =
  sections, glisse = deplacer, glisse horizontal = changer de section.
  `detectTapGestures` doit declarer `onLongPress` explicitement, sinon il ne se
  declenche jamais. `onSectionChange` fait suivre la section affichee au panneau du
  mixer (avant : il restait sur la PREMIERE section).
- **`sheets/model/ChordText.kt`** (nouveau) relit une ligne d'accords (`1 4 | 5 6m |
  %`, `N.C.`, `-`, `/basse`, degres OU notes) et la reecrit dans une section en
  conservant les ids de barres. C'est le support de l'edition rapide.
  Piege : tester `token.take(2)` avant `token.take(1)` (`b3` vs `b`).

## Carte du code Æther
- Fenetres : `AeWindow.kt` dispatch -> `AePianoUi.kt` / `AeSaxUi.kt` / ...
- Banques : `AeBanks` (`AeInstrumentHost.kt`) : `isElectricModel`, `pianoBankId`.
- Moteurs : `app/src/main/cpp/aetherfx/fx_piano.h` (17 params), `fx_sax.h` (23).

## Journal (detail : `.openhands/memory/<date>.md`, NON injecte)
Sessions 2026-10-30 -> 2026-11-04 : pianos/EP (velocite, boucles, packs), sax
(Bulldog, marteau, articulation), ressources mortes, pistes Æther, Sets/Sheets,
fenetre Æther Instruments. Lire le journal a la demande :
```bash
grep -n -A 30 "velocite piano" .openhands/memory/2026-11-04.md
```
