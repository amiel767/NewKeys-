# Design test harness (branche `design/tests`)

Prototyper des designs **sans toucher `main`**. `main` appartient a la conversation
parallele `9c93aa402a634cdfa4a1fbf759935777` (Æther Piano) ; cette branche est le
terrain d'essai.

## Pourquoi une branche isolee

Le depot est developpe par plusieurs conversations a la fois. Un design de test
n'a aucune raison de cohabiter avec le code de production : sur sa propre branche
il ne peut pas casser un build parallele, et il se jette d'un `git branch -D`.

## Usage

```bash
# 1. servir les previews HTML (assets de l'app)
python3 tools/design/preview.py serve app/assets 12000

# 2. capturer une page en PNG, en arriere-plan du terminal
python3 tools/design/preview.py shot \
  "http://localhost:12000/%C3%86ther%20Pianos%20%C2%B7%20preview.html" \
  tools/design/shots/piano.png --size 891,411
```

`chromium --headless --virtual-time-budget` laisse les animations se poser sans
`sleep` : la capture est deterministe et la commande rend la main tout de suite.
Seule la **taille** du PNG est imprimee — jamais les pixels — pour rester dans le
mode economique.

## Regles (mode economique)

- Une capture a la fois, ecrite sur disque, pas affichee.
- Les PNG vivent dans `tools/design/shots/` (gitignore) : regenerables, donc pas
  versionnes.
- Comparer deux captures par un **diff de pixels agrege** (nombre de px changes,
  boite englobante), jamais en les ouvrant a l'oeil dans le terminal.

## Ports

| Port | Host |
|---|---|
| 12000 | https://work-1-ehroriszbxljcojx.prod-runtime.all-hands.dev/ |
| 12001 | https://work-2-ehroriszbxljcojx.prod-runtime.all-hands.dev/ |

Deux serveurs peuvent tourner en parallele (un par port) pour comparer deux
variantes cote a cote.
