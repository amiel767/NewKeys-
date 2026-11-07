package com.aetherkeys.organ

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.soundstage.mixer.R
import com.soundstage.mixer.ui.components.FaderRail
import com.soundstage.mixer.ui.components.FaderRailGeo
import com.soundstage.mixer.ui.components.FaderThumb
import com.soundstage.mixer.ui.components.v2.swapAxes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private val Acc = OrganColors.Accent

/** Place une image vectorielle (avec marge d'ombre `pad`) pour que sa zone utile coïncide avec w×h. */
@Composable
internal fun PaddedVector(res: Int, w: Float, h: Float, pad: Float, modifier: Modifier = Modifier) {
    Box(modifier.size(w.dp, h.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(res), null, Modifier.requiredSize((w + 2 * pad).dp, (h + 2 * pad).dp), contentScale = ContentScale.FillBounds)
    }
}

/** Point LED (dot) : éteint = creux, allumé = couleur + lueur. La couleur est interpolée, donc
 * l'allumage/extinction se voit comme une transition, pas comme un saut. */
@Composable
internal fun LedDot(size: Float, on: Boolean, color: Color = Acc, offColor: Color = OrganColors.Dark2, glow: Float = 5f, modifier: Modifier = Modifier) {
    val c by animateColorAsState(if (on) color else offColor, tween(160), label = "led")
    Box(
        modifier.size(size.dp).cssBox(
            radius = size / 2, fill = solid(c),
            shadows = if (on) listOf(shadow(0f, 0f, glow, color)) else listOf(inset(0f, 1f, 2f, black(0.8f))),
        ),
    )
}

/* ============================== KNOB ============================== */
@Composable
internal fun OrganKnob(size: Int, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val v by rememberUpdatedState(value)
    val cb by rememberUpdatedState(onValueChange)
    val body = when (size) { 48 -> R.drawable.organ_knob_body_48; 52 -> R.drawable.organ_knob_body_52; else -> R.drawable.organ_knob_body_56 }
    val cap = when (size) { 48 -> R.drawable.organ_knob_cap_48; 52 -> R.drawable.organ_knob_cap_52; else -> R.drawable.organ_knob_cap_56 }
    Box(
        modifier
            .size(size.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startY = down.position.y
                    val startValue = v
                    drag(down.id) { ch ->
                        val dy = (startY - ch.position.y) / density
                        cb((startValue + dy / 1.5f).coerceIn(0f, 100f))
                        ch.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PaddedVector(body, size.toFloat(), size.toFloat(), 8f)
        // arc de valeur (SVG 100×100, r=42, trait 3, rotation 135°, 270° de course) — entre la gorge et le capuchon
        Canvas(Modifier.requiredSize((size + 4).dp)) {
            val k = this.size.minDimension / 100f
            val r = 42f * k
            val tl = Offset(this.size.width / 2 - r, this.size.height / 2 - r)
            val sz = Size(2 * r, 2 * r)
            val sw = 3f * k
            drawArc(Color.White.copy(alpha = 0.04f), 135f, 270f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            val sweep = max(0.1f, 270f * (v / 100f))
            drawArc(Acc.copy(alpha = 0.10f), 135f, sweep, false, tl, sz, style = Stroke(sw + 3f * k, cap = StrokeCap.Round))
            drawArc(Acc.copy(alpha = 0.16f), 135f, sweep, false, tl, sz, style = Stroke(sw + 1.5f * k, cap = StrokeCap.Round))
            drawArc(Acc, 135f, sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        PaddedVector(cap, size.toFloat(), size.toFloat(), 8f)
        // repère : 2×8, top 2 dans un conteneur inset 6, tourné de -135° + v·270°
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            rotate(-135f + (v / 100f) * 270f, pivot = center) {
                drawRoundRect(Acc, Offset(center.x - 1f * d, 8f * d), Size(2f * d, 8f * d), androidx.compose.ui.geometry.CornerRadius(1f * d))
            }
        }
    }
}

/**
 * Barre LED d'une poignée de fader, centrée dans son cadre. La maquette la dessine en dur dans le
 * capot (60 % de la largeur, 2 px, rayon 1, halo bleu) : la LED floutée du composant du mixeur est
 * trop large et trop molle pour se lire comme un repère, donc on la redessine nette.
 */
@Composable
private fun LedBar(widthDp: Float, heightDp: Float) {
    Canvas(Modifier.size(widthDp.dp, heightDp.dp)) {
        val r = size.minDimension / 2f
        drawRoundRect(Acc.copy(alpha = 0.35f), Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(r))
        drawRoundRect(Acc, Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(r))
    }
}

/* ======================== FADER — RAIL DU MIXEUR ======================== */
/**
 * Les faders de la section Rotary portent le rail du mixeur ([FaderRail]) : même châssis, même
 * rainure, mêmes liserés que les faders de tranche. La maquette redessinait ce rail en CSS ; on
 * ne le redessine plus, on le reprend — et la rainure qu'il dessine devient la référence.
 *
 * [FaderRailGeo] donne la position de cette rainure, et le capuchon s'y pose. Le remplissage suit
 * le centre du capuchon, comme sur le fader du mixeur : le niveau et la poignée ne peuvent donc
 * pas diverger.
 */
private const val RAIL_W = 13f     // largeur du rail (maquette : .fader-wrapper 13px)
private const val THUMB_W = 20f    // capuchon debout (maquette : .fader-thumb)
private const val THUMB_H = 26f
private const val HTHUMB_W = 22f   // capuchon couché (maquette : .h-fader-thumb)
private const val HTHUMB_H = 18f

@Composable
internal fun OrganVFader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val v by rememberUpdatedState(value)
    val cb by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.requiredWidth(RAIL_W.dp).fillMaxHeight()) {
        val d = LocalDensity.current
        val railWpx = with(d) { RAIL_W.dp.toPx() }
        val thumbHpx = with(d) { THUMB_H.dp.toPx() }
        val hPx = with(d) { maxHeight.toPx() }
        // Rainure du rail, telle que FaderRail la dessine : le capuchon se pose dessus.
        val inset = FaderRailGeo.grooveInsetY(railWpx)
        val gA = inset
        val gB = hPx - inset
        val span = (gB - gA).coerceAtLeast(1f)
        val travel = (span - thumbHpx).coerceAtLeast(0f)
        val fill by animateFloatAsState(v / 100f, if (dragging) tween(0) else spring(0.7f, Spring.StiffnessMedium), label = "vf")
        // 0 : le bord bas du capuchon touche le bas de la rainure ; 1 : son bord haut touche le
        // haut. Son centre voyage donc entre gA + h/2 et gB - h/2, et le remplissage suit ce centre.
        val centre = gA + thumbHpx / 2f + (1f - fill) * travel
        val level = (1f - (centre - gA) / span).coerceIn(0f, 1f)
        FaderRail(accent = Acc, modifier = Modifier.fillMaxSize(), level = level, glow = 0.35f)
        // Le capuchon est plus large que le rail : centré sur la rainure il dépasse de
        // (THUMB_W - RAIL_W)/2 de chaque côté. Il est donc centré horizontalement par le
        // conteneur, et l'offset ne porte que sur la course verticale — le recentrer ici en x
        // l'aurait décalé d'autant vers la gauche.
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (centre - thumbHpx / 2f).roundToInt()) }
                .requiredSize(THUMB_W.dp, THUMB_H.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Le capot vient du composant du mixeur, mais sa LED floutée est large et molle :
            // la maquette veut une barre nette de 60 % de la largeur et 2 px de haut. On la
            // dessine ici, dans le capot, avec son halo — c'est elle qu'on lit sur un fader.
            FaderThumb(accent = Acc, modifier = Modifier.fillMaxSize(), showShadow = true, showLed = false)
            LedBar(widthDp = 12f, heightDp = 2f)
        }
        // zone tactile : tout le rail, le capuchon dépassant de 3,5 unités de chaque côté.
        Box(
            Modifier.fillMaxSize().pointerInput(hPx, railWpx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    fun upd(y: Float) {
                        if (travel <= 0f) return
                        val c = y.coerceIn(gA + thumbHpx / 2f, gB - thumbHpx / 2f)
                        cb(((1f - (c - gA - thumbHpx / 2f) / travel) * 100f).coerceIn(0f, 100f))
                    }
                    upd(down.position.y)
                    drag(down.id) { ch -> upd(ch.position.y); ch.consume() }
                    dragging = false
                }
            },
        )
    }
}

/* ======================== FADER HORIZONTAL BIPOLAIRE (… × 13) ======================== */
/**
 * Même rail du mixeur, couché (rotation 90°, comme le fader master). Le remplissage est
 * bipolaire — il part du MILIEU de la rainure — et suit le centre du capuchon, donc la barre
 * et la poignée restent d'accord. [FaderRailGeo] donne la rainure du rail couché, et le
 * capuchon s'y pose au lieu de flotter à côté.
 */
@Composable
internal fun OrganHFader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val v by rememberUpdatedState(value)
    val cb by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.height(RAIL_W.dp)) {
        val d = LocalDensity.current
        val railWpx = with(d) { RAIL_W.dp.toPx() }
        val thumbWpx = with(d) { HTHUMB_W.dp.toPx() }
        val lPx = with(d) { maxWidth.toPx() }
        // Rainure du rail couché : [gA, gB] le long de l'axe horizontal.
        val inset = FaderRailGeo.grooveInsetY(railWpx)
        val gA = inset
        val gB = lPx - inset
        val span = (gB - gA).coerceAtLeast(1f)
        val travel = (span - thumbWpx).coerceAtLeast(0f)
        val fill by animateFloatAsState(v / 100f, if (dragging) tween(0) else spring(0.7f, Spring.StiffnessMedium), label = "hf")
        // centre du capuchon, puis barre bipolaire entre le milieu de la rainure et ce centre
        val centre = gA + thumbWpx / 2f + fill * travel
        val railC = (gA + gB) / 2f
        // Le rail du mixeur, couché : le même composant que les faders de tranche.
        Box(
            Modifier.fillMaxSize().swapAxes().graphicsLayer { rotationZ = 90f; transformOrigin = TransformOrigin(0.5f, 0.5f) },
        ) {
            FaderRail(accent = Acc, modifier = Modifier.fillMaxSize(), level = 0f, glow = 0f)
        }
        Canvas(Modifier.fillMaxSize()) {
            val gw = FaderRailGeo.grooveWidth(size.height)
            val cy = size.height / 2f
            val a = if (railC < centre) railC else centre
            val b = if (railC < centre) centre else railC
            if (b - a > 0.5f) {
                drawRoundRect(Acc.copy(alpha = 0.35f), Offset(a, cy - gw), Size(b - a, gw * 2f), CornerRadius(gw))
                drawRoundRect(Acc, Offset(a, cy - gw / 2f), Size(b - a, gw), CornerRadius(gw / 2f))
            }
        }
        Box(
            Modifier.align(Alignment.CenterStart)
                .offset { IntOffset((centre - thumbWpx / 2f).roundToInt(), 0) }.requiredSize(HTHUMB_W.dp, HTHUMB_H.dp),
            contentAlignment = Alignment.Center,
        ) {
            FaderThumb(
                accent = Acc, modifier = Modifier.requiredSize(HTHUMB_H.dp, HTHUMB_W.dp).graphicsLayer { rotationZ = 90f },
                showShadow = true, showLed = false,
            )
            // Barre LED verticale (le fader est couché) : 60 % de la hauteur du capot, 2 px de large.
            LedBar(widthDp = 2f, heightDp = 11f)
        }
        Box(
            Modifier.fillMaxSize().pointerInput(lPx, railWpx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    fun upd(x: Float) {
                        if (travel <= 0f) return
                        val c = x.coerceIn(gA + thumbWpx / 2f, gB - thumbWpx / 2f)
                        cb(((c - gA - thumbWpx / 2f) / travel * 100f).coerceIn(0f, 100f))
                    }
                    upd(down.position.x)
                    drag(down.id) { ch -> upd(ch.position.x); ch.consume() }
                    dragging = false
                }
            },
        )
    }
}

/* ======================== DRAWBAR (une barre) ======================== */
private val EaseOutCss = CubicBezierEasing(0f, 0f, 0.58f, 1f)

@Composable
internal fun DrawbarUnit(
    thumbRes: Int, value: Int, epoch: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val cb by rememberUpdatedState(onValue)
    val cur by rememberUpdatedState(value)
    BoxWithConstraints(modifier.fillMaxHeight()) {
        // `den` et non `density` : dans les drawBehind de cette fonction, `density` doit rester
        // le Float de DrawScope. Un local du meme nom le masquait et `3f * d` ne compilait pas.
        val den = LocalDensity.current
        val hDp = maxHeight
        val hPx = with(den) { maxHeight.toPx() }
        val wPx = with(den) { maxWidth.toPx() }
        val thumbH = with(den) { 46.dp.toPx() }
        val thumbW = with(den) { 30.dp.toPx() }
        val four = with(den) { 4.dp.toPx() }
        var topPx by remember { mutableFloatStateOf(0f) }
        var job by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        fun settle(v: Int, ms: Int) {
            job?.cancel()
            val target = v * 0.1f * hPx
            val from = topPx
            job = scope.launch { animate(from, target, animationSpec = tween(ms, easing = EaseOutCss)) { x, _ -> topPx = x } }
        }
        LaunchedEffect(epoch, hPx) { settle(cur, 200) }

        // numéros 1..8 à 20 %…90 %
        Box(Modifier.width(8.dp).fillMaxHeight()) {
            for (i in 1..8) {
                val y = hDp * (0.1f + 0.1f * i)
                androidx.compose.material3.Text(
                    i.toString(),
                    style = otext(8f, androidx.compose.ui.text.font.FontWeight.Bold, Color(0xD98FB2E8), OrganFonts.PlexMono, lh = 8f)
                        .copy(shadow = androidx.compose.ui.graphics.Shadow(black(0.9f), Offset.Zero, 1.5f)),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(8.dp).offset(y = y - 4.dp),
                )
            }
        }
        // rail
        Box(
            Modifier.offset(8.dp, 0.dp).width(maxWidth - 8.dp).fillMaxHeight().drawBehind {
                val d = density
                val r = Rect(Offset.Zero, size)
                val c = Corners(3f * d)
                drawOuterShadow(r, c, shadow(0f, 1f, 0f, white(0.04f)))
                drawRRectCorners(r, c, androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color(0xFF14192A), 0.5f to Color(0xFF282F45), 1f to Color(0xFF14192A), startY = 0f, endY = size.height))
                drawInsetShadow(r, c, inset(0f, -1f, 0f, white(0.04f)))
                drawInsetShadow(r, c, inset(0f, 2f, 5f, black(0.85f)))
                // rainure 4 px (top/bottom 10)
                val g = Rect(size.width / 2 - 2f * d, 10f * d, size.width / 2 + 2f * d, size.height - 10f * d)
                drawOuterShadow(g, Corners(2f * d), shadow(0f, 1f, 0f, white(0.06f)))
                drawRoundRect(black(0.75f), g.topLeft, g.size, androidx.compose.ui.geometry.CornerRadius(2f * d))
                drawInsetShadow(g, Corners(2f * d), inset(0f, 2f, 4f, black(1f)))
                // graduations : 0,5 px tous les 12,5 % sur 2 px de large, à gauche
                for (n in 1..8) {
                    val y = size.height * 0.125f * n
                    drawRect(Color(0x598FB2E8), Offset(2f * d, y - 0.5f * d), Size(2f * d, 0.5f * d))
                }
            },
        )
        // thumb 30×46 centré sur le rail (left: calc(50% + 4px))
        val cx = wPx / 2f + four
        Box(
            Modifier.offset { IntOffset((cx - thumbW / 2f).roundToInt(), topPx.roundToInt()) }.size(30.dp, 46.dp),
            contentAlignment = Alignment.Center,
        ) {
            // ombre portée + image : le XML contient déjà drop-shadow(0 3px 5px .55) ; 46.22 × 62.40 dp (marge 8,2 dp)
            Image(painterResource(thumbRes), null, Modifier.requiredSize(46.2237.dp, 62.4019.dp), contentScale = ContentScale.FillBounds)
        }
        // interaction (toute l'unité) — mêmes règles que le HTML
        Box(
            Modifier.fillMaxSize().pointerInput(hPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    job?.cancel()
                    val travel = size.height - thumbH
                    fun v01(y: Float) = ((y - thumbH / 2f) / travel).coerceIn(0f, 1f)
                    val onThumb = down.position.x in (cx - thumbW / 2f)..(cx + thumbW / 2f) &&
                        down.position.y in topPx..(topPx + thumbH)
                    if (!onThumb) {
                        val v = (v01(down.position.y) * 8f).roundToInt()
                        cb(v); settle(v, 150)
                    }
                    var lastY = down.position.y
                    drag(down.id) { ch ->
                        lastY = ch.position.y
                        topPx = v01(lastY) * travel
                        ch.consume()
                    }
                    val v = (v01(lastY) * 8f).roundToInt()
                    cb(v); settle(v, 150)
                }
            },
        )
    }
}
