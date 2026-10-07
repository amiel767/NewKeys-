// TrackBottomRow.kt
// Rangée du bas d'une colonne de piste : FX, Settings, On/Off.
// Dimensions et espacement identiques aux cases Solo / Mute (hauteur 22.dp, espacement 2.dp, shape 4.dp).

package com.soundstage.mixer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

private object RowTokens {
    // Matière sobre et élégante (même famille que S / M)
    val KeyTop = Color(0xFF384052)
    val KeyBottom = Color(0xFF2C3242)
    val KeyBorder = Color(0xFF485268)
    val OffTop = Color(0xFF1E2433)
    val OffBottom = Color(0xFF161B26)
    val OffIcon = Color(0xFF646E88)
}

/**
 * Rangée du bas de tranche : FX / Settings / On-Off
 * Dimensions et espacement alignés exactement sur Solo / Mute.
 *
 * @param trackColor couleur de la piste (douce, non-néon)
 * @param active     état On/Off de la piste
 * @param height     hauteur de la rangée (22.dp par défaut, alignée sur S / M)
 */
@Composable
fun TrackBottomRow(
    trackColor: Color,
    active: Boolean,
    onFx: () -> Unit,
    onSettings: () -> Unit,
    onToggle: () -> Unit,
    onUnload: () -> Unit = {},
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyButton(trackColor, active, Modifier.weight(1f), onFx) {
            Text("FX", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        KeyButton(trackColor, active, Modifier.weight(1f), onSettings) {
            DiscreetSlidersIcon()
        }
        PowerKey(trackColor, active, height, onToggle, onUnload)
    }
}

// ───────────── FX / Settings ─────────────

@Composable
private fun KeyButton(
    trackColor: Color,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .fillMaxSize()
            .rowClick(onClick)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(RowTokens.KeyTop, RowTokens.KeyBottom)))
            // Voile teinté par la couleur de piste UNIQUEMENT si la piste est active
            .then(
                if (active) {
                    Modifier.background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Transparent,
                            1f to trackColor.copy(alpha = 0.18f),
                        )
                    )
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                drawContent()
                // Liseré discret en haut
                drawLine(
                    Color.White.copy(alpha = if (active) 0.10f else 0.04f),
                    Offset(4.dp.toPx(), 1.dp.toPx()),
                    Offset(size.width - 4.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Icône curseurs discrète, fine et proportionnée à la boîte 22.dp */
@Composable
private fun DiscreetSlidersIcon() {
    Canvas(Modifier.size(13.dp, 10.dp)) {
        val w = size.width
        val sw = 1.2.dp.toPx()
        val y1 = 2.8.dp.toPx()
        val y2 = 7.2.dp.toPx()
        val x1 = w * 0.35f
        val x2 = w * 0.70f

        // Slider 1
        drawLine(Color.White.copy(alpha = 0.75f), Offset(0f, y1), Offset(w, y1), strokeWidth = sw, cap = StrokeCap.Round)
        drawCircle(Color.White, 1.8.dp.toPx(), Offset(x1, y1))

        // Slider 2
        drawLine(Color.White.copy(alpha = 0.75f), Offset(0f, y2), Offset(w, y2), strokeWidth = sw, cap = StrokeCap.Round)
        drawCircle(Color.White, 1.8.dp.toPx(), Offset(x2, y2))
    }
}

// ───────────── On/Off : bouton carré à ombre douce et bulle corbeille sur appui long ─────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PowerKey(
    trackColor: Color,
    active: Boolean,
    keySize: Dp,
    onClick: () -> Unit,
    onUnload: () -> Unit = {}
) {
    val shape = RoundedCornerShape(4.dp)
    var showTrashBubble by remember { mutableStateOf(false) }

    val border = if (active) {
        scaleColor(trackColor, 1.15f).copy(alpha = 0.65f)
    } else {
        RowTokens.KeyBorder.copy(alpha = 0.5f)
    }

    val body = if (active) {
        Brush.verticalGradient(listOf(scaleColor(trackColor, 1.05f), scaleColor(trackColor, 0.78f)))
    } else {
        Brush.verticalGradient(listOf(RowTokens.OffTop, RowTokens.OffBottom))
    }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "powerPress")

    Box(
        Modifier
            .size(keySize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    showTrashBubble = true
                }
            )
            // Ombre adoucie sans halo débordant
            .drawBehind {
                drawRoundRect(
                    Color.Black.copy(alpha = 0.28f),
                    topLeft = Offset(0f, 1.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            .clip(shape)
            .background(body)
            .border(1.dp, border, shape)
            .drawWithContent {
                drawContent()
                if (active) {
                    val w = this.size.width
                    val h = this.size.height
                    // Reflet discret sur la moitié haute
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                            startY = 0f,
                            endY = h * 0.55f,
                        ),
                        size = Size(w, h * 0.55f),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(11.dp, 11.dp)) {
            val iconColor = if (active) Color.White else RowTokens.OffIcon
            drawPower(iconColor)
        }

        // Bulle flottante corbeille sur appui long (sans contours, avec animation de flottement physique)
        if (showTrashBubble) {
            val floatTransition = rememberInfiniteTransition(label = "bubble_floating")
            val floatOffset by floatTransition.animateFloat(
                initialValue = -120f,
                targetValue = -135f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "float_offset"
            )
            val floatScale by floatTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "float_scale"
            )

            Popup(
                alignment = Alignment.TopCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, floatOffset.toInt()),
                onDismissRequest = { showTrashBubble = false }
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = floatScale
                            scaleY = floatScale
                        }
                        .shadow(12.dp, CircleShape, ambientColor = trackColor, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    scaleColor(trackColor, 1.25f),
                                    scaleColor(trackColor, 0.85f),
                                    Color(0xFF141722)
                                )
                            )
                        )
                        .combinedClickable(
                            onClick = {
                                onUnload()
                                showTrashBubble = false
                            }
                        )
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(16.dp)) {
                        drawTrashCan(Color.White)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawTrashCan(color: Color) {
    val sw = 1.3.dp.toPx()
    val w = size.width
    val h = size.height

    // Lid
    drawLine(color, Offset(w * 0.2f, h * 0.28f), Offset(w * 0.8f, h * 0.28f), sw, StrokeCap.Round)
    // Lid handle
    drawLine(color, Offset(w * 0.4f, h * 0.18f), Offset(w * 0.6f, h * 0.18f), sw, StrokeCap.Round)
    // Can body
    val bodyPath = Path().apply {
        moveTo(w * 0.26f, h * 0.32f)
        lineTo(w * 0.32f, h * 0.88f)
        lineTo(w * 0.68f, h * 0.88f)
        lineTo(w * 0.74f, h * 0.32f)
    }
    drawPath(bodyPath, color, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    // Vertical ribs
    drawLine(color, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.44f, h * 0.78f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.56f, h * 0.78f), sw, StrokeCap.Round)
}

private fun DrawScope.drawPower(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f + 0.4.dp.toPx())
    val r = 3.8.dp.toPx()
    val sw = 1.3.dp.toPx()
    drawArc(
        color = color,
        startAngle = -50f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(2 * r, 2 * r),
        style = Stroke(sw, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(c.x, c.y - r - 1.2.dp.toPx()),
        end = Offset(c.x, c.y - 0.2.dp.toPx()),
        strokeWidth = sw,
        cap = StrokeCap.Round,
    )
}

// ───────────── Utilitaires ─────────────

private fun scaleColor(c: Color, k: Float) = Color(
    red = (c.red * k).coerceIn(0f, 1f),
    green = (c.green * k).coerceIn(0f, 1f),
    blue = (c.blue * k).coerceIn(0f, 1f),
    alpha = c.alpha,
)

@Composable
private fun Modifier.rowClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}
