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
- Ne JAMAIS declencher de workflow GitHub (release.yml = workflow_dispatch only).

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
- **« Marteau » sax (N5)** : ce n'etait pas la boucle (saine : chute 0,99) mais le
  `bite` de `fx_sax.h` : `x *= 1 + u²*(2.6+p7)` sur 30 ms = boost large bande =
  « thump » a 33 Hz. Fenetre 30 ms -> 5 ms (un slap d'anche dure < 6 ms).
- **p(1) « Subtone » n'est lu nulle part** dans `fx_sax.h` (parametre mort).
- **Parametre 22 = knob « Character »** (0 = synthese, 1 = echantillon). Les 23
  presets sax sont a **1,0** -> tous les controles de synthese (Breath, Growl,
  Scoop, Grit, Attack) sont annules. C'est « les articulations pas branchees ».
- **Bulldog Sax V2** = Kontakt PAYANT (BDS001, synthonia.com), pas de licence
  libre -> ne pas embarquer. Sur disque /workspace/work/bulldog (non versionne).
- **401 token_not_found_in_db** : rafraichissement par la plateforme via
  `OH_LLM_API_KEY_REFRESH_URL` / base `https://llm-proxy.app.all-hands.dev` /
  en-tete `X-Session-API-Key`. Probleme plateforme, pas depot ; sortir net.
