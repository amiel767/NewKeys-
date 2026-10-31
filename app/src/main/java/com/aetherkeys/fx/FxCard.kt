package com.aetherkeys.fx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Une carte d'effet : le type, le bouton engage, le nom, la vignette, le preset.
 *
 * Elle se tire VERS LE BAS pour la supprimer : la corbeille se révèle derrière
 * la carte, et son couvercle s'ouvre avec la progression. C'est le geste de la
 * maquette — pas de bouton « supprimer », qui serait un geste de plus à viser.
 */
@Composable
fun FxCard(
    info: FxInfo,
    accent: Color,
    on: Boolean,
    presetName: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    dragging: Boolean = true,
) {
    var dy by remember { mutableFloatStateOf(0f) }
    var hot by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    // 118 unités de course : c'est la valeur de la maquette.
    val prog = (dy / 118f).coerceIn(0f, 1f)
    val armed = prog > .6f

    val settle by animateFloatAsState(
        targetValue = if (dragging) dy else 0f,
        animationSpec = spring(dampingRatio = .9f, stiffness = 380f),
        label = "cardDy",
    )
    val p = if (dragging) prog else (settle / 118f).coerceIn(0f, 1f)

    Box(
        Modifier.width(FxGeo.CARD_W.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)),
    ) {
        // La corbeille, révélée par le haut.
        Box(
            Modifier.fillMaxWidth().height(150.dp).padding(top = 14.dp)
                .graphicsLayer { alpha = p; translationY = p * 10f },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).fxSvg(AeFxSvg.trash(p)))
                Spacer(Modifier.height(7.dp))
                Text(
                    if (armed) "Release to delete" else "Swipe down",
                    color = if (armed) FxC.dangerSoft else Color(0xFF7C869C),
                    fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().fillMaxHeight()
                .graphicsLayer {
                    translationY = if (leaving) 380f else settle
                    val sc = 1f - p * .03f
                    scaleX = sc; scaleY = sc
                    alpha = if (leaving) 0f else 1f - p * .18f
                }
                .fxSvg(AeFxSvg.card(hot))
                .padding(10.dp)
                .pointerInput(info.id) {
                    detectVerticalDragGestures(
                        onDragStart = { hot = true },
                        onDragEnd = {
                            if (dy > 92f) { leaving = true; onRemove() } else dy = 0f
                        },
                        onDragCancel = { dy = 0f },
                    ) { _, delta -> dy = (dy + delta).coerceAtLeast(0f) }
                }
                .clickableNoRipple { if (dy < 2f && !leaving) onOpen() },
        ) {
            // ── le type et le bouton engage ────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info.type.uppercase(),
                    color = if (on) accent else FxC.dim,
                    fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                FxPowerButton(on = on, accent = accent, onClick = onToggle)
            }
            Spacer(Modifier.height(7.dp))

            // ── le nom ────────────────────────────────────────────────────
            Text(
                info.name,
                color = if (on) FxC.ink else Color(0xFF98A1B4),
                fontSize = 18.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Spacer(Modifier.height(7.dp))

            // ── la vignette ───────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF12161F)),
            ) {
                FxMiniGraph(info.type, accent, on)
                // Le voile qui éteint la vignette quand l'effet est contourné.
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight()
                        .background(Color(0xFF0A0D14).copy(alpha = .34f * if (on) 0f else 1f)),
                )
            }
            Spacer(Modifier.height(7.dp))

            // ── la pastille d'état et le preset ───────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (on) Color(0xFFC9D2E6) else Color(0xFF2A3040)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    presetName ?: info.type,
                    color = FxC.dim, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
            }
        }
    }
}

/**
 * Le bouton engage. Éteint c'est un PUITS : creusé dans la matière, ombre en
 * haut, lumière en bas, glyphe en retrait. Allumé le puits s'éclaire et le
 * glyphe ressort. Le SVG porte le creux, l'accent porte l'état.
 */
@Composable
private fun FxPowerButton(on: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
            .then(
                if (on) Modifier.background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = .34f), accent.copy(alpha = .10f)),
                    )
                ) else Modifier.fxSvg(AeFxSvg.pressedWell())
            )
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        FxIcon(AeFxIcons.POWER, 13.dp, if (on) accent else Color(0xFF6B7488), sw = 2.2f)
    }
}

/** `clickable` sans ondulation : la maquette n'en a aucune. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
