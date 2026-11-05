package com.soundstage.mixer.instruments

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Æther Piano — 12 modèles, chacun une scène (photo) et un caractère sonore.
 *
 * Ordre des paramètres (17, contrat de `fx_piano.h::kParams`) :
 * 0 HAMMER, 1 TONE, 2 RESONANCE, 3 LID, 4 MIC POS, 5 SPACE, 6 WIDTH, 7 LEVEL,
 * 8 RELEASE, 9 VEL, 10 DAMPER, 11 MIC DIST, 12 KEY, 13 (libre), 14 EQ,
 * 15 MODEL (discret : le moteur l'arrondit, ajouté par [AePianoModel.values]),
 * 16 HAMMER N.
 *
 * AMP (12) et PUNCH (13) ont été RETIRÉS.  Aucun piano acoustique n'a d'ampli, et
 * la saturation `tanh` qu'ils plaçaient en série écrasait un pack déjà à sa crête :
 * c'est le « Amp détruit le son » signalé.  L'index 12 est recyclé en bruit de
 * touche, 13 reste libre (les presets enregistrés le portent, le moteur l'ignore),
 * 16 devient le bruit de marteau.
 *
 * LEVEL (7) est égalisé sur la sonie K-weighted (ITU-R BS.1770) par
 * `tools/aetherfx/mastering.cpp` : les modèles sortent au même niveau perçu,
 * donc aucun n'est plus fort qu'un autre sans raison.
 *
 * Images : res/drawable-nodpi/ae_piano_<id>.webp, id = felt studio bright mellow
 * dark upright lady honky electric.
 */
class AePianoModel(
    val id: String,
    val name: String,
    val tag: String,
    val dark: Boolean,
    val acc: Color,
    val petals: List<Color>,
    val ptype: AePType,
    d: FloatArray,
    idx: Int,
) {
    /**
     * 20 valeurs, une par paramètre du contrat `fx_piano.h`.
     *
     * `d` (18 valeurs) porte les réglages de timbre : 0..14 comme avant (14 = EQ
     * GRAVES), puis 15/16/17 = EQ MÉDIUM / FRÉQUENCE DU MÉDIUM / EQ AIGUS.  Les
     * trois bruits de mécanique sont DÉRIVÉS de la dureté du marteau `d[0]` plutôt
     * que répétés douze fois : un piano à marteau dur claque et cliquette, un feutre
     * effleure.  Cela évite douze constantes à maintenir et garde la table lisible.
     *
     * L'EQ est le levier qui distingue vraiment deux modèles : six acoustiques
     * jouent la MÊME banque, et trois électriques partagent leur propre prise.
     * Avant, un seul potard inclinait les trois bandes ensemble, donc « Bright »
     * ne pouvait pas creuser son grave sans perdre aussi son aigu.
     *
     * Les emplacements 10 (ex-PEDAL), 12 (ex-DRIVE) et 13 (ex-PUNCH) de `d` sont
     * désormais ignorés : DRIVE et PUNCH sont retirés du moteur, et DAMPER/KEY sont
     * recalculés ci-dessous.  Les y laisser serait du code mort déguisé.
     */
    val values: FloatArray = FloatArray(20).also { v ->
        for (i in 0..14) v[i] = d[i]
        v[10] = 0.20f + 0.30f * d[0]     // DAMPER : feutre discret, suit la dureté
        v[12] = 0.15f + 0.40f * d[0]     // KEY : contact de touche, jamais envahissant
        v[13] = 0f                        // libre (ex-PUNCH)
        v[15] = idx / 8f                  // MODEL : >= 1 dès l'index 8 = banque électrique
        v[16] = 0.25f + 0.60f * d[0]     // HAMMER N : bruit de la frappe
        v[17] = d[15]                     // EQ MÉDIUM (peaking)
        v[18] = d[16]                     // EQ FRÉQUENCE du médium (300..2700 Hz)
        v[19] = d[17]                     // EQ AIGUS (shelf 3,2 kHz)
    }
}

private fun col(h: Long) = Color(0xFF000000 or h)

object AePianoModels {
    val all = listOf(
        AePianoModel("felt", "Felt", "Soft felt hammers · warm & round", false, col(0xE9A8B8), listOf(col(0xF7C6D3), col(0xFBE0E6), col(0xF2A9BD)), AePType.PETAL, floatArrayOf(0.15f, 0.05f, 0.50f, 0.30f, 0.42f, 0.32f, 0.50f, 0.6400f, 0.30f, 0.50f, 0.40f, 0.30f, 0.35f, 0.10f, 0.45f, 0.62f, 0.30f, 0.28f), 0),
        AePianoModel("studio", "Studio", "Concert grand · balanced & clear", false, col(0x4F9FE6), listOf(col(0xFFFFFF), col(0xCFE6FF), col(0x9CCAF5)), AePType.SPARK, floatArrayOf(0.55f, 0.45f, 0.50f, 0.65f, 0.55f, 0.40f, 0.60f, 0.7019f, 0.45f, 0.55f, 0.30f, 0.45f, 0.45f, 0.25f, 0.55f, 0.50f, 0.40f, 0.56f), 1),
        AePianoModel("bright", "Bright", "Crisp attack · sparkling top", false, col(0xE8A812), listOf(col(0xFFE9A8), col(0xFFD166), col(0xFFF3C9)), AePType.LEAF, floatArrayOf(0.90f, 0.90f, 0.25f, 0.95f, 0.80f, 0.15f, 0.70f, 0.8641f, 0.35f, 0.60f, 0.25f, 0.80f, 0.20f, 0.30f, 0.70f, 0.34f, 0.45f, 0.86f), 2),
        AePianoModel("mellow", "Mellow", "Warm mids · rounded highs", true, col(0xFF9A5A), listOf(col(0xFFB27A), col(0xFF8A5C), col(0xFFD0A0)), AePType.PETAL, floatArrayOf(0.25f, 0.15f, 0.60f, 0.40f, 0.20f, 0.70f, 0.50f, 0.7386f, 0.60f, 0.45f, 0.40f, 0.25f, 0.75f, 0.15f, 0.30f, 0.44f, 0.22f, 0.26f), 3),
        AePianoModel("dark", "Dark", "Deep bass · cinematic night", true, col(0x8A9BFF), listOf(col(0xAEBCFF), col(0x7F8FE8), col(0xDFE6FF)), AePType.STAR, floatArrayOf(0.10f, 0.00f, 0.80f, 0.20f, 0.10f, 0.95f, 0.75f, 0.6935f, 0.70f, 0.40f, 0.45f, 0.20f, 0.85f, 0.20f, 0.35f, 0.40f, 0.18f, 0.18f), 4),
        AePianoModel("upright", "Cottage", "Small wooden body · close & intimate", true, col(0xD9A15A), listOf(col(0xE8C58F), col(0xD9A15A), col(0xFFF1D6)), AePType.DUST, floatArrayOf(0.50f, 0.35f, 0.60f, 0.30f, 0.45f, 0.30f, 0.40f, 0.6931f, 0.35f, 0.50f, 0.60f, 0.40f, 0.30f, 0.45f, 0.65f, 0.56f, 0.30f, 0.50f), 5),
        AePianoModel("lady", "Parlor", "Parlor grand · close & golden", true, col(0xE0A25A), listOf(col(0xFFD9A0), col(0xF2B866), col(0xFFF0D0)), AePType.SPARK, floatArrayOf(0.35f, 0.30f, 0.40f, 0.55f, 0.35f, 0.60f, 0.45f, 0.6400f, 0.50f, 0.50f, 0.30f, 0.50f, 0.60f, 0.20f, 0.45f, 0.54f, 0.34f, 0.62f), 6),
        AePianoModel("honky", "Saloon", "Detuned old upright · vintage", false, col(0xB8863C), listOf(col(0xA8743A), col(0xC89A5A), col(0x8A5A28)), AePType.LEAF, floatArrayOf(0.70f, 0.70f, 0.30f, 0.50f, 0.60f, 0.25f, 0.90f, 0.5771f, 0.35f, 0.60f, 0.70f, 0.70f, 0.30f, 0.40f, 0.75f, 0.66f, 0.55f, 0.78f), 7),
        AePianoModel("electric", "Tine Keys", "Tine keys · tremolo & neon", true, col(0xFF6AD5), listOf(col(0xFF8BE0), col(0x7BE0FF), col(0xC9A0FF)), AePType.NEON, floatArrayOf(0.40f, 0.85f, 0.15f, 0.70f, 0.90f, 0.35f, 0.85f, 0.8455f, 0.45f, 0.50f, 0.20f, 0.85f, 0.25f, 0.55f, 0.60f, 0.66f, 0.55f, 0.46f), 8),
        // Les trois suivants portent une BANQUE propre (voir AeBanks.electricModels) :
        // c'est ce qui les distingue des huit reglages acoustiques.  L'index est lu par
        // le moteur via le parametre MODEL (15) : `idx / 8f` depasse 1 des l'index 8,
        // donc le moteur les classe tous « electrique » — c'est voulu, ils le sont.
        AePianoModel("ballad", "Ballad", "Ballad electric · soft & wide", true, col(0x7BD8A0), listOf(col(0xA8F0C8), col(0x6BC8E8), col(0xE0FFF0)), AePType.DUST, floatArrayOf(0.30f, 0.55f, 0.30f, 0.50f, 0.70f, 0.55f, 0.80f, 1.0000f, 0.50f, 0.45f, 0.30f, 0.60f, 0.40f, 0.45f, 0.50f, 0.44f, 0.30f, 0.40f), 9),
        AePianoModel("dx", "DX Bell", "FM bell electric · glassy", false, col(0x9AB6FF), listOf(col(0xC8D8FF), col(0x8FA8F0), col(0xEAF0FF)), AePType.STAR, floatArrayOf(0.85f, 0.75f, 0.20f, 0.85f, 0.60f, 0.30f, 0.70f, 0.9295f, 0.40f, 0.55f, 0.25f, 0.75f, 0.30f, 0.60f, 0.65f, 0.40f, 0.60f, 0.72f), 10),
        AePianoModel("epiano", "E.Piano", "Classic electric piano · round", true, col(0xE8B45A), listOf(col(0xFFD48A), col(0xD89A50), col(0xFFF0D0)), AePType.SPARK, floatArrayOf(0.45f, 0.60f, 0.35f, 0.60f, 0.75f, 0.45f, 0.75f, 0.9656f, 0.48f, 0.50f, 0.35f, 0.65f, 0.35f, 0.50f, 0.55f, 0.44f, 0.38f, 0.54f), 11),
    )
}

/** Choisir un modèle = charger un preset (les potards glissent, MODEL bascule d'un coup). */
val AePianoPresets: List<AePreset> = AePianoModels.all.map { AePreset(it.name, "Piano", it.values) }

/**
 * Une scene de piano : l'image, et la luminance de sa bande haute.
 *
 * La luminance est mesuree a l'ouverture, une fois par modele. C'est elle qui
 * choisit la couleur des textes : le drapeau `dark` ecrit a la main peut ne pas
 * correspondre a la photo, alors qu'une mesure garantit le contraste.
 */
class AePianoPlate(val bitmap: ImageBitmap, val topLuma: Float)

object AePianoAssets {
    private val cache = LruCache<String, AePianoPlate>(4)

    /**
     * Repli des modeles electriques sur l'image de Tine Keys.
     *
     * Depuis que le pack de planches fournit une photo par modele, ce repli ne se
     * declenche plus ; il reste pour qu'un modele ajoute plus tard sans photo propre
     * retombe sur l'image de l'electrique au lieu du dessin vectoriel.
     */
    private val epIds = setOf("ballad", "dx", "epiano")

    fun plate(ctx: Context, id: String): AePianoPlate? {
        var name = "ae_piano_$id"
        if (id in epIds && ctx.resources.getIdentifier(name, "drawable", ctx.packageName) == 0) {
            name = "ae_piano_electric"
        }
        cache.get(name)?.let { return it }
        val rid = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (rid == 0) return null
        val bm = BitmapFactory.decodeResource(ctx.resources, rid) ?: return null
        // Luminance relative (Rec. 709) du tiers haut : c'est la que vivent le titre,
        // le nom du modele et la liste des modeles.
        val band = max(1, bm.height / 3)
        val px = IntArray(bm.width * band)
        bm.getPixels(px, 0, bm.width, 0, 0, bm.width, band)
        var acc = 0.0
        for (p in px) {
            acc += 0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)
        }
        val luma = (acc / (px.size * 255.0)).toFloat()
        return AePianoPlate(bm.asImageBitmap(), luma).also { cache.put(name, it) }
    }
}

private class K(val label: String, val idx: Int, val fmt: (Float) -> String)
private fun pc(v: Float) = "${(v * 100).toInt()}%"
private fun db(v: Float) = "${(-40 + v * 46).toInt()}dB"
private fun eqdb(v: Float) = "${((v - .5f) * 18f).toInt().let { if (it > 0) "+$it" else "$it" }}dB"
private fun lvl(v: Float, vararg names: String) = names[(v * names.size).toInt().coerceIn(0, names.size - 1)]

/**
 * Les réglages du piano, rangés en quatre familles nommées.
 *
 * L'ancienne disposition empilait 8 potards, 3 curseurs puis 4 potards sans
 * logique lisible — « moches et illogiques » (retour utilisateur).  Ici chaque
 * famille porte son en-tête et tient sur une ligne, dans l'ordre de la chaîne
 * audio : le timbre, puis le micro, puis le jeu, puis la mécanique.  Pianoteq
 * range ses réglages de la même façon.
 *
 * Chaque potard pilote un paramètre RÉEL et distinct du contrat `fx_piano.h` :
 * plus de doublon « Mic »/« Mic pos », plus d'« Amp drive » (retiré du moteur).
 */
private class KG(val title: String, val ks: List<K>)

private val PIANO_GROUPS = listOf(
    KG("TONE", listOf(
        K("Hardness", 0, ::pc),
        K("Tone", 1) { "${((it - .5f) * 16).toInt()}dB" },
        K("Resonance", 2, ::pc),
        K("Lid", 3) { lvl(it, "Closed", "Half", "Open") },
    )),
    KG("MIC", listOf(
        K("Position", 4) { lvl(it, "Under", "Front", "Over") },
        K("Distance", 11) { lvl(it, "Near", "Mid", "Far") },
        K("Space", 5, ::pc),
        K("Width", 6, ::pc),
    )),
    KG("PLAY", listOf(
        // gamma = 2^((0.5 - v) * 1.36) : v BAS => gamma > 1 => il faut frapper (Hard).
        // L'etiquette suit le comportement, pas la valeur brute.
        K("Velocity", 9) { lvl(it, "Hard", "Linear", "Soft") },
        K("Release", 8) { "${(80 + it * 550).toInt()}ms" },
        K("Damper", 10, ::pc),
        K("Level", 7, ::db),
    )),
)

private fun eqhz(v: Float) = "${(300 * Math.pow(9.0, v.toDouble())).toInt()}Hz"

/**
 * Mecanique : deux bruits, sous la rangee du milieu, avec la frequence du medium.
 *
 * L'EQ etait un seul potard qui inclinait les trois bandes ensemble ; il est remplace
 * par trois bandes independantes (graves, medium, aigus) plus la frequence du medium,
 * ce qui permet de creuser le « bark » d'un Rhodes ou la chaleur d'un feutre sans
 * eteindre le reste.  Six acoustiques jouent la meme banque : c'est l'EQ qui les
 * separe vraiment.  Les trois gains vivent dans la colonne de droite, la frequence
 * reste ici (voir EQ_MID / EQ_SIDE).
 */
private val MECHANICS = listOf(
    K("Key", 12, ::pc),
    K("Hammer", 16, ::pc),
)

/**
 * Egaliseur : la frequence du medium reste au milieu (sous le nom du modele),
 * les trois gains rejoignent la case de droite.  Ils sont volontairement plus
 * petits que les autres potards : c'est un reglage de finition, pas un axe de
 * jeu, et le panneau de droite n'a pas la place de les loger a taille pleine.
 */
private val EQ_MID = K("EQ Hz", 18, ::eqhz)
private val EQ_SIDE = listOf(
    K("EQ Low", 14, ::eqdb),
    K("EQ Mid", 17, ::eqdb),
    K("EQ High", 19, ::eqdb),
)

/**
 * Réglages d'un piano ÉLECTRIQUE (Rhodes/Wurlitzer/DX), pas d'un acoustique.
 *
 * L'utilisateur l'a demandé explicitement : « les réglages EP doivent être autre
 * chose ».  Un EP n'a ni couvercle ni position de micro dans un piano : ce sont
 * un tine, un corps, une position de pickup et une attaque de marteau.  Les
 * mêmes paramètres moteur sont réemployés (le contrat est figé à 17), mais
 * relus avec le vocabulaire de l'instrument — et LID (3) disparaît, faute d'objet.
 *
 * `Hardness` devient `Tine` : sur un EP c'est bien la dureté du tine qui règle le
 * mordant, le moteur bascule déjà la corde en tine quand MODEL >= 1.
 */
private val EP_GROUPS = listOf(
    KG("TONE", listOf(
        K("Tine", 0, ::pc),
        K("Tone", 1) { "${((it - .5f) * 16).toInt()}dB" },
        K("Body", 2, ::pc),
        K("Pickup", 4) { lvl(it, "Near", "Mid", "Far") },
    )),
    KG("SPACE", listOf(
        K("Room", 5, ::pc),
        K("Width", 6, ::pc),
        K("Distance", 11) { lvl(it, "Near", "Mid", "Far") },
        K("Level", 7, ::db),
    )),
    KG("PLAY", listOf(
        K("Velocity", 9) { lvl(it, "Hard", "Linear", "Soft") },
        K("Release", 8) { "${(80 + it * 550).toInt()}ms" },
        K("Damper", 10, ::pc),
    )),
)

/**
 * La fenêtre Piano : scène plein cadre (photo + particules), liste des modèles à
 * gauche, carte de verre des potards à droite, clavier en bas.
 *
 * [procedural] reste faux : la scène du piano est une photographie, sans ciel ni eau.
 * L'eau appartient à Strings.
 */
@Composable
fun BoxScope.AePianoUi(c: AeCtl, manager: AeManager, strip: Int, onOpenBrowser: () -> Unit = {}) {
    val s = manager.sessions[strip] ?: return
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val model = AePianoModels.all[s.preset.coerceIn(0, AePianoModels.all.size - 1)]
    val plate = remember(model.id) { AePianoAssets.plate(ctx, model.id) }
    val th = rememberAeThemeFromLuma(plate?.topLuma, model.acc)
    val baseNow by rememberUpdatedState(48)
    val scene = remember(s) { AeSceneRuntime(procedural = false) }

    LaunchedEffect(model.id) {
        scene.setBackground(plate?.bitmap)
        scene.particles.type = model.ptype
        scene.particles.colors = model.petals
    }
    LaunchedEffect(s) {
        var last = System.nanoTime()
        var seen = s.noteEvents
        while (true) {
            delay(if (s.held.isNotEmpty()) 33L else 45L)
            val now = System.nanoTime()
            val dt = min(0.1f, (now - last) / 1e9f)
            last = now
            manager.pollLevel(s.strip)
            if (s.noteEvents != seen) {
                seen = s.noteEvents
                val ux = 0.3f + ((s.lastNote - baseNow) / 36f).coerceIn(0f, 1f) * 0.4f
                scene.waves.drop(ux, 0.8f, 0.5f * (0.5f + 0.9f * 0.78f))
                scene.particles.spawn(2, scene.w.toFloat(), scene.h.toFloat(), scene.dp, ux * scene.w, scene.h * 0.45f, true)
            }
            if (Random.nextFloat() < dt * scene.particles.ambientRate) {
                scene.particles.spawn(1, scene.w.toFloat(), scene.h.toFloat(), scene.dp)
            }
            scene.update(dt)
        }
    }
    Box(
        Modifier.fillMaxSize(),
    ) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { scene.resize(it.width, it.height, dens) }) {
            scene.frame
            scene.draw(this)
            scene.particles.draw(this)
        }
        AeStage(Modifier.fillMaxSize()) {
            // Titre majestueux en Cinzel, la police des titres du depot : grand et
            // grave, centre sur la zone libre entre les deux panneaux de verre.
            AeT("ÆTHER PIANO", 42, th.ink, Modifier.at(216, 8, 478, 48), AeFonts.plate, FontWeight.ExtraBold, 0.16f, TextAlign.Center)
            // Le nom du modele est le seul libelle qui dit ce qui est charge, et la case
            // « Presets » de l'en-tete a ete retiree : c'est donc lui qui ouvre le navigateur.
            Box(Modifier.at(216, 58, 478, 34).clickable(remember { MutableInteractionSource() }, null, onClick = onOpenBrowser)) {
                AeT(model.name.uppercase(), 26, th.ink, Modifier.fillMaxSize(), AeFonts.display, FontWeight.Light, 0.2f, TextAlign.Center)
            }
            run {
                // Fond de verre derriere la colonne de gauche, le meme que la carte des
                // potards. La liste s'affiche en encre suivant la photo : sur les 6 modeles
                // clairs l'encre passe sombre, et la photo claire la noyait (contraste 60,
                // illisible). Un voile NOIR n'aide pas — il assombrit le fond alors que
                // l'encre est deja sombre. Le verre du theme, lui, est toujours du cote
                // oppose a l'encre : clair quand l'encre est sombre, sombre sinon.
                // Fond de la colonne, choisi d'apres l'ENCRE et non d'apres la photo.
                // La luminance du theme est mesuree sur le tiers haut, mais la liste
                // descend a 80 % de l'image : sur Felt le haut est clair (encre sombre)
                // et le bas est sombre, donc encre sombre sur fond sombre — contraste
                // mesure 4, texte invisible. Un fond translucide ne peut pas le garantir.
                // Ici le fond est toujours du cote oppose a l'encre, donc lisible partout.
                // Le clavier virtuel a ete RETIRE (demande utilisateur) : il occupait
                // les 60 unites du bas pour un doublon du clavier materiel, alors que
                // la hauteur manquait aux reglages. Les deux panneaux descendent donc
                // jusqu'a y=430.
                AeGlassPanel(th, plate?.bitmap?.asAndroidBitmap(), 16, 88, 200, 342)
                AeModelList(th, AePianoModels.all.map { it.name }, s.preset, 28, 100, 310) { manager.loadPreset(s.strip, it) }
                AeGlassPanel(th, plate?.bitmap?.asAndroidBitmap(), 694, 54, 280, 376)
                // Trois familles, une par ligne. Un modele electrique n'affiche pas
                // les memes : pas de couvercle, pas de micro de piano, mais un tine et
                // une position de pickup (demande utilisateur : « les reglages EP
                // doivent etre autre chose »).
                val groups = if (AeBanks.isElectricModel(s.preset)) EP_GROUPS else PIANO_GROUPS
                groups.forEachIndexed { gi, g ->
                    // Trois familles au lieu de quatre : la mecanique est sortie du lot.
                    // L'espacement passe a 84 (et non 112) pour degager, sous la
                    // derniere famille, la rangee de l'egaliseur : 62 + 2*84 + 16 + 72
                    // = 318, et le panneau s'arrete a 430.
                    val gy = 62 + gi * 84
                    AeT(g.title, 9, th.acc, Modifier.at(704, gy, 240, 12), AeFonts.plate, FontWeight.ExtraBold, 0.22f)
                    g.ks.forEachIndexed { j, k ->
                        AeLightKnob(th, c.v(k.idx), k.label, k.fmt(c.v(k.idx)), 704 + j * 62, gy + 16) { c.set(k.idx, it) }
                    }
                }
                // Les trois gains de l'egaliseur, sous les familles, dans la meme
                // colonne de verre.  Plus petits que les potards de famille (0,95
                // contre 1,0) : l'EQ se regle a l'oreille une fois pour toutes, il
                // n'a pas besoin de la place d'un axe de jeu.  y=330 : la derniere
                // famille finit a 318, le panneau a 430.
                EQ_SIDE.forEachIndexed { j, k ->
                    AeLightKnob(th, c.v(k.idx), k.label, k.fmt(c.v(k.idx)), 716 + j * 84, 330, 0.95f) { c.set(k.idx, it) }
                }
                // Mecanique + frequence de l'EQ, en bas au milieu, sans titre.
                // Centres sur l'axe du titre (455) et non sur l'ancienne abscisse
                // de la rangee de six (537), qui les laissait 37 unites a droite du
                // milieu.  Pose au ras du bas : 370 + 76 de haut laisse 14 unites,
                // et le panneau s'arrete a 430, donc aucun recouvrement.
                val midRow = MECHANICS + EQ_MID
                midRow.forEachIndexed { j, k ->
                    AeLightKnob(th, c.v(k.idx), k.label, k.fmt(c.v(k.idx)), 337 + j * 80, 370, 1.2f) { c.set(k.idx, it) }
                }
            }
        }
    }
}
