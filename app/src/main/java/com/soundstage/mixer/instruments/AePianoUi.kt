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
import androidx.compose.runtime.mutableIntStateOf
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
     * 17 valeurs, une par paramètre du contrat `fx_piano.h`.
     *
     * `d` (15 valeurs) porte les réglages de timbre 0..14.  Les trois bruits de
     * mécanique et l'EQ sont DÉRIVÉS de la dureté du marteau `d[0]` plutôt que
     * répétés douze fois : un piano à marteau dur claque et cliquette, un feutre
     * effleure.  Cela évite douze constantes à maintenir et garde la table lisible.
     *
     * Les emplacements 10 (ex-PEDAL), 12 (ex-DRIVE) et 13 (ex-PUNCH) de `d` sont
     * désormais ignorés : DRIVE et PUNCH sont retirés du moteur, et DAMPER/KEY sont
     * recalculés ci-dessous.  Les y laisser serait du code mort déguisé.
     */
    val values: FloatArray = FloatArray(17).also { v ->
        for (i in 0..14) v[i] = d[i]
        v[10] = 0.20f + 0.30f * d[0]     // DAMPER : feutre discret, suit la dureté
        v[12] = 0.15f + 0.40f * d[0]     // KEY : contact de touche, jamais envahissant
        v[13] = 0f                        // libre (ex-PUNCH)
        v[15] = idx / 8f                  // MODEL : >= 1 dès l'index 8 = banque électrique
        v[16] = 0.25f + 0.60f * d[0]     // HAMMER N : bruit de la frappe
    }
}

private fun col(h: Long) = Color(0xFF000000 or h)

object AePianoModels {
    val all = listOf(
        AePianoModel("felt", "Felt", "Soft felt hammers · warm & round", false, col(0xE9A8B8), listOf(col(0xF7C6D3), col(0xFBE0E6), col(0xF2A9BD)), AePType.PETAL, floatArrayOf(0.15f, 0.05f, 0.50f, 0.30f, 0.42f, 0.32f, 0.50f, 0.70f, 0.30f, 0.50f, 0.40f, 0.30f, 0.35f, 0.10f, 0.45f), 0),
        AePianoModel("studio", "Studio", "Concert grand · balanced & clear", false, col(0x4F9FE6), listOf(col(0xFFFFFF), col(0xCFE6FF), col(0x9CCAF5)), AePType.SPARK, floatArrayOf(0.55f, 0.45f, 0.50f, 0.65f, 0.55f, 0.40f, 0.60f, 0.84f, 0.45f, 0.55f, 0.30f, 0.45f, 0.45f, 0.25f, 0.55f), 1),
        AePianoModel("bright", "Bright", "Crisp attack · sparkling top", false, col(0xE8A812), listOf(col(0xFFE9A8), col(0xFFD166), col(0xFFF3C9)), AePType.LEAF, floatArrayOf(0.90f, 0.90f, 0.25f, 0.95f, 0.80f, 0.15f, 0.70f, 0.89f, 0.35f, 0.60f, 0.25f, 0.80f, 0.20f, 0.30f, 0.70f), 2),
        AePianoModel("mellow", "Mellow", "Warm mids · rounded highs", true, col(0xFF9A5A), listOf(col(0xFFB27A), col(0xFF8A5C), col(0xFFD0A0)), AePType.PETAL, floatArrayOf(0.25f, 0.15f, 0.60f, 0.40f, 0.20f, 0.70f, 0.50f, 0.58f, 0.60f, 0.45f, 0.40f, 0.25f, 0.75f, 0.15f, 0.30f), 3),
        AePianoModel("dark", "Dark", "Deep bass · cinematic night", true, col(0x8A9BFF), listOf(col(0xAEBCFF), col(0x7F8FE8), col(0xDFE6FF)), AePType.STAR, floatArrayOf(0.10f, 0.00f, 0.80f, 0.20f, 0.10f, 0.95f, 0.75f, 0.51f, 0.70f, 0.40f, 0.45f, 0.20f, 0.85f, 0.20f, 0.35f), 4),
        AePianoModel("upright", "Cottage", "Small wooden body · close & intimate", true, col(0xD9A15A), listOf(col(0xE8C58F), col(0xD9A15A), col(0xFFF1D6)), AePType.DUST, floatArrayOf(0.50f, 0.35f, 0.60f, 0.30f, 0.45f, 0.30f, 0.40f, 0.79f, 0.35f, 0.50f, 0.60f, 0.40f, 0.30f, 0.45f, 0.65f), 5),
        AePianoModel("lady", "Parlor", "Parlor grand · close & golden", true, col(0xE0A25A), listOf(col(0xFFD9A0), col(0xF2B866), col(0xFFF0D0)), AePType.SPARK, floatArrayOf(0.35f, 0.30f, 0.40f, 0.55f, 0.35f, 0.60f, 0.45f, 0.64f, 0.50f, 0.50f, 0.30f, 0.50f, 0.60f, 0.20f, 0.45f), 6),
        AePianoModel("honky", "Saloon", "Detuned old upright · vintage", false, col(0xB8863C), listOf(col(0xA8743A), col(0xC89A5A), col(0x8A5A28)), AePType.LEAF, floatArrayOf(0.70f, 0.70f, 0.30f, 0.50f, 0.60f, 0.25f, 0.90f, 0.74f, 0.35f, 0.60f, 0.70f, 0.70f, 0.30f, 0.40f, 0.75f), 7),
        AePianoModel("electric", "Tine Keys", "Tine keys · tremolo & neon", true, col(0xFF6AD5), listOf(col(0xFF8BE0), col(0x7BE0FF), col(0xC9A0FF)), AePType.NEON, floatArrayOf(0.40f, 0.85f, 0.15f, 0.70f, 0.90f, 0.35f, 0.85f, 0.74f, 0.45f, 0.50f, 0.20f, 0.85f, 0.25f, 0.55f, 0.60f), 8),
        // Les trois suivants portent une BANQUE propre (voir AeBanks.electricModels) :
        // c'est ce qui les distingue des huit reglages acoustiques.  L'index est lu par
        // le moteur via le parametre MODEL (15) : `idx / 8f` depasse 1 des l'index 8,
        // donc le moteur les classe tous « electrique » — c'est voulu, ils le sont.
        AePianoModel("ballad", "Ballad", "Ballad electric · soft & wide", true, col(0x7BD8A0), listOf(col(0xA8F0C8), col(0x6BC8E8), col(0xE0FFF0)), AePType.DUST, floatArrayOf(0.30f, 0.55f, 0.30f, 0.50f, 0.70f, 0.55f, 0.80f, 0.68f, 0.50f, 0.45f, 0.30f, 0.60f, 0.40f, 0.45f, 0.50f), 9),
        AePianoModel("dx", "DX Bell", "FM bell electric · glassy", false, col(0x9AB6FF), listOf(col(0xC8D8FF), col(0x8FA8F0), col(0xEAF0FF)), AePType.STAR, floatArrayOf(0.85f, 0.75f, 0.20f, 0.85f, 0.60f, 0.30f, 0.70f, 0.80f, 0.40f, 0.55f, 0.25f, 0.75f, 0.30f, 0.60f, 0.65f), 10),
        AePianoModel("epiano", "E.Piano", "Classic electric piano · round", true, col(0xE8B45A), listOf(col(0xFFD48A), col(0xD89A50), col(0xFFF0D0)), AePType.SPARK, floatArrayOf(0.45f, 0.60f, 0.35f, 0.60f, 0.75f, 0.45f, 0.75f, 0.72f, 0.48f, 0.50f, 0.35f, 0.65f, 0.35f, 0.50f, 0.55f), 11),
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
     * Modeles EP : une seule image, celle de l'electrique.
     *
     * Les 3 modeles ajoutes apres coup (Ballad, DX Bell, E.Piano) n'ont pas de photo
     * propre.  Sans ce repli, `getIdentifier` renvoie 0 et la scene retombait sur son
     * dessin vectoriel : la fenetre changeait d'allure entre Tine Keys et ses voisins,
     * pour la meme famille d'instrument.
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
 * Potards de voix — 8, en deux rangées de quatre.
 *
 * Chacun pilote un paramètre RÉEL et distinct du contrat : plus de doublon
 * « Mic »/« Mic pos » (deux étiquettes sur l'index 4), plus d'« Amp drive » sur un
 * index qui n'était pas l'ampli.  Ordre = ordre du contrat, donc l'écran se lit de
 * gauche à droite comme la chaîne audio.
 */
private val PK = listOf(
    K("Hardness", 0, ::pc),
    K("Tone", 1) { "${((it - .5f) * 16).toInt()}dB" },
    K("Resonance", 2, ::pc),
    K("Lid", 3) { lvl(it, "Closed", "Half", "Open") },
    K("Mic pos", 4) { lvl(it, "Under", "Front", "Over") },
    K("Space", 5, ::pc),
    K("Width", 6, ::pc),
    K("Level", 7, ::db),
)
/** Curseurs de jeu — 3. */
private val PSL = listOf(
    K("Release", 8) { "${(80 + it * 550).toInt()}ms" },
    // gamma = 2^((0.5 - v) * 1.36) : v BAS => gamma > 1 => il faut frapper (Hard).
    // L'etiquette suit le comportement, pas la valeur brute.
    K("Velocity", 9) { lvl(it, "Hard", "Linear", "Soft") },
    K("Damper", 10, ::pc),
)
/**
 * Mécanique et micro — 4 potards, une rangée.
 *
 * C'est ce que l'utilisateur réclamait (« réglages essentiels manquants ») : les
 * trois bruits d'un vrai piano (marteau, touche, étouffoir) sont maintenant des
 * contrôles, au lieu de constantes cachées dans le moteur.
 */
private val PST = listOf(
    K("Mic dist", 11) { lvl(it, "Near", "Mid", "Far") },
    K("Key noise", 12, ::pc),
    K("Hammer noise", 16, ::pc),
    K("EQ", 14, ::eqdb),
)

/** Aperçu de carte : la scène Felt, recadrée au centre. */
@Composable
fun AePianoCardPreview(modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp = remember { AePianoAssets.plate(ctx, "felt")?.bitmap }
    Canvas(modifier.clip(RoundedCornerShape(12.dp))) {
        bmp?.let {
            val sc = max(size.width / it.width, size.height / it.height)
            val dw = it.width * sc
            val dh = it.height * sc
            drawImage(
                it,
                dstOffset = IntOffset(((size.width - dw) / 2f).roundToInt(), ((size.height - dh) / 2f).roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            )
        }
    }
}

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
    var base by remember { mutableIntStateOf(48) }
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
            AeT("ÆTHER PIANO", 10, th.ink2, Modifier.at(24, 44, 200, 12), AeFonts.display, FontWeight.Normal, 0.3f)
            AeT("PIANO", 10, th.ink, Modifier.at(300, 12, 400, 12), AeFonts.display, FontWeight.Light, 0.42f, TextAlign.Center)
            // Le nom du modele est le seul libelle qui dit ce qui est charge, et la case
            // « Presets » de l'en-tete a ete retiree : c'est donc lui qui ouvre le navigateur.
            Box(Modifier.at(300, 24, 400, 38).clickable(remember { MutableInteractionSource() }, null, onClick = onOpenBrowser)) {
                AeT(model.name.uppercase(), 30, th.ink, Modifier.fillMaxSize(), AeFonts.display, FontWeight.ExtraLight, 0.2f, TextAlign.Center)
            }
            AeT(model.tag, 11, th.ink2, Modifier.at(300, 66, 400, 14), AeFonts.display, FontWeight.Light, 0.14f, TextAlign.Center)
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
                AeGlassPanel(th, plate?.bitmap?.asAndroidBitmap(), 16, 88, 200, 300)
                // La liste doit tenir DANS le panneau de verre (y 88..388) : elle allait
                // jusqu'a 400, donc les 12 dernieres unites debordaient sous la case.
                // 270 = 9 lignes de 30 exactement, donc la derniere ligne est entiere
                // (100 + 270 = 370, 18 unites de marge avant le bas du panneau).
                AeModelList(th, AePianoModels.all.map { it.name }, s.preset, 28, 100, 270) { manager.loadPreset(s.strip, it) }
                AeGlassPanel(th, plate?.bitmap?.asAndroidBitmap(), 694, 54, 280, 326)
                PK.forEachIndexed { j, k ->
                    AeLightKnob(th, c.v(k.idx), k.label, k.fmt(c.v(k.idx)), 704 + (j % 4) * 62, 74 + (j / 4) * 70) { c.set(k.idx, it) }
                }
                PSL.forEachIndexed { j, k -> AeLightSlider(th, c.v(k.idx), k.label, 708, 222 + j * 24) { c.set(k.idx, it) } }
                PST.forEachIndexed { j, k ->
                    AeLightKnob(th, c.v(k.idx), k.label, k.fmt(c.v(k.idx)), 704 + j * 62, 304) { c.set(k.idx, it) }
                }
                AeGlassKeyboard(c, th, base, 150, 396, 700, 52)
                AeSmallBtn(th, "−", 108, 410, 28, 28) { base = (base - 12).coerceAtLeast(24) }
                AeSmallBtn(th, "+", 864, 410, 28, 28) { base = (base + 12).coerceAtMost(60) }
            }
        }
    }
}
