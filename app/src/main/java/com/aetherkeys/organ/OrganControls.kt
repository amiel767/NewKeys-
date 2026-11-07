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
import androidx.compose.foundation.layout.requiredHeight
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
import com.soundstage.mixer.ui.components.FaderThumb
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

/* ======================== FADER VERTICAL (13 × H) ======================== */
@Composable
internal fun OrganVFader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val v by rememberUpdatedState(value)
    val cb by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.width(13.dp).fillMaxHeight()) {
        val h = maxHeight.value
        val usable = h - 16f
        val travel = max(0f, usable - 26f)
        // Le rail et la rainure ne dépendent pas de la valeur : un drawBehind qui ne lit pas `v`
        // n'est pas rejoué quand le fader bouge, seul le graphicsLayer du thumb/remplissage l'est.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val d = density
                val r = Rect(Offset.Zero, size)
                val c = Corners(4f, 5f, 5f, 4f).scale(d)
                drawOuterShadow(r, c, shadow(0f, 2f, 4f, black(0.5f)))
                drawRailV(r, c, d)
                val g = Rect(size.width / 2 - 1.5f * d, 8f * d, size.width / 2 + 1.5f * d, size.height - 8f * d)
                drawOuterShadow(g, Corners(1.5f * d), shadow(0f, 1f, 1f, white(0.1f)))
                drawRoundRect(black(0.6f), g.topLeft, g.size, androidx.compose.ui.geometry.CornerRadius(1.5f * d))
                drawInsetShadow(g, Corners(1.5f * d), inset(0f, 2f, 4f, black(0.9f)))
            },
        )
        // remplissage : hauteur = val × usable, ancré en bas à 8. Redimensionné par graphicsLayer
        // (pas de relayout, pas de redraw du rail) et sans ressort pendant le drag.
        val fill by animateFloatAsState(v / 100f, if (dragging) tween(0) else spring(0.7f, Spring.StiffnessMedium), label = "vf")
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = 5f.dp, y = (-8).dp)
                .width(3.dp).height(usable.dp)
                .graphicsLayer {
                    val fh = fill * usable * density
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = if (usable > 0f) fh / (usable * density) else 0f
                }
                .drawBehind {
                    drawOuterShadow(Rect(Offset.Zero, size), Corners(1.5f * density), shadow(0f, 0f, 6f, Acc))
                    drawRoundRect(Acc, Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(1.5f * density))
                },
        )
        // thumb : centré sur le rail (left = 6.5 - 20/2 = -3.5), borné à [8, h-34] pour ne jamais
        // déborder du panneau, ni en haut ni en bas.
        val bottom = 8f + fill * travel
        val topY = (h - bottom - 26f).coerceIn(0f, max(0f, h - 26f))
        Box(
            Modifier.offset { IntOffset((-3.5f * density).roundToInt(), (topY * density).roundToInt()) }
                .size(20.dp, 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            FaderThumb(accent = Color.Transparent, modifier = Modifier.fillMaxSize(), showShadow = false, showLed = false)
        }
        // zone tactile élargie à 20 dp (le thumb dépasse du rail)
        Box(
            Modifier.requiredWidth(20.dp).fillMaxHeight().align(Alignment.Center).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    fun upd(y: Float) {
                        val d = density
                        val usablePx = size.height - 16f * d
                        cb(((size.height - 8f * d - y) / usablePx * 100f).coerceIn(0f, 100f))
                    }
                    upd(down.position.y)
                    drag(down.id) { ch -> upd(ch.position.y); ch.consume() }
                    dragging = false
                }
            },
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRailV(r: Rect, c: Corners, d: Float) {
    val brush = androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to Color(0xFF2D3041), 0.5f to Color(0xFF373A4B), 1f to Color(0xFF474A63), startY = r.top, endY = r.bottom,
    )
    drawRRectCorners(r, c, brush)
    drawInsetShadow(r, c, inset(-1f, 0f, 0f, black(0.16f)))
    drawInsetShadow(r, c, inset(1f, 0f, 0f, white(0.07f)))
}

/* ======================== FADER HORIZONTAL BIPOLAIRE (… × 13) ======================== */
@Composable
internal fun OrganHFader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val v by rememberUpdatedState(value)
    val cb by rememberUpdatedState(onValueChange)
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.height(13.dp)) {
        val w = maxWidth.value
        val usable = w - 16f
        val travel = max(0f, usable - 22f)
        Box(
            Modifier.fillMaxSize().drawBehind {
                val d = density
                val r = Rect(Offset.Zero, size)
                val c = Corners(6f * d)
                drawOuterShadow(r, c, shadow(0f, 2f, 4f, black(0.5f)))
                drawRRectCorners(r, c, androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to Color(0xFF2D3041), 0.5f to Color(0xFF373A4B), 1f to Color(0xFF474A63), startX = 0f, endX = size.width))
                drawInsetShadow(r, c, inset(0f, -1f, 0f, black(0.16f)))
                drawInsetShadow(r, c, inset(0f, 1f, 0f, white(0.07f)))
                val g = Rect(8f * d, size.height / 2 - 1.5f * d, size.width - 8f * d, size.height / 2 + 1.5f * d)
                drawOuterShadow(g, Corners(1.5f * d), shadow(0f, 1f, 1f, white(0.1f)))
                drawRoundRect(black(0.6f), g.topLeft, g.size, androidx.compose.ui.geometry.CornerRadius(1.5f * d))
                drawInsetShadow(g, Corners(1.5f * d), inset(0f, 2f, 4f, black(0.9f)))
            },
        )
        // remplissage bipolaire depuis le centre, redimensionné par graphicsLayer (transformOrigin
        // au bord selon le côté) : pas de relayout, et le rail n'est pas redessiné.
        val fill by animateFloatAsState(v / 100f, if (dragging) tween(0) else spring(0.7f, Spring.StiffnessMedium), label = "hf")
        val railC = 8f + usable / 2f
        val tc = 8f + fill * travel + 11f
        val right = tc >= railC
        Box(
            Modifier.align(Alignment.TopStart).offset(x = (if (right) railC else tc).dp, y = 5f.dp)
                .width(max(0f, if (right) tc - railC else railC - tc).dp).height(3.dp)
                .drawBehind {
                    drawOuterShadow(Rect(Offset.Zero, size), Corners(1.5f * density), shadow(0f, 0f, 6f, Acc))
                    drawRoundRect(Acc, Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(1.5f * density))
                },
        )
        // thumb : left = 8 + v × travel, borné pour rester dans la largeur du panneau.
        val thumbLeft = (8f + fill * travel).coerceIn(0f, max(0f, w - 22f))
        Box(Modifier.offset { IntOffset((thumbLeft * density).roundToInt(), 0) }.size(22.dp, 13.dp), contentAlignment = Alignment.Center) {
            FaderThumb(
                accent = Color.Transparent, modifier = Modifier.size(18.dp, 22.dp).graphicsLayer { rotationZ = 90f },
                showShadow = false, showLed = false,
            )
        }
        Box(
            Modifier.fillMaxSize().requiredHeight(18.dp).align(Alignment.Center).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    fun upd(x: Float) {
                        val d = density
                        cb(((x - 8f * d) / (size.width - 16f * d) * 100f).coerceIn(0f, 100f))
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
