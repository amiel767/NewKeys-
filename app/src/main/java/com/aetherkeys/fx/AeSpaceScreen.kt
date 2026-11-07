package com.aetherkeys.fx

import androidx.compose.animation.animateColorAsState
import kotlin.math.ln
import kotlin.math.pow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * L'ÉCRAN DE SPACE — la page de reverb, portée de la référence.
 *
 * CE QU'IL REMPLACE. La page d'effet générique disposait les réglages dans une
 * grille de cellules. Ici chaque preset a son MOTIF CIRCULAIRE : le gros potard
 * est vivant, et sa figure dit de quel type de pièce il s'agit. C'est la
 * référence, mot pour mot — huit motifs d'origine, plus deux pour nos presets
 * Infinite et Bloom.
 *
 * LE CONTRAT AVEC L'HÔTE. Cet écran ne connaît ni le ViewModel, ni le moteur : il
 * reçoit les valeurs et rend des intentions ([onValue], [onPreset], [onSave]). Le
 * même écran sert donc à l'application et au harnais de capture.
 *
 * LES INDICES SONT UN CONTRAT. [onValue] reçoit l'INDEX du réglage dans
 * [FxInfoD.params], qui est l'ordre du moteur. Les groupes et les presets de la
 * référence sont exprimés en CLÉS (`"decay"`, `"hf"`…) et résolus en indices ici,
 * une seule fois : c'est ce qui permet de relire la maquette sans risquer de
 * décaler un réglage.
 *
 * PAS D'AUDITION. La référence appelle `onFire` sur l'appui du gros potard pour
 * faire sonner la reverb. Le moteur n'expose pas d'audition ; l'appui déclenche
 * donc les ondulations, qui sont l'affichage, et rien d'autre. Le brancher plus
 * tard ne demande qu'un rappel.
 */
@Composable
fun AeSpaceScreen(
    fx: FxInfoD,
    values: List<Float>,
    presetName: String?,
    userNames: List<String>,
    custom: Boolean,
    onValue: (Int, Float) -> Unit,
    onPreset: (String) -> Unit,
    onSave: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val t = rememberSpaceTime()
    val fires = remember { mutableStateListOf<Float>() }

    val preset = presetName ?: fx.firstPreset ?: ""
    val style = spaceStyleFor(preset)
    val ramp = (fx.palettes[preset] ?: fx.palettes[fx.firstPreset] ?: FxDesign.SPACE.palettes.values.first()).colors
    val pal = ramp.map { animateColorAsState(it, tween(450), label = "pal").value }
    val all = fx.presets.keys.toList() + userNames
    val modified = custom

    var dropOpen by remember(fx.id) { mutableStateOf(false) }
    var saveOpen by remember(fx.id) { mutableStateOf(false) }
    var saveName by remember(fx.id) { mutableStateOf("") }

    val main = fx.mainIndex
    val decaySpec = fx.params[main]
    val decayShown by animateFloatAsState(
        values.getOrElse(main) { decaySpec.def }, spring(1f, Spring.StiffnessMediumLow), label = "decay",
    )
    fun v(key: String): Float {
        val spec = fx.byKey[key] ?: return 0f
        return values.getOrElse(fx.params.indexOf(spec)) { spec.def }
    }
    fun set(key: String, n: Float) {
        val i = fx.params.indexOfFirst { it.key == key }
        if (i >= 0) onValue(i, n.coerceIn(0f, 1f))
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(pal[0].mix(SPACE_STAGE, .86f), SPACE_STAGE),
                Offset(891f * .38f, 411f * .45f), 640f,
            ),
        ),
    ) {
        // ── LE MOT : « Space » dans la teinte du preset, le style ensuite. Pas de
        //    « Æther » devant : le nom de l'effet EST « Space ». Le style suit le
        //    preset, comme la figure.
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = pal[0])) { append("Space") }
                withStyle(SpanStyle(color = pal[2])) { append(style.label) }
            },
            Modifier.offset(28.dp, 10.dp),
            fontSize = 42.sp, fontWeight = FontWeight.Bold, fontFamily = FxFonts.ui,
            letterSpacing = (-1).sp, maxLines = 1,
        )

        // ── LA FERMETURE, SEULE EN HAUT À DROITE. Le nom du preset vit dans la
        //    pastille du bas : l'écrire ici aussi le montrait DEUX fois.
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // La fermeture est PLATE et prend la teinte du preset : c'était une
            // puce bombée, comme un bouton de matériel, ce que la référence n'a
            // pas. Le retour a disparu — la fermeture suffit à sortir de la page.
            SpaceFlatButton(AeFxIcons.CLOSE, pal[0], 30.dp, 16.dp, onClose)
        }

        // ── LE PIED, EN BAS À DROITE : la case de preset et son pas à pas, puis
        //    SAVE. Le nom n'est écrit qu'ICI — une seconde copie en haut le
        //    doublait.
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpacePresetPill(
                preset = preset, modified = modified, pal = pal, open = dropOpen,
                onClick = { dropOpen = !dropOpen; saveOpen = false },
            )
            Spacer(Modifier.width(6.dp))
            SpaceFlatButton(AeFxIcons.STEP_PREV, pal[0], 32.dp, 20.dp) {
                stepPreset(all, preset, -1)?.let(onPreset)
            }
            SpaceFlatButton(AeFxIcons.STEP_NEXT, pal[0], 32.dp, 20.dp) {
                stepPreset(all, preset, 1)?.let(onPreset)
            }
            Spacer(Modifier.width(12.dp))
            SpaceFlatText("SAVE", pal[3], saveOpen) { saveOpen = !saveOpen; dropOpen = false }
        }

        // ── LE GROS POTARD : DECAY, décalé à GAUCHE pour prendre la place que
        //    MIX et PRE-DELAY occupaient. Appui = ondulations.
        Box(Modifier.offset(84.dp, 78.dp)) {
            BigDecayKnob(
                style = style, pal = pal, decay01 = decaySpec.norm(decayShown), t = t, fires = fires,
                decayText = FxDesign.fmt(decayShown, decaySpec),
                onDecay = { set("decay", decaySpec.fromNorm(it)) },
                onFire = { fires.removeAll { t - it > 8f }; fires.add(t) },
            )
        }

        // ── LA BARRE DU HAUT : MIX puis PRE-DELAY, juste AU-DESSUS des panneaux et
        //    à leur aplomb. Chacun est un anneau FIN, son nom et sa valeur à
        //    DROITE. Ils étaient empilés dans la colonne de gauche, où leur texte
        //    vertical concurrençait DECAY.
        Row(
            Modifier.offset(x = 362.dp, y = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            listOf("mix" to 0, "pre" to 1).forEach { (k, ci) ->
                val spec = fx.byKey[k] ?: return@forEach
                SpaceRingKnob(
                    label = spec.name, spec = spec, value = v(k), color = pal[ci], size = 44.dp,
                    onChange = { set(k, it) }, onReset = { set(k, spec.def) },
                    labelSp = 12.5f, valueSp = 12.5f, horizontal = true,
                )
            }
        }

        // ── LES GROUPES DE LA RÉFÉRENCE, à droite. Leur largeur se DÉDUIT de la
        //    place restante : les largeurs fixes (160 + 4×84 = 496, plus 28 de
        //    gouttières) partaient de x=362 et sortaient de la fenêtre de 860 — le
        //    panneau EQ était coupé et la rangée dépassait le cadre. Ici la rangée
        //    se partage ce qui reste, au prorata des colonnes de chaque panneau, et
        //    le potard suit la largeur obtenue.
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(start = 362.dp, end = 16.dp).offset(y = 100.dp),
        ) {
            val gap = 7.dp
            val cols = fx.groups.map { if (it.keys.size > 2) 2 else 1 }
            val units = cols.sum().coerceAtLeast(1)
            val free = maxWidth - gap * (fx.groups.size - 1)
            val unit = free / units
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                fx.groups.forEachIndexed { gi, g ->
                    val col = pal[gi % pal.size]
                    val c = cols[gi]
                    val panelW = unit * c
                    val knob = min(44f, ((panelW - 10.dp) / c).value).coerceAtLeast(24f)
                    val f = (knob / 44f).coerceIn(.72f, 1f)
                    Column(
                        Modifier.width(panelW)
                            .drawBehind {
                                drawRoundRect(col.copy(alpha = .10f), cornerRadius = CornerRadius(24.dp.toPx()), style = Stroke(12.dp.toPx()))
                            }
                            .border(2.6.dp, col, RoundedCornerShape(24.dp))
                            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            g.title, color = SPACE_INK, fontSize = 15.5.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FxFonts.ui, letterSpacing = .4.sp, maxLines = 1,
                        )
                        g.keys.chunked(c).forEach { rowKeys ->
                            Row(Modifier.fillMaxWidth()) {
                                rowKeys.forEach { k ->
                                    val spec = fx.byKey[k] ?: return@forEach
                                    Box(Modifier.weight(1f)) {
                                        SpaceRingKnob(
                                            label = spec.name, spec = spec, value = v(k), color = col,
                                            size = knob.dp, labelSp = 11.5f * f, valueSp = 11.5f * f,
                                            onChange = { set(k, it) }, onReset = { set(k, spec.def) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── La liste des presets : elle monte DEPUIS la pastille du bas.
        if (dropOpen) {
            Box(
                Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { dropOpen = false },
            )
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 62.dp)
                    .width(300.dp).heightIn(max = 240.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF70E0E0E))
                    .border(2.dp, pal[0].copy(alpha = .5f), RoundedCornerShape(18.dp))
                    .verticalScroll(rememberScrollState()).padding(8.dp),
            ) {
                listOf("FACTORY" to fx.presets.keys.toList(), "USER" to userNames).forEach { (title, list) ->
                    if (list.isEmpty()) return@forEach
                    Text(
                        title, Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp),
                        color = Color(0xFF8A8A8A), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FxFonts.ui, letterSpacing = 2.sp,
                    )
                    list.forEach { name ->
                        val on = name == preset
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (on) pal[0].copy(alpha = .16f) else Color.Transparent)
                                    .clickable { onPreset(name); dropOpen = false }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row {
                                    ramp.take(4).forEachIndexed { i, c ->
                                        Box(
                                            Modifier.size(9.dp, 14.dp).background(
                                                c,
                                                RoundedCornerShape(
                                                    topStart = if (i == 0) 4.dp else 0.dp,
                                                    bottomStart = if (i == 0) 4.dp else 0.dp,
                                                    topEnd = if (i == 3) 4.dp else 0.dp,
                                                    bottomEnd = if (i == 3) 4.dp else 0.dp,
                                                ),
                                            ),
                                        )
                                    }
                                }
                                Text(
                                    name, Modifier.weight(1f),
                                    color = if (on) Color.White else Color(0xFFC8C8C8), fontSize = 14.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                                    fontFamily = FxFonts.ui, maxLines = 1,
                                )
                                Text(
                                    spaceStyleFor(name).label, color = Color(0xFF8A8A8A), fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold, fontFamily = FxFonts.ui,
                                )
                            }
                            if (name in userNames) {
                                Box(
                                    Modifier.size(26.dp, 30.dp)
                                        .clickable(remember { MutableInteractionSource() }, null) { onDeleteUser(name) },
                                    contentAlignment = Alignment.Center,
                                ) { FxIcon(AeFxIcons.CLOSE, size = 12.dp, color = Color(0xFF8A8A8A)) }
                            }
                        }
                    }
                }
            }
        }

        // ── La boîte de nommage d'un preset utilisateur.
        if (saveOpen) {
            Box(
                Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { saveOpen = false },
            )
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 62.dp)
                    .width(280.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF70E0E0E))
                    .border(2.dp, pal[3].copy(alpha = .55f), RoundedCornerShape(18.dp)).padding(12.dp),
            ) {
                Text(
                    "SAVE TO USER", color = Color(0xFF8A8A8A), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FxFonts.ui, letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = saveName,
                    onValueChange = { if (it.length <= 22) saveName = it },
                    singleLine = true,
                    cursorBrush = SolidColor(pal[3]),
                    textStyle = TextStyle(
                        color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FxFonts.ui,
                    ),
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = .45f))
                                .border(1.5.dp, pal[3].copy(alpha = .4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (saveName.isEmpty()) {
                                Text(
                                    "User ${userNames.size + 1}", color = Color(0xFF666666), fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold, fontFamily = FxFonts.ui,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Cancel", Modifier.clickable { saveOpen = false }, color = Color(0xFF9A9A9A),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FxFonts.ui,
                    )
                    Box(
                        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(pal[3])
                            .clickable {
                                onSave(saveName.trim().ifEmpty { "User ${userNames.size + 1}" })
                                saveName = ""; saveOpen = false
                            }
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Save", color = Color(0xFF111111), fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FxFonts.ui,
                        )
                    }
                }
            }
        }

    }
}

/**
 * LA PASTILLE DE PRESET, EN HAUT. Elle porte la teinte du preset, son nom, et le
 * point qui dit qu'un réglage a bougé. Elle ne s'étire pas : c'est un bouton.
 */
@Composable
private fun SpacePresetPill(
    preset: String,
    modified: Boolean,
    pal: List<Color>,
    open: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = .35f))
            .border(2.dp, if (open) pal[0] else pal[0].copy(alpha = .45f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            preset + if (modified) " •" else "",
            color = pal[1], fontSize = 19.sp, fontWeight = FontWeight.Bold,
            fontFamily = FxFonts.ui, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        FxIcon(AeFxIcons.CHEV_DOWN, size = 15.dp, color = pal[0])
    }
}

/**
 * UN BOUTON PLAT À LIBELLÉ. Même dessin que [SpaceFlatButton], pour l'action qui
 * a besoin d'un mot plutôt que d'un glyphe. Un cadre mince dit l'état engagé.
 */
@Composable
private fun SpaceFlatText(text: String, color: Color, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp).clip(RoundedCornerShape(8.dp))
            .background(if (on) color.copy(alpha = .18f) else Color.Transparent)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            fontFamily = FxFonts.ui, letterSpacing = 1.sp)
    }
}

/**
 * UN BOUTON PLAT DE LA PAGE. Rien qu'un glyphe, à la teinte du preset : pas de
 * creux, pas d'ombre, pas de puce. Les commandes de l'écran sont ainsi du même
 * dessin que les anneaux — c'est la demande, « en 2D comme l'interface ».
 */
@Composable
private fun SpaceFlatButton(
    icon: String,
    color: Color,
    side: Dp,
    glyph: Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(side).clip(RoundedCornerShape(8.dp))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FxIcon(icon, size = glyph, color = color) }
}

/** Le pas à pas dans la liste : on boucle, comme la référence. */
private fun stepPreset(all: List<String>, current: String, d: Int): String? {
    if (all.isEmpty()) return null
    val i = all.indexOf(current).coerceAtLeast(0)
    return all[((i + d) % all.size + all.size) % all.size]
}

private val SPACE_STAGE = Color(0xFF141414)
private val SPACE_INK = Color(0xFFF2F2F2)

/** Le fond de scène, pour la fusion des voiles et des anneaux. */
private fun Color.mix(o: Color, t: Float) = Color(
    red + (o.red - red) * t,
    green + (o.green - green) * t,
    blue + (o.blue - blue) * t,
    alpha + (o.alpha - alpha) * t,
)

private val StarPath = Path().apply {
    moveTo(0f, -1f); lineTo(.28f, -.28f); lineTo(1f, 0f); lineTo(.28f, .28f)
    lineTo(0f, 1f); lineTo(-.28f, .28f); lineTo(-1f, 0f); lineTo(-.28f, -.28f); close()
}

/** Normalisé 0..1 d'une valeur réelle du réglage. */
// Les quatre frequences de Space sont normalisees en LOGARITHME (FxParamD.logRatio).
// Ces deux conversions sont le seul chemin du bouton vers une valeur : elles doivent donc
// suivre la meme loi que le moteur, sinon le remplissage du bouton et le glissement
// mentiraient tous les deux — un bouton qui va a fond alors que le filtre est deja ouvert.
private fun FxParamD.norm(v: Float): Float =
    if (logRatio > 0f) (ln((v / min).coerceAtLeast(1e-6f)) / ln(logRatio)).coerceIn(0f, 1f)
    else ((v - min) / (max - min)).coerceIn(0f, 1f)

private fun FxParamD.fromNorm(n: Float): Float =
    if (logRatio > 0f) min * logRatio.pow(n.coerceIn(0f, 1f))
    else min + n.coerceIn(0f, 1f) * (max - min)

/**
 * L'ANNEAU FIN — le potard de la référence.
 *
 * Un anneau de 4 unités, une aiguille, et une lueur qui s'élargit avec la valeur :
 * sombre et terne en bas de course, saturé et large en haut. C'est ce qui dit la
 * valeur sans lire le nombre.
 */
@Composable
private fun SpaceRingKnob(
    label: String,
    spec: FxParamD,
    value: Float,
    color: Color,
    size: Dp,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
    labelSp: Float = 11.5f,
    valueSp: Float = 11.5f,
    knobSize: Dp = size,
    horizontal: Boolean = false,
) {
    val shown by animateFloatAsState(value, spring(1f, Spring.StiffnessMediumLow), label = "knob")
    val n = spec.norm(shown)
    val cur by rememberUpdatedState(spec.norm(value))
    var base = 0f
    var acc = 0f
    val full = with(LocalDensity.current) { 150.dp.toPx() }
    val dial: @Composable () -> Unit = {
        Canvas(
            Modifier.size(knobSize)
                .pointerInput(spec.key) { detectTapGestures(onDoubleTap = { onReset() }) }
                .pointerInput(spec.key) {
                    detectVerticalDragGestures(
                        onDragStart = { base = cur; acc = 0f },
                        onVerticalDrag = { c, dy -> c.consume(); acc += dy; onChange(spec.fromNorm(base - acc / full)) },
                    )
                },
        ) {
            val u = this.size.minDimension / 64f
            val dim = color.mix(SPACE_STAGE, .64f)
            val col = dim.mix(color, .18f + .82f * n)
            val c = center
            val r = 26f * u
            val w = 4f * u
            val glow = .12f + .7f * n
            for (k in 3 downTo 1) {
                drawCircle(col.copy(alpha = glow * .16f / k), r, c, style = Stroke(w + k * (2f + 5f * n) * u))
            }
            drawCircle(col.copy(alpha = .03f + .14f * n), r, c)
            drawCircle(col, r, c, style = Stroke(w))
            rotate(-135f + n * 270f, c) {
                drawLine(col, Offset(c.x, c.y - 22f * u), Offset(c.x, c.y - 1f * u), 4.4f * u, StrokeCap.Round)
            }
        }
    }
    val caption: @Composable (Alignment.Horizontal) -> Unit = { align ->
        Column(horizontalAlignment = align) {
            Text(
                label, color = Color(0xFFEDEDED), fontSize = labelSp.sp, fontWeight = FontWeight.Bold,
                fontFamily = FxFonts.ui, maxLines = 1,
            )
            Text(
                FxDesign.fmt(shown, spec), color = Color(0xFFEDEDED), fontSize = valueSp.sp,
                fontWeight = FontWeight.Bold, fontFamily = FxFonts.ui, maxLines = 1,
            )
        }
    }
    // Deux dispositions, une seule géométrie de potard. À droite (par défaut), le
    // nom est AU-DESSUS et la valeur EN DESSOUS — c'est la disposition des
    // panneaux, où les cases sont étroites et hautes. À plat, les deux textes
    // passent à DROITE du disque : c'est la disposition de la barre du haut, où
    // la place manque en hauteur.
    if (horizontal) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            dial()
            caption(Alignment.Start)
        }
    } else {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label, color = Color(0xFFEDEDED), fontSize = labelSp.sp, fontWeight = FontWeight.Bold,
                fontFamily = FxFonts.ui, maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            dial()
            Spacer(Modifier.height(3.dp))
            Text(
                FxDesign.fmt(shown, spec), color = Color(0xFFEDEDED), fontSize = valueSp.sp,
                fontWeight = FontWeight.Bold, fontFamily = FxFonts.ui, maxLines = 1,
            )
        }
    }
}

/**
 * LES POTARDS DE LA CARTE « SLOTS ».
 *
 * Elle portait une orbe décorative : une boule qui tournait sans rien dire du
 * réglage. Elle porte désormais les SIX réglages qui donnent son caractère au
 * preset — Decay, Size, Attack, Early, Late, Depth —, disposés en trois colonnes
 * sur deux rangées, et ils SE RÈGLENT depuis la carte. La vignette dit donc
 * l'état de l'effet, pas seulement son nom.
 *
 * Ils sont compacts (libellés et valeurs à 8,5) parce que la carte fait 208
 * unités de large : trois potards y tiennent, pas plus.
 */
@Composable
fun AeSpaceSlotKnobs(
    info: FxInfoD,
    values: List<Float>,
    palette: FxPalette?,
    live: Boolean,
    onValue: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ramp = palette?.colors ?: FxDesign.SPACE.palettes.values.first().colors
    val cols = 3
    val rows = (FxDesign.CARD_KEYS.size + cols - 1) / cols
    // ── LA TAILLE SUIT LA CARTE ─────────────────────────────────────────────
    // La carte est un carré dont le côté dépend de la fenêtre. Une taille figée
    // de potard déborderait d'un carré court. On prend donc le plus grand disque
    // qui entre, colonne par colonne ET rangée par rangée, et les libellés
    // suivent proportionnellement — sinon un petit potard garde un gros texte.
    BoxWithConstraints(
        modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val gap = 4.dp
        val textH = 26.dp
        val byW = (maxWidth - gap * (cols - 1)) / cols
        val byH = (maxHeight - gap * (rows - 1)) / rows - textH
        val knob = minOf(byW, byH, 40.dp)
        val f = (knob.value / 34f).coerceIn(.62f, 1.25f)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            FxDesign.CARD_KEYS.chunked(cols).forEachIndexed { rowI, rowKeys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowKeys.forEachIndexed { colI, key ->
                        val spec = info.byKey[key] ?: return@forEachIndexed
                        val idx = info.params.indexOfFirst { it.key == key }
                        val col = ramp[(rowI * cols + colI) % ramp.size]
                        Box(Modifier.weight(1f)) {
                            SpaceRingKnob(
                                label = spec.name, spec = spec,
                                value = values.getOrElse(idx) { spec.def },
                                color = if (live) col else col.copy(alpha = .4f),
                                size = knob, knobSize = knob,
                                labelSp = 8.5f * f, valueSp = 8.5f * f,
                                onChange = { onValue(idx, it) }, onReset = { onValue(idx, spec.def) },
                            )
                        }
                    }
                    repeat(cols - rowKeys.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * LE GROS POTARD — DECAY. Glisser règle, appuyer envoie une ondulation.
 *
 * Il est TOUJOURS vivant : une respiration lente, et une énergie qui monte à
 * l'appui puis retombe avec le temps de queue. C'est ce qui relie le réglage au
 * son : plus la queue est longue, plus l'onde met à mourir.
 *
 * Le changement de preset fait un FONDU ENCHAÎNÉ entre les deux figures, pas une
 * substitution sèche.
 */
@Composable
private fun BigDecayKnob(
    style: SpaceStyle,
    pal: List<Color>,
    decay01: Float,
    t: Float,
    fires: List<Float>,
    decayText: String,
    onDecay: (Float) -> Unit,
    onFire: () -> Unit,
) {
    // Fondu enchaîné entre deux figures : la sortante reste à 1, l'entrante monte par-dessus.
    // Écrire l'état pendant la composition marchait par accident ; ici la bascule est pilotée
    // par une coroutine, donc elle ne dépend pas de l'ordre des recompositions.
    var shown by remember { mutableStateOf(style) }
    var incoming by remember { mutableStateOf<SpaceStyle?>(null) }
    LaunchedEffect(style) { if (style != shown) incoming = style }
    val fade by animateFloatAsState(if (incoming != null) 1f else 0f, tween(450), label = "fade") {
        if (it == 1f && incoming != null) { shown = incoming!!; incoming = null }
    }
    val curD by rememberUpdatedState(decay01)
    var base = 0f
    var acc = 0f
    val full = with(LocalDensity.current) { 170.dp.toPx() }
    val e = (0.12f + 0.12f * sin(t * 1.4f) +
        (fires.maxOfOrNull { exp(-(t - it) * (1.1f / (0.35f + decay01))) } ?: 0f)).coerceIn(0f, 1f)

    Column(Modifier.width(236.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "DECAY", color = SPACE_INK, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            fontFamily = FxFonts.ui, letterSpacing = .5.sp,
        )
        Canvas(
            Modifier.size(236.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { onFire() }) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { base = curD; acc = 0f },
                        onVerticalDrag = { c, dy -> c.consume(); acc += dy; onDecay((base - acc / full).coerceIn(0f, 1f)) },
                    )
                },
        ) {
            val u = size.minDimension / 240f
            val ctr = center
            drawCircle(pal[0].copy(alpha = .04f + .05f * e), 114f * u, ctr)
            drawDesign(shown, pal, decay01, t, e)
            // La figure entrante est posée dans un calque : son opacité monte par-dessus la
            // sortante, elle ne la remplace pas d'un coup.
            incoming?.let { inc ->
                drawIntoCanvas { cv ->
                    cv.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint().apply { alpha = fade })
                    drawDesign(inc, pal, decay01, t, e)
                    cv.restore()
                }
            }
            val life = 1.6f + decay01 * 3f
            fires.forEach { b ->
                val a = t - b
                if (a in 0f..life) {
                    drawCircle(
                        pal[0].copy(alpha = (1f - a / life) * .6f),
                        (14f + a * (50f + decay01 * 20f)) * u, ctr, style = Stroke(2.4f * u),
                    )
                }
            }
            val ang = Math.toRadians((-135f + decay01 * 270f).toDouble()).toFloat()
            fun tap(r: Float) = Offset(ctr.x + sin(ang) * r * u, ctr.y - cos(ang) * r * u)
            drawLine(pal[0].copy(alpha = .35f), tap(86f), tap(112f), 9f * u, StrokeCap.Round)
            drawLine(Color.White, tap(86f), tap(112f), 5f * u, StrokeCap.Round)
        }
        Text(
            decayText, color = SPACE_INK, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            fontFamily = FxFonts.ui, modifier = Modifier.offset(y = (-2).dp),
        )
    }
}

/** L'horloge de l'animation : une seule pour toute la page. */
@Composable
private fun rememberSpaceTime(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) withInfiniteAnimationFrameNanos { ns -> t = ns / 1e9f } }
    return t
}

/**
 * LES DIX FIGURES. Dessinées sur une grille de 240 unités, mises à l'échelle au
 * canevas. `d` = decay 0..1, `t` = secondes, `e` = énergie 0..1.
 */
private fun DrawScope.drawDesign(style: SpaceStyle, c: List<Color>, d: Float, t: Float, e: Float) {
    val u = size.minDimension / 240f
    val ctr = center
    fun p(r: Float, a: Float) = Offset(ctr.x + cos(a) * r * u, ctr.y + sin(a) * r * u)

    when (style) {
        SpaceStyle.Hall -> {
            for (i in 0..6) {
                val r = 99f - i * 14.5f + sin(t * 1.3f + i) * (.8f + e * 2.4f)
                drawCircle(
                    c[min(4, floor(i / 6f * 4.99f).toInt())].copy(alpha = (.6f + .4f * (i / 6f) * (.6f + e)).coerceIn(0f, 1f)),
                    max(2f, r) * u, ctr, style = Stroke(9f * u),
                )
            }
            drawCircle(c[4], (5f + e * 5f) * u, ctr)
        }
        SpaceStyle.Chamber -> {
            for (i in 0..5) {
                val r = 100f - i * 15.5f
                val len = 20f + i * 12f + d * 30f
                val ph = t * (14f + i * 7f) * (if (i % 2 == 1) 1f else -1f) * (.5f + d + e)
                drawCircle(
                    c[i % 5].copy(alpha = .55f + .45f * e), r * u, ctr,
                    style = Stroke(7f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(len * u, r * .9f * u), ph * u)),
                )
            }
            drawCircle(c[0], (8f + e * 6f) * u, ctr)
        }
        SpaceStyle.Room -> for (i in 0..5) {
            val s = (188f - i * 31f + sin(t * 2f - i * .7f) * (2f + e * 5f)) * u
            val a = (.5f + .5f * max(0f, sin(t * 2.4f - i * .9f) * .5f + .5f + e * .3f)).coerceIn(0f, 1f)
            drawRoundRect(
                c[i % 5].copy(alpha = a),
                Offset(ctr.x - s / 2, ctr.y - s / 2), Size(s, s),
                CornerRadius((14f - i) * u), style = Stroke(6f * u),
            )
        }
        SpaceStyle.Shimmer -> {
            for (i in 0..4) {
                val r = 96f - i * 18f
                val n = 24 - i * 3
                val w = (.12f + .07f * i) * (if (i % 2 == 1) -1f else 1f)
                drawCircle(c[i].copy(alpha = .07f + .05f * e), r * u, ctr, style = Stroke(1f * u))
                for (k in 0 until n) {
                    val tw = .5f + .5f * sin(t * 5f + k * 2f + i)
                    val sz = (2.6f + .5f * i) * (.7f + .6f * tw) * (1f + .5f * e)
                    val q = p(r, k.toFloat() / n * 2f * PI.toFloat() + t * w)
                    withTransform({
                        translate(q.x, q.y); rotate(t * 40f + k * 30f, Offset.Zero); scale(sz * u, sz * u, Offset.Zero)
                    }) {
                        drawPath(StarPath, c[i].copy(alpha = (.45f + .55f * tw).coerceIn(0f, 1f)))
                    }
                }
            }
            drawCircle(
                Brush.radialGradient(listOf(c[0].copy(alpha = .6f), Color.Transparent), ctr, (30f + e * 14f) * u),
                (30f + e * 14f) * u, ctr,
            )
        }
        SpaceStyle.Plate -> {
            for (k in 1..6) {
                val r = 16f + k * 14.5f
                val n = 6 + k * 6
                drawCircle(c[min(4, k - 1)].copy(alpha = .06f), r * u, ctr, style = Stroke(1f * u))
                for (m in 0 until n) {
                    val a = m.toFloat() / n * 2f * PI.toFloat()
                    val w = sin(k * 1.3f - t * (2f + d * 3f)) * cos(3f * a + t * .8f) * (.5f + e)
                    drawCircle(
                        c[min(4, k - 1)].copy(alpha = (.4f + abs(w) * .6f).coerceIn(0f, 1f)),
                        (1.8f + abs(w) * 2.8f) * u, p(r + w * 4.5f, a),
                    )
                }
            }
            drawCircle(c[0], (3f + e * 4f) * u, ctr)
        }
        SpaceStyle.Cathedral -> {
            for (i in 0..3) {
                val ro = 98f - i * 24f
                val ri = ro - 21f
                val n = 12 - i * 2
                val da = (PI.toFloat() / n) * .82f
                for (k in 0 until n) {
                    val a = k.toFloat() / n * 2f * PI.toFloat() + t * .05f * (if (i % 2 == 1) -1f else 1f)
                    val path = Path().apply {
                        moveTo(p(ro, a - da).x, p(ro, a - da).y)
                        quadraticBezierTo(p((ro + ri) / 2f + 7f, a - da * .95f).x, p((ro + ri) / 2f + 7f, a - da * .95f).y, p(ri, a).x, p(ri, a).y)
                        quadraticBezierTo(p((ro + ri) / 2f + 7f, a + da * .95f).x, p((ro + ri) / 2f + 7f, a + da * .95f).y, p(ro, a + da).x, p(ro, a + da).y)
                    }
                    val al = (.4f + .6f * max(0f, sin(t * 1.6f - i * .8f + k * .5f) * .5f + .5f + e * .4f)).coerceIn(0f, 1f)
                    drawPath(path, c[i % 5].copy(alpha = al), style = Stroke(3.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            drawCircle(c[0], (12f + e * 5f) * u, ctr, style = Stroke(4f * u))
        }
        SpaceStyle.Spring -> {
            for (arm in 0..1) {
                val path = Path()
                val turns = 4.2f
                val steps = 240
                for (i in 0..steps) {
                    val th = i / steps.toFloat() * turns * 2f * PI.toFloat() + arm * PI.toFloat()
                    val r = 14f + (i / steps.toFloat()) * 86f + sin(th * 6f - t * (4f + 4f * d)) * (2.2f + e * 4f + d * 2f)
                    val q = p(r, th - t * .25f)
                    if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                drawPath(
                    path, c[if (arm == 0) 0 else 2].copy(alpha = if (arm == 0) 1f else .55f),
                    style = Stroke(5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            drawCircle(c[4], (5f + e * 4f) * u, ctr)
        }
        SpaceStyle.Cloud -> {
            for (i in 0..3) {
                val R = 88f - i * 22f
                val n = 7 - i
                for (k in 0 until n) {
                    val a = k.toFloat() / n * 2f * PI.toFloat() + t * (.25f + .1f * i) * (if (i % 2 == 1) -1f else 1f)
                    val rad = (24f - i * 3f + e * 7f) * u
                    val q = p(R, a)
                    drawCircle(
                        Brush.radialGradient(listOf(c[i].copy(alpha = .55f + .2f * e), Color.Transparent), q, rad),
                        rad, q, blendMode = BlendMode.Plus,
                    )
                }
            }
            val rad = (26f + e * 10f) * u
            drawCircle(
                Brush.radialGradient(listOf(c[0].copy(alpha = .7f), Color.Transparent), ctr, rad),
                rad, ctr, blendMode = BlendMode.Plus,
            )
        }
        // Ajoutés pour nos deux presets sans équivalent dans la maquette.
        SpaceStyle.Infinite -> {
            for (i in 0..4) {
                val r = 104f - i * 19f
                drawCircle(
                    c[i % 5].copy(alpha = (.30f + .12f * e).coerceIn(0f, 1f)), r * u, ctr,
                    style = Stroke((2.4f + i * .5f) * u),
                )
            }
            // Une spirale qui se referme : le trait rentre au lieu de sortir.
            val path = Path()
            val steps = 300
            for (i in 0..steps) {
                val f = i / steps.toFloat()
                val th = f * 2.2f * PI.toFloat() + t * .18f
                val r = 100f * (1f - f) + 6f
                val q = p(r, th)
                if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
            }
            drawPath(path, c[0].copy(alpha = .85f), style = Stroke(3.4f * u, cap = StrokeCap.Round))
            drawCircle(c[4], (4f + e * 5f) * u, ctr)
        }
        SpaceStyle.Bloom -> {
            for (i in 0..3) {
                val ro = 92f - i * 20f
                val n = 6 + i
                for (k in 0 until n) {
                    val a = k.toFloat() / n * 2f * PI.toFloat() + t * .09f * (if (i % 2 == 1) -1f else 1f)
                    val open = (.35f + .65f * (.5f + .5f * sin(t * .9f - i * .6f + k * .7f)) + e * .3f).coerceIn(0f, 1f)
                    val path = Path().apply {
                        moveTo(p(8f, a).x, p(8f, a).y)
                        quadraticBezierTo(p(ro * open, a - .5f).x, p(ro * open, a - .5f).y, p(ro, a).x, p(ro, a).y)
                        quadraticBezierTo(p(ro * open, a + .5f).x, p(ro * open, a + .5f).y, p(8f, a).x, p(8f, a).y)
                    }
                    drawPath(
                        path, c[i % 5].copy(alpha = (.35f + .55f * open).coerceIn(0f, 1f)),
                        style = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            drawCircle(c[0], (9f + e * 6f) * u, ctr)
        }
    }
}
