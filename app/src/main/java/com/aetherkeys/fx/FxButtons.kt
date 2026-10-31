package com.aetherkeys.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * LA CASE D'UN BOUTON — le cadre commun à tous les `+`, `−`, `✕` et chevrons.
 *
 * Quatre couches, dans cet ordre : une ombre douce **portée sous la case**, le
 * fond bombé (dégradé clair en haut), le liseré de l'arête haute, puis la
 * bordure. C'est ce qui sépare une case « posée » d'un rectangle plat — et
 * c'est exactement ce que la maquette fait avec `inset 0 1px 0` plus une ombre
 * externe.
 *
 * À l'appui la case **s'enfonce** : elle descend, rétrécit de 5 %, et son liseré
 * passe en bas. Le bouton n'a donc pas besoin d'ondulation pour répondre.
 *
 * @param side côté de la case (carrée).
 * @param radius rayon des coins ; par défaut le tiers du côté.
 * @param on état « engagé » : le fond se teinte de l'accent.
 */
@Composable
fun FxButtonChip(
    side: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = side / 3f,
    accent: Color = FxC.acc,
    on: Boolean = false,
    content: @Composable () -> Unit,
) {
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val sink by animateFloatAsState(press.value, label = "chipSink")
    Box(
        modifier
            .size(side)
            .graphicsLayer {
                translationY = sink * 1.1f
                scaleX = 1f - sink * .05f
                scaleY = 1f - sink * .05f
            }
            // L'ombre est peinte AVANT le clip : dans le drawBehind de la case,
            // elle serait rognée à ses propres bords et ne dépasserait pas.
            .drawBehind {
                softDropShadow(
                    radius = radius.toPx(),
                    w = size.width, h = size.height,
                    drop = size.height * .34f,
                    alpha = if (on) .60f else .70f,
                )
            }
            .clip(RoundedCornerShape(radius))
            .drawBehind {
                val r = radius.toPx()
                val w = size.width
                val h = size.height
                val path = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
                drawPath(
                    path,
                    Brush.verticalGradient(
                        if (on) listOf(accent.copy(alpha = .34f), accent.copy(alpha = .12f))
                        else listOf(Color(0xFF232A38), Color(0xFF171D28)),
                    ),
                )
                // Le liseré : en haut au repos, en bas quand la case est enfoncée.
                val lip = if (sink > .5f) 1f else 0f
                drawPath(
                    path,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .17f), Color.Transparent),
                        startY = h - 1.6f, endY = h,
                    ),
                )
                if (lip == 0f) {
                    drawPath(
                        path,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = .17f), Color.Transparent),
                            startY = 0f, endY = 1.6f,
                        ),
                    )
                }
                drawPath(path, Color.Black.copy(alpha = .48f), style = Stroke(1f))
                if (on) drawPath(path, accent.copy(alpha = .5f), style = Stroke(1f))
            }
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        scope.launch { press.animateTo(1f, tween(70)) }
                        tryAwaitRelease()
                        scope.launch { press.animateTo(0f, tween(140)) }
                    },
                    onTap = { onClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Ombre portée douce, dessinée en rectangles empilés.
 *
 * On n'utilise ni `BlurMaskFilter` ni `Modifier.shadow` : le premier n'est pas
 * fiable en accélération matérielle selon la version d'Android, le second est un
 * halo d'élévation gris qui ne suit pas la teinte de la case. Ici, on empile des
 * copies de la même forme, de plus en plus décalées vers le bas et de plus en
 * plus transparentes : la partie qui dépasse sous la case se lit comme un flou.
 */
private fun DrawScope.softDropShadow(radius: Float, w: Float, h: Float, drop: Float, alpha: Float) {
    val steps = 6
    for (k in steps - 1 downTo 0) {
        val t = k / (steps - 1f)
        val a = alpha * (1f - t) * .42f
        if (a <= .003f) continue
        val d = drop * t
        drawRoundRect(
            color = Color.Black.copy(alpha = a),
            topLeft = Offset(0f, d),
            size = Size(w, h),
            cornerRadius = CornerRadius(radius),
        )
    }
}
