package com.aetherkeys.fx

import androidx.compose.ui.graphics.Color

/**
 * Les DEUX effets qui existent, tels que la maquette les dessine.
 *
 * La maquette montre cinq effets audio et cinq MIDI : c'est une vitrine de
 * design, pas l'inventaire du moteur. Ici il n'y a que ce qui existe — Space et
 * Arpeggiator — pour que la fenêtre ne propose jamais un effet qu'on ne sait pas
 * faire sonner.
 *
 * POURQUOI CE FICHIER EXISTE À CÔTÉ DE `FxRegistry`. Le registre est GÉNÉRÉ
 * depuis `fx_registry.h` : ses paramètres sont ceux du moteur, pas ceux de la
 * maquette. Space y a sept réglages, la maquette en dessine dix (Low Cut, Hi
 * Shelf et Mod sont des réglages de reverb qu'on veut). On ne touche pas au
 * registre généré : on porte la maquette ici, et c'est ce catalogue que la
 * fenêtre rend.
 */

/** Un réglage. [def], [min] et [max] sont dans l'ordre de la maquette : [def]
 * est NORMALISÉ (0..1), l'affichage vaut `min + def * (max - min)`. */
data class FxParamD(
    val name: String,
    val key: String,
    val def: Float,
    val min: Float,
    val max: Float,
    val unit: String,
)

/**
 * La palette d'une orbe. Chaque preset porte la sienne : c'est elle qui donne
 * sa couleur à la nébuleuse, pas un accent unique.
 *
 * [h2] et [multi] : quand un preset mélange DEUX teintes (Shimmer magenta +
 * cyan, Bloom rose + violet…), le mélange se fait en RVB. Mélanger en degrés
 * ferait traverser le cercle chromatique à l'envers — 30 et 268 donneraient un
 * vert.
 */
data class FxPalette(
    val h: Float,
    val dh: Float,
    val s: Float,
    val l: Float,
    val h2: Float = h,
    val multi: Boolean = false,
    val core: Color,
    val rim: Color,
)

/** Le genre d'effet : un effet audio, ou un effet MIDI. */
enum class FxKindD { AUDIO, MIDI }

/** Un effet du catalogue de design. */
data class FxInfoD(
    val id: Int,
    val name: String,
    val type: String,
    val family: String,
    val kind: FxKindD,
    val accent: Color,
    /** Vrai pour Space : sa vignette est une orbe, pas une courbe. */
    val space: Boolean = false,
    val params: List<FxParamD> = emptyList(),
    val presets: Map<String, List<Float>> = emptyMap(),
    val palettes: Map<String, FxPalette> = emptyMap(),
) {
    /** Le premier preset, celui qu'on affiche à l'ouverture. */
    val firstPreset: String? get() = presets.keys.firstOrNull()
}

/** Le catalogue de design. */
object FxDesign {

    const val MAX_AUDIO = 3
    const val MAX_MIDI = 1
    const val SLOTS = MAX_AUDIO + MAX_MIDI

    private fun pal(
        h: Float, dh: Float, s: Float, l: Float,
        core: String, rim: String, h2: Float = h, multi: Boolean = false,
    ) = FxPalette(h, dh, s, l, h2, multi, Color(android.graphics.Color.parseColor(core)),
        Color(android.graphics.Color.parseColor(rim)))

    /**
     * Space : dix réglages, seize presets, une palette par preset.
     *
     * Les sept premiers réglages pilotent l'orbe (taille, queue, pré-delai,
     * amortissement, largeur, mix, diffusion) ; les trois derniers sont des
     * réglages de reverb qu'on attend et que la maquette ajoute.
     */
    val SPACE = FxInfoD(
        id = 0, name = "Æther Space", type = "Reverb", family = "Space", kind = FxKindD.AUDIO,
        accent = Color(0xFFA99AD8), space = true,
        params = listOf(
            FxParamD("Size", "size", .62f, 0f, 100f, " %"),
            FxParamD("Decay", "decay", .7f, .4f, 8f, " s"),
            FxParamD("Pre-delay", "pre", .2f, 0f, 120f, " ms"),
            FxParamD("Damp", "damp", .45f, 1f, 12f, " kHz"),
            FxParamD("Width", "width", .8f, 0f, 100f, " %"),
            FxParamD("Mix", "mix", .35f, 0f, 100f, " %"),
            FxParamD("Diffuse", "diff", .5f, 0f, 100f, " %"),
            FxParamD("Low Cut", "lowcut", .25f, 20f, 400f, " Hz"),
            FxParamD("Hi Shelf", "shelf", .55f, -12f, 6f, " dB"),
            FxParamD("Mod", "mod", .3f, 0f, 100f, " %"),
        ),
        presets = mapOf(
            "Hall" to listOf(.62f, .70f, .20f, .45f, .80f, .35f, .50f, .30f, .50f, .35f),
            "Plate" to listOf(.40f, .50f, .10f, .70f, .90f, .30f, .70f, .35f, .60f, .25f),
            "Room" to listOf(.25f, .30f, .05f, .50f, .60f, .25f, .30f, .20f, .40f, .15f),
            "Cathedral" to listOf(.95f, .95f, .35f, .30f, 1f, .45f, .80f, .45f, .55f, .55f),
            "Shimmer" to listOf(.70f, .85f, .25f, .20f, 1f, .50f, .90f, .40f, .70f, .65f),
            "Chamber" to listOf(.35f, .40f, .08f, .55f, .70f, .28f, .45f, .25f, .45f, .20f),
            "Bloom" to listOf(.80f, .80f, .30f, .35f, .95f, .42f, 1f, .35f, .65f, .60f),
            "Infinite" to listOf(1f, 1f, .40f, .25f, 1f, .60f, .95f, .50f, .50f, .70f),
            "Ambience" to listOf(.55f, .88f, .30f, .60f, .95f, .28f, .85f, .40f, .45f, .45f),
            "Spring" to listOf(.30f, .45f, .02f, .75f, .55f, .40f, .25f, .15f, .70f, .80f),
            "Dark Hall" to listOf(.78f, .82f, .28f, .22f, .85f, .38f, .65f, .55f, .35f, .30f),
            "Bright Hall" to listOf(.60f, .62f, .16f, .78f, .88f, .32f, .55f, .20f, .68f, .40f),
            "Vocal Plate" to listOf(.38f, .46f, .06f, .66f, .95f, .30f, .78f, .42f, .58f, .20f),
            "Drum Room" to listOf(.28f, .26f, .01f, .42f, .70f, .24f, .35f, .12f, .46f, .10f),
            "Chorus Verb" to listOf(.66f, .74f, .22f, .58f, 1f, .40f, .72f, .30f, .62f, .85f),
            "Gated" to listOf(.44f, .22f, .00f, .68f, .60f, .34f, .30f, .25f, .52f, .00f),
        ),
        // Chaque preset emprunte ses couleurs à une nébuleuse réelle : la teinte
        // principale au cœur, la teinte secondaire aux volutes, le bord sombre à
        // la poussière.
        palettes = mapOf(
            "Hall" to pal(268f, 75f, 48f, 74f, "#eee8ff", "#1d1a38"),
            "Plate" to pal(196f, 40f, 62f, 76f, "#f0fbff", "#0f2530"),
            "Room" to pal(158f, 34f, 34f, 68f, "#eafaf4", "#12241f"),
            "Cathedral" to pal(246f, 60f, 56f, 66f, "#e9e4ff", "#161034"),
            "Shimmer" to pal(312f, 90f, 66f, 76f, "#fff0fb", "#2a1030", h2 = 186f, multi = true),
            "Chamber" to pal(172f, 40f, 40f, 70f, "#eefffb", "#0e2a28"),
            "Bloom" to pal(328f, 70f, 58f, 74f, "#ffeef6", "#2c1030", h2 = 268f, multi = true),
            "Infinite" to pal(210f, 50f, 30f, 84f, "#ffffff", "#101a2e"),
            "Ambience" to pal(224f, 44f, 36f, 64f, "#e6ecff", "#0d1424"),
            "Spring" to pal(34f, 26f, 62f, 70f, "#fff4e2", "#2a1a0c"),
            "Dark Hall" to pal(22f, 16f, 68f, 58f, "#ffd9a8", "#07050e", h2 = 268f, multi = true),
            "Bright Hall" to pal(48f, 30f, 56f, 74f, "#fff8e2", "#2a2410", h2 = 118f, multi = true),
            "Vocal Plate" to pal(42f, 26f, 52f, 74f, "#fff2dc", "#241c0e", h2 = 152f, multi = true),
            "Drum Room" to pal(186f, 40f, 46f, 66f, "#e8fbff", "#0c2224", h2 = 138f, multi = true),
            "Chorus Verb" to pal(184f, 40f, 60f, 74f, "#eaffff", "#0c2430", h2 = 300f, multi = true),
            "Gated" to pal(318f, 30f, 64f, 62f, "#ffe4f4", "#26081c", h2 = 26f, multi = true),
        ),
    )

    /**
     * L'Arpeggiator. Il n'a pas de réglages continus : ses commandes sont la
     * subdivision, le sens, le nombre de pas, et la grille des hauteurs. Sa
     * vignette montre ses pas, pas une courbe.
     */
    val ARP = FxInfoD(
        id = 101, name = "Arpeggiator", type = "Arp", family = "Pattern", kind = FxKindD.MIDI,
        accent = Color(0xFF8FB2E8),
    )

    /** Les effets qu'on peut ajouter, par genre. */
    val audio: List<FxInfoD> = listOf(SPACE)
    val midi: List<FxInfoD> = listOf(ARP)

    fun list(kind: FxKindD): List<FxInfoD> = if (kind == FxKindD.MIDI) midi else audio

    /** Les subdivisions de l'arpégiateur. */
    val SUBS = listOf("1/4", "1/8", "1/16", "1/32")

    /** Les sens de parcours. */
    val STYLES = listOf("up", "down", "updown", "random")

    /**
     * L'affichage d'un réglage : `min + v * (max - min)`, puis l'unité. Les
     * secondes gardent une décimale, les autres valeurs sont entières.
     */
    fun fmt(v: Float, p: FxParamD): String {
        val x = p.min + v * (p.max - p.min)
        val txt = if (p.unit.trim() == "s") {
            val r = kotlin.math.round(x * 10f) / 10f
            if (r == r.toInt().toFloat()) "${r.toInt()}" else "$r"
        } else kotlin.math.round(x).toInt().toString()
        return txt + p.unit
    }

    /** La durée affichée sur la pastille de Space : `.4 + decay * 7.6` secondes. */
    fun decaySeconds(v: Float): String = ((.4f + v * 7.6f) * 10f).toInt().let { "${it / 10}.${it % 10}" }
}
