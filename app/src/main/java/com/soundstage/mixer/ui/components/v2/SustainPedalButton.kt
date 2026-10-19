package com.soundstage.mixer.ui.components.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sustain pedal case, translated from the JSX (MixerPreview.jsx:425).
 *
 * The JSX has no lit indicator: only a small dot whose colour changes
 * (#5A6379 idle -> #8FB2E8 active). No halo, no lamp, and no glow behind the pedal
 * when it is down — the JSX only tints the case background. The pedal itself tilts
 * with the state, which is what makes the external MIDI pedal visible.
 *
 * The press motion is deliberately large (the cap drops 3 dp and swings ~18°), so a
 * real pedal press reads at a glance instead of being a barely visible 8° twitch.
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
    val idleDot = Color(0xFF5A6379)

    // The pedal swings down while pressed; the spring keeps it from snapping.
    val angle by animateFloatAsState(
        targetValue = if (isActive) 18f else -9f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "pedalAngle"
    )
    val pressDepth by animateFloatAsState(
        targetValue = if (isActive) 3f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "pedalDepth"
    )
    val dotColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isActive) accent else idleDot,
        animationSpec = tween(250),
        label = "pedalDot"
    )
    val caseTint by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(220),
        label = "pedalTint"
    )

    Box(
        modifier = modifier
            .width(84.dp)
            .height(44.dp) // JSX: width 84, height 44
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(panelTop, Color(0xFF5B79AE), caseTint * 0.55f),
                        lerp(panelBottom, Color(0xFF33507E), caseTint * 0.55f)
                    )
                )
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact pedal illustration; the cap swings down while pressed.
            Canvas(Modifier.width(22.dp).fillMaxHeight()) {
                val cx = size.width * 0.60f
                val baseY = size.height * 0.72f
                val depth = pressDepth.dp.toPx()

                drawOval(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(cx - 9.dp.toPx(), baseY + 4.dp.toPx()),
                    size = Size(18.dp.toPx() + if (isActive) 2.dp.toPx() else 0f, 3.dp.toPx())
                )
                val cable = Path().apply {
                    moveTo(cx - 10.dp.toPx(), baseY - 3.dp.toPx())
                    quadraticBezierTo(cx - 14.dp.toPx(), baseY - 4.dp.toPx(), cx - 17.dp.toPx(), baseY - 10.dp.toPx())
                }
                drawPath(cable, Color(0xFF2E3549), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
                drawRoundRect(
                    color = Color(0xFF2E3549),
                    topLeft = Offset(cx - 11.dp.toPx(), baseY),
                    size = Size(7.dp.toPx(), 4.dp.toPx()),
                    cornerRadius = CornerRadius(1.2.dp.toPx())
                )

                rotate(degrees = angle, pivot = Offset(cx - 8.dp.toPx(), baseY - 4.dp.toPx() + depth)) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF6E7799), Color(0xFF4B5373), Color(0xFF3F4766))
                        ),
                        topLeft = Offset(cx - 8.dp.toPx(), baseY - 12.dp.toPx() + depth),
                        size = Size(20.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                    drawLine(
                        Color(0x0FFFFFFF),
                        Offset(cx - 6.dp.toPx(), baseY - 9.dp.toPx() + depth),
                        Offset(cx + 10.dp.toPx(), baseY - 9.dp.toPx() + depth),
                        0.6.dp.toPx()
                    )
                }
            }

            // Status dot (8 x 8) then the label, exactly like the JSX span pair.
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(5.dp))
            Text(
                "SUSTAIN",
                color = if (isActive) Color(0xFFF1F4FA) else Color(0xFF9AA5BA),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp,
                softWrap = false,
                maxLines = 1
            )
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
