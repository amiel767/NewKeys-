# Écart interface FX câblée ↔ référence Sunday Keys

Référence retenue : **les captures Sunday Keys** (centre d'aide officiel
`support.sundaysounds.com/article/3140-effects`, 20 captures). Le zip
`fx-screens-spec.zip` est écarté : sa source n° 1
(`reference_source_fx_window.html`) pèse **0 octet dans toutes les branches**
(vérifié sur `main`, `design/tests`, `docs/fx-agent-pack`,
`feature/fx-shell-ui`, `recovery/fx-window-shell`).

## Ce qui est mesuré

| Élément | Valeur |
|---|---|
| Captures téléchargées | 19/20 (`/workspace/dl/sk/app/effects_*.png`) |
| Taille iPad | 1192 × 829 (ratio 1,44) |
| Fenêtre FX câblée | 891 × 411 (ratio 2,17), flottante, `FxGeo` |
| Source de vérité design | `app/assets/FxWindowPreview.jsx` + `tools/design/SK_GRAMMAR.md` |
| Écart Compose ↔ maquette JSX (page Space) | **10,2 %** de pixels > 24/255 |

## Grammaire Sunday Keys (relevée, `SK_GRAMMAR.md`)

1. **Panneau plein cadre** — « il n'y a pas de fenêtre flottante posée sur le
   mixer : l'écran entier devient l'effet ». Trois bandes : en-tête / corps / pied.
2. **Deux onglets TEXTE** `Settings` / `Output` — pas de segmented à pouce. Le
   corps ne défile pas : les réglages sont répartis sur deux pages.
3. **Cellule = libellé au-dessus, contrôle au-dessous, valeur à droite du libellé.**
4. **Deux formes de contrôle, et seulement deux** : potard (anneau + aiguille,
   ~60×42) et curseur horizontal (proportions : Mix, Feedback, Dry/Wet).
5. **Grille** 2 lignes × 2-3 colonnes ; jamais 10 potards à plat.
6. Engage/Bypass = bouton à gauche du nom ; `Replace Effect` en haut-gauche ;
   pied = Replace / Discard / Confirm.

## Écarts constatés sur la fenêtre câblée

| Règle | Référence | État câblé | Verdict |
|---|---|---|---|
| Plein cadre | écran entier | fenêtre flottante 891 × 411 | **écart** |
| Onglets Settings/Output | 2 pages texte | aucune (une colonne) | **écart** |
| Valeur à droite du libellé | oui | sous le potard | **écart** |
| Grille alignée | cellules | rangée de 4 potards + colonne de sliders | **partiel** |
| Deux contrôles seulement | potard + curseur | potard + curseur (conforme) | OK |
| Titre Cinzel penché | — | titre penché −1,2° | choix maquette |

## Blocage

La source n° 1 étant vide, la spec demande de « signaler la contradiction, ne pas
choisir ». La règle « plein cadre » contredit frontalement la contrainte
`FxGeo` (fenêtre flottante 891 × 411, jamais étendue). Il faut trancher avant de
recomposer la page.

## Outils livrés

- `tools/fxcompare/diffwin.py` — diff aligné capture ↔ référence + carte de zones.
- `tools/fxcompare/measure_ref.py` — mesure orbe / potards d'une référence.
- `tools/design/sk_*.py` — bandes, colonnes, blobs, ASCII (déjà présents).
