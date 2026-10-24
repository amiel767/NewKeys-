# Fenêtre FX Æther — paquet d'implémentation pour agent

Document unique et autonome. Il contient **le code exact et complet** des fichiers
nécessaires pour concevoir une nouvelle interface de la fenêtre FX, sans explorer
le dépôt.

- **Dépôt :** `amiel767/NewKeys-` (public)
- **Banc isolé :** branche `fx-preview-blocs` — ne touche pas l'application
- **Rien n'est tronqué.** Chaque bloc est le fichier tel quel, avec sa taille, son
  nombre de lignes et son empreinte sha256.

---

## 0. Comment lire ce document

| Étape | Section | Pourquoi |
|---|---|---|
| 1 | §2 `fx-window-preview.html` | **La source de vérité visuelle.** Tout y est déclaré au chiffre près. |
| 2 | §3 `fx_layout_spec.json` | Les mesures par effet (potards, presets, polices). |
| 3 | §1 `FxRegistry.kt` | Les paramètres réels (nom, bornes, défaut) de chaque effet. |
| 4 | §6 les scènes Kotlin | Le rendu déjà porté et validé contre les références. |
| 5 | §7 la coquille Kotlin | Fenêtre, panneau ouvert, potard, rack, store. |
| 6 | §8 le port autonome mono-fichier | Tout-en-un, le plus rapide à appréhender. |

### Règles non négociables — chacune a coûté une passe de débogage

1. **Lire le preview avant de mesurer un pixel.** `AB(i,j,x,y,z,sk)` donne le
   centre et le diamètre de chaque potard, `TG(i,label,x,y,items)` les groupes de
   puces, `SKN`/`KSH` la matière et la forme des chapeaux, `<defs>` les 15 dégradés
   `c0..c14`, le CSS `.xN`/`.pn`/`.bd`/`.cd`/`.bt`/`.chg`/`.kb` les fonds et les
   ombres. Une heure de mesures ne vaut pas dix lignes de source.
2. **Le diamètre d'un potard est `z` plein.** Pas de coefficient `* 0.86` : il
   rendait tous les potards 13 % trop petits (ampli 40 au lieu de 46).
3. **Attention au double décalage.** Les scènes et les tables `AB()`/`TG()` sont en
   coordonnées **contenu** et portent déjà `(-OXL, -OYL)`. Une scène se dessine donc
   à `(0,0)` sans translation, et une boîte `AB()` se place à `(x+OXL, y+OYL)`.
   Ajouter un offset par-dessus décale tout d'un `dy` constant.
4. **Un `DrawScope` travaille en pixels**, les scènes en unités logiques (1 unité =
   1 dp). Il faut `scale(density, density, pivot = Offset.Zero)` — le pivot par
   défaut étant le centre, la scène atterrissait dans le coin haut-gauche.
5. **`Modifier.shadow` est inutilisable ici** : la capture Roborazzi ne rend pas
   l'ombre de plateforme (donc invérifiable), et elle est peinte **au-dessus** du
   contenu, donc ressort à travers un remplissage translucide. Utiliser une ombre
   peinte en `drawBehind` — voir `FxSurface.kt` (§6), `Modifier.raised`.
6. **La fenêtre fait 825 × 343** et le fond d'effet la remplit exactement. Les PNG
   de référence en 1728 × 800 sont la **page entière**, marge incluse : mesurer les
   bords du fond, pas les contenus. Un cadre faux produit exactement les symptômes
   d'un contenu mal placé.
7. **`rail()` n'existe que pour certains instruments** : ids 5, 6, 7, 8, 9, 12, 13,
   14. L'ampli (10) et la basse (11) n'en ont pas — leur « Level » est un potard
   normal, et leur panneau descend jusqu'en bas.
8. **Le texte de scène se mesure avec `density = Density(1f, 1f)`** : le canvas
   applique déjà la densité, un `sp` résolu à la densité réelle serait multiplié une
   seconde fois (2,75× trop grand en 440 dpi).
9. **La couleur du texte vient de la règle `.xN`** de l'effet, pas d'une valeur
   codée en dur : sur la face crème de Solar le texte est en encre sombre
   (`#2a241b`), une valeur claire le rend invisible.

### Pièges du paquet de spécification

- `reference_source_fx_window.html` est **vide (0 octet)** dans le zip livré.
  L'équivalent réel est `fx-window-preview.html` (§2). Ne pas le chercher ailleurs.
- `fx-screens-spec.zip` **complète** `aether-fx-package.zip`, il ne le remplace pas.
- `compare_refs.py` exige des tailles **strictement identiques** et un seuil de
  1,5 % de pixels différant de plus de 24/255. Ce seuil est calibré pour deux rendus
  du **même** moteur : il ne peut pas juger un portage inter-moteurs (navigateur vs
  Skia). Un écart résiduel uniforme est du rasterisage, pas une erreur de mise en page.
- `ref-10-amp-presets.png` est **corrompu** dans le paquet (131 Ko, tronqué).

### Polices

Quatre familles variables, présentes dans le dépôt sous `app/src/main/res/font/` :

| Fichier | Effets |
|---|---|
| `space_grotesk.ttf` | 0, 8, 9, 12, 13 |
| `jetbrains_mono.ttf` | 1 |
| `orbitron.ttf` | 2, 10, 14 |
| `share_tech_mono.ttf` | 3 |
| `sora.ttf` | défaut (4, 5, 6, 7, 11) |

La table fait foi : `FxFonts.title(id)` (§6). Le titre porte `fontWeight = Normal`,
`letterSpacing = 0` et `lineHeight = fontSize` — la hauteur de ligne Material3
(24 sp) fait sinon descendre le titre de ~2 px.

### `FxRegistry.kt`

- **Chemin :** `/tmp/pkg/aether/FxRegistry.kt`
- **Taille :** 29689 octets · **lignes :** 396 · **sha256 (12) :** `14e97148c9f2`
- Généré depuis `fx_registry.h` par `gen_kotlin.cpp`. **La** définition des paramètres : nom, bornes, unité, décimales, défaut, courbe.

````kotlin
// GENERATED by gen_kotlin.cpp from fx_registry.h. Do not edit by hand.
package com.aetherkeys.fx

enum class FxCurve { LINEAR, FREQ_LOG, RATIO_STEP }
enum class FxKind { EFFECT, SHAPER, SOURCE }  // EFFECT: track/master slots. SHAPER: shapes the track's soundfont. SOURCE: replaces the soundfont (one instrument per track)
data class FxParam(val name: String, val min: Float, val max: Float, val unit: String, val decimals: Int, val default: Float, val curve: FxCurve, val uiOnly: Boolean)
data class FxPreset(val name: String, val values: List<Float>)
data class FxInfo(val id: Int, val name: String, val type: String, val kind: FxKind, val params: List<FxParam>, val presets: List<FxPreset>)

object FxRegistry {
    const val SPACE = 0
    const val ECHO = 1
    const val TONE = 2
    const val GRIP = 3
    const val CEILING = 4
    const val PIANO = 5
    const val ORGAN = 6
    const val SAX = 7
    const val STRINGS = 8
    const val GUITAR = 9
    const val AMP = 10
    const val BASS = 11
    const val BRASS = 12
    const val VOICES = 13
    const val SYNTH = 14
    const val SLOTS_PER_TRACK = 6
    const val SLOTS_MASTER = 8
    const val MAX_PARAMS = 32
    const val STRIP_MASTER = 15  // strips: 0..7 soundfont tracks, 8 drum channel, 9 tonic pad, 10 drum-pad sampler
    val effectIds = listOf(0, 1, 2, 3, 4, 10, 11, 13)
    val instrumentIds = listOf(5, 6, 7, 8, 9, 12, 14)

    val all: List<FxInfo> = listOf(
        FxInfo(0, "Aether Space", "Reverb", FxKind.EFFECT,
            listOf(
                FxParam("Size", 0f, 100f, "%", 0, 0.62f, FxCurve.LINEAR, false),
                FxParam("Decay", 0.4f, 8f, "s", 1, 0.7f, FxCurve.LINEAR, false),
                FxParam("Pre-delay", 0f, 120f, "ms", 0, 0.2f, FxCurve.LINEAR, false),
                FxParam("Damp", 1f, 12f, "kHz", 1, 0.45f, FxCurve.LINEAR, false),
                FxParam("Width", 0f, 100f, "%", 0, 0.8f, FxCurve.LINEAR, false),
                FxParam("Mix", 0f, 100f, "%", 0, 0.35f, FxCurve.LINEAR, false),
                FxParam("Diffuse", 0f, 100f, "%", 0, 0.5f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Hall", listOf(0.62f, 0.7f, 0.2f, 0.45f, 0.8f, 0.35f, 0.5f)),
                FxPreset("Plate", listOf(0.4f, 0.5f, 0.1f, 0.7f, 0.9f, 0.3f, 0.7f)),
                FxPreset("Room", listOf(0.25f, 0.3f, 0.05f, 0.5f, 0.6f, 0.25f, 0.3f)),
                FxPreset("Cathedral", listOf(0.95f, 0.95f, 0.35f, 0.3f, 1f, 0.45f, 0.8f)),
                FxPreset("Shimmer", listOf(0.7f, 0.85f, 0.25f, 0.2f, 1f, 0.5f, 0.9f)),
                FxPreset("Chamber", listOf(0.35f, 0.4f, 0.08f, 0.55f, 0.7f, 0.28f, 0.45f)),
                FxPreset("Bloom", listOf(0.8f, 0.8f, 0.3f, 0.35f, 0.95f, 0.42f, 1f)),
                FxPreset("Infinite", listOf(1f, 1f, 0.4f, 0.25f, 1f, 0.6f, 0.95f)),
            )),
        FxInfo(1, "Aether Echo", "Delay", FxKind.EFFECT,
            listOf(
                FxParam("Time 1", 10f, 1000f, "ms", 0, 0.15f, FxCurve.LINEAR, false),
                FxParam("Level 1", 0f, 100f, "%", 0, 0.9f, FxCurve.LINEAR, false),
                FxParam("Time 2", 10f, 1000f, "ms", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Level 2", 0f, 100f, "%", 0, 0.7f, FxCurve.LINEAR, false),
                FxParam("Time 3", 10f, 1000f, "ms", 0, 0.45f, FxCurve.LINEAR, false),
                FxParam("Level 3", 0f, 100f, "%", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Time 4", 10f, 1000f, "ms", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("Level 4", 0f, 100f, "%", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Feedback", 0f, 95f, "%", 0, 0.42f, FxCurve.LINEAR, false),
                FxParam("Tone", 1f, 12f, "kHz", 1, 0.6f, FxCurve.LINEAR, false),
                FxParam("Wow", 0f, 100f, "%", 0, 0.2f, FxCurve.LINEAR, false),
                FxParam("Mix", 0f, 100f, "%", 0, 0.28f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Dotted", listOf(0.15f, 0.9f, 0.3f, 0.7f, 0.45f, 0.5f, 0.6f, 0.3f, 0.42f, 0.6f, 0.2f, 0.28f)),
                FxPreset("Tape", listOf(0.12f, 0.9f, 0.26f, 0.8f, 0.42f, 0.65f, 0.6f, 0.5f, 0.6f, 0.35f, 0.6f, 0.3f)),
                FxPreset("Ping-pong", listOf(0.1f, 0.9f, 0.35f, 0.8f, 0.55f, 0.6f, 0.8f, 0.4f, 0.5f, 0.7f, 0.1f, 0.32f)),
                FxPreset("Slap", listOf(0.05f, 0.9f, 0.1f, 0.2f, 0.15f, 0.1f, 0.2f, 0.05f, 0.15f, 0.8f, 0f, 0.2f)),
                FxPreset("Ambient", listOf(0.2f, 0.7f, 0.4f, 0.6f, 0.62f, 0.5f, 0.85f, 0.4f, 0.7f, 0.45f, 0.35f, 0.4f)),
                FxPreset("Dub", listOf(0.3f, 0.9f, 0.5f, 0.75f, 0.7f, 0.55f, 0.9f, 0.4f, 0.78f, 0.3f, 0.5f, 0.35f)),
                FxPreset("Doubler", listOf(0.02f, 0.8f, 0.04f, 0.7f, 0.06f, 0.5f, 0.08f, 0.3f, 0.1f, 0.9f, 0.1f, 0.3f)),
                FxPreset("Pulse", listOf(0.1f, 0.9f, 0.2f, 0.9f, 0.3f, 0.9f, 0.4f, 0.9f, 0.55f, 0.7f, 0f, 0.3f)),
            )),
        FxInfo(2, "Prism Tone", "Equalizer", FxKind.EFFECT,
            listOf(
                FxParam("Low freq", 20f, 20000f, "Hz", 0, 0.15f, FxCurve.FREQ_LOG, false),
                FxParam("Low", -12f, 12f, "dB", 1, 0.62f, FxCurve.LINEAR, false),
                FxParam("Mid freq", 20f, 20000f, "Hz", 0, 0.5f, FxCurve.FREQ_LOG, false),
                FxParam("Mid", -12f, 12f, "dB", 1, 0.45f, FxCurve.LINEAR, false),
                FxParam("Mid Q", 0.3f, 4f, "", 1, 0.4f, FxCurve.LINEAR, false),
                FxParam("High freq", 20f, 20000f, "Hz", 0, 0.85f, FxCurve.FREQ_LOG, false),
                FxParam("High", -12f, 12f, "dB", 1, 0.7f, FxCurve.LINEAR, false),
                FxParam("Output", -12f, 12f, "dB", 1, 0.5f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Air", listOf(0.15f, 0.5f, 0.5f, 0.45f, 0.3f, 0.85f, 0.85f, 0.5f)),
                FxPreset("Warm", listOf(0.15f, 0.72f, 0.5f, 0.55f, 0.4f, 0.85f, 0.35f, 0.5f)),
                FxPreset("Vocal", listOf(0.15f, 0.4f, 0.58f, 0.62f, 0.5f, 0.85f, 0.62f, 0.5f)),
                FxPreset("Presence", listOf(0.15f, 0.45f, 0.62f, 0.5f, 0.6f, 0.85f, 0.72f, 0.5f)),
                FxPreset("Telephone", listOf(0.15f, 0.25f, 0.52f, 0.78f, 0.9f, 0.85f, 0.3f, 0.5f)),
                FxPreset("Smile", listOf(0.15f, 0.78f, 0.45f, 0.4f, 0.3f, 0.85f, 0.78f, 0.5f)),
                FxPreset("Flat", listOf(0.15f, 0.5f, 0.5f, 0.5f, 0.4f, 0.85f, 0.5f, 0.5f)),
                FxPreset("Black Box", listOf(0.15f, 0.62f, 0.4f, 0.46f, 0.45f, 0.85f, 0.7f, 0.52f)),
            )),
        FxInfo(3, "Iron Grip", "Compressor", FxKind.EFFECT,
            listOf(
                FxParam("Threshold", -60f, 0f, "dB", 0, 0.48f, FxCurve.LINEAR, false),
                FxParam("Ratio", 1f, 20f, ":1", 1, 0.35f, FxCurve.RATIO_STEP, false),
                FxParam("Attack", 0.1f, 100f, "ms", 1, 0.25f, FxCurve.LINEAR, false),
                FxParam("Release", 10f, 1000f, "ms", 0, 0.52f, FxCurve.LINEAR, false),
                FxParam("Knee", 0f, 24f, "dB", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Makeup", 0f, 24f, "dB", 1, 0.4f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Glue", listOf(0.55f, 0.2f, 0.35f, 0.6f, 0.6f, 0.3f)),
                FxPreset("Gentle", listOf(0.4f, 0f, 0.25f, 0.5f, 0.7f, 0.2f)),
                FxPreset("Punch", listOf(0.5f, 0.6f, 0.6f, 0.3f, 0.3f, 0.5f)),
                FxPreset("Limit", listOf(0.7f, 1f, 0.05f, 0.2f, 0.1f, 0.6f)),
                FxPreset("Vocal Leveler", listOf(0.45f, 0.4f, 0.3f, 0.55f, 0.8f, 0.35f)),
                FxPreset("Drum Bus", listOf(0.5f, 0.4f, 0.2f, 0.25f, 0.3f, 0.45f)),
                FxPreset("Parallel", listOf(0.6f, 0.8f, 0.1f, 0.4f, 0.2f, 0.5f)),
            )),
        FxInfo(4, "Solar Ceiling", "Maximizer", FxKind.EFFECT,
            listOf(
                FxParam("Amount", 0f, 100f, "%", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Ceiling", -6f, 0f, "dB", 1, 0.92f, FxCurve.LINEAR, false),
                FxParam("Release", 10f, 500f, "ms", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Width", 0f, 100f, "%", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Mix", 0f, 100f, "%", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Output", -12f, 12f, "dB", 1, 0.5f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Transparent", listOf(0.3f, 0.95f, 0.6f, 0.2f, 1f, 0.5f)),
                FxPreset("Loud", listOf(0.75f, 0.9f, 0.35f, 0.4f, 1f, 0.55f)),
                FxPreset("Punch", listOf(0.6f, 0.9f, 0.2f, 0.2f, 0.9f, 0.55f)),
                FxPreset("Wide", listOf(0.5f, 0.92f, 0.5f, 0.8f, 1f, 0.5f)),
                FxPreset("Master Glue", listOf(0.4f, 0.93f, 0.55f, 0.5f, 1f, 0.5f)),
                FxPreset("Warm Tape", listOf(0.55f, 0.9f, 0.45f, 0.3f, 0.85f, 0.52f)),
                FxPreset("Radio", listOf(0.85f, 0.85f, 0.2f, 0.1f, 1f, 0.58f)),
            )),
        FxInfo(5, "Aether Piano", "Instrument", FxKind.SHAPER,
            listOf(
                FxParam("Felt", 0f, 100f, " %", 0, 0.8f, FxCurve.LINEAR, false),
                FxParam("Lid", 0f, 100f, " %", 0, 0.35f, FxCurve.LINEAR, false),
                FxParam("Pedal", 0f, 100f, " %", 0, 0.55f, FxCurve.LINEAR, false),
                FxParam("Room", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Width", 0f, 100f, " %", 0, 0.7f, FxCurve.LINEAR, false),
                FxParam("Tone", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Body", 0f, 100f, " %", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("Space", 0f, 100f, " %", 0, 0.25f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Felt", listOf(0.8f, 0.35f, 0.55f, 0.3f, 0.7f, 0.4f, 0.6f, 0.25f, 0.8f)),
                FxPreset("Studio", listOf(0.4f, 0.6f, 0.35f, 0.15f, 0.6f, 0.55f, 0.4f, 0.1f, 0.8f)),
                FxPreset("Upright", listOf(0.5f, 0.4f, 0.4f, 0.2f, 0.4f, 0.45f, 0.7f, 0.2f, 0.8f)),
                FxPreset("Concert", listOf(0.55f, 0.7f, 0.7f, 0.6f, 0.9f, 0.5f, 0.5f, 0.6f, 0.8f)),
                FxPreset("Mellow", listOf(0.9f, 0.2f, 0.5f, 0.3f, 0.7f, 0.3f, 0.65f, 0.3f, 0.8f)),
                FxPreset("Bright", listOf(0.2f, 0.85f, 0.45f, 0.2f, 0.6f, 0.75f, 0.4f, 0.15f, 0.8f)),
            )),
        FxInfo(6, "Aether Organ", "Instrument", FxKind.SOURCE,
            listOf(
                FxParam("Upper 16'", 0f, 8f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Upper 5⅓'", 0f, 8f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Upper 8'", 0f, 8f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Upper 4'", 0f, 8f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Upper 2⅔'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Upper 2'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Upper 1⅗'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Upper 1⅓'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Upper 1'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Lower 16'", 0f, 8f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Lower 5⅓'", 0f, 8f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Lower 8'", 0f, 8f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Lower 4'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Lower 2⅔'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Lower 2'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Lower 1⅗'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Lower 1⅓'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Lower 1'", 0f, 8f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Perc on", 0f, 1f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Perc soft", 0f, 1f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Perc fast", 0f, 1f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Perc 3rd", 0f, 1f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Vibrato", 0f, 5f, "", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("Vib upper", 0f, 1f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Vib lower", 0f, 1f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Rotary", 0f, 2f, "", 0, 1f, FxCurve.LINEAR, false),
                FxParam("Click", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Tone", 0f, 100f, " %", 0, 0.55f, FxCurve.LINEAR, false),
                FxParam("Drive", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Space", 0f, 100f, " %", 0, 0.25f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Gospel", listOf(1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.6f, 1f, 1f, 1f, 0.3f, 0.55f, 0.4f, 0.25f, 0.8f)),
                FxPreset("Full", listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 0.5f, 0.3f, 0.5f, 0.2f, 0.2f, 0.8f)),
                FxPreset("Jazz", listOf(1f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0.2f, 1f, 0f, 1f, 0.35f, 0.5f, 0.3f, 0.2f, 0.8f)),
                FxPreset("Rock", listOf(1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0.4f, 0.6f, 0.7f, 0.15f, 0.8f)),
                FxPreset("Balanced", listOf(0.75f, 1f, 1f, 0.75f, 0.5f, 0.625f, 0.375f, 0.25f, 0.25f, 0f, 0f, 1f, 0.625f, 0f, 0.375f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0.4f, 1f, 1f, 0.5f, 0.3f, 0.5f, 0.3f, 0.25f, 0.8f)),
                FxPreset("Whisper", listOf(0f, 0f, 0.5f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0.625f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 0.5f, 0.2f, 0.4f, 0.1f, 0.35f, 0.8f)),
            )),
        FxInfo(7, "Aether Sax", "Instrument", FxKind.SHAPER,
            listOf(
                FxParam("Breath", 0f, 100f, " %", 0, 0.55f, FxCurve.LINEAR, false),
                FxParam("Reed", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Growl", 0f, 100f, " %", 0, 0.15f, FxCurve.LINEAR, false),
                FxParam("Vibrato", 0f, 100f, " %", 0, 0.35f, FxCurve.LINEAR, false),
                FxParam("Rate", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Tone", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Body", 0f, 100f, " %", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("Space", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Smooth", listOf(0.55f, 0.4f, 0.1f, 0.3f, 0.35f, 0.45f, 0.65f, 0.3f, 0.8f)),
                FxPreset("Breathy", listOf(0.85f, 0.3f, 0.05f, 0.4f, 0.4f, 0.5f, 0.5f, 0.35f, 0.8f)),
                FxPreset("Growl", listOf(0.7f, 0.7f, 0.8f, 0.25f, 0.45f, 0.6f, 0.6f, 0.2f, 0.8f)),
                FxPreset("Club", listOf(0.5f, 0.5f, 0.2f, 0.5f, 0.4f, 0.4f, 0.7f, 0.6f, 0.8f)),
                FxPreset("Bright", listOf(0.6f, 0.8f, 0.15f, 0.3f, 0.5f, 0.8f, 0.4f, 0.25f, 0.8f)),
                FxPreset("Subtone", listOf(0.45f, 0.2f, 0.1f, 0.2f, 0.3f, 0.25f, 0.8f, 0.3f, 0.8f)),
            )),
        FxInfo(8, "Aether Strings", "Instrument", FxKind.SHAPER,
            listOf(
                FxParam("Pressure", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Speed", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Motion", 0f, 100f, " %", 0, 0.35f, FxCurve.LINEAR, false),
                FxParam("Morph", 0f, 100f, " %", 0, 0.1f, FxCurve.LINEAR, false),
                FxParam("Time", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Color", 0f, 100f, " %", 0, 0.1f, FxCurve.LINEAR, true),
                FxParam("Tone", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Body", 0f, 100f, " %", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("Space", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Solo", listOf(0.55f, 0.5f, 0.5f, 0.05f, 0.5f, 0.1f, 0.5f, 0.55f, 0.25f, 0.8f)),
                FxPreset("Chamber", listOf(0.5f, 0.45f, 0.4f, 0.35f, 0.45f, 0.55f, 0.45f, 0.6f, 0.35f, 0.8f)),
                FxPreset("Orchestra", listOf(0.45f, 0.4f, 0.3f, 0.9f, 0.6f, 0.75f, 0.5f, 0.6f, 0.55f, 0.8f)),
                FxPreset("Cinematic", listOf(0.6f, 0.3f, 0.25f, 1f, 0.75f, 0.92f, 0.35f, 0.7f, 0.8f, 0.8f)),
                FxPreset("Warm", listOf(0.4f, 0.5f, 0.45f, 0.5f, 0.4f, 0.02f, 0.25f, 0.75f, 0.4f, 0.8f)),
                FxPreset("Bright", listOf(0.65f, 0.65f, 0.3f, 0.4f, 0.3f, 0.5f, 0.8f, 0.4f, 0.3f, 0.8f)),
            )),
        FxInfo(9, "Aether Guitar", "Instrument", FxKind.SHAPER,
            listOf(
                FxParam("Body", 0f, 3f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Pick", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Strum", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Mute", 0f, 100f, " %", 0, 0.1f, FxCurve.LINEAR, false),
                FxParam("Bend", 0f, 100f, " %", 0, 0.2f, FxCurve.LINEAR, false),
                FxParam("Pickup", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Finish", 0f, 3f, "", 0, 0.33f, FxCurve.LINEAR, true),
                FxParam("Tone", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Resonance", 0f, 100f, " %", 0, 0.7f, FxCurve.LINEAR, false),
                FxParam("Space", 0f, 100f, " %", 0, 0.25f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Acoustic", listOf(0f, 0.45f, 0.4f, 0.1f, 0f, 0.5f, 0.33f, 0.5f, 0.7f, 0.25f, 0.8f)),
                FxPreset("Nylon", listOf(0.33f, 0.25f, 0.3f, 0.15f, 0f, 0.45f, 0f, 0.4f, 0.75f, 0.3f, 0.8f)),
                FxPreset("Clean", listOf(0.67f, 0.55f, 0.45f, 0.1f, 0.1f, 0.5f, 0.33f, 0.55f, 0.4f, 0.3f, 0.8f)),
                FxPreset("Crunch", listOf(1f, 0.65f, 0.45f, 0.2f, 0.2f, 0.7f, 0.67f, 0.6f, 0.35f, 0.2f, 0.8f)),
                FxPreset("Lead", listOf(0.67f, 0.75f, 0.2f, 0f, 0.6f, 0.9f, 1f, 0.7f, 0.3f, 0.35f, 0.8f)),
                FxPreset("Ambient", listOf(0f, 0.4f, 0.6f, 0f, 0.1f, 0.5f, 0f, 0.35f, 0.5f, 0.8f, 0.8f)),
            )),
        FxInfo(10, "Aether Amp", "Amp", FxKind.EFFECT,
            listOf(
                FxParam("Gain", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Bass", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Mid", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Treble", 0f, 100f, " %", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("Presence", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Master", 0f, 100f, " %", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("Channel", 0f, 2f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Mic pos", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Mic dist", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Boost", 0f, 1f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Tremolo", 0f, 1f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Clean", listOf(0.3f, 0.5f, 0.5f, 0.6f, 0.5f, 0.6f, 0f, 0.4f, 0.4f, 0f, 0f, 0.8f)),
                FxPreset("Crunch", listOf(0.6f, 0.55f, 0.6f, 0.55f, 0.55f, 0.55f, 0.5f, 0.5f, 0.4f, 0f, 0f, 0.8f)),
                FxPreset("Lead", listOf(0.8f, 0.5f, 0.65f, 0.6f, 0.6f, 0.5f, 1f, 0.55f, 0.35f, 1f, 0f, 0.8f)),
                FxPreset("Blues", listOf(0.5f, 0.6f, 0.55f, 0.5f, 0.45f, 0.55f, 0.5f, 0.4f, 0.5f, 0f, 1f, 0.8f)),
                FxPreset("Jazz", listOf(0.2f, 0.65f, 0.4f, 0.4f, 0.3f, 0.6f, 0f, 0.3f, 0.6f, 0f, 0f, 0.8f)),
                FxPreset("Metal", listOf(0.95f, 0.6f, 0.4f, 0.7f, 0.7f, 0.5f, 1f, 0.6f, 0.3f, 1f, 0f, 0.8f)),
            )),
        FxInfo(11, "Aether Bass", "Amp", FxKind.EFFECT,
            listOf(
                FxParam("Drive", 0f, 100f, " %", 0, 0.25f, FxCurve.LINEAR, false),
                FxParam("Comp", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Blend", 0f, 100f, " %", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("63 Hz", 0f, 100f, " %", 0, 0.55f, FxCurve.LINEAR, false),
                FxParam("250 Hz", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("1 kHz", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("3 kHz", 0f, 100f, " %", 0, 0.45f, FxCurve.LINEAR, false),
                FxParam("8 kHz", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Cab", 0f, 2f, "", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Motown", listOf(0.15f, 0.5f, 0.3f, 0.6f, 0.45f, 0.4f, 0.3f, 0.25f, 0.5f, 0.8f)),
                FxPreset("Rock", listOf(0.5f, 0.55f, 0.7f, 0.5f, 0.5f, 0.65f, 0.6f, 0.5f, 1f, 0.8f)),
                FxPreset("Slap", listOf(0.35f, 0.6f, 0.8f, 0.7f, 0.3f, 0.45f, 0.7f, 0.75f, 0.5f, 0.8f)),
                FxPreset("Fuzz", listOf(0.9f, 0.4f, 0.9f, 0.5f, 0.5f, 0.7f, 0.65f, 0.4f, 1f, 0.8f)),
                FxPreset("Dub", listOf(0.2f, 0.7f, 0.3f, 0.9f, 0.4f, 0.2f, 0.2f, 0.15f, 0f, 0.8f)),
                FxPreset("Clean DI", listOf(0f, 0.3f, 0f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.8f)),
            )),
        FxInfo(12, "Aether Brass", "Instrument", FxKind.SHAPER,
            listOf(
                FxParam("Lips", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Bite", 0f, 100f, " %", 0, 0.45f, FxCurve.LINEAR, false),
                FxParam("Vibrato", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Section", 0f, 4f, "", 0, 0.25f, FxCurve.LINEAR, false),
                FxParam("Mute", 0f, 4f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Tone", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Body", 0f, 100f, " %", 0, 0.6f, FxCurve.LINEAR, false),
                FxParam("Space", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Open", listOf(0.5f, 0.45f, 0.3f, 0.25f, 0f, 0.5f, 0.6f, 0.3f, 0.8f)),
                FxPreset("Muted Jazz", listOf(0.4f, 0.3f, 0.4f, 0f, 0.75f, 0.35f, 0.7f, 0.4f, 0.8f)),
                FxPreset("Section Stab", listOf(0.7f, 0.8f, 0.1f, 1f, 0f, 0.7f, 0.55f, 0.4f, 0.8f)),
                FxPreset("Fanfare", listOf(0.8f, 0.7f, 0.2f, 0.75f, 0f, 0.8f, 0.5f, 0.55f, 0.8f)),
                FxPreset("Soft", listOf(0.3f, 0.2f, 0.45f, 0.25f, 0.5f, 0.3f, 0.7f, 0.35f, 0.8f)),
                FxPreset("Harmon", listOf(0.55f, 0.5f, 0.25f, 0f, 0.75f, 0.55f, 0.45f, 0.3f, 0.8f)),
            )),
        FxInfo(13, "Aether Voices", "Voice", FxKind.EFFECT,
            listOf(
                FxParam("Vowel X", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Vowel Y", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Breath", 0f, 100f, " %", 0, 0.3f, FxCurve.LINEAR, false),
                FxParam("Choir", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Vibrato", 0f, 100f, " %", 0, 0.35f, FxCurve.LINEAR, false),
                FxParam("Tone", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Body", 0f, 100f, " %", 0, 0.5f, FxCurve.LINEAR, false),
                FxParam("Space", 0f, 100f, " %", 0, 0.45f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Ah", listOf(0.5f, 0.1f, 0.3f, 0.5f, 0.35f, 0.5f, 0.5f, 0.45f, 0.8f)),
                FxPreset("Ooh", listOf(0.9f, 0.9f, 0.2f, 0.6f, 0.3f, 0.35f, 0.5f, 0.5f, 0.8f)),
                FxPreset("Eeh", listOf(0.1f, 0.9f, 0.3f, 0.5f, 0.35f, 0.6f, 0.45f, 0.4f, 0.8f)),
                FxPreset("Ensemble", listOf(0.5f, 0.5f, 0.25f, 0.9f, 0.4f, 0.5f, 0.55f, 0.6f, 0.8f)),
                FxPreset("Whisper", listOf(0.5f, 0.5f, 0.9f, 0.4f, 0.1f, 0.4f, 0.4f, 0.5f, 0.8f)),
                FxPreset("Angels", listOf(0.7f, 0.6f, 0.35f, 1f, 0.45f, 0.7f, 0.6f, 0.85f, 0.8f)),
            )),
        FxInfo(14, "Aether Synth", "Instrument", FxKind.SOURCE,
            listOf(
                FxParam("Wave A", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Wave B", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Detune", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Sub", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Noise", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Unison", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Spread", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Cutoff", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Resonance", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Drive", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Env amt", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Filter", 0f, 3f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Attack", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Decay", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Sustain", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Release", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("LFO1 rate", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("LFO1 depth", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("LFO1 shape", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("LFO1 dest", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("LFO2 rate", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("LFO2 depth", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Macro 1", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Macro 2", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Macro 3", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Macro 4", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Mode", 0f, 2f, "", 0, 0f, FxCurve.LINEAR, false),
                FxParam("Glide", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Voices", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Bend", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Tune", 0f, 100f, " %", 0, 0.4f, FxCurve.LINEAR, false),
                FxParam("Level", -24f, 6f, " dB", 1, 0.8f, FxCurve.LINEAR, false),
            ),
            listOf(
                FxPreset("Pad", listOf(0.3f, 0.5f, 0.3f, 0.2f, 0.05f, 0.7f, 0.7f, 0.35f, 0.2f, 0.15f, 0.35f, 0f, 0.7f, 0.5f, 0.7f, 0.8f, 0.3f, 0.3f, 0f, 0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f)),
                FxPreset("Lead", listOf(0.9f, 0.6f, 0.2f, 0.2f, 0.05f, 0.3f, 0.4f, 0.6f, 0.4f, 0.5f, 0.35f, 0f, 0.05f, 0.4f, 0.8f, 0.3f, 0.3f, 0.3f, 0f, 0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f)),
                FxPreset("Bass", listOf(0.6f, 0.2f, 0.2f, 0.7f, 0.05f, 0f, 0.4f, 0.25f, 0.35f, 0.4f, 0.5f, 0f, 0.02f, 0.4f, 0.6f, 0.2f, 0.3f, 0.3f, 0f, 0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0.5f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f)),
                FxPreset("Pluck", listOf(0.5f, 0.5f, 0.2f, 0.2f, 0.05f, 0.3f, 0.4f, 0.5f, 0.25f, 0.15f, 0.6f, 0f, 0.01f, 0.25f, 0.1f, 0.2f, 0.3f, 0.3f, 0f, 0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f)),
                FxPreset("Keys", listOf(0.1f, 0.2f, 0.15f, 0.2f, 0.05f, 0.3f, 0.4f, 0.45f, 0.25f, 0.15f, 0.35f, 0f, 0.03f, 0.5f, 0.5f, 0.4f, 0.3f, 0.3f, 0f, 0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f)),
                FxPreset("FX", listOf(1f, 0.4f, 0.8f, 0.2f, 0.5f, 0.3f, 0.4f, 0.5f, 0.7f, 0.15f, 0.35f, 0f, 0.1f, 0.4f, 0.7f, 0.35f, 0.7f, 0.8f, 0.5f, 0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f)),
            )),
    )

    fun info(id: Int): FxInfo = all[id]
    fun toReal(p: FxParam, norm: Float): Float = when (p.curve) {
        FxCurve.FREQ_LOG -> 20f * Math.pow(1000.0, norm.toDouble()).toFloat()
        FxCurve.RATIO_STEP -> floatArrayOf(1.5f, 2f, 3f, 4f, 8f, 20f)[Math.round(norm * 5f)]
        FxCurve.LINEAR -> p.min + (p.max - p.min) * norm
    }
}

````

---

## 2. Source de vérité visuelle — `fx-window-preview.html`

Fichier unique, autonome, ouvrable dans un navigateur. **C'est la référence** : le
JSON de mesures, les captures et le Kotlin en dérivent tous. À lire en premier.

Contient le canvas 825 × 343, la table `AB()` (potards), `TG()` (groupes de puces),
`SKN`/`KSH` (matière et forme des chapeaux), `RS` (rails), `<defs>` (les 15 dégradés
`c0..c14`), le CSS des classes `.xN`/`.pn`/`.bd`/`.cd`/`.bt`/`.chg`/`.kb`, et les 15
fonctions de scène.

### `fx-window-preview.html`

- **Chemin :** `/tmp/pkg/aether/fx-window-preview.html`
- **Taille :** 147198 octets · **lignes :** 728 · **sha256 (12) :** `c3fc45848c6b`
- Ouvrir dans un navigateur : c'est le rendu cible.

````html
<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>FX window</title>
<link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@600;800&family=Share+Tech+Mono&family=Space+Grotesk:wght@300;500&family=Sora:wght@500;800&family=JetBrains+Mono:wght@500;700&family=Plus+Jakarta+Sans:wght@500;700;800&display=swap" rel="stylesheet">
<style>
:root{box-sizing:border-box;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}
*{box-sizing:border-box;margin:0}
html,body{height:100%;background:#05070b;overflow:hidden;font-family:'Plus Jakarta Sans',system-ui,sans-serif;color:#e6eaf3}
#fit{position:fixed;left:50%;top:50%;width:var(--sw,851px);height:var(--sh,393px);transform-origin:center}
#st{position:relative;width:100%;height:100%;border-radius:28px;overflow:hidden;background:#0e1420;box-shadow:0 0 0 7px #000,0 0 0 8px #232a3a,0 30px 60px #000c}
.mt{height:34px;background:linear-gradient(#171f2e,#121926);border-bottom:1px solid #ffffff10}
#mx{position:absolute;inset:0;display:flex;flex-wrap:wrap;gap:8px;padding:34px 10px 10px;align-content:stretch;opacity:.55}
#mx .mt{position:absolute;left:0;right:0;top:0}
.mb{position:absolute;left:12px;right:12px;bottom:10px;height:44px;display:flex;gap:8px;align-items:center;opacity:.75}.mb i{flex:1;height:100%;border-radius:12px;background:linear-gradient(#1a2232,#131a27);border:1px solid #ffffff0d;font:700 11px/42px 'Plus Jakarta Sans';font-style:normal;text-align:center;color:#8d96ab}.mb i.a{background:#9db8f2;color:#0b0f1c}
.ch{flex:1;border-radius:12px;margin-top:8px;background:linear-gradient(#1a2232,#131a27);border:1px solid #ffffff0d;position:relative}
.ch .r{position:absolute;left:50%;top:50px;bottom:30px;width:10px;margin-left:-5px;border-radius:6px;background:#0b0f18;box-shadow:inset 0 1px 3px #000}
.ch .r b{position:absolute;left:50%;margin-left:-11px;width:22px;height:30px;border-radius:5px;background:linear-gradient(#6b7185,#454a5c);box-shadow:0 4px 8px #0009;top:var(--p)}
.ch .r b::after{content:"";position:absolute;left:4px;right:4px;top:13px;height:2px;background:var(--c);opacity:.8}
.ch .nm{display:block;margin:8px;height:22px;border-radius:8px;background:#0e1420;border:1px solid #ffffff0d}
#win{position:absolute;left:12px;right:12px;top:38px;bottom:10px;border-radius:20px;background:linear-gradient(#1f2635,#131925);border:1px solid #ffffff14;box-shadow:0 24px 50px #000b,inset 0 1px 0 #ffffff14;display:flex;flex-direction:column;overflow:hidden}
.wh{height:42px;display:flex;align-items:center;gap:10px;padding:0 12px;flex:none}
.wh h1{flex:1;font:800 12px 'Plus Jakarta Sans';letter-spacing:.16em;text-transform:uppercase;color:#8d96ab}
.bt{height:28px;min-width:28px;padding:0 12px;border-radius:9px;background:linear-gradient(#ffffff14,#ffffff08);border:1px solid #ffffff14;box-shadow:0 2px 4px #0006;display:flex;align-items:center;justify-content:center;font:700 11px 'Plus Jakarta Sans';color:inherit;cursor:pointer;transition:transform .12s}
.bt:active{transform:scale(.94)}
#rack{flex:1;display:flex;gap:12px;padding:4px 14px 14px;overflow-x:auto;scrollbar-width:none}
#rack::-webkit-scrollbar{display:none}
.cd{flex:0 0 158px;border-radius:16px;padding:10px;background:linear-gradient(#323845,#222733);border:1px solid #ffffff12;box-shadow:0 8px 16px #0008,inset 0 1px 0 #ffffff1c;cursor:pointer;display:flex;flex-direction:column;gap:6px;transition:transform .2s,box-shadow .2s}
.cd:hover{transform:translateY(-3px)}.cd:active{transform:scale(.97)}
.cd .t{font:700 8px 'Plus Jakarta Sans';letter-spacing:.16em;text-transform:uppercase;color:#8d96ab}
.cd .n{font-size:20px;line-height:1;color:#dfe4ee}
.cd canvas{width:136px;height:160px;border-radius:10px;background:#12161f;box-shadow:inset 0 2px 6px #000b,inset 0 0 0 1px #ffffff0a;align-self:center}
.cd .f{display:flex;align-items:center;gap:6px;font:600 9px 'Plus Jakarta Sans';color:#8d96ab;margin-top:auto}
.led{width:8px;height:8px;border-radius:50%;background:#2a3040;box-shadow:inset 0 1px 2px #000}
.on .led,.led.on{background:#c9d2e6;box-shadow:0 0 6px #c9d2e688}
.add{flex:0 0 74px;border-radius:16px;border:1.5px dashed #ffffff24;display:flex;align-items:center;justify-content:center;font-size:28px;color:#6d778c;cursor:pointer}
#ex{position:absolute;overflow:hidden;background:#10141d;z-index:4;transition:all .5s cubic-bezier(.2,.85,.2,1)}
#tr{position:absolute;left:0;top:0;width:var(--ww,819px);height:var(--wh,305px);display:flex;overflow-x:auto;scroll-snap-type:x mandatory;scrollbar-width:none;touch-action:pan-x}
#tr::-webkit-scrollbar{display:none}
.pn{flex:0 0 100%;scroll-snap-align:start;display:flex;flex-direction:column;position:relative;transition:filter .3s,opacity .3s}
.pn.by{filter:saturate(.2) brightness(.7)}
.ph{position:relative;z-index:3;height:58px;display:flex;align-items:center;gap:12px;padding:0 16px 0 58px;flex:none}
.ph .nm{font-size:30px;line-height:1}.ph .ty{font:700 9px 'Plus Jakarta Sans';letter-spacing:.18em;text-transform:uppercase;opacity:.6;margin-top:3px}
.ph .sp{flex:1}
.pb{flex:1;display:flex;gap:18px;padding:0 20px 24px;min-height:0}
.vz{flex:0 0 322px;display:flex;flex-direction:column;gap:8px}
.vz canvas{width:322px;height:172px;border-radius:14px;background:#0a0d14;box-shadow:inset 0 3px 10px #000,inset 0 0 0 1px #ffffff14,0 1px 0 #ffffff1a}
.vz small{font:600 10px 'Plus Jakarta Sans';opacity:.65}
.kg{flex:1;display:grid;grid-template-columns:repeat(3,1fr);gap:8px 6px;align-content:center}
.kn{display:flex;flex-direction:column;align-items:center;gap:1px}
.kn label{font:700 9px 'Plus Jakarta Sans';letter-spacing:.14em;text-transform:uppercase;opacity:.65}
.kb{width:70px;height:70px;touch-action:none;cursor:ns-resize;filter:drop-shadow(0 7px 7px #000a)}
.vl{font:700 11px 'Plus Jakarta Sans';font-variant-numeric:tabular-nums}
.x0{background:radial-gradient(110% 90% at 12% 0,#2b2c58,transparent 60%),radial-gradient(90% 70% at 100% 100%,#1c3b4a,transparent 55%),radial-gradient(1.2px 1.2px at 12% 22%,#fff9,#0000),radial-gradient(1px 1px at 58% 12%,#fff8,#0000),radial-gradient(1.4px 1.4px at 86% 40%,#fff9,#0000),radial-gradient(1px 1px at 38% 80%,#fff7,#0000),#0f1120;color:#e7e3f6}
.x1{background:repeating-linear-gradient(90deg,#ffffff08 0 1px,#0000 1px 3px),linear-gradient(#41382a,#1f1b14);color:#f0e4c8}
.x2{background:linear-gradient(#ffffff0a 1px,#0000 1px) 0 0/100% 22px,linear-gradient(90deg,#ffffff0a 1px,#0000 1px) 0 0/22px 100%,linear-gradient(135deg,#16242f,#0b121a);color:#dcecf3}
.x3{background:linear-gradient(#3f444d,#23262c);box-shadow:inset 0 2px 0 #ffffff22;color:#e3e8f0}
.x4{background:linear-gradient(#efe9db,#d6cdb9);color:#2a241b}.x4 .vz canvas{background:#17140f}
#dr{position:absolute;left:16px;right:16px;top:58px;z-index:6;border-radius:14px;background:#0c0f17f2;border:1px solid #ffffff14;display:grid;grid-template-columns:repeat(4,1fr);gap:8px;padding:0 12px;max-height:0;overflow:hidden;transition:max-height .4s cubic-bezier(.2,.8,.2,1),padding .4s;color:#e6eaf3}
#dr.o{max-height:140px;padding:12px}
#dr div{padding:16px 8px;border-radius:10px;background:#ffffff0c;border:1px solid #ffffff10;text-align:center;font:700 12px 'Plus Jakarta Sans';cursor:pointer}
#dr div.c{background:#ffffff22}
#dots{position:absolute;bottom:8px;left:0;right:0;display:flex;justify-content:center;gap:6px;z-index:5}
#dots i{width:6px;height:6px;border-radius:3px;background:#ffffff40;transition:all .25s}#dots i.a{width:18px;background:#fffc}
#cl{position:absolute;left:12px;top:12px;z-index:7}






#rot{position:fixed;right:10px;bottom:10px;z-index:9;display:none}
.bd{position:relative;flex:1;margin:0 20px 22px;min-height:0}.bd canvas{position:absolute;inset:0;width:100%;height:100%}canvas.dg{touch-action:none;cursor:crosshair}
.kn.ab{position:absolute;transform:translate(-50%,-50%);z-index:2}
.np{position:absolute;z-index:3;width:156px;padding:8px 10px;border-radius:12px;background:#0a1016ea;border:1px solid #ffffff1c;box-shadow:0 8px 18px #0009;display:flex;align-items:center;gap:8px;font:700 10px 'Plus Jakarta Sans';color:#dcecf3;transition:left .2s,top .2s}
.pp{position:relative;width:150px;height:30px;z-index:7;flex:none}
.ppi{position:absolute;right:0;top:0;width:150px;max-height:30px;overflow:hidden;border-radius:10px;background:linear-gradient(#ffffff18,#ffffff0a),#0d1119;border:1px solid #ffffff1a;color:#e6eaf3;transition:max-height .4s cubic-bezier(.2,.9,.2,1),box-shadow .3s}
.pp.o .ppi{max-height:230px;box-shadow:0 14px 28px #000a}
.ph0{height:28px;display:flex;justify-content:space-between;align-items:center;padding:0 12px;font:700 11px 'Plus Jakarta Sans';cursor:pointer}
.pi{padding:8px 12px;font:600 11px 'Plus Jakarta Sans';opacity:0;transform:translateY(-5px);transition:opacity .3s,transform .3s;cursor:pointer;border-top:1px solid #ffffff0d}
.pp.o .pi{opacity:1;transform:none}.pi:hover{background:#ffffff14}.pi.c{color:#f2d58a}.pi.sv{opacity:.6}

.sl{flex:0 0 158px;border-radius:16px;background:linear-gradient(#0a0d14,#141925);box-shadow:inset 0 6px 14px #000c,inset 0 0 0 1px #ffffff0c,0 1px 0 #ffffff12;position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;cursor:pointer}
.sl.d{opacity:.5}.sl.pl{flex:0 0 74px}#rack.em{padding-top:10px}
.sx{width:46px;height:46px;border-radius:50%;background:radial-gradient(#3c4457,#1b2030);box-shadow:0 4px 8px #000a,inset 0 1px 0 #ffffff30;display:flex;align-items:center;justify-content:center;font-size:24px;color:#c8d0e4;animation:br 2.6s ease-in-out infinite}
@keyframes br{50%{box-shadow:0 0 0 9px #ffffff0c,0 4px 8px #000a,inset 0 1px 0 #ffffff30}}
.st{font:700 10px 'Plus Jakarta Sans';letter-spacing:.14em;text-transform:uppercase;color:#6d778c}
.sc{position:absolute;left:10px;top:10px;width:8px;height:8px;border-radius:50%;background:radial-gradient(#5a6174,#1e2230)}.sc.b{left:auto;right:10px}
.cd.pop{animation:pop .55s cubic-bezier(.2,1.4,.3,1)}@keyframes pop{from{transform:scale(.6) translateY(24px);opacity:0}}
.cd.lift{z-index:20;box-shadow:0 22px 34px #000c;transition:box-shadow .2s;position:relative}
#tz{position:absolute;left:14px;right:14px;bottom:-70px;height:52px;border-radius:14px;background:linear-gradient(#3a1518,#2a0e11);border:1px solid #ff6b6b33;display:flex;align-items:center;justify-content:center;gap:10px;font:700 12px 'Plus Jakarta Sans';color:#ffb4b4;transition:bottom .35s cubic-bezier(.2,.9,.2,1),background .2s;z-index:6}
#tz.o{bottom:12px}#tz.hot{background:#6a1f25;box-shadow:0 0 0 2px #ff6b6b66}
#ub{position:absolute;left:50%;bottom:14px;transform:translate(-50%,90px);display:flex;gap:16px;align-items:center;padding:10px 18px;border-radius:14px;background:#2a3042;border:1px solid #ffffff1c;box-shadow:0 10px 24px #000a;font:600 12px 'Plus Jakarta Sans';z-index:12;transition:transform .4s cubic-bezier(.2,.9,.2,1)}
#ub.o{transform:translate(-50%,0)}#ub b{color:#f2d58a;cursor:pointer}
#ad{position:absolute;inset:0;z-index:10;background:linear-gradient(#1b2231,#10141d);border-radius:20px;display:flex;flex-direction:column;align-items:stretch;justify-content:flex-start;transform:translateY(102%);transition:transform .5s cubic-bezier(.2,.9,.2,1)}
#ad.o{transform:none}.ah{height:48px;display:flex;align-items:center;justify-content:space-between;padding:0 16px;flex:none}
.ah h4{font:800 12px 'Plus Jakarta Sans';letter-spacing:.16em;text-transform:uppercase;color:#8d96ab}
.ag{flex:1;display:grid;grid-template-columns:1fr 1fr 1fr 1.15fr;gap:12px;padding:0 16px 16px;min-height:0}
.ag section{display:flex;flex-direction:column;gap:8px}.ag h5{font:700 9px 'Plus Jakarta Sans';letter-spacing:.16em;color:#8d96ab;text-transform:uppercase}
.tl{display:flex;gap:10px;align-items:center;padding:10px;border-radius:14px;background:linear-gradient(#ffffff12,#ffffff06);border:1px solid #ffffff12;cursor:pointer;position:relative;transition:transform .15s}.tl:active{transform:scale(.97)}
.tl .ti{width:38px;height:38px;color:#cfd6e6;flex:none}.tl b{display:block;font-size:16px;font-weight:400;line-height:1.1}.tl span{display:block;font:600 9px 'Plus Jakarta Sans';color:#8d96ab;margin-top:2px}
.tl.dis{opacity:.4;pointer-events:none}.tl.soon{opacity:.5}.tl em{position:absolute;right:8px;top:6px;font:700 8px 'Plus Jakarta Sans';font-style:normal;color:#9fe0b0}.tl.soon em{color:#8d96ab}
#pm{position:absolute;right:16px;top:14px;width:150px;max-height:30px;overflow:hidden;z-index:9;border-radius:10px;background:linear-gradient(#ffffff18,#ffffff0a),#0d1119;border:1px solid #ffffff1a;color:#e6eaf3;opacity:0;pointer-events:none;transition:max-height .4s cubic-bezier(.2,.9,.2,1),box-shadow .3s,opacity .3s}
#pm.v{opacity:1;pointer-events:auto}#pm.o{max-height:230px;box-shadow:0 14px 28px #000a}#pm.o .pi{opacity:1;transform:none}
.np{width:auto;padding:6px 10px;line-height:1.35}
.x2{background:radial-gradient(130% 70% at 18% -10%,#ff8a8a33,transparent 60%),repeating-linear-gradient(90deg,#ffffff07 0 1px,#0000 1px 3px),linear-gradient(160deg,#c4182c 0%,#93111f 46%,#5a0a14 100%);color:#fff1ea}
.x2 .kn label,.x2 .vl{color:#ffe9e2}.x2 .vl{font-family:'Share Tech Mono',monospace;font-weight:400;font-size:12px}.x2 .kn label{font-family:'Share Tech Mono',monospace;font-weight:400;opacity:.85}
.kb{position:relative}.kb::after{content:'';position:absolute;inset:-12px}
#pm{width:176px;top:11px;max-height:36px}#pm.o{max-height:300px}.ph0{height:34px;font-size:12px;padding:0 14px;touch-action:manipulation}.pi{padding:11px 14px;font-size:12px}
#pml{max-height:236px;overflow-y:auto;touch-action:pan-y;scrollbar-width:none}#pml::-webkit-scrollbar{display:none}
.bd canvas.vc{inset:auto;left:calc(var(--ox,20)*-1px);top:calc(var(--oy,58)*-1px);width:calc(var(--pw,825)*1px);height:calc(var(--ph,343)*1px)}.tab .bd{position:absolute;margin:0;flex:none;left:calc(var(--oxp)*1px);top:calc(var(--oyp)*1px);width:779px;height:263px;transform:scale(var(--ks));transform-origin:0 0}
#tb{position:fixed;left:10px;bottom:10px;z-index:9;height:30px;padding:0 14px;border-radius:99px;background:#10141fe6;border:1px solid #ffffff22;font:700 11px 'Plus Jakarta Sans';letter-spacing:.08em;display:flex;align-items:center;cursor:pointer;color:#e6eaf3}
.x5{background:#120a06;color:#f3e6cc}.x5 .kn label,.x5 .vl{color:#f3e6cc}
.slotc{font:700 10px 'Plus Jakarta Sans';letter-spacing:.12em;color:#8d96ab;text-transform:uppercase;margin-right:6px}
.ci{display:inline-block;width:13px;height:13px;vertical-align:-3px;margin-right:6px;color:#aab3c8}.ci svg{width:100%;height:100%}

.x6{background:#120a06;color:#f3e6cc}.x6 .kn label,.x6 .vl{color:#f3e6cc}
.ib{height:28px;padding:0 11px;border-radius:9px;background:linear-gradient(#ffffff14,#ffffff08);border:1px solid #ffffff1a;font:700 10px 'Plus Jakarta Sans';letter-spacing:.06em;display:flex;align-items:center;cursor:pointer;touch-action:manipulation;white-space:nowrap}
.ib:active{transform:scale(.95)}.ib.on{background:#f2d58a;color:#2a1a10}
.pn.ins .ph{padding-right:200px}
.kn label.lk::after{content:'\25CF';color:#f2d58a;margin-left:4px;font-size:7px}
.chg{position:absolute;z-index:2;display:flex;gap:4px;align-items:center}.chg span{width:46px;font:700 8px 'Plus Jakarta Sans';letter-spacing:.12em;color:#c9b88f}
.chg i{font-style:normal;padding:6px 8px;border-radius:7px;background:#00000066;border:1px solid #ffffff14;font:700 10px 'Plus Jakarta Sans';cursor:pointer;color:#e9dcc0;touch-action:manipulation}.chg i.a{background:#f2d58a;color:#2a1a10}
#ed{position:absolute;right:0;top:58px;bottom:0;width:272px;z-index:8;background:#0d0a08f2;border-left:1px solid #ffffff18;transform:translateX(102%);transition:transform .45s cubic-bezier(.2,.9,.2,1);padding:12px 16px;overflow-y:auto;scrollbar-width:none;color:#f3e6cc}
#ed.o{transform:none}#ed h6{font:800 9px 'Plus Jakarta Sans';letter-spacing:.16em;color:#c9b88f;margin:10px 0 4px;text-transform:uppercase}
#ed label{display:flex;justify-content:space-between;font:600 11px 'Plus Jakarta Sans';margin-top:6px}#ed label b{font:400 11px 'Share Tech Mono',monospace;color:#f2d58a}
#ed input{width:100%;accent-color:#f2d58a;height:22px;touch-action:pan-y}
.ag section.ins{display:grid;grid-template-columns:1fr 1fr;gap:8px;align-content:start}.ag section.ins h5{grid-column:1/-1}.ag section.ins .tl{padding:8px}.ag section.ins .tl em{top:auto;bottom:4px;right:6px;font-size:7px}.ag section.ins .tl .ti{width:28px;height:28px}.ag section.ins .tl b{font-size:13px}
.ag{display:flex;flex-direction:column;gap:8px;padding:0 16px 14px;overflow:hidden;grid-template-columns:none}.cf{display:flex;gap:6px;align-items:center;overflow-x:auto;scrollbar-width:none;flex:none}.cf::-webkit-scrollbar{display:none}.cf span{flex:none;padding:6px 12px;border-radius:99px;background:#ffffff0e;border:1px solid #ffffff14;font:700 10px 'Plus Jakarta Sans';letter-spacing:.06em;cursor:pointer}.cf span.a{background:#f2f4fa;color:#0b0f1c}.cf input{flex:none;margin-left:auto;width:130px;height:28px;border-radius:99px;border:1px solid #ffffff18;background:#00000044;color:#fff;padding:0 12px;font:600 11px 'Plus Jakarta Sans'}.gl{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;overflow-y:auto;flex:1;min-height:0;align-content:start}.gl .tl{padding:9px}.gl .tl em{top:auto;bottom:4px;right:6px;font-size:7px}
.spg{position:absolute;inset:0;pointer-events:none;display:none}.spg>*{pointer-events:auto}.pn[data-pg="0"] .spg[data-p="0"],.pn[data-pg="1"] .spg[data-p="1"],.pn[data-pg="2"] .spg[data-p="2"]{display:block}
.pgt{position:absolute;display:flex;gap:4px;z-index:4}.pgt i{font-style:normal;padding:5px 9px;border-radius:99px;background:#ffffff10;border:1px solid #ffffff18;font:700 9px 'Plus Jakarta Sans';letter-spacing:.08em;cursor:pointer;touch-action:manipulation}.pgt i.a{background:#7ad0ff;color:#04101c}
.x10{background:#0a0806;color:#f3e6cc}.x11{background:#0a0806;color:#f3e6cc}.x12{background:#0a0806;color:#f3e6cc}.x13{background:#05040c;color:#e8e4ff}.x14{background:#04060c;color:#e0f2ff}
.x10 .kn label,.x10 .vl,.x11 .kn label,.x11 .vl,.x12 .kn label,.x12 .vl,.x13 .kn label,.x13 .vl,.x14 .kn label,.x14 .vl{color:#f3e6cc}
.x5 .kn label,.x5 .vl{font-family:'Sora'}.x6 .kn label,.x6 .vl{font-family:'Share Tech Mono',monospace}.x7 .kn label,.x7 .vl{font-family:'Sora'}.x8 .kn label,.x8 .vl{font-family:'Space Grotesk'}.x9 .kn label,.x9 .vl{font-family:'JetBrains Mono',monospace}.x10 .kn label,.x10 .vl{font-family:'Orbitron'}.x11 .kn label,.x11 .vl{font-family:'Sora'}.x12 .kn label,.x12 .vl{font-family:'Space Grotesk'}.x13 .kn label,.x13 .vl{font-family:'Space Grotesk';font-weight:300}.x14 .kn label,.x14 .vl{font-family:'Share Tech Mono',monospace}
.tab .pn.x14 .ph .nm{font-size:18px!important}
.tbs{display:flex;gap:8px;flex:none}.tbs span{padding:8px 16px;border-radius:12px;background:#ffffff0c;border:1px solid #ffffff14;font:800 11px 'Plus Jakarta Sans';letter-spacing:.1em;cursor:pointer}.tbs span.a{background:#f2f4fa;color:#0b0f1c}.tbs b{font-weight:700;opacity:.6;margin-left:6px}
.cd.isins{box-shadow:inset 0 0 0 1px #e8c98a66}
#sf,#pi{white-space:nowrap}#sf{max-width:200px;overflow:hidden;text-overflow:ellipsis}
.warn{position:absolute;left:14px;right:14px;top:64px;z-index:7;display:none;align-items:center;gap:12px;padding:10px 14px;border-radius:14px;background:linear-gradient(#7a2216f2,#4a120cf2);border:1.5px solid #ff7a5a;box-shadow:0 10px 24px #000a;color:#ffe9e0;font:600 11px/1.35 'Plus Jakarta Sans'}.warn .wi{font-size:26px;line-height:1}.warn .wt{flex:1}.warn .wt b{display:block;font-size:12px;margin-bottom:2px}.warn .wb{display:flex;flex-direction:column;gap:5px}.warn .wb i{font-style:normal;padding:6px 10px;border-radius:9px;background:#ffffff1c;border:1px solid #ffffff30;font:800 9.5px 'Plus Jakarta Sans';letter-spacing:.06em;cursor:pointer;text-align:center;white-space:nowrap}.warn .wb i:first-child{background:#ffe9e0;color:#4a120c}
#db{position:fixed;left:96px;bottom:10px;z-index:9;height:30px;padding:0 12px;border-radius:99px;background:#10141fe6;border:1px solid #ffffff22;font:700 11px 'Plus Jakarta Sans';letter-spacing:.08em;display:flex;align-items:center;cursor:pointer;color:#8d96ab}
#dbg{position:fixed;left:10px;bottom:48px;z-index:9;display:none;padding:8px 10px;border-radius:10px;background:#000c;border:1px solid #ffffff22;font:400 10px/1.5 'Share Tech Mono',monospace;color:#9fe;white-space:pre}
#sfw{position:absolute;inset:0;z-index:12;display:none;flex-direction:column;padding:14px 18px 8px;background:linear-gradient(#232a3dfa,#141926fa);border-radius:22px;border:1px solid #ffffff1c;box-shadow:0 20px 50px #000a}#sfw.o{display:flex}
.sfh{display:flex;gap:10px;align-items:center;flex:none}.sfs{flex:1;display:flex;align-items:center;gap:10px;height:40px;padding:0 14px;border-radius:14px;background:#ffffff14;color:#aab3c8}.sfs input{flex:1;background:none;border:0;outline:0;color:#e6eaf3;font:600 14px 'Plus Jakarta Sans'}
.sfb{width:40px;height:40px;border-radius:50%;background:#ffffff1a;display:flex;align-items:center;justify-content:center;font:700 16px 'Plus Jakarta Sans';cursor:pointer}.sfb.sfp{background:#a8c8ff;color:#0b1830}
.sfbody{display:flex;gap:14px;flex:1;min-height:0;margin-top:12px}.sft{width:170px;flex:none;display:flex;flex-direction:column;border-radius:16px;overflow:hidden;background:#ffffff0f;height:fit-content}.sft div{padding:16px 16px;font:700 14px 'Plus Jakarta Sans';cursor:pointer}.sft div.a{background:#a8c8ff;color:#0b1830}
.sfr{flex:1;min-width:0;display:flex;flex-direction:column;gap:10px}.sfc{display:flex;gap:8px;flex:none;overflow-x:auto;scrollbar-width:none}.sfc span{flex:none;padding:8px 14px;border-radius:12px;background:#ffffff10;border:1px solid #ffffff18;font:700 12px 'Plus Jakarta Sans'}.sfc span.a{background:#a8c8ff;color:#0b1830}
.sfl{flex:1;min-height:0;overflow-y:auto;scrollbar-width:none;display:flex;flex-direction:column;gap:6px}.sfi{display:flex;align-items:center;gap:12px;padding:8px 12px;border-radius:14px;background:#ffffff0c;cursor:pointer}.sfi.a{background:#3a4a78}.sfi i{font-style:normal;width:34px;height:34px;border-radius:50%;background:#ffffff1c;display:flex;align-items:center;justify-content:center;font:800 13px 'Plus Jakarta Sans';flex:none}
.sfi div{flex:1;min-width:0}.sfi b{display:block;font:700 14px 'Plus Jakarta Sans'}.sfi span{font:500 11px 'Plus Jakarta Sans';color:#8d96ab}.sfi em{font-style:normal;padding:6px 10px;border-radius:10px;background:#a8c8ff44;color:#cfe0ff;font:800 10px 'Plus Jakarta Sans';letter-spacing:.08em}
.sfn{flex:none;margin-top:6px;font:500 9.5px 'Plus Jakarta Sans';color:#5f6a82;text-align:center}
</style></head><body>
<svg width="0" height="0" style="position:absolute"><defs>
<radialGradient id="hl"><stop offset="0" stop-color="#fff" stop-opacity=".9"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
<radialGradient id="c0" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#5b5a7c"/><stop offset="1" stop-color="#23233a"/></radialGradient>
<linearGradient id="c1" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#e3cc96"/><stop offset=".3" stop-color="#8d7240"/><stop offset=".55" stop-color="#eed9a6"/><stop offset=".8" stop-color="#7a6234"/><stop offset="1" stop-color="#cdb27a"/></linearGradient>
<radialGradient id="c2" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#4a6678"/><stop offset="1" stop-color="#17252f"/></radialGradient>
<linearGradient id="c3" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#d8dee8"/><stop offset=".35" stop-color="#8a92a1"/><stop offset=".6" stop-color="#eef2f7"/><stop offset="1" stop-color="#737b89"/></linearGradient>
<radialGradient id="c4" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#faf3e2"/><stop offset="1" stop-color="#c9bb9a"/></radialGradient>
<radialGradient id="c5" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#f6e6bd"/><stop offset="1" stop-color="#b08a4a"/></radialGradient>
<radialGradient id="c6" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#7a5238"/><stop offset="1" stop-color="#2a1a10"/></radialGradient>
<radialGradient id="c7" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#55555c"/><stop offset="1" stop-color="#0a0a0c"/></radialGradient>
<linearGradient id="c8" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f4f6fa"/><stop offset=".25" stop-color="#9aa2b0"/><stop offset=".5" stop-color="#e6eaf2"/><stop offset=".75" stop-color="#7c8492"/><stop offset="1" stop-color="#dfe4ec"/></linearGradient>
<radialGradient id="c9" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#1e2c44"/><stop offset="1" stop-color="#080d18"/></radialGradient>
<radialGradient id="c10" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#ffffff"/><stop offset=".6" stop-color="#efe6dc"/><stop offset="1" stop-color="#b8a998"/></radialGradient>
<linearGradient id="c11" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#e2bd86"/><stop offset=".5" stop-color="#b98a52"/><stop offset="1" stop-color="#8a6034"/></linearGradient>
<linearGradient id="c12" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#eef1f5"/><stop offset=".4" stop-color="#9aa1ab"/><stop offset=".7" stop-color="#d6dbe2"/><stop offset="1" stop-color="#6c737d"/></linearGradient>
<linearGradient id="c13" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f4d878"/><stop offset=".4" stop-color="#b8902a"/><stop offset=".7" stop-color="#f0cf6c"/><stop offset="1" stop-color="#8a6a1e"/></linearGradient>
<radialGradient id="c14" cx=".4" cy=".3" r=".8"><stop offset="0" stop-color="#ffffff66"/><stop offset="1" stop-color="#c9b8ff22"/></radialGradient>
<linearGradient id="s0" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a2d41"/><stop offset="1" stop-color="#0c0e17"/></linearGradient>
</defs></svg>
<div id="fit"><div id="st"><div id="mx"><div class="mt"></div></div>
<div class="mb"><i>CPU 16 %</i><i>116 BPM</i><i class="a">Default</i><i>Scene A</i><i>Scene B</i><i>Scene C</i></div>
<div id="win"><div class="wh"><div class="bt" id="up">⌄</div><h1>Effects</h1><div class="slotc" id="sc">0 / 6 effects</div><div class="bt" id="sf">Soundfont · Grand Piano ▾</div><div class="bt" id="pi">+ Instrument</div><div class="bt" id="pl">+ Effect</div></div><div id="rack"></div>
<div id="ex" style="opacity:0;pointer-events:none"><div class="bt" id="cl">⌄</div><div id="pm"><div class="ph0" id="pmh"><span id="pmn"></span><i style="font-style:normal">▾</i></div><div id="pml"></div></div><div id="tr"></div><div id="dots"></div><div id="ed"></div></div>
<div id="ad"><div class="ah"><h4 id="ah4">Add an effect</h4><div class="bt" id="ax">✕</div></div><div class="ag" id="ag"></div></div>
<div id="sfw"><div class="sfh"><div class="sfs"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5L21 21"/></svg><input id="sfq" placeholder="Search presets…"></div><div class="sfb sfp" id="sfplus">＋</div><div class="sfb" id="sfx">✕</div></div><div class="sfbody"><div class="sft"><div class="a" data-tab="p">Presets</div><div data-tab="f">SoundFonts</div></div><div class="sfr"><div class="sfc" id="sfc"></div><div class="sfl" id="sfl"></div></div></div><div class="sfn">Stand-in for the soundfont window that already exists in the app. The Soundfont chip and “Change soundfont” both open it.</div></div>
<div id="tz"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6"/></svg>Drop here to remove</div>
<div id="ub"><span id="ut"></span><b id="uu">Undo</b></div></div></div></div>
<div class="bt" id="rot">⟳ Rotate</div><div id="tb">Tablet</div><div id="db">Debug</div><div id="dbg"></div>
<script>
const SYB=[0.5,0.3,0.2,0.2,0.05,0.3,0.4,0.55,0.25,0.15,0.35,0,0.1,0.4,0.7,0.35,0.3,0.3,0,0,0.2,0.2,0.3,0.5,0.5,0.5,0,0.1,0.5,0.2,0.5,0.8];
const SY=o=>{const b=[...SYB];Object.entries(o).forEach(([k,v])=>b[+k]=v);return b};
const OP=(up,lo,pc,vib,vu,vl,les,ck,tn,dr,sp)=>[...up.map(n=>n/8),...lo.map(n=>n/8),...pc,vib/5,vu,vl,les/2,ck,tn,dr,sp,.8];
const FX=[
{n:'Æther Space',t:'Reverb',ff:'Space Grotesk',ac:'#a99ad8',x:'x0',k:[['Size',.62,0,100,' %',0],['Decay',.7,.4,8,' s',1],['Pre-delay',.2,0,120,' ms',0],['Damp',.45,1,12,' kHz',1],['Width',.8,0,100,' %',0],['Mix',.35,0,100,' %',0],['Diffuse',.5,0,100,' %',0]],p:{Hall:[.62,.7,.2,.45,.8,.35,.5],Plate:[.4,.5,.1,.7,.9,.3,.7],Room:[.25,.3,.05,.5,.6,.25,.3],Cathedral:[.95,.95,.35,.3,1,.45,.8],Shimmer:[.7,.85,.25,.2,1,.5,.9],Chamber:[.35,.4,.08,.55,.7,.28,.45],Bloom:[.8,.8,.3,.35,.95,.42,1],Infinite:[1,1,.4,.25,1,.6,.95]}},
{n:'Æther Echo',t:'Delay',ff:'JetBrains Mono',ac:'#d4b06a',x:'x1',k:[],p:{}},
{n:'Prism Tone',t:'Equalizer',ff:'Orbitron',ac:'#8fd0ff',x:'x2',k:[],p:{}},
{n:'Iron Grip',t:'Compressor',ff:'Share Tech Mono',ac:'#b8c2d4',x:'x3',k:[['Threshold',.48,-60,0,' dB',0],['Ratio',.35,1,20,'',0],['Attack',.25,.1,100,' ms',1],['Release',.52,10,1000,' ms',0],['Knee',.4,0,24,' dB',0],['Makeup',.4,0,24,' dB',1]],p:{Glue:[.55,.2,.35,.6,.6,.3],Gentle:[.4,0,.25,.5,.7,.2],Punch:[.5,.6,.6,.3,.3,.5],Limit:[.7,1,.05,.2,.1,.6],'Vocal Leveler':[.45,.4,.3,.55,.8,.35],'Drum Bus':[.5,.4,.2,.25,.3,.45],Parallel:[.6,.8,.1,.4,.2,.5]}},
{n:'Solar Ceiling',t:'Maximizer',ff:'Sora',ac:'#c9852c',x:'x4',k:[['Amount',.5,0,100,' %',0],['Ceiling',.92,-6,0,' dB',1],['Release',.4,10,500,' ms',0],['Width',.3,0,100,' %',0],['Mix',1,0,100,' %',0],['Output',.5,-12,12,' dB',1]],p:{Transparent:[.3,.95,.6,.2,1,.5],Loud:[.75,.9,.35,.4,1,.55],Punch:[.6,.9,.2,.2,.9,.55],Wide:[.5,.92,.5,.8,1,.5],'Master Glue':[.4,.93,.55,.5,1,.5],'Warm Tape':[.55,.9,.45,.3,.85,.52],Radio:[.85,.85,.2,.1,1,.58]}},
{n:'Aether Piano',t:'Instrument',ff:'Sora',ac:'#e8c98a',x:'x5',k:[['Felt',.8,0,100,' %',0],['Lid',.35,0,100,' %',0],['Pedal',.55,0,100,' %',0],['Room',.3,0,100,' %',0],['Width',.7,0,100,' %',0],['Tone',.4,0,100,' %',0],['Body',.6,0,100,' %',0],['Space',.25,0,100,' %',0],['Level',.8,-24,6,' dB',1]],p:{Felt:[.8,.35,.55,.3,.7,.4,.6,.25,.8],Studio:[.4,.6,.35,.15,.6,.55,.4,.1,.8],Upright:[.5,.4,.4,.2,.4,.45,.7,.2,.8],Concert:[.55,.7,.7,.6,.9,.5,.5,.6,.8],Mellow:[.9,.2,.5,.3,.7,.3,.65,.3,.8],Bright:[.2,.85,.45,.2,.6,.75,.4,.15,.8]}},
{n:'Aether Organ',t:'Instrument',ff:'Sora',ac:'#d9c08a',x:'x6',k:[...["16'","5⅓'","8'","4'","2⅔'","2'","1⅗'","1⅓'","1'"].map((n,j)=>['Upper '+n,[1,1,1,1,0,0,0,0,0][j],0,8,'',0]),...["16'","5⅓'","8'","4'","2⅔'","2'","1⅗'","1⅓'","1'"].map((n,j)=>['Lower '+n,[1,1,1,0,0,0,0,0,0][j],0,8,'',0]),['Perc on',0,0,1,'',0],['Perc soft',0,0,1,'',0],['Perc fast',0,0,1,'',0],['Perc 3rd',0,0,1,'',0],['Vibrato',.6,0,5,'',0],['Vib upper',1,0,1,'',0],['Vib lower',1,0,1,'',0],['Rotary',1,0,2,'',0],['Click',.3,0,100,' %',0],['Tone',.55,0,100,' %',0],['Drive',.4,0,100,' %',0],['Space',.25,0,100,' %',0],['Level',.8,-24,6,' dB',1]],p:{Gospel:OP([8,8,8,8,0,0,0,0,0],[8,8,8,0,0,0,0,0,0],[0,0,0,0],3,1,1,2,.3,.55,.4,.25),Full:OP([8,8,8,8,8,8,8,8,8],[8,8,8,8,8,0,0,0,0],[0,0,0,0],0,1,1,1,.3,.5,.2,.2),Jazz:OP([8,0,8,0,0,0,0,0,0],[0,0,8,0,0,0,0,0,0],[1,0,1,1],1,1,0,2,.35,.5,.3,.2),Rock:OP([8,8,8,0,0,0,0,8,8],[8,8,8,0,0,0,0,0,0],[0,0,0,0],5,1,1,2,.4,.6,.7,.15),Balanced:OP([6,8,8,6,4,5,3,2,2],[0,0,8,5,0,3,0,0,0],[1,1,0,0],2,1,1,1,.3,.5,.3,.25),Whisper:OP([0,0,4,0,2,0,0,0,0],[0,0,5,0,0,0,0,0,0],[1,1,1,0],0,1,0,1,.2,.4,.1,.35)}},
{n:'Aether Sax',t:'Instrument',ff:'Sora',ac:'#e0a45a',x:'x7',k:[['Breath',.55,0,100,' %',0],['Reed',.5,0,100,' %',0],['Growl',.15,0,100,' %',0],['Vibrato',.35,0,100,' %',0],['Rate',.4,0,100,' %',0],['Tone',.5,0,100,' %',0],['Body',.6,0,100,' %',0],['Space',.3,0,100,' %',0],['Level',.8,-24,6,' dB',1]],p:{Smooth:[.55,.4,.1,.3,.35,.45,.65,.3,.8],Breathy:[.85,.3,.05,.4,.4,.5,.5,.35,.8],Growl:[.7,.7,.8,.25,.45,.6,.6,.2,.8],Club:[.5,.5,.2,.5,.4,.4,.7,.6,.8],Bright:[.6,.8,.15,.3,.5,.8,.4,.25,.8],Subtone:[.45,.2,.1,.2,.3,.25,.8,.3,.8]}},
{n:'Aether Strings',t:'Instrument',ff:'Space Grotesk',ac:'hsl(36,85%,62%)',x:'x8',k:[['Pressure',0.5,0,100,' %',0],['Speed',0.5,0,100,' %',0],['Motion',0.35,0,100,' %',0],['Morph',0.1,0,100,' %',0],['Time',0.5,0,100,' %',0],['Color',0.1,0,100,' %',0],['Tone',0.5,0,100,' %',0],['Body',0.6,0,100,' %',0],['Space',0.3,0,100,' %',0],['Level',.8,-24,6,' dB',1]],p:{Solo:[.55,.5,.5,.05,.5,.1,.5,.55,.25,.8],Chamber:[.5,.45,.4,.35,.45,.55,.45,.6,.35,.8],Orchestra:[.45,.4,.3,.9,.6,.75,.5,.6,.55,.8],Cinematic:[.6,.3,.25,1,.75,.92,.35,.7,.8,.8],Warm:[.4,.5,.45,.5,.4,.02,.25,.75,.4,.8],Bright:[.65,.65,.3,.4,.3,.5,.8,.4,.3,.8]}},
{n:'Aether Guitar',t:'Instrument',ff:'Space Grotesk',ac:'#e08a4a',x:'x9',k:[['Body',0,0,3,'',0],['Pick',0.5,0,100,' %',0],['Strum',0.4,0,100,' %',0],['Mute',0.1,0,100,' %',0],['Bend',0.2,0,100,' %',0],['Pickup',0.5,0,100,' %',0],['Finish',.33,0,3,'',0],['Tone',0.5,0,100,' %',0],['Resonance',0.7,0,100,' %',0],['Space',0.25,0,100,' %',0],['Level',.8,-24,6,' dB',1]],p:{Acoustic:[0,.45,.4,.1,0,.5,.33,.5,.7,.25,.8],Nylon:[.33,.25,.3,.15,0,.45,0,.4,.75,.3,.8],Clean:[.67,.55,.45,.1,.1,.5,.33,.55,.4,.3,.8],Crunch:[1,.65,.45,.2,.2,.7,.67,.6,.35,.2,.8],Lead:[.67,.75,.2,0,.6,.9,1,.7,.3,.35,.8],Ambient:[0,.4,.6,0,.1,.5,0,.35,.5,.8,.8]}},
{n:'Aether Amp',t:'Amp',ff:'Orbitron',ac:'#ff9a3c',x:'x10',k:[['Gain',0.3,0,100,' %',0],['Bass',0.5,0,100,' %',0],['Mid',0.5,0,100,' %',0],['Treble',0.6,0,100,' %',0],['Presence',0.5,0,100,' %',0],['Master',0.6,0,100,' %',0],['Channel',0,0,2,'',0],['Mic pos',0.4,0,100,' %',0],['Mic dist',0.4,0,100,' %',0],['Boost',0,0,1,'',0],['Tremolo',0,0,1,'',0],['Level',.8,-24,6,' dB',1]],p:{Clean:[.3,.5,.5,.6,.5,.6,0,.4,.4,0,0,.8],Crunch:[.6,.55,.6,.55,.55,.55,.5,.5,.4,0,0,.8],Lead:[.8,.5,.65,.6,.6,.5,1,.55,.35,1,0,.8],Blues:[.5,.6,.55,.5,.45,.55,.5,.4,.5,0,1,.8],Jazz:[.2,.65,.4,.4,.3,.6,0,.3,.6,0,0,.8],Metal:[.95,.6,.4,.7,.7,.5,1,.6,.3,1,0,.8]}},
{n:'Aether Bass',t:'Amp',ff:'Sora',ac:'#f0c060',x:'x11',k:[['Drive',0.25,0,100,' %',0],['Comp',0.4,0,100,' %',0],['Blend',0.6,0,100,' %',0],['63 Hz',0.55,0,100,' %',0],['250 Hz',0.5,0,100,' %',0],['1 kHz',0.5,0,100,' %',0],['3 kHz',0.45,0,100,' %',0],['8 kHz',0.4,0,100,' %',0],['Cab',.5,0,2,'',0],['Level',.8,-24,6,' dB',1]],p:{Motown:[.15,.5,.3,.6,.45,.4,.3,.25,.5,.8],Rock:[.5,.55,.7,.5,.5,.65,.6,.5,1,.8],Slap:[.35,.6,.8,.7,.3,.45,.7,.75,.5,.8],Fuzz:[.9,.4,.9,.5,.5,.7,.65,.4,1,.8],Dub:[.2,.7,.3,.9,.4,.2,.2,.15,0,.8],'Clean DI':[0,.3,0,.5,.5,.5,.5,.5,.5,.8]}},
{n:'Aether Brass',t:'Instrument',ff:'Space Grotesk',ac:'#e8c060',x:'x12',k:[['Lips',0.5,0,100,' %',0],['Bite',0.45,0,100,' %',0],['Vibrato',0.3,0,100,' %',0],['Section',.25,0,4,'',0],['Mute',0,0,4,'',0],['Tone',0.5,0,100,' %',0],['Body',0.6,0,100,' %',0],['Space',0.3,0,100,' %',0],['Level',.8,-24,6,' dB',1]],p:{Open:[.5,.45,.3,.25,0,.5,.6,.3,.8],'Muted Jazz':[.4,.3,.4,0,.75,.35,.7,.4,.8],'Section Stab':[.7,.8,.1,1,0,.7,.55,.4,.8],Fanfare:[.8,.7,.2,.75,0,.8,.5,.55,.8],Soft:[.3,.2,.45,.25,.5,.3,.7,.35,.8],Harmon:[.55,.5,.25,0,.75,.55,.45,.3,.8]}},
{n:'Aether Voices',t:'Voice',ff:'Space Grotesk',ac:'#c9b8ff',x:'x13',k:[['Vowel X',0.5,0,100,' %',0],['Vowel Y',0.3,0,100,' %',0],['Breath',0.3,0,100,' %',0],['Choir',0.5,0,100,' %',0],['Vibrato',0.35,0,100,' %',0],['Tone',0.5,0,100,' %',0],['Body',0.5,0,100,' %',0],['Space',0.45,0,100,' %',0],['Level',.8,-24,6,' dB',1]],p:{Ah:[.5,.1,.3,.5,.35,.5,.5,.45,.8],Ooh:[.9,.9,.2,.6,.3,.35,.5,.5,.8],Eeh:[.1,.9,.3,.5,.35,.6,.45,.4,.8],Ensemble:[.5,.5,.25,.9,.4,.5,.55,.6,.8],Whisper:[.5,.5,.9,.4,.1,.4,.4,.5,.8],Angels:[.7,.6,.35,1,.45,.7,.6,.85,.8]}},
{n:'Aether Synth',t:'Instrument',ff:'Orbitron',ac:'#7ad0ff',x:'x14',k:[...['Wave A','Wave B','Detune','Sub','Noise','Unison','Spread','Cutoff','Resonance','Drive','Env amt'].map(n=>[n,.4,0,100,' %',0]),['Filter',0,0,3,'',0],...['Attack','Decay','Sustain','Release','LFO1 rate','LFO1 depth','LFO1 shape','LFO1 dest','LFO2 rate','LFO2 depth','Macro 1','Macro 2','Macro 3','Macro 4'].map(n=>[n,.4,0,100,' %',0]),['Mode',0,0,2,'',0],...['Glide','Voices','Bend','Tune'].map(n=>[n,.4,0,100,' %',0]),['Level',.8,-24,6,' dB',1]],p:{Pad:SY({0:.3,1:.5,2:.3,5:.7,6:.7,7:.35,8:.2,12:.7,13:.5,14:.7,15:.8}),Lead:SY({0:.9,1:.6,2:.2,5:.3,7:.6,8:.4,9:.5,12:.05,14:.8,15:.3}),Bass:SY({0:.6,1:.2,3:.7,5:0,7:.25,8:.35,9:.4,10:.5,12:.02,14:.6,15:.2,26:.5}),Pluck:SY({0:.5,1:.5,7:.5,10:.6,12:.01,13:.25,14:.1,15:.2}),Keys:SY({0:.1,1:.2,2:.15,7:.45,12:.03,13:.5,14:.5,15:.4}),FX:SY({0:1,1:.4,2:.8,4:.5,7:.5,8:.7,16:.7,17:.8,18:.5})}}];
{const E=(a,f,t,w,m)=>[...a,f,t,w,m];
FX[1].k=[...[.15,.9,.3,.7,.45,.5,.6,.3].map((v,j)=>[(j%2?'Level ':'Time ')+(1+(j>>1)),v,j%2?0:10,j%2?100:1000,j%2?' %':' ms',0]),['Feedback',.42,0,95,' %',0],['Tone',.6,1,12,' kHz',1],['Wow',.2,0,100,' %',0],['Mix',.28,0,100,' %',0]];
FX[1].p={Dotted:E([.15,.9,.3,.7,.45,.5,.6,.3],.42,.6,.2,.28),Tape:E([.12,.9,.26,.8,.42,.65,.6,.5],.6,.35,.6,.3),'Ping-pong':E([.1,.9,.35,.8,.55,.6,.8,.4],.5,.7,.1,.32),Slap:E([.05,.9,.1,.2,.15,.1,.2,.05],.15,.8,0,.2),Ambient:E([.2,.7,.4,.6,.62,.5,.85,.4],.7,.45,.35,.4),Dub:E([.3,.9,.5,.75,.7,.55,.9,.4],.78,.3,.5,.35),Doubler:E([.02,.8,.04,.7,.06,.5,.08,.3],.1,.9,.1,.3),Pulse:E([.1,.9,.2,.9,.3,.9,.4,.9],.55,.7,0,.3)};
const Q=(l,m,h,mf=.5,q=.4,o=.5)=>[.15,l,mf,m,q,.85,h,o];
FX[2].k=[['Low freq',.15,20,20000,' Hz',0],['Low',.62,-12,12,' dB',1],['Mid freq',.5,20,20000,' Hz',0],['Mid',.45,-12,12,' dB',1],['Mid Q',.4,.3,4,'',1],['High freq',.85,20,20000,' Hz',0],['High',.7,-12,12,' dB',1],['Output',.5,-12,12,' dB',1]];
FX[2].p={Air:Q(.5,.45,.85,.5,.3),Warm:Q(.72,.55,.35),Vocal:Q(.4,.62,.62,.58,.5),Presence:Q(.45,.5,.72,.62,.6),Telephone:Q(.25,.78,.3,.52,.9),Smile:Q(.78,.4,.78,.45,.3),Flat:Q(.5,.5,.5),'Black Box':Q(.62,.46,.7,.4,.45,.52)}}
const $=s=>document.querySelector(s),RAT=[1.5,2,3,4,8,20];
const V=FX.map(f=>f.k.map(k=>k[1])),B=FX.map(()=>0),P=FX.map(f=>Object.keys(f.p)[0]),mo=FX.map(()=>1),ph=FX.map(()=>0);V[14]=[...FX[14].p.Pad];
let S=1,rot=0,cur=-1,sel=1,dr=null,act=[],U=null,ubT=0,lastDrag=0;
// mixer back
{const cs=['#d8923a','#3fae7c','#4a86e0','#9a6ae0','#e05a8e','#2eb8a8'],ps=[34,52,60,24,44,38];
$('#mx').innerHTML+=cs.map((c,i)=>`<div class="ch" style="--c:${c}"><i class="nm"></i><i class="r"><b style="--p:${ps[i]}%"></b></i></div>`).join('')}

const pol=(a,r)=>{const q=(a-135)*Math.PI/180;return[50+r*Math.cos(q),50+r*Math.sin(q)]};
function arc(a0,a1,r){const[p,q]=pol(a0,r),[u,w]=pol(a1,r);return`M${p} ${q}A${r} ${r} 0 ${a1-a0>180?1:0} 1 ${u} ${w}`}
const SKN={5:5,6:6,7:10,8:11,9:12,10:7,11:8,12:13,13:14,14:9},KSH={6:'flute',10:'knurl',11:'chrome',13:'glass',14:'flat'},LIGHT=[1,3,4,8,10,11,12,13];
function knob(i,j,sk=i){const v=V[i][j],a=270*v,f=FX[i],kk=SKN[i]!==undefined?SKN[i]:sk,sh=KSH[i]||'',dark=LIGHT.includes(kk),pc=kk==6?'#f2ead4':kk==7?f.ac:kk==9?f.ac:kk==14?'#fff':(kk==4||dark)?'#1d1b16':'#f2f4fa';
 const track=`<path d="${arc(0,270,46)}" fill="none" stroke="${i==4?'#0002':'#ffffff1c'}" stroke-width="${sh=='flat'?5:3.5}" stroke-linecap="round"/><path d="${arc(0,Math.max(.1,a),46)}" fill="none" stroke="${f.ac}" stroke-width="${sh=='flat'?5:3.5}" stroke-linecap="round"/>`;
 if(sh=='flat')return`<svg viewBox="0 0 100 100">${track}<circle cx="50" cy="50" r="33" fill="url(#c9)" stroke="${f.ac}" stroke-opacity=".5" stroke-width="1.2"/><circle cx="50" cy="50" r="26" fill="none" stroke="#ffffff14" stroke-width="1"/><g transform="rotate(${a-135} 50 50)"><circle cx="50" cy="29" r="3.4" fill="${f.ac}"/><circle cx="50" cy="29" r="6" fill="${f.ac}" opacity=".25"/></g></svg>`;
 if(sh=='glass')return`<svg viewBox="0 0 100 100">${track}<circle cx="50" cy="50" r="35" fill="#ffffff10" stroke="#ffffff40" stroke-width="1.2"/><circle cx="50" cy="50" r="28" fill="url(#c14)" stroke="${f.ac}" stroke-opacity=".7" stroke-width="1"/><ellipse cx="42" cy="38" rx="14" ry="7" transform="rotate(-28 42 38)" fill="url(#hl)" opacity=".5"/><g transform="rotate(${a-135} 50 50)"><rect x="48.6" y="24" width="2.8" height="13" rx="1.4" fill="#fff"/></g></svg>`;
 const skirt=sh=='knurl'?`<circle cx="50" cy="50" r="36" fill="none" stroke="#000" stroke-opacity=".8" stroke-width="6" stroke-dasharray="1 1.3"/>`:sh=='flute'?`<circle cx="50" cy="50" r="36" fill="none" stroke="#000" stroke-opacity=".7" stroke-width="6" stroke-dasharray="4 2.4"/>`:`<circle cx="50" cy="50" r="36" fill="none" stroke="#000" stroke-opacity=".75" stroke-width="5" stroke-dasharray="1.5 2.2"/>`;
 const extra=sh=='chrome'?`<circle cx="50" cy="50" r="19" fill="none" stroke="#fff" stroke-opacity=".35" stroke-width=".8"/><circle cx="50" cy="50" r="12" fill="none" stroke="#000" stroke-opacity=".25" stroke-width=".8"/>`:sh=='knurl'?`<circle cx="50" cy="50" r="27" fill="none" stroke="${f.ac}" stroke-opacity=".6" stroke-width="1.2"/>`:'';
 return`<svg viewBox="0 0 100 100">${track}<circle cx="50" cy="50" r="38" fill="url(#s0)" stroke="#000" stroke-opacity=".7"/>${skirt}<circle cx="50" cy="50" r="${sh=='chrome'?30:27}" fill="url(#c${kk})" stroke="#fff" stroke-opacity=".22" stroke-width=".8"/>${extra}<ellipse cx="42" cy="37" rx="15" ry="7" transform="rotate(-28 42 37)" fill="url(#hl)" opacity=".4"/><g transform="rotate(${a-135} 50 50)"><rect x="48.4" y="26" width="3.2" height="15" rx="1.6" fill="${pc}"/></g></svg>`}

const TS=['font-weight:300;font-size:28px','font-size:22px;font-weight:500','font-weight:600;font-size:21px;letter-spacing:.04em','font-size:23px','font-weight:800;font-size:24px','font-weight:500;font-size:24px;letter-spacing:.02em','font-weight:500;font-size:24px;letter-spacing:.02em','font-weight:500;font-size:24px','font-weight:300;font-size:26px','font-weight:600;font-size:24px','font-weight:600;font-size:22px;letter-spacing:.06em','font-weight:800;font-size:24px','font-weight:600;font-size:24px','font-weight:300;font-size:26px','font-weight:700;font-size:14px;letter-spacing:.03em'],CT=['font-weight:300;font-size:19px','font-size:14px;font-weight:500','font-weight:600;font-size:14px','font-size:16px','font-weight:800;font-size:16px','font-weight:500;font-size:15px','font-weight:500;font-size:15px','font-weight:500;font-size:15px','font-weight:300;font-size:16px','font-weight:600;font-size:15px','font-weight:600;font-size:14px','font-weight:800;font-size:15px','font-weight:600;font-size:15px','font-weight:300;font-size:16px','font-weight:700;font-size:14px'];
let hapT=0;function hap(ms=8){const n=performance.now();if(n-hapT>40&&navigator.vibrate){navigator.vibrate(ms);hapT=n}}
const val=(i,j)=>{const k=FX[i].k[j];if(i==3&&j==1){const r=RAT[Math.round(V[i][j]*5)];return(r==20?'∞':r)+' : 1'}return(k[2]+(k[3]-k[2])*V[i][j]).toFixed(k[5])+k[4]};
function upd(i){if(i==8)FX[8].ac=`hsl(${Math.round(V[8][5]*360)},85%,62%)`;document.querySelectorAll(`.kb[data-i="${i}"]`).forEach(el=>{const j=+el.dataset.j;el.innerHTML=knob(i,j,+(el.dataset.sk||i));el.nextElementSibling.textContent=val(i,j)});if(i==2)npu();if(i>=6)chipsUpd()}
const AB=(i,j,x,y,z,sk=i)=>`<div class="kn ab" style="left:${x}px;top:${y}px"><label>${FX[i].k[j][0]}</label><div class="kb" data-i="${i}" data-j="${j}" data-sk="${sk}" style="width:${z}px;height:${z}px">${knob(i,j,sk)}</div><span class="vl">${val(i,j)}</span></div>`;
const OR=[0,1,2,3,4,5,6].map(k=>{const a=(-90+k*360/7)*Math.PI/180;return[389+285*Math.cos(a),114+80*Math.sin(a)]});
const CV=g=>`<canvas class="vc ${g}" width="2" height="2"></canvas>`;
const BODY=[()=>CV('')+OR.map((q,j)=>AB(0,j,q[0],q[1],54)).join(''),
 ()=>CV('dg')+AB(1,8,650,70,56)+AB(1,9,735,70,56)+AB(1,10,692,188,56)+AB(1,11,95,205,52),
 ()=>CV('dg')+AB(2,1,66,126,100,3)+AB(2,3,330,214,60,4)+AB(2,6,664,122,78,2)+AB(2,4,430,216,44,1),
 ()=>CV('dg')+AB(3,1,628,78,78)+AB(3,4,725,64,50)+AB(3,2,598,200,54)+AB(3,3,670,200,54)+AB(3,5,742,200,54),
 ()=>CV('')+AB(4,0,125,128,136)+AB(4,1,262,78,76)+AB(4,2,262,196,50)+AB(4,3,340,196,50)+AB(4,4,340,70,50)+AB(4,5,735,128,62),
 ()=>CV('dg')+AB(5,5,150,252,42,4)+AB(5,6,290,252,42,1)+AB(5,7,430,252,42,4),
 ()=>CV('dg')+TG(6,'PERCUSSION',520,10,[['ON',18,'t'],['SOFT',19,'t'],['FAST',20,'t'],['3RD',21,'t']])+TG(6,'',640,84,[['UPPER',23,'t']])+TG(6,'',640,116,[['LOWER',24,'t']])+TG(6,'ROTARY',520,176,[['STOP',25,'s',0],['SLOW',25,'s',.5],['FAST',25,'s',1]])+AB(6,26,742,106,40,1)+AB(6,27,150,252,42,4)+AB(6,28,290,252,42,1)+AB(6,29,430,252,42,4),
 ()=>CV('dg')+AB(7,5,150,252,42,4)+AB(7,6,290,252,42,1)+AB(7,7,430,252,42,4),
 ()=>CV('dg')+AB(8,5,96,176,40)+AB(8,4,212,176,40)+AB(8,3,389,168,62)+AB(8,2,566,176,40)+AB(8,0,680,176,40)+AB(8,1,756,176,40)+AB(8,6,150,252,42)+AB(8,7,290,252,42)+AB(8,8,430,252,42),
 ()=>CV('dg')+TG(9,'BODY',16,6,[['ACOUSTIC',0,'s',0],['NYLON',0,'s',.33],['ELEC DC',0,'s',.67],['ELEC SC',0,'s',1]])+AB(9,7,150,252,42)+AB(9,8,290,252,42)+AB(9,9,430,252,42),
 ()=>CV('dg')+TG(10,'CHANNEL',16,6,[['CLEAN',6,'s',0],['CRUNCH',6,'s',.5],['LEAD',6,'s',1]])+TG(10,'PEDALS',560,6,[['BOOST',9,'t'],['TREM',10,'t']])+[0,1,2,3,4,5].map(j=>AB(10,j,70+j*80,86,46,j%2?1:4)).join('')+AB(10,11,730,86,46,4),
 ()=>CV('dg')+TG(11,'CABINET',16,134,[['1×15',8,'s',0],['4×10',8,'s',.5],['8×10',8,'s',1]])+AB(11,0,330,72,44,1)+AB(11,1,400,72,44,4)+AB(11,2,470,72,44,1)+AB(11,9,730,206,46,4),
 ()=>CV('dg')+TG(12,'MUTE',16,6,[['OPEN',4,'s',0],['STRAIGHT',4,'s',.25],['CUP',4,'s',.5],['HARMON',4,'s',.75],['PLUNGER',4,'s',1]])+TG(12,'SECTION',500,6,[['1',3,'s',0],['2',3,'s',.25],['3',3,'s',.5],['4',3,'s',.75],['5',3,'s',1]])+AB(12,5,150,252,42,4)+AB(12,6,290,252,42,1)+AB(12,7,430,252,42,4),
 ()=>CV('dg')+AB(13,2,70,56,44,4)+AB(13,3,70,150,44,1)+AB(13,4,700,60,44,4)+AB(13,5,150,252,42,4)+AB(13,6,290,252,42,1)+AB(13,7,430,252,42,4),
 ()=>CV('dg')+`<div class="pgt" style="left:268px;top:-44px"><i data-pg="0" class="a">SOUND</i><i data-pg="1">MOTION</i><i data-pg="2">VOICE</i></div>`+SPG(0,AB(14,0,45,102,40,1)+AB(14,1,125,102,40,1)+AB(14,2,205,102,40,4)+AB(14,3,45,170,40,4)+AB(14,4,125,170,40,4)+AB(14,5,205,170,40,1)+AB(14,9,630,64,40,1)+AB(14,10,700,64,40,4)+AB(14,6,764,64,40,1)+TG(14,'',586,120,[['LP',11,'s',0],['HP',11,'s',.33],['BP',11,'s',.67],['NOTCH',11,'s',1]]))+SPG(1,AB(14,16,50,128,40,1)+AB(14,17,130,128,40,4)+TG(14,'',14,178,[['SIN',18,'s',0],['TRI',18,'s',.33],['SAW',18,'s',.67],['SQR',18,'s',1]])+AB(14,20,290,128,40,1)+AB(14,21,370,128,40,4)+TG(14,'',250,178,[['CUTOFF',19,'s',0],['PITCH',19,'s',.33],['WAVE',19,'s',.67],['LEVEL',19,'s',1]])+AB(14,25,650,176,40,4))+SPG(2,TG(14,'',14,14,[['POLY',26,'s',0],['MONO',26,'s',.5],['LEGATO',26,'s',1]])+AB(14,27,60,104,44,1)+AB(14,28,140,104,44,4)+AB(14,29,220,104,44,1)+AB(14,30,300,104,44,4))+AB(14,22,150,252,42,4)+AB(14,23,290,252,42,1)+AB(14,24,430,252,42,4)];
const DBL='';
function TG(i,l,x,y,it){return `<div class="chg" style="left:${x}px;top:${y}px">${l?`<span style="width:auto;margin-right:6px">${l}</span>`:''}${it.map(([n,j,m,v])=>`<i data-i="${i}" data-j="${j}" data-m="${m}" data-v="${v??1}">${n}</i>`).join('')}</div>`}
const SPG=(p,h)=>`<div class="spg" data-p="${p}">${h}</div>`;
function chipsUpd(){document.querySelectorAll('.chg i').forEach(e=>{const j=+e.dataset.j,v=V[+e.dataset.i][j];e.classList.toggle('a',e.dataset.m=='t'?v>.5:Math.abs(v-+e.dataset.v)<.01)})}
function npu(){}
function mk(i){const f=FX[i],c=document.createElement('div');c.className='cd pop'+(B[i]?'':' on')+(INSTR.includes(i)?' isins':'');c.dataset.i=i;
 c.innerHTML=`<div class="t"><span class="ci">${IC[ICK[i]]}</span>${f.t}</div><div class="n" style="font-family:'${f.ff}',cursive;${CT[i]}">${f.n}</div><canvas width="272" height="320"></canvas><div class="f"><i class="led"></i><span id="pr${i}">${P[i]}</span></div>`;
 c.onclick=()=>{if(performance.now()-lastDrag>400)open(i)};setTimeout(()=>c.classList.remove('pop'),700);
 const p=document.createElement('div');p.className='pn '+f.x+(B[i]?' by':'');p.dataset.i=i;
 p.innerHTML=`<div class="ph"><div class="bt" data-b="${i}"><i class="led${B[i]?'':' on'}" style="margin-right:7px"></i>Bypass</div><div><div class="nm" style="font-family:'${f.ff}',cursive;${TS[i]}">${f.n}</div><div class="ty">${f.t}</div></div><div class="sp"></div>${i>=5?`<div class="ib" data-sur="${i}">Surprise me</div><div class="ib" data-ab="${i}">A</div>${[5,7,8,9,12].includes(i)?`<div class="ib" data-ed="1">Edit</div>`:''}`:''}</div><div class="bd">${BODY[i]()}</div>${FAMOK[i]?`<div class="warn" data-w="${i}"><span class="wi">⚠</span><div class="wt"></div><div class="wb"><i data-wc="${i}">Change soundfont</i><i data-wu="${i}">Use anyway</i></div></div>`:''}`;if(i>=5)p.classList.add('ins');if(i==14)p.dataset.pg=0;return[c,p]}
function toast(m){$('#ut').textContent=m;$('#uu').style.display='none';$('#ub').classList.add('o');clearTimeout(ubT);ubT=setTimeout(()=>{$('#ub').classList.remove('o');setTimeout(()=>$('#uu').style.display='',420)},1800)}
function dots(){chipsUpd();$('#sc').textContent=effCount()+' / 6 effects';updSf();$('#dots').innerHTML=act.map(()=>'<i></i>').join('');dotA(Math.max(0,cur))}
function dotA(k){document.querySelectorAll('#dots i').forEach((d,x)=>d.classList.toggle('a',x==k))}
function renderRack(){const r=$('#rack');r.querySelectorAll('.sl').forEach(e=>e.remove());
 if(!act.length){r.classList.add('em');for(let k=0;k<3;k++){const s=document.createElement('div');s.className='sl'+(k?' d':'');s.innerHTML='<i class="sc"></i><i class="sc b"></i>'+(k?'':'<div class="sx">＋</div><div class="st">Insert an effect</div>');s.onclick=()=>openAdd('fx');r.appendChild(s)}}
 else{r.classList.remove('em');const s=document.createElement('div');s.className='sl pl';s.innerHTML='<div class="sx">＋</div>';s.onclick=()=>openAdd('fx');r.appendChild(s)}}
const INSTR=[5,6,7,8,9,12,14],SOURCES=[6,14];
function effCount(){return act.filter(j=>!INSTR.includes(j)).length}
function addFX(i,at){if(act.includes(i))return;if(INSTR.includes(i)){const old=act.find(j=>INSTR.includes(j));if(old!=null){delFX(old,true);toast(FX[i].n+' replaces '+FX[old].n)}at=0}else{if(effCount()>=6)return;if(at==null||at>act.length)at=act.length}
 const[c,p]=mk(i),nx=act[at];act.splice(at,0,i);renderRack();const rk=$('#rack'),tr=$('#tr');
 rk.insertBefore(c,nx!=null?rk.querySelector(`.cd[data-i="${nx}"]`):rk.querySelector('.sl'));tr.insertBefore(p,nx!=null?tr.querySelector(`.pn[data-i="${nx}"]`):null);dots();updWarn();if(i==2)npu()}
function delFX(i,silent){const k=act.indexOf(i);U=silent?null:{i,at:k,v:[...V[i]],p:P[i]};act.splice(k,1);$('#rack').querySelector(`.cd[data-i="${i}"]`).remove();$('#tr').querySelector(`.pn[data-i="${i}"]`).remove();renderRack();dots();if(silent)return;$('#uu').style.display='';$('#ut').textContent=FX[i].n+' removed';$('#ub').classList.add('o');clearTimeout(ubT);ubT=setTimeout(()=>$('#ub').classList.remove('o'),5200)}
$('#uu').onclick=()=>{if(!U)return;V[U.i]=U.v;P[U.i]=U.p;addFX(U.i,U.at);U=null;$('#ub').classList.remove('o')};
// add overlay (categories with icons)
const IC={amp:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="26" height="18" rx="2.5"/><path d="M3 12.5h26"/><circle cx="7.5" cy="9.8" r="1.1"/><circle cx="12" cy="9.8" r="1.1"/><circle cx="16.5" cy="9.8" r="1.1"/><circle cx="21" cy="9.8" r="1.1"/><circle cx="25" cy="9.8" r="1.1"/><circle cx="16" cy="19" r="4.4"/><circle cx="16" cy="19" r="1.4" fill="currentColor" stroke="none"/></svg>',bas:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="3" width="20" height="26" rx="2.5"/><circle cx="16" cy="11" r="4.3"/><circle cx="16" cy="11" r="1.2" fill="currentColor" stroke="none"/><circle cx="16" cy="22.4" r="4.3"/><circle cx="16" cy="22.4" r="1.2" fill="currentColor" stroke="none"/></svg>',brs:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3.5 16.5H21"/><path d="M21 16.5l7.5-6v13z"/><path d="M8 16.5v-5.5h11v5.5"/><path d="M12 8.8V11M15 8.8V11M18 8.8V11"/><circle cx="12" cy="7.8" r="1"/><circle cx="15" cy="7.8" r="1"/><circle cx="18" cy="7.8" r="1"/></svg>',voc:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="11" y="3" width="10" height="14" rx="5"/><path d="M7 13c0 5 4 8 9 8s9-3 9-8M16 21v7M11 28h10"/><path d="M13 8h6M13 11h6" opacity=".6"/></svg>',pno:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="6" width="24" height="20" rx="2.5"/><path d="M10 18v8M16 18v8M22 18v8" stroke-width="1.3" opacity=".8"/><rect x="8.3" y="6" width="3.4" height="12.5" rx=".8" fill="currentColor"/><rect x="14.3" y="6" width="3.4" height="12.5" rx=".8" fill="currentColor"/><rect x="20.3" y="6" width="3.4" height="12.5" rx=".8" fill="currentColor"/></svg>',syn:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="28" height="18" rx="2.5"/><path d="M2 15.5h28"/><circle cx="7" cy="11.2" r="1.3"/><circle cx="11.6" cy="11.2" r="1.3"/><circle cx="16.2" cy="11.2" r="1.3"/><rect x="21" y="9.2" width="6.5" height="4" rx=".9"/><path d="M7 20.7v4.3M11.7 20.7v4.3M16.4 15.5v9.5M21.1 20.7v4.3M25.8 20.7v4.3" stroke-width="1.1" opacity=".7"/><rect x="5.5" y="15.5" width="2.8" height="5.2" fill="currentColor" stroke="none"/><rect x="10.1" y="15.5" width="2.8" height="5.2" fill="currentColor" stroke="none"/><rect x="19.7" y="15.5" width="2.8" height="5.2" fill="currentColor" stroke="none"/><rect x="24.3" y="15.5" width="2.8" height="5.2" fill="currentColor" stroke="none"/></svg>',org:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3.5" width="26" height="7.5" rx="1.5"/><path d="M6.5 5.4v3M9 5.4v3.8M11.5 5.4v2.4M14 5.4v3.8M16.5 5.4v2M19 5.4v3.4M21.5 5.4v1.8M24 5.4v2.8M26.5 5.4v2" stroke-width="1.1"/><rect x="3" y="13" width="26" height="6" rx="1"/><rect x="3" y="20.5" width="26" height="6" rx="1"/><path d="M6.7 13v6M10.4 13v6M14.1 13v6M17.9 13v6M21.6 13v6M25.3 13v6M6.7 20.5v6M10.4 20.5v6M14.1 20.5v6M17.9 20.5v6M21.6 20.5v6M25.3 20.5v6" stroke-width="1" opacity=".6"/><path d="M6 26.5v3M26 26.5v3"/></svg>',gtr:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="14.3" y="1.4" width="3.4" height="3.4" rx=".8"/><path d="M16 4.8v5.4"/><path d="M12 12.6c0-1.5 1.7-2.4 4-2.4s4 .9 4 2.4c0 1.4-.7 2.4-1 3.5 1.9.6 3.6 2.4 3.6 5.1 0 3.8-3 7.4-6.6 7.4S9.4 25 9.4 21.2c0-2.7 1.7-4.5 3.6-5.1-.3-1.1-1-2.1-1-3.5z"/><circle cx="16" cy="20" r="2"/><path d="M13.3 25.4h5.4"/></svg>',sax:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M5.4 3.2l5.2 2.4c1.6.8 2.4 2.2 2.4 4" stroke-width="2"/><path d="M13 9.6V21c0 4.3 3 7 6.8 7s6.4-2.6 6.4-6v-4.6" stroke-width="3"/><ellipse cx="26.2" cy="15.6" rx="4" ry="1.7"/><circle cx="16.8" cy="12.5" r="1"/><circle cx="16.8" cy="16.5" r="1"/><circle cx="16.8" cy="20.5" r="1"/></svg>',str:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="16" cy="2.7" r="1.4"/><path d="M16 4.1v4.3"/><path d="M12.3 10.6c0-1.4 1.6-2.2 3.7-2.2s3.7.8 3.7 2.2c0 1.5-1 2.5-1.3 3.6 1.4.8 3.3 2.2 3.3 5.2 0 4-2.7 8.2-5.7 8.2s-5.7-4.2-5.7-8.2c0-3 1.9-4.4 3.3-5.2-.3-1.1-1.3-2.1-1.3-3.6z"/><path d="M13.2 17.2c.7 1 .7 2.6 0 3.6M18.8 17.2c-.7 1-.7 2.6 0 3.6M14 22h4"/><path d="M4 28L28 13" opacity=".8"/></svg>',rev:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="16" cy="16" r="6"/><ellipse cx="16" cy="16" rx="13.5" ry="4.6" transform="rotate(-22 16 16)"/><circle cx="5" cy="6" r=".9" fill="currentColor" stroke="none"/><circle cx="27.5" cy="26" r=".9" fill="currentColor" stroke="none"/></svg>',dly:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="16" cy="16" r="12.4"/><circle cx="16" cy="16" r="2.2"/><circle cx="16" cy="9.2" r="3"/><circle cx="9.9" cy="19.5" r="3"/><circle cx="22.1" cy="19.5" r="3"/></svg>',eq:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M7 4v24M16 4v24M25 4v24" opacity=".45"/><rect x="4.4" y="17" width="5.2" height="4" rx="1.2"/><rect x="13.4" y="8.5" width="5.2" height="4" rx="1.2"/><rect x="22.4" y="13" width="5.2" height="4" rx="1.2"/></svg>',comp:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 24a12 12 0 0124 0z"/><path d="M7.4 17.2l1.8 1.1M12 12.4l1 1.8M20 12.4l-1 1.8M24.6 17.2l-1.8 1.1" stroke-width="1.2"/><path d="M16 24l6-8.6"/><circle cx="16" cy="24" r="1.6" fill="currentColor"/></svg>',max:'<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 5h26"/><circle cx="16" cy="18" r="5"/><path d="M16 10.4V8.4M9.6 12.2l-1.5-1.5M22.4 12.2l1.5-1.5M8.4 18H6M23.6 18H26M10 24l-1.6 1.6M22 24l1.6 1.6M16 25.6v2.4"/></svg>'};
const ICK=['rev','dly','eq','comp','max','pno','org','sax','str','gtr','amp','bas','brs','voc','syn'];
const CAT2=[[3,'Controls the punch','comp','Dynamics'],[4,'Loud, clean, finished','max','Dynamics'],[2,'Shapes the tone','eq','EQ'],[1,'Echoes that follow the groove','dly','Space'],[0,'Rooms, halls, deep space','rev','Space'],[10,'Tube amp · Cab · Mic','amp','Amps'],[11,'Bass head · Cab · Graphic EQ','bas','Amps'],[5,'Felt · Lid · Pedals','pno','Keys'],[6,'Drawbars · Rotary','org','Keys'],[14,'Oscillators · Filter · Motion','syn','Synth'],[7,'Breath · Reed · Growl','sax','Winds'],[12,'Mutes · Lips · Section','brs','Winds'],[8,'Bow · Section · Rosin','str','Strings'],[9,'Pick · Amp · Strum','gtr','Guitar'],[13,'Vowels · Choir · Breath','voc','Voices']];
let cf='All',cq='',cTab='fx';const FAMS={fx:['All','Dynamics','EQ','Space','Amps','Voices'],ins:['All','Keys','Guitar','Winds','Strings','Synth']};
function renderAdd(){const fams=FAMS[cTab];if(!fams.includes(cf))cf='All';$('#ah4').textContent=cTab=='fx'?'Add an effect':'Choose the instrument';
 $('#ag').innerHTML=`<div class="tbs"><span class="${cTab=='fx'?'a':''}" data-t="fx">EFFECTS<b>${effCount()}/6</b></span><span class="${cTab=='ins'?'a':''}" data-t="ins">INSTRUMENT<b>1 per track</b></span></div><div class="cf">${fams.map(f=>`<span class="${f==cf?'a':''}" data-f="${f}">${f}</span>`).join('')}<input id="cs" placeholder="Search…" value="${cq}"></div><div class="gl" id="gl"></div>`;listAdd();
 $('#cs').oninput=e=>{cq=e.target.value;listAdd()}}
function listAdd(){const q=cq.toLowerCase();$('#gl').innerHTML=CAT2.filter(([id,d,ic,f])=>(cTab=='ins')==INSTR.includes(id)&&(cf=='All'||f==cf)&&(!q||(FX[id].n+' '+FX[id].t+' '+d+' '+f).toLowerCase().includes(q))).map(([id,d,ic,f])=>{const ins=INSTR.includes(id),on=act.includes(id),dis=!ins&&(on||effCount()>=6);return`<div class="tl${dis?' dis':''}" data-a="${id}"><div class="ti">${IC[ic]}</div><div><b style="font-family:'${FX[id].ff}',cursive">${FX[id].n}</b><span>${f} · ${SOURCES.includes(id)?'Sound source · ':ins?'Shapes the soundfont · ':''}${d}</span></div>${on?'<em>✓ ON THIS TRACK</em>':''}</div>`}).join('')}
function openAdd(t){cTab=t==='ins'?'ins':'fx';renderAdd();$('#ad').classList.add('o')}
document.addEventListener('click',e=>{const t=e.target.closest('.tbs [data-t]');if(t){cTab=t.dataset.t;cf='All';renderAdd()}});
document.addEventListener('click',e=>{const f=e.target.closest('.cf [data-f]');if(f){cf=f.dataset.f;renderAdd()}});
$('#ag').onclick=e=>{const t=e.target.closest('.tl[data-a]');if(!t)return;$('#ad').classList.remove('o');setTimeout(()=>addFX(+t.dataset.a),260)};
$('#ax').onclick=()=>$('#ad').classList.remove('o');$('#pl').onclick=()=>openAdd('fx');$('#pi').onclick=()=>openAdd('ins');
const SFL=[['Grand Piano',0],['Alto Sax',65],['Violin',40],['Nylon Guitar',24],['Overdrive Guitar',29],['Trumpet',56],['Choir Aahs',52],['Drum Kit',128],['Warm Pad',89],['Fretless Bass',35]];let sfI=0;
const FAMOK={5:[[0,7],[16,23]],7:[[64,71]],8:[[40,51]],9:[[24,31]],12:[[56,63]]},KW={5:/piano|keys|clav|harpsichord/i,7:/sax|reed/i,8:/violin|viola|cello|string|orchestra|fiddle/i,9:/guitar|gtr|strat|nylon|banjo/i,12:/trumpet|trombone|horn|brass|tuba|cornet/i},SHN={5:'a piano',7:'a saxophone',8:'a violin or string section',9:'a guitar',12:'a brass instrument'},WS={};
function sfMatch(i,k=sfI){const[nm,gm]=SFL[k];return!FAMOK[i]||KW[i].test(nm)||FAMOK[i].some(([a,b])=>gm>=a&&gm<=b)}
function updWarn(){document.querySelectorAll('.pn .warn').forEach(w=>{const i=+w.dataset.w,bad=!sfMatch(i)&&!WS[i];w.style.display=bad?'flex':'none';if(bad)w.querySelector('.wt').innerHTML=`<b>This doesn't look like ${SHN[i]}.</b>The loaded soundfont is “${SFL[sfI][0]}”. Using ${FX[i].n} on it may sound wrong. Choose a matching soundfont for the best result.`})}
function updSf(){const src=act.find(j=>SOURCES.includes(j));$('#sf').textContent=src!=null?'Soundfont paused · '+SFL[sfI][0]:'Soundfont · '+SFL[sfI][0]+' ▾'}
const SFF=[['General Soundfont.sf2','30,2 MB'],['Alto Soft Vib.sf2','127,2 MB'],['Analog Warm Pad Vip.sf2','40,4 MB']];let sfTab='p',sfQ='';
function listSf(){const q=sfQ.toLowerCase();$('#sfc').innerHTML='<span class="a">Bank 0 (Main)</span><span>Bank 1</span><span>Bank 2</span><span>Bank 3</span><span>Bank 4</span>';
 $('#sfl').innerHTML=sfTab=='p'?SFL.map(([n,g],k)=>[n,g,k]).filter(([n])=>!q||n.toLowerCase().includes(q)).map(([n,g,k])=>`<div class="sfi${k==sfI?' a':''}" data-k="${k}"><i>${g==128?'D':g}</i><div><b>${n}</b><span>Bank ${g==128?128:0} · Preset #${g==128?0:g}</span></div>${k==sfI?'<em>✓ ACTIVE</em>':''}</div>`).join(''):SFF.map(([n,z],k)=>`<div class="sfi${k==0?' a':''}"><i>▤</i><div><b>${n}</b><span>${z}</span></div>${k==0?'<em>✓ LOADED</em>':''}</div>`).join('');
 document.querySelectorAll('.sft div').forEach(d=>d.classList.toggle('a',d.dataset.tab==sfTab))}
function openSf(){sfTab='p';sfQ='';$('#sfq').value='';listSf();$('#sfw').classList.add('o')}
function closeSf(){$('#sfw').classList.remove('o')}
$('#sf').onclick=openSf;$('#sfx').onclick=closeSf;$('#sfplus').onclick=()=>toast('Import a soundfont (opens the file picker in the app)');$('#sfq').oninput=e=>{sfQ=e.target.value;listSf()};
document.addEventListener('click',e=>{const t=e.target.closest('.sft [data-tab]');if(t){sfTab=t.dataset.tab;listSf();return}const r=e.target.closest('.sfi[data-k]');if(r){sfI=+r.dataset.k;for(const k in WS)delete WS[k];updSf();updWarn();closeSf();toast('Soundfont: '+SFL[sfI][0]);return}if(e.target.closest('[data-wc]')){openSf();return}const u=e.target.closest('[data-wu]');if(u){WS[+u.dataset.wu]=1;updWarn()}});
// open / close with morph
const ex=$('#ex'),win=$('#win'),tr=$('#tr');
function rel(el){const a=el.getBoundingClientRect(),w=win.getBoundingClientRect();return{l:(a.left-w.left)/S,t:(a.top-w.top)/S,w:a.width/S,h:a.height/S}}
function place(r,rad){ex.style.cssText=`left:${r.l}px;top:${r.t}px;width:${r.w}px;height:${r.h}px;border-radius:${rad}px;opacity:1;pointer-events:auto`}
function pmu(){const i=act[cur];if(i==null)return;$('#ed').classList.remove('o');document.querySelectorAll('.ib[data-ed]').forEach(b=>b.classList.remove('on'));$('#cl').style.color=i==4?'#2a241b':'';$('#cl').style.background=i==4?'#00000012':'';$('#dots').style.filter=i==4?'invert(1)':'';$('#pmn').textContent=P[i];$('#pml').innerHTML=Object.keys(FX[i].p).map(n=>`<div class="pi${n==P[i]?' c':''}" data-pi="${i}">${n}</div>`).join('')+'<div class="pi sv">＋ Save preset</div>'}
function open(i){cur=act.indexOf(i);const r=rel(document.querySelector(`.cd[data-i="${i}"]`));ex.style.transition='none';place(r,16);tr.style.opacity=0;$('#pm').classList.remove('v','o');
 requestAnimationFrame(()=>{tr.scrollLeft=cur*(tr.clientWidth||win.clientWidth);dotA(cur);pmu();requestAnimationFrame(()=>{ex.style.transition='';place({l:0,t:0,w:win.clientWidth,h:win.clientHeight},20);setTimeout(()=>{tr.style.opacity=1;$('#pm').classList.add('v')},170)})})}
function close(){$('#pm').classList.remove('v','o');const k=Math.round(tr.scrollLeft/tr.clientWidth),i=act[k];tr.style.opacity=0;const c=document.querySelector(`.cd[data-i="${i}"]`);if(c)place(rel(c),16);cur=-1;setTimeout(()=>{ex.style.opacity=0;ex.style.pointerEvents='none'},500)}
$('#cl').onclick=close;
tr.addEventListener('scroll',()=>{const k=Math.round(tr.scrollLeft/tr.clientWidth);if(k==cur)return;cur=k;$('#pm').classList.remove('o');pmu();dotA(k);$('#cl').style.color=act[k]==4?'#2a241b':'';$('#cl').style.background=act[k]==4?'#00000012':'';$('#dots').style.filter=act[k]==4?'invert(1)':''});
{let y0=null;ex.addEventListener('pointerdown',e=>{if(e.target.closest('.kb,#pm,#ed,canvas.dg'))return;y0=e.clientY});addEventListener('pointerup',e=>{if(y0!==null&&(e.clientY-y0)/S>90)close();y0=null})}
// bypass, presets
document.addEventListener('click',e=>{const b=e.target.closest('[data-b]');if(b){const i=+b.dataset.b;B[i]^=1;b.querySelector('.led').classList.toggle('on',!B[i]);b.closest('.pn').classList.toggle('by',B[i]);const c=document.querySelector(`.cd[data-i="${i}"]`);if(c)c.classList.toggle('on',!B[i])}
  const sv=e.target.closest('#pm .sv');if(sv){const i=act[cur];if(i!=null){const n='My preset '+(Object.keys(FX[i].p).filter(k=>k.startsWith('My preset')).length+1);FX[i].p[n]=[...V[i]];P[i]=n;$('#pr'+i).textContent=n;pmu();$('#pm').classList.remove('o');hap(14);toast('Saved as '+n)}}
 const q=e.target.closest('#pm [data-pi]');if(q){hap(12);const i=+q.dataset.pi,n=q.textContent;P[i]=n;$('#pr'+i).textContent=n;$('#pm').classList.remove('o');pmu();const a=[...V[i]],t0=performance.now(),to=FX[i].p[n];(function s(t){const u=Math.min(1,(t-t0)/380),k=1-Math.pow(1-u,3);V[i]=a.map((x,j)=>x+(to[j]-x)*k);upd(i);if(u<1)requestAnimationFrame(s)})(t0)}});
{let py=null;$('#pmh').addEventListener('pointerdown',e=>{py=e.clientY});$('#pmh').addEventListener('pointerup',e=>{if(py===null)return;const dy=e.clientY-py;py=null;if(Math.abs(dy)<10)$('#pm').classList.toggle('o');else $('#pm').classList.toggle('o',dy>0);hap(8)});
document.addEventListener('pointerup',e=>{if(!e.target.closest('#pm'))$('#pm').classList.remove('o')},true)}
// knobs
{let st=null,last=0;document.addEventListener('pointerdown',e=>{const k=e.target.closest('.kb');if(!k)return;const i=+k.dataset.i,j=+k.dataset.j;if(e.timeStamp-last<320){V[i][j]=FX[i].k[j][1];upd(i);return}last=e.timeStamp;st={i,j,x:e.clientX,y:e.clientY,v:V[i][j]};k.setPointerCapture(e.pointerId)});
document.addEventListener('pointermove',e=>{if(!st)return;let v=Math.min(1,Math.max(0,st.v+((e.clientX-st.x)+(st.y-e.clientY))/(170*S)));if(st.i==3&&st.j==1){v=Math.round(v*5)/5;if(v!==V[st.i][st.j])hap(10)}V[st.i][st.j]=v;upd(st.i)});document.addEventListener('pointerup',()=>st=null)}
// long press = lift card, drop on trash = delete
{let T=null,D=null,dn=null;const tz=$('#tz'),over=e=>{const r=tz.getBoundingClientRect();return e.clientY>r.top&&e.clientX>r.left&&e.clientX<r.right};
document.addEventListener('pointerdown',e=>{const c=e.target.closest('.cd');if(!c||ex.style.opacity==1)return;dn={x:e.clientX,y:e.clientY};T=setTimeout(()=>{D={c,x:dn.x,y:dn.y};c.classList.add('lift');tz.classList.add('o');navigator.vibrate&&navigator.vibrate(18)},520)});
document.addEventListener('pointermove',e=>{if(!D){if(dn&&Math.hypot(e.clientX-dn.x,e.clientY-dn.y)>10){clearTimeout(T)}return}const dx=(e.clientX-D.x)/S,dy=(e.clientY-D.y)/S;D.c.style.transform=`translate(${dx}px,${dy}px) scale(1.07) rotate(${dx*.03}deg)`;tz.classList.toggle('hot',over(e))});
const fin=(e,ok)=>{clearTimeout(T);dn=null;if(!D)return;lastDrag=performance.now();const c=D.c,i=+c.dataset.i,hit=ok&&over(e);D=null;tz.classList.remove('o','hot');if(hit)delFX(i);else{c.style.transition='transform .35s cubic-bezier(.2,1.3,.3,1)';c.style.transform='';c.classList.remove('lift');setTimeout(()=>c.style.transition='',400)}};
document.addEventListener('pointerup',e=>fin(e,1));document.addEventListener('pointercancel',e=>fin(e,0));document.addEventListener('touchmove',e=>{if(D)e.preventDefault()},{passive:false})}
// ---------- visuals ----------
function kg(x,cx,cy,r,v,a){x.save();x.fillStyle='#0a0c12';x.beginPath();x.arc(cx,cy,r,0,6.283);x.fill();x.lineWidth=1.6;x.strokeStyle='#ffffff22';x.beginPath();x.arc(cx,cy,r*.9,2.356,7.069);x.stroke();x.strokeStyle=a;x.beginPath();x.arc(cx,cy,r*.9,2.356,2.356+4.712*v);x.stroke();const g=x.createRadialGradient(cx-r*.3,cy-r*.3,1,cx,cy,r*.7);g.addColorStop(0,'#9a9ab2');g.addColorStop(1,'#25263a');x.fillStyle=g;x.beginPath();x.arc(cx,cy,r*.62,0,6.283);x.fill();const an=2.356+4.712*v;x.strokeStyle='#fff';x.beginPath();x.moveTo(cx+r*.2*Math.cos(an),cy+r*.2*Math.sin(an));x.lineTo(cx+r*.55*Math.cos(an),cy+r*.55*Math.sin(an));x.stroke();x.restore()}

const gs=[0,0];
let PWL=825,PHL=343,OXL=20,OYL=58,KS=1,TAB=0,SW=851,SH=393;
function draw(i,c,m){if(!m&&c.width<8)return;c.style.filter=`saturate(${(.25+.75*mo[i]).toFixed(2)})`;const t=ph[i],W=m?136:779,H=m?160:263,x=c.getContext('2d'),v=V[i],a=FX[i].ac,sx=c.width/(m?W:PWL),sy=c.height/(m?H:PHL);
 x.setTransform(sx,0,0,sy,m?0:OXL*sx,m?0:OYL*sy);x.clearRect(m?0:-OXL,m?0:-OYL,m?W:PWL,m?H:PHL);if(!m)bg(i,x,t,v,a);[orb,echo,eq,grip,ceil,piano,organ,sax,strings,guitar,amp,bass,brass,voices,synth][i](x,W,H,v,a,t,m);x.globalAlpha=1}
// ambient layers that run across the WHOLE effect window (not just the control area)
function bg(i,x,t,v,a){const X0=-OXL,Y0=-OYL,w=PWL,h=PHL;x.save();
 if(i==0){const g=x.createRadialGradient(389,114,20,389,114,520);g.addColorStop(0,`hsla(${268-v[3]*75},45%,30%,${.18+.35*v[5]})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(X0,Y0,w,h)}
 else if(i==1){const fb=.2+.8*v[8];for(let k=0;k<4;k++){const u=(t*.22+k/4)%1;x.globalAlpha=(1-u)*.2*fb;x.strokeStyle=a;x.lineWidth=1.5;x.beginPath();x.arc(95,80,20+u*640,0,6.283);x.stroke()}
  x.globalAlpha=.18;x.strokeStyle=a;x.lineWidth=1;for(let r=0;r<2;r++){x.beginPath();for(let q=X0;q<=X0+w;q+=8){const y=(r?Y0+h-22:Y0+20)+Math.sin(q*.02+t*(1+v[10]*3)+r*2)*(4+v[10]*10);q==X0?x.moveTo(q,y):x.lineTo(q,y)}x.stroke()}}
 else if(i==2){for(let k=0;k<3;k++){const px=X0+((t*30+k*300)%(w+400))-200,g=x.createLinearGradient(px-120,0,px+120,0);g.addColorStop(0,'#fff0');g.addColorStop(.5,'#ffffff1c');g.addColorStop(1,'#fff0');x.fillStyle=g;x.fillRect(X0,Y0,w,h)}}
 else if(i==3){const sq=Math.min(1,gs[0]*1.6),c=Y0+h/2;x.strokeStyle='#fff';x.lineWidth=1;for(let k=-8;k<=8;k++){x.globalAlpha=.06+.06*(1-Math.abs(k)/8);const y=c+k*(h/17)*(1-sq*.55);x.beginPath();x.moveTo(X0,y);x.lineTo(X0+w,y);x.stroke()}}
 else if(i==4){const ty=Y0+26+(1-v[1])*40,g=x.createLinearGradient(0,ty,0,ty+100);x.globalAlpha=.25+.3*v[0];x.fillStyle='#c9852c';x.fillRect(X0,ty,w,2);g.addColorStop(0,'#c9852c55');g.addColorStop(1,'#0000');x.globalAlpha=1;x.fillStyle=g;x.fillRect(X0,ty,w,100)}
 x.restore();x.globalAlpha=1}
function orb(x,W,H,v,a,t,m){const cx=W/2,cy=m?H*.4:114,sz=v[0],dc=v[1],pd=v[2],dm=v[3],wd=v[4],mx=v[5],df=v[6],R=(m?15:40)*(.7+sz*.8)*(1+.03*Math.sin(t*2)),hue=268-dm*75,col=`hsl(${hue},48%,74%)`,k=m?.55:1.2;
 let g;if(m){g=x.createRadialGradient(cx,cy,R*.3,cx,cy,R*4.5);g.addColorStop(0,`hsla(${hue},45%,36%,${.2+.5*mx})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(0,0,W,H)}
 for(let q=0;q<(m?16:70);q++){x.globalAlpha=.2+.5*Math.abs(Math.sin(t*1.3+q));x.fillStyle='#fff';x.fillRect((m?0:-OXL)+(Math.sin(q*91.7)*.5+.5)*(m?W:PWL),(m?0:-OYL)+(Math.sin(q*47.3)*.5+.5)*(m?H:PHL),1.2,1.2)}
 // ripples: rate follows size, silent gap follows pre-delay
 for(let w=0;w<3;w++){const u=(t*(.12+sz*.2)+w/3)%1;if(u<pd*.4)continue;x.globalAlpha=(1-u)*.4;x.strokeStyle=col;x.lineWidth=1.6;x.beginPath();x.arc(cx,cy,R*(1.1+u*(m?3:5)),0,6.283);x.stroke()}
 // ring A: dashed arcs (decay = dash length, width = precession)
 let rx=R*1.75*k,ry=rx*(.3+wd*.18),tl=-.35+Math.sin(t*.25)*wd*.55;x.globalAlpha=.3+.5*mx;x.strokeStyle=col;x.lineWidth=m?1.2:2.2;x.setLineDash([4+dc*(m?10:36),m?5:9]);x.lineDashOffset=-t*30*(.3+wd);x.beginPath();x.ellipse(cx,cy,rx,ry,tl,0,6.283);x.stroke();x.setLineDash([]);
 // ring B: wavy particle stream (diffuse = turbulence)
 rx=R*2.7*k;ry=rx*.36;tl=.25+Math.cos(t*.2)*wd*.4;const N=(m?10:22)+Math.round(sz*(m?6:14)),L=3+Math.round(dc*6);
 for(let p=0;p<N;p++){const an=t*(.25+wd*.9)+p*6.283/N;for(let s=0;s<L;s++){const aa=an-s*.08*(.4+dc),wob=Math.sin(aa*5+t*3)*df*R*.45*(m?.4:1),ca=Math.cos(aa),sa=Math.sin(aa),X=(rx+wob)*ca,Y=(ry+wob*.4)*sa;x.globalAlpha=(1-s/L)*.85;x.fillStyle=s?col:'#fff';x.beginPath();x.arc(cx+X*Math.cos(tl)-Y*Math.sin(tl),cy+X*Math.sin(tl)+Y*Math.cos(tl),(m?1.3:2.1)*(1-s/L*.5),0,6.283);x.fill()}}
 // ring C: comets with long tails (decay) + reverb-tail echoes of the orbit
 rx=Math.min(R*3.7*k,m?1e9:250);ry=rx*.3;tl=-.6+Math.sin(t*.17)*wd*.3;for(let e=0;e<3;e++){x.globalAlpha=.05+.25*dc*(1-e/3);x.strokeStyle=col;x.lineWidth=1;x.beginPath();x.ellipse(cx,cy,rx*(1+e*.06),ry*(1+e*.06),tl,0,6.283);x.stroke()}
 for(let c=0;c<3;c++){const an=-t*(.18+wd*.6)+c*2.094,T2=8+Math.round(dc*(m?8:20));for(let s=0;s<T2;s++){const aa=an+s*.05,ca=Math.cos(aa),sa=Math.sin(aa);x.globalAlpha=(1-s/T2)*(.3+.6*mx);x.fillStyle=s?col:'#fff';x.beginPath();x.arc(cx+rx*ca*Math.cos(tl)-ry*sa*Math.sin(tl),cy+rx*ca*Math.sin(tl)+ry*sa*Math.cos(tl),(m?1.6:3)*(1-s/T2*.6),0,6.283);x.fill()}}
 x.globalAlpha=1;g=x.createRadialGradient(cx-R*.35,cy-R*.4,R*.1,cx,cy,R);g.addColorStop(0,'#eee8ff');g.addColorStop(.35,a);g.addColorStop(1,'#1d1a38');x.fillStyle=g;x.beginPath();x.arc(cx,cy,R,0,6.283);x.fill();
 x.save();x.beginPath();x.arc(cx,cy,R,0,6.283);x.clip();x.strokeStyle='#fff';x.lineWidth=.8;for(let q=0;q<3;q++){x.globalAlpha=.1+.1*df;x.beginPath();x.ellipse(cx,cy,R*Math.abs(Math.cos(t*.4*(.5+wd)+q*1.05)),R,0,0,6.283);x.stroke()}x.restore();
 x.globalAlpha=.08+.4*mx;x.strokeStyle='#fff';x.lineWidth=1;x.beginPath();x.arc(cx,cy,R*1.08,0,6.283);x.stroke();x.globalAlpha=1;
 if(m)[0,1,3,5].forEach((j,n)=>kg(x,22+n*31,H-20,10,v[j],a))}
function echo(x,W,H,v,a,t,m){const px0=m?56:190,pw=m?74:400,py0=m?16:18,ph=m?98:218,pb=py0+ph,rc=m?[27,50]:[95,80],rr=m?19:62,sp=1.7-(v[0]+v[2]+v[4]+v[6])/4*1.3;
 x.save();x.translate(rc[0],rc[1]);x.rotate(t*sp+Math.sin(t*6)*v[10]*.1);let g=x.createLinearGradient(-rr,-rr,rr,rr);g.addColorStop(0,'#e8d2a0');g.addColorStop(.4,'#7d6335');g.addColorStop(.7,'#d9bf86');g.addColorStop(1,'#5f4a28');x.fillStyle=g;x.beginPath();x.arc(0,0,rr,0,6.283);x.fill();x.fillStyle='#14110c';for(let k=0;k<6;k++){x.save();x.rotate(k*1.0472);x.beginPath();x.ellipse(rr*.55,0,rr*.26,rr*.17,0,0,6.283);x.fill();x.restore()}x.fillStyle=g;x.beginPath();x.arc(0,0,rr*.22,0,6.283);x.fill();x.restore();
 g=x.createRadialGradient(rc[0]-rr*.4,rc[1]-rr*.45,2,rc[0],rc[1],rr);g.addColorStop(0,'#ffffff55');g.addColorStop(1,'#0000');x.fillStyle=g;x.beginPath();x.arc(rc[0],rc[1],rr,0,6.283);x.fill();
 x.fillStyle='#0d0b08';x.fillRect(px0,py0,pw,ph);x.globalAlpha=.07;x.fillStyle='#fff';for(let k=1;k<5;k++){x.fillRect(px0+pw*k/5,py0,1,ph);x.fillRect(px0,py0+ph*k/5,pw,1)}
 const P=[0,1,2,3].map(n=>[px0+v[2*n]*pw,pb-v[2*n+1]*ph]).sort((u,w)=>u[0]-w[0]),fb=.2+v[8]*.8;
 x.globalAlpha=.25*fb;x.fillStyle=a;P.forEach(q=>x.fillRect(q[0]-(m?1.5:4),q[1],m?3:8,pb-q[1]));const sp2=(P[3][0]-P[0][0])+pw*.1;for(let r=1;r<4;r++)P.forEach(q=>{const gx=q[0]+sp2*r;if(gx<px0+pw){x.globalAlpha=.18*Math.pow(fb,r+1);x.fillRect(gx-(m?1:3),q[1]+(pb-q[1])*(1-Math.pow(fb,r)),m?2:6,(pb-q[1])*Math.pow(fb,r))}});
 x.globalAlpha=.5+.5*fb;x.strokeStyle=a;x.lineWidth=m?1.4:2;x.beginPath();x.moveTo(px0,pb);P.forEach(q=>x.lineTo(q[0],q[1]));x.lineTo(px0+pw,pb);x.stroke();
 const u=(t*.28)%1,dx=px0+u*pw;let dy=pb;for(let k=0;k<=P.length;k++){const A=k?P[k-1]:[px0,pb],B=k<P.length?P[k]:[px0+pw,pb];if(dx>=A[0]&&dx<=B[0]){dy=A[1]+(B[1]-A[1])*(dx-A[0])/Math.max(1,B[0]-A[0]);break}}
 x.globalAlpha=1;g=x.createRadialGradient(dx,dy,0,dx,dy,m?6:14);g.addColorStop(0,'#fff');g.addColorStop(.3,a);g.addColorStop(1,'#0000');x.fillStyle=g;x.beginPath();x.arc(dx,dy,m?6:14,0,6.283);x.fill();
 for(let n=0;n<4;n++){const X=px0+v[2*n]*pw,Y=pb-v[2*n+1]*ph,r=m?3.4:9;g=x.createRadialGradient(X-r*.3,Y-r*.3,1,X,Y,r);g.addColorStop(0,'#fff0c8');g.addColorStop(.5,'#c9a45c');g.addColorStop(1,'#4a3a1c');x.fillStyle=g;x.beginPath();x.arc(X,Y,r,0,6.283);x.fill();if(!m){x.fillStyle='#d9c79a';x.font='600 9px sans-serif';x.textAlign='center';x.fillText(Math.round(10+v[2*n]*990)+' ms',X,Y-14)}}
 if(m)[8,11].forEach((j,n)=>kg(x,36+n*64,H-22,11,v[j],a))}

function eq(x,W,H,v,a,t,m){const X0=m?8:134,Wd=m?W-16:466,Y0=m?14:12,Hh=m?96:138,my=Y0+Hh/2,sg=.05+.14/(.3+v[4]*3.7);
 const cv=u=>(v[1]-.5)*2/(1+Math.exp((u-v[0])*16))+(v[3]-.5)*2*Math.exp(-Math.pow((u-v[2])/sg,2))+(v[6]-.5)*2/(1+Math.exp(-(u-v[5])*16)),yy=u=>my-cv(u)*Hh*.4;
 const rr=(x0,y0,w,h,r)=>{x.beginPath();x.moveTo(x0+r,y0);x.arcTo(x0+w,y0,x0+w,y0+h,r);x.arcTo(x0+w,y0+h,x0,y0+h,r);x.arcTo(x0,y0+h,x0,y0,r);x.arcTo(x0,y0,x0+w,y0,r);x.closePath()};
 rr(X0-4,Y0-4,Wd+8,Hh+8,16);x.fillStyle='#0000006a';x.fill();rr(X0,Y0,Wd,Hh,13);let g=x.createLinearGradient(0,Y0,0,Y0+Hh);g.addColorStop(0,'#1b070c');g.addColorStop(1,'#0c0305');x.fillStyle=g;x.fill();
 x.save();rr(X0,Y0,Wd,Hh,13);x.clip();
 g=x.createLinearGradient(0,Y0,0,Y0+Hh);g.addColorStop(0,'#ff2d4a2a');g.addColorStop(.5,'#0000');g.addColorStop(1,'#6fc8ff2a');x.fillStyle=g;x.fillRect(X0,Y0,Wd,Hh);
 x.globalAlpha=.1;x.fillStyle='#fff';[.25,.75].forEach(f=>x.fillRect(X0,Y0+Hh*f,Wd,1));[.2,.4,.6,.8].forEach(f=>x.fillRect(X0+f*Wd,Y0,1,Hh));
 x.setLineDash([5,5]);x.globalAlpha=.35;x.strokeStyle='#fff';x.beginPath();x.moveTo(X0,my);x.lineTo(X0+Wd,my);x.stroke();x.setLineDash([]);
 x.globalAlpha=.24;const nb=m?24:70;for(let k=0;k<nb;k++){const u=k/nb,hh=Hh*.3*(.35+.65*Math.abs(Math.sin(k*.6+t*2.1)*Math.sin(k*.17+t*.7)))*(1-u*.45)*(.55+v[7]*.9)*(1+cv(u)*.8);x.fillStyle=cv(u)>0?'#ff6b7e':'#7fd0ff';x.fillRect(X0+u*Wd,Y0+Hh-Math.max(1,hh),Wd/nb-1,Math.max(1,hh))}
 x.globalAlpha=1;const path=()=>{x.beginPath();x.moveTo(X0,yy(0));for(let k=0;k<=Wd;k+=3)x.lineTo(X0+k,yy(k/Wd))};
 x.save();x.beginPath();x.rect(X0,Y0,Wd,my-Y0);x.clip();path();x.lineTo(X0+Wd,my);x.lineTo(X0,my);x.closePath();g=x.createLinearGradient(0,Y0,0,my);g.addColorStop(0,'#ff3d58aa');g.addColorStop(1,'#ff3d5815');x.fillStyle=g;x.fill();x.restore();
 x.save();x.beginPath();x.rect(X0,my,Wd,Y0+Hh-my);x.clip();path();x.lineTo(X0+Wd,my);x.lineTo(X0,my);x.closePath();g=x.createLinearGradient(0,my,0,Y0+Hh);g.addColorStop(0,'#7fd0ff15');g.addColorStop(1,'#7fd0ffaa');x.fillStyle=g;x.fill();x.restore();
 x.lineWidth=m?2:3.4;x.lineJoin='round';x.save();x.translate(0,m?2:5);x.strokeStyle='#00000099';path();x.stroke();x.restore();
 [[Y0,my,'#ff5468'],[my,Y0+Hh,'#86d6ff']].forEach(([ya,yb,c])=>{x.save();x.beginPath();x.rect(X0,ya,Wd,yb-ya);x.clip();x.shadowColor=c;x.shadowBlur=m?6:12;x.strokeStyle=c;path();x.stroke();x.restore()});
 x.restore();
 if(!m){x.fillStyle='#ffe9e2';x.globalAlpha=.75;x.font='400 9px "Share Tech Mono",monospace';x.textAlign='center';[['100',.3],['1k',.6],['10k',.9]].forEach(([q,f])=>x.fillText(q,X0+f*Wd,Y0+Hh+16));x.textAlign='right';[['+12',0],['0',.5],['-12',1]].forEach(([q,f])=>x.fillText(q,X0-9,Y0+Hh*f+(f?3:9)));x.globalAlpha=1}
 [[0,1],[2,3],[5,6]].forEach(([F,G],b)=>{const nx=X0+v[F]*Wd,ny=Y0+(1-v[G])*Hh,r=m?4.5:(b==sel?13:11),up=v[G]>=.5,c1=up?'#ff6b7e':'#86d6ff';
  x.save();x.shadowColor='#000b';x.shadowBlur=m?4:9;x.shadowOffsetY=m?2:5;g=x.createRadialGradient(nx-r*.35,ny-r*.4,1,nx,ny,r);g.addColorStop(0,'#fff');g.addColorStop(.35,c1);g.addColorStop(1,up?'#6a0b18':'#124a6a');x.fillStyle=g;x.beginPath();x.arc(nx,ny,r,0,6.283);x.fill();x.restore();
  if(!m){x.fillStyle='#1a0508';x.font='700 9px "Share Tech Mono",monospace';x.textAlign='center';x.fillText('LMH'[b],nx,ny+3.2);
   if(b==sel){x.strokeStyle='#fff';x.globalAlpha=.6;x.lineWidth=1.5;x.beginPath();x.arc(nx,ny,r+5+Math.sin(t*4)*1.5,0,6.283);x.stroke();x.globalAlpha=1;const hz=20*Math.pow(1000,v[F]),gd=(v[G]-.5)*24;x.fillStyle='#ffe9e2';x.textAlign='left';x.font='400 10px "Share Tech Mono",monospace';x.fillText(`${['LOW','MID','HIGH'][b]}  ${hz>=1000?(hz/1000).toFixed(1)+' kHz':Math.round(hz)+' Hz'}  ${gd>=0?'+':''}${gd.toFixed(1)} dB`,X0+12,Y0+16)}}});
 if(!m){const fx=752,ty=26,by=232,th=ty+(1-v[7])*(by-ty);x.fillStyle='#00000066';rr(fx-4,ty-6,8,by-ty+12,4);x.fill();g=x.createLinearGradient(0,th,0,by);g.addColorStop(0,'#ffe9e2');g.addColorStop(1,'#ffb9a8');x.fillStyle=g;rr(fx-2,th,4,by-th,2);x.fill();
  x.save();x.shadowColor='#000a';x.shadowBlur=7;x.shadowOffsetY=4;g=x.createLinearGradient(0,th-11,0,th+11);g.addColorStop(0,'#f4f0ea');g.addColorStop(.5,'#a39b95');g.addColorStop(1,'#5c5550');x.fillStyle=g;rr(fx-15,th-11,30,22,5);x.fill();x.restore();x.fillStyle='#2a0c10';x.fillRect(fx-10,th-1,20,2);
  x.fillStyle='#ffe9e2';x.font='400 9px "Share Tech Mono",monospace';x.textAlign='center';x.fillText('OUT',fx,ty-12);x.fillText(((v[7]-.5)*24).toFixed(1)+' dB',fx,by+20)}
 if(m)[1,3,6].forEach((j,n)=>kg(x,26+n*42,H-22,12,v[j],a))}
function grip(x,W,H,v,a,t,m){const th=v[0],R=RAT[Math.round(v[1]*5)],L=.52+.36*Math.sin(t*2.3)*Math.sin(t*.7+1)+.08*Math.sin(t*7.1),gr=Math.max(0,L-th*.9)*(1-1/R)*2.2;gs[m]+=(gr-gs[m])*.1;const g1=Math.min(1,gs[m]*1.6);
 const gx=m?W/2:135,gy=m?78:218,gR=m?46:118,a0=Math.PI*1.17,a1=Math.PI*1.83;
 let g=x.createLinearGradient(0,gy-gR,0,gy+8);g.addColorStop(0,'#d6d0bb');g.addColorStop(1,'#a79f86');
 x.fillStyle='#1c1e24';x.beginPath();x.arc(gx,gy,gR+8,Math.PI,6.283);x.lineTo(gx+gR+8,gy+12);x.lineTo(gx-gR-8,gy+12);x.fill();x.fillStyle=g;x.beginPath();x.arc(gx,gy,gR+3,Math.PI,6.283);x.lineTo(gx+gR+3,gy+7);x.lineTo(gx-gR-3,gy+7);x.fill();
 for(let k=0;k<=20;k++){const an=a0+(a1-a0)*k/20,r1=gR*.84,r2=gR*(k%5?.9:.96);x.strokeStyle=k>15?'#a33':'#2a2820';x.lineWidth=k%5?1:2;x.beginPath();x.moveTo(gx+r1*Math.cos(an),gy+r1*Math.sin(an));x.lineTo(gx+r2*Math.cos(an),gy+r2*Math.sin(an));x.stroke()}
 const na=a0+(a1-a0)*g1;x.save();x.shadowColor='#0009';x.shadowBlur=6;x.shadowOffsetX=3;x.strokeStyle='#14120e';x.lineWidth=m?1.5:2.6;x.beginPath();x.moveTo(gx,gy+4);x.lineTo(gx+gR*.9*Math.cos(na),gy+gR*.9*Math.sin(na));x.stroke();x.restore();
 g=x.createRadialGradient(gx-3,gy-3,1,gx,gy+2,m?7:14);g.addColorStop(0,'#aab2c0');g.addColorStop(1,'#2a2e38');x.fillStyle=g;x.beginPath();x.arc(gx,gy+2,m?6:13,0,6.283);x.fill();
 if(!m){x.fillStyle='#2a2820';x.font='700 10px sans-serif';x.textAlign='center';x.fillText('GAIN REDUCTION',gx,gy-gR*.42)}
 const PX=m?10:272,PW=m?116:150,PY=m?104:20,PH=m?28:223,gap=PH*(1-g1*.64),pc=PY+PH/2;
 x.fillStyle='#0c0d10';x.fillRect(PX,PY,PW,PH);x.strokeStyle=a;x.lineWidth=m?1.2:2;x.beginPath();for(let k=0;k<=PW;k+=2){const y=pc+Math.sin(k*.09+t*5)*(gap/2)*.9*(.6+.4*Math.sin(k*.03+t));k?x.lineTo(PX+k,y):x.moveTo(PX+k,y)}x.stroke();
 const pl=(y0,h)=>{const q=x.createLinearGradient(0,y0,0,y0+h);q.addColorStop(0,'#e1e6ee');q.addColorStop(.5,'#8a92a1');q.addColorStop(1,'#4d5461');x.fillStyle=q;x.fillRect(PX,y0,PW,h);x.fillStyle='#0004';x.fillRect(PX,y0+h-1,PW,1)};pl(PY,(PH-gap)/2);pl(PY+PH-(PH-gap)/2,(PH-gap)/2);
 if(!m){x.fillStyle='#2a2e38';[[PX+10,PY+8],[PX+PW-10,PY+8],[PX+10,PY+PH-8],[PX+PW-10,PY+PH-8]].forEach(q=>{x.beginPath();x.arc(q[0],q[1],3,0,6.283);x.fill()});
  const ty=30+(1-th)*205;x.fillStyle='#08090c';x.fillRect(452,30,8,205);x.fillStyle=a;x.globalAlpha=.7;x.fillRect(452,ty,8,235-ty);x.globalAlpha=1;g=x.createLinearGradient(0,ty-11,0,ty+11);g.addColorStop(0,'#dfe4ec');g.addColorStop(.5,'#8b93a3');g.addColorStop(1,'#515866');x.fillStyle=g;x.fillRect(438,ty-11,36,22);x.fillStyle='#14120e';x.fillRect(442,ty-1,28,2);x.fillStyle='#cfd6e6';x.font='700 9px sans-serif';x.textAlign='center';x.fillText('THRESH '+Math.round(th*60-60)+' dB',456,252);
  x.setLineDash([4,4]);x.globalAlpha=.5;x.strokeStyle='#e0a24a';x.beginPath();x.moveTo(PX,pc-th*PH*.42);x.lineTo(PX+PW,pc-th*PH*.42);x.stroke();x.setLineDash([]);x.globalAlpha=1}
 if(m)[1,5].forEach((j,n)=>kg(x,36+n*64,H-18,11,v[j],a))}
function ceil(x,W,H,v,a,t,m){const am=v[0],cl=v[1],SX=m?10:390,SW=m?116:300,SY=m?16:20,SH=m?96:223,my=SY+SH/2,kx=m?W/2:125,ky=m?H-34:128;
 if(!m||true){const R0=(m?18:50)+am*(m?30:130),g=x.createRadialGradient(kx,ky,4,kx,ky,R0);g.addColorStop(0,`rgba(224,162,74,${.18+.3*am})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(0,0,m?W:380,H);
  for(let k=0;k<(m?10:26);k++){const an=-Math.PI*.95+k*Math.PI*1.9/(m?10:26)+Math.sin(t*.5)*.05,len=(m?20:70)+am*(m?30:110)*(.6+.4*Math.sin(t*2+k));x.globalAlpha=.08+.28*am;x.strokeStyle='#e0a24a';x.lineWidth=m?1:1.6;x.beginPath();x.moveTo(kx+Math.cos(an)*(m?14:62),ky+Math.sin(an)*(m?14:62));x.lineTo(kx+Math.cos(an)*((m?14:62)+len),ky+Math.sin(an)*((m?14:62)+len));x.stroke()}x.globalAlpha=1}
 x.fillStyle='#17140f';x.fillRect(SX,SY,SW,SH);const gn=1+am*2.4,cy=my-cl*SH*.42,fy=my+cl*SH*.42;let hit=0;
 x.strokeStyle='#e0a24a';x.lineWidth=m?1.4:2.2;x.beginPath();for(let k=0;k<=SW;k+=2){const u=k/SW,y0=Math.sin(u*(m?9:13)+t*3)*.72+Math.sin(u*(m?22:31)+t)*.2,s=Math.max(-cl,Math.min(cl,y0*gn));if(Math.abs(y0*gn)>cl)hit++;k?x.lineTo(SX+k,my-s*SH*.42):x.moveTo(SX+k,my-s*SH*.42)}x.stroke();
 const hf=Math.min(1,hit/(SW/2));let g2=x.createLinearGradient(0,cy-30,0,cy);g2.addColorStop(0,'#0000');g2.addColorStop(1,`rgba(224,162,74,${.15+.5*hf})`);x.fillStyle=g2;x.fillRect(SX,cy-30,SW,30);
 const pl=(y0,h)=>{const q=x.createLinearGradient(0,y0,0,y0+h);q.addColorStop(0,'#f1dfae');q.addColorStop(.5,'#b9934d');q.addColorStop(1,'#6e5226');x.fillStyle=q;x.fillRect(SX,y0,SW,h)};pl(SY,Math.max(3,cy-SY));pl(fy,Math.max(3,SY+SH-fy));
 if(m)kg(x,kx,ky,17,am,'#c9852c')}

document.addEventListener('pointerdown',e=>{const c=e.target.closest('canvas.dg');if(!c)return;const i=+c.closest('.pn').dataset.i,r=c.getBoundingClientRect(),X=(e.clientX-r.left)/r.width*PWL-OXL,Y=(e.clientY-r.top)/r.height*PHL-OYL,v=V[i];let best=-1,bd=38;
 if(i==1)for(let n=0;n<4;n++){const d=Math.hypot(X-(190+v[2*n]*400),Y-(236-v[2*n+1]*218));if(d<bd){bd=d;best=n}}
 if(i==2){[[0,1],[2,3],[5,6]].forEach(([F,G],b)=>{const d=Math.hypot(X-(134+v[F]*466),Y-(12+(1-v[G])*138));if(d<bd){bd=d;best=b}});if(best<0&&X>728&&Y>8&&Y<256)best=9}
 if(i==3&&X>428&&X<486&&Y>12&&Y<248)best=0;
 if(i==5){const d=(a,b)=>Math.hypot(X-a,Y-b);if(d(44,158)<28)best=10;else if(d(720,40)<30)best=11;else if(X>412&&X<446&&Y>170&&Y<236)best=12;else if(d(80,30+v[3]*110)<28)best=13;else if(d(430+v[4]*300,118)<28)best=14;else if(X>528&&X<752&&Y>232&&Y<272)best=15}
 if(i==6){for(let k=0;k<9;k++){if(Math.abs(X-(24+k*26))<12&&Y>28&&Y<176){best=20+k;break}if(Math.abs(X-(278+k*26))<12&&Y>28&&Y<176){best=30+k;break}}if(best<0&&Math.hypot(X-578,Y-112)<30){best=50;V[6][22]=((Math.round(V[6][22]*5)+1)%6)/5;hap(12);upd(6)}if(best<0&&X>528&&X<752&&Y>232&&Y<272)best=15}
 if(i>=7){(HS[i]||[]).forEach((h,k)=>{if(best<0&&Math.hypot(X-h[0],Y-h[1])<h[2]+10)best=100+k});if(best<0&&![10,11].includes(i)&&X>528&&X<752&&Y>232&&Y<272)best=15}
 if(best<0)return;if(i==2&&best<3){if(sel!==best)hap(12);sel=best}const hh=best>=100?HS[i][best-100]:null;dr={i,n:best,c,y0:Y,x0:X,v0:hh&&hh[3]>=0?v[hh[3]]:0,vh0:hh&&hh[4]>=0?v[hh[4]]:0};if(hh)hap(8);c.setPointerCapture(e.pointerId)});
document.addEventListener('pointermove',e=>{if(!dr)return;const r=dr.c.getBoundingClientRect(),X=(e.clientX-r.left)/r.width*PWL-OXL,Y=(e.clientY-r.top)/r.height*PHL-OYL,v=V[dr.i],k=(z,a=0,b=1)=>Math.min(b,Math.max(a,z));
 if(dr.i==1){v[2*dr.n]=k((X-190)/400);v[2*dr.n+1]=k((236-Y)/218)}
 else if(dr.i==2){if(dr.n==9)v[7]=k(1-(Y-26)/206);else{const b=dr.n,F=[0,2,5][b],G=[1,3,6][b],z=[[0,.35],[.25,.75],[.65,1]][b];v[F]=k((X-134)/466,z[0],z[1]);v[G]=k(1-(Y-12)/138)}upd(2)}
 else if(dr.i==3){v[0]=k(1-(Y-30)/205);upd(3)}
 else if(dr.i==5){const n=dr.n;if(n==10)v[0]=k(1-(Y-60)/170);else if(n==11)v[1]=k(1-(Y-10)/140);else if(n==12)v[2]=k((Y-170)/60);else if(n==13)v[3]=k((Y-30)/110);else if(n==14)v[4]=k((X-430)/300);else if(n==15)v[8]=k((X-540)/200);upd(5)}
 else if(dr.i>=7){const h=dr.n>=100?HS[dr.i][dr.n-100]:null;if(dr.n==15)v[v.length-1]=k((X-540)/200);else if(h){if(h[5]){v[h[3]]=k(1-(Y-h[5][1])/h[5][3]);if(h[4]>=0)v[h[4]]=k((X-h[5][0])/h[5][2])}else{if(h[3]>=0)v[h[3]]=k(dr.v0+(dr.y0-Y)/(h[7]||110));if(h[4]>=0)v[h[4]]=k(dr.vh0+(X-dr.x0)/(h[6]||160))}}upd(dr.i)}
 else if(dr.i==6){if(dr.n==15)v[30]=k((X-540)/200);else if(dr.n<50){const j=dr.n<30?dr.n-20:dr.n-30+9,nv=Math.round(k((Y-38)/124)*8)/8;if(nv!==v[j])hap(8);v[j]=nv}upd(6)}});
document.addEventListener('pointerup',()=>dr=null);
// loop: motion eases to zero on bypass
let lt=0,lm=0,lastScroll=0,lastFrame=0,fpsT=2,slow=0,fast=0,QS=1,QMAX=1,CTX=0,LITE=false,DBG=0,LID=0;const ERR={},NOD=new Set();
function szc(c,on){const w=on?Math.round(PWL*QS):2,h=on?Math.round(PHL*QS):2;if(c.width!==w||c.height!==h){c.width=w;c.height=h}}
function sdraw(i,c,m){const k=i+(m?'m':'p');if(NOD.has(k))return;try{draw(i,c,m)}catch(e){const q=k+': '+(e&&e.message||e);ERR[q]=(ERR[q]||0)+1;if(ERR[q]>40)NOD.add(k)}}
$('#tr').addEventListener('scroll',()=>{lastScroll=performance.now()},{passive:true});
function startLoop(){const id=++LID;lt=performance.now();(function L(n){if(id!==LID)return;requestAnimationFrame(L);lastFrame=n;const dt=Math.min(.05,(n-lt)/1000);lt=n;
 try{act.forEach(i=>{mo[i]+=((B[i]?0:1)-mo[i])*Math.min(1,dt*5);ph[i]+=dt*mo[i]})}catch(e){}
 const t0=performance.now();
 if(cur<0&&n-lm>66){lm=n;document.querySelectorAll('.cd canvas').forEach(c=>sdraw(+c.closest('.cd').dataset.i,c,1))}
 document.querySelectorAll('.pn canvas.vc').forEach(c=>{if(!c._h){c._h=1;c.addEventListener('contextlost',e=>{e.preventDefault();CTX++});c.addEventListener('contextrestored',()=>{c._r=1})}
  const i=+c.closest('.pn').dataset.i,k=act.indexOf(i),on=cur>=0&&(k===cur||(n-lastScroll<500&&Math.abs(k-cur)<=1));szc(c,on);if(on)sdraw(i,c,0)});
 const ft=performance.now()-t0;fpsT+=(ft-fpsT)*.1;
 if(cur>=0){if(fpsT>12){if(++slow>30){slow=0;fast=0;if(QS>.9){QS=Math.max(.9,QS*.8)}LITE=true}}else{slow=0;if(fpsT<5&&++fast>300){fast=0;QS=Math.min(QMAX,QS*1.1);if(QS>=QMAX*.98)LITE=false}}}
 if(DBG&&n%30<1.2){const px=[...document.querySelectorAll('.pn canvas.vc')].reduce((a,c)=>a+c.width*c.height,0);$('#dbg').textContent=`draw ${fpsT.toFixed(1)} ms/frame\nquality ${QS.toFixed(2)} (max ${QMAX.toFixed(2)})  lite ${LITE}\ncanvas px ${(px/1e6).toFixed(2)} M\ncontext lost ${CTX}\nerrors ${Object.keys(ERR).length}${Object.keys(ERR).length?'\n'+Object.entries(ERR).slice(0,3).map(([k,v])=>k.slice(0,60)+' ×'+v).join('\n'):''}`}
 })(0)}
startLoop();setInterval(()=>{if(document.visibilityState==='visible'&&performance.now()-lastFrame>1500)startLoop()},1000);document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')startLoop()});
$('#db').onclick=()=>{DBG^=1;$('#dbg').style.display=DBG?'block':'none'};
renderRack();dots();
const PE=[new Float32Array(48),new Float32Array(48)],PT=[0,0];let LS=0,LP=0,OT=0;
function rr2(x,a,b,w,h,r){x.beginPath();x.moveTo(a+r,b);x.arcTo(a+w,b,a+w,b+h,r);x.arcTo(a+w,b+h,a,b+h,r);x.arcTo(a,b+h,a,b,r);x.arcTo(a,b,a+w,b,r);x.closePath()}
const RS={5:{bg:['#1f1f26','#07070a'],ln:'#e8c98a',fill:['#c9a45c','#f2d58a'],th:'ivory',tx:'#e8e0d0',pat:'lacquer'},6:{bg:['#4a2a16','#1a0d07'],ln:'#d9c08a',fill:['#c9a45c','#f2d58a'],th:'ivory',tx:'#f3e6cc',pat:'wood'},7:{bg:['#2a1810','#0e0806'],ln:'#e0a45a',fill:['#b86a2a','#f0b070'],th:'pearl',tx:'#f4e4d0',pat:'copper'},8:{bg:['#4a3a20','#1c1408'],ln:'#d9a05b',fill:['#9a6a3a','#e0b070'],th:'wood',tx:'#f0e2c8',pat:'wood'},9:{bg:['#2a2e36','#0a0b0e'],ln:'#c0c8d0',fill:['#8a929c','#e0e6ee'],th:'nickel',tx:'#dfe4ea',pat:'steel'},12:{bg:['#4a0e1c','#14060a'],ln:'#e8c060',fill:['#b8902a','#f4d070'],th:'brass',tx:'#f4e4c0',pat:'velvet'},13:{bg:['#ffffff22','#ffffff08'],ln:'#c9b8ff',fill:['#9a88ff','#e0d8ff'],th:'glass',tx:'#e8e4ff',pat:'glass'},14:{bg:['#0a1220','#04060c'],ln:'#7ad0ff',fill:['#2a7aa0','#7ad0ff'],th:'led',tx:'#a8d8f0',pat:'led'}};
function rail(x,lv,ii){const R=RS[ii]||RS[6],X0=-OXL,Y0=214,RW=PWL,RH=Math.max(71,PHL-OYL-214);let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,R.bg[0]);g.addColorStop(1,R.bg[1]);x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
 x.save();x.beginPath();x.rect(X0,Y0,RW,RH);x.clip();
 if(R.pat=='wood'){x.globalAlpha=.1;x.strokeStyle='#000';x.lineWidth=1;for(let k=0;k<8;k++){x.beginPath();for(let u=0;u<=RW;u+=10){const y=Y0+8+k*9+Math.sin(u*.02+k)*2;u?x.lineTo(X0+u,y):x.moveTo(X0,y)}x.stroke()}}
 else if(R.pat=='steel'){x.globalAlpha=.07;x.strokeStyle='#fff';for(let k=0;k<RH;k+=2){x.beginPath();x.moveTo(X0,Y0+k);x.lineTo(X0+RW,Y0+k);x.stroke()}x.globalAlpha=.5;x.fillStyle='#0008';for(let k=0;k<8;k++){x.beginPath();x.arc(X0+14+k*(RW-28)/7,Y0+RH-10,2.4,0,6.283);x.fill()}}
 else if(R.pat=='velvet'){x.globalAlpha=.12;x.strokeStyle='#000';for(let k=-RH;k<RW;k+=7){x.beginPath();x.moveTo(X0+k,Y0);x.lineTo(X0+k+RH,Y0+RH);x.stroke()}}
 else if(R.pat=='copper'){x.globalAlpha=.1;x.fillStyle='#ffd0a0';for(let k=0;k<RW;k+=14)for(let r=0;r<RH;r+=14){x.beginPath();x.arc(X0+k+7,Y0+r+7,1.6,0,6.283);x.fill()}}
 else if(R.pat=='led'){x.globalAlpha=.08;x.strokeStyle='#7ad0ff';for(let k=0;k<RW;k+=18){x.beginPath();x.moveTo(X0+k,Y0);x.lineTo(X0+k,Y0+RH);x.stroke()}}
 else if(R.pat=='glass'){x.globalAlpha=.5;g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,'#ffffff22');g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH)}
 x.restore();
 g=x.createLinearGradient(X0,0,X0+RW,0);g.addColorStop(0,R.ln+'00');g.addColorStop(.5,R.ln);g.addColorStop(1,R.ln+'00');x.fillStyle=g;x.fillRect(X0,Y0,RW,R.th=='led'?1:2);
 const sx=540,sw=200,sy=252;x.fillStyle='#0009';rr2(x,sx-3,sy-4,sw+6,8,4);x.fill();g=x.createLinearGradient(sx,0,sx+sw,0);g.addColorStop(0,R.fill[0]);g.addColorStop(1,R.fill[1]);x.fillStyle=g;rr2(x,sx,sy-2,Math.max(2,sw*lv),4,2);x.fill();
 const tx=sx+sw*lv;x.save();x.shadowColor='#000a';x.shadowBlur=6;x.shadowOffsetY=3;
 if(R.th=='led'){x.shadowColor=R.ln;x.shadowBlur=10;x.shadowOffsetY=0;x.fillStyle=R.ln;rr2(x,tx-2,sy-11,4,22,2);x.fill()}
 else if(R.th=='glass'){g=x.createLinearGradient(0,sy-11,0,sy+11);g.addColorStop(0,'#ffffffcc');g.addColorStop(1,'#c9b8ff88');x.fillStyle=g;rr2(x,tx-9,sy-11,18,22,9);x.fill();x.strokeStyle='#fff8';x.lineWidth=1;x.stroke()}
 else{const c0={ivory:['#f4efe6','#a8a090','#5c554a'],pearl:['#ffffff','#e6d6c6','#8a7a6a'],wood:['#e2bd86','#b98a52','#6a4020'],nickel:['#f4f6fa','#a0a8b2','#555c66'],brass:['#f6dc88','#c4982e','#6a4e10']}[R.th]||['#f4efe6','#a8a090','#5c554a'];g=x.createLinearGradient(0,sy-11,0,sy+11);g.addColorStop(0,c0[0]);g.addColorStop(.5,c0[1]);g.addColorStop(1,c0[2]);x.fillStyle=g;rr2(x,tx-8,sy-11,16,22,R.th=='pearl'||R.th=='brass'?8:4);x.fill();x.restore();x.save();x.fillStyle='#2a1a10';x.fillRect(tx-1,sy-7,2,14)}
 x.restore();x.fillStyle=R.tx;x.font='400 9px "Share Tech Mono",monospace';x.textAlign='left';x.fillText('LEVEL',sx,sy-17);x.textAlign='right';x.fillText((lv*30-24).toFixed(1)+' dB',sx+sw,sy-17)}
function mic(x,cx,cy,r,col){x.save();x.shadowColor='#000b';x.shadowBlur=6;x.shadowOffsetY=3;let g=x.createRadialGradient(cx-r*.3,cy-r*.3,1,cx,cy,r);g.addColorStop(0,'#aeb6c0');g.addColorStop(1,col||'#2a2e34');x.fillStyle=g;x.beginPath();x.arc(cx,cy,r,0,6.283);x.fill();x.restore();x.strokeStyle='#0008';x.lineWidth=.8;for(let k=-2;k<=2;k++){x.beginPath();x.moveTo(cx-r*.8,cy+k*r*.32);x.lineTo(cx+r*.8,cy+k*r*.32);x.stroke()}}
function tag(x,txt,cx,cy,al){x.fillStyle='#f3e6cc';x.globalAlpha=.8;x.font='400 8px "Share Tech Mono",monospace';x.textAlign=al||'center';x.fillText(txt,cx,cy);x.globalAlpha=1}
function piano(x,W,H,v,a,t,m){const names=Object.keys(FX[5].p),kind=[0,1,2,3,0,1][Math.max(0,names.indexOf(P[5]))]||0,X0=m?0:-OXL,Y0=m?0:-OYL,RW=m?W:PWL,RH=m?H:PHL,hard=v[0],lid=v[1],sus=v[2],room=v[3],wid=v[4],tone=v[5],body=v[6],space=v[7],q=m?1:0,brt=.2+.65*lid*(.55+.45*tone);
 const pal=[['#4a2a16','#1a0d07'],['#1b2027','#0b0e12'],['#5a341c','#22110a'],['#101018','#050508']][kind];
 let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,pal[0]);g.addColorStop(1,pal[1]);x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
 x.globalAlpha=.06+.07*body;x.strokeStyle='#000';x.lineWidth=1;for(let k=0;k<(m?6:24);k++){const yy=Y0+RH*k/(m?6:24);x.beginPath();for(let u=0;u<=RW;u+=12){const y=yy+Math.sin(u*.01+k)*3+Math.sin(u*.043+k*2)*1.2;u?x.lineTo(X0+u,y):x.moveTo(X0,y)}x.stroke()}x.globalAlpha=1;
 const lx=kind==3?X0+RW*.5:X0+RW*.22;g=x.createRadialGradient(lx,Y0,10,lx,Y0,RH*1.1);g.addColorStop(0,`rgba(255,226,170,${.1+.32*brt})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
 const nS=m?16:46,top=Y0+RH*(m?.12:.2),bot=Y0+RH*(m?.62:.6),E=PE[q],dt=Math.max(0,Math.min(.1,t-PT[q]));PT[q]=t;
 const ev=Math.floor(t*2.4);if(ev!==E.ev){E.ev=ev;E[(ev*17+5)%nS]=1}for(let k=0;k<nS;k++)E[k]*=Math.exp(-dt*(2.6-sus*2.2));
 x.globalAlpha=.16;x.fillStyle='#c9a45c';x.beginPath();x.moveTo(X0+RW*.03,bot+8);for(let k=0;k<=24;k++){const u=k/24;x.lineTo(X0+RW*(.03+.94*u),top+(bot-top)*.5*Math.pow(u,.9)-12)}x.lineTo(X0+RW*.97,bot+8);x.closePath();x.fill();x.globalAlpha=1;
 if(kind==2){x.globalAlpha=.85;x.fillStyle='#8a1c24';x.fillRect(X0,top+(bot-top)*.38,RW,m?3:9);x.globalAlpha=1}
 for(let k=0;k<nS;k++){const u=k/(nS-1),px=X0+RW*(.05+.9*u),y0=top+(bot-top)*.5*Math.pow(u,.9),len=bot-y0,pb=px+(X0+RW*.5-px)*.1,A=(m?1.4:3.6)*E[k];x.lineWidth=m?.7:(.8+(1-u)*1.5);x.strokeStyle=kind==1?'#c9d3df':(u<.3?'#d49a5a':'#e6dcc2');x.globalAlpha=.5+.45*brt;x.beginPath();for(let s=0;s<=10;s++){const yy=y0+len*s/10,dx=Math.sin(Math.PI*s/10)*A*Math.sin(t*(40+u*60)),xx=px+(pb-px)*s/10+dx;s?x.lineTo(xx,yy):x.moveTo(xx,yy)}x.stroke();
  x.globalAlpha=1;x.fillStyle='#b99a58';x.beginPath();x.arc(px,y0-5,m?1:2.2,0,6.283);x.fill();if(brt>.25&&E[k]>.15){x.globalAlpha=E[k]*brt;x.fillStyle='#fff';x.fillRect(px-1,y0+len*(.3+.4*Math.sin(t*3+k)),2,m?3:6)}}
 x.globalAlpha=1;const hw=(RW*.9/nS)*2.3*(.6+.7*hard);
 for(let k=0;k<nS;k+=2){const u=k/(nS-1),px0=X0+RW*(.05+.9*u),px=px0+(X0+RW*.5-px0)*.1,hh=m?8:20,hy=bot+4-Math.max(0,E[k]-.55)*40;x.fillStyle='#2a1810';x.fillRect(px-1.5,hy+hh*.5,3,m?10:26);
  g=x.createRadialGradient(px,hy,1,px,hy,hh);const f=.55+.4*(1-hard);g.addColorStop(0,`rgb(${Math.round(240*f)},${Math.round(228*f)},${Math.round(200*f)})`);g.addColorStop(1,'#6b5a3a');x.fillStyle=g;x.beginPath();x.ellipse(px,hy,hw/2*(m?.7:1),hh*.55,0,0,6.283);x.fill()}
 if(kind==1&&!m){x.fillStyle='#07090c';x.fillRect(X0+54,Y0+RH*.3,3,RH*.4);x.beginPath();x.arc(X0+55,Y0+RH*.3,22,0,6.283);x.fill()}
 if(kind==3){g=x.createLinearGradient(X0,Y0,X0+RW,Y0+RH*.5);g.addColorStop(0,'#fff0');g.addColorStop(.5,'#ffffff14');g.addColorStop(1,'#fff0');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH*.5)}
 if(space>.05){const u=(t*.3)%1;x.globalAlpha=(1-u)*space*.4;x.strokeStyle='#ffe2aa';x.lineWidth=2;x.beginPath();x.ellipse(X0+RW*.5,Y0+RH*.5,RW*(.15+u*.6),RH*(.1+u*.35),0,0,6.283);x.stroke();x.globalAlpha=1}
 g=x.createRadialGradient(X0+RW/2,Y0+RH/2,RH*.3,X0+RW/2,Y0+RH/2,RW*.62);g.addColorStop(0,'#0000');g.addColorStop(1,'#000a');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
 if(m){[0,1,5].forEach((j,n)=>kg(x,28+n*40,H-20,11,v[j],a));return}
 // ---- lid (angle = Lid) with prop rod hot-spot
 const ly=Y0+16+(1-lid)*100;g=x.createLinearGradient(0,Y0,0,ly);g.addColorStop(0,'#05060a');g.addColorStop(1,'#1a1d26');x.fillStyle=g;x.beginPath();x.moveTo(440,Y0);x.lineTo(X0+RW,Y0);x.lineTo(X0+RW,ly+22);x.lineTo(540,ly);x.closePath();x.fill();
 g=x.createLinearGradient(440,Y0,X0+RW,ly);g.addColorStop(0,'#fff0');g.addColorStop(.5,'#ffffff22');g.addColorStop(1,'#fff0');x.fillStyle=g;x.fill();
 x.strokeStyle='#c9a45c';x.lineWidth=2;x.beginPath();x.moveTo(720,40);x.lineTo(720,ly+16);x.stroke();mic(x,720,40,9,'#6b5428');tag(x,'LID '+Math.round(lid*100)+'%',720,62);
 // ---- felt dial on the hammer rail
 g=x.createRadialGradient(40,154,1,44,158,16);g.addColorStop(0,'#f6e6bd');g.addColorStop(1,'#8a6a2c');x.save();x.shadowColor='#000b';x.shadowBlur=8;x.shadowOffsetY=4;x.fillStyle=g;x.beginPath();x.arc(44,158,15,0,6.283);x.fill();x.restore();const an=(-135+270*hard)*Math.PI/180;x.strokeStyle='#2a1a10';x.lineWidth=2.4;x.beginPath();x.moveTo(44+4*Math.cos(an),158+4*Math.sin(an));x.lineTo(44+12*Math.cos(an),158+12*Math.sin(an));x.stroke();tag(x,'FELT '+Math.round(hard*100)+'%',44,184);
 // ---- three pedals, right one = sustain
 [350,389,428].forEach((px,k)=>{const dy=k==2?sus*12:0;x.save();x.shadowColor='#000a';x.shadowBlur=6;x.shadowOffsetY=4;g=x.createLinearGradient(px-14,0,px+14,0);g.addColorStop(0,'#7a5e2a');g.addColorStop(.5,'#f2d58a');g.addColorStop(1,'#6b5224');x.fillStyle=g;x.beginPath();x.moveTo(px-12,196+dy);x.lineTo(px+12,196+dy);x.lineTo(px+9,181+dy);x.lineTo(px-9,181+dy);x.closePath();x.fill();x.restore()});tag(x,'PEDAL '+Math.round(sus*100)+'%',428,212);
 // ---- room mic (height) and stereo pair (spread)
 const my=30+room*110;x.strokeStyle='#ffffff40';x.lineWidth=1;x.beginPath();x.moveTo(80,Y0);x.lineTo(80,my);x.stroke();mic(x,80,my,11);tag(x,'ROOM '+Math.round(room*100)+'%',80,my+24);
 const rx=430+wid*300,lx2=778-rx;x.setLineDash([3,4]);x.strokeStyle='#ffffff40';x.beginPath();x.moveTo(lx2,118);x.lineTo(rx,118);x.stroke();x.setLineDash([]);mic(x,lx2,118,9);mic(x,rx,118,9);tag(x,'WIDTH '+Math.round(wid*100)+'%',rx,140);
 rail(x,v[8],5)}
function organ(x,W,H,v,a,t,m){const X0=m?0:-OXL,Y0=m?0:-OYL,RW=m?W:PWL,RH=m?H:PHL,dtt=Math.max(0,Math.min(.1,t-OT));OT=t;
 let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,'#3d2314');g.addColorStop(1,'#150a05');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
 x.globalAlpha=.08;x.strokeStyle='#000';for(let k=0;k<(m?8:40);k++){x.beginPath();const yy=Y0+RH*k/(m?8:40);for(let u=0;u<=RW;u+=14){const y=yy+Math.sin(u*.012+k*1.7)*4;u?x.lineTo(X0+u,y):x.moveTo(X0,y)}x.stroke()}x.globalAlpha=1;
 const cap=['#6b3d22','#6b3d22','#f2eadb','#f2eadb','#1c1a18','#f2eadb','#1c1a18','#1c1a18','#f2eadb'],nm=["16'","5⅓'","8'","4'","2⅔'","2'","1⅗'","1⅓'","1'"],up=v.slice(0,9).map(z=>Math.round(z*8)),lo=v.slice(9,18).map(z=>Math.round(z*8));
 const tgt=[0,2.2,9][Math.round(v[25]*2)];LS+=(tgt-LS)*(1-Math.exp(-dtt*(tgt>LS?.9:.5)));LP+=LS*dtt;
 if(m){for(let k=0;k<9;k++){const px=10+k*9,h=up[k]/8*38;x.fillStyle='#ffffff22';x.fillRect(px,14,2.4,46);x.fillStyle=cap[k];x.fillRect(px-1.6,14+h,5.6,6)}
  for(let k=0;k<9;k++){const px=10+k*9,h=lo[k]/8*38;x.fillStyle='#ffffff22';x.fillRect(px,68,2.4,46);x.fillStyle=cap[k];x.fillRect(px-1.6,68+h,5.6,6)}
  x.strokeStyle='#e6dcc2';x.beginPath();x.arc(W-24,40,12,LP,LP+3);x.stroke();[26,27,28].forEach((j,n)=>kg(x,28+n*40,H-20,11,v[j],a));return}
 // two drawbar manuals
 const set=(ox,vals,title,base)=>{g=x.createLinearGradient(0,0,0,200);g.addColorStop(0,'#1d1814');g.addColorStop(1,'#0e0b09');x.fillStyle=g;rr2(x,ox-16,2,238,200,10);x.fill();x.strokeStyle='#c9a45c55';x.lineWidth=1.2;x.stroke();
  x.fillStyle='#f2d58a';x.font='700 9px "Share Tech Mono",monospace';x.textAlign='left';x.fillText(title,ox-6,16);x.textAlign='right';x.fillText(`${vals[0]}${vals[1]} ${vals[2]}${vals[3]}${vals[4]}${vals[5]} ${vals[6]}${vals[7]}${vals[8]}`,ox+214,16);
  for(let k=0;k<9;k++){const px=ox+k*26,top=38,len=124,cy=top+vals[k]/8*len;x.fillStyle='#00000099';rr2(x,px-3,top-6,6,len+12,3);x.fill();
   x.fillStyle=cap[k]=='#1c1a18'?'#555':cap[k];x.fillRect(px-1.6,top,3.2,cy-top);
   x.save();x.shadowColor='#000b';x.shadowBlur=6;x.shadowOffsetY=3;g=x.createLinearGradient(px-11,0,px+11,0);g.addColorStop(0,'#0006');g.addColorStop(.45,'#fff3');g.addColorStop(1,'#0008');x.fillStyle=cap[k];rr2(x,px-11,cy-8,22,16,4);x.fill();x.fillStyle=g;x.fill();x.restore();
   x.fillStyle=cap[k]=='#f2eadb'?'#2a1a10':'#f2eadb';x.font='700 9px "Share Tech Mono",monospace';x.textAlign='center';x.fillText(vals[k],px,cy+3.2);x.fillStyle='#c9b88f';x.font='400 7px "Share Tech Mono",monospace';x.fillText(nm[k],px,top-12)}};
 set(24,up,'UPPER',0);set(278,lo,'LOWER',0);
 // vibrato / chorus selector dial (tap to turn)
 const dc=[578,112],vi=Math.round(v[22]*5),lab=['V1','V2','V3','C1','C2','C3'];
 g=x.createRadialGradient(dc[0]-8,dc[1]-8,2,dc[0],dc[1],34);g.addColorStop(0,'#5b4630');g.addColorStop(1,'#1e140c');x.save();x.shadowColor='#000b';x.shadowBlur=8;x.shadowOffsetY=4;x.fillStyle=g;x.beginPath();x.arc(dc[0],dc[1],24,0,6.283);x.fill();x.restore();
 for(let k=0;k<6;k++){const an=(-200+k*40)*Math.PI/180;x.fillStyle=k==vi?'#f2d58a':'#c9b88f';x.font='700 8px "Share Tech Mono",monospace';x.textAlign='center';x.fillText(lab[k],dc[0]+38*Math.cos(an),dc[1]+38*Math.sin(an)+3);x.fillStyle=k==vi?'#f2d58a':'#5a4a30';x.beginPath();x.arc(dc[0]+29*Math.cos(an),dc[1]+29*Math.sin(an),1.6,0,6.283);x.fill()}
 const va=(-200+vi*40)*Math.PI/180;x.strokeStyle='#f2eadb';x.lineWidth=3;x.beginPath();x.moveTo(dc[0]+5*Math.cos(va),dc[1]+5*Math.sin(va));x.lineTo(dc[0]+20*Math.cos(va),dc[1]+20*Math.sin(va));x.stroke();
 x.fillStyle='#c9b88f';x.font='700 7px "Share Tech Mono",monospace';x.textAlign='center';x.fillText('VIBRATO / CHORUS',dc[0]+8,150);
 // rotary speaker (own box, bottom right)
 const cx=764,cy=186;g=x.createLinearGradient(cx-44,0,cx+44,0);g.addColorStop(0,'#2a1a10');g.addColorStop(.5,'#3d2616');g.addColorStop(1,'#1e120a');x.fillStyle=g;rr2(x,cx-42,150,84,54,8);x.fill();x.strokeStyle='#c9a45c44';x.stroke();
 x.fillStyle='#07050399';x.beginPath();x.ellipse(cx-18,166,20,9,0,0,6.283);x.fill();const hx=Math.cos(LP*1.2)*13;x.fillStyle='#d9c08a';x.beginPath();x.ellipse(cx-18+hx,166,6+Math.abs(Math.sin(LP*1.2))*3,4,0,0,6.283);x.fill();
 x.fillStyle='#07050399';x.beginPath();x.ellipse(cx+18,180,20,18,0,0,6.283);x.fill();for(let k=0;k<6;k++){const ph2=LP*.8+k*1.05,px=cx+18+Math.sin(ph2)*15;x.globalAlpha=.25+.6*Math.max(0,Math.cos(ph2));x.fillStyle='#c9a45c';x.fillRect(px-1,166,2,28)}x.globalAlpha=1;
 rail(x,v[30],6)}
function chaikin(p,n){for(let k=0;k<n;k++){const q=[p[0]];for(let i=0;i<p.length-1;i++){const a=p[i],b=p[i+1];q.push([.75*a[0]+.25*b[0],.75*a[1]+.25*b[1]],[.25*a[0]+.75*b[0],.25*a[1]+.75*b[1]])}q.push(p[p.length-1]);p=q}return p}
function resample(p,n){const L=[0];for(let i=1;i<p.length;i++)L.push(L[i-1]+Math.hypot(p[i][0]-p[i-1][0],p[i][1]-p[i-1][1]));const T=L[L.length-1],o=[];let j=0;for(let k=0;k<n;k++){const d=T*k/(n-1);while(j<L.length-2&&L[j+1]<d)j++;const f=(d-L[j])/(L[j+1]-L[j]||1);o.push([p[j][0]+(p[j+1][0]-p[j][0])*f,p[j][1]+(p[j+1][1]-p[j][1])*f])}return o}
const SXP=resample(chaikin([[40,14],[120,22],[200,44],[310,84],[430,126],[530,164],[600,184],[660,170],[698,130],[712,86],[718,60]],3),160);
const HS={7:[.2,.32,.44,.56,.68].map((u,j)=>{const q=SXP[Math.round(u*159)];return[q[0],q[1],17,j,-1]}),8:[[360,88,24,0,1],[560,88,20,2,-1],[70,150,22,3,-1],[700,150,20,4,-1]],9:[[40,22,15,0,-1],[72,22,15,1,-1],[104,22,15,2,-1],[40,156,15,3,-1],[72,156,15,4,-1],[104,156,15,5,-1],[790,90,16,2,-1]]};
HS[8]=[[175,66,24,0,1]];HS[9]=[];HS[10]=[[410,212,36,8,7]];HS[11]=[0,1,2,3,4].map(k=>[575+k*42,60,18,3+k,-1]);HS[12]=[[230,62,16,0,-1],[266,62,16,1,-1],[302,62,16,2,-1]];HS[13]=[[400,150,26,1,0,[150,10,400,190]]];HS[14]=[];
const SS=[],GE=[0,0,0,0,0,0];GE.ev=-1;let ST=0,GT=0;
function sax(x,W,H,v,a,t,m){const kind=Math.max(0,Object.keys(FX[7].p).indexOf(P[7]))%6,X0=m?-20:-OXL,Y0=m?-30:-OYL,RW=m?800:PWL,RH=m?250:PHL,br=v[0],rd=v[1],gr=v[2],vb=v[3],vr=v[4],tone=v[5],body=v[6],sp=v[7];
 if(m){x.save();x.fillStyle='#0b0c10';x.fillRect(0,0,W,H);x.translate(4,38);x.scale(.18,.18)}
 const pal=[['#0e2229','#04090c','#e0a45a'],['#171c26','#05080c','#d9c8b0'],['#2a0e12','#080305','#ff5a4a'],['#101a3a','#05070f','#6ab0ff'],['#12261e','#060a07','#ffd36a'],['#1c1c28','#06060a','#c9a45c']][kind];
 let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,pal[0]);g.addColorStop(1,pal[1]);x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
 for(let k=0;k<5;k++){const bx=X0+RW*(.15+.18*k)+Math.sin(t*.2+k*2)*60,by=Y0+RH*(.35+.2*Math.sin(t*.15+k)),r=130+60*Math.sin(t*.3+k);g=x.createRadialGradient(bx,by,5,bx,by,r);g.addColorStop(0,pal[2]+'2a');g.addColorStop(1,'#0000');x.fillStyle=g;x.globalAlpha=.35+.65*br;x.fillRect(bx-r,by-r,2*r,2*r)}x.globalAlpha=1;
 const n=SXP.length;x.lineCap='round';
 for(let pass=0;pass<3;pass++)for(let i=1;i<n;i++){const u=i/n,w=(9+u*9+(u>.84?Math.pow((u-.84)/.16,2)*30:0))*(pass==0?1.35:pass==1?1:.25),A=SXP[i-1],B=SXP[i],off=pass==2?-2.5:0;x.strokeStyle=pass==0?'#2a1c08':pass==1?`hsl(${34+tone*10},${55+body*20}%,${28+tone*24}%)`:`rgba(255,240,200,${.3+.4*tone})`;x.lineWidth=w;x.beginPath();x.moveTo(A[0]+off,A[1]+off);x.lineTo(B[0]+off,B[1]+off);x.stroke()}
 const E=SXP[n-1],E0=SXP[n-6],ang=Math.atan2(E[1]-E0[1],E[0]-E0[0]);x.save();x.translate(E[0],E[1]);x.rotate(ang);g=x.createRadialGradient(0,0,2,0,0,30);g.addColorStop(0,`rgba(255,226,170,${.5+.5*br})`);g.addColorStop(1,'#1a0f04');x.fillStyle=g;x.beginPath();x.ellipse(0,0,10,30,0,0,6.283);x.fill();x.restore();
 const dt=Math.max(0,Math.min(.1,t-ST));ST=t;if(!SS.length)for(let k=0;k<90;k++)SS.push({u:Math.random(),v:.6+Math.random()*.8,o:Math.random()*6.283});
 for(const p of (LITE?SS.slice(0,40):SS)){p.u+=dt*(.1+.9*br)*p.v;if(p.u>1)p.u-=1;const i=Math.min(n-2,Math.floor(p.u*n)),q=SXP[i],q2=SXP[i+1],nx=-(q2[1]-q[1]),ny=q2[0]-q[0],nl=Math.hypot(nx,ny)||1,off=Math.sin(p.u*22-t*(4+vr*14))*(3+vb*13)+(Math.random()-.5)*gr*12;x.globalAlpha=Math.sin(p.u*Math.PI)*(.35+.55*br);x.fillStyle=gr>.5&&p.o>3?'#ff7a5a':'#fff3dc';x.beginPath();x.arc(q[0]+nx/nl*off,q[1]+ny/nl*off,1.2+br*1.6,0,6.283);x.fill()}x.globalAlpha=1;
 x.strokeStyle='#f0dcae';x.lineWidth=2;x.beginPath();const RA=SXP[0];for(let k=0;k<=8;k++){const ux=RA[0]-22+k*4,uy=RA[1]-8+Math.sin(t*(30+rd*40)+k*.5)*rd*5*(k/8);k?x.lineTo(ux,uy):x.moveTo(ux,uy)}x.stroke();
 if(sp>.05){const u=(t*.35)%1;x.globalAlpha=(1-u)*sp*.5;x.strokeStyle=pal[2];x.lineWidth=2;x.beginPath();x.arc(E[0],E[1],30+u*150,0,6.283);x.stroke();x.globalAlpha=1}
 if(m){x.restore();[0,2,5].forEach((j,k)=>kg(x,28+k*40,H-20,11,v[j],a));return}
 const nm=['BREATH','REED','GROWL','VIBRATO','RATE'];HS[7].forEach((h,j)=>{const val=v[j];x.save();x.shadowColor='#000b';x.shadowBlur=8;x.shadowOffsetY=3;g=x.createRadialGradient(h[0]-4,h[1]-4,1,h[0],h[1],h[2]);g.addColorStop(0,'#fff');g.addColorStop(.6,'#e9e0d4');g.addColorStop(1,'#9d8f7a');x.fillStyle=g;x.beginPath();x.arc(h[0],h[1],h[2]-4,0,6.283);x.fill();x.restore();x.strokeStyle=pal[2];x.lineWidth=2.4;x.beginPath();x.arc(h[0],h[1],h[2],-2.356,-2.356+4.712*val);x.stroke();tag(x,nm[j]+' '+Math.round(val*100)+'%',h[0],h[1]+h[2]+13)});
 rail(x,v[8],7)}
const PD=[];let SOL=0,STT=0;
function strings(x,W,H,v,a,t,m){const pr=v[0],spd=v[1],mot=v[2],mor=v[3],tim=v[4],hue=v[5]*360,tone=v[6],bd=v[7],sp=v[8];
 wrapMini(x,m,()=>{const X0=-OXL,Y0=-OYL,RW=PWL,RH=PHL,dt=Math.max(0,Math.min(.1,t-STT));STT=t;
  const act=(Math.sin(t*.9)+Math.sin(t*.37+1))>-.5;SOL+=((act?.3+.7*pr:0)-SOL)*(1-Math.exp(-dt*(.6+(1-tim)*3.4)));
  const hs=(o,s2=85,l=62,al=1)=>`hsla(${(hue+o)%360},${s2}%,${l}%,${al})`;
  let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,hs(0,45,13));g.addColorStop(1,hs(20,50,3));x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  [[.2,.3,0],[.75,.6,90],[.5,.1,200]].forEach(([u,w,o],k)=>{const bx=X0+RW*u+Math.sin(t*.2+k)*40,by=Y0+RH*w;g=x.createRadialGradient(bx,by,5,bx,by,220);g.addColorStop(0,hs(o,80,50,.22));g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(bx-220,by-220,440,440)});
  const vp=()=>{x.beginPath();x.moveTo(228,66);x.ellipse(150,66,78,52,0,0,6.283);x.moveTo(348,66);x.ellipse(292,66,56,40,0,0,6.283);x.rect(205,34,50,64)};
  const nc=1+Math.round(mor*5);
  for(let k=nc-1;k>=1;k--){x.save();x.translate(210,66);x.scale(1-.05*k,1-.05*k);x.translate(-210-16*k,-66+(k%2?1:-1)*12*k);vp();x.strokeStyle=hs(40*k,80,62,.5/k);x.lineWidth=1;x.stroke();x.restore()}
  for(let k=0;k<5;k++){x.save();x.translate(210,66);x.scale(1-.1*k,1-.1*k);x.translate(-210,-66);vp();x.strokeStyle=hs(k*8,85,65,(.55-.08*k)*(.5+.5*(1-SOL)));x.lineWidth=.9;x.stroke();x.restore()}
  x.save();vp();x.clip();x.globalAlpha=SOL;g=x.createLinearGradient(70,0,350,0);g.addColorStop(0,hs(10,70,28+tone*10));g.addColorStop(1,hs(30,75,18+bd*8));x.fillStyle=g;x.fillRect(60,10,300,116);g=x.createLinearGradient(0,14,0,118);g.addColorStop(0,'#fff3');g.addColorStop(.4,'#fff0');g.addColorStop(1,'#0006');x.fillStyle=g;x.fillRect(60,10,300,116);x.globalAlpha=1;
  const A=2+tone*4,B=3+bd*4+Math.sin(t*.2)*.3,thr=.16+.08*Math.sin(t*1.7)*pr;x.fillStyle=hs(50,60,88,.35+.45*SOL);
  for(let i=0;i<40;i++)for(let j=0;j<24;j++){const u=i/39,w=j/23,f=Math.cos(A*Math.PI*u)*Math.cos(B*Math.PI*w)-Math.cos(B*Math.PI*u)*Math.cos(A*Math.PI*w);if(Math.abs(f)<thr)x.fillRect(72+u*274+Math.sin(i*7+j*3+t*8)*pr*1.2,14+w*104+Math.cos(i*5+j*9+t*7)*pr*1.2,1.5,1.5)}
  x.restore();
  // particles: the body dissolves into them, and they gather back when the bow plays
  PD.acc=(PD.acc||0)+(30+260*(1-SOL))*(.5+.5*(mot+sp)/1.5)*dt;
  while(PD.acc>=1&&PD.length<(LITE?160:480)){PD.acc--;const px=72+Math.random()*274,py=14+Math.random()*104;if(Math.pow((px-150)/78,2)+Math.pow((py-66)/52,2)<1||Math.pow((px-292)/56,2)+Math.pow((py-66)/40,2)<1||(px>205&&px<255&&py>34&&py<98))PD.push({x:px,y:py,ox:px,oy:py,vx:(Math.random()-.3)*14,vy:-4-Math.random()*16,l:0,L:.8+sp*3+Math.random()*1.2,h:Math.random()*60,s:1+Math.random()*2})}
  for(let i=PD.length-1;i>=0;i--){const p=PD[i];p.l+=dt;if(p.l>p.L){PD[i]=PD[PD.length-1];PD.pop();continue}const kk=SOL>.5?3*SOL:0;p.vx+=((p.ox-p.x)*kk*2+Math.sin(t*3+p.y*.1)*mot*30)*dt;p.vy+=((p.oy-p.y)*kk*2)*dt;p.vx*=.985;p.vy*=.985;p.x+=p.vx*dt;p.y+=p.vy*dt;x.globalAlpha=(1-p.l/p.L)*.9;x.fillStyle=hs(p.h,85,70);x.beginPath();x.arc(p.x,p.y,p.s*(1-p.l/p.L*.4),0,6.283);x.fill()}x.globalAlpha=1;
  // neck, scroll, strings, bridge, f-holes
  g=x.createLinearGradient(0,56,0,76);g.addColorStop(0,'#1a1210');g.addColorStop(1,'#0a0706');x.fillStyle=g;x.fillRect(340,56,206,20);x.strokeStyle=hs(20,60,70,.6);x.lineWidth=1.2;x.beginPath();x.arc(570,66,13,0,6.283);x.stroke();x.beginPath();x.arc(570,66,7,.5,5.2);x.stroke();x.beginPath();x.arc(570,66,3,0,4);x.stroke();x.fillStyle=hs(20,40,60,.8);[[540,50],[552,82]].forEach(([qx,qy])=>{x.beginPath();x.ellipse(qx,qy,5,3,0,0,6.283);x.fill()});
  x.strokeStyle=hs(30,80,60,.7);x.lineWidth=1.6;x.beginPath();x.moveTo(190,48);x.quadraticCurveTo(178,66,190,84);x.moveTo(236,48);x.quadraticCurveTo(248,66,236,84);x.stroke();
  const bx2=175+Math.sin(t*(.6+spd*3))*(20+spd*35);
  for(let k=0;k<4;k++){const y0=58.5+k*5,vy=Math.sin(t*(80+k*20))*(.5+pr*2)*(k==1?1:.6)*(act?1:.2);x.strokeStyle=hs(40,60,80,.9);x.lineWidth=.8+(3-k)*.2;x.beginPath();x.moveTo(84,y0+(k-1.5)*.6);x.lineTo(215,y0+vy);x.lineTo(545,58.5+k*5+vy*.4);x.stroke()}
  x.fillStyle=hs(10,40,40);x.beginPath();x.moveTo(209,52);x.lineTo(221,52);x.lineTo(224,80);x.lineTo(206,80);x.closePath();x.fill();
  // bow (hot-spot): pressure up/down, speed left/right
  for(let s2=0;s2<2;s2++){}
  x.save();x.translate(bx2,66);x.rotate(.12);x.shadowColor=hs(0,90,65,.9);x.shadowBlur=8+12*pr;x.fillStyle=hs(30,30,92,.95);x.fillRect(-1.5-pr*2,-62,3+pr*4,128);x.restore();
  if(sp>.05){const u=(t*.3)%1;x.globalAlpha=(1-u)*sp*.4;x.strokeStyle=hs(0,80,65);x.lineWidth=2;x.beginPath();x.ellipse(250,66,100+u*420,60+u*130,0,0,6.283);x.stroke();x.globalAlpha=1}
  HS[8][0][0]=bx2;
  if(!m){x.fillStyle=hs(20,40,80,.8);x.font='400 8px "Share Tech Mono",monospace';x.textAlign='center';x.fillText('BOW '+Math.round(pr*100)+' · '+Math.round(spd*100),bx2,140)}});
 if(m){[3,5,6].forEach((j,k)=>kg(x,28+k*40,H-20,11,v[j],a));return}
 rail(x,v[9],8)}
const GCH=[[-1,0,2,2,1,0],[3,2,0,0,0,3],[-1,3,2,0,1,0],[1,3,3,2,1,1]],FW=[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15].map(k=>110+759*(1-Math.pow(2,-k/12)));
function guitar(x,W,H,v,a,t,m){const bt=Math.round(v[0]*3),pick=v[1],str=v[2],mute=v[3],bend=v[4],pu=v[5],fin=Math.round(v[6]*3),tone=v[7],res=v[8],sp=v[9],elec=bt>=2,bx=elec?676:708;
 wrapMini(x,m,()=>{const X0=-OXL,Y0=-OYL,RW=PWL,RH=PHL;
  const pal=[['#241810','#07060a','#e0a060'],['#2a2418','#0a0906','#e8d0a0'],['#141a26','#05070c','#6ab0ff'],['#241010','#080304','#ff6a4a']][bt];
  let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,pal[0]);g.addColorStop(1,pal[1]);x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  g=x.createRadialGradient(560,90,10,560,90,420);g.addColorStop(0,pal[2]+'2a');g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  const FIN=elec?[['#d89a40','#6a2a10'],['#f0eadc','#bdb4a0'],['#26262c','#0c0c10'],['#b02a2a','#5a1010']][fin]:[['#e8c488','#b88a48'],['#d8943c','#5a2a10'],['#3a2e26','#14100c'],['#c88838','#7a4a18']][fin];
  const bodyPath=()=>{x.beginPath();if(!elec)x.ellipse(716,92,178,128,0,0,6.283);else if(bt==2){x.ellipse(716,92,168,112,0,0,6.283);x.moveTo(646,-8);x.arc(616,-8,38,0,6.283);x.moveTo(654,190);x.arc(616,190,38,0,6.283)}else{x.ellipse(716,96,170,112,0,0,6.283);x.moveTo(660,196);x.arc(620,196,44,0,6.283)}};
  x.save();bodyPath();g=x.createRadialGradient(700,70,10,716,90,200);g.addColorStop(0,FIN[0]);g.addColorStop(1,FIN[1]);x.fillStyle=g;x.fill();x.strokeStyle=elec?'#0008':'#f2e6c8';x.lineWidth=3;x.stroke();x.clip();
  x.globalAlpha=.1;x.strokeStyle='#000';x.lineWidth=1;for(let k=0;k<30;k++){x.beginPath();for(let u=520;u<=800;u+=14){const y=-30+k*8+Math.sin(u*.03+k)*3;u==520?x.moveTo(u,y):x.lineTo(u,y)}x.stroke()}x.globalAlpha=1;
  if(!elec){const r0=bt==1?40:36;g=x.createRadialGradient(650,92,2,650,92,r0);g.addColorStop(0,'#000');g.addColorStop(1,'#1a0e06');x.fillStyle=g;x.beginPath();x.arc(650,92,r0,0,6.283);x.fill();[0,1,2].forEach(k=>{x.strokeStyle=k==1?'#1a1208':'#f2e6c8';x.lineWidth=k==1?2:1.2;x.beginPath();x.arc(650,92,r0+3+k*3,0,6.283);x.stroke()});if(res>.05){g=x.createRadialGradient(650,92,r0,650,92,r0+60);g.addColorStop(0,`rgba(255,180,90,${.35*res})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.beginPath();x.arc(650,92,r0+60,0,6.283);x.fill()}
   x.fillStyle='#1a1008';rr2(x,bt==1?694:698,64,bt==1?30:18,56,3);x.fill();x.fillStyle='#efe6d0';x.fillRect(bt==1?700:702,66,2,52);[0,1,2,3,4,5].forEach(k=>{x.fillStyle='#d8d0c0';x.beginPath();x.arc(bt==1?712:710,68+k*9.5,1.6,0,6.283);x.fill()})}
  else if(bt==2){x.fillStyle='#efe8d8';x.globalAlpha=.92;rr2(x,576,16,134,150,26);x.fill();x.globalAlpha=1;[596,632,668].forEach((px,k)=>{x.fillStyle='#f4f0e6';rr2(x,px-7,58,14,66,3);x.fill();x.strokeStyle='#0003';x.lineWidth=1;x.stroke();for(let q=0;q<6;q++){x.fillStyle='#3a3a40';x.beginPath();x.arc(px,66+q*9,2.2,0,6.283);x.fill()}const on=Math.abs(pu*2-k)<.75;if(on){x.strokeStyle=pal[2];x.lineWidth=2;x.shadowColor=pal[2];x.shadowBlur=10;rr2(x,px-9,56,18,70,4);x.stroke();x.shadowBlur=0}});x.fillStyle='#c0c4cc';rr2(x,694,62,10,60,2);x.fill();[0,1,2].forEach(k=>{x.fillStyle='#b8bcc4';x.beginPath();x.arc(654+k*26,160,8,0,6.283);x.fill();x.strokeStyle='#0008';x.stroke()})}
  else{[[594,0],[640,1]].forEach(([px,k])=>{g=x.createLinearGradient(px-22,0,px+22,0);g.addColorStop(0,'#e8eaee');g.addColorStop(.5,'#9aa0aa');g.addColorStop(1,'#d4d8de');x.fillStyle=g;rr2(x,px-22,58,44,68,5);x.fill();x.strokeStyle='#0008';x.lineWidth=1;x.stroke();for(let q=0;q<6;q++){x.fillStyle='#2a2a30';x.beginPath();x.arc(px-8,68+q*10,2,0,6.283);x.arc(px+8,68+q*10,2,0,6.283);x.fill()}if(Math.abs(pu-k)<.55||pu==.5){x.strokeStyle=pal[2];x.lineWidth=2;x.shadowColor=pal[2];x.shadowBlur=10;rr2(x,px-24,56,48,72,6);x.stroke();x.shadowBlur=0}});x.fillStyle='#c0c4cc';rr2(x,672,62,8,60,2);x.fill();rr2(x,706,62,10,60,2);x.fill();[[654,150],[684,150],[654,176],[684,176]].forEach(([qx,qy])=>{x.fillStyle='#d8b050';x.beginPath();x.arc(qx,qy,8,0,6.283);x.fill();x.strokeStyle='#0008';x.stroke()})}
  x.restore();
  // ---- straight neck: rosewood fretboard with grain, binding, fret wires, pearl inlays
  const fb0=64,fb1=118,fe=580;g=x.createLinearGradient(0,fb0,0,fb1);g.addColorStop(0,`hsl(18,42%,${15+tone*9}%)`);g.addColorStop(1,`hsl(14,46%,${9+tone*7}%)`);x.save();x.shadowColor='#000b';x.shadowBlur=14;x.shadowOffsetY=6;x.fillStyle=g;x.fillRect(104,fb0,fe-104,fb1-fb0);x.restore();
  x.save();x.beginPath();x.rect(104,fb0,fe-104,fb1-fb0);x.clip();for(let k=0;k<26;k++){const yy=fb0+1+k*2.1;x.globalAlpha=.1+.06*(k%3);x.strokeStyle=k%2?'#000':'#b86a40';x.lineWidth=.7;x.beginPath();for(let u=104;u<=fe;u+=18){const y2=yy+Math.sin(u*.021+k*1.3)*.9;u==104?x.moveTo(u,y2):x.lineTo(u,y2)}x.stroke()}x.globalAlpha=1;
  g=x.createLinearGradient(0,fb0,0,fb1);g.addColorStop(0,'#fff2');g.addColorStop(.3,'#fff0');g.addColorStop(1,'#0006');x.fillStyle=g;x.fillRect(104,fb0,fe-104,fb1-fb0);x.restore();
  x.strokeStyle='#eadfc2';x.lineWidth=1.6;x.beginPath();x.moveTo(104,fb0+.8);x.lineTo(fe,fb0+.8);x.moveTo(104,fb1-.8);x.lineTo(fe,fb1-.8);x.stroke();
  for(let k=1;k<=15;k++){const fx=FW[k];if(fx>fe-4)break;x.strokeStyle='#0008';x.lineWidth=3;x.beginPath();x.moveTo(fx+1.4,fb0+1);x.lineTo(fx+1.4,fb1-1);x.stroke();x.strokeStyle='#d9d6cc';x.lineWidth=2;x.beginPath();x.moveTo(fx,fb0+1);x.lineTo(fx,fb1-1);x.stroke();x.strokeStyle='#fff';x.globalAlpha=.7;x.lineWidth=.8;x.beginPath();x.moveTo(fx-.5,fb0+1);x.lineTo(fx-.5,fb1-1);x.stroke();x.globalAlpha=1;x.fillStyle='#eadfc2';x.beginPath();x.arc(fx,fb0,1.4,0,6.283);x.arc(fx,fb1,1.4,0,6.283);x.fill()}
  [3,5,7,9,15].forEach(k=>{const cx=(FW[k-1]+FW[k])/2;g=x.createRadialGradient(cx-1,89,.5,cx,91,4);g.addColorStop(0,'#fff');g.addColorStop(1,'#b8b0a0');x.fillStyle=g;x.beginPath();x.arc(cx,91,3.6,0,6.283);x.fill();x.fillStyle='#e6dfd0';x.beginPath();x.arc(cx,fb0-3.2,1.2,0,6.283);x.fill()});
  {const cx=(FW[11]+FW[12])/2;[80,102].forEach(yy=>{g=x.createRadialGradient(cx-1,yy-1,.5,cx,yy,4);g.addColorStop(0,'#fff');g.addColorStop(1,'#b8b0a0');x.fillStyle=g;x.beginPath();x.arc(cx,yy,3.5,0,6.283);x.fill()});x.fillStyle='#e6dfd0';x.beginPath();x.arc(cx,fb0-3.2,1.2,0,6.283);x.fill()}
  x.fillStyle='#efe6d0';rr2(x,102,fb0-2,7,fb1-fb0+4,1.5);x.fill();x.fillStyle='#0003';x.fillRect(107.6,fb0-2,1.4,fb1-fb0+4);
  // ---- headstock + tuners
  x.save();x.shadowColor='#000a';x.shadowBlur=10;x.shadowOffsetY=4;g=x.createLinearGradient(0,40,0,140);g.addColorStop(0,'#2a1c14');g.addColorStop(1,'#120c08');x.fillStyle=g;x.beginPath();if(elec){x.moveTo(6,56);x.lineTo(104,63);x.lineTo(104,119);x.lineTo(6,126);x.quadraticCurveTo(0,91,6,56)}else{x.moveTo(10,46);x.lineTo(104,60);x.lineTo(104,122);x.lineTo(10,136);x.quadraticCurveTo(0,91,10,46)}x.fill();x.restore();
  x.strokeStyle='#ffffff22';x.lineWidth=1;x.beginPath();x.moveTo(12,91);x.lineTo(100,91);x.stroke();
  const pegs=[[26,48],[58,52],[90,57],[26,134],[58,130],[90,125]];
  HS[9]=pegs.map((q,i)=>[q[0],q[1],14,i+1,-1]);
  // ---- strings with chord, fingers, pluck energy
  const ci=Math.floor(t/1.7)%4,sh=GCH[ci],ph2=t%1.7,dt=Math.max(0,Math.min(.1,t-GT));GT=t;
  for(let s2=0;s2<6;s2++){const delay=s2*(.02+str*.09),played=sh[s2]>=0;if(played&&ph2>delay&&ph2-dt<=delay)GE[s2]=1;GE[s2]*=Math.exp(-dt*(1.4+mute*8));
   const ys=69.5+s2*8,Am=(2+pick*5)*GE[s2]*(1-.6*mute),nylon=bt==1,wound=s2<3;x.strokeStyle=wound?(nylon?'#d8c890':'#c9a86a'):(nylon?'#eef0ea':'#d4d8de');x.lineWidth=[2.6,2.1,1.6,1.2,.95,.75][s2];x.beginPath();for(let i=0;i<=30;i++){const u=i/30,xx=110+u*(bx-110);let yy=ys+Math.sin(Math.PI*u)*Am*Math.sin(t*(60+s2*14));if(s2==3)yy-=Math.sin(Math.PI*u)*bend*12;i?x.lineTo(xx,yy):x.moveTo(xx,yy)}x.stroke();
   if(wound){x.save();x.globalAlpha=.35;x.setLineDash([1.5,1.5]);x.strokeStyle='#fff';x.lineWidth=.6;x.beginPath();x.moveTo(110,ys-.4);x.lineTo(bx,ys-.4);x.stroke();x.restore()}
   const f=sh[s2];if(f>0&&FW[f]<fe){const fx=(FW[f-1]+FW[f])/2;g=x.createRadialGradient(fx,ys,1,fx,ys,8);g.addColorStop(0,'#fff');g.addColorStop(.35,pal[2]);g.addColorStop(1,pal[2]+'00');x.fillStyle=g;x.beginPath();x.arc(fx,ys,8,0,6.283);x.fill()}else if(f==0){x.strokeStyle=pal[2];x.lineWidth=1.2;x.beginPath();x.arc(97,ys,3,0,6.283);x.stroke()}else{x.strokeStyle='#ffffff55';x.lineWidth=1.2;x.beginPath();x.moveTo(94,ys-3);x.lineTo(100,ys+3);x.moveTo(100,ys-3);x.lineTo(94,ys+3);x.stroke()}
   x.strokeStyle='#cfd2d8';x.lineWidth=.8;x.beginPath();const tp=pegs[s2];x.moveTo(110,ys);x.lineTo(tp[0],tp[1]+(tp[1]<91?6:-6));x.stroke()}
  if(ph2<.25)for(let k=0;k<7;k++){x.globalAlpha=(1-ph2*4)*.9;x.fillStyle=pal[2];x.beginPath();x.arc(bx-70+Math.cos(k*1.7)*ph2*60,66+k*8+Math.sin(k*2.3)*ph2*26,1.5,0,6.283);x.fill()}x.globalAlpha=1;
  if(mute>.05){x.fillStyle=`rgba(10,8,8,${.7*Math.min(1,mute*2)})`;rr2(x,bx-34,60,34,62,6);x.fill()}
  if(sp>.05){const u=(t*.3)%1;x.globalAlpha=(1-u)*sp*.4;x.strokeStyle=pal[2];x.lineWidth=2;x.beginPath();x.arc(700,92,60+u*330,0,6.283);x.stroke();x.globalAlpha=1}
  if(!m){const nm=['PICK','STRUM','MUTE','BEND',elec?'PICKUP':'MIC','FINISH'];pegs.forEach((q,i)=>{const val=v[i+1],an=-2.2+val*4.4;x.save();x.shadowColor='#000b';x.shadowBlur=7;x.shadowOffsetY=3;g=x.createRadialGradient(q[0]-3,q[1]-3,1,q[0],q[1],12);g.addColorStop(0,'#f4efe6');g.addColorStop(1,'#7a7064');x.fillStyle=g;x.beginPath();x.arc(q[0],q[1],10,0,6.283);x.fill();x.restore();x.strokeStyle='#1a120a';x.lineWidth=2.4;x.beginPath();x.moveTo(q[0]+2*Math.cos(an),q[1]+2*Math.sin(an));x.lineTo(q[0]+8*Math.cos(an),q[1]+8*Math.sin(an));x.stroke();tag(x,nm[i],q[0],q[1]+(q[1]<91?-15:24));tag(x,Math.round(val*100)+'%',q[0],q[1]+(q[1]<91?-6:33))})}});
 if(m){[1,2,7].forEach((j,k)=>kg(x,28+k*40,H-20,11,v[j],a));return}
 rail(x,v[10],9)}
function wrapMini(x,m,fn){if(m){x.save();x.fillStyle='#0b0b0f';x.fillRect(0,0,136,160);x.translate(3,46);x.scale(.165,.165)}fn();if(m)x.restore()}
let BT=0,BML=-1,BMT=0,SYPG=0;const BS=[];
const BXP=resample(chaikin([[40,34],[180,34],[180,112],[360,112],[360,34],[500,34],[500,112],[560,112],[640,100],[700,80]],3),150);
function amp(x,W,H,v,a,t,m){const gain=v[0],ch=Math.round(v[6]*2),boost=v[9]>.5,lvl=v[11],mpos=v[7],mdist=v[8];
 wrapMini(x,m,()=>{const X0=-OXL,Y0=-OYL,RW=PWL,RH=PHL;let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,'#1a1410');g.addColorStop(1,'#07050a');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  const heat=Math.min(1,gain*(.55+.3*ch)+(boost?.2:0));g=x.createRadialGradient(400,60,10,400,60,420);g.addColorStop(0,`rgba(255,140,50,${.1+.3*heat})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  g=x.createLinearGradient(0,4,0,126);g.addColorStop(0,'#2a2622');g.addColorStop(.5,'#16130f');g.addColorStop(1,'#0c0a08');x.fillStyle=g;rr2(x,24,36,764,92,12);x.fill();x.strokeStyle='#c9a45c';x.lineWidth=1.5;x.stroke();x.globalAlpha=.06;x.strokeStyle='#fff';for(let k=0;k<22;k++){x.beginPath();x.moveTo(30,40+k*4);x.lineTo(780,40+k*4);x.stroke()}x.globalAlpha=1;
  [560,612,664].forEach((tx,k)=>{const f=.35+.65*heat*(.85+.15*Math.sin(t*9+k*2));g=x.createRadialGradient(tx,86,2,tx,86,26);g.addColorStop(0,`rgba(255,${180-60*heat|0},70,${f})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.beginPath();x.arc(tx,86,26,0,6.283);x.fill();x.fillStyle='#0a0806';rr2(x,tx-11,52,22,56,11);x.fill();x.strokeStyle='#ffffff30';x.lineWidth=1;x.stroke();x.fillStyle=`rgba(255,200,120,${.4+.6*f})`;rr2(x,tx-2.5,64,5,36,2.5);x.fill()});
  const cx=410,cy=212;g=x.createLinearGradient(0,138,0,284);g.addColorStop(0,'#2a1d12');g.addColorStop(1,'#120b06');x.fillStyle=g;rr2(x,170,138,480,150,10);x.fill();x.strokeStyle='#c9a45c88';x.lineWidth=2;x.stroke();
  x.save();rr2(x,182,148,456,130,8);x.clip();x.fillStyle='#0c0907';x.fillRect(170,138,480,150);x.globalAlpha=.25;x.strokeStyle='#8a7a5a';x.lineWidth=1;for(let k=0;k<70;k++){x.beginPath();x.moveTo(170+k*7,138);x.lineTo(170+k*7,288);x.stroke()}for(let k=0;k<22;k++){x.beginPath();x.moveTo(170,138+k*7);x.lineTo(650,138+k*7);x.stroke()}x.globalAlpha=1;
  const pump=1+.04*Math.sin(t*50)*(.3+lvl*.7)*(.4+heat);x.fillStyle='#0a0a0c';x.beginPath();x.arc(cx,cy,66,0,6.283);x.fill();x.strokeStyle='#44444a';x.lineWidth=5;x.beginPath();x.arc(cx,cy,60,0,6.283);x.stroke();g=x.createRadialGradient(cx,cy,4,cx,cy,50*pump);g.addColorStop(0,'#3a3a40');g.addColorStop(.35,'#1c1c20');g.addColorStop(1,'#060608');x.fillStyle=g;x.beginPath();x.arc(cx,cy,50*pump,0,6.283);x.fill();x.fillStyle='#16161a';x.beginPath();x.arc(cx,cy,13*pump,0,6.283);x.fill();
  for(let k=0;k<3;k++){const u=(t*.8+k/3)%1;x.globalAlpha=(1-u)*.25*(.3+heat);x.strokeStyle='#ff9a3c';x.lineWidth=1.5;x.beginPath();x.arc(cx,cy,18+u*34,0,6.283);x.stroke()}x.globalAlpha=1;x.restore();
  const mx=340+mpos*140,my=150+mdist*90;x.strokeStyle='#ffffff40';x.lineWidth=1.2;x.beginPath();x.moveTo(mx,my);x.quadraticCurveTo(mx+60,my-30,720,170);x.stroke();mic(x,mx,my,10);HS[10][0][0]=mx;HS[10][0][1]=my;
  if(!m){tag(x,'MIC '+Math.round(mpos*100)+' · '+Math.round(mdist*100),mx,my+24);}});
 if(m)[0,1,5].forEach((j,k)=>kg(x,28+k*40,H-20,11,v[j],a))}
function bass(x,W,H,v,a,t,m){const dr=v[0],cp=v[1],cab=Math.round(v[8]*2),lvl=v[9];
 wrapMini(x,m,()=>{const X0=-OXL,Y0=-OYL,RW=PWL,RH=PHL;let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,'#1c1a14');g.addColorStop(1,'#07060a');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  const sig=(.45+.25*Math.sin(t*3.1)*Math.sin(t*.9+1)+.15*Math.sin(t*7.3))*(.6+dr*.6)*(.5+lvl*.7);
  g=x.createLinearGradient(0,4,0,124);g.addColorStop(0,'#2c2820');g.addColorStop(1,'#0e0c09');x.fillStyle=g;rr2(x,14,4,776,120,12);x.fill();x.strokeStyle='#f0c06088';x.lineWidth=1.5;x.stroke();
  const vx=122,vy=76;g=x.createLinearGradient(0,14,0,114);g.addColorStop(0,'#efe3c0');g.addColorStop(1,'#c9b88a');x.fillStyle=g;rr2(x,34,14,176,100,10);x.fill();x.strokeStyle='#3a2a10';x.lineWidth=2;x.stroke();
  for(let k=0;k<=10;k++){const an=Math.PI*(1.2+.6*k/10),r1=62,r2=k%5?70:76;x.strokeStyle=k>7?'#c0392b':'#2a1a08';x.lineWidth=k%5?1:2;x.beginPath();x.moveTo(vx+r1*Math.cos(an),vy+34+r1*Math.sin(an));x.lineTo(vx+r2*Math.cos(an),vy+34+r2*Math.sin(an));x.stroke()}
  const na=Math.PI*(1.2+.6*Math.min(1,Math.max(0,sig)));x.strokeStyle='#1a0e04';x.lineWidth=2.2;x.beginPath();x.moveTo(vx,vy+34);x.lineTo(vx+66*Math.cos(na),vy+34+66*Math.sin(na));x.stroke();x.fillStyle='#1a0e04';x.beginPath();x.arc(vx,vy+34,5,0,6.283);x.fill();
  for(let k=0;k<8;k++){const on=k<Math.round(cp*sig*9),col=k>5?'#ff5040':k>3?'#ffb040':'#60e070';x.fillStyle=on?col:'#2a2a2a';x.beginPath();x.arc(244,24+k*13,4.5,0,6.283);x.fill();if(on){x.globalAlpha=.4;x.fillStyle=col;x.beginPath();x.arc(244,24+k*13,9,0,6.283);x.fill();x.globalAlpha=1}}
  HS[11].forEach((h,k)=>{const sx=575+k*42,val=v[3+k],ty=104-val*80;h[0]=sx;h[1]=ty;x.fillStyle='#000a';rr2(x,sx-3,22,6,84,3);x.fill();g=x.createLinearGradient(0,22,0,106);g.addColorStop(0,'#ffd070');g.addColorStop(1,'#7a5a20');x.fillStyle=g;x.fillRect(sx-1.5,ty,3,106-ty);g=x.createLinearGradient(sx-9,0,sx+9,0);g.addColorStop(0,'#6a6258');g.addColorStop(.5,'#f0e8da');g.addColorStop(1,'#5a5248');x.fillStyle=g;rr2(x,sx-9,ty-6,18,12,3);x.fill();x.fillStyle='#2a1a08';x.fillRect(sx-5,ty-.8,10,1.6);if(!m){x.fillStyle='#c9b88f';x.font='400 8px "Share Tech Mono",monospace';x.textAlign='center';x.fillText(['63','250','1k','3k','8k'][k],sx,119)}});
  const cols=cab==0?[[410,212,62]]:cab==1?[[366,176,30],[454,176,30],[366,246,30],[454,246,30]]:[...[0,1,2,3].map(k=>[338+k*48,176,19]),...[0,1,2,3].map(k=>[338+k*48,246,19])];
  g=x.createLinearGradient(0,136,0,286);g.addColorStop(0,'#1e1a14');g.addColorStop(1,'#0a0806');x.fillStyle=g;rr2(x,cab==2?296:cab==1?316:324,136,cab==2?228:cab==1?188:172,148,10);x.fill();x.strokeStyle='#f0c06066';x.lineWidth=2;x.stroke();
  cols.forEach(([cx,cy,r],k)=>{const pump=1+.07*Math.sin(t*14+k*.3)*(.3+sig*.9);g=x.createRadialGradient(cx,cy,2,cx,cy,r*pump);g.addColorStop(0,'#3a3a3e');g.addColorStop(.4,'#1a1a1e');g.addColorStop(1,'#050506');x.fillStyle=g;x.beginPath();x.arc(cx,cy,r*pump,0,6.283);x.fill();x.strokeStyle='#666';x.lineWidth=1.6;x.beginPath();x.arc(cx,cy,r+2,0,6.283);x.stroke();x.fillStyle='#111';x.beginPath();x.arc(cx,cy,r*.24*pump,0,6.283);x.fill()});
  if(!m){x.fillStyle='#3a2a10';x.font='700 8px "Share Tech Mono",monospace';x.textAlign='center';x.fillText('VU',vx,32)}});
 if(m)[0,1,2].forEach((j,k)=>kg(x,28+k*40,H-20,11,v[j],a))}
function brass(x,W,H,v,a,t,m){const lips=v[0],bite=v[1],vib=v[2],sec=Math.round(v[3]*4)+1,mute=Math.round(v[4]*4),tone=v[5],sp=v[7];
 wrapMini(x,m,()=>{const X0=-OXL,Y0=-OYL,RW=PWL,RH=PHL;let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,'#2e0c18');g.addColorStop(1,'#08040a');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  g=x.createRadialGradient(700,80,10,700,80,400);g.addColorStop(0,`rgba(255,210,120,${.12+.25*bite})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  const n=BXP.length;x.lineCap='round';
  const tube=(dx,dy,sc,al)=>{x.save();x.globalAlpha=al;x.translate(dx,dy);x.scale(sc,sc);for(let pass=0;pass<3;pass++)for(let i=1;i<n;i++){const u=i/n,w=(8+(u>.8?Math.pow((u-.8)/.2,2)*44:0))*(pass==0?1.4:pass==1?1:.25),A=BXP[i-1],B=BXP[i],off=pass==2?-2:0;x.strokeStyle=pass==0?'#2a1c08':pass==1?`hsl(${42+tone*8},70%,${30+tone*22}%)`:`rgba(255,244,210,${.35+.35*tone})`;x.lineWidth=w;x.beginPath();x.moveTo(A[0]+off,A[1]+off);x.lineTo(B[0]+off,B[1]+off);x.stroke()}x.restore()};
  for(let s2=sec-1;s2>=1;s2--)tube(-20*s2,-14*s2+20,1-.06*s2,.18);tube(0,20,1,1);
  const dt=Math.max(0,Math.min(.1,t-BT));BT=t;if(!BS.length)for(let k=0;k<70;k++)BS.push({u:Math.random(),v:.5+Math.random()*.9});
  for(const p of (LITE?BS.slice(0,30):BS)){p.u+=dt*(.12+.6*lips)*p.v;if(p.u>1)p.u-=1;const i=Math.min(n-1,Math.floor(p.u*n)),q=BXP[i];x.globalAlpha=Math.sin(p.u*Math.PI)*.6;x.fillStyle='#fff3d0';x.beginPath();x.arc(q[0],q[1]+20+Math.sin(p.u*30-t*(4+vib*14))*(2+vib*8),1+bite*2,0,6.283);x.fill()}x.globalAlpha=1;
  [230,266,302].forEach((vx,k)=>{const pr=Math.max(0,Math.sin(t*(2+k*.7)+k*2))>.85?8:0;x.fillStyle='#9a7a2a';rr2(x,vx-8,74+pr,16,54,5);x.fill();x.fillStyle='#e8c060';rr2(x,vx-6,76+pr,12,48,4);x.fill();HS[12][k][0]=vx;HS[12][k][1]=62+pr;x.fillStyle='#f4efe6';x.beginPath();x.arc(vx,62+pr,12,0,6.283);x.fill();x.strokeStyle='#e8c060';x.lineWidth=2.2;x.beginPath();x.arc(vx,62+pr,15,-2.356,-2.356+4.712*v[k]);x.stroke();if(!m)tag(x,['LIPS','BITE','VIB'][k],vx,42)});
  if(mute!==BML){BML=mute;BMT=t}const slide=Math.max(0,1-(t-BMT)*3);x.save();x.translate(706+slide*70,100);x.fillStyle='#8a8a92';x.strokeStyle='#d0d0d8';x.lineWidth=2;
  if(mute==1){x.beginPath();x.moveTo(-20,-18);x.lineTo(26,-6);x.lineTo(26,6);x.lineTo(-20,18);x.closePath();x.fill();x.stroke()}
  else if(mute==2){x.beginPath();x.moveTo(-14,-30);x.quadraticCurveTo(26,-32,28,0);x.quadraticCurveTo(26,32,-14,30);x.closePath();x.fill();x.stroke()}
  else if(mute==3){x.beginPath();x.ellipse(4,0,22,30,0,0,6.283);x.fill();x.stroke();x.fillStyle='#2a2a30';x.beginPath();x.arc(8,0,8,0,6.283);x.fill()}
  else if(mute==4){x.fillStyle='#4a2a22';x.beginPath();x.arc(10,0,28,0,6.283);x.fill();x.stroke();x.fillStyle='#2a1a14';x.beginPath();x.arc(10,0,8,0,6.283);x.fill()}
  x.restore();if(sp>.05){const u=(t*.35)%1;x.globalAlpha=(1-u)*sp*.5;x.strokeStyle='#e8c060';x.lineWidth=2;x.beginPath();x.arc(700,100,40+u*160,0,6.283);x.stroke();x.globalAlpha=1}});
 if(m){[0,1,5].forEach((j,k)=>kg(x,28+k*40,H-20,11,v[j],a));return}
 rail(x,v[8],12)}
function voices(x,W,H,v,a,t,m){const vx=v[0],vy=v[1],br=v[2],ch=v[3],vib=v[4],tone=v[5],sp=v[7];
 wrapMini(x,m,()=>{const X0=-OXL,Y0=-OYL,RW=PWL,RH=PHL;let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,'#14102a');g.addColorStop(1,'#05040c');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  const nc=3+Math.round(ch*16);for(let k=0;k<nc;k++){const px=X0+RW*(k+.5)/nc,h=(60+90*(.5+.5*Math.sin(t*(.8+br*2)+k*1.7))*(.4+.6*(1-Math.abs(vy-.5)))+vib*14*Math.sin(t*(4+vib*8)+k))*2.2,hue=250+vx*60;g=x.createLinearGradient(0,Y0+RH,0,Y0+RH-h);g.addColorStop(0,`hsla(${hue},70%,70%,0)`);g.addColorStop(.3,`hsla(${hue},70%,75%,${.1+.12*br+.08*tone})`);g.addColorStop(1,'#0000');x.fillStyle=g;x.fillRect(px-RW/nc*.35,Y0+RH-h,RW/nc*.7,h)}
  if(sp>.05){const u=(t*.3)%1;x.globalAlpha=(1-u)*sp*.4;x.strokeStyle='#c9b8ff';x.lineWidth=2;x.beginPath();x.ellipse(400,110,80+u*400,50+u*120,0,0,6.283);x.stroke();x.globalAlpha=1}
  const px0=150,py0=10,pw=400,ph=190;g=x.createLinearGradient(0,py0,0,py0+ph);g.addColorStop(0,'#ffffff18');g.addColorStop(1,'#ffffff08');x.fillStyle=g;rr2(x,px0,py0,pw,ph,16);x.fill();x.strokeStyle='#c9b8ff66';x.lineWidth=1.4;x.stroke();
  x.strokeStyle='#ffffff14';x.lineWidth=1;for(let k=1;k<4;k++){x.beginPath();x.moveTo(px0+pw*k/4,py0);x.lineTo(px0+pw*k/4,py0+ph);x.stroke();x.beginPath();x.moveTo(px0,py0+ph*k/4);x.lineTo(px0+pw,py0+ph*k/4);x.stroke()}
  [['I',.1,.9],['U',.9,.9],['E',.25,.55],['O',.75,.55],['A',.5,.1]].forEach(([l,u,w])=>{x.fillStyle='#c9b8ff';x.globalAlpha=.7;x.font='700 22px "Space Grotesk"';x.textAlign='center';x.fillText(l,px0+pw*u,py0+ph*(1-w)+8);x.globalAlpha=1});
  const ux=px0+pw*vx,uy=py0+ph*(1-vy);g=x.createRadialGradient(ux,uy,2,ux,uy,60);g.addColorStop(0,'#c9b8ffaa');g.addColorStop(1,'#0000');x.fillStyle=g;x.beginPath();x.arc(ux,uy,60,0,6.283);x.fill();x.fillStyle='#fff';x.beginPath();x.arc(ux,uy,10+Math.sin(t*5)*vib*3,0,6.283);x.fill();x.strokeStyle='#c9b8ff';x.lineWidth=2;x.beginPath();x.arc(ux,uy,16,0,6.283);x.stroke();HS[13][0][0]=ux;HS[13][0][1]=uy;
  const f1=.1+.2*(1-vy),f2=.3+.4*vx,f3=.82;x.strokeStyle='#c9b8ff';x.lineWidth=2;x.beginPath();for(let i=0;i<=80;i++){const u=i/80;let h=0;[[f1,.05,1],[f2,.07,.85],[f3,.06,.5]].forEach(([c,w,am])=>h+=am*Math.exp(-Math.pow((u-c)/w,2)));const xx=590+u*190,yy=190-h*70;i?x.lineTo(xx,yy):x.moveTo(xx,yy)}x.stroke();x.fillStyle='#c9b8ff';x.font='400 8px "Share Tech Mono",monospace';x.textAlign='left';if(!m)x.fillText('FORMANTS  F1 F2 F3',590,206)});
 if(m){[0,1,3].forEach((j,k)=>kg(x,28+k*40,H-20,11,v[j],a));return}
 rail(x,v[8],13)}
function synth(x,W,H,v,a,t,m){const pg=SYPG;
 wrapMini(x,m,()=>{const X0=-OXL,Y0=-OYL,RW=PWL,RH=PHL;let g=x.createLinearGradient(0,Y0,0,Y0+RH);g.addColorStop(0,'#0c1424');g.addColorStop(1,'#04060c');x.fillStyle=g;x.fillRect(X0,Y0,RW,RH);
  x.globalAlpha=.07;x.strokeStyle='#7ad0ff';x.lineWidth=1;for(let k=0;k<40;k++){x.beginPath();x.moveTo(X0+k*22,Y0);x.lineTo(X0+k*22,Y0+RH);x.stroke()}for(let k=0;k<16;k++){x.beginPath();x.moveTo(X0,Y0+k*22);x.lineTo(X0+RW,Y0+k*22);x.stroke()}x.globalAlpha=1;
  const wave=(u,w)=>{const ph=((u%1)+1)%1,sn=Math.sin(6.283*ph),tr=4*Math.abs(ph-.5)-1,sw=2*ph-1,sq=ph<.5?1:-1;return w<.33?sn+(tr-sn)*(w/.33):w<.66?tr+(sw-tr)*((w-.33)/.33):sw+(sq-sw)*((w-.66)/.34)};
  const scope=(x0,y0,w,h,fn,col)=>{x.fillStyle='#00000066';rr2(x,x0,y0,w,h,8);x.fill();x.strokeStyle='#ffffff18';x.lineWidth=1;x.stroke();x.strokeStyle=col;x.lineWidth=1.8;x.beginPath();for(let i=0;i<=80;i++){const u=i/80,yy=y0+h/2-fn(u)*h*.38;i?x.lineTo(x0+u*w,yy):x.moveTo(x0,yy)}x.stroke()};
  const fc=20*Math.pow(1000,v[7]),Q=.6+v[8]*10,ft=Math.round(v[11]*3);
  const resp=f=>{const r=f/fc,d=Math.sqrt(Math.pow(1-r*r,2)+Math.pow(r/Q,2))+1e-6;return ft==0?1/d:ft==1?r*r/d:ft==2?(r/Q)/d:Math.abs(1-r*r)/d};
  if(m){scope(4,4,128,34,u=>wave(u*2+t*.4,v[0]),'#7ad0ff');x.save();x.translate(0,0);x.restore();return}
  if(pg==0){scope(14,8,110,58,u=>wave(u*2+t*.4,v[0])*.6+wave(u*2+t*.4+v[2]*.3,v[0])*.4,'#7ad0ff');scope(134,8,110,58,u=>wave(u*2-t*.3,v[1]),'#ff9ad0');
   x.fillStyle='#00000066';rr2(x,270,8,290,104,10);x.fill();x.strokeStyle='#ffffff18';x.stroke();g=x.createLinearGradient(0,8,0,112);g.addColorStop(0,'#7ad0ff66');g.addColorStop(1,'#7ad0ff00');x.beginPath();for(let i=0;i<=120;i++){const u=i/120,f=20*Math.pow(1000,u),hh=Math.max(-30,Math.min(20,20*Math.log10(resp(f)+1e-6))),yy=94-(hh+30)/50*80;i?x.lineTo(270+u*290,yy):x.moveTo(270,yy)}x.strokeStyle='#7ad0ff';x.lineWidth=2.2;x.stroke();x.lineTo(560,112);x.lineTo(270,112);x.fillStyle=g;x.fill();
   const pxx=270+v[7]*290,pyy=8+(1-v[8])*104;x.fillStyle='#fff';x.beginPath();x.arc(pxx,pyy,7+Math.sin(t*4)*1.2,0,6.283);x.fill();x.strokeStyle='#7ad0ff';x.lineWidth=2;x.beginPath();x.arc(pxx,pyy,12,0,6.283);x.stroke();x.fillStyle='#8fb8d8';x.font='400 8px "Share Tech Mono",monospace';x.textAlign='left';x.fillText('CUTOFF '+Math.round(fc)+' Hz  ·  RES '+Math.round(v[8]*100)+'%',278,22);
   const x0=282,top=136,bot=200,ax=x0+v[12]*70,dx=ax+v[13]*70,sy=top+(1-v[14])*(bot-top),sx=dx+40,rx=sx+v[15]*70;x.fillStyle='#00000066';rr2(x,270,120,290,88,10);x.fill();x.strokeStyle='#ffffff18';x.stroke();
   x.beginPath();x.moveTo(x0,bot);x.lineTo(ax,top);x.lineTo(dx,sy);x.lineTo(sx,sy);x.lineTo(rx,bot);x.strokeStyle='#ffa0d0';x.lineWidth=2.2;x.stroke();x.lineTo(x0,bot);g=x.createLinearGradient(0,top,0,bot);g.addColorStop(0,'#ffa0d044');g.addColorStop(1,'#ffa0d000');x.fillStyle=g;x.fill();
   HS[14]=[[pxx,pyy,18,8,7,[270,8,290,104]],[ax,top,13,-1,12,null,70],[dx,sy,13,14,13,null,70,64],[rx,bot,13,-1,15,null,70]];HS[14].forEach((h,k)=>{if(k){x.fillStyle='#fff';x.beginPath();x.arc(h[0],h[1],6,0,6.283);x.fill();x.strokeStyle='#ffa0d0';x.lineWidth=2;x.beginPath();x.arc(h[0],h[1],10,0,6.283);x.stroke();x.fillStyle='#e8b0cc';x.font='700 8px "Share Tech Mono",monospace';x.textAlign='center';x.fillText('ADSR'[k],h[0],h[1]+(k==2?-14:22))}});
   x.fillStyle='#8fb8d8';x.font='700 8px "Share Tech Mono",monospace';x.textAlign='left';x.fillText('OSC A',22,20);x.fillText('OSC B',142,20)}
  else if(pg==1){const ls=s2=>u=>s2<.33?Math.sin(6.283*u):s2<.66?4*Math.abs(((u%1)+1)%1-.5)-1:s2<.9?2*(((u%1)+1)%1)-1:(((u%1)+1)%1<.5?1:-1);
   scope(14,8,220,62,u=>ls(v[18])(u*2+t*(.3+v[16]*3))*(.2+v[17]*.8),'#7ad0ff');scope(250,8,220,62,u=>Math.sin(6.283*(u*2+t*(.3+v[20]*3)))*(.2+v[21]*.8),'#ff9ad0');
   const src=[['LFO1',v[17]],['LFO2',v[21]],['ENV',v[10]],['MACRO 4',v[25]]],dst=[['CUTOFF',0],['PITCH',1],['DRIVE',2],['LEVEL',3]],map=[Math.round(v[19]*3),1,0,3];x.font='700 8px "Share Tech Mono",monospace';
   src.forEach(([nm,am],k)=>{const y1=34+k*28,y2=34+map[k]*28;g=x.createLinearGradient(530,0,770,0);g.addColorStop(0,'#7ad0ff');g.addColorStop(1,'#ff9ad0');x.strokeStyle=g;x.globalAlpha=.25+.6*am;x.lineWidth=1+am*5;x.beginPath();x.moveTo(548,y1);x.bezierCurveTo(640,y1,660,y2,752,y2);x.stroke();x.globalAlpha=1;const pu=(t*.5+k*.25)%1,bx=548+(752-548)*pu,by=y1+(y2-y1)*(pu*pu*(3-2*pu));x.fillStyle='#fff';x.beginPath();x.arc(bx,by,2.4+am*2,0,6.283);x.fill();x.fillStyle='#8fb8d8';x.textAlign='right';x.fillText(nm,540,y1+3);x.fillStyle='#0c1424';x.strokeStyle='#7ad0ff';x.lineWidth=1.6;x.beginPath();x.arc(548,y1,5,0,6.283);x.fill();x.stroke()});
   dst.forEach(([nm],k)=>{const y2=34+k*28;x.fillStyle='#8fb8d8';x.textAlign='left';x.fillText(nm,764,y2+3);x.fillStyle='#0c1424';x.strokeStyle='#ff9ad0';x.beginPath();x.arc(752,y2,5,0,6.283);x.fill();x.stroke()});x.textAlign='left';x.fillStyle='#8fb8d8';x.fillText('MODULATION',520,16)}
  else{const act=[0,2,4,7,9,4][Math.floor(t*3.2)%6],nv=1+Math.round(v[28]*15);for(let k=0;k<15;k++){const kx=20+k*50.6,on=k==act;g=x.createLinearGradient(0,150,0,208);g.addColorStop(0,on?'#7ad0ff':'#f4f6fa');g.addColorStop(1,on?'#4aa0d0':'#c8ccd6');x.fillStyle=g;rr2(x,kx,150,48,56,4);x.fill()}[0,1,3,4,5,7,8,10,11,12].forEach(k=>{x.fillStyle=(k==act+.5)?'#7ad0ff':'#10141c';rr2(x,20+(k+1)*50.6-14,150,28,34,3);x.fill()});
   for(let k=0;k<16;k++){x.fillStyle=k<nv?'#7ad0ff':'#ffffff22';x.beginPath();x.arc(560+k*13,40,4.2,0,6.283);x.fill()}x.fillStyle='#8fb8d8';x.font='700 8px "Share Tech Mono",monospace';x.textAlign='left';x.fillText('VOICES '+nv+'  ·  '+['POLY','MONO','LEGATO'][Math.round(v[26]*2)],560,62)}
 });
 if(m){[7,8,9].forEach((j,k)=>kg(x,28+k*40,H-20,11,v[j],a));return}
 if(pg!=0)HS[14]=[];rail(x,v[31],14)}
// ---- instrument extras: Surprise me (with locks), A/B compare, soundfont edit drawer
const LK={},ABS={},STEP={9:{0:4,6:4},10:{6:3,9:2,10:2},11:{8:3},12:{3:5,4:5},14:{11:4,18:4,19:4,26:3}};
document.addEventListener('click',e=>{
 const ch=e.target.closest('.chg i');if(ch){const ii=+ch.dataset.i,j=+ch.dataset.j;V[ii][j]=ch.dataset.m=='t'?(V[ii][j]>.5?0:1):+ch.dataset.v;hap(10);upd(ii);return}
 const pt=e.target.closest('.pgt i');if(pt){SYPG=+pt.dataset.pg;pt.closest('.pn').dataset.pg=SYPG;pt.parentNode.querySelectorAll('i').forEach(q=>q.classList.toggle('a',q===pt));hap(8);return}
 const lb=e.target.closest('.pn.ins .kn label');if(lb){const kb=lb.parentNode.querySelector('.kb'),i=+kb.dataset.i,j=+kb.dataset.j;(LK[i]=LK[i]||new Set());LK[i].has(j)?LK[i].delete(j):LK[i].add(j);lb.classList.toggle('lk',LK[i].has(j));hap(8);return}
 const su=e.target.closest('[data-sur]');if(su){surprise(+su.dataset.sur);return}
 const ab=e.target.closest('[data-ab]');if(ab){abToggle(+ab.dataset.ab,ab);return}
 const ed=e.target.closest('.ib[data-ed]');if(ed){$('#ed').classList.toggle('o');ed.classList.toggle('on')}});
function tween(i,to){const a=[...V[i]],t0=performance.now();(function s(t){const u=Math.min(1,(t-t0)/420),k=1-Math.pow(1-u,3);V[i]=a.map((x,j)=>x+(to[j]-x)*k);upd(i);if(u<1)requestAnimationFrame(s)})(t0)}
function surprise(i){const lk=LK[i]||new Set(),to=[...V[i]],n=FX[i].k.length,r=Math.random;for(let j=0;j<n-1;j++){if(lk.has(j))continue;
  if(i!=6){to[j]=.12+r()*.78;const st=(STEP[i]||{})[j];if(st)to[j]=Math.round(to[j]*(st-1))/(st-1)}else if(j<18)to[j]=Math.round((j==2||j==11?4+r()*4:r()*8))/8;else if(j<22||j==23||j==24)to[j]=Math.round(r());else if(j==22)to[j]=Math.floor(r()*6)/5;else if(j==25)to[j]=r()<.5?.5:1;else to[j]=.15+r()*.7}
 tween(i,to);hap(16);toast('Surprise: new sound'+(lk.size?' ('+lk.size+' locked)':''))}
function abToggle(i,btn){if(!ABS[i])ABS[i]={a:[...V[i]],b:[...V[i]],cur:'a'};const s=ABS[i];s[s.cur]=[...V[i]];s.cur=s.cur=='a'?'b':'a';btn.textContent=s.cur.toUpperCase();btn.classList.toggle('on',s.cur=='b');tween(i,s[s.cur]);hap(10)}
const ED={Attack:[0,0,100,' ms'],Decay:[.3,0,100,' %'],Sustain:[.8,0,100,' %'],Release:[.4,0,100,' %'],Cutoff:[1,0,100,' %'],Resonance:[.1,0,100,' %'],Coarse:[.5,-12,12,' st'],Fine:[.5,-50,50,' ct'],Curve:[.5,0,100,' %'],'Vib depth':[0,0,100,' %'],'Vib rate':[.4,0,100,' %'],'Key low':[0,0,127,''],'Key high':[1,0,127,'']};
{const G=[['ENVELOPE',['Attack','Decay','Sustain','Release']],['FILTER',['Cutoff','Resonance']],['TUNING',['Coarse','Fine']],['VELOCITY',['Curve']],['VIBRATO',['Vib depth','Vib rate']],['KEY RANGE',['Key low','Key high']]],id=k=>'ev-'+k.replace(' ','_'),fmt=k=>{const d=ED[k];return Math.round(d[1]+(d[2]-d[1])*d[0])+d[3]};
 $('#ed').innerHTML='<div style="font:700 11px Plus Jakarta Sans">Soundfont edit</div><div style="font:500 10px Plus Jakarta Sans;opacity:.6">Applies to the soundfont of this track. No reload.</div>'+G.map(([h,ks])=>`<h6>${h}</h6>`+ks.map(k=>`<label>${k}<b id="${id(k)}">${fmt(k)}</b></label><input type="range" min="0" max="1" step="0.01" value="${ED[k][0]}" data-ek="${k}">`).join('')).join('');
 $('#ed').querySelectorAll('input').forEach(inp=>inp.oninput=()=>{const k=inp.dataset.ek;ED[k][0]=+inp.value;$('#'+id(k)).textContent=fmt(k)})}
// real-size phone (851 x 393 dp), fitted to screen
function fit(){const p=innerWidth<innerHeight;$('#rot').style.display=p?'flex':'none';const a=rot?[SH,SW]:[SW,SH];S=Math.min(innerWidth*.96/a[0],innerHeight*.96/a[1]);$('#fit').style.transform=`translate(-50%,-50%) scale(${S}) rotate(${rot?90:0}deg)`;QMAX=Math.min(2,Math.max(1,S*(window.devicePixelRatio||1)));if(!QS||QS>QMAX)QS=QMAX;document.documentElement.style.setProperty('--ww',win.clientWidth+'px');document.documentElement.style.setProperty('--wh',win.clientHeight+'px');if(cur>=0)$('#tr').scrollLeft=cur*$('#tr').clientWidth}
$('#rot').onclick=()=>{rot^=1;fit()};addEventListener('resize',fit);
function applyMode(){const R=document.documentElement.style,st=$('#st');st.classList.toggle('tab',!!TAB);SW=TAB?1100:851;SH=TAB?520:393;KS=TAB?1.28:1;R.setProperty('--sw',SW+'px');R.setProperty('--sh',SH+'px');R.setProperty('--ks',KS);
 const W=win.clientWidth,Hh=win.clientHeight;if(TAB){const L=(W-779*KS)/2,T=58+(Hh-58-263*KS)/2;PWL=W/KS;PHL=Hh/KS;OXL=L/KS;OYL=T/KS;R.setProperty('--oxp',L);R.setProperty('--oyp',T)}else{PWL=825;PHL=343;OXL=20;OYL=58}
 R.setProperty('--ox',OXL);R.setProperty('--oy',OYL);R.setProperty('--pw',PWL);R.setProperty('--ph',PHL);$('#tb').textContent=TAB?'Phone':'Tablet';fit()}
$('#tb').onclick=()=>{TAB^=1;applyMode()};fit();

</script></body></html>
````

---

## 3. Spécification mesurée — `fx_layout_spec.json`

Boîtes en px logiques, origine = coin du panneau. Toutes les scènes y sont données
pour **837,6 × 348,2** (le port utilise 825 × 343 : le JSON mesure la page, pas la
fenêtre — voir §0 règle 6). Champs par effet : `id, name, type, font, accent,
knobs[], presets[], layout{w,h,knobs[],body,title,buttons}`.

**Aucune zone de scène** n'y est décrite : les contrôles dessinés (tirettes d'orgue,
cordes de guitare, micros d'ampli, pad XY des voix) vivent dans le HTML de référence,
pas dans le JSON.

### `fx_layout_spec.json`

- **Chemin :** `/tmp/pkg/spec/fx_layout_spec.json`
- **Taille :** 44531 octets · **lignes :** 3293 · **sha256 (12) :** `9bf010b03880`
- Mesures de référence par effet.

````json
{
 "0": {
  "id": 0,
  "name": "Æther Space",
  "type": "Reverb",
  "font": "Space Grotesk",
  "accent": "#a99ad8",
  "knobs": [
   {
    "name": "Size",
    "def": 0.62,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Decay",
    "def": 0.7,
    "min": 0.4,
    "max": 8,
    "unit": " s"
   },
   {
    "name": "Pre-delay",
    "def": 0.2,
    "min": 0,
    "max": 120,
    "unit": " ms"
   },
   {
    "name": "Damp",
    "def": 0.45,
    "min": 1,
    "max": 12,
    "unit": " kHz"
   },
   {
    "name": "Width",
    "def": 0.8,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Mix",
    "def": 0.35,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Diffuse",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   }
  ],
  "presets": [
   "Hall",
   "Plate",
   "Room",
   "Cathedral",
   "Shimmer",
   "Chamber",
   "Bloom",
   "Infinite"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Size",
     "box": {
      "x": 387.8,
      "y": 53.8,
      "w": 54.8,
      "h": 79.2
     }
    },
    {
     "label": "Decay",
     "box": {
      "x": 614.1,
      "y": 84.4,
      "w": 54.8,
      "h": 79.2
     }
    },
    {
     "label": "Pre-delay",
     "box": {
      "x": 665.3,
      "y": 153.1,
      "w": 64,
      "h": 79.2
     }
    },
    {
     "label": "Damp",
     "box": {
      "x": 513.4,
      "y": 208.2,
      "w": 54.8,
      "h": 79.2
     }
    },
    {
     "label": "Width",
     "box": {
      "x": 262.3,
      "y": 208.2,
      "w": 54.8,
      "h": 79.2
     }
    },
    {
     "label": "Mix",
     "box": {
      "x": 105.7,
      "y": 153.1,
      "w": 54.8,
      "h": 79.2
     }
    },
    {
     "label": "Diffuse",
     "box": {
      "x": 161.6,
      "y": 84.4,
      "w": 54.8,
      "h": 79.2
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [],
   "title": {
    "x": 146.2,
    "y": 8.6,
    "w": 144.5,
    "h": 28.4
   }
  }
 },
 "1": {
  "id": 1,
  "name": "Æther Echo",
  "type": "Delay",
  "font": "JetBrains Mono",
  "accent": "#d4b06a",
  "knobs": [
   {
    "name": "Time 1",
    "def": 0.15,
    "min": 10,
    "max": 1000,
    "unit": " ms"
   },
   {
    "name": "Level 1",
    "def": 0.9,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Time 2",
    "def": 0.3,
    "min": 10,
    "max": 1000,
    "unit": " ms"
   },
   {
    "name": "Level 2",
    "def": 0.7,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Time 3",
    "def": 0.45,
    "min": 10,
    "max": 1000,
    "unit": " ms"
   },
   {
    "name": "Level 3",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Time 4",
    "def": 0.6,
    "min": 10,
    "max": 1000,
    "unit": " ms"
   },
   {
    "name": "Level 4",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Feedback",
    "def": 0.42,
    "min": 0,
    "max": 95,
    "unit": " %"
   },
   {
    "name": "Tone",
    "def": 0.6,
    "min": 1,
    "max": 12,
    "unit": " kHz"
   },
   {
    "name": "Wow",
    "def": 0.2,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Mix",
    "def": 0.28,
    "min": 0,
    "max": 100,
    "unit": " %"
   }
  ],
  "presets": [
   "Dotted",
   "Tape",
   "Ping-pong",
   "Slap",
   "Ambient",
   "Dub",
   "Doubler",
   "Pulse"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Feedback",
     "box": {
      "x": 649.7,
      "y": 89.3,
      "w": 61,
      "h": 81.2
     }
    },
    {
     "label": "Tone",
     "box": {
      "x": 738.1,
      "y": 89.3,
      "w": 56.9,
      "h": 81.2
     }
    },
    {
     "label": "Wow",
     "box": {
      "x": 694.4,
      "y": 209.1,
      "w": 56.9,
      "h": 81.2
     }
    },
    {
     "label": "Mix",
     "box": {
      "x": 90.4,
      "y": 228.4,
      "w": 52.8,
      "h": 77.2
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [],
   "title": {
    "x": 146.2,
    "y": 11.7,
    "w": 106.1,
    "h": 22.3
   }
  }
 },
 "2": {
  "id": 2,
  "name": "Prism Tone",
  "type": "Equalizer",
  "font": "Orbitron",
  "accent": "#8fd0ff",
  "knobs": [
   {
    "name": "Low freq",
    "def": 0.15,
    "min": 20,
    "max": 20000,
    "unit": " Hz"
   },
   {
    "name": "Low",
    "def": 0.62,
    "min": -12,
    "max": 12,
    "unit": " dB"
   },
   {
    "name": "Mid freq",
    "def": 0.5,
    "min": 20,
    "max": 20000,
    "unit": " Hz"
   },
   {
    "name": "Mid",
    "def": 0.45,
    "min": -12,
    "max": 12,
    "unit": " dB"
   },
   {
    "name": "Mid Q",
    "def": 0.4,
    "min": 0.3,
    "max": 4,
    "unit": ""
   },
   {
    "name": "High freq",
    "def": 0.85,
    "min": 20,
    "max": 20000,
    "unit": " Hz"
   },
   {
    "name": "High",
    "def": 0.7,
    "min": -12,
    "max": 12,
    "unit": " dB"
   },
   {
    "name": "Output",
    "def": 0.5,
    "min": -12,
    "max": 12,
    "unit": " dB"
   }
  ],
  "presets": [
   "Air",
   "Warm",
   "Vocal",
   "Presence",
   "Telephone",
   "Smile",
   "Flat",
   "Black Box"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Low",
     "box": {
      "x": 36.5,
      "y": 122.8,
      "w": 101.5,
      "h": 127.9
     }
    },
    {
     "label": "Mid",
     "box": {
      "x": 324.9,
      "y": 232.5,
      "w": 60.9,
      "h": 87.3
     }
    },
    {
     "label": "High",
     "box": {
      "x": 654.9,
      "y": 130,
      "w": 79.2,
      "h": 105.6
     }
    },
    {
     "label": "Mid Q",
     "box": {
      "x": 434.5,
      "y": 242.7,
      "w": 44.7,
      "h": 71.1
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [],
   "title": {
    "x": 146.2,
    "y": 12.2,
    "w": 112.2,
    "h": 21.3
   }
  }
 },
 "3": {
  "id": 3,
  "name": "Iron Grip",
  "type": "Compressor",
  "font": "Share Tech Mono",
  "accent": "#b8c2d4",
  "knobs": [
   {
    "name": "Threshold",
    "def": 0.48,
    "min": -60,
    "max": 0,
    "unit": " dB"
   },
   {
    "name": "Ratio",
    "def": 0.35,
    "min": 1,
    "max": 20,
    "unit": ""
   },
   {
    "name": "Attack",
    "def": 0.25,
    "min": 0.1,
    "max": 100,
    "unit": " ms"
   },
   {
    "name": "Release",
    "def": 0.52,
    "min": 10,
    "max": 1000,
    "unit": " ms"
   },
   {
    "name": "Knee",
    "def": 0.4,
    "min": 0,
    "max": 24,
    "unit": " dB"
   },
   {
    "name": "Makeup",
    "def": 0.4,
    "min": 0,
    "max": 24,
    "unit": " dB"
   }
  ],
  "presets": [
   "Glue",
   "Gentle",
   "Punch",
   "Limit",
   "Vocal Leveler",
   "Drum Bus",
   "Parallel"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Ratio",
     "box": {
      "x": 618.3,
      "y": 86.3,
      "w": 79.2,
      "h": 103.6
     }
    },
    {
     "label": "Knee",
     "box": {
      "x": 731,
      "y": 86.3,
      "w": 50.8,
      "h": 75.1
     }
    },
    {
     "label": "Attack",
     "box": {
      "x": 600,
      "y": 222.3,
      "w": 54.8,
      "h": 79.2
     }
    },
    {
     "label": "Release",
     "box": {
      "x": 673.1,
      "y": 222.3,
      "w": 54.8,
      "h": 79.2
     }
    },
    {
     "label": "Makeup",
     "box": {
      "x": 746.2,
      "y": 222.3,
      "w": 54.8,
      "h": 79.2
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [],
   "title": {
    "x": 146.2,
    "y": 11.2,
    "w": 87.6,
    "h": 23.4
   }
  }
 },
 "4": {
  "id": 4,
  "name": "Solar Ceiling",
  "type": "Maximizer",
  "font": "Sora",
  "accent": "#c9852c",
  "knobs": [
   {
    "name": "Amount",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Ceiling",
    "def": 0.92,
    "min": -6,
    "max": 0,
    "unit": " dB"
   },
   {
    "name": "Release",
    "def": 0.4,
    "min": 10,
    "max": 500,
    "unit": " ms"
   },
   {
    "name": "Width",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Mix",
    "def": 1,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Output",
    "def": 0.5,
    "min": -12,
    "max": 12,
    "unit": " dB"
   }
  ],
  "presets": [
   "Transparent",
   "Loud",
   "Punch",
   "Wide",
   "Master Glue",
   "Warm Tape",
   "Radio"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Amount",
     "box": {
      "x": 78.2,
      "y": 107.6,
      "w": 138.1,
      "h": 162.4
     }
    },
    {
     "label": "Ceiling",
     "box": {
      "x": 247.7,
      "y": 87.3,
      "w": 77.2,
      "h": 101.5
     }
    },
    {
     "label": "Release",
     "box": {
      "x": 260.5,
      "y": 220.3,
      "w": 51.6,
      "h": 75.1
     }
    },
    {
     "label": "Width",
     "box": {
      "x": 340.1,
      "y": 220.3,
      "w": 50.8,
      "h": 75.1
     }
    },
    {
     "label": "Mix",
     "box": {
      "x": 340.1,
      "y": 92.4,
      "w": 50.8,
      "h": 75.1
     }
    },
    {
     "label": "Output",
     "box": {
      "x": 735.1,
      "y": 145.2,
      "w": 62.9,
      "h": 87.3
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [],
   "title": {
    "x": 146.2,
    "y": 10.7,
    "w": 135.6,
    "h": 24.4
   }
  }
 },
 "5": {
  "id": 5,
  "name": "Aether Piano",
  "type": "Instrument",
  "font": "Sora",
  "accent": "#e8c98a",
  "knobs": [
   {
    "name": "Felt",
    "def": 0.8,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Lid",
    "def": 0.35,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Pedal",
    "def": 0.55,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Room",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Width",
    "def": 0.7,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Tone",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Body",
    "def": 0.6,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Space",
    "def": 0.25,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Felt",
   "Studio",
   "Upright",
   "Concert",
   "Mellow",
   "Bright"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Tone",
     "box": {
      "x": 151.3,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Body",
     "box": {
      "x": 293.4,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Space",
     "box": {
      "x": 435.6,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 448.6,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 544.6,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "Edit",
     "box": {
      "x": 589.1,
      "y": 15.2,
      "w": 45.4,
      "h": 28.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 10.7,
    "w": 133.7,
    "h": 24.4
   }
  }
 },
 "6": {
  "id": 6,
  "name": "Aether Organ",
  "type": "Instrument",
  "font": "Sora",
  "accent": "#d9c08a",
  "knobs": [
   {
    "name": "Upper 16'",
    "def": 1,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Upper 5⅓'",
    "def": 1,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Upper 8'",
    "def": 1,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Upper 4'",
    "def": 1,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Upper 2⅔'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Upper 2'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Upper 1⅗'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Upper 1⅓'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Upper 1'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 16'",
    "def": 1,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 5⅓'",
    "def": 1,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 8'",
    "def": 1,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 4'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 2⅔'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 2'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 1⅗'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 1⅓'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Lower 1'",
    "def": 0,
    "min": 0,
    "max": 8,
    "unit": ""
   },
   {
    "name": "Perc on",
    "def": 0,
    "min": 0,
    "max": 1,
    "unit": ""
   },
   {
    "name": "Perc soft",
    "def": 0,
    "min": 0,
    "max": 1,
    "unit": ""
   },
   {
    "name": "Perc fast",
    "def": 0,
    "min": 0,
    "max": 1,
    "unit": ""
   },
   {
    "name": "Perc 3rd",
    "def": 0,
    "min": 0,
    "max": 1,
    "unit": ""
   },
   {
    "name": "Vibrato",
    "def": 0.6,
    "min": 0,
    "max": 5,
    "unit": ""
   },
   {
    "name": "Vib upper",
    "def": 1,
    "min": 0,
    "max": 1,
    "unit": ""
   },
   {
    "name": "Vib lower",
    "def": 1,
    "min": 0,
    "max": 1,
    "unit": ""
   },
   {
    "name": "Rotary",
    "def": 1,
    "min": 0,
    "max": 2,
    "unit": ""
   },
   {
    "name": "Click",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Tone",
    "def": 0.55,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Drive",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Space",
    "def": 0.25,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Gospel",
   "Full",
   "Jazz",
   "Rock",
   "Balanced",
   "Whisper"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Click",
     "box": {
      "x": 753.3,
      "y": 133.5,
      "w": 40.6,
      "h": 66
     }
    },
    {
     "label": "Tone",
     "box": {
      "x": 151.3,
      "y": 280.7,
      "w": 42.6,
      "h": 68
     }
    },
    {
     "label": "Drive",
     "box": {
      "x": 293.4,
      "y": 280.7,
      "w": 42.6,
      "h": 68
     }
    },
    {
     "label": "Space",
     "box": {
      "x": 435.6,
      "y": 280.7,
      "w": 42.6,
      "h": 68
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 506.2,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 602.2,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "ON",
     "box": {
      "x": 620.5,
      "y": 69,
      "w": 33.5,
      "h": 25.4
     }
    },
    {
     "text": "SOFT",
     "box": {
      "x": 658.1,
      "y": 69,
      "w": 44.8,
      "h": 25.4
     }
    },
    {
     "text": "FAST",
     "box": {
      "x": 706.9,
      "y": 69,
      "w": 43.5,
      "h": 25.4
     }
    },
    {
     "text": "3RD",
     "box": {
      "x": 754.5,
      "y": 69,
      "w": 38,
      "h": 25.4
     }
    },
    {
     "text": "UPPER",
     "box": {
      "x": 670.1,
      "y": 144.2,
      "w": 52.1,
      "h": 25.4
     }
    },
    {
     "text": "LOWER",
     "box": {
      "x": 670.1,
      "y": 176.7,
      "w": 57.2,
      "h": 25.4
     }
    },
    {
     "text": "STOP",
     "box": {
      "x": 598.6,
      "y": 237.6,
      "w": 44.6,
      "h": 25.4
     }
    },
    {
     "text": "SLOW",
     "box": {
      "x": 647.2,
      "y": 237.6,
      "w": 48.7,
      "h": 25.4
     }
    },
    {
     "text": "FAST",
     "box": {
      "x": 700,
      "y": 237.6,
      "w": 43.5,
      "h": 25.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 10.7,
    "w": 138.7,
    "h": 24.4
   }
  }
 },
 "7": {
  "id": 7,
  "name": "Aether Sax",
  "type": "Instrument",
  "font": "Sora",
  "accent": "#e0a45a",
  "knobs": [
   {
    "name": "Breath",
    "def": 0.55,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Reed",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Growl",
    "def": 0.15,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Vibrato",
    "def": 0.35,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Rate",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Tone",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Body",
    "def": 0.6,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Space",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Smooth",
   "Breathy",
   "Growl",
   "Club",
   "Bright",
   "Subtone"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Tone",
     "box": {
      "x": 151.3,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Body",
     "box": {
      "x": 293.4,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Space",
     "box": {
      "x": 435.6,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 448.6,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 544.6,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "Edit",
     "box": {
      "x": 589.1,
      "y": 15.2,
      "w": 45.4,
      "h": 28.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 10.7,
    "w": 108.9,
    "h": 24.4
   }
  }
 },
 "8": {
  "id": 8,
  "name": "Aether Strings",
  "type": "Instrument",
  "font": "Space Grotesk",
  "accent": "hsl(36,85%,62%)",
  "knobs": [
   {
    "name": "Pressure",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Speed",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Motion",
    "def": 0.35,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Morph",
    "def": 0.1,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Time",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Color",
    "def": 0.1,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Tone",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Body",
    "def": 0.6,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Space",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Solo",
   "Chamber",
   "Orchestra",
   "Cinematic",
   "Warm",
   "Bright"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Color",
     "box": {
      "x": 97.5,
      "y": 205.1,
      "w": 40.6,
      "h": 65
     }
    },
    {
     "label": "Time",
     "box": {
      "x": 215.2,
      "y": 205.1,
      "w": 40.6,
      "h": 65
     }
    },
    {
     "label": "Morph",
     "box": {
      "x": 383.8,
      "y": 185.8,
      "w": 62.9,
      "h": 87.3
     }
    },
    {
     "label": "Motion",
     "box": {
      "x": 571.6,
      "y": 205.1,
      "w": 46.8,
      "h": 65
     }
    },
    {
     "label": "Pressure",
     "box": {
      "x": 681.7,
      "y": 205.1,
      "w": 58,
      "h": 65
     }
    },
    {
     "label": "Speed",
     "box": {
      "x": 767.5,
      "y": 205.1,
      "w": 40.6,
      "h": 65
     }
    },
    {
     "label": "Tone",
     "box": {
      "x": 151.3,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Body",
     "box": {
      "x": 293.4,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Space",
     "box": {
      "x": 435.6,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 448.6,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 544.6,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "Edit",
     "box": {
      "x": 589.1,
      "y": 15.2,
      "w": 45.4,
      "h": 28.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 9.6,
    "w": 153.2,
    "h": 26.4
   }
  }
 },
 "9": {
  "id": 9,
  "name": "Aether Guitar",
  "type": "Instrument",
  "font": "Space Grotesk",
  "accent": "#e08a4a",
  "knobs": [
   {
    "name": "Body",
    "def": 0,
    "min": 0,
    "max": 3,
    "unit": ""
   },
   {
    "name": "Pick",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Strum",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Mute",
    "def": 0.1,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Bend",
    "def": 0.2,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Pickup",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Finish",
    "def": 0.33,
    "min": 0,
    "max": 3,
    "unit": ""
   },
   {
    "name": "Tone",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Resonance",
    "def": 0.7,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Space",
    "def": 0.25,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Acoustic",
   "Nylon",
   "Clean",
   "Crunch",
   "Lead",
   "Ambient"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Tone",
     "box": {
      "x": 151.3,
      "y": 280.7,
      "w": 42.6,
      "h": 68
     }
    },
    {
     "label": "Resonance",
     "box": {
      "x": 284.2,
      "y": 280.7,
      "w": 61,
      "h": 68
     }
    },
    {
     "label": "Space",
     "box": {
      "x": 435.6,
      "y": 280.7,
      "w": 42.6,
      "h": 68
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 448.6,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 544.6,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "Edit",
     "box": {
      "x": 589.1,
      "y": 15.2,
      "w": 45.4,
      "h": 28.4
     }
    },
    {
     "text": "ACOUSTIC",
     "box": {
      "x": 74.1,
      "y": 65,
      "w": 71.9,
      "h": 25.4
     }
    },
    {
     "text": "NYLON",
     "box": {
      "x": 150,
      "y": 65,
      "w": 55,
      "h": 25.4
     }
    },
    {
     "text": "ELEC DC",
     "box": {
      "x": 209,
      "y": 65,
      "w": 63.1,
      "h": 25.4
     }
    },
    {
     "text": "ELEC SC",
     "box": {
      "x": 276.2,
      "y": 65,
      "w": 61.4,
      "h": 25.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 10.7,
    "w": 147.8,
    "h": 24.4
   }
  }
 },
 "10": {
  "id": 10,
  "name": "Aether Amp",
  "type": "Amp",
  "font": "Orbitron",
  "accent": "#ff9a3c",
  "knobs": [
   {
    "name": "Gain",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Bass",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Mid",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Treble",
    "def": 0.6,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Presence",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Master",
    "def": 0.6,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Channel",
    "def": 0,
    "min": 0,
    "max": 2,
    "unit": ""
   },
   {
    "name": "Mic pos",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Mic dist",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Boost",
    "def": 0,
    "min": 0,
    "max": 1,
    "unit": ""
   },
   {
    "name": "Tremolo",
    "def": 0,
    "min": 0,
    "max": 1,
    "unit": ""
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Clean",
   "Crunch",
   "Lead",
   "Blues",
   "Jazz",
   "Metal"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Gain",
     "box": {
      "x": 68,
      "y": 110.7,
      "w": 46.7,
      "h": 71.1
     }
    },
    {
     "label": "Bass",
     "box": {
      "x": 149.2,
      "y": 110.7,
      "w": 46.7,
      "h": 71.1
     }
    },
    {
     "label": "Mid",
     "box": {
      "x": 230.5,
      "y": 110.7,
      "w": 46.7,
      "h": 71.1
     }
    },
    {
     "label": "Treble",
     "box": {
      "x": 311.7,
      "y": 110.7,
      "w": 46.7,
      "h": 71.1
     }
    },
    {
     "label": "Presence",
     "box": {
      "x": 386.8,
      "y": 110.7,
      "w": 59,
      "h": 71.1
     }
    },
    {
     "label": "Master",
     "box": {
      "x": 474.1,
      "y": 110.7,
      "w": 46.8,
      "h": 71.1
     }
    },
    {
     "label": "Level",
     "box": {
      "x": 738.1,
      "y": 110.7,
      "w": 46.7,
      "h": 71.1
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 506.2,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 602.2,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "CLEAN",
     "box": {
      "x": 94.2,
      "y": 65,
      "w": 53.8,
      "h": 25.4
     }
    },
    {
     "text": "CRUNCH",
     "box": {
      "x": 152,
      "y": 65,
      "w": 62.8,
      "h": 25.4
     }
    },
    {
     "text": "LEAD",
     "box": {
      "x": 218.9,
      "y": 65,
      "w": 46.5,
      "h": 25.4
     }
    },
    {
     "text": "BOOST",
     "box": {
      "x": 636.9,
      "y": 65,
      "w": 53.3,
      "h": 25.4
     }
    },
    {
     "text": "TREM",
     "box": {
      "x": 694.2,
      "y": 65,
      "w": 48.7,
      "h": 25.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 11.7,
    "w": 130.2,
    "h": 22.3
   }
  }
 },
 "11": {
  "id": 11,
  "name": "Aether Bass",
  "type": "Amp",
  "font": "Sora",
  "accent": "#f0c060",
  "knobs": [
   {
    "name": "Drive",
    "def": 0.25,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Comp",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Blend",
    "def": 0.6,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "63 Hz",
    "def": 0.55,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "250 Hz",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "1 kHz",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "3 kHz",
    "def": 0.45,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "8 kHz",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Cab",
    "def": 0.5,
    "min": 0,
    "max": 2,
    "unit": ""
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Motown",
   "Rock",
   "Slap",
   "Fuzz",
   "Dub",
   "Clean DI"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Drive",
     "box": {
      "x": 333,
      "y": 97.5,
      "w": 44.7,
      "h": 69
     }
    },
    {
     "label": "Comp",
     "box": {
      "x": 404.1,
      "y": 97.5,
      "w": 44.7,
      "h": 69
     }
    },
    {
     "label": "Blend",
     "box": {
      "x": 475.1,
      "y": 97.5,
      "w": 44.7,
      "h": 69
     }
    },
    {
     "label": "Level",
     "box": {
      "x": 738.1,
      "y": 232.5,
      "w": 46.7,
      "h": 71.1
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 506.2,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 602.2,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "1×15",
     "box": {
      "x": 90.5,
      "y": 194.9,
      "w": 39.3,
      "h": 25.4
     }
    },
    {
     "text": "4×10",
     "box": {
      "x": 133.9,
      "y": 194.9,
      "w": 39.3,
      "h": 25.4
     }
    },
    {
     "text": "8×10",
     "box": {
      "x": 177.3,
      "y": 194.9,
      "w": 39.3,
      "h": 25.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 10.7,
    "w": 124.8,
    "h": 24.4
   }
  }
 },
 "12": {
  "id": 12,
  "name": "Aether Brass",
  "type": "Instrument",
  "font": "Space Grotesk",
  "accent": "#e8c060",
  "knobs": [
   {
    "name": "Lips",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Bite",
    "def": 0.45,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Vibrato",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Section",
    "def": 0.25,
    "min": 0,
    "max": 4,
    "unit": ""
   },
   {
    "name": "Mute",
    "def": 0,
    "min": 0,
    "max": 4,
    "unit": ""
   },
   {
    "name": "Tone",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Body",
    "def": 0.6,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Space",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Open",
   "Muted Jazz",
   "Section Stab",
   "Fanfare",
   "Soft",
   "Harmon"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Tone",
     "box": {
      "x": 151.3,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Body",
     "box": {
      "x": 293.4,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Space",
     "box": {
      "x": 435.6,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 448.6,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 544.6,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "Edit",
     "box": {
      "x": 589.1,
      "y": 15.2,
      "w": 45.4,
      "h": 28.4
     }
    },
    {
     "text": "OPEN",
     "box": {
      "x": 75,
      "y": 65,
      "w": 46.5,
      "h": 25.4
     }
    },
    {
     "text": "STRAIGHT",
     "box": {
      "x": 125.5,
      "y": 65,
      "w": 71.9,
      "h": 25.4
     }
    },
    {
     "text": "CUP",
     "box": {
      "x": 201.5,
      "y": 65,
      "w": 39.2,
      "h": 25.4
     }
    },
    {
     "text": "HARMON",
     "box": {
      "x": 244.7,
      "y": 65,
      "w": 65.7,
      "h": 25.4
     }
    },
    {
     "text": "PLUNGER",
     "box": {
      "x": 314.4,
      "y": 65,
      "w": 67.9,
      "h": 25.4
     }
    },
    {
     "text": "1",
     "box": {
      "x": 581.5,
      "y": 65,
      "w": 23.4,
      "h": 25.4
     }
    },
    {
     "text": "2",
     "box": {
      "x": 608.9,
      "y": 65,
      "w": 23.4,
      "h": 25.4
     }
    },
    {
     "text": "3",
     "box": {
      "x": 636.3,
      "y": 65,
      "w": 23.4,
      "h": 25.4
     }
    },
    {
     "text": "4",
     "box": {
      "x": 663.7,
      "y": 65,
      "w": 23.4,
      "h": 25.4
     }
    },
    {
     "text": "5",
     "box": {
      "x": 691.1,
      "y": 65,
      "w": 23.4,
      "h": 25.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 10.7,
    "w": 135.6,
    "h": 24.4
   }
  }
 },
 "13": {
  "id": 13,
  "name": "Aether Voices",
  "type": "Voice",
  "font": "Space Grotesk",
  "accent": "#c9b8ff",
  "knobs": [
   {
    "name": "Vowel X",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Vowel Y",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Breath",
    "def": 0.3,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Choir",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Vibrato",
    "def": 0.35,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Tone",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Body",
    "def": 0.5,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Space",
    "def": 0.45,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Ah",
   "Ooh",
   "Eeh",
   "Ensemble",
   "Whisper",
   "Angels"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Breath",
     "box": {
      "x": 69,
      "y": 81.2,
      "w": 44.7,
      "h": 69
     }
    },
    {
     "label": "Choir",
     "box": {
      "x": 69,
      "y": 176.7,
      "w": 44.7,
      "h": 69
     }
    },
    {
     "label": "Vibrato",
     "box": {
      "x": 706.8,
      "y": 85.3,
      "w": 48.4,
      "h": 69
     }
    },
    {
     "label": "Tone",
     "box": {
      "x": 151.3,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Body",
     "box": {
      "x": 293.4,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    },
    {
     "label": "Space",
     "box": {
      "x": 435.6,
      "y": 281.2,
      "w": 42.6,
      "h": 67
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 506.2,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 602.2,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 9.6,
    "w": 147.8,
    "h": 26.4
   }
  }
 },
 "14": {
  "id": 14,
  "name": "Aether Synth",
  "type": "Instrument",
  "font": "Orbitron",
  "accent": "#7ad0ff",
  "knobs": [
   {
    "name": "Wave A",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Wave B",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Detune",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Sub",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Noise",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Unison",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Spread",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Cutoff",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Resonance",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Drive",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Env amt",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Filter",
    "def": 0,
    "min": 0,
    "max": 3,
    "unit": ""
   },
   {
    "name": "Attack",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Decay",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Sustain",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Release",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "LFO1 rate",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "LFO1 depth",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "LFO1 shape",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "LFO1 dest",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "LFO2 rate",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "LFO2 depth",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Macro 1",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Macro 2",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Macro 3",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Macro 4",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Mode",
    "def": 0,
    "min": 0,
    "max": 2,
    "unit": ""
   },
   {
    "name": "Glide",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Voices",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Bend",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Tune",
    "def": 0.4,
    "min": 0,
    "max": 100,
    "unit": " %"
   },
   {
    "name": "Level",
    "def": 0.8,
    "min": -24,
    "max": 6,
    "unit": " dB"
   }
  ],
  "presets": [
   "Pad",
   "Lead",
   "Bass",
   "Pluck",
   "Keys",
   "FX"
  ],
  "layout": {
   "w": 837.6,
   "h": 348.2,
   "knobs": [
    {
     "label": "Wave A",
     "box": {
      "x": 45.6,
      "y": 129.4,
      "w": 40.7,
      "h": 66
     }
    },
    {
     "label": "Wave B",
     "box": {
      "x": 126.9,
      "y": 129.4,
      "w": 40.7,
      "h": 66
     }
    },
    {
     "label": "Detune",
     "box": {
      "x": 208.1,
      "y": 129.4,
      "w": 40.7,
      "h": 66
     }
    },
    {
     "label": "Sub",
     "box": {
      "x": 45.7,
      "y": 198.5,
      "w": 40.6,
      "h": 66
     }
    },
    {
     "label": "Noise",
     "box": {
      "x": 126.9,
      "y": 198.5,
      "w": 40.6,
      "h": 66
     }
    },
    {
     "label": "Unison",
     "box": {
      "x": 208.1,
      "y": 198.5,
      "w": 40.7,
      "h": 66
     }
    },
    {
     "label": "Drive",
     "box": {
      "x": 639.6,
      "y": 90.9,
      "w": 40.6,
      "h": 66
     }
    },
    {
     "label": "Env amt",
     "box": {
      "x": 707.3,
      "y": 90.9,
      "w": 47.5,
      "h": 66
     }
    },
    {
     "label": "Spread",
     "box": {
      "x": 775.6,
      "y": 90.9,
      "w": 40.7,
      "h": 66
     }
    },
    {
     "label": "LFO1 rate",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "LFO1 depth",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "LFO2 rate",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "LFO2 depth",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "Macro 4",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "Glide",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "Voices",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "Bend",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "Tune",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "label": "Macro 1",
     "box": {
      "x": 148.9,
      "y": 280.7,
      "w": 47.5,
      "h": 68
     }
    },
    {
     "label": "Macro 2",
     "box": {
      "x": 291,
      "y": 280.7,
      "w": 47.5,
      "h": 68
     }
    },
    {
     "label": "Macro 3",
     "box": {
      "x": 433.1,
      "y": 280.7,
      "w": 47.5,
      "h": 68
     }
    }
   ],
   "body": {
    "x": 0,
    "y": 0,
    "w": 837.6,
    "h": 348.2
   },
   "buttons": [
    {
     "text": "Surprise me",
     "box": {
      "x": 506.2,
      "y": 15.2,
      "w": 83.8,
      "h": 28.4
     }
    },
    {
     "text": "A",
     "box": {
      "x": 602.2,
      "y": 15.2,
      "w": 32.3,
      "h": 28.4
     }
    },
    {
     "text": "SOUND",
     "box": {
      "x": 292.4,
      "y": 14.2,
      "w": 56,
      "h": 22.3
     }
    },
    {
     "text": "MOTION",
     "box": {
      "x": 352.4,
      "y": 14.2,
      "w": 63.8,
      "h": 22.3
     }
    },
    {
     "text": "VOICE",
     "box": {
      "x": 420.3,
      "y": 14.2,
      "w": 53.7,
      "h": 22.3
     }
    },
    {
     "text": "LP",
     "box": {
      "x": 615.3,
      "y": 180.7,
      "w": 31.3,
      "h": 25.4
     }
    },
    {
     "text": "HP",
     "box": {
      "x": 650.6,
      "y": 180.7,
      "w": 32.4,
      "h": 25.4
     }
    },
    {
     "text": "BP",
     "box": {
      "x": 687,
      "y": 180.7,
      "w": 31.3,
      "h": 25.4
     }
    },
    {
     "text": "NOTCH",
     "box": {
      "x": 722.3,
      "y": 180.7,
      "w": 55.5,
      "h": 25.4
     }
    },
    {
     "text": "SIN",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "TRI",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "SAW",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "SQR",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "CUTOFF",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "PITCH",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "WAVE",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "LEVEL",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "POLY",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "MONO",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    },
    {
     "text": "LEGATO",
     "box": {
      "x": -31.2,
      "y": -55.1,
      "w": 0,
      "h": 0
     }
    }
   ],
   "title": {
    "x": 146.2,
    "y": 15.7,
    "w": 85.8,
    "h": 14.2
   }
  }
 }
}
````

### Briefs du paquet de spécification

### `00-LIS-MOI-EN-PREMIER.md`

- **Chemin :** `/tmp/pkg/spec/00-LIS-MOI-EN-PREMIER.md`
- **Taille :** 2862 octets · **lignes :** 34 · **sha256 (12) :** `02fa17a0b2f0`
- Règles de priorité des sources.

````markdown
# Fenêtre FX · Spécification exacte pour l'implémentation (ordre strict)

Objectif : la fenêtre Compose doit être **identique** à la référence, pixel près. Aucune interprétation.

## Sources, par ordre d'autorité
1. `reference_source_fx_window.html` : le code de référence. Ouvre-le dans un navigateur pour le voir. Toute valeur (couleur, rayon, position, durée) se lit dedans.
2. `fx_layout_spec.json` : mesures extraites de la référence, par effet (boîte de chaque potard, bouton, zone de scène, en pixels logiques, origine = coin du panneau d'effet).
3. `references/ref-NN-*.png` : rendu à 2× du preset par défaut. `references/ref-NN-*-presets.png` : tous les presets. `references/ref-tablet-*.png` : tablette.

Si une source contredit une autre, la source 1 gagne. Signale la contradiction, ne choisis pas.

## Règles de travail
- Aucune valeur « arrondie à l'œil ». Copie les nombres du JSON.
- Aucun écran ajouté, retiré ou simplifié. Chaque élément visible dans la référence doit exister dans l'app.
- Chaque effet est une étape. Une étape n'est terminée que si le test de la section « Comparaison » passe.
- Pas de « presque ». Un écart au-delà du seuil = étape non terminée.

## Ordre d'implémentation
1. Le cadre commun : panneau, en-tête (titre, compteur, boutons), zone de contenu, bandeau LEVEL. Mêmes dimensions que la référence.
2. Les composants communs : potard (sprite mis en cache), bouton de preset, bouton Bypass, bouton A/B, bouton Surprise me.
3. Un effet par étape, dans l'ordre : Ampli (10), Maximizer (4), puis les autres. Ne passe pas au suivant tant que le précédent n'est pas validé.

## Comparaison (obligatoire à chaque effet)
- Capture de l'écran Compose à la même taille logique que la référence, rendu à 2×.
- Superposition avec `references/ref-NN-*.png` via `compare_refs.py` (fourni).
- Seuil : au plus 1,5 % de pixels qui diffèrent de plus de 24/255 sur les 3 canaux, hors zones animées (les scènes sont comparées à l'image fixe de t = 0).
- Les zones animées ne sont pas comparées pixel à pixel ; elles sont vérifiées par un test de mouvement (la zone change entre deux instants).
- Rapport : `Effet N : PASS/FAIL · écart = X % · zones hors seuil = [...]`.

## Branchement (à vérifier séparément, pas dans le rendu)
Pour chaque potard : une valeur envoyée produit un changement attendu côté natif (journal + mesure). Pour chaque preset : tous les paramètres changent. Pour Bypass : le signal change.

## Ce qui n'est PAS dans fx_layout_spec.json
Les contrôles dessinés dans la scène (ex. les tirettes de l'orgue, les pistes du synthé, les micros de l'ampli) sont des zones définies dans le code de référence (tableaux `HS` et `AB`, fonctions `draw…`). Elles se lisent dans `reference_source_fx_window.html`. Ne les réinvente pas.

````

### `01-ecrans-a-reproduire.md`

- **Chemin :** `/tmp/pkg/spec/01-ecrans-a-reproduire.md`
- **Taille :** 1673 octets · **lignes :** 21 · **sha256 (12) :** `813dc165ab89`
- Liste des écrans à produire.

````markdown
# Liste des écrans à reproduire (une ligne par effet)

| Id | Effet | Potards (DOM) | Presets | Référence PNG |
|---|---|---|---|---|
| 0 | Æther Space | 7 | Hall, Plate, Room, Cathedral, Shimmer, Chamber, Bloom, Infinite | ref-00-*.png |
| 1 | Æther Echo | 4 | Dotted, Tape, Ping-pong, Slap, Ambient, Dub, Doubler, Pulse | ref-01-*.png |
| 2 | Prism Tone | 4 | Air, Warm, Vocal, Presence, Telephone, Smile, Flat, Black Box | ref-02-*.png |
| 3 | Iron Grip | 5 | Glue, Gentle, Punch, Limit, Vocal Leveler, Drum Bus, Parallel | ref-03-*.png |
| 4 | Solar Ceiling | 6 | Transparent, Loud, Punch, Wide, Master Glue, Warm Tape, Radio | ref-04-*.png |
| 5 | Aether Piano | 3 | Felt, Studio, Upright, Concert, Mellow, Bright | ref-05-*.png |
| 6 | Aether Organ | 4 | Gospel, Full, Jazz, Rock, Balanced, Whisper | ref-06-*.png |
| 7 | Aether Sax | 3 | Smooth, Breathy, Growl, Club, Bright, Subtone | ref-07-*.png |
| 8 | Aether Strings | 9 | Solo, Chamber, Orchestra, Cinematic, Warm, Bright | ref-08-*.png |
| 9 | Aether Guitar | 3 | Acoustic, Nylon, Clean, Crunch, Lead, Ambient | ref-09-*.png |
| 10 | Aether Amp | 7 | Clean, Crunch, Lead, Blues, Jazz, Metal | ref-10-*.png |
| 11 | Aether Bass | 4 | Motown, Rock, Slap, Fuzz, Dub, Clean DI | ref-11-*.png |
| 12 | Aether Brass | 3 | Open, Muted Jazz, Section Stab, Fanfare, Soft, Harmon | ref-12-*.png |
| 13 | Aether Voices | 6 | Ah, Ooh, Eeh, Ensemble, Whisper, Angels | ref-13-*.png |
| 14 | Aether Synth | 21 | Pad, Lead, Bass, Pluck, Keys, FX | ref-14-*.png |

Les contrôles dessinés dans la scène (tirettes, pistes, micros, pads) ne sont pas dans les potards DOM : ils sont dans le code de référence (HS/AB).

````

### `compare_refs.py`

- **Chemin :** `/tmp/pkg/spec/compare_refs.py`
- **Taille :** 923 octets · **lignes :** 20 · **sha256 (12) :** `ef8fd54fd348`
- Comparateur fourni (tailles strictement égales).

````python
"""Compare a Compose capture with its reference PNG.
usage: python3 compare_refs.py capture.png references/ref-00-space.png
Exit code 0 = PASS (<= 1.5 % of pixels differ by more than 24/255 on any channel).
Capture must be the same logical size as the reference (rendered at 2x)."""
import sys
from PIL import Image, ImageChops
cap, ref = Image.open(sys.argv[1]).convert('RGB'), Image.open(sys.argv[2]).convert('RGB')
if cap.size != ref.size:
    print(f'FAIL size {cap.size} != reference {ref.size}'); sys.exit(1)
d = ImageChops.difference(cap, ref).split()
bad = [0] * (cap.size[0] * cap.size[1])
mask = Image.new('L', cap.size, 0)
for ch in d:
    m = ch.point(lambda v: 255 if v > 24 else 0)
    mask = ImageChops.lighter(mask, m)
n = sum(1 for v in mask.getdata() if v)
pct = 100.0 * n / (cap.size[0] * cap.size[1])
ok = pct <= 1.5
print(f"{'PASS' if ok else 'FAIL'} · écart = {pct:.2f} %")
sys.exit(0 if ok else 1)

````

### Références PNG — non incluses (binaires)

33 images dans `fx-screens-spec.zip`, lisibles depuis le dépôt public :

- Écrans : `references/ref-NN-<nom>.png` — **1728 × 800** (échelle 2,0292)
- Variantes presets : `ref-NN-<nom>-presets.png` — 1728 × 1600
- Tablette : `ref-tablet-NN-<nom>.png` — 1230 × 582

Recadrage correct : la fenêtre est le **fond d'effet**, mesuré sur `ref-01-echo.png`
à y 39,4..381,6 et x 12,8..837,0 de la page 851 × 393.

---

## 4. Briefs d'architecture — paquet `aether-fx-package.zip`

### `00-LISEZ-MOI-AGENT.md`

- **Chemin :** `/tmp/pkg/aether/00-LISEZ-MOI-AGENT.md`
- **Taille :** 4737 octets · **lignes :** 44 · **sha256 (12) :** `7ca0e984566e`
- Point d'entrée du paquet d'origine.

````markdown
# Æther Keys · Package FX (effets, instruments, interface) · À LIRE EN PREMIER

Tu implémentes le système d'effets et d'instruments d'Æther Keys dans l'application existante. **Tout ce dont tu as besoin est dans ce dossier, à plat.** Ne cherche rien sur internet pour les API déjà décrites ici.

## Règles (à respecter à la lettre)
1. **Agis, ne divague pas.** Pas de recherches inutiles, pas de refactor non demandé. Fais l'étape, lance son test, passe à la suivante.
2. **Copie les fichiers `fx_*.h` et `fx-window-preview.html` octet pour octet.** Seuls le `package` des `.kt` et les chemins CMake peuvent changer. Si un en-tête ne compile pas, rapporte l'erreur : ne réécris pas la logique.
3. **Ne casse rien d'existant.** Chaque piste garde **sa propre soundfont** à tout moment. Ne touche pas aux sources de FluidSynth (réglages CMake seulement). Ne modifie pas le fonctionnement des pistes, du mixeur, du TonicPad, du DrumPad, des Sheets.
4. **Deux lots bien séparés** : *Effets* (6 emplacements par piste, 8 sur le Master) et *Instrument* (un seul par piste, **jamais** sur le Master). Voir 01.
5. **L'aperçu HTML est la référence visuelle sans altération.** Aucune couleur, cote, police ou animation « à ta façon ». Voir 03.
6. **Si un test échoue trois fois de suite, arrête-toi** et rapporte en 10 lignes maximum (ce que tu as fait, l'erreur exacte, ce que tu supposes). Ne devine pas.
7. **Ne prétends pas avoir vérifié ce que tu n'as pas exécuté.** Écris « non vérifié » quand c'est le cas.

## Contenu (tout à plat)
| Fichiers | Rôle |
|---|---|
| `00` à `05-*.md` | Ces instructions. 01 architecture · 02 paramètres et mappages · 03 interface · 04 intégration audio et JNI · 05 tests |
| `fx-window-preview.html` | **Référence visuelle et comportementale** de toute la fenêtre FX (téléphone et tablette). À ouvrir dans un navigateur |
| `ref-NN-*.png`, `ref-NN-*-presets.png`, `ref-tablet-*.png` | Rendus de référence (2× pour le preset par défaut, feuille de tous les presets, tablette) |
| `ic_fx_*.xml` / `ic_fx_*.svg` | 15 icônes (VectorDrawable Android et SVG), conversion vérifiée pixel à pixel |
| `fx_core.h` … `fx_mixer.h` | Bibliothèque C++17 header-only (DSP, moteurs, façade `FxMixer`) |
| `fx_test.cpp`, `fx_test2.cpp`, `Makefile` | Bancs de test hôte (116 contrôles) |
| `gen_kotlin.cpp`, `FxRegistry.kt` | Générateur et registre Kotlin (même contenu que `fx_registry.h`) |

## Ordre de travail
**Étape 0 · Tests hôte.** `make test` dans ce dossier. Attendu : `26 checks, 0 failed` puis `90 checks, 0 failed`. Si ce n'est pas le cas, arrête-toi.
**Étape 1 · Bibliothèque dans le projet.** Copie les `fx_*.h` dans `app/src/main/cpp/aetherfx/`, ajoute `target_include_directories(native-lib PRIVATE aetherfx)` (aucun .cpp à ajouter). `minSdk` = 26. Compile. *Critère : le projet compile, le son est inchangé.*
**Étape 2 · Sortie séparée par canal** (04, étape 2). *Critère : la somme des canaux égale l'ancien mix à 1e-5.*
**Étape 3 · Brancher `FxMixer`** dans `AudioEngine` (04, étape 3), supprimer l'ancienne chaîne d'effets, retirer mutex, logs et redimensionnements du rappel audio. *Critère : 8 pistes et 2 réverbes pendant 10 minutes sans xrun.*
**Étape 4 · Instruments** : notes routées vers les sources, soundfont mise en pause pour les sources, décalages de générateurs pour les façonneurs (04, étapes 4 et 5).
**Étape 5 · JNI et Kotlin** : `FxRegistry.kt` + pont natif (04, étape 6).
**Étape 6 · Interface** : port fidèle de `fx-window-preview.html` (03). Un effet à la fois, comparaison aux `ref-*.png`.
**Étape 7 · Persistance et avertissement de soundfont** (01).
**Étape 8 · Validation sur appareil** (05, liste de contrôle).

## Format du rapport après chaque étape (10 lignes maximum)
`Étape N : faite / bloquée` · fichiers modifiés · commande de test lancée et résultat exact · ce qui n'a pas été vérifié · prochaine étape.

## Ce qui n'a PAS été vérifié par l'auteur de ce package
- **Aucun code Kotlin/Compose de l'interface n'a été écrit ni compilé** : l'interface est livrée comme aperçu HTML de référence et rendus PNG. Le port Compose est ton travail (03).
- `FxRegistry.kt` est généré mais n'a pas été compilé dans le projet.
- Le son sur un téléphone réel (qualité subjective, CPU, latence). Les mesures de 05 sont sur un ordinateur ; compte 3 à 6 fois plus sur téléphone.
- L'effet réel de `fluid_synth_set_gen` sur **tes** soundfonts (voir 04, étape 5 : à tester une fois sur un soundfont réel).
- Le bug de « disparition des animations » n'a pas été reproduit dans l'aperçu ; des protections sont en place (03).

````

### `01-architecture.md`

- **Chemin :** `/tmp/pkg/aether/01-architecture.md`
- **Taille :** 4252 octets · **lignes :** 40 · **sha256 (12) :** `2bf49546c545`
- Architecture du moteur et de la chaîne.

````markdown
# 01 · Architecture : deux lots séparés

## Vocabulaire
- **Effet** (`Kind::Effect`) : traite le son d'une piste ou du Master. Ids 0 Space, 1 Echo, 2 Tone, 3 Grip, 4 Ceiling, 10 Amp, 11 Bass, 13 Voices.
- **Façonneur** (`Kind::Shaper`) : *modifie le soundfont chargé* sur la piste (décalages de générateurs + traitement interne). Ids 5 Piano, 7 Sax, 8 Strings, 9 Guitar, 12 Brass.
- **Source** (`Kind::Source`) : *remplace* le son du soundfont tant qu'elle est choisie. Ids 6 Organ, 14 Synth.
- Façonneurs et sources forment le lot **Instrument**. `kindOf(id)` (dans `fx_registry.h`) est la seule règle de répartition.

## Règles
| Règle | Détail |
|---|---|
| Lot Effets | 6 emplacements par piste, **8 sur le Master**. Un effet ne peut être présent qu'une fois par piste. `FxMixer::setEffect` refuse tout id qui n'est pas un effet. |
| Lot Instrument | **Un seul** par piste (`FxMixer::setInstrument`). Choisir un nouvel instrument **remplace** l'ancien (message « X remplace Y »). Refusé sur le Master, le sampler DrumPad et les canaux Drum/TonicPad (`instrumentAllowed`). |
| Soundfont | Chaque piste garde sa soundfont **chargée en permanence**. Pas d'instrument = le soundfont tel quel. Façonneur = soundfont modifié. Source = soundfont **en pause** (ne pas le rendre, `soundfontPaused(strip)`), il revient à l'identique quand on repasse sur « aucun instrument » ou sur un façonneur. |
| Master | Seulement des effets. Aucune option Instrument. |
| Voices | C'est un **effet** (filtre à formants), pas une source. |
| Couches | Une source par piste en v1. Soundfont + Synth en même temps = v2. |

## Strips (`FxMixer`)
0..7 pistes (canaux MIDI 0..7) · 8 canal Drum du soundfont · 9 TonicPad (canal 9) · 10 sampler DrumPad · `kMaster = 15`. Chaque strip a fader, balance, mute et vumètres ; les strips 0..7 ont en plus l'emplacement Instrument.

## Interface : où vit chaque lot
- En-tête du rack : titre « Effects », compteur `n / 6 effects`, puce **Soundfont**, boutons **+ Instrument** et **+ Effect** (voir l'aperçu).
- La **puce Soundfont ouvre la fenêtre soundfonts qui existe déjà dans l'app** (ne la refais pas). Dans l'aperçu c'est une doublure qui reproduit sa structure. Si une source est active, la puce affiche « Soundfont paused · nom ».
- La grille d'ajout a deux onglets : **EFFECTS** (puces Dynamics, EQ, Space, Amps, Voices) et **INSTRUMENT** (Keys, Guitar, Winds, Strings, Synth). Dans l'onglet Instrument, choisir remplace l'instrument courant.
- L'instrument est toujours la **première** carte du rack, avec un liseré doré.

## Avertissement soundfont / façonneur (ne bloque jamais)
`matchShaper(fxId, presetName, gmProgram, bank, keyLow, keyHigh)` (`fx_match.h`) renvoie `Ok`, `Weak` ou `Mismatch`. Ordre : le **nom du preset** d'abord, puis la famille General MIDI du **numéro de programme**, puis la plage de notes. Un kit de batterie (bank 128) donne toujours `Mismatch`.
- À chaque changement de soundfont/preset de la piste, et à l'ajout d'un façonneur : appeler `matchShaper` avec les données de la fenêtre soundfonts existante (nom du preset, programme, banque).
- Si `Mismatch` : afficher le **gros bandeau rouge** de l'aperçu en haut de la fenêtre du façonneur : « This doesn't look like a saxophone. The loaded soundfont is “…”. Using Aether Sax on it may sound wrong. Choose a matching soundfont for the best result. » avec deux boutons : **Change soundfont** (ouvre la fenêtre soundfonts existante) et **Use anyway** (masque le bandeau jusqu'au prochain changement de soundfont).
- Les sources et les effets n'affichent pas ce bandeau.

## Persistance (JSON par piste, clés stables)
```json
{ "track": 2, "instrument": {"id": 7, "preset": "Smooth", "params": [0.55, 0.4, ...], "bypass": false},
  "effects": [ {"id": 0, "preset": "Hall", "params": [...], "bypass": false}, null, ... 6 entrées ],
  "ab": {"a": [...], "b": [...], "cur": "a"} }
```
Master : `{"effects": [ ... 8 entrées ]}`. Les presets utilisateur (« My preset N ») sont stockés à part, par id d'effet. Les paramètres sont toujours normalisés 0..1. À la restauration : `setInstrument`, `setInstrumentParam` pour chaque indice, puis les décalages de générateurs (04).

````

### `02-parametres-et-mappages.md`

- **Chemin :** `/tmp/pkg/aether/02-parametres-et-mappages.md`
- **Taille :** 15378 octets · **lignes :** 334 · **sha256 (12) :** `eea05616f10b`
- Paramètres et mappages.

````markdown
# 02 · Paramètres, presets et mappages audio

Source de vérité des valeurs : `fx_registry.h` (généré avec le même contenu que l'aperçu `fx-window-preview.html`). `FxRegistry.kt` en est la copie Kotlin générée. **Tous les paramètres sont normalisés 0..1** entre l'interface et le moteur ; la conversion en unités réelles se fait avec `min`/`max`/`curve` du registre.

Légende : *UI* = paramètre visuel seulement, jamais envoyé au moteur audio.


## 0 · Æther Space  —  Effet

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Size | 0 | 100 | % | 0.62 |  |
| 1 | Decay | 0.4 | 8 | s | 0.7 |  |
| 2 | Pre-delay | 0 | 120 | ms | 0.2 |  |
| 3 | Damp | 1 | 12 | kHz | 0.45 |  |
| 4 | Width | 0 | 100 | % | 0.8 |  |
| 5 | Mix | 0 | 100 | % | 0.35 |  |
| 6 | Diffuse | 0 | 100 | % | 0.5 |  |

Presets : `Hall`, `Plate`, `Room`, `Cathedral`, `Shimmer`, `Chamber`, `Bloom`, `Infinite`


## 1 · Æther Echo  —  Effet

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Time 1 | 10 | 1000 | ms | 0.15 |  |
| 1 | Level 1 | 0 | 100 | % | 0.9 |  |
| 2 | Time 2 | 10 | 1000 | ms | 0.3 |  |
| 3 | Level 2 | 0 | 100 | % | 0.7 |  |
| 4 | Time 3 | 10 | 1000 | ms | 0.45 |  |
| 5 | Level 3 | 0 | 100 | % | 0.5 |  |
| 6 | Time 4 | 10 | 1000 | ms | 0.6 |  |
| 7 | Level 4 | 0 | 100 | % | 0.3 |  |
| 8 | Feedback | 0 | 95 | % | 0.42 |  |
| 9 | Tone | 1 | 12 | kHz | 0.6 |  |
| 10 | Wow | 0 | 100 | % | 0.2 |  |
| 11 | Mix | 0 | 100 | % | 0.28 |  |

Presets : `Dotted`, `Tape`, `Ping-pong`, `Slap`, `Ambient`, `Dub`, `Doubler`, `Pulse`


## 2 · Prism Tone  —  Effet

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Low freq | 20 | 20000 | Hz | 0.15 |  |
| 1 | Low | -12 | 12 | dB | 0.62 |  |
| 2 | Mid freq | 20 | 20000 | Hz | 0.5 |  |
| 3 | Mid | -12 | 12 | dB | 0.45 |  |
| 4 | Mid Q | 0.3 | 4 | — | 0.4 |  |
| 5 | High freq | 20 | 20000 | Hz | 0.85 |  |
| 6 | High | -12 | 12 | dB | 0.7 |  |
| 7 | Output | -12 | 12 | dB | 0.5 |  |

Presets : `Air`, `Warm`, `Vocal`, `Presence`, `Telephone`, `Smile`, `Flat`, `Black Box`


## 3 · Iron Grip  —  Effet

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Threshold | -60 | 0 | dB | 0.48 |  |
| 1 | Ratio | 1 | 20 | — | 0.35 |  |
| 2 | Attack | 0.1 | 100 | ms | 0.25 |  |
| 3 | Release | 10 | 1000 | ms | 0.52 |  |
| 4 | Knee | 0 | 24 | dB | 0.4 |  |
| 5 | Makeup | 0 | 24 | dB | 0.4 |  |

Presets : `Glue`, `Gentle`, `Punch`, `Limit`, `Vocal Leveler`, `Drum Bus`, `Parallel`


## 4 · Solar Ceiling  —  Effet

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Amount | 0 | 100 | % | 0.5 |  |
| 1 | Ceiling | -6 | 0 | dB | 0.92 |  |
| 2 | Release | 10 | 500 | ms | 0.4 |  |
| 3 | Width | 0 | 100 | % | 0.3 |  |
| 4 | Mix | 0 | 100 | % | 1 |  |
| 5 | Output | -12 | 12 | dB | 0.5 |  |

Presets : `Transparent`, `Loud`, `Punch`, `Wide`, `Master Glue`, `Warm Tape`, `Radio`


## 5 · Aether Piano  —  Façonneur

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Felt | 0 | 100 | % | 0.8 |  |
| 1 | Lid | 0 | 100 | % | 0.35 |  |
| 2 | Pedal | 0 | 100 | % | 0.55 |  |
| 3 | Room | 0 | 100 | % | 0.3 |  |
| 4 | Width | 0 | 100 | % | 0.7 |  |
| 5 | Tone | 0 | 100 | % | 0.4 |  |
| 6 | Body | 0 | 100 | % | 0.6 |  |
| 7 | Space | 0 | 100 | % | 0.25 |  |
| 8 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Felt`, `Studio`, `Upright`, `Concert`, `Mellow`, `Bright`


## 6 · Aether Organ  —  Source

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Upper 16' | 0 | 8 | — | 1 |  |
| 1 | Upper 5⅓' | 0 | 8 | — | 1 |  |
| 2 | Upper 8' | 0 | 8 | — | 1 |  |
| 3 | Upper 4' | 0 | 8 | — | 1 |  |
| 4 | Upper 2⅔' | 0 | 8 | — | 0 |  |
| 5 | Upper 2' | 0 | 8 | — | 0 |  |
| 6 | Upper 1⅗' | 0 | 8 | — | 0 |  |
| 7 | Upper 1⅓' | 0 | 8 | — | 0 |  |
| 8 | Upper 1' | 0 | 8 | — | 0 |  |
| 9 | Lower 16' | 0 | 8 | — | 1 |  |
| 10 | Lower 5⅓' | 0 | 8 | — | 1 |  |
| 11 | Lower 8' | 0 | 8 | — | 1 |  |
| 12 | Lower 4' | 0 | 8 | — | 0 |  |
| 13 | Lower 2⅔' | 0 | 8 | — | 0 |  |
| 14 | Lower 2' | 0 | 8 | — | 0 |  |
| 15 | Lower 1⅗' | 0 | 8 | — | 0 |  |
| 16 | Lower 1⅓' | 0 | 8 | — | 0 |  |
| 17 | Lower 1' | 0 | 8 | — | 0 |  |
| 18 | Perc on | 0 | 1 | — | 0 |  |
| 19 | Perc soft | 0 | 1 | — | 0 |  |
| 20 | Perc fast | 0 | 1 | — | 0 |  |
| 21 | Perc 3rd | 0 | 1 | — | 0 |  |
| 22 | Vibrato | 0 | 5 | — | 0.6 |  |
| 23 | Vib upper | 0 | 1 | — | 1 |  |
| 24 | Vib lower | 0 | 1 | — | 1 |  |
| 25 | Rotary | 0 | 2 | — | 1 |  |
| 26 | Click | 0 | 100 | % | 0.3 |  |
| 27 | Tone | 0 | 100 | % | 0.55 |  |
| 28 | Drive | 0 | 100 | % | 0.4 |  |
| 29 | Space | 0 | 100 | % | 0.25 |  |
| 30 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Gospel`, `Full`, `Jazz`, `Rock`, `Balanced`, `Whisper`


## 7 · Aether Sax  —  Façonneur

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Breath | 0 | 100 | % | 0.55 |  |
| 1 | Reed | 0 | 100 | % | 0.5 |  |
| 2 | Growl | 0 | 100 | % | 0.15 |  |
| 3 | Vibrato | 0 | 100 | % | 0.35 |  |
| 4 | Rate | 0 | 100 | % | 0.4 |  |
| 5 | Tone | 0 | 100 | % | 0.5 |  |
| 6 | Body | 0 | 100 | % | 0.6 |  |
| 7 | Space | 0 | 100 | % | 0.3 |  |
| 8 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Smooth`, `Breathy`, `Growl`, `Club`, `Bright`, `Subtone`


## 8 · Aether Strings  —  Façonneur

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Pressure | 0 | 100 | % | 0.5 |  |
| 1 | Speed | 0 | 100 | % | 0.5 |  |
| 2 | Motion | 0 | 100 | % | 0.35 |  |
| 3 | Morph | 0 | 100 | % | 0.1 |  |
| 4 | Time | 0 | 100 | % | 0.5 |  |
| 5 | Color | 0 | 100 | % | 0.1 | UI |
| 6 | Tone | 0 | 100 | % | 0.5 |  |
| 7 | Body | 0 | 100 | % | 0.6 |  |
| 8 | Space | 0 | 100 | % | 0.3 |  |
| 9 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Solo`, `Chamber`, `Orchestra`, `Cinematic`, `Warm`, `Bright`


## 9 · Aether Guitar  —  Façonneur

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Body | 0 | 3 | — | 0 |  |
| 1 | Pick | 0 | 100 | % | 0.5 |  |
| 2 | Strum | 0 | 100 | % | 0.4 |  |
| 3 | Mute | 0 | 100 | % | 0.1 |  |
| 4 | Bend | 0 | 100 | % | 0.2 |  |
| 5 | Pickup | 0 | 100 | % | 0.5 |  |
| 6 | Finish | 0 | 3 | — | 0.33 | UI |
| 7 | Tone | 0 | 100 | % | 0.5 |  |
| 8 | Resonance | 0 | 100 | % | 0.7 |  |
| 9 | Space | 0 | 100 | % | 0.25 |  |
| 10 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Acoustic`, `Nylon`, `Clean`, `Crunch`, `Lead`, `Ambient`


## 10 · Aether Amp  —  Effet

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Gain | 0 | 100 | % | 0.3 |  |
| 1 | Bass | 0 | 100 | % | 0.5 |  |
| 2 | Mid | 0 | 100 | % | 0.5 |  |
| 3 | Treble | 0 | 100 | % | 0.6 |  |
| 4 | Presence | 0 | 100 | % | 0.5 |  |
| 5 | Master | 0 | 100 | % | 0.6 |  |
| 6 | Channel | 0 | 2 | — | 0 |  |
| 7 | Mic pos | 0 | 100 | % | 0.4 |  |
| 8 | Mic dist | 0 | 100 | % | 0.4 |  |
| 9 | Boost | 0 | 1 | — | 0 |  |
| 10 | Tremolo | 0 | 1 | — | 0 |  |
| 11 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Clean`, `Crunch`, `Lead`, `Blues`, `Jazz`, `Metal`


## 11 · Aether Bass  —  Effet

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Drive | 0 | 100 | % | 0.25 |  |
| 1 | Comp | 0 | 100 | % | 0.4 |  |
| 2 | Blend | 0 | 100 | % | 0.6 |  |
| 3 | 63 Hz | 0 | 100 | % | 0.55 |  |
| 4 | 250 Hz | 0 | 100 | % | 0.5 |  |
| 5 | 1 kHz | 0 | 100 | % | 0.5 |  |
| 6 | 3 kHz | 0 | 100 | % | 0.45 |  |
| 7 | 8 kHz | 0 | 100 | % | 0.4 |  |
| 8 | Cab | 0 | 2 | — | 0.5 |  |
| 9 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Motown`, `Rock`, `Slap`, `Fuzz`, `Dub`, `Clean DI`


## 12 · Aether Brass  —  Façonneur

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Lips | 0 | 100 | % | 0.5 |  |
| 1 | Bite | 0 | 100 | % | 0.45 |  |
| 2 | Vibrato | 0 | 100 | % | 0.3 |  |
| 3 | Section | 0 | 4 | — | 0.25 |  |
| 4 | Mute | 0 | 4 | — | 0 |  |
| 5 | Tone | 0 | 100 | % | 0.5 |  |
| 6 | Body | 0 | 100 | % | 0.6 |  |
| 7 | Space | 0 | 100 | % | 0.3 |  |
| 8 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Open`, `Muted Jazz`, `Section Stab`, `Fanfare`, `Soft`, `Harmon`


## 13 · Aether Voices  —  Effet

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Vowel X | 0 | 100 | % | 0.5 |  |
| 1 | Vowel Y | 0 | 100 | % | 0.3 |  |
| 2 | Breath | 0 | 100 | % | 0.3 |  |
| 3 | Choir | 0 | 100 | % | 0.5 |  |
| 4 | Vibrato | 0 | 100 | % | 0.35 |  |
| 5 | Tone | 0 | 100 | % | 0.5 |  |
| 6 | Body | 0 | 100 | % | 0.5 |  |
| 7 | Space | 0 | 100 | % | 0.45 |  |
| 8 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Ah`, `Ooh`, `Eeh`, `Ensemble`, `Whisper`, `Angels`


## 14 · Aether Synth  —  Source

| # | Réglage | Min | Max | Unité | Défaut (0..1) | Remarque |
|---|---|---|---|---|---|---|
| 0 | Wave A | 0 | 100 | % | 0.4 |  |
| 1 | Wave B | 0 | 100 | % | 0.4 |  |
| 2 | Detune | 0 | 100 | % | 0.4 |  |
| 3 | Sub | 0 | 100 | % | 0.4 |  |
| 4 | Noise | 0 | 100 | % | 0.4 |  |
| 5 | Unison | 0 | 100 | % | 0.4 |  |
| 6 | Spread | 0 | 100 | % | 0.4 |  |
| 7 | Cutoff | 0 | 100 | % | 0.4 |  |
| 8 | Resonance | 0 | 100 | % | 0.4 |  |
| 9 | Drive | 0 | 100 | % | 0.4 |  |
| 10 | Env amt | 0 | 100 | % | 0.4 |  |
| 11 | Filter | 0 | 3 | — | 0 |  |
| 12 | Attack | 0 | 100 | % | 0.4 |  |
| 13 | Decay | 0 | 100 | % | 0.4 |  |
| 14 | Sustain | 0 | 100 | % | 0.4 |  |
| 15 | Release | 0 | 100 | % | 0.4 |  |
| 16 | LFO1 rate | 0 | 100 | % | 0.4 |  |
| 17 | LFO1 depth | 0 | 100 | % | 0.4 |  |
| 18 | LFO1 shape | 0 | 100 | % | 0.4 |  |
| 19 | LFO1 dest | 0 | 100 | % | 0.4 |  |
| 20 | LFO2 rate | 0 | 100 | % | 0.4 |  |
| 21 | LFO2 depth | 0 | 100 | % | 0.4 |  |
| 22 | Macro 1 | 0 | 100 | % | 0.4 |  |
| 23 | Macro 2 | 0 | 100 | % | 0.4 |  |
| 24 | Macro 3 | 0 | 100 | % | 0.4 |  |
| 25 | Macro 4 | 0 | 100 | % | 0.4 |  |
| 26 | Mode | 0 | 2 | — | 0 |  |
| 27 | Glide | 0 | 100 | % | 0.4 |  |
| 28 | Voices | 0 | 100 | % | 0.4 |  |
| 29 | Bend | 0 | 100 | % | 0.4 |  |
| 30 | Tune | 0 | 100 | % | 0.4 |  |
| 31 | Level | -24 | 6 | dB | 0.8 |  |

Presets : `Pad`, `Lead`, `Bass`, `Pluck`, `Keys`, `FX`


---
## Mappages audio par effet (ce que fait chaque réglage)

### Effets (emplacements de piste ou master)
- **0 Space, 1 Echo, 2 Tone, 3 Grip, 4 Ceiling** : inchangés, déjà dans la bibliothèque et testés (voir 05).
- **10 Amp** : Gain/Channel → étages à lampes sur-échantillonnés x2 (Clean, Crunch, Lead = 1, 1 et 2 étages) ; Bass/Mid/Treble/Presence → correcteur (shelf 120 Hz, cloche 700 Hz, shelf 3,2 kHz, shelf 5,5 kHz) ; Master → saturation de l'étage de puissance et volume ; MicPos → coupe-haut de l'enceinte 6,5 → 3,2 kHz et creux de présence ; MicDist → effet de proximité (grave) ; Boost = +9 dB avant les étages ; Tremolo = 5 Hz, profondeur 50 % ; Level = −24..+6 dB.
- **11 Bass** : Comp → compresseur (seuil −18 dB, rapport 1,5..8) ; Drive → préampli à lampe ; Blend → mélange voie directe (DI) / voie ampli+enceinte ; EQ 63 Hz (shelf), 250 Hz, 1 kHz, 3 kHz (cloches), 8 kHz (shelf), ±12 dB ; Cab → 1×15, 4×10, 8×10 (filtres d'enceinte) ; Level. Compteurs : 0 = réduction de gain (dB), 1 = crête de sortie.
- **13 Voices** (EFFET, pas une source) : Vowel X/Y → carte de voyelles I, U, E, O, A, interpolation inverse-distance des formants F1..F3 ; trois filtres passe-bande en parallèle ; Breath = bruit modulé par l'enveloppe du signal, passé dans les mêmes formants ; Choir = ensemble de 4 voix à retard modulé ; Vibrato = modulation de retard à 5,5 Hz ; Tone = shelf 3 kHz ; Body = mélange du signal sec ; Space = réverbe interne (SpaceReverb) ; Level.

### Façonneurs (un seul par piste, agissent sur le soundfont chargé)
Deux parties : **(a) décalages de générateurs SoundFont** calculés par `computeGens()` (appliqués sur le canal MIDI de la piste, voir 04) et **(b) traitement audio interne** de `ShaperFx`. Les décalages sont **additifs** aux valeurs du soundfont.
| Façonneur | Générateurs (décalages) | Traitement audio interne |
|---|---|---|
| 5 Piano | Felt, Lid, Tone → fréquence de coupure (gen 8) ; Pedal → release (38) et decay (36) | Tone = shelf 3 kHz ; Body = cloche 250 Hz ; Width = largeur stéréo ; Room + Space = réverbe |
| 7 Sax | Reed, Tone → coupure (8) et Q (9) ; Breath → attack (34) ; Vibrato → vibLfoToPitch (6) ; Rate → vibLfoFreq (24) | Growl = saturation + flutter 7 Hz ; Breath = bruit de souffle ; Body = cloche 350 Hz |
| 8 Strings | Pressure, Tone → coupure (8) ; Speed, Time → attack (34) ; Time → release (38) ; Motion → vibrato (6) | Morph = ensemble de 1..6 voix détunées ; Body = cloche 300 Hz ; **Color = UI seulement** |
| 9 Guitar | Pick, Pickup, Tone, Mute → coupure (8) ; Pick → attack (34) ; Mute → decay (36) et release (38) | Body (type) = courbe d'EQ du corps (acoustique, nylon, double cut, simple cut) ; Resonance = cloche 100 Hz ; **Finish = UI seulement**. Niveau « note » : **Strum** = décalage de 0..60 ms entre les notes d'un accord, **Bend** = sensibilité du pitch-bend 2..12 demi-tons (`PerfSet`) |
| 12 Brass | Lips, Tone → coupure (8) ; Bite → attack (34) ; Vibrato → (6) | Bite = saturation ; Section = ensemble de 1..5 voix ; Mute = filtres (Open rien, Straight HP 500 Hz + LP 6,5 kHz, Cup LP 2,4 kHz, Harmon cloche 1,4 kHz +9 dB, Plunger LP 1,7 kHz) |
Tous : Tone, Body, Space, Level (bandeau du bas) sont traités par le même code (indices selon `shaperMap()`).

### Sources (remplacent le soundfont de la piste tant qu'elles sont choisies)
- **6 Organ** (31 réglages) : tirettes Upper 0..8 et Lower 9..17 (0..8 pas de 3 dB), percussion (18..21), vibrato/chorus V1..C3 (22..24, appliqué aux deux claviers à la fois), rotary Stop/Slow/Fast (25), click, tone, drive, space, level. Touches < 60 = clavier Lower, ≥ 60 = Upper. Moteur à roues phoniques additif : le coût dépend du nombre de roues actives, pas du nombre de notes.
- **14 Synth** (32 réglages) : 2 oscillateurs à forme continue (sinus > triangle > scie > carré, PolyBLEP), sub, bruit, unison 1..8 (plafonné à 48 / voix max pour tenir le CPU), filtre SVF (LP, HP, BP, notch), ADSR (commune ampli et filtre), LFO1 (4 formes, 4 destinations), LFO2 → pitch, macros M1 → coupure ±3 octaves, M2 → résonance, M3 → forme de l'oscillateur A, M4 → drive (**assignation fixe en v1**, la matrice modifiable est la v2), modes poly / mono / legato, glide.

### Tiroir « Soundfont edit » (aperçu seulement en v1)
Les curseurs du tiroir (Attack, Decay, Sustain, Release, Cutoff, Resonance, Coarse, Fine, Vib depth, Vib rate, Key low/high) sont des **maquettes** sans effet audio dans cette version. Mappage prévu avec le même mécanisme : Attack → gen 34, Decay → 36, Sustain → 37, Release → 38, Cutoff → 8, Resonance → 9, Coarse → 51, Fine → 52, Vib depth → 6, Vib rate → 24 ; Velocity curve et plage de touches se font dans le moteur de notes.

````

### `03-interface-fidelite-et-performance.md`

- **Chemin :** `/tmp/pkg/aether/03-interface-fidelite-et-performance.md`
- **Taille :** 9019 octets · **lignes :** 78 · **sha256 (12) :** `e87b84177140`
- **Fidélité d'interface et performance** — table des polices par effet.

````markdown
# 03 · Interface : fidélité visuelle, performance, robustesse

## Principe
`fx-window-preview.html` est **la** référence. Il contient tout le dessin (fonction par effet), la logique des commandes et le comportement (rack, ajout, remplacement, avertissement, soundfont, tablette). Tu le **portes** en Jetpack Compose sans rien changer aux valeurs : mêmes coordonnées, mêmes couleurs, mêmes dégradés, mêmes durées. Les rendus `ref-*.png` servent à vérifier.

## Fonctions à porter (dans cet ordre, une par effet)
Utilitaires d'abord : `rr2` (rectangle arrondi), `knob` (potard SVG, voir plus bas), `rail` (bandeau du bas), `mic`, `tag`, `wrapMini` (version carte du rack), `chaikin`/`resample`.
Puis les scènes : `orb`(0 Space) · `echo`(1) · `eq`(2 Tone) · `grip`(3) · `ceil`(4) · `piano`(5) · `organ`(6) · `sax`(7) · `strings`(8) · `guitar`(9) · `amp`(10) · `bass`(11) · `brass`(12) · `voices`(13) · `synth`(14), plus `bg` (ambiance plein cadre des effets 0 à 4).
Chaque scène reçoit `(x, W, H, v, a, t, m)` : contexte, taille, valeurs 0..1, couleur d'accent, temps, `m = 1` pour la **carte du rack** (version réduite) et `0` pour la fenêtre.

## Géométrie
- Fenêtre téléphone : région logique **825 × 343**, contenu **779 × 263** posé à (20, 58). Le bandeau du bas commence à y = 214 (hauteur 71). Les scènes dessinent leur fond sur **toute** la région (de `-OXL`, `-OYL` sur `PWL × PHL`), jamais seulement sur le contenu.
- **Mode tablette** : mêmes dispositions. Scène 1100 × 520, facteur d'agrandissement `KS = 1.28`, contenu centré : `L = (W − 779·KS)/2`, `T = 58 + (H − 58 − 263·KS)/2`, `PWL = W/KS`, `PHL = H/KS`, `OXL = L/KS`, `OYL = T/KS`. Le bandeau s'étire jusqu'en bas : `RH = max(71, PHL − OYL − 214)`. Les écrans tactiles (hit-tests) utilisent la même conversion : `X = (clientX − r.left)/r.width·PWL − OXL`.
- Commandes dans la scène (« hotspots ») : liste `HS[i] = [x, y, rayon, indiceVertical, indiceHorizontal, rectAbsolu?, échelleH?, échelleV?]`. Par défaut le glissement est **relatif** (110 px verticaux = pleine course, 160 px horizontaux). Avec `rectAbsolu` la valeur suit directement la position du doigt dans le rectangle (curseur filtre du Synth, pad XY de Voices). La zone Level du bandeau : x 528..752, y 232..272.

## Identité par instrument (ne pas uniformiser)
| Id | Bandeau (matière) | Potards | Remarque |
|---|---|---|---|
| 5 Piano | laque noire + filet or | ivoire | scène : intérieur de piano à queue |
| 6 Organ | noyer veiné | bakélite brune cannelée | deux claviers de tirettes, cadran V/C, rotary |
| 7 Sax | cuivre perforé | nacre | souffle en particules le long du tube |
| 8 Strings | bois clair | bois clair | construit / dissous, couleur sur toute la roue |
| 9 Guitar | acier brossé | nickel | manche droit, 4 corps |
| 10 Amp | (pas de bandeau) | noirs moletés + liseré ambre | lampes, cône animé, micro mobile |
| 11 Bass | (pas de bandeau) | chrome à anneaux | vumètre, EQ graphique, 3 baffles |
| 12 Brass | velours bordeaux | laiton | sourdines, pistons, section |
| 13 Voices | verre dépoli | verre translucide | pad XY de voyelles, formants |
| 14 Synth | quadrillage LED | plats à LED (point lumineux) | 3 pages Sound / Motion / Voice |
Table des couleurs d'accent et polices (toutes OFL, à placer dans `res/font`) : police de l'interface **Plus Jakarta Sans** ; libellés techniques **Share Tech Mono** ; **Orbitron**, **Space Grotesk**, **Sora**, **JetBrains Mono** selon le tableau.

| Id | Effet | Couleur d'accent | Police du titre |
|---|---|---|---|
| 0 | Æther Space | `#a99ad8` | Space Grotesk |
| 1 | Æther Echo | `#d4b06a` | JetBrains Mono |
| 2 | Prism Tone | `#8fd0ff` | Orbitron |
| 3 | Iron Grip | `#b8c2d4` | Share Tech Mono |
| 4 | Solar Ceiling | `#c9852c` | Sora |
| 5 | Aether Piano | `#e8c98a` | Sora |
| 6 | Aether Organ | `#d9c08a` | Sora |
| 7 | Aether Sax | `#e0a45a` | Sora |
| 8 | Aether Strings | `hsl(36,85%,62%)` | Space Grotesk |
| 9 | Aether Guitar | `#e08a4a` | Space Grotesk |
| 10 | Aether Amp | `#ff9a3c` | Orbitron |
| 11 | Aether Bass | `#f0c060` | Sora |
| 12 | Aether Brass | `#e8c060` | Space Grotesk |
| 13 | Aether Voices | `#c9b8ff` | Space Grotesk |
| 14 | Aether Synth | `#7ad0ff` | Orbitron |

## Potard (`knob`)
Un potard est un SVG 100×100 : piste de 270° (pas de rotation de −135°), arc de valeur en couleur d'accent, anneau, jupe (cannelée selon le style), capuchon dégradé, reflet, repère. **Ne le reconstruis pas à chaque image** : pré-rends **un sprite par matière** et fais pivoter le repère seul, ou mets le potard en cache (`drawWithCache`) et ne le redessine que quand sa valeur change. Styles : `flat` (Synth), `glass` (Voices), `flute` (Organ), `knurl` (Amp), `chrome` (Bass), `round` (autres).

## Comportement du rack
Compteur `n / 6 effects` ; une seule carte instrument, toujours première ; ajouter un instrument alors qu'il y en a un le **remplace** (bandeau « X replaces Y ») ; suppression par appui long puis glissé vers la corbeille, avec bandeau « Undo » 5 s (le remplacement n'a pas d'Undo) ; **Surprise me** (verrous : toucher un libellé de potard le verrouille), **A/B** (comparaison avec transition 420 ms), **Edit** (tiroir soundfont, façonneurs seulement : maquette en v1).

## Robustesse : le bug des animations qui disparaissent
Je n'ai **pas** pu le reproduire. Dans l'aperçu d'origine, l'animation se relançait à la fin de chaque image : **une seule exception dans un dessin l'arrêtait pour toujours, pour tous les effets**. Deux autres causes plausibles : perte de contexte graphique (mémoire) et grosse empreinte mémoire. Règles obligatoires (déjà dans l'aperçu, à reproduire) :
1. **Une seule boucle**, qui se **reprogramme en premier** (avant tout dessin). Chaque dessin est dans un `try/catch` : l'erreur est comptée, le dessin concerné est désactivé après 40 erreurs, **les autres effets continuent**.
2. **Changer de preset ne recrée jamais la surface de dessin** ni ne relance la boucle : on ne met à jour que les valeurs.
3. **Surveillance** : si aucune image n'a été dessinée depuis 1,5 s alors que l'écran est visible, relancer la boucle. Relancer aussi quand l'application revient au premier plan.
4. **Perte de contexte** (`contextlost` / `contextrestored` sur le web ; sur Android : surfaces/bitmaps invalidés) : recréer la ressource et redessiner.
5. **Mode Debug** (bouton dans l'aperçu) : affiche ms de dessin par image, qualité, pixels alloués, pertes de contexte, erreurs. Garde-le dans l'application (réglages développeur).

## Performance (à implémenter, mesurable avec le mode Debug)
- **Qui dessine quoi** : rack visible = seules les **cartes** (version réduite) à ~15 images/s ; fenêtre ouverte = **seulement cet effet** à pleine cadence ; les voisins uniquement pendant le balayage (500 ms) ; les autres surfaces sont **libérées** (2×2 px dans l'aperçu).
- **Résolution adaptative** : qualité initiale `min(2, échelle × densité)`. Si le temps de dessin dépasse 12 ms pendant 30 images → qualité × 0,8 (plancher 0,9) et **mode Lite** ; retour progressif si < 5 ms pendant 300 images.
- **Mode Lite** : plafonds de particules Strings 160 (480 sinon), Sax 40 (90), Brass 30 (70) ; mêmes dessins sinon. À exposer aussi comme réglage manuel.
- **Cache** : fonds, grain du bois, quadrillages, corps de violon/guitare, matières des bandeaux = dessinés une fois (`drawWithCache` / `ImageBitmap`), seules les parties animées sont redessinées.
- **Pas d'allocation dans le dessin** : réutilise `Path`, `Brush`, tableaux de particules (primitifs). Aucune recomposition par image : lis l'horloge uniquement dans la portée de dessin.
- **Mémoire** : une surface plein cadre active à la fois ; ne mets pas les surfaces des effets non visibles en mémoire.

## Correspondance Canvas 2D → Compose
`createLinearGradient/RadialGradient` + `addColorStop` → `Brush.linearGradient/radialGradient` · `arc/ellipse` → `Path.arcTo / addOval` · `clip` → `clipPath` · `setLineDash` → `PathEffect.dashPathEffect` · `globalAlpha` → paramètre `alpha` · `save/restore/translate/scale/rotate` → `withTransform` · **`shadowBlur` n'existe pas en Compose natif** : simule-le par des couches translucides empilées (méthode déjà utilisée pour `FaderThumb.kt`) · `fillText` → `drawText(TextMeasurer)`. Garde chaque constante numérique identique à l'aperçu.

## Icônes
`ic_fx_*.xml` : VectorDrawable 32×32, blanc unique. Conversion vérifiée pixel à pixel contre le SVG d'origine (au pire 35 pixels sur 16 384 s'écartent de plus de 40/255, soit l'anti-crénelage). N'y touche pas.

## Critères de réussite
Pour chaque effet : capture Compose du preset par défaut à la même taille que `ref-NN-*.png`, comparaison côte à côte, aucun écart de couleur, de position ni d'animation visible ; puis les 6 presets contre la feuille `*-presets.png`. Tablette : `ref-tablet-*.png`.

````

### `04-integration-audio-jni.md`

- **Chemin :** `/tmp/pkg/aether/04-integration-audio-jni.md`
- **Taille :** 7393 octets · **lignes :** 62 · **sha256 (12) :** `546d486431d6`
- Intégration audio/JNI (contexte moteur).

````markdown
# 04 · Intégration audio, JNI et Kotlin

Contexte (audit existant) : Oboe (AAudio, float, stéréo), un synthé FluidSynth 2.6.0 avec 16 canaux MIDI (pistes 0..7, Drum = 8, TonicPad = 9), un sampler DrumPad. **Ne change rien d'autre que ce qui est demandé.** Lis **une seule fois** les en-têtes `fluidsynth/synth.h` et `settings.h` de la version vendue pour confirmer les signatures citées (`fluid_synth_nwrite_float`, `fluid_synth_set_gen`, `fluid_synth_pitch_wheel_sens`, réglages `synth.audio-channels`, `synth.audio-groups`).

## Étape 1 · Bibliothèque
Copie les `fx_*.h` à plat dans `app/src/main/cpp/aetherfx/`, `target_include_directories(native-lib PRIVATE aetherfx)`, `minSdk 26`. Compile. Lance les tests hôte (05).

## Étape 2 · Une sortie stéréo par canal
Dans `SoundfontEngine::init`, avant la création du synthé : `synth.audio-channels = 16`, `synth.audio-groups = 16` (canal i vers groupe i), `synth.chorus.active = 0`, `synth.reverb.active = 0`. Remplace le rendu mono-mix par un rendu multi-canaux (`fluid_synth_nwrite_float`) qui remplit des tampons **préalloués à l'ouverture du flux** (taille max = rafale × 4, au moins 2048 images, jamais de `resize` dans le rappel). Le `synth.sample-rate` doit être celui du **flux réel** (`stream->getSampleRate()`).
**Mise à niveau du volume** : fixe CC7 = 127 et CC10 = 64 sur chaque canal ; le volume et la balance passent par les strips. Gain par défaut d'un strip = (108/127)² = 0,723 pour garder le niveau actuel.
**Test obligatoire** : strips à gain 1, effets vides : la somme des groupes doit égaler l'ancien mix à 1e-5 sur un morceau de test. Écris ce test et rapporte le résultat.

## Étape 3 · `FxMixer` dans l'AudioEngine
- Membre `aetherfx::FxMixer mFx;` ; `mFx.prepare(sampleRate, maxBlock)` à l'ouverture du flux (et si le taux change).
- Dans le rappel audio :
```
renderChannels(L[], R[], n)                       // étape 2
for s in 0..9:  interleave(L[s],R[s]) -> scratch
                if (mFx.soundfontPaused(s)) -> scratch = 0          // la source remplace
                mFx.processStrip(s, scratch, n); mix += scratch
sampler.render(scratch, n); mFx.processStrip(10, scratch, n); mix += scratch
mFx.processMaster(mix, n); -> sortie (float, ou int16 avec écrêtage)
```
- **Supprime du rappel** : `std::mutex`/`mRenderMutex`, `LOGI`, `pthread_setschedparam`, tout `resize`. Remplace-les par des compteurs atomiques lus depuis le thread de contrôle (`getXRunCount()`, `calculateLatencyMillis()`). Liste d'abord tous les endroits qui prennent ces verrous et remplace chaque usage concurrent par la file d'événements existante.
- **Supprime l'ancienne chaîne d'effets** (EQ, Goodizer, Reverb, Delay, Chorus, Compressor, Widener, Punch, PadFilter, SoftClipper, `processMasterChain`, setters JNI/Kotlin associés) **seulement après** compilation propre. Liste les fichiers concernés avant de les effacer.
- Le chemin de secours (`renderDirect`/`FallbackSynth`) doit appeler **exactement le même** code de rendu.

## Étape 4 · Notes vers les sources
Quand `mFx.soundfontPaused(strip)` est vrai : les notes de la piste vont à `mFx.noteOn/noteOff/pitchBend(strip, …)` **au lieu de** FluidSynth (même thread audio que la file MIDI existante). Au changement d'instrument, envoyer `fluid_synth_all_notes_off(chan)` et `mFx.allNotesOff(strip)` pour éviter les notes bloquées. La pédale de sustain n'est pas gérée par les sources en v1 (documente-le).

## Étape 5 · Façonneurs : décalages de générateurs
- Calcul : `GenUpdate u = mFx.instrumentGens(strip)` (thread de contrôle) après chaque changement de paramètre, de preset ou d'instrument. `u.gens` = liste (générateur, valeur) à **ajouter** au soundfont ; `u.perf.strumSpreadMs` et `u.perf.bendSemis` = options de notes.
- Application : `fluid_synth_set_gen(synth, canal, gen, valeur)` pour chaque couple, **seulement ceux qui ont changé** (compare avec les dernières valeurs envoyées). **Attention au thread** : avec `synth.threadsafe-api = 0` (recommandé), tout appel FluidSynth doit se faire sur le thread audio : poste une commande `SET_GEN(canal, gen, valeur)` dans la file d'événements existante et exécute-la dans le rappel. Quand l'instrument est retiré ou remplacé par une source : renvoie 0 pour tous les générateurs précédemment modifiés.
- Ré-applique tous les décalages après un changement de preset, de banque ou un « reset all controllers » sur ce canal (non vérifié : vérifie une fois, sur un soundfont réel, que les décalages persistent et agissent sur les notes déjà jouées ; sinon passe par NRPN).
- `strumSpreadMs` : quand plusieurs notes d'une même piste arrivent dans une fenêtre de 8 ms, retarde la n-ième de `n × spread / nbNotes`. `bendSemis` : `fluid_synth_pitch_wheel_sens(synth, canal, semitones)` via la même file.
- Détail des décalages par instrument : 02.

## Étape 6 · JNI et Kotlin (`NativeAudioBridge`)
Supprime les anciens `external fun` d'effets. Ajoute (strip : 0..7 pistes, 8 Drum, 9 TonicPad, 10 sampler, **15 = Master**). Tout est appelé depuis le thread de contrôle sauf mention :
| Fonction | Rôle |
|---|---|
| `fxPrepare(sampleRate, maxBlock)` | à l'ouverture du flux |
| `fxSetInstrument(strip, id): Boolean` | id ≥ 0 ; `-1` = soundfont seul ; refus si strip ≠ 0..7 ou id est un effet |
| `fxSetInstrumentParam(strip, idx, norm)` | atomique, appelable à 60 Hz |
| `fxApplyInstrumentPreset(strip, preset): Boolean` | |
| `fxSetInstrumentBypass(strip, b)` | |
| `fxGetInstrumentId(strip): Int` · `fxSoundfontPaused(strip): Boolean` | |
| `fxSetEffect(strip, slot, id): Boolean` | id = −1 vide ; refus si l'id n'est pas un effet ; 6 slots (0..5), 8 sur le Master |
| `fxSetParam(strip, slot, idx, norm)` · `fxSetBypass(strip, slot, b)` · `fxApplyPreset(strip, slot, preset): Boolean` | |
| `fxGetEffectId(strip, slot): Int` · `fxGetMeter(strip, slot, i): Float` | vumètres (30 Hz côté UI) |
| `stripSetGain(strip, lin)` · `stripSetPan(strip, −1..1)` · `stripSetMute(strip, b)` · `stripPeak(strip, canal): Float` | solo géré en Kotlin (mute des autres) |
| `fxInstrumentGens(strip)` | renvoie le tableau (gen, valeur) + strum + bend pour l'étape 5 |
| `fxMatchShaper(fxId, nomPreset, programmeGM, banque, notePlusBasse, notePlusHaute): Int` | 0 = Ok, 1 = Weak, 2 = Mismatch (`fx_match.h`) |
| `fxCollectGarbage()` | toutes les ~500 ms et après chaque `fxSetEffect`/`fxSetInstrument` |
Côté audio (pas d'appel Kotlin) : `mFx.noteOn/noteOff/pitchBend/allNotesOff`, `processStrip`, `processMaster`.
Place `FxRegistry.kt` dans le projet (adapte le `package`). Les valeurs de l'interface sont toujours normalisées 0..1 ; `FxRegistry.toReal` convertit pour l'affichage.

## Étape 7 · Latence et sécurité
Le Ceiling du Master a une anticipation de 48 échantillons à 48 kHz (1 ms), déjà compensée dans l'effet : n'ajoute rien. Tous les effets et instruments sont sans allocation ni verrou dans `process`. L'ajout, le retrait et le changement d'effet passent par un échange de pointeur atomique ; l'ancien effet est détruit par `collectGarbage()` après 3 blocs.

## Étape 8 · Limites de ressources (à respecter)
Synth : unison plafonné à `48 / voix max` ; Organ : coût proportionnel aux roues actives. Echo : ~512 Ko de ligne à retard par instance. Ne pré-alloue rien dans le rappel.

````

### `05-tests-et-validation.md`

- **Chemin :** `/tmp/pkg/aether/05-tests-et-validation.md`
- **Taille :** 3779 octets · **lignes :** 47 · **sha256 (12) :** `eaaecc7b6030`
- Tests et validation sur appareil.

````markdown
# 05 · Tests et validation

## Commandes (dans ce dossier, g++ ≥ 9)
```
make test      # compile et lance fx_test (26 contrôles) puis fx_test2 (90 contrôles)
make asan      # idem sous AddressSanitizer + UBSan
make tsan      # idem sous ThreadSanitizer
```
Résultat attendu : `26 checks, 0 failed` et `90 checks, 0 failed`, aucune alerte de sanitizer.

## Ce que couvrent les tests
- **Effets 0 à 4** (fx_test) : EQ ±6 dB mesuré, courbe statique du compresseur, plafond du maximizer avec et sans anticipation, durée de queue de réverbe, retards à l'échantillon près, fondu au bypass, échange d'effets pendant le rendu (stress multi-threads).
- **Amp, Bass, Voices** (fx_test2) : distorsion croissante avec le gain et le canal, position du micro, contrôle des graves, trémolo, vumètre de compression, gain de l'EQ graphique, voie directe transparente, cabinets différents, formants des voyelles, 120 jeux de paramètres aléatoires finis et bornés.
- **Façonneurs** : décalages dans la plage, sens des effets (feutre plus mou = plus sombre, étouffement = release plus court), sourdine harmon, avertissement de soundfont (6 cas).
- **Organ** : fondamentale pure à 40 dB, loi des tirettes (3 dB par pas : mesuré 12 dB sur 4 pas), pied de 16, percussion qui décroît à déclenchement unique, modulation du rotary, 10 touches bornées, relâchement.
- **Synth** : justesse (440 Hz à ±1), repliement < −35 dB, pente du filtre, passe-haut, durée d'attaque, constante de relâchement (0,37 à t = release), polyphonie et vol de voix, mode mono, 40 jeux aléatoires.
- **Piste** : un seul instrument, sources vs façonneurs, interdits (instrument sur le Master, instrument dans un emplacement d'effet, effet dans l'emplacement instrument), 8 emplacements Master, échange concurrent instrument/effets.
- **Registre** : chaque preset a une valeur par paramètre, toutes entre 0 et 1, 15 entrées, répartition 8 effets / 5 façonneurs / 2 sources.

## Mesures de coût (ordinateur de bureau, -O2, 10 s d'audio stéréo à 48 kHz ; téléphone : compte 3 à 6 fois plus)
| Module | % d'un cœur |
|---|---|
| Space | 0,51 | 
| Echo | 0,36 |
| Tone | 0,16 |
| Grip | 0,18 |
| Ceiling (piste / Master avec anticipation) | 0,39 / 0,81 |
| Amp | 2,47 |
| Bass | 1,50 |
| Voices | 1,54 |
| Piano (façonneur) | 0,76 |
| Strings (façonneur) | 0,86 |
| Brass (façonneur) | 1,82 |
| Organ (8 touches) | 2,41 |
| Synth (8 voix, preset Pad) | 5,17 |
Le Synth est le module le plus coûteux : surveille-le sur appareil (budget : voix max 8 et unison ≤ 4 pour les téléphones modestes).

## Liste de contrôle sur appareil (Étape 8)
1. 8 pistes + 2 réverbes + 1 Amp : 10 minutes, **0 xrun**.
2. Changer d'instrument, d'effet et de preset **pendant le jeu** : aucun clic ni blocage ; notes tenues sans note bloquée.
3. Passer Organ → soundfont → Organ : le soundfont de la piste est identique à l'origine.
4. Façonneur sur un mauvais soundfont : le bandeau rouge s'affiche, rien n'est bloqué.
5. Interface : mode Debug, 10 minutes d'utilisation, **aucune** disparition d'animation ; si elle se produit, relève les compteurs du mode Debug et envoie-les.
6. Mémoire : une seule surface plein cadre active, pas de croissance au fil des ouvertures/fermetures.

## Limites connues
Les amplis sont des simulations (le son est celui d'un ampli simulé). Les articulations de guitare (palm mute, glissés) et de sax (growl) sont des approximations à partir des échantillons du soundfont : le soundfont doit les contenir pour qu'elles sonnent juste. Le Synth ne propose pas encore de matrice de modulation éditable (macros à assignation fixe). Le tiroir « Soundfont edit » est une maquette. Qualité sonore subjective et CPU sur téléphone : non mesurés.

````

---

## 5. Icônes des effets — 15 fichiers vectoriels

Même famille : grille cohérente, tracé net. Ce sont les icônes du rack et de la
page d'ajout. Les `.svg` sources sont dans le même zip.

### `ic_fx_space.xml (effet 0)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_space.xml`
- **Taille :** 1138 octets · **lignes :** 20 · **sha256 (12) :** `88046eaeabaa`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M10,16A6,6 0 1 0 22,16A6,6 0 1 0 10,16Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <group android:rotation="-22" android:pivotX="16" android:pivotY="16">
        <path android:pathData="M2.5,16A13.5,4.6 0 1 0 29.5,16A13.5,4.6 0 1 0 2.5,16Z"
            android:fillColor="#00000000"
            android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
            android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    </group>
    <path android:pathData="M4.1,6A0.9,0.9 0 1 0 5.9,6A0.9,0.9 0 1 0 4.1,6Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
    <path android:pathData="M26.6,26A0.9,0.9 0 1 0 28.4,26A0.9,0.9 0 1 0 26.6,26Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
</vector>

````

### `ic_fx_echo.xml (effet 1)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_echo.xml`
- **Taille :** 1579 octets · **lignes :** 24 · **sha256 (12) :** `e55ccd8a97ec`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M3.6,16A12.4,12.4 0 1 0 28.4,16A12.4,12.4 0 1 0 3.6,16Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M13.8,16A2.2,2.2 0 1 0 18.2,16A2.2,2.2 0 1 0 13.8,16Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M13,9.2A3,3 0 1 0 19,9.2A3,3 0 1 0 13,9.2Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M6.9,19.5A3,3 0 1 0 12.9,19.5A3,3 0 1 0 6.9,19.5Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M19.1,19.5A3,3 0 1 0 25.1,19.5A3,3 0 1 0 19.1,19.5Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_tone.xml (effet 2)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_tone.xml`
- **Taille :** 1484 octets · **lignes :** 20 · **sha256 (12) :** `f8397bf57670`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M7 4v24M16 4v24M25 4v24"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="0.45"/>
    <path android:pathData="M5.6,17H8.4A1.2,1.2 0 0 1 9.6,18.2V19.8A1.2,1.2 0 0 1 8.4,21H5.6A1.2,1.2 0 0 1 4.4,19.8V18.2A1.2,1.2 0 0 1 5.6,17Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M14.6,8.5H17.4A1.2,1.2 0 0 1 18.6,9.7V11.3A1.2,1.2 0 0 1 17.4,12.5H14.6A1.2,1.2 0 0 1 13.4,11.3V9.7A1.2,1.2 0 0 1 14.6,8.5Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M23.6,13H26.4A1.2,1.2 0 0 1 27.6,14.2V15.8A1.2,1.2 0 0 1 26.4,17H23.6A1.2,1.2 0 0 1 22.4,15.8V14.2A1.2,1.2 0 0 1 23.6,13Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_grip.xml (effet 3)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_grip.xml`
- **Taille :** 1281 octets · **lignes :** 21 · **sha256 (12) :** `c6eb526d43bc`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M4 24a12 12 0 0124 0z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M7.4 17.2l1.8 1.1M12 12.4l1 1.8M20 12.4l-1 1.8M24.6 17.2l-1.8 1.1"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.2"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M16 24l6-8.6"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M14.4,24A1.6,1.6 0 1 0 17.6,24A1.6,1.6 0 1 0 14.4,24Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_ceiling.xml (effet 4)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_ceiling.xml`
- **Taille :** 1026 octets · **lignes :** 16 · **sha256 (12) :** `46be5f5afdb9`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M3 5h26"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M11,18A5,5 0 1 0 21,18A5,5 0 1 0 11,18Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M16 10.4V8.4M9.6 12.2l-1.5-1.5M22.4 12.2l1.5-1.5M8.4 18H6M23.6 18H26M10 24l-1.6 1.6M22 24l1.6 1.6M16 25.6v2.4"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_piano.xml (effet 5)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_piano.xml`
- **Taille :** 1907 octets · **lignes :** 27 · **sha256 (12) :** `df352989fdda`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M6.5,6H25.5A2.5,2.5 0 0 1 28,8.5V23.5A2.5,2.5 0 0 1 25.5,26H6.5A2.5,2.5 0 0 1 4,23.5V8.5A2.5,2.5 0 0 1 6.5,6Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M10 18v8M16 18v8M22 18v8"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.3"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="0.8"/>
    <path android:pathData="M9.1,6H10.9A0.8,0.8 0 0 1 11.7,6.8V17.7A0.8,0.8 0 0 1 10.9,18.5H9.1A0.8,0.8 0 0 1 8.3,17.7V6.8A0.8,0.8 0 0 1 9.1,6Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M15.1,6H16.9A0.8,0.8 0 0 1 17.7,6.8V17.7A0.8,0.8 0 0 1 16.9,18.5H15.1A0.8,0.8 0 0 1 14.3,17.7V6.8A0.8,0.8 0 0 1 15.1,6Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M21.1,6H22.9A0.8,0.8 0 0 1 23.7,6.8V17.7A0.8,0.8 0 0 1 22.9,18.5H21.1A0.8,0.8 0 0 1 20.3,17.7V6.8A0.8,0.8 0 0 1 21.1,6Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_organ.xml (effet 6)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_organ.xml`
- **Taille :** 2087 octets · **lignes :** 28 · **sha256 (12) :** `4b34ec44945d`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M4.5,3.5H27.5A1.5,1.5 0 0 1 29,5V9.5A1.5,1.5 0 0 1 27.5,11H4.5A1.5,1.5 0 0 1 3,9.5V5A1.5,1.5 0 0 1 4.5,3.5Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M6.5 5.4v3M9 5.4v3.8M11.5 5.4v2.4M14 5.4v3.8M16.5 5.4v2M19 5.4v3.4M21.5 5.4v1.8M24 5.4v2.8M26.5 5.4v2"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.1"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M4,13H28A1,1 0 0 1 29,14V18A1,1 0 0 1 28,19H4A1,1 0 0 1 3,18V14A1,1 0 0 1 4,13Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M4,20.5H28A1,1 0 0 1 29,21.5V25.5A1,1 0 0 1 28,26.5H4A1,1 0 0 1 3,25.5V21.5A1,1 0 0 1 4,20.5Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M6.7 13v6M10.4 13v6M14.1 13v6M17.9 13v6M21.6 13v6M25.3 13v6M6.7 20.5v6M10.4 20.5v6M14.1 20.5v6M17.9 20.5v6M21.6 20.5v6M25.3 20.5v6"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="0.6"/>
    <path android:pathData="M6 26.5v3M26 26.5v3"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_sax.xml (effet 7)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_sax.xml`
- **Taille :** 1843 octets · **lignes :** 28 · **sha256 (12) :** `1cd3e2caf8a3`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M5.4 3.2l5.2 2.4c1.6.8 2.4 2.2 2.4 4"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="2"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M13 9.6V21c0 4.3 3 7 6.8 7s6.4-2.6 6.4-6v-4.6"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="3"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M22.2,15.6A4,1.7 0 1 0 30.2,15.6A4,1.7 0 1 0 22.2,15.6Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M15.8,12.5A1,1 0 1 0 17.8,12.5A1,1 0 1 0 15.8,12.5Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M15.8,16.5A1,1 0 1 0 17.8,16.5A1,1 0 1 0 15.8,16.5Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M15.8,20.5A1,1 0 1 0 17.8,20.5A1,1 0 1 0 15.8,20.5Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_strings.xml (effet 8)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_strings.xml`
- **Taille :** 1643 octets · **lignes :** 24 · **sha256 (12) :** `4baa1f529555`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M14.6,2.7A1.4,1.4 0 1 0 17.4,2.7A1.4,1.4 0 1 0 14.6,2.7Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M16 4.1v4.3"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M12.3 10.6c0-1.4 1.6-2.2 3.7-2.2s3.7.8 3.7 2.2c0 1.5-1 2.5-1.3 3.6 1.4.8 3.3 2.2 3.3 5.2 0 4-2.7 8.2-5.7 8.2s-5.7-4.2-5.7-8.2c0-3 1.9-4.4 3.3-5.2-.3-1.1-1.3-2.1-1.3-3.6z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M13.2 17.2c.7 1 .7 2.6 0 3.6M18.8 17.2c-.7 1-.7 2.6 0 3.6M14 22h4"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M4 28L28 13"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="0.8"/>
</vector>

````

### `ic_fx_guitar.xml (effet 9)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_guitar.xml`
- **Taille :** 1667 octets · **lignes :** 24 · **sha256 (12) :** `ed4b80b2b883`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M15.1,1.4H16.9A0.8,0.8 0 0 1 17.7,2.2V4A0.8,0.8 0 0 1 16.9,4.8H15.1A0.8,0.8 0 0 1 14.3,4V2.2A0.8,0.8 0 0 1 15.1,1.4Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M16 4.8v5.4"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M12 12.6c0-1.5 1.7-2.4 4-2.4s4 .9 4 2.4c0 1.4-.7 2.4-1 3.5 1.9.6 3.6 2.4 3.6 5.1 0 3.8-3 7.4-6.6 7.4S9.4 25 9.4 21.2c0-2.7 1.7-4.5 3.6-5.1-.3-1.1-1-2.1-1-3.5z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M14,20A2,2 0 1 0 18,20A2,2 0 1 0 14,20Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M13.3 25.4h5.4"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_amp.xml (effet 10)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_amp.xml`
- **Taille :** 2618 octets · **lignes :** 39 · **sha256 (12) :** `91aaf312e951`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M5.5,7H26.5A2.5,2.5 0 0 1 29,9.5V22.5A2.5,2.5 0 0 1 26.5,25H5.5A2.5,2.5 0 0 1 3,22.5V9.5A2.5,2.5 0 0 1 5.5,7Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M3 12.5h26"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M6.4,9.8A1.1,1.1 0 1 0 8.6,9.8A1.1,1.1 0 1 0 6.4,9.8Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M10.9,9.8A1.1,1.1 0 1 0 13.1,9.8A1.1,1.1 0 1 0 10.9,9.8Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M15.4,9.8A1.1,1.1 0 1 0 17.6,9.8A1.1,1.1 0 1 0 15.4,9.8Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M19.9,9.8A1.1,1.1 0 1 0 22.1,9.8A1.1,1.1 0 1 0 19.9,9.8Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M23.9,9.8A1.1,1.1 0 1 0 26.1,9.8A1.1,1.1 0 1 0 23.9,9.8Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M11.6,19A4.4,4.4 0 1 0 20.4,19A4.4,4.4 0 1 0 11.6,19Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M14.6,19A1.4,1.4 0 1 0 17.4,19A1.4,1.4 0 1 0 14.6,19Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
</vector>

````

### `ic_fx_bass.xml (effet 11)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_bass.xml`
- **Taille :** 1404 octets · **lignes :** 22 · **sha256 (12) :** `218b1bd04041`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M8.5,3H23.5A2.5,2.5 0 0 1 26,5.5V26.5A2.5,2.5 0 0 1 23.5,29H8.5A2.5,2.5 0 0 1 6,26.5V5.5A2.5,2.5 0 0 1 8.5,3Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M11.7,11A4.3,4.3 0 1 0 20.3,11A4.3,4.3 0 1 0 11.7,11Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M14.8,11A1.2,1.2 0 1 0 17.2,11A1.2,1.2 0 1 0 14.8,11Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
    <path android:pathData="M11.7,22.4A4.3,4.3 0 1 0 20.3,22.4A4.3,4.3 0 1 0 11.7,22.4Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M14.8,22.4A1.2,1.2 0 1 0 17.2,22.4A1.2,1.2 0 1 0 14.8,22.4Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
</vector>

````

### `ic_fx_brass.xml (effet 12)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_brass.xml`
- **Taille :** 1992 octets · **lignes :** 32 · **sha256 (12) :** `0c473ef15a49`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M3.5 16.5H21"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M21 16.5l7.5-6v13z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M8 16.5v-5.5h11v5.5"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M12 8.8V11M15 8.8V11M18 8.8V11"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M11,7.8A1,1 0 1 0 13,7.8A1,1 0 1 0 11,7.8Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M14,7.8A1,1 0 1 0 16,7.8A1,1 0 1 0 14,7.8Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M17,7.8A1,1 0 1 0 19,7.8A1,1 0 1 0 17,7.8Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
</vector>

````

### `ic_fx_voices.xml (effet 13)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_voices.xml`
- **Taille :** 1009 octets · **lignes :** 16 · **sha256 (12) :** `60027d775fb1`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M16,3H16A5,5 0 0 1 21,8V12A5,5 0 0 1 16,17H16A5,5 0 0 1 11,12V8A5,5 0 0 1 16,3Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M7 13c0 5 4 8 9 8s9-3 9-8M16 21v7M11 28h10"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M13 8h6M13 11h6"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="0.6"/>
</vector>

````

### `ic_fx_synth.xml (effet 14)`

- **Chemin :** `/tmp/pkg/aether/ic_fx_synth.xml`
- **Taille :** 2773 octets · **lignes :** 44 · **sha256 (12) :** `31b620e66361`

````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp" android:height="32dp"
    android:viewportWidth="32" android:viewportHeight="32">
    <path android:pathData="M4.5,7H27.5A2.5,2.5 0 0 1 30,9.5V22.5A2.5,2.5 0 0 1 27.5,25H4.5A2.5,2.5 0 0 1 2,22.5V9.5A2.5,2.5 0 0 1 4.5,7Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M2 15.5h28"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M5.7,11.2A1.3,1.3 0 1 0 8.3,11.2A1.3,1.3 0 1 0 5.7,11.2Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M10.3,11.2A1.3,1.3 0 1 0 12.9,11.2A1.3,1.3 0 1 0 10.3,11.2Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M14.9,11.2A1.3,1.3 0 1 0 17.5,11.2A1.3,1.3 0 1 0 14.9,11.2Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M21.9,9.2H26.6A0.9,0.9 0 0 1 27.5,10.1V12.3A0.9,0.9 0 0 1 26.6,13.2H21.9A0.9,0.9 0 0 1 21,12.3V10.1A0.9,0.9 0 0 1 21.9,9.2Z"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="1"/>
    <path android:pathData="M7 20.7v4.3M11.7 20.7v4.3M16.4 15.5v9.5M21.1 20.7v4.3M25.8 20.7v4.3"
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="1.1"
        android:strokeLineCap="round" android:strokeLineJoin="round" android:strokeAlpha="0.7"/>
    <path android:pathData="M5.5,15.5H8.3V20.7H5.5Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
    <path android:pathData="M10.1,15.5H12.9V20.7H10.1Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
    <path android:pathData="M19.7,15.5H22.5V20.7H19.7Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
    <path android:pathData="M24.3,15.5H27.1V20.7H24.3Z"
        android:fillColor="#FFFFFFFF"
        android:fillAlpha="1"/>
</vector>

````

---

## 6. Scènes Kotlin — rendu validé des 15 effets

Ces fichiers portent le dessin de chaque scène et sont **validés** contre les
références : les reprendre, pas réinventer.

| Fichier | Contenu |
|---|---|
| `FxScenes.kt` | Effets 0–4 (Space, Echo, Tone, Grip, Ceiling) + `FxStyle`, `bg()`, `rail()`, `FxKnob`, `drawFxScene` |
| `FxInstrumentScenes.kt` | Instruments 5–14 + `FxSceneState` (état d'animation persistant) |
| `FxFonts.kt` | Table des polices par effet |
| `FxSurface.kt` | `Modifier.raised` — l'ombre peinte en `drawBehind` (§0 règle 5) |

Point d'entrée unique : `DrawScope.drawFxScene(effectId, values, t, tm, st)`.
`values` sont **normalisées 0..1**, `t` est le temps en secondes, `st` porte l'état
d'animation (touches du piano, tirettes de l'orgue, particules des cordes…) pour
qu'une recomposition ne le remette pas à zéro.

### `FxScenes.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/fx/FxScenes.kt`
- **Taille :** 49088 octets · **lignes :** 949 · **sha256 (12) :** `3aecee71af1c`
- Effets 0–4, style, fond, rail, potard.

````kotlin
package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometry and colours of the FX window, ported unchanged from
 * `fx-window-preview.html`: logical phone region 825x343, content 779x263 at
 * (20, 58), bottom rail from y = 214. One canvas px is one dp.
 */
object FxStyle {
    const val PWL = 825f
    const val PHL = 343f
    const val OXL = 20f
    const val OYL = 58f
    const val RAIL_Y = 214f

    private val accents = mapOf(
        0 to Color(0xFFA99AD8), 1 to Color(0xFFD4B06A), 2 to Color(0xFF8FD0FF),
        3 to Color(0xFFB8C2D4), 4 to Color(0xFFC9852C), 5 to Color(0xFFE8C98A),
        6 to Color(0xFFD9C08A), 7 to Color(0xFFE0A45A), 8 to Color(0xFFECB05C),
        9 to Color(0xFFE08A4A), 10 to Color(0xFFFF9A3C), 11 to Color(0xFFF0C060),
        12 to Color(0xFFE8C060), 13 to Color(0xFFC9B8FF), 14 to Color(0xFF7AD0FF),
    )

    fun accent(id: Int): Color = accents[id] ?: Color(0xFFA99AD8)

    /** Rail material per instrument id, from `RS` in the preview. */
    data class Rail(val bgTop: Color, val bgBottom: Color, val line: Color, val tx: Color, val pattern: String)

    /**
     * True when the instrument draws the bottom rail. `RS` in the preview has no
     * entry for 10 (amp) and 11 (bass): they carry a regular "Level" knob
     * (FxKnobBoxes) and fill the panel to the bottom, so calling `rail()` for
     * every id >= 5 drew a second, wrong LEVEL slider under those two.
     */
    fun hasRail(id: Int): Boolean = id == 5 || id == 6 || id == 7 || id == 8 ||
        id == 9 || id == 12 || id == 13 || id == 14

    /**
     * Parameter index driven by the rail's LEVEL slider, from the
     * `rail(x, v[n], id)` calls in the preview. It differs per instrument: the
     * slider is not always the last parameter (piano uses v[8], organ v[30]…).
     */
    fun railLevelIndex(id: Int): Int = when (id) {
        6 -> 30
        8 -> 9
        9 -> 10
        14 -> 31
        else -> 8
    }

    fun rail(id: Int): Rail = when (id) {
        6 -> Rail(Color(0xFF4A2A16), Color(0xFF1A0D07), Color(0xFFD9C08A), Color(0xFFF3E6CC), "wood")
        7 -> Rail(Color(0xFF2A1810), Color(0xFF0E0806), Color(0xFFE0A45A), Color(0xFFF4E4D0), "copper")
        8 -> Rail(Color(0xFF4A3A20), Color(0xFF1C1408), Color(0xFFD9A05B), Color(0xFFF0E2C8), "wood")
        9 -> Rail(Color(0xFF2A2E36), Color(0xFF0A0B0E), Color(0xFFC0C8D0), Color(0xFFDFE4EA), "steel")
        12 -> Rail(Color(0xFF4A0E1C), Color(0xFF14060A), Color(0xFFE8C060), Color(0xFFF4E4C0), "velvet")
        13 -> Rail(Color(0x22FFFFFF), Color(0x08FFFFFF), Color(0xFFC9B8FF), Color(0xFFE8E4FF), "glass")
        14 -> Rail(Color(0xFF0A1220), Color(0xFF04060C), Color(0xFF7AD0FF), Color(0xFFA8D8F0), "led")
        else -> Rail(Color(0xFF1F1F26), Color(0xFF07070A), Color(0xFFE8C98A), Color(0xFFE8E0D0), "lacquer")
    }
}

/** Knob geometry as declared by `AB(...)` in the preview, per effect id. */
data class FxKnobSpec(val param: Int, val x: Float, val y: Float, val size: Float)

private val knobLayout: Map<Int, List<FxKnobSpec>> = buildMap {
    put(0, (0..6).map { k ->
        val a = Math.toRadians(-90.0 + k * 360.0 / 7.0)
        FxKnobSpec(k, (389 + 285 * cos(a)).toFloat(), (114 + 80 * sin(a)).toFloat(), 54f)
    })
    put(1, listOf(FxKnobSpec(8, 650f, 70f, 56f), FxKnobSpec(9, 735f, 70f, 56f),
                  FxKnobSpec(10, 692f, 188f, 56f), FxKnobSpec(11, 95f, 205f, 52f)))
    put(2, listOf(FxKnobSpec(1, 66f, 126f, 100f), FxKnobSpec(3, 330f, 214f, 60f),
                  FxKnobSpec(6, 664f, 122f, 78f), FxKnobSpec(4, 430f, 216f, 44f)))
    put(3, listOf(FxKnobSpec(1, 628f, 78f, 78f), FxKnobSpec(4, 725f, 64f, 50f),
                  FxKnobSpec(2, 598f, 200f, 54f), FxKnobSpec(3, 670f, 200f, 54f),
                  FxKnobSpec(5, 742f, 200f, 54f)))
    put(4, listOf(FxKnobSpec(0, 125f, 128f, 136f), FxKnobSpec(1, 262f, 78f, 76f),
                  FxKnobSpec(2, 262f, 196f, 50f), FxKnobSpec(3, 340f, 196f, 50f),
                  FxKnobSpec(4, 340f, 70f, 50f), FxKnobSpec(5, 735f, 128f, 62f)))
    // Effects 5..14, from the `AB(id,param,x,y,z)` calls in the reference BODY table.
    // Positions are window coordinates (the -OXL/-OYL translate), sizes are the
    // knob diameter `z` — not the wider label box from fx_layout_spec.json.
    put(5, listOf(FxKnobSpec(5, 150f, 252f, 42f), FxKnobSpec(6, 290f, 252f, 42f), FxKnobSpec(7, 430f, 252f, 42f)))
    put(6, listOf(FxKnobSpec(26, 742f, 106f, 40f), FxKnobSpec(27, 150f, 252f, 42f),
                  FxKnobSpec(28, 290f, 252f, 42f), FxKnobSpec(29, 430f, 252f, 42f)))
    put(7, listOf(FxKnobSpec(5, 150f, 252f, 42f), FxKnobSpec(6, 290f, 252f, 42f), FxKnobSpec(7, 430f, 252f, 42f)))
    put(8, listOf(FxKnobSpec(5, 96f, 176f, 40f), FxKnobSpec(4, 212f, 176f, 40f), FxKnobSpec(3, 389f, 168f, 62f),
                  FxKnobSpec(2, 566f, 176f, 40f), FxKnobSpec(0, 680f, 176f, 40f), FxKnobSpec(1, 756f, 176f, 40f),
                  FxKnobSpec(6, 150f, 252f, 42f), FxKnobSpec(7, 290f, 252f, 42f), FxKnobSpec(8, 430f, 252f, 42f)))
    put(9, listOf(FxKnobSpec(7, 150f, 252f, 42f), FxKnobSpec(8, 290f, 252f, 42f), FxKnobSpec(9, 430f, 252f, 42f)))
    // Effect 10 (Aether Amp): AB(10,j,70+j*80,86,46) for j=0..5, plus Level (param 11)
    // at AB(10,11,730,86,46). Params 6..10 are scene hotspots, not DOM knobs.
    put(10, (0..5).map { j -> FxKnobSpec(j, 70f + j * 80f, 86f, 46f) } + FxKnobSpec(11, 730f, 86f, 46f))
    put(11, listOf(FxKnobSpec(0, 330f, 72f, 44f), FxKnobSpec(1, 400f, 72f, 44f),
                   FxKnobSpec(2, 470f, 72f, 44f), FxKnobSpec(9, 730f, 206f, 46f)))
    put(12, listOf(FxKnobSpec(5, 150f, 252f, 42f), FxKnobSpec(6, 290f, 252f, 42f), FxKnobSpec(7, 430f, 252f, 42f)))
    put(13, listOf(FxKnobSpec(2, 70f, 56f, 44f), FxKnobSpec(3, 70f, 150f, 44f), FxKnobSpec(4, 700f, 60f, 44f),
                   FxKnobSpec(5, 150f, 252f, 42f), FxKnobSpec(6, 290f, 252f, 42f), FxKnobSpec(7, 430f, 252f, 42f)))
    put(14, listOf(FxKnobSpec(0, 45f, 102f, 40f), FxKnobSpec(1, 125f, 102f, 40f), FxKnobSpec(2, 205f, 102f, 40f),
                   FxKnobSpec(3, 45f, 170f, 40f), FxKnobSpec(4, 125f, 170f, 40f), FxKnobSpec(5, 205f, 170f, 40f),
                   FxKnobSpec(9, 630f, 64f, 40f), FxKnobSpec(10, 700f, 64f, 40f), FxKnobSpec(6, 764f, 64f, 40f),
                   FxKnobSpec(16, 50f, 128f, 40f), FxKnobSpec(17, 130f, 128f, 40f),
                   FxKnobSpec(20, 290f, 128f, 40f), FxKnobSpec(21, 370f, 128f, 40f),
                   FxKnobSpec(25, 650f, 176f, 40f), FxKnobSpec(27, 60f, 104f, 44f), FxKnobSpec(28, 140f, 104f, 44f),
                   FxKnobSpec(29, 220f, 104f, 44f), FxKnobSpec(30, 300f, 104f, 44f),
                   FxKnobSpec(22, 150f, 252f, 42f), FxKnobSpec(23, 290f, 252f, 42f), FxKnobSpec(24, 430f, 252f, 42f)))
}

fun knobsFor(effectId: Int): List<FxKnobSpec> = knobLayout[effectId] ?: emptyList()

private const val TAU = 6.2831855f

private fun dash(a: Float, b: Float) = PathEffect.dashPathEffect(floatArrayOf(a, b))

private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m)
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float,
                           color: Color, width: Float, effect: PathEffect?) {
    val path = Path()
    for (i in 0..64) {
        val a = i * TAU / 64
        val x = rx * cos(a); val y = ry * sin(a)
        val px = cx + x * cos(rot) - y * sin(rot)
        val py = cy + x * sin(rot) + y * cos(rot)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color = color, style = Stroke(width = width, pathEffect = effect))
}

private fun DrawScope.label(tm: TextMeasurer, text: String, cx: Float, cy: Float,
                            color: Color, sp: Float, bold: Boolean = false,
                            align: LabelAlign = LabelAlign.CENTER) {
    // Measured at density 1 on purpose: the scene canvas is already scaled by the
    // display density (`scale(d, d)` in FxOpenPanel/RackCard), so a `sp` resolved at
    // the real density was multiplied by it a second time — every scene label came
    // out 2.75x too large on a 440dpi device. At density 1 the `sp` is 1:1 with the
    // scene's logical units, which is what the preview's `9px` means.
    val layout = tm.measure(text, style = TextStyle(color = color, fontSize = sp.sp,
        fontFamily = FxFonts.mono,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        density = Density(1f, 1f))
    val x = when (align) {
        LabelAlign.CENTER -> cx - layout.size.width / 2f
        LabelAlign.LEFT -> cx
        LabelAlign.RIGHT -> cx - layout.size.width
    }
    drawText(layout, topLeft = Offset(x, cy - layout.size.height / 2f))
}

/** `textAlign` of a canvas `fillText`. */
enum class LabelAlign { LEFT, CENTER, RIGHT }

// ---------------------------------------------------------------------------
// Scenes, ported 1:1 from the preview functions of the same name.
// ---------------------------------------------------------------------------

/**
 * Per-effect panel background, from the `.xN` rules of `fx-window-preview.html`.
 * The effects 0..4 are painted by `bg()`; the instruments (5..14) are painted by
 * their own scene functions. `.x2` is declared twice in the reference (a slate
 * grid at line 54, a red amp face at line 104) and the later rule wins in CSS:
 * ref-02-prism-tone.png samples (180,26,43), so the red face is the one to keep.
 */
internal val PANEL_BG: Map<Int, Color> = mapOf(
    0 to Color(0xFF0F1120),
    1 to Color(0xFF1F1B14),
    2 to Color(0xFF93111F),
    3 to Color(0xFF23262C),
    4 to Color(0xFFD6CDB9),
    10 to Color(0xFF0A0806),
    11 to Color(0xFF0A0806),
    12 to Color(0xFF0A0806),
    13 to Color(0xFF05040C),
    14 to Color(0xFF04060C),
)

/**
 * Per-effect text colour, from the `color` of the same `.xN` rule that gives the
 * panel its background. The port had `#DFE4EE` / `#E6EAF3` hard-coded for every
 * readout, so solar (`.x4`, background `#efe9db`) painted cream text on cream and
 * the page looked empty. `.x4` declares `#2a241b`; ids 7/8/9 have no rule of their
 * own and fall back to the `#st` colour the preview inherits from `body`.
 */
internal val PANEL_TEXT: Map<Int, Color> = mapOf(
    0 to Color(0xFFE7E3F6),
    1 to Color(0xFFF0E4C8),
    2 to Color(0xFFFFF1EA),   // the later `.x2` rule (red face) wins over the first
    3 to Color(0xFFE3E8F0),
    4 to Color(0xFF2A241B),
    5 to Color(0xFFF3E6CC),
    6 to Color(0xFFF3E6CC),
    10 to Color(0xFFF3E6CC),
    11 to Color(0xFFF3E6CC),
    12 to Color(0xFFF3E6CC),
    13 to Color(0xFFE8E4FF),
    14 to Color(0xFFE0F2FF),
)

/** Text colour of [effectId]'s panel, or the `body` colour for the ids without a rule. */
fun fxPanelText(effectId: Int): Color = PANEL_TEXT[effectId] ?: Color(0xFFE6EAF3)

/**
 * Panel background for [effectId] as the `.xN` rule declares it. Those rules are
 * gradients, not flat colours — `.x1` is `linear-gradient(#41382a,#1f1b14)` — so a
 * single colour renders too dark and flat. Top and bottom are sampled from the
 * references; the accent overlay (`.x0`, `.x2`) is left to `bg()`.
 */
internal val PANEL_BG_GRADIENT: Map<Int, Pair<Color, Color>> = mapOf(
    // `.x0` is a flat #0f1120 under two radial washes; the washes live in `bg()`
    // because a single Brush cannot stack a radial over a linear.
    0 to (Color(0xFF0F1120) to Color(0xFF0F1120)),
    1 to (Color(0xFF41382A) to Color(0xFF1F1B14)),
    2 to (Color(0xFFC4182C) to Color(0xFF5A0A14)),
    3 to (Color(0xFF3F444D) to Color(0xFF23262C)),
    4 to (Color(0xFFEFE9DB) to Color(0xFFD6CDB9)),
    // `.x5`/`.x6` are a flat #120a06.
    5 to (Color(0xFF120A06) to Color(0xFF120A06)),
    6 to (Color(0xFF120A06) to Color(0xFF120A06)),
    // 7, 8 and 9 have no `.xN` rule at all, so they keep `#win`'s own gradient.
    7 to (Color(0xFF1F2635) to Color(0xFF131925)),
    8 to (Color(0xFF1F2635) to Color(0xFF131925)),
    9 to (Color(0xFF1F2635) to Color(0xFF131925)),
    10 to (Color(0xFF0A0806) to Color(0xFF0A0806)),
    11 to (Color(0xFF0A0806) to Color(0xFF0A0806)),
    12 to (Color(0xFF0A0806) to Color(0xFF0A0806)),
    13 to (Color(0xFF05040C) to Color(0xFF05040C)),
    14 to (Color(0xFF04060C) to Color(0xFF04060C)),
)

/** Vertical gradient of the panel, or null for an unknown id (shared fallback). */
fun fxPanelGradient(effectId: Int): Pair<Color, Color>? = PANEL_BG_GRADIENT[effectId]

/** Base colour of the panel for [effectId], from its `.xN` rule; the shared
 * gradient is the fallback. The scene canvas does not cover the whole window, so
 * the host needs the same colour to paint the surrounding band. */
fun fxPanelBackground(effectId: Int): Color =
    fxPanelGradient(effectId)?.first ?: Color(0xFF171C28)

/** Panel background as a brush, used both by the host (whole window) and by the
 * scene (`bg()`), so the two agree. */
@Composable
fun fxPanelBrush(effectId: Int): Brush {
    val g = fxPanelGradient(effectId)
    return if (g != null) Brush.verticalGradient(listOf(g.first, g.second))
    else Brush.verticalGradient(listOf(Color(0xFF171C28), Color(0xFF0B0E16)))
}


/**
 * A CSS `radial-gradient(<rx> <ry> at <cx> <cy>, colour, transparent <stop>)`.
 * Compose only has circular radial gradients, so the circle is drawn with radius
 * [rx] inside a space scaled on Y by `ry/rx`: a circle of radius rx becomes an
 * ellipse with radii (rx, ry). Skipping the scale made every wash reach too far
 * sideways at mid-height (the reference ellipse is 110% wide but only 90% tall).
 */
private fun DrawScope.radialWash(
    cx: Float, cy: Float, rx: Float, ry: Float, color: Color, stop: Float,
) {
    scale(1f, ry / rx, pivot = Offset(cx, cy)) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to color, stop to color.copy(alpha = 0f),
                    1f to color.copy(alpha = 0f)),
                center = Offset(cx, cy),
                radius = rx,
            ),
            radius = rx, center = Offset(cx, cy),
        )
    }
}


/**
 * A CSS `linear-gradient(<deg>, ...)` over the box (x0, y0, w, h). CSS angles run
 * clockwise from "to top", so the direction is `(sin a, -cos a)` and the line is
 * `|w·sin a| + |h·cos a|` long. Hand-picking a start/end pair for 160deg put the
 * ramp almost horizontal, which washed the header of effect 2 out.
 */
private fun cssLinearGradient(
    x0: Float, y0: Float, w: Float, h: Float, deg: Float, colors: List<Color>,
): Brush {
    val a = Math.toRadians(deg.toDouble())
    val dx = sin(a).toFloat()
    val dy = -cos(a).toFloat()
    val len = abs(w * dx) + abs(h * dy)
    val cx = x0 + w / 2f
    val cy = y0 + h / 2f
    return Brush.linearGradient(
        colors,
        start = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
        end = Offset(cx + dx * len / 2f, cy + dy * len / 2f),
    )
}

private fun DrawScope.bg(effectId: Int, v: FloatArray, t: Float, accent: Color) {
    val X0 = -FxStyle.OXL
    val Y0 = -FxStyle.OYL
    val w = FxStyle.PWL
    val h = FxStyle.PHL

    // `bg(i,x,t,v,a)` only draws for effects 0 and 1. Everything else is the `.xN`
    // CSS rule on the canvas, which covers the whole window.
    if (effectId == 0) {
        radialWash(X0 + w * 0.12f, Y0, 1.10f * w, 0.90f * h, Color(0xFF2B2C58), 0.60f)
        radialWash(X0 + w, Y0 + h, 0.90f * w, 0.70f * h, Color(0xFF1C3B4A), 0.55f)
        // The four specks are CSS `radial-gradient(1.2px 1.2px at ...)` layers, not
        // a starfield: exactly four, at fixed positions, never animated. The old
        // loop drew 70 twinkling dots for every effect, which the reference has
        // nowhere.
        listOf(
            0.12f to 0.22f to 1.2f, 0.58f to 0.12f to 1.0f,
            0.86f to 0.40f to 1.4f, 0.38f to 0.80f to 1.0f,
        ).forEach { (p, r) ->
            drawCircle(Color(0xE6FFFFFF), radius = r, center = Offset(X0 + w * p.first, Y0 + h * p.second))
        }
        // `bg(0,...)`: a soft glow around the orb, driven by the damp and the mix.
        // The reference is `hsla(268-v[3]*75,45%,30%,.18+.35*v[5])`: a dark, low
        // saturation purple, not the screen accent. Using the accent at the same
        // alpha washed the whole corner out, +12 per channel.
        val mix = v.getOrElse(5) { 0f }
        val damp = v.getOrElse(3) { 0f }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(hsl(268f - damp * 75f, 0.45f, 0.30f).copy(alpha = 0.18f + 0.35f * mix),
                    Color.Transparent),
                center = Offset(389f, 114f),
                radius = 520f,
            ),
            radius = 520f, center = Offset(389f, 114f),
        )
    }

    if (effectId == 1) {
        // `.x1` base: `linear-gradient(#41382a,#1f1b14)` with hairline stripes.
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF41382A), Color(0xFF1F1B14)), startY = Y0, endY = Y0 + h),
            topLeft = Offset(X0, Y0), size = Size(w, h),
        )
        var x = X0
        while (x < X0 + w) {
            drawRect(Color(0x08FFFFFF), topLeft = Offset(x, Y0), size = Size(1f, h))
            x += 3f
        }
        // `bg(1,...)`: four expanding rings and two wave lines.
        val fb = 0.2f + 0.8f * v.getOrElse(8) { 0f }
        for (k in 0 until 4) {
            val u = (t * 0.22f + k / 4f) % 1f
            drawCircle(accent.copy(alpha = (1f - u) * 0.2f * fb), radius = 20f + u * 640f,
                center = Offset(95f, 80f), style = Stroke(width = 1.5f))
        }
        for (r in 0 until 2) {
            val path = Path()
            var q = X0
            var first = true
            while (q <= X0 + w) {
                val y = (if (r == 1) Y0 + h - 22f else Y0 + 20f) +
                    sin(q * 0.02f + t * (1f + v.getOrElse(10) { 0f } * 3f) + r * 2f) * (4f + v.getOrElse(10) { 0f } * 10f)
                if (first) { path.moveTo(q, y); first = false } else path.lineTo(q, y)
                q += 8f
            }
            drawPath(path, accent.copy(alpha = 0.18f), style = Stroke(width = 1f))
        }
    }

    if (effectId == 2) {
        // The second `.x2` rule wins over the first: a 160deg three-stop ramp, with
        // 1px vertical stripes on a 3px pitch over it. Drawn here rather than as the
        // host brush because a Brush cannot stack a repeating pattern on a ramp.
        drawRect(
            brush = cssLinearGradient(X0, Y0, w, h, 160f,
                listOf(Color(0xFFC4182C), Color(0xFF93111F), Color(0xFF5A0A14))),
            topLeft = Offset(X0, Y0), size = Size(w, h),
        )
        var sx = X0
        while (sx < X0 + w) {
            drawRect(Color(0x07FFFFFF), topLeft = Offset(sx, Y0), size = Size(1f, h))
            sx += 3f
        }
        // `#ff8a8a33` in the preview: alpha 0x33, not full. A full-alpha wash here
        // over-brightened the whole top band — measured (215,93,101) against the
        // reference's (188,55,70).
        radialWash(X0 + w * 0.18f, Y0 - 0.10f * h, 1.30f * w, 0.70f * h, Color(0x33FF8A8A), 0.60f)
    }

    if (effectId == 3) {
        // `.x3`: `box-shadow:inset 0 2px 0 #ffffff22`, a 2 px highlight along the top.
        drawRect(Color(0x22FFFFFF), topLeft = Offset(X0, Y0), size = Size(w, 2f))
    }
}

private fun DrawScope.sceneOrb(v: FloatArray, a: Color, t: Float) {
    val cx = FxStyle.PWL / 2f
    val cy = 114f
    val sz = v[0]; val dc = v[1]; val pd = v[2]; val dm = v[3]; val wd = v[4]; val mx = v[5]; val df = v[6]
    val r = 40f * (0.7f + sz * 0.8f) * (1f + 0.03f * sin(t * 2f))
    val col = hsl(268f - dm * 75f, 0.48f, 0.74f)
    // `for(let q=0;q<70;q++)`: the starfield. Seventy 1.2 px dots at fixed hashed
    // positions, each twinkling on its own phase. The port had none, so the reverb
    // read as a bare orb on a flat field.
    for (q in 0 until 70) {
        val sx = -FxStyle.OXL + (sin(q * 91.7f) * 0.5f + 0.5f) * FxStyle.PWL
        val sy = -FxStyle.OYL + (sin(q * 47.3f) * 0.5f + 0.5f) * FxStyle.PHL
        drawRect(Color.White.copy(alpha = 0.2f + 0.5f * abs(sin(t * 1.3f + q))),
            topLeft = Offset(sx, sy), size = Size(1.2f, 1.2f))
    }
    for (w in 0 until 3) {
        val u = (t * (0.12f + sz * 0.2f) + w / 3f) % 1f
        if (u < pd * 0.4f) continue
        drawCircle(col.copy(alpha = (1f - u) * 0.4f), radius = r * (1.1f + u * 5f),
            center = Offset(cx, cy), style = Stroke(width = 1.6f))
    }
    var rx = r * 1.75f
    oval(cx, cy, rx, rx * (0.3f + wd * 0.18f), -0.35f + sin(t * 0.25f) * wd * 0.55f,
        col.copy(alpha = 0.3f + 0.5f * mx), 2.2f, PathEffect.dashPathEffect(floatArrayOf(4f + dc * 36f, 9f), -t * 30f * (0.3f + wd)))
    rx = r * 2.7f
    val ry = rx * 0.36f
    val tl1 = 0.25f + cos(t * 0.2f) * wd * 0.4f
    val n = 22 + (sz * 14f).roundToInt()
    val l = 3 + (dc * 6f).roundToInt()
    for (p in 0 until n) {
        val an = t * (0.25f + wd * 0.9f) + p * TAU / n
        for (s in 0 until l) {
            val aa = an - s * 0.08f * (0.4f + dc)
            val wob = sin(aa * 5f + t * 3f) * df * r * 0.45f
            val x = (rx + wob) * cos(aa); val y = (ry + wob * 0.4f) * sin(aa)
            drawCircle(if (s == 0) Color.White else col, radius = 2.1f * (1f - s.toFloat() / l * 0.5f),
                center = Offset(cx + x * cos(tl1) - y * sin(tl1), cy + x * sin(tl1) + y * cos(tl1)),
                alpha = (1f - s.toFloat() / l) * 0.85f)
        }
    }
    rx = min(r * 3.7f, 250f)
    val ry2 = rx * 0.3f
    val tl2 = -0.6f + sin(t * 0.17f) * wd * 0.3f
    for (e in 0 until 3) {
        oval(cx, cy, rx * (1f + e * 0.06f), ry2 * (1f + e * 0.06f), tl2,
            col.copy(alpha = 0.05f + 0.25f * dc * (1f - e / 3f)), 1f, null)
    }
    for (c in 0 until 3) {
        val an = -t * (0.18f + wd * 0.6f) + c * 2.094f
        val t2 = 8 + (dc * 20f).roundToInt()
        for (s in 0 until t2) {
            val aa = an + s * 0.05f
            drawCircle(if (s == 0) Color.White else col, radius = 3f * (1f - s.toFloat() / t2 * 0.6f),
                center = Offset(cx + rx * cos(aa) * cos(tl2) - ry2 * sin(aa) * sin(tl2),
                                cy + rx * cos(aa) * sin(tl2) + ry2 * sin(aa) * cos(tl2)),
                alpha = (1f - s.toFloat() / t2) * (0.3f + 0.6f * mx))
        }
    }
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFEEE8FF), a, Color(0xFF1D1A38)),
        center = Offset(cx - r * 0.35f, cy - r * 0.4f), radius = r * 1.4f),
        radius = r, center = Offset(cx, cy))
    for (q in 0 until 3) {
        oval(cx, cy, r * abs(cos(t * 0.4f * (0.5f + wd) + q * 1.05f)), r, 0f,
            Color.White.copy(alpha = 0.1f + 0.1f * df), 0.8f, null)
    }
    drawCircle(Color.White.copy(alpha = 0.08f + 0.4f * mx), radius = r * 1.08f,
        center = Offset(cx, cy), style = Stroke(width = 1f))
}

private fun DrawScope.sceneEcho(v: FloatArray, a: Color, t: Float) {
    val px0 = 190f; val pw = 400f; val py0 = 18f; val ph = 218f; val pb = py0 + ph
    val rr = 62f
    val sp = 1.7f - (v[0] + v[2] + v[4] + v[6]) / 4f * 1.3f
    // The whole wheel — gradient disc, the six ports and the hub — is drawn inside
    // the wheel's own rotation. Drawing the ports in a separate rotate block left
    // them at absolute angles while the reference spins them with the disc, which
    // was most of the 10.8/255 error on the wheel.
    val wheelGrad = Brush.linearGradient(
        colorStops = arrayOf(0f to Color(0xFFE8D2A0), 0.40f to Color(0xFF7D6335),
            0.70f to Color(0xFFD9BF86), 1f to Color(0xFF5F4A28)),
        start = Offset(95f - rr, 80f - rr), end = Offset(95f + rr, 80f + rr),
    )
    rotate((t * sp + sin(t * 6f) * v[10] * 0.1f) * 57.29578f, pivot = Offset(95f, 80f)) {
        drawCircle(brush = wheelGrad, radius = rr, center = Offset(95f, 80f))
        for (k in 0 until 6) {
            rotate(k * 60f, pivot = Offset(95f, 80f)) {
                drawOval(Color(0xFF14110C), topLeft = Offset(95f + rr * 0.29f, 80f - rr * 0.17f),
                    size = Size(rr * 0.52f, rr * 0.34f))
            }
        }
        // The hub reuses the disc gradient, not a flat dark fill.
        drawCircle(brush = wheelGrad, radius = rr * 0.22f, center = Offset(95f, 80f))
    }
    drawCircle(brush = Brush.radialGradient(listOf(Color(0x55FFFFFF), Color.Transparent),
        center = Offset(95f - rr * 0.4f, 80f - rr * 0.45f), radius = rr * 1.2f),
        radius = rr, center = Offset(95f, 80f))
    drawRect(Color(0xFF0D0B08), topLeft = Offset(px0, py0), size = Size(pw, ph))
    for (k in 1 until 5) {
        drawRect(Color(0x12FFFFFF), topLeft = Offset(px0 + pw * k / 5f, py0), size = Size(1f, ph))
        drawRect(Color(0x12FFFFFF), topLeft = Offset(px0, py0 + ph * k / 5f), size = Size(pw, 1f))
    }
    val p = (0 until 4).map { n -> floatArrayOf(px0 + v[2 * n] * pw, pb - v[2 * n + 1] * ph) }.sortedBy { it[0] }
    val fb = 0.2f + v[8] * 0.8f
    for (q in p) drawRect(a.copy(alpha = 0.25f * fb), topLeft = Offset(q[0] - 4f, q[1]), size = Size(8f, pb - q[1]))
    val sp2 = (p[3][0] - p[0][0]) + pw * 0.1f
    for (r in 1 until 4) for (q in p) {
        val gx = q[0] + sp2 * r
        if (gx < px0 + pw) drawRect(a.copy(alpha = 0.18f * fb.pow(r + 1)),
            topLeft = Offset(gx - 3f, q[1] + (pb - q[1]) * (1f - fb.pow(r))),
            size = Size(6f, (pb - q[1]) * fb.pow(r)))
    }
    val path = Path().apply {
        moveTo(px0, pb)
        p.forEach { lineTo(it[0], it[1]) }
        lineTo(px0 + pw, pb)
    }
    drawPath(path, a.copy(alpha = 0.5f + 0.5f * fb), style = Stroke(width = 2f))
    val dx = px0 + ((t * 0.28f) % 1f) * pw
    var dy = pb
    val pts = listOf(floatArrayOf(px0, pb)) + p + listOf(floatArrayOf(px0 + pw, pb))
    for (k in 0 until pts.size - 1) {
        val aa = pts[k]; val bb = pts[k + 1]
        if (dx >= aa[0] && dx <= bb[0]) {
            dy = aa[1] + (bb[1] - aa[1]) * (dx - aa[0]) / max(1f, bb[0] - aa[0]); break
        }
    }
    drawCircle(brush = Brush.radialGradient(listOf(Color.White, a, Color.Transparent),
        center = Offset(dx, dy), radius = 14f), radius = 14f, center = Offset(dx, dy))
    for (n in 0 until 4) {
        val x = px0 + v[2 * n] * pw
        val y = pb - v[2 * n + 1] * ph
        drawCircle(brush = Brush.radialGradient(
            listOf(Color(0xFFFFF0C8), Color(0xFFC9A45C), Color(0xFF4A3A1C)),
            center = Offset(x - 2.7f, y - 2.7f), radius = 9f), radius = 9f, center = Offset(x, y))
    }
}

private fun DrawScope.rrect(x0: Float, y0: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    moveTo(x0 + r, y0)
    arcTo(Rect(x0 + w - r * 2, y0, x0 + w, y0 + r * 2), -90f, 90f, false)
    arcTo(Rect(x0 + w - r * 2, y0 + h - r * 2, x0 + w, y0 + h), 0f, 90f, false)
    arcTo(Rect(x0, y0 + h - r * 2, x0 + r * 2, y0 + h), 90f, 90f, false)
    arcTo(Rect(x0, y0, x0 + r * 2, y0 + r * 2), 180f, 90f, false)
    close()
}

private fun DrawScope.sceneEq(v: FloatArray, t: Float, tm: TextMeasurer) {
    val x0 = 134f; val wd = 466f; val y0 = 12f; val hh = 138f; val my = y0 + hh / 2f
    val sg = 0.05f + 0.14f / (0.3f + v[4] * 3.7f)
    fun cv(u: Float): Float =
        (v[1] - 0.5f) * 2f / (1f + exp(((u - v[0]) * 16f).toDouble())).toFloat() +
        (v[3] - 0.5f) * 2f * exp(-((u - v[2]) / sg).toDouble().pow(2.0)).toFloat() +
        (v[6] - 0.5f) * 2f / (1f + exp((-(u - v[5]) * 16f).toDouble())).toFloat()
    fun yy(u: Float) = my - cv(u) * hh * 0.4f
    drawPath(rrect(x0 - 4f, y0 - 4f, wd + 8f, hh + 8f, 16f), color = Color(0x6A000000))
    drawPath(rrect(x0, y0, wd, hh, 13f), brush = Brush.linearGradient(
        listOf(Color(0xFF1B070C), Color(0xFF0C0305)), start = Offset(0f, y0), end = Offset(0f, y0 + hh)))
    clipPath(rrect(x0, y0, wd, hh, 13f)) {
        drawRect(brush = Brush.linearGradient(
            listOf(Color(0x2AFF2D4A), Color.Transparent, Color(0x2A6FC8FF)),
            start = Offset(0f, y0), end = Offset(0f, y0 + hh)),
            topLeft = Offset(x0, y0), size = Size(wd, hh))
        for (f in floatArrayOf(0.25f, 0.75f)) drawRect(Color(0x1AFFFFFF), topLeft = Offset(x0, y0 + hh * f), size = Size(wd, 1f))
        for (f in floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f)) drawRect(Color(0x1AFFFFFF), topLeft = Offset(x0 + f * wd, y0), size = Size(1f, hh))
        drawLine(Color(0x59FFFFFF), Offset(x0, my), Offset(x0 + wd, my), 1f, pathEffect = dash(5f, 5f))
        val nb = 70
        for (k in 0 until nb) {
            val u = k.toFloat() / nb
            val h = hh * 0.3f * (0.35f + 0.65f * abs(sin(k * 0.6f + t * 2.1f) * sin(k * 0.17f + t * 0.7f))) *
                    (1f - u * 0.45f) * (0.55f + v[7] * 0.9f) * (1f + cv(u) * 0.8f)
            drawRect(if (cv(u) > 0f) Color(0xFFFF6B7E) else Color(0xFF7FD0FF),
                topLeft = Offset(x0 + u * wd, y0 + hh - max(1f, h)), size = Size(wd / nb - 1f, max(1f, h)),
                alpha = 0.24f)
        }
        fun curve(): Path = Path().apply {
            moveTo(x0, yy(0f))
            var k = 0f
            while (k <= wd) { lineTo(x0 + k, yy(k / wd)); k += 3f }
        }
        clipRect(x0, y0, x0 + wd, my) {
            val p = curve(); p.lineTo(x0 + wd, my); p.lineTo(x0, my); p.close()
            drawPath(p, brush = Brush.linearGradient(listOf(Color(0xAAFF3D58), Color(0x15FF3D58)),
                start = Offset(0f, y0), end = Offset(0f, my)))
        }
        clipRect(x0, my, x0 + wd, y0 + hh) {
            val p = curve(); p.lineTo(x0 + wd, my); p.lineTo(x0, my); p.close()
            drawPath(p, brush = Brush.linearGradient(listOf(Color(0x157FD0FF), Color(0xAA7FD0FF)),
                start = Offset(0f, my), end = Offset(0f, y0 + hh)))
        }
        translate(0f, 5f) { drawPath(curve(), Color(0x99000000), style = Stroke(width = 3.4f)) }
        clipRect(x0, y0, x0 + wd, my) { drawPath(curve(), Color(0xFFFF5468), style = Stroke(width = 3.4f)) }
        clipRect(x0, my, x0 + wd, y0 + hh) { drawPath(curve(), Color(0xFF86D6FF), style = Stroke(width = 3.4f)) }
    }
    for ((q, f) in listOf("100" to 0.3f, "1k" to 0.6f, "10k" to 0.9f)) {
        label(tm, q, x0 + f * wd, y0 + hh + 16f, Color(0xBFFFE9E2), 9f)
    }
    // Left axis: the preview draws `+12 / 0 / -12` right-aligned at x0-9. The port
    // had no vertical scale at all, so the curve's dB range was unreadable.
    for ((q, f) in listOf("+12" to 0f, "0" to 0.5f, "-12" to 1f)) {
        label(tm, q, x0 - 9f, y0 + hh * f + (if (f != 0f) 3f else 9f),
            Color(0xBFFFE9E2), 9f, align = LabelAlign.RIGHT)
    }
    // `L`/`M`/`H` inside each band dot, and the per-band readout at the top left of
    // the curve: `LOW 200 Hz +0.0 dB`. Both were missing from the port.
    for ((b, fg) in listOf(0 to (0 to 1), 1 to (2 to 3), 2 to (5 to 6))) {
        val nx = x0 + v[fg.first] * wd
        val ny = y0 + (1f - v[fg.second]) * hh
        label(tm, "LMH"[b].toString(), nx, ny + 3.2f, Color(0xFF1A0508), 9f, bold = true)
        val hz = 20f * 1000f.pow(v[fg.first])
        val gd = (v[fg.second] - 0.5f) * 24f
        val hzTxt = if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "${hz.roundToInt()} Hz"
        val gdTxt = (if (gd >= 0f) "+" else "") + "%.1f dB".format(gd)
        label(tm, "${listOf("LOW", "MID", "HIGH")[b]}  $hzTxt  $gdTxt",
            x0 + 12f, y0 + 12f, Color(0xFFFFE9E2), 10f, align = LabelAlign.LEFT)
    }
    for (b in 0 until 3) {
        val fg = listOf(0 to 1, 2 to 3, 5 to 6)[b]
        val nx = x0 + v[fg.first] * wd
        val ny = y0 + (1f - v[fg.second]) * hh
        val up = v[fg.second] >= 0.5f
        drawCircle(brush = Brush.radialGradient(
            listOf(Color.White, if (up) Color(0xFFFF6B7E) else Color(0xFF86D6FF),
                   if (up) Color(0xFF6A0B18) else Color(0xFF124A6A)),
            center = Offset(nx - 3.8f, ny - 4.4f), radius = 11f),
            radius = 11f, center = Offset(nx, ny))
    }

    // `if(!m)`: the OUT fader at the far right of the curve. The port had no fader
    // at all — `sceneEq` stopped at the band dots — so prism-tone rendered with a
    // bare right edge. Geometry and colours are the preview's `eq()` block.
    run {
        val fx = 752f; val ty = 26f; val by = 232f
        val th = ty + (1f - v[7]) * (by - ty)
        drawPath(rrect(fx - 4f, ty - 6f, 8f, by - ty + 12f, 4f), color = Color(0x66000000))
        drawPath(rrect(fx - 2f, th, 4f, by - th, 2f), brush = Brush.linearGradient(
            listOf(Color(0xFFFFE9E2), Color(0xFFFFB9A8)),
            start = Offset(0f, th), end = Offset(0f, by)))
        // The preview saves/restores a `shadowColor '#000a', shadowBlur 7,
        // shadowOffsetY 4` around the cap. A blurred shadow is not a band: it
        // spreads sideways past the object as well as downwards, and it is darkest
        // right under the bottom edge. An earlier attempt used a 34 px wide rrect
        // with a vertical ramp, which kept hard vertical sides and read as an
        // invisible outline around the handle. This is an elliptical penumbra
        // centred below the cap, wider than the cap itself, fading out in every
        // direction: a radial gradient drawn into an oval bounds.
        drawOval(
            brush = Brush.radialGradient(
                listOf(Color(0x8A000000), Color(0x5E000000), Color(0x2E000000), Color(0x00000000)),
                center = Offset(fx, th + 15f), radius = 24f),
            topLeft = Offset(fx - 24f, th + 6f), size = Size(48f, 18f))
        drawPath(rrect(fx - 15f, th - 11f, 30f, 22f, 5f), brush = Brush.linearGradient(
            listOf(Color(0xFFF4F0EA), Color(0xFFA39B95), Color(0xFF5C5550)),
            start = Offset(0f, th - 11f), end = Offset(0f, th + 11f)))
        drawRect(Color(0xFF2A0C10), topLeft = Offset(fx - 10f, th - 1f), size = Size(20f, 2f))
        label(tm, "OUT", fx, ty - 12f, Color(0xFFFFE9E2), 9f)
        label(tm, "%.1f dB".format((v[7] - 0.5f) * 24f), fx, by + 20f, Color(0xFFFFE9E2), 9f)
    }
}

private fun DrawScope.sceneGrip(v: FloatArray, a: Color, t: Float, tm: TextMeasurer) {
    val th = v[0]
    val ratios = floatArrayOf(1.5f, 2f, 3f, 4f, 8f, 20f)
    val r = ratios[(v[1] * 5f).roundToInt().coerceIn(0, 5)]
    val l = 0.52f + 0.36f * sin(t * 2.3f) * sin(t * 0.7f + 1f) + 0.08f * sin(t * 7.1f)
    val g1 = min(1f, max(0f, l - th * 0.9f) * (1f - 1f / r) * 2.2f * 1.6f)
    val gx = 135f; val gy = 218f; val gR = 118f
    val a0 = Math.PI.toFloat() * 1.17f; val a1 = Math.PI.toFloat() * 1.83f
    drawArc(Color(0xFF1C1E24), 180f, 180f, true,
        topLeft = Offset(gx - gR - 8f, gy - gR - 8f), size = Size((gR + 8f) * 2, (gR + 8f) * 2))
    drawArc(brush = Brush.linearGradient(listOf(Color(0xFFD6D0BB), Color(0xFFA79F86)),
        start = Offset(0f, gy - gR), end = Offset(0f, gy + 8f)),
        180f, 180f, true, topLeft = Offset(gx - gR - 3f, gy - gR - 3f), size = Size((gR + 3f) * 2, (gR + 3f) * 2))
    for (k in 0..20) {
        val an = a0 + (a1 - a0) * k / 20f
        val r1 = gR * 0.84f
        val r2 = gR * (if (k % 5 != 0) 0.9f else 0.96f)
        drawLine(if (k > 15) Color(0xFFAA3333) else Color(0xFF2A2820),
            Offset(gx + r1 * cos(an), gy + r1 * sin(an)),
            Offset(gx + r2 * cos(an), gy + r2 * sin(an)),
            strokeWidth = if (k % 5 != 0) 1f else 2f)
    }
    val na = a0 + (a1 - a0) * g1
    drawLine(Color(0xFF14120E), Offset(gx, gy + 4f),
        Offset(gx + gR * 0.9f * cos(na), gy + gR * 0.9f * sin(na)), 2.6f)
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFAAB2C0), Color(0xFF2A2E38)),
        center = Offset(gx - 3f, gy - 3f), radius = 14f), radius = 13f, center = Offset(gx, gy + 2f))
    label(tm, "GAIN REDUCTION", gx, gy - gR * 0.42f, Color(0xFF2A2820), 10f, true)
    val px = 272f; val pw = 150f; val py = 20f; val ph = 223f
    val gap = ph * (1f - g1 * 0.64f)
    val pc = py + ph / 2f
    drawRect(Color(0xFF0C0D10), topLeft = Offset(px, py), size = Size(pw, ph))
    val wave = Path()
    var k = 0f
    while (k <= pw) {
        val y = pc + sin(k * 0.09f + t * 5f) * (gap / 2f) * 0.9f * (0.6f + 0.4f * sin(k * 0.03f + t))
        if (k == 0f) wave.moveTo(px + k, y) else wave.lineTo(px + k, y)
        k += 2f
    }
    drawPath(wave, a, style = Stroke(width = 2f))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFE1E6EE), Color(0xFF8A92A1), Color(0xFF4D5461)),
        start = Offset(0f, py), end = Offset(0f, py + (ph - gap) / 2f)),
        topLeft = Offset(px, py), size = Size(pw, (ph - gap) / 2f))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFE1E6EE), Color(0xFF8A92A1), Color(0xFF4D5461)),
        start = Offset(0f, py + ph - (ph - gap) / 2f), end = Offset(0f, py + ph)),
        topLeft = Offset(px, py + ph - (ph - gap) / 2f), size = Size(pw, (ph - gap) / 2f))
    for (q in listOf(Offset(px + 10f, py + 8f), Offset(px + pw - 10f, py + 8f),
                     Offset(px + 10f, py + ph - 8f), Offset(px + pw - 10f, py + ph - 8f))) {
        drawCircle(Color(0xFF2A2E38), radius = 3f, center = q)
    }
    val ty = 30f + (1f - th) * 205f
    drawRect(Color(0xFF08090C), topLeft = Offset(452f, 30f), size = Size(8f, 205f))
    drawRect(a.copy(alpha = 0.7f), topLeft = Offset(452f, ty), size = Size(8f, 235f - ty))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFDFE4EC), Color(0xFF8B93A3), Color(0xFF515866)),
        start = Offset(0f, ty - 11f), end = Offset(0f, ty + 11f)),
        topLeft = Offset(438f, ty - 11f), size = Size(36f, 22f))
    drawRect(Color(0xFF14120E), topLeft = Offset(442f, ty - 1f), size = Size(28f, 2f))
    label(tm, "THRESH ${(th * 60f - 60f).roundToInt()} dB", 456f, 252f, Color(0xFFCFD6E6), 9f, true)
    drawLine(Color(0x80E0A24A), Offset(px, pc - th * ph * 0.42f), Offset(px + pw, pc - th * ph * 0.42f),
        1f, pathEffect = dash(4f, 4f))
}

private fun DrawScope.sceneCeil(v: FloatArray, t: Float) {
    val am = v[0]; val cl = v[1]
    val sx = 390f; val sw = 300f; val sy = 20f; val sh = 223f; val my = sy + sh / 2f
    val kx = 125f; val ky = 128f
    val r0 = 50f + am * 130f
    drawCircle(brush = Brush.radialGradient(
        listOf(Color(0xFFE0A24A).copy(alpha = 0.18f + 0.3f * am), Color.Transparent),
        center = Offset(kx, ky), radius = r0), radius = r0, center = Offset(kx, ky))
    for (k in 0 until 26) {
        val an = -Math.PI.toFloat() * 0.95f + k * Math.PI.toFloat() * 1.9f / 26f + sin(t * 0.5f) * 0.05f
        val len = 70f + am * 110f * (0.6f + 0.4f * sin(t * 2f + k))
        drawLine(Color(0xFFE0A24A), Offset(kx + cos(an) * 62f, ky + sin(an) * 62f),
            Offset(kx + cos(an) * (62f + len), ky + sin(an) * (62f + len)), 1.6f,
            alpha = 0.08f + 0.28f * am)
    }
    drawRect(Color(0xFF17140F), topLeft = Offset(sx, sy), size = Size(sw, sh))
    val gn = 1f + am * 2.4f
    val cy = my - cl * sh * 0.42f
    val fy = my + cl * sh * 0.42f
    var hit = 0
    val wave = Path()
    var k = 0f
    while (k <= sw) {
        val u = k / sw
        val y0 = sin(u * 13f + t * 3f) * 0.72f + sin(u * 31f + t) * 0.2f
        val s = max(-cl, min(cl, y0 * gn))
        if (abs(y0 * gn) > cl) hit++
        if (k == 0f) wave.moveTo(sx + k, my - s * sh * 0.42f) else wave.lineTo(sx + k, my - s * sh * 0.42f)
        k += 2f
    }
    drawPath(wave, Color(0xFFE0A24A), style = Stroke(width = 2.2f))
    val hf = min(1f, hit / (sw / 2f))
    drawRect(brush = Brush.linearGradient(listOf(Color.Transparent, Color(0xFFE0A24A).copy(alpha = 0.15f + 0.5f * hf)),
        start = Offset(0f, cy - 30f), end = Offset(0f, cy)), topLeft = Offset(sx, cy - 30f), size = Size(sw, 30f))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFF1DFAE), Color(0xFFB9934D), Color(0xFF6E5226)),
        start = Offset(0f, sy), end = Offset(0f, cy)), topLeft = Offset(sx, sy), size = Size(sw, max(3f, cy - sy)))
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFF1DFAE), Color(0xFFB9934D), Color(0xFF6E5226)),
        start = Offset(0f, fy), end = Offset(0f, sy + sh)), topLeft = Offset(sx, fy), size = Size(sw, max(3f, sy + sh - fy)))
}

/** Bottom rail: material strip plus the Level fader. */
private fun DrawScope.rail(instrumentId: Int, level: Float, tm: TextMeasurer) {
    val r = FxStyle.rail(instrumentId)
    val x0 = -FxStyle.OXL
    val y0 = FxStyle.RAIL_Y
    val rw = FxStyle.PWL
    val rh = max(71f, FxStyle.PHL - FxStyle.OYL - FxStyle.RAIL_Y)
    drawRect(brush = Brush.linearGradient(listOf(r.bgTop, r.bgBottom),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)),
        topLeft = Offset(x0, y0), size = Size(rw, rh))
    clipRect(x0, y0, x0 + rw, y0 + rh) {
        when (r.pattern) {
            "wood" -> for (k in 0 until 8) {
                val p = Path()
                var u = 0f
                while (u <= rw) {
                    val y = y0 + 8 + k * 9 + sin(u * 0.02f + k) * 2
                    if (u == 0f) p.moveTo(x0 + u, y) else p.lineTo(x0 + u, y)
                    u += 10f
                }
                drawPath(p, Color(0x1A000000), style = Stroke(width = 1f))
            }
            "steel" -> {
                var y = 0f
                while (y < rh) { drawLine(Color(0x12FFFFFF), Offset(x0, y0 + y), Offset(x0 + rw, y0 + y), 1f); y += 2f }
                for (k in 0 until 8) drawCircle(Color(0x88000000), radius = 2.4f,
                    center = Offset(x0 + 14 + k * (rw - 28) / 7f, y0 + rh - 10f))
            }
            "velvet" -> {
                var k = -rh
                while (k < rw) { drawLine(Color(0x1F000000), Offset(x0 + k, y0), Offset(x0 + k + rh, y0 + rh), 1f); k += 7f }
            }
            "copper" -> {
                var k = 0f
                while (k < rw) { var rr = 0f
                    while (rr < rh) { drawCircle(Color(0x1AFFD0A0), radius = 1.6f, center = Offset(x0 + k + 7, y0 + rr + 7)); rr += 14f }
                    k += 14f }
            }
            "led" -> {
                var k = 0f
                while (k < rw) { drawLine(Color(0x147AD0FF), Offset(x0 + k, y0), Offset(x0 + k, y0 + rh), 1f); k += 18f }
            }
            "glass" -> drawRect(brush = Brush.linearGradient(listOf(Color(0x22FFFFFF), Color.Transparent),
                start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
        }
    }
    drawRect(brush = Brush.linearGradient(listOf(r.line.copy(alpha = 0f), r.line, r.line.copy(alpha = 0f)),
        start = Offset(x0, 0f), end = Offset(x0 + rw, 0f)),
        topLeft = Offset(x0, y0), size = Size(rw, 2f))
    val sx = 540f; val sw = 200f; val sy = 252f
    drawPath(rrect(sx - 3f, sy - 4f, sw + 6f, 8f, 4f), color = Color(0xE6000000))
    drawRect(brush = Brush.linearGradient(listOf(r.line.copy(alpha = 0.85f), Color.White),
        start = Offset(sx, 0f), end = Offset(sx + sw, 0f)),
        topLeft = Offset(sx, sy - 2f), size = Size(max(2f, sw * level), 4f))
    val tx = sx + sw * level
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFFF4EFE6), Color(0xFFA8A090), Color(0xFF5C554A)),
        start = Offset(0f, sy - 11f), end = Offset(0f, sy + 11f)),
        topLeft = Offset(tx - 8f, sy - 11f), size = Size(16f, 22f))
    drawRect(Color(0xFF2A1A10), topLeft = Offset(tx - 1f, sy - 7f), size = Size(2f, 14f))
    label(tm, "LEVEL", sx, sy - 17f, r.tx, 9f)
    label(tm, "${(level * 30f - 24f)}", sx + sw, sy - 17f, r.tx, 9f)
}

// ---------------------------------------------------------------------------
// Knob
// ---------------------------------------------------------------------------

/**
 * One knob, ported from `knob()` in the preview: 270 degrees of travel from
 * -135, accent value arc, metallic cap, white indicator. Drag is relative and
 * mixed (170 px on either axis is the full course). Double tap resets to the
 * registry default.
 */
@Composable
fun FxKnob(value: Float, accent: Color, default: Float, sizeDp: Float, onValue: (Float) -> Unit) {
    var v by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableFloatStateOf(0f) }
    if (dragging == 0f && abs(v - value) > 0.0001f) v = value
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = 1f },
                    onDragEnd = { dragging = 0f },
                    onDragCancel = { dragging = 0f },
                ) { change, drag ->
                    change.consume()
                    val nv = (v + (drag.x - drag.y) / 170f).coerceIn(0f, 1f)
                    v = nv
                    onValue(nv)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    v = default
                    onValue(default)
                })
            },
    ) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val r = size.minDimension / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(Color(0xFF0A0C12), radius = r, center = Offset(cx, cy))
            drawCircle(Color(0xFF0A0C12), radius = r * 0.9f, center = Offset(cx, cy), style = Stroke(width = 1.6f))
            val rect = Rect(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)
            drawArc(accent, -135f, 270f * v, false, topLeft = rect.topLeft, size = rect.size,
                style = Stroke(width = 1.6f, cap = StrokeCap.Round))
            drawCircle(brush = Brush.radialGradient(listOf(Color(0xFF9A9AB2), Color(0xFF25263A)),
                center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r * 0.9f),
                radius = r * 0.62f, center = Offset(cx, cy))
            val ang = Math.toRadians((-135f + 270f * v).toDouble()).toFloat()
            drawLine(Color.White, Offset(cx + r * 0.2f * cos(ang), cy + r * 0.2f * sin(ang)),
                Offset(cx + r * 0.55f * cos(ang), cy + r * 0.55f * sin(ang)), strokeWidth = 2f)
        }
    }
}

/** Draws the scene for [effectId] at the given animation time. */
fun DrawScope.drawFxScene(effectId: Int, values: FloatArray, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val accent = FxStyle.accent(effectId)
    if (effectId in 0..4) bg(effectId, values, t, accent)
    when (effectId) {
        0 -> sceneOrb(values, accent, t)
        1 -> sceneEcho(values, accent, t)
        2 -> sceneEq(values, t, tm)
        3 -> sceneGrip(values, accent, t, tm)
        4 -> sceneCeil(values, t)
        5 -> scenePiano(values, accent, t, tm, st)
        6 -> sceneOrgan(values, accent, t, tm, st)
        7 -> sceneSax(values, accent, t, tm, st)
        8 -> sceneStrings(values, accent, t, tm, st)
        9 -> sceneGuitar(values, accent, t, tm, st)
        10 -> sceneAmp(values, accent, t, tm, st)
        11 -> sceneBass(values, accent, t, tm, st)
        12 -> sceneBrass(values, accent, t, tm, st)
        13 -> sceneVoices(values, accent, t, tm, st)
        14 -> sceneSynth(values, accent, t, tm, st)
    }
}

/** Bottom rail, public entry point. */
fun DrawScope.drawFxRail(instrumentId: Int, level: Float, tm: TextMeasurer) = rail(instrumentId, level, tm)

````

### `FxInstrumentScenes.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/fx/FxInstrumentScenes.kt`
- **Taille :** 73588 octets · **lignes :** 1262 · **sha256 (12) :** `d1034c3141c4`
- Instruments 5–14 et état d'animation.

````kotlin
package com.soundstage.mixer.ui.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Mutable animation state shared by the instrument scenes. The preview keeps the
 * same accumulators as module-level globals; here they live in one holder owned
 * by the window so a recomposition never resets an animation.
 */
class FxSceneState {
    var presetIndex = 0

    val pianoE = FloatArray(64)
    var pianoEv = -1
    var pianoT = 0f

    var organT = 0f
    var organLs = 0f
    var organLp = 0f

    val saxP = mutableListOf<FloatArray>()
    var saxT = 0f

    val strP = mutableListOf<FloatArray>()
    var strSol = 0f
    var strT = 0f

    val gtrE = FloatArray(6)
    var gtrT = 0f

    val bsP = mutableListOf<FloatArray>()
    var bt = 0f
    var ampMute = -1
    var ampMuteT = 0f
}

// ---------------------------------------------------------------------------
// Geometry shared by the scenes (paths built once, as in the preview).
// ---------------------------------------------------------------------------

private fun chaikin(points: List<FloatArray>, n: Int): List<FloatArray> {
    var p = points
    repeat(n) {
        val q = mutableListOf(p[0])
        for (i in 0 until p.size - 1) {
            val a = p[i]; val b = p[i + 1]
            q.add(floatArrayOf(0.75f * a[0] + 0.25f * b[0], 0.75f * a[1] + 0.25f * b[1]))
            q.add(floatArrayOf(0.25f * a[0] + 0.75f * b[0], 0.25f * a[1] + 0.75f * b[1]))
        }
        q.add(p.last())
        p = q
    }
    return p
}

private fun resample(p: List<FloatArray>, n: Int): List<FloatArray> {
    val l = FloatArray(p.size)
    for (i in 1 until p.size) l[i] = l[i - 1] + hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1])
    val total = l.last()
    val out = ArrayList<FloatArray>(n)
    var j = 0
    for (k in 0 until n) {
        val d = total * k / (n - 1)
        while (j < l.size - 2 && l[j + 1] < d) j++
        val f = (d - l[j]) / (l[j + 1] - l[j]).let { if (it == 0f) 1f else it }
        out.add(floatArrayOf(
            p[j][0] + (p[j + 1][0] - p[j][0]) * f,
            p[j][1] + (p[j + 1][1] - p[j][1]) * f,
        ))
    }
    return out
}

private val SXP = resample(chaikin(listOf(
    floatArrayOf(40f, 14f), floatArrayOf(120f, 22f), floatArrayOf(200f, 44f), floatArrayOf(310f, 84f),
    floatArrayOf(430f, 126f), floatArrayOf(530f, 164f), floatArrayOf(600f, 184f), floatArrayOf(660f, 170f),
    floatArrayOf(698f, 130f), floatArrayOf(712f, 86f), floatArrayOf(718f, 60f)), 3), 160)

private val BXP = resample(chaikin(listOf(
    floatArrayOf(40f, 34f), floatArrayOf(180f, 34f), floatArrayOf(180f, 112f), floatArrayOf(360f, 112f),
    floatArrayOf(360f, 34f), floatArrayOf(500f, 34f), floatArrayOf(500f, 112f), floatArrayOf(560f, 112f),
    floatArrayOf(640f, 100f), floatArrayOf(700f, 80f)), 3), 150)

private val FW = FloatArray(16) { k -> 110f + 759f * (1f - 2.0.pow(-k / 12.0).toFloat()) }
private val GCH = listOf(
    intArrayOf(-1, 0, 2, 2, 1, 0), intArrayOf(3, 2, 0, 0, 0, 3),
    intArrayOf(-1, 3, 2, 0, 1, 0), intArrayOf(1, 3, 3, 2, 1, 1))

private fun hsl(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m, alpha)
}

private fun parseHex(s: String): Color {
    val v = s.removePrefix("#")
    return when (v.length) {
        6 -> Color(0xFF000000L.toInt() or v.toLong(16).toInt())
        8 -> Color(v.toLong(16).toInt())
        else -> Color.Gray
    }
}

private fun DrawScope.rrect(x0: Float, y0: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    moveTo(x0 + r, y0)
    arcTo(Rect(x0 + w - r * 2, y0, x0 + w, y0 + r * 2), -90f, 90f, false)
    arcTo(Rect(x0 + w - r * 2, y0 + h - r * 2, x0 + w, y0 + h), 0f, 90f, false)
    arcTo(Rect(x0, y0 + h - r * 2, x0 + r * 2, y0 + h), 90f, 90f, false)
    arcTo(Rect(x0, y0, x0 + r * 2, y0 + r * 2), 180f, 90f, false)
    close()
}

private fun DrawScope.tag(tm: TextMeasurer, text: String, cx: Float, cy: Float, alignCenter: Boolean = true) {
    // Density 1, same reason as `label()`: the scene canvas already applies the
    // display density, so an `sp` resolved at the real density was multiplied by it
    // twice. Sax's BREATH/REED/GROWL tags measured 33 px tall against the
    // reference's 16 (2.06x) until this was pinned.
    val layout = tm.measure(text, style = TextStyle(color = Color(0xCCF3E6CC), fontSize = 8.sp,
        fontFamily = FxFonts.mono), density = Density(1f, 1f))
    drawText(layout, topLeft = Offset(if (alignCenter) cx - layout.size.width / 2f else cx, cy - 8f))
}

private fun DrawScope.mic(cx: Float, cy: Float, r: Float, col: Color = Color(0xFF2A2E34)) {
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFAEB6C0), col),
        center = Offset(cx - r * 0.3f, cy - r * 0.3f), radius = r), radius = r, center = Offset(cx, cy))
    for (k in -2..2) {
        drawLine(Color(0x88000000), Offset(cx - r * 0.8f, cy + k * r * 0.32f),
            Offset(cx + r * 0.8f, cy + k * r * 0.32f), 0.8f)
    }
}

// ---------------------------------------------------------------------------
// Instrument scenes.
// ---------------------------------------------------------------------------

fun DrawScope.scenePiano(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val hard = v[0]; val lid = v[1]; val sus = v[2]; val room = v[3]; val wid = v[4]
    val tone = v[5]; val body = v[6]; val space = v[7]
    val kind = intArrayOf(0, 1, 2, 3, 0, 1).getOrElse(st.presetIndex) { 0 }
    val brt = 0.2f + 0.65f * lid * (0.55f + 0.45f * tone)
    val pal = listOf(
        listOf("#4a2a16", "#1a0d07"), listOf("#1b2027", "#0b0e12"),
        listOf("#5a341c", "#22110a"), listOf("#101018", "#050508"))[kind]

    drawRect(brush = Brush.linearGradient(listOf(parseHex(pal[0]), parseHex(pal[1])),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    for (k in 0 until 24) {
        val yy = y0 + rh * k / 24f
        val p = Path()
        var u = 0f
        while (u <= rw) {
            val y = yy + sin(u * 0.01f + k) * 3f + sin(u * 0.043f + k * 2f) * 1.2f
            if (u == 0f) p.moveTo(x0 + u, y) else p.lineTo(x0 + u, y)
            u += 12f
        }
        drawPath(p, Color(0x11000000), style = Stroke(width = 1f))
    }
    val lx = if (kind == 3) x0 + rw * 0.5f else x0 + rw * 0.22f
    drawCircle(brush = Brush.radialGradient(
        listOf(Color(0xFFFFE2AA).copy(alpha = 0.1f + 0.32f * brt), Color.Transparent),
        center = Offset(lx, y0), radius = rh * 1.1f), radius = rh * 1.1f, center = Offset(lx, y0))

    val nS = 46
    val top = y0 + rh * 0.2f
    val bot = y0 + rh * 0.6f
    val dt = max(0f, min(0.1f, t - st.pianoT)); st.pianoT = t
    val ev = floor(t * 2.4f).toInt()
    if (ev != st.pianoEv) { st.pianoEv = ev; st.pianoE[(ev * 17 + 5) % nS] = 1f }
    for (k in 0 until nS) st.pianoE[k] *= exp(-dt * (2.6f - sus * 2.2f))

    val harp = Path().apply {
        moveTo(x0 + rw * 0.03f, bot + 8f)
        for (k in 0..24) {
            val u = k / 24f
            lineTo(x0 + rw * (0.03f + 0.94f * u), top + (bot - top) * 0.5f * u.pow(0.9f) - 12f)
        }
        lineTo(x0 + rw * 0.97f, bot + 8f); close()
    }
    drawPath(harp, Color(0x29C9A45C))
    // `if(kind==1&&!m){...}`: the felt-strip marker of the upright. A 3 px post from
    // 30% down plus a filled 22 px disc beside it, in near-black `#07090c`. The port
    // had neither, so `kind==1` (the second preset) showed no marker at all.
    if (kind == 1) {
        drawRect(Color(0xFF07090C), topLeft = Offset(x0 + 54f, y0 + rh * 0.3f), size = Size(3f, rh * 0.4f))
        drawCircle(Color(0xFF07090C), radius = 22f, center = Offset(x0 + 55f, y0 + rh * 0.3f))
    }
    if (kind == 2) drawRect(Color(0xD98A1C24), topLeft = Offset(x0, top + (bot - top) * 0.38f),
        size = Size(rw, 9f))

    for (k in 0 until nS) {
        val u = k / (nS - 1f)
        val px = x0 + rw * (0.05f + 0.9f * u)
        val y0s = top + (bot - top) * 0.5f * u.pow(0.9f)
        val len = bot - y0s
        val pb = px + (x0 + rw * 0.5f - px) * 0.1f
        val amp = 3.6f * st.pianoE[k]
        val p = Path()
        for (s in 0..10) {
            val yy = y0s + len * s / 10f
            val dx = sin(Math.PI.toFloat() * s / 10f) * amp * sin(t * (40f + u * 60f))
            val xx = px + (pb - px) * s / 10f + dx
            if (s == 0) p.moveTo(xx, yy) else p.lineTo(xx, yy)
        }
        drawPath(p, color = if (kind == 1) Color(0xFFC9D3DF) else if (u < 0.3f) Color(0xFFD49A5A) else Color(0xFFE6DCC2),
            style = Stroke(width = 0.8f + (1f - u) * 1.5f, cap = StrokeCap.Round), alpha = 0.5f + 0.45f * brt)
        drawCircle(Color(0xFFB99A58), radius = 2.2f, center = Offset(px, y0s - 5f))
        if (brt > 0.25f && st.pianoE[k] > 0.15f) {
            drawRect(Color.White, topLeft = Offset(px - 1f, y0s + len * (0.3f + 0.4f * sin(t * 3f + k))),
                size = Size(2f, 6f), alpha = st.pianoE[k] * brt)
        }
    }
    val hw = (rw * 0.9f / nS) * 2.3f * (0.6f + 0.7f * hard)
    for (k in 0 until nS step 2) {
        val u = k / (nS - 1f)
        val px0 = x0 + rw * (0.05f + 0.9f * u)
        val px = px0 + (x0 + rw * 0.5f - px0) * 0.1f
        val hh = 20f
        val hy = bot + 4f - max(0f, st.pianoE[k] - 0.55f) * 40f
        drawRect(Color(0xFF2A1810), topLeft = Offset(px - 1.5f, hy + hh * 0.5f), size = Size(3f, 26f))
        val f = 0.55f + 0.4f * (1f - hard)
        drawOval(brush = Brush.radialGradient(
            listOf(Color((240 * f).toInt(), (228 * f).toInt(), (200 * f).toInt()), Color(0xFF6B5A3A)),
            center = Offset(px, hy), radius = hh), topLeft = Offset(px - hw / 2f, hy - hh * 0.55f),
            size = Size(hw, hh * 1.1f))
    }
    if (kind == 3) drawRect(brush = Brush.linearGradient(
        listOf(Color.Transparent, Color(0x14FFFFFF), Color.Transparent),
        start = Offset(x0, y0), end = Offset(x0 + rw, y0 + rh * 0.5f)),
        topLeft = Offset(x0, y0), size = Size(rw, rh * 0.5f))
    if (space > 0.05f) {
        val u = (t * 0.3f) % 1f
        drawOval(Color(0xFFFFE2AA).copy(alpha = (1f - u) * space * 0.4f),
            topLeft = Offset(x0 + rw * 0.5f - rw * (0.15f + u * 0.6f), y0 + rh * 0.5f - rh * (0.1f + u * 0.35f)),
            size = Size(rw * (0.15f + u * 0.6f) * 2, rh * (0.1f + u * 0.35f) * 2),
            style = Stroke(width = 2f))
    }
    drawCircle(brush = Brush.radialGradient(listOf(Color.Transparent, Color(0xAA000000)),
        center = Offset(x0 + rw / 2f, y0 + rh / 2f), radius = rw * 0.62f),
        radius = rw * 0.62f, center = Offset(x0 + rw / 2f, y0 + rh / 2f))

    val ly = y0 + 16f + (1f - lid) * 100f
    drawPath(Path().apply {
        moveTo(440f, y0); lineTo(x0 + rw, y0); lineTo(x0 + rw, ly + 22f); lineTo(540f, ly); close()
    }, brush = Brush.linearGradient(listOf(Color(0xFF05060A), Color(0xFF1A1D26)),
        start = Offset(0f, y0), end = Offset(0f, ly)))
    drawLine(Color(0xFFC9A45C), Offset(720f, 40f), Offset(720f, ly + 16f), 2f)
    mic(720f, 40f, 9f, Color(0xFF6B5428))
    tag(tm, "LID ${(lid * 100).roundToInt()}%", 720f, 62f)

    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFF6E6BD), Color(0xFF8A6A2C)),
        center = Offset(40f, 154f), radius = 16f), radius = 15f, center = Offset(44f, 158f))
    val an = Math.toRadians((-135f + 270f * hard).toDouble()).toFloat()
    drawLine(Color(0xFF2A1A10), Offset(44f + 4f * cos(an), 158f + 4f * sin(an)),
        Offset(44f + 12f * cos(an), 158f + 12f * sin(an)), 2.4f)
    tag(tm, "FELT ${(hard * 100).roundToInt()}%", 44f, 184f)

    for ((k, px) in listOf(350f, 389f, 428f).withIndex()) {
        val dy = if (k == 2) sus * 12f else 0f
        drawPath(Path().apply {
            moveTo(px - 12f, 196f + dy); lineTo(px + 12f, 196f + dy)
            lineTo(px + 9f, 181f + dy); lineTo(px - 9f, 181f + dy); close()
        }, brush = Brush.linearGradient(listOf(Color(0xFF7A5E2A), Color(0xFFF2D58A), Color(0xFF6B5224)),
            start = Offset(px - 14f, 0f), end = Offset(px + 14f, 0f)))
    }
    tag(tm, "PEDAL ${(sus * 100).roundToInt()}%", 428f, 212f)

    val my = 30f + room * 110f
    drawLine(Color(0x40FFFFFF), Offset(80f, y0), Offset(80f, my), 1f)
    mic(80f, my, 11f)
    tag(tm, "ROOM ${(room * 100).roundToInt()}%", 80f, my + 24f)
    val rx = 430f + wid * 300f
    val lx2 = 778f - rx
    drawLine(Color(0x40FFFFFF), Offset(lx2, 118f), Offset(rx, 118f), 1f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
    mic(lx2, 118f, 9f); mic(rx, 118f, 9f)
    tag(tm, "WIDTH ${(wid * 100).roundToInt()}%", rx, 140f)
}

fun DrawScope.sceneOrgan(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val dtt = max(0f, min(0.1f, t - st.organT)); st.organT = t
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF3D2314), Color(0xFF150A05)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    for (k in 0 until 40) {
        val yy = y0 + rh * k / 40f
        val p = Path()
        var u = 0f
        while (u <= rw) {
            val y = yy + sin(u * 0.012f + k * 1.7f) * 4f
            if (u == 0f) p.moveTo(x0 + u, y) else p.lineTo(x0 + u, y)
            u += 14f
        }
        drawPath(p, Color(0x14000000), style = Stroke(width = 1f))
    }
    val cap = listOf("#6b3d22", "#6b3d22", "#f2eadb", "#f2eadb", "#1c1a18", "#f2eadb", "#1c1a18", "#1c1a18", "#f2eadb")
    val nm = listOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'")
    val up = FloatArray(9) { (v[it] * 8f).roundToInt().toFloat() }
    val lo = FloatArray(9) { (v[9 + it] * 8f).roundToInt().toFloat() }
    val tgt = floatArrayOf(0f, 2.2f, 9f)[(v[25] * 2f).roundToInt().coerceIn(0, 2)]
    st.organLs += (tgt - st.organLs) * (1f - exp(-dtt * (if (tgt > st.organLs) 0.9f else 0.5f)))
    st.organLp += st.organLs * dtt

    fun manual(ox: Float, vals: FloatArray, title: String) {
        drawPath(rrect(ox - 16f, 2f, 238f, 200f, 10f),
            brush = Brush.linearGradient(listOf(Color(0xFF1D1814), Color(0xFF0E0B09)), start = Offset(0f, 0f), end = Offset(0f, 200f)))
        drawPath(rrect(ox - 16f, 2f, 238f, 200f, 10f), Color(0x55C9A45C), style = Stroke(width = 1.2f))
        val lt = tm.measure(title, style = TextStyle(color = Color(0xFFF2D58A), fontSize = 9.sp,
            fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
        drawText(lt, topLeft = Offset(ox - 6f, 16f - lt.size.height / 2f))
        val rt = tm.measure("${vals[0].toInt()}${vals[1].toInt()} ${vals[2].toInt()}${vals[3].toInt()}${vals[4].toInt()}${vals[5].toInt()} ${vals[6].toInt()}${vals[7].toInt()}${vals[8].toInt()}",
            style = TextStyle(color = Color(0xFFF2D58A), fontSize = 9.sp, fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
        drawText(rt, topLeft = Offset(ox + 214f - rt.size.width, 16f - rt.size.height / 2f))
        for (k in 0 until 9) {
            val px = ox + k * 26f; val top = 38f; val len = 124f
            val cy = top + vals[k] / 8f * len
            drawPath(rrect(px - 3f, top - 6f, 6f, len + 12f, 3f), color = Color(0x99000000))
            drawRect(if (cap[k] == "#1c1a18") Color(0xFF555555) else parseHex(cap[k]),
                topLeft = Offset(px - 1.6f, top), size = Size(3.2f, cy - top))
            drawPath(rrect(px - 11f, cy - 8f, 22f, 16f, 4f), color = parseHex(cap[k]))
            val vt = tm.measure(vals[k].toInt().toString(), style = TextStyle(
                color = if (cap[k] == "#f2eadb") Color(0xFF2A1A10) else Color(0xFFF2EADB),
                fontSize = 9.sp, fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
            drawText(vt, topLeft = Offset(px - vt.size.width / 2f, cy + 3.2f - vt.size.height / 2f))
            val nt = tm.measure(nm[k], style = TextStyle(color = Color(0xFFC9B88F), fontSize = 7.sp, fontFamily = FxFonts.mono), density = Density(1f, 1f))
            drawText(nt, topLeft = Offset(px - nt.size.width / 2f, top - 12f - nt.size.height / 2f))
        }
    }
    manual(24f, up, "UPPER")
    manual(278f, lo, "LOWER")

    val dcx = 578f; val dcy = 112f
    val vi = (v[22] * 5f).roundToInt().coerceIn(0, 5)
    val lab = listOf("V1", "V2", "V3", "C1", "C2", "C3")
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFF5B4630), Color(0xFF1E140C)),
        center = Offset(dcx - 8f, dcy - 8f), radius = 34f), radius = 24f, center = Offset(dcx, dcy))
    for (k in 0 until 6) {
        val an = Math.toRadians((-200f + k * 40f).toDouble()).toFloat()
        val lt = tm.measure(lab[k], style = TextStyle(color = if (k == vi) Color(0xFFF2D58A) else Color(0xFFC9B88F),
            fontSize = 8.sp, fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
        drawText(lt, topLeft = Offset(dcx + 38f * cos(an) - lt.size.width / 2f, dcy + 38f * sin(an) + 3f - lt.size.height / 2f))
        drawCircle(if (k == vi) Color(0xFFF2D58A) else Color(0xFF5A4A30), radius = 1.6f,
            center = Offset(dcx + 29f * cos(an), dcy + 29f * sin(an)))
    }
    val va = Math.toRadians((-200f + vi * 40f).toDouble()).toFloat()
    drawLine(Color(0xFFF2EADB), Offset(dcx + 5f * cos(va), dcy + 5f * sin(va)),
        Offset(dcx + 20f * cos(va), dcy + 20f * sin(va)), 3f)
    tag(tm, "VIBRATO / CHORUS", dcx + 8f, 150f)

    val cx = 764f; val cy = 186f
    drawPath(rrect(cx - 42f, 150f, 84f, 54f, 8f), brush = Brush.linearGradient(
        listOf(Color(0xFF2A1A10), Color(0xFF3D2616), Color(0xFF1E120A)), start = Offset(cx - 44f, 0f), end = Offset(cx + 44f, 0f)))
    drawPath(rrect(cx - 42f, 150f, 84f, 54f, 8f), Color(0x44C9A45C), style = Stroke(width = 1f))
    drawOval(Color(0x99070503), topLeft = Offset(cx - 38f, 157f), size = Size(40f, 18f))
    val hx = cos(st.organLp * 1.2f) * 13f
    drawOval(Color(0xFFD9C08A), topLeft = Offset(cx - 18f + hx - (6f + abs(sin(st.organLp * 1.2f)) * 3f), 162f),
        size = Size((6f + abs(sin(st.organLp * 1.2f)) * 3f) * 2, 8f))
    drawOval(Color(0x99070503), topLeft = Offset(cx - 2f, 162f), size = Size(40f, 36f))
    for (k in 0 until 6) {
        val ph2 = st.organLp * 0.8f + k * 1.05f
        val px = cx + 18f + sin(ph2) * 15f
        drawRect(Color(0xFFC9A45C), topLeft = Offset(px - 1f, 166f), size = Size(2f, 28f),
            alpha = 0.25f + 0.6f * max(0f, cos(ph2)))
    }
}

fun DrawScope.sceneSax(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val br = v[0]; val rd = v[1]; val gr = v[2]; val vb = v[3]; val vr = v[4]; val tone = v[5]; val body = v[6]; val sp = v[7]
    val kind = st.presetIndex % 6
    val pal = listOf(
        listOf("#0e2229", "#04090c", "#e0a45a"), listOf("#171c26", "#05080c", "#d9c8b0"),
        listOf("#2a0e12", "#080305", "#ff5a4a"), listOf("#101a3a", "#05070f", "#6ab0ff"),
        listOf("#12261e", "#060a07", "#ffd36a"), listOf("#1c1c28", "#06060a", "#c9a45c"))[kind]
    drawRect(brush = Brush.linearGradient(listOf(parseHex(pal[0]), parseHex(pal[1])),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    for (k in 0 until 5) {
        val bx = x0 + rw * (0.15f + 0.18f * k) + sin(t * 0.2f + k * 2f) * 60f
        val by = y0 + rh * (0.35f + 0.2f * sin(t * 0.15f + k))
        val r = 130f + 60f * sin(t * 0.3f + k)
        drawCircle(brush = Brush.radialGradient(listOf(parseHex(pal[2]).copy(alpha = 0.16f), Color.Transparent),
            center = Offset(bx, by), radius = r), radius = r, center = Offset(bx, by),
            alpha = 0.35f + 0.65f * br)
    }
    val n = SXP.size
    for (pass in 0 until 3) for (i in 1 until n) {
        val u = i / n.toFloat()
        val w = (9f + u * 9f + (if (u > 0.84f) ((u - 0.84f) / 0.16f).pow(2) * 30f else 0f)) *
                (if (pass == 0) 1.35f else if (pass == 1) 1f else 0.25f)
        val aa = SXP[i - 1]; val bb = SXP[i]
        val off = if (pass == 2) -2.5f else 0f
        val col = when (pass) {
            0 -> Color(0xFF2A1C08)
            1 -> hsl(34f + tone * 10f, 0.55f + body * 0.2f, 0.28f + tone * 0.24f)
            else -> Color(0xFFFFF0C8).copy(alpha = 0.3f + 0.4f * tone)
        }
        drawLine(col, Offset(aa[0] + off, aa[1] + off), Offset(bb[0] + off, bb[1] + off), w, cap = StrokeCap.Round)
    }
    val e = SXP[n - 1]; val e0 = SXP[n - 6]
    val ang = Math.toDegrees(kotlin.math.atan2(e[1] - e0[1], e[0] - e0[0]).toDouble()).toFloat()
    rotate(ang, pivot = Offset(e[0], e[1])) {
        drawOval(brush = Brush.radialGradient(listOf(Color(0xFFFFE2AA).copy(alpha = 0.5f + 0.5f * br), Color(0xFF1A0F04)),
            center = Offset(e[0], e[1]), radius = 30f), topLeft = Offset(e[0] - 10f, e[1] - 30f), size = Size(20f, 60f))
    }
    val dt = max(0f, min(0.1f, t - st.saxT)); st.saxT = t
    if (st.saxP.isEmpty()) repeat(90) {
        st.saxP.add(floatArrayOf(Random.nextFloat(), 0.6f + Random.nextFloat() * 0.8f, Random.nextFloat() * 6.283f))
    }
    for (p in st.saxP) {
        p[0] += dt * (0.1f + 0.9f * br) * p[1]
        if (p[0] > 1f) p[0] -= 1f
        val i = min(n - 2, floor(p[0] * n).toInt())
        val q = SXP[i]; val q2 = SXP[i + 1]
        val nx = -(q2[1] - q[1]); val ny = q2[0] - q[0]
        val nl = hypot(nx, ny).let { if (it == 0f) 1f else it }
        val off = sin(p[0] * 22f - t * (4f + vr * 14f)) * (3f + vb * 13f) + (Random.nextFloat() - 0.5f) * gr * 12f
        drawCircle(if (gr > 0.5f && p[2] > 3f) Color(0xFFFF7A5A) else Color(0xFFFFF3DC),
            radius = 1.2f + br * 1.6f, center = Offset(q[0] + nx / nl * off, q[1] + ny / nl * off),
            alpha = sin(p[0] * Math.PI.toFloat()) * (0.35f + 0.55f * br))
    }
    val ra = SXP[0]
    val reed = Path()
    for (k in 0..8) {
        val ux = ra[0] - 22f + k * 4f
        val uy = ra[1] - 8f + sin(t * (30f + rd * 40f) + k * 0.5f) * rd * 5f * (k / 8f)
        if (k == 0) reed.moveTo(ux, uy) else reed.lineTo(ux, uy)
    }
    drawPath(reed, Color(0xFFF0DCAE), style = Stroke(width = 2f))
    if (sp > 0.05f) {
        val u = (t * 0.35f) % 1f
        drawCircle(parseHex(pal[2]), radius = 30f + u * 150f, center = Offset(e[0], e[1]),
            style = Stroke(width = 2f), alpha = (1f - u) * sp * 0.5f)
    }
    val hs = listOf(
        floatArrayOf(0.2f, 0.32f, 0.44f, 0.56f, 0.68f).map { u ->
            val q = SXP[(u * 159f).roundToInt()]; floatArrayOf(q[0], q[1], 17f)
        })
    val nmL = listOf("BREATH", "REED", "GROWL", "VIBRATO", "RATE")
    hs[0].forEachIndexed { j, h ->
        val value = v[j]
        drawCircle(brush = Brush.radialGradient(
            listOf(Color.White, Color(0xFFE9E0D4), Color(0xFF9D8F7A)),
            center = Offset(h[0] - 4f, h[1] - 4f), radius = h[2]), radius = h[2] - 4f, center = Offset(h[0], h[1]))
        drawArc(parseHex(pal[2]), -135f, 270f * value, false,
            topLeft = Offset(h[0] - h[2], h[1] - h[2]), size = Size(h[2] * 2, h[2] * 2),
            style = Stroke(width = 2.4f))
        tag(tm, "${nmL[j]} ${(value * 100).roundToInt()}%", h[0], h[1] + h[2] + 13f)
    }
}

fun DrawScope.sceneStrings(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val pr = v[0]; val spd = v[1]; val mot = v[2]; val mor = v[3]; val tim = v[4]
    val hue = v[5] * 360f; val tone = v[6]; val bd = v[7]; val sp = v[8]
    val dt = max(0f, min(0.1f, t - st.strT)); st.strT = t
    val act = (sin(t * 0.9f) + sin(t * 0.37f + 1f)) > -0.5f
    val target = if (act) 0.3f + 0.7f * pr else 0f
    st.strSol += (target - st.strSol) * (1f - exp(-dt * (0.6f + (1f - tim) * 3.4f)))
    val sol = st.strSol
    fun hs(o: Float, s2: Float = 85f, l: Float = 62f, al: Float = 1f) = hsl(hue + o, s2 / 100f, l / 100f, al)

    drawRect(brush = Brush.linearGradient(listOf(hs(0f, 45f, 13f), hs(20f, 50f, 3f)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    listOf(Triple(0.2f, 0.3f, 0f), Triple(0.75f, 0.6f, 90f), Triple(0.5f, 0.1f, 200f)).forEachIndexed { k, (u, w, o) ->
        val bx = x0 + rw * u + sin(t * 0.2f + k) * 40f
        val by = y0 + rh * w
        drawCircle(brush = Brush.radialGradient(listOf(hs(o, 80f, 50f, 0.22f), Color.Transparent),
            center = Offset(bx, by), radius = 220f), radius = 220f, center = Offset(bx, by))
    }
    fun vp(): Path = Path().apply {
        moveTo(228f, 66f); addOval(Rect(150f - 78f, 66f - 52f, 150f + 78f, 66f + 52f))
        moveTo(348f, 66f); addOval(Rect(292f - 56f, 66f - 40f, 292f + 56f, 66f + 40f))
        addRect(Rect(205f, 34f, 255f, 98f))
    }
    val nc = 1 + (mor * 5f).roundToInt()
    for (k in nc - 1 downTo 1) {
        translate(210f, 66f) {
            scale(1f - 0.05f * k, 1f - 0.05f * k, pivot = Offset.Zero) {
                translate(-210f - 16f * k, -66f + (if (k % 2 == 1) 1f else -1f) * 12f * k) {
                    drawPath(vp(), hs(40f * k, 80f, 62f, 0.5f / k), style = Stroke(width = 1f))
                }
            }
        }
    }
    for (k in 0 until 5) {
        translate(210f, 66f) {
            scale(1f - 0.1f * k, 1f - 0.1f * k, pivot = Offset.Zero) {
                translate(-210f, -66f) {
                    drawPath(vp(), hs(k * 8f, 85f, 65f, (0.55f - 0.08f * k) * (0.5f + 0.5f * (1f - sol))),
                        style = Stroke(width = 0.9f))
                }
            }
        }
    }
    clipPath(vp()) {
        drawRect(brush = Brush.linearGradient(listOf(hs(10f, 70f, 28f + tone * 10f), hs(30f, 75f, 18f + bd * 8f)),
            start = Offset(70f, 0f), end = Offset(350f, 0f)), topLeft = Offset(60f, 10f), size = Size(300f, 116f),
            alpha = sol)
        val bigA = 2f + tone * 4f; val bigB = 3f + bd * 4f + sin(t * 0.2f) * 0.3f
        val thr = 0.16f + 0.08f * sin(t * 1.7f) * pr
        for (i in 0 until 40) for (j in 0 until 24) {
            val u = i / 39f; val w = j / 23f
            val f = cos(bigA * Math.PI.toFloat() * u) * cos(bigB * Math.PI.toFloat() * w) -
                    cos(bigB * Math.PI.toFloat() * u) * cos(bigA * Math.PI.toFloat() * w)
            if (abs(f) < thr) {
                drawRect(hs(50f, 60f, 88f, 0.35f + 0.45f * sol),
                    topLeft = Offset(72f + u * 274f + sin(i * 7f + j * 3f + t * 8f) * pr * 1.2f,
                        14f + w * 104f + cos(i * 5f + j * 9f + t * 7f) * pr * 1.2f),
                    size = Size(1.5f, 1.5f))
            }
        }
    }
    // particles: the body dissolves, the bow gathers them back
    var acc = (30f + 260f * (1f - sol)) * (0.5f + 0.5f * (mot + spd) / 1.5f) * dt
    while (acc >= 1f && st.strP.size < 480) {
        acc -= 1f
        val px = 72f + Random.nextFloat() * 274f
        val py = 14f + Random.nextFloat() * 104f
        val inBody = ((px - 150f) / 78f).pow(2) + ((py - 66f) / 52f).pow(2) < 1f ||
                     ((px - 292f) / 56f).pow(2) + ((py - 66f) / 40f).pow(2) < 1f ||
                     (px > 205f && px < 255f && py > 34f && py < 98f)
        if (inBody) st.strP.add(floatArrayOf(px, py, px, py, (Random.nextFloat() - 0.3f) * 14f, -4f - Random.nextFloat() * 16f,
            0f, 0.8f + sp * 3f + Random.nextFloat() * 1.2f, Random.nextFloat() * 60f, 1f + Random.nextFloat() * 2f))
    }
    for (i in st.strP.indices.reversed()) {
        val p = st.strP[i]
        p[6] += dt
        if (p[6] > p[7]) { st.strP[i] = st.strP.last(); st.strP.removeAt(st.strP.size - 1); continue }
        val kk = if (sol > 0.5f) 3f * sol else 0f
        p[4] += ((p[2] - p[0]) * kk * 2f + sin(t * 3f + p[1] * 0.1f) * mot * 30f) * dt
        p[5] += ((p[3] - p[1]) * kk * 2f) * dt
        p[4] *= 0.985f; p[5] *= 0.985f
        p[0] += p[4] * dt; p[1] += p[5] * dt
        drawCircle(hs(p[8], 85f, 70f), radius = p[9] * (1f - p[6] / p[7] * 0.4f),
            center = Offset(p[0], p[1]), alpha = (1f - p[6] / p[7]) * 0.9f)
    }
    // neck, scroll, strings, bridge, f-holes
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF1A1210), Color(0xFF0A0706)),
        start = Offset(0f, 56f), end = Offset(0f, 76f)), topLeft = Offset(340f, 56f), size = Size(206f, 20f))
    drawCircle(hs(20f, 60f, 70f, 0.6f), radius = 13f, center = Offset(570f, 66f), style = Stroke(width = 1.2f))
    drawArc(hs(20f, 60f, 70f, 0.6f), 28f, 270f, false, topLeft = Offset(563f, 59f), size = Size(14f, 14f), style = Stroke(width = 1.2f))
    // The preview draws three concentric arcs at (570,66): r13 full, r7 from 0.5 to
    // 5.2 rad, and r3 from 0 to 4 rad. The innermost was missing.
    drawArc(hs(20f, 60f, 70f, 0.6f), 0f, 229f, false, topLeft = Offset(567f, 63f), size = Size(6f, 6f), style = Stroke(width = 1.2f))
    for ((qx, qy) in listOf(540f to 50f, 552f to 82f)) {
        drawOval(hs(20f, 40f, 60f, 0.8f), topLeft = Offset(qx - 5f, qy - 3f), size = Size(10f, 6f))
    }
    drawPath(Path().apply {
        moveTo(190f, 48f); quadraticBezierTo(178f, 66f, 190f, 84f)
        moveTo(236f, 48f); quadraticBezierTo(248f, 66f, 236f, 84f)
    }, hs(30f, 80f, 60f, 0.7f), style = Stroke(width = 1.6f))
    val bx2 = 175f + sin(t * (0.6f + spd * 3f)) * (20f + spd * 35f)
    for (k in 0 until 4) {
        val ys = 58.5f + k * 5f
        val vy = sin(t * (80f + k * 20f)) * (0.5f + pr * 2f) * (if (k == 1) 1f else 0.6f) * (if (act) 1f else 0.2f)
        drawPath(Path().apply {
            moveTo(84f, ys + (k - 1.5f) * 0.6f); lineTo(215f, ys + vy); lineTo(545f, 58.5f + k * 5f + vy * 0.4f)
        }, hs(40f, 60f, 80f, 0.9f), style = Stroke(width = 0.8f + (3 - k) * 0.2f))
    }
    drawPath(Path().apply { moveTo(209f, 52f); lineTo(221f, 52f); lineTo(224f, 80f); lineTo(206f, 80f); close() },
        hs(10f, 40f, 40f))
    translate(bx2, 66f) {
        rotate(6.9f) {
            drawRect(hs(30f, 30f, 92f, 0.95f), topLeft = Offset(-1.5f - pr * 2f, -62f),
                size = Size(3f + pr * 4f, 128f))
        }
    }
    if (sp > 0.05f) {
        val u = (t * 0.3f) % 1f
        drawOval(hs(0f, 80f, 65f), topLeft = Offset(250f - (100f + u * 420f), 66f - (60f + u * 130f)),
            size = Size((100f + u * 420f) * 2, (60f + u * 130f) * 2), style = Stroke(width = 2f),
            alpha = (1f - u) * sp * 0.4f)
    }
    tag(tm, "BOW ${(pr * 100).roundToInt()} · ${(spd * 100).roundToInt()}", bx2, 140f)
}

fun DrawScope.sceneGuitar(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val bt = (v[0] * 3f).roundToInt(); val pick = v[1]; val str = v[2]; val mute = v[3]; val bend = v[4]
    val pu = v[5]; val fin = (v[6] * 3f).roundToInt(); val tone = v[7]; val res = v[8]; val sp = v[9]
    val elec = bt >= 2
    val bx = if (elec) 676f else 708f
    val pal = listOf(
        listOf("#241810", "#07060a", "#e0a060"), listOf("#2a2418", "#0a0906", "#e8d0a0"),
        listOf("#141a26", "#05070c", "#6ab0ff"), listOf("#241010", "#080304", "#ff6a4a"))[bt]
    drawRect(brush = Brush.linearGradient(listOf(parseHex(pal[0]), parseHex(pal[1])),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    drawCircle(brush = Brush.radialGradient(listOf(parseHex(pal[2]).copy(alpha = 0.16f), Color.Transparent),
        center = Offset(560f, 90f), radius = 420f), radius = 420f, center = Offset(560f, 90f))
    val finCols = if (elec)
        listOf(listOf("#d89a40", "#6a2a10"), listOf("#f0eadc", "#bdb4a0"), listOf("#26262c", "#0c0c10"), listOf("#b02a2a", "#5a1010"))[fin]
    else
        listOf(listOf("#e8c488", "#b88a48"), listOf("#d8943c", "#5a2a10"), listOf("#3a2e26", "#14100c"), listOf("#c88838", "#7a4a18"))[fin]
    val bodyPath = Path().apply {
        if (!elec) addOval(Rect(716f - 178f, 92f - 128f, 716f + 178f, 92f + 128f))
        else if (bt == 2) {
            addOval(Rect(716f - 168f, 92f - 112f, 716f + 168f, 92f + 112f))
            addOval(Rect(616f - 38f, -8f - 38f, 616f + 38f, -8f + 38f))
            addOval(Rect(616f - 38f, 190f - 38f, 616f + 38f, 190f + 38f))
        } else {
            addOval(Rect(716f - 170f, 96f - 112f, 716f + 170f, 96f + 112f))
            addOval(Rect(620f - 44f, 196f - 44f, 620f + 44f, 196f + 44f))
        }
    }
    clipPath(bodyPath) {
        drawPath(bodyPath, brush = Brush.radialGradient(listOf(parseHex(finCols[0]), parseHex(finCols[1])),
            center = Offset(700f, 70f), radius = 200f))
        drawPath(bodyPath, if (elec) Color(0x88000000) else Color(0xFFF2E6C8), style = Stroke(width = 3f))
        for (k in 0 until 30) {
            val p = Path()
            var u = 520f
            while (u <= 800f) {
                val yy = -30f + k * 8f + sin(u * 0.03f + k) * 3f
                if (u == 520f) p.moveTo(u, yy) else p.lineTo(u, yy)
                u += 14f
            }
            drawPath(p, Color(0x1A000000), style = Stroke(width = 1f))
        }
        if (!elec) {
            val r0 = if (bt == 1) 40f else 36f
            drawCircle(brush = Brush.radialGradient(listOf(Color.Black, Color(0xFF1A0E06)),
                center = Offset(650f, 92f), radius = r0), radius = r0, center = Offset(650f, 92f))
            for (k in 0..2) drawCircle(if (k == 1) Color(0xFF1A1208) else Color(0xFFF2E6C8), radius = r0 + 3f + k * 3f,
                center = Offset(650f, 92f), style = Stroke(width = if (k == 1) 2f else 1.2f))
            if (res > 0.05f) drawCircle(brush = Brush.radialGradient(
                listOf(Color(0xFFFFB45A).copy(alpha = 0.35f * res), Color.Transparent),
                center = Offset(650f, 92f), radius = r0 + 60f), radius = r0 + 60f, center = Offset(650f, 92f))
            // `fillStyle '#1a1008'`: the acoustic bridge — a dark block, a light
            // saddle rail and six saddle dots. It belongs to this branch only; the
            // preview nests it inside `if(!elec)`.
            val bw = if (bt == 1) 30f else 18f
            val bx0 = if (bt == 1) 694f else 698f
            drawPath(rrect(bx0, 64f, bw, 56f, 3f), color = Color(0xFF1A1008))
            drawRect(Color(0xFFEFE6D0), topLeft = Offset(if (bt == 1) 700f else 702f, 66f), size = Size(2f, 52f))
            for (k in 0..5) drawCircle(Color(0xFFD8D0C0), radius = 1.6f,
                center = Offset(if (bt == 1) 712f else 710f, 68f + k * 9.5f))
        } else if (bt == 2) {
            // Preview `else if(bt==2)`: three single-coil rails sunk in a pickguard.
            // This whole branch was missing, which is why the audit found 14 of the
            // preview's 58 guitar colours unused.
            drawRect(Color(0xFFEFE8D8), topLeft = Offset(576f, 16f), size = Size(134f, 150f), alpha = 0.92f)
            for ((idx, px) in listOf(596f, 632f, 668f).withIndex()) {
                drawPath(rrect(px - 7f, 58f, 14f, 66f, 3f), color = Color(0xFFF4F0E6))
                drawPath(rrect(px - 7f, 58f, 14f, 66f, 3f), color = Color(0x33000000), style = Stroke(width = 1f))
                for (q in 0 until 6) drawCircle(Color(0xFF3A3A40), radius = 2.2f, center = Offset(px, 66f + q * 9f))
                if (abs(pu * 2f - idx) < 0.75f) drawPath(rrect(px - 9f, 56f, 18f, 70f, 4f),
                    color = parseHex(pal[2]), style = Stroke(width = 2f))
            }
            drawPath(rrect(694f, 62f, 10f, 60f, 2f), color = Color(0xFFC0C4CC))
            for (k in 0..2) {
                drawCircle(Color(0xFFB8BCC4), radius = 8f, center = Offset(654f + k * 26f, 160f))
                drawCircle(Color(0x88000000), radius = 8f, center = Offset(654f + k * 26f, 160f), style = Stroke(width = 1f))
            }
        } else {
            // Preview `else`: two humbuckers in milled cavities, six screws each, a
            // ring that lights up when the selector is near, a bridge and four
            // gold-plated screws.
            for ((k, px) in listOf(594f, 640f).withIndex()) {
                drawPath(rrect(px - 22f, 58f, 44f, 68f, 5f), brush = Brush.linearGradient(
                    listOf(Color(0xFFE8EAEE), Color(0xFF9AA0AA), Color(0xFFD4D8DE)),
                    start = Offset(px - 22f, 0f), end = Offset(px + 22f, 0f)))
                drawPath(rrect(px - 22f, 58f, 44f, 68f, 5f), color = Color(0x88000000), style = Stroke(width = 1f))
                for (q in 0 until 6) {
                    drawCircle(Color(0xFF2A2A30), radius = 2f, center = Offset(px - 8f, 68f + q * 10f))
                    drawCircle(Color(0xFF2A2A30), radius = 2f, center = Offset(px + 8f, 68f + q * 10f))
                }
                if (abs(pu - k) < 0.55f || pu == 0.5f) drawPath(rrect(px - 24f, 56f, 48f, 72f, 6f),
                    color = parseHex(pal[2]), style = Stroke(width = 2f))
            }
            drawPath(rrect(672f, 62f, 8f, 60f, 2f), color = Color(0xFFC0C4CC))
            drawPath(rrect(706f, 62f, 10f, 60f, 2f), color = Color(0xFFC0C4CC))
            for (qy in listOf(150f, 176f)) for (qx in listOf(654f, 684f)) {
                drawCircle(Color(0xFFD8B050), radius = 8f, center = Offset(qx, qy))
                drawCircle(Color(0x88000000), radius = 8f, center = Offset(qx, qy), style = Stroke(width = 1f))
            }
        }
    }
    // neck: fretboard, binding, fret wires, inlays
    val fb0 = 64f; val fb1 = 118f; val fe = 580f
    drawRect(brush = Brush.linearGradient(listOf(hsl(18f, 0.42f, 0.15f + tone * 0.09f), hsl(14f, 0.46f, 0.09f + tone * 0.07f)),
        start = Offset(0f, fb0), end = Offset(0f, fb1)), topLeft = Offset(104f, fb0), size = Size(fe - 104f, fb1 - fb0))
    for (k in 0 until 26) {
        val yy = fb0 + 1f + k * 2.1f
        val p = Path()
        var u = 104f
        while (u <= fe) {
            val y2 = yy + sin(u * 0.021f + k * 1.3f) * 0.9f
            if (u == 104f) p.moveTo(u, y2) else p.lineTo(u, y2)
            u += 18f
        }
        drawPath(p, if (k % 2 == 1) Color.Black else Color(0xFFB86A40), style = Stroke(width = 0.7f),
            alpha = 0.1f + 0.06f * (k % 3))
    }
    for (k in 1..15) {
        val fx = FW[k]
        if (fx > fe - 4f) break
        drawLine(Color(0x88000000), Offset(fx + 1.4f, fb0 + 1f), Offset(fx + 1.4f, fb1 - 1f), 3f)
        drawLine(Color(0xFFD9D6CC), Offset(fx, fb0 + 1f), Offset(fx, fb1 - 1f), 2f)
        drawLine(Color(0xB3FFFFFF), Offset(fx - 0.5f, fb0 + 1f), Offset(fx - 0.5f, fb1 - 1f), 0.8f)
    }
    // `strokeStyle '#eadfc2'`: the binding along both edges of the fretboard.
    drawLine(Color(0xFFEADFC2), Offset(104f, fb0 + 0.8f), Offset(fe, fb0 + 0.8f), 1.6f)
    drawLine(Color(0xFFEADFC2), Offset(104f, fb1 - 0.8f), Offset(fe, fb1 - 0.8f), 1.6f)
    for (k in listOf(3, 5, 7, 9, 15)) {
        val cx = (FW[k - 1] + FW[k]) / 2f
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color(0xFFB8B0A0)),
            center = Offset(cx - 1f, 89f), radius = 4f), radius = 3.6f, center = Offset(cx, 91f))
        // `fillStyle '#e6dfd0'`: the small dot marking each inlay at the top edge.
        drawCircle(Color(0xFFE6DFD0), radius = 1.2f, center = Offset(cx, fb0 - 3.2f))
    }
    // Second inlay pair, at the octave, with the same top-edge dot.
    val cx12 = (FW[11] + FW[12]) / 2f
    for (yy in listOf(80f, 102f)) {
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color(0xFFB8B0A0)),
            center = Offset(cx12 - 1f, yy - 1f), radius = 4f), radius = 3.5f, center = Offset(cx12, yy))
    }
    drawCircle(Color(0xFFE6DFD0), radius = 1.2f, center = Offset(cx12, fb0 - 3.2f))
    drawRect(Color(0xFFEFE6D0), topLeft = Offset(102f, fb0 - 2f), size = Size(7f, fb1 - fb0 + 4f))
    // headstock
    drawPath(Path().apply {
        if (elec) {
            moveTo(6f, 56f); lineTo(104f, 63f); lineTo(104f, 119f); lineTo(6f, 126f)
            quadraticBezierTo(0f, 91f, 6f, 56f)
        } else {
            moveTo(10f, 46f); lineTo(104f, 60f); lineTo(104f, 122f); lineTo(10f, 136f)
            quadraticBezierTo(0f, 91f, 10f, 46f)
        }
    }, brush = Brush.linearGradient(listOf(Color(0xFF2A1C14), Color(0xFF120C08)),
        start = Offset(0f, 40f), end = Offset(0f, 140f)))
    val pegs = listOf(26f to 48f, 58f to 52f, 90f to 57f, 26f to 134f, 58f to 130f, 90f to 125f)
    // strings with chord, pluck energy
    val ci = (floor(t / 1.7f).toInt()) % 4
    val sh = GCH[ci]
    val ph2 = t % 1.7f
    val dt = max(0f, min(0.1f, t - st.gtrT)); st.gtrT = t
    for (s2 in 0 until 6) {
        val delay = s2 * (0.02f + str * 0.09f)
        val played = sh[s2] >= 0
        if (played && ph2 > delay && ph2 - dt <= delay) st.gtrE[s2] = 1f
        st.gtrE[s2] *= exp(-dt * (1.4f + mute * 8f))
        val ys = 69.5f + s2 * 8f
        val amp = (2f + pick * 5f) * st.gtrE[s2] * (1f - 0.6f * mute)
        val nylon = bt == 1
        val wound = s2 < 3
        val p = Path()
        for (i in 0..30) {
            val u = i / 30f
            val xx = 110f + u * (bx - 110f)
            var yy = ys + sin(Math.PI.toFloat() * u) * amp * sin(t * (60f + s2 * 14f))
            if (s2 == 3) yy -= sin(Math.PI.toFloat() * u) * bend * 12f
            if (i == 0) p.moveTo(xx, yy) else p.lineTo(xx, yy)
        }
        drawPath(p, if (wound) (if (nylon) Color(0xFFD8C890) else Color(0xFFC9A86A)) else (if (nylon) Color(0xFFEEF0EA) else Color(0xFFD4D8DE)),
            style = Stroke(width = listOf(2.6f, 2.1f, 1.6f, 1.2f, 0.95f, 0.75f)[s2]))
        val f = sh[s2]
        if (f > 0 && FW[f] < fe) {
            val fx = (FW[f - 1] + FW[f]) / 2f
            drawCircle(brush = Brush.radialGradient(
                listOf(Color.White, parseHex(pal[2]), parseHex(pal[2]).copy(alpha = 0f)),
                center = Offset(fx, ys), radius = 8f), radius = 8f, center = Offset(fx, ys))
        } else if (f == 0) {
            drawCircle(parseHex(pal[2]), radius = 3f, center = Offset(97f, ys), style = Stroke(width = 1.2f))
        } else {
            drawLine(Color(0x55FFFFFF), Offset(94f, ys - 3f), Offset(100f, ys + 3f), 1.2f)
            drawLine(Color(0x55FFFFFF), Offset(100f, ys - 3f), Offset(94f, ys + 3f), 1.2f)
        }
    }
    // `strokeStyle '#cfd2d8'`: one thin guide per string, from the nut to its peg.
    for (s2 in 0 until 6) {
        val ys = 69.5f + s2 * 8f
        val tp = pegs[s2]
        drawLine(Color(0xFFCFD2D8), Offset(110f, ys),
            Offset(tp.first, tp.second + (if (tp.second < 91f) 6f else -6f)), 0.8f)
    }
    if (ph2 < 0.25f) for (k in 0 until 7) {
        drawCircle(parseHex(pal[2]), radius = 1.5f,
            center = Offset(bx - 70f + cos(k * 1.7f) * ph2 * 60f, 66f + k * 8f + sin(k * 2.3f) * ph2 * 26f),
            alpha = (1f - ph2 * 4f) * 0.9f)
    }
    if (mute > 0.05f) drawPath(rrect(bx - 34f, 60f, 34f, 62f, 6f),
        color = Color(0xB30A0808).copy(alpha = 0.7f * min(1f, mute * 2f)))
    if (sp > 0.05f) {
        val u = (t * 0.3f) % 1f
        drawCircle(parseHex(pal[2]), radius = 60f + u * 330f, center = Offset(700f, 92f),
            style = Stroke(width = 2f), alpha = (1f - u) * sp * 0.4f)
    }
    val nmG = listOf("PICK", "STRUM", "MUTE", "BEND", if (elec) "PICKUP" else "MIC", "FINISH")
    pegs.forEachIndexed { i, q ->
        val value = v[i + 1]
        val an = -2.2f + value * 4.4f
        drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFF4EFE6), Color(0xFF7A7064)),
            center = Offset(q.first - 3f, q.second - 3f), radius = 12f), radius = 10f, center = Offset(q.first, q.second))
        drawLine(Color(0xFF1A120A), Offset(q.first + 2f * cos(an), q.second + 2f * sin(an)),
            Offset(q.first + 8f * cos(an), q.second + 8f * sin(an)), 2.4f)
        tag(tm, nmG[i], q.first, q.second + (if (q.second < 91f) -15f else 24f))
        tag(tm, "${(value * 100).roundToInt()}%", q.first, q.second + (if (q.second < 91f) -6f else 33f))
    }
}

fun DrawScope.sceneAmp(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val gain = v[0]; val ch = (v[6] * 2f).roundToInt(); val boost = v[9] > 0.5f
    val lvl = v[11]; val mpos = v[7]; val mdist = v[8]
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF1A1410), Color(0xFF07050A)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    val heat = min(1f, gain * (0.55f + 0.3f * ch) + (if (boost) 0.2f else 0f))
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFFF8C32).copy(alpha = 0.1f + 0.3f * heat), Color.Transparent),
        center = Offset(400f, 60f), radius = 420f), radius = 420f, center = Offset(400f, 60f))
    drawPath(rrect(24f, 36f, 764f, 92f, 12f), brush = Brush.linearGradient(
        listOf(Color(0xFF2A2622), Color(0xFF16130F), Color(0xFF0C0A08)), start = Offset(0f, 4f), end = Offset(0f, 126f)))
    drawPath(rrect(24f, 36f, 764f, 92f, 12f), Color(0xFFC9A45C), style = Stroke(width = 1.5f))
    listOf(560f, 612f, 664f).forEachIndexed { k, tx ->
        val f = 0.35f + 0.65f * heat * (0.85f + 0.15f * sin(t * 9f + k * 2f))
        drawCircle(brush = Brush.radialGradient(
            listOf(Color(1f, 0.71f - 0.24f * heat, 0.27f, f), Color.Transparent),
            center = Offset(tx, 86f), radius = 26f), radius = 26f, center = Offset(tx, 86f))
        drawPath(rrect(tx - 11f, 52f, 22f, 56f, 11f), color = Color(0xFF0A0806))
        drawPath(rrect(tx - 11f, 52f, 22f, 56f, 11f), Color(0x30FFFFFF), style = Stroke(width = 1f))
        drawPath(rrect(tx - 2.5f, 64f, 5f, 36f, 2.5f), color = Color(1f, 0.78f, 0.47f, 0.4f + 0.6f * f))
    }
    val cx = 410f; val cy = 212f
    drawPath(rrect(170f, 138f, 480f, 150f, 10f), brush = Brush.linearGradient(
        listOf(Color(0xFF2A1D12), Color(0xFF120B06)), start = Offset(0f, 138f), end = Offset(0f, 284f)))
    drawPath(rrect(170f, 138f, 480f, 150f, 10f), Color(0x88C9A45C), style = Stroke(width = 2f))
    clipPath(rrect(182f, 148f, 456f, 130f, 8f)) {
        drawRect(Color(0xFF0C0907), topLeft = Offset(170f, 138f), size = Size(480f, 150f))
        for (k in 0 until 70) drawLine(Color(0x408A7A5A), Offset(170f + k * 7f, 138f), Offset(170f + k * 7f, 288f), 1f)
        for (k in 0 until 22) drawLine(Color(0x408A7A5A), Offset(170f, 138f + k * 7f), Offset(650f, 138f + k * 7f), 1f)
        val pump = 1f + 0.04f * sin(t * 50f) * (0.3f + lvl * 0.7f) * (0.4f + heat)
        drawCircle(Color(0xFF0A0A0C), radius = 66f, center = Offset(cx, cy))
        drawCircle(Color(0xFF44444A), radius = 60f, center = Offset(cx, cy), style = Stroke(width = 5f))
        drawCircle(brush = Brush.radialGradient(
            listOf(Color(0xFF3A3A40), Color(0xFF1C1C20), Color(0xFF060608)),
            center = Offset(cx, cy), radius = 50f * pump), radius = 50f * pump, center = Offset(cx, cy))
        drawCircle(Color(0xFF16161A), radius = 13f * pump, center = Offset(cx, cy))
        for (k in 0 until 3) {
            val u = (t * 0.8f + k / 3f) % 1f
            drawCircle(Color(0xFFFF9A3C), radius = 18f + u * 34f, center = Offset(cx, cy),
                style = Stroke(width = 1.5f), alpha = (1f - u) * 0.25f * (0.3f + heat))
        }
    }
    val mx = 340f + mpos * 140f; val my = 150f + mdist * 90f
    drawPath(Path().apply { moveTo(mx, my); quadraticBezierTo(mx + 60f, my - 30f, 720f, 170f) },
        Color(0x40FFFFFF), style = Stroke(width = 1.2f))
    mic(mx, my, 10f)
    // The reference draws `MIC x · y` here (a readout of the two mic params). It is
    // dropped on request: the amp screen shows the Channel/Pedals groups instead,
    // and the readout cluttered the cabinet. The params stay in the registry.
}

fun DrawScope.sceneBass(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val dr = v[0]; val cp = v[1]; val cab = (v[8] * 2f).roundToInt(); val lvl = v[9]
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF1C1A14), Color(0xFF07060A)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    val sig = (0.45f + 0.25f * sin(t * 3.1f) * sin(t * 0.9f + 1f) + 0.15f * sin(t * 7.3f)) * (0.6f + dr * 0.6f) * (0.5f + lvl * 0.7f)
    drawPath(rrect(14f, 4f, 776f, 120f, 12f), brush = Brush.linearGradient(
        listOf(Color(0xFF2C2820), Color(0xFF0E0C09)), start = Offset(0f, 4f), end = Offset(0f, 124f)))
    drawPath(rrect(14f, 4f, 776f, 120f, 12f), Color(0x88F0C060), style = Stroke(width = 1.5f))
    val vx = 122f; val vy = 76f
    drawPath(rrect(34f, 14f, 176f, 100f, 10f), brush = Brush.linearGradient(
        listOf(Color(0xFFEFE3C0), Color(0xFFC9B88A)), start = Offset(0f, 14f), end = Offset(0f, 114f)))
    drawPath(rrect(34f, 14f, 176f, 100f, 10f), Color(0xFF3A2A10), style = Stroke(width = 2f))
    for (k in 0..10) {
        val an = Math.PI.toFloat() * (1.2f + 0.6f * k / 10f)
        val r1 = 62f; val r2 = if (k % 5 != 0) 70f else 76f
        drawLine(if (k > 7) Color(0xFFC0392B) else Color(0xFF2A1A08),
            Offset(vx + r1 * cos(an), vy + 34f + r1 * sin(an)),
            Offset(vx + r2 * cos(an), vy + 34f + r2 * sin(an)), if (k % 5 != 0) 1f else 2f)
    }
    val na = Math.PI.toFloat() * (1.2f + 0.6f * min(1f, max(0f, sig)))
    drawLine(Color(0xFF1A0E04), Offset(vx, vy + 34f), Offset(vx + 66f * cos(na), vy + 34f + 66f * sin(na)), 2.2f)
    for (k in 0 until 8) {
        val on = k < (cp * sig * 9f).roundToInt()
        val col = if (k > 5) Color(0xFFFF5040) else if (k > 3) Color(0xFFFFB040) else Color(0xFF60E070)
        drawCircle(if (on) col else Color(0xFF2A2A2A), radius = 4.5f, center = Offset(244f, 24f + k * 13f))
        if (on) drawCircle(col, radius = 9f, center = Offset(244f, 24f + k * 13f), alpha = 0.4f)
    }
    for (k in 0 until 5) {
        val sx = 575f + k * 42f; val value = v[3 + k]; val ty = 104f - value * 80f
        drawPath(rrect(sx - 3f, 22f, 6f, 84f, 3f), color = Color(0xAA000000))
        drawRect(brush = Brush.linearGradient(listOf(Color(0xFFFFD070), Color(0xFF7A5A20)),
            start = Offset(0f, 22f), end = Offset(0f, 106f)), topLeft = Offset(sx - 1.5f, ty), size = Size(3f, 106f - ty))
        drawPath(rrect(sx - 9f, ty - 6f, 18f, 12f, 3f), brush = Brush.linearGradient(
            listOf(Color(0xFF6A6258), Color(0xFFF0E8DA), Color(0xFF5A5248)), start = Offset(sx - 9f, 0f), end = Offset(sx + 9f, 0f)))
        val lt = tm.measure(listOf("63", "250", "1k", "3k", "8k")[k],
            style = TextStyle(color = Color(0xFFC9B88F), fontSize = 8.sp, fontFamily = FxFonts.mono), density = Density(1f, 1f))
        drawText(lt, topLeft = Offset(sx - lt.size.width / 2f, 119f - lt.size.height / 2f))
    }
    val cols: List<Triple<Float, Float, Float>> = when (cab) {
        0 -> listOf(Triple(410f, 212f, 62f))
        1 -> listOf(Triple(366f, 176f, 30f), Triple(454f, 176f, 30f), Triple(366f, 246f, 30f), Triple(454f, 246f, 30f))
        else -> (0..3).map { Triple(338f + it * 48f, 176f, 19f) } + (0..3).map { Triple(338f + it * 48f, 246f, 19f) }
    }
    val bw = if (cab == 2) 228f else if (cab == 1) 188f else 172f
    val bx = if (cab == 2) 296f else if (cab == 1) 316f else 324f
    drawPath(rrect(bx, 136f, bw, 148f, 10f), brush = Brush.linearGradient(
        listOf(Color(0xFF1E1A14), Color(0xFF0A0806)), start = Offset(0f, 136f), end = Offset(0f, 286f)))
    drawPath(rrect(bx, 136f, bw, 148f, 10f), Color(0x66F0C060), style = Stroke(width = 2f))
    cols.forEachIndexed { k, (ccx, ccy, r) ->
        val pump = 1f + 0.07f * sin(t * 14f + k * 0.3f) * (0.3f + sig * 0.9f)
        drawCircle(brush = Brush.radialGradient(
            listOf(Color(0xFF3A3A3E), Color(0xFF1A1A1E), Color(0xFF050506)),
            center = Offset(ccx, ccy), radius = r * pump), radius = r * pump, center = Offset(ccx, ccy))
        drawCircle(Color(0xFF666666), radius = r + 2f, center = Offset(ccx, ccy), style = Stroke(width = 1.6f))
        drawCircle(Color(0xFF111111), radius = r * 0.24f * pump, center = Offset(ccx, ccy))
    }
    val vt = tm.measure("VU", style = TextStyle(color = Color(0xFF3A2A10), fontSize = 8.sp,
        fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
    drawText(vt, topLeft = Offset(vx - vt.size.width / 2f, 32f - vt.size.height / 2f))
}

fun DrawScope.sceneBrass(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val lips = v[0]; val bite = v[1]; val vib = v[2]; val sec = (v[3] * 4f).roundToInt() + 1
    val mute = (v[4] * 4f).roundToInt(); val tone = v[5]; val sp = v[7]
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF2E0C18), Color(0xFF08040A)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFFFFD278).copy(alpha = 0.12f + 0.25f * bite), Color.Transparent),
        center = Offset(700f, 80f), radius = 400f), radius = 400f, center = Offset(700f, 80f))
    val n = BXP.size
    fun tube(dx: Float, dy: Float, sc: Float, al: Float) {
        translate(dx, dy) {
            scale(sc, sc, pivot = Offset.Zero) {
                for (pass in 0 until 3) for (i in 1 until n) {
                    val u = i / n.toFloat()
                    val w = (8f + (if (u > 0.8f) ((u - 0.8f) / 0.2f).pow(2) * 44f else 0f)) *
                            (if (pass == 0) 1.4f else if (pass == 1) 1f else 0.25f)
                    val aa = BXP[i - 1]; val bb = BXP[i]
                    val off = if (pass == 2) -2f else 0f
                    val col = when (pass) {
                        0 -> Color(0xFF2A1C08)
                        1 -> hsl(42f + tone * 8f, 0.7f, 0.3f + tone * 0.22f)
                        else -> Color(0xFFFFF4D2).copy(alpha = 0.35f + 0.35f * tone)
                    }
                    drawLine(col, Offset(aa[0] + off, aa[1] + off), Offset(bb[0] + off, bb[1] + off), w,
                        cap = StrokeCap.Round, alpha = al)
                }
            }
        }
    }
    for (s2 in sec - 1 downTo 1) tube(-20f * s2, -14f * s2 + 20f, 1f - 0.06f * s2, 0.18f)
    tube(0f, 20f, 1f, 1f)
    val dt = max(0f, min(0.1f, t - st.bt)); st.bt = t
    if (st.bsP.isEmpty()) repeat(70) { st.bsP.add(floatArrayOf(Random.nextFloat(), 0.5f + Random.nextFloat() * 0.9f)) }
    for (p in st.bsP) {
        p[0] += dt * (0.12f + 0.6f * lips) * p[1]
        if (p[0] > 1f) p[0] -= 1f
        val i = min(n - 1, floor(p[0] * n).toInt())
        val q = BXP[i]
        drawCircle(Color(0xFFFFF3D0), radius = 1f + bite * 2f,
            center = Offset(q[0], q[1] + 20f + sin(p[0] * 30f - t * (4f + vib * 14f)) * (2f + vib * 8f)),
            alpha = sin(p[0] * Math.PI.toFloat()) * 0.6f)
    }
    listOf(230f, 266f, 302f).forEachIndexed { k, vx ->
        val pr = if (max(0f, sin(t * (2f + k * 0.7f) + k * 2f)) > 0.85f) 8f else 0f
        drawPath(rrect(vx - 8f, 74f + pr, 16f, 54f, 5f), color = Color(0xFF9A7A2A))
        drawPath(rrect(vx - 6f, 76f + pr, 12f, 48f, 4f), color = Color(0xFFE8C060))
        drawCircle(Color(0xFFF4EFE6), radius = 12f, center = Offset(vx, 62f + pr))
        drawArc(Color(0xFFE8C060), -135f, 270f * v[k], false,
            topLeft = Offset(vx - 15f, 62f + pr - 15f), size = Size(30f, 30f), style = Stroke(width = 2.2f))
        tag(tm, listOf("LIPS", "BITE", "VIB")[k], vx, 42f)
    }
    if (mute != st.ampMute) { st.ampMute = mute; st.ampMuteT = t }
    val slide = max(0f, 1f - (t - st.ampMuteT) * 3f)
    translate(706f + slide * 70f, 100f) {
        when (mute) {
            1 -> {
                val p = Path().apply { moveTo(-20f, -18f); lineTo(26f, -6f); lineTo(26f, 6f); lineTo(-20f, 18f); close() }
                drawPath(p, Color(0xFF8A8A92)); drawPath(p, Color(0xFFD0D0D8), style = Stroke(width = 2f))
            }
            2 -> {
                val p = Path().apply {
                    moveTo(-14f, -30f); quadraticBezierTo(26f, -32f, 28f, 0f)
                    quadraticBezierTo(26f, 32f, -14f, 30f); close()
                }
                drawPath(p, Color(0xFF8A8A92)); drawPath(p, Color(0xFFD0D0D8), style = Stroke(width = 2f))
            }
            3 -> {
                drawOval(Color(0xFF8A8A92), topLeft = Offset(4f - 22f, -30f), size = Size(44f, 60f))
                drawOval(Color(0xFFD0D0D8), topLeft = Offset(4f - 22f, -30f), size = Size(44f, 60f), style = Stroke(width = 2f))
                drawCircle(Color(0xFF2A2A30), radius = 8f, center = Offset(8f, 0f))
            }
            4 -> {
                drawCircle(Color(0xFF4A2A22), radius = 28f, center = Offset(10f, 0f))
                drawCircle(Color(0xFFD0D0D8), radius = 28f, center = Offset(10f, 0f), style = Stroke(width = 2f))
                drawCircle(Color(0xFF2A1A14), radius = 8f, center = Offset(10f, 0f))
            }
        }
    }
    if (sp > 0.05f) {
        val u = (t * 0.35f) % 1f
        drawCircle(Color(0xFFE8C060), radius = 40f + u * 160f, center = Offset(700f, 100f),
            style = Stroke(width = 2f), alpha = (1f - u) * sp * 0.5f)
    }
}

fun DrawScope.sceneVoices(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    val vx = v[0]; val vy = v[1]; val br = v[2]; val ch = v[3]; val vib = v[4]; val tone = v[5]; val sp = v[7]
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF14102A), Color(0xFF05040C)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    val nc = 3 + (ch * 16f).roundToInt()
    for (k in 0 until nc) {
        val px = x0 + rw * (k + 0.5f) / nc
        val h = (60f + 90f * (0.5f + 0.5f * sin(t * (0.8f + br * 2f) + k * 1.7f)) * (0.4f + 0.6f * (1f - abs(vy - 0.5f))) +
                 vib * 14f * sin(t * (4f + vib * 8f) + k)) * 2.2f
        val hue = 250f + vx * 60f
        drawRect(brush = Brush.linearGradient(
            listOf(hsl(hue, 0.7f, 0.7f, 0f), hsl(hue, 0.7f, 0.75f, 0.1f + 0.12f * br + 0.08f * tone), Color.Transparent),
            start = Offset(0f, y0 + rh), end = Offset(0f, y0 + rh - h)),
            topLeft = Offset(px - rw / nc * 0.35f, y0 + rh - h), size = Size(rw / nc * 0.7f, h))
    }
    if (sp > 0.05f) {
        val u = (t * 0.3f) % 1f
        drawOval(Color(0xFFC9B8FF), topLeft = Offset(400f - (80f + u * 400f), 110f - (50f + u * 120f)),
            size = Size((80f + u * 400f) * 2, (50f + u * 120f) * 2), style = Stroke(width = 2f),
            alpha = (1f - u) * sp * 0.4f)
    }
    val px0 = 150f; val py0 = 10f; val pw = 400f; val ph = 190f
    drawPath(rrect(px0, py0, pw, ph, 16f), brush = Brush.linearGradient(
        listOf(Color(0x18FFFFFF), Color(0x08FFFFFF)), start = Offset(0f, py0), end = Offset(0f, py0 + ph)))
    drawPath(rrect(px0, py0, pw, ph, 16f), Color(0x66C9B8FF), style = Stroke(width = 1.4f))
    for (k in 1 until 4) {
        drawLine(Color(0x14FFFFFF), Offset(px0 + pw * k / 4f, py0), Offset(px0 + pw * k / 4f, py0 + ph), 1f)
        drawLine(Color(0x14FFFFFF), Offset(px0, py0 + ph * k / 4f), Offset(px0 + pw, py0 + ph * k / 4f), 1f)
    }
    for ((l, u, w) in listOf(Triple("I", 0.1f, 0.9f), Triple("U", 0.9f, 0.9f), Triple("E", 0.25f, 0.55f),
        Triple("O", 0.75f, 0.55f), Triple("A", 0.5f, 0.1f))) {
        val lt = tm.measure(l, style = TextStyle(color = Color(0xFFC9B8FF), fontSize = 22.sp,
            fontFamily = FxFonts.spaceGrotesk, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
        drawText(lt, topLeft = Offset(px0 + pw * u - lt.size.width / 2f, py0 + ph * (1f - w) + 8f - lt.size.height / 2f),
            alpha = 0.7f)
    }
    val ux = px0 + pw * vx; val uy = py0 + ph * (1f - vy)
    drawCircle(brush = Brush.radialGradient(listOf(Color(0xAAC9B8FF), Color.Transparent),
        center = Offset(ux, uy), radius = 60f), radius = 60f, center = Offset(ux, uy))
    drawCircle(Color.White, radius = 10f + sin(t * 5f) * vib * 3f, center = Offset(ux, uy))
    drawCircle(Color(0xFFC9B8FF), radius = 16f, center = Offset(ux, uy), style = Stroke(width = 2f))
    val f1 = 0.1f + 0.2f * (1f - vy); val f2 = 0.3f + 0.4f * vx; val f3 = 0.82f
    val fp = Path()
    for (i in 0..80) {
        val u = i / 80f
        var h = 0f
        for ((c, w, am) in listOf(Triple(f1, 0.05f, 1f), Triple(f2, 0.07f, 0.85f), Triple(f3, 0.06f, 0.5f))) {
            h += am * exp(-(((u - c) / w).toDouble().pow(2.0))).toFloat()
        }
        val xx = 590f + u * 190f; val yy = 190f - h * 70f
        if (i == 0) fp.moveTo(xx, yy) else fp.lineTo(xx, yy)
    }
    drawPath(fp, Color(0xFFC9B8FF), style = Stroke(width = 2f))
    val ft = tm.measure("FORMANTS  F1 F2 F3", style = TextStyle(color = Color(0xFFC9B8FF), fontSize = 8.sp, fontFamily = FxFonts.mono), density = Density(1f, 1f))
    drawText(ft, topLeft = Offset(590f, 206f))
}

fun DrawScope.sceneSynth(v: FloatArray, a: Color, t: Float, tm: TextMeasurer, st: FxSceneState) {
    val x0 = -FxStyle.OXL; val y0 = -FxStyle.OYL; val rw = FxStyle.PWL; val rh = FxStyle.PHL
    drawRect(brush = Brush.linearGradient(listOf(Color(0xFF0C1424), Color(0xFF04060C)),
        start = Offset(0f, y0), end = Offset(0f, y0 + rh)), topLeft = Offset(x0, y0), size = Size(rw, rh))
    for (k in 0 until 40) drawLine(Color(0x127AD0FF), Offset(x0 + k * 22f, y0), Offset(x0 + k * 22f, y0 + rh), 1f)
    for (k in 0 until 16) drawLine(Color(0x127AD0FF), Offset(x0, y0 + k * 22f), Offset(x0 + rw, y0 + k * 22f), 1f)
    fun wave(u: Float, w: Float): Float {
        val ph = ((u % 1f) + 1f) % 1f
        val sn = sin(6.283f * ph)
        val tr = 4f * abs(ph - 0.5f) - 1f
        val sw = 2f * ph - 1f
        val sq = if (ph < 0.5f) 1f else -1f
        return when {
            w < 0.33f -> sn + (tr - sn) * (w / 0.33f)
            w < 0.66f -> tr + (sw - tr) * ((w - 0.33f) / 0.33f)
            else -> sw + (sq - sw) * ((w - 0.66f) / 0.34f)
        }
    }
    fun scope(sx0: Float, sy0: Float, sw: Float, sh: Float, fn: (Float) -> Float, col: Color) {
        drawPath(rrect(sx0, sy0, sw, sh, 8f), color = Color(0x66000000))
        drawPath(rrect(sx0, sy0, sw, sh, 8f), Color(0x18FFFFFF), style = Stroke(width = 1f))
        val p = Path()
        for (i in 0..80) {
            val u = i / 80f
            val yy = sy0 + sh / 2f - fn(u) * sh * 0.38f
            if (i == 0) p.moveTo(sx0, yy) else p.lineTo(sx0 + u * sw, yy)
        }
        drawPath(p, col, style = Stroke(width = 1.8f))
    }
    val fc = 20f * 1000f.pow(v[7])
    val q = 0.6f + v[8] * 10f
    val ft = (v[11] * 3f).roundToInt()
    fun resp(f: Float): Float {
        val r = f / fc
        val d = sqrt((1f - r * r).pow(2) + (r / q).pow(2)) + 1e-6f
        return when (ft) {
            0 -> 1f / d
            1 -> r * r / d
            2 -> (r / q) / d
            else -> abs(1f - r * r) / d
        }
    }
    when (st.presetIndex.coerceIn(0, 2)) {
        0 -> {
            scope(14f, 8f, 110f, 58f, { u -> wave(u * 2f + t * 0.4f, v[0]) * 0.6f + wave(u * 2f + t * 0.4f + v[2] * 0.3f, v[0]) * 0.4f }, Color(0xFF7AD0FF))
            scope(134f, 8f, 110f, 58f, { u -> wave(u * 2f - t * 0.3f, v[1]) }, Color(0xFFFF9AD0))
            drawPath(rrect(270f, 8f, 290f, 104f, 10f), color = Color(0x66000000))
            drawPath(rrect(270f, 8f, 290f, 104f, 10f), Color(0x18FFFFFF), style = Stroke(width = 1f))
            val p = Path()
            for (i in 0..120) {
                val u = i / 120f
                val f = 20f * 1000f.pow(u)
                val hh = max(-30f, min(20f, 20f * log10(resp(f) + 1e-6f)))
                val yy = 94f - (hh + 30f) / 50f * 80f
                if (i == 0) p.moveTo(270f, yy) else p.lineTo(270f + u * 290f, yy)
            }
            drawPath(p, Color(0xFF7AD0FF), style = Stroke(width = 2.2f))
            // The preview fills the area under the response curve with a fading
            // `#7ad0ff` wash before drawing the ADSR envelope.
            drawPath(Path().apply { addPath(p); lineTo(560f, 112f); lineTo(270f, 112f); close() },
                brush = Brush.verticalGradient(listOf(Color(0x667AD0FF), Color(0x007AD0FF)),
                    startY = 8f, endY = 112f))
            val pxx = 270f + v[7] * 290f; val pyy = 8f + (1f - v[8]) * 104f
            drawCircle(Color.White, radius = 7f + sin(t * 4f) * 1.2f, center = Offset(pxx, pyy))
            drawCircle(Color(0xFF7AD0FF), radius = 12f, center = Offset(pxx, pyy), style = Stroke(width = 2f))
            val lt = tm.measure("CUTOFF ${fc.roundToInt()} Hz  ·  RES ${(v[8] * 100).roundToInt()}%",
                style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono), density = Density(1f, 1f))
            drawText(lt, topLeft = Offset(278f, 22f - lt.size.height / 2f))
            val ax = 282f + v[12] * 70f; val dx = ax + v[13] * 70f
            val sy = 136f + (1f - v[14]) * 64f; val sx = dx + 40f; val rx = sx + v[15] * 70f
            drawPath(rrect(270f, 120f, 290f, 88f, 10f), color = Color(0x66000000))
            drawPath(rrect(270f, 120f, 290f, 88f, 10f), Color(0x18FFFFFF), style = Stroke(width = 1f))
            val ap = Path().apply {
                moveTo(282f, 200f); lineTo(ax, 136f); lineTo(dx, sy); lineTo(sx, sy); lineTo(rx, 200f)
            }
            drawPath(ap, Color(0xFFFFA0D0), style = Stroke(width = 2.2f))
            // Same for the envelope: `#ffa0d0` wash down to the panel floor.
            drawPath(Path().apply { addPath(ap); lineTo(282f, 200f); close() },
                brush = Brush.verticalGradient(listOf(Color(0x44FFA0D0), Color(0x00FFA0D0)),
                    startY = 136f, endY = 200f))
            listOf(ax to 136f, dx to sy, rx to 200f).forEach { (hx, hy) ->
                drawCircle(Color.White, radius = 6f, center = Offset(hx, hy))
                drawCircle(Color(0xFFFFA0D0), radius = 10f, center = Offset(hx, hy), style = Stroke(width = 2f))
            }
            // `fillStyle '#e8b0cc'`: A/D/S under the three envelope handles, offset
            // 22 px down except D which sits 14 px above its handle.
            listOf("A" to (ax to 136f), "D" to (dx to sy), "S" to (rx to 200f)).forEachIndexed { k, (nm, h) ->
                val lt = tm.measure(nm, style = TextStyle(color = Color(0xFFE8B0CC), fontSize = 8.sp,
                    fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
                drawText(lt, topLeft = Offset(h.first - lt.size.width / 2f,
                    h.second + (if (k == 1) -14f else 22f) - lt.size.height / 2f))
            }
            val ot = tm.measure("OSC A", style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp,
                fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
            drawText(ot, topLeft = Offset(22f, 20f - ot.size.height / 2f))
            val ob = tm.measure("OSC B", style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp,
                fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
            drawText(ob, topLeft = Offset(142f, 20f - ob.size.height / 2f))
        }
        1 -> {
            fun lfoFn(s2: Float): (Float) -> Float = { u ->
                val ph = ((u % 1f) + 1f) % 1f
                when {
                    s2 < 0.33f -> sin(6.283f * u)
                    s2 < 0.66f -> 4f * abs(ph - 0.5f) - 1f
                    s2 < 0.9f -> 2f * ph - 1f
                    else -> if (ph < 0.5f) 1f else -1f
                }
            }
            scope(14f, 8f, 220f, 62f, { u -> lfoFn(v[18])(u * 2f + t * (0.3f + v[16] * 3f)) * (0.2f + v[17] * 0.8f) }, Color(0xFF7AD0FF))
            scope(250f, 8f, 220f, 62f, { u -> sin(6.283f * (u * 2f + t * (0.3f + v[20] * 3f))) * (0.2f + v[21] * 0.8f) }, Color(0xFFFF9AD0))
            val src = listOf("LFO1" to v[17], "LFO2" to v[21], "ENV" to v[10], "MACRO 4" to v[25])
            val dst = listOf("CUTOFF", "PITCH", "DRIVE", "LEVEL")
            val map = intArrayOf((v[19] * 3f).roundToInt(), 1, 0, 3)
            src.forEachIndexed { k, (nm, am) ->
                val y1 = 34f + k * 28f; val y2 = 34f + map[k] * 28f
                drawPath(Path().apply { moveTo(548f, y1); cubicTo(640f, y1, 660f, y2, 752f, y2) },
                    brush = Brush.linearGradient(listOf(Color(0xFF7AD0FF), Color(0xFFFF9AD0)), start = Offset(530f, 0f), end = Offset(770f, 0f)),
                    style = Stroke(width = 1f + am * 5f), alpha = 0.25f + 0.6f * am)
                val lt = tm.measure(nm, style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono), density = Density(1f, 1f))
                drawText(lt, topLeft = Offset(540f - lt.size.width, y1 + 3f - lt.size.height / 2f))
            }
            dst.forEachIndexed { k, nm ->
                val y2 = 34f + k * 28f
                val lt = tm.measure(nm, style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono), density = Density(1f, 1f))
                drawText(lt, topLeft = Offset(764f, y2 + 3f - lt.size.height / 2f))
            }
            // White dots travelling along each curve, and the socket rings at both
            // ends of the patch cables.
            src.forEachIndexed { k, (_, am) ->
                val y1 = 34f + k * 28f; val y2 = 34f + map[k] * 28f
                val pu = (t * 0.5f + k * 0.25f) % 1f
                val bx = 548f + (752f - 548f) * pu
                val by = y1 + (y2 - y1) * (pu * pu * (3f - 2f * pu))
                drawCircle(Color.White, radius = 2.4f + am * 2f, center = Offset(bx, by))
                drawCircle(Color(0xFF0C1424), radius = 5f, center = Offset(548f, y1))
                drawCircle(Color(0xFF7AD0FF), radius = 5f, center = Offset(548f, y1), style = Stroke(width = 1.6f))
            }
            dst.forEachIndexed { k, _ ->
                val y2 = 34f + k * 28f
                drawCircle(Color(0xFF0C1424), radius = 5f, center = Offset(752f, y2))
                drawCircle(Color(0xFFFF9AD0), radius = 5f, center = Offset(752f, y2), style = Stroke(width = 1.6f))
            }
            val mt = tm.measure("MODULATION", style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono), density = Density(1f, 1f))
            drawText(mt, topLeft = Offset(520f, 16f - mt.size.height / 2f))
        }
        else -> {
            val act = intArrayOf(0, 2, 4, 7, 9, 4)[(floor(t * 3.2f).toInt()) % 6]
            for (k in 0 until 15) {
                val kx = 20f + k * 50.6f; val on = k == act
                drawPath(rrect(kx, 150f, 48f, 56f, 4f), brush = Brush.linearGradient(
                    listOf(if (on) Color(0xFF7AD0FF) else Color(0xFFF4F6FA), if (on) Color(0xFF4AA0D0) else Color(0xFFC8CCD6)),
                    start = Offset(0f, 150f), end = Offset(0f, 208f)))
            }
            // `fillStyle '#10141c'`: the black keys, only over the ten slots the
            // preview lists. They were absent, leaving a bare white keyboard.
            for (k in listOf(0, 1, 3, 4, 5, 7, 8, 10, 11, 12)) {
                drawPath(rrect(20f + (k + 1) * 50.6f - 14f, 150f, 28f, 34f, 3f),
                    color = if (k.toFloat() == act + 0.5f) Color(0xFF7AD0FF) else Color(0xFF10141C))
            }
            val nv = 1 + (v[28] * 15f).roundToInt()
            for (k in 0 until 16) drawCircle(if (k < nv) Color(0xFF7AD0FF) else Color(0x22FFFFFF), radius = 4.2f,
                center = Offset(560f + k * 13f, 40f))
            val lt = tm.measure("VOICES $nv  ·  ${listOf("POLY", "MONO", "LEGATO")[(v[26] * 2f).roundToInt().coerceIn(0, 2)]}",
                style = TextStyle(color = Color(0xFF8FB8D8), fontSize = 8.sp, fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold), density = Density(1f, 1f))
            drawText(lt, topLeft = Offset(560f, 62f - lt.size.height / 2f))
        }
    }
}

````

### `FxFonts.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/fx/FxFonts.kt`
- **Taille :** 2653 octets · **lignes :** 68 · **sha256 (12) :** `9a694e176c86`
- Table des polices par effet.

````kotlin
package com.soundstage.mixer.ui.fx

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.soundstage.mixer.R

/**
 * Fonts for the FX window. The families named by the design reference now ship
 * in `res/font` (they arrived in the `JetBrains_Mono,Orbitron,Share_Tech_Mono,
 * Sora,Space_Grotesk.zip` asset). The app's own interface font (Plus Jakarta
 * Sans, also used by the Sheets module) and JetBrains Mono were already present
 * and are reused rather than duplicated.
 */
object FxFonts {
    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int, w: FontWeight) =
        Font(res, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))

    /** Interface font of the whole app (Sheets uses it too). */
    val ui = FontFamily(
        variable(R.font.plus_jakarta_sans, FontWeight.Normal),
        variable(R.font.plus_jakarta_sans, FontWeight.Medium),
        variable(R.font.plus_jakarta_sans, FontWeight.Bold),
        variable(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
    )

    /** Technical labels, from the reference. */
    val mono = FontFamily(variable(R.font.share_tech_mono, FontWeight.Normal))

    /** JetBrains Mono, already in the project, kept for dense readouts. */
    val jetBrainsMono = FontFamily(
        variable(R.font.jetbrains_mono, FontWeight.Normal),
        variable(R.font.jetbrains_mono, FontWeight.Medium),
        variable(R.font.jetbrains_mono, FontWeight.Bold),
    )

    val orbitron = FontFamily(
        variable(R.font.orbitron, FontWeight.Normal),
        variable(R.font.orbitron, FontWeight.Medium),
        variable(R.font.orbitron, FontWeight.Bold),
        variable(R.font.orbitron, FontWeight.ExtraBold),
    )

    val spaceGrotesk = FontFamily(
        variable(R.font.space_grotesk, FontWeight.Normal),
        variable(R.font.space_grotesk, FontWeight.Medium),
        variable(R.font.space_grotesk, FontWeight.Bold),
    )

    val sora = FontFamily(
        variable(R.font.sora, FontWeight.Normal),
        variable(R.font.sora, FontWeight.Medium),
        variable(R.font.sora, FontWeight.Bold),
        variable(R.font.sora, FontWeight.ExtraBold),
    )

    /** Title face per effect, exactly as the doc 03 table specifies. */
    fun title(effectId: Int): FontFamily = when (effectId) {
        0, 8, 9, 12, 13 -> spaceGrotesk
        1 -> jetBrainsMono
        2, 10, 14 -> orbitron
        3 -> mono
        else -> sora
    }
}

````

### `FxSurface.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/fx/FxSurface.kt`
- **Taille :** 2860 octets · **lignes :** 69 · **sha256 (12) :** `680ad1d05780`
- Modifier.raised — ombre peinte.

````kotlin
package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The raised treatment shared by the rack's cards and the header chips.
 *
 * Three things make the lift read: a drop shadow just below the bottom edge, a 1px
 * sheen along the top edge, and a 1px darker seam along the bottom one.
 *
 * The shadow is painted here rather than with `Modifier.shadow` for two reasons: the
 * platform shadow is not rendered by the Roborazzi capture, and it is drawn over the
 * card, so a translucent fill would let it show through the card itself. Painting it
 * with `drawBehind` and starting the gradient exactly at the bottom edge keeps it
 * strictly underneath, whatever the fill's alpha. Two passes give it a penumbra: a
 * wide faint one, then a tighter darker one.
 */
fun Modifier.raised(
    corner: Dp,
    elevation: Dp = 6.dp,
    fill: Brush? = null,
): Modifier = this
    .drawBehind {
        val r = corner.toPx()
        val e = elevation.toPx()
        val w = size.width
        val h = size.height

        val soft = Path().apply {
            addRoundRect(RoundRect(0f, h * 0.70f, w, h + e * 1.5f, CornerRadius(r * 1.15f)))
        }
        drawPath(soft, Brush.verticalGradient(
            colors = listOf(Color(0x00000000), Color(0x4D000000)),
            startY = h, endY = h + e * 1.5f))

        val tight = Path().apply {
            addRoundRect(RoundRect(0f, h * 0.86f, w, h + e * 0.8f, CornerRadius(r)))
        }
        drawPath(tight, Brush.verticalGradient(
            colors = listOf(Color(0x00000000), Color(0x99000000)),
            startY = h, endY = h + e * 0.8f))
    }
    .clip(RoundedCornerShape(corner))
    .then(if (fill != null) Modifier.background(fill) else Modifier)
    .drawWithContent {
        drawContent()
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color(0x00FFFFFF), Color(0x26FFFFFF), Color(0x00FFFFFF))),
            start = Offset(0f, 0.5f), end = Offset(size.width, 0.5f), strokeWidth = 1f)
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color(0x00000000), Color(0x45000000), Color(0x00000000))),
            start = Offset(0f, size.height - 0.5f), end = Offset(size.width, size.height - 0.5f),
            strokeWidth = 1f)
    }

````

---

## 7. Coquille Kotlin — fenêtre, panneau, potard, rack, store

| Fichier | Contenu |
|---|---|
| `FxWindowHost.kt` | La coquille : en-tête, fond d'effet plein cadre, clip arrondi |
| `FxOpenPanel.kt` | Le panneau ouvert : titre, potards, groupes de puces, rail |
| `FxKnob.kt` | Le potard : arc, repère, glisser vertical, double-tap = défaut |
| `FxShellStore.kt` | L'état UI — **doit** être du snapshot state, sinon rien ne recompose |
| `FxAddOverlay.kt` | La page d'ajout (familles, recherche, grille) |
| `FxGeometry.kt` | Géométrie générée depuis le preview (`AB()`, `TG()`) |
| `FxRack.kt` | Cartes du rack, mini-aperçu animé, appui long + glisser = poubelle |
| `FxOverlay.kt` | L'hôte appelé par le mixer : restaure puis monte la coquille |
| `FxController.kt` | État par piste, pont natif, persistance |

Deux invariants à ne pas casser : `FxShellStore` garde `slots`/`instrument` en
snapshot state (`mutableStateMapOf`) sinon un effet ajouté ne recompose jamais ; et
`FxRegistry.info(id)` **doit** rester borné (`all.getOrElse(id) { all[0] }`) — un id
hors bornes levait depuis un gestionnaire d'événement Compose et démontait la fenêtre.

### `FxWindowHost.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxWindowHost.kt`
- **Taille :** 24063 octets · **lignes :** 520 · **sha256 (12) :** `44531a821e9c`

````kotlin
package com.aetherkeys.fx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.soundstage.mixer.ui.fx.FxFonts
import com.soundstage.mixer.ui.fx.fxPanelBrush
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * The window is the effect area itself: 825x343, the reference's `PWL` x `PHL`.
 * It is *not* the 851x394 of the PNG — that is the screenshot, which includes the
 * page margin (12 px left/right, 38 px top, 10 px bottom) around the window.
 * Measured: the effect background spans y_log 39.4..381.6 and x_log 12.8..837.0,
 * so 825x343 with the page outside it. Taking 851x394 as the window is what made
 * the background look undersized.
 */
const val CANVAS_W = 825f
const val CANVAS_H = 343f

/**
 * `.bd`: the scene canvas, 779x263 at the content origin (20,58) inside the
 * window. The scenes are written in content-local coordinates and paint their own
 * `.xN` background from (-OXL,-OYL), which lands exactly on the window. Painting
 * them at the window origin dropped every element by (20,58).
 */
const val SCENE_X = 20f
const val SCENE_Y = 58f

/**
 * The whole FX window, ported from `#win` in fx-window-preview.html.
 * Chrome values are the reference's: header 42, chip 28 r9, card 158 r16,
 * panel padding 20 r22, add sheet translateY(102%) -> 0 over 500 ms.
 *
 * The window hosts one fixed logical canvas ([CANVAS_W] x [CANVAS_H]) scaled once
 * from the outside. Scenes, the rail and the knobs all live in those coordinates,
 * so the scale must never be reapplied inside. Rack XOR panel: opening an effect
 * replaces the rack entirely instead of overlaying it.
 */
@Composable
fun FxWindowHost(store: FxShellStore, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    LaunchedEffect(store.notice) {
        if (store.notice != null) { delay(2400); store.notice = null }
    }
    // The scrim stays full screen, but the window itself keeps the reference's own
    // size: 825x343 logical, centred, and never blown up past 1:1. Filling the screen
    // stretched the rack and the effect window on tablets and looked nothing like the
    // preview.
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val scale = minOf(maxWidth.value / CANVAS_W, maxHeight.value / CANVAS_H, 1f)
        val winW = (CANVAS_W * scale).dp
        val winH = (CANVAS_H * scale).dp
        Box(
            modifier = Modifier
                .width(winW)
                .height(winH)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (store.openId != null) fxPanelBrush(store.openId!!)
                    else Brush.verticalGradient(listOf(Color(0xFF1F2635), Color(0xFF131925))),
                )
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.TopStart,
        ) {
            val tm = rememberTextMeasurer()
            Box(
                modifier = Modifier
                    .width(CANVAS_W.dp)
                    .height(CANVAS_H.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0f)),
            ) {
                val openId = store.openId
                if (openId == null) {
                    Column(Modifier.fillMaxSize()) {
                        Header(store, onClose)
                        Rack(store, tm)
                    }
                } else {
                    val module = store.modules[openId]
                    if (module != null) {
                        FxOpenPanel(
                            module = module,
                            params = store.valuesOf(module.id),
                            presets = module.presets,
                            activePreset = store.activePresetOf(module.id),
                            bypassed = store.isBypassed(module.id),
                            abLabel = store.abLabel(module.id),
                            onParam = { i, v -> store.setParam(module.id, i, v) },
                            onPreset = { name -> store.applyPreset(module.id, module.presets.indexOf(name)) },
                            onBypass = { store.toggleBypass(module.id) },
                            onAb = { store.toggleAB(module.id) },
                            onSurprise = { store.surprise(module.id) },
                            onClose = { store.openId = null },
                            page = store.page,
                            onPage = { store.page = it },
                            onEdit = { store.editOpen = !store.editOpen },
                            editOpen = store.editOpen,
                            tm = tm,
                        )
                    }
                }
            }
            // `#ad` and the notice are window chrome, not canvas content: they stay
            // unscaled so hit targets do not shrink with the canvas.
            // The reference slides the sheet from translateY(102%) to 0 over 500 ms.
            // It used to appear in a single frame, which read as a hard cut; the scrim
            // fades while the sheet rises, and both are keyed on `addOpen` so closing
            // plays the reverse instead of tearing the overlay down instantly.
            androidx.compose.animation.AnimatedVisibility(
                visible = store.addOpen,
                enter = androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(500)
                ) + androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(500),
                ),
                exit = androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(220)
                ) + androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(220),
                ),
            ) {
                Box(Modifier.fillMaxSize().background(Color(0xE60A0E16)).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { store.addOpen = false }) {
                    FxAddOverlay(
                        modules = store.modules.values.sortedBy { it.id },
                        tab = store.addTab,
                        onTab = { store.addTab = it },
                        onPick = { m ->
                            val ok = if (m.kind == FxScreenKind.EFFECT) store.addEffect(m.id) else store.setInstrument(m.id)
                            if (ok) store.addOpen = false
                        },
                        isOn = { store.isOn(it) },
                        effectsUsed = store.effectCount(),
                        onClose = { store.addOpen = false },
                    )
                }
            }
            store.notice?.let { msg ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2A3042))
                        .border(1.dp, Color(0x1CFFFFFF), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text(msg, fontSize = 12.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EAF3)) }
            }
        }
    }
}

/** `.wh`: height 42, padding 0 12, gap 10. */
@Composable
private fun Header(store: FxShellStore, onClose: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
    ) {
        Text("⌄", fontSize = 14.sp, color = Color(0xFF8D96AB))
        Text(
            "EFFECTS",
            fontSize = 12.sp,
            fontFamily = FxFonts.ui,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (12 * 0.16f).sp,
            color = Color(0xFF8D96AB),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${store.effectCount()} / 6 EFFECTS",
            fontSize = 10.sp,
            fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold,
            letterSpacing = (10 * 0.12f).sp,
            color = Color(0xFF8D96AB),
        )
        // Not yet wired to the mixer's soundfont window: say so instead of a silent no-op.
        Chip("Soundfont · Grand Piano ▾") { store.notice = "Soundfont: open it from the mixer track" }
        Chip("+ Instrument") { store.addTab = FxAddTab.INSTRUMENT; store.addOpen = true }
        Chip("+ Effect") { store.addTab = FxAddTab.EFFECTS; store.addOpen = true }
        if (onClose != null) CloseChip(onClose)
    }
}

/**
 * Window close button. The FX window is a modal overlay with no other way out, so
 * this is the one control the user needs to leave it; it sits last in the header,
 * on the same rhythm as the other chips.
 */
@Composable
private fun CloseChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .shadow(3.dp, RoundedCornerShape(9.dp), clip = false)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0FFFFFFF))))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Two strokes so the cross is crisp at this size without an icon dependency.
        androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.6f * density
            drawLine(Color(0xFFE6EAF3), Offset(0f, 0f), Offset(w, h), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(Color(0xFFE6EAF3), Offset(w, 0f), Offset(0f, h), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** `.bt`: height 28, radius 9, gradient #ffffff14 -> #ffffff08, border #ffffff14, shadow 0 2 4 #0006. */
@Composable
internal fun Chip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            // Raised look: a soft drop shadow plus a 1 dp top highlight. Without the
            // highlight the buttons read as flat text and did not look pressable.
            .shadow(4.dp, RoundedCornerShape(9.dp), clip = false)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            color = Color(0xFFE6EAF3), maxLines = 1)
    }
}

/** `#rack`: gap 12, padding 4 14 14. */
@Composable
private fun Rack(store: FxShellStore, tm: androidx.compose.ui.text.TextMeasurer) {
    val slots = store.slotsOf(store.strip)
    val ids = buildList {
        store.instrumentOf(store.strip)?.let { add(it) }
        slots.forEach { if (it != null) add(it) }
    }
    // The rack always ends with an empty `+` slot: it is the only way to reach the add
    // sheet, and the reference shows it as a card of the same size. It used to be
    // missing entirely, so adding an effect had no visible entry point.
    val hasRoom = slots.any { it == null }

    // Drag-to-delete: press and hold a card, lift it, and drop it on the bin. The
    // reference plays this as a real lift (the card rises and casts a bigger shadow)
    // and offers an Undo bandeau for a few seconds. Long-press alone used to delete
    // the card with no animation and no way back, which read as a glitch.
    var lifted by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var undo by remember { mutableStateOf<Int?>(null) }
    val overBin = lifted != null && dragY < -BIN_DROP
    LaunchedEffect(undo) {
        if (undo != null) { delay(5_200); undo = null }
    }

    Box(Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
        ) {
            items(ids) { id ->
                val m = store.modules[id] ?: return@items
                RackCard(
                    m = m,
                    params = store.valuesOf(id),
                    tm = tm,
                    lifted = lifted == id,
                    dragY = if (lifted == id) dragY else 0f,
                    onOpen = { store.openId = id },
                    onDragStart = { lifted = id; dragY = 0f },
                    onDrag = { dy -> dragY = dy },
                    onDragEnd = {
                        val drop = dragY < -BIN_DROP
                        lifted = null
                        dragY = 0f
                        if (drop) { store.removeEffect(id); undo = id }
                    },
                )
            }
            if (hasRoom) {
                item { AddSlotCard { store.addTab = FxAddTab.EFFECTS; store.addOpen = true } }
            }
        }

        // The bin appears only while a card is lifted, so the rack keeps its clean
        // look the rest of the time.
        if (lifted != null) {
            val armed = overBin
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (armed) Color(0xF27A2216) else Color(0xCC1A2030))
                    .border(1.5.dp, if (armed) Color(0xFFFF7A5A) else Color(0x24FFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    if (armed) "Release to delete" else "Drag up to delete",
                    fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFE9E0),
                )
            }
        }

        undo?.let { id ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE62A3042))
                    .border(1.dp, Color(0x1CFFFFFF), RoundedCornerShape(14.dp))
                    .padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${store.modules[id]?.name ?: "Effect"} removed",
                        fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE6EAF3),
                    )
                    Text(
                        "UNDO",
                        fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (11 * 0.12f).sp, color = Color(0xFF8FB2E8),
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { store.addEffect(id); undo = null }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/** Vertical travel (logical px) that arms the bin. */
private const val BIN_DROP = 110f

/**
 * The trailing empty slot: same footprint as a card, but carved *into* the window
 * instead of raised on top of it. A raised card here read as a filled effect; the
 * inset gradient (dark at the top, catching light at the bottom) plus the inner
 * shadow rim is what makes it read as an empty socket waiting for an effect.
 */
@Composable
private fun AddSlotCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(158.dp)
            .fillMaxHeight()
            .clip(shape)
            // Carved: inverted gradient (top dark -> bottom lighter) and no drop shadow.
            .background(Brush.verticalGradient(listOf(Color(0xFF141924), Color(0xFF232A38))))
            .border(1.dp, Color(0x14000000), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                // Concave disc: darker at the top so it looks pressed in.
                .background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x0AFFFFFF))))
                .border(1.dp, Color(0x22000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The `+` is drawn, not typed: a text glyph sits off-centre inside its
            // line box, which is why the plus looked low in the circle.
            androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
                val c = size.width / 2f
                val arm = size.width * 0.36f
                val stroke = 2.4f * density
                drawLine(Color(0xFFC7CFDD), Offset(c - arm, c), Offset(c + arm, c), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color(0xFFC7CFDD), Offset(c, c - arm), Offset(c, c + arm), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("ADD EFFECT", fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            letterSpacing = (10 * 0.12f).sp, color = Color(0xFF8D96AB))
    }
}

/**
 * `.cd`: width 158, radius 16, padding 10, gap 6, gradient #323845 -> #222733.
 * Canvas 136x160 (`.cd canvas`), scene drawn at mini scale (m = 1).
 */
@Composable
internal fun RackCard(
    m: EffectModule,
    params: FloatArray,
    tm: androidx.compose.ui.text.TextMeasurer,
    lifted: Boolean = false,
    dragY: Float = 0f,
    onOpen: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val clock = rememberFxClock(fullRate = false)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(158.dp)
            .fillMaxHeight()
            // While lifted the card rises with the finger, sits above its neighbours
            // and casts a deeper shadow, which is what makes the drag read as a lift
            // rather than a jump.
            .zIndex(if (lifted) 20f else 0f)
            .offset(y = dragY.dp)
            .graphicsLayer { scaleX = if (lifted) 1.04f else 1f; scaleY = if (lifted) 1.04f else 1f }
            .shadow(if (lifted) 18.dp else 8.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF323845), Color(0xFF222733))))
            .border(1.dp, Color(0x12FFFFFF), shape)
            .clickable(onClick = onOpen)
            .pointerInput(m.id) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, amount ->
                    change.consume()
                    onDrag(dragY + amount)
                }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(m.type.uppercase(), fontSize = 8.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, letterSpacing = (8 * 0.16f).sp, color = Color(0xFF8D96AB), maxLines = 1)
        Text(m.name, fontSize = 20.sp, fontFamily = FxFonts.title(m.id), color = Color(0xFFDFE4EE), maxLines = 1)
        Box(
            modifier = Modifier
                .width(136.dp)
                .height(160.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12161F)),
        ) {
            // `wrapMini`: fill 136x160, translate(3, 46), scale(.165) — the whole
            // 825x343 scene at 16.5 %, not a different drawing. The scene's logical
            // units are dp, the canvas is in px, so density is folded into the scale.
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val d = this.density
                withTransform({
                    translate(3f * d, 46f * d)
                    scale(0.165f * d, 0.165f * d, pivot = Offset.Zero)
                }) {
                    with(m) { drawScene(params, clock.value, mini = true, tm = tm) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(8.dp).height(8.dp).clip(CircleShape).background(Color(0xFF2A3040)))
            Text(m.presets.firstOrNull() ?: "", fontSize = 9.sp, fontFamily = FxFonts.ui,
                fontWeight = FontWeight.SemiBold, color = Color(0xFF8D96AB), maxLines = 1)
        }
    }
}

````

### `FxOpenPanel.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxOpenPanel.kt`
- **Taille :** 27037 octets · **lignes :** 570 · **sha256 (12) :** `c7ed1dbde2eb`

````kotlin
package com.aetherkeys.fx.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.soundstage.mixer.ui.fx.FxFonts
import com.soundstage.mixer.ui.fx.fxPanelText
import com.soundstage.mixer.ui.fx.FxStyle
import com.soundstage.mixer.ui.fx.raised
import com.soundstage.mixer.ui.fx.drawFxRail

/**
 * The open effect window (`#ex` content), on the same 825x343 logical canvas as
 * the rest of the window. Geometry is `FxGeom` / `FxKnobBoxes`, measured in the
 * reference, and used verbatim: 1 logical px = 1 dp here, the caller owns the
 * single scale. Knob coordinates are window coordinates (the measured boxes
 * already include the content origin, verified on ref-10-amp.png: Gain sits at
 * x = 91.2 while its box starts at x = 68).
 */
@Composable
fun FxOpenPanel(
    module: EffectModule,
    params: FloatArray,
    presets: List<String>,
    activePreset: String?,
    bypassed: Boolean,
    abLabel: String,
    onParam: (index: Int, value: Float) -> Unit,
    onPreset: (String) -> Unit,
    onBypass: () -> Unit,
    onAb: () -> Unit,
    onSurprise: () -> Unit,
    onClose: () -> Unit,
    page: Int,
    onPage: (Int) -> Unit,
    onEdit: () -> Unit,
    editOpen: Boolean,
    tm: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val clock = rememberFxClock(fullRate = true)
    Box(modifier = modifier.size(CANVAS_W.dp, CANVAS_H.dp)) {
        // `.bd`: the scene canvas, 779x263 at the content origin (20,58). The
        // scenes are written in content-local coordinates and paint their `.xN`
        // background from (-OXL,-OYL), which lands exactly on the window, so the
        // effect colour fills the frame and no clip is needed here: the host's
        // rounded clip already bounds the window.
        Box(
            Modifier
                .offset(x = SCENE_X.dp, y = SCENE_Y.dp)
                .size(779.dp, 263.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val d = this.density
                // Only the logical-unit -> pixel scale. The scenes already carry the
                // (OXL, OYL) offset in their coordinates (`y0 = -OYL`), so they are
                // drawn at the window origin and the background lands at (0,0).
                // Adding translate(20,58) on top shifted everything down by 58 and
                // inset the background, which is what truncated the bottom.
                withTransform({ scale(d, d, pivot = Offset.Zero) }) {
                    with(module) { drawScene(params, clock.value, mini = false, tm = tm, page = page) }
                    // Only the instruments that actually have one get the bottom rail.
                    // The amp (10) and the bass (11) have no `RS` entry: their "Level"
                    // is a regular knob and the panel runs to the bottom.
                    if (FxStyle.hasRail(module.id)) {
                        drawFxRail(module.id, params.getOrElse(FxStyle.railLevelIndex(module.id)) { 0f }, tm)
                    }
                }
            }
        }

        // `#cl`: close chip pinned at 12,12 (top-left), the `.ph` padding clears it.
        Box(Modifier.offset(x = 12.dp, y = 12.dp)) {
            SmallChip("⌄", onClose)
        }

        // `#pm`: preset menu pinned top-right (176 wide, top 11). `zIndex` lifts the
        // open list above the knobs, which are declared later in this Box and would
        // otherwise paint over it (the reference has `#pm` on its own layer).
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 11.dp).zIndex(1f)) {
            PresetChip(activePreset ?: presets.firstOrNull() ?: "", presets, onPreset)
        }

        // `.ph`: height 58, padding 0 16 0 58.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(start = 58.dp, end = 200.dp),
        ) {
            BypassChip(bypassed, onBypass, fxPanelText(module.id))
            Spacer(Modifier.width(12.dp))
            Column {
                val style = NAME_STYLES[module.id] ?: NAME_STYLES.getValue(0)
                // `.ph .nm`/`.ty` take their colour from the panel's `.xN` rule: solar
                // (`.x4`) is dark ink on a cream face, and the hard-coded light grey
                // made the whole title invisible.
                val ink = fxPanelText(module.id)
                // `lineHeight = fontSize`: a `Text` in this 58 px header would
                // otherwise inherit the Material3 line height (24 sp) and sit ~2 px
                // low. Measured against the reference every title was +2 px down.
                // `.ph .nm{font-size:30px;line-height:1}` declares no weight and no
                // tracking, so the title keeps the per-effect face from the doc 03
                // table but takes weight 400 and zero tracking. `lineHeight =
                // fontSize` stops the Material3 24 sp line height from pushing it low.
                Text(module.name, fontSize = style.first.sp, fontFamily = FxFonts.title(module.id),
                    fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
                    lineHeight = style.first.sp, color = ink, maxLines = 1)
                Text(module.type.uppercase(), fontSize = SUB_TITLE_SIZE[module.id]?.sp ?: 9.sp, fontFamily = FxFonts.ui,
                    fontWeight = FontWeight.Bold, letterSpacing = (9 * 0.18f).sp,
                    color = ink.copy(alpha = 0.6f), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (module.id >= 5) {
                SmallChip("Surprise me", onSurprise)
                Spacer(Modifier.width(8.dp))
                SmallChip(abLabel, onAb)
                // `[5,7,8,9,12].includes(i)`: the soundfont-backed instruments
                // carry the Edit chip; the preview only toggles `.on` on `#ed`,
                // which has no markup of its own, so the panel below is a stand-in.
                if (module.id in EDIT_EFFECTS) {
                    Spacer(Modifier.width(8.dp))
                    SmallChip("Edit", onEdit, active = editOpen)
                }
            }
        }

        if (editOpen) EditPanel(module.id)

        // `.chg` groups (`TG(i,label,x,y,items)` in the preview): the preset/section
        // chips that sit above the knobs — CHANNEL + PEDALS for the amp, ADSR, BODY,
        // CABINET, MUTE, PERCUSSION, ROTARY for the others. Positions are window
        // coordinates, like the knob boxes.
        val chipGroups = FxChipGroups.byEffect[module.id].orEmpty()
        val pageChips = FxChipGroups.pages[module.id]
        (pageChips?.firstOrNull { it.page == page }?.groups ?: chipGroups).forEach { group ->
            ChipGroupRow(group, params) { onParam(it.first, it.second) }
        }

        // `.pgt` (`SPG` tabs): only the synth has pages. The reference shows SOUND.
        if (pageChips != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.offset(x = 288.dp, y = 14.dp),
            ) {
                listOf("SOUND", "MOTION", "VOICE").forEachIndexed { k, name ->
                    PageTab(name, active = k == page) { onPage(k) }
                }
            }
        }

        // Knobs, at their boxes. `FxKnobBoxes` is already in window coordinates
        // (the preview's `AB()` centre + (OXL,OYL)), so nothing is added here.
        // `.kn.ab` is translate(-50%,-50%) on the box centre.
        FxKnobBoxes.byEffect[module.id].orEmpty().forEachIndexed { i, box ->
            val spec = module.knobs.getOrNull(i) ?: return@forEachIndexed
            if (box.w <= 0f) return@forEachIndexed
            // `SPG`: knobs outside the active synth page are `display:none`.
            if (box.page >= 0 && page != -1 && box.page != page) return@forEachIndexed
            val v = params.getOrElse(spec.paramIndex) { 0f }
            val cx = box.x + box.w / 2f
            val cy = box.y + box.h / 2f
            // `AB(i,j,x,y,z)` passes the diameter z straight through; the old
            // 0.86 factor shrank every knob by 13 % (amp 40 vs 46, echo 47 vs 56).
            val diameter = box.w.dp
            FxKnob(
                label = box.label,
                value = v,
                accent = module.accent,
                diameter = diameter,
                gradientIndex = SKN[module.id] ?: box.sk,
                valueText = formatValue(module, spec.paramIndex, v),
                style = KnobStyle.forEffect(module.id),
                effectId = module.id,
                light = (SKN[module.id] ?: box.sk) in LIGHT_BODIES,
                onChange = { onParam(spec.paramIndex, it) },
                modifier = Modifier.offset(
                    x = cx.dp - diameter / 2f,
                    y = cy.dp - diameter / 2f,
                ),
            )
        }

    }
}

/**
 * `SKN={5:5,6:6,7:10,8:11,9:12,10:7,11:8,12:13,13:14,14:9}`: the cap material per
 * effect. In the preview `kk = SKN[i] !== undefined ? SKN[i] : sk`, so this wins
 * over the per-knob `sk` from `AB()`. The amp, for instance, asks for 1/4 in its
 * `AB()` calls but ends up on 7.
 */
private val SKN = mapOf(5 to 5, 6 to 6, 7 to 10, 8 to 11, 9 to 12, 10 to 7, 11 to 8, 12 to 13, 13 to 14, 14 to 9)

/** Effects whose knob bodies are dark, from `LIGHT` in the reference. */
/**
 * `LIGHT=[1,3,4,8,10,11,12,13]` from the preview. It is an index into the cap
 * materials `c0..c14`, NOT an effect id: the check is `LIGHT.includes(kk)` where
 * `kk = SKN[i] ?? sk`. Testing the effect id instead made every knob of the guitar
 * (id 9, kk 12) and of the bass (id 11, kk 8) dark when the reference draws them
 * light.
 */
private val LIGHT_BODIES = setOf(1, 3, 4, 8, 10, 11, 12, 13)

/** `.ph .nm`/`label` value readout: the reference prints the raw value plus unit. */
private fun formatValue(module: EffectModule, index: Int, norm: Float): String {
    val p = module.paramAt(index) ?: return "${(norm * 100).toInt()}"
    val raw = p.min + (p.max - p.min) * norm
    val d = p.decimals
    return "%.${d}f%s".format(raw, p.unit)
}

/** `.pgt i`: 5x9 padding, radius 99, `#ffffff10` fill, active `#7ad0ff` on `#04101c`. */
@Composable
private fun PageTab(name: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) Color(0xFF7AD0FF) else Color(0x10FFFFFF))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(name, fontSize = 9.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            letterSpacing = (9 * 0.08f).sp,
            color = if (active) Color(0xFF04101C) else Color(0xFFE6EAF3))
    }
}

@Composable
private fun BypassChip(bypassed: Boolean, onClick: () -> Unit, ink: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(28.dp)
            // `.bt` carries `box-shadow:0 2px 4px #0006` plus the usual top bevel, so
            // it reads as a raised key like `.ib`. `raised` is the project's chip
            // relief (two-pass shadow, top highlight, bottom shade).
            .raised(9.dp, 4.dp, Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
    ) {
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(50))
                .background(if (bypassed) Color(0xFF2A3040) else Color(0xFFF2D58A)),
        )
        Spacer(Modifier.width(7.dp))
        // `.bt{color:inherit}`: the chip takes the panel's `.xN` text colour, so it
        // is dark ink on solar's cream face instead of invisible light grey.
        Text("Bypass", fontSize = 10.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, letterSpacing = (10 * 0.06f).sp, color = ink)
    }
}


/**
 * The preview's `TS` table: a per-effect override for the name line that beats the
 * base `.nm{font-size:30px}`. Using 30 px everywhere made echo's name 253 px wide
 * against 160 in the reference, and synth's far too large.
 */
/** `.ph .ty` is a flat 9px in the preview; solar's block reads a touch large next
 * to its 22px title, so it drops one step. */
private val SUB_TITLE_SIZE: Map<Int, Int> = mapOf(4 to 8)

private val NAME_STYLES: Map<Int, Triple<Int, FontWeight, Float>> = mapOf(
    0 to Triple(28, FontWeight.Light, 0f),
    1 to Triple(22, FontWeight.Medium, 0f),
    2 to Triple(21, FontWeight.SemiBold, 21f * 0.04f),
    3 to Triple(23, FontWeight.Normal, 0f),
    4 to Triple(22, FontWeight.ExtraBold, 0f),
    5 to Triple(24, FontWeight.Medium, 24f * 0.02f),
    6 to Triple(24, FontWeight.Medium, 24f * 0.02f),
    7 to Triple(24, FontWeight.Medium, 0f),
    8 to Triple(26, FontWeight.Light, 0f),
    9 to Triple(24, FontWeight.SemiBold, 0f),
    10 to Triple(22, FontWeight.SemiBold, 22f * 0.06f),
    11 to Triple(24, FontWeight.ExtraBold, 0f),
    12 to Triple(24, FontWeight.SemiBold, 0f),
    13 to Triple(26, FontWeight.Light, 0f),
    14 to Triple(14, FontWeight.Bold, 14f * 0.03f),
)

/** `.ib`: height 28, radius 9, gradient #ffffff14 -> #ffffff08, 10 px bold. */
@Composable
private fun SmallChip(label: String, onClick: () -> Unit, active: Boolean = false) {
    RaisedChip(label, active = active, onClick = onClick)
}

/** `[5,7,8,9,12].includes(i)`: the instruments backed by a soundfont. */
private val EDIT_EFFECTS = setOf(5, 7, 8, 9, 12)

/**
 * `#ed`, the panel the Edit chip toggles. The preview's `#ed` has no markup of its
 * own, so this is the soundfont group list the older window carried, kept verbatim.
 */
@Composable
private fun EditPanel(effectId: Int) {
    Column(
        Modifier
            .offset(x = 545.dp, y = 44.dp)
            .width(268.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF01A2130))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text("Soundfont edit", fontSize = 11.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, color = FxStyle.accent(effectId))
        Text("Applies to the soundfont of this track. No reload.", fontSize = 10.sp,
            fontFamily = FxFonts.ui, color = Color(0x99FFFFFF))
        Spacer(Modifier.height(8.dp))
        EDIT_GROUPS.forEach { (head, keys) ->
            Text(head, fontSize = 9.sp, fontFamily = FxFonts.mono,
                fontWeight = FontWeight.Bold, color = FxStyle.accent(effectId))
            keys.forEach { k ->
                Text(k, fontSize = 10.sp, fontFamily = FxFonts.ui, color = Color(0xFFB9C2D4))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private val EDIT_GROUPS: List<Pair<String, List<String>>> = listOf(
    "ENVELOPE" to listOf("Attack", "Decay", "Sustain", "Release"),
    "FILTER" to listOf("Cutoff", "Resonance"),
    "TUNING" to listOf("Coarse", "Fine"),
    "VELOCITY" to listOf("Curve"),
    "VIBRATO" to listOf("Vib depth", "Vib rate"),
    "KEY RANGE" to listOf("Key low", "Key high"),
)

/**
 * `.bt` chip: a low-contrast outline, a top-edge sheen and a short drop shadow so
 * it reads as raised. The active state (`.chg i.a`) is the brass fill.
 */
@Composable
private fun RaisedChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .raised(
                9.dp, 4.dp,
                if (active) Brush.verticalGradient(listOf(Color(0xFFF2D58A), Color(0xFFE0BE6F)))
                else Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF))),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, letterSpacing = (10 * 0.06f).sp,
            color = if (active) Color(0xFF2A1A10) else Color(0xFFE6EAF3), maxLines = 1)
    }
}

/**
 * `.chg i`: `padding:6px 8px; border-radius:7px; background:#00000066; border:1px
 * solid #ffffff14; font:700 10px`, active `#f2d58a` on `#2a1a10`. This is NOT the
 * same chip as `.ib` in the header (radius 9, gradient fill, 0 11 px, drop shadow):
 * reusing the header chip made every option 6 px too wide and gave it a shadow the
 * reference does not draw.
 */
@Composable
private fun ChgChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) Color(0xFFF2D58A) else Color(0x66000000))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            color = if (active) Color(0xFF2A1A10) else Color(0xFFE9DCC0), maxLines = 1)
    }
}

/**
 * One `.chg` group: `.chg span` label (46 wide, brass) then `.chg i` options
 * (padding 6x8, radius 7, `#00000066` fill, `#ffffff14` border, active `#f2d58a`
 * on `#2a1a10`). An option with a `value` is one position of a stepped param; one
 * without is a toggle read as "on" above 0.5.
 */
@Composable
private fun ChipGroupRow(group: ChipGroup, params: FloatArray, onSet: (Pair<Int, Float>) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            // `TG` positions are content coordinates, like `AB`; shift by the same
            // (OXL, OYL) the scenes already carry.
            .offset(x = (group.x + FxStyle.OXL).dp, y = (group.y + FxStyle.OYL).dp),
    ) {
        if (group.label.isNotEmpty()) {
            // `TG` writes `style="width:auto;margin-right:6px"` on the span, which
            // beats `.chg span{width:46px}`: the label wraps its text and only the
            // 6 px gap separates it from the first option. Forcing 46 px pushed
            // every option to the right and made the row too wide.
            Text(group.label, fontSize = 8.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                letterSpacing = (8 * 0.12f).sp, color = Color(0xFFC9B88F),
                maxLines = 1, modifier = Modifier.padding(end = 6.dp))
        }
        group.items.forEach { item ->
            val v = params.getOrElse(item.param) { 0f }
            val on = if (item.value == null) v > 0.5f else kotlin.math.abs(v - item.value) < 0.01f
            ChgChip(item.label, on) { onSet(item.param to (item.value ?: if (v > 0.5f) 0f else 1f)) }
        }
    }
}

/**
 * Preset chip + list (`#pm` / `#pml`). `#pm` is its own dark surface —
 * `linear-gradient(#ffffff18,#ffffff0a)` over `#0d1119`, a `#ffffff1a` border and a
 * 10 px radius — not a `.bt` chip. Drawing it as a translucent chip made it vanish
 * on the light panels (effect 4), where the reference shows a dark 176x28 bar.
 */
@Composable
private fun PresetChip(current: String, presets: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // `#pm` is a fixed-height header (36 px) whose box grows to `max-height: 300px`
    // over 400 ms with cubic-bezier(.2,.9,.2,1); the list is clipped by the box and
    // the rows fade/translate in. Rendering it as a Material DropdownMenu made it
    // pop in a single frame (the "ugly" case the user flagged) and drew it in the
    // wrong place, outside the window chrome. The box below is the real animation:
    // only its height changes, so the header stays put and the rows are revealed.
    val rowH = 34.dp
    val headerH = 34.dp
    // Cap the visible list at 6 presets: `.pi` rows are 11px + 12px text (~34 px),
    // and `#pml` is clamped to `max-height: 236px`, so about six fit before the list
    // scrolls. The `+ Save preset` row is always the last entry.
    val listH = (rowH * (presets.size.coerceAtMost(6) + 1))
    val targetH = if (open) headerH + listH else headerH
    val height by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetH,
        animationSpec = tween(400, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)),
        label = "presetHeight",
    )
    val scrim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(300),
        label = "presetScrim",
    )
    Box(Modifier.width(176.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(height)
                // `#pm.o{box-shadow:0 14px 28px #000a}`: the shadow only appears while
                // the menu is open, which is what makes it read as a raised surface
                // instead of a flat bar.
                .shadow(if (open) 14.dp else 0.dp, RoundedCornerShape(10.dp), clip = false)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0D1119))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .clickable { open = !open },
        ) {
            // `.ph0`: the 34 px header, always visible; the current preset is the label.
            Row(
                Modifier.fillMaxWidth().height(headerH).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current, Modifier.weight(1f), fontSize = 12.sp, fontFamily = FxFonts.ui,
                    fontWeight = FontWeight.Bold, letterSpacing = (12 * 0.06f).sp,
                    color = Color(0xFFE6EAF3), maxLines = 1)
                Text("▾", fontSize = 12.sp, color = Color(0xFFE6EAF3))
            }
            if (scrim > 0f) {
                // `.pi` rows: revealed as the box grows, each fading in on the same
                // 300 ms curve. The scroll keeps a long list inside `max-height`.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    presets.forEach { p ->
                        val selected = p == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(rowH)
                                // `.pi{border-top:1px solid #ffffff0d}`: the hairline
                                // between rows is what separates them, not a gap.
                                .drawBehind {
                                    drawLine(Color(0x0DFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                                }
                                .clickable { open = false; onPick(p) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p, Modifier.weight(1f), fontSize = 12.sp, fontFamily = FxFonts.ui,
                                fontWeight = FontWeight.SemiBold,
                                // `.pi.c{color:#f2d58a}`: the selected preset is brass,
                                // not the shell's blue accent.
                                color = if (selected) Color(0xFFF2D58A) else Color(0xFFE6EAF3),
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    // `.pi sv`: the reference's "Save preset" row, dimmed to 60 %.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(rowH)
                            .drawBehind {
                                drawLine(Color(0x0DFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("＋ Save preset", fontSize = 12.sp, fontFamily = FxFonts.ui,
                            color = Color(0xFF6F7A90), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * `.bt` chip: a low-contrast outline, a top-edge sheen and a short drop shadow so
 * it reads as raised. The active state (`.chg i.a`) is the brass fill.
 */

````

### `FxKnob.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxKnob.kt`
- **Taille :** 15706 octets · **lignes :** 330 · **sha256 (12) :** `6a696841183c`

````kotlin
package com.aetherkeys.fx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.fx.FxFonts
import com.soundstage.mixer.ui.fx.fxPanelText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Skirt shape of a knob, from `KSH` in the reference. */
enum class KnobStyle {
    FLUTE, KNURL, CHROME, GLASS, FLAT, ROUND;

    companion object {
        /** `KSH={6:'flute',10:'knurl',11:'chrome',13:'glass',14:'flat'}`. */
        fun forEffect(id: Int): KnobStyle = when (id) {
            6 -> FLUTE; 10 -> KNURL; 11 -> CHROME; 13 -> GLASS; 14 -> FLAT; else -> ROUND
        }
    }
}


/**
 * `.kn label` and `.vl` in the preview are re-typed per effect: the base rule is
 * `700 9px 'Plus Jakarta Sans'` / `700 11px`, but `.x2 .kn label{font-family:'Share
 * Tech Mono';font-weight:400}`, `.x10 .kn label{font-family:'Orbitron'}`, `.x6
 * .vl{font-size:12px}`, and so on override both the family and the weight. Hard-coding
 * Plus Jakarta Sans everywhere is what made the readouts look out of proportion and
 * what clipped the `E` off the amp's `PRESENCE` (Orbitron is far wider at 9 px).
 */
data class ReadoutFont(val family: androidx.compose.ui.text.font.FontFamily, val weight: FontWeight, val size: Float)

private val READOUT_FONTS: Map<Int, ReadoutFont> = mapOf(
    // Effect 0 (space) had no entry, so it took the 11 sp default. Its readouts
    // read a touch large against the reference, so they are set to 10 sp.
    0 to ReadoutFont(FxFonts.ui, FontWeight.Bold, 10f),
    2 to ReadoutFont(FxFonts.mono, FontWeight.Normal, 11f),
    4 to ReadoutFont(FxFonts.ui, FontWeight.Bold, 10f),
    5 to ReadoutFont(FxFonts.sora, FontWeight.Bold, 11f),
    6 to ReadoutFont(FxFonts.mono, FontWeight.Normal, 12f),
    7 to ReadoutFont(FxFonts.sora, FontWeight.Bold, 11f),
    8 to ReadoutFont(FxFonts.spaceGrotesk, FontWeight.Bold, 11f),
    9 to ReadoutFont(FxFonts.jetBrainsMono, FontWeight.Bold, 11f),
    10 to ReadoutFont(FxFonts.orbitron, FontWeight.Bold, 11f),
    11 to ReadoutFont(FxFonts.sora, FontWeight.Bold, 11f),
    12 to ReadoutFont(FxFonts.spaceGrotesk, FontWeight.Bold, 11f),
    13 to ReadoutFont(FxFonts.spaceGrotesk, FontWeight.Light, 11f),
    14 to ReadoutFont(FxFonts.mono, FontWeight.Normal, 12f),
)

/** The label is the same face as the value, but the base weight is 700 9px. */
private fun labelFont(id: Int): ReadoutFont {
    val f = READOUT_FONTS[id] ?: return ReadoutFont(FxFonts.ui, FontWeight.Bold, LABEL_SIZE)
    return f.copy(size = LABEL_SIZE, weight = if (f.family == FxFonts.mono) FontWeight.Normal else FontWeight.Bold)
}

/** Base label size is 9px; EQ and solar read a step large against their bodies. */
private const val LABEL_SIZE = 9f
private val SMALL_LABELS = setOf(2, 4)

/** `LIGHT=[1,3,4,8,10,11,12,13]`: dark knob bodies, so the pointer turns dark too. */
private val DARK_BODIES = setOf(1, 3, 4, 8, 10, 11, 12, 13)

/** Cap gradients `c0..c14` from the reference `<defs>`; true = linear, false = radial. */
private val CAP_GRADIENTS: Map<Int, Pair<Boolean, List<Color>>> = mapOf(
    0 to (false to listOf(Color(0xFF5B5A7C), Color(0xFF23233A))),
    1 to (true to listOf(Color(0xFFE3CC96), Color(0xFF8D7240), Color(0xFFEED9A6), Color(0xFF7A6234), Color(0xFFCDB27A))),
    2 to (false to listOf(Color(0xFF4A6678), Color(0xFF17252F))),
    3 to (true to listOf(Color(0xFFD8DEE8), Color(0xFF8A92A1), Color(0xFFEEF2F7), Color(0xFF737B89))),
    4 to (false to listOf(Color(0xFFFAF3E2), Color(0xFFC9BB9A))),
    5 to (false to listOf(Color(0xFFF6E6BD), Color(0xFFB08A4A))),
    6 to (false to listOf(Color(0xFF7A5238), Color(0xFF2A1A10))),
    7 to (false to listOf(Color(0xFF55555C), Color(0xFF0A0A0C))),
    8 to (true to listOf(Color(0xFFF4F6FA), Color(0xFF9AA2B0), Color(0xFFE6EAF2), Color(0xFF7C8492), Color(0xFFDFE4EC))),
    9 to (false to listOf(Color(0xFF1E2C44), Color(0xFF080D18))),
    10 to (false to listOf(Color(0xFFFFFFFF), Color(0xFFEFE6DC), Color(0xFFB8A998))),
    11 to (true to listOf(Color(0xFFE2BD86), Color(0xFFB98A52), Color(0xFF8A6034))),
    12 to (true to listOf(Color(0xFFEEF1F5), Color(0xFF9AA1AB), Color(0xFFD6DBE2), Color(0xFF6C737D))),
    13 to (true to listOf(Color(0xFFF4D878), Color(0xFFB8902A), Color(0xFFF0CF6C), Color(0xFF8A6A1E))),
    14 to (false to listOf(Color(0x66FFFFFF), Color(0x22C9B8FF))),
)

/**
 * Knob with the reference look (`knob()` in fx-window-preview.html): 270 degree
 * track from -135, accent value arc, body gradient, dashed skirt, glossy highlight
 * and a marker rotated with the value. Vertical drag: 110 px = full range.
 */
@Composable
fun FxKnob(
    label: String,
    value: Float,
    accent: Color,
    diameter: Dp,
    valueText: String,
    style: KnobStyle,
    light: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    gradientIndex: Int = 0,
    effectId: Int = -1,
) {
    // `.kn` is a column with `label` first, so the name sits above the body and the
    // readout below. Kept out of the flow here: a real column would push the body
    // down by the label's height and break the `AB()` centre the geometry relies on.
    Box(modifier = modifier.size(diameter)) {
        // The gesture key must NOT be `value`: every drag sample calls onChange(),
        // which changes the key and restarts pointerInput, so detectDragGestures is
        // cancelled on the first move and the knob looks dead to the touch. The drag
        // instead captures the value it started from and applies the total delta.
        // rememberUpdatedState so the gesture reads the latest value without being
        // keyed on it (a changing key would cancel the drag mid-gesture).
        val latest = androidx.compose.runtime.rememberUpdatedState(value)
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(effectId, label) {
                    var start = 0f
                    detectDragGestures(
                        onDragStart = { start = latest.value },
                    ) { change, drag ->
                        change.consume()
                        start = (start - drag.y / 110f).coerceIn(0f, 1f)
                        onChange(start)
                    }
                },
        ) {
            drawKnob(value, accent, style, gradientIndex, light)
        }
        val lf = labelFont(effectId).let { if (effectId in SMALL_LABELS) it.copy(size = 8f) else it }
        val vf = READOUT_FONTS[effectId] ?: ReadoutFont(FxFonts.ui, FontWeight.Bold, 11f)
        // `.xN` sets the text colour per effect; the readouts inherited a hard-coded
        // light grey, which is invisible on the light panels (solar `.x4`).
        val ink = fxPanelText(effectId)
        // The box is the knob diameter (46 dp on the amp), but a label like
        // `PRESENCE` in Orbitron 9 sp is wider than that. A plain Text is clipped to
        // the parent's width, which cut the final `E` off; `unbounded` lets the text
        // measure at its natural width and then be centred on the knob, which is the
        // `translate(-50%,-50%)` the reference does.
        Text(label.uppercase(), fontSize = lf.size.sp, fontFamily = lf.family,
            fontWeight = lf.weight, color = ink.copy(alpha = 0.65f),
            softWrap = false, maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-13).dp)
                .wrapContentWidth(unbounded = true))
        Text(valueText, fontSize = vf.size.sp, fontFamily = vf.family, fontWeight = vf.weight,
            color = ink, softWrap = false, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 10.dp)
                .wrapContentWidth(unbounded = true))
    }
}

/**
 * A small, read-only knob for the rack cards: same body, arc and pointer as the
 * full-size [FxKnob], without the drag handling or the label/value readouts. The
 * reference's slot shows the effect's controls under the preview; this is that row.
 */
@Composable
fun FxMiniKnob(
    value: Float,
    accent: Color,
    diameter: Dp,
    style: KnobStyle,
    gradientIndex: Int,
    light: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(diameter)) {
        drawKnob(value, accent, style, gradientIndex, light)
    }
}

private fun DrawScope.drawKnob(value: Float, accent: Color, style: KnobStyle, kk: Int, light: Boolean) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    val a = 270f * value
    val flat = style == KnobStyle.FLAT
    val trackW = if (flat) r * 0.10f else r * 0.07f
    val ar = r * 0.92f

    // track (full 270) then value arc, both from -135 (Compose 0 is 3 o'clock).
    drawArc(Color(0x1CFFFFFF), startAngle = -135f, sweepAngle = 270f, useCenter = false,
        style = Stroke(width = trackW, cap = StrokeCap.Round),
        topLeft = Offset(c.x - ar, c.y - ar), size = Size(ar * 2, ar * 2))
    if (a > 0.1f) {
        drawArc(accent, startAngle = -135f, sweepAngle = a, useCenter = false,
            style = Stroke(width = trackW, cap = StrokeCap.Round),
            topLeft = Offset(c.x - ar, c.y - ar), size = Size(ar * 2, ar * 2))
    }

    // `.kb{filter:drop-shadow(0 7px 7px #000a)}`: the body sits on a soft shadow.
    // Drawn here rather than with `Modifier.shadow` so it also shows up in the
    // Roborazzi capture, which does not render platform elevation.
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x7A000000), Color(0x00000000)),
            center = Offset(c.x, c.y + r * 0.42f),
            radius = r * 0.98f,
        ),
        topLeft = Offset(c.x - r * 0.92f, c.y + r * 0.10f),
        size = Size(r * 1.84f, r * 0.80f),
    )

    // `dark=LIGHT.includes(kk)`: the reference keys the dark bodies off the cap
    // material index `kk`, not the effect id. Effect 9 (guitar) maps to kk 12 via
    // SKN, which is not in LIGHT, so its pointer stays light.
    val dark = light || kk in DARK_BODIES
    val pointer = when {
        kk == 6 -> Color(0xFFF2EAD4)
        kk == 7 || kk == 9 -> accent
        kk == 14 -> Color.White
        kk == 4 || dark -> Color(0xFF1D1B16)
        else -> Color(0xFFF2F4FA)
    }

    when (style) {
        KnobStyle.FLAT -> {
            drawCircle(capBrush(kk), radius = r * 0.66f, center = c)
            drawCircle(accent.copy(alpha = 0.5f), radius = r * 0.66f, center = c, style = Stroke(width = r * 0.024f))
            drawCircle(Color(0x14FFFFFF), radius = r * 0.52f, center = c, style = Stroke(width = r * 0.02f))
            val mp = markerPoint(c, r, a, 0.42f)
            drawCircle(accent.copy(alpha = 0.25f), radius = r * 0.12f, center = mp)
            drawCircle(accent, radius = r * 0.068f, center = mp)
        }
        KnobStyle.GLASS -> {
            drawCircle(Color(0x10FFFFFF), radius = r * 0.70f, center = c)
            drawCircle(Color(0x40FFFFFF), radius = r * 0.70f, center = c, style = Stroke(width = r * 0.024f))
            drawCircle(capBrush(kk), radius = r * 0.56f, center = c)
            drawCircle(accent.copy(alpha = 0.7f), radius = r * 0.56f, center = c, style = Stroke(width = r * 0.02f))
            drawHighlight(c, r)
            markerBar(c, r, a, Color.White)
        }
        else -> {
            drawCircle(Brush.linearGradient(listOf(Color(0xFF2A2D41), Color(0xFF0C0E17))),
                radius = r * 0.76f, center = c)
            drawSkirt(c, r, style)
            val capR = if (style == KnobStyle.CHROME) r * 0.60f else r * 0.54f
            drawCircle(capBrush(kk), radius = capR, center = c)
            drawCircle(Color(0x38FFFFFF), radius = capR, center = c, style = Stroke(width = r * 0.016f))
            if (style == KnobStyle.CHROME) {
                drawCircle(Color(0x59FFFFFF), radius = r * 0.38f, center = c, style = Stroke(width = r * 0.016f))
                drawCircle(Color(0x40000000), radius = r * 0.24f, center = c, style = Stroke(width = r * 0.016f))
            } else if (style == KnobStyle.KNURL) {
                drawCircle(accent.copy(alpha = 0.6f), radius = r * 0.54f, center = c, style = Stroke(width = r * 0.024f))
            }
            drawHighlight(c, r)
            markerBar(c, r, a, pointer)
        }
    }
}

private fun capBrush(kk: Int): Brush {
    val (linear, colors) = CAP_GRADIENTS[kk] ?: (false to listOf(Color(0xFF5B5A7C), Color(0xFF23233A)))
    return if (linear) Brush.linearGradient(colors) else Brush.radialGradient(colors)
}

private fun DrawScope.drawSkirt(c: Offset, r: Float, style: KnobStyle) {
    val dashes = when (style) {
        KnobStyle.KNURL -> floatArrayOf(r * 0.02f, r * 0.026f)
        KnobStyle.FLUTE -> floatArrayOf(r * 0.08f, r * 0.048f)
        else -> floatArrayOf(r * 0.03f, r * 0.044f)
    }
    val w = if (style == KnobStyle.ROUND) r * 0.10f else r * 0.12f
    drawCircle(Color(0xC0000000), radius = r * 0.72f, center = c,
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(dashes)))
}

/**
 * Soft top-left sheen. The reference's `hl` gradient is `#ffffffa8 -> transparent`
 * with `opacity:.5`, i.e. a gentle wash, not a hard blob; the old values (alpha
 * .4 on an already-opaque white) read as a painted-on oval.
 */
private fun DrawScope.drawHighlight(c: Offset, r: Float) {
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0xB3FFFFFF), Color(0x00FFFFFF)),
            center = Offset(c.x - r * 0.18f, c.y - r * 0.30f), radius = r * 0.62f),
        topLeft = Offset(c.x - r * 0.54f, c.y - r * 0.50f),
        size = Size(r * 0.70f, r * 0.34f),
        alpha = 0.26f,
    )
    // Faint bottom bounce: keeps the cap inside its skirt instead of floating.
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF)),
            center = Offset(c.x, c.y + r * 0.34f), radius = r * 0.44f),
        topLeft = Offset(c.x - r * 0.46f, c.y + r * 0.06f),
        size = Size(r * 0.92f, r * 0.44f),
        alpha = 0.5f,
    )
}

private fun markerPoint(c: Offset, r: Float, a: Float, dist: Float): Offset {
    val th = (a - 135f) * PI.toFloat() / 180f
    return Offset(c.x + r * dist * sin(th), c.y - r * dist * cos(th))
}

private fun DrawScope.markerBar(c: Offset, r: Float, a: Float, color: Color) {
    rotate(degrees = a - 135f, pivot = c) {
        drawRoundRect(
            color = color,
            topLeft = Offset(c.x - r * 0.032f, c.y - r * 0.48f),
            size = Size(r * 0.064f, r * 0.30f),
            cornerRadius = CornerRadius(r * 0.032f),
        )
    }
}

````

### `FxShellStore.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxShellStore.kt`
- **Taille :** 10791 octets · **lignes :** 232 · **sha256 (12) :** `36a8d7321162`

````kotlin
package com.aetherkeys.fx.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.aetherkeys.fx.FxRegistry

/**
 * Single source of truth for the FX window. Compose reads it; actions write it and push to [engine].
 * Lots are separate: effects (6 track slots, 8 on master) and one instrument per track (never on master).
 * Parameter arrays come from FxRegistry (the generated registry): size = number of engine params.
 * Convention: the LAST parameter of every module is the level (see FxBottomBar).
 */
class FxShellStore(
    val modules: Map<Int, EffectModule>,
    private val engine: FxEngine,
    /** Strip this store edits. Passed in so the first frame never renders strip 0 by mistake. */
    initialStrip: Int = 0,
) {
    /** Currently edited strip: 0..7 tracks, 8 drum, 9 tonic pad, 10 drum-pad sampler, 15 master. */
    var strip by mutableStateOf(initialStrip)
    /** Id of the open panel, or null for the rack. */
    var openId by mutableStateOf<Int?>(null)
    var addOpen by mutableStateOf(false)
    var addTab by mutableStateOf(FxAddTab.EFFECTS)
    /** Active `SPG` page of the open panel. Only the synth uses more than one. */
    var page by mutableStateOf(0)
    /** `#ed`: the soundfont edit panel, toggled by the Edit chip on effects 5/7/8/9/12. */
    var editOpen by mutableStateOf(false)
    /** Short message shown in the window (refusals, replacements). Cleared by the UI after display. */
    var notice by mutableStateOf<String?>(null)

    // Compose observes these: a plain HashMap made `addEffect` invisible. The rack
    // read `slotsOf(strip)` once and nothing invalidated it, so a newly added effect
    // never appeared until the window was closed and reopened. Snapshot state makes
    // every read of the rack recompose when a slot or the instrument changes.
    private val slots = mutableStateMapOf<Int, SnapshotStateList<Int?>>()
    private val instrument = mutableStateMapOf<Int, Int?>()
    // Parameter arrays are mutated in place by the UI, and an in-place FloatArray
    // write is invisible to Compose. Every mutation therefore goes through [edit],
    // which stores a fresh array so the readers of [valuesOf] recompose. The same
    // applies to the A/B snapshots, whose arrays are copied on write too.
    private val values = mutableStateMapOf<Long, FloatArray>()
    private val bypass = mutableStateMapOf<Long, Boolean>()
    private val abSnap = mutableStateMapOf<Long, Pair<FloatArray, FloatArray>>()

    private fun key(strip: Int, id: Int) = (strip.toLong() shl 32) or id.toLong()

    /**
     * Copy-on-write mutation of the parameter array of [id] on [s]. Compose only
     * sees a change when the map entry is replaced, so the block works on a copy.
     */
    private inline fun edit(s: Int, id: Int, block: (FloatArray) -> Unit) {
        val k = key(s, id)
        val next = (values[k] ?: FxRegistry.info(id).params.map { it.default }.toFloatArray()).copyOf()
        block(next)
        values[k] = next
    }

    fun slotsOf(strip: Int): SnapshotStateList<Int?> = slots.getOrPut(strip) {
        mutableStateListOf<Int?>().apply { repeat(if (strip == 15) 8 else 6) { add(null) } }
    }
    fun instrumentOf(strip: Int): Int? = instrument[strip]
    fun effectCount(strip: Int = this.strip) = slotsOf(strip).count { it != null }
    fun isOn(id: Int): Boolean = slotsOf(strip).contains(id) || instrument[strip] == id

    /** Current values of [id] on the current strip. Created from the registry defaults on first use. */
    fun valuesOf(id: Int): FloatArray = valuesOf(strip, id)

    private fun valuesOf(s: Int, id: Int): FloatArray = values.getOrPut(key(s, id)) {
        FxRegistry.info(id).params.map { it.default }.toFloatArray()
    }

    /**
     * Fills the local state of [s] from an already-restored source *without*
     * pushing to the engine. Used when the shell adopts an existing strip: the
     * engine is the authority at that point, re-pushing would only replay it.
     */
    fun seed(s: Int, ids: List<Int>, params: List<List<Float>>, byp: List<Boolean>, inst: Int?) {
        val list = slotsOf(s)
        ids.forEachIndexed { i, id -> if (i < list.size) list[i] = id.takeIf { it >= 0 } }
        ids.forEachIndexed { i, id ->
            if (id >= 0) {
                val k = key(s, id)
                val v = (values[k] ?: FxRegistry.info(id).params.map { it.default }.toFloatArray()).copyOf()
                params.getOrNull(i)?.forEachIndexed { j, x -> if (j < v.size) v[j] = x }
                values[k] = v
                bypass[k] = byp.getOrNull(i) ?: false
            }
        }
        instrument[s] = inst
    }

    fun isBypassed(id: Int) = bypass[key(strip, id)] ?: false

    fun addEffect(id: Int): Boolean {
        val m = modules[id] ?: return refuse("Unknown effect")
        if (m.kind != FxScreenKind.EFFECT) return refuse("${m.name} is an instrument")
        if (isOn(id)) return refuse("${m.name} is already on this track")
        val list = slotsOf(strip)
        val free = list.indexOfFirst { it == null }
        if (free < 0) return refuse("All effect slots are full")
        if (!guardBool("add effect") { engine.setEffect(strip, free, id) }) return refuse("Engine refused ${m.name}")
        list[free] = id
        pushAll(id)
        return true
    }

    /** One instrument per track. Choosing one replaces the current one. Refused on master. */
    fun setInstrument(id: Int): Boolean {
        if (strip == 15) return refuse("Instruments are not available on master")
        val m = modules[id] ?: return refuse("Unknown instrument")
        if (m.kind == FxScreenKind.EFFECT) return refuse("${m.name} is an effect")
        val old = instrument[strip]
        if (!guardBool("instrument") { engine.setInstrument(strip, id) }) return refuse("Engine refused ${m.name}")
        instrument[strip] = id
        if (old != null && old != id) notice = "${m.name} replaces ${modules[old]?.name ?: "previous"}"
        pushAll(id)
        return true
    }

    fun removeEffect(id: Int) {
        val list = slotsOf(strip)
        val slot = list.indexOf(id)
        if (slot >= 0) { guard("remove effect") { engine.setEffect(strip, slot, -1) }; list[slot] = null }
        if (instrument[strip] == id) { guard("remove instrument") { engine.setInstrument(strip, -1) }; instrument[strip] = null }
    }

    fun setParam(id: Int, index: Int, value: Float) {
        val v = valuesOf(id)
        if (index !in v.indices) return
        edit(strip, id) { it[index] = value.coerceIn(0f, 1f) }
        pushParam(id, index)
    }

    fun toggleBypass(id: Int) {
        val now = !isBypassed(id)
        bypass[key(strip, id)] = now
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) guard("bypass") { engine.setBypass(strip, slot, now) }
    }

    /** Applies preset [index] from the registry: updates local values and tells the engine. */
    fun applyPreset(id: Int, index: Int): Boolean {
        val preset = FxRegistry.info(id).presets.getOrNull(index) ?: return false
        val v = valuesOf(id)
        if (preset.values.size != v.size) return refuse("Preset size mismatch")
        edit(strip, id) { arr -> preset.values.forEachIndexed { i, x -> arr[i] = x } }
        activePreset[key(strip, id)] = index
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) guard("preset") { engine.applyPreset(strip, slot, index) }
        pushAll(id)
        return true
    }

    /** Name of the preset last applied to [id] on this strip, or null when none was. */
    fun activePresetOf(id: Int): String? =
        activePreset[key(strip, id)]?.let { FxRegistry.info(id).presets.getOrNull(it)?.name }

    private val activePreset = mutableStateMapOf<Long, Int>()

    /** A/B: first press stores the current state as A and copies it to B. Next presses swap. */
    fun toggleAB(id: Int) {
        val k = key(strip, id)
        val cur = valuesOf(id)
        val pair = abSnap[k] ?: (cur.copyOf() to cur.copyOf()).also { abSnap[k] = it }
        val (a, b) = pair
        // store the live state into whichever side is active, then load the other side
        val activeIsA = abActive[k] ?: true
        if (activeIsA) { cur.copyInto(a) } else { cur.copyInto(b) }
        val target = if (activeIsA) b else a
        // Copy-on-write so the readers of `valuesOf` recompose on the A/B switch.
        values[k] = target.copyOf()
        abActive[k] = !activeIsA
        pushAll(id)
    }
    private val abActive = mutableStateMapOf<Long, Boolean>()
    fun abLabel(id: Int): String = if (abActive[key(strip, id)] == false) "B" else "A"

    /**
     * Surprise me: random value for every parameter except the level and any locked index.
     * Ranges are the registry ranges (0..1 normalised). Locks come from the UI.
     */
    fun surprise(id: Int, locked: Set<Int> = emptySet()) {
        val v = valuesOf(id)
        val last = v.size - 1
        edit(strip, id) { arr ->
            for (i in arr.indices) {
                if (i == last || i in locked) continue
                arr[i] = kotlin.random.Random.nextFloat()
            }
        }
        pushAll(id)
    }

    private fun pushAll(id: Int) { valuesOf(id).indices.forEach { pushParam(id, it) } }

    private fun pushParam(id: Int, index: Int) {
        val v = valuesOf(id)
        val slot = slotsOf(strip).indexOf(id)
        if (slot >= 0) guard("param") { engine.setParam(strip, slot, index, v[index]) }
        else if (instrument[strip] == id) guard("param") { engine.setInstrumentParam(strip, index, v[index]) }
    }

    private fun refuse(msg: String): Boolean { notice = msg; return false }

    /**
     * Every engine call goes through here. The engine is a native bridge: a
     * refused call or a missing library raises instead of returning, and an
     * exception thrown from a Compose event handler tears the window down
     * without a way back. Folding it into [notice] keeps the UI alive and tells
     * the user what did not apply.
     */
    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            notice = "$what failed: ${e.message ?: e::class.simpleName}"
        }
    }

    /** [guard] for the engine calls that report success with a Boolean. */
    private inline fun guardBool(what: String, block: () -> Boolean): Boolean = try {
        block()
    } catch (e: Throwable) {
        notice = "$what failed: ${e.message ?: e::class.simpleName}"
        false
    }
}

````

### `FxAddOverlay.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxAddOverlay.kt`
- **Taille :** 13520 octets · **lignes :** 300 · **sha256 (12) :** `6661bf7f362b`

````kotlin
package com.aetherkeys.fx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.ui.fx.FxFonts

/** Add overlay: two tabs (Effects / Instrument), family chips, search, grid. Tiles are sized from FxGeom. */
enum class FxAddTab { EFFECTS, INSTRUMENT }

/**
 * The `#ad` sheet of fx-window-preview.html, ported from `renderAdd()` / `listAdd()`:
 * header, the two tab pills with their counts, family chips plus a search box, then
 * a three-column grid of tiles. Each tile carries the effect's own icon, its name in
 * the effect's face, and a `family · description` line, exactly as the preview's
 * `CAT2` table spells them. The old version was a bare name/type list with no icons,
 * families or search, which is why it read as "a weird, ugly page".
 */
@Composable
fun FxAddOverlay(
    modules: List<EffectModule>,
    tab: FxAddTab,
    onTab: (FxAddTab) -> Unit,
    onPick: (EffectModule) -> Unit,
    isOn: (Int) -> Boolean,
    effectsUsed: Int,
    onClose: () -> Unit,
) {
    var family by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }

    val families = if (tab == FxAddTab.EFFECTS) FAMS_EFFECTS else FAMS_INSTRUMENT
    if (family !in families) family = "All"

    val isInstrument = { m: EffectModule -> m.kind != FxScreenKind.EFFECT }
    val visible = modules
        .filter { if (tab == FxAddTab.EFFECTS) !isInstrument(it) else isInstrument(it) }
        .filter { family == "All" || AddMeta.familyOf(it.id) == family }
        .filter {
            query.isBlank() || (it.name + " " + it.type + " " + AddMeta.descOf(it.id) + " " + AddMeta.familyOf(it.id))
                .contains(query, ignoreCase = true)
        }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // `.ah`: height 48, title left, close right.
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (tab == FxAddTab.EFFECTS) "Add an effect" else "Choose the instrument",
                fontSize = 12.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (12 * 0.16f).sp, color = Color(0xFF8D96AB),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x08FFFFFF))))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(9.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(11.dp)) {
                    val s = 1.6f * density
                    drawLine(Color(0xFFE6EAF3), Offset(0f, 0f), Offset(size.width, size.height), s, cap = StrokeCap.Round)
                    drawLine(Color(0xFFE6EAF3), Offset(size.width, 0f), Offset(0f, size.height), s, cap = StrokeCap.Round)
                }
            }
        }

        // `.tbs`: EFFECTS n/6 | INSTRUMENT 1 per track.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabPill("EFFECTS", "$effectsUsed/6", tab == FxAddTab.EFFECTS) { onTab(FxAddTab.EFFECTS) }
            TabPill("INSTRUMENT", "1 per track", tab == FxAddTab.INSTRUMENT) { onTab(FxAddTab.INSTRUMENT) }
        }

        // `.cf`: family chips, search box pushed to the right.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            families.forEach { f ->
                val on = family == f
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (on) Color(0xFFF2F4FA) else Color(0x0EFFFFFF))
                        .border(1.dp, if (on) Color.Transparent else Color(0x14FFFFFF), RoundedCornerShape(99.dp))
                        .clickable { family = f }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(f, fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                        letterSpacing = (10 * 0.06f).sp,
                        color = if (on) Color(0xFF0B0F1C) else Color(0xFFE6EAF3))
                }
            }
            Box(Modifier.weight(1f))
            Box(
                Modifier
                    .width(150.dp).height(28.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x44000000))
                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(99.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text("Search…", fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6D778C))
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFFFFFFFF), fontSize = 11.sp, fontFamily = FxFonts.ui),
                    cursorBrush = SolidColor(Color(0xFF8FB2E8)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // `.gl`: three columns of tiles.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visible, key = { it.id }) { m ->
                val instrument = isInstrument(m)
                val on = isOn(m.id)
                val disabled = !instrument && (on || effectsUsed >= 6)
                AddTile(m = m, on = on, disabled = disabled, onClick = { onPick(m) })
            }
        }
    }
}

/** `.tbs span`: a pill with the label and its count on separate lines. */
@Composable
private fun TabPill(label: String, count: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFF2F4FA) else Color(0x0EFFFFFF))
            .border(1.dp, if (selected) Color.Transparent else Color(0x14FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (10 * 0.08f).sp,
            color = if (selected) Color(0xFF0B0F1C) else Color(0xFFE6EAF3))
        Text(count, fontSize = 9.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF5A6478) else Color(0xFF8D96AB))
    }
}

/** `.tl`: icon, name in the effect's face, `family · description`, optional ON badge. */
@Composable
private fun AddTile(m: EffectModule, on: Boolean, disabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth().height(FxGeom.ADD_TILE_H.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x12FFFFFF), Color(0x06FFFFFF))))
            .border(1.dp, Color(0x12FFFFFF), shape)
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp)
            .then(if (disabled) Modifier.alpha(0.4f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(AddMeta.iconRes(m.id)),
            contentDescription = null,
            tint = Color(0xFFCFD6E6),
            modifier = Modifier.size(30.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                m.name,
                fontSize = 14.sp,
                fontFamily = FxFonts.title(m.id),
                color = Color(0xFFE6EAF3),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${AddMeta.familyOf(m.id)} · ${AddMeta.prefix(m.id)}${AddMeta.descOf(m.id)}",
                fontSize = 9.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8D96AB), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (on) {
            Text("✓ ON THIS TRACK", fontSize = 7.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                color = Color(0xFF9FE0B0))
        }
    }
}

private val FAMS_EFFECTS = listOf("All", "Dynamics", "EQ", "Space", "Amps", "Voices")
private val FAMS_INSTRUMENT = listOf("All", "Keys", "Guitar", "Winds", "Strings", "Synth")

/**
 * `CAT2`, `ICK` and `INSTR` from the preview: per-effect family, description and icon.
 * Keeping the tables here means the sheet cannot drift from the reference.
 */
private object AddMeta {
    private val INSTR = setOf(5, 6, 7, 8, 9, 12, 14)
    private val SOURCES = setOf(6, 14)

    private val CAT2: Map<Int, Triple<String, String, String>> = mapOf(
        3 to Triple("comp", "Dynamics", "Controls the punch"),
        4 to Triple("max", "Dynamics", "Loud, clean, finished"),
        2 to Triple("eq", "EQ", "Shapes the tone"),
        1 to Triple("dly", "Space", "Echoes that follow the groove"),
        0 to Triple("rev", "Space", "Rooms, halls, deep space"),
        10 to Triple("amp", "Amps", "Tube amp · Cab · Mic"),
        11 to Triple("bas", "Amps", "Bass head · Cab · Graphic EQ"),
        5 to Triple("pno", "Keys", "Felt · Lid · Pedals"),
        6 to Triple("org", "Keys", "Drawbars · Rotary"),
        14 to Triple("syn", "Synth", "Oscillators · Filter · Motion"),
        7 to Triple("sax", "Winds", "Breath · Reed · Growl"),
        12 to Triple("brs", "Winds", "Mutes · Lips · Section"),
        8 to Triple("str", "Strings", "Bow · Section · Rosin"),
        9 to Triple("gtr", "Guitar", "Pick · Amp · Strum"),
        13 to Triple("voc", "Voices", "Vowels · Choir · Breath"),
    )

    fun familyOf(id: Int) = CAT2[id]?.second ?: "All"
    fun descOf(id: Int) = CAT2[id]?.third ?: ""

    fun prefix(id: Int) = when {
        SOURCES.contains(id) -> "Sound source · "
        INSTR.contains(id) -> "Shapes the soundfont · "
        else -> ""
    }

    fun iconRes(id: Int): Int = when (CAT2[id]?.first) {
        "rev" -> R.drawable.ic_fx_space
        "dly" -> R.drawable.ic_fx_echo
        "eq" -> R.drawable.ic_fx_tone
        "comp" -> R.drawable.ic_fx_grip
        "max" -> R.drawable.ic_fx_ceiling
        "pno" -> R.drawable.ic_fx_piano
        "org" -> R.drawable.ic_fx_organ
        "sax" -> R.drawable.ic_fx_sax
        "str" -> R.drawable.ic_fx_strings
        "gtr" -> R.drawable.ic_fx_guitar
        "amp" -> R.drawable.ic_fx_amp
        "bas" -> R.drawable.ic_fx_bass
        "brs" -> R.drawable.ic_fx_brass
        "voc" -> R.drawable.ic_fx_voices
        "syn" -> R.drawable.ic_fx_synth
        else -> R.drawable.ic_fx_tone
    }
}

````

### `FxGeometry.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxGeometry.kt`
- **Taille :** 10718 octets · **lignes :** 221 · **sha256 (12) :** `f3ba5ff64fbb`

````kotlin
// GENERATED from fx-window-preview.html (layout extractor). Numbers are measured, not hand-typed.
package com.aetherkeys.fx.ui

object FxGeom {
    const val LOGICAL_W = 825f
    const val LOGICAL_H = 343f
    const val CONTENT_X = 20f
    const val CONTENT_Y = 58f
    const val BOTTOM_BAR_Y = 214f
    const val BOTTOM_BAR_H = 71f
    const val LEVEL_X = 540f
    const val LEVEL_W = 200f
    const val LEVEL_Y = 252f
    const val CARD_W = 161.0f
    const val CARD_H = 288.3f
    const val ADD_TILE_W = 263.0f
    const val ADD_TILE_H = 58.9f
}

/**
 * A knob's box, in window coordinates, taken straight from the preview's
 * `AB(i,j,x,y,z,sk)` table (centre (x,y) in content coordinates, diameter z).
 * [sk] is the cap material `c0..c14`; the preview defaults it to the effect id,
 * and `SKN` overrides it per effect.
 */
data class KnobBox(
    val label: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val sk: Int,
    /** `SPG` page this knob belongs to; -1 means it is drawn on every page. */
    val page: Int = -1,
)

object FxKnobBoxes {
    val byEffect: Map<Int, List<KnobBox>> = mapOf(
        0 to listOf(
            KnobBox("Size", 387.8f, 53.8f, 54.8f, 79.2f, 0),
            KnobBox("Decay", 614.1f, 84.4f, 54.8f, 79.2f, 0),
            KnobBox("Pre-delay", 665.3f, 153.1f, 64.0f, 79.2f, 0),
            KnobBox("Damp", 513.4f, 208.2f, 54.8f, 79.2f, 0),
            KnobBox("Width", 262.3f, 208.2f, 54.8f, 79.2f, 0),
            KnobBox("Mix", 105.7f, 153.1f, 54.8f, 79.2f, 0),
            KnobBox("Diffuse", 161.6f, 84.4f, 54.8f, 79.2f, 0),
        ),
                        1 to listOf(
            KnobBox("Feedback", 642.0f, 100.0f, 56.0f, 56.0f, 1),
            KnobBox("Tone", 727.0f, 100.0f, 56.0f, 56.0f, 1),
            KnobBox("Wow", 684.0f, 218.0f, 56.0f, 56.0f, 1),
            KnobBox("Mix", 89.0f, 237.0f, 52.0f, 52.0f, 1),
        ),
                        2 to listOf(
            KnobBox("Low", 36.0f, 134.0f, 100.0f, 100.0f, 3),
            KnobBox("Mid", 320.0f, 242.0f, 60.0f, 60.0f, 4),
            KnobBox("High", 428.0f, 252.0f, 44.0f, 44.0f, 1),
            KnobBox("Mid Q", 645.0f, 141.0f, 78.0f, 78.0f, 2),
        ),
                        3 to listOf(
            KnobBox("Ratio", 609.0f, 97.0f, 78.0f, 78.0f, 3),
            KnobBox("Knee", 591.0f, 231.0f, 54.0f, 54.0f, 3),
            KnobBox("Attack", 663.0f, 231.0f, 54.0f, 54.0f, 3),
            KnobBox("Release", 720.0f, 97.0f, 50.0f, 50.0f, 3),
            KnobBox("Makeup", 735.0f, 231.0f, 54.0f, 54.0f, 3),
        ),
                        4 to listOf(
            KnobBox("Amount", 77.0f, 118.0f, 136.0f, 136.0f, 4),
            KnobBox("Ceiling", 244.0f, 98.0f, 76.0f, 76.0f, 4),
            KnobBox("Release", 257.0f, 229.0f, 50.0f, 50.0f, 4),
            KnobBox("Width", 335.0f, 229.0f, 50.0f, 50.0f, 4),
            KnobBox("Mix", 335.0f, 103.0f, 50.0f, 50.0f, 4),
            KnobBox("Output", 724.0f, 155.0f, 62.0f, 62.0f, 4),
        ),
                        5 to listOf(
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        6 to listOf(
            KnobBox("Click", 742.0f, 144.0f, 40.0f, 40.0f, 1),
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Drive", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        7 to listOf(
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        8 to listOf(
            KnobBox("Color", 680.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Time", 756.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Morph", 566.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Motion", 378.0f, 195.0f, 62.0f, 62.0f, 8),
            KnobBox("Pressure", 212.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Speed", 96.0f, 214.0f, 40.0f, 40.0f, 8),
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 8),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 8),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 8),
        ),
                        9 to listOf(
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 9),
            KnobBox("Resonance", 289.0f, 289.0f, 42.0f, 42.0f, 9),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 9),
        ),
                        10 to listOf(
            KnobBox("Gain", 67.0f, 119.0f, 46.0f, 46.0f, 4),
            KnobBox("Bass", 147.0f, 119.0f, 46.0f, 46.0f, 1),
            KnobBox("Mid", 227.0f, 119.0f, 46.0f, 46.0f, 4),
            KnobBox("Treble", 307.0f, 119.0f, 46.0f, 46.0f, 1),
            KnobBox("Presence", 387.0f, 119.0f, 46.0f, 46.0f, 4),
            KnobBox("Master", 467.0f, 119.0f, 46.0f, 46.0f, 1),
            KnobBox("Level", 727.0f, 119.0f, 46.0f, 46.0f, 4),
        ),
                        11 to listOf(
            KnobBox("Drive", 328.0f, 108.0f, 44.0f, 44.0f, 1),
            KnobBox("Comp", 398.0f, 108.0f, 44.0f, 44.0f, 4),
            KnobBox("Blend", 468.0f, 108.0f, 44.0f, 44.0f, 1),
            KnobBox("Level", 727.0f, 241.0f, 46.0f, 46.0f, 4),
        ),
                        12 to listOf(
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        13 to listOf(
            KnobBox("Breath", 68.0f, 92.0f, 44.0f, 44.0f, 4),
            KnobBox("Choir", 68.0f, 186.0f, 44.0f, 44.0f, 1),
            KnobBox("Vibrato", 698.0f, 96.0f, 44.0f, 44.0f, 4),
            KnobBox("Tone", 149.0f, 289.0f, 42.0f, 42.0f, 4),
            KnobBox("Body", 289.0f, 289.0f, 42.0f, 42.0f, 1),
            KnobBox("Space", 429.0f, 289.0f, 42.0f, 42.0f, 4),
        ),
                        14 to listOf(
            KnobBox("Wave A", 45.0f, 140.0f, 40.0f, 40.0f, 1, 0),
            KnobBox("Wave B", 125.0f, 140.0f, 40.0f, 40.0f, 1, 0),
            KnobBox("Detune", 205.0f, 140.0f, 40.0f, 40.0f, 4, 0),
            KnobBox("Sub", 45.0f, 208.0f, 40.0f, 40.0f, 4, 0),
            KnobBox("Noise", 125.0f, 208.0f, 40.0f, 40.0f, 4, 0),
            KnobBox("Unison", 205.0f, 208.0f, 40.0f, 40.0f, 1, 0),
            KnobBox("Drive", 764.0f, 102.0f, 40.0f, 40.0f, 1, 0),
            KnobBox("Env amt", 630.0f, 102.0f, 40.0f, 40.0f, 1, 0),
            KnobBox("Spread", 700.0f, 102.0f, 40.0f, 40.0f, 4, 0),
            KnobBox("LFO1 rate", 50.0f, 166.0f, 40.0f, 40.0f, 1, 1),
            KnobBox("LFO1 depth", 130.0f, 166.0f, 40.0f, 40.0f, 4, 1),
            KnobBox("LFO2 rate", 290.0f, 166.0f, 40.0f, 40.0f, 1, 1),
            KnobBox("LFO2 depth", 370.0f, 166.0f, 40.0f, 40.0f, 4, 1),
            KnobBox("Macro 4", 149.0f, 289.0f, 42.0f, 42.0f, 4, -1),
            KnobBox("Glide", 289.0f, 289.0f, 42.0f, 42.0f, 1, -1),
            KnobBox("Voices", 429.0f, 289.0f, 42.0f, 42.0f, 4, -1),
            KnobBox("Bend", 650.0f, 214.0f, 40.0f, 40.0f, 4, 1),
            KnobBox("Tune", 58.0f, 140.0f, 44.0f, 44.0f, 1, 2),
            KnobBox("Macro 1", 138.0f, 140.0f, 44.0f, 44.0f, 4, 2),
            KnobBox("Macro 2", 218.0f, 140.0f, 44.0f, 44.0f, 1, 2),
            KnobBox("Macro 3", 298.0f, 140.0f, 44.0f, 44.0f, 4, 2),
        ),
    )
}

/** One option inside a `.chg` group. [value] null means a toggle on [param]. */
data class ChipItem(val label: String, val param: Int, val value: Float? = null)

/** A `.chg` group: a label plus its options, at an absolute window position. */
data class ChipGroup(val label: String, val x: Float, val y: Float, val items: List<ChipItem>)

private fun pick(p: Int, v: Float, vararg l: String) =
    l.mapIndexed { i, n -> ChipItem(n, p, v * i) }

/** `SPG(p, html)`: a page of the synth screen, shown one at a time. */
data class ChipPage(val page: Int, val groups: List<ChipGroup>)

/**
 * `TG(i,label,x,y,items)` from the preview, in window coordinates. A `TG` label is
 * never empty in the source except on the synth, where `TG(14,'',...)` marks a
 * group that belongs to a page. Effects 5, 7, 8 and 13 have no `TG` call at all:
 * their chips are ADSR handles drawn on the canvas by `HS`, not DOM groups.
 *
 * For 14, `SPG(p, ...)` wraps each page; the reference shows one page at a time and
 * starts on page 0.
 */
object FxChipGroups {
    val byEffect: Map<Int, List<ChipGroup>> = mapOf(
        6 to listOf(
            ChipGroup("PERCUSSION", 520f, 10f, listOf(
                ChipItem("ON", 18), ChipItem("SOFT", 19), ChipItem("FAST", 20), ChipItem("3RD", 21))),
            ChipGroup("", 640f, 84f, listOf(ChipItem("UPPER", 23))),
            ChipGroup("", 640f, 116f, listOf(ChipItem("LOWER", 24))),
            ChipGroup("ROTARY", 520f, 176f, listOf(
                ChipItem("STOP", 25, 0f), ChipItem("SLOW", 25, 0.5f), ChipItem("FAST", 25, 1f))),
        ),
        9 to listOf(ChipGroup("BODY", 16f, 6f,
            pick(0, 0.333f, "ACOUSTIC", "NYLON", "ELEC DC", "ELEC SC"))),
        10 to listOf(
            ChipGroup("CHANNEL", 16f, 1f, pick(6, 0.5f, "CLEAN", "CRUNCH", "LEAD")),
            ChipGroup("PEDALS", 560f, 1f, listOf(ChipItem("BOOST", 9), ChipItem("TREM", 10)))),
        11 to listOf(ChipGroup("CABINET", 16f, 134f, listOf(
            ChipItem("1\u00d715", 8, 0f), ChipItem("4\u00d710", 8, 0.5f), ChipItem("8\u00d710", 8, 1f)))),
        12 to listOf(
            ChipGroup("MUTE", 16f, 6f, listOf(
                ChipItem("OPEN", 4, 0f), ChipItem("STRAIGHT", 4, 0.25f), ChipItem("CUP", 4, 0.5f),
                ChipItem("HARMON", 4, 0.75f), ChipItem("PLUNGER", 4, 1f))),
            ChipGroup("SECTION", 500f, 6f, listOf(
                ChipItem("1", 3, 0f), ChipItem("2", 3, 0.25f), ChipItem("3", 3, 0.5f),
                ChipItem("4", 3, 0.75f), ChipItem("5", 3, 1f))),
        ),
    )

    /** The synth's three pages. Page 0 also carries the filter group at (586,120). */
    val pages: Map<Int, List<ChipPage>> = mapOf(
        14 to listOf(
            ChipPage(0, listOf(
                ChipGroup("", 586f, 120f, pick(11, 0.333f, "LP", "HP", "BP", "NOTCH")))),
            ChipPage(1, listOf(
                ChipGroup("", 14f, 178f, pick(18, 0.333f, "SIN", "TRI", "SAW", "SQR")),
                ChipGroup("", 250f, 178f, pick(19, 0.333f, "CUTOFF", "PITCH", "WAVE", "LEVEL")))),
            ChipPage(2, listOf(
                ChipGroup("", 14f, 14f, pick(26, 0.5f, "POLY", "MONO", "LEGATO")))),
        ),
    )
}

````

### `FxClock.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxClock.kt`
- **Taille :** 1255 octets · **lignes :** 31 · **sha256 (12) :** `74bad138b80d`

````kotlin
package com.aetherkeys.fx.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * One time source for the FX window, in seconds since the first frame.
 * It measures real frame deltas (vsync), so a slow frame never speeds up or freezes the motion.
 * [fullRate] = open panel (every display frame). Otherwise rack cards are refreshed at [miniFps].
 * Changing a preset does not restart or recreate this clock.
 */
@Composable
fun rememberFxClock(fullRate: Boolean, miniFps: Int = 15): State<Float> =
    produceState(initialValue = 0f, fullRate, miniFps) {
        var last = -1L
        var elapsed = 0f
        while (true) {
            val nowNs = withFrameNanos { it }
            if (last >= 0L) elapsed += (nowNs - last) / 1_000_000_000f
            last = nowNs
            value = elapsed
            if (!fullRate) delay(1000L / miniFps.coerceIn(5, 60))
        }
    }

/** Reduced motion and card rate come from settings state, never hard-coded in a scene. */
data class FxAnimPrefs(val reducedMotion: Boolean = false, val miniFps: Int = 15)

````

### `FxModule.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxModule.kt`
- **Taille :** 2501 octets · **lignes :** 58 · **sha256 (12) :** `f5633fdcc221`

````kotlin
package com.aetherkeys.fx.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.aetherkeys.fx.FxKind
import com.aetherkeys.fx.FxRegistry
import com.soundstage.mixer.ui.fx.FxSceneState
import com.soundstage.mixer.ui.fx.FxStyle
import com.soundstage.mixer.ui.fx.drawFxScene
import com.soundstage.mixer.ui.fx.knobsFor

/**
 * Bridges the generated registry (com.aetherkeys.fx) and the already-written
 * scenes (com.soundstage.mixer.ui.fx) to the shell's [EffectModule] contract.
 * Nothing is re-drawn here: [drawScene] delegates to `drawFxScene`, the faithful
 * port of the reference's per-effect drawing functions.
 */
class FxModule(private val effectId: Int) : EffectModule {
    // Same bound as FxRegistry.info(): an unknown id must never index all[id] and crash.
    private val info = FxRegistry.all.getOrElse(effectId) { FxRegistry.all[0] }

    override val id = effectId
    override val name = info.name
    override val type = info.type
    override val kind: FxScreenKind = when (info.kind) {
        FxKind.EFFECT -> FxScreenKind.EFFECT
        FxKind.SHAPER -> FxScreenKind.SHAPER
        FxKind.SOURCE -> FxScreenKind.SOURCE
    }
    override val accent = FxStyle.accent(effectId)
    override val fontId = info.name
    override val presets = info.presets.map { it.name }
    override val params = info.params

    /**
     * Shell knob specs, from the `AB(id,param,x,y,z)` calls in the reference: the
     * measured box carries the position and the knob diameter (z), and the parameter
     * index is bound here instead of being paired by position at draw time.
     */
    override val knobs: List<FxKnobSpec> = knobsFor(effectId).map { s ->
        FxKnobSpec(
            label = info.params.getOrNull(s.param)?.name ?: "",
            box = KnobBox(info.params.getOrNull(s.param)?.name ?: "", s.x, s.y, s.size, s.size, effectId),
            paramIndex = s.param,
        )
    }

    private val sceneState = FxSceneState()

    override fun DrawScope.drawScene(params: FloatArray, t: Float, mini: Boolean, tm: TextMeasurer, page: Int) {
        // The synth's `SYPG` global: the scene draws only the active `SPG` page.
        sceneState.presetIndex = if (effectId == 14 && page >= 0) page else 0
        drawFxScene(effectId, params, t, tm, sceneState)
    }
}

/** All 15 modules, ids 0..14, in registry order. */
fun fxModules(): Map<Int, EffectModule> = FxRegistry.all.associate { it.id to FxModule(it.id) }

````

### `EffectModule.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/EffectModule.kt`
- **Taille :** 2300 octets · **lignes :** 55 · **sha256 (12) :** `f68cd0b32273`

````kotlin
package com.aetherkeys.fx.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.aetherkeys.fx.FxParam

/** Shell-level kind. Shaper and Source are instruments (one per track); Effect goes into the 6 (track) / 8 (master) slots. */
enum class FxScreenKind { EFFECT, SHAPER, SOURCE }

/** One control of an effect screen: its measured box (FxKnobBoxes) and the engine parameter index it drives. */
data class FxKnobSpec(val label: String, val box: KnobBox, val paramIndex: Int)

/**
 * Contract every effect or instrument screen implements. The shell (FxWindow) only knows this interface.
 * Scenes never lay out knobs themselves: positions come from FxKnobBoxes, scaled by the shell.
 */
interface EffectModule {
    val id: Int
    val name: String
    val type: String
    val kind: FxScreenKind
    val accent: Color
    /** Font family id, resolved by the app's font table (see FxFonts). */
    val fontId: String
    val knobs: List<FxKnobSpec>
    val presets: List<String>
    /** Registry parameters of this screen, in engine index order (size = engine param count). */
    val params: List<FxParam>

    /** Registry parameter at [index], or null when the screen has fewer parameters. */
    fun paramAt(index: Int): FxParam? = params.getOrNull(index)

    /**
     * Draws the animated scene inside the scene frame.
     * [t]: seconds since the screen opened (never reset by a preset change).
     * [mini]: true when drawing a rack card (reduced detail, same layout).
     */
    fun DrawScope.drawScene(params: FloatArray, t: Float, mini: Boolean, tm: TextMeasurer, page: Int = -1)
}

/** Used until a real scene exists: the frame, the knobs and the header are real, the scene area is empty. */
class PlaceholderModule(
    override val id: Int,
    override val name: String,
    override val type: String,
    override val kind: FxScreenKind,
    override val accent: Color,
    override val fontId: String,
    override val knobs: List<FxKnobSpec>,
    override val presets: List<String>,
    override val params: List<FxParam> = emptyList(),
) : EffectModule {
    override fun DrawScope.drawScene(params: FloatArray, t: Float, mini: Boolean, tm: TextMeasurer, page: Int) = Unit
}

````

### `FxEngine.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxEngine.kt`
- **Taille :** 861 octets · **lignes :** 17 · **sha256 (12) :** `422508719408`

````kotlin
package com.aetherkeys.fx.ui

/**
 * Everything the UI asks of the audio engine. The UI never calls native code directly.
 * Implement once in the app (JNI bridge). Indices are the engine's parameter indices (0..31).
 * Strips: 0..7 tracks, 8 drum channel, 9 tonic pad, 10 drum-pad sampler, 15 master.
 * Every method returns false (or does nothing) when the engine refuses; the store then shows a notice.
 */
interface FxEngine {
    fun setEffect(strip: Int, slot: Int, id: Int): Boolean
    fun setInstrument(strip: Int, id: Int): Boolean
    fun setParam(strip: Int, slot: Int, index: Int, value: Float)
    fun setInstrumentParam(strip: Int, index: Int, value: Float)
    fun setBypass(strip: Int, slot: Int, bypassed: Boolean)
    fun applyPreset(strip: Int, slot: Int, presetIndex: Int): Boolean
    fun meter(strip: Int, slot: Int, index: Int): Float
}

````

### `FxControllerEngine.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxControllerEngine.kt`
- **Taille :** 1249 octets · **lignes :** 30 · **sha256 (12) :** `6bc3ea051eb2`

````kotlin
package com.aetherkeys.fx.ui

import com.soundstage.mixer.fx.FxController

/**
 * Binds the shell's [FxEngine] to the existing [FxController]. No new native call
 * is introduced: every method maps to one already used by the old FX window.
 * The controller is bound to one strip, so the store is created per strip and the
 * strip argument is informational here.
 */
class FxControllerEngine(private val controller: FxController) : FxEngine {

    override fun setEffect(strip: Int, slot: Int, id: Int): Boolean = controller.setEffect(slot, id)

    override fun setInstrument(strip: Int, id: Int): Boolean = controller.setInstrument(id)

    override fun setParam(strip: Int, slot: Int, index: Int, value: Float) =
        controller.setEffectParam(slot, index, value)

    override fun setInstrumentParam(strip: Int, index: Int, value: Float) =
        controller.setInstrumentParam(index, value)

    override fun setBypass(strip: Int, slot: Int, bypassed: Boolean) =
        controller.setBypass(slot, bypassed)

    override fun applyPreset(strip: Int, slot: Int, presetIndex: Int): Boolean =
        controller.applyPreset(slot, presetIndex)

    override fun meter(strip: Int, slot: Int, index: Int): Float = controller.meter(slot, index)
}

````

### `FxBottomBar.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fx/ui/FxBottomBar.kt`
- **Taille :** 616 octets · **lignes :** 14 · **sha256 (12) :** `0cfd096254fa`

````kotlin
package com.aetherkeys.fx.ui

/**
 * Bottom bar values are read from FxGeom (BOTTOM_BAR_Y/H, LEVEL_X/Y/W). This file only documents
 * the contract used by FxOpenPanel: the bar is a plain strip; the level control is the last parameter
 * of every module (params.size - 1), in normalised 0..1, displayed in dB by FxParamFormat.
 */
object FxBottomBar {
    const val LEVEL_MIN_DB = -24f
    const val LEVEL_MAX_DB = 6f

    /** Level in dB from normalised value: linear mapping over the module range, as in the reference. */
    fun levelDb(norm: Float): Float = LEVEL_MIN_DB + (LEVEL_MAX_DB - LEVEL_MIN_DB) * norm
}

````

### `FxRack.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/fx/FxRack.kt`
- **Taille :** 16515 octets · **lignes :** 391 · **sha256 (12) :** `ab1f6e0a0704`

````kotlin
package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.aetherkeys.fx.ui.FxMiniKnob
import com.aetherkeys.fx.ui.KnobStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxKind
import com.soundstage.mixer.fx.FxRegistry

/** FX strip for a mixer track: tracks 1..8 map to strips 0..7, track 0 is master. */
fun stripForTrack(trackId: Int): Int =
    if (trackId == 0) FxRegistry.STRIP_MASTER else (trackId - 1).coerceIn(0, 7)

private val CardBg = Brush.linearGradient(listOf(Color(0xFF323845), Color(0xFF222733)))
private val HeaderBg = Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF)))

/**
 * Reduced, animated preview of an effect, as the rack cards show it. The card is
 * 158x164 logical units; the scene is drawn at the preview's own mini scale
 * (card width / 825) and centred vertically.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FxCard(
    id: Int,
    selected: Boolean,
    lifted: Boolean,
    t: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = remember(id) { FxRegistry.info(id) }
    val accent = FxStyle.accent(id)
    val tm = rememberTextMeasurer()
    val scene = remember(id) { FxSceneState() }
    val values = remember(id) { FloatArray(FxRegistry.MAX_PARAMS) { i -> info.params.getOrNull(i)?.default ?: 0f } }
    var dragY by remember(id) { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .width(158.dp)
            .zIndex(if (lifted) 20f else 0f)
            .offset(y = dragY.dp)
            .graphicsLayer { alpha = if (lifted) 0.92f else 1f }
            // Raised card: no outline, the lift comes from the drop shadow and a
            // 1px inner highlight along the top edge (`.cd` in the reference uses
            // `0 8px 16px #0008, inset 0 1px 0 #ffffff1c`). Selection is shown by
            // the shadow's accent tint instead of a border.
            // Raised card, no outline: the lift is the shadow below plus the edge
            // sheen, and selection deepens the shadow instead of adding a border.
            .raised(16.dp, if (selected) 13.dp else 8.dp, CardBg)
            .combinedClickable(onClick = { onClick() }, onLongClick = { onLongClick() })
            .pointerInput(id) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragStop() },
                    onDragCancel = { onDragStop() },
                ) { change, amount ->
                    change.consume()
                    dragY += amount
                    onDrag(dragY)
                }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(info.type.uppercase(), color = Color(0xFF8D96AB), fontSize = 8.sp,
            fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(info.name, color = Color(0xFFDFE4EE), fontSize = 20.sp,
            fontFamily = FxFonts.title(id), maxLines = 1)
        // The scene is 825x343, so on a 136-wide tile it only fills a thin band and
        // leaves the rest empty. The void is filled with the effect's own colour and
        // the controls sit at the bottom of the mini display, sized to the effect: a
        // 4-knob screen gets big knobs, a 21-knob one small ones.
        val n = info.params.size
        val dia = when {
            n <= 4 -> 34.dp
            n <= 6 -> 27.dp
            n <= 9 -> 21.dp
            else -> 16.dp
        }
        val gap = 5.dp
        val fit = (((136.dp - 10.dp) + gap) / (dia + gap)).toInt().coerceAtLeast(1)
        val shown = n.coerceAtMost(fit)
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(136.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(
                    Color(0xFF12161F),
                    accent.copy(alpha = 0.30f).compositeOver(Color(0xFF12161F)),
                ))),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / FxStyle.PWL
                val sceneH = FxStyle.PHL * s
                withTransform({
                    translate(0f, (size.height - sceneH) / 2f)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    drawFxScene(id, values, t, tm, scene)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            ) {
                repeat(shown) { i ->
                    FxMiniKnob(
                        value = values.getOrElse(i) { 0f },
                        accent = accent,
                        diameter = dia,
                        style = KnobStyle.forEffect(id),
                        gradientIndex = SKN[id] ?: id,
                        light = (SKN[id] ?: id) in MINI_LIGHT,
                    )
                }
                if (n > shown) {
                    Text("+${n - shown}", color = Color(0xFF8D96AB), fontSize = 9.sp,
                        fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** `SKN` from the reference, used to pick the mini knobs' cap material. */
private val SKN = mapOf(5 to 5, 6 to 6, 7 to 10, 8 to 11, 9 to 12, 10 to 7, 11 to 8, 12 to 13, 13 to 14, 14 to 9)

/** `LIGHT=[1,3,4,8,10,11,12,13]`: dark bodies, indexed by cap material `kk`. */
private val MINI_LIGHT = setOf(1, 3, 4, 8, 10, 11, 12, 13)

/** One pill button of the window header. */
@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(28.dp)
            .raised(9.dp, 4.dp, HeaderBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFFDDE3EE), fontSize = 11.sp,
            fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * The rack: header (count, add buttons, close) plus the row of effect cards.
 * [selectedSlot] is the slot whose full window is open, if any.
 */
@Composable
fun FxRackStrip(
    controller: FxController,
    selectedSlot: Int?,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDragUpdate: (Int, Float) -> Unit,
    onDragStop: (Int) -> Unit,
    onAddInstrument: () -> Unit,
    onAddEffect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ids = controller.effectIds
    val count = ids.count { it >= 0 }
    var draggingSlot by remember { mutableIntStateOf(-1) }
    var dragDist by remember { mutableFloatStateOf(0f) }
    // One clock for the whole rack: cards animated at ~15 images/s (doc 03),
    // previously one LaunchedEffect per card.
    var cardT by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { cardT = (it / 1_000_000_000f) }
            kotlinx.coroutines.delay(66)
        }
    }
    val threshold = 110f
    val overTrash = draggingSlot >= 0 && dragDist > threshold
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1F2635), Color(0xFF131925))))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (draggingSlot >= 0 && event.type == PointerEventType.Release) {
                            val slot = draggingSlot
                            val drop = dragDist > threshold
                            draggingSlot = -1
                            dragDist = 0f
                            onDragStop(slot)
                            if (drop) onRemove(slot)
                        }
                    }
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Effects", color = Color(0xFF8D96AB), fontSize = 12.sp,
                fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f))
            Text("$count / ${controller.slotCount} effects", color = Color(0xFF8D96AB),
                fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp))
            HeaderButton("+ Instrument", onAddInstrument)
            Spacer(Modifier.width(8.dp))
            HeaderButton("+ Effect", onAddEffect)
            Spacer(Modifier.width(8.dp))
            HeaderButton("✕", onClose)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ids.forEachIndexed { slot, id ->
                if (id >= 0) {
                    FxCard(
                        id = id,
                        selected = selectedSlot == slot,
                        lifted = draggingSlot == slot,
                        t = cardT,
                        onClick = { onSelect(slot) },
                        onLongClick = { onRemove(slot) },
                        onDragStart = {
                            draggingSlot = slot
                            dragDist = 0f
                        },
                        onDrag = { dy ->
                            dragDist = dy
                            onDragUpdate(slot, dy)
                        },
                        onDragStop = {
                            draggingSlot = -1
                            dragDist = 0f
                            onDragStop(slot)
                        },
                    )
                }
            }
        }
        if (overTrash) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE67A2216))
                        .border(1.5.dp, Color(0xFFFF7A5A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\u232B", color = Color(0xFFFFE9E0), fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("Drop here to remove", color = Color(0xFFFFE9E0), fontSize = 12.sp,
                        fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Add panel: two tabs (effects / instrument) over a grid of the registry entries.
 * Picking an instrument replaces the current one, as the preview does.
 */
@Composable
fun FxAddPanel(
    controller: FxController,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(if (controller.instrumentId >= 0) 1 else 0) }
    val entries = remember(tab) {
        if (tab == 1) FxRegistry.all.filter { it.kind != FxKind.EFFECT }
        else FxRegistry.all.filter { it.kind == FxKind.EFFECT }
    }
    Column(
        modifier = modifier
            .widthIn(min = 420.dp, max = 640.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF232A3A), Color(0xFF161C28))))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab == 1) "Add an instrument" else "Add an effect",
                color = Color(0xFFE6EAF3), fontSize = 14.sp,
                fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            HeaderButton("✕", onClose)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderButton("EFFECTS", { tab = 0 })
            HeaderButton("INSTRUMENT", { tab = 1 })
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { fx ->
                        Box(
                            Modifier
                                .width(180.dp)
                                .raised(10.dp, 5.dp, Brush.verticalGradient(
                                    listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF))))
                                .clickable { onPick(fx.id) }
                                .padding(10.dp),
                        ) {
                            Column {
                                Text(fx.name, color = FxStyle.accent(fx.id), fontSize = 12.sp,
                                    fontFamily = FxFonts.title(fx.id), maxLines = 1)
                                Text(fx.type, color = Color(0xFF8D96AB), fontSize = 9.sp,
                                    fontFamily = FxFonts.mono, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

````

### `FxOverlay.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/fx/FxOverlay.kt`
- **Taille :** 6711 octets · **lignes :** 142 · **sha256 (12) :** `286405d7fa6d`

````kotlin
package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.aetherkeys.fx.ui.FxControllerEngine
import com.aetherkeys.fx.ui.FxShellStore
import com.aetherkeys.fx.ui.FxWindowHost
import com.aetherkeys.fx.ui.fxModules
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxKind
import com.soundstage.mixer.fx.FxRegistry

/**
 * FX overlay for one mixer strip: the rack, the full window of the selected
 * effect, and the add panel. Only the open window animates at full cadence; the
 * cards keep their own reduced cadence.
 */
@Composable
fun FxOverlay(
    trackId: Int,
    soundfontName: String,
    program: Int,
    onClose: () -> Unit,
    onChooseSoundfont: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strip = stripForTrack(trackId)
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val controller = remember(strip) { FxController(strip, appContext) }
    // The new shell owns the rack, the effect window and the add sheet. The
    // controller stays the bridge to the native mixer and the persistence layer.
    val store = remember(strip) { FxShellStore(fxModules(), FxControllerEngine(controller), strip) }
    val warned = remember(strip) { mutableStateOf(emptySet<Int>()) }
    val inst = controller.instrumentId
    val warnId = if (inst >= 0 && FxSfWarning.checked(inst) && inst !in warned.value &&
        !FxSfWarning.matches(inst, soundfontName, program)) inst else -1

    // Restore once, then auto-save whenever the strip changes.
    androidx.compose.runtime.LaunchedEffect(strip) {
        // Storage is restored first: the controller pushes every effect and every
        // parameter to the native mixer, so the engine is the authority at this
        // point. Seeding the shell from the controller, rather than the reverse,
        // avoids replaying the whole strip back into the mixer.
        controller.restore()
        store.strip = strip
        store.seed(strip, controller.effectIds.toList(), controller.params.map { it.toList() },
            controller.bypassed.toList(), controller.instrumentId.takeIf { it >= 0 })
    }
    androidx.compose.runtime.LaunchedEffect(strip) {
        snapshotFlow { controller.effectIds.toList() to controller.bypassed.toList() }
            .collect { controller.save() }
    }
    // Knob drags fire many changes; save once the user stops for 500 ms.
    androidx.compose.runtime.LaunchedEffect(strip) {
        snapshotFlow { controller.params.map { it.toList() } }
            .collectLatest {
                kotlinx.coroutines.delay(500)
                controller.save()
            }
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC05070C))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            FxWindowHost(store = store, modifier = Modifier.fillMaxSize(), onClose = onClose)
        }

        if (warnId >= 0) {
            Box(
                Modifier.fillMaxWidth().padding(top = 64.dp, start = 14.dp, end = 14.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xF27A2216), Color(0xF24A120C))))
                        .border(1.dp, Color(0xFFFF7A5A), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(FxSfWarning.head(warnId), color = Color(0xFFFFE9E0), fontSize = 12.sp,
                        fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold)
                    Text(FxSfWarning.body(warnId, soundfontName), color = Color(0xFFFFE9E0),
                        fontSize = 11.sp, fontFamily = FxFonts.ui)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CHOOSE A SOUNDFONT", color = Color(0xFF4A120C), fontSize = 9.5.sp,
                            fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFFFE9E0))
                                .clickable { onChooseSoundfont() }
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                        Text("USE ANYWAY", color = Color(0xFFFFE9E0), fontSize = 9.5.sp,
                            fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0x1CFFFFFF))
                                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(9.dp))
                                .clickable { warned.value = warned.value + warnId }
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }

    }
}

````

### `FxSfWarning.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/ui/fx/FxSfWarning.kt`
- **Taille :** 1929 octets · **lignes :** 47 · **sha256 (12) :** `f61169487eed`

````kotlin
package com.soundstage.mixer.ui.fx

/**
 * Soundfont / instrument mismatch warning, from the preview's `FAMOK`, `KW` and
 * `SHN` tables. A shaper instrument only sounds right on a soundfont of its own
 * family; the window warns when the loaded one looks unrelated.
 */
object FxSfWarning {
    /** GM program ranges accepted per instrument (inclusive). */
    private val FAMOK: Map<Int, List<IntRange>> = mapOf(
        5 to listOf(0..7, 16..23),
        7 to listOf(64..71),
        8 to listOf(40..51),
        9 to listOf(24..31),
        12 to listOf(56..63),
    )

    private val KW: Map<Int, Regex> = mapOf(
        5 to Regex("piano|keys|clav|harpsichord", RegexOption.IGNORE_CASE),
        7 to Regex("sax|reed", RegexOption.IGNORE_CASE),
        8 to Regex("violin|viola|cello|string|orchestra|fiddle", RegexOption.IGNORE_CASE),
        9 to Regex("guitar|gtr|strat|nylon|banjo", RegexOption.IGNORE_CASE),
        12 to Regex("trumpet|trombone|horn|brass|tuba|cornet", RegexOption.IGNORE_CASE),
    )

    private val SHN: Map<Int, String> = mapOf(
        5 to "a piano",
        7 to "a saxophone",
        8 to "a violin or string section",
        9 to "a guitar",
        12 to "a brass instrument",
    )

    /** True when [effectId] is not a family-checked instrument (no warning applies). */
    fun checked(effectId: Int): Boolean = FAMOK.containsKey(effectId)

    fun matches(effectId: Int, soundfontName: String, program: Int): Boolean {
        val ranges = FAMOK[effectId] ?: return true
        if (KW[effectId]?.containsMatchIn(soundfontName) == true) return true
        return ranges.any { program in it }
    }

    fun head(effectId: Int): String = "This doesn't look like ${SHN[effectId] ?: "a matching instrument"}."

    fun body(effectId: Int, soundfontName: String): String =
        "The loaded soundfont is \u201C$soundfontName\u201D. Choose a matching soundfont for the best result."
}

````

### `FxController.kt`

- **Chemin :** `app/src/main/java/com/soundstage/mixer/fx/FxController.kt`
- **Taille :** 8094 octets · **lignes :** 198 · **sha256 (12) :** `b5b01529b893`
- État par piste + pont natif + persistance.

````kotlin
package com.soundstage.mixer.fx

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.soundstage.mixer.audio.NativeAudioBridge

/**
 * Mirrors the native FxMixer state for the Compose UI and pushes every change to
 * the JNI bridge. Strips follow FxMixer: 0..7 tracks, 8 drum channel, 9 tonic pad,
 * 10 drum-pad sampler, 15 master (see [FxRegistry.STRIP_MASTER]).
 *
 * All setters are control-thread calls and cheap enough for a slider drag.
 */
class FxController(private val strip: Int, private val context: Context? = null) {
    /** Instrument id on this strip, -1 when the soundfont plays as it is. */
    var instrumentId by mutableStateOf(-1)
        private set

    /** Slot -> effect id, -1 when empty. Sized for the strip's slot count. */
    val effectIds = mutableStateListOf<Int>()

    /** Slot -> bypass. */
    val bypassed = mutableStateListOf<Boolean>()

    /** Slot -> parameter values, normalized 0..1. */
    val params = mutableStateListOf<MutableList<Float>>()

    val slotCount: Int
        get() = if (strip == FxRegistry.STRIP_MASTER) FxRegistry.SLOTS_MASTER else FxRegistry.SLOTS_PER_TRACK

    init {
        repeat(slotCount) {
            effectIds.add(-1)
            bypassed.add(false)
            params.add(MutableList(FxRegistry.MAX_PARAMS) { 0f })
        }
    }

    private fun bridge() = if (NativeAudioBridge.isLibraryLoaded) NativeAudioBridge else null

    /**
     * Every native call goes through here. The bridge is a JNI boundary: a missing
     * library, a stale native build or a rejected argument raises, and an exception
     * thrown from a Compose event handler tears the window (and sometimes the process)
     * down with no way back. Swallowing it into [lastError] keeps the UI alive; the
     * shell surfaces [lastError] through its notice.
     */
    var lastError: String? = null
        private set

    private inline fun <T> safe(fallback: T, what: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        lastError = "$what failed: ${e.message ?: e::class.simpleName}"
        fallback
    }

    // ---- instrument ----

    fun setInstrument(id: Int): Boolean {
        val b = bridge() ?: return false
        if (id < 0) { instrumentId = -1; return true }
        if (!safe(false, "setInstrument") { b.setTrackInstrument(strip, id) }) return false
        instrumentId = id
        return true
    }

    fun setInstrumentParam(param: Int, norm: Float) {
        if (instrumentId < 0) return
        if (param !in 0 until FxRegistry.MAX_PARAMS) return
        paramsOfInstrument(param)[param] = norm
        safe(Unit, "setInstrumentParam") { bridge()?.setTrackInstrumentParam(strip, param, norm) }
    }

    private val instrumentParams = MutableList(FxRegistry.MAX_PARAMS) { 0f }
    private fun paramsOfInstrument(param: Int) = instrumentParams

    fun applyInstrumentPreset(preset: Int) {
        val b = bridge() ?: return
        if (instrumentId < 0) return
        if (!safe(false, "applyInstrumentPreset") { b.fxApplyInstrumentPreset(strip, preset) }) return
        FxRegistry.info(instrumentId).presets.getOrNull(preset)?.values?.forEachIndexed { i, v ->
            if (i < instrumentParams.size) instrumentParams[i] = v
        }
    }

    // ---- effects ----

    /** Installs [id] in [slot]. Returns false when the id is not valid for the slot. */
    fun setEffect(slot: Int, id: Int): Boolean {
        if (slot !in effectIds.indices) return false
        val b = bridge() ?: return false
        if (!safe(false, "setEffect") { b.setFxEffect(strip, slot, id) }) return false
        effectIds[slot] = id
        bypassed[slot] = false
        params[slot] = MutableList(FxRegistry.MAX_PARAMS) { i ->
            if (id >= 0) FxRegistry.info(id).params.getOrNull(i)?.default ?: 0f else 0f
        }
        safe(Unit, "fxCollectGarbage") { b.fxCollectGarbage() }
        return true
    }

    fun setEffectParam(slot: Int, param: Int, norm: Float) {
        if (slot !in effectIds.indices || effectIds[slot] < 0) return
        val p = params.getOrNull(slot) ?: return
        if (param !in p.indices) return
        p[param] = norm
        safe(Unit, "setEffectParam") { bridge()?.setFxEffectParam(strip, slot, param, norm) }
    }

    fun setBypass(slot: Int, bypass: Boolean) {
        if (slot !in bypassed.indices) return
        bypassed[slot] = bypass
        safe(Unit, "setBypass") { bridge()?.setFxEffectBypass(strip, slot, bypass) }
    }

    fun applyPreset(slot: Int, preset: Int): Boolean {
        val b = bridge() ?: return false
        if (slot !in effectIds.indices) return false
        if (!safe(false, "applyPreset") { b.applyFxPreset(strip, slot, preset) }) return false
        val id = effectIds.getOrNull(slot) ?: return true
        FxRegistry.info(id).presets.getOrNull(preset)?.values?.forEachIndexed { i, v ->
            if (i < params[slot].size) params[slot][i] = v
        }
        return true
    }

    // ---- strip ----

    fun setGain(lin: Float) = safe(Unit, "setGain") { bridge()?.setFxStripGain(strip, lin) }
    fun setPan(pan: Float) = safe(Unit, "setPan") { bridge()?.setFxStripPan(strip, pan) }
    fun setMute(mute: Boolean) = safe(Unit, "setMute") { bridge()?.setFxStripMute(strip, mute) }

    /** Peak level 0..1 for [channel] (0 = left, 1 = right). Safe to poll at 30 Hz. */
    fun peak(channel: Int): Float = safe(0f, "peak") { bridge()?.fxStripPeak(strip, channel) ?: 0f }

    /** Effect meter for the vu-meters; safe to poll at 30 Hz. */
    fun meter(slot: Int, index: Int): Float = safe(0f, "meter") { bridge()?.fxGetMeter(strip, slot, index) ?: 0f }

    /** Soundfont is silenced while a source instrument is selected. */
    fun soundfontPaused(): Boolean = safe(false, "soundfontPaused") { bridge()?.fxSoundfontPaused(strip) ?: false }

    // ---- persistence ----

    /** A/B pair, kept here so it survives a restore and is written back. */
    var abA: FloatArray? = null
    var abB: FloatArray? = null
    var abCur: Int = 0

    /** User-facing preset name per slot, for persistence. */
    val presetNames = mutableStateListOf<String?>().apply { repeat(slotCount) { add(null) } }

    private fun snapshot(): FxPersistence.Strip {
        val instPreset = if (instrumentId < 0) null
            else FxRegistry.info(instrumentId).presets
                .firstOrNull { it.values == instrumentParams }?.name
        return FxPersistence.Strip(
            instrumentId = instrumentId,
            instrumentParams = instrumentParams.toList(),
            instrumentPreset = instPreset,
            effects = effectIds.mapIndexed { slot, id ->
                if (id < 0) null
                else FxPersistence.Slot(id, presetNames[slot], params[slot].toList(), bypassed[slot])
            },
            abA = abA?.toList(),
            abB = abB?.toList(),
            abCur = abCur,
        )
    }

    /** Persists the current strip state. */
    fun save() {
        val ctx = context ?: return
        FxPersistence.save(ctx, strip, FxPersistence.toJson(strip, snapshot()))
    }

    /** Restores the strip from storage and pushes everything to the native mixer. */
    fun restore() {
        val ctx = context ?: return
        val saved = FxPersistence.fromJson(FxPersistence.load(ctx, strip), slotCount) ?: return
        if (saved.instrumentId >= 0) {
            setInstrument(saved.instrumentId)
            saved.instrumentParams.forEachIndexed { i, v -> setInstrumentParam(i, v) }
        }
        saved.effects.forEachIndexed { slot, entry ->
            if (entry != null && setEffect(slot, entry.id)) {
                entry.params.forEachIndexed { i, v -> if (i < params[slot].size) params[slot][i] = v }
                if (entry.bypass) setBypass(slot, true)
            }
        }
        abA = saved.abA?.toFloatArray()
        abB = saved.abB?.toFloatArray()
        abCur = saved.abCur
    }
}

````

---

## 8. Port autonome mono-fichier — `FxWindowBlocFull.kt`

Tout-en-un : registre, état, contrôleur, en-tête, rack, fenêtre ouverte, page
d'ajout, doublure soundfont, toast/undo, dispatcher de scènes et **les 15 scènes**.
Aucun appelant dans l'application — c'est un banc d'essai.

**Le plus rapide à appréhender** : un agent le lit d'un seul tenant sans suivre
d'imports à travers dix fichiers. Le code d'origine n'était pas compilable ; les
correctifs appliqués sont :

1. `com.aetherkeys.R` n'existe pas → utiliser `com.soundstage.mixer.R`.
2. Lire un `CompositionLocal` dans `remember { }` n'est pas composable.
3. Un compteur `mutableIntStateOf` écrit dans un lambda de dessin invalide le dessin
   → **boucle infinie**. Utiliser un `intArrayOf` (porteur muet).
4. Imports manquants : `ImageBitmap`, `Dp`, `IntSize`, `detectTapGestures`,
   `collectIsPressedAsState`, `mutableLongStateOf`, `drawWithContent`, `scale`,
   `shadow`, `asImageBitmap`, `LocalHapticFeedback`, `Animatable`, `animateFloatAsState`.

### Capturer hors appareil

Pas d'émulateur dans cet environnement : la capture passe par Roborazzi +
Robolectric. **Sans `-Proborazzi.test.record=true`, aucun PNG n'est écrit** (le test
passe quand même, en silence).

```bash
export JAVA_HOME=/workspace/jdk/jdk-21.0.13+9
export ANDROID_HOME=/workspace/android-sdk
./gradlew :app:recordRoborazziDebug --tests "com.soundstage.mixer.FxPreview2CaptureTest"
```

Trois pièges d'animation, vérifiés :

- Sous Robolectric, `withFrameMillis` **ne reçoit aucune frame** : une capture sans
  override est un `t=0` non reproductible. D'où un `fxPreview2TimeOverride`.
- Pour une **séquence** de frames, écrire l'override ne suffit pas : le frame clock
  est figé. Il faut `mainClock.advanceTimeBy(step)` **après** l'écriture, puis
  `waitForIdle()` avant `captureRoboImage`. Sans le tick, toutes les frames sont
  identiques (écart mesuré : 0,00).
- Mesurer l'animation dans la **région d'intérêt** : un écart moyen sur l'image
  entière vaut 0,3 pour un orbe qui tourne (la zone qui bouge est petite). Un
  `mean|Δ|` faible n'est pas une preuve d'immobilité.

### GIF sous 100 Ko

Une image 2340 × 1080 en 256 couleurs dépasse largement le budget. Ce qui marche :
recadrer la fenêtre, réduire la largeur (~300–460 px), **palette adaptative
partagée** (`quantize(colors=N, MEDIANCUT)` sur la première frame puis
`quantize(palette=pal)` sur les autres), tramage Floyd-Steinberg, `disposal=1`,
puis boucler la réduction jusqu'à passer sous 100 Ko.

### `FxWindowBlocFull.kt`

- **Chemin :** `app/src/main/java/com/aetherkeys/fxpreview2/FxWindowBlocFull.kt`
- **Taille :** 111658 octets · **lignes :** 2351 · **sha256 (12) :** `f59de253e050`
- Banc autonome, les 15 scènes incluses.

````kotlin
// ═══════════════════════════════════════════════════════════════════════════
//  Æther Keys · FxWindow — port of fx-window-preview.html
//
//  Produced by the parallel conversation (DeepSeek share w8e2b45n), dropped
//  into an ISOLATED package so it can be compiled and captured without
//  touching the app. Package `com.aetherkeys.fxpreview2` has no caller.
//
//  GEOMETRY (from the HTML, to the letter):
//    Window       : 825 x 343 , radius 20 , gradient #1F2635 -> #131925
//    Header       : 58 high   , padding left 58 , right 16
//    Content      : 779 x 263 at (20, 58) — the knob reference frame
//    Scene canvas : 825 x 343 at (0, 0)   — covers EVERYTHING, header included
//    Rail         : y in [214, 343] in the canvas
//    Close        : (12, 12) , 28 x 28
//    Preset chip  : TopEnd 11 , 176 x 36 (closed)
//    Dots         : BottomCenter , 8 dp up , 6x6 / 18x6 active
//
//  FIDELITY:
//    Space (0)     : 100 % — the `orb` function ported as-is
//    Echo (1)      : 75 %
//    Tone (2)      : 70 %
//    Grip (3)      : 65 %
//    Ceiling (4)   : 60 %
//    Others (5-14) : background + accent + pulse + knobs at the HTML coords + rail
//
//  SHADOWS: "isolated element + shadow underneath". Each knob draws a
//  translucent black radial gradient first, then the element. Replaces the
//  HTML `shadowBlur`.
// ═══════════════════════════════════════════════════════════════════════════
package com.aetherkeys.fxpreview2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.LinearGradient as ALinearGradient
import android.graphics.Paint as APaint
import android.graphics.RadialGradient as ARadialGradient
import android.graphics.RectF as ARectF
import android.graphics.Shader as AShader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

// ─── R (the repo's R lives in com.soundstage.mixer) ────────────────────────
private val R_ic_fx_space = com.soundstage.mixer.R.drawable.ic_fx_space
private val R_ic_fx_echo = com.soundstage.mixer.R.drawable.ic_fx_echo
private val R_ic_fx_tone = com.soundstage.mixer.R.drawable.ic_fx_tone
private val R_ic_fx_grip = com.soundstage.mixer.R.drawable.ic_fx_grip
private val R_ic_fx_ceiling = com.soundstage.mixer.R.drawable.ic_fx_ceiling
private val R_ic_fx_piano = com.soundstage.mixer.R.drawable.ic_fx_piano
private val R_ic_fx_organ = com.soundstage.mixer.R.drawable.ic_fx_organ
private val R_ic_fx_sax = com.soundstage.mixer.R.drawable.ic_fx_sax
private val R_ic_fx_strings = com.soundstage.mixer.R.drawable.ic_fx_strings
private val R_ic_fx_guitar = com.soundstage.mixer.R.drawable.ic_fx_guitar
private val R_ic_fx_amp = com.soundstage.mixer.R.drawable.ic_fx_amp
private val R_ic_fx_bass = com.soundstage.mixer.R.drawable.ic_fx_bass
private val R_ic_fx_brass = com.soundstage.mixer.R.drawable.ic_fx_brass
private val R_ic_fx_voices = com.soundstage.mixer.R.drawable.ic_fx_voices
private val R_ic_fx_synth = com.soundstage.mixer.R.drawable.ic_fx_synth

// ─── Geometry imposed by the HTML ─────────────────────────────────────────
/**
 * Capture hook: freezes the animation phase so a Robolectric screenshot is
 * reproducible, and lets a test walk the phase frame by frame. Null in the app
 * (the frame clock drives it). Backed by snapshot state so a write recomposes.
 */
private val fxPreview2Phase = mutableStateOf<Float?>(null)
var fxPreview2TimeOverride: Float?
    get() = fxPreview2Phase.value
    set(value) { fxPreview2Phase.value = value }

private const val WIN_W = 825f
private const val WIN_H = 343f
private const val HEADER_H = 58f
private const val CONTENT_X = 20f
private const val CONTENT_Y = 58f
private const val CONTENT_W = 779f
private const val CONTENT_H = 263f
private const val RAIL_H = 71f

// ─── Locked palette (cf. HTML) ────────────────────────────────────────────
private val BG_TOP = Color(0xFF1F2635)
private val BG_BOTTOM = Color(0xFF131925)
private val INK = Color(0xFFE6EAF3)
private val INK_DIM = Color(0xFF8D96AB)
private val CHIP_BG = Color(0x14FFFFFF)
private val CHIP_BORDER = Color(0x1AFFFFFF)

// ═══════════════════════════════════════════════════════════════════════════
//  REGISTRY — 15 definitions with knobs at the EXACT HTML coordinates
// ═══════════════════════════════════════════════════════════════════════════

enum class FxKind { EFFECT, SHAPER, SOURCE }
enum class AddTab { EFFECTS, INSTRUMENT }
enum class KnobStyle { ROUND, CHROME, FLUTE, KNURL, GLASS, FLAT }

data class FxParam(
    val name: String,
    val def: Float,
    val min: Float,
    val max: Float,
    val unit: String,
    val decimals: Int,
)

/** (paramIdx, x in the content frame, y in the content frame, size, style) */
data class KnobSlot(
    val paramIdx: Int,
    val x: Float,
    val y: Float,
    val size: Float = 54f,
    val style: KnobStyle = KnobStyle.ROUND,
)

data class FxDef(
    val id: Int,
    val title: String,
    val type: String,
    val kind: FxKind,
    val accent: Color,
    val titleFont: FontFamily,
    val icon: Int,
    val params: List<FxParam>,
    val presets: Map<String, FloatArray>,
    val knobs: List<KnobSlot>,
    val hasRail: Boolean = false,
    val railStyleId: Int = 0,
)

private fun P(vararg v: Float) = v

private val F_SORA = FontFamily(Font(com.soundstage.mixer.R.font.sora))
private val F_ORB = FontFamily(Font(com.soundstage.mixer.R.font.orbitron))
private val F_SPACE = FontFamily(Font(com.soundstage.mixer.R.font.space_grotesk))
private val F_JET = FontFamily(Font(com.soundstage.mixer.R.font.jetbrains_mono))
private val F_SHR = FontFamily(Font(com.soundstage.mixer.R.font.share_tech_mono))
private val F_UI = FontFamily(Font(com.soundstage.mixer.R.font.plus_jakarta_sans))

private val BAR_LABELS = listOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'")

/** Space knob ellipse: `389 + 285·cos(a)`, `114 + 80·sin(a)`, a = -90° + k·360/7. */
private fun spaceKnob(k: Int): KnobSlot {
    val a = (-90f + k * 360f / 7f) * PI.toFloat() / 180f
    return KnobSlot(k, 389f + 285f * cos(a), 114f + 80f * sin(a), 54f)
}

private val FX_DEFS: List<FxDef> = listOf(
    FxDef(
        0, "Æther Space", "Reverb", FxKind.EFFECT, Color(0xFFA99AD8), F_SPACE, R_ic_fx_space,
        listOf(
            FxParam("Size", .62f, 0f, 100f, " %", 0),
            FxParam("Decay", .7f, .4f, 8f, " s", 1),
            FxParam("Pre-delay", .2f, 0f, 120f, " ms", 0),
            FxParam("Damp", .45f, 1f, 12f, " kHz", 1),
            FxParam("Width", .8f, 0f, 100f, " %", 0),
            FxParam("Mix", .35f, 0f, 100f, " %", 0),
            FxParam("Diffuse", .5f, 0f, 100f, " %", 0),
        ),
        mapOf(
            "Hall" to P(.62f, .70f, .20f, .45f, .80f, .35f, .50f),
            "Plate" to P(.40f, .50f, .10f, .70f, .90f, .30f, .70f),
            "Room" to P(.25f, .30f, .05f, .50f, .60f, .25f, .30f),
            "Cathedral" to P(.95f, .95f, .35f, .30f, 1f, .45f, .80f),
            "Shimmer" to P(.70f, .85f, .25f, .20f, 1f, .50f, .90f),
            "Chamber" to P(.35f, .40f, .08f, .55f, .70f, .28f, .45f),
            "Bloom" to P(.80f, .80f, .30f, .35f, .95f, .42f, 1f),
            "Infinite" to P(1f, 1f, .40f, .25f, 1f, .60f, .95f),
        ),
        (0..6).map { spaceKnob(it) },
    ),
    FxDef(
        1, "Æther Echo", "Delay", FxKind.EFFECT, Color(0xFFD4B06A), F_JET, R_ic_fx_echo,
        listOf(
            FxParam("Time 1", .15f, 10f, 1000f, " ms", 0),
            FxParam("Level 1", .9f, 0f, 100f, " %", 0),
            FxParam("Time 2", .3f, 10f, 1000f, " ms", 0),
            FxParam("Level 2", .7f, 0f, 100f, " %", 0),
            FxParam("Time 3", .45f, 10f, 1000f, " ms", 0),
            FxParam("Level 3", .5f, 0f, 100f, " %", 0),
            FxParam("Time 4", .6f, 10f, 1000f, " ms", 0),
            FxParam("Level 4", .3f, 0f, 100f, " %", 0),
            FxParam("Feedback", .42f, 0f, 95f, " %", 0),
            FxParam("Tone", .6f, 1f, 12f, " kHz", 1),
            FxParam("Wow", .2f, 0f, 100f, " %", 0),
            FxParam("Mix", .28f, 0f, 100f, " %", 0),
        ),
        mapOf(
            "Dotted" to P(.15f, .9f, .3f, .7f, .45f, .5f, .6f, .3f, .42f, .6f, .2f, .28f),
            "Tape" to P(.12f, .9f, .26f, .8f, .42f, .65f, .6f, .5f, .6f, .35f, .6f, .3f),
            "Ping-pong" to P(.1f, .9f, .35f, .8f, .55f, .6f, .8f, .4f, .5f, .7f, .1f, .32f),
            "Slap" to P(.05f, .9f, .1f, .2f, .15f, .1f, .2f, .05f, .15f, .8f, 0f, .2f),
            "Ambient" to P(.2f, .7f, .4f, .6f, .62f, .5f, .85f, .4f, .7f, .45f, .35f, .4f),
            "Dub" to P(.3f, .9f, .5f, .75f, .7f, .55f, .9f, .4f, .78f, .3f, .5f, .35f),
            "Doubler" to P(.02f, .8f, .04f, .7f, .06f, .5f, .08f, .3f, .1f, .9f, .1f, .3f),
            "Pulse" to P(.1f, .9f, .2f, .9f, .3f, .9f, .4f, .9f, .55f, .7f, 0f, .3f),
        ),
        listOf(
            KnobSlot(8, 650f, 70f, 56f), KnobSlot(9, 735f, 70f, 56f),
            KnobSlot(10, 692f, 188f, 56f), KnobSlot(11, 95f, 205f, 52f),
        ),
    ),
    FxDef(
        2, "Prism Tone", "Equalizer", FxKind.EFFECT, Color(0xFF8FD0FF), F_ORB, R_ic_fx_tone,
        listOf(
            FxParam("Low freq", .15f, 20f, 20000f, " Hz", 0),
            FxParam("Low", .62f, -12f, 12f, " dB", 1),
            FxParam("Mid freq", .5f, 20f, 20000f, " Hz", 0),
            FxParam("Mid", .45f, -12f, 12f, " dB", 1),
            FxParam("Mid Q", .4f, .3f, 4f, "", 1),
            FxParam("High freq", .85f, 20f, 20000f, " Hz", 0),
            FxParam("High", .7f, -12f, 12f, " dB", 1),
            FxParam("Output", .5f, -12f, 12f, " dB", 1),
        ),
        mapOf(
            "Air" to P(.50f, .45f, .50f, .45f, .30f, .85f, .75f, .50f),
            "Warm" to P(.72f, .55f, .55f, .55f, .40f, .65f, .35f, .50f),
            "Vocal" to P(.40f, .62f, .58f, .62f, .50f, .62f, .62f, .50f),
            "Presence" to P(.45f, .50f, .62f, .72f, .60f, .72f, .68f, .50f),
            "Telephone" to P(.25f, .78f, .52f, .30f, .90f, .55f, .40f, .50f),
            "Smile" to P(.78f, .40f, .50f, .78f, .45f, .85f, .68f, .50f),
            "Flat" to P(.50f, .50f, .50f, .50f, .40f, .50f, .50f, .50f),
            "Black Box" to P(.62f, .46f, .52f, .70f, .45f, .60f, .52f, .52f),
        ),
        listOf(
            KnobSlot(1, 66f, 126f, 100f, KnobStyle.CHROME),
            KnobSlot(3, 330f, 214f, 60f),
            KnobSlot(6, 664f, 122f, 78f),
            KnobSlot(4, 430f, 216f, 44f),
        ),
    ),
    FxDef(
        3, "Iron Grip", "Compressor", FxKind.EFFECT, Color(0xFFB8C2D4), F_SHR, R_ic_fx_grip,
        listOf(
            FxParam("Threshold", .48f, -60f, 0f, " dB", 0),
            FxParam("Ratio", .35f, 1f, 20f, "", 0),
            FxParam("Attack", .25f, .1f, 100f, " ms", 1),
            FxParam("Release", .52f, 10f, 1000f, " ms", 0),
            FxParam("Knee", .4f, 0f, 24f, " dB", 0),
            FxParam("Makeup", .4f, 0f, 24f, " dB", 1),
        ),
        mapOf(
            "Glue" to P(.55f, .2f, .35f, .6f, .6f, .3f),
            "Gentle" to P(.4f, 0f, .25f, .5f, .7f, .2f),
            "Punch" to P(.5f, .6f, .6f, .3f, .3f, .5f),
            "Limit" to P(.7f, 1f, .05f, .2f, .1f, .6f),
            "Vocal Leveler" to P(.45f, .4f, .3f, .55f, .8f, .35f),
            "Drum Bus" to P(.5f, .4f, .2f, .25f, .3f, .45f),
            "Parallel" to P(.6f, .8f, .1f, .4f, .2f, .5f),
        ),
        listOf(
            KnobSlot(1, 628f, 78f, 78f), KnobSlot(4, 725f, 64f, 50f),
            KnobSlot(2, 598f, 200f, 54f), KnobSlot(3, 670f, 200f, 54f),
            KnobSlot(5, 742f, 200f, 54f),
        ),
    ),
    FxDef(
        4, "Solar Ceiling", "Maximizer", FxKind.EFFECT, Color(0xFFC9852C), F_SORA, R_ic_fx_ceiling,
        listOf(
            FxParam("Amount", .5f, 0f, 100f, " %", 0),
            FxParam("Ceiling", .92f, -6f, 0f, " dB", 1),
            FxParam("Release", .4f, 10f, 500f, " ms", 0),
            FxParam("Width", .3f, 0f, 100f, " %", 0),
            FxParam("Mix", 1f, 0f, 100f, " %", 0),
            FxParam("Output", .5f, -12f, 12f, " dB", 1),
        ),
        mapOf(
            "Transparent" to P(.3f, .95f, .6f, .2f, 1f, .5f),
            "Loud" to P(.75f, .9f, .35f, .4f, 1f, .55f),
            "Punch" to P(.6f, .9f, .2f, .2f, .9f, .55f),
            "Wide" to P(.5f, .92f, .5f, .8f, 1f, .5f),
            "Master Glue" to P(.4f, .93f, .55f, .5f, 1f, .5f),
            "Warm Tape" to P(.55f, .9f, .45f, .3f, .85f, .52f),
            "Radio" to P(.85f, .85f, .2f, .1f, 1f, .58f),
        ),
        listOf(
            KnobSlot(0, 125f, 128f, 136f), KnobSlot(1, 262f, 78f, 76f),
            KnobSlot(2, 262f, 196f, 50f), KnobSlot(3, 340f, 196f, 50f),
            KnobSlot(4, 340f, 70f, 50f), KnobSlot(5, 735f, 128f, 62f),
        ),
    ),
    FxDef(
        5, "Aether Piano", "Instrument", FxKind.SHAPER, Color(0xFFE8C98A), F_SORA, R_ic_fx_piano,
        listOf(
            FxParam("Felt", .8f, 0f, 100f, " %", 0),
            FxParam("Lid", .35f, 0f, 100f, " %", 0),
            FxParam("Pedal", .55f, 0f, 100f, " %", 0),
            FxParam("Room", .3f, 0f, 100f, " %", 0),
            FxParam("Width", .7f, 0f, 100f, " %", 0),
            FxParam("Tone", .4f, 0f, 100f, " %", 0),
            FxParam("Body", .6f, 0f, 100f, " %", 0),
            FxParam("Space", .25f, 0f, 100f, " %", 0),
            FxParam("Level", .8f, -24f, 6f, " dB", 1),
        ),
        mapOf(
            "Felt" to P(.8f, .35f, .55f, .3f, .7f, .4f, .6f, .25f, .8f),
            "Studio" to P(.4f, .6f, .35f, .15f, .6f, .55f, .4f, .1f, .8f),
            "Upright" to P(.5f, .4f, .4f, .2f, .4f, .45f, .7f, .2f, .8f),
            "Concert" to P(.55f, .7f, .7f, .6f, .9f, .5f, .5f, .6f, .8f),
            "Mellow" to P(.9f, .2f, .5f, .3f, .7f, .3f, .65f, .3f, .8f),
            "Bright" to P(.2f, .85f, .45f, .2f, .6f, .75f, .4f, .15f, .8f),
        ),
        listOf(KnobSlot(5, 150f, 252f, 42f), KnobSlot(6, 290f, 252f, 42f), KnobSlot(7, 430f, 252f, 42f)),
        hasRail = true, railStyleId = 5,
    ),
    FxDef(
        6, "Aether Organ", "Instrument", FxKind.SOURCE, Color(0xFFD9C08A), F_SORA, R_ic_fx_organ,
        (0..8).map { FxParam("Upper ${BAR_LABELS[it]}", listOf(1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f)[it], 0f, 8f, "", 0) } +
            (0..8).map { FxParam("Lower ${BAR_LABELS[it]}", listOf(1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)[it], 0f, 8f, "", 0) } +
            listOf(
                FxParam("Perc on", 0f, 0f, 1f, "", 0),
                FxParam("Perc soft", 0f, 0f, 1f, "", 0),
                FxParam("Perc fast", 0f, 0f, 1f, "", 0),
                FxParam("Perc 3rd", 0f, 0f, 1f, "", 0),
                FxParam("Vibrato", .6f, 0f, 5f, "", 0),
                FxParam("Vib upper", 1f, 0f, 1f, "", 0),
                FxParam("Vib lower", 1f, 0f, 1f, "", 0),
                FxParam("Rotary", 1f, 0f, 2f, "", 0),
                FxParam("Click", .3f, 0f, 100f, " %", 0),
                FxParam("Tone", .55f, 0f, 100f, " %", 0),
                FxParam("Drive", .4f, 0f, 100f, " %", 0),
                FxParam("Space", .25f, 0f, 100f, " %", 0),
                FxParam("Level", .8f, -24f, 6f, " dB", 1),
            ),
        mapOf(
            "Gospel" to FloatArray(31) { .5f }, "Full" to FloatArray(31) { .5f },
            "Jazz" to FloatArray(31) { .5f }, "Rock" to FloatArray(31) { .5f },
            "Balanced" to FloatArray(31) { .5f }, "Whisper" to FloatArray(31) { .5f },
        ),
        listOf(
            KnobSlot(26, 742f, 106f, 40f), KnobSlot(27, 150f, 252f, 42f),
            KnobSlot(28, 290f, 252f, 42f), KnobSlot(29, 430f, 252f, 42f),
        ),
        hasRail = true, railStyleId = 6,
    ),
    FxDef(
        7, "Aether Sax", "Instrument", FxKind.SHAPER, Color(0xFFE0A45A), F_SORA, R_ic_fx_sax,
        listOf(
            FxParam("Breath", .55f, 0f, 100f, " %", 0),
            FxParam("Reed", .5f, 0f, 100f, " %", 0),
            FxParam("Growl", .15f, 0f, 100f, " %", 0),
            FxParam("Vibrato", .35f, 0f, 100f, " %", 0),
            FxParam("Rate", .4f, 0f, 100f, " %", 0),
            FxParam("Tone", .5f, 0f, 100f, " %", 0),
            FxParam("Body", .6f, 0f, 100f, " %", 0),
            FxParam("Space", .3f, 0f, 100f, " %", 0),
            FxParam("Level", .8f, -24f, 6f, " dB", 1),
        ),
        mapOf(
            "Smooth" to FloatArray(9) { .5f }, "Breathy" to FloatArray(9) { .5f },
            "Growl" to FloatArray(9) { .5f }, "Club" to FloatArray(9) { .5f },
            "Bright" to FloatArray(9) { .5f }, "Subtone" to FloatArray(9) { .5f },
        ),
        // 5 on the tube + 3 on the rail
        listOf(
            KnobSlot(0, 180f, 80f, 40f), KnobSlot(1, 250f, 100f, 40f),
            KnobSlot(2, 320f, 120f, 40f), KnobSlot(3, 390f, 140f, 40f),
            KnobSlot(4, 460f, 160f, 40f), KnobSlot(5, 150f, 252f, 42f),
            KnobSlot(6, 290f, 252f, 42f), KnobSlot(7, 430f, 252f, 42f),
        ),
        hasRail = true, railStyleId = 7,
    ),
    FxDef(
        8, "Aether Strings", "Instrument", FxKind.SHAPER, Color(0xFFE8A43C), F_SPACE, R_ic_fx_strings,
        listOf(
            FxParam("Pressure", .5f, 0f, 100f, " %", 0),
            FxParam("Speed", .5f, 0f, 100f, " %", 0),
            FxParam("Motion", .35f, 0f, 100f, " %", 0),
            FxParam("Morph", .1f, 0f, 100f, " %", 0),
            FxParam("Time", .5f, 0f, 100f, " %", 0),
            FxParam("Color", .1f, 0f, 100f, " %", 0),
            FxParam("Tone", .5f, 0f, 100f, " %", 0),
            FxParam("Body", .6f, 0f, 100f, " %", 0),
            FxParam("Space", .3f, 0f, 100f, " %", 0),
            FxParam("Level", .8f, -24f, 6f, " dB", 1),
        ),
        mapOf(
            "Solo" to FloatArray(10) { .5f }, "Chamber" to FloatArray(10) { .5f },
            "Orchestra" to FloatArray(10) { .5f }, "Cinematic" to FloatArray(10) { .5f },
            "Warm" to FloatArray(10) { .5f }, "Bright" to FloatArray(10) { .5f },
        ),
        listOf(
            KnobSlot(5, 96f, 176f, 40f), KnobSlot(4, 212f, 176f, 40f),
            KnobSlot(3, 389f, 168f, 62f), KnobSlot(2, 566f, 176f, 40f),
            KnobSlot(0, 680f, 176f, 40f), KnobSlot(1, 756f, 176f, 40f),
            KnobSlot(6, 150f, 252f, 42f), KnobSlot(7, 290f, 252f, 42f),
            KnobSlot(8, 430f, 252f, 42f),
        ),
        hasRail = true, railStyleId = 8,
    ),
    FxDef(
        9, "Aether Guitar", "Instrument", FxKind.SHAPER, Color(0xFFE08A4A), F_SPACE, R_ic_fx_guitar,
        listOf(
            FxParam("Body", 0f, 0f, 3f, "", 0),
            FxParam("Pick", .5f, 0f, 100f, " %", 0),
            FxParam("Strum", .4f, 0f, 100f, " %", 0),
            FxParam("Mute", .1f, 0f, 100f, " %", 0),
            FxParam("Bend", .2f, 0f, 100f, " %", 0),
            FxParam("Pickup", .5f, 0f, 100f, " %", 0),
            FxParam("Finish", .33f, 0f, 3f, "", 0),
            FxParam("Tone", .5f, 0f, 100f, " %", 0),
            FxParam("Resonance", .7f, 0f, 100f, " %", 0),
            FxParam("Space", .25f, 0f, 100f, " %", 0),
            FxParam("Level", .8f, -24f, 6f, " dB", 1),
        ),
        mapOf(
            "Acoustic" to FloatArray(11) { .5f }, "Nylon" to FloatArray(11) { .5f },
            "Clean" to FloatArray(11) { .5f }, "Crunch" to FloatArray(11) { .5f },
            "Lead" to FloatArray(11) { .5f }, "Ambient" to FloatArray(11) { .5f },
        ),
        listOf(
            KnobSlot(0, 40f, 22f, 15f), KnobSlot(1, 72f, 22f, 15f), KnobSlot(2, 104f, 22f, 15f),
            KnobSlot(3, 40f, 156f, 15f), KnobSlot(4, 72f, 156f, 15f), KnobSlot(5, 104f, 156f, 15f),
            KnobSlot(7, 150f, 252f, 42f), KnobSlot(8, 290f, 252f, 42f), KnobSlot(9, 430f, 252f, 42f),
        ),
        hasRail = true, railStyleId = 9,
    ),
    FxDef(
        10, "Aether Amp", "Amp", FxKind.EFFECT, Color(0xFFFF9A3C), F_ORB, R_ic_fx_amp,
        listOf(
            FxParam("Gain", .3f, 0f, 100f, " %", 0),
            FxParam("Bass", .5f, 0f, 100f, " %", 0),
            FxParam("Mid", .5f, 0f, 100f, " %", 0),
            FxParam("Treble", .6f, 0f, 100f, " %", 0),
            FxParam("Presence", .5f, 0f, 100f, " %", 0),
            FxParam("Master", .6f, 0f, 100f, " %", 0),
            FxParam("Channel", 0f, 0f, 2f, "", 0),
            FxParam("Mic pos", .4f, 0f, 100f, " %", 0),
            FxParam("Mic dist", .4f, 0f, 100f, " %", 0),
            FxParam("Boost", 0f, 0f, 1f, "", 0),
            FxParam("Tremolo", 0f, 0f, 1f, "", 0),
            FxParam("Level", .8f, -24f, 6f, " dB", 1),
        ),
        mapOf(
            "Clean" to FloatArray(12) { .5f }, "Crunch" to FloatArray(12) { .5f },
            "Lead" to FloatArray(12) { .5f }, "Blues" to FloatArray(12) { .5f },
            "Jazz" to FloatArray(12) { .5f }, "Metal" to FloatArray(12) { .5f },
        ),
        listOf(
            KnobSlot(0, 70f, 86f, 46f), KnobSlot(1, 150f, 86f, 46f), KnobSlot(2, 230f, 86f, 46f),
            KnobSlot(3, 310f, 86f, 46f), KnobSlot(4, 390f, 86f, 46f), KnobSlot(5, 470f, 86f, 46f),
            KnobSlot(11, 730f, 86f, 46f),
        ),
    ),
    FxDef(
        11, "Aether Bass", "Amp", FxKind.EFFECT, Color(0xFFF0C060), F_SORA, R_ic_fx_bass,
        listOf(
            FxParam("Drive", .25f, 0f, 100f, " %", 0),
            FxParam("Comp", .4f, 0f, 100f, " %", 0),
            FxParam("Blend", .6f, 0f, 100f, " %", 0),
            FxParam("63 Hz", .55f, 0f, 100f, " %", 0),
            FxParam("250 Hz", .5f, 0f, 100f, " %", 0),
            FxParam("1 kHz", .5f, 0f, 100f, " %", 0),
            FxParam("3 kHz", .45f, 0f, 100f, " %", 0),
            FxParam("8 kHz", .4f, 0f, 100f, " %", 0),
            FxParam("Cab", .5f, 0f, 2f, "", 0),
            FxParam("Level", .8f, -24f, 6f, " dB", 1),
        ),
        mapOf(
            "Motown" to FloatArray(10) { .5f }, "Rock" to FloatArray(10) { .5f },
            "Slap" to FloatArray(10) { .5f }, "Fuzz" to FloatArray(10) { .5f },
            "Dub" to FloatArray(10) { .5f }, "Clean DI" to FloatArray(10) { .5f },
        ),
        listOf(
            KnobSlot(0, 70f, 206f, 44f, KnobStyle.CHROME),
            KnobSlot(1, 140f, 206f, 44f, KnobStyle.CHROME),
            KnobSlot(2, 210f, 206f, 44f, KnobStyle.CHROME),
        ),
    ),
    FxDef(
        12, "Aether Brass", "Instrument", FxKind.SHAPER, Color(0xFFE8C060), F_SPACE, R_ic_fx_brass,
        listOf(
            FxParam("Lips", .5f, 0f, 100f, " %", 0),
            FxParam("Bite", .45f, 0f, 100f, " %", 0),
            FxParam("Vibrato", .3f, 0f, 100f, " %", 0),
            FxParam("Section", .25f, 0f, 4f, "", 0),
            FxParam("Mute", 0f, 0f, 4f, "", 0),
            FxParam("Tone", .5f, 0f, 100f, " %", 0),
            FxParam("Body", .6f, 0f, 100f, " %", 0),
            FxParam("Space", .3f, 0f, 100f, " %", 0),
            FxParam("Level", .8f, -24f, 6f, " dB", 1),
        ),
        mapOf(
            "Open" to FloatArray(9) { .5f }, "Muted Jazz" to FloatArray(9) { .5f },
            "Section Stab" to FloatArray(9) { .5f }, "Fanfare" to FloatArray(9) { .5f },
            "Soft" to FloatArray(9) { .5f }, "Harmon" to FloatArray(9) { .5f },
        ),
        listOf(
            KnobSlot(0, 230f, 62f, 16f, KnobStyle.CHROME),
            KnobSlot(1, 266f, 62f, 16f, KnobStyle.CHROME),
            KnobSlot(2, 302f, 62f, 16f, KnobStyle.CHROME),
            KnobSlot(5, 150f, 252f, 42f), KnobSlot(6, 290f, 252f, 42f), KnobSlot(7, 430f, 252f, 42f),
        ),
        hasRail = true, railStyleId = 12,
    ),
    FxDef(
        13, "Aether Voices", "Effect", FxKind.EFFECT, Color(0xFFC9B8FF), F_SPACE, R_ic_fx_voices,
        listOf(
            FxParam("Vowel X", .5f, 0f, 100f, " %", 0),
            FxParam("Vowel Y", .3f, 0f, 100f, " %", 0),
            FxParam("Breath", .3f, 0f, 100f, " %", 0),
            FxParam("Choir", .5f, 0f, 100f, " %", 0),
            FxParam("Vibrato", .35f, 0f, 100f, " %", 0),
            FxParam("Tone", .5f, 0f, 100f, " %", 0),
            FxParam("Body", .5f, 0f, 100f, " %", 0),
            FxParam("Space", .45f, 0f, 100f, " %", 0),
            FxParam("Level", .8f, -24f, 6f, " dB", 1),
        ),
        mapOf(
            "Ah" to FloatArray(9) { .5f }, "Ooh" to FloatArray(9) { .5f },
            "Eeh" to FloatArray(9) { .5f }, "Ensemble" to FloatArray(9) { .5f },
            "Whisper" to FloatArray(9) { .5f }, "Angels" to FloatArray(9) { .5f },
        ),
        listOf(
            KnobSlot(2, 70f, 56f, 44f, KnobStyle.GLASS),
            KnobSlot(3, 70f, 150f, 44f, KnobStyle.GLASS),
            KnobSlot(4, 700f, 60f, 44f, KnobStyle.GLASS),
            KnobSlot(5, 150f, 252f, 42f, KnobStyle.GLASS),
            KnobSlot(6, 290f, 252f, 42f, KnobStyle.GLASS),
            KnobSlot(7, 430f, 252f, 42f, KnobStyle.GLASS),
        ),
        hasRail = true, railStyleId = 13,
    ),
    FxDef(
        14, "Aether Synth", "Instrument", FxKind.SOURCE, Color(0xFF7AD0FF), F_ORB, R_ic_fx_synth,
        listOf("Wave A", "Wave B", "Detune", "Sub", "Noise", "Unison", "Spread", "Cutoff", "Resonance", "Drive", "Env amt")
            .map { FxParam(it, .4f, 0f, 100f, " %", 0) } +
            listOf(FxParam("Filter", 0f, 0f, 3f, "", 0)) +
            listOf(
                "Attack", "Decay", "Sustain", "Release",
                "LFO1 rate", "LFO1 depth", "LFO1 shape", "LFO1 dest",
                "LFO2 rate", "LFO2 depth", "Macro 1", "Macro 2", "Macro 3", "Macro 4",
            ).map { FxParam(it, .4f, 0f, 100f, " %", 0) } +
            listOf(FxParam("Mode", 0f, 0f, 2f, "", 0)) +
            listOf("Glide", "Voices", "Bend", "Tune").map { FxParam(it, .4f, 0f, 100f, " %", 0) } +
            listOf(FxParam("Level", .8f, -24f, 6f, " dB", 1)),
        mapOf(
            "Pad" to FloatArray(32) { .5f }, "Lead" to FloatArray(32) { .5f },
            "Bass" to FloatArray(32) { .5f }, "Pluck" to FloatArray(32) { .5f },
            "Keys" to FloatArray(32) { .5f }, "FX" to FloatArray(32) { .5f },
        ),
        listOf(
            KnobSlot(7, 120f, 80f, 44f, KnobStyle.FLAT), KnobSlot(8, 200f, 80f, 44f, KnobStyle.FLAT),
            KnobSlot(0, 120f, 160f, 44f, KnobStyle.FLAT), KnobSlot(1, 200f, 160f, 44f, KnobStyle.FLAT),
            KnobSlot(22, 150f, 252f, 42f, KnobStyle.FLAT), KnobSlot(23, 290f, 252f, 42f, KnobStyle.FLAT),
            KnobSlot(24, 430f, 252f, 42f, KnobStyle.FLAT),
        ),
        hasRail = true, railStyleId = 14,
    ),
)

private val FX_BY_ID = FX_DEFS.associateBy { it.id }
private fun FxDef.isInstrument() = kind == FxKind.SHAPER || kind == FxKind.SOURCE
private fun FxDef.isEffect() = kind == FxKind.EFFECT

// ═══════════════════════════════════════════════════════════════════════════
//  STATE + CONTROLLER
// ═══════════════════════════════════════════════════════════════════════════

data class UndoEntry(val id: Int, val at: Int, val params: FloatArray, val preset: String)

class FxUiState(val track: Int = 0) {
    val act = mutableStateListOf<Int>()
    val values = mutableStateMapOf<Int, FloatArray>()
    val bypass = mutableStateMapOf<Int, Boolean>()
    val presetName = mutableStateMapOf<Int, String>()

    var openedIdx by mutableStateOf(-1)
    var addOpen by mutableStateOf(false)
    var addTab by mutableStateOf(AddTab.EFFECTS)
    var addFilter by mutableStateOf("All")
    var addQuery by mutableStateOf("")
    var soundfont by mutableStateOf("Grand Piano")
    var sfOpen by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var undo by mutableStateOf<UndoEntry?>(null)

    fun ensure(def: FxDef) {
        if (!values.containsKey(def.id)) {
            values[def.id] = def.params.map { it.def }.toFloatArray()
            presetName[def.id] = def.presets.keys.firstOrNull() ?: "Default"
            bypass[def.id] = false
        }
    }

    fun effectCount() = act.count { FX_BY_ID[it]?.isEffect() == true }
    fun instrument() = act.firstOrNull { FX_BY_ID[it]?.isInstrument() == true }
}

class FxController(val s: FxUiState, private val bridge: FxBridge? = null) {

    fun openAdd(t: AddTab) {
        s.addTab = t; s.addOpen = true; s.addFilter = "All"; s.addQuery = ""
    }

    fun closeAdd() { s.addOpen = false }

    fun add(id: Int) {
        val def = FX_BY_ID[id] ?: return
        if (s.act.contains(id)) return
        s.ensure(def)
        if (def.isInstrument()) {
            s.instrument()?.let { cur ->
                remove(cur, silent = true)
                toast("${def.title} replaces ${FX_BY_ID[cur]?.title}")
            }
            s.act.add(0, id)
            bridge?.onInstrument(id, s.track)
        } else {
            if (s.effectCount() >= 6) return
            s.act.add(id)
            bridge?.onEffectAdded(id, s.track)
        }
        bridge?.onParamBatch(id, s.values[id]!!)
    }

    fun remove(id: Int, silent: Boolean = false) {
        val idx = s.act.indexOf(id); if (idx < 0) return
        if (!silent) s.undo = UndoEntry(id, idx, s.values[id]?.copyOf() ?: FloatArray(0), s.presetName[id] ?: "")
        s.act.removeAt(idx)
        bridge?.onEffectRemoved(id, s.track)
        if (!silent) toast("${FX_BY_ID[id]?.title} removed")
    }

    fun undoRemove() {
        val u = s.undo ?: return
        s.undo = null
        s.values[u.id] = u.params
        s.presetName[u.id] = u.preset
        s.act.add(u.at.coerceAtMost(s.act.size), u.id)
        bridge?.onEffectAdded(u.id, s.track)
        bridge?.onParamBatch(u.id, u.params)
    }

    // Copy-on-write: mutating the FloatArray in place is invisible to Compose.
    fun setParam(id: Int, idx: Int, v: Float) {
        val def = FX_BY_ID[id] ?: return
        if (idx !in def.params.indices) return
        val cur = s.values[id] ?: return
        val next = cur.copyOf()
        next[idx] = v.coerceIn(0f, 1f)
        s.values[id] = next
        bridge?.onParam(id, idx, next[idx])
    }

    fun applyPreset(id: Int, name: String) {
        val def = FX_BY_ID[id] ?: return
        val p = def.presets[name] ?: return
        val cur = s.values[id] ?: return
        val next = cur.copyOf()
        for (i in p.indices) if (i < next.size) next[i] = p[i]
        s.values[id] = next
        s.presetName[id] = name
        bridge?.onParamBatch(id, next)
    }

    fun toggleBypass(id: Int) {
        val b = !(s.bypass[id] ?: false)
        s.bypass[id] = b
        bridge?.onBypass(id, b)
    }

    fun openCard(idx: Int) { if (idx in s.act.indices) s.openedIdx = idx }
    fun closeCard() { s.openedIdx = -1 }
    fun toast(msg: String) { s.toast = msg }

    fun surprise(id: Int) {
        val cur = s.values[id] ?: return
        val next = cur.copyOf()
        for (i in 0 until next.size - 1) next[i] = (0.15f + kotlin.random.Random.nextFloat() * .7f).coerceIn(0f, 1f)
        s.values[id] = next
        bridge?.onParamBatch(id, next)
        toast("Surprise: new sound")
    }
}

interface FxBridge {
    fun onEffectAdded(id: Int, track: Int)
    fun onEffectRemoved(id: Int, track: Int)
    fun onInstrument(id: Int, track: Int)
    fun onParam(id: Int, paramIdx: Int, value: Float)
    fun onParamBatch(id: Int, values: FloatArray)
    fun onBypass(id: Int, bypass: Boolean)
}

// ═══════════════════════════════════════════════════════════════════════════
//  ENTRY
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun FxWindow(s: FxUiState, c: FxController, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF0E1420))) {
        Column(Modifier.fillMaxSize()) {
            FxHeader(s, c)
            FxRack(s, c, Modifier.weight(1f))
        }
        if (s.openedIdx >= 0 && s.openedIdx < s.act.size) FxOpenWindow(s, c)
        if (s.addOpen) FxAddOverlay(s, c)
        if (s.sfOpen) FxSoundfontWindow(s, c)
        FxToastAndUndo(s, c)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  HEADER
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FxHeader(s: FxUiState, c: FxController) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .08f)),
            contentAlignment = Alignment.Center,
        ) { Text("⌄", color = INK_DIM) }
        Spacer(Modifier.width(10.dp))
        Text("EFFECTS", color = INK_DIM, fontSize = 12.sp, fontWeight = FontWeight(800), letterSpacing = 1.9.sp)
        Spacer(Modifier.weight(1f))
        Text("${s.effectCount()} / 6 effects", color = INK_DIM, fontSize = 10.sp)
        Spacer(Modifier.width(10.dp))
        FxChip("Soundfont · ${s.soundfont} ▾") { s.sfOpen = true }
        Spacer(Modifier.width(6.dp))
        FxChip("+ Instrument") { c.openAdd(AddTab.INSTRUMENT) }
        Spacer(Modifier.width(6.dp))
        FxChip("+ Effect") { c.openAdd(AddTab.EFFECTS) }
    }
}

@Composable
private fun FxChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(CHIP_BG)
            .border(1.dp, CHIP_BORDER, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = INK, fontSize = 11.sp, fontWeight = FontWeight(700)) }
}

// ═══════════════════════════════════════════════════════════════════════════
//  RACK — cards with a mini canvas
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FxRack(s: FxUiState, c: FxController, modifier: Modifier) {
    if (s.act.isEmpty()) {
        Row(modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { k -> FxEmptySlot(dim = k > 0) { c.openAdd(AddTab.EFFECTS) } }
        }
    } else {
        LazyRow(modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(s.act.toList(), key = { it }) { id -> FxCard(s, c, id) }
            item { FxPlusSlot { c.openAdd(AddTab.EFFECTS) } }
        }
    }
}

@Composable
private fun FxEmptySlot(dim: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !dim) 0.97f else 1f,
        animationSpec = tween(90),
        label = "emptySlotPress",
    )
    Box(
        Modifier.size(width = 158.dp, height = 240.dp)
            .scale(pressScale)
            .shadow(
                elevation = if (dim) 2.dp else 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.7f),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1A2130), Color(0xFF0D111E))))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (dim) 0.04f else 0.09f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = !dim, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!dim) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(52.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.6f),
                            spotColor = Color.Black.copy(alpha = 0.8f),
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF4A5268), Color(0xFF232838), Color(0xFF0E1220)),
                                center = Offset(18f, 16f),
                                radius = 52f,
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("＋", color = Color(0xFFD6DEEA), fontSize = 26.sp, fontWeight = FontWeight(300)) }
                Spacer(Modifier.height(14.dp))
                Text(
                    "INSERT AN EFFECT", color = Color(0xFF6D778C), fontSize = 10.sp,
                    fontWeight = FontWeight(700), letterSpacing = 1.4.sp,
                )
            }
        }
    }
}

@Composable
private fun FxPlusSlot(onClick: () -> Unit) {
    Box(
        Modifier.size(74.dp, 240.dp).clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text("＋", color = Color(0xFF6D778C), fontSize = 28.sp) }
}

@Composable
private fun FxCard(s: FxUiState, c: FxController, id: Int) {
    val def = FX_BY_ID[id] ?: return
    s.ensure(def)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Bounce on appearance: starts small and low, overshoots on the way up.
    val appearScale = remember { Animatable(0.6f) }
    val appearAlpha = remember { Animatable(0f) }
    val appearY = remember { Animatable(24f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { appearScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow)) }
            launch { appearAlpha.animateTo(1f, tween(180)) }
            launch { appearY.animateTo(0f, spring(dampingRatio = 0.35f)) }
        }
    }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(90),
        label = "cardPress",
    )

    Box(
        Modifier.offset(y = appearY.value.dp)
            .size(width = 158.dp, height = 240.dp)
            .scale(appearScale.value * pressScale)
            .alpha(appearAlpha.value)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.85f),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF353C4B), Color(0xFF212734))))
            // Inset highlight along the top edge: the reflection that reads as relief.
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 26.dp.toPx(),
                    ),
                    size = Size(size.width, 26.dp.toPx()),
                )
            }
            .border(
                width = if (def.isInstrument()) 1.5.dp else 1.dp,
                color = if (def.isInstrument()) Color(0xFFE8C98A).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interaction, indication = null) { c.openCard(s.act.indexOf(id)) }
            .padding(10.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(def.icon), null,
                    Modifier.size(13.dp).alpha(0.85f),
                    colorFilter = ColorFilter.tint(Color(0xFFAAB3C8)),
                )
                Spacer(Modifier.width(6.dp))
                Text(def.type.uppercase(), color = INK_DIM, fontSize = 8.sp, fontWeight = FontWeight(700), letterSpacing = 1.3.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(def.title, color = Color(0xFFDFE4EE), fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF12161F))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)),
            ) { FxSceneCanvas(def, s.values[id] ?: FloatArray(0), mini = true) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (!(s.bypass[id] ?: false)) Color(0xFFC9D2E6) else Color(0xFF2A3040)),
                )
                Spacer(Modifier.width(6.dp))
                Text(s.presetName[id] ?: "", color = INK_DIM, fontSize = 9.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  OPEN WINDOW — faithful structure: full-frame canvas + overlays
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FxOpenWindow(s: FxUiState, c: FxController) {
    val pager = rememberPagerState(initialPage = s.openedIdx.coerceIn(0, s.act.lastIndex)) { s.act.size }
    LaunchedEffect(pager.currentPage) { s.openedIdx = pager.currentPage }
    Box(
        Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(BG_TOP, BG_BOTTOM)))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(20.dp)),
    ) {
        HorizontalPager(state = pager, Modifier.fillMaxSize()) { page ->
            FxEffectPage(s, c, s.act[page])
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            s.act.forEachIndexed { i, _ ->
                Box(
                    Modifier.size(if (i == pager.currentPage) 18.dp else 6.dp, 6.dp).clip(CircleShape)
                        .background(
                            if (i == pager.currentPage) Color.White.copy(alpha = .9f)
                            else Color.White.copy(alpha = .25f),
                        ),
                )
            }
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(12.dp).size(28.dp).clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = .08f)).clickable { c.closeCard() },
            contentAlignment = Alignment.Center,
        ) { Text("⌄", color = INK) }
    }
}

@Composable
private fun FxEffectPage(s: FxUiState, c: FxController, id: Int) {
    val def = FX_BY_ID[id] ?: return
    s.ensure(def)
    val v = s.values[id] ?: FloatArray(0)
    val by = s.bypass[id] ?: false
    val density = LocalDensity.current
    rememberTextMeasurer()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scaleX = constraints.maxWidth / WIN_W
        val scaleY = constraints.maxHeight / WIN_H

        // 1) SCENE, full frame (behind everything)
        FxSceneCanvas(def, v, mini = false)

        // 2) KNOBS overlay (HTML content coords -> window)
        def.knobs.forEach { slot ->
            val xInWin = (CONTENT_X + slot.x) * scaleX
            val yInWin = (CONTENT_Y + slot.y) * scaleY
            val sizePx = slot.size * min(scaleX, scaleY)
            if (slot.paramIdx < def.params.size) {
                FxKnob(
                    value = v[slot.paramIdx],
                    label = def.params[slot.paramIdx].name,
                    display = formatParam(def.params[slot.paramIdx], v[slot.paramIdx]),
                    accent = def.accent,
                    style = slot.style,
                    sizeDp = with(density) { sizePx.toDp() },
                    onValueChange = { c.setParam(id, slot.paramIdx, it) },
                    modifier = Modifier
                        .offset(
                            with(density) { (xInWin / scaleX).dp },
                            with(density) { (yInWin / scaleY).dp },
                        )
                        .offset { IntOffset((sizePx / -2f).toInt(), (sizePx / -2f).toInt()) },
                )
            }
        }

        // 3) RAIL (instruments only)
        if (def.hasRail) {
            val railH = RAIL_H * scaleY
            FxRail(
                def = def,
                value = v.lastOrNull() ?: 0f,
                styleId = def.railStyleId,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp * min(scaleX, scaleY))
                    .height(with(density) { railH.toDp() })
                    .align(Alignment.BottomStart),
                onChange = { c.setParam(id, def.params.lastIndex, it) },
            )
        }

        // 4) HEADER overlay
        Row(
            Modifier.fillMaxWidth().height(with(density) { (HEADER_H * scaleY).toDp() })
                .padding(
                    start = with(density) { (58f * scaleX).toDp() },
                    end = with(density) { (16f * scaleX).toDp() },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .08f))
                    .clickable { c.toggleBypass(id) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (!by) Color(0xFFC9D2E6) else Color(0xFF2A3040)),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Bypass", color = INK, fontSize = 11.sp, fontWeight = FontWeight(700))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    def.title, color = Color(0xFFE7E3F6),
                    fontSize = with(density) { (26f * scaleY / density.density).sp },
                    fontFamily = def.titleFont,
                )
                Text(
                    def.type.uppercase(), color = Color.White.copy(alpha = .55f),
                    fontSize = 9.sp, fontWeight = FontWeight(700), letterSpacing = 1.6.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (def.isInstrument()) {
                FxChip("Surprise me") { c.surprise(id) }
                Spacer(Modifier.width(6.dp))
                FxChip("A / B") { /* bloc 2 */ }
                Spacer(Modifier.width(6.dp))
            }
            FxPresetChip(def, s.presetName[id] ?: "", onSelect = { c.applyPreset(id, it) })
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  PRESET CHIP (176 x 36 closed) — faithful to the HTML
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FxPresetChip(def: FxDef, current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.width(176.dp).height(36.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Color(0x18FFFFFF), Color(0x0AFFFFFF))))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .clickable { open = !open }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, color = INK, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("▾", color = INK.copy(alpha = .6f), fontSize = 12.sp)
        }
        if (open) {
            Column(
                Modifier.offset(y = 38.dp).width(176.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D1119))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp)),
            ) {
                def.presets.keys.forEach { name ->
                    Box(
                        Modifier.fillMaxWidth().clickable { onSelect(name); open = false }.padding(11.dp),
                    ) {
                        Text(name, color = if (name == current) Color(0xFFF2D58A) else INK, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  KNOB — 270° arc, shadow underneath, rotating indicator
// ═══════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════
//  KNOB RENDERER — bitmaps pre-rendered per (material x size)
//
//  Uses android.graphics.Canvas + Paint.setShadowLayer() for a real blurred
//  shadow (the shadowBlur equivalent). Seven layers, bottom to top:
//    1. drop shadow under the knob        (setShadowLayer)
//    2. grey 270-degree track (the groove)
//    3. knurled skirt (or flutes, by material)
//    4. body: multi-stop LinearGradient   <- the polished metal
//    5. specular highlight (radial, upper left)
//    6. dark inner ring (body / cap seam)
//    7. light outer ring (bevel)
//
//  NOT in the bitmap, redrawn every frame: the coloured value arc and the
//  rotating indicator with its LED tip.
// ═══════════════════════════════════════════════════════════════════════════

private object KnobRenderer {
    private val cache = HashMap<String, ImageBitmap>()

    fun get(style: KnobStyle, sizePx: Int): ImageBitmap {
        val key = "${style.name}_$sizePx"
        cache[key]?.let { return it }
        return render(style, sizePx).also { cache[key] = it }
    }

    private fun render(style: KnobStyle, S: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp)
        val p = APaint(APaint.ANTI_ALIAS_FLAG)
        val cx = S / 2f
        val cy = S / 2f
        val rArc = S * 0.46f
        val rSkirt = S * 0.37f
        val rBody = S * 0.29f

        // 1) drop shadow
        p.style = APaint.Style.FILL
        p.color = 0x00000000
        p.setShadowLayer(S * 0.14f, 0f, S * 0.08f, 0x88000000.toInt())
        c.drawCircle(cx, cy + S * 0.04f, rSkirt * 1.02f, p)
        p.clearShadowLayer()

        // 2) grey 270-degree track
        p.style = APaint.Style.STROKE
        p.strokeWidth = S * 0.035f
        p.strokeCap = APaint.Cap.ROUND
        p.color = 0x28FFFFFF
        c.drawArc(ARectF(cx - rArc, cy - rArc, cx + rArc, cy + rArc), 135f, 270f, false, p)

        // 3) skirt
        when (style) {
            KnobStyle.ROUND, KnobStyle.CHROME, KnobStyle.KNURL -> {
                p.style = APaint.Style.FILL
                p.color = 0xFF16161B.toInt()
                c.drawCircle(cx, cy, rSkirt, p)
                p.style = APaint.Style.STROKE
                p.strokeWidth = max(1f, S * 0.008f)
                p.color = 0xAA000000.toInt()
                for (i in 0 until 44) {
                    val a = i * (2 * PI.toFloat() / 44)
                    c.drawLine(
                        cx + cos(a) * rSkirt * 0.85f, cy + sin(a) * rSkirt * 0.85f,
                        cx + cos(a) * rSkirt * 1.00f, cy + sin(a) * rSkirt * 1.00f,
                        p,
                    )
                }
            }
            KnobStyle.FLUTE -> {
                p.style = APaint.Style.FILL
                p.color = 0xFF2E1810.toInt()
                c.drawCircle(cx, cy, rSkirt, p)
                p.style = APaint.Style.STROKE
                p.strokeWidth = max(1f, S * 0.020f)
                p.color = 0xCC000000.toInt()
                for (i in 0 until 18) {
                    val a = i * (2 * PI.toFloat() / 18)
                    c.drawLine(
                        cx + cos(a) * rSkirt * 0.78f, cy + sin(a) * rSkirt * 0.78f,
                        cx + cos(a) * rSkirt * 1.00f, cy + sin(a) * rSkirt * 1.00f,
                        p,
                    )
                }
            }
            KnobStyle.GLASS, KnobStyle.FLAT -> {
                p.style = APaint.Style.STROKE
                p.strokeWidth = S * 0.018f
                p.color = 0x33FFFFFF
                c.drawCircle(cx, cy, rSkirt, p)
            }
        }

        // 4) body: multi-stop gradient
        val bodyColors = when (style) {
            KnobStyle.ROUND -> intArrayOf(
                0xFFE6EAF2.toInt(), 0xFFB8C0CC.toInt(), 0xFF7A8290.toInt(),
                0xFF3A4048.toInt(), 0xFF6A7280.toInt(), 0xFF22262E.toInt(),
            )
            KnobStyle.CHROME -> intArrayOf(
                0xFFFFFFFF.toInt(), 0xFFF4F6FA.toInt(), 0xFF9AA2B0.toInt(), 0xFFE6EAF2.toInt(),
                0xFF7C8492.toInt(), 0xFFDFE4EC.toInt(), 0xFF5A626E.toInt(),
            )
            KnobStyle.KNURL -> intArrayOf(
                0xFF55555C.toInt(), 0xFF3A3A40.toInt(), 0xFF1A1A1E.toInt(),
                0xFF33333A.toInt(), 0xFF0A0A0C.toInt(),
            )
            KnobStyle.FLUTE -> intArrayOf(
                0xFF5C3A22.toInt(), 0xFF3D2414.toInt(), 0xFF1A0D07.toInt(),
                0xFF4A2A16.toInt(), 0xFF0F0603.toInt(),
            )
            KnobStyle.GLASS -> intArrayOf(0x88FFFFFF.toInt(), 0x44FFFFFF, 0x22FFFFFF, 0x33FFFFFF, 0x11FFFFFF)
            KnobStyle.FLAT -> intArrayOf(
                0xFF2A3448.toInt(), 0xFF1E2C44.toInt(), 0xFF0C1220.toInt(),
                0xFF1A2438.toInt(), 0xFF060A14.toInt(),
            )
        }
        val positions = FloatArray(bodyColors.size) { it / (bodyColors.size - 1).toFloat() }
        p.shader = ALinearGradient(
            cx - rBody * 0.75f, cy - rBody * 0.75f,
            cx + rBody * 0.75f, cy + rBody * 0.75f,
            bodyColors, positions, AShader.TileMode.CLAMP,
        )
        p.style = APaint.Style.FILL
        c.drawCircle(cx, cy, rBody, p)
        p.shader = null

        // 5) specular highlight
        if (style != KnobStyle.FLAT) {
            val specCx = cx - rBody * 0.36f
            val specCy = cy - rBody * 0.40f
            val specR = rBody * 0.74f
            p.shader = ARadialGradient(
                specCx, specCy, specR,
                intArrayOf(0xCCFFFFFF.toInt(), 0x44FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.42f, 1f), AShader.TileMode.CLAMP,
            )
            c.drawCircle(specCx, specCy, specR, p)
            p.shader = null
        }

        // 6) dark inner ring
        p.style = APaint.Style.STROKE
        p.strokeWidth = max(1f, S * 0.010f)
        p.color = 0x55000000
        c.drawCircle(cx, cy, rBody * 0.94f, p)

        // 7) light outer bevel
        p.strokeWidth = max(1f, S * 0.006f)
        p.color = 0x33FFFFFF
        c.drawCircle(cx, cy, rBody, p)

        return bmp.asImageBitmap()
    }
}

@Composable
private fun FxKnob(
    value: Float,
    label: String,
    display: String,
    accent: Color,
    style: KnobStyle,
    sizeDp: Dp,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val sizePx = with(density) { sizeDp.toPx() }.toInt().coerceAtLeast(20)
    val body = remember(style, sizePx) { KnobRenderer.get(style, sizePx) }
    var v by remember { mutableFloatStateOf(value) }
    var lastHap by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value) { if (v != value) v = value }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(), color = Color.White.copy(alpha = 0.65f),
            fontSize = with(density) { (sizeDp.value * 0.10f).sp },
            fontWeight = FontWeight(700), letterSpacing = 1.1.sp, maxLines = 1,
        )
        Canvas(
            Modifier.size(sizeDp)
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        val nv = (v - amount.y / (170f * density.density)).coerceIn(0f, 1f)
                        if (nv != v) {
                            v = nv
                            onValueChange(nv)
                            val now = System.currentTimeMillis()
                            if (now - lastHap > 55) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHap = now
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        v = 0.5f
                        onValueChange(0.5f)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    })
                },
        ) {
            drawImage(
                image = body,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rArc = size.width * 0.46f
            val rBody = size.width * 0.29f
            val sweep = 270f * v
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = sweep.coerceAtLeast(0.4f),
                useCenter = false,
                topLeft = Offset(cx - rArc, cy - rArc),
                size = Size(rArc * 2, rArc * 2),
                style = Stroke(width = size.width * 0.035f, cap = StrokeCap.Round),
            )
            val ang = (-135f + sweep) * PI.toFloat() / 180f
            val r1 = rBody * 0.28f
            val r2 = rBody * 0.82f
            val indicatorColor = if (style == KnobStyle.FLAT || style == KnobStyle.GLASS) accent else Color.White
            drawLine(
                color = indicatorColor,
                start = Offset(cx + r1 * cos(ang), cy + r1 * sin(ang)),
                end = Offset(cx + r2 * cos(ang), cy + r2 * sin(ang)),
                strokeWidth = size.width * 0.028f,
                cap = StrokeCap.Round,
            )
            val tip = Offset(cx + r2 * cos(ang), cy + r2 * sin(ang))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, indicatorColor.copy(alpha = 0.2f), Color.Transparent),
                    center = tip, radius = size.width * 0.05f,
                ),
                radius = size.width * 0.05f,
                center = tip,
            )
        }
        Text(
            display, color = Color.White.copy(alpha = 0.9f),
            fontSize = with(density) { (sizeDp.value * 0.12f).sp },
            fontWeight = FontWeight(700), maxLines = 1,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  RAIL (bottom band) — for instruments
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FxRail(def: FxDef, value: Float, styleId: Int, modifier: Modifier, onChange: (Float) -> Unit) {
    val density = LocalDensity.current
    var v by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { v = value }
    val (bgTop, bgBottom, accentCol) = when (styleId) {
        5 -> Triple(Color(0xFF1F1F26), Color(0xFF07070A), Color(0xFFE8C98A))
        6 -> Triple(Color(0xFF4A2A16), Color(0xFF1A0D07), Color(0xFFD9C08A))
        7 -> Triple(Color(0xFF2A1810), Color(0xFF0E0806), Color(0xFFE0A45A))
        8 -> Triple(Color(0xFF4A3A20), Color(0xFF1C1408), Color(0xFFD9A05B))
        9 -> Triple(Color(0xFF2A2E36), Color(0xFF0A0B0E), Color(0xFFC0C8D0))
        12 -> Triple(Color(0xFF4A0E1C), Color(0xFF14060A), Color(0xFFE8C060))
        13 -> Triple(Color(0x22FFFFFF), Color(0x08FFFFFF), Color(0xFFC9B8FF))
        else -> Triple(Color(0xFF0A1220), Color(0xFF04060C), Color(0xFF7AD0FF))
    }
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures { change, amount ->
                change.consume()
                v = (v - amount.y / (170f * density.density)).coerceIn(0f, 1f)
                onChange(v)
            }
        },
    ) {
        val h = size.height
        val w = size.width
        drawRect(Brush.linearGradient(listOf(bgTop, bgBottom)), size = size)
        drawRect(accentCol.copy(alpha = .5f), topLeft = Offset(0f, 0f), size = Size(w, 2f))
        val sx = w * .62f
        val sw = w * .30f
        val sy = h * .48f
        drawRoundRect(
            Color.Black.copy(alpha = .55f),
            topLeft = Offset(sx - 3f, sy - 4f), size = Size(sw + 6f, 8f),
            cornerRadius = CornerRadius(4f, 4f),
        )
        drawRoundRect(
            Brush.linearGradient(listOf(accentCol.copy(alpha = .7f), accentCol)),
            topLeft = Offset(sx, sy - 2f), size = Size((sw * v).coerceAtLeast(2f), 4f),
            cornerRadius = CornerRadius(2f, 2f),
        )
        val tx = sx + sw * v
        drawCircle(Color.Black.copy(alpha = .4f), radius = h * .22f, center = Offset(tx + 1f, sy + 2f))
        drawCircle(
            Brush.linearGradient(listOf(Color.White, Color(0xFFD0D5DE))),
            radius = h * .20f, center = Offset(tx, sy - 2f),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  SCENE — full-frame canvas + dispatcher
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FxSceneCanvas(def: FxDef, v: FloatArray, mini: Boolean) {
    val fixed = fxPreview2TimeOverride
    var t by remember { mutableFloatStateOf(fixed ?: 0f) }
    val errCount = remember { intArrayOf(0) }
    if (fixed == null) {
        LaunchedEffect(Unit) {
            val start = System.nanoTime()
            while (isActive) {
                withFrameMillis { t = (System.nanoTime() - start) / 1_000_000_000f }
                if (mini) delay(66)
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (errCount[0] <= 40) {
            try {
                drawScene(def, v, fixed ?: t, size, mini)
            } catch (_: Throwable) {
                errCount[0]++
            }
        }
    }
}

private fun DrawScope.drawScene(def: FxDef, v: FloatArray, t: Float, sz: Size, mini: Boolean) {
    if (v.isEmpty()) return
    // Common background, identical to the HTML
    drawRect(Brush.linearGradient(listOf(BG_TOP, BG_BOTTOM)), size = sz)
    when (def.id) {
        0 -> sceneSpace(v, def.accent, t, sz)
        1 -> sceneEcho(v, def.accent, t, sz, mini)
        2 -> sceneTone(v, def.accent, t, sz, mini)
        3 -> sceneGrip(v, def.accent, t, sz, mini)
        4 -> sceneCeiling(v, def.accent, t, sz, mini)
        5 -> scenePiano(v, def.accent, t, mini)
        6 -> sceneOrgan(v, def.accent, t, mini)
        7 -> sceneSax(v, def.accent, t, mini)
        8 -> sceneStrings(v, def.accent, t, mini)
        9 -> sceneGuitar(v, def.accent, t, mini)
        10 -> sceneAmp(v, def.accent, t, mini)
        11 -> sceneBass(v, def.accent, t, mini)
        12 -> sceneBrass(v, def.accent, t, mini)
        13 -> sceneVoices(v, def.accent, t, mini)
        14 -> sceneSynth(v, def.accent, t, mini)
        else -> sceneGeneric(def, v, t, sz, mini)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  SPACE (0) — 100 % faithful to the HTML `orb`
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.sceneSpace(v: FloatArray, accent: Color, t: Float, sz: Size) {
    if (v.size < 7) return
    val cx = sz.width / 2f
    val cy = 114f * sz.height / WIN_H
    val sz0 = v[0]; val dc = v[1]; val pd = v[2]; val dm = v[3]
    val wd = v[4]; val mx = v[5]; val df = v[6]

    val R = 40f * (.7f + sz0 * .8f) * (1f + .03f * sin(t * 2f))
    val hue = 268f - dm * 75f
    val col = Color.hsl(hue, .48f, .74f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.hsl(hue, .45f, .30f, .20f + .5f * mx), Color.Transparent),
            center = Offset(cx, cy), radius = R * 4.5f,
        ),
        radius = R * 4.5f, center = Offset(cx, cy),
    )

    for (q in 0 until 70) {
        val px = (sin(q * 91.7f) * .5f + .5f) * sz.width
        val py = (sin(q * 47.3f) * .5f + .5f) * sz.height
        val a = .2f + .5f * abs(sin(t * 1.3f + q))
        drawCircle(Color.White.copy(alpha = a), radius = .6f, center = Offset(px, py))
    }

    for (w in 0 until 3) {
        val u = ((t * (.12f + sz0 * .2f) + w / 3f))
        if (u < pd * .4f) continue
        drawCircle(
            col.copy(alpha = (1f - u) * .4f),
            radius = R * (1.1f + u * 5f), center = Offset(cx, cy),
            style = Stroke(width = 1.6f),
        )
    }

    val rx1 = R * 1.75f
    val ry1 = rx1 * (.3f + wd * .18f)
    val tl1 = -.35f + sin(t * .25f) * wd * .55f
    rotate(tl1 * 57.2958f, Offset(cx, cy)) {
        drawOval(
            color = col.copy(alpha = .3f + .5f * mx),
            topLeft = Offset(cx - rx1, cy - ry1),
            size = Size(rx1 * 2f, ry1 * 2f),
            style = Stroke(
                width = 2.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f + dc * 36f, 9f), -t * 30f * (.3f + wd)),
            ),
        )
    }

    val rxB = R * 2.7f
    val ryB = rxB * .36f
    val tlB = .25f + cos(t * .2f) * wd * .4f
    val NB = 22 + (sz0 * 14).toInt()
    val cosB = cos(tlB); val sinB = sin(tlB)
    for (p in 0 until NB) {
        val an = t * (.25f + wd * .9f) + p * 6.2832f / NB
        for (s in 0 until 3) {
            val aa = an - s * .08f * (.4f + dc)
            val wob = sin(aa * 5f + t * 3f) * df * R * .45f
            val ca = cos(aa); val sa = sin(aa)
            val x1 = (rxB + wob) * ca
            val y1 = (ryB + wob * .4f) * sa
            drawCircle(
                color = if (s > 0) col.copy(alpha = (1f - s / 3f) * .85f) else Color.White,
                radius = 2.1f * (1f - s / 3f * .5f),
                center = Offset(cx + x1 * cosB - y1 * sinB, cy + x1 * sinB + y1 * cosB),
            )
        }
    }

    val rxC = min(R * 3.7f, 250f)
    val ryC = rxC * .3f
    val tlC = -.6f + sin(t * .17f) * wd * .3f
    for (c2 in 0 until 3) {
        val an = -t * (.18f + wd * .6f) + c2 * 2.094f
        val T2 = 8 + (dc * 20).toInt()
        for (s in 0 until T2) {
            val aa = an + s * .05f
            val ca = cos(aa); val sa = sin(aa)
            val px = cx + rxC * ca * cos(tlC) - ryC * sa * sin(tlC)
            val py = cy + rxC * ca * sin(tlC) + ryC * sa * cos(tlC)
            drawCircle(
                color = if (s > 0) col.copy(alpha = (1f - s / T2) * (.3f + .6f * mx)) else Color.White.copy(alpha = .9f),
                radius = 3f * (1f - s / T2 * .6f), center = Offset(px, py),
            )
        }
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFEEE8FF), accent, Color(0xFF1D1A38)),
            center = Offset(cx - R * .35f, cy - R * .4f), radius = R,
        ),
        radius = R, center = Offset(cx, cy),
    )

    clipPath(Path().apply { addOval(Rect(Offset(cx - R, cy - R), Size(R * 2, R * 2))) }) {
        for (q in 0 until 3) {
            val rx = R * abs(cos(t * .4f * (.5f + wd) + q * 1.05f))
            drawOval(
                color = Color.White.copy(alpha = .1f + .1f * df),
                topLeft = Offset(cx - rx, cy - R),
                size = Size(rx * 2, R * 2),
                style = Stroke(width = .8f),
            )
        }
    }

    drawCircle(Color.White.copy(alpha = .08f + .4f * mx), radius = R * 1.08f, center = Offset(cx, cy), style = Stroke(width = 1f))
}

// ═══════════════════════════════════════════════════════════════════════════
//  ECHO (1) — 75 %
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.sceneEcho(v: FloatArray, accent: Color, t: Float, sz: Size, mini: Boolean) {
    if (v.size < 12) return
    val fb = .2f + v[8] * .8f
    val cxc = 95f; val cyc = 80f
    for (k in 0 until 4) {
        val u = ((t * .22f + k / 4f))
        drawCircle(
            accent.copy(alpha = (1f - u) * .2f * fb),
            radius = 20f + u * 640f, center = Offset(cxc, cyc), style = Stroke(width = 1.5f),
        )
    }
    val px0 = 190f; val pw = 400f
    val py0 = 18f; val ph = 218f
    val pb = py0 + ph
    drawRect(Color(0xFF0D0B08), topLeft = Offset(px0, py0), size = Size(pw, ph))
    for (k in 1 until 5) {
        drawRect(Color.White.copy(alpha = .07f), topLeft = Offset(px0 + pw * k / 5f, py0), size = Size(1f, ph))
        drawRect(Color.White.copy(alpha = .07f), topLeft = Offset(px0, py0 + ph * k / 5f), size = Size(pw, 1f))
    }
    val pts = (0 until 4).map { n -> Offset(px0 + v[2 * n] * pw, pb - v[2 * n + 1] * ph) }.sortedBy { it.x }
    for (r in 1 until 4) {
        val sp2 = (pts[3].x - pts[0].x) + pw * .1f
        pts.forEach { p ->
            val gx = p.x + sp2 * r
            if (gx < px0 + pw) {
                drawRect(
                    accent.copy(alpha = .18f * fb.pow(r + 1f)),
                    topLeft = Offset(gx - 3f, p.y + (pb - p.y) * (1f - fb.pow(r.toFloat()))),
                    size = Size(6f, (pb - p.y) * fb.pow(r.toFloat())),
                )
            }
        }
    }
    val path = Path()
    path.moveTo(px0, pb)
    pts.forEach { path.lineTo(it.x, it.y) }
    path.lineTo(px0 + pw, pb)
    drawPath(path, color = accent.copy(alpha = .5f + .5f * fb), style = Stroke(width = 2f))
    val u = (t * .28f) % 1f
    val dx = px0 + u * pw
    var dy = pb
    val allPts = listOf(Offset(px0, pb)) + pts + listOf(Offset(px0 + pw, pb))
    for (k in 1 until allPts.size) {
        val A = allPts[k - 1]; val B = allPts[k]
        if (dx in A.x..B.x) { dy = A.y + (B.y - A.y) * (dx - A.x) / (B.x - A.x + .001f); break }
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, accent.copy(alpha = .8f), Color.Transparent),
            center = Offset(dx, dy), radius = 14f,
        ),
        radius = 14f, center = Offset(dx, dy),
    )
    pts.forEach { drawCircle(accent, radius = 9f, center = it) }
    drawCircle(
        brush = Brush.linearGradient(listOf(Color(0xFFE8D2A0), Color(0xFF7D6335))),
        radius = 30f, center = Offset(56f, 16f),
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  TONE (2) — 70 %
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.sceneTone(v: FloatArray, accent: Color, t: Float, sz: Size, mini: Boolean) {
    if (v.size < 8) return
    val X0 = 134f; val Y0 = 12f; val W = 466f; val Hh = 138f
    drawRoundRect(
        Color(0xFF1B070C), topLeft = Offset(X0, Y0), size = Size(W, Hh),
        cornerRadius = CornerRadius(13f, 13f),
    )
    drawRect(Color.White.copy(alpha = .08f), topLeft = Offset(X0, Y0 + Hh / 2f), size = Size(W, 1f))
    val path = Path()
    for (k in 0..W.toInt()) {
        val u = k / W
        val yy = Y0 + Hh / 2f -
            ((v[1] - .5f) * 2f * (1f / (1f + exp((u - v[0]) * 16f))) +
                (v[3] - .5f) * 2f * exp(-((u - v[2]) / .08f).pow(2f)) +
                (v[6] - .5f) * 2f * (1f / (1f + exp(-(u - v[5]) * 16f)))) * Hh * .4f
        if (k == 0) path.moveTo(X0 + k, yy) else path.lineTo(X0 + k, yy)
    }
    drawPath(path, color = accent, style = Stroke(width = 2.5f))
    listOf(0 to 1, 2 to 3, 5 to 6).forEach { (fi, gi) ->
        val nx = X0 + v[fi] * W
        val ny = Y0 + (1f - v[gi]) * Hh
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, accent, accent.copy(alpha = .5f)),
                center = Offset(nx, ny), radius = 11f,
            ),
            radius = 11f, center = Offset(nx, ny),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  GRIP (3) — 65 %
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.sceneGrip(v: FloatArray, accent: Color, t: Float, sz: Size, mini: Boolean) {
    if (v.size < 6) return
    val gx = 135f; val gy = 218f; val gR = 118f
    drawArc(
        color = Color(0xFF1C1E24), startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(gx - gR, gy - gR), size = Size(gR * 2, gR * 2),
        style = Stroke(width = 18f, cap = StrokeCap.Round),
    )
    drawArc(
        color = accent.copy(alpha = .6f), startAngle = 180f, sweepAngle = 180f * v[0], useCenter = false,
        topLeft = Offset(gx - gR, gy - gR), size = Size(gR * 2, gR * 2),
        style = Stroke(width = 6f, cap = StrokeCap.Round),
    )
    val ang = PI.toFloat() * (1f + v[0])
    drawLine(
        Color(0xFF14120E), start = Offset(gx, gy),
        end = Offset(gx + gR * .9f * cos(ang), gy + gR * .9f * sin(ang)),
        strokeWidth = 2.6f, cap = StrokeCap.Round,
    )
    drawCircle(Color(0xFF2A2E38), radius = 13f, center = Offset(gx, gy))
}

// ═══════════════════════════════════════════════════════════════════════════
//  CEILING (4) — 60 %
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.sceneCeiling(v: FloatArray, accent: Color, t: Float, sz: Size, mini: Boolean) {
    if (v.size < 6) return
    val SX = 390f; val SY = 20f; val SW = 300f; val SH = 223f
    val my = SY + SH / 2f
    val cy = my - v[1] * SH * .42f
    val fy = my + v[1] * SH * .42f
    val kx = 125f; val ky = 128f
    val R0 = 50f + v[0] * 130f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFE0A24A).copy(alpha = .18f + .3f * v[0]), Color.Transparent),
            center = Offset(kx, ky), radius = R0,
        ),
        radius = R0, center = Offset(kx, ky),
    )
    drawRect(Color(0xFF17140F), topLeft = Offset(SX, SY), size = Size(SW, SH))
    val path = Path()
    val gain = 1f + v[0] * 2.4f
    for (k in 0..SW.toInt()) {
        val u = k / SW
        val y0 = sin(u * 13f + t * 3f) * .7f + sin(u * 31f + t) * .2f
        val s = (y0 * gain).coerceIn(-v[1], v[1])
        val yy = my - s * SH * .42f
        if (k == 0) path.moveTo(SX + k, yy) else path.lineTo(SX + k, yy)
    }
    drawPath(path, color = accent, style = Stroke(width = 2.2f))
    drawLine(accent.copy(alpha = .6f), Offset(SX, cy), Offset(SX + SW, cy), 2f)
    drawLine(accent.copy(alpha = .6f), Offset(SX, fy), Offset(SX + SW, fy), 2f)
}

// ═══════════════════════════════════════════════════════════════════════════
//  INSTRUMENT / AMP SCENES (5-14)
//  Coordinates are window units (825 x 343); the caller applies the density
//  scale, so nothing here multiplies by density.
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.instFace(top: Color, bottom: Color) {
    drawRect(Brush.verticalGradient(listOf(top, bottom)), size = size)
}

private fun rrectPath(x: Float, y: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    moveTo(x + r, y); lineTo(x + w - r, y); quadraticBezierTo(x + w, y, x + w, y + r)
    lineTo(x + w, y + h - r); quadraticBezierTo(x + w, y + h, x + w - r, y + h)
    lineTo(x + r, y + h); quadraticBezierTo(x, y + h, x, y + h - r)
    lineTo(x, y + r); quadraticBezierTo(x, y, x + r, y); close()
}

/** 5 — Piano: strings receding into the body, felt bar, pedal glow. */
private fun DrawScope.scenePiano(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 9) return
    instFace(Color(0xFF120A06), Color(0xFF060302))
    val felt = v[0]; val lid = v[1]; val pedal = v[2]; val body = v[6]
    for (k in 0 until 22) {
        val u = k / 21f
        val y = 40f + u * 150f
        val sag = sin(t * .8f + u * 3f) * 3f * (1f - felt)
        val a = (.10f + .35f * (1f - felt)) * (.4f + .6f * body)
        drawLine(accent.copy(alpha = a), Offset(60f, y + sag), Offset(700f, y + sag * .4f),
            strokeWidth = 1.2f + (1f - u) * 1.4f)
    }
    drawRect(Color(0xFF2A1A12).copy(alpha = .5f + .4f * felt),
        topLeft = Offset(150f, 96f), size = Size(420f, 14f + felt * 22f))
    drawRect(accent.copy(alpha = .10f + .25f * lid),
        topLeft = Offset(0f, 0f), size = Size(825f, 30f + lid * 40f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = .05f + .3f * pedal), Color.Transparent),
            center = Offset(412f, 343f), radius = 60f + pedal * 220f),
        radius = 60f + pedal * 220f, center = Offset(412f, 343f))
}

/** 6 — Organ: nine drawbars per manual, a rotary horn, percussive click. */
private fun DrawScope.sceneOrgan(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 31) return
    instFace(Color(0xFF120A06), Color(0xFF060302))
    val rotary = v[25]; val drive = v[27]
    val rcx = 560f; val rcy = 120f; val rr = 78f
    for (lobe in 0 until 2) {
        val a = t * (1.2f + rotary) + lobe * PI.toFloat()
        val lx = rcx + cos(a) * rr * .55f
        val ly = rcy + sin(a) * rr * .30f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = .30f + .2f * drive), Color.Transparent),
                center = Offset(lx, ly), radius = 70f),
            radius = 70f, center = Offset(lx, ly))
    }
    drawCircle(Color.Black.copy(alpha = .55f), radius = rr, center = Offset(rcx, rcy))
    for (i in 0 until 9) {
        val x = 60f + i * 26f
        drawRect(Color.White.copy(alpha = .07f), topLeft = Offset(x, 60f), size = Size(14f, 170f))
        val up = v[i] / 8f
        drawRect(accent.copy(alpha = .85f),
            topLeft = Offset(x + 3f, 60f + (1f - up) * 150f), size = Size(8f, up * 150f))
        val lo = v[9 + i] / 8f
        drawRect(Color.White.copy(alpha = .55f),
            topLeft = Offset(x + 3f, 240f + (1f - lo) * 60f), size = Size(8f, lo * 60f))
    }
    if (v[18] > .5f) {
        val a = .25f + .25f * abs(sin(t * 6f))
        drawRect(accent.copy(alpha = a), topLeft = Offset(58f, 54f), size = Size(240f, 3f))
    }
}

/** 7 — Sax: a tapered tube with tone holes, air particles along it. */
private fun DrawScope.sceneSax(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 9) return
    instFace(Color(0xFF1F2635), Color(0xFF131925))
    val breath = v[0]; val growl = v[2]; val vib = v[3]
    val p = Path().apply {
        moveTo(300f, 40f)
        quadraticBezierTo(260f, 150f, 330f, 210f)
        quadraticBezierTo(420f, 280f, 540f, 300f)
        quadraticBezierTo(660f, 318f, 720f, 250f)
    }
    drawPath(p, color = accent.copy(alpha = .85f), style = Stroke(width = 15f, cap = StrokeCap.Round))
    drawPath(p, color = Color(0xFF8A6A32).copy(alpha = .6f), style = Stroke(width = 7f, cap = StrokeCap.Round))
    for (k in 0 until 7) {
        val u = .12f + k * .11f
        val hx = 300f + u * 400f
        val hy = 60f + sin(u * 3.1f) * 120f + u * 150f
        drawCircle(Color(0xFF1A1208), radius = 6f, center = Offset(hx, hy))
    }
    val n = (breath * 26f).toInt()
    for (k in 0 until n) {
        val ph = t * (1.1f + vib) + k * .7f
        val x = 720f + (ph % 1f) * 90f
        val y = 250f - ((ph * 1.7f) % 1f) * 70f + sin(ph * 5f) * 6f * growl
        drawCircle(accent.copy(alpha = (1f - (ph % 1f)) * .6f), radius = 2.4f, center = Offset(x, y))
    }
}

/** 8 — Strings: a violin body, four vibrating strings, a bow crossing them. */
private fun DrawScope.sceneStrings(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 10) return
    instFace(Color(0xFF1F2635), Color(0xFF131925))
    val pressure = v[0]; val speed = v[1]; val motion = v[2]
    val bx = 150f; val by = 60f; val bw = 300f; val bh = 210f
    drawPath(rrectPath(bx, by, bw, bh, 70f), color = Color(0xFF6A3A1A).copy(alpha = .55f))
    drawPath(rrectPath(bx, by, bw, bh, 70f), color = accent.copy(alpha = .35f), style = Stroke(width = 2f))
    drawLine(Color(0xFF160C06), Offset(250f, 130f), Offset(250f, 210f), 3f)
    drawLine(Color(0xFF160C06), Offset(350f, 130f), Offset(350f, 210f), 3f)
    for (s in 0 until 4) {
        val y = 110f + s * 32f
        val amp = 2f + pressure * 7f
        val path = Path().apply { moveTo(bx + 20f, y) }
        for (k in 1..40) {
            val u = k / 40f
            val x = bx + 20f + u * (bw - 40f)
            val dy = sin(u * PI.toFloat() * 3f + t * (6f + speed * 20f)) * amp * sin(u * PI.toFloat())
            path.lineTo(x, y + dy)
        }
        drawPath(path, color = Color(0xFFE8D8C0).copy(alpha = .75f), style = Stroke(width = 1.4f))
    }
    val ba = -.5f + sin(t * .6f) * .3f * motion
    rotate(ba * 57.2958f, Offset(bx + bw / 2f, by + bh / 2f)) {
        drawRect(Color(0xFF3A2410), topLeft = Offset(bx - 40f, by + bh / 2f - 4f), size = Size(bw + 80f, 8f))
    }
}

/** 9 — Guitar: body, neck, six strings, a pickup that moves with the knob. */
private fun DrawScope.sceneGuitar(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 11) return
    instFace(Color(0xFF1F2635), Color(0xFF131925))
    val pick = v[1]; val strum = v[2]; val bend = v[4]; val pickup = v[5]
    drawPath(rrectPath(60f, 120f, 260f, 170f, 60f), color = Color(0xFF8A4A20).copy(alpha = .6f))
    drawCircle(Color(0xFF160C06), radius = 30f, center = Offset(190f, 205f))
    drawRect(Color(0xFF3A2410), topLeft = Offset(300f, 150f), size = Size(430f, 40f))
    for (f in 0 until 12) {
        val x = 320f + f * 34f
        drawRect(Color.White.copy(alpha = .18f), topLeft = Offset(x, 150f), size = Size(1.5f, 40f))
    }
    for (s in 0 until 6) {
        val y = 156f + s * 5.6f
        val amp = 1f + strum * 5f
        val path = Path().apply { moveTo(180f, y) }
        for (k in 1..60) {
            val u = k / 60f
            val x = 180f + u * 550f
            val dy = sin(u * PI.toFloat() * 2f + t * 9f) * amp * sin(u * PI.toFloat()) + bend * 12f * u
            path.lineTo(x, y + dy)
        }
        drawPath(path, color = Color(0xFFE0D0B8).copy(alpha = .8f), style = Stroke(width = 1.2f))
    }
    val px = 420f + pickup * 260f
    drawPath(rrectPath(px, 142f, 34f, 56f, 8f), color = Color(0xFF2A2E38))
    drawPath(rrectPath(px, 142f, 34f, 56f, 8f), color = accent.copy(alpha = .6f), style = Stroke(width = 2f))
    if (pick > .5f) {
        val a = .3f + .4f * abs(sin(t * 8f))
        drawCircle(accent.copy(alpha = a), radius = 10f, center = Offset(230f, 200f))
    }
}

/** 10 — Amp: the cabinet, a cone breathing with gain, the mic on a stand. */
private fun DrawScope.sceneAmp(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 12) return
    instFace(Color(0xFF0A0806), Color(0xFF0A0806))
    val gain = v[0]; val master = v[5]; val micPos = v[7]; val micDist = v[8]
    val cx = 150f; val cy = 60f; val cw = 380f; val ch = 240f
    drawPath(rrectPath(cx, cy, cw, ch, 18f), color = Color(0xFF241812))
    drawPath(rrectPath(cx, cy, cw, ch, 18f), color = accent.copy(alpha = .25f), style = Stroke(width = 2f))
    val r = 70f + gain * 34f * (1f + .04f * sin(t * 8f * (1f + gain * 2f)))
    val ccx = cx + cw / 2f; val ccy = cy + ch / 2f
    drawCircle(Color(0xFF120C08), radius = 96f, center = Offset(ccx, ccy))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF3A2A1E), accent.copy(alpha = .25f + .45f * master), Color(0xFF0E0A06)),
            center = Offset(ccx - r * .3f, ccy - r * .3f), radius = r),
        radius = r, center = Offset(ccx, ccy))
    drawCircle(Color(0xFF1A120C), radius = r * .28f, center = Offset(ccx, ccy))
    val mx = 640f + micDist * 60f
    val my = 200f - micPos * 70f
    drawLine(Color(0xFF3A4048), Offset(mx, my + 60f), Offset(mx, 330f), 6f)
    drawPath(rrectPath(mx - 16f, my - 34f, 32f, 68f, 12f), color = Color(0xFF4A5260))
    drawPath(rrectPath(mx - 16f, my - 34f, 32f, 68f, 12f), color = accent.copy(alpha = .7f), style = Stroke(width = 1.6f))
    val n = 2 + (master * 4f).toInt()
    for (k in 0 until n) {
        val a = .10f + .18f * (1f - k / n.toFloat())
        drawArc(
            color = accent.copy(alpha = a),
            startAngle = -60f, sweepAngle = 120f, useCenter = false,
            topLeft = Offset(ccx + 90f + k * 26f, ccy - 60f - k * 20f),
            size = Size(120f + k * 40f, 120f + k * 40f),
            style = Stroke(width = 2f))
    }
}

/** 11 — Bass: a five-band EQ ladder and a VU needle driven by drive. */
private fun DrawScope.sceneBass(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 10) return
    instFace(Color(0xFF0A0806), Color(0xFF0A0806))
    val drive = v[0]; val comp = v[1]
    val bands = listOf(v[3], v[4], v[5], v[6], v[7])
    for (i in 0 until 5) {
        val x = 90f + i * 60f
        drawRect(Color.White.copy(alpha = .06f), topLeft = Offset(x, 60f), size = Size(18f, 190f))
        val h = bands[i] * 190f
        drawRect(accent.copy(alpha = .85f), topLeft = Offset(x + 4f, 250f - h), size = Size(10f, h))
        drawRect(Color.White.copy(alpha = .5f), topLeft = Offset(x - 2f, 60f), size = Size(22f, 2f))
    }
    val gx = 500f; val gy = 220f; val gR = 110f
    drawArc(Color(0xFF1C1E24), 180f, 180f, false,
        topLeft = Offset(gx - gR, gy - gR), size = Size(gR * 2, gR * 2), style = Stroke(width = 14f))
    drawArc(accent.copy(alpha = .55f), 180f, 180f * (1f - comp), false,
        topLeft = Offset(gx - gR, gy - gR), size = Size(gR * 2, gR * 2), style = Stroke(width = 5f))
    val swing = (drive * .8f + .1f * sin(t * 4f) * (1f - comp))
    val ang = PI.toFloat() * (1f + swing.coerceIn(0f, 1f))
    drawLine(Color(0xFFE8E0D0), Offset(gx, gy),
        Offset(gx + gR * .88f * cos(ang), gy + gR * .88f * sin(ang)), 3f, StrokeCap.Round)
    drawCircle(Color(0xFF2A2E38), 12f, Offset(gx, gy))
}

/** 12 — Brass: a bell and three pistons, the mute plugging the bell. */
private fun DrawScope.sceneBrass(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 9) return
    instFace(Color(0xFF0A0806), Color(0xFF0A0806))
    val lips = v[0]; val vibrato = v[2]; val section = v[3]; val mute = v[4]
    val p = Path().apply {
        moveTo(320f, 110f); lineTo(560f, 60f); lineTo(560f, 260f); lineTo(320f, 210f); close()
    }
    drawPath(p, color = accent.copy(alpha = .30f + .35f * lips))
    drawPath(p, color = accent.copy(alpha = .7f), style = Stroke(width = 3f))
    drawOval(color = accent.copy(alpha = .5f),
        topLeft = Offset(548f, 56f), size = Size(34f, 208f), style = Stroke(width = 3f))
    if (mute > .05f) {
        val mx = 560f - mute * 90f
        val mp = Path().apply {
            moveTo(mx, 90f); lineTo(mx + 60f, 120f); lineTo(mx + 60f, 200f); lineTo(mx, 230f); close()
        }
        drawPath(mp, color = Color(0xFF3A2410).copy(alpha = .85f))
    }
    for (k in 0 until 3) {
        val x = 150f + k * 50f
        val lift = abs(sin(t * (3f + vibrato * 8f) + k)) * 10f * vibrato
        drawRect(Color(0xFF2A2E38), topLeft = Offset(x, 140f - lift), size = Size(30f, 40f))
        drawRect(accent.copy(alpha = .8f), topLeft = Offset(x + 10f, 120f - lift), size = Size(10f, 26f))
    }
    for (k in 1..(section.toInt())) {
        drawPath(
            Path().apply {
                moveTo(320f + k * 18f, 130f); lineTo(540f + k * 18f, 90f)
                lineTo(540f + k * 18f, 240f); lineTo(320f + k * 18f, 190f); close()
            },
            color = accent.copy(alpha = .10f))
    }
}

/** 13 — Voices: a vowel XY pad and a choir of breathing dots. */
private fun DrawScope.sceneVoices(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 9) return
    instFace(Color(0xFF05040C), Color(0xFF05040C))
    val vx = v[0]; val vy = v[1]; val breath = v[2]; val choir = v[3]
    val px = 90f; val py = 60f; val pw = 260f; val ph = 210f
    drawPath(rrectPath(px, py, pw, ph, 14f), color = Color(0xFF0C0A18))
    drawPath(rrectPath(px, py, pw, ph, 14f), color = accent.copy(alpha = .35f), style = Stroke(width = 2f))
    for (k in 1 until 4) {
        drawLine(Color.White.copy(alpha = .06f), Offset(px + pw * k / 4f, py), Offset(px + pw * k / 4f, py + ph), 1f)
        drawLine(Color.White.copy(alpha = .06f), Offset(px, py + ph * k / 4f), Offset(px + pw, py + ph * k / 4f), 1f)
    }
    val fx2 = px + vx * pw
    val fy2 = py + vy * ph
    val fr = 20f + breath * 40f + 3f * sin(t * 2f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = .5f), accent.copy(alpha = .35f), Color.Transparent),
            center = Offset(fx2, fy2), radius = fr * 1.8f),
        radius = fr * 1.8f, center = Offset(fx2, fy2))
    drawCircle(accent.copy(alpha = .9f), radius = 5f, center = Offset(fx2, fy2))
    val n = 4 + (choir * 18f).toInt()
    for (k in 0 until n) {
        val a = k * 2.4f
        val rr = 120f + (k % 5) * 26f
        val x = 560f + cos(a) * rr
        val y = 165f + sin(a) * rr * .8f
        val s = 2f + 2.4f * (.5f + .5f * sin(t * (1.5f + breath * 3f) + k))
        drawCircle(accent.copy(alpha = .35f + .4f * (.5f + .5f * sin(t + k))), radius = s, center = Offset(x, y))
    }
}

/** 14 — Synth: two oscillators, a filter sweep and an envelope. */
private fun DrawScope.sceneSynth(v: FloatArray, accent: Color, t: Float, mini: Boolean) {
    if (v.size < 32) return
    instFace(Color(0xFF04060C), Color(0xFF04060C))
    val waveA = v[0]; val waveB = v[1]; val detune = v[2]; val cutoff = v[7]
    val reso = v[8]; val atk = v[12]; val dec = v[13]; val sus = v[14]; val rel = v[15]
    val sx = 60f; val sy = 60f; val sw = 420f; val sh = 140f
    drawPath(rrectPath(sx, sy, sw, sh, 12f), color = Color(0xFF080C18))
    drawPath(rrectPath(sx, sy, sw, sh, 12f), color = accent.copy(alpha = .30f), style = Stroke(width = 1.6f))
    drawLine(Color.White.copy(alpha = .08f), Offset(sx, sy + sh / 2f), Offset(sx + sw, sy + sh / 2f), 1f)
    for (osc in 0 until 2) {
        val f = if (osc == 0) 3f + waveA * 4f else (3f + waveB * 4f) * (1f + detune * .12f)
        val path = Path()
        for (k in 0..sw.toInt()) {
            val u = k / sw
            val y = sy + sh / 2f - sin(u * f * PI.toFloat() * 2f + t * 2f * osc) * sh * .34f
            if (k == 0) path.moveTo(sx + k, y) else path.lineTo(sx + k, y)
        }
        drawPath(path, color = (if (osc == 0) accent else Color.White).copy(alpha = .8f), style = Stroke(width = 1.8f))
    }
    val fxp = 60f; val fyp = 230f; val fwp = 420f; val fhp = 90f
    val curve = Path()
    for (k in 0..fwp.toInt()) {
        val u = k / fwp
        val g = 1f / (1f + exp((u - cutoff) * 14f))
        val spike = reso * .5f * exp(-((u - cutoff) / .05f).pow(2f))
        val y = fyp + fhp - (g + spike) * fhp
        if (k == 0) curve.moveTo(fxp + k, y) else curve.lineTo(fxp + k, y)
    }
    drawPath(curve, color = accent, style = Stroke(width = 2.4f))
    val ex = 520f; val ey = 230f; val ew = 240f; val eh = 80f
    val env = Path().apply {
        moveTo(ex, ey + eh)
        lineTo(ex + atk * ew * .25f, ey)
        lineTo(ex + (atk * .25f + dec * .25f) * ew, ey + eh * (1f - sus))
        lineTo(ex + (atk * .25f + dec * .25f + .3f) * ew, ey + eh * (1f - sus))
        lineTo(ex + ew, ey + eh)
    }
    drawPath(env, color = Color.White.copy(alpha = .55f), style = Stroke(width = 2f))
    drawRect(accent.copy(alpha = .18f + .2f * rel), topLeft = Offset(ex, ey), size = Size(ew, eh))
}

// ═══════════════════════════════════════════════════════════════════════════
//  GENERIC (5-14) — exact background + accent + pulse + rail if instrument
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.sceneGeneric(def: FxDef, v: FloatArray, t: Float, sz: Size, mini: Boolean) {
    val r = sz.height * (.35f + .04f * sin(t * 2f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(def.accent.copy(alpha = .35f), Color.Transparent),
            center = Offset(sz.width / 2f, sz.height * .55f), radius = r * 1.5f,
        ),
        radius = r * 1.5f, center = Offset(sz.width / 2f, sz.height * .55f),
    )
    drawCircle(
        def.accent.copy(alpha = .8f), radius = r,
        center = Offset(sz.width / 2f, sz.height * .55f), style = Stroke(width = 1.5f),
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════════════════

private fun formatParam(p: FxParam, v: Float): String {
    val raw = p.min + (p.max - p.min) * v
    return String.format("%.${p.decimals}f%s", raw, p.unit)
}

// ═══════════════════════════════════════════════════════════════════════════
//  ADD OVERLAY
// ═══════════════════════════════════════════════════════════════════════════

private val FX_FILTERS = listOf("All", "Dynamics", "EQ", "Space", "Amps", "Voices")
private val INS_FILTERS = listOf("All", "Keys", "Guitar", "Winds", "Strings", "Synth")

@Composable
private fun FxAddOverlay(s: FxUiState, c: FxController) {
    val filters = if (s.addTab == AddTab.EFFECTS) FX_FILTERS else INS_FILTERS
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { c.closeAdd() },
    )
    Column(
        Modifier.fillMaxSize().padding(16.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF232A3D), Color(0xFF141926))))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(20.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FxChip(if (s.addTab == AddTab.EFFECTS) "EFFECTS" else "Effects") {
                s.addTab = AddTab.EFFECTS; s.addFilter = "All"
            }
            Spacer(Modifier.width(8.dp))
            FxChip(if (s.addTab == AddTab.INSTRUMENT) "INSTRUMENT" else "Instrument") {
                s.addTab = AddTab.INSTRUMENT; s.addFilter = "All"
            }
            Spacer(Modifier.weight(1f))
            FxChip("✕") { c.closeAdd() }
        }
        LazyRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filters) { f ->
                val sel = s.addFilter == f
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp))
                        .background(if (sel) Color(0xFFF2F4FA) else Color.White.copy(alpha = .05f))
                        .clickable { s.addFilter = f }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(f, color = if (sel) Color(0xFF0B0F1C) else INK, fontSize = 10.sp, fontWeight = FontWeight(700))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val filtered = FX_DEFS
            .filter { (s.addTab == AddTab.INSTRUMENT) == it.isInstrument() }
            .filter { matchesFilter(s.addFilter, it) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { def ->
                val on = s.act.contains(def.id)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (on) .14f else .05f))
                        .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(12.dp))
                        .clickable { c.add(def.id); c.closeAdd() }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painterResource(def.icon), null,
                        Modifier.size(28.dp).alpha(.85f),
                        colorFilter = ColorFilter.tint(Color(0xFFCFD6E6)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(def.title, color = Color.White, fontSize = 13.sp)
                        Text(def.type, color = Color.White.copy(alpha = .5f), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun matchesFilter(f: String, def: FxDef): Boolean = when (f) {
    "All" -> true
    "Dynamics" -> def.id == 3 || def.id == 4
    "EQ" -> def.id == 2
    "Space" -> def.id == 0 || def.id == 1
    "Amps" -> def.id == 10 || def.id == 11
    "Voices" -> def.id == 13
    "Keys" -> def.id == 5 || def.id == 6
    "Guitar" -> def.id == 9
    "Winds" -> def.id == 7 || def.id == 12
    "Strings" -> def.id == 8
    "Synth" -> def.id == 14
    else -> false
}

// ═══════════════════════════════════════════════════════════════════════════
//  SOUNDFONT (stand-in)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FxSoundfontWindow(s: FxUiState, c: FxController) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { s.sfOpen = false },
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141926))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Soundfonts", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight(700))
            Spacer(Modifier.weight(1f))
            FxChip("✕") { s.sfOpen = false }
        }
        Spacer(Modifier.height(12.dp))
        listOf("Grand Piano", "Alto Sax", "Violin", "Nylon Guitar", "Trumpet", "Choir Aahs").forEach { name ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    .clickable { s.soundfont = name; s.sfOpen = false },
            ) {
                Text(if (name == s.soundfont) "● $name" else "○ $name", color = INK, fontSize = 14.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  TOAST + UNDO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FxToastAndUndo(s: FxUiState, c: FxController) {
    val toast = s.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            if (s.toast == toast) s.toast = null
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (toast != null || s.undo != null) {
            Row(
                Modifier.padding(bottom = 20.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2A3042))
                    .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(toast ?: "", color = INK, fontSize = 12.sp)
                if (s.undo != null) {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Undo", color = Color(0xFFF2D58A), fontSize = 12.sp,
                        fontWeight = FontWeight(700),
                        modifier = Modifier.clickable { c.undoRemove() },
                    )
                }
            }
        }
    }
}

````

---

## 9. Captures de rendu

Lisibles depuis le dépôt public sous `app/src/test/screenshots/fxpreview2/` :

| Fichier | Vue |
|---|---|
| `01_space_open.png` | Effet 0, fenêtre ouverte |
| `02_space_phase.png` | Effet 0, autre phase d'animation |
| `03_echo_open.png` | Effet 1 |
| `04_tone_open.png` | Effet 2 |
| `05_grip_open.png` | Effet 3 |
| `06_ceiling_open.png` | Effet 4 |
| `07_amp_open.png` | Effet 10 |
| `08_piano_open.png` | Effet 5 |
| `09_synth_open.png` | Effet 14 |
| `10_rack.png` | Le rack |
| `11_add_overlay.png` | La page d'ajout |

Résolution native : 2268 × 943 (densité 440 dpi). Pour comparer à une référence,
recadrer sur 825 × 343 puis redimensionner.

---

## 10. Fichiers du paquet non reproduits ici

Binaires, DSP moteur, ou inutiles pour dessiner l'interface :

```
aether-fx-package.zip
  Makefile, gen_kotlin.cpp, fx_test.cpp, fx_test2.cpp
  fx_registry.h, fx_core.h, fx_chain.h, fx_mixer.h, fx_match.h, fx_shaper.h, fx_source.h
  fx_space.h, fx_echo.h, fx_tone.h, fx_grip.h, fx_ceiling.h, fx_organ.h
  fx_synth.h, fx_voices.h, fx_amp.h, fx_bass.h
  ic_fx_*.svg   (sources vectorielles des 15 icônes)

fx-screens-spec.zip
  reference_source_fx_window.html   (VIDE — 0 octet, défaut du paquet)
  references/ref-*.png              (33 images de référence)
```

Les en-têtes `fx_*.h` portent le DSP, pas l'interface : nécessaires pour faire
**sonner** un effet, inutiles pour dessiner la fenêtre.

---

## 11. Où trouver les fichiers bruts

```
https://github.com/amiel767/NewKeys-
```

```bash
curl -L https://raw.githubusercontent.com/amiel767/NewKeys-/main/app/assets/aether-fx-package.zip -o aether-fx-package.zip
curl -L https://raw.githubusercontent.com/amiel767/NewKeys-/main/app/assets/fx-screens-spec.zip  -o fx-screens-spec.zip
```

---

## 12. Résumé pour un agent pressé

1. Ouvre `fx-window-preview.html` (§2). C'est le rendu cible.
2. Prends les mesures dans `fx_layout_spec.json` (§3), pas à l'œil.
3. Prends les paramètres dans `FxRegistry.kt` (§1) — ne les invente pas.
4. Reprends les scènes de `FxScenes.kt` / `FxInstrumentScenes.kt` (§6).
5. Reprends la coquille de `FxWindowHost.kt` + `FxOpenPanel.kt` + `FxKnob.kt` (§7),
   ou pars de `FxWindowBlocFull.kt` (§8) pour un fichier unique.
6. Respecte les 9 règles de §0 — chacune a coûté une passe de débogage.
7. Valide hors appareil avec Roborazzi (§8) et compare aux PNG (§9).
