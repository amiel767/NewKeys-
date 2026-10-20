package com.soundstage.mixer.ui.components.v2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Sustain pedal case: no label, no in-case light — just the pedal itself.
 *
 * The plate tilts down when sustain is held and springs back on release, driven
 * purely by [isActive], so an external MIDI pedal (CC64) animates it exactly like
 * a tap on the case.
 */
@Composable
fun SustainPedalButton(
    isActive: Boolean,
    onToggle: () -> Unit,
    panelTop: Color = Color(0xFF2B3348),
    panelBottom: Color = Color(0xFF1D2433),
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF8FB2E8)

    val caseTint by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(220),
        label = "pedalTint"
    )
    val plateTint by animateColorAsState(
        targetValue = if (isActive) accent else Color(0xFFC3CCDE),
        animationSpec = tween(220),
        label = "pedalPlate"
    )
    // Pressed = plate tipped down; released = plate lifted. Slight overshoot on the way up.
    val press by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "pedalPress"
    )
    val glow by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(240),
        label = "pedalGlow"
    )

    Box(
        modifier = modifier
            .width(84.dp)
            .height(44.dp)
            .shadow(5.dp, RoundedCornerShape(14.dp),
                spotColor = Color(0x59000000), ambientColor = Color.Black)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(panelTop, Color(0xFF5B79AE), caseTint * 0.42f),
                        lerp(panelBottom, Color(0xFF33507E), caseTint * 0.42f)
                    )
                )
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val baseY = size.height - 7.dp.toPx()
            val plateW = 46.dp.toPx()
            val plateH = 12.dp.toPx()
            val hingeX = cx - plateW / 2f

            // Glow pooled under the pedal while it is held down.
            if (glow > 0f) {
                drawOval(
                    color = accent.copy(alpha = 0.30f * glow),
                    topLeft = Offset(cx - plateW * 0.55f, baseY - 3.dp.toPx()),
                    size = Size(plateW * 1.1f, 7.dp.toPx())
                )
            }

            // Heel: the fixed block the plate hinges on.
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF3A4560), Color(0xFF232B3C))),
                topLeft = Offset(hingeX - 2.dp.toPx(), baseY - 5.dp.toPx()),
                size = Size(plateW + 4.dp.toPx(), 8.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.35f),
                start = Offset(hingeX, baseY + 3.dp.toPx()),
                end = Offset(hingeX + plateW, baseY + 3.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )

            // Plate: -17 deg at rest, -3 deg when pressed, hinged at its left edge.
            val angle = -17f + press * 14f
            rotate(degrees = angle, pivot = Offset(hingeX, baseY)) {
                val top = baseY - 4.dp.toPx() - plateH
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            lerp(plateTint, Color.White, 0.22f),
                            lerp(plateTint, Color(0xFF0B0F18), 0.42f)
                        )
                    ),
                    topLeft = Offset(hingeX, top),
                    size = Size(plateW, plateH),
                    cornerRadius = CornerRadius(3.5.dp.toPx())
                )
                // Top face of the plate catches the light.
                val face = Path().apply {
                    moveTo(hingeX + 2.dp.toPx(), top + 1.dp.toPx())
                    lineTo(hingeX + plateW - 2.dp.toPx(), top + 1.dp.toPx())
                    lineTo(hingeX + plateW - 5.dp.toPx(), top + 4.dp.toPx())
                    lineTo(hingeX + 5.dp.toPx(), top + 4.dp.toPx())
                    close()
                }
                drawPath(face, Color.White.copy(alpha = 0.16f + 0.14f * press))
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.30f),
                    topLeft = Offset(hingeX, top),
                    size = Size(plateW, plateH),
                    cornerRadius = CornerRadius(3.5.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

/** Linear colour mix, used for the animated active tint of the sustain case. */
private fun lerp(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = a.alpha + (b.alpha - a.alpha) * f
    )
}
