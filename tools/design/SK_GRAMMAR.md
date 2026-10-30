# Sunday Keys — grammaire de la fenêtre d'effet (relevé sur 20 captures)

Source : centre d'aide officiel `support.sundaysounds.com/article/3140-effects`
(20 captures d'écran de l'app iPad/Mac). **Design seulement** — les effets du
dépôt sont déjà écrits, il n'y a rien à reprendre côté DSP.

Outils : `tools/design/sk_fetch.py` (collecte des URLs), `sk_dl.py` (téléchargement),
`sk_ocr.py` (libellés), `sk_grammar.py` (bandes), `sk_cols.py` (colonnes),
`sk_blobs.py` (potards/curseurs), `sk_ascii.py` (lecture visuelle en texte).

## 1. La fenêtre est un panneau unique, plein cadre

Il n'y a **pas de fenêtre flottante** posée sur le mixer : l'écran entier devient
l'effet. Trois bandes, de haut en bas :

| Bande | Contenu | Hauteur mesurée (iPad 1192×829) |
|---|---|---|
| En-tête | icône + **nom de l'effet**, onglets `Settings` / `Output`, bandeau LEVEL à droite | y 28..60 |
| Corps | la grille des paramètres, sur un fond légèrement plus clair que la page | y 289..733 |
| Pied | barre d'actions (Confirm / Discard / Replace) | y 821..829 |

Le nom de l'effet est en **haut-gauche**, précédé d'une petite icône. À droite du
nom, deux onglets texte — `Settings` et `Output` — pas un segmented à pouce.

## 2. La grille de paramètres : 2 lignes, 2 ou 3 colonnes

C'est le point qui compte pour « moins encombrant ». Chaque paramètre occupe une
**cellule** : libellé au-dessus, contrôle au-dessous, valeur à droite du libellé.

Relevé des colonnes sur Chorus (label row 253..270) : centres à **x = 225, 599, 890**
(puis la zone de valeur à 1072). Sur Space Verb : centres à **x = 186, 409, 633**.
Sur Overdrive (sliders) : colonnes à **173..644**, puis 691, 891, 1071.

Autrement dit : **une seule grande colonne pour les curseurs larges**, et **deux à
trois colonnes pour les potards**. Jamais 10 potards à plat.

## 3. Deux formes de contrôle, et seulement deux

| Contrôle | Forme | Emploi |
|---|---|---|
| **Potard** | anneau + aiguille, ~60×42 px | un réglage continu isolé |
| **Curseur horizontal** | barre longue, rail fin, poignée | les réglages qui se lisent en proportion : Mix, Feedback, Depth, Dry/Wet |

Le **Dry/Wet** est toujours un curseur horizontal, à droite, en colonne fixe
(x ≈ 891 et 1071 sur les captures 1192 de large).

## 4. `Settings` / `Output` : deux pages, pas un scroll

Le corps ne défile pas. Les paramètres sont répartis sur deux pages, et l'onglet
`Output` porte la sortie (Gain Boost, Mix, niveau). Sur Chorus : `Settings` =
Frequency / Depth / Feedback, `Output` = Gain Boost / Mix.

## 5. Le catalogue réel (20 écrans)

| Écran | Type | Paramètres relevés |
|---|---|---|
| Arpeggiator | MIDI | Subdivision, Style, Steps, 16 pas (velocity + on/off) |
| Easy Chord | MIDI | Input, 8 Output, + Add Chord (99 max), poubelle |
| Octave Up | MIDI | Blend (un seul paramètre) + texte d'aide |
| Sequencer | MIDI | Subdivision, Velocity, Steps, 16 pas, Input/Output |
| Bitcrusher | Audio | Bit depth, Sample rate, Mix |
| Chorus | Audio | Frequency, Depth, Feedback, Gain Boost, Mix |
| Delay | Audio | Feedback, Subdivision, Ping-Pong, Filter, Mix |
| Flanger | Audio | Rate, Depth, Feedback, Mix |
| Gain | Audio | Gain (un seul paramètre) |
| Overdrive | Audio | Drive, Low Cut, Tone, Mix, Boost |
| Pitch Shifter | Audio | Pitch shift, Amount |
| Pure Verb | Audio | Reverb Size, Mod Amount, Mod Rate, High Cut, Low Cut, Dry, Wet |
| Rotor | Audio | Style, Intensity, Cabinet, Amp Modeling, Drive, Boost |
| Shimmer | Audio | Amount (un seul paramètre) |
| Space Verb | Audio | Predelay, Size, High Cut, Low Cut, Mod Amount, Mod Rate, Dry, Wet |
| Tremolo | Audio | Depth, Frequency, Subdivision, Stereo |

**Constat majeur** : les effets à **1 ou 2 paramètres** (Gain, Shimmer, Octave Up,
Pitch Shifter) sont traités comme les autres, sans écran spécial — un grand potard
centré et une phrase d'explication. C'est l'inverse de l'encombrement : peu de
paramètres = écran aéré, pas un écran vide à compenser.

## 6. Trois règles de flux à reprendre

1. **Engage / Bypass est un bouton à gauche du nom**, pas une case dans une liste.
   Deux états visuels seulement (captures `effects_1` / `effects_2`).
2. **L'édition est un aperçu vivant** : on entend les changements pendant qu'on
   tourne, et rien n'est écrit avant `Confirm`. `Discard Changes` revient à l'état
   précédent. C'est exactement le couple snapshot/restore du `FxController` reçu.
3. **`Replace Effect` est en haut-gauche de l'en-tête**, et le catalogue propose
   **« No Effect »** comme entrée : retirer un effet et le remplacer sont le même
   geste. Le dépôt reçu a déjà cette structure (`onReplace` → `view = Picker`).

## 7. Ce que ça implique pour nos FX existants

Le dépôt a déjà 15 effets (`FxRegistry`, `FxKnobBoxes`) posés sur une fenêtre
**825 × 343**, avec des potards placés en coordonnées libres (p. ex. Æther Space :
7 potards à x = 105, 161, 262, 387, 513, 614, 665). La grammaire Sunday Keys
propose au contraire **une grille** : des cellules alignées, 2 lignes × 2-3 colonnes.

C'est là que « plus simple et moins encombrant » se joue : pas dans la couleur,
dans le **fait de ranger les mêmes potards sur une grille** au lieu de les disperser,
et de passer les réglages de proportion (Mix, Dry/Wet, Feedback) en curseurs.

## Reste à faire

- Arbitrer la grille cible : combien de colonnes sur 825 de large, et où tombe la
  colonne des valeurs.
- Reprendre les 15 effets du `FxRegistry` dans cette grille, un par un.
