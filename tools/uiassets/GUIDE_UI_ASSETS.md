# Guide des assets UI — Æther Keys

Guide opérationnel pour produire les images d'interface (pianos, cordes,
instruments) sans gonfler l'APK et sans perdre de netteté.

## 1. La règle qui gouverne tout : dimensionner sur l'affichage

Un fond d'interface n'a **jamais** besoin de plus de pixels qu'il n'en affiche.
Les images fournies par les générateurs (Qwen, Gemini) font 2736×1520 ou plus ;
c'est 3,6× la résolution utile. Ce surplus ne se voit pas, il se paie.

Mesures faites sur le preview (`app/assets/Æther Pianos & Strings · preview.html`) :

| Grandeur | Valeur mesurée |
|---|---|
| Canvas WebGL (téléphone) | **500 × 230 CSS** |
| `devicePixelRatio` | **plafonné à 1,5** (`Math.min(devicePixelRatio\|\|1,1.5)`) |
| Résolution utile téléphone | **750 × 345 px** |
| Canvas desktop | plein viewport (1920 × 883 mesuré) |
| Filtrage de texture | `LINEAR` **sans mipmaps** |

Le dernier point est le plus important. Sans mipmaps, une texture plus grande
que sa zone d'affichage n'est pas « plus nette » : le GPU la sous-échantillonne
en linéaire, ce qui **ajoute de l'aliasing**. Au-delà de la résolution utile,
on paie des octets pour une dégradation.

**Donc : redimensionner une fois, à la taille utile, avant d'encoder.**

## 2. Résolutions retenues

| Palier | Largeur | Usage |
|---|---|---|
| `_750` | 750 px | téléphone (500 CSS × 1,5) — le cas normal |
| `_1500` | 1500 px | desktop 2× (le canvas monte au viewport) |

Pas de 3× : le preview plafonne le DPR à 1,5, un palier 3× ne serait jamais lu.

## 3. Format : AVIF plutôt que WebP

Mesuré sur ce corpus, à qualité perçue égale (SSIM ≥ 0,985) :

| Format | Taille | SSIM |
|---|---|---|
| PNG source | 7,7 Mo | 1,000 |
| WebP q80 @750 | 45,7 Ko | 0,967 |
| **AVIF q80 @750** | **64,5 Ko** | **0,986** |

À qualité égale l'AVIF est ~30 % plus petit que le WebP ; à taille égale il est
nettement meilleur. Décodage vérifié dans le moteur de rendu (Chromium headless) :
`data:image/avif;base64,...` charge bien en `750×417`, ce n'est pas ignoré
silencieusement.

## 4. La chaîne

```bash
pip install pillow numpy scikit-image

# 1. générer le jeu complet (14 instruments × 2 paliers)
python3 /workspace/dl/tools/make_all.py

# 2. réinjecter dans le HTML
python3 /workspace/dl/tools/build_html.py /tmp/preview.html /workspace/dl/uikit.html

# 3. vérifier le rendu
chromium --headless --no-sandbox --disable-gpu --hide-scrollbars \
  --window-size=891,411 --virtual-time-budget=10000 \
  --screenshot=/workspace/dl/render_uikit.png file:///workspace/dl/uikit.html
```

`tools/uikit.py` porte la logique :

- `prep(src, width, sharpen)` — rééchantillonne en **LANCZOS**, un seul passage.
  Ne pas chaîner les redimensionnements.
- `reference(src, width)` — l'idéal : la source pleine résolution vue à la taille
  d'affichage. C'est la cible de la mesure, pas l'image déjà réduite (sinon on se
  compare à soi-même et la métrique ne dit rien).
- `pick(im, fmt, ref, target=0.985)` — cherche **la qualité la plus basse** qui
  tient le SSIM cible. Encoder à qualité fixe gaspille ; le seuil, lui, s'adapte
  au contenu (une image bruitée tolère moins de compression qu'un aplat).

## 5. Résultats obtenus

| Lot | Avant | Après |
|---|---|---|
| HTML complet | 11,61 Mo | **0,69 Mo** |
| 14 instruments × 2 paliers | 61,6 Mo (PNG) | **4,25 Mo** |

Tous les fichiers sont à SSIM ≥ 0,985 mesuré **à la taille d'affichage**.
Objectif « sous 10 Mo » tenu avec une marge de 14×.

## 6. Pièges vérifiés

- **`devicePixelRatio` est plafonné à 1,5** dans le preview. Ne pas dimensionner
  sur un DPR de 3 en croyant bien faire.
- **Pas de mipmaps** (`TEXTURE_MIN_FILTER = LINEAR`). Sur-dimensionner dégrade.
- **Mesurer à la taille d'affichage.** Comparer un 750 px à un 2736 px donne un
  SSIM artificiellement bas qui pousse à sur-dimensionner.
- **L'AVIF est décodé, mais vérifier quand même.** Un navigateur peut accepter
  un `data:` sans le décoder ; `img.decode()` puis `naturalWidth > 0` tranche.
- **Les images du preview ne sont pas les tiennes.** Le HTML d'origine embarque
  ses propres images (NCC 0,41 avec les photos fournies) — ne pas conclure
  « mon image ne s'affiche pas » sur un écart de luminance. Corréler avec
  l'asset : 0,72 = affiché, 0,39 = non affiché.
- **`/tmp` est effacé à chaque redémarrage du sandbox.** Les sources Qwen/Gemini
  et le HTML téléchargé doivent être récupérés à nouveau (voir §7).

## 7. Où sont les sources

| Source | Emplacement | Contenu |
|---|---|---|
| HTML de référence | `app/assets/Æther Pianos & Strings · preview.html` (dépôt) | 9 slots piano + 8 presets strings |
| Photos piano | `/workspace/dl/piano/*.png` (6) | felt, studio_bright, reference, upright, honky, dark |
| Rendu Qwen | `/tmp/qwen/raw_00..11.png` | 3 pianos + 5 strings + 2 rejetés |
| Rendu Gemini | `/tmp/gemini_img_0.png` | le 6ᵉ string (PAD, aurore cyan/violet) |

Le HTML du dépôt se récupère par l'API GitHub :

```bash
curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.raw" \
  "https://api.github.com/repos/amiel767/NewKeys-/contents/app/assets/%C3%86ther%20Pianos%20%26%20Strings%20%C2%B7%20preview.html" \
  -o /tmp/preview.html
```

## 8. Mappage des images Qwen (ordre du récapitulatif)

| Fichier | Preset | Atmosphère |
|---|---|---|
| `raw_01` | Mellow | golden hour, reflets ambrés |
| `raw_02` | Lady D | intime, salon, doré |
| `raw_03` | Electric Keys | néon |
| `raw_07` | Solo Violin + Viola | dorée, intime, pagode |
| `raw_08` | Solo Cello + Bass | bleue nocturne, pleine lune |
| `raw_09` | Section | ambre/cuivre, sakura |
| `raw_10` | Orchestra | blanc dramatique, contre-jour |
| `raw_11` | Pizzicato | rouge orangé vif |
| Gemini | Pad | cyan/violet aurora |

`raw_05` et `raw_06` sont une **première tentative rejetée** (contrôles sur les
côtés) — ne pas les réutiliser.

## 9. Reste à faire

- Les **strings sont encore procédurales** dans le HTML (particules `pet`/`pal`),
  les 6 images ne sont pas branchées : les slots strings n'existent pas côté
  `IMG`. À faire quand l'UI strings sera portée en Compose.
- L'intégration côté app se fait via `ui/pages/DrumPadScreen.kt` et
  `ui/soundfont/` — voir `AGENTS.md` pour les pièges Compose.
