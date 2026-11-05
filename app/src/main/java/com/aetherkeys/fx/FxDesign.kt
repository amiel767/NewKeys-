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
 * est NORMALISÉ (0..1), l'affichage vaut `min + def * (max - min)`.
 *
 * [main] : le réglage porté par le GROS potard central. Un seul par effet ; les
 * autres sont des contrôles secondaires. Le choix est éditorial — la largeur
 * stéréo est ce qui se règle à l'oreille en premier sur une reverb. */
data class FxParamD(
    val name: String,
    val key: String,
    val def: Float,
    val min: Float,
    val max: Float,
    val unit: String,
    val main: Boolean = false,
)

/**
 * La palette d'un preset : elle porte à la fois le cœur du gros potard et le
 * FOND de la page, qui change de preset en preset.
 *
 * [bgA] et [bgB] sont les deux teintes fusionnées du fond — un dégradé diagonal
 * entre elles, pas un aplat. C'est la demande : « violet noir », « orange rouge
 * (magma) », « noir bleu ciel ». Elles restent sombres : le fond est un fond, il
 * ne doit pas concurrencer les contrôles.
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
    val bgA: Color,
    val bgB: Color,
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
        core: String, rim: String, bgA: String, bgB: String,
        h2: Float = h, multi: Boolean = false,
    ) = FxPalette(
        h, dh, s, l, h2, multi,
        Color(android.graphics.Color.parseColor(core)),
        Color(android.graphics.Color.parseColor(rim)),
        Color(android.graphics.Color.parseColor(bgA)),
        Color(android.graphics.Color.parseColor(bgB)),
    )

    /**
     * Space : dix réglages, huit presets, une palette par preset.
     *
     * Les sept premiers pilotent la reverb (taille, queue, pré-délai, diffusion,
     * largeur, mix, amortissement) ; Lo Cut, Mod Rate et Mod Depth complètent la
     * liste. Un seul réglage porte [main] : c'est lui qu'affiche le GROS potard
     * central.
     */
    val SPACE = FxInfoD(
        id = 0, name = "Æther Space", type = "Reverb", family = "Space", kind = FxKindD.AUDIO,
        accent = Color(0xFFA99AD8), space = true,
        params = listOf(
            // Les SEPT premiers suivent l'ordre du moteur (`FxRegistry` id 0) :
            // `setValue(i)` pousse l'index `i` tel quel, donc cet ordre est un
            // contrat. Ne pas le réarranger pour faire joli.
            FxParamD("Size", "size", .62f, 0f, 100f, " %"),
            FxParamD("Decay", "decay", .7f, .4f, 8f, " s"),
            FxParamD("Pre-delay", "pre", .2f, 0f, 120f, " ms"),
            FxParamD("Damp", "damp", .45f, 1f, 12f, " kHz"),
            FxParamD("Width", "width", .8f, 0f, 100f, " %", main = true),
            FxParamD("Mix", "mix", .35f, 0f, 100f, " %"),
            FxParamD("Diffuse", "diff", .5f, 0f, 100f, " %"),
            // Ceux-ci n'ont pas d'équivalent moteur : ils se règlent et se
            // mémorisent à l'écran, et sont ignorés par le push (index >= 7).
            FxParamD("Lo Cut", "lowcut", .25f, 20f, 400f, " Hz"),
            FxParamD("Mod Rate", "modrate", .30f, 0f, 5f, " Hz"),
            FxParamD("Mod Depth", "moddepth", .35f, 0f, 100f, " %"),
        ),
        // HUIT presets, dans l'ORDRE du moteur : `pickPreset` retrouve l'index
        // moteur par `indexOf`, donc l'ordre de cette table est un contrat.
        presets = mapOf(
            "Hall" to listOf(.62f, .70f, .20f, .45f, .80f, .35f, .50f, .30f, .30f, .35f),
            "Plate" to listOf(.42f, .52f, .10f, .70f, .90f, .30f, .72f, .35f, .26f, .25f),
            "Room" to listOf(.28f, .30f, .06f, .55f, .60f, .25f, .35f, .20f, .18f, .12f),
            "Cathedral" to listOf(.95f, .95f, .35f, .30f, 1f, .45f, .80f, .45f, .55f, .55f),
            "Shimmer" to listOf(.70f, .85f, .25f, .20f, 1f, .50f, .90f, .40f, .65f, .65f),
            "Chamber" to listOf(.38f, .42f, .10f, .55f, .70f, .28f, .45f, .25f, .22f, .20f),
            "Bloom" to listOf(.80f, .80f, .30f, .35f, .95f, .42f, 1f, .35f, .60f, .60f),
            "Infinite" to listOf(1f, 1f, .40f, .25f, 1f, .60f, .95f, .50f, .70f, .70f),
        ),
        // Le FOND change avec le preset : deux teintes fusionnées en diagonale.
        // Le cœur et le liseré du gros potard suivent la même paire.
        palettes = mapOf(
            "Hall" to pal(272f, 75f, 48f, 74f, "#E7D9FF", "#22103A", "#120A20", "#2A1244"),
            "Plate" to pal(210f, 40f, 62f, 76f, "#EAF3FF", "#16283C", "#0A1018", "#1B2C40"),
            "Room" to pal(205f, 44f, 34f, 68f, "#CFE8FF", "#0E2336", "#0A0F18", "#14324A"),
            "Cathedral" to pal(258f, 60f, 56f, 66f, "#E4DAFF", "#1A0F38", "#0B0A1C", "#241244"),
            "Shimmer" to pal(312f, 90f, 66f, 76f, "#FFF0FB", "#2A1030", "#1A0A22", "#2A1030", h2 = 186f, multi = true),
            "Chamber" to pal(165f, 40f, 40f, 70f, "#D6FFF0", "#0C2A1E", "#07130F", "#123528"),
            "Bloom" to pal(18f, 70f, 58f, 74f, "#FFC98A", "#3E1206", "#1A0805", "#3C1408"),
            "Infinite" to pal(214f, 50f, 30f, 84f, "#FFFFFF", "#0E1626", "#06070C", "#101A2E"),
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
