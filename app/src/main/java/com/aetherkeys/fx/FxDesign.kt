package com.aetherkeys.fx

import androidx.compose.ui.graphics.Color

/**
 * Le catalogue de design de la fenêtre FX.
 *
 * DEUX EFFETS, ceux qui existent : Space et l'Arpeggiator. La maquette en montre
 * dix en vitrine ; ici il n'y a que ce qui sonne, pour que la fenêtre ne propose
 * jamais un effet qu'on ne sait pas faire.
 *
 * POURQUOI CE FICHIER EXISTE À CÔTÉ DE `FxRegistry`. Le registre est GÉNÉRÉ depuis
 * `fx_registry.h` : il porte les paramètres du MOTEUR. Ici on porte la MAQUETTE —
 * les groupes de contrôles, la rampe de cinq couleurs par preset, le style du
 * titre. Les deux tables doivent rester d'accord sur l'ORDRE des paramètres :
 * l'interface pousse une valeur normalisée par INDEX, donc un décalage enverrait
 * silencieusement un réglage sur le mauvais paramètre DSP.
 *
 * `FxDesignTest.leCatalogueSuitLeRegistreMoteur` vérifie cet accord, et
 * `tools/design/sync_space_registry.py` régénère les deux miroirs Kotlin.
 */

/** Un réglage. [def], [min] et [max] sont NORMALISÉS (0..1) ; l'affichage vaut
 * `min + def * (max - min)`.
 *
 * [main] : le réglage porté par le GROS potard central. Un seul par effet. */
data class FxParamD(
    val name: String,
    val key: String,
    val def: Float,
    val min: Float,
    val max: Float,
    val unit: String,
    val decimals: Int = 0,
    val main: Boolean = false,
)

/**
 * La palette d'un preset : cinq couleurs, plus le style du titre.
 *
 * [colors] est la rampe de la référence — c'est elle qui porte l'ambiance du
 * preset, et c'est elle que la page d'effet emploie pour ses anneaux, son fond et
 * ses groupes. [core] et [rim] en dérivent, comme le fond [bgA]/[bgB].
 *
 * [titleSize], [titleSpacing] et [titleWeight] sont le STYLE DU TITRE, propre au
 * preset : le lettrage change de corps, d'interlettre et de graisse avec
 * l'ambiance. Le mot ne change pas — c'est sa mise en forme qui suit le preset.
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
    val titleSize: Float = 30f,
    val titleSpacing: Float = 1.0f,
    val titleWeight: Int = 700,
    /** La rampe de cinq couleurs du preset. */
    val colors: List<Color> = listOf(core, rim, bgA, bgB),
)

/** Le genre d'effet : un effet audio, ou un effet MIDI. */
enum class FxKindD { AUDIO, MIDI }

/** Un groupe de contrôles : son titre, ses clés, et la largeur de son panneau. */
data class FxGroup(val title: String, val keys: List<String>, val width: Float)

/** Un effet du catalogue de design. */
data class FxInfoD(
    val id: Int,
    val name: String,
    val type: String,
    val family: String,
    val kind: FxKindD,
    val accent: Color,
    /** Vrai pour Space : sa page est l'écran de reverb, pas la grille de cellules. */
    val space: Boolean = false,
    val params: List<FxParamD> = emptyList(),
    val presets: Map<String, List<Float>> = emptyMap(),
    val palettes: Map<String, FxPalette> = emptyMap(),
    /**
     * Les GROUPES de contrôles, tels que la référence les dispose : titre, clés,
     * largeur du panneau. C'est la disposition de la maquette, pas un découpage
     * inventé — DAMPING à quatre réglages, les autres à deux.
     */
    val groups: List<FxGroup> = emptyList(),
) {
    /** Le premier preset, celui qu'on affiche à l'ouverture. */
    val firstPreset: String? get() = presets.keys.firstOrNull()

    /** Le réglage porté par le gros potard. */
    val mainParam: FxParamD get() = params.firstOrNull { it.main } ?: params.first()

    /** L'index du réglage principal. */
    val mainIndex: Int get() = params.indexOfFirst { it.main }.let { if (it < 0) 0 else it }

    /** Les réglages rangés par clé, pour que les groupes s'y réfèrent par nom. */
    val byKey: Map<String, FxParamD> get() = params.associateBy { it.key }

    /** Les réglages hors groupes : ceux de la colonne de gauche de la page. */
    val looseParams: List<FxParamD>
        get() {
            val grouped = groups.flatMap { it.keys }.toSet()
            return params.filter { it.key !in grouped }
        }
}

/** Le catalogue de design. */
object FxDesign {

    const val MAX_AUDIO = 3
    const val MAX_MIDI = 1
    const val SLOTS = MAX_AUDIO + MAX_MIDI

    /** Le fond de scène de la maquette : c'est vers lui que se fond une couleur. */
    private val STAGE = Color(0xFF141414)

    /**
     * Un littéral `#RRGGBB` lu SANS passer par `android.graphics`. Le catalogue
     * est ainsi une pure donnée Kotlin : il se teste sur la JVM, sans Robolectric,
     * et un test unitaire peut l'interroger sans monter d'Android.
     */
    private fun hex(s: String): Color {
        val v = s.removePrefix("#").toLong(16)
        return Color(0xFF000000L or v)
    }

    /** Fusionne deux couleurs, `t` = part de la seconde. */
    private fun mix(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t,
        a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t,
        a.alpha + (b.alpha - a.alpha) * t,
    )

    /** Teinte, saturation, valeur d'une couleur. */
    private fun hsv(c: Color): FloatArray {
        val mx = maxOf(c.red, c.green, c.blue)
        val mn = minOf(c.red, c.green, c.blue)
        val d = mx - mn
        val h = when {
            d < 1e-6f -> 0f
            mx == c.red -> (60f * ((c.green - c.blue) / d) + 360f) % 360f
            mx == c.green -> 60f * ((c.blue - c.red) / d) + 120f
            else -> 60f * ((c.red - c.green) / d) + 240f
        }
        return floatArrayOf(h, if (mx <= 0f) 0f else d / mx, mx)
    }

    /**
     * Construit la palette d'un preset depuis sa rampe de cinq couleurs.
     *
     * Le fond est la rampe fondue vers le noir de scène — jamais un aplat, jamais
     * une couleur claire : le fond reste un fond, il ne concurrence pas les
     * contrôles. Les teintes [FxPalette.h] et [FxPalette.h2] sont celles des deux
     * voiles colorés de la page ; elles viennent des extrémités de la rampe.
     */
    private fun pal(
        ramp: List<String>,
        titleSize: Float = 30f,
        titleSpacing: Float = 1f,
        titleWeight: Int = 700,
    ): FxPalette {
        val c = ramp.map(::hex)
        val a = hsv(c[0])
        val b = hsv(c[4])
        return FxPalette(
            h = a[0], dh = ((b[0] - a[0] + 540f) % 360f) - 180f,
            s = a[1] * 100f, l = a[2] * 100f,
            h2 = b[0], multi = true,
            core = c[0], rim = c[1],
            bgA = mix(c[0], STAGE, .88f), bgB = mix(c[2], STAGE, .93f),
            titleSize = titleSize, titleSpacing = titleSpacing, titleWeight = titleWeight,
            colors = c,
        )
    }

    /**
     * LES SIX RÉGLAGES DE LA CARTE « SLOTS ». Ce sont ceux qui donnent son
     * caractère au preset : sa queue, sa taille, sa tenue, ses deux champs, sa
     * modulation. Trois colonnes sur deux rangées dans la vignette — la carte
     * fait 208 unités de large, trois potards y tiennent, pas plus.
     */
    val CARD_KEYS = listOf("decay", "size", "att", "early", "late", "depth")

    /**
     * Les GROUPES de la référence : DAMPING à quatre réglages, puis SHAPE, DIFF,
     * MOD et EQ à deux. Les largeurs sont celles de la maquette (160 pour le
     * panneau à quatre, 84 pour les autres).
     */
    val SPACE_GROUPS = listOf(
        FxGroup("DAMPING", listOf("hf", "hs", "bf", "bm"), 160f),
        FxGroup("SHAPE", listOf("size", "att"), 84f),
        FxGroup("DIFF", listOf("early", "late"), 84f),
        FxGroup("MOD", listOf("rate", "depth"), 84f),
        FxGroup("EQ", listOf("hc", "lc"), 84f),
    )

    /**
     * Space : QUINZE réglages, dans l'ORDRE du moteur.
     *
     * L'ordre est un contrat, pas une mise en page : `setValue(i)` pousse l'index
     * `i` tel quel au moteur. La liste suit donc `fx_registry.h` (FX_SPACE) :
     * Mix, Pre-delay, Decay, HighFreq, HighShelf, BassFreq, BassMult, Size,
     * Attack, Early, Late, Rate, Depth, HighCut, LowCut. Ne pas la réarranger
     * pour faire joli — `FxDesignTest` le refuse.
     *
     * Le gros potard porte DECAY : c'est le réglage qu'on tourne en premier sur
     * une reverb, et la référence en fait le centre de son écran.
     */
    val SPACE = FxInfoD(
        id = 0, name = "Æther Space", type = "Reverb", family = "Space", kind = FxKindD.AUDIO,
        accent = Color(0xFFA99AD8), space = true,
        params = listOf(
            FxParamD("Mix", "mix", .34f, 0f, 100f, "%", 0),
            FxParamD("Pre-delay", "pre", .10f, 0f, 200f, "ms", 0),
            FxParamD("Decay", "decay", .1304f, .1f, 30f, "s", 2, main = true),
            FxParamD("HighFreq", "hf", .3333f, 1000f, 16000f, "Hz", 0),
            FxParamD("HighShelf", "hs", .50f, -24f, 0f, "dB", 1),
            FxParamD("BassFreq", "bf", .6667f, 100f, 1000f, "Hz", 0),
            FxParamD("BassMult", "bm", .40f, .5f, 3f, "X", 2),
            FxParamD("Size", "size", 1f, 0f, 100f, "%", 0),
            FxParamD("Attack", "att", .50f, 0f, 100f, "%", 0),
            FxParamD("Early", "early", 1f, 0f, 100f, "%", 0),
            FxParamD("Late", "late", 1f, 0f, 100f, "%", 0),
            FxParamD("Rate", "rate", .2455f, .1f, 10f, "Hz", 2),
            FxParamD("Depth", "depth", .38f, 0f, 100f, "%", 0),
            FxParamD("HighCut", "hc", .3684f, 1000f, 20000f, "Hz", 0),
            FxParamD("LowCut", "lc", 0f, 10f, 500f, "Hz", 0),
        ),
        // DIX presets, les valeurs de la référence, normalisées dans l'ordre moteur.
        // L'ordre de cette table est aussi un contrat : `pickPreset` retrouve
        // l'index moteur par `indexOf`.
        presets = mapOf(
            "Shimmer" to listOf(.38f, .14f, .2467f, .5333f, .50f, .6667f, .40f, .82f, .62f, .70f, .80f, .1111f, .46f, .7895f, .3469f),
            "Chamber" to listOf(.30f, .07f, .0862f, .3667f, .50f, .4444f, .46f, .48f, .40f, .60f, .55f, .0505f, .22f, .5789f, .1633f),
            "Room" to listOf(.24f, .03f, .0336f, .28f, .75f, .3556f, .32f, .30f, .55f, .80f, .40f, .0253f, .12f, .4211f, .1020f),
            "Hall" to listOf(.34f, .10f, .1304f, .3333f, .50f, .6667f, .40f, 1f, .50f, 1f, 1f, .2455f, .38f, .3684f, 0f),
            "Plate" to listOf(.32f, .05f, .0706f, .6667f, .8333f, .2778f, .20f, .55f, .72f, .90f, .70f, .0707f, .18f, .7368f, .2653f),
            "Cathedral" to listOf(.42f, .24f, .4181f, .2133f, .25f, .7778f, .64f, .96f, .25f, .50f, .90f, .0303f, .30f, .3158f, .2245f),
            "Spring" to listOf(.28f, .02f, .0572f, .44f, .6667f, .3889f, .32f, .40f, .80f, .70f, .35f, .3131f, .52f, .5f, .2857f),
            "Cloud" to listOf(.48f, .30f, .2980f, .1733f, .1667f, .6111f, .52f, .90f, .18f, .40f, 1f, .0808f, .64f, .2895f, .2041f),
            "Infinite" to listOf(.45f, .40f, .6980f, .2f, .1667f, .6667f, .72f, 1f, .3f, .5f, 1f, .0707f, .70f, .4737f, .4082f),
            "Bloom" to listOf(.42f, .30f, .2800f, .24f, .25f, .6111f, .56f, .95f, .35f, 1f, .6f, .0505f, .60f, .2632f, .3061f),
        ),
        // Les rampes de cinq couleurs de la référence, et le style du titre par preset.
        palettes = mapOf(
            "Shimmer" to pal(listOf("#FF3FA4", "#FF6FB8", "#E07BFF", "#9D7CFF", "#6C8CFF"),
                titleSize = 33f, titleSpacing = 4.0f, titleWeight = 500),
            "Chamber" to pal(listOf("#27E0D4", "#2BC4E8", "#3C9CF0", "#5D7CFF", "#7C6CFF"),
                titleSize = 26f, titleSpacing = 1.0f, titleWeight = 700),
            "Room" to pal(listOf("#FFB347", "#FF9A3C", "#FF7A3A", "#F2553E", "#C93B4A"),
                titleSize = 24f, titleSpacing = 0.4f, titleWeight = 900),
            "Hall" to pal(listOf("#B34BFF", "#8E5BFF", "#6D6CFF", "#4F8BFF", "#27C4FF"),
                titleSize = 30f, titleSpacing = 1.4f, titleWeight = 700),
            "Plate" to pal(listOf("#CFE3FF", "#A9C8F5", "#86AEE8", "#6E94DA", "#5C7BC8"),
                titleSize = 27f, titleSpacing = 2.2f, titleWeight = 500),
            "Cathedral" to pal(listOf("#FFD36B", "#FFB54A", "#FF8B3E", "#F2603E", "#D43A4A"),
                titleSize = 34f, titleSpacing = 3.2f, titleWeight = 500),
            "Spring" to pal(listOf("#8CF07A", "#5FE08A", "#34D09C", "#22BFB2", "#2CA6C8"),
                titleSize = 29f, titleSpacing = 0.6f, titleWeight = 700),
            "Cloud" to pal(listOf("#FFC9E8", "#F5B0F2", "#D6A0FF", "#B1A4FF", "#8CB4FF"),
                titleSize = 31f, titleSpacing = 3.6f, titleWeight = 500),
            "Infinite" to pal(listOf("#E8F0FF", "#C2D4FF", "#9AB8FF", "#7C9CF0", "#5C7BC8"),
                titleSize = 29f, titleSpacing = 5.0f, titleWeight = 500),
            "Bloom" to pal(listOf("#FFC98A", "#FFB066", "#FF8F5C", "#F26B4E", "#C93B4A"),
                titleSize = 31f, titleSpacing = 0.2f, titleWeight = 900),
        ),
        groups = SPACE_GROUPS,
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
     * L'affichage d'un réglage : `min + v * (max - min)`, puis l'unité.
     * Les décimales sont celles du paramètre, jamais devinées par l'unité.
     */
    fun fmt(v: Float, p: FxParamD): String {
        val x = p.min + v * (p.max - p.min)
        val txt = if (p.decimals <= 0) {
            kotlin.math.round(x).toInt().toString()
        } else {
            val r = kotlin.math.round(x * 10f) / 10f
            if (r == r.toInt().toFloat()) r.toInt().toString() else "$r"
        }
        return if (p.unit.isBlank()) txt else "$txt ${p.unit}"
    }

    /** La durée affichée sur la pastille de Space : `.1 + decay * 29.9` secondes. */
    fun decaySeconds(v: Float): String {
        val t = ((.1f + v * 29.9f) * 100f).toInt()
        return "${t / 100}.${(t % 100) / 10}"
    }
}

/**
 * LES DIX MOTIFS CIRCULAIRES.
 *
 * Huit viennent de la référence, mot pour mot : Hall (anneaux épais), Chamber
 * (anneaux pointillés qui tournent), Room (carrés arrondis imbriqués), Shimmer
 * (anneaux d'étoiles en orbite), Plate (grille polaire de points avec une onde
 * radiale), Cathedral (pétales de rosace), Spring (ressort circulaire), Cloud
 * (anneaux de halos doux). Deux sont ajoutés pour nos presets qui n'existaient
 * pas dans la maquette : Infinite (un anneau qui se referme sur lui-même) et
 * Bloom (des pétales qui s'ouvrent). Le choix est ÉDITORIAL et assumé : il fallait
 * une figure pour chaque preset du catalogue.
 */
enum class SpaceStyle(val label: String) {
    Shimmer("Shimmer"), Chamber("Chamber"), Room("Room"), Hall("Hall"), Plate("Plate"),
    Cathedral("Cathedral"), Spring("Spring"), Cloud("Cloud"), Infinite("Infinite"), Bloom("Bloom"),
}

/** Le motif d'un preset. Tout preset inconnu tombe sur Hall. */
fun spaceStyleFor(preset: String): SpaceStyle = when (preset) {
    "Shimmer" -> SpaceStyle.Shimmer
    "Chamber" -> SpaceStyle.Chamber
    "Room" -> SpaceStyle.Room
    "Hall" -> SpaceStyle.Hall
    "Plate" -> SpaceStyle.Plate
    "Cathedral" -> SpaceStyle.Cathedral
    "Spring" -> SpaceStyle.Spring
    "Cloud" -> SpaceStyle.Cloud
    "Infinite" -> SpaceStyle.Infinite
    "Bloom" -> SpaceStyle.Bloom
    else -> SpaceStyle.Hall
}
