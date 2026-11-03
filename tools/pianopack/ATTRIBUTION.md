# Attribution — piano samples

Les packs produits ici contiennent de l'audio derive de deux banques. Leur usage est
lie par leurs licences respectives.

## S90 Full Grand (piano acoustique, `piano.bin`)

- **Auteur** : JSF (SoundFont publie par son auteur)
- **Format** : SF2, 167 Mo, 205 echantillons, `isng` EMU8000, construit avec Polyphone
- **Licence** : aucune mention `ICOP` / `ISBJ` dans le fichier.  L'auteur l'a diffuse
  librement ; la source est citee par correction, comme pour la banque precedente.
- **Ce qui est repris** : la STRUCTURE (deux prises par couche, pan gauche/droite,
  7 plages de velocite, hauteur des racines) et les echantillons.  Le pack est
  reconstruit au format du moteur par `extract_sf2.py` + `build_sf2.py`.
- **Mesure** : centroide 1 995 Hz a C4 contre 1 276 Hz pour la banque precedente.

## SplendidGrandPiano (ancienne source de `piano.bin`, conservee pour reference)

- **Auteur** : AKAI / Steinway, programme par sfzinstruments
- **Source** : https://github.com/sfzinstruments/SplendidGrandPiano
- **Licence** : **domaine public**
- Rien n'est exige, mais la source est citee par correction.
- Remplacee parce que la banque ne porte presque rien au-dessus de 5 kHz
  (mesure : 0,002 % de l'energie a C4 fortissimo) : aucun moteur ne peut en tirer
  un piano brillant.

## GregSullivan.E-Pianos (piano electrique, `electric.bin`)

- **Auteur** : Greg Sullivan, conversion SFZ par kinwie
- **Source** : https://github.com/sfzinstruments/GregSullivan.E-Pianos
- **Licence** : Creative Commons Attribution 3.0 Unported (CC-BY 3.0)
  https://creativecommons.org/licenses/by/3.0/
- **Attribution obligatoire** : le nom de l'auteur et la licence doivent apparaitre
  partout ou l'application est distribuee. C'est ce fichier qui porte cette mention.

L'echantillon est transforme (mono vers stereo, 22,05 kHz, troncature, boucle,
ADPCM 4 bits) mais reste une oeuvre derivee : la licence s'applique au resultat.

## ED DX7 ROMANCE TINES (piano electrique, `ep_tine.bin`)

- **Auteur** : Ed Araujo (`ICOP = SAMPLED BY ED ARAUJO 2023`)
- **Format** : SF2 37,9 Mo, 89 echantillons, 22 racines (tous les 7 demi-tons),
  2 couches de velocite, paires stereo L/R, boucles presentes, construit avec Polyphone
- **Licence** : aucune mention `ICOP` de licence explicite. Comme S90 Full Grand,
  l'auteur l'a diffuse librement ; la source est citee par correction.
- **Ce qui est repris** : les echantillons et leur structure (2 couches, paires
  stereo).  Reconstruit par `build_dxrom.py` (44100 Hz, 30 regions, 3,16 Mo).
- **Mesure** : centroide 881 Hz, soit **2,8x plus sombre** que les autres EP
  (2173-2970 Hz).  C'est un tine feutre, pas un tine brillant — d'ou le remplacement
  de l'ancien `ep_tine.bin` (SF2 « E.Piano », centroide 2497 Hz).
- **Pitch** : verifie par produit harmonique, +-2,3 cents (identique a `piano.bin`).

## Banques examinees et rejetees

| Banque | Raison |
|---|---|
| SalamanderGrandPiano | 714 Mo, aucun point de boucle dans les metadonnees |
| jlearman.jRhodes3c | CC BY-**NC**-SA : interdiction commerciale |
